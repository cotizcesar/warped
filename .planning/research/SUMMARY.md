# Research Summary — Warped (Android LLM Client)

**Date**: 2026-04-30
**Status**: Synthesis complete — feeds into requirements and roadmap

---

## 1. Executive Summary

Warped is an Android equivalent to LM Studio, combining local GGUF inference via llama.cpp with multi-provider remote connectivity in a single polished Compose app. The project targets flagship and midrange Android devices (minSdk 28, ~95% reach), using Kotlin 2.1.10 with Clean Architecture, Hilt DI, and a custom JNI bridge for native inference. The build order is sequenced for fast feedback: remote chat ships first (validating the `LLMProvider` interface and streaming pipeline), then local inference, then model acquisition and polish. The primary risk vector is native stability (SIGSEGV crashes, OOM, thread safety) — mitigated by running inference in a separate process and enforcing strict JNI safety patterns.

---

## 2. Recommended Stack

| Layer | Choice | Version | Rationale |
|-------|--------|---------|-----------|
| Language | Kotlin | 2.1.10 | K2 compiler default, widest tooling support; required by Hilt 2.59.x |
| UI | Jetpack Compose + Material 3 | BOM 2025.04.00 | Declarative, first-class Android, Compose compiler bundled in Kotlin 2.0+ |
| DI | Hilt (Dagger) | 2.59.2 | Compile-time validation, ViewModel/WorkManager/Navigation integration |
| Local DB | Room | 2.7.x | Official Android ORM, Flow-based observation, KSP annotation processing |
| Prefs | DataStore (Preferences) | 1.1.x | Coroutine-based, type-safe; replaces deprecated SharedPreferences |
| HTTP Client | OkHttp + Retrofit | 4.12.0 / 2.11.1 | SSE streaming, interceptors, connection pooling; Retrofit for type-safe REST |
| JSON | Kotlinx Serialization | 1.7.x | Kotlin-native, no reflection, KSP-based code generation |
| Background Work | WorkManager | 2.10.x | Survives process death, constraints (WiFi-only, battery), expedited work |
| Concurrency | Kotlin Coroutines + Flow | 1.9.x | Structured concurrency, `StateFlow` for UI state, `callbackFlow` for JNI |
| Inference Engine | llama.cpp | latest release | Reference GGUF implementation, Android arm64 builds, 108k GitHub stars |
| Native Build | NDK 27+ + CMake | 3.22+ | Cross-compile llama.cpp for arm64-v8a; build from source for ABI match |
| Security | EncryptedSharedPreferences | 1.1.0 (security-crypto) | AES-256 backed by Android Keystore (TEE/StrongBox) |
| Testing | JUnit 5 + MockK + Turbine + Truth | 5.11.x / 1.13.x / 1.1.x / 1.4.x | Kotlin-first mocking, Flow testing, fluent assertions |
| Build | AGP 9.0.x + Gradle 8.13+ + KSP | 2.1.10-1.0.x | Version catalog (`libs.versions.toml`), Kotlin DSL, kapt replaced by KSP |
| Linting | Detekt + ktlint | 1.23.x / 1.5.x | Static analysis + formatting for Kotlin |

**Key anti-choices**: NO Flutter/React Native (JNI friction), NO Koin (runtime DI), NO Ktor (Android-specific OkHttp is better), NO MLC-LLM/ExecuTorch (llama.cpp is the reference), NO KAPT (use KSP), NO LiveData (use Flow/StateFlow).

---

## 3. v1 Feature Set

### Table Stakes (must ship for M1)

| # | Feature | Complexity | Dependencies |
|---|---------|------------|--------------|
| 1 | Chat interface with real-time token streaming | Medium | SSE parser, Compose Flow integration |
| 2 | Remote provider connectivity (OpenAI-compatible + Ollama + LM Studio + custom) | Medium | OkHttp, SSE, endpoint config |
| 3 | Local model inference via llama.cpp/GGUF | **High** | JNI/NDK, GGUF files on disk |
| 4 | Hugging Face model search and download with pause/resume | **High** | HF API, WorkManager, file I/O |
| 5 | Model management (list, view metadata, delete) | Medium | Room, file system |
| 6 | API key management (encrypted Keystore storage) | Medium | Android Keystore, EncryptedSharedPreferences |
| 7 | Chat history persistence (conversations + messages survive restarts) | Low-Medium | Room |
| 8 | Basic generation parameters (temperature, top_p, top_k, repeat_penalty, max_tokens, context_size, seed) | Low | UI widgets, serialization |

