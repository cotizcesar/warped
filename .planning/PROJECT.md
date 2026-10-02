# Warped

## What This Is

An Android application equivalent to LM Studio for mobile, enabling users to run large language models (LLMs) locally via LiteRT-LM and connect to remote LLM providers. The app ships a static curated model catalog with direct downloads, executes models on-device, and connects to OpenAI-compatible APIs, Anthropic, Ollama, LM Studio, and custom servers. Pasted URLs ground answers via a heuristic zero-dependency web-fetch hook with offline fallback. Code blocks in AI responses render with language-aware syntax highlighting using 4 preset themes. Built with Kotlin + Jetpack Compose, targeting production quality with clean modular architecture.

## Core Value

Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.

## Previous Milestone: v3.0 Chat UX + Voice Dictation — COMPLETE ✅

**Shipped:** 2026-10-02 | [Archive →](.planning/milestones/v3.0-ROADMAP.md)

4 phases (63-66), 7 plans, 19/19 requirements verified. Tavily integration fully removed (DDG-only search, grep-clean, startup Keystore alias cleanup, legacy citations render read-only). Drawer/settings/catalog/help polished (sheet CTA, uniform footer, drawer-bottom delete-all, Settings removals, Help EN+ES rewrite, Use-in-Chat + empty-state CTAs). Voice dictation (SpeechRecognizer, single-insertion state machine, first-tap rationale + Settings escape). Ambient Play In-App Review (mutex-serialized 5/21d/3 policy + Settings Store entry). 914 unit tests green, security SECURED 18/18.

**Known deferred:** POL-01 review-threshold tuning, POL-02 offline-dictation hint, POL-03 Pixel 7 benchmarks, POL-04 standing release-UAT device smokes (see STATE.md Deferred Items).

## Previous Milestone: v1.6 Code Syntax Highlighting — COMPLETE ✅

**Shipped:** 2026-05-15 | [Archive →](.planning/milestones/v1.6-ROADMAP.md)

3 phases, 20 requirements completed. Code blocks render with token-level syntax highlighting (12 token types, 43 languages) via Highlights 1.1.0 engine. 4 preset themes (Monokai, One Dark, GitHub, Dracula) with light/dark auto-adaptation. Language header bar with copy button, line numbers, expand/collapse on every code block. Streaming-to-highlighted smooth transitions. Applied in chat, model cards, and everywhere code blocks appear.

**Known deferred:** INTG-03 (README preview — requires new API endpoint). 18 pre-existing open items recorded in STATE.md.

## Previous Milestone: v1.7 App Optimization & Smart Presets — COMPLETE ✅

**Shipped:** 2026-05-25 | [Archive →](.planning/milestones/v1.7-ROADMAP.md)

4 phases (31-34), 19 requirements completed. LiteRT-LM upgraded to v0.12.0, unified Models & Endpoints selector with connect/disconnect toggle, traffic light status indicator for both local and remote, and memory-aware smart presets with manual override.

**Known deferred:** 12 quick tasks + 6 verification gaps (see STATE.md Deferred Items).

## Previous Milestone: v1.8 LiteRT Update, Bugfix Round & Recommended Models — COMPLETE ✅

**Shipped:** 2026-06-05 | [Archive →](.planning/milestones/v1.8-ROADMAP.md)

5 phases (35-39), 30 requirements implemented. LiteRT-LM upgrade path, Hugging Face model browser bugfixes (sibling file rendering, search persistence, post-download nav, background downloads with progress + cancel), chat UI redesign (rounded pill input, no TopAppBar, unified model+endpoint picker with traffic-light status), endpoint CRUD + LM Studio native v1 API, and a hand-curated recommended models section.

**Known deferred:** 3 verification gaps in Phase 35 (compile gate not run, no unit test for conversation reuse, multi-turn smoke test pending) — user/CI verification required. 6 pre-existing verification gaps (Phases 06-10, 29) remain.

## Previous Milestone: v2.0 Gallery Convergence & Performance Overhaul — COMPLETE ✅

