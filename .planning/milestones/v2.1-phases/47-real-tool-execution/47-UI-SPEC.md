# Phase 47: Real Tool Execution — UI-SPEC

**Status:** draft
**Phase:** 47 - Real Tool Execution
**Design System:** Manual (Material 3 dark theme, no shadcn — Android/Compose project)
**Sources:** 47-CONTEXT.md (locked), PROJECT.md, ChatScreen.kt, ChatInputBar.kt, MessageBubble.kt, Color.kt/Theme.kt

---

## 1. Design Tokens (locked — do not re-derive)

| Token | Value | Usage |
|-------|-------|-------|
| Background / Surface | `0xFF1F1F1E` (`BackgroundDark`/`SurfaceDark`) | Chat column, screen background |
| Input bar surface | `0xFF2B2B29` | `ChatInputBar` Surface fill; SkillChipsRow container inherits this bar |
| Accent (primary) | `0xFFD97757` coral (`PrimaryDark`) | Send button fill, active/ON chip fill, spinner accent where specified |
| On-accent | `0xFF1C1C1C` (`OnPrimaryDark`) | Text/icons on coral fills |
| Muted secondary text | `0xFF545450` | Tool status row text, Thinking header, stats, transcript rows |
| Spinner (tool status) | `0xFF545450` | Existing `toolCallActive` row spinner (KEEP — do not switch to accent) |
| Error | `0xFFEF4444` (`ErrorDark`) | Tool error row icon/text accents |
| User bubble | `0xFF121212` | Unchanged |
| Assistant bubble | Transparent | Tool rows live outside bubbles, left-aligned in transcript column |

**Typography:** `bodySmall` for status/transcript/error rows; `labelSmall` for chips and stats line; `bodyMedium` for fallback answer text. Weights: Regular 400 body, Medium 500 chip labels only.
**Spacing:** 8dp chip gaps, 10dp transcript row spacing (matches message column `spacedBy(10.dp)`), 8dp vertical padding on status rows, 16dp pill corner radius on chips (match Think toggle precedent), 12dp on input bar (existing `extraLarge`).

---

## 2. SkillChipsRow (new composable, above chat input)

**Location:** Inside `ChatInputBar`'s existing `Column`, ABOVE the `OutlinedTextField` (Row 0). Never a separate bar — inherits the `0xFF2B2B29` pill surface and `10.dp` inner padding.

**Content (locked per CONTEXT §Skills Surface):** Exactly **3 tool chips** — Calculator, CurrentTime, JsonFormatter. Summarize is a `PromptTemplate` persona, NOT a function — it gets **no chip** in this phase.

**Layout:**
- Single-line horizontal `LazyRow`, `Arrangement.spacedBy(8.dp)`, no wrap.
- Scrollable if overflow (small screens / large font); `contentPadding` 0 — bar padding suffices.
- Height 28dp per chip (match existing Think toggle `height(28.dp)`).
- Rendered always (empty chat and active chat); hidden only when no model selected (`canSend == false` → hide row to avoid implying availability).

**Chip visuals:**
- `FilterChip`-style custom `Surface`: shape `RoundedCornerShape(16.dp)` (pill), matching input-bar pill language.
- ON state (default): `containerColor = MaterialTheme.colorScheme.primary` (`0xFFD97757`), `contentColor = 0xFF1C1C1C`, label weight Medium 500, checkmark icon NOT shown (label + leading dot only — keep compact).
- OFF state: `containerColor = Color.Transparent` with 1dp border `Color.White.copy(alpha = 0.25f)`, `contentColor = Color.White.copy(alpha = 0.6f)`, weight Regular 400.
- Labels (exact strings): `Calculator`, `Current time`, `JSON format`. Short user-facing names; internal tool names (`calculator`, `current_time`, `json_formatter`) never shown.
- Leading icon: none (text-only chips, 12sp `labelSmall`). Rationale: 28dp height + 3 chips of icons would crowd; text-only matches Think toggle precedent.

**Behavior:**
- Tap toggles enable/disable → writes `SkillPreferences` DataStore via ViewModel (`setSkillEnabled(id, Boolean)`); state hoisted as `Map<String, Boolean>` param into `ChatInputBar` (same pattern as `reasoningEnabled`/`onToggleReasoning`).
- Default all-ON (DataStore default `true` per key; first launch shows all ON).
- Toggling mid-stream is ignored while `isGenerating` (chips `enabled = !isGenerating`); taps during generation are no-ops, no ripple.
- At least zero enabled is legal — zero enabled = no `tools[]` sent, plain chat, no notice shown.
- State survives rotation (ViewModel-backed, not `remember`).

