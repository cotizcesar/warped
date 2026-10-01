package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.GroundingPrompt
import com.warped.data.grounding.GroundingResult
import com.warped.data.grounding.MultiUrlResult
import com.warped.data.grounding.TavilySearchOutcome
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.local.security.ApiKeyStore
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
import com.warped.data.repository.AllowlistCapabilities
import com.warped.data.repository.AllowlistedModel
import com.warped.data.repository.ModelAllowlistRepository
import com.warped.domain.model.ParameterStore
import com.warped.domain.model.ProviderType
import com.warped.domain.model.RemoteSelection
import com.warped.domain.model.Role
import com.warped.domain.model.ModelOnlyNotice
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
 * Quick-task (always-search): the DDG-primary pre-search runs on EVERY
 * grounded no-URL turn, including turns where the agentic loop is armed
 * (function-calling-capable model + online). Previously the VM skipped
 * the branch on armed turns trusting the ~2B model to call `web_search` —
 * on-device evidence says it usually doesn't, so those turns returned a
 * "paste a link" reply instead of sources.
 *
 * Fallback matrix (locked, wallet bounds unchanged): the VM never calls
 * Tavily directly — Tavily fires only inside
 * [DuckDuckGoSearchRepository.search] when DDG yields nothing usable AND
 * a key is stored; `LocalToolLoop.MAX_TOOL_CALLS` untouched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatAlwaysSearchTest {

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
    private lateinit var ddgSearchRepository: DuckDuckGoSearchRepository
    private lateinit var lastHelper: LlmModelHelper

    private fun buildViewModel(
        modelPath: String,
        online: Boolean = true,
        supportsFunctionCalling: Boolean = true,
        // Quick-task (image-turn routing): stored Tavily key state for the
        // unkeyed image-intent notice gate. Null = unkeyed device.
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
        // Allowlist capability: true reproduces the previously-armed turn
        // (capable model + online). The VM no longer consults it for the
        // pre-search decision — the provider owns arming.
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
        // Fresh copy per call: the VM zero-fills the returned array after
        // the presence check, so a shared instance would read as missing
        // on the second turn.
        val apiKeyStore = mockk<ApiKeyStore>()
        every { apiKeyStore.getTavilyKey() } answers { tavilyKey?.copyOf() }

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
            multiUrlFetcher = mockk(),
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

    @Test
    fun `previously-armed turn still runs the ddg pre-search`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath, supportsFunctionCalling = true)
        runCurrent()
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns groundedOutcome()

        vm.sendMessage("latest news")
        advanceUntilIdle()

        // The regression: this combination used to skip the branch.
        coVerify(exactly = 1) {
            ddgSearchRepository.search("latest news", any(), any(), any())
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
    fun `unarmed turn runs the pre-search unchanged`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath, supportsFunctionCalling = false)
        runCurrent()
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns groundedOutcome()

        vm.sendMessage("latest news")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            ddgSearchRepository.search("latest news", any(), any(), any())
        }
        assertThat(
            vm.transcriptState.value.messages.any {
                it.role == Role.ASSISTANT && it.content == "hola"
            },
        ).isTrue()
    }

    @Test
    fun `offline turn renders offline notice and never opens a socket`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath, online = false)
        runCurrent()

        vm.sendMessage("latest news")
        advanceUntilIdle()

        coVerify(exactly = 0) {
            ddgSearchRepository.search(any(), any(), any(), any())
        }
        assertThat(
            vm.transcriptState.value.messages.any {
                it.role == Role.ASSISTANT && it.modelOnlyNotice == ModelOnlyNotice.OFFLINE
            },
        ).isTrue()
    }

    @Test
    fun `non-image question passes include-images false`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns groundedOutcome()

        vm.sendMessage("latest news")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            ddgSearchRepository.search(any(), any(), any(), includeImages = false)
        }
        assertThat(
            vm.transcriptState.value.messages.any {
                it.role == Role.ASSISTANT && it.groundedImages.isEmpty()
            },
        ).isTrue()
    }

    @Test
    fun `image-intent question passes include-images true and carries fused images`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        val outcome = groundedOutcome()
        val withImages = TavilySearchOutcome.Grounded(outcome.fused.copy(images = listOf("https://img.example/a.png")))
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns withImages

        vm.sendMessage("muéstrame fotos de gatos")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            ddgSearchRepository.search(any(), any(), any(), includeImages = true)
        }
        assertThat(
            vm.transcriptState.value.messages.any {
                it.role == Role.ASSISTANT &&
                    it.groundedImages == listOf("https://img.example/a.png")
            },
        ).isTrue()
    }

    @Test
    fun `ddg fetch-failed renders fetch-failed notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns TavilySearchOutcome.ModelOnly(
            MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
        )

        vm.sendMessage("latest news")
        advanceUntilIdle()

        // DDG-fail + no key is FETCH_FAILED (no key nag); the Tavily leg
        // fires only inside the repository when a key is stored.
        assertThat(
            vm.transcriptState.value.messages.any {
                it.role == Role.ASSISTANT &&
                    it.modelOnlyNotice == ModelOnlyNotice.FETCH_FAILED
            },
        ).isTrue()
        val requestSlot = slot<ChatRequest>()
        coVerify(exactly = 1) { lastHelper.runInference(capture(requestSlot), any()) }
        assertThat(requestSlot.captured.messages.last().content).startsWith(
            GroundingPrompt.SYSTEM_PROMPT,
        )
    }

    // ------------------------------------------------------------------
    // Quick-task (image-turn routing): intent x key matrix at the VM seam.
    // The Tavily-direct skip lives inside DuckDuckGoSearchRepository (see
    // DuckDuckGoSearchRepositoryTest); here the repo is mocked, so these
    // tests pin the VM contract: image list threading + the unkeyed
    // images-need-key notice gate.
    // ------------------------------------------------------------------

    @Test
    fun `image-intent keyed turn populates grid with no notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(
            modelFile.absolutePath,
            tavilyKey = "tvly-test-key".toCharArray(),
        )
        runCurrent()
        val outcome = groundedOutcome()
        val withImages = TavilySearchOutcome.Grounded(outcome.fused.copy(images = listOf("https://img.example/a.png")))
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns withImages

        vm.sendMessage("muéstrame fotos de gatos")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            ddgSearchRepository.search(any(), any(), any(), includeImages = true)
        }
        assertThat(
            vm.transcriptState.value.messages.any {
                it.role == Role.ASSISTANT &&
                    it.groundedImages == listOf("https://img.example/a.png") &&
                    it.modelOnlyNotice == null
            },
        ).isTrue()
    }

    @Test
    fun `image-intent unkeyed turn grounds text with images-need-key notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns groundedOutcome()

        vm.sendMessage("muéstrame fotos de gatos")
        advanceUntilIdle()

        // DDG text grounding preserved (sources on the message), grid
        // empty (no image API on the DDG leg), actionable notice naming
        // the Settings fix alongside.
        assertThat(
            vm.transcriptState.value.messages.any {
                it.role == Role.ASSISTANT &&
                    it.groundedSources == listOf("https://a.example/uno") &&
                    it.groundedImages.isEmpty() &&
                    it.modelOnlyNotice == ModelOnlyNotice.IMAGES_NEED_KEY
            },
        ).isTrue()
    }

    @Test
    fun `image-intent unkeyed fetch-failed turn renders images-need-key notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns TavilySearchOutcome.ModelOnly(
            MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
        )

        vm.sendMessage("muéstrame fotos de gatos")
        advanceUntilIdle()

        // Single banner slot: the images-need-key notice takes precedence
        // — the user asked for images and storing a key is the actionable
        // fix, while a DDG failure is not user-fixable. No crash, the
        // model still answers.
        assertThat(
            vm.transcriptState.value.messages.any {
                it.role == Role.ASSISTANT &&
                    it.groundedImages.isEmpty() &&
                    it.modelOnlyNotice == ModelOnlyNotice.IMAGES_NEED_KEY &&
                    it.content == "hola"
            },
        ).isTrue()
    }

    @Test
    fun `non-image keyed turn stays ddg-primary with no notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(
            modelFile.absolutePath,
            tavilyKey = "tvly-test-key".toCharArray(),
        )
        runCurrent()
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns groundedOutcome()

        vm.sendMessage("latest news")
        advanceUntilIdle()

        // Key present but no image intent: include_images stays false, no
        // grid, no notice — byte-identical to the unkeyed non-image path.
        coVerify(exactly = 1) {
            ddgSearchRepository.search(any(), any(), any(), includeImages = false)
        }
        assertThat(
            vm.transcriptState.value.messages.any {
                it.role == Role.ASSISTANT &&
                    it.groundedSources == listOf("https://a.example/uno") &&
                    it.groundedImages.isEmpty() &&
                    it.modelOnlyNotice == null
            },
        ).isTrue()
    }
}
