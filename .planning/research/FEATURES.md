# Feature Research: v3.0 Chat UX + Voice Dictation

**Domain:** Android LLM chat app (Warped — LM Studio equivalent, Kotlin + Compose)
**Researched:** 2026-10-02
**Confidence:** HIGH (Play In-App Review + STT patterns verified against official Android docs; scope items are deletions/simplifications of already-built surfaces)
**Scope:** Subsequent milestone — covers ONLY the 11 new v3.0 items. Everything else (local/remote chat, static catalog + downloads, Models & Endpoints CRUD, grounding, syntax highlighting, settings, help) already exists.

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist. Missing these = product feels incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Voice dictation into chat input (STT → text field) | Every major chat/messaging app (WhatsApp, Telegram, Gboard mic) offers mic-to-text in the message box; chat is the app's core surface so a mic button is expected, not a novelty | LOW | Use `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` + `LANGUAGE_MODEL_FREE_FORM` via Activity Result API, append best match to existing input text (don't overwrite). No `RECORD_AUDIO` permission needed for the Intent path (recognition Activity owns the mic). Graceful fallback: disable/hide mic button when no recognizer resolves (`queryIntentActivities` empty) or on `ActivityNotFoundException`. Dictation only — recognized text lands in the input field for editing before send, no audio messages stored/sent. Dependency: existing chat input pill composable. |
| Model-drawer empty-state "Download a model" CTA → catalog | An empty picker that strands the user with no next step is a dead end; Material empty-state guidance is illustration + explanation + single primary action. User has no local model = the only sensible action is the catalog | LOW | Text-button/CTA in the existing model-drawer (unified model+endpoint picker from v1.7/v1.8) visible only when downloaded-models list is empty; navigates to existing Model Catalog screen via existing nav route. Dependency: model drawer + catalog route. Zero new screens. |
| Models & Endpoints empty-state CTAs ("Download a local model" / "Add a new Endpoint") | Same dead-end principle, one per section: empty local-models list → catalog; empty endpoints list → existing endpoint add/edit screen. Two CTAs because the screen has two independent lists | LOW | Conditional empty-state rows in the existing Models & Endpoints screen reusing its current navigation actions (catalog route, endpoint editor). Dependency: Models & Endpoints screen only. |
| Catalog "Use in Chat" on downloaded models | After downloading, forcing the user to hunt through a drawer to activate the model breaks the download→chat funnel; competitors (LM Studio "load model", AI Edge Gallery task screens) complete the loop in place | LOW | Button on catalog cards where `download_status == DOWNLOADED`; on tap: set active model (same ViewModel call the drawer uses) + navigate to chat. Dependency: catalog card composable + existing model-activation path. Must be hidden/disabled while downloading. |
| Chat-drawer footer parity (Models/Help/Settings = New Chat text size) | Uniform footer sizing is baseline visual hygiene; mismatched sizes read as a bug, not a design choice | LOW | Single typography token change in the existing chat drawer footer. Dependency: chat drawer composable only. Trivial, batch with item below. |
| Help screen rewrite (short/minimal) | Help must be scannable; the current screen still references removed surfaces (Tavily key step, `help_s7_step5`) which actively misleads after the DDG-only cut | LOW | Rewrite `HelpScreen.kt` + `help_*` strings (EN + ES `values-es`) as a short minimal list; delete stale Tavily/search-key steps. Dependency: HelpScreen + strings; MUST be sequenced after/with Tavily removal or help will document a dead feature. Batch the string edits together. |

### Differentiators (Competitive Advantage)

