# Warped

## What This Is

An Android application equivalent to LM Studio for mobile, enabling users to run large language models (LLMs) locally via LiteRT-LM and connect to remote LLM providers. The app downloads `.litertlm` models from the litertlm-community on Hugging Face, executes them on-device, and connects to OpenAI-compatible APIs, Anthropic, Ollama, LM Studio, and custom servers. Code blocks in AI responses render with language-aware syntax highlighting using 4 preset themes. Built with Kotlin + Jetpack Compose, targeting production quality with clean modular architecture.

## Core Value

Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.

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

## Current Milestone: v2.0 Gallery Convergence & Performance Overhaul

**Goal:** Download and study the Google AI Edge Gallery repo, port its best patterns and any fitting features into Warped, and apply a file-by-file performance/efficiency overhaul so the app is as fast, smooth, and resource-light as possible. Major version bump signals architectural/feature convergence with the reference implementation.

**Target features (tentative — refined after research):**
- Performance audit across all axes: cold start, UI smoothness (60fps scrolling, no jank during streaming), memory footprint, time-to-first-token, APK size
- Port Gallery-aligned features that fit Warped's scope (Prompt Lab, Thinking Mode toggle, Model Benchmark, Agent Skills infrastructure, mobile-first model allowlist patterns — refined by research)
- Architectural pattern convergence: DI structure, state management, navigation, theming, model-loading pipeline
- File-by-file, line-by-line optimization of every hot path (Hilt graph, Room queries, OkHttp interceptors, Compose recomposition, JNI/engine init)
- Research output: 4 parallel researchers (Stack, Features, Architecture, Pitfalls) analyze the Gallery repo and surface concrete migration candidates

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

### Active

- [ ] v2.0 Gallery Convergence & Performance Overhaul — major version: research Gallery repo, port best patterns, file-by-file perf overhaul (see Current Milestone above)

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

Warped has shipped 8 milestones (v1.0 through v1.8) across 39 phases and 234 requirements. The app supports LiteRT-LM local inference, LM Studio v1 REST API, Hugging Face model search/download with background downloads and progress, chat with streaming, presets, history, an 8-step onboarding wizard, and syntax-highlighted code blocks with 4 themes. Production hardening is applied (ProGuard, Keystore encryption, input sanitization, crash resilience, network security).

**v1.8 (2026-06-05):** Hugging Face model browser bugfixes (sibling file rendering, search persistence, post-download nav, background downloads with progress + cancel), chat UI redesign (rounded pill input, no TopAppBar, unified model+endpoint picker with traffic-light status), endpoint CRUD with LM Studio native v1 API, and a hand-curated recommended models section.

**v1.7 (2026-05-25):** LiteRT-LM upgraded to v0.12.0, unified Models & Endpoints selector with connect/disconnect toggle, traffic light status indicator, memory-aware smart presets.

**v1.6 (2026-05-15):** Syntax highlighting engine built on Highlights 1.1.0 with custom TypeMapper and LanguageDetector. Code blocks show token-level coloring across 12 token types, auto-detected language with 20+ aliases, 4 themes with light/dark variants, copy button, line numbers, and expand/collapse. Streaming transitions smooth with flat monospace during active streaming and full highlighting on closing fence.

**Known issue:** JUnit Platform launcher classpath — `./gradlew :app:testDebugUnitTest` fails with "Failed to load JUnit Platform" (pre-existing, not introduced by v1.6).

**v1.8 verification gaps:** 3 gaps in Phase 35 (compile gate not run, no unit test for conversation reuse, multi-turn smoke test pending) — user/CI verification required.

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
*Last updated: 2026-06-05 after v2.0 Gallery Convergence & Performance Overhaul milestone start*
