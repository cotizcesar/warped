package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.MultiUrlResult
import com.warped.data.grounding.GroundingResult
import com.warped.data.grounding.SearchOutcome
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.local.inference.ActiveEngine
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.EngineType
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.ProviderRouter
import com.warped.domain.llm.LlmModelHelper
import com.warped.domain.model.ActiveModelSelection
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.GroundedSourceStatus
import com.warped.domain.model.LocalSelection
import com.warped.data.repository.AllowlistCapabilities
import com.warped.data.repository.AllowlistedModel
import com.warped.data.repository.ModelAllowlistRepository
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
 * Quick-task (always-presearch-anchored): the always-on VM pre-search anchors
 * anaphoric follow-ups with the prior turn's topic words.
 *
 * Mirrors the ChatAlwaysSearchTest harness (mocked ddgSearchRepository). The
 * mock stands in for the always-on DDG leg regardless of loop arming — loop
 * arming lives provider-side and the VM never consults it.
 *
 * Wallet bounds (locked): exactly 1 `ddgSearchRepository.search` invocation
 * per eligible turn; the VM holds the DDG-only repository collaborator and
 * no key-store field. Query-text only — never call count, never gate
 * conditions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatAnaphoraAnchorTest {

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
            capabilities = AllowlistCapabilities(supportsFunctionCalling = true),
        )
        ddgSearchRepository = mockk()
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
            modelAllowlistRepository = allowlist,
            context = context,
            reviewHelper = mockk(relaxed = true),
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

    private fun groundedOutcome() = SearchOutcome.Grounded(
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
    fun `armed follow-up pre-searches the anchored query`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns groundedOutcome()

        vm.sendMessage("Háblame de la familia real")
        advanceUntilIdle()
        vm.sendMessage("Quien es su hermanastro?")
        advanceUntilIdle()

        // The bug: the follow-up used to search the RAW message (zero topical
        // signal). Now it carries the prior turn's topic words.
        coVerify(exactly = 1) {
            ddgSearchRepository.search("Háblame de la familia real", any(), any(), any())
        }
        coVerify(exactly = 1) {
            ddgSearchRepository.search(
                "Quien es su hermanastro? Háblame de la familia real",
                any(), any(), any(),
            )
        }
        assertThat(
            vm.transcriptState.value.messages.count { it.role == Role.ASSISTANT },
        ).isEqualTo(2)
    }

    @Test
    fun `unarmed single turn searches the raw query`() = runTest {
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
            ddgSearchRepository.search("latest news", any(), any(), any())
        }
    }

    @Test
    fun `anaphoric first turn with no history searches raw`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns groundedOutcome()

        vm.sendMessage("Quien es su hermanastro?")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            ddgSearchRepository.search("Quien es su hermanastro?", any(), any(), any())
        }
    }

    @Test
    fun `non-anaphoric follow-up with history searches raw`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns groundedOutcome()

        vm.sendMessage("Háblame de Marte")
        advanceUntilIdle()
        vm.sendMessage("Ultimas noticias de hoy")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            ddgSearchRepository.search("Ultimas noticias de hoy", any(), any(), any())
        }
    }

    @Test
    fun `social turn never searches`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()

        vm.sendMessage("hola, quien Eres")
        advanceUntilIdle()

        coVerify(exactly = 0) {
            ddgSearchRepository.search(any(), any(), any(), any())
        }
    }

    @Test
    fun `code turn never searches`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()

        vm.sendMessage("Dame un ejemplo de codigo simple en jsavascript")
        advanceUntilIdle()

        coVerify(exactly = 0) {
            ddgSearchRepository.search(any(), any(), any(), any())
        }
    }

    @Test
    fun `url turn never searches`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()

        vm.sendMessage("hola https://example.com/algo")
        advanceUntilIdle()

        coVerify(exactly = 0) {
            ddgSearchRepository.search(any(), any(), any(), any())
        }
    }

    @Test
    fun `offline turn never searches`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath, online = false)
        runCurrent()

        vm.sendMessage("Quien es su hermanastro?")
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
    fun `exactly one search invocation per eligible turn`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns groundedOutcome()

        vm.sendMessage("Háblame de la familia real")
        advanceUntilIdle()
        vm.sendMessage("Quien es su hermanastro?")
        advanceUntilIdle()

        // Two eligible turns → exactly two invocations total (anchoring adds
        // query text, never extra calls).
        coVerify(exactly = 2) {
            ddgSearchRepository.search(any(), any(), any(), any())
        }
    }

    @Test
    fun `vm holds the ddg-only search collaborator and no key store`() {
        // DDG-only posture: the VM calls ddgSearchRepository.search
        // keylessly — no key-store field exists on the ViewModel.
        val ddgFields = ChatViewModel::class.java.declaredFields.filter {
            it.type == DuckDuckGoSearchRepository::class.java
        }
        assertThat(ddgFields).hasSize(1)
        val keyStoreFields = ChatViewModel::class.java.declaredFields.filter {
            it.type == com.warped.data.local.security.ApiKeyStore::class.java
        }
        assertThat(keyStoreFields).isEmpty()
    }
}
