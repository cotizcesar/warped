package com.warped.data.remote.api

import com.warped.data.remote.dto.TavilySearchRequest
import com.warped.data.remote.dto.TavilySearchResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Phase 55 (TAV-02): Tavily Search API (`POST https://api.tavily.com/search`).
 *
 * Auth is a per-call `Authorization: Bearer <key>` header assembled at
 * call time by [com.warped.data.grounding.TavilySearchRepository] — never
 * a static interceptor holding the key, never `api_key` in the body.
 * Served by the dedicated `@Named("tavily")` Retrofit instance (no
 * `AuthInterceptor`, no body-level logging) in `NetworkModule`.
 */
interface TavilyApi {
    @POST("search")
    @Headers("Content-Type: application/json")
    suspend fun search(
        @Header("Authorization") auth: String,
        @Body request: TavilySearchRequest,
    ): Response<TavilySearchResponse>
}
