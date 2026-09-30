package com.warped.data.grounding

import com.google.common.truth.Truth.assertThat
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.GroundedSourceStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Quick-task (search OG enrichment) exit gates for [SearchOgEnricher].
 *
 * No network: every test sets [SearchOgEnricher.headSupplier] (canned
 * HTML or scripted failure), so no socket ever opens. The dispatcher is
 * the test-scoped Unconfined dispatcher, so timeout cases run on virtual
 * time with zero real waiting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchOgEnricherTest {

    private lateinit var enricher: SearchOgEnricher

    @BeforeEach
    fun setUp() {
        // Real client instance (never used — every test sets headSupplier,
        // so no socket ever opens).
        enricher = SearchOgEnricher(OkHttpClient())
    }

    private fun okSource(
        url: String,
        title: String? = null,
        text: String = "Page text with details.",
    ) = GroundedSource(
        url = url,
        extractedText = text,
        status = GroundedSourceStatus.OK,
        ogTitle = title,
    )

    private fun ogHtml(
        title: String = "Scraped Title",
        image: String = "https://img.example/pic.png",
        description: String = "Scraped description",
    ) = """
        <html><head>
        <meta property="og:title" content="$title">
        <meta property="og:image" content="$image">
        <meta property="og:description" content="$description">
        </head><body>Body</body></html>
    """.trimIndent()

    @Test
    fun `success sets og fields and scraped title wins over threaded`() = runTest {
        enricher.ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        enricher.headSupplier = { ogHtml() to "text/html; charset=utf-8" }

        val out = enricher.enrich(listOf(okSource("https://example.com/a", title = "Threaded")))

        assertThat(out).hasSize(1)
        assertThat(out[0].ogTitle).isEqualTo("Scraped Title")
        assertThat(out[0].ogImageUrl).isEqualTo("https://img.example/pic.png")
        assertThat(out[0].ogDescription).isEqualTo("Scraped description")
        // Original row fields survive the merge.
        assertThat(out[0].url).isEqualTo("https://example.com/a")
        assertThat(out[0].extractedText).isEqualTo("Page text with details.")
        assertThat(out[0].status).isEqualTo(GroundedSourceStatus.OK)
    }

    @Test
    fun `missing content-type is treated as html`() = runTest {
        enricher.ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        enricher.headSupplier = { ogHtml(title = "No Type Title") to null }

        val out = enricher.enrich(listOf(okSource("https://example.com/a", title = "Threaded")))

        assertThat(out[0].ogTitle).isEqualTo("No Type Title")
    }

    @Test
    fun `partial failure keeps the threaded title on the failed row only`() = runTest {
        enricher.ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        enricher.headSupplier = { url ->
            if (url.contains("/fail")) throw java.io.IOException("boom")
            ogHtml(title = "Scraped for $url") to "text/html"
        }

        val out = enricher.enrich(
            listOf(
                okSource("https://example.com/ok1", title = "Threaded 1"),
                okSource("https://example.com/fail", title = "Threaded fail"),
                okSource("https://example.com/ok2", title = "Threaded 2"),
            ),
        )

        // Full-size and order-preserving; the turn still grounds.
        assertThat(out.map { it.url }).containsExactly(
            "https://example.com/ok1",
            "https://example.com/fail",
            "https://example.com/ok2",
        ).inOrder()
        assertThat(out[0].ogTitle).isEqualTo("Scraped for https://example.com/ok1")
        assertThat(out[1].ogTitle).isEqualTo("Threaded fail")
        assertThat(out[1].ogImageUrl).isNull()
        assertThat(out[2].ogTitle).isEqualTo("Scraped for https://example.com/ok2")
    }

    @Test
    fun `blank scraped page keeps the threaded title`() = runTest {
        enricher.ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        enricher.headSupplier = { "   " to "text/html" }

        val out = enricher.enrich(listOf(okSource("https://example.com/a", title = "Threaded")))

        assertThat(out[0].ogTitle).isEqualTo("Threaded")
    }

    @Test
    fun `timeout returns all rows original without throwing`() = runTest {
        enricher.ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        enricher.totalTimeoutMs = 200
        enricher.headSupplier = { url ->
            delay(5000)
            ogHtml() to "text/html"
        }
        val originals = listOf(
            okSource("https://example.com/a", title = "Threaded A"),
            okSource("https://example.com/b", title = "Threaded B"),
        )

        val out = enricher.enrich(originals)

        assertThat(out).isEqualTo(originals)
    }

    @Test
    fun `non-html content-type skips the row`() = runTest {
        enricher.ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        enricher.headSupplier = { ogHtml() to "application/pdf" }

        val out = enricher.enrich(listOf(okSource("https://example.com/a.pdf", title = "Threaded")))

        assertThat(out[0].ogTitle).isEqualTo("Threaded")
        assertThat(out[0].ogImageUrl).isNull()
        assertThat(out[0].ogDescription).isNull()
    }

    @Test
    fun `null supplier result skips the row`() = runTest {
        enricher.ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        enricher.headSupplier = { null }

        val out = enricher.enrich(listOf(okSource("https://example.com/a", title = "Threaded")))

        assertThat(out[0].ogTitle).isEqualTo("Threaded")
    }

    @Test
    fun `non-http url passes through without calling the supplier`() = runTest {
        enricher.ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        var calls = 0
        enricher.headSupplier = { url ->
            calls++
            ogHtml() to "text/html"
        }
        val original = okSource("ftp://example.com/file", title = "Threaded")

        val out = enricher.enrich(listOf(original))

        assertThat(out).containsExactly(original)
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun `omitida rows pass through untouched`() = runTest {
        enricher.ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        var calls = 0
        enricher.headSupplier = { url ->
            calls++
            ogHtml() to "text/html"
        }
        val omitida = GroundedSource(
            url = "https://example.com/skipped",
            extractedText = null,
            status = GroundedSourceStatus.OMITIDA,
        )

        val out = enricher.enrich(listOf(omitida))

        assertThat(out).containsExactly(omitida)
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun `cancellation propagates instead of keeping the row`() = runTest {
        enricher.ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        enricher.headSupplier = { throw CancellationException("stop") }

        var thrown: CancellationException? = null
        try {
            enricher.enrich(listOf(okSource("https://example.com/a", title = "Threaded")))
        } catch (e: CancellationException) {
            thrown = e
        }
        assertThat(thrown).isNotNull()
    }

    @Test
    fun `more than five ok urls enriches only the first five`() = runTest {        enricher.ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        val called = mutableListOf<String>()
        enricher.headSupplier = { url ->
            called.add(url)
            ogHtml(title = "Scraped $url") to "text/html"
        }
        val sources = (0..6).map { okSource("https://example.com/p$it", title = "Threaded $it") }

        val out = enricher.enrich(sources)

        assertThat(out).hasSize(7)
        assertThat(called).containsExactly(
            "https://example.com/p0",
            "https://example.com/p1",
            "https://example.com/p2",
            "https://example.com/p3",
            "https://example.com/p4",
        ).inOrder()
        assertThat(out[4].ogTitle).isEqualTo("Scraped https://example.com/p4")
        // Rows past the cap keep their threaded titles.
        assertThat(out[5].ogTitle).isEqualTo("Threaded 5")
        assertThat(out[6].ogTitle).isEqualTo("Threaded 6")
    }

    // ── Quick-task (YouTube oEmbed fallback) ──────────────────
    // No sockets: headSupplier returns blank HTML (no OG captured) and
    // oembedSupplier returns canned JSON, so neither path opens a socket.

    private val youtubeUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

    private fun oembedJson(
        title: String = "Never Gonna Give You Up",
        thumb: String = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
    ) = """{"title":"$title","author_name":"Rick Astley","thumbnail_url":"$thumb"}"""

    @Test
    fun `youtube url with null og gets title and thumb from oembed`() = runTest {
        enricher.ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        enricher.headSupplier = { "   " to "text/html" }
        enricher.oembedSupplier = { oembedJson() }

        val out = enricher.enrich(listOf(okSource(youtubeUrl)))

        assertThat(out).hasSize(1)
        assertThat(out[0].ogTitle).isEqualTo("Never Gonna Give You Up")
        assertThat(out[0].ogImageUrl).isEqualTo("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg")
        assertThat(out[0].url).isEqualTo(youtubeUrl)
    }

    @Test
    fun `oembed transport failure keeps the pre-oembed row`() = runTest {
        enricher.ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        enricher.headSupplier = { "   " to "text/html" }
        enricher.oembedSupplier = { throw java.io.IOException("oembed down") }

        val out = enricher.enrich(listOf(okSource(youtubeUrl)))

        assertThat(out[0].ogTitle).isNull()
        assertThat(out[0].ogImageUrl).isNull()
        assertThat(out[0].status).isEqualTo(GroundedSourceStatus.OK)
    }

    @Test
    fun `oembed malformed json keeps the pre-oembed row`() = runTest {
        enricher.ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        enricher.headSupplier = { "   " to "text/html" }
        enricher.oembedSupplier = { "not json{" }

        val out = enricher.enrich(listOf(okSource(youtubeUrl)))

        assertThat(out[0].ogTitle).isNull()
        assertThat(out[0].ogImageUrl).isNull()
    }

    @Test
    fun `non-youtube url never calls the oembed path`() = runTest {
        enricher.ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        enricher.headSupplier = { "   " to "text/html" }
        var oembedCalls = 0
        enricher.oembedSupplier = {
            oembedCalls++
            oembedJson()
        }

        val out = enricher.enrich(listOf(okSource("https://example.com/a")))

        assertThat(oembedCalls).isEqualTo(0)
        assertThat(out[0].ogTitle).isNull()
    }

    @Test
    fun `youtube url with present og never calls the oembed path`() = runTest {
        enricher.ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        enricher.headSupplier = { ogHtml() to "text/html" }
        var oembedCalls = 0
        enricher.oembedSupplier = {
            oembedCalls++
            oembedJson()
        }

        val out = enricher.enrich(listOf(okSource(youtubeUrl)))

        assertThat(oembedCalls).isEqualTo(0)
        assertThat(out[0].ogTitle).isEqualTo("Scraped Title")
        assertThat(out[0].ogImageUrl).isEqualTo("https://img.example/pic.png")
    }

    @Test
    fun `non-http thumbnail from oembed is gated to null while title maps`() = runTest {
        enricher.ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        enricher.headSupplier = { "   " to "text/html" }
        enricher.oembedSupplier = { oembedJson(thumb = "data:image/png;base64,AAA") }

        val out = enricher.enrich(listOf(okSource(youtubeUrl)))

        assertThat(out[0].ogTitle).isEqualTo("Never Gonna Give You Up")
        assertThat(out[0].ogImageUrl).isNull()
    }

    @Test
    fun `author-name-only oembed json leaves the row unchanged`() = runTest {
        enricher.ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        enricher.headSupplier = { "   " to "text/html" }
        enricher.oembedSupplier = { """{"author_name":"Rick Astley"}""" }

        val out = enricher.enrich(listOf(okSource(youtubeUrl)))

        // author_name must never land in any column.
        assertThat(out[0].ogTitle).isNull()
        assertThat(out[0].ogImageUrl).isNull()
        assertThat(out[0].ogDescription).isNull()
    }

    @Test
    fun `oembed cancellation propagates instead of keeping the row`() = runTest {
        enricher.ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        enricher.headSupplier = { "   " to "text/html" }
        enricher.oembedSupplier = { throw CancellationException("stop") }

        var thrown: CancellationException? = null
        try {
            enricher.enrich(listOf(okSource(youtubeUrl)))
        } catch (e: CancellationException) {
            thrown = e
        }
        assertThat(thrown).isNotNull()
    }
}
