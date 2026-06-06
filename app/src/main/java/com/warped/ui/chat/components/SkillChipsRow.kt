package com.warped.ui.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.warped.domain.skill.Skill
import com.warped.domain.skill.SkillCategory

/**
 * 44-03: horizontally-scrollable row of FilterChips, one per skill. Tapping
 * a chip toggles whether that skill is active for the next chat request.
 *
 * - Tool-category skills: render with a wrench/build icon
 * - PromptTemplate-category skills: render with a description icon
 */
@Composable
fun SkillChipsRow(
    skills: List<Skill>,
    selectedSkillIds: Set<String>,
    onToggleSkill: (Skill) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (skills.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(skills, key = { it.id }) { skill ->
            val selected = skill.id in selectedSkillIds
            FilterChip(
                selected = selected,
                onClick = { onToggleSkill(skill) },
                label = { Text(skill.name) },
                leadingIcon = {
                    Icon(
                        imageVector = skill.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}

private fun Skill.icon(): ImageVector = when (category) {
    SkillCategory.Tool -> Icons.Filled.Build
    SkillCategory.PromptTemplate -> Icons.Filled.Description
}
