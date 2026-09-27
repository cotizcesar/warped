# Phase 45: Foundation Refresh - Research

**Researched:** 2026-09-27
**Domain:** Android dependency catalog refresh + LiteRT-LM 0.13.1 → 0.17.1 engine migration
**Confidence:** HIGH (LiteRT-LM surface + major bumps) / MEDIUM (exact patch versions of minor libs)

## Summary

Phase 45 bumps the entire `gradle/libs.versions.toml` catalog to latest stable and migrates LiteRT-LM 0.13.1 → **0.17.1** (verified latest stable, released 2026-09-16 per [LiteRT-LM releases](https://github.com/google-ai-edge/LiteRT-LM/releases)). The 0.14–0.17 delta is dominated by tool-calling and thinking/reasoning work (streaming tool-call tokens, `AutoToolChat`, int-type tool-call fix in 0.17.1, `ThinkingConfig`, `response.channels["thought"]`) plus Gemma 4 MTP/extended-context support — all directly relevant to later phases (47 adopts the tool surface, 48 hardens R8).

The existing code already uses the 0.17-shaped API (`EngineConfig` with `cacheDir`, `ConversationConfig` with `tools`/`automaticToolCalling`, `ExperimentalFlags.enableSpeculativeDecoding`), so the engine migration is a **re-verification + delta-adoption** task, not a rewrite. The version-namespaced mmap cache (`cacheDir = cacheDir/"litertlm"/BuildConfig.LITERTLM_VERSION`) auto-namespaces on bump — the work is verifying no silent schema drift on upgrade-install.

**Critical finding:** `assets/model_allowlist.json` and any `ModelAllowlistRepository` **do not exist in the tree** (verified by glob + grep 2026-09-27) despite CONTEXT.md listing them as reusable assets and RUNTIME-05 requiring the file to ship. LRT-09's allowlist capability flags therefore mean **creating** the file, not updating it.

**Primary recommendation:** Bump in dependency order (Kotlin/KSP/AGP → Lifecycle/Navigation → Hilt → Room → networking/serialization → remainder → LiteRT-LM last), one library group per commit with `./gradlew assembleDebug + testDebugUnitTest` gates, then re-verify the EngineConfig/ConversationConfig surface against 0.17.x docs, extend R8 keeps for `ToolSet`/`OpenApiTool`, create `model_allowlist.json`, and smoke-test load + streaming on a real device.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Bump every catalog entry to latest stable where build stays green, incl. LiteRT-LM 0.17.1 (user accepted)
- LiteRT-LM target pinned at 0.17.1 stable (2026-09-16), verified against Maven Central
- No SNAPSHOT or -alpha artifacts in release graph; audit fails on hit (RUNTIME-12 stays green)
- Full re-verification of EngineConfig/ConversationConfig API surface against 0.17.x
- Version-namespaced mmap cache dir (BuildConfig.LITERTLM_VERSION), verify no silent schema drift
- model_allowlist.json capability flags reflect only features actually verified on 0.17.x Android
- Review release notes per bump (esp. Room migrations, Hilt/AGP, Navigation, OkHttp/Retrofit); migrate, preserve chat history/presets/endpoints
- Full unit-test suite green + dependency audit empty after refresh
- Manual smoke: load downloaded .litertlm + streaming chat, no UnsatisfiedLinkError/serializer errors

### the agent's Discretion
- Per-library bump ordering and exact stable versions at planner discretion (verify against Maven Central / release notes)
- Zero new dependencies — all work rides refreshed catalog

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DEPS-01 | Every catalog entry verified vs latest stable and bumped where safe; no SNAPSHOT/-alpha | Version table below (verified bumps + assumed-with-verify-command entries) |
| DEPS-02 | Breaking-change sweep; migrations + keep rules adjusted; build + tests green, audit green | Breaking-change risk table (Room, Hilt/AGP, Navigation, OkHttp/Retrofit, serialization) |
| LRT-07 | litertlm-android 0.13.1 → 0.17.1, Maven Central verified, compile clean, audit green | 0.14–0.17 changelog table; AAR-only integration unchanged |
| LRT-09 | EngineConfig/ConversationConfig + R8 keeps + version-namespaced cache re-verified; allowlist capability flags | 0.17.x API surface section; R8 section; cache verification plan; allowlist creation (file missing) |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Dependency catalog versions | Build (Gradle) | — | `gradle/libs.versions.toml` is the single source of truth |
| LiteRT-LM engine init / cache | Data layer (`data/local/inference/`) | — | `LiteRTLmEngine`, `EngineManager`, `LiteRtLmCacheManager` own native lifecycle |
| R8 keep rules | Build (`app/proguard-rules.pro`) | — | Release-only concern, verified via `assembleRelease` |
| Room schema preservation | Data layer (`data/local/db/`) | — | Manual `Migration` chain owns chat history/presets/endpoints |
| Allowlist capabilities | Data layer (new asset + repository) | — | Static model metadata, read-only at runtime |

## Standard Stack

### Core — bump targets (verified [CITED], verify exact at build time via `./gradlew dependencyUpdates` or Maven Central)

| Library | Current | Target | Purpose | Why / Source |
|---------|---------|--------|---------|--------------|
| litertlm-android | 0.13.1 | **0.17.1** | On-device LLM inference AAR | Pinned by user; latest stable 2026-09-16 [CITED: github.com/google-ai-edge/LiteRT-LM/releases] |
| kotlin | 2.3.20 | **2.3.20 (verify newer)** | Language + compiler plugins | 2.3.20 is Mar-2026 stable [CITED: blog.jetbrains.com/kotlin/2026/03]; KSP release 2 weeks ago targets Kotlin 2.3 [CITED: github.com/google/ksp/releases] — check for 2.3.30+/2.4.x at build |
| agp | 9.2.1 | **9.3.0** | Android build | 9.3.0 stable Jul 2026, supports API 37 [CITED: developer.android.com/build/releases/agp-9-3-0-release-notes]. Lifecycle 2.11 needs AGP ≥ 9.2.0 — already satisfied |
| room | 2.8.4 | **2.8.5** | SQLite ORM | 2.8.5 stable Sep 9 2026 [CITED: developer.android.com/jetpack/androidx/releases/room]. **Do NOT jump to Room 3.x** (3.0.3 stable exists but is the KMP breaking major) [CITED: same page] |
| lifecycle | 2.10.0 | **2.11.0** | ViewModel / lifecycle-runtime | 2.11.0 stable Jun 17 2026 [CITED: developer.android.com/jetpack/androidx/releases/lifecycle]. Additive only (scoped ViewModels, `ViewModelProvider.get<VM>(key)`); Compose UI 1.7.0+ required (satisfied via BOM) |
| navigation-compose | 2.9.0 | **2.10.2** | Type-safe `@Serializable` routes | 2.10.2 stable Sep 23 2026 [CITED: developer.android.com/jetpack/androidx/releases/navigation]. Breaking: minSdk 21→24 (we are 28, no-op); `handleDeepLink` now ignores unrecognized links |
| hilt | 2.59.2 | **2.60.1** | Compile-time DI | 2.60.1 latest, Jul 7 2026 [CITED: mvnrepository.com/artifact/com.google.dagger/hilt-android]. No breaking notes found; KSP path unchanged |
| workmanager | 2.10.0 | **2.11.0** | Download workers | 2.11.0 stable Aug 12 2026 [CITED: developer.android.com/jetpack/androidx/releases/work] |
| kotlinx-serialization-json | 1.7.3 | **1.9.0** | JSON, NavType routes, R8 serializers | 1.9.0 exists, requires Kotlin 2.2+ [CITED: github.com/Kotlin/kotlinx.serialization/releases]. Navigation docs still reference 1.7.3 as baseline — 1.9.0 is forward-compatible |
| hilt-navigation-compose / hilt-work | 1.3.0 / 1.3.0 | **verify latest (1.3.x/1.4.x)** | Hilt + Nav / Hilt + Worker bridges | Must stay compatible with lifecycle 2.11 + navigation 2.10 [ASSUMED — check Google Maven at build] |

### Supporting — verify-and-bump [ASSUMED — confirm via Maven Central / Google Maven at build time]

| Library | Current | Guidance |
|---------|---------|----------|
| compose-bom | 2026.05.01 | Bump to latest 2026.0x BOM (Compose 1.11 line per Apr-2026 notes). BOM governs all Compose artifacts — single-line change |
| okhttp / logging-interceptor | 4.12.0 | **Keep 4.12.0** — still the 4.x stable line; OkHttp 5.x is API-breaking and explicitly deferred (Out of Scope) |
| retrofit / converter-kotlinx-serialization | 3.0.0 | Keep unless a 3.x patch exists; Retrofit 3 is Kotlin-first, no migration needed from current code |
| coroutines (+test) | 1.9.0 | Check for 1.10.x stable; keep `-test` artifact version in lockstep |
| datastore-preferences | 1.2.1 | **Keep** — 1.2.1 confirmed current stable Sep 2026 [CITED: developer.android.com/jetpack/androidx/releases/datastore] |
| core-splashscreen | 1.2.0-beta01 | Check for stable 1.2.0+ or newer beta; never regress to alpha |
| kotlinx-collections-immutable | 0.4.0 | Check for newer stable |
| security-crypto | 1.1.0 | Keep (latest stable) |
| sqlcipher | 4.5.4 | Check Zetetic releases; Room+SQLCipher open-helper wiring is version-sensitive — test DB open after bump |
| timber / errorprone-annotations | 5.0.1 / 2.28.0 | Check; low risk |
| highlights (dev.snipme) | 1.1.0 | Check; low risk, isolated to code highlighting |
| ksp (plugin) | 2.3.7 | Must match Kotlin version exactly (`<kotlin>-<ksp-build>`); re-resolve after Kotlin bump via [github.com/google/ksp/releases] |
| junit5 / mockk / turbine / truth | 5.11.4 / 1.13.16 / 1.1.0 / 1.4.4 | Check each; Turbine 1.2.x likely exists. MockK + new Kotlin needs compatibility check (mockk lags K2 sometimes — run full suite) |
| benchmark / uiautomator (androidTest) | 1.3.3 / 2.3.0 | Keep unless needed |
| junit-platform-launcher (hardcoded `1.11.4` in app/build.gradle.kts:181) | 1.11.4 | Must match junit5 major (5.11.x→1.11.x, 5.12.x→1.12.x, 5.13.x→1.13.x) — update in lockstep |

**Version verification (run at plan start, before editing the catalog):**
```bash
# Per-artifact ground truth — Google Maven for AndroidX, Maven Central for the rest:
# AndroidX: https://developer.android.com/jetpack/androidx/versions/stable-channel
# Hilt: https://github.com/google/dagger/releases  |  KSP: https://github.com/google/ksp/releases
# LiteRT-LM: https://github.com/google-ai-edge/LiteRT-LM/releases + maven.google.com (litertlm-android)
./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep -iE 'SNAPSHOT|alpha' && echo "AUDIT-DIRTY" || echo "AUDIT-CLEAN"
./scripts/audit-dependencies.sh
```

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Room 2.8.5 | Room 3.0.3 | 3.x is the KMP breaking major (Mar-2026 alpha line matured); migration risk to chat-history schema with zero benefit for an Android-only app — stay on 2.8.x |
| OkHttp 4.12.0 | OkHttp 5.x | API-breaking; explicitly out of scope — defer |
| AGP 9.2.1 | AGP 9.3.0 | 9.3.0 is stable and safe; only caution is Gradle wrapper compat (needs Gradle 9.x — wrapper already 9.4.1+ per codebase scan) |
| Manual Migration chain | AutoMigration | Codebase already replaced AutoMigration with manual `Migration(11,12)` because `app/schemas/` isn't versioned — keep manual pattern |

**Installation:** No new artifacts. Edits are version strings in `gradle/libs.versions.toml` (+ `ksp` plugin version lockstep + `junit-platform-launcher` lockstep in `app/build.gradle.kts`).

## Package Legitimacy Audit

Zero new dependencies per locked decision — every artifact is already in the release graph. No slopcheck run required (no package names introduced). The existing `scripts/audit-dependencies.sh` + RUNTIME-12 banned list (kapt, firebase, moshi, gson, kotlin-reflect, ktor, mcp, tflite, mlkit-genai, appauth, compose-richtext, camerax, datastore-proto) is the legitimacy gate: it must stay green, and no `-alpha`/`-SNAPSHOT` may enter the release graph.

## Architecture Patterns

### Bump ordering (one group per commit, build+test gate each)

```
1. Kotlin + KSP plugin (+ kotlin-bom platform line in app/build.gradle.kts:104)
2. AGP (+ Gradle wrapper if 9.3.0 demands it)
3. Compose BOM (absorbs Compose compiler/Kotlin compat)
4. Lifecycle 2.11.0 + Navigation 2.10.2 + hilt-navigation-compose (interlocked trio)
5. Hilt 2.60.1 (+ androidx.hilt compilers)
6. Room 2.8.5 (+ room-testing) → run MigrationTest / DB round-trip tests
7. Networking/serialization: serialization-json → retrofit → coroutines (+coroutines-test)
8. Remainder: workmanager, datastore (likely no-op), splash, immutable, sqlcipher, timber, highlights, testing libs
9. LiteRT-LM 0.13.1 → 0.17.1 LAST (after foundation is green — Phases 46-48 build on it)
```

### Recommended Project Structure
No structural changes. Files touched:
```
gradle/libs.versions.toml          # all version strings
app/build.gradle.kts               # junit-platform-launcher lockstep (line 181), kotlin-bom (line 104)
app/proguard-rules.pro             # extend keeps for ToolSet/OpenApiTool entry points
app/src/main/assets/model_allowlist.json   # CREATE (missing — see pitfalls)
app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt  # adopt NPU/ThinkingConfig deltas if safe
gradle.properties                  # only if AGP 9.3 demands flag changes
```

### Pattern: version-namespaced cache (already correct, re-verify only)
`LiteRTLmEngine.init()` builds `cacheDir = context.cacheDir/"litertlm"/BuildConfig.LITERTLM_VERSION` and `LiteRtLmCacheManager.cacheRoot` uses the same path; `BuildConfig.LITERTLM_VERSION` is generated from the catalog (app/build.gradle.kts:30). Bumping `litertlm` auto-namespaces. Verification = upgrade-install test (install 0.13.1 build, seed cache, install 0.17.1 build, confirm fresh namespace + no crash on stale dir + LRU cap still enforced).

### Anti-Patterns to Avoid
- **Big-bang catalog bump:** changing 15 versions in one commit makes bisection impossible — one group per commit.
- **Bumping past a KMP major (Room 3.x, OkHttp 5.x):** flagged out of scope; build must stay on the 2.8.x / 4.x lines.
- **Deleting the old cache dir on upgrade:** stale `litertlm/0.13.1` dirs must be lazily evicted by the existing LRU cap, never force-deleted (a second profile could still reference them; also destroys offline models' compiled cache silently).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Latest-version discovery | Ad-hoc guessing | Google Maven stable-channel + androidx release notes + GitHub releases pages | Training data is stale; only registry/release notes are ground truth |
| LiteRT-LM Kotlin API | Guessing from 0.13.1 memory | [developers.google.com/edge/litert-lm/android](https://developers.google.com/edge/litert-lm/android) (updated 2026-09-04) | Surface grew (ThinkingConfig, ToolSet, MTP flags); old knowledge misses new required params |
| R8 rules for engine | Custom `-keep` invention | Existing broad keeps + upstream Gallery app rules; verify with `assembleRelease` on device | Over-narrow keeps cause release-only `UnsatisfiedLinkError`/serializer crashes |
| Cache schema migration | Custom migration code | Version-namespaced dir (already implemented) + LRU eviction | mmap cache is opaque native state; namespacing sidesteps schema drift entirely |

## LiteRT-LM 0.13.1 → 0.17.1 Delta (verified [CITED: github.com/google-ai-edge/LiteRT-LM/releases + developers.google.com/edge/litert-lm/android])

| Version | Date | Android-relevant change | Phase-45 action |
|---------|------|------------------------|-----------------|
| 0.14.0 | 2026-07-08 | Streaming tool-call tokens (C/Swift), reasoning/thinking channels, auto backend selection for audio/vision | Re-verify `ConversationConfig` + thinking accessor mapping (THINK-02 groundwork) |
| 0.15.0 | 2026-08-04 | **Concurrency fix: re-entrancy on concurrent engine init**; `AutoToolChat` (JS only) | Our `@Synchronized init()` already serializes — keep; no action beyond regression test |
| 0.16.0/0.16.1 | 2026-08-11/18 | C API prebuilts, YNNPACK Linux-only (both N/A — we ride Maven AAR only); Windows JVM crash fix (N/A) | None; do NOT add C prebuilts/YNNPACK (out of scope) |
| 0.17.0 | 2026-09-09 | Optimized local attention (longer contexts), **Gemma 4 MTP + extended context**, Apple Metal (N/A) | Allowlist capability flags for MTP/extended-context where verified on Android |
| 0.17.1 | 2026-09-16 | **Int-type tool-call bug fix** | Adopt; re-test tool path types in Phase 47 |

### 0.17.x Kotlin API surface (re-verification checklist for `LiteRTLmEngine.kt` / `LiteRTLmProvider.kt`)
Source: official Android guide, last updated 2026-09-04. Current code already matches the stable core:
- `EngineConfig(modelPath, backend, visionBackend?, audioBackend?, cacheDir?)` — shape unchanged. **Delta:** `Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)` is now a real option — current code falls back NPU→GPU (line 68); adopt NPU properly or keep fallback with comment.
- `ConversationConfig(systemInstruction?, initialMessages?, samplerConfig?, extraContext?, tools?, automaticToolCalling?, thinkingConfig?, maxOutputToken?)` — current code uses all but `systemInstruction`, `thinkingConfig`, `maxOutputToken`. **Delta:** adopt `ThinkingConfig(enableThinking, thinkingTokenBudget)`; `response.channels["thought"]` delivers reasoning tokens; per-message `thinkingConfig` override exists in `sendMessage`.
- Tools: `ToolSet` interface + `@Tool`/`@ToolParam` (params: String/Int/Boolean/Float/Double or List thereof; nullable = optional via default value), `tool(...)` wrapper in `ConversationConfig(tools = listOf(tool(SampleToolSet())))`, `OpenApiTool` alternative, manual path via `automaticToolCalling = false` + `response.toolCalls` + `Message.tool(Contents.of(Content.ToolResponse(...)))`. Surface re-verified in 45, **wired in 47** — do not wire tools here.
- MTP: `@OptIn(ExperimentalApi::class) ExperimentalFlags.enableSpeculativeDecoding = true` before init (already in code, GPU path) — now "universally recommended for all GPU tasks."
- JNI lib name: code loads `System.loadLibrary("litertlm_jni")` — **verify still correct in the 0.17.1 AAR** (`unzip -l ~/.gradle/.../litertlm-android-0.17.1.aar | grep '\.so'` + confirm `liblitertlm_jni.so` per ABI).
- Manifest: docs require `libvndksupport.so` + `libOpenCL.so` (`required=false`); tree has a third entry `libcdsprpc.so` — verify all three still needed, keep-or-trim with reason.

### R8 re-verification (LRT-09)
Existing `app/proguard-rules.pro` keeps all of `com.google.ai.edge.litertlm.**` plus `MessageCallback`/`ToolProvider` interfaces — broad enough to cover 0.17.x, but explicitly add keeps for the new tool entry points (`ToolSet`, `OpenApiTool`, `@Tool`/`@ToolParam` annotations — annotations need `-keepattributes *Annotation*`, already present line 2) and any new skill/mapper classes. Verify with `assembleRelease` + install on a real device (Phase 48 does the full hardening sweep; 45 does the smoke).

## Common Pitfalls

### Pitfall 1: model_allowlist.json doesn't exist (blocks LRT-09 as scoped)
**What goes wrong:** Planner writes "update allowlist flags" tasks that fail — there is no `assets/model_allowlist.json`, no `ModelAllowlistRepository`, zero `Allowlist` references in `app/src` (verified 2026-09-27).
**How to avoid:** Phase 45 must **create** `app/src/main/assets/model_allowlist.json` (Gallery schema subset per RUNTIME-05: `name`, `displayName`, `modelFile`, `sizeInBytes`, `capabilities`, `llmPromptTemplates`, `taskTypes`) plus the repository + capability queries (`supportsThinking`, speculative-decoding/MTP, extended context). Capability flags must reflect only 0.17.x-on-Android-verified features.

### Pitfall 2: Navigation 2.10 `handleDeepLink` behavior change
**What goes wrong:** Unrecognized deep links are now silently ignored. If Warped handles any implicit/custom-scheme intents, they break without error.
**How to avoid:** Warped uses `@Serializable` type-safe routes (RUNTIME-07) — audit `NavHost`/deep-link registrations after the bump; add a test that each declared deep link resolves.

### Pitfall 3: Lifecycle 2.11 minSdk/AGP floor moves
**What goes wrong:** Lifecycle 2.10 moved minSdk 21→23; Navigation 2.10 moved to 24; Compose compileSdk moved to API 37 requiring AGP ≥ 9.2.0.
**How to avoid:** Non-issues here (minSdk 28, AGP 9.2.1→9.3.0), but do NOT lower minSdk in this phase and confirm `compileSdk = 35` still satisfies the BOM (API 37 compileSdk is required only by alpha 2.12 line, not 2.11 stable).

### Pitfall 4: Serialization 1.7.3 → 1.9.x + R8 serializer companions
**What goes wrong:** Serializer lookup changes across 1.8/1.9 can break release-only (R8) deserialization of `@Serializable` routes/entities — debug-green, release-red.
**How to avoid:** After bumping, run `assembleRelease` + release-smoke (chat history load, endpoint list, navigation to each route). Proguard `$$serializer` keeps (lines 6-10) stay as-is.

### Pitfall 5: MockK vs new Kotlin
**What goes wrong:** MockK frequently lags new Kotlin/K2 releases — full unit suite can fail on mock creation, not on product code.
**How to avoid:** If `testDebugUnitTest` fails inside mockk internals after the Kotlin bump, prefer a MockK patch bump first; do not "fix" production code to satisfy a stale mocking lib.

### Pitfall 6: Stale Room schema dir + manual migrations
**What goes wrong:** `app/schemas/` isn't versioned and AutoMigration was replaced by manual `Migration(11,12)`; Room 2.8.5's schema validator can reject the manual chain if entity hashes drift.
**How to avoid:** After Room bump, run existing DAO/Migration tests; never add `fallbackToDestructiveMigration` (explicitly forbidden — data-loss risk).

## Code Examples

### Target catalog edit shape (Gradle version catalog)
```toml
# Source: gradle/libs.versions.toml (existing pattern — version.ref + KSP)
litertlm = "0.17.1"
room = "2.8.5"
lifecycle = "2.11.0"
navigation = "2.10.2"
hilt = "2.60.1"
workmanager = "2.11.0"
agp = "9.3.0"
kotlinx-serialization = "1.9.0"
```
```kotlin
// app/build.gradle.kts — keep in lockstep (existing lines 104, 181):
implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.3.20")) // match kotlin version
testRuntimeOnly("org.junit.platform:junit-platform-launcher:<matching 1.x for junit 5.x>")
```

### 0.17.x ThinkingConfig adoption (candidate delta for LiteRTLmEngine/Provider)
```kotlin
// Source: https://developers.google.com/edge/litert-lm/android (2026-09-04)
val conversationConfig = ConversationConfig(
    thinkingConfig = ThinkingConfig(enableThinking = true, thinkingTokenBudget = 1024),
    maxOutputToken = 2048
)
val response = conversation.sendMessage("Solve this step by step.")
val thought = response.channels["thought"]  // reasoning tokens, separate from answer
```

### 0.17.x NPU backend (replaces current NPU→GPU fallback)
```kotlin
// Source: https://developers.google.com/edge/litert-lm/android (2026-09-04)
val engineConfig = EngineConfig(
    modelPath = modelPath,
    backend = Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
)
```

### Cache-dir verification snippet (upgrade-install test, manual or androidTest)
```kotlin
//_namespaces: litertlm/<BuildConfig.LITERTLM_VERSION> — assert both dirs coexist after upgrade
val old = File(context.cacheDir, "litertlm/0.13.1")
val new = File(context.cacheDir, "litertlm/${BuildConfig.LITERTLM_VERSION}")
check(BuildConfig.LITERTLM_VERSION == "0.17.1")
check(new.exists())
// old dir: left for LRU eviction, never force-deleted
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| LiteRT-LM 0.13.x tool calling | 0.17.x `ToolSet`/`@Tool` + streaming tool tokens + int-type fix | 0.14.0–0.17.1 (Jul–Sep 2026) | Phase 47 builds on the fixed surface; 45 only re-verifies |
| Opaque thinking accessor (0.13.1) | `ThinkingConfig` + `channels["thought"]` + per-message override | 0.14.0–0.17.x | THINK-02 mapping must be re-pointed (Phase 41 code → new API) |
| Navigation 2.9 / Lifecycle 2.10 | Navigation 2.10.2 / Lifecycle 2.11.0 | Aug–Sep 2026 | Minor breaking (minSdk floors, deep-link strictness); additive lifecycle APIs |
| Room 2.8.4 | Room 2.8.5 (stay off 3.x KMP major) | Sep 9 2026 | Patch-level, low risk |
| AGP 9.2.x | AGP 9.3.0 (API 37 support) | Jul 2026 | Safe minor; check wrapper compat |

**Deprecated/outdated:**
- Room 3.x for this app: KMP breaking major, no Android-only benefit — deferred indefinitely.
- OkHttp 5.x: API-breaking — deferred per Out of Scope.
- LiteRT-LM C API prebuilts / YNNPACK delegate / Apple Metal / Apple FM adapter: non-Android or non-AAR surfaces — out of scope (REQUIREMENTS.md).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Exact latest patch versions for compose-bom, coroutines, hilt-androidx compilers, splash, immutable, sqlcipher, timber, highlights, ksp-build, junit5, mockk, turbine, truth at build time | Standard Stack (Supporting) | Low — planner verifies each against Maven Central before editing; build gate catches drift |
| A2 | Hilt 2.60.1 has no AGP-9.3/Kotlin-2.3 incompatibility | Standard Stack | Medium — gate: bump Hilt right after AGP+Kotlin, run kspDebugKotlin; roll back to 2.59.2 if processor fails |
| A3 | kotlinx-serialization 1.9.0 is the right target (not a newer 1.10/1.11) | Standard Stack | Low — verify release page at build; requirement is Kotlin-2.2+ compat, any 1.9.x+ works |
| A4 | `liblitertlm_jni.so` keeps its JNI lib name in 0.17.1 AAR | LiteRT-LM delta | Medium — verify via `unzip -l` on the resolved AAR before smoke; rename loadLibrary call if changed |
| A5 | Full unit suite exists and runs via `testDebugUnitTest` (JUnit Platform) | Validation | Low — build.gradle.kts wires `useJUnitPlatform()`; Phase 45 confirms green baseline BEFORE first bump |

## Open Questions

1. **Compose BOM exact target**
   - What we know: catalog 2026.05.01; Compose 1.11 line current per Apr-2026 notes; Lifecycle 2.11 needs Compose UI 1.7.0+ (trivially satisfied).
   - What's unclear: latest BOM date-code as of build day.
   - Recommendation: resolve at build via Google Maven stable channel; BOM is a single-line change with the whole Compose tree moving atomically.
2. **core-splashscreen stable availability**
   - What's unclear: whether 1.2.0 stable (or newer) shipped; catalog pins a beta.
   - Recommendation: prefer stable 1.2.x if present, else newest beta; never alpha (locked decision).
3. **SQLCipher + Room 2.8.5 open-helper compat**
   - Recommendation: after bump, run an encrypted-DB open + 50-message round-trip test (mirrors BENCH-04's MigrationTest pattern).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Android SDK + Gradle wrapper | All build/test tasks | Verify at plan start (`./gradlew --version`, `sdkmanager --list`) | Wrapper 9.4.1+ expected | None — blocking |
| Maven Central / Google Maven network | Version verification + artifact download | Assumed ✓ | — | None — blocking for bump tasks |
| Physical device (arm64, GPU) | Smoke: load .litertlm + streaming, no UnsatisfiedLinkError | Unknown — confirm at execution | — | Emulator (x86_64) for compile-level check only; note GPU/NPU paths unverifiable |
| `.litertlm` model file | Smoke test | Unknown — needs a downloaded model on the device | — | None — smoke is a manual gate per CONTEXT |

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (jupiter) + MockK + Turbine + Truth + coroutines-test |
| Config file | `app/build.gradle.kts` (`tasks.withType<Test> { useJUnitPlatform() }`) |
| Quick run command | `./gradlew :app:testDebugUnitTest --tests "<Class>"` |
| Full suite command | `./gradlew :app:testDebugUnitTest` + `./scripts/audit-dependencies.sh` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DEPS-01 | No SNAPSHOT/-alpha in release graph | script | `./scripts/audit-dependencies.sh` + `:app:dependencies` grep | ✅ script exists |
| DEPS-02 | Room migrations preserve history | unit/androidTest | existing DAO + Migration tests | ✅ pattern exists (verify names at plan) |
| LRT-07 | 0.17.1 compiles, audit green | build+script | `./gradlew :app:assembleDebug` + audit script | ✅ |
| LRT-09 | Cache namespaced, no drift; R8 keeps hold | manual + release build | `assembleRelease` + device smoke (load .litertlm + streaming) | ❌ Wave 0 — smoke is manual per CONTEXT |

### Sampling Rate
- **Per task commit:** module compile + targeted unit test class
- **Per wave merge:** full `:app:testDebugUnitTest` + audit script
- **Phase gate:** Full suite green + audit empty + manual device smoke recorded

### Wave 0 Gaps
- Baseline: run full suite + audit BEFORE first version edit and record green (rollback reference)
- Manual smoke checklist (load .litertlm, streaming chat, thinking toggle if adopted, release-APK install) — owner + device recorded in PLAN

## Security Domain

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | — (no auth changes in this phase) |
| V5 Input Validation | Partial | No parsing changes; serialization bump covered by release-smoke |
| V6 Cryptography | Yes | `security-crypto` stays 1.1.0; SQLCipher bump needs encrypted-DB open test; Keystore handling re-audited in Phase 48 (HARD-01) |
| V9 Communications | No | OkHttp stays 4.12.0; `network_security_config` untouched (re-audit in Phase 48) |
| V14 Config/Build | Yes | R8 full-mode + keeps re-verified via `assembleRelease`; no secrets in logs (RedactingTree untouched) |

Threat note: dependency bumps are a supply-chain surface — all artifacts come from Google Maven / Maven Central (no new repos, no SNAPSHOT). The audit script is the gate.

## Sources

### Primary (HIGH confidence)
- [LiteRT-LM releases v0.13.1–v0.17.1](https://github.com/google-ai-edge/LiteRT-LM/releases) — per-version changelog, dates, 0.17.1 int-type tool-call fix
- [LiteRT-LM Android/Kotlin guide](https://developers.google.com/edge/litert-lm/android) (updated 2026-09-04) — EngineConfig/ConversationConfig/ToolSet/ThinkingConfig/MTP/NPU/R8-relevant surface
- [Lifecycle release notes](https://developer.android.com/jetpack/androidx/releases/lifecycle) — 2.11.0 stable Jun 2026, AGP ≥ 9.2.0 requirement
- [Navigation release notes](https://developer.android.com/jetpack/androidx/releases/navigation) — 2.10.2 stable Sep 2026, minSdk 24, handleDeepLink change
- [Room release notes](https://developer.android.com/jetpack/androidx/releases/room) — 2.8.5 stable Sep 2026; 3.0.3 KMP major exists
- [DataStore release notes](https://developer.android.com/jetpack/androidx/releases/datastore) — 1.2.1 current stable
- [WorkManager release notes](https://developer.android.com/jetpack/androidx/releases/work) — 2.11.0 stable Aug 2026
- [AGP 9.3.0 release notes](https://developer.android.com/build/releases/agp-9-3-0-release-notes) — stable Jul 2026
- [Hilt on Maven Central](https://mvnrepository.com/artifact/com.google.dagger/hilt-android) — 2.60.1 latest Jul 2026
- In-tree: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `LiteRTLmEngine.kt`, `LiteRTLmProvider.kt`, `EngineManager.kt`, `LiteRtLmCacheManager.kt`, `scripts/audit-dependencies.sh`

### Secondary (MEDIUM confidence)
- [Kotlin 2.3.20 release blog](https://blog.jetbrains.com/kotlin/2026/03/kotlin-2-3-20-released/) + [KSP releases](https://github.com/google/ksp/releases) — Kotlin/KSP currency
- [kotlinx.serialization releases](https://github.com/Kotlin/kotlinx.serialization/releases) — 1.9.0 exists, Kotlin 2.2+ required
- [Retrofit 3.0 migration guide (proandroiddev)](https://proandroiddev.com/retrofit-3-0-0-detailed-migration-guide-0d2c043d43e3) — Retrofit 3 is Kotlin-first, no migration expected

### Tertiary (LOW confidence)
- Minor-lib latest patches (splash, immutable, sqlcipher, timber, highlights, mockk, turbine, truth, coroutines 1.10.x) — to be resolved against registries at build; see A1

## Metadata

**Confidence breakdown:**
- Standard Stack: HIGH for engine + major AndroidX/Hilt/AGP bumps (registry + official notes cited); MEDIUM for minor-lib exact patches (A1)
- Architecture: HIGH — in-tree code read; bump order + cache pattern verified against existing files
- Pitfalls: HIGH — allowlist absence and nav/lifecycle breaking items verified against tree + official notes

**Research date:** 2026-09-27
**Valid until:** ~2026-10-27 (patch versions drift; re-check registries if planning slips)
