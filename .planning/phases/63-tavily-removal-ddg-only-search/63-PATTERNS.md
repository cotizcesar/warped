# Phase 63: Tavily Removal → DDG-only Search - Pattern Map

**Mapped:** 2026-10-02
**Files analyzed:** 20 (3 deletes, 14 modifies, 3 test modifies)
**Analogs found:** 17 / 20

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| DELETE `data/remote/api/TavilyApi.kt` | route (retrofit API) | request-response | `TavilyApi.kt` itself (27 lines, delete verbatim) | exact |
| DELETE `data/remote/dto/TavilyDtos.kt` | model (DTO) | transform | `TavilyDtos.kt` itself (64 lines, delete verbatim) | exact |
| DELETE `data/grounding/TavilySearchRepository.kt` | service (producer) | request-response | `TavilySearchRepository.kt` itself (254 lines, delete verbatim) | exact |
| `di/NetworkModule.kt` | config (DI) | request-response | `NetworkModule.kt` lines 123-166 (Tavily bindings block to remove) | exact |
| `data/local/security/ApiKeyStore.kt` | service (keystore) | file-I/O | `ApiKeyStore.kt` lines 28-56 (`deleteAllKeys` calls `deleteTavilyKey`; orphan precedent `huggingface_token`) | exact |
| `data/grounding/DuckDuckGoSearchRepository.kt` | service (producer) | request-response | Itself — DDG leg lines 159-179 + `fuse()` lines 305-376 is the surviving contract; Tavily legs lines 72, 102, 115, 135-158, 180-198 to cut | exact |
| `ui/settings/SettingsViewModel.kt` | component (viewmodel) | request-response | Itself lines 187-321 (Tavily block to delete); keep pattern lines 146-170 (`deleteAllApiKeys` + `coroutineExceptionHandler`) | exact |
| `ui/settings/SettingsUiState.kt` | model (UI state) | transform | Itself lines 20-27 (5 tavily fields to delete) | exact |
| `ui/settings/SettingsScreen.kt` | component (composable) | request-response | Itself `TavilyKeyCard` lines 402-478 + call site lines 352-362 (delete); keep `SettingsSectionHeader` pattern line 355-358 | exact |
| `data/agentic/LocalToolLoop.kt` | utility (loop policy) | transform | Itself `mapSearchOutcome` lines 221-231 + copy consts lines 70-84 (Tavily branches to cut/replace) | exact |
| `data/remote/provider/CompatToolLoop.kt` | service (tool executor) | request-response | Itself `executeRemoteTool` lines 234-302 (DDG call site; swap `TavilySearchRepository.DEFAULT_MAX_RESULTS` line 263) | exact |
| `data/remote/provider/OpenAIProvider.kt` + `AnthropicProvider.kt` + `LmStudioHelper.kt` | service (provider) | streaming | `CompatToolLoop.kt` lines 261-271 (same DDG-call + `mapSearchOutcome`/`searchSources`/`searchImages` shape) | role-match |
| `ui/chat/ChatViewModel.kt` | component (viewmodel) | request-response | Itself DDG pre-search block lines 600-780 (outcome `when` lines 685-776; cut Tavily branches, keep Grounded/ModelOnly) | exact |
| `ui/chat/components/MessageBubble.kt` | component (composable) | request-response | Itself banner `when` lines 466-500 (cut `TAVILY_*`/`IMAGES_NEED_KEY` branches lines 479-489, keep OFFLINE/FETCH_FAILED/TOOLS_UNSUPPORTED) | exact |
| `domain/model/ChatMessage.kt` | model (enum) | transform | Itself `ModelOnlyNotice` lines 33-56 (remove `TAVILY_*`/`IMAGES_NEED_KEY` entries; enum is ephemeral — see notes) | exact |
| `res/values/strings.xml` + `res/values-es/strings.xml` | config (strings) | transform | Themselves — EN lines 258-261 + 334-351; ES lines 259-262 + 337-352 + 505 (delete verbatim) | exact |
| `data/local/db/entity/GroundedSourceEntity.kt` | model (entity) | CRUD | Itself lines 29-50 (STAYS — no schema change; NULL-OG comment line 39 already documents Tavily rows) | exact |
| `ui/chat/components/OgSourceCard.kt` | component (composable) | transform | Itself `ogDisplayTitle`/`ogHostOf` lines 331-362 (generic host-fallback label precedent for SEARCH-04) | exact |
| Startup Keystore cleanup (new, location at planner discretion) | utility/middleware | file-I/O | `WarpedApplication.onCreate` lines 72-106 + `KeystoreManager.remove` lines 54-60 | partial |
| Tests: `SettingsTavilyTest.kt`, `SettingsGroundingToggleTest.kt`, `SingletonScopeRegressionTest.kt` | test | request-response | Themselves (rewrite/delete per table below) | exact |

