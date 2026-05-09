# Phase 21: Step Content & Context — Plan

**Created:** 2026-05-09
**Status:** Executing
**Requirements:** WZST-01..11, WZCT-01, WZCT-02 (13)

## Tasks

### Task 1: WizardStep Enum
- **File:** `ui/wizard/WizardStep.kt`
- **Action:** NEW
- **What:** Enum with 9 entries: WELCOME, ENGINES, GGUF_DOWNLOAD, LITERT_LM, LOCAL_CHAT, REMOTE_PROVIDERS, REMOTE_CHAT, PRESETS, HISTORY. Each has `title`, `description`, `icon`, `ctaLabel`, `ctaRoute`. Warm Spanish text, 2-3 sentences per description. Icons: AutoAwesome, Memory, CloudDownload, Android, Chat, Dns, Cloud, Tune, History.

### Task 2: PageIndicator Component
- **File:** `ui/components/PageIndicator.kt`
- **Action:** NEW
- **What:** Reusable composable extracted from Phase 20 inline code. Parameters: `pageCount`, `currentPage`, `modifier`, `activeColor`, `inactiveColor`, `activeSize`, `inactiveSize`. Animated dot size transition. Replaces inline dot rendering in TopAppBar.

### Task 3: StepContent Composable
- **File:** `ui/wizard/StepContent.kt`
- **Action:** NEW
- **What:** Single composable parameterized by `WizardStep` + `WizardContextData` + `onCtaClick`. Layout: large icon (48dp), title (22sp bold), context-aware description (15sp), context badge (data count), CTA button. Context variants: 0 vs >=1 threshold for steps 3,4,6,8,9. Badge shows count with encouraging message.

### Task 4: Update WizardUiState
- **File:** `ui/wizard/WizardUiState.kt`
- **Action:** MODIFY
- **What:** Add `contextData: WizardContextData` field. Add `WizardContextData` data class with `ggufModelCount`, `litertlmModelCount`, `endpointCount`, `chatCount`, `presetCount`.

### Task 5: Update WizardViewModel
- **File:** `ui/wizard/WizardViewModel.kt`
- **Action:** MODIFY
- **What:** Inject `LocalModelRepository`, `EndpointRepository`, `ChatRepository`, `PresetRepository`. Add `steps` property (WizardStep.entries). Add `snapshotContext()` using `combine(flows).first()`. Replace `stepKeys`/`stepLabels`/`stepDescriptions` with WizardStep-based lookups. Update `skipCurrentStep()` to use WizardStep name for key.

### Task 6: Update WizardScreen
- **File:** `ui/wizard/WizardScreen.kt`
- **Action:** MODIFY
- **What:** Replace inline placeholder card content with `StepContent` composable. Replace inline page dots with `PageIndicator` component. Add `onNavigate: (String) -> Unit` parameter (default no-op for Phase 21, wired in Phase 22). Use `viewModel.steps` for data instead of hardcoded lists.

## Verification Against Success Criteria

| Criterion | How Verified |
|-----------|-------------|
| 1. Each step renders with distinct icon, title, 2-3 sentence description | WizardStep enum defines all content; StepContent renders icon + title + description |
| 2. All text uses warm, friendly Spanish tone | All descriptions reviewed: informal tú, encouraging, brief |
| 3. Step 3 shows "Descarga tu primer modelo" if 0 models | contextDescription() returns first-download variant when ggufModelCount == 0 |
| 4. Step 3 shows "Ya tienes 3 modelos" if 3 models | contextDescription() returns has-data variant with count |
| 5. CTA buttons render with correct labels; navigation deferred | CTA Button in StepContent with ctaLabel; onNavigate callback default no-op |
