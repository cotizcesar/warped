# SUMMARY — Agentic-turn Fuentes rows + re-search prompts + warning cleanup

**Status:** COMPLETE — all 3 tasks executed, committed atomically, full gate green.
**Date:** 2026-09-29/30. **Commits:** `0acb8d49` (1.1) · `a384c0ef` (1.2) · `f4f1a7b1` (2.1).

## Test results (verification gate)

| Gate | Result |
|------|--------|
| `./gradlew :app:assembleDebug --rerun-tasks` | BUILD SUCCESSFUL, **zero `w:` lines** (incl. build scripts) |
| `./gradlew :app:lintDebug` | BUILD SUCCESSFUL, **0 issues** in `lint-results-debug.xml` |
| `./gradlew :app:testDebugUnitTest` (full) | **529 tests, 0 failures, 0 errors, 0 skipped** |

New tests: `CompatToolLoopSourcesTest` (3), `ChatLoopSourcesTest` (4); extended
`LocalToolLoopTest` (+3), `LiteRTLmLoopTest` (+5 incl. pin update),
`GroundingPromptTest` (+1), `ToolSetSchemaTest` (+1).

## Task 1.1 — Loop tool results → Fuentes rows (commit `0acb8d49`)

**Plumbing mechanism (planner's choice, documented): option (a) — extend
`StreamToken.ToolCompleted` with `sources: List<GroundedSource> = emptyList()`.**
Why: the token already flows through all 5 loop drivers (local `runToolLoop`,
`CompatToolLoop`, OpenAI + Anthropic inline loops) AND the `LmStudioHelper`
passthrough `map`; default-empty keeps every call site compiling; no new
exhaustive-`when` branches needed anywhere (a new token would force branches in
the VM, `LmStudioHelper`, and tests); a provider accessor would add 5+
interfaces plus a read-vs-Done race. Transient `ToolStatus` handling untouched.

- `LocalToolLoop`: new `ToolCallOutcome(text, sources)` record + `searchSources()`
  / `fetchSources()` extractors reading `Fused.details` verbatim off the SAME
  outcome object (OG columns included) — never re-parsed from fused strings.
- `LiteRTLmProvider`: `executeToolCall` delegates to new `executeToolCallDetailed`
  (existing tests untouched); `runToolLoop` emits `ToolCompleted(local:name#n,
  ≤200-char summary, sources)` per EXECUTED search/fetch call only
  (short-circuit/cap/offline/unknown emit nothing — IN-02 extended).
- `CompatToolLoop` (covers Ollama/LM Studio/Custom) + `OpenAIProvider` +
  `AnthropicProvider` inline loops: private `executeRemoteTool` now returns
  `ToolCallOutcome`; same conditional emission. Wire echoes still in-memory only.
- `ChatViewModel` collector: accumulates `ToolCompleted.sources` across the turn
  (union, first-seen order, distinct by URL); on Done merges with pre-search
  details (`groundedSources` mirrors the ok-URL-only convention) and persists via
  the IDENTICAL `saveMessageWithSources` call — same replaceSources-safety, same
  Snackbar, same post-restart preview. Zero-tool turns still plain-save.
- Reconciliation honored: `ToolCompleted` KDoc + collector comments now state
  explicitly that NO `role=tool`/`Role.TOOL` rows are produced — Fuentes rows are
  the deliverable; legacy TOOL replay untouched.

## Task 1.2 — Re-search rules + history-semantics decision (commit `a384c0ef`)

Re-search rule in three loop-armed prompt sites (all loop-armed models see ≥1):
1. `LiteRTLmProvider.TOOL_USE_SYSTEM_HINT` (local `systemInstruction`): explicit
   "treat each new user message on its own… call web_search again instead of
   answering from stale results" (pin test updated + dedicated contains-test).
2. `GroundingPrompt.SYSTEM_PROMPT` (tool-neutral mirrored line): coherent for the
   pre-search path (model has no tools there; the VM re-searches every turn, and
   the existing "paste a link" recourse stays) AND reaches every armed loop via
   the augmented user message — including all remote loops, which have no other
   prompt channel (their system content is the VM-augmented message).
3. `WEB_SEARCH_TOOL_DESCRIPTION` (re-search clause): flows verbatim into the local
   `@Tool` schema + OpenAI `tools[]` + Anthropic `tools` — the prompt surface
   every armed loop sees when deciding to call (contains-test added).

**HISTORY-SEMANTICS DECISION (explicit, in code at the request-construction site
+ here): KEEP-AS-IS.** Room keeps ORIGINAL user text (saved at send); only the
outgoing request's current message is augmented. Trade-off: (a) history stays
clean, remote replay stays token-lean — stale fused blocks are never re-sent or
persisted; (b) the local native conversation DOES reuse `Message.tool` results
engine-side across turns and remote replays see prior citations without blocks —
residual staleness risk; (c) per-turn conversation resets would cost multi-turn
coherence + reload latency, and history rewrites are forbidden. The prompt rule
is therefore the freshness mechanism. Pinned by `ChatLoopSourcesTest.history
keeps originals…` (request augmented + block-free on armed turns; Room user row
byte-original).

## Task 2.1 — Warning inventory → zero (commit `f4f1a7b1`)

**Kotlin inventory (31 → 0):** `hiltViewModel` import migration ×10 ·
`menuAnchor(type, enabled)` ×2 · `Icons.AutoMirrored.Outlined.OpenInNew` ×2 ·
deleted duplicated `onLowMemory` (real `onTrimMemory` already forwards levels) ·
deleted dead `resolvedSelectedProvider` (sole `ChatUiState`-shim user) · Jsoup
redundant guards ×2 (extractor suites green) · unneeded `!!` ×3 ·
`srcDirs`→`directories` in `build.gradle.kts` (surfaced on script recompile).

**Lint inventory (100+ → 0):** `MissingClass` TimerReceiver — dead manifest entry,
zero code references, deleted (Error) · `ParcelFileDescriptor` leak → `.use{}`
· `commit()`→KTX `edit{}` ×3 · `String.format` locales ×4 · `Uri.parse`→`toUri` ·
`LocalConfiguration`→`LocalWindowInfo` · hoisted `getString`→`stringResource` ×2
(Errors) · `mutableIntStateOf` · `Modifier` first-optional · `ObsoleteSdkInt` ×3
(incl. `mipmap-anydpi-v26`→`mipmap-anydpi`) · uncaught-handler now delegates ·
`DataExtractionRules` (below) · `TypographyEllipsis` ×8 (both locales) ·
`PluralsCandidate` ×3 → real `<plurals>` + `pluralStringResource` (both locales;
fixes "1 pages/lines/conversations" grammar) · `UnusedResources` ×31 (30 strings
+ `splash_bg`, all reference-checked incl. tests) · monochrome launcher layers ·
`logo.png`→`drawable-nodpi` · uiautomator→version catalog.

**Documented suppressions / policy disables (all with in-code justification):**
- 8× `@Suppress("DEPRECATION")` on `ProviderType.LOCAL` branches
  (`ProviderType`, `ToolCapabilityMatrix`, `ProviderRouter` ×4, `ChatViewModel` ×2):
  the deprecated entry is still live and NOTHING produces it anymore — only
  persisted rows may carry it, so exhaustive `when`s must name it. Full removal
  needs a Room migration (Rule-4 scale) → **deferred follow-up**, not silent.
- `app/lint.xml`: `GradleDependency`/`NewerVersionAvailable`/
  `AndroidGradlePluginVersion`/`OldTargetApi` ignored — versions deliberately
  pinned (TOML `HOLD` comments; no android-37 platform exists; STACK.md pins
  targetSdk 35). Upgrades are a release decision.
- `Aligned16KB` ignored — sqlcipher 4.5.4 verified latest on Maven Central;
  no 16KB-aligned upstream build exists. Re-enable on upgrade.
- `InsecureBaseConfiguration` ignored on `<base-config>` — LAN LLM servers are
  HTTP-only on user-entered IPs (existing ENDPT-06 rationale in-file).
- `DataExtractionRules` ignored on `<application>` — `allowBackup="false"`, so no
  backup config takes effect on any API level.
- No `warningsAsErrors` enabled globally, per plan.

## Deviations from plan

None structural. Two test-authoring bugs fixed inline (fixture missing `details`;
`saveMessage` count included the USER row) — no production impact.

## On-device notes (UNCONFIRMED — no adb in this env)

- Fuentes carousel rendering on agentic turns (local + remote) after restart.
- Multi-turn re-search behavior on-device (prompt rule + tests verify statically).
- Themed-icon monochrome rendering; `menuAnchor` dropdown behavior; plural
  rendering in Spanish locale (`many` quantity).

## Self-check

- [x] All 3 tasks executed, each committed atomically (`0acb8d49`, `a384c0ef`, `f4f1a7b1`)
- [x] SUMMARY.md written to the quick-task dir (this file)
- [x] Gate: assembleDebug zero warnings + lintDebug zero issues + 529/529 unit tests
- [x] English copy throughout; no `role=tool` rows resurrected; no global warningsAsErrors
