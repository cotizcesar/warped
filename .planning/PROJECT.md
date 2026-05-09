# Warped

## What This Is

An Android application equivalent to LM Studio for mobile, enabling users to run large language models (LLMs) locally and connect to remote LLM providers. The app downloads GGUF models from Hugging Face, executes them on-device via llama.cpp, runs `.litertlm` models via LiteRT-LM, and connects to OpenAI-compatible APIs, Ollama, LM Studio, and custom servers. Built with Kotlin + Jetpack Compose, targeting production quality with clean modular architecture.

## Core Value

Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.

## Current Milestone: v1.4 Onboarding Wizard

**Goal:** Build a full-screen introductory wizard that guides new users through the complete app workflow — understanding Warped, local engines (GGUF + LiteRT-LM), downloading models, chatting locally/remotely, managing presets, and browsing history — with a warm, friendly tone. First-launch auto-trigger with skip options, always accessible from Settings.

**Target features:**
- Full-screen onboarding flow with slides/swipe navigation between steps
- 9-step wizard: Bienvenida & qué es Warped, Motores locales (GGUF + LiteRT-LM), Descargar modelos GGUF, Modelos LiteRT-LM, Chat con modelo local, Conectar proveedores remotos (OpenAI, Anthropic, Ollama, LM Studio), Chat con modelos remotos, Presets de generación, Historial de chats
- Context-aware: adapts steps based on existing app state (models downloaded, endpoints configured, chats created)
- First-launch detection via DataStore flag — auto-triggers wizard on fresh install
- "Skip" per step (skip individual) + "Skip all" (skip entire wizard) at each step
- Call-to-action buttons per step that navigate to relevant screens
- Re-accessible anytime from Settings screen
- Warm, friendly visual tone with Material 3 styling

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] User can chat with a remote OpenAI-compatible model with streaming responses
- [ ] User can add, edit, delete, and test remote endpoints (OpenAI-compatible, Ollama, LM Studio, llama.cpp server, custom)
- [ ] User can list models from a remote endpoint
- [ ] User can search for GGUF models on Hugging Face
- [ ] User can download GGUF models from Hugging Face with pause/resume
- [ ] User can import local GGUF files from device storage
- [ ] User can load a local GGUF model and chat with streaming via llama.cpp
- [ ] User can configure generation parameters (temperature, top_p, top_k, repeat_penalty, max_tokens, context_size, seed, threads)
- [ ] User can manage downloaded models (view, delete)
- [ ] User can save and reuse generation presets
- [ ] User can browse chat history and resume conversations
- [ ] API keys are stored encrypted via Android Keystore
- [ ] Chat history and model/endpoint configs persist across app restarts via Room

### Out of Scope

- Voice input/output — defer, focus on text chat
- Image/multimodal models — defer, focus on text LLMs
- AI agents / autonomous tool use — defer, MCP tool calling via LM Studio API is in scope
- Real-time sync across devices — defer
- Paid subscriptions to remote providers built into the app — user brings own API keys

## Context

The Android ecosystem lacks a polished, production-grade app that combines local LLM inference (llama.cpp/GGUF) with remote provider connectivity in a single interface. Existing solutions are either CLI-only, desktop-only (LM Studio), or limited to one provider. There is a growing demand for running LLMs on flagship Android phones with 8-16GB RAM, especially for privacy-sensitive use cases and offline scenarios.

The user is an experienced Android developer with deep knowledge of Kotlin, Compose, and LLM inference. They want all the artifacts needed to build this app: architecture documents, data models, domain interfaces, initial implementations, and a phased roadmap.

v1.1 added LiteRT-LM as a second local inference engine (shipped). LiteRT-LM is Google's production framework that powers on-device GenAI in Chrome, Chromebook Plus, and Pixel Watch. Its Kotlin API provides `Engine → Conversation → sendMessageAsync(Flow)` with native GPU/NPU backends and `.litertlm` model format.

v1.2 implementó inferencia GGUF nativa con llama.cpp: pipeline completo (download → load → streaming chat) con paridad de UX respecto a LiteRT-LM, backend CPU + Vulkan GPU, y manejo de memoria OOM graceful.

v1.3 expande los endpoints de red: el ProviderType enum ya tiene OPENAI, ANTHROPIC, OLLAMA, LM_STUDIO, CUSTOM, pero el EndpointForm solo muestra LM_STUDIO. ProviderRouter ya maneja todos correctamente — solo falta exponerlos en la UI con nombres legibles y agregar soporte MCP para LM Studio.

**v1.3 SHIPPED (2026-05-06):** 4 fases, 31 requisitos — todos los providers remotos expuestos en UI, API keys por endpoint, OpenAI completo (chat/responses/embeddings/completions), Anthropic Messages API con SSE, Ollama API completa (9 endpoints), LM Studio con MCP ephemeral + plugin servers.

## Constraints

- **Platform**: Android only (no iOS, no desktop)
- **Tech stack**: Kotlin, Jetpack Compose, Hilt DI, Room, DataStore, WorkManager
- **Local inference**: llama.cpp via JNI/NDK (GGUF) + LiteRT-LM via Maven dependency (.litertlm)
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
*Last updated: 2026-05-06 after milestone v1.3 start*
