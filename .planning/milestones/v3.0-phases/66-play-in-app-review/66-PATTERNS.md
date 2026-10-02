# Phase 66: Play In-App Review - Pattern Map

**Mapped:** 2026-10-02
**Files analyzed:** 7 (2 new, 5 modified)
**Analogs found:** 7 / 7

No RESEARCH.md exists (research skipped — standard Play Core review API). File list extracted from 66-CONTEXT.md decisions + code_context.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `data/local/preferences/ReviewPreferences.kt` (new) | preferences/store | request-response (DataStore read/write) | `data/local/preferences/WizardPreferences.kt` | exact |
| `domain/util or ui helper ReviewHelper.kt` (new) | service/helper | request-response (fire-and-forget side effect) | `ui/chat/voice/VoiceDictationManager.kt` + `ui/chat/components/BrowserIntents.kt` | role-match |
| `ui/chat/ChatViewModel.kt` (modify) | viewmodel | event-driven (turn-completion signal) | self (`ChatViewModel.kt` Done branch) | exact |
| `ui/settings/SettingsScreen.kt` (+ `SettingsViewModel.kt` if row needs state) (modify) | component | request-response (external intent) | `ui/chat/components/BrowserIntents.kt` + `SettingsScreen.kt` wizard card | exact |
| `app/src/main/res/values/strings.xml` + `values-es/strings.xml` (modify) | resource | transform (EN+ES pairs) | existing `settings_wizard_*` / `toast_*` pairs | exact |
| `gradle/libs.versions.toml` + `app/build.gradle.kts` (modify) | config | N/A (dependency declaration) | existing `datastore-preferences` / `coil3` entries | exact |
| `MainActivity` / `ChatScreen.kt` touchpoint (activity handle for `launchReview`) | integration | request-response | `ChatScreen.kt` `LocalContext.current` usage | role-match |

## Pattern Assignments

### 1. `data/local/preferences/ReviewPreferences.kt` (new — preferences/store)

**Analog:** `app/src/main/java/com/warped/data/local/preferences/WizardPreferences.kt` (whole file, 54 lines — smallest prefs analog; use `AdvancedPreferences.kt` for int/long key types)

**Imports pattern** (`WizardPreferences.kt` lines 1-14):
```kotlin
package com.warped.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
```

**Store delegate + keys pattern** (`WizardPreferences.kt` lines 16-25; int/long key types from `AdvancedPreferences.kt` lines 28-36):
```kotlin
private val Context.wizardPreferencesStore: DataStore<Preferences> by preferencesDataStore(name = "wizard_preferences")

@Singleton
class WizardPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val KEY_WIZARD_COMPLETED = booleanPreferencesKey("wizard_completed")
        val KEY_SKIPPED_STEPS = stringSetPreferencesKey("skipped_steps")
    }
```
Copy: new file `reviewPreferencesStore` with `preferencesDataStore(name = "review_preferences")`; keys `intPreferencesKey("completed_turns")` + `longPreferencesKey("last_prompt_millis")` (+ optional `booleanPreferencesKey`/`intPreferencesKey` for prompt count). Key-type imports (`intPreferencesKey`, `longPreferencesKey`) copy from `AdvancedPreferences.kt` lines 6-10.

**Read/write pattern** (`WizardPreferences.kt` lines 27-46):
```kotlin
val isWizardComplete: Flow<Boolean> = context.wizardPreferencesStore.data.map { prefs ->
    prefs[KEY_WIZARD_COMPLETED] ?: false
}

suspend fun markWizardComplete() {
    context.wizardPreferencesStore.edit { prefs ->
        prefs[KEY_WIZARD_COMPLETED] = true
    }
}
```
Copy: expose `Flow<Int>`/`Flow<Long>` with `?: default` fallbacks (no `distinctUntilChanged` needed for one-shot reads; add it if collecting in a ViewModel — see `AdvancedPreferences.kt` lines 109-111). Writes are `suspend fun ... edit { }`, called from `viewModelScope.launch(handler)`. No Hilt module needed — `@Singleton @Inject` with `@ApplicationContext` is self-registering (both analogs have zero Module entries; `AppModule.kt` binds only lifecycle).

