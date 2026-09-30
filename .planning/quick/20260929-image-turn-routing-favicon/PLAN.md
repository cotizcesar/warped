# Quick-task PLAN — image-turn routing + favicon fallback

**Diagnoses locked** (on-device evidence 2026-09-29, key confirmed in app):
- BUG 1: DDG-primary always runs first and succeeds → Tavily fallback (the ONLY
  image source via `include_images`) never executes → `groundedImages` always
  empty on image-intent turns.
- BUG 2: search-result sources never carry `og:image` (OG scrape lives only in
  the fetchAll HTML branch) → `OgSourceCard` renders hollow.

**Out of scope:** new providers, OG scrape changes, sheet/modal changes,
download path changes.

## Fix summary

- **FIX 1 (routing):** image-intent turns with a stored Tavily key go STRAIGHT
  to Tavily with `include_images=true` (skip DDG for that turn — latency +
  relevance). Image-intent turns WITHOUT key → honest text answer + actionable
  notice (images need Tavily key → Settings path), still grounded via DDG text.
  Non-image turns keep the current DDG-primary policy untouched.
- **FIX 2 (favicon):** when `og:image` is null, load the Google S2 favicon by
  host (`https://www.google.com/s2/favicons?domain=<host>&sz=128`) through the
  existing Coil singleton + existing `http(s)` gating. Text-only card only if
  the host itself is unusable. Cheap, keyless, reliable (S2 is Google infra,
  not a new API dependency).

---

## Task 1 — Fix 1: image-intent routing straight to Tavily

**Files:**
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` (search branch ~L560-650:
  gate on `ImageIntent.hasImageIntent(query)` + key presence BEFORE the DDG call;
  image-intent+keyed → `tavilySearchRepository.search(..., includeImages=true)`
  direct; image-intent+unkeyed → DDG text grounding + `TAVILY_MISSING_KEY`-style
  actionable notice that images need the Tavily key → Settings; non-image turns
  keep the existing `ddgSearchRepository.search(...)` call byte-identical)
- `app/src/main/java/com/warped/data/grounding/DuckDuckGoSearchRepository.kt`
  (only if a routing helper is cleaner than VM-inline: e.g. early `includeImages`
  + key check at the top of `search()` that skips the DDG leg; keep the existing
  DDG-OK / DDG-fail→Tavily / unkeyed→FETCH_FAILED policy for non-image turns)
- `app/src/test/java/com/warped/ui/chat/ChatAlwaysSearchTest.kt` (extend: routing
  matrix — intent×key/DDG-outcome; grid-appears-when-images cases)
- New or extended repo-level test:
  `app/src/test/java/com/warped/data/grounding/DuckDuckGoSearchRepositoryTest.kt`
  (routing matrix at the repo seam if the helper lives there)

**Routing matrix (must all be covered by tests):**

| # | Image intent? | Tavily key? | DDG outcome | Expected |
|---|---|---|---|---|
| 1 | yes | yes | n/a (skipped) | Tavily direct, `include_images=true`, grid when `images[]` non-empty |
| 2 | yes | no | OK (text) | DDG text grounding, empty grid, actionable notice (images need key → Settings) |
| 3 | yes | no | fail | DDG FETCH_FAILED notice + same images-need-key notice (honest, no crash) |
| 4 | no | yes/no | OK | DDG-primary as today, `include_images=false`, no grid |
| 5 | no | yes | fail | Tavily fallback as today (unchanged policy) |
| 6 | no | no | fail | FETCH_FAILED, no key nag (unchanged policy) |

**Notes:**
- Key-presence check must follow the existing `ApiKeyStore.getTavilyKey()` +
  zero-fill pattern (never log the key).
- The unkeyed image-intent notice must reuse the existing `ModelOnlyNotice` /
  message-notice plumbing and point at the Settings path; no new navigation.
- `LocalToolLoop` / provider `web_search` executor routing is NOT changed —
  this fix is scoped to the `ChatViewModel` pre-search branch (the image-grid
  path). If verification shows the tool-loop path also serves image turns,
  note it honestly as follow-up; do not expand scope.

**Verify:**
- `<automated>./gradlew :app:testDebugUnitTest --tests
  "com.warped.ui.chat.ChatAlwaysSearchTest" --tests
  "com.warped.data.grounding.DuckDuckGoSearchRepositoryTest" --tests
  "com.warped.data.grounding.ImageIntentTest" --tests
  "com.warped.data.grounding.TavilySearchRepositoryTest"</automated>

