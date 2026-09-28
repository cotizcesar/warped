---
phase: 53-sources-preview-per-chat-toggle
plan: "02"
subsystem: grounding
tags: [web-grounding, tri-state-override, chat-repository, compose, jvm-test]

# Dependency graph
requires:
  - phase: 53-sources-preview-per-chat-toggle
    plan: "01"
    provides: v15 store + domain contracts + repository signatures (stubs)
provides:
  - Texts-carrying Fused carrier (pageTexts keyed by resolved URL)
  - Pure-Kotlin GroundingPrecedence.shouldGround + 12-row truth-table test
  - Row-id source persistence + loadConversation hydration (ok AND omitida)
  - Tri-state Web Si/No/Heredar menu + one-shot Sin web chip + hook wiring
affects: [53-03 (SourcePreviewSheet reads hydrated groundedSourceDetails)]

# Tech tracking
tech-stack:
  added: []
  patterns: [precedence-as-pure-function, one-shot UI events via SharedFlow+tryEmit, pending-override-held-pre-conversation]

key-files:
  created:
    - app/src/main/java/com/warped/data/grounding/GroundingPrecedence.kt
    - app/src/test/java/com/warped/data/grounding/GroundingPrecedenceTest.kt
    - app/src/test/java/com/warped/data/repository/GroundedSourceHydrationTest.kt
    - app/src/test/java/com/warped/ui/chat/ChatGroundingToggleTest.kt
  modified:
    - app/src/main/java/com/warped/data/grounding/MultiUrlFetcher.kt
    - app/src/main/java/com/warped/data/repository/ChatRepositoryImpl.kt
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/ui/chat/ChatUiState.kt
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
    - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt

key-decisions:
  - "Fused.pageTexts is Map<resolvedUrl, text> with emptyMap default — zero churn in Phase 52 tests, union rebuilt in ViewModel in fetch-block order"
  - "Source-insert failure is Timber-logged AND rethrown so the ViewModel can surface the UI-SPEC Snackbar; the send always continues"
  - "Pre-first-send toggle is held as in-memory pendingWebOverride applied at ensureConversation (RESEARCH open question 3, first option)"
  - "Tri-state control is an overflow menu on the inline model-selector bar (RESEARCH cheapest-first rank); no TopAppBar added"
  - "Override read failures degrade to inherit (null), never break the send or the conversation open"

patterns-established:
  - "Single-decision precedence function at hook top (T-53-05): shouldGround(skipOnce, perChat, global)"
  - "One-shot UI events: MutableSharedFlow(extraBufferCapacity) + tryEmit only, ChatScreen renders as Snackbar"

requirements-completed: [TOGGLE-01, TOGGLE-02, TOGGLE-03, SRC-02]

# Metrics
duration: 40min
completed: 2026-09-28
---

# Phase 53 Plan 02: Repository Hydration + Precedence + Per-Chat Toggle Summary

**shouldGround precedence (Sin web > per-chat > global) driving the send hook, row-id source persistence with ok+omitida hydration, and the tri-state menu + one-shot chip — 273 unit tests green, no DAO imports in UI**

## Performance

- **Duration:** ~40 min
- **Completed:** 2026-09-28
- **Tasks:** 2/2
- **Files modified:** 10 (4 created tests+impl, 6 modified incl. ViewModel and screens)

## Accomplishments

