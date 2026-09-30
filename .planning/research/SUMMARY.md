# Project Research Summary

**Project:** Warped — v2.5 Play Compliance + Leaks
**Domain:** Android on-device LLM chat app (LiteRT-LM / Compose) — Play-compliance retrofit + memory-leak audit milestone
**Researched:** 2026-09-30
**Confidence:** HIGH

## Executive Summary

Warped is a shipped, production-grade Android LLM chat app (12 milestones, 494-green test suite in v2.4) entering a **compliance + stability milestone, not a feature milestone**. The v2.5 work is threefold: (1) prove 16 KB page-size compatibility for Play (AGP 9.3.0 already auto-aligns packaging; the risk is confined to transitive prebuilt `.so` files from LiteRT-LM 0.17.1 and SQLCipher 4.5.4), (2) audit runtime behavior under the already-set `targetSdk 36` (edge-to-edge opt-out removal, predictive back default, large-screen resizability ignore, JobScheduler quota sensitivity, Local Network Permission opt-in), and (3) run a scripted memory-leak audit over the heaviest surfaces (EngineManager native handles, chat turn-scoped Flows/SSE, grounding 5-fan-out, Coil thumbnails, download workers) with LeakCanary 2.14 as a debug-only observer.

The recommended approach is **verify-don't-rebuild, audit-don't-refactor, fix-at-the-owner**. 16 KB needs zero Kotlin changes — extract the release APK, run `check_elf_alignment.sh` + `zipalign -c -P 16`, attribute any UNALIGNED `.so` to its Maven AAR, and fix by version bump only (never linker flags, never hand-patched `.so`, never `pageSizeCompat`). The API-36 audit is an allowlist checklist with evidence per behavior change, defaulting to "already conformant." Leak fixes are 5–20 line changes inside owning components following the already-established turn-scoped structured-concurrency pattern, each paired with a cancel/close regression test. No new packages, no Room migration, no engine/network modernization.

The key risks are external and behavioral, not architectural: an **unaligned LiteRT-LM `.so` blocks Play submission from outside the repo** (check first — it gates everything), `targetSdk 36` silently changes edge-to-edge/back/resizability contracts (audit before any leak churn), and leak fixes can boomerang into worse bugs (over-closed shared singletons, re-scoped state losing rotation drafts). Mitigation is sequencing — 16 KB verification first, API-36 audit second, leak tour third, hardening loop last — with the v2.1–v2.4 invariants (single-flight inference, Stop semantics, retry same-row reuse) re-run as regression gates after every fix.

## Key Findings

### Recommended Stack

No toolchain churn: AGP 9.3.0, Kotlin 2.3.20, compileSdk/targetSdk 36 all stay. The v2.5 delta is two debug-only additions (LeakCanary 2.14 + plumber-android 2.14) and lifting the stale Coil 3.4.0 ceiling to 3.5.x minimum (validate 3.6.3 latest), plus platform verification tooling (`check_elf_alignment.sh`, `zipalign -P 16`, 16 KB emulator image). See STACK.md for install snippets and compatibility matrix.

**Core technologies:**
- AGP 9.3.0 (keep) — 16 KB zip-alignment automatic since 8.5.1; `useLegacyPackaging=false` already correct
- LeakCanary 2.14 + plumber-android (NEW, `debugImplementation` only) — auto-installs, zero app code; covers EngineManager singletons, chat StateFlow collectors, Coil requests, OkHttp SSE Calls
- Coil 3 `3.5.x` min / validate `3.6.3` — unblocks compileSdk-36 line; same coordinates, low-risk bump validated by build + OG-thumbnail screen pass
- Verification tooling (no artifacts) — `check_elf_alignment.sh` for transitive `.so` (LiteRT-LM, SQLCipher), `zipalign -c -P 16`, 16 KB emulator image (`getconf PAGE_SIZE` → 16384), shark-cli for oversized heap dumps
- No NDK pin / no linker flags — zero first-party native code since v1.5; dead config if added

### Expected Features

v2.5 is audit-only on a frozen surface — no new user features. "MVP" = minimum shippable Play-compliant release. See FEATURES.md for the full prioritization matrix.

**Must have (table stakes):**
- 16 KB alignment verified — every shipped `.so` ALIGNED, 16 KB emulator smoke (model load + inference) passes
- targetSdk 36 + behavior audit closed — real edge-to-edge (no opt-out), predictive back migrated, large-screen smoke, FGS/WorkManager quota conformance for downloads
- Zero-application-leak pass — scripted LeakCanary sweep (load/switch/unload, streaming + Stop, 5-URL grounding + cancel, offline→retry, thumbnail scroll); all application leaks fixed + re-verified
- Release gates green — aligned AAB, `assembleRelease` + R8, Play Console with no 16 KB/target warnings

