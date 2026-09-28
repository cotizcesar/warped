# Roadmap: Warped

## Overview

v2.2 Simplificación + Web Grounding removes dead surface (Skills tool-calling, Hugging Face token plumbing, HF model search) and adds one heuristic, zero-dependency web-grounding hook plus a fix for the syntax-theme selector that only applies Monokai. Removals land first so grounding builds on the post-removal transcript shape; the theme fix lands last so verification runs against final call sites. Continues numbering from v2.1 (ended at Phase 48).

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

- [x] **Phase 49: Surface Removal** - Skills, HF token, and model search deleted; static catalog only (2026-09-28, automated gates pass, device smoke deferred)
- [x] **Phase 50: Web Grounding** - URL-in-message fetch → [WEB CONTEXT] injection → grounded answer with offline fallback (2026-09-28, automated gates pass, device smoke deferred)
- [x] **Phase 51: Syntax-Theme Fix** - All 4 code presets apply in chat, with regression test (2026-09-28, automated gates pass, device smoke deferred)

## Phase Details

### Shipped: v2.1 Finish v2.0 Leftovers (2026-09-28)

4 phases (45–48), 18/18 requirements MET. Full archive: [`.planning/milestones/v2.1-ROADMAP.md`](milestones/v2.1-ROADMAP.md) · Audit: [`v2.1-MILESTONE-AUDIT.md`](v2.1-MILESTONE-AUDIT.md) (passed)

### 🚧 v2.2 Simplificación + Web Grounding (In Progress)

**Milestone Goal:** Simplificar la app quitando superficie innecesaria (skills, token HF, buscador) y agregar grounding web heurístico con fallback offline, además de arreglar los temas de código del chat.

### Phase 49: Surface Removal
**Goal**: Users chat and download models with no skills, HF token, or search surface — smaller app, same core capability via static catalog
**Depends on**: Phase 48 (v2.1 close)
**Requirements**: DEL-01, DEL-02, DEL-03, DEL-04, DEL-05, DEL-06
**Success Criteria** (what must be TRUE):
  1. User chats locally and remotely with no skill chips, skill prefs, or remote tool rounds; legacy chats with Role.TOOL rows still open and render readably
  2. Settings shows no Hugging Face token field and downloads complete with no Authorization headers or gated-model filtering
  3. User finds models only via the static allowlist catalog, with download progress/cancel and local management (view, delete)
  4. Release posture holds: dependency audit script green, R8 keeps narrowed with LiteRT-LM SDK keeps intact, release smoke (launch → load model → local + remote turn) passes
**Plans**: TBD
**UI hint**: yes

### Phase 50: Web Grounding
**Goal**: User pastes a URL and gets a grounded answer with cited sources — or a clearly-marked model-only answer when offline
**Depends on**: Phase 49
**Requirements**: WEB-01, WEB-02, WEB-03, WEB-04, WEB-05, WEB-06
**Success Criteria** (what must be TRUE):
  1. User pastes a URL in a message, sees a "Leyendo página…" status, and receives an answer reflecting the page content with numbered sources
  2. Oversized, redirect-loop, or slow pages are capped (bytes/redirects/timeouts) and cancelable via Stop without freezing the UI
  3. Grounded answers cite numbered sources, ask the user for a link when freshness is needed, and never invent URLs; fetched content cannot hijack the model via injected instructions
  4. With no internet or a failed fetch, the user gets a model-only answer with a visible non-model notice — never error text injected as context
  5. User can toggle web grounding on/off in Settings
**Plans**: TBD
**UI hint**: yes

### Phase 51: Syntax-Theme Fix
**Goal**: All four code themes apply in chat code blocks
**Depends on**: Phase 50 (ordered last so verification runs against final call sites, e.g. post-removal screens; no file overlap, parallelizable with Phase 49)
**Requirements**: THEME-01, THEME-02
**Success Criteria** (what must be TRUE):
  1. User selects each of the 4 presets (Monokai, One Dark, GitHub, Dracula) and sees it applied on Python code blocks in chat, in both light and dark mode
  2. A regression test covering all 4 presets fails if any preset is silently ignored
**Plans**: TBD
**UI hint**: yes

## Progress

**Execution Order:**
Phases execute in numeric order: 49 → 50 → 51 (51 parallelizable with 49 — zero file overlap)

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 49. Surface Removal | 0/TBD | Not started | - |
| 50. Web Grounding | 0/TBD | Not started | - |
| 51. Syntax-Theme Fix | 0/TBD | Not started | - |

---

**Cumulative state after v2.1:** 48 phases shipped, 305 requirements delivered across v1.0–v2.1.
