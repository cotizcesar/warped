# Roadmap: Warped v2.1 — Finish v2.0 Leftovers

**Created:** 2026-09-27
**Phases:** 4 (continuing from Phase 44 — numbering does NOT reset)
**Total requirements:** 18 (SKILLS-07..12, RUNTIME-13..14, PERF-14..16, LRT-07..09, DEPS-01..02, HARD-01..02)
**Granularity:** coarse (per `config.json`)

---

## Historical Note — v2.0 Gallery Convergence & Performance Overhaul

v2.0 shipped 2026-06-06 with 5 phases (40–44) and 53 requirements — 48 MET, 5 PARTIAL (Phase 43 PERF-01, PERF-05, PERF-06, PERF-12, PERF-13) + 2 carry-overs (Phase 44 SKILLS-02/03 Tool execution). Full archive lives at [`.planning/milestones/v2.0-ROADMAP.md`](milestones/v2.0-ROADMAP.md). v2.1 closes every leftover: PERF-01 → PERF-14 (sub-state split), PERF-06 → PERF-15 (keyed LazyColumn), SKILLS-02/03 → SKILLS-08/10 (real tool execution), double-collect → RUNTIME-13, Call.cancel() → RUNTIME-14 — plus the full catalog refresh (LiteRT-LM 0.13.1 → 0.17.1), release hardening, and sub-1s cold start.

**Cumulative state entering v2.1:** 44 phases shipped, 287 requirements delivered across v1.0–v2.0. v2.1 starts at **Phase 45**.

**Still CI-gated (not in v2.1 scope):** PERF-12/13 benchmark numbers require a Pixel 7 reference device — stay deferred.

---

## Phase Order Rationale (researcher disagreement resolved)

Research produced two defensible orders — ARCHITECTURE (runtime → skills → perf: fix the pipe before pushing tool traffic through it) vs PITFALLS (tools → perf → runtime: tools define the turn lifecycle the UI must carry). **Decision: runtime → tools → perf**, for two reasons:

1. The tool loop executes *through* `runInference`. Building a 5-round tool loop on top of the sentinel no-op job + socket-drain defects bakes unstoppable-tool-call bugs into SKILLS-08/10 (a 25-round-cap loop with no working Stop is the worst possible version of this bug).
2. Perf-last *also* satisfies PITFALLS' core concern: the LazyColumn split and sub-state work happen after tool-turn UI states (activity rows, multi-round turns) exist, so the split accommodates them rather than being retrofitted.

Both researchers agree PERF-14 + PERF-15 are atomic (never split) and that runtime/skills touch the same `runInference` flow and must be explicitly sequenced, not parallelized. The roadmap honors both.

**Foundation-first addendum:** DEPS-01/02 + LRT-07 lead (Phase 45) because the engine bump changes the `EngineConfig`/`ConversationConfig`/tool API surface everything else builds on. Research SUMMARY pinned 0.13.1, but REQUIREMENTS (2026-09-27) supersedes: **0.17.1 is the target** (latest stable 2026-09-16, with tool-calling fixes v2.1 adopts via LRT-08).

---

## Phases

- [x] **Phase 45: Foundation Refresh** — Full catalog to latest stable + LiteRT-LM 0.13.1 → 0.17.1 with API-surface re-verification (completed 2026-09-27)
- [x] **Phase 46: Runtime Hardening** — Single shared inference Flow (`shareIn`) + true Stop (`Call.cancel()`/`cancelProcess()`) (completed 2026-09-27)
- [x] **Phase 47: Real Tool Execution** — Skills surface recovery + LiteRT-LM `@Tool`s + LM Studio `tools[]` loop + trust boundary (completed 2026-09-27)
- [x] **Phase 48: Chat Perf + Startup + Release** — Atomic sub-state/LazyColumn split, sub-1s cold start, release hardening sweep (completed 2026-09-27)

---

## Phase Details

### Phase 45: Foundation Refresh

**Goal**: The entire dependency catalog rides latest stable releases and the app runs on LiteRT-LM 0.17.1 with its new `EngineConfig`/`ConversationConfig` surface, R8 rules, and cache schema re-verified — so every later phase builds on the final APIs, not the old ones
**Depends on**: Nothing (Phase 44 complete; foundation for Phases 46–48)
**Requirements**: DEPS-01, DEPS-02, LRT-07, LRT-09
**Success Criteria** (what must be TRUE):

  1. User can load a downloaded `.litertlm` model and chat with streaming on the bumped engine — no `UnsatisfiedLinkError`, no serializer errors, no silent cache-schema drift after the upgrade
  2. Dependency audit returns empty (no SNAPSHOT, no `-alpha` artifacts, RUNTIME-12 anti-pattern list still clean) and the full unit-test suite is green after the refresh
  3. `model_allowlist.json` capability flags reflect only model features actually verified against 0.17.x on Android
  4. Room/Hilt/Navigation breaking changes (if any) are migrated — existing chat history, presets, and endpoints survive the upgrade intact

**Plans**: TBD

### Phase 46: Runtime Hardening

