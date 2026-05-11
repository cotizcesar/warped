---
phase: 23-gguf-removal
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/build.gradle.kts
  - app/proguard-rules.pro
  - .gitmodules
  - app/src/main/java/com/warped/data/local/inference/EngineManager.kt
  - app/src/main/java/com/warped/data/local/inference/BackendDetector.kt
  - app/src/main/java/com/warped/data/local/inference/LocalLlmProvider.kt
  - app/src/main/java/com/warped/data/local/inference/MemoryChecker.kt
  - app/src/main/java/com/warped/data/local/inference/ModelImportManager.kt
  - app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt
  - app/src/main/java/com/warped/data/local/db/Migrations.kt
  - app/src/main/java/com/warped/data/local/db/entity/LocalModelEntity.kt
  - app/src/main/java/com/warped/data/local/db/entity/PresetEntity.kt
  - app/src/main/java/com/warped/domain/model/LocalModel.kt
  - app/src/main/java/com/warped/domain/model/Preset.kt
  - app/src/main/java/com/warped/domain/model/ProviderType.kt
  - app/src/main/java/com/warped/di/InferenceModule.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt
  - app/src/main/java/com/warped/ui/models/ModelsScreen.kt
  - app/src/main/java/com/warped/ui/help/HelpScreen.kt
  - app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt
  - app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt
  - app/src/main/java/com/warped/ui/huggingface/HuggingFaceUiState.kt
  - app/src/main/java/com/warped/ui/presets/PresetsScreen.kt
  - app/src/main/java/com/warped/ui/presets/PresetsUiState.kt
  - app/src/main/java/com/warped/ui/presets/PresetsViewModel.kt
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values-es/strings.xml
  - app/src/main/java/com/warped/data/remote/api/HuggingFaceApi.kt
autonomous: true
requirements: [GGUF-01, GGUF-02, GGUF-03, GGUF-04, GGUF-05, GGUF-06, GGUF-07]

must_haves:
  truths:
    - "App compiles without NDK, CMake, or externalNativeBuild configuration in build.gradle.kts"
    - "No import of LlamaEngine, LlamaLoadError, GgufMetadataParser, or GgufQuantizationParser exists anywhere in the codebase"
    - "EngineManager.kt contains no reference to LLAMA_CPP, switchToLlama(), probeVulkan(), or getLlamaEngine()"
    - "modelFormat column defaults to 'LITERTLM' in Room entity annotations and migration SQL"
    - "Non-wizard string resources contain no 'GGUF', 'llama.cpp', '.gguf', or 'Local (llama.cpp)' entries"
    - "ModelDownloadWorker.kt and ModelImportManager.kt only handle .litertlm format"
  artifacts:
    - path: "app/build.gradle.kts"
      provides: "Build config without ndk, externalNativeBuild, or jniLibs blocks"
      grep_gate: "grep -c 'ndk\\|externalNativeBuild\\|jniLibs' app/build.gradle.kts"
      expect: 0
    - path: "app/src/main/java/com/warped/data/local/inference/EngineManager.kt"
      provides: "Engine manager with only LITE_RT_LM path"
      grep_gate: "grep -c 'LLAMA_CPP\\|switchToLlama\\|getLlamaEngine\\|probeVulkan'"
      expect: 0
    - path: "app/src/main/java/com/warped/data/local/db/entity/LocalModelEntity.kt"
      provides: "Entity with LITERTLM default"
      grep_gate: "grep 'LITERTLM' app/src/main/java/com/warped/data/local/db/entity/LocalModelEntity.kt | grep -c 'defaultValue'"
      expect: 1
  key_links:
    - from: "ChatViewModel.kt"
      to: "EngineManager.kt"
      via: "engineManager field"
      pattern: "no LlamaEngine import"
    - from: "InferenceModule.kt"
      to: "EngineManager.kt"
      via: "provideEngineManager()"
      pattern: "no LlamaEngine parameter"
---

<objective>
Delete all llama.cpp/GGUF native code, Kotlin engine files, and GGUF references from the codebase. LiteRT-LM becomes the sole local inference engine.

Purpose: Eliminate the entire GGUF/llama.cpp subsystem (native code, JNI bridge, Kotlin engine, DI wiring, UI references) as Phase 23 of the v1.5 cleanup milestone.
Output: A codebase that compiles without NDK; EngineManager has only LiteRT-LM; all non-wizard GGUF UI references and strings removed.
</objective>

<execution_context>
@/home/cotizcesar/.config/opencode/get-shit-done/workflows/execute-plan.md
@/home/cotizcesar/.config/opencode/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/REQUIREMENTS.md
@.planning/phases/23-CONTEXT.md

**Wizard files EXCLUDED per CONTEXT.md deferral:** WizardStep.kt, StepContent.kt, WizardViewModel.kt, WizardUiState.kt, and wizard-prefixed string resources are NOT modified in this phase — Phase 27 handles them.
</context>

<tasks>

<task type="auto">
  <name>Task 1: Delete native code, NDK config, Kotlin engine files, and ProGuard rule</name>
  <read_first>
    - app/build.gradle.kts (lines 21-23, 60-71)
    - .gitmodules
    - app/proguard-rules.pro (lines 17-18)
    - app/src/main/cpp/ (directory listing)
  </read_first>
  <files>
    app/build.gradle.kts
    .gitmodules
    app/proguard-rules.pro
    app/src/main/cpp/ (DELETED)
    app/src/main/java/com/warped/data/local/inference/LlamaEngine.kt (DELETED)
    app/src/main/java/com/warped/data/local/inference/LlamaLoadError.kt (DELETED)
    app/src/main/java/com/warped/data/local/inference/GgufMetadataParser.kt (DELETED)
    app/src/main/java/com/warped/data/local/inference/GgufQuantizationParser.kt (DELETED)
  </files>

  <action>
**Sub-step 1.1 — Delete the native cpp/ directory:**
```bash
rm -rf app/src/main/cpp/
```