### 2. Review helper, e.g. `domain/review/ReviewHelper.kt` or `ui/chat/ReviewHelper.kt` (new — service/helper)

**Analog A (ownership/lifecycle):** `ChatViewModel.kt` `dictationManager: VoiceDictationManager?` pattern (lines 173-179, 397-401) — ViewModel-owned, lazily created helper, destroyed in `onCleared`, never `@Singleton` when it holds an Activity/scope:
```kotlin
private var dictationManager: VoiceDictationManager? = null
// ...
fun refreshSpeechAvailability() {
    viewModelScope.launch(Dispatchers.IO) {
        _speechAvailable.value = getDictationManager().isAvailable()
    }
}
```
Copy: helper owned by `ChatViewModel` (or application-scoped only if it holds no Activity — `ReviewManager` needs an Activity at `launchReview` call time, so pass Activity as a method param, never store it). Decision at planner's discretion per CONTEXT.

**Analog B (silent external-side-effect shape):** `app/src/main/java/com/warped/ui/chat/components/BrowserIntents.kt` (whole file, 55 lines) — top-level `fun`, guarded preconditions, dual-catch returning Boolean, user-silent on expected failures:
```kotlin
fun openUrlInBrowser(context: Context, url: String): Boolean {
    val uri = url.toUri()
    if (!uri.scheme.equals("http", ignoreCase = true) &&
        !uri.scheme.equals("https", ignoreCase = true)) {
        // ... toast + return false
    }
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (_: ActivityNotFoundException) {
        // ... toast + false
    } catch (_: SecurityException) {
        // ... toast + false
    }
}
```
Copy for review flow: `suspend fun maybePrompt(...)` that (a) checks eligibility first (turn count + cooldown from `ReviewPreferences`), (b) wraps `requestReview()`/`launchReview()` in `try/catch (Exception)` with `Timber.w` and **no toast/snackbar** on failure (CONTEXT: quota/API failures fully silent — unlike BrowserIntents which toasts, the review path stays silent), (c) returns Boolean or Unit. Quota suppression (`ReviewException` / task failure) is just another silent catch branch.

**Fire-and-forget call convention** (see Shared Patterns §1) — call site never suspends the turn.

### 3. `ui/chat/ChatViewModel.kt` (modify — turn-completion hook)

**Analog:** self, `StreamToken.Done` success branch (lines 1039-1130) + `finally` block (lines 1167-1174).

**Hook point** (lines 1080-1089 — the only place a *completed* assistant turn is observable without touching streaming):
```kotlin
updateTranscript {
    it.copy(
        messages = it.messages + assistantMessage,
        streamingContent = "",
        streamingReasoning = "",
        isStreaming = false
    )
}
// 56-02: turn Done clears the transient tool row.
updateInput { it.copy(isGenerating = false, toolCallActive = null) }
```
Copy: insert the review trigger immediately after the successful persist `try/catch` (lines 1101-1118) inside the `content.isNotBlank() || finalReasoning.isNotBlank()` branch — i.e. after line ~1118, before the `else` (silent-turn branch at line 1119). Do NOT hook the `Error` branch (1132), the silent-turn `else` (1119), `stopGeneration()`, or `retryGrounding()` — only genuine completed turns count.

**Required shape** (fire-and-forget, never stall):
```kotlin
// Phase 66: ambient review — fire-and-forget, never stalls the turn.
viewModelScope.launch(coroutineExceptionHandler) {
    try {
        reviewHelper.maybePrompt(activityProvider())
    } catch (e: Exception) {
        Timber.w(e, "Review prompt failed silently")
    }
}
```
Notes: (a) use the existing `coroutineExceptionHandler` field (lines 211-213) as the launch handler — convention across all ViewModels; (b) `activeHelper = null` stale-guard and `refreshConnectivity()` in `finally` (lines 1167-1174) stay untouched; (c) Activity handle: `launchReview` needs an `Activity`, but ViewModel holds only `@ApplicationContext Context` (line 78) — resolve Activity at the UI layer (`ChatScreen.kt` already does `val context = LocalContext.current`, lines 186/209) and pass down, or expose a one-shot `SharedFlow` event consumed by the screen. Prefer the existing `_events: MutableSharedFlow<ChatEvent>` + `tryEmit` precedent (lines 154-155, 414-416: "tryEmit only — the turn never suspends waiting for a collector") over a new channel.

