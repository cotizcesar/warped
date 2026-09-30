# Feature Research

**Domain:** Android Play compliance (16 KB pages, API 36) + memory-leak audit for on-device LLM chat app
**Researched:** 2026-09-30
**Confidence:** HIGH (16 KB + API 36 behavior changes from official Android Developers docs; leak-audit practice from LeakCanary official docs + community consensus — MEDIUM on Play deadline exact dates, which shifted via extensions)

## Feature Landscape

### Table Stakes (Users Expect These)

Play compliance and stability items. Missing these = app can't ship updates on Play, or crashes/OOMs in long sessions. All scoped to the v2.5 milestone — existing features (chat, catalog, grounding, OG thumbnails, agentic loops) are already built and out of scope except as leak-audit surfaces.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| 16 KB page-size support (ELF 16 KB alignment) | Play blocks updates targeting API 35+ without it (deadline Nov 1 2025, extensions to mid-2026/2027 per Play notices); 16 KB devices can't install/run unaligned native libs | LOW | Warped is Kotlin-first so likely near-free: AGP 8.5.1+ auto-aligns uncompressed .so at packaging; NDK r28+ compiles 16 KB-aligned by default. Work = bump AGP/NDK, verify every `.so` (LiteRT-LM AAR, Coil/OkHttp transitive natives if any) with APK Analyzer + `check_elf_alignment.sh`, smoke-test on 16 KB emulator image (arm64 v8a). No code change if pure Kotlin. |
| Target API 36 (compileSdk 36 + targetSdk 36) with behavior-change audit | Play annual target-API requirement (~Aug 2026 for API 36); targeting 36 flips runtime behaviors even with no code change | MEDIUM | Two-step: `compileSdk 36` first (zero behavior change, surfaces deprecations), then `targetSdk 36` + audit. Mandatory fixes: edge-to-edge (opt-out flag dead), predictive back, large-screen resizability, JobScheduler/WorkManager quota sensitivity. |
| Mandatory edge-to-edge UI | API 36 ignores `windowOptOutEdgeToEdgeEnforcement` / `setDecorFitsSystemWindows(false)`-style opt-outs; content renders behind status/nav bars without insets | MEDIUM | Compose: `enableEdgeToEdge()` in Activity + `WindowInsets` consumption (`safeContent`/`systemBars`, Scaffold `contentWindowInsets`), remove any opt-out attr. Acceptance: no overlap/bleed on gesture-nav + 3-button nav, light/dark icon contrast. Chat screen (pill input, bottom sheet, Fuentes list) is the highest-risk surface. |
| Predictive-back compliance | API 36 enables predictive back by default; `onBackPressed()` no longer called, `KEYCODE_BACK` not dispatched | LOW | Migrate back interception to `OnBackInvokedCallback` / Compose `PredictiveBackHandler` / `OnBackPressedDispatcher.addCallback`. Audit: chat back (exit sheet? exit conversation?), bottom-sheet dismiss, settings/preset screens. Or explicit `android:enableOnBackInvokedCallback=false` opt-out as stopgap (document as tech debt). |
| Large-screen adaptability (sw ≥ 600dp) | API 36 ignores orientation/resize/aspect constraints (`resizeableActivity=false`, min/maxAspectRatio) on tablets/foldables/ChromeOS; pillarboxing gone | LOW | Remove reliance on portrait lock / aspect limits; verify chat + catalog + bottom sheets fill window on tablet/foldable emulator. Temporary opt-out exists via manifest compat flag — prefer real adaptivity (this app is a scrolling chat list, inherently adaptive). |
| Foreground-service types + JobScheduler/WorkManager quota conformance | Android 16 enforces FGS types/timeouts and JobScheduler runtime quotas by standby bucket (affects WorkManager model downloads + any `DownloadManager`/periodic jobs) | MEDIUM | Work = declare precise FGS types for download workers, use user-initiated data-transfer jobs where applicable, log `WorkInfo.getStopReason()` / `JobParameters.getStopReason()` (incl. new `STOP_REASON_TIMEOUT_ABANDONED`), test download progress/cancel/retry under quota pressure. Existing background-download + offline-retry flows are the test bed. |
| Full memory-leak audit with fixes (EngineManager, ViewModels, chat Flows, grounding pipeline, Coil/OkHttp) | Long chat + model-load/unload + multi-URL grounding sessions OOM or jank without it; users expect an LLM app to survive hours of use | MEDIUM | LeakCanary (debug-only dep) scripted pass over: model load/switch/unload, streaming chat + Stop/cancel, 5-URL grounding fan-out + cancel, offline→retry, OG thumbnail scroll, endpoint CRUD, config rotation/process death. Fix classes: uncancelled `Flow` collections, singleton holding Activity context, leaked `JobParameters`/callbacks, OkHttp `Call`/`ResponseBody` not closed, Coil requests outliving composables, SSE streams not cancelled. Acceptance: zero application leaks on scripted pass. |
| Release verification gates (aligned AAB + 16 KB emulator + no-leak pass) | Compliance is only real if CI/device-verified; Play Console flags non-compliant AABs | LOW | `check_elf_alignment.sh` on release AAB in CI, `assembleRelease` + R8 green, smoke on 16 KB system image, LeakCanary pass clean, Play Console pre-launch report with no 16 KB/target-API warnings. |

