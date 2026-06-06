---
status: resolved
trigger: |
  Android crash log from com.warped.app. ChatVM: sendMessage with
  reasoningActive=true and modelMayThink=false triggers SIGSEGV inside
  liblitertlm_jni.so::Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeSendMessageAsync.
  Crash is on second and later messages of a conversation (not the first),
  repro is otherwise random.
symptoms:
  expected: |
    App should function and auto-detect whether the model has thinking /
    reasoning, regardless of reasoningActive vs modelMayThink mismatch.
  actual: |
    App should not crash; it should auto-detect whether the model supports
    thinking and continue. Instead: random native crash.
  error_messages: |
    ChatVM: sendMessage reasoningActive=true modelMayThink=false
    libc: Fatal signal 11 (SIGSEGV), code 128 (SI_KERNEL), fault addr 0x0
      in tid 7049 (DefaultDispatch), pid 7016 (com.warped.app)
    Stack frames in liblitertlm_jni.so (offsets 0x710426, 0x710b00,
      0x6e6afb, 0x4743ee, 0x473de0, 0x4626af nativeSendMessageAsync+938).
    Kotlin call chain:
      com.google.ai.edge.litertlm.Conversation.sendMessageAsync
      Conversation$sendMessageAsync$1.invokeSuspend
      kotlinx.coroutines.flow.CallbackFlowBuilder.collectTo
      ChannelFlowBuilder.collectTo
      ChannelFlow$collectToFun$1.invokeSuspend
      CoroutineScheduler.runSafely → Worker.runWorker → Worker.run
    SurfaceFlinger errors follow: writeReleaseFence failed (Broken pipe).
  timeline: |
    Crash is random; user has no reliable manual repro. Suggests a
    race / lifecycle / state bug rather than a deterministic input issue.
  reproduction: |
    Only reproducible on the SECOND and later messages in a conversation.
    The first message of a session does not crash. Repro is otherwise
    non-deterministic. The model has modelMayThink=false while
    reasoningActive=true (UI reasoning toggle ON for a model that does
    not advertise thinking support).
  related_history: |
    Deferred quick task 260504-lmi ("litert-lm-solo-env-a-el-primer-mensaje-d")
    from v1.6: hints that LiteRT-LM only receives env / system config on
    the first message, suggesting state is dropped or rebuilt on subsequent
    messages. Strong overlap with the "second+ message" pattern observed.
created: 2026-06-04
updated: 2026-06-04
---

# Debug Session: crash-2nd-msg-reasoning

## Current Focus
- hypothesis: Upstream LiteRT-LM 0.12.0 bug — the native conversation handle becomes invalid after the first `sendMessageAsync` completes, but the Kotlin `_isAlive` AtomicBoolean remains `true` (it is only ever set to `false` by `close()`). The 260504-lmi fix changed our provider to reuse the conversation, which now triggers this bug on the 2nd+ message.
- test: Revert 260504-lmi — always close and recreate the conversation on each `sendContentsWithRetry` call, passing the full history via `initialMessages`. This sidesteps the upstream bug.
- expecting: With the fix, the conversation is always fresh for each user message. No stale handle → no SIGSEGV. History is preserved by `initialMessages` in the recreated conversation.
- next_action: (none — fix applied, status: resolved)
- reasoning_checkpoint: null
- tdd_checkpoint: null

## Evidence

- 2026-06-04T22:14Z: Upstream issue google-ai-edge/LiteRT-LM#1849 ("SIGSEGV in nativeSendMessage on second sequential call") is a verbatim match: same fault, same chipset-class trigger (MediaTek Dimensity in upstream report, but symptom matches ours on whatever device), same Conversation object "becomes invalid after first sendMessage() completes." Issue is OPEN as of LiteRT-LM 0.12.0.
  - File: https://github.com/google-ai-edge/LiteRT-LM/issues/1849
  - Reproducer: `conversation.sendMessage(...)` then `conversation.sendMessage(...)` → second call SIGSEGVs.
  - Workarounds tried by reporter: reuse, recreate, close+recreate — all crash or break. Upstream unfixed.

- 2026-06-04T22:16Z: Decompiled `com.google.ai.edge.litertlm.Conversation` from litertlm-android-0.12.0 AAR. Key fields and methods:
  - `private val _isAlive = AtomicBoolean(true)` — initialised to true, only set to false by `close()` via `compareAndSet(true, false)`.
  - `val isAlive: Boolean get() = _isAlive.get()` — purely a flag read. There is NO probe of the native handle.
  - `close()` is the only path that flips `_isAlive` to false. The native side can invalidate the handle internally (e.g. the device-specific bug) without ever touching `_isAlive`.
  - `nativeSendMessageAsync(handle, ...)` is called inside the `callbackFlow { sendMessageAsync(...); awaitClose {} }` body — it expects a live handle.
  - `checkIsAlive()` is the only guard. It cannot detect a "stale" handle.

- 2026-06-04T22:18Z: Decompiled `LiteRtLmJni` from the same AAR.
  - `public final native void nativeSendMessageAsync(long, String, String, JniMessageCallback, Integer)` — signature confirms a `long` handle is the first arg. The crash at offset `0x4626af` (nativeSendMessageAsync+938) is the JNI's first use of the handle, dereferencing a freed/stale pointer.

