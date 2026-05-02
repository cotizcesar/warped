# Requirements: Warped

**Defined:** 2026-04-30
**Core Value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.

## v1.0 Requirements ✓

Requirements for initial release. All 30 verified and complete.

### Remote Providers

- [x] **PROV-01**: User can add a remote endpoint by providing a name, base URL, and provider type (OpenAI-compatible, Ollama, LM Studio, llama.cpp server, custom)
- [x] **PROV-02**: User can edit and delete saved remote endpoints
- [x] **PROV-03**: User can test connection to a remote endpoint and see success/failure with latency info
- [x] **PROV-04**: User can list available models from a remote endpoint
- [x] **PROV-05**: User can select a remote model and chat with streaming token-by-token responses

### Chat

- [x] **CHAT-01**: User can send a message and receive a streaming response from a selected provider and model
- [x] **CHAT-02**: User can stop an in-progress generation with a cancel button
- [x] **CHAT-03**: User can view conversation history and resume previous chats
- [x] **CHAT-04**: User can configure a system prompt per conversation
- [x] **CHAT-05**: User can see which provider and model are currently active during chat

### Local Models

- [x] **LOCL-01**: User can import a GGUF model file from device storage via the system file picker
- [x] **LOCL-02**: User can load a local GGUF model for inference and receive streaming responses
- [x] **LOCL-03**: User can stop local generation and unload the model to free memory
- [x] **LOCL-04**: User can view a list of imported local models with file size, path, and quantization info
- [x] **LOCL-05**: User can delete a local model from device storage

### Model Acquisition (Hugging Face)

- [x] **ACQ-01**: User can search for models on Hugging Face and filter by GGUF format
- [x] **ACQ-02**: User can view model details including file list, sizes, and quantization types (Q2-Q8)
- [x] **ACQ-03**: User can download a GGUF model file with a foreground progress notification
- [x] **ACQ-04**: User can pause and resume an in-progress download
- [x] **ACQ-05**: App validates available storage space before starting a download

### Generation Parameters

- [x] **PARM-01**: User can configure temperature, top_p, top_k, repeat_penalty, max_tokens, context_size, seed, and threads before generation
- [x] **PARM-02**: User can save named generation presets and load them for reuse

### Security

- [x] **SEC-01**: API keys for remote endpoints are stored encrypted via Android Keystore
- [x] **SEC-02**: User can delete all chat history and conversation data
- [x] **SEC-03**: User can delete saved API keys and endpoint credentials

### Persistence

- [x] **PERS-01**: Chat sessions and messages survive app restarts
- [x] **PERS-02**: Endpoint configurations survive app restarts
- [x] **PERS-03**: Local model metadata survives app restarts

### Device Awareness

- [x] **DEV-01**: App warns the user if a selected GGUF model is too large for the device's available RAM
- [x] **DEV-02**: App displays a clear error message when a model fails to load due to memory or compatibility issues

## v1.1 Requirements

Requirements for LiteRT-LM integration. Each maps to roadmap phases.

### LiteRT-LM Engine & Chat

- [x] **LITE-01**: App includes litertlm-android Maven dependency and compiles successfully
- [x] **LITE-02**: BackendDetector probes GPU availability and falls back to CPU automatically
- [x] **LITE-03**: LiteRTLmEngine wraps Engine lifecycle (initialize, createConversation, close) with thread safety
- [x] **LITE-04**: Room schema migration adds model_format and engine_type columns to models table
- [ ] **LITE-05**: LiteRTLmProvider implements LlmProvider with streaming chat via Conversation.sendMessageAsync(Flow)
- [ ] **LITE-06**: Input sanitization prevents Unicode/LaTeX native crashes before reaching the engine
- [ ] **LITE-07**: Generation parameters map correctly to LiteRT-LM's SamplerConfig
- [x] **LITE-08**: AndroidManifest declares libOpenCL for GPU backend with required="false"

### Model Acquisition

- [ ] **ACQ-06**: User can search Hugging Face for .litertlm models filtered by litert-community org
- [ ] **ACQ-07**: User can view .litertlm model details including file size and format info
- [ ] **ACQ-08**: User can download .litertlm model files with foreground progress notification
- [ ] **ACQ-09**: User can pause and resume .litertlm model downloads
- [ ] **ACQ-10**: User can import local .litertlm files from device storage

### UI Integration

- [ ] **UI-01**: Models screen has separate GGUF and LiteRT-LM tabs via TabRow
- [ ] **UI-02**: Model list shows format badge (GGUF/LiteRT-LM) on each model card
- [ ] **UI-03**: Active backend (CPU/GPU) is displayed during chat for LiteRT-LM models
- [ ] **UI-04**: User can view and delete downloaded LiteRT-LM models
- [ ] **UI-05**: LiteRT-LM models appear in the model selector for chat sessions

### Generation Parameters

- [ ] **PARM-03**: User can configure LiteRT-LM specific parameters (temperature, topK, topP, seed)
- [ ] **PARM-04**: Unsupported parameters (repeat_penalty, context_size, threads) are greyed out for LiteRT-LM
- [ ] **PARM-05**: Presets support both GGUF and LiteRT-LM parameter models

### Polish & Hardening

