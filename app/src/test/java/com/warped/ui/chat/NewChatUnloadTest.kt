package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.data.local.inference.ActiveEngine
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.EngineType
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.LmStudioHelper
import com.warped.data.remote.provider.ProviderRouter
import com.warped.domain.llm.LlmModelHelper
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.Endpoint
import com.warped.domain.model.LocalSelection
import com.warped.domain.model.ParameterStore
import com.warped.domain.model.ProviderType
import com.warped.domain.model.RemoteSelection
import com.warped.domain.model.SyntaxTheme
import com.warped.domain.provider.LlmProvider
import com.warped.domain.repository.ChatRepository
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.LocalModelRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * 2026-10-04 New-chat teardown: starting a chat stops any in-flight
 * turn (no ghost answers streaming into the fresh chat) and frees
 * EVERYTHING — device engine and server-side instance alike — so the
 * user picks what to load next with a clean slate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewChatUnloadTest {

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @TempDir
    lateinit var tempDir: File

    private val endpoint = Endpoint(
        id = 7,
        name = "srv",
        url = "http://192.168.40.48:1234/",
        apiType = ProviderType.LM_STUDIO,
        modelId = "a",
    )

    private class Fixture(
        val vm: ChatViewModel,
        val activeModelSelection: com.warped.domain.model.ActiveModelSelection,
        val engineManager: EngineManager,
        val lmHelper: LmStudioHelper,
    )

    private fun build(modelPath: String?): Fixture {
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
        coEvery { chatRepository.createConversation(any(), any(), any(), any()) } returns 42L
        coEvery { chatRepository.saveMessage(any(), any()) } returns Unit
        coEvery { chatRepository.getWebOverride(any()) } returns null
        every { endpointRepository.observeEndpoints() } returns
            MutableStateFlow(listOf(endpoint))
        coEvery { endpointRepository.getById(7) } returns endpoint
        coEvery { endpointRepository.getActive() } returns endpoint
        coEvery { endpointRepository.activateEndpoint(any()) } just Runs
        every { localModelRepository.observeModels() } returns MutableStateFlow(emptyList())
        every { activeModelSelection.activeModel } returns MutableStateFlow(null)
        val remoteSelectionFlow = MutableStateFlow(RemoteSelection())
        every { activeModelSelection.localSelection } returns
            MutableStateFlow(LocalSelection(modelId = modelPath, isConnected = modelPath != null))
        every { activeModelSelection.remoteSelection } returns remoteSelectionFlow
        every { activeModelSelection.disconnectLocal() } just Runs
        every { activeModelSelection.clearRemote() } just Runs
        every { activeModelSelection.selectLocalPending(any()) } just Runs
        every { activeModelSelection.markLocalLoading(any()) } just Runs
        every { activeModelSelection.saveLastConversation(any()) } just Runs
        every { activeModelSelection.selectRemote(any(), any(), any()) } answers {
            remoteSelectionFlow.value = RemoteSelection(
                modelId = firstArg(),
                providerType = secondArg(),
                endpointId = thirdArg(),
            )
        }
        every { engineManager.getActiveEngine() } returns
            modelPath?.let { ActiveEngine(EngineType.LITE_RT_LM, it) }
        every { engineManager.scheduleUnload() } just Runs
        every { memoryChecker.shouldWarn(any()) } returns false
        every { advancedPreferences.syntaxTheme } returns flowOf(SyntaxTheme.MONOKAI)
        every { advancedPreferences.codeFontScale } returns flowOf(1.0f)
        every { advancedPreferences.thinkingEnabled } returns flowOf(false)
        every { advancedPreferences.webGroundingEnabled } returns flowOf(false)
        val fetcher = mockk<com.warped.data.grounding.WebPageFetcher>()
        every { fetcher.cancel() } just Runs
        val multiUrlFetcher = mockk<com.warped.data.grounding.MultiUrlFetcher>()

        val localHelper = mockk<LlmModelHelper>()
        every { localHelper.type } returns ProviderType.LITE_RT_LM
        coEvery { localHelper.initialize(any()) } returns Unit
        coEvery { localHelper.stopResponse() } just Runs
        every { localHelper.runInference(any(), any()) } returns flow { awaitCancellation() }
        every { providerRouter.resolveLocalHelper(any(), any()) } returns localHelper

        val lmHelper = mockk<LmStudioHelper>()
        coEvery { lmHelper.initialize(any()) } just Runs
        every { lmHelper.getInstanceId() } returns "inst-a"
        coEvery { lmHelper.cleanUp() } just Runs
        every { providerRouter.resolveHelper(endpoint, any()) } returns lmHelper
        val probeProvider = mockk<LlmProvider>()
        every { providerRouter.resolve(endpoint, any()) } returns probeProvider
        coEvery { probeProvider.testConnection() } returns
            Result.success(ConnectionStatus.Connected)

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
        return Fixture(vm, activeModelSelection, engineManager, lmHelper)
    }

    @Test
    fun `new chat stops the in-flight turn`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "m.litertlm").apply { writeText("fake") }
        val f = build(modelFile.absolutePath)
        runCurrent()

        f.vm.sendMessage("hola")
        runCurrent()
        assertThat(f.vm.inputState.value.isGenerating).isTrue()

        f.vm.newConversation()
        advanceUntilIdle()

        assertThat(f.vm.inputState.value.isGenerating).isFalse()
        assertThat(f.vm.transcriptState.value.isStreaming).isFalse()
        assertThat(f.vm.transcriptState.value.messages).isEmpty()
    }

    @Test
    fun `new chat unloads the server-side instance`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = build(modelPath = null)
        runCurrent()

        // Load remote model A through the picker path.
        f.vm.launchModelSelection("a", ProviderType.LM_STUDIO, 7)
        advanceUntilIdle()
        assertThat(f.vm.connectionState.value.loadedInstanceId).isEqualTo("inst-a")

        f.vm.newConversation()
        advanceUntilIdle()

        // The server unload runs off-main: poll for it.
        coVerify(timeout = 5000) { f.lmHelper.cleanUp() }
        assertThat(f.vm.connectionState.value.loadedInstanceId).isNull()
    }
}
