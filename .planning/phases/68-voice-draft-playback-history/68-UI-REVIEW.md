# Phase 68 — UI Review (Voice Draft + Playback History)

**Audited:** 2026-10-02
**Baseline:** `68-UI-SPEC.md` (draft status, checker sign-off pending)
**Screenshots:** not captured — native Android project, no web dev server (code-only audit of `ChatInputBar.kt`, `MessageBubble.kt`, `ChatScreen.kt`, `values/strings.xml` + `values-es/strings.xml`)
**Registry audit:** skipped — `shadcn_initialized: false`, native Android, zero new Gradle deps

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 4/4 | All 7 contract strings exact, EN+ES paired, zero hardcoded copy |
| 2. Visuals | 3/4 | Hierarchy + chrome correct; draft/bubble progress track colors inconsistent |
| 3. Color | 3/4 | Accent/error/informational roles respected; one track-color + one pre-existing hardcode |
| 4. Typography | 3/4 | Correct 14sp Label role; Medium-500 instead of Regular-400 + unlocalized duration format |
| 5. Spacing | 4/4 | All values on the 4dp scale; 48dp touch floors honored |
| 6. Experience Design | 3/4 | States + lifecycle fully wired; bubble row lacks draft-parity TalkBack; rotation contract ambiguous |

**Overall: 20/24**

---

## Top 3 Priority Fixes

1. **Bubble player row has no row-level `stateDescription` (draft card has one)** — TalkBack users get button labels ("Play voice message, 0:12") but no "paused/playing" row state, unlike the draft card's "Voice draft, 0:12, paused" announcement — add `.semantics { stateDescription = … }` to `VoicePlayerRow` mirroring `DraftPreviewCard` (`ChatInputBar.kt:431`), reusing `voice_msg_play/pause_message` strings.
2. **Rotation-position contract contradicts the implementation** — UI-SPEC Surface 2 + considerations table say "position resets to 0 on rotation", but `ChatScreen.kt:224-234` pauses-and-keeps position on every `ON_PAUSE` (rotation included) and Plan 01's own summary claims both behaviors in different bullets — decide one convention (keep-position is the better UX and matches CONTEXT's "resumes with one tap") and amend the UI-SPEC line.
3. **Draft progress track uses `surfaceVariant`, bubble uses `surfaceContainerHighest`** — same player language, two track colors (`ChatInputBar.kt:462` vs `MessageBubble.kt:769`) — change the draft track to `surfaceContainerHighest` for one consistent voice-progress treatment.

---

## Detailed Findings

### Pillar 1: Copywriting (4/4)

All contract rows verified against `values/strings.xml` (EN) + `values-es/strings.xml` (ES):

- ✅ "Play voice draft" / "Pause voice draft" toggle CDs — `voice_msg_play/pause_draft` (`strings.xml:250-251`), ES "Escuchar/Pausar borrador de voz" — exact match to contract.
- ✅ "Send voice message" — `voice_msg_send_voice` (:252); "Delete voice draft" — `voice_msg_delete_draft` (:253). Exact.
- ✅ "Play voice message, 0:12" / "Pause voice message" with interpolated duration — `voice_msg_play/pause_message` with `%1$d:%2$02d` (:260-261), wired in `MessageBubble.kt:748-753`. Exact.
- ✅ "Recording too short" — `voice_msg_too_short` (:249), ES "Grabación muy corta". Rejection path deletes the file and emits via the existing `SnackbarHost`/`SnackbarDuration.Short` channel (`ChatScreen.kt:286`); draft card never appears on the reject path. Exact.
- ✅ "Voice clip unavailable" — `voice_msg_clip_unavailable` (:258), ES "Clip de voz no disponible". Exact, informational only.
- ✅ No-confirmation delete per contract — single-tap discard + immediate file delete, no dialog in either card or bubble path.
- ✅ Bilingual parity: every new key exists in both files with matching format args (including the two additive `voice_msg_draft_playing/paused` state words, :255-256).
- ✅ Hardcoded-copy grep clean per plan summaries (0 hits); 21 `voice_msg_*` keys total, consistent prefix.
- **WARNING (minor):** duration readout uses `"%d:%02d".format(...)` with the default locale (`ChatInputBar.kt:466`, `MessageBubble.kt:773`) rather than a localized format string — most locales render identically, but locale-sensitive digits (e.g. ar-EG) would diverge from the `strings.xml` convention. Recommend `String.format(locale, …)` or a `voice_msg_duration_fmt` resource.

### Pillar 2: Visuals (3/4)

