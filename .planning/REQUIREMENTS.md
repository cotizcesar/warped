# Requirements: Warped

**Defined:** 2026-04-30
**Core Value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Remote Providers

- [ ] **PROV-01**: User can add a remote endpoint by providing a name, base URL, and provider type (OpenAI-compatible, Ollama, LM Studio, llama.cpp server, custom)
- [ ] **PROV-02**: User can edit and delete saved remote endpoints
- [ ] **PROV-03**: User can test connection to a remote endpoint and see success/failure with latency info
- [ ] **PROV-04**: User can list available models from a remote endpoint
- [ ] **PROV-05**: User can select a remote model and chat with streaming token-by-token responses

### Chat

- [ ] **CHAT-01**: User can send a message and receive a streaming response from a selected provider and model
- [ ] **CHAT-02**: User can stop an in-progress generation with a cancel button
- [ ] **CHAT-03**: User can view conversation history and resume previous chats
- [ ] **CHAT-04**: User can configure a system prompt per conversation
- [ ] **CHAT-05**: User can see which provider and model are currently active during chat

### Local Models

- [ ] **LOCL-01**: User can import a GGUF model file from device storage via the system file picker
- [ ] **LOCL-02**: User can load a local GGUF model for inference and receive streaming responses
- [ ] **LOCL-03**: User can stop local generation and unload the model to free memory
- [ ] **LOCL-04**: User can view a list of imported local models with file size, path, and quantization info
- [ ] **LOCL-05**: User can delete a local model from device storage

### Model Acquisition (Hugging Face)

- [ ] **ACQ-01**: User can search for models on Hugging Face and filter by GGUF format
- [ ] **ACQ-02**: User can view model details including file list, sizes, and quantization types (Q2-Q8)
- [ ] **ACQ-03**: User can download a GGUF model file with a foreground progress notification
- [ ] **ACQ-04**: User can pause and resume an in-progress download
- [ ] **ACQ-05**: App validates available storage space before starting a download

### Generation Parameters

- [ ] **PARM-01**: User can configure temperature, top_p, top_k, repeat_penalty, max_tokens, context_size, seed, and threads before generation
- [ ] **PARM-02**: User can save named generation presets and load them for reuse

### Security

- [ ] **SEC-01**: API keys for remote endpoints are stored encrypted via Android Keystore
- [ ] **SEC-02**: User can delete all chat history and conversation data
- [ ] **SEC-03**: User can delete saved API keys and endpoint credentials

### Persistence

- [ ] **PERS-01**: Chat sessions and messages survive app restarts
- [ ] **PERS-02**: Endpoint configurations survive app restarts
- [ ] **PERS-03**: Local model metadata survives app restarts

### Device Awareness

- [ ] **DEV-01**: App warns the user if a selected GGUF model is too large for the device's available RAM
- [ ] **DEV-02**: App displays a clear error message when a model fails to load due to memory or compatibility issues

## v2 Requirements

Deferred to future release.

### Advanced Inference

- **ADV-01**: GPU acceleration via Vulkan delegate for local inference
- **ADV-02**: Local model benchmarks (tokens/second, memory usage)

### Advanced Security

- **ADV-03**: Export/import endpoint configuration (without keys)
- **ADV-04**: Advanced logging mode for technical users

### Polish

- **ADV-05**: Quantization and model size recommendations for specific device profiles
- **ADV-06**: Auto-detect Ollama/LM Studio on local network

## Out of Scope

| Feature | Reason |
|---------|--------|
| Voice input/output | Focus on text LLM chat first; adds audio pipeline complexity |
| Image/multimodal models | Requires different model architecture; text-first MVP |
| AI agents / tool use / function calling | Adds significant complexity; core chat must work first |
| Built-in paid subscriptions | User brings own API keys; no payment integration needed |
| iOS or desktop support | Android-only by design |
| Real-time sync across devices | Not core to single-device LLM workflow |
| Model fine-tuning or training | Inference-only scope |
| RAG / document ingestion | Deferred; chat-first MVP |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| PROV-01 | Phase 1 | Pending |
| PROV-02 | Phase 1 | Pending |
| PROV-03 | Phase 1 | Pending |
| PROV-04 | Phase 1 | Pending |
| PROV-05 | Phase 1 | Pending |
| CHAT-01 | Phase 1 | Pending |
| CHAT-02 | Phase 1 | Pending |
| CHAT-03 | Phase 1 | Pending |
| CHAT-04 | Phase 1 | Pending |
| CHAT-05 | Phase 1 | Pending |
| PERS-01 | Phase 1 | Pending |
| PERS-02 | Phase 1 | Pending |
| SEC-01 | Phase 1 | Pending |
| LOCL-01 | Phase 2 | Pending |
| LOCL-02 | Phase 2 | Pending |
| LOCL-03 | Phase 2 | Pending |
| LOCL-04 | Phase 2 | Pending |
| LOCL-05 | Phase 2 | Pending |
| PERS-03 | Phase 2 | Pending |
| DEV-01 | Phase 2 | Pending |
| DEV-02 | Phase 2 | Pending |
| ACQ-01 | Phase 3 | Pending |
| ACQ-02 | Phase 3 | Pending |
| ACQ-03 | Phase 3 | Pending |
| ACQ-04 | Phase 3 | Pending |
| ACQ-05 | Phase 3 | Pending |
| PARM-01 | Phase 4 | Pending |
| PARM-02 | Phase 4 | Pending |
| SEC-02 | Phase 5 | Pending |
| SEC-03 | Phase 5 | Pending |

**Coverage:**
- v1 requirements: 29 total
- Mapped to phases: 29
- Unmapped: 0 ✓

---
*Requirements defined: 2026-04-30*
*Last updated: 2026-04-30 after initial definition*
