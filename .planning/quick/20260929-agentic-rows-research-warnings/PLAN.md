# PLAN — Agentic-turn Fuentes rows + re-search prompts + warning cleanup

## Goal
Agentic (tool-loop) turns persist their per-turn web sources as Fuentes rows (local AND remote loops), follow-up questions re-search instead of answering from stale turn-1 sources, and the build is warning-free (Kotlin + lint).

## Locked diagnoses (verified in code 2026-09-29 — NON-NEGOTIABLE problem statements)
- **BUG 1 (no Fuentes/carousel on agentic turns):** the armed-loop path never persists rows. `ChatViewModel` armed-skip (~L563: `loopArmed` → `augment(null)`) leaves `groundedSources`/`groundedSourceDetails` empty; the collector treats `ToolStatus`/`ToolCompleted` as transient only (~L793-794, "Rows NEVER reach ChatMessage/Room/transcript"); the Done save (~L827-849 `saveMessageWithSources`) persists the empties → assistant message saved sourceless, yet the answer cites [1..5] from the in-context fused block. Loop side: `LiteRTLmProvider.runToolLoop` (~L382-452) feeds `executeToolCall` results back only as `Content.ToolResponse` — engine-internal, nothing flows back to the VM for persistence. Remote loops have the identical gap: `CompatToolLoop` L168 emits `ToolCompleted(call.id, ≤200-char summary)` with round echoes "in-memory only, never persisted to Room" (L59); `OpenAIProvider.kt:284` and `AnthropicProvider.kt:312` use the same inline pattern; `LmStudioHelper.kt:144` is passthrough. `StreamToken.ToolCompleted` carries `toolId`/`summary`/`errorReason` only — no sources. FIX: plumb per-turn tool results (URLs + texts + OG if available) from the loop back to the VM and persist via the identical `saveMessageWithSources` path (local AND remote loops). Mechanism is the planner's call (extend `ToolCompleted`? new token? provider accessor read post-collection?) with constraints: no history rewrite, same row shape incl. OG columns, `replaceSources`-safe, transient status rows untouched.
- **BUG 2 (follow-ups stuck on turn-1 sources):** the model answers from history sources, never re-searches. Neither `GroundingPrompt.SYSTEM_PROMPT` (`GroundingPrompt.kt:12-18` — "no context block → ask the user to paste a link", no re-search rule) nor `LiteRTLmProvider.TOOL_USE_SYSTEM_HINT` (`LiteRTLmProvider.kt:96-100` — no per-turn-independence / stale-source rule) tells the model to RE-search on follow-ups. Stale-source persistence mechanism: Room transcript keeps originals (`ChatViewModel` L380 saves original `userMessage`; L738-749 comment confirms only the outgoing request carries augmented text), BUT the local native conversation is reused across turns (`acquire` L340-356) with fused `Message.tool` results re-entering model context — stale sources persist engine-side; remote stateless loops see prior-turn citations in the replayed transcript. FIX: (a) prompt rule in `TOOL_USE_SYSTEM_HINT` (+ minimal mirrored line in grounding `SYSTEM_PROMPT` if coherent): treat each new user message independently — if it needs facts not covered by prior results, call `web_search` again; never answer from stale sources; (b) planner MUST analyze and decide: is the AUGMENTED user text (with fused Source blocks) saved into history? If yes, decide keep (rely on prompt fix) vs save-original (rely on rows + re-search), documenting the context-semantics trade-off explicitly. No silent choice.
- **WORKSTREAM 3 (warnings):** fix ALL build warnings (Kotlin + lint) from the inventory — zero-warning build is the gate (`warningsAsErrors` NOT to be enabled globally; fix causes, no blanket suppressions except documented false positives).

## Out of scope
- Extraction/budgets, new tools, OG-scrape changes, engine/provider loop mechanics beyond the persistence plumbing.

## Honest notes
- Multi-turn re-search behavior + carousel-on-agentic-turns need on-device confirmation (no adb in this env) — verify via build + unit tests; state the on-device check explicitly in the summary.
- Reconciliation flag for the planner: `StreamToken.ToolCompleted` KDoc says the VM writes `role=tool` transcript rows, but the VM collector (~L794) only clears the transient row and Phase 49 DEL-01 says no `Role.TOOL` rows are produced. Do not resurrect `role=tool` rows; the Fuentes-rows persistence is the deliverable.

---

## Workstream 1 — Plumb loop tool results to Fuentes rows (local + remote)

