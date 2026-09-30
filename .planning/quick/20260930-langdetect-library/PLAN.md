# Quick-Task Plan: langdetect-library

## Cause (locked)
`GroundingPrompt.isSpanish()` is a hand-rolled regex over Spanish diacritics
(`[¿¡áéíóúñü]`). Tildeless Spanish ("hola quien eres", "que es hollow knight")
carries zero markers → misclassified as English → wrong language directive.
A real language-detection library with es+en short-text profiles fixes the
recall gap without heuristics maintenance.

## Fix (locked — conditional on Task 0 gate)
Integrate Optimaize `com.optimaize.languagedetector:language-detector`
(JVM, offline, no network, no Play Services, KB-sized profiles), loading
ONLY es+en profiles — **IF AND ONLY IF** its transitive dependency tree is
clean against the guardrail. Fail-safe fallback: library uncertain / throws /
init-fails → current regex heuristic decides; never crash, never block send;
empty/uncertain → English default (current fail-open preserved).

**Decision rule (planner locks, executor obeys — no judgment calls):**
resolve the tree first; CLEAN → integrate per Tasks 1–3; DIRTY → ABANDON the
dep, do Task 4 instead (extend regex with curated function-word list, same
fail-open contract), and say so in SUMMARY.

## Guardrail nuance (read before Task 0)
`scripts/audit-dependencies.sh` enforces TWO things: (a) no banned patterns
(kapt, firebase, moshi, gson, kotlin-reflect, ktor, mcp, tflite, mlkit-genai,
appauth, compose-richtext, camerax, datastore-proto) as DIRECT declarations
in `gradle/libs.versions.toml`; (b) no pre-release artifacts in
`releaseRuntimeClasspath`. Transitive gson/kotlin-reflect/moshi already exist
in the graph via required Google libs (tink-android ← security-crypto,
litertlm-android, benchmark-common) and are explicitly exempted in the script
header. The Task 0 gate is therefore STRICTER than the script: the candidate
must not ADD any new banned transitive beyond what is already in the graph.
Diff attributable-to-language-detector, not absolute presence.

## Out of scope
More locales, device-locale prior, UI language, prompt wording changes
(directive strings untouched).

## Tasks

### Task 0 (GATE): CLEAN-or-ABANDON dependency verdict — do this first, nothing else until decided
Files: none (read-only investigation; do NOT edit catalog/build yet).

Actions:
1. Resolve the candidate tree WITHOUT adding the dep: check Maven Central
   metadata for `com.optimaize.languagedetector:language-detector` latest
   stable (expected `0.6`) and read its POM's `<dependencies>`; then confirm
   empirically with a scratch check (`./gradlew :app:dependencies
   --configuration releaseRuntimeClasspath` after a temporary local add, OR
   `mvn dependency:tree`-equivalent reasoning from the POM — the empirical
   Gradle resolution is authoritative; revert the scratch add before proceeding).
2. List every transitive the candidate pulls (expected: `net.arnx:jsonic`
   ~1.3.x, pure-Java JSON, no deps of its own) and test each against the
   banned list: kapt, firebase, moshi, gson, kotlin-reflect, ktor, mcp,
   tflite, mlkit-genai, appauth, compose-richtext, camerax, datastore-proto.
   ALSO check for pre-release coordinates (SNAPSHOT/alpha/beta/RC) — banned
   by gate (b) above.
3. Verdict:
   - CLEAN (no new banned transitive, no pre-release) → proceed to Tasks 1–3.
   - DIRTY (any new banned transitive or pre-release) → SKIP Tasks 1–3, do
     Task 4 instead.
4. Record the verdict + full transitive list in SUMMARY regardless of branch.

Verify:
- Verdict stated with evidence (transitive list + banned-pattern diff).
- If CLEAN: scratch add fully reverted (`git status` clean of build/catalog changes before Task 1).

Done:
- One of: CLEAN (Tasks 1–3 follow) or DIRTY (Task 4 follows). No middle ground.

