# Architecture Research: v2.2 Simplificación + Web Grounding

**Domain:** Android LLM chat app (Warped) — Clean Architecture, MVVM, Hilt
**Researched:** 2026-09-28
**Confidence:** HIGH (verified against live codebase: ChatViewModel, LlmModelHelper, LiteRtLlmHelper, LmStudioHelper, LmStudioToolLoop, ToolGating, AdvancedPreferences, SyntaxTheme, SyntaxHighlighterImpl, HuggingFaceAuthInterceptor)

## Standard Architecture

### System Overview (current v2.1 → v2.2 delta)

```
┌─────────────────────────────────────────────────────────────────┐
│                          UI LAYER (Compose)                      │
│  ┌──────────────┐ ┌──────────────┐ ┌───────────┐ ┌────────────┐  │
│  │ ChatScreen / │ │ Settings     │ │ DELETE:   │ │ DELETE:    │  │
│  │ ChatViewModel│ │ (HF token    │ │ SkillChips│ │ HF search  │  │
│  │ (sub-states) │ │  field,theme │ │ Row       │ │ screens/VM │  │
│  │              │ │  picker)     │ │           │ │            │  │
│  └──────┬───────┘ └──────┬───────┘ └───────────┘ └────────────┘  │
│         │                │                                       │
├─────────┼────────────────┼───────────────────────────────────────┤
│         │      DOMAIN LAYER (pure Kotlin)                        │
│         │   ┌──────────────────┐  ┌──────────────┐               │
│         │   │ LlmModelHelper   │  │ ChatRequest /│               │
│         │   │ (KEYSTONE —      │  │ StreamToken  │               │
│         │   │  UNCHANGED)      │  │ (+ToolStatus │               │
│         │   └────────┬─────────┘  │  ToolCompleted│              │
│         │            │            │  → SIMPLIFY) │               │
│         │   ┌────────┴─────────┐  └──────────────┘               │
│         │   │ NEW: WebFetcher  │  ┌──────────────┐               │
│         │   │ + WebGrounding   │  │ DELETE:      │               │
│         │   │ Policy/heuristic │  │ Skill domain │               │
│         │   └────────┬─────────┘  │ (SkillIds,   │               │
│         │            │            │  ToolExecutor│               │
│         │            │            │  SkillRepo)  │               │
│         │            │            └──────────────┘               │
├─────────┼────────────┼──────────────────────────────────────────┤
│         │   DATA LAYER                                          │
│         │  ┌──────────────┐ ┌──────────────┐ ┌───────────────┐   │
│         │  │LiteRTLmProvider│ │LMStudioProvider│ │DELETE:      │   │
│         │  │+ LiteRtLlmHelper│ │+ LmStudioHelper│ │LocalToolExec│   │
│         │  │(strip tools    │ │+ LmStudioToolLoop│ │ToolGating  │   │
│         │  │ wiring)        │ │(strip loop)    │ │SkillPrefs/ │   │
│         │  └──────────────┘ └──────────────┘ │Repo/Descr. │   │
│         │  ┌──────────────┐ ┌──────────────┐ └───────────────┘   │
│         │  │NEW: WebFetch │ │SyntaxHighlighter│ ┌────────────┐   │
│         │  │OkHttp client │ │Impl (FIX: theme │ │DELETE: HF  │   │
│         │  │+ HtmlToText  │ │ param)         │ │AuthInterc. │   │
│         │  └──────────────┘ └──────────────┘ │HF Api/Repo │   │
│         │  ┌──────────────┐ ┌──────────────┐ └────────────┘   │
│         │  │Room (keep    │ │AdvancedPrefs │                    │
│         │  │ Role.TOOL?*) │ │(keep theme,  │                    │
│         │  └──────────────┘ │ drop nothing)│                    │
└─────────────────────────────────────────────────────────────────┘
* Decision point — see § Removal Completeness.
```

### Component Responsibilities

