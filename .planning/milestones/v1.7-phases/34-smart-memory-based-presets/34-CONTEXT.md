# Phase 34: Smart Memory-Based Presets — Context

**Gathered:** 2026-05-25
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver one dynamically calculated optimal preset based on available device RAM and selected model size, with manual override capability. When user adjusts any slider, the preset name changes to "Custom".

</domain>

<decisions>
## Implementation Decisions

### Memory Tiers (Auto-Decided)
- **Low** (<4 GB free): conservative params — context_size=2048, max_tokens=1024, threads=2
- **Mid** (4-8 GB free): balanced params — context_size=4096, max_tokens=2048, threads=4
- **High** (>8 GB free): generous params — context_size=8192, max_tokens=4096, threads=6
- Temperature/topP/topK stay at defaults (user preference, not memory-dependent)
- Thread count proportional to available cores up to 8

### Smart Preset Name (Auto-Decided)
- When sliders match smart preset: "Smart Preset (3.2 GB free)"
- When any slider is manually changed: label changes to "Custom"
- PresetsScreen top bar shows the name

### Integration Point
- PresetsViewModel observes ActiveModelSelection.localSelection → fetches model size from LocalModelRepository
- On model selection change → recalculate smart preset
- Memory info checked on screen open (real-time, not cached)
</decisions>

<code_context>
## Existing Code Insights
- `MemoryChecker.getMemoryInfo()` → MemoryInfo(availableBytes, totalBytes, usedPercent)
- `GenerationParameters` data class with temperature, topP, topK, repeatPenalty, maxTokens, contextSize, seed, threads
- `PresetsViewModel` manages parameter state and preset loading
- `PresetsScreen` renders sliders for all GenerationParameters fields
- `ActiveModelSelection.localSelection` gives connected model info
- `LocalModelRepository` gives modelSizeBytes
</code_context>

<specifics>
## Specific Ideas
Per ROADMAP: Smart preset on model selection, RAM tiers, manual override → "Custom", memory info displayed
</specifics>

<deferred>
## Deferred Ideas
None
</deferred>
