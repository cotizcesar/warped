---
phase: 64-drawer-settings-help-funnel-polish
plan: "01"
subsystem: drawer-settings
tags: [compose, navigation-drawer, settings, model-selector, strings]
dependency_graph:
  requires: []
  provides: [sheet-catalog-cta, drawer-delete-all, settings-removals]
  affects: ["64-02"]
tech_stack:
  added: []
  patterns: [WarpedAlertDialog-confirm, accent-filled-CTA, EN-ES-string-parity]
key_files:
  created: []
  modified:
    - app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
    - app/src/main/java/com/warped/ui/navigation/NavGraph.kt
    - app/src/main/java/com/warped/ui/settings/SettingsScreen.kt
    - app/src/main/java/com/warped/ui/settings/SettingsViewModel.kt
    - app/src/main/java/com/warped/ui/settings/SettingsUiState.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-es/strings.xml
decisions: []
metrics:
  duration: "~35 min"
  completed: "2026-10-02"
---

# Phase 64 Plan 01: Drawer Cluster + Settings Cleanup Summary

Drawer empty-state CTA navigating to the Model Catalog, Web Options removed from the
model sheet, uniform 16sp Semibold drawer footer with drawer-owned bulk delete, and
removals-only Settings cleanup with EN+ES string parity.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | Sheet empty-state CTA plus Web Options removal | 43f88d49 | ModelSelector.kt, ChatScreen.kt |
| 2 | NavGraph drawer footer restyle plus delete-all row and catalog wiring | bf0f82e5 | NavGraph.kt |
| 3 | Settings removals-only cleanup with string hygiene | 9da99221 | SettingsScreen.kt, SettingsViewModel.kt, SettingsUiState.kt, strings.xml (EN+ES) |

## What Was Built

**Task 1 — Model sheet (DRAWER-01, DRAWER-02 per D-01):**
- `ModelSelectorSheet` empty state keeps `selector_no_models` text and adds below it
  (centered Column, 16dp gap) a filled accent Button (`0xFFD97757`, white text, 8dp
  shape) labeled `drawer_empty_download_cta`; tap calls `onDismiss()` then the new
  `onNavigateToCatalog: () -> Unit = {}` callback.
- Deleted the divider + `cd_web_options` header + `WebOverrideSheetRow` block, the
  `WebOverrideSheetRow`/`WebOverrideOption` composables, the `webOverride`,
  `globalWebEnabled`, `onWebOverrideSelected` params, and orphaned `Circle`,
  `WebOverrideIndicator`/`webOverrideIndicator`, `Box`, `Arrangement`, `size`
  imports (`clickable`, `Check`, `HorizontalDivider`, `SectionHeader` retained —
  still used by `ModelRow`/local-network headers).
- `ChatScreen` (verified sole caller) threads
  `onNavigateToCatalog = { showModelPicker = false; onNavigateToCatalog() }` and
  drops the three web arguments; adds the `onNavigateToCatalog` param defaulted
  to `{}` next to `onNavigateToSelector`.

**Task 2 — Drawer (DRAWER-03, DRAWER-04 per D-01):**
- Footer Models/Help/Settings labels bumped 12sp to 16sp Semibold, matching the New
  Chat label; tint logic, 20dp icons, `SpaceEvenly` row, `weight(1f)` unchanged.
  No Web Options item exists in the drawer (nothing to remove there).
- Full-width error-colored `settings_delete_all` TextButton row directly below the
  recents `LazyColumn` and above the footer divider, rendered only when
  `conversations.isNotEmpty()` (T-64-01 gate). Opens a `WarpedAlertDialog` on the
  per-row pattern with `settings_delete_chats_title` + plural
  `settings_delete_chats_msg`, error confirm, Cancel dismiss; confirm calls
  `chatRepository.deleteAllConversations()`, clears `activeConversationId` +
  `activeModelSelection`, calls `engineManager.scheduleUnload()`, navigates
  `Screen.NewChat`. Per-row delete dialog untouched.
