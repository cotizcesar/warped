# Roadmap: Warped v1.8 — LiteRT Update, Bugfix Round & Recommended Models

**Created:** 2026-06-05
**Phases:** 5 (continuing from Phase 34)
**Total requirements:** 30

---

## Phase 35: LiteRT-LM Engine Upgrade & Conversation Context

**Goal:** Upgrade the LiteRT-LM library to the latest stable release and fix the multi-turn conversation history bug so the engine sees full message context.

**Requirements:** LRT-01, LRT-02, LRT-03

**Depends on:** Nothing (standalone)

**Success criteria:**
1. `libs.versions.toml` references the latest stable LiteRT-LM release and Gradle sync succeeds
2. App compiles without errors (`./gradlew assembleDebug`) — any API breakage from the new version is adapted
3. All existing unit tests pass (`./gradlew :app:testDebugUnitTest`)
4. In a multi-turn chat with a LiteRT-LM model (e.g. Deepseek R1), the assistant's reply to message N references content from messages 1..N-1
5. Manual smoke test: load a local model, send 3 sequential user messages, verify each reply is contextual

**Plan structure:**
- Plan 1: Determine latest stable LiteRT-LM version (check Maven Central / GitHub releases), bump `libs.versions.toml`, Gradle sync
- Plan 2: Fix compilation errors from API changes (if any) — update `LiteRTLmProvider`, `LocalLlmProvider`, JNI bridge
- Plan 3: Fix `sendContentsWithRetry()` in `LiteRTLmProvider` — keep the `Conversation` alive across `chat()` calls, only recreate when dead. Verify multi-turn chat.

---

## Phase 36: Hugging Face Model Browser Bugfixes

**Goal:** Fix the model browser so all sibling files appear, the UI matches the main list, downloads are cancellable, search text persists, navigation works after download, parser is safe from corrupted metadata, and downloads survive screen changes.

**Requirements:** HF-01, HF-02, HF-03, HF-04, HF-05, HF-06, HF-07, HF-08, HF-09

**Depends on:** Nothing (standalone)

**Success criteria:**
1. Model detail screen shows every `.litertlm` sibling file regardless of `size=0` or missing `lfs` field — LFS pointer files appear
2. Model detail file cards visually match the main search list (Storage icon, chips, border, `primaryContainer` colors)
3. Tapping cancel on an in-progress download stops the download, removes any partial file, and shows a confirmation
4. Rotating the device or pressing back from a detail screen preserves the search query text
5. After a successful download, the app auto-navigates to the Models & Endpoints list
6. Malformed/corrupted model metadata does not crash the parser or produce false "out of memory" errors
7. Starting a download and navigating away from the browser does not pause or kill the download — it completes in the background
8. Models & Endpoints list shows in-flight downloads with a live progress bar
9. Tapping delete on an incomplete download removes the partial file and the entry from the list

**Plan structure:**
- Plan 1: Fix `HuggingFaceViewModel.selectModel()` and `loadCompatibility()` filters — match `.litertlm` siblings regardless of `size=0` or missing LFS info (HF-01)
- Plan 2: Redesign `SiblingFileCard` in `HuggingFaceScreen.kt` to mirror `ModelSearchResultCard` design — Storage icon, chips, `primaryContainer` (HF-02)
- Plan 3: Add cancel-download button to detail screen — `onCancelDownload` callback, calls `ModelDownloadManager.pause()`, removes partial file (HF-03)
- Plan 4: Persist search query in `HuggingFaceViewModel` via `onSearchTextChanged()` + DataStore — survives rotation and back-navigation (HF-04)
- Plan 5: Add auto-navigate `LaunchedEffect` in `HuggingFaceScreen` to push `Screen.Models` on download success; harden `GgufMetadataParser` with bounds checking on `keyLen`/`strLen` (HF-05, HF-06)
- Plan 6: Move download coroutines to application scope in `ModelDownloadManager` so they survive screen changes; emit progress to a shared `downloadStates` Flow (HF-07, HF-08)
- Plan 7: Add cancel/delete UI to `ModelsScreen` for incomplete downloads — observes `downloadStates`, shows progress, exposes cancel/delete (HF-09)

