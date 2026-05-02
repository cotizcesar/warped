package com.warped.data.repository

import com.warped.data.remote.api.HuggingFaceApi
import com.warped.data.remote.dto.HuggingFaceModel
import com.warped.data.remote.dto.HuggingFaceModelDetail
import com.warped.domain.repository.HuggingFaceRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HuggingFaceRepositoryImpl @Inject constructor(
    okHttpClient: OkHttpClient,
    json: Json
) : HuggingFaceRepository {

    private val client = okHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://huggingface.co/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val api = retrofit.create(HuggingFaceApi::class.java)

    override suspend fun searchModels(query: String, format: String, limit: Int): Result<List<HuggingFaceModel>> {
        return try {
            val response = api.searchModels(query = query, filter = format, limit = limit)
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception("Search failed: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getModelDetail(modelId: String): Result<HuggingFaceModelDetail> {
        return try {
            val response = api.getModelDetail(modelId)
            if (response.isSuccessful) {
                Result.success(response.body() ?: throw Exception("Empty response"))
            } else {
                Result.failure(Exception("Failed to get model: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
