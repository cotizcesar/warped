# Phase 65 — UI Review

**Audited:** 2026-10-02
**Baseline:** 65-UI-SPEC.md (design contract)
**Screenshots:** not captured (no dev server — native Android app; code-only audit of Compose sources)

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 3/4 | 7/7 keys with EN+ES parity, but shipped rationale confirm copy drifts from contract |
| 2. Visuals | 4/4 | Mic placement, sizing, tints, and listening toggle all match contract |
| 3. Color | 4/4 | Accent confined to contract-declared surfaces; no error-red, no hardcoded branches |
| 4. Typography | 4/4 | Zero new roles; rationale reuses dialog defaults identically to 19 sibling call sites |
| 5. Spacing | 4/4 | Bar values only (8dp spacer, 40dp button, 24dp icon); 10.dp count unchanged at baseline |
| 6. Experience Design | 3/4 | All 7 spec states wired + teardown defense-in-depth; silent-error and TalkBack gaps remain |

**Overall: 22/24**

---

## Top 3 Priority Fixes

1. **Rationale confirm copy drifts from contract** — UI-SPEC §4 prescribes confirm `voice_rationale_allow` = EN "Allow" / ES "Permitir"; shipped strings are "Allow Microphone" / "Permitir micrófono" (`values/strings.xml:224`, `values-es/strings.xml:225`). Either amend the contract or revert the strings — contract and code must agree. — Fix: update 65-UI-SPEC.md §4 + contract-summary row to the verb+noun form (recommended — clearer Play-disclosure copy), or change both strings back to "Allow"/"Permitir".
2. **Listening state is not reliably announced to TalkBack** — contract §6 specifies "announced via content-description change only," and the implementation swaps `cd_dictate` ↔ `cd_stop_listening` (`ChatInputBar.kt:212-216`), but TalkBack does not reliably re-announce a content-description change on a focused/stable node. A blind user gets no confirmation that recording started. — Fix: add `Modifier.semantics { stateDescription = "Listening" }` (localized) on the mic `IconButton` when `isListening`, cleared when idle.
3. **Recognizer failure is fully silent with no user signal** — `onDictationError` only clears `isListening` (`ChatViewModel.kt:1542-1547`); if the user spoke into a failing recognizer (no-speech, network, timeout), the mic silently reverts to idle with an empty or truncated draft and no explanation. Spec §3 mandates silence, so this is contract-compliant — but the residual UX risk (user cannot distinguish "heard nothing" from "error, try again") should be explicitly accepted or revisited with a transient Short feedback. — Fix (advisory): keep silent-error default, but log decision; optionally show a Short non-blocking Snackbar on `ERROR_NO_MATCH`/`ERROR_SPEECH_TIMEOUT` only.

---

## Detailed Findings

### Pillar 1: Copywriting (3/4)

