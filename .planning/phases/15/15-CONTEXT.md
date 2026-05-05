# Phase 15: Cross-Engine UX Parity - Context

**Gathered:** 2026-05-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Ensure the GGUF experience is indistinguishable from LiteRT-LM and remote providers. All 5 UX facets — metadata display, TPS visualization, stop button, RAM recommendations, and model file management — work uniformly across engines.

After Phases 11-14, most parity already exists. This phase verifies and fills remaining gaps.

In-scope: UXMT-01 (unified metadata), UXMT-02 (unified TPS), UXMT-03 (consistent stop), UXMT-04 (quantization-aware RAM badges), UXMT-05 (uniform model management).
</domain>

<decisions>
## Implementation Decisions

- **Metadata parity**: GGUF metadata already parses via `GgufMetadataParser` (Phase 11) and native `getModelInfo()` (Phase 12). LiteRT-LM metadata is generated at import. Ensure both use same UI card layout.
- **TPS parity**: Already works — `LocalLlmProvider.chat()` tracks TPS (Phase 13), LiteRT-LM uses `Conversation.sendMessageAsync(Flow)`. Same UI widget position.
- **Stop button**: Already consistent — `LlamaEngine.stop()` for GGUF, `liteRTLmEngine.close()` for LiteRT-LM, OkHttp cancel for remote.
- **RAM badges**: Update quantization badge colors to match RAM severity (red=too big, amber=tight, green=fits).
- **Model management**: Already unified via `ModelsScreen` — same list, same delete flow.
</decisions>

<code_context>
## Existing Code Insights
- **`ModelsScreen.kt`** — already shows GGUF and LiteRT-LM models in same list with format badges
- **`QuantizationBadge`** (Phase 11) — color-coded quantization labels in HuggingFace browser
- **`HuggingFaceScreen.kt`** — model detail + download progress
- **Chat screen** — same stop button for all engines; TPS shown in chat header
- **`ModelsViewModel`** — unified model management (delete, select, RAM check)
</code_context>

<specifics>
## Specific Ideas

- Add device-aware RAM badge: compare model size to device RAM, color-code as red (tight, >80%), amber (moderate, >50%), green (fits comfortably)
- Ensure metadata card appears identically for GGUF and LiteRT-LM models in the Models screen detail view
- Add a format-switch animation: smooth transition when user switches between GGUF and LiteRT-LM engines
</specifics>
</deferred>
</deferred>
