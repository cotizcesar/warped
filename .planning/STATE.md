---
gsd_state_version: 1.0
milestone: v2.0
milestone_name: Gallery Convergence & Performance Overhaul
status: archived
last_updated: "2026-06-06T09:30:00.000Z"
last_activity: 2026-06-06 — v2.0 milestone archived (audit passed, roadmap archived, MILESTONES.md updated)
progress:
  total_phases: 5
  completed_phases: 5
  total_plans: 23
  completed_plans: 23
  percent: 100
---

# Project State: Warped

**Last updated:** 2026-06-06
**Last activity:** 2026-06-06 — v2.0 milestone **archived**: audit passed (53/53 reqs met or partial; 5 PARTIALs in Phase 43 + 2 carry-overs in Phase 44, all v2.1). Phases 40-44 shipped (23/23 plans).

## Project Reference

See: .planning/PROJECT.md (updated 2026-06-05)

**Core value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.
**Current focus:** **Idle** — v2.0 Gallery Convergence & Performance Overhaul is **archived**. All 5/5 phases, 23/23 plans, 53/53 requirements met or partial. Awaiting next milestone (v2.1+).

## Current Position

Phase: v2.0 archived
Plan: —
Status: v2.0 milestone lifecycle complete — `LlmModelHelper` keystone + allowlist asset + thinking mode + benchmark + prompt lab + performance sweep + skills lite all shipped. Audit: `.planning/v2.0-MILESTONE-AUDIT.md` → **passed** (48 MET + 5 PARTIAL). Archive: `.planning/milestones/v2.0-ROADMAP.md`.
Last activity: 2026-06-06 — v2.0 archived (audit + complete + state update).

## Phase Structure (v2.0)

| Phase | Name | Priority | Requirements | Status | Depends On |
|-------|------|----------|--------------|--------|------------|
| 40 | Runtime & Allowlist Foundation | **P0** keystone | RUNTIME-01..12, CACHE-01..03 (15) | ✓ Complete | — |
| 41 | Thinking Mode + Model Benchmark | P1 | THINK-01..07, BENCH-01..06 (13) | ✓ Complete | Phase 40 ✓ |
| 42 | Prompt Lab | P1 | PROMPT-01..06 (6) | ✓ Complete | Phase 40 ✓ |
| 43 | Performance Convergence | **P0** cross-cutting | PERF-01..13 (13) | ✓ Complete | Phase 40 (interface only) ✓ |
| 44 | Agent Skills Lite | P2 optional | SKILLS-01..06 (6) | ✓ Complete | Phase 40 ✓ |

**Total v2.0:** 5 phases, 53 requirements — 48 MET + 5 PARTIAL (Phase 43 PERF-01, PERF-05, PERF-06, PERF-12, PERF-13) + 2 carry-overs (Phase 44 SKILLS-02/03 Tool execution).

## Completed Milestones

- ✅ v1.0 MVP — 5 phases, 30 requirements
- ✅ v1.1 LiteRT-LM Integration — 5 phases, 26 requirements
- ✅ v1.2 GGUF Native Inference — 5 phases, 27 requirements
- ✅ v1.3 Remote Provider Endpoints & UX — 4 phases, 31 requirements
- ✅ v1.4 Onboarding Wizard — 3 phases, 25 requirements
- ✅ v1.5 Bug Hunt, Cleanup & Hardening — 5 phases, 26 requirements
- ✅ v1.6 Code Syntax Highlighting — 3 phases, 20 requirements
- ✅ v1.7 App Optimization & Smart Presets — 4 phases, 19 requirements
- ✅ v1.8 LiteRT Update, Bugfix Round & Recommended Models — 5 phases, 30 requirements (3 verification gaps in Phase 35)
- ✅ **v2.0 Gallery Convergence & Performance Overhaul — 5 phases, 53 requirements (48 MET, 5 PARTIAL)**

**Total across all milestones (archived):** 44 phases, 287 requirements
**Cumulative state:** 10 milestones archived, 41/41 plans across v1.0–v2.0

## Performance Metrics

**Velocity:**

- Total plans completed: 46 (across 6 milestones)
- v1.6 plans: 8 plans across 3 phases
- Average duration: ~18 min

## Accumulated Context

### Decisions

