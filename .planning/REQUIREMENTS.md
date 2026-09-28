# Requirements: Warped — v2.3 Web Grounding v2

**Defined:** 2026-09-28
**Core Value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.

## v2.3 Requirements

Extend the v2.2 single-URL heuristic grounding pipeline to multi-page context with previewable sources and offline resilience. Providers stay untouched (prompt-prefix augmentation only).

### Multi-URL Fetch

- [x] **FETCH-01**: User pasting 2–5 URLs in one message gets a fused answer grounded in all fetchable pages (parallel fan-out, numbered `[WEB CONTEXT 1..N]` blocks)
- [ ] **FETCH-02**: User gets a grounded answer from the pages that loaded even when one link is dead (partial grounding; model-only banner only when ALL pages fail)
- [ ] **FETCH-03**: User sees fetch progress per source ("Leyendo 2 de 4…") with per-source ok/skipped states (silent drops never happen)

### Sources

- [ ] **SRC-01**: User can tap a source to preview its extracted text in a bottom sheet without leaving chat
- [ ] **SRC-02**: User sees a numbered Fuentes list covering all N fetched sources for the turn
- [ ] **SRC-03**: User can open the full page in the browser from the preview ("Abrir en navegador")

### Per-Chat Toggle

- [ ] **TOGGLE-01**: User can override web grounding per conversation (tri-state: on/off/inherit-global, null = follow global default-ON)
- [ ] **TOGGLE-02**: User can send a one-off model-only message from the composer ("Sin web" override) without changing any toggle
- [ ] **TOGGLE-03**: User's per-chat web preference and persisted sources survive app restarts (combined Room migration v15: override column + sources table)

### Offline Retry & Extraction

- [ ] **RETRY-01**: User offline at send time gets a queued state with "Reintentar" on reconnect (message-scoped, OFFLINE-only; retry fetches, never rewrites history or re-runs inference silently)
- [x] **EXTRACT-01**: User gets cleaner grounded answers via Jsoup 1.23.2 parse-only extraction replacing the hand-rolled regex core (fetch policy unchanged: stripped client, 64KB cap, timeouts; never `Jsoup.connect()`)
- [x] **EXTRACT-02**: User on small local models gets answers that fit context (global grounding budget divided across pages, model-window-aware, replacing fixed per-page constants)

## v2.4 Requirements

Deferred, trigger-based. Tracked but not in the v2.3 roadmap.

### Tuning

- **TUNE-01**: Fused-context budget auto-tuned per model context size (trigger: overflow reports on small local models)
- **TUNE-02**: Preview source-store LRU size/TTL tuned for low-RAM devices (trigger: memory pressure reports)

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Model-output citation pills (e.g. [1][2] markers in generated text) | Anti-feature: small local models hallucinate markers and collide with Markdown; Fuentes list + preview covers the trust need |
| Periodic WorkManager retry for offline grounding | Wrong granularity (15-min minimum vs seconds-scale chat expectation); message-scoped foreground retry instead |
| `Jsoup.connect()` fetching | Security boundary: would bypass AuthInterceptor stripping, 64KB cap, and timeout policy; Jsoup is parse-only |
| Provider interface changes for grounding | Grounding stays prompt-prefix augmentation; `LlmModelHelper` keystone untouched |
| Real-time sync across devices | Deferred globally, not grounding-specific |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| FETCH-01 | Phase 52 | Complete |
| FETCH-02 | Phase 52 | Pending |
| FETCH-03 | Phase 52 | Pending |
| SRC-01 | Phase 53 | Pending |
| SRC-02 | Phase 53 | Pending |
| SRC-03 | Phase 53 | Pending |
| TOGGLE-01 | Phase 53 | Pending |
| TOGGLE-02 | Phase 53 | Pending |
| TOGGLE-03 | Phase 53 | Pending |
| RETRY-01 | Phase 54 | Pending |
| EXTRACT-01 | Phase 52 | Complete |
| EXTRACT-02 | Phase 52 | Complete |

**Coverage:**

- v2.3 requirements: 12 total
- Mapped to phases: 12
- Unmapped: 0 ✓

---
*Requirements defined: 2026-09-28*
*Last updated: 2026-09-28 after initial definition*
