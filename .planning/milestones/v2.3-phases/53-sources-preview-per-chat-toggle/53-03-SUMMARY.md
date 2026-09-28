---
phase: 53-sources-preview-per-chat-toggle
plan: "03"
subsystem: ui
tags: [compose, bottom-sheet, fuentes, preview, browser-intent]
requires:
  - phase: 53-sources-preview-per-chat-toggle
    provides: GroundedSource details contract + hydrated shape (53-01)
provides:
  - SourcePreviewSheet pure-render sheet over hydrated state (zero I/O)
  - Clickable Fuentes list (ok → sheet, omitida struck/disabled) + guarded ACTION_VIEW
  - SourcePreviewMappingTest (7 JVM mapper tests)
affects: [53-04 (toggle UI reuses sheet/Fuentes surfaces), 54-offline-retry (rows are the retry read-model)]

tech-stack:
  added: []
  patterns: [ModalBottomSheet copied from ModelSelector, local remember sheet state re-resolved from message param, pure mapper functions for JVM-testable tap logic]

key-files:
  created:
    - app/src/main/java/com/warped/ui/chat/components/SourcePreviewSheet.kt
    - app/src/test/java/com/warped/ui/chat/SourcePreviewMappingTest.kt
  modified:
    - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt

key-decisions:
  - "Sheet takes an onOpenBrowser callback; MessageBubble owns the guarded ACTION_VIEW so the sheet stays pure-render with zero Android-intent imports"
  - "Toast (not Snackbar) for the browserless-device fallback — no SnackbarHost exists in MessageBubble scope; screens own the hosts"
  - "fuenteItems prefers hydrated details, falls back to legacy ok-only urls so pre-53-02 history still renders"
  - "Legacy taps open the sheet with the empty-extract copy + browser button instead of being dead rows"

requirements-completed: [SRC-01, SRC-02, SRC-03]

duration: 12min
completed: 2026-09-28
---

# Phase 53 Plan 03: Sources Preview Sheet + Clickable Fuentes Summary

**Clickable Fuentes list in fetch-block order opening a zero-I/O bottom-sheet preview (number + resolved URL + scrollable verbatim text + Abrir en navegador), omitida rows struck/disabled, browser intent guarded — 7 mapper tests + full suite green**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-09-28T16:52Z (approx)
- **Completed:** 2026-09-28T17:04Z
- **Tasks:** 3/3
- **Files modified:** 3 (1 created composable, 1 upgraded bubble, 1 created test)

## Accomplishments

- SourcePreviewSheet per CONTEXT/UI-SPEC: ModelSelector ModalBottomSheet scaffold (skipPartiallyExpanded, surface container), drag-handle clearance, Fuente N title, [N] badge + single-line ellipsis URL header, divider, weight-based sticky action row with FilledTonalButton Abrir en navegador; scrollable verbatim body preserving the Phase 52 truncado suffix; UI-SPEC empty-extract Spanish copy with button staying available
- MessageBubble Fuentes upgrade: ok items clickable (SemanticsRole.Button, 44dp targets via 12dp vertical padding, "Vista previa de la fuente N" label, ripple via clip+clickable); omitida rows "[N] url — omitida" in onSurfaceVariant + LineThrough, no clickable; zero-ok → no block (unchanged); local previewSource/previewNumber remember re-resolved from the message param, never in the ViewModel
- Guarded browser intent: ACTION_VIEW with Uri.parse of the resolved Grounded.url only (never pasted text, never extracted text), sheet dismisses on press, ActivityNotFoundException → Toast fallback so browserless emulators never crash chat
- Pure mapper layer (fuenteItems, previewForTap, isEmptyExtract, browserTarget) covered by 7 JVM tests; full suite 263 tests, 0 failures

## Task Commits

Each task was committed atomically:

1. **Task 1: SourcePreviewSheet composable from ModelSelector pattern** - `f143f99` (feat)
2. **Task 2: Clickable Fuentes list plus sheet host and guarded browser intent** - `9acb354` (feat)
3. **Task 3: Preview mapping tests plus full build** - `1286d61` (test)

## Files Created/Modified

- `app/src/main/java/com/warped/ui/chat/components/SourcePreviewSheet.kt` - NEW: sheet composable + FuenteItem + 4 pure mappers + EMPTY_EXTRACT_COPY
- `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt` - clickable Fuentes + sheet host + guarded intent (+91/-12)
- `app/src/test/java/com/warped/ui/chat/SourcePreviewMappingTest.kt` - NEW: 7 JVM tests

## Decisions Made

