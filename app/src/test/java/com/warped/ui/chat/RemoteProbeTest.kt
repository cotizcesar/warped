package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.data.local.inference.ActiveEngine
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.EngineType
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.ProviderRouter
import com.warped.data.repository.ModelAllowlistRepository
import com.warped.domain.model.ActiveModelSelection
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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Remote parity with the cold-start mount: selecting an endpoint probes
 * reachability once so the traffic light turns green by itself.
 * Failures stay red (send surfaces the real error).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteProbeTest {

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val remoteSelection = MutableStateFlow(RemoteSelection())
    private val endpointRepository = mockk<EndpointRepository>()
    private val providerRouter = mockk<ProviderRouter>()
    private val endpoint = Endpoint(
        id = 7,
        name = "srv",
        url = "http://localhost:11434/",
        apiType = ProviderType.OPENAI,
        modelId = "m",
    )

    private fun buildViewModel(): ChatViewModel {
        val chatRepository = mockk<ChatRepository>()
        val localModelRepository = mockk<LocalModelRepository>()
        val activeModelSelection = mockk<ActiveModelSelection>()
        val engineManager = mockk<EngineManager>()
        val memoryChecker = mockk<MemoryChecker>()
        val advancedPreferences = mockk<AdvancedPreferences>()
        val context = mockk<Context>()
        every { context.getString(any<Int>()) } returns ""
        every { context.getString(any<Int>(), *anyVararg<Any>()) } returns ""

        every { chatRepository.observeConversations() } returns MutableStateFlow(emptyList())
        every { endpointRepository.observeEndpoints() } returns MutableStateFlow(emptyList())
        every { localModelRepository.observeModels() } returns MutableStateFlow(emptyList())
        every { activeModelSelection.activeModel } returns MutableStateFlow(null)
        every { activeModelSelection.localSelection } returns MutableStateFlow(LocalSelection())
        every { activeModelSelection.remoteSelection } returns remoteSelection
        every { activeModelSelection.saveLastConversation(any()) } just Runs
        every { engineManager.getActiveEngine() } returns null
        every { advancedPreferences.syntaxTheme } returns flowOf(SyntaxTheme.MONOKAI)
        every { advancedPreferences.codeFontScale } returns flowOf(1.0f)
        every { advancedPreferences.thinkingEnabled } returns flowOf(false)
        every { advancedPreferences.webGroundingEnabled } returns flowOf(false)
        val fetcher = mockk<com.warped.data.grounding.WebPageFetcher>()
        every { fetcher.cancel() } just Runs

        return ChatViewModel(
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
            modelAllowlistRepository = mockk<ModelAllowlistRepository>().also(::stubEffectiveCapabilities),
            context = context,
            reviewHelper = mockk(relaxed = true),
        )
    }

    @Test
    fun `reachable endpoint turns connection green`() = runTest {
        val provider = mockk<LlmProvider>()
        coEvery { endpointRepository.getById(7) } returns endpoint
        every { providerRouter.resolve(endpoint, "m") } returns provider
        coEvery { provider.testConnection() } returns Result.success(ConnectionStatus.Connected)
        val vm = buildViewModel()
        advanceUntilIdle()

        remoteSelection.value = RemoteSelection(modelId = "m", providerType = ProviderType.OPENAI, endpointId = 7)
        advanceUntilIdle()

        assertThat(vm.connectionState.value.connectionStatus).isEqualTo(ConnectionStatus.Connected)
    }

    @Test
    fun `unreachable endpoint stays red`() = runTest {
        val provider = mockk<LlmProvider>()
        coEvery { endpointRepository.getById(7) } returns endpoint
        every { providerRouter.resolve(endpoint, "m") } returns provider
        coEvery { provider.testConnection() } returns Result.success(ConnectionStatus.Disconnected)
        val vm = buildViewModel()
        advanceUntilIdle()

        remoteSelection.value = RemoteSelection(modelId = "m", providerType = ProviderType.OPENAI, endpointId = 7)
        advanceUntilIdle()

        assertThat(vm.connectionState.value.connectionStatus).isEqualTo(ConnectionStatus.Disconnected)
    }
}
