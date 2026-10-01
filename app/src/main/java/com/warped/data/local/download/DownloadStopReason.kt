package com.warped.data.local.download

/**
 * Phase 60-02 (API-04): pure WorkManager stop-reason to user-facing retry
 * copy mapping.
 *
 * `WorkInfo.getStopReason()` is only readable UI-side (workers cannot read
 * their own stop reason), so [ModelDownloadManager] observes it and stores
 * the mapped copy on [DownloadState.stopReasonCopy] for the progress/retry
 * UI. The int constants below mirror `androidx.work.WorkInfo` companion
 * values (work-runtime 2.12.0) — see `DownloadStopReasonTest` parity check.
 *
 * Pure Kotlin, no Android framework calls: safe for JVM unit tests and for
 * API < 31 devices (which never produce a real stop reason).
 *
 * Unrelated: `AnthropicProvider`/`AnthropicDtos` have their own LLM token
 * `stopReason` field for agentic tool-use loops — do not confuse the two.
 */
object DownloadStopReason {

    // Special values (negative range).
    const val FOREGROUND_SERVICE_TIMEOUT: Int = -128
    const val NOT_STOPPED: Int = -256
    const val UNKNOWN: Int = -512

    // Platform stop reasons (mirror JobParameters.STOP_REASON_*).
    const val CANCELLED_BY_APP: Int = 1
    const val PREEMPT: Int = 2
    const val TIMEOUT: Int = 3
    const val DEVICE_STATE: Int = 4
    const val CONSTRAINT_BATTERY_NOT_LOW: Int = 5
    const val CONSTRAINT_CHARGING: Int = 6
    const val CONSTRAINT_CONNECTIVITY: Int = 7
    const val CONSTRAINT_DEVICE_IDLE: Int = 8
    const val CONSTRAINT_STORAGE_NOT_LOW: Int = 9
    const val QUOTA: Int = 10
    const val BACKGROUND_RESTRICTION: Int = 11
    const val APP_STANDBY: Int = 12
    const val USER: Int = 13
    const val SYSTEM_PROCESSING: Int = 14
    const val ESTIMATED_APP_LAUNCH_TIME_CHANGED: Int = 15

    /**
     * Safe generic copy for unknown ints — covers the JobScheduler reserved
     * mirror range `[0, MAX_VALUE]` and any future WorkManager codes. Never
     * blank, never crashes.
     */
    const val GENERIC_COPY: String = "Download interrupted. Tap Retry to resume."

    /**
     * Maps a [WorkInfo.getStopReason] int to English retry copy. Pure —
     * same int always yields the same string.
     */
    fun stopReasonCopy(reason: Int): String = when (reason) {
        NOT_STOPPED ->
            "Download active."
        UNKNOWN ->
            "Download stopped for an unknown reason. Tap Retry to resume."
        FOREGROUND_SERVICE_TIMEOUT ->
            "Download stopped — the system reclaimed the foreground service. Tap Retry to resume."
        CANCELLED_BY_APP ->
            "Download cancelled."
        PREEMPT ->
            "Download paused by the system to prioritize another task. Tap Retry to resume."
        TIMEOUT ->
            "Download timed out. Tap Retry to resume."
        DEVICE_STATE ->
            "Download stopped due to device state. Tap Retry to resume."
        CONSTRAINT_BATTERY_NOT_LOW ->
            "Download stopped — battery too low. Charge the device, then tap Retry."
        CONSTRAINT_CHARGING ->
            "Download stopped — the charger was disconnected. Reconnect power, then tap Retry."
        CONSTRAINT_CONNECTIVITY ->
            "Connection lost. Tap Retry when you are back online."
        CONSTRAINT_DEVICE_IDLE ->
            "Download stopped while the device was idle. Tap Retry to resume."
        CONSTRAINT_STORAGE_NOT_LOW ->
            "Download stopped — storage is low. Free up space, then tap Retry."
        QUOTA ->
            "Download stopped — the system quota paused background work. Tap Retry to resume."
        BACKGROUND_RESTRICTION ->
            "Download stopped — background work is restricted. Tap Retry to resume."
        APP_STANDBY ->
            "Download stopped — the app was idle too long. Open the app and tap Retry."
        USER ->
            "Download stopped by the user. Tap Retry to resume."
        SYSTEM_PROCESSING ->
            "Download stopped — the system is busy. Tap Retry to resume."
        ESTIMATED_APP_LAUNCH_TIME_CHANGED ->
            "Download stopped — system scheduling changed. Tap Retry to resume."
        else -> GENERIC_COPY
    }
}
