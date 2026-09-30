# Quick-task plan: thinking join + live thinking + scroll hardening + hairline hunt

Three confirmed fixes + one bounded hunt. Each cause re-confirmed in code before planning (see Evidence per task). Task 1 defines the accumulation contract; Task 2 builds on it — run Task 1 first. Tasks 3 and 4 touch different files and are independent of 1/2.

Out of scope: thinking content quality, model behavior, loop/search mechanics, header/menu, other screens.

## Task 1 — Thinking join: no-separator append + regression test

**Evidence (confirmed in code):**
- `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt:493-497` — `onThought` currently does `thought.append(thinking)` with NO separator; `onText` (`:492`) emits `StreamToken.Delta(text)` directly. No `joinToString("\n")` exists anywhere in the thought path (grep over `app/src/main/java` → only hits in grounding/markdown, unrelated). The `"\n"`-join described in the brief is NOT present at these lines — either already fixed or line-shifted; executor re-reads the accumulation site before touching it.
- `extractThoughtContent` (`:969-976`) reads `message.channels["thought"]` per-message; `extractTextContent` (`:946-956`) joins multi-part text with `""` — the no-separator precedent.
- `Done(reasoning = thought...)` emitted at `:505` and `:510` stays the final in all tasks.
- Existing loop tests: `app/src/test/java/com/warped/data/local/inference/LiteRTLmLoopTest.kt` (fake-transport pattern to extend).

**Files:**
- `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt` (accumulation site only — ONLY if a separator is found)
- `app/src/test/java/com/warped/data/local/inference/LiteRTLmLoopTest.kt` (or new `LiteRTLmThinkingJoinTest.kt` if the existing file's fakes don't fit)

**Action:**
- Re-read the `onThought` accumulation site in `runToolLoop`. If ANY separator (`"\n"`, `" "`, `joinToString` with separator, `.append("\n")`) is found between thought deltas, remove it so thought deltas concatenate with NO separator — identical to text handling; newlines that arrive INSIDE a single delta are preserved verbatim (never stripped). If no separator exists, leave the site byte-identical.
- Add a regression test with a word-delta fake transport (e.g. deltas `"Hello"`, `" "`, `"world"`) asserting the accumulated `Done.reasoning` contains NO inserted newlines — exact expected string equality (e.g. `"Hello world"`, not `"Hello\n world"` or `"Hello\nworld"`). Also assert a delta carrying an interior newline (e.g. `"para1\npara2"`) passes through unchanged.

**Must-haves:**
- Word-delta fake turn → `Done.reasoning` equals the exact concatenation with zero inserted separators.
- Interior-newline delta → newline preserved in `Done.reasoning`.
- `Done` remains the sole carrier of final reasoning (no other emission added by this task).

**Verify:**
- `./gradlew :app:testDebugUnitTest --tests "com.warped.data.local.inference.*"`

## Task 2 — Live thinking: stream native thought deltas during generation

