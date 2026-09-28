---
phase: 45-foundation-refresh
reviewed: 2026-09-27T08:00:00Z
depth: standard
files_reviewed: 11
files_reviewed_list:
  - gradle/libs.versions.toml
  - app/build.gradle.kts
  - gradle.properties
  - gradle/wrapper/gradle-wrapper.properties
  - scripts/audit-dependencies.sh
  - app/proguard-rules.pro
  - app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt
  - app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt
  - app/src/main/java/com/warped/data/local/inference/LiteRtLmCache.kt
  - app/src/main/java/com/warped/data/local/inference/LiteRtLmCacheManager.kt
  - app/src/main/java/com/warped/data/repository/ModelAllowlistRepository.kt
  - app/src/main/assets/model_allowlist.json
  - app/src/test/java/com/warped/data/repository/ModelAllowlistTest.kt
  - app/src/test/java/com/warped/data/local/inference/LiteRtLmCacheTest.kt
findings:
  critical: 2
  warning: 9
  info: 2
  total: 13
status: issues_found
---

# Phase 45: Code Review Report

**Reviewed:** 2026-09-27T08:00:00Z
**Depth:** standard
**Files Reviewed:** 14 (11 source + catalog/build/audit/test scope; wrapper properties confirmed unchanged content except version)
**Status:** issues_found

## Summary

Reviewed the Phase 45 Foundation Refresh scope (45-01 catalog bumps + rescoped audit, 45-02 LiteRT-LM 0.17.1 migration + cache helper + model allowlist + tests). The catalog work is disciplined (holds documented, per-group gates), the new pure-Kotlin helpers are clean and well-tested, and the allowlist's verified-only default-false posture is correct.

Two BLOCKERs must be fixed: (1) the engine `companion init` calls `Engine.setNativeMinLogSeverity()` outside the `loadLibrary` try/catch, turning a recoverable missing-native-lib into an uncatchable `ExceptionInInitializerError` that poisons the class permanently; (2) the new `model_allowlist.json` advertises `.task` models while `LiteRTLmProvider`'s on-demand load gate only accepts paths ending in `.litertlm`, so selecting an allowlisted model through that path yields "Select a .litertlm model first." Warnings cover a JNI-catch fall-through in the provider flow, a dead-engine leak on failed init, silent NPU vision/audio downgrade, unchecked `mkdirs`, cache filename collisions, audit-script pre-release gaps, hardcoded version strings drifting from the catalog, and global R8 `-dontoptimize`/`-dontobfuscate`.

## Critical Issues

### CR-01: Native-lib failure poisons class via unguarded setNativeMinLogSeverity

**File:** `app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt:24-31`
**Issue:** Only `System.loadLibrary("litertlm_jni")` is wrapped in try/catch. `Engine.setNativeMinLogSeverity(LogSeverity.ERROR)` on line 29 executes unconditionally afterward and is itself a JNI call. If the `.so` is missing (x86 emulator without the ABI, corrupt install, upgrade race), the log call throws `UnsatisfiedLinkError` from inside the `companion object init` block, producing `ExceptionInInitializerError`. The class is then permanently poisoned for the process lifetime — every later `LiteRTLmEngine` reference throws, including the recovery path in `LiteRTLmProvider.recoverEngine()`. A recoverable "native lib missing" becomes an unrecoverable process-level crash loop.
**Fix:**
```kotlin
companion object {
    init {
        try {
            System.loadLibrary("litertlm_jni")
            Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
        } catch (e: UnsatisfiedLinkError) { Timber.e(e, "LiteRTLmEngine: native lib not found") }
    }
}
```

### CR-02: Allowlist advertises `.task` models the provider refuse to auto-load

**File:** `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt:75` vs `app/src/main/assets/model_allowlist.json:12,30`
**Issue:** Both allowlisted entries use `.task` files (`gemma-3n-E2B-it-int4.task`, `gemma-3n-E4B-it-int4.task`), but the provider's on-demand engine-load gate requires `modelPath.endsWith(".litertlm", ignoreCase = true)`. A user who selects an allowlisted `.task` model through a path where no engine is loaded yet falls into the `else` branch and gets `StreamToken.Error("No LiteRT-LM engine is loaded. Select a .litertlm model first.")` — the phase's own curated models are rejected by its own gate. `EngineManager.switchToLiteRT()` documents `.litertlm` only, so either the engine accepts `.task` and the gate is wrong, or the allowlist ships files the engine cannot load. Either way this contradiction is new in 45-02 and must be resolved before ship.
**Fix:**
```kotlin
// Accept both engine-supported containers (verify against Engine docs which apply):
if (modelPath != null && (modelPath.endsWith(".litertlm", ignoreCase = true) ||
    modelPath.endsWith(".task", ignoreCase = true))) {
```
And update the `else` error string and `EngineManager.switchToLiteRT()` KDoc to name both extensions. If the 0.17.1 `Engine` genuinely rejects `.task`, the allowlist entries themselves are wrong and must be replaced with loadable files.

