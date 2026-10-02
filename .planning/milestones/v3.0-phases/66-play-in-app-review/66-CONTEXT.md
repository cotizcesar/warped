# Phase 66: Play In-App Review - Context

**Gathered:** 2026-10-02
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous, 2 areas accepted by user)

<domain>
## Phase Boundary

Add ambient Play In-App Review at chat success moments plus an always-reachable Play Store entry. Smallest slice, fully independent — no shared files with Phase 65. Review prompt is never behind a visible "Rate" button, never blocks chat, and fails silent on quota/API errors. Store entry lives in Settings so rating is reachable even when the dialog is quota-suppressed.

</domain>

<decisions>
## Implementation Decisions

### Trigger policy
- Ambient prompt after a few completed chat turns + cooldown, persisted (e.g. DataStore). Exact thresholds at the agent's discretion — modest (single-digit turns, weeks-scale cooldown).
- Never behind a visible "Rate" button — strictly ambient per RATE-01.
- Quota suppression and API failures fully silent — no fallback dialog.
- Review flow fire-and-forget so chat send/streaming never stalls waiting on it.

### Store entry
- Always-reachable Store entry lives in Settings (footer/about row), opening the `market://` listing with an `https://` fallback.
- Agent drafts the entry copy EN+ES; user reviews wording in code review.
- Play review dependency added via version catalog (`review-ktx`); no other new dependencies.

### the agent's Discretion
- Exact turn-count/cooldown/max-prompt thresholds and DataStore keys.
- Exact helper shape (e.g. ReviewHelper owned by ViewModel vs application-scoped) — must not stall chat; follow codebase conventions.
- Exact entry copy wording EN+ES.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ui/settings/SettingsScreen.kt` — footer/about row home for the Store entry (Phase 64 just cleaned this surface).
- `ui/chat/ChatViewModel.kt` — completed-turn signal lives here (send/stream completion); hook the turn counter without touching streaming.
- DataStore Preferences — check existing keys file for the cooldown/counter storage convention.
- `libs.versions.toml` — version catalog for the `com.google.android.play:review-ktx` dependency.
- `ui/navigation/` — no new destination needed (external intent only).

### Established Patterns
- Fire-and-forget side work off the UI thread (coroutine scope, never block send/stream).
- EN+ES string pairs for user-facing copy.
- External intents: check for existing `market://`/browser intent precedent; include https fallback.

### Integration Points
- ChatViewModel turn-completion → counter increment → eligibility check → ReviewManager requestReview/launchReview (silent on failure).
- Settings footer row → market intent with fallback.
- No shared files with Phase 65 (independent slice).

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches. Success criteria are the contract: ambient prompt at success moments, always-reachable Store entry, chat never stalls.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
