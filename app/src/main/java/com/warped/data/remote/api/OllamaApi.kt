package com.warped.data.remote.api

import com.warped.data.remote.dto.OllamaChatRequest
import com.warped.data.remote.dto.OllamaModelListResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

interface OllamaApi {
    @POST("api/chat")
    @Headers("Content-Type: application/json")
    suspend fun chat(@Body request: OllamaChatRequest): Response<ResponseBody>

    @GET("api/tags")
    suspend fun listModels(): Response<OllamaModelListResponse>
}
