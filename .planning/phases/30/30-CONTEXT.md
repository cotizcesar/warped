# Phase 30: Streaming Integration & Everywhere Application - Context

**Gathered:** 2026-05-14
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase completes the syntax highlighting feature by handling the streaming transition (flat monospace → colored when fence closes) and applying `MarkdownText`/`CodeBlock` everywhere code blocks appear beyond chat messages.

**Deliverables:**
1. Streaming transition: when `isStreaming` changes from `true` to `false`, trigger syntax highlighting in `CodeBlock` composable
2. Smooth color transition via `animateColorAsState` (already built in CodeBlock)
3. Apply `MarkdownText` in: model card descriptions, README/markdown preview, help screens, onboarding wizard, settings documentation — anywhere code blocks render
4. Performance: verify no frame drops during streaming, smooth scrolling through highlighted code blocks
5. Integration tests for streaming and multi-location rendering

**Out of scope:**
- New features beyond syntax highlighting application
- New UI components
- New dependencies

</domain>

<decisions>
## Implementation Decisions

### Streaming Transition
- `CodeBlock` already accepts `isStreaming: Boolean` parameter from Phase 29
- When `isStreaming` is `true`: render flat monospace (current behavior in CodeBlock.kt line 134-136 returns early)
- When `isStreaming` transitions from `true` to `false`: `LaunchedEffect(code, language, isStreaming)` re-triggers `syntaxHighlighter.highlight()`
- `animateColorAsState` already handles smooth color transitions (400ms, FastOutSlowInEasing)
- No special animation needed for the transition — just re-run highlighting when streaming ends
- Alternative considered but rejected: highlighting on each chunk (O(n²) jank — rejected in STATE.md)

### Everywhere Application
- Find all current code block rendering sites (grep for `Surface` + `FontFamily.Monospace` patterns)
- Replace with `MarkdownText()` composable call using existing `SyntaxTheme` from the nearest available source
- Sites to check:
  - Chat messages (already done via Phase 29 — ChatScreen→MessageBubble→MarkdownText)
  - Model card descriptions (ModelsScreen, HuggingFaceScreen, ModelDetailScreen)
  - README/markdown preview (if any markdown viewer exists)
  - Help screens / onboarding wizard steps
  - Settings documentation text
- Use `SyntaxTheme.MONOKAI` as default when no user preference is available at that call site
- `MarkdownText` handles inline code, headers, lists, and code blocks — it's a drop-in replacement for any plain text that may contain code

### Performance
- CodeBlock already caches tokens (50-entry LRU in SyntaxHighlighterImpl)
- CodeBlock already caps at 500KB for highlighting safety
- Streaming rendering uses flat monospace — no parsing overhead during stream
- Post-streaming highlighting runs on `Dispatchers.Default` — non-blocking for UI
- `animateContentSize` for expand/collapse — hardware-accelerated
- Verify via: scrolling through chat with multiple highlighted code blocks; frame time profiling
- No new optimization work unless profiling shows regressions

### the agent's Discretion
- Exact approach for finding code block rendering sites (grep patterns, manual scan, or both)
- Whether to create a shared helper or just inline MarkdownText calls
- Frame profiling methodology (basic timing logs vs systrace)
- Test structure for streaming integration

</decisions>

<code_context>
## Existing Code Insights

### Already Built (Phases 28-29)
- `SyntaxHighlighter` interface + `SyntaxHighlighterImpl` (50-entry LRU, 500KB cap, Dispatchers.Default)
- `LanguageDetector` (43 aliases, 14-language auto-detection)
- `SyntaxTheme` with 4 presets (Monokai, One Dark, GitHub, Dracula) + light/dark variants
- `MarkdownBlock` sealed class (TextBlock, HeaderBlock, CodeBlock, InlineCodeBlock, ListItemBlock)
- `parseMarkdown()` function
- `MarkdownText()` composable (Column of block composables)
- `CodeBlock()` composable (syntax highlighting, header bar, copy, line numbers, expand/collapse, warning)
- `isStreaming` parameter plumbed through ChatScreen→MessageBubble→MarkdownText→CodeBlock
- Hilt DI: `SyntaxHighlightingEntryPoint`, `MarkdownEntryPoint`
- Settings: theme dropdown with color swatch, code font scale slider
- `ChatUiState.codeTheme: SyntaxTheme` (migrated from CodeTheme)
- `AdvancedPreferences.syntaxTheme: Flow<SyntaxTheme>`

### CodeBlock Streaming Handling (Current)
```kotlin
// CodeBlock.kt:134-136
if (isStreaming) {
    // Flat monospace during stream; Phase 30 handles transition
    return@LaunchedEffect
}
```

The existing code already returns early during streaming. Phase 30 needs to ensure that when isStreaming becomes false, the LaunchedEffect re-fires and highlighting runs.

### Files to Potentially Modify
- `CodeBlock.kt` — handle streaming→highlighted transition (may already work via LaunchedEffect key change)
- `ModelsScreen.kt` — add MarkdownText for model descriptions with code blocks
- `HuggingFaceScreen.kt` — add MarkdownText for model README/card content
- Various: help screens, onboarding, settings docs — apply MarkdownText

</code_context>

<specifics>
## Specific Ideas

- The `LaunchedEffect(code, language, isStreaming)` in CodeBlock.kt already has `isStreaming` as a key — when it changes from true to false, the effect re-launches and highlighting runs. This may already work correctly!
- Focus on finding all code block rendering sites using grep patterns: `FontFamily.Monospace` + `Surface` combinations in non-chat composables
- Use `./gradlew :app:compileDebugKotlin` to verify each change compiles
- No new external dependencies needed

</specifics>

<deferred>
## Deferred Ideas

- Per-code-block language override UI — out of scope
- Advanced profiling with systrace/perfetto — out of scope, basic frame timing sufficient
- Custom scroll physics for code blocks — out of scope
</deferred>
