# Stack Research: Web Grounding v2 (v2.3 milestone)

**Domain:** Multi-URL web grounding for on-device LLM chat (Android)
**Researched:** 2026-09-28
**Confidence:** HIGH

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Jsoup `org.jsoup:jsoup` | 1.23.2 | HTML→text extraction (parse-only) | WHATWG HTML5-spec parser — the same DOM a browser builds. The hand-rolled regex extractor breaks on malformed HTML, nested `<script>`, `<noscript>` fallbacks, tables/lists losing structure, and entity edge cases. Jsoup's `doc.body().text()` + CSS selectors (`article p`, `main`) give main-content extraction no regex can match. Zero runtime dependencies, ~430 KB jar (R8-shrunk further), MIT license, actively maintained (1.23.2 released Aug 2026). This is the single new dependency v2.3 needs. |
| Desugar JDK libs NIO `com.android.tools:desugar_jdk_libs_nio` | 2.1.5 | Build config required by Jsoup on Android | Jsoup's official docs mandate core library desugaring with the NIO spec on Android (Java 8+ API usage). The project does NOT currently enable desugaring (`compileOptions` only sets Java 17 compat) — adding Jsoup without this breaks at runtime on older API levels. Requires AGP 7.4+; project is on AGP 9.3.0, so compatible. |
| Kotlinx Coroutines (existing) | 1.11.0 | Multi-URL parallel fetch fan-out | `async` + `awaitAll` with a `Semaphore(3)` permits gives bounded 2–5 URL parallelism with structured cancellation (one failed/slow page never blocks the turn; Stop button cancels the parent scope). No new library — already in the catalog. |
| OkHttp (existing) | 4.12.0 | Per-URL bounded fetch transport | The existing `WebPageFetcher` policy (8s connect / 10s read / 20s call, 3 manual redirects, 64 KB cap, AuthInterceptor stripped, text/html+text/plain only) is reused unchanged per URL; multi-URL just calls it N times concurrently. No new library. |
| Room (existing) | 2.8.5 | Per-chat web-toggle persistence | One nullable/boolean column (e.g. `web_grounding_override`) on the conversations table + a **manual** migration (project convention: `Migrations.kt` manual migrations, schemas not versioned — AutoMigration is explicitly avoided). No new library. |
| DataStore Preferences (existing) | 1.2.1 | Global default-ON toggle (unchanged) | Resolution order becomes per-chat override → global default. No new library. |
| WorkManager (existing) | 2.12.0 + hilt-work 1.4.0 | Offline retry queue for failed fetches | `OneTimeWorkRequest` with `Constraints(NetworkType.CONNECTED)` + backoff + input Data (message ID, URL list) survives process death and retries when connectivity returns — exactly the offline-resilience requirement. Project already ships WorkManager for model downloads. No new library. |
| Compose Material3 via BOM (existing) | BOM 2026.06.01 | Sources preview UI | `ModalBottomSheet` (Material3, in the existing Compose BOM) renders extracted per-source text without leaving chat. No new library. |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Jsoup | 1.23.2 | DOM parse + `body().text()` + main-content selectors | Always in v2.3 — replaces the regex core of `HtmlToTextExtractor`, keeping its 4000-char line-boundary truncation contract and the downstream `WebContextSanitizer` untouched |
| Desugar NIO | 2.1.5 | `coreLibraryDesugaring` configuration | Mandatory alongside Jsoup; `isCoreLibraryDesugaringEnabled = true` in app build config |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| R8 / ProGuard (existing) | Keep Jsoup reachable code, shrink the ~430 KB jar | Jsoup 1.22.2+ ships an R8 rule ignoring the optional re2j dependency — no manual keep rules needed unless reflection-based selectors are stripped; verify with `assembleRelease` + smoke test |

## Installation

```kotlin
// gradle/libs.versions.toml
[versions]
jsoup = "1.23.2"
desugar = "2.1.5"

[libraries]
jsoup = { group = "org.jsoup", name = "jsoup", version.ref = "jsoup" }
desugar-nio = { group = "com.android.tools", name = "desugar_jdk_libs_nio", version.ref = "desugar" }
```

```kotlin
// app/build.gradle.kts
android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true // REQUIRED by Jsoup on Android
    }
}
dependencies {
    implementation(libs.jsoup)
    coreLibraryDesugaring(libs.desugar.nio)
}
```

