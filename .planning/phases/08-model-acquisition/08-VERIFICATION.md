---
phase: 08-model-acquisition
verified: 2026-05-02T13:30:00Z
status: gaps_found
score: 3/5 roadmap criteria verified, 12/14 plan must-haves verified
overrides_applied: 0
overrides: []

gaps:
  - truth: "User sees a foreground progress notification during .litertlm downloads, and downloads continue if the app is backgrounded"
    status: failed
    reason: "No foreground service, WorkManager Worker, or Android notification exists for any download (GGUF or .litertlm). Download runs in CoroutineScope(SupervisorJob() + Dispatchers.IO) which dies on process death or extended backgrounding. No code references setForeground(), FOREGROUND_SERVICE, POST_NOTIFICATIONS, or any Worker subclass anywhere in the project."
    artifacts:
      - path: "app/src/main/java/com/warped/data/local/download/ModelDownloadManager.kt"
        issue: "Download runs in volatile CoroutineScope — no foreground service binding, no notification channel, no WorkManager integration. Lines 46-214 use downloadScope.launch which doesn't survive process death."
    missing:
      - "WorkManager download Worker with setForeground() for persistent foreground notification"
      - "AndroidManifest FOREGROUND_SERVICE + POST_NOTIFICATIONS permission declarations"
      - "Notification channel creation and download progress notification"

  - truth: "User can pause an in-progress .litertlm download, close the app, return, and resume without data loss"
    status: failed
    reason: "Pause mechanism is in-memory only (isPaused flag in DownloadState StateFlow). No checkpoint persistence to Room or DataStore. On process death, all download state is lost — there's no way to resume because the starting offset is unknown. The Range header (line 97) enables technical resume but offset comes from destFile.length() which survives — however the coroutine that drives the download does not. The resumeDownload() method (line 220) only sets isPaused=false but doesn't restart the download loop."
    artifacts:
      - path: "app/src/main/java/com/warped/data/local/download/ModelDownloadManager.kt"
        issue: "pauseDownload() sets in-memory flag (line 217). resumeDownload() only clears flag (line 220-222) but doesn't restart download. No persistent offset tracking. No WorkManager rescheduling."
    missing:
      - "Persistent download checkpoint (bytes written, URL, destination path) in Room or DataStore"
      - "resumeDownload() must restart the download loop with the saved Range offset"
      - "WorkManager Worker to survive process death and handle resumption on app restart"

  - truth: "User can search for .litertlm models from the litert-community and see only text-capable models (filtered from vision/speech)"
    status: partial
    reason: "Format-based search (filter=litertlm) works end-to-end: HuggingFaceApi -> Repository -> ViewModel. However, vision and speech models are not filtered out. The HuggingFaceModel DTO has pipelineTag field (line 15) but the ViewModel doesn't use it. A search for litertlm returns all litert-community models including vision/image-to-text ones, not just text-generation models."
    artifacts:
      - path: "app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt"
        issue: "search() method (line 61-89) doesn't filter results by pipelineTag. loadCompatibility() (line 192-207) filters by file extension only, not by model task type."
      - path: "app/src/main/java/com/warped/data/remote/dto/HuggingFaceDtos.kt"
        issue: "HuggingFaceModel has pipelineTag field (line 15) but it's never used in search result filtering."
    missing:
      - "Filter search results to exclude vision/speech pipelineTags (e.g., 'image-to-text', 'automatic-speech-recognition')"
      - "Only show models with pipelineTag='text-generation' or no pipelineTag (default for litert-community text models)"

deferred:
  - truth: "User can switch between GGUF and litertlm search in the UI"
    reason: "HuggingFaceViewModel.setActiveFormat() exists but no UI TabRow calls it. CONTEXT.md defers UI integration to Phase 9."
    addressed_in: "Phase 9 (UI Integration)"
    evidence: "Phase 9 success criteria: 'User sees separate GGUF and LiteRT-LM tabs on the Models screen, each showing only models of that format'"

  - truth: "File picker filters by .litertlm MIME type"
    reason: "ModelsScreen uses ActivityResultContracts.OpenDocument() with '*/*' (all files) rather than .litertlm-specific MIME types. Plan 08-03 explicitly notes this is a Phase 9 concern."
    addressed_in: "Phase 9 (UI Integration)"
    evidence: "Plan 08-03 Task 2 notes: 'the UI layer in Phase 9 will pass the MIME type filter for file picker'"

