# Phase 23: GGUF Removal - Context

**Gathered:** 2026-05-09
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase — discuss skipped)

<domain>
## Phase Boundary

Completely remove llama.cpp/GGUF support — native code, Kotlin engine, JNI bridge, NDK config, and all GGUF references across the codebase. LiteRT-LM becomes the sole local inference engine.

This is a pure infrastructure/cleanup phase. No new features, no user-facing behavior changes beyond what disappears. The goal is elimination, not replacement.
</domain>

<decisions>
## Implementation Decisions

### the agent's Discretion
All implementation choices are at the agent's discretion — pure infrastructure phase. The ROADMAP tasks and REQUIREMENTS.md constraints (GGUF-01 through GGUF-07) are the spec. Follow existing codebase patterns (4-space indent, K&R braces, trailing commas, MVVM pattern) when modifying files.
</decisions>

<code_context>
## Existing Code Insights

### Files to Delete (entire)
- `app/src/main/cpp/` — llama.cpp submodule, JNI bridge (`jni_bridge.cpp`, `jni_bridge.h`), `CMakeLists.txt`
- `app/src/main/java/com/warped/data/local/inference/LlamaEngine.kt`
- `app/src/main/java/com/warped/data/local/inference/GgufMetadataParser.kt`
- `app/src/main/java/com/warped/data/local/inference/GgufQuantizationParser.kt` (if exists)
- `app/src/main/java/com/warped/data/local/inference/LlamaLoadError.kt` (if exists)

### Files to Modify (GGUF references to remove)
- `app/build.gradle.kts` — `ndk { abiFilters }`, `externalNativeBuild { cmake }`, `packaging { jniLibs }`
- `.gitmodules` — llama.cpp submodule entry
- `app/src/main/java/com/warped/data/local/db/Migrations.kt` — MIGRATION_6_7, MIGRATION_8_9 DEFAULT 'GGUF' → 'LITERTLM'
- `app/src/main/java/com/warped/data/local/db/entity/LocalModelEntity.kt` — modelFormat @ColumnInfo defaultValue
- `app/src/main/java/com/warped/domain/model/LocalModel.kt` — modelFormat default
- `app/src/main/java/com/warped/di/InferenceModule.kt` — provideLlamaEngine(), provideLocalLlmProvider()
- `app/src/main/java/com/warped/data/local/inference/EngineManager.kt` — LLAMA_CPP enum, switchToLlama(), probeVulkan(), getLlamaEngine()
- `app/src/main/java/com/warped/data/local/inference/BackendDetector.kt` — Vulkan-related logic
- `app/src/main/java/com/warped/data/local/inference/LocalLlmProvider.kt` — llamaEngine dependency
- `app/src/main/java/com/warped/data/local/inference/MemoryChecker.kt` — GGUF memory checks
- `app/src/main/java/com/warped/data/local/inference/ModelImportManager.kt` — GGUF metadata parsing, .gguf default
- `app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt` — GGUF validation
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` — LlamaEngine imports
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` — GGUF format pills (lines 173-186, 229)
- `app/src/main/java/com/warped/ui/models/ModelsScreen.kt` — GGUF RAM check badges (lines 380-401, 443-444)
- `app/src/main/java/com/warped/ui/help/HelpScreen.kt` — GGUF references (lines 71-72, 76, 94)
- `app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt` — GGUF references
- `app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt` — GGUF references
- `app/src/main/java/com/warped/ui/presets/PresetsScreen.kt` — GGUF references
- `app/src/main/java/com/warped/ui/presets/PresetsUiState.kt` — GGUF fields
- `app/src/main/java/com/warped/ui/presets/PresetsViewModel.kt` — GGUF logic
- `app/src/main/java/com/warped/ui/wizard/WizardViewModel.kt` — GGUF count logic
- `app/src/main/java/com/warped/ui/wizard/WizardStep.kt` — GGUF step enum
- `app/src/main/java/com/warped/ui/wizard/StepContent.kt` — GGUF content
- `app/src/main/res/values/strings.xml` — GGUF/llama.cpp/.gguf strings
- `app/src/main/res/values-es/strings.xml` — GGUF/llama.cpp/.gguf strings (Spanish)
- `app/proguard-rules.pro` — llama JNI keep rule (lines 17-18)
- `app/src/main/java/com/warped/data/local/db/entity/PresetEntity.kt` — GGUF default
- `app/src/main/java/com/warped/domain/model/Preset.kt` — GGUF default

### Established Patterns
- MVVM: ViewModels expose StateFlow<UiState> to Compose
- Hilt DI with @Singleton, @Provides in modules
- Room with @Dao interfaces, @Entity data classes, KSP annotation processing
- Kotlin code style: 4-space indent, K&R braces, trailing commas
</code_context>

<specifics>
## Specific Ideas

Phase 23 (wizard GGUF content) will be handled in Phase 27 (Wizard Update) per ROADMAP dependency ordering. Only non-wizard GGUF strings and references are removed in this phase. The wizard strings/step for GGUF remain for now.

ProviderType.LOCAL should be deprecated (not removed) to maintain backward compatibility, routing to LiteRT-LM.
</specifics>

<deferred>
## Deferred Ideas

- Full wizard GGUF content removal — deferred to Phase 27 (Wizard Update)
- Replacing GGUF wizard step content with LiteRT-LM content — deferred to Phase 27
</deferred>
