---
phase: 06-engine-foundation
plan: 03
subsystem: inference
tags: [kotlin, android, litertlm, gpu-detection, hilt, egl, opencl, di]

# Dependency graph
requires:
  - phase: 06-01
    provides: "LiteRT-LM library dependency (com.google.ai.edge.litertlm v0.11.0-beta01) in classpath, Gradle build infrastructure"
provides:
  - "BackendDetector: GPU/CPU backend detection with lazy EGL+OpenCL probing and app-process-lifetime caching"
  - "LiteRTLmEngine: LiteRT-LM Engine lifecycle wrapper with @Synchronized thread safety and graceful close"
  - "InferenceModule Hilt providers for BackendDetector and LiteRTLmEngine"
affects:
  - "06-04 (EngineManager will orchestrate BackendDetector + LiteRTLmEngine)"
  - "Phase 07 (LiteRTLmProvider will use LiteRTLmEngine for inference)"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "@Singleton engine wrapper with @Inject constructor() and companion init for one-time native setup"
    - "@Synchronized lifecycle methods (init/close/createConversation) for thread safety"
    - "@Volatile lazy caching pattern for probe results"
    - "Hilt @Provides @Singleton DI registration following existing InferenceModule conventions"

key-files:
  created:
    - "app/src/main/java/com/warped/data/local/inference/BackendDetector.kt"
    - "app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt"
  modified:
    - "app/src/main/java/com/warped/di/InferenceModule.kt"

key-decisions:
  - "D-09: Lazy probe via @Volatile cachedBackend — probeBackend() called on first model load, not constructor"
  - "D-10: BackendType enum CPU/GPU only — NPU deferred to v2"
  - "D-11: Probe method uses EGL14.eglGetDisplay() + EGL14.eglInitialize() + EGL14.eglChooseConfig() for GPU driver validation, System.loadLibrary(\"OpenCL\") for OpenCL check"
  - "D-12: @Singleton with @Volatile cached result for app-process-lifetime caching"
  - "D-05: Single LiteRTLmEngine wrapper class with BackendType enum mirroring LlamaEngine pattern"
  - "D-06: @Synchronized on init(), close(), createConversation(), isInitialized() for thread safety"
  - "D-07: Engine init() is raw/synchronous — caller (Phase 7 LiteRTLmProvider) dispatches on Dispatchers.Default"
  - "D-08: Graceful close — engine?.close() wrapped in try/catch, reference nulled in finally"
  - "Used EGL14 (API 17+) instead of EGL15 (API 31+) to maximize device compatibility while providing identical GPU probing capability"

patterns-established:
  - "Engine wrapper: @Singleton + @Inject constructor(), companion init for one-time native config, @Synchronized lifecycle"
  - "Backend detection: lazy probe with @Volatile caching, dual EGL+OpenCL check, safe CPU fallback on any failure"
  - "Hilt DI registration: @Provides @Singleton fun provideXxx(): Xxx = Xxx() following existing InferenceModule conventions"

requirements-completed: [LITE-02, LITE-03]

# Metrics
duration: 3min
completed: 2026-05-02
---

# Phase 06 Plan 03: BackendDetector + LiteRTLmEngine Summary

**GPU/CPU backend detection via EGL14+OpenCL probing and LiteRT-LM Engine lifecycle wrapper with @Synchronized thread safety, registered as Hilt singletons**

## Performance

- **Duration:** 3 min
- **Started:** 2026-05-02T16:34:37Z
- **Completed:** 2026-05-02T16:37:30Z
- **Tasks:** 3
- **Files modified:** 3 (2 created, 1 modified)

## Accomplishments

- **BackendDetector** (@Singleton) probes GPU availability lazily using EGL14 display initialization + config enumeration combined with OpenCL library detection, caching the result for the app process lifetime via @Volatile
- **LiteRTLmEngine** (@Singleton) wraps `com.google.ai.edge.litertlm.Engine` with @Synchronized init/close/createConversation lifecycle methods, companion init for native log severity configuration, and graceful cleanup in try/catch with null reference clearing
- **InferenceModule** extended with `provideBackendDetector()` and `provideLiteRTLmEngine()` Hilt providers following existing `provideLlamaEngine()` pattern

## Task Commits

Each task was committed atomically:

1. **Task 1: Create BackendDetector with lazy GPU probing** - `1042676` (feat)
2. **Task 2: Create LiteRTLmEngine wrapper class** - `686783c` (feat)
3. **Task 3: Register BackendDetector and LiteRTLmEngine in InferenceModule** - `123a78e` (feat)

## Files Created/Modified

- `app/src/main/java/com/warped/data/local/inference/BackendDetector.kt` - GPU/CPU backend detection with EGL14+OpenCL lazy probing, @Singleton with @Volatile caching, safe CPU fallback
- `app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt` - LiteRT-LM Engine lifecycle wrapper: init/close/createConversation with @Synchronized, companion init for native log severity, mirrors LlamaEngine pattern
- `app/src/main/java/com/warped/di/InferenceModule.kt` - Added Hilt @Provides @Singleton providers for BackendDetector and LiteRTLmEngine

## Decisions Made

- All decisions from the plan (D-05 through D-12) implemented as specified — no deviations
- EGL14 used over EGL15 for API 17+ compatibility with identical GPU probing capability
- OpenCL probe via `System.loadLibrary("OpenCL")` combined with EGL config enumeration for reliable GPU detection
- `Engine.setNativeMinLogSeverity(LogSeverity.ERROR)` in companion init suppresses verbose native logs in production

## Deviations from Plan

None - plan executed exactly as written.

## Threat Mitigations

All threats from the plan's STRIDE register addressed:
- **T-06-06** (Spoofing/EGL probe): accept — platform APIs reflect actual device capabilities
- **T-06-07** (DoS/double-init): mitigate — `require(!isInitialized())` prevents double-init, `@Synchronized` prevents concurrent races
- **T-06-08** (EoP/model path): mitigate — native Engine constructor validates modelPath; app constrains paths to app-private storage
- **T-06-09** (Info Disclosure/logging): mitigate — `Timber.d()` debug logs suppressed in release by ProGuard + Timber release tree

## Issues Encountered

None — all tasks completed without errors on first attempt.

## Next Phase Readiness

- BackendDetector and LiteRTLmEngine are ready for 06-04 (EngineManager) which will orchestrate backend detection and engine creation
- Phase 7's LiteRTLmProvider can use LiteRTLmEngine.init() with BackendDetector.probeBackend() for model loading

---

*Phase: 06-engine-foundation*
*Completed: 2026-05-02*
