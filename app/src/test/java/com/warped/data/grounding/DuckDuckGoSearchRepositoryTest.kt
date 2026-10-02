package com.warped.data.grounding

import com.google.common.truth.Truth.assertThat
import com.warped.domain.model.GroundedSourceStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * DDG-only search exit gates: `uddg` unwrap + http(s) validation,
 * snippet tolerance, fuse parity (OK/OMITIDA/all-blank), and the
 * DDG-only policy matrix (keyless: empty/throw collapse to model-only).
 *
 * No network, no key, no Android: the HTML layer is faked via
 * [DuckDuckGoSearchRepository.htmlSupplier], the connectivity gate is a
 * MockK fake, and the repository runs on Unconfined for determinism.
 */
class DuckDuckGoSearchRepositoryTest {

    private lateinit var webPageFetcher: WebPageFetcher
    private lateinit var repository: DuckDuckGoSearchRepository

    @BeforeEach
    fun setUp() {
        webPageFetcher = mockk()
        every { webPageFetcher.hasValidatedInternet() } returns true
        // Real client instance (never used — every test sets htmlSupplier
        // or asserts the no-socket path, so no socket ever opens).
        // The enricher seam returns null for every URL (enrichment no-op):
        // every search() test runs with headSupplier set, so unit tests
        // never open real sockets.
        val enricher = SearchOgEnricher(OkHttpClient()).apply {
            headSupplier = { null }
            ioDispatcher = Dispatchers.Unconfined
        }
        repository = DuckDuckGoSearchRepository(
            OkHttpClient(),
            webPageFetcher,
            enricher,
        )
        repository.ioDispatcher = Dispatchers.Unconfined
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private fun fixtureHtml(): String = """
        <html><body>
        <div class="result results_links results_links_deep web-result">
          <div class="links_main links_deep result__body">
            <h2 class="result__title">
              <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fkotlin&amp;rut=abc123">Kotlin News</a>
            </h2>
            <a class="result__snippet" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fkotlin">Kotlin 2.1 released with details.</a>
          </div>
        </div>
        <div class="result results_links web-result">
          <h2 class="result__title">
            <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=http%3A%2F%2Fexample.org%2Fandroid">Android Update</a>
          </h2>
        </div>
        <div class="result results_links web-result">
          <h2 class="result__title">
            <a rel="nofollow" class="result__a" href="https://bare.example/direct">Bare Link</a>
          </h2>
          <a class="result__snippet" href="https://bare.example/direct">Direct snippet text here.</a>
        </div>
        <div class="result results_links web-result">
          <h2 class="result__title">
            <a rel="nofollow" class="result__a" href="javascript:alert(1)">Bad Scheme</a>
          </h2>
        </div>
        </body></html>
    """.trimIndent()

    private fun manyResultsHtml(n: Int): String = buildString {
        append("<html><body>")
        repeat(n) { i ->
            append(
                """<div class="result"><h2 class="result__title">""" +
                    """<a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fp$i">Title $i</a>""" +
                    """</h2><a class="result__snippet">Snippet $i with details.</a></div>""",
            )
        }
        append("</body></html>")
    }

    // ------------------------------------------------------------------
    // Parser: uddg unwrap + scheme validation
    // ------------------------------------------------------------------

    @Test
    fun `parser unwraps uddg wrappers and drops non-http schemes`() {
        val results = repository.parseResults(fixtureHtml(), maxResults = 5)

        assertThat(results.map { it.url }).containsExactly(
            "https://example.com/kotlin",
            "http://example.org/android",
            "https://bare.example/direct",
        ).inOrder()
        assertThat(results.map { it.url }.joinToString()).doesNotContain("duckduckgo.com/l/")
    }

    @Test
    fun `parser reads titles and tolerates a missing snippet`() {
        val results = repository.parseResults(fixtureHtml(), maxResults = 5)

        assertThat(results[0].title).isEqualTo("Kotlin News")
        assertThat(results[0].snippet).isEqualTo("Kotlin 2.1 released with details.")
        assertThat(results[1].title).isEqualTo("Android Update")
        assertThat(results[1].snippet).isEmpty()
        assertThat(results[2].title).isEqualTo("Bare Link")
        assertThat(results[2].snippet).isEqualTo("Direct snippet text here.")
    }

    @Test
    fun `resolveResultUrl unwraps wrapper variants and gates schemes`() {
        assertThat(
            repository.resolveResultUrl("//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fa&rut=x"),
        ).isEqualTo("https://example.com/a")
        assertThat(
            repository.resolveResultUrl("https://duckduckgo.com/l/?uddg=http%3A%2F%2Fexample.com%2Fb"),
        ).isEqualTo("http://example.com/b")
        assertThat(
            repository.resolveResultUrl("https://bare.example/direct"),
        ).isEqualTo("https://bare.example/direct")
        assertThat(repository.resolveResultUrl("javascript:alert(1)")).isNull()
        assertThat(repository.resolveResultUrl("data:text/plain,hi")).isNull()
        assertThat(repository.resolveResultUrl("//duckduckgo.com/l/?rut=no-uddg")).isNull()
        assertThat(repository.resolveResultUrl("//duckduckgo.com/l/?uddg=ftp%3A%2F%2Fexample.com%2Ff")).isNull()
        assertThat(repository.resolveResultUrl("")).isNull()
        assertThat(repository.resolveResultUrl("/relative/path")).isNull()
    }

    @Test
    fun `parser caps at ten results`() {
        val results = repository.parseResults(manyResultsHtml(12), maxResults = 99)

        assertThat(results).hasSize(10)
        assertThat(results.first().url).isEqualTo("https://example.com/p0")
        assertThat(results.last().url).isEqualTo("https://example.com/p9")
    }

    @Test
    fun `parser returns empty on blank html`() {
        assertThat(repository.parseResults("   ", maxResults = 5)).isEmpty()
    }

    // ------------------------------------------------------------------
    // Policy matrix: DDG-only (keyless)
    // ------------------------------------------------------------------

    @Test
    fun `ddg-ok grounds silently`() = runTest {
        repository.htmlSupplier = { fixtureHtml() }

        val outcome = repository.search("kotlin news")

        assertThat(outcome).isInstanceOf(SearchOutcome.Grounded::class.java)
        val fused = (outcome as SearchOutcome.Grounded).fused
        assertThat(fused.okUrls).containsExactly(
            "https://example.com/kotlin",
            "https://bare.example/direct",
        ).inOrder()
        assertThat(fused.skippedUrls).containsExactly("http://example.org/android")
        assertThat(fused.block).contains("--- Source [1]: https://example.com/kotlin ---")
        assertThat(fused.block).doesNotContain("example.org/android")
    }

    // ------------------------------------------------------------------
    // Title threading (quick-task): fuse() OK rows carry the anchor title.
    // ------------------------------------------------------------------

    @Test
    fun `ok rows carry the search-result title, omitida rows stay null`() = runTest {
        repository.htmlSupplier = { fixtureHtml() }

        val outcome = repository.search("kotlin news")

        assertThat(outcome).isInstanceOf(SearchOutcome.Grounded::class.java)
        val fused = (outcome as SearchOutcome.Grounded).fused
        assertThat(fused.details).hasSize(3)
        assertThat(fused.details[0].ogTitle).isEqualTo("Kotlin News")
        assertThat(fused.details[1].status).isEqualTo(GroundedSourceStatus.OMITIDA)
        assertThat(fused.details[1].ogTitle).isNull()
        assertThat(fused.details[2].ogTitle).isEqualTo("Bare Link")
        // Fused prompt text construction byte-identical to before.
        assertThat(fused.block).contains("--- Source [1]: https://example.com/kotlin ---")
    }

    @Test
    fun `blank anchor title threads null ogTitle`() = runTest {
        repository.htmlSupplier = {
            """
            <html><body>
            <div class="result"><h2 class="result__title">
              <a class="result__a" href="https://blank.example/title">   </a>
            </h2><a class="result__snippet">Snippet with details.</a></div>
            </body></html>
            """.trimIndent()
        }

        val outcome = repository.search("blank title")

        assertThat(outcome).isInstanceOf(SearchOutcome.Grounded::class.java)
        val fused = (outcome as SearchOutcome.Grounded).fused
        assertThat(fused.details).hasSize(1)
        assertThat(fused.details[0].ogTitle).isNull()
    }

    @Test
    fun `ddg-served turn fuses zero images - no image api on the ddg leg`() = runTest {
        repository.htmlSupplier = { fixtureHtml() }

        val outcome = repository.search("show me pictures of cats", includeImages = true)

        assertThat(outcome).isInstanceOf(SearchOutcome.Grounded::class.java)
        val fused = (outcome as SearchOutcome.Grounded).fused
        assertThat(fused.images).isEmpty()
    }

    @Test
    fun `ddg-empty collapses to fetch-failed`() = runTest {
        repository.htmlSupplier = { "<html><body>No results.</body></html>" }

        val outcome = repository.search("kotlin news")

        assertThat(outcome).isEqualTo(
            SearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
            ),
        )
    }

