# Project Research Summary

**Project:** Warped — v3.0 Chat UX + Voice Dictation (Android)
**Domain:** Incremental polish milestone on an existing Kotlin + Compose + Hilt LLM chat app (LM Studio equivalent; local llama.cpp + remote OpenAI-compatible providers, DDG-grounded answers)
**Researched:** 2026-10-02
**Confidence:** HIGH

## Executive Summary

v3.0 is a net-deletion polish milestone on a healthy, existing Clean-architecture app — not a greenfield build. Nothing in the layering changes: all work is new leaves (Play In-App Review wrapper, voice dictation holder) or deletions (Tavily integration, key-delete/Data-section surfaces, drawer Web Options) on the existing tree. The stack delta is exactly **one new dependency** (`com.google.android.play:review-ktx` 2.0.2); everything else reuses artifacts already in the repo (OkHttp 4.12.0 + Jsoup 1.23.2 for DDG search, `androidx.activity` for the RECORD_AUDIO permission flow, navigation-compose 2.9.8 for all drawer/catalog CTAs).

The recommended approach is contract-first deletion before addition: move/rename the shared `TavilySearchOutcome` sealed interface into the DDG repository first (every consumer imports it — ~15 files), re-point consumers, then delete the Tavily client/producer/DI/keystore/Settings surfaces, and only then layer the pure-UI work (drawer cluster, empty-state CTAs, Help rewrite, Settings cleanup) plus the two independent additions (voice dictation, Play Review). Two cross-file research agreements drive the phase structure: the drawer cluster (footer parity + delete-all relocation + empty-state CTA + Web Options removal) batches into one phase because it edits the same drawer composables, and the Help rewrite must follow Tavily removal or it documents a dead feature.

The key risks are all litigated in advance: (1) the Play Review quota silently suppresses the dialog, so a visible "Rate us" button wired to `launchReviewFlow()` looks broken and violates Google guidance — the rating must be an ambient trigger plus an optional Store deep-link; (2) `SpeechRecognizer` without an availability gate crashes or dead-ends on GMS-less devices Warped's privacy-conscious users disproportionately own — gate the mic on `isRecognitionAvailable()`; (3) Tavily removal leaves billable/secret-bearing leftovers (Keystore key, `@Named("tavily")` client, tests, strings) unless grep-gated — this time delete the Keystore entry via upgrade migration rather than repeating the v2.2 HF-token orphan precedent.

## Key Findings

### Recommended Stack

v3.0 keeps every core technology pinned — Kotlin 2.3.20, AGP 9.3.0, navigation-compose 2.9.8, OkHttp 4.12.0, Jsoup 1.23.2 (parse-only, never `connect()`) — and adds exactly one artifact: `review` + `review-ktx` 2.0.2 behind a Hilt `ReviewLauncher`/`PlayReviewManager` seam so ViewModels never touch Play Tasks directly. Speech, DDG search, and permissions add zero dependencies (platform `SpeechRecognizer` + `RecognizerIntent`, existing OkHttp+Jsoup, `rememberLauncherForActivityResult(RequestPermission)`; Accompanist-permissions explicitly rejected as archived). RECORD_AUDIO returns to the manifest (deliberately removed 2026-10-01 as dead code) as a dangerous runtime permission requested in-context at first mic tap. Full details in `STACK.md`.

**Core technologies:**
- `review-ktx` 2.0.2 — Play In-App Review flow (`requestReviewFlow` → `launchReviewFlow` with coroutine extensions) — the only new dep; pure Kotlin/Java AAR, no 16 KB native risk, passes the `auditDependencies` gate
- Platform `SpeechRecognizer` (primary) + `RecognizerIntent` (fallback) — dictation into the chat pill, partial results streamed into draft text, no audio persistence — zero artifacts, framework APIs since API 8
- Existing OkHttp + Jsoup — DDG `html.duckduckgo.com/html/?q=` GET with browser UA, `Jsoup.parse(body)` selecting `.result__a`/`.result__snippet` — replaces the Tavily producer behind the same search-producer interface
- `rememberLauncherForActivityResult(RequestPermission)` — RECORD_AUDIO in-context request with rationale + Settings escape on permanent denial — already on the classpath via `androidx.activity`

