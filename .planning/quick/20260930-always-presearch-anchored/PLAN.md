---
phase: quick-20260930-always-presearch-anchored
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/warped/data/grounding/AnaphoraAnchor.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/test/java/com/warped/data/grounding/AnaphoraAnchorTest.kt
  - app/src/test/java/com/warped/ui/chat/ChatAnaphoraAnchorTest.kt
autonomous: true
requirements: [ALWAYS-PRESEARCH-01, ANAPHORA-ANCHOR-02]
---

<objective>
Fix the armed-turn stale-answer bug at the pre-search query level: every eligible
no-URL turn already pre-searches (armed-skip is gone at HEAD — verify, do not
re-remove), but follow-up turns like "Quien es su hermanastro?" search the RAW
message, so keyword DDG finds nothing topical and the model answers from stale
history, recycling old citation numbers. This plan keeps always-on pre-search and
anchors anaphoric follow-ups with the prior turn's topic words.

Purpose: armed follow-up turns get topical grounding instead of zero-signal
searches that push the model back onto stale history sources.
Output: AnaphoraAnchor builder + VM hook wiring + unit/VM tests, full suite
green + assembleDebug.
</objective>

<execution_context>
@/home/cotizcesar/Documents/warped/.opencode/gsd-core/workflows/execute-plan.md
@/home/cotizcesar/Documents/warped/.opencode/gsd-core/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
@app/src/main/java/com/warped/data/grounding/NeedsWeb.kt
@app/src/main/java/com/warped/data/grounding/CodeIntent.kt
@app/src/main/java/com/warped/data/grounding/DuckDuckGoSearchRepository.kt
@app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt
@app/src/main/java/com/warped/data/grounding/TavilySearchRepository.kt
</context>

<verified_code_state>
Verified at HEAD before writing (executor: spot-check, do not re-research):

