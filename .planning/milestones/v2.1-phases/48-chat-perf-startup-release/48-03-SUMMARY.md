---
phase: 48-chat-perf-startup-release
plan: "03"
subsystem: release-hardening
tags: [hard-01, log-hygiene, r8-full-mode, dependency-audit, keystore, release-gate]
requires: [48-01-perf-fixes, 48-02-startup-baseline]
provides: [content-free-release-logging, r8-fullmode-verification, green-dependency-audit, network-keystore-reaudit]
affects: [release-smoke-device-checkpoint]
tech-stack:
  added: []
  patterns: [length-only-shape-logging, DEBUG-only-RedactingTree, explicit-R8-keeps]
key-files:
  modified:
    - app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt
  verified-unchanged:
    - app/proguard-rules.pro
    - scripts/audit-dependencies.sh
    - app/src/main/res/xml/network_security_config.xml
    - app/src/main/java/com/warped/data/local/security/KeystoreManager.kt
    - app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt
    - app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt
    - app/src/main/java/com/warped/WarpedApplication.kt
decisions:
  - "Raw-content delta log stripped to length-only integer (T-48-07); no per-site redaction wrappers — central tree plus deletion per locked approach"
  - "No proguard changes needed: all Phase 47 skill/ToolSet classes already covered by data.skills/domain.skills keeps; mapper classes are plain objects with no reflective access"
  - "Network posture unchanged: base-config cleartext stays with ENDPT-06 LAN rationale (T-48-10 accept)"
  - "Keystore posture unchanged: EncryptedSharedPreferences + AES256_GCM MasterKey + CharArray zeroing intact (T-48-11)"
metrics:
  duration: ~15m (mostly Gradle: 1 assembleRelease + audit + release-graph dump)
  completed: 2026-09-28
  tasks: 2/2 auto complete, 1/1 checkpoint returned (blocking-human, no hardware)
---

# Phase 48 Plan 03: Release Hardening Summary

HARD-01 release sweep done as far as automation can take it: the one known raw-content log leak (LiteRTLmProvider.kt:345 `content.takeLast(100)`) is stripped to a length-only integer, the log-secret gate is clean with every hit dispositioned, R8 full mode is explicitly confirmed (`android.enableR8.fullMode=true` + AGP 9.3.0) with `assembleRelease` green and all ToolSet/@Tool/skills keeps verified present, the dependency audit is green over the final graph (profileinstaller 1.4.1 stable transitives included, litertlm 0.17.1), and network + Keystore postures are re-audited unchanged with file:line evidence. The release device smoke (Task 3) cannot run here — no hardware — and is returned as a blocking checkpoint with exact steps plus the deferred Phase 45/46/47 checklists.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | Strip raw-content logging + log-secret gate | 163ac9c | LiteRTLmProvider.kt |
| 2 | R8 re-verify + dependency/network/Keystore re-audits | (no commit — verification-only, all files already correct) | — |
| 3 | Release device smoke | CHECKPOINT (blocking-human, no hardware on build machine) | — |

## Task 1 — Log-secret gate (T-48-07, T-48-08)

**Fix:** `LiteRTLmProvider.kt:345`
`Timber.d("LiteRTLmProvider: delta (${content.length} chars): %s", content.takeLast(100))`
→ `Timber.d("LiteRTLmProvider: delta (%d chars)", content.length)` — length-only integer, zero payload.

**Gate results:**
- `grep -n "takeLast(100)" LiteRTLmProvider.kt` → **LEAK-REMOVED**
- Secret-pattern grep over all `Timber.[dew]` lines → exactly **one** hit, dispositioned SAFE:
  - `InputSanitizer.kt:31` — `sanitized input (${input.length} -> ${result.length} chars)`: interpolates only `.length` integers, never content. Length-only shape log, no payload.
- Verified-safe lines re-confirmed untouched: `:224` request-shape counts (images/audio/text booleans only), `LocalToolExecutor.kt:76` id-only (`%s descriptor.id`), `:42`/`:50` failure shapes (exception class name + static rejection string, no args), `LmStudioToolLoop` status-shape logs (`:358`/`:409` static "malformed tool args — content fallback", never interpolates `argsJson`), `ChatViewModel.kt:429` booleans / `:1095` clean/reasoning lengths / `:1179` tier+numbers telemetry.
- Structural backstop confirmed: `WarpedApplication.kt:40-41` — `Timber.plant(RedactingTree())` inside `if (BuildConfig.DEBUG)`; release plants no tree (Timber no-op). `RedactingTree` (`:99-108`) redacts `api_key/secret/token/authorization` + `Bearer` patterns as defense-in-depth.
- Must-have artifact string `"LiteRTLmProvider: sending"` still present (`:213` audio-bytes shape + `:224` request-shape lines).

