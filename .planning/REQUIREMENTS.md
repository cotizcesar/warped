# Requirements: Warped

**Defined:** 2026-05-08
**Core Value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.

## v1.4 Requirements

Requirements for the Onboarding Wizard milestone. SHIPPED 2026-05-09 — all 25 requirements complete.

### Wizard Flow (WZFL)

- [x] **WZFL-01**: User sees wizard on first app launch via DataStore flag
- [x] **WZFL-02**: User can skip individual steps via "Skip" button on each step
- [x] **WZFL-03**: User can skip entire wizard via "Skip all" with confirmation dialog
- [x] **WZFL-04**: User navigates between steps via swipe (HorizontalPager) and Next/Back buttons
- [x] **WZFL-05**: User sees step indicator dots showing current position out of 9 steps
- [x] **WZFL-06**: On completion, wizard marks as completed and navigates to Chat
- [x] **WZFL-07**: Pressing Back on first step exits the app (first launch) or returns to previous screen (re-entry from Settings)
- [x] **WZFL-08**: Wizard never auto-shows again after completion or skip all; stays accessible from Settings

### Wizard Steps (WZST)

- [x] **WZST-01**: Step 1 — Bienvenida: introduces Warped (local + remote LLMs), warm friendly tone
- [x] **WZST-02**: Step 2 — Motores locales: GGUF (llama.cpp) vs LiteRT-LM, how to switch
- [x] **WZST-03**: Step 3 — Descargar GGUF: Hugging Face search, download, manage models
- [x] **WZST-04**: Step 4 — Modelos LiteRT-LM: .litertlm import and usage
- [x] **WZST-05**: Step 5 — Chat local: load model, configure params, streaming chat
- [x] **WZST-06**: Step 6 — Proveedores remotos: add OpenAI, Anthropic, Ollama, LM Studio
- [x] **WZST-07**: Step 7 — Chat remoto: select remote model, chat with streaming
- [x] **WZST-08**: Step 8 — Presets: save/load generation parameter presets
- [x] **WZST-09**: Step 9 — Historial: browse past conversations, resume chats
- [x] **WZST-10**: Each step has CTA button navigating to relevant screen, wizard stays in back stack
- [x] **WZST-11**: Each step has icon, title, and short description (2-3 sentences)

### Wizard Context (WZCT)

- [x] **WZCT-01**: Wizard reads app state (model count, endpoint count, chat count) at open time
- [x] **WZCT-02**: Steps adapt content when data exists (e.g. "Ya tienes 3 modelos GGUF")
- [x] **WZCT-03**: Context data snapshotted once, not continuously observed

### Wizard Accessibility (WZAC)

- [x] **WZAC-01**: User can re-open wizard from Settings → General → "Setup Wizard"
- [x] **WZAC-02**: Re-opened wizard shows review variant with current app state (checkmarks, counts)
- [x] **WZAC-03**: Back from re-opened wizard returns to Settings (not exits app)

## v1.5 Requirements

Requirements for the Bug Hunt, Cleanup & Hardening milestone. Each maps to a roadmap phase.

### GGUF Removal (GGUF)

- [ ] **GGUF-01**: Native/NDK code deleted — cpp/ tree, CMakeLists.txt, .gitmodules llama.cpp submodule removed; ndk and externalNativeBuild blocks removed from build.gradle.kts
- [ ] **GGUF-02**: Kotlin engine files deleted — LlamaEngine.kt, LlamaLoadError.kt, GgufMetadataParser.kt, GgufQuantizationParser.kt removed; InferenceModule.kt updated
- [ ] **GGUF-03**: EngineManager simplified — LLAMA_CPP enum value removed, switchToLlama(), probeVulkan(), getLlamaEngine() removed; only LiteRT-LM path remains
- [ ] **GGUF-04**: Data model updated — LocalModel.modelFormat default → "LITERTLM", LocalModelEntity column default → "LITERTLM", ProviderType.LOCAL deprecated, Migrations MIGRATION_6_7 updated
- [ ] **GGUF-05**: UI GGUF references removed — "GGUF" format pills/badges removed from ChatScreen, ModelsScreen, HelpScreen
- [ ] **GGUF-06**: String resources cleaned — GGUF, llama.cpp, .gguf references removed from strings.xml (en + es)
- [ ] **GGUF-07**: Download/Import cleaned — GGUF validation/header check removed from ModelDownloadWorker; .gguf default and GgufMetadataParser removed from ModelImportManager

### Search Simplification (SRCH)

- [ ] **SRCH-01**: All tabs removed — PrimaryTabRow with Staff Picks/LiteRT-LM/GGUF tabs deleted from HuggingFaceScreen; single OutlinedTextField search bar remains
- [ ] **SRCH-02**: Staff Picks code removed — loadStaffPicks() deleted from ViewModel; getCollectionModels() removed from HuggingFaceApi, HuggingFaceRepository (domain + impl); HuggingFaceCollection/HuggingFaceCollectionItem DTOs removed
- [ ] **SRCH-03**: Search hardcoded — library=litert, no author filter, no format switching; always searches all .litertlm models on Hugging Face
- [ ] **SRCH-04**: UI state simplified — activeFormat and ggufFileDetails fields removed from HuggingFaceUiState; format badge always shows "LiteRT-LM"

### Bug Fixes (BUG)

