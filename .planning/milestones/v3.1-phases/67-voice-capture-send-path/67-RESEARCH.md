# Phase 67: Voice Capture + Send Path - Research

**Researched:** 2026-10-02
**Domain:** Android on-device voice recording (platform MediaRecorder) + AAC→PCM transcode (MediaExtractor/MediaCodec) + existing LiteRT-LM audio send path
**Confidence:** HIGH

## Summary

Phase 67 adds a voice-message record → send path to the chat input with zero new Gradle dependencies. Recording uses platform `MediaRecorder` (AAC/M4a into `filesDir/voice/`), following the `VoiceDictationManager` VM-owned wrapper pattern with `destroy()` in `onCleared`. The send path already exists end-to-end: `ChatViewModel.sendMessage(text, images, audioBytes)` → `ChatRequest.audioBytes` → `LiteRTLmProvider` wraps it in `Content.AudioBytes` (verified: serializes as `{"type":"audio","blob":base64}` — raw bytes, no sample-rate metadata in the envelope). The missing middle is a platform-only AAC→mono-16kHz-PCM transcoder (MediaExtractor + MediaCodec decode + downmix/resample, first-30 s truncation) plus the chat-input recording UI. The highest-risk item is the transcoder's MediaCodec decode loop; it is mitigated by splitting pure-Kotlin PCM math (JVM-testable) from the thin codec wrapper (device-smoke verified).

**Primary recommendation:** Build `VoiceMessageRecorder` (MediaRecorder wrapper, injectable factory for tests) + `PcmTranscoder` (MediaExtractor/MediaCodec decode, pure-function resample/truncate) in `ui/chat/voice/`, wire record state + send through `ChatViewModel` into the existing `audioBytes` path, and render the recording row in `ChatInputBar` per the UI-SPEC.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Dedicated voice-send button (waveform/audio-clip icon) next to the STT dictation mic, tap-to-start / tap-to-stop (not hold-to-record)
- Inline mm:ss timer + amplitude bar in the input row, turns red in the last 10 s
- 60 s auto-stop-and-keep the clip + toast "60s limit reached" (never auto-send, never discard on cap)
- Explicit cancel (X) discards the file + deletes bytes immediately
- Send transmits voice clip + optional caption text as one user bubble via existing `ChatViewModel.sendMessage(text, images, audioBytes)` path
- Keep full 60 s file (m4a/AAC via platform MediaRecorder) for playback; transcode first 30 s → mono 16 kHz PCM for the `audioBytes` path
- User-visible note "First 30s sent to model" whenever the clip exceeds 30 s
- Minimal text-only-model guard in this phase (send disabled + toast)
- Audio bytes in `filesDir/voice/*.m4a`, Room holds path + duration only (never BLOB)
- Recorder VM-owned in `ui/chat/voice/` (new `VoiceMessageRecorder`, sibling to `VoiceDictationManager`), survives rotation via ViewModel; backgrounding auto-stops and keeps the clip (no mic-type FGS)
- RECORD_AUDIO runtime request with first-tap rationale (Phase 65 pattern); denied → graceful Snackbar, recording never starts, no crash
- Failed send keeps the file for retry; cancel/delete removes the file immediately
- Sub-1 s clips kept in this phase (<1 s rejection guard belongs to Phase 68)
- Local-only send path; remote endpoints blocked minimally
- Amplitude bar + progress only — waveform artwork deferred (VF-02)

### the agent's Discretion
- Exact waveform-vs-mic glyph choice, timer red-threshold styling, and toast vs Snackbar copy within existing error-channel conventions

