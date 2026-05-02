---
phase: 06-engine-foundation
plan: 02
one-liner: "Room schema migration v6→v7 adding model_format column to local_models table for GGUF/LITERTLM format tracking"
subsystem: data-local-storage
tags: [room, migration, schema, local_models, model_format, GGUF, LITERTLM]
requires: []
provides: [MIGRATION_6_7, LocalModelEntity.modelFormat]
affects: [AppDatabase, DatabaseModule]
tech-stack:
  added: []
  patterns: [Room Migration object pattern, @ColumnInfo defaultValue annotation]
key-files:
  created: []
  modified:
    - app/src/main/java/com/warped/data/local/db/Migrations.kt
    - app/src/main/java/com/warped/data/local/db/entity/LocalModelEntity.kt
    - app/src/main/java/com/warped/data/local/db/AppDatabase.kt
    - app/src/main/java/com/warped/di/DatabaseModule.kt
decisions:
  - "D-13 implemented: model_format TEXT NOT NULL DEFAULT 'GGUF' column added via MIGRATION_6_7"
  - "D-14 implemented: modelFormat field accepts 'GGUF' and 'LITERTLM' values, defaults to 'GGUF'"
  - "engine_type column intentionally excluded per user decision (only model_format in this plan)"
metrics:
  duration: "33 seconds"
  completed_date: "2026-05-02T16:27:45Z"
---

# Phase 06 Plan 02: Room Schema Migration (LITE-04) Summary

## What Was Done

Added a Room database migration from version 6 to 7 that adds a `model_format` column to the `local_models` table. This enables the app to distinguish between GGUF models (llama.cpp) and LiteRT-LM models — a prerequisite for Phase 08 (Hugging Face Model Acquisition) and Phase 07 (Provider Integration).

### Changes by Task

**Task 1 — MIGRATION_6_7 + modelFormat field:**
- Created `MIGRATION_6_7` object in `Migrations.kt` with `ALTER TABLE local_models ADD COLUMN model_format TEXT NOT NULL DEFAULT 'GGUF'`
- Added `modelFormat: String = "GGUF"` field to `LocalModelEntity` with `@ColumnInfo(name = "model_format", defaultValue = "GGUF")`
- Follows the exact same pattern as existing `MIGRATION_4_5` and `MIGRATION_5_6`

**Task 2 — Version bump + registration:**
- Bumped `AppDatabase` version from 6 to 7
- Imported `MIGRATION_6_7` in `DatabaseModule`
- Registered the migration in the `addMigrations()` chain: `MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7`

## Success Criteria Verification

| # | Criterion | Status |
|---|-----------|--------|
| 1 | `MIGRATION_6_7` exists with `Migration(6, 7)` + correct ALTER TABLE | ✅ PASS |
| 2 | `modelFormat` field with `@ColumnInfo(defaultValue = "GGUF")` | ✅ PASS |
| 3 | `AppDatabase.version = 7` | ✅ PASS |
| 4 | `MIGRATION_6_7` imported and registered in `DatabaseModule` | ✅ PASS |

**Build verification:** Full `./gradlew :app:compileDebugKotlin` could not complete due to a **pre-existing** dependency resolution failure (`litertlm-android:0.11.0-beta01` not found). This is unrelated to the Room migration changes (the dependency was added in 06-01 or 06-04, not 06-02). All source-level success criteria pass.

## Deviations from Plan

None — plan executed exactly as written.

## Deferred Issues

| Issue | Source | Description |
|-------|--------|-------------|
| litertlm-android dependency unresolved | Pre-existing (not 06-02) | `com.google.ai.edge.litertlm:litertlm-android:0.11.0-beta01` cannot be resolved from Google Maven or Maven Central. Blocks full build verification but is not caused by 06-02 changes. |

## Commits

| Hash | Message |
|------|---------|
| `50a05ff` | feat(06-02): add MIGRATION_6_7 and modelFormat field to LocalModelEntity |
| `2d93c60` | feat(06-02): bump Room database to v7 and register MIGRATION_6_7 |

## Threat Model Compliance

| Threat | Status |
|--------|--------|
| T-06-04 (Tampering — 6→7 migration) | ✅ Mitigated: `DEFAULT 'GGUF'` + `NOT NULL` constraint + `fallbackToDestructiveMigration()` fallback |
| T-06-05 (Info Disclosure — model_format column) | ✅ Accepted: Column stores only format identifiers, no sensitive data |
