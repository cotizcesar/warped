# Roadmap: Warped

## Milestones

- ✅ **v2.1 Finish v2.0 Leftovers** — Phases 45-48 (shipped 2026-09-28)
- ✅ **v2.2 Simplificación + Web Grounding** — Phases 49-51 (shipped 2026-09-28)
- ✅ **v2.3 Web Grounding v2** — Phases 52-54 (shipped 2026-09-28)
- ✅ **v2.4 Agentic Web** — Phases 55-58 (shipped 2026-09-29)
- ✅ **v2.5 Play Compliance + Leaks** — Phases 59-62 (shipped 2026-10-01)
- 🚧 **v3.0 Chat UX + Voice Dictation** — Phases 63-66 (in progress)

## Phases

<details>
<summary>✅ v2.5 Play Compliance + Leaks (Phases 59-62) — SHIPPED 2026-10-01</summary>

- [x] Phase 59: 16 KB Dependency Verification (2/2 plans) — completed 2026-09-30
- [x] Phase 60: API-36 Behavior Audit (2/2 plans) — completed 2026-09-30
- [x] Phase 61: LeakCanary Instrumentation + Guided Audit (2/2 plans) — completed 2026-09-30
- [x] Phase 62: Fix Loop + Release Hardening (2/2 plans) — completed 2026-10-01

</details>

### Shipped: v2.1 Finish v2.0 Leftovers (2026-09-28)

4 phases (45–48), 18/18 requirements MET. Full archive: [`milestones/v2.1-ROADMAP.md`](milestones/v2.1-ROADMAP.md)

### Shipped: v2.2 Simplificación + Web Grounding (2026-09-28)

3 phases (49–51), 5 plans, 14 requirements — 10 MET, 4 PARTIAL (device smoke deferred, user-accepted). Full archive: [`milestones/v2.2-ROADMAP.md`](milestones/v2.2-ROADMAP.md)

### Shipped: v2.3 Web Grounding v2 (2026-09-28)

3 phases (52–54), 8 plans, 12 requirements — 12/12 verified. Full archive: [`milestones/v2.3-ROADMAP.md`](milestones/v2.3-ROADMAP.md)

### Shipped: v2.4 Agentic Web (2026-09-29)

4 phases (55–58), 9 plans, 10 requirements — 10/10 verified. Full archive: [`milestones/v2.4-ROADMAP.md`](milestones/v2.4-ROADMAP.md)

### Shipped: v2.5 Play Compliance + Leaks (2026-10-01)

4 phases (59–62), 8 plans, 15 requirements — 15/15 satisfied (audit passed, G-59-01 closed by Phase 62). Full archive: [`milestones/v2.5-ROADMAP.md`](milestones/v2.5-ROADMAP.md)

---

**Cumulative state after v2.5:** 62 phases shipped, 352 requirements delivered across v1.0–v2.5.

---

### 🚧 v3.0 Chat UX + Voice Dictation (In Progress)

**Milestone Goal:** Simplify web search to DuckDuckGo-only (Tavily removed), polish drawer/settings/catalog/help surfaces, and add voice dictation + ambient Play rating.

**Phases:**

- [x] **Phase 63: Tavily Removal → DDG-only Search** - Delete Tavily integration, single DuckDuckGo producer behind existing interface (completed 2026-10-02)
- [x] **Phase 64: Drawer + Settings + Help + Funnel Polish** - Drawer cluster, settings cleanup, help rewrite, empty-state CTAs (completed 2026-10-02)
- [x] **Phase 65: Voice Dictation** - Speech-to-text into chat input with permission flow and fallback (completed 2026-10-02)
- [ ] **Phase 66: Play In-App Review** - Ambient rating trigger plus always-reachable Store entry

## Phase Details

### Phase 63: Tavily Removal → DDG-only Search

**Goal**: Users get grounded answers from DuckDuckGo-only search with no API-key friction
**Depends on**: Phase 62
**Requirements**: SEARCH-01, SEARCH-02, SEARCH-03, SEARCH-04
**Success Criteria** (what must be TRUE):

  1. User asking a web-grounded question gets an answer grounded in DuckDuckGo results with zero API-key setup
  2. User never sees a Tavily key field, Tavily settings card, or any API-key prompt for web search
  3. Upgrading user keeps working search with no orphaned Tavily Keystore entry left behind
  4. Legacy chats with Tavily citations still open and render read-only without crashes