**Sub-step 1.2 — Remove submodule from .gitmodules and git:**
Delete the entire `[submodule "app/src/main/cpp/llama.cpp"]` block from `.gitmodules` (the 3 lines: `[submodule "app/src/main/cpp/llama.cpp"]`, `path = app/src/main/cpp/llama.cpp`, `url = https://github.com/ggerganov/llama.cpp.git`). Then run:
```bash
git rm --cached app/src/main/cpp/llama.cpp 2>/dev/null || true
```
Also delete any `.git/modules/app/src/main/cpp/llama.cpp` directory if it exists.

**Sub-step 1.3 — Remove NDK/native build config from app/build.gradle.kts:**
Remove these three blocks from the `defaultConfig { }` and `android { }` blocks:
1. Lines 21-23: The entire `ndk { abiFilters += listOf("arm64-v8a", "x86_64") }` block inside `defaultConfig`.
2. Lines 60-65: The entire `externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }` block.
3. Lines 67-71: The entire `packaging { jniLibs { useLegacyPackaging = false } }` block.

**Sub-step 1.4 — Delete Kotlin engine files:**
Delete these four files:
- `app/src/main/java/com/warped/data/local/inference/LlamaEngine.kt`
- `app/src/main/java/com/warped/data/local/inference/LlamaLoadError.kt`
- `app/src/main/java/com/warped/data/local/inference/GgufMetadataParser.kt`
- `app/src/main/java/com/warped/data/local/inference/GgufQuantizationParser.kt`

**Sub-step 1.5 — Remove llama JNI ProGuard keep rule:**
In `app/proguard-rules.pro`, delete lines 17-18:
```
# llama.cpp JNI
-keep class com.warped.data.local.inference.llama.** { native <methods>; }
```

**Sub-step 1.6 — Remove GgufQuantizationParser import from HuggingFaceScreen.kt (prep for Task 3):**
This file references `com.warped.data.local.inference.GgufQuantizationParser.formatRamBytes` on line 357. Since the class will be deleted, this line would cause a compile error. The full fix is in Task 3, but the executor should note that HuggingFaceScreen.kt line 357 references a now-deleted class — Task 3 handles this.
  </action>

  <acceptance_criteria>
    - `app/src/main/cpp/` directory does not exist: `test ! -d app/src/main/cpp/`
    - `.gitmodules` does not contain "llama.cpp": `grep -c "llama.cpp" .gitmodules` returns 0
    - `app/build.gradle.kts` does not contain "ndk": `grep -c "ndk {" app/build.gradle.kts` returns 0
    - `app/build.gradle.kts` does not contain "externalNativeBuild": `grep -c "externalNativeBuild" app/build.gradle.kts` returns 0
    - `app/build.gradle.kts` does not contain "jniLibs": `grep -c "jniLibs" app/build.gradle.kts` returns 0
    - `LlamaEngine.kt` deleted: `test ! -f app/src/main/java/com/warped/data/local/inference/LlamaEngine.kt`
    - `LlamaLoadError.kt` deleted: `test ! -f app/src/main/java/com/warped/data/local/inference/LlamaLoadError.kt`
    - `GgufMetadataParser.kt` deleted: `test ! -f app/src/main/java/com/warped/data/local/inference/GgufMetadataParser.kt`
    - `GgufQuantizationParser.kt` deleted: `test ! -f app/src/main/java/com/warped/data/local/inference/GgufQuantizationParser.kt`
    - `app/proguard-rules.pro` does not contain "llama" (case-insensitive): `grep -ci "llama" app/proguard-rules.pro` returns 0
  </acceptance_criteria>

  <verify>
    <automated>grep -c 'ndk\|externalNativeBuild\|jniLibs' app/build.gradle.kts && test ! -f app/src/main/java/com/warped/data/local/inference/LlamaEngine.kt && test ! -f app/src/main/java/com/warped/data/local/inference/GgufMetadataParser.kt && grep -ci "llama" app/proguard-rules.pro</automated>
  </verify>

  <done>
    - cpp/ directory fully deleted, submodule de-registered from git
    - build.gradle.kts has no NDK, CMake, or jniLibs configuration
    - All four Kotlin GGUF engine files deleted
    - llama JNI ProGuard rule removed
  </done>
</task>

<task type="auto">
  <name>Task 2: Simplify EngineManager, BackendDetector, InferenceModule, and dependent providers</name>
  <read_first>
    - app/src/main/java/com/warped/data/local/inference/EngineManager.kt
    - app/src/main/java/com/warped/data/local/inference/BackendDetector.kt
    - app/src/main/java/com/warped/di/InferenceModule.kt
    - app/src/main/java/com/warped/data/local/inference/LocalLlmProvider.kt
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt (lines 1-43, 404-477, 502-556)
    - app/src/main/java/com/warped/data/local/inference/MemoryChecker.kt
  </read_first>
  <files>
    app/src/main/java/com/warped/data/local/inference/EngineManager.kt
    app/src/main/java/com/warped/data/local/inference/BackendDetector.kt
    app/src/main/java/com/warped/di/InferenceModule.kt
    app/src/main/java/com/warped/data/local/inference/LocalLlmProvider.kt
    app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    app/src/main/java/com/warped/data/local/inference/MemoryChecker.kt
  </files>

  <action>
