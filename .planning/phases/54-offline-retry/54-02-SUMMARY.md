---
phase: 54-offline-retry
plan: 02
subsystem: chat-grounding-ui
tags: [kotlin, compose, material3, offline-retry, grounding, accessibility]

# Dependency graph
requires:
  - phase: 54-offline-retry
    provides: retryGrounding + retryJob + isValidatedOnline + refreshConnectivity (plan 01)
  - phase: 53-sources-preview-per-chat-toggle
    provides: ModelOnlyBanner OFFLINE/FETCH_FAILED contract, Fuentes list, preview sheet
provides:
  - ModelOnlyBanner queued OFFLINE row (En espera suffix + validated-online-gated Reintentar)
  - ChatScreen ON_RESUME refreshConnectivity + onRetry → retryGrounding plumbing
affects: [milestone audit, release UAT visual backstop (banner overflow, both themes)]

# Tech tracking
tech-stack:
  added: []
  patterns: [visibility-gate-is-hint-only, resume-refresh-without-fetch, callback-only-bubble-boundary]

key-files:
  created: []
  modified:
    - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt

key-decisions:
  - "Shared banner Text carries weight(1f, fill=false) so the trailing Reintentar slot fits — FETCH_FAILED copy strings byte-identical, layout behavior unchanged when no button renders"
  - "accessibility description applied via semantics on the TextButton, replacing the default label announcement with the full UI-SPEC sentence"
  - "Streaming transient bubble keeps default no-op retry params — no id to retry, never wired"

patterns-established:
  - "Retry affordance visibility is a hint only — OFFLINE + connectivity + eligibility gates live in retryGrounding (T-54-05/T-54-06)"
  - "Bubble never imports the ViewModel — onRetry callback preserves the clean-architecture boundary"

requirements-completed: [RETRY-01]

# Metrics
duration: 20min
completed: 2026-09-28
---

# Phase 54 Plan 02: Retry UI Surface Summary

**Queued OFFLINE banner (`En espera.` suffix + validated-online-gated `Reintentar` TextButton) wired through ChatScreen resume-refresh to the plan-01 `retryGrounding` backend — the phase's only visual change**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-09-28T18:30:00Z
- **Completed:** 2026-09-28T18:50:00Z
- **Tasks:** 2
- **Files modified:** 2 (prod only, zero test changes needed)

## Accomplishments

- `ModelOnlyBanner` OFFLINE branch renders the exact UI-SPEC queued copy (`Sin conexión. Respuesta solo del modelo, sin contenido de la página. En espera.`) in the existing Body 14sp `onSurfaceVariant` style — no warning/error tint, no new row
- Trailing-slot Material 3 `TextButton` labeled `Reintentar` (verb only, primary label color, 44dp min touch target, UI-SPEC contentDescription) renders ONLY when `OFFLINE && isValidatedOnline && !isFetchingWeb` — hidden while offline or while any fetch is in flight
- `MessageBubble` threads `isValidatedOnline`, `isFetchingWeb`, `onRetry(messageId)` from the message-list call site; bubble holds no ViewModel reference
- `ChatScreen` adds an `ON_RESUME` `LifecycleEventObserver` calling `viewModel.refreshConnectivity()` — flips button visibility only, never fetches (D-no-auto-retry)
- Message-list items pass `onRetry = { viewModel.retryGrounding(it) }` with the correct assistant id; success clears the notice so the Phase 53 Fuentes list replaces the banner, failure keeps banner + retry by construction (no new rendering code)
- FETCH_FAILED banners never show Reintentar (branch-gated by construction); preview sheet untouched

## Task Commits

Each task was committed atomically:

1. **Task 1: Queued banner + Reintentar button (OFFLINE only)** - `b534016` (feat)
2. **Task 2: Resume refresh + retry wiring** - `00cb375` (feat)

## Files Created/Modified

