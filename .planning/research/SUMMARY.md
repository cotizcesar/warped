# Project Research Summary

**Project:** Warped — Android LM-Studio-equivalent LLM chat app
**Domain:** Code syntax highlighting in Android/Compose AI chat application
**Researched:** 2026-05-14
**Confidence:** HIGH

## Executive Summary

Warped is an Android chat application for running and chatting with LLMs locally and remotely. The v1.6 milestone adds language-aware syntax highlighting to code blocks in AI responses — transforming flat monospace rendering into colored, token-aware display with language header bars, copy buttons, and 4 preset themes that auto-adapt to system light/dark mode.

The recommended approach is to **integrate the `dev.snipme:highlights:1.1.0` library as the tokenization engine**, wrapped behind a Clean Architecture `SyntaxHighlighter` domain interface. This resolves a key research conflict: STACK.md identified Highlights as the best library option (183 GitHub stars, 26 Maven Central dependents, pure Kotlin, actively maintained), while FEATURES/ARCHITECTURE/PITFALLS conducted library surveys that either missed or cursorily dismissed Highlights and defaulted to a fully custom regex tokenizer. Using Highlights eliminates ~750–1,400 lines of hand-written regex definitions across 15 languages while maintaining all Clean Architecture benefits via the domain interface — if Highlights ever becomes unmaintained, a single implementation swap suffices. The theme system, language auto-detector, and CodeBlock composable must be custom-built regardless (Highlights only provides 2 themes, no auto-detection, and no Compose rendering).

The two critical risks are: (1) **streaming performance** — re-tokenizing code on every ~50ms streaming token causes O(n²) jank, mitigated by deferring syntax highlighting until the closing ` ``` ` fence arrives (render flat monospace mid-stream, then apply full highlighting once); and (2) **recomposition thrashing** — `buildAnnotatedString` running on every `StateFlow` emission, mitigated by `remember(text, theme)` caching. The existing `MarkdownText` composable must be restructured from a single `Text(AnnotatedString)` to a block-based `Column` of composable blocks so code blocks can host a language header bar and copy button — elements that cannot exist inside an `AnnotatedString`.

## Key Findings

### Recommended Stack (from STACK.md)

The syntax highlighting pipeline consists of four components, only one of which is a new external dependency:

| Component | Choice | Reason |
|-----------|--------|--------|
| **Tokenization engine** | `dev.snipme:highlights:1.1.0` | Most mature pure-Kotlin syntax highlighter for Android. 183 stars, 26 Maven Central dependents. Supports 17 languages covering 95%+ of LLM output. Hand-written regex — zero native deps, zero JS interop. Apache 2.0 license. Version-compatible with project's Kotlin 2.3.20, coroutines 1.9.0, and kotlinx-serialization 1.7.x. |
| **AnnotatedString bridge** | Custom adapter (~50 lines) | Maps Highlights' `CodeHighlight` token types to Compose `SpanStyle` with per-token colors from the active theme palette. Integrates with the existing `buildAnnotatedString` pattern in `MarkdownText.kt`. |
| **Syntax color schemes** | Custom `SyntaxTheme` data classes | 4 themes × 2 variants (light/dark) × 12 token types = 96 color values defined as pure Kotlin. Highlights only provides Monokai and Atom One — One Dark, GitHub, and Dracula must be custom-defined regardless of engine choice. |
| **Language detector** | Custom two-tier heuristic | (1) Parse markdown fence ` ```python ` covering ~90% of LLM output; (2) Keyword-frequency heuristics for unspecified languages. No mature JVM language-detection library exists — this is custom regardless of engine choice. |

**Rejected alternatives:**
- **Custom regex tokenizer (recommended by FEATURES/ARCHITECTURE/PITFALLS):** Writing 12–15 language grammars from scratch (~750–1,400 lines) is unnecessary when Highlights provides the same regex-based tokenization with proven accuracy across its 26 Maven Central dependents. Both approaches need the same custom theme system, language detector, and Composables. The `SyntaxHighlighter` domain interface makes the engine swappable — start with Highlights, swap later if needed.
- **Prism4j:** Archived July 2023. Dead dependency.
- **kotlin-textmate:** v0.1.0 released today (May 14, 2026). 12 stars. Known limitations (no injection grammars, Joni regex fallback, not thread-safe). Too new for production.
- **WebView + highlight.js:** Destroys scroll performance, breaks Compose text selection, adds memory pressure.

