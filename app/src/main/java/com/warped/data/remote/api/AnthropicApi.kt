package com.warped.data.remote.api

import com.warped.data.remote.dto.AnthropicChatRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface AnthropicApi {
    @POST("v1/messages")
    @Headers("Content-Type: application/json")
    suspend fun chatCompletions(@Body request: AnthropicChatRequest): Response<ResponseBody>
}
