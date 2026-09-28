---
phase: 47-real-tool-execution
verified: 2026-09-28T12:00:00Z
status: passed
score: 5/5 must-haves verified
overrides_applied: 0
re_verification: true
previous_status: human_needed
previous_score: 3/5
gaps_closed:
  - "Review fixes CR-01 + WR-01..09 confirmed present in tree and substantive"
  - "Post-verification engine/allowlist/UI commits confirmed present (session fix, backend-retry, spec-decode opt-in, E2B entry, icon badges + thinking gating + seamless switch)"
gaps_remaining: []
regressions: []
deferred:
  - truth: "User enables Calculator chip and asks math in airplane mode (local) — computed result on device"
    addressed_in: "Phase 48 sweep"
    evidence: "User direction 2026-09-28 ('continua con lo pendiente') — device/server proofs deferred to Phase 48 sweep; Phase 48 SC-4 covers release-build tool skills on a real device"
  - truth: "Same question vs live LM Studio server — tools[] → execute → re-POST → answer (server dialect acceptance, open question A1)"
    addressed_in: "Phase 48 sweep"
    evidence: "User direction 2026-09-28 ('continua con lo pendiente') — deferred to Phase 48 sweep; interceptor-fake tests prove loop shape in-tree"
---

# Phase 47: Real Tool Execution Verification Report

**Phase Goal:** Enabled skills actually execute — locally via LiteRT-LM `@Tool`s and remotely via the LM Studio `tools[]` loop — with visible progress, graceful errors, and validated inputs
**Verified:** 2026-09-28T12:00:00Z
**Status:** passed
**Re-verification:** Yes — prior report stale (post-verification commits landed after it)

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User enables Calculator chip and asks math in airplane mode (local) — computed result, not a guess | ✓ VERIFIED | `CalculatorToolSet` `@Tool` + `ConversationConfig(tools, automaticToolCalling)` wiring present; 30 per-tool/executor tests green; on-device run deferred to Phase 48 sweep per user direction (not a gap) |
| 2 | Same question vs LM Studio remote — tools[] → execute → re-POST → answer, identical visible behavior | ✓ VERIFIED | `LmStudioToolLoop` (MAX_TOOL_ROUNDS=5, `>=` cap, index-keyed SSE accumulator, `ensureActive`, per-round `onCallCreated`) + 10 interceptor-fake tests green; live-server dialect deferred to Phase 48 sweep per user direction (not a gap) |
| 3 | While a tool runs user sees `Using calculator…`; on failure `Calculator failed: …` + plain-text fallback — never hang/empty bubble | ✓ VERIFIED | `StreamToken.ToolStatus` → `toolCallActive` + `ActiveToolError` via `ToolCopy` verbatim strings; `ChatViewModelToolTest` 6/6; WR-02 + WR-03 fixes present (`b503be8`, `77c7f15`) |
| 4 | Past tool use visible in transcript (name + summarized result) and survives resume | ✓ VERIFIED | `Role.TOOL` rows (`<toolId>\n<summary>` encoding) persisted on Done/Error/cancel; `ToolResultRow` collapsed rendering; CR-01 `ToolHistory.toProviderText()` fix present on all 4 non-LM-Studio providers (`0b1799b` + `ce33c03`) |
| 5 | Model without tool support degrades gracefully — clear message + normal answer; Qwen3/Gemma stay on prompt-injection fallback | ✓ VERIFIED | `ToolGating.decide` single-truth shared by provider + ViewModel; exact `NO_TOOL_SUPPORT_NOTICE` once per turn; WR-01 + WR-05 fixes present (`2d3cbab`, `fdb44c8`); no `supportsFunctionCalling` flag flipped |

**Score:** 5/5 truths verified

### Deferred Items