### Expected Features

v3.0 covers only 11 items; everything else (local/remote chat, catalog, grounding, settings) already exists. The headline is voice dictation into the chat input (STT → editable text, never auto-send, no audio messages). The breaking change justifying the major bump is Tavily removal → DDG-only zero-config grounding. The visible polish is the drawer cluster plus catalog/empty-state CTAs that close the download→chat funnel. Play rating is ambient infrastructure, invisible when quota-suppressed. Full landscape, dependencies, and MVP slice in `FEATURES.md`.

**Must have (table stakes):**
- Voice dictation into chat input — headline capability; recognized text lands in the input for editing before send
- Tavily removal (DDG-only) — removes the last API-key friction; highest blast radius (providers, tool loops, Settings, Help, strings EN+ES, tests)
- Drawer cluster: model-drawer empty-state "Download a model" CTA + footer font parity + delete-all-chats relocation (above Models, with existing confirm dialog) + Web Options removal — the visible polish, one phase
- Catalog "Use in Chat" on downloaded rows + Models & Endpoints empty-state CTAs — closes download→chat dead ends

**Should have (competitive):**
- Play In-App Review ambient trigger — timely ratings lift listing conversion; fires at success moments (e.g. N completed turns + cooldown), never from a button
- Help rewrite (short/minimal) — must follow Tavily removal; deletes stale key steps in EN+ES
- Settings cleanup (key-delete affordance + Data section removal) — mechanical once delete-all is reparented; key rotation survives via endpoint edit-overwrite

**Defer (out of v3.0):**
- Audio messages / voice replies (TTS), continuous listening / live transcription — PROJECT.md explicitly defers voice I/O
- Custom on-device STT engines (Vosk/whisper.cpp), ML Kit — 50–150 MB downloads + NDK surface for a convenience feature; ML Kit banned by `auditDependencies`
- New keyed search provider — would resurrect the key UI just deleted
- Key-deletion UI, per-chat delete, Settings Data section — removed surfaces, not reintroduced

### Architecture Approach

All v3.0 work is new leaves or deletions on the existing Clean tree (Compose+VM UI / pure-Kotlin domain / Hilt-singleton data). `ChatInputBar` stays stateless (new mic params only); a small lifecycle-aware voice holder (`VoiceInputManager` / `rememberVoiceInputState` in `ui/voice/`) owns the recognizer with `DisposableEffect.destroy()`; a thin `PlayReviewManager` wrapper owns the Review API with DataStore eligibility; every CTA reuses an existing nav callback (zero new destinations). Full component map, data flows, and build order in `ARCHITECTURE.md`.

**Major components:**
1. Voice holder (`ui/voice/VoiceInputManager`) — NEW; `SpeechRecognizer` lifecycle + `RecognitionListener` → `Flow<DictationState>`, output re-enters via existing `onTextChange`; `ChatInputBar`/`ChatScreen` stay thin
2. `PlayReviewManager` (data/util `@Singleton`) — NEW; `maybePromptForReview(activity)` with DataStore turn-count + cooldown, exception-swallowing, single post-success call site in `ChatViewModel`
3. `DuckDuckGoSearchRepository` (sole `web_search` producer) — MODIFIED; fallback + Tavily-direct image legs deleted, `[WEB CONTEXT]` fusion shape unchanged downstream
4. Tavily vertical-slice deletion (`TavilyApi`/`TavilyDtos`/`TavilySearchRepository`, 3× `@Named("tavily")` DI providers, `ApiKeyStore` Tavily fns, `TavilyKeyCard`/Data-section UI, strings EN+ES, tests) — DELETE in contract-first order
5. Drawer/catalog/settings/help UI surfaces (`NavGraph` drawer, `ModelSelector`, `ModelsScreen`, `EndpointsScreen`, `HuggingFaceScreen`, `SettingsScreen`+VM+UiState, `HelpScreen`) — MODIFIED, additive/CTA-only except deletions

### Tension Resolution (explicit)

