# Quick Plan: Catalog Card Redesign (dense, expandable)

## Goal
Redesign `CatalogModelCard` in HuggingFaceScreen.kt into a dense two-row collapsed card (title + download IconButton; capability icons + size) with tap-to-expand Spanish RAM/blurb description, fed by new `ramNote` + `blurb` JSON fields parsed with missing-field tolerance.

## Current-state enumeration (read from code — do not regress)

`CatalogModelCard` (app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt:113-231) + `CatalogDownloadProgress` (:254-299) have these states, ALL of which must survive in icon form:
1. **idle** — `downloadState == null`, or error == "Cancelled" (terminal-idle, partial deleted), or any non-active non-downloaded state → `OutlinedButton("Download")` (:221-226).
2. **downloading** — `isDownloading`, error != "Cancelled" → `LinearProgressIndicator` + "Downloading: file (N%)" + "downloaded/total · speed" + Pause + Cancel buttons (:261-289).
3. **paused** — `isPaused` → "Paused: ..." label + Resume + Cancel buttons (:269, :281).
4. **progress %** — `(progress*100).toInt()` 0..100 shown in label (:261).
5. **cancel + confirm** — Cancel opens `WarpedAlertDialog("Cancel download? / partial file deleted")` → confirm calls `onCancel()` (:131-144). Must keep dialog.
6. **downloaded** — `!active && error == null && progress >= 1f` → "Downloaded" text (:206-210). Icon form: completed/check icon, non-clickable or opens models list (keep as status icon, no new nav).
7. **error (non-Cancelled)** — idle branch shows `downloadState.error` text in error color + Download button (:212-219); progress branch also shows `state.error` (:290-297). Icon form: error-tinted icon + error text line retained (a11y), retry = download icon tap.
8. **cancel-confirm dialog** strings stay as-is (out of scope for Spanish a11y pass).

Current chips: `CatalogCapabilityBadge` text chips for vision/audio ONLY (:177-188, :233-252) with colors vision 0xFF2196F3 / audio 0xFFFF9800. Thinking (`supportsThinking`) is NOT shown on catalog cards today — new design adds it.

Existing iconography to reuse: `CapabilityIconRow`/`CapabilityIconBadge` (ui/components/CapabilityBadges.kt) — Visibility (vision, purple 0xFF9C27B0), Audiotrack (audio, green 0xFF4CAF50), Psychology (thinking/reasoning, orange 0xFFFF9800). DO NOT reuse its tools icon on catalog cards (function-calling is false everywhere per Phase 49 DEL-01; showing it would promise a removed feature).

