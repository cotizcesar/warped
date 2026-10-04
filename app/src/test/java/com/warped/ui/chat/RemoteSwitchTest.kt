package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.LmStudioHelper
import com.warped.data.remote.provider.ProviderRouter
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
import io.mockk.coVerifyOrder
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
 * 2026-10-04 Casos 1-2: switching remote models unloads the previous
 * server-side instance STRICTLY BEFORE loading the new one (single
 * coroutine — the old split launches raced and could unload the
 * just-loaded model, overloading the server GPU). Loading a remote
 * model must never pollute the local selection (that routed later
 * sends down the local-file path).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteSwitchTest {

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
        modelId = "a",
    )

    private lateinit var currentHelperModel: String

    private fun build(): Triple<ChatViewModel, LmStudioHelper, MutableStateFlow<RemoteSelection>> {
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
        every { endpointRepository.observeEndpoints() } returns
            MutableStateFlow(listOf(endpoint))
        coEvery { endpointRepository.getById(7) } returns endpoint
        coEvery { endpointRepository.getActive() } returns endpoint
        coEvery { endpointRepository.activateEndpoint(any()) } just Runs
        every { localModelRepository.observeModels() } returns MutableStateFlow(emptyList())
        every { activeModelSelection.activeModel } returns MutableStateFlow(null)
        every { activeModelSelection.localSelection } returns MutableStateFlow(LocalSelection())
        every { activeModelSelection.remoteSelection } returns remoteSelectionFlow
        every { activeModelSelection.selectRemote(any(), any(), any()) } answers {
            remoteSelectionFlow.value = RemoteSelection(
                modelId = firstArg(),
                providerType = secondArg(),
                endpointId = thirdArg(),
            )
        }
        every { activeModelSelection.saveLastConversation(any()) } just Runs
        every { engineManager.getActiveEngine() } returns null
        every { advancedPreferences.syntaxTheme } returns flowOf(SyntaxTheme.MONOKAI)
        every { advancedPreferences.codeFontScale } returns flowOf(1.0f)
        every { advancedPreferences.thinkingEnabled } returns flowOf(false)
        every { advancedPreferences.webGroundingEnabled } returns flowOf(false)
        val fetcher = mockk<com.warped.data.grounding.WebPageFetcher>()
        every { fetcher.cancel() } just Runs

        val lmHelper = mockk<LmStudioHelper>()
        coEvery { lmHelper.initialize(any()) } answers { currentHelperModel = firstArg() }
        every { lmHelper.getInstanceId() } answers { "inst-$currentHelperModel" }
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
            multiUrlFetcher = mockk<com.warped.data.grounding.MultiUrlFetcher>(),
            ddgSearchRepository = mockk(),
            modelAllowlistRepository = mockk(relaxed = true),
            context = context,
            reviewHelper = mockk(relaxed = true),
        )
        return Triple(vm, lmHelper, remoteSelectionFlow)
    }

    @Test
    fun `remote load never pollutes the local selection`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        currentHelperModel = ""
        val (vm, _, _) = build()
        runCurrent()

        vm.launchModelSelection("a", ProviderType.LM_STUDIO, 7)
        advanceUntilIdle()

        assertThat(vm.connectionState.value.selectedLocalModelId).isNull()
        assertThat(vm.connectionState.value.selectedRemoteModelId).isEqualTo("a")
        assertThat(vm.connectionState.value.loadedInstanceId).isEqualTo("inst-a")
        assertThat(vm.connectionState.value.connectionStatus)
            .isEqualTo(ConnectionStatus.Connected)
    }

    @Test
    fun `switching remote models unloads old before loading new`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        currentHelperModel = ""
        val (vm, lmHelper, _) = build()
        runCurrent()

        vm.launchModelSelection("a", ProviderType.LM_STUDIO, 7)
        advanceUntilIdle()
        vm.launchModelSelection("b", ProviderType.LM_STUDIO, 7)
        advanceUntilIdle()

        coVerifyOrder {
            lmHelper.cleanUp()
            lmHelper.initialize("b")
        }
        assertThat(vm.connectionState.value.loadedInstanceId).isEqualTo("inst-b")
        assertThat(vm.connectionState.value.selectedLocalModelId).isNull()
    }
}
