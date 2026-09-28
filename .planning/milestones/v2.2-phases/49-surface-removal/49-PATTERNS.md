# Phase 49: Surface Removal — Pattern Map

**Mapped:** 2026-09-28
**Sources:** 49-CONTEXT.md, 49-RESEARCH.md, 49-UI-SPEC.md

> NOTE (runtime): `gsd-pattern-mapper` subagent type unavailable in this runtime; mapping performed inline against the same contract.

## Files to delete (whole-file)

| File | Role | Analog / note |
|------|------|---------------|
| `data/skills/SkillDescriptors.kt` | skill definitions | — |
| `data/skills/SkillPreferences.kt` | DataStore prefs | — |
| `data/skills/SkillRepositoryImpl.kt` | repository impl | `ModelAllowlistRepository` stays as the retained repository-pattern example |
| `data/skills/ExpressionEvaluator.kt` | @Tool impl | — |
| `data/skills/ToolText.kt` | summary codec | `splitToolContent` in LiteRTLmProvider mirrors it; keep fallback only if ToolResultRow needs it |
| `data/skills/CalculatorSkill.kt` | @Tool impl | — |
| `data/skills/CurrentTimeSkill.kt` | @Tool impl | — |
| `data/skills/JsonFormatterSkill.kt` | @Tool impl | — |
| `data/skills/LocalToolExecutor.kt` | tool dispatcher | deleted with LmStudioToolLoop (remote counterpart) |
| `data/skills/ToolGating.kt` | routing truth | call sites in ChatViewModel + LiteRTLmProvider removed with it |
| `data/skills/ToolHistory.kt` | history helper | — |
| `domain/skills/Skill.kt` | domain model (+SkillIds) | — |
| `domain/skills/SkillRepository.kt` | domain interface | — |
| `domain/skills/ToolExecutor.kt` | domain interface | — |
| `di/SkillsModule.kt` | Hilt module | pattern: Hilt modules per feature (e.g. HuggingFaceModule — also deleted this phase) |
| `data/remote/provider/LmStudioToolLoop.kt` | remote tool rounds | — |
| `ui/chat/components/SkillChipsRow.kt` | skill chips UI | — |
| `ui/chat/components/ToolCopy.kt` | tool strings | copy contract moves to UI-SPEC Copywriting table |
| `data/remote/network/HuggingFaceAuthInterceptor.kt` | HF auth | generic AuthInterceptor (narrowed to remote endpoint keys) is the retained analog |
| `data/remote/api/HuggingFaceApi.kt` | HF search API | Retrofit interface pattern retained by remote providers |
| `data/remote/dto/HuggingFaceDtos.kt` | HF DTOs | kotlinx-serialization DTO pattern retained (LmStudioDtos minus tools[]) |
| `data/repository/HuggingFaceRepositoryImpl.kt` | HF repo | — |
| `domain/repository/HuggingFaceRepository.kt` | HF repo iface | — |
| `di/HuggingFaceModule.kt` | HF Hilt module | — |
| `ui/huggingface/HuggingFaceScreen.kt` | search screen | REPURPOSE → static catalog screen (keeps NavGraph route stable); visual analog: `ModelListCard` pattern (storage icon + title + subtitle + badges + description + end-aligned OutlinedButton) |
| `ui/huggingface/HuggingFaceViewModel.kt` | search VM | replaced by allowlist read via ModelAllowlistRepository (no ViewModel needed if synchronous asset parse; planner discretion) |
| `ui/huggingface/HuggingFaceUiState.kt` | search state | — |

## Files to modify

| File | Change | Must-read before editing |
|------|--------|--------------------------|
| `ui/chat/components/ChatInputBar.kt` | delete SkillChipsRow call site (~111-118) + Spacer + params | self |
| `ui/chat/ChatViewModel.kt` | remove skill/tool plumbing (~10 sites), transcript tool flags | self + ChatUiState + ChatScreen call sites |
| `ui/chat/ChatUiState.kt` | remove skillEnabled/tool flags | ChatViewModel consumers |
| `ui/chat/ChatScreen.kt` | update ChatInputBar call (drop skill args) | ChatInputBar signature |
| `ui/chat/components/MessageBubble.kt` | keep TOOL branch (62-65); delete ToolErrorRow + NoToolSupportNotice | self |
| `data/local/inference/LiteRTLmProvider.kt` | drop TOOL branch (:197), tool rounds (:371), ToolGating (:433); legacy TOOL → assistant-adjacent | LocalLlmProvider.buildPrompt (retained pattern) |
| `data/local/inference/LocalLlmProvider.kt` | collapse TOOL case to assistant line (keep shape) | self |
| `data/local/db/entity/EntityMappers.kt` | NO CHANGE (name-based converter keeps working) | — verify only |
| `di/InferenceModule.kt` | remove skills param from runInference | all runInference call sites |
| `domain/prompt/PromptTemplateConfigs.kt` | delete Summarize entry | grep Summarize consumers |
| `data/remote/provider/LMStudioProvider.kt`, `OpenAIProvider.kt`, `AnthropicProvider.kt`, `OllamaProvider.kt`, `CustomProvider.kt`, `LmStudioHelper.kt`, `data/remote/dto/LmStudioDtos.kt` | drop tools[] fields + multi-round handling | self + delete LmStudioToolLoop |
| `ui/settings/SettingsScreen.kt` | delete HF section (119-162) | self |
| `ui/settings/SettingsViewModel.kt` + SettingsUiState | delete HF state/methods | SettingsScreen consumers |
| `data/local/security/ApiKeyStore.kt` | delete HF key + 3 methods | SettingsViewModel (only consumer) |
| `data/local/download/ModelDownloadWorker.kt` | strip token (107-112), rewrite 401 copy (:134) | self |
| `ui/models/ModelsScreen.kt` | wizard relabel (144-154, Search→Download icon + copy) | UI-SPEC §4 |
| `ui/navigation/NavGraph.kt` + `Screen.kt` | rewire HF route → catalog (label/icon) | HuggingFaceScreen repurpose |
| `res/values/strings.xml` + `values-es/strings.xml` | delete search/HF keys | grep each key before deletion |
| `app/proguard-rules.pro` | narrow skills keeps; keep LiteRT-LM intact | full file |
| `scripts/audit-dependencies.sh` | verify-only (must stay green) | — |

## Retained-as-is (verify, don't touch)
- `EntityMappers.kt:24` name-based Role mapping
- `ToolResultRow` legacy renderer styling (Thinking-panel, #545450 italic, 16dp indent, chevron)
- `ModelAllowlistRepository` + `assets/model_allowlist.json`
- `ModelDownloadManager`/`ModelDownloadWorker` download mechanics (minus auth)
- WarpedAlertDialog destructive confirmations; WarpedTypography/Shapes/Color tokens
