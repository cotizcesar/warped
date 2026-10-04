package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.ProviderRouter
import com.warped.domain.model.Conversation
import com.warped.domain.model.LocalModel
import com.warped.domain.model.LocalSelection
import com.warped.domain.model.ParameterStore
import com.warped.domain.model.ProviderType
import com.warped.domain.model.RemoteSelection
import com.warped.domain.model.SyntaxTheme
import com.warped.domain.repository.ChatRepository
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.LocalModelRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
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
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * 2026-10-04 silent-RED fix: a local conversation whose file is gone
 * from disk (DB row intact) must surface the sticky unavailable state
 * instead of mounting nothing and sitting red with no message; a failed
 * mount must be retryable via [ChatViewModel.retryLocalMount].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalMountRecoveryTest {

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @TempDir
    lateinit var tempDir: File

    private fun model(path: String) = LocalModel(
        name = path.substringAfterLast("/"),
        filePath = path,
        sizeBytes = 600L * 1024L * 1024L,
        quantization = "Q8",
        parameterCount = "1B",
        architecture = "gemma3",
        importedAt = Instant.EPOCH,
    )

    private class Fixture(
        val vm: ChatViewModel,
        val activeModelSelection: com.warped.domain.model.ActiveModelSelection,
        val engineManager: EngineManager,
        val chatRepository: ChatRepository,
        val localSelectionFlow: MutableStateFlow<LocalSelection>,
    )

    private fun build(models: List<LocalModel>): Fixture {
        val chatRepository = mockk<ChatRepository>()
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
        coEvery { chatRepository.getWebOverride(any()) } returns null
        every { endpointRepository.observeEndpoints() } returns MutableStateFlow(emptyList())
        every { localModelRepository.observeModels() } returns MutableStateFlow(models)
        // DB row present (the file itself may still be gone — that is
        // exactly what the missing-file test exercises).
        coEvery { localModelRepository.existsByFilePath(any()) } returns true
        val localSelectionFlow = MutableStateFlow(LocalSelection())
        every { activeModelSelection.activeModel } returns MutableStateFlow(null)
        every { activeModelSelection.localSelection } returns localSelectionFlow
        every { activeModelSelection.remoteSelection } returns MutableStateFlow(RemoteSelection())
        every { activeModelSelection.selectLocalPending(any()) } answers {
            localSelectionFlow.value = LocalSelection(modelId = firstArg())
        }
        every { activeModelSelection.markLocalLoading(any()) } answers {
            localSelectionFlow.value = LocalSelection(modelId = firstArg(), isLoading = true)
        }
        every { activeModelSelection.markLocalDisconnected() } answers {
            localSelectionFlow.value = localSelectionFlow.value.copy(isConnected = false, isLoading = false)
        }
        every { activeModelSelection.connectLocal(any(), any()) } answers {
            localSelectionFlow.value = LocalSelection(modelId = firstArg(), isConnected = true)
        }
        every { activeModelSelection.saveLastConversation(any()) } just Runs
        every { engineManager.getActiveEngine() } returns null
        every { engineManager.scheduleUnload() } just Runs
        every { memoryChecker.shouldWarn(any()) } returns false
        every { memoryChecker.canLoadModel(any()) } returns true
        every { advancedPreferences.syntaxTheme } returns flowOf(SyntaxTheme.MONOKAI)
        every { advancedPreferences.codeFontScale } returns flowOf(1.0f)
        every { advancedPreferences.thinkingEnabled } returns flowOf(false)
        every { advancedPreferences.webGroundingEnabled } returns flowOf(false)
        val fetcher = mockk<com.warped.data.grounding.WebPageFetcher>()
        every { fetcher.cancel() } just Runs

        val vm = ChatViewModel(
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
            multiUrlFetcher = mockk<com.warped.data.grounding.MultiUrlFetcher>(),
            ddgSearchRepository = mockk(),
            modelAllowlistRepository = mockk(relaxed = true),
            context = context,
            reviewHelper = mockk(relaxed = true),
        )
        return Fixture(vm, activeModelSelection, engineManager, chatRepository, localSelectionFlow)
    }

    @Test
    fun `missing file surfaces unavailable instead of silent red`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val missingPath = File(tempDir, "gone.litertlm").absolutePath
        val f = build(models = listOf(model(missingPath)))
        coEvery { f.chatRepository.loadConversation(11) } returns (
            Conversation(
                id = 11,
                title = "chat",
                providerType = ProviderType.LITE_RT_LM,
                endpointId = 0,
                modelId = missingPath,
                createdAt = java.time.Instant.EPOCH,
                updatedAt = java.time.Instant.EPOCH,
            ) to emptyList()
            )
        // DB row exists, but nothing on disk.
        advanceUntilIdle()

        f.vm.selectConversation(11)
        advanceUntilIdle()

        assertThat(f.vm.connectionState.value.modelUnavailable).isTrue()
        // The missing file is never mounted (the init-time auto-pick may
        // still mark it pending — that path never mounts).
        verify(exactly = 0) { f.activeModelSelection.markLocalLoading(any()) }
    }

    @Test
    fun `retry recovers after a failed mount`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val path = File(tempDir, "m.litertlm").apply { writeText("fake") }.absolutePath
        val f = build(models = listOf(model(path)))
        val calls = AtomicInteger(0)
        every { f.engineManager.switchToLiteRT(any()) } answers {
            if (calls.getAndIncrement() == 0) throw RuntimeException("boom") else Unit
        }
        advanceUntilIdle()

        f.vm.launchModelSelection(path, ProviderType.LITE_RT_LM, null)
        // The throw hops off Dispatchers.Default (a real thread under
        // runTest) — interleave scheduler pumping with real waits until
        // the failure lands (virtual-only delays starve real threads
        // under parallel load).
        var deadline = System.currentTimeMillis() + 5000
        while (f.vm.connectionState.value.modelLoadError == null &&
            System.currentTimeMillis() < deadline
        ) {
            advanceUntilIdle()
            Thread.sleep(25)
        }
        advanceUntilIdle()
        assertThat(f.vm.connectionState.value.modelLoadError).isNotNull()

        f.vm.retryLocalMount()
        // Remount settles async: wait for the loaded flag the collector
        // derives from connectLocal's emission (observing it proves the
        // connect was recorded — deterministic, no timeout-verify).
        deadline = System.currentTimeMillis() + 5000
        while (!f.vm.connectionState.value.isLocalModelLoaded &&
            System.currentTimeMillis() < deadline
        ) {
            advanceUntilIdle()
            Thread.sleep(25)
        }
        advanceUntilIdle()

        assertThat(f.vm.connectionState.value.modelLoadError).isNull()
        assertThat(f.vm.connectionState.value.isLocalModelLoaded).isTrue()
        verify { f.activeModelSelection.connectLocal(path, ProviderType.LITE_RT_LM, null) }
    }
}
