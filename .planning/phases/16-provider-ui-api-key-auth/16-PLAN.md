# Phase 16: Provider UI & API Key Auth - Plan

**Planned:** 2026-05-06
**Status:** Ready for execution

## Tasks

### T1: Add ProviderType.displayName extension property
- Add string resources (`provider_openai`, `provider_anthropic`, etc.) to `res/values/strings.xml`
- Create `ProviderType.displayName` extension property in `domain/model/ProviderType.kt`
- Map: OPENAI→"OpenAI", ANTHROPIC→"Anthropic", OLLAMA→"Ollama", LM_STUDIO→"LM Studio", CUSTOM→"Custom", LOCAL→"Local (llama.cpp)", LITE_RT_LM→"LiteRT-LM"
- Files: `res/values/strings.xml`, `domain/model/ProviderType.kt`

### T2: Expand EndpointForm dropdown to all 5 remote types
- Change `val providerTypes = listOf("LM_STUDIO")` to include OPENAI, ANTHROPIC, OLLAMA, LM_STUDIO, CUSTOM
- Display human-readable labels in dropdown. Use `ProviderType.valueOf(type).displayName` for display
- Files: `ui/endpoints/components/EndpointForm.kt`

### T3: Update EndpointCard label to displayName
- Change `endpoint.apiType.name` to human-readable display name
- Add `import com.warped.domain.model.ProviderType` and use `ProviderType.displayName`
- Files: `ui/endpoints/components/EndpointCard.kt`, `ui/endpoints/components/EndpointForm.kt`

### T4: Integrate ApiKeyStore in EndpointsViewModel save flow
- In `saveEndpoint()`, after saving endpoint, store API key via `apiKeyStore.storeKey(endpointId, apiKey.toCharArray())`
- Clear form API key from state after save
- Read `ApiKeyStore` via constructor injection
- Files: `ui/endpoints/EndpointsViewModel.kt`

### T5: Pass API keys to OpenAI and Custom providers
- Update `ProviderRouter.resolve()`: read API key from `ApiKeyStore` for OPENAI and CUSTOM
- Update `OpenAIProvider` to accept optional `apiKey: String?` constructor parameter
- Update `CustomProvider` to accept optional `apiKey: String?` constructor parameter
- Inject `Authorization: Bearer <token>` for OpenAI/Custom; `x-api-key` for Anthropic/LM Studio
- Files: `data/remote/provider/ProviderRouter.kt`, `data/remote/provider/OpenAIProvider.kt`, `data/remote/provider/CustomProvider.kt`

### T6: Masked API key placeholder in edit mode
- Add `hasSavedKey: Boolean = false` parameter to EndpointForm
- When hasSavedKey is true and apiKey is blank, show "••••••••" placeholder
- On user typing, replace placeholder with new input
- Files: `ui/endpoints/components/EndpointForm.kt`, `ui/endpoints/EndpointsViewModel.kt`

### T7: Hide Fetch button for non-LM-Studio types
- Show "Fetch" button only when `apiType == "LM_STUDIO"`
- For other provider types, "Fetch" button hidden; user types model ID manually
- Files: `ui/endpoints/components/EndpointForm.kt`

### T8: URL validation on save
- Add lightweight validation in EndpointsViewModel: non-blank name, non-blank URL, URL starts with "http"
- Show error in EndpointsUiState.error on validation failure
- Files: `ui/endpoints/EndpointsViewModel.kt`, `ui/endpoints/EndpointsUiState.kt`

### T9: Update EndpointsScreen/ViewModel for formApiType default
- Default `formApiType` already "OPENAI" — verify it works with the dropdown
- File: `ui/endpoints/EndpointsUiState.kt`

## Execution Order
1. T1 (ProviderType.displayName) — foundation
2. T3 (EndpointCard label) — depends on T1
3. T2 (EndpointForm dropdown expansion) — depends on T1
4. T7 (Hide Fetch for non-LM-Studio) — depends on T2
5. T6 (Masked API key) — depends on T2
6. T8 (URL validation) — independent
7. T4 (ApiKeyStore integration) — depends on T2
8. T5 (ProviderRouter API keys) — depends on T1
9. T9 (formApiType default) — verify
