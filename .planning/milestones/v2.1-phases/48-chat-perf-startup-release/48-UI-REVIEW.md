# Phase 48 — UI Review

**Audited:** 2026-09-28
**Baseline:** `.planning/phases/48-chat-perf-startup-release/48-UI-SPEC.md` (contract: Jump-to-latest pill, stick rules, input stability, must-not-change list)
**Screenshots:** not captured (Android/Compose project — no web dev server; code-only audit against contract with file:line evidence)
**Scope:** Phase 48 frontend implementation (48-01 PERF-14/PERF-15; 48-02 startup/splash; input-bar and bubble rendering pinned by must-not-change list)

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 4/4 | All §8 exact strings verified byte-for-byte, including pill label + a11y |
| 2. Visuals | 4/4 | Pill placement/appearance/z-order/fade match §2; list metrics + fades match §6 |
| 3. Color | 3/4 | All locked tokens honored; one off-token gray in empty state (pre-existing) |
| 4. Typography | 4/4 | Only declared styles/weights in touched code; pill uses labelSmall + Medium 500 |
| 5. Spacing | 4/4 | List, pill, touch-target, and fade metrics all match declared scale |
| 6. Experience Design | 3/4 | Stick/latch/splash/input-stability logic correct in code; on-device visual bars unverified |

**Overall: 22/24**

---

## Top 3 Priority Fixes

1. **[WARNING] On-device streaming quality bars still unverified** — §3's observable bars (no judder at bottom, pixel-stable reading while scrolled up, long-press copy mid-stream, IME/focus survival) cannot be proven by unit tests; the phase's own summary leaves hardware verification open — run the 100+ message + code-block + streaming fling/scroll session on a real device before shipping.
2. **[WARNING] Empty-state message uses off-token gray `0xFF9CA3AF`** — contract §6 specifies `titleMedium` muted (`0xFF545450`); `ChatScreen.kt:325` renders `Color(0xFF9CA3AF)` — either change to `0xFF545450` or amend the spec; pre-existing, not introduced by Phase 48.
3. **[Minor] Pill ripple bleeds outside the pill visual** — `clickable` sits on the 48dp touch-target `Box` (`ChatScreen.kt:572-576`), so the default ripple fills the square target, not the 36dp pill — confine the ripple to the pill shape (custom `indication`/`interactionSource` on the inner `Surface` or bounded ripple) so taps don't flash a square halo.

---

## Detailed Findings

### Pillar 1: Copywriting (4/4)

Contract §8 exact-string deck verified against implementation:

- Pill label `Latest` — `ChatScreen.kt:601` (`Text("Latest", …)`). Exact. ✅
- Pill a11y `Jump to latest message` — `ChatScreen.kt:578` (`contentDescription = "Jump to latest message"`). Exact. ✅
- `Using {display}…` — `ToolCopy.kt:14` (`TOOL_STATUS_TEMPLATE = "Using {display}…"`, rendered via `formatToolStatus`, `ChatScreen.kt:363`). ✅
- `{Display} failed: {reason}` — `MessageBubble.kt:291` via `formatToolError`. ✅
- `Used {Display}` transcript header — `ToolCopy.kt:16` + `MessageBubble.kt:226` via `formatToolTranscriptHeader`. ✅
- No-support notice `This model doesn't support tools — answering directly.` — `ToolCopy.kt:17` (`NO_TOOL_SUPPORT_NOTICE`), rendered `MessageBubble.kt:307`. ✅
- No count badge, no counter, no close affordance on the pill — one pill, one action per §2. ✅
- Empty/error/snackbar strings use `stringResource` or pre-existing literals; Phase 48 added no user-facing copy beyond the pill. ✅

No generic labels, no paraphrase, no new copy surface. No findings beyond full compliance.

### Pillar 2: Visuals (4/4)

**Jump-to-latest pill (§2) — the only new visible element:**

