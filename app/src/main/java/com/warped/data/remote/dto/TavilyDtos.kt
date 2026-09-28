package com.warped.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Phase 55 (TAV-02): Tavily Search API request/response DTOs.
 *
 * Shape confirmed from the official API reference + Python SDK source
 * (see 55-RESEARCH.md): auth is `Authorization: Bearer <key>` header
 * (never `api_key` in body), `query` is the only required body field,
 * `search_depth` defaults to `basic` (1 credit), `max_results` 0-20
 * default 5. `include_answer` stays false — we fuse raw `results[]`
 * snippets, not the LLM answer. Follows the plain `@Serializable` style
 * of the other `data/remote/dto` files (`ignoreUnknownKeys` comes from
 * the shared `Json` instance).
 */
@Serializable
data class TavilySearchRequest(
    val query: String,
    val search_depth: String = "basic",
    val max_results: Int = 5,
    val include_answer: Boolean = false,
    val chunks_per_source: Int = 3,
)

@Serializable
data class TavilySearchResult(
    val title: String = "",
    val url: String = "",
    val content: String = "",
    val score: Double = 0.0,
)

@Serializable
data class TavilySearchResponse(
    val query: String = "",
    val results: List<TavilySearchResult> = emptyList(),
    val answer: String? = null,
    val response_time: Float? = null,
)
