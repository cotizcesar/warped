# Phase 49: Surface Removal — Research

**Researched:** 2026-09-28
**Phase:** 49-surface-removal (DEL-01..DEL-06)
**Mode:** removal-only, coarse granularity

> NOTE (runtime): `gsd-phase-researcher` / `gsd-planner` / `gsd-plan-checker` subagent types are not available in this runtime (OpenCode subagent, no Agent() tool). Research, pattern mapping, planning, and verification below were performed inline against the same contracts (CONTEXT.md + REQUIREMENTS.md + STATE.md + codebase evidence).

## 1. What must disappear (verified by grep)

### Skills execution surface (DEL-01)
- `data/skills/`: SkillDescriptors.kt, SkillPreferences.kt, SkillRepositoryImpl.kt, ExpressionEvaluator.kt, ToolText.kt, CalculatorSkill.kt, CurrentTimeSkill.kt, JsonFormatterSkill.kt, LocalToolExecutor.kt, ToolGating.kt, ToolHistory.kt
- `domain/skills/`: Skill.kt, SkillRepository.kt, ToolExecutor.kt (+ SkillIds referenced by ChatViewModel:23)
- `di/SkillsModule.kt` — delete; remove `skills` param from `InferenceModule.runInference` signature
- `LiteRTLmProvider.kt:197` TOOL branch (Message.tool/ToolResponse wiring), `:371` MAX_TOOL_ROUNDS, `:433` ToolGating.decide pre-filter; `LocalLlmProvider.kt:40` TOOL special-case → collapse to assistant rendering (local path is deprecated stub, keep shape)
- ChatViewModel tool-write sites (~lines 514, 597, 1140 per CONTEXT — verified clusters at 273 StreamToken.ToolCompleted, 289 turnToolRecords, 376-409 NoSupportFallback gates, 235-248 setSkillEnabled, 218-222 enabledMap collection)

### Skills UI/prefs surface (DEL-02)
- `ui/chat/components/SkillChipsRow.kt` (file delete) + `ChatInputBar.kt:110-118` call site + 8dp Spacer + `skillEnabled`/`onToggleSkill` params
- `ToolCopy.kt`, `ToolErrorRow`, `NoToolSupportNotice` composables (delete; UI-SPEC §2)
- `ChatUiState` skill fields, `SkillPreferences` DataStore keys
- `PromptTemplateConfigs.kt` Summarize entry + any Summarize persona/template references (delete with skills surface per CONTEXT)
- Grep-zero gate: `Skill|ToolGating|ToolHistory|ToolText|ExpressionEvaluator|Calculator|CurrentTime|JsonFormatter|SkillChipsRow|NoToolSupportNotice|ToolErrorRow|Summarize` must return zero in `app/src` (excluding legacy `ToolResultRow` renderer + `Role.TOOL` + `splitToolContent` fallback, which stay)

