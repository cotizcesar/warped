---
audit_acknowledged:
  milestone: v2.5
  at: 2026-10-01
  status: unknown
---

# Execution SUMMARY — image-turn routing + favicon fallback

**Date:** 2026-09-29 / 2026-09-30 UTC
**Plan:** `.planning/quick/20260929-image-turn-routing-favicon/PLAN.md`
**Status:** COMPLETE — both tasks executed, all verification green
**Commits:** `6290bcbc` (Task 1 routing), `93d6e91a` (Task 2 favicon)

## What was built

**Task 1 — image-intent routing straight to Tavily (`6290bcbc`, 14 files):**

- `DuckDuckGoSearchRepository.search()`: when `includeImages=true` AND a
  Tavily key is stored, the DDG leg is skipped entirely and Tavily runs
  direct with `include_images=true` (key presence check follows the existing
  `getTavilyKey()` + zero-fill pattern; the delegate re-reads the key, so the
  `MissingKey` key-race edge is preserved). Unkeyed image-intent turns fall
  through to the DDG leg (text grounding, empty grid). Non-image turns keep
  the DDG-primary policy byte-identical.
- `ChatViewModel`: new `ApiKeyStore` dependency (read-only presence probe;
  the VM still never calls Tavily directly). After the outcome mapping, an
  unkeyed image-intent turn with an empty grid attaches the new
  `ModelOnlyNotice.IMAGES_NEED_KEY` banner (Settings path) alongside grounded
  text or the outcome notice. Scoped to `wantImages && grid empty && no key`
  — keyed and non-image turns untouched.
- New `IMAGES_NEED_KEY` enum value + `ModelOnlyBanner` branch + copy EN/ES
  (`bubble_images_need_key`: "Image results need a Tavily key — get one at
  tavily.com and paste it in Settings > Web Search.").
- Tests: 5 new repo-seam tests (`DuckDuckGoSearchRepositoryTest`, now 23) +
  4 new VM-seam tests (`ChatAlwaysSearchTest`, now 10); all 7 `ChatViewModel`
  construction sites wired with an unkeyed `ApiKeyStore` stub.

**Task 2 — S2 favicon fallback (`93d6e91a`, 3 files):**

- `faviconFallbackUrl(pageUrl)` in `OgSourceCard.kt`: host via `URI`, returns
  `https://www.google.com/s2/favicons?domain=<host>&sz=128`, null on
  unparseable/blank host. Adopted in `OgSourceCard`, `CompactSourceCard`,
  and the `SourcePreviewSheet` header as
  `gatedHttpImageUrl(ogImageUrl) ?: faviconFallbackUrl(url)?.let(::gatedHttpImageUrl)`
  — existing Coil `OgThumb`, existing `http(s)` gate, existing `onError`
  collapse. Zero purple, no new dependency.
- Tests: new `FaviconFallbackTest` (14 tests: exact-URL table + bad-host cases).

## Deviations from plan

1. **Routing helper lives in the repo, not VM-inline** (plan-allowed cleaner
   option). Rationale: `DuckDuckGoSearchRepository` already owns both
   `ApiKeyStore` and the Tavily delegate — zero new VM dependencies for the
   Tavily-direct call, single seam, directly testable. The VM gained only the
   key-presence probe for the notice gate.
2. **Matrix case 3 (image-intent + unkeyed + DDG-fail): single banner slot
   forces precedence — `IMAGES_NEED_KEY` wins over `FETCH_FAILED`.**
   Rationale: the user asked for images and storing a key is the actionable
   fix; a DDG failure is not user-fixable. Non-image full-failure policy
   (`FETCH_FAILED`, no key nag) is unchanged. Test pins this explicitly.
3. **New `IMAGES_NEED_KEY` enum value + string** instead of reusing
   `TAVILY_MISSING_KEY`: the existing copy says "Model-only answer", which is
   false on case-2 turns that carry grounded DDG text. Same plumbing
   (`modelOnlyNotice` slot + `ModelOnlyBanner`), honest copy. ES translation
   included per project EN+ES parity.

## Verification

- Targeted: `ChatAlwaysSearchTest` 10/10, `DuckDuckGoSearchRepositoryTest`
  23/23, `ImageIntentTest` 31/31, `TavilySearchRepositoryTest` 19/19,
  `FaviconFallbackTest` 14/14, `OgSourceCardHelpersTest` 10/10 — all green.
- Phase: `./gradlew :app:assembleDebug` BUILD SUCCESSFUL;
  `./gradlew :app:testDebugUnitTest` full suite **599 tests, 0 failures,
  0 errors, 0 skipped**.
- Routing matrix: cases 1–6 all covered (1: repo direct test + VM grid test;
  2: repo fall-through + VM notice test; 3: repo fail test + VM precedence
  test; 4: existing false-threading test + new keyed non-image test;
  5: existing fallback tests; 6: existing FETCH_FAILED tests).
- Stub scan: no stubs introduced. Threat scan: no new surface — S2 favicon is
  a client-side image URL through the existing gated Coil pipeline (same trust
  shape as `og:image`); Tavily-direct reuses the existing repo/client/auth.

## On-device notes (deferred to user — no adb in this environment)

- Live image relevance (are Tavily `images[]` on image-intent turns good?) +
  grid visuals need a screenshot on a keyed device.
- S2 favicons are best-effort (some hosts have no favicon; S2 returns a
  default glyph) — strictly better than the previous hollow box.
- Follow-up (out of scope, noted honestly): if the `LocalToolLoop` /
  provider `web_search` executor path also serves image-intent turns, it still
  bypasses the image-direct routing — this fix covers the `ChatViewModel`
  pre-search branch only.

## Self-Check: PASSED

- Files verified on disk: `FaviconFallbackTest.kt` (new), all modified
  sources present; `git status` shows only the two task commits ahead plus
  pre-existing unrelated tree noise (build/, launcher monochrome) untouched.
- Commits `6290bcbc` + `93d6e91a` verified in `git log`; no unintended file
  deletions in either commit.
