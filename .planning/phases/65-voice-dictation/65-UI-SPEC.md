# Phase 65: Voice Dictation — UI-SPEC

**Status:** draft
**Design System:** Manual (Material 3 dark-first, no shadcn — Android native)
**Sources:** 65-CONTEXT.md (locked decisions), ROADMAP.md (3 success criteria), REQUIREMENTS.md (VOICE-01..03), codebase survey (ChatInputBar.kt, ChatScreen.kt, Color.kt, Theme.kt, WarpedAlertDialog)

## UI Considerations

Single-button feature. No new design language, no new screens, no new type roles, no new color tokens. Every element reuses an existing component or pattern.

### 1. Mic button placement (ChatInputBar Row 2, right group)

- **Location:** Row 2 right group, immediately LEFT of the send/stop slot (`ui/chat/components/ChatInputBar.kt` lines 168–191). Mic and send/stop coexist — mic never replaces send.
- **Component:** `IconButton(onClick, modifier = Modifier.size(40.dp))` + `Icon(..., modifier = Modifier.size(24.dp))` — identical sizing to the existing image/send/stop buttons.
- **Idle icon:** `Icons.Filled.Mic`, tint `Color.White.copy(alpha = 0.6f)` (matches idle image-button tint), content-description `R.string.cd_dictate` (new EN "Dictate" / ES "Dictar").
- **Enabled rule:** same gate as the text field — disabled while `isGenerating`; otherwise always tappable (empty or non-empty draft).
- **Visibility rule (VOICE-03):** mic is rendered ONLY when `speechAvailable == true` (platform `SpeechRecognizer.isRecognitionAvailable()`). When false → button omitted entirely (`if` guard, same pattern as `modelHasVision` image button, line 138). No placeholder, no dimmed state — no dead affordance.
- **New params on ChatInputBar:** `speechAvailable: Boolean = false`, `isListening: Boolean = false`, `onMicClick: () -> Unit = {}`. Defaults keep all existing call sites compiling.

### 2. Listening state (stop toggle + indicator)

