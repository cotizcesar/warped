---
phase: 63-tavily-removal-ddg-only-search
reviewed: 2026-10-02T00:00:00Z
depth: standard
files_reviewed: 35
files_reviewed_list:
  - app/src/main/java/com/warped/data/grounding/DuckDuckGoSearchRepository.kt
  - app/src/main/java/com/warped/data/grounding/TavilySearchRepository.kt
  - app/src/main/java/com/warped/data/grounding/GroundedImages.kt
  - app/src/main/java/com/warped/data/grounding/MultiUrlFetcher.kt
  - app/src/main/java/com/warped/data/grounding/OpenGraphParser.kt
  - app/src/main/java/com/warped/data/grounding/SearchOgEnricher.kt
  - app/src/main/java/com/warped/data/agentic/LocalToolLoop.kt
  - app/src/main/java/com/warped/data/agentic/WebSearchToolSet.kt
  - app/src/main/java/com/warped/data/remote/api/TavilyApi.kt
  - app/src/main/java/com/warped/data/remote/dto/TavilyDtos.kt
  - app/src/main/java/com/warped/data/remote/provider/CompatToolLoop.kt
  - app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/AnthropicProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/LmStudioHelper.kt
  - app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt
  - app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/CustomProvider.kt
  - app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt
  - app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt
  - app/src/main/java/com/warped/data/local/db/Migrations.kt
  - app/src/main/java/com/warped/data/local/db/entity/GroundedSourceEntity.kt
  - app/src/main/java/com/warped/di/NetworkModule.kt
  - app/src/main/java/com/warped/domain/model/ChatMessage.kt
  - app/src/main/java/com/warped/domain/model/GroundedSource.kt
  - app/src/main/java/com/warped/domain/model/StreamToken.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/ui/chat/TurnStatus.kt
  - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
  - app/src/main/java/com/warped/ui/settings/SettingsViewModel.kt
  - app/src/main/java/com/warped/ui/settings/SettingsUiState.kt
  - app/src/main/java/com/warped/ui/settings/SettingsScreen.kt
  - app/src/main/java/com/warped/WarpedApplication.kt
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values-es/strings.xml
findings:
  critical: 0
  warning: 2
  info: 5
  total: 7
status: issues_found
---

# Phase 63: Code Review Report

**Reviewed:** 2026-10-02T00:00:00Z
**Depth:** standard
**Files Reviewed:** 35
**Status:** issues_found

## Summary

Reviewed the 5 phase-63 commits (494856d2, 747d3ce3, deed2513, 98666c1a, eba57eef):
Tavily client/DTO/producer deletion, DDG-only `SearchOutcome` contract, DI rebind,
`ApiKeyStore` accessor removal, startup orphan-alias cleanup, settings/UI/string
removal, and all caller rewires. Test files were spot-checked for rename consistency
only (no test findings per review policy).

The migration itself is sound: no remaining `tavily` tokens in main/test sources
except the single exempt alias literal, no dangling references to
`TavilySearchOutcome`/`TavilySearchRepository`/key accessors, all `when` expressions
over the shrunk sealed types remain exhaustive (compile passes), `ModelOnlyNotice`
is ephemeral-only (no Room persistence, so enum-value removal cannot break history
loads), and the DDG stripped-client policy (AuthInterceptor removal) is preserved.
No critical issues. Two warnings (startup main-thread Keystore IO; orphan-alias
deletion path split) and five info-level dead-code leftovers should be fixed.

## Warnings

### WR-01: Keystore/crypto IO runs synchronously on the main thread in Application.onCreate

**File:** `app/src/main/java/com/warped/WarpedApplication.kt:79,121-131`
**Issue:** `cleanupOrphanedSearchAlias()` is invoked directly from `onCreate()` on the
main thread. The first `KeystoreManager` access lazily builds `MasterKey` and
`EncryptedSharedPreferences` (keystore + disk IO, `encryptedPrefs.edit { remove(...) }`),
which can block startup for hundreds of milliseconds and risks ANR on slow/locked
devices — violating the project constraint "Never block UI thread during inference
or downloads" (applies equally to launch). The `catch (e: Exception)` guard also
lets `Error` (e.g., Hilt entry-point misconfiguration) escape and crash launch,
defeating the "never throws" contract in the KDoc.
**Fix:**
```kotlin
// In onCreate(): don't block startup on keystore IO.
backgroundScope.launch(Dispatchers.IO) { cleanupOrphanedSearchAlias() }
// ...and harden the guard:
} catch (e: Throwable) {
    Timber.w(e, "Orphaned search alias cleanup skipped")
}
```
(Any process-lifetime scope, `WorkManager`, or simple background executor works;
the removal is idempotent so async timing is safe.)

