---
phase: quick-260430-qv6
plan: "01"
subsystem: ui
tags: [huggingface, gguf, lfs, kotlin, compose]

# Dependency graph
requires: []
provides:
  - "GGUF sibling filtering by extension only (no size-gate), enabling LFS-tracked models to appear in model detail"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns: []

key-files:
  modified:
    - app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt

key-decisions:
  - "Remove size-gate from GGUF filters rather than attempting to fix size metadata — the existing effectiveSize fallback pattern at lines 76, 85, and 185 already handles 0/null gracefully"

patterns-established: []

requirements-completed: []

# Metrics
duration: 2min
completed: 2026-05-01
---

# Quick Fix: Remove size-gate from GGUF sibling filters in HuggingFaceViewModel

**Fixed: LFS-tracked GGUF models (size=0, no LFS info) now appear in model detail screen instead of being filtered out.**

## Performance

- **Duration:** ~2 min
- **Started:** 2026-05-01T00:34:00Z
- **Completed:** 2026-05-01T00:36:46Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments

- Removed `&& (it.size > 0 || (it.lfs?.size ?: 0) > 0)` condition from `selectModel()` GGUF filter (line 74)
- Removed `&& (it.size > 0 || (it.lfs?.size ?: 0) > 0)` condition from `loadCompatibility()` GGUF filter (line 184)
- LFS-tracked `.gguf` files now pass through both filters regardless of size metadata availability
- Existing `effectiveSize` fallback (`sibling.size.takeIf { it > 0 } ?: sibling.lfs?.size ?: 0L`) handles 0/null sizes at lines 76, 85, 185

## Task Commits

Each task was committed atomically:

1. **Task: Remove size-gate from GGUF sibling filters** - `2def465` (fix)

## Files Modified

- `app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt` - Removed size condition from both GGUF filter predicates; all other logic unchanged

## Decisions Made

None - followed plan as specified. The fix is minimal and surgical: two predicate simplifications that let the existing fallback pattern handle edge cases.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- **`rg` (ripgrep) not available in environment**: Used `grep` tool for automated verification instead. All three checks passed (0 matches for size-gated filters, exactly 2 `.endsWith(".gguf"` occurrences).

## User Setup Required

None - no external service configuration required.

---

*Quick Task: 260430-qv6-01*
*Completed: 2026-05-01*
