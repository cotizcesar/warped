---
phase: quick-code-intent-gate
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/warped/data/grounding/CodeIntent.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt
  - app/src/test/java/com/warped/data/grounding/CodeIntentTest.kt
  - app/src/test/java/com/warped/ui/chat/ChatCodeIntentGateTest.kt
  - app/src/test/java/com/warped/data/local/inference/LiteRTLmLoopTest.kt
autonomous: true
requirements: [CODE-INTENT-01, CODE-INTENT-02, CODE-INTENT-03]
must_haves:
  truths:
    - "On-device: 'Dame un ejemplo de codigo simple en jsavascript' skips the pre-search and the model writes code from weights (no 'based on Source [5]' hedge)"
    - "'write a quicksort in python' skips the pre-search (code noun + language name)"
    - "'Kotlin 2.3 new features' still SEARCHES (code noun but no action verb, no language name)"
    - "'write an email' still SEARCHES (action verb but no code noun)"
    - "Factual queries ('que es la fotosintesis') still SEARCH exactly as today"
    - "Social turns still skip via the NeedsWeb gate, unchanged"
    - "The armed agentic loop can still web_search (escape hatch for versioned API facts)"
    - "./gradlew :app:testDebugUnitTest and :app:assembleDebug are green"
  artifacts:
    - path: "app/src/main/java/com/warped/data/grounding/CodeIntent.kt"
      provides: "Pure code-intent detector (isCodeTurn), JVM-testable"
    - path: "app/src/main/java/com/warped/ui/chat/ChatViewModel.kt"
      provides: "Combined no-URL skip: social OR code"
    - path: "app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt"
      provides: "TOOL_USE_SYSTEM_HINT with code-from-knowledge clause"
  key_links:
    - from: "app/src/main/java/com/warped/ui/chat/ChatViewModel.kt"
      to: "CodeIntent.isCodeTurn"
      via: "no-URL branch combined skip with NeedsWeb"
      pattern: "CodeIntent\\.isCodeTurn"
    - from: "app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt"
      to: "systemInstruction"
      via: "TOOL_USE_SYSTEM_HINT pinned on armed turns"
      pattern: "TOOL_USE_SYSTEM_HINT"
---

<objective>
Stop the heuristic pre-search from hijacking code-generation turns the model
handles better alone. A pure CodeIntent detector gates the same no-URL
pre-search branch NeedsWeb already gates (single combined skip: social OR
code), and one clause is added to TOOL_USE_SYSTEM_HINT so code comes from
knowledge first.

Purpose: on-device evidence — "Dame un ejemplo de codigo simple en
jsavascript" web-searched a tutorial and the answer hedged "based on Source
[5]" instead of writing code from weights. Code-generation turns must reach
the model clean.
Output: new CodeIntent helper + gate wiring + hint clause + tests, build +
full suite green.
</objective>

<execution_context>
@/home/cotizcesar/Documents/warped/.opencode/gsd-core/workflows/execute-plan.md
@/home/cotizcesar/Documents/warped/.opencode/gsd-core/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@app/src/main/java/com/warped/data/grounding/NeedsWeb.kt
@app/src/test/java/com/warped/data/grounding/NeedsWebTest.kt
@app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
@app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt
@app/src/test/java/com/warped/ui/chat/ChatNeedsWebGateTest.kt
@app/src/test/java/com/warped/data/local/inference/LiteRTLmLoopTest.kt
</context>

<principles>
PRINCIPLE (locked, user): search runs when the MODEL lacks info — the model
always keeps its tool opportunity. The armed loop REMAINS the model's escape
hatch: this gate touches the heuristic VM pre-search ONLY. Loop arming
(computeArmSnapshot / ConversationConfig.tools), the URL-fetch branch, and
GroundingPrecedence are untouched. The model can still web_search mid-turn
for versioned/fresh API facts — state this in code comments at the gate site.
Fail-open: empty or uncertain messages return needs-search TRUE (search as
today), mirroring NeedsWeb.
</principles>

<tasks>

