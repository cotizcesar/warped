package com.warped.data.agentic

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

/**
 * Phase 56 (56-01): `web_fetch` tool schema for the local agentic loop.
 *
 * Fixed three-tool allowlist (AGENT-04): this file plus [WebSearchToolSet]
 * and [ReadTextToolSet] are the ONLY tool schemas in the app — no
 * file/system/shell surface exists.
 *
 * The body is schema-only: the manual loop (plan 02) executes
 * `MultiUrlFetcher.fetchAll()` as a suspend call on `Dispatchers.IO` and
 * feeds the fused-block string back via `Content.ToolResponse` — the
 * engine never invokes this body (`automaticToolCalling=false`). It returns
 * [HOST_EXECUTED] instead of throwing (47 never-throw lesson), and contains
 * NEVER `runBlocking` (Pitfall 3).
 *
 * Signatures are provider-neutral on purpose: Phase 57 maps this 1:1 to an
 * OpenAI `tools[]` entry (name, description, `{url: string}` schema).
 * Param names stay single-word lowercase (`url`) to dodge the SDK
 * camel-to-snake mapping; only `String` params (0.17.1 nullable-optional
 * fail-closed precedent from 47-REVIEW).
 */
const val WEB_FETCH_TOOL_DESCRIPTION =
    "Fetch a web page's readable text. " +
        "Call with a full https URL from search results or the user. " +
        "Returns the page content as a numbered source."

const val WEB_FETCH_URL_DESCRIPTION =
    "The full page URL, starting with http:// or https://."

class WebFetchToolSet : ToolSet {

    // Snake-case tool name is the wire contract (engine dispatch +
    // Phase-57 tools[] mapping) — not a style violation.
    @Suppress("FunctionName")
    @Tool(description = WEB_FETCH_TOOL_DESCRIPTION)
    fun web_fetch(
        @ToolParam(description = WEB_FETCH_URL_DESCRIPTION) url: String,
    ): String = HOST_EXECUTED
}
