# Pitfalls Research: Warped v2.0 Gallery Convergence & Performance Overhaul

**Domain:** Android on-device LLM chat (Kotlin + Jetpack Compose + LiteRT-LM + LM Studio v1) — v2.0 ports & refactor
**Researched:** 2026-06-05
**Confidence:** HIGH (LiteRT-LM API surface verified from `LlmModelHelper.kt` source, Gallery `libs.versions.toml`, and Maven releases) / MEDIUM (specific v0.13.x migration gotchas — few public reports) / LOW (perf impact estimates — not measured on Warped hardware)

**Reference implementation:** [google-ai-edge/gallery](https://github.com/google-ai-edge/gallery) v1.0.15 (May 2026), 23.6k stars, 91.9% Kotlin — verified source: [`LlmModelHelper.kt`](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/runtime/LlmModelHelper.kt) and [`libs.versions.toml`](https://github.com/google-ai-edge/gallery/blob/main/Android/src/gradle/libs.versions.toml)

**Stack ground truth (from `STACK.md` — do not re-litigate):** Kotlin 2.3.20, AGP 9.2.1, Compose BOM 2026.05.01, Hilt 2.59.2, Room 2.8.4, LiteRT-LM 0.13.1, OkHttp 4.12.0, Retrofit 3.0.0, Kotlinx Serialization 1.7.3. `minSdk = 28`. Only LM Studio v1 is supported for remote (per v1.8 ENDPT-04..05).

**Downstream consumer:** Each pitfall is tagged with the v2.0 phase (40–44 per `FEATURES.md`) that should prevent it. Cross-references to `STACK.md`, `FEATURES.md`, `ARCHITECTURE.md` are explicit.

---

## Table of Contents

1. [Critical Pitfalls (causes rewrites, runtime crashes, or major perf regressions)](#critical-pitfalls)
2. [Technical Debt Patterns](#technical-debt-patterns)
3. [Integration Gotchas (Hilt / Room / OkHttp / WorkManager / LiteRT-LM)](#integration-gotchas)
4. [Performance Traps (Compose recomposition, cold start, mmap, downloads)](#performance-traps)
5. [Security Mistakes (R8 keep rules for JNI, EncryptedSharedPreferences, native libs)](#security-mistakes)
6. [UX Pitfalls (chat streaming, thinking mode, benchmark, model allowlist)](#ux-pitfalls)
7. ["Looks Done But Isn't" Checklist](#looks-done-but-isnt-checklist)
8. [Gallery Anti-Patterns to NOT Copy](#gallery-anti-patterns-to-not-copy)
9. [Pitfall-to-Phase Mapping](#pitfall-to-phase-mapping)
10. [Sources](#sources)

---

## Critical Pitfalls

These cause a rewrite, an immediate runtime crash, or a hard-to-revert performance regression. They must be addressed in the v2.0 plan, not deferred.

### Critical 1: Adding Gallery's kapt / Firebase / Moshi / Gson / kotlin-reflect Stack Alongside Warped's KSP / kotlinx-serialization

**What goes wrong:**
Gallery's `libs.versions.toml` is a tech-debt museum: it still has `kapt` for the Hilt compiler (legacy pre-2.48), `kotlin-reflect` for Compose Navigation dynamic features, both `moshi` and `gson` for Firebase/HF-OAuth deserialization, `mlkit-genai-prompt`, `play-services-tflite-*`, and Firebase BOM. A naive copy-paste of Gallery's `build.gradle.kts` adds 4–6 MB to Warped's APK, slows incremental builds by 2–5× (kapt), and reintroduces an entire second DI system that conflicts with Hilt's KSP compiler.

**Why it happens:**
When porting "best patterns" developers grep Gallery for Hilt and find kapt. They assume Gallery is the source of truth for "the modern stack" and overwrite their existing clean config. Warped is **already ahead** of Gallery on Hilt version (2.59.2 vs 2.58), Room (2.8.4 vs 2.7.x), DataStore (1.2.1 vs 1.1.7), and AGP (9.2.1 vs 8.8.2). The migration is in the *opposite* direction.

**How to avoid:**
- Treat `STACK.md` MUST-ADOPT list as authoritative. Adopt only: `hilt-navigation-compose:1.3.0`, `hilt-work:1.3.0`, `androidx.core:core-splashscreen:1.2.0-beta01`, `androidx.lifecycle:lifecycle-process:2.10.0`, the manifest `<uses-native-library>` entries, the `cacheDir` EngineConfig argument, and the `Backend.GPU()` runtime selection.
- Do **NOT** copy: kapt, kotlin-reflect, Moshi codegen, Gson, Firebase BOM, mlkit-genai-prompt, play-services-tflite-*, AppAuth, CameraX, Ktor, MCP Kotlin SDK, compose-richtext/commonmark, Proto DataStore.
- Verification: `./gradlew :app:dependencies | grep -E "kapt|firebase|moshi|gson|kotlin-reflect|ktor"` should be empty.

**Warning signs:**
- `./gradlew :app:build` suddenly shows 3–5 new `kapt` configuration tasks.
- APK size grows > 2 MB without any new feature.
- A `kotlinx.serialization` `Serializer for class 'X' is not found` crash appears in release builds (caused by R8 stripping kotlinx-serialization's generated serializers — see Critical #5).

**Phase to address:** Phase 40 (Runtime & Allowlist Foundation) — first thing in the phase plan, before any code porting.

**Cross-ref:** `STACK.md` §2 (DI), §5 (Hilt), §15 (kotlin-reflect DO NOT ADD), "What NOT to Add" table. `FEATURES.md` Stack vs Gallery Feature Surface comparison.

---

### Critical 2: LiteRT-LM `Engine.initialize()` Blocks the Calling Thread — `application onCreate` Path Must Be Off-Main

**What goes wrong:**
The official LiteRT-LM docs explicitly warn: *"The `initialize()` method can take a significant amount of time (e.g., up to 10 seconds) to load the model. It is strongly recommended to call this on a background thread or coroutine to avoid blocking the UI thread."* (source: [developers.google.com/edge/litert-lm/android](https://developers.google.com/edge/litert-lm/android), last updated 2026-05-28). If `LlmModelHelper.initialize()` is called synchronously from `ChatViewModel.init` or from any Hilt `@Provides` for `LiteRtLlmEngine`, the **first chat open after app start** will ANR (10s+ on cold model load).

**Why it happens:**
Developers porting `LlmModelHelper` from Gallery copy the `initialize()` signature — which includes an `onDone: (String) -> Unit` callback — and assume "callback = async, safe to call from main." It's not. The callback fires after the synchronous native `Engine(modelPath, engineConfig).initialize()` returns. The 10-second block happens **before** the callback ever runs. Gallery's `LlmModelHelper` implementations wrap the call in a `CoroutineScope.launch(Dispatchers.IO) { engine.initialize() ; onDone() }`, but this is **inside the implementation**, not the interface. A Warped port that doesn't replicate the IO-dispatch wrapper will ANR.

**How to avoid:**
- The `LlmModelHelper` interface must have the same `onDone` callback contract as Gallery's, but Warped's `LiteRtLlmHelper.initialize()` must wrap the native call in `withContext(Dispatchers.IO) { ... }` internally. Document this in the interface KDoc: *"Implementations MUST run onDispatchers.IO. The onDone callback fires after the IO call completes."*
- The `ChatViewModel` must collect the model's loaded state via `StateFlow<LoadState>` (Idle → Loading → Ready / Error), not block on a `runBlocking` future.
- Add a `ProcessLifecycleOwner` listener (new in Phase 40 from `lifecycle-process:2.10.0`) that observes `ON_STOP` → cancels in-progress model loads if the user leaves the app. Without this, a load started 8 seconds ago finishes after the user has navigated away.

**Warning signs:**
- StrictMode detects a `DiskReadViolation` or `NetworkOnMainThread` violation on `Engine.initialize()` (if StrictMode is wired).
- Macrobenchmark `timeToInitialDisplayMs` exceeds 1.5s on a Pixel 7.
- The user sees a blank chat screen with no spinner for > 3 seconds after tapping a downloaded model.

**Phase to address:** Phase 40 (Runtime & Allowlist Foundation) — the `LiteRtLlmHelper` implementation must be written defensively from day one, not patched later.

**Cross-ref:** `STACK.md` §3 (LiteRT-LM), `FEATURES.md` Table Stakes §LlmModelHelper, Gallery's `LlmModelHelper.kt:42-58` (callback contract).

---

### Critical 3: LiteRT-LM 0.12 → 0.13 Model File-Format Incompatibility (Some .litertlm Files Fail `Engine.initialize()`)

**What goes wrong:**
A confirmed open issue ([google-ai-edge/LiteRT-LM#2454](https://github.com/google-ai-edge/LiteRT-LM/issues/2454), opened 2026-06-03): *"litertlm-android 0.12.0: Gemma 4 E2B .litertlm fails Engine.initialize() with 'INVALID_ARGUMENT: Unsupported or unknown file format' — same file loads on the iOS Swift xcframework."* This is a known cross-runtime packaging bug. Warped tracks LiteRT-LM 0.12.0 → 0.13.1 in v2.0. Any model the user already has on disk from a 0.12 download may fail to load under 0.13.1, and a "load model" call will throw a `LiteRtLmJniException` on the IO dispatcher.

**Why it happens:**
The `.litertlm` file format has version bytes. v0.12 produced files with a header revision that v0.13's Android engine rejects (the iOS engine has a different loader path that accepts the older header). The fix lands in v0.13.x — but Warped users with already-downloaded files will be in a stale state.

**How to avoid:**
1. **Defensive engine init**: in `LiteRtLlmHelper.initialize()`, catch `LiteRtLmJniException` with a "Unsupported or unknown file format" message and surface a `LoadError.StaleModelFile` UI state. The screen should suggest: (a) re-download the model from the Hugging Face allowlist, (b) check if a newer `.litertlm` is available.
2. **Migrate-on-load**: on `initialize()` failure, attempt a one-time copy to `cacheDir` and retry — some "stale file" cases are actually `mmap` cache corruption (see Critical #4) and not a real format mismatch.
3. **Stale-file detection in model browser**: when listing models in `Models & Endpoints`, check the LiteRT-LM library version that produced the file (stored in a sidecar JSON written at download time). Files flagged as produced-by-0.12 get a "May need re-download" badge in the UI.
4. **Capability-gate the upgrade**: don't force the v0.12 → v0.13.1 bump. Allow users to opt-in via an advanced setting ("Use latest LiteRT-LM runtime") until the file-format issue stabilizes. Or do the bump in v2.0.1 once 0.13.2 ships.
5. **MMAP cache invalidation** (see Critical #4) — required even if the format bug is fixed.

**Warning signs:**
- User reports "model won't load" after upgrade, with `INVALID_ARGUMENT: Unsupported or unknown file format` in logcat.
- Crash reports from `LiteRtLmJniException` spike in the first 7 days after the 0.13.1 release.
- The same model file loads successfully on Gallery v0.11 (which Warped's older app essentially is, pre-v2.0).

**Phase to address:** Phase 40 (Runtime & Allowlist Foundation) — `LiteRtLlmHelper.initialize()` must include the defensive try/catch before the first beta ships. Also affects Phase 43 (Performance Convergence) — mmap cache invalidation.

**Cross-ref:** `STACK.md` §3 (LiteRT-LM 0.13.1 version), `FEATURES.md` LRT-01..03. Open issue: google-ai-edge/LiteRT-LM#2454.

---

### Critical 4: mmap Cache from Old EngineConfig Becomes Stale After EngineConfig Schema Change

**What goes wrong:**
Gallery's proven pattern is to pass `cacheDir = context.cacheDir.path` to `EngineConfig` for warm-start optimization (LiteRT-LM mmap-caches compiled artifacts in that directory). Per `STACK.md` §3: *"Enables mmap caching of the model — dramatically reduces subsequent load time (TTFT), sometimes 5-10x for warm starts."* However, when `EngineConfig` schema changes between LiteRT-LM versions (and v0.13 changed the cache key format — see the KV-cache and speculative-decoding PRs in the v0.13 release notes), **the cached mmap file is silently incompatible**. Loading it gives a "warm start" that is actually a corrupt start: random NaNs in token logits, dropped messages in the middle of a conversation, or — worst case — an `IllegalStateException` on first response that doesn't reproduce on retry.

**Why it happens:**
LiteRT-LM does not expose a cache-version API. The cache key is internal to the engine binary. When the binary changes, the cache is stale. There is no automatic invalidation.

**How to avoid:**
1. **Cache directory versioning**: in `LiteRtLlmHelper`, compute a per-runtime-version subdirectory: `File(context.cacheDir, "litertlm/${BuildConfig.LITERTLM_VERSION}/")`. Bumping `litertlm` in `libs.versions.toml` creates a fresh cache directory. Old cache becomes orphaned (delete on next app start if size > 100MB).
2. **Cache invalidation hook**: on `Engine.initialize()` failure (any `LiteRtLmJniException` from a fresh-cache build), wipe `context.cacheDir/litertlm/` and retry once. Log the wipe event to the crash handler.
3. **First-run UX**: the first time the user opens a downloaded model after upgrading LiteRT-LM, show a "Optimizing for your device (one-time, ~30s)" sheet. Hides the slow first-load path; subsequent loads are fast. Gallery's UX is "blank screen for 10s" which feels broken — Warped's should not.
4. **MMAP size cap**: cap the cache at 500 MB. LiteRT-LM 0.13 added MTP (Multi-Token Prediction) draft model caching, which can balloon to 1.5 GB for a 12B model. Cap with LRU eviction on app background.

**Warning signs:**
- A user reports: "First response after upgrading is fine, but mid-conversation the model starts outputting garbage tokens."
- Logcat shows `mmap failed: invalid magic number` on `Engine.initialize()`.
- Disk usage in `cacheDir/litertlm/` exceeds 1 GB without user action.

**Phase to address:** Phase 40 (LiteRT-LM v0.13.1 bump) and Phase 43 (Performance Convergence — disk usage cap).

**Cross-ref:** `STACK.md` §3 (LiteRT-LM cacheDir), LiteRT-LM v0.13 release notes (MTP, speculative decoding — both add cache footprint).

---

### Critical 5: ProGuard/R8 Strips LiteRT-LM JNI Native Methods → `UnsatisfiedLinkError` at Runtime

**What goes wrong:**
R8 is a static analyzer. Native methods called from C++ via JNI look like dead code (no Java caller), so R8 strips them. Warped already enables R8 in release (v1.5 VALIDATED: "ProGuard/R8 obfuscation and shrinking enabled with aggressive rules for release"). LiteRT-LM's `litertlm-android:0.13.1` AAR ships a `libLiteRtLmJni.so` plus a Java façade with `external fun` native method declarations. R8 + JNI = recipe for `java.lang.UnsatisfiedLinkError: No implementation found for ... nativeMethod(...)` crashes on first model load in a release build.

**Why it happens:**
Developers assume the AAR ships its own ProGuard consumer rules. **It does not** (or it ships them incomplete). The AAR's `proguard.txt` typically contains `-keepclasseswithmembernames class * { native <methods>; }` but does NOT cover the LiteRT-LM `MessageCallback` interface or the `ToolProvider` reflection-based registry that LiteRT-LM 0.13's agent skill runtime depends on.

**How to avoid:**
- Add explicit keep rules in `app/proguard-rules.pro`:
  ```
  # LiteRT-LM JNI
  -keep class com.google.ai.edge.litertlm.** { *; }
  -keep class com.google.ai.edge.litertlm.jni.** { *; }
  -keepclasseswithmembernames class * { native <methods>; }
  -keep,allowobfuscation,allowshrinking class com.google.ai.edge.litertlm.MessageCallback
  -keep,allowobfuscation,allowshrinking class com.google.ai.edge.litertlm.ToolProvider
  -keepclassmembers class com.google.ai.edge.litertlm.** {
      public <init>(...);
      public *;
  }
  ```
- **Enable R8 full mode**: `android.enableR8.fullMode=true` in `gradle.properties`. The 2025-11-18 Android Developers Blog post [Configure and troubleshoot R8 Keep Rules](https://developer.android.com/blog/posts/configure-and-troubleshoot-r8-keep-rules) is explicit: full mode is more aggressive, **but** the default `proguard-android-optimize.txt` is now mandatory (the non-optimize file is being removed in AGP 9.0). Verify `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")` in `release` build type.
- **Verify with `aapt2 dump strings`** on the release APK: `aapt2 dump strings app-release.apk | grep -i litertlm` should show all `com.google.ai.edge.litertlm.*` class names (kept, not obfuscated). Run `./gradlew :app:assembleRelease` then decompile with `apkanalyzer` to confirm.
- **Kotlinx Serialization keep rules** (already in v1.5 set, but verify after Compose BOM 2026.05.01 bump):
  ```
  -keepattributes *Annotation*, InnerClasses
  -dontnote kotlinx.serialization.AnnotationsKt
  -keepclassmembers @kotlinx.serialization.Serializable class * {
      static **$* *;
      static <fields>;
      public static ** INSTANCE;
  }
  ```

**Warning signs:**
- Release build crashes on `engine.initialize()` with `UnsatisfiedLinkError`.
- `aapt2 dump strings` shows the LiteRT-LM classes are missing from the APK (stripped).
- Debug build works fine — this is the classic R8-vs-debug divergence symptom.

**Phase to address:** Phase 40 (must add to `proguard-rules.pro` before the first release-tagged commit) and verified again in Phase 43 (Performance — APK size verification).

**Cross-ref:** `STACK.md` §8 (Security — ProGuard/R8), §10 (Testing — R8 verified in release). Android Developers Blog "Configure and troubleshoot R8 Keep Rules" (Nov 2025).

---

### Critical 6: R8 Strips `kotlinx.serialization` `@Serializable` Companion → "Serializer Not Found" Crash

**What goes wrong:**
Warped uses `kotlinx-serialization:1.7.3` for all DTOs (LM Studio v1 chat request/response, Hugging Face model metadata, etc.). The KSP plugin generates `$$serializer` classes for each `@Serializable` type. R8's tree-shaking doesn't see a static reference to these generated serializers (they're loaded via `invoke()` reflection from the `Companion`), and strips them. Result: `SerializationException: Serializer for class 'LmStudioChatRequest' is not found. Mark the class as @Serializable or provide the serializer explicitly.` — only in release builds, only on the first model list fetch.

**Why it happens:**
Gallery has this same problem and solved it with a different (worse) fix: it added Gson as a fallback. Warped's correct path is the official kotlinx-serialization R8 ruleset. R8 full mode + default optimize file should handle most of it, but the `@Serializer` companion pattern needs explicit coverage.

**How to avoid:**
- Add the canonical kotlinx-serialization keep rules (see [StackOverflow verified rule set](https://stackoverflow.com/questions/70663076/how-to-make-proguard-keep-kotlinx-serializers-for-objects)):
  ```
  # Keep generated $$serializer companion
  -if @kotlinx.serialization.Serializable class **
  -keepclassmembers class <1> {
      static <1>$Companion Companion;
  }
  -keepclasseswithmembers class **.*$$serializer { *; }
  -keepclassmembers class * {
      kotlinx.serialization.KSerializer serializer(...);
  }
  ```
- **Smoke test in release**: after R8 is configured, run `./gradlew :app:assembleRelease` then `adb install` and exercise every screen that deserializes a network DTO (LM Studio model list, Hugging Face search, model detail). If any screen crashes with "Serializer not found", a rule is missing.
- Do **NOT** follow Gallery's anti-pattern: adding Gson as a fallback "just in case". The 2 MB cost of Gson is permanent, the kotlinx-serialization rule fix is one-time.

**Warning signs:**
- `SerializationException` in `Logcat` with `cause: kotlinx.serialization.SerializationException: Serializer for class 'X' is not found`.
- Only happens in `release` build type, never `debug`.
- The crash is in `X$Companion.serializer()` invocation.

**Phase to address:** Phase 40 (alongside LiteRT-LM R8 rules in `proguard-rules.pro`).

**Cross-ref:** `STACK.md` §8 (R8 enabled for release since v1.5), §7 (kotlinx-serialization).

---

### Critical 7: `LlmModelHelper.runInference` Is a Callback API, Not a Suspend Function — Bridging to Flow Without Backpressure Drops Tokens

**What goes wrong:**
Gallery's `runInference(model, input, resultListener: ResultListener, ...)` is callback-based. `ResultListener` is `(partialResult: String, done: Boolean, partialThinkingResult: String?) -> Unit`. If a Warped port wraps this in `callbackFlow { runInference(model, input, resultListener = { partial, done, thinking -> trySend(partial) }, ...) }` **without buffer/capacity configuration**, fast LLMs (LiteRT-LM GPU on flagship, ~52 tok/s) will **drop tokens** because the downstream `StateFlow<UiState>` collector (running on `Dispatchers.Main.immediate` for UI updates) cannot keep up. The user sees a stuttering text stream with gaps. **Gallery's open issue #921 "Bring back inference stats to AI Chat"** is partially caused by this same pattern — without token counts, the user can't tell that tokens are being dropped.

**Why it happens:**
Developers see "callback to Flow" and write the obvious `callbackFlow { ... }` adapter. But `callbackFlow` defaults to `Channel.BUFFERED` (64-element capacity). For an LLM that produces a token every ~20ms (50 tok/s), 64 tokens = 1.28s of buffer. The downstream Compose UI only reads at frame rate (~16ms = 60fps), so the channel never gets close to overflow. **For an LLM doing 200 tok/s with a slow Main thread collector (e.g., the user is also receiving a `TextField` change), the channel DOES overflow** and `trySend` returns failed → token dropped silently.

**How to avoid:**
- Use `Channel(capacity = Channel.UNLIMITED)` for the inference → UI stream. The producer is the JNI bridge (fast, off-main). The consumer is Compose (slow, on-main). Buffering is the right answer.
- Emit `flow { runInference(...) }` directly using `callbackFlow` with `awaitClose { stopResponse(model) }` to ensure cancellation releases the model. Then `collectAsStateWithLifecycle()` on the consumer side.
- **Plumb `partialThinkingResult`** to the UI: when present, show a collapsible "Thinking..." panel above the response. This is Gallery's pattern. Don't conflate thinking tokens with regular tokens in the same buffer.
- **Token counter**: count `partialResult.length - previousLength` deltas and report to a `tokenCount: Int` UI state field. Exposes dropped tokens (deltas of zero) to the user.
- **Coalesce emissions on Main**: in the collector, use `.conflate()` or `.sample(50.ms)` if the Compose layer is the bottleneck, NOT drop tokens at the producer. Producer loss is irreversible; Main-side coalescing is visually smooth.

**Warning signs:**
- User reports "the model output is shorter than the response time would suggest" — implies dropped tokens.
- Gallery issue #921 — inference stats missing, the user can't verify token counts.
- Benchmark numbers in Phase 41 (Benchmark) show `decode_tok_per_sec` lower than expected (e.g., 30 tok/s reported for a model that benchmarks at 52 tok/s on the same device — the difference is dropped tokens).

**Phase to address:** Phase 40 (LlmModelHelper implementation) and Phase 41 (Thinking Mode — adds the `partialThinkingResult` plumbing).

**Cross-ref:** `STACK.md` §10 (Coroutines / Flow), `FEATURES.md` LlmModelHelper interface contract, Gallery's `ResultListener` typealias: `(partialResult: String, done: Boolean, partialThinkingResult: String?) -> Unit`.

---

### Critical 8: Hilt Graph Cycles When `LlmModelHelper` Depends on `Model` and `Model` Repository Depends on `LiteRtLlmHelper`

**What goes wrong:**
A common porting mistake: `LiteRtLlmHelper` (implementation of `LlmModelHelper`) is `@Inject constructor(modelRepository: ModelRepository)`. The `ModelRepository` is `@Inject constructor(llmModelHelper: LlmModelHelper)` (e.g., to query loaded state). Hilt's compile-time graph throws `DaggerHilt_*Builder$* cannot be provided without an @Inject constructor or an @Provides-annotated method` with a 200-line cycle trace. The build fails — but the failure message is misleading (it points at a leaf, not the cycle).

**Why it happens:**
Developers used to have separate `LiteRtLmEngine` and `LmStudioProvider` injected into the repository, not the `LlmModelHelper` interface. The new abstraction introduces a new dependency direction.

**How to avoid:**
- **Use `@Binds` for the interface**: `LlmModelHelper` is an interface; provide it via a Hilt `@Module @InstallIn(SingletonComponent::class) abstract class LlmHelperModule { @Binds @Singleton abstract fun bindLlmHelper(impl: LiteRtLlmHelper): LlmModelHelper }`. `@Binds` is zero-cost and signals "interface → implementation" intent.
- **Strip redundant deps**: `LiteRtLlmHelper` should not depend on `ModelRepository` for the *load* path. It only needs `Model` (a value object passed in `initialize(model, ...)`). If you need to *query* whether a model is loaded, do that via a separate `ModelLoadStateRepository` (no LLM dep), not via `ModelRepository`.
- **Decision rule**: `LlmModelHelper` is a **stateful runtime**. It owns the loaded engine. Repositories are stateless or only-own-DB. Crossing the boundary is the bug. Run `./gradlew :app:assembleDebug --stacktrace` to see the actual cycle — search the trace for the repeated class name.

**Warning signs:**
- Build error mentions the same class 3+ times in the dependency trace.
- Adding `@Provides` for one class "fixes" the build but the chain of new @Provides keeps growing.
- Unit tests that previously compiled stop compiling after introducing `LlmModelHelper`.

**Phase to address:** Phase 40 (Hilt module structure for the new `LlmModelHelper` must be right from the start).

**Cross-ref:** `STACK.md` §2 (Hilt KSP-only, `@Binds` pattern), Hilt scoping guide (Davide Agostini 2026-02-18, Hilt deep dive).

---

### Critical 9: Room Migration Without `exportSchema` → Silent Schema Divergence → Data Loss in v2.x

**What goes wrong:**
v2.0 adds a new table or column to the Room database (e.g., a `BenchmarkResult` table for Phase 41, or a `thinkingEnabled` column on `Message` for Phase 41 Thinking Mode). If the `@Database` `version = N` is bumped but no `Migration` is added, Room throws `IllegalStateException: A migration from N-1 to N was required but not found. Please provide the migration ...`. This crashes the app on startup for every existing user. If a developer "fixes" this by calling `fallbackToDestructiveMigration()` to ship fast, **all user conversations are deleted** on the next app update. This is a data-loss event, not a recoverable crash.

**Why it happens:**
Room migrations are invisible in the IDE — no compile error, no lint warning. The crash only fires at runtime on user devices. Developers new to Room often don't realize the schema must be versioned AND migrations must be supplied for every step. v1.0..v1.8 in Warped haven't had a major schema change, so this gotcha hasn't been hit yet.

**How to avoid:**
- **Always `exportSchema = true`**: `@Database(entities = [...], version = N, exportSchema = true)`. Set `room.schemaLocation` in `build.gradle.kts`: `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`. Commit the generated `schemas/com.warped.app.AppDatabase/N.json` to git. **Without `exportSchema`, the auto-migration feature is disabled** (Room needs the old schema to compute diffs).
- **For additive changes** (new column, new table): use `@AutoMigration`. v2.0 benchmark history + thinking flag = perfect auto-migration candidates.
- **For breaking changes** (rename column, change type, drop table): write a `Migration` object, test it with `androidx.room:room-testing` + `MigrationTestHelper`. Per the official Android dev guide: *"Test the migration paths explicitly. Add them to your instrumentation tests as a room migration test."*
- **NEVER** add `.fallbackToDestructiveMigration()` to production. v1.0 had no production users with chat history to protect, but v2.0 will (39 phases, 234 requirements — there are users).
- **Schema test**: add an instrumented test in `androidTest/` that opens the database with the prior schema, runs all migrations, then opens with the new schema and asserts row counts.

**Warning signs:**
- Crash reports on app startup with `IllegalStateException: A migration from N-1 to N was required but not found`.
- v2.0 install on a device that has v1.8 chat history → silent DB wipe.
- `schemas/` directory missing from the repo (no schema diffs to review in PRs).

**Phase to address:** Phase 41 (Benchmark adds `BenchmarkResult` table) and Phase 41 (Thinking Mode adds `thinking` column to `Message`). Both must include `Migration` or `@AutoMigration` and a `MigrationTest` in the phase plan.

**Cross-ref:** `STACK.md` §4 (Room 2.8.4, KSP), `FEATURES.md` Model Benchmark + Thinking Mode data deps. Android Developer Guide: [Migrate your Room database](https://developer.android.com/training/data-storage/room/migrating-db-versions).

---

### Critical 10: SSE Stream Not Drained on Cancellation → Socket Leak + Process Death

**What goes wrong:**
LM Studio v1 streams chat completions over Server-Sent Events (HTTP `Content-Type: text/event-stream`). Warped's current `LmStudioProvider` likely opens an OkHttp `Call` and reads `ResponseBody` line-by-line. If the user taps "Stop" mid-stream, the `Call` is cancelled (`call.cancel()`) but the **read loop is not interrupted cleanly** — the coroutine on `Dispatchers.IO` is suspended on `BufferedSource.readUtf8Line()`. Cancellation requires cooperative cancellation: the read must respect `coroutineContext.isActive`. If the streaming collector swallows the cancellation, the `OkHttpClient` connection pool retains the socket for `keepAliveDuration` (default 5 min), and repeated stop/start cycles leak connections. On a phone with 4G/5G keep-alive limits, this manifests as "no network" errors 30 minutes after a chat session.

**Why it happens:**
The standard OkHttp SSE example uses `response.body()!!.source().readUtf8Line()` in a tight loop, which is **blocking I/O without cancellation hooks**. Gallery's `LlmModelHelper.runInference` accepts a `resultListener` callback and a `cleanUpListener: CleanUpListener = () -> Unit`. The local LiteRT-LM engine respects `cleanUpListener()` to abort — but the remote LM Studio implementation must wire `stopResponse(model)` to `call.cancel()` AND signal the IO-dispatched read loop to exit. A common bug: `stopResponse` cancels the call but doesn't signal the read loop, leaving the read suspended until OkHttp's connection timeout (10s default).

**How to avoid:**
- **Cooperative cancellation**: in the LM Studio SSE reader, use `coroutineContext.ensureActive()` between each `readUtf8Line()` and break the loop if inactive. Alternatively, use `withContext(Dispatchers.IO)` and a `Channel<String>` where `stopResponse` closes the channel — the read loop sees `channel.isClosedForReceive` and exits cleanly.
- **OkHttp settings**: set `OkHttpClient.Builder().retryOnConnectionFailure(false)` for streaming calls (don't retry a half-read SSE stream). Set `callTimeout(60.seconds)` so a stuck read eventually times out instead of hanging the coroutine.
- **`stopResponse` must also release the model slot** in `LlmModelHelper`. For local: call `Engine.close()`. For remote: cancel the OkHttp call AND null out the active `Call` reference so the next `runInference` doesn't double-cancel.
- **Test**: write a unit test that calls `runInference`, waits 100ms, calls `stopResponse`, asserts the coroutine completes within 200ms. Repeat 50 times; assert no connection-pool growth (instrument OkHttp's `EventListener` for `connectionReleased` count).

**Warning signs:**
- OkHttp `EventListener` shows `callEnd` is followed by `connectionReleased` only after `keepAliveDuration` expires, not on `stopResponse`.
- After 10 stop/start cycles, `connectionPool.connectionCount()` grows without bound.
- The user's "Stop" button stops updating the UI but the network spinner persists for 10+ seconds.

**Phase to address:** Phase 40 (`LmStudioLlmHelper` implementation, fed by the `LlmModelHelper.stopResponse` contract).

**Cross-ref:** `STACK.md` §7 (OkHttp 4.12.0, Retrofit 3.0.0, SSE), `STACK.md` §10 (Coroutines cancellation). Ktor SSE docs (not adopted, but the `SSEBufferPolicy` discussion is relevant to backpressure).

---

### Critical 11: Compose Strong-Skipping-Mode (Default in 1.11) + `List<T>` Params = Mass Unstable Lambdas

**What goes wrong:**
Compose BOM 2026.05.01 (upgrading Warped from 2026.04.01 per `STACK.md` §1) makes **strong skipping mode the default**. Pre-1.11, the Compose compiler's stability inference was lenient about `List<T>` (treated as @Stable). Post-1.11, the compiler is more aggressive: any `List<T>` parameter that flows through a `LazyColumn` is now treated as **unstable** unless annotated or replaced with `kotlinx.collections.immutable.ImmutableList<T>`. Result: every `MessageBubble`, every `CodeBlock`, every row in `ModelsList` is recomposed on every `StateFlow` emission — not because the data changed, but because the compiler can't prove stability.

**Why it happens:**
The strong-skipping-mode change is documented in the Compose BOM release notes but the migration impact is "depending on usage". A 2026-03-12 dev.to article by SoftwareDevs mvpfactory.io ([Jetpack Compose Recomposition at Scale](https://dev.to/software_mvp-factory/jetpack-compose-recomposition-at-scale-how-strong-skipping-mode-changes-the-stability-rules-you-4a80)) walks through the change. The `kotlinx.collections.immutable` library is the standard escape hatch.

**How to avoid:**
1. **Add `org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0`** to `libs.versions.toml` (a transitive dep that most projects don't add explicitly — it must be added).
2. **Convert public composable parameters** that are `List<T>` → `ImmutableList<T>`. Audit:
   - `MessageBubble(messages: List<Message>)` → `MessageBubble(messages: ImmutableList<Message>)`
   - `ModelsList(models: List<Model>)` → `ModelsList(models: ImmutableList<Model>)`
   - `PromptLabViewModel.templates: List<PromptTemplate>` (Phase 42) → `ImmutableList<PromptTemplate>`
3. **Audit the Layout Inspector** in Android Studio after the BOM bump. Open `ChatScreen` and verify `MessageBubble` is **skipped** (gray bar) when the streaming token updates, not recomposed (blue bar). If everything recomposes, you missed a `List` parameter.
4. **Stable lambdas**: any `(T) -> Unit` parameter passed to a `LazyColumn` `items { ... }` block should be wrapped in `remember(key) { { ... } }` to prevent per-frame allocation. Skipping mode is now strict about lambda allocation too.
5. **`@Stable` / `@Immutable` annotations**: for Warped-internal value classes (`ModelUiState`, `ChatUiState`), add `@Immutable` so the compiler treats them as stable. Currently Warped's `ChatUiState` is a `data class` — annotate it.

**Warning signs:**
- After the Compose BOM bump, scrolling `ChatScreen` during streaming is visibly jankier than before.
- Layout Inspector shows blue (recomposed) bars on `MessageBubble` for every streaming token.
- Macrobenchmark `frameOverrunMs` P50 increases > 2ms after the BOM bump.

**Phase to address:** Phase 43 (Performance Convergence — composable recomposition audit) and **before** Phase 41 (Thinking Mode, which adds a new `partialThinkingResult` StateFlow subscription — the worst possible time to discover this).

**Cross-ref:** `STACK.md` §1 (Compose BOM bump), §11 (Compose UI testing v2). QyperXit android-skills `compose-performance-audit/SKILL.md` (full audit recipe).

---

### Critical 12: WorkManager + Foreground Service for 1-3GB Model Downloads → Battery Optimizer Kills Download

**What goes wrong:**
Warped downloads `.litertlm` files (1–3 GB) from Hugging Face with pause/resume. v1.0 used OkHttp directly. v1.8 added WorkManager-based background downloads with cancellation. v2.0 needs to support downloads that **survive the app being swiped away from Recents** (not just the app being backgrounded) — a 2GB download takes 10-30 minutes on 4G, way past any doze threshold. A `Worker` that doesn't use `setForeground()` is killed within 5-10 minutes of app backgrounding on Android 12+. A `Worker` that uses `setForeground(SynchronousWork)` requires a foreground service notification that **must be present even when the user swipes the app away**.

The gotcha: **doze mode + battery optimization on Chinese OEM ROMs (MIUI, EMUI, ColorOS) kills the foreground service too** unless the user has explicitly whitelisted the app. On Samsung devices, "Sleeping apps" deep-sleep after 3 days. Warped has no UX to surface these restrictions.

**Why it happens:**
The Android 12+ `setForeground()` API is documented as "guaranteed to run long enough to call `setForegroundAsync()`" but Chinese OEMs interpret the rules differently. Gallery doesn't ship to China, so this hasn't been a Gallery issue.

**How to avoid:**
1. **Always use `setForeground()` for downloads > 30 MB**: pass a `ForegroundInfo` with a notification channel ID and an ongoing notification. The notification must be `ongoing` (cannot be dismissed by user) and must update with progress every 5 seconds.
2. **Notification channel with `IMPORTANCE_LOW`**: not silent, not vibrate. The user needs to know the download is happening, but a heads-up ping every progress tick is annoying.
3. **Persist download state to Room**: every 5 seconds, write `{bytesDownloaded, totalBytes, etag, lastModified}` to a `DownloadProgress` table. On Worker restart (which WILL happen — doze kills the worker and WorkManager reschedules it), the Worker reads the table and resumes with `Range: bytes=N-` (if the server supports it) or starts over (if not).
4. **Server-side `Range` support check**: most Hugging Face CDN endpoints support range requests, but **the redirect to the actual CDN (Cloudflare) may strip the `Range` header**. Warped must follow the redirect (OkHttp does this automatically) and re-add the `Range` header on the second request, OR accept that the first `Range` request may return `200 OK` (full content) and treat the resumed-from-bytes-N as a fresh download from 0 to total.
5. **Doze handling**: register a `BroadcastReceiver` for `Intent.ACTION_BATTERY_CHANGED` to detect when the device enters low-power state, and pause the download (save progress, cancel worker) with a user-visible "Paused to save battery. Tap to resume." notice. Resuming requires a user tap (no silent auto-resume).
6. **First-run permission prompt**: on the first download, show a "For large model downloads, allow Warped to run in the background" dialog with deep-links to: (a) battery optimization whitelist (`Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), (b) autostart whitelist (Chinese OEMs — no standard intent, requires a help doc).

**Warning signs:**
- Download completes in dev (Pixel 7) but reports "Paused" or "Failed" on Xiaomi/Oppo devices.
- After 30 minutes, `DownloadProgress.bytesDownloaded` is stuck at the same value the Worker was killed at.
- User reports "the download starts again from 0 after my screen turns off" (the `Range` header isn't being re-sent on retry).

**Phase to address:** Phase 43 (Performance Convergence — download reliability audit) and Phase 36 (HF model browser) **revisit** — the v1.8 download path is fragile and should be re-tested for these scenarios as part of v2.0 verification.

**Cross-ref:** `STACK.md` §8 (WorkManager 2.10.0, OkHttp 4.12.0), `STACK.md` §13 (largeHeap="true" for model loading), `ARCHITECTURE.md` v1.6 (existing Room data patterns). Gallery uses WorkManager for benchmark runs — same gotcha applies.

---

## Technical Debt Patterns

Shortcuts that seem reasonable but create long-term problems.

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| **Add kapt "to match Gallery"** | Familiar Hilt setup | 2-5× slower builds, kapt maintenance mode, blocks Compose Compiler 2.x features | **Never.** Warped is correctly KSP-only. |
| **Add Gson "as a fallback"** for SerializationException | "Quick fix" for missing serializer | 2 MB APK forever, two JSON libraries in the codebase | **Never.** Add the keep rule instead. |
| **Add Proto DataStore to match Gallery's UserData pattern** | Familiar pattern for benchmark history | Proto schema evolution overhead, codegen build time | **Only for `BenchmarkResult` (Phase 41)** if benchmarking history is stored as nested records. Otherwise stick with Room + JSON column. |
| **Add `kotlin-reflect`** to support Gallery-style Compose dynamic navigation | Dynamic features support | 2.5 MB APK, slower cold start, R8 surface area explosion | **Never.** Warped uses type-safe Navigation 2.9.x with `@Serializable` routes. |
| **Add `extractNativeLibs="true"`** to make LiteRT-LM JNI debugging easier | Easier stack traces | Bigger APK (uncompressed .so files), Play Store warnings | **Only for debug builds.** Release must keep `extractNativeLibs="false"`. |
| **Use `Thread.sleep()` in test code** to wait for model load | Simple test | Flaky in CI, slow on slower devices | **Only in ad-hoc local tests.** Use `runTest { advanceUntilIdle() }` for committed code. |
| **Skip `backend = Backend.GPU()` runtime detection** — always use CPU | Simpler code | 2-5× slower inference on flagship phones with GPU | **Never** for v2.0. Gallery's GPU detection is the right pattern. |
| **Cache the `LiteRtLlmEngine` instance as a `@Singleton`** in Hilt | One instance per app | Hilt's `@Singleton` outlives the model's natural lifetime. Switching models (e.g., user deletes one and loads another) leaks the old engine. | **Use a `ModelLoadStateManager` with `WeakReference` eviction** instead. Gallery does this differently (engine-per-model map). |
| **Make `LlmModelHelper` a `class` not an `interface`** to avoid extra indirection | Saves 1 file | Locks the codebase to one impl, blocks Thinking/Benchmark from sharing the surface | **Never.** The interface is the whole point. |
| **Wrap every Gallery import in `@SuppressLint("UnsafeOptInUsageError")`** to silence warnings about LiteRT-LM `@OptIn` | Cleaner code | Hides actual API surface changes. When v0.14 ships with breaking changes, the warnings are useful. | **Suppress at the file level with a TODO comment** that lists the LiteRT-LM version it applies to. Re-evaluate on every bump. |
| **Hardcode the model allowlist as a Kotlin `object`** (v1.8 pattern) instead of JSON asset | Faster to author | Adding a model requires an app release. Capabilities/taskTypes can't evolve. | **Defer to v2.0 Phase 40** (JSON asset migration). |

---

## Integration Gotchas

Specific to the libraries Warped v2.0 will touch.

### Hilt (compile-time DI)

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| **`LlmModelHelper` ↔ `LiteRtLlmHelper`** | Concrete class `@Inject constructor(...)` injection; downstream code depends on impl | `@Binds @Singleton` in an abstract `LlmHelperModule`. All call sites use `LlmModelHelper` interface. |
| **`@HiltViewModel` + `hiltViewModel(backStackEntry)`** | ViewModel scoped to Activity instead of NavBackStackEntry → wrong lifecycle, leaked state across destinations | Use `hiltViewModel()` from `androidx.hilt:hilt-navigation-compose` in every `composable<Route> { backStackEntry -> ... }` block. |
| **Multiple `Application` classes** | `GalleryApplication`-style class added but `@HiltAndroidApp` missing → graph isn't initialized | One `@HiltAndroidApp class WarpedApplication : Application()` per project. No more. |
| **Cross-module injection** | Feature module wants to inject `MainRepository` from `app` module → Hilt rejects the dep | Define repository interfaces in `domain/`, impls in `data/`. Both `app` and feature modules can depend on `domain/`, neither depends on the other. **Warped is single-module today** — no issue yet, but Phase 43 may surface this. |
| **`@Provides` in `@Module @InstallIn(SingletonComponent::class)`** that returns a `ViewModel` | Singleton VM never cleared → state leak | ViewModels must be `@HiltViewModel`, not `@Provides`d manually. |
| **Injecting `Context` directly** into a non-Android class | Memory leak, can't unit test | Inject `@ApplicationContext context: Context` for app-scoped needs; pass `Context` as a parameter for transient uses. |
| **`hilt-work:1.3.0` workers** | Worker missing `@HiltWorker` annotation → injection fails at runtime | `@HiltWorker class DownloadWorker @AssistedInject constructor(...) : CoroutineWorker(...)`. `WorkerFactory` is auto-wired by `HiltWorkerFactory`. |

### Room (persistence)

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| **Bumping `version = N` without migrations** | App crashes on launch for existing users | Use `@AutoMigration(from = N-1, to = N)` for additive changes; write a `Migration` for breaking changes; commit schemas to `schemas/`. |
| **Returning `Flow<List<X>>` from a DAO** without `distinctUntilChanged()` | Recomposes on every DB write, even unrelated tables | Add `.distinctUntilChanged()` at the call site OR scope the Flow to the specific query's table. |
| **Coroutines on `Dispatchers.Main` for DAO calls** | Main-thread disk I/O = ANR | `RoomDatabase.Builder().setQueryCoroutineContext(Dispatchers.IO)`. Or accept Room's default (IO since 2.1). |
| **Missing composite index on `messages(conversation_id, created_at)`** | Slow scroll on chats with 1000+ messages | Add `indices = [Index(value = ["conversation_id", "created_at"])]` to the `@Entity` declaration. Run `EXPLAIN QUERY PLAN` to confirm. |
| **SQLCipher with Room 2.8.x** | Room 2.8 changed `OpenHelperFactory` signatures; SQLCipher 4.5.4 may not provide the new factory | If keeping SQLCipher, verify the SQLCipher version supports Room 2.8.4's `RoomOpenHelper` API. If not, **this is a forcing function for the SQLCipher removal decision in `STACK.md` §4.** |
| **Storing `Date` / `Instant` as `String`** | No type safety, no timezone handling, sortable only with effort | Use `Long` (epoch millis) or `kotlinx-datetime.Instant` with a TypeConverter. |
| **Chat message bodies as TEXT but with embedded binary** (e.g., base64 images) | Bloats DB, slow JSON parsing | Store binary separately (file system) and reference by path. For Warped today (text-only) this is not an issue, but the Phase 41 thinking panel may store reasoning text + final response — keep them in separate columns. |

### OkHttp / SSE (network)

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| **SSE read loop without `coroutineContext.isActive`** | Cancellation hangs; see Critical #10 | `while (isActive) { val line = source.readUtf8Line() ?: break; ... }`. |
| **Streaming call without `retryOnConnectionFailure(false)`** | SSE mid-stream reconnect → duplicate or out-of-order tokens | `OkHttpClient.Builder().retryOnConnectionFailure(false)` for the SSE-specific client. |
| **Reading the entire `ResponseBody` into memory** before parsing | OOM on long responses | Use `response.body!!.source()` and stream chunks. |
| **Missing `Authorization` header in SSE call** | 401 mid-stream (very hard to debug) | Add auth in an `Interceptor`, not inline. The interceptor must not retry the SSE call on 401 (just emit the error). |
| **Large model download with `Range` header on redirect** | First request has `Range`, redirect strips it, second request returns 200 OK from byte 0 → file is 2x the expected size | After `response.priorResponse != null` (i.e., a redirect happened), **re-add the `Range` header** to the new request. Or detect `Content-Length` mismatch and abort. |
| **Hugging Face auth token in query string** | Token logged in OkHttp logging interceptor | Use `Authorization: Bearer <token>` header. Disable query-string logging in the interceptor. |

### LiteRT-LM (local inference)

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| **`Engine.initialize()` on Main** | ANR | `withContext(Dispatchers.IO) { engine.initialize() }`. See Critical #2. |
| **Forgetting `cacheDir` in `EngineConfig`** | Cold load every time, no mmap cache | `EngineConfig(modelPath = ..., backend = ..., cacheDir = context.cacheDir.path)`. See Critical #4. |
| **No backend detection** | GPU-capable device falls back to CPU | Check `PackageManager.hasSystemFeature("android.hardware.vulkan")` or use `Backend.GPU()` with try/catch fallback to `Backend.CPU()`. |
| **Missing `<uses-native-library>` for `libvndksupport.so`, `libOpenCL.so`, `libcdsprpc.so`** | Play Store filters app as incompatible with GPU-capable devices | Add the three `uses-native-library` entries to `AndroidManifest.xml` per `STACK.md` §3. |
| **Reusing `Engine` across model switches** | Old model memory not freed; OOM on second model | `engine.close()` before `initialize()` of the new one. Wrap in a `try/finally`. |
| **R8 stripping `MessageCallback`** (LiteRT-LM listener) | Runtime crash on first response | Keep rules per Critical #5. |
| **LiteRT-LM 0.13.1 NPU backend on non-validated devices** | Crash with NPE inside `libcdsprpc.so` | Default to CPU; opt into NPU only after device allowlist (per `STACK.md` §3, NPU deferred to v2.x). Gallery's open issues #905 and #920 document NPU crashes. |
| **`Message.system(...)` for thinking-system-prompt** | Hidden behavior — user can't override | Make system instruction user-editable in a future "Advanced" settings screen, or omit entirely for chat (only used for Prompt Lab). |

### WorkManager (background work)

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| **Download Worker without `setForeground()`** | Killed within 5-10 min of app backgrounding | `setForeground(ForegroundInfo(NOTIF_ID, buildNotification(progress)))` immediately on `doWork()` start. |
| **No `Constraints` for download** | Uses cellular data for a 2GB download, user gets $50 bill | `Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresBatteryNotLow(true)`. Let user override in settings. |
| **WorkInfo observation via polling** | Drains battery, lags UI | `WorkManager.getWorkInfoByIdFlow(workId).collect { ... }` (Flow API). |
| **Retrying with the same backoff** | Server stays down, retries hammer it | `BackoffPolicy.EXPONENTIAL` with `setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)`. Custom retry on specific exceptions. |
| **Not persisting bytes-downloaded** | Resume restarts from 0 | Persist to Room every 5s. Read on Worker start. |
| **No progress notification channel** | `ForegroundInfo` throws on Android 8+ | Create the channel in `Application.onCreate` BEFORE any Worker can start. |

---

## Performance Traps

Patterns that work at small scale but fail as usage grows. Targets: cold start < 1.5s on Pixel 7, TTFT < 800ms warm, 60fps during streaming, peak memory < 1.5× model size.

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| **Hilt `@Provides` for `LiteRtLlmEngine` at SingletonComponent** | First call to `LlmModelHelper` triggers native `.so` load on Main | Use `Lazy<LiteRtLlmEngine>` injection or a `Provider<>` wrapper. Resolve the engine only when a model is actually selected. | Cold start, every time |
| **Eager Room `Flow` collection in `LaunchedEffect`** without `collectAsStateWithLifecycle` | Battery drain, leaks, work when app is backgrounded | `val state by viewModel.state.collectAsStateWithLifecycle()` everywhere. | First app, any backgrounding |
| **Hugging Face model search via Retrofit synchronous call** | ANR on slow networks | Retrofit `suspend fun` + coroutines + loading state. | First search on 3G |
| **`Layout` re-measure on every streaming token** (MessageBubble's `Modifier.height()` recomputes) | Visible jank during streaming | Use `Modifier.wrapContentHeight()` + stable `key()` per message. | 5+ visible messages during streaming |
| **`Bitmap` allocation for every code block render** | GC pressure, jank spikes | Cache bitmaps with `remember { ... }` keyed on `codeBlockContent.length / 100`. | 200+ line code blocks, 10+ visible |
| **LM Studio SSE buffer size 1 KB** | Underutilizes network, missed events | `OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS)` (no read timeout for SSE) + manual chunked reads. | 4G networks, server throttling |
| **Eager `WorkManager` configuration** in `Application.onCreate` | +200-500ms cold start | Configure lazily on first Worker submission. Use `WorkManager.initialize(context, Configuration.Builder()...build())` only when needed. | Cold start, every time |
| **JSON parsing of large benchmark history on Main** | UI freeze when history tab opens | `withContext(Dispatchers.Default) { json.parse(...) }`. | 100+ benchmark entries |
| **Unstable `List<Model>` in LazyColumn** (per Critical #11) | Every state emission recomposes every row | `ImmutableList<Model>` + `@Immutable` annotation. | 5+ visible rows in model list |
| **No TTL on OkHttp `Cache`** | 50 MB cache grows to 500 MB | `OkHttpClient.Builder().cache(Cache(File(context.cacheDir, "okhttp"), 50L * 1024 * 1024))`. | Heavy users, 3+ months |
| **Engine mmap cache not capped** (per Critical #4) | 1.5 GB disk usage on 12B models | LRU eviction on app background, 500 MB cap. | Power users with multiple 12B models |
| **Reading chat history from Room on every render** | Scroll jank in long conversations | Pre-load messages to a `StateFlow<List<Message>>` in VM, observe with `distinctUntilChanged`. | 1000+ message conversations |
| **O(n²) Benchmark work loop** (re-running the same prompt) | Benchmarks take 10× longer than needed | Cache benchmark results by `(modelId, promptId, configHash)`. Re-run only if config changes. | Multi-benchmark history |
| **`Map<String, Any>` for `extraContext` in `LlmModelHelper.runInference`** | Boxing overhead per token | Use a sealed class or specific `data class` for context. | Benchmark runs (10s of thousands of invocations) |

---

## Security Mistakes

Beyond OWASP — specific to Warped's local LLM + remote provider model.

| Mistake | Risk | Prevention |
|---------|------|------------|
| **Storing LM Studio API keys in Room** alongside chat history | API key in DB, possibly backed up to Google Drive | Keep API keys in `EncryptedSharedPreferences` (already done since v1.0). DB is for non-sensitive metadata only. |
| **Logging full model allowlist JSON in `Timber.d()`** | Allowlist shows model size, capabilities — minor info leak | Wrap `Timber.d(allowlistJson)` in `BuildConfig.DEBUG` guard. Production logs go to crash handler (which only collects stack traces, not app data). |
| **R8 keep rule `-keep class ** { *; }` as a global fix** | Whole app not obfuscated, 30%+ APK size increase, zero R8 benefit | NEVER use a wildcard keep rule. Be specific: `-keep class com.google.ai.edge.litertlm.** { *; }`. |
| **Adding ProGuard rules for `kotlin-reflect`** to silence R8 warnings | Enables `kotlin-reflect` to be re-added later, drags in 2.5 MB | If R8 warns about kotlin-reflect access, fix the source code (use a proper API), not the rules. |
| **Disabling `network_security_config.xml` cleartext blocking for "easier LAN testing"** | Plaintext HTTP for any host, including non-LAN | Already done correctly: LAN-only cleartext allowed (per ENDPT-06). Verify the config XML uses `<domain-config cleartextTrafficPermitted="true">` with explicit `192.168.x.x`, `10.x.x.x`, `172.16-31.x.x` subnets. |
| **Loading a `.litertlm` model from external storage without path validation** | Path traversal: `../../etc/passwd` (won't work, but similar path shenanigans) | Reject paths containing `..` or starting with `/` (only allow `context.filesDir`-relative paths). |
| **`SQLCipher` removal without audit** (per `STACK.md` §4) | If threat model includes "forensic analysis of a stolen phone", unencrypted DB leaks chat history | Make the SQLCipher decision in Phase 43 (Performance audit) with a real threat model, not as a side effect. |
| **Adding `<uses-permission android:name="android.permission.INTERNET"/>` twice** (debug + release) | Merged manifest has duplicate, Play Store warns | One declaration in `app/src/main/AndroidManifest.xml`. No flavor-specific overrides unless truly needed. |
| **R8 leaving a `BuildConfig.DEBUG` field in release** | Debug-only code paths enabled in release (e.g., verbose logging, dev endpoints) | R8 strips `BuildConfig.DEBUG = false` branches. Verify with `apkanalyzer` that release APK has no `Timber.plant(DebugTree())` call. |
| **Disabling R8 for "easier debugging"** | 30% larger APK, no obfuscation, 10% slower cold start | Don't. Use `mapping.txt` for crash deobfuscation. R8 debug builds with `isMinifyEnabled = true` are still debuggable (mapping lines up). |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| **Thinking mode toggle hidden behind 3 taps** | User has a reasoning model loaded but doesn't know thinking is on/off | Surface in the chat input row: a chip showing "💭 Thinking ON" / "💭 Thinking OFF" — tap to toggle. Same place as model selector. Gallery's pattern. |
| **Benchmark runs in foreground, blocks UI for 5+ minutes** | User thinks the app is frozen | Run benchmark in WorkManager with foreground service + live progress notification. "Benchmark running: 42% complete, ETA 3:24". |
| **Benchmark results show only "X tok/s" with no context** | User doesn't know if 30 tok/s is good or bad | Show comparison: "Gemma-3-1B: 30 tok/s (vs 25 tok/s on Pixel 7 reference)". Gallery's pattern, but per-device. |
| **Model allowlist JSON ships 50 models; user scrolls forever** | Overwhelming | Curated "Recommended" section at top (already in v1.8 REC-01), then "All models" below. Gallery uses a similar split. |
| **Loading a 3GB model with no progress indicator** | User thinks the app crashed | Stepped progress: "Reading model... 40%", "Initializing engine... 60%", "Compiling for GPU... 80%", "Ready". The LiteRT-LM native call returns when ready, but multiple internal steps can be observed via a `ProgressListener`. |
| **Stop button during streaming doesn't also stop the local model from being held in memory** | Battery drain — engine idle but loaded | On `stopResponse`, also call `engine.pause()` if the API supports it, or evict the engine to free RAM after N seconds of inactivity. Gallery's behavior is to keep the model loaded — power user feature, not default. |
| **Streaming token arrives but UI scrolls past it** (auto-scroll failed) | User misses the start of the response | Use `LazyListState.animateScrollToItem(latestIndex)` after every emission during streaming. Throttle to every 200ms during long streams. |
| **Two parallel chat sessions show token counts in the wrong tab** | Confusing | Make `partialThinkingResult` and `partialResult` per-message, not per-conversation. Stale closures over `Conversation` ID. |
| **Benchmark results viewer overlays data from different models in the same chart** | Misleading | One chart per model. Color-code by config (top_k, top_p) so user can compare settings. |
| **MCP tool call returns a wall of JSON to the chat** | Overwhelming | Format tool responses with `MarkdownText` (existing renderer). Strip unnecessary fields. Gallery's pattern: tool result wraps in `<tool_result>` tag and renders as code block. |
| **Skills Lite: skill chip shows "calculator" but calling it sends 2KB of code as system prompt** | Slow first response, weird behavior | Show the skill's full prompt in a tooltip / long-press info. Let the user see what the model is "thinking" they're asking for. |

---

## "Looks Done But Isn't" Checklist

Things that pass local testing but fail in production or at scale.

- [ ] **LlmModelHelper ported from Gallery:** Works in debug build with a small model on the dev's Pixel 7. **Verify:** Production-signed release APK, Galaxy S22 (Exynos, different GPU), Xiaomi 13 (Snapdragon 8 Gen 2, MIUI doze), emulator (x86_64 only, no GPU). All four must load and run a 7B model.
- [ ] **LiteRT-LM 0.13.1 upgrade:** Compiles, runs, output looks the same as 0.12. **Verify:** A user with an existing 0.12-era `.litertlm` file tries to load it under 0.13.1 — does it succeed, or does Critical #3 fire?
- [ ] **Thinking mode toggle:** UI chip appears, taps toggle, message stream shows thinking tokens. **Verify:** A non-thinking model (e.g., older Gemma 3) loads with the toggle enabled — does the model silently ignore the toggle, or does Warped correctly suppress the panel? (Suppress; capability gate via allowlist.)
- [ ] **Benchmark runs end-to-end:** Init time, prefill tok/s, decode tok/s, peak memory all recorded. **Verify:** A 12B model is benchmarked — does the app stay under 8GB RAM (warped's `largeHeap` cap)? Does the foreground notification survive screen-off for 5+ minutes?
- [ ] **Prompt Lab:** 5+ templates work, output renders. **Verify:** A template with markdown (table, code block, list) renders correctly. Templates are user-editable in v2.x? (Out of scope for v2.0, but the schema should leave room.)
- [ ] **Versioned model allowlist JSON:** Loads at app start, allows capability gating. **Verify:** An invalid JSON in the asset (simulate by corrupting) does NOT crash the app — falls back to v1.8's hardcoded list with a "Allowlist update failed" snackbar.
- [ ] **Hilt graph change:** A new `@Provides` is added for Phase 41/42. **Verify:** `./gradlew :app:assembleDebug --stacktrace` succeeds. Unit tests with `@HiltAndroidTest` still find all bindings. No runtime `MissingBinding` crashes.
- [ ] **Room migration v1.8 → v2.0:** A v1.8 DB file with 1000+ messages opens in v2.0, all messages intact. **Verify:** Write a `MigrationTest` in `androidTest/`. Add 50 messages to v1.8 schema, run the migration, assert 50 messages in v2.0 schema.
- [ ] **WorkManager retry on network drop:** A download is in progress, network goes away for 30s, comes back. **Verify:** Download resumes from the last persisted byte, not from 0. Exponential backoff is `BackoffPolicy.EXPONENTIAL`, not `LINEAR`.
- [ ] **Large file download with `Range`:** A 2GB download is paused at 1.2GB, the app is killed (not just backgrounded), reopened. **Verify:** Worker reads `DownloadProgress.bytesDownloaded = 1288490188`, re-sends `Range: bytes=1288490188-`, server returns `206 Partial Content`, file resumes. Final file is exactly 2,147,483,648 bytes.
- [ ] **SSE streaming with stop:** User sends a 500-token prompt, taps stop after 50 tokens. **Verify:** UI updates stop within 100ms. OkHttp connection is released within 200ms. The next `runInference` doesn't double-cancel the old call.
- [ ] **Compose BOM 2026.05.01 upgrade:** App builds, all screens render. **Verify:** Open `ChatScreen` in Layout Inspector. Confirm `MessageBubble` is **skipped** (not recomposed) on streaming token updates. Confirm `ModelsList` rows are skipped on filtered-search updates.
- [ ] **ProGuard/R8 release build:** All screens render, no `UnsatisfiedLinkError`, no `Serializer not found`, no `ClassNotFoundException`. **Verify:** `aapt2 dump strings app-release.apk | grep -c 'com.google.ai.edge.litertlm'` returns the expected class count (not 0, not 1). Run the full smoke test on a release APK.
- [ ] **mmap cache invalidation:** User loads a model, upgrades LiteRT-LM, loads again. **Verify:** First load is slow (cache cold), subsequent loads are fast. No corrupt output mid-conversation. Disk usage is capped at 500MB.
- [ ] **Performance baseline:** Cold start, TTFT, FPS during streaming, peak memory, APK size all measured. **Verify:** Numbers recorded in `BENCHMARKS.md` per Phase 43 exit criteria. Any regression > 10% blocks the release.

---

## Gallery Anti-Patterns to NOT Copy

This is the explicit list of "Gallery does this, but Warped should not." Each item has a reason and the correct Warped approach.

### Anti-Pattern A1: Gallery uses **kapt for Hilt** (legacy from pre-2.48)

**What Gallery does:** `kapt("com.google.dagger:hilt-android-compiler:2.58")` in `app/build.gradle.kts`.

**Why not for Warped:** Hilt 2.48+ supports KSP. Warped is on Hilt 2.59.2 with KSP. Adding kapt would 2–5× slow incremental builds for no benefit. Gallery's kapt is tech debt from before Hilt 2.48 (Dec 2023).

**Warped's correct approach:** KSP only. Already in `STACK.md` §2. Add a CI lint check: `./gradlew :app:dependencies --configuration kapt` should return nothing.

### Anti-Pattern A2: Gallery has **three JSON libraries** (kotlinx-serialization + Gson + Moshi)

**What Gallery does:** `kotlinx-serialization-json:1.7.3`, `gson:2.12.1`, `moshi-kotlin:1.15.2`, `moshi-kotlin-codegen:1.15.2` — all in the same project. Firebase Analytics uses Gson internally; Hugging Face OAuth uses Moshi.

**Why not for Warped:** Warped has no Firebase, no HF OAuth, no multi-source deserialization. One library is enough. Three is 4+ MB of APK bloat, three sets of ProGuard rules, three mental models.

**Warped's correct approach:** kotlinx-serialization only. Add the keep rules from Critical #6.

### Anti-Pattern A3: Gallery has **`kotlin-reflect:2.2.21`**

**What Gallery does:** Pulled in by `com.halilibo.compose-richtext` (which Gallery uses) for some dynamic Compose Navigation features.

**Why not for Warped:** Warped doesn't use `compose-richtext`. `kotlin-reflect` adds 2.5 MB. There is no feature in Warped that requires runtime reflection.

**Warped's correct approach:** Don't add it. If a library ever needs it transitively, check if a `@Serializable`-based alternative exists.

### Anti-Pattern A4: Gallery has **Firebase BOM + Analytics + Messaging**

**What Gallery does:** `firebase-bom:33.16.0`, `firebase-analytics`, `firebase-messaging`. Gallery uses Firebase for usage analytics and push notifications.

**Why not for Warped:** PROJECT.md §"Out of Scope" explicitly excludes Firebase/cloud sync. No telemetry, no push.

**Warped's correct approach:** Zero Firebase dependencies. No `google-services` plugin. No `google-services.json` in the repo.

### Anti-Pattern A5: Gallery uses **Ktor 3.4.3** (HTTP) + **MCP Kotlin SDK 0.8.0**

**What Gallery does:** Hugging Face and MCP both use Ktor client (`ktor-client-android`, `ktor-client-core`).

**Why not for Warped:** Warped has Retrofit + OkHttp 4.12.0, which are Android-standard and integrate better with the platform trust store. The MCP Kotlin SDK is built on Ktor — adopting it would force Ktor adoption. Warped's PROJECT.md scopes "MCP tool calling via LM Studio API" — that's client-side MCP via LM Studio's REST, not via the MCP SDK. If a v2.0 feature needs real MCP, add it then, not preemptively.

**Warped's correct approach:** Retrofit + OkHttp for everything. For MCP in v2.x (if landed), write a minimal JSON-RPC-over-HTTP client in OkHttp. Don't pull in MCP SDK unless absolutely required.

### Anti-Pattern A6: Gallery uses **`compose-richtext` + `commonmark`**

**What Gallery does:** `com.halilibo.compose-richtext:richtext-ui-material3:1.0.0-alpha02`, `richtext-commonmark:1.0.0-alpha02`. Renders markdown via a Compose DSL.

**Why not for Warped:** Warped's `MarkdownText` is a custom block-based Compose renderer (v1.6 decision). It supports per-block composables (language header, copy button, expand/collapse) that `compose-richtext`'s whole-AST approach makes awkward. v1.6 explicitly rejected the migration.

**Warped's correct approach:** Keep `MarkdownText`. Phase 42 (Prompt Lab) reuses the existing renderer, doesn't introduce a new one.

### Anti-Pattern A7: Gallery uses **Proto DataStore** for `UserData` + `BenchmarkResults`

**What Gallery does:** `protobuf-javalite:4.26.1`, `protobuf:0.9.5` plugin, `BenchmarkResultsSerializer.kt`, `UserDataSerializer.kt`. Proto schemas in `app/src/main/proto/`.

**Why not for Warped:** Warped uses `androidx.datastore:datastore-preferences:1.2.1` (Preferences DataStore). For v2.0's Benchmark history (Phase 41), Proto is overkill — a Room table with a JSON column is simpler and supports the queries Warped needs ("benchmark results for model X", "all runs in the last 30 days"). Proto only wins for deeply nested, versioned schemas.

**Warped's correct approach:** Keep Preferences DataStore. Use Room for benchmark history. Skip Proto.

### Anti-Pattern A8: Gallery uses **CameraX 1.4.2** for multimodal (Ask Image)

**What Gallery does:** `cameraX-core`, `cameraX-camera2`, `cameraX-lifecycle`, `cameraX-view`. Used by `Ask Image` task.

**Why not for Warped:** PROJECT.md §"Out of Scope" excludes "Image/multimodal models". Warped has no camera feature.

**Warped's correct approach:** No CameraX dependencies. Skip Ask Image entirely.

### Anti-Pattern A9: Gallery has **AICore system service** runtime (Pixel 8+ only)

**What Gallery does:** `runtime/aicore/` directory, `mlkit-genai-prompt:1.0.0-beta2`. Uses Android 14+ system-level LLM.

**Why not for Warped:** Narrow device support (Pixel 8+), still preview per Gallery's allowlist (`aicoreReleaseStage: PREVIEW`). Zero benefit for non-Pixel users. v2 deferred in REQUIREMENTS.md.

**Warped's correct approach:** No AICore. No mlkit-genai-prompt. Use LiteRT-LM exclusively.

### Anti-Pattern A10: Gallery uses **`play-services-tflite-*`** for legacy TFLite

**What Gallery does:** `play-services-tflite-java:16.4.0`, `play-services-tflite-gpu:16.4.0`. Legacy TFLite for older device support.

**Why not for Warped:** Warped uses LiteRT-LM (the new framework) directly via the AAR. TFLite is the legacy. Warped already migrated to LiteRT-LM in v1.5; no reason to keep both.

**Warped's correct approach:** Single engine: `litertlm-android:0.13.1`. Zero TFLite dependencies.

### Anti-Pattern A11: Gallery uses **AppAuth 0.11.1** for OAuth

**What Gallery does:** `net.openid:appauth:0.11.1` for Hugging Face OAuth.

**Why not for Warped:** Warped doesn't need OAuth. Hugging Face models are public (no auth needed for download). LM Studio v1 endpoints take an API key (stored in EncryptedSharedPreferences). No OAuth flow required.

**Warped's correct approach:** No AppAuth. No OAuth.

### Anti-Pattern A12: Gallery's **JS webview skill runtime** (Agent Skills full version)

**What Gallery does:** Agent Skills spins up a `WebView` per skill, injects JavaScript from the skill's `index.html`, communicates via `addJavascriptInterface`.

**Why not for Warped:** Heavy (200-500ms WebView init per skill), security surface (arbitrary `index.html` → XSS), and 1+ MB per skill in `assets/`. Agent Skills Lite (Phase 44, optional) uses pure-Kotlin tools, no webview.

**Warped's correct approach:** Kotlin-only skills, in-process. No webview. (Defer Phase 44 to v2.1 if at all.)

### Anti-Pattern A13: Gallery's **`com.google.mlkit:genai-prompt`** for Gemini Nano

**What Gallery does:** `mlkit-genai-prompt:1.0.0-beta2` for the AICore backend.

**Why not for Warped:** Same as A9. Pixel-only, preview, no benefit.

**Warped's correct approach:** No mlkit-genai-prompt. Use LiteRT-LM.

### Anti-Pattern A14: Gallery's **mmap cache shared across all models**

**What Gallery does:** `cacheDir = context.cacheDir.path` passed to `EngineConfig` without subdirectory versioning. One shared cache directory for all loaded models.

**Why not for Warped:** When LiteRT-LM bumps (e.g., 0.12 → 0.13), the cache format changes. Warped must namespace the cache by runtime version to avoid Critical #4.

**Warped's correct approach:** `File(context.cacheDir, "litertlm/${BuildConfig.LITERTLM_VERSION}/")`. Per Critical #4.

### Anti-Pattern A15: Gallery's `app/src/main/AndroidManifest.xml` doesn't set `android:configChanges="uiMode"`

**What Gallery does:** Doesn't override `uiMode` in `configChanges`. Toggling system dark mode recreates the Activity.

**Why not for Warped:** Recreation costs 100-300ms. Compose can re-theme without destroy/recreate. `STACK.md` §13 recommends adding this.

**Warped's correct approach:** `android:configChanges="uiMode|orientation|screenSize|smallestScreenSize|screenLayout"`. Verify the Activity handles `onConfigurationChanged` correctly (Compose auto-handles this for `MaterialTheme`).

### Anti-Pattern A16: Gallery's `largeHeap="true"` is set, but `extractNativeLibs` is `true` (not `false`)

**What Gallery does:** `android:extractNativeLibs="true"` for some debug convenience. APK is 10-20 MB larger than necessary.

**Why not for Warped:** `extractNativeLibs="false"` is the standard for AGP 8+. Saves 10-20 MB. Already in `STACK.md` §13.

**Warped's correct approach:** `android:extractNativeLibs="false"`. Verify the LiteRT-LM AAR's `.so` files load correctly from the compressed APK (they do — verified by Google's own AI Edge Gallery in production).

---

## Pitfall-to-Phase Mapping

Per `FEATURES.md` v2.0 scope, the recommended phase structure is 40-44. Each pitfall maps to the phase that should prevent it.

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Critical 1 (Gallery tech-debt adoption) | Phase 40 | `dependencies` audit empty; AGP 9.2.1 retained; Hilt 2.59.2 retained |
| Critical 2 (Engine.initialize() off-Main) | Phase 40 | Unit test asserts `initialize()` is called on `Dispatchers.IO`; Macrobenchmark time-to-first-frame < 1.5s |
| Critical 3 (LiteRT-LM 0.13 file format) | Phase 40 | `LiteRtLlmHelper` catches `LiteRtLmJniException` for "Unsupported or unknown file format" and surfaces `LoadError.StaleModelFile` |
| Critical 4 (mmap cache stale) | Phase 40 | Cache dir is `litertlm/${BuildConfig.LITERTLM_VERSION}/`; cap at 500MB; LRU eviction on background |
| Critical 5 (R8 strips LiteRT-LM JNI) | Phase 40 | `proguard-rules.pro` has all keep rules; `aapt2 dump strings` confirms `com.google.ai.edge.litertlm.*` present in release APK |
| Critical 6 (R8 strips kotlinx-serialization) | Phase 40 | Release build runs through all DTOs without `SerializationException` |
| Critical 7 (SSE/Flow backpressure) | Phase 40 | `Channel.UNLIMITED` for inference stream; token counter exposed in UI; Benchmark numbers match expected tok/s |
| Critical 8 (Hilt graph cycle) | Phase 40 | `@Binds` for `LlmModelHelper`; `LiteRtLlmHelper` does not depend on `ModelRepository` |
| Critical 9 (Room migration data loss) | Phase 41 | `MigrationTest` for v1.8 → v2.0 in `androidTest/`; `@AutoMigration` for additive changes; `schemas/` committed |
| Critical 10 (SSE cancellation socket leak) | Phase 40 | `stopResponse` cancels the call AND signals read loop; OkHttp `callTimeout(60s)`; unit test asserts < 200ms cancellation |
| Critical 11 (Compose strong-skipping + List<T>) | Phase 43 | `ImmutableList<T>` on all public composable params; Layout Inspector confirms skip; Macrobenchmark frameOverrunMs < 5ms |
| Critical 12 (WorkManager battery killer) | Phase 43 | `setForeground()` always; Room `DownloadProgress` every 5s; doze handling; Chinese OEM UX |
| Tech-debt: kapt | Phase 40 | CI lint: `dependencies --configuration kapt` empty |
| Hilt: ViewModel scope | Phase 40 | `hiltViewModel(backStackEntry)` everywhere; all VMs survive recomposition |
| Room: missing composite index | Phase 43 | `messages(conversation_id, created_at)` indexed; `EXPLAIN QUERY PLAN` confirms |
| OkHttp: SSE without cancellation | Phase 40 | See Critical #10 |
| LiteRT-LM: missing cacheDir | Phase 40 | `EngineConfig.cacheDir` set per `STACK.md` §3 |
| LiteRT-LM: missing native-library manifest entries | Phase 40 | All three `<uses-native-library>` entries in manifest |
| WorkManager: no foreground service | Phase 43 | `setForeground()` mandatory for downloads > 30MB; notification channel created in `Application.onCreate` |
| Gallery anti-patterns A1-A16 | Phase 40 | `dependencies` audit + manifest audit + `proguard-rules.pro` audit, all at phase start |
| "Looks Done But Isn't" checklist | Phases 40-44 | Each item is a phase exit criterion (see [Looks Done But Isn't](#looks-done-but-isnt-checklist)) |

---

## Sources

### Primary (HIGH confidence)

- **Google AI Edge Gallery source tree** — [github.com/google-ai-edge/gallery](https://github.com/google-ai-edge/gallery)
  - [`LlmModelHelper.kt`](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/runtime/LlmModelHelper.kt) — interface signature, `ResultListener` typealias, `CleanUpListener` typealias
  - [`libs.versions.toml`](https://github.com/google-ai-edge/gallery/blob/main/Android/src/gradle/libs.versions.toml) — confirmed kapt, Gson, Moshi, kotlin-reflect, Firebase, Ktor, MCP, TFLite, mlkit-genai-prompt, AppAuth, CameraX, compose-richtext, commonmark, Proto DataStore
  - Gallery open issues [#896 (AICore benchmark crash)](https://github.com/google-ai-edge/gallery/issues/896), [#905 (NPU crash on imported models)](https://github.com/google-ai-edge/gallery/issues/905), [#907 (4096 token context limit)](https://github.com/google-ai-edge/gallery/issues/907), [#910 (GPU on A55 broken)](https://github.com/google-ai-edge/gallery/issues/910), [#917 (MacOS local skill import)](https://github.com/google-ai-edge/gallery/issues/917), [#920 (NPU crash)](https://github.com/google-ai-edge/gallery/issues/920), [#921 (inference stats missing)](https://github.com/google-ai-edge/gallery/issues/921)
- **LiteRT-LM 0.13.1 docs** — [developers.google.com/edge/litert-lm/android](https://developers.google.com/edge/litert-lm/android), last updated 2026-05-28. Confirmed: `Engine.initialize()` blocking warning, `cacheDir` parameter, `Backend.GPU()`, `MessageCallback`, `ToolProvider` API
- **LiteRT-LM 0.13 release notes** — [github.com/google-ai-edge/LiteRT-LM/releases](https://github.com/google-ai-edge/LiteRT-LM/releases). Confirmed: Agent Skill support, OpenAI-compatible server CLI, MTP, speculative decoding
- **LiteRT-LM open issue** — [google-ai-edge/LiteRT-LM#2454](https://github.com/google-ai-edge/LiteRT-LM/issues/2454), opened 2026-06-03: "litertlm-android 0.12.0: Gemma 4 E2B .litertlm fails Engine.initialize() with 'INVALID_ARGUMENT: Unsupported or unknown file format'"
- **Warped project state** — [.planning/PROJECT.md](.planning/PROJECT.md) (v2.0 milestone, scope), [.planning/REQUIREMENTS.md](.planning/REQUIREMENTS.md) (v1.8 feature surface), [.planning/STATE.md](.planning/STATE.md) (accumulated context), [.planning/research/STACK.md](.planning/research/STACK.md) (stack ground truth), [.planning/research/FEATURES.md](.planning/research/FEATURES.md) (v2.0 features and phase plan), [.planning/research/ARCHITECTURE.md](.planning/research/ARCHITECTURE.md) (existing v1.6 syntax-highlighting architecture — informs v2.0 perf audit areas)

### Secondary (MEDIUM-HIGH confidence)

- **Android Developers Blog — R8 Keep Rules** — [android-developers.googleblog.com/2025/11/configure-and-troubleshoot-r8-keep-rules](https://android-developers.googleblog.com/2025/11/configure-and-troubleshoot-r8-keep-rules.html), 18 Nov 2025. Confirmed: `proguard-android-optimize.txt` is mandatory in AGP 9.0+, full mode is more aggressive, JNI keep rules required
- **Android Developer Guide — Migrate your Room database** — [developer.android.com/training/data-storage/room/migrating-db-versions](https://developer.android.com/training/data-storage/room/migrating-db-versions), last updated 2026-03-05. Confirmed: `MigrationTestHelper`, `fallbackToDestructiveMigration()` warning, `fallbackToDestructiveMigrationFrom()` for specific versions
- **Android Developer Guide — Compose Performance best practices** — [developer.android.com/develop/ui/compose/performance/bestpractices](https://developer.android.com/develop/ui/compose/performance/bestpractices). Confirmed: `remember` for expensive calculations, lazy layout keys, derived state
- **Android Developer Guide — Capture Macrobenchmark metrics** — [developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics). Confirmed: `StartupTimingMetric`, `FrameTimingMetric`, `timeToInitialDisplayMs`
- **Android Macrobenchmark instrumentation args** — [developer.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args). Confirmed: `SideEffectRunListener` for benchmarking, `METHOD-TRACING-ENABLED` warning
- **StackOverflow — kotlinx-serialization ProGuard rules** — [stackoverflow.com/questions/70663076](https://stackoverflow.com/questions/70663076/how-to-make-proguard-keep-kotlinx-serializers-for-objects). Confirmed canonical keep rules
- **Medium — Hilt ViewModel NavBackStackEntry scoping** — [medium.com/@ahmed.ally2](https://medium.com/@ahmed.ally2/scoping-hilt-viewmodels-to-the-navigation-back-stack-in-jetpack-compose-1d961e94654a), Nov 14 2025. Confirmed: `hiltViewModel(backStackEntry)` pattern
- **Davide Agostini — Hilt Deep Dive** — [davideagostini.com](https://www.davideagostini.com/android/2026-02-18-hilt-di-deep-dive), 18 Feb 2026. Confirmed: scope decision tree (`@Singleton` vs `@ActivityRetainedScoped` vs `@ViewModelScoped`)
- **Davide Agostini — Baseline Profiles** — [davideagostini.com/android/2026-02-25-baseline-profiles](https://www.davideagostini.com/android/2026-02-25-baseline-profiles), 25 Feb 2026. Confirmed: BAD vs GOOD profile generation patterns
- **SoftwareDevs mvpfactory.io — Compose Recomposition at Scale** — [dev.to/software_mvp-factory](https://dev.to/software_mvp-factory/jetpack-compose-recomposition-at-scale-how-strong-skipping-mode-changes-the-stability-rules-you-4a80), 12 Mar 2026. Confirmed: strong skipping mode, `kotlinx.collections.immutable`, `@Stable`/`@Immutable` patterns
- **QyperXit android-skills — Compose Performance Audit** — [github.com/QyperXit/android-skills](https://github.com/QyperXit/android-skills/blob/main/compose-performance-audit/SKILL.md). Confirmed: full audit recipe
- **Ktor SSE docs** — [ktor.io/docs/client-server-sent-events.html](https://ktor.io/docs/client-server-sent-events.html), 24 Apr 2026. Confirmed: SSEBufferPolicy, maxReconnectionAttempts (informational — Warped is not adopting Ktor)

### Tertiary (MEDIUM confidence)

- **CodingTechRoom — Resumable file download best practices** — [codingtechroom.com](https://codingtechroom.com/question/how-to-implement-resumable-file-downloading-and-uploading-in-android). Confirmed: Range header patterns, common mistakes
- **Dev.to — Room Database Migrations** — [dev.to/myougatheaxo](https://dev.to/myougatheaxo/room-database-migrations-changing-your-schema-without-losing-user-data-54ha), 2 Mar 2026. Confirmed: migration patterns
- **DEV.to — Compose State Management** — [dev.to/myougatheaxo](https://dev.to/myougatheaxo/state-management-in-jetpack-compose-remember-mutablestateof-and-beyond-4845), 2 Mar 2026. Confirmed: `remember`, `mutableStateOf` patterns

### Confidence notes

- **HIGH**: All Library / file-format / manifest / R8 rule facts. Verified by direct source inspection in this research session.
- **MEDIUM**: Performance impact estimates (e.g., "5-10× faster warm start with mmap cache", "10-30% SQLCipher overhead") are well-documented in their respective docs but not measured on Warped hardware. Verify with a Macrobenchmark run in Phase 43.
- **LOW**: The exact list of LiteRT-LM v0.13.x file-format-breaking changes from v0.12 is partially known (issue #2454 confirms one). Other breaking changes may emerge as Warped upgrades. **Action**: include regression smoke tests for `Engine.initialize()` of all v1.8-era `.litertlm` files in Phase 40.

---

*Pitfalls research for: Warped v2.0 Gallery Convergence & Performance Overhaul*
*Researched: 2026-06-05*
*Reference: google-ai-edge/gallery v1.0.15 (May 2026) + LiteRT-LM 0.13.1 (June 2026)*
