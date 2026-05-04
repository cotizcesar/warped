# Quick Task 260504-lmi: Fix LiteRT-LM conversation history

**Goal:** Fix bug where LiteRT-LM (Deepseek R1) only sees the first message in a conversation, ignoring all prior context.

**Root Cause:** `LiteRTLmProvider.sendContentsWithRetry()` closes and recreates the `Conversation` on every `chat()` call. The conversation history is passed via `ConversationConfig.initialMessages`, but the engine doesn't properly honor `initialMessages` for multi-turn context — each message is treated as the first message.

**Fix:** Keep the `Conversation` object alive across `chat()` calls. When the conversation is already alive, reuse it. Only create a new one with `initialMessages` history when no conversation exists or it died.

## Tasks

### Task 1: Fix conversation reuse in LiteRTLmProvider
- **File:** `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt`
- **Action:** Modify `sendContentsWithRetry()` to reuse the existing `activeConversation` when it's alive instead of closing and recreating it every time. Remove the forced conversation close at the start of `sendContentsWithRetry`. Only close and recreate when the conversation is dead or doesn't exist.
- **Verify:** In a multi-turn chat with a LiteRT-LM model, each subsequent user message should be answered in context of prior messages.
