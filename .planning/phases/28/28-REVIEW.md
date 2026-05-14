---
phase: 28
reviewed: 2026-05-14T00:00:00Z
depth: standard
files_reviewed: 16
files_reviewed_list:
  - gradle/libs.versions.toml
  - app/build.gradle.kts
  - app/src/main/java/com/warped/domain/model/TokenType.kt
  - app/src/main/java/com/warped/domain/model/SyntaxToken.kt
  - app/src/main/java/com/warped/domain/model/SyntaxColor.kt
  - app/src/main/java/com/warped/domain/model/SyntaxTheme.kt
  - app/src/main/java/com/warped/domain/highlighting/SyntaxHighlighter.kt
  - app/src/main/java/com/warped/domain/highlighting/LanguageDetector.kt
  - app/src/main/java/com/warped/data/highlighting/SyntaxHighlighterImpl.kt
  - app/src/main/java/com/warped/data/highlighting/TypeMapper.kt
  - app/src/main/java/com/warped/data/local/preferences/AdvancedPreferences.kt
  - app/src/main/java/com/warped/di/SyntaxModule.kt
  - app/src/test/java/com/warped/domain/highlighting/LanguageDetectorTest.kt
  - app/src/test/java/com/warped/data/highlighting/TypeMapperTest.kt
  - app/src/test/java/com/warped/data/highlighting/SyntaxHighlighterImplTest.kt
  - app/src/test/java/com/warped/domain/model/SyntaxThemeTest.kt
findings:
  critical: 2
  warning: 3
  info: 3
  total: 8
status: issues_found
---

# Phase 28: Code Review Report

**Reviewed:** 2026-05-14
**Depth:** standard
**Files Reviewed:** 16
**Status:** issues_found

## Summary

Reviewed all 16 files in the Phase 28 scope: 9 main source files, 1 build configuration file, 1 version catalog, and 5 test files. The syntax highlighting engine, language detector, theme system, and DataStore integration are well-structured and follow the project's Clean Architecture conventions. The implementation correctly wraps the Highlights 1.1.0 library behind a domain interface and provides comprehensive unit tests.

However, two BLOCKER issues were found: a thread-safety bug in the `SyntaxHighlighterImpl` cache that can cause crashes under concurrent access, and a `codeTheme` Flow regression in `AdvancedPreferences` where the existing CodeTheme-enum-based flow breaks after the new SyntaxTheme migration writes lowercase keys to DataStore. Three WARNING-level issues and three INFO-level items were also identified.

---

## Critical Issues

### CR-01: Unsynchronized concurrent access to LinkedHashMap cache in SyntaxHighlighterImpl

**File:** `app/src/main/java/com/warped/data/highlighting/SyntaxHighlighterImpl.kt:20-24`, `:34`, `:44`

**Issue:** The `cache` field is a `LinkedHashMap` accessed from multiple coroutines (via `withContext(Dispatchers.Default)`) without any synchronization. `LinkedHashMap` is documented as not thread-safe. Concurrent reads on line 34 (`cache[key]?.let { return it }`) and writes on line 44 (`cache[key] = tokens`) can produce:

- **`ConcurrentModificationException`** — crashing the coroutine and failing highlighting with an unhandled exception.
- **Silent data corruption** — returning incorrect or partial token lists for a given (code, language) pair.
- **Lost cache entries** — writes interleaving with internal reordering (access-order mode) producing an inconsistent map state.

This is a real risk because `SyntaxHighlighterImpl` is `@Singleton` (one instance app-wide) and `highlight()` runs on `Dispatchers.Default`, where multiple concurrent highlighting requests are likely when streaming LLM responses that contain code blocks.

**Fix:**

```kotlin
private val cache = Collections.synchronizedMap(
    object : LinkedHashMap<Int, List<SyntaxToken>>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, List<SyntaxToken>>?): Boolean {
            return size > 50
        }
    }
)
```

Or better yet, use a thread-safe construct like `ConcurrentHashMap` combined with manual eviction, or a coroutine-aware approach with `Mutex`:

```kotlin
private val cacheMutex = Mutex()
private val cache = object : LinkedHashMap<Int, List<SyntaxToken>>(50, 0.75f, true) { ... }

override suspend fun highlight(...): List<SyntaxToken> {
    if (code.length > 500_000) { ... }
    val key = cacheKey(code, language)
    cacheMutex.withLock {
        cache[key]?.let { return@withLock it }
    }
    return withContext(Dispatchers.Default) {
        // ... compute tokens ...
        cacheMutex.withLock { cache[key] = tokens }
        tokens
    }
}
```

---

