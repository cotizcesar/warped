package com.warped.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
            contentDescription = "Vision",
            color = Color(0xFF9C27B0)
        )
        if (caps.audio) CapabilityIconBadge(
            icon = Icons.Filled.Audiotrack,
            contentDescription = "Audio",
            color = Color(0xFF4CAF50)
        )
        if (caps.reasoning) CapabilityIconBadge(
            icon = Icons.Filled.Psychology,
            contentDescription = "Thinking",
            color = Color(0xFFFF9800)
        )
        if (caps.tools) CapabilityIconBadge(
            icon = Icons.Filled.Build,
            contentDescription = "Tools",
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
