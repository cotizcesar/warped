---
phase: 24-search-simplification
plan: 01
subsystem: ui
tags: [compose, huggingface, litertlm, search, cleanup]

# Dependency graph
requires:
  - phase: 23-gguf-removal
    provides: GGUF-free codebase (no llama.cpp, JNI, or GGUF quantization needed)
provides:
  - Tab-free single-search-bar HuggingFace screen
  - Staff Picks API/repository/DTOs fully deleted
  - Hardcoded library=litert in search — no format switching
  - Simplified FormatBadge always showing "LiteRT-LM" in green
  - No GgufFileDetail, QuantizationBadge, or format plumbing in HuggingFace UI
affects: [search, ui, hugging-face, model-discovery]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Single-search-bar pattern with no tabbed browsing for model discovery"
    - "Hardcoded library parameter at repository implementation level — callers are format-agnostic"

key-files:
  created: []
  modified:
    - app/src/main/java/com/warped/data/remote/dto/HuggingFaceDtos.kt
    - app/src/main/java/com/warped/data/remote/api/HuggingFaceApi.kt
    - app/src/main/java/com/warped/domain/repository/HuggingFaceRepository.kt
    - app/src/main/java/com/warped/data/repository/HuggingFaceRepositoryImpl.kt
    - app/src/main/java/com/warped/ui/huggingface/HuggingFaceUiState.kt
    - app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt
    - app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt
    - app/src/main/java/com/warped/ui/models/ModelsScreen.kt

key-decisions:
  - "Hardcoded library=litert at HuggingFaceRepositoryImpl level — callers pass only query + author"
  - "Removed library default from HuggingFaceApi.searchModels() — all callers must pass explicit library value"
  - "FormatBadge simplified to parameterless composable — always green 0xFF4CAF50 for LiteRT-LM"

patterns-established:
  - "Format-agnostic search interface: repository contract omits format/library, implementation hardcodes it"

requirements-completed: [SRCH-01, SRCH-02, SRCH-03, SRCH-04]

# Metrics
duration: 14 min
completed: 2026-05-10
---

# Phase 24 Plan 01: Search Simplification Summary

**Tab-free single-search-bar HuggingFace screen with hardcoded library=litert, Staff Picks fully deleted, and no GGUF format plumbing**

## Performance

- **Duration:** 14 min
- **Started:** 2026-05-10T04:14:44Z
- **Completed:** 2026-05-10T04:28:56Z
- **Tasks:** 3 (+ 1 pre-existing fix)
- **Files modified:** 8

## Accomplishments

