# Phase 67 — UI Review (Voice Capture + Send Path)

**Audited:** 2026-10-02
**Baseline:** `.planning/phases/67-voice-capture-send-path/67-UI-SPEC.md`
**Screenshots:** not captured — native Android project, no web dev server (code-only audit of `ChatInputBar.kt`, `ChatScreen.kt`, `voice_msg_*` strings EN+ES)
**Registry audit:** skipped — UI-SPEC declares no third-party registries (native Android, shadcn not applicable, zero new Gradle deps)

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 3/4 | All 6 spec'd strings exact + ES; voice-send first tap reuses dictation rationale copy |
| 2. Visuals | 3/4 | GraphicEq glyph + inline recording row correct; duplicate Stop nodes + 40dp idle voice button |
| 3. Color | 3/4 | Locked recording-state rule followed; Row-2 button adds un-spec'd primary tint while recording |
| 4. Typography | 4/4 | Timer uses Label 14sp + tnum only; no new sizes/weights introduced |
| 5. Spacing | 3/4 | Gaps are valid 8dp tokens; timer-to-bar gap is 8dp vs spec-suggested 4dp xs |
| 6. Experience Design | 3/4 | All states/permission/lifecycle covered; long-caption backstop test has no evidence |

**Overall: 19/24**

---

## Top 3 Priority Fixes

1. **Voice-send first-tap rationale shows dictation copy** — user tapping the waveform button sees dialog titled "Voice dictation" with dictation body text, misrepresenting what they tapped — add `voice_msg_rationale_title/body` strings and branch on `pendingVoiceRequestName` in `ChatScreen.kt` (~lines 753–788).
2. **Two "Stop recording" nodes live simultaneously during recording** — Row-1 recording-row stop (`ChatInputBar.kt:197`) and Row-2 voice button flipped to Stop (`ChatInputBar.kt:330`) share the same content description; TalkBack users hear duplicates and tap targets are ambiguous — hide the Row-2 voice button while `isVoiceRecording` (Row 1 owns stop).
3. **Idle voice-send button is 40dp, spec floor is 48dp** — `ChatInputBar.kt:324` uses `Modifier.size(40.dp)`; UI-SPEC spacing exceptions require 48dp minimum touch targets for voice-send/stop/cancel — bump voice-send (and dictation mic for parity) to 48dp.

---

## Detailed Findings

### Pillar 1: Copywriting (3/4) — WARNING

Spec contract vs implemented strings (`app/src/main/res/values/strings.xml:238-246`, mirrored in `values-es/strings.xml:239-247`):

| Element | Spec | Implemented | Verdict |
|---------|------|-------------|---------|
| Primary CTA | `Record voice message` | `voice_msg_record` = "Record voice message" / "Grabar mensaje de voz" | ✅ exact |
| Stop action | `Stop recording` | `voice_msg_stop` = "Stop recording" / "Detener grabación" | ✅ exact |
| Cancel action | `Cancel recording` | `voice_msg_cancel` = "Cancel recording" / "Cancelar grabación" | ✅ exact |
| Error state | `Microphone access needed — grant permission to record voice messages` | `voice_msg_denied`, exact incl. em dash | ✅ exact |
| Cap notice | `60s limit reached` | `voice_msg_cap_reached`, exact | ✅ exact |
| Truncation note | `First 30s sent to model` | `voice_msg_first_30s`, exact | ✅ exact |
| Destructive confirmation | none (instant discard) | `onCancelRecording` → `cancelVoiceRecording()` with no dialog | ✅ per VMSG-01 |

- **WARNING (finding 1): shared rationale shows wrong feature copy.** `ChatScreen.kt:753-788` renders the same `voice_rationale_title` ("Voice dictation") / `voice_rationale_body` (dictation body) regardless of `pendingVoiceRequestName`. A voice-message first tap is explained as dictation. Fix: add `voice_msg_rationale_title`/`voice_msg_rationale_body` (EN+ES) and branch the dialog text on the pending intent.
- Minor: three extra strings beyond the six spec'd (`voice_msg_remote_blocked`, `voice_msg_transcode_failed`, `voice_msg_recording_state`) — justified (zero hardcoded copy; `grep` for literals returns 0 per SUMMARY) and all shipped EN+ES. Not a defect.
- Minor: visual timer uses `"%d:%02d"` (`ChatInputBar.kt:183` → `0:05` style) while spec writes `mm:ss` (`00:05` style); TalkBack format `Recording, %1$d:%2$02d` matches the spec's "Recording, 0:12" example. Cosmetic only.

