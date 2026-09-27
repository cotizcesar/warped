# Architecture Research — Warped v2.0 Gallery Convergence & Performance Overhaul

**Domain:** Android on-device LLM chat (Kotlin + Jetpack Compose + LiteRT-LM + LM Studio)
**Researched:** 2026-06-05
**Confidence:** **HIGH** for file/pattern references (direct GitHub source inspection); **MEDIUM** for impact estimates (no device profiling yet on Warped hardware)

**Reference implementation:** [google-ai-edge/gallery](https://github.com/google-ai-edge/gallery) v1.0.16 main branch (May 2026), 23.6k stars, 91.9% Kotlin. Warped is the "LiteRT-LM + Compose + LM Studio remote" cousin of Gallery; convergence is the cheapest available architectural improvement path.

---

## Executive Summary

Gallery and Warped are far more architecturally similar than different. Both are single-Activity Compose apps with Hilt, Room, DataStore, WorkManager, OkHttp/Retrofit, kotlinx-serialization, and a local LiteRT-LM inference engine. Both are clean-architecture MVVM. Where they diverge, it is in **abstraction quality** (interface boundaries), **state ownership** (mega-VM vs per-screen VMs), and **engine plumbing** (one `LlmModelHelper` vs two parallel providers).

The highest-leverage v2.0 refactors are:

1. **Introduce `LlmModelHelper`-style interface in `data/runtime/`** that unifies `LiteRTLmProvider` and `LMStudioProvider` behind a single `chat()`-style API. This is the keystone — Thinking Mode, Model Benchmark, Prompt Lab, and Agent Skills all depend on it.
2. **Replace the file-copy "model cache"** in `EngineManager` with Gallery's mmap-via-`cacheDir` pattern. Warped currently *copies* the model file to `cacheDir/litertlm_cache/` *and* passes the cache dir to `EngineConfig`. Only the mmap is needed; the copy is dead I/O.
3. **Add `GalleryLifecycleProvider`-style `AppLifecycleProvider`** in `domain/lifecycle/` and hook it from `MainActivity` via `LifecycleEventObserver` to suppress download notifications when foregrounded. Warped has no such gate today.
4. **Adopt `installSplashScreen()` + cross-fade mask** in `MainActivity` to eliminate cold-start flash. Plus `android:configChanges="uiMode"` in manifest to skip Activity recreate on dark-mode toggle.
5. **Per-task module pattern** (Gallery's `LlmChatTaskModule` + `CustomTask` interface, registered via `@IntoSet`). This is the seam v2.0 features (Prompt Lab, Benchmark, Skills) need to slot in without bloating `ChatViewModel` (already 844 lines).
6. **Versioned `assets/model_allowlist.json`** (see FEATURES.md) — replaces the v1.8 Kotlin `RecommendedModels` constant with Gallery's typed schema (`name`, `displayName`, `sizeInBytes`, `capabilities`, `llmPromptTemplates`).
7. **Compose performance audit** on `MessageBubble`, `CodeBlock`, `ChatInputBar`, `ConversationList`, and the 3 `LazyColumn`s (`ModelsList`, `EndpointsList`, `Recents` in the drawer) — `derivedStateOf` for computed fields, stable lambdas, `key()` in lazy lists, immutable `MessageBubble` data class.

The lowest-value changes (skip or defer) are Gallery's runBlocking DataStore reads (`DefaultDataStoreRepository` is annotated with `// TODO(b/423700720): Change to async (suspend) functions` — it is tech debt Gallery has not fixed), the `firebaseAnalytics` per-screen event calls, and Gallery's mega-`ModelManagerViewModel` (Warped's per-screen VMs are better — keep them).

---

## Reference: Gallery's Architecture at a Glance

| Layer | Gallery file / pattern | Notes |
|-------|------------------------|-------|
| Application | `GalleryApplication.kt` (63 lines) | `@HiltAndroidApp`. Injects `DataStoreRepository` and `NotificationScheduleManager` into `onCreate`. Calls `ThemeSettings.themeOverride.value = dataStoreRepository.readTheme()` — note the synchronous DataStore read on the main thread. |
| Activity | `MainActivity.kt` (198 lines) | Single `ComponentActivity`. Uses `installSplashScreen()` with custom cross-fade mask. `super.onCreate(null)` to force Home Screen. `enableEdgeToEdge()` + `FLAG_KEEP_SCREEN_ON`. `LifecycleEventObserver` in nav graph drives `AppLifecycleProvider.isAppInForeground`. |
| Top-level | `GalleryApp.kt` (24 lines) | Single `@Composable` that wraps `GalleryNavHost(navController, modelManagerViewModel)`. |
| Navigation | `ui/navigation/GalleryNavGraph.kt` (430 lines) | `NavHost` with 6 routes (home, model_list, model, model_manager, benchmark, notifications). Custom `slideIntoContainer` animations. Deep-link handling. |
| DI | `di/AppModule.kt` (1 file, ~150 lines) | Single `object AppModule` with `@Provides @Singleton` for 5 `DataStore<T>` instances, `DataStoreRepository`, `DownloadRepository`, `AppLifecycleProvider`, `Moshi`. **No per-feature modules**. |
| Per-feature DI | `ui/llmchat/LlmChatTaskModule.kt`, etc. | Each `CustomTask` has its own `object LlmChatTaskModule { @Provides @IntoSet fun provideTask(): CustomTask = LlmChatTask() }` — feature modules are *the mechanism for adding features*. |
| State | `ModelManagerViewModel.kt` (~850 lines) + `ModelManagerUiState` (data class) | One mega-VM owns: tasks, models, downloads, initialization, theme, history, auth. **Single source of truth** for the entire app. |
| Engine | `runtime/LlmModelHelper.kt` (interface, 5 methods) + `LlmChatModelHelper.kt` (singleton `object`) | `initialize, runInference, resetConversation, cleanUp, stopResponse`. `ResultListener` and `CleanUpListener` typealiases. |
| Local LLM runtime | `LlmChatModelHelper.kt` (340 lines) | Wraps `Engine` + `Conversation` from `com.google.ai.edge.litertlm`. Stores `LlmModelInstance(engine, conversation)` on `model.instance`. |
| Model data | `data/Model.kt` (440 lines) | Rich metadata: `capabilities: List<ModelCapability>`, `accelerators`, `configs`, `runtimeType`, `extraDataFiles`, `getPath(Context, fileName)`. **Mutable fields on the data class** (e.g. `var instance: Any? = null`, `var initializing: Boolean = false`) — code smell. |
| Tasks | `data/Tasks.kt` (190 lines) | `data class Task(id, label, category, models, modelNames, ...)`. `BuiltInTaskId` sealed constants. `isLegacyTasks(id)` helper. |
| Allowlist | `model_allowlists/1_0_15.json` | Versioned by `BuildConfig.VERSION_NAME`. Loaded by `ModelManagerViewModel.loadModelAllowlist()` from disk cache → GitHub raw → bundled asset fallback. |
| Download | `data/DownloadRepository.kt` (~280 lines) + `worker/DownloadWorker.kt` | WorkManager-based. `getWorkInfoByIdLiveData(workerId).observeForever { }` to drive UI. SharedPreferences for download start times. Notifications gated by `lifecycleProvider.isAppInForeground`. |
| Persistence | 5 `DataStore<T>` (Proto) | `Settings`, `UserData`, `CutoutCollection`, `BenchmarkResults`, `Skills`. Each gets a `Serializer<T>`. Default impl uses `runBlocking { dataStore.data.first() }` — synchronous reads, on whatever thread calls. |
| Custom task | `customtasks/common/CustomTask.kt` | Interface with `task: Task`, `initializeModelFn`, `cleanUpModelFn`, `@Composable MainScreen(data)`. |
| Sample task | `customtasks/llmchat/LlmChatTask.kt` | One Hilt module per task. `MainScreen` uses `LlmChatScreen(modelManagerViewModel, viewModel, ...)`. |

**Key takeaway:** Gallery's design is *one big VM + one big repo + one big DataStore + many tiny per-task modules*. The per-task modules are the elegant part; the rest is a maintainability time bomb.

---

## Warped Current Architecture (treated as ground truth)

| Layer | Warped file / pattern |
|-------|-----------------------|
| Application | `WarpedApplication.kt` (108 lines) — `@HiltAndroidApp`, `Configuration.Provider`, `onTrimMemory` → `engineManager.handleTrimMemory`, custom `RedactingTree` for Timber. |
| Activity | `MainActivity.kt` (37 lines) — `enableEdgeToEdge()` only; no splash, no cross-fade. **No `AppLifecycleProvider` integration.** |
| Top-level | `WarpedNavGraph()` directly inside `setContent` in `MainActivity`. |
| Navigation | `ui/navigation/NavGraph.kt` (404 lines) + `ui/navigation/Screen.kt`. Single `NavHost`, 7 routes, drawer pattern, `EntryPointAccessors.fromApplication(...)` to grab singletons in Composable scope. |
| DI | `di/` (8 modules): `DatabaseModule`, `NetworkModule`, `InferenceModule`, `ProviderModule`, `RepositoryModule`, `SecurityModule`, `SyntaxModule`, `HuggingFaceModule`. **Per-feature, organized by concern, not by feature**. |
| State | 9 separate `ViewModel`s each with their own `UiState` (sealed/data class): `ChatViewModel` (844 lines, 50-field `ChatUiState`), `ModelsViewModel`, `EndpointsViewModel`, `HuggingFaceViewModel`, `PresetsViewModel`, `SettingsViewModel`, `WizardViewModel`, `UnifiedSelectorViewModel`, `ToolSettingsViewModel`. |
| Shared state | `domain/model/ActiveModelSelection.kt` (singleton with `MutableStateFlow`s) — the one cross-screen state holder. |
| Engine | `data/local/inference/EngineManager.kt` (155 lines) + `LiteRTLmEngine.kt` (132 lines) + `LiteRTLmProvider.kt` (314 lines). `EngineManager` does the file copy + LiteRT-LM lifecycle. `LiteRTLmProvider` implements `LlmProvider` (the domain interface). |
| Remote | `data/remote/provider/ProviderRouter.kt` (62 lines) + 5 providers (`OpenAIProvider`, `AnthropicProvider`, `OllamaProvider`, `LMStudioProvider`, `CustomProvider`). Each implements `LlmProvider`. |
| Domain | `domain/provider/LlmProvider.kt` interface + `domain/repository/*` interfaces (5 of them) + `domain/model/*` (10+ data classes). |
| Persistence | Room (`AppDatabase` + 5 DAOs) + `DataStore` (preferences) + `EncryptedSharedPreferences` (API keys). |
| Download | `data/local/download/` — `WorkManager`-based. |
| Theme | `ui/theme/WarpedTheme.kt` + `AdvancedPreferences` for user settings. |
| Allowlist | `RecommendedModels` Kotlin constant in code (v1.8). |

**Key takeaway:** Warped has *good* layering (clean architecture, per-screen VMs, repository pattern) but pays the cost in chat — `ChatViewModel` does too many things because the underlying `LlmProvider` interface is too narrow (`Flow<StreamToken>` only, no thinking, no tools, no benchmark).

---

## Side-by-Side Architectural Comparison

| Concern | Gallery | Warped | Verdict |
|---------|---------|--------|---------|
| **Activity count** | 1 (MainActivity) | 1 (MainActivity) | Match — keep. |
| **Splash / cold start** | `installSplashScreen()` + cross-fade mask | None | Gallery wins. **MUST REFACTOR**. |
| **Dark-mode recreate** | `android:configChanges="uiMode"` | Recreates Activity | Gallery wins. **MUST REFACTOR (manifest)**. |
| **Native libs in manifest** | `<uses-native-library>` for `libvndksupport`, `libOpenCL`, `libcdsprpc` | None | Gallery wins — required for LiteRT-LM GPU. **MUST REFACTOR (manifest)**. |
| **DI count** | 1 `AppModule` + N `*TaskModule` (1 per feature) | 8 modules (Database/Network/Inference/Provider/Repository/Security/Syntax/HuggingFace) | Warped's per-concern split is **cleaner for testing**; Gallery's per-task split is **better for feature addition**. **CONSIDER partial merge**. |
| **State ownership** | One `ModelManagerViewModel` (850 lines, single `ModelManagerUiState`) | 9 ViewModels + 1 shared `ActiveModelSelection` | Warped wins on modularity; Gallery wins on "single source of truth." Both work. **KEEP Warped's pattern**, but **extract `ActiveModelSelection` patterns to a `StateManager` interface** for testability. |
| **`StateFlow<UiState>`** | `ModelManagerUiState` (data class) | `ChatUiState` (data class, 40+ fields) | Match — keep. **MUST REFACTOR: reduce `ChatUiState` field count via composition**. |
| **Engine interface** | `LlmModelHelper { initialize, runInference, resetConversation, cleanUp, stopResponse }` (5 methods, image/audio aware) | `LlmProvider { chat(request): Flow<StreamToken>, listModels(), testConnection(), loadModel(), unloadModel() }` (provider-agnostic) | Different abstraction levels. Gallery's is *engine*-level (talks to a single model), Warped's is *provider*-level (talks to a source). **MUST REFACTOR: add `LlmModelHelper`-style interface in `data/runtime/`, wrap `LiteRTLmProvider` and add `LMStudioModelHelper`** — see "MUST REFACTOR" #1. |
| **Model data class** | `Model` with mutable `var instance: Any? = null` and `var initializing: Boolean` | `LocalModel` (Room entity, immutable from DB; runtime state in `ActiveModelSelection` and `EngineManager`) | **Warped's separation is cleaner**. KEEP. **KEEP**. |
| **Conversation model** | `LlmChatViewModelBase` (abstract, shared by `LlmChatViewModel`, `LlmAskImageViewModel`, `LlmAskAudioViewModel`) | `ChatViewModel` (concrete) | Gallery's pattern enables per-task VMs that share logic. **CONSIDER for Prompt Lab + Benchmark**. |
| **Lifecycle observation** | `GalleryLifecycleProvider` (`var isAppInForeground`) + `LifecycleEventObserver` in nav graph | `onTrimMemory` only; **no foreground/background awareness** | Gallery wins on this specific point. **MUST REFACTOR**. |
| **Model cache** | `EngineConfig.cacheDir = context.getExternalFilesDir(null)?.absolutePath` (mmap, no copy) | `EngineManager.getCachedModelPath()` does a `sourceFile.copyTo(cachedFile)` + passes the copy path to `EngineConfig.cacheDir` (file copy + mmap) | Gallery's pattern is strictly better. **MUST REFACTOR**. |
| **Engine warmup** | `ModelManagerViewModel.initializeModel()` launched from `LaunchedEffect` on download completion, with `cleanUpAfterInit` flag for race | `EngineManager.switchToLiteRT()` called from `ChatViewModel.preloadLocalModel()`; `activeModelSelection.connectLocal()` flips state | Match. KEEP. |
| **Conversation reset** | `LlmModelHelper.resetConversation(...)` — clean | `LiteRTLmProvider.resetConversation()` — clean | Match. KEEP. |
| **`LlmModelHelper` keys** | `model.instance` is `Any?` (anti-type-safety) | `EngineManager.getActiveEngine(): ActiveEngine?` is a typed data class | **Warped's typed approach is better**. KEEP. |
| **Repository count** | 1 (`DataStoreRepository` — does 5 things) | 5 (`ChatRepository`, `EndpointRepository`, `LocalModelRepository`, `PresetRepository`, `HuggingFaceRepository`-via-DI) | **Warped wins**. KEEP. |
| **Use-case layer** | None | None | Match — YAGNI for both. SKIP. |
| **DataStore** | 5 separate Proto `DataStore`s (`Settings`, `UserData`, `CutoutCollection`, `BenchmarkResults`, `Skills`) + `runBlocking { dataStore.data.first() }` (synchronous) | `androidx.datastore.preferences.core.Preferences` for simple settings, Room for structured | Warped is simpler and more correct. **KEEP Warped's approach** (Preferences + Room) for v2.0. |
| **Proto** | 5 `.proto` files in `proto/` | None | Out of scope. KEEP. |
| **Model allowlist** | `assets/model_allowlists/1_0_15.json` (versioned) | `RecommendedModels` Kotlin constant | FEATURES.md covers this. **MUST REFACTOR (in features phase)**. |
| **Splash screen library** | `androidx.core:core-splashscreen` | None | **MUST REFACTOR (already in STACK.md)**. |
| **CustomTask extensibility** | `interface CustomTask` + `IntoSet` Hilt binding | None | **MUST REFACTOR for Phase 42 (Prompt Lab)**. |
| **Per-task Hilt module** | One `object LlmChatTaskModule` per task | None | **MUST REFACTOR for v2.0 feature addition**. |
| **Compose state stability** | `data class` everywhere; `LaunchedEffect` keyed on `curDownloadStatus, selectedModel.name`; `BackHandler { handleNavigateUp() }` | `data class` everywhere; 50-field `ChatUiState` (composition smell); `LaunchedEffect` used correctly | **Warped is mostly correct; needs stability audit on `MessageBubble` and `CodeBlock`**. |
| **`derivedStateOf`** | Used in `GalleryNavGraph` (e.g. `lastNavigatedModelName` for nav guard) | Not used; `_uiState.value.X` accessed directly in many places | **MUST REFACTOR on hot paths**. |
| **`@Stable` / `@Immutable`** | Not explicitly annotated; relies on Compose's heuristics | Not explicitly annotated | **CONSIDER adding to `ChatUiState`, `ChatMessage`, `LocalModel`, `Endpoint`** — these are read in `LazyColumn` items, so stability matters. |
| **Lazy list keys** | Uses `model.name` (stable string) | Uses `conv.id` (stable Long) for recents — correct | Match. KEEP. |
| **Notification suppression** | `if (lifecycleProvider.isAppInForeground) return` in `DownloadRepository.sendNotification` | Notifications always shown (no gate) | **MUST REFACTOR**. |
| **Deep links** | `com.google.ai.edge.gallery://model/<taskId>/<modelName>` and `…://global_model_manager` | None | **CONSIDER for v2.1+** (not v2.0). |
| **`<uses-native-library>`** | Declared for GPU/NPU/DSP | None | **MUST REFACTOR (manifest)**. |
| **`enableEdgeToEdge()`** | Yes + `isNavigationBarContrastEnforced = false` | Yes | Match. KEEP. |
| **`FLAG_KEEP_SCREEN_ON`** | Yes (for demos) | No | SKIP — Warped is a chat app, not a demo. |
| **FCM / Firebase** | Yes (push, analytics, deep links) | No | Out of scope. KEEP. |
| **FcmMessagingService** | Yes | No | Out of scope. KEEP. |
| **Moshi + Gson + kotlinx-serialization** | All three (Firebase uses Moshi, allowlist uses Gson, everything else uses kotlinx-serialization) | kotlinx-serialization only | Warped wins. KEEP. |
| **kapt** | Used for Hilt + Room | KSP only | Warped wins. KEEP. |
| **`runBlocking` on main thread** | `runBlocking { dataStore.data.first() }` in every `read*` of `DataStoreRepository` | None — Warped uses `Flow.first()` in coroutines | Gallery has a perf bug here. **Warped avoids it** — keep. |
| **Per-screen `rememberSaveable`** | Used for nav route, promo id, etc. | Used for `activeConversationId` and `savedRoute` in `NavGraph` | Match. KEEP. |
| **Pure-Kotlin domain models** | `data class` in `data/` (some have Android dependencies via `getPath(Context, ...)`) | Pure-Kotlin `data class` in `domain/model/` | **Warped wins** — clean architecture is preserved. KEEP. |
| **Inferred `runtimeType` on `Model`** | `RuntimeType { UNKNOWN, LITERT_LM, AICORE }` with `model.runtimeHelper` extension property that switches impl | Hard-coded `ProviderType` enum on `Endpoint` | Both work. Gallery's extension property is a neat trick. **CONSIDER for v2.0**. |

---

## Findings by Focus Area

### 1. DI Module Organization

**Gallery's pattern:** One `object AppModule` with ~10 `@Provides @Singleton` bindings (5 `DataStore<T>` + their `Serializer<T>` + repos + lifecycle provider + Moshi). Per-task modules are tiny — each `*Task.kt` has its own `object *TaskModule { @Provides @IntoSet fun provideTask(): CustomTask = *Task() }`.

**Warped's pattern:** 8 modules organized by **concern** (Database, Network, Inference, Provider, Repository, Security, Syntax, HuggingFace). `RepositoryModule` uses `@Binds` for `interface → impl` mapping. `InferenceModule` mixes `@Provides` and `@Binds`.

**Difference:** Gallery's per-task modules are the **feature extensibility mechanism** — adding a feature is one new module + one new `@IntoSet` binding. Warped's per-concern modules are **easier to grep** but adding a feature requires touching multiple modules.

**Verdict:** **Warped's approach is fine for what it does.** The recommendation is to **add per-feature DI modules for v2.0 new features** (Prompt Lab, Benchmark, Skills) following Gallery's pattern — each new screen/screen-group gets its own `*FeatureModule` and its own `*FeatureComponent` scope. This is a partial merge: keep the per-concern modules, add per-feature modules alongside.

**Integration point in Warped:**
- `app/src/main/java/com/warped/di/` — keep existing 8 modules.
- New: `app/src/main/java/com/warped/ui/promptlab/di/PromptLabModule.kt` (Phase 42).
- New: `app/src/main/java/com/warped/ui/benchmark/di/BenchmarkModule.kt` (Phase 41).
- New: `app/src/main/java/com/warped/skills/di/SkillsModule.kt` (Phase 44, optional).

**Complexity:** S (per-feature module is ~15 lines).

---

### 2. State Management & ViewModel Patterns

**Gallery's pattern:** One `ModelManagerViewModel` owns **everything**:
- `tasks: List<Task>`
- `modelDownloadStatus: Map<String, ModelDownloadStatus>`
- `modelInitializationStatus: Map<String, ModelInitializationStatus>`
- `selectedModel: Model`
- `textInputHistory: List<String>`
- `configValuesUpdateTrigger: Long`
- `loadingModelAllowlist: Boolean`

Shared across all screens via `hiltViewModel<ModelManagerViewModel>()` at the activity scope. Child VMs (`LlmChatViewModel`, `LlmAskImageViewModel`) are scoped to their nav back-stack entry and inject `ModelManagerViewModel` directly.

**Warped's pattern:** 9 separate ViewModels, each with its own `MutableStateFlow<UiState>`. Cross-screen state lives in `domain/model/ActiveModelSelection.kt` (a `@Singleton` with `MutableStateFlow`s for `activeModel`, `localSelection`, `remoteSelection`).

**Difference:** Gallery's mega-VM is **simpler to reason about** (one place to look) but **brittle** (one VM is responsible for 8+ screens, hard to test, hard to refactor). Warped's per-screen VMs are **modular** but pay the cost in `ChatViewModel` doing too much (it has 50+ fields in `ChatUiState`).

**Verdict:** **Warped's pattern is better** for maintainability. KEEP. The refactor opportunity is **internal to `ChatViewModel`**:
1. Split `ChatUiState` (50 fields, 2 deprecated) into a `sealed class ChatUiState` with `Loading`, `Idle(messages, conversationId, ...)`, `Generating(streaming, partial, ...)`, `Error(error)`. This is Gallery's pattern (different `ChatMessageType` constants).
2. Use `derivedStateOf` for computed fields (`trafficLightState`, `trafficLightStatusText`) — these currently recompute on every state update.
3. Add `@Immutable` to `ChatUiState` to opt out of Compose's stability introspection.

**Integration point in Warped:**
- `app/src/main/java/com/warped/ui/chat/ChatUiState.kt` — refactor.
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` — split out 3 derived helpers, reduce direct `_uiState.value.X` access in Composable (pass derived `State<T>` from VM instead).
- New: `app/src/main/java/com/warped/ui/chat/derived/TrafficLightState.kt` — use `derivedStateOf` inside Composables that read traffic light.

**Complexity:** M (touches the most-called VM; risk of breaking chat regression tests).

---

### 3. Navigation & Screen Composition

**Gallery's pattern:**
- 6 routes in `ui/navigation/GalleryNavGraph.kt`: `homepage`, `model_list`, `route_model/{taskId}/{modelName}?query={query}`, `model_manager`, `benchmark/{modelName}`, `notifications`.
- Custom slide-up/slide-down transitions via `slideIntoContainer(SlideDirection.Up/Down)`.
- Deep links via `com.google.ai.edge.gallery://model/<taskId>/<modelName>?query=...`.
- The `CustomTaskScreen` wrapper in `GalleryNavGraph` is a `@Composable` that adds a `Scaffold` with `ModelPageAppBar`, then renders the custom task's `MainScreen` inside.
- Route strings as private constants, **not** sealed `Screen` class.
- `super.onCreate(null)` to force fresh start (prevents Compose state restoration on OS kill).

**Warped's pattern:**
- 7 routes in `ui/navigation/NavGraph.kt`: `Chat`, `Models`, `HuggingFace`, `Presets`, `Settings`, `Help`, `Wizard`, `Selector`.
- `ModalNavigationDrawer` + bottom-bar pattern.
- `EntryPointAccessors.fromApplication(...)` to access `ChatRepository`, `ActiveModelSelection`, `EngineManager`, `WizardPreferences` from the composable.
- `Screen` sealed class with `route: String` for each.
- Wizard redirect for first-launch.
- `rememberSaveable` for `activeConversationId` and `savedRoute` to survive process death.

**Difference:**
- Gallery uses **route constants** (no sealed class). Warped uses **`Screen` sealed class** — better for refactoring.
- Gallery has **deep links**; Warped doesn't.
- Gallery has **per-task screen wrapper** that bakes in the app bar; Warped has **per-screen composables** that own their own bars.
- Warped has the **drawer pattern**; Gallery has a **home screen with category tabs** (no drawer).

**Verdict:** **Warped's navigation is correct for its scope** (chat app with HF browser and settings; not a multi-category showcase). KEEP. The actionable refactor is:

1. **Type-safe nav arguments** (Navigation 2.9.x `@Serializable` destinations) — verify `composable<Route> { ... }` pattern is used everywhere. Currently Warped uses string routes with `navArgument` extractors; should be migrated.
2. **Drop `EntryPointAccessors` in `NavGraph`** — pass dependencies as composable parameters or use `hiltViewModel()`. The current pattern works but is harder to test and breaks the "Composables are pure functions" rule.
3. **Adopt Gallery's `LifecycleEventObserver` pattern in `NavGraph`** to set `AppLifecycleProvider.isAppInForeground` (see #4 below).

**Integration point in Warped:**
- `app/src/main/java/com/warped/ui/navigation/NavGraph.kt` — extract dependencies, add lifecycle observer.
- `app/src/main/java/com/warped/ui/navigation/Screen.kt` — convert to `@Serializable` data classes (type-safe nav).
- `app/src/main/AndroidManifest.xml` — add `configChanges="uiMode"`, `<uses-native-library>` blocks (covered in #13 below).

**Complexity:** S for lifecycle observer; M for type-safe nav (touches every screen).

---

### 4. Model Loading & Lifecycle (engine warmup, conversation persistence)

**Gallery's pattern:**
- `GalleryLifecycleProvider` is a 1-field mutable boolean class: `var isAppInForeground: Boolean`.
- `GalleryApplication.onCreate` doesn't set it (Gallery binds via Activity observer).
- `MainActivity.onCreate` registers `LifecycleEventObserver` in `NavGraph` (via `DisposableEffect(lifecycleOwner) { ... }`) that flips the boolean on `ON_START`/`ON_STOP`.
- `ModelManagerViewModel.setAppInForeground(foreground)` is the bridge from Composable to provider.
- `Model.instance` is mutated directly: `model.instance = LlmModelInstance(...)` and `model.initializing = true/false`. There's a `cleanUpAfterInit` flag for the init-then-cleanup race.
- `ModelManagerViewModel.initializeModel(force=false)` is idempotent — re-entry is a no-op.

**Warped's pattern:**
- `EngineManager` is the single owner of engine state. `engineManager.switchToLiteRT(path)` is the only way to load a model; it is `@Synchronized`.
- `EngineManager.activeEngine: ActiveEngine?` is the typed state (vs Gallery's `Any?`).
- `EngineManager.handleTrimMemory(level)` releases the engine at `TRIM_MEMORY_RUNNING_CRITICAL` and clears the cache dir.
- `WarpedApplication.onTrimMemory` is the bridge from platform → `EngineManager`.
- `ChatViewModel.preloadLocalModel(filePath)` calls `engineManager.switchToLiteRT(filePath)` and updates `ActiveModelSelection.connectLocal(filePath, type)` on success.
- `LiteRTLmProvider` lazy-creates and **reuses** the `Conversation` across chat calls (LRT-02 fix from v1.8). The conversation is reset only on engine reload.
- **No `AppLifecycleProvider`** — Warped has no notion of foreground/background.

**Difference:**
- Warped's typed state (`ActiveEngine`) is **stricter** than Gallery's `model.instance: Any?`.
- Warped's `Conversation` reuse pattern is **correct** (LRT-02) and Gallery does the same.
- Gallery has the **foreground-aware download notification** gate; Warped does not.
- Gallery's `cleanUpAfterInit` race-handling is a **single boolean on the data class**; Warped uses `EngineManager`'s `@Synchronized` methods (different tradeoff — Warped blocks the caller; Gallery retries).

**Verdict:** **Warped's engine plumbing is correct and ahead of Gallery in type safety.** KEEP. The actionable refactor is to add `AppLifecycleProvider`:

1. New `domain/lifecycle/AppLifecycleProvider.kt` interface + `DefaultAppLifecycleProvider` impl.
2. Bind in `di/AppLifecycleModule.kt` (or add to `InferenceModule`).
3. `MainActivity.onCreate` → `lifecycle.addObserver(LifecycleEventObserver { ... setAppInForeground(true/false) })`.
4. `DownloadRepository` reads `appLifecycleProvider.isAppInForeground` before posting notification.

**Integration point in Warped:**
- New: `app/src/main/java/com/warped/domain/lifecycle/AppLifecycleProvider.kt`.
- New: `app/src/main/java/com/warped/data/local/download/AppLifecycleObserver.kt` (or wire into `WarpedApplication`).
- `app/src/main/java/com/warped/di/InferenceModule.kt` (or new `AppLifecycleModule.kt`) — `@Provides @Singleton`.
- `app/src/main/java/com/warped/WarpedApplication.kt` — call `appLifecycleProvider.setForeground(true)` from `onCreate` and wire to `Lifecycle.Event.ON_START/ON_STOP` in `MainActivity` (or use `ProcessLifecycleOwner` from `lifecycle-process` — STACK.md already adds this).
- `app/src/main/java/com/warped/data/local/download/DownloadWorker.kt` — read provider in `doWork()` before posting notification.

**Complexity:** S.

---

### 5. Inference Engine Wrapper/Manager — `LlmModelHelper` Interface

**Gallery's pattern:**
```kotlin
interface LlmModelHelper {
  fun initialize(context, model, taskId, supportImage, supportAudio,
                 onDone, systemInstruction, tools,
                 enableConversationConstrainedDecoding, coroutineScope)
  fun resetConversation(model, supportImage, supportAudio,
                        systemInstruction, tools,
                        enableConversationConstrainedDecoding, initialMessages)
  fun cleanUp(model, onDone)
  fun runInference(model, input, resultListener, cleanUpListener, onError,
                   images, audioClips, coroutineScope, extraContext)
  fun stopResponse(model)
}

typealias ResultListener = (partialResult: String, done: Boolean, partialThinkingResult: String?) -> Unit
typealias CleanUpListener = () -> Unit
```

The `partialThinkingResult` callback is the **seam for Thinking Mode**. The `tools: List<ToolProvider>` parameter is the seam for Agent Skills. The `extraContext: Map<String, String>` is the seam for speculative decoding, enableThinking flags, etc.

Implementation: `LlmChatModelHelper` is a Kotlin `object` (singleton). Multi-runtime dispatch via `val Model.runtimeHelper: LlmModelHelper` extension property that switches on `RuntimeType.AICORE` vs `LITERT_LM`.

**Warped's pattern:**
```kotlin
interface LlmProvider {
  val type: ProviderType
  fun chat(request: ChatRequest): Flow<StreamToken>
  suspend fun listModels(): Result<List<ModelInfo>>
  suspend fun testConnection(): Result<ConnectionStatus>
  fun loadModel(modelId: String): Result<String>     // LM Studio only
  fun unloadModel(instanceId: String)                 // LM Studio only
}
```

This is **provider-agnostic** (good for LM Studio vs LiteRT-LM), but **engine-level concepts are missing**:
- No `enableThinking` / `partialThinkingResult` plumbing → thinking must be parsed from `StreamToken.Delta` content in `ChatViewModel.parseThinkBlocks` (string regex hack).
- No `tools: List<ToolProvider>` plumbing → `LiteRTLmProvider.chat()` reads `toolRegistry.enabledToolIds.first()` (runBlocking!) every call.
- No `extraContext` → no per-call flags for speculative decoding, max tokens override, etc.
- `loadModel`/`unloadModel` are only used for LM Studio; for LiteRT-LM the engine is owned by `EngineManager`.

**Difference:** Gallery's interface is **engine-shaped** (talks to a specific model that has an instance), Warped's is **provider-shaped** (talks to a source of completions). They model different things.

**Verdict:** **MUST REFACTOR.** Add a `LlmModelHelper`-shaped interface that both `LiteRTLmProvider` and `LMStudioProvider` implement. This is the keystone refactor for v2.0.

Concretely:
1. New `domain/runtime/LlmModelHelper.kt` interface (mirror Gallery's, drop `image`/`audio` since PROJECT.md says multimodal is OOS).
2. `data/runtime/LiteRtLlmModelHelper.kt` — wraps `EngineManager` + `LiteRTLmEngine`. Owns the `Conversation` lifecycle.
3. `data/runtime/LmStudioModelHelper.kt` — wraps `LMStudioProvider`. State is server-side (LM Studio holds the loaded model).
4. `ChatViewModel` accepts `LlmModelHelper` (interface) instead of branching on `ProviderType`.
5. `ProviderRouter` becomes a `LlmModelHelper` factory.

**Integration point in Warped:**
- New: `app/src/main/java/com/warped/domain/runtime/LlmModelHelper.kt` (interface).
- New: `app/src/main/java/com/warped/data/runtime/LiteRtLlmModelHelper.kt`.
- New: `app/src/main/java/com/warped/data/runtime/LmStudioModelHelper.kt`.
- Modify: `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt` — keep as low-level LiteRT wrapper; `LiteRtLlmModelHelper` calls into it.
- Modify: `app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt` — return `LlmModelHelper` instead of `LlmProvider`.
- Modify: `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` — switch dependency from `ProviderRouter` to `LlmModelHelper` factory.
- Modify: `app/src/main/java/com/warped/di/InferenceModule.kt` + `di/ProviderModule.kt` — rebind.

**Complexity:** **L**. Touches the chat hot path; risk of breaking the LRT-02 (conversation reuse) fix and the runBlocking tool registry read. Estimated 7-10 days.

**Why this is MUST-REFACTOR (not consider):** without it, Thinking Mode (Phase 41), Model Benchmark (Phase 41), Prompt Lab (Phase 42), and Agent Skills (Phase 44) all need their own bespoke plumbing. This is the single biggest leverage point in v2.0.

---

### 6. Repository + Use-Case Structure

**Gallery's pattern:** One `DataStoreRepository` interface (50+ methods) with one `DefaultDataStoreRepository` impl. The interface is the union of every read/write the app does: `readTheme`, `saveSecret`, `addBenchmarkResult`, `getAllSkills`, `addViewedPromoId`, etc. No use-case layer.

**Warped's pattern:** 5 separate `domain/repository/*` interfaces (5-10 methods each), each with a `data/repository/*Impl` impl. No use-case layer.

**Difference:** Gallery's single-repo pattern is **faster to wire** (one place to add a method) but is **SRP violation** — `DefaultDataStoreRepository` is 400+ lines. Warped's per-domain repos are **SRP-compliant** and **easy to test** in isolation.

**Verdict:** **Warped wins.** KEEP. The only refactor is to add a sixth repo for v2.0 features:

- New: `domain/repository/BenchmarkRepository.kt` + `data/repository/BenchmarkRepositoryImpl.kt` (Phase 41).
- New: `domain/repository/ModelAllowlistRepository.kt` + `data/repository/ModelAllowlistRepositoryImpl.kt` (Phase 40, for the JSON asset).
- New: `domain/repository/SkillRepository.kt` + `data/repository/SkillRepositoryImpl.kt` (Phase 44, optional).

**Integration point in Warped:**
- `app/src/main/java/com/warped/domain/repository/` — new interfaces.
- `app/src/main/java/com/warped/data/repository/` — new impls.
- `app/src/main/java/com/warped/di/RepositoryModule.kt` — new `@Binds`.

**Complexity:** S per repo (M total across 3 repos).

**Use-case layer:** Neither project has one. YAGNI for both. SKIP.

---

### 7. Compose Performance Patterns (stability, remembered state, derivedStateOf, hoisting, key())

**Gallery's pattern:**
- `data class` everywhere — leverages Compose's auto-stability heuristics.
- No explicit `@Stable`/`@Immutable` annotations.
- `LaunchedEffect(key1, key2) { ... }` for keyed side effects (e.g. `LaunchedEffect(curDownloadStatus, selectedModel.name) { initializeModel(...) }`).
- `BackHandler { handleNavigateUp() }` for back press.
- `Modifier.onGloballyPositioned { coords -> appBarHeight = coords.size.height }` for measurement → `animateDpAsState` for animated padding.
- `AnimatedContent(targetState = ..., transitionSpec = { fadeIn() togetherWith fadeOut() })` for state crossfades.
- `var foo by remember { mutableStateOf(...) }` for local UI state.
- No `derivedStateOf` for hot paths (Gallery is mostly fine because state is in one VM and updates are coalesced).

**Warped's pattern:**
- `data class` everywhere — same.
- 50-field `ChatUiState` data class — borderline unstable due to deprecation annotations; should be `@Immutable` annotated.
- `var foo by remember { mutableStateOf(false) }` for local UI state in MessageBubble (`showReasoning`).
- `var bitmap = remember(uri) { ... BitmapFactory.decodeStream(it) }` in `ChatInputBar` — good caching.
- `LazyColumn` items use `key = { it.id }` (stable Long) — correct.
- `combinedClickable` with `onClick = {}` and `onLongClick = { ... }` in `MessageBubble` — wastes a tap event.
- **No `derivedStateOf` used** — `trafficLightState()` and `trafficLightStatusText()` are computed eagerly in `ChatUiState.kt` on every state change. Should be `derivedStateOf` at the Composable level.
- `Modifier.combinedClickable` is a **recomposition hotspot** when paired with `LazyColumn` (every tap recomposes the whole row).

**Verdict:** **MUST REFACTOR** the hot path. **CONSIDER** the broader perf audit. Concretely:

1. **`MessageBubble.kt`** — extract `ReasoningPanel` and `MessageContent` as separate child composables with stable inputs. Hoist `codeTheme` and `codeFontScale` to the parent (chat screen) and pass as plain values, not read from VM state inside the bubble.
2. **`CodeBlock.kt`** (565 lines — the largest composable in the app) — extract per-line composable. Move `remember { mutableStateOf(false) }` to local. The `animateColorAsState` per `TokenType` (13 calls per render) is expensive during streaming — defer to v1.6 deferred highlighting (already done).
3. **`ChatInputBar.kt`** — hoist `attachedImages` decoding to the screen-level (not per-render).
4. **`ChatViewModel` + `ChatUiState`** — split into `ChatListState(messages, conversationId)` + `ChatInputState(text, attachments)` + `ChatStreamingState(streaming, reasoning, toolCallActive)`. Composables read only the slice they need; this is the **standard pattern** for scaling Compose state.
5. **Recents drawer** (`NavGraph.kt`) — `items(conversations, key = { it.id })` is correct, but `combinedClickable` + per-item `remember { mutableStateOf(false) }` for `showDeleteConfirm` allocates a state per row on every recomposition. Use `rememberLazyListState` for the drawer to avoid re-laying out.
6. **`ModelsList` / `EndpointsList` in `Selector`** — add `contentType = { ... }` to `LazyColumn` items; use `key()` everywhere; mark the row data class `@Immutable`.

**Integration point in Warped:**
- `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt` — extract subcomposables, hoist state.
- `app/src/main/java/com/warped/ui/chat/components/CodeBlock.kt` — extract `CodeLine` composable, move `remember` outside the inner Column.
- `app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt` — hoist image decode to chat screen.
- `app/src/main/java/com/warped/ui/chat/ChatUiState.kt` — split into 3 sub-states; add `@Immutable`.
- `app/src/main/java/com/warped/ui/navigation/NavGraph.kt` — use `LazyListState` for the drawer.
- New: `app/src/main/java/com/warped/ui/chat/components/derivedStateHelpers.kt` — `derivedStateOf` for traffic light, char count, etc.

**Complexity:** M (touches the most-recalled composables; requires re-baselining frame stats on a device).

---

### 8. Caching / Lazy-Init Strategy (mmap cache, settings cache, etc.)

**Gallery's pattern:**
- `EngineConfig.cacheDir = context.getExternalFilesDir(null)?.absolutePath` — LiteRT-LM uses this for mmap cache of the model weights on warm starts. **This is the only "cache" — no file copy.**
- `downloadStartTimeSharedPreferences` (`SharedPreferences`) — stores `modelName → startTimeMs` for download analytics. Survives app restart.
- `DataStoreRepository` reads settings eagerly in `GalleryApplication.onCreate` (via `runBlocking`).
- 5 `DataStore<T>` files in `context.filesDir/datastore/`.

**Warped's pattern:**
- `EngineManager.getCachedModelPath()` does a **`sourceFile.copyTo(cachedFile)`** to `context.cacheDir/litertlm_cache/<filename>` — this is a **physical file copy** of the entire model (1-3 GB).
- Then `LiteRTLmEngine.init()` passes `cacheDir = java.io.File(context.cacheDir, "litertlm_cache").absolutePath` to `EngineConfig.cacheDir` — this is the mmap cache.
- So Warped has **both** patterns layered: copies the file, then mmap's it.
- `WarpedApplication.onTrimMemory` clears `cacheDir/litertlm_cache/` on `TRIM_MEMORY_RUNNING_CRITICAL` — but the original file is in `getExternalFilesDir`, not the cache, so it survives.
- `DataStore` for preferences (theme, font scale, syntax theme) — async via `Flow`.
- Room for structured data — async via `Flow`.

**Difference:** Gallery's pattern is **strictly better**. Copying a 2-3 GB file is a massive cold-start tax and doubles the on-device storage footprint. Gallery's `cacheDir` mmap is the LiteRT-LM team's intended pattern (per the official `ai.google.dev/edge/litert-lm/android` doc).

**Verdict:** **MUST REFACTOR.** Remove the file copy in `EngineManager.getCachedModelPath()`. Pass `context.getExternalFilesDir(null)` (or `context.cacheDir`) directly to `EngineConfig.cacheDir` as Gallery does.

Specifically:
1. `EngineManager.getCachedModelPath()` → **delete the method** (or keep for future custom-cache logic but unused).
2. `EngineManager.switchToLiteRT()` → pass `context.cacheDir.absolutePath` (or `context.getExternalFilesDir(null)?.absolutePath`) directly to a new `EngineConfig.init` parameter.
3. `LiteRTLmEngine.init()` → add a `cacheDir: String? = null` parameter; thread it into `EngineConfig(cacheDir = cacheDir)`.
4. The `model_normalizedName/version/filename` directory scheme in `EngineManager` is **no longer needed** (LiteRT-LM resolves paths directly). Simplify to just `modelPath`.
5. `WarpedApplication.onTrimMemory` → stop clearing `cacheDir/litertlm_cache/` (no-op now); instead rely on LiteRT-LM's own cache eviction.

**This change saves 1-3 GB of disk per model and ~3-10 seconds of cold-start I/O.** It's the single biggest perf win in the v2.0 file-by-line audit.

**Integration point in Warped:**
- `app/src/main/java/com/warped/data/local/inference/EngineManager.kt` — remove `getCachedModelPath`, simplify `switchToLiteRT`.
- `app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt` — add `cacheDir` param to `init`, thread to `EngineConfig`.
- `app/src/main/java/com/warped/WarpedApplication.kt` — simplify `onTrimMemory`.
- `app/src/main/java/com/warped/data/local/inference/BackendDetector.kt` — verify backend selection still works (no signature change needed).

**Complexity:** M (well-scoped; risk of breaking model loading).

---

## Cross-Cutting: AndroidManifest Updates

Gallery ships the following in `AndroidManifest.xml` that Warped should copy verbatim:

```xml
<application ...>
  <activity android:name=".MainActivity"
            android:configChanges="uiMode|orientation|screenSize|screenLayout|keyboardHidden"  <!-- NEW -->
            android:windowSoftInputMode="adjustResize"
            android:theme="@style/Theme.Warped.SplashScreen">  <!-- NEW -->

    <uses-native-library android:name="libvndksupport.so" android:required="false"/>  <!-- NEW -->
    <uses-native-library android:name="libOpenCL.so" android:required="false"/>  <!-- NEW -->
    <uses-native-library android:name="libcdsprpc.so" android:required="false"/>  <!-- NEW -->
  </activity>
</application>
```

**Integration point:** `app/src/main/AndroidManifest.xml`.

**Complexity:** S.

---

## Decision Matrix: MUST REFACTOR / CONSIDER / SKIP

### MUST REFACTOR (high value, concrete win)

| # | Change | Why | Files Touched | Complexity | Phase |
|---|--------|-----|---------------|------------|-------|
| 1 | **`LlmModelHelper` interface + 2 impls** | Keystone for Thinking / Benchmark / Prompt Lab / Skills | `data/runtime/*` (new), `di/InferenceModule`, `ui/chat/ChatViewModel` | L | 40 |
| 2 | **Engine cache: drop file copy, keep mmap** | 1-3 GB disk savings, 3-10s cold-start win | `data/local/inference/EngineManager.kt`, `LiteRTLmEngine.kt`, `WarpedApplication.kt` | M | 43 |
| 3 | **AppLifecycleProvider + foreground gate** | Suppress download notifications when app is open | `domain/lifecycle/AppLifecycleProvider.kt` (new), `MainActivity`, `data/local/download/DownloadWorker.kt` | S | 43 |
| 4 | **Splash screen + cross-fade + configChanges="uiMode"** | Eliminate cold-start flash, skip Activity recreate | `MainActivity.kt`, `AndroidManifest.xml`, `core-splashscreen` dep (STACK.md) | S | 43 |
| 5 | **`<uses-native-library>` for GPU/NPU/DSP** | Required for LiteRT-LM GPU backend on supported devices | `AndroidManifest.xml` only | S | 43 |
| 6 | **Split `ChatUiState` into 3 sub-states + `@Immutable`** | 50-field `data class` is a recomposition hotspot | `ui/chat/ChatUiState.kt`, `ChatViewModel.kt` | M | 43 |
| 7 | **Per-feature Hilt module pattern (Gallery `*TaskModule`)** | Slot-in for Prompt Lab / Benchmark / Skills | `di/RepositoryModule.kt`, new `ui/promptlab/di/*`, `ui/benchmark/di/*` | S | 40, 41, 42 |
| 8 | **`MessageBubble` / `CodeBlock` recomposition audit** | Hot path during streaming; baseline profile material | `ui/chat/components/MessageBubble.kt`, `CodeBlock.kt`, `ChatInputBar.kt` | M | 43 |
| 9 | **`derivedStateOf` for computed UiState fields** | `trafficLightState`, `trafficLightStatusText` recompute on every state mutation | `ui/chat/ChatUiState.kt` (move to composable) | S | 43 |
| 10 | **Type-safe nav arguments (`@Serializable`)** | Compile-time safety; better predictive back | `ui/navigation/NavGraph.kt`, `Screen.kt` | M | 40 |

### CONSIDER (cleaner, optional)

| # | Change | Why | Files Touched | Complexity | Phase |
|---|--------|-----|---------------|------------|-------|
| 1 | **AppLifecycleProvider extension property on `Model`** | Like Gallery's `Model.runtimeHelper` — gives a clean per-instance helper | `domain/runtime/`, `data/local/inference/` | S | 40 |
| 2 | **Compose `Modifier.drawWithCache` audit** | Gallery uses it for code blocks; Warped's CodeBlock redraws on every recompose | `ui/chat/components/CodeBlock.kt` | S | 43 |
| 3 | **Drop `EntryPointAccessors` in `NavGraph`** | Replace with `hiltViewModel()` and explicit composable parameters | `ui/navigation/NavGraph.kt` | M | 40 |
| 4 | **Per-task `*ViewModelBase` (Gallery `LlmChatViewModelBase`)** | For Prompt Lab / Benchmark VMs that share chat logic | `ui/promptlab/`, `ui/benchmark/` | S | 41, 42 |
| 5 | **Model allowlist JSON asset** (FEATURES.md P0) | Replace `RecommendedModels` constant with bundled JSON | `assets/model_allowlist.json`, `data/local/allowlist/*` | S | 40 |
| 6 | **Gallery-style `WindowInsets.statusBars` + `animateDpAsState` for app bar** | Smooth app bar show/hide on model-page screens | `ui/navigation/NavGraph.kt` + per-screen Scaffold | S | 40 |
| 7 | **Single `Model` data class spanning local + remote (Gallery `data class Model`)** | Unify `LocalModel` (Room) + `Endpoint` + `EndpointModel` behind one runtime-facing type | `domain/model/`, `data/repository/` | L | 40 |
| 8 | **OkHttp 5.x (5.0.0 stable)** | API breaking but stricter cancellation; Gallery stayed on 4.12 | `di/NetworkModule.kt`, all `data/remote/*` | L | 43 |
| 9 | **R8 full mode** | More aggressive shrinking; needs ProGuard rule audit | `gradle.properties`, `proguard-rules.pro` | S | 43 |
| 10 | **SQLCipher removal** | 10-30% per-query CPU savings if threat model allows | `data/local/db/AppDatabase.kt`, `di/DatabaseModule.kt` | S | 43 |

### SKIP (Gallery does it but Warped's approach is fine or better)

| Pattern | Gallery does it this way | Warped does it this way | Verdict |
|---------|--------------------------|--------------------------|---------|
| Mega-ViewModel | Single 850-line `ModelManagerViewModel` | 9 focused ViewModels | **Warped is better**. KEEP. |
| `runBlocking` DataStore reads | `runBlocking { dataStore.data.first() }` in every read | `Flow.first()` in coroutines | **Warped avoids the bug**. KEEP. |
| Per-concern DI modules | One `AppModule` | 8 per-concern modules | **Warped is better for testing**. KEEP. |
| `data class Model` with mutable `.instance` | Mutable state on the data class | `ActiveEngine` data class in `EngineManager` | **Warped is better typed**. KEEP. |
| `Moshi + Gson + kotlinx-serialization` | All three | `kotlinx-serialization` only | **Warped is leaner**. KEEP. |
| `kapt` for Hilt | Yes (legacy) | KSP only | **Warped is faster**. KEEP. |
| Firebase analytics + FCM | Yes | No | Out of scope. SKIP. |
| 5 Proto DataStores | Yes (`Settings`, `UserData`, `CutoutCollection`, `BenchmarkResults`, `Skills`) | Room + Preferences DataStore | **Warped is simpler**. KEEP. |
| Deep links | `gallery://model/<taskId>/<modelName>` | None | Defer to v2.1+. |
| `FLAG_KEEP_SCREEN_ON` | Yes (for demos) | No | Chat app, not demo. SKIP. |
| Custom task extensibility (`interface CustomTask`) | Yes (for Prompt Lab / Agent Chat / Mobile Actions / Tiny Garden / etc.) | None | **Adopt for v2.0 features** (see "MUST REFACTOR" #7). |
| `AppLifecycleProvider` boolean | Yes (single boolean) | (Not yet, planned) | **Adopt (see "MUST REFACTOR" #3)**. |
| Per-screen `rememberSaveable` | Yes | Yes | Match. KEEP. |
| Pure-Kotlin domain models | Mostly (`data class` in `data/`) | All (`domain/model/`) | **Warped is cleaner**. KEEP. |

---

## Build Order (with Dependency Constraints)

The following phase ordering respects dependencies — each phase builds on the previous.

```
Phase 40 — Runtime & Allowlist Foundation (P0, 5-7 days)
  ├─ 1. Add `LlmModelHelper` interface in `data/runtime/`
  ├─ 2. Implement `LiteRtLlmModelHelper` (wraps `EngineManager` + `LiteRTLmProvider`)
  ├─ 3. Implement `LmStudioModelHelper` (wraps `LMStudioProvider`)
  ├─ 4. Refactor `ChatViewModel` to depend on `LlmModelHelper` (not `ProviderRouter`)
  ├─ 5. Convert `RecommendedModels` constant to `assets/model_allowlist.json`
  ├─ 6. New `ModelAllowlistRepository` reading the asset
  ├─ 7. Adopt type-safe nav (Navigation 2.9.x `@Serializable` destinations) for 7 routes
  ├─ 8. Add per-feature Hilt module pattern (e.g. `RuntimeModule` for the two helpers)
  DEPENDS ON: nothing (foundation).
  ENABLES: Phase 41, 42, 44.

Phase 41 — Thinking Mode + Benchmark (P1, 7-10 days)
  ├─ 1. Extend `LlmModelHelper` with `enableThinking` param (via `extraContext`)
  ├─ 2. Add `ResultListener.thinking: String?` callback support
  ├─ 3. UI: collapsible "Thinking" panel in `MessageBubble` (use existing reasoning plumbing)
  ├─ 4. New `ui/benchmark/` package
  │     ├─ `BenchmarkScreen.kt`, `BenchmarkViewModel.kt`
  │     ├─ `BenchmarkResultsViewer.kt`, `BenchmarkValueSeriesViewer.kt`
  │     └─ `BenchmarkTaskModule` (per-feature Hilt)
  ├─ 5. New `domain/repository/BenchmarkRepository.kt` + impl
  ├─ 6. `BenchmarkWorker` (WorkManager) for foreground-execution benchmark runs
  ├─ 7. Wire `capabilities: List<ModelCapability>` (LLM_THINKING) to the UI
  DEPENDS ON: Phase 40.
  ENABLES: nothing new.

Phase 42 — Prompt Lab (P1, 5-7 days)
  ├─ 1. New `ui/promptlab/` package mirroring `ui/llmchat/`
  │     ├─ `PromptLabScreen.kt`, `PromptLabViewModel.kt`
  │     ├─ `PromptTemplateConfigs.kt` (5-8 templates)
  │     └─ `PromptLabTaskModule`
  ├─ 2. Reuses `LlmModelHelper` from Phase 40
  DEPENDS ON: Phase 40.

Phase 43 — Performance Convergence (P0, 7-10 days, cross-cutting)
  ├─ 1. Drop `EngineManager.getCachedModelPath()` — file copy → mmap only (BIGGEST WIN)
  ├─ 2. Add `AppLifecycleProvider` + foreground gate on download notifications
  ├─ 3. Add `installSplashScreen()` + cross-fade mask in `MainActivity`
  ├─ 4. AndroidManifest: `configChanges="uiMode"`, `<uses-native-library>` blocks, SplashScreen theme
  ├─ 5. Split `ChatUiState` into 3 sub-states + `@Immutable`
  ├─ 6. `MessageBubble` / `CodeBlock` recomposition audit (extract subcomposables, hoist state)
  ├─ 7. `derivedStateOf` for `trafficLightState` / `trafficLightStatusText`
  ├─ 8. R8 full mode + ProGuard rule audit
  ├─ 9. SQLCipher microbench (decide keep/remove)
  ├─ 10. Room composite index audit (`messages(conversation_id, created_at)`)
  ├─ 11. Macrobenchmark cold-start baseline (Pixel 7 reference)
  DEPENDS ON: nothing (orthogonal to features). Can run in parallel with 41/42.
  ENABLES: nothing.

Phase 44 — Agent Skills Lite (P2, XL — 10-14 days, optional, defer if scope tight)
  ├─ 1. Extend `LlmModelHelper` with `tools: List<ToolProvider>` parameter
  ├─ 2. New `skills/` package — 3-5 built-in Kotlin skills
  ├─ 3. New `SkillRepository` + `SkillTaskModule`
  ├─ 4. UI: skill chips under chat input
  DEPENDS ON: Phase 40 (LlmModelHelper must exist). LiteRT-LM 0.13.1 `ToolProvider` (STACK.md).

Defer to v2.1+:
  - LM Studio MCP Bridge (L, separate phase)
  - Speculative Decoding toggle (S, in Phase 40 if time)
  - Benchmark history viewer (S, needs enough data)
  - Deep links (`com.warped://...`) (M, after v2.0 ships)
  - Gallery `Model.runtimeHelper` extension property (S, nice-to-have)
```

**Why this order:**
- **Phase 40 is keystone** — every feature phase depends on it. Must land first.
- **Phase 43 is parallel** — it touches engine plumbing, manifest, and Compose state but not the new `LlmModelHelper` surface. Can start as soon as Phase 40's `LlmModelHelper` is interface-defined (not necessarily after Phase 40 ships).
- **Phase 41 and 42 are independent** — they both depend on Phase 40 but not on each other. Can run in parallel.
- **Phase 44 is optional and XL** — defer to v2.1 if v2.0 scope is tight.

---

## Open Questions for Phase-Specific Research

These gaps cannot be resolved without deeper investigation during the relevant phase:

1. **Does `LiteRT-LM 0.13.1`'s `EngineConfig` accept a `cacheDir: String?` parameter, or is it called `mmapDir` / something else?** Verify in `com.google.ai.edge.litertlm.EngineConfig` (the AAR). Gallery's `LlmChatModelHelper.kt:177` shows: `cacheDir = if (modelPath.startsWith("/data/local/tmp")) context.getExternalFilesDir(null)?.absolutePath else null`. Warped currently passes a directory inside `context.cacheDir` and copies the file there — must verify the exact parameter name and acceptable path conventions before doing the engine-cache refactor.

2. **Does the `ResultListener` typealias need to be `suspend`?** Gallery's is `(partialResult, done, partialThinkingResult) -> Unit` (synchronous). For the Warped integration with Flow-based streaming, it may need to be `suspend` so the helper can call `delay(0)` for back-pressure. Verify in Phase 40.

3. **What's the exact `Message.thinking` accessor in `LiteRT-LM 0.13.1`?** Gallery uses `message.channels["thought"]` — that's an untyped map. Verify the stable API surface in the AAR (could be `message.thinking` or `message.thought`).

4. **Does `LiteRT-LM 0.13.1` expose `ToolProvider` for local use, or is it only used by AICore?** Gallery's `LlmModelHelper.initialize(tools: List<ToolProvider>)` accepts the param, but the local `LlmChatModelHelper` doesn't pass it to `ConversationConfig.tools`. Phase 44 must verify whether local tool-calling actually works in 0.13.1 or if it's still experimental.

5. **What does Warped's `LiteRTLmProvider` look like after a `LlmModelHelper` extraction?** `LiteRTLmProvider.chat()` currently does a lot: engine lazy-load, history construction, current-msg construction, sampler config, tool registry, retry loop, recovery. Most of this belongs in `LiteRtLlmModelHelper.runInference` (the Gallery shape), not in `LiteRTLmProvider`. Phase 40 must decide how much to keep in the provider vs. the helper.

6. **Compose 1.11 (BOM 2026.05.01) stability annotations** — has the `Strong Skipping` mode landed? If yes, explicit `@Immutable` becomes less important; if no, it's mandatory. Verify in Compose 1.11.0 release notes during Phase 43.

7. **`EngineManager.handleTrimMemory` and the new cache pattern** — when we drop the file copy, what does `handleTrimMemory` do? Currently it clears `cacheDir/litertlm_cache/` which is gone. Phase 43 must decide: trust LiteRT-LM's own eviction, or add a custom `EvictionPolicy` for cached conversation contexts.

8. **SQLCipher removal — what's the current Warped threat model?** v1.5 decision rationale: "API keys in EncryptedSharedPreferences, no plaintext secrets." If still valid, the DB doesn't need SQLCipher. Phase 43 should benchmark with/without and make a data-driven decision.

9. **`runBlocking` in `LiteRTLmProvider.chat()` for `toolRegistry.enabledToolIds.first()`** — this blocks the inference thread. Must move to a constructor-time inject (Phase 40 refactor) or convert `toolRegistry` to a `StateFlow<Set<String>>` cached at the helper level.

10. **Deep-link strategy** — does Warped want to support `warped://chat/<conversationId>`? Phase 40's type-safe nav migration makes this easy. Defer to v2.1 unless an early user asks.

---

## Sources

### Primary (HIGH confidence — direct repo inspection)

- [google-ai-edge/gallery repository](https://github.com/google-ai-edge/gallery) — main branch, May 2026, versionCode 34, versionName 1.0.16
- [`GalleryApplication.kt`](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/GalleryApplication.kt) — 63 lines, simple `@HiltAndroidApp` + DataStore theme read
- [`GalleryApp.kt`](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/GalleryApp.kt) — top-level composable, 24 lines
- [`GalleryLifecycleProvider.kt`](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/GalleryLifecycleProvider.kt) — 1-field mutable boolean
- [`MainActivity.kt`](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/MainActivity.kt) — 198 lines, `installSplashScreen`, `super.onCreate(null)`, `enableEdgeToEdge`
- [`AppModule.kt`](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/di/AppModule.kt) — 1 file, ~150 lines, all `@Provides @Singleton`
- [`runtime/LlmModelHelper.kt`](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/runtime/LlmModelHelper.kt) — interface with 5 methods, `ResultListener` and `CleanUpListener` typealiases
- [`runtime/ModelHelperExt.kt`](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/runtime/ModelHelperExt.kt) — `Model.runtimeHelper` extension property
- [`ui/llmchat/LlmChatModelHelper.kt`](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt) — 340 lines, wraps `Engine` + `Conversation`, stores `LlmModelInstance`
- [`ui/llmchat/LlmChatViewModel.kt`](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatViewModel.kt) — `LlmChatViewModelBase` abstract + 3 concrete VMs
- [`ui/llmchat/LlmChatTaskModule.kt`](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatTaskModule.kt) — 3 tasks × 1 module each, `@IntoSet` binding
- [`ui/navigation/GalleryNavGraph.kt`](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/navigation/GalleryNavGraph.kt) — 430 lines, 6 routes, deep links, `CustomTaskScreen` wrapper
- [`ui/modelmanager/ModelManagerViewModel.kt`](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/modelmanager/ModelManagerViewModel.kt) — ~850 lines, mega-VM, `ModelManagerUiState` data class
- [`data/Model.kt`](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/data/Model.kt) — 440 lines, mutable `var instance: Any? = null`
- [`data/Tasks.kt`](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/data/Tasks.kt) — `Task` data class, `BuiltInTaskId`
- [`data/DataStoreRepository.kt`](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/data/DataStoreRepository.kt) — 50+ method interface, `DefaultDataStoreRepository` with `runBlocking` reads
- [`data/DownloadRepository.kt`](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/data/DownloadRepository.kt) — 280 lines, WorkManager, foreground notification gate
- [`customtasks/common/CustomTask.kt`](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/common/CustomTask.kt) — interface, 4 methods + 1 composable
- [`customtasks/llmchat/`](https://github.com/google-ai-edge/gallery/tree/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks) — directory listing (agentchat, common, examplecustomtask, mobileactions, tinygarden)
- [Gallery `model_allowlists/1_0_15.json`](https://github.com/google-ai-edge/gallery/raw/refs/heads/main/model_allowlists/1_0_15.json) — versioned allowlist with capabilities
- [Gallery `model_allowlist.json`](https://github.com/google-ai-edge/gallery/blob/main/model_allowlist.json) — legacy allowlist
- [Gallery `AndroidManifest.xml`](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/AndroidManifest.xml) — `configChanges="uiMode"`, `<uses-native-library>`, splash screen theme
- [Gallery `libs.versions.toml`](https://github.com/google-ai-edge/gallery/blob/main/Android/src/gradle/libs.versions.toml) — versionCode 34, versionName 1.0.16

### Warped internal (HIGH confidence — direct file read)

- [PROJECT.md](.planning/PROJECT.md) — current state, v2.0 milestone goals
- [REQUIREMENTS.md](.planning/REQUIREMENTS.md) — v1.8 surface
- [STATE.md](.planning/STATE.md) — current phase structure
- [research/STACK.md](.planning/research/STACK.md) — confirmed stack
- [research/FEATURES.md](.planning/research/FEATURES.md) — Gallery feature port map

### Warped source files inspected

- `app/src/main/java/com/warped/WarpedApplication.kt` (108 lines) — `onTrimMemory`, `RedactingTree`
- `app/src/main/java/com/warped/MainActivity.kt` (37 lines) — minimal, no splash
- `app/src/main/java/com/warped/ui/navigation/NavGraph.kt` (404 lines) — drawer pattern, `EntryPointAccessors`
- `app/src/main/java/com/warped/data/local/inference/EngineManager.kt` (155 lines) — file copy + LiteRT-LM lifecycle
- `app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt` (132 lines) — `EngineConfig.cacheDir` set
- `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt` (314 lines) — `LlmProvider` impl, `runBlocking` for tool registry
- `app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt` (62 lines) — 5 providers
- `app/src/main/java/com/warped/di/` (8 modules) — per-concern DI
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` (844 lines) — 50-field `ChatUiState`
- `app/src/main/java/com/warped/ui/chat/ChatUiState.kt` (105 lines) — 50-field data class, deprecated fields
- `app/src/main/java/com/warped/ui/chat/components/` (9 files, 1736 total lines) — `CodeBlock` 565 lines, `ChatInputBar` 200 lines

### Secondary (MEDIUM confidence)

- [LiteRT-LM Android getting started](https://ai.google.dev/edge/litert-lm/android) — `Backend.GPU()`, `cacheDir`, NPU patterns
- [LiteRT-LM Maven metadata](https://dl.google.com/android/maven2/com/google/ai/edge/litertlm/litertlm-android/maven-metadata.xml) — 0.13.1 latest as of 2026-06-04
- [Compose BOM 2026.05.01 mapping](https://developer.android.com/jetpack/compose/bom/bom-mapping) — Compose 1.11.0
- [Navigation 2.9 release notes](https://developer.android.com/jetpack/androidx/releases/navigation) — type-safe destinations
- [Hilt KSP support](https://dagger.dev/dev-guide/ksp) — stable since 2.48
- [Dagger 2.59 release notes](https://github.com/google/dagger/releases) — AGP 9+ requirement

### Confidence notes

- **HIGH** on file paths, pattern references, and architectural claims — all directly inspected from the Gallery repo on 2026-06-05.
- **HIGH** on Warped's current architecture — all integration points verified by reading the actual source files.
- **MEDIUM** on impact estimates (e.g. "3-10s cold-start win from cache refactor") — well-documented in LiteRT-LM docs but not yet measured on Warped hardware.
- **MEDIUM** on the `LlmModelHelper` interface design — it's a direct port of Gallery's, but Warped's existing `LlmProvider` interface means there will be a transition period where both exist.
- **LOW** on the exact `LiteRT-LM 0.13.1` `ToolProvider` Kotlin API surface — needs Phase 44 prototype to confirm.

---

*Architecture research for: Warped v2.0 Gallery Convergence & Performance Overhaul*
*Researched: 2026-06-05*
*Confidence: HIGH (architecture) / MEDIUM (impact estimates)*
