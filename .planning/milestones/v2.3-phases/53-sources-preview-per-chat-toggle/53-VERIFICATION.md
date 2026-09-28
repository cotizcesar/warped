---
phase: 53-sources-preview-per-chat-toggle
verified: 2026-09-28T19:30:00Z
status: passed
score: 6/6 must-haves verified
overrides_applied: 0
re_verification: true
previous_status: passed
previous_score: 6/6
gaps_closed: []
gaps_remaining: []
regressions: []
restamp_reason: "Summary commit-order staleness only — 53-01/02/03-SUMMARY.md first committed in bc0a474 AFTER the 549979e verification commit; content unchanged and already covered. No Phase 53 production/test file changed since 549979e (byte-identical). Phase 54 touched shared chat files only; full suite green confirms no regression."
deferred: []
human_verification: []
---

# Phase 53: Sources Preview + Per-Chat Toggle Verification Report

**Phase Goal:** Users can preview what each source says and control web grounding per conversation
**Verified:** 2026-09-28T19:30:00Z
**Status:** passed
**Re-verification:** Yes — mechanical re-stamp of the `549979e` passed verdict (6/6). See restamp note below.

## Re-stamp Note (why this file was rewritten without changing the verdict)

The GSD staleness checker flagged `53-VERIFICATION.md` (commit `549979e`, status passed, 6/6) as stale ONLY because `53-01/02/03-SUMMARY.md` were first committed in `bc0a474` — one commit AFTER the verification commit. Their content is unchanged and was already covered by the `549979e` verification (which ran against the working tree containing those summaries plus all 4 plans incl. gap-closure 53-04). No code changed in `bc0a474` (8 files: REQUIREMENTS/ROADMAP/STATE/state.json + 3 SUMMARY docs + ui-reviews .gitignore — zero `app/src` files). This re-verification re-confirmed the verdict against current HEAD:

- Phase 53 files byte-identical since `549979e`: `git diff 549979e..HEAD` over `SourcePreviewSheet.kt`, `GroundingPrecedence.kt`, `Migrations.kt`, `Migration14To15StaticTest.kt` is EMPTY.
- Phase 54 changes since touch shared chat files (`WebPageFetcher`, `MessageDao`, `ChatRepositoryImpl`, `ChatRepository`, `ChatScreen`, `ChatUiState`, `ChatViewModel`, `MessageBubble`) but introduce no regression: full unit suite 289/289 green (279 at `549979e` + Phase 54 additions), all Phase 53 test classes present and green.
- Spot-checks below re-run at HEAD; all PASS.

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can tap a source to preview its extracted text in a bottom sheet without leaving chat | ✓ VERIFIED | `SourcePreviewSheet.kt` exists (ModalBottomSheet, skipPartiallyExpanded, Fuente N title, scrollable verbatim body, empty-extract Spanish copy); `MessageBubble.kt:221-315` clickable Fuentes rows opening sheet via local `previewSource` remember; sheet zero-I/O confirmed — grep for fetch/DAO/repository in sheet returns only doc comments; `SourcePreviewMappingTest` green (in 289-suite) |
| 2 | User sees a numbered Fuentes list covering all N fetched sources for the turn | ✓ VERIFIED | `fuenteItems` prefers hydrated `groundedSourceDetails`, falls back to legacy ok-only urls; omitida rows `[N] url — omitida` struck (`LineThrough`, `onSurfaceVariant`, non-clickable); **CR-01 FIXED** in `06b9845`: `Fused.details` built at fusion time from `(pastedUrl, result)` pairs (resolved URL + text survive redirects), ViewModel persists `result.details` directly with legacy lookup as fallback-only; redirect JVM test in `MultiUrlFetcherTest` green |
| 3 | User can open the full page in the browser from the preview ("Abrir en navegador") | ✓ VERIFIED | `FilledTonalButton` "Abrir en navegador" in sheet; intent owned by `MessageBubble.kt` via `onOpenBrowser` callback (sheet stays pure-render); **WR-01 FIXED** in `75a5e3b`: http/https scheme allowlist + `SecurityException` catch alongside `ActivityNotFoundException`, Toast fallbacks; `browserTarget` returns `Grounded.url` only |
| 4 | User can override web grounding per conversation (on/off/inherit-global) and send a one-off model-only message ("Sin web") without changing any toggle | ✓ VERIFIED | `GroundingPrecedence.shouldGround(skipOnce, perChat, global)` single decision point, truth-table test green (3/3); `ChatScreen.kt:654-745` tri-state `Web: Sí/No/Heredar` menu with live `Heredar (activado/desactivado global)` hint (re-confirmed at HEAD); `ChatInputBar.kt:174-188` "Sin web" chip (re-confirmed at HEAD), reset after every send; `ChatViewModel.toggleSkipWebOnce` + `skipOnce` send-path (re-confirmed at HEAD); `ChatGroundingToggleTest` green; no DAO imports in `ui/` |
| 5 | User's per-chat web preference and persisted sources survive app restarts (single Room migration v15) | ✓ VERIFIED | Exactly one `Migration(14, 15)` in `Migrations.kt` (nullable `web_override` + `grounded_sources` + index); `AppDatabase` version 15; `15.json` re-confirmed at HEAD: `"version": 15`, `grounded_sources` + `web_override` present; `ChatRepositoryImpl` implements all 4 stubs (row-id keyed save, `loadConversation` hydration of assistant rows only); **WR-02 FIXED** (`e0f499b`: toggle no longer bumps `updated_at`); **WR-03 FIXED** (`da29274`: delete-then-insert per message, unique-index backing) |
| 6 | v14→v15 migration is gated for merge (JVM static gate green; on-device run deferred to release UAT) | ✓ VERIFIED | `Migration14To15StaticTest` re-run at HEAD: 5/5 green (tests=5 failures=0 errors=0): schema versions 14/15, v14 frozen baseline (no `web_override`/`grounded_sources` in 14.json), exact v15 delta (entity diff exactly `{grounded_sources}`, column diff exactly `{web_override}`, INTEGER nullable no-default, FK CASCADE, index), migration SQL exactness (start 14/end 15, exactly 3 captured `execSQL`, identifiers cross-checked against 15.json `createSql`), registration (`@Database version = 15`, exactly one `Migration(14, 15)`, `MIGRATION_14_15` in `addMigrations`). Full suite 289/289 green. On-device `MigrationTest` remains in-tree as release-UAT gold standard (see follow-up below) |

