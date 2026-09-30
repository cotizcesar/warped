package com.warped.domain.model

sealed interface StreamToken {
    data class Delta(val content: String) : StreamToken
    data class Done(val stats: String? = null, val reasoning: String? = null) : StreamToken
    data class Error(val message: String) : StreamToken

    /**
     * 47-02: live tool-execution signal. With `automaticToolCalling=true`
     * the engine emits no tool events, so each `@Tool` body posts
     * start/finish via `ToolEventSink` and the provider forwards them here.
     * Non-null [toolName] = tool running (drives `toolCallActive`
     * "Using …" row); null = clear. The 47-03 remote loop reuses this type.
     */
    data class ToolStatus(val toolName: String?) : StreamToken

    /**
     * Quick-task (agentic-rows): [sources] carries the per-call Fuentes
     * rows in the SAME row shape (incl. OG columns) as the pre-search
     * grounded turns — the ViewModel accumulates them across the turn and
     * persists via the identical `saveMessageWithSources` path on Done.
     * Empty when the call produced no persistable rows (validation
     * short-circuit, offline, cap string, key/limit outcomes). Local manual
     * loop emits this too (one per executed search/fetch call).
     *
     * Quick-task (loop-images): [images] carries the call's fused Tavily
     * `images[]` URLs verbatim — the ViewModel unions them across the turn
     * into the ephemeral `groundedImages` the grid reads. Empty for fetch
     * calls and non-grounded search outcomes.
     *
     * Explicitly NOT `role=tool` transcript rows (Phase 49 DEL-01: no
     * Role.TOOL rows are produced; legacy ones replay read-only) — the
     * Fuentes-rows persistence is the deliverable.
     */
    data class ToolCompleted(
        val toolId: String,
        val summary: String,
        val errorReason: String? = null,
        val sources: List<GroundedSource> = emptyList(),
        val images: List<String> = emptyList(),
    ) : StreamToken

    /**
     * Phase 57 UI-review fix: typed tools-unsupported notice. The remote
     * attempt-then-fallback drivers emit this (instead of an
     * `Error` carrying render copy) when a server rejects `tools[]` — the
     * turn retries once without tools and completes model-only. Routing on
     * a type (not `Error.message == <copy>`) keeps copy edits from
     * silently re-routing the informational notice into the hard-error
     * banner. Passes through think-strip/passthrough maps untouched.
     */
    data object ToolsUnsupported : StreamToken
}
