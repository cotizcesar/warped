package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.ProviderRouter
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.Conversation
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
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.time.Instant

/**
 * 2026-10-04 single-selection invariant: opening a chat drops the
 * OTHER selection at the source. A stale local selection left alive
 * behind a remote chat resurrects phantom mounts (stuck "Loading
 * model" rows) and double selections route sends to the wrong engine;
 * a stale remote selection behind a local chat re-probes pointlessly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SingleSelectionTest {

    @TempDir
    lateinit var tempDir: File

    private val gemmaPath by lazy {
        File(tempDir, "gemma.litertlm").apply { writeText("fake") }.absolutePath
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val endpoint = Endpoint(
        id = 7,
        name = "srv",
        url = "http://192.168.40.48:1234/",
        apiType = ProviderType.LM_STUDIO,
        modelId = "m",
    )

    private class Fixture(
        val vm: ChatViewModel,
        val activeModelSelection: com.warped.domain.model.ActiveModelSelection,
        val engineManager: EngineManager,
        val chatRepository: ChatRepository,
        val localSelectionFlow: MutableStateFlow<LocalSelection>,
    )

    private fun build(): Fixture {
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

        val localSelectionFlow = MutableStateFlow(LocalSelection())
        val remoteSelectionFlow = MutableStateFlow(RemoteSelection())
        every { chatRepository.observeConversations() } returns MutableStateFlow(emptyList())
        coEvery { chatRepository.getWebOverride(any()) } returns null
        every { endpointRepository.observeEndpoints() } returns
            MutableStateFlow(listOf(endpoint))
        coEvery { endpointRepository.getById(7) } returns endpoint
        coEvery { endpointRepository.getActive() } returns endpoint
        coEvery { endpointRepository.activateEndpoint(any()) } just Runs
        coEvery { localModelRepository.existsByFilePath(any()) } returns true
        every { localModelRepository.observeModels() } returns MutableStateFlow(emptyList())
        every { activeModelSelection.activeModel } returns MutableStateFlow(null)
        every { activeModelSelection.localSelection } returns localSelectionFlow
        every { activeModelSelection.remoteSelection } returns remoteSelectionFlow
        every { activeModelSelection.selectLocalPending(any()) } answers {
            localSelectionFlow.value = LocalSelection(modelId = firstArg())
        }
        every { activeModelSelection.markLocalLoading(any()) } answers {
            localSelectionFlow.value = localSelectionFlow.value.copy(isLoading = true)
        }
        every { activeModelSelection.markLocalDisconnected() } answers {
            localSelectionFlow.value = localSelectionFlow.value.copy(isConnected = false, isLoading = false)
        }
        every { activeModelSelection.connectLocal(any(), any()) } answers {
            localSelectionFlow.value = LocalSelection(modelId = firstArg(), isConnected = true)
        }
        every { activeModelSelection.disconnectLocal() } answers {
            localSelectionFlow.value = LocalSelection()
        }
        every { activeModelSelection.clearRemote() } answers {
            remoteSelectionFlow.value = RemoteSelection()
        }
        every { activeModelSelection.selectRemote(any(), any(), any()) } answers {
            remoteSelectionFlow.value = RemoteSelection(
                modelId = firstArg(),
                providerType = secondArg(),
                endpointId = thirdArg(),
            )
        }
        every { activeModelSelection.saveLastConversation(any()) } just Runs
        every { engineManager.getActiveEngine() } returns null
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
            multiUrlFetcher = mockk<com.warped.data.grounding.MultiUrlFetcher>(),
            ddgSearchRepository = mockk(),
            modelAllowlistRepository = mockk(relaxed = true),
            context = context,
            reviewHelper = mockk(relaxed = true),
        )
        return Fixture(vm, activeModelSelection, engineManager, chatRepository, localSelectionFlow)
    }

    private fun remoteConversation() = Conversation(
        id = 11,
        title = "net",
        providerType = ProviderType.LM_STUDIO,
        endpointId = 7,
        modelId = "m",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun localConversation() = Conversation(
        id = 12,
        title = "local",
        providerType = ProviderType.LITE_RT_LM,
        endpointId = 0,
        modelId = gemmaPath,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `opening a remote chat drops the stale local selection and frees the engine`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = build()
        coEvery { f.chatRepository.loadConversation(11) } returns (remoteConversation() to emptyList())
        // A stale local selection from before (pending model, engine idle).
        f.localSelectionFlow.value = LocalSelection(modelId = gemmaPath)
        runCurrent()

        f.vm.selectConversation(11)
        advanceUntilIdle()

        verify { f.activeModelSelection.disconnectLocal() }
        verify { f.engineManager.scheduleUnload() }
        assertThat(f.localSelectionFlow.value.modelId).isNull()
    }

    @Test
    fun `opening a local chat drops the stale remote selection`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = build()
        coEvery { f.chatRepository.loadConversation(12) } returns (localConversation() to emptyList())
        runCurrent()

        f.vm.selectConversation(12)
        advanceUntilIdle()

        verify { f.activeModelSelection.clearRemote() }
        verify { f.activeModelSelection.selectLocalPending(gemmaPath, null) }
    }
}
