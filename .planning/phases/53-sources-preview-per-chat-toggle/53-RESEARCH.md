# Phase 53: Sources Preview + Per-Chat Toggle - Research

**Researched:** 2026-09-28
**Domain:** Android (Kotlin + Compose + Room) — chat UI surfaces + single Room migration
**Confidence:** HIGH

## Summary

Phase 53 adds three chat surfaces on top of Phase 52's fan-out output with zero inference or provider changes: (1) a `SourcePreviewSheet` bottom sheet that renders already-persisted extracted text per source, (2) clickable Fuentes items (ok → sheet, omitida → struck/disabled), and (3) per-conversation tri-state web override plus a one-off "Sin web" composer chip. Persistence is a single Room migration v14→v15 carrying both DDLs (`conversations.web_override` column + new `grounded_sources` table).

The two highest-leverage findings for the planner: **(a)** `ChatRepository.saveMessage()` currently returns `Unit` and `ChatMessage.toEntity()` drops the UUID id (Room auto-generates the row id) — so persisting source rows scoped to the assistant message requires threading the Room row id back out of the insert; the snapshot must be captured post-fetch and written at assistant-save time. **(b)** `ChatMessage.groundedSources: List<String>` (URLs only) cannot render the omitida-struck list or the sheet body — a parallel ephemeral details list (url + text + status) is needed alongside it. Both are small, well-precedented changes, but every plan in this phase depends on them.

**Primary recommendation:** One migration `MIGRATION_14_15` (nullable `web_override` INTEGER + `grounded_sources` table with FK CASCADE to `messages`), new `GroundedSourceDao` following existing DAO conventions, row-id-returning save path, ephemeral details hydration on `loadConversation`, `SourcePreviewSheet` copied from the `ModelSelector` sheet pattern with sheet state held locally in `MessageBubble`, and a pure-Kotlin precedence function (`oneOff > perChat > global`) covered by JVM unit tests plus an extended `MigrationTest`.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Tap a Fuentes item opens preview — same `ModalBottomSheet` pattern as `ModelSelector` (`skipPartiallyExpanded`)
- Content: source number + URL + extracted page text (scrollable), no re-fetch — reads persisted rows created in this phase
- "Abrir en navegador" button fires `ACTION_VIEW` intent with the resolved post-redirect URL
- Dismiss via swipe/button only, no state change — preview never edits chat or sources
- New `grounded_sources` table (id, message_id FK, index, resolved url, extracted_text, status ok/omitida) — part of the single v15 migration
- Rows scoped to the assistant message that used them; history reload re-renders Fuentes + preview from rows
- `ChatMessage.groundedSources` stays an ephemeral render list; repository hydrates from the table on load (extend `EntityMappers`, same drop-unknown pattern as siblings)
- Rows deleted with conversation cascade; no LRU/TTL in v2.3 (TUNE-02 trigger-gated to v2.4)
- Tri-state `conversations.web_override` NULL/0/1 (null = inherit global default-ON) — same v15 migration
- Placement: conversation header/menu toggle (on/off/inherit) + composer "Sin web" one-off chip that skips grounding for that send only
- Precedence at send time in `ChatViewModel` hook: one-off "Sin web" > per-chat override > global setting
- Spanish labels (Web: Sí/No/Heredar, Sin web); toggle change never refetches history, applies to next send
- One migration `MIGRATION_14_15`: `web_override` column + `grounded_sources` table together — single auto-tested migration, no multi-step
- Write timing: grounding hook persists source rows right after fetch, before inference — Phase 54 retry reuses the same rows
- Fuentes upgrade: items become clickable (tap → sheet), order == fetch block order; omitida rows shown struck/disabled without preview text
- Exit gates: v14→v15 migration test, restart-persistence test (toggle + sources survive), preview-open + browser-intent test
- Bottom sheet shows extracted text already on device — never re-fetches on open (offline-safe preview)
- Omitida sources visible but struck/disabled — no silent drops, consistent with Phase 52 chip honesty
- One migration only — v15 carries both DDLs so a single auto-migration test gates the phase

### the agent's Discretion
- Exact sheet layout (header rows, paddings) within existing 4/8/16dp scale and UI-SPEC contract
- DAO/query shapes for sources-by-message and override read/write — follow existing DAO conventions
- Menu vs header placement detail for the tri-state control — cheapest consistent with existing conversation surfaces

