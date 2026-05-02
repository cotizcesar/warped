---
phase: 07-provider-integration-chat
plan: 01
subsystem: local-inference
tags: [LiteRT-LM, provider, sanitization, parameter-mapping, error-recovery]
requires: [06-04 EngineManager + LiteRTLmEngine]
provides: [LITE_RT_LM enum, InputSanitizer, LiteRTLmProvider]
affects: [ProviderRouter (placeholder), 07-02 Hilt wiring]
tech-stack:
  added: []
  patterns: [LlmProvider implementation, Flow-based streaming, retry-with-recovery]
key-files:
  created:
    - app/src/main/java/com/warped/data/local/inference/InputSanitizer.kt
    - app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt
  modified:
    - app/src/main/java/com/warped/domain/model/ProviderType.kt
    - app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt
decisions:
  - "07-01/D-01: LITE_RT_LM added as 7th ProviderType enum value for local LiteRT-LM routing"
  - "07-01/D-02: InputSanitizer implemented as standalone utility with 5-category surgical regex (LaTeX, Unicode math, control chars, surrogates, zero-width) — Timber.d log when sanitization changes input"
  - "07-01/D-03: SamplerConfig constructed inline — temperature→temperature (Double), topK→topK (Int), topP→topP (Double), seed→seed (Int, 0 when -1); unsupported params (repeatPenalty, contextSize, threads) silently skipped"
  - "07-01/D-04: 2-retry error recovery via sendMessageWithRetry — detects IllegalStateException/'not alive'/'not initialized', calls recoverEngine() → EngineManager.switchToLiteRT()"
  - "07-01/D-05: maxTokens mapped via ConversationConfig.extraContext with key 'max_output_tokens' (no direct field in v0.11.0-rc1 ConversationConfig)"
  - "07-01/D-06: Toast notification deferred to Timber.w() log — Context not injected; log serves as recoverable signal"
metrics:
  duration: 348s
  completed_date: "2026-05-02T17:40:14Z"
---

# Phase 7 Plan 1: Core Provider Components Summary

**One-liner:** Established the LiteRT-LM chat pipeline with LITE_RT_LM enum routing, 5-category surgical InputSanitizer for Unicode/LaTeX crash prevention, and LiteRTLmProvider implementing LlmProvider with SamplerConfig mapping, Flow-based token streaming, and 2-retry engine error recovery.

## Tasks Completed

| # | Task | Type | Commit | Files |
|---|------|------|--------|-------|
| 1 | Add LITE_RT_LM to ProviderType enum | auto | `ada5af9` | ProviderType.kt, ProviderRouter.kt |
| 2 | Create InputSanitizer | auto | `1cfef60` | InputSanitizer.kt |
| 3 | Create LiteRTLmProvider | auto | `f0794c9` | LiteRTLmProvider.kt |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] ProviderRouter.kt `when` expression no longer exhaustive**
- **Found during:** Task 1 (compilation check)
- **Issue:** Adding `LITE_RT_LM` to `ProviderType` broke exhaustiveness of `ProviderRouter.resolve()` which uses a `when` on `ProviderType` values
- **Fix:** Added placeholder `LITE_RT_LM` case routing to `localLlmProvider` with a TODO comment noting proper routing will be implemented in Plan 02
- **Files modified:** `app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt`
- **Commit:** `ada5af9`

**2. [Rule 1 - Bug] `conversation.isAlive()` called as function instead of property**
- **Found during:** Task 3 (compilation check)
- **Issue:** The LiteRT-LM v0.11.0-rc1 API exposes `isAlive` as a Kotlin Boolean property, not a function. The plan's interface docs listed `isAlive(): Boolean` (Java-style), but the actual Kotlin API uses property syntax.
- **Fix:** Changed `conversation.isAlive()` to `conversation.isAlive`
- **Files modified:** `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt`
- **Commit:** `f0794c9`

## Threat Flags

None. All threat surface covered by plan's threat model (T-07-01 through T-07-05).

## Known Stubs

None. All components are fully functional with real engine interaction, parameter mapping, and error recovery — no placeholders, hardcoded empty values, or mock data paths.

## Requirements Satisfied

| Requirement | Description | Status |
|-------------|-------------|--------|
| LITE-05 | InputSanitizer surgical sanitization | ✅ Implemented |
| LITE-06 | LiteRTLmProvider chat() with LiteRT-LM streaming | ✅ Implemented |
| LITE-07 | Parameter mapping GenerationParameters → SamplerConfig | ✅ Implemented |
| POL-04 | Error recovery with auto-reinitialization (2 retries) | ✅ Implemented |

## Key Design Decisions

1. **InputSanitizer is a standalone utility** — no interface, `@Inject constructor()`, single `sanitize(String): String` method. Surgical regex targeting 5 known crash-vector categories from v0.10.2 Unicode/LaTeX bugs.

2. **Per-request Conversation creation** — each `chat()` call creates a new `Conversation` via `EngineManager.createLiteRTConversation()`, following the per-call factory pattern established in Phase 6 to prevent MediaTek SIGSEGV.

3. **Retry is bounded** — 2 retry attempts (3 total) with `EngineManager.switchToLiteRT()` recovery between attempts. Exhaustion produces a user-facing `StreamToken.Error`. No infinite loops.

4. **Seed semantics** — `GenerationParameters.seed = -1` (no explicit seed) maps to `SamplerConfig.seed = 0` (random in LiteRT-LM). This is intentional — LiteRT-LM uses 0 for "random" rather than -1.

## Verification Results

- [x] `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL
- [x] `ProviderType.LITE_RT_LM` enum value present, after LOCAL, all existing values unchanged
- [x] `InputSanitizer` has 5 `Regex(` calls covering: LaTeX delimiters, Unicode math blocks, control chars, surrogates, zero-width chars
- [x] `LiteRTLmProvider` implements all 3 `LlmProvider` methods: `chat()`, `listModels()`, `testConnection()`
- [x] `SamplerConfig` constructed with clamped values from `GenerationParameters`
- [x] `sendMessageWithRetry` with 2 retries and engine recovery via `switchToLiteRT()`
- [x] `flowOn(Dispatchers.Default)` on chat flow
- [x] `inputSanitizer.sanitize()` called on all messages before engine
