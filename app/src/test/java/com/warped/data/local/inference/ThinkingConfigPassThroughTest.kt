package com.warped.data.local.inference

import android.content.Context
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.google.common.truth.Truth.assertThat
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.repository.ModelAllowlistRepository
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.MultiUrlFetcher
import com.warped.data.grounding.WebPageFetcher
import com.warped.domain.model.ActiveModelSelection
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Quick-task (thinking-config): capable-model thinking toggle reaches
 * engine conversation creation as ThinkingConfig.
 *
 * The toggle+capability AND lives upstream (`ChatViewModel` passes
 * `enableThinking = toggle && supportsThinking` into `runInference`,
 * which copies it to `parameters.reasoningEnabled`) — the provider
 * threads the already-gated flag, it never re-gates. These tests pin:
 * gated-on ⇒ non-null ThinkingConfig forwarded to the engine;
 * gated-off ⇒ null (engine defaults); a thinking flip changes the
 * conversation snapshot so the channel can't linger across states.
 */
class ThinkingConfigPassThroughTest {

    private fun provider() = LiteRTLmProvider(
        engineManager = mockk(),
        inputSanitizer = mockk(),
        activeModelSelection = mockk<ActiveModelSelection>(),
        ddg = mockk<DuckDuckGoSearchRepository>(),
        multiUrlFetcher = mockk<MultiUrlFetcher>(),
        webPageFetcher = mockk<WebPageFetcher>(),
        allowlist = mockk<ModelAllowlistRepository>(),
        advancedPreferences = mockk<AdvancedPreferences>(),
    )

    @Test
    fun `thinking on maps to enabled ThinkingConfig with the bounded budget`() {
        val config = provider().thinkingConfigFor(true)
        assertThat(config).isNotNull()
        assertThat(config!!.enableThinking).isTrue()
        assertThat(config.thinkingTokenBudget)
            .isEqualTo(LiteRTLmProvider.THINKING_TOKEN_BUDGET)
    }

    @Test
    fun `thinking off maps to null so engine defaults apply`() {
        assertThat(provider().thinkingConfigFor(false)).isNull()
    }

    @Test
    fun `engine manager forwards thinkingConfig to engine conversation creation`() {
        val engine = mockk<LiteRTLmEngine>()
        val manager = EngineManager(
            liteRTLmEngine = engine,
            backendDetector = mockk(),
            context = mockk<Context>(),
            cacheManager = mockk(),
            allowlist = mockk(),
        )
        val config = ConversationConfig()
        val thinking = ThinkingConfig(true, LiteRTLmProvider.THINKING_TOKEN_BUDGET)
        every { engine.createConversation(any(), any(), any()) } returns mockk(relaxed = true)

        manager.createLiteRTConversation(config, thinking)

        verify { engine.createConversation(config, thinking, null) }
    }

    @Test
    fun `engine manager defaults to null thinkingConfig preserving legacy behavior`() {
        val engine = mockk<LiteRTLmEngine>()
        val manager = EngineManager(
            liteRTLmEngine = engine,
            backendDetector = mockk(),
            context = mockk<Context>(),
            cacheManager = mockk(),
            allowlist = mockk(),
        )
        val config = ConversationConfig()
        every { engine.createConversation(any(), any(), any()) } returns mockk(relaxed = true)

        manager.createLiteRTConversation(config)

        verify { engine.createConversation(config, null, null) }
    }

    @Test
    fun `thinking flip changes the arming snapshot so the conversation rebuilds`() = runTest {
        val advancedPreferences = mockk<AdvancedPreferences>()
        val engineManager = mockk<EngineManager>()
        val allowlist = mockk<ModelAllowlistRepository>()
        val webPageFetcher = mockk<WebPageFetcher>()
        every { advancedPreferences.webGroundingEnabled } returns flowOf(false)
        every { engineManager.getActiveEngine() } returns null
        every { webPageFetcher.hasValidatedInternet() } returns false
        val p = LiteRTLmProvider(
            engineManager = engineManager,
            inputSanitizer = mockk(),
            activeModelSelection = mockk<ActiveModelSelection>(),
            ddg = mockk<DuckDuckGoSearchRepository>(),
            multiUrlFetcher = mockk<MultiUrlFetcher>(),
            webPageFetcher = webPageFetcher,
            allowlist = allowlist,
            advancedPreferences = advancedPreferences,
        )

        val off = p.computeArmSnapshot(perChat = null, thinking = false)
        val on = p.computeArmSnapshot(perChat = null, thinking = true)

        assertThat(off.thinking).isFalse()
        assertThat(on.thinking).isTrue()
        assertThat(off).isNotEqualTo(on)
    }
}