### CR-02: `codeTheme` Flow permanently broken after SyntaxTheme migration

**File:** `app/src/main/java/com/warped/data/local/preferences/AdvancedPreferences.kt:63-66`, `:87-100`

**Issue:** The `codeTheme` Flow (line 63) and the new `syntaxTheme` Flow (line 87) share the same DataStore key `KEY_CODE_THEME`. The `syntaxTheme` Flow includes migration logic (line 92-96) that converts legacy `CodeTheme` enum names (e.g., `"ONE_DARK"`) to new `SyntaxTheme` keys (e.g., `"one_dark"`) and **writes the new lowercase key back to DataStore** on line 95.

After this migration write occurs, the `codeTheme` Flow reads the DataStore key `"one_dark"` and calls `CodeTheme.valueOf("one_dark")` on line 65. Since Kotlin's `enum.valueOf()` is case-sensitive and `CodeTheme` enum values are UPPER_CASE (`ONE_DARK`), this throws `IllegalArgumentException`. The `catch (_: Exception)` returns `CodeTheme.MONOKAI`.

**Impact:** `codeTheme` is actively consumed by `ChatViewModel`, `SettingsViewModel`, and the UI composables `MessageBubble` and `MarkdownText`. After migration, all these consumers will always receive `CodeTheme.MONOKAI` regardless of the user's actual theme selection. The settings screen will display "Monokai" instead of the selected theme, and all code blocks will render with Monokai colors.

**Fix:** Update the `codeTheme` Flow to handle both legacy enum names and new SyntaxTheme keys:

```kotlin
val codeTheme: Flow<CodeTheme> = context.advancedPreferencesStore.data.map { prefs ->
    val name = prefs[KEY_CODE_THEME] ?: return@map CodeTheme.MONOKAI
    // Try direct enum match first (legacy format)
    try {
        CodeTheme.valueOf(name)
    } catch (_: IllegalArgumentException) {
        // Attempt migration from SyntaxTheme key
        when (name.lowercase()) {
            "monokai" -> CodeTheme.MONOKAI
            "one_dark" -> CodeTheme.ONE_DARK
            "github" -> CodeTheme.GITHUB
            "dracula" -> CodeTheme.DRACULA
            else -> CodeTheme.MONOKAI
        }
    }
}
```

---

## Warnings

### WR-01: Side-effect in Flow `map` operator (DataStore write during migration)

**File:** `app/src/main/java/com/warped/data/local/preferences/AdvancedPreferences.kt:95`

**Issue:** The `syntaxTheme` Flow performs a DataStore write (`edit {}`) inside the `map` operator's suspend lambda:

```kotlin
context.advancedPreferencesStore.edit { it[KEY_CODE_THEME] = migrated.key }
```

This violates the principle that `map` operators should be pure (side-effect-free). While technically correct because `map` accepts `suspend` lambdas, this pattern causes:

1. **Duplicate writes**: Every collector of this Flow triggers the migration write when a legacy key is encountered, even though the write is idempotent.
2. **Unobserved async work**: The DataStore `edit` runs asynchronously within the `map` and the Flow continues emitting without waiting for it to complete. If two operations interleave (e.g., rapid theme toggling), the write-order from `map` and a concurrent `setSyntaxTheme` call may produce unpredictable results.
3. **Surprising behavior for future maintainers**: A `map` that mutates external state is unexpected and easy to miss during refactoring.

**Fix:** Use `onStart` or `onEach` for side effects, or better yet, perform migration once during app startup rather than inside the Flow pipeline:

```kotlin
// In a one-time init block:
suspend fun migrateIfNeeded() {
    context.advancedPreferencesStore.edit { prefs ->
        val stored = prefs[KEY_CODE_THEME] ?: return
        val migrated = migrateCodeTheme(stored)
        if (migrated.key != stored.lowercase()) {
            prefs[KEY_CODE_THEME] = migrated.key
        }
    }
}

// Then the syntaxTheme Flow becomes pure:
val syntaxTheme: Flow<SyntaxTheme> = context.advancedPreferencesStore.data.map { prefs ->
    val storedKey = prefs[KEY_CODE_THEME]
    when (storedKey) {
        null -> SyntaxTheme.MONOKAI
        "monokai", "one_dark", "github", "dracula" -> SyntaxTheme.fromKey(storedKey)
        else -> SyntaxTheme.MONOKAI  // already migrated
    }
}.distinctUntilChanged()
```

---

### WR-02: `"None"` never classified as CONSTANT due to case mismatch

**File:** `app/src/main/java/com/warped/data/highlighting/TypeMapper.kt:10`, `:51`