**Shipped:** 2026-06-06 | [Archive →](.planning/milestones/v2.0-ROADMAP.md)

5 phases (40-44), 23 plans, 53 requirements — 48 MET, 5 PARTIAL (Phase 43 PERF-01, PERF-05, PERF-06, PERF-12, PERF-13) + 2 carry-overs (Phase 44 SKILLS-02 Tool execution, SKILLS-03 LM Studio tools[] mapping). LlmModelHelper keystone interface, model allowlist asset, thinking mode, model benchmark, Prompt Lab, performance convergence sweep, and Agent Skills Lite.

**Known deferred:** 7 items carried into v2.1 (see STATE.md Blockers/Concerns). PERF-12/13 benchmark numbers stay CI-gated (require Pixel 7 reference device).

## Previous Milestone: v2.1 Finish v2.0 Leftovers — COMPLETE ✅

**Shipped:** 2026-09-28 | [Archive →](.planning/milestones/v2.1-ROADMAP.md) · [Audit →](v2.1-MILESTONE-AUDIT.md) (passed)

4 phases (45–48), 10 plans, 18/18 requirements MET, no partials left:
- Catalog to latest stable + LiteRT-LM 0.13.1 → 0.17.1 (45)
- Cancellable single-flight runInference — Stop means stop (46)
- Real tool execution local (@Tool) + remote (tools[] loop) with trust boundary (47)
- Atomic sub-state/LazyColumn split, Baseline Profiles, release hardening (48)
- Plus device-driven fixes: Thinking fallback, session lifecycle, GPU-constraint retry, spec-decode opt-in, icon badges, seamless model switch, history repair migration v14

**Known deferred:** Pixel 7 reference numbers (PERF-16 + PERF-12/13 gate) — emulator note in BENCHMARKS.md, CI-gated.

## Previous Milestone: v2.2 Simplificación + Web Grounding — COMPLETE ✅

**Shipped:** 2026-09-28 | [Archive →](.planning/milestones/v2.2-ROADMAP.md) · [Audit →](milestones/v2.2-MILESTONE-AUDIT.md) (gaps_found, accepted)

3 phases (49–51), 5 plans, 14 requirements — 10 MET, 4 PARTIAL (all device-smoke-bound, user-accepted):
- Surface removal: skills/tool-loop deleted (26 files), HF token + model search deleted → static allowlist catalog with direct downloads (net −4721/+1378 lines)
- Web grounding: heuristic single-fetch pipeline (`data/grounding/`) + chip/Fuentes/banner surfaces + default-ON toggle, zero new dependencies
- Syntax-theme fix: all 4 presets apply in chat (was Monokai-only) with all-4-preset regression tests

**Known deferred:** 4 release-UAT device smokes (DEL-06 release smoke, WEB-05 banner visual, WEB-06 chip/Fuentes/E2E, THEME-01 per-preset visual light+dark). Orphaned Keystore `huggingface_token` entry on upgrades (harmless).

## Previous Milestone: v2.3 Web Grounding v2 — COMPLETE ✅

**Shipped:** 2026-09-28 | [Archive →](.planning/milestones/v2.3-ROADMAP.md) · [Audit →](milestones/v2.3-MILESTONE-AUDIT.md) (gaps_found, accepted)

3 phases (52–54), 8 plans, 12 requirements — 12/12 verified:
- Multi-URL fetch: parallel fan-out (cap 5), fused `[WEB CONTEXT 1..N]` blocks, partial grounding, per-source `Leyendo N de M…` progress, concurrent-safe cancel
- Jsoup 1.23.2 parse-only extraction (never `connect()`), frozen fetch policy, global model-window-aware grounding budget + adversarial/budget exit gates
- Sources preview bottom sheet (zero-I/O, guarded `Abrir en navegador`), clickable Fuentes, `grounded_sources` table + single `MIGRATION_14_15` (JVM static gate)
- Per-chat tri-state toggle (Sí/No/Heredar) + one-off `Sin web` chip via pure `GroundingPrecedence`
- Offline retry: queued `En espera` banner + validated-online `Reintentar`, same-entry fetch, sources-only attach, same-row reuse, Stop/overlap/streaming guards
- 289/289 unit green; SECURED all phases (11/11, 16/16, 8/8); integration 5/5 flows wired

