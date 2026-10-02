# Phase 70: Document Reader Tool — Research

**Date:** 2026-10-02
**Status:** Complete — verdicts below are binding on the plans.

## Q1. What tool infrastructure exists TODAY that readTextFile plugs into?

**Verdict: full invocation path EXISTS on both local and remote — the phase extends it, never builds it.**

### Local loop (LiteRT-LM)

- `app/src/main/java/com/warped/data/agentic/LocalToolLoop.kt:42` — pure-JVM policy object: allowlist dispatch (`mapToolCallName`, exact-match, ASVS V4), arg validation (`validateArgs`), outcome mapping (`mapSearchOutcome`/`mapFetchResult`), status copy (`statusDisplay`), budget (`MAX_TOOL_CALLS = 5`, `isCapReached`). readTextFile needs one new `TOOL_READ_TEXT` constant + one `when` branch in each of `mapToolCallName` (`LocalToolLoop.kt:140-144`), `validateArgs` (`LocalToolLoop.kt:240-262`), `statusDisplay` (`LocalToolLoop.kt:105-117`), and one outcome mapper next to `mapFetchResult` (`LocalToolLoop.kt:224-231`).
- Tool schemas: `data/agentic/WebSearchToolSet.kt:39-48` + `WebFetchToolSet.kt:35-44` — LiteRT `ToolSet` classes whose bodies are schema-only (return `HOST_EXECUTED`, `LocalToolLoop.kt:13`); the manual loop executes the real work on `Dispatchers.IO` and feeds back via `Content.ToolResponse`. A `ReadTextToolSet` file follows the identical 40-line shape. Wired per-conversation in `data/local/inference/LiteRTLmProvider.kt:333-334` (`tools = listOf(tool(WebSearchToolSet()), tool(WebFetchToolSet()))`) — append one entry.
- Loop driver: `LiteRTLmProvider.kt:483 runToolLoop` (call-counting, `LocalToolLoop.MAX_TOOL_CALLS`) executes and maps via `LocalToolLoop`; loop arming `LocalToolLoop.isLoopArmed` (`LocalToolLoop.kt:88-92`, consumed at `LiteRTLmProvider.kt:189`). No new loop, no arming change: document grounding rides the existing armed-turn path (grounding-on + function-calling model + validated internet — internet check is a no-op gate for a local file read, still satisfied).

### Remote tools[] path

- `data/remote/dto/OpenAiChatRequest.kt:84-119 defaultRemoteTools()` builds the two-entry `tools[]` list from the SAME description constants (`WEB_SEARCH_TOOL_DESCRIPTION` etc.). readTextFile adds a third `OpenAiTool` entry reusing the new tool's description constants — one function, zero wire-format work (`tools` encoding, echo, `tool_calls` reassembly all generic).
- Drivers are tool-agnostic: `OpenAIProvider.kt:201-231 runTooledLoop` (attempt → `ToolsUnsupported` → exactly-one retry without tools), `CompatToolLoop.kt:93-117` (same shape for Ollama/LM-Studio/custom), `AnthropicProvider.kt:241-262` (native dialect). Dispatch funnels through `LocalToolLoop.mapToolCallName`/`validateArgs` (`OpenAIProvider.kt:267,341-343`) — extending the allowlist extends every provider at once. `ToolCapabilityMatrix.kt:38-80` (ATTEMPT / ATTEMPT_FALLBACK / NATIVE_ANTHROPIC + `isToolsRejection` 400-classifier) is unchanged; a server rejecting the 3-tool list hits the existing fallback + `TOOLS_UNSUPPORTED_NOTICE` path.
- Anthropic native tools (`AnthropicProvider.kt:241 defaultAnthropicTools()`) need the parallel one-entry addition (same description constants, Anthropic schema shape).

### How much new invocation path must the phase build?

Almost none: 1 new `ToolSet` file + allowlist/validation/status/outcome branches in `LocalToolLoop` + 3 one-entry additions (`defaultRemoteTools`, `defaultAnthropicTools`, LiteRT `tools = listOf(...)`). No new loop, matrix, classifier, or retry logic.

## Q2. TOOL-03 contingency verdict: FIT or UNFIT?

**Verdict: FIT — readTextFile proves fit with the evidence above; TOOL-03 (unit converter) stays UNBUILT.**

- Every hard sub-problem already has a proven in-repo solution: SAF picker (`ModelsScreen.kt:75-79` `ActivityResultContracts.OpenDocument` → ViewModel import — same contract with a `text/plain`-family MIME filter serves the chat attach point); bounded content reads off-main-thread (`ChatViewModel.kt:3413-3427 uriToBase64` — `contentResolver.openInputStream` + `Dispatchers` discipline precedent; text read is strictly simpler, no Base64); bounded prompt fusion (`GroundingPrompt.buildFusedBlock` + `augment`, see Q3); graceful model-only fallback (`MODEL_ONLY_STRING` / `FETCH_FAILED_STRING`, `LocalToolLoop.kt:68-73`).
- Zero-dependency feasibility is proven: SAF is a platform API (`androidx.activity.compose`, already on classpath — `ModelsScreen.kt:76` compiles today); plain-text read needs only `ContentResolver` + charset decode; no PDF/DOCX extraction is attempted (graceful-unsupported per CONTEXT).
- Per CONTEXT.md trust-boundary rule ("implemented if and only if readTextFile proves unfit, with evidence"): no unfitness evidence exists, so the plans MUST NOT contain converter tasks, converter strings, or converter UI. The plans record the verdict + the single-sentence evidence pointer instead.