- VM armed-skip is ALREADY GONE. ChatViewModel.kt contains zero `loopArmed`
  references; the no-URL DDG-primary branch (~L546-724) runs
  `ddgSearchRepository.search(query = userMessage.content, ...)` unconditionally
  once past the gates. Comments at ~L587-601 ("runs on EVERY grounded no-URL
  turn INCLUDING armed ones") describe the live code. Do NOT re-remove anything.
- STALE comment at ~L408-416 ("Armed turns skip the VM pre-search, so this is
  normally the only source") contradicts the live code — fix it in Task 2.
- Provider-side `isLoopArmed` (OpenAIProvider ~L136, AnthropicProvider ~L136,
  Ollama/LMStudio/Custom equivalents) guards the tools[] round driver vs the
  plain turn — NOT a pre-search skip. Untouched. LocalToolLoop web_search —
  untouched.
- History is available in the hook: `userMessage` is appended to
  `_transcript.value.messages` at ~L367 BEFORE the grounding block (~L401+), so
  at pre-search time prior turns = `_transcript.value.messages.dropLast(1)`
  (same dropLast(1) pattern reused at ~L832-836). History rows keep persisted
  originals (~L807) — anchors are clean text. ChatMessage(role: Role, content:
  String); filter Role.USER for the anchor; Role.TOOL rows are read-only
  history, never anchors.
- Gates that stay (untouched): NeedsWeb social skip (~L555-558), CodeIntent
  code skip (~L556), attachments skip no-URL pre-search (~L546),
  offline OFFLINE path (~L602-608), URL branch (~L459-530), doGround precedence
  (~L440, never re-read mid-turn), wantImages/image-grid (~L615+).
- GroundingPrompt reference rule (~L15: "Resolve pronouns and references...
  against the conversation history first, and use the resolved names when
  searching and answering") is MODEL-side instruction — COMPLEMENTARY to this
  VM-side query anchoring, no contradiction. Do NOT reword it.
- Tavily caps (TavilySearchRepository companion): DEFAULT_MAX_RESULTS=5,
  MAX_RESULTS_CAP=10, MAX_QUERY_CHARS=500 (pass-through take, "Phase 56 owns
  that" — DuckDuckGoSearchRepository.search already applies
  `query.take(MAX_QUERY_CHARS)` at ~L115, so the anchored query is capped
  downstream; no extra truncation needed).
- Test models to mirror: ChatAlwaysSearchTest, ChatNeedsWebGateTest,
  ChatCodeIntentGateTest, ChatAttachmentSearchSkipTest (VM harness with mocked
  ddgSearchRepository + coVerify exact query strings), NeedsWebTest /
  CodeIntentTest (pure unit style for the builder).
</verified_code_state>

<wallet_analysis>
MANDATORY read for the executor — why this plan cannot increase spend:

- DDG-primary leg is free/keyless (no key read, no metered call). Always-on
  pre-search on armed turns therefore costs LATENCY (~1-3s inside the existing
  isFetchingWeb/progress UI), not credits. The loop stays armed provider-side
  (computeArmSnapshot / ConversationConfig.tools untouched) — the model keeps
  full tool opportunity for depth; pre-search is a floor, not a replacement.
- Tavily fallback keeps its EXISTING caps/gates on both armed and unarmed
  turns, unchanged by this plan: fires only when (a) DDG yields nothing usable
  AND (b) a Tavily key is stored (DuckDuckGoSearchRepository ~L180-198);
  image-intent direct-Tavily leg unchanged (~L144-158). Worst case per message:
  <= 1 Tavily search call (one `search()` invocation, maxResults=5 results in a
  single API call) from the VM pre-search + <= 5 loop calls (existing loop cap,
  untouched). Total paid calls per message <= today — the anchor changes QUERY
  TEXT only, never call count, never gate conditions.
- Offline / no-key paths are byte-identical: offline still short-circuits to
  OFFLINE with no socket (VM ~L602 + repo ~L129); no-key + DDG-fail still
  FETCH_FAILED with no key nag.
</wallet_analysis>

<tasks>

<task type="auto">
  <name>Task 1: AnaphoraAnchor builder + pure unit tests</name>
  <files>app/src/main/java/com/warped/data/grounding/AnaphoraAnchor.kt, app/src/test/java/com/warped/data/grounding/AnaphoraAnchorTest.kt</files>
  <action>Create pure-Kotlin object AnaphoraAnchor (JVM-testable, no Android imports) mirroring the NeedsWeb/CodeIntent normalization pipeline exactly: NFD-normalize + strip combining marks (diacritic-insensitive, so `él`==`el`, `quién`==`quien`) THEN lowercase THEN split on non-letters into letter-tokens. Expose `fun buildQuery(message: String, priorUser: List<String>, lastAssistant: String?): String` with LOCKED semantics: (1) anaphora detection = normalized message tokens intersect ANAPHORA set (whole-token only, never substrings); (2) no anaphora hit OR no history (priorUser empty AND lastAssistant null/blank) → return message raw (today's behavior); (3) anchor = most recent non-blank priorUser entry else lastAssistant trimmed and truncated to ANCHOR_MAX_CHARS=200 via take(200); (4) dedupe: if anchor blank OR message contains anchor (case-insensitive contains on raw strings) → return message raw; (5) else return "$message $anchor". LOCKED ANAPHORA set (stored WITHOUT diacritics, ES+EN): su, sus, el, ella, ello, ellos, ellas, este, esta, estos, estas, ese, esa, esos, esas, aquel, aquella, aquellos, aquellas, eso, esto, aquello, lo, la, los, las, le, les, it, its, this, these, that, those, he, she, they, him, her, them, his, hers, theirs. Known accepted over-fire (document in KDoc): `el` collides with the article after diacritic-strip and lo/la/le fire on articles — fail-open, worst case is a harmless topic suffix DDG tolerates; under-anchoring (a miss) is the failure we refuse. KDoc must state: pure function, query-text only, never changes call count/gates; complement to the GroundingPrompt model-side reference rule (do not reword that rule). Unit tests in AnaphoraAnchorTest covering the matrix: pronoun×(prior-user history / assistant-only history / no history / first turn) → anchored vs raw; anchor selection (most-recent prior USER wins over assistant); truncation (long anchor → 200 chars); dedupe (anchor equals message, anchor contained in message → raw); diacritic-insensitivity (`su hermanastro` fires, `quién` handling); ES+EN spot checks (su, él, this, they, eso, le).</action>
  <verify>
    <automated>./gradlew :app:testDebugUnitTest --tests "com.warped.data.grounding.AnaphoraAnchorTest" --tests "com.warped.data.grounding.NeedsWebTest" --tests "com.warped.data.grounding.CodeIntentTest"</automated>
  </verify>
  <done>AnaphoraAnchorTest green; NeedsWeb/CodeIntent tests still green (no shared-code changes); buildQuery returns raw message for no-anaphora and no-history inputs.</done>
</task>

<task type="auto">
  <name>Task 2: Wire anchored query into the VM no-URL pre-search hook</name>
  <files>app/src/main/java/com/warped/ui/chat/ChatViewModel.kt</files>
  <action>In the no-URL DDG-primary branch (~L609-636), replace `query = userMessage.content` with the anaphora-anchored query: snapshot `_transcript.value.messages.dropLast(1)` (dropLast excludes the current userMessage appended at ~L367 — same pattern as ~L832), take prior USER contents via `filter { it.role == Role.USER }.map { it.content }`, take last assistant content via `lastOrNull { it.role == Role.ASSISTANT }?.content` (Role.TOOL rows excluded — read-only history), then `query = AnaphoraAnchor.buildQuery(userMessage.content, priorUser, lastAssistant)`. Everything else in the branch is byte-identical: gates (NeedsWeb/CodeIntent/attachments/offline), wantImages, searchCount, progress state, Grounded/ModelOnly/MissingKey/InvalidKey/UsageLimit mapping, images-need-key notice, finally-clear. Fix the STALE comment at ~L408-416 to state armed turns DO run the VM pre-search (floor) with loop ToolCompleted rows unioning on Done. Add/refresh a hook comment citing the wallet facts: DDG free so always-on costs latency not credits; Tavily fallback keeps existing caps (keyed + DDG-empty only, <=1 call/turn); loop cap untouched. Do NOT touch: URL branch, provider arming, LocalToolLoop, GroundingPrompt copy, TavilySearchRepository caps, budgets, OG/cards, UI.</action>
  <verify>
    <automated>./gradlew :app:testDebugUnitTest --tests "com.warped.ui.chat.ChatAlwaysSearchTest" --tests "com.warped.ui.chat.ChatNeedsWebGateTest" --tests "com.warped.ui.chat.ChatCodeIntentGateTest" --tests "com.warped.ui.chat.ChatAttachmentSearchSkipTest" --tests "com.warped.ui.chat.ChatLoopSourcesTest"</automated>
  </verify>
  <done>All five existing gate suites green with the anchored query wired (single-message turns have no history → raw query → exact coVerify strings unchanged); stale armed-skip comment gone.</done>
</task>

<task type="auto">
  <name>Task 3: VM anchoring tests + gates/wallet regression + full gate</name>
  <files>app/src/test/java/com/warped/ui/chat/ChatAnaphoraAnchorTest.kt</files>
  <action>Create ChatAnaphoraAnchorTest mirroring the ChatAlwaysSearchTest harness (mocked ddgSearchRepository, fake transcript with a seeded prior USER turn + assistant turn): armed-turn pre-search RUNS (coVerify search called exactly once — the mock stands in for the always-on DDG leg regardless of loop arming; assert with a prior USER turn present and an anaphoric follow-up, e.g. prior USER "Háblame de la familia real" + follow-up "Quien es su hermanastro?" → coVerify search("Quien es su hermanastro? <anchor…>", any, any, any) with the exact anchored string); unarmed single-turn unchanged (raw query passthrough); anaphora matrix at VM level (pronoun + history → anchored; pronoun + first turn → raw; no pronoun + history → raw); gates intact (social turn, code turn, URL turn, offline turn → search never called — coVerify exactly 0); wallet-cap assertions (exactly 1 search invocation per eligible turn; no direct Tavily client use from the VM — grep gate asserting no TavilySearchRepository import added to ChatViewModel beyond existing, i.e. the VM still calls only ddgSearchRepository.search). Then run the FULL unit suite green plus assembleDebug.</action>
  <verify>
    <automated>./gradlew :app:testDebugUnitTest :app:assembleDebug</automated>
  </verify>
  <done>ChatAnaphoraAnchorTest green; FULL :app:testDebugUnitTest green; assembleDebug succeeds; no new Tavily path from the VM.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| user-text → search-query | Prior-turn content is user/model text concatenated into a DDG/Tavily query string |
| query → network | Anchored query leaves the device to DDG HTML endpoint / Tavily fallback |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-anaph-01 | Tampering | AnaphoraAnchor.buildQuery | mitigate | Pure string concat of already-sent history + current message; downstream `take(MAX_QUERY_CHARS)` cap and WebContextSanitizer unchanged; no new parsers, no regex on raw HTML |
| T-anaph-02 | Information Disclosure | anchored query → DDG/Tavily | accept | Anchor is prior-turn text the user already sent to the same search-backed pipeline; no new exfiltration surface, no secrets (key handling untouched, zeroed copies intact) |
| T-anaph-SC | Tampering | npm/pip/cargo installs | mitigate | No package installs in this plan — no new dependencies |
</threat_model>

<verification>
- `./gradlew :app:testDebugUnitTest --tests "com.warped.data.grounding.*"` green (builder + NeedsWeb + CodeIntent + DDG repo suites)
- `./gradlew :app:testDebugUnitTest --tests "com.warped.ui.chat.*"` green (all VM gate suites + new anchoring suite)
- `./gradlew :app:testDebugUnitTest :app:assembleDebug` fully green
- grep: `loopArmed` still absent from ChatViewModel.kt; no `TavilySearchRepository` import added to ChatViewModel.kt; GroundingPrompt.kt copy untouched (diff empty)
</verification>

<success_criteria>
- Follow-up "Quien es su hermanastro?" with prior USER history pre-searches "<message> <anchor>" (anchored query asserted in VM test)
- Armed turns pre-search (regression-locked); unarmed single turns byte-identical to today
- Social/code/URL/offline gates intact; wallet worst case ≤ today (query-text-only change)
- Full unit suite green + assembleDebug
</success_criteria>

<output>
Create `.planning/quick/20260930-always-presearch-anchored/01-SUMMARY.md` when done
</output>
