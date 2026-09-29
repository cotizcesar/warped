# Phase 57 — UI Review (Remote Agentic Loop)

**Audited:** 2026-09-29
**Baseline:** Abstract 6-pillar standards + app conventions (backend phase, no UI-SPEC.md; parity with Phase 56 local loop mandated by 57-CONTEXT.md)
**Screenshots:** Not captured — Android native app, no web dev server (code-only audit of the two UI touchpoints)
**Scope:** `TOOLS_UNSUPPORTED` notice banner branch, remote transient status rows (same row language as Phase 56), Stop behavior in the remote loop

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 3/4 | Notice copy mirrors constant byte-for-byte; "endpoint" is implementer jargon |
| 2. Visuals | 3/4 | Reuses existing banner/row slots cleanly; Info icon undifferentiated across 6 notice kinds |
| 3. Color | 4/4 | Zero new color tokens; primary confined to spinner + OFFLINE-only Retry |
| 4. Typography | 4/4 | 14.sp rows match existing banner/status scale exactly |
| 5. Spacing | 4/4 | No new spacing values; reuses 8.dp/16.dp slot rhythm |
| 6. Experience Design | 2/4 | Exact-string notice routing is fragile; tools-unsupported is a guidance dead-end; Stop device-unverified |

**Overall: 20/24**

---

## Top 3 Priority Fixes

1. **[WARNING] Notice routing keys on exact-string match of user-visible copy** — `ChatViewModel.kt:875` routes with `token.message == ToolCapabilityMatrix.TOOLS_UNSUPPORTED_NOTICE`. Any future copy edit, punctuation drift, or localization silently re-routes the informational notice into the hard-error banner (`ChatError.Network`), breaking Truth 5 ("never silent, never hard error"). Decouple routing from render copy: emit a typed token (e.g. `StreamToken.ToolsUnsupported`) or a dedicated notice field, and keep the constant as render-only.
2. **[WARNING] TOOLS_UNSUPPORTED banner is a dead-end — no actionable next step** — Every sibling banner names a path forward ("check your connection", "paste it in Settings > Web Search"); this one states a limitation with no recourse (`MessageBubble.kt:407-409`). User impact: a user on an incompatible endpoint retries turns indefinitely with no idea that switching model/endpoint would restore web sources. Concrete fix: extend the banner copy with one clause, e.g. "…no web sources this turn. Switch to a tool-capable endpoint to restore search." (keep Retry button hidden — retrying the same endpoint cannot help).
3. **[WARNING] Ephemeral banner evaporates on history reload** — `modelOnlyNotice` is never persisted (`ChatMessage.kt:15-22` comment confirms), so revisiting the conversation shows a model-only answer with zero explanation of why sources are missing. This matches the existing convention for all six notice kinds, so it is not a Phase 57 regression — but Phase 57 adds the first *capability* (not connectivity) reason, which reads as a permanent model defect rather than a transient outage. At minimum document the convention; ideally persist the notice enum on the message row (no migration needed if stored alongside existing ephemeral hydration, same as `groundedSourceDetails` precedent).

---

## Detailed Findings

### Pillar 1: Copywriting (3/4)

**What was built:**
- Constant: `ToolCapabilityMatrix.kt:45-47` — `"This endpoint doesn't support tool calling. Model-only answer — no web sources this turn."`
- Banner branch: `MessageBubble.kt:407-409` — byte-identical mirror (comment at `:402-406` explicitly documents the mirror contract).
- Status rows: unchanged `LocalToolLoop.statusDisplay` (`Searching for "<query>"…` / `Reading <host>…` / bare `Searching…`/`Reading…` fallbacks) reused verbatim by all three remote drivers (`CompatToolLoop.kt:156`, `OpenAIProvider.kt:275`, `AnthropicProvider.kt:303`). No new row copy introduced — parity requirement met.