### Deferred Ideas (OUT OF SCOPE)
- None — discussion stayed within phase scope. Global out-of-scope guardrails reaffirmed (no citation pills, no WorkManager retry — Phase 54 is message-scoped foreground, no provider interface changes).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SRC-01 | Tap a source to preview extracted text in a bottom sheet without leaving chat | `SourcePreviewSheet` pattern (ModelSelector copy), hydrated details list, offline-safe read from Room |
| SRC-02 | Numbered Fuentes list covering all N fetched sources | Clickable ok items + struck omitida items in fetch-block order from hydrated details (ok + omitida, not just okUrls) |
| SRC-03 | Open full page in browser from preview ("Abrir en navegador") | `ACTION_VIEW` with resolved URL, ActivityNotFoundException guard |
| TOGGLE-01 | Per-conversation tri-state override (on/off/inherit-global, null = follow global default-ON) | `web_override INTEGER NULL` column, domain `Boolean?`, DAO get/set, precedence function |
| TOGGLE-02 | One-off model-only send from composer ("Sin web") without changing toggles | `ChatInputState` one-shot flag, reset after send, top of precedence chain |
| TOGGLE-03 | Per-chat preference + persisted sources survive restarts (combined v15 migration) | `MIGRATION_14_15` + MigrationTest extension + repository hydration on load |
</phase_requirements>

## Project Constraints (from AGENTS.md)

- **Language:** Kotlin only (no Java) — all new files (entity, DAO, sheet, tests) in Kotlin.
- **Stack:** Jetpack Compose (M3), Hilt DI, Room, DataStore — use existing DI modules; new DAO needs a `@Provides` in `DatabaseModule`.
- **Architecture:** Clean architecture, MVVM, repository pattern — sources persistence goes through `ChatRepository(Impl)`, never DAO-from-ViewModel.
- **Security:** API keys via Keystore; no plaintext secrets — N/A here, but extracted text at rest inherits SQLCipher DB encryption (verified: `DatabaseModule` uses `SupportFactory(passphrase)`).
- **Offline-first:** Preview reads persisted rows, never re-fetches — matches CONTEXT offline-safe requirement.
- **Performance:** Never block UI thread during inference/downloads — DB writes on IO dispatcher inside the turn coroutine; sheet opens synchronously from already-hydrated state (no loader).
- **GSD workflow:** Work goes through GSD commands; plans handle their own commits.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Sources preview sheet + Fuentes list | UI (Compose) | — | Pure render of already-hydrated state; no data fetching on open |
| Browser open (ACTION_VIEW) | UI (Android framework) | — | Fire-and-forget intent from sheet; system handles the rest |
| Tri-state control + Sin web chip | UI (Compose) + ViewModel | — | Chip/toggle write ViewModel state; precedence evaluated at send time |
| Source rows persist + hydrate | Data (Room repository) | — | `grounded_sources` table owned by data layer; ViewModel calls repository |
| Grounding skip decision | ViewModel (turn logic) | — | Precedence evaluated in `sendMessage` hook before fetch |
| Fetch + extraction | Data (grounding core, Phase 52) | — | Untouched; Phase 53 only consumes its snapshot |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Room (`androidx.room`) | 2.7.x (existing) | `grounded_sources` entity + DAO, v14→v15 migration | Already the structured-data layer; KSP annotation processing; schema export on |
| Compose Material 3 (`ModalBottomSheet`, `rememberModalBottomSheetState`) | via Compose BOM (existing) | `SourcePreviewSheet` | Exact `ModelSelector` precedent in-repo (`ModelSelector.kt:55-57`) |
| SQLCipher (`net.sqlcipher`) | existing via `DatabaseModule` | At-rest encryption of new table | New table inherits it for free — no action needed |
| JUnit 5 + Truth + MockK + `runTest` | existing test stack | Precedence + hydration unit tests | `MultiUrlFetcherTest` is the in-repo template (mockk fetcher, Unconfined dispatcher) |
| `MigrationTestHelper` (androidTest) | existing | v14→v15 migration test | `MigrationTest.kt` template; schemas `13.json`/`14.json` present, `15.json` generated at build |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Timber | existing | Log persist failures (non-blocking Snackbar path) | On source-row insert failure |
| DataStore (`AdvancedPreferences.webGroundingEnabled`) | existing | Global default-ON leg of precedence | Read as today; no change |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| New `grounded_sources` table | New columns on `messages` (JSON blob) | Rejected per CONTEXT: query-by-message + index + cascade is cleaner; JSON blob would need manual parsing and couldn't index |
| Nullable `web_override` INTEGER | DataStore per-conversation key | Rejected: override must cascade with conversation delete and survive alongside the conversation row; DB column does both |
| `ACTION_VIEW` intent | Custom in-app WebView | Rejected: WebView adds attack surface and maintenance; system browser is the CONTEXT-locked choice |

