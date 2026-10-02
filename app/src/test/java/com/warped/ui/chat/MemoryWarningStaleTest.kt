package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.data.local.inference.ActiveEngine
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.EngineType
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.inference.MemoryInfo
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.ProviderRouter
import com.warped.domain.model.ActiveModelSelection
import com.warped.domain.model.LocalModel
import com.warped.domain.model.ModelCapabilities
import com.warped.domain.model.ParameterStore
import com.warped.domain.model.ProviderType
import com.warped.domain.model.SyntaxTheme
import com.warped.domain.repository.ChatRepository
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.LocalModelRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
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
 * Device report: the "Not enough memory" banner stayed on screen after
 * switching from a too-big model (gemma-4-E2B-it, 2468 MB) to a small one
 * (gemma-3-1b-it) — `memoryWarningModel` was set on selection but never
 * cleared. Selecting a fitting model must drop the stale warning.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoryWarningStaleTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun model(path: String, sizeBytes: Long) = LocalModel(
        id = 1,
        name = path.substringAfterLast("/"),
        filePath = path,
        sizeBytes = sizeBytes,
        quantization = "Q4",
        parameterCount = "1B",
        architecture = "gemma",
        importedAt = Instant.EPOCH
    )

    private fun buildVm(models: List<LocalModel>, warnOverBytes: Long): ChatViewModel {
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
        every { memoryChecker.shouldWarn(any()) } answers { firstArg<Long>() > warnOverBytes }
        every { memoryChecker.getMemoryInfo() } returns
            MemoryInfo(
                availableBytes = 1459L * 1024L * 1024L,
                totalBytes = 4000L * 1024L * 1024L,
                usedPercent = 63
            )

        val chatRepository = mockk<ChatRepository>()
        every { chatRepository.observeConversations() } returns MutableStateFlow(emptyList())
        coEvery { chatRepository.createConversation(any(), any(), any(), any()) } returns 42L
        coEvery { chatRepository.saveMessage(any(), any()) } just Runs
        coEvery { chatRepository.getWebOverride(any()) } returns null
        val endpointRepository = mockk<EndpointRepository>(relaxed = true)
        every { endpointRepository.observeEndpoints() } returns MutableStateFlow(emptyList())
        val providerRouter = mockk<ProviderRouter>(relaxed = true)
        val advancedPreferences = mockk<AdvancedPreferences>()
        every { advancedPreferences.syntaxTheme } returns flowOf(SyntaxTheme.MONOKAI)
        every { advancedPreferences.codeFontScale } returns flowOf(1.0f)
        every { advancedPreferences.thinkingEnabled } returns flowOf(false)
        every { advancedPreferences.webGroundingEnabled } returns flowOf(false)
        val context = mockk<Context>()
        every { context.getString(any<Int>()) } returns ""
        every { context.getString(any<Int>(), *anyVararg<Any>()) } returns ""
        val fetcher = mockk<com.warped.data.grounding.WebPageFetcher>()
        every { fetcher.cancel() } just Runs
        every { fetcher.hasValidatedInternet() } returns false
        val allowlist = mockk<com.warped.data.repository.ModelAllowlistRepository>()
        every { allowlist.effectiveCapabilities(any()) } returns ModelCapabilities()

        return ChatViewModel(
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
            multiUrlFetcher = mockk<com.warped.data.grounding.MultiUrlFetcher>(),
            ddgSearchRepository = mockk(),
            modelAllowlistRepository = allowlist,
            context = context,
            reviewHelper = mockk(relaxed = true),
        )
    }

    @Test
    fun `selecting an oversized model raises the warning`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val big = model("/m/gemma-4-E2B-it.litertlm", 2_468_000_000L)
        val vm = buildVm(listOf(big), warnOverBytes = 1_000_000_000L)
        runCurrent()
        advanceUntilIdle()

        vm.launchModelSelection(big.filePath, ProviderType.LITE_RT_LM)
        advanceUntilIdle()

        assertThat(vm.connectionState.value.memoryWarningModel?.filePath).isEqualTo(big.filePath)
    }

    @Test
    fun `switching to a fitting model clears the stale warning`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val big = model("/m/gemma-4-E2B-it.litertlm", 2_468_000_000L)
        val small = model("/m/gemma-3-1b-it.litertlm", 500_000_000L)
        val vm = buildVm(listOf(big, small), warnOverBytes = 1_000_000_000L)
        runCurrent()
        advanceUntilIdle()

        vm.launchModelSelection(big.filePath, ProviderType.LITE_RT_LM)
        advanceUntilIdle()
        assertThat(vm.connectionState.value.memoryWarningModel?.filePath).isEqualTo(big.filePath)

        vm.launchModelSelection(small.filePath, ProviderType.LITE_RT_LM)
        advanceUntilIdle()

        assertThat(vm.connectionState.value.memoryWarningModel).isNull()
        assertThat(vm.connectionState.value.selectedLocalModelId).isEqualTo(small.filePath)
    }
}
