# Phase 68: Voice Draft + Playback History — Research

**Date:** 2026-10-02
**Scope:** VMSG-02 (draft preview + sub-1s reject), VMSG-06 (history playback bubbles)
**Builds on:** Phase 67 (recorder, filesDir/voice clips, sendVoiceMessage → sendMessage(audioBytes))

## 1. Message-list rendering pipeline + bubble chrome

- `ChatScreen.kt` renders transcript messages in a keyed `LazyColumn` (`rememberLazyListState`, isAtBottom + Jump-to-latest pill per 48-UI-SPEC §3). Each row → `MessageBubble(message, ...)` in `ui/chat/components/MessageBubble.kt`.
- `MessageBubble` chrome: outer `Column` (fillMaxWidth, alignment End for user) → `Surface` (user: `Color(0xFF121212)`, 12dp rounded, `widthIn(max=340dp)`) → inner `Column` (12dp horizontal / 10dp vertical padding for user). Content branches: `MessageImageStack` for user images, then `message.content` text (user: white bodyMedium; assistant: MarkdownText).
- Voice extension point: inside the user-bubble `Column`, after `MessageImageStack` and alongside/before the content `Text`. Caption text renders as the normal content `Text` — the player row slots above or below it without re-layout (Phase 69 transcript captions slot underneath the same way).
- `ChatMessage` (domain) currently has NO voice fields — `imageUris`, `groundedSources`, `groundedSourceDetails`, `groundedImages` are all ephemeral (never persisted; EntityMappers maps field-by-field). Voice needs the opposite: persisted path + duration on the entity, hydrated into domain on history load.
- ChatScreen collects per-region sub-state flows (`transcript`, `input`, `connection`, voice flows) via `collectAsStateWithLifecycle` — new playback flows follow the same pattern (never the monolith).
- Phase 67 recording row lives in `ChatInputBar.kt` (inline replacement of the text field while recording: mm:ss timer Label 14sp + LinearProgressIndicator amplitude + 48dp cancel X + stop toggle; red at ≥50s; TalkBack stateDescription throttled to 5s buckets). The draft card reuses these patterns in the `bottomBar` slot above `ChatInputBar`.

## 2. Room schema + migration conventions

- `AppDatabase` version **17**, `exportSchema = true`. `MessageEntity` (table `messages`): id, conversation_id, role, content, token_count, created_at, images (TEXT JSON, MIGRATION_4_5), stats (MIGRATION_5_6), reasoning (MIGRATION_9_10).
- Migration convention: one `Migration(N, N+1)` object per version in `Migrations.kt` (nullable `ALTER TABLE ... ADD COLUMN`, no index/backfill unless queried — cf. MIGRATION_15_16/16_17 OG/snippet columns), registered in `di/DatabaseModule.kt` `.addMigrations(...)` chain (currently ends `MIGRATION_16_17`), `fallbackToDestructiveMigration(false)`.
- Phase 68 migration: **MIGRATION_17_18** adds `audio_path TEXT` (nullable; NULL = non-voice message), `audio_duration_ms INTEGER NOT NULL DEFAULT 0`, `transcript TEXT` (nullable placeholder for Phase 69 VMSG-07 — NULL = no transcript yet; never rendered in Phase 68).
- Mapper precedent: `EntityMappers.toDomain()/toEntity()` map field-by-field; untrusted stored text degrades safely (`toRoleSafe`, `toGroundedSourceStatusSafe` — unknown → safe default, never crash). Voice mapping follows: blank/missing path → no player row; duration 0 → `0:00` readout.
- Critical gap from Phase 67: `sendVoiceMessage(caption)` transcodes and calls `sendMessage(caption, audioBytes)` which builds a plain `ChatMessage(role=USER, content=caption)` — the clip path/duration are NEVER persisted. Phase 68 must persist at send time: the VM knows `voiceClipFile` + duration (elapsed sec or MediaMetadataRetriever), so it stamps the user message with `audioPath`/`audioDurationMs` before `saveMessage`. Duration source: `MediaMetadataRetriever.METADATA_KEY_DURATION` on Dispatchers.IO at stop time (accurate ms), NOT the 1s ticker (coarse).
- History load: `chatRepository.saveMessage` / `messageDao.insert` (REPLACE); history hydrated via `toDomain()` on load — voice fields flow through the same path. No new DAO needed (columns on existing table).
- `ChatMessage.id` is a UUID string unrelated to the DB row id (`toEntity` drops it) — voice bubbles key playback state on domain id in VM memory only, never persisted.

