# Android LLM Client Pitfalls

Mistakes that sink Android LLM projects. Organized by subsystem with early-warning signals, prevention strategies, and phase assignments.

---

## 1. JNI/Native Pitfalls

### 1.1 Calling llama.cpp inference on the UI thread

Even with a background dispatcher, it's easy to accidentally route a callback through the main thread. A single `Generate` call can hold the thread for seconds or minutes. The result is a frozen UI and an ANR after 5–10 seconds.

- **Warning signs**: `generate()` blocks for more than a few frames; Android Studio warns "Inefficient blocking call on main thread"; latency jumps when model context grows.
- **Prevention**: Run all native calls via `Dispatchers.Default` on a dedicated single-thread executor. Add `StrictMode.setThreadPolicy` in debug builds to crash on accidental main-thread native calls. Use `withContext(logDispatcher) { nativeGenerate(...) }` everywhere.
- **Relevant phase**: Local inference phase.

### 1.2 Native crash taking down the entire process

An unhandled SIGSEGV in llama.cpp (bad model file, OOM during token generation, corrupted context state) kills the JVM process with zero Kotlin-level recovery. Users see a hard crash with no error UI.

- **Warning signs**: crash reports point to `libllama.so` frames with no stacktrace above JNI boundary; intermittent crashes when model goes out of memory.
- **Prevention**: Wrap every JNI call in a `nativeOutcome {}` helper that:
  1. Sets a SIGSEGV signal handler via `sigaction` to `longjmp` back into a recovery path.
  2. Catches C++ exceptions at the JNI boundary and converts to Kotlin exceptions via `env->ExceptionDescribe()`.
  3. Runs inference in a **separate process** (`android:process=":inference"`) so a native crash only kills that process, not the UI. The Service communicates via AIDL or Messenger.
  Also expose a `llama_free_all()` JNI function to release all native memory so the inference process can gracefully restart.
- **Relevant phase**: Local inference phase.

### 1.3 Thread safety: multiple coroutines calling into a single llama_context

llama.cpp's `llama_context` is not thread-safe. If multiple coroutines or a `runBlocking` snag causes concurrent `llama_eval` calls on the same context, you get silent corruption, garbage tokens, or a crash.

- **Warning signs**: duplicate or nonsensical token output; intermittent `llama_eval` returning nonzero; crash in `llama_batch_add`.
- **Prevention**: Use a `Mutex` or single-threaded `Channel` actor to serialize all inference work for a given model. Document: one context = one coroutine actor. If you want concurrent inference on the same model, create separate contexts (each consumes more RAM).
- **Relevant phase**: Local inference phase.

### 1.4 Not checking `llama_model_load` failure before using the model

`llama_model_load` returns `nullptr` on GPU-less devices or incompatible GGUF files, but JNI code that blindly dereferences the pointer crashes.

- **Warning signs**: crash in `llama_context_new` or `llama_model_desc` on specific devices; user bug reports "crashes when I tap Load" across certain SoCs.
- **Prevention**: After every JNI call that returns a pointer, check `ptr == 0L` in Kotlin before any further native calls. Surface a typed `LoadError` into the domain layer with a human-readable reason.
- **Relevant phase**: Local inference phase.

### 1.5 GPU delegate / Vulkan-in-NDK complexity

If you add GPU acceleration later, the Vulkan driver landscape on Android is fragmented (Qualcomm, Mali, PowerVR). A model that runs on one GPU may crash on another.

- **Warning signs**: "GPU offload works on my Pixel but not my Galaxy S23"; bug reports clustering around specific GPU vendors.
- **Prevention**: Start CPU-only (llama.cpp is fast enough on flagship CPUs for 1B–8B models). If GPU is added later, make it opt-in per model, detect `VK_ERROR_DEVICE_LOST` and fall back to CPU without loss of chat context. Maintain a device-compatibility blocklist in DataStore.
- **Relevant phase**: Later milestone (GPU offload).

---

## 2. Model Management Pitfalls

### 2.1 Storing GGUF files in app-private directory that gets wiped on uninstall/reinstall

Users expect their multi-GB downloads to survive app reinstalls. `context.filesDir` is deleted on uninstall.

