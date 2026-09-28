---
phase: 50-web-grounding
plan: "02"
subsystem: ui
tags: [compose, material3, settings, chat-ui, grounding]

# Dependency graph
requires:
  - phase: 50-web-grounding plan 01
    provides: fetch hook states (isFetchingWeb, groundedSources, modelOnlyNotice) and DataStore toggle
provides:
  - Web settings section with grounding toggle
  - Fetch status chip, Fuentes list, model-only banner in chat
affects: [51-syntax-theme-fix]

# Actuals (#2632)
actuals:
  tokens: 4133
  tasks: 2
  commits: 1

# Tech tracking
tech-stack:
  added: []
  patterns: [transient status chip above input bar, ephemeral-state transcript adornments]

key-files:
  created:
    - app/src/test/java/com/warped/ui/settings/SettingsGroundingToggleTest.kt
  modified:
    - app/src/main/java/com/warped/ui/settings/SettingsScreen.kt
    - app/src/main/java/com/warped/ui/settings/SettingsViewModel.kt
    - app/src/main/java/com/warped/ui/settings/SettingsUiState.kt
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
    - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt

key-decisions:
  - "Banner and Fuentes copy stays inline per MessageBubble precedent — no new strings.xml keys (chat input-level copy lives in strings.xml, transcript copy is inline)"
  - "Switch contentDescription set via semantics modifier (Material3 Switch has no contentDescription param)"

patterns-established:
  - "Ephemeral grounding adornments render inside MessageBubble so they scroll with their message"
  - "Toggle takes effect on the next message via the ViewModel's collected snapshot; no restart, no retroactive re-grounding"

requirements-completed: [WEB-05, WEB-06]

coverage:
  - id: D1
    description: "Web settings section (Data → Web → Display order) with default-ON grounding toggle writing through to DataStore"
    requirement: "WEB-06"
    verification:
      - kind: unit
        ref: "com.warped.ui.settings.SettingsGroundingToggleTest (2 tests: write-through + default ON)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Transient Leyendo página fetch chip above the input bar with cancel announcement"
    requirement: "WEB-06"
    verification:
      - kind: other
        ref: "grep Leyendo página in ChatScreen.kt + ./gradlew :app:assembleDebug BUILD SUCCESSFUL"
        status: pass
    human_judgment: true
    rationale: "Chip transient behavior (mounts only during fetch, unmounts on Stop) and visual layout need on-device confirmation"
  - id: D3
    description: "Fuentes list under grounded answers and UI-rendered model-only banner (offline vs failure copy)"
    requirement: "WEB-05"
    verification:
      - kind: other
        ref: "grep Fuentes + banner copies in MessageBubble.kt + ./gradlew :app:assembleDebug BUILD SUCCESSFUL"
        status: pass
    human_judgment: true
    rationale: "Rendered Fuentes/banner appearance, wrapping, and spacing need on-device visual confirmation"

# Metrics
duration: 20min
completed: 2026-09-28
status: complete
---

# Phase 50: Grounding Surfaces Summary

**Transient fetch chip, numbered Fuentes list, UI-rendered model-only banner, and default-ON Web settings toggle — Material 3 only, inline copy**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-09-28T14:20:00Z
- **Completed:** 2026-09-28T14:40:00Z
- **Tasks:** 2
- **Files modified:** 6 (1 created test, 5 modified)

## Accomplishments

- Settings Web section (Data → Web → Display → App → Security) with "Grounding web" Switch bound to `webGroundingEnabled`, default ON
- Transient "Leyendo página…" chip above the input bar with full cancel announcement; no separate cancel button, never persisted
- Fuentes list (`Fuentes` + `[1] {url}`) under grounded answers; zero sources renders nothing
- Model-only banner with exact offline/failure Spanish copy, Info icon, muted style; grounded answers render no banner

## Task Commits

Tasks were batched into one atomic plan commit (single-plan wave):

1. **Task 1: settings toggle + test** — `6ec5b2e` (feat)
2. **Task 2: chip + Fuentes + banner** — `6ec5b2e` (feat)

**Plan metadata:** `4d5aeb5` (docs: phase plans)

## Files Created/Modified

- `app/src/main/java/com/warped/ui/settings/SettingsScreen.kt` — Web section card between Data and Display
- `app/src/main/java/com/warped/ui/settings/SettingsViewModel.kt` — webGroundingEnabled collector + setter
- `app/src/main/java/com/warped/ui/settings/SettingsUiState.kt` — `webGroundingEnabled: Boolean = true`
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` — transient fetch chip in bottomBar column
- `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt` — ModelOnlyBanner + Fuentes list
- `app/src/test/java/com/warped/ui/settings/SettingsGroundingToggleTest.kt` — toggle write-through test

## Decisions Made

- Inline literals for banner/Fuentes/chip copy per MessageBubble precedent ("Thinking", "Copied!"); strings.xml holds input-level copy, not transcript copy — no new keys.
- Switch `contentDescription "Grounding web"` via semantics modifier (Material3 Switch exposes no such param).

## Deviations from Plan

### Auto-fixed Issues

**1. [Correctness] MockK `coAnswers` import unresolved in toggle test**
- **Found during:** Task 1 (SettingsGroundingToggleTest verification)
- **Issue:** `import io.mockk.coAnswers` fails to resolve under mockk 1.14.11 in this module
- **Fix:** Stubbed setter with `just Runs` and drove UI-state assertion through the prefs flow (`groundingState.value = false`), keeping the write-through `coVerify` intact
- **Files modified:** `app/src/test/java/com/warped/ui/settings/SettingsGroundingToggleTest.kt`
- **Verification:** 2/2 toggle tests pass
- **Committed in:** `6ec5b2e` (part of plan commit)

---

**Total deviations:** 1 auto-fixed (correctness)
**Impact on plan:** Test-only change; same write-through guarantee. No scope creep.

## Issues Encountered

None — plan executed as written apart from the one test auto-fix.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 50 implementation complete; all WEB-01..WEB-06 truths hold at unit/grep/build level.
- On-device visual confirmation of chip/Fuentes/banner deferred to release UAT (same precedent as Phase 49 device smoke).

---
*Phase: 50-web-grounding*
*Completed: 2026-09-28*