- `onNavigateToCatalog = { navController.navigate(Screen.HuggingFace) }` wired at
  all three chat destinations (`Chat`, `NewChat`, `ChatDetail`).

**Task 3 — Settings (SET-01, SET-02 per D-02, deed2513 recipe, removals only):**
- `SettingsScreen.kt`: removed Data section header + counts card (incl. Delete
  chats button) and its `showDeleteChatsDialog` block; removed Security
  key-deletion header + card and its `showDeleteKeysDialog` block. Orphaned
  `Storage`/`Lock` icon, `pluralStringResource`, `WarpedAlertDialog` imports
  deleted. Remaining sections (Web grounding, Display, App) keep order, cards,
  and styles.
- `SettingsViewModel.kt`: deleted `show/dismissDeleteChatsDialog`,
  `deleteAllChats`, `show/dismissDeleteKeysDialog`, `deleteAllApiKeys`;
  **retained `deleteEndpointKey`** + `apiKeyStore.deleteKey` programmatic path
  (T-64-02). Removed the four now-readerless count collectors, the unused
  repository constructor params, and the orphaned `first` + repository imports.
- `SettingsUiState.kt`: deleted `isDeletingChats`, `isDeletingKeys`, `chatCount`,
  `endpointCount`, `modelCount`, `presetCount`, `showDeleteChatsDialog`,
  `showDeleteKeysDialog` (grep-proved zero remaining readers).
- Strings: added `drawer_empty_download_cta` ("Download a model" / "Descargar un
  modelo") with exact EN+ES parity; deleted 12 orphaned keys from both files
  (`settings_section_data`, `settings_section_security`,
  `settings_delete_all_keys_btn`, `settings_delete_keys_title/msg`,
  `settings_row_*`, `settings_security_desc`, `settings_msg_chats_deleted`,
  `settings_msg_keys_deleted`). Kept `settings_delete_chats_title/msg` +
  `settings_delete_all` (drawer reuses) and `settings_msg_key_deleted`
  (`deleteEndpointKey` uses).

## Verification

- Sheet: `onNavigateToCatalog` count 2/2 files; `WebOverrideSheetRow|cd_web_options|globalWebEnabled` count 0; no `webOverride` remnants in ModelSelector.kt.
- Drawer: `deleteAllConversations` present; `onNavigateToCatalog` in 3 destinations; `fontSize = 16.sp` on New Chat + 3 footer labels; zero `12.sp` labels; no Web Options in drawer.
- Settings: zero matches for `settings_section_data|settings_delete_all_keys_btn|showDeleteKeysDialog|showDeleteChatsDialog`; `deleteEndpointKey` retained; CTA key present in both strings files; zero residual refs to deleted keys.
- `./gradlew :app:assembleDebug --offline` → BUILD SUCCESSFUL (incl. full `--rerun-tasks` rebuild).
- Typography: all touched text uses surveyed roles (`bodyMedium` sheet/button text, default button-label style mirroring existing patterns, locked 16sp Semibold footer) — no new sizes or weights.

## Deviations from Plan

None - plan executed exactly as written.

## Decisions Made

None — all choices locked by CONTEXT/SPEC/PATTERNS; implemented as specified.

## Known Stubs

None.

## Threat Flags

None — no new security surface beyond the plan's threat register: the delete-all
row is gated on non-empty recents plus confirm (T-64-01 mitigated), and
`deleteEndpointKey` remains programmatic-only with no new UI trigger (T-64-02
mitigated). No package-manager installs (T-64-SC accepted).

## Self-Check: PASSED

- All 7 modified files exist on disk.
- All 3 commits exist: 43f88d49, bf0f82e5, 9da99221.
- SUMMARY.md created at `.planning/phases/64-drawer-settings-help-funnel-polish/64-01-SUMMARY.md`.
