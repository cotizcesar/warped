---
phase: 63-tavily-removal-ddg-only-search
verified: 2026-10-02T00:00:00Z
status: passed
score: 7/7 must-haves verified
overrides_applied: 0
---

# Phase 63: Tavily Removal → DDG-only Search Verification Report

**Phase Goal:** Users get grounded answers from DuckDuckGo-only search with no API-key friction
**Verified:** 2026-10-02
**Status:** passed
**Re-verification:** No — initial verification (includes review-fixer commits)

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Tavily client, DTO, and producer files no longer exist on disk | ✓ VERIFIED | `TavilyApi.kt`, `TavilyDtos.kt`, `TavilySearchRepository.kt` absent; `data/remote/api/` lists only Anthropic/Custom/LmStudio/Ollama/OpenAI; `data/grounding/` has no Tavily file; no `*tavily*` filenames under `app/src` |
| 2 | Keyless DDG search call returns Grounded or ModelOnly with no MissingKey path | ✓ VERIFIED | `SearchOutcome` sealed interface has exactly `Grounded`/`ModelOnly` (DuckDuckGoSearchRepository.kt:61-67); zero `MissingKey`/`InvalidKey`/`UsageLimit` tokens in main; `CompatToolLoop.kt:259-266` calls `ddg.search(maxResults = DuckDuckGoSearchRepository.DEFAULT_MAX_RESULTS)`; `LocalToolLoop.mapSearchOutcome/searchSources/searchImages` typed on `SearchOutcome`; `ChatViewModel` has zero `apiKeyStore` refs, `is SearchOutcome.Grounded/ModelOnly` branches intact |
| 3 | User never sees a Tavily key field, settings card, or API-key prompt for web search | ✓ VERIFIED | `SettingsViewModel/UiState/Screen` zero tavily hits; `TavilyKeyCard` deleted; `SettingsUiState.webGroundingEnabled` retained; `ChatMessage.ModelOnlyNotice` = OFFLINE/FETCH_FAILED/TOOLS_UNSUPPORTED only; `MessageBubble` keeps OFFLINE/FETCH_FAILED/TOOLS_UNSUPPORTED branches, zero TAVILY/IMAGES_NEED branches; EN+ES `strings.xml` zero tavily hits (incl. `help_s7_step5` rewritten) |
| 4 | Upgrading user keeps working search with no orphaned Tavily Keystore entry left behind | ✓ VERIFIED | `ApiKeyStore` exposes only per-endpoint store/get/deleteKey; `deleteAllKeys` also removes `KeystoreManager.LEGACY_SEARCH_ALIAS` (WR-02 fix); `WarpedApplication.cleanupOrphanedSearchAlias()` runs on every launch via `backgroundScope` (Dispatchers.IO, WR-01 fix) calling `keystoreManager().remove(LEGACY_SEARCH_ALIAS)` in a `catch (Throwable)` never-throws guard |
| 5 | Legacy chats with Tavily citations still open and render read-only without crashes | ✓ VERIFIED | `GroundedSourceEntity` schema untouched (comment-only reword to "legacy keyed-provider rows"); `Migrations.kt` comment-only reword, zero migration-object change; `OgSourceCard` `ogDisplayTitle → ogHostOf` host-fallback chain intact (lines 84/199/332-355); no logic changes to DatabaseModule/SearchOgEnricher/MultiUrlFetcher/OpenGraphParser |
| 6 | Case-insensitive grep for Tavily across app/src main+res returns zero except single alias literal | ✓ VERIFIED | `grep -rni "tavily" app/src/main app/src/main/res` → sole hit `KeystoreManager.kt:77` `LEGACY_SEARCH_ALIAS = "tavily_api_key"` (IN-05 fix: single source of truth referenced by both `ApiKeyStore` and `WarpedApplication`, so `WarpedApplication.kt` itself is now token-free); `grep -rni "tavily" app/src/test` → zero; stale-token sweep (`TavilySearchOutcome`, `TavilySearchRepository`, `get/store/deleteTavilyKey`, `TAVILY_ALIAS`, `provideTavilyOkHttpClient`, `IMAGES_NEED_KEY`, `TAVILY_*`) → zero |
| 7 | All 7 review findings fixed, no regressions | ✓ VERIFIED | WR-01: `backgroundScope.launch` + `catch (Throwable)` in `WarpedApplication.kt:85,128-139`; WR-02: legacy-alias removal in `deleteAllKeys`; IN-01: Tavily-retrofit imports deleted (`NetworkModule` retains only pre-existing `okhttp3.Response`); IN-02: `settings_section_websearch` strings deleted EN+ES; IN-03: dead `MAX_IMAGES` deleted, render cap is `GroundedImages.MAX_GRID_IMAGES`; IN-04: `includeImages` marked `@Suppress("UNUSED_PARAMETER")` + `TODO(Phase 64+)` pointer; IN-05: `LEGACY_SEARCH_ALIAS` constant + accurate "every launch, idempotent" KDoc. Fixer commits `af225ce5`, `390927da`, `dbf404cc` present in git log |