**Plans**: 2 plans

Plans:
**Wave 1**

- [x] 63-01-PLAN.md — Tracer: delete Tavily files, standalone DDG producer, DI rebind, CompatToolLoop rewired

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 63-02-PLAN.md — Settings/Keystore/strings removal, remaining callers, tests, legacy citation verification

### Phase 64: Drawer + Settings + Help + Funnel Polish

**Goal**: Users navigate drawers, settings, catalog and help without dead ends or clutter
**Depends on**: Phase 63 (help rewrite and copy audit must follow the Tavily cut)
**Requirements**: DRAWER-01, DRAWER-02, DRAWER-03, DRAWER-04, SET-01, SET-02, HELP-01, FUN-01, FUN-02, FUN-03
**Success Criteria** (what must be TRUE):

  1. User with no models sees a "Download a model" button in the chat model drawer that navigates to Model Catalog
  2. User sees Models, Help and Settings footer items in the same text size as New Chat, with delete-all-chats at the drawer bottom (confirm dialog intact) and no Web Options in the drawer
  3. User no longer sees a key-deletion affordance or a Data section in Settings; bulk chat delete lives only in the drawer and key rotation still works via endpoint edit
  4. User sees a "Use in Chat" button on downloaded catalog models that activates the model, plus "Download a local model" / "Add a new Endpoint" empty-state buttons that navigate correctly
  5. User reads a short, minimal, to-the-point Help screen (EN+ES) with no Tavily/key steps

**Plans**: 2 plans

Plans:
**Wave 1**

- [x] 64-01-PLAN.md — Drawer sheet CTA + footer + delete-all row, Settings removals-only cleanup

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 64-02-PLAN.md — Catalog Use in Chat + two-CTA empty states + Help rewrite (EN+ES)

**Cross-cutting constraints:**

- Catalog downloaded cards keep the existing delete IconButton with its existing delete content-description string beside the new Use in Chat button — no bare icon button (checker constraint a)
- All touched composables reuse only the 5 surveyed Material 3 type roles with no new sizes or weights (checker constraint b)

**UI hint**: yes

### Phase 65: Voice Dictation

**Goal**: Users dictate chat messages by voice on the chat input screen instead of typing
**Depends on**: Phase 64 (scheduled after drawer/chat UI churn settles to avoid ChatScreen merge conflicts)
**Requirements**: VOICE-01, VOICE-02, VOICE-03
**Success Criteria** (what must be TRUE):

  1. User taps the mic, speaks, and sees recognized text land editable in the chat input without auto-sending
  2. User tapping mic the first time gets an in-context rationale plus permission request, and a Settings escape on permanent denial
  3. User on a device without speech recognition gets a graceful fallback with no crash

**Plans**: 2 plans

Plans:
**Wave 1**

- [x] 65-01-PLAN.md — Foundation: RECORD_AUDIO manifest, 7 EN+ES strings, VoiceDictationManager wrapper, Chat state + ViewModel ownership

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 65-02-PLAN.md — Input-bar mic button, permission launcher, rationale dialog, denial Snackbar, lifecycle teardown

**UI hint**: yes

### Phase 66: Play In-App Review

**Goal**: Users can rate the app via Play at success moments without ever hitting a dead button
**Depends on**: Phase 65 (smallest slice last; fully independent, no shared files)
**Requirements**: RATE-01, RATE-02
**Success Criteria** (what must be TRUE):

  1. User completing chats occasionally gets an ambient Play review prompt at success moments, silently absent when quota-suppressed
  2. User can always reach the Play Store listing from an in-app entry even when the Review dialog is quota-suppressed
  3. Chat send/streaming never stalls waiting on the review flow

**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 63 → 64 → 65 → 66

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 63. Tavily Removal → DDG-only Search | 2/2 | Complete    | 2026-10-02 |
| 64. Drawer + Settings + Help + Funnel Polish | 2/2 | Complete    | 2026-10-02 |
| 65. Voice Dictation | 2/2 | Complete    | 2026-10-02 |
| 66. Play In-App Review | 0/TBD | Not started | - |
