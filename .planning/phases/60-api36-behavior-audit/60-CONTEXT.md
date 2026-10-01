# Phase 60: API-36 Behavior Audit - Context

**Gathered:** 2026-09-30
**Status:** Ready for planning
**Mode:** Smart discuss (4 areas, user accepted all recommended answers)

<domain>
## Phase Boundary

App runs correctly under Android 16 platform contracts: compileSdk 36 + targetSdk 36
(already set — verify + R8 green), real edge-to-edge on chat pill / bottom sheets /
Fuentes list, predictive-back dismissal everywhere, downloads + offline retry
surviving Android 16 quotas, and adaptive fill on large-screen windows. No bespoke
tablet layouts (out of scope per REQUIREMENTS).

</domain>

<decisions>
## Implementation Decisions

### Edge-to-edge (accepted: per-screen WindowInsets, both navs, both themes)
- Inset strategy: `WindowInsets` consumed per screen (pill input, sheets, Fuentes) — standard M3, not a global Scaffold override, never draw-under-bars.
- Coverage: gesture-nav + 3-button nav verified.
- Themes: light + dark in both nav modes.
- Icon contrast: respect `isAppearanceLightStatusBars` per theme (no forced light icons).

### Predictive back (accepted: Compose BackHandler, full scope, sweep dead paths)
- API: Compose `BackHandler` (rides `OnBackPressedDispatcher`; predictive animation free on 36). No manual `PredictiveBackHandler` progress, no Activity callbacks.
- Scope: chat, sheets (sources/preview), settings/presets, existing wizard handler stays.
- Dead `onBackPressed()` paths: grep sweep, remove/replace (not just the visibly broken ones).
- Gesture and button must dismiss identically (gesture animated, button direct).

### Quotas + downloads (accepted: audit dataSync, log stop reasons, device-verify)
- FGS types: `dataSync` already declared (manifest + both workers) — audit that every download/retry path uses it; no new types unless the audit finds a gap.
- `WorkInfo.getStopReason()`: log in DownloadWorker + BenchmarkWorker, surfaced in progress/retry (not logcat-only, not generic-UI-only).
- Verification under pressure: manual cancel + retry on physical Pixel 8 + emulator (stop job, retry, progress survives).
- v2.3 offline→retry: re-verify under 36 quotas, touch only if broken.

### Large screens (accepted: emulator sw≥600dp, adaptive-fill only, device visuals to UAT)
- Surface: tablet/foldable emulator sw≥600dp (reuse 16 KB x86_64 AVD if healthy, else a plain AVD).
- Visual scope: chat, catalog, sheets fill window — no pillarboxing, no broken constraints.
- No bespoke layouts (`WindowSizeClass` breakpoints are product work — out of scope).
- Device-only visual failures → explicit release-UAT (house precedent), never blocking, never silent.

### the agent's Discretion
- Exact inset padding values per screen; which stop-reason codes map to which retry copy.
- Test structure (which unit tests prove insets/back wiring without device).
- Order of the audit passes within the phase.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `MainActivity.kt:24` — `enableEdgeToEdge()` already called; audit starts from here.
- `ui/wizard/WizardScreen.kt:130` — existing `BackHandler` pattern to replicate.
- `data/local/download/ModelDownloadWorker.kt` (`setForeground` ×3, FGS type line 361) + `data/local/benchmark/ModelBenchmarkWorker.kt` + `BenchmarkNotifier.kt:33` (`FOREGROUND_SERVICE_TYPE_DATA_SYNC`) — quota audit surface.
- `AndroidManifest.xml:6-7,51` — FGS permissions + `dataSync` service type declared.
- `WarpedApplication.newImageLoader` — Coil singleton (sheets/Fuentes image surfaces).

### Established Patterns
- Hilt Singleton providers; StateFlow UI state; Timber logging (RedactingTree — never log key material); English copy only.
- Manual device verification with user on hardware (precedent v2.4 E2B ToolCalls).

### Integration Points
- `app/build.gradle.kts` (compileSdk/targetSdk 36 already set) + release pipeline with R8.
- Sheets used by grounding (sources preview) + OG thumbnails; settings/preset screens; chat pill input.
- WorkManager download + offline-retry (v2.3 message-scoped) paths.

</code>

<specifics>
## Specific Ideas

No specific visual references — user accepted standard M3 approaches throughout.
Verification leans on real devices (user's Pixel 8 on USB + emulators); emulator-only
gaps become release-UAT per precedent.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope. Bespoke tablet layouts explicitly
re-confirmed out of scope (REQUIREMENTS.md).

</deferred>
