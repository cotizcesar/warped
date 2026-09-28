---
phase: 53-sources-preview-per-chat-toggle
plan: "04"
subsystem: persistence-migration-gate
tags: [room, migration, v14-v15, unit-test, gap-closure]
requires: [53-01-migration-ddl, 53-VERIFICATION-gap]
provides: [jvm-static-migration-gate, v15-delta-evidence]
affects: [53-VERIFICATION-closure]
tech-stack:
  added: []
  patterns: [junit5-truth-static-gate, mockk-captured-execsql, schema-json-diff]
key-files:
  created: [app/src/test/java/com/warped/data/local/db/Migration14To15StaticTest.kt]
  modified: []
decisions:
  - "Source-text assertion for @Database version: Room annotations are CLASS retention, invisible to runtime reflection"
  - "Literal regex match for Migration(14, 15): unescaped parens silently match the wrong string"
metrics:
  duration: "~25 min"
  completed: 2026-09-28
---

# Phase 53 Plan 04: JVM Static Migration Gate Summary

Closed the single Phase 53 verification gap (device-gated `MigrationTest.migrate14To15`, no adb in this environment) with a runnable-on-JVM static migration gate: 5 tests asserting the FULL v14→v15 delta — schema versions, frozen v14 baseline, exact entity/column delta, captured migration SQL cross-checked against the exported schema, and builder registration. Full unit suite green: 279 tests, 0 failures.

## One-liner

JVM static gate for the v14→v15 Room migration: schema diff, captured migration SQL, and registration asserted with zero new dependencies.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | Create Migration14To15StaticTest JVM static migration gate | aa6c9c8 | Migration14To15StaticTest.kt (new) |
| 2 | Run full unit suite and confirm no regressions | (no changes — verification only) | — |

## Test Report

- **Targeted:** `./gradlew :app:testDebugUnitTest --offline --tests "com.warped.data.local.db.Migration14To15StaticTest"` — 5/5 green.
- **Full suite:** `./gradlew :app:testDebugUnitTest --offline` — 31 classes, **279 tests (274 pre-existing + 5 new), 0 failures, 0 errors, 0 skipped**. `Migration14To15StaticTest` result XML present.
- **The 5 gate tests:**
  1. `schema versions` — 14.json parses as version 14, 15.json as version 15.
  2. `v14 frozen baseline` — `web_override` and `grounded_sources` appear nowhere in 14.json (tamper freeze).
  3. `exact v15 delta` — entity set difference exactly `{grounded_sources}` (nothing dropped); `conversations` column delta exactly `{web_override}`; all other entities column-identical; `web_override` INTEGER, nullable, no default (NULL preserves the inherit leg); `grounded_sources` columns exactly `{id, message_id, source_index, resolved_url, extracted_text, status}` with `extracted_text` the sole nullable column; FK `message_id → messages.id` `onDelete CASCADE`; index `index_grounded_sources_message_id(message_id)`.
  4. `migration SQL exactness` — `MIGRATION_14_15` start 14 / end 15; exactly 3 captured `execSQL` statements (ALTER ADD COLUMN with neither NOT NULL nor DEFAULT; CREATE TABLE with CASCADE; CREATE INDEX); identifiers `resolved_url/extracted_text/status/source_index` present in both the CREATE TABLE statement and the 15.json `createSql`.
  5. `registration` — `@Database version = 15`, exactly one `Migration(14, 15)` in Migrations.kt, `MIGRATION_14_15` inside the `addMigrations` call in DatabaseModule.kt.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Room `@Database` annotation invisible to runtime reflection**
- **Found during:** Task 1 (first targeted run: NPE at `getAnnotation(...).version`)
- **Issue:** Plan specified asserting the annotation version "via reflection", but Room annotations use `CLASS` retention — `getAnnotation()` returns null under unit tests.
- **Fix:** Assert `version = 15` from the `AppDatabase.kt` source text (resolved via the same fallback chain); kept a best-effort reflection check that asserts 15 if a runtime-visible annotation ever exists. The 15.json version cross-check in test 1 provides the second leg of evidence.
- **Files modified:** `Migration14To15StaticTest.kt` (new file, pre-commit)
- **Commit:** aa6c9c8

**2. [Rule 1 - Bug] Unescaped regex parens in `Migration(14, 15)` occurrence count**
- **Found during:** Task 1 (second targeted run: "found 0")
- **Issue:** `"Migration(14, 15)".toRegex()` treats parens as groups, matching the literal `Migration14, 15` — count was 0 despite the declaration existing.
- **Fix:** `toRegex(RegexOption.LITERAL)` for an exact literal count (= 1).
- **Files modified:** `Migration14To15StaticTest.kt` (new file, pre-commit)
- **Commit:** aa6c9c8

## Scope Verification

`git status` confirms **no modifications** to `app/schemas/`, `Migrations.kt`, `AppDatabase.kt`, `DatabaseModule.kt`, or any `*.gradle.kts` / version catalog — only the new test file was added (the `app/build/` generated-artifact churn is KSP output regeneration, untouched). Zero new dependencies: uses only `kotlinx.serialization.json` (implementation dep), `mockk` + `truth` (test deps), JUnit5.

## Gap Closure

The 53-VERIFICATION.md gap truth — "v14→v15 migration executes green on a device/emulator" — is now covered JVM-side by evidence: exact schema delta, exact migration SQL, and registration all asserted in `testDebugUnitTest`. The on-device `MigrationTest.migrate14To15` remains the gold standard; hardware follow-up (not a blocker):

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.warped.data.local.db.MigrationTest"
```

## Known Stubs

None — test-only plan, no production surface.

## Self-Check: PASSED

- FOUND: `app/src/test/java/com/warped/data/local/db/Migration14To15StaticTest.kt` (5 `@Test` methods, references `MIGRATION_14_15`)
- FOUND: commit `aa6c9c8` in `git log`
- Full suite re-verified post-commit path: 279 tests, 0 failures