### Task 1 (CLEAN branch): Catalog + build dep + singleton lazy detector
Files:
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- NEW `app/src/main/java/com/warped/data/grounding/LanguageDetectorHolder.kt`
  (name flexible; keep it in `data/grounding`, pure JVM, no Android imports)

Actions:
1. Catalog: add `langdetect = "0.6"` (or the Task-0-verified latest stable —
   if Maven Central shows a newer stable than 0.6, use it and note in
   SUMMARY) under `[versions]`, plus `langdetect-detector = { group =
   "com.optimaize.languagedetector", name = "language-detector",
   version.ref = "langdetect" }` under `[libraries]`. Do NOT add a catalog
   entry for the transitive JSON parser (leave it transitive; fewer direct
   declarations = smaller audit surface).
2. Build: add ONE line `implementation(libs.langdetect.detector)` in the
   dependencies block (near `jsoup`/`highlights` third-party parsing libs).
   Do NOT add kapt, ksp, or any plugin.
3. New holder object, e.g. `internal object LanguageDetectorHolder`:
   - Loads ONLY es+en profiles (`LanguageDetectorBuilder.fromLanguages("es",
     "en")` — never `fromAllBuiltInLanguages()`, never the full profile pack).
   - Singleton, lazily initialized, thread-safe (`by
     lazy(LazyThreadSafetyMode.SYNCHRONIZED)` or equivalent). Init cost
     (profile JSON parse) is paid ONCE on first use, NEVER on the UI thread:
     first use happens on the send path which is already background — confirm
     the `isSpanish` call-site dispatcher via grep (ChatViewModel send path /
     `augment` callers) and state the finding in a code comment on the holder.
     If any caller is on Main, pre-warm from a background scope instead —
     do NOT add Hilt/DI sprawling for this; a plain object is enough.
   - Exposes `fun detectSpanish(text: String): Boolean?` returning
     TRUE (confident Spanish) / FALSE (confident non-Spanish) / NULL
     (empty input, below confidence threshold, init failure, or ANY thrown
     exception — catch `Throwable` narrowly around library calls only).
     Choose a confidence threshold (e.g. probability > 0.5 with Spanish the
     top language) and pin it as a named constant with a comment.
   - Test seam: make the library call overridable for tests WITHOUT Android
     (e.g. `internal var detectorFactory` / constructor-injectable delegate
     with a default production implementation). The fallback test (Task 2.4)
     must be able to force a throw on the JVM.
   - NEVER crash, NEVER block send: all library interaction wrapped so any
     failure → NULL → caller falls back to regex.

Verify:
- `./gradlew :app:assembleDebug` green.
- `./gradlew :app:auditDependencies` green (runs in `check` too).
- `grep -viE 'kapt|firebase|moshi|gson|kotlin-reflect|ktor|mcp|tflite|mlkit|appauth|richtext|camerax' gradle/libs.versions.toml` shows no new hits vs. pre-change.

Done:
- Dep resolves offline-safe; holder loads es+en only, lazy + thread-safe,
  documented call-site thread finding; uncertain/throw → NULL contract.

### Task 2 (CLEAN branch): Wire `isSpanish` library-first + tests
Files:
- `app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt`
  (modify `isSpanish` path only)
- `app/src/test/java/com/warped/data/grounding/GroundingPromptTest.kt`
  (extend; existing tests keep passing unmodified unless they pin behavior
  this task intentionally changes — none should)

Actions:
1. Rewire the decider (keep the name `isSpanish`, keep `SPANISH_MARKERS`
   regex AS the fallback — do NOT delete it, do NOT touch
   `languageDirective()` selection, directives, `augment()`, `buildBlock()`,
   `buildFusedBlock()`):
   - `isSpanish(text)`: consult `LanguageDetectorHolder.detectSpanish(text)`
     first — TRUE → true, FALSE → false, NULL → fall back to
     `SPANISH_MARKERS.containsMatchIn(text)`. Empty input → false (English
     default, current fail-open preserved).
