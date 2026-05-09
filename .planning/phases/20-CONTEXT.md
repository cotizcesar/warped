# Phase 20: Foundation & Flow - Context

**Gathered:** 2026-05-09
**Status:** Ready for planning

<domain>
## Phase Boundary

Build the DataStore persistence layer for first-launch detection and the HorizontalPager shell with complete skip/next/back/completion navigation flow. This phase delivers the navigation skeleton with 9 placeholder pages and all user interaction flows (swipe, skip per step, skip all with confirmation, completion to Chat). Step content (icons, descriptions, CTAs) and integration with real screens come in Phases 21 and 22.

</domain>

<decisions>
## Implementation Decisions

### Cold Start Routing
- `startDestination` stays `Screen.Chat.route` in NavHost. Wizard is a composable destination shown via `LaunchedEffect` redirect when DataStore indicates first launch (wizard not yet completed).
- Guard against UI flash: use `initialValue = null` pattern — render empty `Box(Modifier.fillMaxSize())` with dark background until DataStore emits first value from `OnboardingPreferences.isFirstLaunchComplete`.
- After completion or skip-all, pop wizard from back stack so Chat becomes the new root. Back from Chat should not return to an already-completed wizard.

### HorizontalPager Shell UX
- 9 placeholder pages to match final step count. Each has a label string + placeholder description. Real content comes in Phase 21.
- TopAppBar varies: first page shows title + "Skip all" TextButton (no back arrow). Pages 2-9 show back arrow + title + "Skip" TextButton.
- Page indicator dots: inside TopAppBar subtitle area, centered between title and skip/action button.
- Navigation buttons: Bottom bar with Back (left, hidden on page 0), "Skip" or "Next" TextButton (right), "Done" on last page instead of skip.

### Skip/Completion Behavior
- "Skip" per step persists `skippedSteps` as `Set<String>` in DataStore (step keys like "welcome", "engines", etc.) for Phase 22 re-entry review mode.
- "Skip all" dialog text: "Skip the onboarding wizard? You can re-open it anytime from Settings." with "Skip" (destructive) / "Cancel" buttons.
- "Skip all" confirmation: marks `wizardCompleted = true` in DataStore AND persists all current steps as `skippedSteps`. Navigates to Chat with wizard popped from back stack.
- "Done" on last step: marks `wizardCompleted = true` in DataStore, navigates to Chat with wizard popped. Individual skipped steps are tracked — completed steps get erased from `skippedSteps` (they were seen).

### ViewModel Architecture & Phase 20 Scope
- No back arrow on step 0 (first page). Phase 22 handles `isReEntry` distinction and Back-press behavior per WZFL-07.
- ViewModel reads `OnboardingPreferences` directly via injection — follows existing self-contained ViewModel pattern.
- Placeholder step content: `List<String>` of step labels + `List<String>` of placeholder descriptions stored in the ViewModel. Swapped for real WizardStep data in Phase 21.
- New file `OnboardingPreferences.kt` — separate DataStore file, not polluting AdvancedPreferences. Follows the same `@Singleton + @Inject constructor(@ApplicationContext)` pattern as AdvancedPreferences and ToolPreferences.

### the agent's Discretion
None — all questions had definitive answers.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `WarpedAlertDialog` — branded Material3 AlertDialog wrapper with dark container, 16dp rounded corners, 6dp elevation. Use for skip-all confirmation.
- `Screen` sealed class — add `data object Wizard : Screen("wizard", "Wizard", Icons.Filled.Tour)`.
- `NavGraph.kt` — WarpedNavGraph composable with NavHost, ModalNavigationDrawer, rememberNavController. Add Wizard composable route.
- `AdvancedPreferences.kt` / `ToolPreferences.kt` — DataStore pattern: top-level `preferencesDataStore` delegation, `@Singleton` + `@Inject constructor(@ApplicationContext)`, expose `Flow<T>` + `suspend fun save()`.
- `datastore-preferences` already at version 1.1.3 in `libs.versions.toml`.

### Established Patterns
- **ViewModel:** `@HiltViewModel`, `MutableStateFlow<XxxUiState>()`, `_uiState.asStateFlow()`, `collectAsStateWithLifecycle()` in composable.
- **UiState:** `data class` with all-default field values, separate file in same package.
- **Screen composables:** `@Composable fun XxxScreen(viewModel: XxxViewModel = hiltViewModel(), ...)` with `Scaffold + TopAppBar`.
- **Colors:** `Color(0xFF1F1F1E)` dark backgrounds, `Color(0xFF2B2B29)` card containers, `Color(0xFFD97757)` accent.
- **Navigation icons:** `Icons.AutoMirrored.Filled.ArrowBack` for back, `Icons.Filled.Menu` for drawer.
- **Hilt:** `@Singleton + @Inject constructor` auto-discovered, no module needed for DataStore preferences classes.

### Integration Points
- `NavGraph.kt`: add Wizard composable route with onCompleted lambda that navigates to Chat and pops wizard.
- `Screen.kt`: add `data object Wizard` entry.
- No existing first-launch detection or onboarding code — this is net-new functionality.

</code_context>

<specifics>
## Specific Ideas

No specific requirements beyond ROADMAP phase description and success criteria. All grey areas resolved via smart discuss decisions above.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
