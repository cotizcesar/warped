---
status: complete
date: 2026-10-01
branch: main
pushed: false
---

# Summary: 32-bit ABI exclusion (abiFilters)

## Done
- `app/build.gradle.kts` `defaultConfig`: `ndk { abiFilters +=
  listOf("arm64-v8a", "x86_64") }` with rationale comment.
- Verified: `bundleRelease` green; AAB `base/lib/` contains ONLY
  `arm64-v8a` + `x86_64`; `check_elf_alignment.sh` → all 8 native libs
  16KB-aligned OK.
- Committed on `main`, no push.

## Effect
- 32-bit devices (`armeabi-v7a`, `x86`) can no longer install the app
  → the `UnsatisfiedLinkError`-on-model-load crash class (1.7.1 vitals)
  is structurally impossible going forward.
- Smaller AAB (32-bit sqlcipher slices etc. dropped). Play device
  catalog will shrink on next upload — expected.
