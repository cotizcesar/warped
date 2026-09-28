---
phase: 54-offline-retry
plan: 01
subsystem: chat-grounding
tags: [kotlin, room, viewmodel, offline-retry, grounding, junit5, mockk, truth]

# Dependency graph
requires:
  - phase: 53-sources-preview-per-chat-toggle
    provides: GroundedSourceDao.deleteByMessage row-reuse primitive, saveMessageWithSources delete-then-insert, OFFLINE banner + modelOnlyNotice contract
  - phase: 52-multi-url-fetch-foundation
    provides: MultiUrlFetcher.fetchAll single grounding entry point, UrlDetector.allUrls, isFetchingWeb/webFetchProgress chip states
provides:
  - ChatRepository.replaceSources row-reuse write path (no re-save)
  - MessageDao.findAssistantRowId lookup by (conversation_id, created_at, ASSISTANT)
  - ChatViewModel.retryGrounding + retryJob + isValidatedOnline flag with Stop/send wiring
  - ChatGroundingRetryTest 8-test exit-gate suite
affects: [54-offline-retry plan 02 (UI banner + Reintentar affordance), milestone audit]

# Tech tracking
tech-stack:
  added: []
  patterns: [sources-only-attach, check-on-resume-connectivity-flag, retry-scoped-job-sharing-stop-path]

key-files:
  created:
    - app/src/test/java/com/warped/ui/chat/ChatGroundingRetryTest.kt
  modified:
    - app/src/main/java/com/warped/data/grounding/WebPageFetcher.kt
    - app/src/main/java/com/warped/data/local/db/dao/MessageDao.kt
    - app/src/main/java/com/warped/domain/repository/ChatRepository.kt
    - app/src/main/java/com/warped/data/repository/ChatRepositoryImpl.kt
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/ui/chat/ChatUiState.kt

key-decisions:
  - "MessageEntity.role stores the enum name ('ASSISTANT') — role predicate kept in findAssistantRowId, no fallback needed"
  - "Row-reuse gate tested with mockk DAOs at repository level — room-testing is androidTest-only, in-memory Room cannot run in JVM unit tests"
  - "refreshConnectivity is best-effort try/catch — unstubbed fetcher mocks in existing tests read as offline, zero regressions"

patterns-established:
  - "Sources-only attach: retry success clears the notice and renders Fuentes with byte-identical assistant text, never re-runs inference"
  - "OFFLINE-only eligibility re-checked on transcript data inside retryGrounding, never on UI visibility"

requirements-completed: [RETRY-01]

# Metrics
duration: 35min
completed: 2026-09-28
---

# Phase 54 Plan 01: Offline Retry Backend Summary

**Foreground offline-retry backend: `replaceSources()` row-reuse write path plus `retryGrounding()` orchestration with connectivity gating, proven by an 8-test exit-gate suite**

## Performance

- **Duration:** ~35 min
- **Started:** 2026-09-28T17:45:00Z
- **Completed:** 2026-09-28T18:20:00Z
- **Tasks:** 2
- **Files modified:** 7 (4 prod + 1 interface in Task 1, 2 prod + 1 test in Task 2, plus 54-RESEARCH.md)

## Accomplishments

- `ChatRepository.replaceSources()` persists retried sources via `findAssistantRowId → deleteByMessage → insertAll → timestamp` without ever re-saving the message (REPLACE CASCADE hazard avoided); null row id is a silent no-op
- `MessageDao.findAssistantRowId` resolves the assistant row by `(conversation_id, created_at)` with the verified `'ASSISTANT'` role literal
- `WebPageFetcher.hasValidatedInternet` raised `private → internal` for the ViewModel gate
- `ChatViewModel.retryGrounding()` re-derives URLs from the persisted user message, reuses `MultiUrlFetcher.fetchAll`, attaches sources-only on `Fused`, leaves the transcript untouched on `AllFailed`; guarded by `isFetchingWeb` no-op, synchronous connectivity re-check, OFFLINE-only data gate, dedicated `retryJob` cancelled on Stop and new send
- `isValidatedOnline` flag on `ChatInputState` (+ shim), refreshed on init, after each send, and after each retry
- 54-RESEARCH.md `## Open Questions` marked (RESOLVED): role column stores the enum name

## Task Commits

Each task was committed atomically:

1. **Task 1: Row-reuse write path without re-save** - `bd1150c` (feat)
2. **Task 2: retryGrounding orchestration with guards + exit-gate tests** - `dba97f9` (feat)

## Files Created/Modified

