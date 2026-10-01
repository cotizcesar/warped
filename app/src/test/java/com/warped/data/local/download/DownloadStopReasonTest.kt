package com.warped.data.local.download

import androidx.work.WorkInfo
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Phase 60-02 (API-04): WorkManager stop-reason to retry-copy mapping.
 *
 * The mapper is pure Kotlin (no Android framework calls) so these run as
 * plain JVM unit tests. WorkInfo constants are compile-time `const` ints —
 * referencing them here inlines the values without loading framework code.
 */
class DownloadStopReasonTest {

    // Every documented WorkManager stop-reason int (work-runtime 2.12.0
    // WorkInfo companion), paired with a keyword its copy must contain so a
    // regression to the generic fallback is caught per-code, not just
    // "non-empty".
    private val documentedReasons: List<Pair<Int, String>> = listOf(
        DownloadStopReason.NOT_STOPPED to "active",
        DownloadStopReason.UNKNOWN to "unknown",
        DownloadStopReason.FOREGROUND_SERVICE_TIMEOUT to "foreground",
        DownloadStopReason.CANCELLED_BY_APP to "cancelled",
        DownloadStopReason.PREEMPT to "prioritize",
        DownloadStopReason.TIMEOUT to "timed out",
        DownloadStopReason.DEVICE_STATE to "device state",
        DownloadStopReason.CONSTRAINT_BATTERY_NOT_LOW to "battery",
        DownloadStopReason.CONSTRAINT_CHARGING to "charger",
        DownloadStopReason.CONSTRAINT_CONNECTIVITY to "Connection lost",
        DownloadStopReason.CONSTRAINT_DEVICE_IDLE to "idle",
        DownloadStopReason.CONSTRAINT_STORAGE_NOT_LOW to "storage",
        DownloadStopReason.QUOTA to "quota",
        DownloadStopReason.BACKGROUND_RESTRICTION to "background",
        DownloadStopReason.APP_STANDBY to "idle",
        DownloadStopReason.USER to "user",
        DownloadStopReason.SYSTEM_PROCESSING to "busy",
        DownloadStopReason.ESTIMATED_APP_LAUNCH_TIME_CHANGED to "scheduling",
    )

    @Test
    fun `every documented stop reason maps to a specific non-empty copy`() {
        // 18 documented codes in work-runtime 2.12.0 — fail loudly if the
        // mapper gains/loses one without updating this contract.
        assertThat(documentedReasons).hasSize(18)

        for ((reason, keyword) in documentedReasons) {
            val copy = DownloadStopReason.stopReasonCopy(reason)
            assertThat(copy).isNotEmpty()
            assertThat(copy).contains(keyword)
            // Known codes must never fall through to the generic fallback.
            assertThat(copy).isNotEqualTo(DownloadStopReason.GENERIC_COPY)
        }
    }

    @Test
    fun `mapper constants stay in sync with WorkInfo`() {
        assertThat(DownloadStopReason.NOT_STOPPED).isEqualTo(WorkInfo.STOP_REASON_NOT_STOPPED)
        assertThat(DownloadStopReason.UNKNOWN).isEqualTo(WorkInfo.STOP_REASON_UNKNOWN)
        assertThat(DownloadStopReason.FOREGROUND_SERVICE_TIMEOUT)
            .isEqualTo(WorkInfo.STOP_REASON_FOREGROUND_SERVICE_TIMEOUT)
        assertThat(DownloadStopReason.CANCELLED_BY_APP).isEqualTo(WorkInfo.STOP_REASON_CANCELLED_BY_APP)
        assertThat(DownloadStopReason.PREEMPT).isEqualTo(WorkInfo.STOP_REASON_PREEMPT)
        assertThat(DownloadStopReason.TIMEOUT).isEqualTo(WorkInfo.STOP_REASON_TIMEOUT)
        assertThat(DownloadStopReason.DEVICE_STATE).isEqualTo(WorkInfo.STOP_REASON_DEVICE_STATE)
        assertThat(DownloadStopReason.CONSTRAINT_BATTERY_NOT_LOW)
            .isEqualTo(WorkInfo.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW)
        assertThat(DownloadStopReason.CONSTRAINT_CHARGING)
            .isEqualTo(WorkInfo.STOP_REASON_CONSTRAINT_CHARGING)
        assertThat(DownloadStopReason.CONSTRAINT_CONNECTIVITY)
            .isEqualTo(WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY)
        assertThat(DownloadStopReason.CONSTRAINT_DEVICE_IDLE)
            .isEqualTo(WorkInfo.STOP_REASON_CONSTRAINT_DEVICE_IDLE)
        assertThat(DownloadStopReason.CONSTRAINT_STORAGE_NOT_LOW)
            .isEqualTo(WorkInfo.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW)
        assertThat(DownloadStopReason.QUOTA).isEqualTo(WorkInfo.STOP_REASON_QUOTA)
        assertThat(DownloadStopReason.BACKGROUND_RESTRICTION)
            .isEqualTo(WorkInfo.STOP_REASON_BACKGROUND_RESTRICTION)
        assertThat(DownloadStopReason.APP_STANDBY).isEqualTo(WorkInfo.STOP_REASON_APP_STANDBY)
        assertThat(DownloadStopReason.USER).isEqualTo(WorkInfo.STOP_REASON_USER)
        assertThat(DownloadStopReason.SYSTEM_PROCESSING).isEqualTo(WorkInfo.STOP_REASON_SYSTEM_PROCESSING)
        assertThat(DownloadStopReason.ESTIMATED_APP_LAUNCH_TIME_CHANGED)
            .isEqualTo(WorkInfo.STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED)
    }

    @Test
    fun `unknown and future stop reasons map to a safe generic copy`() {
        // JobScheduler reserves [0, MAX_VALUE] for mirrored platform reasons
        // and future WorkManager versions may add negatives — none of these
        // may crash or render blank.
        val futureAndBogus = listOf(0, 16, 999, -1, Int.MIN_VALUE, Int.MAX_VALUE)
        for (reason in futureAndBogus) {
            val copy = DownloadStopReason.stopReasonCopy(reason)
            assertThat(copy).isNotEmpty()
            assertThat(copy).isEqualTo(DownloadStopReason.GENERIC_COPY)
        }
    }

    @Test
    fun `mapping is pure - same int always yields same copy`() {
        val sampled = documentedReasons.map { it.first } + listOf(999, Int.MIN_VALUE)
        for (reason in sampled) {
            val first = DownloadStopReason.stopReasonCopy(reason)
            val second = DownloadStopReason.stopReasonCopy(reason)
            val third = DownloadStopReason.stopReasonCopy(reason)
            assertThat(first).isEqualTo(second)
            assertThat(second).isEqualTo(third)
        }
    }
}
