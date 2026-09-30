package com.warped.data.agentic

import com.warped.data.grounding.GroundingResult
import com.warped.data.grounding.MultiUrlResult
import com.warped.data.grounding.TavilySearchOutcome
import com.warped.domain.model.GroundedSource

/**
 * Schema-only `@Tool` bodies return this: the manual loop (plan 02)
 * executes every tool as a suspend repository call and feeds the result
 * back via `Content.ToolResponse`, so the engine never invokes a body.
 */
const val HOST_EXECUTED = "executed by host loop"

/**
 * Phase 56 (56-01): pure loop policy for the local agentic tool loop
 * (AGENT-02 result mapping, AGENT-04 allowlist).
 *
 * JVM-testable by design — NO `litertlm` imports (47 `ToolGating`
 * precedent). The provider loop in plan 02 consumes this object; every
 * function is total (never throws) so tool failures degrade to concise
 * English strings the model can continue from.
 *
 * Threat coverage (see plan threat model):
 * - T-56-01 (tampering, tool-result strings): [mapSearchOutcome] returns
 *   the fused `Source [N]` block verbatim — already passed through
 *   `WebContextSanitizer` + budget — never raw JSON.
 * - T-56-02 (tampering, fetch URL): [validateArgs] rejects non-http(s)
 *   URLs pre-fetch; `fetchAll`'s own allowlist verifies the public host
 *   before any socket.
 * - T-56-03 (info disclosure, tool args): inputs are arg-only
 *   (`query`/`url` authored by the model and validated here); conversation
 *   history is never serialized into tool calls.
 * - T-56-04 (denial, wallet): the [MAX_TOOL_CALLS] cap counts CALLS, and
 *   `web_search` is DDG-primary (keyless, no credit burn) — only the
 *   Tavily fallback leg burns 1 credit (basic-depth default) — so worst
 *   case stays 5 credits/message; blank queries short-circuit
 *   pre-socket (no credit burn).
 *
 * Executor gating order (cheapest first, plan 02 implements): grounding
 * armed check → validated-internet check (no socket offline) → repo call.
 */
object LocalToolLoop {

    /** Fixed allowlist tool names — dispatch is exact-match only (ASVS V4). */
    const val TOOL_WEB_SEARCH = "web_search"

    /** Fixed allowlist tool names — dispatch is exact-match only (ASVS V4). */
    const val TOOL_WEB_FETCH = "web_fetch"

    /**
     * Step cap: max TOOL CALLS per message (not rounds). Call-counting
     * bounds worst-case fallback credits 1:1 (5 calls = at most 5 Tavily
     * credits; DDG-primary calls burn none); multi-call rounds cannot
     * multiply the burn.
     */
    const val MAX_TOOL_CALLS = 5

    /**
     * Cap-reached continuation (locked: graceful, no hard error). Fed as
     * the final tool result so the model answers with gathered context.
     */
    const val CAP_REACHED_STRING =
        "Tool budget reached (5 calls). Answer with the context gathered so far."

    // Phase-55 copy twins. UI source of truth is
    // MessageBubble.ModelOnlyBanner; these carry the same actionable copy
    // into model context (tool-result strings), minus UI-only suffixes
    // ("Queued." belongs to the retry banner, never to the model).
    const val OFFLINE_STRING = "Offline. Model-only answer, no page content."
    const val FETCH_FAILED_STRING =
        "Couldn't read the page. Model-only answer — " +
            "check your connection or paste another link."
    const val MISSING_KEY_STRING =
        "No Tavily key saved. Model-only answer — get a key at " +
            "tavily.com and paste it in Settings > Web Search."
    const val INVALID_KEY_STRING =
        "Invalid Tavily key. Model-only answer — check the key " +
            "in Settings > Web Search."
    const val USAGE_LIMIT_STRING =
        "Tavily usage limit reached. Model-only answer — check " +
            "your plan usage and try again later."
    const val MODEL_ONLY_STRING =
        "Search returned no usable results. Answer from model knowledge."

    /** True once the call budget is exhausted — the next tool result must be [CAP_REACHED_STRING]. */
    fun isCapReached(callsUsed: Int): Boolean = callsUsed >= MAX_TOOL_CALLS

