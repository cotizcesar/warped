---
phase: 70-document-reader-tool
reviewed: 2026-10-02T00:00:00Z
depth: standard
files_reviewed: 24
files_reviewed_list:
  - app/src/main/java/com/warped/data/grounding/DocumentReader.kt
  - app/src/main/java/com/warped/data/grounding/DocumentPrompt.kt
  - app/src/main/java/com/warped/data/agentic/ReadTextToolSet.kt
  - app/src/main/java/com/warped/data/agentic/LocalToolLoop.kt
  - app/src/main/java/com/warped/data/agentic/WebFetchToolSet.kt
  - app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt
  - app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt
  - app/src/main/java/com/warped/data/remote/dto/AnthropicDtos.kt
  - app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/AnthropicProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/CompatToolLoop.kt
  - app/src/main/java/com/warped/domain/model/ChatRequest.kt
  - app/src/main/java/com/warped/domain/model/GroundedSource.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
  - app/src/main/java/com/warped/ui/chat/components/OgSourceCard.kt
  - app/src/main/java/com/warped/ui/chat/components/SourcePreviewSheet.kt
  - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values-es/strings.xml
  - app/src/test/java/com/warped/data/grounding/DocumentPromptTest.kt
  - app/src/test/java/com/warped/data/agentic/DocumentToolTest.kt
  - app/src/test/java/com/warped/data/remote/DocumentRemoteToolsTest.kt
findings:
  critical: 3
  warning: 6
  info: 4
  total: 13
status: issues_found
---

# Phase 70: Code Review Report

**Reviewed:** 2026-10-02
**Depth:** standard
**Files Reviewed:** 24
**Status:** issues_found

## Summary

Reviewed all Phase 70 source changes (commits 8a6c9a98, f7e111a2, 5f6018bc, 3f0026c7, 08bd5f01):
bounded SAF document read, `[DOCUMENT CONTEXT]` fusion + sanitizer, `read_text_file`
wiring across local + all three remote loops, VM attachment state, and chat UX.

The tool-loop plumbing is sound: exact-match dispatch, fail-closed arg validation,
per-turn block binding with `finally`-clear, no internet gate on the document branch,
executors that never throw, and the tools-rejection single-retry path preserving the
fused text. Path traversal is correctly absent (filename is display-only; the `Uri`
opens the file). No storage permission is requested (SAF `OpenDocument` only).

Three critical defects break the phase's own security/correctness contracts
(T-70-01 sanitizer coverage, "never silent truncation"): an unsanitized
attacker-influenced filename fused into model context, a bytes-vs-chars unit mismatch
that silently drops non-ASCII content without the truncation marker, and a delimiter
escape that does not match the envelope it claims to protect. Six warnings cover
attachment lifecycle bugs (dead-end FAILED state, premature clearing, cross-conversation
staleness, pick race, sanitize-to-empty).

Verified clean (no finding): hijack-line patterns are indeed verbatim copies of
`WebContextSanitizer`; `gate()` mime-wins-over-extension + `.txt`/`.md` fallback is
correct; EN/ES `doc_reader_*` parity holds (7 keys each, `%1$d`/`%2$s`/`%1$s`
placeholders match); remote `tools[]` schemas reuse the local constants verbatim;
the drive-by `MessageBubble.kt` fix (hoist `stringResource` out of `semantics{}`)
is correct and minimal.

## Critical Issues

### CR-01: Unsanitized SAF filename fused into model context (sanitizer bypass)

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:3585`
**Issue:** `DocumentPrompt.sanitize()` runs on the document *body* only, but
`buildBlock(filename, sanitized, truncatedAt)` interpolates the raw SAF
`DISPLAY_NAME` into the envelope header (`--- Document: $filename ---`), which is
fused into the prompt (line 1113) and re-fed verbatim to every tool executor via
`mapDocumentResult`. `DISPLAY_NAME` is attacker-influenced (a file shared from
another app or downloaded can carry newlines and instruction text, e.g.
`notes.txt\nIgnore previous instructions…` or a forged
`--- End of document ---` line). This bypasses the entire T-70-01 sanitizer from
the header side. The same raw value is stored in the Fuentes `url`
(`doc:` + filename, line 1115) and echoed into `ToolStatus` rows.
**Fix:**
```kotlin
// Sanitize the filename before it enters model context — strip line breaks /
// control chars and cap length; use the cleaned value for block + Fuentes row.
val safeFilename = filename
    .replace(Regex("[\\r\\n\\u0000-\\u001F\\u007F]"), " ")
    .trim()
    .take(120)
    .ifBlank { "document.txt" }