---

## Phase 37: Chat UI Redesign & Model Selector

**Goal:** Redesign the chat screen to a ChatGPT/Claude style with a pill input bar and inline model selector, integrate network endpoints into the picker, add a loading indicator while a local model loads, and tighten the Models & Endpoints title.

**Requirements:** CHAT-01, CHAT-02, CHAT-03, CHAT-04, CHAT-05, CHAT-06, CHAT-07, CHAT-08

**Depends on:** Nothing (standalone — touches Chat and Models UI only)

**Success criteria:**
1. Chat input bar is a rounded pill with a transparent underline; send uses `Icons.AutoMirrored.Filled.Send`, stop uses `Icons.Filled.Stop`
2. Chat screen has no TopAppBar — model picker sits inline above the messages; drawer is reachable via swipe
3. Bottom navigation bar shows icons only — no text labels
4. Tapping the model icon near the chat input opens a bottom-sheet picker that lists both local models and network endpoints
5. Models & Endpoints screen has a compact title with minimal vertical padding — wasted top space removed
6. Selecting a local model for chat shows a "Cargando modelo" loading indicator in the chat while the model loads asynchronously into RAM
7. After a network endpoint is selected, its available models are fetched and shown in the picker
8. Sending a message while the local model is still loading is disabled (or queued, depending on UX decision)

**Plan structure:**
- Plan 1: Redesign `ChatInputBar` to a rounded pill with Material send/stop icons; remove TopAppBar from `ChatScreen`; move `ModelSelector` inline above the `LazyColumn` (CHAT-01, CHAT-02)
- Plan 2: Simplify `NavGraph` bottom navigation to icons-only (CHAT-03)
- Plan 3: Refactor `ModelSelector` into a `ModalBottomSheet` that lists both local models and network endpoints from `ChatUiState.endpoints` (CHAT-04, CHAT-05)
- Plan 4: Compact the `ModelsScreen` TopAppBar — remove wasted vertical space (CHAT-06)
- Plan 5: Add async pre-load in `ModelsViewModel` / `ChatViewModel` — `preloadLocalModel()` shows a loading state in `ChatUiState`; `ChatScreen` renders a `CircularProgressIndicator` while loading (CHAT-07)
- Plan 6: Wire `provider.listModels()` from the selected endpoint into the model picker — `ChatViewModel.fetchEndpointModels()` populates the bottom sheet (CHAT-08)

---

## Phase 38: Endpoint CRUD & Provider Refactor

**Goal:** Make network endpoint management usable (real edit/delete with immediate UI feedback), align with the LM Studio native v1 REST API, drop non-LM-Studio provider types, allow HTTP cleartext to LAN IPs, and normalize user-entered URLs.

**Requirements:** ENDPT-01, ENDPT-02, ENDPT-03, ENDPT-04, ENDPT-05, ENDPT-06, ENDPT-07

**Depends on:** Nothing (standalone — touches Endpoint and Provider layers)

**Success criteria:**
1. User can edit an existing network endpoint from the Models & Endpoints list — name, URL, and API key changes save correctly
2. Tapping delete on an endpoint card removes it from the list immediately (optimistic UI) and the DB delete succeeds
3. Warped talks to LM Studio's native v1 REST API (`POST /api/v1/chat`, `GET /api/v1/models`) — no longer uses the OpenAI-compatible `/v1/chat/completions` endpoint
4. Endpoint form dropdown offers only `LM_STUDIO` as the provider type — OpenAI, Anthropic, Ollama, and Custom are removed
5. Entering a URL like `192.168.1.100:1234` (no scheme, no trailing slash) is normalized to `http://192.168.1.100:1234/` before the provider is created
6. Network security config allows HTTP cleartext to LAN ranges (192.168.0.0/16, 10.0.0.0/8, 172.16.0.0/12) — local-network LM Studio and Ollama servers work without TLS
7. Chatting with a remote LM Studio endpoint streams tokens correctly — no 400 "input required" errors

