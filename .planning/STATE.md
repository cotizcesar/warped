---
gsd_state_version: 1.0
milestone: v2.0
milestone_name: Gallery Convergence & Performance Overhaul
status: complete
last_updated: "2026-06-06T08:00:00.000Z"
last_activity: 2026-06-06
progress:
  total_phases: 5
  completed_phases: 5
  total_plans: 23
  completed_plans: 23
  percent: 100
---

# Project State: Warped

**Last updated:** 2026-06-06
**Last activity:** 2026-06-06 — v2.0 COMPLETE: Phases 40-44 shipped (23/23 plans, 53/53 requirements)

## Project Reference

See: .planning/PROJECT.md (updated 2026-06-05)

**Core value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.
**Current focus:** v2.0 Gallery Convergence & Performance Overhaul — **100% complete** (5/5 phases, 23/23 plans, 53/53 requirements). v2.0 milestone closed.

## Current Position

Phase: v2.0 complete
Plan: —
Status: All 5 v2.0 phases shipped (40 Runtime/Allowlist, 41 Thinking+Benchmark, 42 Prompt Lab, 43 Performance, 44 Skills Lite)
Last activity: 2026-06-06 — Phase 44 complete (Agent Skills Lite, 3 plans shipped: 44-01 domain/prefs/repo, 44-02 LlmModelHelper plumbing, 44-03 chat-input chips UI)

## Phase Structure (v2.0)

| Phase | Name | Priority | Requirements | Status | Depends On |
|-------|------|----------|--------------|--------|------------|
| 40 | Runtime & Allowlist Foundation | **P0** keystone | RUNTIME-01..12, CACHE-01..03 (15) | ✓ Complete | — |
| 41 | Thinking Mode + Model Benchmark | P1 | THINK-01..07, BENCH-01..06 (13) | ✓ Complete | Phase 40 ✓ |
| 42 | Prompt Lab | P1 | PROMPT-01..06 (6) | ✓ Complete | Phase 40 ✓ |
| 43 | Performance Convergence | **P0** cross-cutting | PERF-01..13 (13) | ✓ Complete | Phase 40 (interface only) ✓ |
| 44 | Agent Skills Lite | P2 optional | SKILLS-01..06 (6) | ✓ Complete | Phase 40 ✓ |

**Total v2.0:** 5 phases, 53 requirements — all met.

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

**Total across all milestones (completed):** 39 phases, 234 requirements
**v2.0 target:** 5 phases, 53 requirements → 44 phases, 287 requirements at completion

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

- Phase 29 human verification pending: 4 visual UI checks require device/emulator (theme dropdown, font scale slider, CodeBlock rendering, expand/collapse)
- JUnit Platform launcher classpath issue — pre-existing, not introduced by v1.6
- v1.8 Phase 35: 3 verification gaps remain (compile gate, conversation-reuse unit test, multi-turn smoke test) — user/CI verification required before v2.0 ships
- v2.0 Phase 40 has 3 research-flagged questions (ARCHITECTURE §Open Q #1, #3, #6) to resolve in `--research-phase` before planning the LlmModelHelper, EngineConfig cacheDir, and `@AutoMigration` schema design
- v2.0 Phase 41 has LM Studio `reasoning_content` JSON-path verification (LOW-MEDIUM confidence) and WorkManager `setForeground()` reliability on Chinese OEM ROMs (LOW confidence) as research flags
- v2.0 Phase 44 (P2) is gated on LiteRT-LM 0.13.1 `ToolProvider` Kotlin API verification (MEDIUM confidence) — if the API is unstable, defer to v2.1

## Deferred Items

Items acknowledged and deferred at milestone close on 2026-05-15. **All 12 quick tasks picked up in v1.8 roadmap** (Phases 35–38). Verification gaps remain pending until human runs them.

| Category | Item | Status | Mapped Phase |
|----------|------|--------|--------------|
| verification_gap | Phase 06: 06-VERIFICATION.md | human_needed | — |
| verification_gap | Phase 07: 07-VERIFICATION.md | human_needed | — |
| verification_gap | Phase 08: 08-VERIFICATION.md | human_needed | — |
| verification_gap | Phase 09: 09-VERIFICATION.md | human_needed | — |
| verification_gap | Phase 10: 10-VERIFICATION.md | human_needed | — |
| verification_gap | Phase 29: 29-VERIFICATION.md | human_needed | — |
| quick_task | 260430-qv6-no-salen-los-modelos-en-el-detalle-del-m | picked-up-v1.8 | Phase 36 (HF-01) |
| quick_task | 260430-rdt-unificar-diseno-detalle-con-listado-prin | picked-up-v1.8 | Phase 36 (HF-02..04) |
| quick_task | 260430-ryn-rediseno-chat-ocultar-topbar-selector-mo | picked-up-v1.8 | Phase 37 (CHAT-01..03) |
| quick_task | 260430-sx3-navegar-a-models-al-terminar-descarga-ar | picked-up-v1.8 | Phase 36 (HF-05..06) |
| quick_task | 260430-tac-descargas-en-segundo-plano-listar-modelo | picked-up-v1.8 | Phase 36 (HF-07..09) |
| quick_task | 260430-u5f-editar-y-borrar-endpoints-anthropic-prov | picked-up-v1.8* | Phase 38 (ENDPT-01..02) — Anthropic portion removed in v1.8 scope |
| quick_task | 260430-ulx-endpoints-en-selector-chat-titulo-models | picked-up-v1.8 | Phase 37 (CHAT-04..06) |
| quick_task | 260430-v7v-cargar-modelo-local-con-loading-listar-m | picked-up-v1.8 | Phase 37 (CHAT-07..08) + Phase 38 (ENDPT-06) |
| quick_task | 260430-vsl-lm-studio-nativo-v1-api-remover-openai-a | picked-up-v1.8 | Phase 38 (ENDPT-04..05) |
| quick_task | 260430-wgt-arreglar-delete-endpoints-dropdown-model | picked-up-v1.8 | Phase 38 (ENDPT-02..04) |
| quick_task | 260430-wtn-fix-real-delete-endpoints-y-fetch-modelo | shipped-v1.6 (commit 777f604) | — |
| quick_task | 260504-lmi-litert-lm-solo-env-a-el-primer-mensaje-d | picked-up-v1.8 | Phase 35 (LRT-02) |

## Session Continuity

Last session: 2026-06-06T04:00:00.000Z
Stopped at: Phase 40 complete (Runtime & Allowlist Foundation, 8 plans, RUNTIME-01..12 + CACHE-01..03). Verification written to `.planning/phases/40-runtime-allowlist-foundation/40-VERIFICATION.md`. Next: plan + execute Phase 41 (Thinking + Benchmark).
Resume file: None

## Next Step

Run `/gsd-plan-phase 41` to plan Thinking Mode + Model Benchmark. Phase 41 surfaces reasoning trace as a collapsible panel and ships an on-device benchmark screen for downloaded models.
