---
phase: quick-20260928-catalog-downloaded-spacing
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/warped/ui/huggingface/CatalogViewModel.kt
  - app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt
  - app/src/test/java/com/warped/ui/huggingface/CatalogDownloadUrlTest.kt
  - app/src/test/java/com/warped/ui/huggingface/CatalogDownloadedTest.kt
autonomous: true
requirements: [QUICK-catalog-downloaded, QUICK-catalog-spacing]
must_haves:
  truths:
    - "A model already on-device shows the Descargado check in the catalog, never a Download button"
    - "Tapping the Descargado check does nothing (no download, no navigation)"
    - "Card gaps are tighter: 4dp title-to-action, 4dp row gap, 3dp icon spacing"
  artifacts:
    - path: "app/src/main/java/com/warped/ui/huggingface/CatalogViewModel.kt"
      provides: "downloadedFileNames StateFlow from LocalModelRepository.observeModels"
      contains: "downloadedFileNames"
    - path: "app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt"
      provides: "isEffectivelyDownloaded helper + isOnDevice wiring + spacing edits"
      contains: "isEffectivelyDownloaded"
    - path: "app/src/test/java/com/warped/ui/huggingface/CatalogDownloadedTest.kt"
      provides: "on-device mapping + helper unit tests"
      contains: "isEffectivelyDownloaded"
  key_links:
    - from: "app/src/main/java/com/warped/ui/huggingface/CatalogViewModel.kt"
      to: "LocalModelRepository.observeModels"
      via: "StateFlow mapping filePath substringAfterLast"
      pattern: "observeModels"
    - from: "app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt"
      to: "CatalogDownloadActions downloaded branch"
      via: "downloaded = sessionDownloaded || isOnDevice"
      pattern: "isEffectivelyDownloaded"
---

<objective>
Fix two catalog-card defects: (1) models already on-device are offered for
download again because `downloaded` derives only from in-session WorkManager
state; (2) card icon/title gaps are too wide.

Purpose: downloaded models must render the CheckCircle branch with no download
affordance; spacing tightens per locked spec.
Output: ViewModel on-device flow, screen wiring, spacing edits, unit tests, green build.
</objective>

<execution_context>
@/home/cotizcesar/Documents/warped/.opencode/gsd-core/workflows/execute-plan.md
@/home/cotizcesar/Documents/warped/.opencode/gsd-core/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt
@app/src/main/java/com/warped/ui/huggingface/CatalogViewModel.kt
@app/src/main/java/com/warped/domain/repository/LocalModelRepository.kt
@app/src/main/java/com/warped/domain/model/LocalModel.kt
@app/src/main/java/com/warped/di/InferenceModule.kt
@app/src/test/java/com/warped/ui/huggingface/CatalogDownloadUrlTest.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: ViewModel on-device presence flow</name>
  <files>app/src/main/java/com/warped/ui/huggingface/CatalogViewModel.kt</files>
  <action>Inject LocalModelRepository into CatalogViewModel constructor (Hilt binding already exists: InferenceModule.LocalModelRepositoryBinder binds LocalModelRepositoryImpl to the interface — no new module needed). Expose downloadedFileNames: StateFlow<Set<String>> derived from localModelRepository.observeModels() via viewModelScope + stateIn with SharingStarted.Eagerly and emptySet() initial (matching existing downloadStates strategy). Map each LocalModel.filePath (full on-device path) to its file name via substringAfterLast('/') and collect into a Set. Match key MUST be entry.modelFile (catalog file name, e.g. gemma-4-E4B-it.litertlm), NOT downloadId/modelId — on-device rows predate the repo-slug modelId change and carry no slug. Do not touch startDownload/pause/resume/cancel or URL logic.</action>
  <verify>
    <automated>./gradlew :app:assembleDebug 2>&1 | tail -5</automated>
  </verify>
  <done>CatalogViewModel constructor takes LocalModelRepository; downloadedFileNames emits file-name set derived from observeModels with Eagerly sharing.</done>
</task>