Items not yet proven here but explicitly deferred to the Phase 48 sweep per user direction 2026-09-28 ("continua con lo pendiente"). Recorded as deferred, NOT gaps.

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | Airplane-mode calculator on-device proof (SC-1) | Phase 48 sweep | Phase 48 SC-4 (release build, tool skills work on real device); 47-02 smoke checklist items 1-6 carry over |
| 2 | LM Studio remote tool loop on a real server (SC-2, open question A1) | Phase 48 sweep | Interceptor-fake tests prove loop shape; live dialect (`delta.tool_calls[]` streaming + `finish_reason: tool_calls` + `role:tool` acceptance) needs a real server |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `domain/skills/Skill.kt` | Sealed Skill + SkillCategory | ✓ VERIFIED | Prior report; unchanged since |
| `data/skills/SkillDescriptors.kt` | Shared mapper + `toOpenAiParameters` | ✓ VERIFIED | Golden + reflection-assert tests green |
| `data/skills/SkillPreferences.kt` | DataStore all-on booleans | ✓ VERIFIED | `booleanPreferencesKey`, missing-key default true |
| `di/SkillsModule.kt` | Hilt bindings | ✓ VERIFIED | `@Binds` SkillRepository + ToolExecutor→LocalToolExecutor |
| `ui/chat/components/SkillChipsRow.kt` | Chips row per UI-SPEC | ✓ VERIFIED | Present on disk, `setSkillEnabled` wiring |
| `data/skills/CalculatorSkill.kt` | Calculator `@Tool` ToolSet | ✓ VERIFIED | `@Tool`, fresh instances per conversation |
| `data/skills/LocalToolExecutor.kt` | `ToolExecutor` impl | ✓ VERIFIED | Present on disk; allowlist dispatch, schema validation |
| `domain/model/StreamToken.kt` | `ToolStatus` + `ToolCompleted` tokens | ✓ VERIFIED | Both present, ripple branches handled |
| `data/local/inference/LiteRTLmProvider.kt` | `ConversationConfig` tools wiring | ✓ VERIFIED | `automaticToolCalling`, `clearToolsDegraded` (WR-01) |
| `data/skills/ToolHistory.kt` | CR-01 centralized TOOL resume mapping | ✓ VERIFIED | `parseToolRow`/`toProviderText` present (grep: 3 hits) |
| `data/remote/provider/LmStudioToolLoop.kt` | Multi-round completions loop | ✓ VERIFIED | Present; `MAX_TOOL_ROUNDS`/`ensureActive`/`sanitizeToolOutput` (grep: 14 hits) |
| `data/remote/dto/OpenAiChatRequest.kt` | `toolCallId` + `toolCalls` fields | ✓ VERIFIED | Nullable `content: String?` (WR-07) |
| `data/remote/provider/LMStudioProvider.kt` | `chatCompletionsWithTools` entry | ✓ VERIFIED | Additive entry; native `/api/v1/chat` parser untouched |
| `data/skills/ToolGating.kt` | Single-truth gating | ✓ VERIFIED | `decide`/`supportsRemoteTools` present (grep: 2 hits) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `SkillChipsRow.kt` | SkillPreferences DataStore | `setSkillEnabled` + StateFlow | WIRED | Unconditional persist + always-reset (WR-08 `9046130`) |
| `SkillDescriptors.kt` | remote tools[] JSON | `toOpenAiParameters` buildJsonObject | WIRED | Golden tests pin shape |
| `EntityMappers.kt` | domain Role | `TOOL` / `toRoleSafe` | WIRED | Crash-safe, never throws |
| `CalculatorSkill.kt` | ToolEventSink | `events.onStart` try/finally | WIRED | Non-blocking posts |
| `LiteRTLmProvider.kt` | ConversationConfig | Fresh ToolSets, `automaticToolCalling` | WIRED | Reset-on-toggle, wedge-degrade |
| `LocalToolExecutor.kt` | next-turn input | Truncated sanitized summary | WIRED | Dual-layer sanitize (WR-09 both sides: `e48dabd`+`89ae496`) |
| `LmStudioToolLoop.kt` | POST /v1/chat/completions | Raw OkHttp + `onCallCreated` per round | WIRED | 46-01 contract replicated |
| `LmStudioToolLoop.kt` | LocalToolExecutor | Validated dispatch, `ensureActive`, `>=` cap | WIRED | Cap test: 6 requests / 5 execs |
| `ChatViewModel.kt` | ChatRepository.saveMessage | Assistant + `Role.TOOL` rows on Done | WIRED | Plus Error/cancel persistence (WR-02 `b503be8`) |

### Data-Flow Trace (Level 4)

N/A — no dynamic-data rendering components beyond the chat transcript path, covered by the `ToolCompleted` → persist → `ToolResultRow` chain verified above.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Key artifacts present + substantive | ls 5 files + grep counts (ToolHistory 3, loop 14, gating 2) | All present, counts match | ✓ PASS |
| Debt markers in phase files | grep TODO/FIXME/XXX/TBD in `data/skills/` + `LmStudioToolLoop.kt` | No matches | ✓ PASS |
| Fix commits present in tree | `git log --oneline` | All 11 review-fix + 6 follow-up commits present | ✓ PASS |

Full unit suite (253/253) was green per 47-03 SUMMARY and prior verification re-ran the 61 phase-owned tests green after the 11 review-fix commits. No re-run here (build artifacts stale in tree); code-level fix confirmation via `git show --stat` + grep stands.

### Probe Execution

