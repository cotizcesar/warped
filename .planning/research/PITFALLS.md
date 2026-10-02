# Pitfalls Research: v3.0 Chat UX + Voice Dictation

**Domain:** Adding Play In-App Review + voice dictation to an existing Android chat app (Warped, Kotlin + Compose + Hilt), and deleting Tavily integration + destructive settings surfaces
**Researched:** 2026-10-02
**Confidence:** HIGH for Play Review quota/testing and SpeechRecognizer API behavior (official Android docs); MEDIUM for Compose-lifecycle and removal-cleanup specifics (community + codebase evidence); LOW where noted.

This file is scoped to the v3.0 milestone only: what breaks when you *add* Review + voice to this chat app, and when you *delete* Tavily + settings surfaces. General Android pitfalls from prior milestones are not repeated here.

## Critical Pitfalls

### Pitfall 1: Play Review triggered from a user-visible CTA (button/menu item)

**What goes wrong:**
A "Rate us ★" button is wired directly to `launchReviewFlow()`. Most taps show nothing — Play's time-bound quota silently suppresses the dialog (quota may be as tight as roughly once per month per user, exact value undisclosed and changeable without notice). Users perceive a dead button; support/deletion pressure follows; the team "fixes" it by calling the API more aggressively, which changes nothing.

**Why it happens:**
Developers treat the Review API like a normal dialog (`show()` = visible). It is not — `requestReviewFlow()` + `launchReviewFlow()` is a *request* Play may decline silently, by design. Google's own guidance explicitly says: do not attach a call-to-action to the API.

**How to avoid:**
- Never wire a button directly to `launchReviewFlow()`. The in-app flow fires only from *opportunistic, app-chosen* moments (e.g., after N successful chat turns, post-download success) with internal gating logic (DataStore flag: last-prompt timestamp, session count, cooldown).
- If v3.0 wants a visible "Rate" affordance (likely in the rewritten Help screen or Settings), that affordance must deep-link to the Play Store listing (`market://details?id=...` with `https://` fallback) — never to the in-app API.
- Treat `requestReviewFlow()` failure and silent no-show as the *normal* path: always continue the user flow unchanged. Log/skip silently; never show "please try again" or block navigation on it.

**Warning signs:**
- Plan shows a star button calling `ReviewManager` directly.
- Acceptance criteria say "tapping Rate shows the review dialog" — untestable in production by definition.
- No cooldown/prompt-count state in the design.

**Phase to address:** Review-flow phase (first). The gating policy (when to prompt, cooldown storage, Store-link fallback) is a design decision that must land before any code.

---

### Pitfall 2: Review flow tested only with FakeReviewManager — shipping untested production path

**What goes wrong:**
`FakeReviewManager` always returns a fake `ReviewInfo` and success — it renders no UI and performs no review. A suite green on Fake proves only that *post-completion callbacks* run. The real path (Play Store presence, `requestReviewFlow()` Task failure modes, Activity-result handling, R8/ProGuard keeping Play classes) goes unverified, and release-day failures (silent no-op or crash on `launchReviewFlow`) surprise the team.

**Why it happens:**
Fake is frictionless (unit-test friendly) while real testing requires an internal test track upload, a tester account in the Play library, and quota-aware expectations. Teams stop at Fake.

