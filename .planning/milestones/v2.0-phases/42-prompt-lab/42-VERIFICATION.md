# Phase 42 — Prompt Lab

**Status:** ✅ Complete
**Plans executed:** 3/3
**Plans:** 42-01 (scaffold), 42-02 (UI), 42-03 (Markdown + NavGraph)
**Requirements:** PROMPT-01..06 ✅

## Plan-by-plan

### 42-01: domain + Hilt scaffold (PROMPT-01, PROMPT-04)
- `domain/prompt/PromptTemplate.kt`: data class with `id`, `name`, `description`, `systemPrompt`, `userPromptTemplate` lambda, `outputMarkdown`, `requiresLanguage`.
- `domain/prompt/PromptTemplateConfigs.kt`: 7 templates via `@Provides @IntoSet @Named("promptTemplate")` (rewrite, summarize, extractKeyPoints, codeExplain, translate, sentiment, tableToJson). All installed in `ViewModelComponent`.
- `di/PromptLabTaskModule.kt`: documentation-only object (multibound set is consumed directly in the VM).
- `compileDebugKotlin` ✅.

### 42-02: ViewModel + Screen (PROMPT-03, PROMPT-06)
- `PromptLabViewModel` (@HiltViewModel): injects `Set<@JvmSuppressWildcards PromptTemplate>` from the multibinding; resolves the active model via `ProviderRouter.resolveLocalHelper()`. Single-turn `runInference()` collects `StreamToken.Delta` into a `StringBuilder` that becomes the `output` state. Exposes `codeTheme` + `codeFontScale` as StateFlows.
- `PromptLabScreen`: `Scaffold` + `TopAppBar`, template dropdown, optional target-language field (when the template requires it), side-by-side `Row` on `screenWidthDp >= 600` (stacked on phones), input column with `OutlinedTextField` + Run button, output column. Error feedback via `Snackbar`.
- `TemplateDropdown`: ExposedDropdownMenuBox listing all templates (sorted by name).
- `compileDebugKotlin` ✅.

### 42-03: MarkdownText + NavGraph (PROMPT-02, PROMPT-05)
- Output column renders through the existing `MarkdownText` (v1.6), passing `codeTheme` and `codeFontScale` from `AdvancedPreferences`. `isStreaming = ui.isRunning` enables the auto-scroll effect while tokens flow.
- `NavGraph.kt`: `composable<Screen.PromptLab>` now invokes `PromptLabScreen()`. `PromptLabPlaceholderScreen` is gone.
- `PlaceholderScreens.kt` deleted (both placeholders replaced).
- **Hilt fix during 42-03**: the original `PromptLabTaskModule` had a `@Provides @Named("promptTemplate") fun provideAllTemplates(set: Set<...>)` that conflicted with the `@IntoSet @Named("promptTemplate")` multibinding. Replaced with a documentation-only object; the VM injects the multibound set directly.
- `assembleDebug` ✅.

## Notes

- All 7 templates use English system prompts; localization deferred.
- `ProviderRouter.resolveLocalHelper()` is restricted to `ProviderType.LITE_RT_LM` / `LOCAL`. Remote-only models surface an error ("Select a model first"). PROMPT-06 only requires reuse of the local path; remote routing for prompt lab is a future enhancement.
- The `PlaceholderScreens.kt` file was the last consumer of the `com.warped.ui.navigation.PlaceholderContent` composable; deleting it doesn't break any other screen.

## Commits

- `2d1f8ac` (docs) → `42-01..03-PLAN.md`
- `7e0c33a` (42-01): PromptTemplate interface + 7 templates + @IntoSet Hilt scaffold
- `9f2d4a3` (42-02): PromptLabScreen + ViewModel + side-by-side layout
- `7bf6955` (42-03): prompt lab output via MarkdownText + wire into NavGraph
