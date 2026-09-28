package com.warped.data.skills

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.warped.domain.skills.SkillIds
import com.warped.domain.skills.ToolEventSink
import com.warped.domain.skills.ToolResult
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 47-02 (HARD-02): CurrentTime pure tool body.
 *
 * [getCurrentTime] is PURE SYNC (reads the clock only) and NEVER throws out.
 * Timezone ids are allowlisted via [ZoneId.of] (unknown → Failure); formatting
 * uses a fixed pattern + [Locale.US], never default-locale `DateFormat`, so
 * tool-result output is deterministic across devices. The `@Tool` ToolSet
 * wrapper lands in Task 2 and delegates here.
 */
const val TIMEZONE_MAX_CHARS = 100

private val TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd HH:mm:ss z", Locale.US)

fun getCurrentTime(timezone: String?): ToolResult {
    return try {
        val tz = timezone?.trim().orEmpty()
        if (tz.length > TIMEZONE_MAX_CHARS) return ToolResult.Failure("unknown timezone")
        val zone: ZoneId = if (tz.isEmpty()) {
            ZoneId.systemDefault()
        } else {
            try {
                ZoneId.of(tz)
            } catch (e: Exception) {
                return ToolResult.Failure("unknown timezone")
            }
        }
        val text = "${ZonedDateTime.now(zone).format(TIME_FORMATTER)} (${zone.id})"
        ToolResult.Success(text = text, summary = summarizeToolOutput(text))
    } catch (e: Exception) {
        // Belt-and-braces: the trust boundary guarantees no throw-out.
        ToolResult.Failure("time unavailable")
    }
}

/**
 * 47-02 (D-04): CurrentTime `@Tool` via [ToolSet]. Descriptions import the
 * Plan 01 descriptor constants verbatim (SKILLS-09 no-drift). The timezone
 * param is nullable-with-default (engine-optional); blank/absent resolves to
 * the device zone in [getCurrentTime]. Same [ToolEventSink] + never-throw
 * contract as [CalculatorToolSet].
 */
class CurrentTimeToolSet(private val events: ToolEventSink) : ToolSet {
    @Tool(description = TIME_TOOL_DESCRIPTION)
    fun currentTime(
        @ToolParam(description = TIME_TIMEZONE_DESCRIPTION) timezone: String? = null,
    ): String {
        events.onStart(SkillIds.CURRENT_TIME)
        return try {
            when (val r = getCurrentTime(timezone)) {
                is ToolResult.Success -> sanitizeToolOutput(r.text)
                is ToolResult.Failure -> "Error: ${r.reason}"
            }
        } catch (e: Exception) {
            "Error: time unavailable"
        } finally {
            events.onFinish(SkillIds.CURRENT_TIME)
        }
    }
}
