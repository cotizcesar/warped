# Phase 48: Chat Perf + Startup + Release - Research

**Researched:** 2026-09-28
**Domain:** Jetpack Compose chat-list performance, Android startup (Baseline Profiles), release hardening (R8, log hygiene, network security)
**Confidence:** HIGH (codebase-verified) / MEDIUM (AndroidX API details from training)

## Summary

Phase 48 has three independent workstreams converging on one release gate: (1) chat-list performance via a `ChatUiState` sub-state split (PERF-14) plus a `Column+verticalScroll` → `LazyColumn` migration (PERF-15), which are ATOMIC because they touch the same `ChatScreen.kt`/`ChatViewModel.kt`/`ChatUiState.kt` files; (2) startup via Baseline Profiles + lazy-init verification + ≤200 ms splash (PERF-16); (3) release hardening via R8 full-mode re-verification, dependency audit, log-secret audit, and network/Keystore re-audit (HARD-01).

The codebase inspection confirms the CONTEXT diagnosis exactly: `ChatScreen.kt` today uses `Column` + `verticalScroll(scrollState)` with `animateScrollTo(maxValue)` fired on **every streaming token** (lines 171–183) — the named defect. `ChatUiState` is a single 30-field data class exposed as one `StateFlow`, so every token emission recomposes `ChatInputBar` and every keystroke recomposes the list. `ChatMessage.id` is already a stable UUID [VERIFIED: codebase], so no id-migration work is needed before keyed items. Baseline Profiles do **not** exist yet (no `profileinstaller` dep, no generator rule, no `baseline-prof.txt`) — this is the largest new-build work in the phase. The splash fade (200 ms, `AccelerateInterpolator`, `keepOnScreen=false`) is already done per PERF-11; PERF-16 only re-verifies it. Release logging is structurally safe (Timber tree planted only in DEBUG) but two debug-only log lines emit raw model content / tool metadata and must be audited or removed.

**Primary recommendation:** Do PERF-14+15 as one atomic change (split state into three `@Immutable` sub-states with single ViewModel updaters, then migrate the list to keyed `LazyColumn` with a stick-to-bottom `LazyListState` + Jump-to-latest latch); add Baseline Profiles via the standard `profileinstaller` + `macrobenchmark` generator pattern; verify release with `assembleRelease` + device smoke + scripted grep audits for secrets in logs.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- ChatUiState split into single-owner sub-states (messages/streaming vs input vs connection/models, @Immutable); streaming tokens never recompose input bar, keystrokes never recompose list
- Chat list on LazyColumn items(messages, key = { id }) with stable ChatMessage.id; streaming message as keyed trailing item (incl. Phase 47 tool-status/transcript rows as keyed items)
- Auto-scroll sticks to bottom only when already at bottom + "Jump to latest" affordance; no scroll jumps on 100+ message + code blocks + streaming
- Baseline Profiles (Macrobenchmark rules + profileinstaller) in release; no eager engine/helper init on startup path (lazy ProviderRouter verified); splash cross-fade ≤200ms
- Measure cold start on available hardware, record in BENCHMARKS.md next to PERF-12 targets (Pixel 7 reference hardware not available — emulator measurement + note)
- assembleRelease installs on real device; tool skills work in release; logcat shows no secrets/PII/raw tool arguments (tool args redacted)
- R8 full mode re-verified with 0.17.x + ToolSet/skill/mapper classes; dependency audit extended to new transitives; network_security_config + Keystore re-audited

