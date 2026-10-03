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

        assertThat(models).hasSize(64)
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
        val g1b = models.first { it.name == "gemma-3-1b-it" }
        assertThat(g1b.displayName).isEqualTo("Gemma 3 1B IT")
        assertThat(g1b.modelFile).isEqualTo("gemma-3-1b-it.litertlm")
        assertThat(g1b.sizeInBytes).isEqualTo(584417280L)
        assertThat(g1b.repo).isEqualTo("warped-community/gemma-3-1b-it-litert-lm")
        assertThat(g1b.taskTypes).contains("chat")
        val g270m = models.first { it.name == "gemma-3-270m-it" }
        assertThat(g270m.displayName).isEqualTo("Gemma 3 270M IT")
        assertThat(g270m.modelFile).isEqualTo("gemma-3-270m-it.litertlm")
        assertThat(g270m.sizeInBytes).isEqualTo(304005120L)
        assertThat(g270m.repo).isEqualTo("warped-community/gemma-3-270m-it-litert-lm")
        assertThat(g270m.taskTypes).contains("chat")
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
        assertThat(byName["gemma-3-1b-it"]?.ramNote).isEqualTo("From ~2 GB RAM")
        assertThat(byName["gemma-3-1b-it"]?.blurb).isEqualTo("Fast lightweight chat.")
        assertThat(byName["gemma-3-270m-it"]?.ramNote).isEqualTo("From ~1 GB RAM")
        assertThat(byName["gemma-3-270m-it"]?.blurb).isEqualTo("Tiny model for any device.")
    }

    @Test
    fun `shipped asset catalog order is locked`() {
        val models = parseModelAllowlist(shippedAssetText())

        assertThat(models.map { it.name }).containsExactly(
            "gemma-4-E2B-it",
            "gemma-4-E4B-it",
            "gemma-3n-E2B-it-int4",
            "gemma-3n-E4B-it-int4",
            "gemma-3-1b-it",
            "gemma-3-270m-it",
            "qwen3-1.7b",
            "qwen3-4b",
            "qwen2-vl-2b",
            "qwen3-4b-thinking",
            "qwen2.5-coder-3b",
            "qwen3-0.6b",
            "qwen2.5-1.5b",
            "qwen3-8b",
            "qwen3.5-2b-vl",
            "qwen3.5-4b",
            "gemma3-4b-it",
            "gemma3-12b-it",
            "gemma-3n-E2B-official",
            "smolvlm2-500m",
            "llava-ov-0.5b",
            "qwen3-4b-instruct",
            "deepseek-r1-1.5b",
            "ministral-3-3b",
            "phi-4-mini",
            "smolllm2-360m",
            "smolllm3-3b",
            "tinyllama-1.1b",
            "olmo-2-1b",
            "internvl3_5-1b",
            "ovis2.5-2b",
            "smolvlm2-2.2b",
            "qwen2.5-0.5b",
            "smolllm2-135m",
            "qwen3-14b",
            "qwen2-0.5b",
            "parakeet-tdt-0.6b",
            "qwen3-asr-0.6b",
            "moonshine-tiny",
            "whisper-tiny",
            "kokoro-82m",
            "matcha-tts",
            "qwen3-emb-0.6b",
            "qwen3-rerank-0.6b",
            "paddleocr-vl",
            "flux-klein-4b",
            "z-image-turbo",
            "llama-3.2-1b",
            "llama-3.2-3b",
            "medgemma-1.5-4b",
            "embeddinggemma-300m",
            "functiongemma-270m",
            "gemma-4-12B-it",
            "lfm2.5-1.2b-instruct",
            "lfm2.5-1.2b-thinking",
            "lfm2.5-230m",
            "qwen3.5-0.8b",
            "qwen3-0.6b-int4-thinking",
            "qwen2.5-coder-1.5b",
            "ministral-3-3b-reasoning",
            "phi-4-mini-reasoning",
            "fastvlm-0.5b",
            "lfm2.5-vl-450m",
            "granite-4.0-350m",
        ).inOrder()
    }

    @Test
    fun `comingSoon flags parse with back-compat defaults`() {
        val models = parseModelAllowlist(shippedAssetText()).associateBy { it.name }
        // Regular entries default to downloadable.
        assertThat(models.getValue("qwen3-4b").comingSoon).isFalse()
        assertThat(models.getValue("qwen3-4b").comingSoonNote).isNull()
        // Coming-soon entries carry the flag + reason, no actions.
        val parakeet = models.getValue("parakeet-tdt-0.6b")
        assertThat(parakeet.comingSoon).isTrue()
        assertThat(parakeet.comingSoonNote)
            .isEqualTo("Needs on-device speech recognition.")
        assertThat(parakeet.comingSoonNoteEs)
            .isEqualTo("Necesita reconocimiento de voz en el dispositivo.")
        // Locale-aware resolution (both directions, global state restored).
        val previousLocale = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale("es"))
            assertThat(parakeet.localizedComingSoonNote())
                .isEqualTo("Necesita reconocimiento de voz en el dispositivo.")
            java.util.Locale.setDefault(java.util.Locale.ENGLISH)
            assertThat(parakeet.localizedComingSoonNote())
                .isEqualTo("Needs on-device speech recognition.")
        } finally {
            java.util.Locale.setDefault(previousLocale)
        }
        // Old assets lacking the keys entirely still parse.
        val legacy = parseModelAllowlist(
            """{"models": [{"name": "x", "displayName": "X", "modelFile": "x.task",
            "sizeInBytes": 1, "capabilities": {}}]}"""
        )
        assertThat(legacy.single().comingSoon).isFalse()
        assertThat(legacy.single().comingSoonNote).isNull()
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
            "gemma-4-E4B-it" to true,
            "gemma-3-1b-it" to true,
            "gemma-3-270m-it" to true,
            "qwen3-1.7b" to true,
            "qwen3-4b" to true,
            "qwen2-vl-2b" to true,
            "qwen3-4b-thinking" to true,
            "qwen2.5-coder-3b" to true,
            "qwen3-0.6b" to true,
            "qwen2.5-1.5b" to true,
            "qwen3-8b" to true,
            "qwen3.5-2b-vl" to true,
            "qwen3.5-4b" to true,
            "gemma3-4b-it" to true,
            "gemma3-12b-it" to true,
            "gemma-3n-E2B-official" to true,
            "smolvlm2-500m" to true,

            "llava-ov-0.5b" to true,

            "qwen3-4b-instruct" to true,

            "deepseek-r1-1.5b" to true,

            "ministral-3-3b" to true,

            "phi-4-mini" to true,

            "smolllm2-360m" to true,

            "smolllm3-3b" to true,

            "tinyllama-1.1b" to true,

            "olmo-2-1b" to true,

            "internvl3_5-1b" to true,

            "ovis2.5-2b" to true,

            "smolvlm2-2.2b" to true,

            "qwen2.5-0.5b" to true,

            "smolllm2-135m" to true,

            "qwen3-14b" to true,

            "qwen2-0.5b" to true,

            "parakeet-tdt-0.6b" to false,

            "qwen3-asr-0.6b" to false,

            "moonshine-tiny" to false,

            "whisper-tiny" to false,

            "kokoro-82m" to false,

            "matcha-tts" to false,

            "qwen3-emb-0.6b" to false,

            "qwen3-rerank-0.6b" to false,

            "paddleocr-vl" to false,

            "flux-klein-4b" to false,

            "z-image-turbo" to false,

            "llama-3.2-1b" to true,

            "llama-3.2-3b" to true,

            "medgemma-1.5-4b" to true,

            "embeddinggemma-300m" to false,

            "functiongemma-270m" to false,
            "gemma-4-12B-it" to true,

            "lfm2.5-1.2b-instruct" to true,

            "lfm2.5-1.2b-thinking" to true,

            "lfm2.5-230m" to true,

            "qwen3.5-0.8b" to true,

            "qwen3-0.6b-int4-thinking" to true,

            "qwen2.5-coder-1.5b" to true,

            "ministral-3-3b-reasoning" to true,

            "phi-4-mini-reasoning" to true,

            "fastvlm-0.5b" to true,

            "lfm2.5-vl-450m" to true,

            "granite-4.0-350m" to true,

        )
        // 3n multimodal verified; gemma-4 vision/audio docs-verified,
        // thinking docs-verified. Speculative decoding aligned to the
        // engine report 2026-10-03 (litert-lm describe per file):
        // gemma-4 pair true, everything else false (3n docs claim
        // reverted — engine says NO).
        // Qwen wave (2026-10-03): vision and audio split into separate
        // maps — VLMs carry vision WITHOUT audio (the old shared map
        // forced vision==audio per model, which no VLM satisfies).
        // Vision docs-verified via image-text-to-text pipeline_tag + vlm
        // tags; thinking docs-verified via reasoning/thinking tags.
        val expectedVision = mapOf(
            "gemma-3n-E2B-it-int4" to true,
            "gemma-3n-E4B-it-int4" to true,
            "gemma-4-E2B-it" to true,
            "gemma-4-E4B-it" to true,
            "gemma-3-1b-it" to false,
            "gemma-3-270m-it" to false,
            "qwen3-1.7b" to false,
            "qwen3-4b" to false,
            "qwen2-vl-2b" to true,
            "qwen3-4b-thinking" to false,
            "qwen2.5-coder-3b" to false,
            "qwen3-0.6b" to false,
            "qwen2.5-1.5b" to false,
            "qwen3-8b" to false,
            "qwen3.5-2b-vl" to true,
            "qwen3.5-4b" to false,
            "gemma3-4b-it" to true,
            "gemma3-12b-it" to true,
            "gemma-3n-E2B-official" to true,
            "smolvlm2-500m" to true,

            "llava-ov-0.5b" to true,

            "qwen3-4b-instruct" to false,

            "deepseek-r1-1.5b" to false,

            "ministral-3-3b" to false,

            "phi-4-mini" to false,

            "smolllm2-360m" to false,

            "smolllm3-3b" to false,

            "tinyllama-1.1b" to false,

            "olmo-2-1b" to false,

            "internvl3_5-1b" to true,

            "ovis2.5-2b" to true,

            "smolvlm2-2.2b" to true,

            "qwen2.5-0.5b" to false,

            "smolllm2-135m" to false,

            "qwen3-14b" to false,

            "qwen2-0.5b" to false,

            "parakeet-tdt-0.6b" to false,

            "qwen3-asr-0.6b" to false,

            "moonshine-tiny" to false,

            "whisper-tiny" to false,

            "kokoro-82m" to false,

            "matcha-tts" to false,

            "qwen3-emb-0.6b" to false,

            "qwen3-rerank-0.6b" to false,

            "paddleocr-vl" to false,

            "flux-klein-4b" to false,

            "z-image-turbo" to false,

            "llama-3.2-1b" to false,

            "llama-3.2-3b" to false,

            "medgemma-1.5-4b" to true,

            "embeddinggemma-300m" to false,

            "functiongemma-270m" to false,
            "gemma-4-12B-it" to true,

            "lfm2.5-1.2b-instruct" to false,

            "lfm2.5-1.2b-thinking" to false,

            "lfm2.5-230m" to false,

            "qwen3.5-0.8b" to false,

            "qwen3-0.6b-int4-thinking" to false,

            "qwen2.5-coder-1.5b" to false,

            "ministral-3-3b-reasoning" to false,

            "phi-4-mini-reasoning" to false,

            "fastvlm-0.5b" to true,

            "lfm2.5-vl-450m" to true,

            "granite-4.0-350m" to false,

        )
        val expectedAudio = mapOf(
            "gemma-3n-E2B-it-int4" to true,
            "gemma-3n-E4B-it-int4" to true,
            "gemma-4-E2B-it" to true,
            "gemma-4-E4B-it" to true,
            "gemma-3-1b-it" to false,
            "gemma-3-270m-it" to false,
            "qwen3-1.7b" to false,
            "qwen3-4b" to false,
            "qwen2-vl-2b" to false,
            "qwen3-4b-thinking" to false,
            "qwen2.5-coder-3b" to false,
            "qwen3-0.6b" to false,
            "qwen2.5-1.5b" to false,
            "qwen3-8b" to false,
            "qwen3.5-2b-vl" to false,
            "qwen3.5-4b" to false,
            "gemma3-4b-it" to false,
            "gemma3-12b-it" to false,
            "gemma-3n-E2B-official" to true,
            "smolvlm2-500m" to false,

            "llava-ov-0.5b" to false,

            "qwen3-4b-instruct" to false,

            "deepseek-r1-1.5b" to false,

            "ministral-3-3b" to false,

            "phi-4-mini" to false,

            "smolllm2-360m" to false,

            "smolllm3-3b" to false,

            "tinyllama-1.1b" to false,

            "olmo-2-1b" to false,

            "internvl3_5-1b" to false,

            "ovis2.5-2b" to false,

            "smolvlm2-2.2b" to false,

            "qwen2.5-0.5b" to false,

            "smolllm2-135m" to false,

            "qwen3-14b" to false,

            "qwen2-0.5b" to false,

            "parakeet-tdt-0.6b" to false,

            "qwen3-asr-0.6b" to false,

            "moonshine-tiny" to false,

            "whisper-tiny" to false,

            "kokoro-82m" to false,

            "matcha-tts" to false,

            "qwen3-emb-0.6b" to false,

            "qwen3-rerank-0.6b" to false,

            "paddleocr-vl" to false,

            "flux-klein-4b" to false,

            "z-image-turbo" to false,

            "llama-3.2-1b" to false,

            "llama-3.2-3b" to false,

            "medgemma-1.5-4b" to false,

            "embeddinggemma-300m" to false,

            "functiongemma-270m" to false,
            "gemma-4-12B-it" to true,

            "lfm2.5-1.2b-instruct" to false,

            "lfm2.5-1.2b-thinking" to false,

            "lfm2.5-230m" to false,

            "qwen3.5-0.8b" to false,

            "qwen3-0.6b-int4-thinking" to false,

            "qwen2.5-coder-1.5b" to false,

            "ministral-3-3b-reasoning" to false,

            "phi-4-mini-reasoning" to false,

            "fastvlm-0.5b" to false,

            "lfm2.5-vl-450m" to false,

            "granite-4.0-350m" to false,

        )
        val expectedSpeculativeDecoding = mapOf(
            "gemma-3n-E2B-it-int4" to false,
            "gemma-3n-E4B-it-int4" to false,
            "gemma-4-E2B-it" to true,
            "gemma-4-E4B-it" to true,
            "gemma-3-1b-it" to false,
            "gemma-3-270m-it" to false,
            "gemma-4-12B-it" to true
        )
        val expectedThinking = mapOf(
            "gemma-3n-E2B-it-int4" to false,
            "gemma-3n-E4B-it-int4" to false,
            "gemma-4-E2B-it" to true,
            "gemma-4-E4B-it" to true,
            "gemma-3-1b-it" to false,
            "gemma-3-270m-it" to false,
            "qwen3-1.7b" to false,
            "qwen3-4b" to false,
            "qwen2-vl-2b" to false,
            "qwen3-4b-thinking" to true,
            "qwen2.5-coder-3b" to false,
            "qwen3-0.6b" to false,
            "qwen2.5-1.5b" to false,
            "qwen3-8b" to false,
            "qwen3.5-2b-vl" to false,
            "qwen3.5-4b" to false,
            "gemma3-4b-it" to false,
            "gemma3-12b-it" to false,
            "gemma-3n-E2B-official" to false,
            "smolvlm2-500m" to false,

            "llava-ov-0.5b" to false,

            "qwen3-4b-instruct" to false,

            "deepseek-r1-1.5b" to true,

            "ministral-3-3b" to false,

            "phi-4-mini" to false,

            "smolllm2-360m" to false,

            "smolllm3-3b" to true,

            "tinyllama-1.1b" to false,

            "olmo-2-1b" to false,

            "internvl3_5-1b" to false,

            "ovis2.5-2b" to false,

            "smolvlm2-2.2b" to false,

            "qwen2.5-0.5b" to false,

            "smolllm2-135m" to false,

            "qwen3-14b" to false,

            "qwen2-0.5b" to false,

            "parakeet-tdt-0.6b" to false,

            "qwen3-asr-0.6b" to false,

            "moonshine-tiny" to false,

            "whisper-tiny" to false,

            "kokoro-82m" to false,

            "matcha-tts" to false,

            "qwen3-emb-0.6b" to false,

            "qwen3-rerank-0.6b" to false,

            "paddleocr-vl" to false,

            "flux-klein-4b" to false,

            "z-image-turbo" to false,

            "llama-3.2-1b" to false,

            "llama-3.2-3b" to false,

            "medgemma-1.5-4b" to false,

            "embeddinggemma-300m" to false,

            "functiongemma-270m" to false,
            "gemma-4-12B-it" to false,

            "lfm2.5-1.2b-instruct" to false,

            "lfm2.5-1.2b-thinking" to true,

            "lfm2.5-230m" to false,

            "qwen3.5-0.8b" to false,

            "qwen3-0.6b-int4-thinking" to true,

            "qwen2.5-coder-1.5b" to false,

            "ministral-3-3b-reasoning" to true,

            "phi-4-mini-reasoning" to true,

            "fastvlm-0.5b" to false,

            "lfm2.5-vl-450m" to false,

            "granite-4.0-350m" to false,

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
            "gemma-4-E4B-it" to true,
            "gemma-3-1b-it" to false,
            "gemma-3-270m-it" to false,
            "qwen3-1.7b" to false,
            "qwen3-4b" to false,
            "qwen2-vl-2b" to false,
            "qwen3-4b-thinking" to false,
            "qwen2.5-coder-3b" to false,
            "qwen3-0.6b" to false,
            "qwen2.5-1.5b" to false,
            "qwen3-8b" to false,
            "qwen3.5-2b-vl" to false,
            "qwen3.5-4b" to false,
            "gemma3-4b-it" to false,
            "gemma3-12b-it" to false,
            "gemma-3n-E2B-official" to false,
            "smolvlm2-500m" to false,

            "llava-ov-0.5b" to false,

            "qwen3-4b-instruct" to false,

            "deepseek-r1-1.5b" to false,

            "ministral-3-3b" to false,

            "phi-4-mini" to false,

            "smolllm2-360m" to false,

            "smolllm3-3b" to false,

            "tinyllama-1.1b" to false,

            "olmo-2-1b" to false,

            "internvl3_5-1b" to false,

            "ovis2.5-2b" to false,

            "smolvlm2-2.2b" to false,

            "qwen2.5-0.5b" to false,

            "smolllm2-135m" to false,

            "qwen3-14b" to false,

            "qwen2-0.5b" to false,

            "parakeet-tdt-0.6b" to false,

            "qwen3-asr-0.6b" to false,

            "moonshine-tiny" to false,

            "whisper-tiny" to false,

            "kokoro-82m" to false,

            "matcha-tts" to false,

            "qwen3-emb-0.6b" to false,

            "qwen3-rerank-0.6b" to false,

            "paddleocr-vl" to false,

            "flux-klein-4b" to false,

            "z-image-turbo" to false,

            "llama-3.2-1b" to false,

            "llama-3.2-3b" to false,

            "medgemma-1.5-4b" to false,

            "embeddinggemma-300m" to false,

            "functiongemma-270m" to false,
            "gemma-4-12B-it" to false,

            "lfm2.5-1.2b-instruct" to false,

            "lfm2.5-1.2b-thinking" to false,

            "lfm2.5-230m" to false,

            "qwen3.5-0.8b" to false,

            "qwen3-0.6b-int4-thinking" to false,

            "qwen2.5-coder-1.5b" to false,

            "ministral-3-3b-reasoning" to false,

            "phi-4-mini-reasoning" to false,

            "fastvlm-0.5b" to false,

            "lfm2.5-vl-450m" to false,

            "granite-4.0-350m" to false,

        )
        for (model in models) {
            val caps = model.capabilities
            assertThat(caps.text).isEqualTo(expectedTextModality[model.name] == true)
            assertThat(caps.vision).isEqualTo(expectedVision[model.name] == true)
            assertThat(caps.audio).isEqualTo(expectedAudio[model.name] == true)
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

        assertThat(repo.models).hasSize(64)
        assertThat(repo.findByModelFile("gemma-3n-E4B-it-int4.litertlm")?.name)
            .isEqualTo("gemma-3n-E4B-it-int4")
        assertThat(repo.findByModelFile("gemma-4-E4B-it.litertlm")?.name)
            .isEqualTo("gemma-4-E4B-it")
        assertThat(repo.supportsThinking("gemma-4-E4B-it")).isTrue()
        assertThat(repo.supportsFunctionCalling("gemma-4-E4B-it")).isTrue()
        assertThat(repo.supportsFunctionCalling("gemma-4-E2B-it")).isTrue()
        assertThat(repo.supportsSpeculativeDecoding("gemma-4-E4B-it")).isTrue()
        assertThat(repo.supportsSpeculativeDecoding("gemma-4-E2B-it")).isTrue()
        assertThat(repo.supportsThinking("gemma-3n-E2B-it-int4")).isFalse()
        assertThat(repo.supportsFunctionCalling("gemma-3n-E2B-it-int4")).isFalse()
        assertThat(repo.supportsFunctionCalling("gemma-3n-E4B-it-int4")).isFalse()
        assertThat(repo.supportsSpeculativeDecoding("gemma-3n-E2B-it-int4")).isFalse()
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

    @Test
    fun `shipped asset modality flags match device evidence`() {
        // gemma-3-270m-it PROVEN text-only on-device 2026-10-01
        // (NOT_FOUND TF_LITE_AUDIO_ENCODER_HW + TF_LITE_VISION_ENCODER);
        // gemma-3-1b-it is text-only by the same family design. Both stay
        // false so the chat hides image/audio/thinking affordances and the
        // catalog shows no modality badges for them.
        val byName = parseModelAllowlist(shippedAssetText()).associateBy { it.name }
        val tiny = byName.getValue("gemma-3-270m-it").capabilities
        assertThat(tiny.text).isTrue()
        assertThat(tiny.vision).isFalse()
        assertThat(tiny.audio).isFalse()
        assertThat(tiny.supportsThinking).isFalse()
        val oneB = byName.getValue("gemma-3-1b-it").capabilities
        assertThat(oneB.text).isTrue()
        assertThat(oneB.vision).isFalse()
        assertThat(oneB.audio).isFalse()
        assertThat(oneB.supportsThinking).isFalse()
        // Multimodal entries keep their verified flags.
        val e2b = byName.getValue("gemma-4-E2B-it").capabilities
        assertThat(e2b.vision).isTrue()
        assertThat(e2b.audio).isTrue()
        assertThat(e2b.supportsThinking).isTrue()
        val n3e2b = byName.getValue("gemma-3n-E2B-it-int4").capabilities
        assertThat(n3e2b.vision).isTrue()
        assertThat(n3e2b.audio).isTrue()
        assertThat(n3e2b.supportsThinking).isFalse()
        // Wave 5 (CLI describe + smoke 2026-10-03, /tmp/opencode/wave5.log):
        // thinking = in-band trace observed ([thought]/<think>); vision =
        // describe Input Modalities; gemma-12B spec = describe YES.
        val g12 = byName.getValue("gemma-4-12B-it").capabilities
        assertThat(g12.vision).isTrue()
        assertThat(g12.audio).isTrue()
        assertThat(g12.speculativeDecoding).isTrue()
        assertThat(g12.supportsThinking).isFalse()
        assertThat(byName.getValue("lfm2.5-1.2b-thinking").capabilities.supportsThinking).isTrue()
        assertThat(byName.getValue("qwen3-0.6b-int4-thinking").capabilities.supportsThinking).isTrue()
        assertThat(byName.getValue("ministral-3-3b-reasoning").capabilities.supportsThinking).isTrue()
        assertThat(byName.getValue("phi-4-mini-reasoning").capabilities.supportsThinking).isTrue()
        assertThat(byName.getValue("fastvlm-0.5b").capabilities.vision).isTrue()
        assertThat(byName.getValue("lfm2.5-vl-450m").capabilities.vision).isTrue()
        assertThat(byName.getValue("granite-4.0-350m").capabilities.supportsThinking).isFalse()
    }

    @Test
    fun `recommended curation is usable and downloadable`() {
        val models = parseModelAllowlist(shippedAssetText())
        val recommended = models.filter { it.recommended }

        // User curation 2026-10-03: Recommended is the Gemma 4 trio only.
        assertThat(recommended.map { it.name }).containsExactly(
            "gemma-4-E2B-it",
            "gemma-4-E4B-it",
            "gemma-4-12B-it",
        )
        // Every pick must be installable — a coming-soon entry can never
        // back the Recommended section (display-only by design).
        assertThat(recommended.none { it.comingSoon }).isTrue()
        // Every pick resolves to a real download (explicit repo slug).
        for (entry in recommended) {
            assertThat(entry.repo).isNotNull()
            assertThat(entry.repoSlug).isNotEmpty()
        }
    }

    @Test
    fun `recommended defaults to false for hand-built entries`() {
        val entry = AllowlistedModel(
            name = "x",
            displayName = "X",
            modelFile = "x.litertlm",
            sizeInBytes = 1L,
        )

        assertThat(entry.recommended).isFalse()
    }
}
