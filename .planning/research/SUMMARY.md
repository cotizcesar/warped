# Project Research Summary

**Project:** Warped — v3.1 Voice Messages + New Tool (Android)
**Domain:** Incremental milestone on an existing Kotlin + Compose + Hilt LLM chat app (LM Studio equivalent; LiteRT-LM local + remote OpenAI-compatible providers, DDG-grounded, v3.0 STT dictation shipped)
**Researched:** 2026-10-02
**Confidence:** HIGH

## Executive Summary

v3.1 is a two-track incremental milestone on a healthy existing tree — not a greenfield build. Track 1 adds in-chat voice messages (record → 60 s cap → playback-before-send draft → send as audio input to audio-capable local models); track 2 adds one new function-calling tool. The headline stack finding is **zero new Gradle dependencies**: the entire milestone is implementable with platform `android.media.*` APIs plus the already-pinned `litertlm-android 0.17.1`. The audio-bytes inference path (`ChatRequest.audioBytes` → `Content.AudioBytes` → audio backend slot → allowlist audio flag) is fully plumbed end-to-end — v3.1 is a capture + persistence + UI task plus a schema-only `ToolSet`, not an inference task.

The recommended approach is foundations-first, device-verified: Room `audio_path` migration first (unblocks everything, zero UI), then recorder + player + ViewModel state with a pure-Kotlin decode/trim module that is JVM-tested before any UI work, then input-bar + bubble UI, then a mandatory on-device record→model→response smoke on an audio-capable allowlist model, with the independent document-reader tool parallelizable once its local shape freezes. Two cross-file agreements drive the phase structure: voice-send is **local-only at launch** (remote providers silently drop `audioBytes` today — gating, not a silent gap), and the 60 s UX cap coexists with the model's 30 s input limit via **send-first-30-s** (full 60 s kept for playback, user-visible note).

The key risks are all litigated in advance: (1) sending raw recorder output (AAC/M4A) to a model that expects mono 16 kHz PCM fails at prefill while playback works fine — transcode before send, never test record→playback only; (2) background recording on API 28+ captures silence with no error — auto-stop on backgrounding, no foreground service for a 60 s attachment; (3) the new tool must be least-privilege read-only (the document reader) with delimiter-marked results, never a high-agency action — v2.2 deleted calculator/clock/JSON tools for no user value and they stay disqualified.

## Key Findings

### Recommended Stack

Zero new dependencies. Platform `MediaRecorder` (AAC/M4A, `setMaxDuration(60_000)`) for capture, `MediaExtractor` + `MediaCodec` plus ~60 lines of hand-written Kotlin DSP (stereo→mono, resample to 16 kHz, int16→float32) for model-input conversion, platform `MediaPlayer` (single shared instance) for playback, `filesDir/voice/` for storage with Room holding paths only. `litertlm-android` stays pinned at 0.17.1. Full rationale and rejected alternatives (Media3/ExoPlayer, FFmpeg, AudioRecord-capture, `Content.AudioFile`, Oboe) in `STACK.md`.

**Core technologies:**
- `android.media.MediaRecorder` (platform) — voice capture, MIC source, MPEG_4/AAC 64 kbps mono, framework-enforced 60 s cap via `setMaxDuration` + `OnInfoListener`; amplitude via `getMaxAmplitude()` polling
- `android.media.MediaExtractor` + `MediaCodec` (platform) + pure-Kotlin DSP — decode AAC→PCM, resample to mono 16 kHz float32, trim to first 30 s; feeds existing `ChatRequest.audioBytes` untouched
- `android.media.MediaPlayer` (platform, one instance per ChatViewModel) — draft preview + history-bubble playback with seek slider
- Existing `litertlm-android 0.17.1` — `Content.AudioBytes`, audio backend slot, `@Tool`/`ToolSet`; no bump
- New tool: zero-dep `ToolSet` sibling of `WebFetchToolSet`/`WebSearchToolSet` (schema-only `@Tool`, `HOST_EXECUTED`, manual-loop execution on `Dispatchers.IO`)

### Expected Features

Voice messages follow the WhatsApp/Telegram three-state attachment flow (record → review draft → send as audio attachment), deliberately **separate from v3.0 dictation** (mic = "speech becomes text"; new waveform/audio-clip icon = "audio goes to the model"). The new tool winner is the **local document reader** (`readTextFile` via SAF picker) — the only candidate unlocking a new input modality while offline with zero permissions and zero deps. Full landscape, ranked candidates, and MVP slice in `FEATURES.md`.