human_verification:
  - test: "Verify .litertlm search returns real models from Hugging Face API"
    expected: "Searching with filter=litertlm returns models from litert-community organization"
    why_human: "Requires live Hugging Face API call; can't verify programmatically without network + API access"

  - test: "Verify .litertlm download actually completes without crashing"
    expected: "Download a .litertlm model file, verify it's saved to modelsDir/ and appears in local models list with format 'LITERTLM'"
    why_human: "Requires actual download of multi-GB file, runtime behavior, and end-to-end verification that can't be simulated"

  - test: "Verify .litertlm import from device storage works end-to-end"
    expected: "Place a .litertlm file on device, import via Add Model > Import, verify it appears with format 'LITERTLM' and metadata (architecture='LiteRT-LM')"
    why_human: "Requires real Android device, file system access, and UI interaction flow"

  - test: "Verify download progress shows during .litertlm download"
    expected: "Progress bar updates in real-time as .litertlm model downloads"
    why_human: "Requires actual download + UI rendering observation; no foreground notification exists yet so this tests the in-app progress only"
---

# Phase 8: Model Acquisition Verification Report

**Phase Goal:** Users can discover, download, and import `.litertlm` models from Hugging Face's litert-community — extending the existing download infrastructure.
**Verified:** 2026-05-02T13:30:00Z
**Status:** gaps_found (3 roadmap criteria verified, 2 failed)
**Re-verification:** No — initial verification

## Goal Achievement

