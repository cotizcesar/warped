# Phase 69: Voice Differentiation + Gating + Transcript - Context

**Gathered:** 2026-10-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Users can tell voice-send apart from dictation (distinct icons with mutual exclusion), understand when voice-send is unavailable (text-only models and remote endpoints get disabled-with-reason affordances — never dead buttons, never silent), and see a transcript caption under their own voice bubbles (parallel on-device STT during recording, duration-only fallback when STT is unavailable). Builds on Phases 67–68 (recorder, draft, bubbles, Room transcript placeholder column). The biggest v3.1 phase: 4 requirements (VMSG-03/04/07/08).
</domain>

<decisions>
## Implementation Decisions

### Icon Differentiation
- Keep the GraphicEq waveform glyph for voice-send with a first-use coachmark ("Voice message"); the Mic icon stays dictation-only — continuity with 67/68, zero asset churn
- Starting one input mode stops the other (mutual exclusion); both buttons reflect the active mode
- TalkBack descriptions stay distinct ("Record voice message" vs "Dictate text"); no full onboarding tour

### Text-Only Model Gating
- Disabled voice button with reason (reduced opacity + "Needs audio model" hint) — never a dead button, never hidden (discoverability)
- Tapping the disabled button shows an explainer (Snackbar/bottom-sheet: switch to an audio model) with a link to the model catalog
- Gate evaluates on model select + on chat open via allowlist `capabilities.audio`; flips live on model switch
- Model switch with an in-progress draft keeps the draft but blocks send with the reason (draft is model-independent until sent)

### Remote Endpoint Gating
- Same disabled-with-reason pattern as text-only; reason names remote ("Voice messages stay on this device for now") — consistent gating language
- One-line reason + "Learn more" opening a short Help section on voice (EN+ES)
- Draft kept on remote endpoints too; send blocked with reason (never silently dropped, never attempted)
- Gate helper keyed on provider type + audio capability so remote voice-send can unlock later without UI rework (no hardcoded local-only checks at call sites)

### Transcript Captions
- Parallel on-device STT (existing VoiceDictationManager/SpeechRecognizer) during recording; transcript attaches to the bubble on send
- Small caption under own voice bubbles (onSurfaceVariant, 2-line max + expand); fills the Room transcript column (Phase 68 placeholder) so captions survive restarts
- STT-unavailable fallback is a duration-only caption ("0:12 voice message") — honest, never empty, never a fake transcript; voice-send is never blocked by missing STT
- STT follows the device locale (Phase 65 pattern); caption stored as-is, no translation

### the agent's Discretion
- Exact coachmark copy/shape, explainer surface (Snackbar vs bottom-sheet), and caption typography within existing Help/Snackbar/bubble conventions — follow 67/68 UI-SPECs and Material 3.
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- Phases 67–68 output: GraphicEq voice button + Mic dictation button in `ChatInputBar.kt`; minimal guard toasts (`voice_msg_remote_blocked`, text-only backstop) in `ChatScreen.kt`/`ChatViewModel.kt`; `ModelAllowlistRepository.capabilities.audio`; Room `transcript` placeholder column (migration 17→18); voice bubbles with duration readouts
- `ui/chat/voice/VoiceDictationManager.kt` — platform STT wrapper (partial/final/error, device-locale, VM-owned, destroy in onCleared) for the parallel-transcript path
- Help screen (Phase 64 EN+ES rewrite) — landing spot for the voice "Learn more" section
- Model catalog + Use-in-Chat wiring (Phase 64) — link target from the text-only explainer

### Established Patterns
- Disabled-with-reason over hidden affordances; Snackbar + Settings-escape error channels (Phase 65); allowlist capability checks; EN+ES strings for all user copy
- VM-owned platform wrappers; zero new Gradle deps (platform SpeechRecognizer only — already integrated)

### Integration Points
- Gate helper ← provider type (local vs remote) + `capabilities.audio` → voice button enabled state + explainer copy in chat input row
- Parallel STT session ← recording start/stop (mutual exclusion with dictation mode) → transcript field on send → Room `transcript` column → caption under own voice bubbles
</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches within the 67/68 button, Snackbar, and bubble conventions. Keep the gate helper provider-keyed (not hardcoded) so a future milestone can unlock remote voice-send without UI rework.
</specifics>

<deferred>
## Deferred Ideas

- Waveform artwork in bubbles/draft — future VF-02, not v3.1
- Received/model-side audio + captions — future VF-03, not v3.1
- Remote voice-send itself — local-only at launch per milestone research; gate helper keeps the unlock path open
- Full onboarding tour for voice — declined (first-use coachmark only)
</deferred>