**Findings:**
- **(WARNING) "endpoint" is implementer jargon.** Users select *models* in the inline selector bar (`ChatScreen.kt:398-412`); "endpoint" never appears in user-facing chrome elsewhere. A user on LM Studio/Ollama sees "This endpoint doesn't support…" with no mapping to what they tapped. Prefer "This model/server doesn't support…" or name the selected model.
- **(Minor) Copy/constant duplication without a compile guard.** The banner hard-codes the same words as the constant with only a comment linking them. A one-character edit to either side breaks both the user-visible consistency *and* the `==` routing in `ChatViewModel.kt:875`. Reference the constant (or a shared `toolsUnsupportedMessage()` function) from the composable instead.
- **Positive:** English-only, em-dash style and "Model-only answer" phrasing consistent with all five sibling notices; no generic "Submit/OK/Error occurred" patterns (grep over `ui/chat/` returns none); pluralization logic untouched and correct (tools notice takes no count — correctly).

### Pillar 2: Visuals (3/4)

**What was built:**
- Banner: new `ModelOnlyNotice.TOOLS_UNSUPPORTED` enum value (`ChatMessage.kt:43`) rendered through the existing `ModelOnlyBanner` slot (`MessageBubble.kt:407-409`). No new composable, no layout fork.
- Status rows: remote loops emit the same `ToolStatus(display)` → work → `ToolCompleted` → `ToolStatus(null)`-in-`finally` sequence; the single `ChatScreen.kt:330-356` transient slot renders them identically for local and remote. `maxLines = 1` + `Ellipsis` + spinner + `contentDescription = "Running tool: $status. Tap Stop to cancel."` all inherited.

