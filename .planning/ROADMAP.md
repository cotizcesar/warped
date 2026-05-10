# Roadmap: Warped v1.5 — Bug Hunt, Cleanup & Hardening Pre-Prod

**Created:** 2026-05-09
**Milestone:** v1.5
**Goal:** Eliminate GGUF, simplify search, fix 4 chat bugs, harden for production release.
**Phase numbering:** Continues from v1.4 (last phase: 22)

## Phase Overview

| # | Phase | Goal | Requirements | Success Criteria |
|---|-------|------|--------------|------------------|
| 23 | GGUF Removal | Delete all llama.cpp/GGUF code, JNI, NDK, and references | GGUF-01..07 (7) | 5 |
| 24 | Search Simplification | Remove tabs, hardcode litert search, delete Staff Picks | SRCH-01..04 (4) | 4 |
| 25 | Bug Fixes | Fix 4 chat bugs: code rendering, model reload, tracking, ghost delete | BUG-01..04 (4) | 4 |
| 26 | Security Hardening | ProGuard, input sanitization, crash resilience, storage, network | SEC-01..08 (8) | 5 |
| 27 | Wizard Update | Update onboarding wizard for LiteRT-LM-only engine | WZRD-01..03 (3) | 3 |

**26 requirements | 5 phases | All covered ✓**

---

## Phase 23: GGUF Removal

**Goal:** Completely remove llama.cpp/GGUF support — native code, Kotlin engine, JNI bridge, NDK config, and all GGUF references across the codebase. LiteRT-LM becomes the sole local inference engine.

**Requirements:** GGUF-01, GGUF-02, GGUF-03, GGUF-04, GGUF-05, GGUF-06, GGUF-07

**Depends on:** None (foundational cleanup)

### Success Criteria

1. App compiles and runs without NDK, CMake, or externalNativeBuild configuration
2. No `import com.warped.data.local.inference.LlamaEngine` or `GgufMetadataParser` references exist anywhere
3. EngineManager.kt contains no reference to `LLAMA_CPP`, `switchToLlama()`, or `probeVulkan()`
4. `modelFormat` column defaults to `"LITERTLM"` in Room schema; ProviderType.LOCAL deprecated
5. All GGUF strings removed from `strings.xml` (en + es); app compiles without missing resource errors

### Tasks

- Delete `app/src/main/cpp/` directory (llama.cpp submodule, JNI bridge, CMakeLists.txt)
- Remove submodule entry from `.gitmodules`; run `git rm` on submodule
- Remove `ndk { abiFilters }`, `externalNativeBuild { cmake }`, `packaging { jniLibs }` from `app/build.gradle.kts`
- Delete `LlamaEngine.kt`, `LlamaLoadError.kt`, `GgufMetadataParser.kt`, `GgufQuantizationParser.kt`
- Remove `provideLlamaEngine()` and `provideLocalLlmProvider()` from `InferenceModule.kt`
- Simplify `EngineManager.kt`: remove `LLAMA_CPP` enum, `switchToLlama()`, `probeVulkan()` call, `getLlamaEngine()`, Vulkan-related logic in `BackendDetector.kt`
- Update `LocalModel.kt` — default `modelFormat = "LITERTLM"`
- Update `LocalModelEntity.kt` — `@ColumnInfo(defaultValue = "LITERTLM")`
- Deprecate `ProviderType.LOCAL` (keep for backward compat, route to LiteRT-LM)
- Update `MIGRATION_6_7` and `MIGRATION_8_9` — change `DEFAULT 'GGUF'` to `DEFAULT 'LITERTLM'`
- Remove GGUF format pills from `ChatScreen.kt` (lines 173-186, 229)
- Remove GGUF RAM check badges from `ModelsScreen.kt` (lines 380-401, 443-444)
- Remove GGUF references from `HelpScreen.kt` (lines 71-72, 76, 94)
- Remove GGUF validation from `ModelDownloadWorker.kt` (lines 270-302)
- Remove GGUF metadata parsing from `ModelImportManager.kt` (lines 49-67)
- Remove `.gguf` default filename from `ModelImportManager.kt` (line 27)
- Clean `strings.xml` (en + es): remove "GGUF", "llama.cpp", ".gguf", "Local (llama.cpp)" entries
- Remove llama JNI keep rule from `proguard-rules.pro` (lines 17-18)
- Update `ChatViewModel.kt`: remove `LlamaEngine`/`LlamaLoadError` imports and `llamaEngine` field
- Update `LocalLlmProvider.kt`: remove llamaEngine dependency; handle only LiteRT-LM path

