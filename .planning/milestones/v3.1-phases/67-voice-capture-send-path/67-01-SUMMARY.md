---
phase: 67-voice-capture-send-path
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/warped/ui/chat/voice/VoiceMessageRecorder.kt
  - app/src/main/java/com/warped/ui/chat/voice/PcmTranscoder.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/test/java/com/warped/ui/chat/voice/VoiceMessageRecorderTest.kt
  - app/src/test/java/com/warped/ui/chat/voice/PcmTranscoderTest.kt
autonomous: true
requirements: [VMSG-01, VMSG-05]
must_haves:
  truths:
    - "User taps the voice-send button and records with a live timer, stopping with a second tap; the clip file is kept for send"
    - "User sends the voice clip and the first 30 s reach the audio-capable local model as mono 16 kHz PCM via the existing audioBytes path, returning a model response"
    - "Recorder never leaks past the screen and never blocks the UI thread"
  artifacts:
    - path: "app/src/main/java/com/warped/ui/chat/voice/VoiceMessageRecorder.kt"
      provides: "VM-owned MediaRecorder wrapper (start/stop/cancel, amplitude, 60s cap callback)"
      exports: ["VoiceMessageRecorder", "RecorderFactory"]
    - path: "app/src/main/java/com/warped/ui/chat/voice/PcmTranscoder.kt"
      provides: "AAC to mono-16kHz-PCM decode plus first-30s truncation"
      exports: ["transcodeFirst30s", "resampleTo16kMono"]
    - path: "app/src/main/java/com/warped/ui/chat/ChatViewModel.kt"
      provides: "Recording state plus transcode-and-send wiring into sendMessage"
      contains: "startVoiceRecording"
  key_links:
    - from: "app/src/main/java/com/warped/ui/chat/ChatViewModel.kt"
      to: "ui/chat/voice/VoiceMessageRecorder.kt"
      via: "lazy holder plus onCleared destroy, mirroring dictationManager"
      pattern: "getDictationManager"
    - from: "app/src/main/java/com/warped/ui/chat/ChatViewModel.kt"
      to: "ui/chat/voice/PcmTranscoder.kt"
      via: "Dispatchers.IO transcode call inside sendVoiceMessage before sendMessage"
      pattern: "Dispatchers.IO"
    - from: "app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt"
      to: "app/src/main/java/com/warped/ui/chat/ChatViewModel.kt"
      via: "voice button onClick plus recording-state params (dead audio params come alive)"
      pattern: "onAudioRecordingChanged"
subsystem: voice-capture
tags: [voice, mediarecorder, pcm, transcode, chat]
dependency_graph:
  requires: []
  provides:
    - voice-recorder-tracer
    - pcm-transcode-path
    - voice-send-wiring
  affects:
    - 67-02-ux-guards
    - phase-68-voice-draft
tech_stack:
  added: []
  patterns:
    - "VM-owned platform wrapper with injected factory (mirrors VoiceDictationManager)"
    - "Pure-function DSP core with thin platform shell (JVM-testable resample/truncate)"
key_files:
  created:
    - app/src/main/java/com/warped/ui/chat/voice/VoiceMessageRecorder.kt
    - app/src/main/java/com/warped/ui/chat/voice/PcmTranscoder.kt
    - app/src/test/java/com/warped/ui/chat/voice/VoiceMessageRecorderTest.kt
    - app/src/test/java/com/warped/ui/chat/voice/PcmTranscoderTest.kt
  modified:
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
decisions:
  - "Hardcoded tracer copy (record/stop descriptions, transcode-failure and first-30s snackbars) ships in Plan 01 and is fully resourced to voice_msg_* strings in Plan 02 Task 3"
  - "ChatScreen onSend routes a kept voice clip through sendVoiceMessage(caption); text/image path unchanged, hasContent/send-button logic unchanged"
  - "Pre-existing ModelSwitchUnloadTest full-suite race documented as deferred, not fixed (fails identically on base sources)"
metrics:
  duration: "~45 min"
  completed: "2026-10-02"
---

# Phase 67 Plan 01: Voice Tracer (Record → Transcode → Send) Summary

**One-liner:** VM-owned MediaRecorder capture into filesDir/voice with 60 s auto-stop-and-keep, AAC→mono-16kHz first-30s transcode on Dispatchers.IO, and send through the existing audioBytes path behind a GraphicEq toggle.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | VoiceMessageRecorder plus recorder unit test | 202850a7 | VoiceMessageRecorder.kt, VoiceMessageRecorderTest.kt |
| 2 | PcmTranscoder plus PCM-math unit test | fd2bfc0f | PcmTranscoder.kt, PcmTranscoderTest.kt |
| 3 | ChatViewModel recording state plus minimal send UI hookup | b5192ac9 | ChatViewModel.kt, ChatInputBar.kt, ChatScreen.kt |

## What Was Built

