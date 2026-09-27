# Feature Research: Warped v2.0 — Gallery Convergence & Performance Overhaul

**Domain:** On-device LLM chat for Android (Kotlin + Jetpack Compose + LiteRT-LM + LM Studio)
**Researched:** 2026-06-05
**Confidence:** HIGH
**Reference implementation:** [google-ai-edge/gallery](https://github.com/google-ai-edge/gallery) v1.0.15 (commit on main, May 2026), 23.6k stars, 91.9% Kotlin

---

## Executive Summary

Google AI Edge Gallery is a near-ideal reference for Warped: same engine (LiteRT-LM), same language (Kotlin), same UI toolkit (Compose), same domain (run LLMs on Android). The 23.6k-star codebase has converged on a clean `LlmModelHelper` runtime abstraction, a `Model` data class with rich metadata (capabilities, task types, accelerators), a `CustomTask` extension model for adding new "use cases" (Chat, Prompt Lab, Agent Chat, Mobile Actions, Tiny Garden), a versioned JSON `model_allowlists/1_0_X.json` pattern, and a Proto-based DataStore for benchmark history. The architectural surface maps ~85% onto Warped's existing structure.

Of Gallery's user-facing features, the text-only subset that fits Warped's "chat + LM Studio v1" scope is small but high-value: **Thinking Mode toggle**, **Model Benchmark**, **Prompt Lab**, and a **function-calling/agent skills** infrastructure that targets LM Studio's tool-calling API (already in scope per PROJECT.md). Multimodal features (Ask Image, Audio Scribe), the game (Tiny Garden), and the calendar/notification system (Mobile Actions) are explicitly out of Warped scope per PROJECT.md. The biggest win is not any single feature — it is **the runtime abstraction layer** (`LlmModelHelper`) that unifies local LiteRT-LM and remote LM Studio behind a single message-streaming API, which enables the other features to slot in cleanly.

A "performance overhaul" is the second half of v2.0 and is largely orthogonal to the feature ports. The 6 axes to attack — cold start, time-to-first-token, UI smoothness, memory footprint, APK size, network efficiency — each map to concrete files in the codebase. The companion STACK.md, ARCHITECTURE.md, and PITFALLS.md research outputs will surface the specific migration candidates per axis.

---

## Reference: Gallery's Feature Surface (extracted from the repo)

| Gallery Feature | Source in repo | Mapping to Warped v2.0 |
|-----------------|----------------|------------------------|
| **AI Chat with Thinking Mode** | `capabilities: ["llm_thinking"]` in `model_allowlists/1_0_15.json`; `Message` with optional thinking content from `com.google.ai.edge.litertlm` | Tier 1 differentiator |
| **Model Benchmark** | `ui/benchmark/`, `BenchmarkResultsSerializer.kt`, Proto `BenchmarkResults` stored in DataStore | Tier 1 differentiator |
| **Prompt Lab** | `ui/llmsingleturn/` (Screen, ViewModel, `PromptTemplatesPanel.kt`, `PromptTemplateConfigs.kt`) | Tier 1 differentiator |
| **Agent Skills** | `customtasks/agentchat/`, `SkillsSerializer.kt`, `IntentHandler.kt`, `skills/built-in/*/SKILL.md` | Tier 2 differentiator (Lite variant) |
| **MCP (Model Context Protocol)** | `mcp/README.md` (experimental); G4-E4B recommended | Tier 2 (LM Studio MCP bridge) |
| **LlmModelHelper runtime abstraction** | `runtime/LlmModelHelper.kt` | Tier 1 — **enables all of the above** |
| **Model allowlist (versioned JSON)** | `model_allowlists/1_0_X.json`, `model_allowlist.json` legacy | Tier 1 — improves existing v1.8 list |
| **Multimodal Ask Image / Audio Scribe** | `ui/llmchat/` + `Bitmap`/`ByteArray` inputs in `LlmModelHelper.runInference` | **Out of scope** (PROJECT.md) |
| **Mobile Actions (native intents)** | `customtasks/mobileactions/`, `IntentHandler.kt` (send_email, send_sms, calendar) | **Out of scope** (PROJECT.md — autonomous tool use deferred) |
| **Tiny Garden (game)** | `customtasks/tinygarden/`, FunctionGemma 270m | **Out of scope** (novelty) |
| **Scheduled Notifications** | `notifications/`, deep-link to chat | **Out of scope** (notification system complexity) |
| **AICore system service** | `runtime/aicore/` (Android 14+ system LLM) | **Out of scope** (narrow device support) |
| **"Best for" model pinning per task** | `bestForTaskTypes` field in allowlist | Skip — adds curation complexity |
| **Multi-tab model browser** | Removed in v1.5 of Warped | Skip — explicit OOS |

---

## Feature Landscape for Warped v2.0

### Table Stakes (v2.0 must include — competitive baseline with Gallery)

| Feature | Why Expected | Complexity | v2.0 deps |
|---------|--------------|------------|-----------|
| **LlmModelHelper runtime abstraction** | Without this, every other feature (Thinking, Benchmark, Skills, MCP) is bolted on to the existing local/remote split. Gallery's clean `interface LlmModelHelper { initialize, runInference, resetConversation, cleanUp, stopResponse }` is the seam. Warped has two parallel chat paths today (local LiteRT-LM, remote LM Studio) that duplicate streaming, history, and tool plumbing. Unify them. | **L** | New `domain/runtime/LlmModelHelper.kt` interface, `data/runtime/LiteRtLlmHelper.kt` (wrap existing `LiteRtLlmEngine`), `data/runtime/LmStudioLlmHelper.kt` (wrap existing `LmStudioProvider`). ChatViewModel swaps implementations. Foundational — must land first. |
| **Thinking Mode toggle** | Newer Gemma 4 E2B/E4B and DeepSeek-R1-Distill models expose `llm_thinking` capability. The `Message` type in `com.google.ai.edge.litertlm` carries a separate `thinking` field. Users running reasoning models (DeepSeek-R1-Distill-Qwen-1.5B is already in Gallery's allowlist) expect to see the model's reasoning. LM Studio's `/api/v1/chat` response includes `reasoning_content` (OpenAI-compatible extended field). Both engines support it. | **M** | LlmModelHelper plumbs `enableThinking` and `partialThinkingResult` (already part of Gallery's `ResultListener` typealias). UI: collapsible "Thinking" panel above the response. Applies to both local and LM Studio remote. |
| **Model Benchmark** | A user with three models on a phone needs to know which is fastest. Gallery's benchmark measures `init time` (ms), `prefill speed` (tok/s), `decode speed` (tok/s), and `peak memory` (MB) and stores results in a Proto-backed DataStore. Without this, the model browser's "best for" is just static text. | **M** | New `ui/benchmark/`, `worker/ModelBenchmarkWorker.kt` (WorkManager, foreground service for long benchmarks), Proto schema for `BenchmarkResults`. Reads from the same model allowlist as the browser. |
| **Prompt Lab (single-turn)** | Gallery ships Prompt Lab as a distinct use case from chat — it is a workspace for one-shot prompts (rewrite, summarize, extract, code-explain) with no conversation state. Warped's existing **presets** system handles generation params but has no curated template library. Adding 5–8 prompt templates per model fills the "I just want to test the model" gap. | **M** | New `ui/promptlab/` (Screen, ViewModel, `PromptTemplateConfigs.kt`, `PromptTemplatesPanel.kt`). Reuses LlmModelHelper. Templates ship as a static asset (Kotlin constant) — no API scraping, fits existing REC-03 pattern. |
| **Versioned Model Allowlist (JSON)** | Gallery ships `model_allowlists/1_0_X.json` keyed by app version. Warped's v1.8 ships a hardcoded Kotlin `RecommendedModels` list. Porting the JSON pattern means future model additions do not require an app release — but for v2.0, the JSON is bundled as an asset (no remote fetch, per v1.8 REC-03 explicit decision). | **S** | New `assets/model_allowlist.json` matching Gallery's schema (`name`, `modelId`, `modelFile`, `description`, `sizeInBytes`, `taskTypes`, `defaultConfig`, `capabilities`). ModelsScreen replaces `RecommendedModels` constant. Folds in `taskTypes`, `capabilities` for future Thinking/Benchmark gating. |
| **Performance/Architecture Convergence (the second half of v2.0)** | "File-by-file, line-by-line optimization" is the explicit v2.0 mandate. Gallery's `GalleryLifecycleProvider.kt`, single-source-of-truth ViewModel state, and DataStore-based `UserDataSerializer.kt` are the architectural anchors to converge toward. Specific audit areas: Hilt module graph (do all `@Provides` belong in app or feature modules?), Room query indexing (the `messages` table needs a `conversation_id + created_at` composite index for fast scroll-rebuild), OkHttp `Interceptor` chain (logger + auth + gzip order), Compose recomposition hot paths (`MessageBubble`, `CodeBlock`), JNI/engine init blocking. | **L** | Across codebase. No new features, but re-architecture of state ownership, removal of legacy v1.0 patterns, and JNI init off the main thread (already done in v1.0 but worth verifying). |

### Differentiators (v2.0 candidates that move Warped forward)

| Feature | Value Proposition | Complexity | v2.0 deps |
|---------|-------------------|------------|-----------|
| **Agent Skills (Lite)** | A Warped user can ask the model "summarize the model card" or "convert to JSON" and the model can call a local tool that runs in-process (no JS webview, no HTTP fetch, no external MCP server). Gallery's full Skills system is huge (JavaScript webview, native intents, MCP, 100+ community skills). Warped's Lite variant ships 3–5 built-in Kotlin skills (calculator, JSON-formatter, text-summarizer-with-template, current-time, code-block-extractor). Each is a simple `@Tool`-annotated function. | **XL** | LiteRT-LM v0.13.1 added `ToolProvider` support. LlmModelHelper already has `tools: List<ToolProvider>` in the interface. New `skills/` package, per-skill class, skill registry. UI: skill chips under the chat input (Gallery pattern). **MCP tool calling via LM Studio is in scope per PROJECT.md** — when the user routes to LM Studio, route tool calls via LM Studio's MCP server config instead. |
| **LM Studio MCP Bridge** | A user running LM Studio v1 with an MCP server (e.g., the official `fetch` server) can use those tools from Warped. Project context (`mcp/README.md`) says "MCP integration is currently experimental" in Gallery, but LM Studio's MCP support is stable. Warped can adopt the experimental status — it's a power-user feature. | **L** | New `data/mcp/` package, MCP server config CRUD (URL + custom headers), tool schema discovery via JSON-RPC over StreamableHTTP, tool routing when LM Studio is the active provider. Reuse LM Studio's `/api/v1/chat` for the actual round-trip (no separate LLM call needed). |
| **Cold start budget** | Warped's v1.0 cold start was acceptable for v1.5 hard launch; the 8-step onboarding wizard + 5 main screens + Hilt graph mean first frame is heavier than necessary. A measured budget: <1.5s to first frame on a Pixel 7, <800ms warm. Defer non-critical Hilt init (`WorkManager` configuration) to `Application.onCreate` background coroutine. | **M** | Touches `GalleryApplication.kt`-equivalent, `Application.onCreate`, Hilt module ordering, baseline profile. Cold start tracking via Macrobenchmark (new project) — already best practice. |
| **Speculative Decoding toggle** | LiteRT-LM v0.13 supports `--enable-speculative-decoding=true` and Gemma 4 E2B/E4B are trained for it. Gallery exposes this as a per-model `capability: ["speculative_decoding"]` flag. In Warped, a chat-screen gear-icon config can toggle speculative decoding per conversation (off by default — adds memory overhead). | **S** | New `Config` in `ui/llmchat/`, litertlm `ConversationOptions` plumbing. Only enabled for models whose allowlist entry has `capabilities: ["speculative_decoding"]`. |
| **Benchmark history viewer** | After running benchmarks, the user can see a line chart of `decode_tok_per_sec` over the last N runs of model X. Gallery has `BenchmarkValueSeriesViewer.kt`. Lightweight analytics feature that makes the benchmark tool feel finished. | **S** | Compose Canvas line chart, Proto `BenchmarkResults.latestRuns` history. |

### Anti-Features (commonly requested from Gallery, but wrong for Warped)

| Feature | Why Tempting | Why Problematic | What to Do Instead |
|---------|--------------|-----------------|-------------------|
| **Ask Image (multimodal vision)** | It's a flagship Gallery feature, demos beautifully. | Explicitly **out of scope** in PROJECT.md: "Image/multimodal models — defer, focus on text LLMs." v2.0 is text-only by design. Adding `Bitmap` inputs to the chat path would also touch `LlmModelHelper.runInference` signature. | Skip entirely. Revisit v3.0+ when multimodal is in scope. |
| **Audio Scribe (audio input + transcription)** | Flagship Gallery feature. | Explicitly **out of scope** per PROJECT.md (voice I/O deferred). Requires audio capture, PCM ByteArray plumbing, and ASR-capable models. | Skip. Voice I/O is a separate v3+ milestone. |
| **Tiny Garden (mini-game using FunctionGemma 270m)** | Quirky, demoable, showcases on-device agentic workflows. | Off-topic for an "LM Studio for mobile" app. Warped is a chat/utility tool, not a games platform. Requires a separate 270M model download and a custom task UI. | Skip. Not part of the chat + LM Studio value proposition. |
| **Mobile Actions (native intents: email, SMS, calendar, contacts)** | Gallery ships flashlight, contacts, email, Wi-Fi, calendar as FunctionGemma 270m actions. | Each intent needs a runtime permission grant, an Android `Intent` dispatcher, a permission-revocation flow, and a confirmation dialog per action. Total scope is **XL** plus UX overhead. PROJECT.md says "AI agents / autonomous tool use — defer, MCP tool calling via LM Studio API is in scope." Mobile Actions is exactly the "autonomous tool use" that's deferred. | Skip. The Agent Skills Lite variant covers text-only tools; the LM Studio MCP bridge covers remote tools. Mobile intent dispatch is a v3+ concern. |
| **Scheduled notifications from chat** | Gallery lets the model schedule a reminder via `IntentHandler.schedule_notification` with deeplink. | Adds `AlarmManager` + `WorkManager` complexity, notification channels, deep-link parser. PROJECT.md scope is text chat; reminders are a personal-assistant feature. | Skip. |
| **JavaScript webview skill runtime** | Gallery's killer Skills demo is "spin a wheel" or "show a map" inside a hidden WebView. | Requires embedding a JS engine (`androidx.webkit`) per skill invocation, security review of arbitrary `index.html` (XSS surface), and a Skills marketplace format. **XL** scope. | Skip. The Agent Skills Lite variant covers text-only tools with zero webview. |
| **Community Skills marketplace (load from URL, browse on GitHub Discussions)** | Gallery's "share with the community" pitch. | Adds URL fetching, ZIP extraction, signature verification. Warped is offline-first — fetching skills over the network violates that. | Skip. If skills land v2.x, ship curated built-ins only. |
| **AICore system service backend** | Gallery has `runtime/aicore/` — uses the system-level LLM on Pixel 8+. | Android 14+ only, Pixel only, still preview per Gallery's allowlist (`aicoreReleaseStage: PREVIEW`). Zero coverage for Warped's broader user base. | Skip. Already tracked as v2 deferred in REQUIREMENTS.md. |
| **"Best for" model pinning per task** | Gallery's `bestForTaskTypes` field shows a "best overall" banner. | Adds curation coupling between the allowlist author and the UI. Warped's "Recommended Models" is already hand-curated. Adding a per-task best-for overlay duplicates that curation. | Skip. The Recommended section is enough. |
| **Multi-tab model browser (Staff Picks, Trending, Recent)** | Gallery's pre-removal UX. | Explicitly removed in v1.5 of Warped: "Staff Picks tab and multi-tab model browser — REMOVED in v1.5. Single search bar replaces tabbed browsing." | Stay removed. |
| **Public "trending" or "most downloaded" model scraping** | Gallery doesn't do this (it ships curated lists). | Explicitly out of scope per REQUIREMENTS.md: "Auto-scraped 'trending' models list — REC-01 uses a hand-curated static list — no API scraping, no ranking algorithms." | Stay out. |
| **Public model allowlist hosting (remote fetch)** | Gallery's `model_allowlists/1_0_15.json` lives in the repo, but is shipped as a bundled asset. | A remote allowlist adds a network dependency and a CDN/security concern. Violates offline-first. | Bundle the JSON as an asset (Gallery's effective behavior in production). |
| **iOS feature parity / cross-platform abstraction** | Gallery also targets iOS, so the codebase has iOS-specific branches. | Warped is Android-only per PROJECT.md: "Platform: Android only (no iOS, no desktop)." | Don't import iOS-only patterns. The `runtime/LlmModelHelper.kt` interface happens to be iOS-portable, but that's incidental, not a goal. |

