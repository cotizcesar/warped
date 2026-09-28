---
phase: 47-real-tool-execution
plan: "02"
subsystem: skills-local-tools
tags: [skills, litertlm, tool-calling, trust-boundary, gating, compose-status]
dependency_graph:
  requires:
    - phase: 47-01
      provides: [sealed-Skill, SkillRepository-Hilt, ToolExecutor-interface, ToolEventSink-interface, shared-descriptor-mapper, Role-TOOL, SkillChipsRow, tool-copy-deck]
  provides: [pure-tool-bodies, LocalToolExecutor, ToolSet-wiring, ConversationConfig-gating, StreamToken-ToolStatus, ToolGating-decisions, reflection-assert-tests]
  affects: [47-03-remote-loop]
tech_stack:
  added: []
  patterns: [pure-sync-error-mapped-tools, allowlisted-dispatch, ToolGating-single-truth, ToolEventSink-to-ToolStatus-forwarding, fresh-ToolSet-per-conversation, wedge-degrade-once]
key_files:
  created:
    - app/src/main/java/com/warped/data/skills/ExpressionEvaluator.kt
    - app/src/main/java/com/warped/data/skills/ToolText.kt
    - app/src/main/java/com/warped/data/skills/CalculatorSkill.kt
    - app/src/main/java/com/warped/data/skills/CurrentTimeSkill.kt
    - app/src/main/java/com/warped/data/skills/JsonFormatterSkill.kt
    - app/src/main/java/com/warped/data/skills/LocalToolExecutor.kt
    - app/src/main/java/com/warped/data/skills/ToolGating.kt
    - app/src/test/java/com/warped/data/skills/CalculatorSkillTest.kt
    - app/src/test/java/com/warped/data/skills/CurrentTimeSkillTest.kt
    - app/src/test/java/com/warped/data/skills/JsonFormatterSkillTest.kt
    - app/src/test/java/com/warped/data/skills/LocalToolExecutorTest.kt
    - app/src/test/java/com/warped/data/skills/ToolGatingTest.kt
  modified:
    - app/src/main/java/com/warped/domain/model/StreamToken.kt
    - app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt
    - app/src/main/java/com/warped/data/local/inference/LiteRtLlmHelper.kt
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/di/SkillsModule.kt
    - app/src/main/java/com/warped/data/local/benchmark/ModelBenchmarkWorker.kt
    - app/src/main/java/com/warped/data/remote/provider/LmStudioHelper.kt
    - app/src/main/java/com/warped/ui/promptlab/PromptLabViewModel.kt
    - app/src/test/java/com/warped/data/skills/SkillDescriptorsTest.kt
    - app/src/test/java/com/warped/ui/chat/ChatCancellationTest.kt
    - app/src/test/java/com/warped/data/remote/provider/LmStudioCancelTest.kt
key-decisions:
  - "ToolGating single-truth: provider and ViewModel gate via one pure decide() — notice and tools wiring cannot disagree"
  - "channelFlow merge: inner flow{} keeps FlowCollector helpers untouched; outer channelFlow merges body + ToolStatus forwarding"
  - "No-support notice is ViewModel-driven from shared gating (no new token type); cleared per-turn alongside toolCallActive"
  - "NoopToolExecutor deleted with the binding swap (not kept as dead code)"
  - "Local transcript persistence deferred: automatic-mode results are engine-internal; sink carries start/finish only"
patterns-established:
  - "Pure sync tool bodies with catch-all → ToolResult.Failure (never throw across JNI)"
  - "Fresh ToolSet instances per conversation creation; toggle → resetConversation()"
  - "Wedge-degrade: one no-tools retry (attempt=maxRetries), then toolsDegraded session fallback"
requirements-completed: [SKILLS-08, SKILLS-12, LRT-08, HARD-02]
duration: ~90 min
completed: 2026-09-28
---

# Phase 47 Plan 02: Real Local Tool Execution Summary

**Calculator/CurrentTime/JsonFormatter as LiteRT-LM 0.17.1 `@Tool` ToolSets wired from enabled chips via `ConversationConfig(tools, automaticToolCalling)`, with allowlisted dispatch, per-model gating + injection fallback, sink-driven `ToolStatus` tokens, and 45 green skills tests — zero new dependencies.**

## Performance

- **Duration:** ~90 min
- **Tasks:** 3 (all `type="auto"`, fully autonomous — no checkpoints hit)
- **Files modified:** 23 (12 created, 11 modified, 1 deleted)

