# Warped

## What This Is

An Android application equivalent to LM Studio for mobile, enabling users to run large language models (LLMs) locally via LiteRT-LM and connect to remote LLM providers. The app downloads `.litertlm` models from the litertlm-community on Hugging Face, executes them on-device, and connects to OpenAI-compatible APIs, Anthropic, Ollama, LM Studio, and custom servers. Built with Kotlin + Jetpack Compose, targeting production quality with clean modular architecture.

## Core Value

Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.

## Current Milestone: v1.6 Code Syntax Highlighting

**Goal:** Code blocks in AI responses and throughout the app render with language-aware syntax highlighting using popular preset themes that auto-adapt to light/dark mode.

**Target features:**
- Syntax highlighting with auto-detected language for code blocks
- Language header bar showing detected language name on each code block
- Preset themes (Monokai, One Dark, GitHub, Dracula) selectable in Settings
- Light/dark theme auto-adaptation per preset
- Copy-to-clipboard button on each code block
- Applied everywhere code blocks appear (chat, model cards, readmes, etc.)

### Previous Milestone: v1.5 Bug Hunt, Cleanup & Hardening Pre-Prod — COMPLETE ✅

**Archived:** 2026-05-09 | [Archive →](.planning/milestones/v1.5-ROADMAP.md)

5 phases, 26 requirements completed.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] User can search for .litertlm models from litertlm-community on Hugging Face
- [ ] User can download .litertlm models from Hugging Face with pause/resume
- [ ] User can import local .litertlm files from device storage
- [ ] User can load a .litertlm model and chat with streaming via LiteRT-LM
- [ ] User can configure generation parameters (temperature, top_p, top_k, repeat_penalty, max_tokens, context_size, seed, threads)
- [ ] User can chat with a remote OpenAI-compatible model with streaming responses
- [ ] User can add, edit, delete, and test remote endpoints (OpenAI-compatible, Ollama, LM Studio, Anthropic, custom)
- [ ] User can list models from a remote endpoint
- [ ] User can manage downloaded models (view, delete)
- [ ] User can save and reuse generation presets
- [ ] User can browse chat history and resume conversations
- [ ] API keys are stored encrypted via Android Keystore
- [ ] Chat history and model/endpoint configs persist across app restarts via Room
- [ ] Code blocks and markdown render correctly in chat messages
- [ ] App resumes active chat without "model not loaded" warning after backgrounding
- [ ] Active conversation is visually tracked and highlighted in conversation list
- [ ] Deleting a message immediately clears it from the rendered chat UI
- [ ] ProGuard/R8 obfuscation and shrinking enabled with aggressive rules for release
- [ ] All user inputs validated and sanitized against injection
- [ ] Global crash handler with graceful recovery
- [ ] Network security config blocks cleartext traffic in release builds
- [ ] All GGUF/llama.cpp code, JNI, CMake, and Vulkan backend removed
- [ ] Model search has no tabs — single search bar filtered to litertlm-community
- [ ] Onboarding wizard updated to reflect LiteRT-LM-only engine

### Out of Scope

- Voice input/output — defer, focus on text chat
- Image/multimodal models — defer, focus on text LLMs
- AI agents / autonomous tool use — defer, MCP tool calling via LM Studio API is in scope
- Real-time sync across devices — defer
- Paid subscriptions to remote providers built into the app — user brings own API keys
- GGUF / llama.cpp local inference — REMOVED in v1.5. App pivots to LiteRT-LM as sole local engine. Hugging Face search limited to litertlm-community `.litertlm` models.
- Staff Picks tab and multi-tab model browser — REMOVED in v1.5. Single search bar replaces tabbed browsing.

## Context

The Android ecosystem lacks a polished, production-grade app that combines local LLM inference (llama.cpp/GGUF) with remote provider connectivity in a single interface. Existing solutions are either CLI-only, desktop-only (LM Studio), or limited to one provider. There is a growing demand for running LLMs on flagship Android phones with 8-16GB RAM, especially for privacy-sensitive use cases and offline scenarios.

The user is an experienced Android developer with deep knowledge of Kotlin, Compose, and LLM inference. They want all the artifacts needed to build this app: architecture documents, data models, domain interfaces, initial implementations, and a phased roadmap.

v1.1 added LiteRT-LM as a second local inference engine (shipped). LiteRT-LM is Google's production framework that powers on-device GenAI in Chrome, Chromebook Plus, and Pixel Watch. Its Kotlin API provides `Engine → Conversation → sendMessageAsync(Flow)` with native GPU/NPU backends and `.litertlm` model format.

v1.2 implementó inferencia GGUF nativa con llama.cpp: pipeline completo (download → load → streaming chat) con paridad de UX respecto a LiteRT-LM, backend CPU + Vulkan GPU, y manejo de memoria OOM graceful.

v1.3 expande los endpoints de red: el ProviderType enum ya tiene OPENAI, ANTHROPIC, OLLAMA, LM_STUDIO, CUSTOM, pero el EndpointForm solo muestra LM_STUDIO. ProviderRouter ya maneja todos correctamente — solo falta exponerlos en la UI con nombres legibles y agregar soporte MCP para LM Studio.

**v1.3 SHIPPED (2026-05-06):** 4 fases, 31 requisitos — todos los providers remotos expuestos en UI, API keys por endpoint, OpenAI completo (chat/responses/embeddings/completions), Anthropic Messages API con SSE, Ollama API completa (9 endpoints), LM Studio con MCP ephemeral + plugin servers.

**v1.5:** Pivote a LiteRT-LM como único motor local. Se elimina completamente llama.cpp/GGUF (JNI, CMake, Vulkan). El buscador de modelos se simplifica a una sola barra filtrando `litertlm-community`. Hardening pre-producción (ProGuard, validación, crash resilience, network security). Corrección de 4 bugs de chat (code rendering, model reload, active tracking, ghost delete).

**v1.6:** Code syntax highlighting in chat messages and throughout the app. Language auto-detection, preset themes (Monokai, One Dark, GitHub, Dracula) with light/dark variants, copy button, and language header bar on every code block. Settings page for theme selection.

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
| Kotlin + Compose over Flutter/React Native | Native Android performance for JNI/NDK integration, idiomatic platform APIs | — Pending |
| llama.cpp over MLC-LLM or ExecuTorch | Most mature GGUF ecosystem, widest model support, active community | — Pending |
| Clean Architecture with Hilt | Testability, separation of concerns, industry standard for Android | — Pending |
| Room for structured data | Official Android recommendation, Compose integration, type-safe queries | — Pending |
| Coarse granularity (3-5 phases) | Build fast, validate early, iterate | — Pending |
| LiteRT-LM as second local engine | Better performance than llama.cpp on Android, native GPU/NPU, Google's production framework for Chrome/Chromebook/Pixel | — Pending |
| GGUF/llama.cpp removal in v1.5 | App pivots to LiteRT-LM as sole local engine. GGUF path had higher complexity (JNI/CMake/Vulkan), larger APK, and overlapping functionality. Single engine simplifies codebase, reduces bugs, and focuses resources. | — Pending |

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
*Last updated: 2026-05-14 after milestone v1.6 start*
