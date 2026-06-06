package com.warped.data.local.benchmark

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.warped.MainActivity
import com.warped.R
import com.warped.WarpedApplication

class BenchmarkNotifier(private val context: Context) {

    fun foregroundInfo(text: String, progress: Int): ForegroundInfo {
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_BENCHMARK)
            .setContentTitle("Model benchmark")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), progress < 0)
            .setContentIntent(
                android.app.PendingIntent.getActivity(
                    context,
                    0,
                    android.content.Intent(context, MainActivity::class.java),
                    android.app.PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_BENCHMARK,
                "Model Benchmarks",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of model performance benchmarks"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_BENCHMARK = "model_benchmarks"
        const val NOTIFICATION_ID = 2000
    }
}