---

## Feature Dependencies

```
Foundation
└── LlmModelHelper runtime abstraction (Tier 1)
    ├── enables → Thinking Mode (Tier 1)
    │   └── requires ← LiteRT-LM v0.13.1+ for `Message.thinking`; LM Studio `/api/v1/chat` `reasoning_content`
    ├── enables → Model Benchmark (Tier 1)
    │   ├── requires ← LlmModelHelper.resetConversation / cleanUp
    │   └── produces → Proto BenchmarkResults in DataStore
    │       └── consumed by → Benchmark history viewer (S)
    ├── enables → Prompt Lab (Tier 1)
    │   └── requires ← static PromptTemplate assets (Kotlin constant or JSON)
    ├── enables → Agent Skills Lite (Tier 2, differentiator)
    │   ├── requires ← LiteRT-LM v0.13.1 `ToolProvider` for local
    │   ├── requires ← LM Studio MCP support for remote (separate path)
    │   └── produces → skill chips under chat input
    ├── enables → LM Studio MCP Bridge (Tier 2, differentiator)
    │   ├── requires ← Agent Skills Lite (or independent — see notes)
    │   └── requires ← OkHttp + JSON-RPC client
    └── enables → Speculative Decoding toggle (S)
        └── requires ← `capabilities: ["speculative_decoding"]` in allowlist

Architecture
└── Performance Convergence (Tier 1, cross-cutting)
    ├── touches → Hilt graph (Application.onCreate ordering)
    ├── touches → Room (composite index on messages)
    ├── touches → OkHttp (interceptor chain audit)
    ├── touches → Compose (MessageBubble / CodeBlock / ChatInput)
    ├── touches → JNI / engine init (verify off main thread)
    ├── touches → APK size (R8 keep rules, asset compression)
    └── requires ← baseline measurement (Macrobenchmark cold start, TTFT, jank)

Data
└── Model Allowlist JSON (S)
    ├── consumed by → Models & Endpoints screen (existing, refactor)
    ├── consumed by → Model Benchmark (capability gating)
    ├── consumed by → Thinking Mode (capability gating)
    ├── consumed by → Speculative Decoding (capability gating)
    └── produced by → static `assets/model_allowlist.json` (no network)
```

