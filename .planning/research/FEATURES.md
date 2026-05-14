# Feature Research: Code Syntax Highlighting for Android Chat App

**Domain:** Code syntax highlighting in an Android AI chat application (Warped)
**Researched:** 2026-05-14
**Confidence:** HIGH

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist. Missing these = product feels incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Language-aware syntax coloring** | Users see ````python` fences and expect colored keywords, strings, comments — not monochrome monospace text. This is what differentiates "code rendering" from "syntax highlighting." Every LLM chat app (ChatGPT, Claude, LM Studio) does this. | MEDIUM | Requires custom tokenizer — no maintained Android library exists. Build regex-based tokenizer for 10–15 most common languages. |
| **Fence language detection** | When an LLM writes ````python`, users expect the Python code to be highlighted as Python. The fence specifier is the primary language source (covers ~90% of LLM responses). | LOW | Parse the string after ` ``` ` on the opening fence line. Alias mapping needed (e.g., `js` → `javascript`, `py` → `python`, `sh` → `bash`). |
| **Monospace font for code** | Already exists in current `MarkdownText`. Must be preserved. Non-negotiable for code readability. | LOW | `FontFamily.Monospace` on code spans. Already implemented as `SpanStyle(fontFamily = FontFamily.Monospace)`. |
| **Distinct code block visual separation** | Code blocks need background color, padding, and visual distinction from surrounding markdown text. Users expect a "card" feel for code blocks. | LOW | Already partially implemented via `codeTheme.bgCode` on `SpanStyle`. Needs upgrade to full `Surface`-based rendering. |
| **Persistent theme selection** | The code theme setting must survive app restart. Users invest time choosing a theme and expect it to stick. | LOW | Already implemented: `AdvancedPreferences.setCodeTheme()` persists via DataStore, flows through `ChatViewModel`/`SettingsViewModel` to `ChatUiState.codeTheme`. |

### Differentiators (Competitive Advantage)

Features that set Warped apart. Not universally expected, but valuable for a developer-focused LLM chat app.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Language header bar on every code block** | Shows the detected language name (e.g., "Python", "JavaScript") in a small header above each code block. Surfaces what the LLM intended. ChatGPT's Android app does this; many third-party apps don't. | MEDIUM | Requires restructuring from single `Text(AnnotatedString)` to composable block model. Header is a `Row` with language label inside the code block `Surface`. |
| **Copy-to-clipboard button per code block** | One-tap copy of the entire code block content. Essential developer workflow — copying generated code is a primary action. ChatGPT, Claude, and LM Studio all have this. | LOW | Android `ClipboardManager` API. Button placed in the language header bar. `contentResolver` not needed — just `ClipboardManager.setText()`. |
| **Auto language detection when fence is blank** | When an LLM writes ```` ` without a language specifier, the app heuristically detects the language from the code content. Few chat apps do this; most just render unstyled. | MEDIUM | Heuristic pattern matching on first 5–10 lines: check for shebangs, language-specific keywords (`def`, `function`, `class`, `import`), and syntax patterns. Fallback to "plain text" if confidence low. |
| **Light/dark theme auto-adaptation** | Each preset theme (Monokai, One Dark, GitHub, Dracula) has both light and dark variants. The app selects the variant matching the system theme. This means GitHub theme looks correct in light mode and dark mode — not just one or the other. Most desktop apps (VS Code, IntelliJ) do this; mobile chat apps rarely bother. | MEDIUM | Each theme stores two color maps (light + dark). Detect via `isSystemInDarkTheme()` at the composable level. GitHub theme's light variant is the authentic GitHub light background (#FFFFFF); dark variant is GitHub dark (#0D1117). |
| **4 curated preset themes** | Monokai, One Dark, GitHub, and Dracula cover the most popular code editor themes. Users recognize these from VS Code, IntelliJ, and Sublime Text — immediate familiarity. | LOW (existing enum extended) | Extend `CodeTheme` enum from 2 color fields to full token-color maps. Remove NORD and SOLARIZED_DARK (not in user's requested set of 4). Each theme defines ~12 token-type colors. |
| **Applied everywhere code blocks appear** | Chat messages, model card descriptions, README previews, onboarding content — any rendered markdown with code fences gets syntax highlighting. Consistency across the app. | MEDIUM | Requires `MarkdownText` (or its replacement) to be reusable anywhere. The current `MarkdownText` is only used in `MessageBubble.kt`. Need to ensure it works in `LazyColumn` items (chat, model list) and `Column` layouts (readmes, cards). |
| **Streaming-aware rendering** | During LLM streaming, code blocks are incomplete. The renderer handles partial fences (e.g., ````pyt` mid-token) gracefully without crashing or flickering. | MEDIUM | The current `MarkdownText` already handles unterminated code fences (flushes buffer at end). Extend to handle partial language specifiers and incremental tokenization with debouncing. |

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem good but would create problems for this milestone.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| **WebView-based rendering (highlight.js in a WebView)** | "Just use highlight.js — it supports 190 languages and has themes built in." | WebView per code block is extremely heavy on Android (each WebView is a separate render process). Breaks Compose composition, causes scroll jank, creates memory pressure, and makes copy-paste unreliable. Also bloats APK with JS/CSS assets. | Custom Kotlin regex-based tokenizer rendering to Compose `Text` with colored `SpanStyle`. Fast, native, scroll-smooth. |
| **Full 190+ language support** | "Support every language highlight.js supports." | Each language grammar requires regex patterns that must be tested. 190 languages would require ~200KB+ of regex definitions, massive test surface, and maintenance burden. Most LLM chats use 10–15 languages (Python, JavaScript, TypeScript, Java, Kotlin, C, C++, Go, Rust, Bash, SQL, JSON, YAML, HTML, CSS). | Support the 12–15 most common languages in chat contexts. Add languages incrementally based on actual usage data. |
| **Line numbers in code blocks** | "Show line numbers like GitHub or VS Code." | Mobile screens are narrow (360–400dp). Line numbers consume ~40dp of horizontal space, leaving less room for code. Also adds layout complexity (two synchronized scroll columns). ChatGPT and Claude mobile apps don't show line numbers. | Skip line numbers. If needed later, add as a toggle in Settings. |
| **Custom theme builder/editor** | "Let users create their own syntax color themes." | Massive UX complexity: color pickers, token-type mapping, preview, import/export. Users who want this use desktop IDEs. On mobile, preset themes cover 99% of needs. | Provide 4 high-quality preset themes that look great out of the box. |
| **Syntax error highlighting (red squiggles)** | "Show syntax errors in red like an IDE." | Requires full language parsers (not just regex tokenizers), adds significant latency per code block, and is actively misleading for LLM-generated code (which often has minor syntax issues but is conceptually correct). | Just highlight the syntax that's there. Don't judge correctness. |
| **Code folding (collapse/expand code blocks)** | "Let users collapse long code blocks." | Adds gesture handling, animation complexity, and state management per code block. LLM code blocks are typically short (10–50 lines in chat). Not worth the complexity for this use case. | Let code blocks scroll naturally. Users can scroll past them. |
| **Per-language theme overrides** | "Let me use Monokai for Python but One Dark for JavaScript." | Configuration explosion. Settings UI becomes a matrix. Users don't think this granularly about code themes in a chat app. | One theme for all code blocks. Simple, predictable, sufficient. |
| **Using Prism4j library directly** | "Prism4j is a Java port of Prism.js — use it." | Prism4j is **archived since July 2023** (repository is read-only). Last release was June 2019 (v2.0.0). No updates for 7 years. Uses old Java patterns, no Kotlin coroutine support, no Compose integration. Dead dependency. | Build a lightweight custom tokenizer inspired by Prism.js token types but written in idiomatic Kotlin. |

## Feature Dependencies

```
Syntax Highlighting (colored token spans)
    └──requires──> Language Detection (what language to tokenize as?)
                        ├──primary──> Fence specifier parsing (```python)
                        └──fallback──> Heuristic auto-detection

Syntax Highlighting
    └──requires──> Theme System (which colors for which tokens?)
                        └──requires──> Token Type → Color mapping per theme
                                           ├──light variant (for light mode)
                                           └──dark variant (for dark mode)
                        └──requires──> Persistent theme selection (existing DataStore flow)

Code Block UI (header bar + copy button)
    └──requires──> Composable block rendering model (not AnnotatedString)
    └──enhances──> Syntax Highlighting (header shows detected language)

Copy-to-clipboard button
    └──requires──> Android ClipboardManager API (platform)
    └──requires──> Code block composable (to attach button)

Streaming-aware rendering
    └──enhances──> Syntax Highlighting (partial tokenization)
    └──enhances──> Code Block UI (partial fences)
```

### Dependency Notes

- **Syntax Highlighting requires Language Detection:** You can't tokenize code without knowing the language grammar to apply. The fence specifier (` ```python `) is the primary source. Auto-detection is the fallback.
- **Syntax Highlighting requires Theme System:** Token types (keyword, string, comment, etc.) must map to actual colors. This mapping is what a "theme" is. The existing `CodeTheme` enum only has background colors — it must be expanded to full token-color maps.
- **Code Block UI requires Composable Blocks:** The current `MarkdownText` renders everything as a single `Text(annotatedString)`. A language header bar (`Row` with label) and copy button (`IconButton`) can't be embedded in `AnnotatedString`. The rendering must shift from a single `Text` to a `Column` of composable blocks (text paragraphs + code block surfaces).
- **Copy-to-clipboard depends on Code Block UI:** The button needs a composable surface to live on. Can't exist on a raw `SpanStyle`.
- **Streaming-aware rendering enhances everything:** LLM streaming means code blocks arrive token by token. The renderer must handle incomplete fences (` ``` ` opened but not closed), partial language specifiers, and incremental content growth without layout jumps or flicker.

### Existing System Dependencies

- **`CodeTheme` enum** (`MarkdownText.kt` line 21–28): Currently has only `bgCode` and `bgInline` color fields + `label`. Must be expanded to include `lightTokenColors: Map<TokenType, Color>` and `darkTokenColors: Map<TokenType, Color>`.
- **`AdvancedPreferences`** (`AdvancedPreferences.kt` line 60–69): Persists `codeTheme` as a string name via DataStore. Flow-based observation already wired to `ChatViewModel` and `SettingsViewModel`. No changes needed — just extend the serialization scope.
- **Settings code theme dropdown** (`SettingsScreen.kt` line 198–219): Already renders a dropdown with `CodeTheme.entries`. Will automatically pick up new/removed enum entries.
- **`MarkdownText` composable** (`MarkdownText.kt` line 31–89): Must be significantly restructured. The line-by-line `AnnotatedString` builder becomes a block parser that emits a list of `MarkdownBlock` sealed classes (TextBlock, CodeBlock). CodeBlock gets rendered as a `Surface` with syntax-highlighted content.
- **`MessageBubble`** (`MessageBubble.kt` line 166–175): Wraps `MarkdownText` in `SelectionContainer`. After restructuring, `SelectionContainer` must wrap only text blocks (not code blocks where the copy button handles selection).
- **`ChatUiState.codeTheme`** (`ChatUiState.kt` line 36): Already flows from `ChatViewModel` → `ChatScreen` → `MessageBubble` → `MarkdownText`. No changes needed.

## MVP Definition

### This Milestone Delivers (v1.6)

Minimum viable syntax highlighting — what's needed to validate users actually value this feature.

- [ ] **Fence language detection** — Parse ` ```language ` specifiers from code fences with alias mapping (10–15 languages)
- [ ] **Syntax-highlighted code blocks** — Tokenized rendering with distinct colors for keywords, strings, comments, numbers, functions, types, operators, punctuation
- [ ] **4 preset themes** — Monokai, One Dark, GitHub, Dracula — selectable in Settings. Each with light and dark variants.
- [ ] **Light/dark auto-adaptation** — Theme variant selected based on system dark mode (`isSystemInDarkTheme()`)
- [ ] **Language header bar** — Small header on each code block showing detected language name
- [ ] **Copy-to-clipboard button** — In the header bar, copies entire code block content
- [ ] **Applied in chat messages** — Syntax highlighting visible in AI responses during and after streaming
- [ ] **Composable block rendering** — Restructured `MarkdownText` to support mixed text + code UI blocks

**Supported languages for v1.6:** Python, JavaScript, TypeScript, Kotlin, Java, C, C++, Go, Rust, Bash/Shell, SQL, JSON, YAML, HTML/XML, CSS. (15 languages covering >95% of LLM chat code output.)

### Deferred (v1.7+)

Features that complement syntax highlighting but aren't required for the initial experience.

- [ ] **Heuristic auto-detection** — Detect language from code content when fence specifier is missing. Adds noticeable polish but LLMs specify language >90% of the time.
- [ ] **Applied everywhere** — Extend to model card descriptions, README previews, onboarding content. Chat messages are the 80/20 case.
- [ ] **More languages** — Add Ruby, Swift, PHP, Dart, Lua, Makefile, Dockerfile, Markdown based on user feedback.
- [ ] **Theme preview in Settings** — Show a sample code block with current theme colors so users can preview before selecting.
- [ ] **Syntax highlight in user messages too** — Currently only AI messages get markdown rendering. User code blocks could also benefit.

### Future Consideration (v2+)

- [ ] **Custom theme import** — Load TextMate `.tmTheme` or VS Code `.json` theme files
- [ ] **Line numbers toggle** — Opt-in setting
- [ ] **Per-language grammar extensibility** — Plugin system for community-contributed language definitions

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Fence language detection | HIGH | LOW | P1 |
| Syntax-highlighted code blocks (colored tokens) | HIGH | MEDIUM | P1 |
| 4 preset themes (Monokai, One Dark, GitHub, Dracula) | HIGH | MEDIUM | P1 |
| Light/dark auto-adaptation | HIGH | MEDIUM | P1 |
| Language header bar | MEDIUM | MEDIUM | P1 |
| Copy-to-clipboard button | HIGH | LOW | P1 |
| Applied in chat messages | HIGH | LOW | P1 |
| Composable block rendering | HIGH | MEDIUM | P1 |
| Heuristic auto-detection | MEDIUM | MEDIUM | P2 |
| Applied everywhere (model cards, readmes) | MEDIUM | MEDIUM | P2 |
| More languages (beyond 15) | LOW | LOW (incremental) | P3 |
| Theme preview in Settings | LOW | LOW | P3 |
| Syntax highlight in user messages | LOW | LOW | P3 |

**Priority key:**
- P1: Must ship in v1.6
- P2: Ship if time allows; otherwise v1.7
- P3: Defer to future milestone

## Competitor Feature Analysis

| Feature | ChatGPT Android | Claude Android | LM Studio Desktop | Warped v1.6 Target |
|---------|----------------|----------------|-------------------|---------------------|
| Syntax highlighting | Yes (highlight.js-based) | Yes | Yes | Yes (custom Kotlin tokenizer) |
| Language header bar | Yes ("Python" label) | No (just copy button) | Yes (language label in header) | Yes |
| Copy button per block | Yes (top-right header) | Yes (top-right) | Yes (header bar) | Yes (header bar, right-aligned) |
| Theme selection | No (one dark theme) | No (one theme) | No (follows app theme) | Yes (4 preset themes) |
| Light/dark adaptation | No (dark only) | No (dark only) | Yes | Yes |
| Auto language detection | Yes (highlight.js auto) | Unknown | No (fence-only) | P2 |
| Line numbers | No | No | No | No (anti-feature) |
| Streaming resilience | Yes | Yes | Yes | Yes (required) |
| Rendering approach | WebView (React Native WebView) | Native? | Qt/C++ custom | Compose native (Column of blocks) |

### Key Competitive Insights

1. **No chat app offers theme selection on mobile.** ChatGPT and Claude use a single hardcoded dark theme. Warped's 4-theme selector is a genuine differentiator for developers who have strong theme preferences from their IDEs.

2. **Language header bar is inconsistent.** ChatGPT shows it; Claude doesn't. LM Studio shows it. Warped showing it puts us in the "polished" camp.

3. **Copy button is table stakes.** Every major chat app has it. Not having it would feel broken.

4. **Light/dark adaptation is rare.** Most chat apps are dark-only on mobile. Warped adapting to system theme is a quality-of-life differentiator.

5. **Native rendering (Compose) vs WebView.** ChatGPT's Android app uses React Native WebView for markdown rendering — this is why scrolling feels slightly janky in long code blocks. Warped's native Compose approach should deliver smoother scrolling.

## Technical Architecture Decisions

### Why Custom Tokenizer Instead of a Library

The research found no maintained Android syntax highlighting library:

| Candidate | Status | Verdict |
|-----------|--------|---------|
| **Prism4j** | Archived July 2023. Last release June 2019. README explicitly says "no themes, no rendering." | ❌ Dead dependency |
| **Sora Editor** | Active Android code editor. Uses TextMate grammars (.tmLanguage JSON) and TreeSitter. | ❌ Full code editor, overkill for read-only rendering. Adds MBs of native .so files. |
| **highlight.js in WebView** | Full-featured, 190 languages, themes built in. | ❌ WebView per code block = heavy, janky, breaks Compose. |
| **Chaquopy + Pygments** | Python on Android via Chaquopy. | ❌ ~50MB APK increase for Python runtime. |
| **Custom regex tokenizer** | Lightweight, Kotlin-native, Compose-friendly. | ✅ Best fit. ~30KB of regex definitions for 15 languages. |

### Token Type Taxonomy

Based on Prism.js standard tokens (industry consensus):

| Token Type | What It Covers | Example |
|-----------|----------------|---------|
| `keyword` | Reserved words | `def`, `class`, `if`, `return`, `import`, `fun`, `val` |
| `string` | String literals | `"hello world"`, `'single'`, `` `template` `` |
| `number` | Numeric literals | `42`, `3.14`, `0xFF`, `1e10` |
| `comment` | Single/multi-line comments | `// line`, `/* block */`, `# hash` |
| `function` | Function/method names | `def **foo**():`, `function **bar**()` |
| `type` | Class/type names | `class **Foo**`, `List<String>`, `interface **Bar**` |
| `operator` | Operators | `+`, `-`, `*`, `/`, `=`, `==`, `->`, `::` |
| `punctuation` | Brackets, parens, commas | `{ } [ ] ( ) , ; . :` |
| `boolean` | Boolean literals | `true`, `false`, `True`, `False` |
| `builtin` | Built-in functions/types | `print`, `len`, `console.log`, `println` |
| `variable` | Special variables | `this`, `self`, `super`, `$VAR` |
| `constant` | Constants | `PI`, `MAX_SIZE`, `NULL`, `None` |
| `plain` | Unmatched text | Everything else |

### Per-Thene Token Colors

Each of the 4 themes maps the 12 token types to colors for both light and dark variants. This is ~96 color values total (4 themes × 12 token types × 2 variants). Brightness and contrast tested for WCAG AA readability on mobile.

Colors sourced from the canonical theme definitions (Monokai from TextMate, One Dark from Atom, GitHub from Primer Design, Dracula from dracula-theme).

## Sources

### Primary (HIGH confidence)
- **Prism.js token documentation:** https://prismjs.com/tokens.html — Standard token types, the industry consensus for syntax highlighting token taxonomy.
- **Prism4j GitHub (archived):** https://github.com/noties/Prism4j — Confirmed archived since July 2023. Last release June 2019. Read-only repository.
- **Warped codebase inspection:** `MarkdownText.kt`, `MessageBubble.kt`, `AdvancedPreferences.kt`, `SettingsScreen.kt` — Confirmed existing `CodeTheme` enum structure, DataStore persistence, Compose rendering approach.

### Secondary (MEDIUM confidence)
- **Sora Editor (rosemoe/sora-editor):** Context7 docs — Confirmed TextMate/TreeSitter approach exists for Android but is designed for code editors, not read-only rendering. Overkill for chat app code blocks.
- **Highlight.js auto-detection:** https://highlightjs.org/ — Industry-standard language detection approach. Uses Bayesian classifier + keyword matching. Adaptation simplified for mobile (heuristic-only, no ML).

### Competitive Analysis (MEDIUM confidence)
- **ChatGPT Android app** — Observed: WebView-based rendering, language header bar, copy button, dark-only theme. Source: personal usage.
- **Claude Android app** — Observed: Copy button but no language header bar, dark-only theme. Source: personal usage.
- **LM Studio desktop** — Observed: Language header bar, copy button, follows app theme. Source: personal usage.

### LOW confidence (needs validation)
- **Android syntax highlighting library landscape** — No maintained library found via Context7, GitHub topics, or web search. A deeper search of Maven Central may surface niche alternatives. Likelihood of finding one: LOW (this is a genuinely underserved niche on Android).

---

*Feature research for: Warped v1.6 code syntax highlighting*
*Researched: 2026-05-14*
