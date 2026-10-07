package com.warped.ui.wizard

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.vector.ImageVector
import com.warped.R

/**
 * 2026-10-04 four-page wizard: welcome, local models, network models,
 * privacy. "Local engines" and "LiteRT-LM models" were the same thing
 * (one engine, .litertlm files) — unified. Info-only cards; the bottom
 * bar carries the single Next/Done action.
 */
enum class WizardStep(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
) {
    WELCOME(
        titleRes = R.string.wizard_step_1_title,
        descriptionRes = R.string.wizard_step_1_desc,
        icon = Icons.Filled.AutoAwesome,
    ),
    LOCAL_MODELS(
        titleRes = R.string.wizard_step_2_title,
        descriptionRes = R.string.wizard_step_2_desc,
        icon = Icons.Filled.Memory,
    ),
    NETWORK_MODELS(
        titleRes = R.string.wizard_step_6_title,
        descriptionRes = R.string.wizard_step_6_desc,
        icon = Icons.Filled.Dns,
    ),
    PRIVACY(
        titleRes = R.string.wizard_privacy_title,
        descriptionRes = R.string.wizard_privacy_desc,
        icon = Icons.Filled.Shield,
    ),
}
