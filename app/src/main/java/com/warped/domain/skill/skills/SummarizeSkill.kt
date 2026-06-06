package com.warped.domain.skill.skills

import com.warped.domain.skill.Skill
import com.warped.domain.skill.SkillCategory

val SummarizeSkill = Skill(
    id = "summarize",
    name = "Summarize",
    description = "Turn the chat into a summarizer for the next message.",
    category = SkillCategory.PromptTemplate,
    icon = "summarize",
    promptPrefix = "Summarize: ",
    systemPrompt = "You are a summarization specialist. Produce a clear, faithful summary in 3-5 sentences that captures the essential information of the user's text. Output the summary only.",
)
