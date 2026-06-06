# Phase 40: Runtime & Allowlist Foundation - Context

**Gathered:** 2026-06-06
**Status:** Ready for planning

<domain>
## Phase Boundary
Phase 40 unifies the local LiteRT-LM and remote LM Studio chat paths behind one streaming API (`LlmModelHelper`), ships a Gallery-schema model allowlist asset, applies the proven manifest and library bumps, and hardens R8 — so every v2.0 feature (Phases 41-44) shares a single inference surface and the app stops stalling on first chat.

15 requirements total: RUNTIME-01..12 (12) + CACHE-01..03 (3).

This is the **v2.0 keystone** — every other v2.0 phase depends on the `LlmModelHelper` interface landing.
</domain>

<decisions>
## Implementation Decisions

### the agent's Discretion
All implementation choices are at the agent's discretion — pure infrastructure/keystone phase. Use ROADMAP phase goal, success criteria, REQUIREMENTS.md, and existing codebase conventions to guide decisions. Prior decisions in STATE.md and the v1.5-v1.8 milestone history (LiteRT-LM pivot, Gallery alignment, no kapt, etc.) provide guardrails.

### Reference: google-ai-edge/gallery v1.0.16
The `LlmModelHelper` interface shape mirrors Gallery's runtime/llm/LlmModelHelper.kt (5 methods). Warped is the **convergence target** — Gallery anti-patterns (kapt, Moshi, Gson, kotlin-reflect, Firebase, Ktor, MCP, compose-richtext, Proto DataStore, CameraX, TFLite, mlkit-genai, AppAuth) are **explicitly rejected** and verified by the dependency audit script (RUNTIME-12).
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`LiteRTLmProvider`** (`app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt`, 314 lines) — already wraps LiteRT-LM `Engine`/`Conversation`, implements `LlmProvider`, has per-call retry on engine errors, conversation reuse pattern (LRT-02), image/audio content support. Will be wrapped by `LiteRtLlmHelper` implementing `LlmModelHelper`.
- **`LMStudioProvider`** (`app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt`, 352 lines) — wraps LM Studio v1 REST API, SSE parsing, reasoning delta handling, multimodal input (text + image). Will be wrapped by `LmStudioHelper` implementing `LlmModelHelper` with `stopResponse` that cancels both Flow and underlying Call.
- **`ChatViewModel`** (`app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`, 844 lines) — depends on `ProviderRouter`, calls `providerRouter.resolveLocal()` or `providerRouter.resolve()` per send. Will be refactored to depend on `LlmModelHelper` via the interface, with `ProviderRouter` becoming a factory that returns the right `LlmModelHelper`.
- **`EngineManager`** (`app/src/main/java/com/warped/data/local/inference/EngineManager.kt`, 155 lines) — currently copies model file to `context.cacheDir/litertlm_cache/`. Will be refactored to **mmap-only** (CACHE-01): no file copy, just `EngineConfig.cacheDir = context.cacheDir.absolutePath/<version>`.
- **`HuggingFaceViewModel`** (`app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt`) — has `loadRecommended()` querying HF search API. Will be refactored to read `assets/model_allowlist.json` instead.
- **`AndroidManifest.xml`** (`app/src/main/AndroidManifest.xml`) — has `largeHeap="true"`, `configChanges="orientation|screenSize|..."` (missing `uiMode` for dark-mode recreate avoidance), `uses-native-library` for libvndksupport + libOpenCL (missing libcdsprpc). Splash theme already set.
- **`MainActivity.kt`** (`app/src/main/java/com/warped/MainActivity.kt`, 37 lines) — no `installSplashScreen()` call. Will add SplashScreen API integration.
- **`gradle/libs.versions.toml`** — has older versions; needs bumps per RUNTIME-09.
- **`app/proguard-rules.pro`** — has LiteRT-LM keep rules, kotlinx-serialization rules, Room rules. Will add MessageCallback, ToolProvider, full R8 mode.
- **`gradle.properties`** — `-Xmx2048m`, has Kotlin incremental, missing `android.nonTransitiveRClass=true`.

