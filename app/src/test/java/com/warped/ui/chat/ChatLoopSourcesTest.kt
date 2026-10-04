package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.GroundingResult
import com.warped.data.grounding.MultiUrlFetcher
import com.warped.data.grounding.MultiUrlResult
import com.warped.data.grounding.SearchOutcome
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.local.inference.ActiveEngine
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.EngineType
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.ProviderRouter
import com.warped.data.repository.AllowlistCapabilities
import com.warped.data.repository.AllowlistedModel
import com.warped.data.repository.ModelAllowlistRepository
import com.warped.domain.llm.LlmModelHelper
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.GroundedSourceStatus
import com.warped.domain.model.LocalSelection
import com.warped.domain.model.ParameterStore
import com.warped.domain.model.ProviderType
import com.warped.domain.model.RemoteSelection
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
 * Quick-task (agentic-rows): loop-turn Fuentes persistence at the
 * ViewModel collector.
 *
 * The turn runs on a capable local model + grounding on + validated
 * internet. The DDG-primary VM pre-search runs on every such turn
 * (always-on) and the loop's `ToolCompleted.sources` union with it on Done
 * (first-seen order, distinct by URL). These tests stub the pre-search
 * model-only so the ONLY rows come from the helper — isolating the loop
 * half of the union. On Done the VM persists via the IDENTICAL
 * `saveMessageWithSources` path as grounded turns — same row shape (incl.
 * OG columns), same `replaceSources`-safety (the retry path is never
 * touched), no history rewrite (no Role.TOOL rows).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatLoopSourcesTest {

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
    private lateinit var ddgSearchRepository: DuckDuckGoSearchRepository
    private lateinit var lastHelper: LlmModelHelper
    private lateinit var helperTokens: List<StreamToken>

    private fun buildViewModel(modelPath: String): ChatViewModel {
        chatRepository = mockk()
        val endpointRepository = mockk<EndpointRepository>()
        val localModelRepository = mockk<LocalModelRepository>()
        val activeModelSelection = mockk<com.warped.domain.model.ActiveModelSelection>()
        val providerRouter = mockk<ProviderRouter>()
        val engineManager = mockk<EngineManager>()
        val memoryChecker = mockk<MemoryChecker>()
        val advancedPreferences = mockk<AdvancedPreferences>()
        val allowlist = mockk<ModelAllowlistRepository>().also(::stubEffectiveCapabilities)
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
        // Loop-armed: capable model per the verified-only allowlist read.
        every { allowlist.findByModelFile("tiny.litertlm") } returns AllowlistedModel(
            name = "tiny",
            displayName = "Tiny",
            modelFile = "tiny.litertlm",
            sizeInBytes = 1L,
            capabilities = AllowlistCapabilities(supportsFunctionCalling = true),
        )
        val helper = mockk<LlmModelHelper>()
        every { helper.type } returns ProviderType.LITE_RT_LM
        coEvery { helper.initialize(any()) } just Runs
        coEvery { helper.stopResponse() } just Runs
        every { helper.runInference(any(), any()) } returns flow {
            for (token in helperTokens) emit(token)
        }
        every { providerRouter.resolveLocalHelper(any(), any()) } returns helper
        lastHelper = helper
        val fetcher = mockk<WebPageFetcher>()
        every { fetcher.cancel() } just Runs
        every { fetcher.hasValidatedInternet() } returns true
        ddgSearchRepository = mockk()
        // Always-on pre-search stubbed model-only: isolates the loop half
        // of the Done union (no fused rows to merge).
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns SearchOutcome.ModelOnly(
            MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
        )

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

    private val loopRows = listOf(
        GroundedSource(
            url = "https://s1.example/a",
            extractedText = "Search text.",
            status = GroundedSourceStatus.OK,
            ogTitle = "S1",
            ogDescription = "Desc S1",
            ogImageUrl = "https://s1.example/img.png",
        ),
        GroundedSource(
            url = "https://dead.example/x",
            extractedText = null,
            status = GroundedSourceStatus.OMITIDA,
        ),
    )

    @Test
    fun `loop turn persists ToolCompleted rows via saveMessageWithSources`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        helperTokens = listOf(
            StreamToken.ToolStatus("Searching for \"q\"…"),
            StreamToken.ToolCompleted("local:web_search#1", "summary", sources = loopRows),
            StreamToken.ToolStatus(null),
            StreamToken.Delta("answer with [1]"),
            StreamToken.Done(),
        )
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()

        vm.sendMessage("latest news")
        advanceUntilIdle()

        // No VM pre-search (user decision 2026-10-03) — rows come from
        // the loop only.
        coVerify(exactly = 0) { ddgSearchRepository.search(any(), any(), any(), any()) }
        val messageSlot = slot<ChatMessage>()
        val sourcesSlot = slot<List<GroundedSource>>()
        coVerify(exactly = 1) {
            chatRepository.saveMessageWithSources(42L, capture(messageSlot), capture(sourcesSlot))
        }
        // Same row shape incl. OG columns, same order.
        assertThat(sourcesSlot.captured).isEqualTo(loopRows)
        // Message mirrors the okUrls-only convention of grounded turns.
        assertThat(messageSlot.captured.groundedSources).containsExactly("https://s1.example/a")
        assertThat(messageSlot.captured.groundedSourceDetails).isEqualTo(loopRows)
        assertThat(messageSlot.captured.content).contains("answer with [1]")
        // Retry path untouched: no re-save, no history rewrite.
        coVerify(exactly = 0) { chatRepository.replaceSources(any(), any(), any()) }
        // The turn's only plain saveMessage is the USER row at send time —
        // the assistant row went through saveMessageWithSources above.
        coVerify(exactly = 1) { chatRepository.saveMessage(any(), any()) }
    }

    @Test
    fun `multi-call union merges without dupes first-seen order`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val fetchRows = listOf(
            // Duplicate of the search row: union keeps the first occurrence.
            GroundedSource(
                url = "https://s1.example/a",
                extractedText = "Fetch text (newer).",
                status = GroundedSourceStatus.OK,
            ),
            GroundedSource(
                url = "https://f.example/p",
                extractedText = "Fetch text.",
                status = GroundedSourceStatus.OK,
            ),
        )
        helperTokens = listOf(
            StreamToken.ToolCompleted("local:web_search#1", "s", sources = loopRows),
            StreamToken.ToolCompleted("local:web_fetch#2", "f", sources = fetchRows),
            StreamToken.Delta("answer"),
            StreamToken.Done(),
        )
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()

        vm.sendMessage("latest news")
        advanceUntilIdle()

        val sourcesSlot = slot<List<GroundedSource>>()
        coVerify(exactly = 1) {
            chatRepository.saveMessageWithSources(any(), any(), capture(sourcesSlot))
        }
        // s1 (search text wins, first-seen) + dead (omitida) + f.
        assertThat(sourcesSlot.captured.map { it.url }).containsExactly(
            "https://s1.example/a",
            "https://dead.example/x",
            "https://f.example/p",
        ).inOrder()
        assertThat(sourcesSlot.captured[0].extractedText).isEqualTo("Search text.")
    }

    @Test
    fun `zero-tool armed turn saves plain with no source calls`() = runTest {        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        helperTokens = listOf(
            StreamToken.Delta("plain answer"),
            StreamToken.Done(),
        )
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()

        vm.sendMessage("hello")
        advanceUntilIdle()

        // Two plain saves: the USER row at send time + the sourceless
        // assistant row — never the sources path, never the retry path.
        val saved = mutableListOf<ChatMessage>()
        coVerify(exactly = 2) { chatRepository.saveMessage(42L, capture(saved)) }
        assertThat(saved[0].role).isEqualTo(com.warped.domain.model.Role.USER)
        assertThat(saved[1].role).isEqualTo(com.warped.domain.model.Role.ASSISTANT)
        assertThat(saved[1].groundedSources).isEmpty()
        assertThat(saved[1].groundedSourceDetails).isEmpty()
        coVerify(exactly = 0) { chatRepository.saveMessageWithSources(any(), any(), any()) }
        coVerify(exactly = 0) { chatRepository.replaceSources(any(), any(), any()) }
    }

    @Test
    fun `history keeps originals - outgoing request carries raw text (no pre-search)`() = runTest {
        // HISTORY-SEMANTICS DECISION (keep-as-is): Room persists the
        // ORIGINAL user text. Since user decision 2026-10-03 there is no
        // VM pre-search, so the outgoing request's current message is
        // the raw user text too — augmentation only ever comes from
        // pasted-URL fetches (not this test) or the provider-side loop.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        helperTokens = listOf(
            StreamToken.ToolCompleted("local:web_search#1", "s", sources = loopRows),
            StreamToken.Delta("answer"),
            StreamToken.Done(),
        )
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()

        vm.sendMessage("latest news")
        advanceUntilIdle()

        // Outgoing request: raw user text, no SYSTEM_PROMPT prefix, no
        // fused block — the loop works provider-side on the same text.
        val requestSlot = slot<com.warped.domain.model.ChatRequest>()
        coVerify(exactly = 1) { lastHelper.runInference(capture(requestSlot), any()) }
        val sent = requestSlot.captured.messages
        assertThat(sent).hasSize(1)
        assertThat(sent.single().content).isEqualTo("latest news")
        assertThat(sent.single().content).doesNotContain("--- Source [")

        // Persisted user row keeps the original — no augmentation leaks
        // into Room history.
        val savedUser = mutableListOf<ChatMessage>()
        coVerify(atLeast = 1) { chatRepository.saveMessage(42L, capture(savedUser)) }
        assertThat(savedUser.first { it.role == com.warped.domain.model.Role.USER }.content)
            .isEqualTo("latest news")
    }

    @Test
    fun `loop turn unions ToolCompleted images into groundedImages`() = runTest {
        // Quick-task (loop-images): a loop turn whose ToolCompleted rows
        // carry fused images produces an assistant message with
        // `groundedImages` non-empty — the exact input the grid condition
        // (`MessageBubble`: `!isUser && groundedImages.isNotEmpty()` →
        // `GroundedImageGrid`) reads. The pre-search half is stubbed
        // model-only above, so the images come from the loop only.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        helperTokens = listOf(
            StreamToken.ToolCompleted(
                "local:web_search#1",
                "s",
                sources = loopRows,
                images = listOf(
                    "https://s1.example/grid1.png",
                    "https://s1.example/grid2.png",
                ),
            ),
            // Duplicate across calls: union keeps the first occurrence.
            StreamToken.ToolCompleted(
                "local:web_search#2",
                "s",
                sources = loopRows,
                images = listOf("https://s1.example/grid2.png"),
            ),
            StreamToken.Delta("answer with pictures"),
            StreamToken.Done(),
        )
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()

        vm.sendMessage("show me pictures of cats")
        advanceUntilIdle()

        val messageSlot = slot<ChatMessage>()
        coVerify(exactly = 1) {
            chatRepository.saveMessageWithSources(42L, capture(messageSlot), any())
        }
        // First-seen order, distinct — pre-search half empty here.
        assertThat(messageSlot.captured.groundedImages).containsExactly(
            "https://s1.example/grid1.png",
            "https://s1.example/grid2.png",
        ).inOrder()
    }
}
