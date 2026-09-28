# Phase 51: Syntax-Theme Fix - Context

**Gathered:** 2026-09-28
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous, user accepted all recommended)

<domain>
## Phase Boundary

All four code themes apply in chat code blocks. Root cause localized: SyntaxHighlighterImpl.kt:44 hardcodes `.theme(monokai())`. Covers THEME-01..02. Ordered last so verification runs against final call sites. No file overlap with Phase 49 (parallelizable in theory, executed sequentially here).
</domain>

<decisions>
## Implementation Decisions

### Fix Shape (THEME-01)
- Thread DataStore SyntaxTheme flow into SyntaxHighlighter call path; map MONOKAI/ONE_DARK/GITHUB/DRACULA to SyntaxThemes counterparts with existing light/dark variants
- No palette changes (presets already exist since v1.6); Monokai stays default/fallback for unknown keys
- Keep streaming behavior (flat monospace during streaming, full highlight on fence close)

### Coverage
- Apply at chat code-block call sites; verify against post-removal/grounding screens (final call sites incl. Fuentes-adjacent blocks)
- Python as guide language verified in all 4 presets, light + dark mode

### Regression Test (THEME-02)
- Round-trip unit test over all 4 presets asserting each applies distinctly (fails if any preset silently ignored)

### the agent's Discretion
- Exact threading mechanism (param vs observed flow) at planner discretion following codebase patterns
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- data/highlighting/SyntaxHighlighterImpl.kt (fix site, line 44)
- domain/model/SyntaxTheme (MONOKAI/ONE_DARK/GITHUB/DRACULA keys) + AdvancedPreferences.syntaxTheme Flow + migration
- Highlights 1.1.0 SyntaxThemes engine; existing per-preset light/dark variants

### Established Patterns
- Hilt DI (SyntaxModule); DataStore observed via Flow in Compose/ViewModel
- Block-based MarkdownText Column with per-block code composables

### Integration Points
- Chat code-block rendering path; Settings preset selector (already writes SyntaxTheme); model-card code blocks if sharing the path
</code_context>

<specifics>
## Specific Ideas

No specific requirements — root cause localized in REQUIREMENTS.md.
</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope
</deferred>
