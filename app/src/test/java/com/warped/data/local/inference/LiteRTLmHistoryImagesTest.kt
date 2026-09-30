package com.warped.data.local.inference

import android.util.Base64
import com.google.ai.edge.litertlm.Content
import com.google.common.truth.Truth.assertThat
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.MultiUrlFetcher
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.repository.ModelAllowlistRepository
import com.warped.domain.model.ActiveModelSelection
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.Role
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Quick-task (image-history-carry): history-mapping invariants for
 * [LiteRTLmProvider.buildHistoryMessages].
 *
 * The builder is a pure function over sanitized [ChatMessage]s (current
 * message last), so these tests assert on the built litertlm [Message]
 * contents directly — no native engine needed. android.util.Base64 is a
 * JVM stub, so Base64.decode is static-mocked to echo the payload bytes.
 */
class LiteRTLmHistoryImagesTest {

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

    private fun user(text: String, images: List<String> = emptyList()) =
        ChatMessage(role = Role.USER, content = text, imageUris = images)

    private fun assistant(text: String) =
        ChatMessage(role = Role.ASSISTANT, content = text)

    private fun img(tag: String) = "data:image/png;base64,$tag-payload"

    private fun imageBytesOf(message: com.google.ai.edge.litertlm.Message) =
        message.contents.contents.filterIsInstance<Content.ImageBytes>()

    private fun textPartsOf(message: com.google.ai.edge.litertlm.Message) =
        message.contents.contents.filterIsInstance<Content.Text>()

    @BeforeEach
    fun stubBase64() {
        mockkStatic(Base64::class)
        // Echo distinct bytes per payload so different URLs decode
        // differently and identical URLs decode identically.
        every { Base64.decode(any<String>(), any()) } answers {
            (firstArg<String>()).toByteArray()
        }
    }

    @AfterEach
    fun unmockBase64() {
        unmockkStatic(Base64::class)
    }

    @Test
    fun `history USER image is carried as ImageBytes plus the text part`() {
        val history = provider().buildHistoryMessages(
            listOf(
                user("what is this?", images = listOf(img("A"))),
                user("follow-up"),
            )
        )

        assertThat(history).hasSize(1)
        val carried = history.single()
        assertThat(imageBytesOf(carried)).hasSize(1)
        assertThat(imageBytesOf(carried).single().bytes)
            .isEqualTo("A-payload".toByteArray())
        assertThat(textPartsOf(carried).map { it.text })
            .containsExactly("what is this?")
    }

    @Test
    fun `K cap keeps only the newest history images`() {
        val messages = (1..5).map { i ->
            user("turn $i", images = listOf(img("img$i")))
        } + user("current")
        val history = provider().buildHistoryMessages(messages)

        assertThat(history).hasSize(5)
        // Oldest two image turns drop out of the carried set (K=3) and
        // keep the pre-fix text-only shape.
        assertThat(imageBytesOf(history[0])).isEmpty()
        assertThat(imageBytesOf(history[1])).isEmpty()
        // Newest three carry their image.
        assertThat(imageBytesOf(history[2]).map { String(it.bytes) })
            .containsExactly("img3-payload")
        assertThat(imageBytesOf(history[3]).map { String(it.bytes) })
            .containsExactly("img4-payload")
        assertThat(imageBytesOf(history[4]).map { String(it.bytes) })
            .containsExactly("img5-payload")
        assertThat(LiteRTLmProvider.HISTORY_IMAGE_CARRY_MAX).isEqualTo(3)
    }

    @Test
    fun `malformed history image is skipped - text preserved - no exception`() {
        every { Base64.decode(any<String>(), any()) } throws IllegalArgumentException("bad base64")
        val history = provider().buildHistoryMessages(
            listOf(
                user("describe this", images = listOf("data:image/png;base64,!!!")),
                user("follow-up"),
            )
        )

        assertThat(history).hasSize(1)
        assertThat(imageBytesOf(history.single())).isEmpty()
        assertThat(textPartsOf(history.single()).map { it.text })
            .containsExactly("describe this")
    }

    @Test
    fun `same data URL on two history turns is carried once on the newest`() {
        val shared = img("shared")
        val history = provider().buildHistoryMessages(
            listOf(
                user("first look", images = listOf(shared)),
                user("second look", images = listOf(shared)),
                user("follow-up"),
            )
        )

        assertThat(history).hasSize(2)
        // Oldest occurrence dedupes away → text-only shape.
        assertThat(imageBytesOf(history[0])).isEmpty()
        // Newest occurrence carries the single copy plus its text.
        assertThat(imageBytesOf(history[1])).hasSize(1)
        assertThat(textPartsOf(history[1]).map { it.text })
            .containsExactly("second look")
    }

    @Test
    fun `text-only history keeps the pre-fix single-Text shape`() {
        val history = provider().buildHistoryMessages(
            listOf(
                user("hello"),
                assistant("hi there"),
                user("how are you?"),
            )
        )

        assertThat(history).hasSize(2)
        history.forEach { message ->
            assertThat(message.contents.contents).hasSize(1)
        }
        assertThat(textPartsOf(history[0]).single().text).isEqualTo("hello")
        assertThat(textPartsOf(history[1]).single().text).isEqualTo("hi there")
    }

    @Test
    fun `current message images are never part of the carried set`() {
        val historyImage = img("history")
        val currentImage = img("current")
        val history = provider().buildHistoryMessages(
            listOf(
                user("earlier", images = listOf(historyImage)),
                assistant("ok"),
                // Current turn: excluded via dropLast(1) even though it
                // carries imageUris (Step 4 sends them, never the builder).
                user("now this", images = listOf(currentImage)),
            )
        )

        assertThat(history).hasSize(2)
        val allCarried = history.flatMap { imageBytesOf(it) }
        assertThat(allCarried.map { String(it.bytes) })
            .containsExactly("history-payload")
    }
}
