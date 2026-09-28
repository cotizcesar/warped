# Architecture Research: Web Grounding v2 (multi-URL, previews, per-chat toggle, offline retry)

**Domain:** Android LLM chat app (Warped) — grounding subsystem extension
**Researched:** 2026-09-28
**Confidence:** HIGH (all claims verified against the live codebase in `app/src/main/java/com/warped/`)

## Standard Architecture

### System Overview — grounding v1 as built (v2.2, Phase 50)

```
┌─────────────────────────────────────────────────────────────────┐
│  UI layer (Compose + ChatViewModel)                              │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────────┐   │
│  │ ChatScreen   │  │ MessageBubble│  │ SettingsScreen/toggle │   │
│  │ fetch chip   │  │ Fuentes list │  │ (global default-ON)   │   │
│  │ (isFetching- │  │ + ModelOnly  │  │                       │   │
│  │  Web)        │  │ Banner       │  │                       │   │
│  └──────┬───────┘  └──────┬───────┘  └───────────┬───────────┘   │
│         │ StateFlow       │ ephemeral fields     │ DataStore     │
├─────────┴─────────────────┴──────────────────────┴──────────────┤
│  Orchestration (ChatViewModel.sendMessage, lines 269–297)        │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ detect → fetch → augment (sequential, inside turn scope)  │  │
│  └──────┬──────────────┬───────────────┬──────────────────────┘  │
│         │              │               │                          │
├─────────┴──────────────┴───────────────┴──────────────────────────┤
│  Data layer — data/grounding/ (pure Kotlin + 1 Android class)     │
│  ┌──────────┐ ┌──────────────┐ ┌───────────────┐ ┌────────────┐  │
│  │UrlDetector│ │WebPageFetcher│ │HtmlToTextExtr.│ │Grounding-  │  │
│  │firstUrl() │ │fetch(url)    │ │extract() 4K   │ │Prompt+     │  │
│  │(JVM-pure) │ │(IO+OkHttp)   │ │(JVM-pure)     │ │Sanitizer   │  │
│  └──────────┘ └──────────────┘ └───────────────┘ └────────────┘  │
├──────────────────────────────────────────────────────────────────┤
│  Keystone + persistence (UNCHANGED by v2)                         │
│  ┌──────────────────┐  ┌──────────────┐  ┌──────────────────┐    │
│  │ LlmModelHelper   │  │ Room v14     │  │ AdvancedPrefs    │    │
│  │ runInference()   │  │ messages/    │  │ web_grounding_   │    │
│  │ (prompt in/out)  │  │ conversations│  │ enabled (global) │    │
│  └──────────────────┘  └──────────────┘  └──────────────────┘    │
└──────────────────────────────────────────────────────────────────┘
```

Key v1 invariants that v2 must preserve:
1. **Providers unchanged.** Grounding is prompt-prefix augmentation (`GroundingPrompt.augment`), applied to the current user turn's outgoing `ChatRequest` only. `LlmModelHelper.runInference` receives a longer string — no interface change, no helper change.
2. **History keeps originals.** Persisted `MessageEntity.content` is the user's raw text; the augmented text exists only in `requestMessages` (ChatViewModel lines 376–381). v2 must keep this: fused blocks never persist into history.
3. **`activeCall` cancel discipline.** `WebPageFetcher.cancel()` + `stopResponse()` + `generationJob.cancel()` run together in both the new-turn pre-cancel (lines 255–259) and `stopGeneration()` (lines 521–537). Multi-fetch must extend this to N calls, not bypass it.
4. **Auth isolation.** The fetcher's OkHttp client strips `AuthInterceptor` (WebPageFetcher lines 48–59) so endpoint API keys never leak to arbitrary fetched hosts. Any new fetch path (parallel client, WorkManager retry) must reuse this derived client or re-apply the strip.

### Component Responsibilities

