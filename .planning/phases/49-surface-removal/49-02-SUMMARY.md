---
phase: 49-surface-removal
plan: 02
subsystem: settings, model-discovery, release
tags: [kotlin, compose, huggingface-removal, static-catalog, r8, release]

# Dependency graph
requires:
  - phase: 49-surface-removal
    provides: post-removal single-turn transcript shape from 49-01
provides:
  - Static model catalog (model_allowlist.json) with direct unauthenticated downloads
  - HF token + search surface deleted; release posture green (R8 narrowed, audit green, assembleRelease OK)
affects: [phase-50-web-grounding]

# Actuals
actuals:
  tokens: 10500
  tasks: 3
  commits: 1

# Tech tracking
tech-stack:
  added: []
  patterns: [static catalog over ModelAllowlistRepository + ModelDownloadManager, WarpedAlertDialog confirm for destructive cancel]

key-files:
  created:
    - app/src/main/java/com/warped/ui/huggingface/CatalogViewModel.kt
  modified:
    - app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt
    - app/src/main/java/com/warped/ui/settings/SettingsScreen.kt
    - app/src/main/java/com/warped/ui/settings/SettingsViewModel.kt
    - app/src/main/java/com/warped/ui/settings/SettingsUiState.kt
    - app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt
    - app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt
    - app/src/main/java/com/warped/ui/models/ModelsScreen.kt
    - app/src/main/java/com/warped/ui/navigation/Screen.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-es/strings.xml
    - app/proguard-rules.pro

key-decisions:
  - "AuthInterceptor already scoped to remote endpoint keys only — no change needed; only HuggingFaceAuthInterceptor deleted"
  - "Catalog downloads target warped-community org (prior default search author); allowlist name is the repo slug"
  - "New CatalogViewModel instead of reusing deleted HuggingFaceViewModel — sync asset load, downloadStates passthrough, URL building in one place"
  - "Cancel download guarded by WarpedAlertDialog (error-color confirm + Keep dismiss) per destructive-action rule"
  - "Capability flags (supportsFunctionCalling etc.) kept as data — only catalog badges restricted to vision/audio"

# Plan 49-02 Summary: HF token + search removal → static catalog + release posture

## What was built
Deleted the Hugging Face token surface and the model-search surface, retargeted
model discovery to the static model_allowlist.json catalog with direct
(unauthenticated) downloads. Release posture green.

## Task 1 — HF token removal
- SettingsScreen: Hugging Face section (header + Access Token card) deleted;
  Data moves to top, remaining sections keep order/copy/spacing
- SettingsUiState: hasHfToken/hfToken deleted; SettingsViewModel:
  updateHfToken/saveHfToken/deleteHfToken + token read deleted
- ApiKeyStore: HF_TOKEN_KEY + store/get/deleteHuggingFaceToken deleted
  (remote endpoint keys untouched)
- HuggingFaceAuthInterceptor.kt deleted; AuthInterceptor already
  endpoint-key-only (no change)
- ModelDownloadWorker: token query + ?token= suffix deleted, apiKeyStore
  injection removed; 401 copy is exactly the new text (no token/Settings/HF
  reference); progress/cancel retained via Manager/Worker

## Task 2 — static catalog
- HuggingFaceScreen.kt repurposed: TopAppBar "Model catalog" + "Back to models";
  3 allowlist cards (displayName title, modelFile subtitle, formatFileSize size,
  vision/audio badges only, end-aligned Download button); inline progress
  (determinate 0..1, percent + bytes/speed, Pause/Resume/Cancel) via
  CatalogViewModel + ModelDownloadManager/Worker; no search/debounce/gated
  branch/HF button; load failure copy exact; zero entries = load failure
- Deleted: HuggingFaceViewModel/UiState, HuggingFaceApi, HuggingFaceDtos,
  HuggingFaceRepository + Impl, HuggingFaceModule, HuggingFaceAuthInterceptor
- ModelsScreen wizard entry: "Download model / Choose from the built-in
  catalog" with Download icon; empty state gains catalog body line
- Screen.kt: route kept; label "Model catalog", icon Download; NavGraph call
  sites already target Screen.HuggingFace (now the catalog)
- Deleted 6 strings keys in values + values-es (verified unreferenced); type_message kept

## Task 3 — release posture
- proguard-rules.pro: com.warped skills keeps removed; all
  com.google.ai.edge.litertlm.** + 0.17.x tool entry-point keeps intact
- scripts/audit-dependencies.sh: green (no banned deps, no pre-releases)
- :app:assembleRelease: BUILD SUCCESSFUL

## Verification
- HF token gates (2 greps): PASS
- Search-surface gate: PASS
- `./gradlew :app:assembleDebug`: BUILD SUCCESSFUL
- `:app:testDebugUnitTest` (full suite): BUILD SUCCESSFUL
- `bash scripts/audit-dependencies.sh`: green
- `./gradlew :app:assembleRelease`: BUILD SUCCESSFUL
- Gap: on-device release smoke (launch → allowlisted model → local turn →
  remote turn → legacy TOOL chat) not runnable in this environment — human needed
