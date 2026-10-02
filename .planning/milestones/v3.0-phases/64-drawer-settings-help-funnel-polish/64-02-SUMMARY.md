---
phase: 64-drawer-settings-help-funnel-polish
plan: "02"
subsystem: funnel-help
tags: [compose, catalog, empty-state, help, strings]
dependency_graph:
  requires: ["64-01"]
  provides: [catalog-use-in-chat, two-cta-empty-states, help-rewrite]
  affects: []
tech_stack:
  added: []
  patterns: [bound-chat-activation-chain, accent-filled-CTA, EN-ES-string-parity]
key_files:
  created: []
  modified:
    - app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt
    - app/src/main/java/com/warped/ui/huggingface/CatalogViewModel.kt
    - app/src/main/java/com/warped/ui/models/ModelsScreen.kt
    - app/src/main/java/com/warped/ui/selector/UnifiedSelectorScreen.kt
    - app/src/main/java/com/warped/ui/navigation/NavGraph.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-es/strings.xml
decisions:
  - "Kept all 8 Help sections (rewrite in place, one short sentence per step) instead of collapsing section count — meets the short-minimal acceptance with minimal structural risk; wording review stays in code review per locked decision"
  - "ES mirror for models_empty_add_endpoint_cta uses 'Agregar un endpoint' following the ES file's existing convention (cd_add_endpoint = 'Agregar endpoint') rather than the plan draft's 'Anadir'"
metrics:
  duration: "~45 min"
  completed: "2026-10-02"
---

# Phase 64 Plan 02: Funnel CTAs + Help Rewrite Summary

Catalog Use in Chat with bound-chat activation, two-CTA empty states on
ModelsScreen and the live Selector route, and a short minimal Tavily-free
Help rewrite with full EN+ES parity.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | Catalog Use in Chat button plus activation chain | 40059238 | HuggingFaceScreen.kt, CatalogViewModel.kt, NavGraph.kt |
| 2 | Two-CTA empty states on ModelsScreen and UnifiedSelectorScreen | ce8f1bfd | ModelsScreen.kt, UnifiedSelectorScreen.kt |
| 3 | Help numbered-steps rewrite EN plus ES | 0a8c6565 | strings.xml (EN+ES) |

## What Was Built

**Task 1 — Catalog Use in Chat (FUN-01 per D-04, T-64-03 mitigated):**
- Downloaded catalog cards render a `Row` (8dp spacing) with a compact
  filled accent Button (`0xFFD97757`, white text, 8dp shape, `weight(1f)`)
  labeled with the existing `use_in_chat` string beside the unchanged
  delete `IconButton` (22dp, error tint, existing delete content-description,
  same confirm dialog). Active-download, failed, and delete-confirm paths
  untouched; `isEffectivelyDownloaded` stays the branch condition.
- `CatalogViewModel.useDownloadedModel(entry)` resolves the `LocalModel`
  via `localModelRepository` by entry modelFile, then mirrors the
  `ModelsViewModel` chain (`connectLocal` + `openBoundChat` + `pendingChatId` /
  `consumePendingChat`) so the conversation carries the verified model
  binding. New `chatRepository` + `@ApplicationContext` constructor deps
  (Hilt-provided, same as ModelsViewModel).
- `HuggingFaceScreen` collects `pendingChatId` with a `LaunchedEffect`
  calling `onUseInChat` then `consumePendingChat` (ModelsScreen lines 59-64
  pattern); NavGraph `Screen.HuggingFace` destination navigates
  `Screen.ChatDetail(id)` with the same `popUpTo(Screen.Chat)` as
  `Screen.Models`. `EndpointsScreen.kt` untouched.

**Task 2 — Two-CTA empty states (FUN-02, FUN-03 per D-04):**
- `ModelsScreen`: gate, heading (`no_models_endpoints_yet`), and hint
  unchanged; single `add_model` wizard button replaced with a centered
  column (8dp spacing): filled accent Button (`models_empty_download_cta`
  → `onOpenHuggingFace`) above an `OutlinedButton` 8dp
  (`models_empty_add_endpoint_cta` → `viewModel.showEndpointForm()`).
  FAB and add-wizard dialog untouched.