**Goal**: Streaming is truly cancellable on both backends through one shared inference Flow — Stop means stop
**Depends on**: Phase 45 (final engine + OkHttp APIs)
**Requirements**: RUNTIME-13, RUNTIME-14
**Success Criteria** (what must be TRUE):

  1. User taps Stop mid-generation (local or remote) and tokens halt immediately — no trailing tokens keep arriving after Stop
  2. User rotates the device mid-stream and sees no duplicated or dropped tokens in the finished message
  3. User can Stop and immediately send a follow-up message with no hang, wedge, or stale "generating" spinner

**Plans**: TBD

### Phase 47: Real Tool Execution

**Goal**: Enabled skills actually execute — locally via LiteRT-LM `@Tool`s and remotely via the LM Studio `tools[]` loop — with visible progress, graceful errors, and validated inputs
**Depends on**: Phase 46 (cancellable single-flight `runInference` the tool loop runs through)
**Requirements**: SKILLS-07, SKILLS-08, SKILLS-09, SKILLS-10, SKILLS-11, SKILLS-12, LRT-08, HARD-02
**Success Criteria** (what must be TRUE):

  1. User enables the Calculator chip and asks a math question in airplane mode (local model) — the assistant replies with the computed result, not a prompt-injected guess
  2. User asks the same kind of question against an LM Studio remote endpoint — the tool loop runs (tools[] → execute → re-POST → answer) with identical visible behavior
  3. While a tool runs, the user sees a "Using calculator…" status row; on tool failure they see "Calculator failed: …" followed by a plain-text fallback answer — never a hang or empty bubble
  4. Past tool use is visible in the conversation transcript (tool name + summarized result) and survives resume
  5. A model without tool support degrades gracefully — user gets a clear message plus a normal answer, and per-model gating keeps known-bad families (e.g. Qwen3/Gemma template bugs) on prompt-injection fallback until re-tested on-device

**Plans**: 3 plans (47-01 tracer surface, 47-02 local @Tools, 47-03 remote loop)
**UI hint**: yes

### Phase 48: Chat Perf + Startup + Release

**Goal**: Chat stays smooth at scale, the app cold-starts in under a second, and the release build is hardened end-to-end
**Depends on**: Phase 47 (tool-turn UI states exist before the list/state split accommodates them)
**Requirements**: PERF-14, PERF-15, PERF-16, HARD-01
**Success Criteria** (what must be TRUE):

  1. User scrolls a 100+ message conversation with long code blocks during active streaming — no scroll jumps, position stays put unless already at the bottom (with a "Jump to latest" affordance)
  2. User types in the input bar while tokens stream in — keystrokes stay fluid, and streaming tokens never recompose the input bar
  3. Cold start on the Pixel 7 reference device measures under 1 second, recorded in `BENCHMARKS.md` next to the PERF-12 targets
  4. Release build (`assembleRelease`) installs on a real device, tool skills work in it, and logcat shows no secrets, PII, or raw tool arguments

**Plans**: TBD
**UI hint**: yes

---

## Explicit Non-Goals (do NOT phase — per REQUIREMENTS.md Out of Scope)

MCP bridge, JS/WebView skills, parallel tool calls, multimodal/vision/audio, Apple-only LiteRT features (Metal residency, Apple FM adapter), pre-release/alpha artifacts, OkHttp 5.x, SQLCipher removal without measurement, destructive Room migrations.

---

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 45. Foundation Refresh | 2/2 | Complete    | 2026-09-27 |
| 46. Runtime Hardening | 2/2 | Complete    | 2026-09-27 |
| 47. Real Tool Execution | 3/3 | Complete    | 2026-09-27 |
| 48. Chat Perf + Startup + Release | 3/3 | Complete    | 2026-09-27 |

---

## Coverage

- v2.1 requirements: 18 total (note: milestone brief said 19 — actual count in REQUIREMENTS.md is 18: 6 SKILLS + 2 RUNTIME + 3 PERF + 3 LRT + 2 DEPS + 2 HARD)
- Mapped to phases: 18/18 ✓
- Unmapped: 0 ✓

| Requirement | Phase | Notes |
|-------------|-------|-------|
| DEPS-01, DEPS-02 | 45 | Full catalog refresh leads — everything builds on final APIs |
| LRT-07 | 45 | Engine bump 0.13.1 → 0.17.1 rides with the catalog refresh |
| LRT-09 | 45 | API surface / R8 / cache re-verification belongs with the bump |
| RUNTIME-13, RUNTIME-14 | 46 | Sequenced explicitly against Phase 47 (shared `runInference` flow) |
| SKILLS-07 | 47 | Locate-or-rebuild is the first plan in the tool phase (gates 08/10) |
| SKILLS-08, SKILLS-10 | 47 | Local `@Tool`s + remote `tools[]` loop (v2.0 SKILLS-02/03 carry-overs) |
| SKILLS-09 | 47 | Shared mapper lives inside the tool phase, not split out |
| SKILLS-11, SKILLS-12 | 47 | Transcript persistence + progress/error UX through the real path |
| LRT-08 | 47 | 0.14–0.17 tool-calling adoption belongs to the SKILLS-08 path |
| HARD-02 | 47 | Trust boundary tested against the real executor, not in isolation |
| PERF-14, PERF-15 | 48 | ATOMIC — same files, never split |
| PERF-16 | 48 | Pairs with HARD-01 (release build + Pixel 7 measurement) |
| HARD-01 | 48 | Release sweep closes the milestone |
