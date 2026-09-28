---
phase: 47-real-tool-execution
reviewed: 2026-09-28T00:00:00Z
depth: standard
files_reviewed: 32
files_reviewed_list:
  - app/src/main/java/com/warped/domain/skills/Skill.kt
  - app/src/main/java/com/warped/domain/skills/SkillRepository.kt
  - app/src/main/java/com/warped/domain/skills/ToolExecutor.kt
  - app/src/main/java/com/warped/domain/model/Role.kt
  - app/src/main/java/com/warped/domain/model/StreamToken.kt
  - app/src/main/java/com/warped/data/skills/SkillDescriptors.kt
  - app/src/main/java/com/warped/data/skills/SkillPreferences.kt
  - app/src/main/java/com/warped/data/skills/SkillRepositoryImpl.kt
  - app/src/main/java/com/warped/data/skills/ToolText.kt
  - app/src/main/java/com/warped/data/skills/ExpressionEvaluator.kt
  - app/src/main/java/com/warped/data/skills/CalculatorSkill.kt
  - app/src/main/java/com/warped/data/skills/CurrentTimeSkill.kt
  - app/src/main/java/com/warped/data/skills/JsonFormatterSkill.kt
  - app/src/main/java/com/warped/data/skills/LocalToolExecutor.kt
  - app/src/main/java/com/warped/data/skills/ToolGating.kt
  - app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt
  - app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt
  - app/src/main/java/com/warped/data/local/inference/LiteRtLlmHelper.kt
  - app/src/main/java/com/warped/data/local/inference/LocalLlmProvider.kt
  - app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt
  - app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/LmStudioHelper.kt
  - app/src/main/java/com/warped/data/remote/provider/LmStudioToolLoop.kt
  - app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/AnthropicProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/CustomProvider.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/ui/chat/components/ToolCopy.kt
  - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
  - app/src/main/java/com/warped/ui/chat/components/SkillChipsRow.kt
  - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
  - app/src/main/java/com/warped/di/SkillsModule.kt
findings:
  critical: 1
  warning: 9
  info: 7
  total: 17
status: issues_found
---

# Phase 47: Code Review Report

**Reviewed:** 2026-09-28T00:00:00Z
**Depth:** standard
**Files Reviewed:** 32
**Status:** issues_found

## Summary

Reviewed all three Phase 47 plans: 47-01 Skills tracer surface (`Skill`, `SkillRepository`, `SkillDescriptors`, `SkillPreferences`, `Role.TOOL`, chips/copy-deck UI), 47-02 local execution (`ExpressionEvaluator`, pure tool bodies, `LocalToolExecutor`, `@Tool` ToolSets, `ToolGating`, `ConversationConfig` wiring, `StreamToken.ToolStatus`, wedge-degrade), and 47-03 remote loop (`LmStudioToolLoop`, `OpenAiMessage` extension, `StreamToken.ToolCompleted`, `LmStudioHelper` routing, ViewModel persistence).

The core trust boundary is solid: charset allowlist + 200-char cap on calculator input, `ZoneId`-validated timezones, 64KB JSON cap, allowlisted dispatch with `ParamSpec` schema validation, dual-site sanitize/truncate, `>=` loop cap, per-round `Call` reporting with `ensureActive` checkpoints, and crash-safe `toRoleSafe()` persistence. One **critical** defect remains: `Role.TOOL` rows resume as malformed payloads on every non-LM-Studio provider (raw `<toolId>\n<summary>` encoding leaks, and Anthropic receives an API-invalid `role:"tool"`). Nine warnings cover loop-cancel/audit gaps, a never-reset degrade flag, a `StackOverflowError` hole in the never-throw contract, and cross-provider notice/parity gaps.

## Critical Issues

### CR-01: Role.TOOL resume sends malformed payloads on OpenAI / Anthropic / Ollama / Custom providers

