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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * Phase 58 (OG-02): horizontal OG thumbnail card for one grounded source,
 * per the locked card layout (thumb 64dp left | title 1-line + desc 2-line
 * middle | [N] badge + open icon right; rounded-12dp container, 12dp
 * internal padding, 8dp inter-card gaps applied by the caller).
 *
 * Container is the locked 2B2B29 neutral in dark theme /
 * M3 surfaceVariant in light theme; title + description render in neutral
 * onSurface tones; the coral primary is reserved for the [N] badge and the
 * open-in-browser glyph. Whole-card tap opens the preview sheet; the open
 * icon consumes its tap and fires the guarded browser intent instead.
 * Omitida sources never reach this composable (struck text rows stand).
 *
 * Image policy (T-58-06/T-58-07): the model URL is re-gated to http(s) at
 * render (defense in depth over the parse gate); title/description render
 * via plain Compose Text only. A null URL or a failed load collapses the
 * thumb slot silently — the text-only card, no error affordance.
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
    val description = source.ogDescription?.trim()?.takeIf { it.isNotEmpty() }
    val gatedImage = gatedHttpImageUrl(source.ogImageUrl)
    var imageFailed by remember(gatedImage) { mutableStateOf(false) }
    val showThumb = gatedImage != null && !imageFailed
    val container = if (isSystemInDarkTheme()) {
        OgCardDark
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val previewCd = stringResource(R.string.cd_source_preview, number, displayTitle)
    val openBrowserCd = stringResource(R.string.cd_open_in_browser)
    val openSourceCd = stringResource(R.string.cd_open_source_browser, number)

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
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = displayTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = description,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxHeight(),
            ) {
                // [N] badge — same construction as the preview sheet badge.
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = "[$number]",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                // 48dp hit area comes from IconButton's enforced minimum
                // touch target; the glyph itself is 20dp.
                IconButton(onClick = { onOpenBrowser(source.url) }) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInNew,
                        contentDescription = openSourceCd,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/**
 * Sources-carousel compact card (fixed 272dp width) reusing the
 * [OgSourceCard] pieces: the same [OgThumb] 64dp slot (tap → browser), the
 * same title fallback ([ogDisplayTitle], `maxLines = 2`), the same `[N]`
 * badge construction, and the same container colors ([OgCardDark] dark /
 * M3 surfaceVariant light — zero new color constants, zero purple).
 *
 * Tap rules mirror [OgSourceCard]: whole-card tap opens the preview sheet;
 * the thumbnail and the open icon consume their taps and fire the guarded
 * browser intent instead. Text-only sources (null/failed thumb, legacy
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
    val gatedImage = gatedHttpImageUrl(source.ogImageUrl)
    var imageFailed by remember(gatedImage) { mutableStateOf(false) }
    val showThumb = gatedImage != null && !imageFailed
    val container = if (isSystemInDarkTheme()) {
        OgCardDark
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val previewCd = stringResource(R.string.cd_preview_source, number)
    val openSourceTitleCd = stringResource(R.string.cd_open_source_title, number, displayTitle)
    val openSourceCd = stringResource(R.string.cd_open_source_browser, number)

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
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (showThumb) {
                    Box(
                        modifier = Modifier
                            .clickable(role = Role.Button, onClick = { onOpenBrowser(source.url) })
                            .semantics {
                                contentDescription = openSourceTitleCd
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
                // [N] badge — same construction as OgSourceCard.
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = "[$number]",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.weight(1f))
                // 48dp hit area comes from IconButton's enforced minimum
                // touch target; the glyph itself is 20dp.
                IconButton(onClick = { onOpenBrowser(source.url) }) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInNew,
                        contentDescription = openSourceCd,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = displayTitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Phase 58 (OG-02): shared 64dp Coil thumb with shimmer-behind loading.
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
            .size(64.dp)
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