### Dependency notes

- **LlmModelHelper is the keystone.** Without it, Thinking Mode / Benchmark / Prompt Lab / Skills all need their own bespoke plumbing for local vs LM Studio. With it, they share the streaming + tool + state surface.
- **Model Allowlist JSON is independent** of the runtime — it is a static asset, can land in any phase.
- **Agent Skills Lite and LM Studio MCP Bridge are siblings**, not parent/child. Skills Lite is the local-tools story; MCP Bridge is the remote-tools story via LM Studio. They share a UI surface (tool chips) but different transports. Land Skills Lite first, then MCP Bridge.
- **Performance Convergence is parallel** — does not block or depend on the feature ports. Can run as a dedicated phase near the end.
- **Speculative Decoding is a quick win** if the LlmModelHelper exists; defer to v2.1 if scope is tight.

---

## v2.0 Scope Recommendation

**Phase 40 — Runtime & Allowlist Foundation** (Tier 1, 5–7 days)
- Port `LlmModelHelper` interface. Implement `LiteRtLlmHelper` (wrap existing) and `LmStudioLlmHelper` (wrap existing). ChatViewModel accepts `LlmModelHelper` instead of `LiteRtLlmEngine | LmStudioProvider`.
- Replace `RecommendedModels` Kotlin constant with `assets/model_allowlist.json` matching Gallery's schema.