| Component | v2.2 Verdict | Responsibility | Typical Implementation |
|-----------|--------------|----------------|------------------------|
| `LlmModelHelper` (domain/llm) | **UNCHANGED (keystone)** | 5-lifecycle + `runInference` contract; every feature speaks through it | Keep interface byte-identical |
| `LiteRtLlmHelper` | **MODIFIED (strip)** | Remove `ConversationConfig.tools` wiring, `clearToolsDegraded`, ToolStatus/ToolCompleted passthrough notes | Delete tool branches, keep think-strip + flowOn |
| `LmStudioHelper` | **MODIFIED (strip)** | Remove `SkillRepository`/`ModelAllowlistRepository`/`ToolExecutor` ctor deps, loop-vs-native routing | Plain native `/api/v1/chat` path only |
| `LmStudioToolLoop` (+ `MAX_TOOL_ROUNDS`) | **DELETE** | Entire parallel `/v1/chat/completions` tools[] loop | Delete file + DTOs `OpenAiTool/OpenAiFunctionDef/OpenAiNonStreaming*` if unused elsewhere |
| `LiteRTLmProvider` | **MODIFIED (strip)** | Remove `ConversationConfig` ToolSets + `automaticToolCalling` wiring | Plain chat factory |
| `ChatViewModel` | **MODIFIED (heavy)** | Remove skill collectors/gating/notice/tool persistence; ADD pre-inference web-grounding step | Slimmest diff: keep sub-state owners, single-collect, stop discipline untouched |
| `ChatUiState` / `ChatTranscriptState` / `ChatInputState` | **MODIFIED (strip)** | Remove `skillEnabled`, `toolCallActive`, `activeToolError`, `showNoToolSupportNotice` | Keep `codeTheme`, streaming, error fields |
| `SkillChipsRow.kt`, `toolResultContent` | **DELETE** | Chips UI + tool transcript rendering | Delete file + call sites in ChatScreen/MessageBubble |
| `ToolGating`, `ToolHistory`, `ToolText` | **DELETE** | Gating truth + transcript helpers | Delete `data/skills/` tool files |
| `SkillRepository`, `SkillPreferences`, `SkillRepositoryImpl`, `SkillDescriptors`, `*Skill.kt` (×3), `LocalToolExecutor`, `ExpressionEvaluator` | **DELETE** | Full skills data+domain surface | Delete + `SkillsModule.kt` DI module |
| `HuggingFaceApi`, `HuggingFaceRepository(Impl)`, `HuggingFaceAuthInterceptor`, `HuggingFaceModule` | **DELETE** | HF search + token header injection | Delete; keep download path only if allowlist-direct download needs OkHttp (no auth) |
| `HuggingFaceViewModel/Screen`, HF nav routes | **DELETE** | Search UI | Delete + remove from NavGraph/Screen sealed class |
| `SettingsViewModel/UiState/Screen` HF section | **MODIFIED (strip)** | Remove `hfToken/hasHfToken`, save/remove fns | Keep theme picker + unrelated settings |
| `ApiKeyStore` HF methods | **MODIFIED (strip)** | Remove `get/store/deleteHuggingFaceToken` | Keep other keys (endpoint API keys stay) |
| `HelpScreen` §7 HF token | **MODIFIED (strip)** | Remove token help text | One-paragraph delete |
| `Role.TOOL` persistence | **MODIFIED (decision)** | v2.1 wrote `role=TOOL` rows (ChatViewModel:514,597,1140 + MessageBubble:62 filter). Options: (a) stop writing new rows, keep reader for history compat; (b) full delete + migration dropping TOOL rows | **Recommend (a)**: cheapest, no migration; hide TOOL rows in UI or render as plain context. Full delete requires Room migration + history rewrite — overkill for a simplification milestone |
| **NEW `WebFetcher`** (data/) | **ADD** | Direct OkHttp GET → HTML→text → cap | `@Singleton`, `suspend fun fetch(url): Result<FetchedPage>` on `Dispatchers.IO`, own OkHttpClient (timeouts 10s/15s, max 2MB body, redirects ≤5) |
| **NEW `WebGroundingPolicy`** (domain/) | **ADD** | Pure heuristic: should-fetch? URL extraction, offline veto | JVM-testable object: regex URL extract (max 3 URLs), recency/knowledge-cutoff keywords (`latest|today|current price|news`), `isOffline` input — no Android types |
| **NEW `ConnectivityGate`** (data/, thin) | **ADD** | `ConnectivityManager.getNetworkCapabilities` → has `NET_CAPABILITY_INTERNET` + `VALIDATED` | Single `@Singleton` wrapper, injectable/fakeable |
| `SyntaxHighlighterImpl` | **FIX (root cause found)** | Line 44 hardcodes `.theme(SyntaxThemes.monokai())` — engine ignores `SyntaxTheme` entirely. That's why only Monokai applies | Thread selected `SyntaxTheme` through `highlight(code, language, theme)` or constructor-inject current theme; map domain `TokenType→Color` (already exists) instead of Highlights' built-in theme |
| `AdvancedPreferences.syntaxTheme` | **UNCHANGED** | DataStore `code_theme` key + legacy migration already correct (MONOKAI/DRACULA/NORD/ONE_DARK/GITHUB/SOLARIZED_DARK → keys) | No change needed; bug is downstream in the impl, not the flow |
| `MarkdownText` / `CodeBlock` / `MessageBubble` theme params | **MODIFIED (verify)** | They already accept `codeTheme: SyntaxTheme` with MONOKAI default — chain is plumbed | After impl fix, verify no call site hardcodes `SyntaxTheme.MONOKAI` (HuggingFaceScreen:309 does — dies with HF deletion; check ChatScreen/MessageBubble pass-through) |

