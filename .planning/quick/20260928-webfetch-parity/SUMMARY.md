# SUMMARY — WebFetch Parity (OpenCode quality, Android constraints)

**Status:** complete — all 3 tasks executed, atomic commits per task.
**Date:** 2026-09-28

## Commits

- `17c0edb3` feat(webfetch-parity): HtmlToMarkdown converter + fetcher rework (Task 1)
- `0ca40a69` feat(webfetch-parity): sanitizer markdown hardening + budget retune (Task 2)
- `be4bde80` test(webfetch-parity): converter/attack/budget tests + fallout fixes (Task 3)

## Test results

- `:app:testDebugUnitTest --tests "com.warped.data.grounding.*"` — green (67 tests, incl. 11 new converter + 7 new sanitizer cases)
- Full `:app:testDebugUnitTest` — green, **346 tests, 0 failures**
- `:app:assembleDebug` — BUILD SUCCESSFUL
- Grep gate — clean: no `Jsoup.connect` anywhere under `app/src/main/java` (parse-only)

## Behavior deltas (caps / timeouts / budgets)

| Knob | Before | After |
|------|--------|-------|
| `MAX_BODY_BYTES` | 65536 (64KB) | 262144 (256KB), same 8KB-chunk streaming loop |
| connect / read / call timeout | 8 / 10 / 20 s | 10 / 15 / 30 s |
| User-Agent | Android mobile Chrome 120 | Desktop Chrome 143 (OpenCode string verbatim) |
| Accept | `text/html, text/plain` | `text/markdown;q=1.0, text/plain;q=0.8, text/html;q=0.7, */*;q=0.1` |
| HTML grounding | flat text only | structured markdown (`HtmlToMarkdown`), blank → flat-text fallback, blank-from-both → model-only (unchanged) |
| `text/markdown` bodies | rejected | direct passthrough with line-boundary truncation |
| `GroundingBudget.MIN_PER_PAGE` | 800 | 1500 (global tiers 4500/6000/scaled unchanged) |
| `HtmlToTextExtractor.MAX_CHARS` | 4000 | 6000 (default for direct callers only) |
| Sanitizer | line hijack filter + delimiter escape | + link-target allowlist (http(s) only, `//` → https; javascript:/data:/vbscript: incl. case/space/control-char padded → bare `[text]`; relative kept); delimiter escape after neutralization; quote-prefix (`>`) stripped before hijack match; no fence exemption |

Unchanged as locked: `cancel()`/`activeCalls` cooperative cancel, max-3 http(s)-only redirects, offline short-circuit, AuthInterceptor strip, hijack patterns + delimiter replacements byte-for-byte, `perPageBudget`/`globalBudget` formulas.

## Deviations / fallout fixes (Rule 1, committed in Task 3)

1. `TextNode.wholeText()` does not exist in this Jsoup version (synthetic-property resolution error) — used `TextNode.text()`.
2. Sanitizer link regex `[^()]*` missed targets with parens (`javascript:alert(1)`) — now allows one-level nested parens.
3. Top-level `<a>` body children unwrapped to text (link branch only ran for nested anchors) — shared `renderLink()` helper for both paths.
4. `MultiUrlFusionTest` 5-page case (7500 > 6000 global after floor raise) retuned to the plan's explicit 4-page boundary (6000/4 = 1500 = floor); window-fit intent intact. `GroundingBudgetTest` 5-page split 1200 → 1500 (floor).

English copy used throughout (truncation marker `… [truncated]` already English; no Spanish strings added).
