# Phase 67 Deferred Items

## Release-UAT / Device-Smoke (from 67-02 Task 4 checkpoint)

**Status:** DEFERRED — no audio-capable hardware available in this execution environment.

**What requires a physical device:** record → send → on-device model response against an audio-capable allowlist model, covering:
1. Live timer + amplitude in the recording row; manual stop keeps the clip; send returns a model response on-device.
2. 60 s auto-stop keeps the clip with a once-per-event "60s limit reached" toast (never auto-sends, never discards).
3. A >30 s clip shows the "First 30s sent to model" Snackbar and still sends.
4. Cancel (X) discards the file immediately (during recording and after stop).
5. RECORD_AUDIO transient denial shows the Snackbar without crashing; permanent denial shows the Settings-escape Snackbar.
6. Backgrounding mid-recording auto-stops and keeps the clip; rotation preserves recording state.

**What WAS verified without hardware (evidence in VERIFICATION.md):**
- Recorder state machine: 7 JVM unit tests green (failed-start, stop-without-start, cancel-deletes, double-destroy, double-start-ignored, stop-failure).
- PCM math: 8 JVM unit tests green (stereo downmix, 44.1k/48k→16k lengths within 1%, truncate boundary flag, empty input).
- Guard behavior: 3 JVM tests green (text-only + audioBytes hits error_no_audio backstop; blank send no-op; cancel-after-stop deletes file on disk).
- Full unit suite: 955 tests, 0 failures (`:app:testDebugUnitTest`, 2026-10-02).
- Debug build assembles (`:app:assembleDebug` BUILD SUCCESSFUL).
- Zero hardcoded voice copy in main sources; 9 voice_msg_* strings EN+ES.
- MediaCodec decode loop is device-smoke covered by design (stated in code + plan); the watchdog/timeout/TranscodeException contract is what the smoke asserts.

**Suggested release-UAT runbook:** install debug APK on a mic-capable device with an audio-capable allowlist model downloaded; grant RECORD_AUDIO; record 5 s → stop → send with caption → confirm response; record 35 s → send → confirm First-30s note + response; record → background at 10 s → foreground → confirm kept clip + toast only at 60 s cap; deny permission transiently and permanently → confirm Snackbars, no crash.

## Pre-existing Test Flake (out of scope, not fixed)

**`ModelSwitchUnloadTest.failed mount surfaces error and keeps draft` fails intermittently in full-package runs.**
- Fails 3/3 `com.warped.ui.chat.*` runs with Phase 67 changes AND 1/2 runs on stashed base sources (same assertion, line 321); passes in isolation in both states; passed in the final full-suite gate (955/955).
- Root cause hypothesis: the test polls a `Dispatchers.Default` hop with virtual-time delays (self-documented race in the test comment); outcome depends on background-thread timing under suite load.
- Touched files: none in Phase 67 scope. Revisit in a test-hardening pass, not here.