### Roadmap Success Criteria

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| SC1 | User can search for `.litertlm` models from litert-community and see only text-capable models | ⚠️ PARTIAL | Format search works (filter=litertlm via HuggingFaceApi → Repository → ViewModel). No filtering of vision/speech pipeline_tags. |
| SC2 | User can view model details including file size and format info | ✓ VERIFIED | `selectModel()` fetches `HuggingFaceModelDetail`, filters siblings by activeFormat extension, displays file sizes (sibling.size, sibling.lfs?.size). |
| SC3 | User sees foreground progress notification, downloads continue when backgrounded | ✗ FAILED | Zero foreground notification infrastructure. No WorkManager, no foreground service, no notification channel. |
| SC4 | User can pause `.litertlm` download, close app, return, resume without data loss | ✗ FAILED | In-memory pause flag only. No persistent checkpoint. Resume method is hollow (doesn't restart download loop). |
| SC5 | User can import local `.litertlm` from device storage via system file picker | ✓ VERIFIED | `ModelImportManager.importFromUri()` detects `.litertlm` extension, skips GGUF parse, saves modelFormat="LITERTLM". File picker exists in ModelsScreen. |

**Score:** 3/5 roadmap criteria verified (2 failed, both are blockers)

### Plan Must-Have Truths

| # | Plan | Truth | Status | Evidence |
|---|------|-------|--------|----------|
| 1 | 08-01 | LocalModel domain model exposes modelFormat field | ✓ VERIFIED | `LocalModel.kt:13` — `val modelFormat: String = "GGUF"` |
| 2 | 08-01 | Domain-to-entity mapper propagates modelFormat | ✓ VERIFIED | `LocalModelMappers.kt:14,26` — bidirectional propagation |
| 3 | 08-01 | All existing tests and compilation pass | ✓ VERIFIED | `compileDebugKotlin` BUILD SUCCESSFUL |
| 4 | 08-02 | Caller can search with format='litertlm' filter | ✓ VERIFIED | `HuggingFaceApi.filter` default "gguf", overridable; `Repository.searchModels(format=)` passes it through |
| 5 | 08-02 | Search results return litert-community models | ✓ VERIFIED | API + Repository + ViewModel chain passes `filter = format` to HuggingFace API |
| 6 | 08-02 | Backward compatible — GGUF searches continue | ✓ VERIFIED | Default `format="gguf"` in repository; existing callers compile unchanged |
| 7 | 08-03 | Downloaded .litertlm saved with modelFormat='LITERTLM' | ✓ VERIFIED | `ModelDownloadManager.kt:198` — `modelFormat = if (isLitertlm) "LITERTLM" else "GGUF"` |
| 8 | 08-03 | Downloaded .litertlm skip GGUF metadata parse | ✓ VERIFIED | `ModelDownloadManager.kt:181-189` — `if (!isLitertlm)` guard around `GgufMetadataParser.parse()` |
| 9 | 08-03 | File picker accepts .litertlm alongside .gguf | ⚠️ PARTIAL | Uses `"*/*"` — accepts everything but doesn't restrict. Deferred to Phase 9. |
| 10 | 08-03 | Imported .litertlm get modelFormat='LITERTLM' | ✓ VERIFIED | `ModelImportManager.kt:65` — `modelFormat = if (isLitertlm) "LITERTLM" else "GGUF"` |
| 11 | 08-03 | User can search for litertlm via HuggingFaceViewModel | ✓ VERIFIED | `search()` reads `activeFormat` and passes to repository; sibling filtering by extension |
| 12 | 08-03 | Model detail filters siblings by .litertlm extension | ✓ VERIFIED | `selectModel()` line 96-98 — `filter { it.rfilename.endsWith(extension, ignoreCase = true) }` |
| 13 | 08-03 | Downloads show progress and support pause/cancel | ⚠️ PARTIAL | In-app progress (DownloadState StateFlow) works. Pause flag exists. No foreground notification or persistent state. |
| 14 | 08-03 | HuggingFaceUiState tracks activeFormat | ✓ VERIFIED | `HuggingFaceUiState.kt:24` — `val activeFormat: String = "gguf"` |

**Plan must-haves score:** 12/14 verified (2 partial/warning)

### Deferred Items

Items not yet met but explicitly addressed in later milestone phases.

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | UI toggle between GGUF and litertlm format tabs | Phase 9 | Phase 9 SC1: "User sees separate GGUF and LiteRT-LM tabs on the Models screen" |
| 2 | File picker MIME type filter for .litertlm | Phase 9 | Plan 08-03 T2 note: "the UI layer in Phase 9 will pass the MIME type filter for file picker" |

### Required Artifacts

| Artifact | Expected | Level 2 | Level 3 | Level 4 | Status |
|----------|----------|---------|---------|---------|--------|
| `LocalModel.kt` | Domain model with modelFormat field | ✓ 15 lines, field present | ✓ Referenced by all managers | N/A (data class) | ✓ VERIFIED |
| `LocalModelMappers.kt` | Bidirectional modelFormat propagation | ✓ Both functions updated | ✓ Used by LocalModelRepository | N/A (mapper) | ✓ VERIFIED |
| `HuggingFaceApi.kt` | filter param accepts "litertlm" | ✓ Already had filter param | ✓ Called by RepositoryImpl | ✓ Real HF API call | ✓ VERIFIED |
| `HuggingFaceRepository.kt` | searchModels(query, format, limit) | ✓ 9 lines, signature correct | ✓ Called by ViewModel | ✓ Delegates to API | ✓ VERIFIED |
| `HuggingFaceRepositoryImpl.kt` | Passes format as filter to API | ✓ 60 lines, logic present | ✓ Wired via Retrofit call | ✓ Real API call (line 37) | ✓ VERIFIED |
| `ModelDownloadManager.kt` | .litertlm format detection + skip GGUF parse | ✓ 249 lines, detection at L179-200 | ✓ Called by ViewModel.downloadFile | ✓ Saves to Room (L202) | ✓ VERIFIED |
| `ModelImportManager.kt` | .litertlm extension detection + format assignment | ✓ 93 lines, detection at L47-67 | ✓ Called by ModelsViewModel.importModel | ✓ Saves to Room (L69) | ✓ VERIFIED |
| `HuggingFaceViewModel.kt` | Format-aware search, detail filtering, setActiveFormat | ✓ 212 lines, format-aware throughout | ✓ Wired to Repository + DownloadManager | ✓ Real data flow (search, detail, download) | ✓ VERIFIED |
| `HuggingFaceUiState.kt` | activeFormat state tracking | ✓ 25 lines, field at L24 | ✓ Consumed by ViewModel only | N/A (state holder) | ✓ VERIFIED |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `HuggingFaceRepositoryImpl.searchModels()` | `HuggingFaceApi.searchModels(filter=format)` | Retrofit call | ✓ WIRED | Line 37: `api.searchModels(query = query, filter = format, limit = limit)` |
| `HuggingFaceViewModel.search()` | `HuggingFaceRepository.searchModels(query, format)` | `activeFormat` state | ✓ WIRED | Line 67-70: reads `activeFormat`, passes as `format` parameter |
| `HuggingFaceViewModel.selectModel()` | `.litertlm`/`.gguf` sibling filter | Extension derived from `activeFormat` | ✓ WIRED | Line 96-98: `".${_uiState.value.activeFormat}"` |
| `ModelDownloadManager` → `LocalModel.modelFormat` | Format detection from file extension | `localFileName.endsWith(".litertlm")` | ✓ WIRED | Line 179-200: sets modelFormat = "LITERTLM"/"GGUF" |
| `ModelImportManager` → `LocalModel.modelFormat` | Format detection from file extension | `fileName.endsWith(".litertlm")` | ✓ WIRED | Line 47-67: sets modelFormat = "LITERTLM"/"GGUF" |
| `HuggingFaceViewModel.downloadFile()` | `ModelDownloadManager.startDownload()` | Direct method call | ✓ WIRED | Line 137-142: passes modelId, fileName, fileUrl, fileSize |
| `ModelsViewModel.importModel()` | `ModelImportManager.importFromUri()` | Direct method call | ✓ WIRED | ModelsViewModel.kt:66 |
| `HuggingFaceViewModel` → DownloadState tracking | `downloadManager.downloadStates` Flow | `init {}` collector | ✓ WIRED | Line 34-53: updates UiState from download progress |
| `HuggingFaceViewModel.pauseDownload()` | `ModelDownloadManager.cancelDownload()` | ⚠️ MISWIRED | ⚠️ MISWIRED | Line 147: calls `cancelDownload()` instead of `pauseDownload()` — marks download as cancelled with error, not paused |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `HuggingFaceViewModel.search()` | `activeFormat` → `huggingFaceRepository.searchModels(query, format=activeFormat)` | `_uiState.value.activeFormat` (default "gguf") | ✓ Real — API call via Retrofit to `huggingface.co/api/models` | ✓ FLOWING |
| `HuggingFaceViewModel.selectModel()` | `detail.siblings` filtered by extension | `huggingFaceRepository.getModelDetail(modelId)` → `HuggingFaceModelDetail.siblings` | ✓ Real — API call returning sibling list with sizes | ✓ FLOWING |
| `ModelDownloadManager` download completion | `localFileName.endsWith(".litertlm")` → `LocalModel(modelFormat="LITERTLM")` | File downloaded from HF → `GgufMetadataParser.parse()` for GGUF, skipped for .litertlm | ✓ Real — saves to `localModelRepository.saveModel()` | ✓ FLOWING |
| `ModelImportManager` import completion | `fileName.endsWith(".litertlm")` → `LocalModel(modelFormat="LITERTLM")` | Content resolver input stream → file copy → metadata extraction | ✓ Real — saves to `localModelRepository.saveModel()` | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Compilation | `./gradlew :app:compileDebugKotlin` | BUILD SUCCESSFUL in 642ms | ✓ PASS |
| modelFormat in LocalModel | `grep "modelFormat" LocalModel.kt` | 1 match (line 13) | ✓ PASS |
| modelFormat in mappers | `grep "modelFormat" LocalModelMappers.kt` | 2 matches (lines 14, 26) | ✓ PASS |
| format in Repository | `grep "format" HuggingFaceRepository.kt` | `format: String = "gguf"` | ✓ PASS |
| format → filter wiring | `grep "filter.*format" HuggingFaceRepositoryImpl.kt` | `filter = format` at line 37 | ✓ PASS |
| .litertlm detection in download | `grep "isLitertlm\|LITERTLM" ModelDownloadManager.kt` | 8 matches — full detection + save logic | ✓ PASS |
| .litertlm detection in import | `grep "isLitertlm\|LITERTLM" ModelImportManager.kt` | 6 matches — full detection + save logic | ✓ PASS |
| activeFormat in UiState | `grep "activeFormat" HuggingFaceUiState.kt` | 1 match (line 24) | ✓ PASS |
| activeFormat in ViewModel | `grep "activeFormat\|setActiveFormat" HuggingFaceViewModel.kt` | 5 matches — read/write/consume | ✓ PASS |
| No foreground notification code | `grep "setForeground\|WorkManager\|Worker"` app-wide | 0 results — missing entirely | ✗ FAIL |

### Requirements Coverage

| Req ID | Description | Status | Evidence |
|--------|-------------|--------|----------|
| ACQ-06 | User can search Hugging Face for .litertlm models filtered by litert-community org | ✓ SATISFIED | `HuggingFaceApi.searchModels(filter="litertlm")` → `HuggingFaceRepository.searchModels(format="litertlm")` → `HuggingFaceViewModel.search()` passes `activeFormat` |
| ACQ-07 | User can view .litertlm model details including file size and format info | ✓ SATISFIED | `selectModel()` fetches `HuggingFaceModelDetail`, `HuggingFaceModel` has `pipelineTag`, `HuggingFaceSibling` has `size` and `lfs.size` |
| ACQ-08 | User can download .litertlm model files with foreground progress notification | ✗ BLOCKED | Download works but no foreground notification exists. No WorkManager/foreground service. See Gap #1. |
| ACQ-09 | User can pause and resume .litertlm model downloads | ⚠️ PARTIAL | In-app pause via flag check works. Can't resume after app close — no persistent state. `resumeDownload()` is hollow. See Gap #2. |
| ACQ-10 | User can import local .litertlm files from device storage | ✓ SATISFIED | `ModelImportManager.importFromUri()` detects .litertlm extension, skips GGUF parse, sets `modelFormat="LITERTLM"`. `ModelsScreen` file picker exists. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `HuggingFaceViewModel.kt` | 147 | `pauseDownload()` calls `downloadManager.cancelDownload()` instead of `pauseDownload()` | ⚠️ Warning | User cancellation sets error="Cancelled" in download manager instead of proper pause state. Pre-existing in GGUF pipeline, not introduced by Phase 8. |
| `ModelDownloadManager.kt` | 220-222 | `resumeDownload()` only clears `isPaused` flag, doesn't restart the download loop | 🛑 Blocker | No way to actually resume a paused download — flag is cleared but no new download is started. Contributes to SC4 failure. |

### Human Verification Required

1. **Live Hugging Face API search for .litertlm**
   **Test:** Search for models on a real device with `activeFormat = "litertlm"`
   **Expected:** Real litert-community models appear in search results
   **Why human:** Requires live network + HF API access

2. **End-to-end .litertlm download**
   **Test:** Download a .litertlm model, verify it saves to `filesDir/models/` and appears in local models with `modelFormat = "LITERTLM"`
   **Expected:** Model downloads, progress shows in-app, saved with correct format metadata (architecture="LiteRT-LM", quantization="N/A")
   **Why human:** Requires actual multi-GB download, runtime behavior, file system verification

3. **End-to-end .litertlm import**
   **Test:** Place .litertlm file on device, import via Add Model → Import from device
   **Expected:** Model imports successfully, appears with format "LITERTLM", metadata defaults applied correctly
   **Why human:** Requires real device, file system access, UI flow

4. **Download progress display during .litertlm download**
   **Test:** Start a .litertlm download and observe progress
   **Expected:** Progress bar updates smoothly, percentage increments, download completes
   **Why human:** Requires actual download with network latency + UI observation

### Gaps Summary

The `.litertlm` acquisition pipeline is solid at the data and logic layers:
- **Format detection** (extension-based) works in both download and import paths
- **Metadata handling** appropriately skips GGUF parsing for `.litertlm` files
- **Search infrastructure** supports format filtering end-to-end from API to ViewModel
- **Data persistence** correctly stores `modelFormat = "LITERTLM"` in Room

However, two **roadmap success criteria are blocked** by missing download infrastructure:

1. **No foreground download notifications (SC3):** Zero WorkManager or foreground service code exists anywhere in the project — not for GGUF downloads, not for `.litertlm` downloads. The CONTEXT.md states "existing download infrastructure" has foreground notifications, but codebase evidence contradicts this — downloads use a volatile `CoroutineScope` with no Android lifecycle integration. Phase 3 (v1.0 Model Acquisition) had the same scope item ("foreground download notifications") but the infrastructure was never built.

2. **No persistent pause/resume (SC4):** The `pauseDownload()` function sets an in-memory flag that the download loop checks. But `resumeDownload()` is hollow — it clears the flag but never restarts the download. There's no checkpoint persistence, no WorkManager to survive process death, and no way to resume after app restart. The Range header (for byte-offset resume) exists but is useless without persistent state tracking.

3. **No text-capable model filtering (SC1 — partial):** Search correctly filters by format (`filter=litertlm`) but doesn't exclude vision/speech models. The `pipelineTag` field in the DTO is available but unused. This is a small filter to add but impacts user experience.

**Phase 9 (UI Integration) won't fix these gaps** — its scope is UI tabs, format badges, and backend status indicators. Foreground notifications and persistent download state need dedicated infrastructure work that wasn't scoped into any current or planned phase.

---

_Verified: 2026-05-02T13:30:00Z_
_Verifier: the agent (gsd-verifier)_
