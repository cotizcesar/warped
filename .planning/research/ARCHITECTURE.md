# Architecture Research: Code Syntax Highlighting Integration

**Domain:** Android chat app (Warped — LM Studio equivalent)
**Researched:** 2026-05-14
**Confidence:** HIGH

## Executive Summary

Integration of syntax highlighting into Warped's existing Compose chat rendering requires a **custom regex-based tokenizer** built specifically for LLM code block rendering. No well-established, production-ready Compose-native syntax highlighting library exists for Android — the available options are either archived (Prism4j, 2019), View-based (CodeView-Android, 2022), or too new and unproven (kotlin-textmate, 12 stars, updated today).

The custom tokenizer approach is the **correct architectural choice** because:
1. The rendering target is `AnnotatedString` (Compose-native, no bridging needed)
2. LLM output code blocks are small (10–100 lines, not full files)
3. The existing `MarkdownText` component already uses `buildAnnotatedString` — the tokenizer extends this pattern
4. Zero external dependencies (fits clean architecture, no NDK, no JNI, no JS interop)
5. Streaming-compatible: tokenize incrementally as LLM emits tokens

## Standard Architecture

### System Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                         UI Layer (Compose)                             │
│                                                                        │
│  ChatScreen ──── collects uiState.codeTheme                            │
│    │                                                                   │
│    ├─ MessageBubble (EXISTING — modified)                              │
│    │    │                                                               │
│    │    ├─ SelectionContainer                                          │
│    │    │    └─ MarkdownText (EXISTING — refactored)                    │
│    │    │         │                                                     │
│    │    │         ├─ parseInlineMarkdown() ─ unchanged                  │
│    │    │         ├─ parseInlineStyles() ─ unchanged                    │
│    │    │         └─ code block fence ──► CodeBlock (NEW)              │
│    │    │              │                                               │
│    │    │              ├─ LanguageDetector.detect(code) (NEW)          │
│    │    │              ├─ SyntaxHighlighter.highlight(code, lang,       │
│    │    │              │       theme) → AnnotatedString (NEW)          │
│    │    │              ├─ LanguageHeaderBar (NEW)                       │
│    │    │              └─ CopyButton (NEW)                              │
│    │    │                                                               │
│    │    └─ Reasoning block ─ uses MarkdownText (unchanged)              │
│    │                                                                   │
│  SettingsScreen ──── code theme dropdown (EXISTING — modified)         │
│    └─ SettingsViewModel.setCodeTheme() ─ unchanged                     │
│                                                                        │
├──────────────────────────────────────────────────────────────────────┤
│                      Domain Layer (NEW components)                     │
│                                                                        │
│  domain/highlighting/                                                  │
│    ├─ SyntaxHighlighter.kt          (interface)                        │
│    ├─ CodeToken.kt                  (sealed class: token types)        │
│    └─ Language.kt                   (enum: supported languages)        │
│                                                                        │
├──────────────────────────────────────────────────────────────────────┤
│                       Data Layer (NEW components)                      │
│                                                                        │
│  data/highlighting/                                                    │
│    ├─ RegexSyntaxHighlighter.kt     (interface implementation)         │
│    ├─ LanguageDetector.kt           (heuristic language detection)     │
│    ├─ definitions/                  (token patterns per language)      │
│    │   ├─ PythonDefinitions.kt                                        │
│    │   ├─ JavaScriptDefinitions.kt                                    │
│    │   ├─ TypeScriptDefinitions.kt                                     │
│    │   ├─ BashDefinitions.kt                                          │
│    │   ├─ JsonDefinitions.kt                                           │
│    │   ├─ YamlDefinitions.kt                                           │
│    │   ├─ GoDefinitions.kt                                            │
│    │   ├─ RustDefinitions.kt                                           │
│    │   ├─ JavaDefinitions.kt                                          │
│    │   ├─ CppDefinitions.kt                                            │
│    │   ├─ SqlDefinitions.kt                                            │
│    │   └─ ShellDefinitions.kt                                          │
│    └─ theme/                                                           │
│        ├─ HighlightingTheme.kt      (Color scheme per token type)      │
│        ├─ MonokaiTheme.kt                                             │
│        ├─ OneDarkTheme.kt                                              │
│        ├─ GitHubTheme.kt                                              │
│        └─ DraculaTheme.kt                                             │
│                                                                        │
├──────────────────────────────────────────────────────────────────────┤
│                    Existing Infrastructure (modified)                  │
│                                                                        │
│  data/local/preferences/AdvancedPreferences.kt  — CodeTheme enum      │
│  ui/chat/components/MarkdownText.kt             — code block handling  │
│  ui/chat/components/CodeTheme                    — MOVED + expanded    │
│  ui/chat/ChatUiState.kt                          — unchanged           │
│  ui/chat/ChatViewModel.kt                        — unchanged           │
│  ui/settings/SettingsScreen.kt                   — dropdown labels     │
│  ui/settings/SettingsUiState.kt                   — unchanged           │
│  ui/settings/SettingsViewModel.kt                 — unchanged           │
└──────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Status | Responsibility | File(s) |
|-----------|--------|----------------|---------|
| `CodeTheme` enum | **MOVED + EXPANDED** | Currently in `MarkdownText.kt`; moves to `ui/theme/` or `data/highlighting/theme/`. Gains token color maps, light/dark variant support, and `toHighlightingTheme()` mapping. | `CodeTheme.kt` |
| `MarkdownText` | **REFACTORED** | Stays the outer markdown parser. Code block fence detection unchanged. Instead of rendering code blocks inline, delegates to new `CodeBlock` composable. Inline code (`backticks`) continues to use bg color only. | `MarkdownText.kt` |
| `CodeBlock` | **NEW** | Composable that takes raw code text + codeTheme. Orchestrates: language detection → syntax highlighting → AnnotatedString rendering → language header bar → copy button. Replaces lines 56–85 of current MarkdownText. | `ui/chat/components/CodeBlock.kt` |
| `SyntaxHighlighter` | **NEW (interface)** | Domain-layer interface: `fun highlight(code: String, language: Language, theme: HighlightingTheme): AnnotatedString`. Pure Kotlin, no Android/Compose dependency — lives in `domain/highlighting/`. | `domain/highlighting/SyntaxHighlighter.kt` |
| `RegexSyntaxHighlighter` | **NEW (impl)** | Data-layer implementation. Applies regex token patterns to split code into `CodeToken` sealed types, then maps each token to `SpanStyle` from the theme. Returns `AnnotatedString`. | `data/highlighting/RegexSyntaxHighlighter.kt` |
| `LanguageDetector` | **NEW** | Heuristic language detection from raw code text. Uses keyword frequency scoring (not full parsing). Reads the markdown fence language hint (e.g., `\`\`\`python`) as the primary signal, falls back to content-based detection. | `data/highlighting/LanguageDetector.kt` |
| `Language` enum | **NEW** | Enum of supported languages. Each variant maps to a `LanguageDefinition` containing regex token patterns. | `domain/highlighting/Language.kt` |
| `CodeToken` | **NEW** | Sealed class for token types: `Keyword`, `String`, `Number`, `Comment`, `Function`, `Type`, `Operator`, `Punctuation`, `Plain`. Mirror of standard code token categories from TextMate/Pygments. | `domain/highlighting/CodeToken.kt` |
| `HighlightingTheme` | **NEW** | Interface mapping `CodeToken` → `SpanStyle` (color, bold, italic). Each theme (Monokai, One Dark, GitHub, Dracula) provides light and dark mode `SpanStyle` maps. | `data/highlighting/theme/HighlightingTheme.kt` |
| `LanguageHeaderBar` | **NEW (inline in CodeBlock)** | Small bar above code block showing detected language name. No separate file needed — it's a `@Composable` function inside `CodeBlock.kt`. | In `CodeBlock.kt` |
| `CopyButton` | **NEW (inline in CodeBlock)** | Icon button that copies code content to clipboard. Uses `LocalClipboardManager`. | In `CodeBlock.kt` |
| `MessageBubble` | **UNCHANGED** | Passes `codeTheme` through to `MarkdownText` as before. No structural changes needed — `CodeBlock` lives inside `MarkdownText`'s rendering. | `MessageBubble.kt` |
| `ChatUiState` | **UNCHANGED** | `codeTheme: CodeTheme` field remains. Type changes from old enum to new one but data class structure identical. | `ChatUiState.kt` |
| `ChatViewModel` | **UNCHANGED** | `advancedPreferences.codeTheme.collect { ... }` unchanged. Data flow identical. | `ChatViewModel.kt` |
| `AdvancedPreferences` | **MINIMALLY CHANGED** | Stores `CodeTheme.name` as string. Schema unchanged. The `CodeTheme.valueOf(name)` call works with the new enum. | `AdvancedPreferences.kt` |
| `SettingsScreen` | **MINIMALLY CHANGED** | Code theme dropdown labels update to show preview colors alongside theme name. Dropdown `CodeTheme.entries.forEach` unchanged. | `SettingsScreen.kt` |
| `SettingsViewModel` | **UNCHANGED** | `setCodeTheme()` and `codeTheme` collection unchanged. | `SettingsViewModel.kt` |
| `SettingsUiState` | **UNCHANGED** | `codeTheme: CodeTheme` field remains. | `SettingsUiState.kt` |

