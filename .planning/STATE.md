---
gsd_state_version: "1.0"
milestone: v3.0
milestone_name: Chat UX + Voice Dictation
current_phase: 64
current_phase_name: Drawer + Settings + Help + Funnel Polish
status: planning
stopped_at: Phase 63 complete, ready to plan Phase 64
last_updated: "2026-10-02T14:35:59.682Z"
last_activity: 2026-10-02
last_activity_desc: Phase 63 complete, transitioned to Phase 64
state_head: 2fed243e092e37f52297cdda4bcb17d45cfcaf5c
progress:
  total_phases: 4
  completed_phases: 1
  total_plans: 2
  completed_plans: 2
  percent: 25
---

# Project State: Warped

**Last updated:** 2026-09-30
**Last activity:** 2026-10-02 — Phase 63 complete, transitioned to Phase 64

## Project Reference

See: .planning/PROJECT.md (updated 2026-10-01, v2.5 shipped)

**Core value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.
**Current focus:** Phase 64 — Drawer + Settings + Help + Funnel Polish (Phase 63 shipped: DDG-only search, Tavily removed)

## Current Position

Phase: 64 — Drawer + Settings + Help + Funnel Polish
Plan: Not started
Status: Ready to plan
Last activity: 2026-10-02 — Phase 63 execution started

## Phase Structure (v3.0 — PLANNED)

| Phase | Name | Requirements | Status | Depends On |
|-------|------|--------------|--------|------------|
| 63 | Tavily Removal → DDG-only Search | SEARCH-01..04 (4) | Complete (2026-10-02, 2/2 plans, 7/7 verified, 895 tests green) | Phase 62 |
| 64 | Drawer + Settings + Help + Funnel Polish | DRAWER-01..04, SET-01/02, HELP-01, FUN-01..03 (10) | Not started | Phase 63 |
| 65 | Voice Dictation | VOICE-01..03 (3) | Not started | Phase 64 |
| 66 | Play In-App Review | RATE-01/02 (2) | Not started | Phase 65 |

**Total v3.0:** 4 phases, 19 requirements mapped (19/19 ✓). Coarse granularity.

## Performance Metrics

**Velocity:**

- v2.4: 9 plans across 4 phases (single day, 2026-09-29); 494/494 unit green
- v2.3: 8 plans across 3 phases (single day, 2026-09-28); 289/289 unit green
- v2.2: 5 plans across 3 phases; net −4721/+1378 lines across 87 files

## Accumulated Context

### Decisions

- [v3.0-63]: Tavily fully removed (3 files deleted, `SearchOutcome` rename file-wide for grep-clean, startup Keystore alias cleanup off-main-thread via `KeystoreManager.LEGACY_SEARCH_ALIAS`) — DDG single producer, budget/cancel preserved, legacy citations render via existing host-fallback, zero schema change
- [v3.0]: 4 phases per research (compressed 5→4: regression sweep folded into per-phase verification — it carries zero requirements and coarse granularity forbids standalone maintenance phases) — Tavily contract-move + deletion first, drawer/settings/help/funnel batch second (same composables), voice third (after ChatScreen churn), Play Review last (smallest, independent). Continues numbering at Phase 63 (v2.5 ended at 62).

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
| Coverage | Nyquist VALIDATION.md missing for v2.5 phases 59-62 (discovery only, not a compliance failure) | TODO | v2.5 close |

### v2.5 Closeout Acknowledgments (2026-10-01, override_closeout)

47 open artifacts acknowledged at milestone close (suppression lapses automatically if artifact state changes):

| Category | Item | Status | Milestone |
|----------|------|--------|-----------|
| uat_gaps | 62/62-RELEASE-UAT.md | 0 pending scenarios | v2.5 |
| verification_gaps | 59/59-VERIFICATION.md (gaps_found, G-59-01 closed by 62 back-closure) | acknowledged | v2.5 |
| verification_gaps | 51/51-VERIFICATION.md (archived v2.2, gaps_found) | acknowledged | v2.5 |
| quick_tasks | 20260928 backlog (16): active-cluster-overlap, add-gemma4-e4b, card-title-top-spacing, catalog-3n-litertlm-swap, catalog-card-redesign, catalog-downloaded-spacing, catalog-order, catalog-repo-url-fix, chat-scroll-follow-fix, cluster-spacing-english-sweep, honest-delete-unified-download, kill-purple-theme, remove-sin-web, source-delimiter-rename, vision-backend-gpu, webfetch-parity | idea backlog | v2.5 |
| quick_tasks | 20260929 backlog (14): agentic-rows-research-warnings, all-sources-sheet, always-search-image-grid, attachments-skip-search, card-description-line, citation-taps-youtube-oembed, ddg-default-sources-carousel, explicit-language-directive, image-history-carry, image-turn-routing-favicon, langmatch-i18n-paragraphs, loop-images-plumbing, og-thumb-opens-browser, search-og-enrichment | idea backlog | v2.5 |
| quick_tasks | 20260930 backlog (14): always-presearch-anchored, code-intent-gate, drawer-card-reuse, langdetect-library, language-sources-override, loading-flag-stuck, needs-web-gate, pending-sweep, reference-resolution-rule, source-card-density, spanish-word-detection, thinking-header-feelings, thinking-scroll-live-hairline, unified-turn-status | idea backlog | v2.5 |

## Deferred Verification

v2.5 all phases complete (59: 4/5 with G-59-01 closed by 62; 60/61/62 passed). Standing release-UAT deferrals recorded above.

## Quick Tasks Completed

| Date | Task | Result |
|------|------|--------|
| 2026-10-01 | `quick/20261001-play-warnings-fix` (Play warnings: edge-to-edge deprecated attrs, bitmap OOM sites → Coil/bounded decode, ndk debugSymbolLevel FULL; commit on beta, no push) | complete (native-symbols warning honestly unfixable: prebuilt .so stripped upstream) |
| 2026-10-01 | `quick/20261001-branch-restructure` (delete production, beta → main, main → Beta track, manual prod promotion) | complete, pushed; remote: main is default, beta/production deleted |
| 2026-10-01 | `quick/20261001-abi-filters` (32-bit exclusion via abiFilters arm64-v8a+x86_64; AAB verified 2-ABI-only + 16KB OK; commit on main, no push) | complete |
| 2026-10-01 | `quick/20261001-public-security` (read-only pre-public audit: leaked keystore pw, fork-PR runner RCE, unpinned release action, unused sensitive permissions) | findings fixed in public-hardening below |
| 2026-10-01 | `quick/20261001-public-hardening` (rotate keystore pw, pin actions+Dependabot, strip 4 permissions, delete dead AudioRecorder; pushed, CI proof) | complete (except Blocker 2, kept by user) |

See prior STATE history for v2.2–v2.4 quick-task log (archived at roadmap rewrite). 44 quick-task backlog items acknowledged at v2.5 close (see Deferred Items above).

## Session Continuity

**Resume file:** —

Last session: 2026-10-01
Stopped at: Phase 63 complete, ready to plan Phase 64
Resume: `/gsd-new-milestone` (after `/clear`)

## Operator Next Steps

- Start the next milestone with /gsd-new-milestone
