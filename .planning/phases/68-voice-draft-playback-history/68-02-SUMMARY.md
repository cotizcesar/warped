---
phase: 68-voice-draft-playback-history
plan: 02
type: execute
wave: 2
depends_on: ["01"]
files_modified:
  - app/src/main/java/com/warped/data/local/db/entity/MessageEntity.kt
  - app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt
  - app/src/main/java/com/warped/data/local/db/Migrations.kt
  - app/src/main/java/com/warped/di/DatabaseModule.kt
  - app/src/main/java/com/warped/domain/model/ChatMessage.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/ui/chat/voice/VoiceMessagePlayer.kt
  - app/src/test/java/com/warped/data/local/db/VoiceMigrationTest.kt
  - app/src/test/java/com/warped/data/local/db/Migration16To17StaticTest.kt
  - app/src/test/java/com/warped/ui/chat/VoiceHistoryPlaybackTest.kt
  - app/src/test/java/com/warped/ui/chat/voice/VoiceMessagePlayerTest.kt
autonomous: true
requirements: [VMSG-06]
must_haves:
  truths:
    - "User replays sent voice messages from history (bubble with play + duration + progress) across app restarts"
    - "A voice bubble whose file is gone renders a graceful 'clip unavailable' state — never a silent drop, never a crash"
    - "Recording/draft state survives rotation; backgrounding auto-stops and keeps the draft"
  artifacts:
    - path: "app/src/main/java/com/warped/data/local/db/Migrations.kt"
      provides: "MIGRATION_17_18 (audio_path + audio_duration_ms + transcript placeholder)"
      contains: "MIGRATION_17_18"
    - path: "app/src/main/java/com/warped/ui/chat/ChatViewModel.kt"
      provides: "History playback state (single-player) + voice persistence at send time + file cleanup on delete"
      contains: "playingMessageId"
  key_links:
    - from: "app/src/main/java/com/warped/ui/chat/ChatViewModel.kt"
      to: "ui/chat/voice/VoiceMessagePlayer.kt"
      via: "shared single player instance for draft AND history (Plan 01 stopPlayback entry)"
      pattern: "stopPlayback"
    - from: "app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt"
      to: "data/local/db/entity/MessageEntity.kt"
      via: "field-by-field voice mapping with safe defaults for legacy rows"
      pattern: "toRoleSafe"
subsystem: voice-history
tags: [voice, room-migration, history-playback, chat]
dependency_graph:
  requires:
    - 68-01-draft-preview
  provides:
    - voice-persistence-17-18
    - history-playback-state
    - voice-send-stamping
  affects:
    - 68-03-history-bubbles
tech_stack:
  added: []
  patterns:
    - "ALTER-only nullable migration with zero-default duration (mirrors 15_16/16_17 shape)"
    - "Shared single player with currentPath-aware resume (same-path resume, else stop-then-play)"
    - "Send-time holders consumed only by audio turns (text/image sends never steal them)"
key_files:
  created:
    - app/src/test/java/com/warped/data/local/db/VoiceMigrationTest.kt
    - app/src/test/java/com/warped/ui/chat/VoiceHistoryPlaybackTest.kt
  modified:
    - app/src/main/java/com/warped/data/local/db/entity/MessageEntity.kt
    - app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt
    - app/src/main/java/com/warped/data/local/db/Migrations.kt
    - app/src/main/java/com/warped/data/local/db/AppDatabase.kt
    - app/src/main/java/com/warped/di/DatabaseModule.kt
    - app/src/main/java/com/warped/domain/model/ChatMessage.kt
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/ui/chat/voice/VoiceMessagePlayer.kt
    - app/src/test/java/com/warped/data/local/db/Migration16To17StaticTest.kt
    - app/src/test/java/com/warped/ui/chat/voice/VoiceMessagePlayerTest.kt
    - app/schemas/com.warped.data.local.db.AppDatabase/18.json
decisions:
  - "audio_duration_ms is NOT NULL DEFAULT 0 (DDL default required for ALTER on non-empty tables); entity mirrors with = 0"
  - "transcript column is write-never/read-never in Phase 68 (Phase-69-owned placeholder)"
  - "Blank stored audioPath degrades to no-player (drop-unknown precedent, never crash)"
  - "Player tracks currentPath so resume only ever resumes the loaded path"
  - "isHistoryPlaying flow added (plan listed id/position/duration) — the bubble toggle icon needs playing-vs-paused"
metrics:
  duration: "~50 min"
  completed: "2026-10-02"
---

# Phase 68 Plan 02: Voice History Persistence + Playback Summary

