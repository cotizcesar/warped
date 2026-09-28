---
phase: 47-real-tool-execution
plan: "01"
subsystem: skills-tracer-foundation
tags: [skills, tools, datastore, hilt, compose, room-role]
dependency_graph:
  requires: []
  provides: [sealed-Skill, SkillRepository-Hilt, SkillPreferences-all-on, shared-descriptor-mapper, Role-TOOL, SkillChipsRow, tool-copy-deck]
  affects: [47-02-local-tools, 47-03-remote-loop]
tech_stack:
  added: []
  patterns: [DataStore-Preferences-all-on-defaults, shared-descriptor-no-drift, Hilt-Binds-per-feature, ToolEventSink-callback]
key_files:
  created:
    - app/src/main/java/com/warped/domain/skills/Skill.kt
    - app/src/main/java/com/warped/domain/skills/SkillRepository.kt
    - app/src/main/java/com/warped/domain/skills/ToolExecutor.kt
    - app/src/main/java/com/warped/data/skills/SkillDescriptors.kt
    - app/src/main/java/com/warped/data/skills/SkillPreferences.kt
    - app/src/main/java/com/warped/data/skills/SkillRepositoryImpl.kt
    - app/src/main/java/com/warped/data/skills/NoopToolExecutor.kt
    - app/src/main/java/com/warped/di/SkillsModule.kt
    - app/src/main/java/com/warped/ui/chat/components/SkillChipsRow.kt
    - app/src/main/java/com/warped/ui/chat/components/ToolCopy.kt
    - app/src/test/java/com/warped/data/skills/SkillDescriptorsTest.kt
  modified:
    - app/src/main/java/com/warped/domain/model/Role.kt
    - app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt
    - app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt
    - app/src/main/java/com/warped/data/local/inference/LocalLlmProvider.kt
    - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
    - app/src/main/java/com/warped/ui/chat/ChatUiState.kt
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
    - app/proguard-rules.pro
    - app/src/test/java/com/warped/ui/chat/ChatCancellationTest.kt
decisions:
  - "Tool result rows persist as role=tool content='<toolId>\\n<summary>' (ToolCopy contract) instead of RESEARCH's 'Used {Display}: ...' literal — avoids lossy Display→id reverse-mapping at render time"
  - "ToolExecutor binds NoopToolExecutor until Plan 02 swaps the binding target — keeps the Hilt graph resolvable in this tracer commit"
  - "SkillRepositoryImpl owns a dedicated SupervisorJob scope (no @ApplicationScope binding exists in this project)"
metrics:
  duration: "~45 min"
  completed: "2026-09-28"
---

# Phase 47 Plan 01: Skills Tracer Foundation Summary

**One-liner:** Kotlin-only Skills Lite surface — sealed Skill hierarchy, DataStore all-on toggles, shared descriptor→schema mapper with golden tests, Role.TOOL crash fix, and the SkillChipsRow + verbatim copy-deck UI — ready for Plans 02/03 to attach executors.

## What Was Built

**Task 1 — Domain contracts + Role.TOOL fix (`3f538cb`):**
- `domain/skills/Skill.kt`: sealed `Skill` (`Tool{id,displayName,description}`, `PromptTemplate{id,systemPrompt}`), `SkillCategory{Tool,PromptTemplate}`, `SkillIds` constants, `skillChipLabel` / `toolDisplayName` / `toolDisplayNameCapitalized` mapping (calculator→"calculator", current_time→"current time", json_formatter→"JSON formatter", unknown ids degrade to underscore→space).
- `domain/skills/SkillRepository.kt`: `enabledSkills StateFlow<List<Skill.Tool>>` + `enabledMap StateFlow<Map<String,Boolean>>` + `setEnabled`.
- `domain/skills/ToolExecutor.kt`: `ToolExecutor.execute(name, argsJson): ToolResult`, sealed `ToolResult{Success(text,summary),Failure(reason)}`, `ToolEventSink{onStart,onFinish}` (RESEARCH Pitfall 4 option 1).
- `Role.TOOL` added; `EntityMappers` uses crash-safe `toRoleSafe()` (unknown strings → TOOL for "tool", else ASSISTANT — never throws to UI); exhaustive-`when` completed in `LiteRTLmProvider` (TOOL resumes as model-side context; dedicated `Message.tool` mapping lands in Plan 02) and `LocalLlmProvider` (assistant-adjacent tag).

**Task 2 — Mapper + prefs + repo + Hilt + R8 (`30bc1aa`):**
- `SkillDescriptors.kt`: exactly 3 descriptors with single-write `@ToolParam` description constants Plan 02 imports + `toOpenAiParameters()` via `buildJsonObject` (no string templates, T-47-01).
- `SkillPreferences.kt`: DataStore `skill_preferences`, per-skill boolean keys, missing-key default `true` (all-on), unknown-id `setEnabled` rejected.
- `SkillRepositoryImpl.kt`: descriptors × prefs → `enabledSkills` (zero-enabled legal = plain chat).
- `SkillsModule.kt`: `@Binds` SkillRepository + ToolExecutor→NoopToolExecutor (Plan 02 replaces target).
- `proguard-rules.pro`: keeps for `domain/skills` + `data/skills` (T-47-04); existing ToolSet/@Tool keeps untouched.
- `SkillDescriptorsTest.kt`: golden serialized JSON per skill + descriptor≡constant drift guard — green.