### Deferred Ideas (OUT OF SCOPE)
- Draft preview (play/pause + send + delete) and <1 s rejection — Phase 68 (VMSG-02)
- History playback bubbles with duration + progress across restarts — Phase 68 (VMSG-06)
- Full icon-differentiation polish, text-only + remote gating UX, parallel-STT transcript captions — Phase 69
- Waveform artwork in bubbles — future VF-02
- Foreground-service background recording — out of scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| VMSG-01 | User records a voice message from the chat input with live timer + amplitude feedback, a 60-second hard cap with auto-stop-and-keep, and cancel that discards the file | MediaRecorder wrapper pattern (sibling to VoiceDictationManager); ChatInputBar recording-row slot; Phase 65 permission/Snackbar channel |
| VMSG-05 | User sends the voice message to an audio-capable local model (full 60 s kept for playback, first 30 s transcoded to mono 16 kHz PCM via the existing audioBytes path, with a user-visible note) | Existing sendMessage→ChatRequest.audioBytes→Content.AudioBytes path verified in code; PcmTranscoder design below; error_no_audio gate already exists at ChatViewModel.kt:876 |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Audio capture (MediaRecorder lifecycle) | Android platform (ViewModel-owned wrapper) | — | Platform hardware API; must be lifecycle-bound, never in composables |
| AAC→PCM transcode | Android platform (IO dispatcher) | — | CPU-bound codec work; never on Main |
| Recording UI state (timer, amplitude, cap) | UI layer (ViewModel StateFlow → Compose) | — | Same StateFlow→Compose pattern as dictation listening flag |
| Send + capability gate | UI layer (existing sendMessage path) | — | Reuses proven path; no new data-layer code |
| Audio file storage | App-private filesDir | — | Large blobs; Room holds path+duration only |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| platform `MediaRecorder` | API 28+ (minSdk) | AAC/M4a capture, `maxAmplitude` polling | Zero-dep constraint; only sanctioned capture API [ASSUMED for API specifics, HIGH for constraint] |
| platform `MediaExtractor` + `MediaCodec` | API 28+ | AAC decode to PCM | Only zero-dep decode route from m4a [ASSUMED] |
| `Content.AudioBytes(byte[])` (litertlm-android) | 0.17.1 (pinned, catalog) | Audio turn payload | Existing proven path [VERIFIED: gradle/libs.versions.toml:4 + app/build.gradle.kts:254 + LiteRTLmProvider.kt:289] |
| `VoiceDictationManager` pattern | Phase 65 | Lifecycle/ownership template for recorder | VM-owned, destroy in onCleared [VERIFIED: ui/chat/voice/VoiceDictationManager.kt:146-159] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Coroutines (`Dispatchers.IO`) | 1.9.x (catalog) | File/codec work off Main | Transcode, file delete, duration probe |
| `kotlinx-coroutines-test` + MockK + Truth | existing test stack | Recorder/transcoder/VM tests | Same harness as VoiceDictationTest |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| MediaRecorder AAC capture | AudioRecord raw PCM capture | Rejected: loses compressed playback file; CONTEXT locks m4a-for-playback + transcode-for-model |
| MediaCodec decode | FFmpeg / Media3 Transformer | Rejected: new native dep / new Gradle dep — both violate the zero-dep hard constraint |
| `Content.AudioFile(path)` | `Content.AudioBytes(bytes)` | AudioFile exists in SDK [VERIFIED: classes.jar] but existing proven path is AudioBytes via ChatRequest; stay on it |
| Room BLOB for audio | filesDir + path in Room | Rejected: blobs bloat DB, break backup/migration; research contract forbids |

**Installation:** none — zero new dependencies (hard constraint).

## Architecture Patterns

### System Architecture Diagram