### Remote tool loop (DEL-03)
- `data/remote/provider/LmStudioToolLoop.kt` — delete
- `LmStudioHelper.kt`, `LmStudioDtos.kt`, `LMStudioProvider.kt`, `OpenAIProvider.kt`, `AnthropicProvider.kt`, `OllamaProvider.kt`, `CustomProvider.kt` — drop `tools[]` request/response fields and multi-round tool handling; keep single-turn chat/completions
- `MessageBubble.kt:62-65` Role.TOOL early-return → KEEP as sole legacy renderer (read-only ToolResultRow, collapsed, muted #545450 italic, 16dp indent, chevron via formatToolTranscriptA11y)
- `EntityMappers.kt:24` name-based Role mapping — KEEP as-is, no Room migration (unrecognized → ASSISTANT-adjacent)

### HF token (DEL-04)
- `SettingsScreen.kt:119-162` entire "Hugging Face" section (header + card + OutlinedTextField + Save + Remove token) — delete
- `SettingsViewModel.kt:44,174,176,191` HF state + `updateHfToken/saveHfToken/deleteHfToken`; `SettingsUiState.hasHfToken/hfToken`
- `ApiKeyStore.kt:11,36,43,47` HF_TOKEN_KEY + store/get/deleteHuggingFaceToken (remove HF entry only; remote endpoint keys stay)
- `HuggingFaceAuthInterceptor.kt` — delete file; narrow generic AuthInterceptor to remote endpoint keys only
- `ModelDownloadWorker.kt:107-112` token query + `?token=` URL suffix — delete; `:134` 401 copy → "Download failed (unauthorized). The server rejected the request — check your connection and try again." (no token/Settings/HF reference)

### Search → static catalog (DEL-05)
- `HuggingFaceScreen.kt` search surface — delete file (or repurpose as static catalog screen per UI-SPEC §4; planner discretion — recommend repurpose to keep NavGraph route stable, retitled "Model catalog")
- Delete: search OutlinedTextField + supporting text, 400ms debounce, SearchResults list, gated-model error branch, "Open on Hugging Face" button
- Delete: `HuggingFaceApi.kt` search endpoints, `HuggingFaceDtos.kt`, `HuggingFaceRepository.kt` + Impl, `HuggingFaceModule.kt`, `HuggingFaceViewModel.kt`, `HuggingFaceUiState.kt`
- Static catalog: read `assets/model_allowlist.json` (3 entries: gemma-4-E2B-it, gemma-3n-E2B-it-int4, gemma-3n-E4B-it-int4; all `supportsFunctionCalling:false`) via existing `ModelAllowlistRepository`; one ModelListCard-pattern card per entry (displayName title, modelFile subtitle, formatFileSize, vision/audio badges only)
- `ModelsScreen.kt:144-154` wizard entry → title "Download model", body "Choose from the built-in catalog", icon `Icons.Filled.Download`, navigates to static catalog
- strings.xml: delete `search_models`, `search_models_hint`, `search_minimum_length`(if present), `hugging_face_suggestions`, `download_from_hf*`, `hugging_face` (verify each key unreferenced before deletion); keep `type_message`
- `Screen.kt:35,67,85,103` HuggingFace route — keep route object (repurposed to catalog) or rename; update label/icon (Search → Download); NavGraph.kt:43,324,340,344-345 rewire to catalog screen

### Release posture (DEL-06)
- R8: narrow skills keeps; LiteRT-LM `com.google.ai.edge.litertlm.**` keeps + 0.17.x tool entry-point keeps (ToolSet/OpenApiTool/Tool/ToolParam/ReflectionTool/ToolKt/Capabilities) stay INTACT — LiteRTLmProvider stops calling tool APIs but the SDK keeps remain (no crash risk, negligible size cost vs. native-crash risk)
- `scripts/audit-dependencies.sh` must stay green (zero new deps; deletions only)
- `assembleRelease` + smoke (launch → load model → local + remote turn) OK

## 2. Key decisions locked (from CONTEXT + UI-SPEC)
- No Room migration (name-based converter keeps working)
- Summarize persona deleted with skills surface
- Local management (view/delete/progress/cancel) retained via ModelDownloadManager/Worker minus auth
- All destructive confirmations keep WarpedAlertDialog pattern; no dialog downgraded to snackbar
- Chat input card starts 8dp+chip-height shorter; no replacement UI
- Icon-only actions keep contentDescriptions (Send/Stop/Add image/Menu)

## 3. Risks / landmines
- ChatViewModel is the largest edit (tool plumbing at ~10 sites + transcript flags `toolCallActive/activeToolError/showNoToolSupportNotice`). Remove flags + all producers; keep `isStreaming`.
- LiteRTLmProvider TOOL branch removal must keep history sanitization compiling (sanitizedMessages pipeline) — TOOL messages from legacy history now map assistant-adjacent like LocalLlmProvider.
- `splitToolContent` helper: keep only if legacy ToolResultRow summary path needs it; else delete.
- Hilt graph: deleting SkillsModule + HuggingFaceModule requires removing all @Inject consumers; build will surface stragglers — `assembleDebug` is the backstop.
- NavGraph: two call sites (324, 340) navigate to Screen.HuggingFace; rewire both to catalog.
- values-es/strings.xml mirrors values/strings.xml — delete the same keys there.
- Phase 50 builds on post-removal transcript shape — do not invent new transcript fields.

## 4. Verification approach
- `grep -rn` zero-gates for skills/HF/search tokens (explicit lists per plan)
- `:app:assembleDebug` + `:app:assembleRelease` compile gates
- `scripts/audit-dependencies.sh` green
- Manual smoke: launch → load allowlisted model → local turn → remote turn → legacy chat with TOOL rows opens readably