**Installation:** None — zero new dependencies (consistent with v2.2 "zero new deps" posture; Jsoup already landed in Phase 52).

**Version verification:** No registry check needed — no new packages installed.

## Package Legitimacy Audit

No external packages installed in this phase. Skipped per protocol.

## Architecture Patterns

### System Architecture Diagram

```
User message with URLs
        │
        ▼
ChatViewModel.sendMessage()
  │ ① precedence: skipWebOnce? perChatOverride? global DataStore?
  │    (skip → model-only path, no fetch)
  ▼
MultiUrlFetcher.fetchAll()  (Phase 52, untouched)
  │ ② snapshot: ok (resolvedUrl + text) × N, skipped × M
  ▼
persist source rows ──► grounded_sources(message_id=?, …)
  │ ③ post-fetch, pre-inference (assistant row id resolved at save time)
  ▼
inference (providers untouched)
        │
        ▼
assistant save ──► messages row + grounded_sources rows (same turn)
        │
        ▼
History reload: loadConversation() hydrates ChatMessage
  (+ groundedSources urls + details) from grounded_sources
        │
        ▼
MessageBubble Fuentes list (ok clickable / omitida struck)
  │ tap ok item
  ▼
SourcePreviewSheet (persisted text, no re-fetch)
  │ "Abrir en navegador"
  ▼
ACTION_VIEW(resolvedUrl) → system browser
```

### Recommended Project Structure

```
data/local/db/
├── AppDatabase.kt              # version 14 → 15, + GroundedSourceEntity + dao accessor
├── Migrations.kt               # + MIGRATION_14_15 (both DDLs)
├── dao/
│   ├── GroundedSourceDao.kt    # NEW: insertAll, getByMessage, deleteByConversation
│   ├── ConversationDao.kt      # + getWebOverride / setWebOverride
│   └── MessageDao.kt           # unchanged
├── entity/
│   ├── GroundedSourceEntity.kt # NEW
│   ├── ConversationEntity.kt   # + webOverride: Boolean? (nullable)
│   └── EntityMappers.kt        # + sources ↔ domain, override mapping
domain/model/
├── ChatMessage.kt              # + groundedSourceDetails: List<GroundedSource> (ephemeral)
├── GroundedSource.kt           # NEW: url, extractedText, status(ok/omitida)
└── Conversation.kt             # + webOverride: Boolean?
ui/chat/
├── ChatViewModel.kt            # precedence + persist timing + skipWebOnce + override accessors
├── ChatUiState.kt              # ChatInputState.skipWebOnce; connection/transcript override snapshot
├── ChatScreen.kt               # tri-state control + Sin web chip wiring + snackbar copy
└── components/
    ├── SourcePreviewSheet.kt   # NEW (ModelSelector sheet pattern copy)
    ├── MessageBubble.kt        # clickable Fuentes + sheet host
    └── ChatInputBar.kt         # + Sin web chip params
```

### Pattern 1: Migration carrying column + table together

