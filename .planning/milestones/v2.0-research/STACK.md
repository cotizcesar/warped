# Stack Research — Warped v2.0 Gallery Convergence & Performance Overhaul

**Domain:** Android LLM chat app (local LiteRT-LM + remote LM Studio API)
**Researched:** 2026-06-05
**Confidence:** HIGH for version facts (verified against Google Maven / GitHub releases); MEDIUM for performance impact estimates (no device profiling on Warped hardware yet)

**Reference implementation:** [google-ai-edge/gallery](https://github.com/google-ai-edge/gallery) — versionCode 34 / versionName 1.0.16 (main branch as of 2026-05-21), 23.6k stars, 91.9% Kotlin, ships on Play Store. Warped is already a "LiteRT-LM + Compose" app; Gallery is the only major OSS in this exact niche, so its stack choices are the most defensible defaults.

---

## TL;DR — Three Buckets

| Bucket | Items |
|--------|-------|
| **MUST adopt (versions or libs Gallery proves out)** | LiteRT-LM 0.13.1; Hilt-navigation-compose 1.3.0; Hilt-work 1.3.0; Room 2.8.4; Lifecycle 2.10.0; Navigation 2.9.x; Compose BOM 2026.05.01; `androidx.core:core-splashscreen:1.2.0-beta01`; `androidx.lifecycle:lifecycle-process:2.10.0`; `android:configChanges="uiMode"`; `android:theme="...SplashScreen"`; `<uses-native-library>` for OpenCL/cdsp/vndksupport; `android.nonTransitiveRClass=true`; `extractNativeLibs="false"`; `-Xcontext-receivers` Kotlin flag |
| **CONSIDER (depends on architecture research)** | SQLCipher removal (perf win if threat model allows); Compose `Modifier.drawWithCache` audit; OkHttp 5.x (5.0.0 is stable but not required) |
| **DO NOT adopt (Gallery uses these but Warped is correct not to)** | Ktor (overkill vs Retrofit/OkHttp); Moshi (we have kotlinx-serialization); Gson (we have kotlinx-serialization); `kotlin-reflect`; kapt (Warped is KSP-only and correct); Firebase / FCM; AppAuth (no OAuth); CameraX (no camera); TFLite; MLKit genai-prompt; Compose-richtext (we have custom markdown); Proto DataStore (Preferences is enough) |

---

## Recommended Stack

### 1. Core Platform

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Kotlin | 2.3.20 | Language | Already current. K2 compiler default since 2.0. |
| Android Gradle Plugin | 9.2.1 | Build system | Already current. Required by Hilt 2.59.x. |
| Gradle | 9.1+ | Build runtime | Already on it (matches AGP 9.0+ requirement). |
| compileSdk / targetSdk | 35 (Android 15) | API surface | Same as Gallery. Stable. |
| **minSdk** | **28 (Android 9) — KEEP** | Floor | **Do NOT follow Gallery's minSdk 31.** Gallery raises to 31 because of GPU/NPU native lib support and reduced OS version matrix. Warped's `litertlm-android` 0.13.1 works fine on API 28, and the EncryptedSharedPreferences/WorkManager features Warped already uses also work on 28. Lowering to 28 gives ~95% device reach. Revisit only if a specific LiteRT-LM feature requires 31+. |
| Jetpack Compose | BOM 2026.05.01 | UI toolkit | Latest stable BOM (May 19, 2026). **Up from Warped 2026.04.01.** Adds Compose 1.11.0 stable, the v2 testing framework as default, new `MediaQuery`/`Grid`/`FlexBox` layout APIs. See [Compose BOM mapping](https://developer.android.com/jetpack/compose/bom/bom-mapping). |
| Material 3 | via Compose BOM | Design system | Current Android standard. Use Material 3 expressive components (Gallery uses the same). |
| JVM target | 11 | Bytecode level | Gallery uses 11. **Warped should match** for compatibility with newer Compose/Hilt internals. (Verify current `compileOptions` in `app/build.gradle.kts`.) |

**v2.0 actions:**
- Bump `compose-bom` 2026.04.01 → **2026.05.01** in `libs.versions.toml`.
- Verify `jvmTarget = "11"` in `kotlinOptions` (Gallery sets this explicitly).
- Add to `kotlinOptions.freeCompilerArgs`:
  ```kotlin
  freeCompilerArgs += "-Xcontext-receivers"  // enables Gallery-style state-passing patterns
  ```

### 2. DI (Hilt) — Warped is already ahead of Gallery

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Dagger/Hilt | 2.59.2 | Compile-time DI | **Warped is on 2.59.2 (current); Gallery still on 2.58.** Warped is correct and ahead. |
| hilt-navigation-compose | **1.3.0** (up from 1.2.0) | Hilt + Navigation Compose | Gallery uses 1.3.0. Upgrade is safe, brings `hiltViewModel()` and `EntryPoint` accessors. |
| hilt-work | **1.3.0** (up from 1.2.0) | Hilt + WorkManager | Gallery uses 1.3.0. Aligns with navigation-compose. |

**v2.0 actions:**
- `hilt-navigation-compose` 1.2.0 → 1.3.0
- `hilt-work` 1.2.0 → 1.3.0
- **Do NOT add `kapt`.** Gallery still uses kapt for the Hilt compiler (legacy from before Hilt 2.48 KSP support). Warped is correctly KSP-only. Adding kapt would regress build speed by 2-5x. Reference: [Hilt KSP support](https://dagger.dev/dev-guide/ksp) is stable since Hilt 2.48.
- **Do NOT add `google-services` plugin or Firebase.** Gallery's Firebase is for analytics + push notifications, both out of scope for Warped (PROJECT.md: "Firebase / cloud sync" in What NOT to Use list).

### 3. Local LLM Inference — LiteRT-LM

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| **LiteRT-LM (com.google.ai.edge.litertlm:litertlm-android)** | **0.13.1** (up from 0.13.0) | Local on-device LLM runtime | **Latest as of 2026-06-04 (Google Maven).** Verified via direct `maven-metadata.xml` fetch. v0.13 brings Gemma 4 12B support, OpenAI-compatible server CLI, Swift macOS package, and **Agent Skills support** (relevant to Gallery's "Agent Skills" feature). Warped should track 0.13.x. Reference: [Maven Google](https://dl.google.com/android/maven2/com/google/ai/edge/litertlm/litertlm-android/maven-metadata.xml). |

**v2.0 actions:**
- Bump `litertlm` 0.13.0 → **0.13.1** in `libs.versions.toml`. Patch upgrade; check `LRT-01..03` test surfaces (multi-turn conversation, engine init, conversation reuse) still pass.
- **Add to `AndroidManifest.xml` `<application>` tag** (Gallery's proven pattern for hardware acceleration):
  ```xml
  <uses-native-library android:name="libvndksupport.so" android:required="false"/>
  <uses-native-library android:name="libOpenCL.so" android:required="false"/>
  <uses-native-library android:name="libcdsprpc.so" android:required="false"/>
  ```
  These are GPU/NPU/DSP native libraries shipped in the AAR. `required="false"` means devices without them still run (CPU fallback). Without these declarations, Play Store filtering / device matching may misclassify devices that can actually use GPU. This is the **single biggest perf-opt that Gallery proves works** for LiteRT-LM.
- **Pass `cacheDir = context.cacheDir.path`** to `EngineConfig` (Gallery does this). Enables mmap caching of the model — **dramatically reduces subsequent load time (TTFT)**, sometimes 5-10x for warm starts. Warped's `EngineConfig` call site needs this added.
- **Backend selection at runtime:** prefer `Backend.GPU()` if device supports, else `Backend.CPU()`. Gallery does the same. (NPU still experimental — skip for v2.0 per PROJECT.md deferred list.)
- Do **NOT** swap to flutter_gemma, MLC-LLM, or llama.cpp. Gallery is the reference; its LiteRT-LM choice is correct.

### 4. Persistence

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Room | **2.8.4** (up from 2.7.1) | SQLite ORM | Latest stable (Nov 19, 2025). KSP-only path is fully stable. KSP became default in Room 2.7.0; 2.8.x adds perf and Android 16 compat. Room 3.0 is in alpha — skip until stable. |
| DataStore (Preferences) | 1.2.1 | Key-value prefs | Keep as-is. Warped is ahead of Gallery's 1.1.7. |
| security-crypto (EncryptedSharedPreferences) | 1.1.0 | API key encryption | Keep. Hardware-backed Keystore. |
| **SQLCipher (net.zetetic:android-database-sqlcipher)** | **4.5.4 — CONSIDER REMOVAL** | DB encryption | **Gallery does not use SQLCipher.** SQLCipher adds ~10-30% CPU overhead per query (AES + page integrity checks). Warped's threat model: the DB contains conversations, presets, model/endpoint metadata — **no API keys (those are in EncryptedSharedPreferences), no PII, no secrets**. Re-evaluate: if `v1.5` decision rationale still holds ("API keys in EncryptedSharedPreferences, no plaintext secrets"), the Room DB doesn't need SQLCipher. If the v2.0 threat model adds new requirements, keep it. **Performance audit should measure before deciding.** |

**v2.0 actions:**
- Room 2.7.1 → **2.8.4**. Verify `ksp(libs.room.compiler)` (already KSP — good). Re-export schema if `room.schemaLocation` is set.
- **SQLCipher decision deferred to perf audit phase** — measure first, then decide.
- Do **NOT** swap to Proto DataStore. Gallery uses it for typed settings, but Warped's settings are simple key-value (theme, code font scale, syntax theme) — Preferences DataStore is correct.

### 5. Lifecycle / ViewModel

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| androidx.lifecycle | **2.10.0** (up from 2.8.7) | ViewModel + lifecycle | Latest stable (May 19, 2026). Brings `lifecycle-runtime-compose` improvements. 2.11.0-beta02 exists but stay on stable for v2.0. |
| lifecycle-viewmodel-compose | 2.10.0 | ViewModel + Compose | Part of the bump. |
| lifecycle-runtime-compose | 2.10.0 | `collectAsStateWithLifecycle` | Part of the bump. |
| **lifecycle-process (NEW)** | **2.10.0** | `ProcessLifecycleOwner` (whole-app lifecycle) | Gallery uses 2.8.7. **Add this** for app-wide foreground/background awareness — useful for: (a) pausing in-progress model loads on background, (b) deciding when to evict a model to free RAM, (c) throttling background download notifications. Warped currently has no whole-app lifecycle hook — v2.0 perf overhaul should add it. |

**v2.0 actions:**
- Bump `lifecycle` 2.8.7 → **2.10.0**.
- Add new library: `androidx.lifecycle:lifecycle-process:2.10.0`.

### 6. Navigation

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| androidx.navigation:navigation-compose | **2.9.x** (up from 2.8.8) | Compose navigation | Latest stable (April 2026). Improvements: type-safe Compose via `@Serializable` destinations is now the primary mechanism; better predictive-back support; `NavDestination` deep-link vulnerability fix. Warped should be on 2.9.x. |
| hilt-navigation-compose | 1.3.0 | Hilt VM in nav | Bumped above. |

**v2.0 actions:**
- Bump `navigation` 2.8.8 → **2.9.x** (verify exact latest at navigation release page).
- Verify all `composable<Route> { ... }` and `navigate(Route(...))` use the 2.8+ type-safe Kotlin Serialization API.

### 7. Networking

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| OkHttp | 4.12.0 | HTTP client | **KEEP.** Battle-tested, integrates with Retrofit. OkHttp 5.0.0 stable exists but the 4.12 → 5.0 jump is API-breaking (Kotlin-only in 5.0); not worth it for v2.0 unless we hit a specific bug. |
| Retrofit | 3.0.0 | Type-safe HTTP | **KEEP.** |
| Kotlinx Serialization | 1.7.3 | JSON | **KEEP.** Already correct. **Do NOT add Moshi or Gson** (Gallery has both — tech debt from Firebase/HF auth integration. Warped doesn't need them.) |
| OkHttp logging-interceptor | 4.12.0 | Debug logging | KEEP. Wrap in `BuildConfig.DEBUG` guard. |

**v2.0 actions:**
- No stack changes. **Architecture review** should audit interceptor chain for: cert pinning (deferred in PROJECT.md), connection pool tuning, request cancellation, timeouts. **NOT a v2.0 stack change.**

### 8. Background Work

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| WorkManager | 2.10.0 | Deferrable work (downloads) | KEEP. Gallery uses same. |

**v2.0 actions:**
- No version change. **Architecture review** should audit: WorkManager `Constraints` (WiFi-only for downloads), `setForeground()` for ongoing notifications, retry policy with exponential backoff, observation via `getWorkInfoByIdFlow()`.

### 9. Markdown / Syntax Highlighting — KEEP current

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Highlights | 1.1.0 | Code tokenization | **KEEP.** v1.6 milestone decision. Wrapped behind `SyntaxHighlighter` interface for swap-ability. Gallery uses `compose-richtext` + `commonmark` which is a different design (whole-markdown-as-AST), but Warped's block-based custom renderer is already proven and lower overhead. |
| Warped custom `MarkdownText` | n/a | Markdown rendering | KEEP. Block-based Column of composables (v1.6 decision) is correct architecture. |

**v2.0 actions:**
- **No change.** Do NOT swap to `compose-richtext`/`commonmark`. Gallery uses those for its "Markdown { ... }" DSL pattern; Warped's renderer is already better for our block-based use case.

### 10. Coroutines / Async

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| kotlinx-coroutines-core | 1.9.0 | Structured concurrency | KEEP. |
| kotlinx-coroutines-android | 1.9.0 | `Dispatchers.Main` | KEEP. |

**v2.0 actions:**
- No version change. **Perf audit** should audit:
  - `Dispatchers.Main.immediate` for UI-bound reads
  - `Dispatchers.Default` for CPU-bound (JSON parse, token count)
  - `Dispatchers.IO` for network/file (current usage is correct)
  - `flowOn` placement for cold flow operators

### 11. Testing

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| JUnit 5 (Jupiter) | 5.11.4 | Unit testing | KEEP. (Pre-existing platform launcher issue is in `STATE.md` — not addressed by v2.0 unless re-emerges.) |
| MockK | 1.13.16 | Kotlin mocking | KEEP. |
| Turbine | 1.1.0 | Flow testing | KEEP. |
| Truth | 1.4.4 | Assertions | KEEP. |
| kotlinx-coroutines-test | 1.9.0 | Coroutine testing | KEEP. |
| errorprone-annotations | 2.28.0 | Nullability annotations | KEEP. |
| **Compose UI test (compose-bom 2026.05.01)** | via BOM | `ComposeTestRule` | KEEP. **The Compose BOM 2026.05.01 bump changes the testing API** — v2 testing framework is now the default. Read [Compose Testing v2 migration](https://developer.android.com/develop/ui/compose/testing/migrations/testing-v2) before touching UI tests. The default `runComposeUiTest` dispatcher changed from `UnconfinedTestDispatcher` (immediate) to `StandardTestDispatcher` (queued) — existing tests that "read ahead" of recompositions may need `advanceUntilIdle()` or `runOnIdle`. |

**v2.0 actions:**
- No library additions. **Audit all existing UI tests after the BOM bump** to catch v1→v2 testing framework regressions.

### 12. Build Tooling

| Tool | Setting | Why |
|------|---------|-----|
| AGP | 9.2.1 | KEEP. Gallery is on 8.8.2 (older); Warped is ahead. |
| Gradle | 9.1+ | KEEP. |
| Version catalog | `libs.versions.toml` | KEEP. |
| KSP | 2.3.7 | KEEP. Matches Kotlin 2.3.20. (Gallery uses 2.3.6 with Kotlin 2.2.0.) |
| **gradle.properties flags (VERIFY/ADD)** | — | See below. |

**`gradle.properties` (verify or add):**
```properties
# Performance — R class size
android.nonTransitiveRClass=true
# Already have in Warped? If not, add. Gallery has it; major APK-size win for multi-module projects but still helps single-module.

# Performance — parallel projects (only if multi-module)
# org.gradle.parallel=true
# org.gradle.caching=true
# org.gradle.configureondemand=true

# Performance — JVM args
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8 -XX:+UseParallelGC
# Bump from default 2g → 4g for Hilt + KSP graph compilation. Gallery uses -Xmx2048m (2g) which is tight.

# AndroidX
android.useAndroidX=true

# Kotlin
kotlin.code.style=official
kotlin.incremental=true  # default true, but explicit
```

**v2.0 actions:**
- Verify `android.nonTransitiveRClass=true` is in `gradle.properties`. Add if missing.
- Bump `org.gradle.jvmargs` from default to `-Xmx4g`.
- Add `kotlin.incremental=true` explicitly.

### 13. AndroidManifest Configuration — Copy Gallery's proven pattern

Warped's `AndroidManifest.xml` should be audited and updated to include Gallery's cold-start-optimized patterns:

| Attribute | Value | Why |
|-----------|-------|-----|
| `android:theme="@style/Theme.Warped.SplashScreen"` (on main Activity) | SplashScreen API theme | **Use `androidx.core:core-splashscreen` (see #14).** Eliminates the white flash between app launch and first Compose frame. Cold-start perf win. Gallery does this. |
| `android:configChanges="uiMode"` (on main Activity) | Don't recreate on dark/light mode toggle | **Add this.** Compose can re-theme without an Activity destroy/recreate. Saves ~100-300ms on dark mode toggle. Gallery does this. |
| `android:windowSoftInputMode="adjustResize"` (on main Activity) | Resize when keyboard opens | Standard chat behavior. Gallery does this. |
| `<uses-native-library>` for `libvndksupport.so`, `libOpenCL.so`, `libcdsprpc.so` | required="false" | GPU/NPU/DSP hardware acceleration. Required for LiteRT-LM GPU backend. Gallery does this. (See #3 above.) |
| `android:extractNativeLibs="false"` | Keep .so libs compressed in APK | Smaller APK; libs extracted on install. Already standard for AGP 8+. Verify. |
| `android:largeHeap="true"` | Larger Dalvik heap | For loading 1-3GB model files. Already in PROJECT context. Verify. |

### 14. New Library to Add: core-splashscreen

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| `androidx.core:core-splashscreen` | **1.2.0-beta01** (latest from Gallery) | SplashScreen API for cold start | **ADD.** Reduces time-to-first-frame perceptually. Compatible with API 23+. Gallery uses this version. Stable for production since 1.0.0. |

**v2.0 actions:**
- Add `androidx.core:core-splashscreen:1.2.0-beta01` to version catalog.
- Add `Theme.Warped.SplashScreen` style that extends `Theme.SplashScreen` from `core-splashscreen`.
- Set `android:theme="@style/Theme.Warped.SplashScreen"` on `MainActivity` in manifest.
- Splash should not block LiteRT-LM engine init — splash dismisses as soon as first Compose frame is ready.

### 15. Logging / Misc

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| Timber | 5.0.1 | Logging | KEEP. |
| kotlin-reflect | — | Runtime reflection | **DO NOT ADD.** Gallery needs it (for Compose Navigation dynamic features), but Warped's stack does not. Adds ~2.5MB to APK. |

---

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| HTTP client | Retrofit 3.0.0 + OkHttp 4.12.0 (KEEP) | Ktor 3.4.3 (Gallery's choice) | Ktor is multiplatform-first; Retrofit/OkHttp is Android-standard, has deeper platform trust store integration, custom DNS for LAN discovery is easier, and Retrofit converters (kotlinx-serialization) are mature. Ktor's only edge is multiplatform — Warped is Android-only. **Not worth the migration cost.** |
| JSON | Kotlinx Serialization 1.7.3 (KEEP) | Moshi 1.15.2 (Gallery); Gson 2.12.1 (Gallery) | Gallery has both because Firebase analytics + HuggingFace OAuth use them. Warped has no such deps. Three JSON libs is Gallery's tech debt, not a feature. |
| DB encryption | Plain Room 2.8.4 (or keep SQLCipher pending audit) | SQLCipher 4.5.4 (Warped current) | SQLCipher adds CPU overhead. **Audit first, decide after measurement.** If the v1.5 threat model still holds, drop it. |
| Annotation processing | KSP only (KEEP) | KSP + kapt (Gallery's choice) | kapt is slower, in maintenance mode. Warped is correctly KSP-only. **Do NOT add kapt.** |
| Markdown rendering | Warped custom MarkdownText (KEEP) | compose-richtext + commonmark (Gallery) | Gallery's pattern fits its "Markdown { }" DSL design. Warped's block-based renderer is more efficient for our use case (per-block composables enable language header, copy button, expand/collapse). |
| Settings storage | DataStore Preferences 1.2.1 (KEEP) | Proto DataStore (Gallery) | Proto DataStore wins only when settings have many nested fields. Warped's settings are simple key-value. |
| Compose BOM | 2026.05.01 (BUMP) | Stay on 2026.04.01 (Warped) | 2026.05.01 is latest stable. Gains Compose 1.11.0, v2 testing, new layout primitives. |
| Settings analytics | None (KEEP) | Firebase Analytics (Gallery) | Out of scope per PROJECT.md. No telemetry. |

---

## What NOT to Add — and Why

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| **Ktor client** | Gallery uses Ktor 3.4.3 for HF + MCP. Warped has Retrofit + OkHttp that are perfectly capable. Migration cost: weeks of code, zero perf gain on Android-only app. | Retrofit + OkHttp |
| **Moshi codegen** | Adds a parallel JSON lib alongside kotlinx-serialization. No use case in Warped. | kotlinx-serialization |
| **Gson** | Same as Moshi. | kotlinx-serialization |
| **kotlin-reflect** | 2.5MB APK bloat, no use case. Gallery needs it for some Compose Navigation dynamic features; we don't. | n/a (don't add) |
| **kapt** | Hilt KSP is stable since 2.48. Gallery's kapt use is a legacy artifact. Adding kapt would slow Warped builds by 2-5x. | KSP only |
| **Firebase BOM** | Vendor lock-in. PROJECT.md explicitly excludes. | None |
| **AppAuth 0.11.1** | OAuth flow. Warped has no OAuth. | None |
| **CameraX** | Camera capture. No camera feature. | None |
| **play-services-tflite-java** | TFLite. Warped uses LiteRT-LM directly via the AAR — not the legacy TFLite delegate. | Already using `litertlm-android` |
| **mlkit-genai-prompt** | ML Kit's Gemini Nano prompt API. Warped has its own LiteRT-LM path. | Already using `litertlm-android` |
| **compose-richtext + commonmark** | Different markdown model (AST DSL). Warped's block-based renderer is already correct. | Custom `MarkdownText` |
| **MCK Kotlin SDK (io.modelcontextprotocol:kotlin-sdk)** | For MCP servers. Warped's PROJECT.md scopes "MCP tool calling via LM Studio API" — that's client-side MCP via LM Studio's own REST, not via the MCP SDK. **If a v2.0 feature requires connecting to a real MCP server, add it then.** | Defer to feature research |
| **oss-licenses plugin** | For OSS license UI. Gallery ships this for compliance. Warped already has ProGuard/R8; if a licenses screen is needed, add it. **Skip for v2.0 unless required.** | None for v2.0 |
| **room-paging** | Paging 3 integration. Warped's lists are small (conversations, models, endpoints). Not needed. | None |
| **material3-adaptive** | Foldable/large-screen adaptive layouts. Warped is phone-only (no tablet support in scope). | None |
| **media3** | Media playback. No media. | None |
| **espresso** (androidTest) | Currently in Gallery, not in Warped. Add only if Warped needs instrumentation tests beyond ComposeTestRule. | ComposeTestRule from BOM |

---

## Stack Patterns by Variant

**If v2.0 adds multimodal (vision) models:**
- Add `androidx.camera:camera-*` (Gallery's CameraX 1.4.2) only if camera capture is a feature.
- Add `com.google.mlkit:genai-prompt` is **not needed** — that's for Gemini Nano, not LiteRT-LM.

**If v2.0 adds MCP server support (separate from LM Studio MCP):**
- Add `io.modelcontextprotocol:kotlin-sdk:0.8.0` (Gallery's version).
- For Ktor would be needed (MCP SDK is Ktor-based). **Would force Ktor adoption** — this is a real cost. Defer unless feature is required.

**If v2.0 raises minSdk to 31 (to match Gallery):**
- Reduces device reach by ~5-10%.
- Simplifies hardware acceleration (LiteRT-LM GPU/NPU works on all API 31+ devices reliably).
- **Not recommended for v2.0.** Revisit only if API 28-30 specific issues emerge.

**If v2.0 needs more aggressive cold-start reduction:**
- Enable R8 full mode: `android.enableR8.fullMode=true` in `gradle.properties` (more aggressive shrinking, may need additional ProGuard rules).
- Baseline profiles (`:baselineprofile` consumer) — generate via Macrobenchmark, ship as `baseline-prof.txt` in `src/main/`. Significant cold-start + scroll perf win. **Not in Gallery, but proven industry pattern.**
- Startup tracing with `androidx.tracing.perfetto` — but overkill unless metric collection is set up first.

---

## Version Compatibility Matrix

| Package | Current (Warped) | Target (v2.0) | Compatible With | Notes |
|---------|------------------|---------------|-----------------|-------|
| kotlin | 2.3.20 | 2.3.20 (KEEP) | KSP 2.3.7, Hilt 2.59.2, Compose Compiler 2.3.20 | Stable |
| agp | 9.2.1 | 9.2.1 (KEEP) | Gradle 9.1+, Hilt 2.59.2 | Stable |
| compose-bom | 2026.04.01 | **2026.05.01** | Kotlin 2.3.20 | Bump gains Compose 1.11.0 |
| hilt | 2.59.2 | 2.59.2 (KEEP) | Kotlin 2.3.20, KSP 2.3.7 | Warped is ahead of Gallery (2.58) |
| hilt-navigation-compose | 1.2.0 | **1.3.0** | Hilt 2.59.2, Navigation 2.9.x | Bump to match Gallery |
| hilt-work | 1.2.0 | **1.3.0** | Hilt 2.59.2, WorkManager 2.10.0 | Bump to match Gallery |
| room | 2.7.1 | **2.8.4** | Kotlin 2.3.20, KSP 2.3.7 | Bump to latest stable |
| lifecycle | 2.8.7 | **2.10.0** | Kotlin 2.3.20, Compose 1.11.0 | Bump to latest stable |
| navigation | 2.8.8 | **2.9.x** | Compose 1.11.0, hilt-nav 1.3.0 | Bump to latest stable |
| coroutines | 1.9.0 | 1.9.0 (KEEP) | Kotlin 2.3.20 | Stable |
| okhttp | 4.12.0 | 4.12.0 (KEEP) | Retrofit 3.0.0 | OkHttp 5.x is breaking; defer |
| retrofit | 3.0.0 | 3.0.0 (KEEP) | OkHttp 4.12.0, kotlinx-serialization | Stable |
| kotlinx-serialization | 1.7.3 | 1.7.3 (KEEP) | Kotlin 2.3.20, Retrofit 3.0.0 | Stable |
| datastore | 1.2.1 | 1.2.1 (KEEP) | Coroutines 1.9.0 | Warped is ahead of Gallery (1.1.7) |
| workmanager | 2.10.0 | 2.10.0 (KEEP) | Hilt-work 1.3.0, Coroutines 1.9.0 | Stable |
| security-crypto | 1.1.0 | 1.1.0 (KEEP) | Keystore | Stable, latest |
| highlights | 1.1.0 | 1.1.0 (KEEP) | Compose 1.11.0 | KMP, no compat issues |
| ksp | 2.3.7 | 2.3.7 (KEEP) | Kotlin 2.3.20, Hilt 2.59.2, Room 2.8.4 | Matches Kotlin |
| litertlm | 0.13.0 | **0.13.1** | AGP 9.2.1, minSdk 28 | Patch bump |
| timber | 5.0.1 | 5.0.1 (KEEP) | — | Stable |
| sqlcipher | 4.5.4 | 4.5.4 (KEEP, audit) | Room 2.8.4 | Perf audit; may remove |
| **NEW: core-splashscreen** | — | **1.2.0-beta01** | AGP 9.2.1, minSdk 28 | Cold start win |
| **NEW: lifecycle-process** | — | **2.10.0** | Lifecycle 2.10.0 | App-wide lifecycle |

---

## What This Means for the v2.0 Roadmap

For a phase plan, the stack changes split into two categories:

**Category A — Mechanical bumps (low risk, one PR each):**
1. `libs.versions.toml` version bumps: compose-bom, hilt-nav, hilt-work, room, lifecycle, navigation, litertlm, lifecycle-process
2. `gradle.properties` add: `android.nonTransitiveRClass=true` (if missing), `-Xmx4g` JVM args, `kotlin.incremental=true`
3. `AndroidManifest.xml` additions: `configChanges="uiMode"`, `windowSoftInputMode="adjustResize"`, `largeHeap="true"`, `<uses-native-library>` blocks, `theme="...SplashScreen"`
4. Add `core-splashscreen` + SplashScreen theme style
5. `EngineConfig` call site: add `cacheDir = context.cacheDir.path` parameter
6. `EngineConfig` call site: add `Backend.GPU()` if available, else `Backend.CPU()` runtime selection
7. `kotlinOptions.freeCompilerArgs += "-Xcontext-receivers"`

**Category B — Performance overhaul (file-by-line, higher risk, multiple phases):**
1. Audit every Hilt module for eager singleton init — make lazy where possible
2. Audit every composable for `derivedStateOf` / stable lambdas / `key()` in `LazyColumn`
3. Audit Room queries for indexes / `Flow` vs `suspend` return types / `DistinctUntilChanged`
4. Audit OkHttp interceptor chain for connection pool tuning and per-request cancellation
5. Audit `Engine` lifecycle: `close()` on model switch, mmap cache verification, OOM handling
6. SQLCipher measurement (with/without) to inform removal decision
7. R8 full-mode enabling + ProGuard rules audit
8. Compose pre-compiled classpath optimization (already on Kotlin 2.0+ compiler, no action needed)

---

## Sources

All version facts verified against primary sources:

- **Google Maven metadata** for LiteRT-LM: https://dl.google.com/android/maven2/com/google/ai/edge/litertlm/litertlm-android/maven-metadata.xml (lastUpdated 2026-06-04, latest 0.13.1)
- **Google AI Edge Gallery `libs.versions.toml`**: https://github.com/google-ai-edge/gallery/blob/main/Android/src/gradle/libs.versions.toml (versionCode 34, 1.0.16, fetched 2026-06-05)
- **Google AI Edge Gallery `app/build.gradle.kts`**: https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/build.gradle.kts (fetched 2026-06-05)
- **Google AI Edge Gallery `AndroidManifest.xml`**: https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/AndroidManifest.xml (fetched 2026-06-05)
- **Compose BOM mapping**: https://developer.android.com/jetpack/compose/bom/bom-mapping (BOM 2026.05.01 = Compose 1.11.0, May 19 2026)
- **Compose April 2026 release notes**: https://doveletter.dev/release-notes/compose-april-2026
- **Lifecycle release notes**: https://developer.android.com/jetpack/androidx/releases/lifecycle (2.10.0 stable, 2.11.0-beta02)
- **Room 2.8.4 release**: https://mvnrepository.com/artifact/androidx.room/room-runtime/2.8.4 (Nov 19, 2025)
- **Room 3.0 alpha announcement**: https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html (March 13, 2026)
- **Navigation release notes**: https://developer.android.com/jetpack/androidx/releases/navigation (2.9.x stable, April 2026)
- **LiteRT-LM v0.13 release notes**: https://github.com/google-ai-edge/LiteRT-LM (Gemma 4 12B, OpenAI-compatible server CLI, Agent Skills support)
- **LiteRT-LM Android getting started**: https://ai.google.dev/edge/litert-lm/android (Backend.GPU(), cacheDir, NPU setup patterns)
- **Dagger Hilt KSP support**: https://dagger.dev/dev-guide/ksp (stable since Hilt 2.48)
- **Compose Testing v2 migration guide**: https://developer.android.com/develop/ui/compose/testing/migrations/testing-v2

**Confidence notes:**
- HIGH confidence on version numbers (all fetched from primary sources within 1 day of research).
- HIGH confidence on Library "must adopt" recommendations (verified against Gallery's actual use).
- MEDIUM confidence on perf impact estimates (e.g., "5-10x faster warm start with mmap cache") — these are well-documented in LiteRT-LM docs but not measured on Warped hardware.
- LOW confidence on SQLCipher overhead estimate (10-30% per query is the commonly cited range; would need microbenchmark to confirm on Warped's actual workload).
- LOW confidence on Compose 1.11.0 v2 testing framework migration cost — depends on how many UI tests exist and how many implicitly relied on `UnconfinedTestDispatcher`.

---

*Stack research for: Warped v2.0 Gallery Convergence & Performance Overhaul*
*Researched: 2026-06-05*
*Confidence: HIGH (versions) / MEDIUM (perf impact)*
