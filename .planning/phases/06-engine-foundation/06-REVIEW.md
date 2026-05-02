---
phase: 06-engine-foundation
reviewed: 2026-05-02T00:00:00Z
depth: quick
files_reviewed: 13
files_reviewed_list:
  - app/src/main/java/com/warped/data/local/inference/BackendDetector.kt
  - app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt
  - app/src/main/java/com/warped/data/local/inference/EngineManager.kt
  - app/src/main/java/com/warped/data/local/db/Migrations.kt
  - app/src/main/java/com/warped/data/local/db/entity/LocalModelEntity.kt
  - app/src/main/java/com/warped/data/local/db/AppDatabase.kt
  - app/src/main/java/com/warped/di/DatabaseModule.kt
  - app/src/main/java/com/warped/di/InferenceModule.kt
  - app/src/main/java/com/warped/data/local/inference/MemoryChecker.kt
  - gradle/libs.versions.toml
  - app/build.gradle.kts
  - app/proguard-rules.pro
  - app/src/main/AndroidManifest.xml
findings:
  critical: 0
  warning: 9
  info: 5
  total: 14
status: issues_found
---

# Phase 06: Engine Foundation — Code Review Report

**Reviewed:** 2026-05-02
**Depth:** quick (pattern-matching + targeted crash/thread-safety analysis)
**Files Reviewed:** 13
**Status:** issues_found — 9 warnings, 5 info items, 0 critical blockers

## Summary

Phase 06 introduces the local inference engine foundation: LlamaEngine (JNI wrapper), LiteRTLmEngine (MediaPipe/LiteRT-LM), EngineManager (coordinator), BackendDetector (GPU capability probe), and MemoryChecker (RAM headroom validation). Database migrations add `model_format` column support for dual-format model tracking (GGUF + .litertlm).

**Overall assessment:** The code is structurally sound — Hilt injection is correct, Room migrations are syntactically valid, and thread safety is addressed with `@Synchronized` + `@Volatile` guards on the key engine classes. No critical crash paths were found for debug builds. However, there are several **warning-level issues** around incomplete ProGuard coverage for JNI callbacks, destructive database migration fallback, engine state management gaps, and a misleading comment about library loading. The LiteRT-LM integration depends on an alpha dependency (`1.1.0-alpha06` for security-crypto) and an RC library (`litertlm:0.11.0-rc1`), which carries inherent stability risk.

---

## Warnings

### WR-01: `EngineManager.createLiteRTConversation` bypasses active-engine check

**File:** `app/src/main/java/com/warped/data/local/inference/EngineManager.kt:112-114`

**Issue:** The method delegates directly to `liteRTLmEngine.createConversation()` without verifying that LiteRT-LM is the currently active engine. If the active engine is llama.cpp (or nothing is loaded), this call flows through to `LiteRTLmEngine.createConversation()`, which throws `IllegalStateException("LiteRTLmEngine is not initialized")` — crashing the caller. The `EngineManager` itself should gate this.

**Fix:**
```kotlin
@Synchronized
fun createLiteRTConversation(config: ConversationConfig = ConversationConfig()): Conversation {
    val current = activeEngine
    require(current?.type == EngineType.LITE_RT_LM) {
        "Cannot create LiteRT-LM conversation: active engine is ${current?.type ?: "none"}"
    }
    return liteRTLmEngine.createConversation(config)
}
```

---

### WR-02: Force-unwrap on nullable `backend` in `switchToLiteRT`

**File:** `app/src/main/java/com/warped/data/local/inference/EngineManager.kt:47`

**Issue:** `target.backend!!` uses a force-unwrap on a field typed `BackendType?`. While `backendDetector.probeBackend()` always returns a non-null value, the `ActiveEngine` data class declares `backend` as nullable (`BackendType? = null`). The `!!` will compile but violates null-safety discipline. If `probeBackend()` is ever refactored to return null, this becomes a runtime NPE.

**Fix:** Either make `backend` non-null for `EngineType.LITE_RT_LM` (preferred — use a sealed class hierarchy instead of a nullable field), or add an explicit `requireNotNull`:
```kotlin
val backend = requireNotNull(target.backend) { "Backend must be set for LiteRT-LM engine" }
liteRTLmEngine.init(modelPath, backend)
```

---

### WR-03: `fallbackToDestructiveMigration()` enables silent data loss

**File:** `app/src/main/java/com/warped/di/DatabaseModule.kt:30`

**Issue:** `fallbackToDestructiveMigration()` is called on the Room database builder. If a future database version bump is missing a migration path, Room will silently destroy and recreate the database — losing all conversations, model metadata, and endpoints. This is acceptable during early development but dangerous for production builds. The `exportSchema = false` setting (see IN-02) means there is no automated migration testing to catch missing paths.

**Fix:**
```kotlin
Room.databaseBuilder(context, AppDatabase::class.java, "warped.db")
    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
    // .fallbackToDestructiveMigration()  // Remove before production builds
    .build()
```
Alternatively, conditionally include it only for debug builds:
```kotlin
val builder = Room.databaseBuilder(context, AppDatabase::class.java, "warped.db")
    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
if (BuildConfig.DEBUG) {
    builder.fallbackToDestructiveMigration()
}
builder.build()
```

