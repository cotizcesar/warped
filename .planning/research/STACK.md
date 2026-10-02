# Stack Research: v3.0 Chat UX + Voice Dictation

**Domain:** Incremental stack delta — Play In-App Review, Android speech-to-text dictation, DuckDuckGo-only search (Tavily removal), Compose drawer/catalog navigation
**Researched:** 2026-10-02
**Confidence:** HIGH (Play official docs + Android API reference + repo build files verified; DDG scraping details MEDIUM — unofficial endpoint)

## Starting Point (already in the repo — do NOT re-add)

| What | Current state | Implication |
|------|---------------|-------------|
| `navigation-compose` 2.9.8, Hilt + `hilt-navigation-compose` 1.3.0 | Wired navigation graph with existing Model Catalog route | Drawer CTAs ("Download a model", "Download a local model", "Use in Chat") are pure `navController.navigate(route)` calls — **zero new navigation deps** |
| OkHttp 4.12.0 + Jsoup 1.23.2 (parse-only, never `connect()`) | Grounding pipeline `data/grounding/` with frozen fetch policy | DDG search producer reuses the **same two artifacts** — GET via OkHttp, extract via `Jsoup.parse(body)` |
| `androidx.activity` (transitive via ComponentActivity + Compose) | `rememberLauncherForActivityResult` available | RECORD_AUDIO permission flow needs **no new artifact** |
| `auditDependencies` gate (bans kapt, firebase, moshi, gson, kotlin-reflect, ktor, mcp, tflite, mlkit-genai, appauth, compose-richtext, cameraX, datastore-proto; bans SNAPSHOT/alpha/beta in release graph) | Enforced on `check` | The one NEW dep below (review-ktx, stable 2.0.2) passes both halves of the gate — verified below |
| Tavily integration (Keystore key, Bearer client, search→fused producer, Settings UI + strings) | To be **deleted** in v3.0 | Net-negative milestone for the network layer: one client removed, one producer added, zero net deps |

## Recommended Stack

