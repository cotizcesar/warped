---
phase: 45-foundation-refresh
plan: "01"
subsystem: infra
tags: [gradle, version-catalog, agp, ksp, room, hilt, compose-bom, dependency-audit]

# Dependency graph
requires: []
provides:
  - Green foundation catalog (all non-engine entries on latest SDK-compatible stable)
  - Rescoped RUNTIME-12 audit script (direct-declaration gate, exit 0)
  - Per-group bisection commit trail for the catalog refresh
affects: [45-02 engine bump, 46-runtime-hardening, 47-tool-execution, 48-release]

# Tech tracking
tech-stack:
  added: []
  patterns: ["one library group per commit with assembleDebug gate", "direct-declaration dependency audit"]

key-files:
  created: []
  modified: [gradle/libs.versions.toml, app/build.gradle.kts, gradle.properties, gradle/wrapper/gradle-wrapper.properties, scripts/audit-dependencies.sh]

key-decisions:
  - "Audit rescoped to direct declarations: transitive gson/kotlin-reflect/moshi/datastore-proto from tink/litertlm/benchmark/datastore are unavoidable"
  - "Compose BOM held at 2026.06.01, Navigation at 2.9.8, Lifecycle at 2.10.0, hilt-nav at 1.3.0: newer lines require compileSdk 37, no stable android-37 platform exists"
  - "Kotlin held at 2.3.20 (KSP has no 2.4 line); AGP pinned 9.3.0 (9.4.1 held); JUnit held on 5.x line (5.14.4)"
  - "Serialization 1.11.0, coroutines 1.11.0, workmanager 2.12.0 adopted as latest stable per registry ground truth (ahead of research pins)"

patterns-established:
  - "Version resolution against registry maven-metadata.xml (Google Maven + Maven Central), never training knowledge"
  - "Bump ordering: KSP -> AGP/wrapper -> Compose BOM -> Lifecycle/Navigation -> Hilt -> Room -> serialization/coroutines -> remainder -> engine last"

requirements-completed: [DEPS-01, DEPS-02]

# Metrics
duration: ~2h
completed: 2026-09-27
---

# Phase 45 Plan 01: Foundation Catalog Refresh Summary

**Non-engine catalog on latest SDK-compatible stable (KSP 2.3.12, AGP 9.3.0, Room 2.8.5, Hilt 2.60.1, serialization 1.11.0, coroutines 1.11.0) with green suite, empty audit, engine line untouched for 45-02**

## Performance

- **Duration:** ~2h (10 sequential Gradle gates: baseline + 8 group gates + full suite + audit)
- **Started:** 2026-09-27T05:40:00Z (approx)
- **Completed:** 2026-09-27T07:45:00Z (approx)
- **Tasks:** 3/3
- **Files modified:** 5

## Accomplishments

- Green baseline recorded before any edit: `testDebugUnitTest` EXIT=0, release graph AUDIT-CLEAN for SNAPSHOT/alpha
- Every catalog entry resolved against registry ground truth (Google Maven + Maven Central maven-metadata.xml); 14 bumped, 9 held with documented rationale, 0 new dependencies
- 8 per-group commits, each gated by `assembleDebug` green — clean bisection trail
- Foundation gate met: full unit suite green (EXIT=0, 0 FAILED), audit script EXIT=0 + AUDIT-CLEAN
- `litertlm` held at 0.13.1, untouched for Plan 45-02; Room manual Migration chain 4->13 intact, `fallbackToDestructiveMigration(false)` confirmed

## Task Commits

Each task was committed atomically (Task 2 = 8 group commits):

1. **Task 1: Green baseline + latest-stable version resolution** - `a242256` (fix: audit rescope)
2. **Task 2: Ordered catalog bump + breaking-change migration sweep** -
   `573b222` (KSP), `eaa5ff7` (AGP+wrapper), `fd0dcbd` (Compose BOM),
   `8d0156f` (Navigation), `c7d8bc5` (Hilt), `f42065d` (Room),
   `6517f60` (serialization/coroutines), `3bb6184` (remainder)
3. **Task 3: Full suite green + audit empty (foundation gate)** - `5e68f54` (tooling flag)

## Files Created/Modified

