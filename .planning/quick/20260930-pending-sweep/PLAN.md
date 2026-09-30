# Sweep PLAN — all code-doable pending follow-ups (2026-09-30)

Scope: 7 locked items, each reversible, each with its own task + tests.
Out of scope: device-only smokes, TUNE triggers, static-matrix auto-probing,
Nyquist VALIDATION.md, new providers/locales, engine vision changes.

## Verified in code (do not re-research)

- `LiteRTLmEngine.createConversation(config, thinkingConfig=null, maxOutputToken=null)` EXISTS
  (`data/local/inference/LiteRTLmEngine.kt:192`). `ThinkingConfig` import:
  `com.google.ai.edge.litertlm.ThinkingConfig`.
- Thinking flows today only as `parameters.reasoningEnabled` + think-tag strip
  (`LiteRTLmProvider`, `LiteRtLlmHelper.runInference(request, enableThinking)`,
  `LMStudioProvider:286`, `AnthropicProvider:221`). `EngineManager:275` calls
  `liteRTLmEngine.createConversation(config)` with NO thinkingConfig — this is the seam.
- Toggle/capability gate exists: `ChatViewModel.enableThinking/supportsThinking`
  (`supportsThinkingFor`, allowlist `supportsThinking`), toggle stays disabled
  without capability (unchanged).
- Local image-history carry EXISTS: `LiteRTLmProvider.buildHistoryMessages`
  (K=`HISTORY_IMAGE_CARRY_MAX`, newest-first, dedupe, skip-malformed). Remote
  history is text-only: `OpenAIProvider:136`, `OllamaProvider:110,191`,
  `LMStudioProvider:118`, `CustomProvider:116,199` map `content = text`.
- `ChatRequest` has `images: List<String>` + `audioBytes: ByteArray?` (current turn).
  `MessageEntity` persists `images` column; audio persistence NOT confirmed —
  executor verifies and decides per item 2 rule.
- `GroundedSource` has `ogTitle/ogDescription/ogImageUrl`, `extractedText`,
  `status` — NO snippet field (`domain/model/GroundedSource.kt`). Snippets live in
  `DdgResult.snippet` (`DuckDuckGoSearchRepository:387-391`) and Tavily results.
  Executor must thread snippet into `GroundedSource` (new nullable field) and
  populate from DDG/Tavily mapping sites.
- `ActiveDownloadContent` is the shared component (`ui/components/ActiveDownloadCard.kt`),
  Cancel-only per D1, used in `ModelsScreen:455` and `HuggingFaceScreen:261`.
  `ModelDownloadManager.pauseDownload/resumeDownload` exist
  (`data/local/download/ModelDownloadManager.kt:129,155`);
  `CatalogViewModel:75,78` exposes pause/resume. Models-screen VM exposure TBD —
  executor verifies (if missing, expose symmetrically).
- Activation flow: `UnifiedSelectorViewModel.connectLocal` +
  `ActiveModelSelection.connectLocal` + `ChatViewModel.selectConversation`
  (conversation-bound override). Models-screen toggle call site TBD — executor finds it.
- Tool failures degrade to model-only text (`LocalToolLoop:270-276`
  `"Error: ..."` fed back to model). Transient UI precedent: `ChatEvent.Snackbar`
  (`ChatUiState.kt:292`, collected `ChatScreen:183`), transient tool row
  `toolCallActive` (`ChatViewModel:869-871`, cleared on stop `1207`).
- Trio: `webOverride` tri-state exists (`ChatUiState:137,211`,
  `ChatScreen:410,829-854`, `ChatViewModel.setWebOverride:322`); Fuentes/omitida
  rendering in chat components (`AllSourcesSheet`, `OgSourceCard`, grounded-status
  rows — executor locates exact files).

---

## Task 1 — ThinkingConfig wiring (local path)

Files:
- `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt`
- `app/src/main/java/com/warped/data/local/inference/LiteRtLlmHelper.kt`
- `app/src/main/java/com/warped/data/local/inference/EngineManager.kt`
- `app/src/test/java/com/warped/data/local/inference/ThinkingConfigPassThroughTest.kt` (new)

Action (per locked decision 1):
- Thread `enableThinking AND capability-gate` (same `supportsThinking`/allowlist
  predicate the toggle uses — do NOT invent a new gate) from helper/provider down
  to `EngineManager`, which passes `ThinkingConfig(...)` into
  `LiteRTLmEngine.createConversation(config, thinkingConfig, ...)`.
