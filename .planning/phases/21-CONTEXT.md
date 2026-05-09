# Phase 21: Step Content & Context - Context

**Gathered:** 2026-05-09
**Status:** Ready for planning

<domain>
## Phase Boundary

Populate all 9 wizard steps with icons, titles, warm Spanish descriptions, and context-aware content variants. Read app state (models, endpoints, chats, presets) at wizard open time via snapshot to adapt messaging per step. Deliver reusable PageIndicator component. CTA buttons render per step with correct labels; navigation wiring deferred to Phase 22.

</domain>

<decisions>
## Implementation Decisions

### WizardStep Structure & Content
- `enum class WizardStep` with 9 entries: `WELCOME, ENGINES, GGUF_DOWNLOAD, LITERT_LM, LOCAL_CHAT, REMOTE_PROVIDERS, REMOTE_CHAT, PRESETS, HISTORY`.
- Each entry has: `title: String` (Spanish), `description: String` (2-3 sentences, warm tone), `icon: ImageVector`, `ctaLabel: String`, `ctaRoute: String` (Screen reference for Phase 22 wiring).
- Material icons: WELCOME=AutoAwesome, ENGINES=Memory, GGUF_DOWNLOAD=CloudDownload, LITERT_LM=Android, LOCAL_CHAT=Chat (AutoMirrored), REMOTE_PROVIDERS=Dns, REMOTE_CHAT=Cloud, PRESETS=Tune, HISTORY=History.
- Tone: second-person informal Spanish (tú), warm and encouraging. Brief — 2-3 sentences per step. Avoids technical jargon.

### Context-Aware Content Variants
- Count threshold: 0 → "no data" variant (inviting), >=1 → "has data" variant (celebratory). Simple binary — no additional tiers.
- Steps with context: 3 (GGUF models), 4 (LiteRT-LM models), 6 (remote endpoints), 8 (presets), 9 (chat history). Steps 1-2, 5, 7 always show generic content (no quantifiable app state).
- "Has data" format: "Ya tienes {N} modelos GGUF descargados. ¡Explora el catálogo para encontrar más!" — acknowledges progress, invites exploration.
- "No data" format: "Descarga tu primer modelo GGUF desde Hugging Face." + brief encouragement — inviting, not shaming.

### StepContent Composable Design
- Single `StepContent` composable parameterized by `step: WizardStep`, `contextData: WizardContextData`, `onCtaClick: () -> Unit`.
- Layout: Column with large centered icon (48dp), bold title (22sp), secondary description text (15sp), CTA Button at bottom. Uses Warped dark theme colors.
- Context variants: simple `if/else` within composable based on `contextData` fields. Max 2 variants per step.
- CTA button renders inside StepContent (not in WizardScreen bottom bar). Phase 22 wires callback to NavGraph navigation.

### Context Data Gathering & Components
- Injected repos: `LocalModelRepository`, `EndpointRepository`, `ChatRepository`, `PresetRepository` — covers all quantifiable app state.
- Snapshot: `combine(flows).first()` in WizardViewModel `init` block. Single snapshot, not continuously observed (WZCT-03).
- `WizardContextData` data class in WizardUiState.kt: `ggufModelCount`, `litertlmModelCount`, `endpointCount`, `chatCount`, `presetCount`.
- PageIndicator extracted to `ui/components/PageIndicator.kt` — reusable. Parameters: `pageCount: Int, currentPage: Int, modifier: Modifier`.

### the agent's Discretion
None — all questions had definitive answers.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Phase 20 artifacts:** WizardScreen.kt (HorizontalPager, TopAppBar with dots, bottom bar), WizardViewModel.kt (page management, stepLabels), WizardUiState.kt (currentPage), WizardPreferences.kt (skippedSteps).
- **WarpedAlertDialog** — existing branded dialog component.
- **Theme palette:** `Color(0xFF1F1F1E)` (dark bg), `Color(0xFF2B2B29)` (card bg), `Color(0xFFD97757)` (accent), `Color(0xFFECECEC)` (primary text), `Color(0xFF9CA3AF)` (secondary text).
- **Repositories:** `LocalModelRepository.observeModels(): Flow<List<LocalModel>>`, `EndpointRepository.observeEndpoints(): Flow<List<Endpoint>>`, `ChatRepository.observeConversations(): Flow<List<Conversation>>`, `PresetRepository.observePresets(): Flow<List<Preset>>`.

### Established Patterns
- **ViewModel snapshot:** `.first()` for one-shot reads (not continuous observation).
- **Data classes:** flat, all-default values, separate files.
- **Enums:** `ProviderType` enum with constructor fields is the closest analog to WizardStep.

### Integration Points
- `WizardScreen.kt` — replace placeholder content with `StepContent` composable. Remove stepLabels/stepDescriptions from ViewModel, use WizardStep enum instead.
- `WizardViewModel.kt` — inject repos, add `WizardContextData` snapshot, expose WizardStep list.
- `WizardUiState.kt` — add `contextData: WizardContextData` field.
- `NavGraph.kt` — passes `onNavigate` callbacks to WizardScreen in Phase 22.

</code_context>

<specifics>
## Specific Ideas

No specific requirements beyond ROADMAP phase description and success criteria. All grey areas resolved via smart discuss.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
