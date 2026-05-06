package com.warped.data.remote.api

import com.warped.data.remote.dto.OpenAiChatRequest
import com.warped.data.remote.dto.OpenAiModelListResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Url

interface CustomApi {
    @Streaming
    @POST
    @Headers("Content-Type: application/json")
    suspend fun chatCompletions(@Url chatPath: String, @Body request: OpenAiChatRequest): Response<ResponseBody>

    @GET
    suspend fun listModels(@Url modelsPath: String): Response<OpenAiModelListResponse>
}