| Component | Responsibility | Typical Implementation in this codebase |
|-----------|----------------|------------------------------------------|
| `UrlDetector` | URL extraction from user text | `object`, JVM-pure, regex; v1 = `firstUrl()` only |
| `WebPageFetcher` | Bounded network fetch + extract + sanitize + block build | `@Singleton`, `Dispatchers.IO`, manual redirects, 64 KB cap, single `activeCall` |
| `GroundingPrompt` | `[WEB CONTEXT]` block construction + prefix augmentation | `object`, JVM-pure; v1 single-source `buildBlock(url, text)` hardcodes `[1]` |
| `GroundingResult` | Fetch outcome type | `sealed interface`: `Grounded(block, url)` / `ModelOnly(reason)` |
| `ChatViewModel` turn hook | Detect→fetch→augment orchestration, toggle snapshot, state flags | Coroutine in `sendMessage`; `webGroundingEnabled` volatile snapshot from DataStore |
| `ChatInputState.isFetchingWeb` | Transient fetch indicator | Chip in `ChatScreen` bottomBar; never persisted |
| `ChatMessage.groundedSources/modelOnlyNotice` | Ephemeral per-message grounding state | Domain-only fields; `EntityMappers` maps field-by-field so they never reach Room |
| `AdvancedPreferences.webGroundingEnabled` | Global default-ON toggle | DataStore boolean, default `true` |

## Recommended Project Structure

```
app/src/main/java/com/warped/
├── data/grounding/              # EXTEND — stays the grounding home
│   ├── UrlDetector.kt           # MODIFY — firstUrl() → extractUrls(limit=5)
│   ├── WebPageFetcher.kt        # MODIFY — single Call → parallel fetchAll()
│   ├── GroundingOrchestrator.kt # NEW — parallel fan-out + fusion + budget (see below)
│   ├── GroundedSource.kt        # NEW — per-source value type (url, title, excerpt, status)
│   ├── GroundingPrompt.kt       # MODIFY — buildBlock() gains multi-source builder
│   ├── GroundingResult.kt       # MODIFY — Grounded gains List<GroundedSource>
│   ├── HtmlToTextExtractor.kt   # MODIFY only if research picks robust extractor
│   ├── WebContextSanitizer.kt   # REUSE as-is (per-source sanitize, unchanged)
│   └── GroundingRetryWorker.kt  # NEW — WorkManager offline-retry (or data/local/download/ neighbor)
├── data/local/db/
│   ├── entity/
│   │   ├── ConversationEntity.kt  # MODIFY — add web_grounding_mode column (v15 migration)
│   │   └── GroundedSourceEntity.kt# NEW — cached source previews (only if previews persist)
│   ├── dao/
│   │   └── GroundedSourceDao.kt   # NEW — only with the entity above
│   ├── AppDatabase.kt             # MODIFY — version 14 → 15, register entity
│   └── Migrations.kt              # MODIFY — MIGRATION_14_15 (ALTER + CREATE, manual style)
├── domain/model/
│   ├── ChatMessage.kt             # MODIFY — groundedSources: List<String> → List<GroundedSource>
│   └── Conversation.kt            # MODIFY — add webGroundingMode (per-chat override)
├── domain/repository/
│   └── GroundingRepository.kt     # NEW — interface: fetchParallel(), sourcesFor(), retryEnqueue()
├── ui/chat/
│   ├── ChatViewModel.kt           # MODIFY — turn hook calls orchestrator; per-chat toggle resolution
│   ├── ChatUiState.kt             # MODIFY — ChatInputState gains preview/selection state
│   ├── ChatScreen.kt              # MODIFY — per-chat toggle affordance + preview entry point
│   └── components/
│       ├── MessageBubble.kt       # MODIFY — Fuentes list becomes tappable preview rows
│       └── SourcePreviewSheet.kt  # NEW — ModalBottomSheet rendering cached extracted text
```

### Structure Rationale

