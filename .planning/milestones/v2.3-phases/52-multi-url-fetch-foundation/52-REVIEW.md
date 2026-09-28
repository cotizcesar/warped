---
phase: 52-multi-url-fetch-foundation
reviewed: 2026-09-28T00:00:00Z
depth: standard
files_reviewed: 14
files_reviewed_list:
  - app/src/main/java/com/warped/data/grounding/GroundingBudget.kt
  - app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt
  - app/src/main/java/com/warped/data/grounding/GroundingResult.kt
  - app/src/main/java/com/warped/data/grounding/HtmlToTextExtractor.kt
  - app/src/main/java/com/warped/data/grounding/MultiUrlFetcher.kt
  - app/src/main/java/com/warped/data/grounding/UrlDetector.kt
  - app/src/main/java/com/warped/data/grounding/WebPageFetcher.kt
  - app/src/main/java/com/warped/domain/model/ChatMessage.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/main/java/com/warped/ui/chat/ChatUiState.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
  - gradle/libs.versions.toml
  - app/build.gradle.kts
findings:
  critical: 0
  warning: 4
  info: 3
  total: 7
status: fixed
---

# Phase 52: Code Review Report

**Reviewed:** 2026-09-28
**Depth:** standard
**Files Reviewed:** 14
**Status:** fixed

## Summary

Reviewed the Phase 52 multi-URL fetch foundation (plans 01 + 02): Jsoup parse-only extractor, `allUrls` fan-out input, global grounding budget, fused prompt blocks, `MultiUrlFetcher` orchestrator, concurrent cancel set, ViewModel hook swap, and N-source chip/Fuentes/banner surfaces.

**Threat-area verification (all preserved):** `grep` confirms zero `Jsoup.connect` usages outside a doc comment (only `Jsoup.parse` in `HtmlToTextExtractor`); the `AuthInterceptor` strip (`WebPageFetcher.kt:65`) is verbatim; the 64KB cap streamed in 8KB chunks (`WebPageFetcher.kt:133-139`) is intact; the cancel set uses a `ConcurrentHashMap` key set with add-after-`newCall`/remove-in-`finally`/snapshot-cancel (`WebPageFetcher.kt:75-85,101,151-153`); history keeps persisted originals with only the outgoing request copy augmented (`ChatViewModel.kt:428-433`); and `git diff` shows zero changes under `data/remote/` (providers/helpers untouched). Redirect scheme gate (http/https only, 3-hop cap) and per-page sanitizer placement are also intact.

No Critical (ship-blocking) defects found. Four Warnings (one spec-surface gap, two unchecked-public-input robustness holes, one maintainability duplication) and three Info notes below.

## Warnings

### WR-01: Per-source OK/OMITIDA states are written but never rendered, then wiped

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:319-347`
**Issue:** The Fused branch rebuilds `perSource` with final OK/OMITIDA states (lines 319-335), but the enclosing `finally` (lines 345-347) immediately nulls `webFetchProgress`, so the update is transient at best. Worse, no composable ever reads `.perSource` (verified by repo-wide grep — `ChatScreen.kt:241-254` renders only `done`/`total`), and the live `onProgress` callback carries only counts, so per-source rows sit at LOADING for the whole fetch and can never transition live. FETCH-03's "per-source ok/omitida states" surface is therefore unobservable dead state.
**Fix:** Either thread the finished URL through the callback and update the matching row live:
```kotlin
onProgress = { finishedUrl, done, total ->
    updateInput { s ->
        s.copy(webFetchProgress = s.webFetchProgress?.copy(
            done = done,
            total = total,
            perSource = s.webFetchProgress?.perSource?.map {
                if (it.url == finishedUrl) it.copy(status = PerSourceStatus.OK) else it
            } ?: emptyList(),
        ))
    }
}
```
(or keep OMITIDA marking for the terminal pass), or render `perSource` in the chip area; alternatively delete the `perSource` field if N-de-M counts are the intended final UX.

### WR-02: New public `extract(html, url, budget)` / `fetch(url, budget)` accept unchecked budgets that crash truncation

**File:** `app/src/main/java/com/warped/data/grounding/HtmlToTextExtractor.kt:74-77`
**Issue:** When `text.length > budget`, the code computes `truncateAtLineBoundary(text, budget - TRUNCATION_MARKER.length - 1)`. For any `budget < TRUNCATION_MARKER.length + 1` (≈14), the argument goes negative and `String.take(negative)` throws `IllegalArgumentException`, converting a grounding turn into an exception path. `WebPageFetcher.fetch(url, budget)` (`WebPageFetcher.kt:87,141`) forwards its parameter unchecked into this call, and both functions are public API. Currently unreachable via `MultiUrlFetcher` (`perPageBudget` floors at 800), but the new overloads are one careless caller away from a crash.
**Fix:**
```kotlin
fun extract(html: String, url: String, budget: Int): String {
    val safeBudget = budget.coerceAtLeast(GroundingBudget.MIN_PER_PAGE)
    val jsoupResult = extractJsoup(html, url, safeBudget)
    if (jsoupResult.isNotBlank()) return jsoupResult
    return extractLegacy(html, safeBudget)
}
```
(Or `require(budget > TRUNCATION_MARKER.length + 1)` to fail fast at the call site instead of deep in truncation.)

### WR-03: `UrlDetector.allUrls(text, max)` throws on negative `max`

**File:** `app/src/main/java/com/warped/data/grounding/UrlDetector.kt:29-35`
**Issue:** `.take(max)` requires `max >= 0` (`require` inside stdlib) — `allUrls(text, max = -1)` throws `IllegalArgumentException`. `firstUrl()` has no such parameter so the asymmetry is new public surface introduced by this phase. All current callers use the default, so this is latent, not live.
**Fix:**
```kotlin
fun allUrls(text: String, max: Int = 5): List<String> =
    URL_REGEX.findAll(text)
        .map { it.value.trimEnd('.', ',', ';', ':', '!', '?') }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(max.coerceAtLeast(0))
        .toList()
