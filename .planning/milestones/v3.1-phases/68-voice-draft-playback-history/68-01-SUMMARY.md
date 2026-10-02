---
phase: 68-voice-draft-playback-history
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/warped/ui/chat/voice/VoiceMessagePlayer.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values-es/strings.xml
  - app/src/test/java/com/warped/ui/chat/voice/VoiceMessagePlayerTest.kt
  - app/src/test/java/com/warped/ui/chat/VoiceDraftGuardTest.kt
  - app/src/test/java/com/warped/ui/chat/VoiceMessageGuardTest.kt
autonomous: true
requirements: [VMSG-02]
must_haves:
  truths:
    - "User previews the recorded draft before sending (play/pause + send + delete) from a persistent card above the chat input"
    - "Clips under 1 second are rejected at stop time with a graceful Snackbar and the file deleted — the draft card never appears"
    - "Delete removes the audio file immediately with no confirmation; the player never leaks past the screen and never blocks the UI thread"
  artifacts:
    - path: "app/src/main/java/com/warped/ui/chat/voice/VoiceMessagePlayer.kt"
      provides: "VM-owned platform MediaPlayer wrapper (play/pause/resume/stop, progress, audio focus)"
      exports: ["VoiceMessagePlayer", "PlayerFactory"]
    - path: "app/src/main/java/com/warped/ui/chat/ChatViewModel.kt"
      provides: "Draft playback state + sub-1s guard + draft send/delete wiring"
      contains: "playVoiceDraft"
    - path: "app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt"
      provides: "Draft preview card above the input (play/pause + progress + duration + send + delete)"
      contains: "hasVoiceClip"
  key_links:
    - from: "app/src/main/java/com/warped/ui/chat/ChatViewModel.kt"
      to: "ui/chat/voice/VoiceMessagePlayer.kt"
      via: "lazy holder plus onCleared destroy, mirroring getVoiceRecorder"
      pattern: "getVoiceRecorder"
    - from: "app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt"
      to: "app/src/main/java/com/warped/ui/chat/ChatViewModel.kt"
      via: "draft card params (clip state + playback state + callbacks)"
      pattern: "voiceElapsedSec"
    - from: "app/src/main/java/com/warped/ui/chat/ChatScreen.kt"
      to: "app/src/main/java/com/warped/ui/chat/ChatViewModel.kt"
      via: "collect draft playback flows + ON_PAUSE pause + too-short Snackbar channel"
      pattern: "autoStopVoiceRecording"
subsystem: voice-draft
tags: [voice, mediaplayer, draft-preview, chat]
dependency_graph:
  requires:
    - phase-67-voice-recorder
  provides:
    - voice-message-player
    - draft-preview-card
    - sub-1s-guard
    - stopPlayback-entry
  affects:
    - 68-02-history-playback
    - 68-03-history-bubbles
tech_stack:
  added: []
  patterns:
    - "VM-owned platform MediaPlayer wrapper with PlayerFactory seam (mirrors VoiceMessageRecorder)"
    - "Single stop-and-keep choke point for the sub-1s guard (manual + auto + background stops)"
    - "Collect-first Turbine testIn pattern for SharedFlow emissions off the test scheduler"
key_files:
  created:
    - app/src/main/java/com/warped/ui/chat/voice/VoiceMessagePlayer.kt
    - app/src/test/java/com/warped/ui/chat/voice/VoiceMessagePlayerTest.kt
    - app/src/test/java/com/warped/ui/chat/VoiceDraftGuardTest.kt
  modified:
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
    - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-es/strings.xml
    - app/src/test/java/com/warped/ui/chat/VoiceMessageGuardTest.kt
decisions:
  - "Duration readout is total m:ss always; position shows only as progress fill (ONE fixed convention)"
  - "TalkBack stateDescription (total + playing/paused) changes only on toggles — 5 s throttle holds by construction"
  - "Rotation stops playback via stopPlayback (position resets to 0); backgrounding pauses and keeps position"
  - "voiceDurationReader seam for MediaMetadataRetriever (framework-only under JVM tests)"
metrics:
  duration: "~45 min"
  completed: "2026-10-02"
---

# Phase 68 Plan 01: Voice Draft Preview Summary

Draft preview loop shipped: VM-owned `VoiceMessagePlayer` (single-player, transient audio focus), persistent draft card (play/pause + progress + total duration + send + delete), sub-1 s rejection guard at the single stop-and-keep choke point, immediate file delete on discard — VMSG-02 complete.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | VoiceMessagePlayer plus player unit test | (see commit) | VoiceMessagePlayer.kt, VoiceMessagePlayerTest.kt |
| 2 | VM draft playback state + sub-1s guard + draft send/delete | (see commit) | ChatViewModel.kt, VoiceDraftGuardTest.kt, VoiceMessageGuardTest.kt |
| 3 | Draft preview card UI + strings + lifecycle | (see commit) | ChatInputBar.kt, ChatScreen.kt, strings.xml, strings.xml (es) |

## Verification

