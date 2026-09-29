---
phase: 55-tavily-search-foundation
plan: "02"
subsystem: ui
tags: [tavily, settings, keystore, grounding, chat, gates, citations]

# Dependency graph
requires:
  - phase: 55-tavily-search-foundation
    provides: TavilySearchOutcome Grounded/ModelOnly/MissingKey/InvalidKey/UsageLimit + storeTavilyKey/getTavilyKey/deleteTavilyKey (55-01)
  - phase: 52-fetch-orchestrator
    provides: GroundingPrompt.augment fused-block contract + OK/OMITIDA progress snapshot pattern
  - phase: 53-source-persistence
    provides: saveMessageWithSources details-union persist for Fuentes rows
provides:
  - Settings Tavily key row (Web Search card) with save/clear/test-connection + four English states
  - ChatViewModel search branch at doGround && urls.isEmpty() with online + key-present gates
  - TAVILY_MISSING_KEY / TAVILY_INVALID_KEY / TAVILY_LIMIT model-only notices with actionable English copy
  - SettingsTavilyTest (17 JVM tests: key state, gate matrix, fusion wiring)
affects: [phase-56-web-search-tool, agentic-local-loop]

# Tech tracking
tech-stack:
  added: []
  patterns: [repository-owned-key-gate-with-viewmodel-online-gate, probe-search-max-results-1, notice-enum-extension-for-gated-turns]

key-files:
  created:
    - app/src/test/java/com/warped/ui/settings/SettingsTavilyTest.kt
  modified:
    - app/src/main/java/com/warped/ui/settings/SettingsViewModel.kt
    - app/src/main/java/com/warped/ui/settings/SettingsScreen.kt
    - app/src/main/java/com/warped/ui/settings/SettingsUiState.kt
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/domain/model/ChatMessage.kt
    - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
    - app/src/test/java/com/warped/ui/settings/SettingsGroundingToggleTest.kt
    - app/src/test/java/com/warped/ui/chat/ChatGroundingToggleTest.kt
    - app/src/test/java/com/warped/ui/chat/ChatGroundingRetryTest.kt
    - app/src/test/java/com/warped/ui/chat/ChatCancellationTest.kt
    - app/src/test/java/com/warped/ui/chat/ChatSubStateTest.kt

key-decisions:
  - "Key-present gate lives in the repository (55-01 MissingKey short-circuits pre-socket); the ViewModel owns only the online gate — no ApiKeyStore dependency added to ChatViewModel"
  - "Model-only turns keep the always-on SYSTEM_PROMPT prefix (existing no-URL behavior preserved; ToggleTest request-text assertions byte-identical)"
  - "T-55-08 probe is search('test', max_results=1) with card copy disclosing the 1-credit cost"

patterns-established:
  - "Gated third-party branch: online check (try/catch-to-offline) -> progress spinner -> repository outcome when -> identical augment/persist path as the sibling branch"

requirements-completed: [TAV-01, TAV-02, TAV-03]
---

# Phase 55 Plan 02: Settings + Chat Hook Summary

**User-visible Tavily half: Keystore-backed key row with test-connection in Settings, and the ChatViewModel search branch that grounds no-URL turns in top-5 Tavily results through the identical Fuentes/citations pipeline — with honest offline and key gates.**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-09-28T23:44:46Z
- **Completed:** 2026-09-28T23:47:26Z
- **Tasks:** 3
- **Files modified:** 12 (1 created, 11 modified)

## Accomplishments

- Settings shows a Web Search card (above Security, Security card untouched) with password-style key field, Save/Clear/Test connection, presence line, and status line covering success / invalid-key / usage-limit / network-error plus missing-key guidance — all English, 1-credit cost disclosed (TAV-01 complete)
- A grounded turn with zero pasted URLs searches Tavily (top-5, basic depth) and fuses numbered `Source [N]` citations through the identical augment → groundedSources → details-union persist path as URL grounding (TAV-02 complete)
- Grounding-off and offline turns never open a search socket (OFFLINE notice, no search attempt); missing key names tavily.com + the Settings path; 401/429 get distinct copy; retry stays OFFLINE-only (TAV-03 complete)
- Full unit suite green: 381 tests across 40 suites, 0 failures (364 pre-existing + 17 new)

## Task Commits

Each task was committed atomically:

1. **Task 1: Settings Tavily key row + test-connection** - `e31f4181` (feat)
2. **Task 2: ChatViewModel search branch with D-03 gates** - `cfe0d862` (feat)
3. **Task 3: Unit tests — key state, gate matrix, fusion wiring** - `51bb5644` (test)

## Files Created/Modified