## Recommended Project Structure

```
app/src/main/java/com/warped/
│
├── domain/
│   └── highlighting/                    # NEW — pure Kotlin, zero Android deps
│       ├── SyntaxHighlighter.kt         # interface: highlight(code, lang, theme) → AnnotatedString
│       ├── CodeToken.kt                 # sealed class: Keyword, String, Number, Comment, Function, Type, Operator, Punctuation, Plain
│       ├── Language.kt                  # enum: PYTHON, JAVASCRIPT, TYPESCRIPT, BASH, JSON, YAML, GO, RUST, JAVA, CPP, SQL, UNKNOWN
│       └── LanguageDefinition.kt        # data class: List<TokenPattern> per language
│
├── data/
│   └── highlighting/                    # NEW — implements domain interfaces
│       ├── RegexSyntaxHighlighter.kt   # impl of SyntaxHighlighter, regex-based tokenizer
│       ├── LanguageDetector.kt         # keyword frequency scoring + fence info detection
│       ├── definitions/                # token pattern definitions per language
│       │   ├── PythonDefinitions.kt    # Python keyword/string/comment/number patterns
│       │   ├── JavaScriptDefinitions.kt
│       │   ├── TypeScriptDefinitions.kt
│       │   ├── BashDefinitions.kt
│       │   ├── JsonDefinitions.kt
│       │   ├── YamlDefinitions.kt
│       │   ├── GoDefinitions.kt
│       │   ├── RustDefinitions.kt
│       │   ├── JavaDefinitions.kt
│       │   ├── CppDefinitions.kt
│       │   ├── SqlDefinitions.kt
│       │   └── ShellDefinitions.kt
│       └── theme/                      # color themes with light/dark variants
│           ├── HighlightingTheme.kt    # interface: Map<CodeToken, LightDark<SpanStyle>>
│           ├── MonokaiTheme.kt
│           ├── OneDarkTheme.kt
│           ├── GitHubTheme.kt
│           └── DraculaTheme.kt
│
├── ui/
│   ├── chat/
│   │   └── components/
│   │       ├── MarkdownText.kt         # REFACTORED — delegate code blocks to CodeBlock
│   │       ├── CodeBlock.kt            # NEW — orchestration composable
│   │       └── MessageBubble.kt        # UNCHANGED
│   ├── settings/
│   │   └── SettingsScreen.kt           # MODIFIED — dropdown shows theme preview colors
│   └── theme/
│       └── CodeTheme.kt                # MOVED from chat/components/ + EXPANDED
│
└── di/
    └── HighlightingModule.kt           # NEW — Hilt module providing SyntaxHighlighter + LanguageDetector
```

