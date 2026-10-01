package com.warped.ui.huggingface

import android.content.Context
import android.content.res.AssetManager
import com.google.common.truth.Truth.assertThat
import com.warped.data.local.download.ModelDownloadManager
import com.warped.data.repository.AllowlistedModel
import com.warped.data.repository.ModelAllowlistRepository
import com.warped.data.repository.parseModelAllowlist
import com.warped.domain.repository.LocalModelRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

/**
 * Catalog download URL pinning (quick fix 2026-09-28).
 *
 * Short-name URLs (`warped-community/<name>/resolve/...`) 404 — real mirror
 * repos carry a `-litert-lm` suffix. Every catalog download must go through
 * the entry's explicit `repo` slug; the legacy `warped-community/<name>`
 * construction survives only as a fallback for repo-less entries.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogDownloadUrlTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun shippedAssetText(): String =
        java.io.File("src/main/assets/model_allowlist.json").readText()

    private fun allowlistRepository(): ModelAllowlistRepository {
        val assets = mockk<AssetManager>()
        every { assets.open("model_allowlist.json") } returns
            ByteArrayInputStream(shippedAssetText().toByteArray())
        val context = mockk<Context>()
        every { context.assets } returns assets
        return ModelAllowlistRepository(context)
    }

    private data class CapturedDownload(
        val modelId: String,
        val fileName: String,
        val fileUrl: String,
        val fileSizeBytes: Long,
        val isGated: Boolean
    )

    private fun buildViewModel(
        repository: ModelAllowlistRepository = allowlistRepository()
    ): Pair<CatalogViewModel, MutableList<CapturedDownload>> {
        val downloadManager = mockk<ModelDownloadManager>()
        every { downloadManager.downloadStates } returns MutableStateFlow(emptyMap())
        val localRepo = mockk<LocalModelRepository>(relaxed = true)
        every { localRepo.observeModels() } returns MutableStateFlow(emptyList())
        val captured = mutableListOf<CapturedDownload>()
        every {
            downloadManager.startDownload(any(), any(), any(), any(), any())
        } answers {
            captured.add(
                CapturedDownload(
                    modelId = firstArg(),
                    fileName = secondArg(),
                    fileUrl = thirdArg(),
                    fileSizeBytes = arg(3),
                    isGated = arg(4)
                )
            )
        }
        return CatalogViewModel(repository, downloadManager, localRepo, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)) to captured
    }

    @Test
    fun `E4B download resolves to litert-lm mirror with verified size`() {
        val (viewModel, captured) = buildViewModel()
        val e4b = viewModel.models.first { it.name == "gemma-4-E4B-it" }

        viewModel.startDownload(e4b)

        assertThat(captured).hasSize(1)
        val download = captured.single()
        assertThat(download.fileUrl).isEqualTo(
            "https://huggingface.co/warped-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm"
        )
        assertThat(download.fileSizeBytes).isEqualTo(3659530240L)
        assertThat(download.modelId).isEqualTo(
            "warped-community/gemma-4-E4B-it-litert-lm/gemma-4-E4B-it.litertlm"
        )
        assertThat(download.isGated).isFalse()
        assertThat(viewModel.downloadId(e4b)).isEqualTo(download.modelId)
    }

    @Test
    fun `E2B download uses litert-lm suffix not short name`() {
        val (viewModel, captured) = buildViewModel()
        val e2b = viewModel.models.first { it.name == "gemma-4-E2B-it" }

        viewModel.startDownload(e2b)

        val download = captured.single()
        assertThat(download.fileUrl).isEqualTo(
            "https://huggingface.co/warped-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        )
        assertThat(download.fileUrl).doesNotContain("E2B-it/resolve")
    }

    @Test
    fun `repo-less entry falls back to legacy warped-community name slug`() {
        val legacy = AllowlistedModel(
            name = "some-legacy-model",
            displayName = "Some Legacy Model",
            modelFile = "some-legacy-model.litertlm",
            sizeInBytes = 42L
        )
        val assets = mockk<AssetManager>()
        every { assets.open("model_allowlist.json") } returns
            ByteArrayInputStream("""{"models": []}""".toByteArray())
        val context = mockk<Context>()
        every { context.assets } returns assets
        val repository = ModelAllowlistRepository(context)
        val downloadManager = mockk<ModelDownloadManager>()
        every { downloadManager.downloadStates } returns MutableStateFlow(emptyMap())
        val localRepo = mockk<LocalModelRepository>(relaxed = true)
        every { localRepo.observeModels() } returns MutableStateFlow(emptyList())
        val fileUrlSlot = slot<String>()
        val modelIdSlot = slot<String>()
        every {
            downloadManager.startDownload(
                capture(modelIdSlot), any(), capture(fileUrlSlot), any(), any()
            )
        } just Runs
        val viewModel = CatalogViewModel(repository, downloadManager, localRepo, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))

        viewModel.startDownload(legacy)

        assertThat(fileUrlSlot.captured).isEqualTo(
            "https://huggingface.co/warped-community/some-legacy-model/resolve/main/some-legacy-model.litertlm"
        )
        assertThat(modelIdSlot.captured).isEqualTo(
            "warped-community/some-legacy-model/some-legacy-model.litertlm"
        )
    }

    @Test
    fun `every shipped entry resolves to a litert-lm URL`() {
        val models = parseModelAllowlist(shippedAssetText())

        for (model in models) {
            val url =
                "https://huggingface.co/${model.repoSlug}/resolve/main/${model.modelFile}"
            assertThat(url).contains("litert-lm/resolve/main")
        }
    }
}
