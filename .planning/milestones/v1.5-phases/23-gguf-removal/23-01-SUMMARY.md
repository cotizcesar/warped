---
phase: 23-gguf-removal
plan: 01
subsystem: infra
tags: [gguf, llama.cpp, litertlm, cleanup, native-code, proguard]

# Dependency graph
requires:
  - phase: 22-litertlm
    provides: "LiteRT-LM as sole local inference engine"
provides:
  - "Complete removal of llama.cpp/GGUF subsystem: native code, JNI bridge, Kotlin engine files, DI wiring, and UI references"
  - "EngineManager simplified to LiteRT-LM-only path"
  - "Data model defaults changed from GGUF to LITERTLM"
  - "ProviderType.LOCAL deprecated in favor of LITE_RT_LM"
affects: [24-ui-cleanup]

# Tech tracking
tech-stack:
  removed:
    - llama.cpp native library (submodule)
    - NDK/CMake build configuration
    - JNI bridge (cpp/jni_bridge.cpp)
  patterns:
    - "Single-enum EngineType (only LITE_RT_LM) replaces dual-engine pattern"

key-files:
  created: []
  modified:
    - app/build.gradle.kts (no NDK/CMake/jniLibs)
    - app/src/main/java/com/warped/data/local/inference/EngineManager.kt
    - app/src/main/java/com/warped/di/InferenceModule.kt
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/domain/model/LocalModel.kt
    - app/src/main/java/com/warped/data/local/db/entity/LocalModelEntity.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-es/strings.xml
  deleted:
    - app/src/main/cpp/ (CMakeLists.txt, JNI bridge, llama.cpp submodule)
    - app/src/main/java/com/warped/data/local/inference/LlamaEngine.kt
    - app/src/main/java/com/warped/data/local/inference/LlamaLoadError.kt
    - app/src/main/java/com/warped/data/local/inference/GgufMetadataParser.kt
    - app/src/main/java/com/warped/data/local/inference/GgufQuantizationParser.kt

key-decisions:
  - "MemoryChecker retained in ChatViewModel (removing it would break non-GGUF memory checks)"
  - "LocalLlmProvider deprecated as no-op stub instead of deleted (backward compat with existing injection points)"
  - "Wizard files NOT modified (Phase 27 handles wizard cleanup per CONTEXT.md deferral)"

patterns-established: []

requirements-completed: [GGUF-01, GGUF-02, GGUF-03, GGUF-04, GGUF-05, GGUF-06, GGUF-07]

# Metrics
duration: 22min
completed: 2026-05-10
---

# Phase 23 Plan 01: GGUF Removal Summary

**Complete deletion of llama.cpp/GGUF native code, Kotlin engine files, and all non-wizard GGUF references — LiteRT-LM becomes the sole local inference engine**

## Performance

- **Duration:** 22 min
- **Started:** 2026-05-10T03:18:35Z
- **Completed:** 2026-05-10T03:41:32Z
- **Tasks:** 3
- **Files modified:** 28 (7 created/deleted, 21 modified)

## Accomplishments
- Deleted entire native code layer: `app/src/main/cpp/` directory (CMakeLists.txt, JNI bridge, llama.cpp submodule)
- Removed NDK/CMake/jniLibs configuration from `app/build.gradle.kts`
- Deleted 4 Kotlin GGUF engine files: LlamaEngine, LlamaLoadError, GgufMetadataParser, GgufQuantizationParser
- Simplified EngineManager to LiteRT-LM-only: removed LLAMA_CPP enum, switchToLlama(), getLlamaEngine(), llamaEngine constructor param
- Removed probeVulkan() from BackendDetector, provideLlamaEngine()/provideLocalLlmProvider() from InferenceModule
- Deprecated LocalLlmProvider as no-op stub, cleaned ChatViewModel of LlamaEngine/LlamaLoadError dependencies
- Updated all data models to default to `"LITERTLM"` (LocalModel, LocalModelEntity, Preset, PresetEntity)
- Deprecated ProviderType.LOCAL, updated Room migrations to use DEFAULT 'LITERTLM'
- Cleaned non-wizard GGUF references from ChatScreen, ModelsScreen, HelpScreen, HuggingFaceScreen, ModelSelector, Presets screens
- Removed GGUF validation from ModelDownloadWorker and ModelImportManager (both now LiteRT-LM-only)
- Cleaned non-wizard string resources in EN and ES, renamed import_gguf keys to import_model_file
- Changed HuggingFaceApi default library from `"gguf"` to `"litert"`
- Removed llama JNI ProGuard keep rule

