---
phase: 65-voice-dictation
plan: "01"
subsystem: chat-voice-dictation-foundation
tags: [speech-recognizer, record-audio, dictation, chat-input, strings-en-es]
dependency_graph:
  requires: []
  provides:
    - VoiceDictationManager partial/final/error callback interface
    - ChatEvent.SnackbarWithAction denial event with SnackbarAction id
    - ChatViewModel dictation ownership (speechAvailable, isListening, appendDictation, start/stopDictation, emitMicDenied)
    - RECORD_AUDIO manifest declaration plus 7 EN+ES dictation strings
  affects:
    - 65-02 (consumes all foundation interfaces for mic button, permission flow, rationale dialog, denial Snackbar)
tech_stack:
  added: []
  patterns:
    - Single-owner input mutation via private updateInput op helper
    - One-shot UI events via tryEmit SharedFlow ChatEvent channel
    - Platform availability cached off the main thread (Dispatchers.IO at init)
key_files:
  created:
    - app/src/main/java/com/warped/ui/chat/voice/VoiceDictationManager.kt
  modified:
    - app/src/main/AndroidManifest.xml
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-es/strings.xml
    - app/src/main/java/com/warped/ui/chat/ChatUiState.kt
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
decisions:
  - ChatEvent denial carries a stable SnackbarAction enum id instead of a lambda so no intent crosses the ViewModel boundary
  - Denial collector branch renders message plus action label with Long duration; Settings deep-link wiring stays in 65-02
  - Dictation availability probe reuses the lazily created manager instance so no second wrapper exists
  - Partial and final results both route into appendDictation per plan; dedup policy (if needed) belongs to 65-02 UI wiring
metrics:
  duration: "~25 min"
  completed: 2026-10-02
---

# Phase 65 Plan 01: Voice-Dictation Foundation Summary

Offline-capable chat input dictation foundation: RECORD_AUDIO declaration, 7 EN+ES dictation strings with verb+noun rationale copy, a greenfield SpeechRecognizer wrapper with partial/final/error callbacks, and ViewModel-owned dictation state with silent-error policy and lifecycle teardown.

## Completed Tasks

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | RECORD_AUDIO manifest plus 7 dictation strings EN+ES | d69f979b | AndroidManifest.xml, values/strings.xml, values-es/strings.xml |
| 2 | Greenfield VoiceDictationManager SpeechRecognizer wrapper | e865a1bb | ui/chat/voice/VoiceDictationManager.kt |
| 3 | Chat state, dictation events, and ViewModel ownership | a305230d | ChatUiState.kt, ChatViewModel.kt, ChatScreen.kt |

## What Was Built

- **Manifest:** `RECORD_AUDIO` uses-permission declared; the stale removal comment updated so RECORD_AUDIO is no longer listed as dead and records Phase 65 as the real feature with an in-context runtime request.
- **Strings (7 keys, EN+ES parity):** `cd_dictate` (Dictate/Dictar), `cd_stop_listening` (Stop listening/Dejar de escuchar) placed adjacent to `cd_stop`/`cd_send` per the `cd_` convention; `voice_rationale_title`, `voice_rationale_body`, `voice_rationale_allow` (Allow Microphone/Permitir micrófono, verb+noun), `voice_denied`, `voice_open_settings`. Existing `dismiss` reused — no new dismiss-style key.
- **VoiceDictationManager:** constructor takes application Context plus onPartial/onFinal/onError callbacks; `isAvailable()` gates on `SpeechRecognizer.isRecognitionAvailable`; `start()` builds the `ACTION_RECOGNIZE_SPEECH` intent with `EXTRA_PARTIAL_RESULTS` and system-locale language, creates the recognizer once with a `RecognitionListener` forwarding first-result strings; `stop()` plus explicit `destroy()` that stops and destroys. Async-only platform calls, zero Compose/Hilt imports.
- **Chat state:** `ChatEvent.SnackbarWithAction(message, actionLabel, action)` beside `Snackbar`, with `SnackbarAction.OPEN_APP_SETTINGS` stable identifier; tryEmit non-blocking KDoc contract kept.
- **ViewModel:** `speechAvailable`/`isListening` StateFlows (default false); availability resolved once at init on Dispatchers.IO; `appendDictation` appends with separating space exclusively through the private `updateInput` op helper (never replaces, never auto-sends); `startDictation`/`stopDictation` lazily create the manager and flip listening; `onError` only clears listening (silent-error policy, no emission, no copy); `emitMicDenied` tryEmits `voice_denied` plus `voice_open_settings` for permanent-denial only; `onCleared` destroys the recognizer beside `unloadLocalModels`.

## Verification

- All six plan grep checks pass (RECORD_AUDIO present; EN+ES parity on all 7 keys; Allow Microphone/Permitir micrófono copy; manager surface 5/5; zero Compose/Hilt leak; appendDictation + ChatEvent + onCleared + speech flows present).
- `./gradlew :app:assembleDebug` succeeds for the touched module.
- `git log` shows the three atomic commits, each separately revertible.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Exhaustive `when` in ChatScreen broke compilation**
- **Found during:** Task 3 (build verification)
- **Issue:** Adding `ChatEvent.SnackbarWithAction` made the `viewModel.events.collect` `when` in `ChatScreen.kt:188` non-exhaustive — `:app:compileDebugKotlin` failed.
- **Fix:** Added an explicit `SnackbarWithAction` branch rendering through the existing SnackbarHost (message + action label, `SnackbarDuration.Long`). The Settings deep-link action wiring stays in plan 65-02 per plan scope; the branch only keeps compilation green until then.
- **Files modified:** app/src/main/java/com/warped/ui/chat/ChatScreen.kt (collector branch only, no new params or composables)
- **Commit:** a305230d (folded into the Task 3 atomic commit)

Or else: no other deviations — plan executed as written.

## Known Stubs

None. No placeholder copy, no unwired surfaces: the denial event has no emitter yet by design (the permission launcher that calls `emitMicDenied` is plan 65-02 scope), and the collector branch documents that handoff explicitly.

## Threat Flags

None beyond the plan's threat register: T-65-01 mitigated (first-result strings land editable, never auto-send), T-65-02 accepted (rationale body discloses system-service processing; app records no audio), T-65-03 mitigated (in-context request and Settings escape land in 65-02; this plan only declares the permission and owns the denial event), T-65-SC accepted (no package installs).

## Self-Check: PASSED

- Created file exists: `app/src/main/java/com/warped/ui/chat/voice/VoiceDictationManager.kt` FOUND
- Commits exist: d69f979b, e865a1bb, a305230d all FOUND in `git log`
- Backstops hold: 0 new composables, 0 new destinations, 0 new spacing tokens (no UI code added; ChatScreen change is a collector branch only)
