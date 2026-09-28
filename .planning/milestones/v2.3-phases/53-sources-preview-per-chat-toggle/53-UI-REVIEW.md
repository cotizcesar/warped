# Phase 53 — UI Review

**Audited:** 2026-09-28
**Baseline:** `53-UI-SPEC.md` (design contract)
**Screenshots:** not captured — native Android Compose app, no web dev server (ports 3000/5173 closed; port 8080 is unrelated). Code-only audit against contract strings, Material 3 theme roles, spacing scale, and state coverage.

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 3/4 | All contract copy exact; Toast fallback copy invented outside contract |
| 2. Visuals | 3/4 | Sheet adds undeclared `titleLarge` heading duplicating the `[N]` badge |
| 3. Color | 4/4 | Accent reserved correctly; theme-only colors in all new code |
| 4. Typography | 3/4 | Undeclared `titleLarge` role for sheet title; all else matches |
| 5. Spacing | 3/4 | Badge `6dp/2dp` padding off-scale; touch-target exception correctly applied |
| 6. Experience Design | 3/4 | No persistent override-state indicator; all-omitida case renders nothing |

**Overall: 19/24**

---

## Top 3 Priority Fixes

1. **No visible per-chat web-override state** — after setting `Web: No`, nothing in the selector bar or composer shows the override; user can send messages believing web is on — add a state pill/icon reflecting Sí/No/Heredar next to the overflow menu (`ChatScreen.kt:706-754`).
2. **Redundant sheet header (`Fuente N` titleLarge + `[N]` badge)** — number shown twice, and `titleLarge` is outside the spec's three type roles — drop the title line and keep the spec'd badge+URL header block (`SourcePreviewSheet.kt:65-95`).
3. **All-omitida messages render no Fuentes block** — `fuenteList.any { it.clickable }` gate (`MessageBubble.kt:223`) hides omitida rows exactly when the honesty signal matters most; render struck omitida rows (or a count) even with zero ok sources, or amend the contract's zero-rule explicitly.

---

## Detailed Findings

### Pillar 1: Copywriting (3/4)

Contract copy verified exact against `53-UI-SPEC.md §Copywriting Contract`:

- ✅ Fuentes ok `[N] {url}` clickable (`MessageBubble.kt:234`); omitida `[N] {url} — omitida` (`MessageBubble.kt:265`).
- ✅ Sheet title `Fuente {N}` (`SourcePreviewSheet.kt:66`); empty-extract copy verbatim (`SourcePreviewSheet.kt:136-137` matches spec line 111).
- ✅ `Abrir en navegador` (`SourcePreviewSheet.kt:129`); tri-state `Web: Sí` / `Web: No` / `Web: Heredar` (`ChatScreen.kt:719,727,737`); inherit hint `Heredar (activado/desactivado global)` (`ChatScreen.kt:634`).
- ✅ Toggle Snackbar `Preferencia de web actualizada. Se aplicará al próximo mensaje.` (`ChatViewModel.kt:270,279`); persistence-failure Snackbar verbatim (`ChatViewModel.kt:629-630`); `Sin web` chip (`ChatInputBar.kt:187`).

**[WARNING] Toast fallback copy invented outside the contract.** `MessageBubble.kt:298` (`"Enlace no válido."`) and `MessageBubble.kt:308,314` (`"No se encontró un navegador para abrir el enlace."`) have no UI-SPEC entry — the contract only specs swipe/back dismiss + `ACTION_VIEW`. Spanish is reasonable, but (a) unreviewed copy, (b) identical message for two distinct causes (`ActivityNotFoundException` vs `SecurityException`), (c) trailing-period style inconsistent with sibling strings. Fix: add both strings to the contract (or reuse one browserless line) and differentiate only if actionable.

### Pillar 2: Visuals (3/4)

- ✅ Clear focal point: sheet header (badge + URL) → divider → scrollable body → sticky `FilledTonalButton` action, per spec layout.
- ✅ Icon-only `MoreVert` overflow paired with `contentDescription = "Opciones de web"` (`ChatScreen.kt:710`); clickable Fuentes items carry `Role.Button` + `"Vista previa de la fuente N"` semantics (`MessageBubble.kt:243-261`).
- ✅ Omitida rows have no click ripple (plain `Text`, no `clickable` modifier, `MessageBubble.kt:264-273`).
- ✅ Sheet opens without leaving chat; `skipPartiallyExpanded = true` matches `ModelSelector` pattern (`SourcePreviewSheet.kt:51`).

**[WARNING] Undeclared heading duplicates the badge.** Spec header block is `[N]` badge + URL (`SourcePreviewSheet.kt:72-95` implements this correctly), but `SourcePreviewSheet.kt:65-70` adds a separate `Fuente N` `titleLarge` heading above it — the source number appears twice in ~40dp, adding visual noise and an undeclared hierarchy level. Fix: delete the title `Text` (lines 65-71) and keep badge+URL as the header.

### Pillar 3: Color (4/4)

- ✅ Accent (`primary`) appears ONLY on: clickable fuente items (`MessageBubble.kt:236`), sheet badge content (`SourcePreviewSheet.kt:76`), sheet URL (`SourcePreviewSheet.kt:90`), `FilledTonalButton` action (by type), active `Sin web` chip (`ChatInputBar.kt:180`). Body text `onSurface`, omitida `onSurfaceVariant` — per contract.
- ✅ Zero hardcoded colors in phase-53 code paths (all `MaterialTheme.colorScheme`); neighboring hardcodes (`0xFF121212`, `0xFF545450`, `0xFF2B2B29`) are pre-existing, untouched.
- ✅ Sheet container `surface`, divider default — 60/30/10 distribution holds.

