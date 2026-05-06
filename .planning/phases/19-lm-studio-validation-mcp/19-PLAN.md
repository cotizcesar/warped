# Phase 19: LM Studio Validation & MCP - Plan

**Planned:** 2026-05-06
**Status:** Complete

## Tasks

### T1: DTOs
- LmStudioDownloadRequest, LmStudioDownloadResponse, LmStudioDownloadStatusResponse
- LmStudioIntegration (flat data class for ephemeral + plugin MCP)
- LmStudioToolCall (id, name, arguments, result, error)
- LmStudioChatRequest.integrations field
- LmStudioSseEvent.toolCall field
- Files: `data/remote/dto/LmStudioDtos.kt`

### T2: LmStudioApi endpoints
- POST api/v1/models/download, GET api/v1/models/download/status/{jobId}
- File: `data/remote/api/LmStudioApi.kt`

### T3: LMStudioProvider updates
- chat() accepts integrations parameter (MCP support)
- Tool call SSE parsing: tool_call.start, tool_call.arguments, tool_call.success, tool_call.failure
- downloadModel(), downloadStatus() methods
- File: `data/remote/provider/LMStudioProvider.kt`

### T4: Chat UI tool call rendering
- Tool calls rendered inline: [tool:name](args) → result / ✗ error
- Existing StreamToken.Delta format used for tool call text