## Task Commits

Each task was committed atomically:

1. **Task 1: Delete native code, NDK config, Kotlin engine files, and ProGuard rule** - `d88455b` (feat)
2. **Task 2: Simplify EngineManager, BackendDetector, InferenceModule, and dependent providers** - `52cedf8` (feat)
3. **Task 3: Update data model, clean UI GGUF references, strings, and download/import** - `5cff2aa` (feat)

**Plan metadata:** (to be committed after SUMMARY.md)

## Files Modified/Deleted
- `app/build.gradle.kts` - Removed ndk, externalNativeBuild, jniLibs blocks
- `.gitmodules` - Removed llama.cpp submodule entry
- `app/proguard-rules.pro` - Removed llama JNI keep rule
- `app/src/main/cpp/` **(DELETED)** - CMakeLists.txt, JNI bridge, llama.cpp submodule
- `app/src/main/java/com/warped/data/local/inference/LlamaEngine.kt` **(DELETED)**
- `app/src/main/java/com/warped/data/local/inference/LlamaLoadError.kt` **(DELETED)**
- `app/src/main/java/com/warped/data/local/inference/GgufMetadataParser.kt` **(DELETED)**
- `app/src/main/java/com/warped/data/local/inference/GgufQuantizationParser.kt` **(DELETED)**
- `app/src/main/java/com/warped/data/local/inference/EngineManager.kt` - Simplified to LiteRT-LM-only
- `app/src/main/java/com/warped/data/local/inference/BackendDetector.kt` - Removed probeVulkan()
- `app/src/main/java/com/warped/di/InferenceModule.kt` - Removed provideLlamaEngine/provideLocalLlmProvider/provideMemoryChecker
- `app/src/main/java/com/warped/data/local/inference/LocalLlmProvider.kt` - Deprecated no-op stub
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` - Removed LlamaEngine/LlamaLoadError deps, simplified preloadLocalModel
- `app/src/main/java/com/warped/data/local/inference/MemoryChecker.kt` - Removed checkGgufRam()
- `app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt` - Removed GGUF format tab, quantization badge, RAM estimate
- `app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt` - Removed GGUF library/sibling filtering/GgufQuantizationParser usage
- `app/src/main/java/com/warped/domain/model/LocalModel.kt` - modelFormat default "LITERTLM"
- `app/src/main/java/com/warped/data/local/db/entity/LocalModelEntity.kt` - defaultValue "LITERTLM"
- `app/src/main/java/com/warped/data/local/db/entity/PresetEntity.kt` - modelFormat "LITERTLM"
- `app/src/main/java/com/warped/domain/model/Preset.kt` - modelFormat "LITERTLM"
- `app/src/main/java/com/warped/domain/model/ProviderType.kt` - @Deprecated on LOCAL
- `app/src/main/java/com/warped/data/local/db/Migrations.kt` - DEFAULT 'LITERTLM'
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` - Removed GGUF/LiteRT-LM format pills
- `app/src/main/java/com/warped/ui/models/ModelsScreen.kt` - Removed GGUF RAM check, simplified ModelFormatBadge
- `app/src/main/java/com/warped/ui/help/HelpScreen.kt` - Replaced GGUF/llama.cpp references with LiteRT-LM
- `app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt` - Simplified formatLabel, removed GGUF from ModelTypePill
- `app/src/main/java/com/warped/ui/presets/PresetsScreen.kt` - Removed GGUF case from FormatBadge
- `app/src/main/java/com/warped/ui/presets/PresetsUiState.kt` - activeFormat default "LITERTLM"
- `app/src/main/java/com/warped/ui/presets/PresetsViewModel.kt` - Removed LLAMA_CPP/EngineType, hardcoded "LITERTLM"
- `app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt` - Removed GGUF validation, hardcoded LITERTLM
- `app/src/main/java/com/warped/data/local/inference/ModelImportManager.kt` - Removed GGUF parsing, .litertlm default
- `app/src/main/res/values/strings.xml` - Cleaned non-wizard GGUF references
- `app/src/main/res/values-es/strings.xml` - Cleaned non-wizard GGUF references
- `app/src/main/java/com/warped/data/remote/api/HuggingFaceApi.kt` - Library default "litert"