## Recommended Project Structure

```
app/src/main/java/com/warped/
├── domain/
│   ├── llm/                  # UNCHANGED — LlmModelHelper, ChatRequest, StreamToken
│   ├── grounding/            # NEW — WebGroundingPolicy (pure), FetchedPage model
│   ├── highlighting/         # UNCHANGED interface — SyntaxHighlighter (+theme param)
│   └── model/                # MODIFIED — SyntaxTheme (keep), Role (keep TOOL for compat)
├── data/
│   ├── grounding/            # NEW — WebFetcherImpl, HtmlToText, ConnectivityGate
│   ├── local/inference/      # MODIFIED — LiteRTLmProvider/LiteRtLlmHelper (tools stripped)
│   ├── remote/provider/      # MODIFIED — LmStudioHelper/LMStudioProvider (loop stripped)
│   ├── highlighting/         # FIX — SyntaxHighlighterImpl theme threading
│   └── skills/               # DELETE entire package
│   └── remote/
│       ├── api/HuggingFaceApi.kt        # DELETE
│       ├── network/HuggingFaceAuthInterceptor.kt  # DELETE
│       └── repository/HuggingFaceRepositoryImpl.kt # DELETE
├── di/
│   ├── SkillsModule.kt       # DELETE
│   ├── HuggingFaceModule.kt  # DELETE
│   └── GroundingModule.kt    # NEW — binds WebFetcher, ConnectivityGate
├── ui/
│   ├── chat/                 # MODIFIED — ChatViewModel (grounding hook), strip skill UI
│   │   └── components/
│   │       ├── SkillChipsRow.kt  # DELETE
│   │       ├── MarkdownText.kt   # VERIFY theme pass-through
│   │       └── CodeBlock.kt      # VERIFY theme pass-through
│   ├── huggingface/          # DELETE package
│   └── settings/             # MODIFIED — strip HF token section
```

### Structure Rationale

- **`domain/grounding/`:** heuristic must be JVM-testable with zero Android imports (same discipline as `ToolGating` was — pure object, and its testability was the one thing worth preserving from the deleted code).
- **`data/grounding/`:** OkHttp + Jsoup-free HTML strip lives behind an interface so tests fake fetch results; mirrors `SyntaxHighlighter` domain-interface/data-impl split.
- **DELETE whole packages, not methods:** `data/skills/`, `ui/huggingface/` are cohesive — partial deletion leaves dead DI bindings that break Hilt compilation. Package delete + module delete is atomic.
- **`GroundingModule` separate:** keeps network timeouts/fetch caps in one Hilt module; trivially removable later if grounding is cut.

## Architectural Patterns

### Pattern 1: Pre-inference Grounding Hook in ChatViewModel (NOT inside helpers)