**Score:** 7/7 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `data/grounding/DuckDuckGoSearchRepository.kt` | Standalone DDG-only producer, keyless SearchOutcome | ✓ VERIFIED | Companion `DEFAULT_MAX_RESULTS=5`, `MAX_RESULTS_CAP=10`, `MAX_QUERY_CHARS` present; `MAX_IMAGES` correctly deleted per IN-03; no Tavily constructor params; wired via `ddg.search` from CompatToolLoop, ChatViewModel, LiteRTLmProvider |
| `di/NetworkModule.kt` | DI graph without Tavily bindings | ✓ VERIFIED | Zero Tavily tokens; Tavily-retrofit imports removed; `@Named("sse")` binding intact |
| `data/local/security/ApiKeyStore.kt` | Keystore without Tavily accessors | ✓ VERIFIED | No store/get/deleteTavilyKey, no TAVILY_ALIAS; `deleteAllKeys` covers legacy alias |
| `WarpedApplication.kt` | Orphaned-alias cleanup on startup | ✓ VERIFIED | Background (IO) idempotent cleanup, never-throws, via `OrphanedSearchCleanupEntryPoint` (Tavily-token-free name) |
| `ui/settings/SettingsScreen.kt` | Settings without Tavily card | ✓ VERIFIED | No `TavilyKeyCard`, grounding toggle retained |
| `TavilySearchRepositoryTest.kt` / `SettingsTavilyTest.kt` | Deleted | ✓ VERIFIED | Both absent; grounding dir lists `DuckDuckGoSearchRepositoryTest` only; settings test dir lists `SettingsGroundingToggleTest` only; new `ChatSearchGroundingTest` preserves gate-matrix coverage |
| `GroundedSourceEntity.kt` / `Migrations.kt` | Zero schema change | ✓ VERIFIED | Comment-only rewords; migration chain untouched |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `WarpedApplication.kt` | `KeystoreManager.remove` | `LEGACY_SEARCH_ALIAS` constant ref | WIRED | `entryPoint.keystoreManager().remove(KeystoreManager.LEGACY_SEARCH_ALIAS)` line 134 |
| `ApiKeyStore.deleteAllKeys` | `KeystoreManager.remove` | legacy alias | WIRED | Same constant, idempotent |
| `CompatToolLoop.kt` | `DuckDuckGoSearchRepository.search` | `ddg.search` + relocated constant | WIRED | Lines 259-266 |
| `MessageBubble.kt` | `GroundedSourceEntity` render path | `ogDisplayTitle`/`ogHostOf` | WIRED | Lines 84/199/332-355 intact |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `DuckDuckGoSearchRepository.search` | `SearchOutcome.Grounded(fused)` | DDG HTML/JSON fetch → `parseResults`/`resolveResultUrl` → `fuse()` (untouched per threat model) | ✓ FLOWING | Keyless path; only failure outcomes are `ModelOnly(OFFLINE/FETCH_FAILED)` |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Repo-wide Tavily grep-clean (main+res) | `grep -rni "tavily" app/src/main app/src/main/res` | Single exempt `LEGACY_SEARCH_ALIAS` literal only | ✓ PASS |
| Test-source Tavily grep-clean | `grep -rni "tavily" app/src/test` | Zero matches (exit 1) | ✓ PASS |
| Stale-token sweep | `grep -rn "TavilySearchOutcome\|…\|TAVILY_" app/src` | Zero matches (exit 1) | ✓ PASS |
| Unit suite (895 tests, 0 failures) | Per 63-02-SUMMARY `./gradlew :app:testDebugUnitTest` | Claimed BUILD SUCCESSFUL — not re-run here (Gradle suite exceeds spot-check budget; compile-level evidence + zero stale refs corroborate) | ? SKIP (deferred to release-UAT alongside device smoke) |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| SEARCH-01 | 63-01 + 63-02 | Tavily fully removed, grep-clean | ✓ SATISFIED | Deletions + grep evidence above |
| SEARCH-02 | 63-01 | DDG-only grounded answers, keyless | ✓ SATISFIED | `SearchOutcome` Grounded/ModelOnly, three callers wired, no key probe |
| SEARCH-03 | 63-02 | No orphaned Tavily Keystore entry | ✓ SATISFIED | Startup cleanup + `deleteAllKeys` coverage, both idempotent |
| SEARCH-04 | 63-02 | Legacy Tavily citations render read-only | ✓ SATISFIED | Schema-unchanged + host-fallback chain intact (static verification; live render is release-UAT) |

### Anti-Patterns Found

None. No `TODO/FIXME/XXX/placeholder` introduced; `TODO(Phase 64+: …)` on `includeImages` is the review-mandated IN-04 explicit pointer with phase attribution, not an unresolved stub.

### Human Verification Required

None blocking. The only non-statically-verifiable items — live zero-key DDG grounding round-trip on hardware and visual legacy-chat rendering — are explicitly declared **release-UAT items, not plan gates**, in 63-02-PLAN §verification ("Manual device confirmation … is a release-UAT item per house precedent, not a plan gate"). Per house precedent (v2.2 device-smoke deferral, user-accepted), these ride the standing release-UAT backlog (POL-04), not this verification.

### Gaps Summary

No gaps. All four roadmap success criteria hold in the codebase; all 7 review findings are fixed in-tree with fixer commits present; grep gates pass with the single architecturally-intended `LEGACY_SEARCH_ALIAS = "tavily_api_key"` constant as the sole Tavily-substring occurrence.

---
_Verified: 2026-10-02_
_Verifier: the agent (gsd-verifier)_
