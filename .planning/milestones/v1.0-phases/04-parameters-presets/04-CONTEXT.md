# Phase 4: Parameters & Presets — Context

**Phase:** 4 — Parameters & Presets
**Status:** In Progress
**Created:** 2026-04-30

## Phase Boundary

From ROADMAP: "Expose all v1 generation parameters (temperature, top_p, top_k, repeat_penalty, max_tokens, context_size, seed, threads) with sensible defaults, and allow saving/loading named presets."

**Requirements:** PARM-01, PARM-02

**Success Criteria:**
1. User can adjust any generation parameter via sliders or number inputs and see changes reflected in next generation
2. User can save current parameter values as named preset (e.g., "Creative", "Precise") and load it from a list
3. Parameter changes and preset selections apply identically to both local and remote chat sessions

## Implementation Decisions

### Domain Model
- `Preset` data class mirrors `GenerationParameters` fields plus id, name, createdAt
- `Preset.toGenerationParameters()` bridges to existing `ChatRequest.parameters` field
- Sensible defaults match `GenerationParameters` defaults

### Persistence
- Room `PresetEntity` with `presets` table; stored as Long epoch millis for `created_at`
- `PresetDao` with `observeAll()` returning `Flow` for reactive UI updates
- `PresetRepository` interface + `PresetRepositoryImpl` following existing repository pattern

### UI
- `PresetsScreen` composable with slider controls for all parameters
- `PresetsViewModel` exposes `PresetsUiState` with current parameters + presets list
- Save dialog via `AlertDialog` with name input
- `onParametersChanged` callback propagates changes to `ChatViewModel`

### Integration
- `ChatViewModel` stores `GenerationParameters` in `ChatUiState` and uses them in `ChatRequest`
- `ChatRequest` already accepts `GenerationParameters` — just needs proper wiring
- `PresetsScreen` accessible via a "Presets" bottom tab with `Icons.Filled.Settings` icon

## Code Context

### Reused from Phase 1
- Room database infrastructure (AppDatabase, TypeConverters)
- Hilt DI modules (DatabaseModule, RepositoryModule)
- Navigation (NavGraph, Screen sealed class)
- ChatViewModel, ChatUiState

### Existing Dependencies
- `GenerationParameters` already exists at `domain/model/GenerationParameters.kt`
- `ChatRequest` already has `parameters: GenerationParameters` field
- `ChatViewModel.sendMessage()` creates `ChatRequest(messages = ...)` — needs parameters passed

### Files Created/Modified

| File | Action | Purpose |
|------|--------|---------|
| `domain/model/Preset.kt` | CREATE | Domain model for saved presets |
| `data/local/db/entity/PresetEntity.kt` | CREATE | Room entity for presets table |
| `data/local/db/entity/PresetMappers.kt` | CREATE | Domain ↔ Entity mappers |
| `data/local/db/dao/PresetDao.kt` | CREATE | Room DAO for preset queries |
| `domain/repository/PresetRepository.kt` | CREATE | Domain interface |
| `data/repository/PresetRepositoryImpl.kt` | CREATE | Room-backed implementation |
| `ui/presets/PresetsUiState.kt` | CREATE | UI state data class |
| `ui/presets/PresetsViewModel.kt` | CREATE | HiltViewModel for presets screen |
| `ui/presets/PresetsScreen.kt` | CREATE | Composable screen with sliders |
| `data/local/db/AppDatabase.kt` | EDIT | Add PresetEntity + presetDao() |
| `di/DatabaseModule.kt` | EDIT | Add providePresetDao() |
| `di/RepositoryModule.kt` | EDIT | Add bindPresetRepository() |
| `ui/navigation/Screen.kt` | EDIT | Add Presets tab route |
| `ui/navigation/NavGraph.kt` | EDIT | Add Presets composable + bottom tab |
| `ui/chat/ChatUiState.kt` | EDIT | Add generationParameters field |
| `ui/chat/ChatViewModel.kt` | EDIT | Add updateParameters(), wire to ChatRequest |

## Potential Pitfalls

1. **Thread count for local models** — `threads` parameter is only meaningful for local inference; remote providers ignore it. The UI exposes it universally for simplicity.
2. **Seed -1 semantics** — `-1` means random seed. Must be preserved as a valid value (slider range goes -1..32768, handled via IntInputField).
3. **Room migration** — Adding a new table (presets) requires a DB version bump from 2 to 3. `fallbackToDestructiveMigration()` is already set for development, so no migration code needed.
