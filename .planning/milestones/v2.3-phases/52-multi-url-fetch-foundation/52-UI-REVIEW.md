# Phase 52 — UI Review

**Audited:** 2026-09-28
**Baseline:** 52-UI-SPEC.md (design contract for multi-URL fetch foundation)
**Screenshots:** not captured (native Android app — no web dev server; code audit of Compose sources)

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 3/4 | Chip + banner copy exact; `omitida` recorded in state but never rendered |
| 2. Visuals | 3/4 | Hierarchy preserved; chip progress not announced (no liveRegion) |
| 3. Color | 4/4 | Accent only on spinner + fuente items; theme colors throughout |
| 4. Typography | 3/4 | Sizes/weights match spec; hardcoded sp values bypass theme line-heights |
| 5. Spacing | 4/4 | All values match scale verbatim (16/8/4dp) |
| 6. Experience Design | 3/4 | State coverage complete; Stop cancel path predates fan-out |

**Overall: 20/24**

---

## Top 3 Priority Fixes

1. **Chip progress never announced to TalkBack** — `ChatScreen.kt:255-262` sets `contentDescription` on the chip Row but no `liveRegion`, so `Leyendo 2 de 4…` updates are silent for screen-reader users — add `liveRegion = LiveRegionMode.Polite` to the chip semantics (same pattern as `JumpToLatestPill`, `ChatScreen.kt:561`).
2. **Stop may not abort all in-flight page fetches** — `stopGeneration()` (`ChatViewModel.kt:581`) and the prior-turn cancel (`ChatViewModel.kt:258`) call only `fetcher.cancel()` (single shared `Call` handle, `WebPageFetcher.kt:77`), but Phase 52 fans out N parallel `fetcher.fetch()` calls via `MultiUrlFetcher.fetchAll`; cancellation currently relies on `generationJob.cancel()` propagating into the `coroutineScope`. Verify `WebPageFetcher.fetch` is cooperative with coroutine cancellation or expose a fan-out-aware cancel — otherwise Stop unmounts the chip while fetches continue in the background.
3. **Partial-grounding drops are invisible to the user** — dead links are recorded as `OMITIDA` in `webFetchProgress.perSource` (`ChatViewModel.kt:326-342`) but the chip is counts-only and `Fuentes` renders ok sources alone (`MessageBubble.kt:206-222`); no pixel ever shows `omitida`. Contract-compliant for Phase 52 (counts-only chip per UI-SPEC), but either render a per-source `omitida` row or explicitly defer to the Phase 53 bottom sheet — currently the Copywriting Contract's "`omitida` — surfaced in progress state" reads as user copy with no surface.

---

## Detailed Findings

### Pillar 1: Copywriting (3/4) — WARNING

Verified against UI-SPEC `## Copywriting Contract`, all Spanish:

- ✅ Fetch progress chip multi: `"Leyendo ${progress.done} de ${progress.total}…"` (`ChatScreen.kt:245`) — exact match to `Leyendo N de M…`.
- ✅ Single-URL legacy: `"Leyendo página…"` (`ChatScreen.kt:247`) — unchanged as specified.
- ✅ Chip a11y: `"Leyendo ${done} de ${total} páginas. Pulsa Detener para cancelar la lectura."` / legacy single variant (`ChatScreen.kt:249-254`) — exact match to contract.
- ✅ Fuentes heading `"Fuentes"` (`MessageBubble.kt:209`) — unchanged.
- ✅ Fuentes items `"[${i + 1}] $url"` (`MessageBubble.kt:217`) — numbered `[N] url` in ok-order.
- ✅ All-fail offline banner unchanged (`MessageBubble.kt:260`).
- ✅ All-fail fetch banner pluralizes on `totalSources > 1` (`MessageBubble.kt:261-268`) — both singular and plural strings match the contract verbatim, em-dash included.
- ⚠️ **WARNING — `omitida` has no rendered surface.** Contract lists per-source omitted state copy `omitida` (FETCH-02, "surfaced in progress state"). Implementation records `PerSourceStatus.OMITIDA` into `webFetchProgress.perSource` (`ChatViewModel.kt:334`, `ChatUiState.kt:77-87`) and immediately clears it in the enclosing `finally` (`ChatViewModel.kt:352-354`), published-then-cleared in the same turn. `grep omitida` across `ui/chat` hits only `ChatViewModel.kt`/`ChatUiState.kt` comments and enum — zero hits in any `@Composable`. The "no silent drops" guarantee holds at the data layer only; the user sees ok sources with no trace of skipped ones. Acceptable only if Phase 53 bottom sheet is the intended surface — say so explicitly.

