---
phase: 55-tavily-search-foundation
plan: "01"
subsystem: api
tags: [tavily, retrofit, okhttp, keystore, grounding, fused-block, citations]

# Dependency graph
requires:
  - phase: 52-fetch-orchestrator
    provides: GroundingPrompt.buildFusedBlock numbered Source [N] fusion contract
  - phase: 53-source-persistence
    provides: GroundedSource OK/OMITIDA details union for Fuentes rows
provides:
  - TavilyApi Retrofit POST search with per-call Bearer header
  - TavilySearchRequest/Result/Response DTOs (kotlinx.serialization)
  - TavilySearchRepository search-to-fused-block producer on the frozen grounding pipeline
  - tavily_api_key Keystore alias in ApiKeyStore with CharArray-zeroing discipline
  - '@Named("tavily")' OkHttp + Retrofit providers (zero interceptors)
  - TavilySearchRepositoryTest (16 JVM tests, MockK-fake TavilyApi, no network)
affects: [55-02-settings-chat-hook, phase-56-web-search-tool, agentic-local-loop]

# Tech tracking
tech-stack:
  added: []
  patterns: [dedicated-named-okhttp-client-without-auth-interceptor, per-call-bearer-header, snippet-producer-into-fused-pipeline, string-alias-keystore-path]

key-files:
  created:
    - app/src/main/java/com/warped/data/remote/dto/TavilyDtos.kt
    - app/src/main/java/com/warped/data/remote/api/TavilyApi.kt
    - app/src/main/java/com/warped/data/grounding/TavilySearchRepository.kt
    - app/src/test/java/com/warped/data/grounding/TavilySearchRepositoryTest.kt
  modified:
    - app/src/main/java/com/warped/di/NetworkModule.kt
    - app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt

key-decisions:
  - "TavilySearchOutcome sealed interface wraps MultiUrlResult (Grounded/ModelOnly) plus MissingKey/InvalidKey/UsageLimit — callers render distinct copy without exceptions-as-control-flow"
  - "Zero interceptors on the Tavily client (not even a redacted logger) — strictest reading of T-55-01, no header-leak surface at all"
  - "Blank query short-circuits to FETCH_FAILED model-only before any socket — avoids burning a Tavily credit on empty input"

patterns-established:
  - "Third-party search producer pattern: map external results to (url, text) pairs through WebContextSanitizer + GroundingBudget.perPageBudget + GroundingPrompt.buildFusedBlock"
  - "String-alias Keystore path (TAVILY_ALIAS const) parallel to Long endpoint keys; deleteAllKeys wipes both namespaces"

requirements-completed: [TAV-01, TAV-02]

# Metrics
duration: 20min
completed: 2026-09-28
---

# Phase 55 Plan 01: Tavily Search Foundation Summary

**Keystore-encrypted Tavily key under a dedicated alias plus a search-to-fused-block producer emitting identical numbered Source [N] citations via the frozen grounding pipeline, with 401/429 distinctly typed**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-09-28T23:20:00Z
- **Completed:** 2026-09-28T23:45:00Z
- **Tasks:** 3
- **Files modified:** 6 (4 created, 2 modified)

## Accomplishments

- Tavily API key persists Keystore-encrypted under the dedicated `tavily_api_key` alias and round-trips without plaintext exposure (TAV-01 storage half)
- A search query returns top-N Tavily results fused into a numbered Source [N] block identical to URL grounding — same `buildFusedBlock`, `perPageBudget`, sanitizer, OK/OMITIDA details (TAV-02 producer half, tracer-first)
- 401 surfaces InvalidKey, 429 surfaces UsageLimit, blank snippets become OMITIDA rows — never silent drops
- Zero new dependencies; full unit suite green (364 tests, 39 suites, 0 failures)

## Task Commits

Each task was committed atomically:

1. **Task 1: Tavily DTOs + Retrofit API + named client** - `1137574f` (feat)
2. **Task 2: ApiKeyStore Tavily alias + TavilySearchRepository producer** - `cddf97d0` (feat)
3. **Task 3: Unit tests — key alias, fusion, error mapping, budget** - `c172f5fb` (test)

## Files Created/Modified