### Differentiators (Competitive Advantage)

Not required by Play, but valuable for an on-device LLM app where sessions are long and models are huge.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Leak-free multi-hour chat sessions (ViewModel + Flow hygiene) | On-device LLM apps die by a thousand retained chat states; surviving long sessions is the core-value multiplier | MEDIUM | `collectAsStateWithLifecycle`, `viewModelScope` cancellation on clear, single-flight `runInference` cancel propagation, transient Using-rows cleanup. Builds directly on v2.1 cancellable-inference work. |
| Model-memory discipline (load/unload without retained engine) | A 4–8 GB model that can't fully unload bricks the phone; clean unload → reload is the LM-Studio-grade expectation | MEDIUM | EngineManager releases native handles on switch/unload, no static `LlmInference` refs, memory-pressure listener suggests smaller quant. Device-verified on real phone (emulator RAM behavior differs). |
| Grounding-pipeline cancellation hygiene (5-fan-out + Tavily + SSE) | Parallel fetch + streaming + search is the leakiest surface (5 concurrent OkHttp calls, SSE accumulators, per-source progress); clean cancel = no zombie network + no retained chat rows | MEDIUM | Structured-concurrency scope per message-send, `Stop` cancels fan-out + SSE + Tavily, same-row reuse without retaining old jobs. v2.3/v2.4 cancel guards are the foundation. |
| Coil thumbnail cache discipline (disk-bounded, composable-scoped) | OG thumbnails per source can balloon image cache across long grounded threads; bounded cache = smooth scroll without OOM | LOW | Coil 3.4.0 singleton + disk cache (already in v2.4), verify requests cancel on list recycle, cap memory cache for chat context. |
| Faster cold start / lower battery from 16 KB pages | Google cites 3–30% launch improvement, ~4.5% battery gain on 16 KB devices — free marketing + real UX win for a heavyweight app | LOW | No extra work beyond 16 KB support; optionally record before/after cold-start on reference device as release note. |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Rewriting inference/network stack "while we're at it" | Compliance milestone feels like a good time to modernize | Scope explosion; LiteRT-LM 0.17.1 + OkHttp/SSE + Coil stack is proven (494/494 green in v2.4). Touching it risks regressions with zero Play benefit | Freeze engine/network deps; only bump AGP/NDK/compileSdk/targetSdk + what's needed for alignment |
| Shipping LeakCanary (or any heap-dump tooling) in release | "Detect leaks in production" | Heap dumps freeze the app, leak PII/chat content to disk, bloat release APK; Play pre-launch + debug pass is the right venue | `debugImplementation` only; release gets lightweight `WorkInfo.getStopReason()` logging + crash-handler OOM breadcrumbs |
| `android:largeHeap="true"` as the leak fix | Quick OOM suppression | Masks real leaks, hurts system-wide memory, doesn't survive Play review scrutiny for behavior; delays the actual audit | Fix retention roots; use `largeHeap` only if a specific model-load path proves it necessary with profiler evidence |
| Blanket `enableOnBackInvokedCallback=false` + orientation-lock compat flags as permanent fixes | Fastest way to silence API 36 behavior changes | Accumulates compat debt; Google removes these escape hatches (as it just did with edge-to-edge opt-out) | Use opt-outs only as stopgaps with a tracked follow-up; ship real edge-to-edge + predictive back + adaptive layout |
| Dropping 32-bit ABIs / minSdk bump to dodge 16 KB work | Fewer .so to align | 16 KB requirement targets 64-bit; 32-bit alignment has its own edge cases, and minSdk bumps cut off real users for no benefit | Keep ABI/minSdk surface unchanged; align what ships, verify per-ABI with the alignment script |
| Custom native memory manager / manual `mmap` tuning for 16 KB | "Optimize" page handling by hand | LiteRT-LM owns native allocation; hand-tuning against its allocator invites corruption that only reproduces on 16 KB hardware | Rebuild with NDK r28+ defaults, test, and file upstream issues if a bundled .so is misaligned |