## Q3. Web-grounding pipeline reuse points for [DOCUMENT CONTEXT] fusion, budget, Fuentes chip

All under `app/src/main/java/com/warped/data/grounding/` (pure Kotlin, JVM-testable) plus `domain/model/GroundedSource.kt`:

1. **Fusion shape** — `GroundingPrompt.kt:74-77 buildFusedBlock` (numbered `--- Source [N] ---` fusion) + `GroundingPrompt.kt:90-95 augment` (SYSTEM_PROMPT → block → original → language directive). New `DocumentPrompt.buildBlock(filename, text, truncatedAt)` mirrors this with a `[DOCUMENT CONTEXT]` header/footer (`--- Document: {filename} ---` / `--- End of document (truncated at N chars) ---`) so the model always sees the bounds; reuse `languageDirective(original)` verbatim for EN/ES reply discipline. Sanitizer analog: `WebContextSanitizer.kt:34-62` — document text needs the delimiter-collision escaping branch only (escape `[DOCUMENT CONTEXT` / `--- Document: [` / `--- End of document ---` the same way lines 56-60 escape web delimiters); hijack-line stripping is reused as-is (document content is equally untrusted).
2. **Budget** — `GroundingBudget.kt:26-30 globalBudget(contextSize)` + `perPageBudget` are the context-window-aware cap with per-model tiers. One document per turn ⇒ `perPageBudget(contextSize, 1)` IS the document size cap (no new budget math, no new constants — the cap value falls out of the existing function; the agent's-discretion size-cap choice is therefore already made: reuse, don't invent). Truncation marker `truncated at N chars` uses the same N.
3. **Fuentes chip/card** — `domain/model/GroundedSource.kt:23-36` (`url`, `extractedText`, `status`, OG/snippet fields) + `MessageBubble.kt:311-313` Fuentes carousel + `SourcePreviewSheet.kt:56` preview sheet. Document row reuses the card chrome with `url = "doc:{filename}"` display-label convention (filename where a web card shows title/host; document rows never attempt OG enrichment — `SearchOgEnricher` is HTTP-only and untouched). Persistence: document content is per-turn only (CONTEXT-locked) — the `GroundedSource` row persists metadata (filename label + truncated marker) via the existing `groundedSourceDao` path, never the full text.
4. **VM turn plumbing** — `ChatViewModel.kt:801-814` attachment-turn branch (attachment turns already skip heuristic pre-search) + `ChatViewModel.kt:670-686` `groundedSourceDetails`/`loopSourceDetails` collection + `StreamToken.ToolCompleted` Fuentes persistence (`LiteRTLmProvider.kt:550`, `OpenAIProvider.kt:275`). The document attachment joins the same `ToolCompleted.sources` channel.
5. **Attach affordance precedent** — `ChatInputBar.kt:296-301` image-attach `IconButton` (40dp, `enabled = !inputLocked`) + `ChatScreen.kt:591` `imagePickerLauncher.launch("image/*")` + `ChatViewModel.kt:3413 uriToBase64` read path. The doc-attach button mirrors all three (same row, same gate, `OpenDocument` with text MIME filter, `openInputStream` bounded read).
6. **Strings precedent** — `voice_msg_*` block (`strings.xml:238-275` + `values-es` twin) is the naming/shape pattern for the five `doc_reader_*` keys.

## Constraints compliance check (pre-plan)

- Zero new Gradle deps: proven — SAF (`androidx.activity.compose`), `ContentResolver`, `GroundingBudget`, `GroundingPrompt` are all on-classpath or new pure-Kotlin files. No PDF/DOCX library (unsupported-format path instead).
- Kotlin only, never block UI thread: `openInputStream` + decode on `Dispatchers.IO` (uriToBase64 precedent); pre-read size check via `OpenableColumns.SIZE` before reading.
- EN+ES: five `doc_reader_*` keys in `values/` + `values-es/`.

## Open Questions (RESOLVED)

1. **Local loop vs new loop?** — RESOLVED: extend `LocalToolLoop` allowlist (Q1 evidence).
2. **Remote mapping per provider or OpenAI-only?** — RESOLVED: all three drivers via shared `defaultRemoteTools`/`defaultAnthropicTools` + `mapToolCallName` funnel (Q1 evidence).
3. **TOOL-03 build or skip?** — RESOLVED: skip, FIT verdict with evidence (Q2).
4. **New budget constants?** — RESOLVED: reuse `GroundingBudget.perPageBudget(contextSize, 1)`; no new constants.
5. **Document persistence?** — RESOLVED: per-turn only; Room holds Fuentes metadata row, never full text.