No version bumps needed for: coroutines 1.11.0, okhttp 4.12.0, room 2.8.5,
workmanager 2.12.0, datastore 1.2.1, compose-bom 2026.06.01 — all already
satisfy v2.3 requirements.

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Jsoup (parse-only) | Extend hand-rolled regex extractor | Only if APK-size budget becomes hard: regex adds 0 bytes but every malformed-HTML fix is bespoke code + tests. Jsoup's marginal size (~430 KB pre-R8) buys a spec-compliant parser the team never maintains. Prefer Jsoup. |
| Jsoup | readability4j / Crux / Boilerpipe ports | Never for v2.3 — readability ports are thinly maintained, add transitive deps, and solve "article boilerplate removal" which exceeds the need (4000-char fused context budget caps value). Jsoup CSS selectors (`article`, `main`, `[role=main]`) cover 90% of that need. |
| Jsoup | Apache Tika | Never — multi-MB, server-oriented content detection; absurd for 64 KB page snippets. |
| WorkManager retry | In-process `ConnectivityManager.NetworkCallback` re-fetch | Only if retry must complete within the active chat session AND process-death survival is explicitly out of scope. WorkManager is the default: it survives process death, coalesces retries, and the project already pays its cost. |
| WorkManager retry | Ketch / Fetch download managers | Never — those orchestrate large resumable file downloads (GB models). Grounding pages are ≤64 KB in-memory fetches; a download manager adds service/notification/activity overhead for zero benefit. |
| Room column + manual migration | Proto DataStore per-chat map | Never — per-chat state belongs with the conversation row (queried together, deleted together on conversation delete); a sidecar map risks orphan drift. |
| ModalBottomSheet (M3) | New activity / Custom dialog fragment | Never — bottom sheet keeps chat context visible, matches existing Compose-only navigation, zero new deps. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `Jsoup.connect()` fetcher | Bypasses the project's hardened fetch policy: no AuthInterceptor stripping (API-key leak risk to arbitrary hosts), no 64 KB cap, no manual-redirect control, wrong timeouts. Fetching is a security boundary, not a convenience call. | Keep `WebPageFetcher` (OkHttp) for transport; use Jsoup **only** as `Jsoup.parse(htmlString)` on the already-fetched capped body |
| `com.google.re2j:re2j` | Optional Jsoup dependency enabling regex CSS selectors (`:matches()`); unneeded (selectors used are static tag/class names) and every added dep is R8 surface + maintenance | Skip it; Jsoup 1.22.2+ R8 rules already ignore its absence |
| Jsoup `Safelist`/`Cleaner` as hijack sanitizer | `Cleaner` prevents **XSS markup**, not **prompt-injection prose** ("ignore previous instructions" is clean HTML). Swapping would silently drop the prompt-injection defense. | Keep existing `WebContextSanitizer` + `[WEB CONTEXT]` framing unchanged; Jsoup feeds it cleaner *text*, defense stays where it is |
| Coil / image loaders | Sources preview is extracted *text*; no favicons/thumbnails in v2.3 scope | Plain Material3 `ListItem` with domain-initial avatar |
| Firebase / remote queue | Offline retry is local-only, short-lived (minutes, not days); no sync requirement | WorkManager with `NetworkType.CONNECTED` constraint |

## Stack Patterns by Variant

**If a page is text/plain (not HTML):**
- Skip Jsoup entirely, pass through to the existing truncation path
- Because parsing plain text as HTML adds nothing and risks entity mangling

**If Jsoup parse returns blank body text (JS-heavy SPA shell):**
- Fall back to `<title>` + `<meta name="description">` extraction (Jsoup selectors, same dep)
- Because a title+description grounding is strictly better than silent model-only, and costs ~5 lines

**If per-chat toggle is unset (NULL):**
- Resolve to the global DataStore default-ON
- Because tri-state (null = inherit) avoids backfilling every historic conversation row and keeps the global toggle meaningful

**If more than 5 URLs are pasted:**
- Fetch the first 5, note remainder as `[+N more not fetched]` in sources UI
- Because context budget (5 × 4000 chars already exceeds any local model's window — fusion must rank/truncate) and latency (parallel fetch wall-clock ≈ slowest page) both demand a cap

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| jsoup 1.23.2 | AGP 9.3.0 + Kotlin 2.3.20 + Java 17 target | Pure Java 8 bytecode, no Kotlin dependency; zero conflict with Kotlin BOM |
| jsoup 1.23.2 | minSdk (project 28+) | Requires desugar NIO enabled; desugar 2.1.5 needs AGP 7.4+ ✅ (project AGP 9.3.0) |
| desugar_jdk_libs_nio 2.1.5 | R8 full-mode release builds | Standard Google artifact; no extra ProGuard config |
| work-runtime-ktx 2.12.0 | hilt-work 1.4.0 (existing) | `@HiltWorker` retry worker gets fetcher/extractor injected — matches existing download-worker pattern |

## Sources

- Context7 `/jhy/jsoup` (342 snippets, v1.20.1 metadata; README confirms 1.23.x line, zero runtime deps) — docs fetched
- https://jsoup.org/download (official: current release **1.23.2**, Gradle coordinate, "no required runtime dependencies", Android desugaring requirement) — HIGH confidence
- https://jsoup.org/news/release-1.23.1 (perf: 18% faster parsing; confirms active maintenance) — HIGH confidence
- https://jsoup.org/news/release-1.22.2 (R8 rule ignoring optional re2j; TagSet text-boundary improvements aiding `Element.text()`) — HIGH confidence
- mvnrepository.com `org.jsoup:jsoup` versions (1.23.2 latest, Aug 27 2026; 1.23.1 Jul 2026) — HIGH confidence
- github.com/google/desugar_jdk_libs CHANGELOG (2.1.5 latest, 2025-02-14; NIO variant documented) + mvnrepository `desugar_jdk_libs:2.1.5` — MEDIUM-HIGH confidence
- Project source: `app/build.gradle.kts` (no desugaring enabled — integration gap), `WebPageFetcher.kt` (bounded policy to reuse), `HtmlToTextExtractor.kt` (regex core to replace), `Migrations.kt` (manual-migration convention) — HIGH confidence

---
*Stack research for: Warped v2.3 Web Grounding v2 (multi-URL, sources preview, per-chat toggle, offline retry, extraction quality)*
*Researched: 2026-09-28*