## Accomplishments

- 3 pure sync tool bodies (hand-rolled expression evaluator, `java.time` clock, kotlinx pretty-printer) — every invalid class error-mapped, nothing throws out (HARD-02)
- `LocalToolExecutor` behind the 47-01 interface: allowlisted dispatch, ParamSpec schema validation, truncation + control-char strip, `Dispatchers.Default`, name-only logging
- Local `@Tool` path end-to-end: fresh ToolSets per conversation, `automaticToolCalling = enabled.isNotEmpty() && supported`, per-model allowlist gating default CLOSED, prompt-injection fallback + once-per-turn notice, reset-on-toggle, Qwen-wedge single no-tools retry + session fallback (SKILLS-08, LRT-08)
- `StreamToken.ToolStatus(toolName: String?)` observability token (47-03 dependency) forwarded from `@Tool` sink posts via channelFlow merge; ViewModel drives `toolCallActive` + error/notice rows (SKILLS-12 local)
- Reflection-assert (ToolSet annotations ≡ descriptor constants) + ToolGating config-builder tests; full suite 237/237 green; R8 keeps already cover new ToolSets

## Task Commits

Each task was committed atomically:

1. **Task 1: Pure tool bodies + trust boundary + LocalToolExecutor + per-tool tests** - `c61c939` (feat)
2. **Task 2: @Tool ToolSets + ConversationConfig wiring + gating + ToolStatus** - `8f9b964` (feat)
3. **Task 3: LRT-08 verification + reflection-assert test + device smoke checklist** - `ae0fed1` (test)

## Files Created/Modified

- `data/skills/ExpressionEvaluator.kt` - Recursive-descent arithmetic evaluator (in-tree, zero deps), depth-bounded, finite-checked
- `data/skills/ToolText.kt` - Shared output hygiene: control-char strip + truncate (2000 text / 200 summary caps)
- `data/skills/CalculatorSkill.kt` - Pure `calculateExpression` (200-char cap, charset allowlist, div-zero/overflow mapped) + `CalculatorToolSet`
- `data/skills/CurrentTimeSkill.kt` - Pure `getCurrentTime` (ZoneId allowlist, fixed US pattern) + `CurrentTimeToolSet` (nullable optional param)
- `data/skills/JsonFormatterSkill.kt` - Pure `formatJson` (64KB cap, malformed mapped, pretty capped) + `JsonFormatterToolSet`
- `data/skills/LocalToolExecutor.kt` - `ToolExecutor` impl: allowlist dispatch, schema validation, sanitized results; Hilt binding swapped to it
- `data/skills/ToolGating.kt` - Pure `decide`/`supportsLocalTools`/`fallbackSystemPrompt` shared by provider + ViewModel
- `domain/model/StreamToken.kt` - Added `ToolStatus(toolName: String?)` (null = clear; 47-03 reuse)
- `data/local/inference/LiteRTLmProvider.kt` - channelFlow status merge, Step 6 tools wiring, `Message.tool` resume, wedge-degrade, `resetConversation` on toggle path
- `data/local/inference/LiteRtLlmHelper.kt` - ToolStatus passthrough (both thinking branches)
- `ui/chat/ChatViewModel.kt` - ToolStatus→`toolCallActive`, once-per-turn no-support notice, reset-on-toggle, per-turn clearing
- `di/SkillsModule.kt` - Binding `ToolExecutor`→`LocalToolExecutor`
- `data/skills/NoopToolExecutor.kt` - **Deleted** (47-01 placeholder replaced, not kept)
- Ripple branches for the new token: `ModelBenchmarkWorker` (ignore), `LmStudioHelper` (passthrough), `PromptLabViewModel` (ignore)
- Tests: 4 per-tool/executor suites (30 tests) + reflection asserts (+4) + `ToolGatingTest` (6); harness fixes in `ChatCancellationTest`/`LmStudioCancelTest`

## Decisions Made

