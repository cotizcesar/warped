---
phase: 59-16kb-dependency-verification
plan: "01"
subsystem: native-16kb-alignment
tags: [16kb, elf-alignment, sqlcipher, litertlm, zipalign, release-gate]
dependency_graph:
  requires: []
  provides: [check_elf_alignment.sh, 16KB-alignment-evidence, sqlcipher-android-4.19.1]
  affects: [59-02-gradle-ci-gate, 59-02-emulator-smoke]
tech_stack:
  added: [net.zetetic:sqlcipher-android:4.19.1]
  patterns: [ELF PT_LOAD p_align gate via readelf -lW, version-bump-only remediation]
key_files:
  created: [scripts/check_elf_alignment.sh, .planning/phases/59-16kb-dependency-verification/59-16KB-ALIGNMENT-EVIDENCE.md]
  modified: [gradle/libs.versions.toml, app/src/main/java/com/warped/di/DatabaseModule.kt, app/lint.xml]
decisions:
  - "Migrated android-database-sqlcipher 4.5.4 -> sqlcipher-android 4.19.1 (successor artifact) instead of same-artifact bump: 4.5.4 is latest/EOL with no 16KB build; dependency-only fix preserves PAGE-04"
  - "Removed Aligned16KB lint ignore so the check fails closed going forward"
metrics:
  duration: "~25 min (incl. release rebuild + targeted unit tests)"
  completed: "2026-09-30"
---

# Phase 59 Plan 01: 16 KB Alignment Evidence Summary

Proved every shipped native library is 16 KB-aligned (PAGE-01) with a deterministic gate script and per-library evidence, remediating the one misaligned dependency by version change only (PAGE-04).

## What was built

- `scripts/check_elf_alignment.sh` (executable): unzips a release AAB/APK (default `app-release.aab`, falls back to APK), runs `readelf -lW` on every `.so`, requires all `PT_LOAD` segments `p_align >= 16384`; prints `ALIGNED|MISALIGNED <path>` per library, exits 1 on any failure. Handles AAB (`base/lib/`) and APK (`lib/`) layouts.
- `59-16KB-ALIGNMENT-EVIDENCE.md`: artifact paths + SHA-256, full 14-library ALIGNED table, `zipalign -c -P 16` pass, versions verified, remediation record.
- Remediation: `net.zetetic:android-database-sqlcipher:4.5.4` -> `net.zetetic:sqlcipher-android:4.19.1`; `DatabaseModule` `SupportFactory` -> `SupportOpenHelperFactory`; `Aligned16KB` lint ignore removed.

## Verification (all green)

- `./scripts/check_elf_alignment.sh` on fresh `bundleRelease` output: **14/14 ALIGNED, exit 0**.
- `zipalign -c -P 16 4 app-release.apk`: **Verification successful** (14/14 `.so` OK).
- No `pageSizeCompat` / linker-flag workaround present (grep-verified).
- Migration safety: 19 DB migration unit tests pass (`Migration14To15/15To16/16To17StaticTest`, 0 failures); release `bundleRelease + assembleRelease` BUILD SUCCESSFUL.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Script used `readelf -l` (wrapping) instead of `readelf -lW`**
- **Found during:** Task 2, first run (9 false MISALIGNED: litertlm, graphics.path, datastore on 64-bit ABIs)
- **Issue:** Without `-W`, 64-bit segment rows wrap so `awk $NF` grabbed VirtAddr (`0x0`), not Align — every 64-bit `.so` falsely failed.
- **Fix:** Switched to `readelf -lW` (single-line rows, `$NF` = Align); re-ran — only genuinely misaligned `libsqlcipher.so` (0x1000) remained.
- **Files modified:** `scripts/check_elf_alignment.sh`
- **Commit:** folded into 6f2e5f7f (script commit)

**2. [Rule 3 - Blocking] Same-artifact sqlcipher bump impossible; migrated to successor artifact**
- **Found during:** Task 2 remediation
- **Issue:** Plan assumed a newer 16 KB-aligned `android-database-sqlcipher` release; Maven Central confirms 4.5.4 is latest and the Community Edition is EOL (Zetetic 2025-06-26) — bump path does not exist, blocking PAGE-01.
- **Fix:** Migrated to Zetetic's official successor `net.zetetic:sqlcipher-android:4.19.1` (pre-verified 0x4000 on all ABIs by extracting the AAR). Dependency-only change: version catalog coordinate + 2-line `DatabaseModule` API rename (`SupportOpenHelperFactory` keeps the same `ByteArray` constructor and Room factory contract; same DB format, existing encrypted DBs open transparently). No `.so` patching, no linker flags, no `pageSizeCompat` — PAGE-04 spirit preserved.
- **Files modified:** `gradle/libs.versions.toml`, `DatabaseModule.kt`, `app/lint.xml`
- **Commit:** a10ac7dd

## Commits

- `6f2e5f7f` feat(59-01): add check_elf_alignment.sh 16KB ELF gate script
- `a10ac7dd` fix(59-01): migrate sqlcipher to 16KB-aligned sqlcipher-android 4.19.1

## Self-Check: PASSED

- Script exists, executable, `bash -n` clean, exits 0 on release AAB: FOUND
- Evidence file with 14 ALIGNED + zipalign pass + sha256: FOUND
- Commits 6f2e5f7f, a10ac7dd exist, no unintended deletions: VERIFIED
