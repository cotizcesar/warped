---
phase: 48-chat-perf-startup-release
plan: "02"
subsystem: startup-baseline-profiles
tags: [perf-16, baseline-profile, cold-start, macrobenchmark, splash, startup-audit]
requires: [phase-47-tool-ui, perf-12-benchmarks]
provides: [baseline-profile-release-setup, startup-audit-evidence, benchmarks-perf16-target]
affects: [48-03-release-hardening]
tech-stack:
  added: [androidx.profileinstaller-1.4.1, androidx.baselineprofile-plugin-1.5.0, benchmark-1.5.0]
  patterns: [BaselineProfileRule-critical-journey, lazy-native-binding, HS-flagged-profile-rules]
key-files:
  created:
    - app/src/androidTest/java/com/warped/benchmark/BaselineProfileGenerator.kt
    - app/src/main/baselineProfiles/baseline-prof.txt
  modified:
    - gradle/libs.versions.toml
    - app/build.gradle.kts
    - build.gradle.kts
    - app/src/androidTest/java/com/warped/benchmark/ColdStartBenchmark.kt
    - app/src/androidTest/java/com/warped/benchmark/StreamingFrameBenchmark.kt
    - app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt
    - BENCHMARKS.md
decisions:
  - "Baseline-profile plugin + benchmark libs pinned at 1.5.0, not 1.3.3 (AGP 9 incompatibility proven by build)"
  - "profileinstaller pinned at Maven-confirmed latest stable 1.4.1"
  - "Checked-in baseline-prof.txt is a 4-rule hand-seeded startup-path seed; full method profile TODO via generator on proper hardware"
  - "Native loadLibrary moved from class-load to first init() (plan-sanctioned eager-init fix)"
metrics:
  duration: ~50m (mostly Gradle: 3 assembleRelease + 1 androidTest compile + audit gate)
  completed: 2026-09-28
  tasks: 2/2 auto complete, 1/1 checkpoint pending (blocking-human)
---

# Phase 48 Plan 02: Startup + Baseline Profiles Summary

Baseline Profiles now ship in release (profileinstaller 1.4.1 + baselineprofile plugin 1.5.0 + generator rule + checked-in seed profile, all verified by a successful `assembleRelease` with the profile embedded in the APK), the startup path is audited with file:line evidence (one real eager-init defect found and fixed), splash re-verified at exactly 200ms with no code change needed, and BENCHMARKS.md normatively targets <1s with a hardware-blocked measurement TODO. Cold-start measurement is pending at the blocking checkpoint: a device IS attached (emulator-5554) but it is a user build without root, so controlled compilation reset / profile collection cannot run there.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | Add Baseline Profile setup (profileinstaller + generator + checked-in profile) | 4f5dbf1 | libs.versions.toml, app/build.gradle.kts, build.gradle.kts, BaselineProfileGenerator.kt, baseline-prof.txt |
| 2 | Startup-path audit plus BENCHMARKS target correction | 23548d9 | ColdStartBenchmark.kt, StreamingFrameBenchmark.kt, BENCHMARKS.md, LiteRTLmEngine.kt |

## Version Confirmations (Maven-verified 2026-09-28)

- `androidx.profileinstaller:profileinstaller` **1.4.1** — `<latest>`/`<release>` in
  `dl.google.com/dl/android/maven2/androidx/profileinstaller/profileinstaller/maven-metadata.xml`.
  Resolves in `releaseRuntimeClasspath` as `1.4.1` (upgraded from 1.3.0/1.4.0 transitives).
- `androidx.benchmark:benchmark-baseline-profile-gradle-plugin` **1.5.0** — latest stable in its
  maven-metadata (1.5.0, Sept 2026). Plugin 1.3.3 (plan assumption) **cannot apply under AGP 9.3.0**
  — proven by build failure, fixed by upgrade (see deviations).
- `benchmark-macro-junit4` / `benchmark-junit4` bumped 1.3.3 → **1.5.0** to track the plugin.

## Verification Evidence

- `:app:assembleRelease` — **BUILD SUCCESSFUL** (56 tasks). `compileReleaseArtProfile` ran the seed
  profile through AGP validation; `packageRelease` signed with a local throwaway keystore at
  `app/keystore/warped-release.jks` (gitignored, local-only — the real release keystore is absent here).
- APK embeds the profile: `assets/dexopt/baseline.prof` (10668 B) + `assets/dexopt/baseline.profm`
  present in `app/build/outputs/apk/release/app-release.apk`.
- `:app:compileDebugAndroidTestKotlin` — **BUILD SUCCESSFUL** (generator + both benchmarks compile
  against benchmark 1.5.0; only warning is a pre-existing deprecation in `MarkdownTextTest.kt`,
  untouched, out of scope).
- `scripts/audit-dependencies.sh` — **OK**: no banned direct deps, no pre-release artifacts in the
  release graph (profileinstaller/plugin additions are stable first-party AndroidX).
- `grep -rn profileinstaller app/build.gradle.kts gradle/libs.versions.toml` — present in both.

## Startup-Path Audit Evidence (Task 2a)