### the agent's Discretion
- Exact sub-state boundaries and LazyColumn key strategy details; benchmark approach on emulator

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PERF-14 | `ChatUiState` split into single-owner sub-states (messages/streaming vs input vs connection/models, `@Immutable`) | Sub-state split design below; owners map to existing `_uiState.update` call sites |
| PERF-15 | `LazyColumn` with `items(messages, key = { it.id })`, streaming as keyed trailing item, stick-to-bottom + Jump-to-latest | LazyColumn migration design; `ChatMessage.id` stable UUID verified, no migration needed |
| PERF-16 | Cold start < 1 s; Baseline Profiles ship in release; lazy `ProviderRouter` verified; splash ≤ 200 ms; numbers in BENCHMARKS.md | Baseline Profile setup gap analysis; startup-path audit; emulator measurement approach |
| HARD-01 | R8 full-mode re-verify with 0.17.x + ToolSet/skill classes; dependency audit; no secrets/PII in logs; network_security_config + Keystore re-audit | R8 verification approach; log-secret audit method; config files located |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Chat list rendering / scroll-stick / pill | UI (Compose) | — | Pure presentation; `LazyListState` owned by `ChatScreen` composable |
| Streaming token accumulation | ViewModel | — | `ChatViewModel` owns the `shareIn` collector; exposes streaming sub-state |
| Input draft / skill chips | UI (Compose) + ViewModel | — | Draft text + chip toggles; must not share a `StateFlow` with streaming |
| Connection / model selection | ViewModel + DI | — | `ActiveModelSelection` flows already exist; just re-homed into a sub-state |
| Cold-start / Baseline Profiles | Build + OS runtime | — | ART profile compilation; `profileinstaller` + `baseline-prof.txt` in release APK |
| R8 / secrets / network config | Build + platform | — | `proguard-rules.pro`, `network_security_config.xml`, Keystore — static artifacts |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Jetpack Compose BOM | 2026.06.01 [VERIFIED: gradle/libs.versions.toml] | UI incl. `LazyColumn`, `LazyListState`, `@Immutable` | Already the project standard; `LazyColumn` is the only sanctioned lazy list |
| `kotlinx-collections-immutable` | 0.5.2 [VERIFIED: gradle/libs.versions.toml] | `toPersistentList()` snapshots for keyed items | Already imported in `ChatScreen.kt` (line 28); avoids `List` identity churn breaking keys |
| `androidx.profileinstaller:profileinstaller` | 1.4.x [ASSUMED] | Installs Baseline Profiles at app install/first-run | Official Google-endorsed path; required for profiles to take effect on Android 7+ without Play |
| `androidx.benchmark:benchmark-macro-junit4` | 1.3.3 [VERIFIED: app/build.gradle.kts line 188] | `MacrobenchmarkRule` + `BaselineProfileRule` for profile generation | Already a dependency (androidTest); generator reuses it |
| `androidx.test.uiautomator:uiautomator` | 2.3.0 [VERIFIED: app/build.gradle.kts line 190] | Drive the app in profile-generator tests | Already present |
| core-splashscreen | 1.2.0 [VERIFIED: gradle/libs.versions.toml] | Splash exit animation (already implemented) | No new work; re-verify only |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Layout Inspector (recomposition counts) | Android Studio built-in | Verify PERF-14 (input bar no longer recomposes on tokens) | Manual verification step per BENCHMARKS.md §6 pattern |
| `benchmark-junit4` | 1.3.3 [VERIFIED: app/build.gradle.kts] | Microbenchmarks if needed | Only if a sub-claim (e.g. `parseThinkBlocks` cost) needs isolating; not required |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Baseline Profiles | Manual `dexopt` / `compile_filter` tuning | Not app-distributable; profiles are the only sanctioned mechanism |
| `LazyColumn` | `Column` + `verticalScroll` (status quo) | Composes all 100+ items + code blocks on every token; the defect being fixed |
| Three sub-states | One `StateFlow` + `derivedStateOf` everywhere | `PERF-04` already proved `derivedStateOf` insufficient for the input-bar path; split is the locked decision |

**Installation:**
```bash
# Only addition needed (versions to confirm against Google Maven at plan time):
# release dependency:
#   androidx.profileinstaller:profileinstaller:1.4.1
# androidTest dependency (generator):
#   androidx.benchmark:benchmark-macro-junit4:1.3.3 (already present)
# Gradle plugin (root build file):
#   androidx.baselineprofile baseline-profile-gradle-plugin (version tracks benchmark lib)
```

## Package Legitimacy Audit

No new third-party (non-Google) packages are required. Baseline Profile artifacts are first-party AndroidX:

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| androidx.profileinstaller:profileinstaller | Google Maven | ~4 yrs (1.0 in 2021) | n/a (Maven) | android.googlesource.com / androidx | N/A (Maven, first-party) | Approved |
| androidx.benchmark:benchmark-macro-junit4 | Google Maven | ~4 yrs | n/a (Maven) | androidx | N/A (already a dependency) | Approved |

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none
*slopcheck targets npm/PyPI; Maven first-party Google artifacts are out of its scope. Exact `profileinstaller` patch version must be confirmed against Google Maven at plan time — tagged [ASSUMED] until then.*

## Architecture Patterns

### System Architecture Diagram

```
Launcher tap → MainActivity (splash, keepOnScreen=false, 200ms fade)
  → WarpedNavGraph → ChatScreen
      ├── TranscriptState (messages + streaming + tool rows) ──▶ LazyColumn (keyed items)
      │       ▲ updated by ChatViewModel streaming collector only
      ├── InputState (inputText + skill chips + canSend) ──────▶ ChatInputBar
      │       ▲ updated by updateInput / setSkillEnabled only
      ├── ConnectionState (models/endpoints/selection/status) ─▶ InlineModelSelectorBar + picker
      │       ▲ updated by ActiveModelSelection collectors only
      └── Jump-to-latest latch (LazyListState-derived, composable-local)
  → ProviderRouter (dagger.Lazy helpers — NOT created at startup)
  → EngineManager (loaded on first sendMessage, never on startup path)
```

### Recommended Project Structure

