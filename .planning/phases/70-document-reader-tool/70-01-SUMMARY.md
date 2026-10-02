---
phase: 70-document-reader-tool
plan: 01
subsystem: agentic-loop
tags: [document-reader, grounding, litertlm, tool-loop]
requires: []
provides: [DocumentReader-policy, DocumentPrompt-fusion, read_text_file-local-loop]
affects: [70-02-remote-mapping, 70-02-chat-ux]
tech-stack:
  added: []
  patterns: [schema-only-ToolSet, verbatim-outcome-mapping, perPageBudget-cap-reuse]
key-files:
  created:
    - app/src/main/java/com/warped/data/grounding/DocumentReader.kt
    - app/src/main/java/com/warped/data/grounding/DocumentPrompt.kt
    - app/src/main/java/com/warped/data/agentic/ReadTextToolSet.kt
    - app/src/test/java/com/warped/data/grounding/DocumentPromptTest.kt
    - app/src/test/java/com/warped/data/agentic/DocumentToolTest.kt
  modified:
    - app/src/main/java/com/warped/data/agentic/LocalToolLoop.kt
    - app/src/main/java/com/warped/data/agentic/WebFetchToolSet.kt
    - app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt
    - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
decisions:
  - "Turn-bound block contract: LiteRTLmProvider.attachedDocumentBlock (volatile var, Plan 02 threads the VM-fused block per turn; null degrades via DOCUMENT_READ_FAILED_STRING)"
  - "read_text_file skips the internet gate (local file read needs no socket)"
  - "Blank-filename validation degrades via MODEL_ONLY_STRING (plan-literal)"
metrics:
  duration: "~25 min"
  completed: "2026-10-02"
---

# Phase 70 Plan 01: Document-Reader Tracer Summary

Local-agnostic bounded text policy + `[DOCUMENT CONTEXT]` fusion + `read_text_file`
wired through the existing local agentic loop as the third allowlisted tool,
fully unit-tested.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | DocumentReader + DocumentPrompt pure helpers + unit tests | 8a6c9a98 | DocumentReader.kt, DocumentPrompt.kt, DocumentPromptTest.kt |
| 2 | read_text_file schema + LocalToolLoop branches + LiteRT wiring + tests | f7e111a2 | ReadTextToolSet.kt, LocalToolLoop.kt, LiteRTLmProvider.kt, DocumentToolTest.kt |

## What Was Built

- **DocumentReader** (pure JVM object): `gate(mime, filename)` (text family in,
  pdf/docx/octet-stream out, `.txt`/`.md` fallback when mime null),
  `bound(text, cap)` (exact-cap truncation, marker N == cap),
  `capFor(contextSize)` delegating to `GroundingBudget.perPageBudget(contextSize, 1)`.
- **DocumentPrompt** (pure JVM object): `buildBlock` emits
  `--- Document: {filename} ---` / text / `--- End of document ---` with
  ` (truncated at N chars)` appended only when truncated; `sanitize` strips
  hijack lines (WebContextSanitizer patterns verbatim) and escapes document
  delimiter collisions; `augmentWithDocument` follows the exact
  `GroundingPrompt.augment` order with the original-text language directive.
- **ReadTextToolSet**: schema-only `read_text_file(filename: String)` mirroring
  WebFetchToolSet, sharing description constants for the Plan 02 remote mapping.
- **LocalToolLoop**: `TOOL_READ_TEXT` constant, exact-match dispatch, filename
  validation (blank/non-String degrades model-only), filename-only status copy
  (`Reading document "…"`), verbatim `mapDocumentResult`, three-tool unknown
  message, `DOCUMENT_READ_FAILED_STRING` degradation.
- **LiteRTLmProvider**: third `tools` entry, volatile `attachedDocumentBlock`
  turn-binding, `read_text_file` executor branch (Dispatchers.IO, null degrades,
  catch-all never throws, no internet gate).

## Verification

- `./gradlew :app:testDebugUnitTest --tests "…DocumentPromptTest" --tests "…DocumentToolTest"` — BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest` (FULL suite) — BUILD SUCCESSFUL, 0 failures
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
- Zero new Gradle dependencies; Kotlin only; file I/O stays on Dispatchers.IO.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Pre-existing MessageBubble.kt:862 compile error**
- **Found during:** Task 1 (first compile)
- **Issue:** `stringResource()` (composable) invoked inside `Modifier.semantics{}`
  (non-composable) — committed at HEAD by phase 69, confirmed pre-existing by
  compiling with plan files removed. Blocked ALL compilation including unit tests.
- **Fix:** Hoisted the two description strings above the `TextButton` into the
  composable scope; `semantics{}` assigns the pre-resolved value. No behavior change.
- **Files modified:** `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt`
- **Commit:** 8a6c9a98

**2. [Rule 1 - Bug] Nested block comment via `text/*` in KDoc**
- **Found during:** Task 1 (first compile)
- **Issue:** The literal `/*` sequence inside KDoc opened a nested block comment
  (Kotlin nested comments), producing `Missing '}'` / `Unclosed comment` errors.
- **Fix:** Reworded to "any other text-prefixed mime".
- **Files modified:** `DocumentReader.kt`
- **Commit:** 8a6c9a98

## Decisions Made

- Turn-bound block contract defined for Plan 02 consumption
  (`attachedDocumentBlock` writer clears after send; T-70-04).
- Comment-only update in WebFetchToolSet (two-tool → three-tool allowlist note).

## Known Stubs

None.

## Threat Flags

None — T-70-01..04 mitigations implemented as planned (sanitize, cap, allowlist, turn binding).

## Self-Check: PASSED

- All created files exist; commits 8a6c9a98 + f7e111a2 in `git log`.