**How to avoid:**
- Three-tier test strategy, each with a distinct purpose:
  1. **Unit (FakeReviewManager):** verify app behavior *after* completion (cooldown flag written, no crash, flow continues). Nothing more.
  2. **Internal test track on a real device:** the only way to see the real dialog. Prerequisites: tester account on internal track, primary account selected in Play Store, app installed from Play at least once (puts it in the user's library), no existing review from that account. Note quota is *not* enforced for internal-track installs — useful for testing, but means production will show the dialog *less* often than testing suggests.
  3. **Release-UAT negative test:** confirm the app behaves fine when the dialog does *not* appear (fresh account that already reviewed, or quota-hit account).
- Keep Play Review dependency current (`com.google.android.play:review` / `review-ktx` 2.x) and verify R8 keep rules cover Play Core Task classes (release-build smoke, not just debug).
- Troubleshooting checklist for "dialog never shows in test": appId must exist at least on internal track; tester must have downloaded from Play; primary-account mismatch and enterprise-protected accounts are the classic silent blockers.

**Warning signs:**
- Test plan mentions only FakeReviewManager.
- No internal-track build ever uploaded during the phase.
- Dialog-show rate in testing assumed to equal production rate.

**Phase to address:** Review-flow phase. Require an internal-track verification item in the phase's exit criteria, distinct from unit tests.

---

### Pitfall 3: Review flow blocks or hijacks the chat/inference flow

**What goes wrong:**
`requestReviewFlow()` is kicked off on the chat screen's critical path (e.g., `await()` on the Task before sending a message, or `launchReviewFlow` from the streaming completion callback). Play Task latency or failure stalls message send; worse, the review Activity overlay interrupts an active LiteRT-LM inference turn or a grounding fan-out, colliding with v2.1's single-flight cancel discipline and v2.3's per-send grounding scope.

**Why it happens:**
"Prompt after a good chat turn" gets implemented literally *inside* the turn-completion handler, sharing its coroutine scope.

**How to avoid:**
- Fire the review trigger from a **decoupled, app-foreground scope** (e.g., `Application`-scoped or a dedicated `ReviewPromptController`), never from the inference/grounding coroutine scope. Check cooldown + eligibility *after* the turn fully settles (streaming done, rows committed), with a small delay and an idle/app-foreground guard.
- `requestReviewFlow()` failure (Task exception) must be swallowed with a metric/log only — it must never surface as a chat error, snackbar, or retry.
- Verify Stop-means-stop still holds: launching review must not retain inference jobs, SSE streams, or grounding fan-out (v2.1/v2.3 cancel guards).

**Warning signs:**
- Review code lives in the chat ViewModel's send path or streaming collector.
- Review Task awaited with no timeout / on `Dispatchers.Main` in the send pipeline.
- Stop button behavior changes after adding the prompt.

**Phase to address:** Review-flow phase, verified by the regression phase (re-run single-flight cancel + grounding-cancel tests with the prompt enabled).

---

### Pitfall 4: Voice dictation assumes a recognizer exists — crash/no-op on devices without one

**What goes wrong:**
`SpeechRecognizer.createSpeechRecognizer()` throws or `startListening` fails with `ERROR_CLIENT` on devices with no recognition service (de-Googled ROMs, some Fire/Chinese-OEM devices, work profiles with services disabled, emulators without Google apps). If the mic button is always visible and unguarded, tapping it crashes or silently does nothing on exactly the offline/privacy-conscious devices Warped's local-LLM users disproportionately own.

**Why it happens:**
Developers test on Pixel/GMS hardware where Google's recognizer is always present, and never call the availability gate.

**How to avoid:**
- Gate mic-button visibility on `SpeechRecognizer.isRecognitionAvailable(context)` (checked at composition/input-screen entry, cheap PackageManager query). No recognizer → hide the mic icon entirely (preferred) or show a disabled state with an explanatory tooltip — never a live button that dead-ends.
- Prefer the **on-device recognizer path** where available (`createOnDeviceSpeechRecognizer` + `isOnDeviceRecognitionAvailable`, API 31+) for Warped's offline-first story; fall back to network recognizer only when the user consents implicitly by tapping mic while online. Never promise "works offline" for dictation unless the on-device gate passes *and* the language pack is downloaded (`ERROR_LANGUAGE_UNAVAILABLE` is the tell).
- Handle `ERROR_LANGUAGE_NOT_SUPPORTED` / `ERROR_LANGUAGE_UNAVAILABLE` with a specific message ("voice input isn't available for this language on this device"), not a generic error.

**Warning signs:**
- Mic button rendered unconditionally.
- No `isRecognitionAvailable` call anywhere in the voice path.
- Test matrix is Pixel-only.

**Phase to address:** Voice phase (entry gate), verified on a GMS-less emulator image in the regression phase.

---

### Pitfall 5: RECORD_AUDIO permission flow dead-ends (one-shot ask, no rationale, no settings escape)

**What goes wrong:**
First tap on mic fires the system permission dialog with no context; user denies (possibly "don't ask again"); mic now permanently dead with no in-app path to recovery. Or the app loops re-requesting after permanent denial, which Android silently ignores — looking broken. Worst case: `ERROR_INSUFFICIENT_PERMISSIONS` (error 9) surfaces as a cryptic toast, including the known trap where the *Google recognizer app itself* lacks mic permission.

**Why it happens:**
Runtime-permission UX has three states (granted / denied-with-rationale / permanently-denied) but implementations handle one. Compose + Accompanist/activity-result launchers make it easy to fire-and-forget.

**How to avoid:**
- Pre-permission rationale: first mic tap shows an in-app explainer ("voice dictation needs the microphone; audio is only used for transcription") with Cancel, *then* launches the system request. Follow platform guidance: ask in context, always offer cancel.
- Permanent-denial path: when `shouldShowRequestPermissionRationale` returns false after a denial, show a Snackbar/dialog routing to app Settings (`ACTION_APPLICATION_DETAILS_SETTINGS`), not another request.
- Never start `SpeechRecognizer` before permission is granted — pre-check with `ContextCompat.checkSelfPermission`; map `onError(ERROR_INSUFFICIENT_PERMISSIONS)` to the settings-route UI, not a raw error code.
- Declare `RECORD_AUDIO` in the manifest (required regardless) and keep the permission out of onboarding — request only at first mic use.

**Warning signs:**
- `RequestPermission` launcher with no rationale branch.
- No settings-deep-link string in the voice UI.
- Error 9 shown to users verbatim.

**Phase to address:** Voice phase. Permission-state matrix (granted / denied / permanently-denied / no-recognizer) as explicit acceptance criteria.

---

### Pitfall 6: SpeechRecognizer leaks across recompositions and navigation (main-thread + destroy discipline)

**What goes wrong:**
`SpeechRecognizer` must be used from the main thread and **must** have `destroy()` called when done. In Compose, creating it in a composable without `DisposableEffect`, or holding it in a ViewModel that outlives the input screen, leaks the recognizer service connection — each leaked instance holds audio resources and can cause `ERROR_RECOGNIZER_BUSY` on subsequent attempts, battery drain, and (per v2.5's leak-harness precedent) a LeakCanary failure on the scripted tour.

**Why it happens:**
Recognizer is callback-based (`RecognitionListener`) in a Flow/coroutine codebase; bridging it ad hoc (listener writing to mutable state, recognizer stored in a `remember` without cleanup) skips the lifecycle contract. Partial dictation results + recomposition churn make it worse.

**How to avoid:**
- Encapsulate in a `VoiceDictationController` (or repository) exposing `Flow<DictationState>` (`Idle/Listening/PartialResult/FinalResult/Error`), internally bridging `RecognitionListener` → `callbackFlow`. Lifecycle rule: create on mic-start (or `DisposableEffect` entry), `cancel()` + `destroy()` on stop/dispose/navigation-away — never retain across conversation switches.
- `stopListening()` on send; `cancel()` + `destroy()` on screen disposal. Guard double-start (`ERROR_RECOGNIZER_BUSY` / `ERROR_TOO_MANY_REQUESTS`) by disabling the mic button while `Listening`.
- Dictation appends to the existing message `TextField` buffer (insert at cursor), never replaces it and never auto-sends. Partial results update the buffer; only a final result (or explicit stop) commits. This preserves drafts, grounding chips (`Sin web`), and per-chat toggle state.
- Add the voice tour leg to the LeakCanary scripted run (start dictation → stop → navigate away → assert clean), following the v2.5 Phase 61/62 pattern.

**Warning signs:**
- `createSpeechRecognizer` inside a `@Composable` without `DisposableEffect(onDispose { destroy() })`.
- Mic stays "listening" after navigating drawers/catalog and back.
- Second dictation attempt fails with busy errors.

**Phase to address:** Voice phase (controller design), locked by the regression phase (LeakCanary leg + busy-retry test).

---

### Pitfall 7: Tavily removal leaves billable/secret-bearing leftovers (key, client, tests, docs)

**What goes wrong:**
The UI toggle and search call-site are deleted, but remnants survive: the Tavily API key in EncryptedSharedPreferences/Keystore, the `@Named("tavily")` OkHttp client in `NetworkModule`, `TavilySearchRepository.kt` + `TavilySearchRepositoryTest`, string resources, Settings key field + test-connection UI, README/MILESTONES references — and any background retry/worker still holding a Tavily reference. Result: dead code that still compiles (or worse, still *runs* billing-adjacent network calls), a secret with no UI to rotate/delete, and reviewer confusion about which search path is live. Warped already has this exact scar: the v2.2 HF-token removal left an orphaned Keystore `huggingface_token` entry carried as a known deferred item through v2.3–v2.5.

**Why it happens:**
Deletions are verified by "new behavior works" (DDG-only search returns results) rather than by "old surface is fully gone." Keystore/prefs entries are invisible in code review; DI bindings survive because nothing fails to compile.

**How to avoid:**
- Deletion checklist enforced in the removal phase (grep-gated):
  1. `TavilySearchRepository.kt` + `TavilySearchRepositoryTest` deleted; DDG fallback/delegate legs referencing Tavily (`DuckDuckGoSearchRepository` fallback + keyed image-direct legs) re-pointed to DDG-only or removed.
  2. `@Named("tavily")` binding + dedicated Bearer client removed from `NetworkModule`; confirm remaining client count.
  3. Tavily key **deleted from Keystore/EncryptedSharedPreferences on upgrade** via an explicit migration (do not repeat the HF-token orphan — this time delete, with a logged one-shot migration), and the Settings key field + test-connection UI removed.
  4. Room/DataStore: any Tavily-tagged source rows, prefs keys, or `GroundingPrecedence`/capability-matrix entries referencing Tavily updated; existing conversations with Tavily citations still render (read-only legacy rows, per the v2.2 skills-row precedent) but no new Tavily path exists.
  5. Docs updated (README agentic-grounding line, MILESTONES WEB-09, PROJECT validated requirement "stores a Tavily key" moved to Out of Scope/removed).
- Verification: `grep -ri tavily` returns zero source hits (docs excepted and intentional); full unit suite green *after* deletions (dead tests fail loudly if left); release smoke confirms no Tavily network call (proxy/log check).

**Warning signs:**
- PR deletes UI but `TavilySearchRepository` still exists.
- "Orphaned key, harmless" appears in deferred items again.
- DDG repository still imports Tavily classes.

**Phase to address:** Removal phase (Tavily deletion), with a zero-grep verification item owned by the regression phase.

---

### Pitfall 8: Removing key-deletion and Data/delete-chats strands users (no rotation, no recovery, no bulk cleanup)

**What goes wrong:**
Two removals with opposite risks ship together: (a) removing the Keystore key-deletion ability leaves users with *no way to rotate/revoke a compromised endpoint API key* — a leaked key becomes permanent; (b) removing the Data section + delete-all-chats leaves users with *no bulk cleanup* — storage grows unbounded and a user wanting a fresh start must delete conversations one by one. Both look like "simplification" but remove the only escape hatches for real situations.

**Why it happens:**
"Remove destructive surfaces = safer" is half-true: it prevents accidental loss but also prevents intentional recovery. The design omits replacement paths.

**How to avoid:**
- Key management without key-deletion: keep **overwrite/replace** (editing an endpoint's key overwrites the Keystore entry) and keep **per-endpoint delete** (deleting the endpoint deletes its key). What is removed is only the standalone "delete key, keep endpoint" action that orphans endpoints pointing at missing credentials. Acceptance: compromised-key rotation achievable in ≤2 taps (edit endpoint → save new key).
- Delete-all-chats relocation (drawer bottom above Models): the move itself is the mitigation — bulk delete *stays*, relocated, with its existing confirmation. Regression-test that the relocated action deletes all conversations, that undo/timeout semantics (if any) survive the move, and that no second copy remains in Settings.
- Never leave an endpoint referencing a deleted key or a chat referencing a deleted model: deletion paths must null-or-reassign foreign references (chat keeps `model_id` text for history display; active session falls back to auto-select).

**Warning signs:**
- Settings diff shows deletions with no corresponding edit/overwrite path.
- Endpoint with wiped key still selectable in the Models & Endpoints picker (traffic-light status must show disconnected/error, never crash).
- Two delete-all entry points after the move (drawer + Settings remnant).

**Phase to address:** Removal phase for the cuts; drawer/settings UX phase for the relocated delete-all and rotation-path verification.

---

### Pitfall 9: Drawer + catalog CTA navigation regressions (empty-state CTAs route wrong or strand back-stack)

**What goes wrong:**
Five navigation-touching changes land at once (model-drawer "Download a model" → Catalog; chat-drawer footer reorder + delete-all relocation; Models & Endpoints empty-state CTAs; Catalog "Use in Chat" on downloaded models; Web Options removed from drawer). Typical breakage: CTA launches the wrong destination, deep-links drop drawer/back-stack state (Back exits the app instead of returning to chat), "Use in Chat" selects the model but doesn't navigate back to the input, or Web Options becomes unreachable (removed from drawer before its Settings anchor exists).

**Why it happens:**
Drawer destinations are stringly-routed in several places; empty-state CTAs are added per-screen without a navigation map; "Use in Chat" touches model-selection state owned by a different screen.

**How to avoid:**
- Single navigation map for v3.0 before coding: every new CTA gets (source → destination → back behavior) specified, including: model-drawer CTA → Catalog → back returns to drawer/chat; "Use in Chat" → sets active model *and* pops to chat input with the model chip updated; Web Options reachable in Settings with the drawer entry removed (no orphaned route).
- "Use in Chat" must reuse the existing model-select path (same function the picker calls), not a parallel setter — otherwise traffic-light status, auto-select, and Thinking-config state diverge.
- Footer parity (same text size as New Chat) is a theme/typography token change, not per-item font overrides — prevents future drift.
- Regression sweep: each CTA tapped from each entry point, Back pressed, state asserted (active chat preserved, model selected, no duplicate destinations on the stack).

**Warning signs:**
- CTA handlers calling `navigate()` with hardcoded routes duplicated across files.
- "Use in Chat" writes a different prefs key than the picker.
- Back from Catalog exits to launcher instead of returning to chat.

**Phase to address:** Drawer/catalog UX phase (build), regression phase (navigation sweep incl. Back-stack assertions).

---

### Pitfall 10: Help rewrite + grounding-simplification docs drift (Help says Tavily, Settings says web-toggle that no longer exists)

**What goes wrong:**
Help is rewritten "short and minimal" while Tavily references survive elsewhere (README agentic-grounding line, old Help screenshots, Settings descriptions mentioning search providers, per-chat toggle copy implying multi-provider choice). Users read Help promising one thing and see another; reviewers flag stale provider claims during Play review.

**Why it happens:**
Copy lives in many files; the rewrite touches one.

**How to avoid:**
- Copy audit as part of the removal phase: single source of truth for "search = DuckDuckGo only" propagated to Help, Settings copy, README, and in-app empty states in the same PR/plan.
- Keep the rewritten Help minimal but *complete* on: offline model chat, endpoint setup + key rotation path (see Pitfall 8), voice dictation permission note, review/rate Store link (see Pitfall 1), and where Web Options now lives.

**Warning signs:**
- `grep -ri tavily` clean in code but stale in `.md` / string resources.
- Help mentions a Settings section that was deleted.

**Phase to address:** Removal phase (copy audit rides along — cheapest when the code is being deleted anyway).

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| FakeReviewManager-only tests, no internal-track check | Phase closes fast without Play Console uploads | Real-path failures (Task errors, R8, silent no-show handling) found by users | Never — internal-track verification is the exit gate |
| Mic button always visible, no `isRecognitionAvailable` gate | Less branching, simpler UI code | Dead/crashing button on GMS-less devices; 1-star reviews from privacy users | Never |
| Leaving Tavily Keystore key "harmlessly orphaned" (HF-token precedent) | Skips a migration | Second orphaned secret; no UI to rotate/delete; Play data-safety questions linger | Never — one-shot delete migration this time |
| Deleting tests alongside Tavily without DDG coverage replacement | Suite stays green trivially | Search path loses regression net; DDG-only bugs slip through | Only if DDG-equivalent cases are added in the same change |
| Hardcoded CTA routes per screen | Fast to wire | Drawer/catalog nav drift, broken Back stacks | Never — use the shared nav map + existing select paths |
| Per-item font-size overrides for footer parity | Quick visual match | Typography drift on next theme change | Never — use the shared text-style token |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Play Review API (`review`/`review-ktx` 2.x) | CTA button → `launchReviewFlow()`; awaiting result to continue flow | Opportunistic trigger with cooldown state; failure/no-show is normal; visible Rate affordance deep-links to Play Store listing |
| Play Review testing | Relying on FakeReviewManager as full verification | Fake = unit only; real dialog requires internal test track (tester account, Play-library install, primary account); quota unenforced on internal track ≠ production behavior |
| SpeechRecognizer service | Assuming GMS recognizer present; ignoring `isRecognitionAvailable` | Gate mic on `isRecognitionAvailable`; on-device path via `isOnDeviceRecognitionAvailable` (API 31+) for offline; explicit language-unavailable messaging |
| RECORD_AUDIO permission | Fire-and-forget request; looping after permanent denial | Rationale → request → settings-route on permanent denial; never start recognizer ungranted |
| DuckDuckGo search (post-Tavily) | Leaving Tavily fallback legs wired; dual-provider capability matrix | Single DDG path; capability matrix/static copy updated; DDG rate-limit/offline behavior covered by existing offline-retry design |
| Keystore/EncryptedSharedPreferences | Deleting UI but keeping stored Tavily key | One-shot upgrade migration deleting the Tavily entry; keep endpoint-key overwrite + endpoint-delete-key paths |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Review Task on chat critical path | Message-send latency spikes; ANRs on slow Play Services | Decoupled prompt controller, idle/foreground guard, never await in send pipeline | Any device with slow/stale Play Services |
| Recognizer held across navigation | `ERROR_RECOGNIZER_BUSY`, audio-resource drain, LeakCanary failures | Create-on-start / destroy-on-dispose; mic disabled while listening | Second dictation attempt; drawer/catalog tour with mic active |
| Partial-result recomposition storm | Jank in chat input during dictation | Buffer partials with throttled state updates; commit on final/stop | Long dictations on low-end devices |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Orphaned Tavily key with no UI | Unrotatable secret; data-safety disclosure drift | Delete entry via upgrade migration; remove from data-safety/Support copy if declared |
| Endpoint without key after key-deletion removal (if overwrite path missing) | App sends unauthenticated requests; key material in logs on 401 paths | Keep key-overwrite on edit; endpoint with missing key shows disconnected, never sends; no key logging |
| Voice audio leaves the device unexpectedly | Privacy violation for offline-positioned app | Prefer on-device recognizer; disclose network fallback; never persist raw audio, only transcribed text |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Star button that usually does nothing | Feels broken; erodes trust | No API-bound CTA; opportunistic prompt + Store deep-link for manual rating |
| Pre-prompt interrogation ("Do you like the app?") | Policy-violating; user manipulation | Direct, ungated prompt at a good moment per Play guidance |
| Dictation replaces typed draft | Data loss; fury | Insert at cursor; never auto-send; partials preview, final commits |
| Mic with no listening state | User doesn't know if it's working | Visible listening indicator + cancel/stop; auto-stop on silence timeout |
| Delete-all moved without discoverability | Users can't find bulk cleanup | Drawer-bottom placement above Models + confirmation; Help documents it |
| Empty states with no action | Dead-end screens | Every empty state gets its CTA (drawer → Catalog; Models → download; Endpoints → add) |

## "Looks Done But Isn't" Checklist

- [ ] **Review:** Fake tests green — verify internal-track real-dialog test + no-show negative test done
- [ ] **Review:** Prompt fires — verify cooldown state persists across restarts (DataStore) and Stop/cancel guards still pass
- [ ] **Voice:** Mic works on Pixel — verify GMS-less emulator (button hidden/disabled) + permission-denied + permanently-denied paths
- [ ] **Voice:** Dictation appends text — verify no draft overwrite, no auto-send, recognizer destroyed on navigate-away (LeakCanary leg)
- [ ] **Tavily removal:** DDG search works — verify `grep -ri tavily` zero in code, Keystore entry deleted on upgrade, suite green post-deletion
- [ ] **Settings cleanup:** Screens simpler — verify key rotation still possible (edit-overwrite), no endpoint points at a missing key
- [ ] **Drawers/catalog:** CTAs navigate — verify Back-stack returns to chat, "Use in Chat" uses the shared select path, Web Options reachable in Settings

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| API-bound Rate button shipped | LOW | Rewire button to Store deep-link; move in-app prompt to opportunistic trigger (no data migration) |
| Fake-only review testing | MEDIUM | Upload internal-track build; run real-dialog + no-show tests; patch Task-error handling if exposed |
| Orphaned Tavily key (repeat of HF-token) | LOW | Ship one-shot delete migration; confirm with prefs/Keystore dump test |
| Tavily code remnants still compiled | LOW | Follow deletion checklist; grep-gate CI if recurrence feared |
| Users stranded without rotation/cleanup | MEDIUM | Restore overwrite/delete paths in a patch; add migration only if data already orphaned |
| Broken CTA back-stacks | LOW–MEDIUM | Centralize routes; add Back-stack assertions; patch destinations |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| 1. API-bound Rate CTA | Review-flow phase | No UI element calls `launchReviewFlow` directly; manual Rate (if any) opens Store listing |
| 2. Fake-only testing | Review-flow phase | Internal-track real-dialog evidence + no-show negative test recorded |
| 3. Review blocks chat flow | Review-flow phase (+ regression) | Prompt fires off inference scope; single-flight cancel + grounding-cancel suites green with prompt enabled |
| 4. Missing recognizer assumption | Voice phase (+ regression) | `isRecognitionAvailable` gate; GMS-less emulator check |
| 5. Permission dead-ends | Voice phase | Granted/denied/permanently-denied matrix demonstrated; settings deep-link works |
| 6. Recognizer lifecycle leaks | Voice phase (+ regression) | Controller with destroy-on-dispose; LeakCanary voice leg clean; no busy-error on repeat dictation |
| 7. Tavily leftovers | Removal phase (+ regression) | Zero-grep in code; Keystore migration test; suite green; no Tavily network call in smoke |
| 8. Stranded rotation/cleanup | Removal + drawer/settings UX phases | Key rotation in ≤2 taps; relocated delete-all works; no missing-key endpoints selectable |
| 9. CTA nav regressions | Drawer/catalog UX phase (+ regression) | Per-CTA nav map executed incl. Back behavior; "Use in Chat" shares picker select path |
| 10. Copy drift | Removal phase | Copy audit: Help/Settings/README agree on DDG-only + new locations |

## Sources

- Play In-App Review guidance — quotas, no-CTA rule, FakeReviewManager scope: https://developer.android.com/guide/playcore/in-app-review (HIGH)
- Play In-App Review testing — internal track prerequisites, troubleshooting table: https://developer.android.com/guide/playcore/in-app-review/test (HIGH)
- `SpeechRecognizer` API contract — main-thread, `destroy()` MUST, RECORD_AUDIO, `isRecognitionAvailable` / `isOnDeviceRecognitionAvailable`, error codes: https://developer.android.com/reference/android/speech/SpeechRecognizer (HIGH)
- Runtime permission principles — ask in context, always offer cancel: https://developer.android.com/training/permissions/requesting (HIGH)
- Warped codebase evidence — `TavilySearchRepository.kt`, `DuckDuckGoSearchRepository` fallback legs, `@Named("tavily")` client, `TavilySearchRepositoryTest`, v2.2 HF-token orphan precedent (PROJECT.md deferred items), v2.5 LeakCanary tour pattern (MEDIUM — codebase facts, HIGH for file existence)
- Community permission-in-Compose patterns — rationale + settings-route for permanent denial (MEDIUM — pattern consensus, adapt to Warped's permission infra)

---
*Pitfalls research for: v3.0 Chat UX + Voice Dictation*
*Researched: 2026-10-02*
