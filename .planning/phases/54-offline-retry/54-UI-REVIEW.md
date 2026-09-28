# Phase 54 — UI Review

**Audited:** 2026-09-28
**Baseline:** `54-UI-SPEC.md` (design contract, approval pending)
**Screenshots:** not captured (native Android app — no web dev server; code-only audit of `MessageBubble.kt` + `ChatScreen.kt` + `ChatViewModel.kt` retry paths)

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 4/4 | All UI-SPEC strings byte-exact; FETCH_FAILED untouched |
| 2. Visuals | 3/4 | Single trailing-slot change, hierarchy intact; overflow backstop never run on device |
| 3. Color | 3/4 | Phase additions theme-only; pre-existing hardcoded colors in same file bypass theme |
| 4. Typography | 4/4 | No new styles; banner 14sp, button inherits TextButton default per contract |
| 5. Spacing | 4/4 | No new containers/paddings; 44dp touch target present |
| 6. Experience Design | 3/4 | Full state coverage via backend tests; banner visibility gate has zero automated UI test |

**Overall: 21/24**

---

## Top 3 Priority Fixes

1. **Run the held-out overflow backstop on device (both themes)** — long queued copy (`… En espera.`) + trailing `Reintentar` could overflow or wrap awkwardly on narrow screens, breaking the banner row — open the OFFLINE banner with `isValidatedOnline=true` on a small-width device/emulator in light + dark theme and confirm no clipping (UI-SPEC `long-text` row is explicitly a 🧪 backstop, never executed).
2. **Add a Compose UI test for the banner visibility gate** — the `OFFLINE && isValidatedOnline && !isFetchingWeb && !isGenerating` gate in `ModelOnlyBanner` (`MessageBubble.kt:398`) is currently proven only by `assembleDebug` + backend unit tests; a stale-flag or branch regression would ship silently — add a `compose.ui:ui-test-junit4` test asserting Reintentar shown/hidden across the OFFLINE × validated × fetching matrix, including FETCH_FAILED-never-shows.
3. **Migrate pre-existing hardcoded colors in `MessageBubble.kt` to theme tokens (milestone audit)** — `Color(0xFF545450)` (7 sites) and `Color(0xFF121212)` bypass `MaterialTheme.colorScheme`, so surrounding bubble surfaces ignore dark theme while the new banner/button correctly use `onSurfaceVariant`/`primary` — replace with `onSurfaceVariant`/`onSurface` equivalents; out of Phase 54 scope, record for milestone polish.

---

## Detailed Findings

### Pillar 1: Copywriting (4/4)

- ✅ **BLOCKER-check passed:** queued OFFLINE copy is byte-exact per contract — `MessageBubble.kt:381`: `"Sin conexión. Respuesta solo del modelo, sin contenido de la página. En espera."` (v2.2 copy + ` En espera.` suffix, same row, no new screen).
- ✅ Retry affordance is verb-only `Reintentar` (`MessageBubble.kt:409`); accessibility description is the exact UI-SPEC sentence (`MessageBubble.kt:404-405`): `"Reintentar la lectura de las páginas. Disponible al recuperar la conexión."`
- ✅ FETCH_FAILED branch strings byte-identical (`MessageBubble.kt:382-389`); no copy added to failure path (banner + retry persist by construction — `ChatViewModel.kt:836` `AllFailed -> Unit` leaves transcript untouched).
- ✅ Retry in-flight reuses Phase 52 chip verbatim (`ChatScreen.kt:275-279`: `"Leyendo ${done} de ${total}…"` / `"Leyendo página…"`); no generic labels (`Submit`/`OK`/`Click Here`), no new error copy, Spanish convention kept.
- *Justifying finding (verification note):* every contract string was located by grep and compared character-for-character against the UI-SPEC table — zero deviations.

### Pillar 2: Visuals (3/4)

- ✅ No new screen, container, or navigation; the only visual delta is the queued suffix + trailing `TextButton` in the existing banner `Row` (`MessageBubble.kt:370-414`). Icon (16dp `Info`, `onSurfaceVariant`) and text hierarchy unchanged; clear focal point (chat transcript) unaffected.
- ✅ Icon-only-adjacent risk: the banner `Icon` has `contentDescription = null` (decorative, correct); the `Reintentar` action is text-labeled with a full `contentDescription` override via `semantics` (`MessageBubble.kt:403-406`) — standard Compose pattern, screen-reader announcement verified by code inspection.
- ⚠️ **WARNING — long-text backstop never executed:** UI-SPEC marks queued-suffix + trailing-action overflow as a 🧪 held-out device check (WEB-05/WEB-06 precedent). `weight(1f, fill = false)` on the shared `Text` (`MessageBubble.kt:393`) should yield space to the button, but no screenshot, screenshot-test, or device report exists in either SUMMARY. Fix: Priority Fix #1.
- *Score justification:* structure and hierarchy fully meet the contract; the single-point deduction is the unverified visual backstop on the phase's only changed row.

### Pillar 3: Color (3/4)

- ✅ Phase 54 additions use theme tokens exclusively: banner text + icon `onSurfaceVariant` (`MessageBubble.kt:374,392`), Reintentar label `primary` (`MessageBubble.kt:410`), retry chip spinner `primary` (`ChatScreen.kt:298`). Accent appears only on declared elements (button label, spinner); queued marker carries no warning/error tint, per contract.
- ✅ Destructive `errorContainer`/`onErrorContainer` usage elsewhere untouched (`ChatScreen.kt:513-514`).
- ⚠️ **WARNING — pre-existing hardcoded colors in the audited file:** `Color(0xFF545450)` at `MessageBubble.kt:137,143,173,339,442,448,464` and `Color(0xFF121212)` at `:116` bypass the Material 3 theme, so the bubble surfaces surrounding the newly-themed banner render identically in light and dark theme (dark-theme legibility risk). Not introduced by Phase 54 and explicitly out of scope (Phase 53 polish deferred to milestone audit), but it degrades the themed context the banner lives in. Fix: Priority Fix #3.
- *Score justification:* the phase delta is color-clean; the deduction reflects hardcoded colors present in the exact file/surface under audit.

