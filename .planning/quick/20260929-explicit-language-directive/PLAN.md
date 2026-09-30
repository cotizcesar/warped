# Quick Task Plan: Explicit Language Directive (EN+ES)

## Cause (locked, on-device evidence)

Spanish question → fully English answer despite the probabilistic
"Always reply in the same language the user wrote in." rule in
`GroundingPrompt.SYSTEM_PROMPT` (and identically in
`LiteRTLmProvider.TOOL_USE_SYSTEM_HINT`). Probabilistic
instruction-following on a 2B model. FIX (locked): deterministic
language directive — detect Spanish in the user's message with a
high-precision heuristic and attach an EXPLICIT instruction line:
Spanish detected → `Responde en español.`; otherwise → `Reply in English.`
(product scope is EN+ES). The generic "same language" sentence is
REPLACED (not duplicated) by this explicit directive wherever user
language is concerned.

## Position lock (planner decision)

Directive goes LAST — appended after the original user text as the
final line of the augmentation. Justification (recency > primacy for a
2B model): the directive is the last thing the model reads before
generating, and it stays adjacent to the user text even when a ~4000-char
fused source block sits between SYSTEM_PROMPT and the question (a
first-line directive would be buried by long source blocks). Rejected
alternative: first-line/primacy placement — separated from the user text
by the full source block, weakest position on small models.

## Augment shapes after fix

- `block != null` → `"$SYSTEM_PROMPT\n\n$block\n\n$original\n\n$directive"`
- `block == null && groundingEnabled` → `"$SYSTEM_PROMPT\n\n$original\n\n$directive"`
- `block == null && !groundingEnabled` → `$original` untouched (unchanged)
- `directive` = `Responde en español.` if `isSpanish(original)` else `Reply in English.`

## Coverage argument (both paths carry it)

- ALL `ChatViewModel` call sites (~7: lines ~476, 589, 624, 652, 660, 668, 676)
  go through `GroundingPrompt.augment()` — wiring the directive into
  `augment()` covers heuristic (pre-search/DDG) AND loop-armed turns,
  local AND remote (remote inline loops receive the augmented user text
  in `requestMessages`).
- `TOOL_USE_SYSTEM_HINT` is provider-side only (`systemInstruction`,
  set ONLY when the loop is armed) and loop-armed ⇒ grounding on ⇒ the
  augmented user message (with directive) is always present. Therefore the
  generic sentence is REMOVED from the hint (rest byte-identical) with NO
  replacement there — the per-turn directive in the user message is the
  single source of language instruction (exactly-once, no duplication).
  Executor must verify the armed⇒grounded⇒augmented invariant holds at
  every call site (i.e. no armed turn can reach the model with
  `groundingEnabled = false` / unaugmented text); if a hole is found,
  surface it instead of silently duplicating the directive.

## Tasks

### Task 1 (auto): Detect + directive + wire into augment()

**Files:**
- `app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt`
- `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt`

**Action:**
1. In `GroundingPrompt`, add pure JVM-testable functions (no Android imports):
   `fun isSpanish(text: String): Boolean` — returns true iff text contains
   `¿` or `¡` or any of `á é í ó ú ñ ü` (case-insensitive: include
   `Á É Í Ó Ú Ñ Ü`; implement via a single precompiled case-insensitive
   construct, e.g. a Regex with `IGNORE_CASE` or explicit char set — keep it
   dependency-free). Empty string → false. This is the locked high-precision
   heuristic: markers essentially never occur in plain English; do NOT add
   dictionary/word-list detection (precision over recall per locked cause).
2. Add `fun languageDirective(text: String): String` returning exactly
   `Responde en español.` when `isSpanish(text)` else exactly
   `Reply in English.` (with trailing period, byte-exact).
3. Remove the generic sentence ` Always reply in the same language the user
   wrote in.` (including its leading space) from `SYSTEM_PROMPT`; keep every
   other byte identical.
4. Wire `augment(original, block, groundingEnabled)`: on both grounded
   branches compute `languageDirective(original)` from the ORIGINAL user text
   (never from the block — block language must not decide) and append it as
   the last line per the shapes above. Disabled branch returns `original`
   untouched.
