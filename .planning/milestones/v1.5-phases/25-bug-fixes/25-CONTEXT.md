# Phase 25: Bug Fixes - Context

**Gathered:** 2026-05-09
**Status:** Ready for planning
**Mode:** Auto-generated (well-defined bugs, discuss skipped)

<domain>
## Phase Boundary

Fix 4 critical chat bugs — code block rendering during streaming, model reload on re-entry, active conversation highlighting, and ghost messages after stop/delete.

All bugs are behavioral fixes within existing ChatViewModel, ChatScreen, and related components. No new features.
</domain>

<decisions>
## Implementation Decisions

### the agent's Discretion
All implementation choices are at the agent's discretion. The bugs are:
- **BUG-01**: MarkdownText handles unclosed code fences during streaming without swallowing content; partial code blocks displayed as they stream
- **BUG-02**: selectConversation() does not unload engine when the already-loaded model matches the conversation's model; loading indicator shown when reload is actually needed
- **BUG-03**: NavGraph activeConversationId synchronized when chat loads via chat/{conversationId} route; conversation highlighted in drawer
- **BUG-04**: stopGeneration() clears streamingContent and streamingReasoning; deleteMessage(id) added at MessageDao, ChatRepository, and ChatViewModel layers; deleted message immediately removed from UI

Follow existing codebase conventions and fix minimal code needed to resolve each bug.
</decisions>

<code_context>
## Existing Code Insights

### Primary files
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` — Core chat logic, streaming, model loading
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` — Chat UI with markdown rendering
- `app/src/main/java/com/warped/ui/chat/components/MarkdownText.kt` — Markdown/code block renderer
- `app/src/main/java/com/warped/ui/navigation/NavGraph.kt` — Navigation with activeConversationId
- `app/src/main/java/com/warped/data/local/db/dao/MessageDao.kt` — Room DAO for messages
- `app/src/main/java/com/warped/domain/repository/ChatRepository.kt` — Chat domain interface

### Phase 23 context
EngineManager simplified to LiteRT-LM only. No GGUF/llama.cpp. ChatViewModel no longer depends on LlamaEngine.
</code_context>

<specifics>
## Specific Ideas

No specific requirements — follow bug descriptions from REQUIREMENTS.md:
- BUG-01 through BUG-04
</specifics>

<deferred>
## Deferred Ideas

None.
</deferred>
