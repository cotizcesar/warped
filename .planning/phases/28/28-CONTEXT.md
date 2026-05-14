# Phase 28: Tokenization Engine & Theme System - Context

**Gathered:** 2026-05-14
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase delivers the syntax highlighting engine, language detector, and theme infrastructure — all non-UI, pure domain/data layer components. It does NOT touch any Compose UI code, MarkdownText refactoring, or code block rendering. Those belong to Phase 29 (UI Components & MarkdownText Refactoring).

**Deliverables:**
- `SyntaxHighlighter` domain interface wrapping Highlights 1.1.0 library
- `SyntaxToken` data class and `TokenType` enum (12 token types)
- `LanguageDetector` class with alias mapping (20+ aliases) and keyword-frequency auto-detection
- `SyntaxTheme` data class with 4 preset themes (Monokai, One Dark, GitHub, Dracula)
- `SyntaxThemeRepository` for persisting theme selection via DataStore
- Backward-compatible migration from existing `CodeTheme` enum → `SyntaxTheme`
- Comprehensive unit tests for highlighter, detector, and themes

**Out of scope for this phase:**
- Any Compose UI changes (MarkdownText, MessageBubble, Settings screen)
- Streaming integration (flat monospace during stream, full highlighting on close — Phase 30)
- Code block UI components (language header bar, copy button, line numbers, expand/collapse — Phase 29)
- Applying highlighting anywhere in the app (Phase 29 + 30)

</domain>

<decisions>
## Implementation Decisions

### Tokenization Engine Integration
- Wrap Highlights 1.1.0 behind `SyntaxHighlighter` domain interface (same pattern as `LlmProvider`, `ChatRepository`) for swapability
- 12 token types: keyword, string, comment, number, function, type, operator, property, constant, punctuation, plain, tag
- Highlighting executes on `Dispatchers.Default` with result caching keyed by (code block hash + language + theme id)
- Emit intermediate `List<SyntaxToken>` — theme-agnostic, reusable across theme changes without re-parsing. Composable layer (Phase 29) converts tokens → AnnotatedString with current theme colors

### Language Detection
- Separate `LanguageDetector` class with single responsibility; `SyntaxHighlighter` depends on it via constructor injection
- Alias mapping stored as inline `Map<String, String>` (~30 entries covering py→python, js→javascript, sh→bash, etc.)
- Auto-detection via keyword-frequency heuristics: count known keywords per language, pick highest match
- Confidence threshold: 2+ distinct keyword matches minimum. Falls back to plain text if below threshold

### Theme System Architecture
- `SyntaxTheme` data class stores `Map<TokenType, SyntaxColor>` with `lightVariant`/`darkVariant` per theme
- Preset theme data defined as Kotlin `val` constants in `SyntaxTheme` companion object (Monokai, One Dark, GitHub, Dracula)
- Backward-compatible migration: `AdvancedPreferences` currently stores `CodeTheme.name` as string. Migration function reads old enum name, maps to new `SyntaxTheme`. Dropped themes map to closest replacement: Nord → One Dark, Solarized Dark → Monokai
- Theme selection persists via existing `AdvancedPreferences` DataStore key `"code_theme"`, now storing `SyntaxTheme` identifier

### Testing & Language Coverage
- 14 languages supported initially: Python, JavaScript, TypeScript, Kotlin, Java, C, C++, Rust, Go, Bash, JSON, YAML, SQL, Swift
- Assertion-based unit tests: provide code snippet, verify `SyntaxToken` positions and types
- Auto-detection tested with 5 representative snippets per language (70 test cases total)
- `SyntaxHighlighter` interface: `suspend fun highlight(code: String, language: String): List<SyntaxToken>` — language is resolved string (detector already ran)

