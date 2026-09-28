# Pitfalls Research: v2.2 Simplificación + Web Grounding

**Domain:** Android LLM chat app — deleting a shipped subsystem + adding untrusted web fetch into LLM context
**Researched:** 2026-09-28
**Confidence:** HIGH (project-specific claims verified against repo + planning audits); MEDIUM (web-grounding defenses, sourced from OWASP/Microsoft/Palo Alto Unit 42 below)

## Critical Pitfalls

### Pitfall 1: Dangling skill references after subsystem deletion (compile + DI graph breaks)

**What goes wrong:**
Skills surface is woven through many layers: `domain/skill/` (Skill, SkillCategory, SkillRegistry, 4 impls), `SkillPreferences` DataStore, `SkillRepository` + Hilt bind in `RepositoryModule`, `SkillChipsRow` composable + `ChatInputBar` slot, `ChatViewModel.toggleSkill`, `LlmModelHelper.runInference(..., skills: List<Skill>)` signature (touched by both Phase 41 THINK and Phase 44 SKILLS), `LmStudioHelper.applySkills` + `tools[]` DTO, LiteRT-LM `@Tool` ToolSets, 45+ skills tests. Deleting the 3 `@Tool` impls but missing one call site leaves a red build — or worse, a Hilt binding that compiles but crashes at runtime (`NoSuchMethod`, missing `@Binds`).

**Why it happens:**
Removal work is verified by "it compiles" run once, but the surface spans domain/data/UI/di/test layers plus the `runInference` signature shared with thinking mode. The v2.0 audit explicitly notes the signature change history (`enableThinking` + `skills` params) — reverting half the signature breaks the other half.

**How to avoid:**
1. Inventory first: `grep -rn "Skill\|@Tool\|ToolSet\|toggleSkill\|applySkills" app/src` before deleting anything; turn the hit list into the deletion checklist.
2. Delete in dependency order: UI (chips, slots) → ViewModel → repo/prefs → domain → helper signatures → DTOs → tests. Keep `runInference`'s `enableThinking` param untouched.
3. Decide up front: does `Summarize` (PromptTemplate/persona, not a function per STATE.md) survive or die? Half-removal (delete tools, orphan the template plumbing) is the most likely dangling-ref source.
4. Full gates after deletion: `:app:assembleDebug` + unit tests + release build (R8, see Pitfall 2).

**Warning signs:**
- A plan that says "delete skills" without a grep inventory or a per-layer file list.
- `runInference` signature edited in the same diff as thinking-mode code.
- Tests still importing `domain.skill.*` after the "removal complete" commit.

**Phase to address:**
Removal-hygiene phase (first phase of the milestone). Must complete before grounding work starts — grounding reuses `runInference` and the system-prompt builder that skills plumbing touches.

---

### Pitfall 2: Stale R8 keep rules keep dead reflection surface alive (or crash LiteRT-LM)

**What goes wrong:**
Two failure modes, both real in this repo. (a) Deleting skills code but leaving the Phase-47 keep rules (`ToolProvider`, `ToolSet`, `ReflectionTool`, `@Tool`/`@ToolParam`, `ToolKt`, `Capabilities`, `com.warped.domain.skills.**`, `com.warped.data.skills.**` in `proguard-rules.pro` :22-36, :81-85) — harmless to size (~KB) but preserves a reflection attack surface and confuses every future reader into thinking tools still exist. (b) Worse: an over-eager cleanup removes the *LiteRT-LM* keeps (lines 16-36) along with the skills rules, and release builds crash at runtime when LiteRT-LM touches `MessageCallback`/`ToolSet` via JNI — a crash debug builds never show because R8 full mode only runs on release.

**Why it happens:**
The keeps file mixes two concerns in one block: SDK-required keeps (LiteRT-LM JNI, must stay) and feature keeps (skills `@Tool` reflection, safe to drop). The v2.1 audit confirms "R8 full mode on; release smoke proves tool survival" — that smoke test dies with the feature, removing the only guard.

**How to avoid:**
1. Split, don't blanket-delete: keep lines 16-36 SDK rules (`litertlm.**`, `MessageCallback`, `ToolSet`, `ReflectionTool`, etc.); delete only the `com.warped.domain.skills.**` / `com.warped.data.skills.**` package keeps and the 47-01 comment block if no warped-owned skill classes remain.
2. Keep a release smoke test: launch → load model → one inference turn on a `minifyEnabled` build. Promote it from "skills smoke" to "post-removal release smoke" so the gate survives the feature.
3. Verify shrink benefit: compare release APK/AAB size before/after; if unchanged, a keep is still pinning dead code.

