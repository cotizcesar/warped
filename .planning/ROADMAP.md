# Roadmap: Warped v1.7 — App Optimization & Smart Presets

**Created:** 2026-05-25
**Phases:** 4 (continuing from Phase 30)
**Total requirements:** 19

---

## Phase 31: LiteRT-LM v0.12.0 Upgrade

**Goal:** Upgrade the inference engine from v0.11.0 to v0.12.0, verify compilation and all existing functionality.

**Requirements:** ENG-01, ENG-02, ENG-03

**Depends on:** Nothing (standalone)

**Success criteria:**
1. `libs.versions.toml` references LiteRT-LM v0.12.0 and Gradle sync succeeds
2. App compiles without errors (`./gradlew assembleDebug`)
3. All existing unit tests pass (`./gradlew :app:testDebugUnitTest`)
4. Manual smoke test: load a local model and send a message — streaming works
5. Any v0.12.0 API breaking changes are identified and adapted (if applicable)

**Plan structure:**
- Plan 1: Bump version in version catalog, Gradle sync, fix compilation errors
- Plan 2: Update API calls if v0.12.0 introduces deprecations or signature changes
- Plan 3: Run tests, fix regressions, verify local inference end-to-end

---

## Phase 32: Unified Models & Endpoints Selector

**Goal:** Build a single unified selector screen where the user sees 1 local model (with connect/disconnect toggle) alongside infinite remote endpoint models.

**Requirements:** UNIFY-01, UNIFY-02, UNIFY-03, UNIFY-04, UNIFY-05

**Depends on:** Phase 31 (needs working engine)

**Success criteria:**
1. User opens Models & Endpoints screen and sees local models section with a connect/disconnect switch per model
2. Only 1 local model can be connected at a time — connecting a second one gracefully unloads the first
3. Remote endpoints and their models are listed below local models with clear visual separation
4. Local model shows green badge when connected (loaded in RAM), gray when disconnected
5. Tapping a remote endpoint model sets it as active without affecting the local model connection state

**Plan structure:**
- Plan 1: Refactor `ActiveModelSelection` domain model to support dual selection (local + remote simultaneously)
- Plan 2: Build unified selector composable with local model toggle switch and endpoint list
- Plan 3: Wire `ChatViewModel` to handle connect/disconnect lifecycle — load on connect, unload on disconnect
- Plan 4: Add "Use in chat" action for remote endpoints that sets active provider without unloading local model

---

## Phase 33: Unified UI Integration & Traffic Light

**Goal:** Integrate the unified selector into the app navigation, replace old dropdown/standalone screens, and implement the full traffic light status indicator for both local and remote.

**Requirements:** UNIFY-06, SEMAF-01, SEMAF-02, SEMAF-03, SEMAF-04, SEMAF-05

**Depends on:** Phase 32 (needs unified selector)

**Success criteria:**
1. ChatScreen TopAppBar shows the unified Models & Endpoints button instead of the old inline dropdown
2. The standalone EndpointsScreen is removed from navigation (EndpointsViewModel kept for CRUD)
3. Traffic light circle always visible in TopAppBar for both local and remote providers
4. Local states: Green (loaded+ready), Red (error), Gray (not loaded), Yellow (streaming if detectable)
5. Remote states: Green (connected), Red (error/disconnected), Gray (idle)
6. Clicking the traffic light reveals a tooltip or snackbar with detailed status information

**Plan structure:**
- Plan 1: Replace ChatScreen inline dropdown with navigation button to unified Models & Endpoints
- Plan 2: Remove deprecated EndpointsScreen route, keep EndpointsViewModel for management CRUD
- Plan 3: Implement full traffic light logic in ChatViewModel — derive color from combined local+remote state
- Plan 4: Add streaming detection for yellow state (if `isStreaming` observable from engine)

---

## Phase 34: Smart Memory-Based Presets

**Goal:** Deliver one dynamically calculated optimal preset based on available device RAM and selected model size, with manual override capability.

**Requirements:** SMART-01, SMART-02, SMART-03, SMART-04, SMART-05

**Depends on:** Phase 32 (needs model selection to trigger recalculation)

**Success criteria:**
1. When a model is selected, a single "Smart Preset" appears in the presets screen calculated from available RAM
2. Smart preset parameters (temperature, context_size, threads, max_tokens) differ based on memory headroom tiers
3. Selecting a different model triggers recalculation of the smart preset values
4. User can manually adjust any slider — the preset name changes to "Custom" to indicate deviation
5. Available/total RAM is displayed in the presets screen (e.g., "3.2 GB free / 8 GB total")

**Plan structure:**
- Plan 1: Implement `SmartPresetCalculator` — maps available RAM + model size bytes → optimal GenerationParameters
- Plan 2: Define memory tiers: Low (<4GB free), Mid (4-8GB), High (>8GB) with corresponding parameter mappings
- Plan 3: Integrate into PresetsViewModel — observe model changes and recalculate
- Plan 4: UI: show "Smart Preset (4.2 GB free)" label, detect manual overrides, display memory info

---

## Phase Summary

| # | Phase | Goal | Reqs | Success Criteria |
|---|-------|------|------|------------------|
| 31 | LiteRT-LM v0.12.0 Upgrade | Bump engine version, fix compilation | ENG-01..03 | 5 |
| 32 | Unified Models & Endpoints Selector | Single screen: 1 local + N remote | UNIFY-01..05 | 5 |
| 33 | Unified UI Integration & Traffic Light | Replace old UI, full semaforo both directions | UNIFY-06, SEMAF-01..05 | 6 |
| 34 | Smart Memory-Based Presets | Dynamic preset from available RAM | SMART-01..05 | 5 |

**Total: 4 phases, 19 requirements, 21 success criteria**

---
*Roadmap created: 2026-05-25*
