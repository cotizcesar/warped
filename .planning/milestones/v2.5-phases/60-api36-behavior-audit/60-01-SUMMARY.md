---
phase: 60-api36-behavior-audit
plan: "01"
subsystem: ui
tags: [android-16, sdk-36, edge-to-edge, window-insets, predictive-back, r8, material3]
requires:
  - phase: 59-catalog
    provides: HuggingFace catalog screen audited for insets
provides:
  - Verified 36/36 + R8-green release baseline (AGP 9.3.0 / Gradle 9.5.0)
  - Per-screen WindowInsets consumption on pill, sheets, Fuentes host, chat, catalog
  - Full BackHandler coverage with confirm-clean dead-path sweep
affects: [60-02 hardware session, release-UAT, future theme-toggle work]
tech-stack:
  added: []
  patterns: ["Explicit M3 inset contracts (contentWindowInsets stated at call site)", "Compose BackHandler as sole back API (hide()+onDismiss for sheets, popBackStack for screens)"]
key-files:
  created: []
  modified: ["app/src/main/java/com/warped/ui/theme/Theme.kt", "app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt", "app/src/main/java/com/warped/ui/chat/components/SourcePreviewSheet.kt", "app/src/main/java/com/warped/ui/chat/components/AllSourcesSheet.kt", "app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt", "app/src/main/java/com/warped/ui/chat/ChatScreen.kt", "app/src/main/java/com/warped/ui/settings/SettingsScreen.kt", "app/src/main/java/com/warped/ui/presets/PresetsScreen.kt", "app/src/main/java/com/warped/ui/navigation/NavGraph.kt"]
key-decisions:
  - "Sheets state contentWindowInsets explicitly (M3 1.4.0 name) instead of relying on the invisible default"
  - "Theme derives status/nav icon contrast from the app darkTheme flag via platform APIs (no new dependency)"
  - "ChatInputBar left untouched: parent Scaffold imePadding + own navigationBarsPadding already correct; adding imePadding would double-shift"
  - "SettingsScreen gains an onBack param wired to popBackStack; ChatScreen back gated on showModelPicker"
patterns-established:
  - "Sheet back = sheetState.hide() in try/finally + onDismiss (gesture and button share one path)"
  - "Never write legacy back strings (onBackPressed family) in code or comments — the sweep grep must stay at zero"
requirements-completed: [API-01, API-02, API-03]
duration: ~40min
completed: 2026-10-01
---

# Phase 60 Plan 01: API-36 Behavior Audit (Criteria 1–3) Summary

**Release green on 36/36 with R8, explicit per-screen WindowInsets on pill/sheets/catalog, and BackHandler everywhere in scope with a confirm-clean legacy-back sweep**

## Performance

- **Duration:** ~40 min
- **Started:** 2026-10-01 (phase execution)
- **Completed:** 2026-10-01
- **Tasks:** 3 / 3
- **Files modified:** 9 (5 in Task 2 commit, 7 in Task 3 commit, 3 overlap)

## Accomplishments

- **API-01:** `assembleRelease` BUILD SUCCESSFUL with `compileSdk = 36`, `targetSdk = 36`, R8 (`isMinifyEnabled`, `isShrinkResources`, `minifyReleaseWithR8` executed). Toolchain: AGP 9.3.0, Gradle 9.5.0. No target-API warnings in build output. Play Console warning status cannot be checked from this environment — flagged for human eyes below.
- **API-02:** Every in-scope surface now consumes WindowInsets per screen; structural grep `WindowInsets|navigationBarsPadding|imePadding|statusBarsPadding` returns >= 1 on all six files. `enableEdgeToEdge()` verified at MainActivity:24, untouched.
- **API-03:** `BackHandler` present in ChatScreen, all three sheets, Settings, Presets (WizardScreen handler untouched). Dead-path sweep (`onBackPressed|OnBackPressedCallback|OnBackPressedDispatcher` excl. imports/comments) returns **0** — confirm-clean as baselined.
- **Tests:** `:app:testDebugUnitTest` green — **890 tests, 0 failures, 0 errors, 0 skipped** (above the v2.4 494-green baseline; no regressions).

