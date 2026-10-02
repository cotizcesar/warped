# Architecture Patterns: v3.1 Voice Messages + New Tool

**Domain:** In-chat voice-message send (60s cap) + one new agentic tool, on the existing Warped tree
**Researched:** 2026-10-02
**Overall confidence:** HIGH (all integration points verified in-tree; only LiteRT-LM audio-byte format is doc-sourced)

## 1. What Already Exists (Do NOT Rebuild)

The audio-bytes inference path is **fully plumbed end-to-end**. The v3.1 voice feature is a capture + persistence + UI task, not an inference task.

| Existing piece | Location | Status |
|---|---|---|
| `ChatRequest.audioBytes: ByteArray?` | `domain/model/ChatRequest.kt:7` | Ships today; carried on the request, ignored by history |
| `sendMessage(text, images, audioBytes)` | `ui/chat/ChatViewModel.kt:462` | Accepts audio; media gate at lines 876–886 (`error_no_audio` when `!capabilities.audio`) |
| `Content.AudioBytes(audioBytes)` attach | `data/local/inference/LiteRTLmProvider.kt:281–305` | Step 4: audio + images + optional text in one `Contents.of()`; empty-text part suppressed (engine rejects it) |
| Audio backend slot | `data/local/inference/EngineManager.kt:206–211` | `resolveAudioBackend()` probes CPU for allowlist `audio == true` models; slot-aware retry on constraint mismatch |
| Allowlist-verified audio flag | `ModelAllowlistRepository.effectiveCapabilities()` + `ChatViewModel.verifiedLocalCapabilities()` | **The only capability source.** Never `LocalModel.capabilities` (lazy all-true default) |
| Input-bar audio props | `ui/chat/components/ChatInputBar.kt:54–56` | `modelHasAudio`, `onAudioRecorded: ((ByteArray) -> Unit)?`, `onAudioRecordingChanged: ((Boolean) -> Unit)?` — **dead props, no caller wires them**. The recorder UI is the gap |
| Attachment pre-search skip | `ChatViewModel.kt:705` | Audio turns skip the heuristic DDG pre-search (deliberate, anti-junk-context). Untouched by v3.1 |
| STT dictation | `ui/chat/voice/VoiceDictationManager.kt` | `SpeechRecognizer` wrapper, VM-owned, destroyed in `onCleared`, `RECORD_AUDIO` permission flow exists (Phase 65). Must stay **separate** from voice messages |
| Tool pattern | `data/agentic/WebSearchToolSet.kt`, `WebFetchToolSet.kt` | Schema-only `@Tool` bodies (`HOST_EXECUTED`), manual-loop execution, provider-neutral snake-case names |
| Tool execution | `LiteRTLmProvider.runToolLoop` + `executeToolCallDetailed` (local); `CompatToolLoop` + `defaultRemoteTools()` (remote) | New tool plugs into both dispatch sites |
| Room precedent for media | `messages.images TEXT` (`MIGRATION_4_5`) + `EntityMappers` JSON list | Exact template for the audio column (nullable TEXT, no backfill, fail-soft decode) |

**Remote providers ignore audio today.** Grep over `data/remote/provider/` finds zero `audioBytes` handling — voice send is local-only at launch unless a phase explicitly maps audio onto OpenAI audio-input format (out of scope recommendation, see §6).

## 2. Recommended Architecture

### 2.1 Component map (new vs modified)

```
ui/chat/voice/
├── VoiceDictationManager.kt      [EXISTS — untouched, STT into draft]
├── VoiceMessageRecorder.kt       [NEW — MediaRecorder wrapper, 60s cap]
└── VoiceMessagePlayer.kt         [NEW — MediaPlayer wrapper, bubble playback]

ui/chat/
├── ChatViewModel.kt              [MODIFY — own recorder/player, recording state, send path]
├── ChatInputState.kt             [MODIFY — isRecording, recordingSecs, audioDraft fields]
└── components/
    ├── ChatInputBar.kt           [MODIFY — wire dead audio props + new voice-send icon]
    └── VoiceMessageBubble.kt     [NEW — playback row inside user bubbles with audio]

domain/model/
└── ChatMessage.kt                [MODIFY — add audioPath: String? persisted field]

data/local/db/
├── entity/MessageEntity.kt       [MODIFY — audio_path TEXT nullable]
├── entity/EntityMappers.kt       [MODIFY — map audioPath both directions, fail-soft]
└── Migrations.kt                 [MODIFY — MIGRATION_17_18, ALTER-only shape]
                                   (DB is at v17; verify in AppDatabase.kt at plan time)

data/agentic/
└── <New>ToolSet.kt               [NEW — schema-only @Tool, sibling of WebFetchToolSet]
data/local/inference/
└── LiteRTLmProvider.kt           [MODIFY — register tool in armed ConversationConfig]
data/agentic/ or remote loop
├── LocalToolLoop.kt              [MODIFY — validateArgs/mapToolCallName/dispatch entry]
└── CompatToolLoop.kt + defaultRemoteTools() [MODIFY — OpenAI tools[] mapping for remote]

app cache/files:
└── filesDir/voice/<messageId>.m4a [NEW — audio file store, Room holds path only]
```

