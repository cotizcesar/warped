# Phase 20: Foundation & Flow — Review

**Review date:** 2026-05-09
**Status:** clean

## Files Reviewed

### `data/local/preferences/WizardPreferences.kt` (NEW)
- **Pattern match:** Follows `AdvancedPreferences.kt` exactly — top-level `preferencesDataStore` extension, `@Singleton` + `@Inject constructor(@ApplicationContext)`, companion object keys, `Flow` reads + `suspend` writes.
- **Keys:** `booleanPreferencesKey("wizard_completed")` and `stringSetPreferencesKey("skipped_steps")` — correct types.
- **Future-proof:** `resetWizard()` added for Phase 22 re-entry support.
- **Verdict:** ✓ No issues.

### `ui/wizard/WizardUiState.kt` (NEW)
- **Pattern match:** Follows `ChatUiState.kt` — `data class` with all-default values.
- **Fields:** `currentPage: Int = 0`, `isWizardComplete: Boolean = false`, `skippedSteps: Set<String> = emptySet()`, `showSkipAllConfirm: Boolean = false` — minimal, sufficient.
- **Verdict:** ✓ No issues.

### `ui/wizard/WizardViewModel.kt` (NEW)
- **Pattern match:** Follows all existing ViewModels — `@HiltViewModel`, `MutableStateFlow<WizardUiState>()`, `_uiState.asStateFlow()`, `viewModelScope.launch` for DataStore observation.
- **Page management:** `goToPage()` clamps to valid range, `goToNextPage()`/`goToPreviousPage()` guard against out-of-bounds.
- **Skip logic:** `skipCurrentStep()` persists the step key immediately via `markStepsSkipped()`, then advances. Correct.
- **Skip all:** `confirmSkipAll()` marks ALL 9 step keys as skipped + marks wizard complete. Correct — all steps are considered skipped when user aborts.
- **Completion:** `completeWizard()` only writes `wizardCompleted = true` (completed steps are not "skipped").
- **Step keys alignment:** `stepKeys`, `stepLabels`, `stepDescriptions` lists are order-aligned — verified.
- **Verdict:** ✓ No issues.

### `ui/wizard/WizardScreen.kt` (NEW)
- **Pattern match:** Follows `HelpScreen.kt` and other screens — `Scaffold` + `TopAppBar`, `@Composable fun WizardScreen(...)`, `hiltViewModel()`.
- **Fixed:** `collectAsState()` → `collectAsStateWithLifecycle()` (lifecycle-aware, matches all other screens).
- **Pager sync:** Bidirectional sync via two `LaunchedEffect` blocks — `uiState.currentPage` drives `animateScrollToPage`, `pagerState.currentPage` feeds back to ViewModel. Prevents state drift.
- **TopAppBar:** Page 0 shows "Skip all" + no back arrow; pages 1-8 show back arrow + "Skip". Page dots animated inside TopAppBar.
- **Bottom bar:** "Back" (hidden page 0), "Next"/"Done" (last page). Correct button labeling.
- **Skip all dialog:** Uses `WarpedAlertDialog` with correct text "You can re-open it anytime from Settings." Calls `onWizardComplete()` after confirmation.
- **Theme:** Dark background (`#1F1F1E`), card bg (`#2B2B29`), accent (`#D97757`), text colors match existing Warped palette.
- **Verdict:** ✓ No issues (after `collectAsStateWithLifecycle` fix).

### `ui/navigation/Screen.kt` (MODIFIED)
- **Change:** Added `data object Wizard : Screen("wizard", "Wizard", Icons.Filled.Explore)`.
- **Icon:** `Icons.Filled.Explore` — reasonable substitute for `Tour` (not sure if available in extended icons).
- **Verdict:** ✓ No issues.

### `ui/navigation/NavGraph.kt` (MODIFIED)
- **Changes:** Added imports for `WizardPreferences`, `WizardScreen`. Added `wizardPreferences` to EntryPoint. Added `isWizardComplete` state observation with null initial value. Flash guard renders empty dark Box while loading. `LaunchedEffect` redirects to Wizard when complete is false and route isn't already Wizard. Added Wizard composable route with `onWizardComplete` callback.
- **Flash guard:** `if (isWizardComplete == null)` → renders Box and early-returns before NavHost. Prevents Chat from flashing before wizard redirect.
- **Redirect logic:** `popUpTo(Screen.Chat.route) { inclusive = true }` — Wizard becomes new root, Chat is gone from back stack. After completion, `popUpTo(Screen.Wizard.route) { inclusive = true }` — Wizard is gone, Chat is new root. Correct.
- **EntryPoint:** Added `fun wizardPreferences(): WizardPreferences` — auto-resolved by Hilt since `WizardPreferences` is `@Singleton` with `@Inject constructor`.
- **Verdict:** ✓ No issues.

## Summary

| Finding | Severity | File | Status |
|---------|----------|------|--------|
| `collectAsState()` instead of `collectAsStateWithLifecycle()` | Low | WizardScreen.kt | Fixed |
| `@ApplicationContext` annotation target warning | Info | WizardPreferences.kt | Pre-existing Kotlin issue (KT-73255), not a bug |

**Overall:** Clean — no blocking issues. 1 low-severity convention fix applied.
