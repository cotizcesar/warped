---
phase: quick-chat-scroll-follow-fix
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
autonomous: true
requirements:
  - SCROLL-FOLLOW-01
  - SCROLL-PILL-01
must_haves:
  truths:
    - "Streaming answer stays pinned to newest tokens while user is at bottom (no stuck page)"
    - "Latest pill tap lands viewport on newest content instead of looking dead"
    - "Stick-to-bottom gating and pill latch behavior unchanged (48-01 PERF-15 preserved)"
  artifacts:
    - path: "app/src/main/java/com/warped/ui/chat/ChatScreen.kt"
      provides: "pinLastItemEnd suspend helper + end-pinning follow + pill fix"
      contains: "pinLastItemEnd"
  key_links:
    - from: "app/src/main/java/com/warped/ui/chat/ChatScreen.kt follow effect (~line 232-245)"
      to: "listState layoutInfo viewportEndOffset"
      via: "scrollToItem then scrollBy overflow"
      pattern: "pinLastItemEnd"
---

<objective>
Fix chat auto-scroll follow + Latest pill dead-tap. LazyColumn is NOT reversed, so
scrollToItem(last) / animateScrollToItem(last) pin the item TOP to the viewport top;
as the trailing streaming bubble grows downward, fresh tokens land below the fold.
Add end-pinning (scroll to item, then scrollBy the item's overflow past viewport end)
in both the follow effect and the pill onClick. Keep all gating/latch logic identical.

Purpose: one root cause, two symptoms — follow stops looking stuck, pill tap visibly lands.
Output: patched ChatScreen.kt, compile + unit tests green.
</objective>

<execution_context>
@/home/cotizcesar/Documents/warped/.opencode/gsd-core/workflows/execute-plan.md
@/home/cotizcesar/Documents/warped/.opencode/gsd-core/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@app/src/main/java/com/warped/ui/chat/ChatScreen.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add pinLastItemEnd helper in ChatScreen.kt</name>
  <files>app/src/main/java/com/warped/ui/chat/ChatScreen.kt</files>
  <action>Add a private suspend helper near the top-level composables (e.g. above JumpToLatestPillOverlay, ~line 564) or as a LazyListState extension in ChatScreen.kt: suspend fun LazyListState.pinLastItemEnd(index: Int) — calls scrollToItem(index), then reads layoutInfo: finds the item with layoutInfo.visibleItemsInfo matching index; if it is the last visible item, computes overflow = offset + size - viewportEndOffset and if overflow > 0 calls scrollBy(overflow.toFloat()). Guard index < 0 and totalItemsCount == 0 (no-op). Import androidx.compose.foundation.lazy.LazyListState and androidx.compose.foundation.lazy.visibleItemsInfo if needed. No new dependencies, pure list-state math. Do NOT touch MessageBubble or any other surface.</action>
  <verify>
    <automated>./gradlew :app:assembleDebug 2>&1 | tail -5</automated>
  </verify>
  <done>pinLastItemEnd helper exists in ChatScreen.kt; assembleDebug compiles</done>
</task>

<task type="auto">
  <name>Task 2: End-pin follow effect + pill onClick</name>
  <files>app/src/main/java/com/warped/ui/chat/ChatScreen.kt</files>
  <action>Follow effect (lines 232-245): replace listState.scrollToItem(totalItems - 1) (line 241) with listState.pinLastItemEnd(totalItems - 1). Keep the LaunchedEffect keys, the snapToBottomOnNextContent || isAtBottom gating, and the hasNewContentBelow latch byte-identical otherwise — instant scrollToItem path per token, never animate (48-01 PERF-15). Pill onClick (lines 467-473): keep snapToBottomOnNextContent=false and hasNewContentBelow=false clearing as-is; after listState.animateScrollToItem(totalItems - 1) compute the same overflow from listState.layoutInfo (find visible item at totalItems - 1, overflow = offset + size - viewportEndOffset) and if overflow > 0 call listState.animateScrollBy(overflow.toFloat()). Do NOT change isAtBottom derivation (lines 87-95, 48px threshold) or the showPill visibility rule (line 254).</action>
  <verify>
    <automated>./gradlew :app:assembleDebug 2>&1 | tail -5 && ./gradlew :app:testDebugUnitTest 2>&1 | tail -8</automated>
  </verify>
  <done>Follow effect and pill onClick both end-pin; assembleDebug + full testDebugUnitTest green</done>
</task>

</tasks>

<verification>
./gradlew :app:assembleDebug passes; ./gradlew :app:testDebugUnitTest fully green.
Honest gate note: no adb/emulator in this environment, so end-to-end scroll motion
(sticky follow during a long streaming answer, pill tap landing on newest tokens)
needs on-device confirmation. Code-level reasoning (top-pin → end-pin math) +
compile + regression suite is the acceptance gate here.
</verification>

<success_criteria>
- pinLastItemEnd helper present in ChatScreen.kt
- Follow effect uses helper; gating/latch logic unchanged
- Pill onClick animates to item then animates overflow so tap lands on newest content
- assembleDebug + testDebugUnitTest green
- Out of scope respected: no MessageBubble changes, no other surfaces
</success_criteria>

<output>
Create `.planning/quick/20260928-chat-scroll-follow-fix/01-SUMMARY.md` when done
</output>
