# Quick: lazy model load on first send

**Created:** 2026-10-02
**Status:** in-progress
**Mode:** quick (user-directed: "Select > Prompt > carga model > Respuesta")

## Goal

Remove eager model loading. Selecting a model only marks it pending — the
engine loads on the first send, then generates. No "loading model" state on
selection; input stays enabled; traffic light shows selected-not-loaded
until the first send.

## Design (locked)

- `ActiveModelSelection.LocalSelection` gains `isLoading: Boolean = false`.
- `markLocalLoading(modelId)` → `isLoading=true` (load START signal only).
- NEW `selectLocalPending(modelId)` → selected, `isLoading=false`.
- `connectLocal` / `markLocalDisconnected` → `isLoading=false`.
- Restore path rehydrates with `isLoading=false` (never stuck loading).
- `ChatViewModel` selection collector: `isLoadingModel = local.isLoading`
  (replaces `modelId != null && !connected` derivation).
- `preloadLocalModel` drives ONLY via ActiveModelSelection
  (markLocalLoading → connectLocal / markLocalDisconnected); remove its
  direct `isLoadingModel`/`loadingModelName` writes (collector owns them);
  keep `modelLoadError` writes. Failure catch uses `markLocalDisconnected`
  (keeps selection for retry) instead of `disconnectLocal`.
- Selection sites switch mark→pending, delete eager preload launches:
  `setSelectedModel` local branch, auto-pick (~268), conversation open
  (~1449 + suspend preload ~1467), `CatalogViewModel.useDownloadedModel`
  (connectLocal → selectLocalPending), `ModelsViewModel.useLocalModel`
  (same).
- Send path (`sendMessage` generationJob, after NoModel guards, before
  persisting): if local selected and
  `engineManager.getActiveEngine()?.modelPath != selectedId` → 
  `markLocalLoading` + `preloadLocalModel(selectedId)`; afterwards re-check
  engine — if still not loaded, transcript error + return (draft kept,
  nothing persisted). Remote path untouched. Reuse existing
  `ModelLoadingIndicator` + `isLoadingModel` input lock (no new UI).
- Keep: explicit unload on local→remote switch, `unloadLocalModels()` on
  new conversation, smart-preset on justConnected, memory guard inside
  preload (error surfaces via send-path check), `refreshActiveBackend`.
- Audit every `localSelection`/`isConnected`/`isLoadingModel` reader for
  pending-semantics (known: ChatViewModel:263,306,902,1464,1770,1824,1860,
  1998-2022,2025-2066,2253; ChatScreen:404,438,461; ChatUiState:124,192,243,
  316+; ModelsVM:120). Benchmark/PromptLab out of scope unless they share
  these paths (note if touched).

## Files (expected)

- app/src/main/java/com/warped/domain/model/ActiveModelSelection.kt
- app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
- app/src/main/java/com/warped/ui/huggingface/CatalogViewModel.kt
- app/src/main/java/com/warped/ui/models/ModelsViewModel.kt
- Tests: ActiveModelSelectionTest?, ChatViewModel selection/send tests,
  catalog/models VM tests (update eager-load expectations → lazy)

## Out of scope

- ChatInputBar cursor tweak (done inline by orchestrator, uncommitted —
  DO NOT touch ChatInputBar.kt).
- New UI components, traffic-light redesign, benchmark changes.

## Verify

- `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --offline`
- `./gradlew :app:testDebugUnitTest --offline` — 0 failures
- No new strings (EN+ES parity untouched)
- grep: no selection-time `preloadLocalModel` call outside send path +
  `markLocalLoading` Sites = send-path only
