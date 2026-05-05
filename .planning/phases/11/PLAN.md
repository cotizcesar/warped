# Phase 11: Native Foundation — Plan

**Created:** 2026-05-05
**Status:** Ready to execute
**Requirements:** NTVL-01, NTVL-05, HFDL-01, HFDL-02, HFDL-03, HFDL-04, MEMS-04

## Plan Overview

| Plan | Task | Requirements | Type |
|------|------|-------------|------|
| 11.1 | llama.cpp CMake Build & JNI Library | NTVL-01 | Build/Infrastructure |
| 11.2 | ProGuard/R8 Keep Rules for JNI | NTVL-05 | Build/Security |
| 11.3 | Hugging Face GGUF Quantization & RAM Display | HFDL-01, HFDL-02 | UI/Feature |
| 11.4 | GGUF Download Validation & Integrity | HFDL-03, HFDL-04, MEMS-04 | Feature/Hardening |

---

### Plan 11.1: llama.cpp CMake Build & JNI Library

**Goal:** llama.cpp compiles from source via CMake+NDK for arm64-v8a and x86_64, producing `libwarped_llama.so` with real llama.cpp symbols.

**Files to create/modify:**
1. `app/src/main/cpp/CMakeLists.txt` — Rewrite to integrate llama.cpp source as object libraries + link into `libwarped_llama.so`
2. `app/build.gradle.kts` — Verify/extend `externalNativeBuild` block with correct NDK version and ABI filters
3. `app/src/main/cpp/llama.cpp/` — Git submodule (added via `.gitmodules`)

**Implementation steps:**
1. Add llama.cpp as git submodule at `app/src/main/cpp/llama.cpp/`
2. Rewrite `CMakeLists.txt`:
   - Set C++17 standard
   - Add llama.cpp source files as an OBJECT library (ggml + llama + ggml-cpu)
   - Set GGML compile definitions: `GGML_USE_CPU=1`, `GGML_USE_CPU_AARCH64=1`, `GGML_USE_CPU_X86_64=1`
   - Add include directories for llama.cpp and ggml
   - Link the OBJECT library + android + log into `libwarped_llama.so`
   - Set max-page-size=16384 for 4KB alignment compatibility
3. Verify `build.gradle.kts`:
   - Check `externalNativeBuild { cmake { path "src/main/cpp/CMakeLists.txt" } }` block exists
   - Add `abiFilters "arm64-v8a", "x86_64"` to `ndk { }` block
   - Set NDK version to 27.x
4. Build: `./gradlew assembleDebug` must succeed
5. Verify symbols: Use `readelf -s` or `nm` to confirm llama_model_load, llama_create_context, etc. exist in .so

**Acceptance criteria:**
- `./gradlew assembleDebug` produces `libwarped_llama.so` for arm64-v8a and x86_64
- `.so` contains real llama.cpp symbols (not just JNI stubs)
- No build warnings or linker errors
- Git submodule is tracked in `.gitmodules`

---

### Plan 11.2: ProGuard/R8 Keep Rules for JNI

**Goal:** Release builds preserve all JNI callback methods in the `com.warped.data.local.inference.llama` package.

**Files to modify:**
1. `app/proguard-rules.pro` — Add llama.cpp JNI keep rule section

**Implementation steps:**
1. Add to `proguard-rules.pro`:
   ```
   # llama.cpp JNI
   -keep class com.warped.data.local.inference.llama.** { native <methods>; }
   ```
2. Verify with a release build (`./gradlew assembleRelease`)
3. Check that JNI symbols in the `.so` match kept method names in the APK's dex

**Acceptance criteria:**
- Release build succeeds with `minifyEnabled = true`
- No `NoSuchMethodError` at runtime when calling JNI methods (validated in Phase 12)
- Rule is grouped under `# llama.cpp JNI` section, adjacent to `# LiteRT-LM` rule

---

### Plan 11.3: Hugging Face GGUF Quantization & RAM Display

**Goal:** Users browsing Hugging Face models see quantization type parsed from .gguf filenames and estimated RAM requirements per file.

