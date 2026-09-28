---
phase: 45-foundation-refresh
plan: "02"
subsystem: inference-engine
tags: [litertlm, r8, mmap-cache, allowlist, jni, gradle]

# Dependency graph
requires: [45-01 green foundation catalog]
provides:
  - litertlm-android pinned at 0.17.1 stable with verified JNI + API surface
  - 0.17.x R8 keeps (ToolSet/OpenApiTool/@Tool entry points)
  - Version-namespaced mmap cache helper with upgrade-install invariant tests
  - model_allowlist.json asset + ModelAllowlistRepository capability queries
affects: [46-runtime-hardening, 47-tool-execution, 48-release]

# Tech tracking
tech-stack:
  added: []
  patterns: ["pure-Kotlin namespace helper for Android-bound paths (JVM-testable)", "verified-only capability flags", "opt-in thinking surface with default-off behavior"]

key-files:
  created: [app/src/main/assets/model_allowlist.json, app/src/main/java/com/warped/data/local/inference/LiteRtLmCache.kt, app/src/main/java/com/warped/data/repository/ModelAllowlistRepository.kt, app/src/test/java/com/warped/data/repository/ModelAllowlistTest.kt, app/src/test/java/com/warped/data/local/inference/LiteRtLmCacheTest.kt]
  modified: [gradle/libs.versions.toml, app/proguard-rules.pro, app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt, app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt, app/src/main/java/com/warped/data/local/inference/LiteRtLmCacheManager.kt]

key-decisions:
  - "Adopt proper Backend.NPU(nativeLibraryDir) over NPU-to-GPU fallback (0.17.x exposes it; dead path today since BackendDetector only emits CPU/GPU)"
  - "ThinkingConfig/maxOutputToken surfaced as opt-in overload params defaulting to null (zero behavior change); thought-channel extractor exists but is not wired into chat Deltas (Phase 47 owns thinking UX)"
  - "Allowlist anchored on Gemma 3n E2B/E4B int4 .task (app's documented staff-pick family); filenames + byte sizes verified live via HF API + CDN HEAD 2026-09-27"
  - "Capability flags true only for engine-surface-verified features (text/vision/audio/speculative-decoding); thinking/function-calling/MTP/extended-context explicitly false until device-verified"
  - "Release R8 verified via throwaway keystore (repo keystore absent in this checkout); local.properties restored byte-identical"

requirements-completed: [LRT-07, LRT-09]

# Metrics
duration: ~1h
completed: 2026-09-27
---

# Phase 45 Plan 02: LiteRT-LM 0.17.1 Engine Migration Summary

**Engine on pinned stable 0.17.1 with NPU/ThinkingConfig deltas adopted-or-documented, R8 keeps extended, version-namespaced cache unit-pinned, and verified-only model allowlist created — full suite green, audit empty, awaiting human device smoke**

## Performance

- **Duration:** ~1h
- **Started:** 2026-09-27T06:50:00Z (approx)
- **Completed:** 2026-09-27T07:50:00Z (approx)
- **Tasks:** 2/2 auto (1 checkpoint:human-verify pending — returned to orchestrator)
- **Files created:** 5 | **modified:** 5

## Accomplishments

- 0.17.1 confirmed latest stable via Google Maven `maven-metadata.xml` (0.17.0-alpha1 exists; stable pin chosen per D-stable-only)
- AAR native audit: `liblitertlm_jni.so` per ABI (arm64-v8a, x86_64) — `System.loadLibrary("litertlm_jni")` unchanged; notable: 0.13.1's separate `libLiteRt.so` + `libLiteRtClGlAccelerator.so` are consolidated into the single JNI lib in 0.17.1
- 0.17.x Kotlin surface re-verified from 0.17.1 AAR bytecode (`javap`): `EngineConfig` (+optional `maxNumTokens`/`maxNumImages`, source-compatible), `ConversationConfig` (+`thinkingConfig`, `maxOutputToken`, `channels`), `ThinkingConfig(enableThinking, thinkingTokenBudget)`, `Backend.NPU(nativeLibraryDir)`, `ToolSet`/`@Tool`/`@ToolParam`/`OpenApiTool`/`ReflectionTool`, `Message.getChannels(): Map<String,String>`, `ExperimentalFlags.enableSpeculativeDecoding`, per-message `thinkingConfig` override, and a new `Capabilities` class (`supportsThinking()`, `supportsFunctionCalling()`, `hasSpeculativeDecodingSupport()`)
- R8 keeps extended with explicit 0.17.x tool entry points; `assembleRelease` (R8 full mode) clean
- `model_allowlist.json` CREATED (was absent) + `ModelAllowlistRepository` with `supportsThinking` / `supportsFunctionCalling` / `supportsSpeculativeDecoding` / `supportsExtendedContext` / `supportsMtp` / `supportsModality` queries
- Full unit suite green: 180 tests, 0 failures (8 new); dependency audit empty

