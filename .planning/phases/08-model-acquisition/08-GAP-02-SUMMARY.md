---
phase: 08-model-acquisition
plan: GAP-02
subsystem: model-acquisition
tags: [gap-closure, filtering, pipeline-tag, litertlm, search-results]
requires: []
provides: [ACQ-06-client-filter]
affects: [HuggingFaceViewModel.search(), HuggingFaceUiState.searchResults]
tech-stack:
  added: []
  patterns: [blacklist-filtering, format-gated-logic]
key-files:
  created: []
  modified:
    - app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt
decisions:
  - D-GAP-02-01: Blacklist approach for pipelineTag filtering — exclude known vision/speech tags, include everything else (safer than whitelist for new text-only tags)
  - D-GAP-02-02: Filter gated on activeFormat == "litertlm" — GGUF search results are unaffected
  - D-GAP-02-03: Blank/empty pipelineTag passes through — litert-community text models commonly have no pipeline_tag set
  - D-GAP-02-04: Filter applied before compatibility checking and sorting for performance
metrics:
  duration: 576s (GAP-01 + GAP-02 combined)
  tasks: 1
  files: 1
  completed: "2026-05-02T19:03:41Z"
---

# Phase 8 Plan GAP-02: Text-Only Filtering Summary

**One-liner:** Client-side pipelineTag blacklist filter in HuggingFaceViewModel excludes vision/speech models from litertlm search results while preserving GGUF search behavior.

## What Was Built

Added client-side filtering to `HuggingFaceViewModel.search()` that excludes models with vision or speech `pipeline_tag` values from litert-community search results. The filter is applied only when `activeFormat == "litertlm"`, leaving GGUF search results completely unaffected.

### Implementation Details

1. **`EXCLUDED_PIPELINE_TAGS` blacklist** — 15 tags covering image-to-text, automatic-speech-recognition, text-to-speech, image-classification, object-detection, image-segmentation, audio-classification, image-text-to-text, visual-question-answering, text-to-image, zero-shot-image-classification, zero-shot-object-detection, image-feature-extraction, video-classification, and depth-estimation.

2. **Filter logic** — `model.pipelineTag.isBlank() || model.pipelineTag !in EXCLUDED_PIPELINE_TAGS` ensures:
   - Blank pipelineTags pass through (most litert-community text models)
   - Known vision/speech tags are excluded
   - Unknown future text-only tags pass through

3. **Performance ordering** — Filter → Compatibility check → Sort. Models excluded by filter don't consume compatibility check time.

### Task Summary

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Filter search results by pipelineTag | `932a2fc` | HuggingFaceViewModel.kt |

## Verification

- `./gradlew :app:compileDebugKotlin`: BUILD SUCCESSFUL
- `grep -c "pipelineTag" HuggingFaceViewModel.kt` → 2 ✓ (≥2)
- `grep -c "EXCLUDED_PIPELINE_TAGS" HuggingFaceViewModel.kt` → 2 ✓ (≥2)
- `grep -c "activeFormat" HuggingFaceViewModel.kt` → 6 ✓ (≥6)
- Models with blank `pipelineTag` are included (isBlank check)
- GGUF search path is gated on `activeFormat != "litertlm"` and passes through unfiltered

## Deviations from Plan

None — plan executed exactly as written.

## Threat Flags

None — filtering is purely subtractive (removing models from results). No new data flows, endpoints, or auth paths introduced.

## Self-Check: PASSED

- [x] `app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt` exists
- [x] Commit `932a2fc` exists in git log
