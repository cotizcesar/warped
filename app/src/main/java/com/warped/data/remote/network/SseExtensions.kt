package com.warped.data.remote.network

import com.warped.data.remote.dto.OpenAiCompletionsStreamChunk
import com.warped.data.remote.dto.OpenAiResponsesStreamEvent
import com.warped.data.remote.dto.OpenAiStreamChunk
import com.warped.data.remote.dto.OllamaStreamChunk
import com.warped.domain.model.StreamToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import java.io.IOException

fun ResponseBody.asSseFlow(json: Json): Flow<StreamToken> = flow {
    val parser = SseParser()
    try {
        val source = source()
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: continue
            val events = parser.feed(line + "\n")
            for (event in events) {
                when {
                    event.data == "[DONE]" -> {
                        emit(StreamToken.Done())
                        return@flow
                    }
                    event.data.isBlank() -> continue
                    else -> {
                        try {
                            val chunk = json.decodeFromString<OpenAiStreamChunk>(event.data)
                            val content = chunk.choices.firstOrNull()?.delta?.content
                            if (content != null) {
                                emit(StreamToken.Delta(content))
                            }
                        } catch (_: Exception) { /* skip malformed JSON */ }
                    }
                }
            }
        }
    } catch (e: IOException) {
        emit(StreamToken.Error("Connection lost: ${e.message}"))
    } finally {
        parser.reset()
        close()
    }
}.flowOn(Dispatchers.IO)

fun ResponseBody.asOllamaFlow(json: Json): Flow<StreamToken> = flow {
    try {
        val source = source()
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: continue
            if (line.isBlank()) continue
            try {
                val chunk = json.decodeFromString<OllamaStreamChunk>(line)
                if (chunk.done) {
                    emit(StreamToken.Done())
                    return@flow
                }
                val content = chunk.message?.content
                if (content != null) {
                    emit(StreamToken.Delta(content))
                }
            } catch (_: Exception) { /* skip malformed JSON */ }
        }
    } catch (e: IOException) {
        emit(StreamToken.Error("Connection lost: ${e.message}"))
    } finally {
        close()
    }
}.flowOn(Dispatchers.IO)

fun ResponseBody.asResponsesSseFlow(json: Json): Flow<StreamToken> = flow {
    val parser = SseParser()
    try {
        val source = source()
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: continue
            val events = parser.feed(line + "\n")
            for (event in events) {
                if (event.data.isBlank() || event.data == "[DONE]") continue
                try {
                    val streamEvent = json.decodeFromString<OpenAiResponsesStreamEvent>(event.data)
                    when (streamEvent.type) {
                        "response.output_text.delta" -> {
                            streamEvent.delta?.delta?.let { emit(StreamToken.Delta(it)) }
                        }
                        "response.completed" -> {
                            emit(StreamToken.Done())
                            return@flow
                        }
                        "error" -> {
                            emit(StreamToken.Error(streamEvent.delta?.content ?: "Responses API error"))
                            return@flow
                        }
                    }
                } catch (_: Exception) { /* skip unknown events */ }
            }
        }
    } catch (e: IOException) {
        emit(StreamToken.Error("Connection lost: ${e.message}"))
    } finally {
        parser.reset()
        close()
    }
}.flowOn(Dispatchers.IO)

fun ResponseBody.asCompletionsSseFlow(json: Json): Flow<StreamToken> = flow {
    val parser = SseParser()
    try {
        val source = source()
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: continue
            val events = parser.feed(line + "\n")
            for (event in events) {
                when {
                    event.data == "[DONE]" -> {
                        emit(StreamToken.Done())
                        return@flow
                    }
                    event.data.isBlank() -> continue
                    else -> {
                        try {
                            val chunk = json.decodeFromString<OpenAiCompletionsStreamChunk>(event.data)
                            val text = chunk.choices.firstOrNull()?.text
                            if (!text.isNullOrEmpty()) {
                                emit(StreamToken.Delta(text))
                            }
                        } catch (_: Exception) { /* skip malformed JSON */ }
                    }
                }
            }
        }
    } catch (e: IOException) {
        emit(StreamToken.Error("Connection lost: ${e.message}"))
    } finally {
        parser.reset()
        close()
    }
}.flowOn(Dispatchers.IO)
