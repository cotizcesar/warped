---
phase: 56-local-agentic-loop
plan: 1
subsystem: agentic-loop
tags: [litertlm, function-calling, tool-loop, tavily, kv-cache, jvm-tests]

# Dependency graph
requires:
  - phase: 55-tavily-search
    provides: TavilySearchOutcome union + fused Source[N] block pipeline consumed as web_search result strings
  - phase: 52-grounding
    provides: MultiUrlFetcher.fetchAll + OFFLINE collapse consumed as web_fetch result strings
provides:
  - Fixed two-tool @Tool surface (web_search/web_fetch) with schema-only stub bodies
  - LocalToolLoop pure policy: 5-call cap, exact-name dispatch, outcome mapping, arg validation
  - gemma-4 pair supportsFunctionCalling=true capability flags (docs basis, device confirmation pending)
  - KV-cache thinking-channel filter set at engine init
affects: [56-local-agentic-loop plan 02 (provider loop wiring), 57-remote-agentic-loop (provider-neutral schema reuse)]

# Tech tracking
tech-stack:
  added: []
  patterns: ["JVM-testable pure policy with zero engine imports (47 ToolGating precedent)", "schema-only @Tool stubs returning host marker (never runBlocking, never throw)", "Phase-55 copy twins for model-facing tool-result strings"]

key-files:
  created: [app/src/main/java/com/warped/data/agentic/WebSearchToolSet.kt, app/src/main/java/com/warped/data/agentic/WebFetchToolSet.kt, app/src/main/java/com/warped/data/agentic/LocalToolLoop.kt, app/src/test/java/com/warped/data/agentic/LocalToolLoopTest.kt, app/src/test/java/com/warped/data/agentic/ToolSetSchemaTest.kt]
  modified: [app/src/main/assets/model_allowlist.json, app/src/main/java/com/warped/data/repository/ModelAllowlistRepository.kt, app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt, app/src/test/java/com/warped/data/repository/ModelAllowlistTest.kt, app/proguard-rules.pro]

key-decisions:
  - "Stub bodies return HOST_EXECUTED constant (never throw per 47 never-throw lesson)"
  - "KV-cache filter flag set unconditionally at init (backend-independent), not inside the GPU branch"
  - "Schema test pins method name + single-String-param shape; param names are compile-time source (no -java-parameters, kotlin-reflect banned)"

patterns-established:
  - "data/agentic/ package owns the fixed two-tool surface; plan 02 adds the provider loop, never new tools"
  - "Tool-result strings are fused blocks verbatim or Phase-55 copy twins — never raw JSON"

requirements-completed: [AGENT-02, AGENT-04]

# Metrics
duration: 25min
completed: 2026-09-29
---

# Phase 56 Plan 01: Tracer Backbone Summary

**Fixed web_search/web_fetch @Tool schemas, JVM-tested 5-call loop policy with sanitized outcome mapping, gemma-4 function-calling flags, and KV-cache channel hygiene — zero new dependencies**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-09-29T01:25Z
- **Completed:** 2026-09-29T01:50Z
- **Tasks:** 2
- **Files modified:** 10 (5 created, 5 modified)

## Accomplishments

- `data/agentic/` package with exactly two ToolSets: `web_search(query)` and `web_fetch(url)`, single-String params, schema-only bodies returning `HOST_EXECUTED` — no `runBlocking` in code (grep gate clean)
- `LocalToolLoop` pure policy (no litertlm imports): `MAX_TOOL_CALLS=5` counting calls, exact-name dispatch, every `TavilySearchOutcome` mapped (Grounded block verbatim, Phase-55 copy for key/limit/offline), `fetchAll` mapping with OFFLINE collapse, blank-query short-circuit + non-http(s) rejection, cap-reached continuation string, never-throw failure mapping
- Allowlist: `supportsFunctionCalling=true` for gemma-4-E2B-it + gemma-4-E4B-it ONLY (docs evidence cited in `meta.note`); 3n pair stays false — grep gate confirms exactly 2 true flags
- `ExperimentalFlags.filterChannelContentFromKvCache=true` set once at engine init (layer 1 channel hygiene; layer 2 routing is plan 02)
- Full unit suite: 401 tests, 0 failures (agentic 20 + allowlist 12 + inference suites all green)

## Task Commits

Each task was committed atomically:

1. **Task 1: ToolSets + LocalToolLoop pure policy with tests** - `c6832c6b` (feat)
2. **Task 2: Allowlist flag flips + KV-cache channel hygiene** - `d837d13a` (feat)

## Files Created/Modified

