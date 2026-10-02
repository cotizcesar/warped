# Phase 69: Voice Differentiation + Gating + Transcript - Research

**Researched:** 2026-10-02
**Status:** Ready for planning

## Summary

Phase 69 builds on locked Phase 67/68 output. Codebase inspection confirms most
of the assumed foundation already exists: mutual exclusion between dictation and
voice recording is wired in **both** directions, `modelHasAudio` is already a
`ChatInputBar` parameter (currently unwired to the voice button), the Room
`transcript` column plus entity/mapper plumbing exists write-never/read-never,
and the catalog deep link (`onNavigateToCatalog`) already reaches `ChatScreen`.
The genuinely new work is: a provider-keyed gate helper, the
disabled-with-reason button surface, a **second** STT session running parallel
to recording for transcript capture, transcript stamping → caption rendering,
the first-use coachmark flag, and the Help voice subsection.

## Research Questions

### Q1: Dictation-vs-record session interplay — what exists, what is missing?

**Mutual exclusion (record vs dictation) already exists both directions:**

- `ChatViewModel.startDictation()` stops an active voice recording first
  (`ChatViewModel.kt:1700-1702` — "starting dictation stops an active voice
  recording (keeps the clip for send)").
- `ChatViewModel.startVoiceRecording()` calls `stopDictation()` first
  (`ChatViewModel.kt:1940-1942`).
- Screen-level defense in depth: `ChatScreen.onSendMessage` stops dictation
  before sending (`ChatScreen.kt:533`); `sendMessage` also calls
  `stopDictation()` (`ChatViewModel.kt:474`).

**What is missing — the parallel transcript STT session (VMSG-07):**

- `VoiceDictationManager` (`ui/chat/voice/VoiceDictationManager.kt`) is a thin
  platform `SpeechRecognizer` wrapper: single-shot, device-locale
  (`Locale.getDefault()`), partial via `onPartial` / final via `onFinal`,
  errors as codes with zero UI side effects, VM-owned, `destroy()` in
  `onCleared` (`ChatViewModel.kt:3094-3095`). Instantiated lazily via
  `getDictationManager()` with a `dictationManagerOverride` test seam
  (`ChatViewModel.kt:1671-1684`).
- Only **one** manager instance exists today (dictation mode). The transcript
  path needs a **second** instance owned by the VM (`transcriptManager` +
  override seam, same holder discipline), started when recording goes live and
  stopped on every stop path (manual stop, auto-stop, cancel, background
  auto-stop, spin-up pending-stop). Its partials/finals accumulate into a
  transcript buffer (not into `inputText` — the dictation callbacks must stay
  dictation-only).
- Mutual-exclusion extension: starting the transcript session must never run
  concurrently with a dictation session. This holds by construction because
  `startVoiceRecording()` already stops dictation first — but the planner must
  keep that ordering (stop dictation → start recorder → start transcript STT)
  and stop the transcript session inside `stopDictation()` callers' mirror
  (i.e. any path that ends recording ends the transcript session too).
- Platform risk: two `SpeechRecognizer` instances in one process can return
  `ERROR_RECOGNIZER_BUSY` if overlapped. Mitigation: strictly sequential
  lifecycle (dictation stopped before transcript STT starts; transcript STT
  stopped before any dictation restart) plus the existing availability probe
  (`isAvailable()`, resolved off-main at init per Phase 65) — when the
  transcript session errors or is unavailable, the transcript is NULL and the
  duration-only fallback renders. Voice-send is **never** blocked by STT state.
- **RESOLVED:** Reuse `VoiceDictationManager` as-is for the second session
  (zero new Gradle deps — hard constraint). No wrapper changes needed; the VM
  owns session lifecycle and buffering.

### Q2: Provider-type detection for the gate helper — where does the signal live?

- `ChatScreen.onVoiceClick` (`ChatScreen.kt:407-435`) already computes both
  gate signals inline: `audioCapable` via
  `viewModel.verifiedLocalCapabilities(localId)?.audio ?: true` (fail-open
  null) and `remoteSelected` via
  `localId == null && connection.selectedRemoteModelId != null`.
- `verifiedLocalCapabilities(filePath)` (`ChatViewModel.kt:2676-2681`) uses the
  allowlist verified-only path (`ModelAllowlistRepository.effectiveCapabilities`)
  — never raw `LocalModel.capabilities`. The canonical local-vs-remote
  discriminator used at send time is
  `state.selectedLocalModelId?.let { ProviderType.LITE_RT_LM } ?: state.selectedRemoteProvider`
  (`ChatViewModel.kt:478-479`).
- `ProviderType` (`domain/model/ProviderType.kt:6`): OPENAI, ANTHROPIC, OLLAMA,
  LM_STUDIO, CUSTOM, LOCAL (deprecated), LITE_RT_LM. Local == LITE_RT_LM.
- Connection state (`ChatUiState.kt:177-179`) carries `selectedLocalModelId`,
  `selectedRemoteModelId`, `selectedRemoteProvider` — everything the gate needs
  is already in `connectionState`.
- **RESOLVED:** New pure-Kotlin helper `VoiceSendGate`
  (`ui/chat/voice/VoiceSendGate.kt`, no Android imports — unit-testable on JVM):
  `fun evaluate(providerType: ProviderType?, localAudioCapable: Boolean?): GateState`
  with `sealed interface GateState { Allowed, GatedTextOnly, GatedRemote }`.
  Local + audio → Allowed; local + audio==false → GatedTextOnly; any non-local
  provider → GatedRemote (remote check first — remote wins even if a stale
  local id lingers); null provider / unknown → Allowed (fail-open, matches the
  existing `?: true` convention). Call sites (`ChatScreen`, VM send-block) map
  connection state → `providerType` once and call the helper — **no hardcoded
  local-only checks at call sites**, so unlocking remote later means changing
  the helper, not the UI.
- The VM should expose the evaluated gate as a derived `StateFlow`
  (`combine(connectionState, ...)` → GateState) so the button, hint, and
  send-block all read one source and flip live on model switch (CONTEXT: gate
  evaluates on model select + chat open, flips live).

### Q3: Help screen conventions (Phase 64) — how to add the voice subsection?

- `HelpScreen` (`ui/help/HelpScreen.kt:29-188`) is a `Scaffold` + `LazyColumn`
  of `HelpSection(icon, title, steps)` cards (8 numbered sections, hardcoded
  warm palette `#2B2B29` / `#D97757`). Strings are `help_sN_title` +
  `help_sN_stepK` in `values/strings.xml` + `values-es` (Phase 64 EN+ES rewrite).
- **RESOLVED:** Add Section 9 (voice) with the existing `HelpSection`
  composable unchanged: icon `Icons.Filled.GraphicEq` (matches the voice-send
  glyph family), title + 3–4 steps from the UI-SPEC Help copy (voice vs
  dictation, 60 s cap + 30 s model note reused from Phase 67 copy, local-only
  limitation). New keys `help_s9_title` + `help_s9_step1..4` in both locales.
  Reach via "Learn more" from the remote explainer: `ChatScreen` needs a new
  `onNavigateToHelp: () -> Unit = {}` parameter wired in `NavGraph` at all
  three `ChatScreen` call sites (mirrors `onNavigateToCatalog` →
  `navController.navigate(...)`; Help route already exists at
  `NavGraph.kt:466`).
- Explainer surface (agent's discretion per CONTEXT): Snackbar with action via
  the existing `SnackbarHost` channel (Phase 65 Snackbar + escape precedent).
  Text-only explainer action "View models" → existing `onNavigateToCatalog`;
  remote explainer action "Learn more" → new `onNavigateToHelp`.

### Q4: DataStore first-use flag pattern (coachmark per-install flag)?

- Precedent: `WizardPreferences` (`data/local/preferences/WizardPreferences.kt`)
  — `@Singleton` + `@Inject` + `@ApplicationContext`, private
  `preferencesDataStore(name = "...")` delegate, `booleanPreferencesKey`,
  `Flow<Boolean>` read with default, `suspend edit {}` write. Same shape in
  `ReviewPreferences` and `AdvancedPreferences`.
- **RESOLVED:** New `VoicePreferences` (`data/local/preferences/VoicePreferences.kt`)
  with store name `voice_preferences`, key `voice_coachmark_seen` (default
  false = show). VM exposes `showVoiceCoachmark: StateFlow<Boolean>`
  (seen==false AND voice button enabled AND gate allows — coachmark never
  shows on a gated button); any first interaction (voice tap, mic tap, outside
  tap) calls `vm.dismissVoiceCoachmark()` which persists seen=true. One-shot,
  never re-shows. Render with Material3 `PlainTooltip` anchored at the voice
  button in `ChatInputBar` (Compose BOM — zero new deps).

## Locked Constraints (from Phase 67/68 artifacts — build ON, do not re-decide)

- Iconography: GraphicEq = voice-send, Mic = dictation-only; glyphs never swap
  (`ChatInputBar.kt:332-365`). TalkBack "Record voice message"
  (`voice_msg_record`) vs "Dictate text" (`cd_dictate`) stay distinct.
- Active-mode signal is tint-only (`colorScheme.primary` on the active button,
  default `onSurfaceVariant` otherwise); no background recolor, no pulsing
  (68-UI-SPEC / 69-UI-SPEC active-mode rule).
- 60 s cap, send-first-30 s transcode, `voice_msg_first_30s` note, sub-1 s
  rejection, draft lifecycle (rotation keeps, background auto-stops + keeps)
  — all locked, untouched.
- Room: `messages.transcript TEXT` nullable (migration 17→18 shipped),
  `MessageEntity.transcript` + `ChatMessage.transcript` exist write-never /
  render-never (`ChatMessage.kt:30-36`, `MessageEntity.kt:32-36`,
  `EntityMappers.kt:40-58`). Phase 69 only **writes at send time** and
  **renders** — no new migration, no schema change.
- Send-time stamping precedent: `lastSentVoicePath` / `lastSentVoiceDurationMs`
  holders consumed in `sendMessage` user-Message construction
  (`ChatViewModel.kt:498-510`). Transcript follows the identical holder pattern
  (`lastSentVoiceTranscript: String?`), stamped onto `ChatMessage.transcript`
  so the persisted row carries it; captions hydrate from history load for free.
- Bubble chrome: `VoicePlayerRow` (`MessageBubble.kt:715-792`) — caption slots
  **underneath** the player row (68-03 summary forward-compat lock). Duration
  convention (total m:ss, `tnum`) unchanged.
- Zero new Gradle dependencies (platform `SpeechRecognizer` only — already
  integrated). Kotlin only, never block UI thread, VM-owned wrappers.
- Test seams: `voiceRecorderOverride` / `voicePlayerOverride` /
  `dictationManagerOverride` / `voiceDurationReader` — transcript manager gets
  the same override seam. Unit tests live in
  `app/src/test/java/com/warped/ui/chat/` (`VoiceMessageGuardTest`,
  `VoiceHistoryPlaybackTest`, `VoiceDraftGuardTest` precedents).
- Verify commands (proven, reuse verbatim):
  `./gradlew :app:testDebugUnitTest --tests "<class>"`, full
  `./gradlew :app:testDebugUnitTest`, `./gradlew :app:assembleDebug`,
  hardcoded-copy grep for new user strings in main sources.
- Existing guard toasts (`voice_msg_remote_blocked`, `error_no_audio` in
  `ChatScreen.kt:407-435`) are **replaced** by the gate-helper + explainer
  path — the strings can be retired or repurposed (planner: keep
  `voice_msg_remote_blocked` only if the explainer reuses its copy; otherwise
  delete to avoid dead strings — note EN+ES parity either way).

## Open Questions (RESOLVED)

1. **Reuse VoiceDictationManager for parallel STT?** — RESOLVED: yes, second
   instance, VM-owned, same holder/seam discipline. No wrapper changes.
2. **Gate signal source?** — RESOLVED: `VoiceSendGate.evaluate(providerType,
   localAudioCapable)` pure helper; VM exposes derived `StateFlow<GateState>`.
3. **Explainer surface?** — RESOLVED (recommendation, executor's discretion):
   Snackbar with action via existing `SnackbarHost` ("View models" →
   `onNavigateToCatalog`, "Learn more" → new `onNavigateToHelp`).
4. **Coachmark storage?** — RESOLVED: new `VoicePreferences` DataStore store,
   `voice_coachmark_seen` boolean, `PlainTooltip` render.
5. **Transcript locale?** — RESOLVED: device locale via existing manager
   (`EXTRA_LANGUAGE = Locale.getDefault()`, Phase 65 pattern); stored as-is,
   no translation.
6. **STT failure semantics?** — RESOLVED: NULL transcript → duration-only
   fallback caption; voice-send never blocked by STT state.
