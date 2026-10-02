# Phase 65: Voice Dictation - Pattern Map

**Mapped:** 2026-10-02
**Files analyzed:** 8 (1 new, 7 modified)
**Analogs found:** 7 / 8

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `ui/chat/voice/VoiceDictationManager.kt` (NEW) | utility (platform wrapper) | event-driven (streaming partials) | — none — | no-analog (greenfield) |
| `ui/chat/components/ChatInputBar.kt` (MOD) | component | request-response (UI props) | itself: image-button hide + thinking-toggle tint + send/stop slot | exact |
| `ui/chat/ChatScreen.kt` (MOD) | component (screen) | event-driven (launchers, events, lifecycle) | itself: imagePickerLauncher + ChatEvent collector + WarpedAlertDialog + DisposableEffect observers | exact |
| `ui/chat/ChatViewModel.kt` (MOD) | ViewModel | CRUD (state mutation) + event-driven (one-shot events) | itself: `updateInput(text)` + `_events.tryEmit` + `onCleared` | exact |
| `ui/chat/ChatUiState.kt` (MOD) | model (state/events) | event-driven | itself: `ChatEvent` sealed interface (lines 289-291) | exact |
| `app/src/main/AndroidManifest.xml` (MOD) | config | n/a | itself: `POST_NOTIFICATIONS` declaration + removal-comment convention | exact |
| `res/values/strings.xml` + `res/values-es/strings.xml` (MOD) | config (resources) | n/a | themselves: `cd_*` keys + `dismiss` reuse | exact |
| `MainActivity.kt` (REFERENCE ONLY, do not modify) | activity | request-response | permission-request precedent (legacy style — do NOT copy verbatim) | partial (anti-pattern warning) |

## Pattern Assignments

### NEW `ui/chat/voice/VoiceDictationManager.kt` (utility, event-driven) — NO ANALOG

**Analog:** none. Grep for `SpeechRecognizer|RecognizerIntent|RECORD_AUDIO` in `app/src/main` returns zero implementation hits (only the manifest removal comment). This is a greenfield wrapper.

**What the planner must specify from platform APIs (not codebase):** `SpeechRecognizer.createSpeechRecognizer()`, `SpeechRecognizer.isRecognitionAvailable()`, `RecognitionListener` with `onPartialResults`/`onResults`/`onError`, `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` + `EXTRA_PARTIAL_RESULTS`. No codebase precedent constrains the internal shape.

**Lifecycle constraint from codebase conventions (see Shared Patterns § Lifecycle):** the wrapper MUST expose an explicit `destroy()` and be torn down either in `ChatViewModel.onCleared()` or in a composable `DisposableEffect.onDispose` — both precedents exist in this codebase, executor picks one.

---

### `ui/chat/components/ChatInputBar.kt` (component, request-response)

**Analog:** itself — `app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt`

**Hide-pattern precedent** (lines 138-143) — mic visibility MUST copy this `if`-guard (no dimmed state):
```kotlin
if (modelHasVision) {
    IconButton(onClick = onAddImage, modifier = Modifier.size(40.dp)) {
        Icon(Icons.Filled.AddPhotoAlternate, stringResource(R.string.cd_add_image),
            tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
    }
}
```

**Listening-tint precedent** (lines 148-152) — thinking-toggle active pill; reuse exact alpha + `primary`:
```kotlin
colors = ButtonDefaults.buttonColors(
    containerColor = if (reasoningEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent
),
```

**Send/stop slot precedent** (lines 168-191) — `Icons.Filled.Stop` already imported (line 14); mic goes LEFT of this slot per UI-SPEC §1. Note the existing 40.dp button / 24.dp icon sizing and `Color.White.copy(alpha = 0.6f)` idle tint — copy all three values verbatim.

**Dead-param warning:** `modelHasAudio: Boolean = false` (line 48) + `onAudioRecorded`/`onAudioRecordingChanged` are a pre-existing dead audio path. UI-SPEC explicitly excludes touching them — the new mic params (`speechAvailable`, `isListening`, `onMicClick`, all defaulted) are separate.

