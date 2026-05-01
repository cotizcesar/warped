---
phase: quick-260430-v7v
plan: 01
subsystem: chat, models, networking
tags: [quick-fix, local-inference, network-endpoints, cleartext, loading-indicator]
dependency_graph:
  requires: []
  provides: [local-model-preload, endpoint-model-listing, lan-http-access]
  affects: [ChatScreen, ModelsScreen, network-security]
tech-stack:
  added: []
  patterns: [direct-LlamaEngine-injection, provider-router-in-models-viewmodel]
key-files:
  created: []
  modified:
    - app/src/main/res/xml/network_security_config.xml
    - app/src/main/java/com/warped/ui/chat/ChatUiState.kt
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
    - app/src/main/java/com/warped/ui/models/ModelsUiState.kt
    - app/src/main/java/com/warped/ui/models/ModelsViewModel.kt
decisions:
  - "Allow cleartext HTTP globally via network_security_config for LAN access to Ollama/LMStudio"
  - "Inject LlamaEngine directly into ChatViewModel for pre-loading model on selection"
  - "Inject ProviderRouter into ModelsViewModel to call listModels() on endpoint activation"
metrics:
  duration_seconds: 252
  completed_date: "2026-05-01T03:36:19Z"
---

# Quick Task 260430-v7v: Model Loading + Network Endpoint Fixes Summary

**One-liner:** Three fixes: LAN cleartext HTTP access, local model pre-load with loading indicator in chat, and network endpoint model listing on activation.

## Completed Tasks

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | Fix network security config for LAN HTTP access | d492f2a | network_security_config.xml |
| 2 | Add model loading state and pre-load local model on selection | 5d351bc | ChatUiState.kt, ChatViewModel.kt, ChatScreen.kt |
| 3 | Fetch available models from network endpoints and show in selector | 4e1f17a | ModelsUiState.kt, ModelsViewModel.kt |

## Deviations from Plan

None — plan executed exactly as written.

## Success Criteria Verification

- **Cleartext HTTP works for LAN IPs:** ✅ Replaced per-IP `domain-config` with global `cleartextTrafficPermitted="true"` in `base-config`. Remote servers (OpenAI/Anthropic) enforce HTTPS at server level.
- **Local model pre-loads when "Use in chat" is tapped:** ✅ `ChatViewModel.preloadLocalModel()` triggers on `activeModelSelection.activeModel` collection when provider is LOCAL. Shows "Loading {modelName}..." card in ChatScreen.
- **Network endpoints fetch available models:** ✅ `ModelsViewModel.fetchEndpointModels()` calls `provider.listModels()` via `ProviderRouter` when `useEndpoint()` is invoked.
- **All files compile:** ✅ `:app:compileDebugKotlin` passes on all three commits.

## Self-Check

- [x] network_security_config.xml exists with `cleartextTrafficPermitted="true"`
- [x] ChatUiState.kt has `isLoadingModel`, `loadingModelName`, `modelLoadError`
- [x] ChatViewModel.kt has `preloadLocalModel()`, `clearModelLoadError()`, `LlamaEngine` injection
- [x] ChatScreen.kt has loading indicator and model error snackbar
- [x] ModelsUiState.kt has `availableEndpointModels`, `isFetchingModels`
- [x] ModelsViewModel.kt has `fetchEndpointModels()`, `ProviderRouter` injection
- [x] Commit d492f2a exists
- [x] Commit 5d351bc exists
- [x] Commit 4e1f17a exists

**Self-Check: PASSED**