### Pillar 4: Typography (4/4)

- ✅ No new text styles introduced. Banner copy: `fontSize = 14.sp` Body, regular (existing banner style, `MessageBubble.kt:391`). Reintentar label: no explicit size/weight override — inherits Material 3 `TextButton` default label style, exactly as the contract prescribes (`MessageBubble.kt:408-411` sets only `color = primary`).
- ✅ Retry chip: 14sp (`ChatScreen.kt:303`), Phase 52 reuse. Size inventory across touched code paths: `14.sp` only (plus pre-existing `12.sp` Semibold at `MessageBubble.kt:238-239` on an untouched surface — within the declared Body/Label roles, ≤2 roles in scope).
- *Justifying finding (verification note):* grep for `fontSize|\.sp|fontWeight` across both UI files shows no size/weight added by this phase; the button's reliance on the theme default matches the contract's "default label style" wording rather than evading it.

### Pillar 5: Spacing (4/4)

- ✅ Zero new layout containers or paddings: banner reuses existing `Row` (icon 16dp + 8dp spacer + shared text), matching the declared xs/sm/md rhythm. `weight(1f, fill = false)` is a documented no-op for FETCH_FAILED rows (no sibling competes), so the untouched branch lays out identically.
- ✅ Accessibility exception honored: `Modifier.heightIn(min = 44.dp)` on the `TextButton` (`MessageBubble.kt:402`) satisfies the 44dp minimum touch target without becoming a scale token. `heightIn` (not `sizeIn`) is the correct interpretation here — width stays content-driven for a trailing text action; height is what the exception constrains.
- *Justifying finding (verification note):* spacing-class grep across `ChatScreen.kt`/`MessageBubble.kt` shows only existing-scale values (4/6/8/10/16/24dp) plus the 44dp exception — no arbitrary `[…px]` values introduced by this phase.

### Pillar 6: Experience Design (3/4)

- ✅ **Loading:** retry drives `isFetchingWeb` + `webFetchProgress` (`ChatViewModel.kt:770-785`) through the same `MultiUrlFetcher.fetchAll` entry point; `ChatScreen.kt:272-308` re-renders the Phase 52 `Leyendo N de M…` chip with spinner + Stop path. No parallel fetch path, no WorkManager.
- ✅ **Stop during retry:** `retryGrounding` sets `isGenerating = true` (WR-01) to surface the existing Stop; `stopGeneration()` cancels `retryJob` (`ChatViewModel.kt:878-879`) with stale-finally guard (`:843-847`); transcript untouched → queued banner + Reintentar intact. Proven by `ChatGroundingRetryTest` Stop test (8/8 suite green per 54-01-SUMMARY).
- ✅ **Error/empty:** `AllFailed` → transcript untouched (banner + retry persist, no new copy); success → `modelOnlyNotice = null` + Fuentes attach (`ChatViewModel.kt:820-834`), assistant text byte-identical, no re-inference. OFFLINE-only data gate (`:755-760`) rejects FETCH_FAILED/stale taps; synchronous connectivity re-check (`:748-751`) makes stale-flag taps safe no-ops. No destructive actions → no confirmation needed (contract).
- ✅ **No auto-retry:** `ON_RESUME` observer calls only `refreshConnectivity()` (`ChatScreen.kt:132-142`) — flips button visibility, never fetches. Explicit tap is the sole trigger.
- ⚠️ **WARNING — visibility gate has no automated UI test:** the 8-test exit-gate suite covers backend guards, but the Compose condition at `MessageBubble.kt:398` (`OFFLINE && isValidatedOnline && !isFetchingWeb && !isGenerating`) is proven only by `assembleDebug`. Note the gate is *stricter* than the UI-SPEC's stated `OFFLINE && validated && !fetching` (adds `!isGenerating` + `isStreaming` refusal in the VM, `ChatViewModel.kt:739-740`, mirroring WR-03) — conservative and documented, but it also hides Reintentar during unrelated inference streaming, which the contract never describes. Fix: Priority Fix #2 (test should pin the intended four-condition gate so future edits can't silently widen/narrow it).
- *Score justification:* state coverage is complete and backend-tested; the deduction is the untested UI gate plus the undocumented-but-benign gate strictness.

---

## Registry Safety

Skipped — native Android (Jetpack Compose) project, no shadcn. UI-SPEC Registry Safety table: `not applicable (native Android, no shadcn) | none | not required`. Zero new dependencies added by either plan (both SUMMARies confirm reuse-only).

---

## Files Audited

- `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt` (`ModelOnlyBanner` `:358-415`, call site `:105-111`)
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` (resume observer `:131-142`, Leyendo chip `:272-308`, message-list wiring `:412-415`)
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` (`retryGrounding` `:734-851`, `stopGeneration` `:860+`, `refreshConnectivity` `:273-280`)
- `.planning/phases/54-offline-retry/54-UI-SPEC.md` (baseline contract)
- `.planning/phases/54-offline-retry/54-01-SUMMARY.md`, `54-02-SUMMARY.md` (implementation claims cross-checked)
- `.planning/phases/54-offline-retry/54-01-PLAN.md`, `54-02-PLAN.md` (intent reference)