**Known deferred:** 3 release-UAT device smokes (MIG-01 on-device MigrationTest, WEB-07 grounding visuals both themes, WEB-08 offline→retry live E2E). UI polish trio + budget on-device validation (TUNE-01/02 triggers). Pre-existing carry-overs: Pixel 7 reference numbers, v2.2 smokes, orphaned Keystore entry.

## Previous Milestone: v2.4 Agentic Web — COMPLETE ✅

**Shipped:** 2026-09-29 | [Archive →](.planning/milestones/v2.4-ROADMAP.md) · [Audit →](milestones/v2.4-MILESTONE-AUDIT.md) (gaps_found, accepted)

4 phases (55–58), 9 plans, 10 requirements — 10/10 verified:
- Tavily search: Keystore key + test-connection, dedicated Bearer client, search→fused producer (live key HTTP 200)
- Local agentic loop: manual runToolLoop, 5-call cap, Stop-cancels-all, transient Using rows, KV-channel hygiene (real ToolCalls device-confirmed on E2B)
- Remote agentic loop: shared SSE accumulator + capability matrix + one-retry classifier (OpenAI/Anthropic/Ollama/LMStudio/Custom), secret isolation proven
- OG thumbnails: parse-only scrape, Room v16, Coil 3.4.0 singleton + disk cache, per-source cards + sheet header per user mock
- 494/494 unit green; SECURED all phases; integration 5/5 flows wired

**Known deferred:** 3 release-UAT device follow-ups (WEB-09 live Tavily E2E, WEB-10 LM Studio smoke + matrix + Stop, WEB-11 Coil images + ellipsis). Tech debt: budget floor, static matrix, ThinkingConfig enablement, Coil 3.4.0 ceiling. Pre-existing carry-overs: v2.3 smokes, UI polish trio, TUNE-01/02, Pixel 7 numbers, v2.2 smokes, Keystore orphan.

## Previous Milestone: v2.5 Play Compliance + Leaks — COMPLETE ✅

**Shipped:** 2026-10-01 | [Archive →](.planning/milestones/v2.5-ROADMAP.md) · [Audit →](milestones/v2.5-MILESTONE-AUDIT.md) (passed)

4 phases (59–62), 8 plans, 15 requirements — 15/15 satisfied:
- 16 KB page-size compliance: 14/14 `.so` ALIGNED + zipalign OK, sqlcipher 4.5.4→4.19.1 version-bump-only fix, fail-closed CI gates, 16 KB emulator chat turn green (G-59-01 closed by Phase 62)
- API-36 behavior audit: compileSdk/targetSdk 36 + R8 green, per-screen edge-to-edge insets, predictive-back BackHandler sweep (0 legacy paths), WorkManager stop-reason retry UI, sw800dp tablet fill
- LeakCanary 2.14 debug-only harness + 6-leg scripted tour on Pixel 8 hardware: 6/6 clean, zero leaks; 32 regression tests lock the clean paths (932/932 green, zero production changes)
- Tonight's beta extras landed on the same release tree: audio/vision slot gating, same-language reply, toggle removal + auto-select, size-sorted catalog with verified gating
- Release hardened: fresh signed AAB/APK, all gates green

**Known deferred:** Play Console pre-launch + target-API dashboard reads (human); Leg 1B model-B switch; Phase 60 follow-ups (5); standing device smokes (v2.2–v2.4). 47 closeout acknowledgments recorded in STATE.md Deferred Items.

## Current Milestone: v3.0 Chat UX + Voice Dictation

**Goal:** Polish chat drawers, settings, catalog and help surfaces, simplify web search to DuckDuckGo-only, and add voice dictation + Play in-app rating.