---

### WR-04: `MemoryChecker.canLoadModel` checks file size, not runtime memory footprint

**File:** `app/src/main/java/com/warped/data/local/inference/MemoryChecker.kt:30-33`

**Issue:** The check `modelSizeBytes <= memInfo.availableBytes * 0.8` compares the model file's on-disk size against available RAM. In practice, GGUF and .litertlm models require **more RAM at runtime than their file size** — a Q4_K_M quantized 7B GGUF file is ~4GB but may need ~5GB of working memory for inference. Passing this check does not guarantee the model will load without OOM. The STACK.md notes this explicitly ("A 7B Q4_K_M GGUF needs ~4-5 GB RAM"), but the code doesn't apply a buffer factor for runtime overhead.

**Fix:** Apply a headroom multiplier (e.g., 1.3×) for runtime memory:
```kotlin
fun canLoadModel(modelSizeBytes: Long): Boolean {
    val memInfo = getMemoryInfo()
    val estimatedRuntimeBytes = modelSizeBytes * 1.3  // Runtime overhead buffer
    return estimatedRuntimeBytes <= memInfo.availableBytes * 0.8
}
```

---

### WR-05: `System.loadLibrary("OpenCL")` has permanent side effect during probing

**File:** `app/src/main/java/com/warped/data/local/inference/BackendDetector.kt:75-76`

**Issue:** `isOpenCLAvailable()` calls `System.loadLibrary("OpenCL")`. This **permanently loads** the native library into the process — it's not an availability probe, it's a load operation with side effects. If the library loads successfully, it stays resident for the process lifetime with no way to unload it. The comment on line 74 says "We use Class.forName to probe rather than System.loadLibrary which throws UnsatisfiedLinkError," but the code actually uses `System.loadLibrary` — the comment is wrong (see IN-01). On devices where OpenCL is available but incompatible with the app's usage, this pre-loading could cause conflicts.

**Fix:** Use a lightweight probe instead — check if the library file exists on the filesystem without loading it, or use `Runtime.getRuntime().loadLibrary()` wrapped in a try-catch. Better yet, delegate the GPU capability check to the LiteRT-LM library's own backend probing if it provides one:
```kotlin
private fun isOpenCLAvailable(): Boolean {
    return try {
        // Probe library availability without permanent load
        val paths = arrayOf("/vendor/lib64/libOpenCL.so", "/system/lib64/libOpenCL.so")
        paths.any { java.io.File(it).exists() }
    } catch (e: Exception) {
        false
    }
}
```

---

### WR-06: Missing ProGuard keep rules for JNI callback interface `LlamaEngine.TokenCallback`

**File:** `app/proguard-rules.pro:1-15`

**Issue:** The `LlamaEngine.TokenCallback` interface is passed to native code via `nativeGenerate(prompt, callback)`. The native implementation likely calls `onToken(token, done)` via JNI's `GetMethodID` / `CallVoidMethod`. While the default `proguard-android-optimize.txt` keeps classes with native methods (`-keepclasseswithmembernames`), and `includedescriptorclasses` may propagate to `TokenCallback`, this protection is indirect and fragile. If R8 renames `onToken` or `TokenCallback`, the JNI callback will fail with `NoSuchMethodError` at runtime.

**Fix:** Add an explicit keep rule for the callback interface:
```
# LlamaEngine JNI callback interface
-keep interface com.warped.data.local.inference.LlamaEngine$TokenCallback { *; }
```

---

### WR-07: `LlamaEngine.loadModel()` failure leaves engine in indeterminate state

**File:** `app/src/main/java/com/warped/data/local/inference/EngineManager.kt:68-73` (referencing `LlamaEngine.kt`)

**Issue:** In `switchToLlama`, after `unloadCurrent()` succeeds (clearing `activeEngine`), `llamaEngine.loadModel(modelPath)` is called. If it returns `false`, an `IllegalStateException` is thrown. However, the native `loadModel` call is implemented in C++ — a `false` return could mean the library partially allocated resources before failing. The native code in `LlamaEngine.nativeLoadModel` may have left GPU memory, file handles, or thread-local state allocated. Subsequent calls to `loadModel` or `unload` may behave unpredictably.

**Fix:** After a failed `loadModel`, always call `llamaEngine.unload()` to ensure the engine resets to a clean state:
```kotlin
val loaded = llamaEngine.loadModel(modelPath)
if (!loaded) {
    Timber.e("EngineManager: llama.cpp failed to load model; resetting engine")
    llamaEngine.unload()  // Force clean state
    throw IllegalStateException("Failed to load llama.cpp model: $modelPath")
}
```

---

### WR-08: `LiteRTLmEngine.getModelPath()` reads `@Volatile` field without `@Synchronized`

**File:** `app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt:90`