**Plans:** 1 plan

Plans:
- [x] 23-01-PLAN.md — Delete native code + engine files, simplify EngineManager/DI, clean data model/UI/strings/download

---

## Phase 24: Search Simplification

**Goal:** Remove all tabs from model search. Single search bar that queries `library=litert` across all Hugging Face. Delete Staff Picks code and related DTOs.

**Requirements:** SRCH-01, SRCH-02, SRCH-03, SRCH-04

**Depends on:** Phase 23 (GGUF references in HuggingFaceUiState must be cleaned first)

### Success Criteria

1. HuggingFaceScreen renders a single search bar with no tab row
2. Search queries use `library=litert` with no author filter; returns .litertlm models from all Hugging Face
3. Staff Picks API endpoint, repository method, and DTOs are fully deleted
4. HuggingFaceUiState contains no `activeFormat` or `ggufFileDetails` fields

### Tasks

- Delete `PrimaryTabRow` and `formats` list from `HuggingFaceScreen.kt` (lines 158-170)
- Delete `loadStaffPicks()` from `HuggingFaceViewModel.kt` (lines 214-235)
- Delete `getCollectionModels()` from `HuggingFaceApi.kt` (lines 28-32)
- Delete `getCollectionModels()` from `HuggingFaceRepository.kt` (domain interface)
- Delete `getCollectionModels()` from `HuggingFaceRepositoryImpl.kt`
- Delete `HuggingFaceCollection` and `HuggingFaceCollectionItem` from `HuggingFaceDtos.kt`
- Hardcode search: `library = "litert"`, remove `author` parameter, remove `activeFormat` branching
- Remove `activeFormat` from `HuggingFaceUiState.kt` (line 31)
- Remove `ggufFileDetails` / `GgufFileDetail` from `HuggingFaceUiState.kt`
- Remove `setActiveFormat()` from ViewModel
- Simplify `FormatBadge` in screen to always show "LiteRT-LM" (green)
- Remove `.gguf` sibling filtering, quantization parsing, RAM estimate display from detail view
- Update `HuggingFaceRepository.searchModels()` — remove `format` default param, hardcode `library = "litert"`
- Update `HuggingFaceApi.searchModels()` — remove `library` default of `"gguf"`

**Plans:** 1 plan

Plans:
- [x] 24-01-PLAN.md — Delete Staff Picks DTOs/API/Repository, hardcode litert search, remove tabs and activeFormat from UI state and screen

---

## Phase 25: Bug Fixes

**Goal:** Fix 4 critical chat bugs — code block rendering during streaming, model reload on re-entry, active conversation highlighting, and ghost messages after stop/delete.

**Requirements:** BUG-01, BUG-02, BUG-03, BUG-04

**Depends on:** Phase 23 (ChatViewModel.kt and EngineManager.kt must be GGUF-free first)

### Success Criteria

1. Streaming code blocks render progressively; unclosed fences do not swallow content
2. Re-entering a chat with an already-loaded model shows no warning; loading indicator appears only when reload is needed
3. Active conversation is highlighted in drawer regardless of how chat was opened (route, click, app restart)
4. Stopping generation clears the pseudo-bubble immediately; deleting a message removes it from UI and database instantly

### Tasks

