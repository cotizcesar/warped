# Roadmap: Warped v2.0 — Gallery Convergence & Performance Overhaul

**Created:** 2026-06-05
**Phases:** 5 (continuing from Phase 39)
**Total requirements:** 53
**Granularity:** coarse (per `config.json`)
**Reference implementation:** [google-ai-edge/gallery](https://github.com/google-ai-edge/gallery) v1.0.16 (23.6k stars, 91.9% Kotlin)

---

## Historical Note — v1.8 LiteRT Update, Bugfix Round & Recommended Models

v1.8 shipped 2026-06-05 with 5 phases (35–39) and 30 requirements. The full archive lives at [`.planning/milestones/v1.8-ROADMAP.md`](milestones/v1.8-ROADMAP.md). Highlights: LiteRT-LM 0.12.0 → 0.13.0 upgrade, multi-turn conversation context fix (LRT-02), HF model browser bugfixes (HF-01..09), chat UI redesign with pill input and inline model picker (CHAT-01..08), endpoint CRUD + LM Studio native v1 (ENDPT-01..07), and a hand-curated Recommended models section (REC-01..03). Three verification gaps remain in Phase 35 (compile gate, conversation-reuse unit test, multi-turn smoke test) — user/CI verification required.

**Cumulative state entering v2.0:** 39 phases shipped, 234 requirements delivered across v1.0–v1.8. v2.0 starts at **Phase 40** and does **not** reset phase numbering.

---

## Phases

- [x] **Phase 40: Runtime & Allowlist Foundation** [P0, keystone] — Unify local + remote chat behind `LlmModelHelper`, ship `assets/model_allowlist.json`, apply mechanical stack bumps, add R8 keep rules ✓ 2026-06-06 (8 plans)
- [x] **Phase 41: Thinking Mode + Model Benchmark** [P1] — Surface model reasoning trace as collapsible panel; ship on-device init/prefill/decode/peak-memory benchmark ✓ 2026-06-06 (5 plans)
- [ ] **Phase 42: Prompt Lab** [P1] — Side-by-side single-turn prompt workspace with 5–8 curated templates
- [ ] **Phase 43: Performance Convergence** [P0, cross-cutting] — File-by-line perf overhaul: mmap-only cache, Compose state split, AppLifecycleProvider, splash, Macrobenchmark baseline
- [ ] **Phase 44: Agent Skills Lite** [P2, optional — defer to v2.1 if scope tight] — 3–5 built-in Kotlin `@Tool` skills surfaced as chips under chat input

---

## Phase Details

### Phase 40: Runtime & Allowlist Foundation

**Goal:** Unify the local LiteRT-LM and remote LM Studio chat paths behind one streaming API, ship a Gallery-schema model allowlist, adopt the proven manifest and library bumps, and harden R8 — so every v2.0 feature shares a single inference surface and the app stops stalling on first chat.

**Depends on:** Nothing (Phase 39 complete; keystone for Phases 41–44)

**Requirements:** RUNTIME-01..12, CACHE-01..03 (15 reqs)

**Success Criteria** (what must be TRUE):
1. User can chat with a local `.litertlm` model OR a remote LM Studio endpoint using the same chat screen; the provider is transparent in the UI — there is no separate "local" vs "remote" chat code path visible to the user
2. The "Recommended" list in the Hugging Face browser is now driven by `assets/model_allowlist.json` with `name`, `displayName`, `modelFile`, `sizeInBytes`, `capabilities`, `llmPromptTemplates`, `taskTypes` — and reflects the curated entries from the asset
3. The app cold-starts without a white flash; a SplashScreen is shown and dismisses cleanly into the first Compose frame
4. Toggling dark mode in system settings no longer causes a visible Activity recreate; the chat screen re-themes in place
5. The release APK still loads a `.litertlm` model successfully (no `UnsatisfiedLinkError`, no `Serializer not found`); the dependency audit returns empty for kapt, Firebase, Moshi, Gson, kotlin-reflect, Ktor, mcp, TFLite, mlkit-genai, AppAuth, compose-richtext, CameraX, Proto DataStore — and the CI step that runs this audit fails if any of those reappear

**UI hint**: yes

---

### Phase 41: Thinking Mode + Model Benchmark

**Goal:** Surface the model's reasoning trace as a collapsible panel during chat (gated by per-model allowlist capability), and ship an on-device benchmark screen that measures init time, prefill tok/s, decode tok/s, and peak memory for any downloaded model — with results persisted in Room.

**Depends on:** Phase 40 (LlmModelHelper interface + capability allowlist)

**Requirements:** THINK-01..07, BENCH-01..06 (13 reqs)

**Success Criteria** (what must be TRUE):
1. When a user selects a model with `llm_thinking` capability (e.g., DeepSeek-R1-Distill), the chat input bar shows a "Thinking ON/OFF" chip; toggling it persists the choice for the next user message
2. While a thinking-capable model is generating, a collapsible "Thinking..." panel appears above the assistant's reply; tapping the panel expands it to show the model's full reasoning trace
3. After the response completes, the Thinking panel is collapsed by default; the user can tap to re-expand and re-read the trace
4. The benchmark screen lets the user pick a downloaded model and a config (temperature, top-k, max tokens), then start a benchmark; the screen shows init time (ms), prefill tok/s, decode tok/s, and peak memory (MB) when complete
5. Benchmark results are persisted in Room across app restarts and reviewable on a results viewer screen — the app upgrades from v1.8 to v2.0 without losing the user's existing chat history (Room migration passes the `MigrationTest` round-trip)

**UI hint**: yes

---

### Phase 42: Prompt Lab

**Goal:** Add a side-by-side single-turn prompt workspace with 5–8 curated templates (rewrite, summarize, extract key points, code-explain, translate, sentiment, table-to-json) that runs against the active model — separate from the multi-turn chat.

**Depends on:** Phase 40 (LlmModelHelper interface)

**Requirements:** PROMPT-01..06 (6 reqs)

**Success Criteria** (what must be TRUE):
1. The bottom nav has a new "Prompt Lab" entry; tapping it opens a side-by-side layout with the prompt input on the left and the model's output on the right
2. The user can pick from 5–8 curated templates; selecting one fills the input with that template's prompt, which the user can edit before running
3. Tapping "Run" produces the model's response on the right side; if the output contains code blocks, they render with Warped's existing syntax highlighting (the same renderer as chat)
4. Each run is single-turn — there is no conversation history; clearing the input and output starts a fresh run
5. The prompt lab uses the same active model as the chat (selecting a different model in the picker affects both screens)

**UI hint**: yes

---

### Phase 43: Performance Convergence

**Goal:** Make the app measurably faster, smoother, and lighter on resources — across cold start, time-to-first-token, UI smoothness, memory, and storage — with Macrobenchmark numbers recorded in `BENCHMARKS.md` and a `MessageBubble` that no longer recomposes on every streaming token.

**Depends on:** Phase 40 (LlmModelHelper interface defined; can start as soon as the interface signature lands, even before Phase 40 ships end-to-end)

**Requirements:** PERF-01..13 (13 reqs)

**Success Criteria** (what must be TRUE):
1. The app's on-disk model cache footprint is reduced by 1–3 GB per model — `cacheDir/litertlm/<version>/` is mmap-only with no file copy; verified via `adb shell du -sh` on `cacheDir`
2. Chat screen during token streaming stays at a smooth 60fps with no visible jank — verified in Layout Inspector that `MessageBubble` is **skipped** (not recomposed) on each streaming token
3. Cold start is under 1.5s on a Pixel 7 reference device; warm start under 800ms — numbers recorded in `BENCHMARKS.md` via Macrobenchmark
4. In-app downloads suppress the foreground notification when the app is in the foreground; the notification only appears once the user backgrounds the app (driven by `AppLifecycleProvider`)
5. Scrolling a 1,000-message conversation in chat history is fluid with no dropped frames, thanks to the new `messages(conversation_id, created_at)` composite index verified by `EXPLAIN QUERY PLAN`

**UI hint**: yes

---

### Phase 44: Agent Skills Lite

**Goal:** Ship 3–5 built-in Kotlin `@Tool`-annotated skills (calculator, JSON-formatter, current-time, code-block-extractor, text-summarizer-template) that the model can invoke during chat — surfaced as chips under the chat input. Optional P2; defer to v2.1 if v2.0 timeline is tight.

**Depends on:** Phase 40 (LlmModelHelper extended with `tools: List<ToolProvider>`)

**Requirements:** SKILLS-01..06 (6 reqs)

**Success Criteria** (what must be TRUE):
1. Under the chat input bar, the user sees 3–5 skill chips (e.g., "Calculator", "Format JSON", "Current Time", "Extract Code Blocks", "Summarize")
2. Tapping a skill chip inserts a brief instruction into the input (e.g., "Use the calculator tool for arithmetic:") — the user can edit before sending
3. When the model calls a skill during generation, the skill's output appears inline in the assistant's response with a small tool label (e.g., "[calculator] 42")
4. Skills work with both local LiteRT-LM (via LiteRT-LM 0.13.1 `ToolProvider`) AND remote LM Studio (via the `/api/v1/chat` `tools` field) — toggling providers preserves the same skill behavior
5. Skills are discoverable through `SkillRepository` and can be enabled/disabled in Settings → Skills (default: all enabled)

**UI hint**: yes

---

## Phase Summary

| # | Phase | Priority | Depends on | Reqs | Success Criteria |
|---|-------|----------|------------|------|------------------|
| 40 | Runtime & Allowlist Foundation | **P0** keystone | — | RUNTIME-01..12, CACHE-01..03 (15) | 5 |
| 41 | Thinking Mode + Model Benchmark | P1 | Phase 40 | THINK-01..07, BENCH-01..06 (13) | 5 |
| 42 | Prompt Lab | P1 | Phase 40 | PROMPT-01..06 (6) | 5 |
| 43 | Performance Convergence | **P0** cross-cutting | Phase 40 (interface only) | PERF-01..13 (13) | 5 |
| 44 | Agent Skills Lite | P2 optional | Phase 40 | SKILLS-01..06 (6) | 5 |

**Total: 5 phases, 53 requirements, 25 success criteria**

### Dependency Graph

```
Phase 40 (keystone)
  ├── Phase 41 (Thinking + Benchmark) ─┐
  ├── Phase 42 (Prompt Lab)            ├── all can run in parallel
  ├── Phase 43 (Performance, P0) ──────┘   (43 needs only interface signature)
  └── Phase 44 (Skills, P2, optional)
```

- **Phase 40 is keystone** — every other phase depends on the `LlmModelHelper` interface. Must land first.
- **Phase 43 can run parallel to 41/42** once Phase 40's interface signature is defined (it touches engine plumbing, manifest, and Compose state — not the new `LlmModelHelper` surface).
- **Phase 41 and Phase 42 are independent** — both depend on Phase 40 but not on each other.
- **Phase 44 is optional P2** — XL scope, defer to v2.1 if v2.0 timeline is tight.

---

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 40. Runtime & Allowlist Foundation | 8/8 | ✓ Complete | 2026-06-06 |
| 41. Thinking Mode + Model Benchmark | 0/TBD | Not started | — |
| 42. Prompt Lab | 0/TBD | Not started | — |
| 43. Performance Convergence | 0/TBD | Not started | — |
| 44. Agent Skills Lite | 0/TBD | Not started | — |

---

## Gallery Anti-Patterns Explicitly Rejected

The v2.0 plan adopts Gallery's *interface* and *manifest* patterns. It does **NOT** adopt Gallery's tech-debt surface. Each anti-pattern has a verification gate in Phase 40's dependency audit (RUNTIME-12):

- kapt for Hilt compiler → Warped stays KSP-only (builds 2–5× faster)
- Moshi + Gson + kotlinx-serialization (three JSON libs) → Warped stays kotlinx-serialization only
- `kotlin-reflect` → not added (2.5 MB APK savings)
- Firebase BOM / Analytics / Messaging → not added (PROJECT.md "out of scope")
- Ktor + MCP Kotlin SDK → not added (Warped has Retrofit + OkHttp; MCP via LM Studio REST, not the SDK)
- `compose-richtext` + `commonmark` → not added (Warped has custom `MarkdownText` v1.6)
- Proto DataStore → not added for benchmark history (Room instead, per FEATURES.md)
- CameraX → not added (no camera feature)
- `mlkit-genai-prompt` / AICore → not added (Pixel-only preview)
- `play-services-tflite-*` → not added (Warped uses LiteRT-LM AAR directly, removed in v1.5)
- AppAuth → not added (no OAuth; HF models are public, LM Studio uses API keys via EncryptedSharedPreferences)
- JS webview skill runtime (Gallery's full Skills system) → not added (Agent Skills Lite is Kotlin-only, no webview)
- `extractNativeLibs="true"` → stays `false` (10–20 MB APK savings)

**Verification (Phase 40 exit criterion, RUNTIME-12):**
```bash
./gradlew :app:dependencies --configuration kapt | grep -v "^$"            # must be empty
./gradlew :app:dependencies | grep -E "(firebase|moshi|gson|kotlin-reflect|ktor|mcp|tflite|mlkit-genai|appauth|compose-richtext|cameraX|datastore.*proto)"  # must be empty
```

---

## Out of Scope (deferred to v2.1+, tracked in REQUIREMENTS.md)

- **LMSTUDIO-MCP-01** — LM Studio MCP Bridge (separate phase, requires MCP Kotlin SDK or custom JSON-RPC client)
- **LRT-04** — Speculative Decoding toggle for capable models
- **BENCH-VIEW-01** — Benchmark history viewer (needs 1+ weeks of accumulated results)
- **DEEPLINK-01** — Deep links `warped://chat/<id>` (made easy by Phase 40's type-safe nav)
- **LRT-05** — Vulkan GPU acceleration for LiteRT-LM (when stable)
- **LRT-06** — Samsung Hexagon NPU acceleration for LiteRT-LM (when stable)

Plus the long-standing PROJECT.md out-of-scope items: voice I/O, multimodal, autonomous tool use, cross-device sync, paid subscriptions, GGUF/llama.cpp (removed in v1.5), multi-tab browser (removed in v1.5), certificate pinning, Play Integrity / root detection.

---

*Roadmap created: 2026-06-05*
*Reference: [google-ai-edge/gallery](https://github.com/google-ai-edge/gallery) v1.0.16*
*See also: [`.planning/research/SUMMARY.md`](research/SUMMARY.md) for full research synthesis*
