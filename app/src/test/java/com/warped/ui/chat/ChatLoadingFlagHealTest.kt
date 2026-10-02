package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.MultiUrlFetcher
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.local.inference.ActiveEngine
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.EngineType
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.local.security.KeystoreManager
import com.warped.data.remote.provider.ProviderRouter
import com.warped.data.repository.ModelAllowlistRepository
import com.warped.domain.llm.LlmModelHelper
import com.warped.domain.model.ActiveModelSelection
import com.warped.domain.model.ParameterStore
import com.warped.domain.model.ProviderType
import com.warped.domain.model.SyntaxTheme
import com.warped.domain.repository.ChatRepository
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.LocalModelRepository
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
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

/**
 * Regression tests for the stuck "Loading …litertlm" indicator heal in
 * [ChatViewModel.refreshActiveBackend].
 *
 * The indicator derives from `localSelection` (`modelId != null &&
 * !connected`), but several paths leave `LocalSelection(modelId,
 * connected=false)` behind while the engine is already loaded for that same
 * path (restart restore, cancelled preload, provider lazy-load). The heal
 * reconciles selection state against engine truth, so the untouched
 * collector clears `isLoadingModel` itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatLoadingFlagHealTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Fixture(
        val vm: ChatViewModel,
        val selection: ActiveModelSelection,
        val engineManager: EngineManager,
    )

    /**
     * @param persistedLocalJson raw value for the `last_active_local`
     * keystore key (mirrors `ActiveModelSelection.LAST_LOCAL_KEY`), or null
     * for a fresh install with no persisted selection.
     * @param enginePath model path the stubbed engine reports loaded, or
     * null for no loaded engine.
     */
    private fun buildFixture(
        persistedLocalJson: String? = null,
        enginePath: String? = null,
    ): Fixture {
        val chatRepository = mockk<ChatRepository>()
        val endpointRepository = mockk<EndpointRepository>()
        val localModelRepository = mockk<LocalModelRepository>()
        val providerRouter = mockk<ProviderRouter>()
        val engineManager = mockk<EngineManager>()
        val memoryChecker = mockk<MemoryChecker>()
        val advancedPreferences = mockk<AdvancedPreferences>()
        val keystoreManager = mockk<KeystoreManager>()
        val context = mockk<Context>()
        every { context.getString(any<Int>()) } returns ""
        every { context.getString(any<Int>(), *anyVararg<Any>()) } returns ""

        every { chatRepository.observeConversations() } returns MutableStateFlow(emptyList())
        every { endpointRepository.observeEndpoints() } returns MutableStateFlow(emptyList())
        every { localModelRepository.observeModels() } returns MutableStateFlow(emptyList())

        // Generic keystore stub first; the persisted-selection stub declared
        // after it wins for that key (mockk resolves latest-declared first).
        every { keystoreManager.get(any()) } returns null
        if (persistedLocalJson != null) {
            every { keystoreManager.get("last_active_local") } returns persistedLocalJson
        }
        every { keystoreManager.put(any(), any()) } just Runs
        every { keystoreManager.remove(any()) } just Runs

        // Real selection object so markLocalLoading/connectLocal emissions
        // flow through the real collector path under test.
        val selection = ActiveModelSelection(keystoreManager)

        every { engineManager.getActiveEngine() } returns
            enginePath?.let { ActiveEngine(EngineType.LITE_RT_LM, it) }
        every { engineManager.switchToLiteRT(any()) } just Runs
        every { engineManager.scheduleUnload() } just Runs

        every { advancedPreferences.syntaxTheme } returns flowOf(SyntaxTheme.MONOKAI)
        every { advancedPreferences.codeFontScale } returns flowOf(1.0f)
        every { advancedPreferences.thinkingEnabled } returns flowOf(false)
        every { advancedPreferences.webGroundingEnabled } returns flowOf(false)
        every { providerRouter.resolveLocalHelper(any(), any()) } returns idleHelper()
        val fetcher = mockk<WebPageFetcher>()
        every { fetcher.cancel() } just Runs
        val multiUrlFetcher = mockk<MultiUrlFetcher>()

        val vm = ChatViewModel(
            chatRepository = chatRepository,
            endpointRepository = endpointRepository,
            localModelRepository = localModelRepository,
            activeModelSelection = selection,
            providerRouter = providerRouter,
            savedStateHandle = SavedStateHandle(),
            parameterStore = ParameterStore(),
            engineManager = engineManager,
            memoryChecker = memoryChecker,
            advancedPreferences = advancedPreferences,
            fetcher = fetcher,
            multiUrlFetcher = multiUrlFetcher,
            ddgSearchRepository = mockk<DuckDuckGoSearchRepository>(),
            modelAllowlistRepository = mockk<ModelAllowlistRepository>(),
            context = context,
        )
        return Fixture(vm, selection, engineManager)
    }

    private fun idleHelper(): LlmModelHelper {
        val helper = mockk<LlmModelHelper>()
        every { helper.type } returns ProviderType.LITE_RT_LM
        return helper
    }

    private fun ChatViewModel.refreshActiveBackendReflective() {
        val method = ChatViewModel::class.java.getDeclaredMethod("refreshActiveBackend")
        method.isAccessible = true
        method.invoke(this)
    }

    @Test
    fun `connect transition clears the loading flag`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelPath = "/models/tiny.litertlm"
        val fixture = buildFixture(enginePath = modelPath)
        val vm = fixture.vm
        runCurrent()

        assertThat(vm.connectionState.value.isLoadingModel).isFalse()

        fixture.selection.markLocalLoading(modelPath)
        runCurrent()
        assertThat(vm.connectionState.value.isLoadingModel).isTrue()
        assertThat(vm.connectionState.value.selectedLocalModelId).isEqualTo(modelPath)

        fixture.selection.connectLocal(modelPath, ProviderType.LITE_RT_LM)
        advanceUntilIdle()
        assertThat(vm.connectionState.value.isLoadingModel).isFalse()
        assertThat(vm.connectionState.value.isLocalModelLoaded).isTrue()
        assertThat(vm.connectionState.value.selectedLocalModelId).isEqualTo(modelPath)
    }

    @Test
    fun `stuck heal clears the flag once engine truth reconciles`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelPath = "/models/tiny.litertlm"
        // Engine starts absent: mark-without-connect strands the flag on.
        val fixture = buildFixture(enginePath = null)
        val vm = fixture.vm
        runCurrent()

        fixture.selection.markLocalLoading(modelPath)
        runCurrent()
        assertThat(vm.connectionState.value.isLoadingModel).isTrue()

        // The engine is (lazily) loaded for the same path behind the
        // selection's back — the on-device stuck state.
        every { fixture.engineManager.getActiveEngine() } returns
            ActiveEngine(EngineType.LITE_RT_LM, modelPath)
        vm.refreshActiveBackendReflective()
        advanceUntilIdle()

        assertThat(vm.connectionState.value.isLoadingModel).isFalse()
        assertThat(vm.connectionState.value.isLocalModelLoaded).isTrue()
    }

    @Test
    fun `still loading keeps the indicator when engine is absent or different`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelPath = "/models/tiny.litertlm"
        val fixture = buildFixture(enginePath = null)
        val vm = fixture.vm
        runCurrent()

        fixture.selection.markLocalLoading(modelPath)
        runCurrent()
        assertThat(vm.connectionState.value.isLoadingModel).isTrue()

        // Engine absent: genuinely still loading.
        vm.refreshActiveBackendReflective()
        advanceUntilIdle()
        assertThat(vm.connectionState.value.isLoadingModel).isTrue()

        // Engine loaded for a DIFFERENT model: still loading this one.
        every { fixture.engineManager.getActiveEngine() } returns
            ActiveEngine(EngineType.LITE_RT_LM, "/models/other.litertlm")
        vm.refreshActiveBackendReflective()
        advanceUntilIdle()
        assertThat(vm.connectionState.value.isLoadingModel).isTrue()
        assertThat(vm.connectionState.value.selectedLocalModelId).isEqualTo(modelPath)
    }

    @Test
    fun `disconnect clears the flag and the selection`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelPath = "/models/tiny.litertlm"
        val fixture = buildFixture(enginePath = null)
        val vm = fixture.vm
        runCurrent()

        fixture.selection.markLocalLoading(modelPath)
        runCurrent()
        assertThat(vm.connectionState.value.isLoadingModel).isTrue()

        fixture.selection.disconnectLocal()
        advanceUntilIdle()
        assertThat(vm.connectionState.value.isLoadingModel).isFalse()
        assertThat(vm.connectionState.value.selectedLocalModelId).isNull()
    }

    @Test
    fun `restart restore heals the rehydrated unconnected selection`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelPath = "/models/tiny.litertlm"
        // Process restart: the persisted selection rehydrates as
        // LocalSelection(modelId, connected=false) while the engine is
        // already warm for the same path.
        val fixture = buildFixture(
            persistedLocalJson = """{"modelId":"$modelPath"}""",
            enginePath = modelPath,
        )
        val vm = fixture.vm
        advanceUntilIdle()

        // The stuck state is visible right after restore.
        assertThat(vm.connectionState.value.selectedLocalModelId).isEqualTo(modelPath)
        assertThat(vm.connectionState.value.isLoadingModel).isTrue()

        vm.refreshActiveBackendReflective()
        advanceUntilIdle()

        assertThat(vm.connectionState.value.isLoadingModel).isFalse()
        assertThat(vm.connectionState.value.isLocalModelLoaded).isTrue()
    }
}