**Accessibility:** Each chip `contentDescription = "{Label} skill, {enabled|disabled}, tap to toggle"`. Minimum touch target 48dp (chip visual 28dp centered in 48dp clickable — use `FilterChip` default sizing or explicit `sizeIn(minHeight = 48.dp)` wrapper with centered 28dp visual; do NOT shrink touch target to 28dp).

---

## 3. Tool Status Row (live execution — extends existing `toolCallActive` row)

**Existing behavior (KEEP):** `ChatScreen` lines 287–306 — when `isStreaming && streamingContent.isEmpty() && streamingReasoning.isEmpty() && toolCallActive != null`, a left-aligned `Row` shows 14dp `CircularProgressIndicator(strokeWidth 2dp, 0xFF545450)` + 10dp gap + `bodySmall` `0xFF545450` text `"Using {tool}..."`.

**Contract additions for Phase 47:**

- **Text format (exact):** `"Using {display}…"` (ellipsis char `…`, not `...`). Display-name map: `calculator → "calculator"`, `current_time → "current time"`, `json_formatter → "JSON formatter"`. Lowercase sentence style, underscore→space. Source: `toolCallActive` string already set by ViewModel; mapping lives in one `toolDisplayName()` function.
- **Placement:** In-transcript (current location, above input, inside scroll column) — NOT a floating overlay. Keeps scroll/anchor behavior identical to Thinking row.
- **While streaming + tool running simultaneously:** If partial `streamingContent` exists AND a new tool round starts, BOTH render in order: completed text bubble first, then status row below it (never replace text with spinner). Status row is appended after the streaming bubble, never overlaid.
- **Multi-round (up to ~5):** Status text updates in place (`toolCallActive` value swap); single row instance, no stacking. No round counter shown (`"Using calculator… (2/5)"` is OUT — keep clean).
- **Cancellation:** Stop button (existing) cancels between rounds; status row disappears immediately on cancel, partial text remains.
- **Accessibility:** `contentDescription = "Tool running: {display}"`; spinner marked non-actionable (no focus). Row announced once on appearance, silent on text swap (avoid re-announcement spam across rounds — use `liveRegion = Polite` on container, not per-round).

---

## 4. Tool Error Row

**Trigger:** Tool returns error-mapped result (never throws per CONTEXT trust boundary).

**Visual:** Left-aligned row in transcript, directly below the assistant bubble it belongs to:
- Row: 16dp error icon (`Icons.Filled.ErrorOutline`, tint `0xFFEF4444`) + 8dp gap + `bodySmall` text `"{Display} failed: {short reason}"` in `0xFFEF4444`.
- Exact format: `"Calculator failed: invalid expression"` — display name capitalized first letter, reason is the sanitized short error (no stack traces, no raw args echo).
- Below the error row, the **fallback answer renders as normal assistant text** in the same bubble (plain-text, MarkdownText path). Error row and fallback are siblings; fallback is never empty — ViewModel guarantees non-blank content ("never hang/empty bubble").
- No retry button in this phase (deferred). No dialog, no Snackbar — inline only.

**Long reasons:** Ellipsize at 2 lines (`maxLines = 2, overflow = Ellipsis`); full reason available via long-press copy (bubble already copies content — error text included in copied content).

**Accessibility:** `contentDescription = "Tool error: {full reason}"` (full text, not truncated).

---

## 5. Transcript Rows (persisted past tool use, role:tool)

**What renders:** Minimal collapsed rows for completed tool calls persisted via Room (role:tool messages), mirroring the **Thinking panel pattern** in `MessageBubble` (lines 88–146).

**Visual (mirror Thinking exactly):**
- Header row: `bodyMedium` `0xFF545450` text `"Used {Display}"` (per casing rule, past tense — distinguishes from live `"Using …"`) + chevron (`KeyboardArrowRight` collapsed / `KeyboardArrowDown` expanded), `clickable` toggle, 4dp vertical padding.
- Collapsed by default (`showToolResult = false` via `remember`).
- Expanded: `AnimatedVisibility(expandVertically/shrinkVertically)`, result text in `Surface Transparent`, `padding(start = 16.dp)`, `SelectionContainer` + `MarkdownText`, `baseColor 0xFF545450`, italic — byte-identical styling to Thinking body.
- Result text is the **summarized** result (ViewModel truncates to ~200 chars + `"…"`); never raw full JSON dumps.
- 4dp spacer below (matches Thinking `Spacer(4.dp)`).

