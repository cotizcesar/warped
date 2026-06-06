# Phase 43 — Performance Convergence

**Requirements:** PERF-01..13
**Plans:** 4 (43-01 Compose, 43-02 DI/network, 43-03 data/lifecycle/splash, 43-04 macrobench + docs)

## Decisions

- **Compose state split** (PERF-01): `ChatUiState` is decomposed into three sub-states: `ChatListState` (messages, streamingContent, streamingReasoning), `ChatInputState` (input text, attachedImages, audioBytes), `ChatStreamingState` (isStreaming, streamingContent, streamingReasoning, toolCallActive, generationParameters). A single `ChatScreenState` aggregates them. Each is annotated `@Immutable` and used as individual parameters in screen composables. This lets Compose's strong-skipping skip the input subtree on streaming-only updates and vice versa.
- **ImmutableList migration** (PERF-02): add `kotlinx-collections-immutable:0.4.0` (already in `libs.versions.toml`). Convert `List<LocalModel>`, `List<ChatMessage>`, `List<Endpoint>`, `List<Conversation>` etc. to `ImmutableList<T>` only at the Compose boundary (in `@Composable` functions and view-state collections). Keep domain/data layers on `List<T>` to avoid ripple changes. The `ImmutableList` parameter promotes strong-skipping in Compose 1.11.
- **Subcomposable extraction** (PERF-03): in `MessageBubble` (237 lines) extract `MessageContent`, `MessageActions`, `MessageReasoningPanel`; in `CodeBlock` extract `CodeLine`, `CodeHeader`.
- **derivedStateOf** (PERF-04): wrap `trafficLightState` and `trafficLightStatusText` in the settings/UI layer where they currently run on every recomposition.
- **Hoisted state** (PERF-05): `codeTheme`, `codeFontScale`, `attachedImages` already largely hoisted in 41-02; verify and document.
- **LazyColumn key** (PERF-06): add `key = { it.id }` (or `it.javaClass.name + "-" + index` for synthetic items) to message lists in `ChatScreen.kt`.
- **Hilt scoping** (PERF-07): audit every `@Provides`; add missing scopes; ensure no `@Singleton` providers on heavyweight classes (`LiteRtLlmEngine`, `OkHttpClient` is fine because it's shared).
- **OkHttp tuning** (PERF-08): SSE-call `OkHttpClient` variant with `retryOnConnectionFailure(false)`, `callTimeout(60.seconds)`, `Cache(50MB)` + `IMMEDIATE_NO` cache control.
- **Room index** (PERF-09): add `Index(value = ["conversation_id", "created_at"])` on `MessageEntity`; verify with `EXPLAIN QUERY PLAN` via a Room migration.
- **AppLifecycleProvider** (PERF-10): new interface + impl backed by `ProcessLifecycleOwner.lifecycle.currentState`; `DownloadWorker` reads `isAppInForeground` to decide whether to post the notification.
- **Splash screen** (PERF-11): `androidx.core:core-splashscreen:1.2.0-beta01` (verify exact stable version available). `MainActivity` calls `installSplashScreen()` and sets a cross-fade mask in `onCreate` before `super.onCreate`.
- **Macrobenchmark** (PERF-12): new `:benchmark` Gradle module with `androidx.benchmark:benchmark-macro-junit4` + the `BaselineProfileRule`. Cold-start benchmark measures `StartupTimingMetric` and `FrameTimingMetric`. SQLCipher microbench measures with/without encryption. Results go in `BENCHMARKS.md`.
- **Where the measurement is impossible offline**: PERF-12 + PERF-13 require a real device/emulator. Code and Gradle setup ship, but the actual numbers depend on hardware. We document the procedure, ship a runnable Gradle task, and write a stub `BENCHMARKS.md` with methodology + a `[To fill in CI]` placeholder for each metric.

## Carry-overs from Phase 40 / 41 verification
- `LlmModelHelper.runInference` is now called once per chat (fixed in 41-01).
- `LmStudioHelper.activeCall` `AtomicReference<Call?>` is never assigned; `stopResponse()` cannot cancel. Fix: assign `activeCall.set(call)` in `createCall` and `activeCall.set(null)` in `onResponse`. Defer to 43-02.

## Constraints
- Compose 1.11 strong-skipping is enabled by default since 1.10; verify via `androidx.compose.compiler` setup.
- Macrobenchmark module needs `compileSdk = 35`, `minSdk = 28`, `targetProjectPath = ":app"`.
