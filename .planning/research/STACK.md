# Stack Research: v2.5 Play Compliance + Leaks

**Domain:** Android Play-compliance delta (16 KB pages, API 36 audit) + memory-leak detection
**Researched:** 2026-09-30
**Confidence:** HIGH (official Android docs + project build files verified; exact patch versions MEDIUM where noted)

## Starting Point (already in the repo — do NOT re-add)

| What | Current state | Implication |
|------|---------------|-------------|
| `compileSdk = 36`, `targetSdk = 36` | Already set in `app/build.gradle.kts` | API 36 targeting is DONE at build level; v2.5 work is the **behavior-change audit + verification**, not the bump |
| AGP `9.3.0` | Far above the 8.5.1 floor for 16 KB support | 16 KB zip-alignment packaging is automatic; `packaging.jniLibs.useLegacyPackaging = false` already set (correct) |
| No NDK/CMake block, no `cpp/` tree | Removed in v1.5 (GGUF deletion) | App compiles **zero** native code — 16 KB risk is confined to **transitive `.so` files** (LiteRT-LM, SQLCipher) |
| StrictMode in debug `Application.onCreate` | Already present | Complements LeakCanary; keep both |
| `auditDependencies` gate (bans kapt, firebase, moshi, gson, kotlin-reflect, ktor, mcp, tflite, mlkit-genai, appauth, compose-richtext, cameraX, datastore-proto) | Enforced on `check` | Any NEW dep must pass this gate — verified below per addition |
| Coil `3.4.0` ceiling comment | Pinned because repo was on compileSdk 35 | **Ceiling is now liftable**: repo is on compileSdk 36 (see below) |

## Recommended Stack

### Core Technologies (no changes — confirm, don't churn)

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| AGP | 9.3.0 (keep) | Build + 16 KB packaging | ≥ 8.5.1 means uncompressed `.so` files get 16 KB zip-alignment automatically. No packaging change needed. Verified on official 16 KB guide (developer.android.com/guide/practices/page-sizes, updated 2026-09-16). |
| Kotlin | 2.3.20 (keep) | Language | LeakCanary 2.14 and Coil 3.5.x/3.6.x both accept Kotlin 2.2/2.3 metadata. No bump required for this milestone. |
| compileSdk / targetSdk | 36 / 36 (keep) | API 36 compliance | Already compliant. Work is runtime-behavior audit (edge-to-edge, predictive back, resizability, JobScheduler quotas). |

### Supporting Libraries (NEW — the actual v2.5 delta)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `com.squareup.leakcanary:leakcanary-android` | **2.14** (latest stable, 2024-04-17; changelog-verified) | Automatic retained-object / Activity / ViewModel / Fragment leak detection with heap-dump analysis + in-app display activity | `debugImplementation` ONLY. Zero setup (auto-installs via App Startup provider). Covers the audit targets: EngineManager singletons, chat `StateFlow` collectors, Coil image requests, OkHttp SSE `Call`s, grounding pipeline scopes. |
| `com.squareup.leakcanary:plumber-android` | 2.14 (same version, same scope) | Auto-fixes known Android-framework leaks at runtime | `debugImplementation` alongside leakcanary-android. Removes framework-noise leaks so the audit sees only Warped's own leaks. Part of LeakCanary since 2.4, no extra config. |
| Coil 3 bump: `io.coil-kt.coil3:coil-compose`, `coil-network-okhttp`, `coil-test` | **3.5.x minimum, validate 3.6.3 latest** (README quickstart shows 3.6.3; changelog shows 3.5.0 raised compile SDK to 36) | Unblock the stale 3.4.0 ceiling; stay on a 16 KB-era, compileSdk-36-built artifact | Replace the `coil3 = "3.4.0"` pin + delete the ceiling comment. 3.5.0 uses Kotlin 2.2 / compile SDK 36 — compatible with this repo. 3.6.3 is latest; accept if `./gradlew :app:assembleDebug` + OG-thumbnail screens pass, else hold at newest 3.5.x. |

### Development Tools (no new artifacts — platform tooling)

