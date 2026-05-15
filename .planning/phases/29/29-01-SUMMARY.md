---
phase: 29-ui-components-markdown-refactoring
plan: 01
subsystem: ui
tags: [kotlin, jetpack-compose, markdown-parsing, sealed-class, syntax-theme, block-rendering]

# Dependency graph
requires:
  - phase: 28-tokenization-engine-theme-system
    provides: SyntaxTheme, TokenType, SyntaxColor, LanguageDetector domain types used for fence label resolution and theme integration
provides:
  - MarkdownBlock sealed class hierarchy with 5 block types (TextBlock, HeaderBlock, CodeBlock, InlineCodeBlock, ListItemBlock)
  - parseMarkdown() pure function converting raw markdown → List<MarkdownBlock> using LanguageDetector
  - Refactored MarkdownText composable with block-based Column rendering and SyntaxTheme parameter
affects: [29-02-code-block, 29-03-integration, chat-screen, message-bubble, settings-screen]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Sealed class block model for markdown AST representation (pure domain, no Compose imports)"
    - "parseMarkdown as top-level pure function with dependency injection via parameter (LanguageDetector)"
    - "Block-based Column rendering with when(block) dispatch replacing single Text(AnnotatedString)"
    - "SyntaxTheme parameter with isSystemInDarkTheme() variant selection for bgCode color derivation"

key-files:
  created:
    - app/src/main/java/com/warped/domain/model/MarkdownBlock.kt
    - app/src/main/java/com/warped/ui/chat/components/MarkdownParser.kt
    - app/src/test/java/com/warped/domain/model/MarkdownBlockTest.kt
    - app/src/test/java/com/warped/ui/chat/components/MarkdownParserTest.kt
    - app/src/test/java/com/warped/ui/chat/components/MarkdownTextInlineParsingTest.kt
    - app/src/androidTest/java/com/warped/ui/chat/components/MarkdownTextTest.kt
  modified:
    - app/src/main/java/com/warped/ui/chat/components/MarkdownText.kt
    - app/src/main/java/com/warped/data/local/preferences/AdvancedPreferences.kt

key-decisions:
  - "parseMarkdown is a pure top-level function with LanguageDetector as parameter (no Hilt, no class)"
  - "InlineCodeBlock emitted for standalone backtick-only lines; mixed-content inline markers preserved in TextBlock for render-time styling"
  - "CodeTheme enum kept as @Deprecated (not removed) for backward compat with AdvancedPreferences migration path"
  - "SyntaxTheme bgCode color derived via isSystemInDarkTheme() at composable call site, inline code bg at 25% alpha"
  - "CodeBlock rendering uses Surface fallback until CodeBlock composable is created in Plan 02"

patterns-established:
  - "MarkdownBlock sealed hierarchy: pure Kotlin data classes in domain/model/ with no Android/Compose dependencies"
  - "parseMarkdown: single-pass streaming-safe parser with flush-on-unclosed-fence (BUG-01 preservation)"
  - "Block-based Column rendering: when(block) dispatch, per-block composable in domain model"

requirements-completed: [INTG-01]

# Metrics
duration: 12min
completed: 2026-05-15
---

# Phase 29 Plan 01: MarkdownBlock sealed class + parseMarkdown parser + block-based MarkdownText refactoring

**Block-based markdown rendering architecture: MarkdownBlock sealed hierarchy (5 variants), streaming-safe parseMarkdown() with LanguageDetector, and refactored MarkdownText Column composable replacing single Text(AnnotatedString) with SyntaxTheme parameter and deprecated CodeTheme enum**

## Performance

- **Duration:** 12 min
- **Started:** 2026-05-15T00:25:17Z
- **Completed:** 2026-05-15T00:37:18Z
- **Tasks:** 3
- **Files modified:** 8

## Accomplishments
- Created MarkdownBlock sealed class with 5 data class variants in domain/model/ (pure Kotlin, no Android deps)
- Implemented parseMarkdown() pure function converting raw markdown to List<MarkdownBlock> with streaming safety for unclosed code fences
- Refactored MarkdownText from single Text(AnnotatedString) to block-based Column of per-block composables with SyntaxTheme parameter
- CodeTheme enum marked @Deprecated; kept for backward compat with AdvancedPreferences migration path
- Inline markdown styling preserved: bold, italic, inline code with monospace font and derived bg color

## Task Commits

Each TDD task followed RED → GREEN cycle with atomic commits:

1. **Task 1: MarkdownBlock sealed class** - `f7f2fa4` (test) → `804ed7e` (feat)
2. **Task 2: parseMarkdown parser** - `3b13bbc` (test) → `2a6ef5d` (feat)
3. **Task 3: MarkdownText refactoring** - `e038f99` (test) → `fb466d2` (feat)

**Blocking fix:** `69c5aa0` (fix: AdvancedPreferences return@edit)

## Files Created/Modified

