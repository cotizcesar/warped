# Roadmap: Warped

## Milestones

- ✅ **v1.0 MVP** — Phases 1-5 (shipped)
- ✅ **v1.1 LiteRT-LM Integration** — Phases 6-10 (shipped)
- ✅ **v1.2 GGUF Native Inference** — Phases 11-15 (shipped)
- ✅ **v1.3 Remote Provider Endpoints & UX** — Phases 16-19 (shipped)
- ✅ **v1.4 Onboarding Wizard** — Phases 20-22 (shipped)
- ✅ **v1.5 Bug Hunt, Cleanup & Hardening** — Phases 23-27 (shipped 2026-05-09) [Archive →](.planning/milestones/v1.5-ROADMAP.md)
- 🚧 **v1.6 Code Syntax Highlighting** — Phases 28-30 (in progress)

## Phases

<details>
<summary>✅ v1.0 – v1.5 (Phases 1-27) — All Shipped</summary>

### v1.0 MVP
5 phases, 30 requirements. Core chat, local inference, remote providers, presets, history.

### v1.1 LiteRT-LM Integration
5 phases, 26 requirements. Second local engine, GPU backend detection, model format handling.

### v1.2 GGUF Native Inference
5 phases, 27 requirements. llama.cpp JNI, Vulkan GPU, OOM handling, GGUF download/import.

### v1.3 Remote Provider Endpoints & UX
4 phases, 31 requirements. All provider types exposed, OpenAI/Anthropic/Ollama/LM Studio, MCP.

### v1.4 Onboarding Wizard
3 phases, 25 requirements. Full-screen 9-step wizard with context-aware content.

### v1.5 Bug Hunt, Cleanup & Hardening
5 phases, 26 requirements. GGUF removal, search simplification, 4 chat bugs fixed, 8 security hardening measures, wizard updated.

</details>

### 🚧 v1.6 Code Syntax Highlighting (In Progress)

**Milestone Goal:** Code blocks in AI responses and throughout the app render with language-aware syntax highlighting using 4 preset themes that auto-adapt to light/dark mode.

- [x] **Phase 28: Tokenization Engine & Theme System** — Domain models, Highlights library integration, language detection, 4 theme color schemes, syntax issue detection (completed 2026-05-14)
- [x] **Phase 29: UI Components & MarkdownText Refactoring** — CodeBlock composable, header bar, copy button, line numbers, expand/collapse, Settings dropdown, Hilt DI (completed 2026-05-15)
- [ ] **Phase 30: Streaming Integration & Everywhere Application** — Streaming-aware rendering, smooth transitions, performance, apply to model cards/readmes/app-wide

## Phase Details

### Phase 28: Tokenization Engine & Theme System
**Goal**: The syntax highlighting engine, language detector, and theme infrastructure are built, tested, and ready for UI integration.
**Depends on**: Nothing (first phase of v1.6)
**Requirements**: SYNX-01, SYNX-02, SYNX-03, THEM-01, THEM-02, THEM-04
**Success Criteria** (what must be TRUE):
  1. Token-level syntax coloring engine produces correct colored output for keywords, strings, comments, numbers, functions, types, and operators across all 12+ supported languages
  2. Language detection resolves markdown fence labels with 20+ common aliases (py→python, js→javascript, sh→bash, etc.)
  3. Language auto-detection identifies code language from content via keyword-frequency heuristics when no fence label is present, falling back to plain text
  4. 4 preset themes (Monokai, One Dark, GitHub, Dracula) defined with both light and dark color variants covering all 12 token types
  5. Theme selection persists across app restarts via DataStore with backward-compatible migration from existing CodeTheme enum
**Plans**: 3 plans

Plans:
- [x] 28-01-PLAN.md — Dependency wiring + domain models (TokenType, SyntaxToken, SyntaxColor, SyntaxTheme)
- [x] 28-02-PLAN.md — Implementation layer (LanguageDetector, SyntaxHighlighterImpl, TypeMapper, AdvancedPreferences migration, DI module)
- [x] 28-03-PLAN.md — Unit tests (LanguageDetector, TypeMapper, SyntaxHighlighterImpl, SyntaxTheme, migration logic)