No new modules. All changes land in existing files:
```
app/src/main/java/com/warped/ui/chat/
├── ChatUiState.kt        # PERF-14: 3 @Immutable sub-states replace the monolith
├── ChatViewModel.kt      # PERF-14: single-owner updaters per sub-state
├── ChatScreen.kt         # PERF-14+15 ATOMIC: collect 3 flows + LazyColumn
└── components/
    ├── MessageBubble.kt  # unchanged (pixel-identical per UI-SPEC §6)
    ├── ChatInputBar.kt   # unchanged signature; new caller wiring only
    └── ToolCopy.kt       # unchanged copy source
app/src/androidTest/java/com/warped/benchmark/
├── ColdStartBenchmark.kt         # exists — tighten target to <1s, record emulator numbers
└── BaselineProfileGenerator.kt   # NEW — BaselineProfileRule, drives critical user journey
app/build.gradle.kts              # + profileinstaller dep
BENCHMARKS.md                     # PERF-16 numbers next to PERF-12 targets
```

### Pattern 1: Single-owner @Immutable sub-states (PERF-14)

**What:** Replace the single 30-field `ChatUiState` data class (`ChatUiState.kt:13–59`) collected as one `StateFlow` (`ChatViewModel.kt:59–60`, collected once at `ChatScreen.kt:73`) with three `@Immutable` data classes, each exposed as its own `StateFlow` and each mutated only by designated ViewModel functions.

