---
phase: 56-local-agentic-loop
reviewed: 2026-09-29T03:00:00Z
depth: standard
files_reviewed: 22
files_reviewed_list:
  - app/src/main/java/com/warped/data/agentic/LocalToolLoop.kt
  - app/src/main/java/com/warped/data/agentic/WebSearchToolSet.kt
  - app/src/main/java/com/warped/data/agentic/WebFetchToolSet.kt
  - app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt
  - app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt
  - app/src/main/java/com/warped/data/repository/ModelAllowlistRepository.kt
  - app/src/main/assets/model_allowlist.json
  - app/src/main/java/com/warped/domain/model/ChatRequest.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/ui/chat/ChatUiState.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/proguard-rules.pro
  - app/src/test/java/com/warped/data/agentic/LocalToolLoopTest.kt
  - app/src/test/java/com/warped/data/agentic/ToolSetSchemaTest.kt
  - app/src/test/java/com/warped/data/local/inference/LiteRTLmLoopTest.kt
  - app/src/test/java/com/warped/data/remote/provider/LmStudioCancelTest.kt
  - app/src/test/java/com/warped/data/repository/ModelAllowlistTest.kt
  - app/src/test/java/com/warped/ui/chat/ChatCancellationTest.kt
  - app/src/test/java/com/warped/ui/chat/ChatGroundingRetryTest.kt
  - app/src/test/java/com/warped/ui/chat/ChatGroundingToggleTest.kt
  - app/src/test/java/com/warped/ui/chat/ChatSubStateTest.kt
  - app/src/test/java/com/warped/ui/settings/SettingsTavilyTest.kt
findings:
  critical: 0
  warning: 1
  info: 3
  total: 4
status: fixed
---

# Phase 56: Code Review Report

**Reviewed:** 2026-09-29T03:00:00Z
**Depth:** standard
**Files Reviewed:** 22
**Status:** issues_found

## Summary

Reviewed the full Phase 56 diff (56-01 ToolSets + `LocalToolLoop` policy + allowlist/KV flags; 56-02 provider loop + transient rows + VM grounding skip) against the phase's key threat areas. All eight threat gates hold: exactly 2 tool schemas with exact-name dispatch, no `runBlocking` on the agentic path (only pre-existing `LmStudioHelper` usage), `ensureActive` + CE-rethrow + `fetcher.cancel()` Stop propagation, per-call offline gates before any socket, unconditional KV-channel filter, `Content.Text`-only Delta extraction with thought routed to `Done.reasoning`, fused-block-only tool results, and a 3-input AND capability gate shared by provider and VM. One Warning (stale transient tool row on the turn-exception path) and three Info hardening notes. No Critical issues — no security vulnerability, crash, or data-loss risk found.

## Warnings

### WR-01: Turn-exception path leaves a stale transient tool row

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:858-865`
**Issue:** Every terminal path clears `toolCallActive` (Done, Error token, silent turn, Stop, new send) — except the generic `catch (e: Exception)` around the `runInference` collection, which resets only `isGenerating`. If the helper flow throws mid-turn after `ToolStatus` tokens were already collected (e.g. transport failure after a tool executed), the `Using web_search: …` chip stays mounted indefinitely until the next send/Stop.
**Fix:**
```kotlin
} catch (e: Exception) {
    updateTranscript {
        it.copy(
            error = ChatError.Network(e.message ?: "Unknown error"),
            isStreaming = false
        )
    }
    updateInput { it.copy(isGenerating = false, toolCallActive = null) }
}
```
(The `finally` block at ~866 is an equally good single place to clear it, since Done/Error/silent already clear inline idempotently.)

## Info

### IN-01: Engine-error recovery nulls the dead conversation without closing it

**File:** `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt:571-576`
**Issue:** On engine-error retry the code sets `activeConversation = null` then calls `recoverEngine()` → `resetConversation()`, which can now close nothing — the dead native handle is never `close()`d (native handle leak per failed turn). Acknowledged as intentionally matching the pre-existing plain-path wart at `sendContentsWithRetry` (~675-681), so not introduced here, but the agentic path doubles the surface that copies it.
**Fix:** Close before nulling, mirroring `acquireConversation`:
```kotlin
try { activeConversation?.close() } catch (e: Exception) { Timber.w(e, "LiteRTLm: agentic acquire.close() failed") }
activeConversation = null
```
ideally factored into one `dropConversationLocked()` helper shared by both retry paths.

### IN-02: ToolStatus row emits before arg validation, flashing on no-op calls

**File:** `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt:421-432`
**Issue:** `runToolLoop` emits `ToolStatus(display)` before `executeToolCall` runs `validateArgs`, so blank-query, malformed-URL, and unknown-name calls (which never execute and burn budget by design) still flash a transient `Using …` row for one frame. Cosmetic only — no socket, no credit burn — but the row promises execution that never happens.
**Fix:** Move the `validateArgs` check ahead of the emit (return the short-circuit string without posting status), or emit status inside `executeToolCall` only on the execute path.

### IN-03: Raw exception text and uncapped tool names flow into model context

**File:** `app/src/main/java/com/warped/data/agentic/LocalToolLoop.kt:137-138,208-211`
**Issue:** `unknownToolMessage(name)` interpolates the engine-authored tool name with no length cap, and `toolFailureMessage` forwards repository exception text (capped at 300 chars, single-lined — good) that could in principle echo paths or request details into model context and from there into a persisted answer. Risk is low (local model, user-owned key), but the trust boundary deserves the same cap treatment as `MAX_URL_IN_ERROR`.
**Fix:**
```kotlin
fun unknownToolMessage(name: String): String =
    "Unknown tool \"${name.take(MAX_FAILURE_CHARS)}\". Available tools: $TOOL_WEB_SEARCH, $TOOL_WEB_FETCH."
```
and strip anything resembling a credential/host detail from exception text before feeding it back (or keep a static allowlist of safe failure strings).

---

_Reviewed: 2026-09-29T03:00:00Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