### Phase 29: UI Components & MarkdownText Refactoring
**Goal**: Code blocks render with syntax-highlighted text, language header bar, copy button, line numbers, and expand/collapse in chat messages, with theme selection in Settings.
**Depends on**: Phase 28
**Requirements**: SYNX-05, THEM-03, CODE-01, CODE-02, CODE-03, CODE-04, CODE-05, INTG-01
**Success Criteria** (what must be TRUE):
  1. User sees syntax-colored code blocks in chat messages with a language header bar showing the detected language name above each code block
  2. User can tap a copy button on any code block to copy the raw code text, seeing a "Copied!" confirmation icon swap
  3. User sees line numbers alongside code and code blocks over 200 lines are collapsed with tap-to-expand
  4. User can change the code theme from the Settings page dropdown with a live preview color swatch for each option
  5. User sees code font size scaling relative to chat text and subtle visual warning indicators (warning icon) on code blocks with detected syntax issues
**Plans**: 3 plans

Plans:
- [x] 29-01-PLAN.md — MarkdownBlock sealed class hierarchy, parseMarkdown() function, MarkdownText refactored to block-based Column with SyntaxTheme
- [x] 29-02-PLAN.md — CodeBlock composable with syntax highlighting, language header bar, copy button, line numbers, expand/collapse, warning indicator
- [x] 29-03-PLAN.md — Full integration: ChatScreen/ChatViewModel/MessageBubble → SyntaxTheme, Settings ExposedDropdownMenuBox + code font scale slider, AdvancedPreferences persistence

### Phase 30: Streaming Integration & Everywhere Application
**Goal**: Syntax highlighting works smoothly during streaming and everywhere code blocks appear throughout the app, with no performance degradation.
**Depends on**: Phase 29
**Requirements**: SYNX-04, INTG-02, INTG-03, INTG-04, INTG-05, INTG-06
**Success Criteria** (what must be TRUE):
  1. User sees code render in flat monospace while streaming and smoothly fade into full syntax coloring when the closing fence arrives
  2. User sees syntax-highlighted code blocks in model card descriptions and README/markdown preview screens
  3. User sees syntax-highlighted code blocks everywhere they appear — onboarding, help screens, settings documentation
  4. User experiences no jank, flicker, or frame drops during streaming — scrolling through chats with multiple code blocks stays fluid under 16ms per frame
**Plans**: 2 plans

Plans:
- [ ] 30-01-PLAN.md — Streaming transition fix, codeFontScale wiring, HuggingFace description MarkdownText
- [ ] 30-02-PLAN.md — Compilation verification, coverage audit, performance guardrail checks

## Progress

| Phase | Milestone | Requirements | Plans Complete | Status | Completed |
|-------|-----------|-------------|----------------|--------|-----------|
| 23. GGUF Removal | v1.5 | GGUF-01..07 (7) | 7/7 | Complete | 2026-05-09 |
| 24. Search Simplification | v1.5 | SRCH-01..04 (4) | 4/4 | Complete | 2026-05-09 |
| 25. Bug Fixes | v1.5 | BUG-01..04 (4) | 4/4 | Complete | 2026-05-09 |
| 26. Security Hardening | v1.5 | SEC-01..08 (8) | 8/8 | Complete | 2026-05-09 |
| 27. Wizard Update | v1.5 | WZRD-01..03 (3) | 3/3 | Complete | 2026-05-09 |
| 28. Tokenization Engine & Theme System | v1.6 | SYNX-01..03, THEM-01,02,04 (6) | 0/3 | Planned | — |
| 29. UI Components & MarkdownText Refactoring | v1.6 | SYNX-05, THEM-03, CODE-01..05, INTG-01 (8) | 0/3 | Planned | — |
| 30. Streaming Integration & Everywhere Application | v1.6 | SYNX-04, INTG-02..06 (6) | 0/2 | Planned | — |