```

### WR-04: The cap-5 lives in two places that can silently diverge

**File:** `app/src/main/java/com/warped/data/grounding/UrlDetector.kt:29` + `app/src/main/java/com/warped/data/grounding/MultiUrlFetcher.kt:59,104-107`
**Issue:** `allUrls(text, max = 5)` and `MultiUrlFetcher.MAX_URLS = 5` encode the same FETCH-01 contract independently. `ChatViewModel` sizes its progress UI from the `allUrls` result but the fetcher re-caps internally — if either constant ever changes alone, progress `total` and actually-fetched count diverge (phantom slots or untracked fetches). Should be a single source of truth.
**Fix:** Make the detector default reference the orchestrator constant (or a shared `GroundingPolicy.MAX_URLS`):
```kotlin
fun allUrls(text: String, max: Int = MultiUrlFetcher.MAX_URLS): List<String> = ...
```

## Info

### IN-01: Single-URL production path now extracts above the legacy 4000-char ceiling

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:283,300`
**Issue:** The hook routes ALL cases (including a single pasted URL) through `multiUrlFetcher.fetchAll`, so single-page extraction now runs at `perPageBudget` (4500–6000+ chars) instead of the frozen `HtmlToTextExtractor.MAX_CHARS = 4000`. This follows EXTRACT-02's intent (global budget replaces the fixed constant) and the `fetch(url)` default is kept for compat, but it is a deliberate behavior change worth recording: `fetch(url)` currently has no production caller.
**Fix:** No fix needed — informational. Optionally note in CONTEXT.md that the 4000 default is now test/compat-only surface.

### IN-02: `modelOnlySourceCount` is ephemeral — banner plural degrades to singular after history reload

**File:** `app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt:27-49`
**Issue:** Verified `MessageEntity.toDomain()`/`ChatMessage.toEntity()` drop `groundedSources`, `modelOnlyNotice`, AND the new `modelOnlySourceCount`. After `selectConversation` reloads history, an all-fail turn renders the singular banner copy. This is consistent with the Phase 50 ephemeral design and the "no Room migration in Phase 52" guardrail — not a regression — but Phase 53 persistence should carry all three fields together, not just `groundedSources`.
**Fix:** No fix in this phase; carry `modelOnlySourceCount` (and `modelOnlyNotice`) into the Phase 53 persistence scope alongside `groundedSources`.

### IN-03: Fuentes show post-redirect resolved URLs, progress tracks pasted URLs

**File:** `app/src/main/java/com/warped/data/grounding/MultiUrlFetcher.kt:80,98`
**Issue:** `okUrls` are built from `result.url` (post-redirect, `WebPageFetcher` follows up to 3 hops) while `skippedUrls` uses the pasted URL. Block order == Fuentes order holds (both derive from `okPages`), but after a redirect the Fuentes entry differs from the URL the user pasted. Harmless and arguably more correct (cites the actually-read page), but the mixed provenance (resolved for ok, pasted for skipped) is worth a conscious sign-off.
**Fix:** No fix required; if paste-fidelity is preferred, use `pastedUrl` for `okUrls` and pass the resolved URL only into the framed block.

---

_Reviewed: 2026-09-28_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