### Task 1.1: Per-turn tool-result → `saveMessageWithSources` persistence
- **Files:**
  - `app/src/main/java/com/warped/domain/model/StreamToken.kt` (only if extending `ToolCompleted` / adding a token — planner's call)
  - `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt` (`runToolLoop` ~L382-452, `executeToolCall` ~L462+)
  - `app/src/main/java/com/warped/data/remote/provider/CompatToolLoop.kt` (`runTurn` round loop, `executeRemoteTool`, `summarizeForTranscript`)
  - `app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt` (inline loop ~L284), `AnthropicProvider.kt` (inline loop ~L312) — same treatment as CompatToolLoop
  - `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` (collector ~L782-899: accumulate per-turn sources; Done save ~L820-864)
  - `app/src/main/java/com/warped/data/agentic/LocalToolLoop.kt` (`mapSearchOutcome`/`mapFetchResult` — only if the richer record is threaded through here)
  - New/updated unit tests under `app/src/test/java/com/warped/...` (loop-turn row-persistence tests, local + remote)
- **Action:**
  - Choose ONE plumbing mechanism and document why: (a) extend `ToolCompleted` with an optional sources payload (e.g. `List<GroundedSource>`-equivalent, default empty so existing call sites compile), (b) a new `StreamToken` (e.g. `ToolSources`) emitted per tool call, or (c) a provider accessor the VM reads post-collection (before Done-save). Constraint: transient `ToolStatus` rows untouched; no history rewrite.
  - The payload must carry what the Fuentes rows need in the SAME row shape incl. OG columns: URLs + texts (+ OG title/image/description where the outcome already has them — OG scraping itself is out of scope; reuse whatever `GroundedSource` details the `DDG-search` outcome / `MultiUrlResult.Fused.details` already carry). Key subtlety the executor currently discards structure: `executeToolCall`/`executeRemoteTool` map outcomes to fused strings via `mapSearchOutcome`/`mapFetchResult` — capture the structured details alongside the mapped string (richer return record or per-turn side-channel), not by re-parsing the fused string.
  - Local: `runToolLoop` must surface per-call source details to the VM (it currently emits nothing persistable — local path never emits `ToolCompleted` per `StreamToken.kt:22-24`).
  - Remote: `CompatToolLoop` + both inline loops (`OpenAIProvider`, `AnthropicProvider`) must surface the same payload shape (same row shape everywhere).
  - VM collector: accumulate per-turn tool sources across the turn (multiple `web_search`/`web_fetch` calls union, same details-union semantics as the fetch/search branches ~L448-458/604-605); on Done, persist via the IDENTICAL `saveMessageWithSources` path (~L847-853) so `replaceSources`-safety, failure Snackbar, and post-restart preview behave exactly like grounded turns. Armed turns with zero tool calls keep today's behavior (sourceless save, no crash).
  - Tests: loop-turn persistence — local loop turn with one `web_search` outcome yields rows via `saveMessageWithSources` (fake executor outcome with known URLs, assert row URLs/order/shape incl. OG columns); remote `CompatToolLoop` turn ditto; multi-call union (search + fetch) merges without dupes; zero-tool-call turn saves plain; `replaceSources`-safety (retry path untouched — assert `replaceSources`, not re-save).
- **Verify:** `./gradlew :app:testDebugUnitTest --tests "com.warped.data.local.inference.*" --tests "com.warped.data.remote.provider.*" --tests "com.warped.ui.chat.*" --tests "com.warped.data.agentic.*"` green (adjust filters to actual test packages).
- **Done:** An agentic turn whose loop ran `web_search`/`web_fetch` persists Fuentes rows identical in shape to pre-search grounded turns; local + remote; zero-tool turns unchanged.

### Task 1.2: Re-search prompt rules + history-semantics decision
- **Files:**
  - `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt` (`TOOL_USE_SYSTEM_HINT` L96-100)
  - `app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt` (`SYSTEM_PROMPT` L12-18 — minimal mirrored line ONLY if coherent with the loop path)
  - Remote providers' system-prompt/tool-hint sites (grep `TOOL_USE_SYSTEM_HINT` / system prompt constants in `data/remote/provider/` — mirror the rule everywhere the loop is armed; list every touched site in the summary)
  - `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` (history construction L744-749 — ONLY if the history-semantics decision requires a change)
  - Unit tests: prompt-text tests asserting the re-search rule presence (follow the `LiteRTLmLoopTest` pin-verbatim precedent); history-semantics test for whichever side of the decision is implemented
- **Action:**
  - (a) Add the prompt rule to `TOOL_USE_SYSTEM_HINT`: treat each new user message independently — if it needs facts not covered by prior tool results, call `web_search` again; never answer from stale sources / prior-turn citations alone. Keep it short (the hint is pinned verbatim by `LiteRTLmLoopTest` — update the pin). Add a minimal mirrored line in grounding `SYSTEM_PROMPT` only if it stays coherent for the non-loop (pre-search) path; if incoherent, document why it was left out.
  - (b) MANDATORY analysis with an explicit documented decision (code comment + summary section): is the AUGMENTED user text (with fused Source blocks) saved into history? Evidence to reconcile: VM L380/L738-749 (Room keeps originals) vs local native-conversation reuse (`acquire` L340-356, fused `Message.tool` re-entering context) vs remote stateless replay (prior assistant citations without blocks). Decide: keep-as-is (rely on prompt fix) vs save-original-everywhere (rely on rows + re-search). Document the context-semantics trade-off (token cost of re-sending blocks vs staleness risk vs prompt-reliance) explicitly. No silent choice — a reviewer must be able to find the decision and its rationale.
  - Do NOT change loop mechanics, budgets, or caps.
- **Verify:** `./gradlew :app:testDebugUnitTest --tests "*LiteRTLmLoop*" --tests "com.warped.data.grounding.*"` green; `git diff` shows the decision comment.
- **Done:** Re-search rule present in all loop-armed prompt sites with pin tests updated; history-semantics decision documented in code + summary with trade-off.

---

## Workstream 2 — Zero-warning build (Kotlin + lint)

### Task 2.1: Warning inventory → fix all causes
- **Files:**
  - Determined by the inventory (run first, fix list = checklist). Likely candidates: unused imports/params, deprecated API usages, unchecked casts, missing `when` exhaustiveness, lint `UnusedResources`/`HardcodedText`/etc.
  - `app/build.gradle.kts` — ONLY if a warning requires a config-level fix; do NOT enable `warningsAsErrors` globally.
- **Action:**
  - Step 1 — build the inventory: run `./gradlew :app:assembleDebug` capturing ALL Kotlin warnings, and `./gradlew :app:lintDebug` (or `lint`) capturing ALL lint warnings. Record the full list with counts per category in the task summary (this inventory IS the checklist — every item must be checked off).
  - Step 2 — fix causes, not symptoms: remove dead code/unused symbols where safe; fix real deprecations by migrating to the current API; fix real lint findings (resources, accessibility, security). No blanket `//noinspection` / `@Suppress` — each suppression must name the specific false positive with a one-line justification comment, and the summary must list every suppression added.
  - Re-run both commands after fixes; iterate until ZERO warnings from both.
- **Verify:** `./gradlew :app:assembleDebug` emits zero warnings; `./gradlew :app:lintDebug` reports zero warnings; `./gradlew :app:testDebugUnitTest` (full suite) green.
- **Done:** Zero-warning `assembleDebug` + zero-warning `lintDebug` + full unit suite green, with the inventory checklist (categories + counts, all checked) and any per-site suppression justifications recorded in the summary.

---

## Must-haves (goal-backward)
- **Truths:**
  - An agentic local-loop turn that ran `web_search` shows the Fuentes block/carousel with the turn's URLs after restart (rows persisted, not just in-context citations).
  - The same holds for an agentic remote-loop turn (OpenAI-compat path at minimum; OpenAI + Anthropic inline loops included).
  - A follow-up question needing new facts triggers a fresh `web_search` instead of answering from turn-1 sources (prompt rule + tests; on-device multi-turn behavior flagged for physical-device confirmation).
  - `./gradlew :app:assembleDebug` + `./gradlew :app:lintDebug` report zero warnings.
- **Artifacts:**
  - Loop→VM sources plumbing (extended `ToolCompleted`, new token, or provider accessor — planner's documented choice) in `LiteRTLmProvider.kt` + `CompatToolLoop.kt` + `OpenAIProvider.kt` + `AnthropicProvider.kt`.
  - VM accumulation + `saveMessageWithSources` on loop turns in `ChatViewModel.kt`.
  - Re-search rule in `TOOL_USE_SYSTEM_HINT` (+ mirrored line or documented omission in `SYSTEM_PROMPT`).
  - History-semantics decision recorded in code + summary.
  - Warning inventory checklist with counts in the summary.
- **Key links:**
  - Loop tool outcome → VM persistence path (`saveMessageWithSources`, same call site as grounded turns).
  - Prompt rule → all loop-armed prompt sites (local + every remote driver).
  - Warning inventory item → fixed cause or justified suppression (no orphans).

## Verification gate
`./gradlew :app:assembleDebug` (zero warnings) + `./gradlew :app:testDebugUnitTest` (full, green) + `./gradlew :app:lintDebug` (zero warnings). On-device checks that cannot run here (multi-turn re-search behavior, carousel rendering on agentic turns) must be listed as explicit unconfirmed items in the summary.