| Tool | Purpose | Notes |
|------|---------|-------|
| `check_elf_alignment.sh` (AOSP system/extras) | Verify every `arm64-v8a` / `x86_64` `.so` in the APK has `LOAD align 2**14` | Run against release APK/AAB. Critical for the two transitive native carriers: **LiteRT-LM 0.17.1** and **SQLCipher 4.5.4**. Script source: android.googlesource.com, linked from official 16 KB guide. |
| `zipalign -c -P 16 -v 4 app.apk` (build-tools 35.0.0+) | Verify 16 KB zip-alignment of uncompressed `.so` entries | Final gate before Play upload. Must print "Verification successful". |
| APK Analyzer → `lib/` folder + lint alignment warnings | In-Studio detection of misaligned prebuilts | Zero-config; Android Studio flags non-compliant `.so` files automatically. |
| 16 KB emulator system image ("Google APIs Experimental 16KB Page Size", ARM64 v8a) | Runtime regression test on a real 16 KB kernel | `adb shell getconf PAGE_SIZE` must return `16384`. Run: model load + chat stream + download + grounding E2E. Alternative: Pixel 8/9 "Boot with 16KB page size" developer option. |
| LeakCanary in-app activity + `shark-cli` (GitHub release zip) | Triage leaks on-device; analyze large heap dumps off-device | Shark CLI handles heap dumps too large for on-device analysis (LLM chat sessions can produce multi-GB-reachable heaps). |
| App-compat flags `UNIVERSAL_RESIZABLE_BY_DEFAULT`, `STPE_SKIP_MULTIPLE_MISSED_PERIODIC_TASKS` | Pre-verify API 36 behavior changes without code edits | Toggle via `adb shell am compat enable` per package; see Behavior audit below. |

## Installation

```kotlin
// gradle/libs.versions.toml
[versions]
leakcanary = "2.14"
coil3 = "3.6.3"  # validate; fall back to newest 3.5.x if build/screens fail

[libraries]
leakcanary-android = { group = "com.squareup.leakcanary", name = "leakcanary-android", version.ref = "leakcanary" }
plumber-android = { group = "com.squareup.leakcanary", name = "plumber-android", version.ref = "leakcanary" }
```

```kotlin
// app/build.gradle.kts — debug ONLY, never ships to Play
debugImplementation(libs.leakcanary.android)
debugImplementation(libs.plumber.android)
```

```bash
# 16 KB verification (CI or pre-release lane)
./scripts/check_elf_alignment.sh app-release.apk
zipalign -c -P 16 -v 4 app-release.apk
adb shell getconf PAGE_SIZE   # on 16 KB image → 16384
```

## Behavior-Change Audit Scope (API 36 — what the targetSdk bump actually activates)

No new libraries needed; this is code-audit work. Source: developer.android.com/about/versions/16/behavior-changes-16 (verified 2026-09-30).

