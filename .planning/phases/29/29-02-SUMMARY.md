---
phase: 29-ui-components-markdown-refactoring
plan: 02
subsystem: ui-code-block
tags: [compose, syntax-highlighting, code-block, hilt, expand-collapse]
dependency-graph:
  requires: ["29-01", "28-01", "28-02"]
  provides: ["CodeBlock composable", "CodeHeaderBar", "LineNumberGutter", "expand/collapse"]
  affects: ["MarkdownText.kt (future integration)"]
tech-stack:
  added: []
  patterns:
    - "Hilt EntryPoint for composable-level DI (SyntaxHighlightingEntryPoint)"
    - "animateColorAsState per TokenType for syntax color transitions"
    - "Crossfade for copy button icon swap with 2s timeout"
    - "animateContentSize for expand/collapse with 200-line threshold"
    - "Popup for warning tooltip"
key-files:
  created:
    - app/src/main/java/com/warped/ui/chat/components/CodeBlock.kt
  modified: []
decisions:
  - "animateColorAsState applied per TokenType (13 calls) at composable scope rather than per-token in builder lambda"
  - "FastOutLinearSlowInEasing not available in Compose → FastOutSlowInEasing used for Crossfade copy icon transition"
  - "Popup (androidx.compose.ui.window.Popup) used for syntax warning tooltip instead of Material 3 TooltipBox for API simplicity"
  - "Header bar darkening via 0.92f RGB multiplier applied uniformly across all three channels"
metrics:
  duration: 15min
  completed_date: "2026-05-15T01:13:51Z"
---

# Phase 29 Plan 02: CodeBlock Composable with Syntax Highlighting

**One-liner:** Production-quality code rendering composable with syntax-colored tokens, language header bar, copy-to-clipboard, line numbers, expand/collapse, and syntax issue warning indicator.

## Summary

Created `CodeBlock.kt` — a 545-line composable file containing `CodeBlock`, `CodeHeaderBar`, and `LineNumberGutter` composables that render syntax-highlighted code blocks in the Warped chat UI. Integrates Phase 28's `SyntaxHighlighter` via Hilt `EntryPoint` for composable-level DI, with animated color transitions, a copy-to-clipboard button with confirmation state, right-aligned line numbers in a 32dp gutter, and expand/collapse for blocks exceeding 200 lines.

