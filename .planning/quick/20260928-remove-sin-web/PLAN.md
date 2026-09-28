---
phase: quick-remove-sin-web
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/main/java/com/warped/ui/chat/ChatUiState.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/data/grounding/GroundingPrecedence.kt
  - app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt
  - app/src/test/java/com/warped/data/grounding/GroundingPrecedenceTest.kt
  - app/src/test/java/com/warped/data/grounding/GroundingPromptTest.kt
  - app/src/test/java/com/warped/ui/chat/ChatGroundingToggleTest.kt
autonomous: true
requirements: [REMOVE-SIN-WEB-01, REMOVE-SIN-WEB-02]
must_haves:
  truths:
    - "No 'Sin web' chip renders in the chat composer"
    - "No skipWebOnce / toggleSkipWebOnce / skipOnce symbol remains anywhere in main or test sources"
    - "Per-chat tri-state override + global toggle still gate grounding (shouldGround(perChat, global))"
    - "With grounding enabled and no URLs pasted, the outgoing prompt still carries SYSTEM_PROMPT"
    - "With grounding disabled, the outgoing prompt is untouched (no SYSTEM_PROMPT)"
    - "./gradlew :app:assembleDebug and full :app:testDebugUnitTest are green"
  artifacts:
    - path: "app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt"
      provides: "Composer without Sin web chip"
    - path: "app/src/main/java/com/warped/data/grounding/GroundingPrecedence.kt"
      provides: "shouldGround(perChat, global) 2-arg precedence"
    - path: "app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt"
      provides: "augment() with grounding-enabled flag"
  key_links:
    - from: "app/src/main/java/com/warped/ui/chat/ChatViewModel.kt"
      to: "GroundingPrecedence.shouldGround"
      via: "send-path hook call with resolved perChat/global"
      pattern: "shouldGround\\("
    - from: "app/src/main/java/com/warped/ui/chat/ChatViewModel.kt"
      to: "GroundingPrompt.augment"
      via: "request text augmentation passing enabled flag"
      pattern: "GroundingPrompt\\.augment"
---

<objective>
Remove the "Sin web" one-off chip completely (D-01) and make the model always
know web search is available via an always-on SYSTEM_PROMPT whenever grounding
is enabled (D-02).

Purpose: the one-off skip button is gone; grounding is governed only by the
per-chat tri-state + global toggle, and the model is instructed about web
search on every grounded turn even when no URLs were pasted.
Output: edited main sources + rewritten/added unit tests, build + tests green.
</objective>

<execution_context>
@/home/cotizcesar/Documents/warped/.opencode/gsd-core/workflows/execute-plan.md
@/home/cotizcesar/Documents/warped/.opencode/gsd-core/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
@app/src/main/java/com/warped/ui/chat/ChatScreen.kt
@app/src/main/java/com/warped/ui/chat/ChatUiState.kt
@app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
@app/src/main/java/com/warped/data/grounding/GroundingPrecedence.kt
@app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: Remove Sin web chip + skipWebOnce state + skipOnce precedence (D-01)</name>
  <files>app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt, app/src/main/java/com/warped/ui/chat/ChatScreen.kt, app/src/main/java/com/warped/ui/chat/ChatUiState.kt, app/src/main/java/com/warped/ui/chat/ChatViewModel.kt, app/src/main/java/com/warped/data/grounding/GroundingPrecedence.kt</files>
  <action>Remove per D-01 (per-chat tri-state + global toggle STAY; out of scope: per-chat menu, global setting, preview sheet, retry — do not touch them):
  - ChatInputBar.kt: delete the skipWebOnce + onToggleSkipWeb params (lines ~53-57 incl. the Phase 53 TOGGLE-03 comment) and the whole "Sin web" Button block (lines ~174-192) plus its preceding Spacer(8.dp). Leave the thinking chip and all other params untouched.
  - ChatScreen.kt: delete the two wiring lines (skipWebOnce = input.skipWebOnce, onToggleSkipWeb = ...) at ~334-335; do not reformat neighboring args.
  - ChatUiState.kt: delete the skipWebOnce field + its TOGGLE-03 comment from ChatInputState (~lines 51-54); fix the misplaced comment so the isFetchingWeb comment block reads cleanly (isFetchingWeb keeps its own WEB-06 comment only).
  - ChatViewModel.kt: delete toggleSkipWebOnce() (~284-289 incl. KDoc); delete the consume/reset lines in sendMessage (~340-345: the val skipWebOnce read and the skipWebOnce=false in the updateInput copy — keep inputText="" and isGenerating=true); update the hook (~381-397) to call GroundingPrecedence.shouldGround(perChat = perChatOverride, global = webGroundingEnabled) and rewrite the hook comment to drop the "one-off Sin web first" clause (per-chat override read ONCE, then global default-ON).
  - GroundingPrecedence.kt: simplify to fun shouldGround(perChat: Boolean?, global: Boolean) — perChat non-null wins, else global; rewrite object KDoc + @param KDoc to remove every mention of Sin web / skipOnce / chip. Do NOT change per-chat/global semantics.
  After edits grep must show zero hits for skipWebOnce|toggleSkipWebOnce|SkipWeb|skipOnce|Sin web in app/src/main.</action>
  <verify>
    <automated>grep -rn 'skipWebOnce\|toggleSkipWebOnce\|SkipWeb\|skipOnce\|Sin web' app/src/main || echo CLEAN</automated>
  </verify>
  <done>Sin web chip, skipWebOnce state, toggle/consume logic, and skipOnce branch are gone; per-chat + global resolution compiles and behaves as before.</done>