- `app/src/main/java/com/warped/ui/settings/SettingsUiState.kt` - tavilyKeyInput/KeyPresent/Testing/Status/StatusIsError fields
- `app/src/main/java/com/warped/ui/settings/SettingsViewModel.kt` - Tavily key state via StateFlow, Keystore alias save/clear/presence (chars zeroed), test-connection mapping 5 outcomes to English states
- `app/src/main/java/com/warped/ui/settings/SettingsScreen.kt` - TavilyKeyCard (Web Search section above Security): password field, Save/Clear/Test buttons, status line, 1-credit note
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` - search branch at the urls.isEmpty() bypass point with online gate + outcome mapping + identical fusion path on IO
- `app/src/main/java/com/warped/domain/model/ChatMessage.kt` - ModelOnlyNotice += TAVILY_MISSING_KEY / TAVILY_INVALID_KEY / TAVILY_LIMIT
- `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt` - actionable English banner copy per new notice (tavily.com + Settings > Web Search)
- `app/src/test/java/com/warped/ui/settings/SettingsTavilyTest.kt` - 17 JVM tests (JUnit5, Truth, MockK, runTest)
- 5 existing test builders updated for new constructor params (mechanical `tavilySearchRepository = mockk()` additions, zero assertion changes)

## Decisions Made

- **Repository owns the key-present gate:** the ViewModel checks only `doGround` + `hasValidatedInternet()` and delegates key presence to `TavilySearchRepository.search()`, which returns `MissingKey` before opening any socket (55-01). This avoided adding an `ApiKeyStore` dependency to ChatViewModel and keeps the gate in one place. Verified: no socket on missing key by construction (key read precedes the Retrofit call).
- **SYSTEM_PROMPT preserved on model-only search turns:** offline/missing/invalid/limit turns still carry the always-on web instruction (existing no-URL behavior), so `ChatGroundingToggleTest` request-text assertions pass byte-identical. The banner carries the actionable copy, not the prompt.
- **Cheapest truthful probe:** test-connection is `search("test", max_results=1, basic)` = exactly 1 credit; the card carries the static note "Test connection uses one search credit." No auto-retest loops (T-55-08).
- **Password field, no key echo:** the saved key is never read back into the field — only a "Key saved"/"No key saved" presence line. Input state is cleared on save/clear (Strings are immutable so GC-side copies are unavoidable; state retention is the mitigated surface).

## Deviations from Plan

None - plan executed exactly as written. Two Rule 3 blocking fixes inside task scope (no user permission needed):

1. **[Rule 3] Existing test builders needed new constructor args** — adding `tavilySearchRepository` to ChatViewModel/SettingsViewModel broke compilation of 5 existing test files. Fixed with mechanical `= mockk()` additions; zero assertion changes; all pre-existing tests pass unmodified in behavior.
2. **[Rule 3] `ModelOnlyNotice` enum extension required banner branches** — the exhaustive `when` in `ModelOnlyBanner` forced copy for the three new notices. Added per the plan's copy requirements (English, tavily.com + Settings path).

## Threat Flags

None. All new security-relevant surface was in the plan's threat model and mitigated as specified:
- T-55-05: password field, no stored-key echo, input cleared after save, presence-check chars zeroed, no key/payload in logs (verified: only 3 static-tag `Timber.w(e)` calls; zero `Bearer`/key literals in touched files)
- T-55-06: search fires only after online + key-present gates; offline path opens no socket (connectivity read is a `ConnectivityManager` capability check, not a socket); per-call Bearer header from 55-01
- T-55-07: no new prompt path — identical `GroundingPrompt.augment()` call as URL grounding; snippets sanitized producer-side (55-01)
- T-55-08: cheapest probe (max_results 1, basic) + 1-credit disclosure + testing-guard against double-tap loops
- T-55-SC: zero new dependencies — no installs

## Known Stubs

None. All paths wired: save/clear/test round-trip, gate matrix, fusion identity, banner copy, persist union.

## Issues Encountered

None. First compile and first test run passed without fixes (existing-test impact was predicted during context reading: all no-URL sends in old tests are grounding-off or offline-gated; all online sends carry URLs — confirmed green by the full suite).

## Budget-Floor Flag for Phase 56 (RESEARCH Pitfall 4)

Default 5 search results hit the identical floor math as a 5-URL fetch (`perPageBudget(contextSize, 5)` → 1500-char floor): at contextSize 4096 the fused block can reach ~7500 chars vs the 6000 global budget; at ≤2048 windows ~7500 vs 4500 — an overrun. This **matches existing fetchAll floor behavior** (not a regression), but Phase 56 should consider trimming `maxResults` for small windows when it owns query planning.

## Verification

- `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest --tests "com.warped.ui.settings.SettingsTavilyTest"` — 17 tests, 0 failures
- `./gradlew :app:testDebugUnitTest` (full) — 381 tests across 40 suites, 0 failures, 0 errors, 0 skipped
- Gate proof: offline test verifies `search` never called + OFFLINE notice; fused test verifies `search("que hay de nuevo", 5, _)` + `GroundingPrompt.augment` identity + `saveMessageWithSources` OK/OMITIDA union
- Grep gates: no key material/Bearer literals in touched files; stored key never echoed to UI
- Manual tracer (needs real key, device/emulator — NOT run): paste key → Test shows success → grounded no-URL turn returns cited answer with Fuentes rows → airplane-mode turn shows offline path → clear-key turn shows missing-key message

## User Setup Required

None - no external service configuration required. (Live E2E needs a user-provided Tavily key at runtime; unit tests use mocks.)

## Next Phase Readiness

- Ready for Phase 56: `TavilySearchRepository.search(query, maxResults, contextSize)` is the `web_search` tool implementation; the ChatViewModel branch is the direct search→ground path the agentic loop will subsume. Consider the budget-floor flag above when choosing per-call `maxResults`.
- Concern: none blocking.

## Self-Check: PASSED

- All 12 files exist on disk (1 created + 11 modified)
- All 3 task commits exist (`e31f4181`, `cfe0d862`, `51bb5644`)
- No unintended file deletions in any task commit

---
*Phase: 55-tavily-search-foundation*
*Completed: 2026-09-28*
