# Phase 55: Tavily Search Foundation - Research

**Researched:** 2026-09-28
**Domain:** Tavily Search API integration + search→grounding fusion on Android (Kotlin/Hilt/Retrofit)
**Confidence:** HIGH

## Summary

Phase 55 adds Tavily web search as a grounding source: a user-provided Tavily API key stored Keystore-encrypted in Settings (with test-connection), a search client that queries `POST https://api.tavily.com/search` with `Authorization: Bearer <key>`, and a search→fused-block path that reuses the existing grounding pipeline (`GroundingPrompt.buildFusedBlock`, `GroundingBudget`, per-source ok/skipped semantics, `WebContextSanitizer`, `GroundedSource` rows). The ChatViewModel hook gains a search branch that runs only when grounding resolves enabled (global default-ON + per-chat tri-state via `GroundingPrecedence`) AND connectivity is validated AND a key is present — otherwise missing-key / invalid-key(401) / offline paths.

The Tavily Search API shape is confirmed from official docs and the official Python SDK source: auth is `Authorization: Bearer <api_key>` header, `query` is the only required body field, `search_depth` defaults to `basic` (1 credit; `advanced` = 2 credits), `max_results` 0–20 default 5, optional `include_answer` (do NOT enable — we fuse raw results, not the LLM answer). Each result object carries `title`, `url`, `content`, `score`. Error codes: 401 = invalid key, 429 = usage limit, 400 = bad request, 403/432/433 = forbidden. Test-connection should use the cheapest truthful call: `POST /search` with a minimal query and `max_results: 1` (a garbage query still costs 1 credit — there is no free ping endpoint; document this).