**Task 3 — SkillChipsRow + copy deck (`76eb7bf`, plus test fix `32f760c`):**
- `ToolCopy.kt`: UI-SPEC §8 templates verbatim (`Using {display}…`, `{Display} failed: {reason}`, `Used {Display}`, em-dash notice) + `format…` substituters + `toolResultContent`/`parseToolResultContent`/`summarizeToolResult` (~200 chars + "…").
- `SkillChipsRow.kt`: LazyRow of exactly 3 text-only chips, exact labels, Row 0 above the input, ON=primary coral/ink text/Medium, OFF=transparent+border/dimmed, 28dp visuals in 48dp touch targets, `{Label} skill, {enabled|disabled}, tap to toggle` a11y, no-op while generating.
- `ChatInputBar`: hoisted `skillEnabled`/`onToggleSkill` params (reasoningEnabled pattern), chips hidden when `canSend==false`.
- `ChatUiState`: `skillEnabled` map + `ActiveToolError(toolId,reason)` + `showNoToolSupportNotice`.
- `ChatViewModel`: collects `SkillRepository.enabledMap`, exposes `setSkillEnabled` (ViewModel-backed rotation survival, DataStore process-death survival).
- `MessageBubble.kt`: `Role.TOOL` messages render `ToolResultRow` (collapsed default, AnimatedVisibility expand, SelectionContainer+MarkdownText muted italic mirroring Thinking) + `ToolErrorRow` (ErrorOutline 0xFFEF4444, maxLines 2) + `NoToolSupportNotice` (muted italic, no icon).
- `ChatScreen.kt`: chips wired, status row via `formatToolStatus` (U+2026, spinner stays 0xFF545450) + `Tool running:` a11y, error/notice rows placed per UI-SPEC.

## Verification

- `:app:compileDebugKotlin` clean (only pre-existing hiltViewModel deprecation warning).
- `:app:testDebugUnitTest` full suite green, incl. 5 new `SkillDescriptorsTest` tests.
- Copy grep hits all 4 exact strings as live constants in `ToolCopy.kt`.
- Zero dependency changes (`git diff` on gradle/catalog/build files empty — RUNTIME-12 untouched).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `putJsonArray.add(String)` type error**
- **Found during:** Task 2 compile
- **Issue:** `add(it.name)` expects JsonElement in this kotlinx-serialization version
- **Fix:** `add(JsonPrimitive(it.name))` + import
- **Commit:** 30bc1aa

**2. [Rule 1 - Bug] `ChatCancellationTest.buildViewModel` broke on new ViewModel param**
- **Found during:** Task 3 verification (test compile)
- **Issue:** Direct `ChatViewModel(...)` construction missing required `skillRepository`
- **Fix:** Mocked `SkillRepository` with all-on `enabledMap` passed through
- **Commit:** 32f760c

### Plan-File Additions (inline necessities, not scope change)

- `LiteRTLmProvider.kt` / `LocalLlmProvider.kt`: exhaustive-`when` TOOL branches — explicitly required by Task 1's audit action, just absent from its `<files>` list.
- `ChatScreen.kt`: status-row exact string + error/notice placement + chips wiring — required by Task 3's placement/copy contract, absent from its `<files>` list.
- `ToolCopy.kt` (new) + `NoopToolExecutor.kt` (new): single-source copy deck shared across components/ChatScreen, and the Hilt placeholder Task 2's action text explicitly anticipates ("if Hilt compile requires the impl now, bind to a NoopToolExecutor").

## Known Stubs (intentional, owned by follow-up plans)

- `NoopToolExecutor.execute` always returns `Failure("Tool execution not yet available")` — Plan 02 replaces the binding with the local `@Tool` dispatcher.
- `ChatUiState.activeToolError` / `showNoToolSupportNotice` are never set yet — Plans 02/03 produce them; composables render them today.
- `LiteRTLmProvider` TOOL history resumes as `Message.model` — dedicated `Message.tool(ToolResponse)` mapping lands with Plan 02's executor.

## Threat Flags

None — no new surface beyond the plan's threat model. No new network endpoints, auth paths, or schema changes; DataStore keys are fixed per-skill ids (T-47-01…T-47-04 mitigations all landed as specified; T-47-SC accept holds — zero installs).

## Self-Check: PASSED

- All 11 created + 11 modified files verified present on disk.
- All 4 commits (`3f538cb`, `30bc1aa`, `76eb7bf`, `32f760c`) verified in `git log`.
