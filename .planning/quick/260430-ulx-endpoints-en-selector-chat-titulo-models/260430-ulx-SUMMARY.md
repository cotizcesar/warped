---
phase: quick-260430-ulx
plan: 01
type: execute
subsystem: ui/chat + ui/models
tags: [model-selector, endpoints, chat-ui, models-screen, bottom-sheet]
tech-stack:
  added: []
  patterns: [ModalBottomSheet, statusBarsPadding, ExposedDropdownMenu]
key-files:
  modified:
    - app/src/main/java/com/warped/ui/chat/ChatUiState.kt
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
    - app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt
    - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
    - app/src/main/java/com/warped/ui/models/ModelsScreen.kt
decisions:
  - "Psychology (brain) icon for model picker in ChatInputBar — aligns with AI/LLM metaphor"
  - "statusBarsPadding + 8dp vertical padding instead of TopAppBar for Models & Endpoints title — ~64dp saved"
  - "Endpoints shown in ModelSelector with divider separating local models — clear visual distinction"
metrics:
  duration_seconds: 339
  completed_date: "2026-04-30T22:09:51-05:00"
requires: []
provides: [unified-model-picker, compact-models-title, bottom-sheet-selector]
affects: [chat-screen, models-screen, model-selector]
---

# Phase quick-260430-ulx Plan 01: Endpoint Selector + Chat Title + Models Compact Title — Summary

**One-liner:** Network endpoints now appear alongside local models in the chat model picker via a bottom sheet triggered by a Psychology icon in the input bar; Models & Endpoints screen uses a compact title without TopAppBar.

## Completed Tasks

| # | Name | Commit | Key Files |
|---|---|---|---|
| 1 | Add network endpoints to ChatUiState + ChatViewModel + ModelSelector | `30c79f0` | ChatUiState.kt, ChatViewModel.kt, ModelSelector.kt, ChatScreen.kt |
| 2 | Move model selector to icon at bottom + remove top selector | `60e674e` | ChatScreen.kt, ChatInputBar.kt |
| 3 | Reduce top space in Models & Endpoints title | `070c1b5` | ModelsScreen.kt |

## What Was Built

### Task 1: Endpoint Integration in Model Picker

- **ChatUiState** — Added `endpoints: List<Endpoint> = emptyList()` field with `Endpoint` import
- **ChatViewModel** — Added `endpointRepository.observeEndpoints()` collector in `init` block, updating UI state reactively
- **ModelSelector** — Extended signature with `endpoints: List<Endpoint>` parameter. Updated dropdown to show local models and network endpoints with a `HorizontalDivider` separator. Endpoint items display as `"name · PROVIDER_TYPE"`. Label logic updated to find selected item across both local models and endpoints.
- **ChatScreen** — Passes `uiState.endpoints` to `ModelSelector`

### Task 2: Bottom Sheet Model Picker

- **ChatScreen** — Removed the `Box` with inline `ModelSelector` from the top of chat. Added `showModelPicker` state and `ModalBottomSheet` containing the `ModelSelector`. Computes `selectedModelName` for display in the input bar.
- **ChatInputBar** — Added `selectedModelName: String?` and `onModelPickerClick: () -> Unit` parameters. Added `IconButton` with `Icons.Filled.Psychology` (brain icon) on the left of the text field.

### Task 3: Compact Models & Endpoints Title

- **ModelsScreen** — Removed `topBar = { TopAppBar(...) }` from `Scaffold`. Added `Modifier.statusBarsPadding()` to the content `Column`. Added a `Text("Models & Endpoints", style = titleLarge)` with `padding(horizontal = 16.dp, vertical = 8.dp)`. This saves approximately 64dp of vertical space (TopAppBar height).

## Deviations from Plan

None — plan executed exactly as written.

## Verification

All three tasks pass compilation (`./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL). Grep-based verification confirms:
- `observeEndpoints`, `HorizontalDivider`, `endpoints` present in ModelSelector.kt and ChatViewModel.kt
- `showModelPicker`, `ModalBottomSheet`, `onModelPickerClick`, `Psychology` present in ChatScreen.kt and ChatInputBar.kt
- `statusBarsPadding` present and `TopAppBar` absent in ModelsScreen.kt

## Known Stubs

None. All data flows are wired end-to-end (endpoints observed from repository, rendered in selector, wired to callbacks).

## Self-Check

PASSED — all 6 files verified present on disk, all 3 commits confirmed in git history.
