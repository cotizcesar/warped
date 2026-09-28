# Project Research Summary

**Project:** Warped v2.2 Simplificación + Web Grounding
**Domain:** Android local-LLM chat app (Kotlin + Compose + LiteRT-LM local + LM Studio remote)
**Researched:** 2026-09-28
**Confidence:** HIGH

## Executive Summary

Warped v2.2 is a simplification-plus-one-feature milestone, not a greenfield build. The app already ships local inference (LiteRT-LM), remote chat (LM Studio), model downloads (WorkManager), and chat history (Room). The milestone deletes three shipped-but-valueless surfaces — Skills tool-calling (Calculator/CurrentTime/JsonFormatter + both tool loops), the Hugging Face access-token plumbing, and HF model search (replaced by a static allowlist) — fixes a half-broken syntax-theme selector (only Monokai applies), and adds heuristic web grounding: deterministic URL-in-message detection → bounded OkHttp page fetch → HTML→text extraction → injection as a delimited `[WEB CONTEXT]` block → single inference call, with offline/failure fallback to a model-only answer plus a visible notice.

The unanimous research recommendation is **zero new dependencies**: grounding is prompt-engineering plus ~100 lines of fetch plumbing over the already-pinned OkHttp 4.12.0 client, a pure-Kotlin heuristic policy, and a thin `ConnectivityManager` gate. `LlmModelHelper` — the 5-method domain keystone both backends speak through — stays byte-identical; grounding is a single pre-inference hook in `ChatViewModel.sendMessage`, so local and remote backends inherit it for free. The one open conflict across research files (FEATURES recommends adding Jsoup for extraction; STACK/ARCHITECTURE mandate zero-new-deps hand-rolled stripping) is resolved here in favor of **hand-rolled HTML→text stripping**: the milestone's explicit constraint is zero new catalog entries, the audit script (`scripts/audit-dependencies.sh`) and R8 posture punish additions, and heuristic grounding needs ~4–8 KB of clean text, not a DOM.

The dominant risks are deletion hygiene (dangling skill/HF references across domain/data/UI/DI/test layers, stale R8 keeps that either preserve dead reflection surface or — worse — crash LiteRT-LM in release-only builds if SDK keeps are over-deleted, and legacy `Role.TOOL` transcript rows from v2.1 that must keep rendering after their writers die) and grounding security (indirect prompt injection via fetched pages, plus SSRF-adjacent private-IP fetch and unbounded page sizes against on-device memory). Mitigation is structural: removals-first ordering with grep-gate exit criteria, release-variant smoke tests, trust-boundary delimiters + extraction + least-privilege as day-one requirements (never "iterate later"), and fetch caps (byte cap, redirect cap, timeouts, `Call.cancel()` wired to Stop) designed alongside the fetch client, not after it.

## Key Findings

### Recommended Stack

Zero-new-dependency milestone: every v2.2 need is met by the pinned catalog plus platform APIs (full detail in `STACK.md`).

**Core technologies:**
- Kotlin 2.3.20 (pinned) — all new v2.2 code is plain Kotlin + coroutines; no language change
- OkHttp 4.12.0 (existing) — page fetch via raw `newCall()` GET on a small bounded client; model downloads/REST unchanged
- kotlinx-coroutines 1.11.0 (existing) — `Dispatchers.IO` fetch with timeout/cancel, composing with the v2.1 single-flight `Call` pattern
- Android `ConnectivityManager` + `NET_CAPABILITY_VALIDATED` (platform, minSdk 28, permission already declared) — offline pre-check for model-only fallback
- LiteRT-LM 0.17.1 (HOLD, do not bump) — grounding is system-prompt injection, not engine work; no API or version change
- Highlights 1.1.0 + DataStore Preferences 1.2.1 + Hilt 2.60.1 (all existing, unchanged) — theme fix is wiring, not engine; prefs change is deletion-only; DI change is module deletion plus one tiny new module
- R8 full mode + `scripts/audit-dependencies.sh` — gates: shrink keeps after deletions, audit stays green with zero new catalog entries

**Explicitly rejected:** `okhttp-sse` artifact (banned by v2.1 precedent), Jsoup/any HTML parser (new-dep cost for a heuristic), search-API SDKs (reintroduces the secrets surface being deleted), Cronet, platform `DownloadManager` for page fetch, Proto DataStore, version bumps of any kind.

