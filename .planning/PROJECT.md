# Warped

## What This Is

An Android application equivalent to LM Studio for mobile, enabling users to run large language models (LLMs) locally via LiteRT-LM and connect to remote LLM providers. The app downloads `.litertlm` models from the litertlm-community on Hugging Face, executes them on-device, and connects to OpenAI-compatible APIs, Anthropic, Ollama, LM Studio, and custom servers. Code blocks in AI responses render with language-aware syntax highlighting using 4 preset themes. Built with Kotlin + Jetpack Compose, targeting production quality with clean modular architecture.

## Core Value

Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.

## Previous Milestone: v1.6 Code Syntax Highlighting — COMPLETE ✅

**Shipped:** 2026-05-15 | [Archive →](.planning/milestones/v1.6-ROADMAP.md)

3 phases, 20 requirements completed. Code blocks render with token-level syntax highlighting (12 token types, 43 languages) via Highlights 1.1.0 engine. 4 preset themes (Monokai, One Dark, GitHub, Dracula) with light/dark auto-adaptation. Language header bar with copy button, line numbers, expand/collapse on every code block. Streaming-to-highlighted smooth transitions. Applied in chat, model cards, and everywhere code blocks appear.

**Known deferred:** INTG-03 (README preview — requires new API endpoint). 18 pre-existing open items recorded in STATE.md.

## Current Milestone: v1.7 App Optimization & Smart Presets

**Goal:** Optimize the app with latest LiteRT-LM library, unify model/endpoint selection, fix status indicators, and deliver memory-aware smart presets.

**Target features:**
- Upgrade LiteRT-LM library to latest stable release
- Unified Models & Endpoints selector screen (1 local model + infinite endpoints)
- Fix traffic light status indicator (semaforo) for correct connected/disconnected state
- Memory-based auto-optimized generation presets

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

- [ ] README/markdown preview with syntax highlighting — deferred from v1.6 (INTG-03)
- [ ] Pre-existing quick tasks from various milestones (12 items — see STATE.md)
- [ ] Human verification for Phases 06, 07, 08, 09, 10, 29 (6 items — see STATE.md)

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

Warped has shipped 6 milestones (v1.0 through v1.6) across 30 phases and 185 requirements. The app supports LiteRT-LM local inference, 5 remote provider types (OpenAI, Anthropic, Ollama, LM Studio, custom), Hugging Face model search/download, chat with streaming, presets, history, an 8-step onboarding wizard, and syntax-highlighted code blocks with 4 themes. Production hardening is applied (ProGuard, Keystore encryption, input sanitization, crash resilience, network security).

**v1.6 (2026-05-15):** Syntax highlighting engine built on Highlights 1.1.0 with custom TypeMapper and LanguageDetector. Code blocks show token-level coloring across 12 token types, auto-detected language with 20+ aliases, 4 themes with light/dark variants, copy button, line numbers, and expand/collapse. Streaming transitions smooth with flat monospace during active streaming and full highlighting on closing fence.

**Known issue:** JUnit Platform launcher classpath — `./gradlew :app:testDebugUnitTest` fails with "Failed to load JUnit Platform" (pre-existing, not introduced by v1.6).

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
*Last updated: 2026-05-25 after v1.7 App Optimization & Smart Presets milestone start*