## 3. MediaPlayer lifecycle / focus patterns in-repo

- No MediaPlayer / AudioFocus usage exists in-repo (grep: zero matches) — Phase 68 is the first playback code. Platform `android.media.MediaPlayer` only (zero new Gradle deps; no Media3/ExoPlayer per research contract).
- Established lifecycle pattern to mirror: `VoiceMessageRecorder` + `VoiceDictationManager` are VM-owned, created lazily (`getVoiceRecorder()` with `...Override` test seam), destroyed in `ChatViewModel.onCleared()` (cancel jobs → destroy → null). `VoiceMessagePlayer` follows exactly: lazy holder + `PlayerFactory` seam for JVM tests, `destroy()` (stop+release, idempotent) called in `onCleared` alongside the recorder destroy.
- Recorder threading discipline: platform calls on Dispatchers.IO (start) / Default (amplitude sampler binder IPC never on Main); StateFlow updates cheap on Main. Player: `prepare()`/`start()` are fast for local files but `setDataSource` + `prepare()` go on Dispatchers.IO; progress polling (~250ms) on Default; completion callback posts to Main via StateFlow.
- Single-player discipline: one `VoiceMessagePlayer` instance per VM; `play(path)` stops/releases any current clip first (mirrors single-flight inference cancel + recorder double-start guard). Playback state in StateFlows: `playingPath: String?`, `playingPositionMs: Int`, `isPlaying: Boolean`, `draftPlaying: Boolean + draftPositionMs`.
- Audio focus: `AudioManager.requestAudioFocus` transient (`AUDIOFOCUS_GAIN_TRANSIENT`) before start; `OnAudioFocusChangeListener` pauses on LOSS/TRANSIENT loss (calls, other-app audio); abandon on stop/destroy. Chat exit stops playback (ChatScreen `DisposableEffect` or VM `stopPlayback()` on screen leave — onCleared covers VM death; explicit stop covers navigation).
- Background: existing `ON_PAUSE → autoStopVoiceRecording(announceCap=false)` observer in ChatScreen extends to `pausePlayback()` (pause, keep position — no foreground service). Rotation: VM survives; playback position resets to 0 (CONTEXT decision) — implement as pause + seekTo(0) on... simplest honest form: position StateFlow resets because progress polling job is cancelled and position state is NOT in rememberSaveable; draft clip path IS in VM (survives rotation by construction).
- Sub-1s reject: guard at the single stop-and-keep choke point (`keepAndStopVoice()`): retrieve duration via MediaMetadataRetriever on IO; if <1000ms → delete file, emit one-shot Snackbar event ("Recording too short"), never set `hasVoiceClip`. Applies to manual stop + 60s auto-stop + background auto-stop uniformly.
- Delete: `deleteVoiceDraft()` deletes `voiceClipFile` immediately + clears state (Phase 67 cancel-discards precedent, no undo). Own-bubble delete (if exposed) deletes the file too — scope to draft delete + keep bubble-delete out unless trivial (Phase 67 has `deleteMessage`; file cleanup hooks there if cheap).
- Missing file: bubble checks `File(audioPath).exists()` at render/collect time → "Voice clip unavailable" row (info icon + onSurfaceVariant text, no play button). Never crash, never silent drop.

## 4. Risks / unknowns

- MediaMetadataRetriever on a just-stopped AAC file: fast (<100ms) but must run off Main; corrupt file → exception → treat as reject-with-Snackbar (same channel as transcode failure).
- MediaPlayer completion/error callbacks fire on internal threads — route through StateFlow (thread-safe) not direct Compose state.
- Emulator has no microphone — same device-smoke deferral as Phase 67 (release-UAT runbook); unit tests use fake PlayerFactory + in-memory Room; full suite must stay green.
- Duration readout convention (total vs elapsed/remaining) is executor's discretion per UI-SPEC — must pick ONE fixed convention.