### Expected Features

**Must have (table stakes):**
- URL-in-message auto-fetch + single fetch→inject→answer round (1–3 URLs, extract, char-budget truncate) — the core feature
- Offline + fetch-failure fallback with visible notice — offline-first constraint makes this launch-blocking, not polish
- Numbered source list under grounded answers (model cites numbers only, app resolves URLs) — models hallucinate URLs from memory; never let them generate links
- Trust-boundary prompt rules (delimiters + data-not-instructions + recency stamp) — cheapest lines in the prompt builder, prevents the worst failure class
- "Leyendo página…" fetch status + timeout — reuses the status-row composable spared from skills deletion
- Grounding on/off toggle (DataStore boolean, default ON) — opt-out for metered-data/purist users
- Skills removal (chips, prefs, repo, gating, both tool loops) — milestone goal #1
- HF token removal (field, headers, settings, gated filtering) — milestone goal #2
- HF search removal (allowlist-only catalog, direct download kept) — milestone goal #3
- Syntax-theme selector fix (all 4 presets apply, light/dark) — root cause already found: `SyntaxHighlighterImpl.kt:44` hardcodes `.theme(SyntaxThemes.monokai())`

**Should have (competitive):**
- Zero-key, zero-dependency grounding — the differentiator: Perplexity/Copilot/Gemini grounding all require cloud accounts; Warped's works with nothing to configure, on any model including small local ones that can't do function-calling
- Heuristic trigger via system prompt (no tool-calling required) — a ~10-line prompt addition; model consumes `<web-content>` and asks the user for a link when it needs freshness instead of hallucinating
- Grounded vs. ungrounded parts visibly distinguished (`[1]` markers on backed claims only — precision beats coverage)
- Fetch-budget transparency ("página truncada a N caracteres" note when truncation fires)

**Defer (v2.x / v3+):**
- P2: model-asks-for-link wording tuning, source titles/domains, per-message retry-without-web, per-conversation fetch cache
- P3/future: model-suggested URL fetch (hallucination risk on small models), JS-rendered page fallback (WebView cost), keyless search-index endpoint, post-hoc citation entailment check (2× on-device cost)
- Explicit anti-features: search-API integration (breaks the zero-key differentiator), multi-round agentic browse loops, persisting full fetched text in Room, prompt-injection scanner dependency

### Architecture Approach

Removals first while `LlmModelHelper` stays frozen, then one provider-agnostic grounding hook, with the theme fix as an independent parallel track (full delta map in `ARCHITECTURE.md`).

**Major components:**
1. `LlmModelHelper` (domain/llm) — UNCHANGED keystone; every feature speaks through its `initialize`/`runInference`/`stopResponse` contract
2. NEW `WebFetcher` (data/grounding) + `WebGroundingPolicy` (domain/grounding, pure/JVM-testable) + `ConnectivityGate` (data, thin platform wrapper) + `GroundingModule` (di) — fetch policy and mechanism behind injectable seams; ViewModel never touches OkHttp directly
3. `ChatViewModel.sendMessage` — MODIFIED with exactly one new suspend step (connectivity → policy → fetch → prepend SYSTEM `[WEB CONTEXT]` message) before the unchanged `runInference` call; transient `isFetchingWeb` state added, all skill/tool state removed
4. DELETE surfaces — `data/skills/` + `domain/skills/*` + `di/SkillsModule.kt`, `LmStudioToolLoop.kt` + remote `tools[]` path, `ui/huggingface/` + `HuggingFaceApi/Repository/AuthInterceptor/Module`, HF token sections in Settings/`ApiKeyStore`/HelpScreen/nav — package-level deletes with grep-gate exit criteria, never visibility-hiding
5. `SyntaxHighlighterImpl` — FIX: thread the selected `SyntaxTheme` through `highlight(code, language, theme)` and map domain `darkVariant/lightVariant` colors via the existing `TypeMapper` instead of hardcoded Monokai; `AdvancedPreferences.syntaxTheme` key and migration stay byte-identical

**Key data-flow decisions:** grounding hook lives in the ViewModel (NOT inside the two helpers — avoids duplicating fetch logic per backend); fetched content enters as a marked SYSTEM-context message (never raw-concatenated, never in the real system prompt); `Role.TOOL` enum value is kept for history compat while all writers die (no Room migration); fetch runs inside `generationJob` so Stop cancels it.

