---
phase: 63-tavily-removal-ddg-only-search
plan: "02"
subsystem: search-grounding
tags: [tavily-removal, ddg-only, settings, keystore, legacy-citations]
requires: [63-01]
provides:
  - keyless settings (no Tavily card, state, or strings)
  - one-shot orphaned Tavily alias cleanup on startup
  - DDG-only caller graph with SearchOutcome end-to-end
  - DDG-only unit test suite (895 green)
affects: [phase-64-help-rewrite, release-uat-legacy-citations]
tech-stack:
  added: []
  patterns:
    - keyless single-producer search behind SearchOutcome sealed interface
    - Hilt EntryPoint for Application.onCreate Keystore access
    - legacy rows render through shared OG host-fallback chain
key-files:
  created:
    - app/src/test/java/com/warped/ui/chat/ChatSearchGroundingTest.kt
  modified:
    - app/src/main/java/com/warped/ui/settings/SettingsViewModel.kt
    - app/src/main/java/com/warped/ui/settings/SettingsUiState.kt
    - app/src/main/java/com/warped/ui/settings/SettingsScreen.kt
    - app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt
    - app/src/main/java/com/warped/WarpedApplication.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-es/strings.xml
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
    - app/src/main/java/com/warped/domain/model/ChatMessage.kt
    - app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt
    - app/src/main/java/com/warped/data/remote/provider/AnthropicProvider.kt
    - app/src/main/java/com/warped/data/remote/provider/LmStudioHelper.kt
    - app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt
    - app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt
    - app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt
    - app/src/main/java/com/warped/data/remote/provider/CustomProvider.kt
    - app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt
    - app/src/main/java/com/warped/data/agentic/WebSearchToolSet.kt
    - app/src/main/java/com/warped/data/grounding/MultiUrlFetcher.kt
    - app/src/main/java/com/warped/data/grounding/OpenGraphParser.kt
    - app/src/main/java/com/warped/data/grounding/SearchOgEnricher.kt
    - app/src/main/java/com/warped/data/local/db/Migrations.kt
    - app/src/main/java/com/warped/data/local/db/entity/GroundedSourceEntity.kt
    - app/src/main/java/com/warped/domain/model/GroundedSource.kt
    - app/src/main/java/com/warped/domain/model/StreamToken.kt
    - app/src/main/java/com/warped/ui/chat/TurnStatus.kt
  deleted:
    - app/src/test/java/com/warped/data/grounding/TavilySearchRepositoryTest.kt
    - app/src/test/java/com/warped/ui/settings/SettingsTavilyTest.kt
decisions:
  - EntryPoint/interface named without the Tavily token so the file stays grep-clean per SEARCH-01
  - SettingsTavilyTest chat gate-matrix coverage preserved as ChatSearchGroundingTest; key save/clear/test-connection coverage deleted with the removed ViewModel functions
  - ChatAlwaysSearchTest unkeyed image tests rewritten to the no-notice contract instead of deleted
metrics:
  duration: ~90min
  completed: 2026-10-02
---

# Phase 63 Plan 02: Tavily Surface Removal + DDG-only Callers Summary

Settings Tavily card, Keystore accessors, and EN+ES strings deleted; one-shot orphaned-alias cleanup wired into Application startup; every remaining caller rewired to the keyless `SearchOutcome` contract; Tavily-only tests deleted and the surviving suite rewritten DDG-only — 895 unit tests green with repo-wide Tavily grep-clean except the single alias literal.

## Completed Tasks

| Task | Name | Commit | Files |
| ---- | ---- | ------ | ----- |
| 1 | Remove settings Tavily surface, Keystore accessors, startup cleanup, and strings | deed2513 | SettingsViewModel/UiState/Screen, ApiKeyStore, WarpedApplication, strings.xml EN+ES |
| 2 | Rewire remaining callers, strip KDoc/comment mentions, and verify legacy citations | 98666c1a | ChatViewModel, MessageBubble, ChatMessage, 5 providers + LmStudioHelper + Router, LiteRTLmProvider, WebSearchToolSet, grounding ×3, entity + Migrations (comment-only), GroundedSource, StreamToken, TurnStatus |
| 3 | Update grounding/chat test suite to SearchOutcome and delete Tavily-only tests | eba57eef | 26 test files (2 deleted, 1 created, 23 modified) |

## Key Decisions

