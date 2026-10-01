# Phase 61: LeakCanary Instrumentation + Guided Audit - Context

**Gathered:** 2026-09-30
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase — discuss skipped per autonomous-smart-discuss)

<domain>
## Phase Boundary

Team gets a reproducible leak baseline: LeakCanary 2.14 harness installed as
`debugImplementation` only (zero release footprint), plus a scripted leak tour
covering model load/switch/unload, streaming chat + Stop, 5-URL grounding +
cancel, offline→retry, OG thumbnail scroll, rotation/process death. Every finding
triaged to its owning layer (native → VM → network → Compose) with heap evidence,
ready for owner-local fixes in Phase 62.

</domain>

<decisions>
## Implementation Decisions

### the agent's Discretion
All implementation choices are at the agent's discretion — pure infrastructure
phase. Constraints: LeakCanary `debugImplementation` only (never release —
heap dumps freeze the app, leak PII to disk, bloat the APK per REQUIREMENTS);
tour must be scripted and reproducible (steps a human or a future agent can
replay exactly); triage assigns each finding to exactly one owning layer.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `app/build.gradle.kts:169-170` — existing `debugImplementation` precedent
  (compose tooling); add LeakCanary the same way.
- No LeakCanary references anywhere in the repo (grep 2026-09-30) — greenfield install.
- `gradle/libs.versions.toml` — add `leakcanary = "2.14"` + library entry.
- Leak-prone surfaces (tour targets): `EngineManager` + `LiteRTLmEngine`
  (native handles), `ChatViewModel`/`ChatRepositoryImpl` (single-flight
  `runInference`, collectors), `data/grounding/` (5-fan-out, Tavily, SSE
  accumulators), `ModelDownloadManager` (WorkManager + observers), Coil
  singleton in `WarpedApplication`, OkHttp clients in `di/NetworkModule`.
- Physical Pixel 8 on USB (`ANDROID_SERIAL=37141FDJH0065Y`) available for the
  tour; emulator-5554 crash-loops (exclude); release builds sign with
  `app/keystore/warped-release.jks`.

### Established Patterns
- Version catalog for all dependencies; Timber (RedactingTree) for logging;
  894 unit tests green (Phase 60 baseline).
- House precedent: device-only results recorded explicitly, never silent passes.

### Integration Points
- Debug builds only (`debugImplementation` — verify release APK contains zero
  LeakCanary classes, e.g. via APK grep/dex check).
- Tour reuses normal app flows (no test hooks in release code; debug-only
  helpers acceptable if clearly marked and release-excluded).

</code>

<specifics>
## Specific Ideas

No specific requirements — infrastructure phase. LeakCanary 2.14 pinned by
REQUIREMENTS (LEAK-01).

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope (discuss skipped).

</deferred>