### Critical Pitfalls

Top risks from `PITFALLS.md` (7 critical documented; 5 condensed here):

1. **Dangling skill references after deletion (compile + DI breaks)** — surface spans 5+ layers plus the `runInference` signature shared with thinking mode. Avoid: grep inventory first (`Skill|@Tool|ToolSet|toggleSkill|applySkills`), delete UI→VM→repo→domain→signatures→DTOs→tests in dependency order, keep `enableThinking` param untouched, decide the fate of `Summarize` (persona, not function) up front.
2. **Stale R8 keeps / accidental SDK-keep deletion** — leaving warped-owned skill keeps preserves dead reflection surface; deleting LiteRT-LM JNI keeps crashes release-only builds. Avoid: split the edit (keep `litertlm.**`/`MessageCallback`/`ToolSet` SDK rules, drop only `com.warped.*skills*` lines) and promote the release smoke test (launch → load model → one inference turn on a minified build) to a removal-phase exit criterion.
3. **Legacy `Role.TOOL` rows + HF debris** — old chats crash on non-exhaustive `when(role)` or silently rewrite history; HF token lingers in encrypted prefs; dead routes/ViewModels compile silently. Avoid: keep the enum value, render legacy TOOL rows as collapsed plain text (no migration); ship a one-time encrypted-prefs token wipe; assert every allowlist URL is public with no `Authorization` header on the download path; keep the `WarpedApplication` log scrubber (still covers live `api_key`).
4. **Prompt injection via fetched content + SSRF/unbounded fetch** — attacker pages treated as instructions (observed in the wild per Unit 42); crafted links probe LAN/metadata IPs; multi-MB pages OOM on-device inference. Avoid (all day-one, never "iterate later"): extend the v2.1 HARD-02 trust boundary (delimiters + provenance header + "data, never instructions"), HTML→text extraction (no raw HTML, no fetched text in system role), least-privilege grounded turns, adversarial test page ("ignore previous instructions" must not hijack); URL policy blocking private/reserved ranges per redirect hop + HTTPS-only, byte cap (256–512 KB), redirect cap (3–5), total timeout (~8–15 s), `Call.cancel()` wired to Stop, token-budget check before prompt build.
5. **Theme fix treats symptom, not seam** — DataStore key drift vs. stale mapping vs. recomposition-key bug all present as "Monokai always wins," and fixing the wrong layer reopens the report. Avoid: diagnose seam-by-seam end to end before coding (write One Dark → read DataStore → check VM flow → check recomposition), parameterized round-trip test over all 4 presets, verify both streaming (flat) and completed (themed) render paths, no `else → MONOKAI` fallthrough masking the next mapping bug.

## Implications for Roadmap

Based on research, suggested phase structure (removals before additions — every addition references the post-removal shape):

### Phase 1: Surface Removal (skills + remote tool loop + HF token/search)
**Rationale:** Removal hygiene touches `runInference`, the system-prompt builder, prefs, and R8 — everything later phases build on. Must complete before grounding starts.
**Delivers:** Deleted `data/skills/`, `domain/skills/*`, `SkillsModule`, `LmStudioToolLoop` + remote `tools[]` path, HF search UI/API/auth/settings/help/nav surface, one-time HF token prefs wipe; narrowed R8 keeps; legacy TOOL rows render as collapsed text; release smoke green.
**Addresses:** Skills removal, HF token removal, HF search removal (all P1 table stakes).
**Avoids:** Pitfalls 1–4 (dangling refs, R8 keeps, TOOL legacy rows, HF debris).
**Gates (copy-pasteable):** zero hits for the skill grep (`SkillIds|SkillRepository|SkillPreferences|ToolGating|ToolExecutor|automaticToolCalling|SkillChipsRow|toolCallActive|showNoToolSupportNotice`), the loop grep (`LmStudioToolLoop|MAX_TOOL_ROUNDS|chatCompletionsWithTools|OpenAiTool`), and the HF grep (`HuggingFace|huggingface|hfToken|hasHfToken|HuggingFaceToken`) in `app/src/main`; `:app:assembleDebug` + unit tests + minified release smoke all green.

