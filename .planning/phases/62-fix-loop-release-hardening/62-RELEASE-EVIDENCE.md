# 62 Release Evidence (REL-01)

**Date:** 2026-10-01
**Tree:** beta @ final (see fix SHAs below — all tonight's fixes included)

## Beta-fix completeness (verified present, not assumed)

| Fix | Commit | Proof |
|-----|--------|-------|
| sqlcipher `loadLibrary` | `66517aaa` (Phase 59) | `grep -c loadLibrary DatabaseModule.kt` → 1 |
| Audio slot gating | `50dcb5aa` | `resolveAudioBackend(target` ×3 in EngineManager |
| Vision slot gating | `a2d0e131` | same file, null-when-absent |
| Same-language reply | `c5614e82` | IDENTITY_LINE + pinned test |
| Toggle removal + auto-select | `1dcdea26`, `6f92922f` | Switch gone, pickAutoSelectModel |
| Catalog sort + verified gating | `a7deabd4` | sortedBy sizeInBytes, verifiedLocalCapabilities ×5 |
| 62-01 regression lock | `dec5dbb8`, `ebe38148` | 32 new tests |

## Artifacts (fresh build 2026-10-01, `./gradlew bundleRelease assembleRelease`)

| Artifact | SHA-256 |
|----------|---------|
| `app-release.aab` | `082552161a1c1300b7b247fb5a91a753d75c901f9ec659e88abad9ce1ce644d0` |
| `app-release.apk` | `a25b679e0b3cdebc24eb786beda2d29caf610cc40b6849bb7183c2b3402032d6` |

- R8: BUILD SUCCESSFUL, no errors (63 tasks, config cache stored).
- Signing: release keystore `app/keystore/warped-release.jks` via untracked
  `local.properties` (secrets never logged, never committed — T-62-02).

## Gate outputs (exact artifacts above)

- `check_elf_alignment.sh` (AAB): `OK: all 14 native libraries are 16 KB-aligned` (exit 0).
- `zipalign -c -P 16 4` (APK): `Verification successful`.
- `verify16KbAlignment` Gradle task: wired into `check` (Phase 59-02, unchanged).
- LeakCanary in release APK: `unzip -l | grep -ci leakcanary` → **0**.
- `largeHeap`: `android:largeHeap="true"` is pre-existing application config
  for on-device LLM weights (predates v2.5) — not introduced as a leak fix.

## Zero-leak evidence (gate 4)

- Phase 61 baseline: 6/6 tour legs clean on Pixel 8 hardware, zero findings
  (`61-LEAK-BASELINE.md`).
- Phase 62-01: 32 regression tests (engine lifecycle, inference cancel,
  grounding scope, observers/singletons) — full suite **932/932 green**.

## Hardware smoke (gate 3)

- Release on physical Pixel 8 arm64 (Phase 59 evidence): install Success,
  MainActivity alive, zero FATAL, `libsqlcipher.so ok`, encrypted DB opens.
- 16 KB emulator (emulator-5556, `sdk_gphone16k_x86_64`, PAGE_SIZE 16384):
  see section below (in progress at write time).
- Phone's current debug install + user data (2.6 GB E2B) deliberately
  untouched — release/debug signatures differ and an uninstall would wipe
  user data without consent.

## 16 KB release smoke (emulator-5556) — COMPLETE ✅ 2026-10-01

- Uninstalled debug, installed release APK `a25b679e…` → Success.
- Launch: PID alive, **0 FATAL**, `libsqlcipher.so` (x86_64) nativeloader OK.
- Downloaded `gemma-3-1b-it` (584 417 280 bytes) via the normal catalog
  flow (Worker result SUCCESS).
- **Local chat turn completed on the 16 KB system**: `hola` →
  `Hola! ¿Qué tal? ¿Cómo te puedo ayudar hoy?` (Spanish — the same-language
  rule working), zero native failures in logcat (no FATAL, no
  UnsatisfiedLinkError, no dlopen failures, no NOT_FOUND encoders).
- **G-59-01 CLOSED**: install ✓ + launch ✓ + local chat turn ✓ on
  PAGE_SIZE=16384 with the aligned release artifact.