</task>

<task type="auto">
  <name>Task 2: Always-on SYSTEM_PROMPT via augment() enabled flag + tests (D-02)</name>
  <files>app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt, app/src/main/java/com/warped/ui/chat/ChatViewModel.kt, app/src/test/java/com/warped/data/grounding/GroundingPrecedenceTest.kt, app/src/test/java/com/warped/data/grounding/GroundingPromptTest.kt, app/src/test/java/com/warped/ui/chat/ChatGroundingToggleTest.kt</files>
  <action>Implement per D-02:
  - GroundingPrompt.augment(): add a grounding-enabled flag parameter (e.g. augment(original, block, groundingEnabled: Boolean = ...) — pick one signature and update ALL call sites). New contract: block non-null → "$SYSTEM_PROMPT\n\n$block\n\n$original" (unchanged [WEB CONTEXT] behavior); block null + enabled=true → "$SYSTEM_PROMPT\n\n$original"; block null + enabled=false → original untouched. Update KDoc to state this exact contract.
  - ChatViewModel hook: pass the already-resolved doGround value as the enabled flag at the augment call site (~line 432: GroundingPrompt.augment(requestUserText, result.block, ...) — enabled = doGround). Verify the hook passes the resolved perChat/global value, not a fresh DataStore read. Check for any other augment()/buildBlock callers via grep and update each augment call to the new signature (buildBlock/buildFusedBlock signatures stay).
  - GroundingPrecedenceTest: rewrite truth table to 6 rows over (perChat, global): (true,true)->true, (true,false)->true, (false,true)->false, (false,false)->false, (null,true)->true, (null,false)->false; delete the skipOnce-wins test, keep/adjust the inherit-global-off test. No other toggle semantics change.
  - ChatGroundingToggleTest: delete the `skipWebOnce skips fetch and resets after send` test (it covers removed behavior) and REPLACE its coverage: add/keep a test asserting the always-on prompt behavior at the ViewModel or prompt level (e.g. grounded turn with no URLs still sends SYSTEM_PROMPT-prefixed text when enabled; disabled turn sends untouched text). Keep all other toggle tests (per-chat No, inherit-global-on, etc.) green — adjust only lines referencing removed APIs.
  - GroundingPromptTest: update existing augment(null) test to the new signature (disabled → untouched) and ADD: augment(null, enabled=true) contains SYSTEM_PROMPT followed by original; augment(null, enabled=false) equals original exactly; existing block-order test updated to pass enabled=true. Spanish copy otherwise untouched; no Room/provider/inference changes.</action>
  <verify>
    <automated>./gradlew :app:assembleDebug</automated>
    <automated>./gradlew :app:testDebugUnitTest</automated>
  </verify>
  <done>augment() prepends SYSTEM_PROMPT on every grounded message (block or not), stays untouched when disabled; precedence tests are 6-row (perChat, global); skipOnce test replaced by always-on prompt coverage; assembleDebug + full testDebugUnitTest green.</done>
</task>

</tasks>

<verification>
- grep -rn 'skipWebOnce|toggleSkipWebOnce|SkipWeb|skipOnce|Sin web' app/src returns zero hits (main AND test, excluding this plan file).
- ./gradlew :app:assembleDebug succeeds.
- ./gradlew :app:testDebugUnitTest succeeds with zero failures.
- Per-chat override + global toggle behavior unchanged (existing toggle tests green minus the removed skipOnce test).
</verification>

<success_criteria>
- No Sin web chip / skipWebOnce / toggleSkipWebOnce / skipOnce remains in the codebase.
- shouldGround(perChat, global) is the single precedence decision; hook resolves perChat once + global.
- SYSTEM_PROMPT is sent on every grounded turn (even with null block); untouched when grounding disabled; [WEB CONTEXT] block format identical.
- Tests: 6-row precedence table, always-on prompt coverage added, no coverage deleted without replacement.
- Build + full unit test suite green; no Room/provider/inference/Spanish-copy changes beyond the specified edits.
</success_criteria>

<output>
Create `.planning/quick/20260928-remove-sin-web/SUMMARY.md` when done (brief: files changed, test results).
</output>