**File:** `app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt:70-76`, `AnthropicProvider.kt:61-68`, `OllamaProvider.kt:56-62`, `CustomProvider.kt:56-61`
**Issue:** Phase 47 persists `role=tool` rows (encoding `"<toolId>\n<summary>"`) that enter the shared `ChatRequest.messages` history. Only three consumers handle them: `LiteRTLmProvider` (dedicated `Message.tool` mapping), `LMStudioProvider.chat` (plain-text `Used {Display}:` replay), and `LmStudioToolLoop.initialMessages` (`role:tool` + `tool_call_id`). The four other providers blindly map `it.role.name.lowercase()` with raw content:
- **Anthropic** (`AnthropicProvider.kt:67`): emits `role:"tool"`, which the Anthropic Messages API rejects (only `user`/`assistant` are valid) → any resumed conversation containing a tool row fails with an API 400.
- **OpenAI / Custom** (`OpenAIProvider.kt:75`, `CustomProvider.kt:60`): emits `role:"tool"` with the raw `"<toolId>\n<summary>"` string as content and **no** `tool_call_id` → unpaired tool message with leaked internal encoding; strict servers reject tool messages without a matching `tool_calls` echo.
- **Ollama** (`OllamaProvider.kt:61`): same raw-encoding leak into `OllamaMessage`.
Repro: enable skills → run a tool turn via LM Studio (TOOL rows persist) → switch/resume that conversation on an OpenAI or Anthropic endpoint → next send transmits the corrupt history. `Role.TOOL` migration is therefore unsafe outside the LM Studio + LiteRT paths.
**Fix:**
```kotlin
// Centralize once, e.g. in data/skills/ToolHistory.kt (data layer, no UI import):
fun ChatMessage.toProviderText(): Pair<String, String> = when (role) {
    Role.TOOL -> {
        val idx = content.indexOf('\n')
        val toolId = if (idx < 0) content else content.substring(0, idx)
        val summary = if (idx < 0) "" else content.substring(idx + 1)
        "tool" to "Used ${toolDisplayNameCapitalized(toolId)}: $summary"
    }
    else -> role.name.lowercase() to content
}
// AnthropicProvider: filter TOOL rows to user-adjacent text (Anthropic has no tool role)
role = if (it.role == Role.TOOL) "user" else it.role.name.lowercase()
// OpenAI/Custom/Ollama: use toProviderText() so the raw "<id>\n<summary>" encoding never hits the wire.
```

## Warnings

### WR-01: `toolsDegraded` wedge flag is never reset — local tools stay dead for the process lifetime

**File:** `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt:87-88,231-232`
**Issue:** Once a single `tool_response`/`template` `LiteRtLmJniException` wedge fires, `toolsDegraded = true` permanently forces gating CLOSED. Nothing clears it: not `resetConversation()` (called on skill toggle, `LiteRtLlmHelper.initialize`, engine recovery), not model switch, not `newConversation()`. A user who hits the wedge on Qwen3, then switches to a verified tool-supporting model, silently never gets engine tools back until app restart. The "session" in "session fallback" has no boundary.
**Fix:**
```kotlin
fun resetConversation() {
    synchronized(this) {
        // ...
        activeConversation = null
        activeConversationConfig = null
    }
}
// Clear the wedge fallback whenever the model identity changes or a fresh
// conversation starts — the wedge verdict belongs to (model, engine) pair:
fun clearToolsDegraded() { toolsDegraded = false }
// call from LiteRtLlmHelper.initialize() after switchToLiteRT, and/or ChatViewModel.newConversation()
```

### WR-02: Completed tool records are silently dropped on Error and cancel turns

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:419-431,499-508`
**Issue:** `toolRecords` accumulate on `ToolCompleted` but are only persisted in the `Done` branch (lines 445-470). If the turn ends in `StreamToken.Error` (e.g. transport failure after 3 successful tool rounds, or the silent-fallback POST failing), or the user hits Stop (`CancellationException` rethrows at line 515), all collected `ToolCompleted` rows vanish — no transcript rows, no audit trail, and the transient `activeToolError` from line 422 is overwritten by the generic `ChatError.Network`. The 47-03 summary claims "silent turns still persist rows for auditability," but error/cancel turns do not.
**Fix:**
```kotlin
is StreamToken.Error -> {
    // Persist any completed tool rows before surfacing the error.
    val toolMessages = toolRecords.map { record ->
        ChatMessage(role = Role.TOOL, content = toolResultContent(record.toolId, record.summary))
    }
    if (toolMessages.isNotEmpty()) {
        _uiState.update { it.copy(messages = it.messages + toolMessages) }
        for (toolMessage in toolMessages) chatRepository.saveMessage(conversationId, toolMessage)
    }
    _uiState.update { it.copy(error = ChatError.Network(token.message), ...) }
}
```

### WR-03: Every throttled Delta flush clears `toolCallActive`, racing `ToolStatus` mid-loop

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:391-401`
**Issue:** The 50ms content-flush block unconditionally sets `toolCallActive = null` ("clear tool indicator once content arrives"). During a multi-round remote loop, content Deltas from round N arrive while tools are still executing for round N+1, so the `Using {display}…` row flickers or disappears mid-loop until the next `ToolStatus(name)` re-sets it. Status clearing should be owned exclusively by `ToolStatus(null)` / `Done` (lines 406-412, 433), not by content arrival.
**Fix:**
```kotlin
_uiState.update {
    it.copy(
        streamingContent = cleanContent,
        streamingReasoning = reasoning,
        // DO NOT touch toolCallActive here — ToolStatus(null)/Done own clearing.
    )
}
```