**Phase 41 — Thinking Mode + Benchmark** (Tier 1, 7–10 days)
- Plumb `enableThinking` and `partialThinkingResult` through LlmModelHelper. Render "Thinking" panel above the response in chat.
- Add `ui/benchmark/` with `BenchmarkViewModel`, `BenchmarkScreen`, `BenchmarkResultsViewer`, `BenchmarkValueSeriesViewer`. WorkManager worker for foreground-execution benchmark runs. Proto `BenchmarkResults` in DataStore.
- Wire allowlist `capabilities` to gate Thinking on/off per model.

**Phase 42 — Prompt Lab** (Tier 1, 5–7 days)
- New `ui/promptlab/` mirroring Gallery's `ui/llmsingleturn/`. Side-by-side prompt input + output. 5–8 curated templates per common task (rewrite, summarize, extract-key-points, code-explain, translate, sentiment, table-to-json).
- Reuses LlmModelHelper. No new persistence — single-turn outputs are ephemeral.

**Phase 43 — Performance Convergence** (Tier 1, 7–10 days, cross-cutting)
- Cold start audit + baseline profile.
- Room composite index on `messages(conversation_id, created_at)`.
- Hilt module ordering and lazy init.
- Compose recomposition audit on hot paths (MessageBubble, ChatInput, CodeBlock, ModelsList).
- OkHttp interceptor order + connection pool tuning.
- JNI init path verification.
- Measure: cold start (ms), TTFT (ms), FPS during streaming, peak memory (MB), APK size (MB).