### Structure Rationale

- **`domain/highlighting/`** — Pure Kotlin interfaces and models. Follows existing Clean Architecture pattern (like `domain/model/`, `domain/repository/`). Zero Android dependencies enable fast unit testing.
- **`data/highlighting/`** — Concrete implementations. `RegexSyntaxHighlighter` implements `SyntaxHighlighter`. `LanguageDetector` is a standalone class (no interface needed — it's a utility, not a swappable dependency). Follows existing pattern (like `data/repository/` implements `domain/repository/`).
- **`data/highlighting/definitions/`** — One file per language with `List<TokenPattern>`. Keeps definitions self-contained and easy to contribute. Follows single-responsibility: changing Python highlighting doesn't risk breaking JavaScript.
- **`data/highlighting/theme/`** — Theme definitions separated from the `CodeTheme` enum (which stays in `ui/theme/` for the preferences bridge). The enum maps to theme objects, keeping the UI preferences layer simple.
- **`ui/chat/components/CodeBlock.kt`** — New composable in the existing chat components package. Co-located with `MarkdownText.kt` since they're tightly coupled (MarkdownText creates CodeBlock instances).
- **`di/HighlightingModule.kt`** — Hilt `@Module` providing `SyntaxHighlighter` as singleton. Follows existing DI pattern (`RepositoryModule.kt`, `ProviderModule.kt`, etc.).

## Architectural Patterns

### Pattern 1: Strategy Pattern for Syntax Highlighting

**What:** `SyntaxHighlighter` interface with a single implementation (`RegexSyntaxHighlighter`). Language-specific token patterns are injected via `LanguageDefinition` data objects.

**When to use:** When the highlighting engine could be swapped later (e.g., to a TextMate grammar engine like `kotlin-textmate` once it matures). The interface boundary makes this zero-cost future-proofing.

**Trade-offs:** One extra interface indirection, but this is standard Clean Architecture practice. The `LanguageDetector` intentionally has NO interface — it's a utility, not a swappable strategy.

**Example:**
```kotlin
// domain/highlighting/SyntaxHighlighter.kt
interface SyntaxHighlighter {
    fun highlight(
        code: String,
        language: Language,
        theme: HighlightingTheme
    ): AnnotatedString
}

// data/highlighting/RegexSyntaxHighlighter.kt (simplified)
class RegexSyntaxHighlighter @Inject constructor() : SyntaxHighlighter {
    override fun highlight(code: String, language: Language, theme: HighlightingTheme): AnnotatedString {
        val definitions = language.definitions
        return buildAnnotatedString {
            var remaining = code
            while (remaining.isNotEmpty()) {
                val match = findFirstMatch(remaining, definitions)
                if (match != null) {
                    append(match.before) // plain text
                    withStyle(theme.spanStyleFor(match.tokenType)) {
                        append(match.text)
                    }
                    remaining = match.after
                } else {
                    append(remaining)
                    remaining = ""
                }
            }
        }
    }
}
```

### Pattern 2: Theme as Enum-to-Object Bridge

**What:** `CodeTheme` enum (for DataStore persistence and dropdown UI) maps to `HighlightingTheme` objects (for token-level coloring). The enum stays the persistence key; theme objects hold the actual color data.

**When to use:** When preferences need simple serialization (enum name as string) but rendering needs rich color data (per-token `SpanStyle` maps).

**Trade-offs:** Two representations of "theme" (enum + object). Acceptable because serialization and rendering have different requirements.

**Example:**
```kotlin
// ui/theme/CodeTheme.kt
enum class CodeTheme(val label: String, val previewColor: Color) {
    MONOKAI("Monokai", Color(0xFF272822)),
    ONE_DARK("One Dark", Color(0xFF282C34)),
    GITHUB("GitHub", Color(0xFFF6F8FA)),
    DRACULA("Dracula", Color(0xFF282A36));

    fun toHighlightingTheme(isDark: Boolean): HighlightingTheme = when (this) {
        MONOKAI -> MonokaiTheme
        ONE_DARK -> OneDarkTheme
        GITHUB -> if (isDark) GitHubDarkTheme else GitHubLightTheme
        DRACULA -> DraculaTheme
    }
}
```

### Pattern 3: Composable Delegation (MarkdownText → CodeBlock)

**What:** `MarkdownText` detects fenced code blocks (` ``` `) and delegates content + language hint to `CodeBlock` composable instead of rendering inline. `CodeBlock` is a separate `@Composable` that can be composed independently (testable, reusable).

**When to use:** When a section of a composable has distinct behavior that benefits from its own state management (language detection, copy button interaction).

**Trade-offs:** Slightly more composable nesting. Worth it for separation of concerns — MarkdownText handles markdown structure, CodeBlock handles syntax highlighting UX.

**Example:**
```kotlin
// In MarkdownText.kt — refactored code block handling
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    baseColor: Color = Color.Unspecified,
    codeTheme: CodeTheme = CodeTheme.MONOKAI,
    isDarkTheme: Boolean = true  // NEW parameter
) {
    // ... parse lines ...
    for (line in lines) {
        if (line.trimStart().startsWith("```")) {
            if (inCodeBlock) {
                // OLD: withStyle(bg only) { append(content) }
                // NEW: delegate to CodeBlock
                val langHint = fenceLine.removePrefix("```").trim()
                CodeBlock(
                    code = codeBlockContent.toString().trimEnd(),
                    languageHint = langHint,
                    codeTheme = codeTheme,
                    isDarkTheme = isDarkTheme,
                    onCopy = { /* clipboard */ }
                )
                codeBlockContent.clear()
            }
            inCodeBlock = !inCodeBlock
            continue
        }
        // ... rest unchanged ...
    }
}
```

## Data Flow

### Syntax Highlighting Flow (code block rendering)

```
LLM emits token "def fib(n):\n    if n <= 1:\n        return n\n..."
    ↓
ChatViewModel accumulates into streamingContent
    ↓
ChatScreen recomposes → MessageBubble(isStreaming=true, codeTheme=uiState.codeTheme)
    ↓
MessageBubble → MarkdownText(text=streamingContent, codeTheme=codeTheme)
    ↓
MarkdownText line parser detects ``` fence
    ↓ (inside code block)
CodeBlock(code=codeBlockContent, languageHint="python", ...)
    ↓
LanguageDetector.detect(code, hint) → Language.PYTHON
    ↓
SyntaxHighlighter.highlight(code, Language.PYTHON, theme)
    ↓
RegexSyntaxHighlighter tokenizes code into [Keyword("def"), Plain(" "), Function("fib"), ...]
    ↓
Applies theme SpanStyle per token → builds AnnotatedString
    ↓
CodeBlock renders:
    ┌─ LanguageHeaderBar("Python") ────────────────────── [📋 Copy] ─┐
    │  def fib(n):                            [keyword - pink]        │
    │      if n <= 1:                         [keyword - pink]        │
    │          return n                       [keyword - pink]        │
    └────────────────────────────────────────────────────────────────┘
```

### Theme Change Flow

```
User opens Settings → selects "Dracula" from Code Theme dropdown
    ↓
SettingsViewModel.setCodeTheme(CodeTheme.DRACULA)
    ↓
AdvancedPreferences.setCodeTheme(DRACULA) → stores "DRACULA" in DataStore
    ↓
ChatViewModel: advancedPreferences.codeTheme emits CodeTheme.DRACULA
    ↓
ChatViewModel: _uiState.update { copy(codeTheme = CodeTheme.DRACULA) }
    ↓
ChatScreen recomposes → codeTheme flows to all MessageBubble → MarkdownText → CodeBlock instances
    ↓
All visible code blocks instantly re-render with Dracula colors
```

### Copy Button Flow

```
User taps [📋] on a code block
    ↓
CodeBlock's CopyButton: clipboardManager.setText(AnnotatedString(code))
    ↓
Shows brief "Copied!" snackbar (local state, not global)
    ↓
Clipboard now contains plain text code (no formatting)
```

### Streaming Rendering Flow (incremental)

```
During streaming, codeBlockContent grows with each token line
    ↓
MarkdownText sees codeBlockContent change → recomposes CodeBlock
    ↓
CodeBlock re-runs LanguageDetector.detect() on the growing content
    (Detection result cached unless language confidence score improves)
    ↓
SyntaxHighlighter.highlight() runs on full content each recomposition
    (Performance: O(n * p) where n = code lines, p = token patterns per language)
    (For typical LLM output of 10–50 lines, this is < 1ms on any modern phone)
    ↓
AnnotatedString rebuilds with each recomposition
    (Compose efficiently diffs AnnotatedString changes)
```

## Integration Points

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| `MarkdownText` → `CodeBlock` | Direct composable call + props | MarkdownText passes `code`, `languageHint`, `codeTheme`, `isDarkTheme`. CodeBlock is child composable. |
| `CodeBlock` → `LanguageDetector` | Direct function call | Synchronous. Returns `Language`. No coroutine needed. |
| `CodeBlock` → `SyntaxHighlighter` | Hilt-injected dependency | `@Inject lateinit var` or constructor injection in a wrapper ViewModel/holder. `SyntaxHighlighter.highlight()` is a pure function, could be called directly without scoping. |
| `CodeTheme` → `HighlightingTheme` | Enum method `toHighlightingTheme()` | Synchronous mapping. No DI needed. |
| `ChatScreen` → `MessageBubble` → `MarkdownText` → `CodeBlock` | Props threading | `codeTheme` flows from `uiState.codeTheme` (in ChatScreen) → `MessageBubble(codeTheme)` → `MarkdownText(codeTheme)` → `CodeBlock(codeTheme)`. Existing data flow, no new plumbing. |
| `SettingsScreen` → `SettingsViewModel` → `AdvancedPreferences` | Existing MVVM flow | Unchanged. Adding new CodeTheme value to the enum auto-works with existing dropdown + DataStore. |
| `Hilt` → `CodeBlock` | DI injection of `SyntaxHighlighter` + `LanguageDetector` | New `HighlightingModule` provides singletons. `CodeBlock` receives them as composable parameters or via `hiltViewModel()` wrapper. |

### What Does NOT Change

| Component | Why Unchanged |
|-----------|---------------|
| `ChatUiState.kt` | `codeTheme: CodeTheme` field already exists. The enum type reference updates but the data class structure is identical. |
| `ChatViewModel.kt` | `advancedPreferences.codeTheme.collect { _uiState.update { it.copy(codeTheme = theme) } }` — zero changes. |
| `ChatRepository`, `EndpointRepository`, etc. | No code highlighting logic touches data persistence or remote APIs. |
| `Room database` | No new tables, DAOs, or migrations. Code blocks are rendered ephemerally from message content strings. |
| `Retrofit APIs` | No API changes. LLM providers already return markdown with fenced code blocks. |
| `MessageBubble.kt` | Passes `codeTheme` through as before. The internal `MarkdownText` handles the rendering change transparently. |
| `SelectionContainer` | CodeBlock renders inside the existing `SelectionContainer` (wrapped by MessageBubble). Text selection includes code blocks automatically. No extra `SelectionContainer` needed. |
| `NavGraph.kt` | No new routes. Code highlighting is a rendering concern, not navigation. |
| `ConversationList.kt` | Conversation previews don't render full markdown. No change needed. |

## Build Order (Suggested)

Based on dependency analysis of the existing architecture:

### Wave 1: Foundation — Theme & Token Model (no Compose)
Files with zero UI dependencies. Pure Kotlin, fast unit-testable.

1. **`domain/highlighting/CodeToken.kt`** — Sealed class for token types. No deps.
2. **`domain/highlighting/Language.kt`** — Enum of supported languages. No deps.
3. **`domain/highlighting/LanguageDefinition.kt`** — Data class for token patterns. No deps.
4. **`domain/highlighting/SyntaxHighlighter.kt`** — Interface. No deps.

### Wave 2: Token Patterns — Language Definitions (pure data)
12 files, one per language. Pure Kotlin, no UI deps. Can be built in parallel.

5. **`data/highlighting/definitions/*Definitions.kt`** — Regex patterns per language × 12 files. Depends on `CodeToken` + `LanguageDefinition`.

### Wave 3: Theme Definitions (pure color data)
4 theme files with light/dark `SpanStyle` maps. Pure Kotlin, depends on `CodeToken`.

6. **`data/highlighting/theme/HighlightingTheme.kt`** — Interface.
7. **`data/highlighting/theme/MonokaiTheme.kt`** — Token → SpanStyle map.
8. **`data/highlighting/theme/OneDarkTheme.kt`**
9. **`data/highlighting/theme/GitHubTheme.kt`** — Needs both light + dark variants.
10. **`data/highlighting/theme/DraculaTheme.kt`**

### Wave 4: Engine — SyntaxHighlighter + LanguageDetector implementations
11. **`data/highlighting/RegexSyntaxHighlighter.kt`** — Implements `SyntaxHighlighter`. Depends on Wave 1 + Wave 2 + Wave 3.
12. **`data/highlighting/LanguageDetector.kt`** — Heuristic detection. Depends on `Language` enum.

### Wave 5: Preferences Bridge — CodeTheme migration
13. **`ui/theme/CodeTheme.kt`** — MOVED from `ui/chat/components/MarkdownText.kt`. Expanded with `toHighlightingTheme()` and `previewColor`. Depends on Wave 3.
14. **Update imports** in `MarkdownText.kt`, `ChatUiState.kt`, `ChatViewModel.kt`, `AdvancedPreferences.kt`, `SettingsScreen.kt`, `SettingsUiState.kt`, `SettingsViewModel.kt`, `MessageBubble.kt` → import new `CodeTheme` location.

### Wave 6: UI Rendering — CodeBlock Composable
15. **`ui/chat/components/CodeBlock.kt`** — NEW composable with language header bar + copy button + syntax-highlighted code. Depends on Waves 1–5.
16. **Refactor `ui/chat/components/MarkdownText.kt`** — Delegate code block rendering to `CodeBlock`. Keep all other inline markdown handling unchanged.
17. **`ui/settings/SettingsScreen.kt`** — Update theme dropdown to show preview color swatch alongside label. Optional: richer theme picker with preview.

### Wave 7: DI Wiring
18. **`di/HighlightingModule.kt`** — Hilt `@Module` providing `SyntaxHighlighter` and `LanguageDetector` as `@Singleton`.

### Wave 8: Integration & Polish
19. Update `MessageBubble.kt` to pass `isDarkTheme` (from `MaterialTheme.isSystemInDarkTheme()` or global state).
20. Apply `CodeBlock` to any non-chat locations where code appears (model readmes, help screen — if those get markdown rendering later).
21. Performance validation: measure `SyntaxHighlighter.highlight()` time for 50-line code blocks. Target < 2ms.
22. Streaming validation: confirm incremental highlighting doesn't cause visual flicker during recomposition.

### Build Order Rationale

- **Waves 1–4 are pure Kotlin with zero Compose/Android dependencies** — they can be fully unit-tested without emulator or Robolectric. This follows the project's Clean Architecture pattern (domain first, then data).
- **Wave 5 (CodeTheme migration) is isolated** — it's a find-and-replace import change that must happen before the CodeBlock composable but can happen independently of the syntax engine.
- **Wave 6 (UI) can't start until Wave 5 completes** — `CodeBlock` needs the migrated `CodeTheme` enum.
- **Wave 7 (DI) wires after everything exists** — standard Hilt pattern.
- **Wave 8 (integration) validates the end-to-end user experience** — streaming, theme switching, copy.

## Anti-Patterns to Avoid

### Anti-Pattern 1: Building syntax highlighting into MarkdownText.kt without extraction

**What people do:** Add tokenizer logic directly inside the existing `buildAnnotatedString` block in MarkdownText, mixing markdown parsing with syntax coloring.

**Why it's wrong:** MarkdownText becomes a 400+ line god function. Syntax highlighting can't be tested independently of markdown parsing. The code block UI elements (language header, copy button) can't be added because they require Composition, not just AnnotatedString building.

**Do this instead:** Delegate code block rendering to a separate `CodeBlock` Composable. MarkdownText handles markdown structure detection (` ``` ` fences). CodeBlock handles everything inside the code block (syntax coloring, language detection, UX elements).

### Anti-Pattern 2: WebView-based syntax highlighting

**What people do:** Render code blocks in an Android `WebView` with `highlight.js` injected as JavaScript.

**Why it's wrong:** Heavy (WebView init is ~200ms), breaks offline-first (highlight.js CDN dependency or bundled asset bloat), breaks text selection (SelectionContainer can't span WebView boundaries), leaks memory if not carefully managed, breaks dark mode auto-adaptation.

**Do this instead:** Custom regex tokenizer producing `AnnotatedString`. Fully Compose-native, no bridging, works offline, integrates with SelectionContainer.

### Anti-Pattern 3: Full text in clipboard with formatting

**What people do:** Copy button copies `AnnotatedString` with styling to clipboard.

**Why it's wrong:** Users expect plain text when they paste into editors/terminals. Formatted text pastes as garbage in most contexts.

**Do this instead:** Copy button copies raw code text via `ClipboardManager.setText(AnnotatedString(plainTextContent))`.

### Anti-Pattern 4: Re-running language detection on every recomposition

**What people do:** Call `LanguageDetector.detect()` inside a `@Composable` without memoization, re-running detection on every recompose.

**Why it's wrong:** Language detection involves keyword frequency counting across the entire code block. On recomposition during streaming (every ~50ms), this wastes CPU.

**Do this instead:** Use `remember(codeBlockContent)` to cache detection result. Only re-detect if the code content changed meaningfully (e.g., length delta > 20 chars, or a new line was added). The language hint from the markdown fence (e.g., `\`\`\`python`) serves as a strong prior — once detected as Python, don't re-run detection unless the fence hint is absent.

## Scalability Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 10 languages, 50-line code blocks | Waves 1–4 as designed. Regex tokenizer is fast enough (< 2ms per block). No changes needed. |
| 30+ languages | Add more `*Definitions.kt` files to `definitions/`. Tokenizer architecture supports arbitrary language count. Consider grouping less common languages into a secondary module to reduce APK size. |
| 500+ line code blocks | Regex tokenizer may show latency. Two mitigations: (1) Cap rendering at first 200 lines with "Show all" expander. (2) Swap `RegexSyntaxHighlighter` implementation to `TextMateSyntaxHighlighter` (using `kotlin-textmate` library) once that library proves production-ready. The `SyntaxHighlighter` interface makes this a one-line DI change. |
| Streaming at 60fps | `MarkdownText` recomposition is already optimized by Compose. `CodeBlock` should use `derivedStateOf` for the highlighted `AnnotatedString` to avoid recomputing when other state changes. |

### First Bottleneck: Language detection during streaming

**What breaks:** As code streams token-by-token, `LanguageDetector.detect()` runs on every recomposition (every ~50ms). For a 30-line code block, keyword counting is ~0.5ms — acceptable. But if the code block is 200+ lines, this could become noticeable.

**Fix:** Cache detection result with `remember`. Only re-run detection when the content character count increases by > 20% or a newline appears. The fence hint (`\`\`\`python`) acts as a hard override — if present, skip content-based detection entirely.

### Second Bottleneck: Theme switch re-rendering all visible code blocks

User switches theme → all visible `MessageBubble` instances recompose → all `CodeBlock` instances re-run `SyntaxHighlighter.highlight()`.

**Fix:** `AnnotatedString` building is pure computation (no I/O, no allocations beyond the string itself). For a chat screen with 5 visible code blocks of 30 lines each, recomputation is < 10ms total. Not a bottleneck in practice. If it becomes one, use `derivedStateOf` with `codeTheme` as key.

## Sources

- **Existing codebase analysis** (files read directly):
  - `MarkdownText.kt` — current code block rendering: `withStyle(SpanStyle(background = codeTheme.bgCode)) { append(content) }` (lines 56–85)
  - `MessageBubble.kt` — passes `codeTheme` from props to MarkdownText (lines 34, 109, 173)
  - `ChatUiState.kt` — `codeTheme: CodeTheme = CodeTheme.MONOKAI` (line 36)
  - `ChatViewModel.kt` — `advancedPreferences.codeTheme.collect` (lines 72–74)
  - `AdvancedPreferences.kt` — DataStore persistence for `CodeTheme` (lines 60–69)
  - `SettingsScreen.kt` — dropdown with `CodeTheme.entries.forEach` (lines 205–219)
  - `SettingsViewModel.kt` — `setCodeTheme()` (lines 103–107)
  - `ChatMessage.kt` — `content: String` field (line 9) — code blocks are part of the raw markdown string

- **Library ecosystem survey** (GitHub topics, fetched today):
  - **Prism4j** (noties/Prism4j) — ARCHIVED 2023. Java clone of prism.js. Last release 2019. Not suitable. Source: https://github.com/noties/Prism4j
  - **CodeView-Android** (kbiakov/CodeView-Android, 891 stars) — View-based (RecyclerView), not Compose-native. Last updated Jan 2022. Source: GitHub topics search
  - **KodeView** (SnipMeDev/KodeView, 116 stars) — KMM syntax highlighting views. Source: GitHub topics search
  - **compose-code-editor** (Qawaz/compose-code-editor, 87 stars) — Compose-native but editor-focused, overkill for rendering. Last updated Apr 2024. Source: GitHub topics search
  - **Highlights** (SnipMeDev/Highlights, 183 stars) — KMM syntax highlighting engine. Updated Sep 2025. Potential future upgrade path but KMM-focused, may have multiplatform overhead. Source: GitHub topics search
  - **kotlin-textmate** (ivan-magda/kotlin-textmate, 12 stars) — NEW (updated today). Pure Kotlin TextMate grammar engine with Compose AnnotatedString rendering. Very promising for future upgrade but too new to depend on for production. Source: https://github.com/ivan-magda/kotlin-textmate
  - **highlight.kt** (nyancrimew/highlight.kt, 6 stars) — Kotlin port of highlight.js. Unmaintained since 2019. Source: GitHub topics search

- **Architecture references:**
  - `.planning/codebase/ARCHITECTURE.md` — existing Clean Architecture layer documentation (HIGH confidence, mapped from recent commit)
  - `.planning/PROJECT.md` — milestone v1.6 definition, constraints

- **Confidence assessment:**
  - Custom regex tokenizer approach: **HIGH** — proven pattern (TextMate, Pygments, highlight.js all use regex tokenizers). Compose `AnnotatedString` integration is straightforward.
  - Library survey: **HIGH** — directly fetched from GitHub topics and repository pages today.
  - Integration with existing MarkdownText: **HIGH** — read source code directly, zero ambiguity about current implementation.
  - Language auto-detection: **MEDIUM** — heuristic approach (keyword frequency) works well for common LLM languages but edge cases exist. Flagged for deeper research in Phase plan.

---

*Architecture research for: Code syntax highlighting integration into Warped chat app*
*Researched: 2026-05-14*
