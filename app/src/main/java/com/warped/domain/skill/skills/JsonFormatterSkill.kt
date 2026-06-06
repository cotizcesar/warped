package com.warped.domain.skill.skills

import com.warped.domain.skill.Skill
import com.warped.domain.skill.SkillCategory

val JsonFormatterSkill = Skill(
    id = "json_formatter",
    name = "JSON",
    description = "Format, validate, and minify JSON.",
    category = SkillCategory.Tool,
    icon = "code",
)
