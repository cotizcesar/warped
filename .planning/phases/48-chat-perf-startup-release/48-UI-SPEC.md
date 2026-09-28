# Phase 48: Chat Perf + Startup + Release — UI-SPEC

**Status:** draft
**Phase:** 48 - Chat Perf + Startup + Release
**Design System:** Manual (Material 3 dark theme, no shadcn — Android/Compose project)
**Sources:** 48-CONTEXT.md (locked), PROJECT.md, ChatScreen.kt (Column+verticalScroll baseline), components/MessageBubble.kt, components/ChatInputBar.kt, 47-UI-SPEC.md (tool-row contract, must survive), MainActivity.kt (splash), Color.kt/Theme.kt

> Scope: visible behavior only. No architecture, no state-class names, no
> implementation strategy. The LazyColumn migration (PERF-15) and sub-state
> split (PERF-14) are invisible by design — this contract pins what the user
> must see (and must NOT see change).

---

## 1. Design Tokens (locked — do not re-derive)

| Token | Value | Usage |
|-------|-------|-------|
| Background / Surface | `0xFF1F1F1E` | Chat list, screen background, fade-gradient solid end |
| Input bar surface | `0xFF2B2B29` | `ChatInputBar` pill fill |
| Accent (primary) | `0xFFD97757` coral (`PrimaryDark`) | Send fill, active chips, **Jump-to-latest pill fill** |
| On-accent | `0xFF1C1C1C` (`OnPrimaryDark`) | Text/icons on coral fills, incl. pill label + icon |
| Muted secondary | `0xFF545450` | Thinking header, stats, transcript rows, tool status text + spinner |
| Error | `0xFFEF4444` (`ErrorDark`) | Tool error row only |
| User bubble | `0xFF121212` | Unchanged |
| Assistant bubble | Transparent | Unchanged |

**Typography:** `bodySmall` status/pill-label rows; `labelSmall` chips/stats; `bodyMedium` message text + Thinking header. Weights: Regular 400 body, Medium 500 pill/chip labels only.
**Spacing:** list `spacedBy(10.dp)`, list padding `horizontal 16.dp / vertical 8.dp`, pill corner `50` (full round), input bar `extraLarge` (12dp effective). All multiples of 2dp; touch targets ≥ 48dp.

---

## 2. "Jump to Latest" Affordance (new — the only new visible element)

**Placement:**
- Floating pill **inside the message-list `Box`**, `align(Alignment.BottomEnd)`, `padding(end = 16.dp, bottom = 8.dp)` — floats **above the bottom fade gradient** (z-order: pill renders after/above both fade overlays so it is never dimmed).
- Sits directly above the input bar with an 8dp visual gap to the list's bottom edge; never overlaps message text (list content scrolls beneath it; pill is overlay, not a list item).
- Hidden whenever the empty state (logo) is showing — pill exists only when the transcript list exists.

**Appearance:**
- Full-round pill: `RoundedCornerShape(50)`, height 36dp, horizontal padding 16dp, elevation 6dp.
- Fill `0xFFD97757` coral; content `0xFF1C1C1C`.
- Content: 16dp down-arrow icon (`KeyboardArrowDown`) + 8dp gap + text `"Latest"` in `labelSmall`, weight Medium 500, single line. Exact visible string: arrow + `Latest`.
- No count badge, no round counter, no close affordance — one pill, one action.

**Visibility rule (all three must hold):**
1. Transcript list is non-empty (not the logo empty state), AND
2. User is scrolled up — defined as: more than ~48dp (or more than one item) of content below the current viewport bottom, AND
3. New content arrived **after** the user scrolled up — any of: a completed message appended, streaming tokens appended to the trailing bubble, a tool status row appeared/updated, a tool error row or transcript row was added.

- A `hasNewContentBelow` latch sets on (3)-while-(2) and clears the moment the user reaches the bottom (tap pill, manual scroll to bottom, or send).
- Pill appears/disappears with a ≤150ms fade (no slide, no scale pop — must not visually yank the list).
- Pill NEVER shows while already at bottom, even mid-stream. Pill NEVER shows on conversation open (open lands at bottom, §3).

**Interaction:**
- Tap → smooth scroll to the last item (bottom), latch clears, pill fades out on arrival. Single tap, no long-press action, no swipe-to-dismiss.
- Pill tap target is the full 36dp visual centered in a 48dp minimum touch area (extend clickable padding outward, do not shrink the pill).

**Accessibility:** `contentDescription = "Jump to latest message"`. Focusable only while visible. Announcement on appearance: polite, at most once per latch set (no re-announcement per streaming token while pill is already showing).

---

## 3. Scroll-Stick Behavior

**Core rule:** the viewport moves on new content **only when already at the bottom**. "At bottom" = last item visible and viewport offset within 48dp of the end. Otherwise the viewport does not move a single pixel.