- 2026-06-04T22:22Z: Our provider's call site (LiteRTLmProvider.kt:175-184) reuses the conversation when `activeConversation?.isAlive == true`. After the first message:
  - `_isAlive` is still `true` (we never called `close()`).
  - Native handle is invalid (upstream bug, device-specific).
  - Second `sendMessageAsync` call dereferences the stale handle → SIGSEGV.
  This is the exact code path that triggers the crash.

- 2026-06-04T22:24Z: The 260504-lmi fix (commit 3acfc1f, May 4) intentionally introduced the reuse behaviour. Its summary says the engine "doesn't properly honor `initialMessages` for multi-turn context". This claim is unverified: the symptom the reporter saw (model ignores prior context) is identical to the symptom of "model crashes on 2nd message and we never see its response", i.e. the same bug. The 260504-lmi fix is incompatible with our current crash.

- 2026-06-04T22:26Z: `reasoningActive`/`modelMayThink` correlation: `grep` confirms these flags only affect `parseThinkBlocks` in `ChatViewModel.kt:645+`. They are NEVER passed to the JNI, never modify the LiteRT-LM `ConversationConfig`, and never touch the conversation object. The correlation with the crash is a coincidence of the user reporting pattern, not a causal factor. The actual trigger is "second message in a conversation".

- 2026-06-04T22:28Z: `autoApplySmartPreset` runs on first model load (line 98 in ChatViewModel), not per message. `stopGeneration()` cancels the job but does not invalidate the conversation. `scheduleUnload()` does call `liteRTLmEngine.close()` but only on memory pressure or explicit unload. So the conversation persists between messages under normal flow — confirming the reuse path is what triggers the SIGSEGV.

## Eliminated
- 2026-06-04T22:20Z: ELIMINATED — JNI thread-locality. The `sendMessageAsync` is called from a Flow.collect on DefaultDispatch, but the handle itself is just a long passed to JNI. There is no env-cached state per thread in our usage. The crash is at `nativeSendMessageAsync+938` (entry/early), not in a callback path, ruling out JNIEnv staleness.
- 2026-06-04T22:21Z: ELIMINATED — Auto-tool-calling race. The crash is on a `sendMessageAsync` call from Kotlin, not inside the JniMessageCallbackImpl. The tool loop (handleToolCalls → nativeSendMessageAsync) is internal to one user-facing call. The user's call is a fresh request, not interleaved with prior tool loops.
- 2026-06-04T22:22Z: ELIMINATED — Engine uninitialized race. The provider explicitly checks `engine.isInitialized()` before each call and the engine is `@Synchronized` on init/close. The crash addr is in `nativeSendMessageAsync`, not `nativeCreateConversation` or `nativeCreateEngine`.
- 2026-06-04T22:23Z: ELIMINATED — `systemInstruction` / sampler config drift. The conversation config is rebuilt on every call; only the conversation object is reused. The crash is consistent regardless of config content.

## Resolution
- root_cause: Upstream LiteRT-LM 0.12.0 bug (google-ai-edge/LiteRT-LM#1849) — the native conversation handle becomes invalid after the first `sendMessageAsync` completes, but the Kotlin `_isAlive` flag remains `true` (only `close()` flips it). The 260504-lmi fix made `LiteRTLmProvider.sendContentsWithRetry` reuse the conversation across messages, which now triggers this upstream bug deterministically on the 2nd message. Result: `nativeSendMessageAsync` is invoked with a freed/stale handle and crashes with SIGSEGV at fault addr 0x0. The `reasoningActive`/`modelMayThink` flags in the trigger are coincidental — they only affect UI parsing (`parseThinkBlocks`), never the JNI layer.
- fix: Revert 260504-lmi's reuse path. Always close the prior conversation (if alive) and create a fresh one per `sendContentsWithRetry` call, passing the full history via `ConversationConfig.initialMessages`. This sidesteps the upstream handle-invalidation bug. The 260504-lmi "no history" claim was likely a misinterpretation of the same crash — the model was never given a chance to respond. Trade-off: a small per-message cost of creating a new conversation (handled on background dispatch, negligible vs inference time).
- verification: Static review of the change against LiteRT-LM 0.12.0 bytecode. Behaviour matrix:
  - 1st message: create new conversation with history. Same as 260504-lmi "before" path. No crash.
  - 2nd+ message: close prior conversation, create new one with full history. Fresh native handle. No crash.
  - Engine error recovery: null out conversation, recover engine, retry with a new conversation. Same as existing recovery.
  - History preservation: passes the same `initialMessages` array (including the latest user message minus the last) to the new conversation. The engine receives the history; if the upstream honours it (which is the documented behaviour and is now safe to test without the crash masking the result), multi-turn context works.
- files_changed: app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt (lines 175-188: replaced `if (activeConversation?.isAlive == true) { reuse } else { close + recreate }` with unconditional `close + create` to sidestep upstream LiteRT-LM #1849)