Voice history foundation shipped: Room migration 17→18 (audio_path + audio_duration_ms + transcript placeholder), send-time voice stamping, single-player history playback through the Plan 01 player, missing-file grace, file cleanup on message delete — with migration + playback tests green.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | Room voice columns + migration 17-18 + mapper + tests | (see commit) | MessageEntity.kt, EntityMappers.kt, Migrations.kt, DatabaseModule.kt, ChatMessage.kt, VoiceMigrationTest.kt |
| 2 | VM history playback + voice stamping at send time | (see commit) | ChatViewModel.kt, VoiceHistoryPlaybackTest.kt |

## Verification

- `./gradlew :app:testDebugUnitTest --tests "com.warped.data.local.db.VoiceMigrationTest" --tests "com.warped.ui.chat.VoiceHistoryPlaybackTest" --tests "com.warped.ui.chat.VoiceDraftGuardTest"` — BUILD SUCCESSFUL (8 migration + 11 playback + 7 guard)
- `./gradlew :app:testDebugUnitTest` (FULL suite) — BUILD SUCCESSFUL, 995 tests, 0 failures
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
- Zero new Gradle dependencies; transcript column written by nothing and rendered by nothing

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] DatabaseModule missed the MIGRATION_17_18 import**
- **Found during:** Task 1 compile
- **Issue:** Per-migration imports; new migration unresolvable
- **Fix:** Added `import com.warped.data.local.db.MIGRATION_17_18`
- **Files modified:** DatabaseModule.kt

**2. [Rule 1 - Bug] Exported schema carries no Kotlin-constructor default**
- **Found during:** Task 1 migration test (expected `defaultValue: "0"`, Room emits no defaultValue key)
- **Issue:** Room only records `@ColumnInfo` defaults in schema export, not Kotlin `= 0`
- **Fix:** Test asserts affinity + NOT NULL + absent defaultValue (with comment); the DDL `DEFAULT 0` (required by SQLite for ADD COLUMN NOT NULL on non-empty tables) stays pinned by the migration-SQL test
- **Files modified:** VoiceMigrationTest.kt

**3. [Rule 2 - Correctness] Migration16To17StaticTest pinned head version exactly**
- **Found during:** Task 1 (version 17→18 bump broke `version = 17` exact assertions)
- **Issue:** Registration gate asserted the head version instead of its own link
- **Fix:** Forward-compatible `isAtLeast(17)` form mirroring the 15→16 at-least precedent; gate still pins the single 16→17 link
- **Files modified:** Migration16To17StaticTest.kt

**4. [Rule 2 - Correctness] Player tracks currentPath for shared-instance resume**
- **Found during:** Task 2 (draft paused + history play would `resume()` the WRONG clip)
- **Issue:** `resume()` resumes whatever handle is loaded; with one shared player the VM must only resume the same path
- **Fix:** Added `currentPath` (set on play, cleared on stop); VM resumes only on path match, else stop-then-play; added player unit test
- **Files modified:** VoiceMessagePlayer.kt, VoiceMessagePlayerTest.kt

**5. [Rule 2 - Correctness] isHistoryPlaying flow for the bubble toggle**
- **Found during:** Task 2 (pause keeps playingMessageId, so id-alone cannot drive the play/pause icon)
- **Issue:** Plan listed id/position/duration only — paused-vs-playing indistinguishable
- **Fix:** Added `isHistoryPlaying` StateFlow (true on play, false on pause/completion/stop); Plan 03 resolves the icon from `playingMessageId == id && isHistoryPlaying`
- **Files modified:** ChatViewModel.kt

**6. [Rule 1 - Bug] Back-to-back plays raced the in-flight debounce flag**
- **Found during:** Task 2 single-player test (second immediate play dropped → timeout)
- **Issue:** `historyPlayStarting` still set when the second play arrives microseconds after the first completes
- **Fix:** Test-side quiescence — the flag clears before the first 250 ms poll emission, so awaiting the position guarantees acceptance; plus skip-null for the preempt-stop id clearing
- **Files modified:** VoiceHistoryPlaybackTest.kt

## Decisions Made

- `hasVoiceFile(path)` VM helper added for Plan 03 bubble rendering (remembered per-message at the call site)
- Send-time holders consumed+cleared only on audio turns — text/image sends leave fields null/0 and never steal holders
- Kept clip file is NOT deleted on send — it becomes the history playback source of truth in filesDir/voice
- Message delete removes the orphaned clip file best-effort after the DB delete

## Deferred Issues

History replay across app restart, rotation/background passes on hardware — release-UAT deferred items with runbook (see deferred-items.md).

## Self-Check: PASSED

- VoiceMigrationTest.kt, VoiceHistoryPlaybackTest.kt, 18.json exist on disk
- Full suite 995 tests / 0 failures verified from build reports; assembleDebug BUILD SUCCESSFUL
