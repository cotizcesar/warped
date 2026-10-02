# Phase 67: Voice Capture + Send Path - Context

**Gathered:** 2026-10-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Users can record a voice message from the chat input (live timer + amplitude, 60 s hard cap with auto-stop-and-keep, cancel discards) and send it to an audio-capable local LiteRT-LM model. Full 60 s clip kept for playback; first 30 s transcoded to mono 16 kHz PCM via the existing audioBytes path with a user-visible note. Record → send → model response completes on-device against an audio-capable allowlist model. Draft preview/playback-history (<1 s guard, history bubbles, rotation), icon differentiation, model/remote gating, and transcript captions belong to Phases 68–69 — not this phase.
</domain>

<decisions>
## Implementation Decisions

### Recording UX
- Dedicated voice-send button (waveform/audio-clip icon) next to the STT dictation mic, tap-to-start / tap-to-stop (not hold-to-record) — TalkBack-friendly, matches chat pill pattern
- Inline mm:ss timer + amplitude bar in the input row, turns red in the last 10 s — TurnStatusRow pattern, zero new deps
- 60 s auto-stop-and-keep the clip + toast "60s limit reached" — per success criteria (never auto-send, never discard on cap)
- Explicit cancel (X) discards the file + deletes bytes immediately — per VMSG-01

### Send Path + Transcoding
- Send transmits voice clip + optional caption text as one user bubble via existing `ChatViewModel.sendMessage(text, images, audioBytes)` path
- Keep full 60 s file (m4a/AAC via platform MediaRecorder) for playback; transcode first 30 s → mono 16 kHz PCM for the `audioBytes` path (`LiteRTLmProvider` → `Content.AudioBytes`)
- User-visible note "First 30s sent to model" whenever the clip exceeds 30 s (never silent truncation, never block >30 s sends)
- Minimal text-only-model guard in this phase (send disabled + toast) — full hidden/disabled gating UX lands in Phase 69

### Storage + Lifecycle + Permissions
- Audio bytes in `filesDir/voice/*.m4a`, Room holds path + duration only (never BLOB) — per research contract
- Recorder VM-owned in `ui/chat/voice/` (new `VoiceMessageRecorder`, sibling to `VoiceDictationManager`), survives rotation via ViewModel; backgrounding auto-stops and keeps the clip (no mic-type FGS — out of scope)
- RECORD_AUDIO runtime request with first-tap rationale (Phase 65 pattern); denied → graceful Snackbar, recording never starts, no crash
- Failed send keeps the file for retry (basis for the Phase 68 draft); cancel/delete removes the file immediately

### Errors + Scope Boundaries
- Sub-1 s clips kept in this phase — the <1 s rejection guard belongs to Phase 68 (VMSG-02)
- Local-only send path in this phase; remote endpoints blocked minimally (full local-only explanation lands in Phase 69, VMSG-08)
- Transcript caption out of scope — parallel on-device STT + duration-only fallback lands in Phase 69 (VMSG-07)
- Amplitude bar + progress only — waveform artwork explicitly deferred (VF-02)

### the agent's Discretion
- Exact waveform-vs-mic glyph choice, timer red-threshold styling, and toast vs Snackbar copy within existing error-channel conventions — follow ChatScreen/TurnStatusRow patterns and Material 3.
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ui/chat/voice/VoiceDictationManager.kt` — platform STT wrapper pattern (async, VM-owned, destroy in onCleared); mirror lifecycle for the new recorder, keep the two input modes mutually exclusive (starting one stops the other)
- `domain/model/ChatRequest.kt` (`audioBytes: ByteArray?`) → `data/local/inference/LiteRTLmProvider.kt` (`Content.AudioBytes`) — existing audio path, proven by audio/vision slot work on the v2.5 tree
- `ui/chat/ChatScreen.kt` (audioBytes state + `onAudioRecorded`) + `ui/chat/ChatViewModel.kt` (`sendMessage`, `capabilities.audio` guard at :876) — integration points for record → send
- `data/repository/ModelAllowlistRepository.kt` (`capabilities.audio`) + `data/local/inference/EngineManager.kt` (audio backend probe) + `BackendDetector.kt` — allowlist-gated audio model detection
- Phase 65 first-tap rationale + Settings-escape Snackbar pattern for the RECORD_AUDIO permission flow

### Established Patterns
- VM-owned platform wrappers with explicit destroy (no leaks past screen); coroutine dispatch (Main for UI, IO for file/audio work); StateFlow UiState to Compose
- `filesDir` app-private storage for large blobs; Room holds metadata/paths only
- Zero new Gradle deps (research hard constraint) — platform MediaRecorder/MediaPlayer only, no Media3/ExoPlayer/FFmpeg/AudioRecord-capture/Oboe

### Integration Points
- Chat input pill (new voice-send button beside dictation mic) → `VoiceMessageRecorder` → `filesDir/voice/` → 30 s PCM transcode → `ChatViewModel.sendMessage` → `LiteRTLmProvider` audio turn
- `ModelAllowlistRepository.capabilities.audio` for the minimal Phase 67 guard; full gating UI in Phase 69
</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches within the chat pill + TurnStatusRow conventions. Keep icons clearly pre-differentiated (waveform/audio-clip, not a second mic) so Phase 69 refinement is a restyle, not a re-architecture.
</specifics>

<deferred>
## Deferred Ideas

- Draft preview (play/pause + send + delete) and <1 s rejection — Phase 68 (VMSG-02)
- History playback bubbles with duration + progress across restarts — Phase 68 (VMSG-06)
- Full icon-differentiation polish, text-only + remote gating UX, parallel-STT transcript captions — Phase 69 (VMSG-03/04/07/08)
- Waveform artwork in bubbles — future VF-02, not v3.1
- Foreground-service background recording — out of scope (auto-stop on backgrounding instead)
</deferred>
