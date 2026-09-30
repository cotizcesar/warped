# Pitfalls Research

**Domain:** Play compliance retrofit (16 KB pages + target API 36) + memory-leak audit on an existing LiteRT-LM / Compose chat app
**Researched:** 2026-09-30
**Confidence:** HIGH (16 KB + API 36 behavior changes from official Android docs); MEDIUM (leak patterns from community sources + codebase-shaped inference)

## Critical Pitfalls

### Pitfall 1: LiteRT-LM native `.so` ships unaligned and you can't fix it yourself

**What goes wrong:**
App installs fine on 4 KB devices but Play Console flags "not 16 KB compatible", and on 16 KB-kernel devices the app either refuses to install or crashes at `System.loadLibrary` / first inference with a linker `SIGSEGV` (RELRO segment misalignment is the classic signature).

**Why it happens:**
Unlike the old llama.cpp era (CMake-built from source, where you add `-Wl,-z,max-page-size=16384` yourself), LiteRT-LM arrives as a **prebuilt Maven AAR**. Its `.so` ELF alignment is decided by Google's build, not yours. Developers bump `compileSdk` and assume they're done, never running `check_elf_alignment.sh` or APK Analyzer on the LiteRT AAR. Transitive native deps (e.g. an old `libc++_shared.so` from NDK ≤ r26, SQLCipher's native lib) can each independently break alignment.

**How to avoid:**
1. Upgrade AGP to 8.5.1+ (uncompressed `.so` with 16 KB zip alignment) and NDK to r28+ (16 KB ELF by default) — then you only have to worry about prebuilts.
2. Run APK Analyzer → Alignment column, plus `zipalign -c -P 16 -v 4 app.apk` and the `check_elf_alignment.sh` script on every release candidate.
3. If LiteRT-LM's `.so` is UNALIGNED, the only fix is upgrading to a LiteRT-LM version built with 16 KB support — track the release notes, don't try to repack the AAR.
4. Test on a real 16 KB environment: emulator "Google APIs Experimental 16KB Page Size" image or Pixel 8+ "Boot with 16KB page size" developer option, `adb shell getconf PAGE_SIZE` → must return `16384`.

**Warning signs:**
- Play Console 16 KB warning on the release track.
- `backcompat mode` warning dialog on first launch on a 16 KB device (package manager silently compat-modes your app — works today, Play blocks updates from Feb 2027).
- Crash in native load with RELRO/`mmap` in the tombstone and no Kotlin frames.

**Phase to address:**
16 KB phase, first. It gates the entire Play release; everything else is moot if the AAB is rejected.

---

### Pitfall 2: Bumping `targetSdk` to 36 without auditing the three behavior cliffs (edge-to-edge, predictive back, large-screen resizability)

**What goes wrong:**
Chat UI that looked fine on API 35 suddenly draws under the status/nav bars with unreadable input, back-gesture from a bottom sheet or chat navigates wrong (or `onBackPressed` silently stops firing), and on tablets/foldables the locked-portrait chat stretches or loses state on rotation because orientation/resizability restrictions are ignored on `sw600dp+`.

**Why it happens:**
`targetSdk 36` is not a version number — it opts into new platform contracts. The three that bite this app: (a) `windowOptOutEdgeToEdgeEnforcement` is deprecated AND disabled — no escape hatch; (b) predictive back animations are on by default and `onBackPressed`/`KEYCODE_BACK` stop being dispatched unless migrated or opted out via `android:enableOnBackInvokedCallback="false"`; (c) `screenOrientation`, `resizableActivity`, min/max aspect ratio are ignored on large screens (compat opt-out via `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` is temporary — gone at API 37).

**How to avoid:**
1. Do a dedicated API-36 audit pass before flipping `targetSdk`: `WindowInsets` handling in chat (pill input + `LazyColumn` padding), back handling (`OnBackPressedDispatcher` / predictive-back APIs) in chat, sheets (Sources preview), and pickers.
2. Decide explicitly per cliff: migrate (insets + predictive back) vs. temporary opt-out with a dated TODO. Never opt out silently without recording it as tech debt.
3. Test on a large-screen emulator (tablet/foldable, `sw600dp+`) with rotation — activity re-creation wipes chat draft/scroll state if not saved via `rememberSaveable`/`SavedStateHandle`.
4. Use the compat-framework flags (`UNIVERSAL_RESIZABLE_BY_DEFAULT`) to preview breakage before the flip.

**Warning signs:**
- `windowOptOutEdgeToEdgeEnforcement` still referenced in themes — dead on 36, and masking missing insets support on 35.
- Custom back interception (chat "Stop"/sheet dismiss) implemented via `onBackPressed` instead of the dispatcher.
- `screenOrientation="portrait"` in the manifest (ignored on large screens at 36 — layout must be adaptive).

**Phase to address:**
Target-API-36 phase, before any leak-fix refactoring (behavior audit first, code churn second).

---

### Pitfall 3: Local-network permission blindsides LAN endpoints (Ollama / LM Studio / Custom)

**What goes wrong:**
Remote chat to `http://192.168.x.x:11434` (Ollama) or LM Studio on LAN works on the developer's API-35 phone but fails on Android 16 with `sendto failed: EPERM` socket errors. Looks exactly like a server bug or an OkHttp regression — it isn't.

**Why it happens:**
Android's Local Network Protection gates LAN traffic behind a runtime permission (Nearby devices group, `NEARBY_WIFI_DEVICES` today, dedicated permission in the enforcement release). Restrictions apply to **all** sockets including OkHttp/Cronet, native code, mDNS/`.local` resolution. The app's whole remote-provider surface is LAN-first (Ollama/LM Studio are almost always RFC-1918 addresses), so this is a first-order breakage, not an edge case.

**How to avoid:**
1. During the API-36 phase, opt into restriction early: `adb shell am compat enable RESTRICT_LOCAL_NETWORK <pkg>` + reboot, then exercise every remote provider over LAN.
2. Declare `NEARBY_WIFI_DEVICES`, add the permission rationale flow, and handle deny/revoke gracefully (remote fails with a clear "grant nearby-devices to reach LAN servers" message, not a generic network error).
3. Distinguish LAN vs. internet failures in error mapping — `EPERM` on RFC-1918/`*.local` → permission guidance; elsewhere → existing retry/error path.
4. Watch the 25Q4+ guidance for casting/media-picker carve-outs — re-check before release.

**Warning signs:**
- Endpoint "test connection" passes on emulator (host loopback) but fails on physical device on Wi-Fi.
- Bug reports that only mention Ollama/LM Studio (LAN) while OpenAI/Anthropic (internet) work.

**Phase to address:**
Target-API-36 phase (connectivity sub-pass). Verify with a real LAN endpoint, not just mocked HTTP tests.

---

### Pitfall 4: The inference engine outlives the chat — EngineManager / session leak

**What goes wrong:**
Each model switch or conversation leaves the previous LiteRT session/engine referenced. Memory climbs monotonically during a session-heavy day; eventually model load fails with OOM or the app is killed in background. LeakCanary points at a singleton holding an Activity/ViewModel-scoped object, or a native handle never released.

**Why it happens:**
The engine is a heavyweight singleton (correct), but its lifecycle methods (`close()`/session release) are only called on the happy path. Model switch, seamless-switch retry, GPU-constraint fallback, and process-death paths skip cleanup. Helpers captured in callbacks (inference listener → ViewModel → Compose) create reference chains back to destroyed screens. Native LiteRT handles are invisible to the JVM GC — the Java wrapper can be collected while native memory stays resident, so "no Java leak" ≠ "no leak".

**How to avoid:**
1. Single owner, single close: EngineManager owns engine lifetime; sessions are scoped to the active conversation and closed on switch/disconnect/`onCleared`.
2. Audit every early-return/exception path in load → inference → unload for a missing `close()` (try/finally, not happy-path calls).
3. Never let the singleton capture Activity/Fragment/ViewModel references — application context only, callbacks via weak refs or Flow.
4. Verify with a load → chat → switch-model → repeat loop under memory profiler; native (not just Java heap) must return to baseline.

**Warning signs:**
- "Model not loaded" or OOM after 2–3 model switches that a fresh start fixes.
- Profiler shows native memory stair-stepping up per conversation while Java heap looks flat.

**Phase to address:**
Leak-audit phase, engine-lifetime workstream — fix before touching UI collectors (engine leaks dwarf UI leaks in MB).

---

### Pitfall 5: Streaming collectors that never cancel (chat Flows + SSE response bodies)

**What goes wrong:**
Stopping a stream (Stop button, navigating away, offline retry) leaves the coroutine collecting tokens alive plus the OkHttp `ResponseBody`/`BufferedSource` open. Leaked connections exhaust the connection pool ("A connection was leaked. Did you forget to close a response body?"), subsequent chats stall, and cancelled grounding fetches keep burning data in background.

**Why it happens:**
Three compounding mistakes: (a) collecting with `collectAsState()` / bare `launch` instead of lifecycle-aware collection (`collectAsStateWithLifecycle`, `repeatOnLifecycle`, `flowWithLifecycle`); (b) SSE parsing without a `use {}`/`finally` that closes the body when the coroutine is cancelled — `streamJob?.cancel()` only helps if cancellation actually closes the source; (c) `EventSource.cancel()` never called on the Stop path, or `SharedFlow` accumulators (the shared SSE accumulator from the remote agentic loop) holding emissions with no buffer eviction.

**How to avoid:**
1. Rule: every streaming collection site gets lifecycle-aware collection; every SSE read loop gets `try { … } finally { body.close() / eventSource.cancel() }`.
2. Re-audit the cancellable single-flight `runInference` work (v2.1) — "Stop means stop" must also mean "Stop means closed": cancel job → cancel EventSource → close body → clear accumulator.
3. Cap and clear: shared accumulators cleared on conversation switch; grounding fan-out (cap 5) coroutines are children of a supervisor that the Stop/overlap guards actually cancel.
4. Add a regression test: start stream → cancel → assert body closed / EventSource cancelled / no active jobs (fake EventSource + Turbine or `runTest`).

**Warning signs:**
- StrictMode / OkHttp "connection leaked" warnings in logcat after Stop or navigation.
- Token callbacks firing into a disposed chat screen; progress UI (`Leyendo N de M…`) updating after cancel.
- Connection pool exhaustion on long sessions with many remote calls.

**Phase to address:**
Leak-audit phase, streaming workstream. Highest bug-density area (local loop + remote loop + grounding fan-out + offline retry all stream).

---

### Pitfall 6: Image-loader and download-worker leaks (Coil singleton + WorkManager stream handling)

**What goes wrong:**
OG thumbnails (Coil) and model downloads (WorkManager + OkHttp) leak slowly: chat with many grounded sources grows the image memory cache unbounded; a cancelled/retried multi-GB download leaves partial files plus open streams, and retry loops re-download from byte 0.

**Why it happens:**
(a) Coil: more than one `ImageLoader` (each with its own memory+disk cache), or a singleton built with an Activity context instead of application context — the loader pins the destroyed Activity. Per-source cards in a `LazyColumn` without size-bounded requests decode full-size OG images into a scrolling list. (b) WorkManager: download `ResponseBody` stream not closed in `finally`; progress listeners referencing the Worker after completion; `Result.retry()` without backoff/idempotence re-runs non-resumable downloads; partial files never cleaned on cancel.

**How to avoid:**
1. Coil: exactly one `SingletonImageLoader.Factory` wired to the application context; bounded request sizes for thumbnail cards; verify disk-cache sizing (Coil 3.x defaults are sane — don't "tune" without measuring).
2. Downloads: `use {}` on every body/stream, `finally` cleanup of partial files on cancel/failure, `Range`-resume where the server supports it, exponential backoff on retry, `setForeground()` notification cancelled with the work.
3. Test: cancel a large download mid-flight → no FD leak, partial file removed or resumable; scroll a 20-source grounded chat → memory returns to baseline after leaving.

**Warning signs:**
- Memory grows with thumbnail count and never drops after leaving chat.
- Duplicate/parallel download workers for the same model (missing single-flight / unique-work policy).
- Retry storms on flaky Wi-Fi re-downloading gigabytes.

**Phase to address:**
Leak-audit phase, media/download workstream. Lower severity than engine/streaming but user-visible (storage + data usage).

---

### Pitfall 7: "Fixing" leaks by breaking threading or persistence (the remediation boomerang)

**What goes wrong:**
The leak fix introduces a worse bug: moving inference off a leaked scope onto the wrong dispatcher blocks the UI or crashes Room (SQLCipher) with "cannot access database on the main thread" / corrupt-state errors; closing a session too eagerly breaks seamless model switch or drops in-flight `grounded_sources` writes (Room v15/v16 migrations); aggressive `cancel()` kills the offline-retry `Reintentar` path that must survive.

**Why it happens:**
Leak fixes touch ownership and threading at once. Developers "fix" a retained ViewModel by scoping its job to the composable (now inference dies on rotation), or close shared resources (OkHttp client, Room DB, DataStore) that other screens still need. Room + SQLCipher adds a specific trap: leaked `Cursor`/unclosed transaction vs. premature `close()` — both corrupt or crash, and the failure surfaces far from the change.

**How to avoid:**
1. Separate the two concerns: fix *ownership* (who closes, when) without changing *dispatchers*; then review dispatchers separately.
2. Never close shared singletons (OkHttp client, Room DB, DataStore, ImageLoader) from a screen/ViewModel — only conversation-/request-scoped resources.
3. Keep the v2.1–v2.3 invariants as regression gates: single-flight inference, Stop semantics, same-row retry reuse, history-untouched retry, KV-channel hygiene. Every leak fix gets a re-run of those flows.
4. Room: prefer structured transactions + `use {}` on cursors over manual open/close; never call `db.close()` from app code except tests.

**Warning signs:**
- Leak fix PR also moves `withContext` dispatchers or adds `GlobalScope` — review flag, split the change.
- Previously-green flows (Stop, retry, seamless switch) regress after a "cleanup" commit.
- Crash reports migrate from OOM to `IllegalStateException` (closed resource) — you over-closed.

**Phase to address:**
Every leak-fix plan needs a "no-boomerang" verification step; the milestone's final hardening pass re-runs Stop/retry/switch flows end to end.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Rely on 16 KB backcompat mode instead of realigning | App "works" on 16 KB devices today, zero build changes | Play blocks updates from Feb 2027; compat mode warns users and is less stable | Never as the release strategy; only as a local-testing bridge |
| `useLegacyPackaging = true` (compressed `.so`) to dodge zip-alignment | Dodges the AGP-8.5.1 upgrade | Larger installs, more install failures on low-storage devices, still need ELF alignment | Only if AGP upgrade is truly blocked; record as debt |
| `enableOnBackInvokedCallback = false` blanket opt-out | Predictive-back crashes go away in one manifest line | Miss the platform navigation model; forced migration later | Acceptable as a stopgap iff each opted-out screen has a dated migration TODO |
| `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` blanket opt-out | Tablet/foldable breakage hidden immediately | Forced breakage at API 37 with zero remaining escape hatch | Only per-activity, with adaptive-layout work scheduled |
| Fix leaks with `System.gc()` / `onTrimMemory` cache clears | Memory graph looks better in a demo | Masks the real retention chain; leaks return under real use | Never — diagnose with profiler/LeakCanary, fix the reference |
| Closing shared OkHttp client / Room DB to "stop a leak" | One leak warning disappears | Crashes every other consumer of the shared resource | Never |
| Skipping the 16 KB emulator ("CI will catch it") | Saves an hour of setup | 16 KB failures are link-time/device-specific — unit tests cannot catch them | Never for the release candidate |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| LiteRT-LM Maven AAR (native) | Assuming `targetSdk` bump covers 16 KB; never inspecting the AAR's `.so` | APK Analyzer + `check_elf_alignment.sh` + `zipalign -P 16` on every RC; upgrade LiteRT-LM if unaligned |
| SQLCipher / Room native lib | Forgetting SQLCipher ships its own `.so` — app aligns but DB layer doesn't | Include SQLCipher's `.so` in the alignment check; upgrade SQLCipher alongside AGP/NDK |
| Jsoup parse-only grounding | Treating Jsoup as pure-Java and skipping native checks | Jsoup core is pure JVM (safe), but verify no transitive native dep was added; keep the `never connect()` policy — network via OkHttp only |
| Highlights syntax engine | Assuming a text library can't affect 16 KB | Same rule as Jsoup: pure-JVM is inherently 16 KB-safe, but still run APK Analyzer over the final APK rather than reasoning per-library |
| Coil 3.x image pipeline | Second `ImageLoader` in a feature module; Activity context in factory | One app-scoped singleton via `SingletonImageLoader.Factory` with application context |
| OkHttp SSE (all 5 remote providers) | `response.body` read without `use {}`; `EventSource` never cancelled on Stop | `use {}` + `finally { cancel/close }`; Stop path cancels job → source → body → accumulator |
| Tavily / HF direct downloads | New endpoint added without LAN-permission analysis | Every new network integration gets a LAN-vs-internet classification + permission-path test |
| Baseline Profiles / R8 (v2.1 Phase 48) | Leak-fix refactor renames classes without updating R8 keep rules or regenerating profiles | Re-run release build + profile generation after the leak pass; keep rules cover renamed engine/helper classes |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| 16 KB pages inflate RSS (single live object pins 16 KB) | App that fit in memory on 4 KB devices gets LMK-killed on 16 KB devices | Release empty pages (`madvise`-friendly pooling if custom allocators exist — LiteRT manages its own; keep model-window/grounding budgets tight); re-measure on 16 KB emulator | Large-model + grounding-budget + chat-history all resident on a 16 KB device |
| Chat history `LazyColumn` retains all rendered messages | Scroll long conversations → jank then OOM; leak fix misdiagnosed as "engine leak" | Atomic sub-state / LazyColumn split (v2.1) must survive the leak pass; paginate or window history queries; keyed items with stable keys | 500+ message conversations with code blocks + thumbnails |
| Streaming re-composition per token re-renders whole chat | Frame drops during streaming; "no jank" invariant (v1.6) regresses | Scoped recomposition (streaming text node only); deferred highlighting already in place — don't re-highlight per token | Fast remote models emitting many tokens/sec |
| Unbounded grounding context (5 URLs × full text) | Prompt blows model window; inference slows or fails | Keep the model-window-aware grounding budget + adversarial/budget exit gates (v2.3); budget is a ceiling, not a target | Multi-URL grounding on small-window local models |
| LeakCanary in release builds | Release APK slower/larger; Play pre-launch flags it | LeakCanary `debugImplementation` only; release verification via profiler + strictly-scoped manual testing | Any release candidate |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| LAN permission rationale collects more than needed | Over-requesting nearby-devices erodes trust; Play policy scrutiny | Request minimum, explain LAN-server use in rationale, handle deny gracefully |
| Leak-fix logging dumps message content / API keys | PII/secret leak into logcat or leak-trace artifacts attached to bugs | Keep existing redaction (secret isolation from v2.4) in all new logging; scrub traces before sharing |
| Copying native `.so` out of APK for alignment inspection and committing it | Proprietary binary in git; stale copy later mistaken for source of truth | Inspect in `/tmp`, never commit extracted `.so` files |
| Disabling R8/ProGuard "to debug a leak" and shipping that build | De-obfuscated release with larger attack surface | Debug leaks on `debuggable` builds only; release always with shrinking+obfuscation |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Edge-to-edge without insets handling | Chat input hidden behind nav bar; messages under status bar | `WindowInsets` padding on chat scaffold, pill input above nav bar, scroll content clear of bars |
| Predictive back breaking Stop/sheet UX | Back gesture dismisses whole chat instead of bottom sheet; Stop affordance confusing | Migrate back handling per-screen; sheet consumes back first; Stop remains an explicit button |
| Permission wall for LAN without explanation | User denies nearby-devices, Ollama "never works", 1-star review | Pre-permission rationale ("reach your PC's LM Studio on Wi-Fi"), deep-link to Settings on deny |
| Leak-fix induced state loss on rotation | Draft message / scroll position lost when rotating (new scope) | Preserve via `rememberSaveable`/`SavedStateHandle`; test rotation on phone + foldable |
| 16 KB compat-mode warning dialog | User sees a scary system warning on first launch | Ship genuinely aligned — never let users see the compat dialog |

## "Looks Done But Isn't" Checklist

- [ ] **16 KB support:** AGP/NDK bumped but APK never inspected — verify APK Analyzer Alignment column + `zipalign -c -P 16` + `check_elf_alignment.sh` on the actual release AAB/APK
- [ ] **16 KB support:** App `.so` aligned but transitive `.so` (SQLCipher, LiteRT's `libc++_shared`) not checked — verify every `.so` under `lib/`
- [ ] **16 KB support:** Tested on 4 KB emulator only — verify `getconf PAGE_SIZE` = 16384 in the test environment and smoke test model load + inference there
- [ ] **API 36:** `targetSdk` bumped but `windowOptOutEdgeToEdgeEnforcement` still set — verify insets handling with the flag removed on API 35 and 36
- [ ] **API 36:** Back navigation "works" via legacy `onBackPressed` — verify predictive-back animations on API 36 and per-screen back behavior
- [ ] **API 36:** LAN endpoints tested on emulator only — verify real-device Wi-Fi test to Ollama/LM Studio with LNP restriction opted in
- [ ] **Leak fix:** Java heap flat but native memory not measured — verify native RSS returns to baseline after switch-model / long-chat loops
- [ ] **Leak fix:** Stop button stops tokens but connections not verified closed — verify no "connection leaked" warnings and pool recovers
- [ ] **Leak fix:** Fix verified on happy path only — verify cancel-during-stream, switch-during-stream, rotation-during-stream, offline-retry paths
- [ ] **Release:** R8 keep rules + Baseline Profiles regenerated after refactor — verify `assembleRelease` + startup-profile flow post-leak-pass

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Unaligned LiteRT-LM `.so` discovered late | MEDIUM (blocked on upstream) | Confirm via script → check for newer LiteRT-LM with 16 KB support → upgrade → re-verify; if none exists, ship interim with compat mode + expedite tracking issue (Play deadline Feb 2027) |
| API-36 behavior regression shipped | MEDIUM | Compat-flag / manifest opt-out hotfix per cliff (back callback, resizability property) → schedule real migration; use staged rollout halt |
| Engine/session leak in production | HIGH (OOM kills, data-loss risk on force-stop) | Hotfix close-paths on switch/disconnect → verify with profiler loop → release; meanwhile document "restart app" workaround |
| SSE connection-pool exhaustion | MEDIUM | Hotfix `finally { close/cancel }` on all streaming sites → verify pool recovery → release |
| Over-closed shared resource (boomerang) | MEDIUM | Revert to shared ownership, re-scope fix to request/conversation lifetime → re-run Stop/retry/switch regression suite |
| Rotation state loss from re-scoping | LOW | Move state to `SavedStateHandle`/`rememberSaveable`, restore scope to ViewModel → rotation test matrix |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| 1 — Unaligned native `.so` | 16 KB phase (first) | APK Analyzer + `zipalign -P 16` + `check_elf_alignment.sh` green on RC; 16 KB emulator smoke (model load + inference) |
| 2 — API-36 behavior cliffs | Target-API-36 phase (audit before flip) | Insets screenshot review, predictive-back walkthrough, large-screen rotation matrix |
| 3 — LAN permission vs Ollama/LM Studio | Target-API-36 phase (connectivity sub-pass) | Real-device Wi-Fi test with `RESTRICT_LOCAL_NETWORK` opted in; deny-path UX review |
| 4 — Engine/session leak | Leak-audit phase (engine workstream) | Profiler loop: native RSS to baseline across switch-model cycles; no singleton→Activity chains in LeakCanary |
| 5 — Streaming collectors / SSE bodies | Leak-audit phase (streaming workstream) | Cancel-path tests (Stop/nav/rotation) assert closed bodies + cancelled sources; zero "connection leaked" warnings |
| 6 — Coil / WorkManager leaks | Leak-audit phase (media/download workstream) | Single ImageLoader assertion; cancel-download → no FD/partial-file residue; thumbnail scroll memory test |
| 7 — Remediation boomerang | Every leak-fix plan + final hardening | Stop/retry/seamless-switch regression suite re-run after each fix; release build with regenerated R8/profiles |

## Sources

- Official: [Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes) (HIGH — AGP 8.5.1+/NDK r28 guidance, `check_elf_alignment.sh`, `zipalign -P 16`, backcompat mode, Feb 2027 Play deadline)
- Official: [Behavior changes: apps targeting Android 16](https://developer.android.com/about/versions/16/behavior-changes-16) (HIGH — edge-to-edge opt-out removal, predictive back default, resizability ignored on sw600dp+, LNP permission, `scheduleAtFixedRate` single-catch-up)
- Community: 16 KB Play-console fix guides and r/androiddev threads (MEDIUM — confirm deadline/versions against official docs above)
- Community: Compose `collectAsState` vs `collectAsStateWithLifecycle` / `repeatOnLifecycle` leak guides, OkHttp SSE `EventSource.cancel()` + `use {}` patterns, Coil singleton application-context guidance (MEDIUM — standard patterns, verify against project code during audit)
- Project context: `.planning/PROJECT.md` v2.5 milestone scope + v2.1–v2.4 invariants (single-flight inference, Stop semantics, retry same-row reuse, secret isolation, Coil singleton, Room v15/v16)

---
*Pitfalls research for: Warped v2.5 Play Compliance + Leaks*
*Researched: 2026-09-30*
