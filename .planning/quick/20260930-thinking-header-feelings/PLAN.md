# Quick-task plan: thinking accumulation + header declutter + feelings gate

Three independent fixes, causes verified on-device 2026-09-30 and confirmed in code before planning (see Evidence per task). No shared files between tasks — run in any order, sequentially or parallel.

Out of scope: thinking content quality, model behavior, loop/search mechanics, other screens.

## Task 1 — Thinking word-per-line: append deltas directly

**Evidence (confirmed in code):**
- `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt:485` — `onText` emits each delta directly via `emit(StreamToken.Delta(text))`, no joining; answers render fine on-device.
- `LiteRTLmProvider.kt:486-491` — `onThought` accumulates into a `StringBuilder` inserting `"\n"` between emissions.
- `LiteRTLmProvider.kt:691-694` (`ConversationTurnTransport.collectTurn`) — each engine message's thought is forwarded via `onThought(thinking)` per message, i.e. deltas, mirroring the `onText` delta path. The `"\n"` join in `runToolLoop` is the sole cause of word-per-line rendering.

**Files:**
- `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt` (lines ~486-491 only)
- `app/src/test/java/com/warped/data/local/inference/LiteRTLmLoopTest.kt` (extend; fake-transport pattern at ~352-380 already exists — add a word-delta thought case)

**Action:**
- In `runToolLoop`, change the `onThought` accumulator to append the delta directly with no separator (identical to the `onText` path at line 485). Newlines that arrive *inside* a delta are preserved as-is; do not strip or add any separator. No other change to the loop, `Done(reasoning=...)` emission, or transport.

**Must-haves:**
- Word deltas `["Cómo", " te", " puedo", " ayudar"]` accumulate to `"Cómo te puedo ayudar"` (no `"\n"` inserted).
- A delta containing an interior newline (e.g. `"línea1\nlínea2"`) keeps it verbatim.
- Empty deltas still skipped.

**Verify:**
- New test: fake transport emitting per-word thought deltas through `runToolLoop`, asserting `Done.reasoning` equals the plain concatenation.
- `./gradlew :app:testDebugUnitTest --tests "com.warped.data.local.inference.LiteRTLmLoopTest"`

## Task 2 — Header declutter: move web tri-state into ModelSelectorSheet

**Evidence (confirmed in code):**
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt:819-886` — `InlineModelSelectorBar` renders the override-state dot (819-836) plus the ⋮ overflow menu with Sí/No/Heredar items (837-886). This is the clutter to remove.
- `ChatScreen.kt:614-625` — the single `ModelSelectorSheet` call site (verified: no other call sites in `app/src/main`; definition at `ui/chat/components/ModelSelector.kt:47` has model-list params only, no web row).
- State/callbacks to reuse unchanged: `connection.webOverride`, `connection.webGroundingEnabled`, `viewModel.setWebOverride` (wired at `ChatScreen.kt:410-412`); existing strings `R.string.web_on/web_off/web_inherit/web_inherit_on/web_inherit_off`; `ChatGroundingToggleTest` covers `setWebOverride` behavior — keep green.

**Files:**
- `app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt` (add tri-state web row to sheet)
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` (remove dot + ⋮ menu from `InlineModelSelectorBar` ~819-886; pass `webOverride`/`globalWebEnabled`/`onWebOverrideSelected` into `ModelSelectorSheet` at ~614; the ∨ at ~806-811 keeps opening the sheet as today)
- Tests: extend `app/src/test/java/com/warped/ui/chat/Phase53PolishTrioTest.kt` or nearest sheet/VM test