    /**
     * Phase 56 (56-02): single loop-arming predicate shared by the provider
     * (owns the authoritative per-turn decision) and ChatViewModel (must
     * skip its VM-side DDG pre-search when the loop is armed, or the
     * model would never need to search itself and the 5-call wallet bound
     * would stack on top of the pre-search call). All three inputs are
     * ANDed — grounding off, incapable model, or unvalidated internet each
     * independently force the exact pre-56 plain-turn behavior. The provider
     * being LiteRT-LM is structural (this object is only consumed there).
     */
    fun isLoopArmed(
        groundingOn: Boolean,
        supportsFunctionCalling: Boolean,
        hasValidatedInternet: Boolean,
    ): Boolean = groundingOn && supportsFunctionCalling && hasValidatedInternet

    /**
     * Phase 56 (56-02): transient status-row display string for a tool call
     * (Loop Visibility decision). User-facing copy only — never the raw
     * tool identifiers: search shows `Searching for "<query>"…`, fetch shows
     * `Reading <host>…` (host-only, never the full URL which may carry
     * tracking params or tokens). Display args are capped at
     * [MAX_STATUS_ARG_CHARS]; blank args fall back to the bare verb
     * ("Searching…"/"Reading…"). Null for unknown names (the driver falls
     * back to the raw name and the executor returns the unknown-tool error
     * without executing). Carries model-authored text only, never history.
     */
    fun statusDisplay(toolName: String, args: Map<String, Any?>): String? =
        when (mapToolCallName(toolName)) {
            TOOL_WEB_SEARCH -> {
                val query = (args["query"] as? String)?.trim().orEmpty()
                if (query.isEmpty()) "Searching…"
                else "Searching for \"${query.take(MAX_STATUS_ARG_CHARS)}\"…"
            }
            TOOL_WEB_FETCH -> {
                val url = (args["url"] as? String)?.trim().orEmpty()
                if (url.isEmpty()) "Reading…" else "Reading ${urlHost(url)}…"
            }
            else -> null
        }

    /**
     * Host-only display for fetch URLs (UI-REVIEW fix #1). Never throws —
     * unparseable input falls back to a scheme-stripped, delimiter-cut
     * prefix so the row can never leak a full URL.
     */
    private fun urlHost(url: String): String {
        val parsed = runCatching { java.net.URI(url).host }.getOrNull()
        if (!parsed.isNullOrBlank()) return parsed.take(MAX_STATUS_ARG_CHARS)
        val noScheme = url.substringAfter("://", url)
        val host = noScheme.split('/', '?', '#').firstOrNull().orEmpty()
        return host.ifEmpty { url }.take(MAX_STATUS_ARG_CHARS)
    }

    /** Calls left in the budget; floors at 0 for over-cap inputs. */
    fun callsRemaining(callsUsed: Int): Int = (MAX_TOOL_CALLS - callsUsed).coerceAtLeast(0)

    /**
     * Exact-name dispatch (AGENT-04, ASVS V4): returns the canonical tool
     * name for `web_search`/`web_fetch`, null for anything else. Unknown
     * names are never executed — the caller feeds [unknownToolMessage].
     */
    fun mapToolCallName(name: String): String? =
        when (name) {
            TOOL_WEB_SEARCH, TOOL_WEB_FETCH -> name
            else -> null
        }

    /** Concise English error for unknown tool names — never executed, never thrown. */
    fun unknownToolMessage(name: String): String =
        "Unknown tool \"$name\". Available tools: $TOOL_WEB_SEARCH, $TOOL_WEB_FETCH."

    /**
     * Quick-task (agentic-rows): the richer tool-call record threaded from
     * executors to the `ToolCompleted` emission. [text] is the exact string
     * the model continues from (identical to [mapSearchOutcome]/
     * [mapFetchResult] output); [sources] carries the structured details
     * alongside the mapped string in the SAME row shape (incl. OG columns)
     * as pre-search grounded turns — captured from the outcome object, never
     * re-parsed from the fused string. Empty when the call produced no
     * persistable rows.
     *
     * Quick-task (loop-images): [images] carries the fused Tavily `images[]`
     * URLs verbatim (http(s)-gated upstream). Fetch outcomes and
     * non-grounded search outcomes carry an empty list.
     */
    data class ToolCallOutcome(
        val text: String,
        val sources: List<GroundedSource> = emptyList(),
        val images: List<String> = emptyList(),
    )

    /**
     * Quick-task (agentic-rows): structured Fuentes details for a search
     * outcome — the `Fused.details` union verbatim (resolved URLs + texts +
     * OG columns, OK and OMITIDA rows), empty for every non-grounded
     * outcome (ModelOnly/key/limit paths persist no rows).
     */
    fun searchSources(outcome: TavilySearchOutcome): List<GroundedSource> =
        (outcome as? TavilySearchOutcome.Grounded)?.fused?.details.orEmpty()

