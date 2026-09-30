package com.warped.ui.settings

import android.content.Context
import com.warped.R
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.data.grounding.GroundingPrompt
import com.warped.data.grounding.GroundingResult
import com.warped.data.grounding.MultiUrlResult
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.TavilySearchOutcome
import com.warped.data.grounding.TavilySearchRepository
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.local.security.ApiKeyStore
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
import com.warped.domain.repository.PresetRepository
import com.warped.ui.chat.ChatViewModel
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
 * Phase 55 (TAV-01..TAV-03): Settings Tavily key state + ChatViewModel
 * search-branch gate matrix + fusion wiring.
 *
 * - SettingsViewModel save/clear/test transitions run against a MockK
 *   ApiKeyStore Tavily alias + MockK TavilySearchRepository (the 55-01
 *   MockK-fake pattern — no MockWebServer, zero new deps).
 * - The gate matrix pins D-03: grounding-off and offline never reach
 *   search; missing/invalid/limit keys yield distinct actionable copy.
 * - The wiring test pins D-02 fusion identity: a Fused search result
 *   reaches GroundingPrompt.augment + groundedSources + the
 *   saveMessageWithSources details union exactly like the URL path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsTavilyTest {

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

    private lateinit var apiKeyStore: ApiKeyStore
    private lateinit var tavilyRepo: TavilySearchRepository

    private fun buildSettingsViewModel(
        storedKey: CharArray? = null,
    ): SettingsViewModel {
        val chatRepository = mockk<ChatRepository>()
        val endpointRepository = mockk<EndpointRepository>()
        val localModelRepository = mockk<LocalModelRepository>()
        val presetRepository = mockk<PresetRepository>()
        apiKeyStore = mockk()
        val advancedPreferences = mockk<AdvancedPreferences>()
        tavilyRepo = mockk()

        every { chatRepository.observeConversations() } returns flowOf(emptyList())
        every { endpointRepository.observeEndpoints() } returns flowOf(emptyList())
        every { localModelRepository.observeModels() } returns flowOf(emptyList())
        every { presetRepository.observePresets() } returns flowOf(emptyList())
        every { advancedPreferences.syntaxTheme } returns flowOf(SyntaxTheme.MONOKAI)
        every { advancedPreferences.codeFontScale } returns flowOf(1.0f)
        every { advancedPreferences.webGroundingEnabled } returns flowOf(true)
        every { apiKeyStore.getTavilyKey() } returns storedKey
        every { apiKeyStore.storeTavilyKey(any()) } just Runs
        every { apiKeyStore.deleteTavilyKey() } just Runs
        every { apiKeyStore.deleteAllKeys(any()) } just Runs
        // Localized VM copy resolves through Context — stub the EN values so
        // copy assertions below stay anchored to the canonical strings.
        val context = mockk<Context>()
        every { context.getString(R.string.tavily_paste_key) } returns "Paste a key before saving."
        every { context.getString(R.string.tavily_key_saved) } returns "Tavily API key saved."
        every { context.getString(R.string.tavily_save_failed) } returns "Couldn't save the key. Try again."
        every { context.getString(R.string.tavily_key_deleted) } returns "Tavily API key deleted."
        every { context.getString(R.string.tavily_delete_failed) } returns "Couldn't delete the key. Try again."
        every { context.getString(R.string.tavily_testing) } returns "Testing connection..."
        every { context.getString(R.string.tavily_ok) } returns "Connection successful. Tavily search is working."
        every { context.getString(R.string.tavily_bad_key) } returns "Invalid API key. Check the key and try again."
        every { context.getString(R.string.tavily_429) } returns "Usage limit reached (429). Check your Tavily plan."
        every { context.getString(R.string.tavily_net_error) } returns "Network error. Check your connection and try again."
        every { context.getString(R.string.tavily_no_key) } returns "No API key saved. Get one at tavily.com and paste it above."
        every { context.getString(R.string.settings_msg_chats_deleted) } returns "All chat history deleted"
        every { context.getString(R.string.settings_msg_keys_deleted) } returns "All API keys deleted"
        every { context.getString(R.string.settings_msg_key_deleted) } returns "API key deleted"

        return SettingsViewModel(
            chatRepository = chatRepository,
            endpointRepository = endpointRepository,
            localModelRepository = localModelRepository,
            presetRepository = presetRepository,
            apiKeyStore = apiKeyStore,
            advancedPreferences = advancedPreferences,
            tavilySearchRepository = tavilyRepo,
            context = context,
        )
    }

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

    // ------------------------------------------------------------------
    // SettingsViewModel: save / clear / presence
    // ------------------------------------------------------------------

    @Test
    fun `save stores key through Keystore alias and clears input`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = buildSettingsViewModel()
        runCurrent()

        vm.onTavilyKeyInputChange("tvly-secret")
        vm.saveTavilyKey()
        advanceUntilIdle()

        verify(exactly = 1) { apiKeyStore.storeTavilyKey(any()) }
        assertThat(vm.uiState.value.tavilyKeyInput).isEmpty()
        assertThat(vm.uiState.value.tavilyKeyPresent).isTrue()
        assertThat(vm.uiState.value.tavilyStatus).isEqualTo("Tavily API key saved.")
        assertThat(vm.uiState.value.tavilyStatusIsError).isFalse()
    }

    @Test
    fun `save with blank input shows guidance and never touches store`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = buildSettingsViewModel()
        runCurrent()

        vm.onTavilyKeyInputChange("   ")
        vm.saveTavilyKey()
        advanceUntilIdle()

        verify(exactly = 0) { apiKeyStore.storeTavilyKey(any()) }
        assertThat(vm.uiState.value.tavilyStatus).isEqualTo("Paste a key before saving.")
        assertThat(vm.uiState.value.tavilyStatusIsError).isTrue()
    }

    @Test
    fun `clear deletes alias and resets card state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = buildSettingsViewModel(storedKey = "tvly-secret".toCharArray())
        runCurrent()
        assertThat(vm.uiState.value.tavilyKeyPresent).isTrue()

        vm.clearTavilyKey()
        advanceUntilIdle()

        verify(exactly = 1) { apiKeyStore.deleteTavilyKey() }
        assertThat(vm.uiState.value.tavilyKeyPresent).isFalse()
        assertThat(vm.uiState.value.tavilyStatus).isEqualTo("Tavily API key deleted.")
        assertThat(vm.uiState.value.tavilyStatusIsError).isFalse()
    }

    @Test
    fun `presence reflects stored key at init`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        val withKey = buildSettingsViewModel(storedKey = "tvly-secret".toCharArray())
        runCurrent()
        assertThat(withKey.uiState.value.tavilyKeyPresent).isTrue()

        val withoutKey = buildSettingsViewModel(storedKey = null)
        runCurrent()
        assertThat(withoutKey.uiState.value.tavilyKeyPresent).isFalse()
    }

    @Test
    fun `delete-all-keys wipes Tavily and resets card state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = buildSettingsViewModel(storedKey = "tvly-secret".toCharArray())
        runCurrent()
        assertThat(vm.uiState.value.tavilyKeyPresent).isTrue()

        vm.deleteAllApiKeys()
        advanceUntilIdle()

        verify(exactly = 1) { apiKeyStore.deleteAllKeys(emptyList()) }
        assertThat(vm.uiState.value.tavilyKeyPresent).isFalse()
        assertThat(vm.uiState.value.tavilyStatus).isNull()
    }

    // ------------------------------------------------------------------
    // SettingsViewModel: test-connection four states + missing key
    // ------------------------------------------------------------------

    @Test
    fun `test success shows working copy`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = buildSettingsViewModel(storedKey = "tvly-secret".toCharArray())
        runCurrent()
        coEvery { tavilyRepo.search(any(), any(), any()) } returns
            TavilySearchOutcome.Grounded(fusedSearchResult())

        vm.testTavilyConnection()
        advanceUntilIdle()

        coVerify(exactly = 1) { tavilyRepo.search("test", 1, any()) }
        assertThat(vm.uiState.value.tavilyTesting).isFalse()
        assertThat(vm.uiState.value.tavilyStatus)
            .isEqualTo("Connection successful. Tavily search is working.")
        assertThat(vm.uiState.value.tavilyStatusIsError).isFalse()
    }

    @Test
    fun `test invalid key shows invalid-key copy`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = buildSettingsViewModel(storedKey = "tvly-bad".toCharArray())
        runCurrent()
        coEvery { tavilyRepo.search(any(), any(), any()) } returns TavilySearchOutcome.InvalidKey

        vm.testTavilyConnection()
        advanceUntilIdle()

        assertThat(vm.uiState.value.tavilyStatus)
            .isEqualTo("Invalid API key. Check the key and try again.")
        assertThat(vm.uiState.value.tavilyStatusIsError).isTrue()
    }

    @Test
    fun `test usage limit shows limit copy`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = buildSettingsViewModel(storedKey = "tvly-secret".toCharArray())
        runCurrent()
        coEvery { tavilyRepo.search(any(), any(), any()) } returns TavilySearchOutcome.UsageLimit

        vm.testTavilyConnection()
        advanceUntilIdle()

        assertThat(vm.uiState.value.tavilyStatus)
            .isEqualTo("Usage limit reached (429). Check your Tavily plan.")
        assertThat(vm.uiState.value.tavilyStatusIsError).isTrue()
    }

    @Test
    fun `test transport failure shows network copy`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = buildSettingsViewModel(storedKey = "tvly-secret".toCharArray())
        runCurrent()
        coEvery { tavilyRepo.search(any(), any(), any()) } returns TavilySearchOutcome.ModelOnly(
            MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
        )

        vm.testTavilyConnection()
        advanceUntilIdle()

        assertThat(vm.uiState.value.tavilyStatus)
            .isEqualTo("Network error. Check your connection and try again.")
        assertThat(vm.uiState.value.tavilyStatusIsError).isTrue()
    }

    @Test
    fun `test with no saved key shows missing-key copy`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = buildSettingsViewModel(storedKey = null)
        runCurrent()
        coEvery { tavilyRepo.search(any(), any(), any()) } returns TavilySearchOutcome.MissingKey

        vm.testTavilyConnection()
        advanceUntilIdle()

        assertThat(vm.uiState.value.tavilyStatus)
            .isEqualTo("No API key saved. Get one at tavily.com and paste it above.")
        assertThat(vm.uiState.value.tavilyStatusIsError).isTrue()
    }

    // ------------------------------------------------------------------
    // ChatViewModel: D-03 gate matrix + D-02 fusion wiring
    // ------------------------------------------------------------------

    private lateinit var chatRepository: ChatRepository
    private lateinit var chatDdgRepo: DuckDuckGoSearchRepository
    private lateinit var chatFetcher: com.warped.data.grounding.WebPageFetcher
    private lateinit var chatMultiUrlFetcher: com.warped.data.grounding.MultiUrlFetcher
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
        // Unkeyed device for the chat VM (the Settings VM above owns the
        // keyed states); chat queries here never carry image intent, so the
        // gate never reads this — stubbed for construction only.
        apiKeyStore = mockk()
        every { apiKeyStore.getTavilyKey() } returns null

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
            apiKeyStore = apiKeyStore,
            modelAllowlistRepository = mockk<com.warped.data.repository.ModelAllowlistRepository>(),
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

        vm.sendMessage("hola sin urls")
        advanceUntilIdle()

        // No socket: neither the fetcher fan-out nor the search producer runs.
        coVerify(exactly = 0) { chatDdgRepo.search(any(), any(), any()) }
        coVerify(exactly = 0) { chatMultiUrlFetcher.fetchAll(any(), any(), any()) }
        assertThat(assistantOf(vm).modelOnlyNotice).isEqualTo(ModelOnlyNotice.OFFLINE)
        // The always-on web instruction is preserved on the model-only turn.
        val requestSlot = slot<ChatRequest>()
        coVerify(exactly = 1) { lastHelper.runInference(capture(requestSlot), any()) }
        assertThat(requestSlot.captured.messages.last().content).isEqualTo(
            "${GroundingPrompt.SYSTEM_PROMPT}\n\nhola sin urls\n\nReply in English.",
        )
    }

    @Test
    fun `missing key yields actionable notice without fusion`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildChatViewModel(modelFile.absolutePath, online = true)
        runCurrent()
        // MissingKey survives only as the Tavily-fallback key-race edge
        // (DDG-primary needs no key): the arm stays, rendering the
        // actionable notice when the outcome ever surfaces.
        coEvery { chatDdgRepo.search(any(), any(), any()) } returns TavilySearchOutcome.MissingKey

        vm.sendMessage("que hay de nuevo")
        advanceUntilIdle()

        val assistant = assistantOf(vm)
        assertThat(assistant.modelOnlyNotice).isEqualTo(ModelOnlyNotice.TAVILY_MISSING_KEY)
        assertThat(assistant.groundedSources).isEmpty()
        val requestSlot = slot<ChatRequest>()
        coVerify(exactly = 1) { lastHelper.runInference(capture(requestSlot), any()) }
        assertThat(requestSlot.captured.messages.last().content).doesNotContain("Source [")
    }

    @Test
    fun `invalid key yields invalid-key notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildChatViewModel(modelFile.absolutePath, online = true)
        runCurrent()
        coEvery { chatDdgRepo.search(any(), any(), any()) } returns TavilySearchOutcome.InvalidKey

        vm.sendMessage("que hay de nuevo")
        advanceUntilIdle()

        assertThat(assistantOf(vm).modelOnlyNotice).isEqualTo(ModelOnlyNotice.TAVILY_INVALID_KEY)
    }

    @Test
    fun `usage limit yields limit notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildChatViewModel(modelFile.absolutePath, online = true)
        runCurrent()
        coEvery { chatDdgRepo.search(any(), any(), any()) } returns TavilySearchOutcome.UsageLimit

        vm.sendMessage("que hay de nuevo")
        advanceUntilIdle()

        assertThat(assistantOf(vm).modelOnlyNotice).isEqualTo(ModelOnlyNotice.TAVILY_LIMIT)
    }

    @Test
    fun `search failure yields fetch-failed notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildChatViewModel(modelFile.absolutePath, online = true)
        runCurrent()
        coEvery { chatDdgRepo.search(any(), any(), any()) } returns TavilySearchOutcome.ModelOnly(
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
            TavilySearchOutcome.Grounded(fused)

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