**What:** `sendMessage()` gains one suspend step between `helper.initialize()` and `helper.runInference()`: check connectivity → policy.shouldFetch → fetch (≤3 URLs, IO dispatcher, timeout) → prepend fetched text as marked context message → proceed with unchanged `runInference` call.
**When to use:** Always for v2.2 — grounding is provider-agnostic (works for LiteRT + LM Studio identically).
**Trade-offs:** + zero changes to `LlmModelHelper` interface and both helpers; + single place to enforce offline fallback; − adds ~200-500ms latency on fetch turns (mitigate: cap 3 URLs, 2MB, show "Fetching web context…" transient state).

**Example:**
```kotlin
// ChatViewModel.sendMessage — inserted after helper.initialize(modelId), before ChatRequest build
val groundedHistory: List<ChatMessage> = if (groundingPolicy.shouldFetch(text) && connectivityGate.hasInternet()) {
    updateTranscript { it.copy(isFetchingWeb = true) }  // transient, cleared after
    try {
        val pages = webFetcher.fetchRelevant(text.take(2000))  // IO, max 3 URLs
        if (pages.isNotEmpty()) transcriptMessages + ChatMessage(
            role = Role.SYSTEM,  // or USER-marked; never TOOL
            content = buildString {
                appendLine("[WEB CONTEXT — untrusted, fetched ${pages.size} page(s); may be stale or biased. Prefer model knowledge on conflict unless user asks for fresh info.]")
                pages.forEach { appendLine("--- ${it.url} ---"); appendLine(it.text.take(4000)) }
            }
        ) else transcriptMessages
    } finally { updateTranscript { it.copy(isFetchingWeb = false) } }
} else transcriptMessages
val request = ChatRequest(messages = groundedHistory, parameters = ..., images = ..., audioBytes = ...)
```

### Pattern 2: Trust-Boundary Marking (inherited from v2.1 tool-loop discipline)

**What:** Fetched content is untrusted third-party input. Mark it with a delimiter + provenance header (`[WEB CONTEXT]`, source URL, fetch time) inside a `Role.SYSTEM` (or clearly-labeled user) message; never silently concatenate into the user message.
**When to use:** Every grounded turn.
**Trade-offs:** + prevents prompt-injection confusion between user intent and web text; + visible in Room transcript for audit; − consumes context tokens (~4k chars cap per page recommended).

### Pattern 3: Removal-First, Interface-Pinned Refactor

**What:** Delete skills/HF code first while `LlmModelHelper` stays frozen; only then add grounding. Each removal phase ends with a grep gate (zero references) before the next phase starts.
**When to use:** All v2.2 removal work.
**Trade-offs:** + compile errors surface dead references immediately (Hilt will fail fast on orphan bindings); − intermediate states don't compile (phases must be atomic commits, not shippable slices).

### Pattern 4: Theme-as-Parameter (SyntaxTheme fix)

**What:** Change `SyntaxHighlighter.highlight(code, language)` → `highlight(code, language, theme: SyntaxTheme = MONOKAI)`; inside, build the Highlights engine theme from `theme.darkVariant/lightVariant` (via existing `TypeMapper`) instead of `SyntaxThemes.monokai()`. Select variant by system dark-mode (`isSystemInDarkTheme()` at call site, passed down or read in impl via injected context).
**When to use:** The v2.2 theme fix.
**Trade-offs:** + minimal blast radius (interface + 1 impl + pass-through params already exist); − Highlights' `SyntaxThemes` built-ins (one_dark/github/dracula) may not match domain palettes exactly — prefer mapping domain colors (source of truth since v1.6) over switching to built-ins.

## Data Flow

### Request Flow (grounded turn, either backend)

```
User text
  ↓
ChatViewModel.sendMessage
  ↓ (1) persist USER row (Room, unchanged)
  ↓ (2) NEW: connectivityGate.hasInternet()? ──NO──→ skip to (4), no banner (silent offline per spec)
  ↓ YES                                    ("sin internet → solo modelo, sin web")
  ↓ (3) NEW: groundingPolicy.shouldFetch(text)? ──NO──→ (4)
  ↓ YES: webFetcher.fetch (IO, ≤3 URLs, ≤2MB, ≤15s) → SYSTEM [WEB CONTEXT] message
  ↓ (4) helper.initialize(modelId) → helper.runInference(ChatRequest(groundedHistory))
  ↓ (5) StreamToken.Delta collect → transcript (UNCHANGED single-collect/shareIn discipline)
  ↓ (6) persist ASSISTANT row (Room, UNCHANGED — no TOOL rows written anymore)
```

