---
phase: 28
fixed_at: 2026-05-14T00:00:00Z
review_path: .planning/phases/28/28-REVIEW.md
iteration: 1
findings_in_scope: 5
fixed: 5
skipped: 0
status: all_fixed
---

# Phase 28: Code Review Fix Report

**Fixed at:** 2026-05-14
**Source review:** `.planning/phases/28/28-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 5 (2 Critical, 3 Warning)
- Fixed: 5
- Skipped: 0

## Fixed Issues

### CR-01: Unsynchronized concurrent access to LinkedHashMap cache in SyntaxHighlighterImpl

**Files modified:** `app/src/main/java/com/warped/data/highlighting/SyntaxHighlighterImpl.kt`
**Commit:** `b5928d0`
**Applied fix:** Added `Mutex` (coroutine-aware lock) to protect all `LinkedHashMap` cache reads and writes. The `highlight()` function now uses `cacheMutex.withLock { cache[key] }` for the read path and `cacheMutex.withLock { cache[key] = tokens }` for the write path, preventing `ConcurrentModificationException` and data corruption under concurrent access from multiple coroutines on `Dispatchers.Default`.

### CR-02: `codeTheme` Flow permanently broken after SyntaxTheme migration

**Files modified:** `app/src/main/java/com/warped/data/local/preferences/AdvancedPreferences.kt`
**Commit:** `bd5ed34`
**Applied fix:** Updated the `codeTheme` Flow to handle both legacy `CodeTheme` enum names (uppercase, e.g., `"ONE_DARK"`) and migrated `SyntaxTheme` keys (lowercase, e.g., `"one_dark"`). The Flow now tries `CodeTheme.valueOf()` first, and on `IllegalArgumentException`, falls back to a `when` block that maps lowercase `SyntaxTheme` keys back to the appropriate `CodeTheme` enum value. This prevents the silent fallback to `MONOKAI` for all consumers (ChatViewModel, SettingsViewModel, MessageBubble, MarkdownText) after migration.

### WR-01: Side-effect in Flow `map` operator (DataStore write during migration)

**Files modified:** `app/src/main/java/com/warped/data/local/preferences/AdvancedPreferences.kt`
**Commit:** `3553350`
**Applied fix:** Extracted the migration DataStore write from the `map` operator into a separate `suspend fun migrateCodeThemeIfNeeded()` called via `.onStart { migrateCodeThemeIfNeeded() }`. The `syntaxTheme` Flow's `map` operator is now pure (read-only transformation). Migration writes happen exactly once when the Flow is first collected, eliminating duplicate writes per-collector, unobserved async interleaving, and surprising behavior for maintainers.

### WR-02: `"None"` never classified as CONSTANT due to case mismatch

**Files modified:** `app/src/main/java/com/warped/data/highlighting/TypeMapper.kt`
**Commit:** `446e6ef`
**Applied fix:** Changed `"None"` to `"none"` in the `CONSTANT_KEYWORDS` set. Since the classification check uses `text.lowercase() in CONSTANT_KEYWORDS`, this ensures Python's `None` literal (and any future mixed-case constant added to the set) correctly matches and is classified as `TokenType.CONSTANT` instead of `TokenType.NUMBER`.

### WR-03: Cache key uses Int hash — collision risk with hash-based eviction

**Files modified:** `app/src/main/java/com/warped/data/highlighting/SyntaxHighlighterImpl.kt`
**Commit:** `88dcc6a`
**Applied fix:** Replaced the `Int` XOR hash key (`code.hashCode() xor language.hashCode()`) with a collision-free `String` key (`"$code|$language"`). Changed the `LinkedHashMap` type parameter from `<Int, List<SyntaxToken>>` to `<String, List<SyntaxToken>>`. This eliminates the possibility of two different (code, language) pairs producing the same cache key and returning incorrect cached results.

---

_Fixed: 2026-05-14T00:00:00Z_
_Fixer: the agent (gsd-code-fixer)_
_Iteration: 1_
