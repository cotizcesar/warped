package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.data.local.inference.ActiveEngine
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.EngineType
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.ProviderRouter
import com.warped.domain.model.ActiveModelSelection
import com.warped.domain.model.LocalModel
import com.warped.domain.model.ParameterStore
import com.warped.domain.model.ProviderType
import com.warped.domain.model.SyntaxTheme
import com.warped.domain.repository.ChatRepository
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.LocalModelRepository
import com.warped.domain.review.ReviewHelper
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
import io.mockk.verify
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
import java.time.Instant

/**
 * Model switch must unload the old engine and mount the new one with no
 * stray unload in between: switchToLiteRT unloads atomically first, so a
 * concurrent explicit unload could win the monitor AFTER the fresh init
 * and kill the just-mounted engine (stuck "Loading…" forever).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelSwitchUnloadTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun model(path: String, at: Instant) = LocalModel(
        id = 1,
        name = path.substringAfterLast("/"),
        filePath = path,
        sizeBytes = 10,
        quantization = "Q4",
        parameterCount = "1B",
        architecture = "gemma",
        importedAt = at
    )

    @Test
    fun `local to local switch unloads via switch only, no explicit unload`() = runTest {
        val pathA = "/models/a.litertlm"
        val pathB = "/models/b.litertlm"
        val models = listOf(
            model(pathA, Instant.EPOCH),
            model(pathB, Instant.EPOCH.plusSeconds(10))
        )
        val keystoreManager = mockk<com.warped.data.local.security.KeystoreManager>()
        every { keystoreManager.get(any()) } returns null
        every { keystoreManager.put(any(), any()) } just Runs
        every { keystoreManager.remove(any()) } just Runs
        val selection = ActiveModelSelection(keystoreManager)

        val localModelRepository = mockk<LocalModelRepository>()
        every { localModelRepository.observeModels() } returns MutableStateFlow(models)
        val engineManager = mockk<EngineManager>()
        every { engineManager.getActiveEngine() } returns ActiveEngine(EngineType.LITE_RT_LM, pathA)
        every { engineManager.switchToLiteRT(any()) } just Runs
        val memoryChecker = mockk<MemoryChecker>()
        every { memoryChecker.canLoadModel(any()) } returns true
        every { memoryChecker.shouldWarn(any()) } returns false

        val chatRepository = mockk<ChatRepository>()
        every { chatRepository.observeConversations() } returns MutableStateFlow(emptyList())
        val endpointRepository = mockk<EndpointRepository>()
        every { endpointRepository.observeEndpoints() } returns MutableStateFlow(emptyList())
        val advancedPreferences = mockk<AdvancedPreferences>()
        every { advancedPreferences.syntaxTheme } returns flowOf(SyntaxTheme.MONOKAI)
        every { advancedPreferences.codeFontScale } returns flowOf(1.0f)
        every { advancedPreferences.thinkingEnabled } returns flowOf(false)
        every { advancedPreferences.webGroundingEnabled } returns flowOf(false)
        val context = mockk<Context>()
        every { context.getString(any<Int>()) } returns ""
        every { context.getString(any<Int>(), *anyVararg<Any>()) } returns ""

        val vm = ChatViewModel(
            chatRepository = chatRepository,
            endpointRepository = endpointRepository,
            localModelRepository = localModelRepository,
            activeModelSelection = selection,
            providerRouter = mockk<ProviderRouter>(),
            savedStateHandle = SavedStateHandle(),
            parameterStore = ParameterStore(),
            engineManager = engineManager,
            memoryChecker = memoryChecker,
            advancedPreferences = advancedPreferences,
            fetcher = mockk<com.warped.data.grounding.WebPageFetcher>(),
            multiUrlFetcher = mockk<com.warped.data.grounding.MultiUrlFetcher>(),
            ddgSearchRepository = mockk(),
            modelAllowlistRepository = mockk<com.warped.data.repository.ModelAllowlistRepository>(),
            context = context,
            reviewHelper = mockk(relaxed = true),
        )
        runCurrent()
        advanceUntilIdle()

        vm.launchModelSelection(pathB, ProviderType.LITE_RT_LM)
        advanceUntilIdle()

        // The new model mounts through switchToLiteRT (which unloads first).
        // No concurrent explicit unload that could kill the fresh engine.
        verify(exactly = 0) { engineManager.unloadCurrent() }
    }

    @Test
    fun `memory blocked preload clears loading flag and keeps selection`() = runTest {
        val pathB = "/models/b.litertlm"
        val models = listOf(model(pathB, Instant.EPOCH))
        val keystoreManager = mockk<com.warped.data.local.security.KeystoreManager>()
        every { keystoreManager.get(any()) } returns null
        every { keystoreManager.put(any(), any()) } just Runs
        every { keystoreManager.remove(any()) } just Runs
        val selection = ActiveModelSelection(keystoreManager)

        val localModelRepository = mockk<LocalModelRepository>()
        every { localModelRepository.observeModels() } returns MutableStateFlow(models)
        val engineManager = mockk<EngineManager>()
        every { engineManager.getActiveEngine() } returns null
        val memoryChecker = mockk<MemoryChecker>()
        every { memoryChecker.canLoadModel(any()) } returns false
        every { memoryChecker.shouldWarn(any()) } returns false
        every { memoryChecker.getMemoryInfo() } returns
            com.warped.data.local.inference.MemoryInfo(
                availableBytes = 100L * 1024L * 1024L,
                totalBytes = 4000L * 1024L * 1024L,
                usedPercent = 97
            )

        val chatRepository = mockk<ChatRepository>()
        every { chatRepository.observeConversations() } returns MutableStateFlow(emptyList())
        val endpointRepository = mockk<EndpointRepository>()
        every { endpointRepository.observeEndpoints() } returns MutableStateFlow(emptyList())
        val advancedPreferences = mockk<AdvancedPreferences>()
        every { advancedPreferences.syntaxTheme } returns flowOf(SyntaxTheme.MONOKAI)
        every { advancedPreferences.codeFontScale } returns flowOf(1.0f)
        every { advancedPreferences.thinkingEnabled } returns flowOf(false)
        every { advancedPreferences.webGroundingEnabled } returns flowOf(false)
        val context = mockk<Context>()
        every { context.getString(any<Int>()) } returns ""
        every { context.getString(any<Int>(), *anyVararg<Any>()) } returns ""

        val vm = ChatViewModel(
            chatRepository = chatRepository,
            endpointRepository = endpointRepository,
            localModelRepository = localModelRepository,
            activeModelSelection = selection,
            providerRouter = mockk<ProviderRouter>(),
            savedStateHandle = SavedStateHandle(),
            parameterStore = ParameterStore(),
            engineManager = engineManager,
            memoryChecker = memoryChecker,
            advancedPreferences = advancedPreferences,
            fetcher = mockk<com.warped.data.grounding.WebPageFetcher>(),
            multiUrlFetcher = mockk<com.warped.data.grounding.MultiUrlFetcher>(),
            ddgSearchRepository = mockk(),
            modelAllowlistRepository = mockk<com.warped.data.repository.ModelAllowlistRepository>(),
            context = context,
            reviewHelper = mockk(relaxed = true),
        )
        runCurrent()
        advanceUntilIdle()

        vm.launchModelSelection(pathB, ProviderType.LITE_RT_LM)
        advanceUntilIdle()

        // Error surfaced, spinner cleared, selection kept for retry.
        assertThat(vm.connectionState.value.modelLoadError).isNotNull()
        assertThat(vm.connectionState.value.isLoadingModel).isFalse()
        assertThat(vm.connectionState.value.selectedLocalModelId).isEqualTo(pathB)
        verify(exactly = 0) { engineManager.switchToLiteRT(any()) }
    }
}
