# Stack Research — Code Syntax Highlighting

**Domain:** Syntax highlighting for code blocks in Android/Compose chat app
**Researched:** 2026-05-14
**Confidence:** HIGH

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| **Highlights** (SnipMeDev) | 1.1.0 | Syntax tokenization engine — parses raw code into structured `CodeHighlight` tokens (keyword, string, comment, literal, etc.) | Most mature pure-Kotlin syntax highlighting library for Android. 183 GitHub stars, 26 Maven Central dependents, published 8 months ago. Supports 17 languages covering 95%+ of what LLMs output. Built-in themes include Monokai and Atom One. Uses hand-written regex (no native deps, no JS engine). Apache 2.0 license. |
| **Custom `AnnotatedString` bridge** | — | Converts `List<CodeHighlight>` → Compose `AnnotatedString` with per-token `SpanStyle` | Highlights produces structured token data, not `AnnotatedString` directly. We write a thin adapter (~50 lines) that maps `CodeHighlight` subtypes to `SpanStyle` using the selected theme's color palette. Integrates seamlessly with existing `buildAnnotatedString` pattern in `MarkdownText.kt`. |
| **Custom `SyntaxColorScheme`** | — | Full token-color palette per theme variant (dark + light) | Expands existing `CodeTheme` enum (which currently only has `bgCode`/`bgInline` background colors) with 8 token categories per variant. 4 themes × 2 variants = 8 color schemes defined as pure data classes. Auto-adapts via `isSystemInDarkTheme()`. |
| **Custom language detector** | — | Determines programming language from code block content | No mature JVM language-detection library exists. Two-tier approach: (1) parse language from markdown fence ` ```python ` — covers 90%+ of real LLM output, (2) keyword-frequency heuristics for unspecified languages — simple, effective for top 10-15 languages, zero dependencies. |

### Supporting Technologies (already in stack — no new deps)

| Technology | Purpose | When to Use |
|------------|---------|-------------|
| **Compose `buildAnnotatedString`** | Build highlighted `AnnotatedString` from token data | Core rendering — already the backbone of `MarkdownText.kt` |
| **Compose `SpanStyle`** | Per-token color, font weight, font style | Maps `CodeHighlight` types to visual styles via theme palette |
| **`FontFamily.Monospace`** | Code block typeface | Already used for code blocks in existing `MarkdownText.kt` |
| **`LocalClipboardManager`** | Copy-to-clipboard for code blocks | Standard Compose clipboard API — `setText(AnnotatedString(code))` |
| **`isSystemInDarkTheme()`** | Light/dark theme auto-detection | Compose built-in — selects dark or light variant of the active syntax theme |
| **`DataStore` (Preferences)** | Persist user's chosen code theme | Already in stack — stores `CodeTheme` enum value |

## Installation

```kotlin
// build.gradle.kts (app module)
dependencies {
    // Syntax highlighting engine
    implementation("dev.snipme:highlights:1.1.0")
}
```

No transitive dependency surprises — Highlights only pulls in `kotlin-stdlib`, `kotlinx-coroutines-core`, and `kotlinx-serialization-json`, all of which are already in the project's version catalog.

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Highlights 1.1.0 | **kotlin-textmate** 0.1.0 (ivan-magda) | Only if you need 600+ languages via `.tmLanguage` grammars AND can accept v0.1.0 risk. kotlin-textmate uses VS Code's TextMate grammar engine ported to Kotlin with Joni (Java Oniguruma) regex. However: released May 14, 2026 (today), 12 stars, 0 forks, known limitations (no injection grammars, Joni regex fallback for backreferences, not thread-safe, no incremental tokenization). The 17 languages in Highlights cover essentially all LLM output languages — 600+ is overkill for a chat app. |
| Highlights 1.1.0 | **Highlight** 2.3.0 (Irineu333) | Use if you need a general regex-based text highlighting framework (not syntax-specific). This library is a pattern-matching engine, not a syntax highlighter — you'd need to write all language grammars yourself. Good for custom text formatting, not for syntax highlighting out of the box. |
| Highlights 1.1.0 | **CodeView-Android** (kbiakov) | Use only if targeting View-based (XML) UI, not Compose. 891 stars but last updated Jan 2022 — pre-Compose era. Uses `RecyclerView` adapter pattern, incompatible with `AnnotatedString` rendering. |
| Highlights 1.1.0 | **highlight.kt** (nyancrimew) | Do not use. Kotlin port of highlight.js from 2019. 6 stars, unmaintained for 7+ years, likely broken with current Kotlin/AGP versions. |
| Highlights 1.1.0 | **WebView-based highlighting** | Use only for full web-page rendering. Embedding a WebView per code block would destroy scroll performance, break `AnnotatedString` integration, add ~50ms+ per-block layout overhead, and complicate theme synchronization. Pure Compose `AnnotatedString` has zero layout overhead. |
| Custom language detector | **highlight.js auto-detect** via JS engine | Use only if keyword heuristics prove insufficient. Embedding a JS runtime (WebView/J2V8/QuickJS) adds 2-15 MB APK size and significant complexity. Our two-tier approach (fence parsing + keyword heuristics) handles the actual use case well. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| **kotlin-textmate** (ivan-magda) | v0.1.0 released today (May 14, 2026). Too new for production. Known limitations: no injection grammars, Joni regex fallback silently fails on backreferences in lookbehind, not thread-safe, no incremental tokenization, per-token background color not rendered. 12 stars, single contributor, zero forks. | Highlights 1.1.0 |
| **highlight.kt** (nyancrimew) | Kotlin port of highlight.js from 2019. 6 stars. Last updated Oct 2019. Likely incompatible with Kotlin 2.3+ and current AGP. No Compose support. | Highlights 1.1.0 |
| **CodeView-Android** (kbiakov) | View-based `RecyclerView` adapter pattern. Last updated Jan 2022. Does not produce `AnnotatedString`. | Highlights 1.1.0 + custom AnnotatedString bridge |
| **WebView** (per code block) | Destroys scroll performance in `LazyColumn`. Each WebView is a full browser engine — layout, JS, rendering pipeline. Breaks text selection, copy/paste, and theme consistency. | Compose `AnnotatedString` |
| **Embedded JS runtime** (J2V8, QuickJS, etc.) | Adds 2-15 MB APK bloat. Requires NDK cross-compilation. Threading model mismatch with Kotlin coroutines. Maintenance burden for OTA updates. | Pure Kotlin (Highlights + custom detector) |
| **Moshi** (JSON) | Highlights depends on `kotlinx-serialization-json` internally. Adding Moshi would create a duplicate JSON framework in the dependency graph. | kotlinx-serialization (already in stack) |
| **KodeEditor** (markusressel) | Full code editor component (edit, zoom, select). Overkill for read-only code block display. Adds unnecessary complexity. | Highlights 1.1.0 (tokenizer only, not editor) |
| **compose-code-editor** (Qawaz) | Editor-focused (cursor, selection, editing). Heavier dependency than needed for display-only use case. | Highlights 1.1.0 (tokenizer only) |

## Stack Patterns by Use Case

**If rendering code in chat messages (primary use case):**
- Use Highlights for tokenization → custom bridge → `buildAnnotatedString` → Compose `Text`
- Branch existing `MarkdownText.kt` code block path to call the syntax highlighter instead of flat monospace
- Maintain streaming compatibility: tokenize completed code blocks only (not in-progress streaming blocks)

**If rendering code in model cards / READMEs:**
- Same pipeline, wrapped in a reusable `HighlightedCodeBlock` composable
- Composable includes: language header bar + highlighted code + copy button

**If detecting language from unlabeled code:**
- Use keyword-frequency scoring (count known keywords per language, highest score wins)
- Fall back to "plaintext" when no clear winner

**If user switches theme at runtime:**
- `CodeTheme` flows through `StateFlow<UiState>` → recomposition triggers re-render
- Highlights `getHighlights()` uses cached `CodeStructure` (incremental re-tokenization is cheap with the same `Highlights` instance)

## Integration with Existing MarkdownText.kt

The existing `MarkdownText.kt` renders code blocks at lines 55-66 and 79-86 with a single `SpanStyle(background = codeTheme.bgCode, fontFamily = FontFamily.Monospace)`. The integration replaces these flat spans with syntax-highlighted content:

```
Current (flat monospace):
  withStyle(SpanStyle(background = codeTheme.bgCode, fontFamily = FontFamily.Monospace)) {
      append(codeBlockContent)
  }

