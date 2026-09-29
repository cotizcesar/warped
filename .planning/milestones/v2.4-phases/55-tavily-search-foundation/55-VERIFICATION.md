---
phase: 55-tavily-search-foundation
verified: 2026-09-29T00:00:00Z
status: passed
score: 3/3 must-haves verified
overrides_applied: 0
re_verification: true
previous_status: human_needed
previous_score: 3/3
human_verification:
  - test: "Live Tavily key validity (API-level)"
    expected: "POST /search returns 200 with real results"
    why_human: "Device E2E (grounded turn, airplane, missing-key) remains user-side; key itself proven live 2026-09-29"
    result: "PASS — HTTP 200, results returned ( Merriam-Webster test query); key material never persisted"
device_followups:
  - "Grounded no-URL turn with Fuentes rows on device"
  - "Airplane-mode offline path on device"
  - "Missing-key message on device"
---

# Phase 55: Tavily Search Foundation Verification Report

**Phase Goal:** Users ground answers in Tavily search results with a stored API key
**Verified:** 2026-09-29T00:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User stores a Tavily key in Settings (Keystore-encrypted) with a working test-connection | ✓ VERIFIED | `ApiKeyStore.storeTavilyKey/getTavilyKey/deleteTavilyKey` under `tavily_api_key` alias via KeystoreManager; `deleteAllKeys` wipes alias (ApiKeyStore.kt:28-56). SettingsViewModel `saveTavilyKey` (trimmed, chars zeroed, input cleared), `clearTavilyKey`, `testTavilyConnection` probe `search("test", maxResults=1)` with 5-outcome English mapping (SettingsViewModel.kt:210-309). SettingsTavilyTest 17/17 green. |
| 2 | User gets answers grounded in top-N Tavily results with numbered citations through the same Fuentes/preview pipeline | ✓ VERIFIED | `TavilyApi` POST search with per-call `Authorization: Bearer` header (TavilyApi.kt:21-26). `TavilySearchRepository.search()` maps results to `(url, text)` pairs via `WebContextSanitizer` + `GroundingBudget.perPageBudget` + `GroundingPrompt.buildFusedBlock` (TavilySearchRepository.kt:126-188); 401→InvalidKey, 429→UsageLimit. ChatViewModel Grounded branch uses identical `augment` → `groundedSources/details` persist path as URL grounding (ChatViewModel.kt:527-550). TavilySearchRepositoryTest 16/16 green. |
| 3 | Search never runs when grounding is off or offline, with a clear missing/invalid-key message | ✓ VERIFIED | Search branch gated on `doGround` (GroundingPrecedence, same as fetch) + `hasValidatedInternet()` online check → OFFLINE notice, no socket (ChatViewModel.kt:476-506). MissingKey → `TAVILY_MISSING_KEY` (tavily.com + Settings path), InvalidKey → `TAVILY_INVALID_KEY`, UsageLimit → `TAVILY_LIMIT` (ChatViewModel.kt:562-585; MessageBubble.kt:393-399). Retry stays OFFLINE-only (ChatViewModel.kt:869,882). Gate-matrix tests green. |

