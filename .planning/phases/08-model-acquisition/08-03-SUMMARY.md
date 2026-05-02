---
phase: 08-model-acquisition
plan: 03
subsystem: model-acquisition
tags: [litertlm, gguf, huggingface, viewmodel, format-detection, kotlin, android]

# Dependency graph
requires:
  - phase: 08-01
    provides: "LocalModel.modelFormat field, DB migration for format column"
  - phase: 08-02
    provides: "HuggingFaceRepository.searchModels(query, format) with format parameter"
provides:
  - "Format-aware download completion in ModelDownloadManager — skips GGUF parse for .litertlm"
  - "Format-aware import in ModelImportManager — detects .litertlm extension"
  - "activeFormat state tracking in HuggingFaceUiState for Phase 9 UI"
  - "Format-aware search, sibling filtering, and download orchestration in HuggingFaceViewModel"
affects:
  - "09-ui-models-screen (TabRow format toggle, litertlm search tab)"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "File extension-based format detection (not magic bytes) per D-03 context decision"
    - "Format-aware ViewModel state with reactive search re-triggering on format switch"
    - "Dual-format LocalModel construction with format-specific metadata defaults"

key-files:
  modified:
    - "app/src/main/java/com/warped/data/local/download/ModelDownloadManager.kt"
    - "app/src/main/java/com/warped/data/local/inference/ModelImportManager.kt"
    - "app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt"
    - "app/src/main/java/com/warped/ui/huggingface/HuggingFaceUiState.kt"

key-decisions:
  - "D-08-03a: Format detection by file extension (not magic bytes) — file extension is canonical signal per D-03 context decision; LiteRT-LM binary parser does not exist"
  - "D-08-03b: .litertlm metadata defaults — quantization='N/A', architecture='LiteRT-LM', parameterCount='Unknown' — parameter count extraction requires LiteRT-LM introspection (deferred to Phase 10)"
  - "D-08-03c: formatFiles replaces ggufFiles throughout ViewModel — no backward-compat break since default activeFormat='gguf' preserves existing behavior"
  - "D-08-03d: setActiveFormat() clears search results and selected model, then auto-re-searches — provides Phase 9 TabRow integration point"

patterns-established:
  - "Format-specific metadata construction: if (isLitertlm) 'N/A' else modelMetadata.quantization — same pattern used in both download and import managers"
  - "activeFormat-driven filtering: extension derived from _uiState.value.activeFormat — centralized format signal for all sibling/compatibility operations"
  - "Fallback model name from filename: fileName.removeSuffix('.gguf').removeSuffix('.litertlm') — ensures display name even when metadata parsing is skipped"

requirements-completed: [ACQ-06, ACQ-07, ACQ-08, ACQ-09, ACQ-10]

# Metrics
duration: 5min
completed: 2026-05-02
---

# Phase 08 Plan 03: Download/Import/ViewModel Format Awareness Summary

**End-to-end .litertlm acquisition pipeline: download manager skips GGUF parse for .litertlm, import manager detects extension, HuggingFace ViewModel gains activeFormat state for format-switching search and sibling filtering**

## Performance

- **Duration:** 5 min (301 seconds)
- **Started:** 2026-05-02T18:18:28Z
- **Completed:** 2026-05-02T18:23:29Z
- **Tasks:** 3
- **Files modified:** 4

## Accomplishments

- ModelDownloadManager detects `.litertlm` extension on download completion, skips GGUF metadata parse (non-GGUF magic bytes would crash), saves `modelFormat = "LITERTLM"` with `architecture = "LiteRT-LM"`, `quantization = "N/A"`, `parameterCount = "Unknown"`
- ModelImportManager detects `.litertlm` from imported file's extension, skips GGUF parse, sets `modelFormat = "LITERTLM"` with fallback model name from filename
- HuggingFaceViewModel passes `activeFormat` to repository search, filters model detail siblings by active format extension, exposes `setActiveFormat()` for Phase 9 TabRow toggle
- HuggingFaceUiState tracks `activeFormat` (default `"gguf"`) for Phase 9 UI consumption
- All existing GGUF search/download/import behavior preserved — format detection is additive, not breaking

## Task Commits

Each task was committed atomically:

1. **Task 1: Detect .litertlm format in ModelDownloadManager** - `3dd61a9` (feat)
2. **Task 2: Add .litertlm support to ModelImportManager** - `12ed895` (feat)
3. **Task 3: Format-aware search in HuggingFaceViewModel + UiState** - `24c8f78` (feat)

## Files Created/Modified

- `app/src/main/java/com/warped/data/local/download/ModelDownloadManager.kt` - Format-aware download completion: detects `.litertlm`, skips GGUF parse, sets correct `modelFormat`
- `app/src/main/java/com/warped/data/local/inference/ModelImportManager.kt` - Format-aware import: extension detection, filename blank-guard, metadata defaults for `.litertlm`
- `app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt` - Format-aware search/detail/loadCompatibility: `activeFormat` → repository, sibling filtering, `setActiveFormat()` method
- `app/src/main/java/com/warped/ui/huggingface/HuggingFaceUiState.kt` - Added `activeFormat: String = "gguf"` state field

## Decisions Made

- **File extension as format signal:** Both managers use `fileName.endsWith(".litertlm", ignoreCase = true)` per D-03 context decision. No magic-byte detection needed — `.litertlm` is the canonical format indicator.
- **Metadata defaults:** `quantization = "N/A"` (LiteRT-LM binary doesn't expose quantization via GGUF parser), `architecture = "LiteRT-LM"` (canonical engine name), `parameterCount = "Unknown"` (requires LiteRT-LM model introspection — deferred to Phase 10).
- **activeFormat-driven ViewModel:** All three format-sensitive operations (search, selectModel, loadCompatibility) derive the extension from `_uiState.value.activeFormat`. Default `"gguf"` ensures zero behavioral change for existing flows.
- **setActiveFormat clears context:** Switching format resets search results and selected model before re-searching — prevents stale `.gguf` siblings from showing in `.litertlm` detail view.

## Deviations from Plan

None — plan executed exactly as written. All three tasks matched the plan's action blocks precisely.

## Issues Encountered

- **Compile error in `setActiveFormat()`:** Initially referenced `searchQuery` directly (unresolved property), corrected to `_uiState.value.searchQuery`. Fixed within Task 3 before commit.

## User Setup Required

None — no external service configuration required.

## Threat Flags

None — all threat vectors documented in plan's threat model (T-08-03 file extension spoofing, T-08-04 large file import, T-08-05 search query disclosure) remain at accepted dispositions. No new trust boundaries introduced.

## Next Phase Readiness

- Phase 9 (UI Models Screen) can now consume `HuggingFaceViewModel.setActiveFormat()` and `HuggingFaceUiState.activeFormat` for format-switching TabRow
- `.litertlm` acquisition pipeline is fully wired: search → download/import → Room with correct `modelFormat`
- Parameter count extraction for `.litertlm` models (`"Unknown"` default) is deferred to Phase 10 when LiteRT-LM model introspection is implemented

---
*Phase: 08-model-acquisition*
*Completed: 2026-05-02*
