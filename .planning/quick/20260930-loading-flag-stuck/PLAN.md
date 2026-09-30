# PLAN — Stuck "Loading …litertlm" indicator (loading-flag-stuck)

## Objective

Fix the stuck `ModelLoadingIndicator` row: on device, a local model shows green dot + working chat, yet the
"Loading …litertlm" row never clears. Root cause is verified in code (see Context): `isLoadingModel` stays
`true` because `localSelection.connected` never becomes `true` for the selected model, even though the engine
is loaded and serving chat. One minimal heal + regression tests.

## Context (planner-verified in code — supersedes the brief's line numbers)

- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:217-244` — `localSelection` collector. It **already**
  uses loading semantics: line 221 `val loading = modelId != null && !connected`, line 235
  `isLoadingModel = loading`. The brief's described `isLoadingModel = connected` line **does not exist** in
  the current revision — do NOT "fix" line 235; it is already correct. Keep it byte-identical.
- Only writers of `isLoadingModel` (verified via grep): collector line 235, preload lines 1714/1720/1724.
  `updateConnection` is `_connection.update(op)` (line 125, atomic) — no lost-update race.
- Stuck mechanism (matches on-device evidence exactly):
  1. `markLocalLoading(modelId)` (`ActiveModelSelection.kt:81-85`) emits `LocalSelection(modelId, connected=false)`
     → collector sets `isLoadingModel = true`. Callers: `ChatViewModel.kt:1342` (conversation switch),
     `ChatViewModel.kt:1474` (`setSelectedModel`), `UnifiedSelectorViewModel.kt:124`.
  2. `connectLocal` (which would emit `connected=true` and clear the flag) never fires for that modelId.
     Proven reachable paths: process-restart restore rehydrates `LocalSelection(modelId, connected=false)`
     (`ActiveModelSelection.kt:51-55`); a cancelled/failed preload coroutine never reaches line 1719.
  3. Chat still works because `LiteRTLmProvider.chatInternal` (`LiteRTLmProvider.kt:244-266`) lazily loads
     the engine on demand via `engineManager.switchToLiteRT` **without touching selection state** → green
     dot + working chat while `connected` stays false → `loading` stays true → indicator stuck forever.
- `refreshActiveBackend()` (`ChatViewModel.kt:1680-1688`) already reads engine truth
  (`engineManager.getActiveEngine()`); it runs after every connect/disconnect/switch path — it is the single
  correct heal site (no second semantic owner, no collector refactor).
- Tests: grep of `app/src/test` confirms **zero** existing references to `isLoadingModel` — nothing pins the
  old behavior; no test updates needed, only new tests.
- Out of scope: preload path logic (1714-1725), engine load, selection logic, UI layout, `ChatScreen.kt`.

## Tasks

<task type="auto">
  <name>Task 1: Heal stuck loading flag in refreshActiveBackend</name>
  <files>app/src/main/java/com/warped/ui/chat/ChatViewModel.kt</files>
  <action>In `refreshActiveBackend()` (currently lines 1680-1688), add a heal step BEFORE the existing derivation: read `engineManager.getActiveEngine()?.modelPath` and `_connection.value.selectedLocalModelId` directly (do NOT gate on `isLocalModelLoaded`, which derives from the stuck `connected` flag — that would be circular). If `selectedLocalModelId != null` AND `engine?.modelPath == selectedLocalModelId` AND the current `localSelection.value.isConnected == false`, call `activeModelSelection.connectLocal(selectedLocalModelId, ProviderType.LITE_RT_LM)` so the existing collector (line 217-244, untouched) emits `connected=true` and clears `isLoadingModel` via its own loading semantics. Then leave the existing body byte-identical. No loop risk: `connectLocal` only triggers the collector's `updateConnection` (collector never calls `refreshActiveBackend` — confirm by reading lines 217-244 before editing); StateFlow distinctness converges after one emission. Do NOT touch the collector, preload path, `ActiveModelSelection`, provider, or UI files.</action>
  <verify><automated>./gradlew :app:assembleDebug (must succeed)</automated></verify>
  <done>Selecting/switching to a local model whose engine is already loaded for the same path results in `isLoadingModel=false` and `isLocalModelLoaded=true`; genuinely-loading (engine absent or different path) still shows the indicator.</done>
</task>

<task type="auto">
  <name>Task 2: Regression tests for the loading-flag heal</name>
  <files>app/src/test/java/com/warped/ui/chat/ChatLoadingFlagHealTest.kt</files>
  <action>Create `ChatLoadingFlagHealTest.kt` mirroring the fixture setup of an existing ChatViewModel test in the same package (same mocks for `activeModelSelection` (real object or `MutableStateFlow(LocalSelection)`-backed fake), `engineManager`, `parameterStore`, repositories; `runTest` + Turbine per project stack). Real `ActiveModelSelection` backed by an in-memory/fake keystore is preferred so `markLocalLoading`/`connectLocal` emissions flow through the real collector. Four cases: (1) connect-transition — `markLocalLoading(modelId)` then `connectLocal(modelId, LITE_RT_LM)` with engine stubbed loaded → `isLoadingModel` goes true then false; (2) stuck-heal — `markLocalLoading(modelId)` with NO `connectLocal` but `engineManager.getActiveEngine()` stubbed to return engine with `modelPath == modelId`, then trigger `refreshActiveBackend` (via `selectConversation` stub or direct call if visible — use whatever existing tests use) → `isLoadingModel` becomes false; (3) still-loading — same as (2) but engine returns null (or a different modelPath) → `isLoadingModel` stays true; (4) disconnect — `disconnectLocal()` → `isLoadingModel` false and `selectedLocalModelId` null. Assert via `connection.test { awaitItem() ... }` or direct `_connection` reads consistent with neighboring tests.</action>
  <verify><automated>./gradlew :app:testDebugUnitTest --tests "com.warped.ui.chat.ChatLoadingFlagHealTest" (all 4 green)</automated></verify>
  <done>All four tests pass; no existing test modified (verified zero references to `isLoadingModel` in `app/src/test`).</done>
</task>

<task type="auto">
  <name>Task 3: Full gate — assemble + unit tests</name>
  <files></files>
  <action>Run the full verification gate. If failures appear, fix only within the two files above; any failure implicating other files must be reported, not worked around.</action>
  <verify><automated>./gradlew :app:assembleDebug :app:testDebugUnitTest (fully green)</automated></verify>
  <done>Assemble + full unit-test suite green.</done>
</task>

## Honest note (on-device check still required)

Unit tests prove the flag clears given engine-truth; they cannot prove the indicator row disappears on
glass. After merge, on-device check: load a local model (including via conversation-switch and via cold
restart with a persisted selection) → the "Loading …" row must clear at the moment the green dot appears.

## Success criteria

- `isLoadingModel` clears whenever the engine is loaded for the selected local model, regardless of which
  path loaded the engine (explicit preload, selector fast path, lazy provider path, post-restart).
- Genuine loads (engine absent/loading) still show `ModelLoadingIndicator` with the model name.
- Collector (lines 217-244), preload (1695-1726), `ActiveModelSelection`, provider, and `ChatScreen` untouched.
- Full gate green.