Features that set the product apart. Not required, but valuable.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Play in-app star rating | Timely ratings lift Play listing conversion; in-app flow (no store redirect) measurably raises review volume vs "rate us" links | LOW | `com.google.android.play:review:2.0.2` (+ `review-ktx`), `ReviewManagerFactory` → `requestReviewFlow()` (pre-cache) → `launchReviewFlow()`. **Critical official constraints:** (a) NEVER trigger from a "Rate us" CTA button — quota (~1/month, undisclosed, silently no-ops) makes buttons look broken; fire at a natural success moment (e.g. after N successful chat turns / returning to chat), (b) no pre-qualifying questions ("Do you like the app?") — Play policy violation risk, (c) swallow all errors silently, never block user flow, (d) test via internal test track (quota not enforced there) + `FakeReviewManager` for unit tests. So the v3.0 "Play star rating in-app" item = automatic/ambient trigger, NOT a settings button. Dependency: chat screen lifecycle + DataStore counter (turns completed / days installed). Sources: developer.android.com/guide/playcore/in-app-review (+kotlin-java, +test). |
| Delete-all-chats relocated to chat-drawer bottom above Models | Destructive action lives where the objects live (drawer lists the chats) instead of buried in Settings → Data; matches messaging-app convention (bulk delete near the list) and shortens the path | LOW | Move existing delete-all logic (confirmation dialog + Room clear) from Settings Data section into chat drawer footer above Models entry; delete the Settings Data section entirely. Dependency: chat drawer + Settings screen + existing delete-allChats path. Requires confirm dialog (destructive, irreversible) — keep the existing one, just re-parent it. Batch with footer-parity item (same file). |
| Web Options removed from model drawer (settings only) | Declutters the critical chat-entry path: model drawer goes back to one job (pick a model/endpoint). Grounding toggles (tri-state Sí/No/Heredar + Sin web from v2.3) remain fully available in Settings | LOW | Delete the Web Options block from the model-drawer composable; no Settings work needed (controls already there). Dependency: model drawer only. Pure deletion — lowest risk item in the milestone. |
| Tavily removal → DuckDuckGo-only search | Removes the last API-key friction in the app (Tavily key in Keystore + test-connection + settings UI + error strings + fallback legs in `OpenAIProvider`/`CompatToolLoop`/`LocalToolLoop`/`WebSearchToolSet`); DDG needs no key so grounding becomes zero-config for every user | MEDIUM | Delete: `TavilySearchRepository` + key/settings UI + `tavily_*`/`bubble_tavily_*` strings (EN+ES) + fallback call sites (keep DDG leg as the single path) + Keystore `tavily` entry read/write. Keep DDG repository and the `[WEB CONTEXT]` fusion pipeline untouched. Regression net: grounding tests that mock the Tavily fallback must be updated/removed. Cross-cuts providers + agentic loops + settings + help strings, so it is the highest-blast-radius item despite being "just a deletion". Sequence help rewrite + error-string cleanup in the same phase. |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Explicit "Rate this app" button wired to `launchReviewFlow` | Feels like giving users control over when to rate | Quota silently suppresses the dialog → button appears broken; Google explicitly forbids CTA-triggered review flow. A button must deep-link to the Play Store listing instead | Ambient trigger after success moments (the actual v3.0 item) + optional store-link in Settings/Help |
| Pre-rating sentiment gate ("Do you love the app? Yes→review, No→feedback") | Tries to filter 5-star reviews | Direct Play policy violation ("shouldn't ask the user any questions before or while presenting the rating button or card") — removal risk | Trigger unconditionally at good moments; handle feedback via existing channels |
| Continuous/background voice listening or audio messages | "Full voice mode" sounds premium | `SpeechRecognizer` docs warn against continuous recognition (battery/bandwidth); audio messages need storage schema, playback UI, and provider multimodal support — all out of scope (PROJECT.md explicitly defers voice I/O) | One-shot dictation → editable text (the actual v3.0 item); revisit voice mode only after text chat is solid |
| Custom in-app STT engine / on-device model for dictation | Offline dictation parity | Heavy native dependency + model downloads for a convenience feature; platform recognizer already handles offline via `EXTRA_PREFER_OFFLINE` where supported | Platform `RecognizerIntent` (default), `EXTRA_PREFER_OFFLINE=true` hint if offline parity matters later |
| `SpeechRecognizer` + custom listening UI for v1 of dictation | More control, inline UX | Requires `RECORD_AUDIO` runtime permission (new Play declaration + rationale UI), main-thread-only API, manual error/restart handling — all for zero user-visible gain over the Intent dialog in a dictation-only scope | `RecognizerIntent` via Activity Result API; graduate to `SpeechRecognizer` only if partial-results/live-transcription is later requested |
| Keystore key-deletion UI (the item being removed) | "Let users manage keys" | With Tavily gone there are no user-managed keys left (HF token already removed in v2.2); a deletion UI for zero keys is dead surface that suggests secrets exist | Remove as scoped; reintroduce only if a new keyed integration lands |
| Settings Data section / per-chat delete (the items being removed) | Fine-grained data control | Duplicates the drawer bulk action one tap away from the list; two delete paths = two confirmation flows to maintain and test | Single delete-all-chats in drawer (the actual v3.0 item) |

