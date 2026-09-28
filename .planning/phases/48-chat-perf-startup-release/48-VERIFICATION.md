---
phase: 48-chat-perf-startup-release
verified: 2026-09-28T12:30:00Z
status: passed
score: 4/4 must-haves verified
overrides_applied: 0
---

# Phase 48: Chat Perf + Startup + Release Verification Report

**Phase Goal:** Chat stays smooth at scale, the app cold-starts in under a second, and the release build is hardened end-to-end
**Verified:** 2026-09-28T12:30:00Z
**Status:** passed
**Re-verification:** No — initial verification (includes review-fix confirmation)

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User scrolls a 100+ message conversation with long code blocks during active streaming — no scroll jumps, position stays put unless already at bottom (with "Jump to latest" affordance) | ✓ VERIFIED | `ChatScreen.kt:342` `items(transcript.messages, key = { it.id })`; stick gating `showPill = !isEmpty && !isAtBottom && hasNewContentBelow` (:235); pill `contentDescription = "Jump to latest message"` (:578); zero `animateScrollTo` auto-scroll; user device-approved 2026-09-28 |
| 2 | User types in the input bar while tokens stream in — keystrokes stay fluid, streaming tokens never recompose the input bar | ✓ VERIFIED | `ChatUiState.kt` 4× `@Immutable` sub-states; `ChatScreen.kt:77-79` per-sub-state `collectAsStateWithLifecycle`; `ChatSubStateTest` + `ChatKeyStabilityTest` green (isolation proven both directions); user device-approved 2026-09-28 |
| 3 | Cold start on reference device measures under 1 second, recorded in `BENCHMARKS.md` next to the PERF-12 targets | ✓ VERIFIED | `BENCHMARKS.md:7` normative `<1s` target; `baseline-prof.txt` seed checked in + `profileinstaller 1.4.1` in release graph + lazy native load (`ensureNativeLoaded`, `LiteRTLmEngine.kt:40-48`); Pixel 7 hardware unavailable per CONTEXT-locked decision (emulator measurement + note); user device-approved cold-start median 2026-09-28 |
| 4 | Release build (`assembleRelease`) installs on a real device, tool skills work in it, and logcat shows no secrets, PII, or raw tool arguments | ✓ VERIFIED | `takeLast(100)` leak REMOVED (`LiteRTLmProvider.kt` length-only); R8 full mode confirmed (`gradle.properties:10` + AGP 9.3.0) with ToolSet/skills keeps (`proguard-rules.pro:30-36,84-85`); `audit-dependencies.sh` OK; user device-approved release smoke + deferred 45/46/47 items 2026-09-28 ("todo lo probe y quedo limpio") |

**Score:** 4/4 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `app/src/main/java/com/warped/ui/chat/ChatUiState.kt` | Three @Immutable single-owner sub-states | ✓ VERIFIED | 4× `@Immutable`, `ChatListKeys` constants, single-owner updaters |
| `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` | Keyed LazyColumn + stick + pill | ✓ VERIFIED | Keyed items, per-sub-state collectors, spec-exact pill, CR-01 fix (`isEmpty` covers reasoning/tool/notice) |
| `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` | Sub-state flows + fresh reads | ✓ VERIFIED | 3 StateFlows, WR-01/02/03/04/06 fixes present |
| `app/src/androidTest/java/com/warped/benchmark/BaselineProfileGenerator.kt` | BaselineProfileRule generator | ✓ VERIFIED | Present, uses `com.warped.app` package |
| `app/src/main/baselineProfiles/baseline-prof.txt` | Checked-in seed profile | ✓ VERIFIED | Present, HS-flagged, AGP-valid |
| `app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt` | Lazy native load, no Error crash | ✓ VERIFIED | CR-02 fix: `ensureNativeLoaded` throws `IllegalStateException` |
| `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt` | Length-only logging | ✓ VERIFIED | LEAK-REMOVED, gate clean |
| `app/proguard-rules.pro` | ToolSet/skills keeps | ✓ VERIFIED | `:30-36`, `:84-85` present |
| `BENCHMARKS.md` | <1s target + numbers/note | ✓ VERIFIED | `<1s` normative + emulator note + hardware TODO |
| `app/src/test/java/com/warped/ui/chat/ChatSubStateTest.kt` | Isolation proof | ✓ VERIFIED | Present |
| `app/src/test/java/com/warped/ui/chat/ChatKeyStabilityTest.kt` | Key stability proof | ✓ VERIFIED | Present |
| `app/build.gradle.kts` + `gradle/libs.versions.toml` | Catalog benchmark deps | ✓ VERIFIED | WR-08 fix: `libs.benchmark.*` via catalog |
| `app/src/main/java/com/warped/WarpedApplication.kt` | URL-credential redaction | ✓ VERIFIED | WR-07 fix present |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| ChatScreen.kt | ChatViewModel sub-state flows | collectAsStateWithLifecycle per sub-state | WIRED | 3 collectors, never the monolith |
| ChatScreen.kt | LazyColumn items | items(messages, key = { it.id }) + trailing keys | WIRED | Stable keys incl. constant "streaming" |
| app/build.gradle.kts | androidx.profileinstaller | release dependency | WIRED | `implementation(libs.profileinstaller)` resolves 1.4.1 |
| MainActivity.kt | splash exit | setKeepOnScreenCondition false + 200ms fade | WIRED | `:25`, `:27-38`, audited unchanged |
| proguard-rules.pro | ToolSet/@Tool/skills | explicit keeps | WIRED | Verified present, release smoke proves survival |
| release logcat | RedactingTree | DEBUG-only planting + grep gate | WIRED | `WarpedApplication.kt:40-41`, gate clean |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| ChatScreen list | transcript.messages | Room via ViewModel collectors, id-preserving mapper | ✓ | ✓ FLOWING |
| Streaming bubble | transcript.streamingContent/streamingReasoning | shareIn per-turn inference Flow (Phase 46) | ✓ | ✓ FLOWING |
| Tool rows | transcript.toolCallActive/activeToolError | Phase 47 tool loop | ✓ | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Dependency audit green | `bash scripts/audit-dependencies.sh` | OK: no banned, no pre-release | ✓ PASS |
| Raw-content leak gone | `grep takeLast(100) LiteRTLmProvider.kt` | LEAK-REMOVED | ✓ PASS |
| Review-fix commits present | `git log --oneline --grep="48"` | CR-01/CR-02 + WR-01..08 (11 fix commits) | ✓ PASS |

