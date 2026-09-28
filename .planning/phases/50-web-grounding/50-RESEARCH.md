# Phase 50: Web Grounding — Research

**Researched:** 2026-09-28
**Phase:** 50-web-grounding (WEB-01..WEB-06)
**Mode:** coarse granularity, zero new dependencies

> NOTE (runtime): `gsd-phase-researcher` / `gsd-planner` / `gsd-plan-checker` subagent types are not available in this runtime (OpenCode subagent, no Agent() tool). Research, pattern mapping, planning, and verification below were performed inline against the same contracts (CONTEXT.md + REQUIREMENTS.md + UI-SPEC.md + STATE.md + codebase evidence).

## 1. URL detection (WEB-01)

- Deterministic heuristic per CONTEXT: first `http(s)://` URL in the user message triggers exactly one fetch; 2nd+ URLs ignored.
- Regex (planner-locked shape, executor writes verbatim): `https?://[^\s<>"')\]]+` — take `find()` (first match), trim trailing punctuation `.,;:!?` that is sentence punctuation, not URL. No linkification library, no new dependency.
- Detection runs in `ChatViewModel.sendMessage` before inference, gated on the grounding toggle (default ON). When toggle OFF: skip entirely — chat behaves exactly as pre-50.
- New pure-Kotlin object `UrlDetector` in `data/grounding/` — fully unit-testable with zero Android dependencies.

## 2. Bounded fetch (WEB-02)

- Reuse the shared `OkHttpClient` from `NetworkModule.provideOkHttpClient` (singleton, already has connection pool + auth/logging interceptors). Do NOT use the `@Named("sse")` client (infinite read timeout is wrong for a bounded fetch).
- Per-call bounds via `client.newBuilder()` (shares pool/dispatcher, overrides only timeouts — standard OkHttp pattern):
  - `connectTimeout(8, SECONDS)`, `readTimeout(10, SECONDS)`, `callTimeout(20, SECONDS)` (overall backstop).
  - `followRedirects(false)` + manual redirect loop capped at **3** (OkHttp default follows 20 — too many; manual loop returns the 3rd-hop response or aborts with redirect-limit failure).
  - Byte cap **64 KB**: read via `response.body.source()` with a bounded loop (`read(byteArray, 0, min(8192, remaining))`), close the source at the cap. Never `body.string()` (unbounded).
  - Browser User-Agent header: `Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36` (exact string locked here so executor does not invent one).
  - `Accept: text/html, text/plain` — skip non-HTML/plain content types (PDF/video) with a fetch-failure → model-only path.
- Cancelability: `WebPageFetcher.fetch()` retains the `Call`; exposes `cancel()`. `ChatViewModel.stopGeneration()` calls `fetchCall.cancel()` alongside the existing `helper.stopResponse()` + `generationJob.cancel()`. New-turn pre-cancel (CR-02 site, `ChatViewModel.kt:236`) also cancels any in-flight fetch `Call`. Fetch runs on `Dispatchers.IO`, never blocks Main.
- Offline pre-check via platform `ConnectivityManager` (`activeNetwork` + `NetworkCapabilities.NET_CAPABILITY_INTERNET` / `VALIDATED`); no app code uses it yet (grep zero matches — new import, standard platform API, no dependency). Offline short-circuits to the model-only path WITHOUT attempting the fetch.

## 3. HTML→text + budget (WEB-03)

- Hand-rolled extractor `HtmlToTextExtractor` (pure Kotlin, unit-testable):
  1. Remove `<script…>…</script>`, `<style…>…</style>`, `<noscript…>…</noscript>`, HTML comments (case-insensitive DOT_MATCH_ALL).
  2. Replace block tags (`p/div/h1-h6/li/tr/br`) with `\n`, then strip all remaining tags.
  3. Decode entities: `&amp; &lt; &gt; &quot; &#39; &nbsp;` + numeric `&#NNN;` / `&#xHHH;`.
  4. Collapse `\n{3,}` → `\n\n`, collapse horizontal whitespace, trim. Prepend `<title>` text as first line when present.
  5. Truncate to **~4 KB budget (4000 chars)** at a line boundary with `… [truncado]` marker (fits the smallest allowlisted model window per CONTEXT).
