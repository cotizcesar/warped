---
phase: quick-reference-resolution-rule
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt
  - app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt
  - app/src/test/java/com/warped/data/grounding/GroundingPromptTest.kt
  - app/src/test/java/com/warped/data/local/inference/LiteRTLmLoopTest.kt
autonomous: true
requirements: [REF-RESOLVE-01, REF-RESOLVE-02, REF-RESOLVE-03]
must_haves:
  truths:
    - "Follow-up 'Y quien es su hermanastro?' resolves 'su' against history (Hornet) instead of answering a generic dictionary definition"
    - "No answer grafts stale citation numbers from earlier turns onto new claims"
    - "./gradlew :app:testDebugUnitTest and :app:assembleDebug are green"
  artifacts:
    - path: "app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt"
      provides: "SYSTEM_PROMPT with reference-resolution + citation-hygiene sentence"
    - path: "app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt"
      provides: "TOOL_USE_SYSTEM_HINT with reference-resolution + citation-hygiene sentence"
  key_links:
    - from: "app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt"
      to: "augmented user message"
      via: "SYSTEM_PROMPT prefix on every grounded turn"
      pattern: "Cite only sources fetched for the current answer"
    - from: "app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt"
      to: "systemInstruction"
      via: "TOOL_USE_SYSTEM_HINT pinned on armed turns"
      pattern: "TOOL_USE_SYSTEM_HINT"
---

<objective>
Fix follow-up reference drift with a prompt-only swap in both grounding
constants: the model must resolve pronouns/references against conversation
history and must never reuse earlier turns' citation numbers.

Purpose: on-device evidence — follow-up "Y quien es su hermanastro?"
answered with a generic dictionary definition citing recycled [2,5].
Root causes: (1) the per-turn-independence rule never tells the model to
RESOLVE pronouns/references against history, so "su" never becomes Hornet;
(2) nothing forbids reusing prior turns' citation numbers, so stale [2,5]
get grafted onto new claims.
Output: two swapped sentences (planner-locked wording below) + updated
verbatim test pins + two new clause tests, build + full suite green.
</objective>

<execution_context>
@/home/cotizcesar/Documents/warped/.opencode/gsd-core/workflows/execute-plan.md
@/home/cotizcesar/Documents/warped/.opencode/gsd-core/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt
@app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt
@app/src/test/java/com/warped/data/grounding/GroundingPromptTest.kt
@app/src/test/java/com/warped/data/local/inference/LiteRTLmLoopTest.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: Swap the independence sentence in both constants (REF-RESOLVE-01, REF-RESOLVE-02)</name>
  <files>app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt, app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt</files>
  <action>REPLACE (not append) the per-turn-independence sentence in BOTH constants with the planner-locked sentences below, keeping the existing string-concat style. Everything else in both constants stays byte-identical (same order, same spacing, same trailing spaces inside each concat chunk).

GroundingPrompt.kt SYSTEM_PROMPT (line ~15): DELETE exactly "Treat each new question on its own: never answer from earlier sources " + "alone when it needs facts the current sources do not cover. " and INSERT exactly "Resolve pronouns and references (he/she/it/this/that, él/ella/su/eso/este, and names) against the conversation history first, and use the resolved names when searching and answering. " + "Cite only sources fetched for the current answer; never reuse citation numbers from earlier turns. " in its place (same position: after the "Cite sources with [1]/[2] markers. " chunk, before the "Answer with the provided sources; call web_search if you need more. " chunk). The re-search trigger is preserved by the surviving "Answer with the provided sources; call web_search if you need more." sentence — do NOT add a second re-search clause.

