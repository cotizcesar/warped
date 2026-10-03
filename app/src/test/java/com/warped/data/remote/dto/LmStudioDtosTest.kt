package com.warped.data.remote.dto

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class LmStudioDtosTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun `parses LM Studio v1 REST response from real server`() {
        val raw = java.io.File("src/test/resources/lmstudio_models.json").readText()
        val response = json.decodeFromString<LmStudioModelListResponse>(raw)

        assertThat(response.models).hasSize(3)
        assertThat(response.data).isEmpty()

        val first = response.models[0]
        assertThat(first.key).isEqualTo("google/gemma-4-12b-qat")
        assertThat(first.displayName).isEqualTo("Gemma 4 12B Qat")
        assertThat(first.type).isEqualTo("llm")
        assertThat(first.publisher).isEqualTo("google")
        assertThat(first.architecture).isEqualTo("gemma4")
        assertThat(first.quantization?.name).isEqualTo("Q4_0")
        assertThat(first.quantization?.bitsPerWeight).isEqualTo(4.0)
        assertThat(first.capabilities?.vision).isTrue()
        assertThat(first.capabilities?.trainedForToolUse).isTrue()
        assertThat(first.sizeBytes).isEqualTo(7151066820L)
        assertThat(first.paramsString).isEqualTo("12B")
        assertThat(first.maxContextLength).isEqualTo(262144)
        assertThat(first.format).isEqualTo("gguf")
    }

    @Test
    fun `parses embedding model without capabilities`() {
        val raw = java.io.File("src/test/resources/lmstudio_models.json").readText()
        val response = json.decodeFromString<LmStudioModelListResponse>(raw)
        val embedding = response.models.first { it.type == "embedding" }
        assertThat(embedding.key).isEqualTo("text-embedding-nomic-embed-text-v1.5")
        assertThat(embedding.capabilities).isNull()
        assertThat(embedding.architecture).isNull()
    }

    @Test
    fun `parses loaded_instances to tell loaded from available`() {
        // Docs: GET /api/v1/models lists AVAILABLE models — only a
        // non-empty loaded_instances means loaded. The golden file has
        // one loaded, one available-but-idle, one embedding.
        val raw = java.io.File("src/test/resources/lmstudio_models.json").readText()
        val response = json.decodeFromString<LmStudioModelListResponse>(raw)

        val loaded = response.models.first { it.key == "google/gemma-4-12b-qat" }
        assertThat(loaded.loadedInstances).hasSize(1)
        assertThat(loaded.loadedInstances.single().id).isEqualTo("google/gemma-4-12b-qat")

        val idle = response.models.first { it.key == "google/gemma-4-12b" }
        assertThat(idle.loadedInstances).isEmpty()

        val embedding = response.models.first { it.type == "embedding" }
        assertThat(embedding.loadedInstances).isEmpty()
    }
}
