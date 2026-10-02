package com.warped.ui.models

import com.google.common.truth.Truth.assertThat
import com.warped.data.local.download.ModelDownloadManager
import com.warped.domain.model.ActiveModelSelection
import com.warped.domain.model.Endpoint
import com.warped.domain.model.LocalModel
import com.warped.domain.model.LocalSelection
import com.warped.domain.model.ProviderType
import com.warped.domain.repository.ChatRepository
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.LocalModelRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Quick-task (activation-new-chat): toggling a model ON in Models &
 * Endpoints opens a NEW chat bound to the activated model. The existing
 * connect/activate logic is kept; a fresh conversation row carrying the
 * activated binding is appended. Old conversations are untouched (no
 * delete, no rebind, no message move).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelActivationNewChatTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun model() = LocalModel(
        id = 3,
        name = "test",
        filePath = "/models/test.litertlm",
        sizeBytes = 10,
        quantization = "Q4",
        parameterCount = "1B",
        architecture = "Test",
        importedAt = Instant.EPOCH,
    )

    private fun endpoint() = Endpoint(
        id = 7,
        name = "local-ollama",
        url = "http://localhost:11434/",
        apiType = ProviderType.OLLAMA,
        modelId = "qwen3:8b",
    )

    private class Fixture {
        val chatRepository = mockk<ChatRepository>()
        val activeSelection = mockk<ActiveModelSelection>(relaxed = true)
        val endpointRepository = mockk<EndpointRepository>(relaxed = true)
        val providerRouter = mockk<com.warped.data.remote.provider.ProviderRouter>(relaxed = true)
        lateinit var viewModel: ModelsViewModel

        init {
            every { activeSelection.localSelection } returns MutableStateFlow(LocalSelection())
            coEvery {
                chatRepository.createConversation(any(), any(), any(), any())
            } returns 99L
        }

        fun build(): ModelsViewModel {
            val localRepo = mockk<LocalModelRepository>(relaxed = true)
            every { localRepo.observeModels() } returns MutableStateFlow(emptyList())
            every { endpointRepository.observeEndpoints() } returns MutableStateFlow(emptyList())
            viewModel = ModelsViewModel(
                localModelRepository = localRepo,
                endpointRepository = endpointRepository,
                activeModelSelection = activeSelection,
                modelImportManager = mockk(relaxed = true),
                modelDownloadManager = mockk<ModelDownloadManager>(relaxed = true).also {
                    every { it.downloadStates } returns MutableStateFlow(emptyMap())
                },
                memoryChecker = mockk(relaxed = true),
                apiKeyStore = mockk(relaxed = true),
                providerRouter = providerRouter,
                inputSanitizer = mockk(relaxed = true),
                allowlist = mockk(relaxed = true),
                chatRepository = chatRepository,
                context = mockk(relaxed = true),
            )
            return viewModel
        }
    }

    @Test
    fun `useLocalModel opens a new conversation bound to the local model`() =
        runTest(testDispatcher) {
            val fixture = Fixture()
            val viewModel = fixture.build()
            val title = slot<String>()
            val provider = slot<ProviderType>()
            val modelId = slot<String>()
            val endpointId = slot<Long>()

            viewModel.useLocalModel(model())
            advanceUntilIdle()

            coVerify(exactly = 1) {
                fixture.chatRepository.createConversation(
                    capture(title),
                    capture(provider),
                    capture(modelId),
                    capture(endpointId),
                )
            }
            assertThat(provider.captured).isEqualTo(ProviderType.LITE_RT_LM)
            assertThat(modelId.captured).isEqualTo("/models/test.litertlm")
            assertThat(endpointId.captured).isEqualTo(0L)
            assertThat(viewModel.pendingChatId.value).isEqualTo(99L)
            verify { fixture.activeSelection.saveLastConversation(99L) }
            // Lazy load: activation marks pending — the engine mounts on
            // the first send, never here.
            verify { fixture.activeSelection.selectLocalPending("/models/test.litertlm") }
        }

    @Test
    fun `useEndpoint opens a new conversation bound to the endpoint model`() =
        runTest(testDispatcher) {
            val fixture = Fixture()
            val providerMock = mockk<com.warped.domain.provider.LlmProvider>(relaxed = true)
            every { fixture.providerRouter.resolve(any(), any()) } returns providerMock
            coEvery { providerMock.listModels() } returns Result.success(emptyList())
            val viewModel = fixture.build()
            val provider = slot<ProviderType>()
            val modelId = slot<String>()
            val endpointId = slot<Long>()

            viewModel.useEndpoint(endpoint())
            advanceUntilIdle()

            coVerify(exactly = 1) {
                fixture.chatRepository.createConversation(
                    any(),
                    capture(provider),
                    capture(modelId),
                    capture(endpointId),
                )
            }
            assertThat(provider.captured).isEqualTo(ProviderType.OLLAMA)
            assertThat(modelId.captured).isEqualTo("qwen3:8b")
            assertThat(endpointId.captured).isEqualTo(7L)
            assertThat(viewModel.pendingChatId.value).isEqualTo(99L)
            verify { fixture.activeSelection.saveLastConversation(99L) }
            // Existing activate logic kept.
            coVerify { fixture.endpointRepository.activateEndpoint(7L) }
            verify {
                fixture.activeSelection.selectRemote("qwen3:8b", ProviderType.OLLAMA, 7L)
            }
        }

    @Test
    fun `previous conversation rows are never rebound or deleted`() =
        runTest(testDispatcher) {
            val fixture = Fixture()
            val viewModel = fixture.build()

            viewModel.useLocalModel(model())
            advanceUntilIdle()

            // Exactly one write (the new row) — no title updates, no
            // deletes, no other row touches.
            coVerify(exactly = 1) {
                fixture.chatRepository.createConversation(any(), any(), any(), any())
            }
            coVerify(exactly = 0) {
                fixture.chatRepository.updateConversationTitle(any(), any())
            }
            coVerify(exactly = 0) {
                fixture.chatRepository.deleteConversation(any())
            }
        }

    @Test
    fun `pending chat is single-shot`() = runTest(testDispatcher) {
        val fixture = Fixture()
        val viewModel = fixture.build()

        viewModel.useLocalModel(model())
        advanceUntilIdle()
        assertThat(viewModel.pendingChatId.value).isEqualTo(99L)

        viewModel.consumePendingChat()

        assertThat(viewModel.pendingChatId.value).isNull()
    }
}
