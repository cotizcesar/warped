# Phase 69 — UI Review (Voice Differentiation + Gating + Transcript)

**Audited:** 2026-10-02
**Baseline:** `.planning/phases/69-voice-differentiation-gating-transcript/69-UI-SPEC.md`
**Screenshots:** not captured — native Android project, no web dev server (code-only audit of Compose sources + strings)

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 4/4 | All 12 spec copy items exact EN with full ES parity, zero hardcoded strings |
| 2. Visuals | 3/4 | Gated/disabled + coachmark hierarchy correct; active-mode signal contradicts tint-only rule |
| 3. Color | 3/4 | Gated 38%-opacity + hint + expand-tint rules honored; active container recolors break no-recolor rule |
| 4. Typography | 3/4 | Captions/hints Label 14sp correct; coachmark tooltip text has no explicit Label style |
| 5. Spacing | 4/4 | All gaps on the 4dp scale; 48dp touch floors on every new affordance |
| 6. Experience Design | 3/4 | Gate/explainer/dismissal/draft-kept flows complete; expand affordance lacks expanded-state semantics |

**Overall: 20/24**

---

## Top 3 Priority Fixes

1. **Coachmark tooltip text has no explicit text style** — `ChatInputBar.kt:419-421` renders `Text(stringResource(R.string.voice_msg_coachmark))` inside `PlainTooltip` with no `style`, so it inherits the ambient `bodyLarge` (~16sp) instead of the spec-mandated Label 14sp ("Coachmark text uses Label 14sp on the coachmark surface", UI-SPEC Typography). User impact: coachmark renders larger than designed, inconsistent with every other Phase 69 caption. Concrete fix: add `style = MaterialTheme.typography.labelLarge` to the coachmark `Text`.
2. **Transcript expand affordance announces no expanded/collapsed state to TalkBack** — `MessageBubble.kt:859-873` toggles `expanded` on a plain `TextButton` with no `semantics { expanded = … }` or `stateDescription`. User impact: blind users hear "Show more / Show less" as a bare button with no state context, unlike the Phase 49 tool-transcript row which carries `semanticsForToolResult(toolId, expanded)` (`MessageBubble.kt:622-628`). Concrete fix: mirror the Phase 49 pattern — add `.semantics { stateDescription = … }` or `expanded()` semantics to the `TextButton`, reusing the existing show-more/less strings.
3. **Active-mode signal uses container recolor, contradicting the spec's tint-only rule** — UI-SPEC Color §"Active-mode rule" states "No background recolor, no pulsing — tint is the only active-mode signal". Implemented: dictation-listening uses `containerColor = primary.copy(alpha = 0.5f)` (`ChatInputBar.kt:350`) and the (currently unreachable while recording) voice branch does the same (`ChatInputBar.kt:433`), while idle glyphs sit at `White 0.6f` rather than `onSurfaceVariant`. User impact: minor visual inconsistency with the contract; behavior predates Phase 69 (Phase 65/67 chrome) so risk is spec-drift, not regression. Concrete fix: either switch active indication to pure `primary` glyph tint per spec, or amend the UI-SPEC active-mode rule to bless the container-pill convention inherited from Phases 65–68.

---

## Detailed Findings

### Pillar 1: Copywriting (4/4)

Every spec copy item verified verbatim against `app/src/main/res/values/strings.xml`:

- Text-only gate hint `Needs audio model` (:247) and explainer `Voice messages need an audio model. Switch to an audio-capable model to send voice.` (:248) — exact match, with `View models` action label (:251).
- Remote gate hint `Device-only for now` (:249) and explainer `Voice messages stay on this device for now.` (:250) — exact match, with `Learn more` action (:252).
- Coachmark `Voice message` (:273), expand `Show more` / `Show less` (:269-270), fallback `%1$d:%2$02d voice message` (:271) — all exact.
- Help §9 title + 4 steps (:527-531) cover voice-vs-dictation, 60 s cap + 30 s note, local-only transcription, and the remote device-only note — matches the Help voice-section contract.
- Pre-existing TalkBack descriptions untouched: `voice_msg_record` ("Record voice message", :242) and dictation `cd_dictate` retained; no new keys needed, none added.
- ES parity complete: all 15 keys (`voice_msg_gate_*`, `voice_msg_view_models`, `voice_msg_learn_more`, `voice_msg_show_*`, `voice_msg_transcript_fallback`, `voice_msg_coachmark`, `help_s9_*`) present in `values-es/strings.xml` with correct format placeholders; voseo (`Cambiá`, `decís`) consistent with the file's existing convention.
- Explainer wiring carries the documented copy + actions: text-only Snackbar → `onNavigateToCatalog()` (`ChatScreen.kt:417-424`), remote Snackbar → `onNavigateToHelp()` (`ChatScreen.kt:425-435`), both `SnackbarDuration.Long` with action labels from resources (not literals).
- No generic labels, no hardcoded user copy in added lines (corroborated by plan-summary grep claims of 0 hits).