<task type="auto">
  <name>Task 1: CodeIntent detector mirroring NeedsWeb (CODE-INTENT-01)</name>
  <files>app/src/main/java/com/warped/data/grounding/CodeIntent.kt, app/src/test/java/com/warped/data/grounding/CodeIntentTest.kt</files>
  <action>Create object CodeIntent in com.warped.data.grounding as a pure Kotlin helper mirroring NeedsWeb.kt conventions exactly: NFD-normalize + strip combining marks (diacritic-insensitive: codigo == código) THEN lowercase THEN split on non-letters into letter-tokens; whole-token matching only, never substrings. Expose fun isCodeTurn(query: String): Boolean — TRUE when the turn is a code-generation request (caller SKIPS the pre-search), FALSE otherwise; empty/blank token list returns FALSE (fail-open: searches as today). Skip rule is a CONJUNCTION: at least one CODE NOUN token AND (at least one ACTION VERB token OR at least one LANGUAGE NAME token). CODE NOUNS (ES+EN, stored WITHOUT diacritics): codigo, code, script, funcion, function, programa, program, clase, class, metodo, method, bug, error. ACTION VERBS (ES+EN, stored WITHOUT diacritics): ejemplo, example, escribe, write, genera, generate, crea, create, haz, make, implementa, implement, corrige, fix, depura, debug, explica. Rationale to document in the KDoc: the noun+verb/name conjunction keeps "Kotlin 2.3 new features" searching (noun kotlin-adjacent? no — language name alone without noun or verb still needs the noun, so it SEARCHES, correct) and "write an email" searching (verb without code noun, correct), while "write a quicksort in python" skips (noun quicksort? no — VERB write + LANGUAGE python still needs the NOUN; see note). NOTE on quicksort: "write a quicksort in python" has verb + language but no listed noun — the conjunction as specified (noun AND (verb OR language)) would NOT fire. Handle per planner lock: treat LANGUAGE NAME presence as satisfying the noun arm too ONLY when paired with an action verb (language+verb implies code intent), i.e. skip when (noun AND (verb OR language)) OR (verb AND language). Document this explicitly in the KDoc with the three canonical examples. LANGUAGE NAMES (lowercase tokens, split c++/c# on non-letters so "c++" tokenizes as "c" — therefore match "c" ONLY as part of a two-token check? Simpler per planner lock: list language tokens as normalized single tokens: javascript, typescript, python, java, kotlin, swift, go, rust, php, ruby, sql, html, css, dart, plus "c" and "csharp"/"c sharp" handling? Keep minimal: include "c" ONLY when adjacent to a verb token is too complex — instead list: javascript, typescript, python, java, kotlin, swift, golang, rust, php, ruby, sql, html, css, dart, cpp, csharp. "c++" normalizes to tokens ["c"] which does NOT match "cpp" — acceptable documented limitation, do NOT substring-match "c". "c#" likewise tokenizes to ["c"] and does not match — documented limitation). Typo tolerance: NO fuzzy matching beyond the existing pipeline (NFD + token boundaries); the "jsavascript" evidence case MUST skip — achieve it WITHOUT Levenshtein: "jsavascript" contains no listed token, so add explicit handling documented in KDoc: a language-prefix heuristic where a token starting with "javascript", "typescript", "python", "kotlin" (startsWith on the token, anchored at token start — NOT substring) counts as that language. Limit startsWith to these four long names to avoid "go"/"c" over-fire; "jsavascript" startsWith "j"? No — "jsavascript" does not start with "javascript" (extra s after j). Recheck: j-s-a-v-a-s-c-r-i-p-t vs j-a-v-a-s-c-r-i-p-t — startsWith fails. Planner lock alternative: strip a single duplicated-letter typo? Over-engineering. Simplest correct lock: ALSO match tokens that contain "avascript" or "ython" or "kotlin" as substring within a single token (token-scoped contains, still never across tokens)? That fires "jsavascript" (contains "avascript") while whole-token matching stays for everything else. Document as the single deliberate token-scoped exception with rationale (transposed/doubled-letter typos in long language names). Keep the exception list: avascript, ython, ypescript, otlin. Then write CodeIntentTest mirroring NeedsWebTest truth-table style (ParameterizedTest + ValueSource + Truth): code-skip cases INCLUDING the verbatim evidence string "Dame un ejemplo de codigo simple en jsavascript" plus "write a quicksort in python", "escribe una funcion en javascript", "genera una clase en kotlin", "corrige este bug", "depura mi script python", "implementa un programa en java", "explica este codigo", "create a class in typescript", "debug my ruby script"; still-search cases: "Kotlin 2.3 new features" (language alone, no verb/noun arm), "write an email" (verb alone), "que es la fotosintesis", "what is the capital of France", "hola" (social — NeedsWeb's job, CodeIntent returns FALSE here: no code intent), "" and "   " (fail-open FALSE); boundary test "codigos"? token "codigos" != "codigo" so FALSE (still searches) — document whole-token behavior; diacritic test "código/función/método" TRUE.</action>
  <verify><automated>./gradlew :app:testDebugUnitTest --tests "com.warped.data.grounding.CodeIntentTest" --tests "com.warped.data.grounding.NeedsWebTest"</automated></verify>
  <done>CodeIntent.isCodeTurn skips the evidence string verbatim and all code table cases, searches factual/email/language-alone/empty cases; NeedsWebTest still green (untouched behavior).</done>
</task>

<task type="auto">
  <name>Task 2: Gate wiring — combined social OR code skip + hint clause (CODE-INTENT-02, CODE-INTENT-03)</name>
  <files>app/src/main/java/com/warped/ui/chat/ChatViewModel.kt, app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt</files>
  <action>ChatViewModel.kt — at the existing needs-web-gate site (~line 551, inside the `else if (images.isEmpty() && audioBytes == null)` no-URL branch): change the single gate into a combined skip `if (!NeedsWeb.needsWeb(content) || CodeIntent.isCodeTurn(content))` with an updated comment stating (a) social OR code turns skip the pre-search with no socket/no credit/no notice (identical to grounding-off for this turn), (b) the armed loop REMAINS the model's escape hatch — it can still web_search mid-turn for versioned/fresh API facts — this gate touches pre-search only. URL-fetch branch above, loop arming, GroundingPrecedence.read, and the attachments-skip above are untouched. LiteRTLmProvider.kt — append exactly one clause to TOOL_USE_SYSTEM_HINT (CODE-INTENT-03, planner-locked wording, keep the existing string-concat style): "Write code from your own knowledge first; call web_search only for fresh or versioned API facts." Place it after the per-turn-independence sentences, before the greetings sentence. Do NOT change the greetings clause or IDENTITY_LINE. Update the LiteRTLmLoopTest pinned-verbatim assertion if it pins the full hint string (check first with grep for TOOL_USE_SYSTEM_HINT in the test).</action>
  <verify><automated>grep -n "CodeIntent.isCodeTurn" app/src/main/java/com/warped/ui/chat/ChatViewModel.kt && grep -c "Write code from your own knowledge first" app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt</automated></verify>
  <done>Combined gate present at the no-URL branch with escape-hatch comment; hint clause present verbatim once; loop-test pin updated if it pins the string.</done>
</task>

<task type="auto">
  <name>Task 3: Gate-integration tests + hint pin + full suite (CODE-INTENT-01, CODE-INTENT-02, CODE-INTENT-03)</name>
  <files>app/src/test/java/com/warped/ui/chat/ChatCodeIntentGateTest.kt, app/src/test/java/com/warped/data/local/inference/LiteRTLmLoopTest.kt</files>
  <action>New ChatCodeIntentGateTest mirroring ChatNeedsWebGateTest scaffolding (copy buildViewModel/answeringHelper/groundedOutcome verbatim patterns — do NOT refactor the existing test file): (1) code turn skips — send the verbatim evidence "Dame un ejemplo de codigo simple en jsavascript", assert coVerify(exactly=0) ddgSearchRepository.search, exactly 1 fetcher.hasValidatedInternet (init-only), outgoing request content equals the original text with no GroundingPrompt.SYSTEM_PROMPT, assistant row completes with modelOnlyNotice null and groundedSources empty; (2) factual unchanged — "que es la fotosintesis?" runs search exactly once and fuses ("--- Source [1]" in request); (3) code+URL still fetches — "escribe una funcion https://example.com/x" calls multiUrlFetcher.fetchAll once and zero searches (gate lives in no-URL branch only). Hint pin: extend LiteRTLmLoopTest (or the new test file if the pin lives elsewhere — grep first) with one assertion that TOOL_USE_SYSTEM_HINT contains "Write code from your own knowledge first". Then run the FULL unit suite plus assembleDebug.</action>
  <verify><automated>./gradlew :app:testDebugUnitTest :app:assembleDebug</automated></verify>
  <done>All three gate-integration tests pass, hint pin passes, full :app:testDebugUnitTest green, assembleDebug succeeds.</done>
</task>

</tasks>

<verification>
./gradlew :app:testDebugUnitTest :app:assembleDebug — both green.
On-device check (human, next session): "Dame un ejemplo de codigo simple en
jsavascript" answers with code from weights, no Fuentes row, no "based on
Source" hedge; "Kotlin 2.3 new features" still grounds.
</verification>

<success_criteria>
- Code turn skips pre-search (0 search, 0 extra socket), factual/social/URL/loop behavior unchanged
- Hint clause pinned verbatim, loop still armed as the model's escape hatch
- Full suite + assembleDebug green
</success_criteria>

<output>
Create `.planning/quick/20260930-code-intent-gate/PLAN-SUMMARY.md` when done (executor writes it per summary template)
</output>
