# Pitfalls Research: Code Syntax Highlighting for Android Compose Chat

**Domain:** Streaming LLM chat app with code block syntax highlighting
**Researched:** 2026-05-14
**Confidence:** HIGH

## Critical Pitfalls

### Pitfall 1: Prism4j is Archived — Do Not Use

**What goes wrong:**
Prism4j (`io.noties:prism4j`) — the only Java/Kotlin port of Prism.js worth considering — has been **archived since July 2023**. Last release was v2.0.0 in June 2019. No security patches, no new language grammars, no Kotlin multiplatform updates. Using it means shipping an unmaintained dependency that will never receive updates.

**Why it happens:**
Developers search "syntax highlighting library Kotlin Android" and find Prism4j as the top result. It has 24 languages and is mentioned in Markwon integration guides. It looks mature. But digging deeper reveals it's a dead project.

**How to avoid:**
- **Use highlight.js Kotlin Multiplatform port (`highlight-kt`)** if one exists and is maintained — search for alternatives on GitHub. If none exists, consider adapting the Dart port `highlight` (`pd4d10/highlight.dart`) — it has 192 languages, 257 themes, and is actively maintained. Kotlin/Dart interop isn't trivial but the grammar definitions (JSON-based) could be ported.
- **Fallback approach — Build a custom tokenizer**: Since the project only needs code block highlighting (not a full IDE), a lightweight regex-based tokenizer for 5-8 popular languages (Python, JavaScript, Kotlin, Java, Shell, JSON, YAML, SQL) is sufficient. This avoids dependency risk entirely. Each language is ~50-80 lines of regex patterns.
- **Alternatively — Use JS-based syntax highlighting via WebView**: Embed highlight.js in a lightweight WebView for code blocks. This is heavy (~5MB for WebView init) but gives you 190+ languages and 250+ themes from an actively maintained library. Only suitable if WebView cost is acceptable.

**Warning signs:**
- You find a "syntax highlighting library" and the last commit is >2 years old
- The library doesn't mention Kotlin or Compose (Java-only)
- It uses annotation processors that may not work with KSP

**Phase to address:** Phase 1 (Tokenization Engine). This is the first decision — everything else depends on it.

---

### Pitfall 2: Tokenizing Every Streaming Token is O(n²) Jank

**What goes wrong:**
During streaming, the ViewModel emits tokens every ~50ms (see `ChatViewModel.kt:225`). If you re-tokenize the entire code block text on every token emission, the complexity is O(n²) where n is the final block length. A 2000-character code block tokenized 2000/3 ≈ 667 times = ~1.3M regex operations. On a phone CPU, this will drop frames noticeably.

**Why it happens:**
The existing `MarkdownText.kt` uses `buildAnnotatedString` which scans the entire text string on every recomposition. Adding syntax highlighting naturally extends this pattern: "re-highlight everything when new text arrives." But Prism.length matching (`pattern(compile("..."))`) runs regex on the full string — it doesn't have an incremental API.

**How to avoid:**
```
Three strategies, in order of preference:

1. DEBOUNCE + BATCH (recommended): Don't highlight while the block is still
   in streaming state (fence not yet closed). Show the current flat monospace
   rendering until ``` closes the block, THEN tokenize once. This avoids the
   O(n²) problem entirely. User sees plain monospace code streaming in, then
   it "snaps" to full color when the block completes.

2. THROTTLE: Tokenize only every 250ms during streaming. Use `flow {}.sample(250)`
   or manual timestamp comparison. Accept that highlighting lags behind text
   slightly. Better than jank.

