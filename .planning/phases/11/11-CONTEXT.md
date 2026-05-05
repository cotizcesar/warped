# Phase 11: Native Foundation — CMake Build, Hugging Face GGUF Pipeline & Validation - Context

**Gathered:** 2026-05-05
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase establishes the foundation for GGUF native inference: llama.cpp compiles from source via CMake+NDK for Android arm64-v8a and x86_64, producing a single `libwarped_llama.so` shared library. ProGuard/R8 keep rules preserve all JNI methods in a dedicated package. On the UX side, users browse .gguf files on Hugging Face with per-file quantization metadata parsed from filenames, see estimated RAM requirements before downloading, download with foreground progress + pause/resume via WorkManager, and every downloaded file passes automatic integrity validation (magic number + header).

All in-scope requirements: NTVL-01 (llama.cpp compiles), NTVL-05 (ProGuard rules), HFDL-01 (GGUF file browsing with quantization), HFDL-02 (RAM estimates), HFDL-03 (download with progress/resume), HFDL-04 (file validation), MEMS-04 (pre-validation to prevent corrupted-file crashes).

Out of scope: actual model loading via JNI (Phase 12), streaming inference (Phase 13), Vulkan GPU backend (Phase 14), cross-engine UX parity (Phase 15).

</domain>

<decisions>
## Implementation Decisions

### llama.cpp CMake Integration
- **Git submodule** at `app/src/main/cpp/llama.cpp/` for full build control and reproducible builds
- **CPU-only in Phase 11** (`GGML_USE_CPU=1`, `GGML_USE_CPU_AARCH64=1`); Vulkan (`GGML_VULKAN=ON`) deferred to Phase 14
- **Latest stable tag** (b8987+) targeting GGUF v3 with full key-value metadata support
- **Single `libwarped_llama.so`** shared library — llama.cpp compiled as object libraries linked into one .so, matching existing CMakeLists.txt structure

### GGUF File Browsing & Quantization Display
- **Parse quantization from filename** via regex matching `Q[2-8]_[0KMS]` patterns (e.g., `Q4_K_M`, `Q5_K_M`, `Q8_0`); no extra network calls needed
- **GGUF-only view by default** with a "show all files" toggle, consistent with existing `activeFormat` pattern (gguf/litertlm tabs)
- **Text label** RAM estimate: "~3.9 GB RAM needed" with warning icon if above device free RAM; consistent with existing DEV-01 patterns
- **Extend existing `HuggingFaceViewModel`/`HuggingFaceUiState`** — add `ramEstimate` fields and quantization parsing to `modelSiblings`; no separate ViewModel needed

### Download Validation & File Integrity
- **Validate at both times**: post-download (before adding to model list) AND pre-load (catch disk corruption); pre-load check is fast (magic number + header offset only)
- **Magic number + header integrity** post-download: verify "GGUF" magic, version field, KV/tensor counts are consistent with file size; covers 99% of corruption, fast for large files
- **Auto-delete corrupted files** + snackbar with retry action; no invalid files in model list, consistent with existing cancel-download cleanup
- **Extend existing `ModelDownloadWorker`** with GGUF validation step after download completes (before `saveModel` call); reuse checkpoint, progress, gated-model logic

### ProGuard Rules & Build Configuration
- **Per-package wildcard ProGuard rule**: `-keep class com.warped.data.local.inference.llama.** { native <methods>; }`; matches LiteRT-LM blanket rule pattern at `proguard-rules.pro:15`
- **Automatic submodule clone** via Gradle task running `git submodule update --init` before CMake configure; zero manual setup for developers
- **arm64-v8a + x86_64** ABI targets only; skip armeabi-v7a (32-bit devices lack RAM for LLMs)
- **Companion object constant** for `System.loadLibrary("warped_llama")` in the JNI wrapper class; simple, discoverable, Android convention

