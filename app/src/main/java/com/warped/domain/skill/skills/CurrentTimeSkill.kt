package com.warped.domain.skill.skills

import com.warped.domain.skill.Skill
import com.warped.domain.skill.SkillCategory

val CurrentTimeSkill = Skill(
    id = "current_time",
    name = "Time",
    description = "Get the current date and time on this device.",
    category = SkillCategory.Tool,
    icon = "schedule",
)
