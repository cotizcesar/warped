package com.warped.ui.chat

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.GroundingPrompt
import com.warped.data.grounding.GroundingResult
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
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
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
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Quick-task (attachments-skip-search): the heuristic no-URL pre-search
 * ([DuckDuckGoSearchRepository.search]) is SKIPPED on turns carrying image
 * or audio attachments. v2.4 Phase 55 regression: blind text search on an
 * image-question turn injects junk context about the question words while
 * the question is about the attachment, and the small model answers from
 * the injected text ignoring the image. (Before v2.4 no search existed, so
 * recognition worked.)
 *
 * Scope of the skip: ONLY the no-URL `else` branch. The pasted-URL fetch
 * branch still runs with attachments; loop arming inputs
 * ([ChatRequest.webOverride]) and the provider attach path
 * ([ChatRequest.images]/`audioBytes`) are untouched.
 *
 * Harness mirrors [ChatAlwaysSearchTest] (mocked fetcher /
 * ddgSearchRepository / providerRouter); the VM is built per test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatAttachmentSearchSkipTest {

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
    private lateinit var context: Context
    private lateinit var lastHelper: LlmModelHelper

    private fun buildViewModel(
        modelPath: String,
        online: Boolean = true,
        supportsFunctionCalling: Boolean = true,
        tavilyKey: CharArray? = null,
        // Per-chat web override row (null = Heredar). Non-null pins that
        // the arming input still travels on skipped turns.
        webOverride: Boolean? = null,
    ): ChatViewModel {
        chatRepository = mockk()
        val endpointRepository = mockk<EndpointRepository>()
        val localModelRepository = mockk<LocalModelRepository>()
        val activeModelSelection = mockk<ActiveModelSelection>()
        val providerRouter = mockk<ProviderRouter>()
        val engineManager = mockk<EngineManager>()
        val memoryChecker = mockk<MemoryChecker>()
        val advancedPreferences = mockk<AdvancedPreferences>()
        context = mockk()
        every { context.getString(any<Int>()) } returns ""
        every { context.getString(any<Int>(), *anyVararg<Any>()) } returns ""

        every { chatRepository.observeConversations() } returns MutableStateFlow(emptyList())
        coEvery { chatRepository.createConversation(any(), any(), any(), any()) } returns 42L
        coEvery { chatRepository.saveMessage(any(), any()) } just Runs
        coEvery { chatRepository.saveMessageWithSources(any(), any(), any()) } returns 99L
        coEvery { chatRepository.getWebOverride(any()) } returns webOverride
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
        val allowlist = mockk<ModelAllowlistRepository>()
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

    // ------------------------------------------------------------------
    // Skip matrix: attachment turns never call the heuristic search.
    // ------------------------------------------------------------------

    @Test
    fun `image-only turn skips heuristic search`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns groundedOutcome()

        // sendMessage early-returns only on blank text AND no images AND no
        // audio — image-only passes with empty text.
        vm.sendMessage("", images = listOf(mockk()))
        advanceUntilIdle()

        coVerify(exactly = 0) {
            ddgSearchRepository.search(any(), any(), any(), any())
        }
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
    fun `audio-only turn skips heuristic search`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns groundedOutcome()

        vm.sendMessage("", audioBytes = byteArrayOf(1, 2, 3))
        advanceUntilIdle()

        coVerify(exactly = 0) {
            ddgSearchRepository.search(any(), any(), any(), any())
        }
        assertThat(
            vm.transcriptState.value.messages.any {
                it.role == Role.ASSISTANT &&
                    it.content == "hola" &&
                    it.modelOnlyNotice == null
            },
        ).isTrue()
    }

    @Test
    fun `image plus question text skips search and sends original text`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns groundedOutcome()

        vm.sendMessage("what is in this image", images = listOf(mockk()))
        advanceUntilIdle()

        coVerify(exactly = 0) {
            ddgSearchRepository.search(any(), any(), any(), any())
        }
        // No augment on skipped turns: the outgoing request carries the
        // original text byte-identical (no SYSTEM_PROMPT, no fused block).
        val requestSlot = slot<ChatRequest>()
        coVerify(exactly = 1) { lastHelper.runInference(capture(requestSlot), any()) }
        assertThat(requestSlot.captured.messages.last().content).isEqualTo("what is in this image")
        assertThat(requestSlot.captured.messages.last().content).doesNotContain(GroundingPrompt.SYSTEM_PROMPT)
    }

    @Test
    fun `text-only no-URL turn still runs the pre-search`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns groundedOutcome()

        vm.sendMessage("latest news")
        advanceUntilIdle()

        // Regression guard: existing text-only behavior unchanged.
        coVerify(exactly = 1) {
            ddgSearchRepository.search("latest news", any(), any(), any())
        }
    }

    // ------------------------------------------------------------------
    // Combos: the pasted-URL fetch branch is unaffected by the skip.
    // ------------------------------------------------------------------

    @Test
    fun `text plus pasted URL plus image still fetches`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        val url = "https://example.com/a"
        coEvery {
            multiUrlFetcher.fetchAll(any(), any(), any())
        } returns fusedFetch(url)
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns groundedOutcome()

        vm.sendMessage("read this $url what do you see", images = listOf(mockk()))
        advanceUntilIdle()

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

    @Test
    fun `url-only turn still fetches`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        val url = "https://example.com/a"
        coEvery {
            multiUrlFetcher.fetchAll(any(), any(), any())
        } returns fusedFetch(url)

        vm.sendMessage("read this $url")
        advanceUntilIdle()

        coVerify(exactly = 1) { multiUrlFetcher.fetchAll(listOf(url), any(), any()) }
    }

    // ------------------------------------------------------------------
    // Loop interaction + attachment passthrough pins.
    // ------------------------------------------------------------------

    @Test
    fun `loop-armed image turn skips search but keeps arming inputs`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(
            modelFile.absolutePath,
            supportsFunctionCalling = true,
            webOverride = true,
        )
        runCurrent()
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns groundedOutcome()

        vm.sendMessage("what is in this image", images = listOf(mockk()))
        advanceUntilIdle()

        // Heuristic pre-search skipped even though the loop is armed (the
        // model may still tool-search with full multimodal context).
        coVerify(exactly = 0) {
            ddgSearchRepository.search(any(), any(), any(), any())
        }
        // Provider arming input untouched: the per-chat override still
        // travels on the request. Provider internals not asserted.
        val requestSlot = slot<ChatRequest>()
        coVerify(exactly = 1) { lastHelper.runInference(capture(requestSlot), any()) }
        assertThat(requestSlot.captured.webOverride).isTrue()
    }

    @Test
    fun `audio bytes still flow into the request on skipped turns`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns groundedOutcome()

        val audio = byteArrayOf(1, 2, 3, 4)
        vm.sendMessage("transcribe this", audioBytes = audio)
        advanceUntilIdle()

        coVerify(exactly = 0) {
            ddgSearchRepository.search(any(), any(), any(), any())
        }
        val requestSlot = slot<ChatRequest>()
        coVerify(exactly = 1) { lastHelper.runInference(capture(requestSlot), any()) }
        assertThat(requestSlot.captured.audioBytes).isNotNull()
        assertThat(requestSlot.captured.audioBytes!!.toList()).isEqualTo(audio.toList())
    }

    @Test
    fun `decoded image data urls still flow into the request on skipped turns`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        // Unit-test harness: android.util.Base64 is a stub on the JVM, so
        // static-mock the encode call and feed a real stream through a
        // mocked ContentResolver to exercise the real uriToBase64 path.
        val resolver = mockk<ContentResolver>()
        every { context.contentResolver } returns resolver
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(byteArrayOf(9, 9, 9)) }
        every { resolver.getType(any()) } returns "image/png"
        mockkStatic(Base64::class)
        try {
            every { Base64.encodeToString(any(), any()) } returns "CQkJ"
            coEvery {
                ddgSearchRepository.search(any(), any(), any(), any())
            } returns groundedOutcome()

            vm.sendMessage("what is in this image", images = listOf(mockk()))
            advanceUntilIdle()

            coVerify(exactly = 0) {
                ddgSearchRepository.search(any(), any(), any(), any())
            }
            val requestSlot = slot<ChatRequest>()
            coVerify(exactly = 1) { lastHelper.runInference(capture(requestSlot), any()) }
            assertThat(requestSlot.captured.images).hasSize(1)
            assertThat(requestSlot.captured.images.single()).startsWith("data:image/png;base64,")
        } finally {
            unmockkStatic(Base64::class)
        }
    }
}