**Target features:**
- Play in-app star rating from within the app
- Model drawer empty-state: "Download a model" CTA → Model Catalog; Web Options removed from drawer (stays in Settings)
- Chat drawer footer parity: Models/Help/Settings same text size as New Chat; Delete-all-chats moved to drawer bottom above Models
- Help screen rewrite: short, minimal, to-the-point
- Search simplification: remove Tavily integration, DuckDuckGo only
- Settings cleanup: remove Keystore key-deletion ability; remove Data section + delete-chats
- Models & Endpoints empty-states: "Download a local model" + "Add a new Endpoint" CTAs
- Model Catalog: "Use in Chat" button on downloaded models
- Voice dictation into chat input (speech-to-text only, no audio messages yet)

## Requirements

### Validated

- ✓ User can search for .litertlm models from litertlm-community on Hugging Face — v1.0
- ✓ User can download .litertlm models from Hugging Face with pause/resume — v1.0
- ✓ User can import local .litertlm files from device storage — v1.0
- ✓ User can load a .litertlm model and chat with streaming via LiteRT-LM — v1.0
- ✓ User can configure generation parameters (temperature, top_p, top_k, repeat_penalty, max_tokens, context_size, seed, threads) — v1.0
- ✓ User can chat with a remote OpenAI-compatible model with streaming responses — v1.0
- ✓ User can add, edit, delete, and test remote endpoints (OpenAI-compatible, Ollama, LM Studio, Anthropic, custom) — v1.0
- ✓ User can list models from a remote endpoint — v1.0
- ✓ User can manage downloaded models (view, delete) — v1.0
- ✓ User can save and reuse generation presets — v1.0
- ✓ User can browse chat history and resume conversations — v1.0
- ✓ API keys are stored encrypted via Android Keystore — v1.0
- ✓ Chat history and model/endpoint configs persist across app restarts via Room — v1.0
- ✓ Code blocks and markdown render correctly in chat messages — v1.5
- ✓ App resumes active chat without "model not loaded" warning after backgrounding — v1.5
- ✓ Active conversation is visually tracked and highlighted in conversation list — v1.5
- ✓ Deleting a message immediately clears it from the rendered chat UI — v1.5
- ✓ ProGuard/R8 obfuscation and shrinking enabled with aggressive rules for release — v1.5
- ✓ All user inputs validated and sanitized against injection — v1.5
- ✓ Global crash handler with graceful recovery — v1.5
- ✓ Network security config blocks cleartext traffic in release builds — v1.5
- ✓ All GGUF/llama.cpp code, JNI, CMake, and Vulkan backend removed — v1.5
- ✓ Model search has no tabs — single search bar filtered to litertlm-community — v1.5
- ✓ Onboarding wizard updated to reflect LiteRT-LM-only engine — v1.5
- ✓ Syntax highlighting with auto-detected language for code blocks — v1.6
- ✓ Language header bar showing detected language name on each code block — v1.6
- ✓ Preset themes (Monokai, One Dark, GitHub, Dracula) in Settings — v1.6
- ✓ Light/dark theme auto-adaptation per preset — v1.6
- ✓ Copy-to-clipboard button on each code block — v1.6
- ✓ Line numbers displayed alongside code blocks — v1.6
- ✓ Code blocks over 200 lines collapsed with tap-to-expand — v1.6
- ✓ Code font size scales relative to chat text (multiplier setting) — v1.6
- ✓ Syntax highlighting applied everywhere code blocks appear (chat, model cards, etc.) — v1.6
- ✓ Smooth color transition when streaming code block completes — v1.6
- ✓ No jank or frame drops during streaming — v1.6
- ✓ User chats with no skills surface — local `@Tool` execution, skill chips/prefs, Summarize template, and remote `tools[]` loop removed; legacy tool rows render read-only — v2.2
- ✓ No Hugging Face token anywhere — settings field, download auth, encrypted prefs, gated models all removed — v2.2
- ✓ Model discovery is static-catalog-only — `model_allowlist.json` with direct token-free downloads (progress/cancel) — v2.2
- ✓ Pasted URLs ground the answer — bounded fetch → `[WEB CONTEXT]` block with hijack sanitization, numbered sources, offline model-only fallback, default-ON toggle — v2.2 (device-smoke visuals deferred)
- ✓ All 4 code presets apply in chat code blocks — Monokai-only hardcode fixed, all-4-preset regression tests — v2.2 (visual confirmation deferred)
- ✓ User pasting 2–5 URLs gets one answer grounded in all fetchable pages (parallel fan-out, fused `[WEB CONTEXT 1..N]`, partial grounding, per-source `Leyendo N de M…` progress) — v2.3
- ✓ User gets cleaner grounded answers via Jsoup 1.23.2 parse-only extraction with frozen fetch policy + model-window-aware global grounding budget — v2.3
- ✓ User previews each source in a bottom sheet (extracted text, `Abrir en navegador`) from a numbered Fuentes list covering all N sources — v2.3
- ✓ User overrides web grounding per conversation (Sí/No/Heredar) and sends one-off model-only messages (`Sin web`); preferences + sources survive restarts (Room v15) — v2.3
- ✓ User offline at send time retries grounding on reconnect (`Reintentar`, message-scoped foreground, same rows, history untouched, inference never re-run) — v2.3
- ✓ User stores a Tavily key (Keystore) and grounds answers in search results with numbered citations — v2.4
- ✓ Local models search/fetch autonomously via function calling (5-call cap, Stop, transient rows, channel hygiene) — v2.4
- ✓ Remote models use the same tools via native tools[] loop with capability gating + fallback notice — v2.4
- ✓ Every grounded source renders an OpenGraph thumbnail card (Coil, tap → sheet with OG header) — v2.4
- ✓ Every shipped native library is 16 KB-aligned (check_elf_alignment.sh + zipalign on release AAB/APK) — v2.5
- ✓ App installs, launches, and runs a local chat turn on a 16 KB system image with no native load failures — v2.5
- ✓ CI fails the build on misalignment (Gradle check + CI/release gates) — v2.5
- ✓ Misaligned dependencies fixed by version bump only (sqlcipher-android 4.19.1, no hacks) — v2.5
- ✓ App targets Android 16 (compileSdk 36 + targetSdk 36) with assembleRelease + R8 green — v2.5
- ✓ Edge-to-edge insets on chat pill, bottom sheets, Fuentes list (gesture + 3-button nav, light/dark) — v2.5
- ✓ Back navigation on predictive-back APIs, no dead onBackPressed paths — v2.5
- ✓ Model downloads + offline retry survive Android 16 quotas (FGS types, stop reasons, retry) — v2.5
- ✓ App fills large-screen windows (sw ≥ 600dp tablet/foldable) without pillarboxing — v2.5
- ✓ LeakCanary 2.14 debug-only harness with scripted 6-leg leak tour, 6/6 clean on hardware — v2.5
- ✓ EngineManager releases native handles on model switch/unload — v2.5
- ✓ Chat turn-scoped Flows cancel cleanly (single-flight cancel, SSE streams closed on Stop) — v2.5
- ✓ Grounding pipeline cancels as one scope per send, retry reuses rows without old-job retention — v2.5
- ✓ Coil + OkHttp scope discipline (recycle-cancel, bounded cache, never-closed shared clients) — v2.5
- ✓ Release AAB passes all gates (alignment + R8 + 16 KB smoke + zero-leak pass green) — v2.5