### the agent's Discretion
All decisions above were accepted by the user in smart discuss. The agent has latitude to adjust implementation details within the accepted direction.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`ModelDownloadWorker`** (`data/local/download/ModelDownloadWorker.kt`) — full-featured foreground download worker with HTTP Range headers, checkpoint persistence, progress/speed tracking, gated model auth, and `GgufMetadataParser` integration. Reuse/extend for GGUF validation.
- **`ModelDownloadManager`** (`data/local/download/ModelDownloadManager.kt`) — WorkManager orchestration with pause/resume, storage checks, `DownloadState` StateFlow, observer cleanup. Already handles GGUF and LiteRT-LM paths.
- **`HuggingFaceApi`** (`data/remote/api/HuggingFaceApi.kt`) — Retrofit interface for HF Hub API: `searchModels`, `getModelDetail`, `getCollection`. Returns `HuggingFaceSibling` with `rfilename` and `size`.
- **`HuggingFaceViewModel`** (`ui/huggingface/HuggingFaceViewModel.kt`) — Search, model detail, download orchestration, format filtering via `activeFormat`. Extend for quantization display and RAM estimates.
- **`HuggingFaceUiState`** (`ui/huggingface/HuggingFaceUiState.kt`) — State holder with search results, siblings, download progress. Add `ramEstimate`, `quantizationLabel` fields.
- **`GgufMetadataParser`** (`data/local/inference/GgufMetadataParser.kt`) — Reads GGUF magic, version, KV metadata, maps `file_type` to quantization labels. Reuse for header validation logic.
- **`DownloadCheckpointDao`/`DownloadCheckpointEntity`** — Room-persisted download checkpoints for pause/resume. Already used by Worker.

### Established Patterns
- **Clean Architecture**: `domain/repository/HuggingFaceRepository` interface → `data/repository/HuggingFaceRepositoryImpl` implementation; DI via Hilt `@Singleton` with `@Inject constructor`
- **MVVM + StateFlow**: ViewModels expose `StateFlow<UiState>` to Compose via `collectAsStateWithLifecycle()`
- **Hilt modules**: Per-feature modules (e.g., `HuggingFaceModule.kt`) in `di/` package
- **CMake configuration**: `CMakeLists.txt` at `app/src/main/cpp/`, Gradle `externalNativeBuild { cmake { path } }` block
- **ProGuard rules**: `app/proguard-rules.pro` with package-level keep rules (LiteRT-LM: `-keep class com.google.ai.edge.litertlm.** { *; }`)
- **WorkManager Hilt**: `@HiltWorker` with `@AssistedInject` for constructor injection
- **Format detection**: By file extension (`.gguf`, `.litertlm`), not magic bytes — per Phase 08 decision

### Integration Points
- **`HuggingFaceApi.getModelDetail()`** returns `HuggingFaceModelDetail` with `siblings` — already contains `rfilename` and `size` fields; parse `.gguf` files from this list
- **`ModelDownloadWorker.doWork()`** calls `GgufMetadataParser.parse()` and `localModelRepository.saveModel()` after download — insert GGUF validation between download completion and model save
- **`HuggingFaceViewModel.downloadFile()`** constructs the download URL and calls `downloadManager.startDownload()` — remains the entry point for GGUF downloads
- **`HuggingFaceScreen.kt`** Compose UI — consumes `modelSiblings` from `HuggingFaceUiState`; add quantization badge and RAM estimate to each sibling row
- **CMake integration** — Gradle `build.gradle.kts` already has `externalNativeBuild { cmake { path "src/main/cpp/CMakeLists.txt" } }` block; add llama.cpp source paths and include directories
</code_context>

<specifics>
## Specific Ideas

- Quantization labels extracted from GGUF filenames should match the exact format users see on Hugging Face (Q4_K_M, Q5_K_S, Q8_0, F16, IQ2_XXS, etc.) — use a regex that captures the full quantization string between the model name and ".gguf"
- The RAM estimate (file_size × 1.3) should be a static calculation displayed inline — no need for `ActivityManager.MemoryInfo` reads at browse time, only at load time (Phase 12)
- The "show all files" toggle should be a simple switch/checkbox in the model detail header, not a full tab bar — .gguf files are the primary focus of this phase
- ProGuard keep rules should be grouped with the existing LiteRT-LM rule under a `# llama.cpp JNI` section header in `proguard-rules.pro`

</specifics>

<deferred>
## Deferred Ideas

- Vulkan GPU compilation (`GGML_VULKAN=ON`) — deferred to Phase 14
- SHA256 checksum validation against Hugging Face LFS metadata — deferred (current magic+header approach covers >99% of corruption; SHA256 adds complexity without proportional value)
- armeabi-v7a ABI support — 32-bit devices lack sufficient RAM for LLM inference
- Model format tabs (GGUF/LiteRT-LM/All) in model detail view — current format filter is sufficient for GGUF-only browsing
</deferred>
