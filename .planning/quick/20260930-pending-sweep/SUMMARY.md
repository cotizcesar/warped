---
audit_acknowledged:
  milestone: v2.5
  at: 2026-10-01
  status: unknown
---

# Sweep SUMMARY — all code-doable pending follow-ups (2026-09-30)

Status: COMPLETE — 7/7 tasks implemented, committed atomically, full suite green.

## Test results

- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest` — 739 tests, 0 failures, 0 errors, 0 skipped
- New suites (56 tests, all green):

| Suite | Tests | Commit |
|---|---|---|
| ThinkingConfigPassThroughTest | 5 | 83f497aa |
| RemoteImageCarryTest | 13 | 3e0df502 |
| SourceCardSnippetFallbackTest | 8 | 495a1098 |
| Migration16To17StaticTest | 7 | 495a1098 |
| ActiveDownloadPauseTest | 5 | b8a0597f |
| ModelActivationNewChatTest | 4 | 0bba071d |
| ToolFailureAffordanceTest | 5 | 8307e00c |
| Phase53PolishTrioTest | 9 | b21889c3 |

## Per-item notes (incl. on-device confirmation needed)

1. **ThinkingConfig wiring** — `reasoningEnabled` (already toggle-AND-capability
   gated in ChatViewModel:853) threads through `LoopArmSnapshot.thinking` into
   `EngineManager.createLiteRTConversation(config, thinkingConfig)`; budget 1024;
   toggle flip rebuilds the conversation. Toggle-without-capability stays disabled
   (untouched). ON-DEVICE: verify thinking UX appears for a capable model with the
   toggle on, and stays off otherwise.
2. **Remote image carry** — shared `HistoryImageCarry` K=3 newest-first rule
   (local builder delegates to it); OpenAI/Custom `image_url` parts, Ollama native
   `images[]` (prefix stripped), LM Studio native image items; text-only rows
   byte-identical. Audio decision: NO history audio on any path — `MessageEntity`
   has no audio column (`MessageEntity.kt`: id, conversation_id, role, content,
   token_count, created_at, images, stats, reasoning) and `ChatRequest.audioBytes`
   is current-turn-only, so Room-rebuilt history cannot carry audio anywhere.
   ON-DEVICE: verify image follow-ups render/answer correctly per provider.
3. **Snippet fallback** — `GroundedSource.snippet` persisted via `MIGRATION_16_17`
   (DB v17, schema `17.json` exported + committed), populated sanitized from
   DDG excerpts / Tavily content heads; render chain ogDescription → snippet
   (160 cap) → hidden in `OgSourceCard` (both cards) + `AllSourcesSheet`.
   ON-DEVICE: verify card descriptions on snippet-only sources.
4. **Download pause** — `ActiveDownloadContent` gains `onPause`/`onResume`
   (Pause IconButton by Cancel; Resume IconButton in paused branch);
   Models + catalog wired identically to tested manager APIs; `ModelsViewModel`
   exposes pause/resume mirroring `CatalogViewModel`. 4 strings added EN+ES
   (parity test green). ON-DEVICE: verify pause/resume icon layout on both screens.
5. **Activation opens a NEW chat (behavior change)** — `useLocalModel`/`useEndpoint`
   keep connect/activate logic, then create a bound conversation row ("New Chat"
   title until first send retitles it, same title rules) and navigate via
   `ChatDetail(id)`; old rows untouched (verified: exactly one create, zero
   updates/deletes). `onUseInChat` is now `(conversationId: Long) -> Unit`.
   ON-DEVICE: verify toggle-ON lands in a fresh bound chat; old chat intact in
   Recents; first send titles correctly.
6. **Tool-failure note** — `ToolCallOutcome.failed` marks attempted-but-threw
   calls (8 executor catch sites); all 4 loop drivers (local, OpenAI, compat,
   Anthropic) emit `ToolCompleted` with `errorReason` (generic English copy,
   never exception detail); VM shows one auto-clearing non-persisted Snackbar.
   Model-fed `"Error: ..."` text untouched; validation/offline/cap/key paths stay
   silent per IN-02. ON-DEVICE: verify snackbar timing/appearance on a failed call.
7. **Phase-53 trio** — (a) override-state dot on the selector bar
   (green/amber/gray = on/off/inherit, existing palette, a11y labels reused);
   (b) deleted the redundant "Source N" sheet heading (the [N] badge labels the
   row; `sheet_source_fmt` strings removed EN+ES); (c) all-omitida turns render
   struck rows + visible count instead of hiding. ON-DEVICE: verify trio visuals.

## Deviations from plan

1. **[Rule 2] Retitle-on-first-send (Task 5)** — eager row creation would have
   frozen "New Chat" titles forever (titles are set once at creation).
   `ChatViewModel.ensureConversation` now retitles empty activation rows from
   the first message using the extracted shared `conversationTitle` helper.
   Files: `ChatViewModel.kt`. Covered indirectly by existing send-path tests
   (full suite green); no new test (pure refactor + guarded branch).
2. **[Rule 1] Fixed `LmStudioCancelTest` stub (Task 2)** — widening
   `EngineManager.createLiteRTConversation` with a defaulted `thinkingConfig`
   param broke the single-arg mockk stub (`cancelled turn never retries engine
   error`). Updated to the 2-arg form. File: `LmStudioCancelTest.kt`.
3. **[Rule 2] Forward-compat fix for superseded migration gate (Task 3)** —
   the v16 registration assertion in `Migration15To16StaticTest` broke on the
   v17 bump; converted to `isAtLeast(16)` mirroring the established 14→15
   precedent. File: `Migration15To16StaticTest.kt`.
4. **[Rule 2] Added `Migration16To17StaticTest` (Task 3)** — not named in the
   plan artifacts, but required by the project migration-gate convention
   (every migration ships its static gate). Mirrors the 15→16 pattern.

## Out of scope / follow-ups (not fixed)

- `UnifiedSelectorScreen` keeps its own private legacy `DownloadCard` (not the
  shared component) — a third download UI copy; pause wiring there is a separate
  task.
- `EndpointsScreen` Activate stays activate-only (no chat navigation) — the
  "Models & Endpoints" scope here is the Models screen's local + endpoint
  "Use in chat" actions.
- No adb in this environment: all 7 items need the on-device confirmation
  listed above (unit tests cover logic/pass-through only).
