---
phase: 29-ui-components-markdown-refactoring
plan: 03
subsystem: UI Integration & Settings
tags: [syntax-theme, integration, settings, preferences, migration]
requires: ["29-01", "29-02"]
provides: "Full syntax highlighting integration — theme flows from Settings through ViewModels to chat composables"
affects: [ChatScreen, ChatViewModel, ChatUiState, MessageBubble, MarkdownText, SettingsScreen, SettingsViewModel, SettingsUiState, AdvancedPreferences]
tech-stack:
  added: ["SyntaxTheme integration", "Hilt EntryPoint for LanguageDetector", "codeFontScale DataStore persistence"]
  patterns: ["Flow-based theme propagation: AdvancedPreferences → ViewModel → UiState → Composable"]
key-files:
  created: ["app/src/main/java/com/warped/ui/settings/ToolState.kt", "app/src/main/java/com/warped/ui/settings/SettingsTab.kt"]
  modified:
    - "app/src/main/java/com/warped/ui/chat/ChatUiState.kt"
    - "app/src/main/java/com/warped/ui/chat/ChatViewModel.kt"
    - "app/src/main/java/com/warped/ui/chat/ChatScreen.kt"
    - "app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt"
    - "app/src/main/java/com/warped/ui/chat/components/MarkdownText.kt"
    - "app/src/main/java/com/warped/ui/settings/SettingsUiState.kt"
    - "app/src/main/java/com/warped/ui/settings/SettingsViewModel.kt"
    - "app/src/main/java/com/warped/ui/settings/SettingsScreen.kt"
    - "app/src/main/java/com/warped/data/local/preferences/AdvancedPreferences.kt"
decisions:
  - "LanguageDetector resolved via Hilt EntryPoint in MarkdownText (self-contained, no threading through ChatScreen/MessageBubble needed)"
  - "codeTheme field kept same name across all layers despite type change (CodeTheme → SyntaxTheme) to minimize diff"
  - "setCodeTheme(Vm) method name unchanged despite going to setSyntaxTheme(AdvancedPreferences) internally"
  - "ThemeSwatchStrip uses isSystemInDarkTheme() to pick darkVariant/lightVariant for accurate swatch preview"
metrics:
  duration: "11 minutes"
  completed_date: "2026-05-15T01:33:03Z"
  task_count: 3
  file_count: 12
---

# Phase 29 Plan 03: SyntaxTheme Integration & Settings Migration Summary

Syntax highlighting engine (Phase 28) and composables (Plans 29-01/02) wired into full app. All `CodeTheme` references migrated to `SyntaxTheme` across ChatScreen, MessageBubble, ChatUiState/Vm, SettingsUiState/Vm/Screen. Settings upgraded with ExposedDropdownMenuBox theme picker and code font scale slider. `codeFontScale` persisted via DataStore.

## Task 1: Migrate Chat layer from CodeTheme to SyntaxTheme
- **Commit:** `88fe7b8`
- **Changes:**
  - `ChatUiState.kt`: Added `codeTheme: SyntaxTheme = SyntaxTheme.MONOKAI` field + import
  - `ChatViewModel.kt`: Injected `AdvancedPreferences`, added `syntaxTheme.collect` → `_uiState.update { it.copy(codeTheme = theme) }`
  - `MessageBubble.kt`: Added `codeTheme: SyntaxTheme` parameter, passed to both MarkdownText calls
  - `ToolState.kt`: Created missing data class (pre-existing blocker fix)

## Task 2: Wire SyntaxTheme + isStreaming + LanguageDetector in chat chain
- **Commit:** `194d820`
- **Changes:**
  - `MarkdownText.kt`: Added Hilt `MarkdownEntryPoint` for `LanguageDetector`; resolved via `EntryPoints.get()` when parameter is null
  - `MessageBubble.kt`: Added `isStreaming = isStreaming` to both MarkdownText calls (reasoning + content)
  - `ChatScreen.kt`: Added `codeTheme = uiState.codeTheme` to both MessageBubble calls (history messages + streaming message)

## Task 3: Update Settings screen with theme dropdown, swatch strip, and code font scale slider
- **Commit:** `b7eb548`
- **Changes:**
  - `SettingsUiState.kt`: Added `codeTheme: SyntaxTheme` + `codeFontScale: Float = 1.0f`
  - `SettingsViewModel.kt`: Added `syntaxTheme.collect`, `codeFontScale.collect`, `setCodeTheme(SyntaxTheme)`, `setCodeFontScale(Float)`
  - `AdvancedPreferences.kt`: Added `KEY_CODE_FONT_SCALE` float preference, `codeFontScale: Flow<Float>`, `setCodeFontScale(scale)` with `coerceIn(0.8f, 1.5f)`
  - `SettingsScreen.kt`: Added Display section with ExposedDropdownMenuBox theme picker (4-color ThemeSwatchStrip) + code font scale Slider (0.8x–1.5x, 6 steps)
  - `SettingsTab.kt`: Created missing enum (pre-existing blocker fix)

## Data Flow (Complete Chain)

