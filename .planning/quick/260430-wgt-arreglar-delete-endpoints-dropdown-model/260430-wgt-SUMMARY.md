---
phase: quick-260430-wgt
plan: 01
subsystem: endpoints, models, lmstudio-provider
tags: [bugfix, ui, dropdown, endpoint-deletion, lmstudio-400]
requires: []
provides: [endpoint-deletion-fix, model-dropdown, lmstudio-400-fix]
affects: [remote-chat]
tech-stack:
  added: []
  patterns:
    - ExposedDropdownMenuBox for endpoint model selection
    - LMStudioProvider direct instantiation for form-based model listing
    - IllegalArgumentException guard for invalid endpoint IDs
key-files:
  created: []
  modified:
    - app/src/main/java/com/warped/data/repository/EndpointRepositoryImpl.kt
    - app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt
    - app/src/main/java/com/warped/ui/endpoints/components/EndpointForm.kt
    - app/src/main/java/com/warped/ui/models/ModelsViewModel.kt
    - app/src/main/java/com/warped/ui/models/ModelsUiState.kt
    - app/src/main/java/com/warped/ui/models/ModelsScreen.kt
decisions:
  - Changed availableEndpointModels from List<ModelInfo> to List<String> to unify endpoint form dropdown with existing useEndpoint model fetching
  - Used direct LMStudioProvider instantiation (not ProviderRouter) for form-based model listing since the form URL may not be a saved endpoint
  - Removed ModelInfo import from ModelsViewModel since type is inferred via map { it.id }
metrics:
  duration_seconds: 435
  completed_date: 2026-04-30
---

# Quick Task 260430-wgt: Fix Endpoint Deletion, Model Dropdown, and LM Studio 400

Three bug fixes: endpoint deletion silently failing, no model dropdown in endpoint form, and LM Studio returning 400 "input required" on chat.

## One-Liner

Patched endpoint deletion guard (no more silent failures), added LM Studio model dropdown with fetch button to EndpointForm, and fixed 400 error by sending null max_tokens to LM Studio native v1 API.

## Tasks Completed

| # | Task | Commit | Files Changed |
|---|------|--------|---------------|
| 1 | Fix endpoint deletion + ID validation | `569552b` | `EndpointRepositoryImpl.kt` |
| 2 | Add model dropdown to EndpointForm | `0cbe7f2` | `EndpointForm.kt`, `ModelsViewModel.kt`, `ModelsUiState.kt`, `ModelsScreen.kt` |
| 3 | Fix LM Studio 400 "input required" | `3f7e1b0` | `LMStudioProvider.kt` |

## Deviations from Plan

None — plan executed exactly as written.

## Verification

- All three tasks compiled successfully with `JAVA_HOME=/usr/lib/jvm/java-21 ./gradlew :app:compileDebugKotlin`
- Task 1: `deleteEndpoint()` now validates ID != 0, calls `apiKeyStore.deleteKey()` and `endpointDao.deleteById()` unconditionally
- Task 2: EndpointForm has `ExposedDropdownMenuBox` with "Fetch" button, ModelsViewModel has `fetchEndpointModels()` using LMStudioProvider, state fields updated to `List<String>` and `isFetchingEndpointModels`
- Task 3: `maxTokens.takeIf { it > 0 }` sends `null` (omitted by serialization) instead of `-1`, `stop = null` explicitly set

## Self-Check: PASSED

- All 6 modified files confirmed present on disk
- All 3 commits confirmed in git history (`569552b`, `0cbe7f2`, `3f7e1b0`)
- Build compiles with zero errors