**What:** A single `Migration(14, 15)` executing `ALTER TABLE conversations ADD COLUMN web_override INTEGER` (nullable, no default → NULL = inherit) followed by `CREATE TABLE IF NOT EXISTS grounded_sources (...)` + index. Registered in `DatabaseModule.addMigrations(...)` alongside the existing chain; `AppDatabase` version bumped to 15; `exportSchema = true` emits `15.json` at build.
**When to use:** This phase — CONTEXT-locked single migration.
**Example:**
```kotlin
// Source: codebase convention (Migrations.kt MIGRATION_11_12 table-create + MIGRATION_4_5 ALTER COLUMN)
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversations ADD COLUMN web_override INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `grounded_sources` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`message_id` INTEGER NOT NULL, " +
                "`source_index` INTEGER NOT NULL, " +
                "`resolved_url` TEXT NOT NULL, " +
                "`extracted_text` TEXT, " +
                "`status` TEXT NOT NULL, " +
                "FOREIGN KEY(`message_id`) REFERENCES `messages`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_grounded_sources_message_id` " +
                "ON `grounded_sources` (`message_id`)"
        )
    }
}
```
**Table-shape rationale (verified against codebase):** `MessageEntity` uses `id INTEGER PRIMARY KEY AUTOINCREMENT`, snake_case `@ColumnInfo` names, and `ForeignKey(... onDelete = CASCADE)` to `conversations` — the new entity mirrors all three so conversation delete cascades messages → sources transitively with no manual cleanup. `extracted_text` is **nullable** (omitida rows carry no text; empty-extract ok rows render the UI-SPEC empty copy). `status` is TEXT (`"ok"`/`"omitida"`) following the existing `role`-as-TEXT precedent with `toRoleSafe`-style drop-unknown mapping on read.

### Pattern 2: Row-id-returning save for source scoping

**What:** `MessageDao.insert()` already returns `Long` (the Room row id), but `ChatRepository.saveMessage()` collapses it to `Unit`. The turn must capture the assistant message's row id to key source rows (`message_id` FK). Change the repository contract so the caller learns the row id (return `Long` from `saveMessage`, or add a dedicated `saveMessageWithSources(conversationId, message, sources)` that inserts both in one DAO-level call).
**When to use:** Required by the CONTEXT-locked "rows scoped to the assistant message" + "persist right after fetch, before inference" decisions.
**Key subtlety (verified in `EntityMappers.kt:40-49`):** `ChatMessage.toEntity()` does **not** carry `ChatMessage.id` (a UUID string) into `MessageEntity` (auto-generate `Long`) — the domain id and the DB row id are unrelated. Source rows MUST key on the returned insert row id, never on `ChatMessage.id`. Because the assistant `ChatMessage` object only exists after inference `Done`, the practical timing is: hold the fetch snapshot (resolvedUrl + sanitized text + ok/omitida per pasted URL, in block order) in turn-local vars, then write rows immediately around the assistant `saveMessage` (same coroutine, IO dispatcher) — still pre-any-retry and invisible to the user, satisfying "post-fetch, pre-inference-visibility" while keying correctly. Phase 54 retry then reads the same rows.

### Pattern 3: Ephemeral hydration with drop-unknown mapping

**What:** `loadConversation()` joins messages with their source rows and attaches two ephemeral lists to each `ChatMessage`: existing `groundedSources` (URLs, now ok **and** omitida so SRC-02 covers all N) plus new `groundedSourceDetails: List<GroundedSource>` (url + text + status for sheet + struck rendering). Unknown `status` strings map to a safe default (omitida → struck, never crash), mirroring the `toRoleSafe()` precedent (`EntityMappers.kt:21-25`).
**When to use:** History reload and post-restart render — the only path that repopulates sheet content.
**Efficiency note:** Per-message `getByMessage()` in a loop is N+1 queries; acceptable for conversation-sized histories (same scale as current per-message mapping), but a single `WHERE message_id IN (...)` batch query is cheap insurance — planner's call.

### Pattern 4: Pure-Kotlin precedence function

**What:** Extract the skip decision as a pure function unit-testable on JVM with no Android imports:
```kotlin
// (illustrative — planner names it)
fun shouldGround(skipOnce: Boolean, perChat: Boolean?, global: Boolean): Boolean =
    !(skipOnce || (perChat ?: global).not())