```
chat pill [voice btn] --tap--> ChatViewModel.startVoiceRecording()
      |  (permission gate: Phase 65 rationale -> system request -> Snackbar paths)
      v
VoiceMessageRecorder (ui/chat/voice/, VM-owned)
  |-- MediaRecorder -> filesDir/voice/<ts>.m4a (AAC)
  |-- maxAmplitude poll ~100ms -> amplitude StateFlow
  |-- 1s ticker -> elapsed StateFlow (red >= 50s)
  |-- 60s auto-stop-and-keep (callback, never auto-send)
      |-- tap stop --> file kept in memory for send
      |-- tap cancel (X) --> file deleted immediately
      |-- backgrounding --> auto-stop-and-keep
      v
send tap --> PcmTranscoder (Dispatchers.IO)
  |-- MediaExtractor+MediaCodec decode AAC -> PCM
  |-- downmix to mono + resample to 16 kHz (pure fns)
  |-- truncate to first 30 s; flag truncated
  v
ChatViewModel.sendMessage(caption, audioBytes=pcm30s)
  |-- existing capabilities.audio gate (error_no_audio) [VERIFIED: ChatViewModel.kt:876-885]
  |-- LiteRTLmProvider: Content.AudioBytes(pcm) [VERIFIED: LiteRTLmProvider.kt:289]
  |-- >30s --> Snackbar "First 30s sent to model"
```

### Recommended Project Structure

```
ui/chat/voice/
├── VoiceDictationManager.kt   # existing (Phase 65) — untouched
├── VoiceMessageRecorder.kt    # NEW: MediaRecorder wrapper (factory-injected)
└── PcmTranscoder.kt           # NEW: decode + mono/16kHz + 30s truncate
ui/chat/
├── ChatViewModel.kt           # EDIT: recording state, send wiring, mutual exclusion
├── ChatScreen.kt              # EDIT: permission launcher reuse, Snackbar/Toast, lifecycle observer
└── components/ChatInputBar.kt # EDIT: voice button + recording row (dead audio params come alive)
app/src/main/res/values[-es]/strings.xml  # EDIT: 4 new user-visible strings EN+ES
```

### Pattern 1: VM-owned platform wrapper with injected factory
**What:** Mirror `VoiceDictationManager`: thin class owning the platform object, created lazily by the ViewModel, `destroy()` called from `onCleared`. For testability, inject a `RecorderFactory` interface (production creates real `MediaRecorder`; tests supply a fake) — `MediaRecorder` methods are not mockable on JVM without Robolectric.
**When to use:** `VoiceMessageRecorder` construction.
**Example:** see VoiceDictationManager.kt:30-37 (constructor), :146-159 (destroy contract).

### Pattern 2: Existing audio send gate reuse
**What:** `sendMessage` already rejects `audioBytes` on non-audio models with `error_no_audio` [VERIFIED: ChatViewModel.kt:876-885]. Phase 67 adds only a *pre-send* UI guard (send disabled + toast) on top; the in-path gate stays as the backstop.
**When to use:** Minimal text-only guard task.

### Pattern 3: Phase 65 permission flow reuse
**What:** First-tap rationale dialog → `micPermissionLauncher` system request → permanent-denial `SnackbarWithAction` (Settings escape) via `ChatEvent.SnackbarWithAction`; transient denial stays silent [VERIFIED: ChatScreen.kt:279-318, ChatViewModel.kt:1709-1717]. Voice-send reuses the same launcher and event channel with voice-message copy.
**When to use:** Voice-send permission task.

### Anti-Patterns to Avoid
- **Recording state in composables:** `remember` dies on rotation; recorder + elapsed/amplitude state lives in the ViewModel. (CONTEXT locks VM ownership.)
- **Blocking Main on transcode:** MediaCodec decode of up to 60 s AAC must run on `Dispatchers.IO`; the send tap launches a coroutine and disables send until PCM is ready.
- **Deleting the kept clip on send failure:** Failed send keeps the file for retry (Phase 68 draft basis); only explicit cancel deletes.
- **New event bus / new Snackbar host:** Reuse `ChatEvent.Snackbar/SnackbarWithAction` + existing `SnackbarHost` [VERIFIED: ChatScreen.kt:211-241,382].

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| AAC encode/decode | Custom codec, JNI FFmpeg | MediaRecorder / MediaExtractor+MediaCodec | Hardware-backed, zero-dep; custom codecs are a security + ABI minefield |
| Resample DSP from scratch | Naive sample-drop | Linear-interpolation resample (short, pure fn, tested) | Aliasing artifacts confuse the model; linear interp is ~20 lines and testable |
| Permission flow | Custom rationale system | Phase 65 launcher + SnackbarWithAction channel | Proven copy/escalation semantics, TalkBack already handled |
| Audio capability detection | New probe | `ModelAllowlistRepository.capabilities.audio` + existing sendMessage gate | Allowlist-verified, never raw capability defaults [VERIFIED: ChatViewModel.kt:859-864] |

