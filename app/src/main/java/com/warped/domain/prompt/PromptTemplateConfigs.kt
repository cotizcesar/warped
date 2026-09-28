package com.warped.domain.prompt

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoSet
import javax.inject.Named

private const val QUALIFIER = "promptTemplate"

@Module
@InstallIn(ViewModelComponent::class)
object PromptTemplateConfigs {

    @Provides
    @IntoSet
    @Named(QUALIFIER)
    fun provideRewriteTemplate(): PromptTemplate = PromptTemplate(
        id = "rewrite",
        name = "Rewrite",
        description = "Rewrite text in clearer, simpler language",
        systemPrompt = "You are an expert editor. Rewrite the user's text in clearer, more concise language while preserving its meaning. Output the rewritten text only — no preamble or commentary.",
        userPromptTemplate = { vars ->
            "Rewrite the following text:\n\n${vars["input"]}"
        },
    )

    @Provides
    @IntoSet
    @Named(QUALIFIER)
    fun provideExtractKeyPointsTemplate(): PromptTemplate = PromptTemplate(
        id = "extractKeyPoints",
        name = "Key points",
        description = "Extract the 5 most important key points as a bulleted list",
        systemPrompt = "You extract the most important key points from text. Output a Markdown bulleted list with exactly 5 concise points.",
        userPromptTemplate = { vars ->
            "Extract the 5 most important key points from the following text:\n\n${vars["input"]}"
        },
    )

    @Provides
    @IntoSet
    @Named(QUALIFIER)
    fun provideCodeExplainTemplate(): PromptTemplate = PromptTemplate(
        id = "codeExplain",
        name = "Code explain",
        description = "Explain code step by step, line by line",
        systemPrompt = "You are an expert programmer and teacher. Explain the provided code in clear, plain language. Walk through it step by step and call out anything subtle or non-obvious. Use Markdown for structure; preserve code formatting in fenced code blocks.",
        userPromptTemplate = { vars ->
            "Explain the following code:\n\n```\n${vars["input"]}\n```"
        },
    )

    @Provides
    @IntoSet
    @Named(QUALIFIER)
    fun provideTranslateTemplate(): PromptTemplate = PromptTemplate(
        id = "translate",
        name = "Translate",
        description = "Translate text into a target language",
        systemPrompt = "You are a professional translator. Translate the user's text into the requested language while preserving tone, idioms, and formatting. Output the translation only.",
        userPromptTemplate = { vars ->
            val lang = vars["language"] ?: "English"
            "Translate the following text into $lang:\n\n${vars["input"]}"
        },
        requiresLanguage = true,
    )

    @Provides
    @IntoSet
    @Named(QUALIFIER)
    fun provideSentimentTemplate(): PromptTemplate = PromptTemplate(
        id = "sentiment",
        name = "Sentiment",
        description = "Classify sentiment (positive / neutral / negative) and explain why",
        systemPrompt = "You classify the sentiment of short or long-form text. Respond with a single Markdown section: one bold label (Positive / Neutral / Negative) followed by 1-3 sentences of justification.",
        userPromptTemplate = { vars ->
            "Classify the sentiment of the following text:\n\n${vars["input"]}"
        },
    )

    @Provides
    @IntoSet
    @Named(QUALIFIER)
    fun provideTableToJsonTemplate(): PromptTemplate = PromptTemplate(
        id = "tableToJson",
        name = "Table to JSON",
        description = "Convert a Markdown or plain-text table to a JSON array of objects",
        systemPrompt = "You convert tables into structured JSON. Output a single fenced JSON code block (```json) containing an array of objects whose keys come from the table header row. Do not add commentary.",
        userPromptTemplate = { vars ->
            "Convert the following table to a JSON array of objects:\n\n${vars["input"]}"
        },
    )
}
