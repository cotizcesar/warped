---
phase: quick-20260928-gemma-caps-thinking-drawer
plan: 01
status: complete
date: 2026-09-28
tasks_completed: 2
tasks_total: 2
commits:
  - fc55825
  - 4a048b1
tests: 291 passed, 0 failed
---

# 01-SUMMARY: Gemma caps + Pensando row + full-width drawer + Help fix

Docs-verified Gemma 4 E2B capability flags, a transient "Pensando…" processing row for the streaming gap, a full-width navigation drawer, and corrected Help tool-calling text. Full unit suite green (291 tests), assembleDebug passes.

## Tasks completed

### Task 1: Gemma 4 E2B allowlist flags + tests (QUICK-A) — fc55825

- `app/src/main/assets/model_allowlist.json`: ONLY the `gemma-4-E2B-it` entry flipped to `vision:true, audio:true, supportsThinking:true`; `supportsFunctionCalling` stays `false`. Both `gemma-3n` entries byte-untouched. Meta `note` now records the source (Google official Gemma 4 docs, docs-verified 2026-09-28, device confirmation pending — user validates on hardware) while keeping the verified-only policy framing.
- `ModelAllowlistRepository.kt`: KDoc updated in both places (file header verified-only rule + `effectiveCapabilities` thinking opt-in). Zero logic changes.
- `ModelAllowlistTest.kt`: `shipped asset flags` test now expects per-model vision/audio/thinking maps (e2b true/true/true; 3n unchanged; speculativeDecoding still false on e2b; function-calling/extendedContext/mtp false everywhere). `effectiveCapabilities` test now expects e2b `reasoning:true, vision:true, tools:false`.
- Thinking-gating logic untouched — verified by grep: `ChatViewModel.supportsThinkingFor` (lines 205, 218, 1192), `ChatInputBar canThink/modelHasReasoning` (lines 46, 150–163), `ChatScreen modelHasReasoning = input.supportsThinking` (line 327) all unmodified; `git diff` on those files empty.
- Verify: `ModelAllowlistTest` green (BUILD SUCCESSFUL).

### Task 2: Pensando row + full-width drawer + Help fix (QUICK-B/C/D) — 4a048b1

- `ChatUiState.kt`: added `ChatListKeys.THINKING = "thinking"` constant next to `STREAMING` (T-quick-02: never content hashes).
- `ChatScreen.kt`:
  - `showThinkingRow = transcript.isStreaming && streamingContent.isEmpty() && streamingReasoning.isEmpty() && !input.isFetchingWeb`.
  - Trailing slot extended to `if (showStreamingBubble || showThinkingRow) 1 else 0` — exactly one trailing row; branches mutually exclusive so the streaming `MessageBubble` takes over seamlessly on first token/reasoning.
  - `LaunchedEffect` pin/pill latch, `trailingCount`/`totalItems` math, and `isEmpty` semantics untouched. Gap analysis: `ChatViewModel.sendMessage` sets `messages + userMessage` and `isStreaming = true` atomically (line 332), so the user message is always present during the gap — the list branch renders and `isEmpty` needs no change; an idle empty chat (not streaming) is unaffected.
  - New `ThinkingRow()` composable: assistant-aligned row with 14dp `CircularProgressIndicator` + "Pensando…" text, `contentDescription = "Pensando. Generando respuesta."` for accessibility. Static string only — no user content, no injection surface.
- `NavGraph.kt` (QUICK-C): `ModalDrawerSheet` wrapper replaced with a full-size `Surface` replicating the sheet (0dp shape, `DrawerBg`/`DrawerTextPrimary`, `DrawerDefaults.windowInsets` padding). Items, gestures, and scrim untouched (`ModalNavigationDrawer` unchanged).
- `HelpScreen.kt` (QUICK-D): Section 6 "Tool Calling" rewritten — states no automatic tool execution (removed v2.2), badges reflect model support only; "model decides when to call a tool / execution is automatic" claims removed. Title and `HelpSection` structure kept.
- Verify: `./gradlew :app:assembleDebug :app:testDebugUnitTest` — BUILD SUCCESSFUL, 291 tests / 0 failures across 33 classes.

## Deviations from Plan

### Drawer implementation (plan-prescribed "or equivalent")

- **Found during:** Task 2, before editing `NavGraph.kt`.
- **Issue:** The plan suggested `fillMaxWidth` on `ModalDrawerSheet` "or equivalent that compiles". Source verification against the project's resolved material3 1.4.0 (`material3-android-1.4.0-sources.jar`, `NavigationDrawer.kt` → `DrawerSheet`) shows an internal `.sizeIn(minWidth = 240.dp, maxWidth = ContainerWidth = 360.dp)` applied AFTER the caller modifier. Modifier constraints only narrow — no caller-side modifier can exceed the 360dp cap, so bare `fillMaxWidth` would silently not work.
- **Fix:** Full-size `Surface` replicating the sheet's shape/colors/insets inside the unchanged `ModalNavigationDrawer` (gestures + scrim preserved). All drawer items byte-identical.
- **Files:** `NavGraph.kt` only. **Commit:** 4a048b1.

No other deviations. No new dependencies (T-quick-SC). No Rule 1–3 auto-fixes needed.

## Test results

- `./gradlew :app:testDebugUnitTest --tests "com.warped.data.repository.ModelAllowlistTest"` — BUILD SUCCESSFUL (Task 1 gate).
- `./gradlew :app:assembleDebug :app:testDebugUnitTest` — BUILD SUCCESSFUL; 291 tests, 0 failures, 33 test classes (Task 2 gate).
- Honest note: end-to-end (attach image on E2B, thinking toggle enablement, Pensando row, full-width drawer) needs on-device confirmation — no adb in this environment.

## Tech-debt follow-ups (out of scope, untouched)

1. **ThinkingConfig engine wiring** — `supportsThinking=true` enables toggle + badge + think-tag parse/display only; prompt-level ThinkingConfig wiring is a recorded follow-up.
2. **On-device confirmations** — E2B vision/audio/thinking device validation (user validates on hardware); Pensando row, full-width drawer, and image-attach behavior need on-device confirmation.

## Self-Check: PASSED

- `model_allowlist.json` contains `supportsThinking: true` on the gemma-4-E2B-it entry; 3n entries unchanged — FOUND.
- `ChatScreen.kt` contains `Pensando` + `ChatListKeys.THINKING`; `ChatUiState.kt` contains `THINKING` — FOUND.
- `NavGraph.kt` contains full-size `Surface` drawer; `HelpScreen.kt` Section 6 rewritten — FOUND.
- Commits `fc55825`, `4a048b1` present in `git log` — FOUND.
- No file deletions in either commit — verified via `git diff --diff-filter=D`.
