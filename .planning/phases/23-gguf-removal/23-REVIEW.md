---
phase: 23-gguf-removal
reviewed: 2026-05-09T23:00:00Z
depth: standard
files_reviewed: 32
files_reviewed_list:
  - app/build.gradle.kts
  - .gitmodules
  - app/proguard-rules.pro
  - app/src/main/java/com/warped/data/local/inference/EngineManager.kt
  - app/src/main/java/com/warped/data/local/inference/BackendDetector.kt
  - app/src/main/java/com/warped/di/InferenceModule.kt
  - app/src/main/java/com/warped/data/local/inference/LocalLlmProvider.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/data/local/inference/MemoryChecker.kt
  - app/src/main/java/com/warped/data/local/inference/ModelImportManager.kt
  - app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt
  - app/src/main/java/com/warped/data/remote/api/HuggingFaceApi.kt
  - app/src/main/java/com/warped/domain/model/LocalModel.kt
  - app/src/main/java/com/warped/data/local/db/entity/LocalModelEntity.kt
  - app/src/main/java/com/warped/data/local/db/entity/PresetEntity.kt
  - app/src/main/java/com/warped/domain/model/Preset.kt
  - app/src/main/java/com/warped/domain/model/ProviderType.kt
  - app/src/main/java/com/warped/data/local/db/Migrations.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt
  - app/src/main/java/com/warped/ui/models/ModelsScreen.kt
  - app/src/main/java/com/warped/ui/models/ModelsViewModel.kt
  - app/src/main/java/com/warped/ui/help/HelpScreen.kt
  - app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt
  - app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt
  - app/src/main/java/com/warped/ui/huggingface/HuggingFaceUiState.kt
  - app/src/main/java/com/warped/ui/presets/PresetsScreen.kt
  - app/src/main/java/com/warped/ui/presets/PresetsUiState.kt
  - app/src/main/java/com/warped/ui/presets/PresetsViewModel.kt
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values-es/strings.xml
  - app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt
findings:
  critical: 3
  warning: 5
  info: 4
  total: 12
status: issues_found
---

# Phase 23: Code Review Report — GGUF Removal

**Reviewed:** 2026-05-09
**Depth:** standard
**Files Reviewed:** 32
**Status:** issues_found (3 critical, 5 warning, 4 info)

## Summary

Phase 23 successfully deleted the native cpp/ directory, four GGUF Kotlin engine files, NDK build config, and ProGuard rules. The EngineManager was simplified to a LiteRT-LM-only path, data model defaults switched to LITERTLM, and most non-wizard GGUF UI/string references were cleaned. The deletions and data model changes are correctly implemented.

However, **3 critical compile-breaking issues** were found — all caused by dangling references to deleted methods/classes that the phase verification greps did not cover. Two additional compile errors and one call to a non-existent method mean the codebase as-is will not compile. Five warnings identify stale text, wrong provider routing, and unsafe null handling. Four informational items note residual GGUF-prefixed naming deferred to future phases and dead code.

---

## Critical Issues

### CR-01: `GgufMetadata()` instantiation — class not defined

**File:** `app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt:270`
**Issue:** The code instantiates `val modelMetadata = GgufMetadata()` at line 270, but the `GgufMetadata` class does not exist anywhere in the codebase. The import at line 19 (`import com.warped.data.local.inference.GgufMetadata`) references a class that was never created (or was deleted as part of GGUF removal while the worker was being cleaned). A `grep` for `class GgufMetadata|data class GgufMetadata` returns zero results across the entire source tree. This will cause a compile error: `Unresolved reference: GgufMetadata`.

The variable `modelMetadata` is also never used after instantiation (dead assignment), suggesting the GGUF metadata structure is completely vestigial now that only `.litertlm` files are handled.

**Fix:**
```kotlin
// Remove both the import (line 19) and the instantiation (line 270):
- import com.warped.data.local.inference.GgufMetadata
- val modelMetadata = GgufMetadata()
```

---

### CR-02: `memoryChecker.checkGgufRam()` call — method was removed

**File:** `app/src/main/java/com/warped/ui/models/ModelsViewModel.kt:312-318`
**Issue:** The `shouldWarnAboutMemory()` function still calls `memoryChecker.checkGgufRam(modelSizeBytes)` at line 314, but `checkGgufRam()` was explicitly removed from `MemoryChecker.kt` in Phase 23 Task 2 (Sub-step 2.6). The method no longer exists, causing a compile error: `Unresolved reference: checkGgufRam`.

The `isGguf` parameter in the function signature is now always `false` at the call site (`ModelsScreen.kt:268`) since all models default to LITERTLM.

**Fix:**
```kotlin
// Replace lines 312-318:
fun shouldWarnAboutMemory(modelSizeBytes: Long, isGguf: Boolean = false): Boolean {
    return memoryChecker.shouldWarn(modelSizeBytes)  // Always use generic memory check
}
```

