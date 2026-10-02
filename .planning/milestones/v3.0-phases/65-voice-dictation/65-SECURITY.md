# Phase 65 — Voice Dictation: Security Audit (SECURITY.md)

**Phase:** 65 — Voice Dictation (plans 65-01 + 65-02)
**Verdict:** SECURED
**Threats Closed:** 8/8 (6 unique + T-65-SC counted in both plans)
**ASVS Level:** 1 (client-side mobile feature; no server auth/crypto scope)
**Audited:** 2026-10-02
**Auditor stance:** adversarial — every mitigation treated as absent until a grep match proved it in the right location.

## Threat Verification

| Threat ID | Category | Disposition | Verdict | Evidence |
|-----------|----------|-------------|---------|----------|
| T-65-01 | Spoofing (RecognitionListener) | mitigate | CLOSED | `VoiceDictationManager.kt:74-84` — `onPartialResults`/`onResults` forward only `firstResult(bundle)` (defined `:148-152`, `RESULTS_RECOGNITION.firstOrNull`, blank-filtered); `ChatViewModel.kt:1471-1533` — `onDictationPartial`/`onDictationFinal` mutate the draft only via `updateInput` (append/replace at cursor), never auto-send; send path is the separate `sendMessage` |
| T-65-02 | Information Disclosure (speech audio → system service) | accept | CLOSED (accepted risk, logged below) | `res/values/strings.xml:223` rationale body discloses system-service processing ("Your speech is processed by the system's speech service"); app itself records nothing — `VoiceDictationManager.kt:70` `onBufferReceived = Unit` (audio buffer discarded), grep over `ui/chat/**` finds no `MediaRecorder`/`AudioRecord`/audio file writes (only pre-existing dead `audioBytes` params left untouched per UI-SPEC non-goals) |
| T-65-03 | Elevation (RECORD_AUDIO) | mitigate | CLOSED | `ChatScreen.kt:302-312` — permission checked inside `onMicClick` click lambda (first ungranted tap shows rationale, never a startup request; no `requestPermissions` in init/`LaunchedEffect`); permanent denial (`shouldShowRequestPermissionRationale==false`) → `emitMicDenied()` (`ChatScreen.kt:287-293`, `ChatViewModel.kt:1624-1632` tryEmits `voice_denied` + `voice_open_settings`); transient denial stays silent (no emission) — no retry loop |
| T-65-01-SC (65-01) | Tampering (supply chain) | accept | CLOSED (nothing to gate) | No package-manager installs in either plan; `tech_stack.added: []` in both SUMMARIES; no new Gradle dependencies |
| T-65-04 | Spoofing (rationale dialog) | mitigate | CLOSED | `ChatScreen.kt:632-651` — `WarpedAlertDialog` uses exact `voice_rationale_title`/`voice_rationale_body`/`voice_rationale_allow` strings; confirm button is the sole trigger of `micPermissionLauncher.launch(RECORD_AUDIO)` (`:649`); dismiss reuses existing `dismiss` string |
| T-65-05 | Tampering (Settings intent) | mitigate | CLOSED | `ChatScreen.kt:235-237` — intent fixed to `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` with `Uri.fromParts("package", context.packageName, null)` only; no extras, no user-controlled destination; action resolved via `SnackbarAction.OPEN_APP_SETTINGS` enum (`:226-241`), no intent crosses the ViewModel boundary |
| T-65-06 | Denial (mic vs generation stop) | mitigate | CLOSED | `ChatInputBar.kt:203` — mic gated by `if (speechAvailable && !isGenerating)`; listening state swaps to stop icon (`:211-212`) so no two stop icons coexist |
| T-65-SC (65-02) | Tampering (supply chain) | accept | CLOSED (nothing to gate) | Same as 65-01: no installs, no new dependencies |

## Focus-Area Verification (audit brief)

| Check | Result | Evidence |
|-------|--------|----------|
| Microphone lifecycle — no orphaned listening (CR-03) | PASS | Four-layer teardown: `sendMessage` calls `stopDictation()` first (`ChatViewModel.kt:439`); screen `onSend` stops before send (`ChatScreen.kt:401`); `DisposableEffect.onDispose` → `stopDictation()` (`ChatScreen.kt:154-161`); `onCleared` → `dictationManager?.destroy()` (`ChatViewModel.kt:2192-2194`); late callbacks dropped by `if (!_isListening.value) return` guards (`ChatViewModel.kt:1472,1509`); single-shot recognizer always clears flag on final (`:1508-1510`) incl. blank finals (CR-02, `VoiceDictationManager.kt:78-84`) |
| No audio recording/storage/transmission by the app | PASS | Platform `SpeechRecognizer` only; `onBufferReceived` discarded (`VoiceDictationManager.kt:70`); no `MediaRecorder`/`AudioRecord`/audio-file/network-audio code in `ui/chat/**`; `EXTRA_MAX_RESULTS=1`, no `EXTRA_LANGUAGE` override (system locale) |
| Permission rationale honesty | PASS | `voice_rationale_body` (EN `strings.xml:223`, ES parity verified in plan gate) states mic access purpose + system-service processing before consent; confirm label verb+noun `Allow Microphone` / `Permitir micrófono` |
| Denial paths | PASS | Transient denial silent; permanent denial → Long Snackbar with Settings escape (`ChatScreen.kt:225-241`); recognizer availability gates mic visibility (`speechAvailable`, `isAvailable()` via `SpeechRecognizer.isRecognitionAvailable`, resolved off main thread `ChatViewModel.kt:366-368`) |
| No sensitive data in logs | PASS | All `Timber` in voice path log only error codes/exceptions, never transcript text: `onDictationError` logs `"Voice: recognition error $error"` (int only, `ChatViewModel.kt:1543`); `onDictationPartial`/`onDictationFinal` contain zero log calls; manager logs only lifecycle failures (`VoiceDictationManager.kt:43,100,113,124,137,142`) |
| Manifest `uses-feature required=false` | PASS | `AndroidManifest.xml:15` — `<uses-feature android:name="android.hardware.microphone" android:required="false" />` with WR-05 comment; `RECORD_AUDIO` declared `:9` with Phase 65 removal-comment update (`:16-21`) |
| No startup permission request | PASS | Only call sites of `micPermissionLauncher.launch` are the rationale confirm (`ChatScreen.kt:649`) and post-rationale tap (`:310`); none in init/composition path |

## Unregistered Flags

None. Both plan SUMMARIES report `## Threat Flags: None beyond the plan's threat register`, and the audit found no new attack surface: zero new composables/destinations, no new permissions beyond `RECORD_AUDIO`, no new network calls, no new dependencies. The pre-existing dead `modelHasAudio`/`onAudioRecorded`/`onAudioRecordingChanged` audio-message path was deliberately left untouched (UI-SPEC non-goal) — not new surface, flagged for a future audio-messages phase to remove or repurpose.

## Accepted Risks Log

| Risk ID | Threat | Accepted Risk | Rationale |
|---------|--------|---------------|-----------|
| AR-65-01 | T-65-02 | Spoken audio leaves the device to the platform speech service (Google/IME recognizer) for transcription; the app cannot control that service's retention/transmission | Core to the feature (on-device STT out of scope); user is informed via the pre-consent rationale body copy; app itself persists/transmits no audio |
| AR-65-02 | T-65-SC (both plans) | No supply-chain gating performed | No package-manager installs or new dependencies in this phase — nothing to gate |

## Open Threats

None. All declared mitigations verified present in the cited implementation files.
