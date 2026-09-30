# Quick Task: Loop web_search image plumbing

## Cause (locked, device-evidence)

Image-intent question answered via the agentic loop returns links with zero
grid. Two compounding defects:

1. All four loop `web_search` executors call `search()` without
   `includeImages`, so the Tavily fallback leg always sends
   `include_images=false` (pre-search path only sets it).
2. Even if images were fetched, they cannot reach the grid:
   `ToolCallOutcome` carries only `text` + `sources`,
   `StreamToken.ToolCompleted` carries only `sources`, and the VM only sets
   `groundedImages` from the pre-search branch (`fused.images`, ChatViewModel
   ~L608). Loop turns merge `loopSourceDetails` but never touch
   `groundedImages`.

## Fix (locked)

1. All loop `web_search` execution sites pass `includeImages = true` ALWAYS.
   Cost analysis: the DDG leg ignores it (no image API); only the Tavily
   fallback leg uses it, which requires a stored key and stays credit-capped.
   Keyless turns behave byte-identically. No intent detection in the loop.
2. Thread `outcome.images` → `ToolCompleted.images` → VM `groundedImages`
   (ephemeral field the grid reads). Stay ephemeral: `groundedImages` is NOT
   persisted today (`MessageEntity.images` is user-attached input URIs;
   `toEntity` never writes `groundedImages`) — no DB/mapper/migration change.
3. Tests per site + mapping + grid-render coverage.

Out of scope: intent detection changes, modal/download, OG, budgets,
pre-search policy.

## Execution sites (verified in code 2026-09-29)

| # | File | Line | Current call |
|---|------|------|--------------|
| 1 | `data/local/inference/LiteRTLmProvider.kt` | ~511 | `ddg.search(query, maxResults, contextSize)` — no `includeImages` |
| 2 | `data/remote/provider/CompatToolLoop.kt` | ~249 | `ddg.search(query, maxResults, contextSize)` — no `includeImages` |
| 3 | `data/remote/provider/OpenAIProvider.kt` | ~362 | `repo.search(query, maxResults, contextSize)` — no `includeImages` |
| 4 | `data/remote/provider/AnthropicProvider.kt` | ~376 | `repo.search(query, maxResults, contextSize)` — no `includeImages` |

`ToolCompleted` emission sites (need `images = outcome.images`):
LiteRTLmProvider ~463, CompatToolLoop ~176, OpenAIProvider ~290,
AnthropicProvider ~318. VM accumulation: ChatViewModel ~L847-854; Done merge
~L889-906 (`groundedImages = groundedImages` — loop images never merged).

<tasks>

<task type="auto">
  <name>Task 1: includeImages=true at all loop web_search sites</name>
  <files>app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt, app/src/main/java/com/warped/data/remote/provider/CompatToolLoop.kt, app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt, app/src/main/java/com/warped/data/remote/provider/AnthropicProvider.kt</files>
  <action>Add `includeImages = true` to the `ddg`/`repo.search()` call in each file's web_search executor branch (sites 1-4 above). Use the explicit-named-arg style already present (no Kotlin defaults, keeps MockK stubbing on the instance method). Do NOT touch validateArgs, offline gates, mapSearchOutcome, web_fetch branches, or the pre-search path. Rationale in comment: DDG leg ignores it; only the keyed Tavily fallback leg uses it, credit-capped.</action>
  <verify>./gradlew :app:assembleDebug; grep each file for `includeImages = true` (4 hits)</verify>
  <done>All 4 executors pass includeImages=true; keyless behavior byte-identical (DDG leg + MissingKey path untouched)</done>
</task>

<task type="auto">
  <name>Task 2: Thread outcome.images to ChatMessage.groundedImages</name>
  <files>app/src/main/java/com/warped/data/agentic/LocalToolLoop.kt, app/src/main/java/com/warped/domain/model/StreamToken.kt, app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt, app/src/main/java/com/warped/data/remote/provider/CompatToolLoop.kt, app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt, app/src/main/java/com/warped/data/remote/provider/AnthropicProvider.kt, app/src/main/java/com/warped/ui/chat/ChatViewModel.kt</files>
  <action>
  1. LocalToolLoop: add `images: List&lt;String&gt; = emptyList()` to `ToolCallOutcome` with a KDoc noting it carries the fused Tavily `images[]` URLs verbatim (http(s)-gated upstream); add `fun searchImages(outcome: TavilySearchOutcome): List&lt;String&gt; = (outcome as? TavilySearchOutcome.Grounded)?.fused?.images.orEmpty()` next to `searchSources`.
  2. Each of the 4 executors: populate `images = LocalToolLoop.searchImages(outcome)` in the web_search `ToolCallOutcome(...)` (fetch branches stay imageless).
  3. StreamToken.ToolCompleted: add `images: List&lt;String&gt; = emptyList()`; pass `images = outcome.images` at the 4 emission sites.
  4. ChatViewModel: add a per-turn `loopImages` accumulator next to `loopSourceDetails` (union, distinct, first-seen order); extend the `is StreamToken.ToolCompleted` branch (~L847) to accumulate `token.images`; on Done, set `groundedImages = (groundedImages + loopImages).distinct()` so pre-search images keep precedence. Do NOT persist: no EntityMappers/toEntity/MessageEntity change, no migration.
  </action>
  <verify>./gradlew :app:assembleDebug; grep `searchImages` hits LocalToolLoop + 4 executors; grep `loopImages` hits ChatViewModel</verify>
  <done>Loop-sourced images reach ChatMessage.groundedImages on loop turns; pre-search turns unchanged; nothing persisted</done>
</task>

<task type="auto">
  <name>Task 3: Tests + full green suite</name>
  <files>app/src/test/java/com/warped/data/local/inference/LiteRTLmLoopTest.kt, app/src/test/java/com/warped/data/remote/provider/CompatToolLoopSourcesTest.kt, app/src/test/java/com/warped/ui/chat/ChatLoopSourcesTest.kt, app/src/test/java/com/warped/ui/chat/ChatAlwaysSearchTest.kt (or nearest new/existing VM test file)</files>
  <action>
  1. Per executor site: assert `search(..., includeImages = true)` — extend existing MockK verifications (follow the ChatAlwaysSearchTest `ddgSearchRepository.search(any(), any(), any(), includeImages = ...)` pattern) for local, compat, OpenAI, and Anthropic loops.
  2. Mapping: ToolCallOutcome with images → ToolCompleted carries images (sources-only outcomes stay empty).
  3. VM: loop turn whose ToolCompleted carries images produces an assistant message with `groundedImages` non-empty; grid-render condition (`MessageBubble` `groundedImages.isNotEmpty()` → `GroundedImageGrid`) covered — add test only if missing.
  4. Run assembleDebug + FULL testDebugUnitTest green.
  </action>
  <verify>./gradlew :app:assembleDebug :app:testDebugUnitTest</verify>
  <done>New assertions green; full unit suite green; no regressions in pre-search image tests</done>
</task>

</tasks>

## Honest notes

- Live grid on an image-intent question via the loop needs on-device
  confirmation (no adb in this environment): keyed device, ask e.g. "show me
  pictures of X", expect the grid under the answer.
- Keyless loop turns: no grid (Tavily MissingKey path) — same as pre-search;
  the IMAGES_NEED_KEY notice is pre-search-only and unchanged.
- Extra Tavily payload (`include_images=true`) on every keyed loop search is
  the accepted cost; credit caps already bound it.
