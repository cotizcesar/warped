# Phase 68: Voice Draft + Playback History - Context

**Gathered:** 2026-10-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Users can preview a recorded voice draft before sending (play/pause + send + delete; clips under 1 second rejected with a graceful message) and replay sent voice messages from history (bubble with play + duration + progress) across app restarts. Recording/draft state survives rotation; backgrounding auto-stops and keeps the draft. Builds directly on Phase 67 (recorder, filesDir/voice/ clips, send path). Icon differentiation, model/remote gating, and transcript captions belong to Phase 69 — not this phase.
</domain>

<decisions>
## Implementation Decisions

### Draft Preview UX
- Persistent draft card above the chat input (play/pause + progress + duration + send + delete) — reuses Phase 67 recording-row patterns
- Play/pause toggle + Send (primary) + Delete (error tint) — mirrors Phase 67 recording-row button language
- Sub-1 s clips rejected with a graceful Snackbar ("Recording too short"), file deleted — per VMSG-02
- Caption text stays editable alongside the draft; send transmits voice + caption as one bubble (Phase 67 contract)

### History Playback Bubbles
- Own-voice bubble with play/pause + duration + linear progress, same bubble chrome as text/image messages
- Live progress during playback; starting one clip stops any other (single-player discipline)
- Room holds path + duration (+ transcript placeholder column for Phase 69); bubbles reload across restarts; missing file renders a graceful "clip unavailable" state (never silent drop, never crash)
- Players only on own sent voice bubbles — received/model-side audio is future VF-03, out of scope

### State Survival
- Draft survives rotation (VM-owned state + file on disk); playback position resets to 0 on rotation — simple and honest
- Backgrounding mid-draft keeps the draft (Phase 67 auto-stop-and-keep extends naturally); playback pauses and keeps its position
- Rotation during playback pauses and keeps bubble state; user resumes with one tap
- Delete (draft or own bubble) removes the audio file immediately — Phase 67 cancel-discards precedent, no trash/undo

### Playback Engine + Scope
- VM-owned `VoiceMessagePlayer` (platform MediaPlayer) in `ui/chat/voice/`, sibling to `VoiceMessageRecorder` — zero new Gradle deps
- Request transient audio focus; pause on calls/other-app audio; stop playback on chat exit (no leaks past screen)
- Transcript out of scope — caption/transcript lands in Phase 69 (VMSG-07)
- No new gating in this phase — draft/playback available whenever a clip exists; model/remote gating is Phase 69

### the agent's Discretion
- Exact draft-card elevation/shape, progress-bar styling, and "clip unavailable" copy within existing bubble/Snackbar conventions — follow ChatScreen bubble chrome and Material 3.
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- Phase 67 output: `ui/chat/voice/VoiceMessageRecorder.kt` (+ destroy-deletes-partial), VM recording state (`isVoiceRecording`, `hasVoiceClip`, `voiceElapsedSec`, `voiceAmplitude`), recording-row UI in `ChatInputBar.kt` (Row-1 stop/cancel + timer + progress), `sendVoiceMessage` → `sendMessage(audioBytes)`, `filesDir/voice/` storage, 9 `voice_msg_*` strings EN+ES
- Chat message rendering pipeline (bubble chrome for text/image) — extension point for the voice bubble player row
- Room messages/conversation tables — add path + duration (+ transcript placeholder) columns via migration
- Phase 65 Snackbar/Settings-escape channels for the <1 s and unavailable-clip messages

### Established Patterns
- VM-owned platform wrappers with explicit destroy in onCleared; StateFlow UI state to Compose; single-player discipline mirrors single-flight inference cancel
- App-private `filesDir` blobs + Room metadata/paths only; immediate file delete on discard
- Zero new Gradle deps — platform MediaPlayer only, no Media3/ExoPlayer

### Integration Points
- Draft card above `ChatInputBar` ← VM draft state (clip path + duration + playback position)
- Voice bubble player row in message list ← Room voice metadata (path + duration)
- `VoiceMessagePlayer` lifecycle ← ChatViewModel (stop on exit, pause on background, focus loss)
</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches within the Phase 67 recording-row and bubble-chrome conventions. Keep the player row visually consistent with text/image bubbles so Phase 69 transcript captions slot underneath without re-layout.
</specifics>

<deferred>
## Deferred Ideas

- Icon differentiation (waveform vs mic polish), text-only + remote gating UX, parallel-STT transcript captions — Phase 69 (VMSG-03/04/07/08)
- Waveform artwork in bubbles/draft — future VF-02, not v3.1
- Received/model-side audio playback — future VF-03, not v3.1
- Scrubbing seek bar, simultaneous multi-clip playback — declined (single-player discipline)
</deferred>
