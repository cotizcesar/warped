# Phase 64: Drawer + Settings + Help + Funnel Polish - Context

**Gathered:** 2026-10-02
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous, 4 areas accepted by user)

<domain>
## Phase Boundary

Polish the chat drawer, Settings, Help, catalog, and Models & Endpoints surfaces so users navigate without dead ends or clutter — after the Phase 63 Tavily cut. Chat drawer gets an empty-state download CTA, uniform footer, drawer-owned bulk delete, and no Web Options. Settings loses the key-deletion affordance and Data section (removals only). Help is rewritten short and minimal post-Tavily (EN+ES). Catalog and Models & Endpoints gain funnel CTAs (Use in Chat, download/endpoint empty states) reusing existing components.

</domain>

<decisions>
## Implementation Decisions

### Chat drawer
- Empty model drawer shows an inline "Download a model" button navigating to Model Catalog.
- Delete-all-chats sits at the drawer bottom above the Models footer, keeping the existing confirm dialog intact.
- Footer shows New Chat, Models, Help, Settings in uniform text size; Web Options removed from the drawer (web grounding lives in Settings only).

### Settings cleanup
- Remove the key-deletion affordance and the Data section entirely, no replacement UI. Key rotation stays via endpoint edit-overwrite; programmatic `deleteKey` retained for endpoint-deletion flows.
- Strictly removals — no regrouping or restructuring of remaining settings.
- Bulk chat delete lives only in the drawer.

### Help rewrite
- Short, minimal single-scroll Help (EN+ES), no Tavily/key steps; builds on Phase 63's `help_s7_step5` minimal fix — broader rewrite still pending (flagged in 63-02 SUMMARY).
- Numbered-steps structure matching the current HelpScreen pattern.
- Agent drafts EN copy and mirrors ES; user reviews wording in code review.

### Funnel CTAs
- "Use in Chat" on downloaded catalog models activates the model AND navigates to chat.
- Empty Models & Endpoints shows "Download a local model" (navigates to catalog) and "Add a new Endpoint" (opens endpoint creation) buttons.
- Reuse existing ModelCard/button components for all new CTAs — no new design language.

### the agent's Discretion
- Exact CTA placement within ModelCard/catalog rows and empty-state layouts — follow existing composable patterns.
- Exact Help copy wording (EN draft + ES mirror) — keep short, minimal, to-the-point.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ui/chat/ChatScreen.kt` + `ui/chat/components/ConversationList.kt` — drawer structure to modify.
- `ui/settings/SettingsScreen.kt` + `SettingsViewModel.kt` + `SettingsUiState.kt` — card/section removal; Phase 63 already removed the Tavily card there (follow that diff as the pattern).
- `ui/help/HelpScreen.kt` — numbered-steps pattern to keep; EN `help_s*` + ES strings in `main/res/values(-es)/strings.xml`.
- `ui/components/ModelCard.kt`, `WarpedAlertDialog.kt` — reuse for CTAs and confirm dialog.
- `ui/huggingface/HuggingFaceScreen.kt` + `CatalogViewModel.kt` — catalog rows for "Use in Chat".
- `ui/models/ModelsScreen.kt` + `ui/endpoints/EndpointsScreen.kt` — empty states for FUN-02/03.
- `ui/navigation/NavGraph.kt` + `Screen.kt` — navigation targets for all CTAs.

### Established Patterns
- Settings state via ViewModel + UiState; card removal precedent from Phase 63 (Tavily card + EN+ES strings).
- EN+ES string pairs required for every user-facing copy change.
- Activation + navigation: check how model activation currently routes to chat before adding "Use in Chat".

### Integration Points
- NavGraph routes: Model Catalog (HuggingFace destination), endpoint creation, chat with activated model.
- Confirm dialog for delete-all-chats already exists in drawer flow — keep, reposition only.

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches. Success criteria are the contract: five drawer/settings/help truths plus empty-state navigation, all with no dead ends.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