**Phase 44 — Agent Skills Lite (if time permits)** (Tier 2, XL — 10–14 days, optional)
- 3–5 built-in Kotlin skills (calculator, JSON-formatter, text-summarizer-template, current-time, code-block-extractor).
- LlmModelHelper `tools: List<ToolProvider>` plumbed to LiteRT-LM v0.13+.
- Skill chips under chat input. Tap a skill → it gets appended to the system prompt and the model can call it.

**Defer to v2.1+**: LM Studio MCP Bridge (large scope, can land after Skills Lite validates the tool-calling UX), Speculative Decoding toggle (small but peripheral), Benchmark history viewer (small but blocked on enough benchmark data accumulating), Ask Image / Audio Scribe / Mobile Actions / Tiny Garden / Scheduled Notifications (all explicitly out of Warped scope per PROJECT.md).

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|-----------|---------------------|----------|
| LlmModelHelper runtime abstraction | HIGH | HIGH | **P0** — v2.0 keystone |
| Model Allowlist JSON | MEDIUM | LOW | **P0** — v2.0, required for capability gating |
| Thinking Mode toggle | HIGH | MEDIUM | **P1** — v2.0 showcase |
| Model Benchmark | MEDIUM | MEDIUM | **P1** — v2.0, fits mobile use case |
| Prompt Lab | MEDIUM | MEDIUM | **P1** — v2.0, fits preset/template story |
| Performance Convergence | HIGH | HIGH | **P0** — v2.0 explicit mandate |
| Agent Skills Lite | HIGH | XL | **P2** — v2.0 if scope allows, else v2.1 |
| LM Studio MCP Bridge | MEDIUM | L | **P2** — v2.1 |
| Speculative Decoding toggle | LOW | S | **P3** — v2.1 |
| Benchmark history viewer | LOW | S | **P3** — v2.1, needs benchmark data first |
| Ask Image / Audio Scribe | HIGH | XL | **EXCLUDED** — out of scope |
| Mobile Actions (native intents) | MEDIUM | XL | **EXCLUDED** — out of scope |
| Tiny Garden | LOW | L | **EXCLUDED** — off-topic |
| Scheduled Notifications | LOW | L | **EXCLUDED** — out of scope |
| AICore system service | LOW | L | **EXCLUDED** — narrow device support |
| JS webview skill runtime | MEDIUM | XL | **EXCLUDED** — security/complexity |
| Community Skills marketplace | LOW | XL | **EXCLUDED** — violates offline-first |