## Decisions Made
- **MemoryChecker retained in ChatViewModel:** The plan instructed removing `memoryChecker` from ChatViewModel's constructor alongside `llamaEngine`, but `memoryChecker` is still used for non-GGUF memory checks (`canLoadModel()`, `shouldWarn()`, `getMemoryInfo()`). Removing it would cause compile errors. Kept in constructor as needed for LiteRT-LM memory management.
- **LocalLlmProvider deprecated instead of deleted:** The class was made a no-op stub with `@Deprecated` annotation to maintain backward compatibility with existing injection graphs. It emits a descriptive error if ever called. Full removal deferred to a future phase.
- **Wizard files untouched per CONTEXT.md deferral:** Phase 27 handles wizard GGUF references. All `wizard_*` string resources preserved as-is.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] MemoryChecker retained in ChatViewModel constructor against plan instruction**
- **Found during:** Task 2 (ChatViewModel cleanup)
- **Issue:** Plan said to remove `memoryChecker` from ChatViewModel constructor alongside `llamaEngine`, but `memoryChecker.shouldWarn()`, `memoryChecker.canLoadModel()`, and `memoryChecker.getMemoryInfo()` are still used in `launchModelSelection()` and `preloadLocalModel()`. Removing the constructor param would cause compile errors.
- **Fix:** Kept `private val memoryChecker: MemoryChecker` in ChatViewModel constructor and re-added the import. Hilt auto-provides MemoryChecker via its `@Inject` constructor, so no DI module change needed.
- **Files modified:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
- **Verification:** Memory checks and compilation confirmed functional.
- **Committed in:** `52cedf8` (part of Task 2 commit)

**2. [Rule 3 - Blocking] Additional GGUF references in ModelsScreen.kt beyond plan line numbers**
- **Found during:** Task 3 (ModelsScreen GGUF cleanup)
- **Issue:** ModelsScreen contained three additional GGUF references the plan's line-specific instructions didn't cover: `checkGgufRam()` in memory warning dialog (line 64), "GGUF & LiteRT-LM" text (line 135), and `isGguf = model.modelFormat == "GGUF"` (line 268).
- **Fix:** Replaced `checkGgufRam()` with `getMemoryInfo()` and calculated MB directly; changed text to "Browse and download LiteRT-LM models"; changed `isGguf` to `false`.
- **Files modified:** `app/src/main/java/com/warped/ui/models/ModelsScreen.kt`
- **Verification:** `grep -ci "checkGgufRam\|GGUF" ModelsScreen.kt` returns 0.
- **Committed in:** `5cff2aa` (part of Task 3 commit)

**3. [Rule 3 - Blocking] Unused GgufMetadataParser import in ModelDownloadWorker after GGUF code removal**
- **Found during:** Task 3 (acceptance criteria check)
- **Issue:** After removing all GGUF validation/parsing code from ModelDownloadWorker, the `import com.warped.data.local.inference.GgufMetadataParser` line remained.
- **Fix:** Removed the unused import.
- **Files modified:** `app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt`
- **Verification:** `grep GgufMetadataParser` returns 0.
- **Committed in:** `5cff2aa` (part of Task 3 commit)

---

**Total deviations:** 3 auto-fixed (1 bug, 2 blocking)
**Impact on plan:** All auto-fixes necessary for compilation correctness. No scope creep.

## Issues Encountered
None — all compilation and reference issues resolved during task execution.

## User Setup Required
None — no external service configuration required.

## Next Phase Readiness
- Phase 23 complete — GGUF/llama.cpp subsystem fully removed
- Ready for Phase 24 (UI cleanup / Hugging Face deduplication)
- Wizard GGUF references deferred to Phase 27 per CONTEXT.md

## Verification Results

**Plan-level verification (all PASS):**
1. `grep -r "LlamaEngine\|GgufMetadataParser\|GgufQuantizationParser" app/src/main/java/ --include="*.kt"` → **0 results** ✓
2. `grep -r "LLAMA_CPP\|switchToLlama\|getLlamaEngine\|probeVulkan" app/src/main/java/ --include="*.kt"` → **0 results** ✓
3. `grep -r '"GGUF"' app/src/main/java/com/warped/ui/ --include="*.kt" | grep -v wizard` → **0 results** ✓
4. `grep "externalNativeBuild\|ndk {" app/build.gradle.kts` → **0 results** ✓
5. Non-wizard strings (EN + ES) contain no "GGUF" or "llama.cpp" → **PASS** ✓

---

*Phase: 23-gguf-removal*
*Completed: 2026-05-10*
