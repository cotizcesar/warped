# Phase 41: Thinking Mode + Model Benchmark - Context

**Gathered:** 2026-06-06
**Status:** Ready for planning
**Depends on:** Phase 40 (LlmModelHelper interface; ModelAllowlistRepository.supportsThinking)

<domain>
## Phase Boundary
Phase 41 ships two user-facing additions on top of the v2.0 keystone:

1. **Thinking Mode** (THINK-01..07, 7 reqs) — propagate the `enableThinking: Boolean` parameter from chat input → `LlmModelHelper.runInference` → both helpers; surface the model's reasoning trace as a collapsible panel above the assistant bubble. Gated per-model via the allowlist `llm_thinking` capability landed in Phase 40.

2. **Model Benchmark** (BENCH-01..06, 6 reqs) — a new screen at `Screen.Benchmark` (placeholder already wired in Phase 40) where the user picks a downloaded model + config and runs an on-device benchmark via WorkManager. Captures: init time (ms), prefill tok/s, decode tok/s, peak memory (bytes). Results persist to a new Room `BenchmarkResult` table (schema bump v11 → v12 with `@AutoMigration`). Sparkline-capable history viewer.

13 requirements total. Both features are independent of each other but share the same `LlmModelHelper` surface.
</domain>

<decisions>
## Implementation Decisions

### the agent's Discretion
All implementation at the agent's discretion. Constraints:
- Reuse the v2.0 `LlmModelHelper` surface — no new interface shapes.
- Allowlist gating via the existing `ModelAllowlistRepository.supportsThinking(modelId)` — no new repo.
- Room migration must use `@AutoMigration(from=11, to=12)` per v2.0 BENCH-04 (also flips `exportSchema = true`).
- WorkManager `setForeground()` with `Constraints(UNMETERED, BATTERY_NOT_LOW)` per BENCH-02 — same `@HiltWorker` pattern as existing `ModelDownloadWorker`.

### LiteRT-LM Thinking API (THINK-02)
LiteRT-LM 0.13.1 exposes thinking via the `ResultListener.partialThinkingResult: String?` callback (per v2.0 research). For Warped, the existing `LiteRTLmProvider.chat()` already emits a `StreamToken.Delta(reasoning = ...)` shape — the wiring already exists for the `reasoningEnabled = true` path of `ChatUiState`. We surface the `enableThinking` boolean down to that path and bind it to per-model allowlist capability.

### LM Studio Reasoning API (THINK-03)
LM Studio v1 emits `choices[0].delta.reasoning_content` (or `choices[0].delta.reasoning.delta` depending on server) in the SSE stream. `LMStudioProvider.kt` already handles this — Phase 41 just plumbs the toggle. (Reference: v1.8 LM Studio integration; flagged LOW-MEDIUM confidence in v2.0 research but the existing implementation already parses it.)

### Benchmark Metrics Capture (BENCH-01..03)
- `initTimeMs`: measured around `LlmModelHelper.initialize()`.
- `prefillTokPerSec`: prefill phase = time from `runInference` start to first emitted `StreamToken.Delta` from the model body; tokens = encoded input length.
- `decodeTokPerSec`: total output tokens / (time from first delta to `StreamToken.Done`).
- `peakMemoryBytes`: poll `android.os.Debug.getNativeHeapAllocatedSize() + Runtime.getRuntime().totalMemory()` at 100ms intervals while inference runs; report max.
- Each benchmark uses a fixed prompt corpus (`assets/benchmark_prompt.txt`, ~512 tokens) and runs N=3 trials to stabilize.

### Room Migration (BENCH-04)
- Bump `version = 11` → `version = 12` in `AppDatabase`.
- Add `BenchmarkResultEntity` to `entities`.
- Set `exportSchema = true`; add `schemas/` directory + KSP arg already present in `build.gradle.kts`.
- Use `@AutoMigration(from = 11, to = 12)` since the only change is adding a new table — no column changes.
- Add `androidTest/MigrationTest.kt` that round-trips a 50-message conversation through the migration (asserts conversations + messages survive).
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`LlmModelHelper.runInference(request, enableThinking)`** (Phase 40) — already accepts `enableThinking: Boolean`. Both `LiteRtLlmHelper.runInference()` and `LmStudioHelper.runInference()` accept the flag but currently ignore it. Phase 41 wires it to the underlying provider call sites.
- **`ChatUiState.reasoningEnabled: Boolean = true`** — already in state (`ChatUiState.kt:40`). Phase 41 renames this to `enableThinking: Boolean = false` (default off) and wires it to the chip toggle.
- **`ChatUiState.streamingReasoning: String`** — already in state (`ChatUiState.kt:19`). Phase 41 surfaces it in `MessageBubble` as a collapsible panel.
- **`StreamToken.Done(stats, reasoning)`** — already carries final reasoning (per v2.0 contract notes). Phase 41 just plumbs it to `ChatViewModel` → `MessageBubble`.
- **`ModelAllowlistRepository.supportsThinking(modelId)`** (Phase 40, RUNTIME-06) — already exists. Phase 41 calls it from `ChatViewModel` to gate the Thinking chip.
- **`ModelDownloadWorker` + `@HiltWorker` pattern** (`app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt`) — proven template for `ModelBenchmarkWorker`.
- **`Screen.Benchmark` route + `BenchmarkPlaceholderScreen`** (`app/src/main/java/com/warped/ui/navigation/PlaceholderScreens.kt:24`) — Phase 41 replaces the placeholder with the real screen.
- **`AppDatabase` at version 11** (`app/src/main/java/com/warped/data/local/db/AppDatabase.kt:27`) — Phase 41 bumps to 12 + adds `BenchmarkResultEntity`.
- **`AdvancedPreferences` / DataStore** — proven pattern for benchmark default config (temperature, top-k, max tokens, trials).