### 2.2 Layer placement rationale (domain vs data vs ui)

| Component | Layer | Why |
|---|---|---|
| `VoiceMessageRecorder` | **ui/chat/voice** (not data) | Platform capture API, same category as `VoiceDictationManager`. VM-owned lifecycle (lazy-create, destroy in `onCleared`), same `dictationManagerOverride`-style test seam. Putting it in `data/` would imply repository semantics it doesn't have; putting it behind Hilt `@Singleton` would leak recorder/context across screens (Phase 65 precedent: owner-managed, never singleton) |
| `VoiceMessagePlayer` | **ui/chat/voice** | Same reasoning. `MediaPlayer` is a UI-clocked resource (seekbar, completion callback); one instance per ChatViewModel, released on `onCleared` and on new playback start (single-flight, mirrors `generationJob` single-collector precedent) |
| `audioPath` on `ChatMessage` | **domain/model** | Follows `imageUris` precedent: persisted render data, mapped field-by-field in `EntityMappers` |
| `audio_path` column + migration | **data/local/db** | Only data-layer change for voice. Audio bytes themselves NEVER enter Room (see §3.2) |
| New `ToolSet` schema | **data/agentic** | Fixed two-tool allowlist becomes three; schema-only body (`HOST_EXECUTED`, never `runBlocking` — Pitfall 3 from `WebFetchToolSet` kdoc) |
| Tool execution bodies | `LocalToolLoop` + `CompatToolLoop` | Existing dispatch sites; no new loop machinery |
| Zero changes | `LlmModelHelper` interface, `LiteRtLlmHelper`, `EngineManager`, `ChatRequest`, grounding pipeline | The inference contract already carries audio; the interface needs no new method |

### 2.3 Data flow — voice send turn

```
[1] User taps voice-send icon (visible iff modelHasAudio && !inputLocked)
        → ChatInputBar shows recording sheet: timer, 60s progress, stop/send/cancel
[2] VoiceMessageRecorder.start(cacheFile) — MediaRecorder, setMaxDuration(60_000),
    auto-stop → onMaxDuration callback finalizes file
        → VM: _input.isRecording=true, recordingSecs ticker (1s granularity is fine)
[3] Stop → file finalized → playback preview (VoiceMessagePlayer, local file)
        → Send: bytes = file.readBytes() (Dispatchers.IO) → sendMessage(text="", audioBytes=bytes)
        → Cancel: delete cache file, clear draft
[4] sendMessage: existing path unchanged —
    media gate (audio flag) → ensureConversation(hasMedia=true) → saveMessage →
    ChatRequest(audioBytes) → LiteRTLmProvider Step 4 → Content.AudioBytes
[5] Done: persist audio file filesDir/voice/<userMsgId>.m4a,
    update row audio_path; transcript renders VoiceMessageBubble (play/pause + duration)
```

Key invariant: **audio bytes are single-turn, never history-carried.** `buildHistoryMessages` carries images (K=3 newest) but has no audio carry — follow-up turns do not resend voice bytes. This is deliberate (bytes are large, voice is a one-shot utterance) and matches the existing `audioBytes` transient contract. Document it; do not "fix" it.

### 2.4 Data flow — new tool turn (unchanged loop, new schema)

```
Armed ConversationConfig.tools += tool(<New>ToolSet())   // local
defaultRemoteTools() += <new> OpenAiTool mapping          // remote
Model emits toolCall → LocalToolLoop.validateArgs → executeToolCallDetailed
  → ToolCompleted(sources?) → VM unions into loopSourceDetails (Fuentes rows iff sources)
  → Content.ToolResponse back into loop
```

If the new tool returns no citable sources, emit no `ToolCompleted` rows (IN-02 precedent: no transient row for sourceless calls) and return the mapped string only.

## 3. Patterns to Follow

### 3.1 Recorder wrapper mirrors VoiceDictationManager (HIGH confidence — in-tree precedent)

Thin platform wrapper, callbacks only, zero UI side effects; the ViewModel applies all policy (cap, gating, file lifecycle). `start(): Boolean`-style synchronous-accept signal (WR-01 lesson: never flip `isRecording=true` unless the platform accepted). `stop()` keeps the instance for re-record; `destroy()` in `onCleared`.