**Tension A — SpeechRecognizer (inline) vs RecognizerIntent (one-shot): tradeoff and recommended default.**
STACK recommends platform `SpeechRecognizer` inline as primary (partial results streamed into the pill, on-device path on GMS devices, no context-breaking dialog) with `RecognizerIntent` as fallback when `isRecognitionAvailable()` is false and mic-hidden as last resort. FEATURES recommends the opposite order for v1: `RecognizerIntent` via Activity Result (simpler, no RECORD_AUDIO permission, consistent system UI) and graduates to `SpeechRecognizer` only if live transcription is later demanded. ARCHITECTURE sides with STACK (Pattern 2: recognizer holder outside the text field); PITFALLS assumes `SpeechRecognizer` throughout (Pits 4–6: availability gate, permission matrix, destroy discipline) and never prices the Intent path. **Recommended default: STACK/ARCHITECTURE position — inline `SpeechRecognizer` primary, Intent fallback, hidden-mic last resort.** Rationale: (a) v3.0's headline capability deserves live partials in the pill rather than a context-breaking system dialog; (b) the permission cost is a single in-context request already mapped by Pitfalls 5 mitigations (rationale → request → Settings escape); (c) the three-variant cascade (inline → intent → hidden) strictly dominates Intent-only because it degrades gracefully on every device class including GMS-less ROMs. The FEATURES concern (permission + complexity for zero visible gain) is real but answered: the gain is partial-result streaming + offline recognizer path, and the complexity is contained in one holder file plus the Pitfalls 4–6 acceptance gates (availability check, permission matrix, LeakCanary voice leg). Roadmapper note: keep the Intent fallback in the same voice phase (not a separate phase) — it is ~20 lines behind the same availability branch.

**Tension B — Play rating button vs ambient trigger: RESOLVED, no latitude.** FEATURES and PITFALLS agree verbatim: Play quota (~1/month, undisclosed, silently no-ops) makes any visible button wired to `launchReviewFlow()` look broken, and Google explicitly forbids CTA-triggered review flow plus pre-qualifying questions. The v3.0 "Play star rating" item is therefore an **ambient trigger only** (post-success moment, DataStore-gated, fire-and-forget, flow continues regardless) plus — if a visible affordance is wanted at all (Settings/Help) — a **Store deep-link** (`market://details?id=…` with `https://` fallback), never the in-app API. Any plan showing a star button calling `ReviewManager` is a Pitfall 1 violation; acceptance criteria must never read "tapping Rate shows the dialog."

**Tension C — Tavily deletion order: contract-first is mandatory.** ARCHITECTURE Pattern 3 and PITFALLS Pit 7 jointly dictate: (1) move/rename the shared `TavilySearchOutcome` sealed interface into the DDG file (e.g. `SearchOutcome`, shape-identical), (2) re-point all consumer imports (ChatViewModel ~10 refs, LocalToolLoop, CompatToolLoop, providers), each step compiling, (3) simplify the DDG repo to DDG-only, (4) delete API/DTO/repo/DI/keystore/Settings/tests, (5) `grep -ri tavily` zero-gate. Deleting files first leaves ~15 broken importers and an unbisectable wall of red (Anti-Pattern 3). Additionally, PITFALLS upgrades the STACK "harmless orphan" note into a hard requirement: ship a one-shot upgrade migration deleting the Tavily Keystore entry — do not repeat the v2.2 HF-token orphan.

**Tension D — drawer cluster batching + help sequencing.** FEATURES dependency notes and ARCHITECTURE build order agree: footer parity + delete-all relocation + drawer empty-state CTA + Web Options removal all edit the same drawer composables (`NavGraph` drawer content, `ModelSelector`) and **batch into one phase** to avoid merge churn; Help rewrite + error-string cleanup ride in the same removal-adjacent phase **after** the Tavily cut (help currently documents the Tavily key step — rewriting first re-documents a dead feature).

### Critical Pitfalls

Top risks distilled from `PITFALLS.md` (10 critical pitfalls; "Looks Done But Isn't" checklist is the phase exit-gate source):

