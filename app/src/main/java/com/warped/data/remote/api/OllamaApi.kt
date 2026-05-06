package com.warped.data.remote.api

import com.warped.data.remote.dto.OllamaChatRequest
import com.warped.data.remote.dto.OllamaCreateRequest
import com.warped.data.remote.dto.OllamaDeleteRequest
import com.warped.data.remote.dto.OllamaEmbedRequest
import com.warped.data.remote.dto.OllamaEmbedResponse
import com.warped.data.remote.dto.OllamaGenerateRequest
import com.warped.data.remote.dto.OllamaModelListResponse
import com.warped.data.remote.dto.OllamaPsResponse
import com.warped.data.remote.dto.OllamaPullRequest
import com.warped.data.remote.dto.OllamaShowRequest
import com.warped.data.remote.dto.OllamaShowResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Streaming

interface OllamaApi {
    @Streaming
    @POST("api/chat")
    @Headers("Content-Type: application/json")
    suspend fun chat(@Body request: OllamaChatRequest): Response<ResponseBody>

    @Streaming
    @POST("api/generate")
    @Headers("Content-Type: application/json")
    suspend fun generate(@Body request: OllamaGenerateRequest): Response<ResponseBody>

    @POST("api/embed")
    @Headers("Content-Type: application/json")
    suspend fun embed(@Body request: OllamaEmbedRequest): Response<OllamaEmbedResponse>

    @GET("api/ps")
    suspend fun ps(): Response<OllamaPsResponse>

    @POST("api/show")
    @Headers("Content-Type: application/json")
    suspend fun show(@Body request: OllamaShowRequest): Response<OllamaShowResponse>

    @Streaming
    @POST("api/create")
    @Headers("Content-Type: application/json")
    suspend fun create(@Body request: OllamaCreateRequest): Response<ResponseBody>

    @DELETE("api/delete")
    @Headers("Content-Type: application/json")
    suspend fun delete(@Body request: OllamaDeleteRequest): Response<ResponseBody>

    @Streaming
    @POST("api/pull")
    @Headers("Content-Type: application/json")
    suspend fun pull(@Body request: OllamaPullRequest): Response<ResponseBody>

    @GET("api/tags")
    suspend fun listModels(): Response<OllamaModelListResponse>
}
