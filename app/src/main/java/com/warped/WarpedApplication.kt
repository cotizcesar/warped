package com.warped

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.os.Build
import android.os.Process
import android.os.StrictMode

import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.warped.BuildConfig
import com.warped.data.local.inference.EngineManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class WarpedApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var engineManager: EngineManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO
            )
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Timber.e(throwable, "Unhandled exception in thread ${thread.name}")
            Process.killProcess(Process.myPid())
        }
        if (BuildConfig.DEBUG) {
            Timber.plant(RedactingTree())
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectNetwork()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectActivityLeaks()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .detectLeakedSqlLiteObjects()
                    .penaltyLog()
                    .build()
            )
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_DOWNLOADS,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of model file downloads"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::engineManager.isInitialized) {
            engineManager.handleTrimMemory(level)
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::engineManager.isInitialized) {
            engineManager.handleTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        }
    }

    companion object {
        const val CHANNEL_DOWNLOADS = "model_downloads"
    }
}

class RedactingTree : Timber.DebugTree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val redacted = message
            .replace(Regex("(api[_-]?key|secret|token|authorization)[=:]\\s*\\S+", RegexOption.IGNORE_CASE)) { match ->
                "${match.groupValues[1]}=[REDACTED]"
            }
            .replace(Regex("Bearer\\s+\\S+", RegexOption.IGNORE_CASE)) { "Bearer [REDACTED]" }
        super.log(priority, tag, redacted, t)
    }
}
