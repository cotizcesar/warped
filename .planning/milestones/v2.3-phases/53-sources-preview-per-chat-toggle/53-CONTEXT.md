# Phase 53: Sources Preview + Per-Chat Toggle - Context

**Gathered:** 2026-09-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Users can preview what each grounded source says and control web grounding per conversation. Tap a Fuentes item to open its extracted text in a bottom sheet without leaving chat (with "Abrir en navegador"); a numbered Fuentes list covers all N fetched sources; per-conversation tri-state override (on/off/inherit-global) plus a one-off "Sin web" composer escape; per-chat preference and persisted sources survive restarts via a single Room migration v14→v15 (override column + sources table). Builds on Phase 52's fan-out, fused blocks, and per-source ok/omitida snapshot. No inference changes, no provider changes.

</domain>

<decisions>
## Implementation Decisions

### Sources Preview Sheet
- Tap a Fuentes item opens preview — same `ModalBottomSheet` pattern as `ModelSelector` (`skipPartiallyExpanded`)
- Content: source number + URL + extracted page text (scrollable), no re-fetch — reads persisted rows created in this phase
- "Abrir en navegador" button fires `ACTION_VIEW` intent with the resolved post-redirect URL
- Dismiss via swipe/button only, no state change — preview never edits chat or sources

### Persisted Sources Store
- New `grounded_sources` table (id, message_id FK, index, resolved url, extracted_text, status ok/omitida) — part of the single v15 migration
- Rows scoped to the assistant message that used them; history reload re-renders Fuentes + preview from rows
- `ChatMessage.groundedSources` stays an ephemeral render list; repository hydrates from the table on load (extend `EntityMappers`, same drop-unknown pattern as siblings)
- Rows deleted with conversation cascade; no LRU/TTL in v2.3 (TUNE-02 trigger-gated to v2.4)

### Per-Chat Toggle
- Tri-state `conversations.web_override` NULL/0/1 (null = inherit global default-ON) — same v15 migration
- Placement: conversation header/menu toggle (on/off/inherit) + composer "Sin web" one-off chip that skips grounding for that send only
- Precedence at send time in `ChatViewModel` hook: one-off "Sin web" > per-chat override > global setting
- Spanish labels (Web: Sí/No/Heredar, Sin web); toggle change never refetches history, applies to next send

### Migration + Wiring
- One migration `MIGRATION_14_15`: `web_override` column + `grounded_sources` table together — single auto-tested migration, no multi-step
- Write timing: grounding hook persists source rows right after fetch, before inference — Phase 54 retry reuses the same rows
- Fuentes upgrade: items become clickable (tap → sheet), order == fetch block order; omitida rows shown struck/disabled without preview text
- Exit gates: v14→v15 migration test, restart-persistence test (toggle + sources survive), preview-open + browser-intent test

### the agent's Discretion
- Exact sheet layout (header rows, paddings) within existing 4/8/16dp scale and UI-SPEC contract
- DAO/query shapes for sources-by-message and override read/write — follow existing DAO conventions
- Menu vs header placement detail for the tri-state control — cheapest consistent with existing conversation surfaces

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ui/chat/components/MessageBubble.kt:202-214` — numbered Fuentes list (extends to clickable items opening the sheet)
- `ui/chat/components/ModelSelector.kt:55-57` — `ModalBottomSheet` + `rememberModalBottomSheetState(skipPartiallyExpanded)` pattern to copy
- `data/grounding/MultiUrlFetcher.kt` (Phase 52) — fan-out + per-source ok/omitida snapshot + resolved URLs; hook persists these rows
- `domain/model/ChatMessage.kt:17-21` — `groundedSources` ephemeral list + `modelOnlySourceCount` carrier (persist Phase 53 scope per 52-REVIEW IN-02)
- `data/local/db/AppDatabase.kt` — version 14; `Migrations.kt` holds MIGRATION_4_5..8_9 chain to extend
- `data/local/db/entity/MessageEntity.kt` / `ConversationEntity.kt` / `EntityMappers.kt` — entity + mapper extension conventions
- `ui/chat/ChatViewModel.kt` grounding hook — precedence evaluation point (Sin web > override > global)

### Established Patterns
- Room entities `{Name}Entity` + DAOs `{Name}Dao` + `{Source}.toDomain()` mappers; database version bump + `MIGRATION_X_Y` chain
- Pure-Kotlin grounding core stays JVM-testable; Android-touching code (intents, sheets) covered by UI-state tests
- Spanish user copy throughout grounding surfaces ("Leyendo…", "Fuentes", "… [truncado]")

### Integration Points
- `ChatViewModel.sendMessage()` — toggle precedence + row-persist timing (post-fetch, pre-inference)
- `MessageBubble` Fuentes list → new `SourcePreviewSheet` composable
- `ChatScreen` / conversation menu — tri-state control + composer "Sin web" chip
- Phase 54 consumes: persisted rows are the retry read-model (same rows, never rewritten history)

</code>

<specifics>
## Specific Ideas

- Bottom sheet shows extracted text already on device — never re-fetches on open (offline-safe preview)
- Omitida sources visible but struck/disabled — no silent drops, consistent with Phase 52 chip honesty
- One migration only — v15 carries both DDLs so a single auto-migration test gates the phase

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope. Global out-of-scope guardrails reaffirmed (no citation pills, no WorkManager retry — Phase 54 is message-scoped foreground, no provider interface changes).

</deferred>