Minor (non-scoring): active `Sin web` uses `primary.copy(alpha = 0.5f)` on a dark bar — active/inactive distinction rests on a subtle background shift; consistent with the existing thinking-chip idiom, but verify contrast on-device in both themes.

### Pillar 4: Typography (3/4)

- ✅ Badge 12sp semibold (`SourcePreviewSheet.kt:81-82`), URL 14sp (`:89`), sheet body 14sp/21sp (= 1.5 line height, `onSurface`, scrollable, never truncated, `:115-118`). Fuentes heading 12sp semibold, items 14sp (`MessageBubble.kt:226-230,235,266`).
- ✅ Omitida 14sp + `LineThrough` (`MessageBubble.kt:267-268`).

**[WARNING] Sheet title uses `titleLarge` + `SemiBold` (`SourcePreviewSheet.kt:67-68`) — a fourth type role the spec never declares** (spec allows Body / Label / Sheet-body only). Same fix as Visuals finding: remove the title line; no replacement type needed.

### Pillar 5: Spacing (3/4)

Against scale xs 4 / sm 8 / md 16 / lg 24:

- ✅ Sheet: top clearance 24 (`:61`), horizontal 16 (`:62`), header gap 8 (`:71`), divider vertical 8 (`:96`), action row vertical 16 (`:126`), button `heightIn(min = 44.dp)` (`:127`).
- ✅ Fuentes items `padding(vertical = 12.dp)` on ~20sp text ≈ 44dp target — justified under the spec's 44dp accessibility exception, with explanatory comment (`MessageBubble.kt:239-241`); absorbs the old 4dp gaps as spec requires.
- ✅ Menu internals (`ChatScreen.kt`) and chip gaps (`ChatInputBar.kt:176`, 8dp) on-scale.

**[WARNING] Badge padding `horizontal = 6.dp, vertical = 2.dp` (`SourcePreviewSheet.kt:80`) is off-scale** (copied from the pre-existing Local/Net pill idiom, `ChatScreen.kt:674`). Fix: use `horizontal = 8.dp, vertical = 4.dp` (sm/xs) or codify 6/2 as a pill exception in the contract.

### Pillar 6: Experience Design (3/4)

State coverage verified:

- ✅ Loading: no spinner by contract — rows persisted pre-inference, sheet opens synchronously; correct.
- ✅ Error: persist failure → non-blocking Snackbar, send continues (`ChatViewModel.kt:625-633`); browserless → no crash (`ActivityNotFoundException` + `SecurityException` caught, `MessageBubble.kt:305-317`); non-http(s) scheme allowlisted with Toast (`:295-300`).
- ✅ Empty: empty-extract copy path (`SourcePreviewSheet.kt:110-114`), browser button stays available.
- ✅ Disabled: omitida rows non-clickable, `previewForTap` returns null for omitida/out-of-range so the sheet can never open without preview text (`SourcePreviewSheet.kt:178-181`).
- ✅ Chip resets after every send (`ChatViewModel.kt:312`); toggle applies next-send-only, never refetches; precedence one-off > per-chat > global (`:355-359`).
- ✅ Destructive: none in phase — correct, no confirmation needed.

**[WARNING] Override state invisible after setting.** The tri-state lives behind a `MoreVert` overflow with no persistent indicator — unlike the thinking/`Sin web` chips, Sí/No/Heredar leaves no trace in the UI. Users can forget a `Web: No` override across sessions (it survives restart by design). Fix: surface a compact state pill/icon in `InlineModelSelectorBar` reflecting the resolved override.
**[WARNING] All-omitida grounding is silent.** `if (!isUser && fuenteList.any { it.clickable })` (`MessageBubble.kt:223`) hides the entire Fuentes block — including the struck omitida rows — when zero sources are ok. The contract's zero-rule ("no block") and its honesty rule ("omitida visible, no silent drops") collide here; the implementation picked the rule that hides the most informative case. Fix: render omitida rows (or `N omitidas` count) regardless of ok count.
Minor: browserless fallback uses `Toast` while all other phase feedback uses `Snackbar` (`ChatScreen.kt:158-165`) — Toast is less accessible and inconsistent; consider routing through `ChatEvent.Snackbar`.

---

## Registry Safety

Skipped — native Android app, no shadcn (`components.json` absent), UI-SPEC lists no third-party registries. Nothing to audit.

---

## Files Audited

- `app/src/main/java/com/warped/ui/chat/components/SourcePreviewSheet.kt` (sheet, `fuenteItems`, `previewForTap`, `isEmptyExtract`, `browserTarget`, `EMPTY_EXTRACT_COPY`)
- `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt` (Fuentes list, sheet host, browser-intent + Toast fallback)
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` (`InlineModelSelectorBar`, tri-state menu, Snackbar host)
- `app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt` (`Sin web` one-off chip)
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` (`setWebOverride`, `toggleSkipWebOnce`, precedence, persist-failure Snackbar)
- `app/src/main/java/com/warped/domain/model/GroundedSource.kt` (ok/omitida model)
- `.planning/phases/53-sources-preview-per-chat-toggle/53-UI-SPEC.md` (baseline), `53-CONTEXT.md`
