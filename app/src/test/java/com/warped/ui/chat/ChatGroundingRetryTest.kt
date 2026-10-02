package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.data.grounding.GroundingResult
import com.warped.data.grounding.MultiUrlResult
import com.warped.data.local.db.dao.ConversationDao
import com.warped.data.local.db.dao.GroundedSourceDao
import com.warped.data.local.db.dao.MessageDao
import com.warped.data.local.db.entity.GroundedSourceEntity
import com.warped.data.local.inference.ActiveEngine
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.EngineType
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.ProviderRouter
import com.warped.data.repository.ChatRepositoryImpl
import com.warped.domain.llm.LlmModelHelper
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
import com.warped.domain.review.ReviewHelper
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
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
import java.time.Instant

/**
 * Phase 54 (RETRY-01): exit-gate tests for offline retry.
 *
 * Mirrors [ChatGroundingToggleTest]'s fetchAll-mocking-after-buildViewModel
 * pattern: the multi-URL entry point is stubbed per test, the inference
 * helper answers "hola", and the repository is a mockk (row-reuse
 * delete-then-insert is proven at the [ChatRepositoryImpl] level with mockk
 * DAOs — room-testing lives in androidTestImplementation, so in-memory Room
 * cannot run in JVM unit tests).
 *
 * Gates: (1) offline-fake retry attaches sources + clears the notice;
 * (2) history (content + timestamps) byte-identical across retry;
 * (3) row-reuse — repeated writes land in the same message_id rows;
 * (4) no-inference proof — runInference never fires on retry.
 * Plus the locked guards: Stop-during-retry restores the queued state,
 * FETCH_FAILED turns ignore retryGrounding, overlapping taps are no-ops.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatGroundingRetryTest {

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
    private lateinit var multiUrlFetcher: com.warped.data.grounding.MultiUrlFetcher
    private lateinit var fetcher: com.warped.data.grounding.WebPageFetcher
    private lateinit var inferenceHelper: LlmModelHelper

    private fun buildViewModel(modelPath: String): ChatViewModel {
        chatRepository = mockk()
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
        inferenceHelper = answeringHelper()
        every { providerRouter.resolveLocalHelper(any(), any()) } returns inferenceHelper
        fetcher = mockk()
        every { fetcher.cancel() } just Runs
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
            ddgSearchRepository = mockk(),
            modelAllowlistRepository = mockk<com.warped.data.repository.ModelAllowlistRepository>().also(::stubEffectiveCapabilities),
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
        return helper
    }

    private fun fusedResult() = MultiUrlResult.Fused(
        block = "BLOQUE",
        okUrls = listOf("https://a.example/uno"),
        skippedUrls = listOf("https://dead.example/x"),
        pageTexts = mapOf("https://a.example/uno" to "Texto a."),
        details = listOf(
            GroundedSource(
                url = "https://a.example/uno",
                extractedText = "Texto a.",
                status = GroundedSourceStatus.OK,
            ),
            GroundedSource(
                url = "https://dead.example/x",
                extractedText = null,
                status = GroundedSourceStatus.OMITIDA,
            ),
        ),
    )

    private fun assistantOf(vm: ChatViewModel) =
        vm.transcriptState.value.messages.last { it.role == Role.ASSISTANT }

    // ------------------------------------------------------------------
    // Gate 1: offline-fake retry attaches sources + clears the notice.
    // ------------------------------------------------------------------

    @Test
    fun `offline turn retries to fused sources with notice cleared`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery { multiUrlFetcher.fetchAll(any(), any(), any()) } returns
            MultiUrlResult.AllFailed(GroundingResult.Reason.OFFLINE)

        vm.sendMessage("mira https://a.example/uno")
        advanceUntilIdle()

        val queued = assistantOf(vm)
        assertThat(queued.modelOnlyNotice).isEqualTo(ModelOnlyNotice.OFFLINE)
        assertThat(queued.groundedSourceDetails).isEmpty()

        coEvery { multiUrlFetcher.fetchAll(any(), any(), any()) } returns fusedResult()
        every { fetcher.hasValidatedInternet() } returns true

        vm.refreshConnectivity()
        assertThat(vm.inputState.value.isValidatedOnline).isTrue()

        vm.retryGrounding(queued.id)
        advanceUntilIdle()

        val retried = assistantOf(vm)
        assertThat(retried.modelOnlyNotice).isNull()
        assertThat(retried.groundedSources).containsExactly("https://a.example/uno")
        assertThat(retried.groundedSourceDetails.map { it.url }).containsExactly(
            "https://a.example/uno",
            "https://dead.example/x",
        ).inOrder()
        val sourcesSlot = slot<List<GroundedSource>>()
        coVerify(exactly = 1) { chatRepository.replaceSources(42L, queued.createdAt, capture(sourcesSlot)) }
        assertThat(sourcesSlot.captured).isEqualTo(retried.groundedSourceDetails)
    }

    // ------------------------------------------------------------------
    // Gate 2: history-untouched — content + timestamps byte-identical.
    // ------------------------------------------------------------------

    @Test
    fun `retry leaves user and assistant history untouched`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery { multiUrlFetcher.fetchAll(any(), any(), any()) } returns
            MultiUrlResult.AllFailed(GroundingResult.Reason.OFFLINE)

        vm.sendMessage("mira https://a.example/uno")
        advanceUntilIdle()

        val before = vm.transcriptState.value.messages.map {
            Triple(it.role, it.content, it.createdAt)
        }
        val queued = assistantOf(vm)

        coEvery { multiUrlFetcher.fetchAll(any(), any(), any()) } returns fusedResult()
        every { fetcher.hasValidatedInternet() } returns true
        vm.retryGrounding(queued.id)
        advanceUntilIdle()

        val after = vm.transcriptState.value.messages.map {
            Triple(it.role, it.content, it.createdAt)
        }
        assertThat(after).isEqualTo(before)
        assertThat(assistantOf(vm).content).isEqualTo("hola")
    }

    // ------------------------------------------------------------------
    // Gate 3: row-reuse — repeated writes, same message_id rows.
    // ------------------------------------------------------------------

    @Test
    fun `replaceSources reuses the same message rows on repeated retry`() = runTest {
        val messageDao = mockk<MessageDao>()
        val groundedSourceDao = mockk<GroundedSourceDao>()
        val conversationDao = mockk<ConversationDao>()
        coEvery { messageDao.findAssistantRowId(42L, 123L) } returns 7L
        coEvery { groundedSourceDao.deleteByMessage(7L) } just Runs
        coEvery { groundedSourceDao.insertAll(any()) } just Runs
        coEvery { conversationDao.updateTimestamp(42L, any()) } just Runs
        val repo = ChatRepositoryImpl(conversationDao, messageDao, groundedSourceDao)
        val details = fusedResult().details
        val createdAt = Instant.ofEpochMilli(123L)

        repo.replaceSources(42L, createdAt, details)
        repo.replaceSources(42L, createdAt, details)

        coVerify(exactly = 2) { messageDao.findAssistantRowId(42L, 123L) }
        coVerify(exactly = 2) { groundedSourceDao.deleteByMessage(7L) }
        val rowsSlot = mutableListOf<List<GroundedSourceEntity>>()
        coVerify(exactly = 2) { groundedSourceDao.insertAll(capture(rowsSlot)) }
        for (rows in rowsSlot) {
            assertThat(rows).hasSize(details.size)
            assertThat(rows.map { it.messageId }.toSet()).containsExactly(7L)
            assertThat(rows.map { it.sourceIndex }).containsExactly(0, 1).inOrder()
        }
        coEvery { messageDao.findAssistantRowId(43L, 999L) } returns null
        repo.replaceSources(43L, Instant.ofEpochMilli(999L), details)
        coVerify(exactly = 2) { groundedSourceDao.insertAll(any()) }
        coVerify(exactly = 0) { conversationDao.updateTimestamp(43L, any()) }
    }

    // ------------------------------------------------------------------
    // Gate 4: no-inference proof — retry never runs the model.
    // ------------------------------------------------------------------

    @Test
    fun `retryGrounding never invokes inference`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery { multiUrlFetcher.fetchAll(any(), any(), any()) } returns
            MultiUrlResult.AllFailed(GroundingResult.Reason.OFFLINE)

        vm.sendMessage("mira https://a.example/uno")
        advanceUntilIdle()
        val queued = assistantOf(vm)

        coEvery { multiUrlFetcher.fetchAll(any(), any(), any()) } returns fusedResult()
        every { fetcher.hasValidatedInternet() } returns true
        vm.retryGrounding(queued.id)
        advanceUntilIdle()

        coVerify(exactly = 1) { inferenceHelper.runInference(any(), any()) }
    }

    // ------------------------------------------------------------------
    // Locked guards: Stop-during-retry, FETCH_FAILED scope, overlap no-op.
    // ------------------------------------------------------------------

    @Test
    fun `stop during retry restores the queued state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery { multiUrlFetcher.fetchAll(any(), any(), any()) } returns
            MultiUrlResult.AllFailed(GroundingResult.Reason.OFFLINE)

        vm.sendMessage("mira https://a.example/uno")
        advanceUntilIdle()
        val queued = assistantOf(vm)

        val gate = CompletableDeferred<MultiUrlResult>()
        coEvery { multiUrlFetcher.fetchAll(any(), any(), any()) } coAnswers { gate.await() }
        every { fetcher.hasValidatedInternet() } returns true

        vm.retryGrounding(queued.id)
        runCurrent()
        assertThat(vm.inputState.value.isFetchingWeb).isTrue()

        vm.stopGeneration()
        gate.complete(fusedResult())
        advanceUntilIdle()

        assertThat(assistantOf(vm).modelOnlyNotice).isEqualTo(ModelOnlyNotice.OFFLINE)
        assertThat(assistantOf(vm).groundedSourceDetails).isEmpty()
        assertThat(vm.inputState.value.isFetchingWeb).isFalse()
        assertThat(vm.inputState.value.webFetchProgress).isNull()
    }

    @Test
    fun `fetch-failed turns ignore retryGrounding`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery { multiUrlFetcher.fetchAll(any(), any(), any()) } returns
            MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED)

        vm.sendMessage("mira https://a.example/uno")
        advanceUntilIdle()
        val failed = assistantOf(vm)
        assertThat(failed.modelOnlyNotice).isEqualTo(ModelOnlyNotice.FETCH_FAILED)

        every { fetcher.hasValidatedInternet() } returns true
        vm.retryGrounding(failed.id)
        advanceUntilIdle()

        coVerify(exactly = 1) { multiUrlFetcher.fetchAll(any(), any(), any()) }
        assertThat(assistantOf(vm).modelOnlyNotice).isEqualTo(ModelOnlyNotice.FETCH_FAILED)
    }

    @Test
    fun `overlapping retry taps are no-ops`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery { multiUrlFetcher.fetchAll(any(), any(), any()) } returns
            MultiUrlResult.AllFailed(GroundingResult.Reason.OFFLINE)

        vm.sendMessage("mira https://a.example/uno")
        advanceUntilIdle()
        val queued = assistantOf(vm)

        val gate = CompletableDeferred<MultiUrlResult>()
        coEvery { multiUrlFetcher.fetchAll(any(), any(), any()) } coAnswers { gate.await() }
        every { fetcher.hasValidatedInternet() } returns true

        vm.retryGrounding(queued.id)
        runCurrent()
        vm.retryGrounding(queued.id)
        vm.stopGeneration()
        gate.complete(fusedResult())
        advanceUntilIdle()

        // 1 × send-time fetch + 1 × first retry tap; the second tap no-ops.
        coVerify(exactly = 2) { multiUrlFetcher.fetchAll(any(), any(), any()) }
    }

    // ------------------------------------------------------------------
    // WR-01/WR-02 regression: back-to-back taps (no dispatch between
    // them) start exactly one retry; Stop is reachable mid-retry via
    // isGenerating and state resets on completion.
    // ------------------------------------------------------------------

    @Test
    fun `back-to-back retry taps start a single fetch with Stop surfaced`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery { multiUrlFetcher.fetchAll(any(), any(), any()) } returns
            MultiUrlResult.AllFailed(GroundingResult.Reason.OFFLINE)

        vm.sendMessage("mira https://a.example/uno")
        advanceUntilIdle()
        val queued = assistantOf(vm)

        val gate = CompletableDeferred<MultiUrlResult>()
        coEvery { multiUrlFetcher.fetchAll(any(), any(), any()) } coAnswers { gate.await() }
        every { fetcher.hasValidatedInternet() } returns true

        // No dispatch between taps: the second tap must observe the active
        // retryJob synchronously (WR-02) — isFetchingWeb is still false here.
        vm.retryGrounding(queued.id)
        vm.retryGrounding(queued.id)
        runCurrent()
        assertThat(vm.inputState.value.isFetchingWeb).isTrue()
        assertThat(vm.inputState.value.isGenerating).isTrue()

        gate.complete(fusedResult())
        advanceUntilIdle()

        // 1 × send-time fetch + 1 × single retry fetch.
        coVerify(exactly = 2) { multiUrlFetcher.fetchAll(any(), any(), any()) }
        assertThat(assistantOf(vm).modelOnlyNotice).isNull()
        assertThat(assistantOf(vm).groundedSources).containsExactly("https://a.example/uno")
        assertThat(vm.inputState.value.isFetchingWeb).isFalse()
        assertThat(vm.inputState.value.isGenerating).isFalse()
        assertThat(vm.inputState.value.webFetchProgress).isNull()
    }

    // ------------------------------------------------------------------
    // WR-03 regression: retry during active inference streaming is refused.
    // ------------------------------------------------------------------

    @Test
    fun `retryGrounding during active streaming is refused`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery { multiUrlFetcher.fetchAll(any(), any(), any()) } returns
            MultiUrlResult.AllFailed(GroundingResult.Reason.OFFLINE)

        vm.sendMessage("mira https://a.example/uno")
        advanceUntilIdle()
        val queued = assistantOf(vm)
        assertThat(queued.modelOnlyNotice).isEqualTo(ModelOnlyNotice.OFFLINE)

        // Second send stalls mid-streaming behind a gate.
        coEvery { multiUrlFetcher.fetchAll(any(), any(), any()) } returns fusedResult()
        val gate = CompletableDeferred<Unit>()
        every { inferenceHelper.runInference(any(), any()) } returns flow {
            gate.await()
            emit(StreamToken.Delta("hola"))
            emit(StreamToken.Done())
        }
        vm.sendMessage("otra https://b.example/x")
        runCurrent()
        assertThat(vm.inputState.value.isGenerating).isTrue()
        assertThat(vm.transcriptState.value.isStreaming).isTrue()

        every { fetcher.hasValidatedInternet() } returns true
        vm.retryGrounding(queued.id)
        runCurrent()

        // 1 × first-send fetch + 1 × second-send fan-out; no retry fetch.
        coVerify(exactly = 2) { multiUrlFetcher.fetchAll(any(), any(), any()) }
        assertThat(assistantOf(vm).modelOnlyNotice).isEqualTo(ModelOnlyNotice.OFFLINE)

        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `retry with stale connectivity returns without fetching`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery { multiUrlFetcher.fetchAll(any(), any(), any()) } returns
            MultiUrlResult.AllFailed(GroundingResult.Reason.OFFLINE)

        vm.sendMessage("mira https://a.example/uno")
        advanceUntilIdle()
        val queued = assistantOf(vm)

        every { fetcher.hasValidatedInternet() } returns false
        vm.retryGrounding(queued.id)
        advanceUntilIdle()

        coVerify(exactly = 1) { multiUrlFetcher.fetchAll(any(), any(), any()) }
        assertThat(assistantOf(vm).modelOnlyNotice).isEqualTo(ModelOnlyNotice.OFFLINE)
        assertThat(vm.inputState.value.isValidatedOnline).isFalse()
    }
}
