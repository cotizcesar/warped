---
phase: 68-voice-draft-playback-history
reviewed: 2026-10-02T00:00:00Z
depth: standard
files_reviewed: 13
files_reviewed_list:
  - app/src/main/java/com/warped/ui/chat/voice/VoiceMessagePlayer.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
  - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
  - app/src/main/java/com/warped/data/local/db/Migrations.kt
  - app/src/main/java/com/warped/data/local/db/AppDatabase.kt
  - app/src/main/java/com/warped/data/local/db/entity/MessageEntity.kt
  - app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt
  - app/src/main/java/com/warped/domain/model/ChatMessage.kt
  - app/src/main/java/com/warped/di/DatabaseModule.kt
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values-es/strings.xml
findings:
  critical: 1
  warning: 6
  info: 6
  total: 13
status: issues_found
---

# Phase 68: Code Review Report

**Reviewed:** 2026-10-02T00:00:00Z
**Depth:** standard
**Files Reviewed:** 13
**Status:** issues_found

## Summary

Reviewed the full Phase 68 scope (plans 01–03: draft preview player, history persistence + playback, bubble UI) against the required focus areas. Migration 17→18 is correctly registered (`Migrations.kt:165`, `DatabaseModule.kt:61`, `AppDatabase.kt:33` v18), EN/ES string parity holds (21/21 `voice_msg_*` keys), no new permissions/components were added, and the single-player stop-then-play discipline is correctly implemented within each play path. The defects below are all cross-path and async-boundary issues the per-plan tests do not cover: a user-visible incorrect progress state (CR-01), missing mutual exclusion between the draft and history play paths, two IO-thread races around send/stop-then-re-record, completion-before-flag stuck state, transcript-keyed file cleanup that orphans clips, and a rotation behavior that contradicts the CONTEXT decision. No hardcoded secrets, injection vectors, or new attack surface found; path handling is existence-checked but unconfinded (IN-level hardening only).

## Critical Issues

### CR-01: Paused history bubble resets progress to 0 while draft card keeps it

**File:** `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt:758-765`
**Issue:** `VoicePlayerRow` computes progress as `if (isPlaying && durationMs > 0) ... else 0f`, so a paused mid-clip bubble renders an empty track — visually "unplayed" — even though `pauseHistoryVoice()` deliberately keeps the position for one-tap resume. The draft card (`ChatInputBar.kt:452-459`) uses position regardless of playing state, so the two surfaces disagree on the locked "position shows as progress fill" convention. Repro: play a history clip, pause at 0:10/0:30 — track snaps to 0, resume continues from 0:10. Incorrect, user-visible state on the primary VMSG-06 surface.
**Fix:**
```kotlin
progress = {
    if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
},
```

## Warnings

### WR-01: No mutual exclusion between draft-play and history-play on the shared player

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:2174-2202, 2318-2352`
**Issue:** `draftPlayStarting` and `historyPlayStarting` are independent per-path debounces guarding one shared `VoiceMessagePlayer`. Near-simultaneous `playVoiceDraft()` + `playHistoryVoice()` both pass their own gate, both run stop-then-play on the same instance, and both set their own flag (`_isDraftPlaying=true` AND `_isHistoryPlaying=true`) with both poll jobs running — while the player holds only the last-played clip. UI shows two playing surfaces for one audible clip; completion clears both, masking the desync. Single-player discipline holds for the audio but not for the state.
**Fix:**
```kotlin
// Single shared in-flight gate across both paths, e.g.:
@Volatile
private var anyPlayStarting = false
// checked-and-set at the top of playVoiceDraft() and playHistoryVoice(),
// cleared in each finally. And on success, clear the OTHER path's
// playing flag + poll job (stopPlaybackInternal already does this — call
// it after a successful foreign play, not just before).
```

### WR-02: Transcode-failure restore clobbers a newer recording made during send

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:2139-2166`
**Issue:** `sendVoiceMessage()` clears `voiceClipFile=null` up front, then the IO transcode runs. The input bar immediately offers record again, so the user can capture clip B while clip A's transcode is in flight. On failure the catch path unconditionally restores `voiceClipFile = file` (clip A) — silently replacing the live clip B (whose file is now orphaned on disk and whose session state/duration is wrong). Stale-file-send follows if the user then taps send.
**Fix:**
```kotlin
} catch (e: Exception) {
    Timber.w(e, "VoiceMsg: transcode failed")
    // Only restore when no newer clip took the slot mid-flight.
    if (voiceClipFile == null) {
        voiceClipFile = file
        _hasVoiceClip.value = true
        _draftDurationMs.value = lastSentVoiceDurationMs
    } else {
        try { if (file.exists()) file.delete() } catch (_: Exception) {}
        lastSentVoicePath = null
        lastSentVoiceDurationMs = 0L
    }
    ...
}
```