### WR-04: `StackOverflowError` from deeply-nested JSON escapes the never-throw trust boundary

**File:** `app/src/main/java/com/warped/data/skills/JsonFormatterSkill.kt:31-35`, `app/src/main/java/com/warped/data/remote/provider/LmStudioToolLoop.kt:336,385`
**Issue:** HARD-02 promises tool bodies "never throw out," enforced by `catch (e: Exception)` in `formatJson`, the `@Tool` wrappers, `LocalToolExecutor.execute`, and the loop's malformed-args validation. But `StackOverflowError` is an `Error`, not an `Exception`: a deeply-nested payload (`[[[[…]]]]`, trivially under the 64KB cap) recurses in `Json.parseToJsonElement` and escapes every handler — killing the `@Tool` JNI call mid-conversation locally, or propagating out of `LmStudioToolLoop.doRound` (whose `catch (e: Exception)` at line 152 also misses it) into the ViewModel collector, where `catch (e: Exception)` at `ChatViewModel.kt:516` misses it again → `isStreaming` stuck true, spinner forever. Model-controlled `tool_calls[].arguments` make this remotely triggerable by a malicious/custom server.
**Fix:**
```kotlin
// formatJson: pre-reject deep nesting before parsing (cheap, no exception tax):
private const val JSON_MAX_DEPTH = 100
fun formatJson(jsonText: String): ToolResult {
    if (jsonText.length > JSON_MAX_CHARS) return ToolResult.Failure("JSON too large")
    var depth = 0; var maxDepth = 0
    for (c in jsonText) {
        if (c == '{' || c == '[') { depth++; if (depth > maxDepth) maxDepth = depth }
        else if (c == '}' || c == ']') depth--
        if (maxDepth > JSON_MAX_DEPTH) return ToolResult.Failure("JSON too deeply nested")
    }
    // ... existing parse
}
// LmStudioToolLoop arg-validation sites: catch Throwable for the pure-validation
// parse (it never touches resources, so catching SOE there is safe):
val parsed = runCatching { json.parseToJsonElement(callItem.argsJson) }.getOrNull()
```