## Pattern Assignments

### DELETE `data/remote/api/TavilyApi.kt` (route, request-response)

**Analog:** itself — delete the whole file (27 lines). Only consumer is `TavilySearchRepository` (also deleted) + one DI binding in `NetworkModule` (removed separately).

**Imports pattern** — nothing to copy; verify no other imports of `com.warped.data.remote.api.TavilyApi` remain (only `NetworkModule.kt` line 3 and `TavilySearchRepository.kt` line 4 reference it today).

```kotlin
// app/src/main/java/com/warped/data/remote/api/TavilyApi.kt (lines 20-27) — DELETE ALL
interface TavilyApi {
    @POST("search")
    @Headers("Content-Type: application/json")
    suspend fun search(
        @Header("Authorization") auth: String,
        @Body request: TavilySearchRequest,
    ): Response<TavilySearchResponse>
}
```

---

### DELETE `data/remote/dto/TavilyDtos.kt` (model, request-response)

**Analog:** itself — delete the whole file (64 lines). Only consumer is `TavilySearchRepository` + `TavilyApi`. No other DTO file imports it (verified: only `TavilySearchRepository.kt` lines 5-7).

```kotlin
// app/src/main/java/com/warped/data/remote/dto/TavilyDtos.kt (lines 17-64) — DELETE ALL
// 4 @Serializable classes: TavilySearchRequest, TavilySearchResult,
// TavilySearchResponse, TavilyImageResult. Shared-Json conventions
// (ignoreUnknownKeys etc.) live in NetworkModule.provideJson lines 82-87 — untouched.
```

---

### DELETE `data/grounding/TavilySearchRepository.kt` (service, request-response)

**Analog:** itself — delete the whole file (254 lines). Before deleting, migrate its constants and outcome type:

- **Constants to relocate** into `DuckDuckGoSearchRepository` companion (today referenced from 6 call sites): `DEFAULT_MAX_RESULTS = 5` (line 241), `MAX_RESULTS_CAP = 10` (line 244), `MAX_QUERY_CHARS = 500` (line 247), `MAX_IMAGES = 10` (line 250).
- **Outcome type decision** (planner discretion): either keep the `TavilySearchOutcome` sealed interface name (zero churn in `LocalToolLoop`/`ChatViewModel`/`CompatToolLoop`) or rename to `SearchOutcome` with file-wide rename. Keeping the name is the minimal-diff path.
- **Key-race discipline to preserve** in DDG repo (lines 92-98): zero-fill after presence check — but all `getTavilyKey()` call sites are deleted, so this discipline disappears with the key.

```kotlin
// Lines 45-61 — the outcome contract every caller compiles against:
sealed interface TavilySearchOutcome {
    data class Grounded(val fused: MultiUrlResult.Fused) : TavilySearchOutcome
    data class ModelOnly(val failed: MultiUrlResult.AllFailed) : TavilySearchOutcome
    data object MissingKey : TavilySearchOutcome      // DELETE (DDG needs no key)
    data object InvalidKey : TavilySearchOutcome      // DELETE (no 401 without key)
    data object UsageLimit : TavilySearchOutcome      // DELETE (no credits without Tavily)
}
```

