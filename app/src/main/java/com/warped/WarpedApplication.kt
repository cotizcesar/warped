package com.warped

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.StrictMode

import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.warped.BuildConfig
import com.warped.data.local.inference.EngineManager
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class WarpedApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var engineManager: EngineManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO
            )
            .build()

    /**
     * Phase 58 (OG-02, T-58-05): Coil singleton ImageLoader for OG
     * thumbnails. Coil uses its OWN bare OkHttp instance — the app authed
     * client (AuthInterceptor + body logging) is never shared, so endpoint
     * Authorization headers cannot reach image CDNs. Memory 25% of app
     * budget; disk fixed 50MB cap in cacheDir/og_thumbnails (fixed cap, not
     * percent — 2% of 128GB storage would be ~2.5GB beside GGUF models;
     * 50MB holds ~500-1000 64dp thumbs).
     */
    override fun newImageLoader(context: Context): ImageLoader {
        val coilHttp = OkHttpClient.Builder()
            .followRedirects(true)
            .build()
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("og_thumbnails").toOkioPath())
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { coilHttp }))
            }
            .crossfade(true)
            .build()
    }

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
                getString(R.string.notif_dl_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_dl_channel_desc)
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
            // 48 (WR-07): strip credentials in URL query strings
            // (?hf_token=…&…) while preserving host/path for debuggability.
            .replace(Regex("([?&](hf_token|access_token|token|api_key)=)[^&\\s]+", RegexOption.IGNORE_CASE)) { match ->
                "${match.groupValues[1]}[REDACTED]"
            }
        super.log(priority, tag, redacted, t)
    }
}