Or, if `isGguf` is no longer needed:
```kotlin
fun shouldWarnAboutMemory(modelSizeBytes: Long): Boolean {
    return memoryChecker.shouldWarn(modelSizeBytes)
}
```

And update the call site in `ModelsScreen.kt:268`:
```kotlin
// Before:
if (viewModel.shouldWarnAboutMemory(model.sizeBytes, isGguf = false)) {
// After:
if (viewModel.shouldWarnAboutMemory(model.sizeBytes)) {
```

---

### CR-03: `localLlmProvider.get().configure(modelId)` — method doesn't exist

**File:** `app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt:44,51`
**Issue:** Both `resolve()` (line 44) and `resolveLocal()` (line 51) call `.configure(modelId)` on the `LocalLlmProvider` instance when routing `ProviderType.LOCAL`. However, `LocalLlmProvider` was converted to a no-op stub in Phase 23 Task 2 (Sub-step 2.4), and the `configure()` method was not preserved. A `grep` for `fun configure` returns zero matches across the entire codebase, and the `LlmProvider` interface does not define `configure()`. This will cause a compile error: `Unresolved reference: configure`.

ProviderRouter.kt was not in the `files_modified` list for Phase 23, so this cross-file dependency was missed.

**Fix:**
Option A — Add a no-op `configure()` to `LocalLlmProvider.kt`:
```kotlin
@Deprecated("Use LiteRTLmProvider instead", ReplaceWith("LiteRTLmProvider"))
@Singleton
class LocalLlmProvider @Inject constructor() : LlmProvider {

    override val type = ProviderType.LOCAL

    /** No-op — returns self since GGUF is unsupported. */
    @Suppress("UNUSED_PARAMETER")
    fun configure(modelId: String): LocalLlmProvider = this

    // ... rest of class unchanged
}
```

Option B — Remove `ProviderType.LOCAL` from `resolve()` and `resolveLocal()` `when` branches in ProviderRouter.kt:
```kotlin
// In resolve():
ProviderType.LOCAL, ProviderType.LITE_RT_LM -> liteRTLmProvider.get()

// In resolveLocal():
ProviderType.LOCAL, ProviderType.LITE_RT_LM -> liteRTLmProvider.get()
```

Option B is preferred since all models now default to LITERTLM and `ProviderType.LOCAL` is deprecated.

---

## Warnings

### WR-01: Hardcoded GGUF reference in ModelsScreen UI text

**File:** `app/src/main/java/com/warped/ui/models/ModelsScreen.kt:147`
**Issue:** The "Import Model File" dialog has a hardcoded string: `"Load a .gguf or .litertlm model from your device"`. This still references `.gguf` files. The string resources were cleaned (`import_model_file_desc` now reads "Load a .litertlm model from your device"), but this specific instance is a Compose inline text literal, not an `R.string` reference. Users will see GGUF mentioned in the UI despite full GGUF removal.

**Fix:**
```kotlin
- Text("Load a .gguf or .litertlm model from your device", ...)
+ Text(stringResource(R.string.import_model_file_desc), ...)
```

---

### WR-02: `resolvedSelectedProvider()` always returns LITE_RT_LM

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:531-534`
**Issue:** The `resolvedSelectedProvider()` function reads `state.selectedProvider` but unconditionally returns `ProviderType.LITE_RT_LM` on line 533. This means new conversations always get `providerType = LITE_RT_LM` in Room when saved via `ensureConversation()` (line 591), even when the user is chatting with a remote provider (OpenAI, Ollama, LM Studio, etc.). The `selectedModelId` is read from state but then ignored.

This causes incorrect conversation metadata in the database — conversations with remote models will appear as local LiteRT-LM conversations in the Recents list.

**Fix:**
```kotlin
private fun resolvedSelectedProvider(state: ChatUiState): ProviderType {
    return state.selectedProvider ?: ProviderType.LITE_RT_LM
}
```

The original intent was likely to normalize `ProviderType.LOCAL → LITE_RT_LM`, but the function was incorrectly simplified during cleanup. If normalization is still desired:
```kotlin
private fun resolvedSelectedProvider(state: ChatUiState): ProviderType {
    return when (state.selectedProvider) {
        ProviderType.LOCAL -> ProviderType.LITE_RT_LM
        null -> ProviderType.LITE_RT_LM
        else -> state.selectedProvider
    }
}
```

---

### WR-03: ModelSelector uses deprecated ProviderType.LOCAL

**File:** `app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt:85`
**Issue:** `ModelSelector` dispatches `onModelSelected(model.filePath, ProviderType.LOCAL)` for all local models. It does not check `isLiteRtLm()` or `modelFormat`. By contrast, `ChatScreen.kt` (lines 192-194) correctly infers the provider type: `val providerType = if (isLiteRtLm) ProviderType.LITE_RT_LM else ProviderType.LOCAL`. 

If `ModelSelector` is used anywhere (e.g., in future screens), it would route local models through the deprecated `LocalLlmProvider` instead of `LiteRTLmProvider`. As of now, the `ChatScreen` uses its own inline dropdown, but `ModelSelector` still exists as a reusable component.

**Fix:**
```kotlin
// Add isLiteRtLm helper and use it:
private fun LocalModel.isLiteRtLm(): Boolean =
    modelFormat.equals("LITERTLM", ignoreCase = true) || filePath.endsWith(".litertlm", ignoreCase = true)