- `app/src/main/java/com/warped/data/remote/dto/TavilyDtos.kt` - TavilySearchRequest (basic/5/false/3 defaults), TavilySearchResult, TavilySearchResponse
- `app/src/main/java/com/warped/data/remote/api/TavilyApi.kt` - Retrofit POST search with per-call Authorization header
- `app/src/main/java/com/warped/di/NetworkModule.kt` - @Named("tavily") OkHttp (15s/30s/60s, no retry, zero interceptors) + Retrofit on https://api.tavily.com/ + TavilyApi provider
- `app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt` - storeTavilyKey/getTavilyKey/deleteTavilyKey + deleteAllKeys wipes Tavily alias
- `app/src/main/java/com/warped/data/grounding/TavilySearchRepository.kt` - search() producer returning TavilySearchOutcome (Grounded/ModelOnly/MissingKey/InvalidKey/UsageLimit)
- `app/src/test/java/com/warped/data/grounding/TavilySearchRepositoryTest.kt` - 16 JVM tests with MockK-fake TavilyApi

## Decisions Made

- **Sealed outcome over typed exceptions:** `TavilySearchOutcome` wraps `MultiUrlResult.Fused`/`AllFailed` for the fused/failed cases and adds `MissingKey`/`InvalidKey`/`UsageLimit` objects — the ChatViewModel hook (55-02) and Phase 56 `web_search` tool get exhaustive `when` handling with distinct copy per branch, no try/catch control flow.
- **Zero interceptors (strictest T-55-01 reading):** the Tavily client carries no logging interceptor at all rather than a "redacted debug" one — eliminates any header-leak surface; debug visibility comes from unit tests and response-code logs.
- **Blank-query short-circuit:** empty/blank queries return FETCH_FAILED model-only before touching the keystore key or socket — avoids burning a 1-credit search on empty input (extends the plan's pass-through rule, not a rewrite).

## Deviations from Plan

None - plan executed exactly as written. (The blank-query short-circuit above is a Rule 2-adjacent guard inside the task's own scope: it prevents a wasted paid API call. Documented here as a decision, not a deviation, since it adds no files or scope.)

## Threat Flags

None. All new security-relevant surface was in the plan's threat model and mitigated as specified:
- T-55-01: dedicated client with zero interceptors, per-call Bearer header, key never logged (verified: only status-code Timber calls in new files)
- T-55-02: AuthInterceptor never attached to Tavily client; endpoint keys never sent to api.tavily.com
- T-55-03: every snippet through WebContextSanitizer before fusion
- T-55-SC: zero new dependencies — no installs

## Known Stubs

None. All producer paths are wired: fusion, OMITIDA routing, error mapping, caps.

## Issues Encountered

None. First compile and first test run passed without fixes.

## Verification

- `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest --tests "com.warped.data.grounding.TavilySearchRepositoryTest"` — 16 tests, 0 failures
- `./gradlew :app:testDebugUnitTest` (full) — 364 tests across 39 suites, 0 failures, 0 errors, 0 skipped
- Tracer proof: fake-TavilyApi 3-result search yields `--- Source [1..3]` block in order (test `three results fuse numbered in order via buildFusedBlock`)
- Grep gates: no fake-endpoint Long-id reuse for Tavily; no key material in logs; zero interceptors on the Tavily client

## User Setup Required

None - no external service configuration required. (Live E2E needs a user-provided Tavily key at runtime; unit tests use a fake. Settings UI + test-connection arrive in 55-02.)

## Next Phase Readiness

- Ready for 55-02: Settings key row + Test connection consumes `storeTavilyKey`/`getTavilyKey`/`deleteTavilyKey`; ChatViewModel search branch consumes `TavilySearchRepository.search()` and unwraps `TavilySearchOutcome` (Grounded → existing fused persist path; MissingKey/InvalidKey/UsageLimit → distinct copy).
- Ready for Phase 56: `search(query, maxResults, contextSize)` is the `web_search` tool implementation signature (query-rewriting left to the agentic loop per Pitfall 5).
- Concern: none blocking. Live search E2E on device still needs a real key (documented in RESEARCH environment table).

## Self-Check: PASSED

- All 6 files exist on disk (4 created + 2 modified)
- All 3 task commits exist (`1137574f`, `cddf97d0`, `c172f5fb`)
- No unintended file deletions in any task commit

---
*Phase: 55-tavily-search-foundation*
*Completed: 2026-09-28*
