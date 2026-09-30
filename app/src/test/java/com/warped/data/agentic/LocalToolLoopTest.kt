package com.warped.data.agentic

import com.google.common.truth.Truth.assertThat
import com.warped.data.grounding.GroundingResult
import com.warped.data.grounding.MultiUrlResult
import com.warped.data.grounding.TavilySearchOutcome
import org.junit.jupiter.api.Test

/**
 * Phase 56 (56-01): exit gates for the pure loop policy.
 *
 * Every case is JVM-local (no engine, no network, no key): the policy maps
 * outcomes and validates args without ever opening a socket or burning a
 * Tavily credit.
 */
class LocalToolLoopTest {

    private fun fusedBlock(block: String = "Source [1] (https://example.com): text"): MultiUrlResult.Fused =
        MultiUrlResult.Fused(
            block = block,
            okUrls = listOf("https://example.com"),
            skippedUrls = emptyList(),
        )

    // Cap: counts CALLS, floors, continuation string.

    @Test
    fun `cap counts calls with budget exhausted at five`() {
        assertThat(LocalToolLoop.isCapReached(0)).isFalse()
        assertThat(LocalToolLoop.isCapReached(4)).isFalse()
        assertThat(LocalToolLoop.isCapReached(5)).isTrue()
        assertThat(LocalToolLoop.isCapReached(6)).isTrue()
    }

    @Test
    fun `two calls in one message consume two of five`() {
        var used = 0
        used += 2 // one message emitting two parallel ToolCalls
        assertThat(LocalToolLoop.callsRemaining(used)).isEqualTo(3)
        assertThat(LocalToolLoop.isCapReached(used)).isFalse()
        used += 3
        assertThat(LocalToolLoop.isCapReached(used)).isTrue()
        assertThat(LocalToolLoop.callsRemaining(used)).isEqualTo(0)
    }

    @Test
    fun `remaining floors at zero past the cap`() {
        assertThat(LocalToolLoop.callsRemaining(99)).isEqualTo(0)
    }

    @Test
    fun `cap reached string instructs continuation with gathered context`() {
        val s = LocalToolLoop.CAP_REACHED_STRING
        assertThat(s).isNotEmpty()
        assertThat(s).contains("gathered")
    }

    // Search outcome mapping (AGENT-02 trust).

    @Test
    fun `grounded search outcome passes fused block verbatim`() {
        val block = "Source [1] (https://a.example): one\nSource [2] (https://b.example): two"
        val out = TavilySearchOutcome.Grounded(fusedBlock(block))

        assertThat(LocalToolLoop.mapSearchOutcome(out)).isEqualTo(block)
    }

    @Test
    fun `every search outcome maps to its expected string class`() {
        assertThat(
            LocalToolLoop.mapSearchOutcome(
                TavilySearchOutcome.ModelOnly(
                    MultiUrlResult.AllFailed(GroundingResult.Reason.OFFLINE),
                ),
            ),
        ).isEqualTo(LocalToolLoop.OFFLINE_STRING)

        assertThat(
            LocalToolLoop.mapSearchOutcome(
                TavilySearchOutcome.ModelOnly(
                    MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
                ),
            ),
        ).isEqualTo(LocalToolLoop.MODEL_ONLY_STRING)

        assertThat(LocalToolLoop.mapSearchOutcome(TavilySearchOutcome.MissingKey))
            .contains("Settings > Web Search")
        assertThat(LocalToolLoop.mapSearchOutcome(TavilySearchOutcome.MissingKey))
            .contains("tavily.com")
        assertThat(LocalToolLoop.mapSearchOutcome(TavilySearchOutcome.InvalidKey))
            .contains("Settings > Web Search")
        assertThat(LocalToolLoop.mapSearchOutcome(TavilySearchOutcome.UsageLimit))
            .contains("plan usage")
    }

    // Fetch result mapping (OFFLINE collapse preserved).

    @Test
    fun `fused fetch result passes block verbatim`() {
        val block = "Source [1] (https://c.example): page text"

        assertThat(LocalToolLoop.mapFetchResult(fusedBlock(block))).isEqualTo(block)
    }
    @Test
    fun `failed fetch collapses with offline distinction preserved`() {
        assertThat(
            LocalToolLoop.mapFetchResult(
                MultiUrlResult.AllFailed(GroundingResult.Reason.OFFLINE),
            ),
        ).isEqualTo(LocalToolLoop.OFFLINE_STRING)
        assertThat(
            LocalToolLoop.mapFetchResult(
                MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
            ),
        ).isEqualTo(LocalToolLoop.FETCH_FAILED_STRING)
    }