## Task Commits

1. **Task 1: LiteRT-LM 0.17.1 bump + native-lib verification** — `7115f1f` (note: commit also contains orchestrator's pre-staged `.planning` milestone-archiving renames; same phenomenon as 45-01 — see Issues)
2. **Task 2: API-surface + R8 + cache re-verification and allowlist creation** — `819068c` (clean single-scope, 9 files, pathspec-limited)

## Files Created/Modified

- `gradle/libs.versions.toml` — `litertlm` 0.13.1 → 0.17.1 (+ provenance comment)
- `app/proguard-rules.pro` — explicit keeps: `ToolSet`, `OpenApiTool`, `@Tool`, `@ToolParam`, `ReflectionTool`, `ToolKt`, `Capabilities`, `-keepattributes *Annotation*`
- `app/src/main/java/com/warped/data/local/inference/LiteRTLmEngine.kt` — proper `Backend.NPU(nativeLibraryDir)` (choice recorded in comment); `createConversation()` overload with optional `thinkingConfig`/`maxOutputToken` (defaults = pre-0.17 behavior); cache path via `LiteRtLmCache`
- `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt` — `THOUGHT_CHANNEL = "thought"` + `extractThoughtContent()` (safe: null when thinking disabled; NOT wired into chat Deltas)
- `app/src/main/java/com/warped/data/local/inference/LiteRtLmCache.kt` — NEW pure object: `namespaceFor()`, `isIsolated()`, `DEFAULT_CAP_BYTES` (500 MB)
- `app/src/main/java/com/warped/data/local/inference/LiteRtLmCacheManager.kt` — uses shared namespace helper + shared cap constant (private duplicate removed)
- `app/src/main/assets/model_allowlist.json` — NEW: Gemma 3n E2B (3,136,226,711 B) + E4B (4,405,655,031 B) int4 `.task`, sizes verified via CDN HEAD
- `app/src/main/java/com/warped/data/repository/ModelAllowlistRepository.kt` — NEW asset loader + capability queries; pure `parseModelAllowlist()` for JVM tests
- `app/src/test/.../ModelAllowlistTest.kt` (5 tests), `.../LiteRtLmCacheTest.kt` (3 tests) — NEW, all green
- `app/src/main/AndroidManifest.xml` — UNCHANGED (verified): all three `uses-native-library` entries kept — `libvndksupport.so` + `libOpenCL.so` per official LiteRT-LM Android docs (GPU backend, `required=false`); `libcdsprpc.so` for Qualcomm Hexagon DSP RPC (justified further now that `Backend.NPU` is properly adopted; `required=false`)

## AAR Verification Record (Task 1 acceptance)

```
litertlm-android-0.17.1.aar:
  jni/arm64-v8a/liblitertlm_jni.so  (21,802,960 B)
  jni/x86_64/liblitertlm_jni.so     (25,968,008 B)
litertlm-android-0.13.1.aar (baseline):
  jni/{arm64-v8a,x86_64}/liblitertlm_jni.so + libLiteRt.so + libLiteRtClGlAccelerator.so
```

`assembleDebug` clean on 0.17.1 with zero source changes required (existing `EngineConfig`/`ConversationConfig` call sites source-compatible); audit `AUDIT-CLEAN`.

## Decisions Made

- NPU: adopt-proper (not keep-fallback) — API verified in AAR; dead path today; `libcdsprpc.so` manifest entry retained with reason
- Thinking: surface-but-don't-enable — overload + extractor, default off; thought tokens never merged into answer stream (would corrupt output); THINK-02 UX stays a later phase
- Tools: re-verify only — no `ToolSet`/`tool()` attached to the chat path; Phase 47 owns wiring (int-type tool-call fix in 0.17.1 noted for Phase 47 re-test)
- Allowlist scope: 2 entries (Gemma 3n family only) — no unverifiable third-party model IDs/sizes; `llmPromptTemplates: {}` (engine default), `taskTypes: ["chat"]`
- Release-signing workaround (Rule 3, see deviations)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Release keystore absent — R8 verified via throwaway keystore**
- **Found during:** Task 2 (`assembleRelease` gate)
- **Issue:** `validateSigningRelease` failed — `keystore/warped-release.jks` (referenced by tracked `local.properties`) does not exist in this checkout (keys live on CI). Pre-existing environment limitation, unrelated to the engine bump.
- **Fix:** Generated a 30-day throwaway keystore at `/tmp/opencode/debug-release.jks`, temporarily pointed the tracked `local.properties` at it, ran `assembleRelease` → BUILD SUCCESSFUL (R8 full mode holds on 0.17.1), then restored `local.properties` byte-identical (verified via `git diff --quiet` + `diff` against backup). No repo-tracked file left modified; throwaway key lives outside the repo.
- **Files modified:** none (transient only)
- **Committed in:** N/A (verification scaffolding, not product change)

## Issues Encountered

- Task 1 commit `7115f1f` swept orchestrator's pre-staged `.planning` milestone-archiving renames into the task commit (same as 45-01 documented). Task 2 commit `819068c` used `git commit -m ... -- <pathspec>` form and is clean single-scope (9 files). No action needed — flagging for orchestrator awareness.
- `git commit -- <paths> -m` ordering matters: pathspec must follow `--` AFTER `-m`; first attempt failed with "pathspec '-m' did not match" (no state harmed — files were already staged, retry succeeded).

## User Setup Required

**Device smoke (checkpoint:human-verify — BLOCKING for phase gate, returned to orchestrator):**

1. Install the release APK on a real arm64 device with a downloaded `.litertlm`/`.task` model on it
2. Open Warped, load the downloaded model — expect successful load with no `UnsatisfiedLinkError`
3. Send a chat message and confirm streaming tokens arrive
4. Confirm existing chat history, presets, and endpoints from before the upgrade are intact
5. Report device model + Android version + model filename + pass/fail per step

Note: release APK in this environment was signed with a throwaway key (repo keystore absent) — install requires uninstalling the prior-signed build first, or CI should produce the signed APK for the smoke.

## Next Phase Readiness

- Engine foundation ready for Phase 46 (runtime hardening) and Phase 47 (tool execution builds on the verified `ToolSet`/`@Tool` surface + 0.17.1 int-type tool-call fix)
- Watch items: `Capabilities(modelPath)` model-file-based queries (new 0.17.1 API) could back future dynamic capability detection; per-model thinking/function-calling flags flip to true only after device verification
- Deferred: NPU probing in `BackendDetector` (engine side ready); stale-namespace lazy reclamation ("clear stale caches" action)

## Self-Check: PASSED

- All 5 created files exist on disk; `litertlm = "0.17.1"` in catalog
- Both commits verified in `git log` (`7115f1f`, `819068c`); Task 2 commit is single-scope (9 files, no deletions per `git diff --diff-filter=D`)
- Test result XMLs confirm 180 total / 0 failures; `assembleRelease` BUILD SUCCESSFUL; audit `AUDIT-CLEAN`
- No stubs: repository queries functional, thought extractor functional (intentionally default-off, documented); no placeholder text or TODOs introduced
- No new threat surface: allowlist is a read-only asset, repository reads app-private assets only, cache helper is pure path math — no new network/auth/schema surface beyond the plan's threat register

---
*Phase: 45-foundation-refresh*
*Completed: 2026-09-27*
