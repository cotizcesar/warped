---
status: passed
phase: 25
date: 2026-05-09
---

# Phase 25: Bug Fixes — Verification

All 4 bugs fixed and verified (./gradlew assembleDebug compiles):
- BUG-01: MarkdownText flushes code block content at end-of-stream ✓
- BUG-02: selectConversation skips unload when same model loaded ✓
- BUG-03: NavGraph syncs activeConversationId on route navigation ✓
- BUG-04: stopGeneration clears streaming; deleteMessage wired through all layers ✓