- Placement: inside message-list `Box`, `align(BottomEnd)`, `padding(end = 16.dp, bottom = 8.dp)` — `ChatScreen.kt:448-450`. ✅
- Z-order: `JumpToLatestPillOverlay` is declared after both fade overlays (`ChatScreen.kt:439-451` vs fades at `:407-436`), so it renders above them, never dimmed. ✅
- Appearance: `RoundedCornerShape(50)`, `height(36.dp)`, `padding(horizontal = 16.dp)`, `shadowElevation = 6.dp` (`ChatScreen.kt:583-592`); coral fill `0xFFD97757`, content `0xFF1C1C1C` (`:584-585`). ✅
- Content: 16dp `KeyboardArrowDown` (`:594-598`) + 8dp gap (`:599`) + `Latest`, single line (`:600-605`). ✅
- Touch target: 36dp visual centered in 48dp minimum (`sizeIn(minWidth = 48.dp, minHeight = 48.dp)`, `contentAlignment = Alignment.Center`, `ChatScreen.kt:571-574`). ✅
- Fade: `tween(150)` in + out, no slide/scale (`ChatScreen.kt:552-554`). ✅
- Hidden on empty state: pill lives inside the `else` branch of `isEmpty` (`ChatScreen.kt:330-452`), and `showPill` requires `!isEmpty` (`:235`). ✅
- Must-not-change visuals (§6): list `spacedBy(10.dp)` + `PaddingValues(horizontal = 16.dp, vertical = 8.dp)` (`ChatScreen.kt:339-340`); 24dp top/bottom fades with correct gradient direction (`:407-436`); user bubble `0xFF121212`/12dp corner/max-340/padding 12×10 (`MessageBubble.kt:88-97`); assistant transparent/0dp horizontal (`:95`); Thinking header + 16dp chevron + 4dp padding + 16dp indent + italic muted body + 72dp streaming cap (`MessageBubble.kt:99-157`); tool status 14dp spinner/2dp stroke/10dp gap/bodysmall muted (`ChatScreen.kt:378-388`); tool error 16dp `ErrorOutline` red/8dp gap/maxLines 2 (`MessageBubble.kt:283-296`); image `heightIn(max = 200.dp)` (`:355`); empty state 128dp logo + 24dp gap + `titleMedium` (`ChatScreen.kt:317-327`). All byte-identical to contract. ✅

Minor (non-scoring): pill ripple shape (see Top Fix 3).

### Pillar 3: Color (3/4)

- Locked tokens honored everywhere Phase 48 touched: background `0xFF1F1F1E` (fades `ChatScreen.kt:416,431`; splash `colors.xml:4` `window_background #FF1F1F1E`); input surface `0xFF2B2B29` (`ChatInputBar.kt:59`); accent coral `0xFFD97757` pill-only (`ChatScreen.kt:584`); on-accent `0xFF1C1C1C` (`:585`); muted `0xFF545450` status/thinking/stats/transcript (`ChatScreen.kt:381,387`, `MessageBubble.kt:109,145,196,228,254,309`); error `0xFFEF4444` tool-error-row-only (`MessageBubble.kt:286,293`). ✅
- Dark-theme hard rule (§7): pill is the only new colored surface and uses only locked tokens — no white fills, no new colors, no new text styles. ✅
- **[WARNING] Empty-state message color `0xFF9CA3AF` (`ChatScreen.kt:325`) is not a locked token.** Contract §6 describes the empty state as "`titleMedium` muted `empty_state_message`", i.e. `0xFF545450`. The rendered gray is visibly lighter than the muted token used by every other secondary row. **Pre-existing** (not introduced by Phase 48 — the empty-state branch is untouched by the migration), so advisory: either align to `0xFF545450` or record `0xFF9CA3AF` as the intended empty-state token in the spec. One-point deduction because a scored pillar must reflect the contract deviation, not because Phase 48 regressed anything.

No hardcoded-color sprawl: all hexes resolve to contract tokens except the one flagged line.

### Pillar 4: Typography (4/4)

- Contract allows: `bodySmall` status/pill-label rows, `labelSmall` chips/stats, `bodyMedium` message + Thinking header; Regular 400 body, Medium 500 pill/chip labels only.
- Pill: `labelSmall` + `FontWeight.Medium` (`ChatScreen.kt:602-603`). ✅
- Tool status: `bodySmall` (`ChatScreen.kt:386`); tool error: `bodySmall` (`MessageBubble.kt:292`); no-support notice: `bodySmall` italic (`:308`); stats: `labelSmall` italic (`:195`); Thinking header: `bodyMedium` (`:108`); transcript header: `bodyMedium` (`:227`); user text: `bodyMedium` (`:182`). ✅
- No new text styles introduced; `FontWeight.Medium` appears only on the pill label in touched code (chip labels per 47-UI-SPEC, untouched). ✅
- Only two weights in scope (Regular default, Medium pill) — within the ≤2-weight bar. ✅

### Pillar 5: Spacing (4/4)

- List `spacedBy(10.dp)`, padding `horizontal 16.dp / vertical 8.dp` (`ChatScreen.kt:339-340`) — exact. ✅
- Pill corner `50` full-round (outer clip `ChatScreen.kt:575` + surface shape `:583`); pill `bottom 8.dp / end 16.dp` (`:450`); 48dp minimum touch target (`:574`). ✅
- Input bar `extraLarge` shape (`ChatInputBar.kt:60`), 10dp inner padding (`:66`), fixed 40dp send/stop boxes (`:160,190,198,207`). ✅
- Fade overlays 24dp (`ChatScreen.kt:411,427`); all values multiples of 2dp; no arbitrary `[Npx]`/`[Nrem]` hacks in touched code. ✅
- Pre-existing note (not scored): `ChatInputBar.kt:64` outer padding is `start/end 10.dp, top 5.dp, bottom 0.dp` (+ `navigationBarsPadding`) while §4.5 describes "10.dp outer" — doc-vs-code drift that predates Phase 48 (bar untouched this phase); amend the spec line if 5dp-top is intended.

