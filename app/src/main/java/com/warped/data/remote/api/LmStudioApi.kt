package com.warped.data.remote.api

import com.warped.data.remote.dto.LmStudioChatRequest
import com.warped.data.remote.dto.LmStudioModelListResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

interface LmStudioApi {
    @POST("api/v1/chat")
    @Headers("Content-Type: application/json")
    suspend fun chat(@Body request: LmStudioChatRequest): Response<ResponseBody>

    @GET("api/v1/models")
    suspend fun listModels(): Response<LmStudioModelListResponse>
}
