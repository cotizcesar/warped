# Architecture Research: v2.5 Play Compliance + Leaks

**Domain:** Android LLM chat app (Warped) — Play-compliance + memory-leak milestone on existing codebase
**Researched:** 2026-09-30
**Confidence:** HIGH (all structural claims verified against the live codebase; Android 16/16KB claims from official developer.android.com docs fetched same day)

## Standard Architecture

### System Overview — what v2.5 touches (and what it doesn't)

```
┌──────────────────────────────────────────────────────────────────┐
│  BUILD LAYER (16KB work lives here — zero Kotlin changes)         │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐   │
│  │ AGP 9.3.0    │  │ packaging{}  │  │ deps' .so files        │   │
│  │ (≥ 8.5.1 ✓)  │  │ legacy=false │  │ LiteRT 0.17.1 / SQLCipher│
│  │ 16KB zip-    │  │ uncompressed │  │ verify alignment,      │   │
│  │ align default│  │ .so ✓        │  │ never rebuild          │   │
│  └──────────────┘  └──────────────┘  └────────────────────────┘   │
├──────────────────────────────────────────────────────────────────┤
│  MANIFEST + OS BEHAVIOR LAYER (API 36 audit lives here)           │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐   │
│  │ targetSdk 36 │  │ WorkManager  │  │ MainActivity/NavGraph  │   │
│  │ ALREADY set  │  │ download FGS │  │ edge-to-edge / back    │   │
│  │ (audit only) │  │ dataSync ✓   │  │ handling verify/fix    │   │
│  └──────────────┘  └──────────────┘  └────────────────────────┘   │
├──────────────────────────────────────────────────────────────────┤
│  APP RUNTIME (leak audit lives here — existing layers, no new)    │
│  ┌──────────────────┐  ┌──────────────┐  ┌───────────────────┐    │
│  │ Singletons       │  │ ChatViewModel│  │ Network singletons│    │
│  │ EngineManager →  │  │ generationJob│  │ base / sse /      │    │
│  │ LiteRTLmEngine → │  │ retryJob,    │  │ tavily / Coil     │    │
│  │ native Engine +  │  │ shareIn turn │  │ OkHttp clients +  │    │
│  │ openSessions set │  │ scope, flows │  │ callbackFlow SSE  │    │
│  └──────────────────┘  └──────────────┘  └───────────────────┘    │
│  ┌────────────────────────────────────────────────────────────┐   │
│  │ Compose (ChatScreen, sheets, OgSourceCard, MarkdownText)   │   │
│  │ collectAsStateWithLifecycle ✓ / DisposableEffect audit     │   │
│  └────────────────────────────────────────────────────────────┘   │
├──────────────────────────────────────────────────────────────────┤
│  OBSERVABILITY (NEW — debug builds only, zero release footprint)  │
│  ┌────────────────────────────────────────────────────────────┐   │
│  │ LeakCanary 2.14 + plumber-android (debugImplementation)    │   │
│  │ auto-installs via App Startup; no code, no Hilt binding    │   │
│  └────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

The single most important architectural fact for this milestone: **there is no new feature subsystem.** All three workstreams integrate into seams that already exist:

1. **16KB** integrates at the *build/packaging* seam only. The app ships **zero first-party native code** (no `src/main/cpp/`, no CMake, no `ndkVersion` anywhere in Gradle files — verified by grep). Every `.so` in the APK arrives inside a dependency AAR — primarily `litertlm-android:0.17.1` (`liblitertlm_jni.so` + GPU delegate libs + `libcdsprpc.so` for the NPU path) and SQLCipher (`libsqlcipher.so`). Therefore no `LOCAL_LDFLAGS` / `target_link_options` changes exist to make; the work is **verify → escalate-or-bump → test on 16KB image**.
2. **API 36** integrates at the *manifest + WorkManager + navigation* seams, and the codebase is already most of the way there: `compileSdk = 36`, `targetSdk = 36` are set in `app/build.gradle.kts`; `POST_NOTIFICATIONS` runtime request exists in `MainActivity`; `SCHEDULE_EXACT_ALARM` is declared; the download worker already runs as a `dataSync` foreground service (`SystemForegroundService` merge in the manifest). What remains is a **behavior-change audit**, not a migration.
3. **Leak fixes** integrate *inside* existing components (no new layers, no new repositories). The fix surface is: singleton native-handle lifecycle, ViewModel job/flow discipline, OkHttp client/stream lifecycle, Compose effect/collector discipline, grounding-pipeline coroutine scopes. LeakCanary itself is a **debug-only observer** — it adds no architecture, only visibility.

### Component Responsibilities

| Component | Responsibility | Implementation in this codebase |
|-----------|----------------|---------------------------------|
| AGP + `packaging.jniLibs` | 16KB zip-alignment of uncompressed `.so` | AGP 9.3.0 (≥ 8.5.1 requirement ✓); `useLegacyPackaging = false` already (verified `app/build.gradle.kts` lines 81–84). **No change needed.** |
| Dependency AAR `.so` files | 16KB ELF-alignment (`LOAD align 2**14`) | Owned by Google (LiteRT-LM) / Zetetic (SQLCipher). Warped's only lever is version bumps. **Verify, don't rebuild.** |
| `AndroidManifest.xml` | API 36 declarations | `targetSdk 36` via Gradle; FGS `dataSync` service merge; native-lib `required="false"` entries. May gain `enableOnBackInvokedCallback` decision (see Pattern 2). |
| `ModelDownloadWorker` / `ModelBenchmarkWorker` | Deferrable work under Android 16 JobScheduler quotas | Download worker already foreground + `dataSync` (exempt path). Benchmark worker is the audit target for quota-stop handling (`WorkInfo.getStopReason()` logging). |
| `MainActivity` + `NavGraph` | Edge-to-edge, predictive back | Audit: confirm no `windowOptOutEdgeToEdgeEnforcement` usage (attr is dead on target-36/API-36); confirm back handling uses supported APIs or opt out. |
| `EngineManager` → `LiteRTLmEngine` → native `Engine` | Native handle lifecycle (largest leak surface) | `@Singleton` chain; `openSessions` tracking + ordered close (sessions → engine) already exists; `ioScope` is never cancelled (singleton-scoped — acceptable, document). |
| `ChatViewModel` | Turn-scoped job/flow lifecycle | `generationJob` + `retryJob` with pre-cancel discipline (lines ~377–391, ~1266–1287); per-turn `shareIn` scoped to the generation job (line ~904); `onCleared()` exists (line ~1919 — audit its body). |
| `LMStudioProvider` / `LmStudioHelper` / remote providers | SSE `Call` + `callbackFlow` lifecycle | `activeCall` AtomicReference + `callHook` + `onCompletion` null-out already in `LmStudioHelper`; `callbackFlow` in `LMStudioProvider` (line ~278) is the `awaitClose` audit target. |
| OkHttp clients (`NetworkModule` + Coil) | Connection/client lifecycle | 4 clients: base, `@Named("sse")` (50 MB cache), `@Named("tavily")`, Coil bare client in `WarpedApplication.newImageLoader`. All `@Singleton`/singleton-factory — pools live for app lifetime by design. |
| `WebPageFetcher` + `MultiUrlFetcher` + search repos | Grounding fetch scope lifecycle | `activeCalls` ConcurrentHashMap registry + `cancel()` already; audit: parallel fan-out children must die with the turn scope, never `GlobalScope`. |
| Compose screens | Collector/effect discipline | `collectAsStateWithLifecycle` used throughout (ChatScreen, PromptLab, Benchmark, Wizard ✓); `DisposableEffect` observer properly removed ✓; `DisposableEffect(Unit) { unloadLocalModels() }` in ChatScreen is a *correctness* smell to review (model unload on composition leave), not a leak per se. |
| LeakCanary 2.14 + plumber (sibling STACK.md decision) | Debug-only leak detection + framework-leak plumber | `debugImplementation` only; auto-installs via App Startup provider; no Hilt module, no Application code. |

## Recommended Project Structure

No new packages. v2.5 adds at most debug scaffolding and fix-local code inside existing files:

```
app/
├── build.gradle.kts                  # MODIFY (maybe) — LeakCanary debugImplementation lines only;
│                                     #   16KB needs NO build change (AGP 9.3 + legacy=false already)
├── src/main/AndroidManifest.xml      # MODIFY (maybe) — enableOnBackInvokedCallback decision;
│                                     #   NEVER add android:pageSizeCompat (would mask misalignment)
├── src/main/java/com/warped/
│   ├── data/local/inference/
│   │   ├── EngineManager.kt          # MODIFY (fix) — unload/close discipline gaps only
│   │   └── LiteRTLmEngine.kt         # MODIFY (fix) — session/engine close ordering gaps only
│   ├── data/remote/provider/
│   │   ├── LMStudioProvider.kt       # AUDIT — callbackFlow awaitClose
│   │   └── LmStudioHelper.kt         # AUDIT — activeCall null-out (already present, verify)
│   ├── data/grounding/               # AUDIT — scope discipline (no GlobalScope, turn-bound)
│   ├── di/NetworkModule.kt           # AUDIT — client singleton-ness (already @Singleton, verify Coil)
│   ├── ui/chat/
│   │   ├── ChatViewModel.kt          # MODIFY (fix) — onCleared body, job null-outs, shareIn scope
│   │   └── ChatScreen.kt             # AUDIT — DisposableEffect review
│   └── WarpedApplication.kt          # UNCHANGED — Coil factory already singleton-scoped
└── scripts/
    └── verify-16kb.sh                # NEW (optional) — check_elf_alignment + zipalign gate for CI