**WARNING — contract copy drift on rationale confirm label.**
- Contract (UI-SPEC §4): confirm = EN "Allow" / ES "Permitir".
- Shipped (`app/src/main/res/values/strings.xml:224`): "Allow Microphone"; (`values-es/strings.xml:225`): "Permitir micrófono".
- The verb+noun form is arguably *better* copy (matches the VERIFICATION report's blessing), but the contract was never amended — audit fails the string against the written contract. All other copy matches exactly:
  - `cd_dictate` "Dictate"/"Dictar" (strings.xml:192, strings-es:192) ✓
  - `cd_stop_listening` "Stop listening"/"Dejar de escuchar" ✓
  - `voice_rationale_title` "Voice dictation"/"Dictado por voz" ✓
  - `voice_rationale_body` matches spec body incl. system-service disclosure ✓
  - `voice_denied` "Microphone blocked. Enable it in Settings to dictate." / "Micrófono bloqueado. Actívalo en Ajustes para dictar." — exact spec match ✓
  - `voice_open_settings` "Settings"/"Ajustes" ✓
  - Existing `dismiss` ("Dismiss"/"Descartar") reused, no new dismiss key ✓
  - Placeholder `type_message` untouched ✓; no generic "Submit/OK/Click Here" labels anywhere in touched files ✓
- Score 3, not lower: 6 of 7 keys exact, EN+ES parity 7/7, disclosure copy present. One WARNING, no BLOCKER.

### Pillar 2: Visuals (4/4)

- **Placement:** mic `IconButton` immediately left of send/stop slot (`ChatInputBar.kt:203-220`), coexists with send — never replaces it ✓
- **Sizing:** 40.dp button + 24.dp icon, identical to image/send/stop siblings (lines 170, 206, 213, 216, 223, 231) ✓
- **Idle:** `Icons.Filled.Mic`, `Color.White.copy(alpha = 0.6f)` — matches idle image-button tint (line 172) ✓
- **Listening:** swaps to `Icons.Filled.Stop` full-white + `primary.copy(alpha = 0.5f)` container — exact reuse of thinking-toggle active-pill treatment (lines 182, 208), zero new icon dependency (`Mic`/`Stop` both already imported, lines 14-15) ✓
- **No-two-stop-icons:** `if (speechAvailable && !isGenerating)` gate (line 203) + generation-stop in the `isGenerating` branch (line 222) — mutually exclusive by construction ✓
- **Hidden state:** `if` guard, no placeholder, no dimmed dead affordance — matches `modelHasVision` pattern ✓
- Minor observation (not scored down): mic mount/unmount on generation start/stop shifts the send slot horizontally; contract mandates the hiding behavior, so this is accepted layout churn, not a defect.

### Pillar 3: Color (4/4)

- Accent (`primary` #D97757) appears in the touched mic/rationale surfaces only where declared: (a) listening container @0.5 alpha (`ChatInputBar.kt:208`), (b) rationale confirm via `WarpedAlertDialog`/`TextButton` default (`ChatScreen.kt:645-653`), plus the pre-existing send-button fill (line 233) — 60/30/10 distribution unchanged ✓
- No error-red anywhere in the phase (grep over both touched files: zero `Red`/`errorContainer` references) ✓
- Snackbar uses default surface; no custom colors on denial path ✓
- Dark-mode: all mic tints derive from `colorScheme.primary` + white-alpha overlays; bar surface `Color(0xFF2B2B29)` untouched (line 66, pre-existing) — no hardcoded scheme branch added ✓
- Hardcoded-color grep on touched files returns only the pre-existing bar surface; mic block introduces zero hex literals ✓

### Pillar 4: Typography (4/4)

- Zero new type roles: mic button is icon-only (content-descriptions, no `Text`); rationale body explicitly `MaterialTheme.typography.bodyMedium` (`ChatScreen.kt:642`) — the same role the sibling model-switch dialog uses (line 673) ✓
- Rationale title slot passes plain `Text(...)` (line 638) with no explicit style — identical to all sibling `WarpedAlertDialog` call sites (e.g., line 669); relies on dialog default, consistent with the 19 existing usages ✓
- No `fontSize`/`fontWeight` overrides added in either touched file (only pre-existing `labelSmall.fontSize` at `ChatInputBar.kt:191`, untouched) ✓
- New strings are plain body copy, no styled spans ✓

### Pillar 5: Spacing (5-scale audit) (4/4)

- New spacer between mic and send slot reuses `8.dp` (`ChatInputBar.kt:219`) — matches the bar's existing `spacedBy(8.dp)` row rhythm (line 78) ✓
- Button 40.dp / icon 24.dp per contract; no new dp literals introduced in the mic block ✓
- `10.dp` count unchanged at pre-phase baseline of 5 (per 65-02 SUMMARY backstop verification via `git stash` comparison) ✓
- `max-new-spacing-tokens: 0` backstop holds; only the two plan-listed files touched ✓

### Pillar 6: Experience Design (3/4)

State coverage verified in code:
- idle-mic / listening-stop toggle: `isListening` collected via `collectAsStateWithLifecycle`, granted-tap toggles start/stop (`ChatScreen.kt:302-312`) ✓
- hidden without recognizer: `speechAvailable` probed off-main-thread at init (`ChatViewModel.kt:366-369`) + refreshed on `ON_RESUME` (`ChatScreen.kt:169-174`) ✓
- generating yields: mic gate excludes `isGenerating`; send path stops dictation first (`ChatScreen.kt:399-401`) + `stopDictation()` at top of `sendMessage` (`ChatViewModel.kt:439`) — defense-in-depth ✓
- rationale first-tap-only: `rememberSaveable voiceRationaleSeen` (line 132); confirm is the sole launcher trigger; dismiss/scrim both mark seen without requesting (lines 632-663) ✓
- denial split: permanent → `emitMicDenied()` → `SnackbarWithAction(OPEN_APP_SETTINGS)` with `SnackbarDuration.Long` + package-URI Settings intent (lines 225-241); transient → silent (line 293) ✓
- recognition-error silent: `onDictationError` clears flag, no emission (lines 1542-1547) — per spec ✓
- teardown: `DisposableEffect.onDispose stopDictation()` (lines 154-162) + `ViewModel.onCleared` destroy — recognizer never outlives UI ✓
- no-stuck-toggle: `_isListening.value = manager.start()` (line 1597) — flag flips only on platform accept; `cancel()`-before-start and empty-final forwarding present ✓
- dictation lands editable at cursor, never auto-sends: `insertAtCursor`/`updateInputCursor` path, no `sendMessage` call in dictation handlers ✓

**WARNING 1 — silent recognizer failure leaves the user with no signal** (see Top Fix 3). Spec-mandated, recorded here as accepted residual risk, not a defect against the contract.

**WARNING 2 — listening announcement relies on content-description swap alone** (see Top Fix 2). The `liveRegion`/`stateDescription` semantics imports in `ChatScreen.kt` (lines 55-58) serve other nodes (lines 786-788, 943); the mic button itself carries no live-region or state-description semantics.

Minor note (not scored): `voiceRationaleSeen` is `rememberSaveable`, not persisted — after process death the rationale shows once more. Trivial and arguably correct (re-consent after death); no action.

---

## Files Audited

- `app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt` (mic block lines 199-220; siblings 170-245)
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` (rationale 125-135, 628-663; launcher 271-312; Snackbar collector 211-244; teardown 154-179; wiring 395-429)
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` (dictation state machine 1472-1632; availability 366-401; send-stop 439)
- `app/src/main/java/com/warped/ui/chat/voice/VoiceDictationManager.kt` (availability gate, start/stop/destroy — via SUMMARY + VERIFICATION evidence)
- `app/src/main/res/values/strings.xml` + `values-es/strings.xml` (7 dictation keys + `dismiss` reuse)
- `.planning/phases/65-voice-dictation/65-UI-SPEC.md` (contract), `65-01-SUMMARY.md`, `65-02-SUMMARY.md`, `65-VERIFICATION.md` (evidence cross-check)

*Registry audit: skipped — no `components.json` (native Android project, no shadcn/third-party registries).*
*Advisory review — no BLOCKERs; all findings are WARNING-level or minor observations. Nothing here gates shipping.*
