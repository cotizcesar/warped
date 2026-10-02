# Summary: dictation auto-stop + cursor to end + card alignment

**Status:** complete
**Date:** 2026-10-02

## Changes

1. **Auto-stop restored** (`ChatViewModel.kt`): `onDictationFinal` commits text
   and clears `_isListening`; `onDictationError` clears flag + partials for ANY
   error. Deleted `restartDictation()` + `isRecoverableDictationError()` (no
   other callers). Kept: StackOverflow qualification, EXTRA_LANGUAGE,
   flag-first `stopDictation`. KDocs updated to the 2026-10-02 auto-stop
   decision.
2. **Cursor to end** (`ChatInputBar.kt`): external text changes snap caret to
   `TextRange(text.length)` and report `onCursorChange(text.length)` so
   `lastKnownCursor` stays in sync (plain var — no recomposition loop).
   Initial `TextFieldValue` also selects end. Typing path untouched.
3. **Card alignment** (`ModelCard.kt`, shared component): below-header slots
   (`downloadContent`/error/expanded details + CapabilityTable) wrapped in
   `Column(Modifier.padding(start = DotColumnWidth))` (22dp = 10dp dot +
   12dp gap). Button, description and features table align with the title;
   dot keeps its left column. Applies to Available cards too (consistent).

## Tests

- `VoiceDictationTest`: 4 tests renamed/re-asserted to auto-stop
  (final/blank-final/error → `isListening == false`).
- Full suite: **919 tests, 0 failures, 0 errors, 0 skipped**.
- No new strings → EN+ES parity untouched.

## Files

- app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
- app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
- app/src/main/java/com/warped/ui/components/ModelCard.kt
- app/src/test/java/com/warped/ui/chat/VoiceDictationTest.kt