### Differentiators (shipped in v1 to establish competitive position)

| # | Feature | Complexity |
|---|---------|------------|
| 9 | Unified local + remote model list (same UI regardless of backend) | Medium-High |
| 10 | Hugging Face in-app search with GGUF filter and curated recommendations | Medium-High |
| 11 | Model download pause/resume with foreground notification | Medium-High |
| 12 | Import local GGUF files via system file picker | Low-Medium |
| 13 | Generation presets (saved named parameter profiles) | Low |
| 14 | Custom endpoint configuration with "Test Connection" button | Medium |
| 15 | Device-aware model size warnings (RAM-based heuristics) | Medium |
| 16 | Offline chat continuity (local models work; remote shows connectivity errors) | Low |
| 17 | Inference performance controls (thread count, context size, GPU toggle) | Medium-High |

### Build Order

1. **Foundation**: Room + DataStore + Keystore + navigation shell + domain models
2. **Remote chat**: OkHttp + SSE + OpenAI/Ollama/LMStudio providers + chat UI + streaming + chat history
3. **Model acquisition**: HF search + HF download w/ resume + import local GGUF + model management
4. **Local inference**: llama.cpp JNI + GGUF loading + local chat path + performance controls
5. **Polish**: Unified model list + presets + device warnings + offline continuity + hardening

---

## 4. Architecture Blueprint

### Layer Diagram

```
┌─────────────────────────────────────────────────┐
│  UI LAYER (Compose screens + ViewModels)         │
│  ChatScreen, ModelsScreen, EndpointsScreen,      │
│  SettingsScreen, shared :core:ui components      │
├─────────────────────────────────────────────────┤
│  DOMAIN LAYER (pure Kotlin, no Android deps)     │
│  UseCases, LLMProvider interface, StreamToken,   │
│  domain models, repository interfaces            │
├─────────────────────┬───────────────────────────┤
│  DATA LAYER         │  NATIVE LAYER (C++ via JNI)│
│  Room, DataStore,   │  llama.cpp, GGUF loader,   │
│  Retrofit/OkHttp,   │  tokenizer, Vulkan delegate │
│  Keystore           │  (separate :inference proc) │
└─────────────────────┴───────────────────────────┘
```

### Central Abstraction: LLMProvider

All chat providers implement the same interface, decoupling the UI from backend specifics:

```kotlin
interface LLMProvider {
    val type: ProviderType
    suspend fun chat(messages: List<Message>, params: GenerationParams): Flow<StreamToken>
    suspend fun listModels(): Result<List<ModelInfo>>
    suspend fun testConnection(): Result<ConnectionStatus>
}
```

Five implementations: `OpenAIProvider`, `OllamaProvider`, `LMStudioProvider`, `CustomProvider`, `LlamaInferenceProvider`.

### Key Data Flow: Streaming Chat

```
User types → ChatScreen → ChatViewModel → SendMessageUseCase
  → ChatRepository → ProviderRouter.resolve(endpoint)
    → LLMProvider.chat() → SSE/Llama tokens
      → Flow<StreamToken> back up
        → ViewModel updates StateFlow<ChatUiState>
          → Compose recomposes with new token appended
```

Local inference uses the same pipeline — only the provider implementation differs.

### Module Structure (Gradle)

```
:core:common          → domain models, LLMProvider interface (zero Android deps)
:core:network         → OkHttp, SSE parser, interceptor chain
:core:database        → Room DB, DAOs, entities, migrations
:core:security        → Keystore encryption, API key storage
:core:ui              → shared Compose components, theme
:feature:chat         → chat screen, message list, streaming display
:feature:models       → model management, HF search, downloads, import
:feature:endpoints    → endpoint CRUD, testing, model listing
:feature:settings     → params, presets, app settings
:library:llama-native → JNI bridge, .so packaging, inference service
```

