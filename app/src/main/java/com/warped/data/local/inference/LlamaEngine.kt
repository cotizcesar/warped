package com.warped.data.local.inference

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlamaEngine @Inject constructor() {

    companion object {
        init {
            System.loadLibrary("warped_llama")
        }
    }

    interface LoadProgressCallback {
        fun onProgress(percent: Int, message: String)
    }

    interface TokenCallback {
        fun onToken(token: String, done: Boolean)
    }

    @Volatile
    private var isGenerating = false

    private external fun nativeLoadModel(
        path: String,
        nThreads: Int,
        nCtx: Int,
        progressCallback: LoadProgressCallback?
    ): String?

    private external fun nativeGenerate(prompt: String, callback: TokenCallback)
    private external fun nativeStop()
    private external fun nativeUnload()
    private external fun nativeIsLoaded(): Boolean
    private external fun nativeGetModelInfo(): String

    @Synchronized
    fun loadModel(
        path: String,
        nThreads: Int = 4,
        nCtx: Int = 4096,
        onProgress: ((percent: Int, message: String) -> Unit)? = null
    ): Result<Unit> {
        val callback = if (onProgress != null) {
            object : LoadProgressCallback {
                override fun onProgress(percent: Int, message: String) {
                    onProgress(percent, message)
                }
            }
        } else null

        val error = nativeLoadModel(path, nThreads, nCtx, callback)
        return if (error == null) {
            Result.success(Unit)
        } else {
            Result.failure(LlamaLoadError.fromNative(error))
        }
    }

    @Synchronized
    fun generate(prompt: String): Flow<String> = callbackFlow {
        if (isGenerating) {
            trySend("Generation already in progress")
            close(IllegalStateException("Concurrent generation prevented"))
            return@callbackFlow
        }
        if (!isLoaded()) {
            trySend("Model not loaded")
            close()
            return@callbackFlow
        }

        isGenerating = true
        try {
            val callback = object : TokenCallback {
                override fun onToken(token: String, done: Boolean) {
                    if (done) {
                        close()
                    } else if (token.isNotEmpty()) {
                        trySend(token)
                    }
                }
            }
            nativeGenerate(prompt, callback)
            awaitClose {
                nativeStop()
                isGenerating = false
            }
        } catch (e: Exception) {
            isGenerating = false
            throw e
        }
    }

    @Synchronized
    fun stop() {
        nativeStop()
    }

    @Synchronized
    fun unload() {
        if (isGenerating) {
            nativeStop()
        }
        nativeUnload()
        isGenerating = false
    }

    fun isLoaded(): Boolean = nativeIsLoaded()

    fun isBusy(): Boolean = isGenerating

    fun getModelInfo(): String = nativeGetModelInfo()
}
