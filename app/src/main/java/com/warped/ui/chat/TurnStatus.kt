package com.warped.ui.chat

/**
 * Quick-task (unified-turn-status): pure resolver for the single turn-status
 * row above the input bar. No Compose imports — truth-table tested in
 * [TurnStatusTest].
 *
 * Priority: model loading > toolCallActive > isFetchingWeb > streaming
 * gap. Returns null when nothing is active (null renders nothing).
 *
 * Fetch branch mirrors today's slot logic:
 * - `perSource` non-empty → real fan-out progress ([FetchFanout] when
 *   `total > 1`, else [FetchSingle] — same `isMulti` check the old chip used).
 * - `perSource` EMPTY → [Searching]: the DDG single-fuse search path
 *   emits `WebFetchProgress(done = 0, total = searchCount, perSource = [])`,
 *   which is not a 0-of-N fetch — the indeterminate "Searching…" row covers
 *   it and the done/total values never surface.
 * - null progress + `isFetchingWeb` → [FetchSingle] (legacy fallback).
 *
 * `isStreamingGap` is the old thinking-row condition verbatim,
 * precomputed by the caller so the gap math stays in one place.
 */
sealed interface TurnStatus {
    data class Tool(val text: String) : TurnStatus
    data class FetchFanout(val done: Int, val total: Int) : TurnStatus
    data object FetchSingle : TurnStatus
    data object Searching : TurnStatus
    data object ThinkingGap : TurnStatus
    data class LoadingModel(val modelName: String, val firstTime: Boolean) : TurnStatus
    /**
     * 2026-10-04 Caso 3: remote endpoint probe in flight (ping before
     * the turn). Non-blocking like every other row — the input stays
     * usable while the server is verified.
     */
    data class Connecting(val modelName: String) : TurnStatus
}

fun resolveTurnStatus(
    toolCallActive: String?,
    isFetchingWeb: Boolean,
    progress: WebFetchProgress?,
    isStreamingGap: Boolean,
    isLoadingModel: Boolean = false,
    loadingModelName: String = "",
    loadingFirstTime: Boolean = false,
    isProbingRemote: Boolean = false,
    probingModelName: String = "",
): TurnStatus? {
    if (isLoadingModel) return TurnStatus.LoadingModel(loadingModelName, loadingFirstTime)
    if (isProbingRemote) return TurnStatus.Connecting(probingModelName)
    if (toolCallActive != null) return TurnStatus.Tool(toolCallActive)
    if (isFetchingWeb) {
        if (progress == null) return TurnStatus.FetchSingle
        if (progress.perSource.isEmpty()) return TurnStatus.Searching
        return if (progress.total > 1) {
            TurnStatus.FetchFanout(progress.done, progress.total)
        } else {
            TurnStatus.FetchSingle
        }
    }
    if (isStreamingGap) return TurnStatus.ThinkingGap
    return null
}