Boundary rules: `:core:common` has zero Android deps. Features never depend on each other. `llama-native` only depends on domain interfaces — swappable to ExecuTorch/MLC later.

---

## 5. Critical Pitfalls (Top 5)

### P1 | Native crash kills entire process (Severity: CRITICAL)

Native SIGSEGV in llama.cpp (bad model, OOM, corrupted context) kills the JVM with zero Kotlin-level recovery.

**Prevention**: Run inference in a **separate process** (`android:process=":inference"`) communicating via AIDL/Messenger. Wrap JNI calls with SIGSEGV handler and `longjmp` recovery path. Expose `llama_free_all()` for graceful restart.

### P2 | SSE parser breaks on mobile network jitter (Severity: HIGH)

SSE payloads arrive fragmented across TCP packets. Naive per-line parsing misses tokens that span chunk boundaries.

**Prevention**: Use a line accumulator buffer. Parse on `\n\n` double-newline boundaries only. Test with network-condition throttling (400ms latency, 2% packet loss).

### P3 | Download resets on interruption waste gigabytes (Severity: HIGH)

GGUF models are 1-30GB. Without HTTP Range headers, any disruption restarts from byte 0. Mobile connections drop constantly.

**Prevention**: Always use `Range: bytes=<existing_size>-` headers. Persist download progress to Room. Use foreground service with WorkManager to survive Doze. Validate SHA256 after every resume.

### P4 | Thread safety around llama_context (Severity: HIGH)

llama.cpp's `llama_context` is not thread-safe. Concurrent coroutines calling `llama_eval` cause silent token corruption or hard crash.

**Prevention**: Use a `Mutex` or single-threaded `Channel` actor per model context. Document: one context = one coroutine actor. Disable regenerate button during cancellation.

### P5 | Plaintext API key storage (Severity: CRITICAL)

SharedPreferences/DataStore keys are readable via ADB backup, rooted devices, or malware with file permissions.

**Prevention**: Android Keystore with AES-GCM. Store only ciphertext in DataStore. Redact keys from logs via custom Timber tree. Use `CharArray` + zero-fill for in-memory key handling.

### Full Top-8 Project Killers

| Rank | Pitfall | Impact | Phase |
|------|---------|--------|-------|
| 1 | Running inference on UI thread | ANR, 1-star reviews | Local inference |
| 2 | SSE parser breaks on mobile network jitter | Silent data loss | Remote connectivity |
| 3 | Download reset on interruption | Wasted GBs, uninstall | Model management |
| 4 | Native crash kills whole process | Hard crash, no recovery | Local inference |
| 5 | Plaintext API key storage | Data breach, Play rejection | Security |
| 6 | No stop/cancel mechanism | Battery drain, ANR | Chat UI / streaming |
| 7 | Thread safety around llama_context | Corrupted output, crashes | Local inference |
| 8 | Process death loses everything | Terrible UX after app switch | All |

---

## 6. Risk Assessment Matrix

