package com.warped.domain.skills

/**
 * 47-01: tool execution contracts. Interface only — implementations belong
 * to Plans 02 (local @Tools) and 03 (remote loop). The confirmation gate
 * later lands as a decorator, without rewiring.
 */
interface ToolExecutor {
    /** Execute a named tool with its raw JSON arguments object. Never throws. */
    suspend fun execute(name: String, argsJson: String): ToolResult
}

sealed interface ToolResult {
    /** [text] is the full result; [summary] is the ≤200-char transcript form. */
    data class Success(val text: String, val summary: String) : ToolResult
    data class Failure(val reason: String) : ToolResult
}

/**
 * Non-blocking execution signals. With `automaticToolCalling=true` the engine
 * executes tools internally and the `Flow<Message>` carries only text, so each
 * `@Tool` body posts start/finish here (RESEARCH Pitfall 4, option 1) to drive
 * the `toolCallActive` status row and transcript capture.
 */
interface ToolEventSink {
    fun onStart(toolName: String)
    fun onFinish(toolName: String)
}
