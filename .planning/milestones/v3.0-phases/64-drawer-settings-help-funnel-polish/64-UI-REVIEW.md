# Phase 64 — UI Review

**Audited:** 2026-10-02
**Baseline:** 64-UI-SPEC.md (design contract §§1–7)
**Screenshots:** not captured (native Android app — no web dev server; code-only audit of composables + string resources)

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 2/4 | Stale empty-state hint references removed flow; hardcoded English a11y strings; desktop keyboard copy on Android |
| 2. Visuals | 3/4 | All 7 contract placements match; weak delete-all affordance; pre-existing green CTA + gray delete break card-row consistency |
| 3. Color | 3/4 | Accent reservation holds on all new CTAs; one pre-existing green CTA wears a reserved-CTA role in the wrong color |
| 4. Typography | 3/4 | Footer 16sp Semibold fix exact; pre-existing undeclared sizes (11/13/14sp, titleSmall, headlineMedium) outside phase scope |
| 5. Spacing | 3/4 | All phase-added spacing on-scale; pre-existing 6/10dp values + contract's own 3dp step rule contradict the multiples-of-4 scale |
| 6. Experience Design | 3/4 | Full state coverage (Snackbar error channel, gated delete-all, confirms); hardcoded "Dismiss"; no retry on catalog load-failure |

**Overall: 17/24**

---

## Top 3 Priority Fixes

1. **Stale `models_empty_hint` copy contradicts the new two-CTA empty state** — user impact: hint says "Tap Add Model to download one from the catalog or import a file" but the single Add-Model button is gone, replaced by "Download a local model" / "Add a new Endpoint" (`ModelsScreen.kt:259`, `UnifiedSelectorScreen.kt:118`). Fix: rewrite EN+ES, e.g. EN "Download a model from the catalog, or connect a remote endpoint." / ES mirror, in both `values/strings.xml:285` and `values-es/strings.xml`.
2. **Hardcoded English strings bypass EN+ES parity** — user impact: Spanish users hear/see English in a11y + Snackbar. Fix: `NavGraph.kt:241` `Icons.Filled.Close, "Delete"` → `stringResource(R.string.delete)` (same pattern already used at `HuggingFaceScreen.kt:368`); `ModelsScreen.kt:69` `actionLabel = "Dismiss"` → `R.string.dismiss` (already exists, used at `UnifiedSelectorScreen.kt:221`).
3. **Wrong-platform Help step + off-reservation CTA color on the same funnel surface** — user impact: `help_s8_step1` "Enter sends; Shift+Enter adds a new line." describes a desktop keyboard in an Android app; `UnifiedSelectorScreen.kt:342` "Use in Chat" button uses green `Color(0xFF4CAF50)` where the contract reserves accent `#D97757` for exactly this CTA role (ModelsScreen + catalog both use accent). Fix: rewrite s8_step1 EN+ES for touch (e.g. "Tap send to reply; drafts stay in the input."); recolor the selector CTA to `WarpedAccent`.

---

## Detailed Findings

### Pillar 1: Copywriting (2/4)

Contract requires: "Download a model" (sheet), "Use in Chat" (catalog), "Download a local model" + "Add a new Endpoint" (empty states), short one-sentence Help with no Tavily/key steps, EN+ES parity on all new keys.

