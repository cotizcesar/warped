---
phase: 53-sources-preview-per-chat-toggle
plan: "01"
subsystem: database
tags: [room, migration, grounded-sources, tri-state-override, chat-repository]

# Dependency graph
requires:
  - phase: 52-multi-url-fetch-foundation
    provides: Fan-out snapshot shape (ok/omitida per-source) this plan persists
provides:
  - v15 store (conversations.web_override + grounded_sources, one MIGRATION_14_15)
  - Domain contracts (GroundedSource, ChatMessage.groundedSourceDetails, Conversation.webOverride, ChatRepository source/override signatures)
  - MigrationTest v14→v15 gate (device run pending)
affects: [53-02 (repository impl + hydration), 53-03 (UI sheet + toggle), 54-offline-retry (reads persisted rows)]

# Tech tracking
tech-stack:
  added: []
  patterns: [manual Room Migration carrying column + table, drop-unknown status mapping, interface-first repository contracts]

key-files:
  created:
    - app/src/main/java/com/warped/domain/model/GroundedSource.kt
    - app/src/main/java/com/warped/data/local/db/entity/GroundedSourceEntity.kt
    - app/src/main/java/com/warped/data/local/db/dao/GroundedSourceDao.kt
  modified:
    - app/src/main/java/com/warped/domain/model/ChatMessage.kt
    - app/src/main/java/com/warped/domain/model/Conversation.kt
    - app/src/main/java/com/warped/domain/repository/ChatRepository.kt
    - app/src/main/java/com/warped/data/local/db/entity/ConversationEntity.kt
    - app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt
    - app/src/main/java/com/warped/data/local/db/dao/ConversationDao.kt
    - app/src/main/java/com/warped/data/local/db/Migrations.kt
    - app/src/main/java/com/warped/data/local/db/AppDatabase.kt
    - app/src/main/java/com/warped/di/DatabaseModule.kt
    - app/src/androidTest/java/com/warped/data/local/db/MigrationTest.kt
    - app/build.gradle.kts

key-decisions:
  - "ChatRepositoryImpl carries NotImplementedError stubs for the 4 new methods; 53-02 owns the real implementation"
  - "toGroundedSourceStatusSafe uses if/else (K2 parser rejected the when form); semantics identical (ok -> OK, else OMITIDA)"
  - "androidTest assets srcDirs wired to app/schemas so MigrationTestHelper resolves 15.json on device"

patterns-established:
  - "Single-migration DDL bundling: MIGRATION_14_15 carries column + table + index together"
  - "Drop-unknown mapping for untrusted stored TEXT columns (status -> OMITIDA, same precedent as toRoleSafe)"

requirements-completed: [SRC-01, TOGGLE-01, TOGGLE-03]

# Metrics
duration: 25min
completed: 2026-09-28
---

# Phase 53 Plan 01: Persistence + Contracts Summary

**v15 Room store (nullable web_override + grounded_sources with FK CASCADE) behind one MIGRATION_14_15, plus interface-first domain contracts (GroundedSource details, tri-state override, repository signatures) ready for 53-02/53-03**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-09-28T16:25Z (approx)
- **Completed:** 2026-09-28T16:50:17Z
- **Tasks:** 3/3
- **Files modified:** 14 (3 created domain/data, 11 modified incl. schema + test + build)

## Accomplishments

- GroundedSource domain model (url + extractedText + OK/OMITIDA status) with ChatMessage.groundedSourceDetails ephemeral list and Conversation.webOverride tri-state (null = inherit global default-ON)
- GroundedSourceEntity + GroundedSourceDao (insertAll REPLACE, getByMessage ordered by source_index) with FK CASCADE messages→sources; ConversationDao get/setWebOverride; EntityMappers drop-unknown status + override mapping
- Exactly one Migration(14, 15): nullable web_override (no default) + grounded_sources table + index; AppDatabase v15; registered in DatabaseModule (destructive fallback stays false); 15.json schema verified (FK CASCADE, web_override column present)
- MigrationTest v14→v15 gate: rows survive, override NULL default, sources start empty, ok/omitida round-trip in order, null/0/1 round-trip, transitive cascade delete