3. DIFF-BASED: Track the last tokenized length. Only tokenize the new suffix
   and append styles. Requires the tokenizer to support partial/line-based
   highlighting (regex tokenizers typically don't — patterns may span lines).

RECOMMENDATION: Use Strategy #1 for MVP. It's the simplest, avoids all
streaming performance issues, and matches how LM Studio and ChatGPT behave
(they don't syntax-highlight mid-stream). Upgrade to Strategy #2 later if
users demand it.
```

**Warning signs:**
- Scrolling during streaming feels choppy
- `buildAnnotatedString` runs on every `UiState` emission
- Profiler shows `Pattern.compile` or regex matching on the main thread during streaming

**Phase to address:** Phase 2 (Streaming Integration). Must be addressed before code blocks are syntax-highlighted during live streaming.

---

### Pitfall 3: Theme System Only Has 2 Colors — Can't Highlight Tokens

**What goes wrong:**
The existing `CodeTheme` enum (see `MarkdownText.kt:21-28`) stores only `bgCode` (block background) and `bgInline` (inline background). Syntax highlighting needs **per-token-type colors**: keywords (blue), strings (green), numbers (orange), comments (gray), functions (yellow), types (teal), operators (white), punctuation (gray), etc. The current theme cannot express these.

**Why it happens:**
The original `CodeTheme` was designed for flat code rendering (monospace + background only). Adding syntax highlighting requires expanding the color palette from 2 to 12-18 colors per theme. This is a breaking change to the theme data model, and it affects DataStore serialization (`AdvancedPreferences.kt:33` stores `KEY_CODE_THEME` as a string enum name).

**How to avoid:**
```kotlin
// Replace CodeTheme enum with a richer structure
data class SyntaxTheme(
    val name: String,
    val label: String,
    val bgCode: Color,
    val bgInline: Color,
    val isDark: Boolean, // true = dark background theme, false = light
    val tokenColors: Map<String, Color> // "keyword" → Color(0xFF...), etc.
) {
    companion object {
        val MONOKAI = SyntaxTheme(
            name = "monokai",
            label = "Monokai",
            bgCode = Color(0xFF272822),
            bgInline = Color(0xFF3E3D32),
            isDark = true,
            tokenColors = mapOf(
                "keyword" to Color(0xFFF92672),
                "string" to Color(0xFFE6DB74),
                "number" to Color(0xFFAE81FF),
                "comment" to Color(0xFF75715E),
                "function" to Color(0xFFA6E22E),
                "type" to Color(0xFF66D9EF),
                "operator" to Color(0xFFF92672),
                "punctuation" to Color(0xFFF8F8F2),
                "variable" to Color(0xFFF8F8F2),
                "plain" to Color(0xFFF8F8F2)
            )
        )
        // ... DRACULA, ONE_DARK, GITHUB, NORD, etc.
    }
}
```

**Data migration:** When upgrading from the old `CodeTheme` enum, read the stored string, map it to the new `SyntaxTheme.name`, and default to Monokai if unrecognized. The `AdvancedPreferences.kt` storage key doesn't change — only the mapped values do.

**Warning signs:**
- You add a new theme and only change `bgCode` — tokens all render in the same color
- You try to store token colors as a flattened string in DataStore (don't — it's not a KV store; DataStore is fine for storing the theme name; the colors live in code)
- You try to make token colors user-customizable individually (out of scope for v1; just offer 4-5 preset themes)

**Phase to address:** Phase 1 (Tokenization Engine) — must define the theme data model first because tokenizer output references token types, and token types map to colors via the theme.

---

### Pitfall 4: Copied Text is AnnotatedString, Not Raw Code

**What goes wrong:**
Using `clipboardManager.setText(annotatedString)` (Compose's `LocalClipboardManager`) copies the `AnnotatedString` — spans, colors, font styles and all. But `ClipboardManager.setText()` only copies the plain text portion (`annotatedString.text`). The problem is **what you copy**: if you pass the full `buildAnnotatedString` result including styled spans, and the user pastes into a plain text editor, they get the correct raw text — but if they paste into a rich text editor, they may get unwanted formatting.

**Why it happens:**
Compose's `ClipboardManager.setText(AnnotatedString)` is the recommended API. It only copies the text content, not the span styles. So the copy **works correctly for plain text**. But developers sometimes try to build a second `AnnotatedString` specifically for copying or call `setClip(ClipEntry(ClipData.newPlainText(...)))` using the View-based ClipboardManager. Both approaches work but have subtle differences.

The **real pitfall** is: the code block content in memory is the raw text from the LLM response (stored in `ChatMessage.content`), not the annotated string. So when the user taps "Copy" on a code block, you need to extract the **raw text between the ``` fences**, not the rendered `AnnotatedString`.

**How to avoid:**
```kotlin
// Option A — Compose ClipboardManager (simplest, recommended)
val clipboardManager = LocalClipboardManager.current

Button(onClick = {
    clipboardManager.setText(AnnotatedString(rawCodeContent))
}) {
    Icon(Icons.Default.ContentCopy, "Copy code")
}

// Option B — View-based ClipboardManager for Android 10+ background copy
val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
val clip = ClipData.newPlainText("code", rawCodeContent)
clipboard.setPrimaryClip(clip)

// CRITICAL: Never copy the AnnotatedString with syntax spans.
// Always copy the raw source text.
```

**Secret UX improvement:** After copying, show a brief "Copied!" toast or change the icon to a checkmark for 2 seconds (use `LaunchedEffect` + `delay` + local state). This confirms the action without needing a Snackbar.

**Android 10+ restriction:** On Android 10+, `ClipboardManager.setPrimaryClip()` throws `SecurityException` if called from the background. Compose's `LocalClipboardManager` handles this internally. If you use the View-based clipboard API, ensure copy is triggered by a user click (foreground context) — which it always is for a Button click.

**Warning signs:**
- Copy button copies the entire message, not just the code block
- Pasted code includes invisible ANSI escape sequences or markdown syntax
- Copy fails silently on certain Android versions
- "Copied!" toast/icon persists across recompositions (forgot to reset)

**Phase to address:** Phase 3 (UI Components). The copy button is a UI feature; the underlying text extraction is straightforward.

---

### Pitfall 5: Language Detection is Probabilistic — Inconsistent Results Break UX

**What goes wrong:**
When an LLM emits ```` ``` ```` without a language identifier (or with a wrong one), you need to auto-detect the language. Auto-detection is inherently uncertain. A 10-line Python script with lots of comments might be detected as "plain text." A JSON snippet might be detected as "JavaScript." Wrong language = wrong highlighting = user confusion ("why are the colors wrong?").

**Why it happens:**
Language detection algorithms (Bayesian classifiers, keyword heuristics, shebang parsing) have accuracy rates of 70-90% on short snippets. LLM-generated code blocks are often short (5-30 lines), which makes detection harder. The fence info line (```` ```python ````) is the best signal, but LLMs are inconsistent about providing it.

**How to avoid:**
```kotlin
// Priority-based language resolution
fun resolveLanguage(fenceInfoText: String?, codeContent: String): String {
    // 1. Trust explicit fence info (```python)
    if (!fenceInfoText.isNullOrBlank()) {
        val normalized = normalizeLanguageName(fenceInfoText) // "py" → "python"
        if (knownLanguages.contains(normalized)) return normalized
    }
    
    // 2. Check for shebang (#! /usr/bin/env python3)
    val shebang = codeContent.lines().firstOrNull()?.takeIf { it.startsWith("#!") }
    if (shebang != null) {
        val lang = parseShebang(shebang)
        if (lang != null) return lang
    }
    
    // 3. Heuristic keyword-based detection (cheap, fast)
    // Count language-specific keywords: def/class → python, function/const → javascript
    val detected = detectByKeywords(codeContent)
    if (detected != null && confidence(detected) > 0.6f) return detected
    
    // 4. Fall back to "text" (plain rendering, no syntax highlighting)
    return "text"
}

