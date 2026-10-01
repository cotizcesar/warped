package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.GroundingPrompt
import com.warped.data.grounding.MultiUrlFetcher
import com.warped.data.grounding.MultiUrlResult
import com.warped.data.grounding.TavilySearchOutcome
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.local.security.ApiKeyStore
import com.warped.data.remote.provider.ProviderRouter
import com.warped.data.repository.AllowlistCapabilities
import com.warped.data.repository.AllowlistedModel
import com.warped.data.repository.ModelAllowlistRepository
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
 * Quick-task (code-intent-gate): code-generation turns skip the always-on
 * pre-search (no socket, no credit, no notice — identical to
 * grounding-off), while factual and code+URL turns are byte-identical to
 * today. The agentic loop arming is untouched (the model's escape hatch for
 * versioned/fresh API facts).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatCodeIntentGateTest {

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
    private lateinit var fetcher: WebPageFetcher
    private lateinit var multiUrlFetcher: MultiUrlFetcher
    private lateinit var ddgSearchRepository: DuckDuckGoSearchRepository
    private lateinit var lastHelper: LlmModelHelper

    private fun buildViewModel(
        modelPath: String,
        online: Boolean = true,
        supportsFunctionCalling: Boolean = true,
        tavilyKey: CharArray? = null,
    ): ChatViewModel {
        chatRepository = mockk()
        val endpointRepository = mockk<EndpointRepository>()
        val localModelRepository = mockk<LocalModelRepository>()
        val activeModelSelection = mockk<ActiveModelSelection>()
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
        fetcher = mockk()
        every { fetcher.cancel() } just Runs
        every { fetcher.hasValidatedInternet() } returns online
        val allowlist = mockk<ModelAllowlistRepository>().also(::stubEffectiveCapabilities)
        every { allowlist.findByModelFile(any()) } returns AllowlistedModel(
            name = "tiny",
            displayName = "Tiny",
            modelFile = "tiny.litertlm",
            sizeInBytes = 1L,
            capabilities = AllowlistCapabilities(
                supportsFunctionCalling = supportsFunctionCalling,
            ),
        )
        ddgSearchRepository = mockk()
        val apiKeyStore = mockk<ApiKeyStore>()
        every { apiKeyStore.getTavilyKey() } answers { tavilyKey?.copyOf() }
        multiUrlFetcher = mockk()

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
            ddgSearchRepository = ddgSearchRepository,
            apiKeyStore = apiKeyStore,
            modelAllowlistRepository = allowlist,
            context = context,
        )
    }

    private fun answeringHelper(): LlmModelHelper {
        val helper = mockk<LlmModelHelper>()
        every { helper.type } returns ProviderType.LITE_RT_LM
        coEvery { helper.initialize(any()) } just Runs
        coEvery { helper.stopResponse() } just Runs
        every { helper.runInference(any(), any()) } returns flow {
            emit(StreamToken.Delta("hola"))
            emit(StreamToken.Done())
        }
        lastHelper = helper
        return helper
    }

    private fun groundedOutcome() = TavilySearchOutcome.Grounded(
        MultiUrlResult.Fused(
            block = "--- Source [1]: https://a.example/uno ---\nTexto a.\n--- End of sources ---",
            okUrls = listOf("https://a.example/uno"),
            skippedUrls = emptyList(),
            pageTexts = mapOf("https://a.example/uno" to "Texto a."),
            details = listOf(
                GroundedSource(
                    url = "https://a.example/uno",
                    extractedText = "Texto a.",
                    status = GroundedSourceStatus.OK,
                ),
            ),
        ),
    )

    private fun fusedFetch(url: String) = MultiUrlResult.Fused(
        block = "--- Source [1]: $url ---\nPage text.\n--- End of sources ---",
        okUrls = listOf(url),
        skippedUrls = emptyList(),
        pageTexts = mapOf(url to "Page text."),
        details = listOf(
            GroundedSource(
                url = url,
                extractedText = "Page text.",
                status = GroundedSourceStatus.OK,
            ),
        ),
    )

    @Test
    fun `code turn skips search with no socket and plain transcript`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns groundedOutcome()

        // The exact on-device evidence message.
        vm.sendMessage("Dame un ejemplo de codigo simple en jsavascript")
        advanceUntilIdle()

        coVerify(exactly = 0) {
            ddgSearchRepository.search(any(), any(), any(), any())
        }
        // Single init-time connectivity refresh; the gated turn adds zero.
        coVerify(exactly = 1) { fetcher.hasValidatedInternet() }
        // No augment on gated turns: the outgoing request carries the
        // original text byte-identical (no SYSTEM_PROMPT, no fused block).
        val requestSlot = slot<ChatRequest>()
        coVerify(exactly = 1) { lastHelper.runInference(capture(requestSlot), any()) }
        assertThat(requestSlot.captured.messages.last().content)
            .isEqualTo("Dame un ejemplo de codigo simple en jsavascript")
        assertThat(requestSlot.captured.messages.last().content).doesNotContain(GroundingPrompt.SYSTEM_PROMPT)
        // The turn still completes: the model answers, with no notice (the
        // skip is deliberate, not a failure) and no grounded rows.
        assertThat(
            vm.transcriptState.value.messages.any {
                it.role == Role.ASSISTANT &&
                    it.content == "hola" &&
                    it.modelOnlyNotice == null &&
                    it.groundedSources.isEmpty()
            },
        ).isTrue()
    }

    @Test
    fun `factual turn still runs the pre-search`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns groundedOutcome()

        vm.sendMessage("que es la fotosintesis?")
        advanceUntilIdle()

        // Guard against gate over-blocking: factual turns fuse as today.
        coVerify(exactly = 1) {
            ddgSearchRepository.search("que es la fotosintesis?", any(), any(), any())
        }
        val requestSlot = slot<ChatRequest>()
        coVerify(exactly = 1) { lastHelper.runInference(capture(requestSlot), any()) }
        assertThat(requestSlot.captured.messages.last().content).contains("--- Source [1]")
        assertThat(
            vm.transcriptState.value.messages.any {
                it.role == Role.ASSISTANT &&
                    it.groundedSources == listOf("https://a.example/uno")
            },
        ).isTrue()
    }

    @Test
    fun `code text alongside a pasted URL still fetches`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        val url = "https://example.com/x"
        coEvery {
            multiUrlFetcher.fetchAll(any(), any(), any())
        } returns fusedFetch(url)

        vm.sendMessage("escribe una funcion $url")
        advanceUntilIdle()

        // The gate lives in the no-URL branch only — URL turns never
        // consult it, even when the text is a code turn.
        coVerify(exactly = 1) { multiUrlFetcher.fetchAll(listOf(url), any(), any()) }
        coVerify(exactly = 0) {
            ddgSearchRepository.search(any(), any(), any(), any())
        }
        assertThat(
            vm.transcriptState.value.messages.any {
                it.role == Role.ASSISTANT && it.groundedSources == listOf(url)
            },
        ).isTrue()
    }
}