No finding against this pillar. Score 4/4.

### Pillar 2: Visuals (3/4)

- **WARNING — active-mode signal contradicts spec (see Fix #3):** the single-accent focal-point story holds for the idle row (neutral white-0.6f glyphs, gated 38% glyph), but the active states light a `primary@50%` container pill instead of the specified primary glyph tint (`ChatInputBar.kt:350,433`). Inherited from Phase 65/67, not introduced here — but Phase 69's UI-SPEC explicitly re-locks the tint-only rule, so code and contract now disagree.
- Glyph continuity honored: GraphicEq for voice-send, Mic for dictation, never swapped, side-by-side separate buttons (`ChatInputBar.kt:353-358,399,436`).
- Disabled-with-reason, never hidden, never dead: the gated branch keeps the same GraphicEq glyph at reduced opacity with a live tap handler into the explainer (`ChatInputBar.kt:381-403`); no `enabled = false` dead ends on the voice path.
- Coachmark anchoring correct: `TooltipBox` + `PlainTooltip` wraps the enabled voice `IconButton` only (`ChatInputBar.kt:416-439`), gated branch renders no tooltip, and `showVoiceCoachmark` combines seen-flag + gate (`combine(seen, gate) { !seen && gate == Allowed }` per 69-03 summary).
- Icon-only buttons all carry content descriptions (`voice_msg_record`, `cd_dictate`, `cd_stop_listening`); gated button additionally exposes `stateDescription = gatedHint` (`ChatInputBar.kt:395-397`).
- Caption slots underneath the player row with zero player-row re-layout, missing-file rows caption-free, assistant bubbles untouched (`MessageBubble.kt:259-282`) — matches the Surface 2 contract.

Score 3/4: structure and hierarchy are right; the one deduction is the spec-contradicting active-mode signal.

### Pillar 3: Color (3/4)

- **Gated-button rule honored exactly:** `onSurface.copy(alpha = 0.38f)` glyph tint, no container recolor (`Color.Transparent` inherited), no error-red (`ChatInputBar.kt:399-401`); hint text in `onSurfaceVariant` (`ChatInputBar.kt:390`).
- **Caption rule honored:** transcript and duration-only fallback both `onSurfaceVariant` (`MessageBubble.kt:842,851`); expand affordance `colorScheme.primary` text-button language (`MessageBubble.kt:870`); fallback never error-red.
- Accent containment holds for all Phase 69 additions: `primary` appears only on the expand affordance text and the pre-existing send affordance; nothing new paints error color except the untouched draft-delete path.
- **WARNING — active container recolors (see Fix #3):** `primary.copy(alpha = 0.5f)` container fills on dictation-listening (`ChatInputBar.kt:350`) and the voice-recording branch (`ChatInputBar.kt:433`) violate the "no background recolor" clause of the active-mode rule. Same root cause as the Visuals deduction — counted once in fixes, reflected in both pillar scores per the one-finding-per-pillar requirement.

Score 3/4: Phase 69's own color rules are fully honored; the deduction is the inherited active-state recolor contradicting the re-locked rule.

### Pillar 4: Typography (3/4)

- Caption, fallback, and gate-hint text all use `MaterialTheme.typography.labelLarge` (14sp, Regular) — matches the "Label 14sp" mandate (`ChatInputBar.kt:389`, `MessageBubble.kt:841,850,869`).
- Exactly the spec's 4-size/2-weight palette is touched: no new `fontSize`/`fontWeight` literals in Phase 69 hunks; expand affordance reuses `labelLarge` rather than introducing a button-text style.
- **WARNING — coachmark text style unspecified (see Fix #1):** `PlainTooltip { Text(...) }` (`ChatInputBar.kt:419-421`) sets no style, so the coachmark renders at ambient body size instead of Label 14sp. One-line fix, no API risk.

Score 3/4: one concrete deviation, otherwise clean.

### Pillar 5: Spacing (4/4)

- Caption-to-player gap `Spacer(Modifier.height(4.dp))` — the `xs` token (`MessageBubble.kt:275`).
- Gate hint-to-button gap `Spacer(Modifier.width(4.dp))` — the `xs` token (`ChatInputBar.kt:392`); row spacers `8.dp` (`sm`) preserved (`ChatInputBar.kt:361,441`).
- Touch-target floors all met: gated `IconButton` 48dp (`ChatInputBar.kt:395`), enabled voice `IconButton` 48dp (`ChatInputBar.kt:431`), dictation mic 40→48dp region (`ChatInputBar.kt:346` at 40dp — pre-existing Phase 65 size, adjacent voice/gated targets are 48dp; noting, not flagging, since out of Phase 69 scope), expand `TextButton` `sizeIn(minHeight = 48.dp)` (`MessageBubble.kt:861`).
- Expand `TextButton` uses `contentPadding = PaddingValues(0.dp)` — intentional affordance-density choice, still a 4dp-multiple, no arbitrary values.
- No non-scale spacing introduced by this phase; bubble `md` padding untouched.

Score 4/4.

### Pillar 6: Experience Design (3/4)

- Gate evaluation order correct: VM gate first (explainer), then permission flow, then toggle (`ChatScreen.kt:446-453`); `Allowed -> Unit` no-op keeps the allowed path silent.
- Draft-kept rule implemented at both send entry points (`sendMessage` + `sendVoiceMessage` pre-clear guards per 69-01 summary) with reason-Snackbar feedback — never silent drop, never attempted send.
- Coachmark dismissal covers all three specified paths — outside tap via `onDismissRequest` (`ChatInputBar.kt:424`), voice tap (`ChatInputBar.kt:428`), dictation tap (`ChatInputBar.kt:343`) — funnelling to one idempotent VM persist; DataStore write off-Main; never re-shows after dismissal.
- Gate flips live on model/endpoint switch (derived `StateFlow`, `Eagerly`), evaluated on select + chat open; gate helper keyed on provider type + capability with fail-open null semantics — future remote unlock needs no UI rework.
- Rotation/backgrounding: transcript STT session VM-owned, rotation-safe; `remember(messageKey)` expansion resets to collapsed on rotation — explicitly accepted in the UI-SPEC backstop row.
- **WARNING — expand affordance exposes no expanded-state semantics (see Fix #2):** sighted behavior (latch-overflow affordance only when `hasVisualOverflow`, `MessageBubble.kt:854-858`) is correct, but TalkBack users get no state announcement. The Phase 49 tool-transcript row in the same file already solves this (`semanticsForToolResult`, :622-628) — the fix is a local pattern reuse.
- Minor observation (not scored): the voice-tap path calls `onCoachmarkDismiss()` and `onVoiceClick()` sequentially, so a first-tap dismissal double-persists (tooltip `onDismissRequest` + click wrapper); idempotent server-side, no user impact.

Score 3/4: state coverage is genuinely complete (loading/error/empty N/A by contract — gating uses explainers, STT failure degrades to the honest fallback without blocking send); the deduction is the TalkBack state gap on the new expand control.

---

## Registry Safety

Skipped — native Android project, no `components.json`, no third-party registries (UI-SPEC Registry Safety table confirms shadcn not applicable, zero new Gradle deps).

---

## Files Audited

- `app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt` (gated button :381-403, coachmark :404-440, dictation :332-359)
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` (explainer :411-439, voice-tap gate :446-461, coachmark pass-through :602)
- `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt` (caption slot :259-282, `VoiceTranscriptCaption` :827-874, Phase 49 semantics precedent :622-628)
- `app/src/main/java/com/warped/ui/help/HelpScreen.kt` (Section 9 :186-198)
- `app/src/main/res/values/strings.xml` (`voice_msg_*` :238-273, `help_s9_*` :527-531)
- `app/src/main/res/values-es/strings.xml` (ES parity — 15 keys verified)
- `.planning/phases/69-voice-differentiation-gating-transcript/69-UI-SPEC.md` (baseline)
- `.planning/phases/69-voice-differentiation-gating-transcript/69-01-SUMMARY.md`, `69-02-SUMMARY.md`, `69-03-SUMMARY.md`, `69-CONTEXT.md` (intent cross-check)
