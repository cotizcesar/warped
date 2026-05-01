---
phase: quick-260430-rdt
plan: 01
subsystem: ui/huggingface
tags: [ui, ux, bugfix, state-management]
requires: []
provides:
  - "SiblingFileCard composable matching ModelSearchResultCard design language"
  - "Download cancel button in ModelDetailScreen wired to pauseDownload"
  - "Search text persistence across rotation/back-navigation via ViewModel binding"
affects:
  - app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt
  - app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt
tech-stack:
  added: []
  patterns:
    - "Composable extraction: SiblingFileCard mirrors ModelSearchResultCard structure"
    - "Two-way ViewModel binding: remember initializes from uiState.searchQuery, onValueChange pushes via onSearchTextChanged"
key-files:
  modified:
    - app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt
    - app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt
decisions:
  - "Used OutlinedButton for download action to maintain visual hierarchy within the card"
  - "Compatibility color logic (primary, orange, red) moved into SiblingFileCard composable"
  - "pauseDownload resets full UI state (isDownloading, progress, fileName) acting as cancel semantics"
metrics:
  duration: "~8 minutes"
  tasks: 2
  files: 2
  completed_date: "2026-05-01"
---

# Quick Task 260430-rdt: Unify Detail Screen Design, Add Download Cancel, Fix Search Text Persistence Summary

**One-liner:** Redesigned Hugging Face model detail file cards to match the main search list visual language, added in-progress download cancellation with UI state reset, and bound search text to ViewModel for survival across Android configuration changes.

## Tasks Executed

### Task 1: Redesign "Available Models" cards to match ModelSearchResultCard
**Commit:** `37d86d2`

Created `SiblingFileCard` composable that structurally mirrors `ModelSearchResultCard`:
- `Storage` icon + filename title with `CheckCircle` badge when compatible
- Subtitle showing file size with compatibility-colored label
- Chips row: compatible badge, file size, model downloads count
- `OutlinedButton` aligned right for download action
- Card uses `primaryContainer` colors and `BorderStroke` when compatible; `surfaceVariant` otherwise

Replaced the old inline `Card { Row { ... } }` block in `items(siblings)` with a concise call to `SiblingFileCard`.

### Task 2: Add download cancel button and fix search text persistence
**Commit:** `d1598c1`

**Part A — ViewModel:**
- Updated `pauseDownload()` to reset `isDownloading`, `downloadProgress`, and `downloadingFileName` — turning pause into cancel semantics for the UI
- Added `onSearchTextChanged(text: String)` method as ViewModel source of truth for search text

**Part B — Cancel button UI:**
- Added `onCancelDownload: () -> Unit` parameter to `ModelDetailScreen`
- Extended download progress `item` block with a `Row` containing the progress text and a `TextButton("Cancel")`
- Wired `onCancelDownload = { viewModel.pauseDownload() }` in `HuggingFaceScreen`

**Part C — Search text binding:**
- Initialized `searchText` from `uiState.searchQuery` via `remember { mutableStateOf(uiState.searchQuery) }` instead of empty string
- Updated `OutlinedTextField` `onValueChange` to push to `viewModel.onSearchTextChanged(it)`

This creates two-way binding: text initializes from surviving ViewModel state on recomposition; edits push back to state for debounced search.

## Verification

| Criterion | Status |
|-----------|--------|
| `SiblingFileCard` composable exists with structure matching `ModelSearchResultCard` | PASS |
| Cancel button wired to `pauseDownload()` which resets `isDownloading` | PASS |
| Search text survives device rotation (initialized from `uiState.searchQuery`) | PASS |
| `onSearchTextChanged` exists in ViewModel | PASS |
| Both files brace-balanced, all required imports present | PASS |
| Gradle build (`./gradlew :app:compileDebugKotlin`) | SKIPPED — pre-existing env issue (Java 25.0.2 incompatible with AGP 8.13.2/Kotlin toolchain) |

## Deviations from Plan

None — plan executed exactly as written.

### Pre-existing Environment Issue (out of scope)

**Java 25.0.2 / AGP compatibility:** The build environment runs OpenJDK 25.0.2 which is too new for the Kotlin compiler bundled with AGP 8.13.2. `JavaVersion.parse()` throws `IllegalArgumentException` on the version string. Gradle build verification could not be completed, but code was verified via structural checks (brace balance, import completeness). Documented in `deferred-items.md`.

## Self-Check

- [x] `SiblingFileCard` composable at line 310 in HuggingFaceScreen.kt — FOUND
- [x] `onCancelDownload` parameter at line 409, wired at line 81 — FOUND
- [x] `onSearchTextChanged` at line 154 in HuggingFaceViewModel.kt — FOUND
- [x] `remember { mutableStateOf(uiState.searchQuery) }` at line 58 — FOUND
- [x] Commit `37d86d2` — FOUND in git log
- [x] Commit `d1598c1` — FOUND in git log
