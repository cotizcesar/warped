---
phase: 48-chat-perf-startup-release
plan: "01"
subsystem: ui
tags: [jetpack-compose, lazycolumn, stateflow, immutable, chat-performance, recomposition-isolation]

# Dependency graph
requires:
  - phase: 47-real-tool-execution
    provides: Tool-turn UI states (toolCallActive, activeToolError, showNoToolSupportNotice, skill chips, Role.TOOL rows) that the split accommodates and the keyed list renders
provides:
  - Three single-owner @Immutable chat sub-states with isolated StateFlows (PERF-14)
  - Keyed LazyColumn chat list with stick-to-bottom gating + Jump-to-latest pill (PERF-15)
  - Unit proofs of recomposition isolation and key stability
affects: [48-02-startup, 48-03-release, future chat UI work]

# Tech tracking
tech-stack:
  added: []
  patterns: [single-owner Immutable sub-states with combine-derived compat shim, keyed LazyColumn with constant synthetic trailing keys, LazyListState.layoutInfo stick gating with latch]

key-files:
  created:
    - app/src/test/java/com/warped/ui/chat/ChatSubStateTest.kt
    - app/src/test/java/com/warped/ui/chat/ChatKeyStabilityTest.kt
  modified:
    - app/src/main/java/com/warped/ui/chat/ChatUiState.kt
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt

key-decisions:
  - "Kept a deprecated combine-derived uiState shim for one phase instead of deleting the monolith (existing tests keep passing unmodified)"
  - "isGenerating mirror on the input flow tracks isStreaming at turn boundaries so ChatInputBar never reads the transcript flow"
  - "Pill tap uses animateScrollToItem (user-initiated); all automatic scroll uses scrollToItem gated on isAtBottom"

patterns-established:
  - "Sub-state split: transcript (streaming/tool/error) vs input (draft/skills/thinking) vs connection (models/endpoints/prefs/dialogs), each with single-owner updaters"
  - "Trailing LazyColumn items use constant ChatListKeys, never content hashes"

requirements-completed: [PERF-14, PERF-15]

# Metrics
duration: 55min
completed: 2026-09-28
---

# Phase 48 Plan 01: ATOMIC Chat Perf Summary

**30-field ChatUiState split into three @Immutable single-owner sub-states with isolated StateFlows, chat list migrated from Column+verticalScroll to keyed LazyColumn with stick-to-bottom gating and a spec-exact Jump-to-latest pill — zero new dependencies**

## Performance

- **Duration:** 55 min
- **Started:** 2026-09-28T03:36:58Z
- **Completed:** 2026-09-28T04:32:00Z
- **Tasks:** 3
- **Files modified:** 5 (3 modified, 2 created)

## Accomplishments

- PERF-14: `ChatTranscriptState` / `ChatInputState` / `ChatConnectionState` (`@Immutable`, single-owner updaters) replace the monolith as source of truth; streaming tokens never touch the input flow, keystrokes never touch the transcript flow
- PERF-15: keyed `LazyColumn` (`items(messages, key = { it.id })`, constant `ChatListKeys` trailing items), per-token `animateScrollTo(maxValue)` defect deleted, `scrollToItem` gated on `isAtBottom` (48dp threshold) with `hasNewContentBelow` latch + send/open snap flag
- Jump-to-latest pill exactly per 48-UI-SPEC §2 (coral fill, `Latest` label, 150ms fade, polite live region, hidden on empty state and at bottom)
- All Phase 47 tool rows (status, error, transcript, no-support notice) render byte-identically as keyed items
- Full unit suite green: 263 tests, 0 failures (6 new tests added)

## Task Commits

Each task was committed atomically:

1. **Task 1: Split ChatUiState into three single-owner @Immutable sub-states** - `c2e8447` (feat)
2. **Task 2: Migrate chat list to keyed LazyColumn with stick logic and Jump-to-latest pill** - `0db991d` (feat)
3. **Task 3: Prove recomposition isolation and key stability with unit tests** - `ee70f5c` (test)

## Files Created/Modified

- `app/src/main/java/com/warped/ui/chat/ChatUiState.kt` - Three `@Immutable` sub-states, `ChatListKeys` constants, `combineSnapshot` derivation, pair-based traffic-light functions, deprecated monolith shim
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` - Three `MutableStateFlow`s with single-owner updaters; all ~45 `_uiState` sites re-homed; deprecated combine+stateIn `uiState` shim
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` - Per-sub-state collectors, keyed `LazyColumn`, stick gating + latch, `JumpToLatestPill` + overlay wrapper, unchanged Phase 47 rows/fades/empty state/dialogs
- `app/src/test/java/com/warped/ui/chat/ChatSubStateTest.kt` - Bidirectional isolation proof (2 tests)
- `app/src/test/java/com/warped/ui/chat/ChatKeyStabilityTest.kt` - UUID uniqueness, mapper id preservation, constant trailing keys (4 tests)

