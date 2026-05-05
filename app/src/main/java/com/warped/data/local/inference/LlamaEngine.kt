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

    fun generate(prompt: String): Flow<String> = callbackFlow {
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
        awaitClose { nativeStop() }
    }

    fun stop() {
        nativeStop()
    }

    fun unload() {
        nativeUnload()
    }

    fun isLoaded(): Boolean = nativeIsLoaded()

    fun getModelInfo(): String = nativeGetModelInfo()
}
