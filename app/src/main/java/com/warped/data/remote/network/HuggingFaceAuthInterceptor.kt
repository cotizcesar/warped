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

        val requestWithUa = originalRequest.newBuilder()
            .header("User-Agent", USER_AGENT)
            .build()

        if (requestWithUa.header("Authorization") != null) {
            return chain.proceed(requestWithUa)
        }

        val token = apiKeyStore.getHuggingFaceToken()
        if (token == null) {
            Timber.d("HF Auth: no token for %s", requestWithUa.url)
            return chain.proceed(requestWithUa)
        }

        val tokenStr = String(token)
        token.fill('0')

        val request = requestWithUa.newBuilder()
            .header("Authorization", "Bearer $tokenStr")
            .build()

        Timber.d("HF Auth: added token header to %s", request.url.host)
        return chain.proceed(request)
    }

    private companion object {
        // HF's CloudFront returns 401 "Invalid username or password" for the default
        // OkHttp User-Agent. A real browser UA is accepted without auth for public models.
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 Warped/1.0"
    }
}