- `./gradlew :app:testDebugUnitTest --tests "com.warped.ui.chat.voice.*" --tests "com.warped.ui.chat.VoiceDraftGuardTest"` — BUILD SUCCESSFUL (19 tests: 12 player + 7 guard)
- `./gradlew :app:testDebugUnitTest` (FULL suite) — BUILD SUCCESSFUL, 975 tests, 0 failures
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
- Hardcoded-copy grep (`Recording too short|Play voice draft|Delete voice draft` in main sources) — 0
- Zero new Gradle dependencies (android.media + AudioManager only); retriever + prepare on IO, polling on Default

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] FakeHandle property/method JVM signature clash**
- **Found during:** Task 1 test compile
- **Issue:** `var dataSource` generated `setDataSource()` clashing with the `PlayerHandle.setDataSource()` override; same for `var duration` vs `getDuration()`
- **Fix:** Renamed fake internals to `capturedSource` / `fakeDuration`
- **Files modified:** VoiceMessagePlayerTest.kt

**2. [Rule 3 - Blocking] MediaMetadataRetriever unavailable under JVM unit tests**
- **Found during:** Task 2 (android.jar stubs throw; guard tests need fixed durations)
- **Issue:** `keepAndStopVoice` choke must read duration via framework-only API — untestable directly
- **Fix:** Added internal `voiceDurationReader: (File) -> Long` seam (default reads via retriever on IO); tests inject fixed durations
- **Files modified:** ChatViewModel.kt

**3. [Rule 2 - Correctness] Phase-67 cancel test assumed synchronous keep**
- **Found during:** Task 2 (stop now validates duration off-Main before setting hasVoiceClip)
- **Issue:** `cancel after stop deletes the kept clip` asserted `hasVoiceClip == true` synchronously after stop
- **Fix:** Injected `voiceDurationReader = { 3_000L }`, parked on the keep via `hasVoiceClip` turbine instead of asserting synchronously
- **Files modified:** VoiceMessageGuardTest.kt

**4. [Rule 3 - Blocking] Late SharedFlow collectors race the virtual-time Turbine timeout**
- **Found during:** Task 2 guard tests (reject-path `events.test { awaitItem() }` opened after stop timed out with "No value produced in 3s")
- **Issue:** The too-short Snackbar is a buffered SharedFlow emission from a real IO thread; a late collector + virtual-time 3 s Turbine timeout fires before the real thread delivers (StateFlow late-collect works via replay — SharedFlow has none)
- **Fix:** Collect-first pattern — both turbines go live via `testIn(this)` inside `turbineScope` BEFORE the triggering action (Phase 67 awaitItem precedent); probe test used during diagnosis then deleted
- **Files modified:** VoiceDraftGuardTest.kt

**5. [Rule 2 - Correctness] Two extra TalkBack state-word strings**
- **Found during:** Task 3 (draft_state format needs a playing/paused word; no listed string provides one without awkward reuse)
- **Issue:** `voice_msg_draft_state` (`Voice draft, %1$d:%2$02d, %3$s`) needs a localized state word
- **Fix:** Added `voice_msg_draft_playing` / `voice_msg_draft_paused` (+ ES `reproduciendo` / `en pausa`) — additive, zero hardcoded copy preserved
- **Files modified:** values/strings.xml, values-es/strings.xml

**6. [Rule 2 - Correctness] cancelVoiceRecording also tears down draft playback state**
- **Found during:** Task 2 (cancel discards kept clips but left player + duration alive)
- **Issue:** Orphaned player/duration after cancel-discard
- **Fix:** `cancelVoiceRecording` now calls `stopPlaybackInternal()` and zeroes `_draftDurationMs`
- **Files modified:** ChatViewModel.kt

**7. [Rule 2 - Correctness] Player gains resume() for pause-then-resume**
- **Found during:** Task 2 (pause keeps the handle; replay must continue, not restart)
- **Issue:** Plan listed play/pause/stop only — pause without resume forces restart-from-0
- **Fix:** Added `resume()` (no-op false without a live handle; caller falls back to fresh play) + `hasClip`
- **Files modified:** VoiceMessagePlayer.kt

## Decisions Made

- Draft card lives inside `ChatInputBar`'s column (above the input row), not in ChatScreen's bottomBar slot — keeps all input chrome in one component; caption input stays live
- `voice_msg_clip_unavailable` ships in this plan (Wave 2 compile dependency per plan)
- `stopPlayback()` public entry created for Plan 02 history extension; `lastSentVoicePath` / `lastSentVoiceDurationMs` holders stamped at send time for Plan 02 persistence

## Deferred Issues

Device-dependent checks (record/play audibility, background/rotation passes on hardware, 60 s auto-stop real-time) need hardware — recorded as release-UAT deferred items with runbook (see deferred-items.md).

## Self-Check: PASSED

- VoiceMessagePlayer.kt, VoiceMessagePlayerTest.kt, VoiceDraftGuardTest.kt exist on disk
- Full suite 975 tests / 0 failures verified from build reports; assembleDebug BUILD SUCCESSFUL
