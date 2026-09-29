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
 * RUNTIME-05 requires to ship. Verified-only rule: MTP / extended-context
 * must stay false until device-verified on 0.17.x; function-calling is true
 * for the gemma-4 pair ONLY per the 56-01 FLAG DECISION (docs basis, device
 * confirmation pending — reverts if no ToolCall emission on-device) and
 * false for both 3n entries. gemma-4-E2B-it vision/audio/thinking are
 * docs-verified (Google official Gemma 4 docs 2026-09-28, device
 * confirmation pending).
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

        assertThat(models).hasSize(4)
        val e2b = models.first { it.name == "gemma-3n-E2B-it-int4" }
        assertThat(e2b.displayName).isEqualTo("Gemma 3n E2B IT (int4)")
        assertThat(e2b.modelFile).isEqualTo("gemma-3n-E2B-it-int4.litertlm")
        assertThat(e2b.sizeInBytes).isEqualTo(3655827456L)
        assertThat(e2b.taskTypes).contains("chat")
        val g4 = models.first { it.name == "gemma-4-E2B-it" }
        assertThat(g4.displayName).isEqualTo("Gemma 4 E2B IT")
        assertThat(g4.modelFile).isEqualTo("gemma-4-E2B-it.litertlm")
        assertThat(g4.sizeInBytes).isEqualTo(2588147712L)
        assertThat(g4.taskTypes).contains("chat")
        val e4b = models.first { it.name == "gemma-4-E4B-it" }
        assertThat(e4b.displayName).isEqualTo("Gemma 4 E4B IT")
        assertThat(e4b.modelFile).isEqualTo("gemma-4-E4B-it.litertlm")
        assertThat(e4b.sizeInBytes).isEqualTo(3659530240L)
        assertThat(e4b.repo).isEqualTo("warped-community/gemma-4-E4B-it-litert-lm")
        assertThat(e4b.taskTypes).contains("chat")
    }

    @Test
    fun `shipped 3n entries pin litertlm file mapping`() {
        val models = parseModelAllowlist(shippedAssetText())
        val byName = models.associateBy { it.name }

        val e2b = byName["gemma-3n-E2B-it-int4"]
        assertThat(e2b?.repo).isEqualTo("warped-community/gemma-3n-E2B-it-litert-lm")
        assertThat(e2b?.modelFile).isEqualTo("gemma-3n-E2B-it-int4.litertlm")
        assertThat(e2b?.sizeInBytes).isEqualTo(3655827456L)

        val e4b = byName["gemma-3n-E4B-it-int4"]
        assertThat(e4b?.repo).isEqualTo("warped-community/gemma-3n-E4B-it-litert-lm")
        assertThat(e4b?.modelFile).isEqualTo("gemma-3n-E4B-it-int4.litertlm")
        assertThat(e4b?.sizeInBytes).isEqualTo(4919541760L)
    }

    @Test
    fun `shipped asset ramNote and blurb match locked English strings`() {
        val models = parseModelAllowlist(shippedAssetText())
        val byName = models.associateBy { it.name }

        assertThat(byName["gemma-4-E2B-it"]?.ramNote).isEqualTo("From ~4 GB RAM")
        assertThat(byName["gemma-4-E2B-it"]?.blurb).isEqualTo("Light general chat and multimodal.")
        assertThat(byName["gemma-4-E4B-it"]?.ramNote).isEqualTo("6 GB or more recommended")
        assertThat(byName["gemma-4-E4B-it"]?.blurb)
            .isEqualTo("Better reasoning and code quality, multimodal.")
        assertThat(byName["gemma-3n-E2B-it-int4"]?.ramNote)
            .isEqualTo("From ~6 GB RAM (approx.)")
        assertThat(byName["gemma-3n-E2B-it-int4"]?.blurb)
            .isEqualTo("Efficient chat with vision and audio.")
        assertThat(byName["gemma-3n-E4B-it-int4"]?.ramNote)
            .isEqualTo("8 GB or more recommended (approx.)")
        assertThat(byName["gemma-3n-E4B-it-int4"]?.blurb).isEqualTo("Higher multimodal quality.")
    }

    @Test
    fun `shipped asset catalog order is locked`() {
        val models = parseModelAllowlist(shippedAssetText())

        assertThat(models.map { it.name }).containsExactly(
            "gemma-4-E2B-it",
            "gemma-4-E4B-it",
            "gemma-3n-E2B-it-int4",
            "gemma-3n-E4B-it-int4"
        ).inOrder()
    }

    @Test
    fun `missing ramNote and blurb parse to nulls without exception`() {
        val raw = """{"models": [{"name": "x", "displayName": "X", "modelFile": "x.task",
            "sizeInBytes": 1, "capabilities": {}}]}"""
        val models = parseModelAllowlist(raw)

        assertThat(models).hasSize(1)
        assertThat(models[0].ramNote).isNull()
        assertThat(models[0].blurb).isNull()
    }

    @Test
    fun `shipped entries carry explicit repo slugs`() {
        val models = parseModelAllowlist(shippedAssetText())

        for (model in models) {
            assertThat(model.repo).isNotNull()
            assertThat(model.repoSlug).endsWith("-litert-lm")
            assertThat(model.repoSlug).isEqualTo(model.repo)
        }
    }

    @Test
    fun `repoSlug falls back to legacy slug when repo is absent`() {
        val legacy = AllowlistedModel(
            name = "some-legacy-model",
            displayName = "Some Legacy Model",
            modelFile = "some-legacy-model.litertlm",
            sizeInBytes = 1L
        )
        assertThat(legacy.repoSlug).isEqualTo("warped-community/some-legacy-model")

        val blank = legacy.copy(repo = "  ")
        assertThat(blank.repoSlug).isEqualTo("warped-community/some-legacy-model")
    }

    @Test
    fun `shipped asset flags are verified-only`() {
        val models = parseModelAllowlist(shippedAssetText())

        // Per-model verified surface on 0.17.x Android. Unverified stays off
        // (T-45-06, D-allowlist): function-calling / extended-context / MTP
        // must be false on EVERY entry until device-verified. gemma-4-E2B-it
        // thinking is docs-verified (Google official Gemma 4 docs 2026-09-28,
        // device confirmation pending); 3n thinking stays off.
        val expectedTextModality = mapOf(
            "gemma-3n-E2B-it-int4" to true,
            "gemma-3n-E4B-it-int4" to true,
            "gemma-4-E2B-it" to true,
            "gemma-4-E4B-it" to true
        )
        // 3n multimodal + speculative decoding verified; gemma-4
        // vision/audio docs-verified, thinking docs-verified. E2B
        // speculative decoding still unverified (stays false); E4B
        // speculative decoding docs-verified true.
        val expectedVisionAudio = mapOf(
            "gemma-3n-E2B-it-int4" to true,
            "gemma-3n-E4B-it-int4" to true,
            "gemma-4-E2B-it" to true,
            "gemma-4-E4B-it" to true
        )
        val expectedSpeculativeDecoding = mapOf(
            "gemma-3n-E2B-it-int4" to true,
            "gemma-3n-E4B-it-int4" to true,
            "gemma-4-E2B-it" to false,
            "gemma-4-E4B-it" to true
        )
        val expectedThinking = mapOf(
            "gemma-3n-E2B-it-int4" to false,
            "gemma-3n-E4B-it-int4" to false,
            "gemma-4-E2B-it" to true,
            "gemma-4-E4B-it" to true
        )
        // 56-01 FLAG DECISION: supportsFunctionCalling true for the gemma-4
        // pair ONLY (docs basis: Gemma 4 model card built-in function
        // calling + E4B chat_template <|tool|> blocks + LiteRT-LM docs
        // Gemma 4 tool support; device confirmation pending — reverts if
        // no ToolCall emission on-device). 3n pair stays false (no
        // evidence either way, verified-only defaults closed).
        val expectedFunctionCalling = mapOf(
            "gemma-3n-E2B-it-int4" to false,
            "gemma-3n-E4B-it-int4" to false,
            "gemma-4-E2B-it" to true,
            "gemma-4-E4B-it" to true
        )
        for (model in models) {
            val caps = model.capabilities
            assertThat(caps.text).isEqualTo(expectedTextModality[model.name] == true)
            assertThat(caps.vision).isEqualTo(expectedVisionAudio[model.name] == true)
            assertThat(caps.audio).isEqualTo(expectedVisionAudio[model.name] == true)
            assertThat(caps.speculativeDecoding).isEqualTo(expectedSpeculativeDecoding[model.name] == true)
            assertThat(caps.supportsThinking).isEqualTo(expectedThinking[model.name] == true)
            assertThat(caps.supportsFunctionCalling).isEqualTo(expectedFunctionCalling[model.name] == true)
            assertThat(caps.extendedContext).isFalse()
            assertThat(caps.mtpSupport).isFalse()
        }
    }

    @Test
    fun `repository exposes capability queries`() {
        val repo = repositoryBackedBy(shippedAssetText())

        assertThat(repo.models).hasSize(4)
        assertThat(repo.findByModelFile("gemma-3n-E4B-it-int4.litertlm")?.name)
            .isEqualTo("gemma-3n-E4B-it-int4")
        assertThat(repo.findByModelFile("gemma-4-E4B-it.litertlm")?.name)
            .isEqualTo("gemma-4-E4B-it")
        assertThat(repo.supportsThinking("gemma-4-E4B-it")).isTrue()
        assertThat(repo.supportsFunctionCalling("gemma-4-E4B-it")).isTrue()
        assertThat(repo.supportsFunctionCalling("gemma-4-E2B-it")).isTrue()
        assertThat(repo.supportsSpeculativeDecoding("gemma-4-E4B-it")).isTrue()
        assertThat(repo.supportsThinking("gemma-3n-E2B-it-int4")).isFalse()
        assertThat(repo.supportsFunctionCalling("gemma-3n-E2B-it-int4")).isFalse()
        assertThat(repo.supportsFunctionCalling("gemma-3n-E4B-it-int4")).isFalse()
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

    @Test
    fun `effectiveCapabilities prefers allowlist and gates thinking`() {
        val repo = repositoryBackedBy(shippedAssetText())
        fun local(name: String, file: String) = com.warped.domain.model.LocalModel(
            name = name,
            filePath = "/data/models/$file",
            sizeBytes = 1L,
            quantization = "N/A",
            parameterCount = "Unknown",
            architecture = "x",
            importedAt = java.time.Instant.EPOCH
        )

        // Allowlisted gemma-4-E2B-it: docs-verified vision/audio/thinking
        // (Google official Gemma 4 docs 2026-09-28, device confirmation
        // pending); function-calling true per the 56-01 FLAG DECISION
        // (docs basis, device confirmation pending) so the Tools badge
        // shows and the plan-02 loop arms on this model.
        val e2b = repo.effectiveCapabilities(local("gemma-4-E2B-it", "gemma-4-E2B-it.litertlm"))
        assertThat(e2b.reasoning).isTrue()
        assertThat(e2b.vision).isTrue()
        assertThat(e2b.tools).isTrue()

        // Allowlisted 3n keeps its verified vision/audio.
        val n3 = repo.effectiveCapabilities(local("gemma-3n-E2B-it-int4", "gemma-3n-E2B-it-int4.litertlm"))
        assertThat(n3.vision).isTrue()
        assertThat(n3.reasoning).isFalse()

        // Allowlisted E4B: docs-verified vision/audio/thinking +
        // function-calling (56-01 FLAG DECISION) — Tools badge on.
        val e4b = repo.effectiveCapabilities(local("gemma-4-E4B-it", "gemma-4-E4B-it.litertlm"))
        assertThat(e4b.reasoning).isTrue()
        assertThat(e4b.vision).isTrue()
        assertThat(e4b.audio).isTrue()
        assertThat(e4b.tools).isTrue()

        // Unlisted model: stored caps except thinking (opt-in only).
        val other = repo.effectiveCapabilities(local("some-future-model", "some-future-model.litertlm"))
        assertThat(other.vision).isTrue()
        assertThat(other.reasoning).isFalse()
    }
}
