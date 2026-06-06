# Phase 42 — Prompt Lab

**Requirements:** PROMPT-01..06
**Plans:** 3 (42-01 scaffold, 42-02 UI, 42-03 wire)

## Decisions

- **Templates as `@IntoSet`** (PROMPT-04): each `PromptTemplate` is a `@Provides @IntoSet @Named("promptTemplate")` so the VM injects `Set<@JvmSuppressWildcards PromptTemplate>` and iterates. Matches the Gallery pattern: drop a new file → it shows up.
- **Single-turn, no conversation state** (PROMPT-03): PromptLabViewModel holds `input: String`, `selectedTemplateId: String?`, `output: String`, `isRunning: Boolean` — no message history, no streaming buffer beyond the final `String`.
- **Output via existing `MarkdownText`** (PROMPT-05): reuse `com.warped.ui.chat.components.MarkdownText` so syntax highlighting, code blocks, and tables render the same as chat. The output is just a `String`, not a stream.
- **LlmModelHelper reuse** (PROMPT-06): resolve via the same `ProviderRouter.resolveLocalHelper(LITE_RT_LM, …)` used by `ChatViewModel`. The VM has no new inference plumbing.
- **Templates shipped** (PROMPT-02): 7 — rewrite, summarize, extract-key-points, code-explain, translate, sentiment, table-to-json. Each is a 2-line `systemPrompt` + `userPromptTemplate` pair.
- **Layout** (PROMPT-03): `Row` with two `Column`s of equal weight, `OutlinedTextField` on the left, scrolling `MarkdownText` on the right. On phones in portrait the row collapses to a single column (responsiveness).
- **No Hilt subcomponent** — `PromptLabTaskModule` is `@Module @InstallIn(ViewModelComponent::class)` so the `Set<PromptTemplate>` is provided per-ViewModel scope and not at app startup (cheaper).
