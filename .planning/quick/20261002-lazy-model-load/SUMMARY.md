# Summary: lazy model load on first send (20261002-lazy-model-load)

**Status:** implemented — gradle verification BLOCKED by foreign uncommitted edit
**Commits:** 3b08f4f0, 3cf6d3fc, 9e37e1a8, e93d23be (all on main)

## Goal

Remove eager model loading. Selecting a model only marks it pending — the
engine mounts on the first send, then generates. No loading state on
selection; input stays enabled; traffic light shows selected-not-loaded
until the first send. Locked design in PLAN.md followed exactly.

## What changed

**`ActiveModelSelection` (+ new `ActiveModelSelectionTest`)**
- `LocalSelection` gains `isLoading: Boolean = false`.
- NEW `selectLocalPending(modelId)` → selected, `isLoading=false`.
- `markLocalLoading` → `isLoading=true` (load START signal, send path only).
- `connectLocal` / `markLocalDisconnected` → `isLoading=false`.
- Restore rehydrates with `isLoading=false` (default — never stuck loading).
- Pending local claims no active model; connected local wins as before.

**`ChatViewModel`**
- Selection collector: `isLoadingModel = local.isLoading` (derivation removed).
- `setSelectedModel` local branch, auto-pick, conversation-open:
  `selectLocalPending`, all eager `preloadLocalModel` launches deleted.
- `preloadLocalModel`: selection-driven only; direct
  `isLoadingModel`/`loadingModelName` writes removed (collector owns them);
  `modelLoadError` writes kept; failure catch now `markLocalDisconnected`
  (keeps selection for retry) instead of `disconnectLocal`.
- `sendMessage` generationJob, before `ensureConversation`/persist: local
  + engine-path mismatch → `markLocalLoading` + `preloadLocalModel`, then
  re-check engine — still mismatched → transcript error + return (optimistic
  message removed, `inputText` restored = draft kept, nothing persisted).
  Missing file → `DownloadModelFirst` (same shape, now pre-persist).
  Remote path byte-identical. Old post-save file-exists block removed
  (subsumed).
- Kept: local→remote explicit unload, `unloadLocalModels()` on new
  conversation, smart-preset on justConnected, memory guard in preload,
  `refreshActiveBackend` (comment updated to the flag derivation).

**`ChatUiState`** (audit finding, Rule 2)
- `trafficLightState`: pending local (selected, not loaded) is RED
  "Local: X — Not connected" (existing copy, no new strings) instead of
  GRAY "No model selected" while the input is enabled. GREEN still requires
  loaded.

**`CatalogViewModel.useDownloadedModel` / `ModelsViewModel.useLocalModel`**
- `connectLocal` → `selectLocalPending` (activation marks pending).

**Tests**
- New `ActiveModelSelectionTest` (7 tests: pending/loading/connect/
  disconnect/restore/active-model).
- `ModelSwitchUnloadTest` rewritten for lazy semantics: selection never
  touches engine; first send mounts then generates; second send skips mount;
  memory-blocked / switch-failed / missing-file sends surface errors with
  draft kept + zero persistence; local→remote keeps explicit unload.
- `ChatLoadingFlagHealTest`: pending shows no spinner; restore rehydrates
  pending (never stuck loading).
- `ModelActivationNewChatTest` + new catalog test expect
  `selectLocalPending`, never `connectLocal`.
- 15 send-path fixtures stub `getActiveEngine()` as serving the selected
  model (connected steady state → lazy mount skipped).

## Verification (BLOCKED — not green, not run)

- `./gradlew :app:compileDebugKotlin` FAILS on a syntax error in
  `app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt:152`
  (`{ event ->,` — trailing comma), which is the orchestrator's
  UNCOMMITTED cursor work. Hard constraint forbids touching that file, so
  the error cannot be fixed from this task and module compilation (hence
  `compileDebugUnitTestKotlin` and `testDebugUnitTest`) cannot proceed.
- Fix required (orchestrator, ~10 seconds): correct line 152 to
  `.onKeyEvent { event ->`, then run:
  `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --offline`
  `./gradlew :app:testDebugUnitTest --offline` (expect 0 failures)
- Non-compile checks that DID pass:
  - `preloadLocalModel` call sites: send path only (ChatViewModel:525).
  - `markLocalLoading` sites: send path only (ChatViewModel:524).
  - No `switchToLiteRT` on any selection path (only preload + provider init).
  - No `res/` changes (EN+ES parity intact, zero new strings).
  - `ChatInputBar.kt` untouched (verified via git status).
  - Mock/API signatures hand-verified against prod sources
    (KeystoreManager, AllowlistedModel, EngineManager, ChatMessage.id).

## Deviations from plan

None — locked design followed exactly, plus one Rule-2 audit fix
(traffic-light pending state, `ChatUiState.kt`, using existing copy).
`UnifiedSelectorViewModel` audited: only disconnect/remote paths, no change
needed. `Benchmark`/`PromptLab`: no shared selection paths touched.

## Commits

- `3b08f4f0` feat: pending selection with isLoading flag (+ test)
- `3cf6d3fc` feat: lazy engine mount on first send (4 prod files)
- `9e37e1a8` test: lazy expectations for selection and send
- `e93d23be` test: engine-as-serving stubs in 15 send fixtures

## For the orchestrator

1. Fix the `ChatInputBar.kt:152` typo in your uncommitted work.
2. Run the two gradle commands above.
3. If `ModelSwitchUnloadTest` success/failure tests flake: they hop to
   `Dispatchers.Default` inside `preloadLocalModel` (pre-existing prod
   shape); the tests yield up to ~1s for the mount — increase attempts if
   CI is slow.
4. Do NOT update STATE.md/ROADMAP.md from here (yours to own).