```

### Structure Rationale

- **No new production packages** because none of the three workstreams introduces a capability — 16KB is a packaging property, API 36 is behavioral conformance, leaks are lifecycle corrections. A new `util/leaks/` or `di/LeakModule` would be pure ceremony; LeakCanary needs no binding.
- **Fixes land at the owner, not in a central "leak fixer."** The codebase already follows single-owner discipline (48-01 sub-states, per-component cancel methods). Each leak fix is a 5–20 line change inside the owning component (null the `Call` ref, close the session, scope the `shareIn`). Centralizing would break the ownership pattern that makes the current discipline work.
- **The one permissible NEW file is a verification script**, not app code: a `verify-16kb.sh` wrapping `check_elf_alignment.sh` + `zipalign -c -P 16` so CI gates alignment on every release build. This is build tooling, invisible to the app architecture.

## Architectural Patterns

### Pattern 1: Verify-don't-rebuild for transitive native code

**What:** For 16KB, treat every `.so` as a third-party artifact: extract the APK/AAB, run `check_elf_alignment.sh` + `zipalign -c -P 16`, and map each `UNALIGNED` library back to its Maven dependency. The only fixes available at Warped's layer are (a) bump the dependency to a 16KB-aligned release, or (b) file/escalate upstream. There is no CMake/ndk-build file in this repo to add linker flags to.
**When to use:** The entire 16KB workstream.
**Trade-offs:** + zero app-code risk; aligns with Google's own guidance ("update tools + use 16KB-compatible prebuilt dependencies → compatible by default"). − if LiteRT-LM 0.17.1 ships an unaligned `.so`, Warped is blocked on Google's release train; mitigation is the documented 16KB backcompat mode (works but shows a system dialog and is explicitly second-best) — never set `android:pageSizeCompat` in the manifest to silence it, since that hides the debt instead of tracking it.

**Example:**
```bash
# verify-16kb.sh sketch (CI gate on release APK)
unzip -o app-release.apk -d /tmp/warped_apk
./check_elf_alignment.sh app-release.apk          # expect ALIGNED for arm64-v8a
zipalign -c -P 16 -v 4 app-release.apk            # expect "Verification successful"
adb shell getconf PAGE_SIZE                       # 16384 on the 16KB test image
```

### Pattern 2: API-36 audit as allowlist — assert each behavior change, change as little as possible

**What:** Walk the two official behavior-change lists and record a disposition per item against the current code, defaulting to "already conformant, verified by X." From this research, the dispositions are:
- *All-apps / JobScheduler quotas* → download worker already foreground `dataSync`; **benchmark worker** is the one component that must log `WorkInfo.getStopReason()` and tolerate quota stops. Only fix if observed.
- *All-apps / 16KB compat mode* → converges with Pattern 1; no manifest property.
- *Target-36 / edge-to-edge opt-out removed* → target-35 already enforced edge-to-edge; grep for `windowOptOutEdgeToEdgeEnforcement` and delete any use (the attribute is silently ignored on API 36, so stale uses are dead weight, not crashes).
- *Target-36 / predictive back* → if back handling uses only NavController/`OnBackPressedDispatcher` with supported APIs, no change; only if custom `onBackPressed`/key interception exists, either migrate or set `android:enableOnBackInvokedCallback="false"` as a deliberate, documented deferral.
- *Target-36 / `scheduleAtFixedRate` single-catch-up* → grep scheduled executors (`MemorySampler`, benchmark sampling); only matters if code counts missed executions.
- *Local Network Permission* → **opt-in phase only, not enforced**; Ollama/LM Studio-on-LAN and Tavily flows need no permission code today. Record as monitored future work, do not add `NEARBY_WIFI_DEVICES` permission requests now (would confuse users for zero benefit).
- *Intent hardening / Safer Intents* → Warped's `BrowserIntents` (external browser opens) and share intents are standard explicit intents; no `removeLaunchSecurityProtection` needed; do not opt into `enforceIntentFilter` prematurely.
**When to use:** The whole API-36 workstream — it is a checklist with evidence, not a refactor.
**Trade-offs:** + minimal diff, minimal regression risk on a shipped app. − requires real-device/API-36-emulator verification per item; emulator-only gaps must be recorded as release-UAT items (house precedent: v2.2/v2.3/v2.4 all carry device-smoke deferrals).

### Pattern 3: Turn-scoped structured concurrency for every cancellable stream

**What:** Every streaming resource (inference tokens, SSE `Call`s, grounding fetch fan-out, retry jobs) is owned by exactly one coroutine scope whose lifetime equals one chat turn (`generationJob`), and every handle is nulled/closed on all three exit paths: new-turn pre-cancel, Stop, `onCleared`. This pattern is **already the codebase norm** (`generationJob` + `fetcher.cancel()` + `stopResponse()` triple-cancel; `shareIn(this, …)` scoped to the generation job at ChatViewModel ~line 904; `activeCall` AtomicReference with `onCompletion` null-out in `LmStudioHelper`). The leak audit's job is to *verify completeness* of this pattern at each site and close the gaps ( научной: `callbackFlow` without `awaitClose { call.cancel() }`, sessions created but never closed, jobs nulled on some paths but not others).
**When to use:** All leak fixes in chat, providers, and grounding.
**Trade-offs:** + consistent with the v2.1 CR-02 interleaving fix and v2.4 channel-hygiene work — reviewers already know the shape. − the triple-exit-path discipline is easy to regress; each fix should add or extend a unit test asserting cancel/close (e.g. "stop cancels all activeCalls", "close() with live sessions releases sessions first").

**Example:**
```kotlin
// Canonical turn-scoped SSE stream (what the audit should confirm everywhere):
fun stream(url: String): Flow<StreamToken> = callbackFlow {
    val call = client.newCall(request)
    activeCall.set(call)
    try {
        call.execute().use { response ->   // use{} closes the body on all paths
            // ... parse SSE, trySend tokens ...
        }
    } finally {
        activeCall.set(null)
    }
    awaitClose { call.cancel() }           // THE audit line: must exist
}
```

### Pattern 4: Singleton owns native, Application owns pressure signals, nothing else holds either

**What:** The native `Engine` (hundreds of MB via mmap) is referenced only through the `EngineManager` → `LiteRTLmEngine` `@Singleton` chain; the only lifecycle signals are `Application.onTrimMemory → handleTrimMemory` (already wired, with RUNNING_LOW soft-cap vs RUNNING_CRITICAL unload+evict tiers) and explicit user/model-switch unload. No ViewModel, Composable, or repository may cache `Engine`, `Conversation`, or `modelPath` references beyond the turn. The audit checks for exactly this: any `Conversation` stored outside `openSessions`, any engine handle in a ViewModel field, any `remember {}` holding a session across recompositions.
**When to use:** Native-side leak audit.
**Trade-offs:** + single ownership makes the 4 GB-model OOM story tractable (`largeHeap` + `MemoryChecker` gate + trim handling already form a coherent defense). − singleton-scoped `ioScope` (never cancelled) is *by design* but must stay free of per-turn state — a `launch` that captures a `Conversation` or callback there outlives the turn silently.

## Data Flow

### Request Flow — v2.5 adds no data flow

There is deliberately no new data flow in this milestone. The flows the audit traces (to prove nothing is retained) are the existing ones:

```
[Turn start] ChatViewModel.sendMessage()
    ↓ generationJob = viewModelScope.launch { ... }   (turn scope — AUDIT: cancelled on all exits?)
    ↓ fetcher/multiUrlFetcher fan-out                 (AUDIT: children inherit turn scope?)
    ↓ provider stream (callbackFlow + Call)           (AUDIT: awaitClose cancels Call? body use{}?)
    ↓ engine.createConversation → Conversation        (AUDIT: session closed before engine close?)