- **Icon toggle:** when `isListening == true`, the mic `IconButton` swaps to `Icons.Filled.Stop` with tint `Color.White` full-opacity (same stop affordance as the generation-stop button, line 170 — `Icons.Filled.Stop` already imported in this file, zero new icon dependency). Content-description switches to `R.string.cd_stop_listening` (EN "Stop listening" / ES "Dejar de escuchar").
- **Listening indicator:** tint the mic container with the accent — `IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))`, reusing the exact thinking-toggle active-pill treatment (lines 150–152, same alpha, same `primary` = `WarpedAccent #D97757`). No pulsing animation, no waveform, no new composable.
- **Tap semantics:** tap mic to start, tap stop-icon to stop (locked decision). Generation-stop (`onStop`, `isGenerating`) takes precedence: while generating, the right slot shows generation-stop as today; mic is hidden or disabled in that state (executor's call — contract requires no two stop icons visible at once).
- **Auto-stop on silence** is platform behavior — no UI for it.

### 3. Partial-result display in input

- **No new surface.** Partial results stream into the existing `OutlinedTextField` value (Row 1) via the existing `onTextChange` path — user sees words appear as they speak, fully editable, cursor preserved.
- **Append-at-cursor:** final + partial text appends at cursor into the existing draft — never replaces, never auto-sends. Placeholder stays `R.string.type_message`.
- **No error toast on recognition failure:** recognizer errors (network, no-speech, timeout) fail silent — draft keeps whatever partial text arrived. No error copy, no error color. Rationale: dictation is best-effort assistance, not a blocking flow.

### 4. Rationale UI shape (first-tap, VOICE-02)

- **Component:** `WarpedAlertDialog` (`ui/components/WarpedAlertDialog.kt`) — the same dialog used by all 19 existing call sites (model switch, delete confirm, image picker). Standard confirm/dismiss slot signature.
- **Trigger:** first mic tap when `RECORD_AUDIO` not yet granted. Title `R.string.voice_rationale_title` (EN "Voice dictation" / ES "Dictado por voz"), body `R.string.voice_rationale_body` (EN "Allow microphone access to dictate messages. Your speech is processed by the system's speech service." / ES "Permite el acceso al micrófono para dictar mensajes. Tu voz la procesa el servicio de voz del sistema."). Confirm `R.string.voice_rationale_allow` (EN "Allow" / ES "Permitir") → fires the system permission request; dismiss `R.string.dismiss` (existing string, reused).
- **No custom layout.** Title + body + two buttons, dialog width and type roles per WarpedAlertDialog defaults.

### 5. Denial Snackbar + Settings escape (VOICE-02)

- **Component:** existing `SnackbarHost(hostState = snackbarHostState)` in `ChatScreen.kt` (line 280) driven by the `ChatEvent.Snackbar` channel (lines 189–190) — same channel Phase 64 used for the HuggingFace error path. No new host, no overlay banner.
- **Copy:** `R.string.voice_denied` — EN "Microphone blocked. Enable it in Settings to dictate." / ES "Micrófono bloqueado. Actívalo en Ajustes para dictar." Duration `SnackbarDuration.Long` (needs reading time + action tap; Short is 4s, insufficient).
- **Action:** Snackbar action label `R.string.voice_open_settings` (EN "Settings" / ES "Ajustes") → fires `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` deep-link intent (package URI). Trigger condition: permanent denial only (`shouldShowRequestPermissionRationale == false` after denial). Transient denial → silent, no Snackbar.
- **Trigger wiring:** denial event flows through `ChatEvent.Snackbar`-equivalent UiState event with action callback — executor extends the existing event, does not invent a second Snackbar mechanism.

### 6. Motion, a11y, dark-mode

- **Motion:** zero animation. Icon swap is instant (same as send↔stop swap today). Partial text appears at recognizer cadence — no debounce, no shimmer.
- **Dark-mode:** all tints derive from `MaterialTheme.colorScheme.primary` + white-alpha overlays already used in this bar — works in both schemes with no hardcoded branch. Bar surface stays `Color(0xFF2B2B29)` (`OgCardDark`) untouched.
- **Touch target:** 40.dp IconButton (matches siblings; executor notes existing 40.dp vs 48.dp guideline deviation is pre-existing bar convention, not this phase's to change).
- **TalkBack:** `cd_dictate` / `cd_stop_listening` content-descriptions; listening state announced via content-description change only.

## Contract Summary

| Token | Value (prescriptive) |
|---|---|
| Spacing | Reuse bar values only: 8/10/24.dp, 40.dp button, 24.dp icon. No new spacing. |
| Typography | Zero new roles. Dialog text via WarpedAlertDialog defaults; new strings are plain body copy. (Phase 64 checker constraint: 5 surveyed M3 roles only.) |
| Color | 60/30/10 unchanged. Accent `primary` (#D97757 both schemes) reserved for: (a) listening container tint @0.5 alpha, (b) rationale confirm button (dialog default). Snackbar uses default surface. No error-red anywhere in this phase. |
| Icons | `Icons.Filled.Mic` (idle) + `Icons.Filled.Stop` (listening, already imported). No new icon dependency. |
| Copy (EN+ES) | 5 new strings: `cd_dictate`, `cd_stop_listening`, `voice_rationale_title`, `voice_rationale_body`, `voice_rationale_allow`, `voice_denied`, `voice_open_settings` (7 keys); `dismiss` reused. |
| States | idle-mic / listening-stop / hidden (no recognizer) / generating (mic yields to generation-stop) / rationale dialog / denial Snackbar / silent recognition-error |

## Non-Goals (explicitly out of contract)

Audio messages, waveform UI, language picker (system locale), offline-STT engines, new destinations, certificate/pinning changes, `modelHasAudio` prop (pre-existing dead audio param in ChatInputBar — untouched by this phase).
