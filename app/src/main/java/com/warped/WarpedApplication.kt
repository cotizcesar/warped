package com.warped

import android.app.Application
import android.os.StrictMode

import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class WarpedApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(RedactingTree())
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }
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