**Should have (competitive):**
- Leak-free multi-hour chat sessions — ViewModel + Flow hygiene; core-value multiplier for an on-device LLM app
- Model-memory discipline — clean unload→reload without retained engine; device-verified
- Grounding-pipeline cancellation hygiene — structured-concurrency scope per send; Stop cancels fan-out + SSE + Tavily
- 16 KB cold-start / battery numbers — free UX win, release-note fodder if a 16 KB device is available

**Defer (v2+):**
- Engine/network dependency modernization — zero Play benefit, high regression risk on a proven stack
- Production memory telemetry — needs PII story for chat content first
- Tablet/foldable bespoke layouts — adaptive-fill compliance suffices; bespoke is product work
- Shipping LeakCanary in release; `largeHeap=true` as a leak fix; permanent compat-flag opt-outs (all anti-features — see FEATURES.md)

### Architecture Approach

There is no new feature subsystem: 16 KB integrates at the build/packaging seam only, API 36 at the manifest + WorkManager + navigation seams (already mostly conformant), and leak fixes land inside existing components with LeakCanary as a debug-only observer adding zero architecture. Fixes follow two established patterns — turn-scoped structured concurrency (every streaming resource owned by one turn-lifetime scope, nulled/closed on all three exits) and singleton-owns-native (Engine referenced only via EngineManager→LiteRTLmEngine chain, trim tiers preserved). See ARCHITECTURE.md for the system diagram, per-component audit table, and suggested build order.

**Major components:**
1. Build/packaging seam (AGP + `packaging.jniLibs` + AAR `.so` set) — verify alignment, never rebuild; only NEW file allowed is `verify-16kb.sh` CI gate
2. Manifest + OS behavior seam (`targetSdk 36`, WorkManager download/benchmark workers, MainActivity/NavGraph) — allowlist audit, minimal diff
3. Runtime leak surface (EngineManager→LiteRTLmEngine native chain, ChatViewModel jobs/flows, SSE providers, grounding fetch scopes, Compose collectors) — fix at the owner, 5–20 lines each + regression test
4. Debug-only observability (LeakCanary 2.14 + plumber) — auto-installs, no Hilt binding, zero release footprint

### Critical Pitfalls

Top 7 from PITFALLS.md (all mapped to phases with verification):

