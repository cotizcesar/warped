package com.warped.ui.models

import com.google.common.truth.Truth.assertThat
import com.warped.data.local.download.ModelDownloadManager
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.inference.ModelImportManager
import com.warped.data.local.security.ApiKeyStore
import com.warped.data.local.inference.InputSanitizer
import com.warped.data.remote.provider.ProviderRouter
import com.warped.data.repository.ModelAllowlistRepository
import com.warped.domain.model.ActiveModelSelection
import com.warped.domain.model.LocalModel
import com.warped.domain.model.LocalSelection
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.LocalModelRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Delete-error surfacing (quick plan 2026-09-28): manager failures land in
 * `uiState.error` (shown via the Models Snackbar), and deleting the
 * connected model calls `disconnectLocal()` FIRST.
 *
 * Follows the `CatalogDownloadUrlTest` MockK patterns for ViewModel
 * construction (relaxed mocks + stubbed flows + `setMain`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelsDeleteErrorTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun model(filePath: String = "/models/test.litertlm"): LocalModel =
        LocalModel(
            id = 3,
            name = "test",
            filePath = filePath,
            sizeBytes = 10,
            quantization = "Q4",
            parameterCount = "1B",
            architecture = "Test",
            importedAt = Instant.EPOCH
        )

    private fun buildViewModel(
        manager: ModelImportManager,
        localSelection: LocalSelection = LocalSelection()
    ): Pair<ModelsViewModel, ActiveModelSelection> {
        val localRepo = mockk<LocalModelRepository>(relaxed = true)
        every { localRepo.observeModels() } returns MutableStateFlow(emptyList())
        val endpointRepo = mockk<EndpointRepository>(relaxed = true)
        every { endpointRepo.observeEndpoints() } returns MutableStateFlow(emptyList())
        val downloadManager = mockk<ModelDownloadManager>(relaxed = true)
        every { downloadManager.downloadStates } returns MutableStateFlow(emptyMap())
        val activeSelection = mockk<ActiveModelSelection>(relaxed = true)
        every { activeSelection.localSelection } returns MutableStateFlow(localSelection)
        val viewModel = ModelsViewModel(
            localModelRepository = localRepo,
            endpointRepository = endpointRepo,
            activeModelSelection = activeSelection,
            modelImportManager = manager,
            modelDownloadManager = downloadManager,
            memoryChecker = mockk(relaxed = true),
            apiKeyStore = mockk(relaxed = true),
            providerRouter = mockk(relaxed = true),
            inputSanitizer = mockk(relaxed = true),
            allowlist = mockk(relaxed = true)
        )
        return viewModel to activeSelection
    }

    @Test
    fun `manager failure surfaces in uiState error`() = runTest(testDispatcher) {
        val manager = mockk<ModelImportManager>(relaxed = true)
        coEvery { manager.deleteModel(any()) } throws
            IllegalStateException("Could not delete model file at /models/test.litertlm")
        val (viewModel, _) = buildViewModel(manager)

        viewModel.deleteModel(model())
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error)
            .contains("Could not delete model file")
    }

    @Test
    fun `connected model disconnects before manager delete`() = runTest(testDispatcher) {
        val target = model("/models/active.litertlm")
        val manager = mockk<ModelImportManager>(relaxed = true)
        val (viewModel, activeSelection) = buildViewModel(
            manager,
            localSelection = LocalSelection(modelId = target.filePath, isConnected = true)
        )

        viewModel.deleteModel(target)
        advanceUntilIdle()

        verify { activeSelection.disconnectLocal() }
        coVerify { manager.deleteModel(target) }
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `unconnected model skips disconnect but still deletes`() = runTest(testDispatcher) {
        val manager = mockk<ModelImportManager>(relaxed = true)
        val (viewModel, activeSelection) = buildViewModel(
            manager,
            localSelection = LocalSelection(modelId = "/models/other.litertlm", isConnected = true)
        )

        viewModel.deleteModel(model())
        advanceUntilIdle()

        verify(exactly = 0) { activeSelection.disconnectLocal() }
        coVerify { manager.deleteModel(any()) }
        assertThat(viewModel.uiState.value.error).isNull()
    }
}
