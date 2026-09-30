---
gsd_state_version: "1.0"
milestone: v2.4
milestone_name: Agentic Web
current_phase: 58
current_phase_name: OpenGraph Thumbnails
status: complete
stopped_at: v2.4 milestone complete (audit gaps_found accepted, archived)
last_updated: "2026-09-29T17:31:54.121Z"
last_activity: 2026-09-29
last_activity_desc: v2.4 milestone complete — all 4 phases verified and transitioned
state_head: 095cb0a3098cc57ad7dd1b7ea13ffe5f7d122120
progress:
  total_phases: 4
  completed_phases: 4
  total_plans: 9
  completed_plans: 9
  percent: 100
---

# Project State: Warped

**Last updated:** 2026-09-28
**Last activity:** 2026-09-29 — v2.4 milestone complete (Phases 55–58, 10/10 verified, audit gaps accepted)

## Project Reference

See: .planning/PROJECT.md (updated 2026-09-29 after v2.4 milestone close)

**Core value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.
**Current focus:** v2.4 shipped — next milestone scope TBD

## Current Position

Milestone: v2.4 Agentic Web — COMPLETE ✅
Status: Archived, ready for next milestone
Last activity: 2026-09-29 — audit (gaps_found, accepted) → complete → cleanup

Progress: [██████████] 100%

## Phase Structure (v2.4 — SHIPPED)

| Phase | Name | Requirements | Status | Depends On |
|-------|------|--------------|--------|------------|
| 55 | Tavily Search Foundation | TAV-01..03 (3) | Complete (3/3 verified) | Phase 54 |
| 56 | Local Agentic Loop | AGENT-01, AGENT-02, AGENT-04 (3) | Complete (3/3 verified) | Phase 55 |
| 57 | Remote Agentic Loop | AGENT-03 (1) | Complete (9/9 verified) | Phase 56 |
| 58 | OpenGraph Thumbnails | OG-01..03 (3) | Complete (3/3 verified) | Phase 57 |

**Total v2.4:** 4 phases, 10 requirements mapped (10/10 ✓). Coarse granularity.

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
| 20260928-honest-delete-unified-download | 2026-09-28 | Complete ✓ | Borrado honesto (por ruta, con errores visibles) + descarga idéntica en catálogo y Models; 321 green; E2E en dispositivo pendiente |
| 20260928-vision-backend-gpu | 2026-09-28 | Complete ✓ | Visión a GPU probada en init + reintento por slot (main/vision/audio); 328 green; cargar E2B en dispositivo pendiente |
| 20260928-webfetch-parity | 2026-09-28 | Complete ✓ | Extracción markdown (headings/tablas/código/links), 256KB/30s, UA desktop; sanitizer anti-links maliciosos; 346 green |
| 20260928-source-delimiter-rename | 2026-09-28 | Complete ✓ | Bloques "Source [N]" sin etiqueta filtrable + escapes viejos conservados; suite verde |
| 20260929-og-thumb-opens-browser | 2026-09-29 | Complete ✓ | Thumb abre navegador directo (resto→sheet); tap test androidTest; unidad verde |
| 20260929-langmatch-i18n-paragraphs | 2026-09-29 | Complete ✓ | Regla idioma-usuario en prompts + EN/ES 459/459 + párrafos 8dp; 512 green |
| 20260929-always-search-image-grid | 2026-09-29 | Complete ✓ | Pre-búsqueda siempre (DDG gratis aun armada) + grid imágenes/modal/descarga; 576 green |
| 20260929-image-turn-routing-favicon | 2026-09-29 | Complete ✓ | Turnos-imagen directo a Tavily + fallback favicon S2; 599 green |
| 20260929-search-og-enrichment | 2026-09-29 | Complete ✓ | Títulos enhebrados + enrich OG (3s/64KB/max-5) + cards 2-col sin badges; 614 green |
| 20260929-card-description-line | 2026-09-29 | Complete ✓ | Línea descripción (og:desc, 2 líneas) en cards; 618 green |
| 20260929-all-sources-sheet | 2026-09-29 | Complete ✓ | Icono ver-todas + drawer lista completa; 9 tests |
| 20260929-citation-taps-youtube-oembed | 2026-09-29 | Complete ✓ | Citas [N] clicables → drawer individual + oEmbed YouTube; 651 green |
| 20260929-loop-images-plumbing | 2026-09-29 | Complete ✓ | includeImages en los 4 executors + images→mensaje (efímero); 656 green |
| 20260929-attachments-skip-search | 2026-09-29 | Complete ✓ | Sin pre-búsqueda ciega en turnos con imagen/audio (regresión v2.4); 665 green |
| 20260929-explicit-language-directive | 2026-09-29 | Complete ✓ | Detección ES + directiva explícita última línea (adiós regla probabilística); 677 green |
| 20260929-langmatch-i18n-paragraphs | 2026-09-29 | Complete ✓ | Regla idioma-usuario en prompts + EN/ES 459/459 + párrafos 8dp; 512 green |

## Session Continuity

**Resume file:** —

Last session: 2026-09-29T00:00:00Z
Stopped at: v2.4 complete; post-milestone quicks tracked above
Resume: none — define next scope