[Turn end / Stop / new send / onCleared]
    ↓ fetcher.cancel() + stopResponse() + job.cancel()(AUDIT: all three, all paths?)
    ↓ activeCall.set(null), openSessions pruned       (AUDIT: no stale handle reuse?)
```

### State Management

```
LeakCanary (debug only) observes; fixes change ownership discipline, not state shape:
  _transcript / _input / _connection (48-01 single-owner) — UNCHANGED shape
  generationJob / retryJob nullable handles            — FIX: null-out verified on every path
  openSessions set in LiteRTLmEngine                   — FIX: close-before-engine invariant kept
  activeCalls registry / activeCall ref                — FIX: cancel + null verified
  OkHttp pools/caches (app-lifetime singletons)        — NO CHANGE (by design)
  Coil memory (25%) + disk (50 MB og_thumbnails)       — NO CHANGE; trim handled by Coil internally
```

Key state decisions:
1. **LeakCanary is debug-only and installs itself.** No `Application.onCreate` code, no Hilt module, no `ContentProvider` entry — `leakcanary-android` ships its own startup provider. Release APK is byte-identical except the absent dependency. (Per sibling STACK.md: `leakcanary-android:2.14` + `plumber-android:2.14`, `debugImplementation`.)
2. **Heap dumps never leave the device by default.** Large LLM-session heaps may need `shark-cli` off-device analysis (sibling STACK.md) — that is a developer-workstation step, not an app data flow; no new permissions, no upload code.
3. **`onTrimMemory` tiers stay the memory-pressure contract.** The audit must not "fix" leaks by unloading more aggressively (that would regress the v1.5 "resume without model-not-loaded" requirement). Leak fixes remove *unintended* retention; pressure handling stays as designed.

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| v2.5 scope (one release, existing user base) | No scaling work. 16KB verification is per-APK, API-36 audit is per-behavior, leak fixes are per-site. All linear in the existing codebase. |
| Future: 16KB-only devices (post Feb-2027 Play gate) | The `verify-16kb.sh` CI gate becomes release-blocking; LiteRT-LM version bumps must re-run it (new AAR = new `.so` set). |
| Future: local-network permission enforcement | When Google moves LNP from opt-in to enforced, add the Nearby-devices permission flow around LAN endpoint connections only — remote-cloud and on-device paths unaffected. |

### Scaling Priorities

1. **First bottleneck: LiteRT-LM AAR alignment (external dependency).** If `liblitertlm_jni.so` (or its GPU delegate `.so`s) is `UNALIGNED`, Warped cannot ship Play-compliant until Google ships an aligned release. Check this *first* — it is the only item that can block the milestone from outside the repo.
2. **Second bottleneck: heap-dump size during leak triage.** Debug builds chatting with multi-GB models produce heaps too large for on-device Shark analysis; plan workstation-based `shark-cli` analysis from the start rather than discovering it mid-audit.

## Anti-Patterns

### Anti-Pattern 1: Adding linker flags / NDK config to "fix" 16KB

**What people do:** Add `ndkVersion`, CMake `target_link_options(-z max-page-size=16384)`, or `Android.mk` changes to a project with no native sources.
**Why it's wrong:** There is nothing to compile — every `.so` is prebuilt inside AARs. Linker flags on an empty native build change nothing while creating the illusion of compliance; the Play Console warning would persist.
**Do this instead:** Verify with `check_elf_alignment.sh` + `zipalign -P 16`; fix by bumping the offending dependency.

### Anti-Pattern 2: Setting `android:pageSizeCompat` to silence the backcompat dialog

**What people do:** Add the manifest property so the 16KB compat-mode warning stops appearing during testing.
**Why it's wrong:** It opts the app into the degraded compat path permanently and hides genuine misalignment from CI and testers. Google's guidance is explicit: align the app; compat mode is a safety net, not a target.
**Do this instead:** Leave the property unset; treat any compat dialog during testing as a failing test.

### Anti-Pattern 3: "Fixing" leaks by widening unload (aggressive engine eviction)

**What people do:** On finding retained memory, add extra `unloadCurrent()` calls (e.g. on every backgrounding, every navigation).
**Why it's wrong:** Directly regresses the validated v1.5 requirement "app resumes active chat without model-not-loaded warning after backgrounding" and makes every resume pay a multi-second reload. It treats the symptom (heap size) while the disease (unintended retention) remains.
**Do this instead:** Remove the unintended reference (scope the job, close the session, null the handle); keep pressure-driven eviction exactly where it is.

### Anti-Pattern 4: Leaking the audit into feature refactors

**What people do:** While auditing `ChatViewModel`, "simplify" the turn pipeline, merge `generationJob`/`retryJob`, or rework the grounding orchestration.
**Why it's wrong:** v2.5 ships conformance + stability on a codebase with 12 shipped milestones. Refactors widen the blast radius and invalidate the device-smoke history (v2.2–v2.4 deferrals assume current structure). The house rule from v2.1 applies: finish what's there before reshaping it.
**Do this instead:** Minimal diffs at the owning component; each fix paired with a regression test; no signature changes to `LlmModelHelper`, provider interfaces, or Room schema (no migration in this milestone — none is needed).

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Google Play Console | 16KB compliance signal + target-36 gate | Play warns on 4KB-only updates targeting API 35+; hard block from Feb-2027. Verification is local (`zipalign -P 16`); Console is the confirm, not the test. |
| LiteRT-LM Maven (`litertlm-android:0.17.1`) | Transitive `.so` supplier; version-bump lever only | Check its `.so` alignment first (Pattern 1, priority 1). NPU path keeps `libcdsprpc.so required=false`. No JNI code changes in Warped. |
| SQLCipher (+ other AARs with `.so`) | Same verify-and-bump treatment | Enumerate via APK Analyzer `lib/` folder; every `arm64-v8a` + `x86_64` `.so` must be `ALIGNED`. |
| Android 16 device / 16KB emulator image | Test environments, not code deps | `adb shell getconf PAGE_SIZE` → `16384`; compat-flag `adb` overrides for quota testing (`OVERRIDE_QUOTA_ENFORCEMENT_TO_TOP_STARTED_JOBS`, `…_TO_FGS_JOBS`). |

### Internal Boundaries

| Boundary | Communication | New vs modified | Notes |
|----------|---------------|-----------------|-------|
| Gradle build ↔ APK `.so` set | `check_elf_alignment` + `zipalign` gate | NEW script (optional), MODIFIED nothing | Zero app-code impact; CI-gate candidate. |
| Manifest ↔ Android 16 OS | `targetSdk 36` (set), FGS type (set), back-callback decision | MODIFIED at most 1 attr | No new permissions in v2.5 (LNP not enforced; notifications/alarms already declared). |
| WorkManager ↔ JobScheduler quotas | `getStopReason()` logging | MODIFIED benchmark worker only if gaps found | Download worker already foreground-exempt. |
| `Application` ↔ `EngineManager` | `onTrimMemory` tiers | UNCHANGED | Audit must preserve; not a leak-fix lever. |
| `EngineManager` ↔ `LiteRTLmEngine` ↔ native | `init` / `createConversation` / ordered `close` | MODIFIED (fix-only, close/session gaps) | No interface change; `LlmModelHelper` untouched. |
| `ChatViewModel` ↔ providers ↔ grounding | Turn scope + cancel triple | MODIFIED (fix-only, scope/handle gaps) | No pipeline rework (Anti-Pattern 4). |
| LeakCanary ↔ everything | Debug-only observation | NEW dependency, ZERO integration code | Auto-install; release footprint nil. |
| Room schema (v16) | No migration | UNCHANGED (explicit non-goal) | Stating so the phase plan doesn't invent one. |

## Suggested Build Order (dependency-gated)

1. **16KB dependency verification (blocks Play submission — do first).** Extract release APK → `check_elf_alignment.sh` → attribute every `.so` to its AAR → `zipalign -P 16` → record aligned/misaligned per dependency. If LiteRT-LM 0.17.1 (or SQLCipher) is misaligned: bump-or-escalate decision before anything else. *No code; unblocks the release regardless of what follows.*
2. **API-36 behavior-change audit with evidence.** Walk the all-apps + target-36 lists (Pattern 2 dispositions), verifying each against code + API-36 emulator/device. Land the small fixes (edge-to-edge dead attr, back-callback decision, benchmark stop-reason logging) as they are found. *Depends on nothing; parallelizable with 1.*
3. **LeakCanary instrumentation + guided audit.** Add the two `debugImplementation` lines (sibling STACK.md), run the scripted leak tour (cold start → load model → chat turns → Stop mid-stream → model switch → grounding turns → OG thumbnails → background/foreground → endpoint CRUD), triage heap dumps per layer (native → VM → network → Compose). *Depends on 1–2 only for scheduling (needs a runnable API-36/16KB build to be meaningful); technically independent.*
4. **Fix → regression-test → re-verify loop, per owning component.** Each fix at its owner with a cancel/close unit test; full `289+`-style unit gate + release assemble green; close with a 16KB-image + API-36-device smoke (fold into the existing release-UAT deferral pattern if hardware is unavailable).

## Sources

- Official docs (HIGH): `developer.android.com/guide/practices/page-sizes` (Play 16KB requirement for target-35+, Feb-2027 block; AGP ≥ 8.5.1 + NDK r28 default-align; verify via `check_elf_alignment.sh` + `zipalign -P 16`; RELRO check) — fetched 2026-09-30
- Official docs (HIGH): `developer.android.com/about/versions/16/behavior-changes-all` (JobScheduler quota enforcement, incl. FGS-concurrent jobs; 16KB compat mode + `android:pageSizeCompat`) — fetched 2026-09-30
- Official docs (HIGH): `developer.android.com/about/versions/16/behavior-changes-16` (edge-to-edge opt-out dead on target-36; predictive-back default + `enableOnBackInvokedCallback` opt-out; `scheduleAtFixedRate` single catch-up; Local Network Permission opt-in phase via `RESTRICT_LOCAL_NETWORK` compat flag; Safer Intents opt-in) — fetched 2026-09-30
- Live codebase (HIGH): `app/build.gradle.kts` (AGP via catalog 9.3.0, compile/target 36, `useLegacyPackaging=false`, `largeHeap`, `extractNativeLibs=false`); `gradle/libs.versions.toml` (litertlm 0.17.1, coil3 3.4.0 ceiling note); `AndroidManifest.xml` (FGS dataSync merge, permissions, native-lib `required=false`); `EngineManager.kt` / `LiteRTLmEngine.kt` (singleton chain, `openSessions`, ordered close, trim tiers); `ChatViewModel.kt` (job discipline, turn-scoped `shareIn`, `onCleared`); `LmStudioHelper.kt` / `LMStudioProvider.kt` (`activeCall`, `callbackFlow`); `NetworkModule.kt` (4-client shape); `WarpedApplication.kt` (Coil singleton factory, StrictMode, trim forwarding); `ChatScreen.kt` (collectors, DisposableEffects); `WebPageFetcher.kt` (`activeCalls` registry)
- Sibling research (MEDIUM — consumed as input, not re-verified): `.planning/research/STACK.md` (LeakCanary 2.14 + plumber-android `debugImplementation`, Coil ceiling, Kotlin hold)
- No `cpp/`, CMake, or `ndkVersion` in repo (HIGH — grep-verified): the basis for the verify-don't-rebuild recommendation

---
*Architecture research for: Warped v2.5 Play Compliance + Leaks*
*Researched: 2026-09-30*
