# Phase 70 (Document Reader Tool) — UI Review

**Audited:** 2026-10-02
**Baseline:** `.planning/phases/70-document-reader-tool/70-UI-SPEC.md` (draft, checker sign-off pending)
**Screenshots:** not captured — native Android project, no web dev server (code-only audit)
**Scope audited:** attach button + attachment chip/preview (`ChatInputBar.kt`), picker wiring + notices (`ChatScreen.kt`, `ChatViewModel.kt`), document source card rows (`OgSourceCard.kt` `CompactSourceCard`, `MessageBubble.kt` Fuentes, `SourcePreviewSheet.kt`), `doc_reader_*` strings EN+ES.

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 3/4 | All 5 contracted strings exact + EN/ES parity; source-card truncation metadata hardcoded in English, not resourced |
| 2. Visuals | 2/4 | Chip + button match spec; raw `doc:` prefix leaks into card URL line and preview-sheet header |
| 3. Color | 3/4 | Chip/button/card tints per contract; doc-row URL renders in primary (link) color though it is not a link |
| 4. Typography | 3/4 | Sizes correct; chip uses `labelLarge` (Medium 500) where spec demands Label Regular 400 |
| 5. Spacing | 3/4 | 4dp-grid compliant incl. 48dp hit targets; chip row padding (12/4/4) undercuts spec'd 16dp row padding |
| 6. Accessibility / TalkBack | 3/4 | Descriptions, stateDescriptions, Short-Snackbar channel all wired; attach stateDescription is raw filename, not the contracted sentence |

**Overall: 17/24** — advisory, non-blocking. No BLOCKERs; 3 WARNINGs below + minor recommendations.

---

## Top 3 Priority Fixes

1. **[WARNING — Visuals/Copy] Raw `doc:` prefix visible to users in two places** — `CompactSourceCard` renders `text = source.url` verbatim (OgSourceCard.kt:276), so a document card's dimmed third line reads `doc:notes.txt`; `SourcePreviewSheet` header does the same (SourcePreviewSheet.kt:143-149, primary-tinted). The title line is fine (`ogHostOf` strips the prefix, OgSourceCard.kt:359-361) — only these two URL lines leak. **Fix:** in `CompactSourceCard`, when `source.isDocumentSource()`, either hide the URL line or render the truncation metadata there; in `SourcePreviewSheet`, render the stripped filename (e.g. `ogDisplayTitle`/`ogHostOf`) instead of `source.url` for doc rows.
2. **[WARNING — Copywriting/i18n] Truncation metadata is a hardcoded English literal** — ChatViewModel.kt:1122 `snippet = sendableDocument.truncatedAt?.let { "truncated at $it chars" }`. This string renders on the Fuentes card and has no ES twin, violating the UI-SPEC bilingual note (all user-visible strings via `doc_reader_*` + `values-es`). **Fix:** add `doc_reader_source_truncated` (`truncated at %1$d chars` / `truncado a los %1$d caracteres`) to both `strings.xml` files and use `context.getString(...)` at the call site.
3. **[WARNING — TalkBack] Attach-button `stateDescription` is the bare filename, not the contracted sentence** — ChatInputBar.kt:380-382 sets `stateDescription = attachedDocName`; UI-SPEC Surface 1 requires `"Document attached: {filename}"` style (Phase 65 pattern). A bare filename gives TalkBack users no state context. **Fix:** add a formatted string (e.g. reuse pattern `doc_reader_attached_state`: `Document attached: %1$s` / `Documento adjunto: %1$s`) and set that as the `stateDescription`.

---

## Detailed Findings

### Pillar 1: Copywriting (3/4)

Contract-vs-code (EN `values/strings.xml:277-283`, ES `values-es/strings.xml:278-284`):

| Contract element | Expected | Actual | Verdict |
|---|---|---|---|
| attach description | `Attach document` | `doc_reader_attach` = same | ✅ |
| replace description | `Replace attached document` | `doc_reader_replace` = same | ✅ |
| remove description | `Remove attached document` | `doc_reader_remove` = same | ✅ |
| truncation notice | `Showing first N chars of {filename}` | `doc_reader_truncated` = `Showing first %1$d chars of %2$s` | ✅ |
| unsupported | `Can't read {filename} yet — text files (.txt, .md) only` | `doc_reader_unsupported` = same (escaped) | ✅ |
| failed/empty | `Couldn't read {filename} — answering without it` | `doc_reader_failed` = same (escaped) | ✅ |
| chip inline marker | `· showing first N chars` | `doc_reader_showing_first` + `" · "` prefix (ChatInputBar.kt:203-205) | ✅ (7th key, justified split) |
| source card metadata | `truncated at N chars` | hardcoded literal (ChatViewModel.kt:1122) | ❌ WARNING #2 |

