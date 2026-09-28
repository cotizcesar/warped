---
phase: 48-chat-perf-startup-release
reviewed: 2026-09-28T12:00:00Z
depth: standard
files_reviewed: 13
files_reviewed_list:
  - app/src/main/java/com/warped/ui/chat/ChatUiState.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/test/java/com/warped/ui/chat/ChatSubStateTest.kt
  - app/src/test/java/com/warped/ui/chat/ChatKeyStabilityTest.kt
  - app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt
  - app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt
  - app/proguard-rules.pro
  - app/build.gradle.kts
  - gradle/libs.versions.toml
  - app/src/androidTest/java/com/warped/benchmark/BaselineProfileGenerator.kt
  - app/src/main/baselineProfiles/baseline-prof.txt
  - app/src/main/java/com/warped/WarpedApplication.kt
findings:
  critical: 2
  warning: 8
  info: 3
  total: 13
status: issues_found
---

# Phase 48: Code Review Report

**Reviewed:** 2026-09-28T12:00:00Z
**Depth:** standard
**Files Reviewed:** 13
**Status:** issues_found

## Summary

Reviewed the three Phase 48 workstreams: 48-01 sub-state split + keyed LazyColumn, 48-02 Baseline Profiles + lazy native load, 48-03 log strip + R8/audit re-verification. The split routing itself is sound (token writes stay on `_transcript`, keystrokes on `_input`, isolation tests prove reference stability), the trailing-key scheme is content-independent, and the `takeLast(100)` payload leak is genuinely gone. But adversarial tracing found two ship-blocking defects: a reasoning-only stream renders the empty state instead of the bubble (`isEmpty` ignores the reasoning channel the same file already uses for `showStreamingBubble`), and the lazy native-load fix swallows `UnsatisfiedLinkError` and then walks straight into `Engine()` construction whose catch clauses cannot catch `Error` — turning a handled dlopen failure into an uncaught crash. Eight warnings cover stale-snapshot reads, fail-closed capability checks, stale tool-state clearing, nullable-name interpolation, and hardening gaps in log redaction and benchmark versioning.

## Critical Issues

### CR-01: Reasoning-only stream renders the empty state — streaming bubble invisible

**File:** `app/src/main/java/com/warped/ui/chat/ChatScreen.kt:229`
**Issue:** `isEmpty` is `messages.isEmpty() && streamingContent.isEmpty()`, but `showStreamingBubble` (line 201) is `streamingContent.isNotEmpty() || streamingReasoning.isNotEmpty()`. A thinking-model turn that has emitted reasoning with no answer text yet satisfies `showStreamingBubble == true` while `isEmpty == true`, so the `if (isEmpty)` branch renders the logo empty state and the `LazyColumn` (and the reasoning bubble, the pill, and the scroll effect) never exists. The stick-gating effect also early-returns on `totalItems == 0`, and `totalItems` counts the streaming bubble — but the branch that would render it is unreachable. Same hole applies to the tool-status row: an active `toolCallActive` with empty content still shows the empty state.
**Fix:**
```kotlin
val isEmpty = transcript.messages.isEmpty()
    && transcript.streamingContent.isEmpty()
    && transcript.streamingReasoning.isEmpty()
    && transcript.toolCallActive == null
    && transcript.activeToolError == null
    && !transcript.showNoToolSupportNotice
```

### CR-02: Swallowed native-load failure becomes an uncaught Error crash in init()

