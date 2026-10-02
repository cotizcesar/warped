# Feature Landscape: v3.1 Voice Messages + New Tool

**Domain:** In-chat voice messages (audio-attachment input) + one new function-calling tool for a mobile LM-Studio-equivalent
**Researched:** 2026-10-02
**Scope:** NEW features only. Already shipped (do NOT re-spec): text chat local/remote with streaming, image attachments (vision slot gating), STT dictation into input (v3.0 mic), DDG web grounding, presets, history, benchmark, Prompt Lab, Play Review.

## Part 1 — Voice Messages (record → 60s cap → playback → send as model audio input)

### How it typically works in mobile LLM apps

The established pattern (WhatsApp/Telegram voice notes, Sendbird/TalkJS chat SDKs, Gemini Android's 2026 voice-input redesign toward messaging-app style) is a three-state attachment flow that is deliberately **separate from dictation**:

1. **Record** — tap (or hold) a dedicated affordance → `MediaRecorder` captures AAC (`MPEG_4`/`AAC`, `VOICE_RECOGNITION` or `MIC` source) to the app cache dir. A live timer + amplitude/waveform indicator runs; the 60 s cap auto-stops the recorder (standard pattern: hard stop at cap, keep what was captured — "draft preview" à la WhatsApp).
2. **Review (playback before send)** — the captured clip becomes a draft chip/row: play/pause button + duration + waveform or progress bar + **Send** + **Delete/cancel**. Playback via `MediaPlayer` (or ExoPlayer if already a dependency — it is not; prefer `MediaPlayer` for a single local file). This draft-preview step is table stakes: WhatsApp added "Draft Preview" explicitly because users refuse to send unheard audio.
3. **Send as model audio input** — the message is sent as an **audio attachment**, not transcribed text: local path wraps raw PCM/bytes into LiteRT-LM `Content.AudioBytes` (audio-capable models only, e.g. Gemma 3n; audio encoder runs on its own `audioBackend` slot); remote path base64-attaches to the provider's audio-input field (OpenAI-compatible `input_audio`, where supported).

**Dictation (existing v3.0 mic) vs voice message (new) — the critical distinction:**

| | Dictation mic (SHIPPED v3.0) | Voice message (NEW v3.1) |
|---|---|---|
| Output | Text inserted at cursor in the input field | Audio-attachment message sent to the model |
| Engine | Platform `SpeechRecognizer` (on-device/Google STT) | `MediaRecorder` file → model hears raw audio |
| Model requirement | None (works with every model) | Audio-capable model only (local: TF_LITE_AUDIO_ENCODER_HW / Gemma 3n-class; remote: provider audio-input support) |
| Permission | `RECORD_AUDIO` (already declared, `uses-feature required=false`) | Same `RECORD_AUDIO` — no manifest change needed |
| Review step | Live partial text in input (already built) | Playback-before-send draft (must build) |
| Failure surface | Silent-error policy (accepted risk, STATE.md) | Must surface: too-short clip, cap reached, model lacks audio |

### Icon / UX differentiation patterns (industry)

- **Two distinct affordances, never one mic doing both.** ChatGPT mobile: mic = voice *conversation/dictation*, separate attach (`+`) = files. Gemini: prompt-bar mic = dictation, separate waveform/Live entry = audio experience. Gboard: keyboard mic = dictation. The rule: **mic icon = "speech becomes text"; waveform/audio-clip icon = "audio goes to the model."**
- **Recommended for Warped:** keep the existing mic icon for dictation; add a **waveform (`graphic_eq` / `audio_file`) or mic-with-waveform-badge icon** for voice-send, placed in the attachment row/picker next to the image-attach affordance (it IS an attachment — audio slot — not a second mic). Disabled/hidden with an explanatory tooltip when the active model lacks audio capability (mirrors the existing vision/audio slot-gating pattern from v2.5).
- **In-history rendering:** sent voice message renders as a **play-button + duration bubble** (WhatsApp/Telegram/Sendbird standard), replayable on tap; text reply from the model appears below as usual. Do NOT auto-transcribe-and-replace — the audio is the message. (Optional later: model-generated transcript as caption — defer, see Anti-Features.)

### Dependencies on existing code (verified in repo)

| Dependency | Status | Location |
|---|---|---|
| `Content.AudioBytes(audioBytes)` send path | ✅ EXISTS — reuse, feed recorded bytes here | `LiteRTLmProvider.kt:289` |
| Audio backend slot + capability gating (`TF_LITE_AUDIO_ENCODER_HW`, `BackendSlot.AUDIO`) | ✅ EXISTS — reuse for the voice-send enabled/hidden gate | `EngineManager.kt`, `BackendConstraintTest.kt` |
| `RECORD_AUDIO` permission + runtime request flow + rationale UI | ✅ EXISTS (dictation) — share the gate; do not duplicate | `AndroidManifest.xml:9`, `ChatScreen.kt:279-318`, `ChatViewModel.kt:1668` |
| STT dictation state machine (`VoiceDictationManager`) | ✅ EXISTS — keep separate; share only the permission launcher | `ui/chat/voice/`, `VoiceDictationTest.kt` |
| Playback | ❌ NEW — `MediaPlayer` for draft + history bubbles | — |
| Recorder | ❌ NEW — `MediaRecorder` AAC → cache file, 60 s auto-stop, amplitude for waveform | — |
| Chat history audio rows | ❌ NEW — Room: store audio file path/duration alongside message (image-attachment pattern is the analog) | — |

## Table Stakes (voice messages)

Features users expect. Missing = feels broken vs WhatsApp/Telegram/Gemini.

| Feature | Why Expected | Complexity | Notes |
|---|---|---|---|
| Tap-to-record with live timer + level/waveform feedback | Every voice-note UX has this; silent recording feels broken | Med | `MediaRecorder.maxAmplitude` polling → Compose amplitude bar; full waveform (WhatsApp-style) is polish, not required |
| 60 s hard cap with auto-stop + "60 s max" indicator | Milestone requirement; auto-stop-and-keep is the standard cap behavior | Low | Countdown display last 10 s; keep partial clip, don't discard |
| Playback-before-send draft (play/pause + delete + send) | WhatsApp "Draft Preview" proved users won't send unheard audio | Med | `MediaPlayer` on cache file; draft state in ViewModel, survives rotation |
| Cancel/delete draft discards file | Privacy + storage hygiene | Low | Delete cache file on cancel; also on conversation delete |
| Voice bubble in history (play + duration + progress) | Standard render; replay must work after restart | Med | Persist file path + duration in Room; files live in app-private storage |
| Gated on audio capability (hidden/disabled + reason otherwise) | Sending audio to a text-only model fails cryptically (native encoder error) | Low | Reuse `BackendSlot.AUDIO` resolution; same pattern as vision gating |
| Distinct icon from dictation mic | #1 confusion risk of this milestone | Low | Waveform/audio-file icon in attachment row; mic stays dictation-only |
| Graceful failures: <1 s clip, permission denied, encoder error | Table stakes for audio on fragmented Android hardware | Low-Med | Reuse dictation's rationale/Settings-escape Snackbar pattern |

## Differentiators (voice messages)

| Feature | Value Proposition | Complexity | Notes |
|---|---|---|---|
| Waveform visualization (record + bubble) | WhatsApp-grade feel; biggest perceived-quality lever | Med | Amplitude samples persisted per clip; static bars, no live DSP lib |
| Model-generated transcript caption under own bubble | Accessibility + searchability; remote audio models often return it free | Med | Only where provider returns transcript; never local-STT it (duplicates dictation) |
| Speaker/volume normalization notice | Mobile recordings vary wildly; model hears better with 16 kHz mono | Low | Resample at send time; invisible improvement |

## Anti-Features (voice messages)

| Anti-Feature | Why Avoid | What to Do Instead |
|---|---|---|
| Merging dictation + voice-send into one mic button | Guaranteed confusion; industry keeps them separate | Two affordances, two icons, mic = text |
| Auto-transcribing voice message to text and discarding audio | Destroys the feature's purpose (tone, language, singing, non-speech audio); duplicates v3.0 dictation | Send raw audio; transcript only as optional caption |
| Live two-way voice conversation mode (Gemini Live / Realtime API style) | WebSocket session infra, 60-min session billing, huge scope; milestone is async voice *messages* | Async record→send; realtime is a future milestone |
| ExoPlayer dependency for single-file playback | Extra ~1 MB + API surface for what `MediaPlayer` does | `MediaPlayer`, encapsulated behind a `VoicePlayer` interface |
| Cloud STT fallback for local audio models | Breaks offline-first core value; adds key/vendor surface just removed (Tavily lesson, v3.0) | On-device only; non-audio model → gated-off UI |
| Waveform-perfection (SoundCloud-grade rendering lib) | Third-party audio-UI libs rot fast on Compose; amplitude bars suffice | Hand-rolled bars from `maxAmplitude` samples |

---

## Part 2 — New Tool: candidate evaluation + winner

### Context that constrains the choice (verified)

- Tool infra exists: local `@Tool`/`ToolSet` + `automaticToolCalling` (LiteRT-LM supports it; needs tool-capable model e.g. FunctionGemma-class) and remote `tools[]` loop with capability gating (shipped v2.1/v2.4; v2.2's deletion was reverted by v2.4's agentic loops — confirm current wiring during planning).
- **v2.2 explicitly deleted Calculator / CurrentTime / JsonFormatter because the user found no value.** Candidates in that family (arithmetic, clock, JSON pretty-print) are therefore **disqualified** — do not re-propose them, even though generic "new tool" brainstorms suggest them.
- DDG web grounding already covers "look something up online." The winner must cover ground grounding does NOT.

### Ranked candidates

| # | Candidate | Value proposition | Effort | Fit | Verdict |
|---|---|---|---|---|---|
| 1 | **Local document reader — `readTextFile(path-or-picker)`**: model reads a user-picked `.txt`/`.md` (later: PDF) from device storage into context via SAF picker | Only way to get *on-device private content* into context while offline; complements URL grounding (web) + vision (images) + audio (voice) — completes the "any input" matrix; zero new permissions (SAF), zero deps, offline-first | Low-Med (SAF picker → bounded read with size cap + truncation notice → tool result string) | ★★★★★ | **WINNER — implement** |
| 2 | Unit converter (`convertUnits`) | Genuinely useful on mobile (recipes, travel, DIY); deterministic; offline | Low (pure Kotlin, ~200 lines + tests) | ★★★★☆ | Runner-up; resurrect only if winner blocked |
| 3 | Reminder/timer setter (`setReminder` via AlarmManager/WorkManager) | "Remind me in 20 min" is a classic assistant action; sticky value | Med-High (exact-alarm permission on API 31+, notification permission on 33+, Doze edge cases, Play policy scrutiny) | ★★★☆☆ | Defer — permission + policy surface too big for a one-tool milestone |
| 4 | Clipboard read/write (`readClipboard`/`copyResult`) | Handy ("summarize what I copied") | Low but **security-sensitive** (Android 10+ background-clipboard restrictions; foreground-only; user-trust risk) | ★★★☆☆ | Defer — trust cost exceeds value; paste already works |
| 5 | Date/time (`getCurrentTime`) | Model knows "today" for relative dates | Trivial | ★★☆☆☆ | **Disqualified** — deleted in v2.2 for no value |
| 6 | Calculator (`calculate`) | Arithmetic the model flubs | Trivial | ★★☆☆☆ | **Disqualified** — deleted in v2.2 for no value |
| 7 | Currency converter (live rates) | Travel use case | Med + **network dependency** (breaks offline-first; API key/provenance questions) | ★★☆☆☆ | Reject — network-dependent, grounding-adjacent |

### Winner detail: local document reader

- **Tool shape:** `@Tool(description = "Read a user-selected text file into context")` returning truncated content with a `truncated: true/false` + `totalChars` envelope; hard cap (e.g. 20–50 k chars, inside the model-window-aware budget pattern from v2.3) with a "file too large, first N chars" notice so the model can ask for a narrower pick.
- **UX flow:** user intent ("summarize this doc") → model calls tool with no args → app opens SAF `OpenDocument` picker (`text/*` + `application/pdf` guarded: PDFs either rejected with message or text-extracted later) → chosen file read on `Dispatchers.IO` → content returned as tool result → model answers. Picker-first (never silent filesystem access) = no `READ_EXTERNAL_STORAGE`/`MANAGE_EXTERNAL_STORAGE`, no Play policy review trigger.
- **Dependencies:** tool-loop wiring (local `ToolSet` registration + remote `tools[]` mapping — verify current state in planning, v2.4 shipped both); v2.3's window-budget precedent for the size cap; Room optional (no persistence needed — content is ephemeral context, like grounding blocks).
- **Complexity:** Low-Med. One `ToolSet` class + SAF launcher + bounded reader + ~15 unit tests. No new dependencies, no new permissions, works fully offline.
- **Why it beats unit converter:** converter is a nicer calculator (same family the user already rejected); document reader unlocks an entire input modality the app cannot do today.

## Feature Dependencies (new work)

```
RECORD_AUDIO runtime grant (exists, v3.0) → voice recorder + dictation share it
BackendSlot.AUDIO resolution (exists, v2.5) → voice-send icon gate
Content.AudioBytes send path (exists) → voice message send
MediaRecorder draft file (NEW) → MediaPlayer draft preview (NEW) → send
Room message row + audio file ref (NEW, analog: image attachments) → history bubble playback (NEW)
Tool loop wiring local+remote (verify current, v2.4) → readTextFile ToolSet (NEW) → SAF picker (NEW)
```

## MVP Recommendation (v3.1 scope)

**Voice messages — build in this order:**
1. Recorder + 60 s cap + timer/amplitude UI (table stakes core)
2. Draft preview (playback + send/cancel) (table stakes core)
3. Audio-gated voice-send icon, visually distinct from mic (milestone's explicit requirement; cheap)
4. History voice bubbles with replay (table stakes render)
5. Failure handling + permission sharing with dictation

**Defer:** full waveform art, transcript captions, resampling polish, realtime voice mode.

**New tool — build:** local document reader (`readTextFile` via SAF). Defer: unit converter (fallback), reminders, clipboard.

## Sources

- LiteRT-LM Android docs (HIGH): `Content.AudioBytes`/`AudioFile`, `audioBackend` in `EngineConfig`, audio only on multimodal models (Gemma 3n), `@Tool`/`ToolSet`/`automaticToolCalling` — fetched 2026-10-02 via WebFetch (https://developers.google.com/edge/litert-lm/android).
- WhatsApp voice-message features — waveform visualization + draft preview (MEDIUM, official Meta announcement via search).
- Sendbird/TalkJS voice-message SDK patterns — record → preview → cancel/send (MEDIUM, vendor docs via search).
- Gemini Android voice-input redesign toward messaging-app audio style, 9to5Google 2026-03 (MEDIUM, press via search).
- Android `MediaRecorder` overview — official docs (HIGH, platform API, stable for years).
- Repo verification (HIGH): `LiteRTLmProvider.kt:289`, `EngineManager.kt` `BackendSlot.AUDIO`, `AndroidManifest.xml:9`, `ChatScreen.kt:279-318`, `ChatViewModel.kt:1668`, `VoiceDictationTest.kt`.
- v2.2 Calculator/CurrentTime/JsonFormatter deletion for no user value (HIGH, PROJECT.md Key Decisions + v2.2 milestone notes).
