# Quick-task plan — 20260929 always-search + image grid

Two workstreams: (A) root-cause fix for "model never searches" on armed turns,
(B) new image search grid + modal + gallery download.

## Verified diagnoses (code-grounded 2026-09-29)

- BUG A hook: `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
  ~558-578 — `if (loopArmed) { augment(null) }` skips the DDG-primary
  pre-search on armed turns, trusting the ~2B on-device model to call
  `web_search`. On-device evidence: it usually doesn't.
- BUG A prompt conflict: `data/grounding/GroundingPrompt.kt` `SYSTEM_PROMPT`
  (~lines 12-20) says "If you need fresh information and there is no context
  block, ask the user to paste a link" — this fires on EVERY grounded no-URL
  turn including armed ones, and directly competes with
  `LiteRTLmProvider.TOOL_USE_SYSTEM_HINT` ("Use web_search when the question
  needs current or external facts"). The tiny model obeys the former
  (asks for sources/links) instead of calling tools. Both strings verified
  in code.
- Loop/wallet bounds: `data/agentic/LocalToolLoop.kt` `MAX_TOOL_CALLS = 5`,
  `web_search` is DDG-primary (keyless, 0 credits); only the Tavily fallback
  leg burns 1 credit → worst case 5 credits/message. VM pre-search goes
  through `DuckDuckGoSearchRepository.search` (DDG-primary + internal Tavily
  fallback only when DDG fails AND key present) — so running it on armed
  turns adds 0 credits when DDG succeeds. No wallet stacking by construction.
- FEATURE B gaps verified: `TavilySearchRequest` (`data/remote/dto/TavilyDtos.kt`
  ~18-24) has NO `include_images` field — new. `GroundedSource`
  (`domain/model/GroundedSource.kt`) already carries `ogImageUrl` (http(s)-gated
  via `OpenGraphParser.resolveHttpUrl`, never fetched at scrape time) — reuse
  this slot/pattern for Tavily image URLs. Coil singleton exists
  (`WarpedApplication.newImageLoader`, bare OkHttp, disk cache
  `cacheDir/og_thumbnails`) — reuse for grid thumbs. Attach point:
  `MessageBubble.kt` ~237-294 Fuentes carousel (LazyRow + preview sheet).
  `ModelDownloadWorker` downloads via OkHttp → `filesDir/models` (app-private,
  checkpointed) — NOT a gallery path; no MediaStore usage exists in the repo
  (grep confirmed), so gallery save is new code following the worker's OkHttp
  + coroutine precedent, not its destination.

## Workstream A — deterministic always-on pre-search + prompt surgery

### Task A1: Always-on DDG pre-search on armed turns + SYSTEM_PROMPT reword

<files>
`app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
`app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt`
`app/src/test/java/com/warped/data/grounding/GroundingPromptTest.kt` (extend)
`app/src/test/java/com/warped/ui/chat/ChatGroundingToggleTest.kt` (extend, or new
`ChatAlwaysSearchTest.kt` if the matrix doesn't fit the existing file)
</files>

<action>
Per locked diagnosis BUG A. In `ChatViewModel.sendMessage` no-URL branch:
remove the `if (loopArmed) { augment(null) }` skip — run the DDG-primary
branch (`ddgSearchRepository.search` → `GroundingPrompt.augment` →
okUrls/details/progress fusion) on EVERY grounded no-URL turn INCLUDING armed
ones. DDG is free/keyless so no wallet stacking. The loop stays armed (provider
`computeArmSnapshot` / `ConversationConfig.tools` untouched) for deeper
model-driven fetch; loop `ToolCompleted` rows keep merging with pre-search
details in the existing Done union (first-seen order, distinct by URL).
Fallback matrix (LOCKED, preserves current wallet bounds): Tavily fallback
fires ONLY inside `DuckDuckGoSearchRepository.search` when DDG yields nothing
usable AND a key is stored — do NOT add any VM-level direct Tavily call; do NOT
touch `LocalToolLoop.MAX_TOOL_CALLS`; loop `web_search` stays DDG-primary.
Prompt surgery (minimal): reword the no-URL `SYSTEM_PROMPT` sentence "If you
need fresh information and there is no context block, ask the user to paste a
link." → "Answer with the provided sources; call web_search if you need more."
Decide exact wording/scope in code: unconditional-but-harmless is preferred
(it is also correct for unarmed turns — the fused block IS the provided
sources — and avoids a VM→prompt armed-likelihood signal); verify whether the
VM knows armed-likelihood cheaply (it already computes `loopArmed` at ~572)
only if a conditional variant is needed. Keep "Never invent URLs" and the
same-language rule byte-identical. Pin wording with tests.
</action>

<verify>
<automated>./gradlew :app:testDebugUnitTest --tests "com.warped.data.grounding.GroundingPromptTest" --tests "com.warped.ui.chat.ChatGroundingToggleTest"</automated>
</verify>

<done>
Armed no-URL turns produce a `ddgSearchRepository.search` call (matrix test:
armed+online → search runs; unarmed+online → search runs unchanged;
offline → OFFLINE notice, no socket; DDG-fail+key → Tavily leg;
DDG-fail+nokey → FETCH_FAILED); SYSTEM_PROMPT no longer contains "paste a
link"; loop arming + MAX_TOOL_CALLS untouched.
</done>

### must_haves (A)
- truths:
  - "A grounded question with no pasted URL on an armed turn returns web sources, not a 'paste a link' reply"
  - "Tavily credit burn per message is unchanged from current bounds"
  - "The model prompt tells tool-capable turns to use provided sources / call web_search, never to ask for links"
- artifacts:
  - ChatViewModel.kt no-URL branch runs ddgSearchRepository.search regardless of loopArmed
  - GroundingPrompt.kt SYSTEM_PROMPT reworded, pinned by GroundingPromptTest
  - Pre-search-always matrix tests incl. armed-turns (new or extended test file)
- key_links:
  - VM pre-search → DuckDuckGoSearchRepository.search (DDG-primary, internal fallback only)
  - augment(block) → requestUserText → Done union with loop ToolCompleted rows

## Workstream B — image search grid + modal + gallery download (NEW)

### Task B1: Intent-gated Tavily include_images → image grid + modal + download

<files>
`app/src/main/java/com/warped/data/remote/dto/TavilyDtos.kt` (add `include_images`
  + image result fields — check Tavily API shape: `images[]` on response)
`app/src/main/java/com/warped/data/grounding/TavilySearchRepository.kt` (thread
  include_images flag + fuse image URLs)
`app/src/main/java/com/warped/data/grounding/DuckDuckGoSearchRepository.kt`
  (pass-through or DDG-side no-op — planner decides; DDG HTML endpoint has no
  image API, so images come from the Tavily leg only — document this)
`app/src/main/java/com/warped/data/grounding/ImageIntent.kt` (NEW, pure Kotlin,
  JVM-testable keyword detector ES+EN)
`app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` (intent gate →
  include_images=true on the pre-search call; carry image URLs to the message)
`app/src/main/java/com/warped/domain/model/ChatMessage.kt` and/or
  `GroundedSource.kt` (NEW ephemeral `groundedImages: List<ImageResult>`-style
  field — ephemeral like groundedSources, never Room columns; planner decides
  exact home)
`app/src/main/java/com/warped/ui/chat/components/GroundedImageGrid.kt` (NEW:
  LazyVerticalGrid-ish Coil thumbs attached to the grounded answer in
  MessageBubble; tap → Dialog modal full view + DOWNLOAD button)
`app/src/main/java/com/warped/data/grounding/ImageSaver.kt` (NEW: OkHttp
  download → MediaStore on Q+, follow ModelDownloadWorker's OkHttp+coroutine
  precedent for the fetch, MediaStore for the destination)
`app/src/main/res/values/strings.xml` + `values-es/` (English copy + ES parity
  per StringResourceParityTest)
`app/src/test/java/com/warped/data/grounding/ImageIntentTest.kt` (NEW table test)
`app/src/test/java/com/warped/data/grounding/TavilySearchRepositoryTest.kt`
  (extend: include_images flag + image fuse)
</files>

<action>
Per locked spec FEATURE B. Intent gating (RECOMMENDED: intent-gated, not
always-on — saves payload/latency): new pure-Kotlin `ImageIntent` detector
returns true when the query contains an image-intent keyword. Word list must
cover ES+EN: "imagen, imágenes, foto, fotos, fotografía, muéstrame, enséñame,
ver, dibujo, image, images, picture, pictures, photo, photos, show me, picture
of, image of, diagram". Case-insensitive substring match on the trimmed query;
JVM table test pins every word + negatives ("imaginación" must NOT match —
use word-boundary or token matching, planner decides exact mechanism).
When intent fires on a grounded no-URL turn, the pre-search call sets
`include_images=true` (Tavily leg only — DDG HTML has no image API; when the
DDG leg serves the turn alone, images come only from a Tavily fallback hit —
document this honestly in KDoc). Fuse: image URLs from the Tavily response
into the new ephemeral message field, gated to http(s) exactly like OG
(`OpenGraphParser.resolveHttpUrl` precedent — data:/javascript: → drop).
Render: image GRID (LazyVerticalGrid-ish, Coil `AsyncImage` thumbs reusing the
`WarpedApplication` singleton — never the authed client) attached to the
grounded answer below the Fuentes carousel in `MessageBubble`; tap image →
modal (`Dialog`) full view with DOWNLOAD button; download saves to gallery via
the new `ImageSaver` (OkHttp fetch on IO following ModelDownloadWorker's
precedent → MediaStore `Images/Media` on Q+; pre-Q fallback only if trivial).
English copy for grid/modal/download states + ES parity strings (parity test
must stay green). JVM-testable logic (intent table, include_images flag
threading, http(s) gating, fuse mapping) gets unit tests; grid/modal/download
are Compose/Android-framework code — cover what is JVM-testable (state mapping
helpers as pure functions where cheap) and mark the rest for on-device
confirmation.
</action>

<verify>
<automated>./gradlew :app:testDebugUnitTest --tests "com.warped.data.grounding.ImageIntentTest" --tests "com.warped.data.grounding.TavilySearchRepositoryTest" --tests "com.warped.i18n.StringResourceParityTest"</automated>
</verify>

<done>
Image-intent queries (ES+EN table) set include_images and render a Coil image
grid with modal + download; non-intent turns are byte-identical to today
(no extra payload); image URLs http(s)-gated; parity test green.
</done>

### must_haves (B)
- truths:
  - "Asking 'muéstrame fotos de…' / 'show me pictures of…' returns an image grid with the grounded answer"
  - "Tapping a grid image opens a full-view modal with a working DOWNLOAD button"
  - "DOWNLOAD saves the image to the gallery (MediaStore)"
  - "Non-image questions behave exactly as today (no extra search payload)"
- artifacts:
  - ImageIntent.kt detector + ImageIntentTest table (ES+EN incl. "imaginación" negative)
  - TavilySearchRequest.include_images threaded from the VM intent gate
  - GroundedImageGrid.kt (grid + Dialog modal + download) attached in MessageBubble
  - ImageSaver.kt (OkHttp → MediaStore) following ModelDownloadWorker precedent
- key_links:
  - VM intent gate → Tavily include_images → ephemeral message image field → grid
  - Coil singleton (bare OkHttp) → grid thumbs (never the authed client)

## Phase verification
- `./gradlew :app:assembleDebug` green
- `./gradlew :app:testDebugUnitTest` FULL suite green (no `--tests` filter)

## Honest notes (on-device confirmation needed — no adb in this env)
- DDG/Tavily live result quality (markup stability, image URL quality) needs
  on-device confirmation with network.
- Grid visuals (thumb sizing, grid density, dark-theme) need visual check.
- Gallery download (MediaStore write, gallery visibility, permission UX on
  targetSdk 35) needs on-device confirmation.
- Multi-turn freshness with always-on pre-search (stale-block behavior) needs
  on-device conversation testing.

## Out of scope (explicitly NOT in these tasks)
New search providers, OpenGraph changes, engine mechanics, loop-cap changes,
Room migrations (all new message fields ephemeral), new API keys/vendors.