**Call sites that import it today** (all must be touched): `DuckDuckGoSearchRepository.kt` line 72 + 5 `TavilySearchRepository.*` constant refs, `LocalToolLoop.kt` line 5, `ChatViewModel.kt` line 20, `SettingsViewModel.kt` line 9, `CompatToolLoop.kt` line 10, `OpenAIProvider.kt` line 11, `AnthropicProvider.kt` line 12, `OpenAIProvider.kt` line 368 / `CompatToolLoop.kt` line 263 / `AnthropicProvider.kt` line 390 (`DEFAULT_MAX_RESULTS`), `ChatViewModel.kt` line 654.

---

### `di/NetworkModule.kt` (config, request-response)

**Analog:** itself lines 123-166 — remove the three `@Named("tavily")` bindings + the `TavilyApi` import (line 3). Keep the `@Named("sse")` binding (lines 96-121) as the surviving named-client pattern.

```kotlin
// Lines 133-166 — DELETE this entire block (client + retrofit + api):
@Provides
@Singleton
@Named("tavily")
fun provideTavilyOkHttpClient(): OkHttpClient = ...
@Provides
@Singleton
@Named("tavily")
fun provideTavilyRetrofit(...): Retrofit = Retrofit.Builder()
    .baseUrl("https://api.tavily.com/") ...
@Provides
@Singleton
fun provideTavilyApi(
    @Named("tavily") retrofit: Retrofit,
): TavilyApi = retrofit.create(TavilyApi::class.java)
```

**Test coupling:** `SingletonScopeRegressionTest.kt` lines 47-51 pins `"provideTavilyOkHttpClient"` in `singletonBindings` — must drop that entry (see test section).

---

### `data/local/security/ApiKeyStore.kt` (service, file-I/O)

**Analog:** itself — delete `storeTavilyKey`/`getTavilyKey`/`deleteTavilyKey` + `TAVILY_ALIAS` (lines 33-56), and remove the `deleteTavilyKey()` call inside `deleteAllKeys` (line 30). Surviving per-endpoint pattern (lines 10-26) is untouched.

```kotlin
// Lines 28-31 — AFTER (remove line 30):
fun deleteAllKeys(endpointIds: List<Long>) {
    endpointIds.forEach { deleteKey(it) }
}
```

```kotlin
// Lines 54-60 — KeystoreManager.remove: the one-shot cleanup primitive.
// Best-effort by construction (try/catch, never throws) — safe to call on startup.
fun remove(key: String) {
    try {
        encryptedPrefs.edit { remove(key) }
    } catch (e: Exception) {
        Timber.e(e, "KeystoreManager: remove failed")
    }
}
```

**SEARCH-03 cleanup:** one-shot startup deletion of the orphaned `"tavily_api_key"` alias. No direct precedent exists for an upgrade cleanup (see "No Analog Found"). Planner options: (a) call `keystoreManager.remove("tavily_api_key")` once in `WarpedApplication.onCreate()` (file `app/src/main/java/com/warped/WarpedApplication.kt` lines 72-106) via Hilt EntryPoint; (b) a DataStore boolean-gated one-shot in an existing init path. Either way reuse the string literal `"tavily_api_key"` (today `ApiKeyStore.TAVILY_ALIAS`, line 55) — do NOT keep the accessor methods.

---

### `data/grounding/DuckDuckGoSearchRepository.kt` (service, request-response)

**Analog:** itself — the DDG leg is the contract to preserve. Cut list:

1. Constructor param `private val tavily: TavilySearchRepository` (line 72) — remove; class becomes standalone `@Singleton` with `(baseClient, webPageFetcher, apiKeyStore→REMOVE, enricher)`. `apiKeyStore` is used ONLY for Tavily key probes (lines 145, 183) — remove the param too.
2. `includeImages` pass-through direct-Tavily block (lines 144-158) — delete; DDG has no image API so image-intent turns fuse zero images (grid stays empty, text grounding preserved). `fuseImages` relocation: `TavilySearchRepository.fuseImages` is referenced by `GroundedImages.kt` line 8 — planner must decide: keep a no-op/empty `images` in `MultiUrlResult.Fused` or drop the field.
3. Tavily fallback block (lines 180-198) — replace with direct `ModelOnly(FETCH_FAILED)` return (the existing unkeyed path lines 186-189 already does exactly this — promote it to the only path).
4. Constant refs: `TavilySearchRepository.DEFAULT_MAX_RESULTS` (line 102), `MAX_QUERY_CHARS` (line 115), `MAX_RESULTS_CAP` (line 231) — repoint to relocated companions (see TavilySearchRepository section).
5. KDoc rewrite: class KDoc lines 24-66 documents the DDG-primary/Tavily-fallback policy — rewrite to DDG-only.