    // Arg validation (AGENT-02 input validation, pre-socket).

    @Test
    fun `blank query short-circuits without calling search`() {
        // Pure policy: a non-null return means the executor feeds the
        // string back and never touches the repository (no socket, no
        // Tavily credit burn).
        assertThat(
            LocalToolLoop.validateArgs("web_search", mapOf("query" to "   ")),
        ).isEqualTo(LocalToolLoop.MODEL_ONLY_STRING)
        assertThat(LocalToolLoop.validateArgs("web_search", emptyMap()))
            .isEqualTo(LocalToolLoop.MODEL_ONLY_STRING)
        // Non-String values fail closed (0.17.1 nullable-optional precedent).
        assertThat(LocalToolLoop.validateArgs("web_search", mapOf("query" to 42)))
            .isEqualTo(LocalToolLoop.MODEL_ONLY_STRING)
        assertThat(LocalToolLoop.validateArgs("web_search", mapOf("query" to null)))
            .isEqualTo(LocalToolLoop.MODEL_ONLY_STRING)
    }

    @Test
    fun `valid search args pass validation`() {
        assertThat(
            LocalToolLoop.validateArgs("web_search", mapOf("query" to "gemma 4 release date")),
        ).isNull()
    }

    @Test
    fun `non-http url rejected pre-fetch`() {
        assertThat(
            LocalToolLoop.validateArgs("web_fetch", mapOf("url" to "file:///etc/passwd")),
        ).contains("Only http")
        assertThat(
            LocalToolLoop.validateArgs("web_fetch", mapOf("url" to "ftp://files.example/x")),
        ).contains("Only http")
        assertThat(LocalToolLoop.validateArgs("web_fetch", mapOf("url" to "  ")))
            .contains("Invalid URL")
        assertThat(LocalToolLoop.validateArgs("web_fetch", emptyMap()))
            .contains("Invalid URL")
    }

    @Test
    fun `http and https urls pass validation`() {
        assertThat(
            LocalToolLoop.validateArgs("web_fetch", mapOf("url" to "https://example.com/a")),
        ).isNull()
        assertThat(
            LocalToolLoop.validateArgs("web_fetch", mapOf("url" to "http://example.com/a")),
        ).isNull()
        assertThat(
            LocalToolLoop.validateArgs("web_fetch", mapOf("url" to "HTTPS://example.com/a")),
        ).isNull()
    }

    // Exact-name dispatch (AGENT-04, ASVS V4).

    @Test
    fun `unknown tool name returns error string never executed`() {
        assertThat(LocalToolLoop.mapToolCallName("web_search")).isEqualTo("web_search")
        assertThat(LocalToolLoop.mapToolCallName("web_fetch")).isEqualTo("web_fetch")
        assertThat(LocalToolLoop.mapToolCallName("shell_exec")).isNull()
        // Exact match only — near-miss casing never dispatches.
        assertThat(LocalToolLoop.mapToolCallName("Web_Search")).isNull()
        assertThat(LocalToolLoop.mapToolCallName("")).isNull()

        val err = LocalToolLoop.validateArgs("shell_exec", emptyMap())
        assertThat(err).contains("Unknown tool")
        assertThat(err).contains("web_search")
        assertThat(err).contains("web_fetch")
    }

    // Never-throw discipline (47 lesson).

    @Test
    fun `tool failure maps to concise english error string`() {
        assertThat(LocalToolLoop.toolFailureMessage("timeout")).isEqualTo("Error: timeout")
        assertThat(LocalToolLoop.toolFailureMessage(""))
            .isEqualTo("Error: the tool call failed.")
        assertThat(LocalToolLoop.toolFailureMessage("   "))
            .isEqualTo("Error: the tool call failed.")
    }

    @Test
    fun `tool failure message collapses whitespace and truncates`() {
        val multi = LocalToolLoop.toolFailureMessage("line one\n  line\ttwo")
        assertThat(multi).isEqualTo("Error: line one line two")
        assertThat(multi).doesNotContain("\n")

        val long = LocalToolLoop.toolFailureMessage("x".repeat(9999))
        assertThat(long.length).isAtMost("Error: ".length + 300)
    }

    @Test
    fun `mapping functions never throw on edge inputs`() {
        // If any of these threw, the test runner reports it — the
        // assertions below just pin the calls as non-null strings.
        assertThat(LocalToolLoop.validateArgs("", mapOf("" to ""))).isNotNull()
        assertThat(LocalToolLoop.toolFailureMessage("\u0000\u0007")).isNotNull()
        assertThat(LocalToolLoop.unknownToolMessage("")).isNotNull()
        assertThat(LocalToolLoop.mapFetchResult(fusedBlock(""))).isNotNull()
    }