## Task Commits

Each task was committed atomically:

1. **Task 1: SDK 36 + R8 release confirm (API-01)** — verify-only, no code change (36/36 + R8 already declared and green; evidence recorded here).
2. **Task 2: Per-screen edge-to-edge insets (API-02)** — `8d9e7bd5` (feat)
3. **Task 3: Predictive-back BackHandler sweep (API-03)** — `d731636c` (feat)

**Plan metadata:** SUMMARY.md created on disk, uncommitted (orchestrator owns final metadata commit per --no-transition).

## Files Created/Modified

- `app/src/main/java/com/warped/ui/theme/Theme.kt` — **fixed**: `SideEffect` derives status-bar + nav-bar icon contrast from the app `darkTheme` flag (API 30+ `setSystemBarsAppearance`, legacy `systemUiVisibility` below). T-60-02 mitigation; platform APIs only, no new dependency (androidx.core is not a direct dep).
- `app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt` — **fixed**: explicit `contentWindowInsets`; **added** hide()+onDismiss `BackHandler`.
- `app/src/main/java/com/warped/ui/chat/components/SourcePreviewSheet.kt` — **fixed**: explicit `contentWindowInsets`; **added** hide()+onDismiss `BackHandler`.
- `app/src/main/java/com/warped/ui/chat/components/AllSourcesSheet.kt` — **fixed**: explicit `contentWindowInsets`; **added** hide()+onDismiss `BackHandler`.
- `app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt` — **fixed**: explicit `Scaffold(contentWindowInsets = ...)` for the Fuentes/catalog list.
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` — **verified-as-is** for insets; **added** picker-gated `BackHandler(enabled = showModelPicker)`.
- `app/src/main/java/com/warped/ui/settings/SettingsScreen.kt` — **added** `onBack` param + `BackHandler(onBack)`.
- `app/src/main/java/com/warped/ui/presets/PresetsScreen.kt` — **added** `BackHandler(onBack)` (same path as app-bar arrow).
- `app/src/main/java/com/warped/ui/navigation/NavGraph.kt` — **added** `onBack = { navController.popBackStack() }` wiring for Settings; popBackStack coverage otherwise unchanged.
- `app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt` — **verified-as-is** (`navigationBarsPadding` + parent Scaffold `imePadding`; adding `imePadding` here would double-shift under the keyboard).
- `app/src/main/java/com/warped/MainActivity.kt` — **verified-as-is** (`enableEdgeToEdge()` line 24).
- `app/build.gradle.kts` — **verified-as-is** (`compileSdk = 36`, `targetSdk = 36`, R8 on).

## Per-Screen Verified-vs-Fixed List (for the 60-02 device matrix)

| Surface | Status | Evidence |
|---|---|---|
| Chat pill (ChatInputBar) | Verified, untouched | `navigationBarsPadding` + Scaffold-level `imePadding` |
| Chat message list (ChatScreen) | Verified, untouched | Scaffold consumes `padding`; `imePadding` on Scaffold |
| ModelSelectorSheet | Fixed (explicit insets) + BackHandler | `contentWindowInsets`, hide()+onDismiss |
| SourcePreviewSheet | Fixed (explicit insets) + BackHandler | `contentWindowInsets`, hide()+onDismiss |
| AllSourcesSheet | Fixed (explicit insets) + BackHandler | `contentWindowInsets`, hide()+onDismiss |
| Fuentes/catalog list (HuggingFaceScreen) | Fixed (explicit insets) | `ScaffoldDefaults.contentWindowInsets` |
| Settings | BackHandler added | `onBack` → popBackStack |
| Presets | BackHandler added | same path as arrow |
| Wizard | Verified, untouched | existing `:130` handler stays |
| Status/nav icon contrast (Theme) | Fixed | per-app-theme derivation |

## Device-Visual Matrix (for the 60-02 hardware session to execute)

Check each cell on Pixel 8 + emulator; failures become explicit release-UAT, never silent passes:

- Nav mode × surface: gesture-nav + 3-button nav × (chat pill w/ keyboard open, each of the 3 sheets, Fuentes list, settings, presets) — no draw-under-bars, no double-padding gaps.
- Theme × surface: dark (current default) × all surfaces. **Light-theme cells are ASPIRATIONAL**: `WarpedTheme(darkTheme = true)` is hardcoded at the only call site — no theme toggle exists, so light mode cannot be reached on device yet. The Theme.kt contrast code is correct-by-construction for light but unverifiable until a toggle lands.
- Back parity: gesture vs button on chat-picker, each sheet, settings, presets — must dismiss/pop identically (gesture animated, button direct).
- System-vs-app theme mismatch: set device to light mode while app renders dark — status/nav icons must stay light-on-dark (this is what the Theme.kt fix addresses).

## Decisions Made

- Sheets state `contentWindowInsets` explicitly rather than relying on the invisible M3 default — the contract is visible at the call site and satisfies the structural grep without behavior change.
- Theme contrast uses platform `WindowInsetsController` / legacy flags instead of adding an androidx.core dependency for a two-flag call.
- ChatInputBar deliberately NOT given `imePadding` (would stack with the Scaffold-level one); recorded as verified-correct.
- Settings back goes through a new `onBack` param (single caller, always wired) rather than relying on NavHost default — makes gesture/button parity explicit.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `windowInsets` is not a ModalBottomSheet parameter in M3 1.4.0**
- **Found during:** Task 2 (sheet inset contracts)
- **Issue:** Plan prescribed the `windowInsets` parameter; M3 1.4.0 (compose-bom 2026.06.01) names it `contentWindowInsets: @Composable () -> WindowInsets` (verified against material3-android-1.4.0 sources). Debug compile failed with "No parameter with name 'windowInsets'".
- **Fix:** Used `contentWindowInsets = { sheetWindowInsets }` on all three sheets.
- **Files modified:** ModelSelector.kt, SourcePreviewSheet.kt, AllSourcesSheet.kt
- **Verification:** `:app:assembleDebug` green after fix
- **Committed in:** 8d9e7bd5 (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Version-skew naming fix only; zero behavior change versus plan intent. No scope creep.

## Issues Encountered

- Initial `assembleDebug` failed on the `windowInsets` parameter name (see deviation 1). No other issues; release build was green on first run.

## Known Stubs

None introduced. Modified files contain no TODO/FIXME/placeholder paths.

## Threat Flags

None. The only security-relevant change is the T-60-02 mitigation itself (Theme.kt contrast derivation); no new network endpoints, auth paths, or schema changes.

## User Setup Required

None - no external service configuration required.

## Needs Hardware Eyes (for orchestrator / 60-02)

1. **Play Console target-API warning status** — cannot be checked from this environment; confirm no warnings after uploading the 36/36 release artifact.
2. **Full device-visual matrix above** — gesture/3-button × dark on Pixel 8 + emulator; light-theme cells blocked until a theme toggle exists.
3. **Predictive-back animation feel** on sheets (hide() animates; system-predictive preview does not run since our handler consumes the event — confirm it feels native).

## Next Phase Readiness

- Criteria 1–3 code-complete: 36/36 + R8 green, insets explicit per screen, BackHandler everywhere in scope, sweep at zero.
- Ready for 60-02 (quota/large-screen pass + hardware session) with the matrix above as its input.
- Watch-item: light-theme device verification needs a theme toggle (out of this plan's scope) before it can be executed.

## Self-Check: PASSED

- All 9 modified files exist on disk; both task commits (`8d9e7bd5`, `d731636c`) verified in `git log`; no unintended deletions in either commit; `testDebugUnitTest` 890/0/0/0; dead-path sweep 0; inset grep >= 1 on all six surfaces.

---
*Phase: 60-api36-behavior-audit*
*Completed: 2026-10-01*
