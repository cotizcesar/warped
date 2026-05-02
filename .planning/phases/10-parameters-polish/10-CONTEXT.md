# Phase 10: Parameters & Polish - Context

**Gathered:** 2026-05-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Complete the parameter experience for LiteRT-LM with engine-aware UI controls, preset unification, and unsupported parameter handling. Extends ParametersScreen to grey out unsupported params for LiteRT-LM, adds modelFormat to presets, creates Room migration, and handles cross-format preset loading with warning.

**Depends on:** Phase 9 (UI tabs, model selector, EngineManager integration)
**Requirements:** PARM-03, PARM-04, PARM-05
</domain>

<decisions>
## Implementation Decisions

### Parameter Display & Grey-out
- Detect active format via `EngineManager.getActiveEngine()?.type` or model's `modelFormat` field
- Grey out `repeatPenalty`, `contextSize`, `threads` for LiteRT-LM — disabled sliders with 0.38 alpha + "Unsupported for LiteRT-LM" label
- Temperature, topP, topK, seed remain active for both formats
- Update parameter visibility on model selection (provider type change), not engine load
- Use Material3 `enabled = false` + `alpha` modifier for grey-out effect

### Preset Unification
- Add `modelFormat` column to `presets` table via Room migration (DB v8→v9)
- `modelFormat TEXT NOT NULL DEFAULT 'GGUF'` — matches local_models pattern
- New `PresetEntity.modelFormat` → `Preset.modelFormat` → visible in UI
- Cross-format preset loading shows warning dialog: "This preset was created for [format]. Only compatible parameters will be applied."
- Presets list shows format badge (reuse existing FormatBadge composeable from Phase 9)

### the agent's Discretion
- Exact slider disabled styling (colors, spacing)
- Warning dialog wording and button labels
- Migration number (v8→v9 or next available)
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ParametersScreen` / `PresetsScreen` — Phase 4 composables to extend
- `PresetEntity` (`data/local/db/entity/PresetEntity.kt`) — add modelFormat field
- `Preset` domain model — add modelFormat field
- `PresetMappers` — update entity ↔ domain mapping
- `Migrations.kt` — add MIGRATION_8_9 for presets.model_format
- `FormatBadge` composable from Phase 9 — reuse on presets list
- `EngineManager` — provides active engine type for format detection

### Integration Points
- PresetsScreen: add format badge to preset cards, filter by format
- PresetsViewModel: handle cross-format loading with warning
- ParametersScreen: disable sliders based on active format
- ChatViewModel: pass active format to parameters
- Room migration chain: add MIGRATION_8_9
</code_context>

<specifics>
No specific requirements beyond ROADMAP success criteria.
</specifics>

<deferred>
None.
</deferred>
