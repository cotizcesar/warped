---
status: gaps_found
score: "all automated gates pass; 1 manual smoke deferred"
---
# Phase 49 (Surface Removal) — Verification

**Date:** 2026-09-28
**Plans:** 49-01, 49-02 (waves 1–2, sequential)
**Commits:** `6319918` (49-01), `2448a2d` (49-02)
**Transition:** skipped (`--no-transition`; autonomous mode owns STATE/ROADMAP updates)

## Plan gates (all PASS)

### 49-01 — Skills + tool-loop removal
- [x] Skills grep-zero gate (`SkillChipsRow|SkillPreferences|SkillRepository|ToolGating|ToolHistory|ToolText|ExpressionEvaluator|CalculatorSkill|CurrentTimeSkill|JsonFormatterSkill|NoToolSupportNotice|ToolErrorRow|setSkillEnabled|LmStudioToolLoop|LocalToolExecutor`, minus `ToolResultRow|splitToolContent`): zero matches
- [x] Summarize grep-zero gate (`Summarize|summarize`): zero matches (required renaming `summarizeToolResult` → `truncateToolSummary`)
- [x] `./gradlew :app:assembleDebug`: BUILD SUCCESSFUL
- [x] `EntityMappers.kt`: zero diff (name-based Role converter unchanged, no migration)

### 49-02 — HF token + search removal → static catalog + release posture
- [x] HF token gates (`HF_TOKEN_KEY|storeHuggingFaceToken|deleteHuggingFaceToken|getHuggingFaceToken|HuggingFaceAuthInterceptor|?token=` and `add your HuggingFace token`): zero matches
- [x] Search-surface gate (`SearchResults|search_models|search_models_hint|hugging_face_suggestions|download_from_hf|Open on Hugging Face|400ms|debounce`, minus `catalog`): zero matches (one self-inflicted comment hit fixed by rewording)
- [x] `./gradlew :app:assembleDebug`: BUILD SUCCESSFUL
- [x] `bash scripts/audit-dependencies.sh`: green (no banned direct deps, no pre-release artifacts)
- [x] `./gradlew :app:assembleRelease`: BUILD SUCCESSFUL (43s, 48 tasks)
- [x] `proguard-rules.pro`: no `com.warped.*skills` keeps; `com.google.ai.edge.litertlm.**` + 0.17.x tool entry-point keeps intact (16 refs)
- [x] Exact-copy checks: 401 message, catalog-load-failure, empty-state body, wizard entry, "Model catalog" title/label — all exact
- [x] Deleted strings keys (6 × 2 locales): zero references; `type_message` kept
- [x] `:app:testDebugUnitTest` (full suite): BUILD SUCCESSFUL

## Must-haves review (49-01)
- Chat input bar: no skill chips, OutlinedTextField top row — verified by code (SkillChipsRow call site + params removed) + debug build
- Grep-zero gates — passed (above)
- Remote single-turn, no tools[] — verified by code (loop entry deleted, request `tools[]` DTO fields removed, no handler sets them)
- Legacy TOOL rows read-only collapsed — verified by code (ToolResultRow kept, all producers deleted); Room converter untouched
- Placeholder/send/stop/image/Thinking behavior — untouched code paths (ChatInputBar retains all non-skill params)
- Malformed TOOL payloads render muted without crashing — `parseToolRow` fallback preserved (moved, not changed); covered by existing `room mapper preserves tool row identity` test

## Must-haves review (49-02)
- Settings sections order/copy/spacing — Data section now first, no other section touched
- Direct downloads, no auth — verified by code + gate (no `Authorization` in download path; comments only)
- Static catalog 3 cards with vision/audio badges — verified by code (allowlist has exactly 3 entries; badges built from `vision`/`audio` only)
- Exact copies — verified by grep (above)
- Audit/R8/release — passed (above)
- Zero-dependency constraint — no new dependencies (only deleted code + 2 new files using existing deps)
- Spacing scale — reused existing 14dp/8dp/16dp/12dp values, no orphaned Spacers (HF card block removed wholesale)

## Gaps / human needed
1. **On-device release smoke (human_needed):** launch → load allowlisted model → one local turn → one remote turn → open a legacy chat with TOOL rows. Not runnable in this environment (no device/emulator). Required before regarding DEL-06 fully closed.
2. **Orphaned Keystore entry (accepted):** upgraded installs keep an unread `huggingface_token` encrypted entry (never read, never referenced). Harmless; no cleanup wired per plan scope.

## Verdict
**gaps_found** — all automated gates pass; one manual smoke (item 1) needs a human with a device. No code gaps.
