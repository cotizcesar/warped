---
phase: 63-tavily-removal-ddg-only-search
plan: "01"
subsystem: search-grounding
tags: [tavily-removal, ddg-only, tracer, di, grounding]
requires: []
provides:
  - standalone DDG-only search producer
  - keyless SearchOutcome contract (Grounded/ModelOnly)
  - Tavily-free DI graph
affects: [63-02-consumer-rename, settings-tavily-removal, keystore-cleanup]
tech-stack:
  added: []
  patterns:
    - keyless single-producer search behind SearchOutcome sealed interface
    - no-op empty-images fusion for image-intent turns
key-files:
  created: []
  modified:
    - app/src/main/java/com/warped/data/grounding/DuckDuckGoSearchRepository.kt
    - app/src/main/java/com/warped/data/agentic/LocalToolLoop.kt
    - app/src/main/java/com/warped/data/remote/provider/CompatToolLoop.kt
    - app/src/main/java/com/warped/data/grounding/GroundedImages.kt
    - app/src/main/java/com/warped/di/NetworkModule.kt
  deleted:
    - app/src/main/java/com/warped/data/remote/api/TavilyApi.kt
    - app/src/main/java/com/warped/data/remote/dto/TavilyDtos.kt
    - app/src/main/java/com/warped/data/grounding/TavilySearchRepository.kt
decisions:
  - Renamed outcome to SearchOutcome (not kept as TavilySearchOutcome) so tracer files are grep-clean per SEARCH-01
  - KDoc prose mentioning the deleted producer reworded to keep zero case-insensitive tavily tokens in tracer files
  - includeImages param kept (ignored, empty images fused) so call sites compile unchanged
metrics:
  duration: ~25min
  completed: 2026-10-02
---

# Phase 63 Plan 01: DDG-only Tracer Slice Summary

Standalone keyless DuckDuckGo search producer behind a two-variant `SearchOutcome` contract; Tavily client, DTO, and producer files deleted and DI rebound.

## Completed Tasks

| Task | Name | Commit | Files |
| ---- | ---- | ------ | ----- |
| 1 | Delete Tavily files and rebind NetworkModule DI | 494856d2 | TavilyApi.kt, TavilyDtos.kt, TavilySearchRepository.kt (deleted); NetworkModule.kt |
| 2 | Make DuckDuckGoSearchRepository standalone with keyless outcome | 747d3ce3 | DuckDuckGoSearchRepository.kt, LocalToolLoop.kt, CompatToolLoop.kt, GroundedImages.kt |

## Key Decisions

- **Outcome rename branch:** declared `sealed interface SearchOutcome` with only `Grounded` and `ModelOnly` in `DuckDuckGoSearchRepository.kt`; deleted `MissingKey`/`InvalidKey`/`UsageLimit`. The old name contains "Tavily" and could never satisfy SEARCH-01 grep-clean.
- **Fusion decision:** `fuseImages` deleted with its file; single fusion path is DDG `fuse()`; image-intent turns yield an empty images list with text grounding preserved.
- **KDoc hygiene:** two prose mentions of the deleted producer (DDG class KDoc, companion comment) reworded — the Task 2 verify is a case-insensitive `tavily` grep, so even comments must be token-clean.

## Deviations from Plan

None - plan executed exactly as written.

## Verification

- `test ! -f` on all three deleted files → `DELETES_OK`
- `grep -rni "tavily"` across the five tracer files → zero matches (exit 1)
- `grep "TavilySearchOutcome"` across the five tracer files → zero matches (exit 1)
- Companion defines `DEFAULT_MAX_RESULTS=5`, `MAX_RESULTS_CAP=10`, `MAX_QUERY_CHARS=500`, `MAX_IMAGES=10`
- `CompatToolLoop` calls `ddg.search` with `DuckDuckGoSearchRepository.DEFAULT_MAX_RESULTS`
- Full-project compile intentionally deferred to plan 63-02: remaining consumers (ChatViewModel, providers, LiteRTLmProvider, tests) still reference the old outcome name and will be renamed there.

## Threat Flags

None — no new parsing introduced (`parseResults`/`resolveResultUrl`/`fuse()` untouched); the Bearer-key header surface was deleted with `TavilyApi`; no new dependencies.

## Known Stubs

None.

## Self-Check: PASSED

- FOUND: modified DDG/LocalToolLoop/CompatToolLoop/GroundedImages/NetworkModule files on disk
- FOUND: commits 494856d2 and 747d3ce3 in git log
- Deleted files confirmed absent from disk
