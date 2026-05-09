package com.warped.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Chat : Screen("chat", "Chat", Icons.AutoMirrored.Filled.Chat)
    data object Endpoints : Screen("endpoints", "Endpoints", Icons.Filled.Dns)
    data object Models : Screen("models", "Models", Icons.Filled.Memory)
    data object HuggingFace : Screen("huggingface", "HF", Icons.Filled.Search)
    data object Presets : Screen("presets", "Presets", Icons.Filled.Settings)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    data object Help : Screen("help", "Help", Icons.Filled.Info)
    data object Wizard : Screen("wizard", "Wizard", Icons.Filled.Explore)
}