## User-locked layout (implement exactly, no redesign)
- **Collapsed row 1:** Title (`entry.displayName`, left, weight 1f) + download **IconButton** top-right REPLACING the `OutlinedButton("Download")`. Keep `Storage` leading icon? No — drop it for density (title only). Keep card colors: `primaryContainer.copy(alpha=0.4f)` (:148-150), same shape.
- **Collapsed row 2:** feature ICONS only (vision/audio/thinking from `entry.capabilities`: vision→Visibility, audio→Audiotrack, supportsThinking→Psychology; tinted per CapabilityBadges colors; Spanish content descriptions "Visión"/"Audio"/"Razonamiento") + size text (`formatFileSize(entry.sizeInBytes)`). Hide `entry.modelFile` line in collapsed view (keeps density; still shown in expanded view or dropped — planner's call, keep it in expanded).
- **Download icon states** (1:1 with enumeration above): idle→Download icon (`Icons.Filled.Download`, cd "Descargar modelo"); downloading→progress: small `CircularProgressIndicator` (progress %) + Pause icon button + Cancel icon button (cancel still opens confirm dialog); paused→Resume icon (`PlayArrow`, cd "Reanudar descarga") + Cancel; downloaded→Check/CheckCircle icon tinted primary, cd "Descargado"; error→error-tinted Download icon + retained error text line below, tap retries. Pause/Resume/Cancel icons get Spanish cds ("Pausar descarga", "Reanudar descarga", "Cancelar descarga").
- **Tap card toggles expanded:** `Modifier.clickable` on Card (role=Button, Spanish state cd "Expandir detalles"/"Contraer detalles") → expanded shows `ramNote` + `blurb` (1-2 lines, `bodySmall`, `onSurfaceVariant`), plus retained `modelFile` line and full error text when present. Collapsed shows only the two rows. Expansion state: `remember { mutableStateOf(false) }` per card (keyed by `entry.name` via LazyColumn key — survives scroll; no ViewModel change). Extract pure helper `expandedText(entry): String?` (ramNote+blurb join) for unit testing.
- **A11y:** all user-facing cds in Spanish (Descargar/Descargado/Pausar/Reanudar/Cancelar/Visión/Audio/Razonamiento/Expandir/Contraer). Keep "Cancel download?" dialog strings untouched.

## Locked data (implement exactly)
- New optional JSON fields per model entry: `ramNote: String? = null`, `blurb: String? = null` on `AllowlistedModel` (ModelAllowlistRepository.kt:50-77). `ignoreUnknownKeys` already true — missing fields decode to null, NO crash on old JSON. Fallback when null/blank: **hide the expanded section entirely** (card not expandable, no chevron/ripple hint) — chosen over generic text because invented RAM guidance would be worse than none.
- Asset values (Spanish, exact strings):
  - `gemma-4-E2B-it`: ramNote `"Desde ~4 GB de RAM"`, blurb `"Chat general y multimodal ligero."`
  - `gemma-4-E4B-it`: ramNote `"Recomendado 6 GB o más"`, blurb `"Más calidad en razonamiento y código, multimodal."`
  - `gemma-3n-E2B-it-int4`: ramNote `"Desde ~6 GB de RAM (aprox.)"`, blurb `"Chat con visión y audio eficiente."`
  - `gemma-3n-E4B-it-int4`: ramNote `"Recomendado 8 GB o más (aprox.)"`, blurb `"Mayor calidad multimodal."`
- Mobile-footprint basis for honesty note in JSON `meta.note` append: E2B 1.1GB / E4B 2.5GB Google-published mobile numbers; 3n numbers are conservative approximations (no published mobile footprint) — the "(aprox.)" suffix marks them. Do NOT touch flags/sizes/repos.

## Out of scope
Download engine (ModelDownloadManager/Worker), catalog entries/flags/sizes/repos beyond ramNote+blurb, thinking toggle, drawer/nav, dialog strings, ModelsScreen.kt ModelCard.

## Tasks

### Task 1 — JSON + parsing + tests
**Files:** `app/src/main/assets/model_allowlist.json`, `app/src/main/java/com/warped/data/repository/ModelAllowlistRepository.kt`, `app/src/test/java/com/warped/data/repository/ModelAllowlistTest.kt`, `app/src/test/java/com/warped/ui/huggingface/CatalogDownloadUrlTest.kt` (update only if it asserts exact entry shape/count — check first).
**Action:**
- Add `ramNote: String? = null` + `blurb: String? = null` to `AllowlistedModel` with KDoc noting back-compat (absent/blank → null → expanded section hidden). No serializer config change needed (`ignoreUnknownKeys`+`coerceInputValues` already tolerant).
- Add the 4 locked ramNote/blurb pairs to the asset + append honesty note to `meta.note`.
- Update/extend ModelAllowlistTest: shipped asset hasSize(4) retained; assert each entry's ramNote/blurb exact strings; new tests: (a) JSON without ramNote/blurb parses to nulls (missing-field fallback), (b) blank strings treated as absent by `expandedText` helper contract (null return). Keep all verified-only flag assertions untouched.
**Verify:** `./gradlew :app:testDebugUnitTest --tests "com.warped.data.repository.ModelAllowlistTest" --tests "com.warped.ui.huggingface.CatalogDownloadUrlTest"`
**Done:** 4 entries parse with exact locked strings; missing/blank fields → null, no exception.

### Task 2 — Card composable rewrite
**Files:** `app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt` (only), optionally extract `app/src/main/java/com/warped/ui/huggingface/CatalogCardUi.kt` if file exceeds ~450 lines after rewrite (planner's call at implementation time).
**Action:**
- Rewrite `CatalogModelCard`: collapsed Row 1 = Title (weight 1f, maxLines 1...2 with ellipsis — 1 line preferred) + download-state IconButton cluster (idle/download, downloading mini-progress+pause+cancel, paused resume+cancel, downloaded check, error retry). Keep `showCancelConfirm` dialog flow verbatim, triggered from cancel icon.
- Row 2 = capability icons (Visibility/Audiotrack/Psychology per entry.capabilities, colors + shapes from `CapabilityIconBadge` — import and reuse it directly, do NOT duplicate; delete local `CatalogCapabilityBadge` text-chip composable) + size text via `formatFileSize`.
- Expandable: `remember` boolean per card; `Modifier.clickable(role=Role.Button, onClickLabel="Expandir detalles"/"Contraer detalles")`; expanded block shows ramNote (titleSmall/semibold ok) + blurb (bodySmall) + modelFile line; non-expandable when both null/blank (no clickable, no affordance). Pure helper `expandedText(entry: AllowlistedModel): String?` in same file (or extracted file) joining ramNote+blurb, null when both blank — unit-testable.
- Keep card colors/shape (`primaryContainer 0.4f`), 8dp spacing language, storage-icon removal, Spanish cds everywhere user-facing. Pause/resume/cancel callbacks unchanged (`onPause/onResume/onCancel/onDownload` props).
- Delete now-dead `CatalogDownloadProgress` wide layout + `CatalogCapabilityBadge`; keep `formatFileSize`/`formatDownloadSpeed` (speed text may go under expanded or a tooltip — keep at least bytes/total in downloading row for parity).
**Verify:** `./gradlew :app:assembleDebug` + `./gradlew :app:testDebugUnitTest` (full) green.
**Done:** All 7 download states reachable in icon form with Spanish cds; vision/audio/thinking icons render per capabilities; tap toggles Spanish ramNote+blurb; missing fields → no expand affordance, no crash.

## must_haves
- truths:
  - "Collapsed card shows title + download icon + capability icons + size, no Download button"
  - "Every prior download state (idle/progress %/paused/cancel-confirm/downloaded/error) works in icon form"
  - "Tap expands Spanish RAM guidance + blurb; missing fields hide expansion without crashing"
  - "Vision/audio/thinking icons match CapabilityBadges iconography; user-facing strings in Spanish"
- artifacts:
  - path: "app/src/main/assets/model_allowlist.json"
    provides: "ramNote + blurb on 4 entries"
    contains: "ramNote"
  - path: "app/src/main/java/com/warped/data/repository/ModelAllowlistRepository.kt"
    provides: "nullable ramNote/blurb fields"
    contains: "ramNote"
  - path: "app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt"
    provides: "rewritten dense expandable card"
    contains: "expandedText"
- key_links:
  - from: "HuggingFaceScreen.kt CatalogModelCard"
    to: "CapabilityIconBadge"
    via: "direct import/reuse"
    pattern: "CapabilityIconBadge"
  - from: "model_allowlist.json ramNote/blurb"
    to: "AllowlistedModel"
    via: "kotlinx parsing with null defaults"
    pattern: "ramNote.*= null"

## Verify (whole plan)
```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
**Honest note:** visual polish (density, icon alignment, dark-purple card look, expand animation) needs on-device confirmation — no adb in this environment. Logic + build + unit tests verified here; screenshot review on hardware before closing.
