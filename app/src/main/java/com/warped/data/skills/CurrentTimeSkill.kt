package com.warped.data.skills

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
