package com.warped.ui.chat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warped.domain.skills.SkillIds
import com.warped.domain.skills.skillChipLabel

/**
 * 47-01 UI-SPEC §2: exactly 3 text-only tool chips above the chat input.
 *
 * State is hoisted as `Map<String, Boolean>` (reasoningEnabled pattern);
 * ViewModel-backed via SkillRepository, so toggles survive rotation, and
 * DataStore-backed, so they survive process death. All-on by default.
 */
@Composable
fun SkillChipsRow(
    skillEnabled: Map<String, Boolean>,
    onToggleSkill: (String) -> Unit,
    chipsEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(SkillIds.TOOL_IDS, key = { it }) { id ->
            val on = skillEnabled[id] ?: true
            val label = skillChipLabel(id)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (on) MaterialTheme.colorScheme.primary else Color.Transparent,
                border = if (on) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                modifier = Modifier
                    .sizeIn(minHeight = 48.dp)
                    .semantics {
                        contentDescription = formatSkillChipA11y(label, on)
                    }
                    .clickable(enabled = chipsEnabled) { onToggleSkill(id) },
            ) {
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                        color = if (on) Color(0xFF1C1C1C) else Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
