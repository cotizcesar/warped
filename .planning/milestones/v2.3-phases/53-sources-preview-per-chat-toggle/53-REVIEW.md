---
phase: 53-sources-preview-per-chat-toggle
reviewed: 2026-09-28T17:30:00Z
depth: standard
files_reviewed: 27
files_reviewed_list:
  - app/build.gradle.kts
  - app/src/androidTest/java/com/warped/data/local/db/MigrationTest.kt
  - app/src/main/java/com/warped/data/grounding/GroundingPrecedence.kt
  - app/src/main/java/com/warped/data/grounding/MultiUrlFetcher.kt
  - app/src/main/java/com/warped/data/local/db/AppDatabase.kt
  - app/src/main/java/com/warped/data/local/db/dao/ConversationDao.kt
  - app/src/main/java/com/warped/data/local/db/dao/GroundedSourceDao.kt
  - app/src/main/java/com/warped/data/local/db/entity/ConversationEntity.kt
  - app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt
  - app/src/main/java/com/warped/data/local/db/entity/GroundedSourceEntity.kt
  - app/src/main/java/com/warped/data/local/db/Migrations.kt
  - app/src/main/java/com/warped/data/repository/ChatRepositoryImpl.kt
  - app/src/main/java/com/warped/di/DatabaseModule.kt
  - app/src/main/java/com/warped/domain/model/ChatMessage.kt
  - app/src/main/java/com/warped/domain/model/Conversation.kt
  - app/src/main/java/com/warped/domain/model/GroundedSource.kt
  - app/src/main/java/com/warped/domain/repository/ChatRepository.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/main/java/com/warped/ui/chat/ChatUiState.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
  - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
  - app/src/main/java/com/warped/ui/chat/components/SourcePreviewSheet.kt
  - app/src/test/java/com/warped/data/grounding/GroundingPrecedenceTest.kt
  - app/src/test/java/com/warped/data/repository/GroundedSourceHydrationTest.kt
  - app/src/test/java/com/warped/ui/chat/ChatGroundingToggleTest.kt
  - app/src/test/java/com/warped/ui/chat/SourcePreviewMappingTest.kt
findings:
  critical: 1
  warning: 4
  info: 2
  total: 7
status: issues_found
---

# Phase 53: Sources Preview + Per-Chat Toggle — Code Review Report

**Reviewed:** 2026-09-28T17:30:00Z
**Depth:** standard
**Files Reviewed:** 27
**Status:** issues_found

## Summary

Reviewed all 27 Phase 53 source files (commits `b2cc5e0`..`876e022`/`1286d61`, diffed against pre-53 HEAD): v15 Room store + single `MIGRATION_14_15`, `ChatRepositoryImpl` hydration, `GroundingPrecedence`, `ChatViewModel` hook + toggle + chip, `SourcePreviewSheet`, clickable Fuentes, guarded `ACTION_VIEW`, and all new tests.

Threat-gate verification (all pass): exactly one `Migration(14, 15)`; `fallbackToDestructiveMigration(false)`; FK CASCADE on `grounded_sources.message_id` with transitive cascade covered by `MessageEntity` CASCADE; source rows keyed on `MessageDao.insert` Long return (never UUID); `ACTION_VIEW` carries URL only (no extracted text); no history rewrite; providers/inference untouched (`git diff` on `data/remote/*` + `data/local/inference/*` empty); zero DAO/entity imports in `ui/`; no secrets/debug artifacts.

One **critical** correctness bug survives: the ViewModel rebuilds source details by looking up pasted URLs in a map keyed by post-redirect resolved URLs, so **every redirected fetch is recorded as omitida and its extracted text is silently dropped** — while inference still used the page. Four warnings (unvalidated intent URL scheme + narrow catch, timestamp bump on toggle, no-op REPLACE without unique index, unexecuted device migration gate) and two info items round out the report.

## Critical Issues

