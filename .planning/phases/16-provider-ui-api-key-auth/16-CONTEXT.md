# Phase 16: Provider UI & API Key Auth - Context

**Gathered:** 2026-05-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Expose all provider types in UI with readable names and encrypted API key management — the foundation that every other phase builds on.

This phase delivers:

1. `ProviderType.displayName` extension property mapping enum → human-readable label via `stringResource()`
2. EndpointForm dropdown expanded from `listOf("LM_STUDIO")` to all 5 remote `ProviderType` values
3. EndpointCard chip updated from `apiType.name` to `apiType.displayName`
4. API key integration in save flow: encrypt via ApiKeyStore on save, pass to providers on resolve
5. OpenAI and Custom providers receive API key through constructor (copy AnthropicProvider pattern)
6. Masked API key placeholder in edit mode (no decryption to UI)
7. Lightweight URL validation on save
</domain>

<decisions>
## Implementation Decisions

### Provider Type UI Display
- `ProviderType.displayName` extension property — returns `stringResource()` from resource strings
- All 5 remote types in EndpointForm dropdown (OPENAI, ANTHROPIC, OLLAMA, LM_STUDIO, CUSTOM). LOCAL and LITE_RT_LM excluded — they are not remote endpoints
- PascalCase display format: "OpenAI", "Anthropic", "LM Studio", "Ollama", "Custom"
- Resource strings (strings.xml) for i18n — standard Android pattern

### API Key Flow
- Encryption happens in `EndpointRepositoryImpl.saveEndpoint()` — data-layer concern
- Edit mode shows `••••••••` placeholder when key exists — never decrypt to UI
- OpenAI/Custom providers use `Authorization: Bearer <token>` header. Anthropic/LM Studio use `x-api-key`. Copy AnthropicProvider constructor pattern: pass apiKey to provider, inject header internally
- API key is optional — local-network providers (Ollama, LM Studio) often don't need auth

### Form Specialization & Validation
- Minimal specialization for Phase 16: hide "Fetch" button for non-LM-Studio types (only LM Studio model fetch is currently wired). Phase 17-19 will add per-provider-specific fields
- Custom provider uses standard OpenAI-compatible paths (`/v1/chat/completions`, `/v1/models`). Custom path editing deferred
- URL validation: non-blank + `startsWith("http")` — no strict regex. Local network addresses are valid
- `testConnection()` validates API key when present — uses existing LlmProvider interface

### the agent's Discretion
- All implementation choices are at the agent's discretion within the bounds above
- Use ROADMAP phase goal, success criteria, and codebase conventions to guide decisions
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ProviderType` enum (domain/model/ProviderType.kt) — all 7 values already defined
- `ApiKeyStore` (data/local/security/ApiKeyStore.kt) — storeKey/getKey/deleteKey ready
- `ProviderRouter` (data/remote/provider/ProviderRouter.kt) — resolve() already handles all types
- `EndpointRepositoryImpl` — already injected with ApiKeyStore
- `AnthropicProvider` — existing pattern for constructor-injected API key with internal header injection
- `EndpointForm` composable — existing dropdown structure, just needs providerTypes list expanded

### Established Patterns
- Extension property mappers: EntityMappers.kt, LocalModelMappers.kt, PresetMappers.kt
- ViewModel: `@HiltViewModel` + `StateFlow<UiState>` + `_uiState.update { it.copy(...) }`
- Repository: `suspend` functions, Flow observation, Result<T> for fallible ops
- Hilt: `@Provides` in object modules, `@Binds` in abstract modules, `@Singleton` scope

### Integration Points
- `EndpointForm.kt:34` — `val providerTypes = listOf("LM_STUDIO")` needs expansion
- `EndpointCard.kt:46` — `endpoint.apiType.name` needs `endpoint.apiType.displayName`
- `EndpointsUiState.kt:13` — `formApiType: String = "OPENAI"` already defaults to OpenAI
- `ProviderRouter.kt:21-25` — OpenAIProvider needs apiKey passed (currently not)
- `ProviderRouter.kt:43-46` — CustomProvider needs apiKey passed (currently not)
- `EndpointRepositoryImpl` — saveEndpoint needs ApiKeyStore.storeKey() integration
</code_context>

<specifics>
## Specific Ideas

- ROADMAP success criteria specify all 5 remote provider types with human-readable labels
- Requirements PROV-01 through PROV-04 and AUTH-01 through AUTH-03 map to this phase
- "LM Studio", "OpenAI", "Anthropic", "Ollama", "Custom" as display names (PascalCase, no underscores)
- LM Studio model fetch ("Fetch" button) should only appear for LM Studio provider type
</specifics>

<deferred>
## Deferred Ideas

- Per-provider custom path fields (e.g., `/v1/responses`, `/v1/embeddings` paths for Custom) → Phase 17-19
- LM Studio-specific load/unload/download UI fields → Phase 19
- Ollama-specific model management fields → Phase 18
- Full i18n resource infrastructure beyond provider names → future milestone
- Custom provider editable path fields → future if needed
</deferred>