**Passes (verified by grep):**
- `drawer_empty_download_cta` = "Download a model" / "Descargar un modelo" (`strings.xml:288`, `values-es:290`) — exact contract wording, EN+ES paired. **BLOCKER-level check passed.**
- `models_empty_download_cta` = "Download a local model" / "Descargar un modelo local", `models_empty_add_endpoint_cta` = "Add a new Endpoint" / "Agregar un endpoint" — present, paired.
- Zero `tavily` matches and zero `api key` matches inside `help_s*` keys in both languages — HELP-01 hard requirement met. All 41 steps read as one short sentence (6–13 words sampled across s1–s8).
- Delete-all confirm reuses existing `settings_delete_chats_title` + plural `settings_delete_chats_msg` with error confirm + Cancel dismiss (`NavGraph.kt:257–276`) — matches contract verbatim.
- **WARNING — stale hint (funnel dead-end copy):** `models_empty_hint` still reads "Tap Add Model to download one from the catalog or import a file." There is no "Add Model" button anymore on either empty surface. The hint also omits the endpoint path. Both `ModelsScreen.kt:259` and `UnifiedSelectorScreen.kt:118` render it directly above the two new CTAs, so the contradiction is on-screen.
- **WARNING — casing deviation on reserved CTA:** contract writes the catalog CTA as "Use in Chat" (§§Copywriting, §6), but the reused `use_in_chat` string is "Use in chat" (lowercase c, `strings.xml:22`). Reuse was contract-sanctioned ("reuse existing where possible"), so this is a spec-vs-resource drift to resolve by picking one casing — recommend updating the resource to "Use in Chat" (EN) since two new screens now hinge on it.
- **WARNING — hardcoded a11y English:** `NavGraph.kt:241` per-row drawer delete uses the literal `"Delete"` as contentDescription (not a string resource — invisible to translators, hardcoded English for ES TalkBack users). Pre-existing pattern, but this phase owned the drawer and left it.
- **WARNING — wrong-platform Help step:** `help_s8_step1` "Enter sends; Shift+Enter adds a new line." — Shift+Enter is a desktop convention; Android has no Shift+Enter. Also `help_s8_step2` "Save generation settings as Presets." is imperative-fragment while all siblings are full sentences — minor tone inconsistency.
- **WARNING — ambiguous drawer label (contract-compliant but weak):** the drawer delete-all row reuses `settings_delete_all` = "Delete All" (`NavGraph.kt:281`). In the drawer context "Delete All" has no object (all *what*?); the confirm dialog clarifies, but the row itself reads as a generic destructive action. Contract explicitly blessed the reuse, so not a violation — consider `delete_all_chats`-style dedicated key in a follow-up.
- Minor: ES `models_empty_add_endpoint_cta` "Agregar un endpoint" (lowercase e) vs EN "Add a new Endpoint" (capital E) — noun-casing drift between languages.

### Pillar 2: Visuals (3/4)

Contract placements verified in code — all 7 items match:
- DRAWER-01: sheet keeps `selector_no_models`, adds centered filled-accent Button below with 16dp gap; tap calls `onDismiss()` then `onNavigateToCatalog()` (`ModelSelector.kt:158–186`). Focal point on the empty sheet is the accent CTA. ✓
- DRAWER-03: footer order New Chat → Recents → delete-all row → divider → [Models · Help · Settings]; `SpaceEvenly`, `weight(1f)` each; no Web Options item anywhere in drawer (grep-confirmed zero `WebOption` in `NavGraph.kt`). ✓
- DRAWER-02: Web Options block fully deleted from sheet (zero `WebOverrideSheetRow|cd_web_options|globalWebEnabled` matches). ✓
- FUN-01: catalog downloaded cards render `Row(spacedBy 8dp)` with compact filled-accent Button `weight(1f)` + unchanged 22dp error delete icon (`HuggingFaceScreen.kt:355–373`). ✓
- FUN-02/03: both empty surfaces render heading + hint + centered two-CTA column with 8dp spacing; FAB + wizard untouched for non-empty states. ✓
- HELP-01: `HelpSection` composable byte-identical (12dp `#2B2B29` card, 24dp accent icon, accent numbered prefix, 3dp step padding). ✓
- **WARNING — delete-all row has weak affordance:** a bare centered `TextButton` with small default-size text (`NavGraph.kt:278–281`). It sits correctly, but visually it reads as a footer link rather than a destructive row action — no icon, no full-row padding treatment like the conversation cards above it. Functional, but easy to miss / easy to mis-tap past.
- **WARNING — inconsistent destructive + CTA treatment across card rows (pre-existing, visible on phase-touched screens):** `UnifiedSelectorScreen` local-model cards use a gray (`#6B7280`) 18dp trash icon (`:272`) while catalog/Models cards use error-red; the selector endpoint card's primary button is green (`:342`) while every other "Use in Chat" in the app is accent. A user comparing catalog → selector sees three different visual languages for the same two actions.
- Icon-only buttons: catalog download/delete, selector add/edit/delete, drawer per-row delete all carry content-descriptions (one hardcoded — see Pillar 1). Sheet section icons decorative (`contentDescription = null`) — correct.
- Hierarchy: single focal CTA per empty surface; Help headline accent + muted intro preserves scan order. No screenshot verification possible (noted above).

### Pillar 3: Color (3/4)