- Verify the `ThinkingConfig` constructor signature in the 0.17.x AAR sources on
  disk before constructing; `maxOutputToken` stays null (not in scope).
- No `<|think|>` manual injection; tag-strip/display path untouched. Toggle without
  capability stays disabled (no change).

Verify: `./gradlew :app:testDebugUnitTest --tests "*ThinkingConfigPassThroughTest*"`
Done: enableThinking+capable ⇒ conversation created with non-null ThinkingConfig;
  toggle-off or incapable ⇒ null (engine defaults); existing think-tag tests green.

## Task 2 — Remote image history carry (+ audio symmetry check)

Files:
- `app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
- `app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt`
- `app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt`
- `app/src/main/java/com/warped/data/remote/provider/CustomProvider.kt`
- `app/src/test/java/com/warped/data/remote/provider/RemoteImageCarryTest.kt` (new)

Action (per locked decision 2):
- Mirror the local K=3 newest-first carry in each remote history mapping with the
  SAME cap/dedupe/skip rules as `LiteRTLmProvider.buildHistoryMessages` (extract a
  shared pure helper if cheap, else duplicate the ~20-line rule with a comment
  pointing at the local canonical implementation; keep per-provider payload shapes).
- Audio: check whether history audio is symmetric (persisted + rendered) in
  local vs remote. If symmetric, carry audio in both; else local-only carry and
  record a follow-up line in SUMMARY (planner decision with evidence — put the
  evidence: file:line showing audio is current-turn-only).
- Current-turn image-send paths unchanged (current-turn only stays).

Verify: `./gradlew :app:testDebugUnitTest --tests "*RemoteImageCarryTest*"`
Done: remote history carries K newest image turns with dedupe/skip identical to
  local; text-only behavior preserved when no images; audio decision documented.

## Task 3 — Card description fallback (snippet)

Files:
- `app/src/main/java/com/warped/domain/model/GroundedSource.kt` (add `snippet: String? = null`)
- `app/src/main/java/com/warped/data/grounding/DuckDuckGoSearchRepository.kt` (populate)
- `app/src/main/java/com/warped/data/grounding/TavilySearchRepository.kt` (populate)
- Card render site for `ogDescription` (executor locates: likely `OgSourceCard*` —
  grep `ogDescription` under `ui/chat/components`)
- `app/src/test/java/com/warped/ui/chat/components/SourceCardSnippetFallbackTest.kt` (new)

Action (per locked decision 3):
- Fallback chain at render: `ogDescription` → `snippet` (capped, cap constant
  co-located with existing truncation logic) → null-if-blank → hidden.
  Identical rendering rules (same Text style/visibility branch, no new card layout).
- Sanitize snippet through the same sanitizer other search text uses before display.

Verify: `./gradlew :app:testDebugUnitTest --tests "*SourceCardSnippetFallbackTest*"`
Done: null ogDescription + non-blank snippet ⇒ snippet shown capped; blank/both-null
  ⇒ description hidden; ogDescription present ⇒ unchanged.

## Task 4 — Pause in shared download component (both screens)

Files:
- `app/src/main/java/com/warped/ui/components/ActiveDownloadCard.kt`
- `app/src/main/java/com/warped/ui/models/ModelsScreen.kt` (usage ~:455)
- `app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt` (usage ~:261)
- Models-screen ViewModel pause/resume exposure (executor verifies; mirror
  `CatalogViewModel:75,78` if missing)
- `app/src/test/java/com/warped/ui/components/ActiveDownloadPauseTest.kt` (new)

Action (per locked decision 4):
- Add `onPause: () -> Unit, onResume: () -> Unit` params to `ActiveDownloadContent`;
  pause/resume `IconButton` next to Cancel in the downloading branch AND resume
  entry in the `isPaused` branch; wire BOTH call sites identically.
- Reuse `ModelDownloadManager.pauseDownload/resumeDownload` (tested) — no engine
  changes, no worker changes.

Verify: `./gradlew :app:testDebugUnitTest --tests "*ActiveDownloadPauseTest*"`
Done: both screens show pause while downloading and resume while paused; pause →
  manager.pauseDownload called with same id semantics as catalog; no behavior change
  to Cancel/Delete.

## Task 5 — Activation opens a NEW chat

Files:
- Models-screen toggle call site (executor locates: grep `activate` under `ui/models`)
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` (new-chat creation path,
  cf. `createConversation` usage ~:1721)