// In the DropdownMenuItem onClick:
onModelSelected(
    model.filePath,
    if (model.isLiteRtLm()) ProviderType.LITE_RT_LM else ProviderType.LOCAL
)
```

---

### WR-04: Unsafe `!!` on nullable `target.backend` in EngineManager

**File:** `app/src/main/java/com/warped/data/local/inference/EngineManager.kt:71`
**Issue:** `liteRTLmEngine.init(..., backend = target.backend!!, ...)` uses a non-null assertion (`!!`) on `target.backend`, which is typed as `BackendType?`. While `probeBackend()` currently always returns a non-null value (CPU fallback), the type system allows `ActiveEngine.backend` to be `null`. If `probeBackend()` is ever modified to return `null` (e.g., for a future lazy probe that hasn't executed yet), this would throw a `NullPointerException` at runtime.

**Fix:**
```kotlin
val backend = target.backend ?: BackendType.CPU
liteRTLmEngine.init(
    modelPath = resolvedPath,
    backend = backend,
    ...
)
```

---

### WR-05: Unused `modelMetadata` variable in ModelDownloadWorker

**File:** `app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt:270`
**Issue:** After GGUF removal, `val modelMetadata = GgufMetadata()` is instantiated but never referenced anywhere in the function body. The variable was presumably used for GGUF header validation/parsing that was removed. This is dead code and an unused variable.

**Fix:** Remove line 270 (along with the import at line 19, as noted in CR-01):
```kotlin
// Delete these lines:
- import com.warped.data.local.inference.GgufMetadata    // line 19
- val modelMetadata = GgufMetadata()                       // line 270
```

---

## Info

### IN-01: GGUF-prefixed identifiers in HuggingFace UI (deferred to Phase 24)

**Files:** `HuggingFaceScreen.kt:93,314,402`, `HuggingFaceUiState.kt:7,19`, `HuggingFaceViewModel.kt:104,114`
**Issue:** The codebase contains residually-named GGUF identifiers:
- `GgufFileDetail` data class (now stores LiteRT file metadata with `quantization = null`)
- `ggufFileDetails: Map<String, GgufFileDetail>` in HuggingFaceUiState
- `ggufDetail: GgufFileDetail?` parameter in SiblingFileCard composable
- `ggufFileDetails` property in ModelDetailScreen composable

These are all now technically misleading — they store LiteRT file details. Per the Phase 23 PLAN, Phase 24 (Search Simplification) is expected to clean up HuggingFace naming. No functional impact.

---

### IN-02: Wizard strings reference GGUF/llama.cpp (deferred to Phase 27)

**Files:** `app/src/main/res/values/strings.xml` (wizard-prefixed), `app/src/main/res/values-es/strings.xml` (wizard-prefixed)
**Issue:** Wizard strings such as `wizard_step_2_desc` ("Warped uses two engines to run models locally: llama.cpp for GGUF models..."), `wizard_step_3_title` ("GGUF Models"), and all `wizard_badge_*_gguf` strings still reference GGUF and llama.cpp. These were explicitly excluded from Phase 23 per CONTEXT.md deferral and are scheduled for Phase 27 (Wizard Update). No functional impact until then.

---

### IN-03: Dead code — RamRecommendationBadge composable

**File:** `app/src/main/java/com/warped/ui/models/ModelsScreen.kt:592-604`
**Issue:** The `RamRecommendationBadge` composable is defined (lines 592-604) but never called anywhere. It was previously used to display GGUF RAM estimates in the `ModelCard` composable but was disconnected when the GGUF RAM check block was removed (Phase 23 Task 3, Sub-step 3.3). A `grep` for `RamRecommendationBadge` returns only its definition. This is dead code that should be removed.

**Fix:** Delete lines 592-604 (the entire `RamRecommendationBadge` function).

---

### IN-04: `strictMode` typo in build.gradle.kts comment

**File:** `app/build.gradle.kts:44`
**Issue:** The comment reads `// StrictMode enabled in Application.onCreate for debug builds` but "StrictMode" should be "StrictMode" (Android's `android.os.StrictMode`). Trivial typo, no functional impact.

---

_Reviewed: 2026-05-09T23:00:00Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
