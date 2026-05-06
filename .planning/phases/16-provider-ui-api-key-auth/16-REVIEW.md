---
phase: "16"
status: clean
severity: none
---

# Phase 16 Code Review

**Reviewed:** 2026-05-06
**Scope:** Provider UI & API Key Auth

## Findings

### No Issues Found

All changes were reviewed manually (code review skills not installed). Verified:

1. **ProviderType.displayNameRes()** — extension function returns `@StringRes`, properly mapped for all 7 enum values
2. **strings.xml** — 7 new resource strings added, matching PascalCase format from CONTEXT.md
3. **EndpointCard.kt** — changed from `apiType.name` to `stringResource(apiType.displayNameRes())`, imports added
4. **EndpointForm.kt** — dropdown expanded to 5 remote types, Fetch button gated on LM Studio, masked key placeholder with `hasSavedKey` param, `selectedType` computed safely without try-catch
5. **EndpointsViewModel.kt** — `showAddForm()` defaults to OPENAI, `showEditForm()` checks for saved key via `ApiKeyStore.getKey()`
6. **EndpointsUiState.kt** — `hasSavedApiKey: Boolean = false` field added
7. **ModelsUiState.kt** — `hasSavedApiKey` field added, `formApiType` default changed to OPENAI
8. **ModelsViewModel.kt** — `showEndpointForm()` defaults to OPENAI, `editEndpoint()` checks for saved key
9. **ModelsScreen.kt** — passes `hasSavedKey` to both `EndpointForm` calls
10. **ProviderRouter.kt** — reads API key once per resolve, passes to OPENAI, ANTHROPIC, LM_STUDIO, CUSTOM
11. **OpenAIProvider.kt** — added `apiKey` constructor param, injects `Bearer` header via OkHttp interceptor
12. **CustomProvider.kt** — added `apiKey` constructor param, injects `Bearer` header via OkHttp interceptor
13. **LMStudioProvider.kt** — added `apiKey` constructor param, injects `x-api-key` header via OkHttp interceptor

### Security
- API keys zero-filled via `key.fill('0')` after use in ProviderRouter
- No plaintext keys in UI state (showEditForm sets `formApiKey = ""`)
- Keys stored via EncryptedSharedPreferences (Android Keystore)
- Masked placeholder (••••••••) shown instead of decrypted value