**Sub-step 2.1 — Simplify EngineManager.kt:**
Make these edits to `EngineManager.kt`:
- Line 12: Change `enum class EngineType { LLAMA_CPP, LITE_RT_LM }` to `enum class EngineType { LITE_RT_LM }`
- Line 18: Change `val backend: BackendType? = null  // null for llama.cpp (no backend concept)` to `val backend: BackendType? = null`
- Line 23: Remove `private val llamaEngine: LlamaEngine,` from the constructor (line 23). The new constructor line should be: `class EngineManager @Inject constructor(`
- Line 24: `private val liteRTLmEngine: LiteRTLmEngine,` stays (now first param)
- Delete lines 80-148 entirely: the entire `switchToLlama()` method (from `/** Switch to the llama.cpp engine...` to the closing `}` at line 148)
- Delete lines 238-239: the `getLlamaEngine()` method (`/** Returns the llama.cpp engine directly... */ fun getLlamaEngine(): LlamaEngine = llamaEngine`)
- In `unloadCurrent()` (line 154-174): Remove the `EngineType.LLAMA_CPP ->` branch (lines 161-164: `llamaEngine.stop(); llamaEngine.unload()`). Keep only the `EngineType.LITE_RT_LM ->` branch. Remove the `when` block and replace with a direct call: `liteRTLmEngine.close()`
- In `scheduleUnload()` (line 181-192): Remove the `EngineType.LLAMA_CPP -> llamaEngine.scheduleUnload()` branch (line 184). Keep only the `EngineType.LITE_RT_LM ->` branch. Remove the `when` wrapping since only one case remains.
- Remove the import `com.warped.data.local.inference.LlamaEngine` if it's still present (should be gone since file is deleted)

The resulting `EngineManager.kt` should:
- Have `EngineType` enum with only `LITE_RT_LM`
- Constructor take only `liteRTLmEngine`, `backendDetector`, `context` (no LlamaEngine)
- Keep `switchToLiteRT()`, `unloadCurrent()`, `scheduleUnload()`, `isEngineLoaded()`, `handleTrimMemory()`, `createLiteRTConversation()`, `getLiteRTLmEngine()` intact
- `getActiveEngine()`, `ActiveEngine` data class remain

**Sub-step 2.2 — Remove probeVulkan() from BackendDetector.kt:**
Delete lines 46-61: the entire `probeVulkan()` method including its KDoc comment (`/** Probe Vulkan GPU availability for llama.cpp... */` through the closing `}`).

**Sub-step 2.3 — Update InferenceModule.kt:**
- Delete lines 28-30: the `provideLlamaEngine()` function
- Delete lines 32-35: the `provideLocalLlmProvider()` function  
- Line 7: Remove `import com.warped.data.local.inference.LlamaEngine`
- Line 8: Remove `import com.warped.data.local.inference.LocalLlmProvider`
- Remove line 12: `import com.warped.data.local.inference.MemoryChecker`
- In `provideEngineManager()` (line 48): Remove the `llamaEngine: LlamaEngine,` parameter. New signature:
  ```kotlin
  fun provideEngineManager(
      liteRTLmEngine: LiteRTLmEngine,
      backendDetector: BackendDetector,
      @ApplicationContext context: Context
  ): EngineManager = EngineManager(liteRTLmEngine, backendDetector, context)
  ```
- Also add/keep `provideLiteRTLmEngine` if not present (it may be provided elsewhere). Check if there's a provider for `LiteRTLmEngine` — if not, add:
  ```kotlin
  @Provides
  @Singleton
  fun provideLiteRTLmEngine(): LiteRTLmEngine = LiteRTLmEngine()
  ```

**Sub-step 2.4 — Update LocalLlmProvider.kt:**
This file is entirely llama.cpp-based and will become dead code. Since `provideLocalLlmProvider()` is already removed from InferenceModule, this class won't be injected. But to keep the compile clean:
- Delete the entire class body but keep the file as an empty stub, OR
- Since no one injects it anymore, the file can remain but won't be used. 
The cleanest approach: remove the `llamaEngine` and `memoryChecker` constructor params and make the class a no-op. Replace line 20-21:
```kotlin
class LocalLlmProvider @Inject constructor(
    private val llamaEngine: LlamaEngine,
    private val memoryChecker: MemoryChecker
```
With:
```kotlin
@Deprecated("Use LiteRTLmProvider instead", ReplaceWith("LiteRTLmProvider"))
class LocalLlmProvider @Inject constructor()
```
And replace the entire chat(), listModels(), testConnection(), buildPrompt() methods with no-op implementations. The `type` field should be `override val type = ProviderType.LOCAL` (keep for backward compatibility). Remove imports for `LlamaEngine`, `LlamaLoadError`, `MemoryChecker`.

**Sub-step 2.5 — Update ChatViewModel.kt:**
- Line 11: Remove `import com.warped.data.local.inference.LlamaEngine`
- Line 12: Remove `import com.warped.data.local.inference.LlamaLoadError`
- Line 13: Remove `import com.warped.data.local.inference.MemoryChecker`
- Line 39: Remove `private val llamaEngine: LlamaEngine,` from the constructor
- Line 41: Remove `private val memoryChecker: MemoryChecker,` from the constructor
- Lines 413-418: In `setSelectedModel()`, remove the `ProviderType.LOCAL -> !llamaEngine.isLoaded()` branch (lines 416-417). The `needsReload` check should be:
  ```kotlin
  val needsReload = when (providerType) {
      ProviderType.LITE_RT_LM -> engineManager.getActiveEngine() == null
      else -> false
  }
  ```
- Lines 437-441: Remove the `ProviderType.LOCAL ->` unload branch (lines 437-441 in `setSelectedModel`)
- Lines 502-514: In `refreshActiveBackend()`, remove reference to `ProviderType.LOCAL`. Change `val isLocal =` line (503-504) to: `val isLocal = _uiState.value.selectedProvider == ProviderType.LITE_RT_LM`. Remove `_uiState.value.selectedProvider == ProviderType.LOCAL -> llamaEngine.isLoaded()` (line 510).
- Lines 521-556: In `preloadLocalModel()`, remove the GGUF branch (lines 542-549):
  - Remove the `else {` block that calls `engineManager.switchToLlama(filePath)` and the `LlamaLoadError` reference
  - The method should only handle .litertlm files. Remove `modelName`'s `.removeSuffix(".gguf")` call (line 536) — keep only `.removeSuffix(".litertlm")`
  - Remove the `isLitertlm` check — assume all local models are litertlm:
  ```kotlin
  engineManager.switchToLiteRT(filePath)
  ```
- Remove `modelName` derivation involving `.gguf` (line 536): change to `val modelName = filePath.substringAfterLast("/").removeSuffix(".litertlm")`

