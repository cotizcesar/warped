# SECURITY.md — Phase 53: Sources Preview + Per-Chat Toggle

**Phase:** 53 — sources-preview-per-chat-toggle
**Audited:** 2026-09-28 (post gap-closure, incl. CR-01 / WR-01 / WR-02 / WR-03 fixes)
**ASVS Level:** 1 (local-first mobile app; network surface is user-configured endpoints + fetched page URLs)
**Verdict:** SECURED — 16/16 threat dispositions verified in code
**Auditor stance:** every mitigation assumed absent until grep-proved below. All evidence is file:line, not documentation.

## Threat Verification (PLAN threat registers, all four plans)

### Plan 53-01 — Persistence + contracts

| Threat ID | Category | Disposition | Evidence |
|-----------|----------|-------------|----------|
| T-53-01 | Tampering (status TEXT) | mitigate | CLOSED — `data/local/db/entity/EntityMappers.kt:82-85` `toGroundedSourceStatusSafe()` maps only `"ok"` → OK, everything else → OMITIDA; read path `GroundedSourceEntity.toDomain()` (:92-96) uses it, never crashes |
| T-53-02 | Tampering (URL provenance) | mitigate | CLOSED — `GroundedSourceEntity.kt:33` stores `resolved_url` only (no pasted-text column); rows built at fusion time from `(pastedUrl, result)` pairs in `data/grounding/MultiUrlFetcher.kt:116-128` (OK rows carry `result.url` post-redirect); ViewModel persists `result.details` directly (`ui/chat/ChatViewModel.kt:401-404`), legacy pasted-lookup is fallback-only |
| T-53-03 | Info disclosure (text at rest) | accept | CLOSED — accepted risk logged below; new table rides existing SQLCipher factory (`di/DatabaseModule.kt:50-51` `SupportFactory`), no new crypto surface |
| T-53-04 | Availability (v15 upgrade) | mitigate | CLOSED — exactly one `Migration(14, 15)` (`data/local/db/Migrations.kt:116-131`: nullable `web_override` ADD COLUMN, `grounded_sources` CREATE TABLE with `ON DELETE CASCADE`, message_id index); registered in `addMigrations` (`di/DatabaseModule.kt:53`) with `fallbackToDestructiveMigration(false)` (:54); `AppDatabase.kt:33` version 15; JVM static gate `test/.../Migration14To15StaticTest.kt` exists |
| T-53-SC (01) | Tampering (supply chain) | mitigate | CLOSED — zero new dependencies (phase file lists contain no build files; verification confirms `git status` clean on schemas/build files) |

### Plan 53-02 — Hydration + precedence + toggle

| Threat ID | Category | Disposition | Evidence |
|-----------|----------|-------------|----------|
| T-53-05 | Tampering (skip-decision point) | mitigate | CLOSED — single pure function `data/grounding/GroundingPrecedence.kt` `shouldGround`; evaluated once per send at `ChatViewModel.kt:355` via suspend `getWebOverride` read (`:350`), never a hot Flow |
| T-53-06 | Info disclosure (extracted text in UI) | accept | CLOSED — accepted risk logged below; same trust level as chat history already in Room |
| T-53-07 | Availability (persist failure breaks send) | mitigate | CLOSED — `ChatViewModel.kt:615-633`: persist wrapped in try/catch, Timber log + non-blocking Snackbar (`No se pudieron guardar las fuentes…`), send always continues |
| T-53-SC (02) | Tampering (supply chain) | mitigate | CLOSED — zero new dependencies |

### Plan 53-03 — Preview sheet + Fuentes list

| Threat ID | Category | Disposition | Evidence |
|-----------|----------|-------------|----------|
| T-53-08 | Tampering (stored-HTML injection) | mitigate | CLOSED — sheet renders `Text()` composables only (`ui/chat/components/SourcePreviewSheet.kt:65-109`); no WebView/Html/`fromHtml`/fetch/DAO imports (grep-clean); text is sanitized upstream at fetch time (`data/grounding/WebPageFetcher.kt:153` `WebContextSanitizer.sanitize`, Jsoup parse-only `HtmlToTextExtractor.kt:63`) |
| T-53-09 | Tampering (malicious redirect / non-http scheme) | mitigate | CLOSED — `browserTarget` returns `source.url` only (`SourcePreviewSheet.kt:192`); intent site enforces http/https allowlist (`ui/chat/components/MessageBubble.kt:294-301`, invalid scheme → Toast, no intent fired) |
| T-53-10 | Info disclosure (text exfil via intent) | mitigate | CLOSED — `Intent(Intent.ACTION_VIEW, uri)` carries URI only (`MessageBubble.kt:303`); zero `putExtra` in MessageBubble/SourcePreviewSheet (grep-clean); extracted text never crosses into the intent |
| T-53-11 | Availability (ACTION_VIEW crash, browserless) | mitigate | CLOSED — dual catch `ActivityNotFoundException` (:305) + `SecurityException` (:311) with Toast fallback; combined with the WR-01 scheme gate above |
| T-53-SC (03) | Tampering (supply chain) | mitigate | CLOSED — zero new dependencies |