- **BUG-01 — MarkdownText.kt (lines 37-63):** Rewrite code block parser to buffer into a temporary accumulator that renders on each new line during streaming. Handle unclosed fences by rendering accumulated content when stream ends.
- **BUG-02 — ChatViewModel.kt (line 285):** Before calling `scheduleUnload()` in `selectConversation()`, check if `engineManager.getActiveEngine()?.modelPath` matches the conversation's model. If same model, skip unload and call `refreshActiveBackend()` instead.
- **BUG-02 — ChatViewModel.kt (lines 521-556):** Ensure `preloadLocalModel()` or equivalent shows `isLoadingModel = true` indicator when model reload is actually needed.
- **BUG-03 — NavGraph.kt (line 66):** In `composable("chat/{conversationId}")` block (lines 291-305), add `LaunchedEffect(convId) { activeConversationId = convId }`. Also sync from `activeModelSelection.getLastConversation()` on app start.
- **BUG-04 — ChatViewModel.kt (lines 278-282):** In `stopGeneration()`, also clear `streamingContent = ""` and `streamingReasoning = ""`.
- **BUG-04 — MessageDao.kt:** Add `@Query("DELETE FROM messages WHERE id = :messageId") suspend fun deleteById(messageId: Long)`.
- **BUG-04 — ChatRepository.kt + ChatRepositoryImpl.kt:** Add `suspend fun deleteMessage(messageId: Long)`.
- **BUG-04 — ChatViewModel.kt:** Add `fun deleteMessage(messageId: Long)` that calls repository, removes message from `_uiState.value.messages`, and forces UI recomposition.

---

## Phase 26: Security Hardening

**Goal:** Harden the app for production release — ProGuard/R8 coverage, input sanitization on all providers, global crash handling, secure storage audit, network security lock-down, logging protection, credential externalization, Room encryption.

**Requirements:** SEC-01, SEC-02, SEC-03, SEC-04, SEC-05, SEC-06, SEC-07, SEC-08

**Depends on:** None (security changes are orthogonal to other phases)

### Success Criteria

1. `./gradlew assembleRelease` succeeds with ProGuard/R8 enabled; no `MissingClass` or reflective access warnings
2. All 4 remote providers (OpenAI, Anthropic, Ollama, LM Studio) call `InputSanitizer.sanitize()` before sending user messages
3. App shows a user-facing error dialog on unhandled exceptions instead of crashing to home screen
4. `security-crypto` version is stable (not alpha); API key storage does not leak into Java heap via String conversion
5. Cleartext HTTP blocked for all internet traffic; only LAN IP ranges allowed; `extractNativeLibs="false"`
6. Release APK produces no HTTP body/header logs; debug APK logging unchanged
7. Signing keystore path references `local.properties`; no plaintext passwords in `build.gradle.kts`
8. Room database file on disk is encrypted (SQLCipher); readable only when app is running with Keystore-derived passphrase

### Tasks

