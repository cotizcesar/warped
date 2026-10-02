# Architecture Research: v3.0 Chat UX + Voice Dictation

**Domain:** Android (Kotlin + Compose + Hilt, Clean architecture) — incremental milestone on existing app Warped
**Researched:** 2026-10-02
**Confidence:** HIGH (codebase-verified: every integration point below cites an existing file/symbol; Play/SpeechRecognizer API details MEDIUM — framework-stable APIs, no new third-party SDK)

## Standard Architecture (as built — verified in tree)

v3.0 adds to an existing, healthy Clean-architecture app. Nothing in the layering changes. All v3.0 work is
**new leaves or deletions on the existing tree**, not restructuring:

```
┌─────────────────────────────────────────────────────────────────┐
│  UI LAYER (Compose + ViewModel, Hilt @AndroidEntryPoint)        │
│  ┌──────────────┐ ┌──────────────┐ ┌───────────┐ ┌────────────┐  │
│  │ ChatScreen + │ │ NavGraph     │ │ Settings  │ │ Models /   │  │
│  │ ChatInputBar │ │ Drawer +     │ │ Screen +  │ │ Endpoints /│  │
│  │ ModelSelector│ │ NavHost (14  │ │ ViewModel │ │ Catalog    │  │
│  │              │ │ destinations)│ │           │ │ screens    │  │
│  └──────┬───────┘ └──────┬───────┘ └─────┬─────┘ └─────┬──────┘  │
│         │                │               │             │          │
├─────────┴────────────────┴───────────────┴─────────────┴──────────┤
│  DOMAIN LAYER (pure Kotlin interfaces + models)                   │
│  LlmModelHelper (keystone) · LlmProvider · repositories (interfaces)│
│  GroundingPrecedence (pure tri-state) · SmartPresetCalculator      │
├──────────────────────────────────────────────────────────────────┤
│  DATA LAYER (Hilt @Singleton, Dispatchers.IO)                     │
│  grounding/ : MultiUrlFetcher · DuckDuckGoSearchRepository        │
│    · TavilySearchRepository ← DELETE · WebPageFetcher ·           │
│    SearchOgEnricher · GroundedImages · GroundingBudget             │
│  remote/api: TavilyApi ← DELETE · remote/provider/* (consumers)    │
│  local/security: ApiKeyStore (tavily alias ← DELETE)               │
│  Room v16 · DataStore prefs · OkHttp/Retrofit (@Named clients)    │
└─────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | v3.0 change |
|-----------|----------------|-------------|
| `ChatInputBar` (`ui/chat/components/ChatInputBar.kt`, 195 lines) | Stateless pill input: text, image attach, think toggle, send/stop | **MODIFY** — add mic button + listening state params; stays stateless, all logic hoisted |
| `ChatScreen` (`ui/chat/ChatScreen.kt`, 865 lines, calls `ChatInputBar` ~line 298) | Hosts input, owns `RECORD_AUDIO`-adjacent launchers after change | **MODIFY** — permission launcher + recognizer lifecycle owner |
| `ChatViewModel` | Grounding orchestration, DDG-primary/Tavily-fallback branches (`MissingKey`/`InvalidKey`/`UsageLimit`) | **MODIFY** — collapse to DDG-only outcomes |
| `DuckDuckGoSearchRepository` | Today: DDG-primary/Tavily-fallback executor + Tavily-direct image leg | **MODIFY** — becomes the sole `web_search` producer; fallback + image-direct legs deleted |
| `TavilySearchOutcome` sealed interface (defined in `TavilySearchRepository.kt`) | Shared producer→consumer contract used by VM, `LocalToolLoop`, `CompatToolLoop`, providers | **MOVE + RENAME** to `SearchOutcome` (or keep name) in DDG file — this is the highest-risk edit; every consumer imports it |
| `NavGraph.kt` drawer content | `ModalNavigationDrawer` + custom full-width `Surface` sheet; footer row Models/Help/Settings at 12sp | **MODIFY** — footer parity, delete-all-chats row, (verify Web-Options anchor — no web row exists in this drawer today) |
| `ModelSelector.kt` line ~164 `if (localModels.isEmpty() && endpoints.isEmpty())` | Model-pick bottom sheet empty state | **MODIFY** — "Download a model" CTA → Catalog |
| `SettingsScreen` + `SettingsViewModel` + `SettingsUiState` | `TavilyKeyCard` (~line 403/361), Data section (~line 137), key-delete actions | **MODIFY (deletion)** — remove card, Data section, key-delete; Web section (grounding toggle) stays |
| `HelpScreen.kt` (236 lines, string-resource-driven sections) | Long tutorial help | **MODIFY (rewrite)** — content-only, structure (`HelpSection` composable) stays |
| `ModelsScreen` line ~247 empty-state; `EndpointsScreen` line ~50 empty-state | Empty hints | **MODIFY** — add CTA buttons with existing nav callbacks |
| `HuggingFaceScreen` (catalog) | Static-catalog downloads | **MODIFY** — "Use in Chat" on downloaded rows (ModelsScreen already has `onUseInChat: (Long) -> Unit` pattern to copy) |
| `NetworkModule` `@Named("tavily")` client + retrofit + api | Dedicated zero-interceptor Tavily stack | **DELETE** three providers + `TavilyApi.kt`, `TavilyDtos.kt` |
| `ApiKeyStore` `TAVILY_ALIAS` + `store/get/deleteTavilyKey` + `deleteAllKeys` call | Keystore Tavily surface | **MODIFY** — delete Tavily methods (keep stored-key orphan note: same harmless-orphan precedent as `huggingface_token`) |
| NEW: `ReviewManager` wrapper (data or util, `@Singleton`, Hilt-injected) | Play In-App Review: `requestReviewFlow` + `launchReviewFlow`, quota guard via DataStore | **NEW** — thin wrapper, no business logic in UI |
| NEW: voice dictation state holder | `SpeechRecognizer` lifecycle + `RecognitionListener` → text-field append | **NEW** — prefer small `VoiceInputManager` (or `rememberVoiceInputState`) over putting recognizer code in ChatScreen; see Pattern 2 |

## Recommended Project Structure (deltas only)

```
app/src/main/java/com/warped/
├── data/
│   ├── grounding/
│   │   ├── TavilySearchRepository.kt      ← DELETE (move outcome iface out first)
│   │   └── DuckDuckGoSearchRepository.kt  ✎ strip fallback + image-direct legs
│   ├── remote/api/
│   │   ├── TavilyApi.kt                   ← DELETE
│   │   └── dto/TavilyDtos.kt              ← DELETE
│   ├── local/security/ApiKeyStore.kt      ✎ delete Tavily fns (keep endpoint-key fns)
│   └── review/
│       └── ReviewManager.kt               ＋ NEW wrapper (or util/review/)
├── di/
│   └── NetworkModule.kt                   ✎ delete 3× @Named("tavily") providers
└── ui/
    ├── chat/
    │   ├── ChatScreen.kt                  ✎ launcher + recognizer host + drawer CTA wiring
    │   ├── ChatViewModel.kt               ✎ DDG-only branches; review-trigger hook
    │   └── components/
    │       ├── ChatInputBar.kt            ✎ mic button + isListening param (stateless)
    │       └── ModelSelector.kt           ✎ empty-state CTA (line ~164)
    ├── voice/
    │   └── VoiceInputManager.kt (or VoiceInputState.kt) ＋ NEW (see Pattern 2)
    ├── navigation/NavGraph.kt             ✎ drawer footer + delete-all row
    ├── settings/ (Screen+VM+UiState)      ✎ deletions only
    ├── help/HelpScreen.kt                 ✎ content rewrite (keep HelpSection)
    ├── models/ModelsScreen.kt             ✎ empty-state CTA
    ├── endpoints/EndpointsScreen.kt       ✎ empty-state CTA
    └── huggingface/HuggingFaceScreen.kt   ✎ "Use in Chat" on downloaded rows