**Preserve verbatim** — stripped-client policy (lines 86-98), offline gate (lines 123-133), `CancellationException` rethrow (lines 163-165), `fuse()` (lines 305-376), `parseResults`/`resolveResultUrl` (lines 229-296), `htmlSupplier`/`ioDispatcher` test seams (lines 77-84).

```kotlin
// Lines 100-114 — AFTER shape (keep signature minus tavily; includeImages TBD by planner):
suspend fun search(
    query: String,
    maxResults: Int = DEFAULT_MAX_RESULTS,   // relocated companion, was TavilySearchRepository.*
    contextSize: Int = 4096,
    includeImages: Boolean = false,           // planner: keep-as-ignored or remove + update 5 call sites
): SearchOutcome = withContext(ioDispatcher) { ... }
```

---

### `ui/settings/SettingsViewModel.kt` (component, request-response)

**Analog:** itself — delete lines 187-321 (entire Tavily section: `refreshTavilyPresence`, `onTavilyKeyInputChange`, `saveTavilyKey`, `clearTavilyKey`, `testTavilyConnection`, `TEST_QUERY`), plus:
- Import lines 8-9 (`TavilySearchOutcome`, `TavilySearchRepository`), constructor param line 37, `refreshTavilyPresence()` call lines 84-86.
- `deleteAllApiKeys` Tavily state reset lines 157-163 (no card state left to reset).

**Keep pattern** — error discipline for remaining functions (lines 146-170):

```kotlin
fun deleteAllApiKeys() {
    viewModelScope.launch(coroutineExceptionHandler) {   // CoroutineExceptionHandler, line 44-46
        _uiState.update { it.copy(isDeletingKeys = true) }
        try {
            val endpoints = endpointRepository.observeEndpoints().first()
            apiKeyStore.deleteAllKeys(endpoints.map { it.id })
            _uiState.update { it.copy(...) }             // message via context.getString(R.string.*)
        } catch (e: Exception) {
            _uiState.update { it.copy(isDeletingKeys = false, error = e.message) }
        }
    }
}
```

---

### `ui/settings/SettingsUiState.kt` (model, transform)

**Analog:** itself lines 20-27 — delete the 5 Tavily fields (`tavilyKeyInput`, `tavilyKeyPresent`, `tavilyTesting`, `tavilyStatus`, `tavilyStatusIsError`). No other file constructs these fields except `SettingsViewModel` (updated above) and tests (updated below). Keep `webGroundingEnabled` (line 19) — the DDG grounding toggle stays.

---

### `ui/settings/SettingsScreen.kt` (component, request-response)

**Analog:** itself — delete `TavilyKeyCard` composable (lines 402-478) and its call-site `item { TavilyKeyCard(uiState, viewModel) }` (lines 360-362). Planner decision: whether the "Web Search" section header (lines 353-359) stays as the home for the grounding toggle or is removed with the card — the grounding toggle itself lives at lines 211-212 (outside this section, untouched).

**Keep pattern** — section header + card styling for any surviving settings rows:

```kotlin
// Lines 354-358 — section header pattern (if the section survives):
item {
    SettingsSectionHeader(
        icon = Icons.Filled.Search,
        title = stringResource(R.string.settings_section_websearch)
    )
}
// Card pattern (lines 404-408): Card(fillMaxWidth, containerColor Color(0xFF2B2B29), RoundedCornerShape(12.dp))
// + Column(Modifier.padding(16.dp))
```

---

### `data/agentic/LocalToolLoop.kt` (utility, transform)