LiteRTLmProvider.kt TOOL_USE_SYSTEM_HINT (line ~112): DELETE exactly "Treat each new user message on its own: if it needs facts not covered " + "by earlier tool results, call web_search again instead of answering " + "from stale results. " and INSERT exactly "Resolve pronouns and references (he/she/it/this/that, él/ella/su/eso/este, and names) against the conversation history first, and include the resolved names in web_search queries; if the current answer needs facts not covered by earlier tool results, call web_search again instead of answering from stale results. " + "Cite only sources fetched for the current answer; never reuse citation numbers from earlier turns. " in its place (same position: after the "Answer with the gathered context. " chunk, before the "Write code from your own knowledge first..." chunk). The "call web_search again instead of answering from stale results" substring is deliberately preserved verbatim so the existing re-search rule test keeps passing and the agentic-rows re-search behavior is not regressed. Do NOT touch the code-from-knowledge clause, the greetings clause, or IDENTITY_LINE. Also update the KDoc comment above TOOL_USE_SYSTEM_HINT (~lines 96-107) so it describes reference-resolution + citation-hygiene instead of only per-turn independence (comment only, no behavior). Do NOT touch WEB_SEARCH_TOOL_DESCRIPTION, PromptTemplate/PromptTemplateConfigs, ChatViewModel, loop/search code, history mechanics, or UI — out of scope.</action>
  <verify><automated>grep -c "Cite only sources fetched for the current answer; never reuse citation numbers from earlier turns" app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt && grep -c "Treat each new" app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt; test $? -eq 1</automated></verify>
  <done>Both constants carry the resolve + no-reuse wording (grep count 1 per file); zero occurrences of "Treat each new" remain in main sources; all other chunks byte-identical.</done>
</task>

<task type="auto">
  <name>Task 2: Update verbatim pins, add resolve/no-reuse tests, full suite green (REF-RESOLVE-03)</name>
  <files>app/src/test/java/com/warped/data/grounding/GroundingPromptTest.kt, app/src/test/java/com/warped/data/local/inference/LiteRTLmLoopTest.kt</files>
  <action>First grep "Treat each new" across app/src/test to enumerate every pin (known: GroundingPromptTest line ~48, LiteRTLmLoopTest lines ~133 verbatim isEqualTo, ~152, ~192 armed-composition). Update ALL of them: (1) GroundingPromptTest `system prompt carries the per-turn re-search rule` — rewrite to assert SYSTEM_PROMPT contains "Resolve pronouns and references" AND "Cite only sources fetched for the current answer" AND "never reuse citation numbers from earlier turns" (rename the test to `system prompt carries the reference-resolution rule`); (2) LiteRTLmLoopTest `system hint is pinned verbatim` isEqualTo — update to the exact new hint text from Task 1 (copy the constant, do not paraphrase); (3) LiteRTLmLoopTest `armed instruction composes identity plus hint` line ~192 — replace the "Treat each new user message on its own" contains-assertion with the new resolve clause. LEAVE UNTOUCHED: `system hint carries the per-turn re-search rule` (still passes — the "call web_search again instead of answering from stale results" substring is preserved), ToolSetSchemaTest `web_search description carries the re-search rule` (pins WEB_SEARCH_TOOL_DESCRIPTION, a different constant, out of scope). Then ADD one new test per constant: GroundingPromptTest `system prompt resolves references and forbids citation reuse` asserting SYSTEM_PROMPT contains "against the conversation history first" and "never reuse citation numbers from earlier turns"; LiteRTLmLoopTest `system hint resolves references into search queries` asserting TOOL_USE_SYSTEM_HINT contains "include the resolved names in web_search queries" and "never reuse citation numbers from earlier turns". Then run the FULL unit suite plus assembleDebug.</action>
  <verify><automated>./gradlew :app:testDebugUnitTest :app:assembleDebug</automated></verify>
  <done>No test references "Treat each new"; two new clause tests pass; full :app:testDebugUnitTest green; assembleDebug succeeds.</done>
</task>

</tasks>

<verification>
./gradlew :app:testDebugUnitTest :app:assembleDebug — both green.
Honest notes for the summary: 2B-model compliance with the resolve/no-reuse
rule is probabilistic (prompt-level instruction, no enforcement mechanism);
multi-turn anaphora ("su" -> Hornet) needs on-device confirmation — no adb
in this environment, so flag as human follow-up.
</verification>

<success_criteria>
- Both constants carry reference-resolution + cite-only-current + never-reuse wording, replace-not-append, rest byte-identical
- All verbatim pins updated, two new clause tests green, full suite + assembleDebug green
- Out of scope untouched: query rewriting, history mechanics, loop/search code, UI, WEB_SEARCH_TOOL_DESCRIPTION
</success_criteria>

<output>
Create `.planning/quick/20260930-reference-resolution-rule/PLAN-SUMMARY.md` when done (executor writes it per summary template)
</output>
