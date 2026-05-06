# Phase 19: LM Studio Validation & MCP - Context

**Gathered:** 2026-05-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Validate LM Studio works end-to-end (chat, models, load/unload, download) and add MCP support for ephemeral servers and mcp.json plugin servers.

Already implemented: chat (SSE + non-streaming), model listing, load/unload, reasoning/stats parsing.
Needs: download endpoints, MCP integrations field, tool call SSE parsing, chat UI tool call rendering.
</domain>

<decisions>
## Implementation Decisions

### MCP Integration Format
- Two sealed subclasses for integrations: EphemeralMcp(serverLabel, serverUrl, allowedTools, headers) and PluginMcp(id, allowedTools)
- Tool calls displayed inline in chat as collapsible cards: tool name + arguments → result
- Download supports both catalog identifier and Hugging Face URL

### Validation & Integration
- Code-level verification only — no test suite exists
- Model management UI: add load/unload buttons in ModelsScreen for LM Studio endpoints
</decisions>

<code_context>
## Existing Code
- LMStudioProvider — chat(), listModels(), loadModel(), unloadModel(), testConnection() already complete
- LMStudioApi — chat, listModels, load, unload. Needs download endpoints
- LmStudioDtos — comprehensive SSE event types, model data, capabilities
- ChatViewModel — handles StreamToken.Delta/Done/Error with reasoning support
</code_context>

<specifics>
LMST-01 through LMST-08 requirements map to this phase. MCP ephemeral + plugin servers are key deliverable.
</specifics>

<deferred>
- LM Studio /api/v1/models/download via WorkManager for background downloads → future
- LM Studio structured output via grammar-based sampling → future
</deferred>