1. **Unaligned LiteRT-LM `.so` you can't fix yourself** — prebuilt AAR alignment is Google's build decision; bump/compileSdk assumptions don't cover it. Avoid: `check_elf_alignment.sh` + APK Analyzer + `zipalign -P 16` on every RC, 16 KB emulator smoke; fix only by upgrading the artifact.
2. **targetSdk 36 without auditing the three behavior cliffs** (edge-to-edge opt-out dead, predictive back default, resizability ignored on sw600dp+) — chat input hidden, back-gesture wrong, tablet layout stretched. Avoid: dedicated audit pass first; migrate vs. dated-TODO opt-out explicitly.
3. **Local Network Permission blindsiding LAN endpoints** (Ollama/LM Studio over RFC-1918) — `EPERM` socket errors masquerading as server bugs. Avoid: opt into `RESTRICT_LOCAL_NETWORK` compat flag early, real-device Wi-Fi test, LAN-vs-internet error mapping. (ARCHITECTURE.md notes LNP is opt-in/not enforced today — record as monitored, don't add permission requests now.)
4. **Engine outliving the chat** (singleton holding scoped refs, native handles skipped on exception paths) — native RSS stair-steps while Java heap looks flat. Avoid: single-owner close, try/finally on all paths, application-context-only singletons, profiler loop to baseline.
5. **Streaming collectors that never cancel** (chat Flows + SSE bodies + shared accumulators) — connection-pool exhaustion, zombie fetches. Avoid: lifecycle-aware collection everywhere, `use{}`/finally on every SSE loop, Stop-means-closed, cancel-path regression tests.
6. **Coil singleton + WorkManager stream leaks** — unbounded thumbnail cache, partial-file/FD residue on cancel. Avoid: one app-context ImageLoader, bounded thumbnail requests, `use{}` + partial cleanup + backoff + unique-work policy.
7. **Remediation boomerang** (leak fix breaks threading/persistence — over-closed Room/OkHttp, re-scoped state losing rotation drafts). Avoid: fix ownership without moving dispatchers, never close shared singletons from screens, re-run v2.1–v2.4 invariant suite after every fix.

## Implications for Roadmap

Based on research, suggested phase structure:

### Phase 1: 16 KB Dependency Verification
**Rationale:** Gates the entire Play release from outside the repo — if LiteRT-LM 0.17.1 ships an unaligned `.so`, everything else is moot until bump-or-escalate resolves. Zero code, fastest possible unblock.
**Delivers:** Per-`.so` aligned/misaligned evidence (`check_elf_alignment.sh` + `zipalign -P 16` output), bump-or-escalate decision, `verify-16kb.sh` CI gate, 16 KB-image smoke (model load + inference).
**Addresses:** 16 KB page-size support; release-gate alignment CI.
**Avoids:** Pitfall 1 (unaligned native `.so`); Anti-Patterns 1–2 (linker flags, `pageSizeCompat`).

### Phase 2: API-36 Behavior Audit
**Rationale:** Behavior audit before code churn — flipping/confirming `targetSdk 36` changes platform contracts, and leak fixes on a shifting surface invalidate their own baselines. Parallelizable with Phase 1.
**Delivers:** Allowlist disposition per behavior change with evidence (edge-to-edge dead-attr grep + inset screenshots, back-handler migration/opt-out decision, large-screen rotation matrix, benchmark `getStopReason()` logging, scheduleAtFixedRate grep, LNP monitored-future record).
**Uses:** No new stack; manifest + WorkManager + navigation seams only.
**Implements:** Manifest/OS-behavior layer conformance; download worker already foreground-`dataSync` (exempt path confirmed).
**Avoids:** Pitfalls 2–3 (behavior cliffs, LAN permission); UX pitfalls (insets, back-vs-Stop, compat dialog).

### Phase 3: LeakCanary Instrumentation + Guided Audit
**Rationale:** Needs a runnable API-36/16 KB build to be meaningful (scheduling dependency on Phases 1–2), but technically independent. Establishes the frozen-surface baseline the fixes verify against.
**Delivers:** Two `debugImplementation` lines, scripted leak tour (cold start → load → chat turns → Stop mid-stream → model switch → grounding turns → thumbnails → background/foreground → endpoint CRUD), triaged heap dumps per layer (native → VM → network → Compose) via on-device activity + shark-cli.
**Uses:** LeakCanary 2.14 + plumber-android; strict `debugImplementation` (release APK must contain zero LeakCanary classes).
**Implements:** Debug-only observability seam; no production architecture change.
**Avoids:** Pitfall 7 setup (baseline before fixes); performance trap (LeakCanary in release).

### Phase 4: Fix → Regression-Test → Re-verify Loop (+ Hardening)
**Rationale:** Fixes land per owning component in dependency-gated order (engine first — dwarfs UI leaks in MB — then streaming, then media/download), each with a cancel/close unit test, closing with full-gate green + device smoke.
**Delivers:** Minimal-diff fixes (EngineManager/LiteRTLmEngine close ordering, ChatViewModel `onCleared`/job null-outs, `callbackFlow awaitClose`, grounding scope discipline, single-ImageLoader assertion), unit gate + `assembleRelease` + R8 green, regenerated Baseline Profiles if renames occurred, Stop/retry/switch regression suite re-run, 16 KB + API-36 smoke folded into release-UAT deferral pattern if hardware unavailable.
**Addresses:** Zero-application-leak pass; leak-free sessions; model-memory discipline; grounding cancellation hygiene; Coil cache discipline.
**Avoids:** Pitfalls 4–7 (engine, streaming, media/download, boomerang); debt shortcuts (`largeHeap`, `System.gc()`, shared-resource closing).

### Phase Ordering Rationale

- **External-blocker first:** Phase 1 can only be resolved by evidence about a third-party AAR — no amount of app code substitutes, so it leads.
- **Contracts before churn:** Phase 2 settles platform behavior so Phase 4 fixes are verified against the real runtime, not a pre-audit surface (PITFALLS.md: "behavior audit first, code churn second"; FEATURES.md: compileSdk→targetSdk sequencing).
- **Baseline before fixes:** Phase 3 freezes the observable surface; Phase 4's per-site fixes are meaningless without a reproducible leak tour.
- **Owner-local grouping:** Phase 4 groups by owning component (engine → streaming → media/download), not by leak symptom, matching the codebase's single-owner discipline and keeping diffs review-sized.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 2 (API-36 audit):** Local Network Permission guidance is evolving (25Q4+ carve-outs pending) — re-check official behavior-change pages at plan time; exact compat-flag names and enforcement dates shift. `/gsd-plan-phase --research-phase` recommended if the plan touches LAN permission flows.
- **Phase 4 (leak fixes):** Each fix site needs codebase-grounded verification (actual `awaitClose` presence, `onCleared` body, `openSessions` ordering) — plan-phase should re-read the cited file/line targets since code may have drifted since 2026-09-30 research.

Phases with standard patterns (skip research-phase):
- **Phase 1 (16 KB verification):** Fully mechanical — official guide + script + zipalign + emulator image; no API research needed.
- **Phase 3 (LeakCanary instrumentation):** Two Gradle lines, auto-install, well-documented; standard patterns throughout.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Official 16 KB guide + LeakCanary/Coil changelogs + repo build files verified; MEDIUM only on exact Coil 3.6.3 API deltas (needs build + screen pass) and `auditDependencies` interplay (verify via `./gradlew check`) |
| Features | HIGH | 16 KB + API-36 behaviors from official Android docs; leak-audit practice from LeakCanary official docs; MEDIUM on Play deadline exact dates (shifted via extensions — use official page's Feb 1 2027) |
| Architecture | HIGH | All structural claims grep-verified against the live codebase; Android 16/16 KB claims from official docs fetched same day |
| Pitfalls | HIGH | 16 KB + API-36 from official docs; MEDIUM on leak patterns (community sources + codebase-shaped inference — verify per-leak against heap traces) |

**Overall confidence:** HIGH

### Gaps to Address

- **Transitive `.so` alignment status (LiteRT-LM 0.17.1, SQLCipher 4.5.4):** UNVERIFIED at research time — Phase 1 must run `check_elf_alignment.sh` on the actual release APK before any other commitment.
- **Coil 3.6.3 vs 3.5.x hold decision:** resolve in Phase 1/2 by attempting the bump; fall back to newest 3.5.x if build or OG-thumbnail screens fail.
- **`auditDependencies` gate on new debug deps:** LeakCanary pulls no banned modules per analysis, but confirm with `./gradlew check` after adding (MEDIUM confidence).
- **LNP enforcement timeline:** opt-in today; re-check official docs at Phase 2 plan time for any 25Q4+ enforcement change affecting LAN endpoint UX.
- **Device-dependent verification:** 16 KB emulator + API-36 device + real-LAN-endpoint tests; record emulator-only gaps as release-UAT items per house precedent (v2.2–v2.4 deferrals).
- **Heap-dump scale:** multi-GB LLM-session heaps may exceed on-device Shark analysis — plan workstation `shark-cli` workflow from the start of Phase 3.

## Sources

### Primary (HIGH confidence)
- developer.android.com/guide/practices/page-sizes — 16 KB requirements, AGP ≥ 8.5.1, NDK r28 default-align, verification commands, Play deadline (updated 2026-09-16)
- developer.android.com/about/versions/16/behavior-changes-16 — edge-to-edge, predictive back, resizability, scheduleAtFixedRate, local-network permission opt-in
- developer.android.com/about/versions/16/behavior-changes-all — JobScheduler quotas, FGS-concurrent jobs, `STOP_REASON_TIMEOUT_ABANDONED`, 16 KB compat mode
- square.github.io/leakcanary/changelog + LeakCanary fundamentals docs — 2.14 latest stable, ObjectWatcher/heap-dump/categorization model
- Live codebase evidence — `app/build.gradle.kts`, `gradle/libs.versions.toml`, `AndroidManifest.xml`, EngineManager/LiteRTLmEngine/ChatViewModel/LmStudioHelper/NetworkModule/WarpedApplication/ChatScreen/WebPageFetcher (see ARCHITECTURE.md for file-level detail)

### Secondary (MEDIUM confidence)
- coil-kt.github.io/coil/changelog + coil GitHub README — 3.6.3 latest, 3.5.0 compile-SDK-36 entry (exact API deltas vs 3.4.0 unverified)
- Community migration guides (API 34/35→36, Halodoc Android 16 journey) — consistent with official docs, single-source
- Community leak-fix patterns (lifecycle-aware collection, SSE `use{}`/cancel, Coil singleton context) — consensus patterns, verify per-leak
- Android Developers Blog — 16 KB Play requirement + Studio tooling posts

### Tertiary (LOW confidence)
- WebSearch cross-checks (ProAndroidDev 16 KB guide, r/androiddev deadline thread, Medium Android-16 summaries) — directionally consistent; deadline dates vary by source age, defer to official page

---
*Research completed: 2026-09-30*
*Ready for roadmap: yes*