### State Management

```
_transcript / _input / _connection sub-states (48-01 discipline — UNCHANGED)
  + _transcript gains transient isFetchingWeb: Boolean (cleared in finally)
  − _input loses skillEnabled: Map<String,Boolean>
  − _transcript loses toolCallActive, activeToolError, showNoToolSupportNotice
uiState combine-shim recomputes automatically (no direct writes — same rule)
```

### Key Data Flows

1. **Grounded chat (new):** UI → VM grounding hook → WebFetcher (network) → ChatRequest augmentation → helper → tokens → Room. Only NEW network edge in v2.2; all else reused.
2. **Offline fallback (new):** ConnectivityGate false → hook skipped silently → identical flow to v2.1 plain chat. No error banner (spec: "sin internet → solo modelo").
3. **Theme selection (fixed):** Settings picker → `AdvancedPreferences.setSyntaxTheme` → `syntaxTheme` Flow → ChatViewModel collector → `ChatUiState.codeTheme` → `MarkdownText`/`CodeBlock(syntaxTheme)` → `SyntaxHighlighter.highlight(code, lang, theme)` (FIXED impl maps domain colors). Previously the last hop ignored `theme`.
4. **Deletion flows (removed):** Skill toggle → DataStore; HF search → API → download; tool loop → executor → TOOL rows. All gone; no replacement flows.

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| Current (single-user mobile) | No scaling work. Fetch caps (3 URLs/2MB/4k chars) bound memory + latency adequately. |
| Larger context models | Raise per-page char cap via `ModelAllowlistRepository` capabilities (same pattern tools used for gating — reuse the allowlist read path, not the gating logic). |
| Multi-turn grounding | Cache `FetchedPage` by URL in-memory (5-min TTL) to avoid re-fetch on follow-ups. Explicitly OUT of v2.2 scope — add only if latency complaints. |

### Scaling Priorities

1. **First bottleneck:** Fetch latency on mobile networks (~1-3s for 3 pages). Mitigation already in design: parallel fetch with `async`, 15s total timeout, progressive UI (`isFetchingWeb` spinner).
2. **Second bottleneck:** Context-window pressure from injected pages. Mitigation: 4k-char/page cap + max 3 pages ≈ 3k tokens — safe for all allowlisted models.

## Anti-Patterns

### Anti-Pattern 1: Grounding INSIDE LlmModelHelper impls

**What people do:** Add fetch logic to `LiteRtLlmHelper.runInference` and `LmStudioHelper.runInference` separately.
**Why it's wrong:** Duplicates heuristic/offline/fetch code across backends; future backends re-implement; helpers lose single-responsibility (inference only).
**Do this instead:** One hook in `ChatViewModel.sendMessage` before `runInference` — both backends inherit it free.

### Anti-Pattern 2: Raw OkHttp in ViewModel

**What people do:** `OkHttpClient().newCall()` directly in `ChatViewModel` for "simplicity, zero new files".
**Why it's wrong:** Untestable, timeouts/caps scattered, violates Clean Architecture (VM → network), can't fake in unit tests.
**Do this instead:** `domain/grounding` policy (pure) + `data/grounding` fetcher (injected interface) — same seam the codebase already uses for highlighting.

### Anti-Pattern 3: Partial skills deletion ("just hide the chips")

**What people do:** Set `SkillChipsRow` visibility to false, leave ToolSets/ToolLoop/Gating wired.
**Why it's wrong:** Dead code still compiles into the graph (R8 keeps grow, Hilt bindings linger), `ConversationConfig.tools` still alters local inference behavior, remote loop still sends `tools[]` — the exact surface v2.2 wants gone. Next reader can't tell what's live.
**Do this instead:** Package-level delete + grep gate (zero hits for `SkillIds|ToolGating|SkillRepository|ToolExecutor|LmStudioToolLoop|MAX_TOOL_ROUNDS`).

### Anti-Pattern 4: Switching Highlights to built-in themes as the "fix"