- Tab infrastructure (PrimaryTabRow, Tab, format list) completely removed from HuggingFaceScreen — single OutlinedTextField search bar only
- Staff Picks DTOs (HuggingFaceCollection, HuggingFaceCollectionItem), API endpoint (getCollection), repository method (getCollectionModels), and ViewModel method (loadStaffPicks) deleted entirely
- searchModels() signature simplified across all layers — `format` parameter removed, `library = "litert"` hardcoded at HuggingFaceRepositoryImpl
- GgufFileDetail data class deleted from UiState; ggufFileDetails and activeFormat fields removed
- QuantizationBadge composable deleted — no GGUF quantization display in model detail
- FormatBadge simplified to parameterless composable always rendering "LiteRT-LM" in green (#4CAF50)
- setActiveFormat() method removed from ViewModel — no format switching capability remains

## Task Commits

Each task was committed atomically:

1. **Task 1: Delete Staff Picks DTOs + API endpoint + Repository methods; hardcode litert in search** — `bd2a629` (feat)
2. **Task 2: Simplify UiState and ViewModel** — `f20414a` (feat)
3. **Task 3: Strip tabs, activeFormat plumbing, QuantizationBadge, and GgufFileDetail from HuggingFaceScreen** — `3e326e2` (feat)
4. **Fix: add missing stringResource and R imports to ModelsScreen** — `d6cb8c1` (fix)

**Plan metadata:** (pending final commit)

## Files Created/Modified

- `app/src/main/java/com/warped/data/remote/dto/HuggingFaceDtos.kt` — Deleted HuggingFaceCollection + HuggingFaceCollectionItem (lines 80–96)
- `app/src/main/java/com/warped/data/remote/api/HuggingFaceApi.kt` — Deleted getCollection() endpoint; removed library default
- `app/src/main/java/com/warped/domain/repository/HuggingFaceRepository.kt` — Removed format parameter from searchModels(); deleted getCollectionModels()
- `app/src/main/java/com/warped/data/repository/HuggingFaceRepositoryImpl.kt` — Hardcoded `library = "litert"`; deleted getCollectionModels() override
- `app/src/main/java/com/warped/ui/huggingface/HuggingFaceUiState.kt` — Deleted GgufFileDetail data class; removed ggufFileDetails and activeFormat fields
- `app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt` — Deleted loadStaffPicks(), setActiveFormat(); simplified search() and selectModel(); updated clearDetail()
- `app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt` — Removed PrimaryTabRow/Tab, activeFormat plumbing, QuantizationBadge, GgufFileDetail params; simplified FormatBadge
- `app/src/main/java/com/warped/ui/models/ModelsScreen.kt` — Added missing `stringResource` and `R` imports (pre-existing compilation blocker)

## Decisions Made

- Hardcoded `library = "litert"` at the repository implementation level rather than the API interface level, keeping the API contract flexible while the implementation enforces the constraint
- Removed the `library` default value from HuggingFaceApi.searchModels() so every caller explicitly passes the library — discovered it during Task 1 execution
- FormatBadge simplified to parameterless composable rather than keeping the parameterized version with a fixed default — cleaner API, no dead parameter

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Task 1 DTO deletion broke ViewModel compilation — cascaded Task 2+3 changes**
- **Found during:** Task 1 (compilation verification)
- **Issue:** Deleting HuggingFaceCollectionItem DTO caused ViewModel.loadStaffPicks() to fail compilation (referenced deleted type fields). Extracting the fix revealed a cascade: ViewModel → UiState → Screen all needed simultaneous changes.
- **Fix:** Applied Task 2 (UiState + ViewModel) and Task 3 (Screen) changes in the same execution pass to unblock compilation. All tasks were committed individually by their file groups despite being applied together.
- **Files modified:** HuggingFaceUiState.kt, HuggingFaceViewModel.kt, HuggingFaceScreen.kt
- **Verification:** `./gradlew :app:compileDebugKotlin` passes; all 16 acceptance criteria green
- **Committed in:** f20414a (Task 2), 3e326e2 (Task 3)

**2. [Rule 3 - Blocking] Pre-existing missing imports in ModelsScreen.kt blocked compilation**
- **Found during:** Task 1 (compilation verification)
- **Issue:** `stringResource()` at ModelsScreen.kt:147 was missing `import androidx.compose.ui.res.stringResource` and `import com.warped.R` — this pre-existing error blocked `:app:compileDebugKotlin` for the entire project.
- **Fix:** Added both missing imports to ModelsScreen.kt
- **Files modified:** app/src/main/java/com/warped/ui/models/ModelsScreen.kt
- **Verification:** Compilation passes; line 147 resolves correctly
- **Committed in:** d6cb8c1

---

**Total deviations:** 2 auto-fixed (2 blocking)
**Impact on plan:** Both auto-fixes necessary for compilation. Task boundaries were treated as logical (per-plan) rather than sequential due to type dependency cascades. No scope creep — all changes were specified in the plan.

## Issues Encountered

- The plan assumed tasks could compile independently, but DTO deletion (Task 1) immediately broke the ViewModel (Task 2) which broke the Screen (Task 3). Applied all changes together and committed per-file-group to maintain logical task separation.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- HuggingFace search is now a single search bar with hardcoded litert library — ready for Phase 25 (Bug Fixes)
- No residual Staff Picks code anywhere in the codebase
- Format switching infrastructure fully removed — no dead code paths remain

## Self-Check: PASSED

- ✅ All 8 key files exist on disk
- ✅ All 4 commits verified in git log: bd2a629, f20414a, 3e326e2, d6cb8c1
- ✅ All 16 acceptance criteria passed
- ✅ Plan-level verification (SRCH-01 through SRCH-04) passed
- ✅ Compilation: `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL

---
*Phase: 24-search-simplification*
*Completed: 2026-05-10*