### Established Patterns
- **Hilt DI** at `SingletonComponent` scope; `@Binds @Singleton` for interface→impl bindings, `@Provides` for object construction.
- **Repository pattern**: interface in `domain/repository/`, impl in `data/repository/`, binding in `di/RepositoryModule.kt`.
- **`ProviderRouter`** as factory — created per-endpoint for remote, lazy for local.
- **`StreamToken` sealed interface**: `Delta(content)`, `Done(stats, reasoning)`, `Error(message)` — the common currency for streaming.
- **`Result<T>`** for non-streaming RPC ops, `Flow<StreamToken>` for streaming.
- **kotlinx-serialization** for DTOs with `@Serializable` + `@SerialName` for snake_case.
- **Type-safe nav** via Navigation-Compose 2.8.8 with route strings + `NavType.LongType`/`BoolType`. Will migrate to `@Serializable` destinations (RUNTIME-07).
- **Timber logging** with `RedactingTree` for API key/secret redaction.
- **StrictMode** in debug only.
- **WorkManager** with `@HiltWorker` + `HiltWorkerFactory`; default initializer disabled via `tools:node="remove"`.

### Integration Points
- **DI module**: `di/InferenceModule.kt` — extend with `LlmModelHelper` `@Binds` binding + `LiteRtLlmHelper`/`LmStudioHelper` providers.
- **DI module**: `di/ProviderModule.kt` — `ProviderRouter` switches to factory method returning `LlmModelHelper`.
- **New domain layer file**: `app/src/main/java/com/warped/domain/llm/LlmModelHelper.kt` (or `app/src/main/java/com/warped/domain/provider/LlmModelHelper.kt`).
- **New impl files**: `app/src/main/java/com/warped/data/local/inference/LiteRtLlmHelper.kt`, `app/src/main/java/com/warped/data/remote/provider/LmStudioHelper.kt`.
- **New repo**: `app/src/main/java/com/warped/data/repository/ModelAllowlistRepository.kt` + interface in `domain/repository/`.
- **New assets**: `app/src/main/assets/model_allowlist.json` (Gallery schema).
- **New screen packages** (only type-safe nav destinations, no new screens in this phase — they are wired in Phases 41/42).
- **Existing nav**: `ui/navigation/Screen.kt` + `ui/navigation/NavGraph.kt` — migrate to `@Serializable` destinations.
</code_context>