### Pillar 2: Visuals (3/4) — WARNING

- ✅ Voice-send button uses `Icons.Filled.GraphicEq` (`ChatInputBar.kt:334`) — waveform family, never a mic glyph per T-67-06. Dictation mic (`Icons.Filled.Mic`) and voice button coexist with distinct glyphs.
- ✅ Recording replaces Row 1 inline: timer + `LinearProgressIndicator` amplitude bar + cancel X + stop toggle (`ChatInputBar.kt:159-201`); Row 2 controls persist. No new screens/dialogs.
- ✅ Cancel X and Row-1 stop are 48dp targets (`ChatInputBar.kt:178,197`) with 24dp glyphs.
- **WARNING (finding 2): duplicate Stop affordance during recording.** Row-1 stop (`:197`, `voice_msg_stop`) and Row-2 voice button (`:330-332`, also `voice_msg_stop`) are both visible, enabled, and identically labelled while `isVoiceRecording`. Spec State 2 describes a single stop ("tap voice-send button again to stop"); two live nodes split the affordance and duplicate the TalkBack announcement. Fix: gate the Row-2 voice button with `!isVoiceRecording`.
- **WARNING (finding 3): idle voice-send button undersized.** `ChatInputBar.kt:324` = 40dp; spec mandates 48dp minimum for voice-send/stop/cancel. (Row-1 stop and cancel comply at 48dp; only the Row-2 idle button and, for parity, the 40dp dictation mic at `:293` fall short.)
- Note (pre-existing, out of scope): input `Surface` uses hardcoded `Color(0xFF2B2B29)` (`:86`) rather than `colorScheme.surface/surfaceContainer` — predates Phase 67, not scored here.

### Pillar 3: Color (3/4) — WARNING

- ✅ Locked recording-state rule implemented exactly: `recColor = error` when `voiceElapsedSec >= 50`, else `onSurfaceVariant` (`ChatInputBar.kt:160-162`); applied to timer text (`:187`) and amplitude bar fill (`:193`). Track stays `surfaceVariant` (`:194`). No pulsing background, no pill recolor.
- ✅ Cancel X tinted `colorScheme.error` (`:179-180`); error color never used for non-error text; accent never used for cancel.
- **WARNING (minor): Row-2 voice button gains a `primary.copy(alpha = 0.5f)` container while recording** (`ChatInputBar.kt:326-328`). Strict reading of the locked rule ("no other color change during recording") reserves color change to timer + bar. In practice it mirrors the established dictation-listening pattern (`:297-299`) and reads as active-state affordance, but it is a third recording-state color change the spec does not declare. Either declare it in the UI-SPEC or remove the container tint while `isVoiceRecording` (the Row-1 stop already signals state).
- Count: `colorScheme.error` = timer/bar/cancel only; `primary` = voice-button tint + existing send/reasoning affordances. No hardcoded recording colors. No 60/30/10 violation attributable to this phase.

### Pillar 4: Typography (4/4)

- ✅ Only addition is the timer `Text` at `MaterialTheme.typography.labelLarge` (14sp, Label role) with `fontFeatureSettings = "tnum"` (`ChatInputBar.kt:182-188`) — exactly the spec's "Label 14sp tabular-nums equivalent" requirement; `mm:ss` cannot jitter.
- ✅ No new sizes or weights: phase introduces zero `sp`/`fontSize`/`fontWeight` tokens beyond the inherited `labelLarge`. Declared 4-size/2-weight system intact.
- No finding beyond compliance; score reflects exact contract match.

### Pillar 5: Spacing (3/4) — WARNING

- ✅ All recording-row gaps are spec-scale multiples of 4: `8.dp` timer↔bar and bar�↔stop (`ChatInputBar.kt:189,196`), 48dp touch targets in Row 1, existing 10dp/16dp rhythms untouched elsewhere.
- **WARNING (minor): timer-to-bar gap is 8dp (sm) where the spec table parenthetically assigns the xs 4px token to the timer-to-bar gap** (`67-UI-SPEC.md` spacing table). 8dp is the safer, more legible choice and a legal token — recommend amending the spec parenthetical rather than shrinking the gap, but contract and code currently disagree.
- No arbitrary values introduced by this phase (`[.*px]`/`rem` grep: none in `ChatInputBar.kt`/`ChatScreen.kt` voice code); `10.dp`/`8.dp`/`48.dp` all on-scale.

### Pillar 6: Experience Design (3/4) — WARNING

State coverage verified in code:

