---
gsd_state_version: "1.0"
milestone: v2.3
milestone_name: Web Grounding v2
current_phase: 53
current_phase_name: Sources Preview + Per-Chat Toggle
status: planning
stopped_at: Phase 52 complete, ready to plan Phase 53
last_updated: "2026-09-28T16:21:44.460Z"
last_activity: 2026-09-28
last_activity_desc: Phase 52 complete, transitioned to Phase 53
state_head: 29a04b7982b85754e01f436ef19cacc2f7611220
progress:
  total_phases: 3
  completed_phases: 1
  total_plans: 2
  completed_plans: 2
  percent: 17
---

# Project State: Warped

**Last updated:** 2026-09-28
**Last activity:** 2026-09-28 — Phase 52 complete, transitioned to Phase 53

## Project Reference

See: .planning/PROJECT.md (updated 2026-09-28 after v2.2 milestone close)

**Core value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.
**Current focus:** v2.3 Web Grounding v2 — Phase 52 Multi-URL Fetch Foundation (ready to plan)

## Current Position

Phase: 53 of 54 (Sources Preview + Per-Chat Toggle)
Plan: Not started
Status: Ready to plan
Last activity: 2026-09-28 — Roadmap created

Progress: [██░░░░░░░░] 17%

## Phase Structure (v2.3 — PLANNED)

| Phase | Name | Requirements | Status | Depends On |
|-------|------|--------------|--------|------------|
| 52 | Multi-URL Fetch Foundation | FETCH-01..03, EXTRACT-01..02 (5) | Not started | Phase 51 |
| 53 | Sources Preview + Per-Chat Toggle | SRC-01..03, TOGGLE-01..03 (6) | Not started | Phase 52 |
| 54 | Offline Retry | RETRY-01 (1) | Not started | Phase 53 |

**Total v2.3:** 3 phases, 12 requirements mapped (12/12 ✓). Coarse granularity.

## Performance Metrics

**Velocity:**

- Total plans completed: 51 (across 7 milestones)
- v2.2 plans: 5 plans across 3 phases (single day, 2026-09-28)
- Net deletion milestone: +1378 / -4721 lines across 87 files

## Accumulated Context

### Decisions

- [v2.3]: 3 phases per research — fetch+budget+Jsoup foundation first (budget/adversarial baseline depends on extraction density), preview+toggle second (one Room migration v15), retry last (orchestrates all three). Coarse granularity.
- [v2.3]: Message-scoped foreground retry first; WorkManager only as explicit opt-in (FEATURES+PITFALLS over STACK default).
- [v2.2]: Zero new dependencies — v2.3 adds exactly one: Jsoup 1.23.2 (parse-only, never `Jsoup.connect()`) + desugar NIO build config.
- [v2.1]: LiteRT-LM target 0.17.1; Summarize stays PromptTemplate (persona, not function).
- [Phase ?]: budget threaded into fetch() (single truncation point) instead of orchestrator-side re-truncation
- [Phase ?]: coroutineScope (not supervisorScope) preserves the single-cancel-path contract
- [Phase ?]: ephemeral modelOnlySourceCount carries the M>1 plural signal, no Room change

### Pending Todos

None yet.

### Blockers/Concerns

- Pixel 7 reference-device numbers (PERF-16 + PERF-12/13) stay CI-gated — unchanged, carried forward
- Release-UAT device smokes (DEL-06, WEB-05, WEB-06, THEME-01) must run on hardware before release
- Phase 52 exit gates: multi-page adversarial suite + 5×max-size budget assertion (later extractor changes must re-pass)
- Budget numbers per model window are LOW-confidence estimates — validate on device in Phase 52

## Deferred Items

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| Benchmarks | Pixel 7 reference numbers (PERF-16 + PERF-12/13) | CI-gated | v2.1 close |
| Device smoke | DEL-06 release smoke (launch → allowlisted model → local + remote turn → legacy TOOL chat) | Accepted, release UAT | v2.2 close |
| Device smoke | WEB-05 banner visual (offline vs failure copy) | Accepted, release UAT | v2.2 close |
| Device smoke | WEB-06 chip/Fuentes/E2E paste-URL flow | Accepted, release UAT | v2.2 close |
| Device smoke | THEME-01 per-preset visual (light + dark) | Accepted, release UAT | v2.2 close |
| Tech debt | Orphaned Keystore `huggingface_token` entry on upgrades (harmless, never read) | Accepted | v2.2 close |
| Coverage | Nyquist VALIDATION.md missing for phases 49/50/51 (`/gsd-validate-phase 49\|50\|51`) | TODO, not a compliance failure | v2.2 close |
| Phase 52 P02 | ~35 min | 3 tasks | 11 files |

## Deferred Verification

None — v2.2 phases shipped with accepted deferrals recorded above. Next milestone starts clean.

## Session Continuity

**Resume file:** —

Last session: 2026-09-28T16:06:24.315Z
Stopped at: Phase 52 complete, ready to plan Phase 53
Resume: `/gsd-plan-phase 52`