### WR-02: `deleteAllKeys` silently no longer deletes all keys; orphan alias has no UI deletion path

**File:** `app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt:28-30`
**Issue:** Before this phase, `deleteAllKeys()` also wiped the `tavily_api_key` alias;
now it only iterates endpoint IDs. The only remaining removal is the best-effort
startup cleanup in `WarpedApplication`, which swallows all failures. If that
startup removal ever fails persistently, the orphaned secret lingers in the
Keystore with no user-accessible way to delete it — and the function name
`deleteAllKeys` is now misleading (the Settings "delete all keys" action silently
leaves a key behind).
**Fix:**
```kotlin
fun deleteAllKeys(endpointIds: List<Long>) {
    endpointIds.forEach { deleteKey(it) }
    // Legacy alias orphaned by the Phase 63 DDG-only migration; harmless
    // if absent (remove is idempotent).
    keystoreManager.remove(LEGACY_SEARCH_ALIAS)
}
```
(Define `LEGACY_SEARCH_ALIAS = "tavily_api_key"` once in `ApiKeyStore` or
`KeystoreManager` and reference it from `WarpedApplication` too — see IN-05.)

## Info

### IN-01: Unused imports orphaned by the Tavily provider deletion in NetworkModule

**File:** `app/src/main/java/com/warped/di/NetworkModule.kt:8,12-13`
**Issue:** `okhttp3.MediaType.Companion.toMediaType`, `retrofit2.Retrofit`, and
`retrofit2.converter.kotlinx.serialization.asConverterFactory` are no longer
referenced anywhere in the file since `provideTavilyRetrofit`/`provideTavilyApi`
were deleted. (`import okhttp3.Response` at line 10 is also unused, but that
predates this phase.)
**Fix:** Delete the three import lines.

### IN-02: Orphaned `settings_section_websearch` string resources (EN + ES)

**File:** `app/src/main/res/values/strings.xml:315`, `app/src/main/res/values-es/strings.xml:317`
**Issue:** The Web Search section header strings are no longer referenced by any
code (the `TavilyKeyCard` section was removed from `SettingsScreen.kt`) but were
left in both locales. Unused resources trip `UnusedResources` lint and rot over time.
**Fix:** Delete both `<string name="settings_section_websearch">` entries.

### IN-03: Dead `MAX_IMAGES` constant on DuckDuckGoSearchRepository

**File:** `app/src/main/java/com/warped/data/grounding/DuckDuckGoSearchRepository.kt:347-348`
**Issue:** `MAX_IMAGES = 10` has zero readers — `fuse()` always emits the
`MultiUrlResult.Fused` default `images = emptyList()`, and the render side uses
`GroundedImages.MAX_GRID_IMAGES`. A reader will assume it caps something; it caps nothing.
**Fix:** Delete the constant (and, if desired, point the `GroundedImages`
"matches the fuse-time cap" KDoc at `MAX_GRID_IMAGES` instead).

### IN-04: Silently-ignored `includeImages` param with effect-free producer-side computation

**File:** `app/src/main/java/com/warped/data/grounding/DuckDuckGoSearchRepository.kt:105-111`, `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:634,670,682`
**Issue:** `includeImages` is now never read in `search()` — the value is a no-op.
Consequences: (a) `ChatViewModel` still runs `ImageIntent.hasImageIntent()` per
grounded turn (line 634) whose result feeds only this dead param; (b) four
tool-loop call sites pass the literal `includeImages = true` believing it requests
images, when it cannot produce any. The KDoc documents this, so it is a
maintainability trap rather than a bug — but the next reader will "fix" image
support by flipping a flag that does nothing.
**Fix:** Either remove the parameter (and the `wantImages` computation / pass
`false` at the ALWAYS-true loop sites), or mark intent explicitly, e.g.
`@Suppress("UNUSED_PARAMETER")` with a ` TODO(Phase XX: image provider)` pointer
so the no-op is greppable.

### IN-05: Bare `"tavily_api_key"` literal; "one-shot" KDoc is inaccurate

**File:** `app/src/main/java/com/warped/WarpedApplication.kt:114-131`
**Issue:** (a) The `TAVILY_ALIAS` constant was deleted with `ApiKeyStore`, leaving a
bare magic string — typo drift between writer/remover is now undetectable at
compile time. (b) The KDoc calls this "one-shot," but it runs on *every* launch
(idempotent, but each launch pays a Keystore read and the comment misleads).
(c) `catch (e: Exception)` does not cover `Error` (see WR-01).
**Fix:**
```kotlin
companion object { const val LEGACY_SEARCH_ALIAS = "tavily_api_key" }
// KDoc: "best-effort idempotent cleanup ... runs each launch; remove is a no-op when absent"
```

---

_Reviewed: 2026-10-02T00:00:00Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