### Expected Features (from FEATURES.md)

**Must have — P1 (table stakes):**
- **Language-aware syntax coloring** — colored keywords, strings, comments, numbers, functions, types, operators via tokenized `AnnotatedString`. This is what differentiates "code rendering" from "syntax highlighting."
- **Fence language detection** — parse ` ```python ` specifiers with alias mapping (`py` → `python`, `js` → `javascript`). Covers ~90% of LLM cases.
- **4 preset themes** — Monokai, One Dark, GitHub, Dracula, each with light and dark variants. Theme selection persists via existing DataStore flow.
- **Light/dark auto-adaptation** — selects variant matching system `isSystemInDarkTheme()`. Rare in mobile chat apps — competitive differentiator.
- **Language header bar** — shows detected language name above each code block.
- **Copy-to-clipboard button** — in header bar, copies raw code text (not `AnnotatedString`). Table stakes — every major chat app has this.
- **Applied in chat messages** — syntax highlighting visible during and after streaming.

**Should have — P2 (differentiators, can slip to v1.7):**
- **Heuristic auto-detection** — detect language from code content when fence specifier is missing. Adds polish but LLMs specify language >90% of the time.
- **Applied everywhere** — extend to model card descriptions, README previews, onboarding content.

**Anti-features (not in scope):**
- WebView-based rendering (performance killer)
- 190+ language support (maintenance burden; 15 covers >95% of LLM output)
- Line numbers (wastes narrow mobile screen space)
- Custom theme builder/editor (massive UX complexity for marginal value)
- Syntax error highlighting (misleading for LLM-generated code; requires full parsers)

### Architecture Approach (from ARCHITECTURE.md)

The integration follows Clean Architecture with a new `domain/highlighting/` package for interfaces and models, and `data/highlighting/` for implementations:

**Major components:**

1. **`SyntaxHighlighter` (domain interface)** — `fun highlight(code, language, theme): AnnotatedString`. Pure Kotlin, zero Android/Compose deps. Implemented by `RegexSyntaxHighlighter` wrapping Highlights.

2. **`LanguageDetector` (data utility)** — heuristic detection with priority chain: fence specifier → shebang → keyword frequency → "text" fallback. "text" (no highlighting) is better than wrong highlighting.

3. **`CodeTheme` enum → `SyntaxTheme` bridge** — enum persists via DataStore (unchanged key); maps to rich `SyntaxTheme` objects with 12 token-type colors per variant. Migration-safe: old enum names map to new theme objects.

4. **`CodeBlock` composable (new)** — replaces inline code rendering in `MarkdownText`. Renders a `Surface` with language header bar, copy button, and syntax-highlighted `AnnotatedString`. Allows UI elements that can't exist inside `buildAnnotatedString`.

5. **`MarkdownText` (refactored)** — detects code fences as before but delegates content + language hint to `CodeBlock` composable instead of rendering inline. Inline code (single backticks) unchanged.

**What does NOT change:** `ChatUiState`, `ChatViewModel`, `ChatRepository`, Room database, Retrofit APIs, `MessageBubble` (passes `codeTheme` through as before), `NavGraph`, `SelectionContainer`.

**Suggested wave structure** (8 waves in ARCHITECTURE.md) consolidates into 3 roadmap phases (see Roadmap Implications below).

### Critical Pitfalls (from PITFALLS.md)

1. **Streaming O(n²) jank (Pitfall #2):** Re-tokenizing the entire code block on every ~50ms streaming token. **Prevention:** Defer syntax highlighting until closing ` ``` ` fence arrives. Render flat monospace mid-stream, apply full highlighting once. This matches how ChatGPT and LM Studio behave.

