package com.warped.ui.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warped.R

private val CardBg = Color(0xFF2B2B29)
private val Accent = Color(0xFFD97757)
private val TextPrimary = Color(0xFFECECEC)
private val TextSecondary = Color(0xFF9CA3AF)

@Composable
fun StepContent(
    step: WizardStep,
    contextData: WizardContextData?,
    onCtaClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = step.icon,
                    contentDescription = stringResource(step.titleRes),
                    tint = Accent,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(step.titleRes),
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = contextDescription(step, contextData),
                    color = TextSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                if (contextData != null) {
                    Spacer(Modifier.height(16.dp))
                    ContextBadge(step, contextData)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        if (step.ctaRoute != null) {
            Button(
                onClick = onCtaClick,
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(step.ctaLabelRes),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ContextBadge(step: WizardStep, contextData: WizardContextData) {
    // Wizard shows no "nothing yet" empty states — badges render only when
    // there is something to count; otherwise the card is info-only.
    val text = when (step) {
        WizardStep.LITERT_LM -> {
            if (contextData.litertlmModelCount > 0) {
                val pluralRes = if (contextData.litertlmModelCount == 1) R.string.wizard_litertlm_singular else R.string.wizard_litertlm_plural
                stringResource(R.string.wizard_badge_has_litertlm, contextData.litertlmModelCount, stringResource(pluralRes))
            } else {
                ""
            }
        }
        WizardStep.REMOTE_PROVIDERS -> {
            if (contextData.endpointCount > 0) {
                val pluralRes = if (contextData.endpointCount == 1) R.string.wizard_endpoint_singular else R.string.wizard_endpoint_plural
                stringResource(R.string.wizard_badge_has_endpoints, contextData.endpointCount, stringResource(pluralRes))
            } else {
                ""
            }
        }
        WizardStep.PRESETS -> {
            if (contextData.presetCount > 0) {
                val pluralRes = if (contextData.presetCount == 1) R.string.wizard_preset_singular else R.string.wizard_preset_plural
                stringResource(R.string.wizard_badge_has_presets, contextData.presetCount, stringResource(pluralRes))
            } else {
                ""
            }
        }
        WizardStep.HISTORY -> {
            if (contextData.chatCount > 0) {
                val pluralRes = if (contextData.chatCount == 1) R.string.wizard_chat_singular else R.string.wizard_chat_plural
                stringResource(R.string.wizard_badge_has_chats, contextData.chatCount, stringResource(pluralRes))
            } else {
                ""
            }
        }
        else -> ""
    }
    if (text.isNotEmpty()) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (contextData.litertlmModelCount > 0 ||
                contextData.endpointCount > 0 || contextData.presetCount > 0 || contextData.chatCount > 0
            ) Accent.copy(alpha = 0.15f) else TextSecondary.copy(alpha = 0.1f)
        ) {
            Text(
                text = text,
                color = if (contextData.litertlmModelCount > 0 ||
                    contextData.endpointCount > 0 || contextData.presetCount > 0 || contextData.chatCount > 0
                ) Accent else TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun contextDescription(step: WizardStep, contextData: WizardContextData?): String {
    if (contextData == null) return stringResource(step.descriptionRes)

    return when (step) {
        WizardStep.LITERT_LM -> {
            if (contextData.litertlmModelCount > 0) {
                val pluralRes = if (contextData.litertlmModelCount == 1) R.string.wizard_litertlm_singular else R.string.wizard_litertlm_plural
                stringResource(R.string.wizard_step_4_desc_has, contextData.litertlmModelCount, stringResource(pluralRes))
            } else {
                stringResource(step.descriptionRes)
            }
        }
        WizardStep.REMOTE_PROVIDERS -> {
            if (contextData.endpointCount > 0) {
                val pluralRes = if (contextData.endpointCount == 1) R.string.wizard_endpoint_singular else R.string.wizard_endpoint_plural
                stringResource(R.string.wizard_step_6_desc_has, contextData.endpointCount, stringResource(pluralRes))
            } else {
                stringResource(step.descriptionRes)
            }
        }
        WizardStep.PRESETS -> {
            if (contextData.presetCount > 0) {
                val pluralRes = if (contextData.presetCount == 1) R.string.wizard_preset_singular else R.string.wizard_preset_plural
                stringResource(R.string.wizard_step_8_desc_has, contextData.presetCount, stringResource(pluralRes))
            } else {
                stringResource(step.descriptionRes)
            }
        }
        WizardStep.HISTORY -> {
            if (contextData.chatCount > 0) {
                val pluralRes = if (contextData.chatCount == 1) R.string.wizard_chat_singular else R.string.wizard_chat_plural
                stringResource(R.string.wizard_step_9_desc_has, contextData.chatCount, stringResource(pluralRes))
            } else {
                stringResource(step.descriptionRes)
            }
        }
        else -> stringResource(step.descriptionRes)
    }
}
