package com.warped.data.remote.api

import com.warped.data.remote.dto.OpenAiChatRequest
import com.warped.data.remote.dto.OpenAiModelListResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

interface OpenAiApi {
    @POST("v1/chat/completions")
    @Headers("Content-Type: application/json")
    suspend fun chatCompletions(@Body request: OpenAiChatRequest): Response<ResponseBody>

    @GET("v1/models")
    suspend fun listModels(): Response<OpenAiModelListResponse>
}
