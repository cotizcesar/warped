# Phase 22: Integration & Accessibility — Plan

**Created:** 2026-05-09
**Status:** Executing
**Requirements:** WZAC-01..03 (3)

## Tasks

### Task 1: Wire CTA Navigation
- **Files:** `NavGraph.kt` (modify)
- **What:** Pass `onNavigate: (String) -> Unit` to WizardScreen that calls `navController.navigate(route) { launchSingleTop = true }`. CTA clicks navigate away; wizard stays in back stack.

### Task 2: Add Settings Entry Point
- **Files:** `SettingsScreen.kt` (modify), `NavGraph.kt` (modify)
- **What:** Add "Setup Wizard" card in GeneralTab under new "App" section. "Run" TextButton navigates to `"wizard?review=true"`. SettingsScreen gains `onNavigateToWizard: () -> Unit` param. GeneralTab gains same param passed through.

### Task 3: Re-Entry Review Mode
- **Files:** `WizardScreen.kt` (modify), `WizardViewModel.kt` (modify), `WizardUiState.kt` (modify), `NavGraph.kt` (modify)
- **What:** `isReEntry: Boolean` param on WizardScreen, set into ViewModel via `setIsReEntry()`. UiState gains `isReEntry` field. TopAppBar: "Close" instead of "Skip All" label on re-entry. Bottom bar: "Revisar" instead of "Next". "Done" calls `onBackFromReEntry` on re-entry. NavGraph passes `review` argument to determine re-entry.

### Task 4: Back Navigation
- **Files:** `WizardScreen.kt` (modify), `WizardViewModel.kt` (modify), `WizardUiState.kt` (modify)
- **What:** Page 0 now shows back arrow in both modes. First launch: shows exit confirmation dialog ("Exit Warped?"). Re-entry: calls `onBackFromReEntry` → pops back to Settings. `BackHandler` handles system back gesture. All pages 1-8: go to previous page (same as before).

## Verification Against Success Criteria

| Criterion | How Verified |
|-----------|-------------|
| 1. "Setup Wizard" card in Settings → General; "Run" opens wizard | SettingsScreen GeneralTab "App" section card. `onNavigateToWizard()` navigates to `"wizard?review=true"` |
| 2. CTA buttons navigate to correct screens; Back returns to wizard | `onNavigate` wires to `navController.navigate(route)` with `launchSingleTop`. Back in NavHost returns to wizard (in back stack) |
| 3. Re-opening wizard shows current state counts | `review=true` triggers `isReEntry = true`. `snapshotContext()` re-runs in ViewModel.init → fresh counts in StepContent |
| 4. Back from re-entered wizard returns to Settings; first-launch back exits app | Re-entry: `onBackFromReEntry` → `navController.popBackStack()`. First launch: exit confirmation dialog → `onWizardComplete()` |
