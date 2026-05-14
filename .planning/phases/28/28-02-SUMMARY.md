---
phase: 28
plan: 02
subsystem: highlighting
tags: [domain-interface, language-detection, type-mapping, highlights-integration, caching, theme-persistence, migration, dependency-injection]
requires: [28-01]
provides: [SyntaxHighlighter, LanguageDetector, TypeMapper, SyntaxHighlighterImpl, SyntaxTheme-persistence, SyntaxModule]
affects: [Phase 29 (UI components), Phase 30 (streaming integration)]
tech-stack:
  added:
    - dev.snipme:highlights:1.1.0 (integrated via SyntaxHighlighterImpl)
    - JUnit 5 Platform (useJUnitPlatform configured in build.gradle.kts)
  patterns:
    - Domain interface + data implementation with @Binds DI (SyntaxHighlighter/SyntaxHighlighterImpl)
    - Stateless internal object TypeMapper for CodeStructure → List<SyntaxToken> mapping
    - LinkedHashMap LRU cache with access-order eviction at 50 entries
    - DataStore Flow with three-path migration (new key, legacy CodeTheme, default fallback)
    - distinctUntilChanged() on theme Flow to prevent unnecessary recomposition
key-files:
  created:
    - app/src/main/java/com/warped/domain/highlighting/SyntaxHighlighter.kt
    - app/src/main/java/com/warped/domain/highlighting/LanguageDetector.kt
    - app/src/main/java/com/warped/data/highlighting/TypeMapper.kt
    - app/src/main/java/com/warped/data/highlighting/SyntaxHighlighterImpl.kt
    - app/src/main/java/com/warped/di/SyntaxModule.kt
    - app/src/test/java/com/warped/data/highlighting/TypeMapperTest.kt
    - app/src/test/java/com/warped/data/highlighting/SyntaxHighlighterImplTest.kt
  modified:
    - app/src/main/java/com/warped/data/local/preferences/AdvancedPreferences.kt
    - app/build.gradle.kts
decisions:
  - Highlights 1.1.0 wrapped behind SyntaxHighlighter domain interface following project Repository pattern
  - LanguageDetector uses 43-entry alias map bridging markdown fence labels to canonical language names
  - Auto-detection uses hardcoded keyword sets with 2+ match confidence threshold (14 language keyword sets)
  - JSON/YAML/SQL resolved to SyntaxLanguage.DEFAULT (not supported by Highlights) with fallback tokenization
  - TypeMapper splits literals into NUMBER/CONSTANT and punctuations into OPERATOR/PUNCTUATION via regex
  - Gap-fill algorithm ensures every character position has a token (PLAIN fallback for uncovered spans)
  - Overlap resolution by priority: STRING > KEYWORD > COMMENT > CONSTANT > NUMBER > FUNCTION > TYPE > OPERATOR > PROPERTY > PUNCTUATION > TAG > PLAIN
  - LRU cache keyed by code.hashCode() xor language.hashCode() with 50-entry cap
  - 500KB code size safety cap returns single PLAIN token without invoking Highlights
  - SyntaxTheme persistence reuses existing KEY_CODE_THEME DataStore key with automatic legacy CodeTheme migration
  - Migration maps: NORD→one_dark, SOLARIZED_DARK→monokai, MONOKAI→monokai, DRACULA→dracula, ONE_DARK→one_dark, GITHUB→github
  - SyntaxModule follows existing @Binds abstract class pattern (matching RepositoryModule)
metrics:
  duration: ~12m
  completed_date: 2026-05-14
  task_count: 3
---

# Phase 28 Plan 02: Tokenization Engine & Theme System Summary

**One-liner:** Built the syntax highlighting engine core — SyntaxHighlighter domain interface, 43-entry LanguageDetector with keyword-frequency auto-detection, stateless TypeMapper with gap-fill and overlap resolution, Highlights-backed SyntaxHighlighterImpl with 50-entry LRU cache, and SyntaxTheme persistence with automatic CodeTheme migration in AdvancedPreferences.

## Tasks Executed

