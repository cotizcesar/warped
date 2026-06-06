package com.warped.domain.skill

import com.warped.domain.skill.skills.CalculatorSkill
import com.warped.domain.skill.skills.CurrentTimeSkill
import com.warped.domain.skill.skills.JsonFormatterSkill
import com.warped.domain.skill.skills.SummarizeSkill

object SkillRegistry {
    val all: List<Skill> = listOf(
        CalculatorSkill,
        CurrentTimeSkill,
        JsonFormatterSkill,
        SummarizeSkill,
    )
}
