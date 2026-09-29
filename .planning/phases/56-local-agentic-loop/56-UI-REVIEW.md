# Phase 56 — UI Review (Local Agentic Loop, transient ToolStatus rows)

**Audited:** 2026-09-29
**Baseline:** abstract 6-pillar standards + app conventions (no UI-SPEC.md — backend phase; scope is transient "Using …" rows only, no new screens)
**Screenshots:** not captured (native Android app — no web dev server; code-only audit of `ChatScreen.kt`, `ChatViewModel.kt`, `ChatUiState.kt`, `LocalToolLoop.kt`, `LiteRTLmProvider.kt`)
**Device checkpoint:** APPROVED on hardware per 56-02-SUMMARY (real ToolCall emission, rows shown then gone) — visual appearance itself was never screenshot-verified.

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 2/4 | Raw snake_case tool names (`web_search`/`web_fetch`) leak to users; unbounded query/URL with no truncation |
| 2. Visuals | 3/4 | Exact reuse of fetch-chip pattern (spinner + 14sp row); unbounded text can wrap/push input bar; dual-chip stacking possible |
| 3. Color | 4/4 | MaterialTheme only (`primary`, `onSurface`), zero hardcoded colors in new UI |
| 4. Typography | 3/4 | 14.sp matches sibling chip but hardcoded instead of `MaterialTheme.typography`; no `maxLines`/`ellipsis` |
| 5. Spacing | 4/4 | 16.dp / 8.dp / 16.dp spinner matches fetch chip exactly; 4dp scale clean |
| 6. Experience Design | 3/4 | Lifecycle complete on all 7 clear paths, Stop verified; tool failures invisible to user, no step-count progress |

**Overall: 19/24**

---

## Top 3 Priority Fixes

1. **User-facing copy exposes internal tool identifiers** (`ChatScreen.kt:345` renders `"Using $status"` where status = `"web_search: <query>"` from `LocalToolLoop.statusDisplay`, `LocalToolLoop.kt:109-120`) — user impact: non-technical users see `web_search`/`web_fetch` jargon instead of an action ("Searching…", "Reading…"). Concrete fix: map to human verbs at render or in `statusDisplay` — `web_search → "Searching for \"<query>\"…"`, `web_fetch → "Reading <host>…"` (host-only, not full URL).
2. **Unbounded model-authored text with no truncation** (`ChatScreen.kt:344-348` — `Text` has no `maxLines`, `overflow`, or length cap; `statusDisplay` passes full query/URL verbatim) — user impact: a long URL or verbose query wraps to N lines and shoves the input bar / message list. Concrete fix: `maxLines = 2, overflow = TextOverflow.Ellipsis` on the `Text` + cap display string to ~80 chars in `statusDisplay` (`take(80)` + "…").
3. **Tool failures are silent to the user** (executors map every failure to a model-facing string via `toolFailureMessage`/`OFFLINE_STRING`, `LiteRTLmProvider.kt:455-508`; the UI row just clears on Done and the answer arrives with no failure affordance) — user impact: user watches "Using …" spinner, then gets a degraded/model-only answer with no indication grounding failed. Concrete fix: on tool-result-is-failure, either keep the existing grounded-failure notice path (modelOnlyNotice / Fuentes copy per Phase 55) visibly attached, or emit a one-shot Snackbar "Search failed — answered from model knowledge." Advisory only; do not add a persistent row.

---

## Detailed Findings

### Pillar 1: Copywriting (2/4) — WARNING

- **[WARNING] Internal identifiers in user-visible copy.** `statusDisplay()` (`LocalToolLoop.kt:109-120`) returns `"web_search: <query>"` / `"web_fetch: <url>"`, rendered verbatim as `"Using web_search: …"` (`ChatScreen.kt:345`). `web_search`/`web_fetch` are API/tool names, not user language. Sibling chips use human verbs ("Reading page…", "Reading N of M…"). No UI-SPEC to bless this shape; against plain-language convention it fails.
- **[WARNING] Full raw URL shown, including query strings/tokens.** `TOOL_WEB_FETCH` branch passes the complete URL (`LocalToolLoop.kt:116`). URLs can contain tracking params, session tokens, or be hundreds of chars. Show host + short path at most.
- **[WARNING] Empty-arg fallback is a bare "…"** (`"…"` when query/url blank, `LocalToolLoop.kt:113,117`). Renders as "Using web_search: …" — acceptable, but combined with fix #1 should become "Searching…".
- **Positive:** English copy throughout the new surface ("Using …", "Running tool: … Tap Stop to cancel." contentDescription `ChatScreen.kt:333-334`), consistent with the fetch chip's English strings; model-facing error strings (`toolFailureMessage`, `OFFLINE_STRING`, `CAP_REACHED_STRING`) are concise English per the phase contract. Accessibility label present and correct.
- **Note:** app copy is mixed-language overall ("Pensando…", "Reintentar" elsewhere vs English chips) — pre-existing, out of scope; this phase follows its immediate sibling (fetch chip), which is the right local call.

### Pillar 2: Visuals (3/4) — WARNING

