package com.warped.ui.settings

import com.google.common.truth.Truth.assertThat
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.local.security.ApiKeyStore
import com.warped.domain.model.SyntaxTheme
import com.warped.domain.repository.ChatRepository
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.LocalModelRepository
import com.warped.domain.repository.PresetRepository
import io.mockk.coEvery
import io.mockk.coVerify
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

/**
 * Phase 50 (WEB-06): grounding toggle write-through verification.
 *
 * The Settings toggle must write through to AdvancedPreferences; the chat
 * path reads it on the next sent message (no restart, no retroactive
 * re-grounding).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsGroundingToggleTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        advancedPreferences: AdvancedPreferences,
    ): SettingsViewModel {
        val chatRepository = mockk<ChatRepository>()
        val endpointRepository = mockk<EndpointRepository>()
        val localModelRepository = mockk<LocalModelRepository>()
        val presetRepository = mockk<PresetRepository>()
        val apiKeyStore = mockk<ApiKeyStore>()

        every { chatRepository.observeConversations() } returns flowOf(emptyList())
        every { endpointRepository.observeEndpoints() } returns flowOf(emptyList())
        every { localModelRepository.observeModels() } returns flowOf(emptyList())
        every { presetRepository.observePresets() } returns flowOf(emptyList())
        every { advancedPreferences.syntaxTheme } returns flowOf(SyntaxTheme.MONOKAI)
        every { advancedPreferences.codeFontScale } returns flowOf(1.0f)

        return SettingsViewModel(
            chatRepository = chatRepository,
            endpointRepository = endpointRepository,
            localModelRepository = localModelRepository,
            presetRepository = presetRepository,
            apiKeyStore = apiKeyStore,
            advancedPreferences = advancedPreferences,
        )
    }

    @Test
    fun `toggle writes through to AdvancedPreferences`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val groundingState = MutableStateFlow(true)
        val advancedPreferences = mockk<AdvancedPreferences>()
        every { advancedPreferences.webGroundingEnabled } returns groundingState
        coEvery { advancedPreferences.setWebGroundingEnabled(any()) } just Runs
        val vm = buildViewModel(advancedPreferences)
        runCurrent()

        assertThat(vm.uiState.value.webGroundingEnabled).isTrue()

        vm.setWebGroundingEnabled(false)
        // Simulate the DataStore emission the real setter would produce.
        groundingState.value = false
        advanceUntilIdle()

        coVerify(exactly = 1) { advancedPreferences.setWebGroundingEnabled(false) }
        assertThat(vm.uiState.value.webGroundingEnabled).isFalse()
    }

    @Test
    fun `toggle defaults ON`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val advancedPreferences = mockk<AdvancedPreferences>()
        every { advancedPreferences.webGroundingEnabled } returns flowOf(true)

        val vm = buildViewModel(advancedPreferences)
        runCurrent()

        assertThat(vm.uiState.value.webGroundingEnabled).isTrue()
    }
}
