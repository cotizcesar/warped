package com.warped.ui.wizard

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.warped.R
import com.warped.ui.navigation.Screen

enum class WizardStep(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val ctaLabelRes: Int,
    val icon: ImageVector,
    val ctaRoute: Screen?
) {
    WELCOME(
        titleRes = R.string.wizard_step_1_title,
        descriptionRes = R.string.wizard_step_1_desc,
        ctaLabelRes = R.string.wizard_step_1_cta,
        icon = Icons.Filled.AutoAwesome,
        ctaRoute = null
    ),
    ENGINES(
        titleRes = R.string.wizard_step_2_title,
        descriptionRes = R.string.wizard_step_2_desc,
        ctaLabelRes = R.string.wizard_step_2_cta,
        icon = Icons.Filled.Memory,
        ctaRoute = Screen.Models
    ),
    LITERT_LM(
        titleRes = R.string.wizard_step_4_title,
        descriptionRes = R.string.wizard_step_4_desc,
        ctaLabelRes = R.string.wizard_step_4_cta,
        icon = Icons.Filled.Android,
        ctaRoute = Screen.Models
    ),
    LOCAL_CHAT(
        titleRes = R.string.wizard_step_5_title,
        descriptionRes = R.string.wizard_step_5_desc,
        ctaLabelRes = R.string.wizard_step_5_cta,
        icon = Icons.AutoMirrored.Filled.Chat,
        ctaRoute = Screen.Chat
    ),
    REMOTE_PROVIDERS(
        titleRes = R.string.wizard_step_6_title,
        descriptionRes = R.string.wizard_step_6_desc,
        ctaLabelRes = R.string.wizard_step_6_cta,
        icon = Icons.Filled.Dns,
        ctaRoute = Screen.Endpoints
    ),
    REMOTE_CHAT(
        titleRes = R.string.wizard_step_7_title,
        descriptionRes = R.string.wizard_step_7_desc,
        ctaLabelRes = R.string.wizard_step_7_cta,
        icon = Icons.Filled.Cloud,
        ctaRoute = Screen.Chat
    ),
    PRESETS(
        titleRes = R.string.wizard_step_8_title,
        descriptionRes = R.string.wizard_step_8_desc,
        ctaLabelRes = R.string.wizard_step_8_cta,
        icon = Icons.Filled.Tune,
        ctaRoute = Screen.Presets
    ),
    HISTORY(
        titleRes = R.string.wizard_step_9_title,
        descriptionRes = R.string.wizard_step_9_desc,
        ctaLabelRes = R.string.wizard_step_9_cta,
        icon = Icons.Filled.History,
        ctaRoute = Screen.Chat
    )
}
