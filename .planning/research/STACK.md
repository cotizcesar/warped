# Stack Research

**Domain:** Warped v2.2 Simplificación + Web Grounding (Android, Kotlin + Compose + LiteRT-LM)
**Researched:** 2026-09-28
**Confidence:** HIGH

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Kotlin | 2.3.20 (pinned in `gradle/libs.versions.toml`) | Language for all new v2.2 code | Already the project standard; no change. The web-grounding fetcher and connectivity check are plain Kotlin + coroutines — no language feature beyond what the codebase uses. |
| OkHttp | 4.12.0 (existing `libs.okhttp`) | Direct page fetch for heuristic web grounding + unchanged model downloads / REST | Already the HTTP client for everything (default client + `@Named("sse")` client in `NetworkModule.kt`). A plain `newCall(Request)` GET with tight timeouts covers page fetch with zero new artifacts. Battle-tested connection pooling, interceptors, and cancel-via-`Call.cancel()` already proven in v2.1. |
| kotlinx-coroutines | 1.11.0 (existing) | `withContext(Dispatchers.IO)` fetch, `withTimeout` guard, `Flow` token streaming unchanged | Structured concurrency already governs all network I/O. Fetch runs on `Dispatchers.IO`; timeout/cancel composes with the existing single-flight `AtomicReference<Call>` pattern from v2.1 Phase 46. |
| Android `ConnectivityManager` + `NetworkCapabilities` | Platform API (minSdk 28) | Offline connectivity check → model-only fallback | Platform API, no dependency. `ACCESS_NETWORK_STATE` is already declared in `AndroidManifest.xml` (line 5). `NetworkCapabilities.NET_CAPABILITY_VALIDATED` gives a real "usable internet" signal on API 23+; minSdk 28 makes this unconditional — no version branching. |
| LiteRT-LM | 0.17.1 (pinned, HOLD — do not bump) | Local inference; system-prompt injection point for grounding instruction | Web grounding is prompt-engineering, not engine work: the grounding instruction is prepended to the system prompt / `ConversationConfig`. No engine API change, no version bump. The `ToolSet`/`@Tool` API inside litertlm stays used-by-nothing after Skills deletion — no catalog change. |
| Highlights | 1.1.0 (existing `libs.highlights`) | Syntax tokenization engine (unchanged) | The SyntaxTheme fix is a wiring bug, not an engine gap. Tokenizer stays; fix is in theme selection/plumbing (`SyntaxTheme.fromKey`, `AdvancedPreferences`, `CodeBlock` call sites). |
| DataStore Preferences | 1.2.1 (existing) | Remove `SkillPreferences` keys + HF-token-adjacent prefs; keep `syntaxTheme` key | Deletion-only change. The `syntaxTheme` DataStore key and its `CodeTheme → SyntaxTheme` migration mapping stay untouched (the theme fix must not break the migration). |
| Hilt | 2.60.1 (existing, KSP-only) | Delete `SkillsModule`; trim `HuggingFaceModule` if it only served search/token | Deletion-only DI change. No new modules needed: the grounding fetcher is a plain `@Singleton` `@Provides` (or `@Inject constructor`) using the existing default `OkHttpClient`. |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| OkHttp `logging-interceptor` | 4.12.0 (existing) | Keep — page-fetch requests must NOT log bodies | The existing `NetworkModule` interceptor already downgrades `/resolve/main/` to headers-only. Page-fetch URLs must get the same headers-only treatment (fetched HTML can be MBs — never BODY-log it). Reuse the pattern, extend the path allowlist. |
| Retrofit 3.0.0 + `converter-kotlinx-serialization` 3.0.0 | Existing | Unchanged — still serves LM Studio chat + HF allowlist-adjacent REST | Do NOT route page fetch through Retrofit: HTML is not a `@Serializable` body, and `@Streaming ResponseBody` for HTML adds converter overhead for nothing. Raw OkHttp is the correct call. |
| WorkManager 2.12.0 | Existing | Unchanged — still serves model downloads | Page fetch is interactive (part of a chat turn, seconds-long), NOT deferrable work. Never put grounding fetch in a Worker. |
| Room 2.8.5 | Existing | Unchanged schema expected | No new tables for grounding (fetched snippets are ephemeral per-turn context, not persisted history — persisting raw web HTML in Room would bloat the DB). If per-turn "sources used" need persistence, a plain `String` column on messages suffices — no new entity. |
| `security-crypto` 1.1.0 | Existing | Unchanged — remote endpoint keys stay encrypted | HF token methods (`store/get/deleteHuggingFaceToken` in `ApiKeyStore.kt`) are deleted; the Keystore wrapper itself stays for OpenAI/LM Studio keys. |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| `scripts/audit-dependencies.sh` (RUNTIME-12) | Must stay green: no banned direct deps, no pre-release in release graph | v2.2 adds ZERO entries to `libs.versions.toml`. The script checks direct declarations + release graph — deletions can only help. Run it in CI after the removals; removal of `SkillsModule`/HF code must not leave dangling version refs. |
| R8 full mode (`android.enableR8.fullMode=true`, AGP 9.3.0) | Shrink after deletions; remove ToolSet/skills keep rules if they exist only for Skills | `proguard-rules.pro` has ToolSet/`@Tool`/skills keeps (per Phase 48 summary). After deleting `CalculatorToolSet`/`CurrentTimeToolSet`/`JsonFormatterToolSet`, delete or narrow those keeps — dead keeps cost nothing at runtime but mask future auditing. Verify `assembleRelease` still green. |

