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
    /**
     * Quick-task (image-grid): intent-gated only — the VM sets true when
     * [com.warped.data.grounding.ImageIntent] fires. Adds a top-level
     * `images[]` array to the response; default false keeps non-image
     * turns byte-identical to today (no extra payload).
     */
    val include_images: Boolean = false,
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
    /**
     * Quick-task (image-grid): present only when the request set
     * `include_images=true`. URLs are http(s)-gated at fuse time
     * (same precedent as OG image gating) — never fetched here.
     */
    val images: List<TavilyImageResult> = emptyList(),
)

/**
 * Quick-task (image-grid): one entry of the Tavily `images[]` array.
 * Shape per the Tavily Search API reference (`url` + optional
 * `description`); unknown keys ignored by the shared Json instance.
 */
@Serializable
data class TavilyImageResult(
    val url: String = "",
    val description: String? = null,
)