- Sheet exposes onOpenBrowser callback; the guarded intent lives in MessageBubble — keeps the sheet free of Android-intent imports and purely props-driven (zero I/O by construction, verified by grep: no fetcher references outside a doc comment).
- Toast instead of Snackbar for the browserless fallback (see deviations): the only SnackbarHosts live in screens, unreachable from MessageBubble without touching ChatScreen (out of scope, avoids overlap with sibling plans).
- Legacy ok-only rows (no hydrated details) become clickable and open the sheet on the empty-extract path rather than staying dead — consistent forward behavior once 53-02 hydrates everything.
- 12dp vertical padding per row (≈44dp targets) absorbs the old 4dp inter-item gaps; omitida rows keep the same rhythm without click affordance.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Semantics Role import clashed with domain Role**
- **Found during:** Task 2 (compile gate)
- **Issue:** `import androidx.compose.ui.semantics.Role` collided with the existing `com.warped.domain.model.Role` import — ambiguous reference broke USER/TOOL resolution.
- **Fix:** Aliased to `SemanticsRole`; `clickable(role = SemanticsRole.Button)`.
- **Files modified:** app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
- **Verification:** ./gradlew :app:compileDebugKotlin BUILD SUCCESSFUL
- **Committed in:** 9acb354 (part of Task 2 commit)

**2. [Rule 2 - Missing critical] Snackbar fallback unreachable from MessageBubble scope**
- **Found during:** Task 2 (browser-intent guard)
- **Issue:** Plan specifies a Snackbar fallback for ActivityNotFoundException, but the only SnackbarHosts live in screens (ChatScreen owns one); MessageBubble has no host access, and threading one through ChatScreen would touch out-of-scope files shared with sibling plans.
- **Fix:** Toast with Spanish copy "No se encontró un navegador para abrir el enlace." — same non-crash guarantee, available mechanism in scope.
- **Files modified:** app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
- **Verification:** compile green; guard covered by code review (intent not JVM-assertable per plan)
- **Committed in:** 9acb354 (part of Task 2 commit)

**3. [Rule 2 - Missing critical] Legacy-rows tap target when details are absent**
- **Found during:** Task 2 (Fuentes upgrade)
- **Issue:** Plan assumes hydrated details; with 53-02 running in parallel, history may still carry legacy ok-only urls with empty details — leaving those rows clickable-to-nothing or dead.
- **Fix:** Legacy taps synthesize `GroundedSource(url)` so the sheet opens on the empty-extract path with the browser button available.
- **Files modified:** app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt (+1 test in SourcePreviewMappingTest.kt)
- **Verification:** mapping tests green
- **Committed in:** 9acb354 / 1286d61

**Checker advisory applied:** Task 2 verify ran compileDebugKotlin only; mapping-test execution deferred to Task 3's verify as instructed (SourcePreviewMappingTest is created in Task 3).

---

**Total deviations:** 3 auto-fixed (1 bug, 2 missing-critical)
**Impact on plan:** All three preserve the plan's success criteria; no scope creep, zero new dependencies.

## Issues Encountered

None blocking. No auth gates, no architectural questions.

## Threat Flags

None — no surface beyond the plan's threat model. T-53-08 mitigated (sheet renders persisted text via Text(), never HTML; no WebView, no fetcher calls in sheet code — grep-verified). T-53-09/T-53-10 mitigated (browserTarget returns Grounded.url only; intent carries the URL alone). T-53-11 mitigated (ActivityNotFoundException guard; Toast mechanism substituted for Snackbar, same availability guarantee). T-53-SC clean (zero new deps).

## Known Stubs

None — stub scan over new/modified files found no TODO/FIXME/placeholder/empty-value patterns. The legacy-row `GroundedSource(url)` synthesis is intentional forward-compat (documented above), not a stub.

## Verification Results

- `./gradlew :app:compileDebugKotlin` (Tasks 1, 2) — BUILD SUCCESSFUL
- `./gradlew :app:assembleDebug :app:testDebugUnitTest` (Task 3) — BUILD SUCCESSFUL
- `SourcePreviewMappingTest` — 7/7 pass, 0 failures, 0 errors
- Full unit suite — 263 tests, 0 failures, 0 errors, 0 skipped
- Sheet zero-I/O: grep for fetcher/fetch( in SourcePreviewSheet.kt returns only a doc comment
- Ready for WEB-06 device smoke (sheet open + browser intent on hardware)

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- 53-04 (toggle UI) can reuse the Fuentes/sheet surfaces untouched; per-chat Sí/No/Heredar + Sin web chip land in ChatScreen/menu scope with no overlap.
- 54-offline-retry consumes the same persisted rows this sheet reads; no preview changes needed.
- Device-smoke backlog: preview-open + browser-intent on hardware (WEB-06 precedent), both themes for long-extract scroll + long-URL ellipsis (UI-SPEC backstop).

## Self-Check: PASSED

- All 2 created files exist on disk (SourcePreviewSheet.kt, SourcePreviewMappingTest.kt)
- All 3 task commits exist in git log (f143f99, 9acb354, 1286d61)
- Test XML confirms 7/7 SourcePreviewMappingTest green; suite total 263, 0 failures

---
*Phase: 53-sources-preview-per-chat-toggle*
*Completed: 2026-09-28*