### CR-01: Source details keyed by pasted URL against resolved-URL map — redirects recorded as omitida, extracted text dropped

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:1191-1202`
**Issue:** `buildSourceDetails(blockOrder, fused)` iterates pasted URLs (`groundedUrls` from `UrlDetector.allUrls`) and resolves OK-ness via `fused.pageTexts[url]`. But `pageTexts` is built in `MultiUrlFetcher.fetchAll` as `okPages.toMap()`, where keys are `GroundingResult.Grounded.url` — and `WebPageFetcher` sets that to `currentUrl`, the **post-redirect** URL (`WebPageFetcher.kt:119,155`). On any redirect (http→https, shorteners, tracking params, Hugging Face `/resolve/` → CDN), the pasted key misses, so a successfully grounded page is persisted as `GroundedSource(url=pasted, extractedText=null, OMITIDA)`. Consequences: (a) preview shows a struck "omitida" row for a page the model actually used — dishonest Fuentes; (b) the extracted text is discarded, defeating SRC-02 persistence; (c) persisted `resolved_url` holds the **pasted** URL, contradicting the `GroundedSourceEntity` contract ("Only fetcher-resolved post-redirect http/https URLs are stored") and the T-53-02 threat claim; (d) `ChatMessage.groundedSources` (`result.okUrls`, resolved) diverges from `groundedSourceDetails` URLs (pasted) on the same message. The pasted→resolved mapping is destroyed at fusion time, so no caller can repair this downstream — the fix belongs in the carrier/fusion.
**Fix:** Stop the lossy lookup. Emit the union at fusion time, where both URLs are known — e.g. add an ordered details list to `Fused` built in `MultiUrlResult` fusion order (which already equals fetch-block order):

```kotlin
// MultiUrlFetcher.fetchAll, replacing pageTexts lookup contract:
val details = results.map { (pastedUrl, result) ->
    when (result) {
        is GroundingResult.Grounded ->
            GroundedSource(url = result.url, extractedText = result.text, status = GroundedSourceStatus.OK)
        is GroundingResult.ModelOnly ->
            GroundedSource(url = pastedUrl, extractedText = null, status = GroundedSourceStatus.OMITIDA)
    }
}
// Fused carries `details: List<GroundedSource>`; ViewModel persists it directly.
// (data/grounding -> domain/model import is already the established direction.)
```

If the carrier shape must stay, at minimum key `pageTexts` by pasted URL **and** carry the resolved URL alongside — never look up resolved-keyed data by pasted key. Add a JVM test with a redirect (`Grounded.url != pastedUrl`) asserting OK status + resolved URL + text preserved.

## Warnings

### WR-01: ACTION_VIEW fires untrusted stored URL text with no scheme allowlist; only ActivityNotFoundException caught

**File:** `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt:284-299`
**Issue:** The browser intent is built from `GroundedSource.url` loaded from the `resolved_url` TEXT column — the same untrusted-stored-text class the phase correctly distrusts for `status` (drop-unknown mapper), but here passed straight into `Uri.parse` + `ACTION_VIEW` with no `http/https` allowlist. A planted non-web URL (`intent://`, `file://`, `javascript:`) via DB tampering, backup restore, or a future writer bug would be fired as-is; `intent://` URIs in particular are a known intent-scheme attack surface. Additionally only `ActivityNotFoundException` is caught — `Context.startActivity` can also throw `SecurityException` (OEM exported-activity enforcement, blocked schemes), which would crash chat from a tap handler.
**Fix:**

```kotlin
onOpenBrowser = { url ->
    val uri = Uri.parse(url)
    if (uri.scheme != "http" && uri.scheme != "https") {
        Toast.makeText(context, "Enlace no válido.", Toast.LENGTH_SHORT).show()
    } else {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            previewSource = null
        } catch (e: Exception) { // ActivityNotFoundException + SecurityException
            Toast.makeText(context, "No se encontró un navegador para abrir el enlace.", Toast.LENGTH_SHORT).show()
        }
    }
}
```

### WR-02: Per-chat toggle write bumps updated_at, reordering the conversation list