**Warning signs:**
- Diff touches `proguard-rules.pro` deleting more than the `com.warped.*skills*` lines.
- No release-variant verification in the removal phase's success criteria.
- Comment `47-01 SKILLS (threat T-47-04)` still present after skills are gone.

**Phase to address:**
Removal-hygiene phase. R8 rule edit + release smoke are exit criteria of that phase, not a later hardening step.

---

### Pitfall 3: Room Role.TOOL rows + history repair migration v14 left inconsistent

**What goes wrong:**
v2.1 persists `Role.TOOL` transcript rows (SKILLS-11) and shipped history repair migration v14. After skills deletion, old conversations still contain TOOL rows. Depending on how rendering/querying code is cut, three outcomes: (a) crash on re-open (`when(role)` non-exhaustive, unknown-role exception); (b) silent disappearance of tool turns, rewriting the visible history of old chats; (c) a new migration that touches v14's repair path and corrupts it. Any new migration also needs the exported-schema + migration test the project already treats as standard practice (Room docs: test migrations, never `fallbackToDestructiveMigration` in production).

**Why it happens:**
Developers model deletion as "remove the writer" and forget the *reader* of legacy rows. Enum-by-ordinal vs by-name storage makes this worse: if `Role` is ordinal-stored, deleting a value shifts every row's meaning silently (no crash, just corrupted data — per community post-mortems). Even name-stored, a `when` without `else` crashes on the first legacy TOOL row opened.

**How to avoid:**
1. Check `Role` converter first (name vs ordinal). If ordinal — migrate carefully, do not just delete the enum entry.
2. Decide legacy-row policy explicitly and document it: render TOOL rows as plain collapsed text (safest, preserves history) vs filter at DAO level vs one-time migration rewriting them. Recommendation: keep the enum value + DAO filter/render-as-text; delete the execution code, not the data type.
3. Any migration gets a migration test (create v14 DB with TOOL rows → migrate → assert readable). Never `fallbackToDestructiveMigration` — chat history is the user's data.
4. Regression test: seed a conversation with TOOL rows, open it post-removal, assert no crash and history intact.

**Warning signs:**
- `Role.TOOL` deleted from the enum in the same commit as the tools.
- No migration test in the removal plan.
- `when (message.role)` sites not audited (grep them).

**Phase to address:**
Removal-hygiene phase. Legacy-data policy decided before any enum/DAO edit.

---

### Pitfall 4: Leftover HF token + search-removal debris (prefs keys, encrypted entries, dead routes)

