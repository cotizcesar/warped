package com.warped.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.warped.R
import com.warped.domain.model.ModelCapabilities

/**
 * Icon-only capability badges (Vision / Audio / Thinking / Tools).
 *
 * Text badges wrapped vertically on narrow cards ("Tools" rendered T-o-o-l-s);
 * icons fix the layout structurally. Shared by ModelsScreen and
 * UnifiedSelectorScreen so both model lists stay identical.
 */
@Composable
fun CapabilityIconRow(caps: ModelCapabilities) {
    if (!caps.vision && !caps.reasoning && !caps.tools && !caps.audio) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (caps.vision) CapabilityIconBadge(
            icon = Icons.Filled.Visibility,
            contentDescription = stringResource(R.string.badge_vision),
            color = Color(0xFF64B5F6)
        )
        if (caps.audio) CapabilityIconBadge(
            icon = Icons.Filled.Audiotrack,
            contentDescription = stringResource(R.string.badge_audio),
            color = Color(0xFF4CAF50)
        )
        if (caps.reasoning) CapabilityIconBadge(
            icon = Icons.Filled.Psychology,
            contentDescription = stringResource(R.string.badge_thinking),
            color = Color(0xFFFF9800)
        )
        if (caps.tools) CapabilityIconBadge(
            icon = Icons.Filled.Build,
            contentDescription = stringResource(R.string.badge_tools),
            color = Color(0xFF2196F3)
        )
    }
}

@Composable
fun CapabilityIconBadge(
    icon: ImageVector,
    contentDescription: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp).size(14.dp),
            tint = color
        )
    }
}

/**
 * LM-Studio-style capability table: one row per modality with its status.
 * Shared by the catalog expanded details and the Models & Endpoints rows
 * so all three surfaces describe capabilities identically. Only tracked
 * modalities appear (video/PDF have no allowlist flags and are omitted
 * rather than invented).
 *
 * @param vision audio reasoning tools allowlist-verified flags.
 */
@Composable
fun CapabilityTable(
    vision: Boolean,
    audio: Boolean,
    reasoning: Boolean,
    tools: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        CapabilityTableRow(
            icon = Icons.Filled.Audiotrack,
            label = stringResource(R.string.badge_audio),
            status = if (audio) stringResource(R.string.cap_status_input) else "–"
        )
        CapabilityTableRow(
            icon = Icons.Filled.Psychology,
            label = stringResource(R.string.badge_thinking),
            status = if (reasoning) "✓" else "–"
        )
        CapabilityTableRow(
            icon = Icons.Filled.Visibility,
            label = stringResource(R.string.badge_vision),
            status = if (vision) stringResource(R.string.cap_status_input) else "–"
        )
        CapabilityTableRow(
            icon = Icons.Filled.Build,
            label = stringResource(R.string.badge_tools),
            status = if (tools) "✓" else "–",
            showDivider = false
        )
        CapabilityTableRow(
            icon = Icons.Filled.TextFields,
            label = stringResource(R.string.cap_text),
            status = stringResource(R.string.cap_status_inout),
            showDivider = false
        )
    }
}

@Composable
private fun CapabilityTableRow(
    icon: ImageVector,
    label: String,
    status: String,
    showDivider: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color(0xFF9CA3AF)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFECECEC),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF9CA3AF)
        )
    }
    if (showDivider) {
        HorizontalDivider(color = Color(0xFF3A3A38), thickness = 0.5.dp)
    }
}