    @Test
    fun `ddg-throw collapses to fetch-failed`() = runTest {
        repository.htmlSupplier = { throw IOException("socket reset") }

        val outcome = repository.search("kotlin news")

        assertThat(outcome).isEqualTo(
            SearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
            ),
        )
    }

    @Test
    fun `all-blank snippets collapse to fetch-failed model-only`() = runTest {
        repository.htmlSupplier = {
            """
            <html><body>
            <div class="result"><h2 class="result__title">
            <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fnosnippet">No Snippet</a>
            </h2></div>
            </body></html>
            """.trimIndent()
        }

        val outcome = repository.search("kotlin news")

        assertThat(outcome).isEqualTo(
            SearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
            ),
        )
    }

    // ------------------------------------------------------------------
    // Gates: offline, blank query, cancellation, caps
    // ------------------------------------------------------------------

    @Test
    fun `offline short-circuits without opening a socket`() = runTest {
        every { webPageFetcher.hasValidatedInternet() } returns false
        var socketOpened = false
        repository.htmlSupplier = { socketOpened = true; fixtureHtml() }

        val outcome = repository.search("kotlin news")

        assertThat(outcome).isEqualTo(
            SearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.OFFLINE),
            ),
        )
        assertThat(socketOpened).isFalse()
    }

    @Test
    fun `blank query collapses without opening a socket`() = runTest {
        var socketOpened = false
        repository.htmlSupplier = { socketOpened = true; fixtureHtml() }

        val outcome = repository.search("   ")

        assertThat(outcome).isEqualTo(
            SearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
            ),
        )
        assertThat(socketOpened).isFalse()
    }

    @Test
    fun `cancellation propagates instead of degrading to model-only`() = runTest {
        repository.htmlSupplier = { throw CancellationException("stop") }

        var thrown: CancellationException? = null
        try {
            repository.search("kotlin news")
        } catch (e: CancellationException) {
            thrown = e
        }
        assertThat(thrown).isNotNull()
    }

    @Test
    fun `query over five hundred chars is truncated pass-through`() = runTest {
        var captured = ""
        repository.htmlSupplier = { encoded ->
            captured = encoded
            fixtureHtml()
        }

        repository.search("x".repeat(600))

        assertThat(captured).hasLength(500)
    }

    // ------------------------------------------------------------------
    // Image-turn routing: DDG text grounds the turn, grid stays empty
    // ------------------------------------------------------------------

    @Test
    fun `image-intent grounds via ddg text with empty images`() = runTest {
        var ddgLegRan = false
        repository.htmlSupplier = { ddgLegRan = true; fixtureHtml() }

        val outcome = repository.search("show me pictures of cats", includeImages = true)

        // DDG text grounds the turn; the grid stays empty (no image API on
        // the DDG leg).
        assertThat(outcome).isInstanceOf(SearchOutcome.Grounded::class.java)
        val fused = (outcome as SearchOutcome.Grounded).fused
        assertThat(fused.okUrls).isNotEmpty()
        assertThat(fused.images).isEmpty()
        assertThat(ddgLegRan).isTrue()
    }

    @Test
    fun `image-intent and ddg-empty collapses to fetch-failed`() = runTest {
        repository.htmlSupplier = { "<html><body>No results.</body></html>" }

        val outcome = repository.search("show me pictures of cats", includeImages = true)

        assertThat(outcome).isEqualTo(
            SearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
            ),
        )
    }
}
