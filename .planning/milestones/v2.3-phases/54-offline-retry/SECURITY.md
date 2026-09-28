# SECURITY.md — Phase 54 Offline Retry

**Phase:** 54 — offline-retry (plans 01 + 02)
**Date:** 2026-09-28
**Auditor stance:** every mitigation assumed absent until a grep match proved it in the right location.
**Verdict:** all declared mitigations present in implemented code. No implementation files modified by this audit.

## Threat Verification

| Threat ID | Category | Disposition | Evidence (file:line) |
|-----------|----------|-------------|----------------------|
| T-54-01 (orphan-row write) | Tampering | mitigate — CLOSED | `ChatRepositoryImpl.kt:140-143`: `findAssistantRowId(...) ?: return` before any write; `MessageDao.kt:47`: nullable `Long?` lookup; `ChatRepositoryImpl.kt:144-150`: `deleteByMessage` + `insertAll` only after non-null `rowId`; no `messageDao.insert/update` anywhere in `replaceSources` |
| T-54-02 (scope escape to FETCH_FAILED) | Tampering | mitigate — CLOSED | `ChatViewModel.kt:755-760`: `indexOfFirst { id == ... && modelOnlyNotice == OFFLINE }`, `idx <= 0 → return` before any fetch; FETCH_FAILED/unknown ids never reach `fetchAll` |
| T-54-03 (SSRF via re-fetched URLs) | Tampering | mitigate — CLOSED | `ChatViewModel.kt:790-803`: retry calls the same `multiUrlFetcher.fetchAll` entry point (no parallel fetch path); `WebPageFetcher.kt:56-57,116`: stripped client, 8s/10s/20s timeouts, `http/https` scheme allowlist; `HtmlToTextExtractor.kt:9,63`: `Jsoup.parse(html, url)` parse-only, `Jsoup.connect` absent repo-wide in `data/grounding` |
| T-54-04 (overlapping fetches / orphan calls past Stop) | Denial of service | mitigate — CLOSED | `ChatViewModel.kt:739-747`: `isFetchingWeb`/`isGenerating`/`isStreaming` no-op gates + synchronous `retryJob?.isActive` overlap guard; `ChatViewModel.kt:358-362` (new send pre-cancels `retryJob`) and `860-885` (`stopGeneration` cancels `retryJob`, nulls it, clears chip flags; transcript untouched → queued state preserved) |
| T-54-05 (stale validated-online flag) | Spoofing | mitigate — CLOSED | `ChatViewModel.kt:748-751`: synchronous `if (!fetcher.hasValidatedInternet()) { refreshConnectivity(); return }` inside `retryGrounding` before any fetch — stale-flag taps are safe no-ops that hide the button |
| T-54-06 (stale tap retries FETCH_FAILED turn) | Tampering | mitigate — CLOSED | Backend: same `ChatViewModel.kt:755-760` data gate as T-54-02. UI: `MessageBubble.kt:398`: button renders only when `notice == OFFLINE && isValidatedOnline && !isFetchingWeb && !isGenerating`; FETCH_FAILED copy at `382-389` byte-identical, no button branch |
| T-54-SC (supply chain, plan 01) | Tampering | accept — CLOSED | Zero new dependencies: plan-01 commits `bd1150c` (5 files) + `dba97f9` (3 files) touch only grounding/DAO/repo/ViewModel/state/test — no `*.toml`/`build.gradle*` changes |
| T-54-SC (supply chain, plan 02) | Tampering | accept — CLOSED | Zero new dependencies: plan-02 commits `b534016` (`MessageBubble.kt` only) + `00cb375` (`ChatScreen.kt` only) — no dependency manifests touched |

## Prompt-Specified Invariants (verified, not just documented)

| Invariant | Evidence |
|-----------|----------|
| Retry is OFFLINE-only; no auto-retry on resume | `ChatScreen.kt:128-142`: `ON_RESUME` observer calls only `viewModel.refreshConnectivity()` (comment: "never triggers a fetch"); `ChatViewModel.kt:273-281`: `refreshConnectivity` only re-reads `hasValidatedInternet` into `isValidatedOnline`, never calls `fetchAll` |
| No inference re-run in retry path | Retry body `ChatViewModel.kt:787-837` contains `fetchAll` + `replaceSources` + transcript `m.copy(...)` only — `GroundingPrompt.augment` (line 432) and `helper.runInference` (line 592) occur exclusively in the send path; `grep -n` over lines 725-760 shows only the doc comment mentioning `augment` as prohibited |
| No history rewrite | Retry success `820-834` updates only index `idx` (`modelOnlyNotice=null`, `groundedSources`, `groundedSourceDetails`); assistant text/timestamps untouched; `ChatRepositoryImpl.kt:130-134` documents REPLACE-CASCADE avoidance, and `replaceSources` never calls `messageDao.insert/update` |
| Row-lookup predicate correct | `MessageDao.kt:43-47`: `... AND role = 'ASSISTANT' LIMIT 1`; `EntityMappers.kt:44`: `role = role.name` confirms the `'ASSISTANT'` literal is exact |
| Stop-cancel restores queued state | `ChatViewModel.kt:875-885`: `retryJob?.cancel(); retryJob = null`; transcript untouched so OFFLINE banner + Reintentar survive |
| No schema change | Plan commits touch no `@Entity`/`Migrations.kt`/`AppDatabase.kt`; `MessageDao` change is a `@Query`-only addition (`version = 15` unchanged) |
| Providers untouched | `git log` for `data/remote/` + `data/local/inference/` shows no Phase 54 commits; phase file lists contain no provider files |

## Unregistered Flags

None. Both plan summaries declare `## Threat Flags: None`, and no new attack surface was introduced (same fetcher policy, query-only DAO addition, visibility-hint-only button).

## Accepted Risks Log

- T-54-SC (both plans): reuse-only, zero new dependencies — nothing to slopcheck. Accepted per plan; verified by commit file lists.

**threats_open: 0/8**
