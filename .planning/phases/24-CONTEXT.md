# Phase 24: Search Simplification - Context

**Gathered:** 2026-05-09
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase — discuss skipped)

<domain>
## Phase Boundary

Remove all tabs from model search. Single search bar that queries `library=litert` across all Hugging Face. Delete Staff Picks code and related DTOs.

This is a pure infrastructure/cleanup phase. No new features, no user-facing behavior changes beyond simplification. The goal is removal of the tabbed search UI and dead Staff Picks code.
</domain>

<decisions>
## Implementation Decisions

### the agent's Discretion
All implementation choices are at the agent's discretion — pure infrastructure phase. The ROADMAP tasks and REQUIREMENTS.md constraints (SRCH-01 through SRCH-04) are the spec. Follow existing codebase patterns (4-space indent, K&R braces, trailing commas, MVVM pattern) when modifying files.

Phase 23 has been completed so GGUF references in HuggingFace* files should already be cleaned. This phase removes the remaining tab infrastructure and Staff Picks code.
</decisions>

<code_context>
## Existing Code Insights

### Files to Delete
- `HuggingFaceCollection` and `HuggingFaceCollectionItem` DTOs from `HuggingFaceDtos.kt`

### Files to Modify
- `app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt` — Remove `PrimaryTabRow`, `formats` list, simplify to single search bar; simplify `FormatBadge` to always LiteRT-LM
- `app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt` — Delete `loadStaffPicks()`, remove `activeFormat` logic, remove `setActiveFormat()`, simplify search to `library=litert`
- `app/src/main/java/com/warped/ui/huggingface/HuggingFaceUiState.kt` — Remove `activeFormat` field, remove `ggufFileDetails`/`GgufFileDetail` fields
- `app/src/main/java/com/warped/data/remote/api/HuggingFaceApi.kt` — Delete `getCollectionModels()`, simplify `searchModels()` (no library default, hardcode litert)
- `app/src/main/java/com/warped/domain/repository/HuggingFaceRepository.kt` — Delete `getCollectionModels()` from interface
- `app/src/main/java/com/warped/data/remote/repository/HuggingFaceRepositoryImpl.kt` — Delete `getCollectionModels()` implementation
- `app/src/main/java/com/warped/data/remote/dto/HuggingFaceDtos.kt` — Delete Staff Picks DTOs

### Established Patterns
- MVVM: ViewModels expose StateFlow<UiState> to Compose
- Hilt DI with @Singleton, @Provides in modules
- HuggingFaceScreen uses LazyColumn with OutlinedTextField for search
</code_context>

<specifics>
## Specific Ideas

No specific requirements — infrastructure phase. Refer to ROADMAP phase description and success criteria.
</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.
</deferred>
