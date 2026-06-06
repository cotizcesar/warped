package com.warped.data.repository

import com.warped.data.remote.dto.LmStudioModelData

/**
 * Caches the last successful response from any LM Studio `listModels` call so that
 * the form can show capability icons (vision, tool-use) next to each model in
 * the dropdown. Populated by [com.warped.data.remote.provider.LMStudioProvider.listModels].
 */
object LmStudioModelCache {
    @Volatile
    var lastData: List<LmStudioModelData> = emptyList()
}