### Active

- [ ] Play in-app rating — v3.0
- [ ] Model drawer empty-state CTA + Web Options relocation — v3.0
- [ ] Chat drawer footer parity + delete-all-chats relocation — v3.0
- [ ] Help screen rewrite (short/minimal) — v3.0
- [ ] Tavily removal, DuckDuckGo-only search — v3.0
- [ ] Settings cleanup (no key-delete, no Data section) — v3.0
- [ ] Models & Endpoints empty-state CTAs — v3.0
- [ ] Model Catalog "Use in Chat" on downloaded models — v3.0
- [ ] Voice dictation into chat input — v3.0

### Out of Scope

- Voice input/output — defer, focus on text chat
- Image/multimodal models — defer, focus on text LLMs
- AI agents / autonomous tool use — defer, MCP tool calling via LM Studio API is in scope
- Real-time sync across devices — defer
- Paid subscriptions to remote providers built into the app — user brings own API keys
- GGUF / llama.cpp local inference — REMOVED in v1.5. App pivots to LiteRT-LM as sole local engine.
- Staff Picks tab and multi-tab model browser — REMOVED in v1.5. Single search bar replaces tabbed browsing.
- Certificate pinning for remote endpoints — defer, not selected for v1.5 or v1.6 hardening scope
- Play Integrity / root detection — defer, not selected for v1.5 or v1.6 hardening scope

