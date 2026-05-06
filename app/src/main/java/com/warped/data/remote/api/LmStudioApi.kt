package com.warped.data.remote.api

import com.warped.data.remote.dto.LmStudioChatRequest
import com.warped.data.remote.dto.LmStudioDownloadRequest
import com.warped.data.remote.dto.LmStudioDownloadResponse
import com.warped.data.remote.dto.LmStudioDownloadStatusResponse
import com.warped.data.remote.dto.LmStudioLoadRequest
import com.warped.data.remote.dto.LmStudioLoadResponse
import com.warped.data.remote.dto.LmStudioModelListResponse
import com.warped.data.remote.dto.LmStudioUnloadRequest
import com.warped.data.remote.dto.LmStudioUnloadResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

interface LmStudioApi {
    @POST("api/v1/chat")
    @Headers("Content-Type: application/json")
    suspend fun chat(@Body request: LmStudioChatRequest): Response<ResponseBody>

    @GET("api/v1/models")
    suspend fun listModels(): Response<LmStudioModelListResponse>

    @POST("api/v1/models/load")
    @Headers("Content-Type: application/json")
    suspend fun loadModel(@Body request: LmStudioLoadRequest): Response<LmStudioLoadResponse>

    @POST("api/v1/models/unload")
    @Headers("Content-Type: application/json")
    suspend fun unloadModel(@Body request: LmStudioUnloadRequest): Response<LmStudioUnloadResponse>

    @POST("api/v1/models/download")
    @Headers("Content-Type: application/json")
    suspend fun downloadModel(@Body request: LmStudioDownloadRequest): Response<LmStudioDownloadResponse>

    @GET("api/v1/models/download/status/{jobId}")
    suspend fun downloadStatus(@Path("jobId") jobId: String): Response<LmStudioDownloadStatusResponse>
}
