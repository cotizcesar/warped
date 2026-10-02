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
import com.warped.domain.llm.LlmModelHelper
import com.warped.domain.model.LocalSelection
import com.warped.domain.model.ParameterStore
import com.warped.domain.model.ProviderType
import com.warped.domain.model.RemoteSelection
import com.warped.domain.model.Role
import com.warped.domain.model.StreamToken
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * 48-01 (PERF-14): sub-state isolation proof.
 *
 * An inputText update must emit ONLY on the input flow (transcript and
 * connection flow references unchanged), and a streaming/token update must
 * emit ONLY on the transcript flow (input flow reference unchanged
 * mid-stream — boundary flips like the send-clear/isGenerating mirror are
 * turn-boundary emissions, not token emissions).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSubStateTest {

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

    private fun buildViewModel(helper: LlmModelHelper, modelPath: String): ChatViewModel {
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
        every { endpointRepository.observeEndpoints() } returns MutableStateFlow(emptyList())
        every { localModelRepository.observeModels() } returns MutableStateFlow(emptyList())
        every { activeModelSelection.activeModel } returns MutableStateFlow(null)
        every { activeModelSelection.localSelection } returns
            MutableStateFlow(LocalSelection(modelId = modelPath, isConnected = true))
        // Lazy load: the send path mounts only on engine-path mismatch —
        // stub the engine as already serving this model.
        every { engineManager.getActiveEngine() } returns
            ActiveEngine(EngineType.LITE_RT_LM, modelPath)
        every { activeModelSelection.remoteSelection } returns MutableStateFlow(RemoteSelection())
        every { activeModelSelection.saveLastConversation(any()) } just Runs
        every { advancedPreferences.syntaxTheme } returns flowOf(SyntaxTheme.MONOKAI)
        every { advancedPreferences.codeFontScale } returns flowOf(1.0f)
        every { advancedPreferences.thinkingEnabled } returns flowOf(false)
        every { advancedPreferences.webGroundingEnabled } returns flowOf(false)
        every { providerRouter.resolveLocalHelper(any(), any()) } returns helper
        val fetcher = mockk<com.warped.data.grounding.WebPageFetcher>()
        every { fetcher.cancel() } just Runs
        val multiUrlFetcher = mockk<com.warped.data.grounding.MultiUrlFetcher>()

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
            multiUrlFetcher = multiUrlFetcher,
            ddgSearchRepository = mockk(),
            modelAllowlistRepository = mockk<com.warped.data.repository.ModelAllowlistRepository>().also(::stubEffectiveCapabilities),
            context = context,
            reviewHelper = mockk(relaxed = true),
        )
    }

    private fun idleHelper(): LlmModelHelper {
        val helper = mockk<LlmModelHelper>()
        every { helper.type } returns ProviderType.LITE_RT_LM
        coEvery { helper.initialize(any()) } returns Unit
        coEvery { helper.stopResponse() } returns Unit
        every { helper.runInference(any(), any()) } returns flow { }
        return helper
    }

    @Test
    fun `input update emits only on the input flow`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(idleHelper(), modelFile.absolutePath)
        runCurrent()

        val transcriptBefore = vm.transcriptState.value
        val connectionBefore = vm.connectionState.value

        vm.updateInput("hello")
        runCurrent()

        assertThat(vm.inputState.value.inputText).isEqualTo("hello")
        assertThat(vm.transcriptState.value).isSameInstanceAs(transcriptBefore)
        assertThat(vm.connectionState.value).isSameInstanceAs(connectionBefore)
    }

    @Test
    fun `streaming token update emits only on the transcript flow`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val gate = CompletableDeferred<Unit>()
        val helper = mockk<LlmModelHelper>()
        every { helper.type } returns ProviderType.LITE_RT_LM
        coEvery { helper.initialize(any()) } returns Unit
        coEvery { helper.stopResponse() } returns Unit
        every { helper.runInference(any(), any()) } returns flow {
            // Phase 49 (DEL-01): legacy ToolStatus tokens carry no text and
            // are ignored by the single-turn ViewModel.
            emit(StreamToken.ToolStatus("calculator"))
            gate.await() // hold the turn open mid-stream
            emit(StreamToken.ToolStatus(null))
            emit(StreamToken.Delta("twenty"))
            emit(StreamToken.Done())
        }

        val vm = buildViewModel(helper, modelFile.absolutePath)
        runCurrent()

        vm.sendMessage("2*10")
        // Pump until the mid-stream streaming flag lands on the transcript flow.
        var sawStreaming = false
        repeat(100) {
            runCurrent()
            if (vm.transcriptState.value.isStreaming) {
                sawStreaming = true
                return@repeat
            }
            testScheduler.advanceTimeBy(50)
        }
        assertThat(sawStreaming).isTrue()

        // Mid-stream: the input flow reference is untouched by the token
        // emission (keystrokes and tokens live on different flows). The
        // connection reference is captured here too — past the one-time
        // turn-boundary conversation creation — and must not move again.
        val inputMidStream = vm.inputState.value
        val connectionMidStream = vm.connectionState.value
        assertThat(inputMidStream.inputText).isEmpty()
        runCurrent()
        assertThat(vm.inputState.value).isSameInstanceAs(inputMidStream)
        assertThat(vm.connectionState.value).isSameInstanceAs(connectionMidStream)

        gate.complete(Unit)
        advanceUntilIdle()

        // Terminal: answer persisted, stream closed, draft still empty.
        val terminal = vm.transcriptState.value
        assertThat(terminal.isStreaming).isFalse()
        assertThat(
            terminal.messages.any { it.role == Role.ASSISTANT && it.content == "twenty" },
        ).isTrue()
        assertThat(vm.inputState.value.inputText).isEmpty()
    }
}