## Context

The Android ecosystem lacks a polished, production-grade app that combines local LLM inference with remote provider connectivity in a single interface. Existing solutions are either CLI-only, desktop-only (LM Studio), or limited to one provider. There is growing demand for running LLMs on flagship Android phones with 8-16GB RAM.

Warped has shipped 12 milestones (v1.0 through v2.2) across 51 phases and 315 requirements. The app supports LiteRT-LM local inference, LM Studio v1 REST API, static-catalog model download with background downloads and progress, chat with streaming, heuristic web grounding with offline fallback, presets, history, an 8-step onboarding wizard, and syntax-highlighted code blocks with 4 themes. Production hardening is applied (ProGuard, Keystore encryption, input sanitization, crash resilience, network security).

**v1.8 (2026-06-05):** Hugging Face model browser bugfixes (sibling file rendering, search persistence, post-download nav, background downloads with progress + cancel), chat UI redesign (rounded pill input, no TopAppBar, unified model+endpoint picker with traffic-light status), endpoint CRUD with LM Studio native v1 API, and a hand-curated recommended models section.

**v1.7 (2026-05-25):** LiteRT-LM upgraded to v0.12.0, unified Models & Endpoints selector with connect/disconnect toggle, traffic light status indicator, memory-aware smart presets.

**v1.6 (2026-05-15):** Syntax highlighting engine built on Highlights 1.1.0 with custom TypeMapper and LanguageDetector. Code blocks show token-level coloring across 12 token types, auto-detected language with 20+ aliases, 4 themes with light/dark variants, copy button, line numbers, and expand/collapse. Streaming transitions smooth with flat monospace during active streaming and full highlighting on closing fence.

**Known issue:** JUnit Platform launcher classpath — `./gradlew :app:testDebugUnitTest` fails with "Failed to load JUnit Platform" (pre-existing, not introduced by v1.6).

**v1.8 verification gaps:** 3 gaps in Phase 35 (compile gate not run, no unit test for conversation reuse, multi-turn smoke test pending) — user/CI verification required.

**v2.0 (2026-06-06):** LlmModelHelper keystone interface unifying local/remote chat, model allowlist asset, thinking mode, model benchmark with WorkManager, Prompt Lab, performance convergence sweep, and Agent Skills Lite. 48 MET + 5 PARTIAL + 2 carry-overs → all 7 deferred into v2.1.

**v2.0 verification gaps:** PERF-12/13 benchmark numbers require a Pixel 7 reference device — CI-gated, stay deferred through v2.1.

**v2.2 (2026-09-28):** Net-deletion milestone (−4721/+1378 lines across 87 files): skills/tool-loop surface removed, HF token + search removed in favor of the static catalog, heuristic web grounding added with zero new dependencies, all 4 syntax presets fixed. 232/232 unit tests green; `assembleDebug` + `assembleRelease` green. 4 device-smoke partials accepted into release UAT.

---

## Constraints

