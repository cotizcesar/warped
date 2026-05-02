---
phase: 08-model-acquisition
plan: GAP-02
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt
autonomous: true
requirements: [ACQ-06]
gap_closure: true

must_haves:
  truths:
    - "User can search for .litertlm models from the litert-community and see only text-capable models (filtered from vision/speech)"
    - "Search results exclude models with vision/speech pipeline_tags (e.g., 'image-to-text', 'automatic-speech-recognition')"
    - "Models with pipelineTag='text-generation' or empty pipelineTag are included (litert-community text models often have no tag)"
    - "GGUF search results are unaffected by the pipeline_tag filter"
  artifacts:
    - path: "app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt"
      provides: "search() filters results by pipelineTag to exclude vision/speech models"
      contains: "pipelineTag"
  key_links:
    - from: "HuggingFaceViewModel.search() → result.onSuccess { models }"
      to: "HuggingFaceModel.pipelineTag"
      via: "filter { model -> model.pipelineTag !in EXCLUDED_PIPELINE_TAGS }"
      pattern: "pipelineTag.*filter"
    - from: "HuggingFaceModel DTO"
      to: "ViewModel filtering logic"
      via: "HuggingFaceModel.pipelineTag: String field"
      pattern: "pipelineTag"
---

<objective>
Filter Hugging Face search results to exclude vision/speech models, ensuring only text-capable models appear when searching for litert-community models.

Purpose: Currently, searching with `filter=litertlm` returns ALL litert-community models including vision (`image-to-text`) and speech (`automatic-speech-recognition`) models. These are unusable in Warped v1.1 (vision/audio backends deferred to v2.x). The `HuggingFaceModel.pipelineTag` field already exists in the DTO but is never used for filtering — adding client-side filtering gives users a clean, text-only model list.

Output: `HuggingFaceViewModel.search()` filters search results by `pipelineTag`, excluding known vision/speech tags. GGUF search path is unaffected.
</objective>

<execution_context>
@$HOME/.config/opencode/get-shit-done/workflows/execute-plan.md
@$HOME/.config/opencode/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/REQUIREMENTS.md
@.planning/phases/08-model-acquisition/08-VERIFICATION.md
@.planning/phases/08-model-acquisition/08-03-SUMMARY.md

<interfaces>
<!-- Key types and contracts the executor needs. Extracted from codebase. -->

From `app/src/main/java/com/warped/data/remote/dto/HuggingFaceDtos.kt` (lines 7-19):
```kotlin
@Serializable
data class HuggingFaceModel(
    val id: String = "",
    @SerialName("modelId") val modelIdAlias: String? = null,
    val author: String = "",
    val tags: List<String> = emptyList(),
    val downloads: Int = 0,
    val likes: Int = 0,
    @SerialName("pipeline_tag") val pipelineTag: String = "",  // <-- AVAILABLE BUT UNUSED
    @SerialName("private") val isPrivate: Boolean = false,
    val gated: Boolean = false,
    val lastModified: String = "",
    val siblings: List<HuggingFaceSibling> = emptyList()
)
```

From `app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt` (lines 61-89):
```kotlin
fun search(query: String) {
    val trimmedQuery = query.trim()
    if (trimmedQuery.length < MIN_SEARCH_LENGTH) return
    searchJob?.cancel()
    _uiState.update { it.copy(searchQuery = trimmedQuery, isLoading = true, error = null) }
    searchJob = viewModelScope.launch {
        val activeFormat = _uiState.value.activeFormat
        val result = huggingFaceRepository.searchModels(
            query = trimmedQuery,
            format = activeFormat
        )
        result.onSuccess { models ->
            val compatibility = loadCompatibility(models)
            val sortedModels = models.sortedWith(
                compareByDescending<HuggingFaceModel> { compatibility[it.id] == true }
                    .thenByDescending { it.downloads }
            )
            _uiState.update {
                it.copy(
                    searchResults = sortedModels,
                    compatibilityByModelId = compatibility,
                    isLoading = false
                )
            }
        }.onFailure { e ->
            _uiState.update { it.copy(error = e.message, isLoading = false) }
        }
    }
}
```

