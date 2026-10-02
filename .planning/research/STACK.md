# Technology Stack — v3.1 Voice Messages + New Tool (Delta)

**Project:** Warped — milestone v3.1 (subsequent milestone, NOT greenfield)
**Researched:** 2026-10-02
**Scope:** ONLY what is new for (1) 60s-capped in-app voice messages sent as audio input to audio-capable LiteRT-LM models, and (2) one new function-calling-style tool. Everything already validated (Kotlin, Compose, Hilt, Room, DataStore, WorkManager, OkHttp/Retrofit, Kotlinx Serialization, LiteRT-LM 0.17.1, SpeechRecognizer dictation, Play In-App Review) is **reused unchanged and not re-researched**.

## Headline: zero new Gradle dependencies

The entire milestone is implementable with **platform APIs (`android.media.*`) + the already-pinned `litertlm-android 0.17.1`**. The recommended `app/build.gradle.kts` diff is empty. This is a deliberate constraint, not a gap: every capability below maps to a platform class already present at `minSdk 28 / compileSdk 36`.

## Already in Place — Reuse, Do Not Rebuild

| Existing asset | Location | What v3.1 reuses |
|---|---|---|
| `ChatRequest.audioBytes: ByteArray?` | `domain/model/ChatRequest.kt` | Voice message bytes ride the existing field — no DTO change |
| `sendMessage(text, images, audioBytes)` | `ui/chat/ChatViewModel.kt:462` | Entry point already accepts audio; recorder hands it bytes |
| `Content.AudioBytes(audioBytes)` attach | `data/local/inference/LiteRTLmProvider.kt:281-301` | Inference-side wiring already sends audio + text; recorder only needs to supply correctly-formatted PCM |
| Audio backend resolution (`probeAudioBackend()` → CPU, allowlist-gated) | `data/local/inference/EngineManager.kt:198-210`, `BackendDetector.kt:43-44` | Engine init already configures the audio slot for `capabilities.audio == true` models (gemma-4-E2B/E4B, gemma-3n-E2B/E4B per `model_allowlist.json`) |
| `RECORD_AUDIO` permission + `microphone required=false` | `AndroidManifest.xml:9-15` | Declared in v3.0; recorder reuses the same grant + first-tap rationale pattern from `VoiceDictationManager` (Phase 65) |
| `@Tool` / `ToolSet` schema + `LocalToolLoop` + remote `tools[]` mapping + capability matrix | `data/agentic/WebFetchToolSet.kt`, `WebSearchToolSet.kt`, `LocalToolLoop.kt`, `ToolCapabilityMatrix.kt` | New tool plugs into this exact pattern — no new execution machinery |
| `litertlm-android 0.17.1` | `gradle/libs.versions.toml:4` | **Do NOT bump.** Audio types (`AudioBytes`) and tool annotations ship in this pin |

## New Capability 1: Voice Recording — `android.media.MediaRecorder` (platform, no dep)

| Choice | Value | Why |
|---|---|---|
| Recorder | `android.media.MediaRecorder` (platform) | Simplest lifecycle, framework handles encoder/HAL quirks across devices. Battle-tested for exactly this use case (voice notes). Zero dependencies |
| Audio source | `MIC` (`MediaRecorder.AudioSource.MIC`) | Voice-message standard; `VOICE_RECOGNITION` adds DSP tuning aimed at STT, not natural playback — MIC preserves full quality |
| Output format | `MPEG_4` | Universally playable container on Android; `MediaPlayer`-ready without extra codecs |
| Audio encoder | `AAC` (`AudioEncoder.AAC`), 64 kbps, mono | ~500 KB per 60 s message; AAC-in-M4A plays everywhere including the app's own `MediaPlayer` and external players |
| Output file | `context.filesDir/voice-messages/<uuid>.m4a` | App-private, auto-cleaned on uninstall; see Storage below |
| 60 s cap | `setMaxDuration(60_000)` + `setOnInfoListener` → `MEDIA_RECORDER_INFO_MAX_DURATION_REACHED` auto-stops | Framework-enforced cap survives process stalls better than a hand-rolled timer; ViewModel timer mirrors it for the countdown UI only |
| Amplitude/waveform | `getMaxAmplitude()` polled on a 100 ms coroutine ticker, normalized to 0–1 | Zero-dep waveform bars during recording; no audio-visualizer permission or library needed |
| Runtime permission | Reuse v3.0 `RECORD_AUDIO` grant + first-tap rationale (`VoiceDictationManager` precedent) | Permission already declared; if the user granted for dictation, recording just works — no second prompt. Denied state reuses the Settings-escape path |

