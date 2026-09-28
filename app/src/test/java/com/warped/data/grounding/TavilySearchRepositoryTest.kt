package com.warped.data.grounding

import com.google.common.truth.Truth.assertThat
import com.warped.data.local.security.ApiKeyStore
import com.warped.data.local.security.KeystoreManager
import com.warped.data.remote.api.TavilyApi
import com.warped.data.remote.dto.TavilySearchRequest
import com.warped.data.remote.dto.TavilySearchResponse
import com.warped.data.remote.dto.TavilySearchResult
import com.warped.domain.model.GroundedSourceStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Response

/**
 * Phase 55 (TAV-01/TAV-02) exit gates for the Tavily tracer backbone:
 * key alias round-trip, search→fused fusion order, partial vs all-fail
 * routing, 401/429 mapping, and result/query caps.
 *
 * The `TavilyApi` is a MockK fake (no network, no key — same no-network
 * pattern as `MultiUrlFetcherTest`); the keystore is a MockK-backed map
 * fake (never touches Android Keystore on the JVM); the repository runs
 * on Unconfined for determinism. Zero new test dependencies.
 */
class TavilySearchRepositoryTest {

    private lateinit var keystoreManager: KeystoreManager
    private lateinit var backingStore: MutableMap<String, String>
    private lateinit var apiKeyStore: ApiKeyStore
    private lateinit var api: TavilyApi
    private lateinit var repository: TavilySearchRepository

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
        api = mockk()
        repository = TavilySearchRepository(api, apiKeyStore)
        repository.ioDispatcher = Dispatchers.Unconfined
    }

    // Key alias round-trip (TAV-01 storage half).

    @Test
    fun `tavily key round-trips under the dedicated alias`() {
        apiKeyStore.storeTavilyKey("tvly-secret".toCharArray())

        assertThat(backingStore).containsKey(ApiKeyStore.TAVILY_ALIAS)
        assertThat(backingStore[ApiKeyStore.TAVILY_ALIAS]).isEqualTo("tvly-secret")
        assertThat(apiKeyStore.getTavilyKey()?.concatToString()).isEqualTo("tvly-secret")
    }

    @Test
    fun `tavily key lives outside endpoint namespaces and zeroes the input`() {
        val input = "tvly-secret".toCharArray()
        apiKeyStore.storeTavilyKey(input)

        assertThat(input.concatToString()).isEqualTo("00000000000")
        assertThat(backingStore.keys).containsExactly(ApiKeyStore.TAVILY_ALIAS)
    }

    @Test
    fun `delete tavily key removes only the tavily alias`() {
        apiKeyStore.storeKey(7L, "endpoint-key".toCharArray())
        apiKeyStore.storeTavilyKey("tvly-secret".toCharArray())

        apiKeyStore.deleteTavilyKey()

        assertThat(apiKeyStore.getTavilyKey()).isNull()
        assertThat(apiKeyStore.getKey(7L)?.concatToString()).isEqualTo("endpoint-key")
    }

    @Test
    fun `delete-all-keys also wipes the tavily alias`() {
        apiKeyStore.storeKey(7L, "endpoint-key".toCharArray())
        apiKeyStore.storeTavilyKey("tvly-secret".toCharArray())

        apiKeyStore.deleteAllKeys(listOf(7L))

        assertThat(apiKeyStore.getTavilyKey()).isNull()
        assertThat(apiKeyStore.getKey(7L)).isNull()
    }

    // Fusion (TAV-02 producer half).

    private fun result(i: Int, content: String = "Snippet text $i with details."): TavilySearchResult =
        TavilySearchResult(
            title = "Title $i",
            url = "https://tavily.example/page$i",
            content = content,
            score = 0.9 - i * 0.01,
        )

    private fun stubSearch(vararg results: TavilySearchResult) {
        apiKeyStore.storeTavilyKey("tvly-test-key".toCharArray())
        coEvery { api.search(any(), any()) } returns
            Response.success(TavilySearchResponse(query = "q", results = results.toList()))
    }

    @Test
    fun `three results fuse numbered in order via buildFusedBlock`() = runTest {
        stubSearch(result(1), result(2), result(3))

        val outcome = repository.search("kotlin news")

        assertThat(outcome).isInstanceOf(TavilySearchOutcome.Grounded::class.java)
        val fused = (outcome as TavilySearchOutcome.Grounded).fused
        assertThat(fused.okUrls).containsExactly(
            "https://tavily.example/page1",
            "https://tavily.example/page2",
            "https://tavily.example/page3",
        ).inOrder()
        assertThat(fused.skippedUrls).isEmpty()
        assertThat(fused.block).contains("--- Source [1]: https://tavily.example/page1 ---")
        assertThat(fused.block).contains("--- Source [3]: https://tavily.example/page3 ---")
        assertThat(fused.block.indexOf("https://tavily.example/page1"))
            .isLessThan(fused.block.indexOf("https://tavily.example/page3"))
        assertThat(fused.details).hasSize(3)
        assertThat(fused.details.map { it.status }).containsExactly(
            GroundedSourceStatus.OK,
            GroundedSourceStatus.OK,
            GroundedSourceStatus.OK,
        ).inOrder()
    }

    @Test
    fun `blank-content result becomes omitida skipped not silent`() = runTest {
        stubSearch(
            result(1),
            TavilySearchResult(title = "Empty", url = "https://tavily.example/blank", content = "   "),
            result(3),
        )

        val outcome = repository.search("kotlin news")

        assertThat(outcome).isInstanceOf(TavilySearchOutcome.Grounded::class.java)
        val fused = (outcome as TavilySearchOutcome.Grounded).fused
        assertThat(fused.okUrls).containsExactly(
            "https://tavily.example/page1",
            "https://tavily.example/page3",
        ).inOrder()
        assertThat(fused.skippedUrls).containsExactly("https://tavily.example/blank")
        assertThat(fused.block).doesNotContain("tavily.example/blank")
        assertThat(fused.details).hasSize(3)
        assertThat(fused.details[1].status).isEqualTo(GroundedSourceStatus.OMITIDA)
        assertThat(fused.details[1].extractedText).isNull()
    }

    @Test
    fun `all-blank results collapse to fetch-failed model-only`() = runTest {
        stubSearch(
            TavilySearchResult(title = "A", url = "https://tavily.example/a", content = ""),
            TavilySearchResult(title = "", url = "", content = ""),
        )

        val outcome = repository.search("kotlin news")

        assertThat(outcome).isEqualTo(
            TavilySearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
            ),
        )
    }

    @Test
    fun `empty result list collapses to fetch-failed model-only`() = runTest {
        stubSearch()

        val outcome = repository.search("kotlin news")

        assertThat(outcome).isEqualTo(
            TavilySearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
            ),
        )
    }

    // Error mapping.

    @Test
    fun `401 maps to invalid-key`() = runTest {
        apiKeyStore.storeTavilyKey("tvly-bad-key".toCharArray())
        coEvery { api.search(any(), any()) } returns
            Response.error(401, "unauthorized".toResponseBody())

        assertThat(repository.search("kotlin news")).isEqualTo(TavilySearchOutcome.InvalidKey)
    }

    @Test
    fun `429 maps to usage-limit`() = runTest {
        apiKeyStore.storeTavilyKey("tvly-test-key".toCharArray())
        coEvery { api.search(any(), any()) } returns
            Response.error(429, "rate limited".toResponseBody())

        assertThat(repository.search("kotlin news")).isEqualTo(TavilySearchOutcome.UsageLimit)
    }

    @Test
    fun `other http errors collapse to fetch-failed`() = runTest {
        apiKeyStore.storeTavilyKey("tvly-test-key".toCharArray())
        coEvery { api.search(any(), any()) } returns
            Response.error(500, "server error".toResponseBody())

        assertThat(repository.search("kotlin news")).isEqualTo(
            TavilySearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
            ),
        )
    }

    @Test
    fun `missing key short-circuits without opening a socket`() = runTest {
        val outcome = repository.search("kotlin news")

        assertThat(outcome).isEqualTo(TavilySearchOutcome.MissingKey)
        coVerify(exactly = 0) { api.search(any(), any()) }
    }

    // Auth + caps.

    @Test
    fun `auth is a bearer header and the key never enters the body`() = runTest {
        val authSlot = slot<String>()
        val requestSlot = slot<TavilySearchRequest>()
        apiKeyStore.storeTavilyKey("tvly-test-key-123".toCharArray())
        coEvery { api.search(capture(authSlot), capture(requestSlot)) } returns
            Response.success(TavilySearchResponse(results = listOf(result(1))))

        repository.search("kotlin news")

        assertThat(authSlot.captured).isEqualTo("Bearer tvly-test-key-123")
        assertThat(requestSlot.captured.toString()).doesNotContain("tvly-test-key-123")
    }

    @Test
    fun `default max-results is five`() = runTest {
        val requestSlot = slot<TavilySearchRequest>()
        apiKeyStore.storeTavilyKey("tvly-test-key".toCharArray())
        coEvery { api.search(any(), capture(requestSlot)) } returns
            Response.success(TavilySearchResponse(results = listOf(result(1))))

        repository.search("kotlin news")

        assertThat(requestSlot.captured.max_results).isEqualTo(5)
    }

    @Test
    fun `result count caps at ten`() = runTest {
        val requestSlot = slot<TavilySearchRequest>()
        apiKeyStore.storeTavilyKey("tvly-test-key".toCharArray())
        coEvery { api.search(any(), capture(requestSlot)) } returns
            Response.success(
                TavilySearchResponse(results = (1..12).map { result(it) }),
            )

        val outcome = repository.search("kotlin news", maxResults = 99)

        assertThat(requestSlot.captured.max_results).isEqualTo(10)
        val fused = (outcome as TavilySearchOutcome.Grounded).fused
        assertThat(fused.okUrls).hasSize(10)
    }

    @Test
    fun `query over five hundred chars is truncated pass-through`() = runTest {
        val requestSlot = slot<TavilySearchRequest>()
        apiKeyStore.storeTavilyKey("tvly-test-key".toCharArray())
        coEvery { api.search(any(), capture(requestSlot)) } returns
            Response.success(TavilySearchResponse(results = listOf(result(1))))

        repository.search("x".repeat(600))

        assertThat(requestSlot.captured.query).hasLength(500)
    }
}
