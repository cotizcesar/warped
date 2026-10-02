# Domain Pitfalls: v3.1 Voice Messages + New Tool

**Domain:** Adding in-app audio recording + audio-model input + a new function-calling tool to an existing Android LLM app (Warped — LiteRT-LM local, remote OpenAI-compatible endpoints, offline-first, Room v16+, v3.0 SpeechRecognizer dictation already shipped)
**Researched:** 2026-10-02
**Overall confidence:** HIGH for Android platform facts (official docs), MEDIUM for LiteRT-LM audio specifics (docs + community code, no Context7)

---

## Critical Pitfalls

Mistakes that cause rewrites, Play rejection, data loss, or security incidents.

### PIT-01: Recording through Activity-scoped MediaRecorder that dies on rotation/backgrounding
**What goes wrong:** Recorder is owned by a Composable/ViewModel tied to the chat screen. User rotates the phone or switches apps mid-recording → Activity recreates or stops → `MediaRecorder.release()` in `onStop()` kills the session, the partial file is corrupt, and the 60s timer state is lost. User re-records from scratch.
**Why it happens:** Official Android guidance says to release MediaRecorder in `onStop()` — correct for a camera-style Activity, wrong for a chat attachment flow where the recording must survive short lifecycle transitions. Developers copy the sample verbatim.
**Consequences:** Lost recordings, corrupt 0-byte files, 1-star "ate my voice message" reviews.
**Prevention:**
- Own the recorder in a **lifecycle-aware holder outside the UI layer**: either an `Application`-scoped recorder manager (Hilt `@Singleton`) with explicit start/stop, or a **foreground Service** if recording must continue with the screen off (see PIT-02).
- Timer/cap countdown lives in a `StateFlow` in the holder, not in Compose state — recompositions and rotations must not reset it.
- On `onStop()` without an active recording, release; with an active recording, keep and show an ongoing-recording indicator (notification if service-backed).
- Handle the documented `stop()`-immediately-after-`start()` `RuntimeException`: delete the malformed output file, don't crash.
**Detection:** Rotate the device at 0:15 of a recording in QA; if the timer resets or the file is unplayable, this pitfall fired.
**Phase:** Voice recording phase — recorder ownership + rotation/background test must be in the phase plan, not a follow-up.

### PIT-02: Background recording without a foreground service (silent-audio trap on API 28+)
**What goes wrong:** App records while the user locks the screen or takes a call. On API 28+, background apps receive **silent audio** from the mic — the file records 60 seconds of zeros and the user sends a "voice message" containing nothing. No error is raised; the failure is silent.
**Why it happens:** Developer tests only in-foreground; assumes recording continues because no exception is thrown. Official docs state background mic access returns silence, not an error.
**Consequences:** Empty voice messages sent to the model (wasted inference + user confusion), or a Play policy problem if the app tries workarounds (see PIT-04).
**Prevention:** Decide up front, and the milestone scope ("in-chat recorder with 60s cap") suggests the cheap correct answer:
- **Option A (recommended): pause/stop recording when the app backgrounds.** Observe `ProcessLifecycleOwner`; on background, auto-pause (API 24+ `pause()`/`resume()`) or auto-stop-and-keep-draft. Simple, no FGS, no Play declaration burden.
- **Option B: foreground service with `microphone` FGS type** (API 30+ requires `FOREGROUND_SERVICE_MICROPHONE`; API 34+ requires runtime `FOREGROUND_SERVICE_*` permission + manifest type declaration). Only if the requirement demands lock-screen recording. Adds notification, Play review surface, and battery scrutiny — disproportionate for a 60s chat attachment.
- Either way: also handle **audio-focus interruption** (phone call) — pause recording via `onAudioFocusChange`, don't capture the call.
**Detection:** Record → press power button → unlock → play back. Silence = this pitfall.
**Phase:** Voice recording phase — the backgrounding decision is a planning input, not an implementation detail.

