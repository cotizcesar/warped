---
gsd_state_version: 1.0
milestone: v1.5
milestone_name: Bug Hunt, Cleanup & Hardening Pre-Prod
status: Completed Phase 24 (Search Simplification), ready for Phase 25
stopped_at: Completed 24-01-PLAN.md (Search Simplification)
last_updated: "2026-05-10T04:28:56.000Z"
last_activity: 2026-05-10 — Phase 24 complete
progress:
  total_phases: 5
  completed_phases: 2
  total_plans: 2
  completed_plans: 2
  percent: 100
---

# Project State: Warped

**Last updated:** 2026-05-10
**Last activity:** 2026-05-10 — Phase 24 complete
**Milestone:** v1.5 Bug Hunt, Cleanup & Hardening Pre-Prod — In Progress

## Current Position

Phase: 24-search-simplification
Plan: 01 — COMPLETE ✅
Status: Ready for Phase 25 (Bug Fixes)
Last activity: 2026-05-10 — Phase 24 Search Simplification completed

## Phase Structure

| Phase | Name | Requirements | Status | Depends On |
|-------|------|--------------|--------|------------|
| 23 | GGUF Removal | GGUF-01..07 (7) | ✅ Complete | — |
| 24 | Search Simplification | SRCH-01..04 (4) | ✅ Complete | Phase 23 |
| 25 | Bug Fixes | BUG-01..04 (4) | ⏳ Pending | Phase 23 |
| 26 | Security Hardening | SEC-01..08 (8) | ⏳ Pending | — |
| 27 | Wizard Update | WZRD-01..03 (3) | ⏳ Pending | Phase 23 |

## Completed

- ✅ v1.0 MVP — 5 phases, 30 requirements, 107 Kotlin source files
- ✅ v1.1 LiteRT-LM Integration — 5 phases, 26 requirements
- ✅ v1.2 GGUF Native Inference — 5 phases, 27 requirements, 24 success criteria
- ✅ v1.3 Remote Provider Endpoints & UX — 4 phases, 31 requirements
- ✅ v1.4 Onboarding Wizard — 3 phases, 25 requirements, 10 files (7 new, 3 modified)

## v1.4 Deliverables

| File | Status |
|------|--------|
| `data/local/preferences/WizardPreferences.kt` | NEW |
| `ui/wizard/WizardUiState.kt` | NEW |
| `ui/wizard/WizardViewModel.kt` | NEW |
| `ui/wizard/WizardScreen.kt` | NEW |
| `ui/wizard/WizardStep.kt` | NEW |
| `ui/wizard/StepContent.kt` | NEW |
| `ui/components/PageIndicator.kt` | NEW |
| `ui/navigation/Screen.kt` | MODIFIED |
| `ui/navigation/NavGraph.kt` | MODIFIED |
| `ui/settings/SettingsScreen.kt` | MODIFIED |

## Accumulated Context

### Decisions

