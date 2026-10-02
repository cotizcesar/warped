# Summary: lazy model load on first send

**Status:** complete
**Date:** 2026-10-02

## Behavior

Select > Prompt > carga > Respuesta. Selecting a model only marks it
pending — no engine load, no "loading model", input stays enabled. The
engine mounts on the first send, then the turn generates. Traffic light
shows selected-not-loaded (existing RED "Not connected" copy) until then.

## Implementation (5 executor commits + 1 test-stability fix)

- `ActiveModelSelection.LocalSelection` gains `isLoading`; new
  `selectLocalPending`; `markLocalLoading` = load-START signal (send path
  only); collector derives `isLoadingModel = local.isLoading`.
- `preloadLocalModel` selection-driven only; failure keeps selection
  (`markLocalDisconnected`) for retry.
- Send path: missing file → `DownloadModelFirst`; engine mismatch →
  load → re-check → transcript error + draft kept + nothing persisted.
- Selection sites (setSelectedModel, auto-pick, conversation open,
  catalog + models activation) mark pending, zero engine touches.
- Kept: local→remote explicit unload, unload-on-new-conversation,
  smart-preset on connect, memory guard, refreshActiveBackend.
- Orchestrator follow-ups: `ModelSwitchUnloadTest` stability (TestScope
  import, MockK timeout verify for the `Dispatchers.Default` unload hop,
  `confirmModelSwitch` before asserting mid-conversation switch).

## Tests

- New: `ActiveModelSelectionTest` (7), `ModelSwitchUnloadTest` (lazy
  semantics), `ChatLoadingFlagHealTest` updates, activation/catalog updates.
- Full suite: **933 tests, 0 failures, 0 errors, 0 skipped**.
- No new strings → EN+ES parity untouched. Remote paths byte-identical.

## Files

- app/src/main/java/com/warped/domain/model/ActiveModelSelection.kt
- app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
- app/src/main/java/com/warped/ui/chat/ChatUiState.kt
- app/src/main/java/com/warped/ui/huggingface/CatalogViewModel.kt
- app/src/main/java/com/warped/ui/models/ModelsViewModel.kt
- Tests: ActiveModelSelectionTest, ModelSwitchUnloadTest,
  ChatLoadingFlagHealTest, ModelActivationNewChatTest, catalog tests,
  15 send fixtures (engine-as-serving stubs)
