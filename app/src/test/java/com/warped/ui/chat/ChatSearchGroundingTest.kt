package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.GroundingPrompt
import com.warped.data.grounding.GroundingResult
import com.warped.data.grounding.LanguageDetectorHolder
import com.warped.data.grounding.MultiUrlFetcher
import com.warped.data.grounding.MultiUrlResult
import com.warped.data.grounding.SearchOutcome
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.ProviderRouter
import com.warped.domain.llm.LlmModelHelper
import com.warped.domain.model.ActiveModelSelection
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.GroundedSourceStatus
import com.warped.domain.model.LocalSelection
import com.warped.domain.model.ModelOnlyNotice
import com.warped.domain.model.ParameterStore
import com.warped.domain.model.ProviderType
import com.warped.domain.model.RemoteSelection
import com.warped.domain.model.Role
import com.warped.domain.model.StreamToken
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
import io.mockk.slot
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
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Phase 63 (DDG-only): ChatViewModel search-branch gate matrix + fusion
 * wiring against the keyless [SearchOutcome] contract.
 *
 * - The gate matrix pins: grounding-off and offline never reach search;
 *   DDG failure yields the FETCH_FAILED notice with no key nag.
 * - The wiring test pins fusion identity: a Fused search result reaches
 *   GroundingPrompt.augment + groundedSources + the saveMessageWithSources
 *   details union exactly like the URL path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSearchGroundingTest {

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

    private fun fusedSearchResult() = MultiUrlResult.Fused(
        block = "--- Source [1]: https://t.example/a ---\nSnippet a\n--- End of sources ---",
        okUrls = listOf("https://t.example/a"),
        skippedUrls = listOf("https://blank.example/x"),
        pageTexts = mapOf("https://t.example/a" to "Snippet a"),
        details = listOf(
            GroundedSource(
                url = "https://t.example/a",
                extractedText = "Snippet a",
                status = GroundedSourceStatus.OK,
            ),
            GroundedSource(
                url = "https://blank.example/x",
                extractedText = null,
                status = GroundedSourceStatus.OMITIDA,
            ),
        ),
    )

    private lateinit var chatRepository: ChatRepository
    private lateinit var chatDdgRepo: DuckDuckGoSearchRepository
    private lateinit var chatFetcher: WebPageFetcher
    private lateinit var chatMultiUrlFetcher: MultiUrlFetcher
    private lateinit var lastHelper: LlmModelHelper

    private fun buildChatViewModel(modelPath: String, online: Boolean): ChatViewModel {
        chatRepository = mockk()
        val endpointRepository = mockk<EndpointRepository>()
        val localModelRepository = mockk<LocalModelRepository>()
        val activeModelSelection = mockk<ActiveModelSelection>()
        val providerRouter = mockk<ProviderRouter>()
        val engineManager = mockk<EngineManager>()
        val memoryChecker = mockk<MemoryChecker>()
        val advancedPreferences = mockk<AdvancedPreferences>()
        val context = mockk<Context>()

        every { chatRepository.observeConversations() } returns MutableStateFlow(emptyList())
        coEvery { chatRepository.createConversation(any(), any(), any(), any()) } returns 42L
        coEvery { chatRepository.saveMessage(any(), any()) } just Runs
        coEvery { chatRepository.saveMessageWithSources(any(), any(), any()) } returns 99L
        coEvery { chatRepository.getWebOverride(any()) } returns null
        coEvery { chatRepository.setWebOverride(any(), any()) } just Runs
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
        every { advancedPreferences.webGroundingEnabled } returns flowOf(true)
        every { providerRouter.resolveLocalHelper(any(), any()) } returns answeringHelper()
        chatFetcher = mockk()
        every { chatFetcher.cancel() } just Runs
        every { chatFetcher.hasValidatedInternet() } returns online
        chatMultiUrlFetcher = mockk()
        chatDdgRepo = mockk()

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
            fetcher = chatFetcher,
            multiUrlFetcher = chatMultiUrlFetcher,
            ddgSearchRepository = chatDdgRepo,
            modelAllowlistRepository = mockk<com.warped.data.repository.ModelAllowlistRepository>(),
            context = context,
        )
    }

    private fun answeringHelper(): LlmModelHelper {
        val helper = mockk<LlmModelHelper>()
        every { helper.type } returns ProviderType.LITE_RT_LM
        coEvery { helper.initialize(any()) } just Runs
        coEvery { helper.stopResponse() } just Runs
        every { helper.runInference(any(), any()) } returns kotlinx.coroutines.flow.flow {
            emit(StreamToken.Delta("hola"))
            emit(StreamToken.Done())
        }
        lastHelper = helper
        return helper
    }

    private fun assistantOf(vm: ChatViewModel) =
        vm.transcriptState.value.messages.last { it.role == Role.ASSISTANT }

    @Test
    fun `grounding-off never calls search and sends original untouched`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildChatViewModel(modelFile.absolutePath, online = true)
        runCurrent()
        coEvery { chatRepository.getWebOverride(any()) } returns false

        vm.sendMessage("hola sin urls")
        advanceUntilIdle()

        coVerify(exactly = 0) { chatDdgRepo.search(any(), any(), any()) }
        coVerify(exactly = 0) { chatMultiUrlFetcher.fetchAll(any(), any(), any()) }
        val requestSlot = slot<ChatRequest>()
        coVerify(exactly = 1) { lastHelper.runInference(capture(requestSlot), any()) }
        assertThat(requestSlot.captured.messages.last().content).isEqualTo("hola sin urls")
    }

    @Test
    fun `offline never calls search and yields offline notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildChatViewModel(modelFile.absolutePath, online = false)
        runCurrent()

        // Quick-task (needs-web-gate): non-social fixture — "hola" is a
        // locked social token and would skip the pre-search by design.
        // Quick-task (langdetect-library): the detector is warmed
        // synchronously so the directive below is deterministic (the
        // tildeless fixture now — correctly — yields the SPANISH directive).
        LanguageDetectorHolder.resetForTest()
        LanguageDetectorHolder.ensureLoadedBlocking()
        vm.sendMessage("pregunta sin urls")
        advanceUntilIdle()

        // No socket: neither the fetcher fan-out nor the search producer runs.
        coVerify(exactly = 0) { chatDdgRepo.search(any(), any(), any()) }
        coVerify(exactly = 0) { chatMultiUrlFetcher.fetchAll(any(), any(), any()) }
        assertThat(assistantOf(vm).modelOnlyNotice).isEqualTo(ModelOnlyNotice.OFFLINE)
        // The always-on web instruction is preserved on the model-only turn.
        val requestSlot = slot<ChatRequest>()
        coVerify(exactly = 1) { lastHelper.runInference(capture(requestSlot), any()) }
        assertThat(requestSlot.captured.messages.last().content).isEqualTo(
            "${GroundingPrompt.SYSTEM_PROMPT}\n\npregunta sin urls\n\nResponde en español, aunque las fuentes estén en inglés.",
        )
    }

    @Test
    fun `search failure yields fetch-failed notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildChatViewModel(modelFile.absolutePath, online = true)
        runCurrent()
        coEvery { chatDdgRepo.search(any(), any(), any()) } returns SearchOutcome.ModelOnly(
            MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
        )

        vm.sendMessage("que hay de nuevo")
        advanceUntilIdle()

        assertThat(assistantOf(vm).modelOnlyNotice).isEqualTo(ModelOnlyNotice.FETCH_FAILED)
    }

    @Test
    fun `fused search result augments and persists identically to url path`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildChatViewModel(modelFile.absolutePath, online = true)
        runCurrent()
        val fused = fusedSearchResult()
        coEvery { chatDdgRepo.search(any(), any(), any()) } returns
            SearchOutcome.Grounded(fused)

        vm.sendMessage("que hay de nuevo")
        advanceUntilIdle()

        // Same block format the URL path asserts (numbered Source [N]).
        coVerify(exactly = 1) { chatDdgRepo.search("que hay de nuevo", 5, any()) }
        val requestSlot = slot<ChatRequest>()
        coVerify(exactly = 1) { lastHelper.runInference(capture(requestSlot), any()) }
        assertThat(requestSlot.captured.messages.last().content).isEqualTo(
            GroundingPrompt.augment("que hay de nuevo", fused.block, groundingEnabled = true),
        )
        assertThat(requestSlot.captured.messages.last().content).contains("--- Source [1]:")
        // Same downstream: okUrls render list + details-union persist.
        val assistant = assistantOf(vm)
        assertThat(assistant.modelOnlyNotice).isNull()
        assertThat(assistant.groundedSources).containsExactly("https://t.example/a")
        val sourcesSlot = slot<List<GroundedSource>>()
        coVerify(exactly = 1) {
            chatRepository.saveMessageWithSources(42L, any(), capture(sourcesSlot))
        }
        assertThat(sourcesSlot.captured.map { it.url }).containsExactly(
            "https://t.example/a",
            "https://blank.example/x",
        ).inOrder()
        assertThat(sourcesSlot.captured[0].status).isEqualTo(GroundedSourceStatus.OK)
        assertThat(sourcesSlot.captured[1].status).isEqualTo(GroundedSourceStatus.OMITIDA)
    }
}
