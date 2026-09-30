# SUMMARY — Always-on pre-search + image grid

**Status:** complete — Task A1 + Task B1 executed, assemble + full unit suite green.
**Date:** 2026-09-30. **Commits:** `778089c2` (A1), `f0751722` (B1), `3206787a` (Rule-2 hardening).

## What changed

**Workstream A — the model now always searches.** `ChatViewModel.sendMessage`
no-URL branch no longer skips the DDG-primary pre-search on armed turns
(the `if (loopArmed) { augment(null) }` skip is gone, with the whole
VM-side arming mirror: `localArmed`/`remoteArmed` computation,
`isFunctionCallingCapable`, and the `LocalToolLoop`/`ToolCapabilityMatrix`
imports). DDG is free/keyless so armed turns add 0 credits; the loop stays
armed provider-side (`computeArmSnapshot` / `ConversationConfig.tools`
untouched, `LocalToolLoop.MAX_TOOL_CALLS` untouched) for deeper
model-driven fetch, and loop `ToolCompleted` rows keep merging with
pre-search details in the Done union. No VM-level direct Tavily call —
Tavily fires only inside `DuckDuckGoSearchRepository.search` when DDG
yields nothing usable AND a key is stored. `GroundingPrompt.SYSTEM_PROMPT`
no-URL sentence reworded: "If you need fresh information and there is no
context block, ask the user to paste a link." → "Answer with the provided
sources; call web_search if you need more." ("Never invent URLs" and the
same-language rule byte-identical). The retained `modelAllowlistRepository`
field is now unused by the VM (provider owns arming) — kept with KDoc so
Hilt + all 6 test construction sites stay untouched.

**Workstream B — image search grid + modal + gallery download.**
`ImageIntent` (new, pure Kotlin) detects image intent via ES+EN word list
(imagen/imágenes/foto/fotos/fotografía/muéstrame/enséñame/ver/dibujo/
image/images/picture/pictures/photo/photos/diagram + "show me"/"picture
of"/"image of" phrases) with whole-token matching, so "imaginación" never
fires. On intent the VM pre-search passes `include_images=true`
(`TavilySearchRequest.include_images`, default false — non-intent turns
byte-identical, no extra payload); `TavilySearchRepository` fuses the
response `images[]` (`TavilyImageResult(url, description)`) into the new
ephemeral `Fused.images` list with http(s)-gating, distinct, cap 10.
`DuckDuckGoSearchRepository.search` takes `includeImages` as pass-through
to the Tavily fallback leg only — documented in KDoc that DDG-served
turns fuse zero images (DDG HTML has no image API). The VM carries
`fused.images` into the new ephemeral `ChatMessage.groundedImages` (never
a Room column). `GroundedImageGrid` (new) renders chunked 3-column Coil
`AsyncImage` thumbs below the Fuentes carousel (no nested-scroll clash),
tap → `Dialog` full-view modal with DOWNLOAD; `ImageSaver` (new) fetches
via bare OkHttp on IO (ModelDownloadWorker precedent) with a 20MB bound
and writes to MediaStore `Images/Media` (`Pictures/Warped` on Q+).
English copy (`images_title`, `image_download`, `image_close`,
`image_saved`, `image_save_failed`) + ES parity (`Imágenes`,
`Descargar`, `Cerrar`, `Guardada en la galería`, …). Zero new colors
(M3 only, zero purple).

## Test results

- `./gradlew :app:assembleDebug` — green.
- `./gradlew :app:testDebugUnitTest` (FULL suite) — green: **576 tests,
  0 failures, 0 errors, 0 skipped** (fresh run, all XMLs current).
- New/updated coverage: `ChatAlwaysSearchTest` (6 — armed+online runs,
  unarmed+online runs, offline OFFLINE + no socket, DDG-fail FETCH_FAILED,
  include_images false/true carries incl. fused-images→message);
  `ImageIntentTest` (31 — ES+EN table + "imaginación" negative);
  `GroundedImagesTest` (3 — gate/distinct/cap); `TavilySearchRepositoryTest`
  (19 — flag defaults false, true threads + gated fuse, cap);
  `DuckDuckGoSearchRepositoryTest` (18 — flag threads to delegate only,
  DDG-served turn fuses zero images); `GroundingPromptTest` (10 — "paste
  a link" gone, sources/web_search wording pinned, old pins intact);
  `ChatLoopSourcesTest` (4 — updated to always-on union semantics,
  pre-search stubbed model-only to isolate loop rows);
  `StringResourceParityTest` — green; `ChatGroundingToggleTest`,
  `ChatGroundingRetryTest`, `ChatCancellationTest`, `SettingsTavilyTest`
  — green unchanged.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing critical functionality] Bounded ImageSaver reads**
- **Found during:** Task B1 (post-commit review of new code)
- **Issue:** `ImageSaver` used `body.bytes()` (unbounded) — a hostile
  image host could OOM the app with a multi-GB stream before MediaStore.
- **Fix:** bounded `BufferedSource` read capped at 20MB
  (`MAX_IMAGE_BYTES`); over-cap throws → `SaveResult.Failed` copy.
- **Files modified:** `data/grounding/ImageSaver.kt`
- **Commit:** `3206787a`

Or else: none — plan executed as written (fallback matrix untouched,
prompt surgery minimal, no Room migration, no new providers/keys).

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: outbound-fetch | `data/grounding/ImageSaver.kt` | New fetch surface: arbitrary http(s) image URLs from search results downloaded via bare OkHttp (no auth headers leak; http(s)-gated; 20MB-bounded; IO dispatcher). |
| threat_flag: mediastore-write | `data/grounding/ImageSaver.kt` | New MediaStore `Images/Media` insert path (Q+ `RELATIVE_PATH Pictures/Warped`, `IS_PENDING` protocol; pre-Q plain insert). |

## Known Stubs

None — every new field is wired end-to-end (intent → flag → fuse →
message → grid → modal → MediaStore). `groundedImages` is empty by
design on non-intent turns (no extra payload), not a stub.

## On-device confirmation needed (no adb in this env)

- DDG/Tavily live result + image-URL quality with network.
- Grid visuals (thumb sizing, density, dark theme) + modal layout.
- Gallery download (MediaStore write visibility, permission UX on
  targetSdk 35, pre-Q path).
- Multi-turn freshness with always-on pre-search (stale-block behavior).

## Self-Check: PASSED

- Created files exist: `ImageIntent.kt`, `GroundedImages.kt`,
  `ImageSaver.kt`, `GroundedImageGrid.kt`, `ImageIntentTest.kt`,
  `GroundedImagesTest.kt`, `ChatAlwaysSearchTest.kt` — all FOUND.
- Commits exist: `778089c2`, `f0751722`, `3206787a` — all FOUND
  (`git log --oneline`).
- No unintended deletions (`git diff --diff-filter=D` clean for the
  wave).
