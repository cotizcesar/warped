# Requirements: Warped

**Defined:** 2026-05-08
**Core Value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.

## v1.4 Requirements

Requirements for the Onboarding Wizard milestone. Each maps to roadmap phases.

### Wizard Flow (WZFL)

- [ ] **WZFL-01**: User sees wizard on first app launch via DataStore flag
- [ ] **WZFL-02**: User can skip individual steps via "Skip" button on each step
- [ ] **WZFL-03**: User can skip entire wizard via "Skip all" with confirmation dialog
- [ ] **WZFL-04**: User navigates between steps via swipe (HorizontalPager) and Next/Back buttons
- [ ] **WZFL-05**: User sees step indicator dots showing current position out of 9 steps
- [ ] **WZFL-06**: On completion, wizard marks as completed and navigates to Chat
- [ ] **WZFL-07**: Pressing Back on first step exits the app (first launch) or returns to previous screen (re-entry from Settings)
- [ ] **WZFL-08**: Wizard never auto-shows again after completion or skip all; stays accessible from Settings

### Wizard Steps (WZST)

- [ ] **WZST-01**: Step 1 — Bienvenida: introduces Warped (local + remote LLMs), warm friendly tone
- [ ] **WZST-02**: Step 2 — Motores locales: GGUF (llama.cpp) vs LiteRT-LM, how to switch
- [ ] **WZST-03**: Step 3 — Descargar GGUF: Hugging Face search, download, manage models
- [ ] **WZST-04**: Step 4 — Modelos LiteRT-LM: .litertlm import and usage
- [ ] **WZST-05**: Step 5 — Chat local: load model, configure params, streaming chat
- [ ] **WZST-06**: Step 6 — Proveedores remotos: add OpenAI, Anthropic, Ollama, LM Studio
- [ ] **WZST-07**: Step 7 — Chat remoto: select remote model, chat with streaming
- [ ] **WZST-08**: Step 8 — Presets: save/load generation parameter presets
- [ ] **WZST-09**: Step 9 — Historial: browse past conversations, resume chats
- [ ] **WZST-10**: Each step has CTA button navigating to relevant screen, wizard stays in back stack
- [ ] **WZST-11**: Each step has icon, title, and short description (2-3 sentences)

### Wizard Context (WZCT)

- [ ] **WZCT-01**: Wizard reads app state (model count, endpoint count, chat count) at open time
- [ ] **WZCT-02**: Steps adapt content when data exists (e.g. "Ya tienes 3 modelos GGUF")
- [ ] **WZCT-03**: Context data snapshotted once, not continuously observed

### Wizard Accessibility (WZAC)

- [ ] **WZAC-01**: User can re-open wizard from Settings → General → "Setup Wizard"
- [ ] **WZAC-02**: Re-opened wizard shows review variant with current app state (checkmarks, counts)
- [ ] **WZAC-03**: Back from re-opened wizard returns to Settings (not exits app)

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

(None yet)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Tooltips/coach marks on main UI | Defer — full-screen wizard is cleaner for v1. Coach marks add complexity with positioning and z-ordering. |
| Wizard re-trigger on app update | Avoid annoyance — wizard only shows on fresh install, not on updates. |
| Video or animated illustrations | Unnecessary overhead for a text-based LLM app. Static icons + text are sufficient. |
| Forced sequential completion | User must be able to skip or exit at any time — already covered by skip per step + skip all. |
| Per-step analytics/telemetry | No tracking in the app. Out of scope for privacy. |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| WZFL-01 | Phase 20 | Pending |
| WZFL-02 | Phase 20 | Pending |
| WZFL-03 | Phase 20 | Pending |
| WZFL-04 | Phase 20 | Pending |
| WZFL-05 | Phase 20 | Pending |
| WZFL-06 | Phase 20 | Pending |
| WZFL-07 | Phase 20 | Pending |
| WZFL-08 | Phase 20 | Pending |
| WZCT-03 | Phase 20 | Pending |
| WZST-01 | Phase 21 | Pending |
| WZST-02 | Phase 21 | Pending |
| WZST-03 | Phase 21 | Pending |
| WZST-04 | Phase 21 | Pending |
| WZST-05 | Phase 21 | Pending |
| WZST-06 | Phase 21 | Pending |
| WZST-07 | Phase 21 | Pending |
| WZST-08 | Phase 21 | Pending |
| WZST-09 | Phase 21 | Pending |
| WZST-10 | Phase 21 | Pending |
| WZST-11 | Phase 21 | Pending |
| WZCT-01 | Phase 21 | Pending |
| WZCT-02 | Phase 21 | Pending |
| WZAC-01 | Phase 22 | Pending |
| WZAC-02 | Phase 22 | Pending |
| WZAC-03 | Phase 22 | Pending |

**Coverage:**
- v1.4 requirements: 25 total
- Mapped to phases: 25
- Unmapped: 0 ✓

---
*Requirements defined: 2026-05-08*
*Last updated: 2026-05-08 after v1.4 requirements definition*