- **Warning signs**: grief in Play Store reviews after app updates; users confused about "where my models went."
- **Prevention**: Use `MediaStore` or SAF (Storage Access Framework) to store models in a user-visible `Documents/Warped/` directory that survives uninstall. Alternatively, use `context.getExternalFilesDir(null)` which survives *some* uninstalls (Android 10+ with `hasFragileUserData` = false). Best: let the user pick a storage location via SAF with optional SD card support.
- **Relevant phase**: Model management phase.

### 2.2 Downloading models without verifying GGUF compatibility or checksums

Hugging Face repos frequently contain multiple GGUF quantizations for the same base model. Downloading the wrong file silently produces a model that either fails to load or produces garbage output, wasting gigabytes of user bandwidth.

- **Warning signs**: `llama_model_load` returns `nullptr`; OOM on load; user confused which file to download from a repo with 20 Q4_K_M variations.
- **Prevention**: Parse `model.metadata` from GGUF header BEFORE full load (lightweight read of first ~256KB). Validate magic bytes (`GGUF` at offset 0), check architecture matches expected, show user the quantization tag, parameter count, and context length. Compute SHA256 of the downloaded file against the repo manifest. Validate after every resume/retry.
- **Relevant phase**: Model management phase.

### 2.3 Underestimating RAM for model + context

A Q4_K_M 7B model is ~4.4GB on disk but needs ~5.5GB in RAM (weights + KV cache + scratch buffers). Android's low-memory killer will happily kill your inference process when the allocation fails, often without a clean error.

- **Warning signs**: `mmap` failures in native logs; app killed by LMK during inference; works on 12GB RAM devices but not 8GB.
- **Prevention**: Before loading, check `model_size_on_disk * 1.2 + context_size * memory_per_token_estimate`. Query `ActivityManager.getMemoryInfo()` and ensure at least 500MB headroom remains after allocation. Show user a "This model needs ~X GB RAM, your device has Y GB available" estimation. Allow the user to reduce `n_ctx` to fit smaller devices. Use `mlock` + `mmap` mode rather than loading the full file into a heap buffer.
- **Relevant phase**: Local inference phase.

### 2.4 No cleanup of partial downloads on disk

A paused or failed download leaves orphaned `.gguf.part` files that accumulate and silently consume storage.

- **Warning signs**: user reports "I'm out of storage but I only have 2 models"; disk inspection shows stale `.part` files.
- **Prevention**: Track all download artifacts in a Room table. On app startup, reconcile: delete `.part` files older than N hours with no active WorkManager job. Offer "Clear incomplete downloads" in settings. Never leave a `.part` file without a corresponding database row.
- **Relevant phase**: Model management phase.

---

## 3. UI/Streaming Pitfalls

### 3.1 Emitting a UI recomposition for every single incoming token

Each SSE chunk contains a small token (sometimes just "e" or " "). Calling `_uiState.update { it.copy(text = it.text + newToken) }` for every token causes ~50-100 recompositions/second. Compose struggles with large text fields recomposing at this rate.

- **Warning signs**: janky scrolling during token receipt; systrace shows Compose measure/layout thrashing; battery drains fast during inference.
- **Prevention**: Batch token emission at ~30-60ms intervals. Use a `flow { ... }` with `conflate()` and a buffer that accumulates incoming tokens and emits the accumulated delta on a timer tick with `sample()`. The render text grows by chunks, not individual characters. Consider `snapshotFlow` with a frame-aligned debounce.
- **Relevant phase**: Chat UI / streaming phase.

### 3.2 Not stopping inference when the user navigates away or the composable leaves composition

llama.cpp will keep generating tokens forever (or until `n_predict` limit). If the user leaves the chat screen, the inference coroutine keeps burning CPU and battery.

- **Warning signs**: high CPU usage after minimizing app; battery drain; logcat shows token generation while app is backgrounded.
- **Prevention**: Tie inference lifecycle to `ViewModel`. In `onCleared()`, call `inferenceEngine.cancel()`. Use `DisposableEffect` in the chat composable to cancel on leave. Add a foreground service with a persistent notification during active inference so the OS knows it's doing work, but auto-cancel if the user swipes the notification away.
- **Relevant phase**: Chat UI / streaming phase.

### 3.3 Rapid re-generation spam (user taps "stop" then "regenerate" 5× fast)

