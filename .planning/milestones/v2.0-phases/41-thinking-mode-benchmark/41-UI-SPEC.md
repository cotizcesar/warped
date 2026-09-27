# Phase 41 UI Spec — Thinking Mode + Model Benchmark

**Status:** Ready for implementation
**Phase:** 41 (Thinking Mode + Model Benchmark)

## Overview

Two surfaces:

1. **Chat — Thinking** (extends existing chat screen).
2. **Benchmark** (new screen at `Screen.Benchmark`, replacing the placeholder).

## A. Chat — Thinking Toggle + Panel

### A.1 Thinking Chip in `ChatInputBar`

**Location:** Inside the input bar, above the text field, aligned right.
**Visibility:** Only when the active model's allowlist entry has `llm_thinking` capability (`ModelAllowlistRepository.supportsThinking(modelId) == true`).
**Component:** Material 3 `FilterChip`.

```
+---------------------------------------------------------------+
|                                            [✓ Thinking]      |
| [Message input field…]                            [Send →]    |
+---------------------------------------------------------------+
```

- Label: `"Thinking"` (no icon needed; chip selection state communicates on/off).
- `selected = uiState.enableThinking`
- `onClick = { vm.toggleThinking() }`
- Default state: persisted in `AdvancedPreferences.thinkingEnabled` (boolean, default `false`).
- When unselected, model reasoning deltas are dropped at the helper boundary.

### A.2 Thinking Panel in `MessageBubble`

**Location:** Inside each assistant `MessageBubble`, above the markdown body.
**Visibility:** When the message has a non-empty `reasoning` field.
**Component:** Custom collapsible row.