- **Passes:** all four phase-added CTA buttons use the reserved accent via the `WarpedAccent` theme token (`ModelSelector.kt:176`, `HuggingFaceScreen.kt:362`, `ModelsScreen.kt:267`, `UnifiedSelectorScreen.kt:126`) — the IN-04 token fix held. `WarpedAccent = Color(0xFFD97757)` (`Color.kt:28`) matches the contract token. Accent appears only on: filled CTAs, New Chat label+icon, selected footer tints, section header icons, Help step numbers, selected-card border. Destructive actions uniformly use `MaterialTheme.colorScheme.error`. 60/30/10 distribution holds (drawer `#1F1F1E`, cards `#2B2B29`, accent sparingly).
- **WARNING — accent-reservation breach (pre-existing):** `UnifiedSelectorScreen.kt:342` renders the "Use in Chat" CTA in `Color(0xFF4CAF50)` green. The contract reserves accent for *exactly this button role* and every sibling screen uses it. Green elsewhere in the app means "connected/success dot" (`dotConnected`, status pills) — here it means "primary action", colliding with the status semantic.
- Informational: hardcoded `Color(0xFF…)` literals throughout match the surveyed token table exactly (project convention is literals-as-tokens, not theme roles) — consistent, not flagged. Semantic status colors (green `4CAF50` pills/dots, blue `2196F3` Net pill, orange `FF9800` paused) are a pre-existing parallel palette outside this contract's scope; the pills' green/blue predate the phase and are untouched.

### Pillar 4: Typography (3/4)

