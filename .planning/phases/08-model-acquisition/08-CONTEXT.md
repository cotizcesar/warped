# Phase 8: Model Acquisition - Context

**Gathered:** 2026-05-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Users can discover, download, and import `.litertlm` models from Hugging Face's litert-community — extending the existing download infrastructure. This phase adds `.litertlm` search to the existing HuggingFaceApi, extends ModelImportManager, and ensures model_format is tracked correctly on download/import.

**Depends on:** Phase 6 (Room schema with model_format column, EngineManager)
**Requirements:** ACQ-06, ACQ-07, ACQ-08, ACQ-09, ACQ-10
</domain>

<decisions>
## Implementation Decisions

### API & Search Integration
- Add optional `filter` param to existing `HuggingFaceApi.searchModels()` — backward-compatible with GGUF search
- Use `filter=litertlm` for litert-community models
- Extend `HuggingFaceRepository` with `searchByFormat(query, format)` method
- No separate API interface or repository — all HF models share the same infrastructure

### Download & Import
- Reuse existing `ModelDownloadManager` — format-agnostic, downloads any URL to filesDir/models
- Extend `ModelImportManager` to accept `.litertlm` extension for file picker filter
- Set `model_format` based on file extension when saving to Room:
  - `.gguf` → `"GGUF"`
  - `.litertlm` → `"LITERTLM"`
- Download progress, pause/resume, and storage validation already implemented in ModelDownloadManager

### the agent's Discretion
- Exact filter string for litert-community (may need HF API testing)
- Metadata parsing for .litertlm files (may have different header format than GGUF)
- UI integration (model list, download progress) handled in Phase 9
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `HuggingFaceApi` — Retrofit interface with `searchModels(query, filter, sort, direction, limit)`
- `HuggingFaceRepository` — domain interface with `searchModels()`, `getModelDetail()`
- `HuggingFaceRepositoryImpl` — Retrofit-based implementation
- `ModelDownloadManager` — `startDownload()`, pause/resume, progress tracking, storage validation
- `ModelImportManager` — imports files via system file picker, copies to models dir, saves metadata to Room
- `ModelDownloadWorker` — WorkManager worker for foreground download notifications
- `HuggingFaceScreen/ViewModel` — UI for model search/discovery (Phase 9 will extend)

### Established Patterns
- Retrofit `@Query("filter")` for format filtering
- `Result<T>` return types for repository methods
- `MutableStateFlow<DownloadState>` for download progress
- WorkManager `Worker` with `setForeground()` for download notifications

### Integration Points
- `HuggingFaceApi.kt`: add format parameter
- `ModelImportManager.kt`: add `.litertlm` extension support
- `ModelDownloadManager.kt`: set model_format based on file extension when saving
- Phase 9: UI tabs and model list will consume this infrastructure
</code_context>

<specifics>
## Specific Ideas

No specific requirements beyond ROADMAP success criteria — follow existing HF integration patterns from Phase 3.
</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.
</deferred>