### Pillar 6: Experience Design (3/4)

**Pill visibility rule (§2, all three must hold) — `ChatScreen.kt:229-235`:**

1. Non-empty list (`!isEmpty`, where `isEmpty` covers messages + streaming + tool + error + notice, `:229-234`) ✅
2. Scrolled up — `!isAtBottom`, with `isAtBottom` = last item visible AND within 48dp of end (`:85-93`, threshold `:84`) ✅
3. Latch `hasNewContentBelow` sets on content arrival while up, clears on reaching bottom / pill tap / send (`:207-226,250-252`) ✅
- Never at bottom, never on empty, never on conversation open (open sets `snapToBottomOnNextContent`, `:105-120`, which clears the latch and pins to bottom, `:216-219`). ✅
- Tap → `animateScrollToItem(last)` + latch clear + fade out on arrival (`:441-447`); user-initiated animation only, automatic scroll uses instant `scrollToItem` — the per-token `animateScrollTo` defect is deleted (`:219`). ✅
- A11y: `contentDescription` + `liveRegion = Polite` (`:577-580`); content unchanged while visible → at most one announcement per latch set. ✅

**Stick table (§3):** gating moves the viewport only when at bottom (`:214-223`); send snaps instantly (`:250-252` + `scrollToItem`); open/switch lands at bottom with no entry animation; rotation safe — `MainActivity` declares `configChanges="…orientation|screenSize|…"` (`AndroidManifest.xml:41`), so the Activity (and `rememberLazyListState`) survives rotation and the same message stays visible; keyed items (`key = { it.id }`, `:342`; constant `ChatListKeys` trailing keys, `:346,361,395,402`) preserve identity across reloads and flings. ✅

**Input-bar stability (§4):** `ChatInputBar.kt` untouched this phase; isolation is structural — input region collects only `inputState`, transcript only `transcriptState` (`ChatScreen.kt:77-79`); send/stop are both fixed 40dp boxes (`ChatInputBar.kt:190-212`); skill row gated on stable `canSend` with `chipsEnabled = !isGenerating` (disable, no reflow, `:110-118`); `maxLines = 4`, gating, placeholder all preserved. ✅

**Splash (§5):** `setKeepOnScreenCondition { false }` (`MainActivity.kt:25`); exactly `duration = 200L` + `AccelerateInterpolator` + `remove()` on end (`:27-38`); dark `window_background` — no white flash (`colors.xml:4`, `themes.xml:4-9`); `setContent` composed synchronously before fade (`MainActivity.kt:39-43`). Exact match. ✅

**[WARNING] Deduction reason — unverifiable-by-code quality bars:** §3's streaming-at-bottom bar (no judder/rubber-band/blank-gap/caret flicker from per-token `scrollToItem`) and streaming-while-scrolled-up bar (pixel-stable reading, mid-stream long-press copy, mid-stream expansion without viewport movement), plus §4's IME/focus guarantees, are behavioral and need a real device. The phase's own summary keeps "manual on-hardware verification open" (Layout Inspector recomposition check, 100+ message fling). Unit tests prove state-flow isolation and key stability, not the visual bars. Score returns to 4/4 once that device session is recorded; until then this is the honest cap.

---

## Registry Safety

Skipped — Android project, no `components.json`, no shadcn/third-party registries in scope.

---

## Files Audited

- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` (1-725 — LazyColumn, latch, pill, fades, empty state, dialogs)
- `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt` (1-364 — bubbles, Thinking, transcript/error/notice rows, image stack)
- `app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt` (1-216 — bar geometry, gating, chips row; verified untouched)
- `app/src/main/java/com/warped/ui/chat/components/ToolCopy.kt` (copy templates — grep-verified)
- `app/src/main/java/com/warped/MainActivity.kt` (1-56 — splash contract)
- `app/src/main/AndroidManifest.xml` (`configChanges` — rotation contract)
- `app/src/main/res/values/colors.xml`, `themes.xml` (splash dark background)
- `.planning/phases/48-chat-perf-startup-release/48-UI-SPEC.md` (baseline contract)
- `.planning/phases/48-chat-perf-startup-release/48-01-SUMMARY.md`, `48-02-SUMMARY.md` (implementation claims cross-checked)