- **`data/grounding/` stays the home.** v1 deliberately co-located all six files there with zero new dependencies. v2 adds two files (`GroundingOrchestrator`, `GroundedSource`) in the same package rather than a new top-level module — the pipeline is still one bounded subsystem, and Hilt bindings stay trivial (`@Singleton` orchestrator injecting the existing fetcher + OkHttp client).
- **Orchestrator, not ViewModel fan-out.** The parallel-fetch + fusion logic (per-URL timeout, truncation budgeting, partial-failure policy) is pure concurrency + string policy — unit-testable on the JVM. Putting `async` fan-out directly in `ChatViewModel.sendMessage` would entangle it with the turn's `generationJob` cancellation and make it untestable without Hilt. A `GroundingOrchestrator` with a `suspend fun ground(text, budget): GroundingResult` signature keeps the ViewModel hook a 10-line call site, mirroring how v1 isolated `HtmlToTextExtractor`/`GroundingPrompt` as pure objects.
- **Repository interface only if previews persist.** If source previews are served from the in-memory `GroundedSource` list carried on `ChatMessage` (ephemeral, like v1's `groundedSources`), no repository is needed. Introduce `GroundingRepository` + Room entity only when the decision is "previews survive process death / history reload" — that is the single branching decision that changes the schema surface (see Data Flow §2).
- **Migration style follows precedent.** `Migrations.kt` documents why manual `Migration(13,14)` replaced AutoMigration (missing `app/schemas/` JSONs break KSP). v15 must follow the manual pattern: `ALTER TABLE conversations ADD COLUMN web_grounding_mode …` + optional `CREATE TABLE grounded_sources`.

## Architectural Patterns

### Pattern 1: Bounded parallel fan-out with supervisor + per-source budget

**What:** Replace the sequential single `fetcher.fetch(url)` call with a `coroutineScope { urls.map { async { fetchOne(url) } }.awaitAll() }` fan-out inside the orchestrator, where each child has its own timeout derived from the v1 20 s call budget (e.g. 20 s wall-clock total via `withTimeout`, children share the scope so the first failure never cancels siblings — use `supervisorScope` or per-child `try/catch` returning a failure value, never throwing).
**When to use:** The multi-URL turn hook. This is the core v2 data-path change.
**Trade-offs:** + latency is max(slowest) not sum; partial success (2/3 pages) still grounds. − N concurrent sockets need a dispatcher cap (`Dispatchers.IO.limitedParallelism(3)` or a `Semaphore(3)`) so a 5-URL message on a slow radio doesn't saturate the pool; each child reuses the same stripped OkHttp client (connection pool shared, AuthInterceptor still stripped).

**Example:**
```kotlin
// GroundingOrchestrator (data/grounding/) — sketch, JVM-testable minus fetcher fake
suspend fun ground(text: String, maxUrls: Int = 5): GroundingResult = supervisorScope {
    val urls = UrlDetector.extractUrls(text, maxUrls)
    if (urls.isEmpty() || !toggleAllows()) return@supervisorScope ModelOnly(SKIPPED)
    val deferred = urls.map { url ->
        async(Dispatchers.IO) { runCatching { fetcher.fetchOne(url) }.getOrNull() }
    }
    val sources = withTimeoutOrNull(TOTAL_BUDGET_MS) { deferred.awaitAll().filterNotNull() }
        .orEmpty()
    if (sources.isEmpty()) ModelOnly(FETCH_FAILED)
    else Grounded(GroundingPrompt.buildFusedBlock(sources), sources)
}
```

### Pattern 2: Fuse-then-truncate with numbered citations (context fusion)

**What:** `GroundingPrompt` gains `buildFusedBlock(sources: List<GroundedSource>): String` emitting one `[WEB CONTEXT]` envelope containing per-source sections `[1] url + title + excerpt`, `[2] …`. The total fused budget is fixed (recommend: keep v1's ~4000-char extractor output per page, cap the fused block at ~8000 chars by trimming the *longest* excerpts first, never by dropping a cited source silently — if a source is cut, its marker must not appear in the citation list).
**When to use:** Always for multi-URL turns; single-URL turns should also route through the fused builder with one source so prompt shape is uniform (avoids two prompt formats drifting).
**Trade-offs:** + uniform prompt shape simplifies eval and the SYSTEM_PROMPT citation contract (`[1]/[2]` markers already exist in v1's SYSTEM_PROMPT). − naive concatenation blows the model's context window on small local models; the fusion budget must be subtracted from `GenerationParameters.contextSize` awareness (at minimum, document the worst-case prefix length; ideally clamp fused chars to a fraction of contextSize).

**Example:**
```kotlin
fun buildFusedBlock(sources: List<GroundedSource>): String = buildString {
    appendLine("[WEB CONTEXT — ${sources.size} fuentes]")
    sources.forEachIndexed { i, s ->
        appendLine("[${i + 1}] ${s.url} — ${s.title}")
        appendLine(WebContextSanitizer.sanitize(s.excerpt))
    }
    append("[FIN WEB CONTEXT]")
}
```

### Pattern 3: Toggle resolution chain — global default → per-chat override → per-message force

**What:** Three-level resolution computed at turn start: `effective = messageForce ?: conversationOverride ?: globalDefault`. Storage: global stays in `AdvancedPreferences.webGroundingEnabled` (DataStore, default ON — untouched); per-chat override is a new nullable column `web_grounding_mode` on `conversations` (`NULL` = inherit global, `0` = off, `1` = on); per-message force is a transient send-time flag (long-press send / input-bar toggle, never persisted).
**When to use:** The v2.3 "per-chat toggle" requirement.
**Trade-offs:** + nullable-column inherit pattern means existing conversations (all rows) automatically follow the global toggle with zero backfill; only explicit overrides write a value. − adds one Room migration (14→15) and a `Conversation` domain-field addition; the ViewModel must load the override when `selectConversation()` runs and clear it in `newConversation()`.

### Pattern 4: Offline retry as WorkManager one-shot with connectivity constraint (not in-turn blocking)

**What:** When the turn-time fetch short-circuits OFFLINE (existing `hasValidatedInternet()` check), the orchestrator records a pending retry record (URLs + conversationId + messageId) and enqueues a `OneTimeWorkRequest` with `Constraints(REQUIRED_NETWORK_TYPE = CONNECTED)`. The worker re-runs `fetchAll()`, writes the fused result into the source store, and posts a notification / updates the message's `modelOnlyNotice` → grounded state via `ChatRepository`. The *original turn still completes model-only immediately* — retry never blocks the chat response.
**When to use:** The v2.3 "offline retry queue" requirement.
**Trade-offs:** + follows the codebase's existing WorkManager precedent (`ModelDownloadWorker`, `ModelBenchmarkWorker` with `BenchmarkScheduler`); survives process death; OS-gated on connectivity so no polling. − WorkManager is for deferrable work: retry latency is minutes, not seconds — pair it with a lightweight in-UI "Reintentar" button on the ModelOnly banner for the immediate path (button calls the orchestrator directly, no worker). Both paths must converge on the same `ground()` entry point or behavior drifts.

## Data Flow

### Request Flow — v2 turn (modified v1 hook)

```
User taps Send (ChatScreen)
    ↓
ChatViewModel.sendMessage()
    ↓ resolve effective toggle (NEW: messageForce ?: conversationOverride ?: globalDataStore)
    ↓ ensureConversation() → conversationId (UNCHANGED)
    ↓ saveMessage(original user text) (UNCHANGED — history keeps originals)
    ↓ GroundingOrchestrator.ground(text) (MODIFIED hook, was fetch-single)
    │     ├─ UrlDetector.extractUrls(text, max 5) (MODIFIED: firstUrl → list)
    │     ├─ supervisorScope fan-out: N × WebPageFetcher.fetchOne (MODIFIED: activeCall → call registry)
    │     ├─ per-source extract (HtmlToTextExtractor, REUSE) + sanitize (REUSE)
    │     └─ GroundingPrompt.buildFusedBlock(sources) (MODIFIED builder)
    ↓ requestMessages = history.dropLast(1) + last.copy(augmented) (UNCHANGED shape)
    ↓ helper.initialize(modelId) → helper.runInference(request) (UNCHANGED — providers blind)
    ↓ Done → assistantMessage.copy(groundedSources = sources, modelOnlyNotice = …)
    │         (MODIFIED: sources now List<GroundedSource>, persisted per §2 decision)
    ↓ saveMessage(assistant) (UNCHANGED call, MODIFIED payload)
```

### State Management

```
ChatViewModel sub-states (48-01 single-owner discipline PRESERVED):
  _transcript ← turn boundaries, streaming collector, Done/Error (adds preview-selection? NO —
                preview sheet state lives in ChatScreen remember{}, not the VM)
  _input      ← isFetchingWeb (REUSE flag, now covers N-fetch window) + per-message force flag (NEW)
  _connection ← conversation list (REUSE) + per-chat override for active conversation (NEW field)
DataStore     ← global default (REUSE, untouched)
Room          ← conversations.web_grounding_mode (NEW col) + grounded_sources table (NEW, conditional)
WorkManager   ← retry queue (NEW worker, observed via WorkInfo → banner "reintentando" state)
```

Key state decisions:
1. **Preview content does NOT go through the ViewModel.** The `GroundedSource` list already rides on `ChatMessage` (in-memory after the domain-type change). Tapping a Fuentes row opens `SourcePreviewSheet` reading `message.groundedSources[i]` directly — no new StateFlow, no re-fetch. This respects the 48-01 single-owner rule (no new writer groups on `_transcript`).
2. **Preview persistence is the schema fork.** Option A (recommended): previews are ephemeral — history reload shows Fuentes URLs but preview re-fetches on demand (tap → `fetchOne(url)` live, with offline error). Zero new tables, zero migration beyond the toggle column. Option B: persist extracted excerpts in `grounded_sources` keyed by (url hash + fetched_at) with an LRU/TTL cap — enables offline previews and the retry worker's write path, at the cost of a new entity + DAO + eviction policy. The retry queue *needs* Option B's store (or equivalent) to land results; the preview UI alone does not.
3. **`isFetchingWeb` semantics widen, not split.** One boolean still covers "grounding in flight" for N fetches (chip copy changes to "Leyendo N páginas…"). Per-source progress (2/5) is a nice-to-have derived from orchestrator progress callbacks — defer unless cheap.

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 2–5 URLs/turn (v2.3 scope) | In-scope design above is sufficient: `limitedParallelism(3)`, 20 s total budget, ~8K fused cap. No new deps. |
| 5+ URLs or whole-page archives | Per-source excerpt budget must shrink (fused cap fixed, more slices); consider hostname dedup and same-article canonicalization before fetch. |
| Offline-first / metered networks | Retry worker constraints gain `REQUIRES_UNMETERED` opt-in setting; source store gains size cap + TTL eviction (recommend 5 MB / 7 days). |

### Scaling Priorities

1. **First bottleneck: prompt prefix vs context window.** Fused multi-source blocks compete with small local-model context sizes (smart presets already tune `contextSize` per device memory). Clamp fused output and log worst-case prefix length per turn (Timber, debug) so truncation bugs are diagnosable.
2. **Second bottleneck: socket fan-out on slow radios.** 5 parallel fetches × 20 s timeouts on a poor connection delays every grounded turn to the full budget. Mitigate with per-child 8 s connect (reuse v1 values) + total `withTimeoutOrNull` so the turn degrades to partial sources instead of hanging.

## Anti-Patterns

### Anti-Pattern 1: Fetch fan-out directly in the ViewModel with bare `async`

**What people do:** `viewModelScope.launch { urls.map { async { fetcher.fetch(it) } } }` inline in `sendMessage`.
**Why it's wrong:** The turn's `generationJob` cancellation, the Stop path, and the new-turn pre-cancel all assume one cancellable fetch handle; stray `async` children leak past turn boundaries and interleave sources between turns (the exact CR-02 interleaving bug v2.1 fixed for inference). Untestable without Hilt.
**Do this instead:** Encapsulate fan-out in `GroundingOrchestrator` owning a `CallRegistry` (list of active OkHttp `Call`s, `cancelAll()` mirroring v1's `cancel()`); the ViewModel keeps calling exactly two methods: `orchestrator.ground(…)` and `orchestrator.cancel()`.

### Anti-Pattern 2: Persisting the fused block into message history

**What people do:** Saving the augmented prompt text (or the full `[WEB CONTEXT]` block) into `MessageEntity.content` so previews "just work" from history.
**Why it's wrong:** Breaks the v1 invariant that history holds originals — subsequent turns would re-send stale context as user text, citations would compound, and Room rows bloat by KBs per message. `EntityMappers` is field-by-field precisely to prevent this class of leak.
**Do this instead:** Persist structured `GroundedSource(url, title, excerpt, fetchedAt)` rows (or nothing — Option A), and re-fuse at send time only.

### Anti-Pattern 3: Per-chat toggle as a second DataStore key

**What people do:** Storing per-conversation overrides in DataStore (`web_grounding_conv_<id>`) to avoid a migration.
**Why it's wrong:** DataStore is key-value with no cascade: deleting a conversation orphans its key, listing overrides requires key-prefix scans, and backup/restore diverges from Room. Conversations already live in Room with a migration discipline (v14 precedent).
**Do this instead:** Nullable `web_grounding_mode` column on `conversations` with `NULL = inherit`; delete cascades free via the existing conversation delete path.

### Anti-Pattern 4: Retry worker reusing the endpoint-authenticated OkHttp client

**What people do:** Injecting the base `OkHttpClient` (with `AuthInterceptor`) into the worker for convenience.
**Why it's wrong:** The v1 fetcher strips `AuthInterceptor` for a reason — retry fetches hit arbitrary hosts, and a tag-mismatch bug would leak the user's LM Studio/API key to a third-party site. Workers run without the turn's endpoint-tag context, making this *more* likely, not less.
**Do this instead:** Worker injects `WebPageFetcher` (or the shared stripped client provider) — never the raw base client.

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Arbitrary web hosts (fetch targets) | Outbound GET via shared stripped OkHttp client, browser UA, `Accept: text/html, text/plain` (REUSE v1 policy) | No new hosts, no API keys; keep 3-redirect cap, scheme allowlist (http/https), content-type gate per fetch, not just per turn |
| Connectivity (offline detection) | `ConnectivityManager.NET_CAPABILITY_VALIDATED` check (REUSE `hasValidatedInternet`) + WorkManager `NetworkType.CONNECTED` constraint for retries | Turn path stays synchronous-check-then-model-only; worker path is constraint-gated — two mechanisms, same predicate semantics |
| No new external deps | Heuristic extractor extends in place per research verdict | If research picks robust HTML→text (e.g. Jsoup), it lands as one Gradle dep inside `data/grounding` only — never in UI or domain layers |

### Internal Boundaries

| Boundary | Communication | New vs modified | Notes |
|----------|---------------|-----------------|-------|
| `ChatViewModel → GroundingOrchestrator` | `suspend ground()` + `cancel()` | NEW interface, MODIFIED call site (lines 269–297 replaced by ~10 lines) | Orchestrator is `@Singleton` Hilt; ViewModel keeps toggle-resolution + request assembly |
| `Orchestrator → WebPageFetcher` | `suspend fetchOne(url): FetchedSource?` (refactored inner step of v1 `fetch`) | MODIFIED (extract method, add call registry) | v1 `fetch(url): GroundingResult` becomes a 1-URL convenience over `fetchAll`; existing unit tests keep passing |
| `UrlDetector` | `firstUrl()` → `extractUrls(text, limit)` | MODIFIED (additive; keep `firstUrl` delegating) | Pure Kotlin, JVM tests extend naturally |
| `GroundingPrompt` | `buildBlock()` + `buildFusedBlock()` | MODIFIED (additive; single-URL routes through fused builder) | SYSTEM_PROMPT citation contract already covers `[1]/[2]` — verify wording scales to `[5]` |
| `ChatMessage.groundedSources` | `List<String>` → `List<GroundedSource>` | MODIFIED domain type | Ephemeral invariant preserved; `EntityMappers` untouched unless Option B persists |
| `Conversation` + `conversations` table | `webGroundingMode: Boolean?` + `MIGRATION_14_15` | MODIFIED (+1 nullable col, default NULL) | `ConversationDao` gains `setWebGroundingMode(id, mode)`; `ChatRepository` exposes override get/set |
| `MessageBubble Fuentes` → preview | Clickable rows → `SourcePreviewSheet` | MODIFIED list + NEW sheet | Sheet reads in-memory excerpts; stateless, no VM involvement |
| `ModelOnlyBanner` → retry | "Reintentar" button (immediate) + worker status (deferred) | MODIFIED banner | Immediate path calls `viewModel.retryGrounding(messageId)`; deferred path observes `WorkInfo` |
| `LlmModelHelper` + providers | No change | UNCHANGED (explicit non-goal) | Fusion output is a longer prompt string; helpers remain blind — state this in the phase plan so nobody "improves" the interface |
| `AdvancedPreferences` global toggle | No change | UNCHANGED | Becomes the inherit-default; Settings UI untouched |

## Suggested Build Order (dependency-gated)

1. **Foundation: multi-URL fetch + fusion (no UI).** `UrlDetector.extractUrls` → `WebPageFetcher.fetchOne` + call registry → `GroundingOrchestrator.ground` → `GroundingPrompt.buildFusedBlock` → rewire `ChatViewModel` hook. Deliverable: 2–5 URL turns ground with fused context; chip copy counts pages; Stop cancels all. All JVM-testable except the hook. *Unblocks everything below.*
2. **Source store + preview UI.** `GroundedSource` domain type → `ChatMessage` field migration → tappable Fuentes rows → `SourcePreviewSheet`. Decide Option A (ephemeral) vs B (Room persist) at plan time; A ships in this step alone, B adds entity/DAO here. *Depends on 1 (needs the `List<GroundedSource>` shape).*
3. **Per-chat toggle.** `MIGRATION_14_15` (`web_grounding_mode`) → `Conversation` field → repository methods → ViewModel resolution chain → chat-surface toggle affordance (input-bar or conversation menu — plan-time UI call). *Independent of 2 except for shared `ChatViewModel` touchpoints; can parallelize after 1 lands.*
4. **Offline retry queue.** Pending-retry record → `GroundingRetryWorker` + constraints → banner "Reintentar" immediate path + worker-status observation. *Depends on 1 (shared `ground()` entry) and on the 2-schema decision (where results land). Build last.*

## Sources

- Live codebase (HIGH): `data/grounding/{UrlDetector,WebPageFetcher,GroundingPrompt,GroundingResult,HtmlToTextExtractor,WebContextSanitizer}.kt`; `ui/chat/ChatViewModel.kt` (turn hook lines 269–297, cancel lines 255–259/521–537); `ui/chat/{ChatUiState,ChatScreen}.kt`; `ui/chat/components/MessageBubble.kt` (Fuentes/banner); `domain/model/{ChatMessage,Conversation}.kt`; `data/local/{db/{AppDatabase,Migrations,entity/ConversationEntity+MessageEntity+EntityMappers},preferences/AdvancedPreferences.kt,download/ModelDownloadWorker.kt,benchmark/ModelBenchmarkWorker.kt}`; `domain/llm/LlmModelHelper.kt`
- Codebase precedent for WorkManager deferrable work (MEDIUM — pattern reuse, not a spec): `ModelDownloadWorker` + `BenchmarkScheduler`
- No web sources consulted — this is an internal-integration analysis; all integration points are intra-repo. Confidence is HIGH for existing-structure claims, MEDIUM for sizing recommendations (fused cap, parallelism limit, TTL) which need device validation in-phase.

---
*Architecture research for: Warped v2.3 Web Grounding v2*
*Researched: 2026-09-28*