- `app/src/main/java/com/warped/data/agentic/WebSearchToolSet.kt` - web_search @Tool schema (stub body)
- `app/src/main/java/com/warped/data/agentic/WebFetchToolSet.kt` - web_fetch @Tool schema (stub body)
- `app/src/main/java/com/warped/data/agentic/LocalToolLoop.kt` - pure loop policy: cap, dispatch, outcome mapping, arg validation (+ HOST_EXECUTED marker)
- `app/src/test/java/com/warped/data/agentic/LocalToolLoopTest.kt` - 16 policy cases
- `app/src/test/java/com/warped/data/agentic/ToolSetSchemaTest.kt` - 4 reflection no-drift pins
- `app/src/main/assets/model_allowlist.json` - gemma-4 pair flags true + evidence note
- `app/src/main/java/com/warped/data/repository/ModelAllowlistRepository.kt` - 56-01 flag-decision docs
- `app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt` - KV-cache filter flag at init
- `app/src/test/java/com/warped/data/repository/ModelAllowlistTest.kt` - per-model flag pins, tools-derivation true for gemma-4
- `app/proguard-rules.pro` - data.agentic keep (47 precedent)

## Decisions Made

- Stub bodies return the `HOST_EXECUTED` constant instead of throwing `ToolException`: the 47 never-throw lesson wins (a throw across JNI kills the conversation); manual mode never invokes bodies anyway.
- KV-cache filter flag set unconditionally after `ensureNativeLoaded()`, not inside the GPU-only branch next to `enableSpeculativeDecoding`: KV hygiene is backend-independent and CPU is the default backend — GPU-only placement would leave the flag unset on most devices.
- Schema test asserts method name + single-`String`-param shape rather than param names: the build has no `-java-parameters` flag and kotlin-reflect is a banned Gallery anti-pattern, so JVM reflection cannot see param names; renames still break loudly (engine dispatch + Phase-57 mapping).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Nullable getAnnotation warnings in schema test**
- **Found during:** Task 1 (ToolSets + LocalToolLoop pure policy with tests)
- **Issue:** `Method.getAnnotation()` returns nullable `Tool?` in Kotlin — two `Only safe (?.) calls allowed` compiler warnings on first build
- **Fix:** `assertThat(tool?.description).isNotEmpty()` — still fails closed on missing annotation and on empty description
- **Files modified:** `app/src/test/java/com/warped/data/agentic/ToolSetSchemaTest.kt`
- **Verification:** Rebuilt warning-free; ToolSetSchemaTest 4/4 green
- **Committed in:** `c6832c6b` (part of task commit)

**2. [Rule 2 - Correctness] KV-cache flag placed unconditionally, not GPU-branch-local**
- **Found during:** Task 2 (Allowlist flag flips + KV-cache channel hygiene)
- **Issue:** Plan said "next to the existing enableSpeculativeDecoding line (~105)" — that line sits inside the `BackendType.GPU` branch, so literal placement would skip the flag on CPU (the default backend)
- **Fix:** Set `filterChannelContentFromKvCache = true` once right after `ensureNativeLoaded()`, adjacent to the `when` block, with a comment recording why it is unconditional while speculative decoding stays GPU-only
- **Files modified:** `app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt`
- **Verification:** Inference suites green (BackendConstraint 10/10, LiteRtLmCache 3/3); flag semantics are backend-independent per SDK name/bytecode
- **Committed in:** `d837d13a` (part of task commit)

---

**Total deviations:** 2 auto-fixed (1 bug, 1 correctness)
**Impact on plan:** Both required for warning-free build and correct behavior on the default CPU path. No scope creep — no new files, no new surface.

## Issues Encountered

None — SDK surface (`@Tool`/`@ToolParam` single-description, `ToolSet` marker, nullable-Boolean KV flag) verified against 0.17.1 AAR bytecode before writing code; first builds passed.

## Threat Flags

None — no new security-relevant surface beyond the plan's threat model. The two ToolSets are schema-only stubs (no network, no I/O); `LocalToolLoop` is pure mapping over already-sanitized fused blocks; no new endpoints, auth paths, or schema changes at trust boundaries. T-56-01..T-56-06 mitigations all land as specified; T-56-SC holds (zero new deps).

## Known Stubs

- `HOST_EXECUTED` return in both `@Tool` bodies is an intentional schema-only marker, not a data stub: the manual loop (plan 02) executes tools as suspend repository calls with `automaticToolCalling=false`, so the engine never invokes these bodies. Pinned by `stub bodies are schema-only and return host marker` test. Resolved by plan 02 wiring, which consumes (never forwards) this string.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 02 consumes: `LocalToolLoop` policy + both ToolSet schemas + gemma-4 capability flags + Phase-55 copy twins. Provider loop entry point is `LiteRTLmProvider` with `TavilySearchRepository` / `MultiUrlFetcher` as executors.
- Device confirmation pending: plan-02 on-device smoke must observe real `ToolCall` emission on both Gemma 4 models — if either never emits, its flag reverts per the verified-only rule.
- Full suite baseline for plan 02: 401 tests green.

---
*Phase: 56-local-agentic-loop*
*Completed: 2026-09-29*

## Self-Check: PASSED

- All 5 created files exist on disk; all 5 modified files contain the expected changes (2 true flags, KV flag line, proguard keep).
- Commits `c6832c6b` and `d837d13a` verified in `git log`.
- Test evidence: `TEST-com.warped.data.agentic.LocalToolLoopTest.xml` (16/16), `TEST-com.warped.data.agentic.ToolSetSchemaTest.xml` (4/4), `TEST-com.warped.data.repository.ModelAllowlistTest.xml` (12/12), full suite 401/401 zero failures.