<specifics>
## Specific Ideas
- **LlmModelHelper signature** (RUNTIME-01): `interface LlmModelHelper { suspend fun initialize(modelPath: String); fun runInference(request: ChatRequest, enableThinking: Boolean = false): Flow<StreamToken>; fun resetConversation(); fun cleanUp(); fun stopResponse() }`. `initialize()` is `suspend` so callers can `await` it; the impl switches to `Dispatchers.IO` internally (RUNTIME-02).
- **ProviderRouter refactor**: keep `@Singleton`, but `resolve()`/`resolveLocal()` return `LlmModelHelper` instead of `LlmProvider`. The legacy `LlmProvider` interface can be deprecated or kept for `listModels()`/`testConnection()` only.
- **Cache path (CACHE-01)**: `EngineConfig.cacheDir = context.cacheDir.absolutePath + "/litertlm/" + BuildConfig.LITERTLM_VERSION` — namespaced by LiteRT-LM version so upgrades silently invalidate. Add `buildConfigField` to expose `LITERTLM_VERSION` from `libs.versions.toml`.
- **Cache LRU eviction (CACHE-02)**: a small `CacheManager` class that scans `cacheDir/litertlm/<version>/` for last-access times, evicts oldest until under cap (default 500MB). Wired into `EngineManager.handleTrimMemory(level)` for CACHE-03 and exposed via `AdvancedPreferences.cacheMaxSizeMb`.
- **SplashScreen (PERF-11 / RUNTIME-08)**: add `androidx.core:core-splashscreen:1.2.0-beta01` to `libs.versions.toml`, call `installSplashScreen()` in `MainActivity.onCreate()` before `super`, use `SplashScreenViewProvider` to keep the splash until the first frame. Update `Theme.Warped.Splash` parent to `Theme.SplashScreen`.
- **Dark mode no-recreate (RUNTIME-08)**: add `uiMode` to `android:configChanges` in `<activity>`. WarpedTheme already supports light/dark via `isSystemInDarkTheme()`.
- **Type-safe nav (RUNTIME-07)**: define `@Serializable data object Chat : NavKey`, `data class ChatDetail(val id: Long) : NavKey`, etc. for the 7 routes: chat, chat/{id}, models, endpoints, settings, huggingface, promptlab, benchmark. Use `NavHost(navController, startDestination = Chat)` with `composable<Chat> { ... }`. Drop the legacy `Screen.kt` sealed class.
- **Dependency audit (RUNTIME-12)**: a Gradle task `auditDependencies` that runs `./gradlew :app:dependencies` and greps for banned patterns: `kapt`, `firebase`, `moshi`, `gson`, `kotlin-reflect`, `ktor`, `mcp`, `tflite`, `mlkit-genai`, `appauth`, `compose-richtext`, `camerax`, `datastore.*proto`. Wired as `./gradlew check` dependency. Script lives at `scripts/audit-dependencies.sh` and is invoked from `app/build.gradle.kts` via a `tasks.register("auditDependencies")`.
- **Library version bumps (RUNTIME-09)**: compose-bom 2026.04.01 → 2026.05.01; hilt-navigation-compose 1.2.0 → 1.3.0; hilt-work 1.2.0 → 1.3.0; room 2.7.1 → 2.8.4; lifecycle 2.8.7 → 2.10.0; navigation 2.8.8 → 2.9.x; litertlm 0.13.0 → 0.13.1. Add `lifecycle-process:2.10.0`, `core-splashscreen:1.2.0-beta01`, `kotlinx-collections-immutable:0.4.0`. Bump ksp to match new Kotlin (2.3.x). Note: AGP 9 + Kotlin 2.3.20 are already in the catalog — only add the new ones.
- **R8 full mode (RUNTIME-11)**: in `app/build.gradle.kts` set `release { isMinifyEnabled = true; isShrinkResources = true; ... }` and add `android.enableR8.fullMode=true` to `gradle.properties`. Keep rules extend to `MessageCallback`, `ToolProvider` (in `com.google.ai.edge.litertlm`), kotlinx-serialization `$$serializer` companions.
- **Allowlist schema (RUNTIME-05)**: 8 hand-curated entries (Gemma 3 1B/4B, Llama 3.2 1B/3B, Qwen 2.5 1.5B/3B, DeepSeek-R1-Distill-Qwen 1.5B, Phi-4 Mini). Each entry has: `name` (HF id), `displayName` (user-facing), `modelFile` (filename in HF repo), `sizeInBytes`, `capabilities: ["llm_chat", "llm_thinking", ...]`, `llmPromptTemplates: { user: "<start_of_turn>user\n{prompt}<end_of_turn>\n...", model: "..." }`, `taskTypes: ["llm_chat", "llm_thinking", ...]`.
- **ModelAllowlistRepository (RUNTIME-06)**: `@Singleton`; reads JSON once on init, parses via kotlinx-serialization, exposes `getAll(): List<AllowlistEntry>`, `findById(modelId): AllowlistEntry?`, `supportsThinking(modelId): Boolean`, `supportsSpeculativeDecoding(modelId): Boolean`. Used by `HuggingFaceViewModel` for the Recommended tab.
- **gradle.properties (RUNTIME-10)**: add `android.nonTransitiveRClass=true`, bump `org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED`, add `kotlin.incremental=true`, add `android.enableR8.fullMode=true`.
- **AndroidManifest (RUNTIME-08)**: change `configChanges` to `uiMode|orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden`. Add `extractNativeLibs="false"` to `<application>`. Add third `uses-native-library` for `libcdsprpc.so`. `largeHeap="true"` already set; `theme="...SplashScreen"` already set.
</specifics>

<deferred>
## Deferred Ideas
None — keystone phase, scope is locked by requirements. All Gallery anti-patterns are explicitly rejected and gated by RUNTIME-12.
</deferred>
