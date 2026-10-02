# Quick Task: 32-bit ABI exclusion (abiFilters)

## Task (user decision, 2026-10-01)
`armeabi-v7a`/`x86` devices can install the app but `litertlm-android`
ships `.so` only for `arm64-v8a`/`x86_64` → guaranteed
`UnsatisfiedLinkError` on first local-model load (the 1.7.1 vitals crash
was this class). Chosen fix: `abiFilters` (vs runtime degradation —
rejected: 32-bit devices can't run multi-GB local models anyway, and
gating every local path is error-prone).

## Changes
1. `app/build.gradle.kts` `defaultConfig`: `ndk { abiFilters +=
   listOf("arm64-v8a", "x86_64") }` (x86_64 kept for emulators; Play
   serves per-device splits).
2. Side effects: smaller AAB (drops sqlcipher 32-bit slices + any other
   32-bit `.so`); Play device catalog shrinks on next upload
   (32-bit devices excluded).

## Verification
- `bundleRelease` green.
- AAB `lib/` contains ONLY `arm64-v8a`, `x86_64`.
- `scripts/check_elf_alignment.sh` on the AAB still passes.
- Commit on `main`, NO push.