### Key deliverables:
- **CodeBlock composable**: Accepts `language`, `code`, `syntaxTheme`, `isStreaming` parameters. Launches async highlighting on `Dispatchers.Default`, renders syntax-colored tokens via `animateColorAsState` (13 token types, 400ms `FastOutSlowInEasing`), with flat monospace fallback during loading/streaming/failure.
- **CodeHeaderBar**: 28dp height, bgCode darkened 8%, shows capitalized language label (12sp Monospace Medium), warning icon (amber #E6A817, 16dp) for syntax issues with Popup tooltip, and copy button (ContentCopy→Check Crossfade, 200ms, "Copied!" text for 2s timeout via `ClipboardManager`).
- **LineNumberGutter**: 32dp column, right-aligned line numbers (12sp Monospace, `onSurface` alpha 0.4f), 1dp vertical divider (`Color.Gray` alpha 0.2f).
- **Expand/collapse**: ≥200 lines triggers 150dp max-height with `animateContentSize` (300ms), 40dp gradient overlay with "Show all N lines" tap target.
- **Warning indicator**: `detectSyntaxIssues()` detects unclosed string literals and bracket mismatches; amber warning icon with dark tooltip popup.
- **Safety**: 500KB code cap skips highlighting; `try/catch` in `LaunchedEffect` with `Timber.w` on failure; empty code shows "(empty)" placeholder with disabled copy button.

## Plan Completion

| Task | Name | Commit | Status |
|------|------|--------|--------|
| 1 | Create CodeBlock composable with syntax highlighting and Hilt DI | `f04d3c9` | ✅ Complete |
| 2 | Build CodeHeaderBar, LineNumberGutter, and expand/collapse sub-composables | `c03db7e` | ✅ Complete |
| 3 | Add warning indicator, loading/empty/error states, and edge cases | `c3b3123` | ✅ Complete |

**Plan status:** 3/3 tasks complete ✅

## Verification

### Automated checks (all pass):
- `fun CodeBlock` — ✅ (1 match)
- `fun CodeHeaderBar` — ✅ (1 match)
- `fun LineNumberGutter` — ✅ (1 match)
- `EntryPoint` (Hilt DI) — ✅ (8 matches)
- `animateColorAsState` — ✅ (13 matches)
- `animateContentSize` — ✅ (2 matches)
- `Crossfade` — ✅ (3 matches)
- `detectSyntaxIssues` — ✅ (2 matches)
- `ContentCopy` — ✅ (2 matches)
- `Copied!` — ✅ (1 match)
- `Show all.*lines` — ✅ (1 match)
- `28.dp` — ✅ (1 match)
- `32.dp` — ✅ (2 matches)
- `150.dp` — ✅ (1 match)
- `40.dp` — ✅ (1 match)
- `0.92f` — ✅ (3 matches)
- `0xFF4CAF50` — ✅ (1 match)
- `0xFFE6A817` — ✅ (1 match)
- `500_000` — ✅ (1 match)
- `isBlank` — ✅ (4 matches, ≥2 required)
- `Timber.w` — ✅ (2 matches)
- `try/catch` — ✅ (1 match)
- `plaintext` — ✅ (1 match)
- File line count: 545 lines (≥250 required) ✅

### Build:
- `./gradlew :app:compileDebugKotlin` — CodeBlock.kt compiles clean (zero errors). Pre-existing build errors in `MessageBubble.kt` (type mismatch `CodeTheme`→`SyntaxTheme`), `ModelDownloadWorker.kt`, `LiteRTLmProvider.kt`, and `ChatScreen.kt` are out of scope for this plan.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed `Type mismatch: Any vs Int` in color target resolution**
- **Found during:** Task 1
- **Issue:** `variant[TokenType.KEYWORD]?.argb ?: plainColor` mixed `Int` (SyntaxColor.argb) and `Color` (plainColor) types in `?:` operator.
- **Fix:** Extracted `plainArgb: Int` from the PLAIN token's argb, then used `plainArgb` consistently as the `Int` fallback for all other token types' color resolution.
- **Files modified:** `CodeBlock.kt` (token color target lines)
- **Commit:** `f04d3c9`

**2. [Rule 1 - Bug] Fixed `animateColorAsState` in non-composable context**
- **Found during:** Task 1
- **Issue:** Initial approach had `animateColorAsState` inside `buildAnnotatedString {}` lambda which is not `@Composable`.
- **Fix:** Moved all 13 `animateColorAsState` calls to top-level composable scope, one per `TokenType`, then built the `tokenColorMap` from animated values and passed it to `remember(tokens, tokenColorMap, ...)`.
- **Files modified:** `CodeBlock.kt` (composable body restructured)
- **Commit:** `f04d3c9`

**3. [Rule 1 - Bug] Fixed `try/catch` around composable `EntryPoints.get()`**
- **Found during:** Task 1
- **Issue:** `EntryPoints.get()` is a composable function and cannot be called inside `LaunchedEffect` (non-composable context) or wrapped in `try/catch`.
- **Fix:** Moved `EntryPoints.get()` and `syntaxHighlighter()` resolution to composable scope using `remember`, storing the resolved `SyntaxHighlighter` reference. The `LaunchedEffect` then calls `highlighter.highlight()` inside try/catch.
- **Files modified:** `CodeBlock.kt` (Hilt resolution and LaunchedEffect)
- **Commit:** `f04d3c9`

**4. [Rule 1 - Bug] Replaced non-existent `FastOutLinearSlowInEasing`**
- **Found during:** Task 2
- **Issue:** `FastOutLinearSlowInEasing` is not a valid Compose animation easing constant. Caused `Unresolved reference` compilation error.
- **Fix:** Replaced with `FastOutSlowInEasing` for the copy button Crossfade animation, which provides a visually similar deceleration curve.
- **Files modified:** `CodeBlock.kt` (import and Crossfade animationSpec)
- **Commit:** `c03db7e`

## Pre-existing Issues (Out of Scope)

- `MessageBubble.kt:109,173` — Type mismatch: `CodeTheme` parameter passed where `SyntaxTheme` expected. This is a known issue from Plan 29-01 (MarkdownText refactoring). The caller update (MessageBubble parameter type change from `CodeTheme` to `SyntaxTheme`) is part of Plan 29-03.
- `ModelDownloadWorker.kt`, `LiteRTLmProvider.kt`, `ChatScreen.kt` — Multiple pre-existing unresolved references from parallel feature work (audio recording, gated model downloads). Not related to this plan.

## Known Stubs

| File | Line(s) | Stub | Reason |
|------|---------|------|--------|
| `CodeBlock.kt` | ~92 | `val codeFontScale: Float = 1.0f` | Will be wired from `AdvancedPreferences.codeFontScale` in Plan 29-03 (Settings integration). Currently hardcoded to 1.0 (default scale). |
| `CodeBlock.kt` | ~112 | `if (isStreaming) { return@LaunchedEffect }` | Streaming→highlighted transition deferred to Phase 30. During streaming, flat monospace is shown. Phase 30 adds the fade-in transition when streaming completes. |

## Threat Flags

None. No new network endpoints, auth paths, file access patterns, or schema changes at trust boundaries introduced. The `ClipboardManager` integration uses the standard Android clipboard API (no data exfiltration concern — user-initiated copy action).

## Self-Check: PASSED

- [x] `app/src/main/java/com/warped/ui/chat/components/CodeBlock.kt` exists (545 lines)
- [x] Commit `f04d3c9` exists
- [x] Commit `c03db7e` exists
- [x] Commit `c3b3123` exists
- [x] All 3 commits verified in git log
- [x] No file deletions in any commit
- [x] No generated files left untracked
