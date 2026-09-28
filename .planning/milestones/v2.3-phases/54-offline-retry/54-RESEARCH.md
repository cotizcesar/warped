# Phase 54: Offline Retry - Research

**Researched:** 2026-09-28
**Domain:** Android foreground retry orchestration (ViewModel + Room + OkHttp grounding pipeline)
**Confidence:** HIGH

## Summary

Phase 54 adds a message-scoped foreground retry for OFFLINE-grounded turns. All primitives already exist in the codebase: `MultiUrlFetcher.fetchAll` (single grounding entry point), `WebPageFetcher.hasValidatedInternet` (connectivity gate, currently `private`), `GroundedSourceDao.deleteByMessage` (row-reuse primitive), `ChatRepositoryImpl.saveMessageWithSources` (delete-then-insert), and the `isFetchingWeb`/`webFetchProgress` chip states. The phase is glue code: a `retryGrounding` ViewModel function, a `Reintentar` button on the existing `ModelOnlyBanner` OFFLINE row, a validated-connectivity flag refreshed on resume/send, and one new repository method to write retried rows without re-saving the message.

Two non-obvious findings drive the plan. First, OFFLINE turns persist **zero** source rows (the `AllFailed` branch calls plain `saveMessage`), so the "original URLs" must be re-derived at retry time via `UrlDetector.allUrls()` on the turn's preceding user message — which works across restarts because user content is persisted. Second, the ViewModel discards the assistant row id returned by `saveMessageWithSources`, and transcript `ChatMessage.id` is a UUID unrelated to the DB row id (`toEntity` drops it) — so retry needs a repository-side row lookup by `(conversationId, createdAt)` (millis round-trip exactly; a covering index already exists) rather than any re-save. A new `MessageDao.insert` call on retry would CASCADE-wipe source rows per the in-code warning at `ChatRepositoryImpl.kt:92-94` — the plan must forbid it.

**Primary recommendation:** Sources-only attach on successful retry (refetch → persist rows → clear notice → render Fuentes; assistant answer text byte-identical). Never re-run inference. This is the only reading of RETRY-01 consistent with never-rewrite-history and the Phase 49 single-assistant-message-per-turn invariant.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- OFFLINE-grounded turns show a queued marker on the existing model-only banner ("Sin conexión" + "En espera") — no new screen
- "Reintentar" button attached to the banner, enabled only when connectivity is validated (same `NET_CAPABILITY_VALIDATED` gate as the fetcher)
- Button hidden while offline, appears on reconnect — no polling UI; connectivity observed via existing check on resume/send
- "Reintentar" (verb only); success replaces the banner with Fuentes, failure keeps the banner with retry still available
- Retry calls the same `MultiUrlFetcher.fetchAll` grounding entry point with the turn's original URLs — no parallel fetch path
- Retry NEVER re-runs the model silently — refetch is user-initiated (explicit tap = visible by construction); user sees the "Leyendo" chip again
- Original user + assistant rows never rewritten; retry updates the assistant message's grounding attachment (sources persisted, notice cleared) in place
- Scope is OFFLINE-only (`ModelOnlyNotice.OFFLINE` turns); FETCH_FAILED turns keep their banner with no Reintentar — no scope creep
- Foreground only: retry runs in chat scope (ViewModel coroutine), cancellable via Stop like the initial fetch — no WorkManager (15-min granularity wrong per out-of-scope)
- No auto-retry: reconnect never triggers a fetch by itself — the user taps Reintentar explicitly
- Message-scoped: each OFFLINE turn carries its own retry; no queue screen, no cross-message batching
- Stop during retry returns to the queued state, banner + Reintentar intact
- Retry results land in the same `grounded_sources` rows keyed by the assistant message (delete-then-insert per Phase 53 WR-03 fix) — the exact rows the preview sheet reads
- Preview sheet opens on retried sources with zero changes — it already reads hydrated rows
- Answer semantics locked by planner within the guardrails: retry refetches page text; never-rewrite-history + never-silent-inference are the fences
- Exit gates: offline→reconnect retry test (fake offline then online), history-untouched assertion (user text + timestamps intact), row-reuse assertion (same `message_id` rows), no-inference-on-retry unit proof

