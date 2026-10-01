package com.warped.di

import coil3.SingletonImageLoader
import com.google.common.truth.Truth.assertThat
import com.warped.WarpedApplication
import org.junit.jupiter.api.Test
import java.io.File
import javax.inject.Singleton

/**
 * Phase 62-01 (LEAK-05 regression, part 2): locks singleton scope
 * discipline — shared OkHttp clients are @Singleton and never closed by
 * consumers, no Activity-context singletons exist, image requests ride
 * Coil's dispose-cancelling AsyncImage, and SSE/socket streams close on
 * the Stop path.
 *
 * These are architectural assertions (reflection over Dagger bindings +
 * source scans of the production tree), so they fail at the commit that
 * introduces a scope violation rather than on a device weeks later.
 * Every scan carries a positive control so a vacuous (empty) scan fails
 * instead of passing silently.
 *
 * Hard constraints pinned here (62-CONTEXT.md, non-negotiable): shared
 * OkHttp clients are never closed; no Activity-context singletons; image
 * requests cancel on recycle; SSE streams close on Stop.
 */
class SingletonScopeRegressionTest {

    private fun sourceRoot(): File {
        val candidates = listOf(
            File("src/main/java/com/warped"), // Gradle module workdir (app/)
            File("app/src/main/java/com/warped"), // project-root workdir fallback
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("SingletonScopeRegression: no source root found from ${File(".").absolutePath}")
    }

    private fun allSources(): List<File> =
        sourceRoot().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    // ------------------------------------------------------------------
    // Shared OkHttp clients: singleton-scoped, never closed.
    // ------------------------------------------------------------------

    @Test
    fun `network module okhttp bindings are singleton-scoped`() {
        val singletonBindings = setOf(
            "provideOkHttpClient",
            "provideSseOkHttpClient",
            "provideTavilyOkHttpClient",
        )
        val methods = NetworkModule::class.java.declaredMethods
            .filter { it.name in singletonBindings }
        // Positive control: all three bindings must exist to be pinned.
        assertThat(methods.map { it.name }).containsExactlyElementsIn(singletonBindings)
        for (method in methods) {
            assertThat(method.getAnnotation(Singleton::class.java)).isNotNull()
        }
    }

    @Test
    fun `no consumer closes a shared okhttp client`() {
        val sources = allSources()
        assertThat(sources).isNotEmpty() // positive control: the scan ran

        val clientCloses = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val match = Regex("""(\w*[Cc]lient\w*)\.close\(\)""").find(line)
                if (match != null) "${file.name}:${index + 1}: ${line.trim()}" else null
            }
        }
        assertThat(clientCloses).isEmpty()
    }

    @Test
    fun `close-scan positive control - engine close sites are found`() {
        // Proves the `no consumer closes...` scan is not vacuous: legit
        // native-handle releases (the LEAK-02 mechanism) ARE detected.
        val engineManager = File(sourceRoot(), "data/local/inference/EngineManager.kt")
        assertThat(engineManager.exists()).isTrue()
        val closes = engineManager.readLines().filter { it.contains(".close()") }
        assertThat(closes).isNotEmpty()
        assertThat(closes.any { it.contains("liteRTLmEngine.close()") }).isTrue()
    }

    // ------------------------------------------------------------------
    // No Activity-context singletons.
    // ------------------------------------------------------------------

    @Test
    fun `no singleton binding requests an activity context`() {
        val diDir = File(sourceRoot(), "di")
        assertThat(diDir.isDirectory).isTrue() // positive control
        val offenders = diDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("ActivityContext") }
            .map { it.name }
            .toList()
        assertThat(offenders).isEmpty()
    }

    @Test
    fun `network module uses application context only`() {
        val module = File(sourceRoot(), "di/NetworkModule.kt").readText()
        assertThat(module).contains("ApplicationContext") // positive control
        assertThat(module).doesNotContain("ActivityContext")
    }

    @Test
    fun `coil imageloader is application-scoped via SingletonImageLoader Factory`() {
        // WarpedApplication implements Coil's SingletonImageLoader.Factory:
        // Coil passes the APPLICATION context to newImageLoader, so the
        // singleton can never capture an Activity.
        assertThat(
            SingletonImageLoader.Factory::class.java.isAssignableFrom(WarpedApplication::class.java),
        ).isTrue()
        val app = File(sourceRoot(), "../WarpedApplication.kt")
            .let { if (it.exists()) it else File(sourceRoot(), "WarpedApplication.kt") }
        assertThat(app.exists()).isTrue()
        val text = app.readText()
        assertThat(text).contains("override fun newImageLoader(context: Context): ImageLoader")
        // The Coil client is its OWN bare OkHttp instance — the authed app
        // client (AuthInterceptor) is never shared with image CDNs.
        assertThat(text).contains("OkHttpNetworkFetcherFactory")
    }

    // ------------------------------------------------------------------
    // Image requests cancel on recycle; SSE/socket streams close on Stop.
    // ------------------------------------------------------------------

    @Test
    fun `image thumbs use dispose-cancelling AsyncImage never the slow path`() {
        // Coil's AsyncImage cancels the request when the composable leaves
        // the composition (recycle/dispose); SubcomposeAsyncImage is the
        // docs-flagged slow path in lists and must not appear.
        val thumbs = listOf(
            "ui/chat/components/OgSourceCard.kt",
            "ui/chat/components/GroundedImageGrid.kt",
        ).map { File(sourceRoot(), it) }
        for (file in thumbs) {
            assertThat(file.exists()).isTrue()
            val text = file.readText()
            assertThat(text).contains("AsyncImage")
            // No SubcomposeAsyncImage IMPORT or CALL SITE (a KDoc mention
            // stating the rule is fine — usage is what leaks lists).
            assertThat(text).doesNotContain("import coil3.compose.SubcomposeAsyncImage")
            assertThat(text).doesNotContain("SubcomposeAsyncImage(")
        }
    }

    @Test
    fun `fetcher closes responses and aborts all in-flight calls on cancel`() {
        // WebPageFetcher: `call.execute().use {}` auto-closes every
        // response body; `cancel()` iterates a snapshot of ALL in-flight
        // calls (fan-out-safe — no post-Stop socket survives).
        val fetcher = File(sourceRoot(), "data/grounding/WebPageFetcher.kt").readText()
        assertThat(fetcher).contains("call.execute().use")
        assertThat(fetcher).contains("activeCalls.toList()")
        assertThat(fetcher).contains("call.cancel()")
    }

    @Test
    fun `stop path reaches the grounding fetcher cancel`() {
        // ChatViewModel.stopGeneration cancels the in-flight grounding
        // fetch BEFORE the helper transport stop — the socket half of the
        // SSE-streams-close-on-Stop constraint (the stream half is pinned
        // by InferenceCancelRegressionTest's closeable assertions).
        val vm = File(sourceRoot(), "ui/chat/ChatViewModel.kt").readText()
        assertThat(vm).contains("fun stopGeneration()")
        assertThat(vm).contains("fetcher.cancel()")
    }
}
