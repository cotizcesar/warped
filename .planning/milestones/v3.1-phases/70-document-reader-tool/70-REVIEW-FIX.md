---
phase: 70-document-reader-tool
fixed_at: 2026-10-02T00:00:00Z
review_path: .planning/phases/70-document-reader-tool/70-REVIEW.md
iteration: 1
findings_in_scope: 9
fixed: 9
skipped: 0
status: all_fixed
---

# Phase 70: Code Review Fix Report

**Fixed at:** 2026-10-02
**Source review:** .planning/phases/70-document-reader-tool/70-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 9 (3 critical + 6 warnings)
- Fixed: 9
- Skipped: 0

**Verification environment:** gates ran in the main checkout
(` /var/home/cotizcesar/Documents/warped`, no isolated worktree — subagent
was the sole writer, so no foreground race was possible).

## Fixed Issues

### CR-01: Unsanitized SAF filename fused into model context

**Files modified:** `app/src/main/java/com/warped/data/grounding/DocumentPrompt.kt`, `app/src/test/java/com/warped/data/grounding/DocumentPromptTest.kt`, `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
**Commit:** `355eda8a`, `58dac721` (escape-sequence correction — first version broke kotlinc, caught by compile gate, fixed before any other commit built on it)
**Applied fix:** New pure helper `DocumentPrompt.sanitizeFilename()` (strip `\r`/`\n`/controls, trim, cap 120, blank → `document.txt`) with unit tests; `readAttachedDocument()` applies it right after the metadata query so the block header, Fuentes `doc:` row, chip label, and notices all consume the safe value.
**Logic note:** `"fixed: requires human verification"` — the sanitizer shape (which chars are stripped vs preserved) is a judgment call; verify accented/Unicode filenames still display correctly on device.

### CR-02: Byte/char unit mismatch in bounded read

**Files modified:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
**Commit:** `355eda8a`
**Applied fix:** `readAttachedDocument()` now reads `cap + 1` chars via `bufferedReader(Charsets.UTF_8)` into a `CharArray` (auto-close via `use`), replacing the `ByteArray` probe; truncation detection is now in the same unit as `DocumentReader.bound()`. Removed the now-unused `BufferedInputStream` import.
**Logic note:** `"fixed: requires human verification"` — malformed-input REPLACE decoding behavior now comes from the Reader decoder rather than the `String(bytes, UTF_8)` constructor; spot-check a doc with invalid byte sequences on device.

### CR-03: Delimiter escape does not match the emitted envelope

**Files modified:** `app/src/main/java/com/warped/data/grounding/DocumentPrompt.kt`, `app/src/test/java/com/warped/data/grounding/DocumentPromptTest.kt`
**Commit:** `355eda8a`
**Applied fix:** `sanitize()` now escapes the `--- Document:` prefix generally (`--- Document-:`) instead of only the `[` variant; updated the existing delimiter test expectation and added a regression test with a bracket-less `--- Document: forged ---` body line.

### WR-01: FAILED attachment is a dead end

**Files modified:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
**Commit:** `32311588`
**Applied fix:** `attachDocument()` emits `doc_reader_failed` at pick time for the FAILED outcome (same position as the UNSUPPORTED branch); the send-time notice remains as a backstop.

### WR-02: Attachment cleared even when the turn never consumed it

**Files modified:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
**Commit:** `3c296ee5`
**Applied fix:** `consumed` flag declared in the send coroutine, set `true` immediately before `helper.runInference(...).collect`; the `finally` clears the attachment only when `consumed` is true (plus the existing ref-equality guard).
**Logic note:** `"fixed: requires human verification"` — cancellation (`CancellationException` rethrow after inference started) still clears the attachment; confirm that is the desired UX (chip dropped on user Stop).

### WR-03: Attachment survives conversation switch

**Files modified:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
**Commit:** `274ba784`
**Applied fix:** `_attachedDocument.value = null` at the top of both `selectConversation()` (synchronously, before the load coroutine) and `newConversation()`.

### WR-04: Rapid re-picks race

**Files modified:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
**Commit:** `32311588`
**Applied fix:** New `attachSeq: AtomicLong` (same pattern as `generationSeq`); each `attachDocument()` takes a sequence number and drops stale finishers (notices included) so the last pick wins.

### WR-05: Fully-stripped document still attaches as READY

**Files modified:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
**Commit:** `355eda8a`
**Applied fix:** After `sanitize()`, blank content returns a FAILED attachment (degrades with the WR-01 pick-time notice + send-time backstop) instead of a header/footer-only READY block.

### WR-06: Turn-bound block is public mutable singleton state

**Files modified:** `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt`
**Commit:** `8ce58ec8`
**Applied fix:** `attachedDocumentBlock` is now `private` with `bindDocumentBlock()` / `clearDocumentBlock()` accessors; the armed-turn bind/`finally`-clear call site updated. The only other reference (`ReadTextToolSet.kt` KDoc) is a doc comment, still accurate, left untouched. IN-01 folded in: the executor reads the volatile directly, dropping the pointless `withContext(Dispatchers.IO)` hop.

## Verification

- Targeted: `DocumentPromptTest` (14) + `DocumentToolTest` (8) + `DocumentRemoteToolsTest` (5) — 27/27 pass.
- Full suite: `:app:testDebugUnitTest` — **1047 tests, 0 failures, 0 errors, 0 skipped**.
- Build: `:app:assembleDebug` — **BUILD SUCCESSFUL**.
- One self-caught defect during fixing: the first `sanitizeFilename` version embedded a double-escaped unicode literal (`Unsupported escape sequence`, `compileDebugKotlin` FAILED); corrected and committed as `58dac721` before running the suites.

## Not fixed (out of scope — Info findings)

- **IN-02** (size readout "0 KB" / capped-count fallback): cosmetic, touches chip UI strings; left for a UI pass.
- **IN-03** (markdown-link neutralization parity / KDoc claim): low risk (`MarkdownText` renders no clickable links, `BrowserIntents` scheme-gates); suggest narrowing the KDoc claim when next touching the file.
- **IN-04** (duplicate document content when the model calls the tool): explicitly an accepted trade by design per the review; no action.

---

_Fixed: 2026-10-02_
_Fixer: the agent (gsd-code-fixer)_
_Iteration: 1_
