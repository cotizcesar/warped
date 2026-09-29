# Phase 58 — UI Review

**Audited:** 2026-09-29
**Baseline:** `58-UI-SPEC.md` (checker sign-off pending, contract treated as approved per brief: 7/7)
**Screenshots:** not captured (native Android project — no web dev server; on-device visual confirmation deferred per plan backstop)

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 4/4 | All CTA/empty/error/a11y strings match contract verbatim |
| 2. Visuals | 4/4 | Card + sheet-header layout, shimmer, badge/icon code-match spec |
| 3. Color | 4/4 | Accent confined to badge + open icon; zero hex literals in UI files |
| 4. Typography | 4/4 | Sizes 12/14/16/20sp, weights 400+600 only; truncation rules exact |
| 5. Spacing | 4/4 | All values multiples of 4; gaps/padding match layout diagram |
| 6. Experience Design | 3/4 | State coverage complete in code; long-text backstop unverified on-device |

**Overall: 23/24**

---

## Top 3 Priority Fixes

1. **On-device long-text backstop unverified (WARNING)** — 200-char title / 500-char description ellipsis hold is code-correct (`maxLines` + `Ellipsis` set) but never visually confirmed; SUMMARY.md explicitly defers it. Human-verify on a compact phone screen before shipping.
2. **UI-SPEC internal inconsistency: card padding 16dp vs 12dp (WARNING)** — Spacing table says "md 16dp → Card internal padding" but the OgSourceCard layout diagram says "padding 12dp"; implementation uses `12.dp` (OgSourceCard.kt:103). Amend the spec table to 12dp (as built, still a multiple of 4) so the contract is self-consistent.
3. **No screenshot evidence archived (advisory)** — native project has no `localhost:` screenshot path; capture an emulator screenshot of a 3+ card Fuentes block (populated + text-only + omitida mix) and attach to the phase for the record.

---

## Detailed Findings

### Pillar 1: Copywriting (4/4)

Every contract string verified against implementation — no generic labels, no placeholder copy, English-only:

- ✅ Open-icon contentDescription `"Open source $number in browser"` (OgSourceCard.kt:165) matches contract `"Open source [N] in browser"`.
- ✅ Card semantics `"Source preview $number: $displayTitle"` + `role = Button` (OgSourceCard.kt:96-99) matches a11y contract.
- ✅ Toasts `"Invalid link."` / `"No browser found to open the link."` (BrowserIntents.kt:31,43) — existing strings reused, none invented.
- ✅ Omitida row `"[${item.number}] ${item.url} — skipped"`, struck, `onSurfaceVariant`, no card (MessageBubble.kt:262-270).
- ✅ Description-absent renders no row (OgSourceCard.kt:128 — `if (description != null)`), title falls back via `ogTitle ?: host` never-empty chain (OgSourceCard.kt:219-232).
- ✅ Zero-ok-sources renders no block (MessageBubble.kt:230 guard `fuenteList.any { it.clickable }`) — Phase 50 behavior preserved.
- ✅ Sheet `"Source $number"` header, `"Open in browser"` button, `EMPTY_EXTRACT_COPY` English (SourcePreviewSheet.kt:70,163,170-171).

### Pillar 2: Visuals (4/4)

- ✅ Clear focal point: 64dp left thumb → title/desc middle (`weight(1f)`) → badge + icon right column (OgSourceCard.kt:101-171).
- ✅ Shimmer-behind-thumb per locked decision: `OgShimmer` pulse via `animateFloat` 0.35→1, 900ms reverse (OgSourceCard.kt:194-207) — no per-card spinner.
- ✅ Icon-only open button carries contentDescription (OgSourceCard.kt:165); thumb is decorative (`contentDescription = null`) with card-level semantics — correct a11y split.
- ✅ Hierarchy via weight/size: title 14sp Semibold vs desc 14sp Regular `onSurfaceVariant`; badge tinted Surface; icon 20dp glyph in 48dp `IconButton` minimum touch target (OgSourceCard.kt:160-169).
- ✅ Sheet OG header above divider: 64dp thumb + 16sp title + badge + 14sp primary URL (SourcePreviewSheet.kt:85-130); text-only collapse when image absent/failed (no placeholder box).
- ⚠️ Visual proof is code-level only; no rendered screenshots exist (native target). No code deviation found, so no deduction — see fix #1/#3.

### Pillar 3: Color (4/4)