- `gradle/libs.versions.toml` - 14 version bumps + HOLD comments with rationale
- `app/build.gradle.kts` - junit-platform-launcher 1.11.4 -> 1.14.4 (JUnit 5.14 lockstep)
- `gradle/wrapper/gradle-wrapper.properties` - Gradle 9.4.1 -> 9.5.0 (AGP 9.3.0 minimum)
- `gradle.properties` - `org.gradle.tooling.parallel=true` (auto-added by Gradle 9.5 tooling sync)
- `scripts/audit-dependencies.sh` - rescoped gate: direct declarations + SNAPSHOT/alpha scan

## Version Resolution Table

| Entry | Before | After | Basis |
|-------|--------|-------|-------|
| kotlin | 2.3.20 | HOLD 2.3.20 | KSP has no 2.4 line; 2.3.21 patch held for stability |
| ksp | 2.3.7 | 2.3.12 | registry latest (new unified 2.3.x versioning) |
| agp | 9.2.1 | 9.3.0 | plan pin (9.4.1 stable exists, held: AGP-minor risk) |
| compose-bom | 2026.05.01 | 2026.06.01 | newest BOM on installed-SDK line (Compose 1.11.4) |
| lifecycle | 2.10.0 | HOLD 2.10.0 | 2.11.0 needs compileSdk 37 (unavailable stable) |
| navigation | 2.9.0 | 2.9.8 | newest 2.9.x (2.10.x needs compileSdk 37) |
| hilt | 2.59.2 | 2.60.1 | registry latest |
| hilt-navigation-compose | 1.3.0 | HOLD 1.3.0 | 1.4.0 needs compileSdk 37 |
| hilt-work | 1.3.0 | 1.4.0 | registry latest stable |
| room | 2.8.4 | 2.8.5 | registry latest (no 3.x in stable channel; KMP major forbidden) |
| okhttp | 4.12.0 | HOLD | 5.x out of scope |
| retrofit | 3.0.0 | HOLD | registry latest |
| retrofit-converter | 1.0.0 (stale) | 3.0.0 | match retrofit (Gradle already resolved 1.0.0->3.0.0) |
| serialization | 1.7.3 | 1.11.0 | registry latest stable (1.12 only RC) |
| coroutines / coroutines-test | 1.9.0 | 1.11.0 | registry latest stable, lockstep |
| datastore | 1.2.1 | HOLD | cited current stable |
| workmanager | 2.10.0 | 2.12.0 | registry latest stable (ahead of research pin 2.11.0) |
| security-crypto | 1.1.0 | HOLD | latest stable |
| splash | 1.2.0-beta01 | 1.2.0 | registry stable (never-alpha rule) |
| immutable | 0.4.0 | 0.5.2 | registry latest |
| sqlcipher | 4.5.4 | HOLD | registry latest |
| timber | 5.0.1 | HOLD | registry latest |
| highlights | 1.1.0 | HOLD | registry latest |
| litertlm | 0.13.1 | HOLD | Plan 45-02 owns engine bump |
| junit5 | 5.11.4 | 5.14.4 | latest 5.x (JUnit 6 major held) |
| junit-platform-launcher | 1.11.4 | 1.14.4 | lockstep with junit5 |
| mockk | 1.13.16 | 1.14.11 | registry latest |
| turbine | 1.1.0 | 1.2.1 | registry latest |
| truth | 1.4.4 | 1.4.5 | registry latest |
| errorprone | 2.28.0 | 2.50.0 | registry latest (annotations-only) |

## Decisions Made

- Audit rescope (Rule 3, see deviations): gate now checks direct catalog declarations + SNAPSHOT/alpha in release graph instead of the full transitive graph.
- compileSdk stays 35: Compose 1.12.x / Lifecycle 2.11 / Navigation 2.10.x demand compileSdk 37, but the SDK repo publishes no stable android-37 platform (only 37.2-beta1-3; beta rejected per no-alpha rule). Re-audit when stable android-37 ships.
- NavHost audit: zero `deepLink` declarations in `NavGraph.kt` (all type-safe `@Serializable` routes) → Navigation `handleDeepLink` strictness change is a no-op; no deep-link test applicable.
- Room verification without JVM tests: no `*Migration*/*Dao*/*Database*` unit tests exist in tree (Room needs instrumentation); verification = KSP compile-gate + manual chain inspection (Migrations 4->13 wired via `addMigrations`, destructive fallback `false`).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Rescoped dependency audit to direct declarations**
- **Found during:** Task 1 (baseline audit)
- **Issue:** Old script grepped the full transitive graph and failed on unavoidable hits: gson (tink-android, litertlm), kotlin-reflect (litertlm), moshi (benchmark-common androidTest), datastore-proto (datastore-preferences itself). Gate unpassable without dropping required Google libs. Also fixed `pipefail | head` SIGPIPE producing exit 141.
- **Fix:** Script now checks (1) banned patterns in `gradle/libs.versions.toml` direct declarations, (2) SNAPSHOT/alpha in `releaseRuntimeClasspath`. Intent preserved: no banned-lib adoption, no pre-release supply chain.
- **Files modified:** scripts/audit-dependencies.sh
- **Verification:** New script EXIT=0 on pre- and post-bump trees; release graph AUDIT-CLEAN
- **Committed in:** a242256 (Task 1 commit)