**What people do:** Replace `SyntaxThemes.monokai()` with `SyntaxThemes.oneDark()` etc. per selection.
**Why it's wrong:** Domain `SyntaxTheme` palettes (v1.6, user-visible in Settings swatches) diverge from Highlights built-ins — swatch preview and rendered code would disagree.
**Do this instead:** Map domain `darkVariant/lightVariant` maps through `TypeMapper` (already the coloring path); built-ins only as fallback reference.

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Arbitrary web pages (NEW) | Direct OkHttp GET, no API keys, browser UA reuse | Reuse the HF interceptor's browser-UA trick (`HuggingFaceAuthInterceptor.USER_AGENT`) — many sites return 401/403 to default OkHttp UA. No auth headers ever. Size-cap via `ResponseBody.source().read(limit)` + redirect cap. |
| Hugging Face (reduced) | Static `model_allowlist.json` + direct file GET, no token | Interceptor deleted; plain shared OkHttp client suffices. Gated-model support explicitly dropped (accepted scope loss). |
| LM Studio server (slimmed) | Native `/api/v1/chat` only; `/v1/chat/completions` loop deleted | `LMStudioProvider.chatCompletionsWithTools` + `handleSseEvent` tool branches deleted; SSE plain path untouched. `LmStudioModelCache.trainedForToolUse` read in `supportsRemoteTools` dies with ToolGating. |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| ChatViewModel ↔ WebFetcher | `suspend fetch` on IO, injected interface | VM never touches OkHttp/ConnectivityManager directly; both behind domain/data seams. Cancel-safe: fetch inside `generationJob` so Stop cancels it (same discipline as inference). |
| ChatViewModel ↔ LlmModelHelper | UNCHANGED `initialize`/`runInference`/`stopResponse` | Grounding invisible to helpers — biggest risk reducer in the milestone. 46-01 single-collect + activeHelper-stop discipline untouched. |
| Settings → DataStore → Chat | UNCHANGED flow, HF keys removed | `AdvancedPreferences.syntaxTheme` + `codeFontScale` survive; `ApiKeyStore` HF methods deleted (Keystore entries orphaned — harmless, or one-line cleanup delete on upgrade). |
| Room transcript | Stop writing TOOL rows; reader tolerant | `Role.TOOL` enum value + `ToolHistory` helpers: keep enum (history compat), delete helpers; `MessageBubble` TOOL branch → drop or render-as-plain. No migration needed under option (a). |

## Suggested Build Order (removals before additions — dependency-pinned)

1. **Phase A — Skills deletion (local + shared).** Delete `data/skills/*`, `domain/skills/*`, `di/SkillsModule.kt`; strip `LiteRTLmProvider` ToolSets, `LiteRtLlmHelper` degraded-wedge, `ChatViewModel` skill injection/collector/`setSkillEnabled`/gating blocks/persistence branches, `ChatUiState` skill fields, `SkillChipsRow` + screen wiring. Gate: `grep SkillIds|SkillRepository|ToolGating|ToolExecutor|LocalToolExecutor|automaticToolCalling|ConversationConfig.tools → 0 hits`.
2. **Phase B — Remote tool-loop deletion.** Delete `LmStudioToolLoop.kt`, strip `LmStudioHelper` skill ctor deps + routing, `LMStudioProvider.chatCompletionsWithTools`, OpenAI tool DTOs if orphaned. Gate: `grep LmStudioToolLoop|MAX_TOOL_ROUNDS|chatCompletionsWithTools|OpenAiTool|tools\[\] → 0 hits` (excluding historical docs).
3. **Phase C — HF surface deletion.** Delete `ui/huggingface/*`, `HuggingFaceApi/Repository(Impl)/AuthInterceptor/Module`, Settings HF section, `ApiKeyStore` HF methods, HelpScreen §7, NavGraph HF routes. Gate: `grep HuggingFace|huggingface|hfToken|hasHfToken|HuggingFaceToken → 0 hits` in `app/src/main`. Keep allowlist + direct-download path compiling.
4. **Phase D — Web grounding addition.** Add `domain/grounding` policy + `data/grounding` fetcher/gate + `GroundingModule`; hook into `ChatViewModel.sendMessage`; transient `isFetchingWeb` state. Depends on A–C (no dead `Role.TOOL` writers to confuse the new SYSTEM-context message with; no skill chips competing for pre-inference UI).
5. **Phase E — SyntaxTheme fix.** Thread theme into `SyntaxHighlighterImpl` (replace hardcoded `monokai()`), verify pass-through call sites, delete stray `SyntaxTheme.MONOKAI` hardcodes. Independent of A–D — can run parallel with any phase, but keep it last so theme verification isn't invalidated by file deletions (e.g. HuggingFaceScreen:309 hardcoded MONOKAI dies in Phase C).