## Task Commits

Each task was committed atomically:

1. **Task 1: Domain contracts + grounded_sources entity and DAO** - `b2cc5e0` (feat)
2. **Task 2: Single MIGRATION_14_15 plus version bump and registration** - `a1ff03a` (feat)
3. **Task 3: MigrationTest extension gating v15 plus restart-persistence assertions** - `0a8f4a7` (feat)

## Files Created/Modified

- `app/src/main/java/com/warped/domain/model/GroundedSource.kt` - NEW: url, extractedText?, OK/OMITIDA status
- `app/src/main/java/com/warped/data/local/db/entity/GroundedSourceEntity.kt` - NEW: message_id FK CASCADE, source_index, resolved_url, extracted_text?, status TEXT
- `app/src/main/java/com/warped/data/local/db/dao/GroundedSourceDao.kt` - NEW: insertAll + getByMessage ordered ASC
- `app/src/main/java/com/warped/domain/model/ChatMessage.kt` - + groundedSourceDetails (ephemeral, default emptyList)
- `app/src/main/java/com/warped/domain/model/Conversation.kt` - + webOverride Boolean? (default null)
- `app/src/main/java/com/warped/domain/repository/ChatRepository.kt` - + saveMessageWithSources/Long, getSourcesByMessage, get/setWebOverride signatures
- `app/src/main/java/com/warped/data/repository/ChatRepositoryImpl.kt` - stubs for 4 new methods (53-02 implements)
- `app/src/main/java/com/warped/data/local/db/entity/ConversationEntity.kt` - + webOverride Boolean? column
- `app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt` - source entity/domain mapping, drop-unknown status, override mapping
- `app/src/main/java/com/warped/data/local/db/dao/ConversationDao.kt` - + getWebOverride/setWebOverride
- `app/src/main/java/com/warped/data/local/db/Migrations.kt` - + MIGRATION_14_15 (both DDLs + index)
- `app/src/main/java/com/warped/data/local/db/AppDatabase.kt` - version 15, entity + DAO accessor
- `app/src/main/java/com/warped/di/DatabaseModule.kt` - migration registration + GroundedSourceDao provider
- `app/schemas/com.warped.data.local.db.AppDatabase/15.json` - NEW exported schema (verified contents)
- `app/src/androidTest/java/com/warped/data/local/db/MigrationTest.kt` - migrate14To15 gate test
- `app/build.gradle.kts` - androidTest assets srcDirs -> app/schemas

## Decisions Made

- ChatRepositoryImpl stub methods throw NotImplementedError with "53-02 implements ..." messages — keeps Task 1's compile gate green without pre-empting 53-02's implementation choices (constructor injection shape is 53-02's call).
- Status mapper written as if/else instead of when (see deviations); "omitida" needs no explicit leg since the else covers it.
- Wired `androidTest.assets.srcDirs(schemas)` — without it MigrationTestHelper cannot resolve any exported schema on device (pre-existing gap affecting even the old migrate11To12 test).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] ChatRepositoryImpl stubs for new interface methods**
- **Found during:** Task 1 (Domain contracts + entity and DAO)
- **Issue:** Plan extends the ChatRepository interface but assigns implementation to 53-02; without overrides in ChatRepositoryImpl, compileDebugKotlin fails and Task 1's done gate cannot pass.
- **Fix:** Added 4 override stubs throwing NotImplementedError("53-02 implements ..."); no behavior change, no DI change.
- **Files modified:** app/src/main/java/com/warped/data/repository/ChatRepositoryImpl.kt
- **Verification:** ./gradlew :app:compileDebugKotlin BUILD SUCCESSFUL
- **Committed in:** b2cc5e0 (part of Task 1 commit)

