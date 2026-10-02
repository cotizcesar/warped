package com.warped

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
import com.warped.data.local.security.KeystoreManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
        // Keystore/MasterKey IO must not block startup (ANR risk) — the
        // removal is idempotent so async timing is safe.
        backgroundScope.launch { cleanupOrphanedSearchAlias() }
        createNotificationChannels()
        // Chain the previous handler (lint DefaultUncaughtExceptionDelegation):
        // log first, then delegate so crash reporting still fires; only when
        // no handler was installed do we kill the process ourselves.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Timber.e(throwable, "Unhandled exception in thread ${thread.name}")
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
            }
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

    /**
     * Phase 63 (SEARCH-03): best-effort idempotent cleanup of the orphaned
     * legacy search-provider Keystore alias left by pre-DDG-only installs.
     * Runs on every launch (not one-shot); remove is a no-op when absent.
     * Never throws — a locked Keystore must not break launch (T-63-04).
     * KeystoreManager itself already swallows storage exceptions; the outer
     * guard covers EntryPoint resolution as well.
     */
    private fun cleanupOrphanedSearchAlias() {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                this,
                OrphanedSearchCleanupEntryPoint::class.java
            )
            entryPoint.keystoreManager().remove(KeystoreManager.LEGACY_SEARCH_ALIAS)
        } catch (e: Throwable) {
            Timber.w(e, "Orphaned search alias cleanup skipped")
        }
    }

    private fun createNotificationChannels() {
        // minSdk is 28 — NotificationChannel always exists; no version gate.
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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::engineManager.isInitialized) {
            engineManager.handleTrimMemory(level)
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    // NOTE: no onLowMemory() override — onTrimMemory(level) above already
    // forwards the real pressure level to EngineManager.handleTrimMemory
    // (onLowMemory duplicates it with a hardcoded deprecated constant).

    companion object {
        const val CHANNEL_DOWNLOADS = "model_downloads"

        /** Process-lifetime scope for fire-and-forget startup IO. */
        private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/**
 * Phase 63 (SEARCH-03): Hilt EntryPoint for the best-effort idempotent
 * orphaned legacy search alias cleanup. Application.onCreate runs before
 * Hilt injection is available on the Application itself, hence the
 * EntryPoint lookup.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface OrphanedSearchCleanupEntryPoint {
    fun keystoreManager(): KeystoreManager
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
