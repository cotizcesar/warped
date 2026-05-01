package com.warped.data.remote.network

class SseParser {
    private val buffer = StringBuilder()

    fun feed(chunk: String): List<SseEvent> {
        buffer.append(chunk)
        val events = mutableListOf<SseEvent>()

        var doubleNewline = buffer.indexOf("\n\n")
        while (doubleNewline != -1) {
            val raw = buffer.substring(0, doubleNewline)
            buffer.delete(0, doubleNewline + 2)

            val event = parse(raw)
            if (event != null) {
                events.add(event)
            }

            doubleNewline = buffer.indexOf("\n\n")
        }

        return events
    }

    private fun parse(raw: String): SseEvent? {
        val lines = raw.split("\n")
        var data: String? = null
        var event: String? = null

        for (line in lines) {
            when {
                line.startsWith("data:") -> {
                    data = if (data == null) line.removePrefix("data:").trimStart()
                           else data + "\n" + line.removePrefix("data:").trimStart()
                }
                line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                line.startsWith(":") -> { /* comment, skip */ }
            }
        }

        return if (data != null) SseEvent(data, event) else null
    }

    fun reset() {
        buffer.clear()
    }
}
