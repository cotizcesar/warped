---
phase: 30
plan: 01
subsystem: ui
tags: [streaming, syntax-highlighting, codeblock, code-font-scale, huggingface, markdown]
requires: [SYNX-04, INTG-05, INTG-02]
provides: [codeFontScale-end-to-end-wiring, streaming-to-highlighted-transition, model-description-markdown]
affects: [CodeBlock.kt, MarkdownText.kt, MessageBubble.kt, ChatScreen.kt, ChatViewModel.kt, ChatUiState.kt, HuggingFaceDtos.kt, HuggingFaceScreen.kt]
tech-stack:
  added: []
  patterns: [Flow-collection-in-ViewModel-init, parameter-propagation-through-composable-chain]
key-files:
  created: []
  modified:
    - app/src/main/java/com/warped/ui/chat/components/CodeBlock.kt
    - app/src/main/java/com/warped/ui/chat/components/MarkdownText.kt
    - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
    - app/src/main/java/com/warped/ui/chat/ChatUiState.kt
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
    - app/src/main/java/com/warped/data/remote/dto/HuggingFaceDtos.kt
    - app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt
decisions:
  - "codeFontScale follows same DataStore→UiState→component propagation pattern as syntaxTheme (established in Phase 29-03)"
  - "HuggingFaceModel.description uses SyntaxTheme.MONOKAI default when no user preference available at call site (per CONTEXT.md)"
metrics:
  duration: 6m30s
  completed_date: "2026-05-15"
---

# Phase 30 Plan 01: Streaming Transition & Code Font Scale & Everywhere MarkdownText — Summary

**One-liner:** Fixed the streaming→highlighted transition in CodeBlock by adding `isStreaming` to the `LaunchedEffect` key tuple, wired the `codeFontScale` setting from DataStore through the full UI composable chain, and applied `MarkdownText` to HuggingFace model search result descriptions for syntax-highlighted code everywhere.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Fix streaming transition + add codeFontScale parameter to CodeBlock and MarkdownText | `46d381b` | `CodeBlock.kt`, `MarkdownText.kt` |
| 2 | Wire codeFontScale through ChatViewModel → ChatUiState → ChatScreen → MessageBubble | `7527280` | `ChatUiState.kt`, `ChatViewModel.kt`, `MessageBubble.kt`, `ChatScreen.kt` |
| 3 | Apply MarkdownText to HuggingFace model description rendering | `a6b9832` | `HuggingFaceDtos.kt`, `HuggingFaceScreen.kt` |

## What Was Built

### Task 1: Streaming Transition Fix + Code Font Scale Parameter

**Streaming transition (SYNX-04, INTG-05):** Changed `LaunchedEffect(code, language)` to `LaunchedEffect(code, language, isStreaming)` in `CodeBlock.kt`. When `isStreaming` transitions from `true` to `false` within the same composable instance (the fenced code block closes), the effect now re-launches and runs syntax highlighting. The existing `if (isStreaming) { return@LaunchedEffect }` guard continues to exit early during active streaming.

**Code font scale parameter:** Removed the hardcoded `val codeFontScale: Float = 1.0f` in `CodeBlock.kt` and replaced it with a function parameter `codeFontScale: Float = 1.0f`. Added the same parameter to `MarkdownText.kt` and propagated it to the `CodeBlock` call site.

### Task 2: End-to-End codeFontScale Wiring

Wired `codeFontScale` through the established pattern (mirroring `syntaxTheme` flow from Phase 29-03):

```
AdvancedPreferences.codeFontScale (DataStore)
  → ChatViewModel (collect in init)
    → ChatUiState.codeFontScale
      → ChatScreen (pass to MessageBubble)
        → MessageBubble (pass to MarkdownText)
          → MarkdownText (pass to CodeBlock)
            → CodeBlock (multiplied by 16sp base)
```

Files touched: `ChatUiState.kt` (+1 field), `ChatViewModel.kt` (+1 collection), `MessageBubble.kt` (+1 param, 2 call site additions), `ChatScreen.kt` (2 call site additions).

### Task 3: HuggingFace Model Description with MarkdownText (INTG-02)

**DTO:** Added `val description: String = ""` to `HuggingFaceModel` data class. The HuggingFace Hub API `/api/models` endpoint returns this field but it was previously discarded by `ignoreUnknownKeys = true`.

**UI:** Added `MarkdownText` rendering inside `ModelSearchResultCard` when `model.description` is non-blank. Uses `SyntaxTheme.MONOKAI` (per CONTEXT.md default for call sites without user preference) and `maxLines = 4` to keep cards compact.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Ambiguous edit pattern leaked MarkdownText block into ModelDetailScreen**
- **Found during:** Task 3 verification (compilation)
- **Issue:** The `}` `}` `}` edit pattern matched the first occurrence in the file (`ModelDetailScreen`) instead of the target (`ModelSearchResultCard`). This applied `model.description` rendering where `HuggingFaceModelDetail` (which has no `description` field) is used, causing 2 compile errors at lines 428, 431.
- **Fix:** Removed the erroneously duplicated MarkdownText block from `ModelDetailScreen` and restored proper indentation. Re-verified with a more specific context anchor (`FormatBadge` function) for the correct location in `ModelSearchResultCard`.
- **Files modified:** `HuggingFaceScreen.kt`
- **Commit:** `1d895d3`

## Verification

- `grep` checks passed for all acceptance criteria (LaunchedEffect key, codeFontScale parameter chain, description field, MarkdownText import/usage)
- `./gradlew :app:compileDebugKotlin` — **BUILD SUCCESSFUL** (0 errors, only pre-existing warnings unrelated to this plan)

## Known Stubs

None. All changes wire real data flows — no placeholder values, no hardcoded defaults to resolve later.

## Threat Flags

None. All changes are UI composable wiring and DTO field additions. No new network endpoints, auth paths, file access patterns, or trust-boundary schema changes.

## Self-Check: PASSED

**Created files exist:**
- *(none created — all edits to existing files)*

**Commits exist:**
- `46d381b` — FOUND in git log
- `7527280` — FOUND in git log
- `a6b9832` — FOUND in git log
- `1d895d3` — FOUND in git log
