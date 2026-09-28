---
phase: quick-20260928-gemma-caps-thinking-drawer
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/assets/model_allowlist.json
  - app/src/main/java/com/warped/data/repository/ModelAllowlistRepository.kt
  - app/src/test/java/com/warped/data/repository/ModelAllowlistTest.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/main/java/com/warped/ui/chat/ChatUiState.kt
  - app/src/main/java/com/warped/ui/navigation/NavGraph.kt
  - app/src/main/java/com/warped/ui/help/HelpScreen.kt
autonomous: true
requirements:
  - QUICK-A-allowlist-flags
  - QUICK-B-pensando-row
  - QUICK-C-drawer-fullwidth
  - QUICK-D-help-text
must_haves:
  truths:
    - "gemma-4-E2B-it allowlist entry reports vision, audio, and thinking support; function-calling stays false"
    - "While a generation is streaming with no content/reasoning yet (and no web fetch), the chat shows a transient 'Pensando…' row instead of nothing"
    - "When the first token or reasoning arrives, the normal streaming bubble replaces the Pensando row with no duplicate or stuck rows"
    - "The navigation drawer opens full-screen width with unchanged items, colors, and gestures"
    - "Help screen no longer claims automatic tool execution for LiteRT-LM models"
  artifacts:
    - path: "app/src/main/assets/model_allowlist.json"
      provides: "gemma-4-E2B-it capability flags"
      contains: "supportsThinking"
    - path: "app/src/test/java/com/warped/data/repository/ModelAllowlistTest.kt"
      provides: "Updated allowlist flag assertions"
      contains: "gemma-4-E2B-it"
    - path: "app/src/main/java/com/warped/ui/chat/ChatScreen.kt"
      provides: "Pensando transient trailing row"
      contains: "Pensando"
    - path: "app/src/main/java/com/warped/ui/navigation/NavGraph.kt"
      provides: "Full-width drawer sheet"
      contains: "ModalDrawerSheet"
    - path: "app/src/main/java/com/warped/ui/help/HelpScreen.kt"
      provides: "Corrected tool-calling help text"
      contains: "Tool Calling"
  key_links:
    - from: "app/src/main/java/com/warped/ui/chat/ChatScreen.kt"
      to: "transcript.isStreaming / streamingContent / streamingReasoning"
      via: "trailing-item slot derived from existing showStreamingBubble mechanics"
      pattern: "showStreamingBubble|trailingCount|totalItems"
    - from: "app/src/main/java/com/warped/data/repository/ModelAllowlistRepository.kt"
      to: "app/src/main/assets/model_allowlist.json"
      via: "effectiveCapabilities maps supportsThinking to reasoning"
      pattern: "effectiveCapabilities"
---

<objective>
Apply docs-verified Gemma 4 E2B capability flags, add a transient "Pensando…" processing row, widen the nav drawer to full screen, and fix the stale Help tool-execution text.

Purpose: Capability badges/toggles for gemma-4-E2B-it currently under-report what Google documents (vision/audio/thinking); users also see a dead gap with no feedback between send and first token; the drawer is narrow; Help promises a removed feature.
Output: Updated allowlist + tests, Pensando row in the chat list, full-width drawer, corrected Help text — all unit tests green.
</objective>

<execution_context>
@/home/cotizcesar/Documents/warped/.opencode/gsd-core/workflows/execute-plan.md
@/home/cotizcesar/Documents/warped/.opencode/gsd-core/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@app/src/main/assets/model_allowlist.json
@app/src/main/java/com/warped/data/repository/ModelAllowlistRepository.kt
@app/src/main/java/com/warped/ui/chat/ChatScreen.kt
@app/src/main/java/com/warped/ui/navigation/NavGraph.kt
@app/src/main/java/com/warped/ui/help/HelpScreen.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: Gemma 4 E2B allowlist flags + tests (QUICK-A)</name>
  <files>app/src/main/assets/model_allowlist.json, app/src/main/java/com/warped/data/repository/ModelAllowlistRepository.kt, app/src/test/java/com/warped/data/repository/ModelAllowlistTest.kt</files>
  <action>Per LOCKED EVIDENCE (Google Gemma 4 model card + LiteRT-LM docs, device confirmation pending): in model_allowlist.json set ONLY the gemma-4-E2B-it entry capabilities to vision:true, audio:true, supportsThinking:true; supportsFunctionCalling stays false; make NO changes to the two gemma-3n entries. Update the JSON meta note to record the source (Google official Gemma 4 docs, docs-verified 2026-09-28, device confirmation pending — user validates on hardware) while keeping the verified-only policy framing. Update the effectiveCapabilities KDoc in ModelAllowlistRepository.kt to match (docs-verified vision/audio/thinking for gemma-4-E2B-it; function-calling stays false because tool execution was removed in v2.2 Phase 49 DEL-01 so a Tools badge would promise a missing feature; ThinkingConfig engine wiring is a recorded tech-debt follow-up — supportsThinking=true enables toggle + badge + think-tag parse/display only). DO NOT change thinking-toggle gating logic (ChatViewModel.supportsThinkingFor, ChatInputBar canThink/modelHasReasoning) — verify by grep that no change is needed and note the grep result in the summary. Update ModelAllowlistTest.kt assertions that encode the old flags: the shipped-asset-flags test must now expect gemma-4-E2B-it vision/audio true and supportsThinking true (function-calling/extendedContext/mtp still false; 3n entries unchanged), and the effectiveCapabilities test must now expect e2b reasoning true and vision true with tools false. Do NOT touch providers, inference, Room, or ThinkingConfig wiring (explicit follow-up, out of scope).</action>
  <verify>
    <automated>./gradlew :app:testDebugUnitTest --tests "com.warped.data.repository.ModelAllowlistTest" 2>&1 | tail -5</automated>
  </verify>
  <done>gemma-4-E2B-it entry has vision/audio/supportsThinking true and supportsFunctionCalling false; 3n entries untouched; ModelAllowlistTest green with updated expectations; gating logic untouched and verified by grep.</done>
