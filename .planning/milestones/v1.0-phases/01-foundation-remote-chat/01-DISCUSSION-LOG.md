# Phase 1: Foundation & Remote Chat - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-30
**Phase:** 1-foundation-remote-chat
**Mode:** Auto (YOLO) — all decisions auto-selected with recommended defaults
**Areas discussed:** Project structure, Navigation, Domain model, SSE streaming, Room schema, Security, Error handling, Build order

---

## Project Structure

| Option | Description | Selected |
|--------|-------------|----------|
| Single module | Start with one `app/` module, split later (YAGNI) | ✓ |
| Multi-module | Separate `:core:common`, `:feature:chat`, `:feature:models`, etc. from start | |

**Auto-selected:** Single module — recommended for coarse granularity. Split when Phase 2 adds native module.

---

## Navigation

| Option | Description | Selected |
|--------|-------------|----------|
| Bottom navigation bar | 3 tabs (Chat, Endpoints, Models), Settings via gear icon | ✓ |
| Navigation drawer | Side drawer with all screens listed | |
| Simple backstack | Single active screen, switch via top-level selector | |

**Auto-selected:** Bottom navigation — standard Android pattern, matches LM Studio's tab-like layout.

---

## SSE Streaming

| Option | Description | Selected |
|--------|-------------|----------|
| Manual OkHttp parsing | Custom `flow {}` builder reading chunks from BufferedSource | ✓ |
| Retrofit @Streaming | Retrofit's ResponseBody with declarative API | |
| Library-based (okhttp-sse) | OkHttp's EventSource API | |

**Auto-selected:** Manual parsing — most control over backpressure, error handling, and provider quirks. Consistent with research PITFALLS.md P2 recommendation (line accumulator buffer).

---

## Room Schema

| Option | Description | Selected |
|--------|-------------|----------|
| Exact user-specified entities | `ConversationEntity`, `MessageEntity`, `RemoteEndpointEntity` only | ✓ |
| Extended schema | Additional metadata columns, FTS4 indices, soft-delete flags | |

**Auto-selected:** Exact user-specified entities — MVP scope. Extend in later phases.

---

## Security

| Option | Description | Selected |
|--------|-------------|----------|
| EncryptedSharedPreferences only | AES-256 encryption via Android Keystore | ✓ |
| Custom Keystore wrapper | Direct Keystore API with custom encryption layer | |
| Fingerprint/biometric unlock | Require biometric before decrypting API keys | |

**Auto-selected:** EncryptedSharedPreferences — recommended by Android Security docs, sufficient for MVP, no biometric complexity.

---

## Build Order

| Option | Description | Selected |
|--------|-------------|----------|
| Infrastructure-first | Hilt → Room → Navigation → then features | ✓ |
| Feature-first | Start with OpenAI provider + minimal ChatScreen, add infrastructure later | |
| Vertical slice | One endpoint type end-to-end, then repeat for each | |

**Auto-selected:** Infrastructure-first — domain layer and provider interface need to exist before any feature. Research ARCHITECTURE.md supports this: foundation enables all features.

---

## Claude's Discretion

- Theme setup: Material 3 with dark mode, single-activity, `largeHeap=true`, `extractNativeLibs=false`
- Compose component tree: model selector top, conversation center (`LazyColumn`), input bar bottom with send/stop
- SSE Content-Type relaxed parsing: some providers don't set correct MIME type
- Timber logging tree with APK key/sensitive content redaction

## Deferred Ideas

- Multi-module Gradle structure → Phase 2 (native module arrival)
- Mermaid architecture diagrams → documentation phase, post-Phase 5
- Token-completion feedback (sound/vibration) → deferred, visual streaming only
