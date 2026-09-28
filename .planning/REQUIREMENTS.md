# Requirements: Warped — v2.4 Agentic Web

**Defined:** 2026-09-28
**Core Value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.

## v2.4 Requirements

The model itself searches and fetches the web via tool calling (like OpenCode's agent loop with Tavily + WebFetch), grounded answers cite tool results, and every source renders a rich OpenGraph thumbnail card. Local (LiteRT-LM function calling) and remote (OpenAI-compatible tools[]) both supported. Pasted-URL heuristic grounding stays as fallback.

### Tavily Search

- [ ] **TAV-01**: User stores a Tavily API key in Settings (Keystore-encrypted, test-connection button, clear error when missing/invalid)
- [ ] **TAV-02**: Search results ground the answer (query → top-N results fused into context with numbered citations, same Fuentes/preview/rows pipeline as URL grounding)
- [ ] **TAV-03**: Search honors grounding enablement (global + per-chat toggle + offline gate; no search when grounding off or offline)

### Agentic Tool Loop

- [ ] **AGENT-01**: Local model invokes `web_search` / `web_fetch` tools autonomously via LiteRT-LM function calling (multi-turn loop with step cap, Stop cancels, thinking-channel tokens never leak into answers)
- [ ] **AGENT-02**: Tool outputs are sanitized before reaching the model (URL allowlist, hijack filter, delimiter escaping — same trust boundary as fetched pages)
- [ ] **AGENT-03**: Remote OpenAI-compatible models invoke the same tools via native `tools[]` loop (function schemas, tool_calls parsing, tool-role messages, loop cap, per-provider capability check)
- [ ] **AGENT-04**: Only web tools are exposed (no file/system/shell tools; tool surface is a fixed allowlist; remote providers never receive local-only context)

### OpenGraph Thumbnails

- [ ] **OG-01**: Fetch captures OpenGraph tags (title, description, image URL) alongside extraction; persisted with source rows (Room migration v16)
- [ ] **OG-02**: Each grounded source renders a thumbnail card in chat (Coil-loaded image, title, description, tap → preview sheet; graceful text-only fallback when no image)
- [ ] **OG-03**: Preview sheet shows the OG header (image + title + description) above the extracted text

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Auto tool execution for non-web tools (files, shell, system) | Security boundary: web-only tool surface; v2.2 removal stands |
| WorkManager-scheduled search/indexing | No background web crawling; foreground chat scope only |
| Image generation / multimodal tool outputs | Tools return text only in v2.4 |
| Gated-model or per-model tool variations | Tool loop is provider-level, identical across models (capability-gated) |
| Real-time sync across devices | Deferred globally |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| TAV-01 | Phase 55 | Pending |
| TAV-02 | Phase 55 | Pending |
| TAV-03 | Phase 55 | Pending |
| AGENT-01 | Phase 56 | Pending |
| AGENT-02 | Phase 56 | Pending |
| AGENT-04 | Phase 56 | Pending |
| AGENT-03 | Phase 57 | Pending |
| OG-01 | Phase 58 | Pending |
| OG-02 | Phase 58 | Pending |
| OG-03 | Phase 58 | Pending |

**Coverage:**
- v2.4 requirements: 10 total
- Mapped to phases: 10
- Unmapped: 0 ✓

---
*Requirements defined: 2026-09-28*
*Decisions locked via inline discuss: Tavily provider, agentic trigger, local+remote, Coil thumbnails*