**Why NOT `AudioRecord` (raw PCM capture):** manual buffer threading, device-specific sample-rate quirks (must negotiate HAL rates yourself), and you still have to write a WAV header for playback. `MediaRecorder` gives a playable file for free; the one extra step (decode to PCM, next section) is ~120 lines of platform code with no threading hazards.

**Why NOT FFmpeg / Oboe / TarsosDSP:** native/JNI weight for a solved platform problem; FFmpeg alone adds multi-MB `.so` + 16 KB-alignment audit surface (v2.5 fought exactly this class of battle). Rejected outright.

## New Capability 2: Model-Input Conversion — `MediaExtractor` + `MediaCodec` (platform, no dep)

This is the single most important stack decision in the milestone. The model does **not** accept the recorded `.m4a`. Evidence:

- **Gemma official audio docs (HIGH):** model input must be **mono, 16 kHz, 32-bit float, normalized [-1, 1]**; **maximum clip 30 s**; token cost 25 tok/s (Gemma 4) / 6.25 tok/s (3n). Source: `ai.google.dev/gemma/docs/capabilities/audio`.
- **Independent on-device implementation (MEDIUM):** raw PCM bytes via `Content.AudioBytes` works with Gemma 4 E2B; the file-path form (`Content.AudioFile` / WAV path) **fails at prefill** (`Failed to allocate tensors (RunPrefillAsync)`). A second independent guide concurs: "`AudioBytes` must be raw PCM bytes (not MP3/AAC), 16 kHz mono 16-bit."
- **In-repo confirmation (HIGH):** `LiteRTLmProvider` already passes `request.audioBytes` straight into `Content.AudioBytes` — so whatever the recorder produces must be pre-converted to raw PCM before it reaches `sendMessage`.

| Choice | Value | Why |
|---|---|---|
| Decoder | `android.media.MediaExtractor` + `MediaCodec` (platform) | Decodes the recorded AAC/M4A to PCM on every API 28+ device; no deps, no JNI, no licensing questions |
| Post-decode DSP | Hand-written Kotlin: stereo→mono average, resample to 16 kHz (linear interpolation is sufficient for voice; Fourier ideal but overkill on-device), int16→float32 ÷ 32768 | ~60 lines, pure Kotlin, unit-testable on JVM with synthetic PCM fixtures — no native audio library |
| Bytes fed to model | Raw PCM `ByteArray` (strip any header; **never** pass the `.m4a`/WAV file bytes) into existing `ChatRequest.audioBytes` | Matches the `AudioBytes`-expects-raw-PCM evidence above |
| Clip policy | **Send first 30 s; keep full 60 s for playback.** Trim before `sendMessage`, user-visible note ("first 30 s sent to model") | Model hard limit is 30 s; a 60 s clip risks prefill failure. Token math: 30 s × 25 tok/s = 750 audio tokens (Gemma 4) — fits the 4096 input+output budget precedent; 60 s (1500 tokens) does not reliably. 30 s PCM16 mono = 960 KB in memory — safe |

**Phase-ordering implication:** the decode+trim step is a pure-Kotlin module with JVM tests — build and verify it before any UI work. Device-verify the 30 s trim against a real E2B load (prefill success/failure is only observable on hardware).

## New Capability 3: Playback — `android.media.MediaPlayer` (platform, no dep)

| Choice | Value | Why |
|---|---|---|
| Player | `android.media.MediaPlayer` (platform), one instance owned by the chat UI layer, `release()` on dispose | Plays local `.m4a` with seek + completion callback; single-file local playback is exactly what `MediaPlayer` is good at. Instance-per-bubble is a leak — one shared player, tracked current-playing id |
| Seek UI | `Slider` bound to `getCurrentPosition()/getDuration()` via the same 100 ms ticker family as recording amplitude | No new API; progress polling pattern already exists in the codebase idiom |

**Why NOT Media3/ExoPlayer (`androidx.media3`):** adds ~1–2 MB + a new dependency family for features (adaptive streaming, playlists, background audio) this milestone does not need. Voice-message playback is one local file at a time. Explicitly rejected — if background/lock-screen playback ever enters scope, revisit then.

## Storage & Lifecycle (no new library)

| Choice | Value | Why |
|---|---|---|
| Directory | `filesDir/voice-messages/` | Private, included in auto-backup exclusion posture (`allowBackup=false` already); never `cacheDir` (system may evict mid-chat) |
| Retention | Delete-on-send-cancel; cap directory at ~50 MB / 7 days via a WorkManager periodic sweep reusing the existing worker setup | 60 s AAC ≈ 500 KB — unbounded accumulation is the only real storage risk; a tiny sweep worker bounds it |
| History rendering | Voice messages render as player bubbles referencing the stored file; the raw PCM bytes are **never** persisted to Room (re-decodable from the `.m4a` on demand) | Keeps the DB lean; PCM for 30 s is ~1 MB per message — storing it would bloat chat history |