1. **Play Review wired to a visible CTA** — quota makes the button look dead; policy violation risk — fire only from opportunistic app-chosen moments with DataStore cooldown; visible affordance (if any) deep-links to the Store listing
2. **FakeReviewManager-only testing** — Fake proves post-completion callbacks only, never the real dialog/R8 path — require internal-track real-dialog evidence + a no-show negative test as exit criteria
3. **Review Task on the chat critical path** — awaiting Play Tasks in the send/streaming scope stalls inference and breaks single-flight cancel — decoupled prompt controller, idle/foreground guard, swallow all errors, re-run cancel suites with prompt enabled
4. **Voice assumes a recognizer exists** — crash/no-op on GMS-less ROMs Warped's users disproportionately own — gate mic visibility on `isRecognitionAvailable()`; on-device path (API 31+) for offline; explicit language-unavailable messaging
5. **RECORD_AUDIO permission dead-ends** — one-shot ask with no rationale, loops after permanent denial, raw error-9 toasts — rationale → request → Settings deep-link; never start recognizer ungranted; permission matrix as acceptance criteria
6. **SpeechRecognizer leaks across recomposition/navigation** — missing `destroy()` holds audio + `ERROR_RECOGNIZER_BUSY` + LeakCanary failures — encapsulated controller with create-on-start/destroy-on-dispose, mic disabled while listening, dictation appends at cursor and never auto-sends
7. **Tavily leftovers (key, client, tests, docs)** — dead code still compiling/running, unrotatable secret — grep-gated deletion checklist incl. Keystore upgrade migration, DDG coverage replacing deleted tests, docs copy audit
8. **Removals strand rotation/cleanup** — no compromised-key rotation, no bulk chat cleanup — keep endpoint edit-overwrite + per-endpoint delete; delete-all relocates (not vanishes) with its confirmation; no missing-key endpoint selectable

## Implications for Roadmap

Based on research, suggested phase structure (5 phases; removals before additions where they overlap):

### Phase 1: Tavily Contract Move + Deletion Slice (DDG-only)
**Rationale:** Contract-first order is mandatory (Tension C) — the shared outcome interface blocks every consumer; all help/settings/copy work references the same screens and must come after.
**Delivers:** `SearchOutcome` moved/renamed with consumers re-pointed (green build, zero behavior change); then DDG-only simplification, Tavily API/DTO/repo/DI/keystore/Settings-card/tests/strings (EN+ES) deleted, Keystore one-shot upgrade migration, `grep -ri tavily` zero in code.
**Addresses:** Tavily removal → DDG-only (P1, MEDIUM); keeps DDG repo + `[WEB CONTEXT]` fusion untouched.
**Avoids:** Pitfalls 7 (leftovers), 10 (copy drift — audit rides along), Anti-Patterns 3–4.

### Phase 2: Drawer Cluster + Settings Cleanup + Help Rewrite + Empty-State CTAs
**Rationale:** Drawer cluster batches in one phase (Tension D — same composables, avoids merge churn); Help rewrite must follow the Tavily cut; empty-state CTAs reuse existing nav callbacks with zero new destinations.
**Delivers:** Drawer footer parity (typography token) + delete-all-chats row above Models (existing logic + confirm dialog re-parented) + drawer empty-state "Download a model" → Catalog + Web Options drawer removal; Settings key-delete/Data-section removal with edit-overwrite rotation intact; Help short/minimal rewrite (EN+ES) + copy audit (Help/Settings/README agree DDG-only); Models/Endpoints empty-state CTAs; catalog "Use in Chat" via shared select path.
**Addresses:** Drawer cluster, catalog funnel CTAs, help rewrite, settings cleanup (P1/P2 mix).
**Avoids:** Pitfalls 8 (stranded rotation/cleanup), 9 (CTA nav regressions — nav map + Back-stack sweep), 10 (docs drift).

