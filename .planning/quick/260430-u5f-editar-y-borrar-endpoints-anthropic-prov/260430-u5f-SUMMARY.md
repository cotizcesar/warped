---
phase: quick-260430-u5f
plan: 01
subsystem: remote-providers
tags: [anthropic, endpoints, crud, api-key, sse]
requires: []
provides: [AnthropicProvider, EndpointEditDelete, ApiKeyFix]
affects: [ProviderRouter, ModelsScreen, EndpointsViewModel, EndpointRepository]
tech-stack:
  added: [Anthropic Messages API, Anthropic SSE streaming, EndpointForm inline edit]
  patterns: [Provider pattern with self-contained auth, SSE line-based parser for Anthropic format]
key-files:
  created:
    - app/src/main/java/com/warped/data/remote/api/AnthropicApi.kt
    - app/src/main/java/com/warped/data/remote/dto/AnthropicDtos.kt
    - app/src/main/java/com/warped/data/remote/provider/AnthropicProvider.kt
  modified:
    - app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt
    - app/src/main/java/com/warped/ui/models/ModelsScreen.kt
    - app/src/main/java/com/warped/ui/models/ModelsViewModel.kt
    - app/src/main/java/com/warped/ui/models/ModelsUiState.kt
    - app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt
    - app/src/main/java/com/warped/domain/repository/EndpointRepository.kt
    - app/src/main/java/com/warped/data/repository/EndpointRepositoryImpl.kt
metrics:
  duration: 416s
  completed: 2026-05-01T02:55:53Z
---

# Phase quick-260430-u5f Plan 01: Anthropic Provider + Endpoint CRUD Summary

**One-liner:** Full Anthropic Messages API support with custom SSE streaming parser, endpoint edit/delete from Models & Endpoints screen, and API key storage bug fix using row ID from repository.

## Completed Tasks

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create AnthropicApi + DTOs | `e5b0e60` | AnthropicApi.kt, AnthropicDtos.kt |
| 2 | Create AnthropicProvider with SSE streaming | `9bf6af5` | AnthropicProvider.kt |
| 3 | Update ProviderRouter for Anthropic | `eabba6c` | ProviderRouter.kt |
| 4 | Fix endpoint edit/delete + API key bug | `25b4e33` | ModelsScreen.kt, ModelsViewModel.kt, ModelsUiState.kt, EndpointsViewModel.kt, EndpointRepository.kt, EndpointRepositoryImpl.kt |

## What Was Built

### 1. Anthropic Messages API Integration (Tasks 1-3)
- **AnthropicApi**: Retrofit interface for `POST v1/messages` with `Content-Type: application/json` header
- **AnthropicDtos**: Serializable DTOs for request (`AnthropicChatRequest`, `AnthropicMessage`) and SSE response (`AnthropicSseEvent`, `AnthropicDelta`, `AnthropicSseMessage`)
- **AnthropicProvider**: Full `LlmProvider` implementation with:
  - `x-api-key` header and `anthropic-version: 2023-06-01` header via OkHttp interceptor
  - System message extraction as top-level field (Anthropic convention, not role-based)
  - Custom SSE line parser handling `event:/data:` pairs for `content_block_delta`, `message_delta`, `message_stop`, and `error` event types
  - `testConnection()` sending a minimal non-streaming request to verify endpoint reachability and auth
- **ProviderRouter**: Routes `ProviderType.ANTHROPIC` to `AnthropicProvider` with API key retrieved from `ApiKeyStore`

### 2. Endpoint CRUD from Models & Endpoints Screen (Task 4)
- **DeployedEndpointCard**: Added Edit (pencil) and Delete (trash) `IconButton`s alongside "Use in chat"
- Delete confirmation `AlertDialog` before removing endpoints
- **ModelsViewModel**: New methods: `editEndpoint()`, `deleteEndpoint()`, `saveEndpointEdit()`, `cancelEndpointEdit()`
- **ModelsUiState**: New fields `isEditingEndpoint` and `editingEndpoint` for edit form state
- **EndpointForm reuse**: Same form composable used for both new endpoint creation and editing existing endpoints

### 3. API Key Storage Bug Fix (Task 4)
- **Root cause**: `EndpointRepository.saveEndpoint()` returned `Unit`, so callers couldn't know the real row ID assigned by Room upsert. New endpoints got `id=0`, causing API keys to be stored with alias `api_key_0`.
- **Fix**: Changed `saveEndpoint()` signature to return `Long` (the upserted row ID).
- **EndpointRepositoryImpl**: Returns `endpointDao.upsert()` result directly.
- **EndpointsViewModel**: Uses returned ID for `apiKeyStore.storeKey(savedId, ...)`.
- **ModelsViewModel**: Both `saveEndpoint()` (new) and `saveEndpointEdit()` (edit) now store API keys via the returned ID.
- Also injected `ApiKeyStore` into `ModelsViewModel` (was missing previously).

## Deviations from Plan

None — plan executed exactly as written.

## Auth Gates

None — no authentication required for this task.

## Decisions Made

1. **parseAnthropicSse as companion method**: Moved SSE parser to companion object since it doesn't access instance state (both `body` and `json` are passed as parameters), keeping the class surface cleaner.
2. **ModelsViewModel API key storage**: Extended beyond plan scope — also fixed `saveEndpoint()` (new endpoint creation) to store API keys, not just `saveEndpointEdit()`. Previously, API keys entered during new endpoint creation were silently lost.
3. **Delete confirmation pattern**: Mirror `ModelCard`'s confirmation dialog pattern for endpoint deletion consistency.

## Known Stubs

None — all functionality is fully wired with real data stores and providers.

## Self-Check

PASSED:
- All 4 task commits verified in git log
- All created files exist on disk
- All modified files verified with grep patterns
- Full project compiles without errors (`BUILD SUCCESSFUL`)