| Change | Warped impact | Action |
|--------|---------------|--------|
| **Edge-to-edge opt-out removed** (`windowOptOutEdgeToEdgeEnforcement` deprecated/disabled on API 36 devices) | Chat screen, bottom sheets, OG cards must handle insets via Compose (`WindowInsets`, `enableEdgeToEdge`) | Grep for the opt-out attr; remove it; screenshot-verify chat + sheets with gesture nav |
| **Predictive back on by default** (`onBackPressed` / `KEYCODE_BACK` no longer dispatched) | Any custom back handling (navigation-compose back stack, bottom-sheet dismiss, download-cancel-on-back) | Migrate to `OnBackInvokedDispatcher` / predictive-back APIs, or temporarily set `android:enableOnBackInvokedCallback="false"`; test back-to-home + cross-activity transitions |
| **Orientation/resizability/aspect-ratio ignored on sw≥600dp** (opt-out via `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`, temporary — gone at API 37) | Tablets/foldables/desktop windowing: chat layout stretching, activity re-creation losing draft state | Verify adaptive layout; persist chat input across re-creation; use opt-out property only as stopgap |
| **JobScheduler quota adjustments** (standby bucket / top-state / FGS aware) | WorkManager model downloads (regular + expedited jobs) may get shorter/later windows | Re-test background download + retry on Android 16; prefer user-initiated transfer paths for large model files if quota bites |
| **`ScheduledExecutorService.scheduleAtFixedRate`**: at most ONE missed execution fires on return to valid lifecycle | Any polling loops (download progress, retry timers) that assumed catch-up-everything | Audit call sites; behavior usually improves, but verify no logic depended on burst catch-up |
| **Local Network Permission (opt-in phase, enforced later)** — LAN access will require a Nearby-devices-group runtime permission; compat flag `RESTRICT_LOCAL_NETWORK` | **Direct hit**: Ollama / LM Studio / custom endpoints on LAN go through OkHttp | Test with the compat flag enabled NOW; permission-guard LAN connection-test + chat paths behind the new permission; declaring `NEARBY_WIFI_DEVICES` restores access during opt-in |
| Elegant-font `elegantTextHeight` ignored; MediaStore version per-app; health/BT changes | None — no health, BT, or MediaStore-version-dependent code | No action |

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| LeakCanary 2.14 stable | LeakCanary 3.0 Alpha 9 (2026-06-25) | Only if the audit needs heap-growth detection (`HeapDiff`) in UI tests. NOT recommended: alpha channel, minSdk 26 OK but deobfuscation plugin now requires AGP 8+ API changes and Kotlin 2.4 metadata floor is approaching — unnecessary churn for a compliance milestone. Revisit post-v2.5. |
| LeakCanary (debug) | Manual `Android Studio Profiler` heap dumps only | Profiler stays useful for native/LLM memory (Java heap + native), but it has no automatic retained-object detection or CI-friendly leak assertions. Use Profiler as complement (LiteRT inference RSS), not substitute. |
| LeakCanary + Shark CLI | `leakcanary-android-test` heap-growth in `androidTest` | Consider only if v2.5 wants automated leak gates in CI (`HeapDiff.repeatingAndroidInProcessScenario`). Requires `android:largeHeap="true"` in the **app-under-test** manifest (not the test manifest — documented gotcha). Defer unless roadmap demands CI gates. |
| Verify transitive `.so` alignment | Pin `ndkVersion` / add linker flags | Unnecessary: app compiles no native code (no CMake/ndk-build block since v1.5). Linker flags only apply to code you compile. If a transitive `.so` is misaligned, the fix is upgrading that dependency, not NDK config. |
| Coil 3.6.3 (validate) / 3.5.x (hold) | Stay on Coil 3.4.0 | Staying avoids all risk, but 3.4.0 predates the compileSdk-36 line and the v2.4 tech-debt note already flagged the ceiling. The bump is low-risk (same `io.coil-kt.coil3` coordinates, OkHttp networking unchanged) — validate and move. |
| Keep SQLCipher 4.5.4 pending alignment check | Upgrade SQLCipher preemptively | Only if `check_elf_alignment.sh` flags its `.so`. Version 4.5.4 is recent; expect ALIGNED. Do not churn encrypted-DB deps without evidence. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `leakcanary-android` in `implementation` / `releaseImplementation` | Ships heap-dump machinery + display activity to Play users; also risks tripping the `auditDependencies` gate on release classpath | `debugImplementation` only; release APK must contain zero LeakCanary classes (verify via APK Analyzer) |
| LeakCanary 1.x artifacts / manual `LeakCanary.install(this)` / `RefWatcher` | 1.x API removed years ago; 2.x auto-installs, no Application code needed | 2.14 with zero app-code changes |
| `useLegacyPackaging = true` (compressed `.so` fallback) | Documented last-resort for AGP ≤ 8.5 that can't upgrade; increases install size and install-failure rate. AGP 9.3.0 already does 16 KB alignment correctly | Keep `useLegacyPackaging = false`; fix misaligned deps by upgrading them |
| Prebuilt/patched LiteRT-LM or SQLCipher `.so` files hand-dropped into `jniLibs/` | Breaks checksum/provenance, silently reverted by dependency updates, hides the real version requirement | Upgrade the Maven artifact; if no aligned release exists, file upstream + gate Play upload |
| `android:extractNativeLibs="false"` changes for 16 KB | 16 KB guide notes a slight binary-size increase is expected and absorbed by Android 15+ package-manager optimizations — not a problem to solve | Leave packaging defaults alone |
| New runtime deps for "16 KB support" (there are none) | 16 KB is a build/verification property, not a library feature. Any dep marketed for it is snake oil | Tooling above (script + zipalign + emulator image) |
| Firebase Performance / Crashlytics for the leak audit | Banned by `auditDependencies` (firebase); vendor lock-in; no retained-path analysis | LeakCanary (debug) + existing Timber/crash handler |

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| LeakCanary 2.14 | minSdk 28 ✓ (LC 2.x floor is API 21; 3.0-alpha raised to 26 — still below 28), AGP 9.3.0 ✓, Kotlin 2.3.20 ✓ | `debugImplementation` keeps it off the release classpath; confirm `auditDependencies` passes (it checks banned modules — LeakCanary pulls none: no firebase/moshi/gson/ktor) — MEDIUM confidence, verify by running `./gradlew check` after adding |
| Coil 3.5.0+ / 3.6.3 | compileSdk 36 ✓ (3.5.0 raised compile SDK to 36 per changelog), Kotlin 2.2+ metadata readable by 2.3.20 ✓, OkHttp 4.12.0 ✓ (coil-network-okhttp supports OkHttp 4.x) | `coil-test` stays in `androidTestImplementation`. MEDIUM confidence on 3.6.3 specifically (release list confirms it exists; API deltas vs 3.4.0 need a build + OG-thumbnail screen pass) |
| LiteRT-LM 0.17.1 (keep) | 16 KB alignment expected (Google-built, Sept 2026) but UNVERIFIED | Must-run: `check_elf_alignment.sh` on its `.so`. If UNALIGNED, upgrade LiteRT-LM before Play upload |
| SQLCipher 4.5.4 (keep) | 16 KB alignment UNVERIFIED | Same check. Upgrade only on evidence |
| NDK (no pin; README cites 27.x) | No action — nothing compiles native code | Do NOT add `ndkVersion` or linker flags; they would be dead config. Only revisit if native code ever returns |

