# Phase 1: Foundation & Remote Chat - Context

**Gathered:** 2026-04-30
**Status:** Ready for planning

## Phase Boundary

Establish the Android app foundation (Hilt DI, Compose navigation shell, Room database, Android Keystore security) and deliver remote chat with streaming token-by-token responses from OpenAI-compatible, Ollama, LM Studio, and custom endpoints — with full chat persistence and encrypted API key storage.

## Implementation Decisions

### Project Structure

- **D-01:** Single module (`app/`) initially — split to multi-module when complexity grows (YAGNI). Package structure follows Clean Architecture: `app/ui/`, `app/domain/`, `app/data/` with sub-packages per feature.
- **D-02:** `app/domain/` has zero Android dependencies. `app/data/` contains Room DAOs, Retrofit services, OkHttp configuration. `app/ui/` contains Compose screens and ViewModels.

### Navigation

- **D-03:** Bottom navigation bar with 3 tabs for MVP: Chat, Endpoints, Models (Models tab is a placeholder until Phase 2). Settings accessible via top-bar gear icon.
- **D-04:** Navigation Compose (`androidx.navigation:navigation-compose`) with type-safe arguments. No nested navigation graphs in v1.

### Domain Model

- **D-05:** Use the LLMProvider interface exactly as specified in the project brief — all providers implement the same contract, decoupling UI from backend.

```kotlin
interface LlmProvider {
    val type: ProviderType
    suspend fun listModels(): Result<List<ModelInfo>>
    fun chat(request: ChatRequest): Flow<ChatToken>
    suspend fun testConnection(): Result<ConnectionStatus>
}
```

- **D-06:** Domain data classes: `ChatRequest`, `ChatMessage`, `Role` (SYSTEM/USER/ASSISTANT), `ModelInfo`, `ProviderType`, `GenerationParameters`. These live in `app/domain/model/`.

### SSE Streaming (Remote Chat)

- **D-07:** Manual OkHttp SSE parsing with `flow {}` builder. Read chunks with `source.readUtf8Line()`, parse `data:` lines, emit tokens via `Flow<String>`. Use a line accumulator buffer to handle fragmented TCP packets — parse only on `\n\n` boundaries.
- **D-08:** OkHttp client with configurable timeout (default 30s connect, 120s read), connection pooling, and interceptor chain for auth (Bearer token injection).

### Room Schema

- **D-09:** Three core entities for Phase 1: `ConversationEntity`, `MessageEntity`, `RemoteEndpointEntity`. Use the entity definitions from the project brief. Room 2.7.x with KSP annotation processing.

```kotlin
// conversations: id, title, created_at, updated_at, provider_type, endpoint_id
// messages: id, conversation_id, role, content, token_count, created_at
// endpoints: id, name, url, api_type, encrypted_api_key_ref, created_at
```

- **D-10:** DAOs expose `Flow<List<T>>` for reactive observation. ViewModels collect with `collectAsStateWithLifecycle()`.

### Security

- **D-11:** API keys stored via Android Keystore + `EncryptedSharedPreferences` (from `androidx.security:security-crypto:1.1.0`). Keys never stored in Room or plaintext DataStore — Room stores only a key alias reference (`encrypted_api_key_ref`). In-memory key handling uses `CharArray` with zero-fill after use.
- **D-12:** Custom Timber logging tree that redacts API keys and sensitive prompt content from production logs.

### Error Handling

- **D-13:** Domain-layer sealed class for operation results: `sealed class ApiResult<T> { data class Success<T>(val data: T); data class Error<T>(val code: ErrorCode, val message: String) }`. ViewModels map errors to user-friendly messages in UiState.
- **D-14:** SSE disconnection surfaced as a clear "Connection lost" indicator in chat UI with retry affordance. HTTP errors (4xx, 5xx) shown as inline error messages.

### Build Order within Phase

