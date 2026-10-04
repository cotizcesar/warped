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
import com.warped.domain.model.Conversation
import com.warped.domain.model.LocalSelection
import com.warped.domain.model.ParameterStore
import com.warped.domain.model.ProviderType
import com.warped.domain.model.RemoteSelection
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
 * 2026-10-04 fresh-row contract: New chat creates the DB row
 * immediately WITHOUT a model (nothing preselected — the picker
 * decides); the first send claims the row for the chosen model, and
 * repeated New chats reuse the still-empty row instead of littering.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FreshRowTest {

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

    private class Fixture(
        val vm: ChatViewModel,
        val chatRepository: ChatRepository,
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
        coEvery { chatRepository.createConversation(any(), any(), any(), any()) } returns 100L
        coEvery { chatRepository.loadConversation(any()) } returns null
        coEvery { chatRepository.saveMessage(any(), any()) } returns Unit
        coEvery { chatRepository.getWebOverride(any()) } returns null
        coEvery { chatRepository.updateConversationBinding(any(), any(), any(), any()) } just Runs
        coEvery { chatRepository.updateConversationTitle(any(), any()) } just Runs
        every { endpointRepository.observeEndpoints() } returns MutableStateFlow(emptyList())
        every { localModelRepository.observeModels() } returns MutableStateFlow(emptyList())
        every { activeModelSelection.activeModel } returns MutableStateFlow(null)
        every { activeModelSelection.localSelection } returns
            MutableStateFlow(LocalSelection(modelId = modelPath, isConnected = modelPath != null))
        every { activeModelSelection.remoteSelection } returns MutableStateFlow(RemoteSelection())
        every { activeModelSelection.disconnectLocal() } just Runs
        every { activeModelSelection.clearRemote() } just Runs
        every { activeModelSelection.saveLastConversation(any()) } just Runs
        every { engineManager.getActiveEngine() } returns
            modelPath?.let { ActiveEngine(EngineType.LITE_RT_LM, it) }
        every { engineManager.scheduleUnload() } just Runs
        every { advancedPreferences.syntaxTheme } returns flowOf(SyntaxTheme.MONOKAI)
        every { advancedPreferences.codeFontScale } returns flowOf(1.0f)
        every { advancedPreferences.thinkingEnabled } returns flowOf(false)
        every { advancedPreferences.webGroundingEnabled } returns flowOf(false)
        val fetcher = mockk<com.warped.data.grounding.WebPageFetcher>()
        every { fetcher.cancel() } just Runs
        val multiUrlFetcher = mockk<com.warped.data.grounding.MultiUrlFetcher>()

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
            multiUrlFetcher = multiUrlFetcher,
            ddgSearchRepository = mockk(),
            modelAllowlistRepository = mockk(relaxed = true),
            context = context,
            reviewHelper = mockk(relaxed = true),
        )
        return Fixture(vm, chatRepository)
    }

    @Test
    fun `new chat creates an unbound row and selects nothing`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = build(modelPath = null)
        runCurrent()

        f.vm.newConversation()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            f.chatRepository.createConversation(
                title = any(),
                providerType = any(),
                modelId = null,
                endpointId = 0,
            )
        }
        assertThat(f.vm.transcriptState.value.conversationId).isEqualTo(100L)
        assertThat(f.vm.connectionState.value.selectedLocalModelId).isNull()
        assertThat(f.vm.connectionState.value.selectedRemoteModelId).isNull()
    }

    @Test
    fun `second new chat without send reuses the empty row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = build(modelPath = null)
        coEvery { f.chatRepository.loadConversation(100) } returns (
            Conversation(
                id = 100,
                title = "New Chat",
                providerType = ProviderType.LITE_RT_LM,
                endpointId = 0,
                modelId = null,
                createdAt = java.time.Instant.EPOCH,
                updatedAt = java.time.Instant.EPOCH,
            ) to emptyList()
            )
        runCurrent()

        f.vm.newConversation()
        advanceUntilIdle()
        f.vm.newConversation()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            f.chatRepository.createConversation(any(), any(), any(), any())
        }
    }

    @Test
    fun `first send claims the fresh row for the chosen model`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "m.litertlm").apply { writeText("fake") }
        val f = build(modelFile.absolutePath)
        runCurrent()

        f.vm.newConversation()
        advanceUntilIdle()
        f.vm.sendMessage("hola")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            f.chatRepository.updateConversationBinding(
                conversationId = 100L,
                providerType = ProviderType.LITE_RT_LM,
                modelId = modelFile.absolutePath,
                endpointId = 0,
            )
        }
    }
}
