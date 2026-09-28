---
phase: quick-260430-vsl
plan: 01
type: execute
subsystem: data/remote, ui/endpoints, ui/models
tags: [lm-studio, api, provider, endpoint-form, simplification]
dependency_graph:
  requires: []
  provides: [native-lm-studio-api, simplified-endpoint-forms]
  affects: [LMStudioProvider, EndpointForm, EndpointsViewModel, ModelsViewModel]
tech-stack:
  added: [Retrofit interface LmStudioApi]
  patterns: [native REST API (/api/v1/chat, /api/v1/models), SSE streaming via asSseFlow]
key-files:
  created:
    - app/src/main/java/com/warped/data/remote/api/LmStudioApi.kt
  modified:
    - app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt
    - app/src/main/java/com/warped/ui/endpoints/components/EndpointForm.kt
    - app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt
    - app/src/main/java/com/warped/ui/models/ModelsUiState.kt
    - app/src/main/java/com/warped/ui/models/ModelsViewModel.kt
decisions:
  - "LM Studio native v1 REST API used instead of OpenAI-compatible /v1/chat/completions endpoint"
  - "OkHttpClient with 15s connect / 120s read timeouts added to LMStudioProvider"
  - "maxTokens defaults to -1 (LM Studio convention for unlimited) instead of 0 (OpenAI convention)"
  - "Endpoint form providerTypes reduced to only LM_STUDIO for simplified UX"
metrics:
  duration: TBD
  completed_date: 2026-04-30
---

# Quick Task 260430-vsl: LM Studio Native v1 API & Endpoint Form Simplification

## One-Liner
Rewired LMStudioProvider to call LM Studio's native `/api/v1/chat` and `/api/v1/models` REST endpoints, and simplified the endpoint configuration UI to offer only LM_STUDIO as a provider type.

## Tasks Executed

### Task 1: Create native LmStudioApi and rewrite LMStudioProvider
- **Commit:** `2b172ef`
- **Files:** `LmStudioApi.kt` (new), `LMStudioProvider.kt` (rewritten)
- **What changed:**
  - New `LmStudioApi` Retrofit interface with `POST api/v1/chat` and `GET api/v1/models`
  - `LMStudioProvider` now creates `LmStudioApi` instead of `OpenAiApi`
  - Added explicit `OkHttpClient` with 15s connect timeout and 120s read timeout (was using Retrofit defaults)
  - Better error handling: `errorBody()?.string()` included in error tokens
  - `maxTokens` defaults to `-1` for LM Studio (unlimited), via `takeIf { it > 0 } ?: -1`
  - Same SSE parsing via `asSseFlow(json)` since LM Studio native v1 returns OpenAI-format chunks
  - Reuses `OpenAiChatRequest` and `OpenAiModelListResponse` DTOs (same JSON structure)

### Task 2: Simplify UI — only LM_STUDIO in endpoint forms
- **Commit:** `f697e5a`
- **Files:** `EndpointForm.kt`, `EndpointsViewModel.kt`, `ModelsUiState.kt`, `ModelsViewModel.kt`
- **What changed:**
  - `EndpointForm.kt`: `providerTypes` reduced from `["OPENAI", "ANTHROPIC", "OLLAMA", "LM_STUDIO", "CUSTOM"]` to `["LM_STUDIO"]`
  - `EndpointsViewModel.kt`: `showAddForm()` default `formApiType` changed from `"OPENAI"` to `"LM_STUDIO"`
  - `ModelsUiState.kt`: default `formApiType` changed from `"OPENAI"` to `"LM_STUDIO"`
  - `ModelsViewModel.kt`: `showEndpointForm()` default `formApiType` changed from `"OPENAI"` to `"LM_STUDIO"`

## Verification

| Criteria | Status | Notes |
|----------|--------|-------|
| LMStudioProvider uses LmStudioApi with /api/v1/chat and /api/v1/models | Pass | grep confirms both endpoints |
| Endpoint form only offers LM_STUDIO | Pass | No OPENAI/ANTHROPIC/OLLAMA/CUSTOM references remain |
| All form defaults set to LM_STUDIO | Pass | 3 defaults updated |
| Build compiles | Pass | `:app:compileDebugKotlin` BUILD SUCCESSFUL |

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — all data flows are fully wired.

## Threat Flags

None — no new security surface introduced beyond existing provider pattern.

## Self-Check: PASSED

- [x] LmStudioApi.kt exists
- [x] LMStudioProvider.kt rewritten with LmStudioApi
- [x] EndpointForm.kt providerTypes = ["LM_STUDIO"]
- [x] All formApiType defaults = "LM_STUDIO"
- [x] Commits 2b172ef and f697e5a exist in git log
- [x] Build passes
