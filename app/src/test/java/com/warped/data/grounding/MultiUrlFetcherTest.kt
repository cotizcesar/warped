package com.warped.data.grounding

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Phase 52 (FETCH-01..FETCH-03) exit gates for the fan-out orchestrator:
 * fusion order, partial vs all-fail routing, worst-case collapse, the
 * 5-URL cap, dedupe, cancel propagation, and per-page budget threading.
 *
 * The fetcher is a MockK fake (no Android imports, no network); the
 * orchestrator runs on Unconfined for determinism.
 */
class MultiUrlFetcherTest {

    private lateinit var fetcher: WebPageFetcher
    private lateinit var orchestrator: MultiUrlFetcher

    @BeforeEach
    fun setUp() {
        fetcher = mockk()
        orchestrator = MultiUrlFetcher(fetcher)
        orchestrator.ioDispatcher = Dispatchers.Unconfined
    }

    private fun grounded(url: String, text: String = "Texto de $url."): GroundingResult.Grounded =
        GroundingResult.Grounded(
            block = "[WEB CONTEXT — fuente [1]: $url]\n$text\n[FIN WEB CONTEXT]",
            url = url,
            text = text,
        )

    private fun stubOk(vararg urls: String) {
        for (url in urls) {
            coEvery { fetcher.fetch(url, any()) } returns grounded(url)
        }
    }

    @Test
    fun `all-ok pages fuse numbered in paste order`() = runTest {
        val urls = listOf("https://a.example/uno", "https://b.example/dos", "https://c.example/tres")
        stubOk(*urls.toTypedArray())

        val result = orchestrator.fetchAll(urls, 4096)

        assertThat(result).isInstanceOf(MultiUrlResult.Fused::class.java)
        val fused = result as MultiUrlResult.Fused
        assertThat(fused.okUrls).containsExactlyElementsIn(urls).inOrder()
        assertThat(fused.skippedUrls).isEmpty()
        assertThat(fused.block).contains("[WEB CONTEXT 1 — fuente [1]: https://a.example/uno]")
        assertThat(fused.block).contains("[WEB CONTEXT 3 — fuente [3]: https://c.example/tres]")
        assertThat(fused.block.indexOf("https://a.example/uno"))
            .isLessThan(fused.block.indexOf("https://c.example/tres"))
    }

    @Test
    fun `one dead of three grounds partial with the dead link skipped`() = runTest {
        stubOk("https://a.example/uno", "https://c.example/tres")
        coEvery { fetcher.fetch("https://dead.example/x", any()) } returns
            GroundingResult.ModelOnly(GroundingResult.Reason.FETCH_FAILED)

        val result = orchestrator.fetchAll(
            listOf("https://a.example/uno", "https://dead.example/x", "https://c.example/tres"),
            4096,
        )

        assertThat(result).isInstanceOf(MultiUrlResult.Fused::class.java)
        val fused = result as MultiUrlResult.Fused
        assertThat(fused.okUrls)
            .containsExactly("https://a.example/uno", "https://c.example/tres").inOrder()
        assertThat(fused.skippedUrls).containsExactly("https://dead.example/x")
        assertThat(fused.block).doesNotContain("dead.example")
    }

    @Test
    fun `all-fail collapses to fetch-failed`() = runTest {
        coEvery { fetcher.fetch(any(), any()) } returns
            GroundingResult.ModelOnly(GroundingResult.Reason.FETCH_FAILED)

        val result = orchestrator.fetchAll(
            listOf("https://a.example/1", "https://b.example/2"),
            4096,
        )

        assertThat(result).isEqualTo(
            MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
        )
    }

    @Test
    fun `offline wins the worst-case collapse`() = runTest {
        coEvery { fetcher.fetch("https://a.example/1", any()) } returns
            GroundingResult.ModelOnly(GroundingResult.Reason.FETCH_FAILED)
        coEvery { fetcher.fetch("https://b.example/2", any()) } returns
            GroundingResult.ModelOnly(GroundingResult.Reason.OFFLINE)

        val result = orchestrator.fetchAll(
            listOf("https://a.example/1", "https://b.example/2"),
            4096,
        )

        assertThat(result).isEqualTo(
            MultiUrlResult.AllFailed(GroundingResult.Reason.OFFLINE),
        )
    }