## Stack Patterns by Variant

**If `check_elf_alignment.sh` reports ALIGNED for all `arm64-v8a`/`x86_64` `.so` files:**
- No dependency changes for 16 KB. Record the script output as the compliance evidence and move to emulator runtime testing.

**If a transitive `.so` (SQLCipher or LiteRT-LM) reports UNALIGNED:**
- Upgrade that single artifact to the newest aligned release and re-verify. Never hand-patch `.so` files or flip `useLegacyPackaging`.

**If the roadmap wants CI leak gates later:**
- Add `leakcanary-android-test` to `androidTestImplementation` + `android:largeHeap="true"` in `app/src/main/AndroidManifest.xml` (NOT the androidTest manifest — documented no-effect gotcha), with `HeapDiff` scenarios around chat-stream and model-switch flows.

## Sources

- developer.android.com/guide/practices/page-sizes — 16 KB requirements, AGP ≥ 8.5.1, NDK r28 default-align, verification commands, Play deadline (page now states Feb 1 2027) — HIGH — official docs, updated 2026-09-16
- developer.android.com/about/versions/16/behavior-changes-16 — edge-to-edge, predictive back, resizability, scheduleAtFixedRate, local-network permission opt-in — HIGH — official docs, updated 2026-09-16
- square.github.io/leakcanary/changelog — 2.14 latest stable (2024-04-17), 3.0-alpha line status, minSdk-26/Kotlin notes — HIGH — official changelog
- coil-kt.github.io/coil/changelog + coil-kt/coil GitHub README (3.6.3 quickstart, 3.5.0 compile-SDK-36 entry) — MEDIUM — official sources, exact 3.6.x/Compose-BOM interplay unverified in this repo
- `app/build.gradle.kts` + `gradle/libs.versions.toml` (repo HEAD) — compileSdk/targetSdk 36, AGP 9.3.0, Coil 3.4.0 ceiling, no NDK block — HIGH — first-party evidence
- WebSearch cross-checks (ProAndroidDev 16 KB guide, r/androiddev deadline thread, Medium Android-16 impact summary) — LOW — directionally consistent with official docs, deadline dates vary by source age (use the official page's Feb 1 2027)

---
*Stack research for: v2.5 Play Compliance + Leaks (16 KB pages, API 36 audit, leak detection)*
*Researched: 2026-09-30*
