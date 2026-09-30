# Quick-task plan: unified turn status row + honest search progress

ONE unified loading indicator above the input bar; search counts honest (indeterminate "Searching…" for the single-fuse path, real "N of M" for URL fan-out). The in-list trailing "Pensando…" row + `ThinkingRow` are deleted; the streaming gap moves into the slot. Causes and copy verified in code before planning (see Evidence per task). Two tasks share `ChatScreen.kt` — run sequentially (Task 1 first: it defines the `TurnStatus` contract Task 2 consumes).

Out of scope: loop/fetch mechanics, budgets, prompts, download UI, citation taps, `setWebOverride`/persistence, thinking-bubble content.

## Task 1 — `TurnStatus` resolver + new strings (contract first)

**Evidence (confirmed in code):**
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt:284-319` — fetch chip in `bottomBar` slot (ring 16dp + 8dp gap + 14sp text); `:328-355` — tool-call row, same slot visuals + single-line ellipsis. These two `if` blocks are what the unified `TurnStatusRow` replaces.
- `ChatScreen.kt:236-242` — `showThinkingRow` (streaming + empty content + empty reasoning + `!isFetchingWeb` + `toolCallActive == null`); `trailingCount = showStreamingBubble || showThinkingRow`.
- `ChatScreen.kt:831-854` — `ThinkingRow()` uses `R.string.thinking_ellipsis` (`Thinking…` / `Pensando…`, values `:397` / values-es `:400`) with cd `R.string.cd_thinking_generating`. Same copy moves into the slot (D-02).
- `ChatViewModel.kt:617-626` — search turn sets `WebFetchProgress(done = 0, total = searchCount, perSource = emptyList())`: the "0 of N" lie (nothing is 0-of-N; DDG/Tavily single-fuse has no per-source progress). Final update `:649-660` sets `done = total` + populated `perSource` (stays harmless per D-03 — do NOT touch). URL fan-out path (`:449-453`, `:1120-1124`) sets `perSource = urls.map { … LOADING }` — real per-source counts, keeps "N of M".
- `ChatUiState.kt:102-106` — `WebFetchProgress(done, total, perSource)`; `:153-158` — `ChatListKeys.THINKING` (only referenced by `ChatScreen.kt:476`, verified by grep — safe to delete alongside).
- Strings in play: `reading_page` / `reading_multi_fmt` / `reading_page_cd` / `reading_multi_cd_fmt` (plurals, values `:201-206`), `cd_running_tool` (`:171`), `cd_thinking_generating` (`:181`). Parity test (`app/src/test/java/com/warped/i18n/StringResourceParityTest.kt`) checks `<string>` + `<string-array>` keys only — new `<string>` keys are auto-covered; no test change needed for parity itself.
- Precedent for pure helper + unit test: `webOverrideIndicator` (`ChatScreen.kt:817-822`) tested in `Phase53PolishTrioTest.kt:28-47`.

**Files:**
- NEW `app/src/main/java/com/warped/ui/chat/TurnStatus.kt` (pure resolver, no Compose imports)
- NEW `app/src/test/java/com/warped/ui/chat/TurnStatusTest.kt` (truth table)
- `app/src/main/res/values/strings.xml` + `app/src/main/res/values-es/strings.xml` (2 new keys each)

**Action (per D-01, D-03, D-04):**
- Define a pure sealed contract (planner locks final names; minimum shape):
  `Tool(text)`, `FetchFanout(done, total)`, `FetchSingle`, `Searching`, `ThinkingGap`, with
  `resolveTurnStatus(toolCallActive: String?, isFetchingWeb: Boolean, progress: WebFetchProgress?, isStreamingGap: Boolean): TurnStatus?` implementing priority **toolCallActive > isFetchingWeb > streaming-gap**, returning null when nothing is active (null renders nothing).
  - Fetch branch: `perSource` non-empty → fan-out (`FetchFanout(done, total)` if `total > 1`, else `FetchSingle` — mirrors today's `isMulti` check at `ChatScreen.kt:286`); `perSource` EMPTY → `Searching` (the DDG/Tavily single-fuse path, D-03). Null progress + `isFetchingWeb` → `FetchSingle` (same as today's legacy fallback).
  - `isStreamingGap` = today's `showThinkingRow` condition verbatim (streaming + empty content + empty reasoning + `!isFetchingWeb` + `toolCallActive == null`); the resolver takes it as a precomputed Boolean so the gap math stays in one place.
- New strings (EN canonical, ES exact parity — D-03, D-04):
  - `searching` = `Searching…` / `Buscando…`
  - `searching_cd` = `Searching. Tap Stop to cancel.` / `Buscando. Tocá Detener para cancelar.` (mirror the `reading_page_cd` cd pattern)
- Reuse for all other copy/cds: tool text passes through (`cd_running_tool` format), fan-out keeps `reading_multi_fmt` + `reading_multi_cd_fmt`, single keeps `reading_page` + `reading_page_cd`, gap keeps `thinking_ellipsis` + `cd_thinking_generating`.

**Must-haves (resolver truth table):**
- tool set (+ fetch + gap also true) → `Tool(toolCallActive)`; null renders path untouched.
- fetch + `perSource` non-empty + `total > 1` → `FetchFanout(done, total)` (counts pass through verbatim).
- fetch + `perSource` non-empty + `total <= 1` → `FetchSingle`; fetch + null progress → `FetchSingle`.
- fetch + `perSource` EMPTY (search path) → `Searching` regardless of `done`/`total` values (the `done = 0, total = 5` lie never surfaces).
- no tool + no fetch + gap true → `ThinkingGap`; all false → null.
- `values-es` carries exactly `searching` + `searching_cd` extra keys, nothing else (parity test green).

**Verify:**
- `./gradlew :app:testDebugUnitTest --tests "com.warped.ui.chat.TurnStatusTest" --tests "com.warped.i18n.StringResourceParityTest"`

## Task 2 — `TurnStatusRow` in the slot; delete trailing Pensando row + `ThinkingRow`

**Evidence (confirmed in code):**
- Slot site: `ChatScreen.kt:275-355` (`bottomBar` `Column`: fetch chip `:284-319` + tool row `:328-355`, each followed by `Spacer(8.dp)`). Single `TurnStatusRow` replaces both blocks (D-01).
- Trailing render: `ChatScreen.kt:460-479` (`showStreamingBubble` item else `showThinkingRow` item with `ChatListKeys.THINKING`); pin/pill: `:231` (`showStreamingBubble`), `:242-243` (`trailingCount`, `totalItems`), `:263-266` (`isEmpty`, `showPill`).
- Pill-behavior fact (verified by reading): gap state has `streamingContent`/`streamingReasoning` empty, so `isEmpty`/`showPill` never referenced `showThinkingRow` — removing the thinking term from `trailingCount` is the ONLY math change; `isEmpty` and `showPill` lines stay byte-for-byte (D-02).
- No test references `showThinkingRow`/`ThinkingRow`/`trailingCount`/`thinking_ellipsis` (grep over `app/src/test` → zero hits) — no existing suite updates expected; Task 1's new test + parity + full suite is the gate (D-05).

**Files:**
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` (slot rewrite ~275-355, trailing math ~231-243 + render ~460-479, delete `ThinkingRow` ~824-854)
- `app/src/main/java/com/warped/ui/chat/ChatUiState.kt` (delete `ChatListKeys.THINKING` ~155-157)
- `app/src/main/java/com/warped/ui/chat/TurnStatus.kt` (only as consumer — Task 1 defines it; do NOT redefine)