    @Test
    fun `sixth and later urls are never fetched`() = runTest {
        val urls = (1..6).map { "https://n.example/pagina$it" }
        for (url in urls) {
            coEvery { fetcher.fetch(url, any()) } returns grounded(url)
        }

        val result = orchestrator.fetchAll(urls, 4096)

        val fused = result as MultiUrlResult.Fused
        assertThat(fused.okUrls).hasSize(5)
        coVerify(exactly = 0) { fetcher.fetch("https://n.example/pagina6", any()) }
    }

    @Test
    fun `duplicate urls are fetched once preserving first-seen order`() = runTest {
        stubOk("https://a.example/uno", "https://b.example/dos")

        val result = orchestrator.fetchAll(
            listOf("https://a.example/uno", "https://b.example/dos", "https://a.example/uno"),
            4096,
        )

        coVerify(exactly = 1) { fetcher.fetch("https://a.example/uno", any()) }
        val fused = result as MultiUrlResult.Fused
        assertThat(fused.okUrls)
            .containsExactly("https://a.example/uno", "https://b.example/dos").inOrder()
    }

    @Test
    fun `cancellation propagates instead of converting to model-only`() = runTest {
        stubOk("https://a.example/uno")
        coEvery { fetcher.fetch("https://slow.example/x", any()) } throws
            CancellationException("Stop")

        var thrown: CancellationException? = null
        try {
            orchestrator.fetchAll(
                listOf("https://a.example/uno", "https://slow.example/x"),
                4096,
            )
        } catch (e: CancellationException) {
            thrown = e
        }

        assertThat(thrown).isNotNull()
    }

    @Test
    fun `per-page budget split is threaded into every fetch`() = runTest {
        val urls = listOf("https://a.example/1", "https://b.example/2", "https://c.example/3")
        stubOk(*urls.toTypedArray())

        orchestrator.fetchAll(urls, 4096)

        val expected = GroundingBudget.perPageBudget(4096, 3)
        for (url in urls) {
            coVerify(exactly = 1) { fetcher.fetch(url, expected) }
        }
    }

    @Test
    fun `progress callback reports done counts up to the total`() = runTest {
        val urls = listOf("https://a.example/1", "https://b.example/2", "https://c.example/3")
        stubOk(*urls.toTypedArray())
        val seen = mutableListOf<Pair<Int, Int>>()

        orchestrator.fetchAll(urls, 4096) { done, total -> seen.add(done to total) }

        assertThat(seen).hasSize(3)
        assertThat(seen.last()).isEqualTo(3 to 3)
        assertThat(seen.map { it.second }.toSet()).containsExactly(3)
    }

    @Test
    fun `redirect preserves resolved url text and ok status in details`() = runTest {
        // CR-01: pasted short URL resolves post-redirect; the fusion-time
        // details union must carry the resolved URL with OK + text (never a
        // pasted-key lookup that would record OMITIDA and drop the text).
        val pasted = "http://short.example/x"
        val resolved = "https://cdn.example/final"
        coEvery { fetcher.fetch(pasted, any()) } returns grounded(resolved)
        coEvery { fetcher.fetch("https://dead.example/x", any()) } returns
            GroundingResult.ModelOnly(GroundingResult.Reason.FETCH_FAILED)

        val result = orchestrator.fetchAll(listOf(pasted, "https://dead.example/x"), 4096)

        val fused = result as MultiUrlResult.Fused
        assertThat(fused.okUrls).containsExactly(resolved)
        assertThat(fused.details.map { it.url }).containsExactly(
            resolved,
            "https://dead.example/x",
        ).inOrder()
        assertThat(fused.details[0].status)
            .isEqualTo(com.warped.domain.model.GroundedSourceStatus.OK)
        assertThat(fused.details[0].extractedText).isEqualTo("Texto de $resolved.")
        assertThat(fused.details[1].status)
            .isEqualTo(com.warped.domain.model.GroundedSourceStatus.OMITIDA)
    }
}