- Quality bar (executor must demonstrate, Jsoup-escalation trigger): on a saved Wikipedia-article HTML fixture, extracted text must contain the article title + first two section headings + ≥200 chars of body prose with zero `<`/`>` tag remnants. If the bar fails, executor escalates to Jsoup (out-of-scope exception path per CONTEXT) — default expectation is hand-rolled passes.
- Output wrapped as a delimited block (exact delimiters locked):
  ```
  [WEB CONTEXT — fuente [1]: {url}]
  {extracted text ≤4000 chars}
  [FIN WEB CONTEXT]
  ```

## 4. Trust boundary + grounding prompt (WEB-04, WEB-05)

- `WebContextSanitizer` (pure Kotlin, unit-testable):
  - Drop instruction-like lines matching (case-insensitive): `^(ignore|olvida|disregard).{0,40}(previous|anterior|instructions|instrucciones)`, `you are now|eres ahora|actúa como|system\s*:|as an ai|como ia` — line-granular removal, never whole-block rejection.
  - Escape delimiter collisions: replace any fetched line containing `[WEB CONTEXT` / `[FIN WEB CONTEXT` with `[WEB-CONTEXT` / `[FIN-WEB-CONTEXT` so content cannot break out of the block.
- Failure rule (hard): fetch errors (offline, timeout, redirect-limit, byte-cap abort still yields partial? NO — cap abort yields truncated-partial which IS usable content, marked `[truncado]`; network/timeout/HTTP-error/empty-text yield NOTHING) — failures are NEVER injected as context. They route to the model-only path with a UI notice.
- Injection point (works for BOTH transports without touching `LlmModelHelper`): current-turn augmentation in `ChatViewModel` — the outgoing request's current user text becomes `{groundingSystemPrompt}\n\n{webBlock}\n\n{originalUserText}`. Why current-turn prefix and not history: `LiteRTLmProvider` reuses one long-lived `Conversation` (`initialMessages` only on first creation) so a history-inserted system row would persist across turns; remote providers take the full message list each call but also accept the prefix identically. Provider-agnostic, no Room writes (persisted transcript keeps the original URL text), `LlmModelHelper` interface unchanged.
- Grounding system prompt (exact text locked, Spanish, executor writes verbatim):
  > `Responde usando el bloque [WEB CONTEXT] cuando sea relevante. Cita las fuentes con marcadores [1]/[2]. Si necesitas información fresca y no hay bloque de contexto, pide al usuario que pegue un enlace. Nunca inventes URLs: solo cita las URLs del bloque o las que el usuario pegó.`
- Ephemeral metadata WITHOUT Room migration: `EntityMappers.toDomain/toEntity` construct `ChatMessage` field-by-field, so new `ChatMessage` fields with defaults (`groundedSources: List<String> = emptyList()`, `modelOnlyNotice: ModelOnlyNotice? = null` with `enum ModelOnlyNotice { OFFLINE, FETCH_FAILED }`) are migration-free — mappers simply ignore them. Grounding metadata lives only in memory.

## 5. State + UI wiring (WEB-06)