- **Passes (the phase's typographic deliverable is exact):** drawer footer labels at `fontSize = 16.sp, FontWeight.SemiBold` (`NavGraph.kt:289,304,319`) matching New Chat (`:159`); zero `12.sp` labels remain in the drawer. All phase-added text reuses surveyed roles (`bodyMedium` sheet/button text, `bodyLarge`/`bodySmall` empty headings+hints, `titleMedium`/`labelMedium` headers).
- **WARNING — undeclared sizes/roles elsewhere on the same screens (all pre-existing, none introduced by phase 64):** `labelSmall` overridden to `fontSize = 11.sp` in both `ModelMetaChip` definitions (`ModelsScreen.kt:446`, `UnifiedSelectorScreen.kt:370`); drawer "Recents" header hardcoded `13.sp` (`NavGraph.kt:171`); conversation titles hardcoded `14.sp` (`:232`); `EndpointSelectorCard` title uses `titleSmall` (`UnifiedSelectorScreen.kt:320`) where the contract's heading role is `titleMedium`; Help intro headline uses `headlineMedium` (`HelpScreen.kt:53`) which appears nowhere in the contract's type table. Net effect: the audited surfaces use ~9 distinct text treatments against a 4-role contract. Recommend a type-ramp cleanup pass folding 11/13/14sp literals and `titleSmall`/`headlineMedium` into the declared roles — explicitly out of phase-64 scope (removals/additions only), logged here so it isn't mistaken for phase-64 drift.

### Pillar 5: Spacing (3/4)

- **Passes:** every phase-added value sits on the declared scale — sheet CTA gap 16dp (md), catalog row gap 8dp (sm), empty CTA spacing 8dp (sm), sheet/selector empty vertical padding 32dp (xl), card padding 16dp (md), card shape 12dp, button shape 8dp. Contract-mandated values reproduced exactly.
- **WARNING — scale self-contradiction (contract-level, not implementation drift):** the scale demands "multiples of 4", yet the contract itself mandates 3dp step padding (`HelpScreen.kt:220` implements it faithfully). Hairline `0.5.dp` dividers (`NavGraph.kt:170,283`) and `1.dp` selection borders (`ModelSelector.kt:220`) are standard hairline exceptions. Pre-existing off-scale values persist around the new work: 6dp chip/meta gaps, 10dp logo/download gaps, 2dp chip internals, 5dp-adjacent paddings. All pre-existing and untouched — but a strict multiples-of-4 audit cannot pass while the contract's own HelpSection rule is 3dp. Recommend amending the scale to list sanctioned exceptions (hairlines 0.5/1dp, dense chip internals 2/6dp, step rows 3dp) rather than chasing pixel edits.
- No arbitrary `[Npx]`/`[Nrem]` Tailwind-style escapes applicable (native Compose); no anomalous large gaps; `SpaceEvenly` footer and centered CTA columns preserve rhythm.

### Pillar 6: Experience Design (3/4)

State coverage across the four touched surfaces:
- **Loading:** ModelsScreen import progress bar + active-download cards; catalog active downloads via shared `ActiveDownloadContent`; selector download cards with progress/pause states. No skeletons anywhere — catalog explicitly renders load-failure copy instead (documented decision, acceptable for a bundled-asset list).
- **Error:** catalog activation failures surface via `SnackbarHost` + `showSnackbar` + `clearError` (`HuggingFaceScreen.kt:94–103`, the WR-01 fix); ModelsScreen error Snackbar with Dismiss action (`:67–72`); selector error Snackbar with dismiss (`UnifiedSelectorScreen.kt:217–224`). `pendingChatId` consume-after-navigate guards double-navigation on all three screens.
- **Empty:** sheet empty → CTA to catalog; Models + Selector empty → dual CTAs; catalog zero-entries → failure copy. Delete-all row correctly gated on `conversations.isNotEmpty()` (`NavGraph.kt:254`) — no dead button.
- **Destructive confirms:** delete-all (`settings_delete_chats_title` + plural), per-chat, per-model (with name + size), per-endpoint, cancel-download — all `WarpedAlertDialog` with error confirm + Cancel. Post-delete cleanup chain (clear active conversation + selection, `scheduleUnload()`, navigate NewChat) intact.
- **Disabled states:** endpoint cards disable "Use in Chat" when `modelId == null` (ModelsScreen `:544`); selector shows explanatory hint text instead (`:348–354`) — acceptable variant, no dead button.
- **Back/gesture:** sheet `BackHandler` → hide + dismiss; Settings `BackHandler` → `onBack`; consistent with gesture-nav.
- **WARNING — hardcoded Snackbar action:** `ModelsScreen.kt:69` `actionLabel = "Dismiss"` is an English literal; the identical affordance in `UnifiedSelectorScreen.kt:221` correctly uses `R.string.dismiss`. One-line fix.
- **WARNING — catalog load-failure is a dead end:** `HuggingFaceScreen.kt:122–136` renders static `hf_load_failed` text with no retry affordance. If the bundled asset ever fails to parse, the user's only recourse is leaving and returning (which re-runs the same load). Recommend adding a `TextButton` retry calling the ViewModel load path.
- **WARNING — ad-hoc Snackbar placement:** `SettingsScreen.kt:74–79` and `UnifiedSelectorScreen.kt:217–224` render bare `Snackbar` composables inline in content columns (not `SnackbarHost`), so error + message Snackbars can stack (`SettingsScreen` shows both simultaneously if both non-null) and push/overlay content without queue semantics. Pre-existing; works, but diverges from the `SnackbarHost` pattern the phase itself established on the catalog screen.

---

## Registry Safety

Skipped — native Android project, no shadcn (`components.json` absent), no third-party registry blocks per UI-SPEC §Registry Safety. No new dependencies added in either plan (both SUMMARYs confirm `added: []`).

---

## Files Audited

- `app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt` (sheet CTA + Web Options removal)
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` (callback threading — via SUMMARY + NavGraph wiring; not re-read line-by-line)
- `app/src/main/java/com/warped/ui/navigation/NavGraph.kt` (drawer footer, delete-all row, catalog wiring)
- `app/src/main/java/com/warped/ui/settings/SettingsScreen.kt` (+ `SettingsViewModel.kt` / `SettingsUiState.kt` via SUMMARY verification)
- `app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt` (+ `CatalogViewModel.kt` via SUMMARY verification)
- `app/src/main/java/com/warped/ui/models/ModelsScreen.kt` (two-CTA empty state)
- `app/src/main/java/com/warped/ui/selector/UnifiedSelectorScreen.kt` (two-CTA empty state)
- `app/src/main/java/com/warped/ui/help/HelpScreen.kt` (structure — copy reviewed via string resources)
- `app/src/main/res/values/strings.xml` + `app/src/main/res/values-es/strings.xml` (CTA keys, Help rewrite, dialog strings)
- Upstream: `64-UI-SPEC.md`, `64-01-SUMMARY.md`, `64-02-SUMMARY.md`, `64-VERIFICATION.md`