- [x] **POL-01**: EngineManager enforces mutual exclusion (only one local engine loaded at a time)
- [x] **POL-02**: App warns if available RAM is insufficient for the selected .litertlm model
- [ ] **POL-03**: Cached model loading via cacheDir for faster subsequent loads
- [ ] **POL-04**: Defensive error recovery reinitializes engine on "Engine not alive" errors
- [ ] **POL-05**: Memory is released when the app is backgrounded (onTrimMemory handling)

## v2 Requirements

Deferred to future release.

### Advanced Inference

- **ADV-01**: Local model benchmarks (tokens/second, memory usage) for both GGUF and .litertlm
- **ADV-02**: NPU auto-detection and acceleration for Snapdragon devices

### Advanced Security

- **ADV-03**: Export/import endpoint configuration (without keys)
- **ADV-04**: Advanced logging mode for technical users

### Multi-Modality

- **ADV-05**: Vision input support via LiteRT-LM multi-modality (image attachment, camera capture)
- **ADV-06**: Audio input support via LiteRT-LM audio backend

### Agent Capabilities

- **ADV-07**: Tool use / function calling via LiteRT-LM ToolSet API
- **ADV-08**: Auto-detect Ollama/LM Studio on local network

## Out of Scope

| Feature | Reason |
|---------|--------|
| Voice input/output | Focus on text LLM chat first; adds audio pipeline complexity |
| Image/multimodal models | LiteRT-LM supports it but requires separate vision/audio backends plus UI — defer to v2.x |
| AI agents / tool use / function calling | LiteRT-LM supports ToolSet but adds agent architecture complexity — defer to v2.x |
| Built-in paid subscriptions | User brings own API keys; no payment integration needed |
| iOS or desktop support | Android-only by design |
| Real-time sync across devices | Not core to single-device LLM workflow |
| Model fine-tuning or training | Inference-only scope |
| RAG / document ingestion | Deferred; chat-first MVP |
| Converting GGUF → .litertlm on-device | Formats are fundamentally different; computationally infeasible on mobile |
| Running both engines simultaneously | Memory exhaustion on typical 8-16GB Android devices; mutual exclusion enforced |
| NPU auto-detection in v1.1 | SoC-fragmented; deferred to v2.x after real-world testing |

## v1.0 Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| PROV-01 | Phase 1 | Complete |
| PROV-02 | Phase 1 | Complete |
| PROV-03 | Phase 1 | Complete |
| PROV-04 | Phase 1 | Complete |
| PROV-05 | Phase 1 | Complete |
| CHAT-01 | Phase 1 | Complete |
| CHAT-02 | Phase 1 | Complete |
| CHAT-03 | Phase 1 | Complete |
| CHAT-04 | Phase 1 | Complete |
| CHAT-05 | Phase 1 | Complete |
| PERS-01 | Phase 1 | Complete |
| PERS-02 | Phase 1 | Complete |
| SEC-01 | Phase 1 | Complete |
| LOCL-01 | Phase 2 | Complete |
| LOCL-02 | Phase 2 | Complete |
| LOCL-03 | Phase 2 | Complete |
| LOCL-04 | Phase 2 | Complete |
| LOCL-05 | Phase 2 | Complete |
| PERS-03 | Phase 2 | Complete |
| DEV-01 | Phase 2 | Complete |
| DEV-02 | Phase 2 | Complete |
| ACQ-01 | Phase 3 | Complete |
| ACQ-02 | Phase 3 | Complete |
| ACQ-03 | Phase 3 | Complete |
| ACQ-04 | Phase 3 | Complete |
| ACQ-05 | Phase 3 | Complete |
| PARM-01 | Phase 4 | Complete |
| PARM-02 | Phase 4 | Complete |
| SEC-02 | Phase 5 | Complete |
| SEC-03 | Phase 5 | Complete |

## v1.1 Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| LITE-01 | Phase 6 | Complete |
| LITE-02 | Phase 6 | Complete |
| LITE-03 | Phase 6 | Complete |
| LITE-04 | Phase 6 | Complete |
| LITE-05 | Phase 7 | Pending |
| LITE-06 | Phase 7 | Pending |
| LITE-07 | Phase 7 | Pending |
| LITE-08 | Phase 6 | Complete |
| ACQ-06 | Phase 8 | Pending |
| ACQ-07 | Phase 8 | Pending |
| ACQ-08 | Phase 8 | Pending |
| ACQ-09 | Phase 8 | Pending |
| ACQ-10 | Phase 8 | Pending |
| UI-01 | Phase 9 | Pending |
| UI-02 | Phase 9 | Pending |
| UI-03 | Phase 9 | Pending |
| UI-04 | Phase 9 | Pending |
| UI-05 | Phase 9 | Pending |
| PARM-03 | Phase 10 | Pending |
| PARM-04 | Phase 10 | Pending |
| PARM-05 | Phase 10 | Pending |
| POL-01 | Phase 6 | Complete |
| POL-02 | Phase 6 | Complete |
| POL-03 | Phase 9 | Pending |
| POL-04 | Phase 7 | Pending |
| POL-05 | Phase 9 | Pending |

**Coverage:**
- v1.0 requirements: 30 total, 30 mapped ✓
- v1.1 requirements: 26 total, 26 mapped ✓ (25 claimed in plan, 26 present in requirements)
- Mapped to phases: 26 mapped to Phases 6-10

---
*Requirements defined: 2026-04-30*
*Last updated: 2026-05-02 after milestone v1.1 requirements definition*
