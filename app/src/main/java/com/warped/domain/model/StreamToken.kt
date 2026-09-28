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
}