// Map aliases to canonical names
fun normalizeLanguageName(raw: String): String {
    val lower = raw.lowercase().trim()
    return when {
        lower in listOf("py", "python3") -> "python"
        lower in listOf("js", "javascript", "node", "nodejs") -> "javascript"
        lower in listOf("ts", "typescript") -> "typescript"
        lower in listOf("sh", "bash", "zsh", "shell") -> "bash"
        lower in listOf("kt", "kotlin") -> "kotlin"
        lower in listOf("yml") -> "yaml"
        else -> lower
    }
}
```

**Key design decision:** "text" (no highlighting) is better than wrong highlighting. A code block with neutral monospace rendering is usable. A code block with mangled colors is confusing. Show the language name in the header bar even when it's "text" or "plain" — this tells the user what happened.

**Warning signs:**
- You only check the fence info line (LLMs frequently omit it)
- You use a heavy ML-based language detector (>1MB model, >100ms inference)
- Language detection runs on every streaming token (should run once when block completes)
- The header bar shows "python" when the code is clearly JavaScript

**Phase to address:** Phase 1 (Tokenization Engine). Language detection is tightly coupled to tokenization — you can't tokenize without knowing the language.

---

### Pitfall 6: Compose Recomposition Calls buildAnnotatedString on Every Frame

**What goes wrong:**
When `streamingContent` changes (every ~50ms during streaming), `ChatUiState` changes → `StateFlow` emits → `MarkdownText` recomposes → `buildAnnotatedString {}` runs entirely from scratch. This includes: scanning all lines for markdown, parsing inline styles, and (after this feature) tokenizing syntax for code blocks. On every frame. For a message with 500+ lines of text and multiple code blocks, this is a guaranteed frame drop.

**Why it happens:**
`buildAnnotatedString` is called directly in the composable body of `MarkdownText` (line 45). It's not memoized, not `remember`ed, not `derivedStateOf`. Compose treats it as a plain function and re-executes it on every recomposition, which happens when any `StateFlow` dependency in the parent composable changes.

**How to avoid:**
```kotlin
@Composable
fun MarkdownText(
    text: String,
    // ...
) {
    // MEMOIZE the expensive computation
    val annotatedString = remember(text, codeTheme, fontSize, fontStyle, maxLines) {
        buildMarkdownAnnotatedString(text, codeTheme, fontSize, fontStyle)
    }
    
    Text(annotatedString, modifier = modifier, color = baseColor, maxLines = maxLines)
}

