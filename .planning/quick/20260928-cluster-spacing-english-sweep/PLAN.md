---
phase: quick-20260928-cluster-spacing-english-sweep
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt
  - app/src/main/java/com/warped/chat/InputSanitizer.kt
  - app/src/main/java/com/warped/chat/WebContextSanitizer.kt
  - app/src/main/java/com/warped/chat/GroundingPrompt.kt
  - app/src/main/java/com/warped/ui/chat/MessageBubble.kt
  - app/src/main/java/com/warped/ui/chat/SourcePreviewSheet.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/main/java/com/warped/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/ui/help/HelpScreen.kt
  - app/src/main/res/values-es/strings.xml
  - app/src/main/res/values/strings.xml
autonomous: true
requirements: [QS-01-spacing, QS-02-english-only]
must_haves:
  truths:
    - "Active download cluster has visible breathing room (8dp after ring, 8dp before cancel)"
    - "All user-visible UI copy renders in English (no Spanish labels, banners, chips, sheets, snackbars, a11y descriptions)"
    - "Unit test suite is green after conversion"
  artifacts:
    - path: "app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt"
      provides: "Cluster spacing fix + English copy"
  key_links: []
---

<objective>
Two-workstream quick task: (A) give the active download cluster real breathing room, (B) convert all user-visible Spanish copy to English-only UI. No download-engine, catalog-data, or Theme changes.

Purpose: Fix cramped pause/cancel cluster + ship English-only product copy.
Output: Spacing fix, English UI, green tests.
</objective>

<execution_context>
@/home/cotizcesar/Documents/warped/.opencode/gsd-core/workflows/execute-plan.md
@/home/cotizcesar/Documents/warped/.opencode/gsd-core/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: Cluster separation — 4dp to 8dp + 8dp before cancel</name>
  <files>app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt</files>
  <action>LOCKED DECISION A. In the `active` cluster Row (~lines 322-354): (1) change the Spacer after the CircularProgressIndicator from 4dp to 8dp; (2) add an 8dp Spacer immediately before the cancel IconButton (pause/resume <-> cancel gap). Applies to both branches reaching cancel: the playing branch (ring + pause + cancel) and the paused branch (resume + cancel) — in the paused branch there is no ring spacer, so only the before-cancel 8dp spacer applies. Everything else in the cluster byte-identical: no icon, size, color, tint, onClick, or contentDescription changes in this task (a11y ES->EN happens in Task 3). Do NOT touch downloaded/check or idle/download branches. Note: the device screenshot showing overlap is a STALE build predating the Row fix — the Row itself is already correct, this task only adds breathing room.</action>
  <verify>
    <automated>grep -n "Spacer(Modifier.width(8.dp))" app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt && ./gradlew :app:assembleDebug -x test 2>&1 | tail -5</automated>
  </verify>
  <done>Two 8dp spacers present in active cluster (one after ring, one before cancel); assembleDebug succeeds; no other cluster lines changed (git diff shows only the two spacer lines).</done>
</task>

<task type="auto">
  <name>Task 2: Inventory — full file-by-file Spanish user-copy list</name>
  <files>NONE (inventory only, no code edits)</files>
  <action>Build the complete inventory before converting. Grep app/src/main/java for accented chars `[áéíóúñ¿¡]` (known hits: InputSanitizer.kt, WebContextSanitizer.kt, GroundingPrompt.kt, MessageBubble.kt, SourcePreviewSheet.kt, ChatScreen.kt, ChatViewModel.kt, HelpScreen.kt, HuggingFaceScreen.kt) PLUS unaccented Spanish user-facing words the accent-grep misses: Fuentes, Reintentar, Pensando, Leyendo, Descargando, En pausa, Descargado, Abrir en navegador, Visión, Razonamiento, "En espera", model-only banner copies, grounding SYSTEM_PROMPT, snackbar copies, a11y contentDescriptions in Spanish. Also inventory app/src/main/res/values-es/strings.xml vs app/src/main/res/values/strings.xml key parity (check default English file for same keys BEFORE any delete decision). Also grep test dir (app/src/test, app/src/androidTest) for the same Spanish words to list every asserting test that must move in lockstep. Classify each hit as TRANSLATE (user-visible) vs DO-NOT-TOUCH (detection regexes/patterns in InputSanitizer + WebContextSanitizer hijack patterns must keep matching Spanish; code comments; test names; log/Timber messages; model file names). Write the inventory as a table in the task summary (file | line | Spanish | English replacement | translate/keep). Reference replacements: SYSTEM_PROMPT -> careful English preserving citation markers [1]/[2] + no-URL-invention rule; "Fuentes"->"Sources"; "Leyendo N de M…"->"Reading N of M…"; "Pensando…"->"Thinking…"; "Reintentar"->"Retry"; "En espera"->"Queued"; "Abrir en navegador"->"Open in browser"; "Descargando/En pausa/Descargado"->"Downloading/Paused/Downloaded"; "Sin conexión…" banner->English; "… [truncado]"->"… [truncated]"; a11y ES->EN (e.g. "Pausar descarga"->"Pause download", "Reanudar descarga"->"Resume download", "Cancelar descarga"->"Cancel download"); ramNote/blurb asset strings->English.</action>
  <verify>
    <automated>grep -rn "[áéíóúñ¿¡]" app/src/main/java --include="*.kt" | wc -l && grep -rln "Fuentes\|Reintentar\|Pensando\|Leyendo\|Descargando\|En pausa\|Descargado\|Abrir en navegador\|Visión\|Razonamiento\|En espera\|truncado" app/src/main --include="*.kt" --include="*.xml" | sort</automated>
  </verify>
  <done>Inventory table in summary covers every hit file with translate/keep classification; values vs values-es key-parity result recorded; asserting-test list recorded.</done>