### 3.2 Files for bytes, Room for paths (HIGH — images precedent + size math)

60s AAC/M4A ≈ 0.5–1 MB. Base64-ing that into a TEXT column would bloat the `messages` table and break the `conversation_id, created_at` index locality. Store under `filesDir/voice/` (app-private, survives restart, no `READ_MEDIA` permission needed for own files), persist only the path. Cap the directory (count or MB, LRU by message `created_at`; precedent: `LiteRtLmCacheManager` capped cache) and delete-on-message-delete (check `ChatRepository.deleteMessage` path at plan time).

### 3.3 Migration shape: ALTER-only nullable, no backfill (HIGH — MIGRATION_4_5/14_15/15_16 precedent)

```kotlin
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN audio_path TEXT")
    }
}
```

NULL = no voice attached (all pre-v3.1 rows). No index (no query filters on the column), no backfill. Mapper decodes fail-soft (`try/catch → null`), mirroring the `images` JSON decode guard. Verify current DB version in `AppDatabase.kt` before freezing numbers — migrations chain is manual (no AutoMigration; KSP schema caveat documented in `Migrations.kt:62-65`).

### 3.4 Audio format: MediaRecorder M4A/AAC + engine-side bytes (MEDIUM)

LiteRT-LM's public contract is `Content.AudioBytes(audioBytes: ByteArray)` (Google AI Edge docs: "ByteArray of the audio"; verified via webfetch of the LiteRT-LM Android guide). No published sample-rate/format constraint was found in the fetched docs — **flag as a phase-time spike**: record AAC, confirm on-device with an audio-capable allowlist model (`gemma-4-E2B-it` class), and if the engine rejects it, transcode to 16 kHz mono PCM via `AudioRecord` (TensorAudio-compatible shape) before `sendMessage`. Keep the transcode path behind a pure function so it's unit-testable without the engine. LOW-confidence detail, HIGH-confidence fallback plan.

### 3.5 Icon differentiation (HIGH — existing ChatInputBar layout precedent)