### Pillar 2: Visuals (3/4) — WARNING

- ✅ No new screens — scope respected. Only the chip copy branch and banner pluralization extend existing surfaces (`ChatScreen.kt:241-277`, `MessageBubble.kt:248-274`).
- ✅ Clear focal point: spinner + chip text above the input bar during fetch; `Fuentes` heading hierarchy (12sp semibold muted → 14sp primary items) preserved for N items.
- ✅ Banner keeps Info icon + text row, 4dp above the message (`MessageBubble.kt:94-97`).
- ✅ Long URLs wrap (no `maxLines`/`ellipsis` on fuente items, `MessageBubble.kt:216-220`) — matches "wrap, never truncate".
- ✅ Icon-only treatment sound: banner `Info` icon `contentDescription = null` (decorative, adjacent text carries meaning); chip `CircularProgressIndicator` has no competing description.
- ⚠️ **WARNING — chip not announced.** Chip Row semantics (`ChatScreen.kt:259-261`) set `contentDescription` without `liveRegion`, so count updates (`0 de 5` → `4 de 5`) never reach TalkBack. Precedent in the same file (`JumpToLatestPill`, `:561`) uses `LiveRegionMode.Polite`. One-line fix.
- ℹ️ Fuentes items are non-interactive plain `Text` (no `clickable`, no link annotation). Correct per scope (tap preview is Phase 53), not a defect.

### Pillar 3: Color (4/4)

- ✅ Chip spinner `MaterialTheme.colorScheme.primary` (`ChatScreen.kt:267`) — declared accent use.
- ✅ Fuente items `MaterialTheme.colorScheme.primary` (`MessageBubble.kt:219`) — declared accent use, and the ONLY body-adjacent accent.
- ✅ Chip text `onSurface` (`ChatScreen.kt:273`), Fuentes heading `onSurfaceVariant` (`MessageBubble.kt:212`), banner text `onSurfaceVariant` (`MessageBubble.kt:271`), banner icon `onSurfaceVariant` (`MessageBubble.kt:253`) — accent never leaks into body text or banner copy.
- ✅ No hardcoded colors in either Phase 52 surface; pre-existing hardcoded hex elsewhere in the files (fades `:398-414`, pill `:566`, traffic lights `:609-616`) is out of scope and untouched.
- ✅ Destructive `errorContainer`/`onErrorContainer` untouched — model-only banner correctly stays informational.

### Pillar 4: Typography (3/4) — WARNING

Spec: Body 14sp regular/1.5 (`onSurface` chip, `primary` items), Label 12sp semibold/1.2 (`onSurfaceVariant` heading).

| Element | Built | Spec | Match |
|---------|-------|------|-------|
| Chip text | `fontSize = 14.sp`, default weight | Body 14sp regular | ✅ size/weight |
| Fuentes heading | `12.sp` + `SemiBold` | Label 12sp semibold | ✅ |
| Fuente items | `14.sp`, default weight, primary | Body 14sp | ✅ |
| Banner text | `14.sp`, default weight | Body 14sp (inherited) | ✅ |

- ⚠️ **WARNING (minor, pre-existing pattern) — hardcoded `fontSize`/`fontWeight` bypass the theme type scale**, so the spec's line heights (1.5 body / 1.2 label) are not applied. `ChatScreen.kt:270-274`, `MessageBubble.kt:208-220,257-272` use raw `14.sp`/`12.sp` instead of `MaterialTheme.typography.bodyMedium`/`labelSmall`. Matches the v2.2 precedent (not a Phase 52 regression), but the contract cites theme roles — a theme-style pass would lock line height. Only 2 sizes (12/14sp) + 1 weight (SemiBold) in scope — no scale explosion.
- ✅ No new type roles introduced.