- **ToolGating single-truth:** provider (tools wiring) and ViewModel (notice) both call `ToolGating.decide` — the two sides cannot disagree about gating. The notice is ViewModel-driven from this shared truth rather than a second token type, keeping `ToolStatus` minimal for 47-03.
- **channelFlow merge shape:** the turn body stays an inner `flow{}` (so `FlowCollector`-receiver helpers `chatInternal`/`sendContentsWithRetry` are untouched) while the outer `channelFlow` merges body tokens with the `@Tool` status forwarder — `ProducerScope` is not a `FlowCollector`, so calling the helpers directly in `channelFlow` does not compile.
- **NoopToolExecutor deleted** with the binding swap — dead placeholder code is not kept around.
- **Local transcript persistence deferred:** in automatic mode tool *results* are engine-internal; the sink carries start/finish only, so there is nothing to persist yet. Status + error + fallback UX work; result-row capture for the local path (if wanted) needs a result-carrying sink and belongs to a later plan — flagged for 47-03/verify.
- **No `supportsFunctionCalling` flag flipped** (verified-only rule, A5) — gating stays CLOSED until on-device evidence.
- **`@Tool` method names are engine-canonical** (`calculator`, `currentTime`, `jsonFormatter`); UI/allowlist identity always uses `SkillIds` constants posted to the sink, never engine names.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `ProducerScope` is not a `FlowCollector` — channelFlow restructure**
- **Found during:** Task 2 (provider wiring compile)
- **Issue:** Plan implied calling the `FlowCollector`-receiver helpers from a `channelFlow` block; `emit` does not resolve on `ProducerScope`, so the helpers could not be called there.
- **Fix:** Inner `flow{}` runs the untouched turn body; outer `channelFlow` merges it with the status forwarder (`body.collect { send(it) }` + `launch { toolStatus.collect { send(...) } }`, cancelled in `finally`).
- **Files modified:** `LiteRTLmProvider.kt`
- **Verification:** `:app:compileDebugKotlin` clean; cancel/notice semantics preserved (turn-scoped job)
- **Committed in:** 8f9b964 (Task 2 commit)

**2. [Rule 3 - Blocking] Sealed-interface ripple across 4 collectors**
- **Found during:** Task 2 compile (compiler-enforced exhaustiveness)
- **Issue:** Adding `StreamToken.ToolStatus` broke exhaustive `when` in `ModelBenchmarkWorker`, `LmStudioHelper`, `PromptLabViewModel`, and a `ChatCancellationTest` harness.
- **Fix:** Benchmark/PromptLab/test-harness ignore (`-> Unit`); both helpers pass through (`-> token`).
- **Files modified:** the 3 prod files + test harness
- **Verification:** full `:app:testDebugUnitTest` green (237/237)
- **Committed in:** 8f9b964 (Task 2 commit)

**3. [Rule 3 - Blocking] Relaxed mocks return `Object` for new provider/VM deps**
- **Found during:** Task 2 test compile/run (`LmStudioCancelTest` CCE on `activeModel.value`)
- **Issue:** New ctor params (`SkillRepository`, `ModelAllowlistRepository`, `LiteRTLmProvider`) needed explicit stubs; relaxed mocks returned uncastable defaults.
- **Fix:** `relaxedSkills()` helper (empty `enabledSkills` flow), `activeModel → MutableStateFlow(null)` (gates CLOSED, preserving the engine-error path), `findByModelFile → null`, `resetConversation → just Runs`.
- **Files modified:** `LmStudioCancelTest.kt`, `ChatCancellationTest.kt`
- **Verification:** targeted + full suites green
- **Committed in:** 8f9b964 (Task 2 commit)

**4. [Rule 1 - Bug] Overflow test unreachable through the 200-char cap**
- **Found during:** Task 1 verification (1 of 30 tests failed)
- **Issue:** `1e19 × 1e19 = 1e38` is finite — pure-multiply overflow is mathematically unreachable within 200 chars, so the test's expectation was wrong, not the code.
- **Fix:** Evaluator-level `assertThrows<ArithmeticException>` on a 200-digit product (guard coverage) + tool-body no-throw assertion at the cap edge.
- **Files modified:** `CalculatorSkillTest.kt`
- **Verification:** 30/30 task tests green
- **Committed in:** c61c939 (Task 1 commit)

### Plan-File Additions (inline necessities, not scope change)

- `data/skills/ToolText.kt` (new): shared sanitize/truncate used by executor *and* `@Tool` bodies (automatic mode bypasses the executor, so both sites must sanitize per T-47-07).
- `ModelBenchmarkWorker.kt` / `LmStudioHelper.kt` / `PromptLabViewModel.kt`: ToolStatus branches — compiler-mandated by the new token type, semantics are ignore/passthrough.
- `ChatCancellationTest.kt` / `LmStudioCancelTest.kt`: ctor-arg updates for the two widened constructors — same class of fix as 47-01's `32f760c`.

---

