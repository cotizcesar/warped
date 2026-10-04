package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.R
import com.warped.data.local.inference.ActiveEngine
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.EngineType
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.ProviderRouter
import com.warped.domain.llm.LlmModelHelper
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.Conversation
import com.warped.domain.model.LocalSelection
import com.warped.domain.model.ParameterStore
import com.warped.domain.model.ProviderType
import com.warped.domain.model.RemoteSelection
import com.warped.domain.model.Role
import com.warped.domain.model.SyntaxTheme
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
import java.time.Instant

/**
 * 2026-10-04 title-heal: a row still titled "New Chat" while carrying
 * user text is retitled from the beginning of what was written — on
 * open and on send. Textless rows keep the placeholder.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TitleHealTest {

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

    private val modelPath by lazy {
        File(tempDir, "m.litertlm").apply { writeText("fake") }.absolutePath
    }

    private fun userMessage(text: String) = ChatMessage(role = Role.USER, content = text)

    private class Fixture(
        val vm: ChatViewModel,
        val chatRepository: ChatRepository,
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
        every { context.getString(R.string.new_chat) } returns "New Chat"
        every { chatRepository.observeConversations() } returns MutableStateFlow(emptyList())
        coEvery { chatRepository.saveMessage(any(), any()) } just Runs
        coEvery { chatRepository.getWebOverride(any()) } returns null
        coEvery { chatRepository.updateConversationTitle(any(), any()) } just Runs
        coEvery { chatRepository.getConversationTitle(any()) } returns "New Chat"
        every { endpointRepository.observeEndpoints() } returns MutableStateFlow(emptyList())
        every { localModelRepository.observeModels() } returns MutableStateFlow(emptyList())
        coEvery { localModelRepository.existsByFilePath(any()) } returns true
        every { activeModelSelection.activeModel } returns MutableStateFlow(null)
        every { activeModelSelection.localSelection } returns MutableStateFlow(LocalSelection())
        every { activeModelSelection.remoteSelection } returns MutableStateFlow(RemoteSelection())
        every { activeModelSelection.selectLocalPending(any()) } just Runs
        every { activeModelSelection.clearRemote() } just Runs
        every { activeModelSelection.saveLastConversation(any()) } just Runs
        every { engineManager.getActiveEngine() } returns ActiveEngine(EngineType.LITE_RT_LM, modelPath)
        every { engineManager.scheduleUnload() } just Runs
        every { advancedPreferences.syntaxTheme } returns flowOf(SyntaxTheme.MONOKAI)
        every { advancedPreferences.codeFontScale } returns flowOf(1.0f)
        every { advancedPreferences.thinkingEnabled } returns flowOf(false)
        every { advancedPreferences.webGroundingEnabled } returns flowOf(false)
        val fetcher = mockk<com.warped.data.grounding.WebPageFetcher>()
        every { fetcher.cancel() } just Runs

        val helper = mockk<LlmModelHelper>()
        every { helper.type } returns ProviderType.LITE_RT_LM
        coEvery { helper.initialize(any()) } returns Unit
        coEvery { helper.stopResponse() } just Runs
        every { helper.runInference(any(), any()) } returns flow { awaitCancellation() }
        every { providerRouter.resolveLocalHelper(any(), any()) } returns helper

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
        return Fixture(vm, chatRepository)
    }

    private fun newChatConversation() = Conversation(
        id = 11,
        title = "New Chat",
        providerType = ProviderType.LITE_RT_LM,
        endpointId = 0,
        modelId = modelPath,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `opening a default-titled chat with text retitles from the first message`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = build()
        coEvery { f.chatRepository.loadConversation(11) } returns (
            newChatConversation() to listOf(userMessage("hola cómo te va?"))
            )
        runCurrent()

        f.vm.selectConversation(11)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            f.chatRepository.updateConversationTitle(11, "hola cómo te va?")
        }
    }

    @Test
    fun `sending in a default-titled chat with history retitles from the new message`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = build()
        coEvery { f.chatRepository.loadConversation(11) } returns (
            newChatConversation() to listOf(userMessage("hola cómo te va?"))
            )
        runCurrent()
        f.vm.selectConversation(11)
        advanceUntilIdle()

        f.vm.sendMessage("como te sientes")
        advanceUntilIdle()

        coVerify { f.chatRepository.updateConversationTitle(11, "como te sientes") }
    }

    @Test
    fun `textless default-titled chats keep the placeholder`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = build()
        coEvery { f.chatRepository.loadConversation(11) } returns (
            newChatConversation() to emptyList()
            )
        runCurrent()

        f.vm.selectConversation(11)
        advanceUntilIdle()

        coVerify(exactly = 0) {
            f.chatRepository.updateConversationTitle(any(), any())
        }
    }
}
