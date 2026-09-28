# Phase 54: Offline Retry - Context

**Gathered:** 2026-09-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Users offline at send time can retry grounding when back online without resending. OFFLINE-grounded turns show a queued state with a "Reintentar" affordance on reconnect; retry fetches the URLs again through the same grounding entry point and lands results in the same persisted source rows the preview sheet reads. History is never rewritten and inference never re-runs silently. Message-scoped foreground retry only — no WorkManager, no auto-retry, OFFLINE-only scope. Builds on Phase 52 fan-out + Phase 53 persisted rows + preview sheet. Single requirement (RETRY-01).

</domain>

<decisions>
## Implementation Decisions

### Queued State + Affordance
- OFFLINE-grounded turns show a queued marker on the existing model-only banner ("Sin conexión" + "En espera") — no new screen
- "Reintentar" button attached to the banner, enabled only when connectivity is validated (same `NET_CAPABILITY_VALIDATED` gate as the fetcher)
- Button hidden while offline, appears on reconnect — no polling UI; connectivity observed via existing check on resume/send
- "Reintentar" (verb only); success replaces the banner with Fuentes, failure keeps the banner with retry still available

### Retry Execution
- Retry calls the same `MultiUrlFetcher.fetchAll` grounding entry point with the turn's original URLs — no parallel fetch path
- Retry NEVER re-runs the model silently — refetch is user-initiated (explicit tap = visible by construction); user sees the "Leyendo" chip again
- Original user + assistant rows never rewritten; retry updates the assistant message's grounding attachment (sources persisted, notice cleared) in place
- Scope is OFFLINE-only (`ModelOnlyNotice.OFFLINE` turns); FETCH_FAILED turns keep their banner with no Reintentar — no scope creep

### Retry Scope
- Foreground only: retry runs in chat scope (ViewModel coroutine), cancellable via Stop like the initial fetch — no WorkManager (15-min granularity wrong per out-of-scope)
- No auto-retry: reconnect never triggers a fetch by itself — the user taps Reintentar explicitly
- Message-scoped: each OFFLINE turn carries its own retry; no queue screen, no cross-message batching
- Stop during retry returns to the queued state, banner + Reintentar intact

### Row Reuse + Gates
- Retry results land in the same `grounded_sources` rows keyed by the assistant message (delete-then-insert per Phase 53 WR-03 fix) — the exact rows the preview sheet reads
- Preview sheet opens on retried sources with zero changes — it already reads hydrated rows
- Answer semantics locked by planner within the guardrails: retry refetches page text; never-rewrite-history + never-silent-inference are the fences
- Exit gates: offline→reconnect retry test (fake offline then online), history-untouched assertion (user text + timestamps intact), row-reuse assertion (same `message_id` rows), no-inference-on-retry unit proof

### the agent's Discretion
- Exact answer-refresh semantics on successful retry (sources-only attach vs visible grounded refresh) within the locked guardrails — research the cleanest option against RETRY-01 "retry fetches"
- Connectivity observation mechanism (callback vs check-on-resume) — cheapest consistent with existing `hasValidatedInternet` gate
- Queued-marker visual detail within the existing banner row and UI-SPEC contract

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `data/grounding/WebPageFetcher.kt:hasValidatedInternet()` — connectivity gate (INTERNET + VALIDATED); reuse for Reintentar enablement
- `data/grounding/MultiUrlFetcher.kt:fetchAll` — the single grounding entry point retry reuses with original URLs
- `data/grounding/GroundingResult.kt` — `AllFailed(OFFLINE)` is the retry-eligible marker; `Fused` is the success shape
- `ui/chat/ChatViewModel.kt:401-440` — AllFailed→banner routing + `modelOnlySourceCount`; retry hook lives adjacent
- `ui/chat/components/MessageBubble.kt:ModelOnlyBanner` — OFFLINE copy + pluralized failure copy; Reintentar attaches here
- `data/local/db/dao/GroundedSourceDao.kt:deleteByMessage()` (Phase 53 WR-03) — row-reuse primitive for retry writes
- `data/repository/ChatRepositoryImpl.kt:107-123` — delete-then-insert save path retry reuses
- `ui/chat/ChatUiState.kt` — `isFetchingWeb` + `webFetchProgress` chip states retry reuses for visible progress

### Established Patterns
- Grounding is prompt-prefix augmentation on the current turn; persistence via `grounded_sources` rows hydrated on load
- Spanish user copy throughout grounding surfaces
- OFFLINE collapses as worst-case reason; FETCH_FAILED turns are out of retry scope

### Integration Points
- `ChatViewModel` — retry entry (same hook position as initial fetch), Stop-cancel path shared
- `MessageBubble` banner — queued marker + Reintentar button host
- `grounded_sources` rows — retry write-model AND preview read-model (same rows)
- No provider, inference, Room schema, or navigation changes in this phase

</code>

<specifics>
## Specific Ideas

- Tapping Reintentar is explicit user consent — that is what makes the retry "visible, never silent" by construction
- Retry is the last writer to the same rows — preview sheet needs zero changes
- Single-requirement phase (RETRY-01 only) — keep the plan count minimal (1–2 plans)

</specifics>

<deferred>
## Deferred Ideas

- Phase 53 UI-review polish (override-state indicator, Fuente-heading redundancy, all-omitida Fuentes visibility) — recorded for milestone audit; touch the same surfaces but out of RETRY-01 scope
- Periodic WorkManager retry — globally out of scope (wrong granularity)

</deferred>