**Primary recommendation:** Build a Retrofit `TavilyApi` on a dedicated OkHttp client WITHOUT `AuthInterceptor` (per-call `Authorization: Bearer` header from a `TavilyKeyStore` alias, never logged), map `results[]` → sanitized `(url, text)` pairs → `buildFusedBlock` with `GroundingBudget.perPageBudget`, and branch the ChatViewModel hook on `doGround && urls.isEmpty() && keyPresent && online`.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Tavily Search API (https://api.tavily.com/search), user-provided key — same quality as the reference OpenCode flow
- Key stored via existing ApiKeyStore/KeystoreManager pattern (never plaintext, never logged); add a dedicated key id for Tavily (not per-endpoint)
- Settings row: key field + Test connection button + clear missing/invalid-key errors in English
- Top-N results (default 5, max 10) map to the fused-block pipeline: each result = title + URL + content snippet as one numbered source
- Planner decides payload shape (search_depth, chunks) within latency budget; must reuse buildFusedBlock/GroundingBudget/per-source ok-skipped semantics
- Citations, Fuentes list, preview rows, persisted rows: identical to URL grounding (same rows the sheet reads)
- Search runs only when grounding resolves enabled (global default-ON + per-chat tri-state, same precedence as fetch) AND connectivity validated AND key present
- Missing key → actionable message (where to get it + where to paste it); invalid key (401) → distinct invalid-key message; offline → existing offline path, no search attempt

### the agent's Discretion
- Tavily request/response DTO shapes, Retrofit vs OkHttp-direct client, timeouts/retries, result-count default
- Settings placement detail within existing Settings surfaces
- Test-connection implementation (cheap `search` with garbage query vs `/extract` ping — cheapest truthful option)

### Deferred Ideas (OUT OF SCOPE)
- Agentic invocation (Phase 56), remote tools[] (Phase 57), OG thumbnails (Phase 58)
- Non-Tavily providers (Brave/DuckDuckGo rejected by user)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| TAV-01 | User stores a Tavily API key in Settings (Keystore-encrypted, test-connection button, clear error when missing/invalid) | `ApiKeyStore` + `KeystoreManager` pattern (dedicated string alias `tavily_api_key` since store is Long-endpoint-keyed); Settings Security-section row; test-connection = minimal `POST /search` (max_results 1); 401 → invalid-key copy |
| TAV-02 | Search results ground the answer (query → top-N fused into context with numbered citations, same Fuentes/preview/rows pipeline) | `TavilyApi` Retrofit interface + DTOs; snippet mapping (title+URL+content → sanitized text); `buildFusedBlock` + `perPageBudget`; `GroundedSource` OK/OMITIDA details union honored |
| TAV-03 | Search honors grounding enablement (global + per-chat toggle + offline gate; no search when off or offline) | `GroundingPrecedence.shouldGround` single decision; `WebPageFetcher.hasValidatedInternet()` gate reuse; offline → existing `ModelOnlyNotice.OFFLINE` path, no socket opened |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Tavily key storage | API / Backend (data layer) | — | `KeystoreManager` + EncryptedSharedPreferences; same tier as endpoint keys |
| Search HTTP call | API / Backend (data layer) | — | Retrofit on dedicated OkHttp client; `Dispatchers.IO`; never on UI thread |
| Snippet sanitize + budget truncate | API / Backend (data layer) | — | Pure-Kotlin `WebContextSanitizer` + `GroundingBudget`; JVM-testable |
| Fused-block build | API / Backend (data layer) | — | `GroundingPrompt.buildFusedBlock`; identical rows to URL grounding |
| Enablement decision | API / Backend (ViewModel hook) | — | `GroundingPrecedence.shouldGround` read once per send in ChatViewModel |
| Key field + Test button | Browser / Client (Compose UI) | — | SettingsScreen row; StateFlow UI state; English copy only |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Retrofit + OkHttp (existing) | (project-pinned) | `TavilyApi` interface, POST /search | Established DTO/Retrofit conventions in `data/remote/`; zero new deps per project ethos [VERIFIED: codebase grep] |
| kotlinx.serialization (existing) | (project-pinned) | Tavily request/response DTOs (`@Serializable`) | Matches all `data/remote/dto/` DTOs [VERIFIED: codebase grep] |
| Hilt Singleton (existing) | (project-pinned) | `TavilyApi` + search repository providers | Same as `NetworkModule`/`SecurityModule` providers [VERIFIED: codebase read] |
| EncryptedSharedPreferences via `KeystoreManager` | (existing) | Tavily key at rest (AES-256-GCM) | Same as endpoint keys; never plaintext, never logged [VERIFIED: codebase read] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `WebContextSanitizer` | (existing) | Sanitize each Tavily snippet (untrusted input) | Every snippet before fusion — same trust boundary as fetched pages |
| `GroundingBudget` | (existing) | `perPageBudget(contextSize, n)` truncation of snippets | Snippets are denser than pages; still cap at per-page slice |
| `GroundingPrecedence` | (existing) | Single enablement decision per send | Gate search branch identically to fetch branch |
| Timber | (existing) | Debug logging (never log key or full snippets at BODY) | Errors only; key bytes zeroed after use like `AuthInterceptor` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Retrofit `TavilyApi` | OkHttp-direct POST with manual JSON | Retrofit matches `data/remote/api/*Api.kt` conventions; OkHttp-direct only if full-base-URL Retrofit proves awkward (Tavily host differs from endpoint hosts — Retrofit handles this via full `@POST` URL or dedicated Retrofit instance with `baseUrl("https://api.tavily.com/")`) |
| `include_answer=true` | Fuse `results[].content` only | `include_answer` costs latency + returns LLM prose we'd have to cite separately; locked decision says each result = one numbered source → keep `include_answer=false` |
| `search_depth=advanced` default | `basic` default | `advanced` = 2 credits + higher latency; `basic` = 1 credit, chunk content, default per official docs. Recommend `basic` + `max_results=5` default (cap 10 per CONTEXT), `chunks_per_source=3` default. Planner may tune within latency budget |

**Installation:**
```bash
# No new dependencies — Tavily is a plain HTTPS API on the existing OkHttp/Retrofit stack.
```

**Version verification:** No new packages; existing OkHttp/Retrofit/serialization versions are project-pinned (see `libs.versions.toml`).

## Package Legitimacy Audit

No external packages installed in this phase. Tavily is consumed as a raw HTTPS API (same as the Hugging Face Hub custom Retrofit client precedent). Zero-dependency ethos preserved — no audit rows required.

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
User message (ChatViewModel.send)
  │
  ├─ GroundingPrecedence.shouldGround(perChat, global)? ──NO──▶ model-only path (unchanged)
  │YES
  ├─ UrlDetector.allUrls(text) non-empty? ──YES──▶ existing fetchAll path (unchanged)
  │NO (zero URLs → search branch)
  ├─ hasValidatedInternet()? ──NO──▶ ModelOnlyNotice.OFFLINE (no socket, unchanged)
  │YES
  ├─ Tavily key present? ──NO──▶ actionable missing-key message (tavily.com + Settings path)
  │YES
  ├─ TavilySearchRepository.search(query=user text, maxResults=5, depth=basic) [IO]
  │     POST https://api.tavily.com/search {query, search_depth, max_results, include_answer:false}
  │     Authorization: Bearer <key> (per-call header, dedicated client WITHOUT AuthInterceptor)
  │     401 → invalid-key message │ 429 → limit message │ other → FETCH_FAILED model-only
  │
  ├─ For each result: sanitize(content) → truncate to perPageBudget → (url, text)
  │     Blank/failed items → skipped list (never silent drops; OMITIDA rows)
  ├─ GroundingPrompt.buildFusedBlock(okPairs) → augment(requestUserText)
  ├─ GroundedSource details union (OK = Tavily URL + snippet; OMITIDA = skipped)
  └─ Same persist path as URL grounding (Fuentes list, preview rows, citations [1]/[2])
```

### Recommended Project Structure
```
app/src/main/java/com/warped/
├── data/remote/
│   ├── api/TavilyApi.kt          # Retrofit: POST search, full-path on api.tavily.com base
│   └── dto/TavilyDtos.kt         # @Serializable request/response/result DTOs
├── data/grounding/
│   └── TavilySearchRepository.kt # search() + snippet→(url,text) mapping + budget + sanitize
├── data/local/security/
│   └── ApiKeyStore.kt            # EXTEND: Tavily alias (string key, not endpoint Long)
├── di/
│   ├── NetworkModule.kt          # EXTEND: @Named("tavily") OkHttp + Retrofit providers
│   └── SecurityModule.kt         # check: ApiKeyStore binding reuse
├── ui/settings/
│   ├── SettingsScreen.kt         # Tavily key row (Security section) + Test button
│   └── SettingsViewModel.kt      # key state, save/clear/test, English error copy
└── ui/chat/ChatViewModel.kt      # search branch at fetch-bypass point (urls.isEmpty())
```

### Pattern 1: Dedicated Tavily Retrofit client (no AuthInterceptor)
**What:** A `@Named("tavily")` OkHttpClient built from scratch (timeouts ~15s connect / 30s call, `retryOnConnectionFailure(false)` for POST non-idempotency) plus a Retrofit instance with `baseUrl("https://api.tavily.com/")`. Auth is a per-call `@Header("Authorization")` value `"Bearer $key"` assembled at call time from the keystore, with the CharArray/key String zeroed after use — mirroring `AuthInterceptor` lines 18-28 [VERIFIED: codebase read].
**When to use:** Always for Tavily — the shared endpoint client carries `AuthInterceptor` (endpoint-tag-gated) and SSE/download tuning that must never touch Tavily traffic; `WebPageFetcher` already establishes the precedent of stripping `AuthInterceptor` for non-endpoint hosts [VERIFIED: codebase read].
**Example:**
```kotlin
// Source: established codebase patterns (NetworkModule providers + AuthInterceptor key handling)
@Provides @Singleton @Named("tavily")
fun provideTavilyOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .callTimeout(60, TimeUnit.SECONDS)
    .retryOnConnectionFailure(false) // POST is non-idempotent; caller retries explicitly
    .build()

interface TavilyApi {
    @POST("search")
    @Headers("Content-Type: application/json")
    suspend fun search(@Header("Authorization") auth: String, @Body request: TavilySearchRequest): Response<TavilySearchResponse>
}
```

### Pattern 2: Snippet → fused-block mapping with ok/skipped semantics
**What:** Map `results[]` (cap: default 5, max 10) to `List<Pair<url, text>>` where `text = "${title}\n${sanitize(content).take(perPageBudget)}"`. Blank content or blank URL → skipped entry → `GroundedSource(url-or-fallback, null, OMITIDA)`; all-skipped → `MultiUrlResult.AllFailed(FETCH_FAILED)` (or OFFLINE if the gate tripped). Non-empty → `buildFusedBlock(okPairs)` + details union — structurally identical to `MultiUrlFetcher.fetchAll` lines 92-139 [VERIFIED: codebase read].
**When to use:** Every search fusion; the sheet/persist path then works unchanged.
**Example:**
```kotlin
// Source: MultiUrlFetcher CR-01 union pattern (codebase) + Tavily result shape (official docs)
val okPairs = sanitized.filter { it.text.isNotBlank() }.map { it.url to it.text }
val details = sanitized.map { s ->
    if (s.text.isNotBlank()) GroundedSource(s.url, s.text, GroundedSourceStatus.OK)
    else GroundedSource(s.url, null, GroundedSourceStatus.OMITIDA)
}
val block = GroundingPrompt.buildFusedBlock(okPairs)
```

### Pattern 3: Tavily key as string alias alongside Long-keyed endpoint keys
**What:** `ApiKeyStore` today is `storeKey/getKey/deleteKey(endpointId: Long)` with alias `"api_key_$endpointId"` [VERIFIED: codebase read]. Tavily is NOT per-endpoint → add a parallel string-alias path (e.g. alias `"tavily_api_key"`) in `ApiKeyStore` backed by the same `KeystoreManager`, plus extend `deleteAllKeys` coverage (Settings "Delete all keys" must also wipe Tavily).
**When to use:** TAV-01 key persistence; keep the CharArray-zeroing discipline from `storeKey`.

### Anti-Patterns to Avoid
- **Endpoint-id reuse for Tavily:** Don't shoehorn the Tavily key under a fake endpoint Long id — endpoint deletion would orphan/wedge it (cf. deferred `huggingface_token` orphan precedent in STATE.md). Dedicated alias.
- **Logging the key or full request body:** `HttpLoggingInterceptor` BODY level in DEBUG would print `Authorization: Bearer` — the Tavily client must NOT attach the body-logging interceptor, or must redact headers. Never `Timber.d(key)`.
- **`include_raw_content=true`:** Returns full page HTML parses — blows the per-page budget and latency; snippets (`content`) are already query-relevant extracts. Keep false (planner may revisit only with budget math).
- **Swallowing 401 into generic failure:** 401 must surface the distinct invalid-key message per CONTEXT; 429 (usage limit) deserves its own copy too.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Tavily HTTP transport | Custom HttpURLConnection client | Retrofit `TavilyApi` on named OkHttp client | Interceptors, timeouts, `Response<T>` error codes, testability — all established |
| Snippet relevance extraction | Keyword scoring / own summarizer | Tavily `results[].content` (+ `score` for ordering/filtering) | Proprietary AI extraction; `score` enables relevance cutoff |
| Key encryption | Own AES wrapper / plaintext prefs | `KeystoreManager` + EncryptedSharedPreferences | Hardware-backed, audited, already integrated |
| Connectivity check | Ping / DNS probe | `hasValidatedInternet()` (NET_CAPABILITY_VALIDATED) | No socket, no extra permission, frozen behavior |
| Citation block format | New search-specific block syntax | `buildFusedBlock` | Sheet, persist, and citation parsing all key on `Source [N]` format |

**Key insight:** TAV-02's "identical pipeline" constraint means the search branch is a *producer* of `(url, text)` pairs, not a parallel grounding system — every downstream consumer (fusion, persist, Fuentes, preview, citations) stays untouched.

## Common Pitfalls

### Pitfall 1: AuthInterceptor leaking endpoint keys to api.tavily.com (or vice versa)
**What goes wrong:** Reusing the shared OkHttp client sends endpoint `Authorization` headers to Tavily, or Tavily Bearer leaks to chat endpoints.
**Why it happens:** `AuthInterceptor` is tag-gated but attached globally on the shared client.
**How to avoid:** Dedicated named client with zero interceptors except a redacted debug logger; per-call header, never a static interceptor holding the key.
**Warning signs:** 401s on endpoints after Tavily work; key material in logcat.

### Pitfall 2: api_key-in-body vs Bearer header confusion
**What goes wrong:** Older Tavily examples pass `api_key` in the JSON body; new SDKs use `Authorization: Bearer`.
**Why it happens:** Both have existed across doc generations.
**How to avoid:** Use `Authorization: Bearer <key>` header (confirmed in current official Python SDK source: `headers={"Authorization": f"Bearer {api_key}"}`) [CITED: github.com/tavily-ai/tavily-python `async_tavily.py`]. Body `query` carries no key.
**Warning signs:** 401 with a known-good key → check header scheme first.

### Pitfall 3: Test-connection burns credits / uses wrong probe
**What goes wrong:** A "test" that runs a full `max_results=5 advanced` search wastes 2 credits; probing `/extract` tests the wrong endpoint/credential path.
**Why it happens:** No free ping endpoint exists on the Search API.
**How to avoid:** Cheapest truthful probe = `POST /search` with `query="test"`, `max_results=1`, `search_depth="basic"` (1 credit, minimal latency). Copy should note the test uses one search credit. `include_answer=false`, `include_domains` unset.
**Warning signs:** User complaints about credit drain → check test payload.

### Pitfall 4: Snippet budget blowout on small local windows
**What goes wrong:** 5 results × 3 chunks × 500 chars ≈ 7500 chars > 6000 global budget for 4K windows.
**Why it happens:** `chunks_per_source` maxes at 3×500 chars per result.
**How to avoid:** Truncate each sanitized snippet to `perPageBudget(contextSize, n)` (1200 chars at 5 results/4K window — above `MIN_PER_PAGE` floor 1500? No: 6000/5=1200 < 1500 → floor wins → 5×1500=7500 > global. Planner must decide: cap default results at 4 for small windows, or accept floor overrun as today’s fetch path does — `fetchAll` has the identical floor-overrun property, so matching existing behavior is defensible). Flag explicitly in plan.
**Warning signs:** Degraded local-model answers after grounding; truncation markers everywhere.

### Pitfall 5: Query = full user message breaks relevance
**What goes wrong:** Passing a long multi-sentence chat turn verbatim as `query` dilutes Tavily relevance.
**Why it happens:** Direct search→ground path uses the turn text as the query.
**How to avoid:** Phase 55 scope is direct pass-through (no query-rewriting — that belongs to Phase 56's agentic loop). Cap query length client-side (e.g. take first ~500 chars) to avoid 400s; document the limitation for Phase 56 to fix with model-written queries.
**Warning signs:** Low `score` values across results; irrelevant Fuentes.

## Code Examples

Verified patterns from official sources:

### Tavily Search request (minimal, cheapest)
```json
// Source: https://docs.tavily.com/documentation/api-reference/endpoint/search
{
  "query": "Who is Leo Messi?",
  "search_depth": "basic",
  "max_results": 5
}
// Response: { "query": "...", "answer"?: "...", "results": [
//   { "title": "...", "url": "...", "content": "...", "score": 0.97 }
// ], "response_time": 1.09, "request_id": "..." }
```

### Tavily DTOs (Kotlin, matching data/remote/dto conventions)
```kotlin
// Source: DTO shape from official API reference; @Serializable style from codebase LmStudioDtos.kt
@Serializable
data class TavilySearchRequest(
    val query: String,
    val search_depth: String = "basic",
    val max_results: Int = 5,
    val include_answer: Boolean = false,
    val chunks_per_source: Int = 3,
)

@Serializable
data class TavilySearchResult(
    val title: String = "",
    val url: String = "",
    val content: String = "",
    val score: Double = 0.0,
)

@Serializable
data class TavilySearchResponse(
    val query: String = "",
    val results: List<TavilySearchResult> = emptyList(),
    val answer: String? = null,
    val response_time: Float? = null,
)
```

### Error mapping (from official SDK source)
```kotlin
// Source: github.com/tavily-ai/tavily-python async_tavily.py error branches
when (response.code) {
    200 -> parse(response)
    401 -> TavilyError.InvalidKey   // → distinct invalid-key message (TAV-01)
    429 -> TavilyError.UsageLimit  // → limit-exceeded message
    400 -> TavilyError.BadRequest  // → model-only FETCH_FAILED (+ log)
    403, 432, 433 -> TavilyError.Forbidden
    else -> TavilyError.FetchFailed
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `api_key` in JSON body | `Authorization: Bearer` header | SDK generations since 2024; current SDK uses Bearer exclusively | Use header; body-key may still work but is legacy |
| `search_depth: basic/advanced` only | + `fast` / `ultra-fast` tiers | 2025 API expansion | `basic` remains default; `fast` is a valid latency-budget option for planner |
| Single `content` string per result | Chunked `content` (`<chunk 1> [...]`, up to 3×500 chars) | With `chunks_per_source` param | Budget math must assume ~1500 chars/result worst case |

**Deprecated/outdated:**
- `days` parameter: superseded by `time_range`/`start_date`/`end_date` in current API — do not use.
- Keyless SDK mode: rate-limited playground only, irrelevant for user-key flow.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | No free/cheap ping endpoint exists; test-connection costs 1 search credit [ASSUMED — API reference shows only POST /search; no health endpoint found] | Pitfalls/Test-connection | User surprised by credit use → mitigate with copy noting 1-credit cost |
| A2 | `Authorization: Bearer` accepted on POST /search (SDK-confirmed) while legacy body `api_key` also possibly accepted [CITED SDK + ASSUMED fallback] | Architecture | If Bearer rejected, fall back to body `api_key` field — one-line DTO change |
| A3 | Default 5 / max 10 results fits latency budget on mobile (untimed; response_time ~1s server-side per docs example) [ASSUMED] | Standard Stack | Slow turns on poor networks → planner adds timeout + result-count trim |
| A4 | Tavily `content` snippets are English-prose, sanitizer-safe (no new hijack patterns needed) [ASSUMED] | Architecture | Snippets are untrusted input — sanitizer runs regardless, so risk is contained |

## Open Questions

1. **Exact Settings placement for the Tavily row**
   - What we know: SettingsScreen has sections incl. Security ("API keys are encrypted…", Delete all keys) — natural home is the Security card or a new "Web Search" card above it.
   - What's unclear: Whether a dedicated card vs Security-section row fits the settings UX best.
   - Recommendation: Planner picks (new "Web Search" card recommended — key field + Test + status + delete keeps Security card untouched); trivial to move.

2. **Missing-key message trigger point**
   - What we know: Missing key → actionable message (tavily.com + Settings path). Could be a chat banner/notice or an inline assistant message.
   - What's unclear: Which surface (ModelOnlyNotice variant vs chat error bubble) the planner prefers.
   - Recommendation: Reuse the model-only notice plumbing with new copy (no new UI components); Phase 56 will refine.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 17 + Gradle/AGP | Build | ✓ | OpenJDK 17.0.20.1 | — |
| api.tavily.com reachability | Search + test-connection | ? (device/emulator network) | — | Offline gate already handles; test button shows network error copy |
| Tavily API key | Dev E2E verification | ✗ (user-provided at runtime) | — | JVM unit tests with fake `TavilyApi`; manual E2E needs a real key |

**Missing dependencies with no fallback:**
- None blocking — all logic is JVM-testable with fakes; only live E2E needs a key.

**Missing dependencies with fallback:**
- Live Tavily access in CI → fake `TavilyApi` responses in unit tests (same pattern as `MultiUrlFetcherTest` fake fetcher).

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | Yes (API key保管) | EncryptedSharedPreferences via KeystoreManager; dedicated `tavily_api_key` alias; zero CharArrays after use |
| V3 Session Management | No | N/A — stateless per-call header, no session |
| V4 Access Control | No | N/A — single-user local app |
| V5 Input Validation | Yes | Snippets sanitized via `WebContextSanitizer` (hijack patterns + link neutralization + delimiter escaping); query length-capped; DTO `ignoreUnknownKeys=true` |
| V6 Cryptography | Yes | Never hand-roll — Android Keystore AES-256-GCM via existing manager |

### Known Threat Patterns for Retrofit/OkHttp + third-party search

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| API key leakage via logs/interceptors | Information disclosure | Dedicated client without BODY logger; never log key; redact `Authorization` header |
| Key leakage to wrong host | Information disclosure | Per-call header on Tavily-only client; `AuthInterceptor` never attached; endpoint keys never sent to Tavily |
| Prompt injection via search snippets | Tampering / Elevation | `WebContextSanitizer.sanitize` on every snippet (same as fetched pages); Phase 56 AGENT-02 extends with allowlist |
| Malicious markdown links in snippets | Tampering | Sanitizer link neutralization (http(s) only, strips javascript:/data:) |
| Search result URL spoofing (Fuentes shows attacker URL) | Spoofing | Display URLs verbatim (no re-fetch in Phase 55); Phase 58 OG + Phase 56 fetch steps add validation |

## Sources

### Primary (HIGH confidence)
- Tavily official API reference (`docs.tavily.com/documentation/api-reference/endpoint/search`) — params, defaults, response schema (via WebSearch extract)
- Tavily official Python SDK source (`github.com/tavily-ai/tavily-python`, `async_tavily.py`) — `Authorization: Bearer` header, `base_url=https://api.tavily.com`, error-code mapping (401/429/400/403)
- Codebase: `ApiKeyStore.kt`, `KeystoreManager.kt`, `MultiUrlFetcher.kt`, `GroundingPrompt.kt`, `GroundingBudget.kt`, `GroundingResult.kt`, `WebContextSanitizer.kt`, `WebPageFetcher.kt`, `NetworkModule.kt`, `AuthInterceptor.kt`, `ChatViewModel.kt` (send hook), `SettingsScreen.kt`/`SettingsViewModel.kt`, `GroundedSource.kt`, `GroundingPrecedence.kt`

### Secondary (MEDIUM confidence)
- Tavily search best-practices + quick-tutorials (depth/latency tradeoff table, `max_results` guidance) — consistent with API reference

### Tertiary (LOW confidence)
- None — all load-bearing claims verified above; assumptions A1–A4 explicitly logged

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — zero new deps; every component exists in codebase with established patterns
- Architecture: HIGH — API shape from official docs + SDK source; fusion mapping mirrors frozen `fetchAll` contract line-for-line
- Pitfalls: HIGH — budget-floor overrun and interceptor-leak risks derived from read code, not speculation

**Research date:** 2026-09-28
**Valid until:** 2026-10-28 (stable: Tavily v1 search API + frozen v2.3 grounding pipeline)
