package com.warped.di

import com.warped.data.remote.api.TavilyApi
import com.warped.data.remote.network.AuthInterceptor
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import android.content.Context
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Logging that uses BODY level for small API responses (so we can inspect
     * JSON for debugging) and HEADERS-only for large binary downloads. The
     * 614MB+ .litertlm model files would otherwise OOM the body buffer in
     * [HttpLoggingInterceptor]. HF model files always come from `/resolve/main/`.
     */
    @Provides
    @Singleton
    fun provideBodyLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (com.warped.BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(
        body: HttpLoggingInterceptor
    ): Interceptor = Interceptor { chain ->
        val request = chain.request()
        if (request.url.encodedPath.contains("/resolve/main/")) {
            val response = chain.proceed(request)
            Timber.tag("OkHttp")
                .d("--> %s %s", request.method, request.url)
            response.headers.forEach { header ->
                Timber.tag("OkHttp").d("<-- %s: %s", header.first, header.second)
            }
            response
        } else {
            body.intercept(chain)
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: Interceptor,
        authInterceptor: AuthInterceptor
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(5, 1, TimeUnit.MINUTES))
            .build()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    /**
     * PERF-08: SSE-tuned OkHttp client. Streaming responses should NOT be
     * auto-retried on connection failure (the server must explicitly emit
     * another event). The 60s callTimeout covers a hung connection; the
     * 50MB on-disk cache keeps token-throttling responses from re-fetching
     * the model list on every chat open.
     */
    @Provides
    @Singleton
    @Named("sse")
    fun provideSseOkHttpClient(
        @ApplicationContext context: Context,
        authInterceptor: AuthInterceptor,
    ): OkHttpClient {
        val cacheDir = File(context.cacheDir, "okhttp-sse")
        val cache = Cache(cacheDir, 50L * 1024 * 1024)
        val noStoreInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .cacheControl(CacheControl.Builder().noCache().noStore().build())
                .build()
            chain.proceed(request)
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // 0 = no read timeout for SSE
            .callTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(noStoreInterceptor)
            .retryOnConnectionFailure(false)
            .cache(cache)
            .connectionPool(ConnectionPool(5, 1, TimeUnit.MINUTES))
            .build()
    }

    /**
     * Phase 55 (TAV-02, T-55-01/T-55-02): dedicated Tavily HTTP client.
     * Built from scratch with ZERO interceptors — no [AuthInterceptor]
     * (endpoint keys must never reach api.tavily.com) and no body-level
     * logging interceptor (which would print `Authorization: Bearer` and
     * the full request body to logcat). Auth is a per-call header
     * assembled at call time by `TavilySearchRepository`; the key is
     * never logged. `retryOnConnectionFailure(false)` because POST is
     * non-idempotent — the caller retries explicitly.
     */
    @Provides
    @Singleton
    @Named("tavily")
    fun provideTavilyOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .connectionPool(ConnectionPool(5, 1, TimeUnit.MINUTES))
            .build()

    /**
     * Phase 55 (TAV-02): Tavily Retrofit on `https://api.tavily.com/`
     * using the project's shared `Json` converter (ignoreUnknownKeys,
     * lenient, coercing — same conventions as every other DTO).
     */
    @Provides
    @Singleton
    @Named("tavily")
    fun provideTavilyRetrofit(
        @Named("tavily") client: OkHttpClient,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        .client(client)
        .baseUrl("https://api.tavily.com/")
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideTavilyApi(
        @Named("tavily") retrofit: Retrofit,
    ): TavilyApi = retrofit.create(TavilyApi::class.java)
}
