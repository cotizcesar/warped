# Phase 44 — Agent Skills Lite

**Requirements:** SKILLS-01..06
**Plans:** 3 (44-01 domain + 4 skills, 44-02 LlmModelHelper tool plumbing, 44-03 chat UI chips)

## Decisions

- **Skills are metadata, not infrastructure.** A `Skill` is a value object: `id`, `name`, `description`, `icon`, `category` (Tool / PromptTemplate), `promptPrefix` (null for tool-only skills), `systemPrompt` (for the tool's own context).
- **Two skill kinds**:
  - **Tool skills** wrap a `@Tool`-annotated method. The model can call them at inference time. (Calculator, current time, JSON formatter.)
  - **Prompt-template skills** inject a system prompt into the request. The chip inserts a prefix into the chat input. (Text summarizer.)
- **SKILLS-02 — `LlmModelHelper` interface extension**: add an optional `tools: List<Skill>` parameter to `runInference(request, enableThinking, tools)`. Helpers that don't yet support tools no-op the parameter. This keeps the surface area minimal and avoids a breaking change to all 5 call sites.
- **SKILLS-03 — `LmStudioHelper` tool mapping**: convert each `Skill` to an LM Studio `tools[]` schema entry (name, description, parameters JSON Schema). Skip the actual HTTP execution since the model returns the tool call as a regular message and Warped's `LMStudioProvider` doesn't yet execute tool calls server-side.
- **SKILLS-04 — Skill chips in `ChatInputBar`**: a horizontal row of small `AssistChip`s above the input field, gated by `SkillRepository.enabledSkills` (a `Flow<List<Skill>>`). Tapping a chip inserts the skill's `promptPrefix` into the input text.
- **SKILLS-05 — `SkillRepository`**: an interface that exposes `enabledSkills: Flow<List<Skill>>` + a `Set<String>` of enabled skill IDs persisted via DataStore (same pattern as `AdvancedPreferences.thinkingEnabled`).
- **SKILLS-06 — `skills/` package**: each skill is one file under `domain/skills/`:
  - `CalculatorSkill.kt`
  - `CurrentTimeSkill.kt`
  - `JsonFormatterSkill.kt`
  - `SummarizeSkill.kt`
- **Hard cap at 4 skills** in the initial Lite cut to keep scope tight. The chip set stays a flat row of `AssistChip`s; if we add more later we move to a `FlowRow`.
- **No LiteRT-LM tool execution wiring in this phase**: `LiteRtLlmHelper` only logs the tool list (the model surfaces tool calls as `[tool:NAME]` markers, already handled by `ChatViewModel`). Real `@Tool` execution is gated on LiteRT-LM 0.13.1's tool-call API which we'll evaluate in v2.1.

## Carry-overs
- `DeviceToolSet` (existing) provides `@Tool` methods that the LiteRT-LM engine can register. We add the metadata layer; we don't re-annotate the existing tools.
- `ToolRegistry` (existing) is a parallel tool system for LiteRT-LM; we keep it. `SkillRepository` is metadata-only and doesn't conflict.
