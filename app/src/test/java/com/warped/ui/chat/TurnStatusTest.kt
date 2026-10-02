package com.warped.ui.chat

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Quick-task (unified-turn-status): truth table for [resolveTurnStatus].
 * Priority under test: model loading > tool > fetch/search > streaming
 * gap; null renders nothing.
 */
class TurnStatusTest {

    private fun loading(url: String) = SourceFetchState(url, PerSourceStatus.LOADING)

    private fun fanoutProgress(done: Int = 2, total: Int = 5) = WebFetchProgress(
        done = done,
        total = total,
        perSource = listOf(loading("https://a.example/x"), loading("https://b.example/y")),
    )

    // Search-path shape: done/total set, perSource EMPTY (the "0 of N" lie —
    // the resolver must never surface these counts).
    private fun searchProgress() = WebFetchProgress(done = 0, total = 5, perSource = emptyList())

    @Test
    fun `loading model wins over tool fetch and gap`() {
        assertThat(
            resolveTurnStatus(
                toolCallActive = "Searching for cats…",
                isFetchingWeb = true,
                progress = fanoutProgress(),
                isStreamingGap = true,
                isLoadingModel = true,
                loadingModelName = "gemma-3-1b.itertlm",
                loadingFirstTime = true,
            ),
        ).isEqualTo(TurnStatus.LoadingModel("gemma-3-1b.itertlm", true))
    }

    @Test
    fun `loading model without name still resolves`() {
        assertThat(
            resolveTurnStatus(
                toolCallActive = null,
                isFetchingWeb = false,
                progress = null,
                isStreamingGap = false,
                isLoadingModel = true,
            ),
        ).isEqualTo(TurnStatus.LoadingModel("", false))
    }

    @Test
    fun `repeat load carries firstTime false`() {
        assertThat(
            resolveTurnStatus(
                toolCallActive = null,
                isFetchingWeb = false,
                progress = null,
                isStreamingGap = false,
                isLoadingModel = true,
                loadingModelName = "gemma-3-1b.itertlm",
                loadingFirstTime = false,
            ),
        ).isEqualTo(TurnStatus.LoadingModel("gemma-3-1b.itertlm", false))
    }

    @Test
    fun `tool set wins over fetch and gap`() {
        assertThat(
            resolveTurnStatus(
                toolCallActive = "Searching for cats…",
                isFetchingWeb = true,
                progress = fanoutProgress(),
                isStreamingGap = true,
            ),
        ).isEqualTo(TurnStatus.Tool("Searching for cats…"))
    }

    @Test
    fun `tool text passes through verbatim`() {
        val text = "Reading example.com…"
        assertThat(
            resolveTurnStatus(
                toolCallActive = text,
                isFetchingWeb = false,
                progress = null,
                isStreamingGap = false,
            ),
        ).isEqualTo(TurnStatus.Tool(text))
    }

    @Test
    fun `fetch with perSource and total greater than one is fanout with verbatim counts`() {
        assertThat(
            resolveTurnStatus(
                toolCallActive = null,
                isFetchingWeb = true,
                progress = fanoutProgress(done = 2, total = 5),
                isStreamingGap = false,
            ),
        ).isEqualTo(TurnStatus.FetchFanout(done = 2, total = 5))
    }

    @Test
    fun `fetch with perSource and total of one is single`() {
        assertThat(
            resolveTurnStatus(
                toolCallActive = null,
                isFetchingWeb = true,
                progress = WebFetchProgress(done = 0, total = 1, perSource = listOf(loading("https://a.example/x"))),
                isStreamingGap = false,
            ),
        ).isEqualTo(TurnStatus.FetchSingle)
    }

    @Test
    fun `fetch with null progress is single legacy fallback`() {
        assertThat(
            resolveTurnStatus(
                toolCallActive = null,
                isFetchingWeb = true,
                progress = null,
                isStreamingGap = false,
            ),
        ).isEqualTo(TurnStatus.FetchSingle)
    }

    @Test
    fun `fetch with empty perSource is searching regardless of done and total`() {
        assertThat(
            resolveTurnStatus(
                toolCallActive = null,
                isFetchingWeb = true,
                progress = searchProgress(),
                isStreamingGap = false,
            ),
        ).isEqualTo(TurnStatus.Searching)
    }

    @Test
    fun `fetch beats the streaming gap`() {
        assertThat(
            resolveTurnStatus(
                toolCallActive = null,
                isFetchingWeb = true,
                progress = searchProgress(),
                isStreamingGap = true,
            ),
        ).isEqualTo(TurnStatus.Searching)
    }

    @Test
    fun `gap alone is the thinking gap`() {
        assertThat(
            resolveTurnStatus(
                toolCallActive = null,
                isFetchingWeb = false,
                progress = null,
                isStreamingGap = true,
            ),
        ).isEqualTo(TurnStatus.ThinkingGap)
    }

    @Test
    fun `all false renders nothing`() {
        assertThat(
            resolveTurnStatus(
                toolCallActive = null,
                isFetchingWeb = false,
                progress = null,
                isStreamingGap = false,
            ),
        ).isNull()
    }

    @Test
    fun `stale progress without fetch renders nothing`() {
        assertThat(
            resolveTurnStatus(
                toolCallActive = null,
                isFetchingWeb = false,
                progress = fanoutProgress(),
                isStreamingGap = false,
            ),
        ).isNull()
    }
}
