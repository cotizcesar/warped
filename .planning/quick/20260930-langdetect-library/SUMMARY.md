---
audit_acknowledged:
  milestone: v2.5
  at: 2026-10-01
  status: unknown
---

# SUMMARY: langdetect-library (CLEAN branch, Tasks 0–3)

**Branch executed:** CLEAN — Optimaize `language-detector:0.6` integrated
(library-first decider + curated function-word layer, regex kept).
**Commits:** `8c733139` (Task 1: catalog/build/holder), `7df3ca6f`
(Task 2: decider + tests).
**Date:** 2026-09-30.

## Task 0 gate — verdict: CLEAN (empirical, not trust)

POM of `language-detector:0.6` (latest stable on Maven Central; `latest` =
`release` = `0.6`, no newer stable exists) declares 4 compile-scope deps;
each of their POMs declares **zero** dependencies (leaf nodes, verified via
`curl` on Maven Central). Authoritative Gradle check: temporary
`implementation("...:language-detector:0.6")` in `app/build.gradle.kts` +
`./gradlew :app:dependencies --configuration releaseRuntimeClasspath`, diffed
against the pre-add baseline, then fully reverted (`git status` confirmed
`app/build.gradle.kts` clean before Task 1).

Attributable-to-candidate diff (5 lines, nothing else):

```
+--- com.optimaize.languagedetector:language-detector:0.6
|    +--- com.google.guava:guava:18.0
|    +--- com.intellij:annotations:12.0
|    +--- net.arnx:jsonic:1.2.11
|    \--- org.slf4j:slf4j-api:1.7.6
```

Banned-pattern test (`kapt|firebase|moshi|gson|kotlin-reflect|ktor|mcp|
tflite|mlkit|appauth|richtext|camerax|datastore-proto`): **zero new hits**.
Pre-release test (`SNAPSHOT|alpha|beta|rc|cr|-m`): versions are 0.6, 18.0,
12.0, 1.2.11, 1.7.6 — **all stable**. Baseline already contained
`listenablefuture:1.0` via `androidx.concurrent` (pre-existing, kept).

## What was built

- `gradle/libs.versions.toml`: `langdetect = "0.6"` + `langdetect-detector`
  library (transitive jsonic deliberately left undeclared).
- `app/build.gradle.kts`: one `implementation(libs.langdetect.detector)`
  with an `exclude(com.intellij:annotations)` (see deviations), plus
  `packaging.resources.excludes` stripping 83 unused profiles — the APK
  carries exactly 4 files (`languages/{es,en}`,
  `languages.shorttext/{es,en}`, ~158 KB total; verified via
  `unzip -l app-debug.apk`).
- NEW `data/grounding/LanguageDetectorHolder.kt` (pure JVM, no Android
  imports): loads **es+en short-text profiles only** (never
  `readAllBuiltIn`), singleton, daemon-thread pre-warm, `detectSpanish() →
  TRUE/FALSE/NULL` with `SPANISH_CONFIDENCE_THRESHOLD = 0.5`, `Throwable`
  caught narrowly around library calls, test seam (`detectorFactory`,
  `ensureLoadedBlocking`, `resetForTest`).
- `GroundingPrompt.isSpanish` rewired to 3 layers (name, directives,
  `augment`/`buildBlock`/`buildFusedBlock` untouched): **(1)** library TRUE
  → true; **(2)** `SPANISH_MARKERS` → true; **(3)** whole-token
  `SPANISH_FUNCTION_WORDS` → true; else false. Empty → false (fail-open
  preserved). Layers 2–3 mean a FALSE/NULL library never overrides markers
  and a dead/warming detector degrades to regex+words — send never blocked.

## Thread finding (code comment on the holder)

Every `augment` call site runs in `generationJob = viewModelScope.launch(...)`
— **Dispatchers.Main.immediate** (the only `withContext` in ChatViewModel is
the LiteRT model-switch path). Hence profile parsing never happens on the
caller thread: `detectSpanish` returns NULL until the daemon warm finishes,
and the regex/words decide meanwhile. Init cost paid once, off-UI-thread.

## Confidence / short-string behavior (probed, JVM, shorttext profiles)

Pure tildeless Spanish scores es≈1.000 (`hola quien eres`, `gracias por tu
ayuda`, `donde esta la biblioteca`, `que hora es`, `pregunta`); `hola` →
es=0.994, `si` → es=0.996. All probed English controls score en≈1.000.
Short-text profiles chosen over standard (`hola`: 0.994 vs 0.756).

## Test results

- `./gradlew :app:assembleDebug` — GREEN.
- `./gradlew :app:testDebugUnitTest` — **804 tests, 0 failures** (incl. 9
  new/extended: tildeless table, lookalike guards, short-string pins,
  throw/not-ready fallback, marker-non-override).
- `./gradlew :app:auditDependencies` — GREEN; catalog grep shows no new
  banned hits.

## Deviations from plan (auto-fixed, all inline)

1. **[Rule 3] Duplicate-class build failure.** guava-18.0 bundles
   `ListenableFuture` (clashes with standalone 1.0 via androidx.concurrent)
   and annotations-12.0 duplicates annotations-23.0.0. Fixed with scoped
   excludes: `com.intellij:annotations` off the detector declaration;
   standalone `listenablefuture` off the two APK runtime classpaths only
   (test configs untouched; no app source references it — grep-verified;
   runtime class resolves from guava-18 with an identical interface).
2. **[Rule 2] Function-word layer added (plan's test table required
   `"que es hollow knight" → true`, library provably cannot).** JVM probe:
   it scores **en=1.000** — identical to pure-English controls — so no
   threshold rule can catch it without flipping English. Tight curated set
   (excludes `dime`/`favor`/`si` lookalikes), whole-token matching.
3. **[Rule 1] Three existing-test expectations updated** (behavior the task
   intentionally changes): `GroundingPromptTest` no-block assembly fixture
   `pregunta→hello`; `ChatGroundingToggleTest` + `SettingsTavilyTest`
   `pregunta sin urls` now expect the SPANISH directive with synchronous
   warm (deterministic, no daemon-thread timing dependence). Test *intents*
   (assembly order, offline no-search) preserved.

## Honest notes

- Model compliance is probabilistic: correct DETECTION does not guarantee
  the model OBEYS the directive every turn.
- Known artifact: lone `question` scores es=0.999996 (qu+tion ≈ cuestión
  n-grams in the 2-language setup) → Spanish directive on that single-word
  query. Multi-word English is unaffected (all controls en≈1.000). Accepted:
  indistinguishable from real Spanish by score; low harm (soft steering
  line; model still answers the English question).
- Standalone `es`/`que` tokens always trigger Spanish (layer 3); neither is
  an English word, but French `que` would misfire — out of scope (es+en).
- **On-device check needed (no adb here):** send `hola quien eres` and
  confirm a Spanish reply; confirm cold-start first send shows no jank
  (regex path while warming). APK delta ≈ profiles 158 KB + R8-shrunk
  detector/guava/jsonic/slf4j classes.
- slf4j-api ships without a binding (standard harmless `StaticLoggerBinder`
  warning on first use, swallowed by the holder's fail-safe).
