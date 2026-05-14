# Phase 29: UI Components & MarkdownText Refactoring - Context

**Gathered:** 2026-05-14
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase refactors `MarkdownText` from a single `Text(AnnotatedString)` composable to a block-based `Column` of composables, integrating Phase 28's `SyntaxHighlighter`, `LanguageDetector`, and `SyntaxTheme` into the UI layer. It also updates Settings to use `SyntaxTheme` and migrates all `CodeTheme` references throughout the app.

**Deliverables:**
- `MarkdownBlock` sealed class hierarchy (TextBlock, HeaderBlock, CodeBlock, InlineCodeBlock, ListItemBlock)
- `parseMarkdown()` function converting raw markdown text → `List<MarkdownBlock>`
- Refactored `MarkdownText` composable rendering blocks as a `Column`
- `CodeBlock` composable with syntax-highlighted text, language header bar, copy button, line numbers, and expand/collapse
- Line number rendering alongside code blocks
- Warning indicator for syntax issues (unclosed strings, broken brackets)
- `SyntaxTheme` integration in `ChatScreen` → `MessageBubble` → `MarkdownText` chain
- Settings page updated: `ExposedDropdownMenuBox` with color swatch preview, code font scale slider
- `CodeTheme` enum deprecated/removed; all consumers switched to `SyntaxTheme`
- Code font size multiplier stored in `AdvancedPreferences`

**Out of scope:**
- Streaming integration (flat monospace during stream, highlighting on close — Phase 30)
- Applying highlighting outside chat (model cards, readmes — Phase 30)
- Performance profiling and frame-drop prevention (Phase 30)

</domain>

<decisions>
## Implementation Decisions

### MarkdownText Architecture
- Parse markdown into `List<MarkdownBlock>` sealed class via `parseMarkdown()` function — blocks are pure data, no Compose dependency
- Block types: `TextBlock`, `HeaderBlock(level)`, `CodeBlock(language, code)`, `InlineCodeBlock(code)`, `ListItemBlock(ordered, items)`
- Each `MarkdownBlock` renders as its own composable in a `Column` — enables per-block composables (language header, copy button, line numbers hosted as siblings)
- Code area uses `Modifier.horizontalScroll(rememberScrollState())` — preserves indentation, standard for code

### Syntax Highlighting in UI
- `CodeBlock` composable launches `LaunchedEffect(code, language)` — calls `syntaxHighlighter.highlight(code, language)` on `Dispatchers.Default`
- Results stored in `remember { mutableStateOf(emptyList<SyntaxToken>) }`
- Shows flat monospace text (using `SyntaxTheme.bgCode` background) while highlighting runs
- On token arrival, `animateColorAsState` transitions each token color smoothly

### Code Block UI Components
- Language header bar: top of code block, full-width, slightly lighter background, `Typography.labelSmall` monospace, ~28dp height, right side holds copy button
- Copy button: `IconButton` with `ContentCopy` icon at top-right of header bar; swaps to `Check` icon + "Copied!" text for 2s via `LaunchedEffect` + `delay`; uses `ClipboardManager.setText(AnnotatedString(code))`
- Line numbers: always-on, 32dp wide column left of code, right-aligned, muted gray (`onSurface.copy(alpha = 0.4f)`), 1dp vertical divider separator
- Expand/collapse: 200+ lines collapsed to 150dp max-height; "Show all N lines" tap target at bottom; `animateContentSize()` for transition

### Settings Integration
- Theme selector: `ExposedDropdownMenuBox` with 4-color horizontal swatch strip per option (keyword, string, comment, background colors)
- Position: existing "Appearance" section, upgrades the current Code theme dropdown
- Migration: `ChatUiState.codeTheme` type changes from `CodeTheme` to `SyntaxTheme`; `MessageBubble` accepts `SyntaxTheme`; both `ChatViewModel` and `SettingsViewModel` collect `advancedPreferences.syntaxTheme`
- Code font scale: `Slider` in Settings with 0.8x–1.5x range, 0.1 step, default 1.0; stored as `floatPreferencesKey("code_font_scale")` in `AdvancedPreferences`