New (syntax highlighted):
  val language = detectLanguage(fenceHeader, codeBlockContent)
  val highlights = highlightsEngine.getHighlights(codeBlockContent, language)
  appendHighlightedCode(highlights, syntaxColorScheme)
```

The `appendHighlightedCode` function iterates `List<CodeHighlight>` and applies the appropriate `SpanStyle` per token type via the active `SyntaxColorScheme`.

## Version Compatibility

| Package | Compatible With | Notes |
|---------|----------------|-------|
| `dev.snipme:highlights:1.1.0` | Kotlin 2.2.0+ (uses kotlin-stdlib 2.2.0) | Project uses Kotlin 2.3.20 — forward compatible |
| `dev.snipme:highlights:1.1.0` | kotlinx-coroutines 1.9.0 | Project uses 1.9.0 — exact match |
| `dev.snipme:highlights:1.1.0` | kotlinx-serialization-json 1.7.1 | Project uses 1.7.3 — backward compatible (1.7.x) |
| `dev.snipme:highlights:1.1.0` | AGP 8.7.3 / Gradle 8.x | Standard JVM library, no AGP version coupling |

No version conflicts. All transitive dependencies align with or are compatible with the project's existing version catalog.

## Sources

- [Highlights GitHub Repository](https://github.com/SnipMeDev/Highlights) — README, sample code, language list, theme documentation. **HIGH confidence** — official repository.
- [Highlights on Maven Central](https://central.sonatype.com/artifact/dev.snipme/highlights) — Version 1.1.0 confirmed published with POM showing kotlin-stdlib 2.2.0, kotlinx-coroutines 1.9.0, kotlinx-serialization-json 1.7.1 dependencies. **HIGH confidence** — official artifact repository.
- [kotlin-textmate GitHub Repository](https://github.com/ivan-magda/kotlin-textmate) — Evaluated and rejected. README documents known limitations (no injection grammars, Joni regex fallback, not thread-safe). v0.1.0 released 2026-05-14. **HIGH confidence** — official repository.
- [Highlight (Irineu333) GitHub Repository](https://github.com/Irineu333/Highlight) — Evaluated and rejected. General regex highlighting framework, not syntax-specific. v2.3.0. **HIGH confidence** — official repository.
- [CodeView-Android GitHub Repository](https://github.com/kbiakov/CodeView-Android) — Evaluated and rejected. View-based, last updated Jan 2022. **HIGH confidence** — official repository.
- [highlight.dart GitHub Repository](https://github.com/pd4d10/highlight.dart) — Reference for Dart/Flutter approach. Confirms highlight.js port pattern is viable. Not directly usable (Dart, not Kotlin). **MEDIUM confidence** — analogous ecosystem reference.
- [Project's existing MarkdownText.kt](../../app/src/main/java/com/warped/ui/chat/components/MarkdownText.kt) — Integration target. Current code block rendering pattern documented. **HIGH confidence** — own codebase.

---

*Stack research for: Code syntax highlighting in Android/Compose chat app*
*Researched: 2026-05-14*
