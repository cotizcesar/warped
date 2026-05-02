---
phase: 09-ui-integration
plan: 02
type: execute
wave: 1
subsystem: ui/chat
tags: [backend-chip, model-selector, engine-routing, format-badge, litertlm]
depends_on: []
requires: []
provides: ChatScreen backend chip, format-aware model selector, LITE_RT_LM routing
affects: []
tech-stack:
  added: []
  patterns: [EngineManager injection, refreshActiveBackend pattern, format-based provider routing]
key-files:
  created: []
  modified:
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/ui/chat/ChatUiState.kt
decisions:
  - "Backend chip uses Surface (not AssistChip) to avoid icon dependency on Icons.Filled.Memory"
  - "refreshActiveBackend called in setSelectedModel, preloadLocalModel, selectConversation — covers all state transitions"
  - "LITE_RT_LM modelFormat check uses String equality 'LITERTLM' (matching LocalModel default)"
  - "selectConversation preloads both LOCAL and LITE_RT_LM (not just LOCAL)"
metrics:
  duration: "~3 min"
  tasks: 2
  files_modified: 3
  total_commits: 2
  completed_date: 2026-05-02
---

# Phase 09 Plan 02: Chat Backend Status Chip + Model Selector Summary

**One-liner:** Added EngineManager injection to ChatViewModel, backend status chip ("LiteRT-LM · CPU/GPU") in ChatScreen, and format-aware model selector dropdown with GGUF/LiteRT badges and correct provider routing.

## Tasks

### Task 1: EngineManager + backend state + chip
- **Commit:** `474185d`
- **Changes:** 3 files (ChatUiState.kt, ChatViewModel.kt, ChatScreen.kt)
- **What:**
  - ChatUiState: added `activeBackend: BackendType? = null` field
  - ChatViewModel: injected `EngineManager`, added `refreshActiveBackend()` helper that queries `engineManager.getActiveEngine()?.backend` when `selectedProvider == LITE_RT_LM`
  - ChatScreen: inserted backend status chip (Surface-based) showing "LiteRT-LM · CPU" or "LiteRT-LM · GPU" above messages when LiteRT-LM is loaded
- **Wiring:** `refreshActiveBackend()` called in `preloadLocalModel()` (success), `setSelectedModel()` (post-routing), `selectConversation()` (post-restoration)

### Task 2: Format-aware model selector + LITE_RT_LM routing
- **Commit:** `5d05ab6`
- **Changes:** 2 files (ChatViewModel.kt, ChatScreen.kt)
- **What:**
  - `preloadLocalModel()`: detects `model.modelFormat == "LITERTLM"` and routes to `engineManager.switchToLiteRT(filePath)`; GGUF still uses `llamaEngine.loadModel()`
  - `setSelectedModel()`: added `ProviderType.LITE_RT_LM` branch for both unload (via `engineManager.unloadCurrent()`) and load (calls `preloadLocalModel(modelId)`)
  - ChatScreen dropdown: replaced simple `Text(model.name)` with `Row` containing `FormatBadge` (blue GGUF / green LiteRT) + model name; providerType determined by `model.modelFormat`
  - `selectConversation()`: now preloads model when `providerType == LOCAL || LITE_RT_LM`
  - `init` block: `activeModel.collect` now preloads for both LOCAL and LITE_RT_LM
  - `observeModels` collector: validated-to-exist check now includes LITE_RT_LM alongside LOCAL

## Deviations from Plan

None — plan executed exactly as written.

## Verification

- [x] `./gradlew :app:compileDebugKotlin` succeeds
- [x] ChatUiState has `activeBackend: BackendType?`
- [x] ChatViewModel injects EngineManager via constructor
- [x] Backend chip visible only when `selectedProvider == LITE_RT_LM && activeBackend != null`
- [x] Model dropdown shows format badges for all local models
- [x] LITE_RT_LM models route via `ProviderType.LITE_RT_LM`
- [x] GGUF models route via `ProviderType.LOCAL` (no regression)

## Self-Check: PASSED

- [x] ChatScreen.kt contains backend status chip and format badges in dropdown
- [x] ChatViewModel.kt contains EngineManager injection, refreshActiveBackend, format-based routing
- [x] ChatUiState.kt contains activeBackend field
- [x] Commit `474185d` exists (Task 1)
- [x] Commit `5d05ab6` exists (Task 2)
- [x] Build compiles successfully
