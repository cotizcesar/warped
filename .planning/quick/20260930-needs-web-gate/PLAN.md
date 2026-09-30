# Quick-Task PLAN — Needs-Web gate + identity/hint prompt lines

**Problem (locked, on-device evidence):** `ChatViewModel` always-on pre-search fires on
social/identity messages (`"hola, quien Eres"` → web-searched SpanishDict + cited a
translation answer). The model (~2B) doesn't reliably self-gate tool calls, so the app decides
with a transparent deterministic rule; the agentic loop stays as backstop for anything the gate
lets through.

**Files verified by planner (read before writing this plan):**
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` — hook at the no-URL branch
  (`} else if (images.isEmpty() && audioBytes == null) {`, ~line 544), inside `if (doGround)`,
  after the URL branch, before `fetcher.hasValidatedInternet()` (~line 566).
- `app/src/main/java/com/warped/data/grounding/ImageIntent.kt` — pattern to mirror (pure
  `object`, `WORDS`/`PHRASES`, token-subsequence matching, JVM-testable).
- `app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt` — `SYSTEM_PROMPT` reaches
  grounded turns ONLY; ungrounded turns send raw user text (no persona anywhere).
- `app/src/main/java/com/warped/domain/prompt/PromptTemplate.kt` + `PromptTemplateConfigs.kt`
  — PromptLab skill templates, NOT the chat persona. Do NOT touch.
- `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt` — `TOOL_USE_SYSTEM_HINT`
  (~line 104, pinned verbatim by `LiteRTLmLoopTest` — must update that pin); unarmed
  `ConversationConfig` (~line 320) sets NO `systemInstruction` today.
- Tests mirroring targets: `ImageIntentTest.kt` (word-table style),
  `ChatAlwaysSearchTest.kt` (VM-seam harness: mocked repos, `sendMessage` + `coVerify`).

**Planner decision (locked): identity placement = local-only via `LiteRTLmProvider`.**
Rationale: no base-persona path reaches ungrounded turns today (no `systemInstruction` on the
unarmed path, no augment on `doGround == false` turns), and `GroundingPrompt.SYSTEM_PROMPT`
reaches grounded turns only — the exact wrong set for social turns. So the single insertion
covering ALL local turns is `LiteRTLmProvider`: always set `systemInstruction` (identity line;
hint appended when armed). Remote providers untouched → record remote-identity follow-up in the
SUMMARY. Model-aware name: static string (display name not trivially available at that layer).

Out of scope: search providers, loop mechanics, UI changes, budgets, new locales.

---

## Task 1: `NeedsWeb` pure helper + truth-table test (mirror `ImageIntent`)

**Files:**
- NEW `app/src/main/java/com/warped/data/grounding/NeedsWeb.kt`
- NEW `app/src/test/java/com/warped/data/grounding/NeedsWebTest.kt`

**Action:**
- Create `object NeedsWeb` in `com.warped.data.grounding`, mirroring `ImageIntent` structure
  (`internal WORDS`, `internal PHRASES`, `fun needsWeb(query: String): Boolean`, private
  `containsSubsequence`). Semantics inverted vs `ImageIntent`: returns **FALSE** for
  social/identity/capability-about-self messages, **TRUE** for everything else.
- Normalization (extends the `ImageIntent` pattern — locked): NFD-normalize + strip combining
  marks (diacritic-insensitive: `quién` == `quien`, `adiós` == `adios`) THEN lowercase THEN split
  on `[^\\p{L}]+` into letter-tokens. Whole-token matching for `WORDS`, token-subsequence
  matching for `PHRASES` — never substrings (so `hola` inside `mahola`-style tokens can't fire;
  same boundary contract as `ImageIntentTest.imaginacion never matches`).
- **Locked word/phrase lists (exact — no ML, no length heuristics, no additions without asking):**
  - `WORDS` (single tokens, ES+EN): `hola`, `hi`, `hello`, `hey`, `buenas`, `dias`, `tardes`,
    `noches`, `saludos`, `gracias`, `thanks`, `thank`, `adios`, `bye`, `chao`, `goodbye`,
    `ayuda`, `help`
  - `PHRASES` (token lists): `buenos dias`, `buenas tardes`, `buenas noches`, `que tal`,
    `como estas`, `thank you`, `hasta luego`, `nos vemos`, `good bye`, `quien eres`,
    `tu nombre`, `como te llamas`, `cual es tu nombre`, `que modelo eres`, `que puedes hacer`,
    `que sabes hacer`, `who are you`, `your name`, `what is your name`, `what model are you`,
    `what can you do` (all stored WITHOUT diacritics — normalization makes `quién eres`,
    `qué puedes hacer`, `cómo estás` match).
- Empty/blank query → **TRUE** (fail-open: gate never blocks; search as today).
- Pure Kotlin, no Android imports — JVM-testable, same as `ImageIntent`.
- Test mirrors `ImageIntentTest` parameterized-table style:
  - FALSE table (social × ES/EN, mixed case + diacritics): `hola`, `HOLA`, `Hola!`,
    `hola, quien Eres` (the exact regression message), `buenos días`, `qué tal?`,
    `¿cómo estás?`, `gracias`, `Gracias!`, `thanks`, `Thank you`, `adiós`, `bye`,
    `hasta luego`, `quien eres`, `¿quién eres?`, `who are you`, `tu nombre`,
    `what is your name`, `que puedes hacer`, `what can you do`, `ayuda`, `help`,
    `qué modelo eres`, `   ` (whitespace-only → TRUE actually — see below; keep whitespace in
    TRUE table).
  - TRUE table (factual, incl. short ones + tricky near-misses): `qué es X?`, `qué hora es`,
    `quién ganó el partido`, `explícame la fotosíntesis`, `what is the capital of France`,
    `latest news`, `hola mundo program` (contains `hola` as part of larger factual query? NO —
    `hola` IS a whole token here → FALSE. Do NOT include this; instead include substring
    near-misses that must stay TRUE: `mahola`, `gracioso`, `helper`, `adiosible` if sensible —
    at minimum `gracioso` (contains `gracias`? no — `gracioso` vs `gracias` differ; better
    boundary test: `hola` must not fire inside a single token like `cholah`... simplest locked
    boundary case: `"holanda"` (single token, contains `hola` as substring) → TRUE),
    `""` empty → TRUE, `"   "` whitespace → TRUE.
  - URL turns are unaffected BY CONSTRUCTION (gate lives in the no-URL branch; no test needed
    at this layer — pinned at the VM layer in Task 2).

**Verify:** `./gradlew :app:testDebugUnitTest --tests "com.warped.data.grounding.NeedsWebTest"` green.
**Done:** `NeedsWeb.needsWeb` returns FALSE exactly for the locked social set (case/diacritic/
boundary-insensitive), TRUE for factual/short/empty; zero Android imports.

---

## Task 2: Gate insertion in `ChatViewModel.doGround` no-URL branch + integration tests

**Files:**
- MODIFY `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
- MODIFY `app/src/test/java/com/warped/ui/chat/ChatAlwaysSearchTest.kt` (or NEW
  `ChatNeedsWebGateTest.kt` reusing its harness — executor's choice; prefer NEW file to avoid
  churning the always-search contract tests)

**Action:**
- Inside `if (doGround)`, in the no-URL branch (`} else if (images.isEmpty() && audioBytes == null) {`),
  insert as the FIRST statement BEFORE `fetcher.hasValidatedInternet()`:
  `if (!NeedsWeb.needsWeb(userMessage.content))` → skip the entire pre-search block with
  `requestUserText`, `groundedSources/Details/Images`, `modelOnlyNotice` all untouched
  (no augment call at all — NOT even `augment(..., null, ...)`; no notice, no progress state,
  no socket, no credit burn). Behavior identical to grounding-off for that turn. The agentic
  loop arming (`computeArmSnapshot` / `ConversationConfig.tools`) is untouched — armed turns
  stay armed so the loop remains the backstop for anything the gate lets through.
- URL branch, attachment-skip, offline/model-only paths: untouched.
- Integration tests (reuse `ChatAlwaysSearchTest` harness: mocked `ddgSearchRepository`,
  `fetcher`, `sendMessage` + `coVerify` + transcript assertions):
  1. Social turn (`"hola, quien Eres"`): `ddgSearchRepository.search` exactly 0,
     `fetcher.hasValidatedInternet` exactly 0 (no socket), outgoing `ChatRequest` last-message
     content == original text (no `SYSTEM_PROMPT` prefix), `modelOnlyNotice == null`,
     assistant answers (helper still called exactly once).
  2. Social × EN variant (`"thank you, bye!"` or `"who are you?"`): same assertions (one case
     suffices; table the message via loop if cheap).
  3. Factual control (`"qué es la fotosíntesis?"` + existing `"latest news"` behavior):
     search exactly 1, sources fused as today (guards against gate over-blocking; complements
     existing always-search tests which must keep passing unchanged).
  4. URL turn with social text alongside (e.g. `"hola https://example.com/x"`): search/fetch
     path untouched — gate never consulted (assert via `multiUrlFetcher.fetchAll` called;
     exact assertion mirrors existing URL-branch tests).

**Verify:** `./gradlew :app:testDebugUnitTest --tests "com.warped.ui.chat.*"` green (existing
always-search/toggle/retry tests unbroken).
**Done:** Social turn → zero search calls, zero connectivity checks, plain-turn transcript;
factual/URL turns byte-identical to today.

---

## Task 3: Identity line + tool-hint line + prompt tests + full suite

**Files:**
- MODIFY `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt`
- MODIFY `app/src/test/java/com/warped/data/local/inference/LiteRTLmLoopTest.kt` (verbatim
  `TOOL_USE_SYSTEM_HINT` pin — update to new text)
- Test additions: identity/hint assertions (extend `LiteRTLmLoopTest` or NEW
  `LiteRTLmIdentityTest` — executor's choice)

**Action:**
- Add locked identity sentence as a const next to the hint (name it `IDENTITY_LINE`):
  `"You are Warped, a mobile AI assistant running locally."` (static — no model-name
  interpolation; display name not trivially available at that layer).
- Append locked sentence to `TOOL_USE_SYSTEM_HINT`: `"Do not call web_search/web_fetch for
  greetings, thanks, or questions about yourself."` (space-separated, same const block).
- Wire `systemInstruction` on BOTH conversation configs: armed keeps
  `Contents.of(TOOL_USE_SYSTEM_HINT)` (which now includes the no-social-search sentence AND
  must also include the identity — compose as `Contents.of("$IDENTITY_LINE $TOOL_USE_SYSTEM_HINT")`
  or two content parts per executor's read of the `Contents.of` API; minimal diff preferred);
  unarmed path (currently no `systemInstruction`) gets `Contents.of(IDENTITY_LINE)`.
  `PromptTemplate`/`PromptTemplateConfigs`/`GroundingPrompt.SYSTEM_PROMPT`: untouched
  (verified: wrong layer — PromptLab skills / grounded-turns-only).
- Update the verbatim `TOOL_USE_SYSTEM_HINT` pin in `LiteRTLmLoopTest` to the new text (it WILL
  fail otherwise — expected, not a regression).
- Prompt tests: identity const equals locked sentence; hint contains the locked no-social-search
  sentence; armed `systemInstruction` contains both identity + hint; unarmed config carries
  identity (assert at whatever seam `LiteRTLmLoopTest` already uses — do not build new heavy
  harness; if the config seam is unobservable from JVM tests, assert the const composition +
  a code-level grep gate `systemInstruction = Contents.of` appears twice in the provider file).
- Record in the plan SUMMARY (not code): remote-provider identity follow-up
  (OpenAI/Anthropic/Ollama/Custom/LM-Studio system prompts untouched — local-only by design).

**Verify:** full `./gradlew :app:testDebugUnitTest` green + `./gradlew :app:assembleDebug` succeeds.
**Done:** `quien eres` answerable from prompt on every local turn (grounded or not, armed or
not); loop hint forbids social tool calls; full suite + debug APK build green.

---

## Success criteria

- [ ] `NeedsWebTest` truth table green (social FALSE ES/EN × case/diacritics/boundaries; factual TRUE incl. `qué es X?`, `holanda`, empty).
- [ ] Social turn: 0 search calls, 0 connectivity checks, no notice, plain-turn transcript.
- [ ] Factual + URL turns unchanged (existing `ChatAlwaysSearchTest` suite green unmodified).
- [ ] Identity + hint lines pinned by tests; `LiteRTLmLoopTest` verbatim pin updated.
- [ ] Full unit suite green + `assembleDebug` succeeds.
- [ ] SUMMARY records remote-identity follow-up.