2. **Recomposition thrashing (Pitfall #6):** `buildAnnotatedString` runs on every `StateFlow` emission, not just when text changes. **Prevention:** `remember(text, theme) { buildAnnotatedString { ... } }` — only recompute when inputs actually change.

3. **Theme data model gap (Pitfall #3):** Existing `CodeTheme` enum has only 2 colors (`bgCode`, `bgInline`). Syntax highlighting needs 12 token-type colors per theme. **Prevention:** Expand to `SyntaxTheme` data class with `tokenColors: Map<TokenType, Color>` maps. Use existing DataStore key — `CodeTheme` enum name maps to new `SyntaxTheme` objects.

4. **Language detection inconsistency (Pitfall #5):** Wrong detection = wrong colors = user confusion. **Prevention:** Fence info is authoritative. Content-based detection only when fence is absent. Always show detected language in header bar so the user can verify. "Plain text" fallback is better than wrong highlighting.

5. **Code block flicker during streaming (Pitfall #7):** Gray monospace → colored tokens transition when closing fence arrives. **Prevention:** Keep background color consistent during transition. Use `animateColorAsState()` on token text colors for smooth fade.

## Implications for Roadmap

Based on combined research, the build order from ARCHITECTURE.md (8 waves) and the pitfall-to-phase mapping from PITFALLS.md consolidate naturally into 3 roadmap phases:

### Phase 1: Tokenization Engine & Theme System

**Rationale:** Everything depends on the tokenizer and theme data model. Language definitions, token types, and theme color maps must exist before any UI can render them. This phase is pure Kotlin (zero Compose/Android deps) — fully unit-testable without emulator.

**Delivers:**
- `domain/highlighting/` — `CodeToken` sealed class, `Language` enum, `LanguageDefinition` data class, `SyntaxHighlighter` interface
- `data/highlighting/definitions/` — 12 language definition files with Highlights-compatible token patterns
- `data/highlighting/theme/` — `HighlightingTheme` interface + 4 theme implementations (Monokai, One Dark, GitHub, Dracula) with light/dark variants
- `data/highlighting/RegexSyntaxHighlighter.kt` — implementation wrapping Highlights engine via `SyntaxHighlighter` interface
- `data/highlighting/LanguageDetector.kt` — priority-chain language detection (fence → shebang → keyword heuristics → plain)
- `ui/theme/CodeTheme.kt` — migrated + expanded enum with `toHighlightingTheme()` bridge and `previewColor`
- Import updates across `MarkdownText.kt`, `ChatUiState.kt`, `ChatViewModel.kt`, `AdvancedPreferences.kt`, `SettingsScreen.kt`, `MessageBubble.kt`

**Features from FEATURES.md:** Fence language detection, syntax-highlighted code blocks (engine only), 4 preset themes (data), light/dark auto-adaptation (data)

**Pitfalls avoided:** #1 (no Prism4j — using Highlights), #3 (theme data model expanded), #5 (language detection priority chain)

**Research needed:** MEDIUM — Highlights API exploration for language definition format and `CodeHighlight` token type mapping. Plan for `/gsd-research-phase` if token type mapping proves complex.

### Phase 2: UI Components & MarkdownText Refactoring

**Rationale:** The rendering pipeline needs to shift from a single `Text(AnnotatedString)` to a block-based `Column` of composable blocks. The `CodeBlock` composable cannot be built until the tokenization engine exists (Phase 1). This is the largest user-facing change — the composable block model unlocks the language header bar and copy button that differentiate Warped from competitors.

**Delivers:**
- `ui/chat/components/CodeBlock.kt` — new composable: language header bar + copy button + syntax-highlighted `AnnotatedString` rendering
- `ui/chat/components/MarkdownText.kt` — refactored: code fence detection delegates to `CodeBlock`; inline code and other markdown unchanged
- `ui/settings/SettingsScreen.kt` — updated theme dropdown with preview color swatch
- `di/HighlightingModule.kt` — Hilt `@Module` providing `SyntaxHighlighter` and `LanguageDetector` as `@Singleton`

**Features from FEATURES.md:** Language header bar, copy-to-clipboard button, applied in chat messages, composable block rendering

**Uses from STACK.md:** Highlights 1.1.0 (via `RegexSyntaxHighlighter`), Compose `buildAnnotatedString`, `LocalClipboardManager`, custom `AnnotatedString` bridge, custom `SyntaxTheme` color schemes

**Pitfalls avoided:** #4 (copy raw text, not `AnnotatedString` spans), #8 (code blocks extracted as separate composables, not inline in `buildAnnotatedString`)

**Research needed:** LOW — standard Compose patterns, well-documented. Skip `/gsd-research-phase`. May benefit from `/gsd-ui-phase` for the CodeBlock design contract.

### Phase 3: Streaming Integration & Performance

**Rationale:** The core streaming pitfall (#2) must be addressed before the feature ships — highlighting during streaming causes O(n²) jank. This phase is intentionally last because it validates Phase 1 + 2 together under streaming conditions. Deferred highlighting (flat monospace mid-stream → colored on block completion) is the recommended strategy for v1.6.

**Delivers:**
- Streaming-aware code block rendering: flat monospace while ` ``` ` fence is open, syntax highlighting applied once fence closes
- `remember(text, theme)` caching around `buildAnnotatedString` to prevent recomputation on unrelated `StateFlow` emissions
- Language detection cached via `remember(codeBlockContent)` to avoid re-detection on every recomposition
- Smooth color transition from flat monospace to highlighted via `animateColorAsState()`
- Performance validation: < 2ms `SyntaxHighlighter.highlight()` for 50-line code blocks
- Streaming validation: no flicker during recomposition, no frame drops during streaming
- Edge case handling: malformed code, empty blocks, code with only special characters, partial language specifiers

**Features from FEATURES.md:** Streaming-aware rendering (completes chat message integration)

**Pitfalls avoided:** #2 (O(n²) streaming — deferred highlighting), #6 (recomposition thrashing — `remember` caching), #7 (code block flicker — consistent background + `animateColorAsState`)

**Research needed:** LOW — performance optimization patterns are well-understood. Skip `/gsd-research-phase`. Focus on measurement and validation.

### Phase Ordering Rationale

- **Phase 1 must come first** — all rendering depends on the tokenizer, language detector, and theme definitions. These are pure Kotlin with no UI deps, enabling fast unit testing.
- **Phase 2 must follow Phase 1** — the `CodeBlock` composable needs `SyntaxHighlighter`, `LanguageDetector`, and `SyntaxTheme` to render anything. The composable block model is a prerequisite for the language header bar and copy button.
- **Phase 3 is intentionally last** — it validates Phase 1 + 2 together under real streaming conditions. Deferred highlighting is the performance strategy; it can only be verified after the rendering pipeline works.
- This ordering also follows Clean Architecture: domain → data → UI, with integration validation at the end.

### Research Flags

**Phases likely needing deeper research during planning:**
- **Phase 1:** Highlights API integration — the `CodeHighlight` token type system must be mapped to our `CodeToken` sealed class. If the mapping is complex or Highlights' internal token types don't align with the 12-token taxonomy from FEATURES.md, a `/gsd-research-phase` spike is warranted before planning.

**Phases with standard, well-documented patterns (skip `/gsd-research-phase`):**
- **Phase 2:** Standard Compose patterns — `Surface`, `Row`, `IconButton`, `buildAnnotatedString`, `LocalClipboardManager`. May benefit from `/gsd-ui-phase` for the CodeBlock visual design contract.
- **Phase 3:** Performance optimization with `remember`, `derivedStateOf`, and `animateColorAsState` — all well-documented Compose APIs.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | **HIGH** | Highlights 1.1.0 confirmed via Maven Central POM inspection — version, transitive deps, and Kotlin compatibility verified. All version compatibility checks passed against project's version catalog. Custom theme/language-detector/adapter need is unambiguous. |
| Features | **HIGH** | Competitive analysis done against ChatGPT Android, Claude Android, and LM Studio Desktop. Token taxonomy sourced from Prism.js industry standard. Library survey (Prism4j archived status, Sora Editor overkill, kotlin-textmate immaturity) independently verified. MVP definition clear with P1/P2/P3 priorities. |
| Architecture | **HIGH** | Existing codebase inspected directly (`MarkdownText.kt`, `MessageBubble.kt`, `ChatViewModel.kt`, `ChatUiState.kt`, `AdvancedPreferences.kt`, `SettingsScreen.kt`). Integration points mapped precisely — every component's change status (new/refactored/unchanged) documented. 8-wave build order provides granular sequencing. |
| Pitfalls | **HIGH** | Each pitfall grounded in existing code patterns (e.g., `buildAnnotatedString` in composable body without `remember` is observable in current `MarkdownText.kt` line 45). Streaming pitfalls verified against `ChatViewModel.kt` 50ms emission interval. Prevention strategies are concrete Kotlin snippets, not abstract advice. |

**Overall confidence:** HIGH — all four research files drew from primary sources (Maven Central, GitHub repos, direct codebase inspection, official Android documentation). No findings depend on inference or single sources.

### Gaps to Address

- **Highlights `CodeHighlight` → `CodeToken` mapping:** The exact token type taxonomy of Highlights needs verification during Phase 1 planning. If Highlights uses fewer token types than the 12 defined in FEATURES.md, a mapping layer is straightforward. If it uses more, some types may collapse. **Handle during:** Phase 1 plan discussion or a `/gsd-spike` before planning.

- **Language auto-detection accuracy:** The keyword-frequency heuristic for 15 languages has not been benchmarked against real LLM code block output. The 90% fence-coverage claim (LLMs specify language in fence >90% of the time) is a reasonable estimate but unverified. **Handle during:** Phase 2 or 3 — auto-detection is a P2 feature. If accuracy proves low during testing, defer fully to v1.7.

- **Long code block performance (>500 lines):** Highlights' regex tokenizer performance on large code blocks has not been benchmarked on representative Android hardware. The `remember` caching mitigates recomposition but initial tokenization cost is unknown. **Handle during:** Phase 3 performance validation. Cap rendering at 200 lines with "Show all" expander if latency exceeds 16ms.

- **Data migration: old `CodeTheme` enum → new `SyntaxTheme`:** The migration path (store enum name, map to new theme objects) is conceptually sound but the exact migration code needs validation against the current `KEY_CODE_THEME` DataStore key and existing user data. **Handle during:** Phase 1 — test with a pre-migration DataStore snapshot.

## Sources

### Primary (HIGH confidence)
- **Highlights GitHub** (SnipMeDev/Highlights) — 183 stars, 26 dependents, version 1.1.0, 17 languages, Apache 2.0 license. Repository README, sample code, language list, theme documentation.
- **Highlights Maven Central** — Version 1.1.0 confirmed with POM showing kotlin-stdlib 2.2.0, kotlinx-coroutines 1.9.0, kotlinx-serialization-json 1.7.1.
- **Prism4j GitHub** (noties/Prism4j) — Confirmed ARCHIVED July 2023. Last release June 2019. Read-only repository.
- **kotlin-textmate GitHub** (ivan-magda/kotlin-textmate) — v0.1.0 released 2026-05-14. 12 stars. Known limitations documented in README.
- **Warped codebase** — Direct inspection of `MarkdownText.kt`, `MessageBubble.kt`, `ChatViewModel.kt`, `ChatUiState.kt`, `AdvancedPreferences.kt`, `SettingsScreen.kt`, `ChatMessage.kt`. Current `CodeTheme` enum structure, DataStore persistence, Compose rendering approach, and streaming at 50ms intervals confirmed.
- **Prism.js token documentation** (prismjs.com/tokens.html) — Industry-standard token type taxonomy: keyword, string, number, comment, function, type, operator, punctuation, boolean, builtin, variable, constant, plain.
- **Jetpack Compose official docs** — `buildAnnotatedString`, `LocalClipboardManager`, performance/stability guidance (stability, `remember`, recomposition skipping).

### Secondary (MEDIUM confidence)
- **Sora Editor** (rosemoe/sora-editor) — Context7 docs confirming TextMate/TreeSitter approach exists for Android, but designed for code editors, not read-only rendering.
- **Highlight.js auto-detection** (highlightjs.org) — Reference for Bayesian classifier + keyword matching approach. Adaptation simplified for mobile (heuristic-only).
- **Competitive analysis** — ChatGPT Android (WebView, language header, copy button, dark-only), Claude Android (copy button, no language header, dark-only), LM Studio Desktop (language header, copy button, follows app theme). Based on personal usage observation.

### Tertiary (LOW confidence)
- **Android syntax highlighting library landscape** — Secondary search of Maven Central may surface niche alternatives beyond those evaluated. Likelihood of finding a better fit: LOW. This is a genuinely underserved niche on Android.

---

*Research completed: 2026-05-14*
*Ready for roadmap: yes*
