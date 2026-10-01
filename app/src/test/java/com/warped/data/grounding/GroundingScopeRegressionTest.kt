package com.warped.data.grounding

import com.google.common.truth.Truth.assertThat
import com.warped.data.local.db.dao.ConversationDao
import com.warped.data.local.db.dao.GroundedSourceDao
import com.warped.data.local.db.dao.MessageDao
import com.warped.data.local.security.ApiKeyStore
import com.warped.data.local.security.KeystoreManager
import com.warped.data.remote.api.TavilyApi
import com.warped.data.repository.ChatRepositoryImpl
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.GroundedSourceStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * Phase 62-01 (LEAK-04 regression): locks the Phase 61 Leg 3/4 clean result
 * (grounding fetch + cancel, offline → retry with zero retained jobs).
 *
 * The per-send grounding fan-out runs in ONE cancellable scope
 * ([MultiUrlFetcher.fetchAll] uses `coroutineScope`, NOT `supervisorScope`;
 * [TavilySearchRepository.search] rethrows [CancellationException] instead
 * of converting it to model-only): cancelling the send cancels the fan-out
 * children, the Tavily call, and the SSE accumulators as a unit. Retry
 * reuses the existing assistant row via [ChatRepositoryImpl.replaceSources]
 * (delete-then-insert, never a re-save that would CASCADE-wipe sources or
 * retain the old job reference).
 *
 * Mutation-sanity (by inspection): switching `fetchAll` to `supervisorScope`
 * fails `cancelling the per-send scope...` (a child would survive); catching
 * `CancellationException` as model-only in `TavilySearchRepository.search`
 * fails `tavily search in the same scope...`; re-saving the message instead
 * of `replaceSources` fails `retry reuses...` via the `insert(exactly = 0)`
 * check.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroundingScopeRegressionTest {

    // ------------------------------------------------------------------
    // Fan-out cancel: one scope, all children die together.
    // ------------------------------------------------------------------

    private fun blockingFetcher(started: AtomicInteger): WebPageFetcher {
        val fetcher = mockk<WebPageFetcher>()
        coEvery { fetcher.fetch(any(), any()) } coAnswers {
            started.incrementAndGet()
            delay(60_000)
            GroundingResult.Grounded("block", "https://x.example/", "text")
        }
        return fetcher
    }

    @Test
    fun `cancelling the per-send scope cancels every fan-out child as one unit`() = runTest {
        val started = AtomicInteger(0)
        val fetcher = MultiUrlFetcher(blockingFetcher(started)).apply {
            ioDispatcher = Dispatchers.Unconfined
        }
        var result: MultiUrlResult? = null

        // Per-send parent scope (mirrors the turn job): Stop / new-send
        // cancels THIS scope, never individual children.
        val sendScope = CoroutineScope(coroutineContext + SupervisorJob())
        val job = sendScope.launch {
            result = fetcher.fetchAll(
                urls = listOf("https://a.example/", "https://b.example/"),
                contextSize = 4096,
            )
        }
        advanceTimeBy(500)
        assertThat(started.get()).isEqualTo(2)

        sendScope.cancel()
        advanceUntilIdle()

        assertThat(job.isCancelled).isTrue()
        // No fusion ever ran: cancellation threw through coroutineScope
        // (never converted to a partial Fused / AllFailed).
        assertThat(result).isNull()
        sendScope.cancel()
    }

    // ------------------------------------------------------------------
    // Tavily in the same scope: cancel propagates, never model-only.
    // ------------------------------------------------------------------

    private fun blockingTavily(): Pair<TavilySearchRepository, AtomicInteger> {
        val started = AtomicInteger(0)
        val backingStore = mutableMapOf<String, String>()
        val keystoreManager = mockk<KeystoreManager>()
        every { keystoreManager.put(any(), any()) } answers {
            backingStore[firstArg<String>()] = secondArg<String>()
        }
        every { keystoreManager.get(any()) } answers { backingStore[firstArg<String>()] }
        every { keystoreManager.remove(any()) } answers {
            backingStore.remove(firstArg<String>()); Unit
        }
        val apiKeyStore = ApiKeyStore(keystoreManager)
        apiKeyStore.storeTavilyKey("tvly-test".toCharArray())
        val api = mockk<TavilyApi>()
        coEvery { api.search(any(), any()) } coAnswers {
            started.incrementAndGet()
            awaitCancellation()
        }
        val enricher = SearchOgEnricher(OkHttpClient()).apply {
            headSupplier = { null }
            ioDispatcher = Dispatchers.Unconfined
        }
        val repository = TavilySearchRepository(api, apiKeyStore, enricher).apply {
            ioDispatcher = Dispatchers.Unconfined
        }
        return repository to started
    }

    @Test
    fun `tavily search in the same scope cancels with the fan-out never model-only`() = runTest {
        val startedFanOut = AtomicInteger(0)
        val fetcher = MultiUrlFetcher(blockingFetcher(startedFanOut)).apply {
            ioDispatcher = Dispatchers.Unconfined
        }
        val (tavily, startedTavily) = blockingTavily()

        val sendScope = CoroutineScope(coroutineContext + SupervisorJob())
        val fanOut = sendScope.async {
            fetcher.fetchAll(listOf("https://a.example/"), 4096)
        }
        val search = sendScope.async {
            tavily.search(query = "android release", maxResults = 5, contextSize = 4096)
        }
        advanceTimeBy(500)
        assertThat(startedFanOut.get()).isEqualTo(1)
        assertThat(startedTavily.get()).isEqualTo(1)

        sendScope.cancel()
        advanceUntilIdle()

        // Both children cancelled — the Tavily CancellationException
        // propagated (rethrow, never ModelOnly), so `await()` throws rather
        // than returning a stale outcome.
        assertThat(fanOut.isCancelled).isTrue()
        assertThat(search.isCancelled).isTrue()
        var searchThrewCancel = false
        try {
            search.await()
        } catch (e: CancellationException) {
            searchThrewCancel = true
        }
        assertThat(searchThrewCancel).isTrue()
    }

    // ------------------------------------------------------------------
    // Retry row reuse: same row, no re-save, no retained job reference.
    // ------------------------------------------------------------------

    private fun repositoryWithRow(
        rowId: Long?,
        messageDao: MessageDao = mockk(),
        groundedSourceDao: GroundedSourceDao = mockk(relaxed = true),
    ): ChatRepositoryImpl {
        coEvery { messageDao.findAssistantRowId(any(), any()) } returns rowId
        return ChatRepositoryImpl(
            conversationDao = mockk<ConversationDao>(relaxed = true),
            messageDao = messageDao,
            groundedSourceDao = groundedSourceDao,
        )
    }

    private val retrySources = listOf(
        GroundedSource(url = "https://a.example/", extractedText = "text", status = GroundedSourceStatus.OK),
    )

    @Test
    fun `retry reuses the assistant row without re-saving the message`() = runTest {
        val messageDao = mockk<MessageDao>()
        val groundedSourceDao = mockk<GroundedSourceDao>(relaxed = true)
        val repo = repositoryWithRow(rowId = 7L, messageDao = messageDao, groundedSourceDao = groundedSourceDao)
        val at = Instant.ofEpochMilli(1_700_000_000_000L)

        repo.replaceSources(conversationId = 42L, assistantCreatedAt = at, sources = retrySources)

        // Delete-then-insert on the EXISTING row; the message itself is never
        // re-saved (insert REPLACE would CASCADE-wipe the source rows).
        coVerify(exactly = 1) { groundedSourceDao.deleteByMessage(7L) }
        coVerify(exactly = 1) { groundedSourceDao.insertAll(any()) }
        coVerify(exactly = 0) { messageDao.insert(any()) }
    }

    @Test
    fun `second retry re-targets the same row - never mints a new one`() = runTest {
        val messageDao = mockk<MessageDao>()
        val groundedSourceDao = mockk<GroundedSourceDao>(relaxed = true)
        val repo = repositoryWithRow(rowId = 7L, messageDao = messageDao, groundedSourceDao = groundedSourceDao)
        val at = Instant.ofEpochMilli(1_700_000_000_000L)

        repo.replaceSources(42L, at, retrySources)
        repo.replaceSources(42L, at, retrySources)

        coVerify(exactly = 2) { groundedSourceDao.deleteByMessage(7L) }
        coVerify(exactly = 2) { groundedSourceDao.insertAll(any()) }
        coVerify(exactly = 0) { messageDao.insert(any()) }
    }

    @Test
    fun `retry on a deleted message is a silent no-op - never orphan rows`() = runTest {
        val messageDao = mockk<MessageDao>()
        val groundedSourceDao = mockk<GroundedSourceDao>(relaxed = true)
        val repo = repositoryWithRow(rowId = null, messageDao = messageDao, groundedSourceDao = groundedSourceDao)

        repo.replaceSources(42L, Instant.ofEpochMilli(1_700_000_000_000L), retrySources)

        coVerify(exactly = 0) { groundedSourceDao.deleteByMessage(any()) }
        coVerify(exactly = 0) { groundedSourceDao.insertAll(any()) }
        coVerify(exactly = 0) { messageDao.insert(any()) }
    }
}