**Analog:** itself — update the outcome-typed functions to the new outcome type (rename or kept name):
- `searchSources` (lines 196-197), `searchImages` (lines 205-206), `mapSearchOutcome` (lines 221-231): delete `MissingKey`/`InvalidKey`/`UsageLimit` branches (lines 228-230); keep `Grounded` verbatim + `ModelOnly` OFFLINE/FETCH_FAILED mapping (lines 224-227).
- Copy consts (lines 70-84): delete/replace `MISSING_KEY_STRING` (lines 74-76), `INVALID_KEY_STRING` (lines 77-79), `USAGE_LIMIT_STRING` (lines 80-82). Keep `OFFLINE_STRING`, `FETCH_FAILED_STRING`, `MODEL_ONLY_STRING`.
- KDoc: lines 34-38 (wallet bound "5 Tavily credits" — rewrite to no-credit DDG), lines 66-69 (copy twins referencing MessageBubble banners being deleted).

```kotlin
// Lines 221-231 — AFTER shape:
fun mapSearchOutcome(outcome: SearchOutcome): String =
    when (outcome) {
        is SearchOutcome.Grounded -> outcome.fused.block
        is SearchOutcome.ModelOnly -> when (outcome.failed.reason) {
            GroundingResult.Reason.OFFLINE -> OFFLINE_STRING
            GroundingResult.Reason.FETCH_FAILED -> MODEL_ONLY_STRING
        }
    }
```

---

### `data/remote/provider/CompatToolLoop.kt` (service, request-response)

**Analog:** itself `executeRemoteTool` lines 234-302 — the DDG-call shape all 5 provider files share. Change: `maxResults = TavilySearchRepository.DEFAULT_MAX_RESULTS` (line 263) → relocated DDG constant; outcome type name if renamed. The `mapSearchOutcome`/`searchSources`/`searchImages` trio (lines 267-271) is unchanged in shape. Same edit applies to `OpenAIProvider.kt` (~line 364-368), `AnthropicProvider.kt` (~line 386-390), `LmStudioHelper.kt` (line 61 KDoc), plus KDoc-only mentions in `ProviderRouter.kt` (line 37), `OllamaProvider.kt` (line 59), `LMStudioProvider.kt` (line 66), `CustomProvider.kt` (line 56).

```kotlin
// Lines 261-271 — the call shape to preserve (constant source changes only):
val outcome = ddg.search(
    query = query,
    maxResults = DEFAULT_MAX_RESULTS,   // was TavilySearchRepository.DEFAULT_MAX_RESULTS
    contextSize = contextSize,
    includeImages = true,               // planner: keep or drop per DDG-repo decision
)
LocalToolLoop.ToolCallOutcome(
    text = LocalToolLoop.mapSearchOutcome(outcome),
    sources = LocalToolLoop.searchSources(outcome),
    images = LocalToolLoop.searchImages(outcome),
)
```

---

### `ui/chat/ChatViewModel.kt` (component, request-response)

**Analog:** itself DDG pre-search block lines 600-780. Change:
- Delete `MissingKey`/`InvalidKey`/`UsageLimit` branches (lines 729-752) + `TavilySearchRepository.DEFAULT_MAX_RESULTS` (line 654) → relocated constant.
- Delete image-intent key probe (lines 769-776: `apiKeyStore.getTavilyKey()` presence check → `IMAGES_NEED_KEY`); decide replacement notice or silent empty grid.
- Constructor: `ddgSearchRepository` (line 72) stays; `apiKeyStore` (line 81) — remove if no other VM use remains (verify: only other use is line 770 probe).
- Keep verbatim: offline gate (lines 619-647), `Grounded` fusion (lines 693-717), `ModelOnly` mapping (lines 718-728), anaphora anchor (lines 679-684), progress snapshot (lines 703-716).

---

### `ui/chat/components/MessageBubble.kt` (component, request-response)

