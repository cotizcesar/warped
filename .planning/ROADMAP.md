# Roadmap: Warped v1.4 — Onboarding Wizard

**Milestone:** v1.4 Onboarding Wizard
**Created:** 2026-05-08
**Phases:** 3 (continues from Phase 19 of v1.3)
**Requirements:** 25 total, all mapped

## Phase Overview

| # | Phase | Goal | Requirements | Success Criteria |
|---|-------|------|--------------|------------------|
| 20 | Foundation & Flow | DataStore first-launch detection, HorizontalPager shell with skip/next/back, completion flow | WZFL-01..08, WZCT-03 (9) | 5 |
| 21 | Step Content & Context | All 9 step contents with icons, context-aware adaptation, warm friendly tone | WZST-01..11, WZCT-01, WZCT-02 (13) | 5 |
| 22 | Integration & Accessibility | Settings entry, CTA navigation wiring, re-entry review mode, back handling | WZAC-01..03 (3) | 4 |

---

## Phase 20: Foundation & Flow

**Goal:** Build the DataStore persistence layer for first-launch detection and the HorizontalPager shell with complete skip/next/back/completion navigation flow.

**Requirements:** WZFL-01, WZFL-02, WZFL-03, WZFL-04, WZFL-05, WZFL-06, WZFL-07, WZFL-08, WZCT-03

**New files:**
- `data/local/preferences/WizardPreferences.kt` — DataStore with `booleanPreferencesKey("wizard_completed")` and `stringSetPreferencesKey("skipped_steps")`
- `ui/wizard/WizardScreen.kt` — Full-screen composable with HorizontalPager, TopAppBar, skip/next/back buttons, page indicator dots
- `ui/wizard/WizardViewModel.kt` — @HiltViewModel managing pager state, skip logic, completion
- `ui/wizard/WizardUiState.kt` — Data class with currentPage, isCompleted, skippedSteps, showSkipConfirm

**Modified files:**
- `ui/navigation/Screen.kt` — Add `data object Wizard : Screen("wizard", "Wizard", Icons.Filled.Tour)`
- `ui/navigation/NavGraph.kt` — Add `composable(Screen.Wizard.route)` with startDestination logic
- `ui/components/WarpedAlertDialog.kt` — No changes (reuse existing)

**Success criteria:**
1. Fresh install → app opens directly to Wizard screen (not Chat)
2. User can swipe left/right between placeholder step cards
3. "Skip" button on each step advances to next step without completing wizard
4. "Skip all" shows confirmation dialog; confirming marks wizard completed and navigates to Chat
5. "Done" on last step marks wizard completed; subsequent launches go straight to Chat

---

## Phase 21: Step Content & Context

**Goal:** Populate all 9 wizard steps with icons, titles, warm descriptions, and context-aware content variants. Read app state at open time to adapt messaging.

**Requirements:** WZST-01, WZST-02, WZST-03, WZST-04, WZST-05, WZST-06, WZST-07, WZST-08, WZST-09, WZST-10, WZST-11, WZCT-01, WZCT-02

**New files:**
- `ui/wizard/WizardStep.kt` — Enum with 9 steps: `WELCOME, ENGINES, GGUF_DOWNLOAD, LITERT_LM, LOCAL_CHAT, REMOTE_PROVIDERS, REMOTE_CHAT, PRESETS, HISTORY`. Each has `title: String`, `description: String`, `icon: ImageVector`, `ctaLabel: String`, `ctaRoute: String`.
- `ui/wizard/StepContent.kt` — Composable rendering a single step: icon, title, description, CTA button. Handles context variants (e.g. "Ya tienes 3 modelos GGUF" vs "Descarga tu primer modelo").
- `ui/components/PageIndicator.kt` — Reusable dot indicator composable (Row of Box with CircleShape, current page highlighted).

**Modified files:**
- `ui/wizard/WizardViewModel.kt` — Add context reading: inject ModelsRepo, EndpointsRepo, ChatRepo; snapshot counts via `.first()` on init.
- `ui/wizard/WizardUiState.kt` — Add `WizardContextData` (modelCount, endpointCount, chatCount, hasGgufModels, hasLitertlmModels, hasRemoteEndpoints).

**Success criteria:**
1. Each of the 9 steps renders with a distinct Material icon, title, and 2-3 sentence description in Spanish
2. All text uses warm, friendly tone (not technical documentation style)
3. If user has 0 models downloaded, Step 3 shows "Descarga tu primer modelo GGUF" CTA
4. If user has 3 models downloaded, Step 3 shows "Ya tienes 3 modelos descargados. ¡Explora más!" variant
5. CTA button appears on each step with correct label; UI renders but navigation is wired in Phase 22

---

## Phase 22: Integration & Accessibility

**Goal:** Wire CTA navigation to actual screens, add Settings entry point, implement re-entry review mode, handle back navigation correctly for first-launch vs settings re-entry.

**Requirements:** WZAC-01, WZAC-02, WZAC-03

**Modified files:**
- `ui/navigation/NavGraph.kt` — Pass navigation callbacks to WizardScreen (onNavigateToModels, onNavigateToEndpoints, onNavigateToChat, onNavigateToPresets, onNavigateToHuggingFace)
- `ui/settings/SettingsScreen.kt` — Add "Setup Wizard" card in General tab with "Run" TextButton
- `ui/wizard/WizardScreen.kt` — Wire CTA buttons to navigation callbacks; handle re-entry vs first-launch mode (forward to Settings on Back for re-entry, exit app on first launch page 0)
- `ui/wizard/WizardViewModel.kt` — Add `isReEntry: Boolean` parameter; on re-entry, show review variant with checkmarks and current state counts

**Success criteria:**
1. "Setup Wizard" card visible in Settings → General tab; tapping "Run" opens wizard
2. CTA buttons on each step navigate to the correct screen; pressing Back returns to wizard at same step
3. Re-opening wizard from Settings shows each step with current state counts (not generic text)
4. Back from re-entered wizard returns to Settings; Back from first-launch wizard on step 1 exits app

---

## Dependency Graph

```
Phase 20 (Foundation & Flow)
    └─► Phase 21 (Step Content & Context)
            └─► Phase 22 (Integration & Accessibility)
```

No parallel phases — each builds on the previous. Phase 21 depends on Phase 20's WizardViewModel and HorizontalPager shell. Phase 22 depends on Phase 21's completed step content and Phase 20's navigation shell.

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| StartDestination flash (Chat shows before Wizard) | Medium | Low | Use `initialValue = null` guard in NavGraph to show empty box until DataStore emits |
| Dark theme contrast issues with step content | Low | Medium | Use theme tokens exclusively; test all 9 steps on dark background |
| Context data causes recomposition storms | Low | Medium | Snapshot once with `.first()`, don't collect continuously |
| Back navigation edge case (first launch vs re-entry) | Low | Medium | Pass `isReEntry` flag; Phase 22 tests both paths explicitly |

---

## File Count Estimate

| Phase | New Files | Modified Files |
|-------|-----------|---------------|
| 20 | 4 | 2 |
| 21 | 3 | 2 |
| 22 | 0 | 4 |
| **Total** | **7** | **8** |

---
*Roadmap created: 2026-05-08*
*Last updated: 2026-05-08 after v1.4 roadmap creation*
