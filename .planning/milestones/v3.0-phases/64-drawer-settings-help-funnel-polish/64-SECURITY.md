# Phase 64 — Drawer + Settings + Help + Funnel Polish: Security Audit

**Phase:** 64 — drawer-settings-help-funnel-polish (plans 64-01, 64-02)
**Verdict:** SECURED (T-64-02 closed via fix 38351c8d — repository-owned cleanup confirmed, dead injection removed)
**Threats Closed:** 6/6 | **Open:** 0/6
**ASVS Level:** 1 (no `<config>` block supplied; UI-polish phase, no auth/crypto changes)
**Audit date:** 2026-10-02
**Auditor stance:** every mitigation assumed absent until a grep match proved it in the cited location.

## Threat Verification

| Threat ID | Category | Disposition | Status | Evidence |
|-----------|----------|-------------|--------|----------|
| T-64-01 | Denial (bulk-delete without confirm) | mitigate | CLOSED | `app/src/main/java/com/warped/ui/navigation/NavGraph.kt:254` gate `if (conversations.isNotEmpty())`; `WarpedAlertDialog` confirm `:257-276`; confirm calls `chatRepository.deleteAllConversations()` `:264`, clears selection, `engineManager.scheduleUnload()` `:268`, navigates `Screen.NewChat`; Cancel dismiss `:275`. Per-row delete dialog untouched. |
| T-64-02 | Tampering (endpoint key-deletion path) | mitigate | CLOSED | Repository-owned cleanup confirmed: `EndpointRepositoryImpl.deleteEndpoint()` calls `apiKeyStore.deleteKey(endpointId)` (`EndpointRepositoryImpl.kt:39-43`) before `endpointDao.deleteById` — no orphaned Keystore aliases. Dead `apiKeyStore` + unused `context` injections removed from `SettingsViewModel` (fix `38351c8d`); post-fix grep `apiKeyStore` in `SettingsViewModel.kt` → zero hits; `assembleDebug` BUILD SUCCESSFUL; `SettingsGroundingToggleTest` 2/2 green. See Closed section. |
| T-64-03 | Tampering (catalog→chat activation binding) | mitigate | CLOSED | `CatalogViewModel.kt:169-181` `useDownloadedModel` resolves `LocalModel` via `localModelRepository.observeModels().first()` matched by `entry.modelFile`; missing-file case returns early with `catalog_activation_failed` error (no unbound chat created). Binding passed to `openBoundChat` is the repository-verified `model.filePath` (`:182-187`), mirroring `ModelsViewModel`; `HuggingFaceScreen.kt:86-90` `pendingChatId` → `onUseInChat` → `consumePendingChat` chain; NavGraph `Screen.HuggingFace` → `Screen.ChatDetail(id)`. No user-controlled modelId reaches chat creation. |
| T-64-04 | Information (Help copy leaks secrets guidance) | accept | CLOSED | Accepted risk logged below. Verified: zero `tavily` matches in `HelpScreen.kt`, `strings.xml`, `values-es/strings.xml`; only `api_key` matches are the pre-existing endpoint form label (`strings.xml:53`, non-`help_s` key, out of scope); ES `clave` matches are unrelated `lab_tpl_keypoints_*` template strings; `HelpScreen.kt` contains no `TextField`/`OutlinedTextField` (no input handling). |
| T-64-SC (64-01) | Tampering (supply chain) | accept | CLOSED | Accepted risk logged below. `files_modified` in 64-01-PLAN contain no gradle/manifest/package files — Kotlin + strings only. |
| T-64-SC (64-02) | Tampering (supply chain) | accept | CLOSED | Same as above for 64-02 file list. |

## Closed Threats (resolved post-audit)

### T-64-02 — `SettingsViewModel.deleteEndpointKey` retention not found (documentation drift) — CLOSED

- **Mitigation expected:** `deleteEndpointKey` + `apiKeyStore.deleteKey` programmatic path retained in `SettingsViewModel.kt` for endpoint-deletion flows; no UI trigger.
- **Resolution (option (b) from audit — recommended path):** the key-cleanup invariant lives in the repository layer, not Settings, by design:
  1. The key-cleanup invariant the mitigation protected **holds**: `EndpointRepositoryImpl.deleteEndpoint()` calls `apiKeyStore.deleteKey(endpointId)` (`EndpointRepositoryImpl.kt:39-43`) before `endpointDao.deleteById` — endpoint deletion still purges the Keystore alias, so no orphaned-key accumulation.
  2. The "no UI trigger" half **holds and is exceeded**: `deleteAllApiKeys` / `showDeleteKeysDialog` / `showDeleteChatsDialog` / `deleteAllChats` have zero references app-wide; `ApiKeyStore.deleteAllKeys` has zero callers (definition only) — bulk key deletion is fully unreachable.
  3. Hygiene fix applied (`38351c8d`): removed the dead `private val apiKeyStore: ApiKeyStore` injection (`:23`) and its import (`:7`), plus the equally unused `@param:ApplicationContext context` param and its imports (`android.content.Context`, `dagger.hilt.android.qualifiers.ApplicationContext`) — grep had already proven zero usages of both. No Hilt module bound `SettingsViewModel` directly (only `hiltViewModel()` in `SettingsScreen.kt`, which uses the generated factory), so no module change was needed. `SettingsGroundingToggleTest.buildViewModel` updated to the new constructor (unused `ApiKeyStore`/`Context`/`R` imports dropped).
