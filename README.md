# Warped

Warped is an open-source Android app equivalent to LM Studio for mobile: run large language models locally on-device and connect to remote LLM providers — all from a single chat interface.

**Core value:** run and chat with any LLM — local or remote — with an LM Studio-grade experience that works offline.

> **Versioning:** the app version (`versionName` in `app/build.gradle.kts`) tracks the planning milestone version (see `.planning/MILESTONES.md`). Current release: **2.4.0**.

## Features

- **Local inference** — LiteRT-LM engine (`.litertlm` / `.task`), GPU/CPU/NPU backends with automatic fallback, vision + audio multimodal models, thinking mode, spec-decode opt-in, memory-aware smart presets
- **Model catalog** — static allowlist (`model_allowlist.json`) with direct downloads (progress / pause / resume / cancel), capability badges (vision, audio, thinking), per-model details (RAM guidance, description)
- **Remote endpoints** — OpenAI-compatible, Anthropic, Ollama, LM Studio, custom servers; endpoint CRUD, model listing, traffic-light status, per-endpoint encrypted API keys (Android Keystore)
- **Agentic web grounding** — model-invoked `web_search` and `web_fetch` tools, local (LiteRT-LM function calling) and remote (native `tools[]` loop); fused multi-page context with numbered citations, per-source progress, offline retry, per-chat toggle
- **Sources UX** — OpenGraph thumbnail cards, horizontal carousel, view-all drawer, per-source preview bottom sheet, browser open, image search grid with gallery download
- **Chat** — streaming responses, Markdown + syntax-highlighted code blocks (4 themes), conversation history (Room), generation presets (Preset / Custom per model), Prompt Lab, benchmarks
- **Security** — API keys encrypted via Android Keystore (never plaintext, never logged); prompt-injection sanitizer on all fetched/tool content; stripped fetch clients (no credential leaks); R8/ProGuard release builds

## Run

### Install the beta (Open Testing)

