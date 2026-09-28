# Phase 49: Surface Removal - Context

**Gathered:** 2026-09-28
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous, user accepted all recommended)

<domain>
## Phase Boundary

Users chat and download models with no skills, HF token, or search surface — smaller app, same core capability via static catalog. Removals land first so Phase 50 grounding builds on post-removal transcript shape. Covers DEL-01..DEL-06.
</domain>

<decisions>
## Implementation Decisions

### Legacy Transcript & Tool Surface
- Role.TOOL rows render as read-only assistant-style bubbles (MessageBubble existing branch kept); no Room migration, TypeConverter name-based (EntityMappers.kt:24) keeps working
- Summarize persona/template deleted with skills surface (DEL-02)
- Local prompt builders (LocalLlmProvider.kt:40, LiteRTLmProvider.kt:197) and remote providers drop TOOL special-casing except legacy render path; LmStudioToolLoop.kt + LocalToolExecutor deleted
- SkillChipsRow removed from ChatInputBar; ChatViewModel tool-write sites (lines 514,597,1140) removed

### Catalog & Download UX
- Static allowlist only (model_allowlist.json); no search field, no HuggingFaceApi search endpoints, no gated-model filtering
- Download progress/cancel retained via ModelDownloadManager/Worker; local management (view, delete) retained
- 401 message in ModelDownloadWorker updated (no token reference); direct download without Authorization

### Token & Security Cleanup + Release Posture
- Delete HF token field in Settings + SettingsViewModel.storeHuggingFaceToken call; remove ApiKeyStore.storeHuggingFaceToken/clear methods; delete HuggingFaceAuthInterceptor; narrow AuthInterceptor to remote endpoint keys only
- Delete SkillsModule DI, SkillRepository/SkillPreferences/SkillDescriptors/Calculator/CurrentTime/JsonFormatter/ToolGating/ToolHistory/ToolText/ExpressionEvaluator; remove InferenceModule skills param from runInference signature
- R8 keeps narrowed (LiteRT-LM SDK keeps intact); scripts/audit-dependencies.sh green; assembleRelease + local/remote chat smoke OK

### the agent's Discretion
- Exact grep-zero file list and R8 keep narrowing at planner discretion following DEL-02/DEL-06 gates
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- ModelAllowlistRepository + assets/model_allowlist.json (static catalog source of truth)
- MessageBubble.kt:62 TOOL branch (keep as legacy renderer)
- EntityMappers.kt:24 name-based Role mapping (no migration needed)
- ModelDownloadManager/Worker (progress/cancel, strip auth)

### Established Patterns
- Hilt modules per feature (SkillsModule to delete); Repository pattern at domain/data boundary
- EncryptedSharedPreferences via ApiKeyStore (remove HF entry only)
- WorkManager foreground downloads with Range/checkpoints

### Integration Points
- ChatInputBar.kt:111 SkillChipsRow call site; ChatViewModel tool writes; Settings HF field; HuggingFaceApi/HuggingFaceAuthInterceptor; InferenceModule.runInference skills param
</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches. Phase 49 planning must check Room Role TypeConverter (name vs ordinal) and decide Summarize persona fate up front (decided: keep converter, delete Summarize).
</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope
</deferred>
