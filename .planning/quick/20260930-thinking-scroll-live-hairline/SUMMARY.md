# SUMMARY: thinking join + live thinking + scroll hardening + hairline hunt

Three fixes shipped (Tasks 1–3) + one bounded hunt reported (Task 4). No new
shadows/elevations/borders/dividers anywhere in the message path. English copy
throughout.

## Per-task status

### Task 1 — Thinking join: VERIFY-ONLY (no code change)
- Re-read `LiteRTLmProvider.runToolLoop` `onThought`: `thought.append(thinking)`
  with NO separator — the `"\n"`-join premise was stale (already separator-free).
  Site left byte-identical per plan.
- Regression tests already existed (`LiteRTLmLoopTest`: `word-per-line thought
  deltas accumulate with no separator`, `thought delta interior newlines pass
  through verbatim - empties skipped`) covering both must-haves. No new test
  file needed — deviation from plan text, same contract locked.
- Verified green in the targeted run.

### Task 2 — Live thinking: DONE (commit a75ed526)
- Mechanism chosen: **new `StreamToken.Thinking(delta)` type through existing
  when-exhaustiveness** (not provider-side accumulation + periodic VM update).
  Why: the only provider→VM channel is the `StreamToken` flow — a "periodic
  update" without a token type would mean shared mutable state across layers
  (wrong layering). The token touches the right layers and the compiler
  enforces every consumer.
- Provider (`LiteRTLmProvider`): `onThought` emits `Thinking(thinking)` per
  non-empty delta alongside accumulation; `Done(reasoning)` emission sites
  untouched, still final.
- VM (`ChatViewModel`): new branch mirrors the Delta 50ms throttle idiom.
  Deltas append directly to `liveThought` (no parsing needed, so only the UI
  emit is throttled — no unflushed-tail loss); Delta-branch text flushes now
  resolve `streamingReasoning` as `tagReasoning.ifEmpty { liveThought }`
  (Done-branch precedence: tags win, native fills — otherwise every answer
  flush would blank the live panel). `parseThinkBlocks`, `Done` sites,
  loop/search mechanics untouched.
- Thinking-off path: `LiteRtLlmHelper` + `LmStudioHelper` strip-maps became
  `mapNotNull` with `Thinking -> null` (toggle off = never surfaced).
  `PromptLabViewModel`, `ModelBenchmarkWorker` ignore it (`-> Unit`); the
  `ChatCancellationTest` harness `when` likewise. `LMStudioProvider:362` is a
  non-exhaustive `is`-check — unaffected. Grep gate: 6 consumer files, all
  handled, zero `else ->` added.
- Tests: 2 provider tests (live order before Done; empties emit nothing) +
  new `ChatLiveThinkingTest` (VM: intermediate panel non-empty BEFORE Done,
  1 live flush < 11 deltas, persisted reasoning == streamed exactly). One
  pre-existing test updated to the new contract (`multi-round loop…` now
  expects the live `Thinking` token ahead of the tool calls).

### Task 3 — Scroll hardening: DONE (commit 832f089d)
- `pinLastItemEnd`: pin target clamped to `layoutInfo.totalItemsCount - 1`
  (lag → pins current end, never throws); scroll calls wrapped in try/catch
  logging via Timber (`CancellationException` rethrown); clamp math extracted
  to pure internal `clampPinTarget` with truth-table test
  (`ChatPinTargetTest`, 5 cases).
- Diff on `ChatScreen.kt` is imports + helper + `pinLastItemEnd` body only;
  follow `LaunchedEffect`, flag order, snap, `isAtBottom`, pill rule/paths
  byte-identical (verified via diff).

### Task 4 — Hairline hunt: REPORT-ONLY (no code change, STOP rule applied)
- Full render path read: Thinking header Row (no background/divider/border),
  `AnimatedVisibility` expand/shrink (default clipped — no bleed), inner
  `Surface(Transparent)`, user-bubble `Surface`, fade overlays, `TurnStatusRow`
  slot (null renders nothing), `ChatInputBar` container, Scaffold/IME gaps.
- No illegitimate source found. `Divider`s exist only in ModelSelector sheets,
  SourcePreviewSheet, ConversationList — not the message path.
  `shadowElevation = 6.dp` exists only on the JumpToLatestPill (pre-existing,
  pill-only). Zero `shadow|elevation|border` tokens in MessageBubble/
  ChatInputBar (grep gate). Shadow theory for the input bar stays refuted.
- Legitimate edges (reported, NOT restyled):
  - User bubble `0xFF121212` (MessageBubble.kt:156) vs screen background.
  - Input bar `0xFF2B2B29` (ChatInputBar.kt:55), flat, no shadow.
  - List fades (ChatScreen.kt:410-439) opaque foot `0xFF1F1F1E` == theme
    `BackgroundDark`/`SurfaceDark` (Color.kt:11-13) — seamless by construction,
    no seam possible in dark theme.
  - Thinking header text `0xFF545450` (MessageBubble.kt:177,183) — text, not a line.

## Test results
- `./gradlew :app:assembleDebug` — GREEN.
- `./gradlew :app:testDebugUnitTest` (full) — GREEN: **858 tests, 0 failures,
  0 errors, 0 skipped** (includes new `ChatLiveThinkingTest` 1/1 and
  `ChatPinTargetTest` 5/5).
- Grep gates: `is StreamToken.` consumers all exhaustive; no `else ->` in
  token `when`s; no new divider/shadow/elevation/border tokens in message path.

## Deviations from plan
1. Task 1: regression tests pre-existed (prior "thinking-header-feelings"
   quick-task); added none. Contract verified + locked by existing tests.
2. Task 2: one existing test expectation extended for the new token
   (`multi-round loop…`); one test-harness `when` branch added
   (`ChatCancellationTest`); Delta-branch merge line
   (`tagReasoning.ifEmpty { liveThought }`) required so text flushes don't
   blank the live panel — same precedence as Done, no behavior change when
   no native thought flows.
3. Task 2 throttle detail: direct-append + emit-only throttle (no pending
   buffer) — simpler than Delta's buffer/raw split and lossless by
   construction; same 50ms wall-clock idiom.

## On-device notes (no adb here — needs confirmation)
- Thinking paragraph rendering (Task 1) and live-panel timing/feel (Task 2)
  need a real LiteRT model turn with thought channel.
- Follow/pin behavior under real layout lag (Task 3) needs a long streaming
  session with scroll-up + new content.
- The hairline itself (Task 4) needs visual confirmation against the reported
  edges; if it reproduces, re-hunt with the exact screenshot position.

## Self-Check: PASSED
- Commits a75ed526, 832f089d exist; all listed files present; full suite green.