**Action (per D-01, D-02, D-04):**
- Replace the fetch-chip block and the tool-row block with ONE `TurnStatusRow` composable in the same slot, keeping the existing slot visuals exactly (16dp ring + 8dp gap + 14sp text, single-line ellipsis, trailing `Spacer(8.dp)` when non-null; null status renders nothing and no spacer):
  - `Tool(text)` → text verbatim, cd `cd_running_tool.format(text)`.
  - `FetchFanout(done, total)` → `reading_multi_fmt(done, total)`, cd `reading_multi_cd_fmt` plural.
  - `FetchSingle` → `reading_page`, cd `reading_page_cd`.
  - `Searching` → NEW `searching`, cd NEW `searching_cd` (indeterminate ring, no counts — D-03).
  - `ThinkingGap` → `thinking_ellipsis`, cd `cd_thinking_generating` (same copy `ThinkingRow` uses today — D-02).
  - Single `semantics { contentDescription = … }` per resolved state (D-04).
- Delete `showThinkingRow`, the `else if (showThinkingRow)` trailing item, the `ThinkingRow` composable, and `ChatListKeys.THINKING`. `trailingCount = if (showStreamingBubble) 1 else 0`; `isEmpty`/`showPill`/pin-`LaunchedEffect` otherwise untouched (gap keeps pill hidden exactly as today by construction — the gap never set `hasNewContentBelow` and `isEmpty` is unchanged).
- Do NOT touch `ChatViewModel` fetch/search emission (including the harmless final done-update), `MessageBubble`'s `isFetchingWeb` param, or any strings other than the two new keys.

**Must-haves:**
- Exactly one status row above the input bar in every active turn; priority tool > fetch/search > gap observable on-device.
- Search turns show indeterminate `Searching…`/`Buscando…` (never "0 of 5"); URL fan-out still shows real `N of M` counts.
- Streaming gap shows `Thinking…`/`Pensando…` in the slot; no in-list Pensando row exists (`ThinkingRow`, `showThinkingRow`, `ChatListKeys.THINKING` all gone — grep proves it).
- `isEmpty`/`showPill` lines byte-identical to today (diff check).

**Verify:**
- `grep -rn "ThinkingRow\|showThinkingRow\|ChatListKeys.THINKING" app/src/main/java || echo CLEAN`
- `git diff app/src/main/java/com/warped/ui/chat/ChatScreen.kt` shows `isEmpty`/`showPill` lines unchanged.
- `./gradlew :app:assembleDebug`
- `./gradlew :app:testDebugUnitTest` (FULL suite green — D-05)

## Global verification

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Both must be green.

## Honest notes

- Visual + timing feel (single row replacing two slots, gap copy in the slot vs in-list, indeterminate-vs-counts legibility) need on-device check — no adb in this environment.
- No Compose UI test asserts the bottomBar slot or trailing rows, so the slot rewrite is covered by the pure-resolver truth table + full-suite regression, not by UI assertions.
- Parity test does not cover `<plurals>` keys (`reading_multi_cd_fmt` invisible to it) — untouched here, but flagged for whoever next edits plural copy.
