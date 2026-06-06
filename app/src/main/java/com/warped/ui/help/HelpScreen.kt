package com.warped.ui.help

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "How to Use Warped",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD97757)
                )
                Text(
                    "Run and chat with any LLM — local or remote — from a single Android app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9CA3AF)
                )
            }

            // Section 1: Download Models
            item {
                HelpSection(
                    icon = Icons.Filled.CloudDownload,
                    title = "1. Download a Model",
                    steps = listOf(
                        "Open the drawer menu (top-left icon or swipe) and tap Models.",
                        "Tap \"Open Hugging Face\" to browse LiteRT-LM models.",
                        "Use the tabs to filter model formats.",
                        "Search for a model or browse by name.",
                        "Tap a model to see its available .litertlm files.",
                        "Tap Download on the file you want.",
                        "Monitor progress in the Models tab — you can pause, resume, or cancel downloads.",
                        "For gated/private models: add your HuggingFace Access Token in Settings->Hugging Face.",
                        "For staff pick models (Gemma 3n): visit the model page on huggingface.co to accept the terms, then retry.",
                    )
                )
            }

            // Section 2: Use Local Models
            item {
                HelpSection(
                    icon = Icons.Filled.Memory,
                    title = "2. Chat with a Local Model",
                    steps = listOf(
                        "Go to the Models tab and tap \"Use in chat\" on your downloaded model.",
                        "Or: open the chat dropdown (top bar) and select a local model.",
                        "Type a message and press Enter or tap the send button.",
                        "The model loads into memory on first use (takes a few seconds).",
                        "GPU acceleration is auto-detected for LiteRT-LM models.",
                        "LiteRT-LM models auto-detect the best available backend (GPU or CPU).",
                        "Tap the brush icon (top bar) to unload the model from memory.",
                    )
                )
            }

            // Section 3: Remote Providers
            item {
                HelpSection(
                    icon = Icons.Filled.Dns,
                    title = "3. Connect to Remote LLMs",
                    steps = listOf(
                        "Go to Settings -> Endpoints to configure remote providers.",
                        "Supported: OpenAI-compatible APIs, Ollama, LM Studio, Anthropic, and custom servers.",
                        "Enter the server URL and API key (stored securely in Android Keystore).",
                        "Select your remote model from the chat dropdown (marked with \"Net\" badge).",
                        "Remote providers support token streaming — responses appear in real time.",
                    )
                )
            }

            // Section 4: Multimodal
            item {
                HelpSection(
                    icon = Icons.Filled.Image,
                    title = "4. Vision (Image Input)",
                    steps = listOf(
                        "Tap the image icon in the chat input bar to attach photos.",
                        "Supported for: LiteRT-LM models (Gemma 3n) and LM Studio (remote).",
                        "Not yet supported for OpenAI/Ollama/Anthropic remote providers.",
                        "Image previews appear above the input field — tap X to remove.",
                    )
                )
            }

            // Section 5: Audio
            item {
                HelpSection(
                    icon = Icons.Filled.Mic,
                    title = "5. Audio Input",
                    steps = listOf(
                        "Tap the microphone icon in the chat input bar (available for LiteRT-LM models like Gemma 3n).",
                        "Grant microphone permission when prompted.",
                        "Tap to start recording — the icon turns red with a second counter.",
                        "Tap again to stop. Add optional text and send.",
                        "The model receives audio + text together for multimodal understanding.",
                    )
                )
            }

            // Section 6: Tools
            item {
                HelpSection(
                    icon = Icons.Filled.Settings,
                    title = "6. Tool Calling",
                    steps = listOf(
                        "LiteRT-LM models (Gemma 3n) can use built-in tools automatically.",
                        "Available tools: getCurrentTime (date/time), calculate (math), getDeviceInfo.",
                        "Just ask naturally — the model decides when to call a tool.",
                        "Example: \"What time is it?\" or \"Calculate 156 × 23.5\".",
                        "Tool execution is automatic — you just see the result.",
                    )
                )
            }

            // Section 7: HuggingFace Token
            item {
                HelpSection(
                    icon = Icons.Filled.Key,
                    title = "7. HuggingFace Access Token",
                    steps = listOf(
                        "Go to https://huggingface.co/settings/tokens to create a token.",
                        "In the app: Settings -> Hugging Face -> enter your token (starts with hf_) and tap Save.",
                        "A green \"Token configured\" message confirms it's saved.",
                        "This token is required for downloading gated/private models.",
                        "The token is stored securely using Android Keystore encryption.",
                    )
                )
            }

            // Section 8: Tips
            item {
                HelpSection(
                    icon = Icons.Filled.Info,
                    title = "8. Tips & Shortcuts",
                    steps = listOf(
                        "Enter key sends your message. Shift+Enter adds a new line.",
                        "Use Presets to save generation parameters (temperature, top_p, etc.).",
                        "Models tab shows capability badges: Vision, Audio, Tools, Thinking.",
                        "Swipe left on a conversation in Recents to delete it.",
                        "Chat history is stored locally — nothing is sent to the cloud.",
                        "Downloaded models are stored in app-private storage for security.",
                    )
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun HelpSection(
    icon: ImageVector,
    title: String,
    steps: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2B2B29)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color(0xFFD97757),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            steps.forEachIndexed { i, step ->
                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text(
                        "${i + 1}. ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD97757)
                    )
                    Text(
                        step,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
