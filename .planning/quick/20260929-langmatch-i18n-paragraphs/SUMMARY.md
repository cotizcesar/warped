---
audit_acknowledged:
  milestone: v2.5
  at: 2026-10-01
  status: unknown
---

# Execution Summary — langmatch-i18n-paragraphs

Date: 2026-09-30
Status: **COMPLETE** — all 3 tasks implemented, committed, verified.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 (A) language-match rule | `14eca6da` | `feat(quick-langmatch): add language-match rule to both prompts` |
| 3 (C) paragraph spacing | `5a4ca318` | `feat(quick-langmatch): bump MarkdownText paragraph spacing 4dp to 8dp` |
| 2 (B) bilingual sweep | `1205b861` | `feat(quick-langmatch): bilingual EN+ES sweep, restore values-es with full parity` (51 files, +1538/−460) |

## What was built

- **Task 1:** `Always reply in the same language the user wrote in.` appended verbatim to both `GroundingPrompt.SYSTEM_PROMPT` and `LiteRTLmProvider.TOOL_USE_SYSTEM_HINT`. `GroundingPromptTest` gains a rule assertion; `LiteRTLmLoopTest` verbatim pin updated + rule assertion.
- **Task 3:** `MarkdownText` outer Column `Arrangement.spacedBy(4.dp)` → `8.dp` (single line; covers streaming + settled; list-internal `6.dp` untouched). No screenshot/golden tests reference the old value.
- **Task 2:** `values-es/strings.xml` restored from `c5f200a6^` (155 keys, zero drift vs current EN confirmed) and extended to **459 keys with exact 1:1 parity**. Every hardcoded user-visible string moved to resources across ~40 call sites (chat, models, endpoints, selector, settings, help + all 8 sections/46 steps, presets, prompt lab, benchmark, HF catalog, dialogs, toasts, snackbars, a11y labels, notifications, download errors). VMs resolve copy via injected `Application` context; prompt-template names/descriptions localized by id at the UI layer (`TemplateDisplayStrings.kt`); new `StringResourceParityTest` fails CI on key drift.

## Test results

- `./gradlew :app:assembleDebug` — **BUILD SUCCESSFUL**
- `./gradlew :app:testDebugUnitTest` (full, `--rerun-tasks`) — **512 tests, 0 failures, 0 errors, 0 skipped**
- Includes: `GroundingPromptTest`, `LiteRTLmLoopTest`, `StringResourceParityTest` (1/1 green), updated `SettingsTavilyTest` (EN-anchored Context stubs), `SourcePreviewMappingTest` (XML-based copy assertion), `ModelsDeleteErrorTest`, `SettingsGroundingToggleTest`, 4 Chat VM tests (generic `getString` stubs).
- Gate grep `Text("[^"]+")|contentDescription = "[^"]+"` (excluding `stringResource`/Previews) returns only `Text("+")` (FAB symbol) and `Text("••••••••")` (key mask) — both NEVER-translate.

## Deviations from plan (auto-fixed, Rules 1–3)

1. **[Rule 3] `stringResource` illegal in `semantics{}`/`?.let` lambdas** — hoisted to vals in composable scope in `ChatScreen` (jump pill, thinking row, tool-status row restructured `?.let`→`if`), `MessageBubble` (retry hint), `OgSourceCard` (5 sites), `SettingsScreen` (grounding switch).
2. **[Rule 2] Plan inventory missed ~60 literals** (found by re-grep gate): ChatInputBar icons, ModelSelector headers/empty, banner notice variants (Tavily/tools/fetch-failed), sheet/reading/loading/thinking rows, skipped-fuente rows, download paused/interrupted lines, endpoint test button, drawer bottom labels, param dialog + preset labels/descriptions/tiers, Tavily card + all VM statuses, notification channel/titles, `DownloadState.error` strings in worker/manager, PromptLab errors + template catalog, tier labels, smart-preset label. All resourced EN+ES.
3. **[Rule 2] `detectSyntaxIssues` returned display strings** — changed to `@StringRes Int?`, resolved at call site.
4. **[Rule 2] `SmartPresetCalculator.tierLabel` (EN-only)** — call site now maps `MemoryTier`→resource; domain function left untouched (now unused; harmless public API).
5. **[Rule 2] `EMPTY_EXTRACT_COPY` const** — moved to `sheet_empty_extract` resource; const deleted; test now parses EN XML.
6. **[Rule 1] Chat VM tests broke on strict `mockk<Context>()`** — added generic `getString` stubs to 4 Chat test files; EN-anchored per-ID stubs in `SettingsTavilyTest`.
7. **Plan/spec mismatches noted, table followed verbatim** where explicit (voseo imperatives in rows 7/13/34); extra keys use neutral tuteo matching base tone. `settings_grounding_desc` EN backfilled from actual call-site text ("Read the content of links you paste in chat.") since the plan's suggested ES desc didn't match the code. `cd_select_model` kept as separate key ("Select model" ≠ existing `select_model` "Select a model"). `delete_model_message`/`delete_endpoint_message` already existed — reused, no new keys.

## Intentionally NOT translated (NEVER-translate list)

Detection patterns/logs/comments/identifiers/model names/URLs; `"Cancelled"` worker status marker (compared, never rendered); `"LiteRT-LM"`/`"Net"`/`"Local"` provider pills and format badges; `"Native"` LM-Studio mode key; `"English"` PromptLab default (translation-target model parameter); template `systemPrompt`/`userPromptTemplate` (model instructions); `ramNote`/`blurb` catalog asset content; dynamic server/error text (`e.message`, `download.error` passthrough); numeric formats (`"%.1fx"`, `"[$number]"`, `"+"`, `"•"`, `"••••••••"`); `theme.label`; date patterns.

## Known stubs

None. No hardcoded empty values, placeholder text, or unwired components introduced.

## Threat flags

None. No new network endpoints, auth paths, file-access patterns, or schema changes. All edits are presentation-layer string resolution; no grounding/loop/download logic touched.

## On-device verification needed (no adb in this environment)

1. System locale **Español**: tour chat, models, endpoints, selector, settings, help, wizard (re-run from Settings), presets, benchmark, prompt lab, HF catalog, all dialogs/toasts/a11y — all Spanish, no leaks, no missing-resource crashes.
2. System locale **English**: same tour, all English.
3. Paragraph spacing visibly roomier in rendered answers; headings/lists unchanged.
4. Spanish question with web grounding on: answer in Spanish, no English lead-in. (Probabilistic — rule maximizes compliance, small local models may still deviate.)

## Self-Check: PASSED

- `values-es/strings.xml` exists; 459/459 key parity verified by script + `StringResourceParityTest`.
- `MarkdownText.kt` contains `spacedBy(8.dp)`; both prompts contain the language rule.
- Commits `14eca6da`, `5a4ca318`, `1205b861` exist in log; Task 2 commit has no deletions.
- Full suite 512/512 green; `assembleDebug` green.