// Move the build logic OUT of composable scope to a pure function
private fun buildMarkdownAnnotatedString(
    text: String,
    codeTheme: SyntaxTheme,
    fontSize: Float?,
    fontStyle: FontStyle?
): AnnotatedString = buildAnnotatedString {
    // ... existing parsing logic ...
    // Code blocks: only syntax-highlight if theme has token colors defined
    if (codeTheme.tokenColors.isNotEmpty() && codeBlockContent.isNotEmpty()) {
        highlightAndAppend(codeBlockContent.toString(), detectedLanguage, codeTheme)
    } else {
        // Flat monospace fallback (current behavior)
        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeTheme.bgCode)) {
            append(codeBlockContent.toString().trimEnd())
        }
    }
}
```

**The `remember` key:** All inputs that affect the annotated string output must be keys. If any key changes, the entire `buildAnnotatedString` re-runs. This is correct — what `remember` saves you from is the case where `text` hasn't changed but other parts of `ChatUiState` have (e.g., `inputText`, `connectionStatus`), which would trigger unnecessary recomputation.

**Warning signs:**
- `buildAnnotatedString` appears in Compose Layout Inspector as a heavy composable
- Frame times spike when any UiState field changes (not just streamingContent)
- `remember` key list is incomplete — annotated string not invalidated when theme changes

**Phase to address:** Phase 2 (Streaming Integration). This is the core performance fix for streaming.

---

### Pitfall 7: Code Block Detection During Streaming Causes Flicker

**What goes wrong:**
During streaming, `MarkdownText` sees lines arriving one at a time. When a ```` ``` ```` fence opens, the renderer switches to "code block mode" (monospace + background). But it can't syntax-highlight yet because the code block content is incomplete — trailing backticks, half-written lines, and no known language. The block renders as flat monospace gray. When the closing ```` ``` ```` arrives, the renderer switches to syntax-highlighted mode. This causes a visible flicker: gray background → colored tokens. If the closing fence is delayed (LLM is slow), the user sees gray text for seconds, then it snaps to color.

**Why it happens:**
Syntax highlighting requires the complete code block text. You can't highlight a partial JavaScript snippet because regex patterns expect complete tokens (e.g., `/\* ... \*/` for block comments, or matching brackets). The opening `{` hasn't been paired with `}` yet.