**Issue:** `getModelPath()` reads `loadedModelPath` (a `@Volatile` field) without synchronization. While `@Volatile` guarantees visibility of the latest write, it does NOT guarantee consistency between `engine` and `loadedModelPath`. After `close()` runs (which sets both to null), a concurrent `getModelPath()` caller could see a non-null model path while `isInitialized()` returns false — producing inconsistent views of engine state. The rest of the class uses `@Synchronized` for state transitions; this method should follow suit.

**Fix:**
```kotlin
@Synchronized
fun getModelPath(): String? = loadedModelPath
```

---

### WR-09: Missing `android:extractNativeLibs="false"` per project spec

**File:** `app/src/main/AndroidManifest.xml:5-12`

**Issue:** The STACK.md explicitly recommends `android:extractNativeLibs="false"` to save APK install size by keeping native libraries compressed in the APK. The AndroidManifest does not set this attribute, causing libraries (`libwarped_llama.so`, plus LiteRT-LM native libs) to be extracted to disk on install, doubling their on-disk footprint. On devices with limited storage, this can cause install failures.

**Fix:**
```xml
<application
    android:name=".WarpedApplication"
    android:allowBackup="false"
    android:largeHeap="true"
    android:extractNativeLibs="false"
    ...
```

---

## Info

### IN-01: Comment-code mismatch in `isOpenCLAvailable()`

**File:** `app/src/main/java/com/warped/data/local/inference/BackendDetector.kt:74-75`

**Issue:** The comment states "We use Class.forName to probe rather than System.loadLibrary which throws UnsatisfiedLinkError" but the code on line 76 actually uses `System.loadLibrary("OpenCL")`. The comment describes an approach that wasn't implemented.

**Fix:** Update the comment to match the code, or implement the `Class.forName` approach and remove `System.loadLibrary`.

---

### IN-02: `exportSchema = false` contradicts `room.schemaLocation` KSP argument

**File:** `app/src/main/java/com/warped/data/local/db/AppDatabase.kt:25` and `app/build.gradle.kts:142`

**Issue:** The Room database annotation sets `exportSchema = false`, which prevents Room from generating schema JSON files. Meanwhile, `build.gradle.kts` configures `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` — directing Room WHERE to export schemas. With `exportSchema = false`, the KSP argument is ignored and no schemas are generated. This means automated migration tests (e.g., `MigrationTestHelper`) cannot verify migration correctness against golden schemas.

**Fix:** Set `exportSchema = true` and commit the generated schema JSON files to version control:
```kotlin
@Database(
    entities = [...],
    version = 7,
    exportSchema = true
)
```

---

### IN-03: Hardcoded magic number for `EGL_OPENGL_ES2_BIT`

**File:** `app/src/main/java/com/warped/data/local/inference/BackendDetector.kt:55`

**Issue:** `4 /* EGL_OPENGL_ES2_BIT */` uses a literal integer with a comment instead of a named constant. The Android SDK's `EGL14` class does not expose `EGL_OPENGL_ES2_BIT` as a public constant, so a local constant would improve readability and searchability.

**Fix:** Extract a private constant:
```kotlin
private companion object {
    const val EGL_OPENGL_ES2_BIT = 4
}
```

---

### IN-04: Redundant `@Inject constructor()` + explicit `@Provides` in DI module

**File:** `app/src/main/java/com/warped/di/InferenceModule.kt:27,42,46` and `app/src/main/java/com/warped/data/local/inference/LlamaEngine.kt:10`

**Issue:** `LlamaEngine`, `BackendDetector`, and `LiteRTLmEngine` all have `@Inject constructor()` annotations AND are manually constructed in `InferenceModule` via `@Provides` methods. Hilt can inject them automatically from the `@Inject` constructors — the explicit `@Provides` methods are redundant. While not harmful (the `@Provides` takes precedence), the duplication increases maintenance burden and obscures the actual injection path.

**Fix:** Remove the redundant `@Provides` methods from `InferenceModule` and let Hilt use constructor injection, OR remove `@Inject` from the constructors and keep only the `@Provides` methods. Choose one path consistently.

---

### IN-05: `MemoryChecker` duplicates 80%/60% threshold logic across three methods

**File:** `app/src/main/java/com/warped/data/local/inference/MemoryChecker.kt:30-73`

**Issue:** The 80% and 60% RAM thresholds are defined inline in `canLoadModel` (line 32), `shouldWarn` (line 37), `canLoadLitertlmModel` (line 49 via delegation), and `getLitertlmMemoryWarning` (lines 64, 69). If the threshold values change, four locations must be updated. Additionally, `canLoadLitertlmModel` simply delegates to `canLoadModel` — the distinct method adds no value since both GGUF and .litertlm models share the same memory constraint.

**Fix:** Extract constants and reuse:
```kotlin
companion object {
    private const val LOAD_THRESHOLD = 0.8  // 80% of available RAM
    private const val WARN_THRESHOLD = 0.6  // 60% of available RAM
}
```
And consider removing `canLoadLitertlmModel` — callers can use `canLoadModel` directly.

---

_Reviewed: 2026-05-02T00:00:00Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: quick_