- **Verification:** post-fix `grep apiKeyStore|ApiKeyStore|context|Context|ApplicationContext SettingsViewModel.kt` → zero hits; `./gradlew :app:assembleDebug --offline` BUILD SUCCESSFUL; `./gradlew :app:testDebugUnitTest --offline --tests "com.warped.ui.settings.*"` BUILD SUCCESSFUL, `SettingsGroundingToggleTest` 2/2 green.
- **Design record:** endpoint key deletion is owned by `EndpointRepositoryImpl.deleteEndpoint`; `SettingsViewModel` holds no key-deletion API by design; `ApiKeyStore.deleteAllKeys` retained but unreachable (zero callers).

## Focus-area checks (phase brief)

| Check | Result |
|-------|--------|
| No API keys in logs/UI | PASS — Timber calls in touched files (`CatalogViewModel`, `SettingsViewModel`) log only op names/exceptions, never key material; `SettingsScreen.kt` has zero `apiKey/getKey/storeKey` references; `ModelSelector.kt` zero log/key matches; `ModelsScreen.kt:222,239` `formApiKey` passes are pre-existing endpoint-form wiring, untouched by this phase. |
| Destructive-action confirmations intact | PASS — drawer bulk delete gated + `WarpedAlertDialog` confirm (T-64-01 evidence above); catalog downloaded-card `showDeleteConfirm` dialog intact (`HuggingFaceScreen.kt:298-320`, delete `IconButton :365` keeps existing content-description). |
| No new network surfaces | PASS — touched files are Compose UI + ViewModel + strings; no new OkHttp/Retrofit/URL/fetch imports in phase-touched UI files; `CatalogViewModel` activation uses only existing injected repos (`localModelRepository`, `chatRepository`). |
| Keystore handling unchanged-apart (Phase 63 accessor removal respected) | PASS — `ApiKeyStore`/`KeystoreManager` untouched by this phase's commits; per-endpoint `storeKey/getKey/deleteKey` intact; no new keystore accessors added. |
| Endpoint-deletion key cleanup still works | PASS — via `EndpointRepositoryImpl.deleteEndpoint` → `apiKeyStore.deleteKey` (`EndpointRepositoryImpl.kt:41`); former `SettingsViewModel.deleteEndpointKey` drift resolved — T-64-02 CLOSED (fix `38351c8d`). |

## Unregistered Flags

None — both `64-01-SUMMARY.md` and `64-02-SUMMARY.md` report `## Threat Flags: None`, and audit found no new attack surface beyond the registers: sheet CTA navigates to an existing in-app destination, empty-state CTAs target existing callbacks (`onOpenHuggingFace`, `showEndpointForm`), Help rewrite is static text, catalog activation reuses the verified-binding chain.

## Accepted Risks Log

- **A-64-04 (T-64-04):** Help copy is static instructional text with no secrets, no input handling, and EN+ES parity reviewed in code review. Accepted per plan disposition.
- **A-64-SC1 / A-64-SC2 (T-64-SC ×2):** No package-manager installs in either plan (Kotlin + Android strings only); nothing to gate. Accepted per plan disposition.
- **A-64-02 (T-64-02, closed):** Repository-owned key-cleanup path accepted as the permanent design: "Endpoint key deletion owned by `EndpointRepositoryImpl.deleteEndpoint`; `SettingsViewModel` holds no key-deletion API by design; `ApiKeyStore.deleteAllKeys` retained but unreachable (zero callers)."

## Implementation files — READ-ONLY compliance

No implementation files were modified during this audit. Only this SECURITY.md was written.

*Post-audit fix (2026-10-02): T-64-02 closure modified `SettingsViewModel.kt` + `SettingsGroundingToggleTest.kt` (fix `38351c8d`) and this SECURITY.md (docs commit) — outside the audit's read-only window, as prescribed by the audit's own "To close" path (b).*
