# Phase 65: Voice Dictation - Context

**Gathered:** 2026-10-02
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous, 3 areas accepted by user)

<domain>
## Phase Boundary

Add voice dictation to the chat input: user taps a mic button, speaks, and recognized text lands editable in the input without auto-sending. First-tap permission flow (rationale + request, Settings escape on permanent denial). Devices without speech recognition get a graceful fallback (mic hidden, no crash). Recognizer lifecycle is tied to the UI lifecycle. No audio messages — dictation only.

</domain>

<decisions>
## Implementation Decisions

### Recognition approach
- Platform `SpeechRecognizer` with `RecognitionListener` — in-UI experience, no separate recognizer dialog.
- Show partial results live in the input; commit final text editable.
- Recognition language follows the system locale (platform handles it).

### Permission + fallback
- First mic tap shows an in-context rationale, then the system RECORD_AUDIO request; permanent denial gets a Snackbar with a Settings escape.
- Hide the mic button entirely on devices without speech recognition (no dead affordance).
- Tap mic to start, tap again to stop; auto-stop on silence (platform behavior).

### Input integration
- Recognized text appends at the cursor into the existing draft — never replaces, never auto-sends. User reviews and sends manually.
- No audio messages — voice is dictation only, text lands editable.
- Mic icon toggles to a stop state with a subtle listening indicator, reusing existing components.

### the agent's Discretion
- Exact wrapper shape (ViewModel-held helper vs composable-scoped manager) — follow codebase lifecycle conventions; recognizer must be destroyed with the UI lifecycle.
- Exact rationale/Snackbar copy (EN+ES) and listening-indicator visuals — keep minimal, reuse existing components.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ui/chat/components/ChatInputBar.kt` — mic button home; check current trailing-icon layout.
- `ui/chat/ChatScreen.kt` + `ChatViewModel.kt` + `ChatUiState.kt` — input state ownership; draft text lives here.
- `ui/components/` Snackbar patterns (Phase 64 added a SnackbarHost error channel in HuggingFaceScreen — replicate the pattern for permission denial).
- No existing speech infra (grep for SpeechRecognizer/RecognizerIntent/RECORD_AUDIO in app/src/main returns no implementation hits) — greenfield wrapper needed.
- `AndroidManifest.xml` — needs `RECORD_AUDIO` declaration (currently INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE*, POST_NOTIFICATIONS only).

### Established Patterns
- Permission flows: check how POST_NOTIFICATIONS (or other runtime permissions) are requested in-app, if at all — replicate or establish the Accompanist-permissions vs ActivityResultLauncher convention.
- EN+ES string pairs required for rationale, denial Snackbar, Settings action.
- Error/UX channels via UiState + SnackbarHost.

### Integration Points
- ChatInputBar trailing actions (send button, mic addition, stop state).
- ChatViewModel input-text mutation (append-at-cursor helper).
- NavGraph not involved (no new destination); Settings app-page deep-link via intent for the denial escape.

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches. Success criteria are the contract: editable dictated text, rationale + permission + Settings escape, graceful no-recognizer fallback.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