    // Structured source extraction (quick-task agentic-rows): the SAME
    // outcome object yields the model-facing text AND the persistable rows
    // — never re-parsed from the fused string.

    @Test
    fun `grounded search outcome yields fused details verbatim incl og columns`() {
        val details = listOf(
            com.warped.domain.model.GroundedSource(
                url = "https://a.example/uno",
                extractedText = "Texto a.",
                status = com.warped.domain.model.GroundedSourceStatus.OK,
                ogTitle = "Title A",
                ogDescription = "Desc A",
                ogImageUrl = "https://a.example/img.png",
            ),
            com.warped.domain.model.GroundedSource(
                url = "https://dead.example/x",
                extractedText = null,
                status = com.warped.domain.model.GroundedSourceStatus.OMITIDA,
            ),
        )
        val outcome = TavilySearchOutcome.Grounded(
            MultiUrlResult.Fused(
                block = "BLOQUE",
                okUrls = listOf("https://a.example/uno"),
                skippedUrls = listOf("https://dead.example/x"),
                details = details,
            ),
        )

        // Same row shape incl. OG columns, same order.
        assertThat(LocalToolLoop.searchSources(outcome)).isEqualTo(details)
    }

    @Test
    fun `non-grounded search outcomes yield no sources`() {
        assertThat(
            LocalToolLoop.searchSources(
                TavilySearchOutcome.ModelOnly(
                    MultiUrlResult.AllFailed(GroundingResult.Reason.OFFLINE),
                ),
            ),
        ).isEmpty()
        assertThat(LocalToolLoop.searchSources(TavilySearchOutcome.MissingKey)).isEmpty()
        assertThat(LocalToolLoop.searchSources(TavilySearchOutcome.InvalidKey)).isEmpty()
        assertThat(LocalToolLoop.searchSources(TavilySearchOutcome.UsageLimit)).isEmpty()
    }

    @Test
    fun `fused fetch result yields details - failed fetch yields none`() {
        val details = listOf(
            com.warped.domain.model.GroundedSource(
                url = "https://c.example/p",
                extractedText = "Page.",
                status = com.warped.domain.model.GroundedSourceStatus.OK,
                ogTitle = "C",
            ),
        )
        assertThat(
            LocalToolLoop.fetchSources(
                MultiUrlResult.Fused(
                    block = "BLOQUE",
                    okUrls = listOf("https://c.example/p"),
                    skippedUrls = emptyList(),
                    details = details,
                ),
            ),
        ).isEqualTo(details)
        assertThat(
            LocalToolLoop.fetchSources(
                MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
            ),
        ).isEmpty()
    }

    // Quick-task (loop-images): fused Tavily `images[]` carried verbatim.

    @Test
    fun `grounded search outcome yields fused images verbatim`() {
        val outcome = TavilySearchOutcome.Grounded(
            MultiUrlResult.Fused(
                block = "BLOQUE",
                okUrls = listOf("https://a.example/uno"),
                skippedUrls = emptyList(),
                images = listOf(
                    "https://a.example/img1.png",
                    "https://a.example/img2.png",
                ),
            ),
        )

        assertThat(LocalToolLoop.searchImages(outcome)).containsExactly(
            "https://a.example/img1.png",
            "https://a.example/img2.png",
        ).inOrder()
    }

    @Test
    fun `non-grounded search outcomes yield no images`() {
        assertThat(
            LocalToolLoop.searchImages(
                TavilySearchOutcome.ModelOnly(
                    MultiUrlResult.AllFailed(GroundingResult.Reason.OFFLINE),
                ),
            ),
        ).isEmpty()
        assertThat(LocalToolLoop.searchImages(TavilySearchOutcome.MissingKey)).isEmpty()
        assertThat(LocalToolLoop.searchImages(TavilySearchOutcome.InvalidKey)).isEmpty()
        assertThat(LocalToolLoop.searchImages(TavilySearchOutcome.UsageLimit)).isEmpty()
        // Grounded without a Tavily image leg carries an empty list.
        assertThat(
            LocalToolLoop.searchImages(
                TavilySearchOutcome.Grounded(
                    MultiUrlResult.Fused(
                        block = "BLOQUE",
                        okUrls = listOf("https://a.example/uno"),
                        skippedUrls = emptyList(),
                    ),
                ),
            ),
        ).isEmpty()
    }
}