### Established Patterns
- **`@HiltWorker` + `HiltWorkerFactory`** for WorkManager workers.
- **Room `@AutoMigration`** is available (Room 2.7+), but project hasn't used it yet — first time in v2.0.
- **`StreamToken` sealed interface** — `Delta(content, reasoning)`, `Done(stats, reasoning)`, `Error(message)` — already supports the reasoning channel.
- **Collapsible Compose panels** — no precedent in chat UI yet; use `AnimatedVisibility` + `expandVertically()/shrinkVertically()` + a chevron icon (Material Icons).
- **Per-message UI state in `MessageBubble`** — the bubble is stateless and accepts a `ChatMessage`. Thinking expansion is local UI state via `remember { mutableStateOf(false) }`.
- **`StreamingContent` distinct from finalized messages** — `ChatScreen` renders the streaming bubble separately. Phase 41 mirrors this for `streamingReasoning`.

### Integration Points
- **New domain models**: `app/src/main/java/com/warped/domain/model/BenchmarkResult.kt`, `app/src/main/java/com/warped/domain/model/BenchmarkConfig.kt`.
- **New Room files**: `data/local/db/entity/BenchmarkResultEntity.kt`, `data/local/db/dao/BenchmarkResultDao.kt`. Add to `AppDatabase`.
- **New repo**: `domain/repository/BenchmarkRepository.kt` + `data/repository/BenchmarkRepositoryImpl.kt`.
- **New worker**: `data/local/benchmark/ModelBenchmarkWorker.kt` (`@HiltWorker`).
- **New UI**: `ui/benchmark/BenchmarkScreen.kt`, `ui/benchmark/BenchmarkViewModel.kt`, `ui/benchmark/BenchmarkUiState.kt`, `ui/benchmark/components/BenchmarkResultsViewer.kt`, `ui/benchmark/components/BenchmarkValueSeriesViewer.kt` (5 files per BENCH-01).
- **MessageBubble edit** (`app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt`): inject `streamingReasoning` / `finalReasoning`, add `ThinkingPanel` composable above the message body.
- **ChatInputBar edit** (`app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt`): add Thinking chip (visible only when `supportsThinking == true`).
- **ChatViewModel edit**: gate the chip, propagate `enableThinking` to `LlmModelHelper.runInference()`.
- **Migration test**: `app/src/androidTest/java/com/warped/data/local/db/MigrationTest.kt`.
- **DI**: `di/RepositoryModule.kt` — bind `BenchmarkRepository`. `di/DatabaseModule.kt` — register migration & autoMigration.
- **Asset**: `app/src/main/assets/benchmark_prompt.txt` (one file, ~512 tokens).
</code_context>

<specifics>
## Specific Ideas

### Thinking Mode (THINK-01..07)
- **THINK-01** (`LlmModelHelper.runInference` accepts `enableThinking`): already done in Phase 40 — verify call sites pass the boolean through (`ChatViewModel.kt`).
- **THINK-02** (`LiteRtLlmHelper` wires `enableThinking`): pass through to `LiteRTLmProvider.chat()` via a new `chat(request, enableThinking)` overload OR via setting a thinking flag on `ConversationConfig`. Reuses existing reasoning emission path in `LiteRTLmProvider`.
- **THINK-03** (`LmStudioHelper` wires `enableThinking`): `LMStudioProvider.chat()` already parses `reasoning_content` from SSE; if `enableThinking` is false, drop reasoning deltas before emitting (filter at the helper boundary). When true, pass through. No request-side change needed (LM Studio server always emits if model supports it; we just filter on consume).
- **THINK-04** (`MessageBubble` collapsible Thinking panel): new `@Composable ThinkingPanel(text, expanded, onToggle)` placed inside the assistant bubble above the markdown body. Uses `AnimatedVisibility(expanded) { Text(text) }` + a header `Row("Thinking", chevron icon, click toggles)`. Default expanded during streaming, collapsed when stream completes (`expanded = isStreamingActive`).
- **THINK-05** (Chat input bar Thinking chip): new `FilterChip("Thinking", selected = enableThinking, onClick = { vm.toggleThinking() })` in `ChatInputBar`. Persist toggle via `AdvancedPreferences.thinkingEnabled: Flow<Boolean>` (DataStore).
- **THINK-06** (Chip hidden when model lacks `llm_thinking`): `ChatViewModel` derives `val showThinkingChip = allowlistRepo.supportsThinking(activeModelId)` and passes it to `ChatInputBar`. Chip is wrapped in `if (showThinkingChip)`.
- **THINK-07** (Panel collapsed after stream): track `expanded` state per-message in `MessageBubble`; default to `isStreamingActive` (open during stream), collapsed after `StreamToken.Done` arrives. User tap toggles freely.

