package com.warped.data.remote.network

import com.warped.data.local.security.ApiKeyStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val apiKeyStore: ApiKeyStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val endpointId = originalRequest.tag(Long::class.java) ?: return chain.proceed(originalRequest)

        val apiKey = apiKeyStore.getKey(endpointId)
        if (apiKey == null || apiKey.isEmpty()) {
            return chain.proceed(originalRequest)
        }

        val key = String(apiKey)
        apiKey.fill('0')

        val request = originalRequest.newBuilder()
            .header("Authorization", "Bearer $key")
            .build()

        return chain.proceed(request)
    }
}