- Fused carrier now threads per-page extracted texts (`pageTexts`, same order as `okUrls`); fetch internals, redirect policy, truncation, and fan-out ordering untouched (Phase 52 sealed inputs)
- Pure-Kotlin `GroundingPrecedence.shouldGround` with a 12-row truth-table JVM test (skipOnce wins over everything, per-chat wins over global, null inherits global)
- `ChatRepositoryImpl` implements all four 53-01 stubs: sources keyed on the `MessageDao.insert` Long return (never the UUID), `loadConversation` hydrates `groundedSources` urls plus `groundedSourceDetails` for assistant rows only, override read/write via `ConversationDao`
- `ChatViewModel` hook evaluates precedence once per send (suspend override read, never a hot Flow), retains the ok+omitida union in fetch-block order, persists via `saveMessageWithSources`, surfaces both UI-SPEC Snackbar copies through one-shot `ChatEvent`s
- Tri-state `Web: Sí/No/Heredar` overflow menu on the inline model-selector bar with live `Heredar (activado/desactivado global)` hint; composer `Sin web` chip reusing the thinking-chip idiom, reset after every send; pre-first-send toggle held pending and applied at conversation creation

## Task Commits

Each task was committed atomically (Task 1 followed RED/GREEN):

1. **Task 1 RED: failing precedence + hydration tests** - `30c1d51` (test)
2. **Task 1 GREEN: Fused texts, precedence fn, row-id save + hydration** - `1f50bdf` (feat)
3. **Task 2: hook + tri-state menu + Sin web chip + ViewModel tests** - `876e022` (feat)

## Files Created/Modified

- `app/src/main/java/com/warped/data/grounding/GroundingPrecedence.kt` - NEW: pure `shouldGround(skipOnce, perChat, global)`
- `app/src/main/java/com/warped/data/grounding/MultiUrlFetcher.kt` - + `Fused.pageTexts` (default emptyMap), populated from okPages
- `app/src/main/java/com/warped/data/repository/ChatRepositoryImpl.kt` - GroundedSourceDao injection, 4 real implementations, Phase-54 REPLACE/CASCADE warning
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` - hook precedence, details builder, `saveMessageWithSources` + Snackbar, `toggleSkipWebOnce`/`setWebOverride`, pending override, per-open override load
- `app/src/main/java/com/warped/ui/chat/ChatUiState.kt` - `ChatInputState.skipWebOnce`, `ChatConnectionState.webOverride/webGroundingEnabled`, `ChatEvent.Snackbar`, shim passthrough
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` - event collector, overflow tri-state menu with check marks + inherit hint, chip props
- `app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt` - `Sin web` chip next to the thinking chip
- `app/src/test/java/com/warped/data/grounding/GroundingPrecedenceTest.kt` - NEW: 12-row truth table + 2 focused tests
- `app/src/test/java/com/warped/data/repository/GroundedSourceHydrationTest.kt` - NEW: order preservation, drop-unknown status, toEntity round-trip
- `app/src/test/java/com/warped/ui/chat/ChatGroundingToggleTest.kt` - NEW: 4 send-path tests (skip-once, per-chat No, inherit-persist, pending pre-send toggle)

## Decisions Made

- `pageTexts` as a Map rather than a parallel list — lookup by URL when rebuilding the union is order-independent and total (covers exactly `okUrls`); the union order comes from the fetch-block order in the ViewModel, not the carrier.
- Repository rethrows source-insert failures after Timber logging (instead of swallowing) so Task 2's hook can emit the UI-SPEC persistence-failure Snackbar; message-insert failures propagate the same way. Transcript is already updated, so the send continues in all cases.
- Pending-override (not hidden-control) for the pre-conversation toggle — the control stays always visible, zero new surfaces, applied once at `ensureConversation` and cleared.
- Overflow `MoreVert` menu on the inline model-selector bar for the tri-state control — cheapest of the RESEARCH-ranked options; ChatScreen has no TopAppBar and none was added.
- `ChatEvent` uses `tryEmit` exclusively — a suspending `emit` with no collector would hang `advanceUntilIdle` in unit tests and could stall a turn on screen.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Truth `.named()` unavailable on this Truth version**
- **Found during:** Task 1 GREEN (test compile)
- **Issue:** `assertThat(...).named(...)` does not resolve against the project's Truth version.
- **Fix:** Rewrote the row-context assertion with `assertWithMessage(format, args).that(actual)`.
- **Files modified:** app/src/test/java/com/warped/data/grounding/GroundingPrecedenceTest.kt
- **Committed in:** 1f50bdf (part of Task 1 GREEN commit)

