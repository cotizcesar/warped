package com.warped.ui.chat.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.test.FakeImageLoaderEngine
import com.warped.domain.model.GroundedSource
import com.warped.ui.theme.WarpedTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tap-routing widget tests for [OgSourceCard]: the thumbnail opens the
 * browser directly, the card body opens the preview sheet, and the open
 * icon keeps its existing browser behavior.
 *
 * Coil flake handling: `AsyncImage` would try to load the fake
 * `ogImageUrl` over the network and `onError` would collapse the thumb
 * slot before `performClick` runs. A test [ImageLoader] with a
 * [FakeImageLoaderEngine] defaulting to a 1x1 [ColorImage] is installed as the
 * process singleton, so every load succeeds deterministically and the
 * thumb stays mounted. Production code is untouched.
 */
@OptIn(DelicateCoilApi::class)
class OgSourceCardTapTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val source = GroundedSource(
        url = "https://example.com/x",
        ogTitle = "Example title",
        ogDescription = "Example description",
        ogImageUrl = "https://example.com/thumb.png",
    )

    private val browserUrls = mutableListOf<String>()
    private var previewCalls = 0

    @Before
    fun setUpFakeImageLoader() {
        browserUrls.clear()
        previewCalls = 0
        val engine = FakeImageLoaderEngine.Builder()
            .default(ColorImage(android.graphics.Color.RED))
            .build()
        val loader = ImageLoader.Builder(ApplicationProvider.getApplicationContext())
            .components { add(engine) }
            .build()
        SingletonImageLoader.setUnsafe(loader)
    }

    @After
    fun resetImageLoader() {
        SingletonImageLoader.reset()
    }

    private fun setCardContent() {
        composeTestRule.setContent {
            WarpedTheme {
                OgSourceCard(
                    source = source,
                    number = 1,
                    onPreview = { previewCalls++ },
                    onOpenBrowser = { url -> browserUrls.add(url) },
                )
            }
        }
    }

    @Test
    fun `thumbnail_tap_opens_browser_and_not_preview_sheet`() {
        setCardContent()
        composeTestRule
            .onNodeWithContentDescription("Open in browser", useUnmergedTree = true)
            .performClick()
        assert(browserUrls == listOf(source.url)) {
            "Expected browser callback with ${source.url}, got $browserUrls"
        }
        assert(previewCalls == 0) {
            "Preview callback must not fire on thumbnail tap, fired $previewCalls times"
        }
    }

    @Test
    fun `body_tap_opens_preview_sheet_and_not_browser`() {
        setCardContent()
        composeTestRule
            .onNodeWithText("Example title", useUnmergedTree = true)
            .performClick()
        assert(previewCalls == 1) {
            "Expected exactly one preview callback on body tap, got $previewCalls"
        }
        assert(browserUrls.isEmpty()) {
            "Browser callback must not fire on body tap, got $browserUrls"
        }
    }

    @Test
    fun `open_icon_still_routes_to_browser_callback`() {
        setCardContent()
        composeTestRule
            .onNodeWithContentDescription("Open source 1 in browser", useUnmergedTree = true)
            .performClick()
        assert(browserUrls == listOf(source.url)) {
            "Expected browser callback with ${source.url}, got $browserUrls"
        }
        assert(previewCalls == 0) {
            "Preview callback must not fire on open-icon tap, fired $previewCalls times"
        }
    }
}