- ES parity holds for all 7 resourced keys; `%1$d`/`%1$s`/`%2$s` placeholders preserved in ES. ✅
- Zero hardcoded user copy elsewhere in the Phase 70 touch surface; notices route through `ChatEvent.Snackbar` → `SnackbarHost` `Short` (ChatScreen.kt:297-300). ✅
- Finding (minor): `formatDocumentSize` sub-1KB files render `0 KB` (e.g. 500 B → `0 KB`, ChatViewModel.kt:3703-3710). Misleading readout; consider `< 1 KB` floor.

### Pillar 2: Visuals (2/4)

- Attach affordance: `Icons.Filled.AttachFile` `IconButton` 40dp in the left icon row beside image/mic/voice (ChatInputBar.kt:377-393), `enabled = !inputLocked` (disabled-not-hidden, mirroring `onAddImage`). ✅ Content-description swap attach↔replace (ChatInputBar.kt:386-389). ✅ No model gating — renders unconditionally, correct per "same UX local + remote". ✅
- Attachment chip: above-input slot (ChatInputBar.kt:177-223), icon + filename (single-line ellipsis, `weight(1f)`) + size + inline truncation marker + remove X. ✅ Single-tap remove, no dialog (ChatInputBar.kt:212). ✅ Caption stays editable; `hasContent` includes `attachedDocName` (ChatInputBar.kt:327,338). ✅ Picker: `OpenDocument` with `text/plain, text/markdown, text/*` (ChatScreen.kt:647-649); null-result no-op; replace semantics via VM overwrite. ✅
- Doc source card: grounded turn registers `GroundedSource(url = "doc:"+filename, extractedText = bounded text, snippet = truncation)` (ChatViewModel.kt:1118-1123); tap opens `SourcePreviewSheet` on bounded text; zero cards when skipped. ✅ Structure matches contract.
- **WARNING #1:** raw `doc:` prefix leaks — OgSourceCard.kt:276 (`text = source.url`) and SourcePreviewSheet.kt:144 (`text = source.url`, primary-tinted). Users see `doc:notes.txt` styled as a link. The title line is correct; only the URL/metadata lines leak.
- Backstop note: `faviconFallbackUrl("doc:…")` returns null via failed `URI` host parse (OgSourceCard.kt:394-401), so doc cards correctly collapse to text-only — no broken thumb. ✅ Sheet browser button correctly hidden for doc rows (SourcePreviewSheet.kt:180). ✅

### Pillar 3: Color (3/4)