**Priority key:**
- **P0** = must ship in v2.0
- **P1** = should ship in v2.0
- **P2** = nice to have in v2.0, push to v2.1 if scope tight
- **P3** = v2.1+
- **EXCLUDED** = explicitly out of scope

---

## Competitor / Reference Feature Analysis

| Feature | Google AI Edge Gallery | Warped v1.8 (today) | Warped v2.0 (plan) |
|---------|------------------------|---------------------|---------------------|
| Local LLM runtime | LiteRT-LM v0.13.x | LiteRT-LM v0.12.0 | LiteRT-LM v0.13.x + `LlmModelHelper` interface |
| Remote provider | None — local only | LM Studio v1 | LM Studio v1 (unchanged) |
| Unified chat runtime | `LlmModelHelper` | Two parallel paths | One `LlmModelHelper` with two impls |
| Model allowlist | `model_allowlists/1_0_X.json` (versioned) | Static Kotlin constant | Bundled JSON asset matching Gallery schema |
| Thinking mode | `capabilities: ["llm_thinking"]` + `Message.thinking` | Not exposed | Toggle in chat, collapsible panel |
| Benchmark | Init/prefill/decode/peak memory, proto DataStore | None | Same as Gallery |
| Prompt Lab | Single-turn workspace + 5+ template categories | None (presets are param-only) | Single-turn + curated templates |
| Agent Skills | JS webview + native intents + MCP (XL) | None (out of scope v1.x) | Lite variant: 3–5 built-in Kotlin skills |
| Model browser | Single search + curated list | Single search + Recommended section | Same UX, allowlist-driven |
| Multimodal (image/audio) | Yes — Ask Image, Audio Scribe | Out of scope | Out of scope |
| iOS support | Yes (shared architecture) | No (Android only) | No (Android only) |
| Offline-first | Yes | Yes | Yes (preserved) |

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Gallery feature surface map | **HIGH** | Verified by direct repo exploration: README, `model_allowlist.json`, `model_allowlists/1_0_15.json`, `runtime/LlmModelHelper.kt`, `data/Model.kt`, `customtasks/*/`, `ui/*/`, `mcp/README.md`, `Function_Calling_Guide.md`, `skills/README.md` |
| LiteRT-LM v0.13 capabilities (thinking, tools, speculative decoding) | **HIGH** | Confirmed via Google Developers Blog (May 2026) and LiteRT-LM Maven listings (v0.13.1 current) |
| Gallery benchmark metrics (init, prefill, decode, peak memory) | **HIGH** | Confirmed by Google Cloud Blog "Benchmark LLMs on-device with AI Edge Portal" (May 2026) and `BenchmarkResultsSerializer.kt` in repo |
| Warped v1.8 feature inventory | **HIGH** | Verified against PROJECT.md, REQUIREMENTS.md, STATE.md (39 phases, 234 requirements) |
| Out-of-scope boundaries (multimodal, voice, image) | **HIGH** | Explicit in PROJECT.md "Out of Scope" section and REQUIREMENTS.md "Out of Scope" table |
| `LlmModelHelper.tools: List<ToolProvider>` API stability | **MEDIUM** | Visible in `LlmModelHelper.kt` interface. Specific ToolProvider Kotlin API surface should be verified against the LiteRT-LM Android SDK before committing to Skills Lite scope. |
| `Message.thinking` field name in LiteRT-LM v0.13 | **MEDIUM** | Inferred from Gallery's `ResultListener` typealias signature `(partialResult, done, partialThinkingResult)`. Actual field name on `Message` should be verified in the LiteRT-LM artifact. |
| LM Studio `/api/v1/chat` `reasoning_content` field | **LOW-MEDIUM** | OpenAI-compatible convention; not yet confirmed for LM Studio specifically. Verify in LM Studio's API docs or via integration test before promising Thinking Mode for remote. |
| Gallery benchmark UI scope (the 4 source files: `BenchmarkScreen`, `BenchmarkViewModel`, `BenchmarkResultsViewer`, `BenchmarkValueSeriesViewer`) | **HIGH** | Direct from repo tree |
| `LlmModelHelper.runInference(images: List<Bitmap>, audioClips: List<ByteArray>)` signature | **HIGH** | Direct from source — confirms multimodal is part of the runtime interface (even though we will not use it) |

