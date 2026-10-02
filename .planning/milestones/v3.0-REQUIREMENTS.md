# Requirements: Warped

**Defined:** 2026-10-02
**Core Value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.

## v3.0 Requirements

Requirements for v3.0 Chat UX + Voice Dictation. Each maps to roadmap phases.

### Play Rating

- [x] **RATE-01**: User gets an ambient Play In-App Review prompt at success moments (completed turns + cooldown, silent no-show on quota) — never behind a visible "Rate" button
- [x] **RATE-02**: User can open the Play Store listing from an in-app entry so rating is always reachable even when the Review dialog is quota-suppressed

### Chat Drawer

- [x] **DRAWER-01**: User with no models sees a "Download a model" button in the chat model drawer that navigates to Model Catalog
- [x] **DRAWER-02**: User no longer sees Web Options in the model drawer (web grounding lives in Settings only)
- [x] **DRAWER-03**: User sees Models, Help and Settings footer items in the same text size as New Chat
- [x] **DRAWER-04**: User can delete all chats from the chat drawer (bottom of list, above Models) with the existing confirm dialog

### Help

- [x] **HELP-01**: User reads a short, minimal, to-the-point Help screen (rewritten post-Tavily, EN+ES)

### Web Search (DuckDuckGo-only)

- [x] **SEARCH-01**: Tavily integration is fully removed (client, producer, DI, Settings UI, Keystore paths, EN+ES strings, tests — grep-clean)
- [x] **SEARCH-02**: User gets grounded answers from DuckDuckGo-only search behind the existing producer interface (budget + cancel semantics preserved)
- [x] **SEARCH-03**: Upgrading user leaves no orphaned Tavily Keystore entry (upgrade migration deletes it)
- [x] **SEARCH-04**: Legacy chats with Tavily citations still render read-only without crashes

### Settings Cleanup

- [x] **SET-01**: User no longer sees a Keystore key-deletion affordance in Settings (key rotation stays via edit-overwrite; programmatic deleteKey retained for endpoint-deletion flows)
- [x] **SET-02**: User no longer sees a Data section or delete-chats in Settings (bulk delete lives in the chat drawer)

### Download→Chat Funnel

- [x] **FUN-01**: User sees a "Use in Chat" button on downloaded models in Model Catalog that activates the model for chat
- [x] **FUN-02**: User with no local models sees a "Download a local model" button in Models & Endpoints that navigates to Model Catalog
- [x] **FUN-03**: User with no endpoints sees an "Add a new Endpoint" button in Models & Endpoints that opens endpoint creation

### Voice Dictation

- [x] **VOICE-01**: User dictates into the chat input via speech-to-text (recognized text lands editable in the input, never auto-sends; no audio messages)
- [x] **VOICE-02**: User grants RECORD_AUDIO in-context at first mic tap (rationale + Settings escape on permanent denial)
- [x] **VOICE-03**: User on a device without speech recognition gets a graceful fallback (availability gate, no crash; recognizer destroyed with the UI lifecycle)

## Future Requirements

### Polish follow-ups

- **POL-01**: Tune Play Review trigger thresholds (turns/days/cooldown) from real conversion data
- **POL-02**: `EXTRA_PREFER_OFFLINE` dictation hint evaluation
- **POL-03**: Pixel 7 reference benchmark numbers (PERF-16 + PERF-12/13, CI-gated carry-over)
- **POL-04**: Standing release-UAT device smokes (v2.2–v2.4 backlog)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Visible star-rating button wired to `launchReviewFlow()` | Play quota silently suppresses the dialog → looks broken; violates Play guidance |
| Audio/voice messages | User explicitly deferred — dictation to input only for v3.0 |
| Offline STT engines (Vosk/whisper) / ML Kit | 50–150 MB for a nice-to-have; ML Kit banned by dependency gate |
| New screens/destinations for CTAs | All CTAs reuse existing navigation callbacks per Material empty-state guidance |
| Certificate pinning / Play Integrity | Deferred since v1.5, unchanged |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| RATE-01 | Phase 66 | Complete |
| RATE-02 | Phase 66 | Complete |
| DRAWER-01 | Phase 64 | Complete |
| DRAWER-02 | Phase 64 | Complete |
| DRAWER-03 | Phase 64 | Complete |
| DRAWER-04 | Phase 64 | Complete |
| HELP-01 | Phase 64 | Complete |
| SEARCH-01 | Phase 63 | Complete |
| SEARCH-02 | Phase 63 | Complete |
| SEARCH-03 | Phase 63 | Complete |
| SEARCH-04 | Phase 63 | Complete |
| SET-01 | Phase 64 | Complete |
| SET-02 | Phase 64 | Complete |
| FUN-01 | Phase 64 | Complete |
| FUN-02 | Phase 64 | Complete |
| FUN-03 | Phase 64 | Complete |
| VOICE-01 | Phase 65 | Complete |
| VOICE-02 | Phase 65 | Complete |
| VOICE-03 | Phase 65 | Complete |

**Coverage:**

- v3.0 requirements: 19 total
- Mapped to phases: 19
- Unmapped: 0 ✓

---
*Requirements defined: 2026-10-02*
*Last updated: 2026-10-02 after initial definition*
