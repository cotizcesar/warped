---
gsd_state_version: "1.0"
milestone: v2.1
milestone_name: Finish v2.0 Leftovers
current_phase: 48
status: completed
last_updated: "2026-09-28T04:22:13.291Z"
last_activity: 2026-09-27
last_activity_desc: Phase 48 complete
state_head: 5f13d38944bed67eeaddcac517aadede8ace2b69
progress:
  total_phases: 4
  completed_phases: 4
  total_plans: 10
  completed_plans: 10
  percent: 100
---

# Project State: Warped

**Last updated:** 2026-09-27
**Last activity:** 2026-09-27 — Phase 48 complete

## Project Reference

See: .planning/PROJECT.md (updated 2026-09-27 after v2.1 milestone start)

**Core value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.
**Current focus:** **v2.1 Finish v2.0 Leftovers** — close every v2.0 PARTIAL/carry-over (PERF-01→PERF-14, PERF-06→PERF-15, SKILLS-02/03→SKILLS-08/10, double-collect→RUNTIME-13, Call.cancel()→RUNTIME-14) plus full catalog refresh (LiteRT-LM 0.17.1), release hardening, and sub-1s cold start. No partials left.

## Current Position

Phase: 48
Plan: Not started
Status: All phases complete
Last activity: 2026-09-27 — Roadmap created

## Phase Structure (v2.1)

| Phase | Name | Requirements | Status | Depends On |
|-------|------|--------------|--------|------------|
| 45 | Foundation Refresh | DEPS-01..02, LRT-07, LRT-09 (4) | Not started | — |
| 46 | Runtime Hardening | RUNTIME-13..14 (2) | Not started | Phase 45 |
| 47 | Real Tool Execution | SKILLS-07..12, LRT-08, HARD-02 (8) | Not started | Phase 46 |
| 48 | Chat Perf + Startup + Release | PERF-14..16, HARD-01 (4) | Not started | Phase 47 |

**Total v2.1:** 4 phases, 18 requirements, 0 mapped-unmapped ✓

## Completed Milestones

- ✅ v1.0 MVP — 5 phases, 30 requirements
- ✅ v1.1 LiteRT-LM Integration — 5 phases, 26 requirements
- ✅ v1.2 GGUF Native Inference — 5 phases, 27 requirements
- ✅ v1.3 Remote Provider Endpoints & UX — 4 phases, 31 requirements
- ✅ v1.4 Onboarding Wizard — 3 phases, 25 requirements
- ✅ v1.5 Bug Hunt, Cleanup & Hardening — 5 phases, 26 requirements
- ✅ v1.6 Code Syntax Highlighting — 3 phases, 20 requirements
- ✅ v1.7 App Optimization & Smart Presets — 4 phases, 19 requirements
- ✅ v1.8 LiteRT Update, Bugfix Round & Recommended Models — 5 phases, 30 requirements
- ✅ v2.0 Gallery Convergence & Performance Overhaul — 5 phases (40–44), 53 requirements (48 MET, 5 PARTIAL + 2 carry-overs → all in v2.1)

**Total across all milestones (archived):** 44 phases, 287 requirements

## Performance Metrics

**Velocity:**

- Total plans completed: 46 (across 6 milestones)
- v1.6 plans: 8 plans across 3 phases
- Average duration: ~18 min

## Accumulated Context

### Decisions

- [v2.1]: Phase order = runtime → tools → perf (ARCHITECTURE position). Rationale: tool loop runs through `runInference` — building it atop the sentinel no-op bakes unstoppable-tool-call bugs. Perf-last simultaneously satisfies PITFALLS (UI split accommodates existing tool-turn states). PERF-14+15 atomic; runtime/skills explicitly sequenced, never parallelized.
- [v2.1]: LiteRT-LM target is **0.17.1** (latest stable 2026-09-16), superseding research SUMMARY's pinned 0.13.1. LRT-08 adopts 0.14–0.17 tool-calling fixes in the SKILLS-08 path.
- [v2.1]: Summarize stays a PromptTemplate skill (persona, not a function) — SKILLS-08 is 3 real `@Tool`s (Calculator, CurrentTime, JsonFormatter).
- [v2.1]: Zero new dependencies — all v2.1 work rides the refreshed catalog (RUNTIME-12 audit stays green).

### Todos

- [ ] Plan Phase 45 (`/gsd-plan-phase 45`) — catalog refresh + engine bump
- [ ] Phase 47 plan 1 must be "locate or rebuild Skills Lite surface" (no `*Skill*.kt` in tree vs v2.0 claims — verify first)
- [ ] Pixel 7 reference-device measurements (PERF-16 + PERF-12/13 CI gate) need hardware/CI at Phase 48

### Blockers / Concerns

- v2.0 carry-overs (7 items: 5 PARTIALs + SKILLS-02/03) — all mapped into v2.1 Phases 46–48, none left orphaned
- PERF-12/13 benchmark numbers stay CI-gated (Pixel 7 hardware required) — out of v2.1 scope, unchanged
- Model-family fragility in LiteRT tool calling (Qwen3/Gemma template bugs, version-sensitive) — mitigated via per-model gating + prompt-injection fallback (Phase 47)
- Milestone brief said "19 reqs" but REQUIREMENTS.md contains 18 — counted and verified 18/18 mapped, no orphan

## Session Continuity

- 2026-09-27: v2.1 roadmap created (Phases 45–48). Next action: user approves roadmap → `/gsd-plan-phase 45`.