### Created
- `app/src/main/java/com/warped/domain/model/MarkdownBlock.kt` — Sealed class hierarchy: TextBlock, HeaderBlock(level), CodeBlock(language, code), InlineCodeBlock(code), ListItemBlock(ordered, items)
- `app/src/main/java/com/warped/ui/chat/components/MarkdownParser.kt` — `parseMarkdown(text: String, languageDetector: LanguageDetector): List<MarkdownBlock>` pure function
- `app/src/test/java/com/warped/domain/model/MarkdownBlockTest.kt` — 6 unit tests verifying all 5 variants and sealed class exhaustiveness
- `app/src/test/java/com/warped/ui/chat/components/MarkdownParserTest.kt` — 10 unit tests: headers, code blocks, lists, inline code, streaming safety, inline marker preservation
- `app/src/test/java/com/warped/ui/chat/components/MarkdownTextInlineParsingTest.kt` — 4 unit tests: bold, italic, inline code spans, plain text
- `app/src/androidTest/java/com/warped/ui/chat/components/MarkdownTextTest.kt` — 7 Compose UI tests: bold rendering, header ordering, SyntaxTheme acceptance, code blocks, inline code, mixed blocks, empty text

### Modified
- `app/src/main/java/com/warped/ui/chat/components/MarkdownText.kt` — Full refactor: block-based Column, SyntaxTheme parameter, @Deprecated CodeTheme enum, parseInlineMarkdownAsAnnotatedString helper
- `app/src/main/java/com/warped/data/local/preferences/AdvancedPreferences.kt` — Fixed pre-existing compilation error: `return` → `return@edit` in migrateCodeThemeIfNeeded

## Decisions Made
- parseMarkdown is a pure top-level function with LanguageDetector as parameter (no Hilt injection at function level)
- InlineCodeBlock emitted for standalone backtick-only lines; mixed-content inline markers preserved in TextBlock for render-time AnnotatedString styling
- CodeTheme enum kept as @Deprecated (not removed) for backward compat with AdvancedPreferences migration path in data/local/preferences/
- SyntaxTheme → Compose Color conversion uses isSystemInDarkTheme() at composable call site to select dark/light variant, then extracts BACKGROUND token argb
- Inline code background derived from resolved bgCode at 25% alpha (bgCode.copy(alpha = 0.25f))
- CodeBlock fallback rendering: Surface(RoundedCornerShape(8.dp)) with monospace Text until Plan 02 creates the full CodeBlock composable
- Header sizes from MaterialTheme.typography: titleLarge (level 1), titleMedium (level 2), titleSmall (level 3)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed pre-existing compilation error in AdvancedPreferences.kt**
- **Found during:** Task 1 (RED phase — main source wouldn't compile)
- **Issue:** `return` from non-inline `edit {}` lambda in `migrateCodeThemeIfNeeded()` — prohibited in Kotlin
- **Fix:** Changed `return` to `return@edit` on line 94
- **Files modified:** `app/src/main/java/com/warped/data/local/preferences/AdvancedPreferences.kt`
- **Verification:** `./gradlew :app:compileDebugKotlin` BUILD SUCCESSFUL
- **Committed in:** `69c5aa0`

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Pre-existing bug blocked all compilation; fix was minimal (one keyword change). No scope creep.

## Issues Encountered
- MessageBubble.kt still passes `CodeTheme` parameter to `MarkdownText` — compilation fails with type mismatch (CodeTheme vs SyntaxTheme). This is expected per plan design: Plan 03 (29-03-PLAN.md) handles the full CodeTheme → SyntaxTheme migration across MessageBubble, ChatScreen, ChatUiState, ChatViewModel, SettingsScreen, SettingsUiState, SettingsViewModel, and AdvancedPreferences.
- Android instrumentation tests (MarkdownTextTest.kt) cannot be executed without an emulator/device but compile verification confirms correct Compose API usage.

## Known Stubs
- `MarkdownText.kt` CodeBlock rendering: uses temporary `Surface + Text` fallback instead of the full CodeBlock composable (language header bar, line numbers, copy button). Will be replaced by Plan 02's `CodeBlock.kt` composable.
- `languageDetector` parameter defaults to `LanguageDetector()` fallback — no Hilt EntryPoint integration. Plan 03 wires from ViewModel.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| *(none)* | | |

## Next Phase Readiness
- MarkdownBlock hierarchy and parseMarkdown parser ready for Plan 02 (CodeBlock composable)
- Block-based Column architecture provides per-block rendering slots for language header bar, copy button, line numbers
- SyntaxTheme parameter plumbed; CodeBlock composable in Plan 02 receives it directly
- isStreaming parameter ready for Plan 03 streaming integration
- Plan 03 will resolve MessageBubble CodeTheme → SyntaxTheme migration and full wire-up

---

*Phase: 29-ui-components-markdown-refactoring*
*Completed: 2026-05-15*
