---
phase: 70-document-reader-tool
plan: 02
subsystem: chat-ux
tags: [document-reader, remote-tools, chat-ux, fuentes, saf]
requires: [70-01-tracer]
provides: [remote-read_text_file-mapping, document-attach-ux, document-fuentes-card]
affects: []
tech-stack:
  added: []
  patterns: [turn-bound-documentBlock, doc-url-convention, snackbar-notices]
key-files:
  created:
    - app/src/test/java/com/warped/data/remote/DocumentRemoteToolsTest.kt
  modified:
    - app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt
    - app/src/main/java/com/warped/data/remote/dto/AnthropicDtos.kt
    - app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt
    - app/src/main/java/com/warped/data/remote/provider/AnthropicProvider.kt
    - app/src/main/java/com/warped/data/remote/provider/CompatToolLoop.kt
    - app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt
    - app/src/main/java/com/warped/domain/model/ChatRequest.kt
    - app/src/main/java/com/warped/domain/model/GroundedSource.kt
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
    - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
    - app/src/main/java/com/warped/ui/chat/components/OgSourceCard.kt
    - app/src/main/java/com/warped/ui/chat/components/SourcePreviewSheet.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-es/strings.xml
decisions:
  - "ChatRequest.documentBlock threads the turn-bound fused block to all four loop drivers (single source; LiteRT binds/clears per armed turn, remotes pass as executor param)"
  - "Document Fuentes row owned by the VM (groundedSourceDetails union); executors return no sources (no double-emit)"
  - "extractedText stores the sanitized bounded text (web-row precedent); chip marker + notices carry the truncation bounds"
  - "Seven doc_reader_* keys (plan said five; copy contract needs attach+replace+remove+truncated+unsupported+failed+chip-marker), EN+ES parity"
  - "TOOL-03 stays UNBUILT: FIT verdict recorded as a code comment citing 70-RESEARCH.md Q2"
metrics:
  duration: "~3h"
  completed: "2026-10-02"
---

# Phase 70 Plan 02: Remote Mapping + Chat UX Summary

Same pick → ground → answer UX on local + remote: `read_text_file` rides the
existing `tools[]` attempt-then-fallback on OpenAI/compat plus the Anthropic
native mapping; the chat input gains the attach affordance + chip; grounded
turns render the document in Fuentes; TOOL-03 stays unbuilt per the FIT verdict.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | Remote tools[] mapping (OpenAI/compat + Anthropic) + tests | 5f6018bc | OpenAiChatRequest, AnthropicDtos, 3 executors, ChatRequest, LiteRT bind, DocumentRemoteToolsTest |
| 2 | VM attachment state + SAF bounded read + turn fusion | 3f0026c7 | ChatViewModel |
| 3 | Attach button + chip + Fuentes card + doc_reader_* strings (EN+ES) | 08bd5f01 | ChatInputBar, ChatScreen, GroundedSource, OgSourceCard, SourcePreviewSheet, strings EN+ES |

## What Was Built

- **Remote mapping**: third `tools[]` entry (`read_text_file` + filename schema)
  in `defaultRemoteTools` and `defaultAnthropicTools`, reusing the Plan 01
  description constants verbatim. Compat inherits via shared `defaultRemoteTools`
  (no per-file edit). All three executors validate via `LocalToolLoop.validateArgs`,
  re-feed `request.documentBlock` verbatim via `mapDocumentResult` on
  `Dispatchers.IO`, degrade via `DOCUMENT_READ_FAILED_STRING`, never throw, no
  internet gate (content already in hand). Server rejection of the 3-tool list
  flows through the unchanged `isToolsRejection` + exactly-one-retry path.
- **VM attachment**: `AttachedDocument` (uri/filename/sizeBytes/block/text/
  truncatedAt/status) + `AttachStatus` + `attachedDocument` StateFlow.
  `attachDocument` queries OpenableColumns, gates mime-before-read, reads at
  most cap+1 bytes on Dispatchers.IO, decodes UTF-8 REPLACE, sanitizes +
  builds the block; unsupported fires the pick-time notice; new pick replaces.
  `sendMessage` fuses the same string for local + remote, registers the
  `doc:{filename}` Fuentes row, binds `ChatRequest.documentBlock`, clears the
  consumed instance by ref-equality after send. Doc-only sends allowed.
- **UX surfaces**: 40dp AttachFile button in the input icon row (inputLocked-
  gated, attach/replace descriptions, filename stateDescription); chip above
  the input (Description icon, ellipsis filename, `· 842 KB · showing-first-N`
  marker, 48dp remove-X, single tap); document card reuses Fuentes chrome
  with filename label + `truncated at N chars` snippet metadata; tap opens
  SourcePreviewSheet on the bounded text (browser button hidden for doc rows).
- **Strings**: seven `doc_reader_*` keys, identical sets EN+ES, zero hardcoded
  user copy (verified by grep).

## Verification

- `./gradlew :app:testDebugUnitTest --tests "com.warped.data.remote.DocumentRemoteToolsTest"` — BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest` (FULL suite) — BUILD SUCCESSFUL, 0 failures
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
- `doc_reader_*` keys: 7 EN + 7 ES, identical sets
- Hardcoded-copy grep: 0 matches; converter grep: 0 matches
- Zero new Gradle dependencies; resolver I/O on Dispatchers.IO; Kotlin only

## Deviations from Plan

### Auto-fixed Issues

None — plan executed as written, plus the minor adjustments below.

### Minor Adjustments (no behavior drift)

1. **String-key count 5 → 7.** The plan says "five" but its own copy table
   lists six (attach/replace/remove/truncated/unsupported/failed). A seventh
   (`doc_reader_showing_first`) was added for the chip's inline marker so no
   English fragment is hardcoded and ES word order stays natural. EN+ES
   parity holds for all seven.
2. **AttachedDocument gains a `text` field** (sanitized bounded body for the
   Fuentes preview) alongside the plan's `block` — deriving preview text from
   the enveloped block would be fragile string surgery.
3. **TalkBack stateDescription announces the filename alone** (no
   "Document attached:" prefix) to avoid an eighth string; the
   attach→replace description swap already announces state change.
4. **Preview-sheet browser button hidden for doc rows** (dead button would
   only toast via the already-safe scheme gate).

## Decisions Made

- Turn-block threading via `ChatRequest.documentBlock` (default null — all
  existing constructions compile untouched).
- VM owns the document Fuentes row; executors emit no sources for document
  calls (union dedupes anyway, but silence is cleaner).
- `ogHostOf` strips the `doc:` prefix → filename labels everywhere
  (card, sheet, drawer) with zero new chrome.

## Known Stubs

None.

## Threat Flags

None — T-70-05..07 mitigations implemented as planned (picker-only,
display-only filename, visible chip + one-tap remove before send; inherits
Plan 01 T-70-01..04).

## Self-Check: PASSED

- All created/modified files exist; commits 5f6018bc + 3f0026c7 + 08bd5f01
  in `git log`; no unintended deletions.