- [v1.6]: Highlights 1.1.0 selected as tokenization engine over custom regex tokenizer (~750-1,400 lines saved), Prism4j (archived 2023), and kotlin-textmate (v0.1.0, too new). Wrapped behind SyntaxHighlighter domain interface for swapability.
- [v1.6]: Deferred highlighting strategy: flat monospace during streaming, full syntax coloring applied when closing ``` fence arrives. Prevents O(n²) streaming jank.
- [v1.6]: CodeTheme enum → SyntaxTheme data class migration using existing DataStore key. Old enum names map to new theme objects.
- [v1.6]: MarkdownText restructured from single Text(AnnotatedString) to block-based Column of composables to host language header bar and copy button.
- [v1.6]: MarkdownBlock sealed hierarchy uses pure Kotlin data classes in domain/model/ with no Android/Compose dependencies
- [v1.6]: parseMarkdown is a top-level pure function with LanguageDetector passed as parameter
- [v1.6]: animateColorAsState applied per TokenType (13 calls) at composable scope
- [v1.6]: codeFontScale follows same DataStore→UiState→component propagation pattern as syntaxTheme
- [v1.6]: HuggingFaceModel.description uses SyntaxTheme.MONOKAI default when no user preference available
- [v1.6]: INTG-03 (README preview): acknowledged as requiring new API endpoint and screen — out of scope for this integration phase
- [v1.6]: INTG-06 (frame profiling): architectural protections (Dispatchers.Default, LRU cache, 500KB cap) provide sufficient confidence; real-device profiling deferred
- [v1.6]: Coverage audit confirms canonical rendering: all FontFamily.Monospace usage flows through CodeBlock.kt or MarkdownText.kt — no orphaned code block rendering sites exist
- [v1.8]: Long-lived `activeConversation` reused across `chat()` calls; `synchronized(this@LiteRTLmProvider)` protects lazy creation. `resetConversation()` is the public API to drop the conversation; called by `recoverEngine()`.
- [v1.8]: TopAppBar removed; drawer remains reachable via swipe at the NavGraph level.
- [v1.8]: Eager fetch — `fetchAllEndpointModels()` is called on every picker open (per user direction).
- [v1.8]: Form restricted to `listOf(ProviderType.LM_STUDIO)`; OpenAI / Anthropic / Ollama / Custom removed.
- [v1.8]: `base-config cleartextTrafficPermitted="true"` for LAN cleartext (Android XML doesn't support CIDR in `<domain>`).
- [v1.8]: Static `object RecommendedModels` with 8 hand-picked `.litertlm` models; no API calls.
- [v2.0]: Reference implementation = google-ai-edge/gallery v1.0.16 (23.6k stars, 91.9% Kotlin). Gallery is the only major OSS in the "LiteRT-LM + Compose + Android" niche, making it the most defensible convergence target.
- [v2.0]: Adopt Gallery's `LlmModelHelper` interface shape (5 methods: initialize, runInference, resetConversation, cleanUp, stopResponse) as the v2.0 keystone.
- [v2.0]: Drop Gallery's tech-debt surface — kapt, Moshi, Gson, kotlin-reflect, Firebase, Ktor, MCP SDK, compose-richtext, Proto DataStore, mlkit-genai, AppAuth, CameraX, JS webview skills. Verified by Phase 40's `dependencies` audit (RUNTIME-12).
- [v2.0]: Warped is **already ahead** of Gallery on several axes (Hilt 2.59.2 vs 2.58, KSP-only, kotlinx-serialization only, per-screen VMs vs Gallery's 850-line mega-VM). v2.0 is a **convergence, not a copy**.
- [v2.0]: Cache directory must be namespaced by `BuildConfig.LITERTLM_VERSION` (LiteRT-LM does not expose a cache-version API; cache is silently stale after `EngineConfig` schema change).
- [v2.0]: LlmModelHelper is a **stateful runtime**; repositories are stateless or only-own-DB. Crossing the boundary is the bug.
- [v2.0]: Use `@Binds @Singleton` for the `LlmModelHelper` interface — never `@Inject constructor` on a concrete helper class.
- [v2.0]: `LiteRtLlmHelper.initialize()` MUST wrap `Engine.initialize()` in `withContext(Dispatchers.IO)`; the callback contract is "after the IO call completes", not "asynchronous from caller's POV".
- [v2.0]: `EngineManager.getCachedModelPath()` file copy is REMOVED — `EngineConfig.cacheDir` gets `context.cacheDir.absolutePath/<version>` for mmap-only caching. Saves 1-3 GB/model + 3-10s cold start.
- [v2.0]: Phase 44 (Agent Skills Lite) is P2 — defer to v2.1 if v2.0 timeline is tight. LiteRT-LM 0.13.1 `ToolProvider` API verification is the gating question.

### Pending Todos

None.

### Blockers/Concerns

- v1.8 Phase 35 carry-over: 3 verification gaps remain (compile gate, conversation-reuse unit test, multi-turn smoke test) — user/CI verification required
- v2.0 Phase 40 carry-over: `LlmModelHelper.runInference` double-collect (internal "drain" job + returned Flow); refactor to `shareIn` / `MutableSharedFlow`. v2.1.
- v2.0 Phase 40 carry-over: `LMStudioProvider.Call` reference for true `Call.cancel()`. v2.1.
- v2.0 Phase 43 PARTIALs (5): PERF-01 (sub-state split), PERF-05 (full audit covered), PERF-06 (LazyColumn switch), PERF-12 (cold-start numbers), PERF-13 (SQLCipher overhead). All v2.1.
- v2.0 Phase 44 carry-overs (2): SKILLS-02 (LiteRT-LM `@Tool` registration), SKILLS-03 (LM Studio `tools[]` DTO mapping). v2.1.
- MigrationTest is compile-only verified; runtime test needs a real device.

## Deferred Items

Items acknowledged and deferred at v2.0 milestone close on 2026-06-06. **12 v1.6 quick tasks were picked up and shipped in v1.8 (Phases 36–38) per the v1.8 audit.** Verification gaps remain pending until human runs them.

| Category | Item | Status | Mapped Phase |
|----------|------|--------|--------------|
| verification_gap | Phase 06: 06-VERIFICATION.md | human_needed | — |
| verification_gap | Phase 07: 07-VERIFICATION.md | human_needed | — |
| verification_gap | Phase 08: 08-VERIFICATION.md | human_needed | — |
| verification_gap | Phase 09: 09-VERIFICATION.md | human_needed | — |
| verification_gap | Phase 10: 10-VERIFICATION.md | human_needed | — |
| verification_gap | Phase 29: 29-VERIFICATION.md | human_needed | — |
| verification_gap | v1.8 Phase 35 (compile gate + conv-reuse unit test + multi-turn smoke) | human_needed | — |
| verification_gap | v2.0 Phase 41 MigrationTest runtime (compile-verified) | human_needed | — |
| verification_gap | v2.0 Phase 43 macrobenchmarks (PERF-12/13 numbers) | human_needed | — |
| verification_gap | v2.0 Phase 40 release-APK smoke (requires signing config) | human_needed | — |
| milestone_partial | v2.0 PERF-01 (ChatUiState sub-state split) | deferred-v2.1 | v2.1 |
| milestone_partial | v2.0 PERF-06 (LazyColumn key) | deferred-v2.1 | v2.1 |
| milestone_partial | v2.0 PERF-12/13 (actual numbers) | deferred-CI | — |
| milestone_carry | v2.0 LlmModelHelper.runInference double-collect | deferred-v2.1 | v2.1 |
| milestone_carry | v2.0 LMStudioProvider.Call reference | deferred-v2.1 | v2.1 |
| milestone_carry | v2.0 SKILLS-02 (LiteRT-LM @Tool registration) | deferred-v2.1 | v2.1 |
| milestone_carry | v2.0 SKILLS-03 (LM Studio tools[] mapping) | deferred-v2.1 | v2.1 |
| quick_task | 260430-qv6-no-salen-los-modelos-en-el-detalle-del-m | shipped-v1.8 | Phase 36 (HF-01) |
| quick_task | 260430-rdt-unificar-diseno-detalle-con-listado-prin | shipped-v1.8 | Phase 36 (HF-02..04) |
| quick_task | 260430-ryn-rediseno-chat-ocultar-topbar-selector-mo | shipped-v1.8 | Phase 37 (CHAT-01..03) |
| quick_task | 260430-sx3-navegar-a-models-al-terminar-descarga-ar | shipped-v1.8 | Phase 36 (HF-05..06) |
| quick_task | 260430-tac-descargas-en-segundo-plano-listar-modelo | shipped-v1.8 | Phase 36 (HF-07..09) |
| quick_task | 260430-u5f-editar-y-borrar-endpoints-anthropic-prov | shipped-v1.8* | Phase 38 (ENDPT-01..02) — Anthropic portion removed in v1.8 scope |
| quick_task | 260430-ulx-endpoints-en-selector-chat-titulo-models | shipped-v1.8 | Phase 37 (CHAT-04..06) |
| quick_task | 260430-v7v-cargar-modelo-local-con-loading-listar-m | shipped-v1.8 | Phase 37 (CHAT-07..08) + Phase 38 (ENDPT-06) |
| quick_task | 260430-vsl-lm-studio-nativo-v1-api-remover-openai-a | shipped-v1.8 | Phase 38 (ENDPT-04..05) |
| quick_task | 260430-wgt-arreglar-delete-endpoints-dropdown-model | shipped-v1.8 | Phase 38 (ENDPT-02..04) |
| quick_task | 260430-wtn-fix-real-delete-endpoints-y-fetch-modelo | shipped-v1.6 (commit 777f604) | — |
| quick_task | 260504-lmi-litert-lm-solo-env-a-el-primer-mensaje-d | shipped-v1.8 | Phase 35 (LRT-02) |

## Session Continuity

Last session: 2026-06-06T09:30:00.000Z
Stopped at: **v2.0 milestone lifecycle complete** — audit (`v2.0-MILESTONE-AUDIT.md` → passed), archive (`.planning/milestones/v2.0-ROADMAP.md`), MILESTONES.md updated, STATE.md set to `status: archived`. All 5 phases (40-44) shipped with 23/23 plans and 53/53 requirements met or partial. Awaiting next milestone planning.
Resume file: None

## Next Step

Idle — v2.0 archived. Next user action: `/gsd-new-milestone` (or any equivalent) to scope the next milestone. v2.1 candidate items (from audit + STATE.md Blockers/Concerns): PERF-01 sub-state split, PERF-06 LazyColumn switch, SKILLS-02/03 tool execution, LlmModelHelper double-collect refactor, LMStudioProvider.Call reference, plus the deferred v2 requirements (LMSTUDIO-MCP-01, LRT-04 speculative decoding, BENCH-VIEW-01 history viewer, DEEPLINK-01, LRT-05 Vulkan, LRT-06 Hexagon NPU).