## Decisions Made

- **Deprecated combine-derived `uiState` shim instead of monolith deletion:** the pre-task grep proved zero external screens collect `ChatViewModel.uiState` (all other `uiState` collectors belong to their own ViewModels), but existing chat tests assert against `vm.uiState`; the shim keeps them green unmodified for one phase, then goes away.
- **`isGenerating` mirror on the input flow:** `ChatInputBar` previously read `uiState.isStreaming`; reading `transcript.isStreaming` would recompose the bar per token. The mirror flips at exactly the turn-boundary points where `isStreaming` flips, preserving behavior with isolation.
- **Pill tap uses `animateScrollToItem`, automatic scroll uses `scrollToItem`:** UI-SPEC §2 says tap smooth-scrolls; §3 bans per-token animation. Both hold.
- **`supportsThinking` lives on the input flow but is written by selection collectors:** single-owner means one writer group per field, not one flow per writer — the field's only writers are the two selection collectors.
- **No Room schema/id migration:** `MessageEntity.toDomain()` already maps `id.toString()` (stable across reloads); `ChatMessage.id` defaults to a UUID. Verified, not changed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] ColumnScope.AnimatedVisibility overload trap**
- **Found during:** Task 2 (ChatScreen list migration)
- **Issue:** Calling `AnimatedVisibility` directly inside `ChatScreen`'s `Column` bound the `ColumnScope.AnimatedVisibility` overload, which cannot run in the nested `Box` scope — compilation failed.
- **Fix:** Extracted a file-level `JumpToLatestPillOverlay` wrapper composable so the fade resolves to the top-level `AnimatedVisibility`. No visual or behavioral change.
- **Files modified:** `app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
- **Verification:** `:app:compileDebugKotlin` green
- **Committed in:** `0db991d` (Task 2 commit)

**2. [Rule 1 - Bug] Test asserted connection-flow silence across conversation creation**
- **Found during:** Task 3 (isolation test)
- **Issue:** First version of the streaming test captured the connection reference before `sendMessage`; `ensureConversation` legitimately writes `conversationModelId/ProviderType` once at turn creation, failing the over-strong assertion. Production behavior was correct.
- **Fix:** Capture the connection reference mid-stream (past the one-time turn-boundary write) and assert it never moves again during token streaming + Done.
- **Files modified:** `app/src/test/java/com/warped/ui/chat/ChatSubStateTest.kt`
- **Verification:** `ChatSubStateTest` 2/2 green
- **Committed in:** `ee70f5c` (Task 3 commit)

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 bug — one in a test, none in prod behavior)
**Impact on plan:** No scope creep. No prod-behavior deviation from the plan; both fixes were necessary for compilation and test correctness.

## Issues Encountered

- `supportsThinking` placement straddled the input/connection boundary (consumed with input, written by selection collectors) — resolved per the single-writer-group decision above, no plan change needed.
- `newConversation` originally cleared three connection fields alongside transcript fields — preserved exactly by splitting into two updater calls (caught during edit review before compiling).

## Threat Flags

None — the only new surface is the pill (pure presentation, no data) and the sub-state flows (same data, narrower routing). T-48-01/T-48-02 mitigations (stable UUID keys, constant `"streaming"` key) are implemented and unit-proven; T-48-03 accepted per plan (no ordering logic changed).

## Known Stubs

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- PERF-14/PERF-15 complete and unit-proven; ready for 48-02 (startup) and 48-03 (release), which touch disjoint files.
- Manual on-hardware verification still open per plan (Layout Inspector recomposition check, 100+ message fling): input-bar recomposition count must not move during streaming; no text-in-wrong-bubble during fast fling.
- Follow-up (one phase later): delete the deprecated `ChatUiState` monolith + `uiState` shim and migrate the two existing chat test classes to the sub-state flows.

## Self-Check: PASSED

- `ChatUiState.kt`, `ChatViewModel.kt`, `ChatScreen.kt`, `ChatSubStateTest.kt`, `ChatKeyStabilityTest.kt` all FOUND on disk
- Commits `c2e8447`, `0db991d`, `ee70f5c` all FOUND in `git log`
- No unintended file deletions in any task commit

---
*Phase: 48-chat-perf-startup-release*
*Completed: 2026-09-28*
