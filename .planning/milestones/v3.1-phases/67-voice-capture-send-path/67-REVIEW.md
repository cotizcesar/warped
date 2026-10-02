---
phase: 67-voice-capture-send-path
reviewed: 2026-10-02T19:30:00Z
depth: standard
files_reviewed: 7
files_reviewed_list:
  - app/src/main/java/com/warped/ui/chat/voice/VoiceMessageRecorder.kt
  - app/src/main/java/com/warped/ui/chat/voice/PcmTranscoder.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values-es/strings.xml
findings:
  critical: 2
  warning: 5
  info: 2
  total: 9
status: issues_found
fix_applied: 2026-10-02T19:45:00Z
fix_scope_result: 7/7 in-scope (CR+WR) fixed; IN-01/IN-02 remain open (info, out of fix scope)
---

# Phase 67: Code Review Report

**Reviewed:** 2026-10-02T19:30:00Z
**Depth:** standard
**Files Reviewed:** 7
**Status:** issues_found

## Summary

Reviewed the Phase 67 voice-capture tracer (commits 202850a7, fd2bfc0f, b5192ac9, fcbc202f, 555f330c, cd3b0f32): VM-owned MediaRecorder wrapper, AAC-to-PCM transcoder, ViewModel session wiring, permission flow, recording-row UI, and EN+ES strings. The MediaRecorder lifecycle core (start/stop/cancel/destroy release discipline, failed-start cleanup, RuntimeException-safe stop) is solid, and the send path correctly leans on the existing `capabilities.audio` backstop in `sendMessage`. Two Critical defects ship: background auto-stop fires a bogus "60s limit" toast on every pause, and mid-recording teardown orphans unreferenced voice files on disk. Five warnings cover threading, decoder-format, and rotation issues. Security surface is clean (app-private `filesDir`, no user input in paths, grant-gated start). Test files were read for context only; no test-reliability findings.

## Critical Issues

### CR-01: Background auto-stop fires a bogus "60s limit reached" toast on every pause

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:1870` and `app/src/main/java/com/warped/ui/chat/ChatScreen.kt:203`
**Issue:** `autoStopVoiceRecording()` unconditionally emits `_voiceCapEvent`, and the `ON_PAUSE` lifecycle observer calls it. Backgrounding a 5-second recording therefore toasts "60s limit reached" even though the cap was never hit. Same defect fires the toast when `stop()` yields null (too-short recording throws RuntimeException inside `stop()`), telling the user the limit was reached while no clip was kept. The one-shot event is also buffered (`extraBufferCapacity = 1`, no replay), so a cap fired while the screen is gone is delivered late on next collect.
**Fix:**
```kotlin
fun autoStopVoiceRecording(announceCap: Boolean = true) {
    if (!_isVoiceRecording.value) return
    val file = keepAndStopVoice()
    voiceClipFile = file
    _hasVoiceClip.value = file != null
    if (announceCap && file != null) _voiceCapEvent.tryEmit(Unit)
}
// ChatScreen ON_PAUSE observer:
viewModel.autoStopVoiceRecording(announceCap = false)
```

### CR-02: Mid-recording teardown orphans an unreferenced voice file on disk

**File:** `app/src/main/java/com/warped/ui/chat/voice/VoiceMessageRecorder.kt:202`
**Issue:** `destroy()` stops and releases the recorder but never deletes `outputFile`. When `ChatViewModel.onCleared()` destroys an in-progress recording, the partial `.m4a` remains in `filesDir/voice/` with no reference anywhere — it can never be sent, cancelled, or cleaned up. This is retained microphone audio with no owner (privacy-relevant) plus unbounded storage growth across sessions.
**Fix:**
```kotlin
@Synchronized
fun destroy() {
    try {
        if (isRecording) {
            try { handle?.stop() } catch (_: RuntimeException) { }
        }
    } finally {
        try { handle?.release() } catch (e: Exception) {
            Timber.w(e, "VoiceMsg: destroy failed")
        } finally {
            handle = null
            isRecording = false
        }
        // Never leave a partial clip behind: destroy means discard,
        // not keep (keep only happens through stop()).
        try { outputFile?.takeIf { it.exists() }?.delete() } catch (_: Exception) { }
        outputFile = null
    }
}
```

## Warnings

### WR-01: `Thread.sleep()` inside a suspend function blocks an IO thread

**File:** `app/src/main/java/com/warped/ui/chat/voice/PcmTranscoder.kt:190`
**Issue:** The `INFO_TRY_AGAIN_LATER` backoff calls `Thread.sleep(2)` inside `transcodeFirst30s`, a `suspend` function running on `Dispatchers.IO`. This pins a pooled IO thread; combined with the 30 s wall-clock watchdog, a stalled codec can hold the thread for the full timeout. Use coroutine `delay()`.
**Fix:** Replace `Thread.sleep(2)` with `delay(2)` (add `import kotlinx.coroutines.delay`).

### WR-02: Decoder output assumed to be 16-bit PCM without checking

**File:** `app/src/main/java/com/warped/ui/chat/voice/PcmTranscoder.kt:161-170`
**Issue:** Output buffers are read as little-endian shorts unconditionally. If a device decoder emits `ENCODING_PCM_FLOAT` (`KEY_PCM_ENCODING`), the bytes are misinterpreted as 16-bit samples — garbage audio is silently sent to the model with no error. The code already reads the actual sample rate/channels from the output format; encoding deserves the same treatment.
**Fix:** After `INFO_OUTPUT_FORMAT_CHANGED`, read `KEY_PCM_ENCODING`; throw `TranscodeException("unsupported PCM encoding")` for anything other than `ENCODING_PCM_16BIT`, or convert float samples properly.

### WR-03: Amplitude sampler performs binder IPC on the Main thread every 100 ms

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:1834`
**Issue:** `voiceSessionJob` is launched in `viewModelScope` (Main dispatcher), so the 100 ms sampler calls `recorder.maxAmplitude()` — a MediaRecorder binder IPC — on the UI thread for the whole recording, contradicting the phase's own "never blocks the UI thread" claim. `start()`/`stop()` correctly run on `Dispatchers.IO`, but this poller does not.
**Fix:** Launch the sampler child on `Dispatchers.Default` (or `.IO`): `launch(Dispatchers.Default) { while (true) { delay(100); _voiceAmplitude.value = recorder.maxAmplitude() } }`.

