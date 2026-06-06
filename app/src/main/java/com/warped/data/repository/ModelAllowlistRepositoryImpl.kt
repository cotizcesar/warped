package com.warped.data.repository

import android.content.Context
import com.warped.domain.model.AllowlistEntry
import com.warped.domain.model.ModelAllowlist
import com.warped.domain.repository.ModelAllowlistRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelAllowlistRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) : ModelAllowlistRepository {

    private val entries: List<AllowlistEntry> by lazy { loadEntries() }
    private val entriesFlow = MutableStateFlow<List<AllowlistEntry>>(emptyList())

    private fun loadEntries(): List<AllowlistEntry> {
        return try {
            val raw = context.assets.open(ALLOWLIST_ASSET).bufferedReader().use { it.readText() }
            val parsed = json.decodeFromString<ModelAllowlist>(raw)
            entriesFlow.value = parsed.models
            parsed.models
        } catch (e: Exception) {
            Timber.e(e, "Failed to load $ALLOWLIST_ASSET — allowlist is empty")
            emptyList()
        }
    }

    override suspend fun getAll(): List<AllowlistEntry> = withContext(Dispatchers.IO) {
        entries
    }

    override fun observeAll(): Flow<List<AllowlistEntry>> = entriesFlow.asStateFlow()

    override fun findById(modelId: String): AllowlistEntry? {
        if (modelId.isBlank()) return null
        val cached = entries
        if (cached.isEmpty()) return null
        cached.firstOrNull { it.name == modelId || it.id == modelId }?.let { return it }
        val lowered = modelId.lowercase()
        return cached.firstOrNull { entry ->
            entry.modelFile.contains(lowered, ignoreCase = true) ||
                entry.name.contains(lowered, ignoreCase = true)
        }
    }

    override fun supportsThinking(modelId: String): Boolean =
        findById(modelId)?.hasCapability(CAPABILITY_THINKING) == true

    override fun supportsSpeculativeDecoding(modelId: String): Boolean =
        findById(modelId)?.hasCapability(CAPABILITY_SPEC_DECODING) == true

    override fun supportsVision(modelId: String): Boolean =
        findById(modelId)?.hasCapability(CAPABILITY_VISION) == true

    private companion object {
        const val ALLOWLIST_ASSET = "model_allowlist.json"
        const val CAPABILITY_THINKING = "llm_thinking"
        const val CAPABILITY_SPEC_DECODING = "llm_spec_decoding"
        const val CAPABILITY_VISION = "llm_vision"
    }
}
