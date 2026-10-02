---
phase: 66-play-in-app-review
plan: 01
subsystem: monetization-growth
tags: [play-core, review-ktx, in-app-review, datastore, settings, chat]

# Dependency graph
requires: []
provides:
  - Ambient Play In-App Review trigger (turn counter + cooldown + cap in DataStore, silent ReviewManager flow)
  - Always-reachable Play Store listing entry in Settings (market:// with https fallback)
affects: [future rating/engagement phases reusing ReviewPreferences counters]

# Tech tracking
tech-stack:
  added: [com.google.android.play:review-ktx:2.0.2]
  patterns: [fire-and-forget ViewModel child launch with coroutineExceptionHandler, Activity-as-method-param for Play flows, market-first external intent with https fallback]

key-files:
  created:
    - app/src/main/java/com/warped/data/local/preferences/ReviewPreferences.kt
    - app/src/main/java/com/warped/domain/review/ReviewHelper.kt
    - app/src/test/java/com/warped/domain/review/ReviewEligibilityTest.kt
  modified:
    - gradle/libs.versions.toml
    - app/build.gradle.kts
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
    - app/src/main/java/com/warped/ui/settings/SettingsScreen.kt
    - app/src/main/java/com/warped/ui/chat/components/BrowserIntents.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-es/strings.xml

key-decisions:
  - "Eligibility policy: >=5 completed turns, 21-day cooldown, max 3 prompts (single-digit turns, weeks-scale cooldown per CONTEXT)"
  - "Activity reaches the review flow via a nullable provider set by ChatScreen from LocalContext (cleared on dispose) — ViewModel keeps only app context"
  - "Play Task bridged with suspendCancellableCoroutine (no new coroutines-play-services dependency, honoring review-ktx-only constraint)"
  - "Store-entry tap failure toasts toast_no_browser (explicit user tap deserves feedback); dialog-flow failures stay fully silent"

patterns-established:
  - "ReviewEligibility pure predicate (zero Android imports) for JVM-testable policy"
  - "openPlayStoreListing as market-first sibling of openUrlInBrowser (never route market: through the http/https allowlist)"

requirements-completed: [RATE-01, RATE-02]

# Metrics
duration: 35min
completed: 2026-10-02
---

# Phase 66 Plan 01: Play In-App Review Summary

**Ambient Play review prompt after completed chats (5-turn/21-day/3-cap policy, fully silent on quota) plus an always-reachable Settings Store entry, fired fire-and-forget so chat streaming never stalls**

## Performance

- **Duration:** ~35 min
- **Started:** 2026-10-02T16:10:00Z
- **Completed:** 2026-10-02T16:45:00Z
- **Tasks:** 2/2
- **Files modified:** 14 (3 created source/test, 11 modified incl. 17-test call-site sweep counted as one logical change)

## Accomplishments

- review-ktx 2.0.2 declared in version catalog (Google Maven metadata-verified latest stable) and implemented in app build script; no other new dependencies
- ReviewPreferences DataStore store (`review_preferences`: completed_turns int, last_prompt_millis long, prompt_count int) with Flow reads and suspend writes, self-registering @Singleton (WizardPreferences convention, no module)
- ReviewHelper with pure `ReviewEligibility.isEligible` predicate + silent `maybePrompt(activity)` (Activity as method param, never stored; Task bridged via suspendCancellableCoroutine; all failures Timber.w + silent return)
- Turn-Done hook in ChatViewModel after the persist block: `viewModelScope.launch(coroutineExceptionHandler)` calling maybePrompt; Activity resolved via UI-set provider, null skips silently; Error branch / silent-turn else untouched
- Settings review card reusing wizard-card shape + accent in App section, wired to new `openPlayStoreListing` (market:// first, https fallback, dual ActivityNotFoundException/SecurityException catch, packageName-derived URI only)
- EN+ES `settings_review_title/desc/action` string pairs; full unit suite green (905 tests, 0 failures); auditDependencies passes

## Task Commits

Each task was committed atomically:

1. **Task 1: Dependency + ReviewPreferences + ReviewHelper + strings** - `43c9a155` (feat)
2. **Task 2: ChatViewModel hook + Settings Store entry + market intent** - `4310a48e` (feat)

## Files Created/Modified

- `app/src/main/java/com/warped/data/local/preferences/ReviewPreferences.kt` - DataStore turn counter + cooldown/prompt-count persistence
- `app/src/main/java/com/warped/domain/review/ReviewHelper.kt` - ReviewEligibility predicate + silent ReviewManager flow
- `app/src/test/java/com/warped/domain/review/ReviewEligibilityTest.kt` - 6-test JVM truth table for the policy
- `gradle/libs.versions.toml` - play-review 2.0.2 version + play-review-ktx library entries
- `app/build.gradle.kts` - implementation(libs.play.review.ktx) under Play In-App Review comment
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` - ReviewHelper injection, reviewActivityProvider, turn-Done fire-and-forget hook
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` - DisposableEffect publishing LocalContext Activity provider
- `app/src/main/java/com/warped/ui/settings/SettingsScreen.kt` - review card in App section
- `app/src/main/java/com/warped/ui/chat/components/BrowserIntents.kt` - openPlayStoreListing market-first intent
- `app/src/main/res/values/strings.xml` + `values-es/strings.xml` - settings_review_title/desc/action pairs

## Decisions Made

- Eligibility thresholds set to >=5 turns / 21-day cooldown / max 3 prompts (agent discretion per CONTEXT: single-digit turns, weeks-scale cooldown).
- review-ktx 2.0.2 pinned after verifying `latest`/`release` 2.0.2 in Google Maven `review-ktx/maven-metadata.xml` (2026-10-02); versions 2.0.0/2.0.1 are the only others.
- `CancellableContinuation.resume(value)` single-arg overload unavailable in this Kotlin version (requires onCancellation param); used `resumeWith(Result.success(...))` instead.
- Store-entry total failure toasts `toast_no_browser` (explicit tap feedback, consistent with openUrlInBrowser); the ambient dialog flow stays fully silent per RATE-01.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Malformed BrowserIntents.kt after intent insertion**
- **Found during:** Task 2 (market intent)
- **Issue:** Edit replaced the `openUrlInBrowser` signature line instead of inserting after it, nesting `openPlayStoreListing` inside the original function body
- **Fix:** Rewrote the file cleanly with both top-level functions intact and verified bodies
- **Files modified:** app/src/main/java/com/warped/ui/chat/components/BrowserIntents.kt
- **Verification:** grep shows both `fun` definitions; compileDebugKotlin green
- **Committed in:** 4310a48e (part of task commit)

**2. [Rule 3 - Blocking] 17 existing chat tests missing new ReviewHelper constructor param**
- **Found during:** Task 2 (full unit suite)
- **Issue:** Adding `ReviewHelper` to ChatViewModel's @Inject constructor broke `compileDebugUnitTestKotlin` — 17 test files construct the ViewModel manually with named args
- **Fix:** Added `reviewHelper = mockk(relaxed = true)` at each of the 18 construction sites plus the `ReviewHelper` import per file (relaxed so any hook firing in tests is a harmless no-op)
- **Files modified:** 17 files under app/src/test/java/com/warped/ui/chat/
- **Verification:** Full `:app:testDebugUnitTest` green — 905 tests, 0 failures, 0 errors
- **Committed in:** 4310a48e (part of task commit)

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking)
**Impact on plan:** Both necessary for correctness/compilation. No scope creep — no visible Rate button, no new event bus, no new dependencies.

## Issues Encountered

- Kotlin `CancellableContinuation.resume(T)` single-arg form not available (compiler demanded `onCancellation`); switched to `resumeWith(Result.success(...))` — compile green.
- Full Gradle test task initially failed at `compileDebugUnitTestKotlin` (not test failures) due to deviation #2 above; resolved by patching call sites.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- RATE-01/RATE-02 complete: ambient prompt + quota-proof Store entry shipped, chat path non-blocking by construction (child launch, tryEmit-free, no runBlocking/suspend on turn path).
- Review copy (EN+ES) drafted for user wording review in code review per CONTEXT.
- No blockers. Note: Play review dialog quota behavior is server-side; on-device verification of the dialog itself requires a Play-signed build.

---
*Phase: 66-play-in-app-review*
*Completed: 2026-10-02*

## Self-Check: PASSED

All created files exist on disk; both task commits verified in git log; no stub patterns in new review files.
