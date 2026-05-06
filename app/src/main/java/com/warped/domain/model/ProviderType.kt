package com.warped.domain.model

import androidx.annotation.StringRes
import com.warped.R

enum class ProviderType { OPENAI, ANTHROPIC, OLLAMA, LM_STUDIO, CUSTOM, LOCAL, LITE_RT_LM }

@StringRes
fun ProviderType.displayNameRes(): Int = when (this) {
    ProviderType.OPENAI -> R.string.provider_openai
    ProviderType.ANTHROPIC -> R.string.provider_anthropic
    ProviderType.OLLAMA -> R.string.provider_ollama
    ProviderType.LM_STUDIO -> R.string.provider_lm_studio
    ProviderType.CUSTOM -> R.string.provider_custom
    ProviderType.LOCAL -> R.string.provider_local
    ProviderType.LITE_RT_LM -> R.string.provider_lite_rt_lm
}