**Gap context from 08-VERIFICATION.md (Gap #3):**
- `HuggingFaceModel` has `pipelineTag` field (line 15) but ViewModel doesn't use it
- Search returns all litert-community models including vision/image-to-text ones
- Need client-side filtering in ViewModel

**Known Hugging Face pipeline_tag values for vision/speech models:**
- `"image-to-text"` — vision (image captioning, visual QA)
- `"automatic-speech-recognition"` — speech (ASR)
- `"text-to-speech"` — speech (TTS)
- `"image-classification"` — vision
- `"object-detection"` — vision
- `"image-segmentation"` — vision
- `"audio-classification"` — speech/audio
- `"image-text-to-text"` — multi-modal (vision + text input)
- `"visual-question-answering"` — vision

**Known text-only pipeline_tag values (should be included):**
- `"text-generation"` — text LLM (what we want)
- `""` (empty/blank) — litert-community models often have no pipeline_tag set (default is text)
- `"conversational"` — chat models
- `"text-classification"` — text (rare in litert-community)
- `"fill-mask"` — text (BERT-style, rare)

**Design decision:** Whitelist approach is risky — new pipeline_tags could be missed. Use a blacklist approach: exclude known vision/speech tags, include everything else. This is safer because new text-only tags won't be accidentally excluded, and litert-community models with blank pipelineTag (common) pass through.
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Filter search results by pipelineTag to exclude vision/speech models</name>
  <files>app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt</files>
  <action>
Add client-side filtering in `HuggingFaceViewModel.search()` to exclude models with vision or speech `pipeline_tag` values.

**Implementation:**

**1. Add companion object constant with excluded pipeline tags:**

In the existing `private companion object` block (line 209), add:
```kotlin
/**
 * Pipeline tags for vision/speech models that are not usable in Warped v1.1.
 * These model types require vision/audio backends deferred to v2.x.
 * Blacklist approach: exclude known non-text tags; include everything else
 * (handles models with blank pipelineTag, which is common in litert-community).
 */
private val EXCLUDED_PIPELINE_TAGS = setOf(
    "image-to-text",
    "automatic-speech-recognition",
    "text-to-speech",
    "image-classification",
    "object-detection",
    "image-segmentation",
    "audio-classification",
    "image-text-to-text",
    "visual-question-answering",
    "text-to-image",
    "zero-shot-image-classification",
    "zero-shot-object-detection",
    "image-feature-extraction",
    "video-classification",
    "depth-estimation"
)
```

**2. Add filtering step in `search()` method:**

In the `search()` method, after `result.onSuccess { models ->` (around line 72), add a filter step BEFORE sorting. Insert between `val compatibility = loadCompatibility(models)` and `val sortedModels = models.sortedWith(...)`:

```kotlin
result.onSuccess { models ->
    // Filter out vision/speech models — only show text-capable models
    val textModels = if (_uiState.value.activeFormat == "litertlm") {
        models.filter { model ->
            model.pipelineTag.isBlank() || model.pipelineTag !in EXCLUDED_PIPELINE_TAGS
        }
    } else {
        models // GGUF search: don't filter (GGUF format implies text model)
    }

    val compatibility = loadCompatibility(textModels)
    val sortedModels = textModels.sortedWith(
        compareByDescending<HuggingFaceModel> { compatibility[it.id] == true }
            .thenByDescending { it.downloads }
    )
    _uiState.update {
        it.copy(
            searchResults = sortedModels,
            compatibilityByModelId = compatibility,
            isLoading = false
        )
    }
}
```

**Key design decisions:**
- **Blacklist, not whitelist:** Excluding known bad tags is safer than including known good tags. New text-only pipeline_tag values won't be accidentally excluded.
- **Blank pipelineTag passes through:** Most litert-community text models don't set `pipeline_tag`, so `model.pipelineTag.isBlank()` → true → included. This is by far the most common case.
- **Only filter litertlm search:** The filter is gated on `activeFormat == "litertlm"`. GGUF models are inherently text-generation (GGUF format implies LLM), and filtering GGUF results by pipelineTag would incorrectly exclude models that happen to have non-text tags from their HF metadata.
- **Filter BEFORE compatibility checking:** We don't want to spend time checking memory compatibility for models we're going to exclude anyway. `loadCompatibility()` receives `textModels`, not the full list.
- **Filter BEFORE sorting:** Sorting vision models by compatibility is wasted work. Filter first, then sort the remaining text models.

**Impact:**
- Litert-community searches will show fewer results (vision/speech models excluded).
- GGUF searches are completely unaffected (filter is gated on activeFormat).
- The HuggingFaceViewModel.search() method grows by ~6 lines (filter block).
- No DTO changes needed. No new dependencies. No Room changes.
- PipelineTag field in `HuggingFaceModel` DTO is already correctly mapped from the HuggingFace API response via Kotlinx Serialization (`@SerialName("pipeline_tag")`).
</action>
  <verify>
    <automated>./gradlew :app:compileDebugKotlin 2>&1 | tail -20</automated>
  </verify>
  <done>HuggingFaceViewModel.search() filters litertlm search results by pipelineTag, excluding vision/speech models while including text-generation and blank-tag models; GGUF search path unaffected; project compiles without errors</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Hugging Face API → ViewModel | pipeline_tag value comes from external API. No new trust boundary — already deserialized by Kotlinx Serialization into a String field. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-GAP-02-01 | Denial of Service | HuggingFaceViewModel.search() — blacklist bypass | accept | A future HF model could have a new vision/speech pipeline_tag not in the blacklist, causing it to appear in search results. Risk accepted: the model would still fail to load in chat because LiteRT-LM can't interpret vision models. The user would see an error message, not a crash. The blacklist can be extended in a follow-up if new vision tags emerge. |
| T-GAP-02-02 | Information Disclosure | pipelineTag filtering — no new exposure | accept | Filtering is purely subtractive (removing models from results). No new data flows or user-visible strings are added. |
</threat_model>

<verification>
- `./gradlew :app:compileDebugKotlin` exits 0
- `grep -c "pipelineTag" app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt` returns >= 2 (EXCLUDED_PIPELINE_TAGS constant + filter usage)
- `grep -c "EXCLUDED_PIPELINE_TAGS" app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt` returns >= 2 (definition + usage)
- `grep -c "activeFormat" app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt` returns >= 6 (existing 5 usages + new gate for litertlm-only filtering)
</verification>

<success_criteria>
1. `HuggingFaceViewModel.search()` filters litertlm search results by `pipelineTag` — models with vision/speech tags excluded
2. Models with `pipelineTag="text-generation"` are included
3. Models with blank/empty `pipelineTag` are included (common for litert-community text models)
4. GGUF search results are not filtered by `pipelineTag` (filter is gated on `activeFormat == "litertlm"`)
5. Filter is applied before compatibility checking and sorting (performance optimization)
6. Project compiles successfully with `./gradlew :app:compileDebugKotlin`
</success_criteria>

<output>
After completion, create `.planning/phases/08-model-acquisition/08-GAP-02-SUMMARY.md`
</output>