- **Platform**: Android only (no iOS, no desktop)
- **Tech stack**: Kotlin, Jetpack Compose, Hilt DI, Room, DataStore, WorkManager
- **Local inference**: LiteRT-LM via Maven dependency (.litertlm) — single engine, no llama.cpp/GGUF
- **Remote providers**: OpenAI-compatible API protocol, OkHttp, SSE streaming
- **Language**: Kotlin (no Java)
- **Architecture**: Clean architecture (domain/data/ui layers), MVVM, repository pattern
- **Security**: API keys encrypted via Android Keystore, no plaintext secrets
- **Offline-first**: Local chat works without internet; remote fails gracefully
- **Performance**: Never block UI thread during inference or downloads

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Kotlin + Compose over Flutter/React Native | Native Android performance for JNI/NDK integration, idiomatic platform APIs | ✓ Good |
| Clean Architecture with Hilt | Testability, separation of concerns, industry standard for Android | ✓ Good |
| Room for structured data | Official Android recommendation, Compose integration, type-safe queries | ✓ Good |
| Coarse granularity (3-5 phases) | Build fast, validate early, iterate | ✓ Good |
| LiteRT-LM as sole local engine (v1.5) | Better performance than llama.cpp on Android, native GPU/NPU, simpler codebase | ✓ Good |
| GGUF/llama.cpp removal in v1.5 | Single engine simplifies codebase, reduces bugs, focuses resources | ✓ Good |
| Highlights 1.1.0 as syntax tokenization engine (v1.6) | Saved ~750-1,400 lines vs custom regex tokenizer. Wrapped behind SyntaxHighlighter domain interface for swapability | ✓ Good |
| Deferred highlighting during streaming (v1.6) | Flat monospace during streaming, full coloring on closing fence. Prevents O(n²) jank | ✓ Good |
| MarkdownText restructured to block-based Column (v1.6) | Enables per-block composables (language header, copy button) that were impossible with single Text(AnnotatedString) | ✓ Good |
| CodeTheme → SyntaxTheme migration (v1.6) | Old enum names map to new theme objects via existing DataStore key. Backward compatible | ✓ Good |
| v1.8 LM Studio native v1 only | Simplify provider surface, use native REST + native DTOs, drop OpenAI/Anthropic/Ollama/Custom | ✓ Good |
| v1.8 hand-curated recommended models | Static asset shipping with the app, no API scraping, no ranking algorithms | ✓ Good |
| v2.0 references Google AI Edge Gallery (2026-06-05) | Reference implementation in same domain (Kotlin + LiteRT-LM on Android, 23.6k stars). Largest, most active OSS in the space. | — Pending |
| v2.1 finishes v2.0 PARTIALs/carry-overs (2026-09-27) | No new features until every shipped feature is fully done — PERF-01, PERF-06, SKILLS-02/03, double-collect, Call.cancel(). PERF-12/13 numbers stay CI-gated (Pixel 7 hardware required). | ✓ Good |
| v2.2 removes v2.1 Skills surface (2026-09-28) | Calculator/CurrentTime/JsonFormatter + chips/prefs/repo/gating/tool-loops deleted one milestone after introduction — user found no value, simplifies codebase and R8 keeps. Zero new dependencies for web grounding (OkHttp fetch only). | ✓ Good |
| v2.5 version-bump-only remediation (2026-09-30) | Misaligned sqlcipher 4.5.4 (EOL, p_align 0x1000) → successor artifact sqlcipher-android 4.19.1. Never hand-patch .so, linker-flag hacks, or pageSizeCompat. | ✓ Good |
| v2.5 LeakCanary debugImplementation-only (2026-09-30) | Zero release footprint proven at classpath + dex level. Clean baseline → Phase 62 is tests-only, zero production changes. | ✓ Good |
| v2.5 gap back-closure across phases (2026-10-01) | G-59-01 (16 KB chat turn, emulator-blocked) closed by Phase 62's fresh-artifact smoke instead of reopening Phase 59. | ✓ Good |
| v3.0 major bump for UX + removals (2026-10-02) | User chose v3.0 over v2.6: Tavily removal + settings surface removals are breaking-behavior changes justifying a major. | — Pending |
| v3.0 DuckDuckGo-only search (2026-10-02) | Remove Tavily integration entirely (key, client, producer, UI); single DDG path reduces keys, clients, and test matrix. | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-10-02 — v3.0 Chat UX + Voice Dictation milestone started*