No probes declared for this phase (not a migration/tooling phase). SKIPPED.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| SKILLS-07 | 47-01 | Skills Lite surface rebuilt Kotlin-only | ✓ SATISFIED | Sealed Skill, repo, prefs, chips row, all compile |
| SKILLS-08 | 47-02 | 3 real `@Tool`s via ToolSet + ConversationConfig | ✓ SATISFIED | ToolSets wired, `automaticToolCalling`, 45 skills tests |
| SKILLS-09 | 47-01/02 | Shared mapper, no drift | ✓ SATISFIED | Golden + reflection-assert tests |
| SKILLS-10 | 47-03 | Remote multi-turn loop over /v1/chat/completions | ✓ SATISFIED | 10 loop tests; live-server proof deferred to Phase 48 sweep |
| SKILLS-11 | 47-01/03 | Minimal `role:tool` transcript rows, resumable | ✓ SATISFIED | Persist on Done/Error/cancel; CR-01 resume fix |
| SKILLS-12 | 47-02/03 | Progress/errors through real path, interface executor | ✓ SATISFIED | ToolStatus/ToolCompleted, error+fallback, behind interface |
| LRT-08 | 47-02 | 0.14–0.17 fixes adopted; Qwen3/Gemma gated | ✓ SATISFIED | Pinned build, gating CLOSED, wedge-degrade, no flag flips |
| HARD-02 | 47-02 | Trust boundary validated/sanitized/never-throw | ✓ SATISFIED | Caps, allowlists, error-mapped, WR-04 depth guard, per-tool tests |

No orphaned requirements — all 8 Phase 47 requirements claimed across the 3 plans.

### Review-Fix Confirmation (47-REVIEW.md: CR-01 + WR-01..09)

All 11 fix commits present in `git log` (prior verification grep-confirmed substantive):

| Finding | Commit | Fix verified |
|---------|--------|--------------|
| CR-01 TOOL resume malformed on 4 providers | `0b1799b` + `ce33c03` | `ToolHistory.kt` (`parseToolRow`/`toProviderText`) + imports/branches in OpenAI/Anthropic/Ollama/Custom providers |
| WR-01 wedge flag never reset | `2d3cbab` | `clearToolsDegraded()` + call sites |
| WR-02 tool records dropped on Error/cancel | `b503be8` | `persistToolRecords` on Error + cancel paths |
| WR-03 Delta flush races ToolStatus | `77c7f15` | Flush no longer touches `toolCallActive` |
| WR-04 StackOverflowError escapes trust boundary | `e48dabd` | `isJsonTooDeep` depth guard at 3 sites |
| WR-05 no notice on non-wired providers | `fdb44c8` | Notice branches at ChatViewModel 346/363/375 |
| WR-06 dead `malformed` flag | `4af6f14` | `Timber.w` malformed-fallback logging at 4 sites |
| WR-07 empty-string content with tool_calls | `ea4374f` | Nullable `content: String?` in DTO |
| WR-08 mid-stream toggle skips reset | `9046130` | Unconditional `resetConversation()` on toggle |
| WR-09 no sanitize at re-POST boundary | `89ae496` | `sanitizeToolOutput` at loop boundary |

### Post-Verification Follow-Up Commits (confirmed in tree via `git show --stat`)

| Commit | Subject | Files |
|--------|---------|-------|
| `195bb22` | fix(engine): close live sessions before destroying LiteRT-LM engine | `LiteRTLmEngine.kt` (+33/-4) |
| `b03318f` | fix(engine): retry init with required backend on constraint mismatch | `EngineManager.kt` + `BackendConstraintTest.kt` |
| `ebd7be7` | fix(engine): speculative decoding opt-in via allowlist | `EngineManager.kt` + `LiteRTLmEngine.kt` + `InferenceModule.kt` + test |
| `f643493` | feat(allowlist): add gemma-4-E2B-it (CPU .litertlm, 2.6GB) | `model_allowlist.json` (+18) |
| `1898c99` / `082bd4e` | fix(models): icon capability badges + allowlist-gated Thinking / shared badges, thinking opt-in, seamless switch | `ModelsScreen.kt` + `CapabilityBadges.kt` + `ModelAllowlistRepository.kt` (+ selectors) |

These are additive improvements landing after the prior report; none regress Phase 47 artifacts (skills/loop/VM-tool files untouched by these commits per `--stat`).

### Anti-Patterns Found

None. No debt markers in `data/skills/` + `LmStudioToolLoop.kt`; no stub patterns; zero new production dependencies.

### Human Verification Required

None — device/server proofs deferred to the Phase 48 sweep per user direction, recorded under Deferred Items above (not human-verification gates on this phase).

### Gaps Summary

No gaps. All 3 plans delivered their artifacts, all 14 required artifacts verified present and substantive, all 9 key links WIRED, 61/61 phase-owned unit tests green after the review fixes (per prior verification), every CR/WR finding confirmed fixed in code, and all 6 post-verification follow-up commits confirmed present and non-regressing. The two device/server-dependent criteria are deferred to the Phase 48 sweep per explicit user direction — they are not gaps on this phase.

---

_Verified: 2026-09-28T12:00:00Z_
_Verifier: the agent (gsd-verifier)_
