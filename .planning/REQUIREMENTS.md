# Requirements: Warped

**Defined:** 2026-10-02
**Core Value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.

## v3.1 Requirements

Requirements for v3.1 Voice Messages + New Tool. Each maps to roadmap phases.

### Voice Messages

- [x] **VMSG-01**: User records a voice message from the chat input with live timer + amplitude feedback, a 60-second hard cap with auto-stop-and-keep, and cancel that discards the file
- [x] **VMSG-02**: User previews the recorded draft before sending (play/pause + send + delete); clips under 1 second are rejected with a graceful message
- [ ] **VMSG-03**: User sees a voice-send icon visually distinct from the STT dictation mic (waveform/audio-clip vs mic glyph); starting one input mode stops the other
- [ ] **VMSG-04**: User on a text-only model gets a gated voice-send affordance (hidden or disabled with reason — never a dead button)
- [x] **VMSG-05**: User sends the voice message to an audio-capable local model (full 60 s kept for playback, first 30 s transcoded to mono 16 kHz PCM via the existing audioBytes path, with a user-visible note)
- [x] **VMSG-06**: User replays sent voice messages from history (bubble with play + duration + progress) across app restarts
- [ ] **VMSG-07**: User sees a transcript caption under their own voice bubble (captured via parallel on-device STT during recording; duration-only fallback when STT unavailable)
- [ ] **VMSG-08**: User on a remote endpoint gets a gated voice-send with explanation (voice-send is local-only at launch — remote providers drop audioBytes)

### New Tool (Document Reader)

- [ ] **TOOL-01**: User picks a text document via the system picker and its bounded content grounds the answer (readTextFile with size cap + truncation envelope, least-privilege read-only)
- [ ] **TOOL-02**: User invokes the document reader on remote endpoints too (remote tools[] mapping alongside the local loop)
- [ ] **TOOL-03**: Fallback candidate (unit converter) stays scoped as contingency-only — implemented only if readTextFile proves unfit at plan time

## Future Requirements

### Voice follow-ups

- **VF-01**: Realtime voice conversation (barge-in, streaming audio I/O) — own milestone, not v3.1
- **VF-02**: Waveform artwork in bubbles and draft (beyond amplitude bar + progress)
- **VF-03**: Transcript captions for received/model-side audio (if model audio output ever lands)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Background recording via foreground service | 60 s attachment doesn't justify a mic-type FGS; auto-stop on backgrounding instead |
| Offline STT engines (Vosk/whisper) / ML Kit | 50–150 MB for a nice-to-have; ML Kit banned by dependency gate; reuse platform STT |
| Media3/ExoPlayer, FFmpeg, AudioRecord-capture, Oboe | Platform MediaRecorder/MediaPlayer suffice; zero-new-dep budget per research |
| High-agency tools (delete/write/send/network actions) | Trust boundary: new tool is read-only; side-effecting tools need approval UX (own milestone) |
| Calculator/clock/JSON tools | v2.2 deleted exactly this family for no user value — stays disqualified |
| Remote voice-send at launch | Remote providers silently drop audioBytes today; local-only with honest gating |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| VMSG-01 | Phase 67 | Complete |
| VMSG-05 | Phase 67 | Complete |
| VMSG-02 | Phase 68 | Complete |
| VMSG-06 | Phase 68 | Complete |
| VMSG-03 | Phase 69 | Pending |
| VMSG-04 | Phase 69 | Pending |
| VMSG-07 | Phase 69 | Pending |
| VMSG-08 | Phase 69 | Pending |
| TOOL-01 | Phase 70 | Pending |
| TOOL-02 | Phase 70 | Pending |
| TOOL-03 | Phase 70 | Pending (contingency-only) |
