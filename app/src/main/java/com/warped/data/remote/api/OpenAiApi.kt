package com.warped.data.remote.api

import com.warped.data.remote.dto.OpenAiChatRequest
import com.warped.data.remote.dto.OpenAiCompletionsRequest
import com.warped.data.remote.dto.OpenAiEmbeddingsRequest
import com.warped.data.remote.dto.OpenAiEmbeddingsResponse
import com.warped.data.remote.dto.OpenAiModelListResponse
import com.warped.data.remote.dto.OpenAiResponsesRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Streaming

interface OpenAiApi {
    @Streaming
    @POST("v1/chat/completions")
    @Headers("Content-Type: application/json")
    suspend fun chatCompletions(@Body request: OpenAiChatRequest): Response<ResponseBody>

    @Streaming
    @POST("v1/responses")
    @Headers("Content-Type: application/json")
    suspend fun responses(@Body request: OpenAiResponsesRequest): Response<ResponseBody>

    @POST("v1/embeddings")
    @Headers("Content-Type: application/json")
    suspend fun embeddings(@Body request: OpenAiEmbeddingsRequest): Response<OpenAiEmbeddingsResponse>

    @Streaming
    @POST("v1/completions")
    @Headers("Content-Type: application/json")
    suspend fun completions(@Body request: OpenAiCompletionsRequest): Response<ResponseBody>

    @GET("v1/models")
    suspend fun listModels(): Response<OpenAiModelListResponse>
}
