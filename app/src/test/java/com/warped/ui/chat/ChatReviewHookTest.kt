package com.warped.ui.chat

import android.app.Activity
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
import com.warped.domain.model.ChatMessage
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
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
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
 * Phase 66 review-fix (WR-05): the ambient review hook fires only for
 * persisted user-visible turns.
 *
 * - A turn whose save succeeds fires `maybePrompt` exactly once.
 * - A turn whose save FAILED never fires the hook (no counter advance
 *   for an unpersisted turn).
 * - A null activity provider skips silently (previews/tests).
 *
 * Harness mirrors [ChatGroundingRetryTest]: strict mockk repository,
 * an answering inference helper (`Delta` + single `Done`), plain-text
 * messages (no URLs, so no fetch branch).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatReviewHookTest {

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

    private lateinit var chatRepository: ChatRepository
    private lateinit var reviewHelper: ReviewHelper
    private lateinit var inferenceHelper: LlmModelHelper

    private fun buildViewModel(modelPath: String): ChatViewModel {
        chatRepository = mockk()
        reviewHelper = mockk(relaxed = true)
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
        coEvery { chatRepository.saveMessage(any(), any()) } just Runs
        coEvery { chatRepository.saveMessageWithSources(any(), any(), any()) } returns 99L
        coEvery { chatRepository.replaceSources(any(), any(), any()) } just Runs
        coEvery { chatRepository.getWebOverride(any()) } returns null
        coEvery { chatRepository.setWebOverride(any(), any()) } just Runs
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
        every { advancedPreferences.webGroundingEnabled } returns flowOf(true)
        inferenceHelper = mockk<LlmModelHelper>().also { helper ->
            every { helper.type } returns ProviderType.LITE_RT_LM
            coEvery { helper.initialize(any()) } just Runs
            coEvery { helper.stopResponse() } just Runs
            every { helper.runInference(any(), any()) } returns flow {
                emit(StreamToken.Delta("hola"))
                emit(StreamToken.Done())
            }
        }
        every { providerRouter.resolveLocalHelper(any(), any()) } returns inferenceHelper

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
            reviewHelper = reviewHelper,
            fetcher = mockk { every { cancel() } just Runs },
            multiUrlFetcher = mockk(),
            ddgSearchRepository = mockk(),
            modelAllowlistRepository = mockk<com.warped.data.repository.ModelAllowlistRepository>().also(::stubEffectiveCapabilities),
            context = context,
        )
    }

    private fun assistantCount(vm: ChatViewModel) =
        vm.transcriptState.value.messages.count { it.role == Role.ASSISTANT }

    @Test
    fun `persisted turn fires review hook exactly once`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        vm.setReviewActivityProvider { mockk<Activity>() }

        vm.sendMessage("hola, que tal")
        advanceUntilIdle()

        assertThat(assistantCount(vm)).isEqualTo(1)
        coVerify(exactly = 1) { reviewHelper.maybePrompt(any()) }
    }

    @Test
    fun `failed persist never fires review hook`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        vm.setReviewActivityProvider { mockk<Activity>() }
        // WR-05: only the ASSISTANT (turn-completion) save fails — the
        // user-message save at the top of sendMessage must still succeed
        // or the turn never starts.
        coEvery { chatRepository.saveMessage(any(), any()) } coAnswers {
            if (secondArg<ChatMessage>().role == Role.ASSISTANT) {
                throw RuntimeException("disk full")
            }
        }

        vm.sendMessage("hola, que tal")
        advanceUntilIdle()

        // The bubble still renders (non-blocking failure), but the
        // unpersisted turn must not advance the review counter.
        assertThat(assistantCount(vm)).isEqualTo(1)
        coVerify(exactly = 0) { reviewHelper.maybePrompt(any()) }
    }

    @Test
    fun `null activity provider skips hook silently`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()

        vm.sendMessage("hola, que tal")
        advanceUntilIdle()

        assertThat(assistantCount(vm)).isEqualTo(1)
        coVerify(exactly = 0) { reviewHelper.maybePrompt(any()) }
    }
}