### Phase 3: Voice Dictation (inline SpeechRecognizer + Intent fallback)
**Rationale:** Independent of Phases 1–2 (touches only chat input pill + append path) but scheduled after UI churn settles to avoid `ChatScreen`/`ChatInputBar` merge conflicts; Intent fallback ships in the same phase behind the availability branch.
**Delivers:** RECORD_AUDIO manifest + rationale/request/Settings-escape flow; `VoiceInputManager` holder with partials → draft insert-at-cursor (never overwrite/auto-send), listening state, `destroy()` discipline; mic hidden when no recognizer; `ChatInputBar` stateless mic params.
**Uses:** Zero new deps; `isRecognitionAvailable()` / on-device (API 31+) gates.
**Avoids:** Pitfalls 4 (missing recognizer), 5 (permission dead-ends), 6 (lifecycle leaks); performance traps (recomposition storm, busy-retry).

### Phase 4: Play In-App Review (ambient trigger)
**Rationale:** Fully independent and smallest slice — good last; trigger policy (success-moment rule + cooldown) is a design decision landing before code.
**Delivers:** `review`/`review-ktx` 2.0.2 + Hilt wrapper + DataStore eligibility (turns/days/cooldown) + single post-success call site off the inference scope; optional Store deep-link affordance; Fake unit tests + internal-track real-dialog + no-show negative evidence.
**Addresses:** Play in-app rating (P2, invisible infra).
**Avoids:** Pitfalls 1 (API-bound CTA), 2 (Fake-only testing), 3 (blocking chat flow).

### Phase 5: v3.0 Regression Sweep
**Rationale:** Every pitfall file assigns verification to a closing sweep; Tavily deletion + drawer moves + voice + review each need cross-cutting asserts no build phase owns.
**Delivers:** Zero-grep Tavily gate + no-Tavily-network smoke + suite green post-deletion; cancel-guard re-runs (single-flight, grounding) with prompt enabled; GMS-less emulator voice matrix; CTA nav/Back-stack sweep incl. shared-select-path assert; LeakCanary voice leg; DataStore cooldown persistence across restarts.
**Avoids:** Locks Pitfalls 2, 3, 4, 6, 7, 9 — the "Looks Done But Isn't" checklist executed as exit gate.

### Phase Ordering Rationale

- **Contract-first, then leaves:** the outcome-interface move unblocks all grounding-adjacent work with zero behavior change; deletions land before the UI that documents them (help/copy) — per ARCHITECTURE build order steps 1–2.
- **Batch by file, not by theme:** the drawer cluster shares composables (one phase); CTAs/catalog/help share no interdependencies once Tavily is gone (parallelizable within Phase 2, split into two plans if large: settings/drawer one plan, CTAs/catalog/help another).
- **Independents last, settled-first:** voice and review touch disjoint surfaces and could parallelize, but voice waits for `ChatScreen` churn to settle (merge-conflict avoidance) and review is smallest-last; neither blocks the other.
- **Pitfall-driven tail:** the regression sweep exists because six of ten pitfalls require device/track/grep evidence no build phase produces on its own.

### Research Flags

Phases likely needing deeper research during planning (`/gsd-plan-phase --research-phase`):
- **Phase 1 (Tavily slice):** MEDIUM — DDG `.result__a`/`.result__snippet` selector stability is unofficial and DDG may serve bot-challenges (HTTP 202); pin selectors against a live fetch at plan time and confirm the challenge-page degradation path. Also verify no stray `@Inject @Named("tavily")` sites beyond the repo and whether `deleteKey` stays for endpoint flows.
- **Phase 3 (Voice):** LOW-MEDIUM — `SpeechRecognizer` partial/error-code behavior is framework-stable, but verify `EXTRA_ENABLE_FORMATTING` (API 33) guard avoidance, on-device availability API (API 31+) expectations, and whether `activity-compose` needs an explicit catalog dep for launchers.
- **Phase 4 (Review):** LOW — official docs current (2.0.2, guide updated 2026-09-18); only verify artifact coordinates/version at plan time and define the eligibility rule (turns/days/cooldown, once-per-version).