### Phase 2: Web Grounding Core (policy + fetcher + hook + fallback UX)
**Rationale:** Built atop the post-removal shape (exactly one context-message convention, no TOOL-row confusion, status-row composable spared from deletion). Provider-agnostic by construction — one hook serves both backends.
**Delivers:** `WebGroundingPolicy` (pure heuristic: URL extract ≤3, freshness keywords, offline veto) + `WebFetcher` (bounded OkHttp client, extraction, caps) + `ConnectivityGate` + `GroundingModule`; `ChatViewModel` pre-inference hook with `isFetchingWeb` state; system-prompt grounding rules; numbered source list UI; offline/failure fallback with visible notice; grounding toggle in Settings.
**Uses:** OkHttp 4.12.0 raw `Call`, coroutines `Dispatchers.IO`, `ConnectivityManager` pre-check + try/catch defense-in-depth, hand-rolled HTML→text + char cap.
**Implements:** `domain/grounding/` + `data/grounding/` components, trust-boundary marking pattern, pre-inference hook pattern.
**Avoids:** Anti-patterns 1–2 (grounding inside helpers, raw OkHttp in ViewModel); status-row reuse conflict resolved by Phase 1 sparing the composable.

### Phase 3: Grounding Trust Boundary Hardening (security review + adversarial tests)
**Rationale:** Fetch policy + injection boundary are one security review, not two phases (per PITFALLS mapping) — but kept as an explicit phase so the adversarial and caps tests are exit criteria, not afterthoughts. May fold into Phase 2 if the same plan carries the gates; never skipped.
**Delivers:** Private-IP/redirect-hop enforcement, byte/timeout caps with `[truncated]` marking, randomized per-request delimiters, HARD-02 extension documented, adversarial page test, 5 MB-page / redirect-loop / private-IP / airplane-mode test matrix, provenance (grounding URLs) shown per answer, Stop-cancels-fetch verified.
**Avoids:** Pitfalls 5–6 (injection, SSRF/unbounded fetch).

### Phase 4: Syntax-Theme Fix
**Rationale:** Fully independent of Phases 1–3 (highlighting layer only) — schedulable in parallel any time — but ordered last so verification runs against final call sites (e.g. the hardcoded `SyntaxTheme.MONOKAI` in `HuggingFaceScreen.kt:309` dies in Phase 1).
**Delivers:** Theme threaded into `SyntaxHighlighterImpl` via domain-color mapping; stray MONOKAI hardcodes removed; all-4-preset DataStore round-trip regression test; both streaming and completed render paths verified.
**Avoids:** Pitfall 7 (symptom-level fix).

### Phase Ordering Rationale

- **Removals first (Phase 1 → 2 → 3):** grounding writes SYSTEM context rows into a transcript that must have exactly one context-message convention; building it atop live tool code risks colliding with TOOL-row handling and doubles the test matrix. Grep gates make Phase 1's exit binary.
- **Security as a gated phase (Phase 3), not a checklist item:** delimiter + extraction + caps are day-one code, but the adversarial test matrix is the only proof they work — a phase with entry/exit criteria forces it.
- **Theme last but parallelizable (Phase 4):** zero file overlap with grounding; the only ordering constraint is running verification after HF-screen deletion. If bandwidth allows, execute alongside Phase 1.
- **Grouping by blast radius:** each phase owns a disjoint layer set (removal: all layers but deletion-only; grounding: new packages + one VM hook; hardening: tests + policy tightening; theme: highlighting layer) so intermediate states stay reviewable even though mid-removal trees don't compile — phases are atomic commits.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 2:** HTML→text extraction quality bar — hand-rolled stripping is unproven against real target pages (docs, news). If transcripts show garbage context, revisit Jsoup via a proper `/gsd-plan-phase --research-phase` decision with audit-script justification. Also: exact `<web-content>` budget (4k chars/page × 3) against the smallest allowlisted model's context window.
- **Phase 3:** delimiter-mimic robustness on small local models (drift attacks ~89% even with delimiters per single-source community testing) — plan may need a model-specific adversarial pass, not just one fixture page.