- **VoiceMessageRecorder** (`ui/chat/voice/`): coroutine-free MediaRecorder wrapper with injectable `RecorderFactory` seam. `start()` writes `filesDir/voice/vm-<epoch>.m4a` (MIC/MPEG_4/AAC 128 kbps) returning Boolean; `stop()` keeps the file (RuntimeException-safe, always releases); `cancel()` deletes immediately; `maxAmplitude()` best-effort; `destroy()` idempotent. Double-start ignored (single-flight in recorder + VM `voiceStarting` flag).
- **PcmTranscoder** (`ui/chat/voice/`): pure `resampleTo16kMono` (channel-average downmix + linear-interp to 16 kHz) and `truncateTo30s` (480 000-sample cap, exactly-30s not flagged), plus platform `transcodeFirst30s` (MediaExtractor + synchronous MediaCodec loop reading the ACTUAL decoded format, EOS handling, ~30 s watchdog, codec/extractor released in finally, `TranscodeException` on corrupt/empty input).
- **ChatViewModel wiring:** lazy recorder holder mirroring `getDictationManager`; `isVoiceRecording`/`voiceElapsedSec`/`voiceAmplitude`/`hasVoiceClip` flows; `voiceCapEvent` one-shot SharedFlow; 1 s ticker (60 s → `autoStopVoiceRecording`) + 100 ms sampler as children of a session Job cancelled on every exit path; `startVoiceRecording` stops dictation first and flips state only on platform accept; `sendVoiceMessage(caption)` transcodes on Dispatchers.IO then calls `sendMessage(caption, audioBytes)`, keeping the file on transcode/send failure; `onCleared` cancels jobs and destroys the recorder.
- **Minimal UI:** GraphicEq voice-send button beside the dictation mic (same visibility conditions, Stop toggle while recording); ChatScreen collects voice state, gates start on the RECORD_AUDIO runtime grant (no-op otherwise — full rationale flow is Plan 02), and routes a kept clip through `sendVoiceMessage` on send.

## Verification Evidence

- `./gradlew :app:testDebugUnitTest --tests "com.warped.ui.chat.voice.*"`: **BUILD SUCCESSFUL** (15 tests: 7 recorder + 8 transcoder).
- `./gradlew :app:testDebugUnitTest --tests "com.warped.ui.chat.ModelSwitchUnloadTest" --tests "com.warped.ui.chat.VoiceDictationTest" --tests "com.warped.ui.chat.voice.*"`: **BUILD SUCCESSFUL**.
- Full `:app:testDebugUnitTest --tests "com.warped.ui.chat.*"` (224 tests): 223 pass; 1 failure in `ModelSwitchUnloadTest.failed mount surfaces error and keeps draft` — proven pre-existing (see Deviations).
- Zero new Gradle dependencies (android.media + coroutines only, both already in graph); RECORD_AUDIO already declared.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Test bug] Fake recorder never creates the output file**
- **Found during:** Task 1 verification
- **Issue:** `cancel deletes the file` and `stop failure` tests looked for the platform-written file, but the fake handle records the path without creating anything — `listFiles()` returned null.
- **Fix:** Tests now create the file at `factory.handle.outputPath` after start to simulate platform output.
- **Files modified:** VoiceMessageRecorderTest.kt (no production change)

**2. [Rule 1 - Test bug] Short overflow in resample ramp test**
- **Found during:** Task 2 verification
- **Issue:** `ShortArray(44_100) { it.toShort() }` overflows past 32767, so the endpoint assertion compared against a wrapped value.
- **Fix:** Halved ramp `(it / 2).toShort()` (max 22050, no overflow), endpoint expectation 22 050.
- **Files modified:** PcmTranscoderTest.kt (no production change)

### Pre-existing Failure (out of scope, not fixed)

**[Flake] `ModelSwitchUnloadTest.failed mount surfaces error and keeps draft` fails in full-package runs**
- **Evidence:** Fails 3/3 full `com.warped.ui.chat.*` runs WITH Plan 01 changes AND 1/2 full runs on stashed base sources (commit fd2bfc0f, voice files present, VM/UI edits stashed) — same test, same assertion (line 321). Passes in isolation in both states. The test polls a `Dispatchers.Default` hop with virtual-time delays (self-documented race: "The throw hops off Dispatchers.Default — yield, then assert").
- **Action:** Logged to `deferred-items.md`; NOT fixed per scope boundary (pre-existing, unrelated file, needs no Plan 01 behavior change).

## Known Stubs

None — the tracer is fully wired. Hardcoded tracer copy (2 Snackbar strings, 1 content description) is intentional and tracked for Plan 02 Task 3 resourcing, not a stub.

## Threat Flags

None — no new surface beyond the plan's threat model (T-67-01..03, T-67-SC all mitigated as specified: app-private filesDir, timestamp filenames, 60 s cap + job cleanup + onCleared destroy, TranscodeException→Snackbar, zero new deps).

## Self-Check: PASSED

- VoiceMessageRecorder.kt, PcmTranscoder.kt, both test files: FOUND on disk.
- Commits 202850a7, fd2bfc0f, b5192ac9: FOUND in `git log`.