**2. [Rule 1 - Bug] Rewrote status mapper from when to if/else**
- **Found during:** Task 1 (compile gate)
- **Issue:** K2 compiler rejected `when (lowercase())` and `when { equals(...) }` forms in toGroundedSourceStatusSafe with "Expecting a when-condition" syntax errors (file verified clean, no stray characters; enum-when elsewhere in the same file compiles).
- **Fix:** Equivalent if/else: ok (ignoreCase) -> OK, everything else -> OMITIDA. Drop-unknown semantics unchanged.
- **Files modified:** app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt
- **Verification:** ./gradlew :app:compileDebugKotlin BUILD SUCCESSFUL
- **Committed in:** b2cc5e0 (part of Task 1 commit)

**3. [Rule 3 - Blocking] Wired Room schemas as androidTest assets**
- **Found during:** Task 3 (MigrationTest extension)
- **Issue:** MigrationTestHelper resolves exported schemas from androidTest assets, but no assets wiring exists (no srcDirs, no copied JSONs) — runMigrationsAndValidate would throw "schema not found" on device even after assembleDebug emits 15.json. Pre-existing gap: the old migrate11To12 test has the same latent failure.
- **Fix:** `sourceSets { getByName("androidTest").assets.srcDirs(files("$projectDir/schemas")) }` — asset path com.warped.data.local.db.AppDatabase/15.json matches the helper's canonical-name lookup.
- **Files modified:** app/build.gradle.kts
- **Verification:** :app:compileDebugAndroidTestKotlin BUILD SUCCESSFUL (device run pending — no adb device attached)
- **Committed in:** 0a8f4a7 (part of Task 3 commit)

---

**Total deviations:** 3 auto-fixed (2 blocking, 1 bug)
**Impact on plan:** All three required for the plan's own verification gates to pass; no scope creep, zero new dependencies.

## Issues Encountered

- No adb device/emulator attached: `connectedDebugAndroidTest` could not run. androidTest source set compiles; the migrate14To15 device run is recorded as PENDING for the phase exit gate (per plan's fallback instruction).
- K2 `when`-form rejection in one mapper function (see deviation 2) — isolated, worked around, no wider impact (enum-when in the same file compiles).

## Threat Flags

None — no new surface beyond the plan's threat model. T-53-01 mitigated (drop-unknown status mapping), T-53-02 by construction (entity carries resolved_url only), T-53-03 inherits SQLCipher (no new crypto code), T-53-04 mitigated (manual migration registered, destructive fallback false), T-53-SC clean (zero new deps).

## Known Stubs

- `ChatRepositoryImpl.saveMessageWithSources / getSourcesByMessage / getWebOverride / setWebOverride` throw NotImplementedError — intentional interface-first scaffolding; 53-02 implements them (tracked in plan wave 2, not a defect).

## Verification Results

- `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL, 15.json emitted and contents verified (grounded_sources + FK CASCADE + web_override nullable)
- `./gradlew :app:compileDebugAndroidTestKotlin` — BUILD SUCCESSFUL (deprecation warning only, pre-existing)
- `./gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL, 256 tests, 0 failures, 0 errors
- Exactly one `Migration(14, 15)` in tree; no DAO imports in UI layer (grep clean)
- `connectedDebugAndroidTest --tests MigrationTest` — PENDING (no device attached)

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- 53-02 ready: contracts compile; implement the 4 repository methods (inject GroundedSourceDao into ChatRepositoryImpl), persist rows post-fetch/pre-inference, hydrate details + groundedSources on loadConversation. Watch Pitfall 1 (REPLACE re-insert wipes sources) and Pitfall 2 (persist ok + omitida in block order, thread texts through Fused).
- 53-03 ready: GroundedSource details list + webOverride flow into MessageBubble sheet and tri-state control.
- Blocker for phase exit gate: migrate14To15 must run green on a device/emulator (`./gradlew :app:connectedDebugAndroidTest --tests "com.warped.data.local.db.MigrationTest"`).

## Self-Check: PASSED

- All 3 created files exist on disk (GroundedSource.kt, GroundedSourceEntity.kt, GroundedSourceDao.kt)
- All 3 task commits exist in git log (b2cc5e0, a1ff03a, 0a8f4a7)
- 15.json schema verified with grounded_sources table + FK CASCADE + web_override column

---
*Phase: 53-sources-preview-per-chat-toggle*
*Completed: 2026-09-28*
