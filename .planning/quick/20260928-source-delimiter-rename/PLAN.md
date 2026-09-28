# Quick Plan: source-delimiter rename (block-label echo fix)

## Cause (locked, on-device evidence 2026-09-28)
The model echoed the internal label `[WEB CONTEXT 1]` into its answer because the fused
block header reads `[WEB CONTEXT 1 — source [1]: url]`. The citation instruction
(`[1]`/`[2]`) works; only the block LABEL leaks. Fix = rename delimiters so no
`WEB CONTEXT N` phrase exists to echo. Citation-marker scheme (`[1]`/`[2]`) stays.
Out of scope: extraction, budgets, UI.

## New delimiter format (locked)
- Per-page header: `--- Source [N]: <url> ---` (the `[N]` is the desired citation
  marker; the leak was the WORDS `WEB CONTEXT 1`, which no longer exist).
- Footer: `--- End of sources ---` — SINGLE shared footer at the end of the whole
  block, no per-N footer to echo.
- `buildBlock(url, text)` (single-page, used by `WebPageFetcher`): same scheme with N=1:
  `"--- Source [1]: $url ---\n$text\n--- End of sources ---"`.
- `buildFusedBlock(pages)`: each page gets `--- Source [i+1]: url ---` header + text,
  pages joined with `\n\n`, then ONE trailing `\n\n--- End of sources ---`.
- `SYSTEM_PROMPT` (current wording verified in `GroundingPrompt.kt:12-17`): keep the
  cite-`[1]`/`[2]` + never-invent-URLs rules verbatim; change ONLY the block reference
  `"Answer using the [WEB CONTEXT] block when relevant."` →
  `"Answer using the sources below when relevant."` (minimal edit).

## Task 1 — Rename builders + SYSTEM_PROMPT wording
**Files:**
- `app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt`

**Actions:**
1. `buildBlock`: emit new header/footer per format above (N=1).
2. `buildFusedBlock`: per-page `--- Source [i+1]: <url> ---` headers, single shared
   `--- End of sources ---` footer at end (NOT per page).
3. `SYSTEM_PROMPT`: replace only the `[WEB CONTEXT] block` reference with
   `the sources below`; keep cite + no-invented-URLs sentences byte-identical.
4. Update the `augment` KDoc (`[WEB CONTEXT] behavior, unchanged` → neutral wording
   like `block-injection behavior, unchanged`). Also update KDoc in
   `GroundingBudget.kt:7` (`[WEB CONTEXT 1..N] block` → `Source [1..N] block`) and
   `GroundingResult.kt:6` (`[WEB CONTEXT] block` → `sources block`) — user/behavior-
   relevant comments only; internal code identifiers need not change.
5. Do NOT touch `MultiUrlFetcher.kt`, `WebPageFetcher.kt`, `ChatViewModel.kt` call
   sites (they consume `buildBlock`/`buildFusedBlock`/`augment` outputs opaquely).

**Verify:** `./gradlew :app:assembleDebug` compiles.
**Done:** No `WEB CONTEXT` phrase remains in `GroundingPrompt.kt` outside historical
phase tags (e.g. `Phase 50 (WEB-04)` comments may stay).

## Task 2 — Sanitizer escapes for new delimiters (keep old ones)
**Files:**
- `app/src/main/java/com/warped/data/grounding/WebContextSanitizer.kt`

**Actions:**
1. Append AFTER the existing three `.replace` lines (~56-58):
   - `.replace("--- Source [", "--- Source-[")`
   - `.replace("--- End of sources ---", "--- End-of-sources ---")`
   (mirrors the existing hyphen-insertion escape style so a colliding line can never
   equal a real delimiter).
2. KEEP the three old escapes (`[WEB CONTEXT`, `[FIN WEB CONTEXT`, `[END WEB CONTEXT`).
   Reason (planner-locked): old-format blocks may persist in stored chat history /
   cached prompts; dropping the old escapes would let fetched content resurrect an
   old-style breakout. Defense in depth, one line each — keep-vs-drop decided: KEEP.
3. Update KDoc line 8 (`[WEB CONTEXT] block` → `sources block`).

**Verify:** `./gradlew :app:testDebugUnitTest --tests "com.warped.data.grounding.WebContextSanitizerTest"` green
after Task 3 updates (run together).
**Done:** Sanitizer neutralizes both new delimiter strings and all three old ones.

## Task 3 — Update tests + add echo-regression test
**Files (every assertion on old delimiters):**
- `app/src/test/java/com/warped/data/grounding/GroundingPromptTest.kt` (lines ~22, ~49:
  `indexOf("[WEB CONTEXT")` → `indexOf("--- Source [")`; exact-delimiter equality → new format)
- `app/src/test/java/com/warped/data/grounding/MultiUrlFusionTest.kt` (lines ~23-26, ~38, ~50:
  header/footer contains → new strings; poison-input line 38 uses new header with N=9 and
  asserts `doesNotContain("--- Source [9]")`)
- `app/src/test/java/com/warped/data/grounding/MultiUrlFetcherTest.kt` (lines ~35, ~57-58:
  single-block literal → new format; fused contains → `--- Source [1]: …` / `--- Source [3]: …`)
- `app/src/test/java/com/warped/data/grounding/WebContextSanitizerTest.kt` (lines ~51-58, ~78-88:
  keep old-escape assertions AS-IS — old escapes still active per Task 2 — and ADD
  parallel assertions: input containing `--- Source [1]: https://x` and
  `--- End of sources ---` lines is escaped to `--- Source-[1]…` / `--- End-of-sources ---`)
- `app/src/test/java/com/warped/ui/chat/ChatGroundingToggleTest.kt`: references
  `GroundingPrompt.SYSTEM_PROMPT` constant only (no literal delimiter) — NO change expected;
  confirm by inspection.

**New regression test** (in `GroundingPromptTest.kt`): block text contains no standalone
`WEB CONTEXT <digit>` phrase (the echo trigger). Assert for `buildBlock`, `buildFusedBlock`
(2 pages), and `augment` output:
- `doesNotContain` regex `WEB CONTEXT\s*\d` (use `assertThat(out).matches(...)` negation
  or `!Regex("WEB CONTEXT\\s*\\d").containsMatchIn(out)`), AND
- `doesNotContain("[END WEB CONTEXT")`.

**Verify:**
1. `./gradlew :app:testDebugUnitTest` fully green.
2. Grep gate for stray old-delimiter builders (escaping lines excluded by design):
   `grep -rn "WEB CONTEXT" app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt`
   must return ONLY historical phase-tag comments; and
   `grep -rn "WEB CONTEXT \${\|WEB CONTEXT 1\|END WEB CONTEXT" app/src/test --include="*Test.kt" -v`
   shows no test still asserting old delimiters as expected output (sanitizer old-escape
   assertions are the documented exception).

**Done:** Full `:app:testDebugUnitTest` green; no test asserts old delimiters as live output;
regression test fails on the old format (validates it guards the echo trigger).
