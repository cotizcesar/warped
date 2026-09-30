package com.warped.data.grounding

import com.google.common.truth.Truth.assertThat
import com.warped.data.local.security.ApiKeyStore
import com.warped.data.local.security.KeystoreManager
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.GroundedSourceStatus
import io.mockk.coEvery
import io.mockk.coVerify
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
 * DDG-default search exit gates: `uddg` unwrap + http(s) validation,
 * snippet tolerance, fuse parity with Tavily (OK/OMITIDA/all-blank), and
 * the DDG-primary/Tavily-fallback policy matrix.
 *
 * No network, no key, no Android: the HTML layer is faked via
 * [DuckDuckGoSearchRepository.htmlSupplier], the Tavily delegate and the
 * connectivity gate are MockK fakes, and the repository runs on
 * Unconfined for determinism.
 */
class DuckDuckGoSearchRepositoryTest {

    private lateinit var keystoreManager: KeystoreManager
    private lateinit var backingStore: MutableMap<String, String>
    private lateinit var apiKeyStore: ApiKeyStore
    private lateinit var webPageFetcher: WebPageFetcher
    private lateinit var tavily: TavilySearchRepository
    private lateinit var repository: DuckDuckGoSearchRepository

    @BeforeEach
    fun setUp() {
        backingStore = mutableMapOf()
        keystoreManager = mockk()
        every { keystoreManager.put(any(), any()) } answers {
            backingStore[firstArg<String>()] = secondArg()
        }
        every { keystoreManager.get(any()) } answers { backingStore[firstArg()] }
        every { keystoreManager.remove(any()) } answers {
            backingStore.remove(firstArg<String>()); Unit
        }
        apiKeyStore = ApiKeyStore(keystoreManager)
        webPageFetcher = mockk()
        every { webPageFetcher.hasValidatedInternet() } returns true
        tavily = mockk()
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
            apiKeyStore,
            tavily,
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

    private fun groundedOutcome(urls: List<String>): TavilySearchOutcome.Grounded {
        val pairs = urls.map { it to "Text for $it with details." }
        return TavilySearchOutcome.Grounded(
            MultiUrlResult.Fused(
                block = GroundingPrompt.buildFusedBlock(pairs),
                okUrls = urls,
                skippedUrls = emptyList(),
                pageTexts = pairs.toMap(),
                details = urls.map {
                    GroundedSource(
                        url = it,
                        extractedText = "Text for $it with details.",
                        status = GroundedSourceStatus.OK,
                    )
                },
            ),
        )
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
    // Policy matrix: DDG-primary / Tavily-fallback
    // ------------------------------------------------------------------

    @Test
    fun `ddg-ok without key grounds silently and never calls tavily`() = runTest {
        repository.htmlSupplier = { fixtureHtml() }

        val outcome = repository.search("kotlin news")

        assertThat(outcome).isInstanceOf(TavilySearchOutcome.Grounded::class.java)
        val fused = (outcome as TavilySearchOutcome.Grounded).fused
        assertThat(fused.okUrls).containsExactly(
            "https://example.com/kotlin",
            "https://bare.example/direct",
        ).inOrder()
        assertThat(fused.skippedUrls).containsExactly("http://example.org/android")
        assertThat(fused.block).contains("--- Source [1]: https://example.com/kotlin ---")
        assertThat(fused.block).doesNotContain("example.org/android")
        coVerify(exactly = 0) { tavily.search(any(), any(), any(), any()) }
    }

    @Test
    fun `ddg-ok with key grounds without spending a tavily call`() = runTest {
        apiKeyStore.storeTavilyKey("tvly-test-key".toCharArray())
        repository.htmlSupplier = { fixtureHtml() }

        val outcome = repository.search("kotlin news")

        assertThat(outcome).isInstanceOf(TavilySearchOutcome.Grounded::class.java)
        coVerify(exactly = 0) { tavily.search(any(), any(), any(), any()) }
    }

    // ------------------------------------------------------------------
    // Title threading (quick-task): fuse() OK rows carry the anchor title.
    // ------------------------------------------------------------------

    @Test
    fun `ok rows carry the search-result title, omitida rows stay null`() = runTest {
        repository.htmlSupplier = { fixtureHtml() }

        val outcome = repository.search("kotlin news")

        assertThat(outcome).isInstanceOf(TavilySearchOutcome.Grounded::class.java)
        val fused = (outcome as TavilySearchOutcome.Grounded).fused
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

        assertThat(outcome).isInstanceOf(TavilySearchOutcome.Grounded::class.java)
        val fused = (outcome as TavilySearchOutcome.Grounded).fused
        assertThat(fused.details).hasSize(1)
        assertThat(fused.details[0].ogTitle).isNull()
    }

    @Test
    fun `ddg-empty with key delegates to tavily verbatim`() = runTest {
        apiKeyStore.storeTavilyKey("tvly-test-key".toCharArray())
        repository.htmlSupplier = { "<html><body>No results.</body></html>" }
        val delegated = groundedOutcome(listOf("https://tavily.example/delegate"))
        coEvery { tavily.search(any(), any(), any(), any()) } returns delegated

        val outcome = repository.search("kotlin news", maxResults = 5, contextSize = 4096)

        assertThat(outcome).isEqualTo(delegated)
        coVerify(exactly = 1) {
            tavily.search("kotlin news", 5, 4096, false)
        }
    }

    @Test
    fun `include-images threads through to the tavily delegate only`() = runTest {
        apiKeyStore.storeTavilyKey("tvly-test-key".toCharArray())
        repository.htmlSupplier = { "<html><body>No results.</body></html>" }
        val delegated = groundedOutcome(listOf("https://tavily.example/delegate"))
        coEvery { tavily.search(any(), any(), any(), any()) } returns delegated

        val outcome = repository.search("show me pictures of cats", includeImages = true)

        assertThat(outcome).isEqualTo(delegated)
        coVerify(exactly = 1) {
            tavily.search("show me pictures of cats", 5, 4096, true)
        }
    }

    @Test
    fun `ddg-served turn fuses zero images - no image api on the ddg leg`() = runTest {
        repository.htmlSupplier = { fixtureHtml() }

        val outcome = repository.search("show me pictures of cats", includeImages = true)

        assertThat(outcome).isInstanceOf(TavilySearchOutcome.Grounded::class.java)
        val fused = (outcome as TavilySearchOutcome.Grounded).fused
        assertThat(fused.images).isEmpty()
        coVerify(exactly = 0) { tavily.search(any(), any(), any(), any()) }
    }

    @Test
    fun `ddg-empty without key collapses to fetch-failed with no key nag`() = runTest {        repository.htmlSupplier = { "<html><body>No results.</body></html>" }

        val outcome = repository.search("kotlin news")

        assertThat(outcome).isEqualTo(
            TavilySearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
            ),
        )
        coVerify(exactly = 0) { tavily.search(any(), any(), any(), any()) }
    }

    @Test
    fun `ddg-throw with key passes the tavily outcome through`() = runTest {
        apiKeyStore.storeTavilyKey("tvly-bad-key".toCharArray())
        repository.htmlSupplier = { throw IOException("socket reset") }
        coEvery { tavily.search(any(), any(), any(), any()) } returns TavilySearchOutcome.InvalidKey

        assertThat(repository.search("kotlin news")).isEqualTo(TavilySearchOutcome.InvalidKey)
    }

    @Test
    fun `ddg-throw without key collapses to fetch-failed`() = runTest {
        repository.htmlSupplier = { throw IOException("socket reset") }

        val outcome = repository.search("kotlin news")

        assertThat(outcome).isEqualTo(
            TavilySearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
            ),
        )
        coVerify(exactly = 0) { tavily.search(any(), any(), any(), any()) }
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
            TavilySearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
            ),
        )
        // No key stored → no fallback attempt either.
        coVerify(exactly = 0) { tavily.search(any(), any(), any(), any()) }
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
            TavilySearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.OFFLINE),
            ),
        )
        assertThat(socketOpened).isFalse()
        coVerify(exactly = 0) { tavily.search(any(), any(), any(), any()) }
    }

    @Test
    fun `blank query collapses without opening a socket`() = runTest {
        var socketOpened = false
        repository.htmlSupplier = { socketOpened = true; fixtureHtml() }

        val outcome = repository.search("   ")

        assertThat(outcome).isEqualTo(
            TavilySearchOutcome.ModelOnly(
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
    // Image-turn routing: image-intent + key skips the DDG leg
    // ------------------------------------------------------------------

    @Test
    fun `image-intent with key skips ddg and goes straight to tavily`() = runTest {
        apiKeyStore.storeTavilyKey("tvly-test-key".toCharArray())
        var ddgLegRan = false
        repository.htmlSupplier = { ddgLegRan = true; fixtureHtml() }
        val withImages = groundedOutcome(listOf("https://tavily.example/delegate"))
        coEvery { tavily.search(any(), any(), any(), any()) } returns withImages

        val outcome = repository.search(
            "show me pictures of cats",
            maxResults = 5,
            contextSize = 4096,
            includeImages = true,
        )

        assertThat(outcome).isEqualTo(withImages)
        // DDG would have served this turn (fixture parses) — the direct
        // leg must skip it so the grid is never starved by a DDG-OK.
        assertThat(ddgLegRan).isFalse()
        coVerify(exactly = 1) {
            tavily.search("show me pictures of cats", 5, 4096, true)
        }
    }

    @Test
    fun `image-intent with key passes truncated query to tavily direct`() = runTest {
        apiKeyStore.storeTavilyKey("tvly-test-key".toCharArray())
        repository.htmlSupplier = { fixtureHtml() }
        val delegated = groundedOutcome(listOf("https://tavily.example/delegate"))
        coEvery { tavily.search(any(), any(), any(), any()) } returns delegated

        repository.search("x".repeat(600), includeImages = true)

        coVerify(exactly = 1) {
            tavily.search("x".repeat(500), any(), any(), true)
        }
    }

    @Test
    fun `image-intent without key falls through to ddg text grounding`() = runTest {
        var ddgLegRan = false
        repository.htmlSupplier = { ddgLegRan = true; fixtureHtml() }

        val outcome = repository.search("show me pictures of cats", includeImages = true)

        // DDG text grounds the turn; the grid stays empty (no image API on
        // the DDG leg); the caller attaches the images-need-key notice.
        assertThat(outcome).isInstanceOf(TavilySearchOutcome.Grounded::class.java)
        val fused = (outcome as TavilySearchOutcome.Grounded).fused
        assertThat(fused.okUrls).isNotEmpty()
        assertThat(fused.images).isEmpty()
        assertThat(ddgLegRan).isTrue()
        coVerify(exactly = 0) { tavily.search(any(), any(), any(), any()) }
    }

    @Test
    fun `image-intent without key and ddg-empty collapses to fetch-failed`() = runTest {
        repository.htmlSupplier = { "<html><body>No results.</body></html>" }

        val outcome = repository.search("show me pictures of cats", includeImages = true)

        assertThat(outcome).isEqualTo(
            TavilySearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
            ),
        )
        coVerify(exactly = 0) { tavily.search(any(), any(), any(), any()) }
    }

    @Test
    fun `non-image with key keeps ddg-primary even when ddg serves`() = runTest {
        apiKeyStore.storeTavilyKey("tvly-test-key".toCharArray())
        var ddgLegRan = false
        repository.htmlSupplier = { ddgLegRan = true; fixtureHtml() }

        val outcome = repository.search("kotlin news")

        assertThat(outcome).isInstanceOf(TavilySearchOutcome.Grounded::class.java)
        assertThat(ddgLegRan).isTrue()
        coVerify(exactly = 0) { tavily.search(any(), any(), any(), any()) }
    }
}