---

## Open Questions for Phase-Specific Research

These gaps cannot be resolved without deeper investigation during the relevant phase:

1. **Exact `Message.thinking` field in LiteRT-LM 0.13.x** — what is the type, how is it populated by `runInference` callbacks, does LM Studio return it as a top-level `reasoning_content` or nested in `message`? Verify against the LiteRT-LM Android SDK and LM Studio's `/api/v1/chat` response schema.
2. **`ToolProvider` Kotlin API stability** — what does a Warped-built `ToolProvider` look like in code, how is its function schema exposed to the model, and how is the model's tool-call response routed back? Verify by building a 1-skill prototype.
3. **Benchmark memory profiling** — what Android API does Gallery use to measure peak memory (`Debug.MemoryInfo`, `Runtime.totalMemory`, `ActivityManager.MemoryInfo`)? Confirm in `BenchmarkViewModel.kt` source.
4. **Cold start baseline numbers on a typical device** — needs a Macrobenchmark run on a Pixel 6/7/8 reference device. Defer to Phase 43 measurement phase.
5. **MCP server discovery and JSON-RPC over StreamableHTTP** — Gallery's implementation is private. Verify the `modelcontextprotocol` Kotlin SDK or implement a minimal client from spec.
6. **Model allowlist schema evolution** — Gallery's `1_0_15.json` has fields Warped doesn't need (`ios_*`, `aicore*`, `extraDataFiles`). Decide: full schema copy or leaner Warped-specific subset. Lean toward a Warped-specific subset for clarity.
7. **Prompt Lab template content** — which 5–8 templates best cover LM Studio + local chat use cases? "Rewrite" and "Summarize" are universal; "Code explain" fits Warped's developer-friendly posture; "Extract key points" fits productivity. Decide during phase planning.

