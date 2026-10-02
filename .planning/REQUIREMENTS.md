# Requirements: Warped

**Defined:** 2026-10-02 (fresh file after v3.0 archive)
**Core Value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.

## Next Milestone Requirements

Requirements for the next milestone will be defined via `/gsd-new-milestone`.

## Future Requirements (carried from v3.0 archive)

### Polish follow-ups

- **POL-01**: Tune Play Review trigger thresholds (turns/days/cooldown) from real conversion data
- **POL-02**: `EXTRA_PREFER_OFFLINE` dictation hint evaluation
- **POL-03**: Pixel 7 reference benchmark numbers (PERF-16 + PERF-12/13, CI-gated carry-over)
- **POL-04**: Standing release-UAT device smokes (v2.2–v2.4 backlog)

## Out of Scope (carried from v3.0 archive)

| Feature | Reason |
|---------|--------|
| Visible star-rating button wired to `launchReviewFlow()` | Play quota silently suppresses the dialog → looks broken; violates Play guidance |
| Audio/voice messages | User explicitly deferred — dictation to input only for v3.0 |
| Offline STT engines (Vosk/whisper) / ML Kit | 50–150 MB for a nice-to-have; ML Kit banned by dependency gate |
| New screens/destinations for CTAs | All CTAs reuse existing navigation callbacks per Material empty-state guidance |
| Certificate pinning / Play Integrity | Deferred since v1.5, unchanged |

## Traceability

No active milestone. Traceability will be defined with the next milestone's roadmap.

---
*Requirements reset: 2026-10-02 after v3.0 archive*