---

### `ui/chat/ChatScreen.kt` (component/screen, event-driven)

**Analog:** itself — `app/src/main/java/com/warped/ui/chat/ChatScreen.kt`

**Permission-launcher precedent** (lines 195-198) — the RECORD_AUDIO launcher MUST follow this shape, swapping `GetMultipleContents` for `ActivityResultContracts.RequestPermission`:
```kotlin
val imagePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetMultipleContents()
) { uris ->
```
`ActivityResultContracts` is already imported (line 6). **Do NOT copy `MainActivity.requestNotificationPermissionIfNeeded()`** — it uses the legacy `Activity.requestPermissions()` API (see Shared Patterns § Permissions).

**SnackbarHost + ChatEvent channel** (lines 182-193, 280) — the denial path extends this collector, no new host:
```kotlin
val snackbarHostState = remember { SnackbarHostState() }
LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
        when (event) {
            is ChatEvent.Snackbar ->
                snackbarHostState.showSnackbar(event.message, duration = SnackbarDuration.Short)
        }
    }
}
```
```kotlin
snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
```
Denial needs `SnackbarDuration.Long` + action label (Settings deep-link) — the `showSnackbar` overload with `actionLabel` precedent lives in `ModelsScreen.kt` lines 68-73:
```kotlin
snackbarHostState.showSnackbar(message = msg, actionLabel = dismissLabel, withDismissAction = true)
```

**Rationale-dialog precedent** (lines 527-548) — `WarpedAlertDialog` slot signature for the first-tap rationale:
```kotlin
WarpedAlertDialog(
    onDismissRequest = { viewModel.cancelModelSwitch() },
    title = { Text(stringResource(R.string.model_selected_new)) },
    text = {
        Text(
            stringResource(R.string.dialog_model_switch_msg),
            style = MaterialTheme.typography.bodyMedium
        )
    },
    confirmButton = {
        TextButton(onClick = { viewModel.confirmModelSwitch() }) {
            Text(stringResource(R.string.new_chat))
        }
    },
    dismissButton = {
        TextButton(onClick = { viewModel.cancelModelSwitch() }) {
            Text(stringResource(R.string.cancel))
        }
    }
)
```

**Lifecycle-observer precedent** (lines 141-152) — `DisposableEffect(lifecycleOwner)` with `LifecycleEventObserver`; cleanup precedent (lines 133-137):
```kotlin
DisposableEffect(Unit) {
    onDispose {
        viewModel.unloadLocalModels()
    }
}
```

---

### `ui/chat/ChatViewModel.kt` (ViewModel, CRUD + event-driven)

**Analog:** itself — `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`

**Input-text mutation helper** (lines 106, 1375-1377) — append-at-cursor helper goes next to this; single-writer convention via the private `updateInput(op)`:
```kotlin
private fun updateInput(op: (ChatInputState) -> ChatInputState) {
    _input.update(op)
}
```
```kotlin
fun updateInput(text: String) {
    updateInput { it.copy(inputText = text) }
}
```
New helper (e.g. `appendDictation(text: String)`) MUST route through `updateInput { it.copy(...) }` — never write `_input` directly. Ownership rule is documented in `ChatUiState.kt` lines 22-26 (`ChatInputState` owned by `updateInput` + send-clear only).

**One-shot event emission** (lines 153-154, 1042-1046) — `tryEmit`, never suspending:
```kotlin
private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 4)
val events: SharedFlow<ChatEvent> = _events.asSharedFlow()
```
```kotlin
_events.tryEmit(
    ChatEvent.Snackbar(
        context.getString(R.string.snack_sources_not_saved),
    ),
)
```
Denial event needs an action callback (Settings intent) — extend `ChatEvent` (see ChatUiState below); emission stays `tryEmit`.