## Warnings

### WR-01: JNI catch emits Error but falls through instead of returning

**File:** `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt:78-80`
**Issue:** The `catch (e: LiteRtLmJniException)` branch emits `StreamToken.Error` but, unlike the generic `catch` two lines below it, omits `return@flow`. Execution falls through to Step 3 with no engine loaded, builds a conversation config, and enters `sendContentsWithRetry`, which throws `IllegalStateException("Engine not initialized")` and burns through the 2-retry recovery loop (including a pointless `switchToLiteRT` recovery attempt) before emitting a second, misleading Error. One root cause produces two errors plus wasted native recovery.
**Fix:**
```kotlin
} catch (e: LiteRtLmJniException) {
    Timber.e(e, "LiteRTLmProvider: JNI native error — ${e.message}")
    emit(StreamToken.Error("LiteRT-LM native error: ${e.message ?: "Unknown JNI error"}"))
    return@flow
} catch (e: Exception) {
```

### WR-02: Failed init leaves dead Engine assigned (native handle leak)

**File:** `app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt:98-110`
**Issue:** `engine = Engine(config).also { e -> ... e.initialize() ... }` assigns the field before `initialize()` runs. If `initialize()` throws, the catch rethrows but `engine` still references a constructed-but-uninitialized native object that is never `close()`d. `isInitialized()` returns false so retry is possible, but each failed attempt leaks a native handle, and `close()` later closes only the most recent dead instance.
**Fix:**
```kotlin
val created = Engine(config)
try {
    created.initialize()
} catch (e: Exception) {
    try { created.close() } catch (_: Exception) { /* best effort */ }
    throw e
}
engine = created
```

### WR-03: NPU vision/audio backends silently downgraded to CPU

