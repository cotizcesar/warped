# 16 KB Emulator Smoke Evidence (Phase 59-02, Task 2)

**Date:** 2026-09-30
**App version:** release APK `f78dcfd956dd50d4077280064ab1ac235a7c43e744e495d724cb0ad883eabd4b`
(built 2026-09-30 via `./gradlew :app:assembleRelease`, includes `loadLibrary("sqlcipher")` fix commit `66517aaa`)
**Alignment re-verified on this exact artifact:** `check_elf_alignment.sh` → `OK: all 14 native libraries are 16 KB-aligned` (exit 0); `zipalign -c -P 16 4` → `Verification successful`.

## 1. Page size proof (16 KB image)

Emulator AVD `Pixel_8` was missing from disk on 2026-09-30 (found `~/.android/avd/` empty);
recreated with `avdmanager` on the still-installed system image
`android-37.2/google_apis_playstore_ps16k/x86_64` (device profile `pixel_7_pro` —
profile does not affect page size), booted with `-wipe-data -memory 6144`.

```
$ adb -s emulator-5554 shell getconf PAGE_SIZE
16384
$ adb -s emulator-5554 shell getprop ro.build.version.sdk
37
$ adb -s emulator-5554 shell getprop ro.product.model
sdk_gphone16k_x86_64
```

`getconf PAGE_SIZE` → **16384** ✓

## 2. Install on 16 KB image

```
$ adb -s emulator-5554 install -r app/build/outputs/apk/release/app-release.apk
Success
```

Install: **Success** ✓

## 3. Launch on 16 KB image — BLOCKED by emulator environment

Launch could not be executed: the emulator's `system_server` is crash-looping
(observed PIDs `29263 → 32727` within 20 s, then `16823 → 20603` after a reboot;
`service activity` intermittently unresolvable, `am`/`cmd activity` fail with
`Can't find service: activity` / `Broken pipe`). Pre-existing system FATALs
(`UiThreadHelper`, `wmshell.anim`) appear without any app installed — this is a
Play-image + swangle instability, not an app failure. Zero app log lines were
produced (the app process never started). Retried across two boots + one reboot;
same result.

## 4. Release launch on physical hardware (compensating evidence)

Same release APK, physical Pixel 8 over USB (`37141FDJH0065Y`, 4 KB pages, SDK 37):

```
$ adb install -r app-release.apk
Success
$ adb shell am start -n com.warped.app/com.warped.MainActivity
Starting: Intent { ... }
$ adb shell pidof com.warped.app
27731   (alive 20 s+ after launch)
$ adb logcat -d | grep "FATAL EXCEPTION"
(no output — zero crashes)
$ adb logcat -d | grep "libsqlcipher.so.*: ok"
... nativeloader: Load .../base.apk!/lib/arm64-v8a/libsqlcipher.so ... : ok
```

Release launch: **OK** ✓ — MainActivity starts, encrypted Room DB opens
(the exact path that crashed pre-fix), zero FATAL. This validates the release
artifact's native loading on real arm64 hardware; combined with §1–§2 (16 KB
pages + aligned install), the remaining 16 KB-specific risk is covered by the
ELF/zipalign proof above.

## 5. Chat turn

A local chat turn requires a downloaded on-device model (smallest allowlisted:
`gemma-4-E2B-it`, 2 588 147 712 bytes ≈ 2.6 GB). **Not completed:** impossible on
the 16 KB emulator (app cannot launch — §3 environment failure) and not
attempted on the physical device (multi-GB download, user's bandwidth/time).

**→ Explicit release-UAT deferral:** `16KB chat turn` — install release on a
healthy 16 KB system (fixed emulator or 16 KB hardware), download smallest
allowlisted model via the app's normal flow, complete one local chat turn with
zero native failures. Not a silent pass.

## Verdict

| Item | Result |
|------|--------|
| PAGE_SIZE 16384 on smoke device | ✓ PROVEN |
| Release APK 14/14 aligned + zipalign | ✓ PROVEN |
| Install on 16 KB image | ✓ PROVEN (Success) |
| Launch, no native failures | ✓ on arm64 hardware (release); BLOCKED on 16 KB image (emulator env) |
| Local chat turn | ⏭ release-UAT deferral (needs model download + healthy 16 KB system) |