```
┌─────────────────────────────────────────────────────────────┐
│ Assistant bubble                                            │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ ▼ Thinking…  (250ms ▮)                                  │ │  <- header
│ │   The user is asking about prime numbers…              │ │  <- collapsed body
│ │   Let me check 11: it has only 1 and 11 as divisors,…  │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                              │
│ Yes, 11 is a prime number.                                  │ <- final response
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

- Header row:
  - Chevron icon (`Icons.Filled.ExpandLess` / `Icons.Filled.ExpandMore`) — rotates based on `expanded` state.
  - Label: `"Thinking..."` while streaming, `"Thinking"` after `StreamToken.Done`.
  - Optional badge: live token count or "(2.3s)" duration when stream completes.
- Body:
  - Wrapped in `AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically())`.
  - Text rendered in `MaterialTheme.typography.bodySmall` with `MaterialTheme.colorScheme.onSurfaceVariant`.
  - Fixed max height with vertical scroll if the trace is long (>200dp).
- Background: subtle tinted `MaterialTheme.colorScheme.surfaceContainerLow` to differentiate from the response body.
- `expanded` state:
  - During streaming (`isStreamingActive == true`): default `true`.
  - After `StreamToken.Done` arrives: default `false`.
  - User tap toggles freely.

### A.3 Streaming Reasoning Bubble

When the assistant is mid-stream, the streaming bubble shows the reasoning panel above the body, both updating in real time. Mirrors `streamingContent` rendering already in `ChatScreen.kt`.

## B. Benchmark Screen

### B.1 Top Bar

- Title: `"Benchmark"`.
- Back navigation handled by NavGraph (system back; no custom button).
- Reuse `MaterialTheme.colorScheme.background` and existing app surface theming.

### B.2 Body Layout (Column)

```
┌─ Benchmark ───────────────────────────────────────────────┐
│                                                            │
│ Model                                                      │
│ ┌──────────────────────────────────────────────────────┐  │
│ │ Gemma 3 1B Instruct                              ▼   │  │  <- ModelDropdown
│ └──────────────────────────────────────────────────────┘  │
│                                                            │
│ Configuration                                              │
│ ┌──────────────────────────────────────────────────────┐  │
│ │ Temperature        ━━━●─────────── 0.7              │  │
│ │ Top-K              ━━━━━●───────── 40                │  │
│ │ Max tokens         ━━━━━━━●─────── 512              │  │
│ │ Trials             [- 3 +]                           │  │
│ └──────────────────────────────────────────────────────┘  │
│                                                            │
│  ┌─────────────────────────────────┐                       │
│  │   ▶  Start Benchmark             │                       │  <- primary button
│  └─────────────────────────────────┘                       │
│                                                            │
│ Recent results                                             │
│ ┌──────────────────────────────────────────────────────┐  │
│ │ Gemma 3 1B Instruct           2026-06-06 14:23      │  │
│ │ Init: 1.8s  Prefill: 38 tok/s  Decode: 24 tok/s     │  │
│ │ Peak mem: 1.2 GB  ▁▃▅▇▇▆▅▄▃▂   (sparkline)          │  │
│ └──────────────────────────────────────────────────────┘  │
│ ┌──────────────────────────────────────────────────────┐  │
│ │ Llama 3.2 1B Instruct        2026-06-05 09:11       │  │
│ │ Init: 2.1s  Prefill: 35 tok/s  Decode: 22 tok/s     │  │
│ │ Peak mem: 1.4 GB  ▁▃▅▆▇▇▆▅▄▃                         │  │
│ └──────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────┘
```

### B.3 Component Breakdown

- **ModelDropdown** — Material 3 `ExposedDropdownMenuBox` with `LocalModelRepository.observeDownloaded()`. Disables Start button when nothing selected.
- **Configuration card** — `Card { Column { Slider (×3), Stepper } }`. Bound to a local `BenchmarkConfig` state in the VM.
  - Temperature: 0.1–2.0, step 0.1.
  - Top-K: 1–100, step 1.
  - Max tokens: 64–2048, step 64.
  - Trials: 1–10 stepper.
- **Start Benchmark button** — Material 3 `Button` (filled primary). Disabled when:
  - No model picked, OR
  - A benchmark is already running (`uiState.isRunning == true`).
  - During run, label changes to `"Running… (2/3 trials)"` with `CircularProgressIndicator` to the left.
- **Recent results list** — `LazyColumn` of `BenchmarkResultsViewer` cards.
  - Each card: model display name, timestamp (formatted "yyyy-MM-dd HH:mm"), 4 metrics in a grid, sparkline of last 10 decode-tok/s readings for that model.
  - Empty state: `"No benchmarks yet — run one above."` centered.

### B.4 Sparkline (`BenchmarkValueSeriesViewer`)

- 80dp × 24dp `Canvas`.
- Draws decode tok/s history as a smoothed `Path` (Catmull-Rom or simple bezier). Up to 10 most recent points.
- Color: `MaterialTheme.colorScheme.primary`.
- No axes, no labels — minimal visual signal of "trend".

### B.5 In-Run Feedback

While the worker runs (`uiState.isRunning == true`):

- The Start button is disabled with progress indicator and live label `"Trial 2 of 3 — Decode 24 tok/s"`.
- A small foreground service notification (from `setForeground()`) shows in the system tray with the same progress.
- On completion, the new result is inserted at the top of "Recent results" (Flow re-collects).

### B.6 Error States

- **Worker failure** (model load error, OOM, foreground denied): show a `Card` above "Recent results" with `MaterialTheme.colorScheme.errorContainer` background and the error message + a "Retry" button.
- **No downloaded models**: model dropdown shows `"No models downloaded — visit Models tab"` and Start button is disabled.

## C. Color, Typography, Motion

- **Thinking panel header** — `MaterialTheme.colorScheme.onSurfaceVariant` for icon + label; `MaterialTheme.typography.labelMedium`.
- **Thinking panel body** — `MaterialTheme.typography.bodySmall` italic; `MaterialTheme.colorScheme.onSurfaceVariant`.
- **Benchmark cards** — `MaterialTheme.colorScheme.surfaceContainer` background, `12.dp` corner radius, `8.dp` content padding.
- **Sparkline** — `2.dp` stroke width, `StrokeCap.Round`, alpha 0.85.
- **Animations** — Thinking panel expand uses default Compose easing (no custom interpolator).

## D. Accessibility

- Thinking chip: `contentDescription = "Enable model thinking trace"`.
- Thinking panel header: clickable Row with `contentDescription = "Toggle thinking trace visibility"`.
- Benchmark Start button: `contentDescription = "Start model benchmark"`.

## E. State Shape (additions)

### `ChatUiState` (additions)
```kotlin
val enableThinking: Boolean = false,            // renamed from reasoningEnabled
val supportsThinking: Boolean = false,           // derived from allowlist; gates chip visibility
```

### `BenchmarkUiState` (new)
```kotlin
data class BenchmarkUiState(
    val downloadedModels: List<LocalModel> = emptyList(),
    val selectedModel: LocalModel? = null,
    val config: BenchmarkConfig = BenchmarkConfig(),
    val trials: Int = 3,
    val isRunning: Boolean = false,
    val currentTrial: Int = 0,
    val liveDecodeTokPerSec: Float? = null,
    val results: List<BenchmarkResult> = emptyList(),
    val error: String? = null,
)
```