<task type="auto">
  <name>Task 2: Screen wiring + pure helper + spacing</name>
  <files>app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt</files>
  <action>In HuggingFaceScreen collect viewModel.downloadedFileNames via collectAsStateWithLifecycle, and per entry pass isOnDevice = entry.modelFile in downloadedFileNames into CatalogModelCard (add an isOnDevice: Boolean = false param to keep call sites safe). Add a top-level pure JVM-testable helper `fun isEffectivelyDownloaded(downloadState: DownloadState?, isOnDevice: Boolean): Boolean` that returns true when the existing session rule holds (non-null state, not active, error == null, progress >= 1f) OR isOnDevice is true; place it next to expandedText. Card computes downloaded = isEffectivelyDownloaded(downloadState, isOnDevice) and passes it to CatalogDownloadActions unchanged — the existing CheckCircle branch already renders contentDescription "Descargado" with no click handler, so tapping it is a no-op by construction; do NOT add onClick/select/load behavior (out of scope). Spacing edits, exactly these three and nothing else: line 206 Spacer width 8.dp to 4.dp (title-to-action), line 218 Spacer height 8.dp to 4.dp (row gap), line 351 Arrangement.spacedBy(6.dp) to spacedBy(3.dp) (capability icons). Do not alter lazy-column 8.dp arrangement, 14.dp card padding, or progress/error spacers.</action>
  <verify>
    <automated>./gradlew :app:assembleDebug 2>&1 | tail -5</automated>
  </verify>
  <done>On-device entries render CheckCircle with no download button; helper is top-level pure; exactly three spacing values changed (4dp/4dp/3dp).</done>
</task>

<task type="auto">
  <name>Task 3: Tests — on-device mapping, helper, constructor updates</name>
  <files>app/src/test/java/com/warped/ui/huggingface/CatalogDownloadedTest.kt, app/src/test/java/com/warped/ui/huggingface/CatalogDownloadUrlTest.kt</files>
  <action>Create CatalogDownloadedTest with: (a) ViewModel on-device mapping test — fake LocalModelRepository (anonymous object implementing the interface; observeModels returns MutableStateFlow(listOf(LocalModel(...))) with filePath "/data/user/0/com.warped/files/models/gemma-4-E4B-it.litertlm", other methods TODO/throw) + mockk ModelDownloadManager with downloadStates MutableStateFlow(emptyMap()); construct CatalogViewModel(repository, downloadManager, fakeLocalRepo); advance test dispatcher / runTest and assert downloadedFileNames contains "gemma-4-E4B-it.litertlm" and does NOT contain an unrelated name; (b) isEffectivelyDownloaded unit tests — (null,false)->false, session-completed (DownloadState progress 1f, no error, not downloading/paused, true) with isOnDevice false -> true, in-progress state with isOnDevice false -> false, idle/null state with isOnDevice true -> true, failed state with isOnDevice true -> true (on-device wins). Verify DownloadState constructor field names from com.warped.data.local.download.DownloadState before writing (do not guess). Update CatalogDownloadUrlTest buildViewModel and the repo-less test to pass a fake/relaxed LocalModelRepository as the new third constructor param (mockk(relaxed=true) with observeModels returning MutableStateFlow(emptyList()) is cheapest; keep existing URL assertions untouched).</action>
  <verify>
    <automated>./gradlew :app:testDebugUnitTest --tests "com.warped.ui.huggingface.*" 2>&1 | tail -8</automated>
  </verify>
  <done>New test file covers mapping + helper truth table; existing URL tests pass with updated constructor; huggingface test scope green.</done>
</task>

</tasks>

<verification>
./gradlew :app:assembleDebug passes; ./gradlew :app:testDebugUnitTest fully green.
Grep gates (use grep -v '^#' form, never bare == 0 on unfiltered files):
- downloadedFileNames present in CatalogViewModel.kt
- isEffectivelyDownloaded present in HuggingFaceScreen.kt
- No remaining width(8.dp) at old line 206 site / spacedBy(6.dp) in CatalogCapabilityIcons
</verification>

<success_criteria>
- Previously-downloaded model shows Descargado check, no Download affordance, tap is no-op
- Spacings are 4dp / 4dp / 3dp at the three listed sites, nothing else changed
- Download engine, URLs, modelFile naming untouched; no select/load-from-catalog added
- Full :app:testDebugUnitTest green
</success_criteria>

<output>
Create `.planning/quick/20260928-catalog-downloaded-spacing/SUMMARY.md` when done
</output>