**ViewModel cleanup** (lines 1937-1940):
```kotlin
override fun onCleared() {
    super.onCleared()
    unloadLocalModels()
}
```
If the recognizer is ViewModel-held, its `destroy()` call goes here, same position as `unloadLocalModels()`.

**Hilt constructor precedent** (lines 49-78): `@HiltViewModel` + `@Inject constructor(...)` with `@param:ApplicationContext private val context: Context`. `context.getString(...)` for event copy (line 1044) is the established way to resolve strings in this ViewModel.

---

### `ui/chat/ChatUiState.kt` (model, event-driven)

**Analog:** itself — `app/src/main/java/com/warped/ui/chat/ChatUiState.kt` lines 284-291:
```kotlin
/**
 * Phase 53 (TOGGLE-01/SRC-02): one-shot UI events from ChatViewModel to
 * ChatScreen. Emitted with tryEmit (never suspends the turn); the screen
 * renders them as Snackbars. Chat continues regardless (non-blocking).
 */
sealed interface ChatEvent {
    data class Snackbar(val message: String) : ChatEvent
}
```
Extend with an action-carrying variant (e.g. `SnackbarWithAction(message, actionLabel, ...)`) or add optional action fields — executor's call, but the `sealed interface` + `when` in ChatScreen's collector is the extension point. Keep the tryEmit/non-blocking contract in the KDoc.

---

### `app/src/main/AndroidManifest.xml` (config)

**Analog:** itself — `app/src/main/AndroidManifest.xml` lines 4-12:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<!-- Removed 2026-10-01 (pre-public audit): RECORD_AUDIO (dead
     AudioRecorder, no runtime-permission flow), SCHEDULE_EXACT_ALARM
     (no alarm code), ACCESS_FINE/COARSE_LOCATION (zero code usage).
     Re-add only with a real feature + runtime request. -->
```
This phase IS the "real feature + runtime request" the comment demands: re-add `<uses-permission android:name="android.permission.RECORD_AUDIO" />` alongside the others and update/remove the stale comment (RECORD_AUDIO is no longer dead). Note `android:largeHeap="true"` (line 23) and `windowSoftInputMode="adjustResize"` (line 40) are untouched.

---

### `res/values/strings.xml` + `res/values-es/strings.xml` (resources)

**Analog:** themselves. EN+ES pair convention — every key exists in both files. Copy patterns:
- `values/strings.xml` line 39: `<string name="dismiss">Dismiss</string>` → `values-es` line 39: `<string name="dismiss">Descartar</string>` (reuse `dismiss` for the rationale dismiss button, do not create a new key)
- `values/strings.xml` lines 190-191: `<string name="cd_stop">Stop</string>` / `<string name="cd_send">Send</string>` → `values-es` lines 190-191: `Detener` / `Enviar` (new `cd_dictate`/`cd_stop_listening` follow the `cd_*` prefix convention)
- `values/strings.xml` lines 214-215: `snack_*` precedent for snackbar copy (`snack_web_pref_updated`, `snack_sources_not_saved`)

7 new keys per UI-SPEC §6: `cd_dictate`, `cd_stop_listening`, `voice_rationale_title`, `voice_rationale_body`, `voice_rationale_allow`, `voice_denied`, `voice_open_settings`.

---

## Shared Patterns

### Permissions (runtime request)
**Source:** `ui/chat/ChatScreen.kt` lines 195-198 + `ui/models/ModelsScreen.kt` lines 75-79 (`rememberLauncherForActivityResult`); **anti-pattern:** `MainActivity.kt` lines 46-55.
```kotlin
// MainActivity legacy style — DO NOT replicate in Compose:
private fun requestNotificationPermissionIfNeeded() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
}
```
**Rule:** new RECORD_AUDIO flow uses `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` in ChatScreen (same file that owns the image-picker launcher). `RECORD_AUDIO` needs no SDK-gate (unlike POST_NOTIFICATIONS which is TIRAMISU+). Permanent-denial detection via `shouldShowRequestPermissionRationale == false` after denial → Snackbar with Settings action. No Accompanist-permissions dependency exists in the codebase — do not introduce one.
**Apply to:** ChatScreen permission wiring.

### Error/UX channel (Snackbar via UiState event)
**Source:** `ui/chat/ChatUiState.kt` lines 289-291 (`ChatEvent`); collector `ui/chat/ChatScreen.kt` lines 186-193; show-then-clear variant `ui/huggingface/HuggingFaceScreen.kt` lines 96-103 (Phase 64 pattern cited in CONTEXT):
```kotlin
val snackbarHostState = remember { SnackbarHostState() }
val activationError by viewModel.error.collectAsStateWithLifecycle()
LaunchedEffect(activationError) {
    activationError?.let { msg ->
        snackbarHostState.showSnackbar(message = msg)
        viewModel.clearError()
    }
}
```
**Rule:** denial flows through the `ChatEvent` channel (ChatScreen collector), NOT a second host or overlay banner. Recognition errors (network/no-speech/timeout) fail SILENT per UI-SPEC §3 — no event, no copy.
**Apply to:** denial Snackbar only.

### Rationale dialog
**Source:** `ui/components/WarpedAlertDialog.kt` lines 37-50 (slot signature: `onDismissRequest`, `confirmButton`, `dismissButton`, `title`, `text`; 12dp card, `#2B2B29`, titleMedium semibold). 19 existing call sites; ChatScreen 527-548 and ModelsScreen 81-106 are the closest analogs.
**Rule:** no custom layout — title + body + two buttons, WarpedAlertDialog defaults.
**Apply to:** first-tap rationale dialog.

