# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

## Milestone: v1.6 — Code Syntax Highlighting

**Shipped:** 2026-05-15
**Phases:** 3 | **Plans:** 8

### What Was Built
- Token-level syntax highlighting engine (Highlights 1.1.0 + TypeMapper) with 12 token types across 43 languages
- LanguageDetector with 20+ fence label aliases and keyword-frequency auto-detection fallback
- 4 preset themes (Monokai, One Dark, GitHub, Dracula) with automatic light/dark mode adaptation
- CodeBlock composable: syntax-colored tokens, language header bar, copy-to-clipboard, line numbers, expand/collapse, syntax issue warnings
- Full chat integration: theme flows from Settings dropdown through ViewModels to all chat composables
- Streaming-to-highlighted smooth transition (flat monospace during active streaming, full coloring on closing fence)
- Code font scale slider (0.8x–1.5x) in Settings, wired through full composable chain
- HuggingFace model card descriptions render syntax-highlighted markdown

### What Worked
- **Block-based markdown refactoring:** Restructuring MarkdownText from single AnnotatedString to Column of composables unlocked per-block UI (header bar, copy button). This was the key architectural enabler.
- **Domain-first design:** TokenType, SyntaxToken, SyntaxColor, SyntaxTheme all in domain/model/ with no Android dependencies. Made testing trivial (150 unit tests, zero Android framework dependencies).
- **Cross-phase gap detection:** The CodeBlock composable (545 lines) was orphaned — never called from MarkdownText. The verifier caught it, and a 13-line fix unlocked all features. Validation before shipping works.
- **Library wrapping:** Wrapping Highlights behind SyntaxHighlighter domain interface means it can be swapped later without touching UI code.

### What Was Inefficient
- **Orphaned integration:** CodeBlock wasn't wired to MarkdownText in Phase 29 because the two tasks (29-02 CodeBlock creation and 29-01 MarkdownText refactoring) ran in sequence without a final integration task. The plan had 29-03 for integration but it focused on chat wiring, not the block-to-composable connection.
- **Streaming regression:** The LaunchedEffect key didn't include `isStreaming` initially, causing per-token syntax highlighting during streaming. Fixed in Phase 30 but should have been caught in Phase 29.
- **codeFontScale hardcoded:** Font scale was set to 1.0f in CodeBlock and not plumbed through. Discovered late. Phase dependencies meant deferring to Phase 30.

### Patterns Established
- **DataStore → ViewModel → UiState → Composable propagation:** SyntaxTheme and codeFontScale both follow this pattern. New settings should use the same pipeline.
- **Hilt EntryPoint at composable scope:** Resolving LanguageDetector/SyntaxHighlighter via `EntryPoints.get()` in composable functions works cleanly without passing dependencies through every layer.
- **LaunchedEffect key sensitivity:** Any state that affects rendering MUST be in the LaunchedEffect key tuple. The `isStreaming` omission was the single bug causing the streaming regression.

### Key Lessons
1. **Integration tasks need explicit cross-task wiring validation.** A "wire X into Y" subtask in the plan would have prevented the orphaned composable.
2. **Streaming state must be a first-class parameter in composable rendering branches.** Flat monospace vs. highlighted is a fundamentally different rendering path that needs explicit guards.
3. **Block-based rendering is a better pattern for markdown than AnnotatedString.** The ability to place per-block UI elements (headers, buttons, gutters) justifies the composability overhead.

### Cost Observations
- Model mix: AI-managed execution (deepseek-v4-pro)
- Sessions: Multiple across 3 phases
- Notable: Phase 28 (engine + 150 tests) was the most productive phase — domain-first design with pure functions enabled rapid test-driven development

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Phases | Key Change |
|-----------|--------|------------|
| v1.6 | 3 | Block-based rendering pattern established; cross-phase wiring verification improved |

### Cumulative Quality

| Milestone | Tests | Zero-Dep Additions |
|-----------|-------|-------------------|
| v1.6 | 150 | 3 new libraries (Highlights 1.1.0, Kotlinx Serialization, Kotlinx Coroutines Test) |

### Top Lessons (Verified Across Milestones)

1. Domain-first design enables comprehensive testing without framework dependencies
2. Cross-phase integration gaps are the most common failure mode — explicit wiring tasks prevent orphaned components
3. LaunchedEffect/Flow key sensitivity is a recurring source of subtle composable bugs
