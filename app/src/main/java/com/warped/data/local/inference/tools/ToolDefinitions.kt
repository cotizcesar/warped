package com.warped.data.local.inference.tools

import kotlinx.serialization.Serializable

enum class ToolCategory(val displayName: String) {
    PRODUCTIVITY("Productivity"),
    INFORMATION("Information"),
    DEVICE("Device"),
    UTILITIES("Utilities"),
    LOCATION("Location")
}

@Serializable
data class ToolDefinition(
    val id: String,
    val name: String,
    val description: String,
    val category: ToolCategory,
    val tokenEstimate: Int,
    val defaultEnabled: Boolean,
    val openApiSchema: String
)

object ToolDefinitions {

    val all: List<ToolDefinition> = listOf(
        // === PRODUCTIVITY ===
        ToolDefinition(
            id = "get_current_time",
            name = "Get Current Time",
            description = "Returns the current date, time, and timezone",
            category = ToolCategory.PRODUCTIVITY,
            tokenEstimate = 45,
            defaultEnabled = false,
            openApiSchema = """
                {
                  "name": "get_current_time",
                  "description": "Get the current date and time. Format can be: 'time', 'date', or 'full' for both.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "format": {
                        "type": "string",
                        "description": "'time', 'date', or 'full'. Default: full.",
                        "default": "full"
                      }
                    }
                  }
                }
            """.trimIndent()
        ),
        ToolDefinition(
            id = "set_timer",
            name = "Set Timer",
            description = "Sets a countdown timer for a specified duration",
            category = ToolCategory.PRODUCTIVITY,
            tokenEstimate = 55,
            defaultEnabled = false,
            openApiSchema = """
                {
                  "name": "set_timer",
                  "description": "Set a countdown timer with seconds, minutes, or hours. Returns the timer ID.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "seconds": {"type": "integer", "description": "Duration in seconds"},
                      "label": {"type": "string", "description": "Optional label for the timer"}
                    },
                    "required": ["seconds"]
                  }
                }
            """.trimIndent()
        ),
        ToolDefinition(
            id = "set_alarm",
            name = "Set Alarm",
            description = "Sets an alarm for a specific time",
            category = ToolCategory.PRODUCTIVITY,
            tokenEstimate = 55,
            defaultEnabled = false,
            openApiSchema = """
                {
                  "name": "set_alarm",
                  "description": "Set an alarm for a specific hour and minute (24h format).",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "hour": {"type": "integer", "description": "Hour (0-23)"},
                      "minute": {"type": "integer", "description": "Minute (0-59)"},
                      "label": {"type": "string", "description": "Optional alarm label"}
                    },
                    "required": ["hour", "minute"]
                  }
                }
            """.trimIndent()
        ),
        ToolDefinition(
            id = "create_reminder",
            name = "Create Reminder",
            description = "Creates a reminder with title and optional date/time",
            category = ToolCategory.PRODUCTIVITY,
            tokenEstimate = 65,
            defaultEnabled = false,
            openApiSchema = """
                {
                  "name": "create_reminder",
                  "description": "Create a reminder. Opens the calendar app to add the event.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "title": {"type": "string", "description": "The reminder title"},
                      "description": {"type": "string", "description": "Optional details"},
                      "date": {"type": "string", "description": "Date in YYYY-MM-DD format"},
                      "time": {"type": "string", "description": "Time in HH:MM format (24h)"}
                    },
                    "required": ["title"]
                  }
                }
            """.trimIndent()
        ),

        // === INFORMATION ===
        ToolDefinition(
            id = "web_search",
            name = "Web Search",
            description = "Searches the web using the default browser",
            category = ToolCategory.INFORMATION,
            tokenEstimate = 40,
            defaultEnabled = true,
            openApiSchema = """
                {
                  "name": "web_search",
                  "description": "Search the web for the given query. Opens the browser with search results.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "query": {"type": "string", "description": "The search query"}
                    },
                    "required": ["query"]
                  }
                }
            """.trimIndent()
        ),
        ToolDefinition(
            id = "get_weather",
            name = "Get Weather",
            description = "Shows current weather for a city (opens browser)",
            category = ToolCategory.INFORMATION,
            tokenEstimate = 50,
            defaultEnabled = false,
            openApiSchema = """
                {
                  "name": "get_weather",
                  "description": "Get the current weather for a city. Opens the browser.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "city": {"type": "string", "description": "City name, e.g., 'Madrid' or 'London,UK'"}
                    },
                    "required": ["city"]
                  }
                }
            """.trimIndent()
        ),
        ToolDefinition(
            id = "get_news",
            name = "Get News",
            description = "Shows latest news headlines (opens browser)",
            category = ToolCategory.INFORMATION,
            tokenEstimate = 45,
            defaultEnabled = false,
            openApiSchema = """
                {
                  "name": "get_news",
                  "description": "Show latest news headlines. Optionally filter by topic.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "topic": {"type": "string", "description": "Optional news topic to filter by"}
                    }
                  }
                }
            """.trimIndent()
        ),
        ToolDefinition(
            id = "translate_text",
            name = "Translate Text",
            description = "Translates text between languages (opens Google Translate)",
            category = ToolCategory.INFORMATION,
            tokenEstimate = 55,
            defaultEnabled = false,
            openApiSchema = """
                {
                  "name": "translate_text",
                  "description": "Translate text to another language. Opens Google Translate.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "text": {"type": "string", "description": "Text to translate"},
                      "target_language": {"type": "string", "description": "Target language, e.g., 'es', 'fr', 'ja'. Default: auto-detect"}
                    },
                    "required": ["text"]
                  }
                }
            """.trimIndent()
        ),

        // === DEVICE ===
        ToolDefinition(
            id = "get_battery_level",
            name = "Get Battery Level",
            description = "Returns the current battery percentage and charging status",
            category = ToolCategory.DEVICE,
            tokenEstimate = 35,
            defaultEnabled = false,
            openApiSchema = """
                {
                  "name": "get_battery_level",
                  "description": "Get the current device battery level and charging status.",
                  "parameters": {"type": "object", "properties": {}}
                }
            """.trimIndent()
        ),
        ToolDefinition(
            id = "open_app",
            name = "Open App",
            description = "Opens an installed application by name",
            category = ToolCategory.DEVICE,
            tokenEstimate = 45,
            defaultEnabled = false,
            openApiSchema = """
                {
                  "name": "open_app",
                  "description": "Open an installed app by name. Returns error if not found.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "app_name": {"type": "string", "description": "App name, e.g., 'WhatsApp', 'YouTube', 'Chrome'"}
                    },
                    "required": ["app_name"]
                  }
                }
            """.trimIndent()
        ),
        ToolDefinition(
            id = "get_device_info",
            name = "Get Device Info",
            description = "Returns device model, OS version, and app capabilities",
            category = ToolCategory.DEVICE,
            tokenEstimate = 35,
            defaultEnabled = false,
            openApiSchema = """
                {
                  "name": "get_device_info",
                  "description": "Get information about the device and app capabilities.",
                  "parameters": {"type": "object", "properties": {}}
                }
            """.trimIndent()
        ),

        // === UTILITIES ===
        ToolDefinition(
            id = "calculate",
            name = "Calculate",
            description = "Evaluates a mathematical expression",
            category = ToolCategory.UTILITIES,
            tokenEstimate = 50,
            defaultEnabled = true,
            openApiSchema = """
                {
                  "name": "calculate",
                  "description": "Evaluate a mathematical expression. Supports +, -, *, /, %, ^ (power), sqrt(), sin(), cos(), tan(), log(), abs(), floor(), ceil(), round(). Example: '2 + 3 * 4' or 'sqrt(144) + 2^8'",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "expression": {"type": "string", "description": "The math expression to evaluate"}
                    },
                    "required": ["expression"]
                  }
                }
            """.trimIndent()
        ),
        ToolDefinition(
            id = "convert_units",
            name = "Convert Units",
            description = "Converts between common units (length, weight, temperature, etc.)",
            category = ToolCategory.UTILITIES,
            tokenEstimate = 65,
            defaultEnabled = false,
            openApiSchema = """
                {
                  "name": "convert_units",
                  "description": "Convert between units. Supported categories: length (m, km, mi, ft, in, cm, mm), weight (kg, lb, oz, g), temperature (celsius, fahrenheit, kelvin), volume (L, mL, gal, cup, fl_oz), area (m2, km2, ft2, acre, ha), speed (kmh, mph, ms), time (s, min, hr, day, week, month, year), data (B, KB, MB, GB, TB).",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "value": {"type": "number", "description": "The numeric value to convert"},
                      "from_unit": {"type": "string", "description": "Source unit, e.g., 'km', 'lb', 'celsius'"},
                      "to_unit": {"type": "string", "description": "Target unit, e.g., 'mi', 'kg', 'fahrenheit'"}
                    },
                    "required": ["value", "from_unit", "to_unit"]
                  }
                }
            """.trimIndent()
        ),
        ToolDefinition(
            id = "generate_password",
            name = "Generate Password",
            description = "Generates a secure random password",
            category = ToolCategory.UTILITIES,
            tokenEstimate = 55,
            defaultEnabled = false,
            openApiSchema = """
                {
                  "name": "generate_password",
                  "description": "Generate a secure random password with configurable length and character types.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "length": {"type": "integer", "description": "Password length (8-64). Default: 16"},
                      "include_symbols": {"type": "boolean", "description": "Include special characters. Default: true"},
                      "include_numbers": {"type": "boolean", "description": "Include numbers. Default: true"},
                      "include_uppercase": {"type": "boolean", "description": "Include uppercase letters. Default: true"}
                    }
                  }
                }
            """.trimIndent()
        ),
        ToolDefinition(
            id = "read_clipboard",
            name = "Read Clipboard",
            description = "Reads the current text from the clipboard",
            category = ToolCategory.UTILITIES,
            tokenEstimate = 30,
            defaultEnabled = false,
            openApiSchema = """
                {
                  "name": "read_clipboard",
                  "description": "Read the current text content from the system clipboard.",
                  "parameters": {"type": "object", "properties": {}}
                }
            """.trimIndent()
        ),
        ToolDefinition(
            id = "write_clipboard",
            name = "Write Clipboard",
            description = "Writes text to the system clipboard",
            category = ToolCategory.UTILITIES,
            tokenEstimate = 35,
            defaultEnabled = false,
            openApiSchema = """
                {
                  "name": "write_clipboard",
                  "description": "Copy text to the system clipboard.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "text": {"type": "string", "description": "The text to copy to clipboard"}
                    },
                    "required": ["text"]
                  }
                }
            """.trimIndent()
        ),

        // === LOCATION ===
        ToolDefinition(
            id = "get_current_location",
            name = "Get Current Location",
            description = "Returns the device's current GPS coordinates and address",
            category = ToolCategory.LOCATION,
            tokenEstimate = 40,
            defaultEnabled = true,
            openApiSchema = """
                {
                  "name": "get_current_location",
                  "description": "Get the device's current GPS location (latitude, longitude, and approximate address if available).",
                  "parameters": {"type": "object", "properties": {}}
                }
            """.trimIndent()
        ),
        ToolDefinition(
            id = "get_nearby_places",
            name = "Get Nearby Places",
            description = "Finds nearby places (restaurants, cafes, etc.) (opens Maps)",
            category = ToolCategory.LOCATION,
            tokenEstimate = 55,
            defaultEnabled = false,
            openApiSchema = """
                {
                  "name": "get_nearby_places",
                  "description": "Find nearby places of a specific type. Opens Google Maps with the search.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "place_type": {"type": "string", "description": "Type of place: 'restaurant', 'cafe', 'gas_station', 'hotel', 'pharmacy', 'hospital', 'atm', 'parking', etc."}
                    },
                    "required": ["place_type"]
                  }
                }
            """.trimIndent()
        ),
        ToolDefinition(
            id = "get_directions",
            name = "Get Directions",
            description = "Opens navigation directions to a destination (opens Maps)",
            category = ToolCategory.LOCATION,
            tokenEstimate = 50,
            defaultEnabled = true,
            openApiSchema = """
                {
                  "name": "get_directions",
                  "description": "Open navigation directions to a destination. Opens Google Maps.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "destination": {"type": "string", "description": "Destination address or place name"},
                      "mode": {"type": "string", "description": "Travel mode: 'driving', 'walking', 'biking', 'transit'. Default: driving"}
                    },
                    "required": ["destination"]
                  }
                }
            """.trimIndent()
        )
    )

    val totalTokens: Int = all.sumOf { it.tokenEstimate }
}