**Why removals first:** every addition references the post-removal shape (grounding writes SYSTEM rows into a transcript that must have exactly one context-message convention; theme verification must run against final call sites). Building grounding atop live tool code risks the new SYSTEM message colliding with TOOL-row handling and doubles the test matrix.

## Removal Completeness Gates (copy-pasteable)

```bash
# A: skills dead → must print nothing
grep -rn "SkillIds\|SkillRepository\|SkillPreferences\|SkillDescriptors\|ToolGating\|ToolExecutor\|LocalToolExecutor\|ToolHistory\|ToolText\|automaticToolCalling\|skillEnabled\|setSkillEnabled\|SkillChipsRow\|toolResultContent\|showNoToolSupportNotice\|toolCallActive" app/src/main --include=*.kt
# B: remote loop dead → must print nothing
grep -rn "LmStudioToolLoop\|MAX_TOOL_ROUNDS\|chatCompletionsWithTools\|OpenAiTool\|OpenAiFunctionDef\|OpenAiNonStreaming\|ToolCompleted\|ToolStatus" app/src/main --include=*.kt
# C: HF surface dead → must print nothing
grep -rn "HuggingFace\|huggingface\|hfToken\|hasHfToken\|HuggingFaceToken\|HuggingFaceAuth" app/src/main --include=*.kt
# E: theme hardcodes → only legitimate defaults (function default params) may remain
grep -rn "SyntaxThemes\.monokai()\|syntaxTheme.*MONOKAI\|codeTheme = SyntaxTheme.MONOKAI" app/src/main --include=*.kt
```

## Sources

- Live codebase (HIGH): `domain/llm/LlmModelHelper.kt` (5-method keystone, `runInference: Flow<StreamToken>`); `data/local/inference/LiteRtLlmHelper.kt:58-60,79-84` (wedge + ToolStatus passthrough); `data/remote/provider/LmStudioHelper.kt:59-61,117-120` (skill deps + loop routing); `data/remote/provider/LmStudioToolLoop.kt:43-71` (`MAX_TOOL_ROUNDS=5`, per-round Call discipline); `data/skills/ToolGating.kt` (pure gating, shared truth); `ui/chat/ChatViewModel.kt:53-54,221-224,235-251,378-426,514,597,1140` (skill injection, collector, toggle-reset, triple gating blocks, TOOL persistence); `data/local/preferences/AdvancedPreferences.kt:29,65-86` (theme key + migration — correct); `domain/model/SyntaxTheme.kt` (4 palettes, keyed); `data/highlighting/SyntaxHighlighterImpl.kt:44` (`.theme(SyntaxThemes.monokai())` hardcoded — bug root cause); `data/remote/network/HuggingFaceAuthInterceptor.kt` (token injection + browser UA trick worth reusing for WebFetcher).
- PROJECT.md v2.2 scope (HIGH): removals + heuristic grounding + offline fallback + theme fix; `LlmModelHelper`/`ChatViewModel`/`Room` context from milestone prompt (MEDIUM — Role.TOOL migration history not re-verified beyond grep hits).
- HtmlToText approach (MEDIUM — standard Android practice, no new dep): regex/script-style strip or `Jsoup.parse(html).body().text()` — Jsoup NOT currently a dependency; recommend regex-strip to honor "zero new dependencies" decision, or accept `org.jsoup:jsoup:1.18.x` (~400KB) if stripping quality matters. Flag for roadmap decision.

---
*Architecture research for: Warped v2.2 Simplificación + Web Grounding*
*Researched: 2026-09-28*