- Dictation mic (`Icons.Filled.Mic` → `Stop` while listening) stays exactly where it is, gated by `speechAvailable`.
- Voice-send is a **separate affordance** with a non-mic icon (recommend `Audiotrack` — already the codebase's audio-capability badge icon in `CapabilityBadges.kt`/`ModelCard.kt`, so users learn one audio glyph; alternatives `MicExternalOn`/`KeyboardVoice` are untested in this tree). Gated by `modelHasAudio` (allowlist-verified, same fail-open-while-loading rule as `supportsThinkingFor`), hidden while `inputLocked` (generating/loading) so two stop icons never coexist (Phase 65 no-two-stops precedent).
- `hasContent` for send-enabling must include the audio draft (`text.isNotBlank() || images || audioDraft != null`) — the current Enter-key and keyboard-send checks (`ChatInputBar.kt:153,164`) only test text+images and need the same extension.

## 4. Anti-Patterns to Avoid

| Anti-pattern | Why bad | Instead |
|---|---|---|
| Extending `LlmModelHelper` with audio methods | Interface is the local/remote keystone; audio already rides `ChatRequest`. A new method forks every helper + `ProviderRouter` + tests for zero gain | Reuse `ChatRequest.audioBytes` untouched |
| Storing audio bytes/base64 in Room | 0.5–1 MB blobs in `messages` TEXT; history load pays it on every open; no precedent (images are KB-scale data URLs) | `filesDir/voice/` + `audio_path` column |
| Hilt `@Singleton` recorder/player | Recorder holds native handles + context; singleton leaks across screens and survives model switch (EngineManager-unload analogue) | VM-owned, `destroy()/release()` in `onCleared`, single-flight playback |
| Merging dictation + voice-send into one button/mode | Different destinations (draft text vs model audio attachment), different gating (recognizer-available vs audio-capable model), Phase 65 single-insertion state machine assumes mic exclusivity | Two icons, mutually exclusive sessions: starting one stops the other (`sendMessage` already calls `stopDictation()` — mirror it: recorder start calls `stopDictation()`, dictation start stops recording) |
| Carrying audio in `buildHistoryMessages` | Re-sends MB-scale bytes every follow-up turn; context-window eviction + OOM (the exact reason image carry is capped at K=3) | Single-turn only; history shows the playable bubble via `audio_path` |
| New tool with `runBlocking` body or auto-executing SDK path | `ReflectionTool.execute` is synchronous; network I/O there blocks engine threads with no Stop propagation (documented in `WebSearchToolSet` + `LocalToolLoop` kdocs) | Schema-only `@Tool` body + manual-loop suspend execution (`automaticToolCalling=false`), Stop-safe via `ensureActive()` per round |
| Remote voice-send without mapping | Remote providers drop `audioBytes` silently today → user records 60s and the model never hears it | Gate voice-send on `LITE_RT_LM && audio-capable` at launch (local-only); remote audio is an explicit future phase, not a silent gap |

## 5. Scalability / Resource Considerations

| Concern | Approach |
|---|---|
| 60s cap enforcement | `MediaRecorder.setMaxDuration(60_000)` (platform-enforced, survives Duration-ticker drift) + UI progress bar as the soft signal; auto-stop routes through the same finalize path as manual stop |
| Recorder leak on rotation/process death | VM survives rotation (collection lives in VM — 46-01 precedent); on `onCleared` release recorder + player; orphaned cache files swept at next chat open (single `cacheDir/voice_tmp` wipe of files with no matching `audio_path` row — one-shot, `Dispatchers.IO`) |
| Playback concurrency | One `MediaPlayer` per VM; starting bubble B stops bubble A (single-flight, same stale-guard spirit as `generationSeq`); completion releases the handle but keeps the file |
| Voice dir growth | LRU cap (e.g. 50 files / 100 MB); eviction deletes file + nulls `audio_path` (bubble degrades to duration label, never crashes — fail-soft mapper precedent) |
| New-tool loop budget | Existing 5-call cap (`LocalToolLoop.MAX_TOOL_CALLS`) covers the new tool with no change; cap-reached string path reused verbatim |

## 6. Suggested Build Order (dependency-respecting)

1. **Room foundation** — `audio_path` column + `MIGRATION_17_18` + `EntityMappers` + `ChatMessage.audioPath`, with a JVM migration test (static gate precedent: `MIGRATION_14_15` had a JVM static gate). Unblocks everything below; zero UI.
2. **Recorder + player + VM state** — `VoiceMessageRecorder`, `VoiceMessagePlayer`, `ChatInputState` recording fields, VM `startRecording/stopRecording/sendVoiceMessage/cancelRecording`, `RECORD_AUDIO` permission reuse from Phase 65, 60s cap, file→`filesDir/voice/` persist on send. Unit-testable policy (cap, gating, mutual exclusion with dictation) with a fake recorder seam.
3. **Input-bar + bubble UI** — wire the dead `modelHasAudio`/`onAudioRecorded` props, new voice-send icon, recording sheet (timer/progress/send/cancel), `hasContent` extension, `VoiceMessageBubble` playback row; TalkBack `stateDescription` parity with the dictation mic (Phase 65 UI-review precedent).
4. **Device spike: audio format** — record → send → confirm on an audio-capable allowlist model; transcode fallback only if the engine rejects AAC (§3.4). Must precede release-UAT but not the UI above (bytes flow through the same `audioBytes` slot either way).
5. **New tool schema + local execution** — `<New>ToolSet` + `LocalToolLoop` dispatch + armed-config registration + unit tests (validateArgs short-circuits, cap accounting). Independent of 1–4; can parallelize after the tool is chosen.
6. **New tool remote mapping** — `defaultRemoteTools()` entry + `CompatToolLoop` handling + capability-matrix check (`ToolCapabilityMatrix`). Last: needs the local shape frozen first (Phase 57 precedent: local 1:1 → remote tools[] entry).

## Sources

- In-tree (HIGH): `LiteRTLmProvider.kt` Step 4 audio attach (lines 281–305); `ChatViewModel.sendMessage` + audio gate (lines 462–488, 876–886); `EngineManager.resolveAudioBackend` (lines 206–211); `ChatInputBar` dead audio props (lines 54–56) + mic block (lines 224–248); `VoiceDictationManager` contract; `EntityMappers` images precedent; `Migrations.kt` v4→v17 chain; `WebFetchToolSet` schema-only kdoc.
- Official docs (MEDIUM): LiteRT-LM Android guide (`developers.google.com/edge/litert-lm/android`) — `Content.AudioBytes(audioBytes)` ByteArray contract confirmed via WebFetch; no sample-rate/format constraint found in fetched content → §3.4 spike flag (LOW confidence on format details, HIGH on fallback plan).
- Not verified at research time (plan-time checks): current `AppDatabase` version number (=17 assumed from migration chain); whether `RECORD_AUDIO` permission declaration already covers `MediaRecorder` (same permission, but manifest entry must be confirmed); Media3/ExoPlayer absence (recommendation is zero-dep `MediaPlayer` regardless); `deleteMessage` file-cleanup hook point.