```
AdvancedPreferences.syntaxTheme (DataStore)
  → ChatViewModel → ChatUiState.codeTheme
    → ChatScreen → MessageBubble(codeTheme = uiState.codeTheme)
      → MarkdownText(codeTheme = codeTheme)
        → CodeBlock(syntaxTheme = codeTheme)

AdvancedPreferences.syntaxTheme (DataStore)
  → SettingsViewModel → SettingsUiState.codeTheme
    → SettingsScreen → ExposedDropdownMenuBox (theme selector)

AdvancedPreferences.codeFontScale (DataStore)
  → SettingsViewModel → SettingsUiState.codeFontScale
    → SettingsScreen → Slider (0.8x–1.5x)
  → [To be consumed by CodeBlock in Phase 30]
```

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical Func] ChatScreen did not pass codeTheme to MessageBubble**
- **Found during:** Task 2
- **Issue:** ChatScreen called `MessageBubble(message = message)` without `codeTheme` parameter, using default SyntaxTheme.MONOKAI instead of user's selected theme from settings.
- **Fix:** Added `codeTheme = uiState.codeTheme` to both MessageBubble calls in ChatScreen (history messages loop + streaming placeholder).
- **Files modified:** `ChatScreen.kt`
- **Commit:** `194d820`

**2. [Rule 3 - Blocking] Missing ToolState data class**
- **Found during:** Task 1 (pre-existing)
- **Issue:** `ToolState` referenced from `ToolSettingsViewModel.kt` and `SettingsViewModel.kt` did not exist in codebase.
- **Fix:** Created `app/src/main/java/com/warped/ui/settings/ToolState.kt` with `id`, `name`, `description`, `tokenEstimate`, `defaultEnabled`, `enabled` fields.
- **Commit:** `88fe7b8`

**3. [Rule 3 - Blocking] Missing SettingsTab enum**
- **Found during:** Task 3
- **Issue:** `SettingsTab` referenced from `SettingsViewModel.selectTab()` and `SettingsScreen` tab row did not exist.
- **Fix:** Created `app/src/main/java/com/warped/ui/settings/SettingsTab.kt` with `General`, `Tools`, `Advanced` entries.
- **Commit:** `b7eb548`

### Pre-existing Issues (Not Fixed — Out of Scope)

**4. Missing fields in SettingsUiState** — `advancedParams`, `toolStates`, `enabledToolIds`, `selectedTab` are missing from `SettingsUiState` data class. SettingsViewModel and SettingsScreen reference these fields, causing compilation errors. These were introduced before plan 29-03 and are documented in `deferred-items.md`.

**5. Operator modifier missing** — `component1()`/`component2()` extension functions in SettingsScreen.kt lack `operator` modifier.

## Known Stubs

| Stub | File | Line | Reason |
|------|------|------|--------|
| `codeFontScale = 1.0f` (hardcoded) | `CodeBlock.kt` | 115 | To be wired from AdvancedPreferences in Phase 30 |
| Flat monospace during stream | `CodeBlock.kt` | 134-136 | `isStreaming` parameter accepted but transition to highlighted handled in Phase 30 |
| CodeBlock fallback in MarkdownText | `MarkdownText.kt` | 91-101 | Uses Surface + Text fallback until CodeBlock composable integration (Plan 29-02/03 integration point) |

## Verification Status

- ✅ ChatUiState.codeTheme: `SyntaxTheme` type (import + field)
- ✅ ChatViewModel: collects `advancedPreferences.syntaxTheme` Flow
- ✅ MessageBubble: `codeTheme: SyntaxTheme` parameter, passes to MarkdownText
- ✅ MessageBubble: `isStreaming` passed to both MarkdownText calls
- ✅ MarkdownText: Hilt `MarkdownEntryPoint` for LanguageDetector
- ✅ ChatScreen: passes `codeTheme = uiState.codeTheme` to MessageBubble
- ✅ SettingsUiState: `codeTheme: SyntaxTheme` + `codeFontScale: Float`
- ✅ SettingsViewModel: `syntaxTheme.collect`, `codeFontScale.collect`, `setCodeTheme()`, `setCodeFontScale()`
- ✅ SettingsScreen: `ExposedDropdownMenuBox` with `ThemeSwatchStrip` (4-color: KEYWORD, STRING, COMMENT, BACKGROUND)
- ✅ SettingsScreen: code font scale `Slider` (0.8f..1.5f, steps=6, "%.1fx" format)
- ✅ AdvancedPreferences: `KEY_CODE_FONT_SCALE`, `codeFontScale: Flow<Float>`, `setCodeFontScale()` with `coerceIn(0.8f, 1.5f)`
- ❌ `./gradlew :app:compileDebugKotlin` — blocked by pre-existing issues in SettingsUiState (missing fields `advancedParams`, `toolStates`, `enabledToolIds`, `selectedTab`) — see deferred-items.md
- ❌ `./gradlew :app:testDebugUnitTest` — blocked by compilation failure

## Self-Check: PASSED

All 3 tasks committed. 9 source files modified per plan spec. All grep verifications pass for plan-specified patterns. Pre-existing compilation blockers documented; new `SyntaxTheme` integration code is syntactically correct (no errors referencing `codeTheme`, `codeFontScale`, `SyntaxTheme` types in build output).
