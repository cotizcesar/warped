# 16 KB Alignment Evidence (Phase 59-01)

**Date:** 2026-09-30
**Script:** `scripts/check_elf_alignment.sh` (exit 0 = all aligned)
**Method:** unzip release artifact → `readelf -lW` on every `.so` → every `PT_LOAD` segment must have `p_align >= 16384 (0x4000)`

## Verified artifacts

| Artifact | Path | SHA-256 |
|----------|------|---------|
| Release AAB (ELF check) | `app/build/outputs/bundle/release/app-release.aab` | `9533a5ed2a57f9695773a534c1da99f5cdb07b9946480f7b4ece34c45c2b1e75` |
| Release APK (zipalign check) | `app/build/outputs/apk/release/app-release.apk` | `73f8886661b747615c556476b298e568a559e751c1d8a748d4a59d23c50b642c` |

Both built 2026-09-30 via `./gradlew bundleRelease assembleRelease` (BUILD SUCCESSFUL, signed with `app/keystore/warped-release.jks`).

## Dependency versions verified

| Dependency | Version | Coordinate | Natives | Verdict |
|------------|---------|------------|---------|---------|
| litertlm-android | 0.17.1 (unchanged — latest on Google Maven) | `com.google.ai.edge.litertlm:litertlm-android` | `liblitertlm_jni.so` (arm64-v8a, x86_64) | ALIGNED as-shipped, no change needed |
| sqlcipher-android | **4.19.1** (migrated, see below) | `net.zetetic:sqlcipher-android` | `libsqlcipher.so` (all 4 ABIs) | ALIGNED after migration |
| androidx.graphics.path (transitive) | via Compose BOM 2026.06.01 | — | `libandroidx.graphics.path.so` (all 4 ABIs) | ALIGNED as-shipped |
| androidx.datastore (transitive) | 1.2.1 | — | `libdatastore_shared_counter.so` (all 4 ABIs) | ALIGNED as-shipped |

## Per-library ELF alignment (AAB)

```
ALIGNED base/lib/arm64-v8a/libandroidx.graphics.path.so
ALIGNED base/lib/arm64-v8a/libdatastore_shared_counter.so
ALIGNED base/lib/arm64-v8a/liblitertlm_jni.so
ALIGNED base/lib/arm64-v8a/libsqlcipher.so
ALIGNED base/lib/armeabi-v7a/libandroidx.graphics.path.so
ALIGNED base/lib/armeabi-v7a/libdatastore_shared_counter.so
ALIGNED base/lib/armeabi-v7a/libsqlcipher.so
ALIGNED base/lib/x86_64/libandroidx.graphics.path.so
ALIGNED base/lib/x86_64/libdatastore_shared_counter.so
ALIGNED base/lib/x86_64/liblitertlm_jni.so
ALIGNED base/lib/x86_64/libsqlcipher.so
ALIGNED base/lib/x86/libandroidx.graphics.path.so
ALIGNED base/lib/x86/libdatastore_shared_counter.so
ALIGNED base/lib/x86/libsqlcipher.so
OK: all 14 native libraries are 16 KB-aligned
```

## zipalign check (APK)

Command: `$SDK/build-tools/36.0.0/zipalign -c -P 16 4 app/build/outputs/apk/release/app-release.apk`

Result: `Verification successful` — all 14 `.so` entries `(OK)` at 16 KB page alignment.

## Remediation record (PAGE-04, version-bump-only)

Initial run against the pre-migration artifact found `libsqlcipher.so` MISALIGNED
(`p_align 0x1000`) on all 4 ABIs. Remediation, dependency-only — no `.so`
hand-patch, no linker flags, no `pageSizeCompat` (grep-verified absent):

- **Same-artifact bump impossible:** `net.zetetic:android-database-sqlcipher:4.5.4`
  is the latest release on Maven Central; the Community Edition is EOL (2023)
  and will never ship a 16 KB-aligned build (Zetetic blog 2025-06-26).
- **Fix applied:** migrated to Zetetic's official successor artifact
  `net.zetetic:sqlcipher-android:4.19.1` (latest stable on Maven Central;
  16 KB-aligned `0x4000` on all ABIs since 4.6.1 — verified by extracting the
  AAR and running `readelf -lW` before touching the build).
  - `gradle/libs.versions.toml`: version `4.5.4` → `4.19.1`, coordinate
    `android-database-sqlcipher` → `sqlcipher-android`.
  - `app/.../di/DatabaseModule.kt`: `net.sqlcipher.database.SupportFactory`
    → `net.zetetic.database.sqlcipher.SupportOpenHelperFactory` (same
    `ByteArray` passphrase constructor, same Room
    `SupportSQLiteOpenHelper.Factory` contract; same SQLCipher DB format, so
    existing encrypted databases open transparently).
  - `app/lint.xml`: removed the `Aligned16KB` severity-ignore (its comment
    anticipated exactly this upgrade); the lint check is now enforced.
- **Re-verification:** rebuilt release, script exits 0 (table above),
  `zipalign -c -P 16` passes (above).

## Threat-register notes (T-59-01 / T-59-02)

- T-59-01 (Maven AAR spoofing): versions pinned in `gradle/libs.versions.toml`
  (`litertlm 0.17.1`, `sqlcipher 4.19.1`); this file evidences the exact
  verified builds.
- T-59-02 (artifact tampering between build and check): SHA-256 of both
  verified artifacts recorded above; CI re-runs the script on its own build
  output (Phase 59-02 gate).