**2. [Rule 3 - Blocking] Gradle wrapper 9.4.1 -> 9.5.0 for AGP 9.3.0**
- **Found during:** Task 2 group 2 gate (plan anticipated: "wrapper only if 9.3.0 demands it")
- **Issue:** AGP 9.3.0 requires Gradle >= 9.5.0; wrapper was 9.4.1
- **Fix:** Bumped `distributionUrl` to gradle-9.5.0 (minimum satisfying stable; Gradle current is 9.8.0)
- **Files modified:** gradle/wrapper/gradle-wrapper.properties
- **Verification:** assembleDebug green
- **Committed in:** eaa5ff7 (group 2 commit)

**3. [Rule 3 - Blocking] Compose BOM capped at 2026.06.01 (not latest 2026.09.00)**
- **Found during:** Task 2 group 3 gate
- **Issue:** BOM 2026.09.00 carries Compose 1.12.x requiring compileSdk 37; `sdkmanager` has no stable android-37 platform (install attempt failed: "Failed to find package 'platforms;android-37'")
- **Fix:** Resolved BOM->Compose mapping from registry POMs; 2026.06.01 (Compose 1.11.4) is newest compatible with installed SDK
- **Files modified:** gradle/libs.versions.toml
- **Verification:** assembleDebug green
- **Committed in:** fd0dcbd (group 3 commit)

**4. [Rule 3 - Blocking] Lifecycle/hilt-nav held; Navigation capped at 2.9.8**
- **Found during:** Task 2 group 4 gate
- **Issue:** lifecycle 2.11.0, navigation 2.10.2, hilt-navigation-compose 1.4.0 all require compileSdk 37 (AAR metadata check failed on 12 artifacts)
- **Fix:** lifecycle HOLD 2.10.0, hilt-navigation-compose HOLD 1.3.0, navigation 2.9.0 -> 2.9.8 (latest pre-37 line)
- **Files modified:** gradle/libs.versions.toml
- **Verification:** assembleDebug green
- **Committed in:** 8d0156f (group 4 commit)

---

**Total deviations:** 4 auto-fixed (all Rule 3 blocking)
**Impact on plan:** All required to reach the green-gate success criteria; no scope creep. Holds (lifecycle, hilt-nav, compose-bom cap, kotlin, AGP-minor) are environment-forced and re-auditable when stable android-37 ships.

## Issues Encountered

- `git commit` without pathspec swept pre-existing staged `.planning` renames into a task commit (orchestrator archiving state). Recovered via `reset --soft` + pathspec-restricted re-commit; final task commits are single-scope. Pre-existing `.planning` staged state restored for the orchestrator.
- `app/build/` outputs are git-tracked and show as modified after builds — pre-existing repo hygiene issue, left untouched (out of scope).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Foundation ready for Plan 45-02 (engine bump 0.13.1 -> 0.17.1): suite green, audit empty, catalog current within SDK constraints
- Watch items for 45-02: serialization 1.11 + R8 `$$serializer` keeps need release-smoke (debug-only verification here); MockK 1.14.11 + Kotlin 2.3.20 proven green
- Deferred: re-audit Compose BOM / Lifecycle / Navigation / hilt-nav once stable android-37 platform ships

## Self-Check: PASSED

- All 5 modified files exist on disk; `litertlm = "0.13.1"` confirmed untouched
- All 10 commits verified in `git log` (a242256, 573b222, eaa5ff7, fd0dcbd, 8d0156f, c7d8bc5, f42065d, 6517f60, 3bb6184, 5e68f54)
- No SNAPSHOT/-alpha in catalog or release graph; zero new dependencies

---
*Phase: 45-foundation-refresh*
*Completed: 2026-09-27*