**Sub-step 2.6 — Clean MemoryChecker.kt:**
- Delete lines 46-54: the `checkGgufRam()` method
- Update the KDoc on line 56-59 to remove the "same 80% threshold as GGUF models" reference. Change comment to: `* Uses an 80% threshold for safe model loading.`

**Sub-step 2.7 — Delete unused import in HuggingFaceScreen.kt (early fix):**
- Line 357: Change `com.warped.data.local.inference.GgufQuantizationParser.formatRamBytes(...)` — this class is now deleted. Replace the entire AssistInfoChip line (356-357) with a simple size display or remove the RAM estimate line. Since Phase 24 will fully clean this file, a minimal fix: remove the `if (ggufDetail != null && ggufDetail.ramEstimateBytes > 0)` block (lines 356-358) entirely. Keep the `AssistInfoChip(text = "$modelDownloads downloads")` line.
  </action>

  <acceptance_criteria>
    - EngineManager.kt `EngineType` enum only has `LITE_RT_LM`: `grep "LITE_RT_LM" app/src/main/java/com/warped/data/local/inference/EngineManager.kt | head -1` shows the enum declaration without `LLAMA_CPP`
    - EngineManager.kt has no `switchToLlama`: `grep -c "switchToLlama" app/src/main/java/com/warped/data/local/inference/EngineManager.kt` returns 0
    - EngineManager.kt has no `getLlamaEngine`: `grep -c "getLlamaEngine" app/src/main/java/com/warped/data/local/inference/EngineManager.kt` returns 0
    - EngineManager.kt constructor has no `llamaEngine`: `grep "constructor" app/src/main/java/com/warped/data/local/inference/EngineManager.kt | grep -c "llamaEngine"` returns 0
    - BackendDetector.kt has no `probeVulkan`: `grep -c "probeVulkan" app/src/main/java/com/warped/data/local/inference/BackendDetector.kt` returns 0
    - InferenceModule.kt has no `provideLlamaEngine` or `provideLocalLlmProvider`: `grep -c "provideLlamaEngine\|provideLocalLlmProvider" app/src/main/java/com/warped/di/InferenceModule.kt` returns 0
    - ChatViewModel.kt has no `import.*LlamaEngine` or `import.*LlamaLoadError`: `grep -c "LlamaEngine\|LlamaLoadError" app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` returns 0
    - ChatViewModel.kt constructor has no `llamaEngine` or `memoryChecker`: `grep "llamaEngine\|memoryChecker" app/src/main/java/com/warped/ui/chat/ChatViewModel.kt | grep "private val" | wc -l` returns 0
    - MemoryChecker.kt has no `checkGgufRam`: `grep -c "checkGgufRam" app/src/main/java/com/warped/data/local/inference/MemoryChecker.kt` returns 0
  </acceptance_criteria>

  <verify>
    <automated>grep -c 'LLAMA_CPP\|switchToLlama\|getLlamaEngine' app/src/main/java/com/warped/data/local/inference/EngineManager.kt && grep -c 'probeVulkan' app/src/main/java/com/warped/data/local/inference/BackendDetector.kt && grep -c 'provideLlamaEngine' app/src/main/java/com/warped/di/InferenceModule.kt && grep -c 'LlamaEngine\|LlamaLoadError' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt</automated>
  </verify>

  <done>
    - EngineManager simplified to LiteRT-LM-only path
    - probeVulkan() removed from BackendDetector
    - InferenceModule no longer provides LlamaEngine or LocalLlmProvider
    - ChatViewModel no longer depends on LlamaEngine, MemoryChecker
    - LocalLlmProvider deprecated (no-op stub)
    - MemoryChecker GGUF-specific method removed
  </done>
</task>

<task type="auto">
  <name>Task 3: Update data model, clean UI GGUF references, strings, and download/import</name>
  <read_first>
    - app/src/main/java/com/warped/domain/model/LocalModel.kt
    - app/src/main/java/com/warped/data/local/db/entity/LocalModelEntity.kt
    - app/src/main/java/com/warped/data/local/db/entity/PresetEntity.kt
    - app/src/main/java/com/warped/domain/model/Preset.kt
    - app/src/main/java/com/warped/domain/model/ProviderType.kt
    - app/src/main/java/com/warped/data/local/db/Migrations.kt
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt (lines 172-186, 228-241)
    - app/src/main/java/com/warped/ui/models/ModelsScreen.kt (lines 380-401, 442-454)
    - app/src/main/java/com/warped/ui/help/HelpScreen.kt (lines 70-76, 94)
    - app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt (lines 160, 278, 315, 346-358, 410, 555-558)
    - app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt (lines 71-78, 103-127, 132)
    - app/src/main/java/com/warped/ui/huggingface/HuggingFaceUiState.kt
    - app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt (lines 118-119, 132)
    - app/src/main/java/com/warped/ui/presets/PresetsScreen.kt (lines 364-365)
    - app/src/main/java/com/warped/ui/presets/PresetsUiState.kt (line 14)
    - app/src/main/java/com/warped/ui/presets/PresetsViewModel.kt (lines 40-47)
    - app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt (lines 270-303)
    - app/src/main/java/com/warped/data/local/inference/ModelImportManager.kt (lines 27, 49-67)
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-es/strings.xml
    - app/src/main/java/com/warped/data/remote/api/HuggingFaceApi.kt
  </read_first>
  <files>
    app/src/main/java/com/warped/domain/model/LocalModel.kt
    app/src/main/java/com/warped/data/local/db/entity/LocalModelEntity.kt
    app/src/main/java/com/warped/data/local/db/entity/PresetEntity.kt
    app/src/main/java/com/warped/domain/model/Preset.kt
    app/src/main/java/com/warped/domain/model/ProviderType.kt
    app/src/main/java/com/warped/data/local/db/Migrations.kt
    app/src/main/java/com/warped/ui/chat/ChatScreen.kt
    app/src/main/java/com/warped/ui/models/ModelsScreen.kt
    app/src/main/java/com/warped/ui/help/HelpScreen.kt
    app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt
    app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt
    app/src/main/java/com/warped/ui/huggingface/HuggingFaceUiState.kt
    app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt
    app/src/main/java/com/warped/ui/presets/PresetsScreen.kt
    app/src/main/java/com/warped/ui/presets/PresetsUiState.kt
    app/src/main/java/com/warped/ui/presets/PresetsViewModel.kt
    app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt
    app/src/main/java/com/warped/data/local/inference/ModelImportManager.kt
    app/src/main/res/values/strings.xml
    app/src/main/res/values-es/strings.xml
    app/src/main/java/com/warped/data/remote/api/HuggingFaceApi.kt
  </files>

  <action>