5. In `LiteRTLmProvider.TOOL_USE_SYSTEM_HINT`, remove the identical generic
   sentence ` Always reply in the same language the user wrote in.`
   (leading space included); keep every other byte identical. Do NOT add the
   directive to `systemInstruction` (exactly-once rule; see coverage argument).
6. Verify the armed⇒grounded⇒augmented invariant at all `augment()` call
   sites in `ChatViewModel.kt`; confirm search queries still use the
   pre-augmentation `userMessage.content` (directive must not leak into
   search queries) and persisted history still keeps original text.
7. Update stale exact-string assertions that embed the old prompt shape:
   grep for `SYSTEM_PROMPT}` / `SYSTEM_PROMPT` / `same language` across
   `app/src/test/` — known affected: `SettingsTavilyTest` (~line 452),
   `ChatGroundingToggleTest` (~line 185), `ChatAlwaysSearchTest` (~line 352),
   `ChatLoopSourcesTest` (~lines 300-324), `ChatAttachmentSearchSkipTest`
   (~line 309, `doesNotContain` — likely still passes, confirm). Update
   expected strings to the new shape; do NOT weaken them to
   substring-only where exact equality existed.

**Verify:** `<automated>./gradlew :app:assembleDebug -q` succeeds with no
detekt/ktlint regressions in touched files.

**Done:** `augment()` emits exactly one explicit directive as the last line
on every grounded turn; generic sentence gone from both constants; English
product copy otherwise byte-identical; assemble green.

### Task 2 (auto): Tests — detection table, directive selection, assembly

**Files:**
- `app/src/test/java/com/warped/data/grounding/GroundingPromptTest.kt`
  (extend; replace the now-obsolete `system prompt requires replying in the
  user's language` test that asserts the generic sentence — it must assert
  ABSENCE instead)
- Any test files from Task 1 step 7 needing expectation updates

**Action:**
1. Detection table for `isSpanish` (parameterized or individual tests):
   ES-positive: `¿Cómo estás?`, `¡Hola!`, `está aquí`, `niño`, `pingüino`,
   uppercase `ÁRBOL` / `NIÑO` / `¿QUÉ?`; EN-negative plain:
   `What is the capital of France?`, `hello world`; mixed
   (`What is el niño?` → true — marker presence decides); empty string →
   false; strings with `?`/`!` but no markers/accents (e.g. `Really?`) →
   false (guards against ASCII-punctuation false positives).
2. Directive selection: ES input → exactly `Responde en español.`;
   EN/empty input → exactly `Reply in English.`.
3. Prompt-assembly tests: for block + no-block grounded paths assert the
   directive is present EXACTLY ONCE (count occurrences == 1), is the LAST
   line (index of directive > index of original text; output ends with the
   directive), and derives from the ORIGINAL text even when the block is in
   the other language (ES question + EN block → `Responde en español.`;
   EN question + ES block → `Reply in English.`).
4. Negative/guard tests: `SYSTEM_PROMPT` and `TOOL_USE_SYSTEM_HINT` do NOT
   contain `same language`; both still contain their other pinned sentences
   (`web_search`, `Never invent URLs`, `Answer with the provided sources` /
   gathered-context wording) to lock byte-identical remainder; disabled
   branch returns original untouched (no directive).
5. Keep English product copy intact: no test may introduce non-EN UI copy.

**Verify:** `<automated>./gradlew :app:testDebugUnitTest --tests
"com.warped.data.grounding.GroundingPromptTest"` green, then full
`<automated>./gradlew :app:testDebugUnitTest` green (no regressions in the
chat/VM suites that embed prompt expectations).

**Done:** Detection table + selection + exactly-once/last-line assembly
tests green; full `testDebugUnitTest` green.

## Honest notes

- A 2B model executes the instruction probabilistically — an explicit
  last-line directive raises compliance deterministically vs the generic
  rule but is never 100%; residual language drift on tiny models is
  expected.
- On-device ES/EN check still needed (no adb in this environment) —
  manual verification step, not covered by JVM tests.

## Out of scope

New locales beyond EN+ES; UI language (system locale, separate concern);
loop/search mechanics; word-list or ML language detection.
