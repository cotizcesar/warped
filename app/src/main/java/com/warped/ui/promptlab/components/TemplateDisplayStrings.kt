package com.warped.ui.promptlab.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.warped.R
import com.warped.domain.prompt.PromptTemplate

/**
 * Quick-task langmatch-i18n-paragraphs: localized display names and
 * descriptions for the built-in prompt templates, resolved by template id
 * at the UI layer. The domain [PromptTemplate] (name/description/system
 * prompt) stays untouched — system prompts and user-prompt templates are
 * model instructions and must never be translated. Unknown (third-party)
 * template ids fall back to the built-in strings.
 */
@Composable
fun templateDisplayName(template: PromptTemplate): String = when (template.id) {
    "rewrite" -> stringResource(R.string.lab_tpl_rewrite_name)
    "extractKeyPoints" -> stringResource(R.string.lab_tpl_keypoints_name)
    "codeExplain" -> stringResource(R.string.lab_tpl_codeexplain_name)
    "translate" -> stringResource(R.string.lab_tpl_translate_name)
    "sentiment" -> stringResource(R.string.lab_tpl_sentiment_name)
    "tableToJson" -> stringResource(R.string.lab_tpl_tablejson_name)
    else -> template.name
}

@Composable
fun templateDisplayDescription(template: PromptTemplate): String = when (template.id) {
    "rewrite" -> stringResource(R.string.lab_tpl_rewrite_desc)
    "extractKeyPoints" -> stringResource(R.string.lab_tpl_keypoints_desc)
    "codeExplain" -> stringResource(R.string.lab_tpl_codeexplain_desc)
    "translate" -> stringResource(R.string.lab_tpl_translate_desc)
    "sentiment" -> stringResource(R.string.lab_tpl_sentiment_desc)
    "tableToJson" -> stringResource(R.string.lab_tpl_tablejson_desc)
    else -> template.description
}