| Situation | At bottom | Scrolled up |
|-----------|-----------|-------------|
| Streaming tokens arrive | Stick to bottom, follow smoothly, no judder | No movement; "Jump to latest" latch sets (§2) |
| Completed message appended | Stick; new bubble fully visible | No movement; latch sets |
| Tool status row appears/updates (`"Using …"`) | Stick; row visible | No movement; latch sets on first appearance only (text swaps between rounds cause no announcement, no movement) |
| Tool error row / no-support notice added | Stick | No movement; latch sets |
| Long code block streams/grows inside trailing bubble | Bubble grows downward in place; viewport follows only if at bottom | No movement; code block growth never pulls the viewport |
| Collapsed 200+ line code block / Thinking / transcript row expanded by tap | Content expands below tap point; viewport may shift by exactly the expansion amount only if expansion pushes past the bottom edge | Viewport stays anchored to the same visible message (no jump to top or bottom) |
| User sends a message | Snap to bottom immediately (no animation delay) | N/A (send implies intent to see reply) |
| Conversation opened / switched | Land at bottom, no entry animation | N/A |
| Rotation / config change | Same visible message stays visible (no jump to top, no jump to bottom) | Same |
| History load (Room pages in) | Bottom-anchored; never jumps to top | N/A |

**Streaming-at-bottom quality bar (observable):** no visible judder, no rubber-banding, no momentary blank gap at the list end, no caret (`▌`) flicker caused by scrolling. `animateScrollTo(maxValue)`-on-every-token (current behavior) is the defect being replaced — the replacement must look like the list is pinned, not re-animated.

**Streaming-while-scrolled-up quality bar (observable):** the message the user is reading stays pixel-stable while tokens stream below; long-press copy on the visible bubble works mid-stream; expanding a Thinking/transcript row mid-stream does not move the viewport.

---

## 4. Input-Bar Stability Contract (typing while streaming)

Observable guarantees — verifiable without reading code:

1. **No focus loss:** streaming token arrivals, tool status swaps, error-row insertions, and stream start/stop transitions never move focus, never dismiss the keyboard, never reset cursor position. If the field has focus when generation starts, the IME state after generation ends is identical (keyboard still up if it was up).
2. **No keystroke interference:** typing never causes the message list to flicker, scroll, recompose-visibly, or jump. Draft text, cursor, and selection survive any number of streaming updates. No dropped or reordered characters attributable to list updates.
3. **Enabled-state transitions are silent:** the send/stop icon swap (both fixed 40dp boxes) and any disabled↔enabled transition on the text field change no size, no position, and no color of the bar itself. Typing state (text + cursor) is preserved across the transition.
4. **Bar geometry is constant:** the input bar keeps identical height and corner shape across idle / streaming / error / no-model states. The skill-chips row (47-UI-SPEC §2) does not appear, disappear, or reflow mid-stream; the image-preview row appearing (attach while idle) pushes content upward only, never resizes the text field.
5. **List/input independence (both directions):** list growth (status rows, snackbars above the bar) never resizes or shifts the input bar; the bar stays pinned above the navigation bar with its existing `10.dp` outer / `10.dp` inner padding.

What this contract does NOT change: send gating (`canSend`, content-required), Enter-to-send, `maxLines = 4`, placeholder, Think toggle, skill chips, image/audio attachments — all per existing behavior and 47-UI-SPEC §2.

---

## 5. Splash Cross-Fade (≤200ms visual spec)

- Splash background: `window_background` (dark `0xFF1F1F1E` family) — no white flash on any path, cold or warm start.
- Exit: system splash view alpha `1 → 0` over **exactly 200ms**, `AccelerateInterpolator`, starting the moment first Compose content is composed. Splash view `remove()`d on animation end — never lingers, never overlaps chat content.
- Underneath: the chat/conversation destination is already composed when the fade starts — user perceives app content fading in, not a splash sliding away (no slide, no scale, no shared-element motion in this phase).
- No artificial hold: `keepOnScreen = false` equivalent — splash never waits for model load, endpoint fetch, or conversation load. Loading states (model loading indicator, empty state) render as normal in-app UI after the fade.
- Cold start target stays ≤1s to interactive (PERF-16, measured per CONTEXT on available hardware, recorded in BENCHMARKS.md) — the fade is the last 200ms of that budget, not additive to it.

---

## 6. LazyColumn Migration: MUST-NOT-CHANGE List

The Column→LazyColumn split must be pixel-indistinguishable except for §2's pill and §3's stick behavior. Every item below renders **identically** — same composables, same modifiers, same order:

