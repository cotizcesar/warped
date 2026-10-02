# Quick: dictation auto-stop + cursor to end + card alignment

**Created:** 2026-10-02
**Status:** in-progress
**Mode:** quick (no research/discussion — user-specified changes)

## Task

Three user-requested changes (follow-up to debug session dictation-chat-bugs):

1. **Auto-stop restored** — revert continuous dictation: the session ends on
   final/silence/timeout/error (flag clears). Stops only via platform end OR
   user tap. Keep: StackOverflow fix, EXTRA_LANGUAGE, flag-first stopDictation.
   Delete `restartDictation()` + `isRecoverableDictationError()` (dead after
   revert). Update `VoiceDictationTest` continuous expectations → auto-stop.
2. **Cursor to end** — `ChatInputBar`: on external text change, snap caret to
   `TextRange(text.length)` and report `onCursorChange(text.length)` so the
   VM's `lastKnownCursor` stays in sync; initial `TextFieldValue` also selects
   end. Typing path untouched (sync block only runs on external change).
3. **Card alignment** — `ModelCard`: indent `downloadContent`/error/expanded
   slots by 22dp (10dp dot + 12dp gap) so button, description and features
   table align vertically with the title; dot keeps its left column only.

## Files

- app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
- app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
- app/src/main/java/com/warped/ui/components/ModelCard.kt
- app/src/test/java/com/warped/ui/chat/VoiceDictationTest.kt

## Verify

- `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --offline`
- `./gradlew :app:testDebugUnitTest --offline` — 0 failures
- EN+ES parity untouched (no new strings)
