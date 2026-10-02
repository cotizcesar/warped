package com.warped.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warped.R

/** Left-column width: 10.dp status dot + 12.dp header gap. */
private val DotColumnWidth = 22.dp

/**
 * Unified model card shared by the Model Catalog and Models & Endpoints.
 * Same structure everywhere — only the trailing action switches:
 * download cluster (catalog) vs delete (selector).
 *
 * Layout: status dot + title/meta/capability-icons, optional params icon,
 * trailing actions; tap expands the LM-Studio-style [CapabilityTable]
 * (plus optional caller details like blurb/RAM notes).
 *
 * The dot owns only its left column: everything below the header (CTA /
 * progress, error, description, features table) is indented by
 * [DotColumnWidth] so it aligns vertically with the title.
 *
 * @param title model display name.
 * @param sizeText formatted file size (right side of the meta row).
 * @param metaChips small chips under the title (quantization, params…).
 * @param vision audio reasoning tools allowlist-verified flags.
 * @param dotConnected green status dot when true, grey otherwise.
 * @param expandable tap toggles the details/table section.
 * @param onParams tune-icon action (selector only, null hides it).
 * @param trailingActions download cluster or delete button.
 * @param downloadContent active-download progress (catalog only).
 * @param errorText retained error line (catalog only).
 * @param detailsContent blurb / RAM note / file name (catalog only).
 */
@Composable
fun ModelCard(
    title: String,
    sizeText: String,
    ramText: String? = null,
    metaChips: List<String> = emptyList(),
    vision: Boolean = false,
    audio: Boolean = false,
    reasoning: Boolean = false,
    tools: Boolean = false,
    dotConnected: Boolean = false,
    expandable: Boolean = true,
    onParams: (() -> Unit)? = null,
    trailingActions: @Composable RowScope.() -> Unit,
    downloadContent: @Composable ColumnScope.() -> Unit = {},
    errorText: String? = null,
    detailsContent: @Composable ColumnScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B29)),
        shape = RoundedCornerShape(12.dp),
        onClick = { if (expandable) expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(connected = dotConnected)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ModelMetaChip(sizeText)
                        metaChips.forEach { ModelMetaChip(it) }
                        if (ramText != null) {
                            // RAM guidance outside the pill, same muted tone
                            // as the size text.
                            Text(
                                text = ramText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF9CA3AF),
                                maxLines = 1
                            )
                        }
                    }
                    // Text badge always: every model chats (explicit per UX).
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CapabilityIconBadge(
                            icon = Icons.Filled.TextFields,
                            contentDescription = stringResource(R.string.cap_text),
                            color = Color(0xFF9CA3AF)
                        )
                        if (vision) CapabilityIconBadge(
                            icon = Icons.Filled.Visibility,
                            contentDescription = stringResource(R.string.badge_vision),
                            color = Color(0xFF64B5F6)
                        )
                        if (audio) CapabilityIconBadge(
                            icon = Icons.Filled.Audiotrack,
                            contentDescription = stringResource(R.string.badge_audio),
                            color = Color(0xFF4CAF50)
                        )
                        if (reasoning) CapabilityIconBadge(
                            icon = Icons.Filled.Psychology,
                            contentDescription = stringResource(R.string.badge_thinking),
                            color = Color(0xFFFF9800)
                        )
                        if (tools) CapabilityIconBadge(
                            icon = Icons.Filled.Build,
                            contentDescription = stringResource(R.string.badge_tools),
                            color = Color(0xFF2196F3)
                        )
                    }
                }
                if (onParams != null) {
                    IconButton(onClick = onParams) {
                        Icon(
                            Icons.Filled.Tune,
                            stringResource(R.string.cd_parameters),
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                trailingActions()
            }

            // Below-header slots align with the title: the dot owns only
            // its left column (DotColumnWidth = 10.dp dot + 12.dp gap).
            Column(modifier = Modifier.padding(start = DotColumnWidth)) {
                downloadContent()

                if (errorText != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (expanded && expandable) {
                    detailsContent()
                    Spacer(Modifier.height(8.dp))
                    CapabilityTable(
                        vision = vision,
                        audio = audio,
                        reasoning = reasoning,
                        tools = tools
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusDot(connected: Boolean) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (connected) Color(0xFF4CAF50) else Color(0xFF6B7280))
    )
}

@Composable
private fun ModelMetaChip(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF3A3A38)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF9CA3AF),
            maxLines = 1
        )
    }
}
