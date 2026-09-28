---
phase: 51-syntax-theme-fix
plan: "01"
subsystem: ui
tags: [kotlin, compose, syntax-highlighting, highlights, junit5, truth]
requires:
  - phase: 50-web-grounding
    provides: [final chat code-block call sites this fix threads the theme through]
provides:
  - Theme-threaded SyntaxHighlighter call path (all 4 presets apply in chat code blocks)
  - Theme-aware highlight cache key plus source-of-truth comment
  - All-4-preset regression tests (per-preset highlight loop, cache separation, preset distinctness)
affects: [chat rendering, settings theme selector, future color-aware token mapping]
actuals:
  tokens: 2100
  tasks: 2
  commits: 2
tech-stack:
  added: []
  patterns: [theme-as-call-path-param with interface default, per-theme cache namespacing]
key-files:
  created: []
  modified:
    - app/src/main/java/com/warped/domain/highlighting/SyntaxHighlighter.kt
    - app/src/main/java/com/warped/data/highlighting/SyntaxHighlighterImpl.kt
    - app/src/main/java/com/warped/ui/chat/components/CodeBlock.kt
    - app/src/test/java/com/warped/data/highlighting/SyntaxHighlighterImplTest.kt
    - app/src/test/java/com/warped/domain/model/SyntaxThemeTest.kt
key-decisions:
  - "Kept the Monokai Highlights engine structure pass; domain SyntaxTheme variants stay the single color source of truth"
  - "Added a concrete-class 2-arg overload because Kotlin forbids defaults on overriding functions"
  - "Preset distinctness asserted on whole-variant maps, not per-slot, because locked palettes legitimately share light KEYWORD/BACKGROUND between Monokai and Dracula"
patterns-established:
  - "Theme threading via interface default param plus concrete overload for direct callers"
requirements-completed: [THEME-01, THEME-02]
coverage:
  - id: D1
    description: "Selected preset (Monokai/One Dark/GitHub/Dracula) threads through highlight call path into CodeBlock"
    requirement: "THEME-01"
    verification:
      - kind: unit
        ref: "SyntaxHighlighterImplTest#highlights python guide snippet under all 4 presets with KEYWORD each"
        status: pass
      - kind: unit
        ref: "SyntaxHighlighterImplTest#cache separates entries per theme"
        status: pass
    human_judgment: false
  - id: D2
    description: "Regression coverage fails if any preset is silently ignored or aliases another"
    requirement: "THEME-02"
    verification:
      - kind: unit
        ref: "SyntaxThemeTest$PresetDistinctness (6 tests: dark KEYWORD/BACKGROUND slots, whole-variant distinctness, fromKey round-trip)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Python guide code block visibly renders in each of the 4 presets, light and dark mode"
    requirement: "THEME-01"
    verification: []
    human_judgment: true
    rationale: "Token-color rendering is visual; unit tests prove data flow and distinctness but a human must confirm the visible result on device"
duration: 25min
completed: 2026-09-28
status: complete
---

# Phase 51: Syntax-Theme Fix Summary

**Theme-threaded highlighting call path so all 4 presets apply in chat code blocks, with per-preset regression tests**

## Performance

- **Duration:** 25 min
- **Started:** 2026-09-28T14:00:00Z
- **Completed:** 2026-09-28T14:25:00Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- `SyntaxHighlighter.highlight` accepts `theme: SyntaxTheme = MONOKAI`; `CodeBlock` passes `syntaxTheme` and keys `LaunchedEffect` on it
- `SyntaxHighlighterImpl` cache key namespaced per theme (`code|language|theme.key`); Monokai engine pass kept with source-of-truth comment
- Per-preset Python highlight loop plus cache-separation test; preset distinctness plus `fromKey` round-trip tests
- All 81 tests in the highlighting and theme suites pass; `assembleDebug` succeeds

## Task Commits

Each task was committed atomically:

1. **Task 1: Theme-threaded highlighting** - `7737c5f` (feat)
2. **Task 2: All-4-preset regression tests** - `e61af2e` (test)

## Files Created/Modified

- `app/src/main/java/com/warped/domain/highlighting/SyntaxHighlighter.kt` - theme default param on the interface
- `app/src/main/java/com/warped/data/highlighting/SyntaxHighlighterImpl.kt` - theme-aware cache key, 2-arg overload, source-of-truth comment
- `app/src/main/java/com/warped/ui/chat/components/CodeBlock.kt` - theme pass-through plus `LaunchedEffect` theme key
- `app/src/test/java/com/warped/data/highlighting/SyntaxHighlighterImplTest.kt` - per-preset loop plus cache-separation tests
- `app/src/test/java/com/warped/domain/model/SyntaxThemeTest.kt` - `PresetDistinctness` nested class (6 tests)

## Decisions Made

- Kept the Monokai Highlights engine structure pass: Highlights 1.1.0 ships no one_dark/github built-ins and `TypeMapper` consumes only `CodeStructure` locations, so per-preset engine swaps would only risk swatch/render mismatch. Domain variants remain the single color source of truth.
- Added a concrete-class 2-arg overload on `SyntaxHighlighterImpl` because Kotlin forbids default values on overriding functions; the interface default covers interface-typed callers, the overload covers direct concrete callers and keeps all pre-existing 2-arg tests unmodified.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Override default value rejected by the compiler**

- **Found during:** Task 1 (theme threading)
- **Issue:** Plan specified the default param on the impl; Kotlin error: "An overriding function is not allowed to specify default values for its parameters."
- **Fix:** Default lives on the interface only; added a delegating 2-arg overload on `SyntaxHighlighterImpl`.
- **Files modified:** `app/src/main/java/com/warped/data/highlighting/SyntaxHighlighterImpl.kt`
- **Verification:** Full highlighting suite passes unmodified (2-arg calls resolve); `assembleDebug` succeeds.
- **Committed in:** 7737c5f (part of task commit)

**2. [Rule 2 - Missing Critical] Per-slot light distinctness contradicts locked palettes**

- **Found during:** Task 2 (regression tests)
- **Issue:** Plan required 4 distinct values per slot in both variants, but locked palettes share light KEYWORD (`0xFFE62D6B`) and light BACKGROUND (`0xFFF8F8F2`) between Monokai and Dracula, and dark PUNCTUATION/PLAIN too. Per-slot assertions would fail without palette edits, which are prohibited.
- **Fix:** Per-slot distinctness asserted only where palettes genuinely differ (dark KEYWORD, dark BACKGROUND); whole-variant map distinctness asserted for both variants, which still fails if any preset silently aliases another.
- **Files modified:** `app/src/test/java/com/warped/domain/model/SyntaxThemeTest.kt`
- **Verification:** `PresetDistinctness` 6/6 pass; no palette hex touched (`git diff` shows zero changes to `SyntaxTheme.kt`).
- **Committed in:** e61af2e (part of task commit)

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 missing critical)
**Impact on plan:** Both required for correctness; no scope creep. No palette, storage, or streaming-behavior changes.

## Issues Encountered

None beyond the two auto-fixed deviations above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 51 is the final v2.2 phase; no downstream phase depends on it.
- Device visual check (Python guide in all 4 presets, light plus dark) remains for release UAT, matching the deferred-smoke pattern of Phases 49 and 50.

---
*Phase: 51-syntax-theme-fix*
*Completed: 2026-09-28*
