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
     * 47-03: a remote-loop tool finished. Carries the persistable record
     * ([toolId] + ≤200-char [summary]) so the ViewModel can write the
     * `role=tool` transcript row on turn Done, plus the optional
     * [errorReason] driving the `"{Display} failed: …"` error row.
     * Local automatic-mode path does not emit this (results are
     * engine-internal; persistence deferred per 47-02).
     */
    data class ToolCompleted(
        val toolId: String,
        val summary: String,
        val errorReason: String? = null,
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