### WR-04: `pendingVoiceRequest` lost on rotation, grant result becomes a silent no-op

**File:** `app/src/main/java/com/warped/ui/chat/ChatScreen.kt:153`
**Issue:** `pendingVoiceRequest` uses `remember`, not `rememberSaveable`. Rotating while the rationale dialog is shown or while the system permission dialog is up destroys the composition state; when the grant returns, `request` is null and the launcher does `null -> Unit` — recording never starts and the user gets zero feedback. (The adjacent `voiceRationaleSeen` correctly uses `rememberSaveable`.)
**Fix:** `var pendingVoiceRequest by rememberSaveable { mutableStateOf<PendingVoiceRequest?>(null) }` — the enum is Parcelable-compatible via `rememberSaveable` only if annotated; otherwise persist as `String?`/`Int?` ordinal and map back.

### WR-05: Stop tapped during recorder spin-up is swallowed, recording runs on

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:1812-1850`
**Issue:** `startVoiceRecording()` flips `_isVoiceRecording` on a `Dispatchers.IO` coroutine after `recorder.start()` returns. A stop tap in that window hits `stopVoiceRecording()`'s `if (!_isVoiceRecording.value) return` guard and is discarded; the start then completes and recording runs until the user taps again. The `voiceStarting` single-flight flag guards double-start but no pending-stop is recorded.
**Fix:** Track a `voiceStopRequested` flag set by stop/auto-stop paths and checked after platform accept in the IO coroutine (stop immediately if set), or move the flag flip before the platform call with rollback on failure.

## Info

### IN-01: Stale TODO(67-02) left behind after the work it describes shipped

**File:** `app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt:70`
**Issue:** The param comment still reads "TODO(67-02): adopt onAudioRecordingChanged for the recording flag + recording-row UI" although Plan 02 adopted exactly that channel (LaunchedEffect at line ~320). A stale TODO misdirects the next reader into re-doing finished work.
**Fix:** Delete the TODO line; keep the factual description of the toggle behavior.

### IN-02: Magic thresholds and locale-sensitive timer formatting

**File:** `app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt:160,185`
**Issue:** The 60 s cap, 50 s red threshold, and 32767 amplitude normalization are bare literals duplicated between `ChatViewModel` (60) and `ChatInputBar` (50, 32767f) — a cap change must land in two files. `"%d:%02d".format(...)` without an explicit locale renders non-Latin digits in some locales.
**Fix:** Hoist `VOICE_CAP_SEC = 60`, `VOICE_WARN_SEC = 50`, `MAX_AMPLITUDE = 32767f` into shared constants (e.g., companion on `VoiceMessageRecorder` or a `VoiceConstants` object); use `"...".format(Locale.US, ...)`.

## Fix notes (2026-10-02, gsd-code-fixer)

All 7 in-scope findings fixed, one commit per finding (`fix(67): …`), verified with
`:app:testDebugUnitTest` (956 tests, 0 failures) + `:app:assembleDebug` BUILD SUCCESSFUL.
Gates ran in the isolated worktree (see commit hashes), reproducible from `main` after fast-forward.

- CR-01 (bogus cap toast): `autoStopVoiceRecording(announceCap = true)` now emits only when a clip was kept; `ChatScreen` ON_PAUSE passes `announceCap = false`. Commits `88cfc4d9` (VM) + `b005d349` (Screen).
- CR-02 (orphaned partial on destroy): `destroy()` deletes `outputFile`; added `destroy deletes the partial clip` unit test. Commit `a7528f36`.
- WR-01 (`Thread.sleep` in suspend): replaced with `delay(2)` + import. Commit `217943ba`.
- WR-02 (PCM encoding assumed): `KEY_PCM_ENCODING` resolved at format-changed (plus lazy once-only read at first output buffer for codecs that report buffers first); anything non-16-bit throws `TranscodeException` (mapped to the existing transcode-failed Snackbar, never a crash). Absent key keeps the historical 16-bit behavior. Commit `d00aa094` — **requires human verification**: untestable on JVM (android.jar stubs); needs the on-device record→send smoke.
- WR-03 (amplitude IPC on Main): sampler child moved to `Dispatchers.Default`. Commit `60653fce`.
- WR-04 (intent lost on rotation): `pendingVoiceRequest` → `rememberSaveable` enum-name `String?` (all 6 read/write sites + launcher consume updated). Commit `cd9e2ed1` — **requires human verification**: rotation-across-grant needs a manual device pass.
- WR-05 (stop swallowed during spin-up): `pendingVoiceStop` (KEEP/DISCARD) recorded by stop/auto-stop/cancel when `voiceStarting`, consumed post-accept on the IO coroutine; cleared on failed start. Commit `16c95452` — **requires human verification**: concurrency timing, needs a manual rapid-tap pass.
- IN-01/IN-02: left open (info, outside the requested fix scope).

One process note: a mid-run `commit --amend` landed on the wrong commit and was repaired via patch-save + reset + cherry-pick; final stack verified single-file-per-commit (see hashes above).

---

_Reviewed: 2026-10-02T19:30:00Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