### the agent's Discretion
- Exact Highlights 1.1.0 API mapping details — how CodeHighlight translates to TokenType enum
- Cache eviction strategy (LRU, size-capped at ~50 entries)
- Error handling granularity within highlighter (fail open → return plain tokens with warning)
- Exact keyword lists per language for auto-detection (derived from Highlights language definitions)
- File/package placement within domain/data layers (follow existing conventions from codebase maps)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AdvancedPreferences.kt` (`data/local/preferences/`) — DataStore with `KEY_CODE_THEME` string preference key; provides `Flow<CodeTheme>` and `suspend fun setCodeTheme()`. Will be extended for `SyntaxTheme` compatibility
- `Domain repository pattern` — `SyntaxHighlighter` follows same interface-in-domain/impl-in-data pattern as `ChatRepository`, `LlmProvider`, etc.
- `Hilt DI` — new components will use `@Singleton` + `@Inject constructor` + `@Binds` module pattern

### Established Patterns
- **Repository pattern**: interface in `domain/`, implementation in `data/`, bound via `@Binds` in Hilt module
- **ViewModel pattern**: `StateFlow<UiState>` with `_uiState.update { it.copy(...) }` (Phase 29 concern, not this phase)
- **Error handling**: `Result<T>` for data layer, sealed classes for typed errors
- **Coroutine dispatchers**: `Dispatchers.Default` for CPU-bound work, `Dispatchers.IO` for I/O
- **No KDoc** — minimal inline comments only for non-obvious algorithm steps
- **Timber logging** with class prefix convention: `"SyntaxHighlighter: ..."`

### Integration Points
- **`AdvancedPreferences.kt`** — `KEY_CODE_THEME` will be reused; migration logic added here
- **`ChatViewModel.kt`** — currently collects `advancedPreferences.codeTheme: Flow<CodeTheme>`. Will need to collect `Flow<SyntaxTheme>` once Phase 29 connects it (out of scope for Phase 28)
- **`MarkdownText.kt`** — current `CodeTheme` enum lives here. Will be refactored in Phase 29 to consume `SyntaxTheme`. Phase 28 does NOT modify this file
- **`app/build.gradle.kts`** — needs new dependency: Highlights 1.1.0 (Maven coordinate TBD, will be added to `libs.versions.toml`)

### Current CodeTheme State (for migration context)
- `CodeTheme` enum in `MarkdownText.kt:21-28` defines 6 themes with only `bgCode`/`bgInline` colors
- `AdvancedPreferences` stores theme name as `CodeTheme.name` string
- `ChatUiState.codeTheme: CodeTheme` and `SettingsUiState.codeTheme: CodeTheme` fields exist
- `MessageBubble` passes `codeTheme` to `MarkdownText` as parameter
- Phase 28 does NOT modify any of these consumers — only adds the new `SyntaxTheme` infrastructure alongside existing code

</code_context>

<specifics>
## Specific Ideas

- Highlights 1.1.0 was selected over custom regex tokenizer (~750-1,400 lines saved), Prism4j (archived 2023), and kotlin-textmate (v0.1.0, too new)
- ROADMAP requires "light and dark color variants" per theme (THEM-02) and "4 preset themes" (THEM-01) with Monokai, One Dark, GitHub, Dracula
- Existing 6 CodeTheme entries will reduce to 4 SyntaxTheme presets; migration path defined above
- Deferred highlighting strategy (flat monospace during streaming, full coloring on fence close) is a Phase 30 concern — Phase 28 just builds the engine that will power it

</specifics>

<deferred>
## Deferred Ideas

- Custom regex tokenizer as fallback if Highlights proves insufficient — deferred until Highlights is evaluated
- User-custom theme creation (custom hex values per token type) — out of scope, could be future phase
- Language auto-detection via ML/classifier — keyword-frequency is sufficient per SYNX-03
- Hexagon NPU / GPU-accelerated highlighting — CPU is fine for text processing of this scale
- SyntaxTheme export/import (JSON sharing) — nice-to-have, no requirement

</deferred>