### WR-03: Stop-then-immediately-record race lets the sub-1s validation overwrite the new session

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:2027-2058`
**Issue:** `keepClipAfterDurationCheck()` validates duration on `Dispatchers.IO` and writes `voiceClipFile` / `_draftDurationMs` / `_hasVoiceClip` on completion. Nothing prevents `startVoiceRecording()` from opening a new session in that window; when clip A's validation lands after clip B's session started, it overwrites B's slot (or resurrects A's card mid-recording) and orphans one file on disk. The choke point serializes stops but not stop-vs-next-start.
**Fix:** Tag each validation with a session token (e.g. increment a `voiceSessionId` on every `startVoiceRecording`/stop) and ignore the IO result when the token has moved on; or disable the voice-record affordance until `_hasVoiceClip` settles. At minimum, only apply the keep when `voiceClipFile` is still unset/null-for-that-stop.

### WR-04: Completion/error arriving before the playing flag is set wedges the card in "playing"

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:1890-1911, 2195-2197, 2344-2347`
**Issue:** Order in both play paths is `player.play()` → set `_isDraftPlaying/_isHistoryPlaying = true` → `startDraftPoll/startHistoryPoll`. `onCompletion`/`onError` fire on player-internal threads and unconditionally clear the flags and cancel the (not yet created) poll jobs. If either fires in that microsecond window (corrupt clip error, instantaneous completion), the subsequent lines re-arm `_isPlaying=true` and start a poll loop over a dead player that nothing will ever clear — perpetual "playing" affordance with a static bar and a leaked 250 ms poll job until the next explicit stop.
**Fix:**
```kotlin
if (!started) return@launch
// Re-check liveness after arming: the player may have completed/errored
// between play() returning and this line.
if (!player.isPlaying) {
    _isDraftPlaying.value = false
    return@launch
}
_isDraftPlaying.value = true
startDraftPoll(player)
// (mirror for the history path; or make start*Poll itself bail when
// !player.isPlaying && position == 0)
```

