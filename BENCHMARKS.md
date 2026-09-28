# Warped Benchmarks

This file is the source of truth for Warped's performance numbers. Targets and measurement procedures live here; the actual numbers are filled in by CI on a reference device (Pixel 7, Android 14, 8GB RAM).

## 1. Cold-start (PERF-16)

**Target:** <1s from launcher tap to first frame (normative PERF-16 target on Pixel 7, Android 14, 8GB RAM).
**How:** `ColdStartBenchmark` in `:app/src/androidTest/java/com/warped/benchmark/` uses `StartupTimingMetric` + `FrameTimingMetric` in `StartupMode.COLD`, 5 iterations.
**Run:** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.warped.benchmark.ColdStartBenchmark`
**Observed:** `[CI fills in]`

### PERF-16 measurement protocol (before/after Baseline Profile)

1. Measure WITHOUT the profile first (delete/empty `app/src/main/baselineProfiles/baseline-prof.txt`
   temporarily — restore afterwards): run the command above, record `timeToInitialDisplayMs` median.
2. Generate the profile on hardware: `./gradlew :app:generateReleaseBaselineProfile`, then copy the
   emitted profile (`find app/src -name baseline-prof.txt -path "*generated*"` shows the exact path)
   to `app/src/main/baselineProfiles/baseline-prof.txt`. Requires a device that allows compilation
   control (rooted/userdebug); on user builds the task fails — use CI instead.
3. Rebuild release with the profile and measure again. The **delta** (before/after on the SAME
   hardware) is the claim, never the absolute number.
4. Record below: `timeToInitialDisplayMs` median for both runs, the delta, and the full environment
   block (device/AVD image, API level, ABI, host CPU/RAM, build type).

> **Emulator note:** emulator ART compilation behavior (no dexopt profiles, host-CPU variance) makes
> emulator numbers NON-COMPARABLE to the Pixel 7 reference target. Compare emulator-to-emulator
> (before/after profile) only. Pixel 7 reference numbers stay TODO via CI alongside PERF-12/13.

**Observed (emulator):** `[TODO — hardware-blocked 2026-09-28: build-machine emulator-5554 is a user
build without root (adbd cannot run as root), so compilation reset/profile collection cannot run
there. Run the protocol above on a rooted emulator or CI.]`

## 2. Warm start

**Target:** <800ms from background tap to first frame.
**How:** Same as cold-start but `StartupMode.WARM`.
**Run:** `... -Pandroid.testInstrumentationRunnerArguments.class=com.warped.benchmark.ColdStartBenchmark -Pandroid.testInstrumentationRunnerArguments.warm=true`
**Observed:** `[CI fills in]`

## 3. Streaming frame rate

**Target:** 60fps median, <2% jank, while a 7B Q4_K_M model decodes 256 tokens.
**How:** `StreamingFrameBenchmark` in the same module, uses `FrameTimingMetric` for 3 iterations of 10-second streaming sessions.
**Observed:** `[CI fills in]`

## 4. Peak memory during benchmark

**Target:** <1.5× model file size.
**How:** `MemorySampler` from `:app/src/main/java/com/warped/data/local/benchmark/MemorySampler.kt` polls JVM + native heap at 100ms during `ModelBenchmarkWorker`. The peak value is persisted to `benchmark_results.peakMemoryBytes` and surfaced in the Benchmark screen.
**Observed:** `[CI fills in]`

## 5. SQLCipher overhead (PERF-13)

**Decision rule:** if SQLCipher adds >20% to the 1000-message insert + query roundtrip, drop SQLCipher in v2.1 and rely on Android Keystore + file-system encryption. Otherwise keep it.

**How:** Robolectric unit test in the benchmark androidTest source set compares an `androidx.room.Room.databaseBuilder` instance with and without `SupportFactory(passphrase)` for 1000 inserts + one full-table `SELECT COUNT(*)` query. Records wall-clock time.

**Observed (local, Pixel 7 emulator, 2026-06-06):** `[To fill in CI]`

| Configuration    | Insert 1000 rows | Count query |
|------------------|------------------|-------------|
| SQLCipher ON     | TBD              | TBD         |
| SQLCipher OFF    | TBD              | TBD         |
| Overhead         | TBD              | TBD         |

## 6. Compose recomposition (PERF-01..06 — manual)

**How:** Layout Inspector in Android Studio, "Recomposition counts" column, during a long chat session with streaming. After Phase 43, the traffic-light recomposition count should not increment on every streaming character.

**Observed:** `[Manual]`

## 7. Hilt startup (PERF-07)

**How:** `adb logcat | grep Hilt` and inspect the generated `*_HiltComponents` for any eager `@Singleton` provider on heavyweight classes (`LiteRtLlmEngine`, `EngineManager`). After Phase 43 the only `@Singleton` graph nodes are the lightweight caches (`BackendDetector`, `KeystoreManager`, `InputSanitizer`, `Json`, the two `OkHttpClient` variants).

**Observed:** `[Manual]`
