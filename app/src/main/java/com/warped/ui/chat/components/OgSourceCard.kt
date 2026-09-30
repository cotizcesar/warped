package com.warped.ui.chat.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.warped.R
import com.warped.domain.model.GroundedSource
import com.warped.ui.theme.OgCardDark
import com.warped.ui.theme.OgShimmer
import java.net.URI

/**
 * Phase 58 (OG-02) + quick-task two-column layout (user-locked): horizontal
 * source card with TWO columns — left favicon/thumb image, right Title (max
 * 1 line, ellipsis) + description (max 2 lines) + URL last (1 line, dimmed,
 * ellipsis). Rounded-12dp
 * container, 12dp internal padding, 8dp inter-card gaps applied by the
 * caller. No [N] number badges, no open/external-link icon (citations
 * `[1]`/`[2]` in answer text and the model prompt are untouched — only the
 * card chrome loses numbers).
 *
 * Container is the locked 2B2B29 neutral in dark theme /
 * M3 surfaceVariant in light theme; title + URL render in neutral
 * onSurface tones (zero purple). Whole-card tap opens the preview sheet;
 * the thumbnail consumes its tap and fires the guarded browser intent
 * instead ("Open in browser" a11y). Omitida sources never reach this
 * composable (struck text rows stand).
 *
 * Image policy (T-58-06/T-58-07, quick-task favicon fallback): the model
 * URL is re-gated to http(s) at render (defense in depth over the parse
 * gate); a null og:image falls back to the Google S2 favicon for the page
 * host (same gate); title/URL render via plain Compose Text only.
 * A null/unusable host or a failed load collapses the thumb slot silently
 * — the text-only card (title + URL only), no error affordance.
 */
