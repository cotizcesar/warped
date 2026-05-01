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

    private external fun nativeLoadModel(path: String, nThreads: Int, nCtx: Int): Boolean
    private external fun nativeGenerate(prompt: String, callback: TokenCallback)
    private external fun nativeStop()
    private external fun nativeUnload()
    private external fun nativeIsLoaded(): Boolean
    private external fun nativeGetModelInfo(): String

    interface TokenCallback {
        fun onToken(token: String, done: Boolean)
    }

    fun loadModel(path: String, nThreads: Int = 4, nCtx: Int = 4096): Boolean {
        return nativeLoadModel(path, nThreads, nCtx)
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