## Feature Dependencies

```
16 KB page support
    └──requires──> AGP 8.5.1+ / NDK r28+ toolchain bump
                       └──requires──> LiteRT-LM AAR (0.17.1) ships 16 KB-aligned .so
                                              (if misaligned: needs upstream fix or repackaging)

targetSdk 36 audit
    ├──requires──> compileSdk 36 first (zero-behavior-change step)
    ├──requires──> Edge-to-edge UI ──enhances──> chat/bottom-sheet visuals
    ├──requires──> Predictive-back migration
    └──requires──> FGS types + WorkManager quota conformance ──enhances──> model downloads + offline retry

Memory-leak audit + fixes
    ├──requires──> LeakCanary debug harness
    ├──covers──> EngineManager / model load-unload
    ├──covers──> Chat Flows + single-flight runInference cancel
    ├──covers──> Grounding fan-out (multi-URL + Tavily) + SSE accumulators
    ├──covers──> Coil OG thumbnails + OkHttp clients
    └──requires──> NOTHING new in user features (audit-only; no behavior change expected)

Release gates ──requires──> all three above (alignment script + 16 KB emulator smoke + zero-leak pass)
```

### Dependency Notes

- **16 KB requires toolchain bump:** AGP auto-aligns at packaging and NDK r28 compiles aligned by default — the cheapest path is upgrading, not hand-editing linker flags (`-Wl,-z,max-page-size=16384` is the legacy manual route for NDK ≤ r27).
- **LiteRT-LM .so is the critical external dependency:** Warped ships no hand-written JNI (llama.cpp removed in v1.5); if the bundled LiteRT-LM native lib is misaligned, the fix is an upstream version bump or ABI repackaging — verify first with APK Analyzer before assuming work is needed.
- **compileSdk before targetSdk:** raising `compileSdk` to 36 changes nothing at runtime and surfaces deprecations; `targetSdk 36` is what flips edge-to-edge/predictive-back/resizability — sequence them as separate verifiable steps.
- **Leak audit conflicts with feature work in the same phase:** audit needs a frozen surface to get a stable baseline; combining with UI rewrites invalidates the pass. Keep v2.5 audit-only.
- **WorkManager quota work enhances downloads:** existing background-download progress/cancel + offline-retry flows become the conformance test bed — no new download feature needed.

## MVP Definition

(v2.5 is a compliance + stability milestone, so "MVP" = minimum shippable Play-compliant release.)

### Launch With (v1 — this milestone, P1)

- [ ] 16 KB alignment verified — every shipped `.so` 16 KB-aligned (`check_elf_alignment.sh` green), AGP/NDK bumped, 16 KB emulator smoke passes — without it Play blocks updates
- [ ] targetSdk 36 + behavior audit closed — edge-to-edge real (no opt-out), predictive back migrated, large-screen smoke, FGS/WorkManager quota conformance for downloads — without it Play blocks updates on the annual deadline
- [ ] Zero-application-leak pass — scripted LeakCanary sweep over model load/switch/unload, streaming + Stop, 5-URL grounding + cancel, offline→retry, thumbnail scroll; all found application leaks fixed and re-verified
- [ ] Release gates green — aligned AAB, `assembleRelease` + R8, Play Console with no 16 KB/target warnings

### Add After Validation (v1.x — only if the pass surfaces them)

- [ ] Cold-start / battery before-after numbers on reference device — trigger: 16 KB device available; feeds release notes
- [ ] Memory-pressure UX (suggest smaller quant on low RAM) — trigger: audit finds OOM-adjacent paths that aren't leaks per se
- [ ] Per-screen predictive-back animations polish — trigger: default migration works but feels abrupt in chat/sheets

### Future Consideration (v2+ — explicitly out of v2.5)

