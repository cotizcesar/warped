# Phase 22: Integration & Accessibility — Review

**Review date:** 2026-05-09
**Status:** clean

## Files Reviewed

### `ui/wizard/WizardUiState.kt` (MODIFIED)
- Added `showExitConfirm: Boolean = false` and `isReEntry: Boolean = false` fields.
- Follows existing dialog state pattern (`showSkipAllConfirm`).
- **Verdict:** ✓ No issues.

### `ui/wizard/WizardViewModel.kt` (MODIFIED)
- Added `setIsReEntry(Boolean)`, `showExitDialog()`, `dismissExitDialog()` methods.
- `confirmSkipAll()` unchanged — works for both modes (onWizardComplete for first launch, onBackFromReEntry for re-entry).
- **Verdict:** ✓ No issues.

### `ui/wizard/WizardScreen.kt` (MODIFIED)
- **New params:** `isReEntry: Boolean = false`, `onBackFromReEntry: () -> Unit = {}`.
- **Exit dialog:** `WarpedAlertDialog` with "Exit Warped? The wizard will continue next time." — follows existing dialog pattern.
- **Skip/Close dialog:** Text adapts based on `isReEntry` ("Close wizard" vs "Skip wizard").
- **Back arrow on page 0:** Now always visible. First launch → exit dialog. Re-entry → `onBackFromReEntry()` (returns to Settings).
- **System back:** `BackHandler` composable routes to exit dialog (first launch) or previous page/page-0-back (re-entry).
- **Bottom bar labeling:** "Revisar" on re-entry, "Next" on first launch.
- **"Done" routing:** On re-entry, calls `onBackFromReEntry()` instead of `onWizardComplete()`.
- **Verdict:** ✓ No issues.

### `ui/settings/SettingsScreen.kt` (MODIFIED)
- Added `onNavigateToWizard: () -> Unit = {}` param. Passed through to `GeneralTab`.
- GeneralTab: new "App" section between Data and Security. Card with "Setup Wizard" title, description, "Run" TextButton in accent color (#D97757).
- Follows existing card pattern (same colors, shape, padding).
- **Verdict:** ✓ No issues.

### `ui/navigation/NavGraph.kt` (MODIFIED)
- Wizard route updated to `"wizard?review={review}"` with `review: BoolType` argument (default `false`).
- WizardScreen receives `isReEntry`, `onNavigate`, `onBackFromReEntry` lambdas.
- `onNavigate`: `navController.navigate(route) { launchSingleTop = true }`. CTA keeps wizard in back stack.
- `onBackFromReEntry`: `navController.popBackStack()` returns to Settings.
- SettingsScreen receives `onNavigateToWizard` → `navController.navigate("wizard?review=true")`.
- **Verdict:** ✓ No issues.

## Summary

| Finding | Severity | File | Status |
|---------|----------|------|--------|
| `onNavigateToWizard` param not passed to GeneralTab | Compiler error | SettingsScreen.kt | Fixed |

**Overall:** Clean — no remaining issues. All 4 success criteria verified.
