package com.warped.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

sealed interface Screen {
    @Serializable
    data object Chat : Screen

    @Serializable
    data class ChatDetail(val conversationId: Long) : Screen

    @Serializable
    data class NewChat(val newChat: Boolean = true) : Screen

    @Serializable
    data object Selector : Screen

    @Serializable
    data object Models : Screen

    @Serializable
    data object Endpoints : Screen

    @Serializable
    data object HuggingFace : Screen

    @Serializable
    data object Presets : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data object Help : Screen

    @Serializable
    data object Wizard : Screen

    @Serializable
    data class WizardReview(val review: Boolean = false) : Screen

    @Serializable
    data object PromptLab : Screen

    @Serializable
    data object Benchmark : Screen
}

val Screen.route: String
    get() = when (this) {
        Screen.Chat -> "chat"
        is Screen.ChatDetail -> "chat/$conversationId"
        is Screen.NewChat -> "chat?newChat=$newChat"
        Screen.Selector -> "selector"
        Screen.Models -> "models"
        Screen.Endpoints -> "endpoints"
        Screen.HuggingFace -> "huggingface"
        Screen.Presets -> "presets"
        Screen.Settings -> "settings"
        Screen.Help -> "help"
        Screen.Wizard -> "wizard"
        is Screen.WizardReview -> "wizard?review=$review"
        Screen.PromptLab -> "promptlab"
        Screen.Benchmark -> "benchmark"
    }

val Screen.label: String
    get() = when (this) {
        Screen.Chat -> "Chat"
        is Screen.ChatDetail -> "Chat"
        is Screen.NewChat -> "Chat"
        Screen.Selector -> "Models & Endpoints"
        Screen.Models -> "Models"
        Screen.Endpoints -> "Endpoints"
        Screen.HuggingFace -> "HF"
        Screen.Presets -> "Presets"
        Screen.Settings -> "Settings"
        Screen.Help -> "Help"
        Screen.Wizard -> "Wizard"
        is Screen.WizardReview -> "Wizard"
        Screen.PromptLab -> "Prompt Lab"
        Screen.Benchmark -> "Benchmark"
    }

val Screen.icon: ImageVector
    get() = when (this) {
        Screen.Chat -> Icons.AutoMirrored.Filled.Chat
        is Screen.ChatDetail -> Icons.AutoMirrored.Filled.Chat
        is Screen.NewChat -> Icons.AutoMirrored.Filled.Chat
        Screen.Selector -> Icons.Filled.Dns
        Screen.Models -> Icons.Filled.Memory
        Screen.Endpoints -> Icons.Filled.Dns
        Screen.HuggingFace -> Icons.Filled.Search
        Screen.Presets -> Icons.Filled.Settings
        Screen.Settings -> Icons.Filled.Settings
        Screen.Help -> Icons.Filled.Info
        Screen.Wizard -> Icons.Filled.Explore
        is Screen.WizardReview -> Icons.Filled.Explore
        Screen.PromptLab -> Icons.Filled.Science
        Screen.Benchmark -> Icons.Filled.Science
    }
