# Phase 40 UI-SPEC: Runtime & Allowlist Foundation

**Phase:** 40-runtime-allowlist-foundation
**Gathered:** 2026-06-06
**Status:** Approved

## Scope
Phase 40 is infrastructure-heavy; the only user-visible UI changes are:
1. **SplashScreen integration** (RUNTIME-08 / PERF-11) — replaces the white-flash with a themed splash that dismisses into the first Compose frame.
2. **Dark mode no-recreate** (RUNTIME-08) — toggling system dark mode no longer kills the chat; theme re-applies in place.
3. **HF Recommended tab content** (RUNTIME-05/06) — the curated list now comes from the asset, but UI layout is unchanged.

No new screens, no new composables, no new navigation flows. The type-safe nav migration (RUNTIME-07) is invisible to users.

## 1. SplashScreen

**Current behavior:** `Theme.Warped.Splash` extends `android:Theme.Material.Light.NoActionBar` with `windowBackground = @drawable/splash_bg`. Splash dismisses when the first Activity content view is laid out. There's still a brief white-flash on cold start.

**New behavior:** Switch the splash theme parent to `Theme.SplashScreen` (from `androidx.core:core-splashscreen:1.2.0-beta01`). `MainActivity.onCreate` calls `installSplashScreen()` as the first line, before `super.onCreate()`. The splash uses the app's brand background and a static logo (no animation) so it dismisses predictably as soon as the first Compose frame is laid out.

**Components:**
- `Theme.Warped.Splash` (themes.xml) — parent `Theme.SplashScreen`, items `windowSplashScreenBackground = @color/window_background`, `postSplashScreenTheme = @style/Theme.Warped`.
- `MainActivity.onCreate` — calls `installSplashScreen()` first.
- `MainActivity` exposes a `splashScreen.setKeepOnScreenCondition { !isReady }` pattern; `isReady` flips to true in the `setContent` `LaunchedEffect(Unit)`.

**States:**
| State | Visual |
|-------|--------|
| Cold start, before first frame | Theme background (`#1F1F1E`) fills screen — no white flash |
| First frame ready | Splash cross-fades into MainActivity content |
| Warm start | Splash shown for <1 frame; user perceives no flash |

**Success criterion:** "The app cold-starts without a white flash; a SplashScreen is shown and dismisses cleanly into the first Compose frame."

## 2. Dark mode no-recreate

**Current behavior:** Toggling system dark mode while the app is foreground causes `MainActivity` to be recreated (`onDestroy → onCreate`), losing transient ViewModel state. The user sees a brief reload.

**New behavior:** `MainActivity` declares `configChanges="uiMode|orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden"`. The Compose `WarpedTheme` already reads `isSystemInDarkTheme()` and re-themes in place when the configuration changes.

**Components:**
- `AndroidManifest.xml` `<activity>` — configChanges updated.
- `WarpedTheme` (no change) — already supports `isSystemInDarkTheme()`.
- `ChatUiState.codeTheme` / `streamingContent` etc. (no change) — ViewModel survives configuration change because Hilt-scoped ViewModelStore is preserved across `onConfigurationChanged`.

**States:**
| State | Visual |
|-------|--------|
| User toggles dark mode (system) | App instantly re-themes; chat scroll position, drafts, and scroll state preserved |
| User toggles light mode | Same as above |

**Success criterion:** "Toggling dark mode in system settings no longer causes a visible Activity recreate; the chat screen re-themes in place."

## 3. HF Recommended tab (asset-driven)

**Current behavior:** The Recommended tab queries the HF search API with `library=litertlm-community`, filters results by `.litertlm` extension, sorts by downloads. Latency: 0.5-2s on cold load, dependent on HF uptime.

**New behavior:** The Recommended tab reads `assets/model_allowlist.json` (8 hand-curated entries) synchronously at startup. No network call. Same UI layout — `LazyColumn` of model cards, each with display name, size, and a Download button.

**Components:**
- `HuggingFaceViewModel.loadRecommended()` — calls `modelAllowlistRepository.getAll()`, maps each `AllowlistEntry` to a `HuggingFaceModel` for the existing UI.
- `HuggingFaceScreen` (no visual change).

**Success criterion:** "The 'Recommended' list in the HF browser is now driven by `assets/model_allowlist.json` with `name`, `displayName`, `modelFile`, `sizeInBytes`, `capabilities`, `llmPromptTemplates`, `taskTypes` — and reflects the curated entries from the asset."

## Out of Scope (this phase)
- A new splash animation (logo pulse, etc.) — uses static background.
- A custom theme for landscape dark mode — orientation configChanges already handles it.
- The chat screen layout itself — unchanged.
- New top-level destinations in the drawer (Prompt Lab, Benchmark) — wired as routes in Plan 40-03 but not surfaced in the drawer yet (they're stubbed Composables for Phase 41/42).

## Accessibility
- Splash background contrast: `#1F1F1E` on `#1F1F1E` (no text on splash) — N/A.
- Dark/light toggle via system settings: TalkBack announces the new theme via standard `onConfigurationChanged` flow.
- Recommended tab cards: existing `Modifier.semantics { contentDescription = ... }` patterns (no change).
