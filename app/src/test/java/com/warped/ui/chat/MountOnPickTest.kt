package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.warped.data.local.inference.ActiveEngine
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.EngineType
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.ProviderRouter
import com.warped.domain.model.LocalModel
import com.warped.domain.model.LocalSelection
import com.warped.domain.model.ParameterStore
import com.warped.domain.model.ProviderType
import com.warped.domain.model.RemoteSelection
import com.warped.domain.model.SyntaxTheme
import com.warped.domain.repository.ChatRepository
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.LocalModelRepository
import com.warped.domain.review.ReviewHelper
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant

/**
 * 2026-10-04 Caso 1: picking a local model mounts it in the background
 * (loading indicator on) instead of waiting for the first send — unless
 * the engine already serves it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MountOnPickTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @TempDir
    lateinit var tempDir: File

    private val modelPath by lazy {
        File(tempDir, "gemma.litertlm").apply { writeText("fake") }.absolutePath
    }

    private fun model() = LocalModel(
        name = "gemma.litertlm",
        filePath = modelPath,
        sizeBytes = 600L * 1024L * 1024L,
        quantization = "Q8",
        parameterCount = "1B",
        architecture = "gemma3",
        importedAt = Instant.EPOCH,
    )

    private class Fixture(
        val vm: ChatViewModel,
        val activeModelSelection: com.warped.domain.model.ActiveModelSelection,
        val engineManager: EngineManager,
    )

    private fun build(activeEngine: ActiveEngine?): Fixture {
        val chatRepository = mockk<ChatRepository>()
        val endpointRepository = mockk<EndpointRepository>()
        val localModelRepository = mockk<LocalModelRepository>()
        val activeModelSelection = mockk<com.warped.domain.model.ActiveModelSelection>()
        val providerRouter = mockk<ProviderRouter>()
        val engineManager = mockk<EngineManager>()
        val memoryChecker = mockk<MemoryChecker>()
        val advancedPreferences = mockk<AdvancedPreferences>()
        val context = mockk<Context>()
        every { context.getString(any<Int>()) } returns ""
        every { context.getString(any<Int>(), *anyVararg<Any>()) } returns ""

        every { chatRepository.observeConversations() } returns MutableStateFlow(emptyList())
        every { endpointRepository.observeEndpoints() } returns MutableStateFlow(emptyList())
        every { localModelRepository.observeModels() } returns MutableStateFlow(listOf(model()))
        every { activeModelSelection.activeModel } returns MutableStateFlow(null)
        val localSelectionFlow = MutableStateFlow(LocalSelection())
        every { activeModelSelection.localSelection } returns localSelectionFlow
        every { activeModelSelection.remoteSelection } returns MutableStateFlow(RemoteSelection())
        every { activeModelSelection.selectLocalPending(any()) } answers {
            localSelectionFlow.value = LocalSelection(modelId = firstArg())
        }
        every { activeModelSelection.markLocalLoading(any()) } answers {
            localSelectionFlow.value = LocalSelection(modelId = firstArg(), isLoading = true)
        }
        every { activeModelSelection.connectLocal(any(), any()) } answers {
            localSelectionFlow.value = LocalSelection(modelId = firstArg(), isConnected = true)
        }
        every { engineManager.getActiveEngine() } returns activeEngine
        every { engineManager.switchToLiteRT(any()) } just Runs
        every { engineManager.scheduleUnload() } just Runs
        every { memoryChecker.shouldWarn(any()) } returns false
        every { memoryChecker.canLoadModel(any()) } returns true
        every { advancedPreferences.syntaxTheme } returns flowOf(SyntaxTheme.MONOKAI)
        every { advancedPreferences.codeFontScale } returns flowOf(1.0f)
        every { advancedPreferences.thinkingEnabled } returns flowOf(false)
        every { advancedPreferences.webGroundingEnabled } returns flowOf(false)
        val fetcher = mockk<com.warped.data.grounding.WebPageFetcher>()
        every { fetcher.cancel() } just Runs
        val multiUrlFetcher = mockk<com.warped.data.grounding.MultiUrlFetcher>()

        val vm = ChatViewModel(
            chatRepository = chatRepository,
            endpointRepository = endpointRepository,
            localModelRepository = localModelRepository,
            activeModelSelection = activeModelSelection,
            providerRouter = providerRouter,
            savedStateHandle = SavedStateHandle(),
            parameterStore = ParameterStore(),
            engineManager = engineManager,
            memoryChecker = memoryChecker,
            advancedPreferences = advancedPreferences,
            fetcher = fetcher,
            multiUrlFetcher = multiUrlFetcher,
            ddgSearchRepository = mockk(),
            modelAllowlistRepository = mockk(relaxed = true),
            context = context,
            reviewHelper = mockk(relaxed = true),
        )
        return Fixture(vm, activeModelSelection, engineManager)
    }

    @Test
    fun `picking an unmounted model mounts it in background`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = build(activeEngine = null)
        runCurrent()

        f.vm.launchModelSelection(modelPath, ProviderType.LITE_RT_LM, null)
        runCurrent()

        verify(timeout = 5000) { f.activeModelSelection.markLocalLoading(modelPath, null) }
        verify(timeout = 5000) { f.engineManager.switchToLiteRT(modelPath) }
        verify(timeout = 5000) { f.activeModelSelection.connectLocal(modelPath, ProviderType.LITE_RT_LM, null) }
    }

    @Test
    fun `picking the already-mounted model skips the reload`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = build(activeEngine = ActiveEngine(EngineType.LITE_RT_LM, modelPath))
        runCurrent()

        f.vm.launchModelSelection(modelPath, ProviderType.LITE_RT_LM, null)
        runCurrent()

        // Negative assertions need a grace window: the pick pipeline runs
        // async, so "never mounted" only means something after it had a
        // chance to run. Timeout-verify waits, then asserts zero calls.
        verify(timeout = 2000, exactly = 0) { f.activeModelSelection.markLocalLoading(any(), any()) }
        verify(timeout = 2000, exactly = 0) { f.engineManager.switchToLiteRT(any()) }
    }
}