- Chip: `surfaceContainer` bg (ChatInputBar.kt:179), Description icon `onSurfaceVariant` (190), filename `onSurface` (197), size/marker `onSurfaceVariant` (209), remove X `onSurfaceVariant` (216, neutral — correctly not `error`). ✅
- Attach button: `Color.White.copy(alpha = 0.6f)` — identical to the image-attach sibling (ChatInputBar.kt:366,390), i.e. sibling-parity neutral, never accent. ✅ (Hardcoded white is the pre-existing bar pattern, not new debt.)
- Doc card chrome: same `CompactSourceCard` container/shape/padding as web cards (OgSourceCard.kt:214-229). ✅ No accent fill, no pulse on the chip. ✅
- Finding (tied to WARNING #1): the leaked `doc:` URL line in the preview sheet renders in `primary` (SourcePreviewSheet.kt:146) — accent/primary color on a non-actionable, non-link row. Resolves automatically once WARNING #1 is fixed (don't show a URL line, or tint it `onSurfaceVariant`).

### Pillar 4: Typography (3/4)

- Filename: `labelLarge`, `maxLines = 1`, `Ellipsis` (ChatInputBar.kt:194-200) — never wraps, never pushes remove-X out. ✅ Size/marker line: same style, `maxLines = 1`. ✅
- Finding (minor): spec demands **Label 14sp Regular (400)** for filename/size/marker; `MaterialTheme.typography.labelLarge` is **14sp Medium (500)** in M3 defaults. One weight step heavier than contracted across three chip texts. Fix: `.copy(fontWeight = FontWeight.Normal)` on the three chip `Text`s, or switch to `labelMedium` if the project treats it as the Regular slot — either way, align all three identically.
- Card/sheet type (14sp Semibold title, 12sp metadata, 14sp/21sp sheet body) reuses existing web-card chrome byte-for-byte — correct per "exact same card chrome" rule; not new-spec type. ✅

### Pillar 5: Spacing (3/4)

- 4dp grid: chip spacers 8dp (193,202), spacer below chip 8dp (222), remove-X hit target 48dp (212), attach button 40dp sibling parity (378), card padding 12dp (existing chrome). ✅ Touch-target floor: attach 40dp (sibling-parity exception, contracted), remove-X 48dp ✅, sheet action `heightIn(min = 44dp)` n/a for doc rows (button hidden). ✅
- Finding (minor): chip `Row` padding is `start = 12dp, end = 4dp, top/bottom = 4dp` (ChatInputBar.kt:184) vs spec "attachment row padding md 16px". The 4dp end inset is accounted for (48dp IconButton bleeds touch target), but the 12dp start / 4dp verticals undercut the 16dp row-padding token. Either amend the spec or bump to `start = 16dp, top/bottom = 8dp`-ish (keeping `end = 4dp` for the 48dp target math).
- Finding (within tolerance): doc glyph 20dp (191) vs sibling affordances 24dp; remove-X glyph 14dp — both inside the contracted 12–18dp remove-glyph band and visually subordinate. No change required, noted for record.

### Pillar 6: Accessibility / TalkBack (3/4)

- Attach button: content description swaps attach→replace ✅; `stateDescription` present ✅ but **WARNING #3** (bare filename, unlocalized sentence missing).
- Remove X: `doc_reader_remove` description + 48dp target ✅. Chip icon `contentDescription = null` (decorative) ✅. Filename/size plain text ✅.
- Doc cards: `Role.Button` + `contentDescription = previewCd` (`cd_preview_source`) on `CompactSourceCard` (OgSourceCard.kt:217-223) — announces with the filename-derived title via `ogHostOf` stripping ✅. Omitida/doc-OK tap gating unchanged (`clickable = status == OK`, SourcePreviewSheet.kt:216-221). ✅
- Notices: all three go through the existing `SnackbarHost`, `Short` (ChatScreen.kt:297-300; VM emits at ChatViewModel.kt:3541-3555 pick-time, 1108-1134 send-time). ✅ Send never dead-ended: unsupported keeps chip hidden + text sendable (`readyDoc` null-gate, ChatScreen.kt:131,650-652); failed/empty sends text-only. ✅
- Finding (minor): FAILED attachments notify **twice** — pick-time (WR-01 fix, ChatViewModel.kt:3546-3555) **and** send-time backstop (ChatViewModel.kt:1108-1114). TalkBack users hear "Couldn't read…" twice for one event. Consider suppressing the send-time repeat when the pick-time notice already fired for the same attachment instance.
- Out of scope but verified: rotation keeps attachment (VM `StateFlow`, ChatViewModel.kt:124-125 + ChatScreen.kt:130-131 collection); backgrounding keeps chip, no foreground service. Matches the unresolved-assumption row; no code contradicts it.

---

## Files Audited

- `app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt` (attach button :377-393, chip :177-223)
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` (picker :356-360, wiring :644-653, Snackbar channel :284-300)
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` (attach/read :3531-3559+, fusion + notices :1097-1135, `formatDocumentSize` :3703-3710)
- `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt` (Fuentes block, `CompactSourceCard` host :388-397)
- `app/src/main/java/com/warped/ui/chat/components/OgSourceCard.kt` (`CompactSourceCard` :192-287, `ogHostOf` doc branch :357-361)
- `app/src/main/java/com/warped/ui/chat/components/SourcePreviewSheet.kt` (header :116-151, doc browser-button gate :177-190)
- `app/src/main/java/com/warped/domain/model/GroundedSource.kt` (`doc:` convention :10-21)
- `app/src/main/res/values/strings.xml:277-283`, `app/src/main/res/values-es/strings.xml:278-284`
- Baseline: `.planning/phases/70-document-reader-tool/70-UI-SPEC.md`

## Registry Safety

Not applicable — native Android project, no shadcn / no third-party registries (UI-SPEC §Registry Safety: none).