**Total deviations:** 4 auto-fixed (3 blocking, 1 bug) + 3 inline-necessity file additions
**Impact on plan:** All required for compile-correctness or test-truth; no scope creep, no new deps, no flag flips.

## Issues Encountered

- `assertDoesNotThrow` two-arg overload resolution in `JsonFormatterSkillTest` (message supplier picked the `Executable` overload) — dropped the supplier, single-arg form.
- Missing `Json` import in `LocalToolExecutor` (first compile) — one-line fix.
- An accidental no-op whitespace edit joined two lines in the provider (`decodeImage`); repaired immediately and verified by compile + full tests.

## Known Stubs

None introduced — but one intentional deferral (see Decisions): local-path tool *result* transcript rows are not persisted (automatic-mode results never surface to the app). Status, error, fallback, and notice UX are fully wired. Remote-path persistence belongs to 47-03.

## Threat Flags

None — no new surface beyond the plan's threat model. All T-47-05…T-47-09 mitigations landed as specified (caps/allowlist/bounds, allowlisted dispatch + schema validation, truncation + control-char strip, sanitized reasons + name-only logs + `Dispatchers.Default`, gating-CLOSED + single no-tools retry + session fallback); T-47-SC accept holds (zero installs; evaluator hand-rolled).

## Verification

- `:app:compileDebugKotlin` clean (only pre-existing warnings).
- `:app:testDebugUnitTest` full suite: **237 tests, 0 failures** — incl. 45 `data.skills` tests (30 per-tool/executor + 9 descriptors/reflection + 6 gating).
- `grep -c "automaticToolCalling\|ToolStatus\|resetConversation"` hits all four wired files; no cached/reused ToolSet instances (fresh per conversation creation).
- R8: existing `com.warped.data.skills.**` / `domain.skills.**` + `ToolSet`/`@Tool`/`@ToolParam`/`ReflectionTool` keeps cover the new ToolSets — no proguard change needed (debug compile + keep-rule grep per plan's cheap path).
- Zero dependency changes (no gradle/catalog/build-file edits — RUNTIME-12 untouched; kotlin-reflect arrives transitively via litertlm, explicitly allowed by `audit-dependencies.sh`).

## On-Device Smoke Checklist (manual — no device in this env)

Per Task 3, run on a device with a `.litertlm`/`.task` model before flipping any `supportsFunctionCalling` flag (verified-only rule):

1. **Airplane-mode calculator (compute-not-guess):** enable all chips, airplane mode on, send `(2+3)*4` → assistant replies with `20` (exact compute, not a guess). Expect `Using calculator…` status row during the turn.
2. **Current-time tz:** `What time is it in America/New_York?` → reply contains the zone id and a fixed-pattern timestamp. Invalid tz (via prompt-injection fallback) → `unknown timezone` error row + non-blank fallback.
3. **JSON format valid/invalid:** paste minified `{"b":2,"a":1}` → pretty reply; paste `{bad` → `JSON formatter failed: invalid JSON` + fallback answer, never a hang or empty bubble.
4. **Qwen3/Gemma re-test vs 0.17.1:** with chips on, run the calculator prompt; record per-model gating verdict. Expected until verified: fallback path (`This model doesn't support tools — answering directly.` + injection one-liners). If a wedge occurs (zero callbacks after a tool turn), confirm single no-tools retry + session fallback, then record the model as fallback-only.
5. **Toggle behavior:** disable Calculator mid-chat → next turn rebuilds without the tool (no stale firing); zero enabled → plain chat, no notice.
6. **Wedge-degrade observation:** if `tool_response`/`template` `LiteRtLmJniException` fires, verify the turn completes (retry without tools) and later turns stay on fallback for the session.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Ready for **47-03 (remote loop)**: `StreamToken.ToolStatus` is live and ViewModel-mapped; `LocalToolExecutor` serves as the shared execution backend for remote `tool_calls`; `ToolGating`/`SkillDescriptors` are backend-agnostic; `@ToolParam`≡descriptor reflection guard protects both projections.
- No blockers. Device checklist above is the only human-side item, and it gates allowlist flag flips — not 47-03 code.

## Self-Check: PASSED

- All 12 created + 10 modified files verified present on disk; `NoopToolExecutor.kt` verified deleted.
- All 3 task commits (`c61c939`, `8f9b964`, `ae0fed1`) verified in `git log`.
- Full unit suite re-verified after final commit: 237/237 green.

---
*Phase: 47-real-tool-execution*
*Completed: 2026-09-28*
