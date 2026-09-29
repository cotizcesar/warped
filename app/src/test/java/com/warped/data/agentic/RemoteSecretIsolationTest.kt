package com.warped.data.agentic

import com.google.common.truth.Truth.assertThat
import com.warped.data.grounding.GroundingResult
import com.warped.data.grounding.MultiUrlFetcher
import com.warped.data.grounding.MultiUrlResult
import com.warped.data.grounding.TavilySearchOutcome
import com.warped.data.grounding.TavilySearchRepository
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.local.security.ApiKeyStore
import com.warped.data.local.security.KeystoreManager
import com.warped.data.remote.api.TavilyApi
import com.warped.data.remote.dto.TavilySearchRequest
import com.warped.data.remote.dto.TavilySearchResponse
import com.warped.data.remote.dto.TavilySearchResult
import com.warped.data.remote.dto.defaultAnthropicTools
import com.warped.data.remote.dto.defaultRemoteTools
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Response

/**
 * Phase 57 (57-02): endpoint-key isolation proof for the remote agentic
 * loop (AGENT-03, threats T-57-02/T-57-07).
 *
 * The loop executes tools through the Phase 55/52 singletons ONLY: search
 * travels on the dedicated Tavily client with a per-call
 * `Authorization: Bearer <tavily-key>` header, fetch is keyless, and the
 * endpoint Authorization key (which lives on each provider's own OkHttp
 * interceptor) is never in scope in loop code. These tests pin that with
 * DISTINCT fake keys — any cross-contamination fails loudly:
 *
 * - search: the captured `Authorization` header equals the Tavily Bearer
 *   exactly and never contains the endpoint key; the request body carries
 *   no key material at all;
 * - fetch: the fetcher entry takes `(url, budget)` only — no key
 *   parameter exists — and fused outputs contain neither key;
 * - tool schemas (`tools[]`/`input_schema`) serialize with zero key
 *   material (they are static shapes, never per-call secrets);
 * - the remote executor policy (the exact pure functions every provider
 *   loop delegates to) never throws on edge inputs — the 56-02
 *   never-throw expectation extended to the remote executors.
 *
 * Static companion: the plan's verify step greps the agentic loop files
 * for `apiKey` references (must match only pre-existing endpoint-scoped
 * interceptor lines). JVM-local: no network, no Android Keystore (the
 * `TavilySearchRepositoryTest` MockK-backed map fake), no real keys.
 */
class RemoteSecretIsolationTest {

    companion object {
        /** Fake endpoint Authorization key — must NEVER reach Tavily/fetch. */
        const val ENDPOINT_FAKE_KEY = "ENDPOINT-FAKE-KEY-aaa111"

        /** Fake Tavily key — the ONLY secret the search path may carry. */
        const val TAVILY_FAKE_KEY = "TAVILY-FAKE-KEY-bbb222"

        /** Unrelated endpoint id for the fake endpoint key (alias separation). */
        const val ENDPOINT_ID = 99L
    }

    private lateinit var backingStore: MutableMap<String, String>
    private lateinit var apiKeyStore: ApiKeyStore
    private lateinit var tavilyApi: TavilyApi
    private lateinit var searchRepository: TavilySearchRepository

    private val json = Json { ignoreUnknownKeys = true }

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
        // Both keys live in the store under SEPARATE aliases — exactly
        // like production (endpoint keys per endpoint id, Tavily under
        // the dedicated alias).
        apiKeyStore.storeTavilyKey(TAVILY_FAKE_KEY.toCharArray())
        apiKeyStore.storeKey(ENDPOINT_ID, ENDPOINT_FAKE_KEY.toCharArray())

        tavilyApi = mockk()
        searchRepository = TavilySearchRepository(tavilyApi, apiKeyStore)
        searchRepository.ioDispatcher = Dispatchers.Unconfined
    }

    // Search path: Tavily Bearer only.

    @Test
    fun `search Authorization carries only the Tavily Bearer`() = runTest {
        val authSlot = slot<String>()
        val bodySlot = slot<TavilySearchRequest>()
        coEvery {
            tavilyApi.search(capture(authSlot), capture(bodySlot))
        } returns Response.success(
            TavilySearchResponse(
                query = "capital of France",
                results = listOf(
                    TavilySearchResult(
                        title = "Paris",
                        url = "https://example.com/paris",
                        content = "Paris is the capital of France.",
                        score = 0.9,
                    ),
                ),
            ),
        )

        val outcome = searchRepository.search(
            query = "capital of France",
            maxResults = TavilySearchRepository.DEFAULT_MAX_RESULTS,
            contextSize = 4096,
        )

        assertThat(authSlot.captured).isEqualTo("Bearer $TAVILY_FAKE_KEY")
        assertThat(authSlot.captured).doesNotContain(ENDPOINT_FAKE_KEY)
        assertThat(outcome).isInstanceOf(TavilySearchOutcome.Grounded::class.java)
    }

    @Test
    fun `search body carries no key material`() = runTest {
        val bodySlot = slot<TavilySearchRequest>()
        coEvery {
            tavilyApi.search(any(), capture(bodySlot))
        } returns Response.success(TavilySearchResponse(query = "q"))

        searchRepository.search(
            query = "q",
            maxResults = TavilySearchRepository.DEFAULT_MAX_RESULTS,
            contextSize = 4096,
        )

        val bodyJson = json.encodeToString(
            TavilySearchRequest.serializer(),
            bodySlot.captured,
        )
        assertThat(bodyJson).doesNotContain(TAVILY_FAKE_KEY)
        assertThat(bodyJson).doesNotContain(ENDPOINT_FAKE_KEY)
    }

    // Fetch path: keyless by construction.

    @Test
    fun `fetch executes with url and budget only and leaks neither key`() = runTest {
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
        // that could carry either secret to a fetched page.
        coVerify(exactly = 1) { pageFetcher.fetch("https://example.com/page", any()) }
        assertThat(result).isInstanceOf(MultiUrlResult.Fused::class.java)
        val fused = result as MultiUrlResult.Fused
        assertThat(fused.block).doesNotContain(TAVILY_FAKE_KEY)
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
        assertThat(wire).doesNotContain(TAVILY_FAKE_KEY)
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
        assertThat(wire).doesNotContain(TAVILY_FAKE_KEY)
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
            LocalToolLoop.mapSearchOutcome(TavilySearchOutcome.MissingKey),
        ).isNotNull()
        assertThat(
            LocalToolLoop.mapSearchOutcome(TavilySearchOutcome.InvalidKey),
        ).isNotNull()
        assertThat(
            LocalToolLoop.mapSearchOutcome(TavilySearchOutcome.UsageLimit),
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
