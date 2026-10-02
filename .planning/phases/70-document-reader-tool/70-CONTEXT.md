# Phase 70: Document Reader Tool - Context

**Gathered:** 2026-10-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Users can pick a text document via the system picker and get answers grounded in its bounded content — locally and on remote endpoints (remote tools[] mapping alongside the local loop). Size cap + truncation envelope, least-privilege read-only access, graceful fallbacks. The TOOL-03 fallback (unit converter) stays contingency-only: implemented if and only if readTextFile proves unfit at plan time. Independent tool track — no dependency on Phases 67–69.
</domain>

<decisions>
## Implementation Decisions

### Document Picking UX
- Attach affordance in the chat input (paperclip-style) opening the system picker (SAF — user grants per file, least-privilege, read-only)
- Plain-text family first (.txt/.md, size-capped); binary formats (PDF/DOCX) get a graceful "not yet supported" message
- Pre-read size check; over-cap files ground with a truncation-envelope notice ("showing first N chars") — never silent, never blocked
- One document per turn (bounded, simple); a new pick replaces the attachment

### Grounding Presentation
- Bounded `[DOCUMENT CONTEXT]` block fused into the prompt — mirrors the `[WEB CONTEXT]` pipeline conventions (v2.2/v2.3)
- Explicit header/footer with filename + "truncated at N chars" marker so the model knows the bounds
- Chip/citation naming the file in the turn — mirrors the Fuentes pattern
- Failed/empty reads fall back gracefully (model-only answer + notice); send is never dead-ended

### Remote Endpoints
- Remote `tools[]` mapping alongside the local loop (readTextFile as a callable tool per provider capability)
- Same UX on remote (pick → ground → answer); provider differences hidden behind the capability matrix
- Same size cap + truncation envelope, context-window aware per provider
- Same graceful fallback as local (notice + model-only answer), never a hard error

### Trust Boundary + Scope
- System picker only — no broad storage permission, read-only, per-file user grant
- TOOL-03 contingency-only: the planner implements the unit converter if and only if readTextFile proves unfit, with evidence; otherwise TOOL-03 stays unbuilt
- Read-only tool — side-effecting tools (write/delete/send/network) are out of scope (need approval UX, own milestone)
- Attachment is per-turn; picked content is never stored beyond the turn's context (no document library)

### the agent's Discretion
- Exact attach-icon placement, size-cap value (within context-window reason), and truncation-envelope wording within existing grounding/Fuentes conventions — follow the web-grounding pipeline patterns and Material 3.
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- Web-grounding pipeline (`data/grounding/`): bounded fetch → `[WEB CONTEXT]` fused blocks, hijack sanitization, model-window-aware budget, partial grounding, offline fallback — the direct pattern analog for `[DOCUMENT CONTEXT]`
- Fuentes numbered-source surfaces + per-source cards — pattern for the document chip/citation
- Provider capability matrix + remote tools handling (v2.4 agentic loop heritage, as retained post-v2.2) — extension point for the remote readTextFile mapping
- Chat input row (attach point beside image/dictation/voice affordances); Snackbar/notice channels; EN+ES string conventions

### Established Patterns
- Heuristic zero-dependency pipelines (OkHttp fetch only, Jsoup parse-only); least-privilege access; graceful model-only fallbacks
- VM-owned flows; StateFlow UI state; Room for persistent metadata (none needed here — per-turn attachment)
- Zero new Gradle deps unless the planner proves necessity with evidence (TOOL-03 contingency rule generalizes: no dependency without a fitness finding)

### Integration Points
- Attach button in chat input → SAF picker → bounded read → `[DOCUMENT CONTEXT]` fusion → local inference path AND remote tools[] path
- Capability matrix → per-provider readTextFile availability → same UX, provider-aware envelopes
</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches within the web-grounding and Fuentes conventions. Keep the tool read-only and the envelope explicit so the model always knows the content bounds.
</specifics>

<deferred>
## Deferred Ideas

- Binary document extraction (PDF/DOCX) — graceful "not yet supported", not v3.1
- Multi-document turns with fused context — declined (one per turn)
- Document library with re-grounding — declined (per-turn attachment only)
- Side-effecting tools (write/delete/send/network) — own milestone with approval UX
- TOOL-03 unit converter unless readTextFile proves unfit at plan time (contingency-only)
</deferred>
