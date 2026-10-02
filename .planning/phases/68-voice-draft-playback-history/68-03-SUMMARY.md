---
phase: 68-voice-draft-playback-history
plan: 03
type: execute
wave: 3
depends_on: ["02"]
files_modified:
  - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values-es/strings.xml
autonomous: true
requirements: [VMSG-06]
must_haves:
  truths:
    - "User replays sent voice messages from history (bubble with play + duration + progress) across app restarts"
    - "A voice bubble whose file is gone renders a graceful 'clip unavailable' state — never a silent drop, never a crash"
    - "Backgrounding pauses history playback keeping position; chat exit stops playback; rotation pauses with position at 0"
  artifacts:
    - path: "app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt"
      provides: "Own-voice bubble player row inside standard bubble chrome + unavailable state"
      contains: "audioPath"
    - path: "app/src/main/java/com/warped/ui/chat/ChatScreen.kt"
      provides: "History playback wiring (state params + ON_PAUSE pause + unavailable Snackbar)"
      contains: "playingMessageId"
  key_links:
    - from: "app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt"
      to: "app/src/main/java/com/warped/ui/chat/ChatViewModel.kt"
      via: "player-row callbacks (onPlay/onPause) + playback state params resolved by ChatScreen"
      pattern: "onRetry"
    - from: "app/src/main/java/com/warped/ui/chat/ChatScreen.kt"
      to: "app/src/main/java/com/warped/ui/chat/ChatViewModel.kt"
      via: "playingMessageId/historyPositionMs flows + pauseHistoryVoice in ON_PAUSE"
      pattern: "pauseVoiceDraft"
subsystem: voice-history-ui
tags: [voice, message-bubble, playback-ui, chat]
dependency_graph:
  requires:
    - 68-02-history-playback
  provides:
    - history-voice-bubbles
    - clip-unavailable-state
  affects:
    - phase-69-transcript-captions
tech_stack:
  added: []
  patterns:
    - "Bubble player row slotted after images, above caption (Phase 69 transcript slots underneath)"
    - "Remembered per-message file-exists check (no composition-hot-path IO)"
key_files:
  created: []
  modified:
    - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-es/strings.xml
decisions:
  - "Per-row duration reads message.audioDurationMs (identical source the historyDurationMs flow mirrors)"
  - "onPlay routes through toggleHistoryVoice (pause-if-playing-else-play); onPause is direct pause"
  - "voice_msg_play/pause_message both interpolate duration (UI-SPEC bubble-play contract)"
metrics:
  duration: "~25 min"
  completed: "2026-10-02"
---

# Phase 68 Plan 03: History Playback Bubbles Summary

User-visible VMSG-06 surface shipped: own-voice bubble player rows (play/pause + live progress + total duration) inside standard bubble chrome, graceful missing-file state, history lifecycle (background pause-keep, chat-exit stop, rotation stop-to-0), EN+ES strings — full suite green.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | History voice bubbles + unavailable state + strings + full gate | (see commit) | MessageBubble.kt, ChatScreen.kt, strings.xml, strings.xml (es) |

## Verification

- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
- Hardcoded-copy grep (`Voice clip unavailable|Play voice message` in main sources) — 0
- `./gradlew :app:testDebugUnitTest` (FULL suite) — BUILD SUCCESSFUL, 995 tests, 0 failures
- Zero new Gradle dependencies; existence check via remembered VM helper (no composition IO); polling stays on Default

## Deviations from Plan

None - plan executed exactly as written. (The `voice_msg_clip_unavailable` resource shipped in Plan 01 as required — reused here, not duplicated.)

## Decisions Made

- Unavailable row uses `Icons.Filled.VolumeOff` (audio-off family, executor's discretion per UI-SPEC) with `contentDescription = null` (decorative — the text carries the meaning)
- Progress track uses `surfaceContainerHighest` per UI-SPEC bubble color rule
- Long-caption backstop holds by layout order (player row above caption Text, wraps naturally) — visual confirmation is a held-out hardware check in the release-UAT runbook

## Deferred Issues

Hardware checks (replay across restart, rotation/background passes, audibility, long-caption visual) — release-UAT deferred items with runbook (see deferred-items.md).

## Self-Check: PASSED

- MessageBubble.kt voice row + ChatScreen wiring exist on disk; new strings identical in values/ and values-es/
- Full suite 995 tests / 0 failures verified from build reports; assembleDebug BUILD SUCCESSFUL