---

## Sources

### Primary (HIGH confidence)
- [google-ai-edge/gallery repository](https://github.com/google-ai-edge/gallery) — repo root
- [Android source tree](https://github.com/google-ai-edge/gallery/tree/main/Android/src/app/src/main/java/com/google/ai/edge/gallery) — `runtime/`, `data/`, `ui/`, `customtasks/`, `worker/`, `notifications/`
- [model_allowlist.json](https://github.com/google-ai-edge/gallery/blob/main/model_allowlist.json) — legacy allowlist format
- [model_allowlists/1_0_15.json](https://github.com/google-ai-edge/gallery/raw/refs/heads/main/model_allowlists/1_0_15.json) — current versioned allowlist with capabilities and task types
- [runtime/LlmModelHelper.kt](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/runtime/LlmModelHelper.kt) — runtime abstraction
- [data/Model.kt](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/data/Model.kt) — Model data class, RuntimeType, capabilities
- [customtasks/agentchat/IntentHandler.kt](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/IntentHandler.kt) — native intent dispatch (Mobile Actions, send_email, etc.)
- [customtasks/mobileactions/Actions.kt](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/mobileactions/Actions.kt) — FunctionGemma 270m action types
- [BenchmarkResultsSerializer.kt](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/BenchmarkResultsSerializer.kt) — Proto DataStore pattern for benchmark persistence
- [UserDataSerializer.kt](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/UserDataSerializer.kt) — Proto DataStore pattern for user state
- [mcp/README.md](https://github.com/google-ai-edge/gallery/blob/main/mcp/README.md) — Model Context Protocol integration guide
- [skills/README.md](https://github.com/google-ai-edge/gallery/blob/main/skills/README.md) — Agent Skills system documentation
- [Function_Calling_Guide.md](https://github.com/google-ai-edge/gallery/blob/main/Function_Calling_Guide.md) — extending Gallery with custom FunctionGemma actions

### Secondary (MEDIUM confidence)
- [Google Developers Blog — "Blazing fast on-device GenAI with LiteRT-LM"](https://developers.googleblog.com/blazing-fast-on-device-genai-with-litert-lm/) (May 2026) — speculative decoding, MTP, accelerator details
- [LiteRT-LM Maven listing](https://libraries.io/maven/com.google.ai.edge.litertlm%3Alitertlm-android) — v0.13.1 release notes, ToolProvider support
- [Google Cloud Blog — "Benchmark LLMs on-device with AI Edge Portal"](https://cloud.google.com/blog/products/ai-machine-learning/benchmark-llms-on-device-with-ai-edge-portal) (May 2026) — confirms benchmark metrics: init time, prefill, decode, peak memory
- [LiteRT-LM Overview](https://developers.google.cn/edge/litert-lm/overview) — Gemma 4 E2B/E4B benchmark table (S26 Ultra: 52 tok/s GPU decode, 0.3s TTFT)
- [DEV.to — "Gemma 4 on Android: Tricks for Faster On-Device Inference"](https://dev.to/samdude/gemma-4-on-android-tricks-for-faster-on-device-inference-3kj5) (May 2026) — CPU/GPU/NPU backend selection, fallback behavior

### Warped internal (HIGH confidence)
- [PROJECT.md](.planning/PROJECT.md) — current state, v2.0 milestone, out-of-scope boundaries
- [REQUIREMENTS.md](.planning/REQUIREMENTS.md) — v1.8 feature surface, deferred v2 features
- [STATE.md](.planning/STATE.md) — current phase structure
- [research/STACK.md](.planning/research/STACK.md) — confirmed tech stack (Kotlin 2.1.10, Compose BOM 2025.04, Hilt 2.59.2, OkHttp 4.12, LiteRT-LM)

---

*Feature research for: Warped v2.0 Gallery Convergence & Performance Overhaul*
*Researched: 2026-06-05*
*Reference: google-ai-edge/gallery v1.0.15 (May 2026), 23.6k stars, 91.9% Kotlin*