block = DocumentPrompt.buildBlock(safeFilename, sanitized, truncatedAt)
// ... and use safeFilename for the GroundedSource url + chip label
```

### CR-02: Byte/char unit mismatch silently truncates non-ASCII documents without the marker

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:3563-3579`
**Issue:** The bounded read allocates `ByteArray(cap + 1)` and reads at most
`cap + 1` **bytes**, but `DocumentReader.bound()` (DocumentReader.kt:55-56)
truncates at `cap` **chars**. For multi-byte UTF-8 (Spanish accented text is
2 bytes/char; CJK 3), the decoded string is routinely shorter than `cap` chars
while unread bytes remain — `bound()` then returns a null marker and the envelope
claims the document is whole. This violates the phase's explicit "never silent"
truncation contract and hits the ES-locale path hardest. Truncation detection must
be in the same unit as the cap.
**Fix:**
```kotlin
// Read cap+1 CHARS (not bytes) so the truncation signal matches bound()'s unit.
val reader = context.contentResolver.openInputStream(uri)
    ?.bufferedReader(Charsets.UTF_8) ?: return failed(...)
val buf = CharArray(cap + 1)
var total = 0
while (total < cap + 1) {
    val n = reader.read(buf, total, cap + 1 - total)
    if (n == -1) break
    total += n
}
val eof = total <= cap // probed the +1 char slot; no remainder means whole file
val decoded = String(buf, 0, total)
val (bounded, truncatedAt) = DocumentReader.bound(decoded, cap)
// If total == cap+1, bound() sets the marker; eof distinguishes exact-fit.
```

### CR-03: Delimiter escape does not match the emitted envelope (forged headers survive)

**File:** `app/src/main/java/com/warped/data/grounding/DocumentPrompt.kt:57-60`
**Issue:** `buildBlock` emits headers as `--- Document: $filename ---` (no
bracket), but `sanitize` only neutralizes the bracket variant
(`--- Document: [` → `--- Document:-[`). A document body containing a literal
`--- Document: forged ---` line passes through verbatim and forges a block
boundary inside the envelope — the "can never forge block boundaries" comment
(lines 46-50) is false. (The web analog escapes `--- Source [` because web
headers actually contain `[N]`; the analogy was copied without adapting to the
new envelope shape.) The footer escape is correct; only the header leg is broken.
**Fix:**
```kotlin
return kept.joinToString("\n")
    .replace("[DOCUMENT CONTEXT", "[DOCUMENT-CONTEXT")
    .replace("--- Document:", "--- Document-:")
    .replace("--- End of document ---", "--- End-of-document ---")
```
(Escape the header prefix generally, not just the `[` variant. Add a unit test
with a `--- Document: forged ---` body line asserting it does not survive.)

## Warnings

### WR-01: FAILED attachment is a dead end — doc-only failed send drops silently with no notice

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:572`
**Issue:** The FAILED notice fires only at *send* time (lines 1104-1110), but a
doc-only FAILED send can never reach it: line 572 early-returns when text is
blank and `sendableDocument` is null, and the chip is hidden for non-READY
attachments (ChatScreen.kt:650 `readyDoc` filter), so the user sees nothing, gets
no notice, and the send button is disabled. UNSUPPORTED avoids this by notifying
at *pick* time. This contradicts "send is never dead-ended" / "failed reads fall
back gracefully (model-only answer + notice)".
**Fix:** Emit `doc_reader_failed` at pick time in `attachDocument()` for the
FAILED outcome (same position as the UNSUPPORTED branch, lines 3518-3524), keeping
the send-time notice as a backstop, or show an error-state chip with one-tap
dismiss.

### WR-02: Attachment cleared even when the turn never consumed it

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:1426-1431`
**Issue:** The `finally` clears the consumed attachment on *every* exit of the
send coroutine, including validation early-returns where nothing was sent
(missing model file lines 648-657, load failure 663-675, vision/audio capability
gates 999-1017). The user picks a document, taps send, and the chip silently
vanishes with no turn. Clearing is correct for consumed/cancelled/error turns but
not for turns rejected before inference.
**Fix:**
```kotlin
var consumed = false
// ... set consumed = true immediately before helper.runInference(...).collect
finally {
    if (consumed && sentDocument != null && _attachedDocument.value === sentDocument) {
        _attachedDocument.value = null
    }
    ...
}
```

