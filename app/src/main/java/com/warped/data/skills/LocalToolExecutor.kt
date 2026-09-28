package com.warped.data.skills

import com.warped.domain.skills.SkillIds
import com.warped.domain.skills.ToolExecutor
import com.warped.domain.skills.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 47-02 (HARD-02, T-47-06/T-47-07/T-47-08): real [ToolExecutor] behind the
 * 47-01 interface. Plan 02 swaps the [com.warped.di.SkillsModule] binding
 * from `NoopToolExecutor` to this class (no call-site rewiring).
 *
 * Trust boundary:
 * - Allowlisted skill-id dispatch ONLY — unknown model-requested names
 *   return Failure and are never executed (ASVS V4).
 * - Args are schema-validated against the shared [SkillDescriptor] ParamSpecs
 *   before dispatch (ASVS V5): required params present, string-typed,
 *   within maxLength caps.
 * - Results are untrusted text: truncated + control-char stripped before
 *   return (Pitfall 6).
 * - Never throws: catch-all maps to Failure. Logs carry the tool NAME only —
 *   raw args never reach logs (T-47-08).
 * - Runs on [Dispatchers.Default], never Main (T-47-05 DoS guard).
 */
@Singleton
class LocalToolExecutor @Inject constructor() : ToolExecutor {

    override suspend fun execute(name: String, argsJson: String): ToolResult =
        withContext(Dispatchers.Default) {
            try {
                dispatch(name, argsJson)
            } catch (e: Exception) {
                Timber.w("LocalToolExecutor: $name failed (${e.javaClass.simpleName})")
                ToolResult.Failure("tool failed")
            }
        }

    private fun dispatch(name: String, argsJson: String): ToolResult {
        val descriptor = skillDescriptor(name)
            ?: return ToolResult.Failure("unknown tool").also {
                Timber.w("LocalToolExecutor: rejected unknown tool request")
            }
        val args: JsonObject = try {
            Json.parseToJsonElement(argsJson).jsonObject
        } catch (e: Exception) {
            return ToolResult.Failure("invalid arguments")
        }
        // Schema validation against ParamSpecs (ASVS V5).
        val values = mutableMapOf<String, String>()
        for (param in descriptor.params) {
            val element = args[param.name]
            if (element == null) {
                if (param.required) {
                    return ToolResult.Failure("missing ${param.name}")
                }
                continue
            }
            if (element !is JsonPrimitive || !element.isString) {
                return ToolResult.Failure("invalid ${param.name}")
            }
            val value = element.jsonPrimitive.content
            if (param.maxLength != null && value.length > param.maxLength) {
                return ToolResult.Failure("${param.name} too long")
            }
            values[param.name] = value
        }
        Timber.d("LocalToolExecutor: executing %s", descriptor.id)
        val raw: ToolResult = when (descriptor.id) {
            SkillIds.CALCULATOR -> calculateExpression(values["expression"].orEmpty())
            SkillIds.CURRENT_TIME -> getCurrentTime(values["timezone"])
            SkillIds.JSON_FORMATTER -> formatJson(values["json"].orEmpty())
            else -> return ToolResult.Failure("unknown tool")
        }
        return when (raw) {
            is ToolResult.Success -> ToolResult.Success(
                text = sanitizeToolOutput(raw.text),
                summary = summarizeToolOutput(raw.summary),
            )
            is ToolResult.Failure -> raw
        }
    }
}