Each generate press creates a new coroutine. If the cancel isn't synchronous (native `llama_eval` can't be interrupted mid-call on some backends), multiple inference runs overlap.

- **Warning signs**: multiple token streams interleaving in the text view; "stop" button doesn't immediately stop output; native crash from concurrent context use.
- **Prevention**: Maintain a single `AtomicBoolean` or `Mutex` gate. Before starting inference, wait for previous to confirm cancelled via a `Job.cancelAndJoin()` with a 500ms timeout. Disable the "regenerate" button while the current run is cancelling. Show a "stopping..." UI transition state.
- **Relevant phase**: Chat UI / streaming phase.

### 3.4 Losing scroll position when new tokens arrive during user scroll-back

User scrolls up to read previous messages. New tokens push the content down, fighting the user's scroll.

- **Warning signs**: "I can't read old messages while it's generating"; scroll position jumps to bottom every new token batch.
- **Prevention**: Use `LazyColumn` + `rememberLazyListState()`. Only auto-scroll to bottom if the user is already within ~3 items of the bottom. Track `isUserScrollingUp` via `LaunchedEffect(firstVisibleItemIndex)`. If the user has scrolled up more than a threshold, show a "↓ new tokens" floating action button instead of forcing scroll.
- **Relevant phase**: Chat UI / streaming phase.

---

## 4. Networking Pitfalls

### 4.1 SSE parsing that doesn't handle multi-chunk splits on slow connections

SSE payloads can arrive fragmented across multiple TCP packets. A naive `line.contains("data:")` split will miss tokens that span chunk boundaries.