**Done:** all 6 routing-matrix cases pass; image-intent+keyed turns populate
`groundedImages` when Tavily returns `images[]`; non-image turns produce
byte-identical behavior to today (existing tests green unchanged).

---

## Task 2 — Fix 2: S2 favicon fallback in source cards

**Files:**
- `app/src/main/java/com/warped/ui/chat/components/OgSourceCard.kt`
  (add pure-Kotlin `faviconFallbackUrl(pageUrl: String): String?` next to
  `gatedHttpImageUrl`/`ogHostOf`: parse host via `URI`, return
  `https://www.google.com/s2/favicons?domain=<host>&sz=128`, null when host
  unparseable/blank; card + compact card + preview-sheet header use
  `gatedHttpImageUrl(source.ogImageUrl) ?: source.url.let(::faviconFallbackUrl)`
  — still gated through `gatedHttpImageUrl` so only `http(s)` reaches Coil;
  text-only card only when both are null)
- `app/src/main/java/com/warped/ui/chat/components/SourcePreviewSheet.kt`
  (same one-line fallback at its `gatedHttpImageUrl(source.ogImageUrl)` call
  site — shared helper, no duplication)
- New `app/src/test/java/com/warped/ui/chat/components/FaviconFallbackTest.kt`
  (pure JVM: URL builder table incl. bad-host cases — empty string, non-http
  scheme, unparseable URL, `www.` prefix handling, port/query stripping)

**Notes:**
- Uses the existing Coil singleton (`WarpedApplication` `SingletonImageLoader`)
  and the existing `OgThumb` — no new image pipeline, no new dependency.
- S2 URL itself is `https://`, so it passes the existing gate by construction;
  still route it through `gatedHttpImageUrl` for defense in depth.
- `onError` behavior unchanged: failed favicon load collapses to the text-only
  card silently.

**Verify:**
- `<automated>./gradlew :app:testDebugUnitTest --tests
  "com.warped.ui.chat.components.FaviconFallbackTest"</automated>

**Done:** builder returns the exact S2 URL for valid hosts, null for bad hosts;
cards show a favicon thumb where they previously rendered hollow; text-only card
only when the host itself is unusable.

---

## Phase verification

- `<automated>./gradlew :app:assembleDebug</automated>`
- `<automated>./gradlew :app:testDebugUnitTest</automated>` (full suite green)

## Honest notes

- Live image quality (are Tavily `images[]` relevant?) + grid visuals need an
  on-device screenshot — no adb in this environment, so visual confirmation is
  deferred to the user.
- S2 favicons are best-effort (some hosts have no favicon; S2 returns a default
  glyph) — still strictly better than the current hollow box.

## must_haves

- truths:
  - "Image-intent turn with a stored Tavily key returns image results in the grid"
  - "Image-intent turn without a key still grounds via DDG text with an honest
    images-need-key notice pointing at Settings"
  - "Non-image turns behave exactly as today (DDG-primary policy untouched)"
  - "Source cards with no og:image show a favicon instead of a hollow box"
- artifacts:
  - ChatViewModel image-intent routing gate (or DDG-repo helper)
  - `faviconFallbackUrl` helper in OgSourceCard.kt + call-site adoption
    (card, compact card, preview sheet)
  - Routing-matrix tests + favicon builder tests (incl. bad-host)
- key_links:
  - image-intent+keyed → Tavily `include_images=true` → `fused.images` →
    `groundedImages` → `GroundedImageGrid`
  - null `og:image` → S2 favicon URL → existing Coil `OgThumb` → gated `http(s)`
