package com.warped.data.agentic

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

/**
 * Phase 56 (56-01): `web_search` tool schema for the local agentic loop.
 *
 * Fixed two-tool allowlist (AGENT-04): this file plus [WebFetchToolSet] are
 * the ONLY tool schemas in the app — no file/system/shell surface exists.
 *
 * The body is schema-only: the manual loop (plan 02) executes
 * `DuckDuckGoSearchRepository.search()` (DDG-only — keyless outcome)
 * as a suspend call on `Dispatchers.IO`
 * and feeds the fused-block string back via `Content.ToolResponse` — the
 * engine never invokes this body (`automaticToolCalling=false`). It returns
 * [HOST_EXECUTED] instead of throwing (47 never-throw lesson: a throw
 * across JNI kills the conversation), and contains NEVER `runBlocking`
 * (Pitfall 3: `ReflectionTool.execute` is synchronous; network I/O here
 * would strand Stop on engine threads).
 *
 * Signatures are provider-neutral on purpose: Phase 57 maps this 1:1 to an
 * OpenAI `tools[]` entry (name, description, `{query: string}` schema).
 * Param names stay single-word lowercase (`query`) to dodge the SDK
 * camel-to-snake mapping; only `String` params (0.17.1 nullable-optional
 * fail-closed precedent from 47-REVIEW).
 */
const val WEB_SEARCH_TOOL_DESCRIPTION =
    "Search the web for current or external facts. " +
        "Call when the user's question needs information beyond the model's knowledge. " +
        "If a new question needs facts not covered by earlier results, " +
        "call again instead of answering from stale results. " +
        "Returns numbered sources."

const val WEB_SEARCH_QUERY_DESCRIPTION =
    "The search query. Be specific; include key entities."

class WebSearchToolSet : ToolSet {

    // Snake-case tool name is the wire contract (engine dispatch +
    // Phase-57 tools[] mapping) — not a style violation.
    @Suppress("FunctionName")
    @Tool(description = WEB_SEARCH_TOOL_DESCRIPTION)
    fun web_search(
        @ToolParam(description = WEB_SEARCH_QUERY_DESCRIPTION) query: String,
    ): String = HOST_EXECUTED
}