## New Tool: Zero-Dep `ToolSet` (no new library)

The "one new tool" is a **stack non-event by design**: implement it as a third `ToolSet` class following `WebFetchToolSet` / `WebSearchToolSet` exactly (`@Tool` + `@ToolParam String`-only params, snake-case name, `HOST_EXECUTED` body, manual execution in `LocalToolLoop` on `Dispatchers.IO`, 1:1 remote `tools[]` mapping via `ToolCapabilityMatrix`). No new Maven artifact, no SDK, no service client.

Constraints the stack imposes on tool candidacy (roadmap input, not the pick itself):

- **Pure Kotlin + platform/`java.time`/`java.util` only.** Anything needing an API key, network client beyond existing OkHttp, or a binary dep is disqualified by the zero-dep budget.
- **Banned by v2.2 precedent:** calculator, wall-clock/datetime, JSON formatter — removed one milestone after introduction ("user found no value"). Do not re-propose them.
- **Fertile zero-dep classes:** Room-backed note/memory save-recall (reuses existing DB), unit conversion tables, text statistics/readability, regex extract/transform, countdown/timer math, on-device language-detect routing (Optimaize detector already a dep). All fit the `@Tool String`-param contract.

## What NOT to Add

| Technology | Why NOT | Instead |
|---|---|---|
| `androidx.media3` / ExoPlayer | 1–2 MB for unneeded streaming features | Platform `MediaPlayer` |
| FFmpeg / any JNI audio lib | Native weight + 16 KB-alignment audit surface for a solved platform problem | `MediaExtractor` + `MediaCodec` |
| `AudioRecord` raw-capture path | Manual threading + HAL rate quirks; still needs a WAV writer for playback | `MediaRecorder` AAC + decode step |
| New STT/TTS SDK (e.g. ML Kit, Whisper) | Voice messages are audio **input to the model**, not transcription — dictation already covers STT via platform `SpeechRecognizer` | Nothing; model does the understanding |
| `Content.AudioFile` (path-based audio) | Community evidence: WAV-path prefill fails on Gemma 4 E2B | `Content.AudioBytes` with raw PCM (already wired) |
| `litertlm-android` version bump | 0.17.1 already ships `AudioBytes`, `audioBackend`, `@Tool`; bump = re-verification tax with zero feature gain | Stay on 0.17.1 |
| Oboe / AAudio | Low-latency pro-audio path; voice notes don't need <20 ms latency | `MediaRecorder` |
| Storing PCM in Room | ~1 MB/message DB bloat | Store `.m4a`, decode on demand |

## Integration Points (for the roadmap)

1. `VoiceRecorder` (new, `ui/chat/voice/` alongside `VoiceDictationManager`) → emits finished `.m4a` file + duration → `AudioToPcm` (new, pure-Kotlin `data/audio/`) → trims to 30 s → existing `sendMessage(text, images, audioBytes)` → existing `LiteRTLmProvider` attach. No interface changes anywhere upstream.
2. Model gating reads the existing allowlist flag: voice-send icon visible only when active model has `capabilities.audio == true` (gemma-4-E2B/E4B, gemma-3n-E2B/E4B); dictation mic keeps its existing availability gate. Icon differentiation is a UI concern, not a stack one.
3. New `XxxToolSet : ToolSet` + executor branch in `LocalToolLoop` + one row in `ToolCapabilityMatrix` + remote mapping — the exact file list Phase 47 established.

## Sources

- Gemma audio input spec (sample rate, bit depth, 30 s clip, token cost) — `https://ai.google.dev/gemma/docs/capabilities/audio` (official, HIGH)
- `Content.AudioBytes` raw-PCM pattern + `audioBackend = Backend.CPU()` engine config — community Gemma 4 on-device guide, gist `ashokvarmamatta/2305e6d9a2c7b5ac7da8fc5c1c95e489` (LOW-MEDIUM, corroborated)
- Raw-PCM-not-WAV-path + `maxTokens 4096` audio-prefill budget — `github.com/yu23ki14/envsense` mobile CLAUDE.md (independent implementation, MEDIUM)
- In-repo wiring (`ChatRequest.audioBytes`, `LiteRTLmProvider:281-301`, `EngineManager:198-210`, `sendMessage:462`, `model_allowlist.json` audio flags) — verified by direct read (HIGH)
- `MediaRecorder.setMaxDuration` + `OnInfoListener` cap, `MediaPlayer` local playback — platform APIs, `developer.android.com` reference (HIGH)