### Core Technologies (no changes — confirm, don't churn)

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Kotlin | 2.3.20 (keep) | Language | review-ktx 2.0.2 ships Kotlin extensions compiled against older Kotlin metadata — forward-readable by 2.3.20. No bump for this milestone. |
| AGP | 9.3.0 (keep) | Build | Play libraries resolve from Google Maven (already in the repo's repository list — verify `google()` present in `settings.gradle.kts`). No build change. |
| navigation-compose | 2.9.8 (keep) | Drawer → Catalog CTAs, "Use in Chat" deep-link | Existing graph covers every v3.0 navigation need (drawer footer, empty-state CTAs, catalog→chat with model pre-select via savedStateHandle/route arg). Adding a nav dep would be pure churn. |
| OkHttp | 4.12.0 (keep) | DDG search GET + existing fetch policy | The DDG producer is one more `OkHttpClient` call site (or reuse the grounding client) with a browser User-Agent header. No version change. |
| Jsoup | 1.23.2 (keep) | DDG result-page extraction, parse-only | `Jsoup.parse(responseBodyString)` → select `.result__a` anchors + `.result__snippet` nodes → absolute URLs. Same never-`connect()` discipline as the v2.3 pipeline. No version change. |

### Supporting Libraries (NEW — the actual v3.0 delta: exactly ONE addition)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `com.google.android.play:review-ktx` (+ `com.google.android.play:review`) | **2.0.2** (latest stable — official integration guide, updated 2026-09-18) | Play In-App Review flow: `ReviewManagerFactory.create()` → `requestReviewFlow()` → `launchReviewFlow(activity, reviewInfo)` with coroutines extensions | `implementation` for the rating entry point (Settings row and/or post-chat success moment). Hilt-wrap in a `ReviewLauncher` interface (real vs no-op/fake for tests) so ViewModels never touch Play Tasks directly. |
| **Speech: NO library** — platform `android.speech.SpeechRecognizer` + `android.speech.RecognizerIntent` | Platform API (API 8+, no artifact) | Dictation into chat input: `SpeechRecognizer.createSpeechRecognizer()` + `ACTION_RECOGNIZE_SPEECH` / `EXTRA_LANGUAGE_MODEL_FREE_FORM` + `EXTRA_PARTIAL_RESULTS`, results appended to the existing message `TextField` state | Inline mic button in the chat pill. No audio messages, no persistence — recognized text only. |
| **DDG search: NO library** — existing OkHttp + Jsoup | `https://html.duckduckgo.com/html/?q=` GET (non-JS endpoint), parse-only | Single search producer replacing the Tavily producer in the grounding pipeline | Every place Tavily fed: search→fused `[WEB CONTEXT]` producer, numbered Fuentes citations, OG-thumbnail fetch targets. Same budget/cancel/hygiene semantics as v2.4. |
| **Permission: NO library** — `ActivityResultContracts.RequestPermission` via `rememberLauncherForActivityResult` | `androidx.activity` (already transitive) | `RECORD_AUDIO` runtime request from the mic-button tap | Do NOT add Accompanist-permissions (deprecated/archived upstream, would trip the "no pre-release / no dead-dep" spirit and adds a wrapper around an API Compose already exposes natively). |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Play Console → Internal sharing / closed test track | Verify the review flow end-to-end (quota means the dialog often does NOT show on dev builds) | The API deliberately hides whether the dialog showed — code must continue normal flow in `addOnCompleteListener` regardless. Never gate app behavior on "user rated". Test = "no crash, flow completes", not "dialog visible". |
| `adb shell getconf PAGE_SIZE` + existing `check_elf_alignment.sh` | Confirm review-ktx adds no native `.so` risk | review-ktx is pure Kotlin/Java (AAR, no JNI) — expected zero impact on the v2.5 16 KB gates. Re-run `verify16KbAlignment` after adding, record output. |
| Device with Google Play Services + mic (or emulator with mic passthrough) | Dictation + `isRecognitionAvailable()` fallback testing | `SpeechRecognizer.isRecognitionAvailable(context)` gates the inline path; when false (no recognizer service — some emulators, de-Googled ROMs), fall back to `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` via `StartActivityForResult`, and when that also resolves to nothing, hide/disable the mic button. |

## Installation

```kotlin
// gradle/libs.versions.toml
[versions]
play-review = "2.0.2"

[libraries]
play-review = { group = "com.google.android.play", name = "review", version.ref = "play-review" }
play-review-ktx = { group = "com.google.android.play", name = "review-ktx", version.ref = "play-review" }
```

```kotlin
// app/build.gradle.kts
implementation(libs.play.review)
implementation(libs.play.review.ktx)
```

```xml
<!-- AndroidManifest.xml — required for SpeechRecognizer (both paths) -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

```kotlin
// Chat pill (Compose) — permission + recognition skeleton
val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted -> if (granted) startListening() else showRationale() }
```

```kotlin
// Hilt seam for the review flow (keeps Play Tasks out of ViewModels)
interface ReviewLauncher { suspend fun launch(activity: Activity) }
@Singleton class PlayReviewLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) : ReviewLauncher {
    override suspend fun launch(activity: Activity) {
        val manager = ReviewManagerFactory.create(context)
        val info = manager.requestReviewFlow().await()   // review-ktx coroutine ext
        manager.launchReviewFlow(activity, info).await() // completion ≠ rated; continue flow regardless
    }
}
```

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| review-ktx 2.0.2 (official) | Third-party rating wrappers (e.g. community `appreviews` KTX facades) | Never for v3.0 — they wrap the exact same 2.0.2 artifact with extra API surface and weaker provenance. Only if Warped later wants pre-prompt survey logic, and even then build it in-house. |
| review-ktx 2.0.2 | `com.google.android.play:review` (Java) alone | Equivalent runtime; ktx only adds coroutine/task extensions. Docs list both lines — keep both, use ktx in Kotlin code. Dropping `-ktx` saves nothing measurable. |
| Platform `SpeechRecognizer` inline (partial results in the pill) | `RecognizerIntent` system-UI dialog only (`StartActivityForResult`) | Use as FALLBACK, not primary: the system dialog interrupts chat context and can't stream partials into the input. Primary = inline recognizer; fallback = intent; last resort = mic hidden. |
| Platform speech (Google recognizer service) | On-device STT libs (Vosk, whisper.cpp ports) or ML Kit | Only if offline dictation becomes a requirement — it is explicitly NOT (v3.0 = online dictation into input). Vosk/whisper add 50–150 MB model downloads + NDK surface for a nice-to-have; ML Kit is banned by `auditDependencies` (`mlkit-genai` pattern) and needs Play-services model downloads anyway. |
| DDG `html.duckduckgo.com/html/` scraping via OkHttp+Jsoup | DDG Instant Answer API (`api.duckduckgo.com/?format=json`) | Only as a supplement: Instant Answer returns curated abstract/definition answers, NOT organic web results — insufficient as a grounding source. The HTML endpoint is what community scrapers (DDGS backends, OpenClaw provider) use for organic results. |
| DDG scraping in-house | Paid SERP APIs (Brave Search API, Serper, Bing) or third-party DDG-scraper SaaS | Only if DDG bot-challenges make results unreliable in production telemetry. All add API keys (rejected by the v3.0 "fewer keys" direction) or per-query cost. Start key-free; instrument failure rate; revisit on evidence. |
| `rememberLauncherForActivityResult(RequestPermission)` | Accompanist-permissions | Never — archived upstream; duplicates first-party Compose coverage already on the classpath. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Any NEW audio-recording/playback dep (`media3`, `audiorecord` wrappers) | v3.0 is speech-to-TEXT into the input — no audio messages, no waveforms, no persistence. A media dep invites scope creep and binary size for zero shipped value | Platform `SpeechRecognizer`; text only |
| `RECORD_AUDIO` as install-time assumption / `neverForLocation`-style flags | It's a dangerous runtime permission — must be requested in-context from the mic tap with rationale + graceful denial (dictation disabled, typing unaffected) | Activity Result API flow above; respect "Don't ask again" (guide to Settings, don't loop prompts) |
| Persisting or logging raw audio / transcripts beyond the draft | Privacy liability + Play data-safety disclosure surface for a convenience feature | Hold recognized text only in the in-memory input state; existing chat-history persistence covers sent messages |
| Keeping any Tavily remnant (Keystore key entry, Bearer client, producer, Settings row, strings, test-connection) | Dead code + a Keystore entry prompting key-deletion UX the milestone explicitly removes; partial removal caused the v2.2 orphan-entry lesson | Full deletion: client, producer, Keystore read/write paths, Settings UI + `settings_tavily_*`/`bubble_tavily_*` strings (EN+ES), grounding-pipeline branch. Record migration note re: orphaned `tavily_key` Keystore entry on upgrade (harmless, same class as the v2.2 HF-token orphan) |
| `Jsoup.connect()` for DDG (or any fetch) | Violates the frozen v2.3 fetch policy (centralized OkHttp client = timeouts, UA, redirect/cap discipline, testability) | OkHttp GET → `Jsoup.parse(String)` on the body |
| Custom `OkHttpClient` per DDG call without the grounding budget | v2.4 budget/cancel semantics (5-call cap thinking, single-scope cancel per send) must cover the DDG producer identically | Reuse the grounding client + coroutine scope; DDG search counts toward the model-window-aware grounding budget |
| Gating features on review completion ("rate us to continue") | Play policy violation + the API intentionally doesn't reveal outcome; quotas mean most calls are silent no-ops | Fire-and-forget at a natural pause (e.g. after a successful chat turn / from Settings); always continue normal flow |
| `review-test` / fake-manager artifacts in `implementation` | Test doubles must not ship; release-graph gate risk | If a fake is needed for unit tests, scope it to test sources only (`testImplementation` self-written fake of the `ReviewLauncher` interface — no Play test artifact needed since ViewModels depend on the interface) |

## Stack Patterns by Variant

**If the device has Play Services + recognizer service (typical GMS phone):**
- Inline `SpeechRecognizer` with `EXTRA_PARTIAL_RESULTS` → stream partials into the pill draft; `onError` → show one-line retry hint, keep typed text intact.

**If `isRecognitionAvailable()` is false but a recognition Activity resolves (emulators, some ROMs):**
- `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` via `StartActivityForResult` → single-shot result appended to draft. Same permission path.

**If neither path exists (no GMS, no recognizer):**
- Hide the mic affordance entirely. No error banners for a convenience feature on an incapable device.

**If DDG serves a bot-challenge / CAPTCHA page (HTTP 202 / challenge markup):**
- Treat as empty-result (not fatal): degrade to model-only answer with the existing "search unavailable" notice path. Never retry-loop against the challenge (rate-limit risk). Instrument the hit rate.

**If the review quota suppresses the dialog (common in testing):**
- Expected behavior, not a bug. `onComplete` still fires — continue flow. QA sign-off = "no crash, no behavior change", verified on the internal test track.

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| review-ktx 2.0.2 | minSdk 28 ✓ (Play Core libs floor is API 21), AGP 9.3.0 ✓, Kotlin 2.3.20 ✓, compileSdk/targetSdk 36 ✓ | Pure Java/Kotlin AAR — no `.so`, no 16 KB impact expected (confirm via `verify16KbAlignment`). Stable (non-alpha/beta) so the release-graph pre-release check passes; contains none of the banned direct-dep patterns — MEDIUM-HIGH confidence, final proof is `./gradlew check` after adding |
| Platform SpeechRecognizer / RecognizerIntent | minSdk 28 ✓ (APIs since API 8 / API 3) | Zero version risk — framework APIs. `EXTRA_ENABLE_FORMATTING` (API 33) must be guarded if used; recommend sticking to the stable extras (`EXTRA_LANGUAGE_MODEL`, `EXTRA_PARTIAL_RESULTS`, `EXTRA_MAX_RESULTS`, `EXTRA_PROMPT`) for minSdk-28 safety |
| DDG `html/` endpoint + Jsoup 1.23.2 + OkHttp 4.12.0 | No version interplay — plain HTTPS GET + HTML parse | MEDIUM confidence on selector stability: `.result__a` / `.result__snippet` selectors are unofficial and DDG can change markup or challenge automated traffic without notice. Mitigate: single `DuckDuckGoProducer` behind the existing search-producer interface (swap cost = one file), UA header rotation-ready, empty-result degradation |
| navigation-compose 2.9.8 | All v3.0 drawer/catalog routes | No change. New destinations (if any, e.g. rewritten Help content lives on the existing route) reuse established `composable(route)` + Hilt ViewModel patterns |

## Sources

- developer.android.com/guide/playcore/in-app-review/kotlin-java — `review:2.0.2` + `review-ktx:2.0.2` Gradle lines, ReviewManager flow, quota/no-outcome semantics — HIGH — official docs, updated 2026-09-18
- developer.android.com/reference/android/speech/SpeechRecognizer + RecognizerIntent — RECORD_AUDIO requirement, `isRecognitionAvailable`, extras contract — HIGH — official API reference
- developer.android.com/reference/kotlin/androidx/activity/compose/rememberLauncherForActivityResult + ActivityResultContracts.RequestPermission — Compose permission-request pattern — HIGH — official API reference
- `gradle/libs.versions.toml` + `app/build.gradle.kts` + `scripts/audit-dependencies.sh` (repo HEAD) — navigation 2.9.8, OkHttp 4.12.0, Jsoup 1.23.2, gate patterns — HIGH — first-party evidence
- html.duckduckgo.com (non-JS endpoint exists) + community DDGS `html`/`lite` backends + openclaw DuckDuckGo provider doc (key-free, HTML-scrape, experimental/bot-challenge caveats) — MEDIUM — unofficial but convergent across independent sources; exact selector names need pinning against a live fetch during implementation

---
*Stack research for: v3.0 Chat UX + Voice Dictation (Play review, voice dictation, DDG-only search, drawer/catalog navigation)*
*Researched: 2026-10-02*
