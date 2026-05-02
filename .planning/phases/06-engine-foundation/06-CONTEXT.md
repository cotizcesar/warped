# Phase 6: Engine Foundation - Context

**Gathered:** 2026-05-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Establish the LiteRT-LM engine with backend auto-detection, thread-safe lifecycle management, mutual exclusion with llama.cpp, and Room schema support — the foundation everything else depends on. This phase adds the Maven dependency, creates BackendDetector, LiteRTLmEngine wrapper, EngineManager for mutual exclusion, and migrates the Room schema for model format tracking. No user-facing chat or UI changes — those come in Phases 7-9.

Requirements: LITE-01, LITE-02, LITE-03, LITE-04, LITE-08, POL-01, POL-02
</domain>

<decisions>
## Implementation Decisions

### Build Integration & Dependencies
- Add `com.google.ai.edge:litertlm-android:0.11.0-beta01` as Maven dependency from Google Maven
- ProGuard/R8 keep rule: `-keep class com.google.ai.edge.litertlm.** { *; }`
- Declare version in `libs.versions.toml` version catalog (consistent with all existing dependencies)
- AndroidManifest: `<uses-native-library android:name="libOpenCL.so" android:required="false" />` per LITE-08

### Engine Architecture & Lifecycle
- Single `LiteRTLmEngine` wrapper class with internal `Backend` enum (CPU, GPU) — mirrors `LlamaEngine` pattern
- Manual `init(modelPath, backend)` / `close()` with `@Synchronized` for thread safety
- Engine is raw/synchronous; coroutine context switching happens in the caller (`LiteRTLmProvider` in Phase 7) via `flowOn(Dispatchers.Default)` — identical to how `LocalLlmProvider` wraps `LlamaEngine`
- Graceful close: cancel in-progress generation, close native resources, emit stop event — matches `nativeStop()` pattern

### BackendDetector & GPU Probing
- Lazy probing — probe when first LiteRT-LM model is loaded, not at app startup
- Backend types: `CPU` and `GPU` only (NPU deferred to v2 per Out of Scope decision)
- Probe method: `EGL15.eglGetDisplay()` + `OpenCL.available()` for lightweight platform-level check
- Cache probe result for app process lifetime via `@Singleton` (GPU availability doesn't change mid-session)

### Room Schema & EngineManager
- Add `model_format TEXT NOT NULL DEFAULT 'GGUF'` column to existing `local_models` table (Room v6 → v7 migration)
- Format values: `"GGUF"` and `"LITERTLM"` — matches file extensions and Hugging Face naming
- New `EngineManager` `@Singleton` class injected alongside InferenceModule — enforces mutual exclusion (only one local engine loaded at a time)
- Synchronous engine switch: `llamaEngine.unload()` before `litertlmEngine.init()` to prevent race conditions

### the agent's Discretion
- Exact LiteRT-LM Kotlin API surface (class names, method signatures) will be discovered at plan time from official docs/samples
- Specific GPU probe fallback logic ordering is at the agent's discretion
- Minor details like log messages and error strings are at the agent's discretion
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `LlamaEngine` (`data/local/inference/LlamaEngine.kt`) — singleton wrapper with JNI methods; pattern to mirror for `LiteRTLmEngine`
- `MemoryChecker` (`data/local/inference/MemoryChecker.kt`) — singleton using `ActivityManager` for RAM checks; reusable for POL-02 warning
- `InferenceModule` (`di/InferenceModule.kt`) — Hilt module providing `LlamaEngine`, `LocalLlmProvider`, `MemoryChecker` as singletons
- `DatabaseModule` (`di/DatabaseModule.kt`) — Hilt module providing Room database + DAOs with migrations array
- `Migrations.kt` — existing MIGRATION_4_5 and MIGRATION_5_6 patterns for Room version bumps

### Established Patterns
- **Singleton engines**: `@Singleton` + `@Inject constructor()` for all engine components
- **Hilt DI**: `@Module @InstallIn(SingletonComponent::class)` with `@Provides @Singleton` for factory-created deps
- **Room migrations**: `Migration(from, to)` objects added to `addMigrations()` list in `DatabaseModule`
- **Provider interface**: `LlmProvider` interface with `chat()`, `listModels()`, `testConnection()` — Phase 7 will implement for LiteRT-LM
- **Flow-based streaming**: `callbackFlow` for JNI → Kotlin token streaming (llama.cpp), `flow { }` builder for provider wrappers

### Integration Points
- `AppDatabase` v6 → v7 migration: add `model_format` column to `local_models` table
- `InferenceModule`: add `EngineManager` and `BackendDetector` providers
- `proguard-rules.pro`: add LiteRT-LM keep rules
- `AndroidManifest.xml`: add `<uses-native-library>` for libOpenCL
- `libs.versions.toml`: add litertlm version and library declaration
- `app/build.gradle.kts`: add `libs.litertlm` dependency
- `ProviderType` enum: may need `LITE_RT_LM` variant (defer to Phase 7)
</code_context>

<specifics>
## Specific Ideas

No specific requirements beyond ROADMAP success criteria — open to standard approaches following existing engine/provider patterns from v1.0.
</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.
</deferred>