- `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt` - `MessageBubble` gains `isValidatedOnline`/`isFetchingWeb`/`onRetry` params; `ModelOnlyBanner` gains queued suffix + gated trailing `Reintentar` slot
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` - `ON_RESUME` refresh observer + `onRetry`/`isValidatedOnline`/`isFetchingWeb` plumbed into the message list

## Decisions Made

- **Shared Text weight instead of branch-split layout:** the banner `Text` carries `Modifier.weight(1f, fill = false)` so the trailing button fits without overflow. With `fill = false` and no button rendered, FETCH_FAILED rows lay out identically to before — copy strings are byte-identical. Splitting the Row per-branch would have duplicated the icon/spacer and risked visual drift.
- **Semantics replaces default button announcement:** the full UI-SPEC accessibility sentence is applied via `Modifier.semantics { contentDescription = ... }` on the `TextButton`, which is the standard Compose pattern for overriding the label-derived announcement.
- **Streaming bubble unwired by design:** the transient streaming row has no stable id and can never carry a notice — it keeps the default no-op retry params.

## Deviations from Plan

### Auto-fixed Issues

None - plan executed exactly as written, with one documented layout clarification:

**1. [Clarification] Shared Text weight affects both branches' modifier (not copy)**
- **Found during:** Task 1
- **Issue:** Plan required the FETCH_FAILED branch "byte-identical". The `weight(1f, fill = false)` modifier lives on the shared `Text` (outside the `when`), so it technically touches the FETCH_FAILED layout path.
- **Fix:** None needed — copy strings are byte-identical, and `weight(fill = false)` is a no-op when no sibling competes for space (FETCH_FAILED never renders the button). The alternative (duplicating the Row per branch) would risk visual drift. Recorded here for the verifier.
- **Files modified:** `MessageBubble.kt` (already committed in `b534016`)

---

**Total deviations:** 0 auto-fixed, 1 clarification (no behavior impact)
**Impact on plan:** None — zero scope creep, zero new dependencies, no schema/navigation/provider/preview-sheet changes, Phase 53 polish surfaces untouched.

## Issues Encountered

None.

## Test Report

- `./gradlew :app:assembleDebug` — **BUILD SUCCESSFUL** (Task 1 gate)
- `./gradlew :app:testDebugUnitTest --tests "com.warped.ui.chat.*"` — **BUILD SUCCESSFUL, 45 tests, 0 failures, 0 errors** across 8 suites:
  - `ChatCancellationTest`: 6/6
  - `ChatGroundingRetryTest` (plan-01 exit gates): 8/8 — no regressions from UI wiring
  - `ChatGroundingToggleTest`: 4/4
  - `ChatKeyStabilityTest`: 4/4
  - `ChatSubStateTest`: 2/2
  - `MarkdownParserTest`: 10/10
  - `MarkdownTextInlineParsingTest`: 4/4
  - `SourcePreviewMappingTest`: 7/7
- No new tests added — UI plan reuses the plan-01 backend exit-gate suite plus existing regression coverage; Compose UI tests for the banner gate are covered by the release-UAT hardware backstop per the plan's verification contract.

## Threat Flags

None — no new attack surface. The button is visibility-hint-only: stale-flag taps are safe no-ops re-checked synchronously in `retryGrounding` (T-54-05), FETCH_FAILED turns are ineligible by data gate + branch gate (T-54-06), zero new dependencies (T-54-SC).

## Known Stubs

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- RETRY-01 fully delivered end-to-end (backend plan 01 + UI plan 02): offline users see the queued state, reconnect reveals Reintentar, tap reuses the Leyendo chip + Stop path, success renders Fuentes, failure preserves banner + retry.
- Held-out release-UAT backstop (WEB-05/WEB-06 precedent): queued suffix + Reintentar overflow check in both themes; offline-at-send → banner without button; resume → button appears; tap → Leyendo → Fuentes; Stop mid-retry → banner + Reintentar intact.

## Self-Check: PASSED

- `Reintentar` present in `MessageBubble.kt`; `refreshConnectivity` + `ON_RESUME` present in `ChatScreen.kt` (verified via implementation).
- Commits `b534016` and `00cb375` exist in `git log`.
- `assembleDebug` green; `ui.chat.*` 45/45 pass with fresh test-result XMLs.
- FETCH_FAILED copy strings unchanged (verified by diff review during edit).

---
*Phase: 54-offline-retry*
*Completed: 2026-09-28*