## Feature Dependencies

```
[Play in-app rating]
    └──requires──> [chat success signal (completed turn counter)]
                       └──requires──> [existing chat ViewModel / DataStore]

[Catalog "Use in Chat"]
    └──requires──> [existing model-activation path (drawer uses it)]
                       └──requires──> [catalog download_status field]

[Drawer empty-state CTA] ──navigates──> [existing Model Catalog route]
[Endpoints empty-state CTA] ──navigates──> [existing endpoint editor]
[Delete-all-chats in drawer] ──reparents──> [existing delete-all logic + confirm dialog]
[Help rewrite] ──requires──> [Tavily removal strings finalized]
[Tavily removal] ──touches──> [OpenAIProvider, CompatToolLoop, LocalToolLoop, WebSearchToolSet, Settings, HelpScreen, strings EN+ES]
[Voice dictation] ──requires──> [chat input pill composable]
[Footer parity + delete-all + drawer CTA + Web Options removal]
    └──all touch──> [model/chat drawer composables — batch in one phase]
```

### Dependency Notes

- **Help rewrite requires Tavily removal (ordering):** help currently documents the Tavily key step; rewriting before the cut re-documents a dead feature. Same phase, Tavily deletion first.
- **Drawer cluster batches:** footer parity + delete-all relocation + drawer empty-state CTA + Web Options removal all edit the same drawer composables — one phase avoids merge churn.
- **Tavily removal is the blast-radius item:** it is the only v3.0 change touching data-layer providers and grounding tests; everything else is UI-surface only.
- **Voice dictation is independent:** touches only the chat input pill + result appending; no permission, manifest, or data-layer changes on the Intent path. Can parallelize with anything.
- **Play rating is independent but needs a trigger definition:** decide the success-moment rule (e.g. 3+ successful turns AND 2+ days installed, once per version) before implementation; DataStore counter is new but trivial.

## MVP Definition

v3.0 is a polish milestone, not a product launch — "MVP" here = the shippable slice if the milestone had to be cut.

### Launch With (must-ship for the milestone goal)

- [ ] Voice dictation into chat input — the only new user-facing capability; the milestone's headline
- [ ] Tavily removal (DDG-only) — breaking-behavior change justifying the major bump; everything referencing keys must die together
- [ ] Drawer cluster (empty-state CTA, footer parity, delete-all relocation, Web Options removal) — the visible "polish" users judge v3.0 on
- [ ] Catalog "Use in Chat" + Models & Endpoints empty-state CTAs — closes the download→chat funnel gaps

### Add After Validation (same milestone, separable)

- [ ] Play in-app rating — ambient, invisible when quota-suppressed; safe to land late, zero UI coupling
- [ ] Help rewrite — must follow Tavily removal; land any time after
- [ ] Settings cleanup (key-delete + Data section) — mechanical once delete-all is reparented

### Future Consideration (explicitly out of v3.0)