| Item | Finding | Evidence |
|------|---------|----------|
| ProviderRouter laziness | PASS — all three helpers `dagger.Lazy`, never constructed at startup | `data/remote/provider/ProviderRouter.kt:20-24` |
| Application.onCreate | PASS — notification channel + uncaught-exception handler + DEBUG-only Timber/StrictMode; no engine, network, or DB work | `WarpedApplication.kt:33-59` |
| LiteRTLmEngine construction | **FAIL found, FIXED** — `companion object init {}` ran `System.loadLibrary("litertlm_jni")` at class-load, and Hilt constructs the engine graph node at Application creation via the eager `engineManager` field (`WarpedApplication.kt:23`) → native dlopen on the cold-start path | `LiteRTLmEngine.kt:24-31` (before fix) |
| EngineManager/model load | PASS post-fix — `switchToLiteRT`/mmap only runs on first model load (`EngineManager.kt:51-95`); nothing before first sendMessage |
| Remaining eager construction | ACCEPTED (negligible) — `LiteRtLmCacheManager` ctor does one `mkdirs` (`LiteRtLmCacheManager.kt:20-22`); `ModelAllowlistRepository` asset read is `by lazy` (`ModelAllowlistRepository.kt:72`); `BackendDetector` ctor is empty. Full `Lazy<EngineManager>` deferral in Application is architectural follow-up, not this plan. |
| Splash (48-UI-SPEC §5 binding) | PASS, no change — `setKeepOnScreenCondition { false }` (`MainActivity.kt:25`), 200ms alpha fade with `AccelerateInterpolator` + `remove()` on end (`MainActivity.kt:27-38`), dark `window_background #FF1F1F1E` (`colors.xml:4`, no white flash), `setContent` (chat destination) composed synchronously at `MainActivity.kt:39` before the fade |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Baseline-profile plugin 1.3.3 cannot apply under AGP 9.3.0**
- **Found during:** Task 1 (`assembleRelease` failed at configuration: `Failed to apply plugin 'androidx.baselineprofile' ... Module ':app' is not a supported android module` — plugin 1.3.3 uses the legacy AGP extension API removed in AGP 9)
- **Fix:** Pinned plugin + both benchmark libs at Maven-confirmed latest stable **1.5.0**; release build then succeeds
- **Files modified:** `gradle/libs.versions.toml`, `app/build.gradle.kts`
- **Commit:** 4f5dbf1

**2. [Rule 3 - Blocking] AGP validates baseline-prof.txt strictly — method rules require H/S/P flags**
- **Found during:** Task 1 (`expandReleaseArtProfileWildcards` failed: `At least one of flags 'H', 'S', 'P' must be specified for a method rule`)
- **Fix:** Seed method rules flagged `HS` (hot + startup); class rules need no flags
- **Files modified:** `app/src/main/baselineProfiles/baseline-prof.txt`
- **Commit:** 4f5dbf1

**3. [Rule 2 - Missing critical] Eager native load on the startup path (plan-sanctioned: "unless the audit finds eager init")**
- **Found during:** Task 2 audit (see table above)
- **Fix:** Moved `System.loadLibrary` + `setNativeMinLogSeverity` from `companion object init` to a synchronized `ensureNativeLoaded()` called first in `init()` — class construction is now pure-Java; dlopen happens on first model load, never on cold start. Failure semantics preserved (load failure still logged, native use still throws downstream)
- **Files modified:** `app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt` (outside plan `files_modified` — plan-sanctioned fix)
- **Commit:** 23548d9

**4. [Rule 1 - Bug] Benchmarks target the wrong package (`com.warped` vs applicationId `com.warped.app`)**
- **Found during:** Task 2 (macrobenchmark would fail to resolve the target package)
- **Fix:** `packageName = "com.warped.app"` in `ColdStartBenchmark.kt` (in-scope), `StreamingFrameBenchmark.kt` (same one-line bug, outside plan `files_modified` — fixed so the documented verification commands work), and the new generator uses `com.warped.app`
- **Commit:** 23548d9

**5. [Rule 3 - Blocking] Root `build.gradle.kts` needed the plugin's `apply false` declaration**
- **Fix:** Added `alias(libs.plugins.baselineprofile) apply false` (also fixed a `#`→`//` comment-syntax slip caught by the build)
- **Files modified:** `build.gradle.kts` (outside plan `files_modified` — build-required)
- **Commit:** 4f5dbf1

## Authentication Gates

None.

## Known Stubs

- `app/src/main/baselineProfiles/baseline-prof.txt` is a deliberate 4-rule **seed** (2 manifest-verified
  cold-start classes + their `onCreate` methods, `HS`-flagged) — NOT a full method profile. It is
  valid, build-passing, and marginally beneficial (precompiles the true launch path), but the real
  journey-derived profile must replace it via `BaselineProfileGenerator` on proper hardware
  (checkpoint Task 3). This is intentional and tracked, not an oversight.

## Threat Flags

None — no new security surface. Profile supply chain per T-48-05/T-48-04: seed is hand-written from
codebase-verified entry points, checked into git, reviewed like code, never fetched externally.
T-48-06 accepted via the explicit emulator-vs-Pixel note now in BENCHMARKS.md §1.

## Hardware Finding (for checkpoint Task 3)

Contrary to RESEARCH Environment Availability: `adb` EXISTS (`/home/cotizcesar/Android/Sdk/platform-tools/adb`)
and `emulator-5554` is attached and booted (API 37, x86_64, 1080x2400). BUT it is a **user build**
(`ro.build.type=user`, `ro.debuggable=0`, `adbd cannot run as root`) → `cmd package compile` reset and
BaselineProfileRule collection cannot control ART state there. Per plan instruction ("do NOT attempt
here"), no device runs were attempted. Exact commands + environment block are recorded in
BENCHMARKS.md §1 for a rooted emulator / CI (Pixel 7 reference still TODO).

## Self-Check

- FOUND: app/src/androidTest/java/com/warped/benchmark/BaselineProfileGenerator.kt
- FOUND: app/src/main/baselineProfiles/baseline-prof.txt
- FOUND: 4f5dbf1 (`git log` — feat(48-02) Baseline Profile setup)
- FOUND: 23548d9 (`git log` — fix(48-02) startup audit + target correction)
- No file deletions in either commit; `app/keystore/` untracked-but-ignored (local throwaway signing key only)

## Self-Check: PASSED
