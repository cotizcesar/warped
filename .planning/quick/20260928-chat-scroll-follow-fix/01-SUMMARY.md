---
audit_acknowledged:
  milestone: v2.5
  at: 2026-10-01
  status: unknown
---

# 01 — Chat Scroll Follow Fix: Execution Summary

**Status:** Complete — both plan tasks executed, committed, verified green.
**Date:** 2026-09-28
**Commits:** `ab89aa4` (Task 1), `16cf025` (Task 2)

## What was built

Root cause (one cause, two symptoms): the chat `LazyColumn` is NOT reversed,
so `scrollToItem(last)` / `animateScrollToItem(last)` pin the item TOP to the
viewport top. As the trailing streaming bubble grows downward, fresh tokens
land below the fold — follow looks stuck, and the Latest pill tap looks dead.

Fix in `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` only:

1. **`pinLastItemEnd(index)` suspend helper** (`LazyListState` extension):
   instant `scrollToItem(index)`, then reads `layoutInfo`, finds the visible
   item matching `index` (no-op unless it is the last visible item), computes
   `overflow = offset + size - viewportEndOffset`, and if `overflow > 0`
   applies `scrollBy(overflow)`. Guards `index < 0` and empty list.
2. **Follow effect**: `listState.scrollToItem(totalItems - 1)` replaced with
   `listState.pinLastItemEnd(totalItems - 1)`. LaunchedEffect keys,
   `snapToBottomOnNextContent || isAtBottom` gating, and the
   `hasNewContentBelow` latch are byte-identical — still instant per token,
   never animated (48-01 PERF-15 preserved).
3. **Latest pill onClick**: keeps the latch clearing as-is, then
   `animateScrollToItem(last)` followed by the same overflow math with
   `animateScrollBy(overflow)` so the tap visibly lands on newest content.
   `isAtBottom` derivation (48px threshold) and pill visibility rule untouched.

Out of scope respected: no `MessageBubble` or other-surface changes.

## Test results

- `./gradlew :app:assembleDebug` — **BUILD SUCCESSFUL**
- `./gradlew :app:testDebugUnitTest` — **BUILD SUCCESSFUL**, 300 tests across
  33 suites, 0 failures, 0 errors, 0 skipped (`no failing suites` confirmed
  from `app/build/test-results/testDebugUnitTest/*.xml`).

## Deviations from plan

**[Rule 3 — Blocking] Removed a bad proactive import.** While adding imports
for Task 1 I also added `import androidx.compose.foundation.lazy.animateScrollToItem`;
compilation failed with `Unresolved reference 'animateScrollToItem'` on the
import line itself — in this Compose version that symbol is not importable
from the lazy package (the existing pill call site resolves without it).
Removed that single import line; build went green. No behavior impact.

## On-device confirmation note (honest gate)

No adb/emulator exists in this environment, so end-to-end scroll motion
(sticky follow during a long streaming answer, pill tap landing on newest
tokens) **needs on-device confirmation**. Acceptance gate applied here:
code-level reasoning (top-pin → end-pin math) + `assembleDebug` +
full `testDebugUnitTest` regression suite green.