### Recognizer lifecycle
**Sources:** `ui/chat/ChatViewModel.kt` lines 1937-1940 (`onCleared` → `unloadLocalModels()`); `ui/chat/ChatScreen.kt` lines 133-137 (`DisposableEffect.onDispose` → `viewModel.unloadLocalModels()`) and 141-152 (`DisposableEffect(lifecycleOwner)` observer registration/removal).
**Rule:** recognizer MUST be destroyed with the UI lifecycle. Two codebase-sanctioned options: (a) ViewModel-held + `destroy()` in `onCleared()`; (b) composable-scoped manager + `destroy()` in `DisposableEffect.onDispose`. Either satisfies the contract — planner picks one and states it.
**Apply to:** `VoiceDictationManager` teardown wiring.

### State mutation (input)
**Source:** `ui/chat/ChatViewModel.kt` lines 102-112 (single-owner updaters), `ui/chat/ChatUiState.kt` lines 18-26 (ownership doc).
**Rule:** all `ChatInputState` writes go through `updateInput { it.copy(...) }`. Dictation appends at cursor into the existing draft — never replaces, never auto-sends. Partial results stream through the same `onTextChange`/`updateInput` path as keystrokes.
**Apply to:** dictation text insertion (partial + final).

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `ui/chat/voice/VoiceDictationManager.kt` (NEW) | utility (platform SpeechRecognizer wrapper) | event-driven | Zero SpeechRecognizer/RecognizerIntent hits in `app/src/main`; greenfield. Planner uses platform API directly, constrained only by the lifecycle + state-mutation rules above. |

**No Settings deep-link precedent:** grep for `ACTION_APPLICATION_DETAILS_SETTINGS` returns zero hits — the denial-action intent (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS` + package URI) has no in-codebase analog. Standard platform snippet; planner writes it from API knowledge.

## Metadata

**Analog search scope:** `app/src/main/java/com/warped/ui/chat/`, `ui/components/`, `ui/models/`, `ui/huggingface/`, `MainActivity.kt`, `AndroidManifest.xml`, `res/values*/strings.xml`
**Files scanned:** ~12 (ChatInputBar, ChatScreen, ChatViewModel, ChatUiState, WarpedAlertDialog, MainActivity, AndroidManifest, HuggingFaceScreen, ModelsScreen, both strings.xml + targeted greps)
**Pattern extraction date:** 2026-10-02