### Pillar 5: Spacing (4/4)

Spec scale: xs 4px / sm 8px / md 16px, reused verbatim.

- ✅ Chip horizontal padding `16.dp` (`ChatScreen.kt:258`) — md.
- ✅ Spinner-to-text gap `8.dp` (`ChatScreen.kt:269`) — sm.
- ✅ Chip-to-input gap `8.dp` (`ChatScreen.kt:276`) — sm.
- ✅ Banner-to-message `4.dp` (`MessageBubble.kt:96`) — xs.
- ✅ Fuentes-to-bubble `4.dp` (`MessageBubble.kt:207`) — xs.
- ✅ Fuentes item gaps `4.dp` (`MessageBubble.kt:215`) — xs.
- ✅ No arbitrary dp values in either extended surface; no new tokens.

### Pillar 6: Experience Design (3/4) — WARNING

State coverage vs UI-SPEC `## UI Considerations`:

- ✅ Loading: chip gated on `input.isFetchingWeb`, transient, never a transcript message; unmounts on completion/failure (`finally` clears, `:352-354`) and Stop (`:596`). Single cancel path (existing Stop) retained.
- ✅ Partial: `modelOnlyNotice` set ONLY in the `AllFailed` branch (`ChatViewModel.kt:344-350`); partial grounding grounds ok pages, skips dead links, renders no banner. Worst-case collapse (OFFLINE wins) resolved upstream in `MultiUrlFetcher` (`:88-94`).
- ✅ Zero/one/many: `groundedSources.isNotEmpty()` guard (`MessageBubble.kt:206`) — zero renders no block; one item renders legacy single row; N items numbered. `totalSources` defaults to 1 (`ChatMessage.kt:21`) so legacy single-fail copy is safe.
- ✅ Overflow/cap: `distinct().take(MAX_URLS)` with `MAX_URLS = 5` (`MultiUrlFetcher.kt:59,110`); `UrlDetector.allUrls` defaults to the same cap — single source of truth, 6th+ ignored deterministically, never enters state.
- ✅ Order: `awaitAll` preserves `targets` order; `okUrls` built in paste order (`MultiUrlFetcher.kt:75-99`) — Fuentes order == `[WEB CONTEXT 1..N]` block order (both derived from the same `okPages` list).
- ⚠️ **WARNING — fan-out cancel path.** `stopGeneration` and prior-turn cancel call `fetcher.cancel()` only. Under parallel fan-out, N `fetch()` calls share one `WebPageFetcher` instance; a single-`Call` cancel handle may abort only the latest call. Structured-concurrency cancellation (`generationJob.cancel()` → `coroutineScope` in `fetchAll`) is the real backstop — but only if `WebPageFetcher.fetch` checks coroutine cancellation. Unverified here; if `fetch` blocks on a raw OkHttp `execute()` without cooperative cancellation, Stop leaves orphan fetches running after the chip unmounts.
- ℹ️ Chip initializes at `done = 0` (`ChatViewModel.kt:289`) so the first frame reads `Leyendo 0 de M…` before the first completion callback. Trivial; no fix required (alternatively seed `done = 1` on first callback only).
- ℹ️ Backstop item (long-URL wrapping visual in both themes, on device) remains held out — same WEB-06 smoke precedent as spec; cannot verify code-only.

---

## Files Audited

- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` (chip `Leyendo N de M…`, `:241-277`)
- `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt` (Fuentes list `:206-222`, banner `:247-274`)
- `app/src/main/java/com/warped/ui/chat/ChatUiState.kt` (`WebFetchProgress`/`SourceFetchState`/`PerSourceStatus`, `:42-94`)
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` (fan-out wiring `:272-356`, Stop `:576-597`)
- `app/src/main/java/com/warped/data/grounding/MultiUrlFetcher.kt` (cap, order, worst-case collapse)
- `app/src/main/java/com/warped/domain/model/ChatMessage.kt` (`groundedSources`, `modelOnlyNotice`, `modelOnlySourceCount`)
- Baseline: `.planning/phases/52-multi-url-fetch-foundation/52-UI-SPEC.md`