### Edge Cases
- Warning indicator: ⚠ icon in language header bar when syntax issues detected; tap shows tooltip with issue description
- Empty code blocks: language header bar with "(empty)" muted text; no line numbers; copy button disabled
- Highlighting loading: flat monospace immediately visible; `animateColorAsState` on token arrival — smooth transition, no skeleton needed
- Streaming (Phase 30 prep): `CodeBlock` composable receives `isStreaming: Boolean` parameter; when true, accumulates lines in `mutableStateListOf()` and shows flat monospace with live line numbers; when false, triggers highlighting

### the agent's Discretion
- Exact `animateColorAsState` duration and easing curve
- Header bar color derivation formula from `SyntaxTheme.bgCode`
- Copy button ripple and touch target sizing
- Line number gutter styling details (divider color, padding)
- `parseMarkdown()` regex/parser implementation specifics
- Collapse animation curve and duration
- Color swatch strip exact layout (horizontal ordering, spacing)
- Font scale slider label format ("0.8x", "1.0x", "1.5x")

</decisions>

<code_context>
## Existing Code Insights

### Files to Modify
- `MarkdownText.kt` — full rewrite from single Text to block-based Column; `CodeTheme` enum removed; `codeTheme` parameter type changes to `SyntaxTheme`
- `MessageBubble.kt` — `codeTheme` parameter type changes from `CodeTheme` to `SyntaxTheme`; pass `isStreaming` to `MarkdownText`
- `ChatScreen.kt` — `uiState.codeTheme` type changes from `CodeTheme` to `SyntaxTheme`
- `ChatUiState.kt` — `codeTheme: CodeTheme` field changes to `codeTheme: SyntaxTheme`
- `ChatViewModel.kt` — collect `advancedPreferences.syntaxTheme` instead of `advancedPreferences.codeTheme`
- `SettingsScreen.kt` — dropdown upgraded with color swatch preview; add code font scale slider
- `SettingsViewModel.kt` — `setCodeTheme(theme: SyntaxTheme)`, new `setCodeFontScale(scale: Float)`
- `SettingsUiState.kt` — `codeTheme: CodeTheme` → `codeTheme: SyntaxTheme`; add `codeFontScale: Float`
- `AdvancedPreferences.kt` — add `codeFontScale: Flow<Float>` and `setCodeFontScale(scale: Float)`; `Flow<SyntaxTheme>` already exists from Phase 28

### New Files to Create
- `MarkdownBlock.kt` — sealed class hierarchy (in `domain/model/` or `ui/chat/components/`)
- `CodeBlock.kt` — composable rendering highlighted code with header bar, copy button, line numbers, expand/collapse
- Theme preview composable (inline in Settings or separate file)

### Integration Points
- `SyntaxHighlighter` interface (Phase 28) — injected via Hilt into composables through `hiltViewModel()` or `EntryPoint`
- `LanguageDetector` (Phase 28) — used during `parseMarkdown()` for fence label resolution
- `SyntaxTheme` data class (Phase 28) — used for color lookups in `CodeBlock` and color swatch in Settings
- `AdvancedPreferences` (Phase 28 extended) — new `codeFontScale` preference

### Current Code to Preserve
- Inline markdown parsing (*bold*, *italic*, `code`, # headers, - bullets, 1. numbered lists) — preserve existing behavior for non-code blocks
- Reasoning text rendering in MessageBubble — keeps italic style, only code blocks get syntax highlighting
- Active conversation tracking, model loading, generation controls — unchanged

</code_context>

<specifics>
## Specific Ideas

- This phase is 100% UI work — the engine, detector, and theme infrastructure are already built and tested in Phase 28
- No new external dependencies needed — Highlights is already in the project, Compose Material 3 provides all needed components
- `MarkdownText` currently handles: #/##/### headers, **bold**, *italic*, `inline code`, ```fenced code blocks```, -/* bullets, 1. numbered lists — all of these must continue working
- Existing code block streaming safety hook (flush buffered lines on unclosed fence) must be preserved
- Phase 30 will handle the `isStreaming` → highlighting transition, but the parameter must be plumbed now
- CODE-05 requires 200+ line collapse — use `code.lines().size` to count
- THEM-03 requires live preview color swatch — show 4 representative token colors per theme

</specifics>

<deferred>
## Deferred Ideas

- Custom theme creation UI — out of scope, 4 presets only per THEM-01
- Per-language theme overrides — out of scope, one theme for all languages
- Code block search/find within large blocks — out of scope
- Drag-to-resize code block height — out of scope
- Syntax theme import/export — out of scope
- Token-level tap-to-highlight for copy — out of scope, copy button copies entire block

</deferred>
