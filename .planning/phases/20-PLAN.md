# Phase 20: Foundation & Flow — Plan

**Created:** 2026-05-09
**Status:** Executing
**Requirements:** WZFL-01..08, WZCT-03 (9)

## Tasks

### Task 1: WizardPreferences DataStore
- **File:** `data/local/preferences/WizardPreferences.kt`
- **Action:** NEW
- **What:** DataStore with `booleanPreferencesKey("wizard_completed")` and `stringSetPreferencesKey("skipped_steps")`. `@Singleton` + `@Inject constructor(@ApplicationContext)`. Expose `val isWizardComplete: Flow<Boolean>` and `suspend fun markWizardComplete()`, `suspend fun markStepsSkipped(steps: Set<String>)`, `val skippedSteps: Flow<Set<String>>`.

### Task 2: WizardUiState
- **File:** `ui/wizard/WizardUiState.kt`
- **Action:** NEW
- **What:** `data class WizardUiState(currentPage: Int = 0, isWizardComplete: Boolean = false, skippedSteps: Set<String> = emptySet(), showSkipAllConfirm: Boolean = false)`.

### Task 3: WizardViewModel
- **File:** `ui/wizard/WizardViewModel.kt`
- **Action:** NEW
- **What:** `@HiltViewModel`, inject `WizardPreferences`. Manage pager state (next/back/skip), skip-all confirmation dialog, completion. Expose placeholder step labels as `List<String>` (9 entries).

### Task 4: WizardScreen
- **File:** `ui/wizard/WizardScreen.kt`
- **Action:** NEW
- **What:** Full-screen composable with `HorizontalPager(9 pages)`. `Scaffold` with:
  - TopAppBar: page 0 → title + "Skip all" TextButton; pages 1-8 → back arrow + title + "Skip" TextButton. Page indicator dots inside TopAppBar subtitle.
  - Bottom bar: "Back" TextButton (hidden page 0), "Skip"/"Next" TextButton, "Done" on page 8.
  - Center content: placeholder card with step number and label.
  - Skip-all confirmation via WarpedAlertDialog.
  - `onWizardComplete: () -> Unit` callback.

### Task 5: Screen.kt
- **File:** `ui/navigation/Screen.kt`
- **Action:** MODIFY
- **What:** Add `data object Wizard : Screen("wizard", "Wizard", Icons.Filled.Tour)`.

### Task 6: NavGraph.kt
- **File:** `ui/navigation/NavGraph.kt`
- **Action:** MODIFY
- **What:** Add Hilt EntryPoint for WizardPreferences. Read `isWizardComplete` via `collectAsStateWithLifecycle`. On first launch (wizard not complete), redirect to Wizard route. Add `composable(Screen.Wizard.route)` with `onWizardComplete` callback that navigates to Chat and pops wizard.

## Verification Against Success Criteria

| Criterion | How Verified |
|-----------|-------------|
| 1. Fresh install → Wizard opens directly | NavGraph reads DataStore, redirects to Wizard when `isWizardComplete == false` |
| 2. Swipe between 9 placeholder cards | HorizontalPager with 9 pages, each shows step label |
| 3. "Skip" advances to next step | Skip TextButton calls viewModel.skipCurrentStep() → advances pager |
| 4. "Skip all" confirmation → Chat | WarpedAlertDialog → onConfirm: mark wizard complete, call onWizardComplete |
| 5. "Done" on last step → Chat | Done button on page 8 marks complete, calls onWizardComplete |

## Dependencies
- None (first phase of v1.4, builds on existing architecture)
- Uses existing: datastore-preferences 1.1.3, Compose BOM 2026.04.01 (HorizontalPager in Foundation), WarpedAlertDialog, Hilt
