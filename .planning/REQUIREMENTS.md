# Requirements: Warped

**Defined:** 2026-05-25
**Core Value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.

## v1.7 Requirements

Requirements for App Optimization & Smart Presets milestone.

### Engine & Library Upgrade

- [x] **ENG-01**: Upgrade LiteRT-LM library from v0.11.0 to latest stable v0.12.0
- [x] **ENG-02**: Verify compilation succeeds and all existing local inference functionality works after upgrade
- [x] **ENG-03**: Adapt any API changes introduced by v0.12.0 (if breaking changes exist)

### Unified Models & Endpoints Selector

- [x] **UNIFY-01**: User sees all available models (1 local + N remote endpoints) in a single unified selector screen
- [x] **UNIFY-02**: User can connect/disconnect the local model with a toggle switch (only 1 local model at a time to preserve RAM)
- [x] **UNIFY-03**: Local model connection state is visually indicated with a green/gray badge (connected/disconnected)
- [x] **UNIFY-04**: User can select from infinite remote endpoint models alongside the single local model in the same list
- [x] **UNIFY-05**: Connecting a new local model automatically releases the previous one from RAM before loading
- [x] **UNIFY-06**: The unified Models & Endpoints screen replaces both the ChatScreen inline dropdown and the standalone Endpoints list section

### Traffic Light Status Indicator (Semaforo)

- [x] **SEMAF-01**: Traffic light circle indicator shows status for both local models AND remote endpoints in the TopAppBar
- [x] **SEMAF-02**: Local model states displayed correctly: Green = loaded in RAM and ready, Red = error, Gray = not loaded in RAM
- [x] **SEMAF-03**: Yellow/Orange = actively generating tokens (streaming) — if detectable, otherwise omitted
- [x] **SEMAF-04**: Remote endpoint states: Green = connected/healthy, Red = error/disconnected, Gray = idle/untested
- [x] **SEMAF-05**: The status indicator circle is always visible in the TopAppBar regardless of provider type

### Smart Memory-Based Presets

- [x] **SMART-01**: User sees one dynamically calculated optimal preset based on available device RAM and selected model size
- [x] **SMART-02**: The smart preset recalculates parameters when a different model is selected
- [x] **SMART-03**: Smart preset adjusts temperature, context_size, threads, and max_tokens based on available memory headroom
- [x] **SMART-04**: User can manually override any smart preset parameter via existing sliders
- [x] **SMART-05**: Available/total device RAM is displayed alongside the smart preset for user transparency

## v2 Requirements

Deferred to future release.

- **REMOTE-01**: Endpoint health check polling with background refresh
- **REMOTE-02**: Memory pressure-based automatic model eviction with configurable thresholds

## Out of Scope

| Feature | Reason |
|---------|--------|
| GGUF / llama.cpp inference | Removed in v1.5, LiteRT-LM is sole local engine |
| iOS / desktop platforms | Android only per project constraints |
| Multi-model simultaneous loading | Explicitly limited to 1 local model to preserve RAM |
| Cloud sync for presets | Local-only, no server infrastructure |
| Auto-download models based on RAM | User-initiated downloads only, smart presets adjust to what's available |
| Background endpoint health monitoring | Polling on user interaction only, not in background |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| ENG-01 | Phase 31 | Done |
| ENG-02 | Phase 31 | Done |
| ENG-03 | Phase 31 | Done |
| UNIFY-01 | Phase 32 | Done |
| UNIFY-02 | Phase 32 | Done |
| UNIFY-03 | Phase 32 | Done |
| UNIFY-04 | Phase 32 | Done |
| UNIFY-05 | Phase 32 | Done |
| UNIFY-06 | Phase 33 | Done |
| SEMAF-01 | Phase 33 | Done |
| SEMAF-02 | Phase 33 | Done |
| SEMAF-03 | Phase 33 | Done |
| SEMAF-04 | Phase 33 | Done |
| SEMAF-05 | Phase 33 | Done |
| SMART-01 | Phase 34 | Done |
| SMART-02 | Phase 34 | Done |
| SMART-03 | Phase 34 | Done |
| SMART-04 | Phase 34 | Done |
| SMART-05 | Phase 34 | Done |

**Coverage:**
- v1.7 requirements: 19 total
- Mapped to phases: 19
- Unmapped: 0

---
*Requirements defined: 2026-05-25*
*Last updated: 2026-05-25 after milestone v1.7 requirements definition*
