package com.warped.util.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject
import javax.inject.Singleton

interface AppLifecycleProvider {
    val isAppInForeground: Boolean
}

@Singleton
class ProcessLifecycleAppLifecycleProvider @Inject constructor() : AppLifecycleProvider, DefaultLifecycleObserver {
    @Volatile
    private var foreground: Boolean = false

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        foreground = true
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground = false
    }

    override val isAppInForeground: Boolean get() = foreground
}
