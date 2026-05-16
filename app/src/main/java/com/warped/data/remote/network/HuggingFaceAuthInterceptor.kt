package com.warped.data.remote.network

import com.warped.data.local.security.ApiKeyStore
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HuggingFaceAuthInterceptor @Inject constructor(
    private val apiKeyStore: ApiKeyStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val host = originalRequest.url.host

        if (host != "huggingface.co" && !host.endsWith(".huggingface.co")) {
            return chain.proceed(originalRequest)
        }

        if (originalRequest.header("Authorization") != null) {
            return chain.proceed(originalRequest)
        }

        val token = apiKeyStore.getHuggingFaceToken()
        if (token == null) {
            Timber.d("HF Auth: no token for %s", originalRequest.url)
            return chain.proceed(originalRequest)
        }

        val tokenStr = String(token)
        token.fill('0')

        val request = originalRequest.newBuilder()
            .header("Authorization", "Bearer $tokenStr")
            .build()

        Timber.d("HF Auth: added token header to %s", request.url.host)
        return chain.proceed(request)
    }
}
