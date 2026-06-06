package com.warped.domain.skill.skills

import com.warped.domain.skill.Skill
import com.warped.domain.skill.SkillCategory

val CalculatorSkill = Skill(
    id = "calculator",
    name = "Calculator",
    description = "Evaluate a math expression (e.g. 2 + 3 * 4).",
    category = SkillCategory.Tool,
    icon = "calculate",
)