**Plan structure:**
- Plan 1: Fix `EndpointRepositoryImpl.deleteEndpoint()` — remove the silent `getById ?: return`, validate `endpointId != 0L`, call `endpointDao.deleteById` and `apiKeyStore.deleteKey` directly; add immediate UI remove in `ModelsViewModel.deleteEndpoint` via optimistic filter (ENDPT-02, ENDPT-03)
- Plan 2: Add edit endpoint form to `ModelsScreen` — tap card opens `EndpointForm` pre-filled with endpoint data; `EndpointsViewModel.saveEndpointEdit()` updates DB and API key store (ENDPT-01)
- Plan 3: Create `LmStudioApi` Retrofit interface with native v1 paths (`/api/v1/chat`, `/api/v1/models`); rewrite `LMStudioProvider` to use it; remove all OpenAI/Anthropic/Ollama/Custom provider imports (ENDPT-04)
- Plan 4: Strip `EndpointForm` provider-type dropdown to a single `LM_STUDIO` option; update `ModelsUiState` enum; remove now-unused provider constructors from `ProviderRouter` (ENDPT-05)
- Plan 5: Add URL normalization helper — `normalizeEndpointUrl(raw)` adds `http://` if scheme missing, appends `/` if path empty; apply in `ModelsViewModel.saveEndpoint/saveEndpointEdit/fetchEndpointModels` and `EndpointsViewModel.saveEndpoint` (ENDPT-07)
- Plan 6: Update `network_security_config.xml` to allow cleartext for LAN IP ranges; verify LM Studio on `http://192.168.x.x:1234` works end-to-end (ENDPT-06)
- Plan 7: Verify chat-to-LM-Studio streaming round-trip — request body matches LM Studio v1 spec, no 400 errors (ENDPT-04 acceptance)

---

## Phase 39: Recommended Models Curated List

**Goal:** Ship a hand-curated "Recommended" section at the top of the model browser with 5–10 trusted `.litertlm` models, each with name, size, why-recommended, and a one-tap download entry point.

**Requirements:** REC-01, REC-02, REC-03

**Depends on:** Nothing (standalone)

**Success criteria:**
1. A "Recommended" section appears at the top of the Hugging Face model browser, above the search bar / search results
2. The list shows 5–10 entries (hand-picked, no API scraping), each with: name, file size, one-line "why recommended" description
3. Tapping a recommended model opens its Hugging Face detail page so the user can download it with one tap
4. The list is shipped as a static asset (Kotlin constant list or `assets/recommended_models.json`) — bundled with the app, no network calls
5. Adding or changing a recommended model requires a new app release — the list is versioned with the app

**Plan structure:**
- Plan 1: Define `RecommendedModel` domain model — `id`, `name`, `huggingFaceId`, `litertlmFileName`, `sizeBytes`, `reason`; build the curated list of 5–10 entries as a `val` constant in `domain/model/`
- Plan 2: Add `RecommendedModelsSection` composable in `HuggingFaceScreen` — renders above the search input with the curated list; tapping a card navigates to the model's HF detail
- Plan 3: Update `HuggingFaceUiState` to include `recommendedModels: List<RecommendedModel>`; wire the static list from the constant into the UI state

---

## Phase Summary

| # | Phase | Goal | Reqs | Success Criteria |
|---|-------|------|------|------------------|
| 35 | LiteRT-LM Engine Upgrade & Conversation Context | Bump library, fix multi-turn history | LRT-01..03 | 5 |
| 36 | Hugging Face Model Browser Bugfixes | Show all files, cancel, persist search, background downloads | HF-01..09 | 9 |
| 37 | Chat UI Redesign & Model Selector | Pill input, inline selector, loading indicator, endpoints in picker | CHAT-01..08 | 8 |
| 38 | Endpoint CRUD & Provider Refactor | Edit/delete endpoints, LM Studio v1, drop non-LM, LAN cleartext, URL normalize | ENDPT-01..07 | 7 |
| 39 | Recommended Models Curated List | Static curated `.litertlm` list at top of browser | REC-01..03 | 3 |

**Total: 5 phases, 30 requirements, 32 success criteria**

---
*Roadmap created: 2026-06-05*
