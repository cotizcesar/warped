package com.warped.data.remote.api

import com.warped.data.remote.dto.HuggingFaceModel
import com.warped.data.remote.dto.HuggingFaceModelDetail
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface HuggingFaceApi {
    @GET("api/models")
    suspend fun searchModels(
        @Query("search") query: String,
        @Query("filter") filter: String = "gguf",
        @Query("sort") sort: String = "downloads",
        @Query("direction") direction: String = "-1",
        @Query("limit") limit: Int = 20
    ): Response<List<HuggingFaceModel>>

    @GET("api/models/{modelId}")
    suspend fun getModelDetail(
        @Path(value = "modelId", encoded = true) modelId: String
    ): Response<HuggingFaceModelDetail>
}