- **D-15:** 1. Gradle project setup + Hilt DI + Compose theme → 2. Room database + entities + DAOs → 3. Domain models + LLMProvider interface → 4. Navigation shell (bottom bar + screen stubs) → 5. RemoteEndpointsScreen + endpoint CRUD → 6. OpenAI-compatible provider + SSE streaming → 7. ChatScreen + ChatViewModel + streaming UI → 8. Ollama/LMStudio providers → 9. Conversation history persistence → 10. Keystore encryption + API key management → 11. Custom provider support.

### Claude's Discretion

- **D-16:** Theme/manifest/AGP setup — standard Material 3 with dark mode support, single-activity architecture, `AndroidManifest.xml` with `android:largeHeap="true"` and `android:extractNativeLibs="false"` (preparation for Phase 2 native libs).
- **D-17:** Exact Compose component tree within ChatScreen — use existing LM Studio desktop patterns as reference: model selector at top, conversation in center (scrollable `LazyColumn`), input bar at bottom with send/stop buttons.
- **D-18:** Default SSE Content-Type check relaxed — some providers (Ollama, custom) may not set `text/event-stream` correctly. Parse based on content, not header.

## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Definition
- `.planning/PROJECT.md` — Full project scope, constraints, out-of-scope items
- `.planning/REQUIREMENTS.md` — v1 requirements with REQ-IDs (PROV-01..05, CHAT-01..05, PERS-01, PERS-02, SEC-01)
- `.planning/ROADMAP.md` §Phase 1 — Phase goal, success criteria, requirement mapping

### Technical Research
- `.planning/research/STACK.md` — Full technology stack with versions, rationale, confidence levels
- `.planning/research/ARCHITECTURE.md` — Component boundaries, data flow, module structure
- `.planning/research/FEATURES.md` — Feature landscape, table stakes vs differentiators
- `.planning/research/PITFALLS.md` — Top 5 project killers: SSE parsing (P2), API key storage (P5), thread safety, process death
- `.planning/research/SUMMARY.md` §2-5 — Recommended stack, architecture blueprint, critical pitfalls

### Domain Contracts (from user spec)
- Project brief §2 — LLMProvider interface contract (all provider types)
- Project brief §3 — Hugging Face API contract
- Project brief §8 — Data contract definitions (ChatRequest, ChatMessage, ModelInfo, GenerationParameters)
- Project brief §9 — LocalLlamaEngine interface (reference for Phase 2)

## Existing Code Insights

### Reusable Assets
- None yet — greenfield project

### Established Patterns
- Clean Architecture (domain/data/ui layers) — mandated by project constraints
- MVVM with ViewModel + StateFlow — standard Android pattern for Compose
- Repository pattern — one repository per data source

### Integration Points
- Phase 2 (Local Inference) will reuse the LLMProvider interface and ChatScreen UI — design provider abstraction to be swappable
- Phase 3 (Model Acquisition) will extend the Room schema with model entities
- Phase 4 (Parameters & Presets) will add a Presets table and extend ChatScreen settings panel

## Specific Ideas

- ChatScreen should closely match the LM Studio desktop experience: model selector dropdown at top, conversation area filling center, input bar pinned at bottom with animated send/stop button
- RemoteEndpointsScreen should show each endpoint as a card with name, URL, type badge, and "Test" button with loading spinner during connectivity check
- Token streaming should animate tokens appearing one-by-one with a subtle fade-in effect (no jank — use `LazyColumn` with stable keys per message)
- Error messages should be user-friendly: "Can't connect to server. Check the URL and try again." — not raw HTTP error codes

## Deferred Ideas

- Multi-module Gradle structure — defer until after Phase 2 when native module is needed
- Mermaid/PNG architecture diagrams for documentation — nice-to-have, not in Phase 1 scope
- Sound/vibration feedback on token completion — deferred, focus on visual streaming

None — discussion stayed within phase scope

---
*Phase: 1-Foundation & Remote Chat*
*Context gathered: 2026-04-30*
