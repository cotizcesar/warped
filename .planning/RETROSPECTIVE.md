# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

## Milestone: v1.6 — Code Syntax Highlighting

**Shipped:** 2026-05-15
**Phases:** 3 | **Plans:** 8

### What Was Built
- Token-level syntax highlighting engine (Highlights 1.1.0 + TypeMapper) with 12 token types across 43 languages
- LanguageDetector with 20+ fence label aliases and keyword-frequency auto-detection fallback
- 4 preset themes (Monokai, One Dark, GitHub, Dracula) with automatic light/dark mode adaptation
- CodeBlock composable: syntax-colored tokens, language header bar, copy-to-clipboard, line numbers, expand/collapse, syntax issue warnings
- Full chat integration: theme flows from Settings dropdown through ViewModels to all chat composables
- Streaming-to-highlighted smooth transition (flat monospace during active streaming, full coloring on closing fence)
- Code font scale slider (0.8x–1.5x) in Settings, wired through full composable chain
- HuggingFace model card descriptions render syntax-highlighted markdown

### What Worked
- **Block-based markdown refactoring:** Restructuring MarkdownText from single AnnotatedString to Column of composables unlocked per-block UI (header bar, copy button). This was the key architectural enabler.
- **Domain-first design:** TokenType, SyntaxToken, SyntaxColor, SyntaxTheme all in domain/model/ with no Android dependencies. Made testing trivial (150 unit tests, zero Android framework dependencies).
- **Cross-phase gap detection:** The CodeBlock composable (545 lines) was orphaned — never called from MarkdownText. The verifier caught it, and a 13-line fix unlocked all features. Validation before shipping works.
- **Library wrapping:** Wrapping Highlights behind SyntaxHighlighter domain interface means it can be swapped later without touching UI code.

### What Was Inefficient
- **Orphaned integration:** CodeBlock wasn't wired to MarkdownText in Phase 29 because the two tasks (29-02 CodeBlock creation and 29-01 MarkdownText refactoring) ran in sequence without a final integration task. The plan had 29-03 for integration but it focused on chat wiring, not the block-to-composable connection.
- **Streaming regression:** The LaunchedEffect key didn't include `isStreaming` initially, causing per-token syntax highlighting during streaming. Fixed in Phase 30 but should have been caught in Phase 29.
- **codeFontScale hardcoded:** Font scale was set to 1.0f in CodeBlock and not plumbed through. Discovered late. Phase dependencies meant deferring to Phase 30.

### Patterns Established
- **DataStore → ViewModel → UiState → Composable propagation:** SyntaxTheme and codeFontScale both follow this pattern. New settings should use the same pipeline.
- **Hilt EntryPoint at composable scope:** Resolving LanguageDetector/SyntaxHighlighter via `EntryPoints.get()` in composable functions works cleanly without passing dependencies through every layer.
- **LaunchedEffect key sensitivity:** Any state that affects rendering MUST be in the LaunchedEffect key tuple. The `isStreaming` omission was the single bug causing the streaming regression.

### Key Lessons
1. **Integration tasks need explicit cross-task wiring validation.** A "wire X into Y" subtask in the plan would have prevented the orphaned composable.
2. **Streaming state must be a first-class parameter in composable rendering branches.** Flat monospace vs. highlighted is a fundamentally different rendering path that needs explicit guards.
3. **Block-based rendering is a better pattern for markdown than AnnotatedString.** The ability to place per-block UI elements (headers, buttons, gutters) justifies the composability overhead.

### Cost Observations
- Model mix: AI-managed execution (deepseek-v4-pro)
- Sessions: Multiple across 3 phases
- Notable: Phase 28 (engine + 150 tests) was the most productive phase — domain-first design with pure functions enabled rapid test-driven development

---

## Milestone: v2.2 — Simplificación + Web Grounding

**Shipped:** 2026-09-28 (override closeout — 4 device-smoke partials accepted)
**Phases:** 3 (49–51) | **Plans:** 5 | **Tasks:** 11

### What Was Built
- Surface removal: 26 files deleted (skills/tool-loop, HF token, model search); static allowlist catalog with direct downloads via `CatalogViewModel`; net −4721/+1378 lines
- Web grounding pipeline (`data/grounding/`): first-URL detection, bounded cancelable fetch (64KB/3-redirect/8-10-20s), hand-rolled HTML→text, hijack sanitizer, `[WEB CONTEXT]` augmentation; zero new dependencies
- Grounding surfaces: transient "Leyendo página…" chip, Fuentes list, model-only banner, default-ON Web settings toggle
- Syntax-theme fix: theme-threaded highlight call path (was Monokai-hardcoded) + all-4-preset regression tests; 232/232 unit green, assembleDebug + assembleRelease green

### What Worked
- **Net-deletion discipline:** grep-zero gates made removal verifiable — every deleted surface had a machine-checked zero-residue gate, so "is it really gone?" was never a judgment call.
- **Removals-first ordering:** grounding hooked into the post-removal transcript shape with no rework; theme fix verified against final call sites.
- **Zero-dependency constraint:** existing OkHttp + ConnectivityManager covered the whole grounding pipeline; `audit-dependencies.sh` stayed green throughout.

### What Was Inefficient
- **Missing `requirements-completed` frontmatter:** 49-01/49-02 SUMMARYs lacked it, forcing coverage reconstruction from VERIFICATION must-have reviews during audit. Every plan summary must carry the frontmatter.
- **Self-inflicted grep hits:** plan-written comments/docs tripped the plan's own grep gates twice (KDoc `body.string()`, catalog comment). Gate patterns should be validated against the plan text itself.
- **No device in environment:** all 3 phases deferred their visual smoke, producing 4 milestone-level partials. A connected emulator would have closed v2.2 clean.

### Patterns Established
- **Removal plans pair code deletion with grep-zero gates** (skills gate, Summarize gate, HF-token gates, search-surface gate) — reuse for any future surface removal.
- **Ephemeral grounding adornments render inside MessageBubble** so they scroll with their message; toggle takes effect next message, no restart.
- **Theme threading via interface default param plus concrete overload** (Kotlin forbids defaults on overrides).

### Key Lessons
1. Plan SUMMARYs must always include `requirements-completed` frontmatter — audit depends on it.
2. Deferred device smokes compound: 3 phases × no device = 4 milestone partials. Keep a release-UAT checklist per milestone.
3. Net-deletion milestones need release-posture gates (R8 keeps, dependency audit, assembleRelease) in the plan, not as an afterthought.

### Cost Observations
- Model mix: AI-managed execution, single day (2026-09-28)
- Plans: 5 across 3 phases, ~25 min each
- Notable: highest deletion-to-addition ratio to date; docs/audit commits (~13) outnumber feat commits (4)

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Phases | Key Change |
|-----------|--------|------------|
| v1.6 | 3 | Block-based rendering pattern established; cross-phase wiring verification improved |

### Cumulative Quality

| Milestone | Tests | Zero-Dep Additions |
|-----------|-------|-------------------|
| v1.6 | 150 | 3 new libraries (Highlights 1.1.0, Kotlinx Serialization, Kotlinx Coroutines Test) |

### Top Lessons (Verified Across Milestones)

1. Domain-first design enables comprehensive testing without framework dependencies
2. Cross-phase integration gaps are the most common failure mode — explicit wiring tasks prevent orphaned components
3. LaunchedEffect/Flow key sensitivity is a recurring source of subtle composable bugs
