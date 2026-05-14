---
phase: 28
plan: 01
subsystem: highlighting
tags: [dependency, domain-model, theme, foundation]
requires: []
provides: [TokenType, SyntaxToken, SyntaxColor, SyntaxTheme, highlights-dependency]
affects: [Phase 29 (UI components), Phase 30 (streaming integration)]
tech-stack:
  added:
    - dev.snipme:highlights:1.1.0 (syntax tokenization engine)
  patterns:
    - @JvmInline value class for zero-overhead ARGB color wrapper
    - companion object for preset theme constants
    - mapOf() for declarative color tables
key-files:
  created:
    - app/src/main/java/com/warped/domain/model/TokenType.kt
    - app/src/main/java/com/warped/domain/model/SyntaxToken.kt
    - app/src/main/java/com/warped/domain/model/SyntaxColor.kt
    - app/src/main/java/com/warped/domain/model/SyntaxTheme.kt
  modified:
    - gradle/libs.versions.toml
    - app/build.gradle.kts
decisions:
  - Highlights 1.1.0 pinned as sole syntax tokenization engine (resolved via Maven Central)
  - SyntaxColor stores raw ARGB Int (not Compose Color) keeping domain layer Android-free
  - TokenType enum uses 13 values including BACKGROUND for code block background color
  - 4 preset themes implemented: Monokai, One Dark, GitHub, Dracula — each with 13-color light/dark tables
metrics:
  duration: ~3m
  completed_date: 2026-05-14
  task_count: 3
---

# Phase 28 Plan 01: Tokenization Engine & Theme System Summary

**One-liner:** Wired Highlights 1.1.0 dependency and created 4 domain model files — TokenType (13 enum values), SyntaxToken, SyntaxColor, and SyntaxTheme with Monokai/One Dark/GitHub/Dracula presets each holding complete 13-color light/dark color tables.

## Tasks Executed

| Task | Name | Commit | Status |
|------|------|--------|--------|
| 1 | Add Highlights 1.1.0 to Gradle dependency catalog and build script | `e917593` | ✅ Complete |
| 2 | Create TokenType, SyntaxToken, and SyntaxColor domain models | `2b8f70d` | ✅ Complete |
| 3 | Create SyntaxTheme data class with 4 preset themes | `ed3053a` | ✅ Complete |

## Verification Results

| Criteria | Result |
|----------|--------|
| `grep highlights gradle/libs.versions.toml` — version + library entries | ✅ `highlights = "1.1.0"` + `highlights = { group = "dev.snipme"... }` |
| `grep highlights app/build.gradle.kts` — `implementation(libs.highlights)` | ✅ Line 156 |
| `grep "enum class TokenType" TokenType.kt` — 13 values including BACKGROUND | ✅ 13 trailing commas |
| `grep "data class SyntaxToken" SyntaxToken.kt` — correct signature | ✅ 4 val properties |
| `grep "@JvmInline" SyntaxColor.kt` — inline value class | ✅ Wraps Int ARGB |
| `grep "val MONOKAI" SyntaxTheme.kt` — 4 preset constants | ✅ MONOKAI, ONE_DARK, GITHUB, DRACULA |
| `grep "fun fromKey" SyntaxTheme.kt` — fallback to MONOKAI | ✅ Default fallback |
| `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL | ✅ No new warnings |
| `./gradlew :app:dependencies` — `dev.snipme:highlights:1.1.0` resolved | ✅ Resolved with highlights-jvm:1.1.0 |

## Deviations from Plan

None — plan executed exactly as written. All three tasks completed without issues.

## Files Created/Modified

| File | Action | Purpose |
|------|--------|---------|
| `gradle/libs.versions.toml` | Modified | Added highlights version and library declaration |
| `app/build.gradle.kts` | Modified | Wired `implementation(libs.highlights)` dependency |
| `TokenType.kt` | Created | 13-value enum for syntax token categories |
| `SyntaxToken.kt` | Created | Data class holding token position + type + text |
| `SyntaxColor.kt` | Created | `@JvmInline value class` wrapping ARGB Int |
| `SyntaxTheme.kt` | Created | Theme data class with 4 presets, `all()`, `fromKey()` |

## Decided During Execution

- No deviations from plan — all spec values used exactly as defined in RESEARCH.md color tables.
- `SyntaxColor(0xFFXXXXXX.toInt())` pattern used consistently (Long literal truncated to Int for ARGB storage).

## Known Stubs

None. All created files are complete domain models with all specified values — no placeholders, TODO comments, or empty data structures.

## Threat Flags

None. This plan had no trust boundaries — pure dependency wiring and domain model creation with no user input, network I/O, or file access.

## Self-Check: PASSED

- [x] All 4 domain model files exist in `app/src/main/java/com/warped/domain/model/`
- [x] `gradle/libs.versions.toml` and `app/build.gradle.kts` correctly modified
- [x] All 3 commits verified in git log: `e917593`, `2b8f70d`, `ed3053a`
- [x] `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL
- [x] `dev.snipme:highlights:1.1.0` resolved in dependency tree