Once enrolled in Open Testing (link in [Releases](#releases)), install from Google Play. Beta builds come from the `beta` branch; production builds from `main` / `production`.

### Build from source

Requirements: Android Studio (or SDK with platform 36, build-tools 36, NDK 27.x), JDK 21. A physical device or emulator for instrumentation tests and visual confirmation; unit tests run on JVM.

```bash
git clone https://github.com/cotizcesar/warped.git
cd warped

# Debug APK (fast iteration, installs from app/build/outputs/apk/debug/)
./gradlew :app:assembleDebug

# Unit tests, fast subset (skips loopback-socket/timeout tests)
./gradlew :app:testDebugUnitTest -Pfast

# Unit tests, full suite
./gradlew :app:testDebugUnitTest

# Lint
./gradlew :app:lintDebug

# Signed release bundle (needs signing keys in local.properties — NEVER committed)
./gradlew :app:bundleRelease
```

Release signing reads `local.properties` (gitignored). CI injects it from GitHub Secrets and uploads to Google Play.

| Property | Meaning |
|---|---|
| `RELEASE_STORE_FILE` | Path to `warped-release.jks` (PKCS12; key password = store password) |
| `RELEASE_STORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Key alias |
| `RELEASE_KEY_PASSWORD` | Same as store password (PKCS12 limitation) |

## Project structure

```
app/src/main/
├── java/com/warped/
│   ├── di/                  # Hilt modules (per domain)
│   ├── domain/              # Pure Kotlin: models, repository interfaces, LlmProvider
│   ├── data/
│   │   ├── grounding/       # Web fetch, extraction (HTML→Markdown), budgets, sanitizer, search
│   │   ├── agentic/         # Local tool loop (web_search/web_fetch ToolSets, policy)
│   │   ├── local/           # Room DB + migrations, download manager (WorkManager), inference engines, Keystore
│   │   └── remote/          # Retrofit APIs, DTOs, SSE, provider implementations (OpenAI/Anthropic/Ollama/LMStudio/custom)
│   └── ui/                  # Compose screens (chat, models, catalog, endpoints, settings, presets…) + theme
├── assets/model_allowlist.json  # Static model catalog (repo slugs, sizes, capability flags)
└── res/values{,-es}/         # Strings, English default + full Spanish parity (system locale)
```

Architecture: clean MVVM + repository pattern (domain ← data, ui → domain), Hilt DI, Room + DataStore, WorkManager, Kotlin coroutines/Flow. Conventions live in `AGENTS.md`.

## Branches & releases

| Branch | Deploys to | Receives |
|---|---|---|
| `beta` | Google Play **Open Testing** | Feature PRs |
| `main` | Google Play **Production** | Promotion PRs from `beta` |

Flow: `feature/*` → PR into `beta` (gates must pass) → validate in Open Testing → PR `beta` → `main` (gates must pass) → Production release. Direct pushes to `beta` / `main` are blocked; everything lands via PR.

## CI / CD

- **CI** (push + PR on `main` / `beta` / `production`): `build` job (lint → unit tests → debug APK → release APK + 16KB alignment) and `security` job (dependency audit). Both are required status checks — a red gate blocks the merge.
- **Release** (push on `beta` / `main`): signed AAB → Google Play track matching the branch (`beta` → `beta`/Open Testing track, `main` → `production` track). Requires secrets: `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, `GCP_SERVICE_ACCOUNT`.
- Test speed: `tasks.withType<Test>` runs classes in parallel forks; `./gradlew :app:testDebugUnitTest -Pfast` skips `@Tag("slow")` loopback-socket/timeout tests for a sub-minute local loop.

## Security model

- No secrets in code or committed files (`local.properties` with signing keys is gitignored; verified untracked).
- Remote API keys live in EncryptedSharedPreferences backed by Android Keystore.
- Fetched/tool content is untrusted input: hijack-pattern filtering, delimiter escaping, `http(s)`-only links, per-call auth, offline-first with no silent sockets.
- Dependency audit (`./gradlew auditDependencies`) runs as a required CI gate; fail-closed on banned deps (kapt, Firebase, Moshi/Gson, Ktor, TFLite, ML Kit, AppAuth, cameraX, datastore-proto).

## Community and support

- Bug reports and feature requests: [GitHub Issues](https://github.com/cotizcesar/warped/issues).
- Security vulnerabilities: **do not open a public issue** — email the maintainer privately (see profile) with details and reproduction steps.

## Contributing

Contributions are welcome via pull request. The bar is: small, tested, documented.

1. **Fork** the repo and create a feature branch from `beta` (`git checkout -b feature/short-name beta`).
2. **Build & verify** before pushing:
   ```bash
   ./gradlew :app:assembleDebug
   ./gradlew :app:testDebugUnitTest -Pfast   # full suite for risky areas
   ./gradlew :app:lintDebug
   ./gradlew auditDependencies
   ```
3. **Conventions** (enforced in review):
   - Kotlin only, no Java; MVVM + repository pattern; Hilt DI; coroutines/Flow (no LiveData).
   - No secrets in code — API keys only via Android Keystore; no `http` hardcoding outside the LAN-server paths.
   - Strings in `values/strings.xml` **plus** Spanish parity in `values-es/strings.xml`.
   - Never block the Main thread (inference, downloads, and model loads stay off-UI; StrictMode is on in debug).
   - Unit tests for new logic (JUnit 5 + MockK + Truth + Turbine); tag loopback-socket/timeout tests `@Tag("slow")`.
4. **Open a PR against `beta`** (never directly against `main`). Fill in what/why/how-tested. All status checks (build + security) must pass; PRs need one review before merge.
5. **Promotion to production** happens via a `beta` → `main` PR after Open Testing validation.

By contributing you agree your work is licensed under the repository's license.

## Development

Start with `AGENTS.md` (stack, workflow, conventions) and `.planning/` (milestones, phases). For agents, `AGENTS.md` is the contract.

## License

All rights reserved unless a `LICENSE` file states otherwise.