**Score:** 3/3 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `data/remote/dto/TavilyDtos.kt` | Request/Result/Response DTOs | ✓ VERIFIED | Exists, substantive; basic/5 defaults, `coerceIn(1,10)` cap producer-side |
| `data/remote/api/TavilyApi.kt` | Retrofit POST search, per-call Bearer | ✓ VERIFIED | Exists; no `api_key` body; wired into repository + `@Named("tavily")` Retrofit |
| `data/grounding/TavilySearchRepository.kt` | search-to-fused producer + outcome sealed interface | ✓ VERIFIED | Exists; WR-01 (blank-check before keystore) and WR-03 (skippedUrls URL-only) fixes confirmed in code (lines 75-81, 145) |
| `data/local/security/ApiKeyStore.kt` | Tavily alias CRUD | ✓ VERIFIED | Exists; `TAVILY_ALIAS = "tavily_api_key"`; `deleteAllKeys` wipes it |
| `di/NetworkModule.kt` | Dedicated Tavily client, zero interceptors | ✓ VERIFIED | `@Named("tavily")` OkHttp with zero interceptors (lines 124-136); no AuthInterceptor/body logger leakage |
| `ui/settings/SettingsViewModel.kt` | Key state + save/clear/test | ✓ VERIFIED | WR-02 trim fix confirmed (line 211 `tavilyKeyInput.trim()`); chars zeroed; input cleared |
| `ui/settings/SettingsScreen.kt` | Web Search card UI | ✓ VERIFIED | TavilyKeyCard above Security; password field; no stored-key echo; 1-credit disclosure |
| `ui/settings/SettingsUiState.kt` | Tavily UI state fields | ✓ VERIFIED | tavilyKeyInput/KeyPresent/Testing/Status/StatusIsError present |
| `ui/chat/ChatViewModel.kt` | Search branch with gates | ✓ VERIFIED | Branch at `urls.isEmpty()` bypass; online gate; exhaustive outcome `when`; identical fusion path on IO |
| `domain/model/ChatMessage.kt` | TAVILY_* notices | ✓ VERIFIED | `TAVILY_MISSING_KEY / TAVILY_INVALID_KEY / TAVILY_LIMIT` in ModelOnlyNotice |
| `ui/chat/components/MessageBubble.kt` | Banner copy per notice | ✓ VERIFIED | Actionable English copy with tavily.com + Settings > Web Search path |
| `data/grounding/TavilySearchRepositoryTest.kt` | 16 JVM tests | ✓ VERIFIED | 16/16, 0 failures (test-results XML) |
| `ui/settings/SettingsTavilyTest.kt` | 17 JVM tests | ✓ VERIFIED | 17/17, 0 failures (test-results XML) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| TavilySearchRepository | TavilyApi | per-call `"Bearer $key"` header | WIRED | Line 95; key read precedes call; MissingKey short-circuits pre-socket |
| TavilySearchRepository | GroundingPrompt.buildFusedBlock | `fuse()` okPairs | WIRED | Lines 179-187; identical numbered Source [N] contract |
| TavilySearchRepository | WebContextSanitizer | snippet sanitize producer-side | WIRED | Lines 152-155 |
| ChatViewModel | TavilySearchRepository.search | `urls.isEmpty()` branch | WIRED | Lines 520-526; `query=userMessage.content, maxResults=5, contextSize` |
| ChatViewModel Grounded | GroundingPrompt.augment + persist | identical path as URL grounding | WIRED | Lines 527-550; groundedSources/details union |
| SettingsViewModel | ApiKeyStore | store/get/delete Tavily alias | WIRED | save/clear/presence round-trip |
| SettingsViewModel test | TavilySearchRepository.search | probe maxResults=1 | WIRED | Line 289; 5-outcome mapping |
| MessageBubble | ModelOnlyNotice TAVILY_* | exhaustive `when` banner | WIRED | Lines 393-399 |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|-------------------|--------|
| TavilySearchRepository.fuse | okPairs/pageTexts | Tavily API `results` (title/url/content) | ✓ FLOWING | Real API data; blank items → OMITIDA, all-blank → AllFailed |
| ChatViewModel search branch | requestUserText/groundedSources | Fused block via augment | ✓ FLOWING | Identical downstream as URL grounding; Fuentes/preview/citations untouched |
| SettingsViewModel | tavilyKeyPresent/Status | Keystore alias read + probe outcome | ✓ FLOWING | Presence check + 5-state mapping; saved key never echoed |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| TavilySearchRepositoryTest | `:app:testDebugUnitTest --tests TavilySearchRepositoryTest` | 16 tests, 0 failures (XML) | ✓ PASS |
| SettingsTavilyTest | `:app:testDebugUnitTest --tests SettingsTavilyTest` | 17 tests, 0 failures (XML) | ✓ PASS |
| Full unit suite | `:app:testDebugUnitTest` | BUILD SUCCESSFUL; 381 tests / 40 suites, 0 failures/errors/skipped | ✓ PASS |

Note: one `--rerun-tasks` full run hit a transient `compileDebugUnitTestKotlin` failure (KSP/Hilt rerun artifact); immediate retry + incremental full run both BUILD SUCCESSFUL. Not a code defect.

### Probe Execution

| Probe | Command | Result | Status |
|-------|---------|--------|--------|
| — | — | No probe scripts declared for this phase; unit-test gates used instead | SKIP |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| TAV-01 | 55-01 (storage) + 55-02 (UI) | Tavily key in Settings, Keystore-encrypted, test-connection, clear missing/invalid errors | ✓ SATISFIED | Alias CRUD + Web Search card + probe + 4 English states; 17/17 tests |
| TAV-02 | 55-01 (producer) + 55-02 (hook) | Top-N results fused with numbered citations, same Fuentes/preview/rows pipeline | ✓ SATISFIED | Producer + identical augment/persist; 16/16 tests; tracer test `Source [1..3]` |
| TAV-03 | 55-02 | Grounding enablement + offline gate; no search when off/offline | ✓ SATISFIED | doGround + online gates; OFFLINE/MissingKey/InvalidKey/Limit notices; gate-matrix tests |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | None. Key-hygiene grep: no hardcoded keys/Bearer literals; only status-code `Timber.e` calls (no key/query logging). WR-01..WR-03 review fixes confirmed applied in code. IN-01..IN-04 informational, no action required. | — | — |

Review WR-01..WR-03 (55-REVIEW.md, status: fixed) re-verified in current code: blank-check precedes keystore read (TavilySearchRepository.kt:75-81); save trims input (SettingsViewModel.kt:211); skippedUrls URL-only (TavilySearchRepository.kt:145).

### Human Verification Required

### 1. Live Tavily E2E with real key

**Test:** On device/emulator: paste a real Tavily key in Settings > Web Search → tap Test connection → send a grounded no-URL question → toggle airplane mode and send again → clear key and send again
**Expected:** Test shows "Connection successful"; grounded turn returns cited answer with Fuentes rows + preview; offline turn shows offline path with no search attempt; cleared-key turn shows missing-key message naming tavily.com + Settings path
**Why human:** Needs a physical device/emulator plus a user-provided paid Tavily key; cannot verify programmatically. Recorded as accepted follow-up per project precedent (v2.2/v2.3 device smokes), not a gap — all automatable evidence passes.

### Gaps Summary

No gaps. All three success criteria verified in code with 33/33 targeted tests and 381/381 full-suite tests green, review warnings WR-01..WR-03 confirmed fixed, and key-hygiene gates clean. The sole remaining item is live-key E2E on device, which is an accepted follow-up (same standing as v2.2/v2.3 device smokes), not a blocking gap.

---

_Verified: 2026-09-29T00:00:00Z_
_Verifier: the agent (gsd-verifier)_