- `app/src/test/java/com/warped/ui/models/ModelActivationNewChatTest.kt` (new)

Action (per locked decision 5):
- Toggling a model ON in Models & Endpoints: keep existing connect/activate logic,
  then open a NEW chat bound to that model; old conversation untouched in history
  (no delete, no rebind, no message move).
- Document as behavior change in SUMMARY.

Verify: `./gradlew :app:testDebugUnitTest --tests "*ModelActivationNewChatTest*"`
Done: toggle-ON ⇒ new conversation id bound to activated model; previous
  conversation row intact with original model binding.

## Task 6 — Tool-failure transient affordance

Files:
- `app/src/main/java/com/warped/data/agentic/LocalToolLoop.kt` (failure signal site)
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` (StreamToken.ToolStatus
  handling ~:869; Snackbar precedent ~:977)
- Compat/remote tool-loop failure sites (executor greps `ToolStatus`/tool-error
  mapping in remote providers; cover if same shape, note if absent)
- `app/src/test/java/com/warped/ui/chat/ToolFailureAffordanceTest.kt` (new)

Action (per locked decision 6):
- Cheapest consistent option: on tool-call failure, emit the existing transient
  affordance (`ChatEvent.Snackbar` precedent) with an English, auto-clearing,
  non-persisted note; do NOT persist to transcript, do NOT alter the model-fed
  `"Error: ..."` text.
- Clear on next send/stop like `toolCallActive` (single-cancel-path convention).

Verify: `./gradlew :app:testDebugUnitTest --tests "*ToolFailureAffordanceTest*"`
Done: failed tool call ⇒ one transient visible note; success path emits nothing;
  no transcript/persistence side effects.

## Task 7 — Phase 53 polish trio

Files (executor confirms exact paths):
- (a) Selector-bar override indicator: `ChatScreen.kt` (~:410 selector bar +
  ~:829-854 tri-state control)
- (b) Fuente heading vs badge: Fuentes list render site (grep `Fuente` under `ui/`)
- (c) All-omitida struck rows/count: omitida render site + `AllSourcesSheet`
- `app/src/test/java/com/warped/ui/chat/Phase53PolishTrioTest.kt` (new)

Action (per locked decision 7, minimal per prior UI language):
- (a) Per-chat override-state indicator ON the selector bar surface (inherit/on/off
  distinguishable; read from existing `webOverride` tri-state, no new state).
- (b) Remove redundant Fuente heading where badge already labels it (one deletion,
  no restyle).
- (c) All-omitida turns render struck rows + count (honesty fix from UI review;
  same struck/disabled language as existing omitida rows).

Verify: `./gradlew :app:testDebugUnitTest --tests "*Phase53PolishTrioTest*"`
Done: (a) bar shows override state per chat; (b) no duplicate heading; (c) omitida
  rows struck with visible count.

---

## Global verify

- `./gradlew :app:assembleDebug`
- `./gradlew :app:testDebugUnitTest` (full suite green)

## Honest notes

- No adb available: on-device confirmation needed per item (thinking UX, image
  carry rendering, pause icon layout, new-chat navigation, snackbar timing,
  trio visuals). Unit tests cover logic/pass-through only.

## must_haves

- truths:
  - "Capable-model thinking toggle reaches engine conversation creation as ThinkingConfig"
  - "Remote chats carry recent images in history like local does"
  - "Source cards show snippet fallback when og:description is missing"
  - "Both download screens offer pause/resume identically"
  - "Toggling a model ON opens a new chat, old conversation preserved"
  - "Failed tool calls show a transient visible note"
  - "Selector bar shows override state; no redundant Fuente heading; omitida rows struck with count"
- artifacts:
  - "ThinkingConfigPassThroughTest", "RemoteImageCarryTest",
    "SourceCardSnippetFallbackTest", "ActiveDownloadPauseTest",
    "ModelActivationNewChatTest", "ToolFailureAffordanceTest",
    "Phase53PolishTrioTest"
- key_links:
  - "EngineManager → LiteRTLmEngine.createConversation(thinkingConfig)"
  - "Remote providers → shared K=3 carry rule"
  - "GroundedSource.snippet → card description fallback"
  - "ActiveDownloadContent → pause/resume in both usages"