| Spec state | Implementation | Verdict |
|-----------|----------------|---------|
| Idle: both buttons visible when `speechAvailable` | `ChatInputBar.kt:286,316` gate on `speechAvailable && !isGenerating && !isLoadingModel` | ✅ |
| Recording: live timer (1s) + amplitude (~100ms) | VM ticker + sampler as session-Job children (per 67-01 SUMMARY) | ✅ |
| Last-10s red at ≥50s | `:160` threshold | ✅ |
| Mutual exclusion both directions | `startVoiceRecording` stops dictation; `startDictation` stops voice (keeps clip) | ✅ |
| Second-recording impossible (single toggle) | `voiceStarting` single-flight + recorder double-start ignore | ✅ |
| Auto-stop 60s, clip kept, toast once | `voiceCapEvent` SharedFlow → `Toast.LENGTH_SHORT` (`ChatScreen.kt:416-420`), never state-derived | ✅ |
| >30s note via Snackbar Short | `ChatEvent.Snackbar(voice_msg_first_30s)` through existing host | ✅ |
| Text-only guard: toast, never dead | `error_no_audio` toast (`ChatScreen.kt:389-391`); button stays tappable | ✅ |
| Remote guard: minimal block + feedback | `voice_msg_remote_blocked` toast (`:392-398`) | ✅ |
| `isLoadingModel` locks whole bar | existing `inputLocked` gate, button unreachable mid-load | ✅ |
| Permission: rationale → request → transient Snackbar / permanent Settings escape | shared `micPermissionLauncher` + `pendingVoiceRequest` routing (`:322-358`), `emitVoiceDenied(Transient)` | ✅ |
| Rotation survives | VM-owned recorder + flows; intent via `rememberSaveable` enum name (`:155`) | ✅ |
| Background auto-stops, keeps clip, silent | `ON_PAUSE → autoStopVoiceRecording(announceCap = false)` (`:209-211`) | ✅ |
| Cancel discards immediately, no confirm | `cancelVoiceRecording` deletes session + kept clip | ✅ |
| Sub-1s clips kept (Phase 68 owns guard) | no duration rejection in send path | ✅ |
| Failed send keeps file for retry | transcode/send failure keeps file, emits Snackbar | ✅ |
| TalkBack: distinct Record/Stop/Cancel descriptions + 5s-bucket `stateDescription` | `ChatInputBar.kt:166-175,179,198,331,334` | ✅ (modulo duplicate-Stop note) |

- **WARNING (minor): duplicate Stop nodes** (see Visuals finding 2) also degrade the TalkBack experience — two identical "Stop recording" actions in one row.
- **WARNING (minor): held-out backstop has no evidence.** UI-SPEC lists "long caption text beside a voice clip wraps without clipping the send affordance" as a 🧪 backstop visual UI-state test; neither SUMMARY claims it and no such test file exists (`VoiceMessageGuardTest` covers guards only). Send reuses the existing bubble path so risk is low, but the backstop is unverified — add the test or explicitly defer it to Phase 68/69.
- Note: `modelHasAudio` param on `ChatInputBar` (`:55`) is accepted but never read — gating lives in `ChatScreen.onVoiceClick`. Dead parameter; harmless (behavior is spec-correct: toast on attempt) but should be consumed or removed to avoid future confusion.
- Note: recording-row semantics set `stateDescription` without `liveRegion = LiveRegionMode.Polite`, so bucketed announcements depend on focus rather than assertive announcement — consistent with the Phase 65 pattern the spec locks in; flagging for Phase 69 polish, not this phase.

---

## Files Audited

- `app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt` (recording row `:159-201`, voice button `:312-339`, dictation mic `:286-310`)
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` (permission launcher `:322-358`, voice gate `:384-412`, cap toast `:416-420`, send routing `:517-521`, rationale dialog `:753-788`, `TurnStatusRow` — untouched, no voice usage, correct)
- `app/src/main/res/values/strings.xml:238-246` + `app/src/main/res/values-es/strings.xml:239-247` (`voice_msg_*`, 9 pairs)
- `.planning/phases/67-voice-capture-send-path/67-UI-SPEC.md` (design contract)
- `.planning/phases/67-voice-capture-send-path/67-01-SUMMARY.md`, `67-02-SUMMARY.md` (implementation claims cross-checked)
- `ChatViewModel.kt` voice surface verified via grep (`voiceCapEvent`, `emitVoiceDenied(Transient)`, `sendVoiceMessage`, `autoStopVoiceRecording`) — not re-read in full
