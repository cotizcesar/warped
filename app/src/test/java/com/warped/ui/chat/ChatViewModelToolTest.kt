package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.LiteRTLmProvider
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.ProviderRouter
import com.warped.domain.llm.LlmModelHelper
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.GenerationParameters
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
import com.warped.domain.skills.SkillIds
import com.warped.ui.chat.components.NO_TOOL_SUPPORT_NOTICE
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * 47-03 (SKILLS-11/SKILLS-12): ViewModel tool integration.
 *
 * Drives the REAL ChatViewModel with scripted helpers emitting the 47-02/47-03
 * token stream: ToolStatus set/clear, ToolCompleted persistence + error rows,
 * and the once-per-turn no-support notice with its exact copy string.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelToolTest {

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

    private fun buildViewModel(
        helper: LlmModelHelper,
        modelPath: String,
        skillsOn: Boolean = true,
        chatRepository: ChatRepository = mockk(),
        providerRouter: ProviderRouter = mockk(),
    ): ChatViewModel {
        val endpointRepository = mockk<EndpointRepository>()
        val localModelRepository = mockk<LocalModelRepository>()
        val activeModelSelection = mockk<com.warped.domain.model.ActiveModelSelection>()
        val engineManager = mockk<EngineManager>()
        val memoryChecker = mockk<MemoryChecker>()
        val advancedPreferences = mockk<AdvancedPreferences>()
        val skillRepository = mockk<com.warped.domain.skills.SkillRepository>()
        // 47-03: allowlist gates CLOSED (findByModelFile → null) — the
        // no-support notice path. Skills all-on or all-off per test.
        val modelAllowlistRepository = mockk<com.warped.data.repository.ModelAllowlistRepository>()
        val liteRTLmProvider = mockk<LiteRTLmProvider>()
        val context = mockk<Context>()

        every { chatRepository.observeConversations() } returns MutableStateFlow(emptyList())
        coEvery { chatRepository.createConversation(any(), any(), any(), any()) } returns 42L
        coEvery { chatRepository.saveMessage(any(), any()) } returns Unit
        every { endpointRepository.observeEndpoints() } returns MutableStateFlow(emptyList())
        every { localModelRepository.observeModels() } returns MutableStateFlow(emptyList())
        every { activeModelSelection.activeModel } returns MutableStateFlow(null)
        every { activeModelSelection.localSelection } returns
            MutableStateFlow(LocalSelection(modelId = modelPath, isConnected = true))
        every { activeModelSelection.remoteSelection } returns MutableStateFlow(RemoteSelection())
        every { activeModelSelection.saveLastConversation(any()) } just Runs
        every { advancedPreferences.syntaxTheme } returns flowOf(SyntaxTheme.MONOKAI)
        every { advancedPreferences.codeFontScale } returns flowOf(1.0f)
        every { advancedPreferences.thinkingEnabled } returns flowOf(false)
        every { skillRepository.enabledMap } returns
            MutableStateFlow(SkillIds.TOOL_IDS.associateWith { skillsOn })
        every { modelAllowlistRepository.findByModelFile(any()) } returns null
        every { liteRTLmProvider.resetConversation() } just Runs
        every { providerRouter.resolveLocalHelper(any(), any()) } returns helper

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
            skillRepository = skillRepository,
            modelAllowlistRepository = modelAllowlistRepository,
            liteRTLmProvider = liteRTLmProvider,
            context = context,
        )
    }

    private fun helperOf(vararg tokens: StreamToken): LlmModelHelper {
        val helper = mockk<LlmModelHelper>()
        every { helper.type } returns ProviderType.LITE_RT_LM
        coEvery { helper.initialize(any()) } returns Unit
        coEvery { helper.stopResponse() } returns Unit
        every { helper.runInference(any(), any()) } returns flow {
            tokens.forEach { emit(it) }
        }
        return helper
    }

    // ------------------------------------------------------------------
    // ToolStatus → toolCallActive set/clear sequence (Turbine).
    // ------------------------------------------------------------------

    @Test
    fun `ToolStatus tokens drive the status row set then cleared`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val gate = CompletableDeferred<Unit>()
        val helper = mockk<LlmModelHelper>()
        every { helper.type } returns ProviderType.LITE_RT_LM
        coEvery { helper.initialize(any()) } returns Unit
        coEvery { helper.stopResponse() } returns Unit
        every { helper.runInference(any(), any()) } returns flow {
            emit(StreamToken.ToolStatus("calculator"))
            gate.await() // hold the status row open until the test observes it
            emit(StreamToken.ToolStatus(null))
            emit(StreamToken.Delta("twenty"))
            emit(StreamToken.Done())
        }

        val vm = buildViewModel(helper, modelFile.absolutePath)
        runCurrent()

        vm.sendMessage("2*10")
        // Pump the test scheduler until the live status row appears.
        var sawActive = false
        repeat(100) {
            runCurrent()
            if (vm.uiState.value.toolCallActive == "calculator") {
                sawActive = true
                if (!gate.isCompleted) gate.complete(Unit)
                return@repeat
            }
            advanceTimeBy(50)
        }
        assertThat(sawActive).isTrue()
        advanceUntilIdle()

        // Terminal: status cleared, answer persisted.
        val terminal = vm.uiState.value
        assertThat(terminal.toolCallActive).isNull()
        assertThat(
            terminal.messages.any { it.role == Role.ASSISTANT && it.content == "twenty" },
        ).isTrue()
    }

    @Test
    fun `ToolStatus null leaves no stale status after Done`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(
            helperOf(
                StreamToken.ToolStatus("current_time"),
                StreamToken.ToolStatus(null),
                StreamToken.Delta("noon"),
                StreamToken.Done(),
            ),
            modelFile.absolutePath,
        )
        runCurrent()

        vm.sendMessage("time?")
        advanceUntilIdle()

        assertThat(vm.uiState.value.toolCallActive).isNull()
        assertThat(vm.uiState.value.isStreaming).isFalse()
    }

    // ------------------------------------------------------------------
    // Failure → error row + non-blank fallback + tool-row persistence.
    // ------------------------------------------------------------------

    @Test
    fun `tool failure sets error row with non-blank fallback and persists tool row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val chatRepository = mockk<ChatRepository>()
        val vm = buildViewModel(
            helperOf(
                StreamToken.ToolStatus("calculator"),
                StreamToken.ToolCompleted("calculator", "invalid expression", "invalid expression"),
                StreamToken.Delta("I could not compute that directly."),
                StreamToken.Done(),
            ),
            modelFile.absolutePath,
            chatRepository = chatRepository,
        )
        runCurrent()

        vm.sendMessage("(2+)*4")
        advanceUntilIdle()

        val state = vm.uiState.value
        // Error row state carries the exact tool + reason.
        assertThat(state.activeToolError).isEqualTo(
            ActiveToolError(toolId = "calculator", reason = "invalid expression"),
        )
        // Fallback answer is non-blank and persisted.
        val assistants = state.messages.filter { it.role == Role.ASSISTANT }
        assertThat(assistants).hasSize(1)
        assertThat(assistants.single().content).isNotEmpty()
        // One role:tool row persisted in the 47-01 encoding.
        val toolRows = state.messages.filter { it.role == Role.TOOL }
        assertThat(toolRows).hasSize(1)
        assertThat(toolRows.single().content).isEqualTo("calculator\ninvalid expression")
        coVerify { chatRepository.saveMessage(42L, toolRows.single()) }
        coVerify { chatRepository.saveMessage(42L, assistants.single()) }
        assertThat(state.isStreaming).isFalse()
        assertThat(state.toolCallActive).isNull()
    }

    // ------------------------------------------------------------------
    // No-support notice: exact string, once per turn, silent when disabled.
    // ------------------------------------------------------------------

    @Test
    fun `unsupported model shows the exact no-support notice once per turn`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(
            helperOf(StreamToken.Delta("plain answer"), StreamToken.Done()),
            modelFile.absolutePath,
            skillsOn = true,
        )
        runCurrent()

        // Copy contract: the ViewModel flag drives the verbatim composable.
        assertThat(NO_TOOL_SUPPORT_NOTICE)
            .isEqualTo("This model doesn't support tools — answering directly.")

        vm.sendMessage("hi")
        advanceUntilIdle()

        assertThat(vm.uiState.value.showNoToolSupportNotice).isTrue()
    }

    @Test
    fun `zero skills enabled stays silent with plain chat`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(
            helperOf(StreamToken.Delta("plain"), StreamToken.Done()),
            modelFile.absolutePath,
            skillsOn = false,
        )
        runCurrent()

        vm.sendMessage("hi")
        advanceUntilIdle()

        assertThat(vm.uiState.value.showNoToolSupportNotice).isFalse()
        assertThat(vm.uiState.value.messages.filter { it.role == Role.TOOL }).isEmpty()
    }

    // ------------------------------------------------------------------
    // Success records persist without raising an error row.
    // ------------------------------------------------------------------

    @Test
    fun `successful tool persists row with no error state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(
            helperOf(
                StreamToken.ToolStatus("json_formatter"),
                StreamToken.ToolCompleted("json_formatter", "{\"a\": 1}"),
                StreamToken.Delta("Formatted."),
                StreamToken.Done(),
            ),
            modelFile.absolutePath,
        )
        runCurrent()

        vm.sendMessage("format it")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.activeToolError).isNull()
        val toolRows = state.messages.filter { it.role == Role.TOOL }
        assertThat(toolRows).hasSize(1)
        assertThat(toolRows.single().content).isEqualTo("json_formatter\n{\"a\": 1}")
    }
}
