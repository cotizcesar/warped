# Warped

Warped is an Android app equivalent to LM Studio for mobile: run large language models locally on-device and connect to remote LLM providers — all from a single chat interface. It ships a curated static model catalog with direct downloads, executes models on-device via LiteRT-LM, connects to OpenAI-compatible APIs, Anthropic, Ollama, LM Studio, and custom servers, and grounds answers with agentic web search (Tavily), multi-page fetch, and OpenGraph source cards.

**Core value:** run and chat with any LLM — local or remote — with an LM Studio-grade experience that works offline.

> **Versioning:** the app version (`versionName` in `app/build.gradle.kts`) tracks the planning milestone version (see `.planning/MILESTONES.md`). Current release: **2.4.0**.

## Features

- **Local inference** — LiteRT-LM engine (`.litertlm` / `.task`), GPU/CPU/NPU backends with automatic fallback, vision + audio multimodal models, thinking mode, spec-decode opt-in, memory-aware smart presets
- **Model catalog** — static allowlist (`model_allowlist.json`) with direct downloads (progress / pause / resume / cancel), capability badges (vision, audio, thinking), per-model details (RAM guidance, description)
- **Remote endpoints** — OpenAI-compatible, Anthropic, Ollama, LM Studio, custom servers; endpoint CRUD, model listing, traffic-light status, per-endpoint encrypted API keys (Android Keystore)
- **Agentic web grounding** — model-invoked `web_search` (Tavily, keyless DuckDuckGo default) and `web_fetch` tools, local (LiteRT-LM function calling) and remote (native `tools[]` loop); fused multi-page context with numbered citations, per-source progress, offline retry, per-chat toggle
- **Sources UX** — OpenGraph thumbnail cards, horizontal carousel, view-all drawer, per-source preview bottom sheet, browser open, image search grid with gallery download
- **Chat** — streaming responses, Markdown + syntax-highlighted code blocks (4 themes), conversation history (Room), generation presets, Prompt Lab, benchmarks
- **Security** — API keys encrypted via Android Keystore (never plaintext, never logged); prompt-injection sanitizer on all fetched/tool content; stripped fetch clients (no credential leaks); R8/ProGuard release builds

## Requirements

- Android Studio (or SDK with platform 36, build-tools 36, NDK 27.x)
- JDK 21
- A physical device or emulator for instrumentation tests and visual confirmation (no `adb` in some CI-less environments; unit tests run on JVM)

## Build

```bash
# Debug APK (fast iteration, installs from app/build/outputs/apk/debug/)
./gradlew :app:assembleDebug

# Unit tests (JVM)
./gradlew :app:testDebugUnitTest

# Lint
./gradlew :app:lintDebug

# Signed release bundle (needs signing keys in local.properties — NEVER committed)
./gradlew :app:bundleRelease
```

Release signing reads `local.properties` (gitignored — see `local.properties.example` if present, else the keys below). CI injects them from GitHub Secrets and uploads to Google Play.

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
│   │   ├── grounding/       # Web fetch, extraction (HTML→Markdown), budgets, sanitizer, search (Tavily/DDG)
│   │   ├── agentic/         # Local tool loop (web_search/web_fetch ToolSets, policy)
│   │   ├── local/           # Room DB + migrations, download manager (WorkManager), inference engines, Keystore
│   │   └── remote/          # Retrofit APIs, DTOs, SSE, provider implementations (OpenAI/Anthropic/Ollama/LMStudio/custom)
│   └── ui/                  # Compose screens (chat, models, catalog, endpoints, settings, presets…) + theme
├── assets/model_allowlist.json  # Static model catalog (repo slugs, sizes, capability flags)
└── res/values{,-es}/         # Strings, English default + full Spanish parity (system locale)
```

Architecture: clean MVVM + repository pattern (domain ← data, ui → domain), Hilt DI, Room + DataStore, WorkManager, Kotlin coroutines/Flow. Conventions live in `AGENTS.md`.

## CI / CD

- `CI` workflow (push + PR on `beta`/`production`): lint → unit tests → debug APK.
- `Release` workflow (push on `beta`/`production`): signed AAB → Google Play track matching the branch (`beta` → beta track, `production` → production track). Requires secrets: `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, `GCP_SERVICE_ACCOUNT`.
- Branching: feature work lands on `beta` via PR; `production` merges only after validation. (Branch protection rules require GitHub Pro or a public repo on private projects — apply when either holds.)

## Security model

- No secrets in code or committed files (`local.properties` with signing keys is gitignored; verified untracked).
- Remote API keys + Tavily key live in EncryptedSharedPreferences backed by Android Keystore.
- Fetched/tool content is untrusted input: hijack-pattern filtering, delimiter escaping, `http(s)`-only links, per-call auth, offline-first with no silent sockets.

## License

All rights reserved unless a `LICENSE` file states otherwise.