- **Grep-clean naming:** the Hilt EntryPoint and cleanup function are named `OrphanedSearchCleanupEntryPoint` / `cleanupOrphanedSearchAlias` — any `Tavily`-named identifier would itself violate the case-insensitive SEARCH-01 grep gate. The sole exempt token is the `"tavily_api_key"` alias literal passed to `KeystoreManager.remove`.
- **ChatViewModel constructor shrinks:** `apiKeyStore` was used only for the image-intent key probe, so the param (and its Hilt binding usage) was removed; two unlisted chat tests (`ThinkingVisibilityHealTest`, `ModelSwitchUnloadTest`) still passed the arg and were fixed as a Rule 3 compile break.
- **SettingsTavilyTest split, not just deleted:** its settings half (key save/clear/test-connection) tested removed ViewModel functions and was deleted; its ChatViewModel gate-matrix half (grounding-off, offline, fetch-failed, fusion wiring) still matters and was preserved as `ChatSearchGroundingTest` with `SearchOutcome` renames and no key-store field.
- **ChatAlwaysSearchTest image tests rewritten, not deleted:** the keyed cases were deleted per plan, but the unkeyed cases were rewritten to the new no-notice contract (text grounding preserved, empty grid, `modelOnlyNotice == null` / `FETCH_FAILED`) so the image-intent behavior stays pinned.
- **help_s7_step5 rewritten without branding:** EN "Web grounding adds search-result grounding with citations — no key needed." / ES "La búsqueda web agrega grounding con resultados de búsqueda y citas — sin clave." Zero new string resources added. The broader help-section rewrite stays flagged for Phase 64 HELP-01.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Removed stale `apiKeyStore` constructor args in two unlisted chat tests**
- **Found during:** Task 3 (post-edit compile sweep)
- **Issue:** `ThinkingVisibilityHealTest.kt` and `ModelSwitchUnloadTest.kt` passed `apiKeyStore = mockk<...>()` to `ChatViewModel`, whose constructor no longer has that param — compilation of the test source set would fail. Neither file contained a Tavily token, so the plan's file list missed them.
- **Fix:** Deleted the `apiKeyStore = ...` argument lines (1 + 2 occurrences); no behavior change.
- **Files modified:** `app/src/test/java/com/warped/ui/chat/ThinkingVisibilityHealTest.kt`, `app/src/test/java/com/warped/ui/chat/ModelSwitchUnloadTest.kt`
- **Commit:** eba57eef

**2. [Rule 1 - Bug] Fixed over-deletion of imports in LiteRTLmProvider.kt**
- **Found during:** Task 2 (immediately after the import edit)
- **Issue:** The edit removing `import TavilySearchRepository` also swallowed the adjacent `MultiUrlFetcher`/`WebPageFetcher` imports.
- **Fix:** Re-added the two imports on the next edit; verified by successful compile.
- **Files modified:** `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt`
- **Commit:** 98666c1a

**3. [Rule 1 - Bug] Fixed duplicated assertion while trimming LocalToolLoopTest image cases**
- **Found during:** Task 3 (read-back after edit)
- **Issue:** The key-case deletion left a duplicated, awkwardly formatted `ModelOnly(OFFLINE)` images assertion.
- **Fix:** Removed the duplicate, keeping a single assertion plus the empty-images Grounded case.
- **Files modified:** `app/src/test/java/com/warped/data/agentic/LocalToolLoopTest.kt`
- **Commit:** eba57eef

## Verification

- `grep -rni "tavily" app/src/main app/src/main/res | grep -v "tavily_api_key"` → zero matches (exit 1); the single exempt hit is `WarpedApplication.kt:127` `remove("tavily_api_key")`
- `grep -rni "tavily" app/src/test` → zero matches (exit 1); no `*tavily*` filenames under `app/src`
- Zero stale tokens: `TavilySearchOutcome`, `TavilySearchRepository`, `getTavilyKey`, `storeTavilyKey`, `deleteTavilyKey`, `TAVILY_ALIAS`, `provideTavilyOkHttpClient`, `IMAGES_NEED_KEY`, `TAVILY_*` → no matches
- `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin` → success
- `./gradlew :app:testDebugUnitTest` → BUILD SUCCESSFUL, **895 tests, 0 failures, 0 errors, 0 skipped** (includes rewritten `DuckDuckGoSearchRepositoryTest`, `RemoteSecretIsolationTest`, new `ChatSearchGroundingTest`)
- SEARCH-04 legacy path verified by inspection: `OgSourceCard` `ogDisplayTitle` → `ogHostOf` host-fallback chain intact (lines 84/199/332-355); `GroundedSourceEntity` schema and migration chain untouched (comment-only rewords); no logic changes to `DatabaseModule`, `SearchOgEnricher`, `MultiUrlFetcher`, `OpenGraphParser`
- Manual device confirmation of zero-key DDG grounding and legacy chat rendering remains a release-UAT item per plan (not a plan gate)

## Threat Flags

None — no new network endpoints, auth paths, or schema changes. The single new runtime surface is the startup `KeystoreManager.remove("tavily_api_key")` call, which is the plan's T-63-04 mitigation itself (best-effort, never throws; `KeystoreManager.remove` already swallows storage exceptions and the EntryPoint lookup is additionally guarded). No new dependencies added.

## Known Stubs

None.

## Self-Check: PASSED

- FOUND: all 27 main-source files + 2 strings locales modified on disk; `ChatSearchGroundingTest.kt` created; `TavilySearchRepositoryTest.kt` and `SettingsTavilyTest.kt` absent from disk
- FOUND: commits deed2513, 98666c1a, eba57eef in git log
- Deletions in eba57eef are exactly the two intentional test-file deletions
