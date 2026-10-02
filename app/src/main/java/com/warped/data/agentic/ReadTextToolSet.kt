package com.warped.data.agentic

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

/**
 * Phase 70 (70-01): `read_text_file` tool schema for the local agentic loop.
 *
 * Third entry of the fixed allowlist (AGENT-04): joins [WebSearchToolSet]
 * and [WebFetchToolSet] — no other tool surface exists.
 *
 * The body is schema-only, mirroring `WebFetchToolSet` exactly: the manual
 * loop executes the turn-bound document block (fed per turn by the caller —
 * `LiteRTLmProvider.attachedDocumentBlock`, threaded from the VM's
 * attachment state in Plan 02) as a suspend call on `Dispatchers.IO` and
 * feeds the `DocumentPrompt` block string back via `Content.ToolResponse` —
 * the engine never invokes this body (`automaticToolCalling=false`). It
 * returns [HOST_EXECUTED] instead of throwing (47 never-throw lesson), and
 * contains NEVER `runBlocking` (Pitfall 3). Single `String` param keeps the
 * 0.17.1 nullable-optional fail-closed precedent; the filename is a
 * confirmation echo only (never a path — the loop never opens files).
 *
 * Signatures are provider-neutral on purpose: Plan 02 maps this 1:1 to
 * OpenAI `tools[]` / Anthropic native entries (name, description,
 * `{filename: string}` schema) reusing these exact constants.
 */
const val READ_TEXT_TOOL_DESCRIPTION =
    "Read the user-attached text document. " +
        "Call when the user's question refers to the attached document. " +
        "Returns the bounded document content."

const val READ_TEXT_FILENAME_DESCRIPTION =
    "The attached document filename for confirmation."

class ReadTextToolSet : ToolSet {

    // Snake-case tool name is the wire contract (engine dispatch +
    // remote tools[] mapping) — not a style violation.
    @Suppress("FunctionName")
    @Tool(description = READ_TEXT_TOOL_DESCRIPTION)
    fun read_text_file(
        @ToolParam(description = READ_TEXT_FILENAME_DESCRIPTION) filename: String,
    ): String = HOST_EXECUTED
}