### Model Benchmark (BENCH-01..06)
- **BENCH-01** (`ui/benchmark/` package, 4 files mirroring Gallery):
  - `BenchmarkScreen.kt` — `Scaffold` with title bar; body has model picker (dropdown of `LocalModelRepository.observeAll()`), config card (temperature, top-k, max tokens sliders), trials count, "Start Benchmark" button, results list below.
  - `BenchmarkViewModel.kt` — `@HiltViewModel`. Exposes `StateFlow<BenchmarkUiState>`. `start()` enqueues `ModelBenchmarkWorker`. `results: Flow<List<BenchmarkResult>>` from `BenchmarkRepository`.
  - `BenchmarkResultsViewer.kt` — list of past results: model name, config hash, 4 metrics, timestamp.
  - `BenchmarkValueSeriesViewer.kt` — tiny sparkline showing decode-tok/s over time per-model (Canvas/Path drawing — no chart library, ~50 LOC).
- **BENCH-02** (`ModelBenchmarkWorker` with `setForeground()` + constraints):
  - `@HiltWorker class ModelBenchmarkWorker @AssistedInject(workerParams, ...)` with injected `LlmModelHelper` (LITE_RT_LM), `BenchmarkRepository`.
  - `Constraints.Builder().setRequiredNetworkType(UNMETERED).setRequiresBatteryNotLow(true).build()` (BATTERY_NOT_LOW is the safer pair per BENCH-02).
  - `setForegroundAsync(ForegroundInfo(NOTIF_ID, notif))` with a benchmark-progress notification.
  - Worker body: `helper.initialize(modelPath)` (time it), then loop 3 trials: `runInference(req)` collecting tokens, measure prefill/decode/peak.
  - **Chinese OEM ROM risk note**: `setForeground()` may be killed on some ROMs (per v2.0 LOW-confidence research). Wrap in try/catch and fall back to `setProgress()` if foreground service is denied.
- **BENCH-03** (`BenchmarkRepository` + Room table):
  - Entity: `BenchmarkResultEntity(id PRIMARY KEY AUTOINCREMENT, modelId TEXT, configHash TEXT, initTimeMs INT, prefillTokPerSec REAL, decodeTokPerSec REAL, peakMemoryBytes INT, createdAt INT)`.
  - DAO: `insert(result)`, `observeAll(): Flow<List<BenchmarkResultEntity>>`, `observeByModel(modelId): Flow<List<...>>`.
  - Repo interface: `insert(result)`, `observeAll(): Flow<List<BenchmarkResult>>`, `observeByModel(modelId)`.
- **BENCH-04** (Migration v11 → v12):
  - `AppDatabase.kt`: add `BenchmarkResultEntity::class` to entities, bump `version = 12`, set `exportSchema = true`, add `autoMigrations = [AutoMigration(from = 11, to = 12)]`.
  - `app/build.gradle.kts: ksp { arg("room.schemaLocation", "$projectDir/schemas") }` already present.
  - `app/src/androidTest/java/com/warped/data/local/db/MigrationTest.kt`: uses `MigrationTestHelper`. Insert a `ConversationEntity` + 50 `MessageEntity` rows in v11, run migration, assert rows survive.
- **BENCH-05** (Results page UI): list grouped by model. Each row: model display name → small card with 4 numeric values + a 30-pt sparkline (decode tok/s over time). Tap for full detail screen (not in scope; flagged for v2.1).
- **BENCH-06** (Model + config picker): top of `BenchmarkScreen`. ModelDropdown sourced from `LocalModelRepository.observeDownloaded()`. Config card with sliders bound to `GenerationParameters` (existing domain class). "Trials" stepper (1-10, default 3).
</specifics>

<deferred>
## Deferred Ideas
- **BENCH-VIEW-01** — Standalone benchmark history viewer page already deferred to v2.1 in REQUIREMENTS.md.
- **LRT-04** — Speculative decoding toggle stays out of scope; allowlist exposes `llm_spec_decoding` capability but no UI gate in Phase 41.
- **Chinese OEM foreground service hardening** — flagged for follow-up if real-world reports surface (currently LOW confidence per v2.0 research).
- **Sparkline → real chart library** — sticking with hand-rolled Canvas; if requirements grow, defer to MPAndroidChart or YCharts in v2.1.
</deferred>