**Analog:** itself banner `when` lines 466-500. Delete branches lines 479-489 (`TAVILY_MISSING_KEY` → `bubble_tavily_missing`, `IMAGES_NEED_KEY` → `bubble_images_need_key`, `TAVILY_INVALID_KEY` → `bubble_tavily_invalid`, `TAVILY_LIMIT` → `bubble_tavily_limit`). Keep `OFFLINE`, `FETCH_FAILED`, `TOOLS_UNSUPPORTED` branches + the OFFLINE-only retry gate (line 508).

---

### `domain/model/ChatMessage.kt` (model, transform)

**Analog:** itself `ModelOnlyNotice` lines 33-56 — remove `TAVILY_MISSING_KEY`, `IMAGES_NEED_KEY`, `TAVILY_INVALID_KEY`, `TAVILY_LIMIT`. **Safe:** the enum is ephemeral (line 15-16: "never persisted to Room"; `EntityMappers` maps field-by-field), so removing entries cannot break history load — unlike `GroundedSourceEntity`, which MUST stay for SEARCH-04.

---

### `res/values/strings.xml` + `res/values-es/strings.xml` (config, transform)

**Analog:** themselves — delete verbatim. EN (`values/strings.xml`): lines 258-261 (`bubble_tavily_missing`, `bubble_images_need_key`, `bubble_tavily_invalid`, `bubble_tavily_limit`) + lines 334-351 (`settings_tavily_*`, `tavily_*`). ES (`values-es/strings.xml`): lines 259-262 + 337-352 + help line 505 (`help_s7_step5` mentions Tavily — rewrite without Tavily per HELP-01 follow-up in Phase 64, or adjust here; planner discretion, flag the cross-phase link). Keep `settings_section_websearch` (EN 319) iff the Settings section survives; keep `bubble_tools_unsupported` (EN 262).

---

### `data/local/db/entity/GroundedSourceEntity.kt` (model, CRUD) — NO CHANGE

**Analog:** itself lines 29-50. SEARCH-04 requires zero schema change: the table stores resolved URLs + texts, never a producer tag, so legacy Tavily rows already render through the same path. The line 39 comment ("NULL means no OG captured (pre-58 rows, plain/markdown sources, Tavily rows)") stays valid documentation. `DatabaseModule.kt` line 60 migration chain untouched — no new `Migration` object.

---

### `ui/chat/components/OgSourceCard.kt` (component, transform) — SEARCH-04 generic label

**Analog:** itself lines 331-362 — the render-side fallback chain is the pattern for "generic source label, no Tavily branding" (CONTEXT decision). Legacy Tavily rows have `ogTitle = NULL` (pre-58 rows) → `ogDisplayTitle` already falls back to host, never crashes. No code change expected; planner verifies.

```kotlin
// Lines 331-335 + 354-362 — the generic-label precedent (COPY, don't modify):
/** Phase 58 (OG-02): render-side title fallback og:title -> host (never empty). */
fun ogDisplayTitle(ogTitle: String?, url: String): String {
    val title = ogTitle?.trim().orEmpty()
    return if (title.isNotEmpty()) title else ogHostOf(url)
}
/** Phase 58 (OG-02): host fallback for untitled sources; raw URL when unparseable. */
fun ogHostOf(url: String): String {
    val host = try {
        URI(url).host
    } catch (_: Exception) {
        null
    }
    return host?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: url
}
```

---

### Tests

**`app/src/test/java/com/warped/ui/settings/SettingsTavilyTest.kt`** — DELETE or rewrite to DDG per CONTEXT decision ("rewrite to DDG where the coverage still matters, delete Tavily-only tests"). Tavily-only coverage (key save/clear/test-connection against `storeTavilyKey`/`deleteTavilyKey`/mocked `tavilyRepo.search`, lines 177-279) is deleted with the feature. Coverage worth preserving in DDG form: ViewModel construction pattern (lines 94-143: MockK `apiKeyStore`, `context.getString` stubs, `runTest`) — reuse as the template for any new DDG-settings test.

**`app/src/test/java/com/warped/ui/settings/SettingsGroundingToggleTest.kt`** — MODIFY: remove `mockk<TavilySearchRepository>()` (line 63) + constructor arg (line 79); the grounding-toggle coverage itself stays.

