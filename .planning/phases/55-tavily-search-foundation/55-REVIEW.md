---
phase: 55-tavily-search-foundation
reviewed: 2026-09-28T23:55:00Z
depth: standard
files_reviewed: 13
files_reviewed_list:
  - app/src/main/java/com/warped/data/remote/dto/TavilyDtos.kt
  - app/src/main/java/com/warped/data/remote/api/TavilyApi.kt
  - app/src/main/java/com/warped/data/grounding/TavilySearchRepository.kt
  - app/src/main/java/com/warped/di/NetworkModule.kt
  - app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt
  - app/src/main/java/com/warped/ui/settings/SettingsViewModel.kt
  - app/src/main/java/com/warped/ui/settings/SettingsScreen.kt
  - app/src/main/java/com/warped/ui/settings/SettingsUiState.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/domain/model/ChatMessage.kt
  - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
  - app/src/test/java/com/warped/data/grounding/TavilySearchRepositoryTest.kt
  - app/src/test/java/com/warped/ui/settings/SettingsTavilyTest.kt
findings:
  critical: 0
  warning: 3
  info: 4
  total: 7
status: fixed
---

# Phase 55: Code Review Report

**Reviewed:** 2026-09-28T23:55:00Z
**Depth:** standard
**Files Reviewed:** 13
**Status:** fixed (WR-01..WR-03 applied 2026-09-29; IN-01..IN-04 informational, no action)

## Summary

Reviewed the full Phase 55 scope (55-01 Tavily DTOs/API/named client/Keystore alias/search-to-fused producer; 55-02 Settings key UI + ChatViewModel search branch + notices/banners). The security-critical contract holds: dedicated `@Named("tavily")` client carries zero interceptors (no `AuthInterceptor`, no body logger), auth is a per-call `Bearer` header, key material never appears in logs (only status-code `Timber` calls), snippets pass through `WebContextSanitizer`, `deleteAllKeys` wipes the Tavily alias, the Settings field is password-style with no stored-key echo, and the chat branch is correctly gated (grounding-precedence → validated-online → repository key check, no socket offline or key-missing). No Critical issues. Three Warnings (blank-query/keystore ordering contradicts the SUMMARY guarantee, untrimmed key save, non-URL labels leaking into the `skippedUrls` URL contract) and four Info items, all with concrete fixes.

## Warnings

### WR-01: Blank-query short-circuit runs AFTER the keystore read, contradicting the documented guarantee

**File:** `app/src/main/java/com/warped/data/grounding/TavilySearchRepository.kt:75-88`
**Issue:** The 55-01 SUMMARY claims "empty/blank queries return FETCH_FAILED model-only before touching the keystore key or socket". The code does the opposite: it reads `getTavilyKey()`, materializes the immutable `key` String via `concatToString()` (line 80), and only then checks `trimmedQuery.isBlank()` (line 84). No socket opens and no credit burns, so the paid-credit outcome is safe — but an immutable, un-zeroable `String` copy of the key is created on every blank query for no reason, and the documented "before touching the keystore" guarantee is false. The blank check is also applied to the length-capped (`take(500)`) query rather than the raw query, which is harmless but sloppy.
**Fix:**
```kotlin
suspend fun search(...): TavilySearchOutcome = withContext(ioDispatcher) {
    val trimmedQuery = query.take(MAX_QUERY_CHARS)
    if (trimmedQuery.isBlank()) {
        return@withContext TavilySearchOutcome.ModelOnly(
            MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
        )
    }
    val keyChars = apiKeyStore.getTavilyKey()
    ...
}
```

### WR-02: `saveTavilyKey` stores the input untrimmed — pasted whitespace breaks the key

**File:** `app/src/main/java/com/warped/ui/settings/SettingsViewModel.kt:210-220`
**Issue:** The blank guard uses `input.isBlank()` but the stored value is the raw `input.toCharArray()`. On mobile, pasting a key routinely carries a leading/trailing space or trailing newline; the stored key then fails with 401 (surfaced as "Invalid API key") even though the user pasted the correct key. The blank check passing while the stored value differs from the checked value is a classic validate-vs-use mismatch.
**Fix:**
```kotlin
fun saveTavilyKey() {
    val input = _uiState.value.tavilyKeyInput.trim()
    if (input.isBlank()) { ... return }
    ...
}
```

### WR-03: `fuse()` puts non-URL labels into `skippedUrls`, breaking the URL-list contract