**How to avoid:**
This is the same mitigation as Pitfall #2. **Don't syntax-highlight during streaming.** The code block renders as flat monospace while the closing fence hasn't arrived. Once it does, syntax-highlight. The visual transition is:

```
[Streaming, fence closed]     gray monospace code  →  colored highlighted code
         ↑                                                    ↑
      (current)                                        (new behavior)
```

You can make this transition smooth by:
1. Setting `transition = animateColorAsState()` on the text colors (Compose animation API)
2. Using `AnimatedVisibility` with a fade-in for the highlighted block
3. Keeping the background color the same during the transition (only token text colors change)

**Warning signs:**
- Code block appears to "flash" between gray and colored as tokens arrive
- Syntax highlighting runs on incomplete code (regex throws exceptions on malformed input)
- The language header bar disappears and reappears during streaming (it shouldn't — show it as soon as the opening fence appears, even if language is unknown)

**Phase to address:** Phase 2 (Streaming Integration).

---

## Technical Debt Patterns

Shortcuts that seem reasonable but create long-term problems.

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Use Prism4j despite being archived | Fast integration, 24 languages | Zero maintenance, no bug fixes, forced rewrite later | **Never** — it's archived. |
| Skip language detection, always use fence info | Simpler code | 40% of LLM code blocks lack fence info → no highlighting | Never. Detection is essential. |
| Tokenize on main thread for blocks < 100 lines | Simpler threading | Blocks > 100 lines (generated configs, data dumps) cause ANR | Only if all code blocks are verified < 100 lines (they won't be). |
| Store token colors in DataStore as JSON string | "Flexible theming" | Fragile serialization, migration pain, no compile-time safety | Never for v1. Use preset themes defined in Kotlin. |
| Hardcode copy button on every code block without lazy loading | Simple layout | 20+ code blocks in a message → 20 copy buttons → layout passes slow | Never. Use a single copy button pattern with `key()` scoping. |
| Use a WebView for highlight.js rendering | 190+ languages, 250+ themes | 5MB+ memory per WebView, slow init (200-500ms), layout quirks with Compose interop | Only if you need 50+ languages and custom themes are critical. Not for v1. |
| Monospace color hardcoded to `Color.White` regardless of theme | Quick fix | Dark themes → invisible text on dark backgrounds | Never. Always use theme-aware colors. |

## Integration Gotchas

Common mistakes when connecting to existing code.

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| `ChatViewModel.streamingContent` → `MarkdownText` | Rebuild annotated string on every streamingContent change (50ms intervals) | `remember(text, theme) { buildAnnotatedString }` — only recompute when text actually changes, not when other UiState fields change. |
| `CodeTheme` enum → `SyntaxTheme` | Silently replace the enum, breaking DataStore deserialization for existing users | Keep `KEY_CODE_THEME` key. At startup, try `CodeTheme.valueOf(storedName)`, map to new `SyntaxTheme`. If neither works, default to Monokai. |
| `AdvancedPreferences.codeTheme` Flow → `ChatUiState.codeTheme` | Emit twice during theme migration (old value → new value), causing double recomposition | Use `Flow.distinctUntilChanged()` on the mapped theme. Ensure migration emits only once. |
| Prism4j tokenizer → `buildAnnotatedString` | Tokenize on main thread, blocking UI for large blocks | Run tokenization on `Dispatchers.Default`. `remember` the result. |
| `LocalClipboardManager` → copy code block | Copy `AnnotatedString` with syntax spans → pasted text has invisible formatting in some apps | Always copy raw `String` via `AnnotatedString(rawText)`. Compose's `setText()` strips spans, but be explicit. |
| Language header bar → Compose layout | Use `Row` inside `Column` inside `Surface` → extra nesting, hard to slot into existing `MarkdownText` which returns a flat `Text` | Extract code blocks into separate composables (`CodeBlockCard`), don't try to keep them inline in `buildAnnotatedString`. |
| `MarkdownText` composable → multiple call sites | Only `MessageBubble.kt` uses it now → adding copy button, header bar to every call site duplicates code | Create a unified `MarkdownView` composable that wraps `MarkdownText` with code block overlays. All call sites use `MarkdownView`. |

## Performance Traps

Patterns that work at small scale but fail as usage grows.

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| `buildAnnotatedString` inside composable without `remember` | Frame drops during streaming, every UiState change is slow | `remember(text, theme, fontConfig) { buildAnnotatedString {} }` | Immediately on streaming (every 50ms) |
| Regex tokenization on main thread | UI freezes for 200-500ms when large code block closes | `withContext(Dispatchers.Default) { tokenize(content) }` | Code blocks > 200 lines |
| Re-tokenizing same code block on every recomposition | CPU usage spikes when scrolling chat | Cache tokenized result: `Map<String, List<Token>>` with code text as key | 5+ visible code blocks in scroll viewport |
| `HorizontalPager` or `LazyColumn` with code blocks inside | Each item recomposes independently, `remember` scoped to item | Use `key(block.hashCode())` to scope `remember` correctly | Chat history with 20+ messages |
| Clipboard `setPrimaryClip` from background thread | `SecurityException` on Android 10+ | Always call clipboard from user click handler (foreground) | Android 10+ devices |
| Detecting language on every token during streaming | Slower streaming, regex on incomplete code → wrong detection | Detect once when ```` ``` ```` fence closes (final detection) | Any code block > 5 lines |

## UX Pitfalls

Common user experience mistakes in this domain.

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Wrong language detection causes wrong colors | User thinks the app is buggy ("why is my Python code highlighted as JavaScript?") | Fence info is authoritative. Auto-detection is a fallback. Show detected language in header bar so user can verify. |
| No visual indicator that code was copied | User taps "Copy" multiple times, not knowing it worked on the first tap | Show a brief "Copied!" confirmation: icon swap to checkmark for 1.5s with a fade animation. |
| Code header bar takes too much vertical space | Long code blocks become even taller, more scrolling needed | Header bar is 32-40dp height, collapsible. Show language name compactly. No extra padding. |
| Syntax theme doesn't adapt to light/dark mode | Light theme + dark code blocks = jarring contrast. Dark theme + white code blocks = blinding. | Each preset has a light and dark variant. `isSystemInDarkTheme()` switches between them. GitHub theme: white bg in light mode, dark gray in dark mode. |
| Copy button overlaps with code text | Long code lines extend under the button, unreadable | Place copy button in the header bar (top-right of code block), not floating over code. |
| No language detected → code looks plain | User doesn't know if highlighting failed or the feature is broken | Always show language name in header bar, even when "text" / "no highlighting". This signals that detection ran and concluded. |

## "Looks Done But Isn't" Checklist

Things that appear complete but are missing critical pieces.

- [ ] **Syntax highlighting:** Works on the happy-path ```` ```python ```` case, but crashes on empty blocks, malformed regex input, or code with only special characters. **Verify:** Fuzz test with random strings inside code fences.
- [ ] **Theme switching:** Theme changes from Settings, but the active chat doesn't re-compose because the `StateFlow` emits before the MarkdownText `remember` key includes the theme. **Verify:** Change theme in Settings, return to chat, confirm code blocks change color immediately.
- [ ] **Copy button:** Copies text, but copies the entire message (not just the code block) when there's no clear block boundary. **Verify:** A message with "Here's the code: ```python\nprint('hi')\n```\nThat's it." should copy only `print('hi')`, not the full message.
- [ ] **Language header bar:** Shows for ```` ``` ```` blocks, but also shows for inline `code` spans (it shouldn't — inline code has no header). **Verify:** Messages with both inline code and code blocks render correctly without header leakage.
- [ ] **Offline mode:** Syntax highlighting works without internet (all tokenization is local). **Verify:** Airplane mode, load a conversation with code blocks, confirm highlighting renders.
- [ ] **Dark/light mode:** Theme adapts when system dark mode changes at runtime (not just on app start). **Verify:** Toggle system dark mode while viewing a code block, confirm colors switch.
- [ ] **Migration from old theme:** Users upgrading from the old `CodeTheme` enum don't crash. **Verify:** Install old version, set theme to Dracula, upgrade to new version, confirm theme maps correctly (not crash, not reset to Monokai).
- [ ] **Long code blocks:** Blocks > 500 lines don't cause ANR. **Verify:** Generate a 500-line JSON file as an LLM response, scroll to it, confirm smooth rendering.

## Pitfall-to-Phase Mapping

How roadmap phases should address these pitfalls.

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| #1 (Archived Prism4j) | Phase 1: Tokenization Engine | Library chosen is actively maintained (last commit < 6 months) or custom built with test coverage for 8 languages |
| #2 (O(n²) streaming) | Phase 2: Streaming Integration | `remember` with debounce; code block only highlighs after fence closes; no regex on main thread during streaming |
| #3 (Theme data model) | Phase 1: Tokenization Engine | `SyntaxTheme` data class defined with tokenColors map; all 5 preset themes implemented; DataStore migration tested |
| #4 (Clipboard copy) | Phase 3: UI Components | Copy button copies raw text only; "Copied!" confirmation shows for 1.5s; works on Android 10-15 |
| #5 (Language detection) | Phase 1: Tokenization Engine | Fence info → shebang → keyword heuristics → "text" fallback chain; 80%+ accuracy on LLM code outputs |
| #6 (Recomposition jank) | Phase 2: Streaming Integration | `remember(text, theme)` around `buildAnnotatedString`; Layout Inspector shows no skip failures for MarkdownText |
| #7 (Code block flicker) | Phase 2: Streaming Integration | Code blocks render flat monospace while streaming; animate to syntax colors when block completes; smooth color transition |
| #8 (`AnnotatedString` for code blocks) | Phase 3: UI Components | Code blocks extracted as separate `CodeBlockCard` composables from `buildAnnotatedString`; `remember` per block |

## Sources

- **Prism4j GitHub:** https://github.com/noties/Prism4j — ARCHIVED July 2023. Last release v2.0.0 (June 2019). 24 language grammars. Apache 2.0 license. (HIGH confidence — confirmed on GitHub README and repository status)
- **highlight.dart GitHub:** https://github.com/pd4d10/highlight.dart — Active Flutter/Dart port of highlight.js. 192 languages, 257 themes. MIT license. (MEDIUM confidence — useful reference implementation but Dart, not Kotlin)
- **Jetpack Compose Performance (Stability):** https://developer.android.com/develop/ui/compose/performance/stability — Official Google documentation. Covers `@Immutable`, `@Stable`, `remember` patterns, recomposition skipping. (HIGH confidence)
- **Jetpack Compose ClipboardManager:** https://developer.android.com/develop/ui/compose/touch-input/copy-and-paste — Official Google documentation. `LocalClipboardManager`, `setText()`, `getText()`. (HIGH confidence)
- **Jetpack Compose buildAnnotatedString:** https://developer.android.com/reference/kotlin/androidx/compose/ui/text/buildAnnotatedString — Official API reference. (HIGH confidence)
- **Existing Warped codebase:**
  - `MarkdownText.kt` (167 lines) — Current flat monospace code block rendering with `CodeTheme` enum (6 themes, 2 colors each)
  - `ChatViewModel.kt` — Streaming at 50ms intervals via `StreamToken.Delta`; `streamingContent` StateFlow drives recomposition
  - `AdvancedPreferences.kt` — DataStore persistence for `KEY_CODE_THEME` stored as string enum name
  - `MessageBubble.kt` — `MarkdownText` called for message content and reasoning blocks with `codeTheme` parameter
  - `ChatUiState.kt` — `codeTheme: CodeTheme` field, default `MONOKAI`
  - `SettingsScreen.kt` — Dropdown theme selector using `CodeTheme.entries.forEach`
  - (HIGH confidence — directly inspected source files)

---

*Pitfalls research for: Code Syntax Highlighting in Warped Android Chat App*
*Researched: 2026-05-14*
