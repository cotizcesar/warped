package com.warped.ui.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
                    contentDescription = step.title,
                    tint = Accent,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = step.title,
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

        if (step.ctaRoute.isNotEmpty()) {
            Button(
                onClick = onCtaClick,
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = step.ctaLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ContextBadge(step: WizardStep, contextData: WizardContextData) {
    val text = when (step) {
        WizardStep.GGUF_DOWNLOAD -> {
            if (contextData.ggufModelCount > 0) {
                "Ya tienes ${contextData.ggufModelCount} ${if (contextData.ggufModelCount == 1) "modelo" else "modelos"} GGUF descargado${if (contextData.ggufModelCount != 1) "s" else ""}"
            } else {
                "Aún no tienes modelos GGUF"
            }
        }
        WizardStep.LITERT_LM -> {
            if (contextData.litertlmModelCount > 0) {
                "Ya tienes ${contextData.litertlmModelCount} ${if (contextData.litertlmModelCount == 1) "modelo" else "modelos"} LiteRT-LM importado${if (contextData.litertlmModelCount != 1) "s" else ""}"
            } else {
                "Aún no tienes modelos LiteRT-LM"
            }
        }
        WizardStep.REMOTE_PROVIDERS -> {
            if (contextData.endpointCount > 0) {
                "Ya tienes ${contextData.endpointCount} ${if (contextData.endpointCount == 1) "endpoint" else "endpoints"} configurado${if (contextData.endpointCount != 1) "s" else ""}"
            } else {
                "Aún no tienes endpoints configurados"
            }
        }
        WizardStep.PRESETS -> {
            if (contextData.presetCount > 0) {
                "Ya tienes ${contextData.presetCount} ${if (contextData.presetCount == 1) "preset" else "presets"} guardado${if (contextData.presetCount != 1) "s" else ""}"
            } else {
                "Aún no tienes presets guardados"
            }
        }
        WizardStep.HISTORY -> {
            if (contextData.chatCount > 0) {
                "Ya tienes ${contextData.chatCount} ${if (contextData.chatCount == 1) "conversación" else "conversaciones"} en tu historial"
            } else {
                "Aún no tienes conversaciones"
            }
        }
        else -> null
    }
    if (text != null) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (text.startsWith("Ya")) Accent.copy(alpha = 0.15f) else TextSecondary.copy(alpha = 0.1f)
        ) {
            Text(
                text = text,
                color = if (text.startsWith("Ya")) Accent else TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

private fun contextDescription(step: WizardStep, contextData: WizardContextData?): String {
    if (contextData == null) return step.description

    return when (step) {
        WizardStep.GGUF_DOWNLOAD -> {
            if (contextData.ggufModelCount > 0) {
                "Ya tienes ${contextData.ggufModelCount} ${if (contextData.ggufModelCount == 1) "modelo GGUF descargado" else "modelos GGUF descargados"}. " +
                        "¡Explora el catálogo de Hugging Face para encontrar más modelos!"
            } else {
                "Descarga tu primer modelo GGUF desde Hugging Face. " +
                        "Hay miles de modelos disponibles: desde 1B hasta 70B parámetros."
            }
        }
        WizardStep.LITERT_LM -> {
            if (contextData.litertlmModelCount > 0) {
                "Ya tienes ${contextData.litertlmModelCount} ${if (contextData.litertlmModelCount == 1) "modelo LiteRT-LM importado" else "modelos LiteRT-LM importados"}. " +
                        "Puedes importar más desde el almacenamiento de tu dispositivo."
            } else {
                "Importa tu primer modelo .litertlm desde el almacenamiento de tu dispositivo. " +
                        "LiteRT-LM está optimizado para Android con aceleración por GPU."
            }
        }
        WizardStep.REMOTE_PROVIDERS -> {
            if (contextData.endpointCount > 0) {
                "Ya tienes ${contextData.endpointCount} ${if (contextData.endpointCount == 1) "endpoint configurado" else "endpoints configurados"}. " +
                        "¡Conéctate a más proveedores o administra los existentes!"
            } else {
                "Configura tu primer endpoint para conectarte a OpenAI, Anthropic, Ollama o LM Studio. " +
                        "Tus API keys se guardan de forma segura en el dispositivo."
            }
        }
        WizardStep.PRESETS -> {
            if (contextData.presetCount > 0) {
                "Ya tienes ${contextData.presetCount} ${if (contextData.presetCount == 1) "preset guardado" else "presets guardados"}. " +
                        "Crea nuevos presets para diferentes estilos de conversación."
            } else {
                "Crea tu primer preset de generación. Define temperatura, tokens máximos, " +
                        "semilla y más para reutilizar tus configuraciones favoritas."
            }
        }
        WizardStep.HISTORY -> {
            if (contextData.chatCount > 0) {
                "Ya tienes ${contextData.chatCount} ${if (contextData.chatCount == 1) "conversación" else "conversaciones"} en tu historial. " +
                        "Retoma cualquiera de ellas desde el panel de conversaciones."
            } else {
                "Tu historial de conversaciones aparecerá aquí. " +
                        "Cada chat se guarda automáticamente para que puedas retomarlo cuando quieras."
            }
        }
        else -> step.description
    }
}