**`app/src/test/java/com/warped/di/SingletonScopeRegressionTest.kt`** — MODIFY: drop `"provideTavilyOkHttpClient"` from `singletonBindings` (lines 47-51); the positive-control assertion (`containsExactlyElementsIn`) then pins the two surviving bindings. Keep the scan discipline (positive control so vacuous scans fail).

```kotlin
// Lines 46-59 — AFTER shape:
val singletonBindings = setOf(
    "provideOkHttpClient",
    "provideSseOkHttpClient",
)
```

---

## Shared Patterns

### Keystore access discipline (zero-fill CharArray, never log)
**Source:** `data/local/security/ApiKeyStore.kt` lines 10-16
**Apply to:** Startup Tavily-alias cleanup (use `KeystoreManager.remove`, which never throws — lines 54-60)
```kotlin
fun storeKey(endpointId: Long, apiKey: CharArray) {
    val alias = "api_key_$endpointId"
    val bytes = apiKey.concatToString().toByteArray(Charsets.UTF_8)
    apiKey.fill('0')
    keystoreManager.put(alias, bytes.toString(Charsets.UTF_8))
    bytes.fill(0)
}
```

### Hilt ViewModel error discipline
**Source:** `ui/settings/SettingsViewModel.kt` lines 44-46 + `deleteEndpointKey` lines 172-181
**Apply to:** Any new ViewModel function added during the cut (e.g., none expected — deletion only)
```kotlin
private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    Timber.e(throwable, "Unhandled coroutine exception")
}
```

### DDG repository resilience contract (preserve, do not modify)
**Source:** `data/grounding/DuckDuckGoSearchRepository.kt` lines 121-133 (offline gate), 163-170 (cancel rethrow + status-only logging), 86-98 (stripped client)
**Apply to:** `DuckDuckGoSearchRepository` post-cut — these blocks stay byte-identical
```kotlin
val online = try {
    webPageFetcher.hasValidatedInternet()
} catch (e: Exception) {
    Timber.w(e, "DDG: connectivity check failed, treating as offline")
    false
}
// ...
} catch (e: CancellationException) {
    // Cooperative cancel (Stop / new turn) — rethrow, never model-only.
    throw e
} catch (e: Exception) {
    // Never log the query contents — status only.
    Timber.e(e, "DDG: search fetch failed")
    null
}
```

### String-resource UI copy (EN+ES paired deletion)
**Source:** `app/src/main/res/values/strings.xml` lines 258-261 + 334-351; `values-es/strings.xml` lines 259-262 + 337-352
**Apply to:** Every removed user-facing Tavily surface must delete BOTH locales (SEARCH-01 grep-clean); `help_s7_step5` (ES 505) flagged for Phase 64 HELP-01 rewrite.

### Test-scan discipline (positive controls)
**Source:** `SingletonScopeRegressionTest.kt` lines 61-73 (scan + `assertThat(sources).isNotEmpty()` positive control)
**Apply to:** Post-cut grep-clean verification — any new scan test must fail on empty input, never pass vacuously.

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| Startup one-shot Keystore cleanup (SEARCH-03) | utility | file-I/O | No upgrade-migration/one-shot-flag precedent exists in the codebase (grep for `one-shot`/`cleanup`/`upgrade` finds only unrelated Snackbar/observer/download hits). Planner picks the spot: `WarpedApplication.onCreate()` (`WarpedApplication.kt` lines 72-106) via Hilt EntryPoint to `KeystoreManager`, or a DataStore-gated flag in an existing init path. Primitive (`KeystoreManager.remove`, best-effort/never-throws) and alias string (`"tavily_api_key"`) are both concrete — only the wiring location is novel. |

## Metadata

**Analog search scope:** `app/src/main/java/com/warped` (grounding, agentic, remote/api, remote/dto, remote/provider, local/security, local/db, di, ui/settings, ui/chat, domain/model), `app/src/main/res/values{,-es}`, `app/src/test/java/com/warped` (di, ui/settings)
**Files scanned:** ~40 files via targeted Tavily/tavily grep + direct reads of 16 files
**Pattern extraction date:** 2026-10-02
