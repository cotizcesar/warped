package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.ProviderRouter
import com.warped.domain.model.LocalModel
import com.warped.domain.model.LocalSelection
import com.warped.domain.model.ModelCapabilities
import com.warped.domain.model.RemoteSelection
import com.warped.domain.model.SyntaxTheme
import com.warped.domain.repository.ChatRepository
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.LocalModelRepository
import com.warped.domain.review.ReviewHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
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
import java.time.Instant

/**
 * Thinking-button visibility vs model-list timing: the selection collectors
 * fail open while the models list is empty. When the list arrives, the
 * observeModels collector must recompute supportsThinking — otherwise a
 * text-only model keeps the Thinking button forever.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThinkingVisibilityHealTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `thinking hides once text-only model list arrives`() = runTest {
        val modelsFlow = MutableStateFlow<List<LocalModel>>(emptyList())
        val modelPath = "/models/gemma-3-1b-it.litertlm"
        val allowlist = mockk<com.warped.data.repository.ModelAllowlistRepository>()
        every { allowlist.effectiveCapabilities(any()) } returns
            ModelCapabilities(vision = false, reasoning = false, tools = false, audio = false)

        val localModelRepository = mockk<LocalModelRepository>()
        every { localModelRepository.observeModels() } returns modelsFlow
        val activeModelSelection = mockk<com.warped.domain.model.ActiveModelSelection>()
        every { activeModelSelection.activeModel } returns MutableStateFlow(null)
        every { activeModelSelection.localSelection } returns
            MutableStateFlow(LocalSelection(modelId = modelPath, isConnected = false))
        every { activeModelSelection.remoteSelection } returns MutableStateFlow(RemoteSelection())
        every { activeModelSelection.saveLastConversation(any()) } just Runs

        val chatRepository = mockk<ChatRepository>()
        every { chatRepository.observeConversations() } returns MutableStateFlow(emptyList())
        val endpointRepository = mockk<EndpointRepository>()
        every { endpointRepository.observeEndpoints() } returns MutableStateFlow(emptyList())
        val advancedPreferences = mockk<AdvancedPreferences>()
        every { advancedPreferences.syntaxTheme } returns flowOf(SyntaxTheme.MONOKAI)
        every { advancedPreferences.codeFontScale } returns flowOf(1.0f)
        every { advancedPreferences.thinkingEnabled } returns flowOf(false)
        every { advancedPreferences.webGroundingEnabled } returns flowOf(false)
        val context = mockk<Context>()
        every { context.getString(any<Int>()) } returns ""
        every { context.getString(any<Int>(), *anyVararg<Any>()) } returns ""

        val vm = ChatViewModel(
            chatRepository = chatRepository,
            endpointRepository = endpointRepository,
            localModelRepository = localModelRepository,
            activeModelSelection = activeModelSelection,
            providerRouter = mockk<ProviderRouter>(),
            savedStateHandle = SavedStateHandle(),
            parameterStore = com.warped.domain.model.ParameterStore(),
            engineManager = mockk<EngineManager>(),
            memoryChecker = mockk<MemoryChecker>(),
            advancedPreferences = advancedPreferences,
            fetcher = mockk<com.warped.data.grounding.WebPageFetcher>(),
            multiUrlFetcher = mockk<com.warped.data.grounding.MultiUrlFetcher>(),
            ddgSearchRepository = mockk(),
            modelAllowlistRepository = allowlist,
            context = context,
            reviewHelper = mockk(relaxed = true),
        )
        runCurrent()
        // Fail-open while the list is empty: button visible (pre-existing).
        assertThat(vm.inputState.value.supportsThinking).isTrue()

        modelsFlow.emit(
            listOf(
                LocalModel(
                    id = 1,
                    name = "gemma-3-1b-it",
                    filePath = modelPath,
                    sizeBytes = 10,
                    quantization = "Q4",
                    parameterCount = "1B",
                    architecture = "gemma",
                    importedAt = Instant.EPOCH
                )
            )
        )
        runCurrent()
        // List arrived, verified caps say text-only: button must hide.
        assertThat(vm.inputState.value.supportsThinking).isFalse()
    }
}