### WR-05: No-support notice missing on OpenAI / Ollama / Custom / Anthropic endpoints

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:331-359`
**Issue:** The once-per-turn `showNoToolSupportNotice` fires only for `LITE_RT_LM` and `LM_STUDIO`. Tool execution is wired *only* to those two backends, so enabling skills on an OpenAI/Ollama/Custom/Anthropic endpoint leaves tools silently dead — no notice, no fallback prompt, no plain-text indication. Users get the worst outcome: chips ON, zero tool behavior, zero explanation.
**Fix:**
```kotlin
// Treat every non-tool-wired remote provider as NoSupportFallback for notice purposes:
if (selectedProvider != ProviderType.LITE_RT_LM && selectedProvider != ProviderType.LM_STUDIO) {
    val enabledIds = SkillIds.TOOL_IDS.filter { _uiState.value.skillEnabled[it] == true }
    if (enabledIds.isNotEmpty()) {
        _uiState.update { it.copy(showNoToolSupportNotice = true) }
    }
}
```

### WR-06: `RoundOutcome.malformed` is written but never read

**File:** `app/src/main/java/com/warped/data/remote/provider/LmStudioToolLoop.kt:99-107,179,331-340`
**Issue:** `doRound` carefully returns `malformed = true` for blank-name/non-object args in both streaming and non-streaming paths, but `runLoop` only checks `outcome.toolCalls.isEmpty()` (line 179) and never reads `.malformed` or `.failed` distinctly beyond the early `failed` return. The flag is dead: malformed-call turns are indistinguishable from plain-content turns, so a future reader cannot tell whether the fallback was intentional. Either branch on it (e.g. log/telemetry, or skip the silent-model fallback when nothing was malformed) or delete the field.
**Fix:**
```kotlin
if (outcome.toolCalls.isEmpty()) {
    if (outcome.malformed) Timber.w("LmStudioToolLoop: malformed tool call — content fallback")
    break
}
// or remove `malformed` from RoundOutcome entirely.
```

### WR-07: Assistant echo serializes empty-string content with `tool_calls` on pure tool rounds

**File:** `app/src/main/java/com/warped/data/remote/provider/LmStudioToolLoop.kt:206`, `app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt:52-64`
**Issue:** When a round carries tool calls but no text (the common case), line 206 re-POSTs `OpenAiMessage(role="assistant", content="", toolCalls=echo)`. `OpenAiMessage.content` is non-nullable, forcing `"content":""` on the wire. OpenAI accepts this, but strict OpenAI-compatible servers (and the OpenAI spec examples) expect `content:null` alongside `tool_calls`; some LM Studio / Ollama-compat servers reject empty-string content with tool calls. The live-server probe (47-03 open question A1) has not verified this shape.
**Fix:**
```kotlin
@Serializable
data class OpenAiMessage(
    val role: String,
    val content: String? = null,   // null omits/empties correctly alongside tool_calls
    @SerialName("tool_call_id") val toolCallId: String? = null,
    @SerialName("tool_calls") val toolCalls: List<OpenAiNonStreamingToolCall>? = null
)
```

### WR-08: Mid-stream skill toggle changes prefs but skips the conversation reset with no deferred reset

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:200-212`
**Issue:** `setSkillEnabled` writes DataStore unconditionally, but `liteRTLmProvider.resetConversation()` is guarded by `if (!_uiState.value.isStreaming)`. Chips are click-disabled while generating (`SkillChipsRow` `clickable(enabled=false)`, `ChatInputBar.kt:114`), so the UI path is covered — but there is no defense in depth: a toggle landing mid-stream (programmatic caller, or a tap racing turn start through the `viewModelScope.launch` hop) persists the new toggle while the long-lived conversation keeps the *old* `ConversationConfig.tools`, so the next turn silently runs stale tools with chips showing the new state.
**Fix:**
```kotlin
fun setSkillEnabled(id: String, enabled: Boolean) {
    if (id !in SkillIds.TOOL_IDS) return
    viewModelScope.launch(coroutineExceptionHandler) {
        skillRepository.setEnabled(id, enabled)
        // resetConversation() is idempotent and safe mid-stream (next chat() rebuilds);
        // never skip it — a stale ToolSet is worse than a redundant reset.
        try { liteRTLmProvider.resetConversation() } catch (e: Exception) { ... }
    }
}
// (Verify resetConversation-while-streaming is safe: it closes the native handle under
// lock while sendContentsWithRetry may hold it — if unsafe, set a pendingReset flag
// consumed at the top of the next chatInternal instead.)
```

### WR-09: Remote loop trusts the executor for output hygiene — no sanitize at the re-POST boundary

**File:** `app/src/main/java/com/warped/data/remote/provider/LmStudioToolLoop.kt:220-228`
**Issue:** `mapResult` caps reply text with `.take(TOOL_REPLY_MAX_CHARS)` but performs no control-char strip; hygiene depends entirely on `LocalToolExecutor` sanitizing today. `ToolExecutor` is an interface (the confirmation-gate phase explicitly plans to rewire it), so any future implementation returning raw text injects control characters into `role:tool` model context. The local `@Tool` path sanitizes at *both* layers (body + executor) per T-47-07; the remote path should match that belt-and-braces shape.
**Fix:**
```kotlin
// mapResult: sanitize at the loop boundary, independent of executor behavior:
is ToolResult.Success ->
    Triple(result.summary, null, sanitizeToolOutput(result.text, TOOL_REPLY_MAX_CHARS))
// (Move sanitizeToolOutput/summarizeToolOutput somewhere the data.remote package may
// depend on, or duplicate the 3-line control-char strip locally to avoid layer inversion.)
```

## Info

### IN-01: `<toolId>\n<summary>` split logic triplicated across UI / local / remote layers

