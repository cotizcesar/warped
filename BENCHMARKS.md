# Warped Benchmarks

This file is the source of truth for Warped's performance numbers. Targets and measurement procedures live here; the actual numbers are filled in by CI on a reference device (Pixel 7, Android 14, 8GB RAM).

## 1. Cold-start

**Target:** <1.5s from launcher tap to first frame.
**How:** `ColdStartBenchmark` in `:app/src/androidTest/java/com/warped/benchmark/` uses `StartupTimingMetric` + `FrameTimingMetric` in `StartupMode.COLD`, 5 iterations.
**Run:** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.warped.benchmark.ColdStartBenchmark`
**Observed:** `[CI fills in]`

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
