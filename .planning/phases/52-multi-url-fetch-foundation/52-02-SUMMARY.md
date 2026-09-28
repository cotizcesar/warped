---
phase: 52-multi-url-fetch-foundation
plan: "02"
subsystem: data/grounding + ui/chat
tags: [multi-url, fan-out, cancel-safety, progress-ux, fuentes, unit-tested]
dependency_graph:
  requires: [allUrls-fanout-input, jsoup-extractor-core, global-grounding-budget, fused-numbered-blocks]
  provides: [fan-out-orchestrator, concurrent-cancel, multi-url-grounding-hook, n-source-progress-chip, pluralized-banner]
  affects: [53-source-persistence]
tech_stack:
  added: []
  patterns: [coroutineScope-awaitAll-fanout, progress-callback-atomic-counter, ephemeral-banner-count]
key_files:
  created:
    - app/src/main/java/com/warped/data/grounding/MultiUrlFetcher.kt
    - app/src/test/java/com/warped/data/grounding/MultiUrlFetcherTest.kt
  modified:
    - app/src/main/java/com/warped/data/grounding/WebPageFetcher.kt
    - app/src/main/java/com/warped/data/grounding/GroundingResult.kt
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/ui/chat/ChatUiState.kt
    - app/src/main/java/com/warped/domain/model/ChatMessage.kt
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
    - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
    - app/src/test/java/com/warped/ui/chat/ChatCancellationTest.kt
    - app/src/test/java/com/warped/ui/chat/ChatSubStateTest.kt
decisions:
  - "budget threaded into fetch() (single truncation point) instead of orchestrator-side re-truncation"
  - "Grounded carries sanitized text so fusion never re-parses framed blocks"
  - "coroutineScope (not supervisorScope) preserves the single-cancel-path contract"
  - "ephemeral modelOnlySourceCount carries the M>1 plural signal, no Room change"
metrics:
  duration: "~35 min"
  completed: 2026-09-28
---

# Phase 52 Plan 02: Multi-URL Fan-Out + N-Source Surfaces Summary

Parallel fan-out orchestration over the frozen v2.2 fetch policy with the fan-out-unsafe cancel fixed first, ViewModel hook swap with live N-de-M progress and partial routing, and the UI-SPEC chip/Fuentes/banner surfaces — full unit suite 256/256 green, zero Jsoup.connect() usage, no Room migration.

## What Was Built

- **Concurrent cancel fix (`WebPageFetcher.kt`)**: single `@Volatile activeCall` replaced with a `ConcurrentHashMap.newKeySet<Call>` — added right after `newCall()`, removed in `finally`, `cancel()` iterates a snapshot aborting every in-flight socket. Fetch policy verbatim (64KB/8KB cap, 8/10/20s timeouts, 3 manual redirects, html/plain gate, browser UA, stripped client, cooperative CancellationException rethrow).
- **Per-page budget threading**: `fetch(url, budget = MAX_CHARS)` threads the global-budget split into extraction (single truncation point, no double truncation); default keeps legacy single-page behavior for existing callers.
- **`GroundingResult.Grounded` gains `text`**: sanitized extracted text backing fusion, so the orchestrator fuses texts and never re-parses framed blocks.
- **`MultiUrlFetcher.kt`** (Hilt `@Singleton`, pure apart from injected fetcher): `fetchAll(urls, contextSize, onProgress)` — `distinct().take(5)` (6th+ ignored deterministically), `coroutineScope` + `async(ioDispatcher)` + `awaitAll()` zipped by index (Fuentes order == block order), `buildFusedBlock()` concatenation only; routing any-Grounded-wins → `Fused(block, okUrls, skippedUrls)`, all-ModelOnly → `AllFailed` with OFFLINE-wins collapse; CancellationException propagates unconverted. `ioDispatcher` is an internal override for deterministic JVM tests.
- **ViewModel hook swap (`ChatViewModel.kt`)**: `allUrls()` fan-out at the same position (after save, before helper resolution), `WebFetchProgress(done/total/perSource ok-omitida-loading)` published via `ChatInputState` (`isFetchingWeb` kept for compat), live done-counts via an atomic-counter progress callback, `contextSize` from connection `generationParameters` (default 4096); partial augments with ok-only fused block + omitida marks, all-fail sets worst-case notice with source count; `stopGeneration`/new-turn pre-cancel clears progress; providers/helpers untouched, no Room change.
- **N-source surfaces**: chip renders `Leyendo N de M…` (multi) / legacy `Leyendo página…` (single) with matching accessibility descriptions, spinner + Stop-cancel + transient unmount unchanged; Fuentes renders all N in block order (wrapping, zero-sources renders nothing — verified unchanged); model-only banner pluralizes on multi-fail (`No se pudieron leer las páginas…`), OFFLINE copy unchanged, renders only on all-fail via the new ephemeral `ChatMessage.modelOnlySourceCount`.