@Composable
fun OgSourceCard(
    source: GroundedSource,
    number: Int,
    onPreview: () -> Unit,
    onOpenBrowser: (url: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayTitle = ogDisplayTitle(source.ogTitle, source.url)
    val desc = ogDisplayDescription(source.ogDescription, source.snippet)
    val gatedImage = gatedHttpImageUrl(source.ogImageUrl)
        ?: faviconFallbackUrl(source.url)?.let(::gatedHttpImageUrl)
    var imageFailed by remember(gatedImage) { mutableStateOf(false) }
    val showThumb = gatedImage != null && !imageFailed
    val container = if (isSystemInDarkTheme()) {
        OgCardDark
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val previewCd = stringResource(R.string.cd_source_preview, number, displayTitle)
    val openBrowserCd = stringResource(R.string.cd_open_in_browser)

    Surface(
        color = container,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onPreview)
            .semantics {
                contentDescription = previewCd
            },
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top,
        ) {
            if (showThumb) {
                Box(
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = { onOpenBrowser(source.url) })
                        .semantics { contentDescription = openBrowserCd },
                ) {
                    OgThumb(
                        imageUrl = gatedImage,
                        contentDescription = null,
                        onError = { imageFailed = true },
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Top,
            ) {
                Text(
                    text = displayTitle,
                    fontSize = 14.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (desc != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = desc,
                        fontSize = 12.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = source.url,
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Sources-carousel compact card (fixed 272dp width) with the same locked
 * two-column layout as [OgSourceCard]: left thumb (tap → browser), right
 * Title (max 1 line) + description + URL last (1 line, dimmed). Same container colors
 * ([OgCardDark] dark / M3 surfaceVariant light — zero new color constants,
 * zero purple). No [N] badge, no open icon.
 *
 * Tap rules mirror [OgSourceCard]: whole-card tap opens the preview sheet;
 * the thumbnail consumes its tap and fires the guarded browser intent
 * instead. Text-only sources (null/failed thumb, legacy
 * `GroundedSource(url)` rows) render the same card without the thumb slot —
 * title falls back to host, tap still opens the sheet. Omitida sources
 * never reach this composable (struck text rows stand below the carousel).
 */
@Composable
fun CompactSourceCard(
    source: GroundedSource,
    number: Int,
    onPreview: () -> Unit,
    onOpenBrowser: (url: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayTitle = ogDisplayTitle(source.ogTitle, source.url)
    val desc = ogDisplayDescription(source.ogDescription, source.snippet)
    val gatedImage = gatedHttpImageUrl(source.ogImageUrl)
        ?: faviconFallbackUrl(source.url)?.let(::gatedHttpImageUrl)
    var imageFailed by remember(gatedImage) { mutableStateOf(false) }
    val showThumb = gatedImage != null && !imageFailed
    val container = if (isSystemInDarkTheme()) {
        OgCardDark
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val previewCd = stringResource(R.string.cd_preview_source, number)
    val openBrowserCd = stringResource(R.string.cd_open_in_browser)

    Surface(
        color = container,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .width(272.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onPreview)
            .semantics {
                contentDescription = previewCd
            },
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top,
        ) {
            if (showThumb) {
                Box(
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = { onOpenBrowser(source.url) })
                        .semantics {
                            contentDescription = openBrowserCd
                        },
                ) {
                    OgThumb(
                        imageUrl = gatedImage,
                        contentDescription = null,
                        onError = { imageFailed = true },
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Top,
            ) {
                Text(
                    text = displayTitle,
                    fontSize = 14.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (desc != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = desc,
                        fontSize = 12.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = source.url,
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Phase 58 (OG-02): shared 48dp Coil thumb with shimmer-behind loading.
 * Reused by the card and the preview-sheet OG header. Coil's AsyncImage
 * (not SubcomposeAsyncImage — the docs-flagged slow path in lists) resolves
 * the singleton ImageLoader by default; text around the thumb always
 * renders immediately from Room state.
 */
@Composable
internal fun OgThumb(
    imageUrl: String,
    contentDescription: String?,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(MaterialTheme.shapes.small),
    ) {
        val pulse by rememberInfiniteTransition(label = "ogThumbShimmer").animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "ogThumbPulse",
        )
        Box(
            Modifier
                .matchParentSize()
                .background(OgShimmer.copy(alpha = pulse)),
        )
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            onError = { onError() },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Phase 58 (OG-02): render-side title fallback og:title -> host (never empty). */
fun ogDisplayTitle(ogTitle: String?, url: String): String {
    val title = ogTitle?.trim().orEmpty()
    return if (title.isNotEmpty()) title else ogHostOf(url)
}

/**
 * Card description line: render-side fallback chain `og:description` →
 * search `snippet` → hidden (null when blank so the card stays
 * byte-identical Title+URL). Trims and caps at 160 chars for card density
 * (OpenGraphParser.MAX_DESCRIPTION_CHARS is 500, so the 160-char card cap
 * applies to both legs). Render caps at maxLines 2 + ellipsis. Snippets
 * arrive pre-sanitized from the repositories (same WebContextSanitizer as
 * the fused text), so no render-side sanitizing here.
 */
fun ogDisplayDescription(ogDescription: String?, snippet: String? = null): String? {
    val desc = ogDescription?.trim().orEmpty()
    if (desc.isNotEmpty()) return desc.take(160)
    val snip = snippet?.trim().orEmpty()
    if (snip.isEmpty()) return null
    return snip.take(160)
}

/** Phase 58 (OG-02): host fallback for untitled sources; raw URL when unparseable. */
fun ogHostOf(url: String): String {
    val host = try {
        URI(url).host
    } catch (_: Exception) {
        null
    }
    return host?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: url
}

/**
 * Phase 58 (OG-02, T-58-06): render-side http(s) re-gate for og:image URLs.
 * Only gated URLs reach Coil; anything else renders the text-only card.
 */
fun gatedHttpImageUrl(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    return if (value.startsWith("http://", ignoreCase = true) ||
        value.startsWith("https://", ignoreCase = true)
    ) value else null
}

/**
 * Quick-task (favicon fallback): Google S2 favicon URL for a source page
 * whose `og:image` is null. Parses the host via [URI] (same discipline as
 * [ogHostOf]) and returns the keyless S2 lookup
 * (`https://www.google.com/s2/favicons?domain=<host>&sz=128`) — Google
 * infra, not a new API dependency. Null when the host is unparseable or
 * blank, in which case the card stays text-only. Callers gate the result
 * through [gatedHttpImageUrl] (no-op by construction — the constant is
 * `https`) so only `http(s)` ever reaches Coil, exactly like the og:image
 * path. A failed favicon load collapses to the text-only card silently via
 * the existing `onError` handling — no new error affordance.
 */
fun faviconFallbackUrl(pageUrl: String): String? {
    val host = try {
        URI(pageUrl.trim()).host
    } catch (_: Exception) {
        null
    }?.takeIf { it.isNotBlank() } ?: return null
    return "https://www.google.com/s2/favicons?domain=$host&sz=128"
}
