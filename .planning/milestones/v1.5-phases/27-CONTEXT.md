# Phase 27: Wizard Update - Context

**Gathered:** 2026-05-09
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase — discuss skipped)

<domain>
## Phase Boundary

Update the onboarding wizard to reflect LiteRT-LM as the sole local inference engine. Remove GGUF-specific step, consolidate model download steps, update copy and step count (9→8).

This is Phase 27 — the final piece of the v1.5 GGUF removal puzzle. The wizard code was intentionally excluded from Phase 23 (GGUF Removal) and now gets updated.
</domain>

<decisions>
## Implementation Decisions

### the agent's Discretion
All implementation choices are at the agent's discretion. Requirements from REQUIREMENTS.md (WZRD-01 through WZRD-03) are the spec.

Key decisions:
- Remove WizardStep.GGUF_DOWNLOAD entirely; consolidate LiteRT-LM step to cover the download flow
- Update WizardViewModel to remove ggufModelCount; Simplify wizard context state
- Update StepContent to remove GGUF-specific badges and references
- Update string resources (both en and es) — remove wizard GGUF strings
</decisions>

<code_context>
## Existing Code Insights

### Files to modify:
- `app/src/main/java/com/warped/ui/wizard/WizardStep.kt` — Remove GGUF_DOWNLOAD enum, renumber steps
- `app/src/main/java/com/warped/ui/wizard/WizardViewModel.kt` — Remove ggufModelCount logic
- `app/src/main/java/com/warped/ui/wizard/WizardUiState.kt` — Remove ggufModelCount field
- `app/src/main/java/com/warped/ui/wizard/StepContent.kt` — Remove GGUF badges, update descriptions
- `app/src/main/java/com/warped/ui/wizard/WizardScreen.kt` — Adjust page count (9→8)
- `app/src/main/res/values/strings.xml` — Remove wizard GGUF strings
- `app/src/main/res/values-es/strings.xml` — Remove wizard GGUF strings (Spanish)

### Requirements:
- **WZRD-01**: Wizard steps updated — GGUF_DOWNLOAD removed, model download steps consolidated, step count 9→8, descriptions updated
- **WZRD-02**: Wizard state simplified — ggufModelCount removed, GGUF count logic removed, context badges updated
- **WZRD-03**: Step content updated — GGUF badges/descriptions removed, engine comparison simplified to LiteRT-LM only
</code_context>

<specifics>
## Specific Ideas

Remove wizard GGUF strings that were left in Phase 23:
- wizard_step_2_desc (engine comparison → LiteRT-LM only)
- wizard_step_3_title, wizard_step_3_desc, wizard_step_3_desc_has, wizard_step_3_desc_no (all GGUF download)
- wizard_badge_has_gguf, wizard_badge_no_gguf

Consolidate so the wizard explains only LiteRT-LM as the local engine.
</specifics>

<deferred>
## Deferred Ideas

None — this is the final GGUF cleanup.
</deferred>
