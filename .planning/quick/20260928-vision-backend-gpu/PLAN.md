---
phase: quick
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/warped/data/local/inference/EngineManager.kt
  - app/src/test/java/com/warped/data/local/inference/BackendConstraintTest.kt
autonomous: true
requirements:
  - QUICK-VISION-GPU
must_haves:
  truths:
    - "Vision-capable .litertlm model loads with GPU vision backend instead of failing with CPU vision constraint mismatch"
    - "A vision-slot constraint error retries with the vision slot flipped, not the main backend"
    - "A retry is never attempted when the required backend already equals the current slot value"
  artifacts:
    - path: "app/src/main/java/com/warped/data/local/inference/EngineManager.kt"
      provides: "Slot-aware backend resolution and retry"
  key_links:
    - from: "app/src/main/java/com/warped/data/local/inference/EngineManager.kt"
      to: "backendDetector.probeVisionBackend"
      via: "initWith vision resolution"
      pattern: "probeVisionBackend"
---

<objective>
Fix the vision-backend GPU constraint failure (device log 2026-09-28: JNI INVALID_ARGUMENT
"Vision backend constraint mismatch. Model requires one of [gpu] but Vision backend is CPU",
gemma-4-E2B-it): resolve vision/audio backends properly in initWith and make the
constraint-mismatch retry slot-aware (vision/audio/main).

Purpose: vision-capable .litertlm files require GPU vision; hardcoding CPU guarantees a JNI
failure, and the main-only retry repeats the identical error.
Output: patched EngineManager + extended unit tests, full unit suite green.
</objective>

<execution_context>
@/home/cotizcesar/Documents/warped/.opencode/gsd-core/workflows/execute-plan.md
@/home/cotizcesar/Documents/warped/.opencode/gsd-core/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@app/src/main/java/com/warped/data/local/inference/EngineManager.kt
@app/src/main/java/com/warped/data/local/inference/BackendDetector.kt
@app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt
@app/src/test/java/com/warped/data/local/inference/BackendConstraintTest.kt
@app/src/main/java/com/warped/data/repository/ModelAllowlistRepository.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: Slot-aware parser + backend resolution + retry</name>
  <files>app/src/main/java/com/warped/data/local/inference/EngineManager.kt</files>
  <action>Implement all three production changes in EngineManager (per locked root cause, no research):
  (1) Add a new pure unit-testable function parseConstraintSlot(message: String?): BackendSlot? (name it ConstraintSlot or BackendSlot with VISION/AUDIO/MAIN values) that inspects the JNI message prefix: contains "vision backend" (case-insensitive) → VISION; contains "audio backend" → AUDIO; contains "main backend" → MAIN; anything else carrying a "requires one of [...]" constraint → MAIN (fallback/default preserving existing behavior); null or no constraint info → null. Keep parseRequiredBackend's existing signature and behavior byte-identical for all existing callers and tests (extend, don't break).
  (2) Rewrite initWith into a two-arg form: private initWith(target: ActiveEngine, visionOverride: BackendType? = null, audioOverride: BackendType? = null) — or an internal @VisibleForTesting resolveBackends equivalent if cleaner — that resolves visionBackend = override ?: (if allowlist.findByModelFile(target.modelPath.substringAfterLast("/"))?.capabilities?.vision == true then backendDetector.probeVisionBackend() else BackendType.CPU), and audioBackend = override ?: backendDetector.probeAudioBackend(). Keep the existing specDecoding allowlist lookup untouched. Non-vision models must behave byte-identically to today (explicit CPU vision, probed audio which is CPU today). The ActiveEngine data class stays unchanged (main backend only); vision/audio are resolved per-attempt inside initWith, not stored.
  (3) Make the switchToLiteRT catch block slot-aware: parse both parseConstraintSlot(e.message) (default MAIN when null) and parseRequiredBackend(e.message). Compare required against the CURRENT value of the named slot — main: target.backend; vision/audio: the value initWith resolved for this attempt (recompute via the same resolution, since ActiveEngine carries no vision/audio fields). If required == current slot value, do NOT retry — throw immediately. Otherwise retry once flipping ONLY the named slot: main → existing target.copy(backend = required) path; vision → initWith(target, visionOverride = required); audio → initWith(target, audioOverride = required). On retry failure keep the existing IllegalStateException message shape naming the required backend. Never add NPU probing, ThinkingConfig, speculative-decoding, or UI-copy changes.</action>
  <verify>
    <automated>./gradlew :app:assembleDebug 2>&1 | tail -5</automated>
  </verify>
  <done>initWith probes vision for allowlisted vision-capable models and CPU otherwise; retry flips only the named slot and skips retry when required equals current; parseRequiredBackend signature/behavior unchanged; assembleDebug passes</done>
</task>

<task type="auto">
  <name>Task 2: Extend BackendConstraintTest + full suite green</name>
  <files>app/src/test/java/com/warped/data/local/inference/BackendConstraintTest.kt</files>
  <action>Extend (do not rewrite) BackendConstraintTest: (a) slot-parser truth table — "Vision backend constraint mismatch. Model requires one of [gpu] but Vision backend is CPU" → VISION; "Audio backend constraint mismatch ... requires one of [cpu]" → AUDIO; "Main backend constraint mismatch ... requires one of [gpu]" → MAIN; bare "Model requires one of [gpu]" with no slot prefix → MAIN (fallback default); "Failed to create engine: OOM" and null → null. (b) initWith backend-resolution test: with mockk allowlist returning capabilities.vision=true for the test filename and a backendDetector stub returning GPU from probeVisionBackend(), verify liteRTLmEngine.init captures visionBackend=GPU; with vision=false verify visionBackend=CPU (existing hardcoded-CPU assertion updated only if it asserts the old unconditional CPU for a vision-capable file — non-vision CPU assertions stay). Use coEvery/every stubs consistent with existing mockk style; keep all three existing parseRequiredBackend tests passing unmodified. Then run the FULL unit suite.</action>
  <verify>
    <automated>./gradlew :app:testDebugUnitTest 2>&1 | tail -5</automated>
  </verify>
  <done>Slot-parser truth table green, backend-resolution capture test green, all pre-existing tests unmodified and passing, full :app:testDebugUnitTest green</done>
</task>

</tasks>

<verification>
./gradlew :app:assembleDebug passes; ./gradlew :app:testDebugUnitTest fully green.
Honest note: real GPU-vision load needs on-device confirmation (no adb in this environment) — the user validates by loading the previously failing model (gemma-4-E2B-it) on device.
</verification>

<success_criteria>
- Vision-capable models init with probed vision backend; non-vision models unchanged (CPU).
- Constraint retry flips only the named slot; no retry when required == current.
- parseRequiredBackend signature/behavior preserved; existing tests pass.
- Full unit suite green.
</success_criteria>

<output>
Create `.planning/quick/20260928-vision-backend-gpu/01-SUMMARY.md` when done
</output>