**File:** `app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt:40-47,148-161`
**Issue:** `ensureNativeLoaded()` catches `UnsatisfiedLinkError` (an `Error`, not an `Exception`), logs, and returns with `nativeLoaded == false`. `init()` then proceeds unconditionally to `Engine(config)` (line 148), which re-attempts the same missing-native load and throws `UnsatisfiedLinkError` again — but both catch clauses (`LiteRtLmJniException`, `Exception`) cannot catch `java.lang.Error`, so it propagates uncaught out of `init()`. Every caller (`EngineManager.switchToLiteRT`, `LiteRTLmProvider` recovery path) catches `Exception` only, so a device missing `liblitertlm_jni.so` crashes instead of surfacing the handled "engine failed" path. Additionally `nativeLoaded` stays false, so each retry repeats the doomed dlopen.
**Fix:**
```kotlin
@Synchronized
private fun ensureNativeLoaded() {
    if (nativeLoaded) return
    try {
        System.loadLibrary("litertlm_jni")
        Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
        nativeLoaded = true
    } catch (e: UnsatisfiedLinkError) {
        Timber.e(e, "LiteRTLmEngine: native lib not found")
        throw IllegalStateException("Native litertlm_jni library unavailable", e)
    }
}
```
(`IllegalStateException` is already the documented failure type of the provider's engine-error retry path.)

## Warnings

### WR-01: sendMessage reads thinking flags from a stale snapshot while skills read fresh state

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:254,439`
**Issue:** `state = snapshot()` is captured at method entry, and line 439 passes `enableThinking = state.enableThinking && state.supportsThinking` from that stale copy — while `skillEnabled`, `generationParameters`, and `reasoningEnabled` are re-read fresh from `_input.value`/`_connection.value` at lines 366-379/427. A thinking-toggle flip racing `sendMessage` dispatch is silently lost for this turn (and the summary's "mirror flips at exactly the turn-boundary points" claim does not cover this read).
**Fix:** Read `enableThinking = _input.value.enableThinking && _input.value.supportsThinking` at line 439, consistent with the surrounding fresh reads.

### WR-02: Image/audio capability check fail-closes when the model list has not loaded yet

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:299,310`
**Issue:** `state.localModels.firstOrNull { ... }?.capabilities` is `null` both when the model genuinely lacks the capability AND when `localModels` has not arrived from `observeModels()` yet. `capabilities?.vision != true` treats both identically, so sending an image immediately after cold start against a vision-capable model is spuriously rejected with "does not support images". Unknown (not-yet-loaded) must be fail-open or deferred, not fail-closed.
**Fix:**
```kotlin
val capabilities = state.localModels.firstOrNull { it.filePath == modelId }?.capabilities
if (capabilities == null) {
    Timber.w("ChatVM: capabilities unknown for $modelId — skipping media gate")
} else if (images.isNotEmpty() && !capabilities.vision) { ... }
```

### WR-03: supportsThinkingFor is a presence check, not a capability check

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:937-938`
**Issue:** `supportsThinkingFor` returns `localId != null || remoteId != null` — every selected model advertises thinking support, so the Thinking toggle is offered for models whose capabilities lack reasoning. Combined with WR-01's stale read, non-reasoning models can enter the thinking path.
**Fix:** Consult `localModels` capabilities (`capabilities?.reasoning == true`, mirroring the `modelMayThink` check at line 428) and default remote models per allowlist until remote capability data exists.

### WR-04: Stale tool/notice state survives stopGeneration and confirmModelSwitch

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:625-645,768-790`
**Issue:** `stopGeneration()` clears `toolCallActive`/`activeToolError` but not `showNoToolSupportNotice`, so stopping mid-turn leaves the notice row pinned under the next turn's bubble until the next `sendMessage`. `confirmModelSwitch()` clears `error` but none of `toolCallActive`/`activeToolError`/`showNoToolSupportNotice`, so a model switch during an active "Using calculator…" row leaves a stale tool-status row against the new model. `Done` likewise never clears `activeToolError` (only `sendMessage`/`stopGeneration` do), so a single failure stains every subsequent successful render until the next send.
**Fix:** Clear all three transient tool fields in all three terminal paths (`stopGeneration`, `confirmModelSwitch`, `Done`).

### WR-05: Traffic-light status interpolates a nullable model name — renders literal "null"

**File:** `app/src/main/java/com/warped/ui/chat/ChatUiState.kt:257,262,267`
**Issue:** `localName` is `…firstOrNull { … }?.name` (nullable when the model list has not loaded or the id is unknown), then interpolated into `"Local: $localName — Connected"`. Until `observeModels()` emits, the inline selector bar reads "Local: null — Not connected". Same for `remoteName` (`selectedRemoteModelId?.substringAfterLast("/")` is null when nothing is selected, though that path is guarded by `isRemote`).
**Fix:**
```kotlin
val localName = connection.localModels.firstOrNull { it.filePath == connection.selectedLocalModelId }?.name
    ?: connection.selectedLocalModelId?.substringAfterLast("/") ?: "Unknown model"
```

### WR-06: deleteMessage compares Long against String-UUID ids — fresh messages never match locally

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:962-969`
**Issue:** `state.messages.filter { it.id != messageId.toString() }` assumes every row id is a Room-numeric string. `ChatMessage.id` defaults to `UUID.randomUUID().toString()` (ChatMessage.kt:7), so a just-sent message that has not been reloaded from Room keeps its UUID id; deleting it removes the DB row but the local filter matches nothing and the bubble stays on screen until the next reload. The signature (`messageId: Long`) cannot even express a UUID row.
**Fix:** Change the signature to accept the row's `String` id (`fun deleteMessage(messageId: String)`) and compare directly; keep a `Long` overload for Room-sourced call sites if needed.

### WR-07: RedactingTree + log gate miss endpoint URLs, query strings, and error bodies

**File:** `app/src/main/java/com/warped/WarpedApplication.kt:99-108`
**Issue:** The 48-03 gate greps only `Timber.[dew]` lines and the tree redacts only `api_key|secret|token|authorization` assignments plus `Bearer` tokens. Unredacted paths provable in-tree: `HuggingFaceRepositoryImpl.kt:47` logs the full HF error body, `:51/:65` log raw `query`/`author`/`modelId` strings; `EngineManager.kt:65` logs the absolute model path; `LMStudioProvider.kt:381/383` log full request URLs (endpoint host + path). None match the redaction regexes. Release builds plant no tree (Timber no-ops), so exposure is debug-build logcat — still a reversible-secret-adjacent surface (endpoint URLs betray LAN topology; error bodies echo server content).
**Fix:** Extend the gate grep to `Log\.` direct usage and add URL/query redaction (`(hf_token|access_token)=…`, full-URL host preservation) to `RedactingTree`, or downgrade the Task-1 gate claim to "payload-shape logs only".

### WR-08: Benchmark deps bypass the version catalog with hardcoded literals

**File:** `app/build.gradle.kts:192-193`
**Issue:** `androidTestImplementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")` and `benchmark-junit4:1.5.0` hardcode group:artifact:version literals while every other dependency resolves via `libs.*`. The next benchmark bump now requires editing two version sources (catalog `baselineprofile` + these literals), and the audit script's pre-release regex cannot see them as catalog entries.
**Fix:**
```kotlin
androidTestImplementation(libs.benchmark.macro.junit4)
androidTestImplementation(libs.benchmark.junit4)
```
with matching `[libraries]` entries pinned to `version.ref = "baselineprofile"`.

## Info

### IN-01: Isolation test loops 100x — `return@repeat` does not break the polling loop

**File:** `app/src/test/java/com/warped/ui/chat/ChatSubStateTest.kt:175-182`
**Issue:** `return@repeat` returns from the current iteration's lambda, not the loop — after `sawStreaming = true` the test keeps pumping `runCurrent()` + `advanceTimeBy(50)` for the remaining iterations, advancing the test clock ~5s past the assertion point. Harmless today (assertion already latched), but the dead-iteration pattern masks timing sensitivity the test claims to prove.
**Fix:** Replace `repeat(100)` with a `for (i in 0 until 100) { …; if (…) break }` loop.

### IN-02: Pill click target lacks an accessibility role

**File:** `app/src/main/java/com/warped/ui/chat/ChatScreen.kt:566-576`
**Issue:** `JumpToLatestPill` uses `.clickable(onClick = onClick)` with `contentDescription` + `liveRegion` semantics but no `role = Role.Button`, so screen readers announce descriptive text without button affordance.
**Fix:** `.clickable(role = Role.Button, onClick = onClick)` (import `androidx.compose.ui.semantics.Role`).

### IN-03: Seed baseline profile covers only Application/Activity onCreate — chat path unprofiled until hardware run

**File:** `app/src/main/baselineProfiles/baseline-prof.txt:1-4`
**Issue:** The checked-in 4-rule seed precompiles only the launch path; the generator journey (wizard skip, chat list scroll, model picker) exists solely as unrunnable-on-this-hardware code. Release builds until the hardware run get startup-only AOT with zero chat-path methods. This is tracked as a TODO in BENCHMARKS.md, but the profile file itself carries no marker, so a future reader may mistake the seed for the generated artifact.
**Fix:** Add a header comment to `baseline-prof.txt` (`# SEED — replace via generateReleaseBaselineProfile on rooted hardware; see BENCHMARKS.md §1`).

---

_Reviewed: 2026-09-28T12:00:00Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