- **SEC-01:** Add `-keep` rules for `okhttp3.**`, `retrofit2.**`, `dagger.**`, `kotlinx.coroutines.**` to `proguard-rules.pro`. Add `-keepattributes Signature,Exceptions,InnerClasses,EnclosingMethod`. Add `-dontwarn` for okhttp3, retrofit2 internal classes. Remove `android.r8.strictFullModeForKeepRules=false` from `gradle.properties`.
- **SEC-02:** Add `InputSanitizer.sanitize(text)` call in `OpenAIProvider.kt`, `AnthropicProvider.kt`, `OllamaProvider.kt` — at the top of chat/completion methods, before sending to API.
- **SEC-03:** Register `Thread.setDefaultUncaughtExceptionHandler` in `WarpedApplication.onCreate()`. Show a Material 3 AlertDialog or Toast with "Something went wrong" message. Add `CoroutineExceptionHandler` to `viewModelScope` in all ViewModels. Replace all `catch (_: Exception) {}` with `catch (e: Exception) { Timber.e(e, "context") }`.
- **SEC-04:** Fix `ApiKeyStore.kt` — avoid `String(apiKey)` conversion. Use `keystoreManager.put(alias, apiKey)` where apiKey is `CharArray` and internally encodes to `ByteArray` via `Charsets.UTF_8`. Remove `Timber.d("KeystoreManager: put succeeded for key=$key")` log. Upgrade `security-crypto` from `1.1.0-alpha06` to latest stable in `libs.versions.toml`.
- **SEC-05:** Change `network_security_config.xml` `<base-config cleartextTrafficPermitted="false">`. Add `<domain-config cleartextTrafficPermitted="true">` blocks for `192.168.0.0/16`, `10.0.0.0/8`, `172.16.0.0/12`, `localhost`. Add `android:extractNativeLibs="false"` to `<application>` in `AndroidManifest.xml`.
- **SEC-06:** In `HttpClientFactory.kt` and `NetworkModule.kt`, wrap `HttpLoggingInterceptor` level: `if (BuildConfig.DEBUG) Level.BODY else Level.NONE` (or `Level.HEADERS` for debug). Remove the unconditional `Level.BODY`/`HEADERS`.
- **SEC-07:** Move `storeFile`, `storePassword`, `keyAlias`, `keyPassword` from `build.gradle.kts` to `~/.gradle/gradle.properties` or `local.properties`. Reference via `project.findProperty()`. Generate strong password; ensure `local.properties` is in `.gitignore`.
- **SEC-08:** Add `net.zetetic:android-database-sqlcipher` dependency to `libs.versions.toml`. In `DatabaseModule.kt`, wrap `Room.databaseBuilder()` with `.openHelperFactory(SupportFactory(passphrase))`. Derive passphrase from Android Keystore (not hardcoded). Apply to release builds only (keep in-memory for tests).

---

## Phase 27: Wizard Update

**Goal:** Update the onboarding wizard to reflect that LiteRT-LM is the sole local inference engine. Remove GGUF-specific step, simplify content, update copy.

**Requirements:** WZRD-01, WZRD-02, WZRD-03

**Depends on:** Phase 23 (wizard references GGUF which must be gone first)

### Success Criteria

1. Wizard has 8 steps (not 9); step indicator shows 8 dots; no reference to GGUF in any step
2. Engine step describes only LiteRT-LM; download step describes only .litertlm models
3. Re-opening wizard from Settings shows updated content with correct model counts

### Tasks

- Remove `GGUF_DOWNLOAD` from `WizardStep.kt` enum; renumber subsequent steps if needed
- Remove `ggufModelCount` from `WizardContextData` in `WizardUiState.kt`
- Remove GGUF count logic from `WizardViewModel.kt` (line 57)
- Remove GGUF badge text from `StepContent.kt` (lines 98-104)
- Remove GGUF description block from `StepContent.kt` (lines 166-172)
- Update Step 2 (Motores locales): remove GGUF/llama.cpp comparison; explain only LiteRT-LM
- Update Step 3 (Descargar modelos): consolidate GGUF + LiteRT-LM download steps; describe only .litertlm
- Update `WizardStep.kt` step count references; update `PageIndicator` max dots to 8
- Update string resources for wizard GGUF references in `strings.xml` (en + es)

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| GGUF removal breaks LiteRT-LM engine (shared code paths) | Medium | High | Phase 23 isolates EngineManager changes; tests verify LiteRT-LM still works |
| SQLCipher migration corrupts existing Room database | Low | High | Phase 26 includes migration from unencrypted to encrypted; test with real DB file |
| ProGuard/R8 strips critical runtime classes | Medium | Medium | Incremental keep rules; verify release build on device, not just emulator |
| Search change (no author filter) returns irrelevant models | Low | Low | library=litert is specific enough; test with empty and populated queries |
| Keystore changes break existing stored API keys | Low | High | Migration path: read old keys, re-store with new method, delete old entries |

---

*Roadmap created: 2026-05-09*
