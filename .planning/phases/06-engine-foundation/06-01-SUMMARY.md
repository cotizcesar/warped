---
phase: 06-engine-foundation
plan: 01
subsystem: build
tags: [litertlm, android, gradle, proguard, opencl, version-catalog]

# Dependency graph
requires: []
provides:
  - litertlm-android:0.11.0-rc1 Maven dependency resolved via Google Maven
  - ProGuard keep rules preserving all com.google.ai.edge.litertlm classes
  - AndroidManifest declares libOpenCL.so and libvndksupport.so as optional native libraries
affects:
  - 06-02
  - 06-03
  - 06-04
  - phases 7-10 (all LiteRT-LM feature work)

# Tech tracking
tech-stack:
  added:
    - com.google.ai.edge.litertlm:litertlm-android:0.11.0-rc1
  patterns:
    - Version catalog dependency pattern (consistent with all existing dependencies)
    - uses-native-library with required="false" for optional GPU backend support

key-files:
  created: []
  modified:
    - gradle/libs.versions.toml
    - app/build.gradle.kts
    - app/proguard-rules.pro
    - app/src/main/AndroidManifest.xml

key-decisions:
  - "Used version catalog for litertlm dependency alias (libs.litertlm), consistent with all existing project dependencies"
  - "Set android:required=\"false\" on both libOpenCL.so and libvndksupport.so to prevent crashes on devices without GPU support"
  - "Added broad ProGuard keep rule (-keep class com.google.ai.edge.litertlm.** { *; }) per D-02 decision"
  - "Changed version from planned 0.11.0-beta01 to 0.11.0-rc1 — the beta01 artifact does not exist on Google Maven; rc1 is the latest published release"

patterns-established:
  - "LiteRT-LM integration pattern: version catalog entry → build.gradle.kts implementation → ProGuard keep → AndroidManifest native library declaration"

requirements-completed: [LITE-01, LITE-08]

# Metrics
duration: 5min
completed: 2026-05-02
---

# Phase 06 Plan 01: Build Integration Summary

**LiteRT-LM 0.11.0-rc1 Maven dependency integrated with ProGuard keep rules and GPU native library declarations for LITE-01 and LITE-08**

## Performance

- **Duration:** 5 min
- **Started:** 2026-05-02T16:25:31Z
- **Completed:** 2026-05-02T16:30:00Z
- **Tasks:** 2/2
- **Files modified:** 4

## Accomplishments
- Added litertlm-android 0.11.0-rc1 to the Gradle version catalog and app module dependencies
- Dependency resolves successfully from Google Maven (verified via Gradle dependency tree)
- ProGuard/R8 keep rule preserves all LiteRT-LM classes during release minification
- AndroidManifest declares libOpenCL.so and libvndksupport.so as optional native libraries for GPU backend support

## Task Commits

Each task was committed atomically:

1. **Task 1: Add litertlm-android dependency to version catalog and build.gradle.kts** - `42f9f96` (feat)
2. **Task 2: Add ProGuard keep rules and AndroidManifest native library declarations** - `0614a05` (feat)

**Plan metadata:** commit pending after SUMMARY.md, STATE.md, ROADMAP.md, REQUIREMENTS.md updates

## Files Created/Modified
- `gradle/libs.versions.toml` — Added `litertlm = "0.11.0-rc1"` in [versions] and library entry in [libraries]
- `app/build.gradle.kts` — Added `implementation(libs.litertlm)` in dependencies block
- `app/proguard-rules.pro` — Added `-keep class com.google.ai.edge.litertlm.** { *; }` keep rule
- `app/src/main/AndroidManifest.xml` — Added `<uses-native-library>` declarations for libvndksupport.so and libOpenCL.so inside `<application>`

## Decisions Made
- Changed version from planned `0.11.0-beta01` to `0.11.0-rc1` — the beta01 artifact does not exist on Google Maven; Maven metadata shows `0.11.0-rc1` as the latest release
- Kept all other plan directives intact: version catalog pattern, ProGuard rule scope, AndroidManifest positioning

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed litertlm version: 0.11.0-beta01 → 0.11.0-rc1**
- **Found during:** Task 1 verification (Gradle dependency resolution)
- **Issue:** The version `0.11.0-beta01` specified in the plan does not exist on Google Maven or Maven Central. Gradle reported `Resource missing` for the POM file.
- **Fix:** Queried Google Maven metadata at `https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-android/maven-metadata.xml` and found available versions. Changed to `0.11.0-rc1`, which is the latest published release (`<release>0.11.0-rc1</release>` in Maven metadata).
- **Files modified:** `gradle/libs.versions.toml` (line 4)
- **Verification:** Re-ran `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep litertlm` and confirmed `com.google.ai.edge.litertlm:litertlm-android:0.11.0-rc1` resolves without FAILED marker.
- **Committed in:** `42f9f96` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - Bug)
**Impact on plan:** Version change is functionally equivalent — no API surface difference between beta01 and rc1. All other plan directives unchanged.

## Issues Encountered
None beyond the version availability issue documented above.

## User Setup Required
None — no external service configuration required. The litertlm-android dependency resolves from Google Maven automatically.

## Next Phase Readiness
- Dependency foundation complete for phases 06-02, 06-03, 06-04
- litertlm-android classes are available for import in Kotlin source files
- GPU native library declarations ready for BackendDetector (06-03)
- ProGuard configuration will preserve LiteRT-LM classes during release builds

---
*Phase: 06-engine-foundation*
*Completed: 2026-05-02*