**File:** `app/src/main/java/com/warped/data/grounding/TavilySearchRepository.kt:138-150`
**Issue:** When a result has a blank URL, `label` falls back to the title or `"(unknown source)"` and that label is added to `skipped` (line 145), which becomes `skippedUrls` in `MultiUrlResult.Fused`. Downstream, `ChatViewModel` maps `skippedUrls` into `SourceFetchState(url, OMITIDA)` progress rows (ChatViewModel.kt:544) and counts `okUrls.size + skippedUrls.size` as source totals — both consumers expect URLs. The `GroundedSource(url = label, ...)` OMITIDA row with `"(unknown source)"` as its URL likewise pollutes the Fuentes persist path with a non-URL. Titles-as-URLs in progress/rows is user-visible noise at best, a broken link row at worst.
**Fix:** Keep `skippedUrls` URL-only and carry the display label solely in the details row, e.g.:
```kotlin
if (url.isBlank() || content.isBlank()) {
    val label = url.ifBlank { result.title.trim().ifBlank { UNKNOWN_SOURCE } }
    if (url.isNotBlank()) skipped.add(url)
    GroundedSource(url = label, extractedText = null, status = GroundedSourceStatus.OMITIDA)
}
```
(If progress must show a row per skipped item, add a separate display-label list rather than overloading the URL list.)

## Info

### IN-01: `OFFLINE` branch in the search `ModelOnly` mapping is unreachable

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:551-555`
**Issue:** The repository only ever emits `MultiUrlResult.AllFailed(FETCH_FAILED)` — it has no connectivity check and never returns `Reason.OFFLINE` (offline is handled by the ViewModel gate above). The `OFFLINE -> ModelOnlyNotice.OFFLINE` arm is dead but harmless defensive code. Keep or remove deliberately; if kept, add a comment noting it is defensive.
**Fix:** Add `// Defensive: repository currently only emits FETCH_FAILED` or drop the arm.

### IN-02: Test-connection has no offline pre-gate — attempts a socket while offline

**File:** `app/src/main/java/com/warped/ui/settings/SettingsViewModel.kt:278-304`
**Issue:** Unlike the chat branch (online gate before `search()`), `testTavilyConnection()` calls `search()` unconditionally. Tapped while offline it opens a real socket attempt that fails into the generic "Network error" state. No credit is burned (request never completes) and the UX outcome is correct, but a `ConnectivityManager` capability check first would fail faster and keep the "no socket when offline" discipline uniform.
**Fix:** Inject/read the same validated-internet signal and short-circuit to the network-error state before calling `search()`.

### IN-03: `query.take(500)` can split a surrogate pair at the truncation boundary

**File:** `app/src/main/java/com/warped/data/grounding/TavilySearchRepository.kt:83`
**Issue:** `take(MAX_QUERY_CHARS)` cuts on UTF-16 code units; a query with emoji/CJK at exactly the boundary can produce a lone surrogate sent to the API. Cosmetic edge case (Tavily will still answer), but a code-point-safe truncation is trivial.
**Fix:** Truncate on code points, or at minimum strip a trailing lone surrogate after `take()`.

### IN-04: Immutable `String` copies of the key linger until GC (`concatToString` + `"Bearer $key"`)

**File:** `app/src/main/java/com/warped/data/grounding/TavilySearchRepository.kt:80-95`
**Issue:** `keyChars` is zeroed, but `val key = keyChars.concatToString()` and the interpolated `"Bearer $key"` header value are immutable `String`s that cannot be zeroed and linger until GC. This mirrors the pre-existing `AuthInterceptor` discipline (and the SUMMARY acknowledges immutable copies as unavoidable for the Settings `String` state), so it matches project precedent — recorded here for completeness, not as a defect. A documented threat-model note (T-55-01) stating "heap copies in immutable Strings are GC-lifetime, mitigated by state clearing + no logging" would close the loop.
**Fix:** No code change required; optionally note the residual in the threat model. If hardening further, clear via `CharArray`-based header assembly where OkHttp APIs permit.

---

_Verified clean (no finding): zero interceptors on the Tavily client (NetworkModule.kt:136-143); per-call Bearer header, no `api_key` body (TavilyApi.kt:23-26); key never logged — only `Timber.e("Tavily: search failed with HTTP %d", ...)` and `Timber.e(e, "Tavily: search call failed")` status-only calls (TavilySearchRepository.kt:101,119); snippet sanitization producer-side (line 152-155); basic-depth default + `coerceIn(1, 10)` cap (TavilyDtos.kt:20, TavilySearchRepository.kt:89); `CancellationException` rethrown (line 115-116); `deleteAllKeys` wipes Tavily alias (ApiKeyStore.kt:28-31); password field + no key echo + input cleared on save/clear (SettingsScreen.kt:397, SettingsViewModel.kt:227,252); retry stays OFFLINE-only — `retryGrounding` gates on `modelOnlyNotice == OFFLINE` and bails on empty URL lists, so TAVILY notices never retry (ChatViewModel.kt:869,882); distinct 401/429 outcomes end-to-end (repository → ViewModel → banner copy)._
_Reviewed: 2026-09-28T23:55:00Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