    /**
     * Quick-task (loop-images): fused Tavily `images[]` URLs verbatim for a
     * search outcome — empty for every non-grounded outcome (ModelOnly/key/
     * limit paths carry no images). Sits next to [searchSources]; the 4 loop
     * executors populate `ToolCallOutcome.images` from this.
     */
    fun searchImages(outcome: TavilySearchOutcome): List<String> =
        (outcome as? TavilySearchOutcome.Grounded)?.fused?.images.orEmpty()

    /**
     * Quick-task (agentic-rows): structured Fuentes details for a fetch
     * result — the `Fused.details` union verbatim, empty for `AllFailed`.
     */
    fun fetchSources(result: MultiUrlResult): List<GroundedSource> =
        (result as? MultiUrlResult.Fused)?.details.orEmpty()

    /**
     * Maps every [TavilySearchOutcome] to the model-facing result string.
     * `Grounded` passes the fused block through VERBATIM (already
     * sanitized + budgeted, AGENT-02 trust falls out for free — never raw
     * JSON); key/limit outcomes reuse the actionable Phase-55 copy.
     */
    fun mapSearchOutcome(outcome: TavilySearchOutcome): String =
        when (outcome) {
            is TavilySearchOutcome.Grounded -> outcome.fused.block
            is TavilySearchOutcome.ModelOnly -> when (outcome.failed.reason) {
                GroundingResult.Reason.OFFLINE -> OFFLINE_STRING
                GroundingResult.Reason.FETCH_FAILED -> MODEL_ONLY_STRING
            }
            TavilySearchOutcome.MissingKey -> MISSING_KEY_STRING
            TavilySearchOutcome.InvalidKey -> INVALID_KEY_STRING
            TavilySearchOutcome.UsageLimit -> USAGE_LIMIT_STRING
        }

    /**
     * Maps `fetchAll` output the same way: fused blocks pass through
     * verbatim, total failure collapses with the OFFLINE distinction
     * preserved (offline short-circuit opens no socket).
     */
    fun mapFetchResult(result: MultiUrlResult): String =
        when (result) {
            is MultiUrlResult.Fused -> result.block
            is MultiUrlResult.AllFailed -> when (result.reason) {
                GroundingResult.Reason.OFFLINE -> OFFLINE_STRING
                GroundingResult.Reason.FETCH_FAILED -> FETCH_FAILED_STRING
            }
        }

    /**
     * Validates model-authored tool args BEFORE any socket or credit burn
     * (AGENT-02 input validation). Returns null when valid; otherwise the
     * short-circuit/error string the executor feeds back WITHOUT calling
     * the repository. Non-`String` arg values fail closed (0.17.1
     * nullable-optional precedent). Never throws.
     */
    fun validateArgs(toolName: String, args: Map<String, Any?>): String? {
        val canonical = mapToolCallName(toolName) ?: return unknownToolMessage(toolName)
        return when (canonical) {
            TOOL_WEB_SEARCH -> {
                val query = args["query"] as? String
                if (query.isNullOrBlank()) MODEL_ONLY_STRING else null
            }
            TOOL_WEB_FETCH -> {
                val url = (args["url"] as? String)?.trim().orEmpty()
                when {
                    url.isEmpty() ->
                        "Invalid URL. Provide a full page URL " +
                            "starting with http:// or https://."
                    !url.startsWith("http://", ignoreCase = true) &&
                        !url.startsWith("https://", ignoreCase = true) ->
                        "Unsupported URL \"${url.take(MAX_URL_IN_ERROR)}\". " +
                            "Only http:// and https:// pages can be fetched."
                    else -> null
                }
            }
            else -> unknownToolMessage(toolName)
        }
    }

    /**
     * Maps an unexpected tool failure (exception detail from the executor's
     * catch-all) to a concise single-line English string. Never throws —
     * executors never throw (47 never-throw lesson).
     */
    fun toolFailureMessage(detail: String): String {
        val clean = detail.trim().replace(FAILURE_WHITESPACE, " ").take(MAX_FAILURE_CHARS)
        return if (clean.isEmpty()) "Error: the tool call failed." else "Error: $clean"
    }

    private const val MAX_URL_IN_ERROR = 200
    private const val MAX_FAILURE_CHARS = 300
    private const val MAX_STATUS_ARG_CHARS = 80
    private val FAILURE_WHITESPACE = Regex("\\s+")
}
