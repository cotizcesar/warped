# Phase 26: Security Hardening - Context

**Gathered:** 2026-05-09
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase — discuss skipped)

<domain>
## Phase Boundary

Harden the app for production release:
- ProGuard/R8 coverage: keep rules for OkHttp, Retrofit, Hilt/Dagger, Kotlin Coroutines
- Input sanitization on all providers (OpenAI, Anthropic, Ollama)
- Global crash handling with Thread.setDefaultUncaughtExceptionHandler
- Secure storage audit: fix CharArray→String, remove key alias logging
- Network security lock-down: cleartext blocked in release, LAN scoped to 192.168.x.x/10.x.x.x/localhost
- Logging protection: HttpLoggingInterceptor conditioned on BuildConfig.DEBUG
- Credential externalization: keystore passwords → local.properties
- Room encryption: SQLCipher SupportFactory, passphrase in Android Keystore
</domain>

<decisions>
## Implementation Decisions

### the agent's Discretion
All implementation choices are at the agent's discretion — infrastructure phase. The ROADMAP tasks and REQUIREMENTS.md constraints (SEC-01 through SEC-08) are the spec.

### Phase 23 context (completed)
GGUF/llama.cpp removed. ProGuard rule for llama JNI already deleted (line 17-18 in proguard-rules.pro). Existing ProGuard config is the baseline for SEC-01 additions.
</decisions>

<code_context>
## Existing Code Insights

### Key files to modify:
- `app/proguard-rules.pro` — Add keep rules for OkHttp, Retrofit, Hilt, Coroutines
- `app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt` — Input sanitization
- `app/src/main/java/com/warped/data/remote/provider/AnthropicProvider.kt` — Input sanitization
- `app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt` — Input sanitization
- `app/src/main/java/com/warped/WarpedApplication.kt` — Global crash handler
- `app/src/main/AndroidManifest.xml` — extractNativeLibs, largeHeap
- `app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt` — Secure storage audit
- `app/src/main/java/com/warped/data/local/security/KeystoreManager.kt` — Key alias logging
- `app/src/main/res/xml/network_security_config.xml` — Network security config
- `app/src/main/java/com/warped/data/remote/HttpClientFactory.kt` — Logging level
- `app/src/main/java/com/warped/di/NetworkModule.kt` — Logging level
- `app/build.gradle.kts` — Keystore passwords
- `local.properties` — Externalized credentials
- `app/src/main/java/com/warped/di/DatabaseModule.kt` — Room SQLCipher

### New files:
- `app/src/main/java/com/warped/data/local/security/InputSanitizer.kt` — may exist already
</code_context>

<specifics>
## Specific Ideas

Requirements as specified in REQUIREMENTS.md (SEC-01 through SEC-08). See .planning/REQUIREMENTS.md for detailed acceptance criteria.
</specifics>

<deferred>
## Deferred Ideas

- Certificate pinning for remote endpoints — deferred, not in v1.5 scope
- Play Integrity / root detection — deferred, not in v1.5 scope
</deferred>
