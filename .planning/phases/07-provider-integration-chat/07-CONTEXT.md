# Phase 7: Provider Integration & Chat - Context

**Gathered:** 2026-05-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Wire LiteRT-LM into the existing chat architecture — users can select a `.litertlm` model and chat with streaming responses, with input sanitization, parameter mapping, and graceful error recovery. This phase creates `LiteRTLmProvider`, `InputSanitizer`, integrates with `ProviderRouter`, and implements defensive error recovery for "Engine not alive" states.

**Depends on:** Phase 6 (EngineManager, LiteRTLmEngine, BackendDetector)
**Requirements:** LITE-05, LITE-06, LITE-07, POL-04
</domain>

<decisions>
## Implementation Decisions

### Provider Architecture & Routing
- New `LiteRTLmProvider` class implementing `LlmProvider` interface, injected as `@Singleton` via Hilt
- Works alongside `LocalLlmProvider` (GGUF/llama.cpp) — not replacing it. Each handles its own engine
- New `LITE_RT_LM` value added to `ProviderType` enum
- Injected via `Lazy<LiteRTLmProvider>` in `ProviderRouter` (lazy init, same pattern as `localLlmProvider`)
- `ProviderRouter.resolve()` branches on `ProviderType.LITE_RT_LM` to return `LiteRTLmProvider`

### Input Sanitization
- Separate `InputSanitizer` utility class in `data/local/inference/` — reusable, single responsibility
- Surgical targeting: LaTeX delimiters (`$$`, `\(`, `\)`, `\[`, `\]`), Unicode math blocks (U+2200–U+22FF, U+27C0–U+27EF), control characters (< U+0020 except \n/\t), surrogate pairs, zero-width characters
- Normal text passes through untouched — conservative approach for v0.11.0-rc1
- All messages sanitized (user, system, history) — any message can trigger crashes

### Parameter Mapping
- Direct 1:1 mapping from `GenerationParameters` to `SamplerConfig`: temperature→temperature, topK→topK, topP→topP, seed→randomSeed (only when seed != -1)
- `maxTokens` mapped via `ConversationConfig.maxOutputTokens`
- Parameters unsupported by LiteRT-LM (`repeatPenalty`, `contextSize`, `threads`) silently skipped — UI grey-out handled in Phase 10
- Range validation: temperature [0.0, 2.0], topK [1, 100], topP [0.0, 1.0]. Clamp out-of-range with Timber warning
- Mapping done inline in `LiteRTLmProvider.chat()` — simple 4-field conversion doesn't warrant a separate mapper class

### Error Recovery
- Detect "Engine not alive" by catching `IllegalStateException` or LiteRT-LM specific exceptions from `sendMessageAsync`/`createConversation`
- Auto-recovery with toast notification ("Engine recovered, retrying...")
- Reinitialize with same model path from `EngineManager.getActiveEngine()` — model is still on disk
- 2 retries max (3 total attempts). After exhaustion: "Engine failed to recover. Please reload the model manually."
- Check `engine.isInitialized()` before each chat invocation as lightweight pre-condition

### the agent's Discretion
- Exact LiteRT-LM API imports and method signatures (sampler config fields, conversation API) — use what's available in v0.11.0-rc1
- Toast/notification method for user feedback — use Android Toast or Snackbar pattern
- Sanitization regex specifics — any reasonable implementation of the agreed categories
- Error message strings and exact exception types to catch
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `LlmProvider` interface (`domain/provider/LlmProvider.kt`) — `chat(request) -> Flow<StreamToken>`, `listModels()`, `testConnection()` — implement for LiteRT-LM
- `LocalLlmProvider` (`data/local/inference/LocalLlmProvider.kt`) — template to follow: `@Singleton`, injects engine, `flow {}` builder, `flowOn(Dispatchers.Default)`
- `ProviderRouter` (`data/remote/provider/ProviderRouter.kt`) — `resolve(endpoint, modelId)` switching on `ProviderType`; needs new `LITE_RT_LM` branch
- `EngineManager` (`data/local/inference/EngineManager.kt`) — provides `switchToLiteRT()`, `getActiveEngine()`, `createLiteRTConversation()` — use as the LiteRT-LM entry point
- `LiteRTLmEngine` (`data/local/inference/LiteRTLmEngine.kt`) — init/close/createConversation lifecycle
- `ChatRequest` (`domain/model/ChatRequest.kt`) — messages + parameters; pass through to LiteRT-LM
- `StreamToken` (`domain/model/StreamToken.kt`) — Delta, Done, Error — emit from provider
- `GenerationParameters` (`domain/model/GenerationParameters.kt`) — source for SamplerConfig mapping
- `ProviderType` enum — add `LITE_RT_LM`

### Established Patterns
- `@Singleton` providers with `@Inject constructor(engine)` — Hilt wires engine automatically
- `flow {}` builder with `emit(StreamToken.Delta(token))` then `emit(StreamToken.Done())` 
- `flowOn(Dispatchers.Default)` for engine calls
- `configure(modelFilePath)` builder pattern on LocalLlmProvider — LiteRTLmProvider may use similar approach or get path from EngineManager

### Integration Points
- `ProviderType.kt`: add `LITE_RT_LM` enum value
- `ProviderRouter.kt`: add `Lazy<LiteRTLmProvider>` + `LITE_RT_LM` branch
- `InferenceModule.kt` / new Hilt module: provide `LiteRTLmProvider`
- New files: `InputSanitizer.kt`, `LiteRTLmProvider.kt`
- Chat UI (Phase 9): model selector will show LiteRT-LM models — this phase ensures the provider works end-to-end
</code_context>

<specifics>
## Specific Ideas

No specific requirements beyond ROADMAP success criteria — follow existing provider patterns from v1.0.
</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.
</deferred>
