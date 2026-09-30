package com.warped.ui.components

import com.google.common.truth.Truth.assertThat
import com.warped.data.local.download.ModelDownloadManager
import com.warped.data.repository.ModelAllowlistRepository
import com.warped.domain.model.ActiveModelSelection
import com.warped.domain.model.LocalSelection
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.LocalModelRepository
import com.warped.ui.huggingface.CatalogViewModel
import com.warped.ui.models.ModelsViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Quick-task (download-pause): pause/resume is offered identically on both
 * download screens through the shared [ActiveDownloadContent] entry
 * points, reusing the tested `ModelDownloadManager` worker APIs (no
 * engine changes, no worker changes).
 *
 * The composable wiring itself is compile-pinned (both call sites pass
 * `onPause`/`onResume`); these tests pin the ViewModel half: pause and
 * resume delegate to the manager with the same id semantics on both
 * screens, and Cancel/Delete delegation is unchanged.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveDownloadPauseTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun modelsViewModel(
        downloadManager: ModelDownloadManager,
    ): ModelsViewModel {
        val localRepo = mockk<LocalModelRepository>(relaxed = true)
        every { localRepo.observeModels() } returns MutableStateFlow(emptyList())
        val endpointRepo = mockk<EndpointRepository>(relaxed = true)
        every { endpointRepo.observeEndpoints() } returns MutableStateFlow(emptyList())
        val activeSelection = mockk<ActiveModelSelection>(relaxed = true)
        every { activeSelection.localSelection } returns MutableStateFlow(LocalSelection())
        return ModelsViewModel(
            localModelRepository = localRepo,
            endpointRepository = endpointRepo,
            activeModelSelection = activeSelection,
            modelImportManager = mockk(relaxed = true),
            modelDownloadManager = downloadManager,
            memoryChecker = mockk(relaxed = true),
            apiKeyStore = mockk(relaxed = true),
            providerRouter = mockk(relaxed = true),
            inputSanitizer = mockk(relaxed = true),
            allowlist = mockk(relaxed = true),
            chatRepository = mockk(relaxed = true),
            context = mockk(relaxed = true),
        )
    }

    private fun catalogViewModel(
        downloadManager: ModelDownloadManager,
    ): CatalogViewModel {
        val localRepo = mockk<LocalModelRepository>(relaxed = true)
        every { localRepo.observeModels() } returns MutableStateFlow(emptyList())
        return CatalogViewModel(
            allowlistRepository = mockk<ModelAllowlistRepository>(relaxed = true),
            downloadManager = downloadManager,
            localModelRepository = localRepo,
        )
    }

    @Test
    fun `models screen pause delegates to manager with the download id`() {
        val manager = mockk<ModelDownloadManager>(relaxed = true)
        every { manager.downloadStates } returns MutableStateFlow(emptyMap())
        val viewModel = modelsViewModel(manager)

        viewModel.pauseDownload("org/model.gguf")

        verify { manager.pauseDownload("org/model.gguf") }
    }

    @Test
    fun `models screen resume delegates to manager with the download id`() {
        val manager = mockk<ModelDownloadManager>(relaxed = true)
        every { manager.downloadStates } returns MutableStateFlow(emptyMap())
        val viewModel = modelsViewModel(manager)

        viewModel.resumeDownload("org/model.gguf")

        verify { manager.resumeDownload("org/model.gguf") }
    }

    @Test
    fun `catalog pause and resume use the same id semantics as models screen`() {
        val manager = mockk<ModelDownloadManager>(relaxed = true)
        every { manager.downloadStates } returns MutableStateFlow(emptyMap())
        val viewModel = catalogViewModel(manager)

        viewModel.pauseDownload("org/model.gguf")
        viewModel.resumeDownload("org/model.gguf")

        verify { manager.pauseDownload("org/model.gguf") }
        verify { manager.resumeDownload("org/model.gguf") }
    }

    @Test
    fun `models screen cancel delegation is unchanged`() {
        val manager = mockk<ModelDownloadManager>(relaxed = true)
        every { manager.downloadStates } returns MutableStateFlow(emptyMap())
        val viewModel = modelsViewModel(manager)

        viewModel.cancelDownload("org/model.gguf")

        verify { manager.cancelDownload("org/model.gguf") }
        // Pause/resume wiring adds entry points — it never reroutes cancel.
        verify(exactly = 0) { manager.pauseDownload(any()) }
        verify(exactly = 0) { manager.resumeDownload(any()) }
    }

    @Test
    fun `download ids flow through verbatim on both view models`() {
        val modelsManager = mockk<ModelDownloadManager>(relaxed = true)
        every { modelsManager.downloadStates } returns MutableStateFlow(emptyMap())
        val catalogManager = mockk<ModelDownloadManager>(relaxed = true)
        every { catalogManager.downloadStates } returns MutableStateFlow(emptyMap())
        val models = modelsViewModel(modelsManager)
        val catalog = catalogViewModel(catalogManager)

        models.pauseDownload("id-A")
        catalog.pauseDownload("id-A")

        verify { modelsManager.pauseDownload("id-A") }
        verify { catalogManager.pauseDownload("id-A") }
        assertThat(modelsManager).isNotSameInstanceAs(catalogManager)
    }
}