**File:** `app/src/main/java/com/warped/data/local/db/dao/ConversationDao.kt:32-33`
**Issue:** `setWebOverride` sets `updated_at = :ts` alongside the override. `observeAll()` orders by `updated_at DESC`, so flipping Web Sí/No/Heredar on an old conversation teleports it to the top of the list with no new message — a side effect unrelated to the toggle's contract ("applies to next send, never refetches history"). A preference change should not forge recency.
**Fix:**

```sql
UPDATE conversations SET web_override = :override WHERE id = :id
```

(Keep the timestamp bump only on message writes, where it already happens via `saveMessage`/`saveMessageWithSources`.)

### WR-03: GroundedSourceDao REPLACE is a no-op — no unique index guards (message_id, source_index)

**File:** `app/src/main/java/com/warped/data/local/db/dao/GroundedSourceDao.kt:15-16`
**Issue:** `insertAll` uses `OnConflictStrategy.REPLACE`, but rows are built via `toEntity` with the default `id = 0`, which Room treats as "not set" and auto-generates — so no insert ever conflicts and REPLACE never fires. Worse, there is no unique index on `(message_id, source_index)`, so nothing at the DB level prevents duplicate/overlapping rows for one message (e.g. a future double-persist or Phase 54 retry path). The conflict strategy is misleading and the ordering invariant (`ORDER BY source_index`) has no uniqueness backing.
**Fix:**

```kotlin
// GroundedSourceEntity indices:
indices = [
    Index(value = ["message_id"]),
    Index(value = ["message_id", "source_index"], unique = true),
]
```

(With the unique index in place, REPLACE actually gains meaning; schema export + migration test must be updated accordingly — new migration, not an edit to 14→15.)

### WR-04: v14→v15 migration device gate still unexecuted — ships without on-device validation

**File:** `app/src/androidTest/java/com/warped/data/local/db/MigrationTest.kt:55-151`
**Issue:** The `migrate14To15` gate (row survival, NULL default, ok/omitida round-trip, null/0/1 override, transitive cascade) is compile-green but has never run on a device/emulator (no adb attached, per 53-01/53-02 summaries). The single-migration design, raw-SQL DDL, and SQLCipher `SupportFactory` interplay (FK pragma enforcement under SQLCipher is what the transitive-cascade assertion depends on) are exactly the things unit tests cannot prove. The committed `14.json` is currently clean (no `web_override` drift in HEAD — the working-tree drift flagged in 53-02 did not land), so the gate should pass once run, but until it runs the phase exit criterion is open.
**Fix:** Run `./gradlew :app:connectedDebugAndroidTest --tests "com.warped.data.local.db.MigrationTest"` on hardware/emulator before closing the phase; do not carry this gate into Phase 54 (whose retry read-model depends on these rows).

## Info

### IN-01: Sheet selection state not keyed on message — stale preview if message updates while open

**File:** `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt:221-222`
**Issue:** `previewSource`/`previewNumber` use keyless `remember`, while `fuenteList` is correctly keyed on `remember(sourceDetails, message.groundedSources)`. If the underlying message recomposes with fresh data (e.g. post-hydration reload) while the sheet is open, the sheet keeps rendering the superseded snapshot. Low impact (sheet is transient, dismiss re-resolves), but keying is one word.
**Fix:** `var previewSource by remember(message) { mutableStateOf<GroundedSource?>(null) }` (same for `previewNumber`), or derive the sheet item from `fuenteList` + selected number at composition time.

### IN-02: tryEmit results ignored — burst event loss is silent

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:269,278,622`
**Issue:** All three `ChatEvent.Snackbar` emissions use `_events.tryEmit(...)` and discard the Boolean. With `extraBufferCapacity = 4` and a single `showSnackbar` collector, overflow is implausible in practice (human-rate taps, ≤1 event per turn), so this is robustness hygiene only: a dropped toggle confirmation or persist-failure notice would vanish without a trace.
**Fix:** Log on `false`, e.g. `if (!_events.tryEmit(...)) Timber.w("Chat: event buffer full, dropped snackbar")`, so any future burst is observable.

---

_Reviewed: 2026-09-28T17:30:00Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