2. Tests to ADD (JUnit5 + Truth, JVM, no Android):
   - ES/EN table incl. TILDELESS Spanish: "hola quien eres" → true,
     "que es hollow knight" → true, accented "¿Cómo estás?" → true,
     "What is the capital of France?" → false, "hello world" → false.
   - Short strings: "hola" → true (or document library's actual short-text
     behavior if it returns NULL → regex fallback → false; pin WHATEVER the
     fail-open contract produces, and note it — do NOT force the library to
     be confident where it isn't).
   - Empty ("") → false; whitespace-only → false.
   - Mixed ("What is el niño?") → pin current contract outcome.
   - Fallback test: force the detector seam to throw → assert the regex
     decides (e.g. "niño" → true, "hello" → false despite the throw).
   - Existing directive/augment tests keep passing untouched (fail-open +
     selection logic unchanged).
3. Performance sanity: NO flaky timing test. Code-inspection proof only: a
   comment on the holder (Task 1) + a test-Upstream note in SUMMARY stating
   init is lazy/singleton/off-UI-thread. If a test would need
   `Thread.sleep`/timing assertions — do NOT write it.

Verify:
- `./gradlew :app:testDebugUnitTest --tests "com.warped.data.grounding.GroundingPromptTest"` green, then full `:app:testDebugUnitTest` green.
- `./gradlew :app:auditDependencies` green.

Done:
- Library-first decider with regex fallback; table + edge + fallback tests
  green; no directive/augment/selection behavior changed; full unit suite green.

### Task 3 (CLEAN branch): Full verify + SUMMARY
Files: SUMMARY.md (write per quick-task convention).

Actions:
1. Run `./gradlew :app:assembleDebug`, full `:app:testDebugUnitTest`, and
   `auditDependencies` — all green.
2. Write SUMMARY: transitive list from Task 0, thread finding, confidence
   threshold chosen, short-string behavior pinned, honest notes below.

Verify: all three commands green.

Done: SUMMARY written; branch CLEAN recorded.

### Task 4 (DIRTY branch — ONLY if Task 0 verdict is DIRTY; skip Tasks 1–3)
Files:
- `app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt`
- `app/src/test/java/com/warped/data/grounding/GroundingPromptTest.kt`

Actions:
1. ABANDON the library: no catalog entry, no build dep, no holder. State the
   dirty transitive(s) in SUMMARY.
2. Extend the fallback instead: add a curated tildeless-Spanish function-word
   list consulted BEFORE/OR-WITH `SPANISH_MARKERS` inside `isSpanish()` —
   word-boundary, case-insensitive matching over a small curated set (e.g.
   que, es, hola, quien, eres, esta, estas, porque, como, donde, cuando,
   gracias, favor, eres, estoy, tienes, quieres, dime, cual — keep the list
   tight and commented; each entry must be overwhelmingly Spanish-indicative
   as a standalone token). Same fail-open contract: empty → false, no crash,
   pure JVM, no Android imports.
3. Tests: same table as Task 2 ("hola quien eres" → true, "que es hollow
   knight" → true, "hello world" → false, empty → false, mixed pinned).
4. Run `./gradlew :app:assembleDebug` + full `:app:testDebugUnitTest` green;
   `auditDependencies` untouched-green. Write SUMMARY stating DIRTY verdict +
   dirty transitive(s) + regex-extension outcome.

Verify:
- `./gradlew :app:assembleDebug` green; full `testDebugUnitTest` green.

Done:
- No new dep; tildeless Spanish detected via curated list; suite green;
   SUMMARY records the ABANDON decision with evidence.

## Honest notes
- Model compliance is still probabilistic: correct language DETECTION does
  not guarantee the model OBEYS the directive every turn.
- Library detection on short/tildeless strings is itself probabilistic;
  the fail-open (English default) + regex fallback preserve current behavior
  wherever the library is uncertain.
- On-device ES check needed: no adb in this environment; verify on a device
  that "hola quien eres" yields a Spanish reply.
- APK size: es+en short-text profiles are KB-sized; no full profile pack.
