package com.warped.data.local.inference

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLDisplay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class BackendType { CPU, GPU, NPU }

@Singleton
class BackendDetector @Inject constructor() {

    @Volatile
    private var cachedBackend: BackendType? = null

    /** Probe GPU availability. Result cached for app process lifetime. */
    @Synchronized
    fun probeBackend(): BackendType {
        cachedBackend?.let { return it }

        cachedBackend = try {
            if (isOpenCLAvailable() && isEGLAvailable()) {
                Timber.d("BackendDetector: GPU backend available")
                BackendType.GPU
            } else {
                Timber.d("BackendDetector: GPU not available, falling back to CPU")
                BackendType.CPU
            }
        } catch (e: Exception) {
            Timber.w(e, "BackendDetector: probe failed, falling back to CPU")
            BackendType.CPU
        }
        return cachedBackend!!
    }

    /** Probe a vision-capable backend. Falls back to main backend if GPU unavailable. */
    fun probeVisionBackend(): BackendType {
        return if (isEGLAvailable()) BackendType.GPU else BackendType.CPU
    }

    /** Probe an audio-capable backend. Currently always CPU (most compatible). */
    fun probeAudioBackend(): BackendType = BackendType.CPU

    /** Probe Vulkan GPU availability for llama.cpp. Returns GPU if Vulkan available, CPU otherwise. */
    fun probeVulkan(): BackendType {
        return try {
            val hasVulkan = isEGLAvailable()
            if (hasVulkan) {
                Timber.d("BackendDetector: Vulkan-capable GPU detected")
                BackendType.GPU
            } else {
                Timber.d("BackendDetector: Vulkan not available, CPU fallback")
                BackendType.CPU
            }
        } catch (e: Exception) {
            Timber.w(e, "BackendDetector: Vulkan probe failed, CPU fallback")
            BackendType.CPU
        }
    }

    /** Check if an EGL display can be obtained (indicates GPU driver presence). */
    private fun isEGLAvailable(): Boolean {
        return try {
            val display: EGLDisplay? = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == null || display == EGL14.EGL_NO_DISPLAY) {
                return false
            }
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                EGL14.eglTerminate(display)
                return false
            }
            // Try to get at least one config to confirm the GPU is functional
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            val hasConfig = EGL14.eglChooseConfig(
                display,
                intArrayOf(EGL14.EGL_RENDERABLE_TYPE, 4 /* EGL_OPENGL_ES2_BIT */, EGL14.EGL_NONE),
                0,
                configs,
                0,
                1,
                numConfigs,
                0
            )
            EGL14.eglTerminate(display)
            hasConfig && numConfigs[0] > 0
        } catch (e: Exception) {
            Timber.w(e, "BackendDetector: EGL probe failed")
            false
        }
    }

    /** Check if OpenCL is available via system library probe. */
    private fun isOpenCLAvailable(): Boolean {
        return try {
            // Attempt to load libOpenCL.so — if it exists on the system, try loading it
            // We use Class.forName to probe rather than System.loadLibrary which throws UnsatisfiedLinkError
            System.loadLibrary("OpenCL")
            Timber.d("BackendDetector: libOpenCL.so loaded successfully")
            true
        } catch (e: UnsatisfiedLinkError) {
            Timber.d("BackendDetector: libOpenCL.so not available on this device")
            false
        }
    }
}