## Task 2 — R8 + audits (T-48-09, T-48-10, T-48-11)

**(a) R8 full mode — CONFIRMED:**
- `gradle.properties:10` — `android.enableR8.fullMode=true` (explicit, not inferred).
- AGP `9.3.0` (`gradle/libs.versions.toml:3`; full mode is default since AGP 8, flag removes doubt).
- `app/build.gradle.kts:59-63` — release `isMinifyEnabled=true`, `isShrinkResources=true`, `proguard-android-optimize.txt` + `proguard-rules.pro`.
- `:app:assembleRelease` — **BUILD SUCCESSFUL** (56 tasks, 14 executed).
- Keeps verified present: LiteRT 0.17.x `ToolSet`/`OpenApiTool`/`@Tool`/`@ToolParam`/`ReflectionTool`/`ToolKt`/`Capabilities` explicit keeps (`proguard-rules.pro:30-36`) under the broad `litertlm.**` keeps (`:17-24`); skills keeps `com.warped.domain.skills.**` + `com.warped.data.skills.**` (`:84-85`).
- Skills audit: all `@Tool` ToolSets (`CalculatorToolSet`, `CurrentTimeToolSet`, `JsonFormatterToolSet` in `data/skills/`) covered by BOTH the litertlm `ToolSet` keep and the `data.skills.**` keep. `LmStudioToolLoop` (`data/remote/provider/`) needs no keep (direct Hilt instantiation, no reflection). Mapper classes (`TypeMapper` highlighting, `EntityMappers` db) are plain `object`/top-level functions with direct calls, no `@Serializable`, no reflective access — **no keeps added, none needed**.

**(b) Dependency audit — GREEN:** `bash scripts/audit-dependencies.sh` → `OK: no banned direct dependencies, no pre-release artifacts in release graph`. Release-graph dump confirms `profileinstaller:1.4.1` stable (1.3.0/1.4.0 transitives resolve up) and `litertlm-android:0.17.1`; whole-graph pre-release regex (`SNAPSHOT|alpha|beta|rc|cr|-m`) clean.

**(c) Network re-audit — UNCHANGED, rationale intact:** `network_security_config.xml` base-config `cleartextTrafficPermitted="true"` still carries the full ENDPT-06 comment (`:3-13`: LM Studio is the sole v1.8 remote provider, LAN-only HTTP, CIDR allowlist impossible in the XML format). No new remote endpoint type since last audit — no pinning needed (T-48-10 accept stands).

**(d) Keystore re-audit — UNCHANGED, no API change:** `KeystoreManager.kt` — `MasterKey AES256_GCM` + `EncryptedSharedPreferences` (`AES256_SIV` keys / `AES256_GCM` values, `warped_secure_prefs`). `ApiKeyStore.kt:17,19,38,40` — `CharArray.fill('0')` + `bytes.fill(0)` zeroing on both endpoint keys and HF token. `ProviderRouter.kt:33` — `String(key).also { key.fill('0') }` zero-on-read intact (T-48-11 mitigate stands).

## Deviations from Plan

None - plan executed exactly as written. Task 2 required zero file edits (all keeps, config, and crypto already correct) — verification-only, so no commit exists for it. This is intentional, not a gap.

## Known Stubs

None introduced. The checked-in `baseline-prof.txt` seed-profile TODO (full method profile on proper hardware) belongs to 48-02 and is unchanged.

## Threat Flags

None — no new network endpoints, auth paths, file access, or schema changes. All touched surface was already in the plan threat model (T-48-07–T-48-11 dispositioned above).

## Deferred Verifications (for the Task 3 checkpoint)

Per CONTEXT, these land on hardware with the release smoke: **Phase 45** device smoke (install, cold start, local chat round-trip); **Phase 46** stop-behavior (mid-stream stop terminates, never retries/resurrects) + rotation (no stream loss, no duplicate send); **Phase 47** airplane-mode calculator (local tool executes offline, tool row renders per 47-UI-SPEC) + LM Studio loop (remote tool round-trip against LAN host, round cap respected).

## Self-Check: PASSED

- `LiteRTLmProvider.kt` length-only line present, `takeLast(100)` absent — FOUND
- Commit `163ac9c` in log — FOUND
- `proguard-rules.pro` ToolSet/skills keeps — FOUND (`:30-36`, `:84-85`)
- `audit-dependencies.sh` contains `RUNTIME-12` — FOUND (`:4`)
- `assembleRelease` BUILD SUCCESSFUL, audit script OK — both observed this session