- **User bubble:** `0xFF121212` fill, `RoundedCornerShape(12.dp)`, `widthIn(max = 340.dp)`, end-aligned, `12.dp` horizontal / `10.dp` vertical inner padding, `bodyMedium` white text. Long-press copies content + `"Copied!"` toast.
- **Assistant bubble:** transparent, start-aligned, `0.dp` horizontal padding, `MarkdownText` + streaming `▌` caret, syntax-highlighted code blocks (theme, header bar, copy button, line numbers, 200-line collapse per v1.6 contract).
- **List metrics:** `spacedBy(10.dp)`, `padding(horizontal = 16.dp, vertical = 8.dp)` — measure after migration; any deviation is a defect.
- **Thinking panel:** `bodyMedium` muted `0xFF545450` `"Thinking"` header + 16dp chevron (`KeyboardArrowRight` collapsed / `KeyboardArrowDown` expanded), 4dp vertical padding, `expandVertically`/`shrinkVertically`, body indented `start = 16.dp`, italic muted `MarkdownText`, 4dp spacer below, streaming cap `heightIn(max = 72.dp)` with inner scroll.
- **Tool status row (47-UI-SPEC §3):** 14dp spinner / 2dp stroke muted + 10dp gap + `bodySmall` muted `"Using {display}…"`, single in-place instance across rounds, appended after partial-text bubble, never overlaying.
- **Tool error row (47-UI-SPEC §4):** 16dp `ErrorOutline` `0xFFEF4444` + 8dp gap + `bodySmall` red `"{Display} failed: {reason}"`, `maxLines = 2` ellipsis, directly below its bubble; fallback answer in the bubble.
- **Transcript rows (47-UI-SPEC §5):** collapsed `"Used {Display}"` rows byte-identical in styling to Thinking (same header, chevron, indent, italic muted body, `AnimatedVisibility`), inline at the position the tool ran, collapsed by default, independently expandable. **Phase 47 tool-status/transcript rows survive as keyed items with stable identity** — history reload shows the same rows in the same order; expand/collapse state is per-row and never leaks between rows during fast scroll.
- **No-support notice (47-UI-SPEC §6):** single `bodySmall` italic muted line, exact string `"This model doesn't support tools — answering directly."`, once per turn, inline.
- **Stats line:** `labelSmall` italic muted below assistant bubbles.
- **Image stacks:** same thumbnails (`heightIn(max = 200.dp)`), same tap-to-enlarge dialog.
- **Fade gradients:** top + bottom 24dp overlays, `0xFF1F1F1E → Transparent` / `Transparent → 0xFF1F1F1E`, above list content, below the §2 pill.
- **Empty state:** 128dp logo + 24dp gap + `titleMedium` muted `empty_state_message`, centered. Unchanged.
- **Inline model selector bar, model loading indicator, snackbars, model-switch dialog, memory-warning dialog, model picker sheet:** untouched — not in this phase's visual scope.
- **Item identity (observable):** fast fling through 100+ messages never shows one message's text in another's bubble, never duplicates or drops a row, never flashes blank items. Rotation never duplicates the streaming bubble.

---

## 7. Density, States & Dark-Theme Rules

| State | Rendering |
|-------|-----------|
| Idle at bottom | Normal list; pill hidden |
| Idle scrolled up, no new content | Normal list; pill hidden |
| Scrolled up + new content | Pill visible (§2); list frozen (§3) |
| Streaming at bottom | Pinned follow; pill hidden; caret live |
| Streaming scrolled up | List frozen; pill visible; stream continues below |
| Empty (no messages) | Logo empty state; pill hidden; no stick logic |
| Splash (≤200ms) | Dark bg, alpha fade only (§5) |

**Dark-theme hard rules:** pill is the only new colored surface and uses only locked tokens (coral fill, on-accent content). No white fills, no new colors, no new text styles. Light-theme behavior: auto-map via existing `Theme.kt` (coral identical; muted → light variant; error → light variant) — hardcoded hexes are dark-theme values only.

---

## 8. Copy Deck (exact strings — no paraphrase)

| Element | String |
|---------|--------|
| Pill label | `Latest` |
| Pill (a11y) | `Jump to latest message` |
| Live status | `Using {display}…` (per 47-UI-SPEC §8) |
| Tool error | `{Display} failed: {reason}` |
| Transcript header | `Used {Display}` |
| No-support notice | `This model doesn't support tools — answering directly.` |
| Chip labels | `Calculator` · `Current time` · `JSON format` |

---

## 9. Out of Scope (explicitly NOT in this contract)

- Unread-count badges, timestamps on the pill, swipe-to-dismiss, pill on empty state.
- Scroll-to-specific-message, search-in-conversation, "new messages" dividers.
- Enabling input text entry mid-generation (gating unchanged); voice/image input changes.
- Splash branding changes (logo, animation choreography beyond the 200ms fade).
- Light-theme pixel audit; release-build visual differences (release must look identical to debug).
- Logcat/PII/R8/dependency/network-security work — no visible surface by design.

---

*Contract Note: scroll-stick rule, keyed trailing streaming item, tool rows as keyed items, ≤200ms splash, and sub-state visual silence are pre-populated from 48-CONTEXT.md locked decisions. Pill placement/appearance/visibility, stick thresholds, input-bar guarantees, splash visual spec, and the must-not-change inventory are researcher defaults aligned to ChatScreen.kt, MessageBubble.kt, ChatInputBar.kt, MainActivity.kt, Color.kt, and 47-UI-SPEC.*