**Placement:** Inline in transcript at the position the tool ran (between assistant text segments), left-aligned, `fillMaxWidth`, outside any bubble — same as Thinking block.

**Streaming vs history:** During live streaming, tool results do NOT render as transcript rows (only the status row shows); transcript rows appear on history load / after turn completes.

**Accessibility:** Header `contentDescription = "Tool result from {display}, {collapsed|expanded}, tap to toggle"`; same toggle semantics as Thinking panel.

---

## 6. No-Tool-Support Notice

**Trigger:** Model lacks tool support (allowlist `supportsFunctionCalling == false`, incl. Qwen3/Gemma fallback) AND the model attempted or the user enabled skills with an unsupported model.

**Visual:** Inline muted notice, NOT a Snackbar/banner/dialog:
- Single `bodySmall` italic line, `0xFF545450`, left-aligned in transcript where the status row would have been: `"This model doesn't support tools — answering directly."` (exact string).
- Shows once per turn, auto-replaced by the normal answer bubble content streaming in below it. Never blocks input; never requires dismissal.
- No icon, no error color — this is informational, not a failure.

**Do NOT show when:** zero skills enabled (plain chat needs no notice); tool-capable model (normal flow).

---

## 7. Density, States & Dark-Theme Rules

| State | Rendering |
|-------|-----------|
| Idle, skills on | Chips row visible (all ON coral); no status/transcript rows |
| Streaming text only | Normal streaming bubble + `▌` caret (unchanged) |
| Streaming + tool round | Text bubble (partial) + status row below; single spinner |
| Tool error | Error row + fallback text in same bubble; stream continues |
| History with tools | Collapsed `"Used …"` rows inline; expand per row independently |
| No model selected | Chips hidden; input disabled (existing `canSend` gate) |
| Cancel mid-tool | Status row removed; partial text kept; no error row |

**Dark-theme hard rules:** All new surfaces inherit `0xFF2B2B29` (chips bar) or Transparent (rows). No white fills. No new colors beyond the table in §1. Light-theme behavior: auto-map via existing `Theme.kt` (`PrimaryLight` same coral; muted `0xFF545450` becomes `0xFF6B7280` on light; error `0xFFDC2626`) — executor uses `MaterialTheme.colorScheme` equivalents, hardcoded hexes are dark-theme values only.

**Performance:** Chips row is a pure function of `Map<String,Boolean>` — no recomposition on streaming chars (hoist outside streaming `derivedStateOf` scope). Status row recomposes only on `toolCallActive` change. Transcript rows are stateless list items with stable Room keys (PERF-06 pattern).

---

## 8. Copy Deck (exact strings — no paraphrase)

**Casing rule:** `{display}` = lowercase sentence style (`calculator`, `current time`, `JSON formatter` — acronyms keep caps); `{Display}` = first letter capitalized (`Calculator`, `Current time`, `JSON formatter`). Status/transcript-header use `{display}` except transcript header which uses `{Display}` per §5; error header uses `{Display}`.

| Element | String |
|---------|--------|
| Chip labels | `Calculator` · `Current time` · `JSON format` |
| Live status | `Using {display}…` (`calculator` / `current time` / `JSON formatter`) |
| Tool error | `{Display} failed: {reason}` |
| Transcript header | `Used {Display}` |
| No-support notice | `This model doesn't support tools — answering directly.` (em dash) |
| Chip toggle (a11y) | `{Label} skill, {enabled\|disabled}, tap to toggle` |
| Status (a11y) | `Tool running: {display}` |
| Error (a11y) | `Tool error: {full reason}` |
| Transcript toggle (a11y) | `Tool result from {display}, {collapsed\|expanded}, tap to toggle` |

## 9. Out of Scope (explicitly NOT in this contract)

- Skill management screen, add/remove skills, per-skill settings pages.
- Retry button on error rows; confirmation gate before execution (interface reserved per CONTEXT, UI deferred).
- Round counters, tool timing display, raw JSON/args inspector.
- Summarize chip (PromptTemplate persona — no UI).
- Light-theme pixel audit (token mapping only, uses existing Theme.kt).

---

*Contract Note: skill set (3 tools, Summarize excluded), all-on defaults, `toolCallActive` row reuse, error+fallback shape, role:tool transcript rows, and no-support fallback message are pre-populated from 47-CONTEXT.md locked decisions. Chip styling, exact strings, placement, and a11y labels are researcher defaults aligned to ChatInputBar/MessageBubble/Color.kt conventions.*