### PIT-03: Sending raw recorder output to the model — wrong container/codec for audio-capable models
**What goes wrong:** `MediaRecorder` defaults (`THREE_GPP`/`AMR_NB`) produce a file the audio preprocessor can't consume. LiteRT-LM's Gemma 3n/4 audio path (`AudioPreprocessorMiniAudio`, USM-style config) expects **mono 16 kHz WAV/PCM-like input**; community implementations converge on mono 16 kHz WAV and short clips (one reference gates reliability at ≤30s mono clips, experimental beyond). Sending AMR/3GP either errors in preprocessing or silently degrades transcription quality.
**Why it happens:** Developer treats "audio file" as interchangeable; tests only the record→playback path (MediaPlayer plays anything) and never the record→model path on-device.
**Consequences:** "Voice message" feature that records and plays fine but fails or hallucinates at inference time — the core value prop broken while all unit tests pass.
**Prevention:**
- Record in a model-digestible format from the start: `MPEG_4` container is fine for playback, but **transcode/resample to mono 16 kHz WAV before attaching to the prompt** (small utility, testable on JVM with synthetic PCM).
- Alternatively record WAV directly via `AudioRecord` — more code (manual PCM→WAV header), more control (exact sample rate, live amplitude for the waveform UI). Prefer MediaRecorder + transcode unless a live waveform is required.
- Cap enforcement must consider **preprocessed size, not just seconds**: 60 s of 16 kHz mono 16-bit ≈ 1.9 MB PCM — fine for local, but see PIT-05 for context-window cost. Validate duration AND file size before send.
- Gate the send path on the **loaded model's actual audio capability** (runtime capability flag from the model/session, not the model name string) — text-only models (Qwen, Gemma 3 1B) must disable/hide the voice-send icon with an explanation, not send audio into a text-only session and crash the preprocessor with "Provided more audio than expected"-class errors.
**Detection:** First on-device E2E test of record→send→response on an audio-capable model (Gemma 4 E2B / 3n E2B). Emulator-only testing hides this (MediaPipe/LLM runtimes commonly don't support emulators — require physical-device smoke).
**Phase:** Voice recording phase — needs a device-smoke requirement (record→model→response on hardware), CI-gated unit tests can't cover it.

### PIT-04: RECORD_AUDIO Play compliance — missing declaration, missing rationale, dictation/reuse confusion
**What goes wrong:** `RECORD_AUDIO` is a dangerous permission AND Play-sensitive user data (microphone). App adds in-app recording but: (a) no Play Console **Sensitive App Permissions** declaration for the new microphone use, (b) no **Data safety section** update (audio collection), (c) no in-app prominent disclosure before first record, or (d) assumes the v3.0 SpeechRecognizer permission grant covers the new use — it covers the *permission*, not the *disclosure/Declaration* for a new feature (voice messages retained/sent vs. transient dictation).
**Why it happens:** "We already have RECORD_AUDIO from dictation, so nothing to do." The permission grant is the same; the Play paperwork (purpose, retention, Data safety) differs because a stored/sent voice clip is a new data use.
**Consequences:** Extended Play review, rejection, or update stuck in "pending publication"; worst case a User Data policy strike.
**Prevention:**
- Update **Data safety → Microphone/audio collection** (collected, purpose: app functionality, retained as chat attachments until deleted, not shared) and **Sensitive App Permissions** declaration with a voice-message-specific justification before submitting the release with this feature.
- In-app: first-tap rationale for the voice-send icon **separate from the dictation rationale** (v3.0 established the first-tap-rationale + Settings-escape pattern — reuse the pattern, not the same string; the purposes differ).
- Handle **permanent denial** with a Settings deep-link escape hatch (already established in v3.0 — extend, don't reinvent).
- Handle the mic/camera **system toggle + one-time grant**: a one-time grant from dictation may already be expired when the user records — always check `checkSelfPermission` at record-start, never cache "granted".
- Privacy-by-design: store voice clips in **app-private internal storage** (`filesDir`), never external/shared; delete the file when the message is deleted (see PIT-06); never log/transmit raw audio except as the model input the user explicitly sent.
**Detection:** Pre-release checklist item: Play Console declaration + Data safety diff reviewed before rollout. Lint: grep for new `RECORD_AUDIO` usage sites vs. declared purposes.
**Phase:** Voice recording phase — compliance tasks are phase requirements, not release-day paperwork.

### PIT-05: Audio blows the context window and the RAM budget — 60 s of audio ≠ 60 s of text
**What goes wrong:** Audio soft-tokens are far denser than users intuit: tens of seconds of audio can consume **thousands of context tokens** (USM-style encoders emit roughly one token per ~tens of ms of audio — 60 s can be several thousand tokens). On a 4K/8K-context on-device model that's a quarter to half the window for one message; multi-turn chats with several voice messages overflow → silent truncation of history or OOM on low-RAM devices. Remote endpoints bill audio input tokens at a premium (OpenAI-compatible audio input pricing is multiples of text).
**Why it happens:** Feature spec says "60-second cap" as if duration were the only budget. No token accounting for the audio modality; existing text-only budget guards don't see audio tokens.
**Consequences:** Degraded answers (history silently dropped), `OutOfMemoryError` on 6–8 GB devices, surprise API bills on remote endpoints.
**Prevention:**
- Extend the existing **model-window-aware grounding/budget logic** (v2.3 established a global grounding budget with exit gates) to include an **audio token estimate per voice message**; when the budget is exceeded, degrade gracefully (transcribe-first fallback? refuse-with-explanation? drop oldest audio?) — a deliberate policy, not silent truncation.
- **Memory gate before inference**: check `ActivityManager.MemoryInfo` before loading audio into the session; suggest a shorter clip or text instead of crashing.
- For remote endpoints: confirm the endpoint's audio-input support via the **capability matrix** pattern (v2.4) and surface cost/latency honestly; never send audio to an endpoint that will 400 on it — validate and explain.
- Consider a **transcribe-locally-then-send-text** option for text-only models: on-device STT of the clip, user-editable transcript, sent as text. Cheaper, compatible everywhere, and preserves the voice UX. (Scope decision for planning: full audio-input vs. transcript fallback.)
**Detection:** Multi-turn test: 3+ voice messages in one conversation on a mid-range device + a token-count assertion in unit tests for the budget estimator.
**Phase:** Voice recording phase (budget + gating) — not polish; it determines whether the feature works in real conversations.

### PIT-06: Room schema for audio messages — blob in the messages table, no migration, orphaned files
**What goes wrong:** One of three variants: (a) storing the audio bytes as a BLOB in `messages` → DB bloat, slow queries, `TransactionTooLarge` on process death; (b) adding a non-null column without a migration → crash on upgrade (`IllegalStateException: Migration didn't properly handle`); (c) storing a file path but never deleting the file when the message/chat is deleted → unbounded growth in `filesDir` (60 s clips ≈ MBs each).
**Why it happens:** Audio messages look like "just another message" — developer adds a column and moves on. The project already has Room v16 and a `MIGRATION_14_15`-style discipline with JVM static gates; a rushed milestone skips it.
**Consequences:** Upgrade crashes (worst — affects existing users), storage bloat, failed Play reviews on excessive storage.
**Prevention:**
- **Store files on disk, references in Room**: new nullable columns on `messages` (`audio_path`, `audio_duration_ms`, `audio_mime`) or a small `voice_attachments` table keyed by `message_id` with `ON DELETE CASCADE`. Never BLOB audio.
- Follow the established migration discipline: version bump + tested `Migration` object + JVM-verifiable gate; keep the "delete-all-chats" path (drawer-bottom, v3.0) deleting audio files too — cascade or explicit cleanup in the repository, covered by a unit test.
- Playback must stream from disk (`MediaPlayer.setDataSource(path)` + `release()` in `onStop`), never load the whole clip into memory; only one player at a time (stop previous on new play — same single-flight discipline as inference cancellation in v2.1).
- Chat-history export/share must handle audio references (skip with a placeholder, or attach file) — decide explicitly so legacy render paths (read-only legacy rows from v2.2/v3.0) don't crash on the new columns.
**Detection:** Upgrade test (old DB → new version), delete-chat storage assertion, and a "DB size after 20 voice messages" check.
**Phase:** Voice recording phase — schema design is the first task, migration test is a phase gate.

### PIT-07: New tool breaks the trust boundary — excessive agency + untrusted tool output
**What goes wrong:** The new tool is designed for capability, not least privilege: broad file/network access, open-ended parameters, or its output (web content, file text, device data) flows back into the prompt **unmarked** → indirect prompt injection ("ignore previous instructions…") executes through the tool loop. The project previously built and then removed a tool surface (v2.2) and re-added an agentic loop (v2.4) — institutional memory of the trust-boundary design may have atrophied.
**Why it happens:** Tool evaluation optimizes for "value" and "wow"; security review happens after implementation. The Android official guidance (excessive-agency + prompt-injection pages) is explicit but only helps if it's a phase input.
**Consequences:** Data exfiltration via tool output, unintended destructive actions, user-trust loss. For tools touching device data: a malicious page/file can inject instructions the model obeys.
**Prevention (apply the official Android AI-risk mitigations as requirements):**
- **Minimal, single-purpose tools**: the new tool does one thing with a strict parameter schema; validate args against the schema before dispatch (reject unknown enums, out-of-scope identifiers).
- **Trust-boundary marking**: wrap tool results in explicit delimiters (`<tool_result name=…>…</tool_result>` / `<external_data>`) and instruct the system prompt to treat delimited content as data, never instructions. This is the documented indirect-injection mitigation — implement it in the shared tool-loop path, not per-tool.
- **Human approval for side effects**: any tool action that mutates state, sends data off-device, or spends money gets a confirmation dialog (never auto-execute). Read-only tools may auto-run; the classification must be explicit per tool.
- **Cap and hygiene**: keep the v2.4-established loop caps (call cap, Stop-cancels-all, transient rows, channel hygiene) — the new tool inherits them; add a regression test proving it.
- **Candidate analysis must score abuse potential**: each tool candidate gets a risk column (data access × exfiltration path × side-effect severity). A high-value/high-agency candidate loses to a moderate-value/read-only one unless the milestone explicitly budgets the confirmation UX + validation work.
**Detection:** Adversarial test cases in the tool phase: tool output containing "ignore previous instructions" must not alter behavior; destructive-arg fuzzing against the schema validator.
**Phase:** New-tool phase — candidate analysis (with risk scoring) first, then implementation with validation + confirmation UX as requirements.

---

## Moderate Pitfalls

### PIT-08: Two mic icons, one confused user — voice-send vs. dictation indistinguishability
**What goes wrong:** Chat input ends up with two microphone-ish affordances; users tap the wrong one, speak a 45-second message into the transient dictation box (which was designed for short input), or tap voice-send when they wanted quick STT. Support burden + feature looks broken.
**Prevention:** Visually distinct icons (dictation = mic/keyboard-adjacent; voice-send = waveform/attachment-style with duration), distinct entry points (dictation lives in the text field; voice-send lives with attachments/send), model-gated visibility (voice-send hidden/disabled with a one-line reason on text-only models), and a first-run hint. Usability test with 3 users suffices.
**Phase:** Voice recording phase — icon/entry-point spec before implementation.

### PIT-09: SpeechRecognizer dictation and MediaRecorder fighting over the mic
**What goes wrong:** User starts a voice recording while dictation is listening (or vice versa) → `IllegalStateException` / silent failure / both capture garbage. The v3.0 single-insertion dictation state machine doesn't know about the new recorder.
**Prevention:** A single **mic-ownership lock**: starting either path stops/cancels the other; UI reflects the single active capture state. Unit-test the state machine transitions (dictate→record, record→dictate, incoming-call-during-either).
**Phase:** Voice recording phase — integration with the existing dictation state machine is an explicit task.

### PIT-10: 60-second cap enforced only in the UI timer
**What goes wrong:** Countdown is a Compose-side timer; background throttling, Doze, or a slow device lets recording run past 60 s → oversized file, budget overrun (PIT-05), or `setMaxDuration` never set so nothing stops it at the platform level.
**Prevention:** Defense in depth: `MediaRecorder.setMaxDuration(60_000)` + `OnInfoListener(MEDIA_RECORDER_INFO_MAX_DURATION_REACHED)` as the hard stop, UI timer as the display. Auto-stop → draft-kept → user reviews/sends. Test by letting the timer run with the screen off.
**Phase:** Voice recording phase.

### PIT-11: Playback leaks — MediaPlayer held across navigation, multiple simultaneous players
**What goes wrong:** Each chat bubble creates its own player; navigating away leaks native players (the v2.5 LeakCanary tour explicitly covers chat — new players are new leak surface), or two voice messages play simultaneously.
**Prevention:** Single shared `VoicePlayer` holder (one `MediaPlayer` at a time, `release()` on completion/navigation), playback position in UI state (restorable), LeakCanary tour extended with a play-navigate leg, regression tests mirroring the v2.5 clean-path locks.
**Phase:** Voice recording phase — playback architecture + leak-tour extension.

### PIT-12: New tool duplicates the removed-skills mistakes (formatter/parser + remote matrix gaps)
**What goes wrong:** Tool works locally but breaks on remote endpoints (different `tools[]` schemas per provider — OpenAI/Anthropic/Ollama/LM Studio/Custom), or the formatter/parser for the local model doesn't cover the new tool's schema (constrained-decoding mismatch → model emits malformed calls). v2.4 built a capability matrix + classifier; the new tool must extend them, not bypass them.
**Prevention:** New tool ships with: local formatter/parser coverage, capability-matrix entry per provider, fallback notice where unsupported, and Stop-cancellation. Integration test across at least local + one remote provider.
**Phase:** New-tool phase.

### PIT-13: Offline-first violated — voice send requires network, or queued voice messages lose audio
**What goes wrong:** Voice message to a remote endpoint while offline either crashes, silently drops the audio file reference, or retries by re-running inference (v2.3 established: retry reuses rows, inference never re-runs — audio must follow the same rule).
**Prevention:** Same offline pattern as grounding-retry: queue the message with audio intact, `En espera`-style state, retry sends the same attachment without re-recording or re-running inference. Local audio-capable models work fully offline (verify on-device with airplane mode).
**Phase:** Voice recording phase — offline matrix (local-offline, remote-offline-queue, retry) as requirements.

---

## Minor Pitfalls

### PIT-14: Forgetting `setAudioSamplingRate`/`setAudioEncodingBitRate` → device-dependent output
Different OEMs default differently; identical code yields 8 kHz on one phone and 44.1 kHz on another → inconsistent model quality. Set all three (source, rate 16 kHz or 44.1 kHz pre-transcode, encoder AAC) explicitly.
**Phase:** Voice recording phase.

### PIT-15: Not requesting `POST_NOTIFICATIONS` handling for the recording indicator
If a foreground service is chosen (PIT-02 option B), the persistent notification needs runtime notification permission on API 33+; without it the user gets no visible recording indicator — a Play privacy red flag (secret recording appearance). Prefer option A to avoid this entirely.
**Phase:** Voice recording phase (only if FGS chosen).

### PIT-16: Voice drafts lost on process death
Recording in progress when the system kills the process → partial file orphaned, no recovery. Persist recorder state (output path, start timestamp) to DataStore/saved-state; on restore, offer the partial clip or clean it up. At minimum, orphan-scan `filesDir/voice/` on startup and delete unreferenced files.
**Phase:** Voice recording phase.

### PIT-17: Accessibility — record/stop/playback not operable via TalkBack/switch access
Icon-only mic buttons with no content descriptions exclude users and risk Play accessibility review flags. Every new affordance gets a content description, and recording state is announced.
**Phase:** Voice recording phase (cheap if done with the UI, expensive as retrofit).

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| Voice recorder + playback UI | PIT-01 (lifecycle), PIT-08 (icon confusion), PIT-09 (mic fight), PIT-10 (cap) | Recorder holder outside UI; icon spec + mic lock + `setMaxDuration` as phase requirements |
| Audio → model send path | PIT-03 (codec), PIT-05 (context/RAM), PIT-13 (offline) | Transcode-to-WAV utility + capability gating + budget extension + offline matrix; device-smoke E2E required |
| Room schema for audio | PIT-06 (blob/migration/orphans) | Files-on-disk + migration test + cascade-delete test as phase gates |
| Permissions + Play | PIT-04 (declaration/disclosure) | Data safety + declaration + separate rationale before release submit |
| New tool candidates | PIT-07 (trust boundary) | Risk-scored analysis; least-privilege design; delimiter + validation + confirmation UX |
| New tool implementation | PIT-12 (matrix/formatter gaps) | Matrix entry + parser coverage + cross-provider integration test |
| Playback + leaks | PIT-11 (player leaks) | Shared player holder; extend LeakCanary tour with playback leg |
| Release hardening | PIT-02 (background silence), PIT-16 (orphans), PIT-17 (a11y) | Background-policy test, startup orphan sweep, content descriptions |

---

## Sources

- Android MediaRecorder reference — `pause()`/`resume()` semantics, `release()` in `onStop()` guidance, `stop()`-after-`start()` RuntimeException, `setMaxDuration` + `MEDIA_RECORDER_INFO_MAX_DURATION_REACHED`: https://developer.android.com/reference/android/media/MediaRecorder (HIGH)
- Android MediaRecorder overview — runtime permission flow, background-mic restriction (API 28+: background apps get no mic access): https://developer.android.com/media/platform/mediarecorder (HIGH)
- Request RECORD_AUDIO + dangerous-permission runtime model: https://developer.android.com/media/platform/mediarecorder (HIGH)
- Play User Data policy — prominent disclosure + affirmative consent before mic access; Data safety section obligations: https://support.google.com/googleplay/android-developer/answer/10144311 (HIGH)
- Play sensitive-permissions declaration (Permissions Declaration Form / Sensitive App Permissions in Play Console): https://support.google.com/googleplay/android-developer/answer/9214102 (MEDIUM — process details shift; verify in Console at release time)
- LiteRT-LM audio support — Gemma3 data processor audio path (`AudioPreprocessorMiniAudio`, `<audio_soft_token>`, `InputAudio`): https://github.com/google-ai-edge/LiteRT-LM/blob/main/runtime/conversation/model_data_processor/gemma3_data_processor.cc (MEDIUM — C++ source confirms architecture, not Android API contract)
- LiteRT-LM multimodal (Gemma 4 E2B text+image+audio; model-gated audio; mono 16 kHz WAV guidance, short-clip reliability): community implementations (lukaskris/litert-lm-mobile-android, llamadart chat_app) — LOW, patterns agree but unverified against official LiteRT-LM Android audio API docs; **phase research should confirm the exact `LlmInference`/`GenAI` audio-input API and format requirements against the LiteRT-LM version in the app's version catalog**
- Android AI-risk mitigations — prompt injection (delimiters, output validation), excessive agency (least privilege, arg validation, human approval): https://developer.android.com/privacy-and-security/risks/ai-risks/risks-mitigations + prompt-injection and excessive-agency pages (HIGH for guidance; applicability to the new tool is a design judgment)
- AI Edge Function Calling SDK (declarations, formatter/parser, constrained decoding): https://developers.google.com/edge/mediapipe/solutions/genai/function_calling/android (MEDIUM — MediaPipe-era doc; confirm against the LiteRT-LM `@Tool`/loop path actually in the codebase)
- Project context: `.planning/PROJECT.md` v3.0 shipped items (SpeechRecognizer dictation state machine, first-tap rationale, Keystore cleanup) and v2.x established patterns (budget gates, capability matrix, migration discipline, LeakCanary tour) — HIGH (in-repo)
