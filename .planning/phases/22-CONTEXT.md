# Phase 22: Integration & Accessibility - Context

**Gathered:** 2026-05-09
**Status:** Ready for planning

<domain>
## Phase Boundary

Wire CTA navigation from wizard steps to actual app screens. Add "Setup Wizard" entry point in Settings > General tab. Implement re-entry review mode with visual distinction (checkmarks, "Revisar" buttons, "Close wizard" label). Handle back navigation correctly: exit confirmation dialog on first-launch page 0, back-to-Settings on re-entry page 0.

</domain>

<decisions>
## Implementation Decisions

### Navigation Wiring & Settings
- NavGraph passes `onNavigate: (String) -> Unit` and `isReEntry: Boolean` lambdas to WizardScreen. CTA clicks navigate to target screen; wizard stays in back stack (Back returns to wizard at current step).
- "Setup Wizard" card added to SettingsScreen's GeneralTab, below "Data" section, under an "App" section header. Card design follows existing Settings pattern: `Card` with title "Setup Wizard", description "Re-run the onboarding wizard to explore Warped features.", "Run" TextButton with accent color.
- Settings entry directly navigates to `Screen.Wizard.route` (no pop-up — wizard overlays Settings).

### Re-Entry Review Mode
- `isReEntry: Boolean` passed from NavGraph to WizardScreen, set into ViewModel via `setIsReEntry()` on composition.
- Visual distinction: PageIndicator shows checkmarks (✓) on steps that were completed (not in `skippedSteps`). Bottom bar uses "Revisar" instead of "Next". StepContent context data is refreshed with current app state.
- All 9 steps always shown in re-entry mode. Context data is re-snapshotted every time wizard opens (ViewModel.init fires on new composition).
- "Skip all" in TopAppBar is relabeled to "Close wizard" on re-entry. Confirmation dialog text adjusted: "Close the wizard?" instead of "Skip".
- "Done" on last step remains enabled. On re-entry, it just closes the wizard (wizard already marked complete, no redundant write).

### Back Navigation
- First launch, page 0: back arrow (new) + system back show exit confirmation dialog: "Exit Warped? The wizard will continue next time you open the app." with "Exit" (error color) / "Cancel".
- Re-entry, page 0: back arrow navigates to Settings (calls `onBackFromReEntry()` callback). No dialog.
- Re-entry, pages 1-8: back arrow goes to previous page (same as first launch).
- System back button on non-page-0: handled via `BackHandler` composable — same behavior as back arrow.

### ViewModel & UiState Updates
- `isReEntry: Boolean = false` in WizardUiState. Set via `fun setIsReEntry(value: Boolean)` in ViewModel.
- `showExitConfirm: Boolean = false` in WizardUiState for exit confirmation dialog.
- `onBackFromReEntry: () -> Unit` callback added to WizardScreen for re-entry page-0 back navigation.
- "Done" button same behavior regardless of re-entry — completes wizard (no-op if already complete, but doesn't hurt).

### the agent's Discretion
None — all questions had definitive answers.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- **WizardScreen.kt** — already has `onNavigate: (String) -> Unit` parameter (default no-op). Add `isReEntry: Boolean = false` and `onBackFromReEntry: () -> Unit = {}`.
- **WizardViewModel.kt** — already has `steps`, `pageCount`, `completeWizard()`. Add `setIsReEntry()`, `showExitDialog()`/`dismissExitDialog()`.
- **WizardUiState.kt** — already has `showSkipAllConfirm`. Add `isReEntry`, `showExitConfirm`.
- **WarpedAlertDialog** — reuse for exit confirmation dialog.
- **SettingsScreen.kt** — GeneralTab already has "Hugging Face" and "Data" sections with cards. Add "App" section with "Setup Wizard" card.
- **NavGraph.kt** — WizardScreen already rendered with `onWizardComplete` callback. Add `isReEntry` and `onNavigate` params.

### Established Patterns
- **Dialog pattern:** Boolean flag in UiState, dialog rendered before Scaffold, TextButton for confirm/dismiss.
- **Card pattern:** `Card(Modifier.fillMaxWidth(), colors = Card(0xFF2B2B29), shape = RoundedCornerShape(12.dp))` with Column inside.
- **Navigation:** `navController.navigate(route) { launchSingleTop = true }`.

### Integration Points
- `NavGraph.kt` Wizard composable: pass `isReEntry = false`, `onNavigate = { navController.navigate(it) { launchSingleTop = true } }`.
- `SettingsScreen.kt` GeneralTab: add card, on "Run" click → `onNavigateToWizard()` callback.
- `NavGraph.kt` Settings composable: `onNavigateToWizard = { navController.navigate(Screen.Wizard.route) { launchSingleTop = true } }`.
- `SettingsScreen.kt` adds `onNavigateToWizard: () -> Unit` param.
- `NavGraph.kt` might need a separate Wizard composable for re-entry (`isReEntry = true`).

</code_context>

<specifics>
## Specific Ideas

No specific requirements beyond ROADMAP phase description and success criteria. All grey areas resolved via smart discuss.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