## Common Pitfalls

### Pitfall 1: MediaRecorder.stop() throws on too-short / failed recordings
**What goes wrong:** `stop()` throws `RuntimeException` if no valid audio data was captured (immediate stop, interstate calls), crashing the app.
**Why it happens:** Platform behavior — stop is only valid in the recording state with encoded output.
**How to avoid:** Wrap `stop()`/`reset()`/`release()` in try/catch, always `release()` in finally; treat stop-failure as discard-with-log.
**Warning signs:** Crash reports pointing at `MediaRecorder.stop`.

### Pitfall 2: MediaCodec EOS / output-format-change mishandling
**What goes wrong:** Decode loop hangs or drops all output when `INFO_OUTPUT_FORMAT_CHANGED` or EOS flags are mishandled.
**Why it happens:** The format-change event arrives once before real output; EOS must be queued on input AND observed on output.
**How to avoid:** Canonical loop: dequeue input → queue with EOS at extractor exhaustion → on OUTPUT_FORMAT_CHANGED capture PCM format (sample rate/channels) → drain until OUTPUT_FLAG_END_OF_STREAM; hard timeout guard. [ASSUMED — standard platform pattern, executor follows Android docs.]
**Warning signs:** Transcode returns 0 bytes or hangs past a watchdog.

### Pitfall 3: Source sample rate / channel assumptions
**What goes wrong:** AAC from MediaRecorder is typically 44.1/48 kHz stereo, but device-specific; hardcoding input format corrupts resample math.
**Why it happens:** Encoder output varies by OEM.
**How to avoid:** Read actual sample rate + channel count from the decoded output format (post `INFO_OUTPUT_FORMAT_CHANGED`), never assume; pure `resampleTo16kMono(pcm, srcRate, srcChannels)` takes them as params.
**Warning signs:** Chipmunk/slowed model "hearing" or garbage transcriptions.

### Pitfall 4: Amplitude polling leaks coroutines
**What goes wrong:** The ~100 ms `maxAmplitude` sampler keeps running after stop/cancel, leaking a job and holding the mic indicator.
**Why it happens:** Sampler job not tied to the recording session lifecycle.
**How to avoid:** Sampler is a child `Job` of the recording session; cancelled in `stop()`/`cancel()`/`destroy()`/auto-stop paths — every exit path.
**Warning signs:** Battery drain, mic icon stuck on.

### Pitfall 5: Double-tap during recorder spin-up
**What goes wrong:** Second tap while `prepare()/start()` is in flight creates a second session or crashes.
**Why it happens:** `MediaRecorder` is stateful and not re-entrant.
**How to avoid:** Single-flight guard in the ViewModel (ignore taps while `starting` flag set) — matches UI-SPEC unresolved-item assumption.
**Warning signs:** Overlapping files, IllegalStateException from prepare.

## Code Examples

### MediaRecorder AAC capture shape [ASSUMED — standard platform API]
```kotlin
// Source: Android developer docs (MediaRecorder overview)
recorder = factory.create().apply {
    setAudioSource(MediaRecorder.AudioSource.MIC)
    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
    setAudioEncodingBitRate(128_000)
    setAudioSamplingRate(44_100)
    setOutputFile(file.absolutePath)
    prepare()
    start()
}
val amplitude: Int = recorder.maxAmplitude // 0..32767, poll ~100ms
```