### 4. `ui/settings/SettingsScreen.kt` (modify — Store entry footer row)

**Analog A (card row shape):** wizard card in `SettingsScreen.kt` (lines 243-263):
```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B29)),
    shape = RoundedCornerShape(12.dp)
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(stringResource(R.string.settings_wizard_title), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.settings_wizard_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onNavigateToWizard) {
            Text(stringResource(R.string.settings_wizard_run), color = Color(0xFFD97757))
        }
    }
}
```
Copy: new Card in the App section (after the wizard card, before `item { Spacer(Modifier.height(24.dp)) }` at line 265), same `Card`/`Column`/`TextButton` shape, accent `Color(0xFFD97757)`.

**Analog B (external-intent launch):** `BrowserIntents.kt` `openUrlInBrowser` (lines 26-55) — guarded `ACTION_VIEW` + dual catch. Store entry variant:
```kotlin
// market:// first, https fallback — mirror openUrlInBrowser's dual-catch,
// but try market:// then https://play.google.com/store/apps/details?id=<appId>.
try {
    context.startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=${context.packageName}".toUri()))
} catch (_: ActivityNotFoundException) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=${context.packageName}".toUri()))
    } catch (_: ActivityNotFoundException) { /* silent or toast_no_browser */ }
} catch (_: SecurityException) { /* same */ }
```
Place this as a small top-level `fun openPlayStoreListing(context: Context)` next to `openUrlInBrowser` (same package `ui/chat/components/` or a shared `util/` home at planner's discretion) so the scheme allowlist logic doesn't drift. Note: `openUrlInBrowser`'s http/https allowlist would REJECT `market://` — do not reuse it directly; write the market-first variant. `LocalContext.current` provides Context in the composable (SettingsScreen has no Context today — add `val context = LocalContext.current` per `ChatScreen.kt` lines 186/209).

### 5. `values/strings.xml` + `values-es/strings.xml` (modify — EN+ES copy)

**Analog:** existing pairs — `settings_wizard_title/desc/run` (`values/strings.xml` lines 84-87, `values-es/strings.xml` lines 85-87) and `toast_invalid_link/toast_no_browser` (`values` lines 219-220, `values-es` lines 220-221):
```xml
<string name="settings_wizard_title">Setup Wizard</string>
<string name="settings_wizard_desc">Re-run the onboarding wizard to explore Warped features.</string>
<string name="settings_wizard_run">Run</string>
```
```xml
<string name="settings_wizard_title">Wizard de configuración</string>
<string name="settings_wizard_desc">Volver a ejecutar el wizard para explorar las funciones de Warped.</string>
<string name="settings_wizard_run">Ejecutar</string>
```
Copy: add `settings_review_title/desc/action` (names at planner's discretion) with `<!-- Settings review card -->` comment header matching the `<!-- Settings wizard card -->` convention (line 84). Every EN string gets an ES twin with the identical `name`. No quantity strings or formatting args needed unless copy requires them.

### 6. `gradle/libs.versions.toml` + `app/build.gradle.kts` (modify — `review-ktx` dependency)

**Analog:** DataStore + Coil entries (most recent precedents with comments).

Version entry (`libs.versions.toml` line 22 + library line 134):
```toml
datastore = "1.2.1"
# ...
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
```
With hold/ceiling comment style when pinning matters (lines 4, 7, 10-11, 18-21 show the `# HOLD:` / `# CEILING:` convention with reason + date).

Usage (`app/build.gradle.kts` lines 234-235):
```kotlin
// DataStore
implementation(libs.datastore.preferences)
```
Copy: add `play-review = "<latest stable>"` under `[versions]` (verify on Google Maven; Play review-ktx lives under `com.google.android.play:review-ktx`), library `play-review-ktx = { group = "com.google.android.play", name = "review-ktx", version.ref = "play-review" }` under `[libraries]`, and `implementation(libs.play.review.ktx)` in the dependencies block grouped with its own `// Play In-App Review` comment. No KSP/plugin/Kotlin-BOM interaction. Confirm `auditDependencies.sh` allowlist doesn't flag `com.google.android.play` (it bans firebase/moshi/gson/ktor/mcp/tflite/mlkit/appauth/cameraX/datastore-proto — Play Core is not in the ban list, but run the task to confirm).

---

## Shared Patterns

### 1. Fire-and-forget coroutine side work (applies to: ChatViewModel hook, SettingsViewModel if it touches prefs)
**Sources:** `SettingsViewModel.kt` lines 26-28 + 52-56; `ChatViewModel.kt` lines 211-213.
```kotlin
private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    Timber.e(throwable, "Unhandled coroutine exception")
}

fun setCodeTheme(theme: SyntaxTheme) {
    viewModelScope.launch(coroutineExceptionHandler) {
        advancedPreferences.setSyntaxTheme(theme)
    }
}
```
Every ViewModel owns a `coroutineExceptionHandler` logging via Timber; all DataStore writes go through `viewModelScope.launch(handler)`. Off-main-thread probes use `viewModelScope.launch(Dispatchers.IO)` (`ChatViewModel.kt` lines 367-369, 397-401). The review trigger MUST follow this — never `runBlocking`, never suspend the turn.

### 2. Silent-failure + Timber logging (applies to: ReviewHelper, turn hook)
**Sources:** `ChatViewModel.kt` lines 382-385 (`refreshConnectivity` best-effort gate) and `BrowserIntents.kt` lines 40-54 (dual catch).
```kotlin
val online = try {
    fetcher.hasValidatedInternet()
} catch (e: Exception) {
    Timber.w(e, "Chat: connectivity check failed, treating as offline")
    false
}
```
Convention: `Timber.w` for expected/benign failures (connectivity, quota), `Timber.e` for unexpected persistence/logic failures. Review flow uses `Timber.w` + silent return on ALL failure paths (quota suppression is expected, not an error).

### 3. Hilt constructor injection for prefs/helpers (applies to: ReviewPreferences, ReviewHelper if app-scoped)
**Sources:** `WizardPreferences.kt` lines 18-21; `ChatViewModel.kt` lines 51-79.
```kotlin
@Singleton
class WizardPreferences @Inject constructor(
    @ApplicationContext private val context: Context
)
```
No manual instantiation, no service locator. `@HiltViewModel` ViewModels receive prefs via constructor `@Inject`. If ReviewHelper is app-scoped: `@Singleton class ReviewHelper @Inject constructor(private val prefs: ReviewPreferences)`. If ViewModel-scoped (holds Activity-adjacent state): plain class constructed in the ViewModel like `dictationManager`.

### 4. One-shot UI events via SharedFlow tryEmit (applies to: Activity handoff if event-based)
**Source:** `ChatViewModel.kt` lines 150-155.
```kotlin
private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 4)
val events: SharedFlow<ChatEvent> = _events.asSharedFlow()
// ...
_events.tryEmit(ChatEvent.Snackbar(context.getString(R.string.snack_web_pref_updated)))
```
`tryEmit` only — never `emit` (which would suspend the turn). If the review flow needs the Activity, a `ChatEvent.RequestReview` consumed in `ChatScreen.kt` follows this exact channel; no new event bus.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| Play `ReviewManagerFactory` / `requestReview` / `launchReview` call sequence | SDK call | request-response | No Play Core usage exists in the codebase yet — first Play SDK import. Planner must use official `com.google.android.play:review-ktx` docs (`ReviewManagerFactory.create(context)`, `requestReview()` → `launchReview(activity, reviewInfo)`), wrapped in the silent-failure shape above. |
| `market://` intent | external intent | fire-and-forget | No `market://` precedent — `BrowserIntents.kt` covers http/https only and its allowlist rejects `market:`. New small function required (market-first + https fallback). |

## Metadata

**Analog search scope:** `app/src/main/java/com/warped/{data/local/preferences,ui/chat,ui/settings,di}`, `app/src/main/res/values*/strings.xml`, `gradle/libs.versions.toml`, `app/build.gradle.kts`
**Files scanned:** ~12 (2 prefs stores, ChatViewModel, SettingsScreen, SettingsViewModel, BrowserIntents, ChatScreen touchpoint, AppModule, strings EN+ES, version catalog, app build script)
**Pattern extraction date:** 2026-10-02