**What goes wrong:**
Removing the HF token field and model search UI but leaving: DataStore/EncryptedSharedPreferences keys (token value persists on device — a credential the UI claims no longer exists), `Authorization: Bearer` header wiring on download calls, the log-scrubber regex in `WarpedApplication` (`hf_token|access_token|token|api_key` — keep or consciously narrow, don't orphan), dead nav routes/ViewModels/repo methods that still compile because nothing calls them, and gated-model references against a static-only `model_allowlist.json` catalog. Result: dead code that compiles, a token lingering in encrypted storage contradicting the "we removed it" story, and search-ViewModel tests passing against a screen that no longer exists.

**Why it happens:**
UI deletion is visible and satisfying; storage/network cleanup is invisible. Encrypted prefs entries survive feature removal by design — nobody wipes them unless a plan says to.

**How to Avoid:**
1. Per-key checklist: grep `hf_token|HuggingFaceToken|huggingFaceToken|Authorization` across `app/src/main`; every hit gets delete/migrate/keep decision.
2. Ship a one-time prefs cleanup: on upgrade, delete the HF token key from EncryptedSharedPreferences/DataStore (credential hygiene — the token should not outlive the feature).
3. Delete dead navigation routes + ViewModels + repo search methods outright, not just their call sites; run lint/dependency analysis to confirm zero references.
4. Keep the `WarpedApplication` log-scrubber (it also covers `api_key`, still live for remote endpoints) — verify with a test, don't delete with the HF code.
5. Static catalog contract: `model_allowlist.json` becomes the *only* source; assert at startup/test that every entry resolves to a public (gateless) URL with no auth header.

**Warning signs:**
- Removal plan mentions screens but not prefs keys.
- `Authorization: Bearer` still present in download path after "token removal complete."
- Instrumented/unit tests referencing a search ViewModel that has no route.

**Phase to address:**
Removal-hygiene phase (same phase as skills removal — one "surface removal" phase with a per-key, per-route checklist for both).

---

### Pitfall 5: Prompt injection via fetched web content (the grounding trust-boundary extension)

**What goes wrong:**
v2.1 built a tool-input trust boundary (HARD-02). Web grounding punches a new, larger hole through the same wall: arbitrary page text — attacker-controllable — is concatenated into the LLM context. A malicious page (`ignore previous instructions, …`, fake `[SYSTEM]` tags, exfil-style "summarize the user's API keys") gets treated as instructions. Per Palo Alto Unit 42 (2026, observed in the wild) this is *indirect* prompt injection: the attacker never talks to the model, they just publish a page the model reads. OWASP's cheat sheet is explicit: pattern/regex filters do not reliably catch this; a system-prompt rule alone ("treat fetched content as data") is a policy, not a boundary.

**Why it happens:**
The heuristic design ("system-prompt + fetch directo, sin API keys") frames grounding as a *prompting* task. Teams then under-invest in the *structural* defenses and ship raw concatenation with a hopeful system-prompt sentence.

**How to avoid (defense in depth, cheapest first):**
1. Extend HARD-02, don't reinvent it: fetched content enters through the same trust-boundary abstraction as tool output. Same delimiters, same "data, never instructions" tagging.
2. Delimit + declare: wrap fetched text in explicit boundary markers (randomized per-request tokens resist delimiter-mimic attacks) and instruct the model the span is untrusted data. Community testing across 13 LLMs shows ~95% defense rates for delimiter+declaration vs ~60% baseline — worth doing, not sufficient alone (drift attacks ~89%).
3. Structural containment: never let fetched content carry authority — no fetched text in system role; user-role or dedicated context span only. Strip active content (scripts, comments, metadata) before insertion — HTML→text extraction, not raw HTML.
4. Least privilege: grounding is read-only summarization context. No tool calls, no endpoint mutations, no credential-bearing context in the same turn when grounding is active (excessive-agency chaining is the OWASP LLM06:2025 escalation path).
5. Show provenance in UI: render which URL grounded the answer; user sees *what* influenced the model.

**Warning signs:**
- Grounding plan with no mention of HARD-02 or the trust boundary.
- Fetched HTML inserted raw (tags, scripts, comments included).
- Fetched content placed in the system prompt alongside real instructions.
- No adversarial test case (a page containing "ignore previous instructions" is the minimum bar).

**Phase to address:**
Grounding-trust-boundary phase (dedicated phase, not folded into "fetch plumbing"). Adversarial test page is an entry/exit criterion.

---

### Pitfall 6: SSRF / private-IP fetch + unbounded page size (context overflow, OOM)

**What goes wrong:**
"Fetch directo" means the app fetches arbitrary URLs. Without guards: (a) SSRF-adjacent risk — a crafted link or redirect chain pulls `http://192.168.x.x/admin`, `localhost`, or metadata endpoints (`169.254.169.254`) from the user's own network position, leaking LAN content into model context or probing the network; (b) unbounded pages (multi-MB docs, infinite streams) blow the model's context window and, on-device, spike RAM during LiteRT-LM inference — the project already fights memory pressure (smart presets, `largeHeap` awareness); (c) redirect chains turn a 2s fetch into a 30s hang on the inference path.

**Why it happens:**
OkHttp follows redirects by default and imposes no response-size cap unless configured. Mobile + on-device inference makes the cost physical (RAM, battery, ANR), not just a slow server.

**How to avoid:**
1. URL policy before fetch: block private/reserved ranges (10/8, 172.16/12, 192.168/16, 127/8, 169.254/16, IPv6 loopback/ULA) including after redirect resolution — resolve and re-check each hop. HTTPS-only except user-configured LAN hosts (consistent with the existing cleartext-blocking posture).
2. Hard caps: max response bytes (e.g. 256–512 KB), max redirect hops (e.g. 3–5), connect+read timeouts (e.g. 8–10 s total). Truncate with a "…[truncated]" marker so the model knows content is partial.
3. Fetch off the inference path: download → extract → truncate → *then* build the prompt. Never stream a socket into context construction. Cancellation (the v2.1 single-flight `Call.cancel()` work) must cover the fetch call too — Stop means stop for grounding fetches.
4. Budget grounding tokens against the model's context window *before* inference; skip-or-summarize when the page exceeds budget rather than OOM-killing inference.

**Warning signs:**
- No `Dns`/redirect handling or size cap in the fetch client design.
- Grounding fetch sharing the inference coroutine without independent timeout/cancel.
- No test with a 5 MB page, a redirect loop, or a `192.168.x.x` URL.

**Phase to address:**
Grounding-trust-boundary phase (same phase as Pitfall 5 — fetch policy + injection boundary are one security review, not two phases).

---

### Pitfall 7: Theme fix treats symptom, not the DataStore-key / mapping / recomposition cause

**What goes wrong:**
Only Monokai applies; One Dark/GitHub/Dracula are silently ignored. The three usual suspects: (a) DataStore key mismatch (v1.6's CodeTheme→SyntaxTheme migration kept the old key — a write going to a new key while the reader watches the old one reproduces exactly this: default theme always wins); (b) stale mapping (settings writes a name the `when` in the theme resolver doesn't match → falls through to default); (c) recomposition key bug (theme state flows correctly but the code-block composable doesn't re-read it — e.g. `remember` without the theme key, or the streaming flat-monospace path never swapping to the themed path). Fixing the wrong one "works" in the demo (select Monokai, looks fine) and the bug report reopens.

**Why it happens:**
Silent-ignore bugs have no crash and no log. Each layer (settings write → DataStore → ViewModel flow → composable) looks correct in isolation; the break is at a seam. The streaming path (flat monospace during streaming, themed on closing fence — v1.6 deferred-highlighting decision) doubles the suspect surface: theme may apply post-stream but never visibly, or vice versa.

**How to avoid:**
1. Diagnose seam by seam, end to end, before coding: set One Dark → read back DataStore value directly (is the write landing?) → check ViewModel flow emission → check composable recomposition (Layout Inspector / log on theme change). The seam where the value stops changing *is* the bug.
2. Cover all four themes in the fix's test, not just the reported-broken ones: parameterized test asserting each preset resolves to its distinct color table and that selecting each one round-trips through DataStore.
3. Check both render paths: streaming (flat) and completed (themed) code blocks, since v1.6's deferred highlighting means two code paths consume the theme.
4. Add a regression test at the seam that was broken (e.g. DataStore round-trip for all 4 enum values; or resolver `when` exhaustiveness with no default-fallthrough hiding mismatches).

**Warning signs:**
- Fix PR touches only the settings dropdown or only the composable without evidence of seam-by-seam diagnosis.
- Manual verification "selected each theme, looks right" with no automated round-trip test.
- A `when` on theme with an `else → MONOKAI` branch (silently masks the next mapping bug).

**Phase to address:**
Theme-fix phase (small, isolated — safe to schedule parallel/after removal, but before release hardening so the regression test gates the milestone).

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Comment out skills wiring instead of deleting | "Reversible" removal, fast | Dead code rots; Hilt/DTO surface still compiled and R8-kept; next milestone pays full deletion cost again | Never — delete outright, git history is the undo button |
| Keep `@Tool` R8 rules "just in case" | One less file to touch | Fake reflection surface; future devs assume tools exist; masks real shrink regressions | Never — LiteRT-LM SDK keeps stay, warped-owned skill keeps go |
| Delete `Role.TOOL` enum value to "finish" removal | Clean enum | Legacy chat history crashes or silently corrupts; ordinal shift corrupts all rows if ordinal-stored | Never — keep the value, remove the execution |
| Leave HF token in encrypted prefs ("harmless, it's encrypted") | Skip a migration step | Credential outlives its feature; contradicts removal; Play review / audit flag | Never — one-time wipe on upgrade |
| Raw HTML concatenation for grounding v1 ("iterate later") | Fastest grounding demo | Injection hole + context blowout shipped to users; retrofit is a migration, not a tweak | Never — delimiters + caps are day-one, not v2.3 |
| System-prompt-only injection defense ("the model knows") | Zero code | ~60% baseline defense; prompt-only rules are policy, not boundary (OWASP) | Never as sole defense; fine as one layer among delimiters + extraction + least-privilege |
| Theme fix verified manually on one device | Fast close | Silent-ignore regresses on next theme/prefs touch; no gate | Never — parameterized round-trip test is the gate |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| LiteRT-LM after skills removal | Deleting SDK keep rules with feature keeps → release-only JNI crash | Keep `com.google.ai.edge.litertlm.**` rules; release-smoke on minified build |
| LM Studio remote after tool-loop deletion | Leaving `tools[]` DTO + loop scaffolding half-wired → malformed requests or silent param drop | Remove loop + DTO field together; contract-test a plain chat completion post-removal |
| Static allowlist as sole catalog | Entries pointing at gated URLs needing the deleted token → downloads 401 with no auth path | Startup/test assertion: every entry public, no `Authorization` header in download path |
| OkHttp fetch for grounding | Default redirect-following + no size cap → LAN-probe/large-page/OOM exposure | Private-IP block per hop, byte cap, hop cap, timeouts, `Call.cancel()` wired to Stop |
| Offline fallback | Grounding failure surfaces as chat failure | Fetch errors degrade to plain local/remote answer with a visible "sin conexión / sin grounding" indicator; never block the turn |
| Theme prefs across upgrade | Write/read key drift (CodeTheme→SyntaxTheme legacy) → selection silently ignored | Round-trip test all 4 values through the real DataStore key; no `else → default` masking |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Unbounded page into on-device context | Inference slowdown, RAM spike, ANR on large pages | Byte cap + token budget check before prompt build | First 1 MB+ page on a mid-range device |
| Fetch on the inference critical path | Chat latency = page latency; Stop doesn't stop | Fetch with independent timeout/cancel, then build prompt | First slow/flaky network |
| Re-fetching same URL every turn | Repeated latency + data use in multi-turn grounding | Per-conversation fetch cache (URL → truncated text) with size bound | Second follow-up question on same page |
| Theme recomposition storm | Full chat re-highlights on every theme tick; jank during streaming | Theme consumed at code-block scope; streaming path stays flat monospace (v1.6 decision preserved) | Long chats with many code blocks |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Fetched page treated as instructions (indirect injection) | Attacker page hijacks answer, exfiltrates context, issues unauthorized actions | Delimited untrusted spans + HTML→text extraction + system-prompt declaration + least-privilege (no tools/actions on grounded turns) |
| Delimiter mimic (`</context>` forged inside page) | Model "escapes" the data span, follows injected instructions | Randomized per-request boundary tokens; post-extraction scan for boundary collision → re-tokenize or reject |
| Private-IP / metadata-IP fetch (SSRF-adjacent) | LAN probing, local content pulled into model context | Deny private/reserved ranges pre-fetch and per redirect hop; HTTPS-only |
| HF token lingering post-removal | Stale credential on device; contradicts feature removal | One-time encrypted-prefs wipe; verify key absent post-upgrade |
| Log scrubber deleted with HF code | API keys for remote endpoints leak into crash logs | Keep `WarpedApplication` scrubber; test covers `api_key` after HF removal |
| Grounded answer without provenance | User can't tell attacker-influenced content from model knowledge | UI shows grounding URL(s) per answer |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Old chats change appearance after skills removal | Tool turns vanish → history feels rewritten | Render legacy TOOL rows as collapsed plain text; history never shrinks |
| Grounded answer with no source shown | Can't judge trustworthiness of web-influenced answer | Chip/link with grounding URL + "offline, sin grounding" state when fetch fails |
| Grounding failure blocks the turn | No internet → no answer at all | Graceful fallback: plain model answer + visible offline indicator |
| Theme selector still silently ignores choice | User taps, nothing happens, trust erodes | Immediate visible apply + persisted round-trip; all 4 presets selectable and distinct |

## "Looks Done But Isn't" Checklist

- [ ] **Skills removal:** Often missing R8 cleanup + release smoke — verify `proguard-rules.pro` diff only drops warped-owned skill keeps and a minified build passes an inference turn
- [ ] **Skills removal:** Often missing legacy TOOL rows — verify an old conversation with TOOL rows opens without crash and history intact
- [ ] **Skills removal:** Often missing test cleanup — verify zero `*Skill*`/`@Tool` references in `app/src` (main + test) except the kept SDK keeps
- [ ] **HF/search removal:** Often missing stored credential — verify token key absent from encrypted prefs after upgrade
- [ ] **HF/search removal:** Often missing header wiring — verify no `Authorization` header on the download path and all allowlist URLs public
- [ ] **Grounding:** Often missing adversarial case — verify a page containing "ignore previous instructions" does not hijack the answer
- [ ] **Grounding:** Often missing fetch caps — verify 5 MB page, redirect loop, and private-IP URL are all refused/capped
- [ ] **Grounding:** Often missing offline path — verify airplane-mode turn still answers with a visible no-grounding indicator
- [ ] **Theme fix:** Often missing full-matrix check — verify all 4 presets round-trip through DataStore and render distinctly in both streaming and completed paths

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Dangling refs break build/DI | LOW (if caught pre-release) | Grep inventory → delete in dependency order → full gates; never partial-revert `runInference` |
| Release-only LiteRT-LM crash from keep deletion | HIGH (store-hotfix territory) | Restore SDK keeps, emergency release; add minified-build smoke to CI so it can't recur |
| Legacy TOOL rows crash old chats | MEDIUM | Restore enum value + render-as-text; ship reader fix, no data migration needed if rows untouched |
| Token lingering / dead routes shipped | LOW | Follow-up cleanup release: prefs wipe + route deletion; audit log scrubber still active |
| Injection via grounded page in the wild | HIGH (trust + safety) | Server-free mitigation: tighten extraction + delimiters, add adversarial tests, disclose; consider grounding kill-switch pref |
| OOM from unbounded fetch | MEDIUM | Add byte/token caps + fetch-cache bound; hotfix caps first, policy UI later |
| Theme silently ignored again | LOW | Seam-by-seam diagnosis; parameterized regression test locks all 4 presets |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| 1 Dangling skill refs | Removal-hygiene phase (phase 1) | Zero skill refs grep-clean + debug + unit gates green |
| 2 Stale R8 keeps / SDK keep deletion | Removal-hygiene phase (phase 1) | `proguard-rules.pro` diff scoped + minified release smoke passes |
| 3 Role.TOOL legacy rows | Removal-hygiene phase (phase 1) | Seeded legacy conversation opens intact, no crash |
| 4 HF token/search debris | Removal-hygiene phase (phase 1) | Prefs key absent post-upgrade; no auth header; dead routes deleted |
| 5 Injection via fetched content | Grounding-trust-boundary phase (phase 2) | Adversarial page test; HARD-02 extension documented |
| 6 SSRF + unbounded fetch | Grounding-trust-boundary phase (phase 2) | Private-IP/large-page/redirect-loop tests; cancel wired |
| 7 Theme silent-ignore | Theme-fix phase (phase 3, isolable) | All-4-preset round-trip test + both render paths verified |

Suggested ordering rationale: removal hygiene first (it touches `runInference`, system-prompt builder, prefs, and R8 that grounding and theme work both build on) → grounding trust boundary as one security-reviewed phase → theme fix small and independent (schedulable any time after removal, must gate the milestone release).

## Sources

- Repo + planning audits (HIGH): `app/proguard-rules.pro` (:16-36, :81-85); `.planning/v2.1-MILESTONE-AUDIT.md` (SKILLS-07/08/11, R8 keeps, 45 skills tests); `.planning/v2.0-MILESTONE-AUDIT.md` (Phase 44 surface inventory, `runInference` signature history); `.planning/STATE.md` (Summarize-as-persona note); `app/src/main/java/com/warped/WarpedApplication.kt` (:107-108 log scrubber); `app/src` grep (`SyntaxTheme` usages, test fakes of `advancedPreferences.syntaxTheme`)
- OWASP LLM Prompt Injection Prevention Cheat Sheet (MEDIUM): https://cheatsheetseries.owasp.org/cheatsheets/LLM_Prompt_Injection_Prevention_Cheat_Sheet.html — screen retrieved/fetched context; regex filters unreliable; guardrail-LLM is one layer, not the boundary
- Microsoft Zero-Trust AI attack techniques: Prompt Injection (MEDIUM): https://learn.microsoft.com/en-us/security/zero-trust/catalog-ai-attack-techniques/prompt-injection — input segmentation/delimiters for external text; untrusted content as adversarial by default
- Palo Alto Unit 42, web-based indirect prompt injection observed in the wild (MEDIUM): https://unit42.paloaltonetworks.com/ai-agent-prompt-injection — benign-page-embedded instructions influencing summarization/analysis flows at scale
- DEV community delimiter-defense test across 13 LLMs (LOW, single-source): https://dev.to/whetlan/i-tested-delimiter-based-prompt-injection-defense-across-13-llms-50mn — ~95% delimiter+declaration vs ~60% baseline; drift/mimic residual weakness
- Android Room migration docs + community guides (MEDIUM): https://developer.android.com/training/data-storage/room/migrating-db-versions — AutoMigrationSpec, schema export, migration testing; never destructive fallback in production; enum-ordinal fragility

---
*Pitfalls research for: Warped v2.2 Simplificación + Web Grounding*
*Researched: 2026-09-28*