```
i.e. one-off "Sin web" wins → per-chat non-null wins → else global default-ON. `ChatViewModel` evaluates it at the top of the grounding hook (replacing the bare `if (webGroundingEnabled)` at `ChatViewModel.kt:282`); the one-shot flag resets after the send regardless of outcome.
**When to use:** TOGGLE-01/02 logic + JVM tests.

### Pattern 5: Sheet hosted in MessageBubble (local UI state, no ViewModel)

**What:** `MessageBubble` (already per-message) holds `var previewSource: GroundedSource? by remember` and renders `SourcePreviewSheet(source = previewSource, onDismiss = { previewSource = null })` when non-null. Copy `ModelSelectorSheet` structure: `if (!visible) return` guard → `rememberModalBottomSheetState(skipPartiallyExpanded = true)` → `ModalBottomSheet(containerColor = surface)` → 16dp/8dp padding column. Sheet content comes exclusively from the hydrated `ChatMessage` — the sheet itself performs zero I/O, so it opens synchronously with no spinner (UI-SPEC loading contract).
**When to use:** SRC-01 sheet + SRC-02 clickable items. Omitida items render `onSurfaceVariant` + `LineThrough` + `enabled = false`, no ripple — never open the sheet.

### Anti-Patterns to Avoid
- **DAO calls from ViewModel/Composable:** All source/override I/O goes through `ChatRepository(Impl)` (clean-architecture constraint; `ChatViewModel` already injects only repositories + fetchers).
- **Re-fetching on sheet open:** Sheet reads hydrated state only. Any `fetcher.fetch()` call in sheet code is a defect (breaks offline-safe contract).
- **Keying sources on `ChatMessage.id`:** UUID string ≠ Room row id; always use the insert return value.
- **Non-null `web_override` with default:** Would destroy the inherit leg of the tri-state. Column must stay nullable; `null = follow global`.
- **`fallbackToDestructiveMigration(true)` or skipping `addMigrations` registration:** Existing module pins `fallbackToDestructiveMigration(false)` — an unregistered v15 upgrade would crash on launch instead of migrating. Register `MIGRATION_14_15` in `DatabaseModule` in the same change as the version bump.
- **Persisting `groundedSources` into `MessageEntity` columns:** CONTEXT-locked ephemeral; the table is the store.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Bottom sheet | Custom slide-up layout/gestures | M3 `ModalBottomSheet` + `rememberModalBottomSheetState` | Drag handle, swipe-dismiss, scrim, a11y, and full-expanded behavior free; in-repo precedent |
| Browser open | In-app WebView or Custom Tabs wrapper | `ACTION_VIEW` intent with resolved URL | System handles it; no new attack surface; CONTEXT-locked |
| Cascade delete | Manual source cleanup on conversation delete | Room `ForeignKey CASCADE` (messages→sources, conversations→messages) | Transitive delete verified by FK chain; manual deletes race with concurrent turns |
| Migration validation | Hand-written schema diff | `MigrationTestHelper.runMigrationsAndValidate` + exported schemas | Catches column/table drift automatically; template exists |
| Tri-state persistence | Sentinel ints (-1/0/1) or strings | Nullable `INTEGER` (null/0/1) ↔ `Boolean?` | Null literally means "no opinion → inherit"; mapper is two lines |
| URL resolution | Custom redirect follower for the intent | `GroundingResult.Grounded.url` (already post-redirect, scheme-checked http/https in `WebPageFetcher`) | Re-resolving at intent time reintroduces SSRF-adjacent risk the fetcher already closed |

**Key insight:** Every hard problem in this phase (redirect resolution, truncation marking, fan-out ordering) was already solved in Phase 52 — Phase 53 is a persistence + render layer over that snapshot. The plan should treat `MultiUrlFetcher`/`WebPageFetcher` as sealed inputs.

## Common Pitfalls

### Pitfall 1: Source rows orphaned by REPLACE-conflict message re-insert
**What goes wrong:** `MessageDao.insert` uses `OnConflictStrategy.REPLACE`; a re-insert of the same row id deletes + re-inserts the message, and FK CASCADE deletes its source rows.
**Why it happens:** Normal flow inserts each message once, but retry/edit paths that re-save an assistant message silently wipe its sources.
**How to avoid:** Insert sources strictly after the final assistant insert in the turn; Phase 54 retry must READ rows, never re-save the assistant message. Note in plan as a Phase 54 interface warning.
**Warning signs:** Preview works pre-restart (in-memory list) but vanishes post-restart (rows gone).

### Pitfall 2: Omitida URLs lost because only `okUrls` are stored
**What goes wrong:** `MultiUrlResult.Fused` carries `okUrls` + `skippedUrls` separately and today's `groundedSources = result.okUrls` drops the skipped set — SRC-02's "covers all N" list and struck rendering need both.
**Why it happens:** Phase 52 only needed ok URLs for the prompt block.
**How to avoid:** Build source rows from the union in fetch-block order: the terminal `perSource` snapshot (url → OK/OMITIDA, `ChatViewModel.kt:326-342`) already computes exactly this — persist text for OK rows, `extracted_text = null, status = omitida` for skipped. Texts come from the `Grounded.text` values zipped by index (note: `Fused` currently drops per-page texts — the planner must thread texts or the resolved-url→text pairs through; `fetcher.fetch` returns them, only the `Fused` carrier omits them).
**Warning signs:** Fuentes list shows fewer items than the `[WEB CONTEXT 1..N]` block count.

### Pitfall 3: MigrationTestHelper validate fails on missing 15.json
**What goes wrong:** `runMigrationsAndValidate(dbName, 15, true, ...)` requires the exported schema for v15; a clean checkout without a prior build has no `15.json`.
**Why it happens:** `exportSchema = true` generates schemas at compile time (`app/schemas/.../13.json`, `14.json` present today).
**How to avoid:** Plan orders a full `:app:assembleDebug` (or ksp) before running the androidTest migration test; CI builds first anyway. Precedent documented in `Migrations.kt:63-65` for the 11/12 manual-migration switch.
**Warning signs:** `IllegalStateException: ... schema ... not found` in androidTest.

### Pitfall 4: Sheet state inside lazy-list item scope causes lost state on scroll
**What goes wrong:** `remember` inside a lazily-composed item is discarded when the item scrolls out of the viewport composition; an open sheet tied to that scope can dismiss or leak on fling.
**Why it happens:** Message list is a LazyColumn; `MessageBubble` instances come and go.
**How to avoid:** Hoist the minimum: keep `previewSource` in `MessageBubble` (acceptable — sheet dismisses on scroll-away, which matches "dismiss via swipe/button only, no state change"), but never hold the hydrated source *data* in the remember — always read it from the `message` param so re-composition re-resolves. Do NOT lift sheet data into the ViewModel (would violate the transient-UI-state precedent of `webFetchProgress`).
**Warning signs:** Sheet shows stale text after history reload while open.

### Pitfall 5: ACTION_VIEW with unvalidated URL crashes (ActivityNotFoundException)
**What goes wrong:** `startActivity` with a URL no app handles throws, crashing chat.
**Why it happens:** No existing `ACTION_VIEW` precedent in the codebase (grep: zero hits) — easy to write the happy path only.
**How to avoid:** Wrap in try/catch, fall back to the persistence-failure-style Snackbar. Only ever pass `Grounded.url` (fetcher guarantees http/https post-redirect) — never raw user-pasted text. [ASSUMED: standard Android guard pattern — see Assumptions Log A1.]
**Warning signs:** Crash reports on emulator images without a browser.

### Pitfall 6: Toggle change re-triggers fetch or re-renders history as side effect
**What goes wrong:** Wiring the override write through a collector that also drives `webGroundingEnabled` snapshot causes mid-turn behavior flips or redundant recomposition storms.
**Why it happens:** `webGroundingEnabled` is a `@Volatile var` collected from DataStore; adding a second collector (per-conversation override) invites coupling.
**How to avoid:** Read the override once at send time (suspend DAO `getById`/`getWebOverride` for the active conversation id), evaluate the pure precedence function, never observe it as a hot Flow in the turn path. Toggle write = DAO update + Snackbar only.
**Warning signs:** Toggle flips mid-fetch and the turn both fetches and shows model-only banner.

## Code Examples

Verified patterns from the codebase (all paths below were read this session):

### Bottom sheet scaffold (copy target)
```kotlin
// Source: app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt:44-61
if (!visible) return
val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface
) { /* header (16dp pad) → divider → scrollable body → sticky action row */ }
```
Sheet body scroll per UI-SPEC: `Column(Modifier.verticalScroll(rememberScrollState()))`; action row uses `FilledTonalButton` ("Abrir en navegador"); header URL single-line ellipsis; number badge `Label 12sp semibold`.

### Clickable Fuentes item (upgrade of MessageBubble.kt:214-221)
```kotlin
// Source pattern: existing non-clickable Text at MessageBubble.kt:214-221 becomes:
Text(
    text = "[$n] $url",
    fontSize = 14.sp,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier
        .clip(MaterialTheme.shapes.small)
        .clickable(
            onClick = { previewSource = details },
            role = Role.Button, // [ASSUMED: androidx.compose.ui.semantics.Role import — see A2]
        )
        .padding(vertical = 6.dp) // reach 44dp touch target incl. 4dp gaps
        .semantics { contentDescription = "Vista previa de la fuente $n" },
)
// Omitida: same Text with color = onSurfaceVariant,
// style copy(textDecoration = TextDecoration.LineThrough), NO clickable modifier.
```

### DAO shapes (follow existing conventions)
```kotlin
// Source: ConversationDao.kt / MessageDao.kt (@Dao, @Query, suspend, Flow)
@Dao
interface GroundedSourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<GroundedSourceEntity>)

    @Query("SELECT * FROM grounded_sources WHERE message_id = :messageId ORDER BY source_index ASC")
    suspend fun getByMessage(messageId: Long): List<GroundedSourceEntity>
}
// ConversationDao additions:
@Query("SELECT web_override FROM conversations WHERE id = :id")
suspend fun getWebOverride(id: Long): Boolean?
@Query("UPDATE conversations SET web_override = :override, updated_at = :ts WHERE id = :id")
suspend fun setWebOverride(id: Long, override: Boolean?, ts: Long)
```
Note: Room maps nullable INTEGER ↔ `Boolean?` natively (0/1/null). [CITED: Room is the existing structured-data layer per STACK.md §3; Boolean↔INTEGER affinity is long-standing Room behavior — planner acceptance test is the migration test, not docs.]

### Repository hydration point
```kotlin
// Source: ChatRepositoryImpl.kt:26-30 — extend loadConversation:
override suspend fun loadConversation(conversationId: Long): Pair<Conversation, List<ChatMessage>>? {
    val conv = conversationDao.getById(conversationId) ?: return null
    val msgs = messageDao.getByConversation(conversationId)
    // NEW: batch-fetch sources for assistant message row ids, attach via mappers
    ...
    return conv.toDomain() to msgs.map { it.toDomain() }
}
```

### Test templates
- **Precedence (JVM, JUnit5):** pure function test, `Truth.assertThat`, table of (skipOnce, perChat, global → expected) — 8 rows cover the full truth table. Template: `MultiUrlFetcherTest` (`runTest`, no Android imports).
- **Migration (androidTest):** extend `MigrationTest` — `helper.createDatabase(dbName, 14)` + seed conversation/messages, `runMigrationsAndValidate(dbName, 15, true, MIGRATION_14_15)`, assert rows survive + `web_override` defaults NULL + `grounded_sources` empty + insert-then-cascade-delete check.
- **UI-state (JVM):** preview selection is local `remember` state — covered by existing Compose UI-test infra if asserted; at minimum assert Fuentes click mapping (ok → details non-null, omitida → null) as a pure mapper test. Browser intent itself is not JVM-assertable — gate via code review + device smoke (WEB-06 precedent).

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Single-URL `WebPageFetcher.fetch` + regex extract | `MultiUrlFetcher` fan-out + Jsoup parse-only + `GroundingBudget` split | Phase 52 (this milestone) | Phase 53 consumes the snapshot; never touches fetch internals |
| `groundedSources = okUrls` only | Persist ok + omitida rows in block order | This phase (Pitfall 2) | Fuentes covers all N; omitida struck |
| AutoMigration 11→12/12→13 | Manual `Migration` objects (schema dir not versioned) | Pre-Phase 52 (`Migrations.kt:63-65`) | v14→v15 MUST be manual — same file, same pattern |
| Global-only grounding toggle (DataStore) | Global + per-chat NULL/0/1 + one-off chip | This phase | Precedence function is the single decision point |

**Deprecated/outdated:**
- kapt annotation processing: KSP only (Room/Hilt) — new entity/DAO needs no build-file change beyond existing KSP setup.
- `SharedPreferences` / `LiveData`: never introduce; DataStore + Flow precedent stands.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `ACTION_VIEW` + `ActivityNotFoundException` guard is the correct browser-open pattern (zero in-repo precedent; standard Android API) | Pitfalls §5, Code Examples | LOW — worst case is a missing catch; device smoke (exit gate) catches it |
| A2 | `Modifier.clickable(role = Role.Button)` + 44dp touch target via padding satisfies the UI-SPEC a11y exception | Code Examples | LOW — visual/a11y check on device confirms |
| A3 | Room maps nullable INTEGER column to Kotlin `Boolean?` without a TypeConverter | DAO shapes | LOW — migration test + DAO read-back test verify directly; fallback is Int? + manual map |
| A4 | `Fused` needs a texts-carrying extension (or equivalent) since it currently drops per-page `Grounded.text` | Pitfalls §2 | MEDIUM — if texts are recoverable another way (e.g. re-split of `block`), less code; either way planner must close the gap or sheet bodies are empty |
| A5 | Conversation delete already cascades messages via FK (verified: `MessageEntity` CASCADE) so sources cascade transitively | Pattern 1 | LOW — cascade-delete assertion in migration test proves it |

**If this table is empty:** n/a — 5 assumed claims above need confirmation via tests/device smoke, none block planning.

## Open Questions

1. **Tri-state control placement (agent's discretion)**
   - What we know: `ChatScreen` has NO TopAppBar (removed CHAT-02); surfaces available are `InlineModelSelectorBar`, the drawer (`ConversationList`), and `ChatInputBar`. Toggle must show Sí/No/Heredar + inherit hint reflecting the live global setting.
   - What's unclear: Which surface is cheapest without cluttering the composer.
   - Recommendation: Rank (1) overflow menu on the inline model-selector bar — conversation-scoped, one tap away, no composer change; (2) long-press/context row in the conversation drawer; (3) new header row. Planner picks (1) unless it conflicts with the pending-model-switch dialog also anchored there.

2. **Sin web chip visual anchor**
   - What we know: `ChatInputBar` already hosts the reasoning toggle chip precedent (`reasoningEnabled`/`onToggleReasoning`, active = `primary.copy(alpha=0.5f)`); UI-SPEC says active "Sin web" uses accent, inactive uses default chip colors.
   - What's unclear: Exact row (above input vs inline with send row).
   - Recommendation: Inline next to the reasoning chip — same component idiom, zero new layout scaffolding.

3. **New-conversation (no id yet) override behavior**
   - What we know: `ensureConversation` creates the row lazily at first send; override reads need a conversation id.
   - What's unclear: Whether the tri-state control is visible before the first message (no row to write to).
   - Recommendation: Before first send, control edits a ViewModel-held pending value applied at `createConversation`; after row exists, direct DAO writes. Or hide control until `conversationId != null`. Planner decides; either is < 1 task.

## Environment Availability

No external dependencies — pure Room + Compose + intent work with the existing toolchain. `MigrationTest` runs under `androidTest` (needs device/emulator, same as today).

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Room KSP + `MigrationTestHelper` | v15 migration test | ✓ (in-repo, `MigrationTest.kt` present) | existing | — |
| M3 `ModalBottomSheet` | SourcePreviewSheet | ✓ (in-repo, `ModelSelector.kt`) | via BOM | — |
| System browser (device) | SRC-03 smoke | device-dependent | — | `ActivityNotFoundException` → Snackbar |
| New Gradle artifacts | — | n/a | — | Zero new deps |

**Missing dependencies with no fallback:** None.
**Missing dependencies with fallback:** System browser on bare emulators (Snackbar fallback per Pitfall 5).

## Security Domain

`security_enforcement` is not disabled in `.planning/config.json` (key absent → enabled); `nyquist_validation` is explicitly `false` → Validation Architecture section omitted.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | No auth in this phase |
| V3 Session Management | No | No sessions |
| V4 Access Control | Partial | Source rows scoped by `message_id` FK; queries always bind ids via Room (no string-concat SQL) |
| V5 Input Validation | Yes | `status` TEXT mapped with drop-unknown default (omitida-safe); `extracted_text` nullable handled; intent URL restricted to fetcher-resolved http/https — never raw user text |
| V6 Cryptography | Yes (inherit) | SQLCipher DB encryption covers the new table via `DatabaseModule`; no new crypto code — never hand-roll |

### Known Threat Patterns for Kotlin/Room/Compose stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Malicious redirect / non-http scheme in opened URL | Tampering | Only `Grounded.url` (fetcher enforces http/https + max 3 redirects) reaches `ACTION_VIEW` |
| Stored-HTML injection rendered in sheet | Tampering/XSS-analog | Sheet renders `sanitized` extracted plain text (already through `WebContextSanitizer` + Jsoup text extraction); never raw HTML |
| DB downgrade / destructive migration wiping history | Availability | `fallbackToDestructiveMigration(false)` + registered `MIGRATION_14_15`; migration test gates |
| Extracted-text exfiltration via browser intent | Information disclosure | Intent carries only the URL, never the extracted text |

## Sources

### Primary (HIGH confidence)
- Codebase reads this session: `AppDatabase.kt` (v14, 7 entities), `Migrations.kt` (MIGRATION_4_5…13_14 chain + manual-migration rationale), `MessageEntity.kt` (FK CASCADE, composite index), `ConversationEntity.kt` (8 cols, no override yet), `EntityMappers.kt` (`toRoleSafe` precedent, id-dropping `toEntity`), `ChatMessage.kt` (ephemeral `groundedSources`), `ChatViewModel.kt` (hook at :272-356, snapshot vars, terminal OK/OMITIDA pass :326-342, assistant save :502-521), `ChatUiState.kt` (transient progress, single-owner sub-states), `ChatScreen.kt` (no TopAppBar, chip at :241-277, `ChatInputBar` call :278-300), `ChatInputBar.kt` (reasoning-chip precedent), `ModelSelector.kt:44-61` (sheet pattern), `MessageBubble.kt:202-222` (Fuentes list), `MultiUrlFetcher.kt` (`Fused` shape — texts dropped), `WebPageFetcher.kt:87-173` (resolved URL + scheme check), `GroundingBudget.kt`, `GroundingPrompt.kt`, `ChatRepositoryImpl.kt` (`saveMessage` → Unit), `DatabaseModule.kt` (SQLCipher + migration registration), `AdvancedPreferences.kt` (global default-ON), `MigrationTest.kt` (androidTest template), `MultiUrlFetcherTest.kt` (JVM template), `.planning/config.json` (`nyquist_validation: false`)

### Secondary (MEDIUM confidence)
- Phase 52 artifacts (`52-CONTEXT/RESEARCH/REVIEW`) referenced via CONTEXT.md code-context pointers (not re-read; Phase 52 dir listing confirmed present)

### Tertiary (LOW confidence)
- A1–A3 standard-Android/Room behavior claims (marked `[ASSUMED]`, verified by tests not docs)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — everything already in-repo, zero new deps, patterns copied from adjacent files.
- Architecture: HIGH — migration shape, DAO conventions, sheet pattern, and hook insertion point all verified by direct reads; two design deltas (row-id plumbing, details list) are explicit and small.
- Pitfalls: HIGH — Pitfalls 1–3, 6 derived from code actually read; 4–5 are standard Android risks flagged honestly.

**Research date:** 2026-09-28
**Valid until:** 2026-10-28 (stable domain — Room/Compose APIs; Phase 54 consumes the rows, so revisit if retry needs new columns)
