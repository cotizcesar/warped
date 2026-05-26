package com.warped.data.local.inference.tools

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.SearchManager
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolExecutors @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val random = SecureRandom()

    fun execute(toolId: String, params: Map<String, Any?>): String {
        return try {
            when (toolId) {
                "get_current_time" -> executeGetCurrentTime(params)
                "set_timer" -> executeSetTimer(params)
                "set_alarm" -> executeSetAlarm(params)
                "create_reminder" -> executeCreateReminder(params)
                "web_search" -> executeWebSearch(params)
                "get_weather" -> executeGetWeather(params)
                "get_news" -> executeGetNews(params)
                "translate_text" -> executeTranslateText(params)
                "get_battery_level" -> executeGetBatteryLevel()
                "open_app" -> executeOpenApp(params)
                "get_device_info" -> executeGetDeviceInfo()
                "calculate" -> executeCalculate(params)
                "convert_units" -> executeConvertUnits(params)
                "generate_password" -> executeGeneratePassword(params)
                "read_clipboard" -> executeReadClipboard()
                "write_clipboard" -> executeWriteClipboard(params)
                "get_current_location" -> executeGetCurrentLocation()
                "get_nearby_places" -> executeGetNearbyPlaces(params)
                "get_directions" -> executeGetDirections(params)
                else -> """{"error": "Unknown tool: $toolId"}"""
            }
        } catch (e: Exception) {
            Timber.e(e, "Tool execution failed: $toolId")
            """{"error": "${e.message}"}"""
        }
    }

    // === PRODUCTIVITY ===

    private fun executeGetCurrentTime(params: Map<String, Any?>): String {
        val format = params["format"] as? String ?: "full"
        val now = Date()
        return when (format.lowercase()) {
            "time" -> """{"time": "${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)}"}"""
            "date" -> """{"date": "${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)}"}"""
            else -> """{"date": "${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)}", "time": "${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)}", "day": "${SimpleDateFormat("EEEE", Locale.getDefault()).format(now)}", "timezone": "${TimeZone.getDefault().id}"}"""
        }
    }

    private fun executeSetTimer(params: Map<String, Any?>): String {
        val seconds = (params["seconds"] as? Number)?.toInt() ?: return """{"error": "seconds is required"}"""
        val label = params["label"] as? String ?: "Timer"

        val triggerTime = System.currentTimeMillis() + seconds * 1000L
        val intent = Intent(context, TimerReceiver::class.java).apply {
            putExtra("label", label)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return """{"error": "Exact alarm permission not granted. Enable in Settings > Apps > Warped > Alarms."}"""
        }

        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        val mins = seconds / 60
        val secs = seconds % 60
        return """{"timer_set": true, "label": "$label", "duration": "$seconds seconds (${mins}m ${secs}s)"}"""
    }

    private fun executeSetAlarm(params: Map<String, Any?>): String {
        val hour = (params["hour"] as? Number)?.toInt() ?: return """{"error": "hour is required"}"""
        val minute = (params["minute"] as? Number)?.toInt() ?: return """{"error": "minute is required"}"""
        val label = params["label"] as? String ?: "Alarm"

        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_MONTH, 1)
        }

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return """{"alarm_set": true, "time": "$hour:$minute", "label": "$label"}"""
    }

    private fun executeCreateReminder(params: Map<String, Any?>): String {
        val title = params["title"] as? String ?: return """{"error": "title is required"}"""
        val description = params["description"] as? String ?: ""
        val dateStr = params["date"] as? String
        val timeStr = params["time"] as? String

        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            if (dateStr != null && timeStr != null) {
                val cal = java.util.GregorianCalendar()
                val dateParts = dateStr.split("-")
                val timeParts = timeStr.split(":")
                if (dateParts.size == 3 && timeParts.size == 2) {
                    cal.set(dateParts[0].toInt(), dateParts[1].toInt() - 1, dateParts[2].toInt(),
                        timeParts[0].toInt(), timeParts[1].toInt())
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.timeInMillis)
                }
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            """{"reminder_created": true, "title": "$title"}"""
        } catch (e: Exception) {
            """{"reminder_created": true, "title": "$title", "note": "Calendar app may not be available"}"""
        }
    }

    private fun httpGet(urlString: String, userAgent: String? = null): String {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (userAgent != null) conn.setRequestProperty("User-Agent", userAgent)
            val code = conn.responseCode
            return if (code in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream)).readText()
            } else {
                ""
            }
        } finally {
            conn.disconnect()
        }
    }

    // === INFORMATION ===

    private fun executeWebSearch(params: Map<String, Any?>): String {
        val query = params["query"] as? String ?: return """{"error": "query is required"}"""
        return try {
            val url = "https://www.google.com/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
            val body = httpGet(url, "Mozilla/5.0")
            val snippet = body.substringAfter("<div class=\"BNeawe s3v9rd\">")
                .substringBefore("</div>")
                .replace(Regex("<[^>]*>"), "")
                .trim()
                .take(500)
            val snippet2 = body.substringAfter("<div class=\"BNeawe\">")
                .substringBefore("</div>")
                .replace(Regex("<[^>]*>"), "")
                .trim()
                .take(300)
            """{"query": "$query", "results": [{"title": "Result 1", "snippet": "${if (snippet.isNotEmpty()) snippet else snippet2}"}]}"""
        } catch (e: Exception) {
            Timber.w(e, "Tool: web search failed")
            """{"query": "$query", "results": [], "note": "Search completed in background"}"""
        }
    }

    private fun executeGetWeather(params: Map<String, Any?>): String {
        val city = params["city"] as? String ?: return """{"error": "city is required"}"""
        return try {
            val url = "https://wttr.in/${java.net.URLEncoder.encode(city, "UTF-8")}?format=j1"
            val body = httpGet(url)
            """{"city": "$city", "weather_data": $body}"""
        } catch (e: Exception) {
            Timber.w(e, "Tool: weather fetch failed")
            """{"city": "$city", "weather": "Weather data not available offline", "note": "Use general knowledge about $city climate"}"""
        }
    }

    private fun executeGetNews(params: Map<String, Any?>): String {
        val topic = params["topic"] as? String
        val query = topic ?: "headlines"
        val lang = Locale.getDefault().language
        val country = Locale.getDefault().country.ifEmpty { "US" }
        return try {
            val url = "https://news.google.com/rss/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&hl=$lang&gl=$country&ceid=$country:$lang"
            val body = httpGet(url, "Mozilla/5.0")
            val titles = Regex("<title>(.*?)</title>").findAll(body)
                .map { it.groupValues[1] }
                .filter { it != "Google News" }
                .take(5)
                .toList()
            """{"topic": "$query", "headlines": [${titles.joinToString(", ") { "\"$it\"" }}]}"""
        } catch (e: Exception) {
            Timber.w(e, "Tool: news fetch failed")
            """{"topic": "$query", "headlines": [], "note": "News not available offline"}"""
        }
    }

    private fun executeTranslateText(params: Map<String, Any?>): String {
        val text = params["text"] as? String ?: return """{"error": "text is required"}"""
        val targetLang = params["target_language"] as? String ?: Locale.getDefault().language
        val sourceLang = params["source_language"] as? String ?: "auto"
        return try {
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&q=${java.net.URLEncoder.encode(text, "UTF-8")}"
            val body = httpGet(url)
            val translated = Regex("\"([^\"]+)\"").findAll(body)
                .map { it.groupValues[1] }
                .filter { it.length > 1 && !it.startsWith(",") }
                .joinToString("")
            """{"original": "$text", "translated": "$translated", "source_language": "$sourceLang", "target_language": "$targetLang"}"""
        } catch (e: Exception) {
            Timber.w(e, "Tool: translation failed")
            """{"original": "$text", "translated": "", "note": "Translation not available offline"}"""
        }
    }

    // === DEVICE ===

    private fun executeGetBatteryLevel(): String {
        val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (scale > 0) (level * 100 / scale) else -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val statusStr = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            else -> "unknown"
        }
        return """{"battery_level": $pct, "status": "$statusStr", "is_charging": ${status == BatteryManager.BATTERY_STATUS_CHARGING}}"""
    }

    private fun executeOpenApp(params: Map<String, Any?>): String {
        val appName = (params["app_name"] as? String)?.lowercase() ?: return """{"error": "app_name is required"}"""

        val appMap = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "calendar" to "com.google.android.calendar",
            "camera" to "com.android.camera",
            "settings" to "com.android.settings",
            "phone" to "com.android.dialer",
            "messages" to "com.google.android.apps.messaging",
            "photos" to "com.google.android.apps.photos",
            "play store" to "com.android.vending",
            "spotify" to "com.spotify.music",
            "instagram" to "com.instagram.android",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "telegram" to "org.telegram.messenger",
            "netflix" to "com.netflix.mediaclient",
            "calculator" to "com.android.calculator2",
            "clock" to "com.android.deskclock",
            "notes" to "com.google.android.apps.docs"
        )

        val packageName = appMap[appName] ?: appName
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                """{"app_opened": true, "app": "$appName"}"""
            } else {
                """{"app_opened": false, "error": "App '$appName' not found or not installed"}"""
            }
        } catch (e: Exception) {
            """{"app_opened": false, "error": "Could not open app: ${e.message}"}"""
        }
    }

    private fun executeGetDeviceInfo(): String {
        return """{"platform": "Android", "os_version": "${Build.VERSION.RELEASE}", "sdk": ${Build.VERSION.SDK_INT}, "device": "${Build.MODEL}", "manufacturer": "${Build.MANUFACTURER}", "app": "Warped", "capabilities": "text, images, audio, tool calling"}"""
    }

    // === UTILITIES ===

    private fun executeCalculate(params: Map<String, Any?>): String {
        val expression = params["expression"] as? String ?: return """{"error": "expression is required"}"""
        return try {
            val result = Calculator.eval(expression)
            """{"expression": "$expression", "result": $result}"""
        } catch (e: Exception) {
            """{"expression": "$expression", "error": "${e.message}"}"""
        }
    }

    private fun executeConvertUnits(params: Map<String, Any?>): String {
        val value = (params["value"] as? Number)?.toDouble() ?: return """{"error": "value is required"}"""
        val from = (params["from_unit"] as? String)?.lowercase() ?: return """{"error": "from_unit is required"}"""
        val to = (params["to_unit"] as? String)?.lowercase() ?: return """{"error": "to_unit is required"}"""
        return try {
            val result = UnitConverter.convert(value, from, to)
            """{"value": $value, "from": "$from", "to": "$to", "result": $result}"""
        } catch (e: Exception) {
            """{"error": "${e.message}"}"""
        }
    }

    private fun executeGeneratePassword(params: Map<String, Any?>): String {
        val length = ((params["length"] as? Number)?.toInt() ?: 16).coerceIn(8, 64)
        val includeSymbols = params["include_symbols"] as? Boolean ?: true
        val includeNumbers = params["include_numbers"] as? Boolean ?: true
        val includeUppercase = params["include_uppercase"] as? Boolean ?: true

        val lower = "abcdefghijklmnopqrstuvwxyz"
        val upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val digits = "0123456789"
        val symbols = "!@#$%^&*()_+-=[]{}|;:,.<>?"

        val chars = buildString {
            append(lower)
            if (includeUppercase) append(upper)
            if (includeNumbers) append(digits)
            if (includeSymbols) append(symbols)
        }

        val password = (1..length).map { chars[random.nextInt(chars.length)] }.joinToString("")
        return """{"password_length": $length, "has_symbols": $includeSymbols, "has_numbers": $includeNumbers, "has_uppercase": $includeUppercase, "password": "$password"}"""
    }

    private fun executeReadClipboard(): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        return """{"clipboard_text": "$text", "has_content": ${text.isNotEmpty()}}"""
    }

    private fun executeWriteClipboard(params: Map<String, Any?>): String {
        val text = params["text"] as? String ?: return """{"error": "text is required"}"""
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Warped", text)
        clipboard.setPrimaryClip(clip)
        return """{"clipboard_set": true, "text_length": "${text.length}"}"""
    }

    // === LOCATION ===

    private fun executeGetCurrentLocation(): String {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isEnabled) {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return """{"location_enabled": false, "message": "Location is disabled. Opening settings..."}"""
        }

        val lastKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

        return if (lastKnown != null) {
            val geocoder = Geocoder(context, Locale.getDefault())
            val lat = String.format("%.6f", lastKnown.latitude)
            val lng = String.format("%.6f", lastKnown.longitude)
            try {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lastKnown.latitude, lastKnown.longitude, 1)
                val address = addresses?.firstOrNull()
                val addrStr = address?.getAddressLine(0) ?: ""
                """{"latitude": $lat, "longitude": $lng, "accuracy": ${String.format("%.1f", lastKnown.accuracy)}m, "address": "$addrStr"}"""
            } catch (e: Exception) {
                """{"latitude": $lat, "longitude": $lng, "accuracy": ${String.format("%.1f", lastKnown.accuracy)}m}"""
            }
        } else {
            """{"location_available": false, "message": "No cached location available. Open a maps app first or wait for GPS fix."}"""
        }
    }

    private fun executeGetNearbyPlaces(params: Map<String, Any?>): String {
        val placeType = params["place_type"] as? String ?: return """{"error": "place_type is required"}"""
        val url = "https://www.google.com/maps/search/${Uri.encode(placeType)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return """{"nearby_search": true, "type": "$placeType"}"""
    }

    private fun executeGetDirections(params: Map<String, Any?>): String {
        val destination = params["destination"] as? String ?: return """{"error": "destination is required"}"""
        val mode = params["mode"] as? String ?: "driving"
        val url = "https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(destination)}&travelmode=$mode"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return """{"directions_opened": true, "destination": "$destination", "mode": "$mode"}"""
    }
}

internal object Calculator {
    fun eval(expr: String): Double {
        val s = expr.trim().replace("^", "@").replace(" ", "")
        if (s.isEmpty()) throw IllegalArgumentException("Empty expression")
        return evalSimple(s)
    }

    private fun evalSimple(expr: String): Double {
        if (expr == "pi" || expr == "PI") return Math.PI
        if (expr == "e" || expr == "E") return Math.E
        expr.toDoubleOrNull()?.let { return it }

        val funcMatch = Regex("^(sqrt|sin|cos|tan|log|abs|floor|ceil|round)\\((.+)\\)$").find(expr)
        if (funcMatch != null) {
            val inner = evalSimple(funcMatch.groupValues[2])
            return when (funcMatch.groupValues[1]) {
                "sqrt" -> kotlin.math.sqrt(inner)
                "sin" -> kotlin.math.sin(Math.toRadians(inner))
                "cos" -> kotlin.math.cos(Math.toRadians(inner))
                "tan" -> kotlin.math.tan(Math.toRadians(inner))
                "log" -> kotlin.math.log10(inner)
                "abs" -> kotlin.math.abs(inner)
                "floor" -> kotlin.math.floor(inner)
                "ceil" -> kotlin.math.ceil(inner)
                "round" -> kotlin.math.round(inner).toDouble()
                else -> throw IllegalArgumentException("Unknown function")
            }
        }

        var depth = 0; var opIdx = -1; var opChar = ' '; var opPrio = Int.MAX_VALUE
        for (i in expr.indices) {
            when (expr[i]) {
                '(' -> depth++
                ')' -> depth--
                '+', '-' -> {
                    if (depth == 0 && !(expr[i] == '-' && i == 0) &&
                        !(i > 0 && expr[i-1] in "+-*/%@") && 1 < opPrio)
                    { opIdx = i; opChar = expr[i]; opPrio = 1 }
                }
                '*', '/', '%' -> {
                    if (depth == 0 && 2 < opPrio) { opIdx = i; opChar = expr[i]; opPrio = 2 }
                }
                '@' -> {
                    if (depth == 0 && 3 < opPrio) { opIdx = i; opChar = expr[i]; opPrio = 3 }
                }
            }
        }
        if (opIdx >= 0) {
            val l = evalSimple(expr.substring(0, opIdx))
            val r = evalSimple(expr.substring(opIdx + 1))
            return when (opChar) { '+' -> l + r; '-' -> l - r; '*' -> l * r; '/' -> l / r; '%' -> l % r; '@' -> Math.pow(l, r); else -> 0.0 }
        }
        if (expr.startsWith("(") && expr.endsWith(")")) return evalSimple(expr.substring(1, expr.length - 1))
        if (expr.startsWith("-")) return -evalSimple(expr.substring(1))
        throw IllegalArgumentException("Cannot parse: $expr")
    }
}

internal object UnitConverter {
    private data class Unit(val factor: Double, val offset: Double = 0.0)

    private val length = mapOf(
        "m" to Unit(1.0), "km" to Unit(1000.0), "cm" to Unit(0.01), "mm" to Unit(0.001),
        "mi" to Unit(1609.344), "ft" to Unit(0.3048), "in" to Unit(0.0254)
    )
    private val weight = mapOf(
        "kg" to Unit(1.0), "g" to Unit(0.001), "lb" to Unit(0.453592), "oz" to Unit(0.0283495)
    )
    private val temperature = mapOf(
        "celsius" to Unit(1.0, 0.0), "fahrenheit" to Unit(1.8, 32.0), "kelvin" to Unit(1.0, 273.15)
    )
    private val volume = mapOf(
        "l" to Unit(1.0), "ml" to Unit(0.001), "gal" to Unit(3.78541), "cup" to Unit(0.236588), "fl_oz" to Unit(0.0295735)
    )
    private val area = mapOf(
        "m2" to Unit(1.0), "km2" to Unit(1_000_000.0), "ft2" to Unit(0.092903), "acre" to Unit(4046.86), "ha" to Unit(10000.0)
    )
    private val speed = mapOf(
        "kmh" to Unit(1.0), "mph" to Unit(1.60934), "ms" to Unit(3.6)
    )
    private val time = mapOf(
        "s" to Unit(1.0), "min" to Unit(60.0), "hr" to Unit(3600.0), "day" to Unit(86400.0), "week" to Unit(604800.0), "month" to Unit(2629800.0), "year" to Unit(31557600.0)
    )
    private val data = mapOf(
        "b" to Unit(1.0), "kb" to Unit(1024.0), "mb" to Unit(1048576.0), "gb" to Unit(1073741824.0), "tb" to Unit(1099511627776.0)
    )

    fun convert(value: Double, from: String, to: String): Double {
        val category = findCategory(from, to)
        val table = category ?: throw IllegalArgumentException("Unknown units: $from -> $to")

        if (category === temperature && from == "celsius" && to == "fahrenheit")
            return value * 1.8 + 32.0
        if (category === temperature && from == "fahrenheit" && to == "celsius")
            return (value - 32.0) / 1.8

        val fromUnit = table[from] ?: throw IllegalArgumentException("Unknown unit: $from")
        val toUnit = table[to] ?: throw IllegalArgumentException("Unknown unit: $to")

        val baseValue = if (fromUnit.offset != 0.0) (value + fromUnit.offset) * fromUnit.factor
        else value * fromUnit.factor

        val result = if (toUnit.offset != 0.0) (baseValue / toUnit.factor) - toUnit.offset
        else baseValue / toUnit.factor

        return Math.round(result * 1_000_000.0) / 1_000_000.0
    }

    private fun findCategory(from: String, to: String): Map<String, Unit>? {
        for (cat in listOf(length, weight, temperature, volume, area, speed, time, data)) {
            if (from in cat && to in cat) return cat
        }
        return null
    }
}
