# Milestones

## v1.7 App Optimization & Smart Presets (Shipped: 2026-05-25)

**Phases completed:** 4 phases (31-34), 4 plans, 19 requirements
**Requirements:** 19 defined, 19 satisfied
**Known deferred items at close:** 0

**Key accomplishments:**

1. LiteRT-LM upgraded from v0.11.0 to v0.12.0 with compilation verified and all existing functionality preserved
2. Unified Models & Endpoints Selector: single screen showing 1 local model (with connect/disconnect toggle) alongside infinite remote endpoint models
3. Traffic light status indicator (semaforo) integrated in ChatScreen TopAppBar for both local and remote providers
4. Smart Memory-Based Presets: dynamically calculated optimal preset based on available device RAM and selected model size, with manual override capability
5. All 19 requirements satisfied across 4 phases, compilation clean, no tech debt introduced

---

## v1.6 Code Syntax Highlighting (Shipped: 2026-05-15)

**Phases completed:** 3 phases (28-30), 8 plans, 22 tasks
**Requirements:** 20 defined, 18 satisfied, 1 deferred (INTG-03), 1 pending human verification (Phase 29)
**Known deferred items at close:** 18 (6 verification gaps + 12 quick tasks — see STATE.md Deferred Items)

**Key accomplishments:**

1. Highlights 1.1.0 integrated as syntax tokenization engine with 4 domain models — TokenType (13 enum values), SyntaxToken, SyntaxColor, and SyntaxTheme with Monokai/One Dark/GitHub/Dracula presets, each holding complete 13-color light/dark color tables
2. Syntax highlighting engine core: 43-entry LanguageDetector with 20+ alias mappings and keyword-frequency auto-detection, stateless TypeMapper with gap-fill and overlap resolution, Highlights-backed SyntaxHighlighterImpl with LRU cache, and DataStore-backed theme persistence with backward-compatible CodeTheme migration
3. 150 comprehensive unit tests covering language detection, token mapping, syntax highlighting integration, and theme validation — all passing
4. Block-based markdown rendering: MarkdownBlock sealed hierarchy (8 variants), pure parseMarkdown() function with LanguageDetector, and MarkdownText refactored from single Text(AnnotatedString) to composable Column enabling per-block UI elements
5. CodeBlock composable (545 lines): syntax-colored tokens via animateColorAsState, language header bar with copy button, line number gutter, expand/collapse for blocks over 200 lines, and syntax issue warning indicator
6. Full chat + settings integration: SyntaxTheme flows from Settings dropdown (with 4-color swatch preview) through ViewModels to all chat composables. Code font scale slider (0.8x–1.5x) wired end-to-end
7. Streaming transition fix: LaunchedEffect key includes isStreaming — flat monospace during active streaming, full syntax highlighting on closing fence. Code font scale wired through full 6-hop composable chain
8. Compilation verified, monospace coverage audit confirms canonical rendering (all FontFamily.Monospace usage flows through CodeBlock/MarkdownText), HuggingFace model descriptions render with MarkdownText

### Deferred
- **INTG-03:** README/markdown preview screen with syntax highlighting — requires new HuggingFace API endpoint and preview screen
- **Phase 29:** 4 human UI verification checks pending (theme dropdown, font scale slider, CodeBlock rendering, expand/collapse) — require device/emulator
- **Pre-existing:** 6 verification gaps (Phases 06-10, 29) + 12 quick tasks from earlier milestones

---