**File:** `app/src/main/java/com/warped/ui/chat/components/ToolCopy.kt:61-64`, `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt:434-438`, `app/src/main/java/com/warped/data/remote/provider/LmStudioToolLoop.kt:421-427`
**Issue:** Three hand-rolled copies of the same parse with subtly divergent no-newline defaults (`"calculator"` literal ×2, `SkillIds.CALCULATOR` ×1 — same value today, different drift surfaces). The data layer correctly avoids importing UI-layer `ToolCopy`, but the answer is a shared data/domain parser, not triplication.
**Fix:** Add `fun parseToolRow(content: String): Pair<String,String>` next to `skillDescriptor()` (or in `domain.skills`) and call it from all three sites.

### IN-02: Synthetic `call-$index` ids and skill-id-as-`tool_call_id` on resume break tool-call pairing on strict servers

**File:** `app/src/main/java/com/warped/data/remote/provider/LmStudioToolLoop.kt:322-328,204,426`
**Issue:** When the server omits streaming ids, the echo re-POSTs a fabricated `call-$index` id (fine within the turn), but resumed history re-sends `tool_call_id = <skill id>` (`"calculator"`) with no paired assistant `tool_calls` echo at all. Lenient local servers tolerate this; strict OpenAI-compat servers may reject unpaired `role:tool` messages. Acceptable for the LM Studio target, but record the assumption.
**Fix:** No code change required for the LM Studio target; if strict-server support is ever needed, persist the wire `call.id` alongside the transcript row instead of the skill id.

### IN-03: Explicit JSON null for optional `timezone` rejected instead of defaulted; unknown params silently ignored

**File:** `app/src/main/java/com/warped/data/skills/LocalToolExecutor.kt:58-75`
**Issue:** `{"timezone": null}` fails `element !is JsonPrimitive` → `Failure("invalid timezone")` rather than resolving the documented device-zone default; meanwhile unknown extra params are silently dropped. Neither is a bypass (fail-closed both ways), but the null-handling contradicts the "nullable optional param" contract advertised by `CurrentTimeToolSet(timezone: String? = null)`.
**Fix:**
```kotlin
if (element is kotlinx.serialization.json.JsonNull) {
    if (param.required) return ToolResult.Failure("missing ${param.name}")
    continue  // optional null == absent → device-zone default
}
```

### IN-04: `formatResult` renders large integers in exponential notation

**File:** `app/src/main/java/com/warped/data/skills/ExpressionEvaluator.kt:32-39`
**Issue:** `abs(value) >= 1e15` falls through to `Double.toString()`, so `9999999999999999+1`-class results render as `1.0E16`. Display-only (values are still finite-checked), but a calculator showing `1.0E16` looks broken.
**Fix:** Use `java.text.DecimalFormat("#")` or `BigDecimal.valueOf(value).toPlainString()` for the integer path with a wider threshold.

### IN-05: `Parser.parseExpression` is public while `parseTerm`/`parseFactor` are private

**File:** `app/src/main/java/com/warped/data/skills/ExpressionEvaluator.kt:55`
**Issue:** The public surface of the parser exposes an internal recursive method taking a `depth` parameter; only `evaluate`/`formatResult` are meant for callers. A future caller passing a nonzero depth silently weakens the DoS bound.
**Fix:** `private fun parseExpression(depth: Int): Double`.

### IN-06: Non-streaming error JSON decodes to a silent empty round instead of an Error token

**File:** `app/src/main/java/com/warped/data/remote/provider/LmStudioToolLoop.kt:353-363`
**Issue:** `decodeNonStreaming` catches all parse failures (including `{"error":…}` bodies on HTTP 200) and returns an empty `RoundOutcome`, which `runLoop` treats as plain-content finish → potentially an empty bubble with no error. The streaming path surfaces HTTP errors; the non-streaming path swallows semantic ones.
**Fix:**
```kotlin
} catch (e: Exception) {
    Timber.e(e, "LmStudioToolLoop: non-streaming parse failed")
    send(StreamToken.Error("Unexpected response format"))
    return RoundOutcome(failed = true)
}
```

### IN-07: HTTP error bodies interpolated unbounded into user-visible Error tokens

**File:** `app/src/main/java/com/warped/data/remote/provider/LmStudioToolLoop.kt:264-266`, `app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt:186-192`
**Issue:** `StreamToken.Error("HTTP ${response.code}: $errorBody")` embeds the full server error body (potentially a multi-KB HTML page) into the error bubble. Local-network servers only, so severity is minimal — but truncate.
**Fix:** `val errorBody = runCatching { response.body?.string()?.take(300) }.getOrNull() ?: response.message`.

---

_Reviewed: 2026-09-28T00:00:00Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
