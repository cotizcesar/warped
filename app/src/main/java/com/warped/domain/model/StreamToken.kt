package com.warped.domain.model

sealed interface StreamToken {
    data class Delta(val content: String) : StreamToken
    data object Done : StreamToken
    data class Error(val message: String) : StreamToken
}