- `UnifiedSelectorScreen`: new combined
  `localModels.isEmpty() && endpoints.isEmpty() && activeDownloads.isEmpty()`
  gate rendering heading + hint + the same two CTAs (via existing
  `onOpenHuggingFace` param and `UnifiedSelectorViewModel.showEndpointForm`);
  per-section headers/rows render only under the `else` branch for
  non-empty states. Endpoint `selectRemote` + `onNavigateToChat` pattern
  untouched.

**Task 3 — Help rewrite (HELP-01 per D-03):**
- All 41 `help_s*_step*` strings rewritten to one short sentence each in
  both languages; zero Tavily steps and zero API-key steps anywhere in
  `help_s` keys (the old `help_s3_step3` API-key step now reads "Enter the
  server URL and choose the model to use."). `HelpSection` composable and
  all 8 section blocks in `HelpScreen.kt` byte-identical (no .kt change).
- New pairs: `models_empty_download_cta` ("Download a local model" /
  "Descargar un modelo local") and `models_empty_add_endpoint_cta`
  ("Add a new Endpoint" / "Agregar un endpoint") in both files.
- Remaining "API key" matches in the sweep are non-help keys outside plan
  scope: `api_key` form label, wizard onboarding copy, and
  `settings_msg_key_deleted` toast (retained per 64-01 for the
  programmatic `deleteEndpointKey` path).

## Verification

- Catalog: `use_in_chat` count 1 in HuggingFaceScreen; `pendingChatId`
  count 5 in CatalogViewModel / 3 in HuggingFaceScreen; delete
  content-description match present.
- Empty states: both CTA keys count 2 per file; `showEndpointForm`
  present in Selector; `onOpenHuggingFace` in both screens.
- Help: zero Tavily/API-key matches across all 49+49 `help_s` keys;
  `help_s` key sets diff-clean between EN and ES; both CTA pairs present
  1x per file; both strings files parse as valid XML.
- `./gradlew :app:assembleDebug --offline` → BUILD SUCCESSFUL (exit 0).
- Typography: all touched text reuses surveyed roles (`bodyLarge`/`bodySmall`
  headings+hints, `bodyMedium` Help steps, default button-label style,
  `titleMedium`/`labelMedium` Selector headers) — no new sizes or weights.

## Deviations from Plan

### Auto-fixed Issues

None - plan executed as written, with two agent-discretion choices
covered by CONTEXT ("agent's Discretion: exact Help copy wording"):

**1. [Rule 2 - Correctness] UnifiedSelectorScreen combined gate uses
if/else so per-section headers hide when empty**
- **Found during:** Task 2
- **Issue:** A bare additive branch would have left the Local/Remote
  section headers plus `selector_no_local` text rendering above the new
  CTAs on the empty surface.
- **Fix:** Combined-empty renders the CTA item; all existing sections
  moved under `else` so non-empty states are pixel-identical.
- **Files modified:** UnifiedSelectorScreen.kt
- **Commit:** ce8f1bfd

**2. [Rule 2 - Correctness] ES endpoint CTA uses "Agregar un endpoint"**
- **Found during:** Task 3
- **Issue:** Plan draft said "Anadir"; the ES file's existing convention
  for add-endpoint is "Agregar" (`cd_add_endpoint`).
- **Fix:** Used "Agregar un endpoint" per the plan's own "follow the ES
  file's existing convention" instruction.
- **Files modified:** values-es/strings.xml
- **Commit:** 0a8c6565

## Decisions Made

- Kept all 8 Help sections with in-place one-sentence rewrites rather
  than collapsing section count — satisfies the short-minimal acceptance
  with zero structural churn; EN drafted + ES mirrored for user wording
  review in code review per the locked decision.
- Catalog activation uses the `Screen.ChatDetail(id)` bound-chat chain
  (ModelsScreen-proven) rather than the lighter `selectRemote` +
  `Screen.NewChat` variant, so the new conversation carries the model
  binding (T-64-03).

## Known Stubs

None.

## Threat Flags

None — no new security surface beyond the plan's threat register: catalog
activation reuses the verified-binding bound-chat chain (T-64-03
mitigated), Help copy is static text with no input handling (T-64-04
accepted), no package-manager installs (T-64-SC accepted).

## Self-Check: PASSED

- All 7 modified files exist on disk.
- All 3 commits exist: 40059238, ce8f1bfd, 0a8c6565.
- SUMMARY.md created at `.planning/phases/64-drawer-settings-help-funnel-polish/64-02-SUMMARY.md`.
