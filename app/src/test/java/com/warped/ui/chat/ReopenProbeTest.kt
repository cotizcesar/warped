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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
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

/**
 * 2026-10-04 stuck-RED fix: reopening a chat ALWAYS re-pings its
 * endpoint. The selection collector skips the probe when the endpoint
 * id matches the last probed one, so a once-failed probe stayed red
 * forever — even after the server came back up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReopenProbeTest {

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
        url = "http://localhost:11434/",
        apiType = ProviderType.OPENAI,
        modelId = "m",
    )

    private fun conversation() = Conversation(
        id = 11,
        title = "chat",
        providerType = ProviderType.OPENAI,
        endpointId = 7,
        modelId = "m",
        createdAt = java.time.Instant.EPOCH,
        updatedAt = java.time.Instant.EPOCH,
    )

    @Test
    fun `reopening a chat re-pings the same endpoint`() = runTest {
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

        val remoteSelectionFlow = MutableStateFlow(RemoteSelection())
        every { chatRepository.observeConversations() } returns MutableStateFlow(emptyList())
        coEvery { chatRepository.loadConversation(11) } returns
            (conversation() to emptyList())
        coEvery { chatRepository.getWebOverride(11) } returns null
        every { endpointRepository.observeEndpoints() } returns
            MutableStateFlow(listOf(endpoint))
        coEvery { endpointRepository.getById(7) } returns endpoint
        coEvery { endpointRepository.getActive() } returns endpoint
        coEvery { endpointRepository.activateEndpoint(any()) } just Runs
        every { localModelRepository.observeModels() } returns MutableStateFlow(emptyList())
        every { activeModelSelection.activeModel } returns MutableStateFlow(null)
        every { activeModelSelection.localSelection } returns MutableStateFlow(LocalSelection())
        every { activeModelSelection.remoteSelection } returns remoteSelectionFlow
        every { activeModelSelection.disconnectLocal() } just Runs
        every { activeModelSelection.clearRemote() } just Runs
        every { activeModelSelection.selectRemote(any(), any(), any()) } answers {
            remoteSelectionFlow.value = RemoteSelection(
                modelId = firstArg(),
                providerType = secondArg(),
                endpointId = thirdArg(),
            )
        }
        every { activeModelSelection.saveLastConversation(any()) } just Runs
        every { engineManager.getActiveEngine() } returns null
        every { engineManager.scheduleUnload() } just Runs
        every { advancedPreferences.syntaxTheme } returns flowOf(SyntaxTheme.MONOKAI)
        every { advancedPreferences.codeFontScale } returns flowOf(1.0f)
        every { advancedPreferences.thinkingEnabled } returns flowOf(false)
        every { advancedPreferences.webGroundingEnabled } returns flowOf(false)
        val fetcher = mockk<com.warped.data.grounding.WebPageFetcher>()
        every { fetcher.cancel() } just Runs

        val provider = mockk<LlmProvider>()
        every { providerRouter.resolve(endpoint, "m") } returns provider
        coEvery { provider.testConnection() } returns Result.success(ConnectionStatus.Connected)

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
        advanceUntilIdle()

        vm.selectConversation(11)
        advanceUntilIdle()
        vm.selectConversation(11)
        advanceUntilIdle()

        coVerify(exactly = 2) { provider.testConnection() }
        assertThat(vm.connectionState.value.connectionStatus)
            .isEqualTo(ConnectionStatus.Connected)
    }
}