**Sub-step 3.1 — Update data model defaults (GGUF-04):**

In `LocalModel.kt` (line 13):
- Change `val modelFormat: String = "GGUF"` to `val modelFormat: String = "LITERTLM"`

In `LocalModelEntity.kt` (line 17):
- Change `@ColumnInfo(name = "model_format", defaultValue = "GGUF") val modelFormat: String = "GGUF"` to `@ColumnInfo(name = "model_format", defaultValue = "LITERTLM") val modelFormat: String = "LITERTLM"`

In `PresetEntity.kt` (line 19):
- Change `@ColumnInfo(name = "model_format") val modelFormat: String = "GGUF",` to `@ColumnInfo(name = "model_format") val modelFormat: String = "LITERTLM",`

In `Preset.kt` (line 16):
- Change `val modelFormat: String = "GGUF",` to `val modelFormat: String = "LITERTLM",`

In `ProviderType.kt` (line 6):
- Add `@Deprecated("Use LITE_RT_LM instead", ReplaceWith("ProviderType.LITE_RT_LM"))` annotation BEFORE `LOCAL` in the enum. Change:
  ```kotlin
  enum class ProviderType { OPENAI, ANTHROPIC, OLLAMA, LM_STUDIO, CUSTOM, LOCAL, LITE_RT_LM }
  ```
  to:
  ```kotlin
  enum class ProviderType { OPENAI, ANTHROPIC, OLLAMA, LM_STUDIO, CUSTOM, @Deprecated("Use LITE_RT_LM instead") LOCAL, LITE_RT_LM }
  ```

In `Migrations.kt`:
- Line 20: Change `DEFAULT 'GGUF'` to `DEFAULT 'LITERTLM'` (MIGRATION_6_7)
- Line 40: Change `DEFAULT 'GGUF'` to `DEFAULT 'LITERTLM'` (MIGRATION_8_9)

**Sub-step 3.2 — Clean ChatScreen.kt GGUF format pills (GGUF-05):**

Remove the "GGUF" format pill in the top bar (lines 172-186):
- Delete the entire `// Format pill: GGUF or LiteRT-LM` block (lines 172-187: from the `if (isLocal) {` that contains the format pill, but keep the `isLocal` check for the "Local" type pill). The key change: remove lines 173-186 which are the format pill Surface, keeping only the type pill (lines 162-170).

In the dropdown menu (lines 228-241):
- Remove the `// Format pill` block (lines 228-241): the Surface with `formatPill` variable. Since all local models are now LiteRT-LM, the format pill is unnecessary. Remove lines 228-241 entirely.

Result: Top bar shows only "Local" type pill (no GGUF/LiteRT-LM format pill). Dropdown shows only "Local" type pill (no format pill).

**Sub-step 3.3 — Clean ModelsScreen.kt GGUF RAM check (GGUF-05):**

Remove the GGUF RAM check badge block (lines 380-401):
- Delete the entire `if (model.modelFormat == "GGUF") { ... }` block including the RamRecommendationBadge composable. This removes the RAM usage estimate that was GGUF-specific.

Update `ModelFormatBadge` composable (lines 442-454):
- Remove the GGUF case. Change the `when` block to always show "LiteRT" (green):
  ```kotlin
  val (color, label) = Color(0xFF4CAF50) to "LiteRT"
  ```
- Remove the `when` block entirely, hardcode the green color/label since only LiteRT-LM models exist.

**Sub-step 3.4 — Clean HelpScreen.kt GGUF references (GGUF-05):**

- Line 71: Change `"Tap \"Open Hugging Face\" to browse GGUF and LiteRT-LM models."` to `"Tap \"Open Hugging Face\" to browse LiteRT-LM models."`
- Line 72: Change `"Use the tabs (GGUF / LiteRT-LM / Staff Picks) to filter model formats."` to `"Use the tabs (LiteRT-LM / Staff Picks) to filter model formats."`  [NOTE: Phase 24 removes tabs entirely; this is temporary cleanup]
- Line 74: Change `"Tap a model to see its available files (GGUF quantizations or .litertlm files)."` to `"Tap a model to see its available .litertlm files."`
- Line 76: Change `"Monitor progress in the Models tab — you can pause, resume, or cancel downloads."` stays (no GGUF ref)
- Line 94: Change `"GGUF models run on CPU via llama.cpp."` to `"LiteRT-LM models run with auto-detected GPU acceleration when available."`
- Remove line 94 entirely: the GGUF-specific line should become the new line 94 describing LiteRT-LM behavior.

Actually, let's replace lines 71-76 and 94 specifically:
- Line 71: Replace "browse GGUF and LiteRT-LM models" → "browse LiteRT-LM models"
- Line 72: Replace "Use the tabs (GGUF / LiteRT-LM / Staff Picks) to filter" → "Use the tabs to filter"
- Line 74: Replace "(GGUF quantizations or .litertlm files)" → "(.litertlm files)"
- Line 94: Replace "GGUF models run on CPU via llama.cpp." → "LiteRT-LM models auto-detect the best available backend (GPU or CPU)."

**Sub-step 3.5 — Clean HuggingFaceScreen.kt GGUF references (GGUF-05):**

