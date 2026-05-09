package com.warped.ui.wizard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.warped.ui.navigation.Screen

enum class WizardStep(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val ctaLabel: String,
    val ctaRoute: String
) {
    WELCOME(
        title = "Bienvenida a Warped",
        description = "Warped te permite ejecutar modelos de lenguaje (LLMs) directamente en tu dispositivo, " +
                "sin conexión a internet. También puedes conectarte a proveedores como OpenAI, Anthropic, " +
                "Ollama y LM Studio. Todo desde una sola app.",
        icon = Icons.Filled.AutoAwesome,
        ctaLabel = "Comenzar",
        ctaRoute = ""
    ),
    ENGINES(
        title = "Motores locales",
        description = "Warped usa dos motores para ejecutar modelos localmente: llama.cpp para modelos GGUF " +
                "(el estándar más usado) y LiteRT-LM de Google para modelos .litertlm optimizados para Android. " +
                "Puedes elegir el que mejor funcione en tu dispositivo.",
        icon = Icons.Filled.Memory,
        ctaLabel = "Ver modelos",
        ctaRoute = Screen.Models.route
    ),
    GGUF_DOWNLOAD(
        title = "Modelos GGUF",
        description = "Los modelos GGUF son el formato estándar para LLMs locales. Puedes buscar y descargar " +
                "modelos desde Hugging Face directamente en la app, con pausa y reanudación de descargas.",
        icon = Icons.Filled.CloudDownload,
        ctaLabel = "Explorar modelos",
        ctaRoute = Screen.HuggingFace.route
    ),
    LITERT_LM(
        title = "Modelos LiteRT-LM",
        description = "LiteRT-LM es el motor de inferencia de Google, optimizado para Android. " +
                "Usa modelos en formato .litertlm con aceleración por GPU y NPU en dispositivos compatibles. " +
                "Importa tus modelos desde el almacenamiento del dispositivo.",
        icon = Icons.Filled.Android,
        ctaLabel = "Importar modelo",
        ctaRoute = Screen.Models.route
    ),
    LOCAL_CHAT(
        title = "Chat local",
        description = "Carga un modelo local, ajusta los parámetros de generación como temperatura y contexto, " +
                "y chatea con respuestas en tiempo real. Todo funciona sin conexión a internet: " +
                "tus conversaciones son privadas y se quedan en tu dispositivo.",
        icon = Icons.AutoMirrored.Filled.Chat,
        ctaLabel = "Ir al chat",
        ctaRoute = Screen.Chat.route
    ),
    REMOTE_PROVIDERS(
        title = "Proveedores remotos",
        description = "Conecta Warped a servicios en la nube como OpenAI, Anthropic, Ollama y LM Studio. " +
                "También puedes agregar servidores personalizados compatibles con la API de OpenAI. " +
                "Tú traes tus propias API keys, nosotros las guardamos seguras.",
        icon = Icons.Filled.Dns,
        ctaLabel = "Configurar endpoints",
        ctaRoute = Screen.Endpoints.route
    ),
    REMOTE_CHAT(
        title = "Chat remoto",
        description = "Selecciona un modelo de cualquiera de tus proveedores configurados y chatea " +
                "con respuestas en streaming. Cambia entre modelos locales y remotos en cualquier momento " +
                "desde el selector del chat.",
        icon = Icons.Filled.Cloud,
        ctaLabel = "Probar chat remoto",
        ctaRoute = Screen.Chat.route
    ),
    PRESETS(
        title = "Presets de generación",
        description = "Guarda tus configuraciones favoritas de parámetros como presets reutilizables. " +
                "Define temperatura, top-p, tokens máximos, semilla y más. " +
                "Cambia entre presets al instante según el tipo de conversación que necesites.",
        icon = Icons.Filled.Tune,
        ctaLabel = "Crear preset",
        ctaRoute = Screen.Presets.route
    ),
    HISTORY(
        title = "Historial de chats",
        description = "Todas tus conversaciones se guardan automáticamente. Explora tu historial, " +
                "retoma chats donde los dejaste, y organiza tus conversaciones por modelo o proveedor.",
        icon = Icons.Filled.History,
        ctaLabel = "Ver historial",
        ctaRoute = Screen.Chat.route
    )
}
