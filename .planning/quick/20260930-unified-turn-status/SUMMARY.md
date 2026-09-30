# SUMMARY — unified turn status row + honest search progress

**Status:** complete (2/2 tasks)
**Date:** 2026-09-30
**Commits:** `760fac88` (Task 1), `e3d4fb9a` (Task 2)

## What was built

One unified loading indicator above the input bar. The old bottomBar slot held
two independent `if` blocks (fetch chip + tool-call row); the in-list trailing
"Pensando…" row (`ThinkingRow`) is gone and the streaming gap now renders in
the slot. Search turns show indeterminate **Searching… / Buscando…** instead of
the dishonest "0 of 5" (the DDG/Tavily single-fuse path emits
`WebFetchProgress(done = 0, total = N, perSource = [])` — nothing is 0-of-N
there); real URL fan-out keeps honest "N of M" counts.

- `TurnStatus.kt` (new): pure sealed contract
  `Tool(text)` / `FetchFanout(done, total)` / `FetchSingle` / `Searching` /
  `ThinkingGap` + `resolveTurnStatus(toolCallActive, isFetchingWeb, progress,
  isStreamingGap)`. Priority tool > fetch > gap; null renders nothing. Fetch
  branch: `perSource` non-empty → fan-out if `total > 1` else single (mirrors
  the old `isMulti` check); `perSource` empty → `Searching`; null progress →
  `FetchSingle` legacy fallback.
- `TurnStatusRow` (private, in `ChatScreen.kt`): one row in the same slot with
  the same visuals (16dp ring + 8dp gap + 14sp text, single-line ellipsis,
  trailing 8dp spacer only when non-null) and one `contentDescription` per
  state. Copy reuse: tool text verbatim, fan-out `reading_multi_fmt`,
  single `reading_page`, search new `searching`, gap `thinking_ellipsis`.
- New strings EN+ES: `searching` (Searching… / Buscando…), `searching_cd`
  (Searching. Tap Stop to cancel. / Buscando. Tocá Detener para cancelar.).
- Deletions complete: `showThinkingRow`, the `else if (showThinkingRow)`
  trailing item, `ThinkingRow`, `ChatListKeys.THINKING` — grep over
  `app/src/main/java` + `app/src/test` is CLEAN.
- Preserved: `trailingCount = if (showStreamingBubble) 1 else 0` is the ONLY
  math change; `isEmpty`/`showPill`/pin `LaunchedEffect` lines byte-identical
  (verified via diff — no +/- lines for those identifiers). `ChatViewModel`
  fetch/search emission untouched (including the harmless terminal done-update).

## Test results

- Task 1 gate: `TurnStatusTest` (10/10 truth-table cases) +
  `StringResourceParityTest` — green.
- Task 2 gate: `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL.
- Global: `./gradlew :app:testDebugUnitTest` — **845 tests, 0 failures,
  0 errors, 0 skipped across 75 suites** (includes the new `TurnStatusTest`).

## Deviations from plan

None — plan executed exactly as written. One self-corrected mid-task edit
accidentally dropped the streaming-bubble `MessageBubble` body; restored
verbatim in the same task before building (no commit captured the broken
state). One ES strings edit initially replaced the `reading_multi_cd_fmt`
plurals block instead of appending after it; restored immediately (final diff
shows exactly +2 keys per locale).

## On-device notes (not verifiable here — no adb)

- Priority tool > fetch/search > gap observable during a live turn.
- Search turns show indeterminate Searching…/Buscando… (never "0 of 5"); URL
  fan-out still shows real N-of-M counts.
- Streaming gap shows Thinking…/Pensando… in the slot; no in-list Pensando row.
- Single-row-vs-two-slot timing feel and indeterminate-vs-counts legibility
  need a real device check.
