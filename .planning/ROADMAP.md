# Roadmap: Warped

**Created:** 2026-04-30
**Granularity:** Coarse (3-5 phases)
**Mode:** Auto (YOLO)

## Phase Overview

| # | Phase | Goal | Requirements | Plans | Status |
|---|-------|------|--------------|-------|--------|
| 1 | Foundation & Remote Chat | Chat with remote models via OpenAI-compatible endpoints with streaming and persistence | PROV-01..05, CHAT-01..05, PERS-01, PERS-02, SEC-01 | 0/? | ○ Pending |
| 2 | Local Inference | Run GGUF models on-device via llama.cpp with memory awareness | LOCL-01..05, PERS-03, DEV-01, DEV-02 | 0/? | ○ Pending |
| 3 | Model Acquisition | Search and download GGUF models from Hugging Face with pause/resume | ACQ-01..05 | 0/? | ○ Pending |
| 4 | Parameters & Presets | Configure generation parameters and save reusable presets | PARM-01, PARM-02 | 0/? | ○ Pending |
| 5 | Security Hardening & Polish | Data deletion controls and final security pass | SEC-02, SEC-03 | 0/? | ○ Pending |

## Phase Details

### Phase 1: Foundation & Remote Chat

**Goal:** Establish the app foundation and deliver remote chat with streaming token-by-token responses from OpenAI-compatible, Ollama, LM Studio, and custom endpoints — with full persistence and encrypted API keys.

**Requirements:** PROV-01, PROV-02, PROV-03, PROV-04, PROV-05, CHAT-01, CHAT-02, CHAT-03, CHAT-04, CHAT-05, PERS-01, PERS-02, SEC-01

**UI hint:** yes

**Success Criteria:**
1. User can add an endpoint (name, URL, provider type), test connectivity, and see latency/success feedback within 3 seconds
2. User can browse and select a model from a remote endpoint, then type a message and see tokens stream in real-time in the chat UI
3. User can stop mid-generation with a cancel button and the partial response is preserved
4. User can close and reopen the app and find all previous conversations, messages, and endpoint configurations intact
5. API keys are never exposed in logs, UI, or plaintext storage — verified via `adb shell` inspection

---

### Phase 2: Local Inference

**Goal:** Enable on-device GGUF model inference via llama.cpp JNI bridge, with import from storage, streaming chat, cancel/stop, model management, RAM-based warnings, and graceful error handling.

**Requirements:** LOCL-01, LOCL-02, LOCL-03, LOCL-04, LOCL-05, PERS-03, DEV-01, DEV-02

**UI hint:** yes

**Success Criteria:**
1. User can import a GGUF file from device storage via the system file picker and see it appear in the local model list with size, path, and quantization metadata
2. User can load a local model and chat with streaming token-by-token responses that appear at similar latency to LM Studio desktop
3. User can stop local generation and the model unloads, freeing device memory (verifiable via `adb shell dumpsys meminfo`)
4. User receives a clear warning before loading a model that exceeds 80% of available device RAM
5. Local model metadata and import state survive app restart and process death

---

### Phase 3: Model Acquisition

**Goal:** Let users discover and download GGUF models directly from Hugging Face in-app — with search, GGUF filtering, file details, foreground download notifications, pause/resume, and storage validation.

**Requirements:** ACQ-01, ACQ-02, ACQ-03, ACQ-04, ACQ-05

**UI hint:** yes

**Success Criteria:**
1. User can search Hugging Face for models, filter to GGUF-only, and see relevant results with model names and descriptions
2. User can view a model's file list showing per-file size and quantization type (Q2 through Q8) before deciding to download
3. User sees a foreground notification with percentage progress during download, and the download continues if the app is backgrounded
4. User can pause a download, close the app, return later, and resume from the same byte offset without data loss
5. User receives a warning and the download is blocked if free storage is less than 110% of the model file size

---

### Phase 4: Parameters & Presets

**Goal:** Expose all v1 generation parameters (temperature, top_p, top_k, repeat_penalty, max_tokens, context_size, seed, threads) with sensible defaults, and allow saving/loading named presets.

**Requirements:** PARM-01, PARM-02

**UI hint:** yes

**Success Criteria:**
1. User can adjust any generation parameter via sliders or number inputs and see changes reflected in the next generation
2. User can save current parameter values as a named preset (e.g., "Creative", "Precise") and load it from a list
3. Parameter changes and preset selections apply identically to both local and remote chat sessions

---

### Phase 5: Security Hardening & Polish

**Goal:** Provide user-facing data deletion controls (chat history, API keys), ensure all sensitive data is purged, and perform final security review against checklist.

**Requirements:** SEC-02, SEC-03

**UI hint:** yes

**Success Criteria:**
1. User can delete all chat history from settings and verify via Room inspection that conversations and messages are permanently removed
2. User can delete stored API keys individually or all at once, and the Keystore entries are purged
3. Deleting chat history or API keys does not affect endpoint configs, local models, or presets (targeted deletion)

## Coverage

| v1 Requirements | Mapped | Unmapped |
|-----------------|--------|----------|
| 30 | 30 ✓ | 0 |

All v1 requirements are traced to exactly one phase in the [Traceability table](REQUIREMENTS.md#traceability).

---

*Roadmap created: 2026-04-30*