**Findings:**
- **(WARNING) Six notice kinds, one undifferentiated Info icon.** `MessageBubble.kt:371-376` renders `Icons.Filled.Info` in `onSurfaceVariant` for OFFLINE, FETCH_FAILED, all three Tavily gates, and now TOOLS_UNSUPPORTED. A capability limitation (won't fix itself) is visually identical to a transient outage (will fix itself). Consider a distinct icon/tint for the capability class — or at minimum document the intentional uniformity.
- **(Minor) `contentDescription = null` on the banner icon (`:373`).** Pre-existing pattern, not introduced by Phase 57 — but the audit notes it: TalkBack users get the banner text but no icon role. Acceptable, flagged for consistency backlog only.
- **Positive:** Clear focal hierarchy preserved — transient row lives in the same above-input slot as the fetch chip, never enters the transcript, unmounts on Done/Error/Stop/new-send. No focal-point regression. Icon-only Stop button already paired with semantics (Phase 56 precedent, untouched).

### Pillar 3: Color (4/4)

- Banner text + icon: `onSurfaceVariant` (`MessageBubble.kt:374,412`) — identical to all five siblings. No accent creep.
- Retry `TextButton` text is the only `primary` in the banner, and its visibility gate is OFFLINE-only (`:418`) — TOOLS_UNSUPPORTED correctly renders with **no** primary action, which is semantically right (retry cannot help).
- Status row: spinner `primary` + text `onSurface` (`ChatScreen.kt:341-350`) — inherited unchanged; remote adds zero new usages.
- Grep for hardcoded hex/`rgb(` across `data/agentic/` + the three drivers: clean (only pre-existing `Color(0xFF9CA3AF)` empty-state in `ChatScreen.kt:436`, untouched).
- **Finding (informational, no deduction):** The absence of any visual distinction for the capability notice is a deliberate color-pass side effect — see Visuals finding. No color-contract violation.

### Pillar 4: Typography (4/4)

- Banner: `fontSize = 14.sp` (`MessageBubble.kt:411`); status row: `fontSize = 14.sp` (`ChatScreen.kt:349`). Both match the existing notice/status scale exactly; no new size introduced.
- No new `fontWeight` / `TextStyle` in Phase 57 touchpoints (banner inherits default weight like siblings; status row inherits like Phase 56).
- Distinct-size/weight counts across `ui/chat/` unchanged from Phase 56 baseline.
- **Finding (informational, no deduction):** None — typography parity is exact.

### Pillar 5: Spacing (4/4)

- Status row: `padding(horizontal = 16.dp)`, `Spacer(8.dp)`, 16.dp spinner, trailing `Spacer(8.dp)` (`ChatScreen.kt:332-355`) — byte-identical slot to the Phase 56 row and the fetch chip above it.
- Banner: `Spacer(Modifier.width(8.dp))` after 16.dp icon (`MessageBubble.kt:377`) — identical to all siblings; new branch adds no padding/margin values.
- No arbitrary `[…px]`/`[…rem]` values in touched files.
- **Finding (informational, no deduction):** None — spacing parity is exact.

### Pillar 6: Experience Design (2/4)

**State coverage (verified in code):**
- Loading/running: `ToolStatus(display)` per call on all three drivers, cleared in `finally` + on `ToolCompleted`/Done/Error/Stop/new-send (`ChatViewModel.kt:780-781,828,863,903,1081`). ✅
- Error: genuine errors keep the `ChatError.Network` banner path (`ChatViewModel.kt:877-886` else-branch). ✅
- Empty: N/A (no list UI added).
- Disabled: send gating (`ChatScreen.kt:360`) untouched. ✅
- Destructive confirmation: N/A.
- Stop: `ensureActive` per round + per call, retained `Call` + `cancelChat()` on all five providers, `callHook → activeCall` chain for the LM Studio helper path (fix commits `ba8227b9`, verified live per 57-VERIFICATION.md). ✅ contract; see caveat below.

**Findings:**
- **(WARNING) Exact-string routing is the single fragile seam.** `ChatViewModel.kt:875` — see Priority Fix #1. The failure mode is a *silent UX inversion* (informational → hard error banner + `isGenerating = false` mid-turn while the plain retry stream is still expected). A string-equality unit test exists only implicitly via copy-mirror; there is no test asserting the VM routes the constant to `TOOLS_UNSUPPORTED` rather than `ChatError.Network`. Add one (emit `StreamToken.Error(TOOLS_UNSUPPORTED_NOTICE)` through the collector, assert `modelOnlyNotice == TOOLS_UNSUPPORTED && error == null`).
- **(WARNING) Dead-end banner.** See Priority Fix #2.
- **(WARNING) Stop-cancels-mid-loop verified statically only.** 57-VERIFICATION.md explicitly defers device proof ("needs a finger on a real turn", accepted per v2.2 precedent). The contract is construction-identical across all five providers, so risk is low — but a remote tool-call turn holds *two* cancellables (SSE `Call` + `Dispatchers.IO` fetch coroutine), one more than the local loop, and the interleaving has never been finger-tested. Keep the accepted follow-up open; do not close before a live armed-turn Stop smoke.
- **(Minor) Notice-vs-row timing.** The `TOOLS_UNSUPPORTED` notice is emitted on the error channel while `toolCallActive` rows follow normal disappearance rules — during the exactly-one plain retry, no status row shows (correct: no tool is running), but `isGenerating` stays true with no visible progress until the first plain token arrives. On slow endpoints this reads as a hang. Consider holding a transient "Retrying without tools…" row for the retry round.
- **Positive:** Validation short-circuits emit no status row (IN-02 parity, no flash); cap path feeds `CAP_REACHED_STRING` and drops tools for the answer round (no infinite-loop UX); `retryGrounding` correctly stays OFFLINE-only (enum comment `ChatMessage.kt:41` + gate `:418`).

---

## Registry Safety

Skipped — no `components.json` (native Android project, no shadcn registries).

---

## Files Audited

- `app/src/main/java/com/warped/data/agentic/ToolCapabilityMatrix.kt` (notice constant, matrix, classifier)
- `app/src/main/java/com/warped/data/remote/provider/CompatToolLoop.kt` (shared driver: status rows, retry+notice)
- `app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt` (tooled loop)
- `app/src/main/java/com/warped/data/remote/provider/AnthropicProvider.kt` (native loop)
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` (notice routing `:875-876`, remote-armed skip, Stop)
- `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt` (banner branch `:402-409`)
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` (transient row slot `:330-356`)
- `app/src/main/java/com/warped/domain/model/ChatMessage.kt` (enum `:29-44`), `domain/model/StreamToken.kt`, `ui/chat/ChatUiState.kt`
- `.planning/phases/57-remote-agentic-loop/57-CONTEXT.md`, `57-01-SUMMARY.md`, `57-02-SUMMARY.md`, `57-VERIFICATION.md`

---

## Recommendation Count

- Priority fixes: 3 (all WARNING — no BLOCKER; user task completion never breaks, degraded paths always terminate in a readable answer)
- Minor recommendations: 3 (jargon copy, undifferentiated icon, retry-round progress gap)
- Informational: 3 (color/type/spacing parity exact — no action)