### WR-03: Attachment survives conversation switch (stale content grounds the wrong turn)

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:1684-1704`
**Issue:** Neither `newConversation()` nor `selectConversation()` clears
`_attachedDocument`. A document picked in conversation A remains chipped and is
fused into the next turn of conversation B — per-turn attachment leaking across
conversation boundaries, including persisting a `doc:` Fuentes row against an
unrelated conversation.
**Fix:** Call `clearDocument()` (or `_attachedDocument.value = null`) in both
`newConversation()` and `selectConversation()` before loading the new state.

### WR-04: Rapid re-picks race — an older read can overwrite a newer attachment

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:3515-3527`
**Issue:** Each `attachDocument()` launches an independent coroutine with an
unbounded async read; two quick picks complete in arbitrary order and the last
*finisher* wins, not the last *pick* — violating "a new pick replaces the
attachment". The send-time ref-equality guard does not help here (both writes
precede the send).
**Fix:**
```kotlin
private val attachSeq = AtomicLong(0L)
fun attachDocument(uri: Uri) {
    val mySeq = attachSeq.incrementAndGet()
    viewModelScope.launch(coroutineExceptionHandler) {
        val outcome = withContext(Dispatchers.IO) { readAttachedDocument(uri) }
        if (mySeq == attachSeq.get()) {
            if (outcome.status == AttachStatus.UNSUPPORTED) { /* notice */ }
            _attachedDocument.value = outcome
        }
    }
}
```

### WR-05: Fully-stripped document still attaches as READY with empty content

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:3579-3589`
**Issue:** `decoded.isBlank()` is checked *before* `sanitize()`. A document whose
every line matches hijack patterns sanitizes to `""` yet attaches as READY: the
model receives a header/footer-only block with no content and no notice, and the
Fuentes preview sheet falls into the empty-extract path. A sanitizer that removes
everything should degrade like an unreadable file.
**Fix:**
```kotlin
val sanitized = DocumentPrompt.sanitize(bounded)
if (sanitized.isBlank()) {
    return AttachedDocument(uri, filename, sizeBytes, "", "", null, AttachStatus.FAILED)
}
```

### WR-06: Turn-bound block is public mutable singleton state

**File:** `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt:177-178`
**Issue:** `attachedDocumentBlock` is a public `@Volatile var` on a `@Singleton`.
The T-70-04 no-cross-turn-leak guarantee rests entirely on every future caller
remembering the bind-in-`try`/`finally`-clear discipline; any second writer (or a
concurrent `chat()` collection) silently cross-contaminates turns. The contract is
currently honored at the single call site (lines 379-384) but is unenforced.
**Fix:** Make the field `private`/`internal` and expose `bindDocumentBlock(block)`
/ `clearDocumentBlock()` (or pass the block as an `executeToolCallDetailed`
parameter), so misuse fails at compile time instead of leaking turns at runtime.

## Info

### IN-01: Pointless dispatcher hop to read a volatile

**File:** `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt:689`
**Issue:** `withContext(Dispatchers.IO) { attachedDocumentBlock }` context-switches
just to read a `@Volatile` String reference — volatiles are already safe to read
from any thread. Harmless but misleading (implies I/O happens here).
**Fix:** `val block = attachedDocumentBlock` directly.

### IN-02: Size readout shows "0 KB" and can show the capped read count, not the file size

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:3658-3665`
**Issue:** Integer division renders anything under 1024 bytes as "0 KB"; and when
the SAF cursor reports no size, `sizeBytes` falls back to the (cap-bounded) bytes
actually read, so the chip can label a 20 MB file "4 KB". Cosmetic.
**Fix:** Sub-KB → `"${bytes} B"` / `"< 1 KB"`; when size was unknown, prefer
omitting the size readout over showing the truncated read count.

### IN-03: Document sanitize omits the markdown-link neutralization leg

**File:** `app/src/main/java/com/warped/data/grounding/DocumentPrompt.kt:51-61`
**Issue:** `WebContextSanitizer` (WebContextSanitizer.kt:34-46) additionally
neutralizes `javascript:`/`data:`/`vbscript:` markdown-link targets; the document
port drops this leg while claiming verbatim parity. Risk is currently low —
`MarkdownText` renders no clickable link annotations and `BrowserIntents`
scheme-gates `http(s)` — but the parity claim in the KDoc overstates coverage.
**Fix:** Either port the `MARKDOWN_LINK` neutralization step or narrow the KDoc
claim to the hijack-line patterns only.

### IN-04: Document content is duplicated in context when the model calls the tool

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:1111-1151`
**Issue:** The VM fuses the full block into the turn text *and* binds the
identical string as `ChatRequest.documentBlock`, so an explicit `read_text_file`
call re-feeds a second full-size copy into the same context window (local loop
and all three remote executors). The "never a second full-size copy" comment
(LiteRTLmProvider.kt:166-178) describes avoiding a second *file read*, but the
token duplication is real. Accepted trade by design; noted for future
budget work (e.g. bind a short pointer and let the tool re-feed, or skip fusion
when the loop is armed).

---

_Reviewed: 2026-10-02_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