| Task | Name | Commit | Status |
|------|------|--------|--------|
| 1 | Create SyntaxHighlighter domain interface and LanguageDetector class | `5c2e061` | ✅ Complete |
| 2 | Create TypeMapper and SyntaxHighlighterImpl (TDD) | `7730a1b` (RED), `40fc647` (GREEN) | ✅ Complete |
| 3 | Extend AdvancedPreferences with SyntaxTheme persistence, create SyntaxModule | `6ea580e` | ✅ Complete |

## Verification Results

| Criteria | Result |
|----------|--------|
| `grep "suspend fun highlight" SyntaxHighlighter.kt` | ✅ Returns matching line |
| `grep "class LanguageDetector" LanguageDetector.kt` | ✅ Returns matching line |
| `grep "py" to "python" LanguageDetector.kt` | ✅ Entry present |
| `grep "sh" to "bash" LanguageDetector.kt` | ✅ Entry present |
| `grep "fun resolveSyntaxLanguage" LanguageDetector.kt` | ✅ Returns matching line |
| `grep "SyntaxLanguage.DEFAULT" LanguageDetector.kt` | ✅ 2 matches (json/yaml/sql/plaintext + else fallback) |
| `grep "internal object TypeMapper" TypeMapper.kt` | ✅ Returns matching line |
| `grep "fun map" TypeMapper.kt` | ✅ Returns matching line |
| `grep "TokenType.PLAIN" TypeMapper.kt` | ✅ 3 matches (gap fills + fallbacks) |
| `grep "@Singleton" SyntaxHighlighterImpl.kt` | ✅ Returns matching line |
| `grep "Highlights.Builder" SyntaxHighlighterImpl.kt` | ✅ Returns matching line |
| `grep "withContext(Dispatchers.Default)" SyntaxHighlighterImpl.kt` | ✅ Returns matching line |
| `grep "removeEldestEntry" SyntaxHighlighterImpl.kt` | ✅ Returns matching line |
| `grep "val syntaxTheme" AdvancedPreferences.kt` | ✅ Returns matching line |
| `grep "suspend fun setSyntaxTheme" AdvancedPreferences.kt` | ✅ Returns matching line |
| `grep "migrateCodeTheme" AdvancedPreferences.kt` | ✅ Returns matching line |
| `grep "SOLARIZED_DARK.*MONOKAI" AdvancedPreferences.kt` | ✅ Returns matching line |
| `grep "class SyntaxModule" SyntaxModule.kt` | ✅ Returns matching line |
| `grep "abstract fun bindSyntaxHighlighter" SyntaxModule.kt` | ✅ Returns matching line |
| `./gradlew :app:compileDebugKotlin` | ✅ BUILD SUCCESSFUL |
| `./gradlew :app:testDebugUnitTest` — 10/10 passing | ✅ BUILD SUCCESSFUL |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] JUnit 5 Platform not configured for unit tests**
- **Found during:** Task 2 (TDD RED phase)
- **Issue:** `./gradlew :app:testDebugUnitTest` completed successfully but ran zero tests — JUnit 5 `useJUnitPlatform()` was never configured because no test files existed previously.
- **Fix:** Added `tasks.withType<Test> { useJUnitPlatform() }` to `app/build.gradle.kts` after the dependencies block.
- **Files modified:** `app/build.gradle.kts`
- **Commit:** Included in `7730a1b`

**2. [Rule 1 - Bug] Em-dash characters in test method names caused compiler crash**
- **Found during:** Task 2 (TDD RED phase)
- **Issue:** Backtick-quoted test names containing em-dash characters (`—`) caused `java.nio.file.InvalidPathException: Malformed input or input contains unmappable characters` when the Kotlin compiler tried to write class files with those characters in the path.
- **Fix:** Replaced em-dashes with commas in test method names.
- **Files modified:** `TypeMapperTest.kt`, `SyntaxHighlighterImplTest.kt`
- **Commit:** Included in `7730a1b`

