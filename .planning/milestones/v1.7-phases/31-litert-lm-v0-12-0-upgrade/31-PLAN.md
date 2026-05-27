# Phase 31: LiteRT-LM v0.12.0 Upgrade — Plan

**Created:** 2026-05-25
**Phase:** 31 — LiteRT-LM v0.12.0 Upgrade

---

## Plan 1: Bump Version & Gradle Sync

**Goal:** Update `libs.versions.toml` to reference v0.12.0 and verify Gradle sync.

**Steps:**
1. Change `litertlm = "0.11.0"` → `"0.12.0"` in `gradle/libs.versions.toml`
2. Run `./gradlew --refresh-dependencies` to pull new version
3. Check if v0.12.0 resolves correctly

**Verification:** Gradle sync succeeds; dependency resolves.

---

## Plan 2: Fix Compilation Errors

**Goal:** Identify and adapt any v0.12.0 API breaking changes.

**Known v0.11.0 API surface in use:**
- `Engine`, `EngineConfig(modelPath, backend, visionBackend, audioBackend, cacheDir)`
- `Engine.initialize()`, `Engine.isInitialized()`, `Engine.close()`
- `ConversationConfig(initialMessages, samplerConfig, extraContext, tools, automaticToolCalling)`
- `SamplerConfig(topK, topP, temperature, seed)`
- `Message.system()`, `Message.user()`, `Message.model()`
- `Content.Text`, `Content.ImageBytes`, `Content.AudioBytes`, `Contents.of()`
- `Conversation.sendMessageAsync()`, `Conversation.isAlive`, `Conversation.close()`
- `LiteRtLmJniException`, `OpenApiTool`, `tool()`, `Backend.CPU()`, `Backend.GPU()`
- `ExperimentalApi`, `ExperimentalFlags.enableSpeculativeDecoding`
- `LogSeverity.ERROR`

**Steps:**
1. Run `./gradlew :app:compileDebugKotlin` to surface all errors
2. For each compilation error, check v0.12.0 changelog/migration guide
3. Apply fixes — adapt to new constructor signatures, renamed types, deprecated APIs

**Verification:** `./gradlew :app:compileDebugKotlin` succeeds with zero errors.

---

## Plan 3: Run Tests & Verify

**Goal:** Confirm no regressions and verify end-to-end compilation.

**Steps:**
1. Run `./gradlew :app:testDebugUnitTest` — ensure all existing tests pass
2. Run `./gradlew assembleDebug` — full build succeeds
3. Manual inspection of any v0.12.0 deprecation warnings

**Verification:**
- All existing unit tests pass
- Full APK builds without errors
- [Manual] Smoke test: load a local .litertlm model and send a message on device

---

*Plan created: 2026-05-25*