- [ ] Audio messages / voice replies (TTS) — PROJECT.md Out of Scope ("Voice input/output — defer")
- [ ] Continuous listening / live transcription via `SpeechRecognizer` — graduate only on user demand
- [ ] New keyed search provider replacing Tavily — would resurrect key UI just deleted

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Voice dictation | HIGH (headline capability) | LOW (Intent path, no permission) | P1 |
| Tavily removal DDG-only | HIGH (zero-config grounding, justifies major) | MEDIUM (cross-cutting deletion + test updates) | P1 |
| Model-drawer empty-state CTA | HIGH (unblocks no-model users) | LOW | P1 |
| Catalog "Use in Chat" | HIGH (closes download→chat funnel) | LOW | P1 |
| Delete-all-chats relocation | MEDIUM (better placement, removes settings clutter) | LOW | P1 |
| Models & Endpoints empty CTAs | MEDIUM | LOW | P2 |
| Web Options drawer removal | MEDIUM (declutter) | LOW (pure deletion) | P2 |
| Footer font parity | LOW (cosmetic) | LOW (batch free) | P2 |
| Help rewrite | MEDIUM (removes misleading docs) | LOW | P2 |
| Settings cleanup | LOW (removes dead surface) | LOW | P2 |
| Play in-app rating | MEDIUM (ratings lift, invisible infra) | LOW | P2 |

**Priority key:**
- P1: Must have for milestone goal (headline + breaking changes + core funnel)
- P2: Should have, separable, batch where files overlap

## Competitor Feature Analysis

| Feature | LM Studio (desktop) | AI Edge Gallery (Android, Google) | Our Approach |
|---------|---------------------|-----------------------------------|--------------|
| Voice input | None (desktop typing assumed) | None (task-runner focus) | One-shot dictation → editable text; differentiator on mobile, no competitor has it |
| In-app rating | N/A (direct download) | Play listing only | Ambient In-App Review at success moments; no CTA button per Google guidance |
| Empty-state CTAs | Model-load prompts point at search | Task screens assume models present | Inline CTAs routing to existing catalog/editor — matches Material empty-state pattern |
| Download → use funnel | "Load model" in place | Model auto-loads into task | "Use in Chat" on downloaded catalog cards — parity with LM Studio behavior |
| Search grounding keys | N/A (local only) | N/A | DDG-only zero-config; removing Tavily removes the last key friction — unique simplicity vs key-heavy wrappers |

## Sources

- Play In-App Review guide — https://developer.android.com/guide/playcore/in-app-review (HIGH: quota behavior, no-CTA rule, no pre-questions rule)
- Integrate in-app reviews (Kotlin/Java), `review:2.0.2` + `review-ktx:2.0.2`, `ReviewManagerFactory` — https://developer.android.com/guide/playcore/in-app-review/kotlin-java (HIGH)
- Test in-app reviews (internal track bypasses quota, `FakeReviewManager`) — https://developer.android.com/guide/playcore/in-app-review/test (HIGH)
- `SpeechRecognizer` API reference (main-thread only, `RECORD_AUDIO` required, not for continuous use) — https://developer.android.com/reference/android/speech/SpeechRecognizer (HIGH)
- `RecognizerIntent` API reference (`ACTION_RECOGNIZE_SPEECH`, `LANGUAGE_MODEL_FREE_FORM`, `EXTRA_PREFER_OFFLINE`) — https://developer.android.com/reference/android/speech/RecognizerIntent (HIGH)
- RecognizerIntent vs SpeechRecognizer tradeoffs (Intent = simple + consistent UI + no permission; SpeechRecognizer = control + custom UI + permission + error handling) — StackOverflow/community consensus (MEDIUM)
- Existing codebase: `HelpScreen.kt`, `TavilySearchRepository` usages (`OpenAIProvider`, `CompatToolLoop`, `LocalToolLoop`, `WebSearchToolSet`), Tavily strings in `values/strings.xml` + `values-es/strings.xml`, manifest note on removed `RECORD_AUDIO` (HIGH — verified in repo)

---
*Feature research for: v3.0 Chat UX + Voice Dictation*
*Researched: 2026-10-02*