### WR-05: Message-delete file cleanup is keyed to in-memory transcript, orphans clips otherwise

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:2665-2684`
**Issue:** `deleteMessage()` resolves `audioPath` solely from `_transcript.value.messages`. Any delete issued for a row not currently in the transcript (stale id, cross-conversation delete, reload timing) skips the file delete while the DB row disappears — the clip leaks permanently in `filesDir/voice`. There is also no orphan reaper/vacuum anywhere, and a failed file delete after a successful DB delete is swallowed with the same leak. Storage accumulates silently on a mobile device.
**Fix:**
```kotlin
// Resolve the path from the repository (source of truth), not the
// in-memory transcript, e.g. chatRepository.getMessageAudioPath(id),
// and/or add a startup vacuum that deletes filesDir/voice/* files
// referenced by no messages row.
```

### WR-06: Rotation clears which history bubble was playing, contradicting CONTEXT

**File:** `app/src/main/java/com/warped/ui/chat/ChatScreen.kt:238-239`, `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:2284-2288`
**Issue:** CONTEXT decides "rotation during playback pauses and keeps bubble state; user resumes with one tap." The implementation calls `stopPlayback()` on config-change pause, which nulls `_playingMessageId` and zeroes the position — no paused indication survives, so the user must first find the bubble again and restarts from 0 rather than resuming. The VM comment ("paused-at-0 by construction") describes a state the code never produces; `playingMessageId == null` is indistinguishable from never-played. Either the decision or the implementation is wrong; as shipped, the kept-position resume contract holds only for backgrounding.
**Fix:** On the rotation branch, pause (keep `_playingMessageId` + position) instead of stop, and release/re-create the player handle across recreation — or amend CONTEXT + summary to state the honest contract (rotation restarts the bubble from 0 and drops selection).

## Info

### IN-01: `seekTo()` is dead public API

**File:** `app/src/main/java/com/warped/ui/chat/voice/VoiceMessagePlayer.kt:207`
**Issue:** `seekTo()` has no callers (scrubbing was explicitly declined in CONTEXT). Unused public surface on a thin wrapper invites misuse and suggests capability that does not exist.
**Fix:** Remove `seekTo()` (and its `PlayerHandle.seekTo` seam) until a scrubbing phase needs it.

### IN-02: `_historyDurationMs` flow is written but never collected

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:2299-2300, 2346`
**Issue:** `historyDurationMs` is set on every history play but `ChatScreen` never collects it — per-row duration comes from `message.audioDurationMs`. Dead state that misleads the next reader into wiring a redundant source.
**Fix:** Delete the flow, or wire the bubble to it and document which source is canonical.

### IN-03: `currentPath` lacks the visibility guarantees `isPlaying` has

**File:** `app/src/main/java/com/warped/ui/chat/voice/VoiceMessagePlayer.kt:83-98`
**Issue:** `isPlaying` is `@Volatile` but `currentPath` (read from VM IO coroutines, written under `@Synchronized`) is a plain var — same-path resume decisions can observe a stale path across threads.
**Fix:** Mark `currentPath` `@Volatile` (or expose a `@Synchronized` accessor).

### IN-04: Voice-only draft is not sendable from the main send affordances

**File:** `app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt:246, 372`
**Issue:** Enter-key send and the row-2 send button compute `hasContent = text.isNotBlank() || attachedImages.isNotEmpty()` — a kept voice clip with an empty caption counts as nothing, so both stays disabled/dead while the draft-card send works. Users must discover the card-local send for the captionless case.
**Fix:** Thread `hasVoiceClip` into both `hasContent` computations (`text.isNotBlank() || attachedImages.isNotEmpty() || hasVoiceClip`).

### IN-05: `File.exists()` runs on the composition thread on first composition per path

**File:** `app/src/main/java/com/warped/ui/chat/ChatScreen.kt:671-673`
**Issue:** `remember(message.audioPath) { viewModel.hasVoiceFile(...) }` memoizes per path but still executes the `stat` syscall inline during composition the first time each voice bubble composes — the "no composition IO" claim in 68-03-SUMMARY is overstated (it is memoized IO, not absent IO). Small in practice; a StrictMode disk-read policy would flag it.
**Fix:** Hoist existence into the loaded message list (VM-side `Map<path, Boolean>` flow) or wrap in `produceState`/`LaunchedEffect` with a default.