AndroidManifest.xml                        ✎ re-add RECORD_AUDIO (was removed 2026-10-01, noted dead-code)
gradle/libs.versions.toml                  ✎ add play-review (+ activity-compose if missing — verify)
```

### Structure Rationale

- **Voice holder lives outside ChatScreen.** `ChatScreen` is already 865 lines; embedding
  `SpeechRecognizer` + `RecognitionListener` + permission handling inline repeats the exact bloat
  the v2.x milestones kept paying down. A small lifecycle-aware holder (`VoiceInputManager` injected
  into the VM, or a `rememberVoiceInputState()` in `ui/voice/`) keeps `ChatInputBar` stateless and
  the recognizer testable without Compose.
- **Review wrapper lives in data/util, not UI.** Play's API needs an `Activity` at launch time and
  has an opaque quota — both are reasons to centralize, not sprinkle `ReviewManagerFactory` calls
  across screens. One entry point (`maybePromptForReview(activity)`), called from exactly one place
  (post-successful-chat-turn hook in `ChatViewModel` or ChatScreen), guarded by DataStore counters.
- **Tavily deletion is a vertical slice, not scattered edits.** Delete order matters (see Build Order):
  outcome-interface move → consumer re-point → producer simplification → DI/keystore/UI removal → test updates.

## Architectural Patterns

### Pattern 1: Play In-App Review via thin Hilt wrapper + quota guard

**What:** `com.google.android.play:review` (or `review-ktx`) — `ReviewManagerFactory.create(context)`,
`requestReviewFlow()` → `launchReviewFlow(activity, reviewInfo)`. Play enforces its own display quota;
calls are fire-and-forget (no callback on whether UI showed).
**When to use:** exactly this — one wrapper, one call site.
**Trade-offs:** Pro: official API, no permission, works offline-queued. Con: quota is opaque (never
assume the dialog showed; never gate features on it; never call on every launch).

**Example:**
```kotlin
@Singleton
class PlayReviewManager @Inject constructor(
  @ApplicationContext private val context: Context,
  private val prefs: ReviewPrefs, // DataStore: success-turn count + last-prompt epoch
) {
  suspend fun maybePrompt(activity: Activity) {
    if (!prefs.isEligible()) return          // e.g. ≥N successful turns AND cooldown elapsed
    try {
      val manager = ReviewManagerFactory.create(context)
      val info = manager.requestReviewFlow().await()   // Tasks API → coroutine
      manager.launchReviewFlow(activity, info)
      prefs.markPrompted()                   // record attempt regardless of display
    } catch (_: Exception) { /* never crash chat for a rating prompt */ }
  }
}
```

**Entry point (opinionated):** post-successful-chat-turn in `ChatViewModel` (it already owns turn
completion/stream-stop states), not Settings and not app-launch. Settings entry is a fallback link at most.

### Pattern 2: SpeechRecognizer lifecycle owned outside the text field

**What:** Framework `android.speech.SpeechRecognizer` (no new dependency) + `RECORD_AUDIO` runtime
permission via `rememberLauncherForActivityResult(RequestPermission())`. Listener partial results
(`onPartialResults`) stream into the existing `onTextChange` path — dictation appends/inserts text,
it never sends.
**When to use:** this milestone's "speech-to-text only, no audio messages" scope.
**Trade-offs:** Pro: zero-dependency, on-device on GMS devices, partial results feel live. Con:
`SpeechRecognizer` availability varies (`isRecognitionAvailable()` check required); error codes
(`ERROR_NO_MATCH`, `ERROR_SPEECH_TIMEOUT`) must reset UI state or the mic button sticks in "listening".

**Example:**
```kotlin
@Composable
fun rememberVoiceInputState(onResult: (String) -> Unit): VoiceInputState {
  val context = LocalContext.current
  val recognizer = remember {
    if (SpeechRecognizer.isRecognitionAvailable(context))
      SpeechRecognizer.createSpeechRecognizer(context) else null
  }
  val permission = rememberLauncherForActivityResult(RequestPermission()) { granted ->
    if (granted) recognizer?.startListening(recognizerIntent()) // ACTION_RECOGNIZE_SPEECH, partial results
  }
  DisposableEffect(Unit) { onDispose { recognizer?.destroy() } } // hard requirement
  ...
}
```

Rules: (1) `destroy()` in `DisposableEffect.onDispose` — recognizer holds a service connection;
(2) stop listening on send/stop-turn and on navigate-away; (3) mic button hidden (not dimmed) when
`!isRecognitionAvailable()` — matches the codebase's "no dead affordances" convention in `ChatInputBar`;
(4) re-add `RECORD_AUDIO` to the manifest (it was deliberately removed 2026-10-01 as dead — this
milestone is the "real feature + runtime request" that justifies its return).

### Pattern 3: Deletion-slice for Tavily removal (contract-first)

**What:** The shared `TavilySearchOutcome` sealed interface is the load-bearing contract — `ChatViewModel`
(~10 references), `LocalToolLoop`, `CompatToolLoop`, all five remote providers route through it.
Delete in contract-first order: (1) move/rename the outcome interface into `DuckDuckGoSearchRepository.kt`
(e.g. `SearchOutcome`), (2) re-point all consumer imports, (3) simplify DDG repo to DDG-only,
(4) delete `TavilyApi`/`TavilyDtos`/`TavilySearchRepository`/DI providers/keystore fns/Settings UI,
(5) update/delete Tavily tests.
**When to use:** any removal where a deleted file owns a type others import.
**Trade-offs:** Slightly more steps than delete-and-fix-compile, but each step compiles, so bisectability
and reviewability survive. The v2.2 milestone already proved this team can land net-deletion slices cleanly.

### Pattern 4: Drawer/catalog CTA navigation reuses existing callbacks

**What:** Every CTA in this milestone maps to an already-existing nav callback — no new destinations,
no NavGraph route changes. `ModelsScreen.onUseInChat: (Long) -> Unit`, `onOpenHuggingFace`,
`UnifiedSelectorScreen.onNavigateToChat`, drawer `navController.navigate(Screen.Selector/Help/Settings)`
are all in place.
**When to use:** all six navigation touchpoints in v3.0.
**Trade-offs:** Pro: zero navigation risk; footer parity and delete-all relocation are pure UI moves
inside `NavGraph.kt` drawer content. Con: temptation to add a dedicated "review" or "voice" screen —
do not; neither needs one.

## Data Flow

### Request Flow — voice dictation

```
[mic tap] → permission launcher (granted?) → recognizer.startListening
    ↓ onPartialResults / onResults