Phases with standard patterns (skip research-phase):
- **Phase 1:** deletion + R8 + prefs-wipe are fully inventoried procedures with grep gates; no unknown APIs.
- **Phase 4:** root cause already located (`SyntaxHighlighterImpl.kt:44`); fix is a parameter-threading refactor with an established test pattern.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Pinned versions verified against `libs.versions.toml`, `NetworkModule.kt`, `AndroidManifest.xml`; zero-new-deps is a project constraint, not an inference. |
| Features | HIGH | Fetch→inject→answer pipeline, citation UX, and fallback patterns verified against multiple 2026 production sources + official OpenAI/xAI/Gemini docs; competitor matrix grounded in named repos. |
| Architecture | HIGH | Verified against live codebase file-by-file (exact lines cited for helpers, ViewModel, prefs, theme impl, interceptor); theme root cause located to a single line. |
| Pitfalls | HIGH–MEDIUM | Project-specific claims verified against repo + v2.0/v2.1 audits (HIGH); web-grounding defenses sourced from OWASP/Microsoft/Unit 42 (MEDIUM); delimiter-effectiveness stat is single-source community testing (LOW — flagged above). |

**Overall confidence:** HIGH

### Gaps to Address

- **Jsoup vs. hand-rolled extraction (STACK ↔ FEATURES conflict):** resolved here for zero-new-deps, but extraction quality is the load-bearing assumption — handle during Phase 2 planning by defining a minimum quality bar (e.g. fixture pages for docs/news/blog must yield readable article text) and a Jsoup-escalation trigger.
- **`Role` converter semantics (name vs. ordinal):** PITFALLS flags ordinal-stored enums as silent-corruption risk, but no research file confirmed the actual converter — Phase 1 planning must check the Room `@TypeConverter` for `Role` before touching the enum; if ordinal, the keep-enum-value recommendation becomes mandatory, not advisory.
- **`Summarize` persona fate:** flagged in PITFALLS/STATE as neither function nor plain prompt — Phase 1 planning must explicitly decide survive-or-die, or it becomes the most likely dangling-reference source.
- **Fetch-budget numbers (byte cap, char cap, timeouts):** ranges proposed (256–512 KB, 4k chars/page, 8–15 s) but not validated against the smallest allowlisted model's context window or mid-range device RAM — Phase 2 planning should pin exact numbers with a budget calculation.
- **Regenerate-path existence:** FEATURES notes per-message "retry without web" is P2 *if* a regenerate path exists — unconfirmed; verify during Phase 2 planning, do not promise the action in v2.2 scope.

## Sources

### Primary (HIGH confidence)
- Live Warped codebase: `gradle/libs.versions.toml`, `di/NetworkModule.kt`, `AndroidManifest.xml`, `domain/llm/LlmModelHelper.kt`, `data/local/inference/LiteRtLlmHelper.kt`, `data/remote/provider/LmStudioHelper.kt` + `LmStudioToolLoop.kt`, `data/skills/ToolGating.kt`, `ui/chat/ChatViewModel.kt`, `data/local/preferences/AdvancedPreferences.kt`, `domain/model/SyntaxTheme.kt`, `data/highlighting/SyntaxHighlighterImpl.kt:44`, `data/remote/network/HuggingFaceAuthInterceptor.kt`, `app/proguard-rules.pro`, `WarpedApplication.kt` log scrubber
- Project audits: `.planning/v2.0-MILESTONE-AUDIT.md`, `.planning/v2.1-MILESTONE-AUDIT.md`, `.planning/STATE.md`, `scripts/audit-dependencies.sh`
- Official docs: Gemini URL-context (`ai.google.dev`), OpenAI citation formatting (`developers.openai.com`), xAI citations (`docs.x.ai`)

### Secondary (MEDIUM confidence)
- Production grounding pipelines: link.sc real-time web search for LLMs (2026-07); tianpan.co live web grounding pipeline (2026-04)
- Citation/attribution UX: ai-tldr.dev, MUI-X, shapeof.ai, multigrid.ai (2026-08), kds.koder.dev (EU AI Act framing)
- Threat models: OWASP LLM Prompt Injection Prevention Cheat Sheet; Microsoft Zero-Trust AI prompt-injection catalog; Palo Alto Unit 42 indirect prompt injection in the wild (2026)
- Prior-art repos: off-grid-mobile-ai (`read_url` tool), browser-llm (Readability extraction), airgap + OfflineOS (on-device RAG + offline posture), Ratatoskr + Groundhog (injection threat reports)
- Platform docs: Android Room migration/testing guides (enum-ordinal fragility)

### Tertiary (LOW confidence)
- DEV community delimiter-defense test across 13 LLMs (~95% delimiter+declaration vs ~60% baseline) — single source, needs validation on Warped's small local models (see Phase 3 flag)

---
*Research completed: 2026-09-28*
*Ready for roadmap: yes*