**File:** `app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt:89-94`
**Issue:** The main-backend branch adopts proper `Backend.NPU` (45-02's recorded choice), but the vision/audio mappers use `when (it) { GPU -> GPU(); else -> CPU() }`, silently converting an explicitly requested NPU vision/audio backend to CPU with no log. After 45-02's "adopt-proper-NPU" decision this is internally inconsistent: identical input (`BackendType.NPU`) means NPU for the main backend and undisclosed CPU for vision/audio.
**Fix:** Map NPU explicitly and log the choice, e.g. `BackendType.NPU -> Backend.NPU(nativeLibraryDir)` for vision/audio as well, or `Timber.w("vision/audio NPU unsupported, falling back to CPU")` if the downgrade is intentional.

### WR-04: `mkdirs()` results ignored on cache dirs

**File:** `app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt:81-84`, `app/src/main/java/com/warped/data/local/inference/LiteRtLmCacheManager.kt:20`
**Issue:** Both `init()` and the `cacheRoot` initializer call `mkdirs()` and discard the Boolean. On a full disk or a broken cache dir, the engine proceeds with an invalid `cacheDir` path into JNI, where failure surfaces as an opaque native crash instead of a catchable `IOException`.
**Fix:**
```kotlin
.also { if (!it.exists() && !it.mkdirs()) throw java.io.IOException("Cannot create cache dir: $it") }
```

### WR-05: Cache keyed by bare filename — same-name models collide

**File:** `app/src/main/java/com/warped/data/local/inference/LiteRtLmCacheManager.kt:22`
**Issue:** `cacheDirForModel` uses `File(modelPath).name`, so two distinct models with the same filename in different directories (e.g. an older and newer `gemma-3n-E2B-it-int4.task`, or user-imported duplicates) share one compiled-cache slot. The second load silently reuses or overwrites the first model's mmap state — a correctness hazard, not just eviction imprecision.
**Fix:** Key by a stable disambiguator, e.g. `File(cacheRoot, "${modelPath.hashCode()}-$name")`, or store a sidecar file recording the full source path + size and invalidate on mismatch.

### WR-06: Audit gate misses beta/RC pre-releases

**File:** `scripts/audit-dependencies.sh:55`
**Issue:** Step 2 greps the release graph only for `SNAPSHOT|alpha`, but the catalog's own history shows `-beta` artifacts ship (splash `1.2.0-beta01` was held back for exactly this rule). A `-beta01` or `-rc1` artifact in `releaseRuntimeClasspath` passes the gate as AUDIT-CLEAN despite the "never-alpha" (i.e. no-pre-release) policy intent.
**Fix:**
```bash
if echo "$REPORT" | grep -iE 'SNAPSHOT|alpha|beta|rc[0-9]|cr[0-9]|-m[0-9]'; then
```
Tune to avoid false positives (e.g. require version-position matching), but beta/RC must be covered.

### WR-07: Hardcoded versions bypass the catalog (drift risk)

**File:** `app/build.gradle.kts:104,181`
**Issue:** `implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.3.20"))` hardcodes the Kotlin version instead of `libs.versions.kotlin`, and `testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.4")` hardcodes the launcher instead of a catalog ref. Both duplicate values the catalog owns (kotlin `2.3.20`, junit5 `5.14.4` lockstep). The next bump of either catalog entry silently desynchronizes these — exactly the class of drift the version catalog exists to prevent.
**Fix:** Add catalog entries (`kotlin-bom`, `junit-platform-launcher`) and reference them: `implementation(platform(libs.kotlin.bom))`, `testRuntimeOnly(libs.junit.platform.launcher)`.

### WR-08: KSP 2.3.12 vs Kotlin 2.3.20 version skew is unverified

**File:** `gradle/libs.versions.toml:2,25`
**Issue:** KSP historically versions in lockstep with the Kotlin compiler (`<kotlin-version>-<ksp-release>`); here KSP is `2.3.12` against Kotlin `2.3.20`. The summary asserts "new unified 2.3.x versioning" but cites no evidence the KSP plugin `2.3.12` supports compiler `2.3.20`. If skew is real, annotation processing (Room/Hilt) can fail or silently mis-generate on the next compiler edge. Suite-green today does not prove the pairing is supported.
**Fix:** Confirm the supported pairing from the KSP release notes / plugin marker (`com.google.devtools.ksp:2.3.20-*` should exist); either align `ksp = "2.3.20-<release>"` or record the registry proof that `2.3.12` is decoupled and supports the 2.3.20 compiler.

### WR-09: Global `-dontoptimize` / `-dontobfuscate` disables release hardening

**File:** `app/proguard-rules.pro:38-39`
**Issue:** Two global flags turn off R8 optimization and obfuscation for the entire app — including API keys, endpoint URLs, and JNI glue the threat model cares about — while the surrounding broad `-keep` rules already preserve everything needed. If these lines predate 45-02 they are still in the phase's touched file and interact directly with 45-02's R8-verification claim: `assembleRelease` passing with optimization disabled proves far less than claimed.
**Fix:** Remove both lines; if a specific 0.17.1 native crash requires them, scope the exemption narrowly (e.g. `-keep` the crashing class) with a comment citing the stack trace, and re-verify `assembleRelease`.

## Info

### IN-01: `isIsolated` second condition is redundant; version unvalidated

**File:** `app/src/main/java/com/warped/data/local/inference/LiteRtLmCache.kt:37-39`
**Issue:** When `staleVersion != currentVersion`, the two `namespaceFor()` strings necessarily differ (version is interpolated verbatim), so the second conjunct adds nothing. More usefully, `namespaceFor()` interpolates raw input into a filesystem path with no validation — a `..` or `/` in a version string escapes the namespace. Risk is negligible today (input is `BuildConfig.LITERTLM_VERSION`), but a `require` costs one line.
**Fix:** `require(!engineVersion.contains("/") && !engineVersion.contains("..")) { ... }` in `namespaceFor()`.

### IN-02: Tests read the asset via a CWD-relative path

**File:** `app/src/test/java/com/warped/data/repository/ModelAllowlistTest.kt:20-21`
**Issue:** `java.io.File("src/main/assets/model_allowlist.json")` assumes the JVM working dir is the module dir. True under default Gradle `Test` config, but any `workingDir` customization or IDE-runner difference breaks all three shipped-asset tests with `FileNotFoundException` instead of a meaningful assertion.
**Fix:** Load via classloader resource or set `workingDir` explicitly on the `Test` task; alternatively fail with `assume` + message when the file is absent.

---

_Reviewed: 2026-09-27T08:00:00Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