[VoiceInputState] → onTextChange(existing VM path) → input.inputText
    ↓ send validated as usual (text non-blank) — dictation never auto-sends
[ChatViewModel.sendMessage] → unchanged grounding + inference pipeline
[onDispose / onSend / onStop] → recognizer.stopListening/destroy
```

### Request Flow — grounding after Tavily removal (DDG-only)

```
[send with web intent] → DuckDuckGoSearchRepository.search()   (sole producer)
    ↓ HTML fetch → parse-only extract → sanitize → fuse [WEB CONTEXT 1..N]
[TavilySearchOutcome.* → SearchOutcome.*] → ChatViewModel branches collapse:
  Grounded / ModelOnly stay · MissingKey·InvalidKey·UsageLimit DELETE
  (no key exists anymore → no key-error UI; DDG-fail = FETCH_FAILED path)
[image-intent turns] → Tavily-direct leg DELETED → DDG text grounding only,
  image grid empty (GroundedImages stays — render-side gate, harmless with empty input)
```

### State Management — review eligibility

```
[successful turn completes] → ChatViewModel → PlayReviewManager.maybePrompt(activity)
    ↓ DataStore: turn-count++ ; eligible? (count ≥ N AND cooldown elapsed)
[requestReviewFlow → launchReviewFlow] → markPrompted (attempt recorded either way)
```

### Key Data Flows

1. **Review flow:** single call site, DataStore-guarded, exception-swallowing — a rating prompt must
   never crash or block chat.
2. **Voice flow:** recognizer output re-enters through the exact same `updateInput` path as typing,
   so validation, send-enabling, and grounding triggers behave identically.
3. **Grounding flow (post-removal):** producer count goes 2 → 1; downstream (fusion, persist, Fuentes,
   preview, citations) untouched — same guarantee the DDG file's own header documents for its shape parity.

## Scaling Considerations

Not applicable (on-device app, no backend). The analogous "what breaks" list for this milestone:

1. **First bottleneck: outcome-interface rename blast radius.** ~15 files import Tavily symbols
   (VM, 2 tool loops, 5+ providers, Settings ×3, Chat UI ×2, grounding internals). Mitigation: rename-first,
   compile-after-each-step; keep the sealed-interface *shape* identical so branch bodies barely change.
2. **Second bottleneck: ChatScreen/ChatInputBar review churn.** Both are high-traffic files; keep
   `ChatInputBar` stateless (new params only) and voice/review logic in the new holder + wrapper so
   diffs stay additive.

## Anti-Patterns

### Anti-Pattern 1: SpeechRecognizer inline in the Composable

**What people do:** `createSpeechRecognizer` + anonymous `RecognitionListener` inside `ChatInputBar` or `ChatScreen`.
**Why it's wrong:** service-connection leak on recomposition/navigation; untestable; balloons the two
most-edited files. (The v2.5 leak-hunt milestone exists precisely because lifecycle discipline matters here.)
**Do this instead:** lifecycle-aware holder with `DisposableEffect.destroy()` + `isRecognitionAvailable()` gate.

### Anti-Pattern 2: Prompting review from multiple places or on launch

**What people do:** review calls in `onCreate`, Settings, and post-chat simultaneously to "maximize ratings."
**Why it's wrong:** Play's quota silently suppresses all of them, and launch-time prompts train users to dismiss.
**Do this instead:** one post-success call site + DataStore cooldown. One screen (Settings → rate link) optional.

### Anti-Pattern 3: Deleting Tavily files before moving the shared outcome type

**What people do:** delete `TavilySearchRepository.kt` first, then chase 15 broken files.
**Why it's wrong:** repo doesn't compile at any intermediate commit; review becomes a wall of red.
**Do this instead:** Pattern 3 — move/rename contract type first, re-point, then delete leaves.

### Anti-Pattern 4: Leaving key-delete / Data-section ViewModel functions wired to nothing

**What people do:** remove the Settings rows but leave `deleteAllApiKeys()`, `showDeleteChatsDialog()`,
Tavily VM functions in place "in case."
**Why it's wrong:** dead public VM surface + orphaned strings; the delete-all-chats function must *move*
(its logic is reused by the drawer row), the Tavily ones must die with their UI.
**Do this instead:** relocate chat-deletion logic to the drawer call path (VM function reused or moved to
`ChatRepository`-backed action); delete Tavily VM state/functions/tests outright.

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Play In-App Review (`com.google.android.play:review`) | Thin Hilt wrapper; `requestReviewFlow` + `launchReviewFlow(activity, info)` | NEW dependency (verify latest version at plan time — catalog has no play dep today). Quota opaque; never gate features. MEDIUM confidence on artifact coordinates |
| Android `SpeechRecognizer` (framework) | Holder + `RequestPermission` launcher + `ACTION_RECOGNIZE_SPEECH` intent with partial results | No dependency. Requires `RECORD_AUDIO` manifest re-add + `isRecognitionAvailable()` gate. HIGH confidence (stable framework API) |
| DuckDuckGo HTML endpoint (existing) | Unchanged — becomes sole producer | Brittleness note already documented in DDG repo header (markup-shape dependency); unchanged by this milestone |

### Internal Boundaries (new vs modified — explicit)

| Boundary | New / Modified | Notes |
|----------|---------------|-------|
| `ChatInputBar` ← voice holder | MODIFIED (additive params: `isListening`, `onMicClick`, `voiceAvailable`) | stays stateless; no recognizer imports in this file |
| `ChatScreen` ↔ voice holder + permission launcher | MODIFIED | launcher + `DisposableEffect` host; mic affordance placement next to send/stop row |
| `ChatViewModel` → `PlayReviewManager` | MODIFIED (one call site) | needs Activity handle at launch — pass from Composable, don't hold Activity in VM |
| `ChatViewModel` ↔ `DuckDuckGoSearchRepository` | MODIFIED (branch collapse) | delete `MissingKey`/`InvalidKey`/`UsageLimit` arms; simplify key-presence probe (~line 770) |
| `DuckDuckGoSearchRepository` ← former Tavily callers | MODIFIED | fallback + `include_images` Tavily-direct legs deleted; DDG-only + FETCH_FAILED |
| Outcome interface → all consumers | MOVE+RENAME then re-point | the critical-path edit; keep case shape identical |
| `NetworkModule` → rest of graph | MODIFIED (delete 3 `@Named("tavily")` providers) | verify no other `@Inject @Named("tavily")` sites beyond `TavilySearchRepository` |
| `ApiKeyStore` ↔ `SettingsViewModel` | MODIFIED (delete Tavily fns + `deleteAllKeys` Tavily line) | endpoint-key fns + `deleteKey` stay (key-deletion *ability* removal is a UI-scope question — milestone says remove key-delete affordance; keep store-level `deleteKey` for endpoint deletion flows — verify at plan time) |
| Drawer ↔ `ChatRepository.deleteConversation` (+ delete-all) | MODIFIED | drawer row above Models footer; reuse existing delete path + confirm dialog pattern (`WarpedAlertDialog`) |
| Catalog rows → chat | MODIFIED | copy `ModelsScreen.onUseInChat` wiring into `HuggingFaceScreen` downloaded rows |
| `HelpScreen` ↔ `strings.xml` | MODIFIED (content only) | short/minimal rewrite; ES + EN values (`values-es`) both updated |

### Suggested Build Order (removals before additions where they overlap)

1. **Tavily contract move** — move/rename outcome interface, re-point consumers, green build. (Unblocks everything grounding-adjacent; zero behavior change.)
2. **Tavily deletion slice** — DDG simplification → delete API/DTO/repo/DI/keystore/Settings card/tests. (Must precede Help rewrite + Settings cleanup which reference the same screens; behavior change lands here.)
3. **Settings cleanup + drawer/footer/delete-all + empty-state CTAs + Help rewrite** — all pure-UI, parallelizable once (2) is done; no interdependencies. Suggested split: settings/drawer one plan, CTAs/catalog/help another.
4. **Voice dictation** — manifest + holder + `ChatInputBar` mic + launcher wiring. Independent of (1–3); can run parallel, but schedule after UI churn settles to avoid `ChatScreen` merge conflicts.
5. **Play Review** — dependency + wrapper + DataStore prefs + single call site. Fully independent; smallest slice, good last-plan candidate.

## Sources

- Codebase (HIGH): `NavGraph.kt` (drawer + 14 destinations), `Screen.kt`, `ChatInputBar.kt` (195 lines),
  `ChatScreen.kt` (~line 298 input wiring), `ModelSelector.kt` (line ~164 empty-state),
  `SettingsScreen.kt` (Data §137, Web §~190, `TavilyKeyCard` ~403), `SettingsViewModel.kt` (Tavily fns),
  `ApiKeyStore.kt` (`TAVILY_ALIAS`), `NetworkModule.kt` (`@Named("tavily")` ×3),
  `TavilySearchRepository.kt` (outcome iface + Bearer discipline), `DuckDuckGoSearchRepository.kt`
  (DDG-primary/fallback policy header), `WebSearchToolSet.kt`, `GroundedImages.kt`,
  `AndroidManifest.xml` (RECORD_AUDIO removal note), `gradle/libs.versions.toml` (no play/activity deps),
  `.planning/PROJECT.md` (v3.0 scope, v2.2/v2.4/v2.5 precedents).
- Framework knowledge (MEDIUM, verify at plan time): Play In-App Review artifact coordinates + latest
  version; `SpeechRecognizer` partial-results + error-code behavior (stable for years, low drift risk);
  `androidx.activity:activity-compose` launcher APIs (verify catalog needs the explicit dep).

---
*Architecture research for: v3.0 Chat UX + Voice Dictation*
*Researched: 2026-10-02*