</task>

<task type="auto">
  <name>Task 3: Convert to English + update asserting tests + values-es disposition</name>
  <files>app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt, app/src/main/java/com/warped/chat/GroundingPrompt.kt, app/src/main/java/com/warped/ui/chat/MessageBubble.kt, app/src/main/java/com/warped/ui/chat/SourcePreviewSheet.kt, app/src/main/java/com/warped/ui/chat/ChatScreen.kt, app/src/main/java/com/warped/chat/ChatViewModel.kt, app/src/main/java/com/warped/ui/help/HelpScreen.kt, app/src/main/res/values/strings.xml, app/src/main/res/values-es/strings.xml, asserting test files from Task 2 inventory</files>
  <action>LOCKED DECISION B. Apply Task 2 inventory replacements in main source: ALL user-visible Spanish -> English (UI labels, banners, chips, sheets, snackbars, a11y descriptions incl. "Pausar/Reanudar/Cancelar descarga", grounding SYSTEM_PROMPT + prompt-side user-facing Spanish such as "… [truncado]" marker). DO NOT touch: detection regexes/patterns (InputSanitizer.kt + WebContextSanitizer.kt hijack patterns keep matching Spanish — translate only their user-facing outputs if any), code comments, test names, log/Timber messages, model file names. values-es/strings.xml: DELETE it (English-only product) ONLY after verifying app/src/main/res/values/strings.xml carries the same keys (fallback safety); if keys mismatch, KEEP values-es and report the mismatch in the summary instead of deleting. Update every asserting test found in Task 2 in lockstep (expected strings -> English); keep suite green.</action>
  <verify>
    <automated>./gradlew :app:testDebugUnitTest 2>&1 | tail -5</automated>
  </verify>
  <done>All user-visible copy English; values-es deleted (or kept + mismatch reported); every asserting test updated; :app:testDebugUnitTest fully green.</done>
</task>

<task type="auto">
  <name>Task 4: Verification gates + remaining-Spanish grep gate</name>
  <files>NONE (verification only)</files>
  <action>Run: (1) ./gradlew :app:assembleDebug, (2) full :app:testDebugUnitTest, (3) grep gate for remaining user-facing Spanish: accented chars in main UI files (MessageBubble, SourcePreviewSheet, ChatScreen, ChatViewModel, HelpScreen, HuggingFaceScreen, GroundingPrompt, values/strings.xml) — with documented exclusions: sanitizer detection patterns (InputSanitizer.kt, WebContextSanitizer.kt regex/pattern lines), code comments (//, /*, KDoc *), log/Timber lines. If gate hits are only exclusions, list them as such. Out of scope (do not attempt): download engine, catalog data (sizes/repos), layout beyond the two spacers, Theme/colors.</action>
  <verify>
    <automated>./gradlew :app:assembleDebug 2>&1 | tail -3 && ./gradlew :app:testDebugUnitTest 2>&1 | tail -3 && grep -rn "[áéíóúñ¿¡]" app/src/main/java/com/warped/ui app/src/main/java/com/warped/chat/GroundingPrompt.kt app/src/main/java/com/warped/chat/ChatViewModel.kt app/src/main/res/values/strings.xml 2>/dev/null; echo "---EXCLUSIONS---"; grep -rn "[áéíóúñ¿¡]" app/src/main/java/com/warped/chat/InputSanitizer.kt app/src/main/java/com/warped/chat/WebContextSanitizer.kt 2>/dev/null | wc -l</automated>
  </verify>
  <done>assembleDebug + full unit suite green; grep gate shows zero user-facing Spanish outside documented exclusions.</done>
</task>

</tasks>

<verification>
- ./gradlew :app:assembleDebug succeeds
- ./gradlew :app:testDebugUnitTest fully green
- Active cluster shows 8dp after ring + 8dp before cancel, nothing else changed
- No user-facing Spanish remains (exclusions documented)
</verification>

<success_criteria>
- Cluster breathing room shipped per Decision A
- English-only UI shipped per Decision B (values-es deleted or mismatch reported)
- Tests green; honest note recorded: on-device English confirmation needs screenshot (no adb)
</success_criteria>

<output>
No SUMMARY required (quick task). Honest note: on-device English confirmation needs a user-provided screenshot — no adb in this environment.
</output>
