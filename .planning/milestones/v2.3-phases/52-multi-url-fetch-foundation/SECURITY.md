# SECURITY.md — Phase 52 Multi-URL Fetch Foundation

**Phase:** 52 — multi-url-fetch-foundation (plans 52-01 + 52-02)
**ASVS Level:** 1 (no auth/session management in scope; network fetch + prompt-injection boundaries)
**Date:** 2026-09-28
**Verdict:** SECURED — 11/11 threats CLOSED, 0 open, 0 unregistered flags
**Auditor method:** grep-match proof per threat; starting hypothesis OPEN until code evidence found.

## Threat Verification

### Plan 52-01 (engine room)

| Threat ID | Category | Disposition | Evidence | Status |
|-----------|----------|-------------|----------|--------|
| T-52-01 (Jsoup parse-only) | Tampering | mitigate | `HtmlToTextExtractor.kt:63` `Jsoup.parse(html, url)` only; repo-wide grep for `Jsoup` in `app/src` shows zero `Jsoup.connect` matches; 64KB pre-parse cap upstream at `WebPageFetcher.kt:49` (`MAX_BODY_BYTES = 65536`) + `139` (capped streaming read) | CLOSED |
| T-52-02 (fused prompt elevation) | Elevation | mitigate | Sanitizer unchanged and still per-page pre-fusion: `WebPageFetcher.kt:153` `WebContextSanitizer.sanitize(extracted)` inside `fetch()`; `MultiUrlFetcher.kt:80,97` fuses `result.text` (already-sanitized) via `buildFusedBlock` — no raw-text fusion path; `MultiUrlFusionTest.kt:35` hijack+delimiter test gates it | CLOSED |
| T-52-03 (parse DoS) | Denial of service | mitigate | Input bounded by frozen 64KB cap (`WebPageFetcher.kt:49,139-145`); parse runs off UI thread (`WebPageFetcher.kt:87` `withContext(Dispatchers.IO)`, fan-out on `ioDispatcher = Dispatchers.IO` at `MultiUrlFetcher.kt:52`); blank-output → regex fallback → `ModelOnly` chain (`HtmlToTextExtractor.kt:54-59`, `WebPageFetcher.kt:148-150`) never hangs | CLOSED |
| T-52-04 (fetch auth leak) | Information disclosure | accept | Accepted in 52-01 with re-verification deferred to 52-02 — re-verification DONE: strip preserved verbatim at `WebPageFetcher.kt:56-67` (`interceptors().removeAll { it is AuthInterceptor }`). No client changes beyond the cancel-set fix. | CLOSED |
| T-52-SC (jsoup artifact) | Tampering | mitigate | `gradle/libs.versions.toml:17` pins `jsoup = "1.23.2"`, `:91` canonical `org.jsoup:jsoup`; `app/build.gradle.kts` adds it as the only v2.3 dep (per 52-01 summary). Maven provenance (latest==release) taken from RESEARCH audit, not re-verifiable offline. | CLOSED |

### Plan 52-02 (fan-out + wiring)

| Threat ID | Category | Disposition | Evidence | Status |
|-----------|----------|-------------|----------|--------|
| T-52-05 (activeCall race) | Denial of service | mitigate | `WebPageFetcher.kt:75` `ConcurrentHashMap.newKeySet<Call>()`; add-after-`newCall` at `:107`, remove-in-`finally` at `:158`, snapshot cancel-all at `:77-85`; `coroutineScope` (not `supervisorScope`) at `MultiUrlFetcher.kt:58` preserves single-cancel-path; cancel propagation tested (`MultiUrlFetcherTest.kt:144`) | CLOSED |
| T-52-06 (multi-page injection) | Tampering | mitigate | Same per-page sanitize as T-52-02 (`WebPageFetcher.kt:153`); orchestrator concatenates sanitized texts only (`MultiUrlFetcher.kt:96-97`); plan 52-01 hijack-across-pages test gates it (`MultiUrlFusionTest.kt:35-49`) | CLOSED |
| T-52-07 (auth leak on fan-out) | Information disclosure | mitigate | Stripped-client construction preserved verbatim (`WebPageFetcher.kt:56-67`); no new client config in `MultiUrlFetcher.kt` (reuses injected `WebPageFetcher`, no OkHttp usage). NOTE: the plan's "regression assertion that fetch requests carry no Authorization header" exists as code, not as a dedicated test — no `Authorization`/`AuthInterceptor` assertion found in `app/src/test`. Hardening gap only; mitigation itself is in code. | CLOSED (test-hardening note) |
| T-52-08 (redirect SSRF widening) | Tampering | mitigate | `MAX_REDIRECTS = 3` (`WebPageFetcher.kt:51`) enforced at `:111`; http/https scheme gate at `:116` (`resolved.scheme != "http" && != "https"` → ModelOnly); `followRedirects(false)` at `:60` keeps manual-redirect control; unchanged by fan-out (redirect loop is per-URL inside `fetch`) | CLOSED |
| T-52-09 (Fuentes/order spoof) | Spoofing | mitigate | `targets.map { async } .awaitAll()` (`MultiUrlFetcher.kt:65-73`) preserves index order; `url to fetch()` pairs + `okPages` built in result order (`:75-86`) so Fuentes == block order; `distinct().take(MAX_URLS)` dedupe-before-fanout at `:59`; order asserted (`MultiUrlFetcherTest.kt:47`, `MultiUrlFusionTest.kt:15-27`) | CLOSED |
| T-52-SC (package installs) | Tampering | accept | No package-manager installs in 52-02 (Jsoup landed in 52-01; `tech_stack.added: []` per 52-02 summary). Nothing to gate. | CLOSED |

### Prompt-specified invariants (cross-cutting, verified in code)

| Invariant | Evidence | Status |
|-----------|----------|--------|
| AuthInterceptor stripped from fetch client | `WebPageFetcher.kt:65` | CLOSED |
| 64KB cap + 8/10/20s timeouts + 3-redirect scheme gate frozen | `WebPageFetcher.kt:49-51,57-60,111,116,139` | CLOSED |
| No history rewrite (persisted originals) | `ChatViewModel.kt:270` save-before-hook; `:436-440` only outgoing request's last message carries augmented text | CLOSED |
| Providers/helpers untouched | `ChatViewModel.kt:406-421` helper resolution unchanged; prompt-prefix augmentation only (`:316`) | CLOSED |

## Unregistered Flags

None. Both plan summaries declare `Threat Flags: None`, and the new surfaces (`Jsoup.parse`, fan-out, progress state) all map to existing threat IDs (T-52-01, T-52-05..T-52-09).

## Accepted Risks Log

- T-52-04: accepted in-plan (no client changes in 52-01); re-verified CLOSED via 52-02 strip preservation.
- T-52-SC (52-02): accepted in-plan (no installs in 52-02). No residual risk.
- Residual from v2.2 (unchanged scope, per T-52-08 plan): pasted LAN URLs fetch as-is. Carried forward, not introduced here.

## Hardening Notes (non-blocking)

1. T-52-07 has no dedicated test asserting `Authorization` absence on fetch requests — consider adding an OkHttp `recordedRequest` assertion to lock the strip against future client refactors.
2. T-52-SC (52-01) Maven provenance (latest==release==1.23.2) relies on the RESEARCH audit; re-verify on next Jsoup bump.
