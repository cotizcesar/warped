# Phase 66 — Play In-App Review: Security Audit

**Phase:** 66 — Play In-App Review
**Verdict:** SECURED
**Threats Closed:** 4/4
**ASVS Level:** 1 (client-side ambient prompt + external Store intent; no auth, no sensitive data, no server surface)
**Audited:** 2026-10-02
**Artifacts audited:** `66-01-PLAN.md` (threat model), `66-01-SUMMARY.md`, implementation files listed below

## Threat Verification

| Threat ID | Category | Disposition | Evidence | Status |
|-----------|----------|-------------|----------|--------|
| T-66-01 | Tampering (review-ktx dependency) | mitigate | `gradle/libs.versions.toml:26` (`play-review = "2.0.2"`), `:141` (`com.google.android.play:review-ktx` version.ref pin); `app/build.gradle.kts:238` (`implementation(libs.play.review.ktx)`); ban list at `app/build.gradle.kts:303-310` covers kapt/firebase/moshi/gson/kotlin-reflect/ktor/mcp/tflite/mlkit-genai/appauth/compose-richtext/cameraX/datastore-proto — `com.google.android.play` not listed; SUMMARY reports `auditDependencies` passes | CLOSED |
| T-66-02 | Information Disclosure (ReviewPreferences turn counts) | accept | `app/src/main/java/com/warped/data/local/preferences/ReviewPreferences.kt:16,47-51` — app-private `review_preferences` DataStore, keys are `completed_turns` (int), `last_prompt_millis` (long), `prompt_count` (int) only; no PII, no chat content, no keys/secrets — see Accepted Risks log below | CLOSED (accepted risk) |
| T-66-03 | Spoofing (market:// intent) | mitigate | `app/src/main/java/com/warped/ui/chat/components/BrowserIntents.kt:75-90` — `openPlayStoreListing` is a separate top-level fun (never routed through `openUrlInBrowser` http/https allowlist); URI built from `context.packageName` only (line 76, zero user input); `market://details?id=` first (line 89) + `https://play.google.com/store/apps/details?id=` fallback (line 90); dual `ActivityNotFoundException` (84) + `SecurityException` (86) catches per attempt; `FLAG_ACTIVITY_NEW_TASK` for non-Activity callers (80) | CLOSED |
| T-66-SC | Tampering (gradle plugin/maven resolution) | mitigate | `settings.gradle.kts:16-22` — `FAIL_ON_PROJECT_REPOS` with `google()` + `mavenCentral()` only, no new repos added by this phase; catalog diff adds only `com.google.android.play:review-ktx:2.0.2` (first-party Google); SUMMARY documents Google-Maven `maven-metadata.xml` verification (2.0.2 = latest/release, 2026-10-02) | CLOSED |

## Supplemental Verification (phase brief focus areas)

| Area | Finding |
|------|---------|
| Dependency integrity / transitive surprises | Only `review-ktx` added (SUMMARY + catalog grep confirm no other new catalog entry); `suspendCancellableCoroutine` bridge used instead of adding `coroutines-play-services` (SUMMARY key-decision); no transitive surprise introduced by phase code |
| Sensitive data passed to Play APIs | None. `ReviewHelper.maybePrompt(activity: Activity)` (`ReviewHelper.kt:155`) and `DefaultReviewFlowLauncher.launch(activity)` (`:105`) pass only the `Activity`; grep over `domain/review/` for `content|message|prompt text|user input|chat|PII|apiKey|token` returns only doc-comment mentions of "completed chat turns" — no chat content, key, or identifier crosses into `ReviewManagerFactory` |
| Intent safety / untrusted-URI handling | No untrusted URI handled: both URIs are packageName-derived constants; `market:` never passes through the `http/https` allowlist (separate function by design); guarded per-attempt catches fall through to https fallback, toast only when BOTH fail (`BrowserIntents.kt:91-95`) |
| New network surfaces | None. Review files import only Play Core review, DataStore, coroutines, Timber — no OkHttp/Retrofit/SSE; Play SDK calls (`requestReviewFlow`/`launchReviewFlow`) are IPC to Play services, not app-owned network |
| Spam abuse (cooldown / max caps) | `ReviewEligibility` (`ReviewHelper.kt:31-47`): `MIN_COMPLETED_TURNS=5`, `COOLDOWN_MILLIS=21d` (first-prompt exempt via `lastPromptMillis==0`), `MAX_PROMPTS=3`; `maybePrompt` runs increment+read+launch+record under `Mutex` (`:157`) so overlapping prompts serialize — no double-prompt / cooldown / cap bypass; prompt timestamp captured AFTER flow completes (`:171`, cooldown starts at show time) |
| Prompt-state tampering (DataStore writes) | Internal only: `@Singleton @Inject` with `@ApplicationContext` (`ReviewPreferences.kt:43-46`), private companion keys, `edit` writes in `incrementCompletedTurns`/`recordPrompt` only; single-snapshot `reviewState` flow prevents torn reads; hook fires only on persisted user-visible turns (`turnPersisted` gate, `ChatViewModel.kt:1155`), off the streaming path via `viewModelScope.launch(coroutineExceptionHandler)` (`:1156`) with silent `Timber.w` failures; `Activity` reaches the VM via nullable UI-set provider cleared on dispose (`ChatScreen.kt:216-218`), never stored in the VM |

## Accepted Risks Log

| Risk ID | Threat | Rationale | Expiry / Review |
|---------|--------|-----------|-----------------|
| AR-66-02 | T-66-02: turn counters / prompt timestamps in app-private DataStore | Non-sensitive counters (ints + epoch millis); app-private store, no backup/export path introduced; no PII, no chat content persists here | Re-review if ReviewPreferences ever stores identifiers, content, or cross-profile data |

## Unregistered Flags

None. `66-01-SUMMARY.md` contains no `## Threat Flags` section, and no new attack surface beyond the threat-model boundary (Play Core SDK + Play Store intent) was introduced during implementation.

## Verdict

**SECURED** — 4/4 threats verified closed (3 mitigated in code at cited lines, 1 accepted with logged rationale). No open threats. No unregistered flags.