- Line 160: Remove the `"gguf" to "GGUF"` entry from the formats list. New list:
  ```kotlin
  val formats = listOf("staffpicks" to "Staff Picks", "litertlm" to "LiteRT-LM")
  ```
- Line 278: In `FormatBadge`, remove the GGUF case. Change:
  ```kotlin
  format.equals("gguf", ignoreCase = true) -> Color(0xFF2196F3) to "GGUF"
  ```
  Remove this line entirely. The `when` block becomes:
  ```kotlin
  val (color, label) = when {
      format.equals("litertlm", ignoreCase = true) || format.equals("staffpicks", ignoreCase = true) -> Color(0xFF4CAF50) to "LiteRT-LM"
      else -> MaterialTheme.colorScheme.outline to format
  }
  ```
- Line 315: Remove `ggufDetail: GgufFileDetail?` parameter (rename to `fileDetail: ...` or just remove — Phase 24 cleans this). For now, keep the parameter but change type to `Any? = null` — Phase 24 removes it entirely.
- Lines 346-358: Remove GGUF-specific detail cards. Delete the `ggufDetail?.quantization?.let` block (lines 346-349) and the RAM estimate block (lines 356-358, handled in Task 2 already). Keep only the basic file size and download count.
- Lines 410, 555-558: These reference `ggufFileDetails` and `GgufFileDetail` — Phase 24 cleans these. For Phase 23, minimal change: update `ggufFileDetails` parameter name to `fileDetails` on line 410, change `GgufFileDetail?` to `Any?`.

**Sub-step 3.6 — Clean HuggingFaceViewModel.kt GGUF references (GGUF-05):**

- Lines 71-78: Remove GGUF library fallback. Change the `library` derivation:
  ```kotlin
  val library = when (activeFormat) {
      "litertlm" -> "litert"
      else -> "litert"  // was "gguf" — now all searches use litert
  }
  ```
  Or simplify to just: `val library = "litert"` (always searches LiteRT-LM models). Remove the `author` differentiation (lines 75-78) — no author filter needed:
  ```kotlin
  val library = "litert"
  val author: String? = null
  ```
- Lines 103-127: Remove GGUF sibling filtering. Change the filtered siblings to always use `.litertlm`:
  ```kotlin
  val filteredSiblings = detail.siblings.filter {
      it.rfilename.endsWith(".litertlm", ignoreCase = true)
  }
  ```
  Remove the `formatFilter` variable usage. Change `fileDetails` population to not use `GgufQuantizationParser`:
  ```kotlin
  val fileDetails = sortedFiles.associate { sibling ->
      val effectiveSize = sibling.size.takeIf { it > 0 } ?: sibling.lfs?.size ?: 0L
      sibling.rfilename to GgufFileDetail(
          quantization = null,
          fileSizeBytes = effectiveSize,
          ramEstimateBytes = 0L
      )
  }
  ```
- Line 132: `ggufFileDetails` field stays (Phase 24 will rename). But remove the import for `GgufQuantizationParser` if present.

**Sub-step 3.7 — Clean HuggingFaceUiState.kt (GGUF-05):**

- Keep `GgufFileDetail` data class but mark it for removal in Phase 24. Actually, since we're removing GGUF quantization parsing, the class can stay but with only `fileSizeBytes`. Since Phase 24 will delete this, minimal change: keep as-is.
- Line 19: `ggufFileDetails` field stays for now (Phase 24 removes). No change needed.

Actually, to keep things simple and avoid breaking the HuggingFaceScreen compilation: keep `GgufFileDetail` and `ggufFileDetails` as-is in Phase 23. Only remove the GGUF quantization parsing in the ViewModel. Phase 24 does a clean sweep.

**Sub-step 3.8 — Clean ModelSelector.kt (GGUF-05):**

- Line 118-119: `formatLabel()` function: Change `if (model.modelFormat == "LITERTLM") "LiteRT-LM" else "GGUF"` to `"LiteRT-LM"` (always LiteRT-LM since all models now default to LITERTLM)
- Line 132: In `ModelTypePill`, remove the `"GGUF" -> Color(0xFF2196F3) to "GGUF"` line. Since `formatLabel` now always returns "LiteRT-LM", the "GGUF" case is unreachable.

**Sub-step 3.9 — Clean PresetsScreen.kt, PresetsUiState.kt, PresetsViewModel.kt (GGUF-05):**

In `PresetsScreen.kt`:
- Lines 364-365: Remove the GGUF case in the format badge. Change the `when` to only handle `"LITERTLM"`:
  ```kotlin
  format.equals("LITERTLM", ignoreCase = true) -> Color(0xFF4CAF50) to "LiteRT"
  ```
  Or simplify to just always show green.

In `PresetsUiState.kt`:
- Line 14: Change `val activeFormat: String = "GGUF"` to `val activeFormat: String = "LITERTLM"`

In `PresetsViewModel.kt`:
- Line 40-47: In `refreshActiveFormat()`, remove `LLAMA_CPP` case and change default:
  ```kotlin
  private fun refreshActiveFormat() {
      val format = "LITERTLM"
      _uiState.update { it.copy(activeFormat = format) }
  }
  ```
- Remove the `when (engineManager.getActiveEngine()?.type)` expression — always set to "LITERTLM".
- Remove the `import com.warped.data.local.inference.EngineType` (if present).

**Sub-step 3.10 — Clean ModelDownloadWorker.kt (GGUF-07):**

- Lines 270-283: Remove the GGUF validation block. Delete the entire `if (!isLitertlm) { ... }` block that calls `GgufMetadataParser.validateHeader(destFile)`. Since GGUF files are no longer supported, the validation is dead.
- Line 273-274: Remove the `if (!isLitertlm) {` check and the validation within.
- Lines 285-293: Remove the `if (!isLitertlm) { ... } else { ... }` block for metadata parsing. Since only .litertlm models remain, hardcode `GgufMetadata()`:
  ```kotlin
  val modelMetadata = GgufMetadata()
  ```
- Line 296: Remove `.removeSuffix(".gguf")` — change to just `.removeSuffix(".litertlm")`:
  ```kotlin
  name = localFileName.removeSuffix(".litertlm"),
  ```
