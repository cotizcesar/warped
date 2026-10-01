# Phase 61 Plan 01: LeakCanary Harness + Tour Script — Summary

**One-liner:** LeakCanary 2.14 installed as debugImplementation-only (release proven clean at classpath + dex level) with a 6-leg replayable LEAK-TOUR.md for the 61-02 device session.
**Status:** Complete — harness + script only, zero production code changed, no leaks fixed (Phase 62 owns fixes).

## Tasks completed (3/3)

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Install LeakCanary 2.14 as debugImplementation | `fe40548f` | gradle/libs.versions.toml, app/build.gradle.kts |
| 2 | Prove zero release footprint (evidence only, no files) | — | none |
| 3 | Write scripted leak-tour document | `f0038bf3` | .planning/phases/61-leakcanary-guided-audit/LEAK-TOUR.md |

## What was built

- **Version catalog:** `leakcanary = "2.14"` in `[versions]` + `leakcanary-android = { group = "com.squareup.leakcanary", name = "leakcanary-android", version.ref = "leakcanary" }` in `[libraries]`.
- **Gradle dep:** `debugImplementation(libs.leakcanary.android)` next to the existing debugImplementation precedent (app/build.gradle.kts:172). No `implementation`, no release reference, no init code / Application changes (auto-installs via ContentProvider).
- **LEAK-TOUR.md** (181 lines): 6 scripted legs — (1) model load/switch/unload, (2) streaming chat + Stop, (3) 5-URL grounding + cancel, (4) offline→retry, (5) OG thumbnail scroll (Fuentes/Endpoints list), (6) rotation + process death. Includes Pixel 8 preamble (`ANDROID_SERIAL=37141FDJH0065Y`, adb path, emulator-5554 excluded), LeakCanary check steps, result table, explicit deferral template. Tour NOT run — belongs to 61-02's human checkpoint.

## Verification

- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL (44 s).
- Release-footprint proof (Task 2), all commands run in repo root:
  - `./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep -ci leakcanary` → **0**
  - Control: `debugRuntimeClasspath` shows `leakcanary-android:2.14` + shark transitives (present where expected).
  - `unzip -l app-release.apk | grep -ci leakcanary` → **0**
  - Per-dex `strings | grep -c com/squareup/leakcanary` on release APK → **0 on every dex file**
  - Control: same dex-strings check on debug APK → **positive (2 + 85 matches)**, proving the method detects LeakCanary when present.
  - `./gradlew :app:assembleRelease` — BUILD SUCCESSFUL (unsigned, existing keystore config untouched).
- Tour doc check: keyword grep across all six legs → 24 hits; 181 lines ≥ 60-line minimum.
- Unit tests: `./gradlew :app:testDebugUnitTest` — **894 tests, 0 failures, 0 errors** (baseline holds).

## Threat posture (T-61-01/02/03)

- Debug-only wiring keeps heap dumps off release: release APK proven dump-free at three levels (classpath, zip listing, dex strings).
- Supply-chain: Square-published artifact pinned to 2.14, Gradle/Maven outside the npm/pip/cargo legitimacy-gate scope; no LeakCanary code ships in release.
- No heap dumps pulled, committed, or uploaded; dumps stay on the dev device per tour instructions.

## Deviations

None — plan executed exactly as written. Out-of-scope working-tree noise observed but untouched (other agents'/phases' files: `model_allowlist.json`, `ModelAllowlistTest.kt`, phase-60 summaries, generated `app/build` churn, `.planning/state.json`).