**Files to create/modify:**
1. `app/src/main/java/com/warped/data/local/inference/GgufQuantizationParser.kt` — NEW: Parse quantization type from GGUF filenames
2. `app/src/main/java/com/warped/ui/huggingface/HuggingFaceUiState.kt` — Modify: Add `ggufFileDetails` map
3. `app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt` — Modify: Parse quantization when model detail loads
4. `app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt` — Modify: Update `SiblingFileCard` to show quantization and RAM estimate

**Implementation steps:**
1. Create `GgufQuantizationParser.kt`:
   ```kotlin
   object GgufQuantizationParser {
       fun parseQuantization(filename: String): String? {
           val regex = Regex("""(Q[2-8]_[KMS]|Q[2-8]_0|Q[2-8]_1|F16|F32|IQ[2-4]_\w+)""")
           return regex.find(filename)?.value
       }
       fun estimateRamBytes(fileSizeBytes: Long): Long = (fileSizeBytes * 1.3).toLong()
   }
   ```
2. Add `GgufFileDetail` data class and `ggufFileDetails` field to `HuggingFaceUiState`:
   ```kotlin
   data class GgufFileDetail(
       val filename: String,
       val quantization: String?,
       val fileSizeBytes: Long,
       val ramEstimateBytes: Long
   )
   ```
3. In `HuggingFaceViewModel.selectModel()`, after filtering siblings:
   - For each GGUF sibling, parse quantization and compute RAM estimate
   - Store in `ggufFileDetails` map keyed by `rfilename`
4. In `HuggingFaceScreen.kt`, update `SiblingFileCard`:
   - Add quantization badge (colored chip like `FormatBadge`)
   - Add RAM estimate text: "~{size} RAM needed"
   - Show warning icon if RAM estimate exceeds device available RAM

**Acceptance criteria:**
- GGUF files show quantization type (Q4_K_M, Q5_K_M, etc.) in model detail view
- RAM estimate displayed per file as "~X.X GB RAM needed"
- Non-GGUF files show "N/A" for quantization (or hide the field)
- UI layout is consistent with existing Material 3 patterns in the file

---

### Plan 11.4: GGUF Download Validation & Integrity

**Goal:** All downloaded .gguf files are validated post-download. Corrupted files are auto-deleted with user notification.

**Files to modify:**
1. `app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt` — Add post-download GGUF validation
2. `app/src/main/java/com/warped/data/local/inference/GgufMetadataParser.kt` — Add lightweight header validation method

**Implementation steps:**
1. Add `GgufMetadataParser.validateHeader(file: File): Result<Boolean>` — validates:
   - File exists and is non-empty
   - First 4 bytes == "GGUF" (magic number)
   - Version field is valid integer (1-3 for GGUF)
   - KV count + tensor count are non-negative and header doesn't exceed file size
2. In `ModelDownloadWorker.doWork()`, after download completes but before `saveModel()`:
   - If the file is `.gguf`, call `GgufMetadataParser.validateHeader(destFile)`
   - If validation fails: delete the file, delete checkpoint, return `Result.failure()` with error "Corrupted download — file validation failed. Please try again."
   - If validation passes: proceed with existing metadata parsing and `saveModel()`
3. The existing `WorkInfo.State.FAILED` handling in `ModelDownloadManager.observeWorkProgress()` already surfaces the error to UI via `downloadError`

**Acceptance criteria:**
- Valid GGUF files download and appear in model list (existing behavior preserved)
- Corrupted .gguf files (wrong magic, truncated header) are deleted and not added to model list
- User sees a clear error message in the UI: "Corrupted download — file validation failed. Please try again."
- Non-GGUF files (.litertlm) bypass GGUF validation and save normally

---

## Dependency Order

```
11.1 (CMake build) ──► 11.2 (ProGuard)
     │
     ▼
11.3 (UI quantization display) ── can run in parallel with 11.1+11.2
     │
     ▼
11.4 (Download validation) ── depends on 11.1 (GgufMetadataParser already exists)
```

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|-----------|
| llama.cpp submodule fails to clone | Cannot build native code | Fallback: download source archive via Gradle task |
| CMake build fails on CI | Blocks all GGUF work | Test locally first; CMakeLists.txt kept simple |
| GGUF filename quantization regex misses edge cases | Some files show "N/A" quantization | Comprehensive regex; fallback to "GGUF" label |
| ProGuard keeps too much (APK size) | Minor | Package-level rule is narrow enough |