- Lines 299-302: Remove GGUF-specific branching for quantization/parameterCount/architecture. Since only .litertlm:
  ```kotlin
  quantization = "N/A",
  parameterCount = "Unknown",
  architecture = "LiteRT-LM",
  modelFormat = "LITERTLM",
  ```
- Remove the now-unused `isLitertlm` variable (line 271).

**Sub-step 3.11 — Clean ModelImportManager.kt (GGUF-07):**

- Line 27: Change default filename from `"model.gguf"` to `"model.litertlm"`
- Lines 49-67: Remove GGUF metadata parsing. Change the entire metadata block to:
  ```kotlin
  val localModel = LocalModel(
      name = fileName.removeSuffix(".litertlm"),
      filePath = destFile.absolutePath,
      sizeBytes = destFile.length(),
      quantization = "N/A",
      parameterCount = "Unknown",
      architecture = "LiteRT-LM",
      modelFormat = "LITERTLM",
      importedAt = Instant.now()
  )
  ```
- Remove the `val isLitertlm = ...` line (line 47) and the `val metadata = ...` block (lines 49-54).
- Line 58: Change `.removeSuffix(".gguf").removeSuffix(".litertlm")` to just `.removeSuffix(".litertlm")`

**Sub-step 3.12 — Clean string resources (GGUF-06):**

**DO NOT modify wizard-prefixed strings** (wizard_step_2_desc, wizard_step_3_title, wizard_step_3_desc, wizard_step_3_desc_has, wizard_step_3_desc_no, wizard_badge_has_gguf, wizard_badge_no_gguf). These are for Phase 27.

In `app/src/main/res/values/strings.xml`, modify these non-wizard entries:
- Line 28: `hugging_face_suggestions` → change "GGUF suggestions" to "model suggestions"
- Line 64: `download_from_hf_desc` → change "Browse and download GGUF &amp; LiteRT-LM models" to "Browse and download LiteRT-LM models"
- Line 65: `import_gguf` → change name to `import_model` and value "Import Model File" to "Import Model File" (keep English, just remove GGUF from the name). Actually, the key name has "gguf" in it — rename the key to `import_model_file` with value "Import Model File"
- Line 66: `import_gguf_desc` → rename to `import_model_file_desc`, change value from "Load a .gguf or .litertlm model from your device" to "Load a .litertlm model from your device"
- Line 95: `provider_local` → change "Local (llama.cpp)" to "Local (legacy)" or just "Local"

In `app/src/main/res/values-es/strings.xml`, make matching changes:
- Line 28: `hugging_face_suggestions` → change "Sugerencias GGUF" to "Sugerencias de modelos"
- Line 64: `download_from_hf_desc` → change "modelos GGUF y LiteRT-LM" to "modelos LiteRT-LM"
- Line 65: `import_gguf` → rename key to `import_model_file`, value stays "Importar archivo de modelo" (already neutral)
- Line 66: `import_gguf_desc` → rename to `import_model_file_desc`, change value from "Cargar un modelo .gguf o .litertlm desde tu dispositivo" to "Cargar un modelo .litertlm desde tu dispositivo"

**CRITICAL:** After changing string resource names (`import_gguf` → `import_model_file`, `import_gguf_desc` → `import_model_file_desc`), find ALL Kotlin references to `R.string.import_gguf` and `R.string.import_gguf_desc` and update them to the new names. Use:
```bash
grep -rn "R.string.import_gguf" app/src/main/java/ --include="*.kt"
```