**Action:**
- `ModelSelectorSheet` gains a web tri-state row (Sí / No / Heredar + live inherit hint from `globalWebEnabled`) with the same semantics as the current menu items; reuse the existing strings and the `WebOverrideIndicator` mapping (move or keep the helper where cheapest — callbacks must stay `setWebOverride(Boolean?)` with identical values).
- `InlineModelSelectorBar` keeps ONLY the traffic dot (~799-804) + ∨ arrow (~806-811). Remove the override dot, the ⋮ button, the `DropdownMenu`, and the now-unused `webMenuExpanded` state; drop or stop passing the web params into the bar (caller's cheapest consistent choice, as long as nothing else references them).
- Because the sheet has exactly one call site (the chat usage), no scoping decision is needed — add the row directly with required params.
- Do NOT change `setWebOverride` in `ChatViewModel`, persistence, or toggle resolution logic.

**Must-haves:**
- Sheet shows a web row with three selectable options (Sí/No/Heredar) + inherit hint reflecting the global setting; selecting each calls `onWebOverrideSelected(true/false/null)` respectively.
- Header bar contains no override dot and no ⋮ web menu (traffic dot + arrow only).
- `ChatGroundingToggleTest` + `Phase53PolishTrioTest` green (update the latter only if it asserts on removed bar elements; `webOverrideIndicator` mapping tests stay valid wherever the helper lives).

**Verify:**
- `./gradlew :app:testDebugUnitTest --tests "com.warped.ui.chat.ChatGroundingToggleTest" --tests "com.warped.ui.chat.Phase53PolishTrioTest"`
- Manual on-device check required (no adb here): open chat, confirm header shows only traffic dot + arrow, ∨ opens sheet with web row, each option persists per chat.

## Task 3 — Feelings/smalltalk searched: add self-state category to NeedsWeb

**Evidence (confirmed in code):**
- `app/src/main/java/com/warped/data/grounding/NeedsWeb.kt` — `WORDS` (28-49) and `PHRASES` (52-74) have no feelings/self-state category; `como estas` / `que tal` exist but `como te sientes`, `how are you`, `how do you feel` do not.
- Matching pipeline (76-98): NFD-normalize → strip combining marks → lowercase → split on non-letters → whole-token (WORDS) / token-subsequence (PHRASES). Diacritic-insensitive and word-boundary by construction — new entries inherit both.

**Files:**
- `app/src/main/java/com/warped/data/grounding/NeedsWeb.kt` (WORDS/PHRASES additions only; do NOT touch `needsWeb()`/`normalize()`/`containsSubsequence()`)
- `app/src/test/java/com/warped/data/grounding/NeedsWebTest.kt` (extend both `@ValueSource` lists + a negative-guard test)

**Action:**
- Add feelings/self-state entries (stored WITHOUT diacritics, per file convention):
  - PHRASES: `como te sientes`, `como se siente` (usted form), `how are you`, `how do you feel`, `how are you doing`, `estas bien` — planner locks the final list; minimum must include the ES `como te sientes` family and EN `how are you` / `how do you feel` family.
  - WORDS: only if a single token is unambiguous (none proposed — keep to phrases unless planner finds one).
- False-positive guard (locked): factual `cómo está X` (`como esta el clima`, `como esta la economia`) must STILL search. The token-subsequence design already protects this as long as no bare `como esta` phrase is added — do NOT add a `como/esta` bigram; the feelings entries all contain ≥3 tokens or distinctive tokens (`sientes`, `feel`, `doing`) that factual queries lack. Add `como esta el clima` (and accentless variant) to the factual list as a regression guard.

**Must-haves (truth table):**
- `needsWeb = false`: `cómo te sientes`, `como te sientes?`, `how are you`, `How are you?`, `how do you feel`, `how are you doing`, plus existing cases unchanged.
- `needsWeb = true` (regression): `cómo está el clima`, `como esta el clima`, `quién ganó el partido`, `holanda`, plus existing factual cases unchanged.

**Verify:**
- `./gradlew :app:testDebugUnitTest --tests "com.warped.data.grounding.NeedsWebTest"`

## Global verification

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Both must be green.

## Honest notes

- Thinking paragraphs: unit test proves concatenation, but paragraph/newline rendering inside the thinking bubble needs on-device confirmation (no adb in this environment).
- Header declutter: no Compose UI test asserts the bar layout — relocation is covered by callback/row-presence unit tests plus required manual on-device check.
- Feelings gate: truth-table is fully unit-testable; that real user phrasings (`cómo te sientes hoy con…`) route correctly end-to-end needs on-device confirmation.