**2. [Rule 1 - Bug] `skipOnce`/`skipWebOnce` name mismatch in the hook**
- **Found during:** Task 2 (main compile)
- **Issue:** The captured flag was declared `skipWebOnce` but referenced as `skipOnce` inside the turn closure.
- **Fix:** Aligned the call site to `skipOnce = skipWebOnce`.
- **Files modified:** app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
- **Committed in:** 876e022 (part of Task 2 commit)

**3. [Rule 1 - Bug] New ViewModel test stubbed the fetcher mock before construction**
- **Found during:** Task 2 (test run — `UninitializedPropertyAccessException`)
- **Issue:** `coEvery { multiUrlFetcher.fetchAll... }` ran before `buildViewModel` assigned the lateinit mock.
- **Fix:** Moved fetchAll stubbing to after `buildViewModel(...)` in all three affected tests.
- **Files modified:** app/src/test/java/com/warped/ui/chat/ChatGroundingToggleTest.kt
- **Committed in:** 876e022 (part of Task 2 commit)

---

**Total deviations:** 3 auto-fixed (all Rule 1, test/compile-level, no scope change)
**Impact on plan:** None — all required for the plan's own verification gates; zero new dependencies (T-53-SC clean).

## Issues Encountered

- **Pre-existing working-tree drift (NOT this plan, NOT fixed):** `app/schemas/.../AppDatabase/14.json` in the working tree contains `web_override` (mtime 2026-09-28 11:46, i.e. the 53-01 window; HEAD's copy is clean). If the v14→v15 device migration test runs against this drifted asset, `MigrationTestHelper` will build a v14 DB that already has the column and `MIGRATION_14_15`'s `ADD COLUMN` will fail with duplicate-column. Left untouched per scope boundary — whoever runs the phase exit-gate device run should `git checkout --` that file first and check which build rewrites it.

## Threat Flags

None — no new surface beyond the plan's threat model. T-53-05 mitigated (single `shouldGround` decision point, override read once per send), T-53-07 mitigated (Timber + non-blocking Snackbar, send continues), T-53-06 accepted by construction (same trust level as chat history, SQLCipher at rest), T-53-SC clean (zero new deps). Repository never called from UI layers (grep verified).

## Known Stubs

None — all four 53-01 `NotImplementedError` stubs are now implemented; no placeholders introduced.

## Verification Results

- `./gradlew :app:testDebugUnitTest --tests GroundingPrecedenceTest --tests GroundedSourceHydrationTest` — BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest --tests "com.warped.ui.chat.*"` — BUILD SUCCESSFUL (37 tests incl. 4 new)
- `./gradlew :app:testDebugUnitTest` (full) — BUILD SUCCESSFUL, **273 tests, 0 failures, 0 errors, 0 skipped**
- `grep` for DAO/entity imports in `ui/chat` — CLEAN
- Post-commit deletion check on both feat commits — no deletions

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- 53-03 (SourcePreviewSheet) can read `ChatMessage.groundedSourceDetails` directly — hydrated on history load and populated live on the just-sent assistant message; omitida rows carry null text and render struck/disabled per UI-SPEC.
- Phase 54 retry must READ source rows via `getSourcesByMessage` and never re-save the assistant message (REPLACE + CASCADE would wipe them) — warning recorded as a code comment at `saveMessageWithSources`.
- Phase exit gate still pending: v14→v15 migration device run (see drift warning above) and restart-persistence assertions.

## Self-Check: PASSED

- All 4 created files exist on disk (GroundingPrecedence.kt + 3 test files)
- All 3 task commits exist in git log (30c1d51, 1f50bdf, 876e022)
- Full unit suite: 273 tests, 0 failures