**Sub-step 3.13 — Clean HuggingFaceApi.kt GGUF default:**
- Line 15: Change `@Query("library") library: String? = "gguf",` to `@Query("library") library: String? = "litert",`
  </action>

  <acceptance_criteria>
    - LocalModel.kt `modelFormat` defaults to "LITERTLM": `grep 'val modelFormat: String = "LITERTLM"' app/src/main/java/com/warped/domain/model/LocalModel.kt` returns 1 match
    - LocalModelEntity.kt has `defaultValue = "LITERTLM"`: `grep 'defaultValue = "LITERTLM"' app/src/main/java/com/warped/data/local/db/entity/LocalModelEntity.kt` returns 1 match
    - PresetEntity.kt `modelFormat` defaults to "LITERTLM": `grep 'val modelFormat: String = "LITERTLM"' app/src/main/java/com/warped/data/local/db/entity/PresetEntity.kt` returns 1 match
    - Preset.kt `modelFormat` defaults to "LITERTLM": `grep 'val modelFormat: String = "LITERTLM"' app/src/main/java/com/warped/domain/model/Preset.kt` returns 1 match
    - ProviderType.LOCAL has @Deprecated: `grep -c "@Deprecated.*LOCAL" app/src/main/java/com/warped/domain/model/ProviderType.kt` returns 1
    - MIGRATION_6_7 has DEFAULT 'LITERTLM': `grep "DEFAULT 'LITERTLM'" app/src/main/java/com/warped/data/local/db/Migrations.kt | head -1` matches
    - MIGRATION_8_9 has DEFAULT 'LITERTLM': `grep "DEFAULT 'LITERTLM'" app/src/main/java/com/warped/data/local/db/Migrations.kt | tail -1` matches
    - ChatScreen.kt has no "GGUF" string in format pill: `grep -c '"GGUF"' app/src/main/java/com/warped/ui/chat/ChatScreen.kt` returns 0
    - ModelsScreen.kt has no `checkGgufRam`: `grep -c "checkGgufRam" app/src/main/java/com/warped/ui/models/ModelsScreen.kt` returns 0
    - ModelsScreen.kt ModelFormatBadge has no GGUF: `grep -c '"GGUF"' app/src/main/java/com/warped/ui/models/ModelsScreen.kt` returns 0
    - HelpScreen.kt has no "GGUF" or "llama.cpp": `grep -ci 'gguf\|llama\.cpp' app/src/main/java/com/warped/ui/help/HelpScreen.kt` returns 0
    - HuggingFaceScreen.kt formats list has no "gguf": `grep -c '"gguf"' app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt` returns 0
    - HuggingFaceViewModel.kt has no GGUF library: `grep -c '"gguf"' app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt` returns 0
    - PresetsUiState.kt activeFormat defaults to LITERTLM: `grep 'activeFormat.*LITERTLM' app/src/main/java/com/warped/ui/presets/PresetsUiState.kt` returns 1
    - PresetsViewModel.kt has no LLAMA_CPP: `grep -c "LLAMA_CPP" app/src/main/java/com/warped/ui/presets/PresetsViewModel.kt` returns 0
    - ModelDownloadWorker.kt has no GGUF validation: `grep -c "GgufMetadataParser\|validateHeader\|!isLitertlm" app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt` returns 0
    - ModelImportManager.kt has no "model.gguf": `grep -c 'model.gguf' app/src/main/java/com/warped/data/local/inference/ModelImportManager.kt` returns 0
    - ModelImportManager.kt has no GgufMetadataParser: `grep -c "GgufMetadataParser" app/src/main/java/com/warped/data/local/inference/ModelImportManager.kt` returns 0
    - strings.xml (en) has no "GGUF" or "llama.cpp" in non-wizard entries: verify manually that only wizard-prefixed strings contain GGUF
    - strings.xml (es) has no "GGUF" in non-wizard entries: same check
    - HuggingFaceApi.kt library defaults to "litert": `grep 'library.*=.*"litert"' app/src/main/java/com/warped/data/remote/api/HuggingFaceApi.kt` returns 1
  </acceptance_criteria>

  <verify>
    <automated>echo "=== Data model ===" && grep -c 'LITERTLM' app/src/main/java/com/warped/domain/model/LocalModel.kt app/src/main/java/com/warped/data/local/db/entity/LocalModelEntity.kt app/src/main/java/com/warped/data/local/db/entity/PresetEntity.kt app/src/main/java/com/warped/domain/model/Preset.kt && echo "=== UI clean ===" && grep -c '"GGUF"' app/src/main/java/com/warped/ui/chat/ChatScreen.kt app/src/main/java/com/warped/ui/models/ModelsScreen.kt app/src/main/java/com/warped/ui/help/HelpScreen.kt && echo "=== Download/Import ===" && grep -c "GgufMetadataParser\|model.gguf" app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt app/src/main/java/com/warped/data/local/inference/ModelImportManager.kt && echo "=== Strings ===" && grep -ci 'gguf\|llama\.cpp' app/src/main/res/values/strings.xml | grep -v wizard</automated>
  </verify>

  <done>
    - All data models default to LITERTLM
    - ProviderType.LOCAL deprecated
    - Migrations use DEFAULT 'LITERTLM'
    - ChatScreen, ModelsScreen, HelpScreen free of GGUF references
    - HuggingFaceScreen/ViewModel/UiState GGUF references removed
    - ModelSelector, PresetsScreen/UiState/ViewModel cleaned
    - ModelDownloadWorker only handles .litertlm
    - ModelImportManager uses .litertlm defaults
    - Non-wizard strings cleaned in EN and ES
    - HuggingFaceApi defaults to litert library
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Native code → App process | REMOVED — llama.cpp JNI bridge deleted (no more native attack surface from GGUF parsing) |
| File system → App | ModelImportManager no longer parses GGUF headers — only .litertlm binary files handled by LiteRT-LM SDK |
| Network → App | ModelDownloadWorker no longer validates GGUF headers — download trusted to HuggingFace CDN |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-23-01 | Tampering | ModelImportManager (GGUF header parsing) | mitigate | REMOVED — GGUF metadata parsing deleted from ModelImportManager; only .litertlm format handled by LiteRT-LM SDK |
| T-23-02 | Elevation of Privilege | llama.cpp JNI bridge | mitigate | REMOVED — entire cpp/ directory deleted, JNI bridge eliminated, no native code execution surface |
| T-23-03 | Information Disclosure | MemoryChecker.checkGgufRam() | mitigate | REMOVED — GGUF-specific RAM check method deleted; generic memory checking remains |
| T-23-04 | Denial of Service | ModelDownloadWorker GGUF validation | mitigate | REMOVED — GGUF file validation removed; downloaded files assumed .litertlm format |
| T-23-05 | Spoofing | LlamaEngine model loading | mitigate | REMOVED — LlamaEngine and all GGUF model loading paths deleted |
</threat_model>

<verification>
**Phase-level verification:**
1. `./gradlew assembleDebug` compiles without errors (no missing symbols from deleted files)
2. `grep -r "LlamaEngine\|GgufMetadataParser\|GgufQuantizationParser" app/src/main/java/ --include="*.kt"` returns zero results
3. `grep -r "LLAMA_CPP\|switchToLlama\|getLlamaEngine\|probeVulkan" app/src/main/java/ --include="*.kt"` returns zero results
4. `grep -r "GGUF" app/src/main/res/values/strings.xml | grep -v wizard` returns zero results
5. `grep -r '"GGUF"' app/src/main/java/com/warped/ui/ --include="*.kt" | grep -v wizard | grep -v Wizard` returns zero results
6. `grep "externalNativeBuild\|ndk {" app/build.gradle.kts` returns zero results
</verification>

<success_criteria>
1. App compiles and runs without NDK, CMake, or externalNativeBuild configuration
2. No `import com.warped.data.local.inference.LlamaEngine` or `GgufMetadataParser` references exist anywhere
3. EngineManager.kt contains no reference to `LLAMA_CPP`, `switchToLlama()`, or `probeVulkan()`
4. `modelFormat` column defaults to `"LITERTLM"` in Room schema; ProviderType.LOCAL deprecated
5. All non-wizard GGUF strings removed from `strings.xml` (en + es); app compiles without missing resource errors
</success_criteria>

<output>
After completion, create `.planning/phases/23-gguf-removal/23-01-SUMMARY.md`
</output>
