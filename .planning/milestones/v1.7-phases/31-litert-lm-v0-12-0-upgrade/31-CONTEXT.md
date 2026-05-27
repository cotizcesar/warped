# Phase 31: LiteRT-LM v0.12.0 Upgrade - Context

**Gathered:** 2026-05-25
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase — discuss skipped)

<domain>
## Phase Boundary

Upgrade the inference engine from v0.11.0 to v0.12.0, verify compilation and all existing functionality. Pure infrastructure — no user-facing behavior changes.

</domain>

<decisions>
## Implementation Decisions

### the agent's Discretion
All implementation choices are at the agent's discretion — pure infrastructure phase. Use ROADMAP phase goal, success criteria, and codebase conventions to guide decisions.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `LiteRTLmEngine.kt` — wraps `com.google.ai.edge.litertlm.Engine` API: `Engine(EngineConfig)`, `engine.initialize()`, `engine.createConversation()`, `engine.close()`
- `LiteRTLmProvider.kt` — implements `LlmProvider`, uses `Conversation.sendMessageAsync()`, `Message`, `Content`, `Contents`, `SamplerConfig`, `ConversationConfig`, `OpenApiTool`, `tool()`
- `EngineManager.kt` — orchestrates engine lifecycle: `switchToLiteRT()`, `unloadCurrent()`, `createLiteRTConversation()`
- `BackendDetector.kt` — probes EGL/OpenCL for GPU detection
- `InferenceModule.kt` — Hilt DI wiring for `LiteRTLmEngine`, `EngineManager`, `LiteRTLmProvider`

### Established Patterns
- LiteRT-LM v0.11.0 uses `com.google.ai.edge.litertlm` package
- `EngineConfig(modelPath, backend, visionBackend, audioBackend, cacheDir)` constructor
- `ConversationConfig(initialMessages, samplerConfig, extraContext, tools, automaticToolCalling)` constructor
- `Message.system()`, `Message.user()`, `Message.model()` factory methods
- `SamplerConfig(topK, topP, temperature, seed)` constructor
- `@ExperimentalApi` annotation on `ExperimentalFlags.enableSpeculativeDecoding`
- `LiteRtLmJniException` for native errors

### Integration Points
- `libs.versions.toml` — `litertlm = "0.11.0"` → change to `"0.12.0"`
- `app/build.gradle.kts` — references litertlm via version catalog
- All source files in `data/local/inference/` importing `com.google.ai.edge.litertlm.*`

</code_context>

<specifics>
## Specific Ideas

No specific requirements — infrastructure phase. Tasks:
1. Bump `litertlm` version in `gradle/libs.versions.toml` from `0.11.0` to `0.12.0`
2. Gradle sync and resolve any dependency changes
3. Fix compilation errors if v0.12.0 introduces API changes (deprecated methods, changed constructors, renamed types)
4. Run unit tests: `./gradlew :app:testDebugUnitTest`
5. Build check: `./gradlew assembleDebug`

</specifics>

<deferred>
## Deferred Ideas

None — pure infrastructure phase.

</deferred>