**Score:** 6/6 must-haves verified

### Release-UAT Follow-up (accepted, NOT a gap)

Same standing as the v2.2 deferred device smokes (ROADMAP: "automated gates pass, device smoke deferred, user-accepted"). The JVM static gate asserts everything verifiable without hardware — exact schema delta, exact migration SQL text, registration. Residual risk (SQLite runtime acceptance of standard ALTER/CREATE statements, row survival under SQLCipher) is low and covered by the in-tree gold standard on hardware:

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.warped.data.local.db.MigrationTest"
```

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `domain/model/GroundedSource.kt` | url + extractedText + OK/OMITIDA | ✓ VERIFIED | Exists, used by Fused.details, mappers, sheet |
| `data/local/db/entity/GroundedSourceEntity.kt` | FK CASCADE message_id, source_index | ✓ VERIFIED | Exists; 15.json confirms table |
| `data/local/db/dao/GroundedSourceDao.kt` | insertAll + ordered getByMessage + delete | ✓ VERIFIED | Exists; delete-then-insert fix present |
| `data/grounding/GroundingPrecedence.kt` | pure shouldGround | ✓ VERIFIED | Byte-identical since 549979e; 3/3 tests green |
| `ui/chat/components/SourcePreviewSheet.kt` | zero-I/O bottom sheet + mappers | ✓ VERIFIED | Byte-identical since 549979e; grep-clean of I/O imports |
| `data/local/db/Migrations.kt` MIGRATION_14_15 | single 14→15 migration | ✓ VERIFIED | Byte-identical since 549979e; only Migration(14,15) |
| `app/schemas/.../AppDatabase/15.json` | v15 export | ✓ VERIFIED | Re-confirmed: version 15, grounded_sources + web_override |
| `test/.../Migration14To15StaticTest.kt` | JVM static migration gate | ✓ VERIFIED | Byte-identical since 549979e; 5/5 green at HEAD |
| `androidTest/.../MigrationTest.kt` | v14→v15 on-device gold standard | ✓ PRESENT (release-UAT follow-up) | In-tree; runs on hardware via resume command above |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| MessageBubble Fuentes | SourcePreviewSheet | tap → previewSource state | WIRED | Clickable rows set previewSource/previewNumber; sheet renders from props (re-confirmed at HEAD) |
| Sheet | Browser | onOpenBrowser → guarded ACTION_VIEW | WIRED | Scheme gate + dual catch; dismisses on press |
| ChatViewModel hook | GroundingPrecedence | shouldGround per send | WIRED | Suspend override read once per send; skipOnce > perChat > global (re-confirmed at HEAD) |
| ChatViewModel | ChatRepositoryImpl | saveMessageWithSources post-fetch/pre-inference | WIRED | Row-id keyed; failure rethrown → Snackbar, send continues |
| Repository | Room | GroundedSourceDao + ConversationDao override | WIRED | Hydration on loadConversation; override read/write |
| ChatScreen menu/chip | ViewModel | setWebOverride / toggleSkipWebOnce | WIRED | Pending-override pre-first-send applied at ensureConversation |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|-------------------|--------|
| SourcePreviewSheet | extractedText | grounded_sources rows via hydration | ✓ FLOWING | Fused.details → saveMessageWithSources → DAO → loadConversation hydrate → fuenteItems |
| Fuentes list | fuenteList | groundedSourceDetails + legacy urls | ✓ FLOWING | Covers ok + omitida in fetch-block order; zero-ok → no block |

### Behavioral Spot-Checks (re-run at HEAD for this re-stamp)

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full unit suite green | `./gradlew :app:testDebugUnitTest --offline` | **289 tests, 0 failures, 0 errors, 0 skipped** (aggregated from result XMLs; 279 at 549979e + Phase 54 additions) | ✓ PASS |
| Static migration gate | `--tests "com.warped.data.local.db.Migration14To15StaticTest"` | 5/5 green (tests=5 failures=0 errors=0) | ✓ PASS |
| Precedence truth table | GroundingPrecedenceTest | 3/3 green | ✓ PASS |
| Phase 53 test classes present | result XML listing | Hydration, Toggle, Mapping, MultiUrlFetcher, Precedence, Static all present | ✓ PASS |
| 15.json intact | schema parse | version 15, grounded_sources + web_override present | ✓ PASS |
| Tri-state + Sin web + preview wiring present | grep at HEAD | `Heredar (activado/desactivado global)`, `Sin web` chip, `previewSource`/`SourcePreviewSheet`/`onOpenBrowser`, `toggleSkipWebOnce`/`skipOnce` all found | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| SRC-01 | 53-03 | Tap source → bottom-sheet preview | ✓ SATISFIED | Sheet + clickable rows + mapper tests |
| SRC-02 | 53-01/53-02 | Numbered Fuentes covering all N | ✓ SATISFIED | Fusion-time details (CR-01 fix) + hydration |
| SRC-03 | 53-03 | Abrir en navegador | ✓ SATISFIED | Guarded ACTION_VIEW (WR-01 fix) |
| TOGGLE-01 | 53-02 | Tri-state per-chat override | ✓ SATISFIED | Menu + inherit hint + precedence |
| TOGGLE-02 | 53-02 | One-off Sin web chip | ✓ SATISFIED | Chip + skipOnce-first precedence + reset |
| TOGGLE-03 | 53-01/53-02/53-04 | Preference + sources survive restart | ✓ SATISFIED | Code + schema + hydration + JVM static migration gate (5/5); on-device run as release-UAT follow-up |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (none) | — | TODO/FIXME/NotImplementedError scan over sheet, precedence, model, repository impl, bubble + static test | — | CLEAN |

### Review Findings Disposition

| Finding | Status | Evidence |
|---------|--------|----------|
| CR-01 redirect details loss | ✅ FIXED | `06b9845`: Fused.details at fusion time + redirect JVM test |
| WR-01 unvalidated intent URL | ✅ FIXED | `75a5e3b`: scheme allowlist + SecurityException catch |
| WR-02 toggle bumps updated_at | ✅ FIXED | `e0f499b`: override-only UPDATE |
| WR-03 REPLACE no-op | ✅ FIXED | `da29274`: delete-then-insert + unique index |
| WR-04 device migration gate | ✅ CLOSED via static gate | `aa6c9c8`: Migration14To15StaticTest 5/5 + suite green; hardware run recorded as release-UAT follow-up |
| IN-01/IN-02 | ℹ️ Accepted | Low-impact hygiene; no user-visible defect |

### Gaps Summary

No gaps. This re-stamp re-confirmed all 6 truths at HEAD: Phase 53 files byte-identical since `549979e`, `bc0a474` added docs only, Phase 54 shared-file changes cause no regression (full suite 289/289 green, all Phase 53 test classes green, tri-state/Sin web/preview wiring spot-checked present, 15.json intact). The on-device `MigrationTest` remains the release-UAT gold standard as recorded above.

---

_Verified: 2026-09-28T19:30:00Z_
_Verifier: the agent (gsd-verifier)_
_Previous verification: 549979e (passed, 6/6, 2026-09-28T18:00:00Z) — this re-stamp changes the stamp only, not the verdict._
