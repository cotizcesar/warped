# Phase 62: Fix Loop + Release Hardening - Context

**Gathered:** 2026-09-30
**Status:** Ready for planning (blocked on 61-02 baseline for owner-local scope)
**Mode:** Auto-generated (infrastructure phase — discuss skipped per autonomous-smart-discuss)

<domain>
## Phase Boundary

Zero-application-leak release: every Phase 61 baseline finding fixed at its
owner layer (native handles → EngineManager, chat Flows → ViewModels/repos,
grounding pipeline → per-send scope, Coil/OkHttp → scope discipline) with
regression tests, and every Play gate green on the release artifact (alignment,
assembleRelease + R8, 16 KB smoke, zero-leak pass, Play Console pre-launch).

</domain>

<decisions>
## Implementation Decisions

### the agent's Discretion (within hard constraints)
- Fix order follows 61 baseline severity, dependency-gated (native → VM →
  network → Compose per ROADMAP).
- Regression test per fix (unit where possible; tour-leg replay where not).
- Release gates re-run in full on the final artifact.

### Hard constraints (non-negotiable, from REQUIREMENTS/STATE)
- Version-bump-only for native deps (Phase 59 precedent); no `largeHeap` as a
  leak fix; no LeakCanary in release; no permanent compat-flag opt-outs.
- Shared OkHttp clients are never closed; no Activity-context singletons;
  image requests cancel on recycle; SSE streams close on Stop.
- Device-only verifications → explicit release-UAT, never silent passes.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `data/local/inference/EngineManager.kt` + `LiteRTLmEngine.kt` (LEAK-02 owners).
- `ui/chat/ChatViewModel.kt`, `data/repository/ChatRepositoryImpl.kt`
  (single-flight `runInference`, LEAK-03 owners).
- `data/grounding/` fan-out + Tavily + SSE accumulators (LEAK-04 owners).
- `WarpedApplication.newImageLoader` (Coil singleton), `di/NetworkModule.kt`
  (OkHttp clients), `data/local/download/ModelDownloadManager.kt` (observers),
  SQLCipher/Room via `di/DatabaseModule.kt` (LEAK-05 owners).
- `61-LEAK-BASELINE.md` (pending — 61-02) is the authoritative finding list;
  fix only what it names, at the layer it assigns.

### Established Patterns
- Pure-mapper + JVM test pattern (Phase 60 `DownloadStopReason` precedent);
  Timber (RedactingTree); atomic commits per fix; 894-test baseline (Phase 61-01).
- Physical Pixel 8 (USB `37141FDJH0065Y`, debug + LeakCanary) is the fix-
  verification device; `gemma-4-E2B-it` (≈2.6 GB) downloading 2026-09-30 for
  model-leg replay.

### Integration Points
- Release pipeline gates from Phase 59 (`verify16KbAlignment`, CI/release
  workflow steps) must re-run green on the final artifact (REL-01).

</code>

<specifics>
## Specific Ideas

None — scope derives entirely from the 61 baseline when it lands.

</specifics>

<deferred>
## Deferred Ideas

None.

</deferred>

### Blocked on

- `61-LEAK-BASELINE.md` (61-02 device tour, in progress — model downloading).
  Planning 62 before the baseline lands risks fixing non-findings; draft plans
  may be sketched but must be re-verified goal-backward against the baseline.