Phases with standard patterns (skip research-phase):
- **Phase 2 (drawer/settings/help/CTAs):** well-documented — existing nav callbacks, no new destinations, deletion + typography-token mechanics; codebase-verified file/line anchors in ARCHITECTURE.md.
- **Phase 5 (regression):** execution-only — checklist-driven, no new APIs.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Play 2.0.2 + SpeechRecognizer/RecognizerIntent verified against official docs (Sept 2026); repo build files first-party (nav/OkHttp/Jsoup/gate); DDG selectors MEDIUM (unofficial endpoint, convergent community backends) |
| Features | HIGH | Review quota/no-CTA rules + STT patterns from official docs; scope items are deletions/simplifications of already-built surfaces with repo-verified anchors |
| Architecture | HIGH | Every integration point cites an existing file/symbol (NavGraph, ChatScreen/InputBar, DDG/Tavily repos, NetworkModule, ApiKeyStore, Settings/Help screens); Play/SpeechRecognizer API details MEDIUM (framework-stable) |
| Pitfalls | HIGH | Review quota/testing + SpeechRecognizer contract from official docs; removal-cleanup from codebase evidence (v2.2 orphan precedent, v2.5 leak-harness pattern); Compose-lifecycle specifics MEDIUM |

**Overall confidence:** HIGH

### Gaps to Address

- **DDG selector pinning:** `.result__a` / `.result__snippet` names need pinning against a live fetch during Phase 1 planning; keep the single-`DuckDuckGoProducer`-behind-interface seam so swap cost stays one file — handle via `--research-phase` on Phase 1.
- **Review artifact coordinates:** re-verify `review`/`review-ktx` latest stable at Phase 4 plan time (docs say 2.0.2 as of 2026-09-18); confirm `./gradlew check` passes the `auditDependencies` gate after adding.
- **`activity-compose` explicit dep:** ARCHITECTURE notes `rememberLauncherForActivityResult` may need an explicit catalog entry — verify at Phase 3 plan time rather than assuming transitivity.
- **Eligibility rule + Store-link placement:** the ambient trigger definition (N turns + days installed + cooldown, once per version) and whether a Store deep-link appears in Settings/Help are product decisions for Phase 4 discussion, not research gaps.
- **Endpoint `deleteKey` retention:** milestone removes the key-delete affordance but endpoint deletion flows may still need store-level `deleteKey` — verify at Phase 1/2 plan time (ARCHITECTURE boundary table flags this).

## Sources

### Primary (HIGH confidence)
- Play In-App Review guide + Kotlin/Java integration + testing (quota, no-CTA rule, no pre-questions, FakeReviewManager scope, internal-track prerequisites) — developer.android.com/guide/playcore/in-app-review{,/kotlin-java,/test}
- `SpeechRecognizer` API contract (main-thread, `destroy()` MUST, RECORD_AUDIO, `isRecognitionAvailable`/`isOnDeviceRecognitionAvailable`, error codes) + `RecognizerIntent` extras — developer.android.com/reference/android/speech/{SpeechRecognizer,RecognizerIntent}
- Runtime permission principles (ask in context, always offer cancel) — developer.android.com/training/permissions/requesting
- Repo HEAD first-party evidence: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `scripts/audit-dependencies.sh`, `NavGraph.kt` + `Screen.kt`, `ChatScreen.kt`/`ChatInputBar.kt`/`ModelSelector.kt`, `SettingsScreen`+VM+UiState, `HelpScreen.kt`, `ApiKeyStore.kt`, `NetworkModule.kt`, `TavilySearchRepository.kt`, `DuckDuckGoSearchRepository.kt`, `WebSearchToolSet.kt`, `AndroidManifest.xml` RECORD_AUDIO note

### Secondary (MEDIUM confidence)
- DDG `html.duckduckgo.com/html/` non-JS endpoint + community DDGS `html`/`lite` backends + OpenClaw DDG provider doc (key-free HTML-scrape, experimental/bot-challenge caveats, Instant Answer insufficiency)
- RecognizerIntent-vs-SpeechRecognizer tradeoff consensus (Intent = simple/no-permission; SpeechRecognizer = control/permission/error-handling) — community sources
- Compose permission rationale + Settings-route patterns — community consensus adapted to Warped infra

### Tertiary (LOW confidence)
- None outstanding — no single-source claims load-bearing for the roadmap; DDG markup details flagged above as a plan-time verification, not a decision risk.

---
*Research completed: 2026-10-02*
*Ready for roadmap: yes*
