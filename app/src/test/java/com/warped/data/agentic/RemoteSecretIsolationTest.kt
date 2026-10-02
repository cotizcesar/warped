package com.warped.data.agentic

import com.google.common.truth.Truth.assertThat
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.GroundingResult
import com.warped.data.grounding.MultiUrlFetcher
import com.warped.data.grounding.MultiUrlResult
import com.warped.data.grounding.SearchOgEnricher
import com.warped.data.grounding.SearchOutcome
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.local.security.ApiKeyStore
import com.warped.data.local.security.KeystoreManager
import com.warped.data.remote.dto.defaultAnthropicTools
import com.warped.data.remote.dto.defaultRemoteTools
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Phase 57 (57-02): endpoint-key isolation proof for the remote agentic
 * loop (AGENT-03, threats T-57-02/T-57-07), updated to the Phase 63
 * DDG-only posture.
 *
 * The loop executes tools through the keyless DDG singleton ONLY: search
 * is a plain keyless GET (no Bearer-key search client exists), fetch is
 * keyless, and the endpoint Authorization key (which lives on each
 * provider's own OkHttp interceptor) is never in scope in loop code.
 * These tests pin that with a DISTINCT fake endpoint key — any
 * cross-contamination fails loudly:
 *
 * - search: runs with an EMPTY keystore (no key stored anywhere) and the
 *   fused output carries no key material;
 * - fetch: the fetcher entry takes `(url, budget)` only — no key
 *   parameter exists — and fused outputs contain no key material;
 * - tool schemas (`tools[]`/`input_schema`) serialize with zero key
 *   material (they are static shapes, never per-call secrets);
 * - the remote executor policy (the exact pure functions every provider
 *   loop delegates to) never throws on edge inputs — the 56-02
 *   never-throw expectation extended to the remote executors.
 *
 * Static companion: the plan's verify step greps the agentic loop files
 * for `apiKey` references (must match only pre-existing endpoint-scoped
 * interceptor lines). JVM-local: no network, no Android Keystore (a
 * MockK-backed map fake), no real keys.
 */
class RemoteSecretIsolationTest {

    companion object {
        /** Fake endpoint Authorization key — must NEVER reach search/fetch. */
        const val ENDPOINT_FAKE_KEY = "ENDPOINT-FAKE-KEY-aaa111"

        /** Unrelated endpoint id for the fake endpoint key (alias separation). */
        const val ENDPOINT_ID = 99L
    }

    private lateinit var backingStore: MutableMap<String, String>
    private lateinit var apiKeyStore: ApiKeyStore
    private lateinit var webPageFetcher: WebPageFetcher
    private lateinit var searchRepository: DuckDuckGoSearchRepository

    private fun fixtureHtml(): String = """
        <html><body>
        <div class="result results_links web-result">
          <h2 class="result__title">
            <a rel="nofollow" class="result__a" href="https://example.com/paris">Paris</a>
          </h2>
          <a class="result__snippet" href="https://example.com/paris">Paris is the capital of France, with details.</a>
        </div>
        </body></html>
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        backingStore = mutableMapOf()
        val keystoreManager: KeystoreManager = mockk()
        every { keystoreManager.put(any(), any()) } answers {
            backingStore[firstArg<String>()] = secondArg()
        }
        every { keystoreManager.get(any()) } answers { backingStore[firstArg()] }
        every { keystoreManager.remove(any()) } answers {
            backingStore.remove(firstArg<String>()); Unit
        }
        apiKeyStore = ApiKeyStore(keystoreManager)
        // The endpoint key lives in the store under its own alias —
        // exactly like production. The search path must never read it.
        apiKeyStore.storeKey(ENDPOINT_ID, ENDPOINT_FAKE_KEY.toCharArray())

        webPageFetcher = mockk()
        every { webPageFetcher.hasValidatedInternet() } returns true
        // Enrichment no-op seam (no sockets in unit tests — same pattern
        // as the repository tests).
        val enricher = SearchOgEnricher(OkHttpClient()).apply {
            headSupplier = { null }
            ioDispatcher = Dispatchers.Unconfined
        }
        searchRepository = DuckDuckGoSearchRepository(
            OkHttpClient(),
            webPageFetcher,
            enricher,
        )
        searchRepository.ioDispatcher = Dispatchers.Unconfined
        searchRepository.htmlSupplier = { fixtureHtml() }
    }

    // Search path: keyless DDG only — no Bearer-key client exists.

    @Test
    fun `search grounds with an empty keystore and no bearer header`() = runTest {
        backingStore.clear()

        val outcome = searchRepository.search(
            query = "capital of France",
            maxResults = DuckDuckGoSearchRepository.DEFAULT_MAX_RESULTS,
            contextSize = 4096,
        )

        assertThat(outcome).isInstanceOf(SearchOutcome.Grounded::class.java)
        val fused = (outcome as SearchOutcome.Grounded).fused
        assertThat(fused.okUrls).containsExactly("https://example.com/paris")
        assertThat(fused.block).doesNotContain(ENDPOINT_FAKE_KEY)
    }

    @Test
    fun `search output carries no key material`() = runTest {
        val outcome = searchRepository.search(
            query = "capital of France",
            maxResults = DuckDuckGoSearchRepository.DEFAULT_MAX_RESULTS,
            contextSize = 4096,
        )

        assertThat(outcome).isInstanceOf(SearchOutcome.Grounded::class.java)
        val fused = (outcome as SearchOutcome.Grounded).fused
        assertThat(fused.block).doesNotContain(ENDPOINT_FAKE_KEY)
        assertThat(fused.details.map { it.url to it.extractedText }.toString())
            .doesNotContain(ENDPOINT_FAKE_KEY)
    }

    // Fetch path: keyless by construction.

    @Test
    fun `fetch executes with url and budget only and leaks no key`() = runTest {
        val pageFetcher: WebPageFetcher = mockk()
        coEvery { pageFetcher.fetch(any(), any()) } answers {
            GroundingResult.Grounded(
                block = "Source [1] (https://example.com/page): page text",
                url = "https://example.com/page",
                text = "page text",
            )
        }
        val multiUrlFetcher = MultiUrlFetcher(pageFetcher)
        multiUrlFetcher.ioDispatcher = Dispatchers.Unconfined

        val result = multiUrlFetcher.fetchAll(
            urls = listOf("https://example.com/page"),
            contextSize = 4096,
            onProgress = null,
        )

        // The entry takes (url, budget) only — there is no key parameter
        // that could carry the endpoint secret to a fetched page.
        coVerify(exactly = 1) { pageFetcher.fetch("https://example.com/page", any()) }
        assertThat(result).isInstanceOf(MultiUrlResult.Fused::class.java)
        val fused = result as MultiUrlResult.Fused
        assertThat(fused.block).doesNotContain(ENDPOINT_FAKE_KEY)
    }

    // Tool surface: static shapes, zero secrets.

    @Test
    fun `compat tools schemas serialize with exact names and no key material`() {
        val wireJson = Json { encodeDefaults = true }
        val wire = wireJson.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(
                com.warped.data.remote.dto.OpenAiTool.serializer(),
            ),
            defaultRemoteTools(),
        )

        assertThat(wire).contains("\"web_search\"")
        assertThat(wire).contains("\"web_fetch\"")
        assertThat(wire).doesNotContain(ENDPOINT_FAKE_KEY)
        // Locked loose schemas: no strict flag, no tool_choice anywhere.
        assertThat(wire).doesNotContain("strict")
        assertThat(wire).doesNotContain("tool_choice")
    }

    @Test
    fun `anthropic tools schemas serialize with exact names and no key material`() {
        val wireJson = Json { encodeDefaults = true }
        val wire = wireJson.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(
                com.warped.data.remote.dto.AnthropicTool.serializer(),
            ),
            defaultAnthropicTools(),
        )

        assertThat(wire).contains("\"web_search\"")
        assertThat(wire).contains("\"web_fetch\"")
        assertThat(wire).contains("input_schema")
        assertThat(wire).doesNotContain(ENDPOINT_FAKE_KEY)
    }

    // Never-throw extension (56-02 expectation → remote executors).

    @Test
    fun `remote executor policy never throws on edge inputs`() {
        // parseToolArgs: the exact degradation the loops rely on —
        // blank → empty map (validateArgs short-circuit), garbage /
        // non-objects → null (toolFailureMessage degradation).
        assertThat(parseToolArgs("")).isEmpty()
        assertThat(parseToolArgs("   ")).isEmpty()
        assertThat(parseToolArgs("{not json")).isNull()
        assertThat(parseToolArgs("[1,2]")).isNull()
        assertThat(parseToolArgs("\"scalar\"")).isNull()
        assertThat(parseToolArgs("{\"query\": 42}")).isNotNull()

        // validateArgs: non-string values, missing keys, unknown tools,
        // control characters — all fail closed, never throw.
        assertThat(LocalToolLoop.validateArgs("web_search", mapOf("query" to 42))).isNotNull()
        assertThat(LocalToolLoop.validateArgs("web_search", mapOf<String, Any?>())).isNotNull()
        assertThat(LocalToolLoop.validateArgs("web_fetch", mapOf("url" to null))).isNotNull()
        assertThat(LocalToolLoop.validateArgs("web_fetch", mapOf("url" to "ftp://x"))).isNotNull()
        assertThat(LocalToolLoop.validateArgs("rm -rf", mapOf("query" to "x"))).isNotNull()
        assertThat(LocalToolLoop.validateArgs("", mapOf("" to ""))).isNotNull()
        assertThat(LocalToolLoop.validateArgs("web_search", mapOf("query" to "ok"))).isNull()
        assertThat(LocalToolLoop.validateArgs("web_fetch", mapOf("url" to "https://example.com"))).isNull()

        // Outcome mapping across every variant — never throws, never null.
        assertThat(
            LocalToolLoop.mapSearchOutcome(
                SearchOutcome.ModelOnly(
                    MultiUrlResult.AllFailed(GroundingResult.Reason.OFFLINE),
                ),
            ),
        ).isNotNull()
        assertThat(
            LocalToolLoop.mapSearchOutcome(
                SearchOutcome.ModelOnly(
                    MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
                ),
            ),
        ).isNotNull()
        assertThat(
            LocalToolLoop.mapFetchResult(
                MultiUrlResult.AllFailed(GroundingResult.Reason.OFFLINE),
            ),
        ).isNotNull()
        assertThat(
            LocalToolLoop.mapFetchResult(
                MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
            ),
        ).isNotNull()

        // Status display + failure collapse + cap floor — never throws.
        assertThat(LocalToolLoop.statusDisplay("nope", mapOf("query" to "x"))).isNull()
        assertThat(LocalToolLoop.statusDisplay("web_search", mapOf<String, Any?>())).isNotNull()
        assertThat(LocalToolLoop.toolFailureMessage("\u0000\u0007")).isNotNull()
        assertThat(LocalToolLoop.isCapReached(-1)).isFalse()
    }
}
