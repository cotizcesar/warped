# Warped

## What This Is

An Android application equivalent to LM Studio for mobile, enabling users to run large language models (LLMs) locally and connect to remote LLM providers. The app downloads GGUF models from Hugging Face, executes them on-device via llama.cpp, runs `.litertlm` models via LiteRT-LM, and connects to OpenAI-compatible APIs, Ollama, LM Studio, and custom servers. Built with Kotlin + Jetpack Compose, targeting production quality with clean modular architecture.

## Core Value

Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.

## Current Milestone: v1.2 GGUF Native Inference

**Goal:** Hacer funcionar GGUF end-to-end con llama.cpp — misma UI, mismo flujo, misma performance que LiteRT-LM.

**Target features:**
- Descarga de GGUF desde Hugging Face (listar archivos .gguf, progreso, pausa/reanudar)
- Integración llama.cpp vía JNI/NDK (compilar, cargar modelos, generar tokens)
- Inferencia GGUF con streaming de tokens al chat UI
- Backend CPU + Vulkan GPU (detección en runtime, fallback a CPU)
- Cuantizaciones visibles al explorar/descargar modelos GGUF
- Manejo de memoria y OOM graceful (sin límite artificial de tamaño modelo)
- UX transparente: misma experiencia que LiteRT-LM, sin fricción

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
- AI agents / tool use — defer, focus on chat
- Real-time sync across devices — defer
- Paid subscriptions to remote providers built into the app — user brings own API keys

## Context

The Android ecosystem lacks a polished, production-grade app that combines local LLM inference (llama.cpp/GGUF) with remote provider connectivity in a single interface. Existing solutions are either CLI-only, desktop-only (LM Studio), or limited to one provider. There is a growing demand for running LLMs on flagship Android phones with 8-16GB RAM, especially for privacy-sensitive use cases and offline scenarios.

The user is an experienced Android developer with deep knowledge of Kotlin, Compose, and LLM inference. They want all the artifacts needed to build this app: architecture documents, data models, domain interfaces, initial implementations, and a phased roadmap.

v1.1 added LiteRT-LM as a second local inference engine (shipped). LiteRT-LM is Google's production framework that powers on-device GenAI in Chrome, Chromebook Plus, and Pixel Watch. Its Kotlin API provides `Engine → Conversation → sendMessageAsync(Flow)` with native GPU/NPU backends and `.litertlm` model format.

v1.2 implementa inferencia GGUF nativa desde cero con llama.cpp. El pipeline GGUF nunca ha funcionado: la búsqueda en Hugging Face lista modelos pero no muestra archivos .gguf para descargar, y cargar un GGUF manualmente dice "llama not implemented". Este milestone construye el pipeline completo (download → load → streaming chat) con paridad de UX respecto a LiteRT-LM.

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
*Last updated: 2026-05-05 after milestone v1.2 redefinition*