- ✅ Draft card sits above `ChatInputBar` inside the input column (`ChatInputBar.kt:147-158`), caption field stays live — matches "must not cover or disable the caption input".
- ✅ Left-to-right order play → progress → duration → send → delete (`ChatInputBar.kt:433-489`) matches spec; send uses the Phase 67 send affordance, delete the X/delete language.
- ✅ Bubble player row slots after images, above caption text (`MessageBubble.kt:252-261`) inside untouched bubble chrome — Phase 69 transcript slot preserved.
- ✅ All icon-only buttons carry content descriptions (draft play/send/delete, bubble toggle); unavailable-row icon is decorative `contentDescription = null` with text carrying meaning (`MessageBubble.kt:725-727`) — correct.
- ✅ No pulsing background or card recolor on playback; progress-fill motion is the sole state signal per the locked draft-card rule.
- ⚠️ **WARNING:** progress track inconsistency — draft `trackColor = surfaceVariant` (`ChatInputBar.kt:462`) vs bubble `trackColor = surfaceContainerHighest` (`MessageBubble.kt:769`). UI-SPEC pins `surfaceContainerHighest` for the bubble; the draft is unpinned but visual parity demands one value (see fix #3).

### Pillar 3: Color (3/4)

- ✅ Draft: background `surfaceContainer`, play tint `primary`, delete tint `error`, duration `onSurfaceVariant` (`ChatInputBar.kt:426-471`) — exact match to the locked draft-card rule.
- ✅ Bubble: play tint `primary`, progress `primary` on `surfaceContainerHighest`, duration `onSurfaceVariant` (`MessageBubble.kt:754-777`) — exact match to the bubble rule.
- ✅ Unavailable row icon + text in `onSurfaceVariant`, never error-red (`MessageBubble.kt:726-736`) — correct informational treatment.
- ✅ Accent appears only on play affordances, progress fills, and send — no accent bleed into error/delete surfaces; delete owns `error` alone.
- ⚠️ **WARNING:** draft progress track `surfaceVariant` vs bubble `surfaceContainerHighest` (same as Pillar 2 — single inconsistency, counted once for scoring).
- ℹ️ Pre-existing (not Phase 68): own-bubble caption text uses hardcoded `Color.White` (`MessageBubble.kt:281`). Out of scope, noted for a future pass — do not attribute to this phase.

### Pillar 4: Typography (3/4)

- ✅ Duration readouts use Label-scale 14sp with `fontFeatureSettings = "tnum"` digit stability in both card and bubbles (`ChatInputBar.kt:467-469`, `MessageBubble.kt:774-776`) — matches the "no jitter" requirement.
- ✅ Unavailable text uses Label scale in `onSurfaceVariant` (`MessageBubble.kt:734-735`).
- ⚠️ **WARNING:** both duration/unavailable texts use `MaterialTheme.typography.labelLarge` (14sp **Medium 500**); the contract declares Label as 14sp **Regular 400**. One-weight drift, low visual impact — either amend the contract to `labelLarge` (recommended: it is the idiomatic M3 label) or switch to `labelMedium`.
- ⚠️ **WARNING:** duration value itself is not a string resource (see Pillar 1) — translators cannot reorder `m:ss` if a locale ever requires it.

### Pillar 5: Spacing (4/4)

- ✅ Draft card internal padding `16.dp` = md token (`ChatInputBar.kt:434`); play-to-progress gap `4.dp` = xs (:451); progress-to-duration and duration-to-actions `8.dp` = sm (:464, :472) — all on the declared 4dp scale, matching the "draft card + bubble row internal gaps" usage column.
- ✅ Draft-to-input separation `8.dp` (`ChatInputBar.kt:157`); unavailable-row icon gap `8.dp` (`MessageBubble.kt:731`).
- ✅ All four draft affordances (play, send, delete) and the bubble toggle use `Modifier.size(48.dp)` hit targets — the 48dp accessibility floor honored everywhere.
- ✅ No arbitrary/non-scale dp values introduced by this phase in the audited surfaces.

### Pillar 6: Experience Design (3/4)

- ✅ Sub-1 s guard at the single stop-and-keep choke point (manual + auto + background) — file deleted, card never appears, Snackbar emitted.
- ✅ Missing-file bubble: remembered per-message `hasVoiceFile` check (no composition-hot-path IO), graceful row, never silent drop, never crash.
- ✅ Single-player discipline: starting one clip stops any other (shared `VoiceMessagePlayer`, `currentPath`-aware resume); rapid-toggle debounce in VM.
- ✅ Lifecycle: chat exit stops all playback (`ChatScreen.kt:202`); `ON_PAUSE` pauses draft + history (`:233-234`); audio-focus loss pauses; no foreground service; players only on own USER bubbles (`MessageBubble.kt:252`), assistant bubbles untouched.
- ✅ Draft TalkBack: `stateDescription` "Voice draft, m:ss, playing/paused" (`ChatInputBar.kt:420-431`), changing only on toggles — 5 s-spam discipline holds by construction.
- ⚠️ **WARNING:** `VoicePlayerRow` has no row-level `stateDescription` — bubble TalkBack is button-label-only while the draft announces full state (see fix #1). Low effort, real a11y asymmetry.
- ⚠️ **WARNING:** rotation-position ambiguity — spec text says reset-to-0, code keeps position, CONTEXT says both in different bullets (see fix #2). Behavior is graceful either way; the defect is contract drift, not UX breakage.
- 🧪 Backstop carried: long-caption + voice layout wrap is a held-out hardware visual check (release-UAT runbook), unchanged from Phase 67.

---

## Files Audited

- `app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt` (draft card `DraftPreviewCard`, `:407-491`; wiring `:147-158`)
- `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt` (bubble `VoicePlayerRow`, `:715-780`; slot `:252-261`)
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` (draft/history state collection, `ON_PAUSE` pause, exit stop, Snackbar channels)
- `app/src/main/res/values/strings.xml` + `app/src/main/res/values-es/strings.xml` (`voice_msg_*`, 21 keys)
- `.planning/phases/68-voice-draft-playback-history/68-UI-SPEC.md`, `68-CONTEXT.md`, `68-01/02/03-SUMMARY.md` (baseline + intent)
