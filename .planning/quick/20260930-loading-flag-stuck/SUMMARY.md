# SUMMARY — Stuck "Loading …litertlm" indicator (loading-flag-stuck)

## One-liner

Heal-only fix: `refreshActiveBackend()` reconciles selection state against
engine truth so the stuck `ModelLoadingIndicator` clears whenever the engine
is already loaded for the selected local model.

## What was done

**Task 1 — Heal in `refreshActiveBackend`**
(`app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`, commit `f588e2b0`):
added a heal step before the existing derivation. It reads
`engineManager.getActiveEngine()?.modelPath` and
`_connection.value.selectedLocalModelId` directly (deliberately NOT
`isLocalModelLoaded`, which derives from the stuck `connected` flag and
would be circular). When the selected id is non-null, matches the engine
path, and `localSelection.value.isConnected` is false, it calls
`activeModelSelection.connectLocal(...)` so the untouched collector
(lines 217-244) emits `connected=true` and clears `isLoadingModel` via its
own loading semantics. The pre-existing derivation body is byte-identical.
No loop risk: the collector only calls `updateConnection` and never calls
`refreshActiveBackend`; StateFlow distinctness converges after one emission.

**Task 2 — Regression tests**
(`app/src/test/java/com/warped/ui/chat/ChatLoadingFlagHealTest.kt`,
commit `6ab818c0`): 5 tests using the real `ActiveModelSelection`
(in-memory keystore mock) so emissions flow through the real collector,
with `refreshActiveBackend` triggered via reflection (it is private):
1. connect transition true→false; 2. stuck-heal (mark-without-connect +
   engine truth → flag clears); 3. still-loading (engine absent or
   different path → flag stays); 4. disconnect clears flag + selection;
5. restart-restore (keystore-rehydrated `connected=false` heals on refresh).

**Task 3 — Full gate:** `./gradlew :app:assembleDebug
:app:testDebugUnitTest` fully green (no code changes; verification only).

## Deviations from Plan

None — plan executed exactly as written. One inline fix during Task 2:
a stub line `every { chatRepository.saveLastConversation(any()) }` failed
to compile (`saveLastConversation` lives on `ActiveModelSelection`, not
`ChatRepository`); removed it since the fixture uses the real selection
object. Test-only change, no deviation from plan intent. (Plan specified
4 cases; a 5th restart-restore case was added per the brief's explicit
requirement — "new tests for the heal (mark-without-connect …;
restart-restore case)".)

## Verification

- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL.
- Focused: `ChatLoadingFlagHealTest` — 5/5 PASS.
- Full: `:app:testDebugUnitTest` — 890 tests, 0 failures, 0 errors.
- Collector (217-244), preload path, `ActiveModelSelection`, provider, and
  `ChatScreen` untouched, per plan scope.

## Known Stubs / Threat Flags

None. No new surfaces, endpoints, or schema changes.

## On-device check still required (from plan)

Unit tests prove the flag clears given engine truth; after merge, on
device: load a local model (via conversation-switch and via cold restart
with a persisted selection) → the "Loading …" row must clear at the moment
the green dot appears.

## Self-Check: PASSED

- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` — FOUND
- `app/src/test/java/com/warped/ui/chat/ChatLoadingFlagHealTest.kt` — FOUND
- Commits `f588e2b0`, `6ab818c0` — FOUND (`git log`)