- [v1.1]: LiteRT-LM as second local inference engine alongside llama.cpp via Maven dependency (no NDK/CMake)
- [v1.1]: Per-call Conversation factory pattern to avoid MediaTek SIGSEGV
- [v1.1]: BackendDetector runtime GPU probing with CPU fallback to prevent Tensor G3 crashes
- [v1.1]: EngineManager mutual exclusion — only one local engine loaded at a time
- [06-01]: D-01/D-03 — litertlm-android:0.11.0-rc1 added via version catalog; 0.11.0-beta01 did not exist on Google Maven
- [06-01]: D-02 — ProGuard keep rule for all com.google.ai.edge.litertlm classes to preserve public API during R8 minification
- [06-01]: D-04 — libOpenCL.so and libvndksupport.so declared as optional native libraries (required="false") for GPU backend support
- [06-02]: D-13 — model_format TEXT NOT NULL DEFAULT 'GGUF' column added to local_models via MIGRATION_6_7
- [06-02]: D-14 — modelFormat field validates against 'GGUF'/'LITERTLM' values, defaults to 'GGUF'
- [06-02]: engine_type column intentionally excluded — only model_format in this migration per user direction
- [06-03]: D-09/D-10/D-11/D-12 — BackendDetector lazy GPU probe via EGL14 + OpenCL, @Volatile caching, CPU/GPU only (NPU deferred)
- [06-03]: D-05/D-06/D-07/D-08 — LiteRTLmEngine @Synchronized lifecycle wrapper, Engine.setNativeMinLogSeverity in companion init, caller-managed dispatching, graceful close with null cleanup
- [Phase ?]: EngineManager @Singleton managing LlamaEngine + LiteRTLmEngine — only one local engine loaded at a time
- [Phase ?]: Synchronous engine switch via unloadCurrent() before init — @Synchronized prevents concurrent switches
- [Phase ?]: ActiveEngine data class tracks type + modelPath + backend for dedup on redundant switch calls
- [Phase ?]: BackendDetector.probeBackend() called lazily inside switchToLiteRT() — cached result via @Volatile
- [07-01]: D-01 — LITE_RT_LM added as 7th ProviderType enum value for LiteRT-LM routing
- [07-01]: D-02 — InputSanitizer as standalone utility with 5-category surgical regex (LaTeX, Unicode math, control chars, surrogates, zero-width)
- [07-01]: D-03 — SamplerConfig mapping inline — temperature→temperature, topK→topK, topP→topP, seed→seed (0 when -1)
- [07-01]: D-04 — 2-retry error recovery via sendMessageWithRetry with EngineManager.switchToLiteRT() reinitialization
- [07-01]: D-05 — maxTokens via ConversationConfig.extraContext ("max_output_tokens") — no direct field in v0.11.0-rc1
- [07-01]: D-06 — Toast notification deferred to Timber.w() — Context not injected in provider layer
- [07-02]: D-01 — LiteRTLmProvider and InputSanitizer explicitly provided via @Provides (not relying on @Inject auto-discovery), following existing InferenceModule convention
- [07-02]: D-02 — LiteRTLmProvider injected via dagger.Lazy in ProviderRouter (lazy init, consistent with LocalLlmProvider pattern); no .configure(modelId) call needed — model path comes from EngineManager
- [Phase 08]: modelFormat defaults to GGUF for backward compatibility; field ordering matches LocalModelEntity
- [Phase 08]: format parameter defaults to gguf so existing callers dont break; no separate repository for litertlm
- [08-03]: D-08-03a — Format detection by file extension (not magic bytes), file extension is canonical signal per D-03 context decision
- [08-03]: D-08-03b — .litertlm metadata defaults: quantization='N/A', architecture='LiteRT-LM', parameterCount='Unknown' — deferred to Phase 10
- [08-03]: D-08-03c — formatFiles replaces ggufFiles throughout ViewModel; default activeFormat='gguf' preserves existing behavior
- [08-03]: D-08-03d — setActiveFormat() clears search results and selected model, auto-re-searches for Phase 9 TabRow integration
- [Phase 08]: D-GAP-01: WorkManager replaces CoroutineScope for download execution with foreground notifications and Room-persisted checkpoints (survives process death, supports pause/resume)
- [Phase 08]: D-GAP-02: Blacklist pipelineTag filtering for litertlm search results — exclude vision/speech models, include everything else (gated on activeFormat)
- [10-01]: D-10-01a — model_format on presets uses same DEFAULT 'GGUF' pattern as local_models
- [10-01]: D-10-01b — Cross-format warning only triggers when local engine loaded; remote providers format-agnostic
- [10-01]: D-10-01c — Grey-out uses Material3 enabled=false + alpha 0.38f modifier with onSurfaceVariant label
- [10-01]: D-10-01d — FormatBadge composable reused from Phase 9 ModelsScreen (GGUF=blue, LiteRT-LM=green)
- [10-01]: D-10-01e — activeFormat derived from EngineManager.getActiveEngine()?.type, defaults to "GGUF"
- [v1.4]: WizardPreferences DataStore with wizard_completed + skipped_steps keys
- [v1.4]: HorizontalPager 9-page shell with TopAppBar/bottomBar navigation
- [v1.4]: First-launch redirect via LaunchedEffect + flash guard
- [v1.4]: WizardStep enum with 9 steps, warm Spanish descriptions, Material icons
- [v1.4]: Context-aware step content with count badges and adapted descriptions
- [v1.4]: Re-entry review mode with isReEntry flag, "Close wizard"/"Revisar" labels
- [v1.4]: Back navigation: exit dialog on first launch page 0, back to Settings on re-entry
- [23-01]: MemoryChecker retained in ChatViewModel — removing it alongside LlamaEngine would break non-GGUF memory checks (shouldWarn, canLoadModel, getMemoryInfo still used)
- [23-01]: LocalLlmProvider deprecated as no-op stub instead of deleted — backward compatibility with existing injection points
- [23-01]: Wizard files NOT modified per CONTEXT.md deferral to Phase 27
- [24-01]: Hardcoded library=litert at HuggingFaceRepositoryImpl level — callers pass only query + author
- [24-01]: Removed library default from HuggingFaceApi.searchModels() — all callers pass explicit library value
- [24-01]: FormatBadge simplified to parameterless composable — always green 0xFF4CAF50 for LiteRT-LM

### Pending Todos

None yet.

### Blockers/Concerns

- v1.2 GGUF pipeline completed — llama.cpp JNI integration, Vulkan GPU backend, OOM handling all implemented.
- v1.3 Remote providers completed — all provider types exposed, API keys secure.
- v1.4 Onboarding wizard completed — full-screen 9-step flow with context-aware content.
- LM Studio API changes may require updates to match latest LM Studio version (0.4.0+).

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260504-lmi | Fix LiteRT-LM conversation history: reuse conversation across messages | 2026-05-04 | 3acfc1f | [260504-lmi-litert-lm-solo-env-a-el-primer-mensaje-d](./quick/260504-lmi-litert-lm-solo-env-a-el-primer-mensaje-d/) |

## Session Continuity

Last session: 2026-05-10
Stopped at: Completed 24-01-PLAN.md (Search Simplification)
Resume file: None