### Probe Execution

| Probe | Command | Result | Status |
|-------|---------|--------|--------|
| n/a — no phase-declared probes | — | SKIPPED (no runnable probes declared) | — |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| PERF-14 | 48-01 | Sub-state split, tokens never recompose input | ✓ SATISFIED | Sub-states + isolation tests + audit |
| PERF-15 | 48-01 | Keyed LazyColumn + stick + pill | ✓ SATISFIED | Keyed list + pill + CR-01 fix |
| PERF-16 | 48-02 | Baseline Profiles + lazy startup + <1s + BENCHMARKS | ✓ SATISFIED | Profile ships, audit evidence, <1s target, user-approved median |
| HARD-01 | 48-03 | R8 + audit + clean logs + network/Keystore re-audit | ✓ SATISFIED | Full sweep + user-approved smoke |

### Review-Fix Confirmation (48-REVIEW.md)

| Finding | Commit | Status | Evidence |
|---------|--------|--------|----------|
| CR-01 reasoning-only empty state | `13777fa` | ✓ FIXED | `isEmpty` now covers reasoning + toolCallActive + activeToolError + notice |
| CR-02 swallowed native-load Error crash | `2a3c83f` | ✓ FIXED | `ensureNativeLoaded` throws `IllegalStateException` |
| WR-01 stale thinking flags | `975e726` | ✓ FIXED | `_input.value.enableThinking` fresh read (:443) |
| WR-02 fail-closed media gate | `7190886` + `5f13d38` | ✓ FIXED | Fail-open with unknown-capabilities log (:300-301) |
| WR-03 presence-not-capability thinking check | `f192e57` | ✓ FIXED | `capabilities?.reasoning == true` (:954) |
| WR-04 stale tool/notice state | `b0fb032` | ✓ FIXED | stopGeneration clears all three transient fields |
| WR-05 nullable name interpolation | `eb7dd33` | ✓ FIXED | `"Unknown model"` fallback (:258-259) |
| WR-06 Long-vs-UUID deleteMessage | `947eb24` | ✓ FIXED | `deleteMessage(String)` + Long overload (:988,998) |
| WR-07 log redaction gaps | `c443169` | ✓ FIXED | URL-credential redaction in RedactingTree |
| WR-08 hardcoded benchmark deps | `3529780` | ✓ FIXED | `libs.benchmark.macro.junit4/junit4` via catalog |

### Anti-Patterns Found

None — no TBD/FIXME/placeholder/console-only stubs in phase-touched files. `baseline-prof.txt` seed TODO is intentional and tracked (belongs to 48-02 summary, hardware-blocked, not a stub).

### Human Verification Required

None outstanding — user device-approved all checkpoints 2026-09-28 (cold-start median, release smoke, deferred 45/46/47 items: "todo lo probe y quedo limpio"). Pixel 7 reference numbers remain a CI TODO alongside PERF-12/13 (out of v2.1 scope per ROADMAP).

### Gaps Summary

No gaps. All 4 roadmap success criteria verified against codebase evidence, all 10 review findings confirmed fixed in code with commits, all 4 requirements satisfied, audit green. Phase goal achieved. Ready to proceed.

---

_Verified: 2026-09-28T12:30:00Z_
_Verifier: the agent (gsd-verifier)_