**Must have (table stakes):**
- Tap-to-record with live timer + amplitude feedback; 60 s hard cap with auto-stop-and-keep + countdown
- Playback-before-send draft (play/pause + send + delete); cancel discards file
- Voice bubble in history (play + duration + progress), replayable after restart
- Audio-capability gating (hidden/disabled + reason on text-only models); icon visually distinct from dictation mic
- Graceful failures: <1 s clip, permission denied (Settings escape), encoder error
- New tool: `readTextFile` — SAF picker → bounded read with size cap + truncation envelope → tool result string

**Should have (competitive):**
- Waveform visualization from persisted amplitude samples (static bars, no DSP lib)
- Speaker/volume normalization (resample at send time — invisible improvement)
- Model-generated transcript caption only where the provider returns it free

**Defer (out of v3.1):**
- Realtime two-way voice mode (Gemini Live style) — future milestone, async messages only
- Cloud STT fallback, ExoPlayer, SoundCloud-grade waveform libs, auto-transcribe-and-replace
- Runner-up tool (unit converter) only if document reader blocked; reminders (permission/policy surface), clipboard (trust cost), calculator/clock/currency (disqualified — v2.2 deletion, network-dependent)

### Architecture Approach

New leaves plus one ALTER-only migration on the existing Clean tree. `VoiceMessageRecorder` + `VoiceMessagePlayer` live in `ui/chat/voice/` alongside `VoiceDictationManager` (VM-owned, never Hilt `@Singleton`, destroyed in `onCleared`); `ChatMessage.audioPath` domain field backed by nullable `audio_path` TEXT column (`MIGRATION_17_18` shape, fail-soft mapper, no backfill — the `images` precedent); input-bar wires its currently-dead `modelHasAudio`/`onAudioRecorded` props plus a new voice-send icon (`Audiotrack` recommended — already the audio badge glyph); new `ToolSet` plugs into `LocalToolLoop` + `CompatToolLoop`/`defaultRemoteTools()` with no new loop machinery. Audio bytes are single-turn, never history-carried. Full component map, data flows, and build order in `ARCHITECTURE.md`.

**Major components:**
1. `VoiceMessageRecorder` (NEW, `ui/chat/voice/`) — thin `MediaRecorder` wrapper mirroring `VoiceDictationManager` contract; VM applies all policy
2. `VoiceMessagePlayer` (NEW, `ui/chat/voice/`) — single shared `MediaPlayer`, single-flight playback
3. `ChatViewModel` + `ChatInputState` (MODIFY) — recording state, start/stop/send/cancel, mic-ownership lock vs dictation, `hasContent` extended with audio draft
4. `ChatInputBar` + `VoiceMessageBubble` (MODIFY + NEW) — voice-send icon, recording sheet, playback row in user bubbles
5. Room `audio_path` column + migration + `EntityMappers` (MODIFY) — paths only, bytes never in DB
6. `<New>ToolSet` + loop dispatch + remote mapping + capability-matrix row (NEW + MODIFY) — Phase 47 file list exactly

### Tension Resolution (explicit)

**Tension A — 60 s UX cap vs 30 s model input limit: RESOLVED as send-first-30-s.** STACK (Gemma official audio docs, HIGH) establishes the model hard limit: mono 16 kHz float32, max 30 s clip, 25 tok/s on Gemma 4 → 30 s = 750 audio tokens inside the 4096 budget, 60 s = 1500 tokens unreliable plus prefill-failure risk. FEATURES/PITFALLS/ARCHITECTURE all converge on the same policy from different angles (community ≤30 s reliability gate; context/RAM budget; single-turn no-carry). **Consensus: record up to 60 s, keep the full clip for playback, trim to the first 30 s before `sendMessage`, with a user-visible "first 30 s sent to model" note.** No plan may send >30 s of audio to the model. The trim lives in the pure-Kotlin decode module (unit-testable), and the 30 s trim is device-verified against a real E2B load during the milestone.

