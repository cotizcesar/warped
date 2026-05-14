---
gsd_state_version: 1.0
milestone: v1.6
milestone_name: Code Syntax Highlighting
status: planning
last_updated: "2026-05-14T21:07:40.003Z"
last_activity: 2026-05-14
progress:
  total_phases: 3
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State: Warped

**Last updated:** 2026-05-14
**Last activity:** 2026-05-14 — Milestone v1.6 roadmap created

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-14)

**Core value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.
**Current focus:** v1.6 Code Syntax Highlighting — Phase 28 ready to plan

## Current Position

Phase: 28 of 30 (Tokenization Engine & Theme System)
Plan: —
Status: Ready to plan
Last activity: 2026-05-14 — ROADMAP.md created, 20 requirements mapped across 3 phases

Progress: [░░░░░░░░░░] 0%

## Phase Structure

| Phase | Name | Requirements | Status | Depends On |
|-------|------|--------------|--------|------------|
| 28 | Tokenization Engine & Theme System | SYNX-01..03, THEM-01,02,04 (6) | Not started | — |
| 29 | UI Components & MarkdownText Refactoring | SYNX-05, THEM-03, CODE-01..05, INTG-01 (8) | Not started | Phase 28 |
| 30 | Streaming Integration & Everywhere Application | SYNX-04, INTG-02..06 (6) | Not started | Phase 29 |

## Completed Milestones

- ✅ v1.0 MVP — 5 phases, 30 requirements
- ✅ v1.1 LiteRT-LM Integration — 5 phases, 26 requirements
- ✅ v1.2 GGUF Native Inference — 5 phases, 27 requirements
- ✅ v1.3 Remote Provider Endpoints & UX — 4 phases, 31 requirements
- ✅ v1.4 Onboarding Wizard — 3 phases, 25 requirements
- ✅ v1.5 Bug Hunt, Cleanup & Hardening — 5 phases, 26 requirements

**Total across all milestones:** 30 phases (27 complete + 3 planned), 185 requirements (165 complete + 20 pending)

## Performance Metrics

**Velocity:**
- Total plans completed: 38 (v1.5)
- Average duration: ~18 min
- Total execution time: ~12.5 hours

## Accumulated Context

### Decisions

- [v1.6]: Highlights 1.1.0 selected as tokenization engine over custom regex tokenizer (~750-1,400 lines saved), Prism4j (archived 2023), and kotlin-textmate (v0.1.0, too new). Wrapped behind SyntaxHighlighter domain interface for swapability.
- [v1.6]: Deferred highlighting strategy: flat monospace during streaming, full syntax coloring applied when closing ``` fence arrives. Prevents O(n²) streaming jank.
- [v1.6]: CodeTheme enum → SyntaxTheme data class migration using existing DataStore key. Old enum names map to new theme objects.
- [v1.6]: MarkdownText restructured from single Text(AnnotatedString) to block-based Column of composables to host language header bar and copy button.

### Pending Todos

None yet.

### Blockers/Concerns

- Highlights CodeHighlight → CodeToken mapping needs verification during Phase 28 planning. May require spike.
- Language auto-detection accuracy for 15 languages not benchmarked against real LLM output. 90% fence-coverage estimate unverified.
- Long code block performance (>500 lines) not benchmarked on Android hardware. Cap at 200 lines with expander if needed.

## Deferred Items

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| *(none)* | | | |

## Session Continuity

Last session: 2026-05-14
Stopped at: ROADMAP.md created for v1.6 milestone
Resume file: None