### IN-06: Audio paths are existence-checked but never confined to `filesDir/voice`

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:2319-2342, 2676-2682`
**Issue:** `playHistoryVoice()` and the `deleteMessage()` cleanup pass any non-blank stored `audioPath` to `setDataSource()`/`File.delete()`. The DB is app-private so exploitation requires a compromised store, but a tampered row could aim playback or (worse) irreversible deletion at any app-readable/writable file. Defense in depth costs one canonical-path check.
**Fix:**
```kotlin
val voiceDir = java.io.File(context.filesDir, "voice").canonicalPath
val f = java.io.File(path).canonicalFile
require(f.path.startsWith("$voiceDir/")) { "voice path escapes voice dir" }
```

---
_Checked and found clean: migration DDL shape + registration + v18 head; EN/ES parity (21/21 keys, matching format args); no new permissions, receivers, or exported components; per-path single-player preemption; blank-path → no-player degradation in mapper + VM._

_Reviewed: 2026-10-02T00:00:00Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_

---

## Fix notes (2026-10-02, code-fix pass)

All 1 Critical + 6 Warning findings fixed and committed atomically on `main`
(`fix(68): …` × 7 + 1 test-alignment commit). Of the 6 Info findings, only
IN-03 (one-line `@Volatile`, zero risk) was taken; IN-01/IN-02/IN-04/IN-05/IN-06
left as-is per scope (trivial-and-safe only).

- **CR-01** (`MessageBubble.kt` `VoicePlayerRow`): progress now derives from
  `positionMs / durationMs` regardless of playing state — same locked
  convention as the draft card. Commit `911dc659`.
- **WR-01** (`ChatViewModel.kt`): `draftPlayStarting` + `historyPlayStarting`
  replaced by one shared `anyPlayStarting` gate across both play paths; the
  winner's stop-then-play still clears the other path via
  `stopPlaybackInternal`. Commit `dfde103a`.
- **WR-02** (`sendVoiceMessage` catch): restores clip A only when the slot is
  still empty; otherwise deletes A's failed file and drops the stale
  send-time holders. `voiceClipFile` marked `@Volatile` so the IO-thread
  check observes it. Commit `92d5e135`.
- **WR-03** (stop-vs-next-start race): new `voiceClipSession` token, bumped on
  live-session start / cancel / draft-delete / send; `keepClipAfterDurationCheck`
  captures it at stop time and drops stale IO results (orphan file only,
  never live state). Commit `4d867528`.
- **WR-04** (completion-before-flag window): both play paths re-check
  `player.isPlaying` after `play()` returns and bail without arming flags or
  poll jobs when the player already died. Commit `3f9a168e`.
- **WR-05** (transcript-keyed delete): new `MessageDao.getById` +
  `ChatRepository.getMessageAudioPath`; `deleteMessage` resolves the clip from
  the Room row first, transcript as fallback (covers UUID ids of just-sent
  rows and repo failures), and deletes through a `filesDir/voice`-confined
  helper (canonical-path check — also covers the IN-06 concern for this path;
  the `playHistoryVoice` side of IN-06 is untouched). The failed-delete swallow
  now logs loudly. Vacuum/reaper NOT implemented — recorded as OPEN follow-up
  in `deferred-items.md`. Commit `526d6260`; test alignment `cd325634`
  (delete tests now stage clips under `filesDir/voice`, stub `filesDir` +
  `getMessageAudioPath`, plus a new unloaded-row delete test).
- **WR-06** (rotation): `ChatScreen` ON_PAUSE now pauses (never stops) both
  paths on every pause including config change — selection + position survive
  for one-tap resume per CONTEXT; VM rotation note updated. The player is
  VM-owned with no Activity reference, so the paused handle survives
  recreation. Commit `1cd47250`.
- **IN-03**: `VoiceMessagePlayer.currentPath` marked `@Volatile`.
  Commit `9da10970`.

**Verification (all in main checkout):** targeted voice tests green
(`VoiceHistoryPlaybackTest`, `VoiceDraftGuardTest`, `VoiceMessageGuardTest`,
`voice.*`, `VoiceMigrationTest` — 58 tests); full suite
`:app:testDebugUnitTest` — **996 tests, 0 failures**; `:app:assembleDebug` —
**BUILD SUCCESSFUL**. One iteration was needed: the first WR-05 cut broke
`deleting a voice message deletes its file` because the test clip lived outside
`filesDir/voice` (correctly refused by the new confinement) — fixed by aligning
the test with the production contract, not by weakening the fix.

**Needs human/device eyes:** WR-04/WR-01 race windows are timing-dependent and
covered by state assertions only, not by real concurrent MediaPlayer behavior;
WR-06 rotation-keep and the (g) runbook step changed meaning (paused-kept, not
paused-at-0) — confirm on hardware per `deferred-items.md`.
