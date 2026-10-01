---
gsd_state_version: "1.0"
milestone: v2.5
milestone_name: Play Compliance + Leaks
status: planning
last_updated: "2026-09-30T00:00:00Z"
last_activity: 2026-09-30
progress:
  total_phases: 4
  completed_phases: 4
  total_plans: 8
  completed_plans: 8
  percent: 100
---

# Project State: Warped

**Last updated:** 2026-09-30
**Last activity:** 2026-09-30 — v2.5 roadmap created (Phases 59–62, 15/15 requirements mapped)

## Project Reference

See: .planning/PROJECT.md (updated 2026-09-30, v2.5 milestone started)

**Core value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.
**Current focus:** v2.5 Play Compliance + Leaks — 16 KB page-size support, API 36 target, memory-leak audit + fixes

## Current Position

Phase: — (v2.5 all phases complete — lifecycle: audit → complete → cleanup)
Plan: —
Status: Phase 62 closed 2026-10-01 (5/5 passed, G-59-01 closed on 16 KB turn)
Last activity: 2026-10-01 — v2.5 all 4 phases complete, lifecycle starting

## Phase Structure (v2.5 — PLANNED)

| Phase | Name | Requirements | Status | Depends On |
|-------|------|--------------|--------|------------|
| 59 | 16 KB Dependency Verification | PAGE-01..04 (4) | Complete (4/5, G-59-01 → release-UAT) | Phase 58 |
| 60 | API-36 Behavior Audit | API-01..05 (5) | Complete (5/5, 5 release-UAT follow-ups) | Phase 59 |
| 61 | LeakCanary Instrumentation + Guided Audit | LEAK-01 (1) | Complete (3/3, 6/6 legs clean, zero leaks) | Phase 60 |
| 62 | Fix Loop + Release Hardening | LEAK-02..05, REL-01 (5) | Complete (5/5, 932 tests, release hardened) | Phase 61 |

**Total v2.5:** 4 phases, 15 requirements mapped (15/15 ✓). Coarse granularity.

## Performance Metrics

**Velocity:**

- v2.4: 9 plans across 4 phases (single day, 2026-09-29); 494/494 unit green
- v2.3: 8 plans across 3 phases (single day, 2026-09-28); 289/289 unit green
- v2.2: 5 plans across 3 phases; net −4721/+1378 lines across 87 files

## Accumulated Context

### Decisions

- [v2.5]: 4 phases per research — 16 KB verification first (gates Play submission, zero code), API-36 audit second (contracts before churn), LeakCanary baseline third (needs runnable build), fix loop + hardening last (owner-local, dependency-gated order). Coarse granularity.
- [v2.5]: Fix by version bump only for misaligned `.so` — never hand-patched `.so`, never linker-flag hacks, never `pageSizeCompat`.
- [v2.5]: LeakCanary `debugImplementation` only — zero release footprint.
- [v2.3]: 3 phases per research — fetch+budget+Jsoup foundation first, preview+toggle second (one Room migration v15), retry last. Coarse granularity.
- [v2.3]: Message-scoped foreground retry first; WorkManager only as explicit opt-in.
- [v2.2]: Zero new dependencies — v2.3 adds exactly one: Jsoup 1.23.2 (parse-only, never `Jsoup.connect()`).
- [v2.1]: LiteRT-LM target 0.17.1; Summarize stays PromptTemplate (persona, not function).

### Pending Todos

None yet.

### Blockers/Concerns

- Transitive `.so` alignment status (LiteRT-LM 0.17.1, SQLCipher 4.5.4) UNVERIFIED — Phase 59 must run `check_elf_alignment.sh` on the actual release APK first; an unaligned AAR blocks Play submission from outside the repo
- Pixel 7 reference-device numbers (PERF-16 + PERF-12/13) stay CI-gated — unchanged, carried forward
- Release-UAT device smokes (DEL-06, WEB-05, WEB-06, THEME-01, v2.3 MIG-01/WEB-07/WEB-08, v2.4 WEB-09/10/11) must run on hardware before release
- Coil 3.6.3 vs 3.5.x hold decision resolves in Phase 59/60 by attempting the bump
- Device-dependent verification (16 KB emulator, API-36 device, real-LAN endpoints) — record emulator-only gaps as release-UAT per house precedent

## Deferred Items

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| Release-UAT | 61 Leg 1B model-B switch (needs second complete model) | Accepted, release UAT | Phase 61 close |
| Release-UAT | 60 follow-ups: 3-button nav visuals; light-theme visuals (blocked, no toggle); Play Console target warnings; foldable posture; quota-pressure platform stop | Accepted, release UAT | Phase 60 close |
| Release-UAT | G-59-01 16KB chat turn (healthy 16 KB system + smallest-model download + local chat turn, zero native failures) | Accepted, release UAT | Phase 59 close |
| Product idea | Auto-load model after download (chat blocked until manual activation in Models & Endpoints) | Candidate v2.6 | Phase 61 tour |
| Benchmarks | Pixel 7 reference numbers (PERF-16 + PERF-12/13) | CI-gated | v2.1 close |
| Device smoke | DEL-06 release smoke (launch → allowlisted model → local + remote turn → legacy TOOL chat) | Accepted, release UAT | v2.2 close |
| Device smoke | WEB-05 banner visual (offline vs failure copy) | Accepted, release UAT | v2.2 close |
| Device smoke | WEB-06 chip/Fuentes/E2E paste-URL flow | Accepted, release UAT | v2.2 close |
| Device smoke | THEME-01 per-preset visual (light + dark) | Accepted, release UAT | v2.2 close |
| Device smoke | v2.3 MIG-01 / WEB-07 / WEB-08 | Accepted, release UAT | v2.3 close |
| Device smoke | v2.4 WEB-09 / WEB-10 / WEB-11 | Accepted, release UAT | v2.4 close |
| Tech debt | Orphaned Keystore `huggingface_token` entry on upgrades (harmless, never read) | Accepted | v2.2 close |
| Coverage | Nyquist VALIDATION.md missing for phases 49/50/51 | TODO, not a compliance failure | v2.2 close |

## Deferred Verification

None new — v2.5 phases not yet executed. Standing release-UAT deferrals recorded above.

## Quick Tasks Completed

See prior STATE history for v2.2–v2.4 quick-task log (archived at roadmap rewrite).

## Session Continuity

**Resume file:** —

Last session: 2026-09-30
Stopped at: v2.5 roadmap created (Phases 59–62)
Resume: `/gsd-plan-phase 59`
