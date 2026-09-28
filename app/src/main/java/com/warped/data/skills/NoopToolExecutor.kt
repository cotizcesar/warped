package com.warped.data.skills

import com.warped.domain.skills.ToolExecutor
import com.warped.domain.skills.ToolResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 47-01 placeholder binding so the [ToolExecutor] graph resolves before
 * Plan 02 lands the real local executor. Plan 02 replaces this binding
 * with the local `@Tool`-dispatching implementation.
 */
@Singleton
class NoopToolExecutor @Inject constructor() : ToolExecutor {
    override suspend fun execute(name: String, argsJson: String): ToolResult =
        ToolResult.Failure("Tool execution not yet available")
}
