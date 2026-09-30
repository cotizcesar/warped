# SUMMARY — DDG Default Search + Sources Carousel

**Status:** complete — all 4 tasks executed, build + full unit suite green.
**Date:** 2026-09-30. **Commits:** `6e8dd653` (1.1), `f402e8d0` (2.1), `3ffb8323` (1.2).

## What changed

**`web_search` is now keyless by default.** New `DuckDuckGoSearchRepository`
(backend, Wave 1) GETs the documented DDG HTML endpoint
(`https://html.duckduckgo.com/html/?q=...`, desktop Chrome UA, stripped
client with `AuthInterceptor` removed — same policy as `WebPageFetcher`),
parses result anchors with Jsoup **parse-only** (`Jsoup.parse`, never
`connect()`, never form-POST), unwraps `//duckduckgo.com/l/?uddg=<real>`
wrappers with mandatory `http(s)` validation (anything else drops,
including the bare wrapper), and fuses title+snippet pairs through the
identical producer shape as Tavily (`GroundingPrompt.buildFusedBlock`,
OK/OMITIDA semantics, `WebContextSanitizer`, top-5/cap-10, 500-char
pass-through query cap, same `TavilySearchOutcome` type — no new outcome
type, no ToolSet schema change).

Policy: DDG yields ≥1 OK pair → `Grounded` (no key needed, silent success).
DDG empty/throw → Tavily fallback **only when a key is stored** (delegate
outcome verbatim, incl. `InvalidKey`/`UsageLimit`/`MissingKey`-race);
no key → `ModelOnly(FETCH_FAILED)` with no key nag. Offline and blank
query short-circuit pre-socket (`OFFLINE` / `FETCH_FAILED`).
`CancellationException` rethrows. Query/key contents never logged.

All five production search call sites rerouted (Wave 2): `ChatViewModel`
branch (same `when` arms, `MissingKey` arm kept for the race edge),
`LiteRTLmProvider`, `OpenAIProvider`, `AnthropicProvider`, and the shared
`CompatToolLoop` (covers LM Studio / Ollama / Custom) with threading
through `ProviderRouter` + `LmStudioHelper`. The Settings Tavily
key-probe (`SettingsViewModel.testTavilyConnection`) is intentionally
untouched — it probes the Tavily key, not the search path.

**Sources render as a horizontal carousel.** New `CompactSourceCard`
(272dp, in the locked 260–280 band) in `OgSourceCard.kt` reuses the card
pieces: `OgThumb` 64dp slot, `ogDisplayTitle` host fallback, `[N]` badge
construction, `OgCardDark`/M3-surfaceVariant containers (zero new colors,
zero purple). `MessageBubble` Fuentes block is now a `LazyRow`
(`spacedBy(8.dp)`, stable `[N]` keys): thumb tap → `openUrlInBrowser`,
card tap → preview sheet (host unchanged), text-only/legacy rows render
the same card without the thumb slot. Omitida rows stay struck below the
carousel; zero-ok renders no block (unchanged). English a11y only
(`"Preview source N"`, `"Open source N: <title>"`), `Role.Button`
mirrored.

## Test results

- `./gradlew :app:assembleDebug` — green.
- `./gradlew :app:testDebugUnitTest` (full) — **50 suites, 510 tests,
  0 failures, 0 errors, 0 skipped.**
- New `DuckDuckGoSearchRepositoryTest` — 16/16: parser fixtures (2 wrapped
  + bare link + `javascript:` dropped + missing snippet), `uddg` unwrap
  variants + scheme gate, 10-cap, blank-HTML, and the fallback matrix
  (DDG-ok±key → Grounded with zero Tavily calls; DDG-empty+key →
  verbatim delegate; DDG-empty/throw+no-key → `FETCH_FAILED`;
  DDG-throw+key → `InvalidKey` passthrough; all-blank → collapse;
  offline/blank-query no-socket; cancellation rethrows; 500-char cap).
- Existing suites follow the rename untouched in behavior:
  `SettingsTavilyTest` gate matrix 17/17, `LiteRTLmLoopTest` 24/24,
  plus `LmStudioCancelTest`, `RemoteSecretIsolationTest` (Tavily auth
  isolation still proves endpoint keys never reach search),
  `LocalToolLoopTest`, grounding/chat/settings packages — all green.
- No `TavilySearchRepository` signature change; `git diff --stat` shows
  only the new repo + test, the branch/executor reroute, and
  MessageBubble/OgSourceCard (+ stale-credit comment touch-ups).

## Deviations from plan (all auto-applied, no architecture change)

1. **Reroute widened to all 5 call sites (Rule 2).** The plan assumed only
   `ChatViewModel` + `OpenAIProvider` call `tavily.search`; the codebase
   has five (`LiteRTLmProvider`, `AnthropicProvider`, `CompatToolLoop`
   shared by LM-Studio/Ollama/Custom). Leaving three on the old path would
   keep no-key users on `MissingKey` for most providers, violating the
   plan Goal — so all were switched to the same DDG repo (same outcome
   type, mechanical rename + `ddg` threading).
2. **VM takes DDG instead of Tavily (plan wording).** "Add DDG alongside
   (do not remove Tavily)" was honored in spirit: Tavily stays in the
   graph as the DDG repo's fallback delegate + Settings probe, but the VM
   holds only `ddgSearchRepository` — keeping an unused Tavily field would
   be dead injection. Same for all providers.
3. **No Hilt module change (plan 1.1).** `TavilySearchRepository` /
   `WebPageFetcher` have no module bindings (pure `@Inject`
   singletons), so the DDG repo needed none either — Hilt satisfies
   `OkHttpClient` + `WebPageFetcher` + `ApiKeyStore` +
   `TavilySearchRepository` automatically. Verified via green assemble.
4. **Jsoup for parsing (plan-permitted).** Jsoup was on the main classpath
   (`parse-only; never connect()` policy), so the parser uses
   `Jsoup.parse` + `a.result__a` / `.result__snippet` selectors instead of
   regex, with the brittleness caveat in the KDoc.
5. **One mis-scoped sed, reverted inline (Rule 1).**
   `SettingsGroundingToggleTest` builds `SettingsViewModel` (Tavily probe
   — must stay); the mechanical rename briefly touched it and was
   reverted before commit. Caught by `compileDebugUnitTestKotlin`.

## DDG brittleness (honest)

DDG HTML scraping keys on the `a.result__a` / `div.result` /
`.result__snippet` markup shape — a DDG markup change yields zero usable
results. Fail-safe by construction: keyed users fall to Tavily, unkeyed
users get the fetch-failed model-only path (never a crash, never a
fabricated answer). Stated in the repo KDoc. No live-DDg network call is
made by any unit test (all HTML is fixture-supplied).

## On-device notes (honest)

No `adb` in this environment, so the carousel was verified by
`assembleDebug` (green) + code review of the tap wiring
(thumb/`IconButton` → `openUrlInBrowser`, card → `previewSource` sheet
host, unchanged) — not by screenshot. Visual confirmation (272dp card
scroll, thumb/browser/sheet taps, omitida rows, zero-ok) still wants one
on-device pass with a grounded answer.

## Threat flags

None — no new network surface beyond a keyless GET to the documented DDG
endpoint with the stripped client (reviewed: GET-only, desktop UA,
`AuthInterceptor` removed, no query/key logging, `uddg` http(s) gate at
parse + existing render-side image re-gate). No schema, auth, or storage
changes.