**Issue:** `CONSTANT_KEYWORDS` includes `"None"` (capital N, Python's null equivalent), but the classification check on line 51 uses `text.lowercase()` before lookup:

```kotlin
private val CONSTANT_KEYWORDS = setOf("true", "false", "null", "nil", "None", "undefined")
// ...
val type = if (text.lowercase() in CONSTANT_KEYWORDS) TokenType.CONSTANT else TokenType.NUMBER
```

`"None".lowercase()` produces `"none"`, which does not match `"None"` in the set. As a result, Python's `None` literal is classified as `NUMBER` instead of `CONSTANT`. The same issue would apply if any other mixed-case constant were added to the set.

**Fix:** Either make all entries in `CONSTANT_KEYWORDS` lowercase:

```kotlin
private val CONSTANT_KEYWORDS = setOf("true", "false", "null", "nil", "none", "undefined")
```

Or perform case-insensitive lookup:

```kotlin
val type = if (CONSTANT_KEYWORDS.any { it.equals(text, ignoreCase = true) }) TokenType.CONSTANT else TokenType.NUMBER
```

---

### WR-03: Cache key uses Int hash — collision risk with hash-based eviction

**File:** `app/src/main/java/com/warped/data/highlighting/SyntaxHighlighterImpl.kt:26`

**Issue:** The cache key is computed as a 32-bit XOR of two `String.hashCode()` values:

```kotlin
private fun cacheKey(code: String, language: String): Int = code.hashCode() xor language.hashCode()
```

Since `String.hashCode()` returns a 32-bit `Int`, XOR collisions are possible: two different (code, language) pairs could produce the same cache key, causing the wrong cached result to be returned. While the probability is low with a cache size of 50, it is a correctness risk that can be eliminated entirely.

**Fix:** Use a pair-based key instead:

```kotlin
private data class CacheKey(val code: String, val language: String)

private val cache = object : LinkedHashMap<CacheKey, List<SyntaxToken>>(50, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, List<SyntaxToken>>?): Boolean {
        return size > 50
    }
}
```

Or use a long hash with lower collision probability by combining via a multiplier instead of XOR.

---

## Info

### IN-01: `migrateCodeTheme` duplicated in test file — tests copy, not the real function

**File:** `app/src/main/java/com/warped/data/local/preferences/AdvancedPreferences.kt:74-85`
**File:** `app/src/test/java/com/warped/domain/model/SyntaxThemeTest.kt:248-256`

**Issue:** The `migrateCodeTheme` function is `private` in `AdvancedPreferences`, so `SyntaxThemeTest` cannot access it directly. The test file duplicates the entire migration logic as a private helper. If the real function in `AdvancedPreferences` changes without a corresponding change to the test copy, the tests will pass while the production code is broken.

**Fix:** Make `migrateCodeTheme` internal (or package-private) and test it via the real `syntaxTheme` Flow:

```kotlin
// In AdvancedPreferences.kt — expose for testing
internal fun migrateCodeTheme(oldName: String): SyntaxTheme = when (oldName) { ... }
```

Then in the test, rely on the `syntaxTheme` Flow for integration-level migration testing rather than duplicating logic.

---

### IN-02: Misleading test name — test uses 250KB, not 500KB

**File:** `app/src/test/java/com/warped/data/highlighting/SyntaxHighlighterImplTest.kt:125-128`

**Issue:** The test named `code at 500KB cap is processed normally` generates code of `"val x = 1\n".repeat(25000)`. Each repetition is 10 characters, so the total is 250,000 characters (~244 KB), not 500 KB. The test passes because the cap is at 500,001 characters. The test name is misleading and could cause someone to mistakenly believe the test covers boundary conditions at the 500KB cap.

**Fix:** Rename the test to reflect the actual size, e.g.:

```kotlin
fun `code well under 500KB cap is processed normally`() = runTest {
```

---

### IN-03: Self-referencing aliases in `aliasMap` are redundant

**File:** `app/src/main/java/com/warped/domain/highlighting/LanguageDetector.kt:10-29`

**Issue:** The `aliasMap` contains entries where the key and value are identical:

```
"java" to "java",
"c" to "c",
"rust" to "rust",
"go" to "go",
"bash" to "bash",
"swift" to "swift",
"json" to "json",
"yaml" to "yaml",
"sql" to "sql",
```

These self-mapping entries have no effect — if the fence label is `"java"`, the lookup returns `"java"` (already the normalized form). They add visual noise but no functional issue.

**Fix:** Remove the self-referencing entries to simplify the map. The early return behavior (line 51) will still work correctly for matching keys since the non-self-referencing entries handle all real alias cases.

---

_Reviewed: 2026-05-14T00:00:00Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