**Evidence (confirmed in code):**
- Today `onThought` ONLY feeds the local `StringBuilder` (`LiteRTLmProvider.kt:484,493-497`); `Done(reasoning=...)` at `:505`/`:510` is the only reasoning emission — nothing reaches the UI until the turn ends.
- `ChatViewModel.kt:909-925` — `StreamToken.Delta` branch throttles UI updates at 50ms (`tokenBuffer.joinToString("")` → `rawBuffer` → `parseThinkBlocks` → `streamingContent`/`streamingReasoning`); `:926-928` — `Done` flushes the buffer. `streamingReasoning` currently comes ONLY from `<think>`/`<channel|>` tags parsed out of Delta text.
- `StreamToken` sealed hierarchy (`app/src/main/java/com/warped/domain/model/StreamToken.kt:3-53`): `Delta`, `Done`, `Error`, `ToolStatus`, `ToolCompleted`, `ToolsUnsupported`. The VM `when (token)` at `ChatViewModel.kt:872` handles all six — any new token type MUST add a branch there AND in every other exhaustive `when` over `StreamToken` (find all by grep; the compiler will also fail on unhandled branches — keep it green, don't suppress).
- Planner locks the mechanism choice to: **new `StreamToken.Thinking(delta)` type through existing when-exhaustiveness** vs provider-side accumulation + periodic VM update. Prefer whichever touches fewer wrong layers; document the choice + why in the SUMMARY. Either way: throttle like content (~50ms or N-char batching, mirroring the Delta branch), `Done(reasoning)` stays the final authoritative value.

**Files:**
- `app/src/main/java/com/warped/domain/model/StreamToken.kt` (only if the `Thinking` type is chosen)
- `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt` (`runToolLoop` `onThought` forward path)
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` (new token branch / periodic update; `Done` stays final)
- Test file(s): extend `LiteRTLmLoopTest.kt` and/or VM test for the streaming path (whichever layer the mechanism touches)

**Action:**
- Implement live forwarding: native thought deltas update `streamingReasoning` DURING streaming, throttled like content (~50ms time-based or N-char count-based batching — mirror the existing Delta-branch pattern at `ChatViewModel.kt:909-925`, don't invent a new throttle idiom).
- `Done(reasoning)` remains the final: on `Done`, the streamed value is reconciled to the final reasoning (final-equals-streamed when nothing was lost).
- If the `StreamToken.Thinking(delta)` type is chosen: add the branch to EVERY exhaustive `when` over `StreamToken` (grep `is StreamToken\.` across `app/src/main` — remote-loop drivers, think-strip/passthrough maps must pass it through or handle it explicitly, never silently drop to else). If provider-side accumulation is chosen: document why it touches fewer layers.
- Do NOT change `parseThinkBlocks` tag parsing, `Done` emission sites, or loop/search mechanics.

**Must-haves:**
- Live accumulation: a fake turn emitting thought deltas mid-stream → `streamingReasoning` updates BEFORE `Done` arrives (assert intermediate state, not just final).
- Throttle: rapid successive thought deltas coalesce (fewer UI updates than deltas — assert update count < delta count, mirroring the 50ms content behavior).
- Final-equals-streamed: after `Done`, `streamingReasoning` equals `Done.reasoning` exactly.
- Full suite green with no new `else` branches swallowing token types (grep proves no `else ->` added to token `when`s).

**Verify:**
- `grep -rn "is StreamToken\." app/src/main/java --include="*.kt" -l` (every file accounted for)
- `./gradlew :app:testDebugUnitTest --tests "com.warped.data.local.inference.*" --tests "com.warped.ui.chat.*"`

## Task 3 — Scroll hardening: clamp + try/catch in the pin path

**Evidence (confirmed in code):**
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt:564-572` — `pinLastItemEnd(index)` calls `scrollToItem(index)` with NO clamp to `layoutInfo.totalItemsCount - 1` and NO try/catch. Guard only covers `index < 0` / empty list.
- `:246-259` — follow `LaunchedEffect` clears `snapToBottomOnNextContent = false` + `hasNewContentBelow = false` (`:253-254`) BEFORE `listState.pinLastItemEnd(totalItems - 1)` (`:255`): a throw strands the viewport with cleared flags and no latch — the exact strand mechanism.
- Prior art in-file: `ChatScreen.kt:92` already reads `info.totalItemsCount`; send handler snap (`:297`) stays as-is.

**Files:**
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` (`pinLastItemEnd` only)
- Test: pure helper or fake-state test — `pinLastItemEnd` is a `LazyListState` suspend extension (Compose runtime object); if directly unit-testable, test with fake states including lagging layout, else extract the clamp math to a pure function with its own truth table (precedent: `TurnStatus.kt` pure resolver + `TurnStatusTest.kt` truth table)

**Action:**
- In `pinLastItemEnd`: clamp the pin target to `layoutInfo.totalItemsCount - 1` (target beyond laid-out count → scroll to current end) and wrap the scroll calls in try/catch logging via Timber — never crash the `LaunchedEffect`. Layout-lag can never strand the viewport with cleared flags.
- Keep snap/gating/pill math byte-identical otherwise: `LaunchedEffect` keys (`:246-250`), flag-clearing order, `isAtBottom` derivation, `showPill` rule, pill onClick path — diff must show ONLY the clamp + try/catch inside `pinLastItemEnd`.

**Must-haves:**
- Pin target beyond laid-out count still scrolls to current end, no throw (lagging-layout fake state).
- Throwing list state → Timber log, `LaunchedEffect` survives (no crash propagation).
- `git diff` on `ChatScreen.kt` shows ONLY `pinLastItemEnd` internals changed; follow-effect, snap, pill lines byte-identical.

**Verify:**
- `git diff app/src/main/java/com/warped/ui/chat/ChatScreen.kt` (only `pinLastItemEnd` body changed)
- `./gradlew :app:assembleDebug`
- `./gradlew :app:testDebugUnitTest` (full suite green)

## Task 4 — Hairline hunt (BOUNDED): read the full Thinking-panel render path

**Evidence (confirmed in code):**
- `ChatInputBar.kt:55` — input `Surface` is flat `Color(0xFF2B2B29)` with NO shadow/elevation (grep `shadow|elevation|tonalElevation` → zero hits). The user's shadow theory is REFUTED — do NOT chase it, do NOT add shadows/elevations/borders anywhere.
- Scope to read: `MessageBubble` thinking header/panel, user-bubble `Surface` edge, `AnimatedVisibility` clip, fade gradient `Box`es, `TurnStatusRow` slot, `ChatInputBar` container, Scaffold/IME gaps. (Note: `MessageBubble.kt` location — resolve by glob; prior plans reference `ui/chat/components/MessageBubble.kt`.)

**Files:**
- READ-ONLY sweep first; modify ONLY the single site that draws the hairline (if found and illegitimate)

**Action:**
- Read the FULL render path around the Thinking panel open transition (all sites listed above). Find WHATEVER draws a hairline outside text: errant `Divider`/`HorizontalDivider`, background/`Surface` color seam, `shadow`/`elevation`/`border`, `indication`/ripple bleed, tonal overlay.
- Fix ONLY if the candidate is illegitimate (stray divider, wrong background, unclipped animation edge): remove/fix at that one site.
- STOP rule: if the only candidates are legitimate surface-contrast edges (e.g. user-bubble `0xFF121212` vs background), DO NOT restyle — report exact colors + positions in the SUMMARY and stop. No new shadows/elevations/borders introduced anywhere (grep-gate this).

**Must-haves:**
- Either: the one illegitimate hairline source removed (name the exact composable + line), OR a SUMMARY report with exact colors/positions of the legitimate edges and no code change.
- No new `shadow`/`elevation`/`border`/`Divider` tokens anywhere in the message path (grep proves it).

**Verify:**
- `grep -rn "Divider\|shadow\|tonalElevation\|\.border(" app/src/main/java/com/warped/ui/chat/ --include="*.kt"` (review output: only pre-existing legitimate hits, or the single fix)
- `./gradlew :app:assembleDebug`
- `./gradlew :app:testDebugUnitTest` (full suite green)

## Global verification

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Both must be green.

## Honest notes

- Thinking paragraph rendering (Task 1), live-panel timing/feel (Task 2), follow/pin behavior under real layout lag (Task 3), and the hairline itself (Task 4) all need on-device confirmation — no adb in this environment. Unit tests prove accumulation math, throttle behavior, clamp logic, and absence of stray tokens — not the visual bars.
- Task 1's `"\n"`-join premise did not match current code (`thought.append(thinking)` is already separator-free); the executor confirms on read — if a separator reappears at a shifted site it gets removed, otherwise the regression test is the deliverable that locks the contract.
