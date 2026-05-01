# Phase 3: Model Acquisition — Context

**Phase:** 3 — Model Acquisition
**Status:** In Progress
**Created:** 2026-04-30

## Phase Boundary

From ROADMAP: "Let users discover and download GGUF models directly from Hugging Face in-app — with search, GGUF filtering, file details, foreground download notifications, pause/resume, and storage validation."

**Requirements:** ACQ-01, ACQ-02, ACQ-03, ACQ-04, ACQ-05

**Success Criteria:**
1. User can search Hugging Face for models, filter to GGUF-only, and see relevant results with model names and descriptions
2. User can view a model's file list showing per-file size and quantization type (Q2 through Q8) before deciding to download
3. User sees a foreground notification with percentage progress during download, and the download continues if the app is backgrounded
4. User can pause a download, close the app, return later, and resume from the same byte offset without data loss
5. User receives a warning and the download is blocked if free storage is less than 110% of the model file size

## Implementation Decisions

### Hugging Face API Client
- **Retrofit client** at `https://huggingface.co/api/` for search and model detail
- Reuses the shared `OkHttpClient` singleton from `NetworkModule` (Phase 1) for logging, connection pooling, and timeouts
- Uses `kotlinx.serialization` converter (already in project via Phase 1)
- DTOs model the HF Hub API response shape: `HuggingFaceModel` (list search), `HuggingFaceModelDetail` (single model with `siblings` for file list)
- GGUF filtering: `filter=gguf` query param on search endpoint; file-level filtering by `.gguf` extension in `siblings`

### Download Architecture
- **OkHttp Range header** (`bytes=$offset-`) for resume support — if file partially exists, the download continues from the last byte
- **Coroutine-based** downloader (`ModelDownloadManager`) running on `Dispatchers.IO`
- Pause/resume via in-memory `isPaused` flag — suspends the write loop; resume clears the flag
- **NOTE:** True cross-process-death pause/resume (criterion 4) requires WorkManager integration. Current implementation supports pause/resume while the app process is alive. A future iteration should create a `WorkManager Worker` with `setForeground()` for full background resilience.

### Storage Validation
- Uses `StatFs` to check available bytes on the internal storage partition
- Blocks download if `availableBytes < requiredBytes * 1.1` (110% headroom)
- Error message returned as `Result.failure` with human-readable MB values

### Post-Download
- After download completes, parses GGUF metadata via `GgufMetadataParser` (reused from Phase 2)
- Saves model metadata to Room via `LocalModelRepository` (reused from Phase 2), making downloaded models immediately visible in the Models tab

### UI
- Single-screen with two states: search (list results) and detail (file browser)
- `HuggingFaceScreen` composable manages navigation internally (no nested NavGraph)
- `collectAsStateWithLifecycle()` for lifecycle-aware state collection
- Material 3 components: `OutlinedTextField`, `Card`, `LinearProgressIndicator`, `Snackbar`
- New "HF" bottom tab with `Icons.Filled.Search` icon

## Code Context

### Reused from Phase 1
- `NetworkModule.provideOkHttpClient()` — shared HTTP client with logging, auth interceptor, connection pool
- `NetworkModule.provideJson()` — `kotlinx.serialization.Json` instance (`ignoreUnknownKeys = true`, `isLenient = true`)
- `kotlinx.serialization` converter for Retrofit
- Material 3 theming, Compose BOM

### Reused from Phase 2
- `GgufMetadataParser` — parses GGUF header for architecture, quantization, parameter count
- `LocalModelRepository` — saves downloaded model metadata to Room DB
- `LocalModel` — domain model with id, name, filePath, sizeBytes, quantization, parameterCount, architecture, importedAt
- `MemoryChecker` — available for potential memory-aware features

### New Dependencies
- `androidx.work:work-runtime-ktx` — added to `libs.versions.toml` and `build.gradle.kts` for future WorkManager integration (not yet used in this phase's code, but the dependency is wired for Phase 5+)

### Files Created/Modified

| File | Action | Purpose |
|------|--------|---------|
| `data/remote/api/HuggingFaceApi.kt` | CREATE | Retrofit API interface |
| `data/remote/dto/HuggingFaceDtos.kt` | CREATE | Serializable DTOs for HF API |
| `domain/repository/HuggingFaceRepository.kt` | CREATE | Domain interface |
| `data/repository/HuggingFaceRepositoryImpl.kt` | CREATE | Retrofit-backed implementation |
| `data/local/download/ModelDownloadManager.kt` | CREATE | OkHttp-based GGUF downloader |
| `ui/huggingface/HuggingFaceUiState.kt` | CREATE | UI state data class |
| `ui/huggingface/HuggingFaceViewModel.kt` | CREATE | HiltViewModel for HF screen |
| `ui/huggingface/HuggingFaceScreen.kt` | CREATE | Composable screen |
| `di/HuggingFaceModule.kt` | CREATE | Hilt binds for HF repository |
| `ui/navigation/Screen.kt` | EDIT | Add HF tab route |
| `ui/navigation/NavGraph.kt` | EDIT | Add HF composable + bottom tab |
| `gradle/libs.versions.toml` | EDIT | Add `workmanager` version |
| `app/build.gradle.kts` | EDIT | Add `work-runtime-ktx` dependency |

## Potential Pitfalls

1. **HF API rate limiting** — No auth token used for public search. Could hit rate limits for heavy usage. Future: add optional HF token support.
2. **Large file downloads on mobile data** — No WiFi-only constraint yet. WorkManager `Constraints` would solve this.
3. **Resume reliability** — If the server doesn't support `Range` header, resume will start from byte 0 (full re-download). HF CDN does support Range headers.
4. **Cancel vs. Pause** — Current implementation only has pause/resume. Adding cancel (delete partial file) would be a UX improvement.
5. **GGUF file deduplication** — If the same file is downloaded twice, two copies exist on disk. Filename-based dedup prevents this, but modelId+filename would be more robust.