## Test Results

- `MultiUrlFetcherTest` — **9 tests, 0 failures**: fusion order, 1-dead-of-3 partial, all-fail collapse, OFFLINE-wins, 6th-URL never fetched, dedupe-once, cancel propagation, per-page budget threading, progress done-counts.
- Grounding suite — **44 tests, 0 failures** (35 pre-existing + 9 new). UI chat suite — **26 tests, 0 failures**.
- Full `:app:testDebugUnitTest` — **256 tests, 0 failures, 0 errors**. `:app:assembleDebug` green. `grep -rn "Jsoup.connect" app/src/main/java/` clean.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing] `fetch()` exposed no budget/text handle for per-page truncation + fusion**
- **Found during:** Task 1 (orchestrator needs truncated per-page texts; `Grounded` carried only the framed block)
- **Issue:** Truncating orchestrator-side would mean parsing framed blocks or double-truncating the frozen 4000-char extraction.
- **Fix:** Added `fetch(url, budget = MAX_CHARS)` threading budget into the single extraction call, and `Grounded.text` (sanitized text) for fusion input. Observable contract identical to plan: one truncation at the budget split, concatenate-only fusion.
- **Files modified:** `WebPageFetcher.kt`, `GroundingResult.kt`

**2. [Rule 2 - Missing] `awaitAll` alone cannot publish live N-de-M progress**
- **Found during:** Task 1 (plan requires "updating done counts as results land")
- **Issue:** Awaiting all results yields one atomic completion — no intermediate counts for the `Leyendo N de M…` chip.
- **Fix:** `fetchAll(..., onProgress: (done, total) -> Unit)` invoked from each child's `finally` via `AtomicInteger`; ViewModel maps it into `WebFetchProgress` copies (thread-safe `StateFlow.update`).
- **Files modified:** `MultiUrlFetcher.kt`, `ChatViewModel.kt`

**3. [Rule 2 - Missing] Banner pluralization needs the attempted count at render time**
- **Found during:** Task 2 (UI-SPEC requires M>1 plural copy; `ModelOnlyNotice` enum carries no count)
- **Issue:** No carrier for M from hook to `MessageBubble`.
- **Fix:** Ephemeral `ChatMessage.modelOnlySourceCount: Int = 1`, set on all-fail, default keeps all existing constructors compiling; `EntityMappers` maps field-by-field so no Room migration.
- **Files modified:** `ChatMessage.kt`, `ChatViewModel.kt`, `MessageBubble.kt`

**4. [Rule 3 - Blocking] New `MultiUrlFetcher` ctor param broke two ViewModel test builders**
- **Found during:** Task 2 (named-arg `ChatViewModel(...)` calls no longer compile)
- **Issue:** `ChatCancellationTest` and `ChatSubStateTest` builders lacked the new dependency.
- **Fix:** Passed `mockk<MultiUrlFetcher>()` (hook never fires — grounding disabled in those tests); suites green.
- **Files modified:** `ChatCancellationTest.kt`, `ChatSubStateTest.kt`

## Known Stubs

None — no placeholders, TODOs, or unwired surfaces. `webFetchProgress` nulls on completion/Stop by construction; skipped URLs always land in `skippedUrls` + omitida state.

## Threat Flags

None — no new network endpoints, auth paths, file access, or schema changes. Mitigation ledger: T-52-05 concurrent cancel set (add-after-newCall/remove-in-finally/snapshot-cancel, `activeCalls` retains the `activeCall` substring); T-52-06 sanitizer per page inside the frozen fetch path; T-52-07 stripped-client construction verbatim, connect-grep clean; T-52-08 3-redirect cap + scheme gate verbatim; T-52-09 index-zipped awaitAll, order asserted in tests; T-52-SC no package installs.

## Commits

- `9cc16df` feat(52-02): concurrent cancel set + MultiUrlFetcher fan-out orchestrator
- `2d98986` feat(52-02): ViewModel multi-URL hook with N-source progress
- `ce3b2c1` feat(52-02): N-source chip copy plus pluralized all-fail banner

## Self-Check: PASSED

- All 11 plan-listed files created/modified on disk: FOUND
- All 3 task commits exist in `git log`: FOUND (`9cc16df`, `2d98986`, `ce3b2c1`)
- No unintended file deletions in any task commit: verified via `git diff --diff-filter=D`
- Verification re-run post-commit: full suite 256/256 green, `assembleDebug` success, connect-grep clean, stub-grep clean
