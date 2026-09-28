---
gsd_state_version: "1.0"
milestone: v2.3
milestone_name: Web Grounding v2
current_phase: 54
current_phase_name: Offline Retry
status: complete
stopped_at: v2.3 milestone complete (audit gaps_found accepted, ready to archive)
last_updated: "2026-09-28T18:20:00Z"
last_activity: 2026-09-28
last_activity_desc: v2.3 milestone complete — all 3 phases verified and transitioned
state_head: 0c6b68ca25a30b2ca4556301767a2fa1e8ddb943
progress:
  total_phases: 3
  completed_phases: 3
  total_plans: 8
  completed_plans: 8
  percent: 100
---

# Project State: Warped

**Last updated:** 2026-09-28
**Last activity:** 2026-09-28 — v2.3 milestone complete (Phases 52–54, 12/12 verified, audit gaps accepted)

## Project Reference

See: .planning/PROJECT.md (updated 2026-09-28 after v2.3 milestone close)

**Core value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.
**Current focus:** v2.3 shipped — next milestone via `/gsd-new-milestone`

## Current Position

Milestone: v2.3 Web Grounding v2 — COMPLETE ✅
Status: Archived, ready for next milestone
Last activity: 2026-09-28 — audit (gaps_found, accepted) → complete → cleanup

Progress: [██████████] 100%

## Phase Structure (v2.3 — SHIPPED)

| Phase | Name | Requirements | Status | Depends On |
|-------|------|--------------|--------|------------|
| 52 | Multi-URL Fetch Foundation | FETCH-01..03, EXTRACT-01..02 (5) | Complete (5/5 verified) | Phase 51 |
| 53 | Sources Preview + Per-Chat Toggle | SRC-01..03, TOGGLE-01..03 (6) | Complete (6/6 verified) | Phase 52 |
| 54 | Offline Retry | RETRY-01 (1) | Complete (3/3 verified) | Phase 53 |

**Total v2.3:** 3 phases, 8 plans, 12 requirements verified (12/12 ✓). 289/289 unit green. SECURED all phases.

## Performance Metrics

**Velocity:**

- Total plans completed: 59 (51 entering v2.3 + 8 in v2.3: 2 + 4 + 2, single day 2026-09-28)
- v2.3 plans: 8 plans across 3 phases (single day, 2026-09-28)
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

## Quick Tasks Completed

| Slug | Date | Status | Notes |
|------|------|--------|-------|
| 20260928-chat-scroll-follow-fix | 2026-09-28 | Complete ✓ | End-pin follow + Latest pill fix (single root cause: item-top pinning); 300/300 unit green; on-device scroll confirmation pending |
| 20260928-remove-sin-web | 2026-09-28 | Complete ✓ | Sin web chip + skipOnce plumbing removed; SYSTEM_PROMPT always-on when grounding enabled; suite green |
| 20260928-gemma-caps-thinking-drawer | 2026-09-28 | Complete ✓ | E2B flags (thinking/vision/audio) per Google docs; Pensando… row; full-width drawer; Help tools text fixed; 291 green; on-device confirmation pending |
| 20260928-catalog-repo-url-fix | 2026-09-28 | Complete ✓ | Explicit repo field (fixes 404 on all catalog downloads) + E4B entry; 297 green; 3n modelFile/size swap deferred |
| 20260928-catalog-card-redesign | 2026-09-28 | Complete ✓ | Dense card (title + download icon, feature icons + size, tap expands RAM + uso); 7 download states preserved; visual check on-device pending |
| 20260928-catalog-downloaded-spacing | 2026-09-28 | Complete ✓ | On-device downloaded check (disco, no solo sesión) + espaciado iconos/título a la mitad; 309 green |
| 20260928-kill-purple-theme | 2026-09-28 | Complete ✓ | Morado eliminado (5 puntos → neutro 2B2B29 + coral, visión a azul claro); grep gate limpio; 309 green |
| 20260928-card-title-top-spacing | 2026-09-28 | Complete ✓ | Header overlay (título define altura, acciones superpuestas): misma separación en todos lados; confirmación visual pendiente |
| 20260928-catalog-order | 2026-09-28 | Complete ✓ | Orden 4-E2B → 4-E4B → 3n-E2B → 3n-E4B con test que lo fija; 310 green |
| 20260928-catalog-3n-litertlm-swap | 2026-09-28 | Complete ✓ | 3n a .litertlm + tamaños reales (3.41/4.58 GiB); 311 green; E2E en dispositivo pendiente |
| 20260928-active-cluster-overlap | 2026-09-28 | Complete ✓ | Cluster descarga en Row + padding dinámico + colores explícitos; 313 green; captura pendiente |
| 20260928-cluster-spacing-english-sweep | 2026-09-28 | Complete ✓ | Aire en cluster (8dp) + UI 100% inglés (values-es eliminado, sanitizer ajustado al delimitador); 313 green |

## Session Continuity

**Resume file:** —

Last session: 2026-09-28T16:06:24.315Z
Stopped at: Phase 54 complete, ready to plan Phase 53
Resume: `/gsd-plan-phase 52`
