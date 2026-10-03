package com.warped.ui.huggingface

import com.google.common.truth.Truth.assertThat
import com.warped.data.local.download.DownloadState
import com.warped.data.local.download.ModelDownloadManager
import com.warped.data.repository.ModelAllowlistRepository
import com.warped.data.repository.AllowlistedModel
import com.warped.domain.model.GenerationParameters
import com.warped.domain.model.LocalModel
import com.warped.domain.repository.LocalModelRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Catalog on-device presence (quick fix 2026-09-28).
 *
 * Match key is entry.modelFile <-> LocalModel.filePath substringAfterLast('/').
 * isEffectivelyDownloaded truth table: session-complete OR on-device wins.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogDownloadedTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeLocalRepo(models: List<LocalModel>): LocalModelRepository =
        object : LocalModelRepository {
            private val flow = MutableStateFlow(models)
            override fun observeModels(): Flow<List<LocalModel>> = flow
            override suspend fun getById(id: Long): LocalModel? = TODO("not needed")
            override suspend fun getByFilePath(filePath: String): LocalModel? = TODO("not needed")
            override suspend fun existsByFilePath(filePath: String): Boolean = TODO("not needed")
            override suspend fun saveModel(model: LocalModel): Long = TODO("not needed")
            override suspend fun updateParameters(
                modelId: Long,
                parameters: GenerationParameters
            ) = TODO("not needed")
            override suspend fun deleteModel(id: Long) = TODO("not needed")
            override suspend fun deleteByFilePath(filePath: String): Int = TODO("not needed")
            override suspend fun healModelNames(): Int = 0
        }

    private fun onDeviceModel(filePath: String): LocalModel = LocalModel(
        id = 1,
        name = "gemma-4-E4B-it",
        filePath = filePath,
        sizeBytes = 3659530240L,
        quantization = "Q4",
        parameterCount = "4B",
        architecture = "gemma",
        importedAt = Instant.now()
    )

    private fun buildViewModel(localRepo: LocalModelRepository): CatalogViewModel {
        val allowlist = mockk<ModelAllowlistRepository>()
        every { allowlist.models } returns emptyList()
        val downloadManager = mockk<ModelDownloadManager>()
        every { downloadManager.downloadStates } returns MutableStateFlow(emptyMap())
        return CatalogViewModel(allowlist, downloadManager, localRepo, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
    }

    @Test
    fun `downloadedFileNames derives from observeModels file names`() =
        runTest(testDispatcher) {
            val viewModel = buildViewModel(
                fakeLocalRepo(
                    listOf(
                        onDeviceModel("/data/user/0/com.warped/files/models/gemma-4-E4B-it.litertlm")
                    )
                )
            )
            advanceUntilIdle()

            assertThat(viewModel.downloadedFileNames.value)
                .contains("gemma-4-E4B-it.litertlm")
            assertThat(viewModel.downloadedFileNames.value)
                .doesNotContain("other-model.litertlm")
        }

    @Test
    fun `idle with no on-device is not downloaded`() {
        assertThat(isEffectivelyDownloaded(null, false)).isFalse()
    }

    @Test
    fun `session completed without on-device is downloaded`() {
        val completed = DownloadState(
            progress = 1f,
            isDownloading = false,
            isPaused = false,
            error = null
        )
        assertThat(isEffectivelyDownloaded(completed, false)).isTrue()
    }

    @Test
    fun `in-progress without on-device is not downloaded`() {
        val inProgress = DownloadState(
            progress = 0.4f,
            isDownloading = true,
            isPaused = false,
            error = null
        )
        assertThat(isEffectivelyDownloaded(inProgress, false)).isFalse()
    }

    @Test
    fun `idle with on-device is downloaded`() {
        assertThat(isEffectivelyDownloaded(null, true)).isTrue()
    }

    @Test
    fun `failed state with on-device is downloaded — on-device wins`() {
        val failed = DownloadState(
            progress = 0.5f,
            isDownloading = false,
            isPaused = false,
            error = "Network error"
        )
        assertThat(isEffectivelyDownloaded(failed, true)).isTrue()
        assertThat(isEffectivelyDownloaded(failed, false)).isFalse()
    }
}