- ✅ Coral accent (`primary` #FFD97757) appears on exactly two elements: `[N]` badge (tinted `primary.copy(alpha = 0.15f)` surface + `primary` content, OgSourceCard.kt:147-159; identical construction in sheet, SourcePreviewSheet.kt:106-118) and open-icon glyph tint (OgSourceCard.kt:166). Sheet URL line uses `primary` text per explicitly allowed "existing style" (SourcePreviewSheet.kt:124).
- ✅ Card container locked neutral: `OgCardDark` (#FF2B2B29) dark / `surfaceVariant` light (OgSourceCard.kt:84-88); title/desc neutral `onSurface`/`onSurfaceVariant` — zero coral body text, zero purple.
- ✅ Hex-literal grep gate clean on both UI files (verified: `grep 0xFF` exit 1, zero matches); tokens live in Color.kt (`OgCardDark`, `OgShimmer`, Color.kt:27-28) outside grep scope.
- ✅ Dark default + light variant both handled via `isSystemInDarkTheme()` branch.

### Pillar 4: Typography (4/4)

Distribution check against contract (card scope 12/14/20, +16 sheet title; weights 400+600):

- ✅ Title 14sp Semibold 1-line ellipsis (OgSourceCard.kt:120-127); description 14sp Regular 2-line ellipsis (OgSourceCard.kt:130-137); badge 12sp Semibold (OgSourceCard.kt:152-158).
- ✅ Sheet: `"Source N"` uses `titleLarge` + Semibold — `WarpedTypography.titleLarge` resolves to exactly 20sp/28sp (Type.kt:12) matching the contract's Display row; sheet OG title 16sp Semibold 1-line (SourcePreviewSheet.kt:96-104); URL 14sp 1-line (SourcePreviewSheet.kt:121-127); body 14sp/21sp (SourcePreviewSheet.kt:143-152).
- ✅ `FontWeight` occurrences in card file: exactly SemiBold/Normal/SemiBold — no third weight (Medium token untouched; badge hardcodes SemiBold per contract, correctly overriding `labelMedium`'s Medium 500).
- ✅ Line heights: body 14/20 (1.43–1.5 band), display 20/28 (1.4) — within contract.

### Pillar 5: Spacing (4/4)

- ✅ Thumb-to-text gap 8dp (OgSourceCard.kt:112), text-to-right-column 8dp (OgSourceCard.kt:140), inter-card gap 8dp sm (MessageBubble.kt:273), sheet top 24dp lg + horizontal 16dp md (SourcePreviewSheet.kt:65-66), divider vertical 8dp (SourcePreviewSheet.kt:130), sheet title gap 8dp sm (SourcePreviewSheet.kt:75), title-desc gap 2dp per explicit layout note (OgSourceCard.kt:129).
- ✅ Thumb 64dp square default (OgSourceCard.kt:191) — locked lower bound; corner radii via `shapes.medium` (12dp card) / `shapes.small` (8dp thumb), no hardcoded radius.
- ⚠️ **WARNING (spec-side, not code-side):** spacing table claims card internal padding = 16dp (md) while the layout diagram mandates padding 12dp; code uses `12.dp` following the diagram. All values remain multiples of 4 — no scale violation. Recommend amending spec table (fix #2).

### Pillar 6: Experience Design (3/4)

State-matrix coverage (spec §States) verified in code:

- ✅ Populated: full card / full header via Coil `AsyncImage` on singleton loader.
- ✅ Text-only: thumb slot collapses to 0dp on null URL or `onError → imageFailed` (OgSourceCard.kt:82-83,106; sheet SourcePreviewSheet.kt:84-86) — no error icon, no retry, no error copy (locked "no error state").
- ✅ Loading: shimmer behind thumb, title/desc render immediately from Room state.
- ✅ Omitida: non-clickable struck rows, never reach `OgSourceCard`, never open sheet (`previewForTap` null-guard + `clickable` branch, MessageBubble.kt:239-261).
- ✅ Invalid/missing browser: guarded `ACTION_VIEW` allowlist + dual catch with toasts; sheet dismisses only on launched intent (Boolean return, BrowserIntents.kt:25-54).
- ✅ Sheet dismiss via swipe/back, sticky action row, empty-extract copy path unchanged.
- ⚠️ **WARNING:** long-text overflow backstop (200-char title / 500-char desc) is marked 🧪 in the spec and deferred in SUMMARY.md ("visual confirmation deferred — human-verify"). Truncation props are set correctly, but without on-device proof this pillar cannot score 4. No loading skeleton gap, no missing empty/error path — the single point deducted is verification debt, not implementation debt.

---

## Registry Safety

Not applicable — native Android project, no shadcn registries. New third-party surface is Coil 3.4.0 via Gradle/Maven Central (group/artifact legitimacy documented in 58-02-SUMMARY.md; threat mitigations T-58-05–T-58-08 implemented: bare OkHttp client, render-side http(s) re-gate unit-pinned in `OgSourceCardHelpersTest`, plain-`Text()` rendering, single browser gate). No audit flags.

---

## Files Audited

- `app/src/main/java/com/warped/ui/chat/components/OgSourceCard.kt` (244 lines — card, OgThumb shimmer, `ogDisplayTitle`/`ogHostOf`/`gatedHttpImageUrl` helpers)
- `app/src/main/java/com/warped/ui/chat/components/SourcePreviewSheet.kt` (OG header block lines 76-130; body/action paths unchanged)
- `app/src/main/java/com/warped/ui/chat/components/BrowserIntents.kt` (54 lines — shared browser gate)
- `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt` (Fuentes wiring lines 215-276)
- `app/src/main/java/com/warped/ui/theme/Color.kt` (`OgCardDark`/`OgShimmer` tokens), `Type.kt` (`titleLarge` 20sp verification)
- `.planning/phases/58-opengraph-thumbnails/58-UI-SPEC.md`, `58-01-SUMMARY.md`, `58-02-SUMMARY.md`