### MediaCodec decode-loop shape [ASSUMED — standard platform API]
```kotlin
// Source: Android developer docs (MediaCodec, synchronous processing)
extractor.setDataSource(path)
extractor.selectTrack(audioTrackIndex)
val codec = MediaCodec.createDecoderByType(mime)
codec.configure(format, null, null, 0); codec.start()
var inputEos = false
while (!outputEos) {
    if (!inputEos) {
        val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
        if (inIdx >= 0) {
            val sampleSize = extractor.readSampleData(buf)
            if (sampleSize < 0) { queue EOS flag; inputEos = true }
            else { queue input; extractor.advance() }
        }
    }
    val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
    when {
        outIdx >= 0 -> { copy info.size bytes; capture format on first frame; release; if EOS flag -> outputEos = true }
        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> actualFormat = codec.outputFormat
    }
}
```

### Existing send-path call site [VERIFIED: ChatScreen.kt:404-433, ChatViewModel.kt:462]
```kotlin
viewModel.sendMessage(input.inputText, attachedImages, audioBytes) // audioBytes: ByteArray? = null
// Gate inside sendMessage:
if (audioBytes != null && !capabilities.audio) { /* error_no_audio, return */ } // ChatViewModel.kt:876
// Provider:
contentList.add(Content.AudioBytes(audioBytes)) // LiteRTLmProvider.kt:289
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `ChatInputBar` audio params unused | Wire `modelHasAudio`/`onAudioRecorded`/`onAudioRecordingChanged` to real flow | This phase | Dead params come alive — no signature churn needed |
| Dictation-only mic in pill | Voice-send button beside dictation mic | This phase | Two input modes, mutually exclusive |

**Deprecated/outdated:** none.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | MediaRecorder/MediaCodec exact method set and behavior notes | Code Examples, Pitfalls 1-2 | LOW — stable platform APIs since API 16/21; executor verifies against android.jar at compile time |
| A2 | Model expects 16-bit mono 16 kHz PCM byte order (little-endian) | Summary, Transcoder | MEDIUM — envelope carries no format metadata [VERIFIED]; prior v2.5 audio-slot work established the convention per CONTEXT; device smoke (success criterion 3) is the backstop |
| A3 | `Icons.Filled.GraphicEq`-family glyph exists in the app's Compose icons artifact | Plans (UI task) | LOW — executor confirms import at compile time; fallback is any audio-clip vector, never a mic glyph |
| A4 | MediaRecorder AAC output is 44.1/48 kHz stereo on target devices | Pitfall 3 | LOW — mitigated by reading actual format from codec output; never hardcoded |

## Open Questions (RESOLVED)

1. **Where do audio bytes live?** — RESOLVED: `filesDir/voice/*.m4a`, Room holds path + duration only (CONTEXT locked).
2. **What PCM format does the model path expect?** — RESOLVED: mono 16 kHz PCM bytes via existing `audioBytes` → `Content.AudioBytes` (CONTEXT locked; envelope verified format-agnostic).
3. **New dependencies?** — RESOLVED: none permitted (research hard constraint; RECORD_AUDIO already declared [VERIFIED: AndroidManifest.xml:9]).
4. **What about playback?** — RESOLVED: out of scope (Phase 68 owns draft preview + history playback); Phase 67 only keeps the file.

## Environment Availability

Step 2.6: SKIPPED — no external dependencies; pure platform APIs + existing pinned SDK (litertlm-android 0.17.1 in Gradle cache [VERIFIED]).

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (jupiter) + MockK + Truth + kotlinx-coroutines-test |
| Config file | app/build.gradle.kts (existing) |
| Quick run command | `./gradlew :app:testDebugUnitTest --tests "com.warped.ui.chat.voice.*"` |
| Full suite command | `./gradlew :app:testDebugUnitTest` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| VMSG-01 | 60 s auto-stop keeps clip; cancel deletes file; timer/amplitude state | unit (fake recorder factory + TestDispatcher) | `./gradlew :app:testDebugUnitTest --tests "com.warped.ui.chat.voice.*"` | ❌ Wave 0 — new `VoiceMessageRecorderTest` + `PcmTranscoderTest` |
| VMSG-05 | first-30 s truncate + mono/16k resample math; audio gate backstop | unit (pure fns + existing sendMessage gate) | same | ❌ Wave 0 — same new test files |
| VMSG-01/05 E2E | record → send → model response on-device | manual device smoke | device with audio-capable allowlist model | manual-only (needs mic + model; emulator smoke if available) |

### Sampling Rate
- **Per task commit:** targeted `--tests` command above
- **Per wave merge:** full `:app:testDebugUnitTest`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `app/src/test/java/com/warped/ui/chat/voice/VoiceMessageRecorderTest.kt` — recorder state machine (fake factory), auto-stop-keep, cancel-delete, sampler cleanup
- [ ] `app/src/test/java/com/warped/ui/chat/voice/PcmTranscoderTest.kt` — resample/downmix/truncate pure functions with synthetic PCM
- [ ] Existing `VoiceDictationTest.kt` harness as the copy pattern [VERIFIED: app/src/test/java/com/warped/ui/chat/VoiceDictationTest.kt:80-159]

*(MediaCodec decode loop itself is not JVM-testable — covered by device smoke + watchdog/timeout acceptance criteria, stated honestly in plans.)*

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | — |
| V4 Access Control | partial | App-private filesDir (MODE_PRIVATE), no FileProvider export |
| V5 Input Validation | yes | Recording duration/size caps; corrupt-aac decode failure → user message, no crash |
| V6 Cryptography | no | No new crypto; no secrets involved |
| V8 Data Protection | yes | Voice clips are sensitive PII: app-private storage, immediate delete on cancel, no logging of bytes |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Voice clip exfiltration via world-readable file | Information Disclosure | filesDir is app-private by default; never set readable/writable flags; never share via intent in this phase |
| Mic left recording (runaway session) | Information Disclosure | 60 s hard cap + background auto-stop + sampler-job cleanup on every exit path |
| Path traversal via clip filename | Tampering | Filenames are timestamp-generated by the app, never user input |

## Sources

### Primary (HIGH confidence)
- In-repo code reads this session: ChatViewModel.kt (sendMessage:462, gate:876-885, emitMicDenied:1709-1717), ChatScreen.kt (audioBytes state:122, permission flow:279-318, Snackbar channel:211-241), ChatInputBar.kt (dead audio params:54-56, mic pattern:224-248), LiteRTLmProvider.kt:289, VoiceDictationManager.kt (lifecycle:146-159), ModelAllowlistRepository.kt (audio cap:53,164,187), ChatRequest.kt:7, AndroidManifest.xml:9, strings.xml voice keys, VoiceDictationTest.kt harness, 66-01-PLAN.md format precedent
- litertlm-android-0.17.1.aar (Gradle cache): `Content$AudioBytes(byte[])` signature via javap; envelope `{"type":"audio","blob":base64}` via class strings

### Secondary (MEDIUM confidence)
- None (no web fetch needed — platform APIs + in-repo evidence sufficed)

### Tertiary (LOW confidence)
- MediaRecorder/MediaCodec method-level details [ASSUMED] — stable platform APIs, compile-time verified by executor

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - all paths verified in-repo or cache-extracted
- Architecture: HIGH - mirrors proven Phase 65 patterns
- Pitfalls: HIGH - platform-known failure modes with concrete mitigations

**Research date:** 2026-10-02
**Valid until:** 2026-11-01 (stable domain: platform APIs + pinned SDK)