- [ ] **BUG-01**: Code blocks render correctly during streaming — MarkdownText handles unclosed code fences without swallowing content; partial code blocks displayed as they stream
- [ ] **BUG-02**: Model reload fixed — selectConversation() does not unload engine when the already-loaded model matches the conversation's model; loading indicator shown when reload is actually needed
- [ ] **BUG-03**: Active conversation tracked — NavGraph activeConversationId synchronized when chat loads via chat/{conversationId} route; conversation highlighted in drawer
- [ ] **BUG-04**: Ghost message fixed — stopGeneration() clears streamingContent and streamingReasoning; deleteMessage(id) added at MessageDao, ChatRepository, and ChatViewModel layers; deleted message immediately removed from UI

### Security Hardening (SEC)

- [ ] **SEC-01**: ProGuard/R8 hardened — keep rules added for OkHttp, Retrofit, Hilt/Dagger, Kotlin Coroutines; -keepattributes Signature,Exceptions; -dontwarn for okhttp3, retrofit2
- [ ] **SEC-02**: Input sanitization applied globally — InputSanitizer.sanitize() called in OpenAIProvider, AnthropicProvider, and OllamaProvider on all user messages before sending
- [ ] **SEC-03**: Crash resilience — Thread.setDefaultUncaughtExceptionHandler set in WarpedApplication.onCreate(); CoroutineExceptionHandler added to viewModelScope; empty catch blocks replaced with Timber.e() logging
- [ ] **SEC-04**: Secure storage audited — CharArray→String conversion fixed in ApiKeyStore (direct ByteArray to Keystore); key alias logging removed from KeystoreManager; security-crypto upgraded from alpha to stable
- [ ] **SEC-05**: Network security config hardened — cleartextTrafficPermitted=false on base-config; domain-config blocks scoped to LAN IP ranges (192.168.x.x, 10.x.x.x, localhost); extractNativeLibs=false in AndroidManifest
- [ ] **SEC-06**: Logging secured — HttpLoggingInterceptor level conditioned on BuildConfig.DEBUG (Level.NONE in release, Level.BODY/HEADERS in debug) in both HttpClientFactory and NetworkModule
- [ ] **SEC-07**: Signing credentials externalized — keystore passwords and alias moved from build.gradle.kts to local.properties (gitignored); strong password generated
- [ ] **SEC-08**: Room database encrypted — SQLCipher SupportFactory applied in DatabaseModule; passphrase stored in Android Keystore; fallbackToDestructiveMigration retained for dev, removed for release

### Wizard Update (WZRD)

- [ ] **WZRD-01**: Wizard steps updated — GGUF_DOWNLOAD step removed; model download steps consolidated; step count reduced from 9 to 8; step descriptions updated to reflect LiteRT-LM-only engine
- [ ] **WZRD-02**: Wizard state simplified — ggufModelCount removed from WizardUiState; WizardViewModel GGUF count logic removed; context badges updated
- [ ] **WZRD-03**: Step content updated — GGUF-specific badges and descriptions removed from StepContent; engine comparison step simplified to LiteRT-LM-only explanation

## Out of Scope

| Feature | Reason |
|---------|--------|
| Tooltips/coach marks on main UI | Defer — full-screen wizard is cleaner for v1. Coach marks add complexity with positioning and z-ordering. |
| Wizard re-trigger on app update | Avoid annoyance — wizard only shows on fresh install, not on updates. |
| Video or animated illustrations | Unnecessary overhead for a text-based LLM app. Static icons + text are sufficient. |
| Forced sequential completion | User must be able to skip or exit at any time — already covered by skip per step + skip all. |
| Per-step analytics/telemetry | No tracking in the app. Out of scope for privacy. |
| GGUF / llama.cpp local inference | REMOVED in v1.5. App pivots to LiteRT-LM as sole local engine. Hugging Face search limited to litert-community `.litertlm` models. |
| Staff Picks tab and multi-tab model browser | REMOVED in v1.5. Single search bar replaces tabbed browsing. |
| Certificate pinning for remote endpoints | Defer — not selected for v1.5 hardening scope. Can add in future release. |
| Play Integrity / root detection | Defer — not selected for v1.5 hardening scope. Can add in future release. |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| GGUF-01 | Phase 23 | Pending |
| GGUF-02 | Phase 23 | Pending |
| GGUF-03 | Phase 23 | Pending |
| GGUF-04 | Phase 23 | Pending |
| GGUF-05 | Phase 23 | Pending |
| GGUF-06 | Phase 23 | Pending |
| GGUF-07 | Phase 23 | Pending |
| SRCH-01 | Phase 24 | Pending |
| SRCH-02 | Phase 24 | Pending |
| SRCH-03 | Phase 24 | Pending |
| SRCH-04 | Phase 24 | Pending |
| BUG-01 | Phase 25 | Pending |
| BUG-02 | Phase 25 | Pending |
| BUG-03 | Phase 25 | Pending |
| BUG-04 | Phase 25 | Pending |
| SEC-01 | Phase 26 | Pending |
| SEC-02 | Phase 26 | Pending |
| SEC-03 | Phase 26 | Pending |
| SEC-04 | Phase 26 | Pending |
| SEC-05 | Phase 26 | Pending |
| SEC-06 | Phase 26 | Pending |
| SEC-07 | Phase 26 | Pending |
| SEC-08 | Phase 26 | Pending |
| WZRD-01 | Phase 27 | Pending |
| WZRD-02 | Phase 27 | Pending |
| WZRD-03 | Phase 27 | Pending |

**Coverage:**
- v1.5 requirements: 26 total
- Mapped to phases: 26
- Unmapped: 0 ✓

---
*Requirements defined: 2026-05-08*
*Last updated: 2026-05-09 after v1.5 requirements definition*