| Risk | Severity | Probability | Impact | Mitigation |
|------|----------|-------------|--------|------------|
| Native crash (SIGSEGV/OOM) in llama.cpp | Critical | Medium | App hard-crash, no recovery UI | Separate process, JNI safety wrapper, signal handler |
| API key leakage via logs or plaintext storage | Critical | Low | Data breach, Play Store rejection | Keystore + EncryptedSharedPreferences, redacted logging, CharArray zero-fill |
| Download reset on mobile interruptions | High | High | User frustration, wasted data, uninstalls | Range headers, foreground service, Room progress persistence |
| SSE streaming corruption on unreliable networks | High | Medium | Truncated/missing tokens, broken UX | Line accumulator buffer, network-condition testing |
| Thread safety bugs in llama_context | High | Medium | Corrupted output, mysterious crashes | Single-thread Channel actor per context, Mutex gating |
| Thermal throttling during sustained inference | Medium | High | Dramatic token/s drop >3min in, hot device | Thermal status monitoring, auto-pause on SEVERE, reduced thread count |
| Process death during inference | Medium | High | Loss of in-progress message, model reload | SavedStateHandle, Room persistence every N tokens, foreground service |
| Doze blocking long downloads | Medium | High | Hours-long stalls, user confusion | Foreground service exemption, battery optimization request, expedited work |
| GGUF model too large for device RAM | Medium | High | OOM crash, wasted download | Pre-load RAM estimation, device-aware warnings, context size slider |
| Hugging Face API rate limiting | Low-Medium | Medium | 429 bans, broken model search | Token-bucket rate limiter (5 req/s), Room cache with 15min TTL, Retry-After handling |
| GPU delegate fragmentation (Vulkan driver variance) | Medium | Low (deferred) | GPU-feature broken on some SoCs | Start CPU-only; later GPU as opt-in with vendor blocklist and CPU fallback |
| Self-signed cert rejection on local endpoints | Low | Medium | Can't connect to Ollama/LM Studio on LAN | Per-endpoint "trust self-signed" toggle with cert fingerprint pinning; cleartext allowed only for LAN IPs |

---

## 7. Confidence Levels

| Area | Confidence | Notes |
|------|-----------|-------|
| Core Platform (Kotlin, Compose, SDK) | **HIGH** | Kotlin 2.1.10 confirmed via Dagger 2.59 release notes. Compose BOM verified against Google Maven release cadence. |
| DI & Architecture (Hilt, Clean Architecture) | **HIGH** | Dagger 2.59.2 confirmed via GitHub releases (Feb 2026). MVVM + Repository pattern is Android industry standard. |
| Local Storage (Room, DataStore) | **HIGH** | Stable AndroidX libraries. Exact patch versions resolve via BOM or latest stable. |
| Networking (OkHttp, Retrofit, SSE) | **HIGH** | OkHttp 4.12.0 long-term stable. SSE streaming pattern proven in Android chat apps. |
| Background Work (WorkManager, Coroutines) | **HIGH** | WorkManager mature; Coroutines 1.9.x confirmed via Kotlin release cycle. |
| Local LLM Inference (llama.cpp, JNI) | **HIGH** | llama.cpp Android arm64 builds confirmed. JNI bridge pattern proven (community ChatGPT Android apps). Build-from-source with CMake is standard. |
| Hugging Face Integration | **HIGH** | HF Hub API is stable REST. Custom Retrofit client is straightforward. |
| Security (Keystore, EncryptedSharedPreferences) | **HIGH** | security-crypto 1.1.0 stable. Keystore is platform API. |
| Testing (JUnit 5, MockK, Turbine, Truth) | **HIGH** | All mature, actively maintained libraries. |
| Build Tooling (AGP 9, Gradle, version catalog) | **MEDIUM** | AGP 9 required by Hilt 2.59.x. Gradle 9.1+ inferred — verify against AGP compatibility table before freezing. |
| KSP version | **MEDIUM** | Version `2.1.10-1.0.31` inferred from Dagger 2.56 notes. Verify against KSP releases. |
| Material 3 / Compose BOM | **MEDIUM** | BOM version estimated. Verify latest stable at Compose BOM mapping page. |
| Feature scope completeness | **MEDIUM** | 17 features identified. Edge cases may emerge during implementation. 4 open architecture questions need benchmarking spikes. |
| Device compatibility breadth | **MEDIUM** | Tested conceptually on arm64-v8a. Real-world GPU/SoC variance may surface issues during QA. |

---

## Appendix: Open Architecture Questions

1. **GPU delegate**: Vulkan vs. NNAPI vs. CPU-only on Snapdragon 8 Gen 2/3 — needs benchmarking spike
2. **Quantization floor**: Should app enforce minimum Q4_K_M for 7B on 8GB RAM, or just warn?
3. **Model cache eviction**: Auto-delete stale models on low storage or leave to user?
4. **Android 15+ 16KB page size**: llama.cpp must build with `-DLLAMA_NO_ALIGNMENT` — confirm JNI build flags

---

*Summary feeds into: requirements specification, ROADMAP.md milestone planning, and phase-level PLAN.md documents.*
