package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.ProviderRouter
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 2026-10-04 stale-mount guard: a mount that finishes AFTER the
 * selection moved on (new chat, repick) must not connect the dead
 * model, clear the new turn's flags, or banner the chat the user left.
 * Without this the late completion resurrects a dead selection —
 * wrong-engine sends and locked input on the new chat.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StaleMountGuardTest {

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

    @Test
    fun `late mount completion after new chat connects nothing`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pathA = File(tempDir, "a.litertlm").apply { writeText("fake") }.absolutePath
        val modelA = LocalModel(
            name = "a.litertlm",
            filePath = pathA,
            sizeBytes = 600L * 1024L * 1024L,
            quantization = "Q8",
            parameterCount = "1B",
            architecture = "gemma3",
            importedAt = Instant.EPOCH,
        )

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

        val localSelectionFlow = MutableStateFlow(LocalSelection())
        every { chatRepository.observeConversations() } returns MutableStateFlow(emptyList())
        every { endpointRepository.observeEndpoints() } returns MutableStateFlow(emptyList())
        every { localModelRepository.observeModels() } returns MutableStateFlow(listOf(modelA))
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
        every { activeModelSelection.disconnectLocal() } answers {
            localSelectionFlow.value = LocalSelection()
        }
        every { activeModelSelection.clearRemote() } just Runs
        every { activeModelSelection.saveLastConversation(any()) } just Runs
        every { engineManager.getActiveEngine() } returns null
        val releaseMount = CountDownLatch(1)
        every { engineManager.switchToLiteRT(any()) } answers {
            releaseMount.await(10, TimeUnit.SECONDS)
            Unit
        }
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
        runCurrent()

        // Pick A: mount starts and hangs inside the engine switch.
        vm.launchModelSelection(pathA, ProviderType.LITE_RT_LM, null)
        runCurrent()
        assertThat(localSelectionFlow.value.isLoading).isTrue()

        // New chat before the mount finishes: selection cleared.
        vm.newConversation()
        runCurrent()
        assertThat(localSelectionFlow.value.modelId).isNull()

        // The stale mount now completes — it must connect nothing and
        // report nothing on the fresh chat.
        releaseMount.countDown()
        advanceUntilIdle()

        verify(exactly = 0) { activeModelSelection.connectLocal(any(), any(), any()) }
        assertThat(vm.connectionState.value.modelLoadError).isNull()
        assertThat(vm.connectionState.value.selectedLocalModelId).isNull()
    }
}
