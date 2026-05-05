---
gsd_state_version: 1.0
milestone: v1.2
milestone_name: "GGUF Pipeline Remediation"
status: planning
last_updated: "2026-05-05"
last_activity: 2026-05-05
progress:
  total_phases: 0
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State: Warped

**Last updated:** 2026-05-05
**Last activity:** 2026-05-05

See: .planning/PROJECT.md

## Project Reference

**Core value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.
**Current focus:** Defining requirements
**Milestone:** v1.2 GGUF Pipeline Remediation

## Current Position

Phase: Not started (defining requirements)
Plan: —
Status: Defining requirements
Last activity: 2026-05-05 — Milestone v1.2 started

Progress: [░░░░░░░░░░] 0%

## Completed

- ✅ v1.0 MVP — 5 phases, 30 requirements, 107 Kotlin source files
- ✅ v1.1 LiteRT-LM Integration — 5 phases, 26 requirements

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

### Pending Todos

None yet.

### Blockers/Concerns

- v1.0 GGUF pipeline (LOCL-01–05, ACQ-01–05, DEV-01–02) marked complete but user reports end-to-end flow is broken — this milestone fixes it.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260504-lmi | Fix LiteRT-LM conversation history: reuse conversation across messages | 2026-05-04 | 3acfc1f | [260504-lmi-litert-lm-solo-env-a-el-primer-mensaje-d](./quick/260504-lmi-litert-lm-solo-env-a-el-primer-mensaje-d/) |

## Session Continuity

Last session: 2026-05-05
Stopped at: Started milestone v1.2 — defining requirements
Resume file: None
