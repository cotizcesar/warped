package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.warped.data.local.inference.EngineManager
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
import java.time.Instant

/**
 * 2026-10-04 New-chat contract: creating a chat unloads the model
 * from memory AND clears both selections, so the user must pick from
 * the existing selector (input locked until then). The auto-select
 * hook must not reselect the newest file behind the user's back — a
 * genuinely new download still auto-selects.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewChatDeselectTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun localModel(path: String, importedAt: Instant) = LocalModel(
        name = path.substringAfterLast("/"),
        filePath = path,
        sizeBytes = 600L * 1024L * 1024L,
        quantization = "Q8",
        parameterCount = "1B",
        architecture = "gemma3",
        importedAt = importedAt,
    )

    private class Fixture(
        val vm: ChatViewModel,
        val activeModelSelection: com.warped.domain.model.ActiveModelSelection,
        val engineManager: EngineManager,
        val modelsFlow: MutableStateFlow<List<LocalModel>>,
        val localSelectionFlow: MutableStateFlow<LocalSelection>,
        val remoteSelectionFlow: MutableStateFlow<RemoteSelection>,
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

        val modelsFlow = MutableStateFlow<List<LocalModel>>(emptyList())
        val localSelectionFlow =
            MutableStateFlow(LocalSelection(modelId = "/models/gemma.litertlm", isConnected = true))
        val remoteSelectionFlow = MutableStateFlow(
            RemoteSelection(modelId = "gpt", providerType = ProviderType.OPENAI, endpointId = 1L),
        )
        every { chatRepository.observeConversations() } returns MutableStateFlow(emptyList())
        every { endpointRepository.observeEndpoints() } returns MutableStateFlow(emptyList())
        every { localModelRepository.observeModels() } returns modelsFlow
        every { activeModelSelection.activeModel } returns MutableStateFlow(null)
        every { activeModelSelection.localSelection } returns localSelectionFlow
        every { activeModelSelection.remoteSelection } returns remoteSelectionFlow
        every { activeModelSelection.disconnectLocal() } answers {
            localSelectionFlow.value = LocalSelection()
        }
        every { activeModelSelection.clearRemote() } answers {
            remoteSelectionFlow.value = RemoteSelection()
        }
        every { activeModelSelection.selectLocalPending(any()) } answers {
            localSelectionFlow.value = LocalSelection(modelId = firstArg())
        }
        every { engineManager.scheduleUnload() } just Runs
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
        return Fixture(vm, activeModelSelection, engineManager, modelsFlow, localSelectionFlow, remoteSelectionFlow)
    }

    @Test
    fun `new chat unloads memory and clears both selections`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = build()
        runCurrent()

        f.vm.newConversation()
        runCurrent()

        verify { f.engineManager.scheduleUnload() }
        verify { f.activeModelSelection.disconnectLocal() }
        verify { f.activeModelSelection.clearRemote() }
    }

    @Test
    fun `new chat does not auto-reselect the newest file`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = build()
        val newest = localModel("/models/gemma.litertlm", Instant.now())
        // Models already on disk while a model is selected: no auto-pick.
        f.modelsFlow.value = listOf(newest)
        runCurrent()

        f.vm.newConversation()
        runCurrent()
        // The list re-emits after the clear — the hook must stay quiet.
        f.modelsFlow.value = emptyList()
        runCurrent()
        f.modelsFlow.value = listOf(newest)
        runCurrent()

        verify(exactly = 0) { f.activeModelSelection.selectLocalPending(any(), any()) }
    }

    @Test
    fun `nothing auto-selects after new chat, even for new files`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = build()
        runCurrent()

        f.vm.newConversation()
        runCurrent()
        // A download landing mid-picker must NOT hijack the choice —
        // the user is already looking at the selector.
        f.modelsFlow.value = listOf(localModel("/models/fresh.litertlm", Instant.now()))
        runCurrent()

        verify(exactly = 0) { f.activeModelSelection.selectLocalPending(any(), any()) }
    }

    @Test
    fun `explicit pick lifts the suppression for later downloads`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = build()
        runCurrent()

        f.vm.newConversation()
        runCurrent()
        // User picks from the selector: suppression lifts from here on.
        // (The stubbed engine dies inside the mount guard — selection
        // sticks, which is all this path needs.)
        f.vm.launchModelSelection("/models/fresh.litertlm", ProviderType.LITE_RT_LM, null)
        runCurrent()
        // Dismissing the (missing-model) banner drops both selections
        // WITHOUT re-arming suppression — the hook is live again.
        f.vm.dismissModelUnavailable()
        runCurrent()
        // A download landing on the fully-empty selection auto-picks.
        f.modelsFlow.value = listOf(localModel("/models/newer.litertlm", Instant.now()))
        runCurrent()

        verify { f.activeModelSelection.selectLocalPending("/models/newer.litertlm", null) }
    }
}