**3. [Rule 1 - Bug] Incorrect PhraseLocation offsets in test data**
- **Found during:** Task 2 (TDD GREEN phase)
- **Issue:** Manual calculation errors in test CodeStructure position data caused assertion failures — e.g., "null" literal at position `(30, 34)` instead of `(34, 38)`, "42" at `(26, 28)` instead of `(27, 29)`.
- **Fix:** Recalculated all CodeStructure PhraseLocation positions by counting character offsets, then updated test data to match.
- **Files modified:** `TypeMapperTest.kt`
- **Commit:** Included in `40fc647`

**4. [Rule 1 - Bug] Nullable String in `when` branch with `in setOf()` caused type mismatch**
- **Found during:** Task 3 (compilation)
- **Issue:** `storedKey: String?` used with `storedKey in setOf("monokai", ...)` caused Kotlin compiler error: "Argument type mismatch: actual type is 'String?', but 'String' was expected."
- **Fix:** Switched to `when (storedKey) { null -> ... "monokai", "one_dark" -> ... }` pattern with explicit null handling.
- **Files modified:** `AdvancedPreferences.kt`
- **Commit:** Included in `6ea580e`

## Files Created/Modified

| File | Action | Purpose |
|------|--------|---------|
| `domain/highlighting/SyntaxHighlighter.kt` | Created | Domain interface: `suspend fun highlight(code, language): List<SyntaxToken>` |
| `domain/highlighting/LanguageDetector.kt` | Created | Language detection with 43-entry alias map + 14 keyword sets + auto-detection |
| `data/highlighting/TypeMapper.kt` | Created | Stateless object: CodeStructure → sorted, gap-filled List<SyntaxToken> |
| `data/highlighting/SyntaxHighlighterImpl.kt` | Created | @Singleton Highlights-backed implementation with LRU cache |
| `di/SyntaxModule.kt` | Created | Hilt @Binds module: SyntaxHighlighterImpl → SyntaxHighlighter |
| `di/.../TypeMapperTest.kt` | Created | 4 unit tests for type mapping, gap filling, literal/punctuation splitting |
| `di/.../SyntaxHighlighterImplTest.kt` | Created | 6 integration tests for multi-language, caching, unsupported langs, oversized blocks |
| `data/local/preferences/AdvancedPreferences.kt` | Modified | Added syntaxTheme Flow + setSyntaxTheme + migrateCodeTheme (existing code untouched) |
| `app/build.gradle.kts` | Modified | Added `useJUnitPlatform()` for JUnit 5 test runner |

## Known Stubs

None. All created files are complete implementations with no TODO/FIXME comments, placeholder values, or empty data structures. The TypeMapper covers every character position with PLAIN fallback. All 14 language keyword sets are populated. All 4 preset SyntaxTheme constants were created in Plan 01.

## Threat Flags

None beyond the plan's `<threat_model>`. All security mitigations are in place:
- T-28-01 (ReDoS): `OPERATOR_REGEX` is a simple character-class regex with no backtracking
- T-28-02 (OOM): 500KB code size cap with early return before any allocation
- T-28-03 (Highlights EoP): Library is pure text processing — risk accepted
- T-28-04 (Log disclosure): Timber.w() only logs theme name transitions, no secrets; release builds have zero logging
- T-28-05 (Spoofing): Unrecognized fence labels resolve to "plaintext" via fallback — no injection vector

## Self-Check: PASSED

- [x] All 5 source files created in correct packages (`domain/highlighting/`, `data/highlighting/`, `di/`)
- [x] AdvancedPreferences.kt extended without modifying existing CodeTheme code
- [x] All 4 commits verified in git log: `5c2e061`, `7730a1b`, `40fc647`, `6ea580e`
- [x] `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL
- [x] `./gradlew :app:testDebugUnitTest` — 10/10 tests passing
- [x] All verification grep commands return matching lines
- [x] LanguageDetector alias map has 43 entries covering all target languages
- [x] TypeMapper gap-fill handles all character positions (test 2 passes)
- [x] SyntaxHighlighterImpl cache returns same reference on second call (test 4 passes)
- [x] SyntaxHighlighterImpl handles oversized blocks safely (test 5 passes)