- `app/src/main/java/com/warped/data/grounding/WebPageFetcher.kt` - `hasValidatedInternet` visibility `private → internal`
- `app/src/main/java/com/warped/data/local/db/dao/MessageDao.kt` - `findAssistantRowId` query, no schema change
- `app/src/main/java/com/warped/domain/repository/ChatRepository.kt` - `replaceSources()` contract
- `app/src/main/java/com/warped/data/repository/ChatRepositoryImpl.kt` - `replaceSources()` delete-then-insert, null no-op
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` - `retryJob`, `refreshConnectivity()`, `retryGrounding()`, Stop/send wiring
- `app/src/main/java/com/warped/ui/chat/ChatUiState.kt` - `isValidatedOnline` on input state + shim + `combineSnapshot`
- `app/src/test/java/com/warped/ui/chat/ChatGroundingRetryTest.kt` - 8 exit-gate/guard tests
- `.planning/phases/54-offline-retry/54-RESEARCH.md` - open question resolved inline

## Decisions Made

- **Role literal `role.name`:** read `EntityMappers.kt:44` (`role = role.name`) before writing the DAO query — the `'ASSISTANT'` predicate is exact, fallback (lookup without role predicate) not needed.
- **Mockk-DAO row-reuse test instead of in-memory Room:** `room-testing` is `androidTestImplementation` (moved there in 41-VERIFICATION — it does not run in JVM unit tests), so gate 3 is proven at the `ChatRepositoryImpl` level with mockk DAOs: double `replaceSources` → 2× `deleteByMessage(7L)`, 2× `insertAll` with identical `messageId` sets of size `details.size`, plus null-rowId no-op. Documented as a deviation, not a gap.
- **`refreshConnectivity()` is best-effort try/catch:** existing ViewModel tests stub only `fetcher.cancel()`; an unstubbed `hasValidatedInternet()` mock throws, so the refresh catches and reads offline. Zero changes required to existing suites.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Overlap-test assertion counted send-time fetch**
- **Found during:** Task 2 (ChatGroundingRetryTest green run — 7/8 pass, 1 failed)
- **Issue:** `overlapping retry taps are no-ops` asserted `fetchAll` exactly once, but the send itself calls `fetchAll` once — send + first retry tap = 2, second tap no-op.
- **Fix:** Assertion corrected to `exactly = 2` with a comment (1× send + 1× retry, second tap no-op).
- **Files modified:** `app/src/test/java/com/warped/ui/chat/ChatGroundingRetryTest.kt`
- **Verification:** Full suite re-run — 8/8 pass.
- **Committed in:** `dba97f9` (Task 2 commit)

**2. [Rule 3 - Blocking] In-memory Room unavailable in unit tests**
- **Found during:** Task 2 (gate 3 test design)
- **Issue:** Plan specified "in-memory Room" for the row-reuse test, but `room-testing` is `androidTestImplementation` — `Room.inMemoryDatabaseBuilder()` cannot run under `testDebugUnitTest`.
- **Fix:** Gate 3 proven with mockk DAOs against the real `ChatRepositoryImpl` (delete-then-insert identity + count + null no-op), which exercises the exact production code path the Room test would have covered minus the SQL layer.
- **Files modified:** `app/src/test/java/com/warped/ui/chat/ChatGroundingRetryTest.kt`
- **Verification:** `replaceSources reuses the same message rows on repeated retry` passes; no migration involved (query-only DAO addition).
- **Committed in:** `dba97f9` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking)
**Impact on plan:** Both necessary for correctness/testability. No scope creep — zero new dependencies, no schema change, no UI surface touched.

## Issues Encountered

- Gradle up-to-date/cache masking hid the forced re-run (`1 executed, 32 up-to-date` twice); resolved with `--rerun-tasks` — 33 tasks executed, 102 tests, 0 failures across `ui.chat.*` + `data.repository.*` + `data.grounding.*`.
- Test-results XML aggregate initially read stale files; verified freshness per-file (`ChatGroundingRetryTest.xml`: 8 tests, 0 failures/errors).

## Test Report

- `ChatGroundingRetryTest`: **8/8 pass** — offline→reconnect retry, history-untouched, row-reuse double-write + null no-op, no-inference proof (`runInference` exactly 1 = send only), Stop-during-retry queued-state restore, FETCH_FAILED ignore, overlap no-op, stale-connectivity no-fetch.
- Regression: `ui.chat.*` + `data.repository.*` + `data.grounding.*` — **102 tests, 0 failures, 0 errors** (`--rerun-tasks` clean run).

## Threat Flags

None — no new attack surface. Retry reuses the verbatim `WebPageFetcher` policy (stripped client, 64KB cap, timeouts, scheme allowlist); `findAssistantRowId` null → silent no-op (T-54-01); OFFLINE-only data gate (T-54-02); single `retryJob` + `isFetchingWeb` gate + Stop/send cancels (T-54-04); zero new dependencies (T-54-SC).

## Known Stubs

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Backend ready for the UI plan: `retryGrounding(assistantMessageId)` callable, `isValidatedOnline` flag consumable by the `ModelOnlyBanner` Reintentar slot, queued OFFLINE state already persisted by the send path.
- No blockers. Preview sheet needs zero changes (reads the same `grounded_sources` rows).

## Self-Check: PASSED

- All created/modified files exist on disk (verified via Read during execution).
- Commits `bd1150c` and `dba97f9` exist in `git log`.
- `replaceSources` present in `ChatRepositoryImpl.kt`; `findAssistantRowId` in `MessageDao.kt`; `retryGrounding` in `ChatViewModel.kt`; test file exceeds 80-line minimum (~380 lines).

---
*Phase: 54-offline-retry*
*Completed: 2026-09-28*