- Settings toggle: `AdvancedPreferences` boolean key `web_grounding_enabled`, default `true` (`Flow<Boolean>` + `setWebGroundingEnabled`, same pattern as `thinking_enabled`). `SettingsUiState.webGroundingEnabled`, `SettingsViewModel` collector + setter. `SettingsScreen` new "Web" section card AFTER the Data card (Data → Web → Display → App → Security per UI-SPEC), reusing the section pattern (titleMedium header, 12dp card, `#2B2B29` container, 16dp padding).
- Chat state (single-owner convention, PERF-14): fetch status `isFetchingWeb: Boolean` joins `ChatInputState` (mirrors `isGenerating`); per-message grounding metadata rides the new `ChatMessage` ephemeral fields (no new transcript-state maps). While `isFetchingWeb`, Send stays swapped to Stop (`isGenerating` stays true during fetch per UI-SPEC).
- New composables in `ChatScreen.kt` bottom-bar column + transcript (exact copy from UI-SPEC, executor writes verbatim):
  - Status chip (above `ChatInputBar`, 8dp gap, transparent container, 16dp spinner + `Text("Leyendo página…")` 14sp, a11y `"Leyendo página. Pulsa Detener para cancelar la lectura."`).
  - Fuentes list (4dp below grounded answer bubble: `Text("Fuentes")` 12sp SemiBold + `"[1] {url}"` 14sp items, 4dp spacers, wrap never truncate; zero sources → no block at all).
  - Model-only banner (4dp above qualified message: `Icons.Filled.Info` 16dp + muted `onSurfaceVariant` 14sp; offline copy `Sin conexión. Respuesta solo del modelo, sin contenido de la página.` vs failure copy `No se pudo leer la página. Respuesta solo del modelo — revisa tu conexión o pega otro enlace.`).
  - Toggle row: title `Grounding web` (16sp) + description `Lee el contenido de los enlaces que pegues en el chat.` (12sp muted) + `Switch` with `contentDescription "Grounding web"`.
- `strings.xml` (+ `values-es/`): executor adds ONLY the keys above; no other strings touched.

## 6. Risks / landmines

- LiteRT long-lived `Conversation` (LRT-02): grounding MUST be current-turn prefix, never a history system row — a history row would leak into all future turns of the conversation.
- `stopGeneration()` snapshot-then-null discipline (WR-05): cancel the fetch `Call` BEFORE nulling, same ordering as `helper.stopResponse()`.
- `ChatViewModel.sendMessage` turn pre-cancel (CR-02, line ~236): cancel in-flight fetch there too or two turns interleave.
- `InputSanitizer.sanitize` runs provider-side on user text (OpenAI path) — the `[WEB CONTEXT]` prefix passes through it; sanitizer only strips math/invisible chars, safe. LiteRT path sanitizes ALL messages including our prefix — same, safe.
- `toProviderText()` for TOOL rows: grounding prefix is USER-role text, untouched by the TOOL replay path.
- `NetworkModule` singleton client carries `AuthInterceptor` (endpoint keys) — page fetches to arbitrary hosts must NOT send endpoint Authorization headers. `AuthInterceptor` presumably keys off host; executor MUST verify (`read_first` on `AuthInterceptor.kt`) and, if host-agnostic, build the fetch call on `client.newBuilder().interceptors(remove auth)` — concrete fallback: `okHttpClient.newBuilder().apply { interceptors().removeAll { it is AuthInterceptor } }`.
- Byte-cap abort yields usable truncated content (with `[truncado]` marker); all other failures yield zero injected bytes.
- Zero new dependencies: `scripts/audit-dependencies.sh` must stay green.

## 7. Validation Architecture

- Unit tests (JUnit5 + Truth, existing `app/src/test` convention): `UrlDetectorTest` (first-URL, 2nd-ignored, trailing-punctuation, no-URL), `HtmlToTextExtractorTest` (script/style strip, entity decode, 4000-char truncation at line boundary, Wikipedia-fixture quality bar), `WebContextSanitizerTest` (hijack-line removal, delimiter escape), `GroundingPromptTest` (prefix assembly order: prompt → block → original; failures produce no block).
- Gates: `scripts/audit-dependencies.sh` green; `./gradlew :app:assembleDebug` green; grep gates (no `Jsoup|jsoup` import unless escalated; no hardcoded non-browser User-Agent).
- Device smoke (deferred to release UAT like Phase 49): paste URL → "Leyendo página…" → grounded answer + Fuentes; airplane mode → offline banner + model-only answer; Stop during fetch cancels without freeze.