- **Positive:** Pixel-pattern reuse is exact — `CircularProgressIndicator` 16.dp / 2.dp stroke in `primary`, 8.dp gap, 14.sp `onSurface` text, `16.dp` horizontal padding (`ChatScreen.kt:327-351` vs fetch chip `:299-321`). Clear focal point (spinner + single line), thinking row correctly yields (`showThinkingRow` gated on `toolCallActive == null`, `:237-242`), so exactly one trailing/status row at a time in the transcript slot.
- **[WARNING] Unbounded text breaks the one-row visual contract.** No `maxLines`/`ellipsis` (see fix #2). A 200-char URL turns the "single transient row" into a paragraph.
- **[WARNING, minor] Two bottom-bar slots can stack.** Tool row and `isFetchingWeb` chip are independent `if`s in the same `Column` (`:285`, `:327`). VM skips Tavily pre-search when armed (so the common case can't double-stack), but pasted-URL prefetch is explicitly unchanged — a pasted-URL fetch overlapping a tool call shows two spinners stacked. Recommend `else`-chain or a combined condition; low frequency, hence minor.
- Icon-only buttons: none added by this phase. No new screens, no hierarchy changes — nothing else to judge.

### Pillar 3: Color (4/4)

- New UI uses only `MaterialTheme.colorScheme.primary` (spinner) and `onSurface` (text). Zero hardcoded hex/rgb in phase-56 UI files (verified by inspection of `ChatScreen.kt:322-351`; no `Color(` / `#[0-9a-f]` literals added). 60/30/10 distribution untouched — the row inherits the existing surface. No finding beyond: keep it that way.

### Pillar 4: Typography (3/4) — WARNING

- **[WARNING, minor] Hardcoded `fontSize = 14.sp`** (`ChatScreen.kt:346`, mirroring `:315`). Matches the sibling chip so visually consistent, but both bypass `MaterialTheme.typography` (e.g. `bodyMedium`); a future type-scale change misses these rows. Fix: `style = MaterialTheme.typography.bodyMedium` (drop the literal) — apply to both chips together.
- Size/weight inventory for the new surface: exactly one size (14.sp), one weight (default Regular) — no proliferation. The deduction is for the hardcoded literal + missing ellipsis handling, not variety.

### Pillar 5: Spacing (4/4)

- `padding(horizontal = 16.dp)`, `Spacer(width = 8.dp)`, `Spacer(height = 8.dp)`, spinner `size(16.dp)` — identical to the fetch chip, all on the 4dp scale. No arbitrary values (`[.*px]`/rem patterns N/A — Compose dp). No finding.

### Pillar 6: Experience Design (3/4) — WARNING

- **Positive — lifecycle verified complete in code:** `toolCallActive` set only on non-null `ToolStatus`, cleared on null/`ToolCompleted`/Done/Error/silent-turn/exception/Stop/new-send (`ChatViewModel.kt:754-755,802,837,849,865,1043,346`). Stop path reuses the existing single-cancel path (`generationJob.cancel()` + singleton `fetcher.cancel()`); in-flight tool calls die via `ensureActive` + CE rethrow (`LiteRTLmProvider.kt:388/416`). Rows never touch `ChatMessage`/Room/transcript (grep gate: no `toolCallActive` writes outside UI state; VM test pins USER+ASSISTANT-only transcript). Disappearance-after-turn and no-persistence contract holds in code; confirmed on device per checkpoint.
- **[WARNING] Failure invisibility** — see fix #3. Loading state (spinner) ✓, Stop ✓, empty state N/A (transient row), error state for the *user* ✗ (errors go to the model only).
- **[Minor] No step-count progress.** A 5-call loop can spin through several "Using …" rows with no sense of boundedness ("step 2 of 5"). `callsRemaining()` exists as pure logic (`LocalToolLoop.kt:123`) but is never surfaced. Recommend appending nothing now (keep the row stable to avoid flicker); optional enhancement: `"Searching… (2/5)"`. Advisory only.
- **[Minor] Rapid sequential rows may flicker** (clear in `finally` + set on next start, `LiteRTLmProvider.kt:434-438`). Between back-to-back tool calls the row unmounts/remounts. No debounce/hold-last-value. Acceptable for v1; flag for polish if device feedback mentions flashing.

---

## Files Audited

- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` (`:237-242` thinking yield, `:285-351` fetch chip + tool row)
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` (`:346`, `:754-755`, `:802`, `:837`, `:849`, `:865`, `:1043` lifecycle)
- `app/src/main/java/com/warped/ui/chat/ChatUiState.kt` (`:71-76` `toolCallActive` + shim)
- `app/src/main/java/com/warped/data/agentic/LocalToolLoop.kt` (`:54` cap, `:96-100` arming, `:109-120` `statusDisplay`)
- `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt` (`:375-443` loop driver, `:455-508` executors)
- Phase artifacts: `56-CONTEXT.md`, `56-01-SUMMARY.md`, `56-02-SUMMARY.md` (+ device checkpoint), `56-VERIFICATION.md`

## Registry Safety

Skipped — no `components.json` (native Android, no shadcn). Not applicable.
