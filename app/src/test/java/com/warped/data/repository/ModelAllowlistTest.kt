package com.warped.data.repository

import android.content.Context
import android.content.res.AssetManager
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

/**
 * 45-02 LRT-09: allowlist asset + repository verification.
 *
 * The shipped asset is parsed directly (not a copy) so this test guards the file
 * RUNTIME-05 requires to ship. Verified-only rule: MTP / extended-context / thinking /
 * function-calling must stay false until device-verified on 0.17.x.
 */
class ModelAllowlistTest {

    private fun shippedAssetText(): String =
        java.io.File("src/main/assets/model_allowlist.json").readText()

    private fun repositoryBackedBy(raw: String): ModelAllowlistRepository {
        val assets = mockk<AssetManager>()
        every { assets.open("model_allowlist.json") } returns ByteArrayInputStream(raw.toByteArray())
        val context = mockk<Context>()
        every { context.assets } returns assets
        return ModelAllowlistRepository(context)
    }

    @Test
    fun `shipped asset parses with expected entries`() {
        val models = parseModelAllowlist(shippedAssetText())

        assertThat(models).hasSize(2)
        val e2b = models.first { it.name == "gemma-3n-E2B-it-int4" }
        assertThat(e2b.displayName).isEqualTo("Gemma 3n E2B IT (int4)")
        assertThat(e2b.modelFile).isEqualTo("gemma-3n-E2B-it-int4.task")
        assertThat(e2b.sizeInBytes).isEqualTo(3136226711L)
        assertThat(e2b.taskTypes).contains("chat")
    }

    @Test
    fun `shipped asset flags are verified-only`() {
        val models = parseModelAllowlist(shippedAssetText())

        for (model in models) {
            val caps = model.capabilities
            // Engine-surface verified on 0.17.x Android (AAR bytecode + app code paths).
            assertThat(caps.text).isTrue()
            assertThat(caps.vision).isTrue()
            assertThat(caps.audio).isTrue()
            assertThat(caps.speculativeDecoding).isTrue()
            // NOT per-model verified on device — must stay off (T-45-06, D-allowlist).
            assertThat(caps.supportsThinking).isFalse()
            assertThat(caps.supportsFunctionCalling).isFalse()
            assertThat(caps.extendedContext).isFalse()
            assertThat(caps.mtpSupport).isFalse()
        }
    }

    @Test
    fun `repository exposes capability queries`() {
        val repo = repositoryBackedBy(shippedAssetText())

        assertThat(repo.models).hasSize(2)
        assertThat(repo.findByModelFile("gemma-3n-E4B-it-int4.task")?.name)
            .isEqualTo("gemma-3n-E4B-it-int4")
        assertThat(repo.supportsThinking("gemma-3n-E2B-it-int4")).isFalse()
        assertThat(repo.supportsFunctionCalling("gemma-3n-E2B-it-int4")).isFalse()
        assertThat(repo.supportsSpeculativeDecoding("gemma-3n-E2B-it-int4")).isTrue()
        assertThat(repo.supportsExtendedContext("gemma-3n-E2B-it-int4")).isFalse()
        assertThat(repo.supportsMtp("gemma-3n-E2B-it-int4")).isFalse()
        assertThat(repo.supportsModality("gemma-3n-E2B-it-int4", "vision")).isTrue()
        assertThat(repo.supportsModality("gemma-3n-E2B-it-int4", "audio")).isTrue()
        assertThat(repo.supportsModality("gemma-3n-E2B-it-int4", "text")).isTrue()
        assertThat(repo.supportsModality("gemma-3n-E2B-it-int4", "video")).isFalse()
        assertThat(repo.supportsThinking("unknown-model")).isFalse()
    }

    @Test
    fun `repository returns empty list when asset is missing`() {
        val assets = mockk<AssetManager>()
        every { assets.open(any()) } throws java.io.FileNotFoundException("missing")
        val context = mockk<Context>()
        every { context.assets } returns assets
        val repo = ModelAllowlistRepository(context)

        assertThat(repo.models).isEmpty()
    }

    @Test
    fun `parser tolerates unknown future keys`() {
        val raw = """{"models": [{"name": "x", "displayName": "X", "modelFile": "x.task",
            "sizeInBytes": 1, "futureField": {"nested": true}, "capabilities": {}}]}"""
        val models = parseModelAllowlist(raw)

        assertThat(models).hasSize(1)
        assertThat(models[0].name).isEqualTo("x")
    }
}
