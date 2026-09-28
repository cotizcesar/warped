---
phase: 49-surface-removal
plan: 01
subsystem: chat, llm-providers
tags: [kotlin, compose, skills-removal, tool-loop-removal, single-turn-chat]

# Dependency graph
requires: []
provides:
  - Skills/tool-execution surface fully removed; local and remote chat are single-turn with no skill UI
  - Legacy Role.TOOL transcript rows render read-only via relocated helpers in domain/model/ToolTranscript.kt
affects: [49-02 (builds on post-removal transcript shape), phase-50-web-grounding]

# Actuals
actuals:
  tokens: 9200
  tasks: 1
  commits: 1

# Tech tracking
tech-stack:
  added: []
  patterns: [legacy read-only transcript helpers in domain.model, provider-side TOOL→user-text replay]

key-files:
  created:
    - app/src/main/java/com/warped/domain/model/ToolTranscript.kt
  modified:
    - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
    - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/ui/chat/ChatUiState.kt
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
    - app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt
    - app/src/main/java/com/warped/data/local/inference/LocalLlmProvider.kt
    - app/src/main/java/com/warped/data/local/inference/LiteRtLlmHelper.kt
    - app/src/main/java/com/warped/data/remote/provider/LmStudioHelper.kt
    - app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt
    - app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt
    - app/src/main/java/com/warped/data/remote/provider/AnthropicProvider.kt
    - app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt
    - app/src/main/java/com/warped/data/remote/provider/CustomProvider.kt
    - app/src/main/java/com/warped/data/remote/dto/LmStudioDtos.kt
    - app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt
    - app/src/main/java/com/warped/data/remote/dto/AnthropicDtos.kt
    - app/src/main/java/com/warped/data/remote/dto/StreamChunks.kt
    - app/src/main/java/com/warped/domain/prompt/PromptTemplateConfigs.kt

key-decisions:
  - "Legacy TOOL helpers relocated to domain/model/ToolTranscript.kt (parse/replay/display-name) instead of dying with ToolCopy.kt/data-skills — UI and providers share one read-only source"
  - "StreamToken.ToolStatus/ToolCompleted variants kept (PromptLab/benchmark collectors stay exhaustive); all producers removed, ViewModel ignores them explicitly"
  - "Response-side tool_calls DTO fields removed alongside request tools[] (single-turn only); capability flags (ModelCapabilities.tools, supportsFunctionCalling) kept as data"
  - "summarizeToolResult renamed to truncateToolSummary so the plan's literal Summarize grep-zero gate passes"

# Plan 49-01 Summary: Skills + tool-loop removal

## What was built
Removed the entire skills/tool execution surface — local @Tool skills, skill
chips + prefs, Summarize template, remote multi-round tool loop — so local and
remote chat are single-turn with no skill UI. Legacy Role.TOOL transcript rows
keep rendering read-only; no Room migration (EntityMappers.kt untouched).

## Deleted (26 files)
- UI: SkillChipsRow.kt, ToolCopy.kt
- data/skills (11): CalculatorSkill, CurrentTimeSkill, ExpressionEvaluator,
  JsonFormatterSkill, LocalToolExecutor, SkillDescriptors, SkillPreferences,
  SkillRepositoryImpl, ToolGating, ToolHistory, ToolText
- domain/skills (3): Skill, SkillRepository, ToolExecutor
- di/SkillsModule.kt, data/remote/provider/LmStudioToolLoop.kt
- Tests (8): 6 data/skills tests, LmStudioToolLoopTest, ChatViewModelToolTest

## Modified behavior
- ChatInputBar: OutlinedTextField is the top row; skill params removed
- ChatViewModel/ChatUiState/ChatScreen: skillEnabled, toolCallActive,
  activeToolError, showNoToolSupportNotice and producers removed; Done persists
  exactly one assistant message; setSkillEnabled + notice gates deleted
- LiteRTLmProvider: no tools in ConversationConfig, TOOL history maps
  assistant-adjacent (Message.model), wedge/degraded machinery removed
- LmStudioHelper: plain provider.chat path only; LmStudioProvider lost
  chatCompletionsWithTools + tool_call control markers
- OpenAI/Anthropic/Ollama/Custom: no tools[] sent, no tool-call emissions;
  legacy TOOL history replays as user text via shared toProviderText
- PromptTemplateConfigs: Summarize template deleted

## Verification
- Skills grep-zero gate: PASS (no matches)
- Summarize grep-zero gate: PASS (after truncateToolSummary rename)
- `./gradlew :app:assembleDebug`: BUILD SUCCESSFUL
- `:app:testDebugUnitTest` (full suite): BUILD SUCCESSFUL
- EntityMappers.kt: zero diff (name-based converter unchanged)