**Tension B — audio format: transcode-required vs spike-it-later: RESOLVED as transcode-required with spike-as-verification.** ARCHITECTURE §3.4 (LOW on format details) frames AAC-vs-PCM as an open device spike with an AAC-first attempt. STACK §2 + PITFALLS PIT-03 (HIGH: Gemma docs + two independent on-device implementations + in-repo `AudioBytes` wiring) establish that the model consumes raw PCM bytes and the WAV-path form fails at prefill — recording AAC and sending it raw is the "plays fine, inference fails" trap. **Decision: build the `MediaExtractor`→PCM→16 kHz-mono→trim module from the start (it's ~180 lines of platform code + pure Kotlin, JVM-tested with synthetic fixtures); the device spike then verifies rather than discovers.** AAC-first-send is not an acceptable plan A.

**Tension C — recorder ownership: VM-owned vs app-scoped singleton/service: RESOLVED as VM-owned + auto-stop on background.** PITFALLS PIT-01 recommends an application-scoped holder or foreground service so recording survives rotation/backgrounding; ARCHITECTURE (Phase 65 precedent, HIGH in-tree) forbids Hilt `@Singleton` recorders (native-handle + context leaks across screens/model switches) and owns lifecycle in the ViewModel. **Reconciliation: rotation is a non-issue for VM ownership (ViewModel survives rotation; timer in `StateFlow`, never Compose state); backgrounding is handled by PIT-02 option A (auto-pause/auto-stop-and-keep-draft via `ProcessLifecycleOwner`), which removes the only reason for a service/singleton.** No foreground service, no `microphone` FGS type, no `POST_NOTIFICATIONS` surface for a 60 s attachment. Process-death orphans are swept by a startup orphan-scan (files with no matching `audio_path` row). Recorder `start(): Boolean` synchronous-accept signal (WR-01 lesson) + `stop()`-immediately-after-`start()` `RuntimeException` guard are both requirements.

**Tension D — remote voice-send: RESOLVED as local-only at launch with explicit gating.** FEATURES describes the remote base64-attach path as the general pattern; ARCHITECTURE (in-tree grep, HIGH) finds zero `audioBytes` handling in `data/remote/provider/` — remote voice-send silently drops audio today. PITFALLS PIT-05/PIT-13 add cost/queue dimensions. **Decision: gate voice-send on `LITE_RT_LM && allowlist-verified audio == true` at launch; remote audio mapping is an explicit future phase, never a silent gap.** The gate reuses `verifiedLocalCapabilities()` (never the lazy all-true `LocalModel.capabilities` default), with the same fail-open-while-loading rule as thinking support. Offline matrix: local audio-capable works in airplane mode; remote-offline queues text only (voice requires a local audio model — say so in UI).

**Tension E — winning tool: RESOLVED unanimously as `readTextFile` document reader.** FEATURES ranks 7 candidates with the reader ★★★★★ (only offline private-content input; completes the web+vision+audio "any input" matrix); STACK constrains candidacy to zero-dep pure-Kotlin (disqualifies anything needing keys/network/binary); ARCHITECTURE confirms the plug-in file list is mechanical; PITFALLS PIT-07/PIT-12 require least-privilege read-only with delimiter-marked results + matrix coverage (which the reader satisfies and higher-agency candidates don't). Calculator/clock/JSON-formatter stay **disqualified** (v2.2 user-rejected). Runner-up unit converter resurrects only if the reader is blocked.

**Tension F — transcript-fallback scope: flagged as a planning decision, not resolved here.** PITFALLS PIT-05 proposes an optional transcribe-locally-then-send-text path for text-only models (cheap, compatible everywhere, preserves voice UX); FEATURES lists transcript only as an optional caption and bans auto-transcribe-and-replace as an anti-feature (destroys tone/non-speech purpose, duplicates dictation). **Scope question for planning: whether v3.1 includes a user-visible "send as transcript instead" affordance on non-audio models, or gates voice-send off entirely with an explanation.** Default recommendation: gate-off with explanation at launch (simplest, no STT-quality liability); transcript fallback is a discrete follow-up if users demand it. Do not let it smuggle realtime/STT-engine scope into the milestone.

**Minor — storage directory name:** STACK says `filesDir/voice-messages/`, ARCHITECTURE says `filesDir/voice/`. Recommend `filesDir/voice/` (ARCHITECTURE's LRU-cap + orphan-sweep reasoning is fuller); freeze at plan time — one line either way.

### Critical Pitfalls

Top risks distilled from `PITFALLS.md` (7 critical, 6 moderate; phase-warnings table is the exit-gate source):

1. **Raw recorder output sent to model (PIT-03)** — AAC/M4A plays fine, fails at prefill/hallucinates — transcode to mono 16 kHz PCM before attach; first E2E must be record→model→response on hardware, never emulator-only
2. **Background silence trap (PIT-02)** — API 28+ background mic returns zeros with no error — auto-stop-and-keep-draft on backgrounding; handle audio-focus (calls); no FGS
3. **Rotation/background recorder death (PIT-01)** — timer/file state in VM `StateFlow`; `start()`-accept signal; `stop()`-after-`start()` guard; rotation-at-0:15 QA test
4. **Play RECORD_AUDIO paperwork (PIT-04)** — same permission grant, new data use (stored/sent clips) — Data safety + Sensitive-Permissions declaration update, separate first-tap rationale string, always `checkSelfPermission` at record-start, app-private storage only
5. **Context/RAM blowout (PIT-05)** — 60 s audio ≈ thousands of tokens + ~1.9 MB PCM — 30 s trim + token-budget extension (v2.3 gates) + `MemoryInfo` gate; single-turn no-carry
6. **Room audio schema (PIT-06)** — never BLOB; nullable `audio_path` (+duration/mime) or `voice_attachments` table with cascade; tested migration; delete-chat deletes files; export/share decides explicitly
7. **Tool trust boundary (PIT-07)** — least-privilege schema, arg validation, `<tool_result>`/`<external_data>` delimiters, human approval for side effects, loop caps inherited + adversarial tests ("ignore previous instructions" must not alter behavior)
8. **Icon confusion + mic fight (PIT-08/09)** — distinct waveform icon in attachment row, gated visibility with reason, single mic-ownership lock with transition unit tests
9. **Cap enforced only in UI timer (PIT-10)** — `setMaxDuration(60_000)` + `OnInfoListener` hard stop, UI timer display-only; explicit sampling-rate/bitrate sets (PIT-14, OEM variance)

## Implications for Roadmap

Based on research, suggested phase structure (4 build phases + 1 hardening tail; tool track parallelizable):

### Phase 1: Room Audio Foundation
**Rationale:** Unblocks everything below with zero UI; migration discipline is the highest-blast-radius-if-wrong item (upgrade crashes hit existing users).
**Delivers:** `audio_path` (+`audio_duration_ms`, `audio_mime` or equivalent) nullable columns + tested `Migration` (verify DB version in `AppDatabase.kt` at plan time — v17 assumed) + `EntityMappers` fail-soft both directions + `ChatMessage.audioPath`; JVM migration test as phase gate; delete-message/chat file-cleanup hook identified.
**Addresses:** History persistence for voice bubbles; PIT-06 prevention at the root.
**Avoids:** Pitfalls PIT-06 (blob/no-migration/orphans); Anti-pattern: bytes-in-Room.

### Phase 2: Voice Capture + Send Path (recorder, decode/trim, VM state, gating)
**Rationale:** Dependency order — pure-Kotlin decode/trim module is JVM-tested before UI; device smoke proves the core value prop (model hears audio) before UI polish spend.
**Delivers:** `VoiceMessageRecorder` (60 s `setMaxDuration` hard stop, explicit rate/bitrate, amplitude ticker, VM-owned + `onCleared` destroy) + `AudioToPcm` decode/trim module (MediaExtractor→16 kHz mono float32→first-30-s) with JVM unit tests + VM `startRecording/stopRecording/sendVoiceMessage/cancelRecording` + `RECORD_AUDIO` reuse (separate rationale string, Settings escape, check-at-start) + background auto-stop + mic-ownership lock vs dictation + audio-capability gate (local-only, allowlist-verified) + context/RAM budget extension + offline matrix + **device smoke: record→send→response on an audio-capable allowlist model + airplane-mode local test**.
**Addresses:** Table-stakes capture + send; Tensions A–D; transcript-fallback scope question (planning input).
**Avoids:** Pitfalls PIT-01/02/03/04/05/09/10/13/14/16; Anti-patterns: singleton recorder, merged mic button, history-carry, remote-without-mapping.

### Phase 3: Voice UI (recording sheet, draft preview, history bubbles, icon spec)
**Rationale:** Builds on proven send path; icon/entry-point spec lands before code (PIT-08); accessibility is cheap now, expensive as retrofit.
**Delivers:** Voice-send icon (`Audiotrack`, attachment row, `modelHasAudio && !inputLocked` gate with reason) wired to dead `ChatInputBar` props + recording sheet (timer, 60 s progress, stop/send/cancel) + `VoiceMessagePlayer` single-flight holder + draft preview + `VoiceMessageBubble` (play/pause + duration + progress) + `hasContent` extension (text/images/audioDraft) incl. Enter-key paths + TalkBack descriptions + rotation/background QA + LeakCanary playback leg.
**Addresses:** Table-stakes review/render; icon differentiation (milestone requirement).
**Avoids:** Pitfalls PIT-08/11/17; Anti-patterns: player-per-bubble, two stop icons coexisting.

### Phase 4: New Tool — Document Reader (`readTextFile`)
**Rationale:** Fully independent of Phases 1–3 (touches only agentic loop + SAF launcher); parallelizable after tool choice — which is already made.
**Delivers:** Candidate-risk note (one paragraph — choice is decided, scoring is the audit trail) + `<New>ToolSet` schema-only `@Tool` + `LocalToolLoop` validation/dispatch + armed-config registration + `defaultRemoteTools()` mapping + capability-matrix entries + SAF `OpenDocument` picker + bounded reader (20–50 k char cap, truncation envelope) on `Dispatchers.IO` + delimiter-marked results + loop-cap regression test + adversarial prompt-injection tests + cross-provider (local + ≥1 remote) integration test.
**Addresses:** Winner tool; Tension E.
**Avoids:** Pitfalls PIT-07/12; Anti-patterns: `runBlocking` body, auto-executing SDK path, v2.2-family re-proposals.

### Phase 5: v3.1 Hardening Sweep
**Rationale:** Every pitfalls file assigns verification to a closing sweep; device/Play/grep evidence no build phase produces on its own.
**Delivers:** Upgrade test (old DB→new), delete-chat storage assertion, DB-size-after-20-messages check; 3+-voice-message multi-turn token/RAM test; Play declaration + Data safety diff; background-silence + screen-off-cap tests; GMS-less/permission-matrix pass; orphan sweep + export/share audio-reference decision; adversarial tool tests re-run; "Looks Done But Isn't" checklist as exit gate.
**Avoids:** Locks PIT-02/04/06/11/16/17 — release-day paperwork becomes phase requirements.

### Phase Ordering Rationale

- **Foundations before surfaces:** migration (Phase 1) → capture/send proof (Phase 2) → UI polish (Phase 3) — each phase's output is the next phase's assumption; the decode module's JVM tests precede all UI work per STACK's phase-ordering implication.
- **Device truth early:** the record→model smoke sits in Phase 2, not the tail — if the engine rejects the format, only UI work is at risk, never the architecture.
- **Tool independence exploited:** Phase 4 parallelizes with 1–3 (disjoint files: `data/agentic/` + SAF launcher vs chat/voice/Room); its remote mapping still comes last within the phase (local shape frozen first — Phase 57 precedent).
- **Compliance is build, not paperwork:** Play declaration, rationale strings, and privacy storage rules are Phase 2 requirements (PIT-04), re-verified in Phase 5 — never release-day surprises.

### Research Flags

Phases likely needing deeper research during planning (`/gsd-plan-phase --research-phase`):
- **Phase 2 (capture/send):** MEDIUM — confirm exact LiteRT-LM audio-input contract (sample-rate/format/clip limits) against the pinned 0.17.1 artifacts at plan time; community evidence converges but the official Android audio-input API page deserves a direct re-check. Also settle transcript-fallback scope (Tension F) in discussion before planning.
- **Phase 4 (tool):** LOW-MEDIUM — verify current `LocalToolLoop`/`CompatToolLoop`/`defaultRemoteTools()` wiring state (v2.4 shipped both, "confirm current wiring" flagged twice) and SAF `OpenDocument` MIME handling for `.md`/`.txt`/PDF-guard.

Phases with standard patterns (skip research-phase):
- **Phase 1 (Room migration):** well-documented — `MIGRATION_4_5/14_15/15_16` precedents give the exact ALTER-only shape; only verify the current DB version number.
- **Phase 3 (voice UI):** well-documented — `MediaPlayer` local playback + `ChatInputBar` layout + Phase 65 dictation-UI precedents; icon spec is a design decision, not a research question.
- **Phase 5 (hardening):** execution-only — checklist-driven, no new APIs.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Platform `android.media.*` APIs (HIGH, official docs) + Gemma audio spec (HIGH, official) + in-repo wiring verified by direct read (HIGH); `litertlm-android` pin confirmed in version catalog — stay on 0.17.1 |
| Features | HIGH | LiteRT-LM audio/tool contracts fetched 2026-10-02 (HIGH); voice-note UX patterns convergent across WhatsApp/Sendbird/Gemini-redesign (MEDIUM); tool ranking grounded in v2.2 deletion precedent (HIGH in-repo) |
| Architecture | HIGH | Every integration point cites an existing file/symbol; only LiteRT-LM byte-format detail is doc-sourced (MEDIUM) with a HIGH-confidence fallback plan |
| Pitfalls | HIGH | Android platform facts from official docs (HIGH); LiteRT-LM audio specifics docs + community code (MEDIUM); AI-risk mitigations from official Android guidance (HIGH, applicability is design judgment) |

**Overall confidence:** HIGH

### Gaps to Address

- **LiteRT-LM audio-input exact contract vs 0.17.1:** re-confirm sample-rate/format/clip/token-cost expectations against the pinned version's docs at Phase 2 plan time — handle via `--research-phase` on Phase 2. Decision is already made (transcode to 16 kHz mono + 30 s trim); this is verification, not discovery.
- **Current DB version number:** ARCHITECTURE assumes v17 from the migration chain — verify in `AppDatabase.kt` before freezing `MIGRATION_17_18` numbering.
- **`RECORD_AUDIO` manifest coverage for `MediaRecorder`:** same permission, but confirm the declaration covers the new use; Play paperwork (Data safety + Sensitive-Permissions + separate rationale string) is a Phase 2 requirement regardless.
- **`deleteMessage`/export Vorleistung:** confirm the file-cleanup hook point and the export/share audio-reference policy at Phase 1/2 plan time so legacy render paths never crash on new columns.
- **Transcript-fallback scope (Tension F):** product decision for Phase 2 discussion — default is gate-off-with-explanation; do not let it expand STT-engine scope.
- **Storage dir name:** `filesDir/voice/` vs `filesDir/voice-messages/` — freeze at plan time (recommend `voice/`).

## Sources

### Primary (HIGH confidence)
- Gemma audio input spec (mono 16 kHz float32, 30 s clip, 25/6.25 tok/s) — `ai.google.dev/gemma/docs/capabilities/audio` (official)
- LiteRT-LM Android guide (`Content.AudioBytes` ByteArray, `audioBackend`, `@Tool`/`ToolSet`/`automaticToolCalling`) — fetched 2026-10-02 (official)
- Android `MediaRecorder` reference + overview (lifecycle, `setMaxDuration` + `MEDIA_RECORDER_INFO_MAX_DURATION_REACHED`, `pause()`/`resume()`, background-mic restriction, RECORD_AUDIO runtime model) — `developer.android.com` (official)
- Play User Data policy (prominent disclosure, Data safety) + sensitive-permissions declaration — `support.google.com/googleplay` (official)
- Android AI-risk mitigations (prompt-injection delimiters, excessive-agency least-privilege/approval) — `developer.android.com/privacy-and-security/risks/` (official)
- In-repo wiring (`ChatRequest.audioBytes`, `LiteRTLmProvider:281-305`, `EngineManager:206-211`, `sendMessage:462`, `ChatInputBar:54-56`, `model_allowlist.json` audio flags, `Migrations.kt` chain, `WebFetchToolSet` schema-only kdoc) — verified by direct read

### Secondary (MEDIUM confidence)
- WhatsApp voice-message draft-preview + waveform (Meta announcement via search); Sendbird/TalkJS record→preview→send SDK patterns; Gemini Android 2026 voice-input redesign (9to5Google)
- Raw-PCM-not-WAV-path + 4096 audio-prefill budget — independent on-device implementations (envsense mobile CLAUDE.md; ashokvarmamatta gist, corroborated)
- LiteRT-LM Gemma3 audio preprocessor (`AudioPreprocessorMiniAudio`, soft tokens) — C++ source (architecture, not Android API contract)
- v2.2 Calculator/CurrentTime/JsonFormatter deletion for no user value (PROJECT.md Key Decisions + milestone notes)

### Tertiary (LOW confidence)
- None load-bearing — all single-source claims carry a HIGH-confidence fallback (transcode module, device smoke, gate-off defaults); LiteRT-LM format details flagged as plan-time verification, not decision risk.

---
*Research completed: 2026-10-02*
*Ready for roadmap: yes*