## Installation

```bash
# NOTHING TO INSTALL — zero-new-dependency milestone.
# No catalog entries, no Gradle sync of new artifacts, no permission additions.
# (ACCESS_NETWORK_STATE and INTERNET are already in AndroidManifest.xml.)

# After removals, verify:
bash scripts/audit-dependencies.sh
./gradlew :app:assembleDebug :app:assembleRelease
```

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Raw `OkHttpClient.newCall()` GET for page fetch (existing default client or a small dedicated instance) | Retrofit `@GET` + `@Streaming ResponseBody` | Never for v2.2 — Retrofit adds converter/interceptor overhead for an HTML body we parse manually. Raw OkHttp gives direct `Call` ownership (timeout + cancel + byte-cap in one place). |
| Regex/manual HTML→text stripping (title, `<p>`, meta description, tag strip, whitespace collapse, char cap) | Jsoup (`org.jsoup:jsoup`) | Never for v2.2 — new dependency violating the explicit zero-new-deps constraint; would require audit-script justification and R8 keep-rule verification for zero lasting benefit. Heuristic grounding needs ~200–500 words of clean text, not a DOM. Revisit only if grounding graduates to structured extraction (readability-grade boilerplate removal). |
| `ConnectivityManager` + `NET_CAPABILITY_VALIDATED` pre-check | Try-fetch-and-catch-only (no pre-check) | Pre-check is cheap and already permissioned; it lets the UI skip the fetch entirely and go straight to model-only mode (no pointless timeout wait on airplane mode). Keep the try/catch anyway — capabilities can go stale mid-fetch. Defense in depth, both are free. |
| `ConnectivityManager` pre-check | `NetworkCallback` live listener / reactive connectivity Flow | Overkill for per-turn grounding. A synchronous pre-check at fetch time matches the use (one decision per chat turn). No lifecycle-registered callback to leak. |
| System-prompt instruction + fetched-text injection (prompt engineering) | Search API SDK (Google CSE, Bing, Brave) or on-device embedding retrieval | Explicitly out of scope: search APIs need API keys (new secrets surface right after we're deleting the HF token) and new deps; embeddings need a vector store + model. The milestone mandates heuristic fetch with no keys. |
| Delete `SkillsModule.kt`, `*Skill*.kt`, `SkillChipsRow.kt`, `ToolGating.kt`, `LmStudioToolLoop.kt` tools-loop, `HuggingFaceAuthInterceptor.kt`, HF token UI/prefs, `HuggingFaceScreen/ViewModel` search path | Feature-flag the removals instead | Never — flags preserve the dead surface (R8 keeps, DI graph, DataStore keys) that v2.2 exists to delete. Hard delete; git history is the flag. Exception: keep `assets/model_allowlist.json` + `ModelAllowlistRepository` + direct download path (explicitly in scope to keep). |
| Fix theme plumbing in current `SyntaxTheme`/`CodeBlock`/`AdvancedPreferences` code | Migrate to a new highlighting library or rewrite themes | Never — 4 presets with complete 13-color light/dark tables already exist and Monokai proves the pipeline renders. The bug is selection/plumbing (suspects: hardcoded `SyntaxTheme.MONOKAI` call sites e.g. `HuggingFaceScreen.kt:309`, legacy `fromKey` aliases `NORD`/`SOLARIZED_DARK`, or `CodeBlock` ignoring the passed theme), not data or engine. Zero stack change. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `com.squareup.okhttp3:okhttp-sse` artifact | Banned-by-precedent in v2.1 (solved problem; `RealEventSource` cancel quirks okhttp#5544; audit cost). Irrelevant to page fetch anyway (non-streaming GET). | Existing manual `Call` + `BufferedSource` patterns |
| Jsoup or any HTML parser dependency | New direct dep; trips the zero-new-deps constraint and needs R8/audit justification for a heuristic that needs plain text only | Manual tag-strip + whitespace-collapse + char cap (~30 lines, testable as pure Kotlin unit test) |
| Search API client / API key for grounding | Reintroduces the secrets surface v2.2 is deleting (HF token); network-security and Keystore review cost | Keyless direct `https://` GET of user/model-supplied URLs only |
| `android.net.http` Cronet / Cronet Engine | New native dependency, Play-Services-adjacent, async API mismatch with the coroutine stack | OkHttp (already tuned, already kept in R8) |
| `DownloadManager` (platform) for page fetch | No custom headers/timeouts/byte-caps, no coroutine integration, overkill for KB-scale pages | OkHttp with `callTimeout` + `responseBody.close()` in `finally` |
| New DataStore keys / Proto DataStore for grounding | `datastore-proto` matches a banned audit pattern; no persisted state needed for ephemeral fetch | Method parameters + in-memory per-turn context |
| Keeping `ToolSet`/`@Tool` R8 keeps after Skills deletion | Dead keeps hide future dead code and confuse audits | Delete/narrow keeps in `proguard-rules.pro`, verify release build |
| Bumping LiteRT-LM / OkHttp / Compose BOM for v2.2 | No capability in v2.2 requires it; v2.1 pinned stable after verification (litertlm 0.13.1→0.17.1 was the v2.1 work) | HOLD all versions; change code only |

## Stack Patterns by Variant

**If the grounding fetch needs isolation from chat streaming:**
- Give the fetcher its own lightweight `OkHttpClient` built from the same `NetworkModule` pattern (short `callTimeout` ~15s, `retryOnConnectionFailure(true)` is fine for idempotent GET, headers-only logging).
- Because sharing the `@Named("sse")` client (readTimeout=0, no-retry) is wrong for bounded fetches, and sharing the default download client (readTimeout=120s) waits too long before failing. A third tiny client is ~15 lines, zero deps — or just `defaultClient.newBuilder().callTimeout(15, SECONDS).build()` sharing the pool.

**If fetched pages are large or hostile (MBs of HTML, infinite streams):**
- Cap by reading at most N bytes/chars (`source.readUtf8(n)` / `take(n)`), then `cancel()`/`close()`. Never `body.string()` unbounded.
- Because OOM on a chat turn is the #1 grounding pitfall; a 4–8 KB char cap also bounds prompt-token cost.

**If cleartext (`http://`) page URLs appear:**
- Refuse them (require `https`) in release, consistent with the existing `network_security_config` cleartext block.
- Because allowing cleartext for arbitrary web pages reopens the posture v1.5 closed; LAN-cleartext exceptions stay scoped to user-configured endpoints only.

**If the SyntaxTheme fix touches DataStore:**
- Keep the existing `syntaxTheme` key name and the `CodeTheme → SyntaxTheme` migration map byte-identical; fix only the lookup/selection path.
- Because users' persisted theme choices must survive the fix; a key rename orphans every stored preference.

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| OkHttp 4.12.0 | Kotlin 2.3.20, AGP 9.3.0, `minSdk 28` | Current pinned; page fetch uses only stable `Request`/`Call`/`ResponseBody` APIs unchanged since OkHttp 3.x. |
| `ConnectivityManager.getNetworkCapabilities` | `minSdk 28` (needs API 21+, `NET_CAPABILITY_VALIDATED` needs API 23) | No compat shim needed; unconditional use is safe. Test double: wrap in a thin `NetworkMonitor` interface for unit tests (platform API is not unit-testable on JVM). |
| litertlm-android 0.17.1 | Deletions of `ToolSet` subclasses | Removing app-side `ToolSet` implementations does not touch the litertlm artifact; no catalog change, no Gradle resolution change. |
| Highlights 1.1.0 | Theme-fix (no version change) | Tokenizer output unchanged; only color-table selection changes. Existing 150 unit tests stay valid. |
| R8 full mode + AGP 9.3.0 | Post-deletion graph | Fewer keeps = smaller APK; re-run `assembleRelease` + audit script as the gate. |

## Deletion Inventory (stack-relevant surface to remove)

No version changes — but the roadmap must track these excisions or they linger:

1. **Skills surface:** `data/skills/{CalculatorSkill,CurrentTimeSkill,JsonFormatterSkill,SkillRepositoryImpl,SkillPreferences,SkillDescriptors,ToolGating}.kt`, `domain/skills/{Skill,SkillRepository,ToolExecutor}.kt`, `di/SkillsModule.kt`, `ui/chat/components/SkillChipsRow.kt`, local `ToolSet` loops + `LmStudioToolLoop.kt` tools[] path (+ `ToolGating` call in `LmStudioHelper.kt`).
2. **HF token surface:** `data/remote/network/HuggingFaceAuthInterceptor.kt`, `ApiKeyStore.{store,get,delete}HuggingFaceToken`, Settings HF field (`SettingsViewModel.kt:174-191`), `HuggingFaceViewModel` gated-model branch, `ModelDownloadWorker` token read + 401 message, `HelpScreen` token section, log-redaction regex for `hf_token` in `WarpedApplication.kt` (keep generic `token=`/`api_key=` arms).
3. **HF search UI:** `ui/huggingface/{HuggingFaceScreen,HuggingFaceViewModel,HuggingFaceUiState}.kt` search path + `HuggingFaceModule` binding (keep only what direct allowlist download needs); **KEEP** `assets/model_allowlist.json`, `ModelAllowlistRepository`, direct `https://huggingface.co/…/resolve/main/…` download without auth.
4. **R8 keeps:** narrow/remove ToolSet/`@Tool`/skills keeps in `proguard-rules.pro` after (1).

## Sources

- `gradle/libs.versions.toml` — pinned versions (HIGH): Kotlin 2.3.20, AGP 9.3.0, litertlm 0.17.1, OkHttp 4.12.0, Retrofit 3.0.0, coroutines 1.11.0, DataStore 1.2.1, Highlights 1.1.0, Hilt 2.60.1.
- `scripts/audit-dependencies.sh` — banned patterns + scope note (HIGH): direct-declaration check, transitive exemptions documented.
- `app/src/main/java/com/warped/di/NetworkModule.kt` — two-client OkHttp setup, headers-only logging for binary paths (HIGH).
- `app/src/main/AndroidManifest.xml:5` — `ACCESS_NETWORK_STATE` already declared (HIGH).
- `app/src/main/java/com/warped/domain/model/SyntaxTheme.kt` — 4 presets × light/dark complete; `fromKey` with `MONOKAI` fallback (HIGH).
- `AdvancedPreferences.kt:53-61` — legacy key aliases (`NORD`→ONE_DARK, `SOLARIZED_DARK`→MONOKAI) as theme-fix suspects (MEDIUM — mapping seen, call-site wiring not fully traced).
- `.planning/research/STACK.md` + `PITFALLS.md` (v2.0/v2.1) — `okhttp-sse` ban rationale, `Call.cancel()` pattern, minSdk 28 ground truth (MEDIUM — prior research, consistent with live catalog).

---
*Stack research for: Warped v2.2 Simplificación + Web Grounding*
*Researched: 2026-09-28*
