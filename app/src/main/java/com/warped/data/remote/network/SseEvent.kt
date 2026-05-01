package com.warped.data.remote.network

data class SseEvent(val data: String, val event: String? = null)
