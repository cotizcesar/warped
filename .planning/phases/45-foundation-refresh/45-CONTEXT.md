# Phase 45: Foundation Refresh - Context

**Gathered:** 2026-09-27
**Status:** Ready for planning

<domain>
## Phase Boundary

Full dependency catalog to latest stable + LiteRT-LM 0.13.1 → 0.17.1 with EngineConfig/ConversationConfig surface, R8 rules, and cache schema re-verified. Foundation for Phases 46–48 — every later phase builds on the final APIs.
Requirements: DEPS-01, DEPS-02, LRT-07, LRT-09.
</domain>

<decisions>
## Implementation Decisions

### Version Bump Scope
- Bump every catalog entry to latest stable where build stays green, incl. LiteRT-LM 0.17.1 (user accepted)
- LiteRT-LM target pinned at 0.17.1 stable (2026-09-16), verified against Maven Central
- No SNAPSHOT or -alpha artifacts in release graph; audit fails on hit (RUNTIME-12 stays green)

### Engine Migration Depth
- Full re-verification of EngineConfig/ConversationConfig API surface against 0.17.x
- Version-namespaced mmap cache dir (BuildConfig.LITERTLM_VERSION), verify no silent schema drift
- model_allowlist.json capability flags reflect only features actually verified on 0.17.x Android

### Breaking-Change & Verification
- Review release notes per bump (esp. Room migrations, Hilt/AGP, Navigation, OkHttp/Retrofit); migrate, preserve chat history/presets/endpoints
- Full unit-test suite green + dependency audit empty after refresh
- Manual smoke: load downloaded .litertlm + streaming chat, no UnsatisfiedLinkError/serializer errors

### the agent's Discretion
- Per-library bump ordering and exact stable versions at planner discretion (verify against Maven Central / release notes)
- Zero new dependencies — all work rides refreshed catalog
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- gradle/libs.versions.toml — single catalog (kotlin 2.3.20, agp 9.2.1, litertlm 0.13.1, compose-bom 2026.05.01, hilt 2.59.2, room 2.8.4, lifecycle 2.10.0, nav 2.9.0, okhttp 4.12.0, retrofit 3.0.0, serialization 1.7.3, coroutines 1.9.0, datastore 1.2.1, workmanager 2.10.0)
- EngineManager (mmap cache namespaced by BuildConfig.LITERTLM_VERSION), ModelAllowlistRepository + assets/model_allowlist.json
- R8 keep rules for com.google.ai.edge.litertlm.** JNI, MessageCallback, ToolProvider, kotlinx-serialization companions

### Established Patterns
- Version catalog refs (version.ref) + KSP for Room/Hilt/serialization; Gradle Kotlin DSL
- LiteRT-LM via Maven AAR only (no C prebuilts, no YNNPACK); largeHeap=true, extractNativeLibs=false
- Dependency audit script pattern (RUNTIME-12 anti-pattern list)

### Integration Points
- app/build.gradle.kts (catalog refs), gradle.properties (RClass, JVM args), proguard-rules.pro, assets/model_allowlist.json, EngineManager cache path
</code_context>

<specifics>
## Specific Ideas

- LiteRT-LM 0.17.1 is the target (supersedes research SUMMARY's 0.13.1 pin); adopt only stable APIs
- No specific UI requirements — infrastructure/foundation phase
</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope
</deferred>
