---
status: complete
quick_id: 260504-lmi
description: "Fix LiteRT-LM conversation history: reuse conversation across messages"
commit: 3acfc1f
date: "2026-05-04"
---

# Quick Task 260504-lmi: Fix LiteRT-LM conversation history

**Completed:** 2026-05-04
**Commit:** 3acfc1f

## Summary

Fixed a bug where LiteRT-LM models (e.g., Deepseek R1) would only see the first message in a conversation, ignoring all prior context. The root cause was in `LiteRTLmProvider.sendContentsWithRetry()` — every `chat()` call was closing and recreating the `Conversation` object, passing full history via `ConversationConfig.initialMessages`. The LiteRT-LM engine does not properly honor `initialMessages` for multi-turn context.

## Changes

**`app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt`** (lines 146-155):
- Replaced unconditional conversation close-and-recreate with a reuse check
- If `activeConversation` is alive, reuse it (history is already in its internal state)
- Only create a new conversation with `initialMessages` when none exists or it died
- Error recovery path still nulls out the conversation before retrying

## Behavior

| Scenario | Before | After |
|----------|--------|-------|
| 1st message | New conversation with history | Same (no prior conversation) |
| 2nd message | New conversation with history (history ignored by engine) | Reuses conversation (history preserved) |
| 3rd+ message | New conversation with history (history ignored by engine) | Reuses conversation (history preserved) |
| Conversation dies | Recreate with history | Recreate with history (same) |
| Engine error + recovery | Closes, recovers, recreates | Nulls conversation, recovers, recreates |
