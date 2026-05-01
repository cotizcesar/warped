# Phase 2: Local Inference - Context

**Gathered:** 2026-04-30
**Status:** Ready for planning
**Mode:** Smart Discuss (autonomous — recommendations auto-accepted)

<domain>
## Phase Boundary

Enable on-device GGUF model inference via llama.cpp JNI bridge. This phase delivers:
1. CMake/NDK build integration for llama.cpp + custom JNI wrapper
2. Model import from device storage via system file picker with metadata extraction
3. Local LLM provider implementing `LlmProvider` with streaming chat, cancel/stop
4. RAM-based memory warnings before loading large models
5. Model management UI (list imported models, delete, view metadata)
6. Integration with existing ChatScreen from Phase 1 for local chat sessions

Phase does NOT include: downloading models from Hugging Face (Phase 3), generation parameter presets (Phase 4), or local model fine-tuning.
</domain>

<decisions>
## Implementation Decisions

### JNI Bridge Architecture
- Thin C++ JNI wrapper around `libllama` (not full llama.cpp CLI port)
- Expose 6 core functions: `llama_load_model`, `llama_chat`, `llama_stop`, `llama_tokenize`, `llama_get_model_info`, `llama_free`
- JNI calls back into Kotlin via listener interface for token streaming — wrapped in `callbackFlow {}`
- CMake builds `arm64-v8a` and `x86_64` ABIs (x86_64 for emulator only)
- GGUF format only — no GGML backward compatibility
- Use `-p thread_count` CPU inference initially; Vulkan/Hexagon deferred to future

### Model Import Flow
- System file picker (`ACTION_OPEN_DOCUMENT`) with `.gguf` MIME filter
- After selection: read GGUF header to extract metadata (model name, architecture, quantization type, parameter count, file size)
- Copy file to `context.filesDir/models/` (app-private storage) during import
- Parse GGUF metadata keys: `general.name`, `general.architecture`, `general.quantization_version`, `general.file_type`, `llama.embedding_length`, `llama.block_count`, `llama.context_length`
- Show import progress via WorkManager foreground notification

### Memory Management
- Check available RAM via `ActivityManager.MemoryInfo` before model load
- Warning threshold: model file size > 80% of `availMem` (not `totalMem` — accounts for OS usage)
- Show dialog: "This model requires ~X MB. Your device has ~Y MB available. Loading may cause instability. Continue?"
- On `OutOfMemoryError`: catch gracefully, show "Not enough memory" error, suggest smaller quantization
- Native memory is NOT tracked by JVM heap — use `android:largeHeap="true"` already set in Phase 1
- Keep only ONE model loaded at a time (unload previous before loading new)

### Local Provider Integration
- Create `LocalLlmProvider` implementing `LlmProvider` interface (reuse existing contract)
- Provider type: `ProviderType.LOCAL` (already defined in Phase 1)
- Chat flow: receive `ChatRequest` → convert messages to llama.cpp prompt format → JNI call → stream tokens via callback → emit `StreamToken.Delta`
- Cancel: `llama_stop()` JNI call → unload model → emit `StreamToken.Done`
- Model unload: call `llama_free()` in `onCleared()` of ViewModel to ensure cleanup on config change/process death
- Threading: all JNI calls run on dedicated `Dispatchers.Default` thread; token emission back to `Dispatchers.Main`
- Token batching: reuse same 50ms batching strategy from Phase 1 ChatViewModel

### UI Reuse
- Reuse `ChatScreen`, `MessageBubble`, `ChatInputBar` from Phase 1 — local chat is same UX as remote
- Add provider selector in ChatScreen to toggle between remote and local providers
- Add Models tab content (replacing placeholder): list of imported local models with name, size, quantization
- Model detail screen: shows metadata, "Load" button (with RAM check), "Delete" button
- Loading state: progress indicator while model loads (may take 2-10 seconds for 7B model)

### Data Persistence
- New Room entity: `LocalModelEntity` with fields: id, name, file_path, size_bytes, quantization, parameter_count, architecture, imported_at
- New DAO: `LocalModelDao` with observeAll, insert, delete
- Repository: `LocalModelRepository` (extend domain `ModelRepository` or create separate)
- Model files survive app restart (stored in `filesDir/models/`)

### the agent's Discretion
- Exact JNI function signatures (optimize for Kotlin interop)
- llama.cpp build flags (CPU optimizations: `-mfpu=neon`, `-march=armv8-a`)
- GGUF header parsing library choice (manual binary parsing vs. existing Kotlin library)
- UI layout specifics for model list/detail screens
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets (from Phase 1)
- `LlmProvider` interface — local provider implements this directly
- `ChatViewModel` — token batching, stopGeneration, streaming pattern can be adapted
- `ChatScreen` / `MessageBubble` / `ChatInputBar` — fully reusable
- `ProviderRouter` — add `LOCAL` branch
- `ProviderType.LOCAL` — already defined in domain model
- `AppDatabase` — extend with `LocalModelEntity`
- Hilt DI module pattern (`DatabaseModule`, `NetworkModule`) — follow for native dependencies
- `WarpedApplication` — already has `largeHeap="true"` and `extractNativeLibs="false"`
- `network_security_config.xml` — no changes needed

### Established Patterns
- MVVM: ViewModel exposes `StateFlow<UiState>` to Compose
- Repository pattern: domain interfaces, data layer implementations with @Inject
- Hilt DI: @Module @InstallIn(SingletonComponent) for singletons
- Room: entities with @Entity, DAOs with @Dao, Flow-based observation
- SSE streaming: `flow {}` builders with callback-based emission

### Integration Points
- `app/build.gradle.kts` — add `externalNativeBuild { cmake { ... } }` block
- `app/src/main/cpp/` — new directory for JNI bridge C++ code
- `app/src/main/java/com/warped/data/local/inference/` — new package for inference engine
- `ProviderRouter.kt` — add `LOCAL` case
- `WarpedNavGraph.kt` — update Models tab destination
- `AppDatabase.kt` — add LocalModelEntity to @Database annotation
</code_context>

<specifics>
## Specific Ideas

- llama.cpp releases Android arm64 binaries on every commit — build from source using CMake
- Model names use standardized format: "llama-3-8b-Q4_K_M.gguf" (name-quantization.gguf)
- Quantization types to display: Q2_K, Q3_K_S, Q3_K_M, Q3_K_L, Q4_0, Q4_K_S, Q4_K_M, Q5_0, Q5_K_S, Q5_K_M, Q6_K, Q8_0
- Use `android:requestLegacyExternalStorage="true"` for file picker on API 28-29
- NDK version: 27.x (as specified in STACK.md)
- CMake minimum: 3.22 (as specified in STACK.md)
</specifics>

<deferred>
## Deferred Ideas

- Vulkan GPU acceleration (Phase 2+)
- Hexagon NPU support (future — Snapdragon 8 Gen 3+)
- Multi-model concurrent loading
- Remote model download integration (Phase 3 covers Hugging Face downloads)
- GGUF model quantization/re-quantization on-device
</deferred>

---

*Phase: 02-local-inference*
*Context gathered: 2026-04-30 via Smart Discuss (autonomous)*
