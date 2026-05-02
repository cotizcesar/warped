---
phase: 08-model-acquisition
plan: 01
subsystem: domain-model
tags: [domain, entity, mapper, modelFormat, GGUF, LITERTLM]
requires: ["LocalModelEntity.modelFormat (added in Phase 6)"]
provides:
  - "LocalModel.modelFormat: String = GGUF"
  - "Bidirectional mapper propagation of modelFormat"
affects:
  - "app/src/main/java/com/warped/domain/model/LocalModel.kt"
  - "app/src/main/java/com/warped/data/local/db/entity/LocalModelMappers.kt"
tech-stack:
  added: []
  patterns: ["Data class default parameter for backward compatibility"]
key-files:
  created: []
  modified:
    - "app/src/main/java/com/warped/domain/model/LocalModel.kt"
    - "app/src/main/java/com/warped/data/local/db/entity/LocalModelMappers.kt"
decisions:
  - "modelFormat defaults to 'GGUF' for backward compatibility with existing models"
  - "Field ordering matches LocalModelEntity (modelFormat before importedAt)"
metrics:
  duration: 131s
  completed_date: "2026-05-02"
---

# Phase 08 Plan 01: modelFormat Domain Model Summary

**One-liner:** Added `modelFormat: String = "GGUF"` field to `LocalModel` domain class and updated bidirectional mappers to preserve model format during entity↔domain conversion.

## What was implemented

Added the `modelFormat` field to the `LocalModel` domain data class and updated both mapper functions in `LocalModelMappers.kt` to propagate the value between `LocalModelEntity` and `LocalModel`. This closes a gap introduced in Phase 6 where `model_format` was added to the Room entity but never surfaced in the domain layer.

### Changes

- **LocalModel.kt:** Added `val modelFormat: String = "GGUF"` after `architecture` and before `importedAt`
- **LocalModelMappers.kt → toDomain():** Added `modelFormat = modelFormat` parameter
- **LocalModelMappers.kt → toEntity():** Added `modelFormat = modelFormat` parameter

The default value `"GGUF"` ensures backward compatibility — all existing code creating `LocalModel` without specifying `modelFormat` gets the correct default for existing GGUF models.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add modelFormat to LocalModel domain and update mappers | `4aaa776` | `LocalModel.kt`, `LocalModelMappers.kt` |

## Deviations from Plan

None — plan executed exactly as written.

## Verification

- `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL (0 errors)
- `grep -n "modelFormat" LocalModel.kt` → 1 match (line 13)
- `grep -n "modelFormat" LocalModelMappers.kt` → 2 matches (lines 14, 26 — one per mapper function)
- No existing tests broken (default parameter preserves all behavior)

## Known Stubs

None.

## Self-Check: PASSED

- [x] All modified files exist on disk
- [x] Commit `4aaa776` confirmed in git log
- [x] Compilation passes with no errors