### the agent's Discretion
- Exact answer-refresh semantics on successful retry (sources-only attach vs visible grounded refresh) within the locked guardrails — research the cleanest option against RETRY-01 "retry fetches"
- Connectivity observation mechanism (callback vs check-on-resume) — cheapest consistent with existing `hasValidatedInternet` gate
- Queued-marker visual detail within the existing banner row and UI-SPEC contract

### Deferred Ideas (OUT OF SCOPE)
- Phase 53 UI-review polish (override-state indicator, Fuente-heading redundancy, all-omitida Fuentes visibility) — recorded for milestone audit; touch the same surfaces but out of RETRY-01 scope
- Periodic WorkManager retry — globally out of scope (wrong granularity)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| RETRY-01 | User offline at send time gets a queued state with "Reintentar" on reconnect (message-scoped, OFFLINE-only; retry fetches, never rewrites history or re-runs inference silently) | Sources-only attach recommendation (§Architecture Patterns); `fetchAll` + `deleteByMessage` reuse (§Standard Stack); row-id lookup without re-save (§Common Pitfalls #1); exit-gate test patterns (§Code Examples) |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Retry orchestration (tap → fetch → persist → UI update) | Browser / Client (ViewModel) | — | Foreground-only by lock; lives in `ChatViewModel` chat scope next to the send-time hook |
| Connectivity observation (validated-online flag) | Browser / Client (ViewModel + Compose) | — | Synchronous `ConnectivityManager` check on resume/send; no background observer |
| Page refetch | Browser / Client (OkHttp via `WebPageFetcher`) | — | Same fetcher instance, same stripped-client policy; no new network surface |
| Source-row persistence | Database / Storage (Room) | — | Delete-then-insert on existing `grounded_sources` rows; no schema change |
| Preview of retried sources | Browser / Client (Compose) | — | Zero changes; sheet already reads hydrated rows |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `MultiUrlFetcher.fetchAll` (existing, `data/grounding/`) | Phase 52 as-built | Single grounding entry point retry reuses | Locked: no parallel fetch path; fan-out, dedupe, cap-5, OFFLINE-wins collapse all reused verbatim [VERIFIED: codebase `MultiUrlFetcher.kt:72-140`] |
| `WebPageFetcher.hasValidatedInternet` (existing, `private` → raise to `internal`) | Phase 50 as-built | `NET_CAPABILITY_INTERNET + NET_CAPABILITY_VALIDATED` gate for Reintentar visibility | Locked: same gate as the fetcher; one-word visibility change, no new permission (`ACCESS_NETWORK_STATE` already declared [VERIFIED: `AndroidManifest.xml:5`, `WebPageFetcher.kt:175-186`] |
| `GroundedSourceDao.deleteByMessage` + `insertAll` (existing) | Phase 53 WR-03 as-built | Row-reuse write primitive | Locked: retry is the predicted second writer; delete-then-insert keeps `ORDER BY source_index` invariant with no v16 migration [VERIFIED: `GroundedSourceDao.kt:15-25`, `ChatRepositoryImpl.kt:107-123`] |
| `UrlDetector.allUrls` (existing) | Phase 52 as-built | Re-derive the turn's original URLs from the persisted user message at retry time | OFFLINE turns persist zero source rows, so URLs must come from user content; dedupe + cap-5 behavior identical to send time [VERIFIED: `ChatViewModel.kt:361`, `MultiUrlFetcher.MAX_URLS`] |
| `isFetchingWeb` + `webFetchProgress` chip (existing) | Phase 52 as-built | Visible retry progress (`Leyendo N de M…`) + Stop path | Locked: retry is visible by construction; same states, no new UI component [VERIFIED: `ChatViewModel.kt:363-374,440-441`, `ChatUiState.kt:179-180`] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `LifecycleEventObserver` / `ON_RESUME` in `ChatScreen.kt` | androidx.lifecycle (already used: `collectAsStateWithLifecycle`) | Re-check validated connectivity on resume | Connectivity observation mechanism (agent's discretion) — see recommendation below |
| `MessageDao` new `@Query` (no schema change) | Room 2.7.x | Resolve assistant row id by `(conversation_id, created_at)` | Row-id lookup for retry writes; `messages` already has an index on `(conversation_id, created_at)` [VERIFIED: `MessageEntity.kt:19`] |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Check-on-resume/send connectivity flag | `ConnectivityManager.registerNetworkCallback` + `callbackFlow` | Callback gives live updates but adds registration/lifecycle management and a new flow for a flag that only gates one button; check-on-resume matches the locked "no polling UI" constraint and reuses the exact fetcher gate. Recommend check-on-resume. |
| Repository row lookup by `(conversationId, createdAt)` | Track `rowId` in a ViewModel `Map<messageUuid, rowId>` | In-memory map is simpler but lost on process death — retry after restart (a core RETRY-01 scenario: "back online" may follow app kill) would break. `createdAt` millis round-trips exactly (`Instant.ofEpochMilli`/`toEpochMilli` [VERIFIED: `EntityMappers.kt:29-42`]) and the covering index exists. Recommend repository lookup. |
| Sources-only attach on success | Visible re-inference (regenerate answer with fetched context) | Re-inference either rewrites the assistant row (violates never-rewrite-history) or appends a second assistant message (violates the Phase 49 single-assistant-message-per-turn invariant [VERIFIED: `ChatViewModel.kt:589-600` comment]). RETRY-01's verb is "fetches", not "regenerates". Recommend sources-only attach — see §Architecture Patterns. |

**Installation:**
```bash
# Zero new dependencies. No install step.
```

**Version verification:** N/A — no external packages. All reused code verified in-tree by grep/read this session.

## Package Legitimacy Audit

No external packages installed in this phase — reuse-only (in-tree `MultiUrlFetcher`, `WebPageFetcher`, `UrlDetector`, Room DAOs). Audit table omitted; nothing to slopcheck. [VERIFIED: codebase — no new imports required beyond existing modules]

## Architecture Patterns

### System Architecture Diagram

```
User taps Reintentar (OFFLINE banner, validated-online only)
        │
        ▼
ChatViewModel.retryGrounding(assistantMsgId) ── foreground coroutine (own retryJob)
        │  1. Locate preceding USER message in transcript → UrlDetector.allUrls()
        │  2. isFetchingWeb=true + webFetchProgress chip ("Leyendo N de M…")
        ▼
MultiUrlFetcher.fetchAll(urls, contextSize, onProgress)   [same entry point]
        │  fan-out via coroutineScope+async (Stop-cancellable)
        ▼
┌─ Fused (≥1 ok) ──► Repository.replaceSources(convId, createdAt, details)
│                     │  resolve rowId → deleteByMessage → insertAll → timestamp
│                     ▼
│                    Transcript: assistant copy(modelOnlyNotice=null,
│                      groundedSources=okUrls, groundedSourceDetails=details)
│                     Banner replaced by Fuentes; preview sheet reads same rows
│
└─ AllFailed ──────► Transcript unchanged (banner + queued marker + Reintentar)
                      banner stays; OFFLINE-wins collapse reused verbatim
```

File-to-implementation mapping belongs in the Component Responsibilities table, not in the diagram.

### Recommended Project Structure

No new files required except tests. Touch list (all existing files):

```
app/src/main/java/com/warped/
├── ui/chat/
│   ├── ChatViewModel.kt         # retryGrounding() + retryJob + validated-online flag + Stop wiring
│   ├── ChatUiState.kt           # one Boolean (e.g. isValidatedOnline) on input state + combineSnapshot
│   ├── ChatScreen.kt            # ON_RESUME refresh call; pass onRetry into message list
│   └── components/MessageBubble.kt  # ModelOnlyBanner: queued suffix + Reintentar TextButton (OFFLINE only)
├── data/grounding/
│   └── WebPageFetcher.kt        # hasValidatedInternet: private → internal (one word)
├── data/local/db/dao/
│   └── MessageDao.kt            # one new @Query: row id by (conversation_id, created_at, role)
├── domain/repository/
│   └── ChatRepository.kt        # one new method: replaceSources(...) (no re-save)
└── data/repository/
    └── ChatRepositoryImpl.kt    # implement replaceSources via deleteByMessage+insertAll
```

### Pattern 1: Sources-only attach (answer-refresh semantics — RECOMMENDED)

**What:** On successful retry (`Fused`), persist the source rows, clear the notice, and render Fuentes. The assistant answer text is byte-identical before and after. Inference never runs.
**When to use:** Always in this phase — it is the only option satisfying all three guardrails simultaneously.
**Why it wins (against RETRY-01 wording + guardrails):**
1. RETRY-01 says "retry **fetches**" — the fetch is the retry's work product; no regenerate verb appears anywhere in the requirement.
2. never-rewrite-history forbids mutating the assistant row's content; re-inference output must go somewhere, and both destinations are illegal (rewrite the row, or append a second assistant message breaking the Phase 49 single-turn invariant).
3. never-silent-inference is satisfied trivially (zero inference), and on-device re-inference would cost battery/latency for an answer the user never asked to regenerate.
4. The standing model-only answer plus now-previewable Fuentes matches the existing banner copy's promise ("Respuesta solo del modelo, sin contenido de la página" → sources now attached for verification).
5. Phase 52 partial rules apply unchanged: ok pages ground the attachment (`Fuentes` + `omitida` marks), all-refetch-fail keeps the banner.

```kotlin
// Source: established Phase 52/53 contracts in ChatViewModel.kt:393-438 + ChatRepositoryImpl.kt:102-124
// Retry success = Fused branch WITHOUT the GroundingPrompt.augment step:
groundedSourceDetails = result.details          // persist via replaceSources()
groundedSources = result.okUrls                 // ephemeral render list
modelOnlyNotice = null                          // banner clears → Fuentes renders
// requestUserText / inference: NOT TOUCHED — no augment, no helper call
```

**Known caveat (document, do not solve):** the standing answer was generated without page content and may not reflect it; Fuentes show what was fetched for user verification. The locked UI-SPEC adds no disclaimer copy — flag as a milestone-audit note, not a scope expansion. [ASSUMED: user impact of the caveat is editorial judgment, not verified]

### Pattern 2: Check-on-resume connectivity flag (agent's discretion — RECOMMENDED)

**What:** A `isValidatedOnline: Boolean` (naming at planner's choice) on input state, refreshed by calling the newly-`internal` `hasValidatedInternet()` at three points: `ON_RESUME` (ChatScreen lifecycle observer), after each send completes, and after each retry completes/fails. `Reintentar` renders only when the flag is true.
**When to use:** This phase, per the locked "no polling UI" constraint.
**Why not `NetworkCallback`:** a registered callback needs lifecycle-scoped registration/unregistration, a new `StateFlow`, and handles transitions the UI never acts on (reconnect alone must NOT fetch — locked). The flag is read at exactly the moments the button's visibility can matter. Cheapest consistent option.

### Pattern 3: Retry-scoped job sharing the Stop path

**What:** `private var retryJob: Job?` alongside `generationJob`; `stopGeneration()` cancels both and clears `isFetchingWeb`/`webFetchProgress`. Transcript untouched on Stop-during-retry (banner + Reintentar intact per lock).
**When to use:** Required — retry must not run un-cancellable, and must not outlive a new turn.
**Guards:** (a) ignore `retryGrounding` taps while `isFetchingWeb` is true (prevents overlapping `fetchAll` calls sharing the single `WebPageFetcher` cancel handle — see Pitfall #3); (b) cancel `retryJob` when a new send starts (same pre-cancel position as `generationJob` at `ChatViewModel.kt:320-324`); (c) OFFLINE-only gate inside `retryGrounding` (re-check `modelOnlyNotice == OFFLINE`, not just UI visibility) so a stale tap can't retry a FETCH_FAILED turn.

### Anti-Patterns to Avoid
- **Re-saving the assistant message on retry:** `MessageDao.insert` uses `REPLACE` and CASCADE-wipes source rows (in-code warning `ChatRepositoryImpl.kt:92-94`). Retry must use the new `replaceSources` path — never `saveMessage`/`saveMessageWithSources`. [VERIFIED: codebase comment]
- **Re-running inference "visibly":** violates never-rewrite-history under either destination; there is no regenerate feature to piggyback on.
- **Auto-fetch on reconnect:** explicitly locked out; reconnect only flips button visibility.
- **Retrying FETCH_FAILED turns:** locked out; the eligibility check must be on the data (`ModelOnlyNotice.OFFLINE`), not on banner visibility.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Connectivity validation | Custom ping / socket probe | `hasValidatedInternet()` (`NET_CAPABILITY_VALIDATED`) | Captive-portal correctness, zero battery cost, already the fetcher's gate |
| Parallel fetch orchestration | New coroutine fan-out | `MultiUrlFetcher.fetchAll` | Dedupe, cap-5, index-zipped order, OFFLINE-wins collapse, cooperative cancel already handled |
| Source-row dedupe | Client-side diff of rows | `deleteByMessage` + `insertAll` | Phase 53 WR-03 fix; no unique index exists so REPLACE is a no-op on autoGenerate ids |
| URL re-extraction | Persisting URL lists on OFFLINE turns | `UrlDetector.allUrls(userContent)` at retry time | OFFLINE turns persist no rows by design; user content is the durable URL record and survives restarts |
| Background scheduling | WorkManager / AlarmManager retry | Foreground `viewModelScope` coroutine | 15-min WorkManager granularity is wrong for chat; explicitly out of scope (REQUIREMENTS.md Out of Scope) |

**Key insight:** Every hard sub-problem (fan-out, collapse, row identity, progress, cancel) was already solved in Phases 50–53. Phase 54 is orchestration glue; any new mechanism is scope creep by definition.

## Common Pitfalls

### Pitfall 1: Re-save wipes the rows retry just wrote
**What goes wrong:** Calling `saveMessageWithSources` (or `saveMessage`) for the retried turn REPLACE-inserts the message row, CASCADE-deleting the `grounded_sources` rows — Fuentes renders empty and preview degrades post-restart.
**Why it happens:** `ChatMessage.id` (UUID) is unrelated to the DB row id; the natural-looking "save the updated message" path is a delete in disguise.
**How to avoid:** New `replaceSources(conversationId, assistantCreatedAt, details)` repository method: resolve row id → `deleteByMessage` → `insertAll` → `updateTimestamp`. No `MessageDao.insert` anywhere on the retry path. [VERIFIED: `ChatRepositoryImpl.kt:92-101` warning]
**Warning signs:** Fuentes empty after retry success; duplicate `(message_id, source_index)` rows in tests.

### Pitfall 2: No URLs to retry after restart
**What goes wrong:** Retry finds the OFFLINE assistant message but has no URL list (ephemeral `groundedUrls` long gone, zero rows persisted).
**Why it happens:** Assuming the URL list must be persisted at send time.
**How to avoid:** Re-derive via `UrlDetector.allUrls()` on the preceding user message's content (guaranteed non-empty: OFFLINE notice is only set when `groundedUrls.isNotEmpty()` [VERIFIED: `ChatViewModel.kt:361,432-438`]). Locate the user message as the nearest preceding `Role.USER` message in transcript.
**Warning signs:** Retry button present but fetch targets empty → immediate `AllFailed(FETCH_FAILED)` on a previously-OFFLINE turn.

### Pitfall 3: Overlapping fetches share one cancel handle
**What goes wrong:** A retry tapped while a send-time fetch is in flight (or two rapid retry taps) leaves orphan OkHttp calls running after Stop clears the chip — the 52-UI-REVIEW fan-out cancel warning.
**Why it happens:** `stopGeneration` calls single-handle `fetcher.cancel()`; structured-concurrency cancel is the real backstop only if overlapping scopes don't interleave.
**How to avoid:** Gate `retryGrounding` on `!isFetchingWeb`; single `retryJob` reference (second tap while job active is a no-op); cancel `retryJob` on new-send pre-cancel and in `stopGeneration`.
**Warning signs:** Chip cleared but log shows continued fetch callbacks; progress callbacks writing to a cleared `webFetchProgress`.

### Pitfall 4: Stale connectivity flag shows Reintentar while offline
**What goes wrong:** User taps Reintentar with no validated connection → instant `AllFailed(OFFLINE)` churn, chip flash, confusing no-op.
**Why it happens:** Flag refreshed only on resume; user toggles airplane mode while chat is foreground.
**How to avoid:** Re-check `hasValidatedInternet()` synchronously inside `retryGrounding` before fetching (cheap, synchronous call); on false, refresh the flag (button hides) and return without fetching. Belt-and-suspenders with the visibility gate.
**Warning signs:** Retry instantly fails OFFLINE in manual testing with airplane mode toggled in-foreground.

### Pitfall 5: Touching Phase 53 polish surfaces
**What goes wrong:** Banner-row edits accidentally "fix" the deferred UI-review items (override indicator, Fuente-heading redundancy, all-omitida visibility) and blow up the 1–2 plan budget.
**Why it happens:** Same files, adjacent lines.
**How to avoid:** Banner diff limited to: queued suffix + trailing `Reintentar` slot. Any other visual change is rejected in review.

## Code Examples

Verified patterns from the codebase (copy-adjacent, not copy-paste):

### Retry entry skeleton (mirrors send-time hook)
```kotlin
// Source: ChatViewModel.kt:360-443 (send-time hook) — retry reuses its Fused/AllFailed routing
// minus GroundingPrompt.augment and minus inference.
fun retryGrounding(assistantMessageId: String) {
    if (inputState.value.isFetchingWeb) return          // Pitfall #3 guard
    if (!fetcher.hasValidatedInternet()) {              // Pitfall #4 guard (needs internal visibility)
        refreshConnectivity(); return
    }
    retryJob?.cancel()
    retryJob = viewModelScope.launch(coroutineExceptionHandler) {
        val msgs = transcriptState.value.messages
        val idx = msgs.indexOfFirst { it.id == assistantMessageId && it.modelOnlyNotice == ModelOnlyNotice.OFFLINE }
        if (idx <= 0) return@launch                     // OFFLINE-only gate (Pitfall: FETCH_FAILED)
        val userContent = msgs.take(idx).lastOrNull { it.role == Role.USER }?.content ?: return@launch
        val urls = UrlDetector.allUrls(userContent)     // Pitfall #2: re-derive, never stored
        if (urls.isEmpty()) return@launch
        updateInput { it.copy(isFetchingWeb = true, webFetchProgress = WebFetchProgress(0, urls.size, ...)) }
        try {
            when (val result = multiUrlFetcher.fetchAll(urls, state.generationParameters.contextSize, onProgress = {...})) {
                is MultiUrlResult.Fused -> {
                    chatRepository.replaceSources(conversationId, msgs[idx].createdAt, result.details)  // NEW, Pitfall #1
                    updateTranscript { s -> s.copy(messages = s.messages.mapIndexed { i, m ->
                        if (i == idx) m.copy(modelOnlyNotice = null, groundedSources = result.okUrls,
                                             groundedSourceDetails = result.details) else m }) }
                }
                is MultiUrlResult.AllFailed -> Unit     // banner + Reintentar intact
            }
        } finally {
            updateInput { it.copy(isFetchingWeb = false, webFetchProgress = null) }
            refreshConnectivity()
        }
    }
}
```

### Repository replaceSources (delete-then-insert, no re-save)
```kotlin
// Source: ChatRepositoryImpl.kt:102-124 (saveMessageWithSources) + GroundedSourceDao.kt:24-28
suspend fun replaceSources(conversationId: Long, assistantCreatedAt: Instant, sources: List<GroundedSource>) {
    val rowId = messageDao.findAssistantRowId(conversationId, assistantCreatedAt.toEpochMilli())
        ?: return  // message deleted (user cleared chat) — silent no-op, transcript already filtered
    groundedSourceDao.deleteByMessage(rowId)
    groundedSourceDao.insertAll(sources.mapIndexed { i, s -> s.toEntity(rowId, i) })
    conversationDao.updateTimestamp(conversationId, System.currentTimeMillis())
}
// MessageDao addition (no schema change — plain SELECT on indexed columns):
@Query("SELECT id FROM messages WHERE conversation_id = :convId AND created_at = :createdAt AND role = 'ASSISTANT' LIMIT 1")
suspend fun findAssistantRowId(convId: Long, createdAt: Long): Long?
// NOTE: verify the stored role literal ('ASSISTANT' vs ordinal) in EntityMappers/MessageEntity before writing the query.
```

### Exit-gate tests (mirror existing suites)
```kotlin
// Source: ChatGroundingToggleTest.kt:165-209 (fetchAll mocking AFTER buildViewModel) + MultiUrlFetcherTest.kt
// 1. Offline→reconnect: stub fetchAll → AllFailed(OFFLINE), send, assert OFFLINE banner state;
//    flip stub → Fused, call retryGrounding, assert notice==null + details attached.
// 2. History-untouched: capture user/assistant content + createdAt before/after retry; assertThat(after).isEqualTo(before).
// 3. Row-reuse: in-memory Room, retry twice, assert single row set per message_id (count == details.size).
// 4. No-inference-on-retry: coVerify(exactly = 0) on the inference helper / helper.resolve after retryGrounding.
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Single-URL heuristic grounding (v2.2) | Multi-URL fan-out + persisted rows + preview (v2.3 Ph.52/53) | 2026-09-28 | Retry orchestrates the v2.3 pipeline; nothing from v2.2 is reused directly |
| `REPLACE`-insert re-save for sources | Delete-then-insert via `deleteByMessage` | Phase 53 WR-03 | Retry MUST follow the new path (Pitfall #1) |
| Hand-rolled regex extraction | Jsoup 1.23.2 parse-only (EXTRACT-01, done in Ph.52) | 2026-09-28 | Retry inherits cleaner extraction with zero work |

**Deprecated/outdated:**
- `saveMessageWithSources` for already-persisted messages: CASCADE-wipe hazard; retry path must not call it.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Standing model-only answer without disclaimer copy is acceptable UX after sources attach (locked UI-SPEC adds no new copy) | Pattern 1 caveat | Low — user may not realize the answer predates the sources; milestone-audit note covers it |
| A2 | `createdAt` millis collisions between adjacent assistant messages are negligible, making `(conversation_id, created_at)` lookup safe | Patterns / Pitfall 1 | Low — assistant persists happen seconds apart; `LIMIT 1` + delete-then-insert is idempotent anyway; planner may add `content` to the lookup for extra safety |
| A3 | `MessageEntity.role` stores the enum name (`'ASSISTANT'`) — planner must verify literal in `EntityMappers`/`MessageEntity` before writing the DAO query | Code Examples | Medium — wrong literal = lookup always null = silent retry no-op; one-line verification |

**If this table is empty:** All claims in this research were verified or cited — no user confirmation needed. (Not empty — A2/A3 need planner verification against `MessageEntity`/`EntityMappers`.)

## Open Questions

1. **Exact `MessageEntity.role` stored literal** — (RESOLVED 2026-09-28, 54-01 Task 1)
   - What we know: `MessageDao` has no role-filtered query today; `EntityMappers.toEntity/toDomain` round-trips role somehow.
   - What's unclear: Whether the column holds `'ASSISTANT'` (name) or an ordinal/int.
   - Answer: **Enum name.** `ChatMessage.toEntity` writes `role = role.name` (`EntityMappers.kt:44`) and `toDomain` reads via `Role.valueOf` with a `toRoleSafe` fallback (`EntityMappers.kt:23-27`) — the column holds `'ASSISTANT'`, not an ordinal. `findAssistantRowId` uses the `role = 'ASSISTANT'` predicate verbatim; no fallback needed.

## Environment Availability

No external dependencies. Pure in-tree Kotlin/Room/Compose work; `ACCESS_NETWORK_STATE` already declared. Instrumented device smokes (WEB-05/WEB-06 precedent) cover the visual backstop on hardware at release UAT.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Android SDK + Room + Compose | All retry code | ✓ (in-tree) | per STACK.md | — |
| `ACCESS_NETWORK_STATE` | `hasValidatedInternet()` | ✓ (manifest) | — | — |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** none.

## Security Domain

No new attack surface: same OkHttp client, same user-pasted URLs, same redirect/scheme policy, no secrets, no schema change.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | No auth in retry path |
| V3 Session Management | No | No sessions |
| V4 Access Control | No | App-private DB, same UID |
| V5 Input Validation | Yes | `UrlDetector.allUrls` re-extraction (same allowlist as send time); stored `resolved_url` remains untrusted TEXT rendered with http/https scheme check (Phase 53 T-53-09/10 precedent) |
| V6 Cryptography | No | No crypto changes |

### Known Threat Patterns for Grounding Retry

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Retry writes source rows for a deleted conversation/message | Tampering | `findAssistantRowId` null → silent no-op; never insert orphan rows |
| Stale-tap retry on FETCH_FAILED turn (scope escape) | Tampering | Eligibility re-checked on data (`modelOnlyNotice == OFFLINE`) inside `retryGrounding`, not just UI visibility |
| SSRF via re-fetched URLs | Tampering | Same `WebPageFetcher` policy (stripped client, 64KB cap, timeouts, scheme allowlist); Jsoup stays parse-only, never `Jsoup.connect()` |

## Sources

### Primary (HIGH confidence)
- Codebase: `ChatViewModel.kt:320-443,589-633,688-709` (hook, persist, Stop), `MultiUrlFetcher.kt:72-150`, `WebPageFetcher.kt:88,175-186`, `GroundedSourceDao.kt`, `MessageDao.kt`, `ChatRepositoryImpl.kt:31-135` (incl. Phase 54 warning comment), `ChatRepository.kt`, `ChatMessage.kt`, `MessageBubble.kt:100-101,214,347-373`, `ChatUiState.kt:179-180,201-248`, `EntityMappers.kt:29-42`, `MessageEntity.kt:19`, `AndroidManifest.xml:4-5`
- Phase artifacts: `54-CONTEXT.md` (locked decisions), `54-UI-SPEC.md` (copy/contract), `REQUIREMENTS.md` RETRY-01, `53/SECURITY.md` WR-03, `53/53-REVIEW.md` CR-01, `52/52-UI-REVIEW.md` fan-out cancel warning
- Tests as pattern source: `ChatGroundingToggleTest.kt:165-209`, `MultiUrlFetcherTest.kt`

### Secondary (MEDIUM confidence)
- None — no external lookup needed; all findings are in-tree.

### Tertiary (LOW confidence)
- None.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — every primitive verified in-tree by file/line this session.
- Architecture: HIGH — recommendation (sources-only attach) derived from locked guardrails + verbatim requirement wording + Phase 49 invariant.
- Pitfalls: HIGH — Pitfalls #1–#3 grounded in in-code warnings and prior review findings; #4/#5 arereasoned edge cases flagged as such.

**Research date:** 2026-09-28
**Valid until:** 30 days (stable — in-tree contracts; re-verify if Phases 52/53 files change before planning)

## Project Constraints (from AGENTS.md)

- Kotlin only (no Java); Jetpack Compose + Material 3; Hilt DI; Room (KSP) + DataStore.
- Clean architecture (domain/data/ui): all DB I/O through `ChatRepository`, never from ViewModel/Composable layers (DAO comment confirms).
- No plaintext secrets; no hardcoded keys (no secrets involved in this phase).
- Never block UI thread: retry fetch on `Dispatchers.IO` via `fetchAll`'s injected dispatcher; Room suspend calls off main.
- Offline-first: local chat unaffected; retry degrades to no-op banner persistence.
- WorkManager is for deferrable work (model downloads) — explicitly NOT for this phase per out-of-scope table.