</task>

<task type="auto">
  <name>Task 2: Pensando row + full-width drawer + Help fix (QUICK-B/C/D)</name>
  <files>app/src/main/java/com/warped/ui/chat/ChatScreen.kt, app/src/main/java/com/warped/ui/chat/ChatUiState.kt, app/src/main/java/com/warped/ui/navigation/NavGraph.kt, app/src/main/java/com/warped/ui/help/HelpScreen.kt</files>
  <action>In ChatScreen.kt add a transient trailing "Pensando…" row reusing the EXACT existing trailing-item slot mechanics around lines 229-254 and 418-431: derive a boolean such as showThinkingRow = transcript.isStreaming AND streamingContent empty AND streamingReasoning empty AND NOT input.isFetchingWeb; extend the trailing slot so the trailing item renders when showStreamingBubble OR showThinkingRow (keep a single trailing item, distinct constant key — add e.g. ChatListKeys.THINKING in ChatUiState.kt next to STREAMING, never a content hash — and keep trailingCount/totalItems math consistent so exactly one trailing row exists and the stick-to-bottom pin + hasNewContentBelow pill latch in the LaunchedEffect behave identically). Render the Pensando row as a lightweight bubble containing a small CircularProgressIndicator spinner plus Spanish text "Pensando…" with an appropriate contentDescription/semantics for accessibility; when content or reasoning arrives the existing streaming MessageBubble condition takes over seamlessly (mutually exclusive branches, no duplicate rows). Do NOT alter empty-state handling beyond the existing isEmpty semantics (Pensando row only while isStreaming, so an idle empty chat is unaffected). In NavGraph.kt make ModalDrawerSheet take full screen width when open (add a fillMaxWidth modifier or equivalent that compiles against the project's Compose BOM, keeping drawerShape 0dp + DrawerBg/DrawerTextPrimary colors; no changes to items, gestures, or scrim behavior). In HelpScreen.kt fix the stale Section 6 Tool Calling text around line 150: tool execution was removed (Phase 49 DEL-01), so rewrite the steps to state there is currently no automatic tool execution in the app, capability badges reflect model support only, and remove/neutralize the "model decides when to call a tool / execution is automatic" claims while keeping the section title and HelpSection structure.</action>
  <verify>
    <automated>./gradlew :app:assembleDebug :app:testDebugUnitTest 2>&1 | tail -8</automated>
  </verify>
  <done>"Pensando…" row appears only during streaming-with-no-content and yields to the streaming bubble on first token; drawer opens full-width with unchanged content; Help no longer claims automatic tool execution; assembleDebug + full :app:testDebugUnitTest green.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| user-input→UI | Pensando row renders no user content (static string only), no injection surface |
| asset→repository | Allowlist JSON parsed with ignoreUnknownKeys/coerceInputValues; flag flips only widen already-wired modality paths |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-quick-01 | Spoofing | model_allowlist.json flags | accept | Flags mirror Google official docs; device confirmation is an honest user-verified follow-up, no privilege change |
| T-quick-02 | Tampering | ChatScreen trailing-item keys | mitigate | Constant ChatListKeys (never content hashes) so rotation/fling cannot duplicate or cross-contaminate rows |
| T-quick-03 | Information Disclosure | Thinking toggle/badge | accept | supportsThinking=true surfaces only toggle + badge + think-tag display; no ThinkingConfig prompt injection (recorded follow-up) |
| T-quick-SC | Tampering | gradle dependencies | mitigate | No new dependencies in this plan; no package installs |
</threat_model>

<verification>
- ./gradlew :app:assembleDebug passes
- ./gradlew :app:testDebugUnitTest fully green (including updated ModelAllowlistTest)
- Honest note: end-to-end (attach image on E2B, thinking toggle enablement, Pensando row, full-width drawer) needs on-device confirmation — no adb in this environment
</verification>

<success_criteria>
- gemma-4-E2B-it flags flipped per locked evidence, function-calling stays false, 3n untouched
- Pensando row reuses trailing-slot mechanics with constant key and consistent totalItems math
- Drawer is full-width with identical items/gestures/colors
- Help text matches reality (no tool execution)
- Full unit test suite green; out-of-scope items (ThinkingConfig wiring, providers, Room) untouched
</success_criteria>

<output>
Create `.planning/quick/20260928-gemma-caps-thinking-drawer/01-SUMMARY.md` when done
</output>