### Plan 53-04 — Static migration gate

| Threat ID | Category | Disposition | Evidence |
|-----------|----------|-------------|----------|
| T-53-04-01 | Tampering (14.json baseline drift) | mitigate | CLOSED — `Migration14To15StaticTest.kt` asserts `web_override`/`grounded_sources` absent from 14.json |
| T-53-04-02 | Tampering (migration SQL drift) | mitigate | CLOSED — same test captures `MIGRATION_14_15.migrate()` execSQL (exactly 3 statements) and cross-checks identifiers against 15.json `createSql` |
| T-53-04-SC | Tampering (supply chain) | accept | CLOSED — accepted risk logged below; test adds no dependencies by design (uses only kotlinx.serialization.json + mockk, both pre-existing) |

## Review-finding fixes (53-REVIEW.md) — re-verified, not taken on trust

| Finding | Evidence |
|---------|----------|
| CR-01 redirect details loss | FIXED — `MultiUrlFetcher.kt:114-137` builds `Fused.details` at fusion time (resolved URL + text survive redirects); `ChatViewModel.kt:401-404` persists `result.details` when non-empty |
| WR-01 unvalidated intent URL + narrow catch | FIXED — `MessageBubble.kt:294-317` scheme allowlist + `ActivityNotFoundException` + `SecurityException` catches |
| WR-02 toggle bumps `updated_at` | FIXED — `ConversationDao.kt:35-36` `UPDATE conversations SET web_override = :override WHERE id = :id` (no timestamp touch; `observeAll` ordering at :12 unaffected) |
| WR-03 REPLACE no-op, dup rows | FIXED as scoped — `ChatRepositoryImpl.kt:107-123` keys rows on `messageDao.insert` Long return (never UUID) with `deleteByMessage(rowId)` before `insertAll` (delete-then-insert); DAO documents the choice (`GroundedSourceDao.kt:15-25`) |
| WR-04 device migration gate | CLOSED via JVM static gate (5/5) + full suite green; on-device `MigrationTest` retained as release-UAT gold standard |

## Prompt-scoped checks (auditor brief)

- **Source rows keyed on insert return:** `ChatRepositoryImpl.kt:107` `val rowId = messageDao.insert(…)` → `:113-115` delete + insert keyed on `rowId`. Never `ChatMessage.id` (UUID).
- **No history rewrite:** toggle writes override column only; `saveMessageWithSources` inserts the new assistant message + its rows, never UPDATEs prior messages; hydration (`ChatRepositoryImpl.kt:43`) is read-only attach on `loadConversation`.
- **Providers untouched:** phase file lists contain no `data/remote/*` or inference files; git diff of those trees over the phase range is empty.
- **No SQL injection:** all new/changed DAO queries are Room `@Query` with bound parameters (`:messageId`, `:id`, `:override`); no string-concatenated SQL (grep for `" +` in both DAOs clean). Migration DDL is static literal SQL.
- **No DAO/entity imports in `ui/`:** grep over `ui/chat` for `data.local.db.dao|data.local.db.entity` returns zero matches.

## Accepted Risks Log

1. **T-53-03** — Extracted page text at rest inherits SQLCipher DB encryption; no per-table crypto. Same control as chat history. No new exfiltration path introduced.
2. **T-53-06** — Persisted extracted text surfaced in the preview sheet is the same trust level as stored chat history. Sheet performs zero I/O (offline-safe by construction).
3. **T-53-04-SC** — Static gate adds no dependencies; supply-chain surface unchanged.
4. **WR-03 residual (accepted, NOT a blocker)** — No DB-level `UNIQUE(message_id, source_index)` on v15 (adding one requires a new migration + schema export, deliberately deferred per in-code comment). Dup prevention rests on delete-then-insert in the single writer path `saveMessageWithSources`. A future second writer (e.g. Phase 54 retry) MUST reuse `deleteByMessage` first or add the unique index with a v16 migration. Flagged for Phase 54.
5. **WR-04 residual (accepted, NOT a blocker)** — On-device `MigrationTest.migrate14To15` retained in-tree; run `./gradlew :app:connectedDebugAndroidTest --tests "com.warped.data.local.db.MigrationTest"` at release UAT.

## Unregistered Flags

None. All three plan SUMMARIES (`53-02`, `53-03`, `53-04`) record `Threat Flags: None`; no new attack surface beyond the registers above was found during audit.

## Implementation files — READ-ONLY

No implementation file was modified by this audit. Only this SECURITY.md was created.