**Recommended boundaries** (agent's discretion — rationale below):

```kotlin
// Source: pattern derived from ChatUiState.kt fields + ChatViewModel.kt update sites (codebase-verified)
@Immutable
data class ChatTranscriptState(
    val conversationId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),       // owner: sendMessage/Done/persist paths
    val isStreaming: Boolean = false,                    // owner: sendMessage/stopGeneration
    val streamingContent: String = "",                   // owner: Delta collector only
    val streamingReasoning: String = "",                 // owner: Delta collector only
    val toolCallActive: String? = null,                  // owner: ToolStatus/Delta/Done (Phase 47 contract)
    val activeToolError: ActiveToolError? = null,        // owner: ToolCompleted/error paths
    val showNoToolSupportNotice: Boolean = false,        // owner: per-turn gating blocks
    val error: ChatError? = null,                        // owner: error paths (stays with transcript: snackbars anchor the list)
)

@Immutable
data class ChatInputState(
    val inputText: String = "",                          // owner: updateInput + sendMessage-clear only
    val skillEnabled: Map<String, Boolean> = emptyMap(), // owner: skillRepository collector
    val reasoningEnabled: Boolean = true,                // owner: toggleReasoning
    val enableThinking: Boolean = false,                 // owner: AdvancedPreferences collector
    val supportsThinking: Boolean = false,               // owner: selection collectors
    val isGenerating: Boolean = false,                   // mirror of isStreaming for send/stop swap; owner: same turn code
)

@Immutable
data class ChatConnectionState(
    val selectedLocalModelId: String? = null,            // owner: localSelection collector
    val selectedRemoteModelId: String? = null,           // owner: remoteSelection collector
    val selectedRemoteProvider: ProviderType? = null,    // (deprecated selectedProvider/selectedModelId stay here, untouched)
    val isLocalModelLoaded: Boolean = false,
    val connectionStatus: ConnectionStatus = ...,
    val conversations: List<Conversation> = emptyList(),
    val localModels / endpoints / endpointModels / ...,
    val isLoadingModel / loadingModelName / modelLoadError / loadedInstanceId / ...,
    val generationParameters / codeTheme / codeFontScale / memoryWarningModel / ...,
)
```

**Placement rationale:** `inputText` and `streamingContent` must live in different `StateFlow`s — that is the entire PERF-14 guarantee (keystrokes recompose only `ChatInputBar`; tokens recompose only the list). Phase 47 tool states split by update frequency: `toolCallActive`/`streaming*` (per-token) join the transcript state; `skillEnabled` (rare toggle) joins input state where the chips consume it; `activeToolError`/`showNoToolSupportNotice` (per-turn) join transcript state next to the rows that render them. `ChatError.error` stays with transcript (snackbar anchors list area, and error paths already run inside the turn code). Everything else (models, endpoints, prefs, dialogs) is low-frequency connection state consumed by the selector bar/picker/dialogs.

**Single-owner rule:** each field has exactly one writer group — e.g. `streamingContent` is written only by the Delta collector and cleared only by Done/Error/stop/new-conversation turn boundaries; `inputText` only by `updateInput()` and the send-clear. The planner must enumerate the existing `_uiState.update` call sites (~30 in `ChatViewModel.kt`) and re-home each to its sub-state updater; any field written from two unrelated paths is a plan defect.

**Keep `trafficLightState()`/`trafficLightStatusText()` working:** they read cross-cutting fields (`isStreaming`, `selectedLocalModelId`, …). Re-implement as a `combine(transcript, connection)` derived flow or a composable-level `derivedStateOf` over the two collected states (the PERF-04 `derivedStateOf` precedent at `ChatScreen.kt:231–233` stays valid for this low-frequency derivation).

### Pattern 2: Keyed LazyColumn with trailing streaming item + stick logic (PERF-15)

**What:** Replace `Column` + `verticalScroll(scrollState)` (`ChatScreen.kt:276–339`) with `LazyColumn(state = listState)` using stable keys. Delete both `animateScrollTo(maxValue)` `LaunchedEffect`s (`ChatScreen.kt:171–183`).

**Key strategy** (agent's discretion):

```kotlin
// Source: ChatMessage.id stable UUID default — ChatMessage.kt:7 (codebase-verified)
LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)) {
    items(transcript.messages, key = { it.id }) { message ->
        MessageBubble(message = message, codeTheme = ..., codeFontScale = ...)  // unchanged call
    }
    // Keyed trailing streaming item (stable key "streaming"; TOOL rows are already
    // role=TOOL messages inside messages list → keyed transcript rows for free):
    if (transcript.streamingContent.isNotEmpty() || transcript.streamingReasoning.isNotEmpty()) {
        item(key = "streaming") { /* same streaming MessageBubble as today */ }
    } else if (transcript.isStreaming) {
        item(key = "tool-status") { /* same Using…/Thinking row as today */ }
    }
    if (transcript.showNoToolSupportNotice) { item(key = "no-tool-support") { NoToolSupportNotice() } }
    transcript.activeToolError?.let { item(key = "tool-error-${it.toolId}") { ToolErrorRow(...) } }
}
```

**Why this is safe:** `Role.TOOL` transcript rows are persisted `ChatMessage`s rendered inline by `MessageBubble` (`MessageBubble.kt:62–64`) — they already carry stable `id`s, so history reload shows identical rows in identical order with zero extra work. Only the transient streaming bubble / status row / error / notice need synthetic keys. `toPersistentList()` (already imported at `ChatScreen.kt:28` — note: currently misused inside `selectedModelName`, another reason for the split) should wrap `transcript.messages` once per emission so item identity is stable across recompositions.

**Stick-to-bottom + Jump-to-latest wiring (per 48-UI-SPEC §§2–3):**

- `val isAtBottom by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == listState.layoutInfo.totalItemsCount - 1 && listState.firstVisibleItemScrollOffset < ~48dp } }` — threshold per UI-SPEC §3 ("within 48dp of the end").
- On new content (messages size change, streaming length change, tool-row appearance): `if (isAtBottom) listState.scrollToItem(lastIndex)` — `scrollToItem` (instant pin), NEVER `animateScrollTo` per token (UI-SPEC §3 defect note). Send and conversation-open also `scrollToItem(lastIndex)` immediately.
- `hasNewContentBelow` latch: sets when content arrives while `!isAtBottom`, clears on reaching bottom / pill tap / send. Pill is the only new composable (48-UI-SPEC §2 exact spec: coral `0xFFD97757`, `KeyboardArrowDown` + `"Latest"`, `contentDescription = "Jump to latest message"`, ≤150 ms fade, 48dp touch target).
- Rotation: `LazyListState` is `rememberSaveable`-compatible via `rememberLazyListState()` + ViewModel-held messages — same visible message stays visible because items are keyed (unkeyed `Column+scrollState` pixel offset is what jumps today).

### Pattern 3: Baseline Profiles via Macrobenchmark generator (PERF-16)

**What:** Add the standard two-part setup — (a) `profileinstaller` release dependency so the profile ships and installs; (b) a `BaselineProfileGenerator` androidTest using `BaselineProfileRule` that drives the critical user journey (cold start → chat list visible), emitting `baseline-prof.txt` which the `baseline-profile` Gradle plugin embeds into the release APK/AAB.

**What exists vs needed** [VERIFIED: codebase]:

| Needed | Status |
|--------|--------|
| `MacrobenchmarkRule` cold-start test | EXISTS (`ColdStartBenchmark.kt`, 5 iterations, target <1.5 s — note target mismatch vs PERF-16 <1 s, see Pitfalls) |
| `StreamingFrameBenchmark` | EXISTS |
| `benchmark-macro-junit4:1.3.3` + `uiautomator:2.3.0` androidTest deps | EXIST (`app/build.gradle.kts:188–190`) |
| `profileinstaller` release dep | MISSING — must add |
| `baseline-profile` Gradle plugin | MISSING — must add (version tracks benchmark lib) |
| `BaselineProfileGenerator` rule | MISSING — must write (journey: `pressHome` → `startActivityAndWait` → wait for chat list → scroll list → open model picker) |
| `baseline-prof.txt` / `baselineProfiles` srcset | MISSING — generated output, checked in |

### Anti-Patterns to Avoid
- **Splitting PERF-14 and PERF-15 across plans** — ATOMIC constraint; same files, one review, one test pass. A split guarantees merge conflicts in `ChatScreen.kt`.
- **`animateScrollTo` on every token** — the current defect (`ChatScreen.kt:172–176`); use `scrollToItem` gated on `isAtBottom`.
- **Unkeyed `items()` or index keys** — breaks rotation stability and transcript-row expand state (UI-SPEC §6: "expand/collapse state is per-row and never leaks between rows").
- **Reading the whole monolith `uiState` in any composable after the split** — reintroduces the recomposition coupling; each composable collects only its sub-state flow.
- **Eager engine init to "warm" startup** — contradicts PERF-16; `EngineManager` must stay off the startup path (see audit below).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Lazy list virtualization | Custom `SubcomposeLayout` windowing | `LazyColumn` | Item recycling, prefetch, keyed animations, a11y traversal are years of framework work |
| Scroll-stick detection | Manual pixel math on scroll callbacks | `LazyListState.layoutInfo` + `derivedStateOf` | Canonical API; avoids frame-callback jank |
| Startup compilation | Custom dex layout / warm-service hacks | Baseline Profiles + `profileinstaller` | Only Google-sanctioned, Play-compatible mechanism |
| Log redaction | Ad-hoc string scrubbing per call site | Central `RedactingTree` (exists) + delete raw-content logs | Per-site regex rots; central tree already covers key/secret/Bearer patterns |
| Splash timing | `Handler.postDelayed` hold | `setKeepOnScreenCondition { false }` + exit animation (already done) | Artificial holds inflate cold start; current code is correct |

**Key insight:** every PERF-15/16 mechanism needed already exists as a framework API consumed elsewhere in this codebase (`LazyRow`+`items` for chips, splash exit animation, Macrobenchmark rules). The phase is wiring, not invention.

## Common Pitfalls

### Pitfall 1: BENCHMARKS.md target mismatch (1.5 s vs 1 s)
**What goes wrong:** `BENCHMARKS.md:7` says cold-start target `<1.5s`; PERF-16 and CONTEXT say `<1s`. Plan verifies the wrong number.
**How to avoid:** Planner must treat PERF-16's `<1s` as normative and update `BENCHMARKS.md` §1 target line + `ColdStartBenchmark.kt` KDoc in the same change. Emulator numbers get an explicit "emulator, not Pixel 7 reference" note per CONTEXT.

### Pitfall 2: `ChatMessage.id` instability on reload
**What goes wrong:** Keys work in-memory but Room reload regenerates ids → list jumps, transcript rows reorder.
**Why it happens:** Only if the Room entity ↔ domain mapping creates new `ChatMessage()` without preserving id.
**How to avoid:** Verify the `MessageEntity → ChatMessage` mapper preserves `id` (stable UUID default at `ChatMessage.kt:7` covers new messages). Planner: add a mapper check as a Wave-0 verification step. **Warning signs:** duplicate/blank bubbles after conversation switch.

### Pitfall 3: Streaming bubble duplication on rotation
**What goes wrong:** `streamingContent` survives rotation (ViewModel) but the `shareIn` collector re-emits → double bubble.
**Why it happens:** The shareIn is scoped to the generation job (correct per 46-01 comments); rotation doesn't restart the job, but a naive LazyColumn key on content hash would duplicate.
**How to avoid:** Single stable key `"streaming"` for the trailing item regardless of content; content is just a parameter. UI-SPEC §6 explicitly calls this out ("Rotation never duplicates the streaming bubble").

### Pitfall 4: R8 full-mode stripping of `@Tool` reflection surface
**What goes wrong:** Release build strips `ToolSet`/`@Tool`/`ReflectionTool` methods; skills silently dead in release while debug works.
**Why it happens:** R8 full mode is more aggressive than compat mode about reflective access it can't see statically.
**How to avoid:** Keeps already exist (`proguard-rules.pro:30–36` ToolSet/OpenApiTool/Tool/ToolParam/ReflectionTool/ToolKt/Capabilities + `:81–85` skill packages). HARD-01 re-verification = `assembleRelease` → install → run each skill (calculator/time/JSON) → confirm. Any new mapper class added in Phase 47+ needs a keep — audit `data/skills/` + `LmStudioToolLoop.kt` against the rules file.

### Pitfall 5: Raw tool arguments in logcat
**What goes wrong:** `LocalToolExecutor` / `LmStudioToolLoop` log lines include tool names (safe) but a future/current line could interpolate raw `args` JSON — PII (calculator expressions, JSON payloads) in logcat.
**Current state** [VERIFIED: codebase grep]: `LocalToolExecutor.kt:76` logs only `descriptor.id` (safe); `LmStudioToolLoop` logs only status shapes like "malformed tool args — content fallback" without payload (safe); BUT `LiteRTLmProvider.kt:345` logs `content.takeLast(100)` — raw model output in logcat — and `:224` logs request shape (counts only, safe). In release no Timber tree is planted (safe by construction), but debug logcat on a shared device/emulator still leaks content.
**How to avoid:** Delete or content-strip the `:345` delta log; keep the audit grep as a release-gate check (see HARD-01 method below).

### Pitfall 6: Emulator cold-start numbers treated as device numbers
**What goes wrong:** Emulator ART compilation behavior (no dexopt profiles, host-CPU variance) makes numbers non-comparable to the Pixel 7 reference.
**How to avoid:** Record emulator numbers with full environment note (AVD config, host CPU/RAM, build type) in BENCHMARKS.md; mark Pixel-7 numbers as still-TODO for CI. Compare emulator→emulator (before/after profile) rather than emulator→target.

## Code Examples

### Keyed LazyColumn with stick-to-bottom (canonical pattern)

```kotlin
// Source: androidx.compose.foundation.lazy docs (LazyListState.layoutInfo) — adapt to ChatScreen
val listState = rememberLazyListState()
val scope = rememberCoroutineScope()
val isAtBottom by remember {
    derivedStateOf {
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()
        last != null && last.index == info.totalItemsCount - 1 &&
            (listState.firstVisibleItemScrollOffset < 48.dp.roundToPx-ish threshold)
    }
}
// On new content:
LaunchedEffect(transcript.messages.size, transcript.streamingContent.length) {
    if (isAtBottom) listState.scrollToItem(index = <lastIndex>)  // instant pin, not animateScrollTo
    else hasNewContentBelow = true
}
```

### BaselineProfileRule generator (canonical shape)

```kotlin
// Source: androidx.benchmark.macro.junit4.BaselineProfileRule docs — new file BaselineProfileGenerator.kt
rule.collect("com.warped.app", includeInStartupProfile = true) {
    pressHome()
    startActivityAndWait()
    // critical journey: wait for chat list, scroll, open model picker
    device.findObject(By.text("...")).wait(Until.hasText(...), 5_000)
}
```

### Splash exit (already correct — re-verify only)

```kotlin
// Source: MainActivity.kt:25-38 (codebase-verified, PERF-11 done)
splashScreen.setKeepOnScreenCondition { false }
splashScreen.setOnExitAnimationListener { splashProvider ->
    ObjectAnimator.ofFloat(splashProvider.view, "alpha", 1f, 0f).apply {
        interpolator = AccelerateInterpolator(); duration = 200L
    } ... splashProvider.remove()
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `Column` + `verticalScroll` + `animateScrollTo` per token | Keyed `LazyColumn` + `scrollToItem` gated on `isAtBottom` | This phase | O(visible) composition; no scroll judder |
| Single `ChatUiState` + one `StateFlow` | Three `@Immutable` sub-states, single-owner updaters | This phase | Token/keystroke recomposition isolation |
| No Baseline Profiles | `profileinstaller` + generated `baseline-prof.txt` in release | This phase | ART pre-compilation of startup + journey |
| `proguard-android-optimize.txt` + keeps (compat assumptions) | Same file + explicit R8 full-mode verification on device | This phase | Skills/reflection proven working under full mode |

**Deprecated/outdated:** nothing in this phase's scope. Note: project AGENTS.md `STACK.md` snapshot references llama.cpp/GGUF-era stack — the codebase has since migrated to LiteRT-LM; research above follows the actual codebase, not the stale snapshot.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `profileinstaller` latest stable is 1.4.x; `baseline-profile` Gradle plugin version tracks the benchmark lib | Standard Stack | Low — planner confirms exact coordinates against Google Maven before adding |
| A2 | R8 full mode is active by default under the project's AGP version (no explicit flag found in `app/build.gradle.kts`) | Pitfalls/HARD-01 | Medium — planner must confirm via build output (`R8 full mode` marker) or add explicit flag; verification approach is identical either way |
| A3 | Room `MessageEntity → ChatMessage` mapper preserves `id` (not re-read during research) | Pitfall 2 | Medium — planner adds a mapper check as Wave-0 verification |
| A4 | `derivedStateOf { ... firstVisibleItemScrollOffset < 48dp }` is sufficient for the UI-SPEC "at bottom" definition | Pattern 2 | Low — threshold is tunable in one line; UI-SPEC pins the observable behavior |

## Open Questions

1. **Exact sub-state field placement for `generationParameters`, `codeTheme`, `conversations`**
   - What we know: low-frequency consumers (settings screens observe via their own ViewModels in some cases; chat screen reads theme + params).
   - What's unclear: whether any non-chat screen reads `ChatViewModel.uiState` (which would break if the type changes).
   - Recommendation: planner greps for `uiState` collectors outside `ChatScreen.kt` first; if none, keep all three sub-states in `ChatViewModel` and delete the monolith. If external collectors exist, keep a deprecated typealias/combined flow for one phase.

2. **Macrobenchmark module placement**
   - What we know: benchmarks live in `:app/src/androidTest` with macro deps already wired.
   - What's unclear: whether the team prefers a separate `:macrobenchmark` module (Google's recommended template) vs in-app `androidTest`.
   - Recommendation: stay in `:app/src/androidTest` (zero module scaffolding, deps already present); the generator rule co-locates with `ColdStartBenchmark.kt`.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK (Gradle builds) | assembleRelease, benchmarks | ✓ | via linuxbrew java | — |
| Android SDK + emulator | PERF-16 emulator measurement | ✗ (no `adb`/`emulator` on this machine) | — | Run on maintainer's machine / CI; record env note |
| Physical device | HARD-01 release smoke | ✗ (no `adb` devices here) | — | Maintainer device; `assembleRelease` APK still verifiable in CI |
| Google Maven access | Confirm profileinstaller version | ? (not probed) | — | Pin version at plan time from connected machine |

**Missing dependencies with no fallback:**
- A device/emulator on the executing machine for PERF-16 measurement and HARD-01 smoke — the plan must either execute those steps where hardware exists or record them as human-verified checkpoints with exact commands.

**Missing dependencies with fallback:** none.

## Startup-Path Audit (PERF-16 input)

Eager singletons verified in DI modules [VERIFIED: codebase]: `BackendDetector`, `EngineManager` (wraps `LiteRTLmEngine`), `InputSanitizer`, caches, OkHttp clients, Room/WorkManager/Hilt infrastructure. Findings:

- `WarpedApplication` injects `EngineManager` as `@Inject lateinit var` (`WarpedApplication.kt:23`) — Hilt constructs the `EngineManager` graph node at Application creation. `EngineManager` construction itself is lightweight (holds `LiteRTLmEngine` + detector + context refs; no model load until `switchToLiteRT`), but `LiteRTLmEngine` construction cost was not audited — planner must confirm its constructor does no native `System.loadLibrary` / model mmap (the `UnsatisfiedLinkError` catch at `LiteRTLmEngine.kt:29` suggests lazy native binding, which is good).
- `ProviderRouter` already uses `dagger.Lazy<>` for all three helpers (`ProviderRouter.kt:20–24`) — CONTEXT's "lazy ProviderRouter verified" claim holds; re-verify, don't rebuild.
- `onCreate()` does notification-channel creation + Timber/StrictMode(DEBUG only) — no engine, network, or DB work on the path. Correct.
- Splash: `setKeepOnScreenCondition { false }` + 200 ms alpha fade + `remove()` — already meets the ≤200 ms spec; PERF-16 work is measurement, not implementation.
- Deferred verifications landing here (Phase 45 device smoke, 46 stop/rotation, 47 airplane-calculator + LM Studio loop) are execution-time checklists, not research items — noted so the planner adds them as release-gate tasks.

## R8 Verification Approach (HARD-01 input)

1. Confirm R8 mode: check AGP version in `gradle/libs.versions.toml` + build log for full-mode marker (Assumption A2); record the answer in the plan.
2. `assembleRelease` must succeed with current `proguard-rules.pro` (LiteRT 0.17.x keeps `:16–40`, skill keeps `:81–85`, serialization/Room/OkHttp/Hilt/Coroutines keeps — all present and commented with provenance).
3. Install release on device; smoke: cold start → chat list → send message (local) → toggle each skill chip (Calculator, Current time, JSON format) → confirm tool rows render (proves `ToolSet`/`@Tool`/`ReflectionTool` + `com.warped.data.skills.**` + `com.warped.domain.skills.**` survived).
4. Extend `scripts/audit-dependencies.sh`: direct-declaration check auto-covers `profileinstaller` (must NOT match banned patterns — it won't); release-graph check must be re-run since `profileinstaller` + `baseline-profile` plugin add transitives; any new pre-release (alpha/beta/RC) transitive fails the gate per existing rule.
5. Keystore re-audit: `KeystoreManager.kt` + `ApiKeyStore.kt` in `data/local/security/`; confirm no API change since last audit and that `ProviderRouter` key handling (`getKey` → `fill('0')` zeroing at `ProviderRouter.kt:33`) is unchanged.
6. `network_security_config.xml`: `cleartextTrafficPermitted="true"` at base-config with documented LM-Studio-on-LAN rationale (ENDPT-06 comment in file). Re-audit = confirm the rationale still holds (sole remote provider is LAN HTTP) and no new remote endpoint type needs pinning; no change expected.

## Log-Secret Audit Method (HARD-01 input)

1. Gate command (planner to run pre-release):
   ```bash
   grep -rn "Timber\.[dew]" app/src/main/java --include="*.kt" \
     | grep -iE "\$\{?(content|prompt|message|arg|input|text|key|token|secret|password|bearer)" 
   ```
2. Known findings to disposition [VERIFIED: codebase grep]:
   - `LiteRTLmProvider.kt:345` — `Timber.d(... content.takeLast(100))` — raw model output → DELETE or strip to length-only.
   - `LiteRTLmProvider.kt:224` — counts only (`images.size`, booleans) → safe, keep.
   - `LocalToolExecutor.kt:76` (`executing %s`, id only), `:42`/`:50` (failure shapes, no args) → safe, keep. Confirm no log line interpolates tool `args` JSON — none found.
   - `LmStudioToolLoop.kt` — status-shape logs only, no payloads → safe, keep.
   - `ChatViewModel.kt:390` (`reasoningActive=%b`), `:1121` (preset tier/numbers) → safe, keep.
3. Structural guarantee: `RedactingTree` (key/secret/token/Bearer patterns) is planted DEBUG-only (`WarpedApplication.kt:41`); release plants no tree → Timber is a no-op in release. The audit still matters for debug-logcat hygiene (HARD-01 explicitly names logcat).

## Emulator Measurement Approach (PERF-16 input)

1. Command: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.warped.benchmark.ColdStartBenchmark` (from `BENCHMARKS.md:9`); 5 iterations, `StartupMode.COLD`, `StartupTimingMetric` + `FrameTimingMetric`.
2. Before/after protocol: measure WITHOUT profile first (baseline), then generate profile (`BaselineProfileGenerator`), rebuild release with profile, measure again — the delta is the claim, not the absolute number.
3. Record in `BENCHMARKS.md` next to PERF-12 targets: `timeToInitialDisplayMs` median, environment block (AVD image/API/ABI, host CPU/RAM, build type), and an explicit "emulator — Pixel 7 reference numbers still TODO via CI" note. Also fix the §1 target line `<1.5s` → `<1s` per PERF-16.
4. Layout Inspector recomposition check (BENCHMARKS.md §6 pattern) doubles as the PERF-14 proof: input-bar recomposition count must not move during streaming; list item count must stay O(visible).

## Security Domain

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | Partial | API keys in EncryptedSharedPreferences/Keystore (`ApiKeyStore`, `KeystoreManager`); re-audit only |
| V3 Session Management | No | No sessions; local-first app |
| V4 Access Control | No | Single-user device app |
| V5 Input Validation | Yes | `InputSanitizer`; tool-arg validation (HARD-02, done) — untouched by this phase |
| V6 Cryptography | Yes | Android Keystore; no new crypto in this phase — re-audit only |

### Known Threat Patterns for Kotlin/Compose + LiteRT-LM stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| PII/secrets in logcat (tool args, prompts, keys) | Information disclosure | Central `RedactingTree` + delete raw-content logs + pre-release grep gate |
| R8 stripping reflection used by `@Tool` skills | Tampering/DoS (silent feature loss) | Explicit keeps + release device smoke per skill |
| Cleartext LAN traffic interception | Information disclosure | Documented exception for LM Studio LAN HTTP; system trust anchors otherwise; no sensitive data beyond user prompts to user-chosen endpoints |
| Baseline profile poisoning | Tampering | Profile is generated locally from own macrobenchmark, checked into git, reviewed like code |

## Sources

### Primary (HIGH confidence)
- Codebase: `ChatScreen.kt`, `ChatUiState.kt`, `ChatViewModel.kt`, `ChatMessage.kt`, `MessageBubble.kt` (+`ToolCopy.kt`), `ChatInputBar.kt`, `MainActivity.kt`, `WarpedApplication.kt`, `ProviderRouter.kt`, `proguard-rules.pro`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `network_security_config.xml`, `AndroidManifest.xml`, `InferenceModule.kt`, `ColdStartBenchmark.kt`, `BENCHMARKS.md`, `scripts/audit-dependencies.sh`, `48-CONTEXT.md`, `48-UI-SPEC.md`

### Secondary (MEDIUM confidence)
- AndroidX Baseline Profiles / `BaselineProfileRule` / `profileinstaller` / `LazyColumn` keyed-items / splash exit API — training knowledge, patterns cross-checked against in-repo usage (Macrobenchmark rules, splash code, LazyRow items)

### Tertiary (LOW confidence)
- None — all load-bearing claims are codebase-verified; version pins flagged [ASSUMED] where training-sourced

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - all current deps verified in `libs.versions.toml`/`build.gradle.kts`; one new dep version [ASSUMED]
- Architecture: HIGH - split boundaries derived from actual `_uiState.update` call sites; key strategy grounded in verified stable `ChatMessage.id`
- Pitfalls: HIGH - each pitfall cites exact file:line evidence
- Baseline Profiles: MEDIUM - setup absent (verified gap); API details from training, standard Google-documented pattern

**Research date:** 2026-09-28
**Valid until:** ~30 days (stable domain; AndroidX versions drift slowly)