- **Warning signs**: intermittent missing tokens on mobile data; incomplete responses on high-latency connections; duplication when the parser fires on a partial line.
- **Prevention**: Use a proper SSE parser with a line accumulator buffer (e.g., OkHttp's `ResponseBody.source()` read into a `BufferedSource`, accumulating lines until `\n\n` double-newline). Never assume an SSE `data:` line arrives complete in a single `readUtf8Line()`. Test with network-condition throttling (400ms latency, 2% packet loss) in both unit and instrumentation tests.
- **Relevant phase**: Remote provider connectivity phase.

### 4.2 Not handling `[DONE]` and stream termination properly, leaving connections open

OpenAI-compatible servers send `data: [DONE]` to signal completion. If your parser ignores it, the connection stays open until the OkHttp timeout fires, consuming a socket and potentially hitting the provider's connection limit.

- **Warning signs**: socket leaks (lsof shows CLOSE_WAIT connections); provider rate-limits the user; OkHttp timeouts 30s after last token.
- **Prevention**: Treat `data: [DONE]` as a terminal event. Close the response body immediately (`response.body?.close()`). Set shorter read timeouts for SSE streaming (10s idle timeout, because a streaming response should never be idle that long).
- **Relevant phase**: Remote provider connectivity phase.

### 4.3 Retry storms on transient failures

A network error during inference triggers an automatic retry that also fails, which triggers exponential backoff, but the user hits "regenerate" manually — creating a self-reinforcing retry lock.

- **Warning signs**: many parallel HTTP calls hitting the same endpoint; provider returns 429 Too Many Requests; logcat shows repeated failures without user interaction.
- **Prevention**: Implement a single-level retry with capped backoff (max 1 auto-retry, max 5s delay). Never retry on 4xx errors — only on 5xx and connection failures. Use a `RetryInterceptor` in OkHttp that respects `Retry-After` headers. Don't auto-retry if the user explicitly cancelled.
- **Relevant phase**: Remote provider connectivity phase.

### 4.4 Using HTTPS without certificate pinning for self-signed local servers (Ollama, LM Studio, llama.cpp server)

LM Studio desktop and Ollama serve over HTTPS with self-signed certs. Android's default trust manager rejects self-signed certs unless the user explicitly trusts them.

- **Warning signs**: `javax.net.ssl.SSLHandshakeException` when connecting to local endpoints; user confused why "my laptop server won't connect."
- **Prevention**: For local/WiFi endpoints (`192.168.x.x`, `10.x.x.x`, `localhost`), offer a "Trust self-signed certificate" toggle. Build an OkHttp `X509TrustManager` that accepts the specific cert fingerprint (pinned, not blanket-accept-all). Never accept all certs silently — show a security warning and require explicit user approval with the cert details visible.
- **Relevant phase**: Remote provider connectivity phase.

### 4.5 Not implementing a per-endpoint connection health check before chat

User types a message, hits send, and only then discovers the endpoint is unreachable (firewall, server down, wrong port).

- **Warning signs**: "Why did my message fail?" after 10s of waiting; latency injected before error appears.
- **Prevention**: Call a lightweight health endpoint (e.g., `GET /api/tags` for Ollama, `GET /v1/models` for OpenAI-compatible) with a 3s connect timeout when the user adds or edits an endpoint, and optionally on app launch for the active endpoint. Show status inline (green/orange/red dot) before the user invests in typing a message.
- **Relevant phase**: Remote provider connectivity phase.

---

## 5. Hugging Face / Download Pitfalls

### 5.1 Using raw HTTP without range-support for large model downloads

A 4GB GGUF download without HTTP Range headers means any interruption requires starting from byte 0. On mobile, this happens constantly.

- **Warning signs**: users with slow connections can never finish a download; "download keeps restarting"; high data usage from repeated partial downloads.
- **Prevention**: Always use `Accept-Ranges: bytes` with `Range` headers. Hugging Face CDN supports range requests. Implement a `ResumeDownloader` that:
  1. Checks existing file size on disk.
  2. Sends `Range: bytes=<existing_size>-`.
  3. Appends to the file.
  4. Validates SHA256 on completion.
  Use `WorkManager` with `setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())` as default (allow user override for metered networks).
- **Relevant phase**: Model management phase.

### 5.2 Not rate-limiting Hugging Face API calls, triggering 429 bans

A naive "list all models in this repo" implementation fires 50 concurrent `GET` requests to get file metadata. Hugging Face API rate-limits aggressively and will 429-ban the client IP.

- **Warning signs**: `429 Too Many Requests` errors; prolonged download failures after a burst of API calls; API returning empty responses.
- **Prevention**: Implement a token-bucket rate limiter: max 5 req/s on the Hugging Face API, max 2 concurrent connections per repo. Cache repo file listings locally (Room) with a TTL of 15 minutes. Use `Retry-After` headers to dynamically pace requests.
- **Relevant phase**: Model management phase.

### 5.3 Download progress tracking via WorkManager that freezes when app is backgrounded by Doze

WorkManager may defer or suspend work during Doze. If your progress tracking relies on continuous `setProgress()`, it appears stuck to the user who returns after 15 minutes.

- **Warning signs**: download notification stuck at 47% for 20 minutes; user force-kills and restarts app; WorkManager `getWorkInfoById` shows ENQUEUED even when the network is available.
- **Prevention**: Use a foreground service (`ForegroundServiceStartNotAllowedException` handling for Android 12+) with `setForeground(true)` for active downloads. This bypasses Doze restrictions. Combine with WorkManager for scheduling and rescheduling, but move the actual byte transfer into a foreground service. Periodically flush progress to a Room table so the UI can read it even if the service restarts.
- **Relevant phase**: Model management phase.

### 5.4 Not handling Hugging Face repo structure variability

Some repos use `model.Q4_K_M.gguf`, others use `ggml-model-Q4_K_M.gguf` or put files in subdirectories. A parser that assumes a flat `.gguf` glob breaks.

- **Warning signs**: some repos show "no models found" when models exist; user can't download models from certain popular repos.
- **Prevention**: Use the Hugging Face API (`GET https://huggingface.co/api/models/{repo}`) to enumerate siblings, filtering for files ending in `.gguf`. Handle nested paths. Show a file picker UI for repos with multiple GGUF variants, annotated with quantization type, size, and a "Recommended" badge for the `Q4_K_M` quant.
- **Relevant phase**: Model management phase.

---

## 6. Security Pitfalls

### 6.1 Storing API keys in plaintext DataStore or SharedPreferences

Even with `MODE_PRIVATE`, SharedPreferences and DataStore are readable on rooted devices, via ADB backup, or by malware with file read permissions.

- **Warning signs**: keys visible in `adb backup` output; keys show up in crash logs or analytics; security audit flags plaintext storage.
- **Prevention**: Use Android Keystore with `KeyGenParameterSpec` and AES-GCM. Store only the encrypted ciphertext in DataStore. Encrypt with a Keystore-backed key that requires user authentication (`setUserAuthenticationRequired(true)`) for every decrypt, or use `setUserAuthenticationValidityDurationSeconds(30)` for a 30-second grace period. Never log keys — redact them with a custom `Log.e` wrapper that matches API key patterns.
- **Relevant phase**: Security / persistence phase.

### 6.2 Prompt and conversation text leaking into logcat

`Log.d("ChatViewModel", "User message: $prompt")` or an uncaught exception stacktrace that includes the chat text ends up in logcat, which any app with `READ_LOGS` permission can read on older Android, and which appears in bug reports.

- **Warning signs**: private conversations visible in `adb logcat`; bug report archives contain chat history.
- **Prevention**: Never log user prompts, model responses, or API keys. Write a `Timber` tree or custom logger that strips known-sensitive fields. Enable `StrictMode` and ProGuard/R8 rules that detect `Log.*` calls in production release builds. Use a debug-only log tree.
- **Relevant phase**: Security / persistence phase.

### 6.3 Not clearing sensitive data from memory after use

API keys and prompt text stay in `String` objects (immutable, heap-resident until GC). An attacker with a memory dump (rooted device, forensic tool) can recover them.

- **Warning signs**: secure audit flags "in-memory secrets"; forensic analysis recovers past conversation text.
- **Prevention**: Store API keys in `CharArray` instead of `String` and zero-fill after use. Use `Android Keystore` to decrypt into a local scope, use the value, and zero the `ByteArray`. This is a hardening measure — do it for keys, accept the risk for prompt text unless doing on-device inference where the text is already in process memory anyway.
- **Relevant phase**: Security / persistence phase.

### 6.4 Connecting to remote endpoints over cleartext HTTP by default

Accidentally using `http://192.168.1.5:1234` instead of `https://` for a local server. Android 9+ blocks cleartext by default with `CleartextTrafficPermitted=false`.

- **Warning signs**: `java.net.UnknownServiceException: CLEARTEXT communication not permitted`; network security config errors.
- **Prevention**: For local network endpoints, create a `network_security_config.xml` that allows cleartext only for LAN IPs (`192.168.0.0/16`, `10.0.0.0/8`, `172.16.0.0/12`, `localhost`). Never allow cleartext globally. Show an explicit warning when the user enters an `http://` URL: "This server is not encrypted. Anyone on your network can see your messages."
- **Relevant phase**: Remote provider connectivity phase.

---

## 7. Android-Specific Pitfalls

### 7.1 Process death: losing in-progress inference and chat state

Android can kill any background process at any time. If the user switches apps and inference is running in a foreground service, process death kills the native model context. Restarting requires reloading the model (5–30 seconds).

- **Warning signs**: app starts from scratch after switching back from Chrome; inference lost; "I was 80% through a message and it's gone."
- **Prevention**: Use `SavedStateHandle` in ViewModels to survive process death for UI state. Persist chat messages to Room after every complete response (and every ~30 tokens for in-progress messages). On process recreation, restore the conversation and show "[Message interrupted — tap to regenerate]". Notify the user that the model needs reloading. The inference Service should save its last known state to DataStore every few seconds.
- **Relevant phase**: Chat UI / streaming phase.

### 7.2 Background execution limits: WorkManager deferral or ForegroundService restrictions

Android 14+ blocks foreground service launches from the background. Attempting to start a download from a `WorkManager` doWork that's been deferred triggers `ForegroundServiceStartNotAllowedException`.

- **Warning signs**: downloads never start when app is backgrounded; `SecurityException` in crash reports on Android 14+.
- **Prevention**: For Android 14+, use `WorkManager`'s built-in `setForeground(foregroundInfo)` which internally handles the restrictions. For direct foreground services, call `Context.startForegroundService()` while the app is in the foreground, and catch the exception with a WorkManager fallback. Test on Android 14 emulators specifically.
- **Relevant phase**: Model management phase.

### 7.3 Doze mode blocking network during long downloads

After the device enters Doze (typically 15–30 min after screen off), network access is restricted to maintenance windows. A 4GB download will stall for hours.

- **Warning signs**: download resumes only when user wakes the device; download that should take 5 minutes takes 2 hours overnight.
- **Prevention**: Use `PowerManager.isIgnoringBatteryOptimizations()` to check exemption. Request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission. If denied, use `WorkManager` with `setExpedited(true)` and explain to the user to disable battery optimization. For critical workflows, offer a "keep screen on" toggle during downloads (`WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON`).
- **Relevant phase**: Model management phase.

### 7.4 Thermal throttling during sustained inference

Extended inference (5+ minutes) generates significant heat. Android's thermal engine downclocks the CPU, making token generation 3–10× slower mid-conversation.

- **Warning signs**: token/s rate drops dramatically after 3–5 minutes; device gets noticeably hot; sudden performance cliff mid-conversation.
- **Prevention**: Monitor `PowerManager.getCurrentThermalStatus()` (Android 10+). At `THERMAL_STATUS_SEVERE`, pause inference with a "Device cooling down..." message and auto-resume when status returns to `THERMAL_STATUS_NONE` or `THERMAL_STATUS_LIGHT`. For lower-severity thermal states, enable a "throttled mode" that uses fewer threads.
- **Relevant phase**: Local inference phase.

### 7.5 Application Not Responding (ANR) from synchronous disk I/O on main thread

Loading chat history from Room, reading a GGUF file for metadata, or downloading to disk can trigger ANRs. Room's suspend functions are safe, but using `runBlocking` or making sync calls from UI-land bypasses that safety.

- **Warning signs**: ANR dialogs; Play Store pre-launch report flakiness; `Input dispatching timed out` in traces.
- **Prevention**: Use `Dispatchers.IO` for all file I/O and Room queries. Never `runBlocking {}` in composable code. Use `produceState` or `collectAsStateWithLifecycle()` to bridge coroutines to Compose. Enable `StrictMode.setThreadPolicy` with `detectDiskReads()` and `detectDiskWrites()` in development.
- **Relevant phase**: All phases (architectural).

### 7.6 Naive large-file I/O consuming all available heap

Loading a 4GB GGUF file into a `ByteArray` will crash the app with `OutOfMemoryError` on most devices.

- **Warning signs**: OOM crash when loading large models; heap dumps show `byte[]` allocations near model size; works on devices with 12GB RAM but fails on 8GB.
- **Prevention**: Never allocate a `ByteArray` the size of the model file. llama.cpp's `mmap` mode handles memory management correctly — pass the file path, not the file bytes. For the few cases where you must read a chunk (e.g., metadata header), read the first ~256KB into a small buffer. For checksums, stream the file in 4KB chunks using `FileInputStream`.
- **Relevant phase**: Model management phase.

### 7.7 Not handling configuration changes (rotation, multi-window, foldable) gracefully

`Activity.recreate()` destroys and recreates the Activity. If you're not using ViewModels correctly or you're storing inference state in the Activity, the user loses their in-progress message on every rotation.

- **Warning signs**: inference state lost on screen rotation; chat history disappears when switching to split-screen; foldable unfolding resets the app.
- **Prevention**: Never store UI state in `Activity` — use `ViewModel`. For the inference engine, use a `@Singleton` scoped service managed by Hilt. The `ViewModel` holds the lifecycle-aware coroutine scope; the engine survives configuration changes. Test on foldable emulator configurations.
- **Relevant phase**: Chat UI / streaming phase.

---

## Summary: Most-Common Project-Killers

| Rank | Pitfall | Impact | Phase |
|------|---------|--------|-------|
| 1 | Running inference on UI thread | ANR, 1-star reviews | Local inference |
| 2 | SSE parser breaks on mobile network jitter | Silent data loss, broken responses | Remote connectivity |
| 3 | Download reset on interruption | Wasted GBs, uninstall | Model management |
| 4 | Native crash kills whole process | Hard crash, zero recovery | Local inference |
| 5 | Plaintext API key storage | Data breach, Play Store rejection | Security |
| 6 | No stop/cancel mechanism | Battery drain, ANR, crash | Chat UI / streaming |
| 7 | Thread safety around llama_context | Corrupted output, mysterious crashes | Local inference |
| 8 | Process death loses everything | Terrible UX after app switch | All |

---

*Generated: 2026-04-30 | Project: Warped | Context: greenfield Android LLM client*