- [ ] Engine/network dependency modernization — why defer: zero Play benefit, high regression risk on a 494-green stack
- [ ] Production memory telemetry (telemetry-gated, privacy-reviewed) — why defer: needs PII story for chat content first
- [ ] Tablet/foldable bespoke layouts — why defer: adaptive-fill compliance is enough; bespoke layouts are product work, not compliance

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| 16 KB alignment verification | HIGH (installable on new devices; Play shippable) | LOW | P1 |
| targetSdk 36 + edge-to-edge | HIGH (Play shippable; visible UI correctness) | MEDIUM | P1 |
| Predictive-back migration | MEDIUM (correct back everywhere) | LOW | P1 |
| FGS/WorkManager quota conformance | HIGH (downloads survive Android 16 quotas) | MEDIUM | P1 |
| Memory-leak audit + fixes | HIGH (long-session stability = core value) | MEDIUM | P1 |
| Large-screen adaptability smoke | MEDIUM (foldable/tablet correctness) | LOW | P1 |
| Release gates (alignment CI + pre-launch) | MEDIUM (prevents regressions) | LOW | P1 |
| Cold-start/battery numbers | LOW (release-note fodder) | LOW | P2 |
| Memory-pressure UX | MEDIUM (graceful degradation) | MEDIUM | P2 |
| Predictive-back animation polish | LOW (feel, not function) | LOW | P3 |

**Priority key:**
- P1: Must have for launch
- P2: Should have, add when possible
- P3: Nice to have, future consideration

## Competitor Feature Analysis

| Feature | Google AI Edge Gallery (reference impl) | LM Studio (desktop) | Our Approach |
|---------|----------------------------------------|---------------------|--------------|
| 16 KB / target-API currency | Tracks latest AGP/NDK via Google maintainers; de-facto compliance reference for LiteRT-LM apps | Desktop — N/A (no Play policy pressure) | Match Gallery's toolchain posture (AGP 8.5.1+/NDK r28+), verify LiteRT-LM .so alignment the same way |
| Edge-to-edge / predictive back | Compose-first, adopts new platform behaviors early | Desktop windowing — N/A | Real Compose insets + back-handler migration, no permanent opt-outs |
| Leak/stability discipline | Sample-grade; not held to long-session bar | Long-session desktop app; process memory is abundant | Differentiate: audit explicitly for multi-hour chat + model switch + grounding cancel — the mobile-hard part neither reference fully covers |

## Sources

- Android Developers — "Support 16 KB page sizes" (official guide: Play requirement for API 35+ on 64-bit, AGP 8.5.1+ auto-align, NDK r28+ default, APK Analyzer + `check_elf_alignment.sh`, 16 KB emulator images) — https://developer.android.com/guide/practices/page-sizes — HIGH
- Android Developers Blog — "Prepare your apps for Google Play's 16 KB page size compatibility requirement" (Nov 1 2025 enforcement, benefits data) — https://android-developers.googleblog.com/2025/05/prepare-play-apps-for-devices-with-16kb-page-size.html — HIGH
- Android Developers Blog — "Transition to using 16 KB page sizes for Android apps and games using Android Studio" (who must recompile, Studio tooling table) — https://android-developers.googleblog.com/2025/07/transition-to-16-kb-page-sizes-android-apps-games-android-studio.html — HIGH
- Android Developers — "Behavior changes: Apps targeting Android 16" (edge-to-edge opt-out removal, predictive back default, large-screen constraint ignore, fixed-rate scheduling) — https://developer.android.com/about/versions/16/behavior-changes-16 — HIGH
- Android Developers — "Behavior changes: all apps" (JobScheduler quota by standby bucket, FGS-concurrent quota, `STOP_REASON_TIMEOUT_ABANDONED`, affects WorkManager/DownloadManager) — https://developer.android.com/about/versions/16/behavior-changes-all — HIGH
- Community migration guides (API 34/35 → 36 practical guide; Halodoc Android 16 journey: FGS types, edge-to-edge, compat-flag sequencing) — MEDIUM (single-source, consistent with official docs)
- LeakCanary official docs — "How LeakCanary works" (ObjectWatcher on destroyed Activity/Fragment/View/ViewModel, retained threshold → heap dump → analysis → categorization) — https://github.com/square/leakcanary/blob/main/docs/fundamentals-how-leakcanary-works.md — HIGH
- LeakCanary GitHub (square/leakcanary, ~30k stars, Apache-2.0; debug-only integration, instrumentation fail-on-leak listener) — HIGH
- Community LeakCanary fix patterns (remove callbacks on destroy, avoid Activity-context singletons, cancel unscoped coroutines) — MEDIUM (patterns consensus, verify per-leak against heap trace)

---
*Feature research for: v2.5 Play Compliance + Leaks (16 KB, API 36, memory-leak audit)*
*Researched: 2026-09-30*
