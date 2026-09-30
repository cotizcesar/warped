package com.warped.data.remote.provider

import com.warped.data.remote.dto.OpenAiMessage
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.Role
import com.warped.domain.model.toProviderText

/**
 * Quick-task (remote-image-carry): canonical K newest-first history-image
 * carry rule, shared by the local engine path and all remote providers.
 *
 * The local path ([com.warped.data.local.inference.LiteRTLmProvider.buildHistoryMessages])
 * is the canonical implementation this mirrors: among history USER turns
 * (the current message — last element — is always excluded), the K most
 * recent image-bearing turns carry their images; identical data-URL
 * strings dedupe newest-wins; unusable entries ([isUsable]) skip silently
 * so one bad payload never Errors the turn.
 *
 * Transports differ only in payload shape (ImageBytes vs `image_url`
 * parts vs Ollama `images[]` vs LM Studio items) and in [isUsable]
 * (local: Base64-decodable; remote: non-blank, shaped per wire format).
 * Returns original-list indexes (over [messages] including the current
 * turn) so each provider can fuse filtering + carry in one indexed pass.
 */
internal object HistoryImageCarry {
    const val CARRY_MAX = 3

    fun selectKeptUrls(
        messages: List<ChatMessage>,
        max: Int = CARRY_MAX,
        isUsable: (String) -> Boolean = { it.isNotBlank() },
    ): Map<Int, List<String>> {
        val history = messages.dropLast(1)
        val carryIndexes = history
            .mapIndexedNotNull { index, msg ->
                if (msg.role == Role.USER && msg.imageUris.isNotEmpty()) index else null
            }
            .takeLast(max)
            .toSet()
        val keptByIndex = mutableMapOf<Int, List<String>>()
        val seenUrls = mutableSetOf<String>()
        for (index in carryIndexes.sortedDescending()) {
            val kept = history[index].imageUris.distinct()
                .filter { it.isNotBlank() && isUsable(it) && seenUrls.add(it) }
            if (kept.isNotEmpty()) keptByIndex[index] = kept
        }
        return keptByIndex
    }
}

/**
 * Quick-task (remote-image-carry): shape a data URL for Ollama's native
 * `images[]` (raw base64, no `data:…;base64,` prefix). Null when nothing
 * usable remains after the strip (blank payload) — the turn then keeps
 * its exact pre-carry text shape.
 */
internal fun ollamaRawImage(dataUrl: String): String? =
    dataUrl.substringAfter(",").trim().takeIf { it.isNotEmpty() }

/**
 * Quick-task (remote-image-carry): OpenAI-compat history mapping with the
 * shared K=3 carry. Reproduces each provider's pre-carry filter/sanitize
 * exactly ([includeSystem] selects the SYSTEM policy; USER text goes
 * through [sanitizeUser]); TOOL rows replay as plain text (Phase 49
 * DEL-01) and never carry. History USER turns selected by
 * [HistoryImageCarry.selectKeptUrls] ride as `image_url` content parts
 * ([OpenAiMessage.imageUrls]); every other row stays text-only and
 * byte-identical on the wire.
 */
internal fun mapOpenAiHistory(
    messages: List<ChatMessage>,
    includeSystem: Boolean,
    sanitizeUser: (String) -> String,
): List<OpenAiMessage> {
    val kept = HistoryImageCarry.selectKeptUrls(messages)
    return messages.mapIndexedNotNull { index, msg ->
        if ((!includeSystem && msg.role == Role.SYSTEM) || msg.content.isBlank()) {
            return@mapIndexedNotNull null
        }
        if (msg.role == Role.TOOL) {
            val (role, text) = msg.toProviderText()
            OpenAiMessage(role = role, content = text)
        } else {
            val content = if (msg.role == Role.USER) sanitizeUser(msg.content) else msg.content
            OpenAiMessage(
                role = msg.role.name.lowercase(),
                content = content,
                imageUrls = kept[index],
            )
        }
    }
}
