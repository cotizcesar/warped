# Quick Plan — langmatch-i18n-paragraphs

Date: 2026-09-29
Workstreams: (A) language-match prompt rule · (B) bilingual EN+ES sweep · (C) paragraph spacing

## Locked decisions (NON-NEGOTIABLE)

- **A. Language-match rule:** model MUST reply in the user's language (user writes Spanish → answer in Spanish, no English lead-ins like "Based on the search results,"). One explicit sentence added to BOTH prompts: `GroundingPrompt.SYSTEM_PROMPT` and `LiteRTLmProvider.TOOL_USE_SYSTEM_HINT`. Minimal edit, update prompt tests.
- **B. Bilingual EN+ES:** product default English (`res/values/` canonical), full Spanish via `res/values-es/` restored with 1:1 key parity (155 keys baseline + any new keys since). Every hardcoded user-visible string moves to resources with EN + ES values. System locale selection, Android standard — NO in-app language switcher. Detection patterns, logs, comments, code identifiers, model names, URLs NEVER translated. Tests asserting hardcoded copy updated to resource-based or new-copy assertions.
- **C. Paragraph spacing:** `MarkdownText` paragraphs get more bottom spacing — single value bump **4.dp → 8.dp** on the outer Column `Arrangement.spacedBy` (line 88), both streaming + settled paths (single shared composable, one edit covers both). List-internal spacing (`6.dp`, line 136) and list padding untouched.

## Out of scope

New locales beyond EN/ES, RTL, font changes, download engine, grounding logic changes.

## Must-haves (goal-backward)

- Truth: a Spanish user question answered with web grounding comes back in Spanish with no English lead-in.
- Truth: with system locale ES, every screen (chat, models, endpoints, selector, settings, help, wizard, presets, benchmark, prompt lab, HF catalog, dialogs, toasts, snackbars, a11y labels) shows Spanish; with EN locale, English.
- Truth: paragraphs in rendered chat answers have visibly larger gaps than before.
- Artifacts: `GroundingPrompt.kt` + `LiteRTLmProvider.kt` rule sentences; `res/values-es/strings.xml` full parity; no hardcoded user-facing literals in `app/src/main/java`; `MarkdownText.kt` `spacedBy(8.dp)`.
- Key links: prompt constants → pinned verbatim by `LiteRTLmLoopTest` / asserted by `GroundingPromptTest`; string resources → `stringResource()` call sites; parity test → CI gate on key drift.

---

## Task 1 — Language-match rule in both prompts (A)

**Files:**
- `app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt` (SYSTEM_PROMPT, ~line 12-17)
- `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt` (TOOL_USE_SYSTEM_HINT, ~line 96-99)
- `app/src/test/java/com/warped/data/grounding/GroundingPromptTest.kt`
- `app/src/test/java/com/warped/data/local/inference/LiteRTLmLoopTest.kt` (pins hint verbatim — update expected value)

**Action:**
1. Append one sentence to `SYSTEM_PROMPT`: `Always reply in the same language the user wrote in.` (keep existing sentences verbatim; this is a minimal additive edit per D-A).
2. Append the equivalent clause to `TOOL_USE_SYSTEM_HINT`: e.g. `Always reply in the same language the user wrote in.` Keep it short — the hint is pinned short on purpose.
3. Update `GroundingPromptTest` — add assertion that `SYSTEM_PROMPT` contains the language-match sentence and that `augment()` output carries it.
4. Update `LiteRTLmLoopTest` expected verbatim hint string.
5. Do NOT touch grounding logic, block fusion, arming, or loop behavior.

**Verify:** `./gradlew :app:testDebugUnitTest --tests "com.warped.data.grounding.GroundingPromptTest" --tests "com.warped.data.local.inference.LiteRTLmLoopTest"`

**Done:** Both prompt constants contain the language rule; both prompt test suites green.

---

## Task 2 — Bilingual EN+ES sweep: restore values-es + eliminate hardcoded strings (B)

**Files:**
- `app/src/main/res/values/strings.xml` (canonical EN, 155 keys — add new keys for every hardcoded string below)
- `app/src/main/res/values-es/strings.xml` (CREATE — restore pre-sweep content from `git show c5f200a6^:app/src/main/res/values-es/strings.xml` as the base: 155 keys with 1:1 parity, then add ES values for every new key)
- All Kotlin files listed in the inventory table (call sites switch to `stringResource(R.string.*)`)
- New or updated test: resource parity test asserting `values` vs `values-es` key-set equality (FAIL on drift)
- Existing tests asserting hardcoded copy (grep `assertThat.*"[A-Z]` / `isEqualTo("` in `app/src/test`) — update to new copy or resource-based assertions

**Action:**
1. Restore `values-es/strings.xml` from pre-sweep commit `c5f200a6^` (full 155-key file; prior product copy e.g. "Fuentes" style is the tone reference). Verify zero key drift vs current EN file — sweep commit changed values' *values*? No: `git log c5f200a6..HEAD -- values/strings.xml` is EMPTY, so current EN keys == pre-sweep keys. Restore is exact; confirm with diff of key names.
2. Move every hardcoded string in the inventory table to resources: add EN key to `values/strings.xml`, ES translation to `values-es/strings.xml`, replace call site with `stringResource()` (composables) or `context.getString()` (Toast/VM — pass `Context` or resolve at emit site; for `ChatEvent.Snackbar` VM strings, resolve in ViewModel via injected `Application` context or move resolution to ChatScreen; keep it simple and consistent with existing patterns).
3. Format strings with args use `%1$s`/`%1$d` placeholders in both locales. Plurals for dynamic counts reuse the existing `wizard_*_singular/plural` pattern where applicable, else plain format strings.
4. NEVER translate: detection patterns, log/Timber messages, comments, code identifiers, model names (LiteRT-LM, GGUF excluded anyway), URLs, `••••••••` mask, `+`/`•` symbols.
5. Add parity test (new file, e.g. `app/src/test/java/com/warped/i18n/StringResourceParityTest.kt`): parse both XMLs, assert identical key sets; fail listing missing/extra keys. JVM-only (java.xml), no device needed.
6. Run the full suite + `assembleDebug`.

**String inventory table** (file → literal → EN key/value → ES value; rioplatense-neutral Latin American Spanish, matching prior copy tone):

| # | File:line | Hardcoded literal | EN key + value | ES value |
|---|-----------|-------------------|----------------|----------|
| 1 | CodeBlock.kt:449 | `contentDescription = "Syntax issue"` | `cd_syntax_issue` / "Syntax issue" | "Problema de sintaxis" |
| 2 | CodeBlock.kt:503,509 | `"Copied"` / `"Copied!"` | `copied` / "Copied!" | "¡Copiado!" |
| 3 | CodeBlock.kt:519 | `"Copy code"` | `cd_copy_code` / "Copy code" | "Copiar código" |
| 4 | ConversationList.kt:28 | `"+ New Chat"` | reuse `new_chat` ("New Chat") with "+" prefix in layout, or `new_chat_plus` / "+ New Chat" | "+ Nuevo chat" |
| 5 | ModelSelector.kt:222 | `"Selected"` | `cd_selected` / "Selected" | "Seleccionado" |
| 6 | MessageBubble.kt:94 | Toast `"Copied!"` | reuse `copied` | "¡Copiado!" |
| 7 | MessageBubble.kt:523/532/542 | `"Close"` / `"Full image"` / `"Image (tap to enlarge)"` | reuse `cancel`? NO — add `cd_close`/`cd_full_image`/`cd_image_tap_enlarge` | "Cerrar" / "Imagen completa" / "Imagen (tocá para ampliar)" |
| 8 | BrowserIntents.kt:30,42,49 | `"Invalid link."` / `"No browser found to open the link."` (×2) | `toast_invalid_link` / `toast_no_browser` | "Enlace inválido." / "No se encontró un navegador para abrir el enlace." |
| 9 | OgSourceCard.kt:98,110,171,221,236,267 | `"Source preview $number: $displayTitle"`, `"Open in browser"`, `"Open source $number in browser"`, `"Preview source $number"`, `"Open source $number: $displayTitle"` | `cd_source_preview` (`Source preview %1$d: %2$s`), `cd_open_in_browser`, `cd_open_source_browser` (`Open source %1$d in browser`), `cd_preview_source` (`Preview source %1$d`), `cd_open_source_title` (`Open source %1$d: %2$s`) | "Vista previa de fuente %1$d: %2$s", "Abrir en el navegador", "Abrir fuente %1$d en el navegador", "Vista previa de fuente %1$d", "Abrir fuente %1$d: %2$s" |
| 10 | ChatScreen.kt:336 | `"Running tool: $status. "` + (continuation — read full literal at call site) | `cd_running_tool` / "Running tool: %1$s. …" (mirror existing text) | "Ejecutando herramienta: %1$s. …" |
| 11 | ChatScreen.kt:430 | `"Warped"` (logo cd) | `cd_logo` / "Warped" (same both locales) | "Warped" |
| 12 | ChatScreen.kt:548,555,580 | `Text("Dismiss")` ×3 | reuse `dismiss` | "Descartar" |
| 13 | ChatScreen.kt:583 | `"The model for this conversation is no longer installed. …"` | `model_no_longer_installed` | "El modelo de esta conversación ya no está instalado. Elegí otro modelo o descargalo de nuevo." |
| 14 | ChatScreen.kt:591/601/606 | `"New model selected"` / `"New Chat"` / `"Cancel"` | `model_selected_new` (new) / reuse `new_chat` / reuse `cancel` | "Nuevo modelo seleccionado" / "Nuevo chat" / "Cancelar" |
| 15 | ChatScreen.kt:686,763,800,807,818,900 | `"Jump to latest message"`, `"Open drawer"`, `"Connection status"`, `"Select model"`, `"Web options"`, `"Thinking. Generating answer."` | `cd_jump_latest`, `cd_open_drawer`, `cd_connection_status`, `cd_select_model`, `cd_web_options`, `cd_thinking_generating` | "Ir al último mensaje", "Abrir panel", "Estado de conexión", "Seleccionar modelo", "Opciones web", "Pensando. Generando respuesta." |
| 16 | ChatScreen.kt:845 | `"Web: Inherit"` | `web_inherit` / "Web: Inherit" | "Web: Heredar" |
| 17 | ChatViewModel.kt:316,325 | `"Web preference updated. Will apply to the next message."` ×2 | `snack_web_pref_updated` | "Preferencia web actualizada. Se aplicará al próximo mensaje." |
| 18 | ChatViewModel.kt:859 | `"Couldn't save the sources. Preview may be unavailable after restart."` | `snack_sources_not_saved` | "No se pudieron guardar las fuentes. La vista previa puede no estar disponible al reiniciar." |
| 19 | ModelParamsDialog.kt:115,118 | `"Save"` / `"Cancel"` | reuse `save` / `cancel` | — (already in ES base) |
| 20 | CapabilityBadges.kt:35,40,45,50 | `"Vision"` / `"Audio"` / `"Thinking"` / `"Tools"` | `badge_vision`, `badge_audio`, `badge_thinking`, `badge_tools` | "Visión", "Audio", "Razonamiento", "Herramientas" |
| 21 | ActiveDownloadCard.kt:58,67,83 | `"Cancel"` / `"Delete"` / `"Delete partial file"` | reuse `cancel` / `delete` / `delete_partial_file` | — (in ES base) |
| 22 | EndpointCard.kt:80,81,84 + :58 | `"Edit"` / `"Activate"` / `"Delete"` / `"Active"` | reuse `edit` / NEW `activate` / reuse `delete` / NEW `badge_active` | "Activar" / "Activo" (rest in base) |
| 23 | EndpointForm.kt:113,121,167,170,193,230,270,275,335 + placeholder | `"Name"` / `"URL"` / `"Cancel"` / `"Save"` / `"Provider Type"` / `"Connection Type"` / `"Model ID"` / `"Select or type model ID"` / `"API Key"` | reuse `name`, `url`, `cancel`, `save`, `provider_type`, `model_id`, `select_or_type_model`, `api_key` / NEW `connection_type` | "Tipo de conexión" (rest in base) |
| 24 | EndpointForm.kt:299,304 | `"Vision"` / `"Tool use"` | reuse `badge_vision` / NEW `cap_tool_use` ("Tool use") | "Uso de herramientas" |
| 25 | EndpointsScreen.kt:26,31,84 | `"Endpoints"` / `"+"` / `"Dismiss"` | reuse title key? add `endpoints_title` / symbol as-is / reuse `dismiss` | "Endpoints" |
| 26 | HelpScreen.kt:33 + body texts (:51,57,217,226,232 — read at call site) | `"Help"` + section bodies | `help_title` (reuse? add) + `help_*` keys per body paragraph | Translate all body copy |
| 27 | HuggingFaceScreen.kt:75,184,185,190,192 | `"Model catalog"` / `"Cancel download?"` / `"The partial file will be deleted."` / `"Cancel download"` / `"Keep"` | NEW `hf_catalog`, `hf_cancel_title`, `hf_cancel_msg`, `hf_cancel_dl`, `hf_keep` | "Catálogo de modelos", "¿Cancelar descarga?", "Se eliminará el archivo parcial.", "Cancelar descarga", "Conservar" |
| 28 | HuggingFaceScreen.kt:325,333 + caps 354,361,368 | `"Downloaded"` / `"Download model"` / `"Vision"` / `"Audio"` / `"Reasoning"` | NEW `hf_downloaded`, `hf_download_model` / reuse badge keys | "Descargado", "Descargar modelo", (caps reused) |
| 29 | HuggingFaceScreen.kt:78 / ModelsScreen.kt:119 | `"Back to models"` | `cd_back_to_models` | "Volver a modelos" |
| 30 | ModelsScreen.kt:71,89,92,184,251,339,340,348,404,476,477,485 | `"Memory Warning"`, `"Continue"`, `"Cancel"`, `"Add Model"`, `"Delete model"`, `"Delete ${name} (…) from the device?"`, `"Delete endpoint"`, `"Delete ${name} (…)…"` | reuse `memory_warning_title`, `continue_text`, `cancel`, `add_model`, `delete_model_title`, NEW `delete_model_msg_fmt` (`Delete %1$s (%2$s) from the device?`), reuse `delete_endpoint_title/message` | "Eliminar %1$s (%2$s) del dispositivo?" (rest in base) |
| 31 | ModelsScreen.kt:123 / NavGraph / Settings :94 etc. | `"Menu"` | `cd_menu` | "Menú" |
| 32 | NavGraph.kt:148,179,198 | `"Logo"` / `"Delete chat"` / `"Cancel"` | `cd_logo_nav` ("Logo" both) / reuse `delete_chat_title` / reuse `cancel` | "Logo" |
| 33 | PresetsScreen.kt:79,83,84,211,216,221,224,234,243,248,258,334,378,382 | `"Back"`, `"Save"`, `"Reset"`, `"Save Preset"`, `"Preset Name"`, `"Format Mismatch"`, `"Apply Compatible"`, `"Dismiss"`, `"Load"`, `"Del"` + field labels | NEW `preset_back`? reuse `back`; NEW `preset_reset`, `preset_save_title`, `preset_name_label`, `preset_format_mismatch`, `preset_apply_compatible`, `preset_load`, `preset_delete_short` (+ reuse save/cancel/dismiss) | "Restablecer", "Guardar preset", "Nombre del preset", "Formato incompatible", "Aplicar compatible", "Cargar", "Elim." |
| 34 | PromptLabScreen.kt:65,93,150,151,171,175 + TemplateDropdown :38 | `"Prompt Lab"`, `"Target language"`, `"Prompt input"`, `"Paste text, code, or a table…"`, `"Running…"`, `"Run"`, `"Template"`, `"Model"` (ModelDropdown :39) | NEW `lab_*` keys | "Laboratorio de prompts", "Idioma objetivo", "Entrada del prompt", "Pegá texto, código o una tabla…", "Ejecutando…", "Ejecutar", "Plantilla", "Modelo" |
| 35 | UnifiedSelectorScreen.kt:52,60,66,69,101,230,254,255,262,348,349,357,454,459,464 | `"Memory Warning"`, `"This model needs ~$neededMB MB, …"`, `"Continue"`, `"Cancel"`, `"Back"` + delete dialogs + `"Delete partial file"` | reuse base keys + NEW `selector_memory_msg_fmt` (`This model needs ~%1$d MB, your device has %2$d MB available. Loading may cause instability.`) | "Este modelo necesita ~%1$d MB, tu dispositivo tiene %2$d MB disponibles. Cargarlo puede causar inestabilidad." |
| 36 | SettingsScreen.kt:56,58,67,69,76,77,82,84,91,126,136,148,153,158,167,181,183,194,203,213,215,247(theme label — code identifier, DO NOT translate),279,282,302,311,313,320,329,337,346,354,378,380,386,395,415,420 | Full settings copy: section headers (`"Data"`, `"Web"`, `"Display"`, `"App"`, `"Web Search"`, `"Security"`), rows (`"Chats"`, `"Endpoints"`, `"Models"`, `"Presets"`, `"Grounding web"` + desc, `"Code Theme"`, `"Code font size"` + value, `"Tavily search"` + descs, `"Tavily API key"`, `"Delete all keys"`, `"Save"`, `"Clear"`), dialogs (`"Delete All Chats"` + text, `"Delete All API Keys"` + text) | NEW `settings_section_*`, `settings_row_*`, `settings_delete_*`, `settings_tavily_*`, `settings_grounding_*`, `settings_code_*` keys; `theme.label` stays code | "Datos", "Web", "Pantalla", "App", "Búsqueda web", "Seguridad", "Chats", "Endpoints", "Modelos", "Presets", "Web con grounding" + desc "Buscar y citar fuentes…", "Tema de código", "Tamaño de fuente del código", "Búsqueda Tavily", "Clave API de Tavily", "Eliminar todas las claves", "Guardar", "Borrar", "Eliminar todos los chats", "Eliminar todas las API keys", "Esto eliminará permanentemente todas las API keys guardadas. No se puede deshacer." |
| 37 | BenchmarkScreen.kt:43,84,88 + ConfigCard 87,93 + ModelDropdown | `"Benchmark"`, `"Running…"`, `"Start Benchmark"`, `"Fewer trials"`, `"More trials"`, `"Model"` | NEW `bench_*` keys | "Benchmark" (same), "Ejecutando…", "Iniciar benchmark", "Menos pruebas", "Más pruebas", "Modelo" |
| 38 | ChatInputBar / EndpointForm placeholders | Already `stringResource` (verified — no action) | — | — |
| 39 | `••••••••` mask, `"+"`, `"•"` bullets | Symbols — leave as-is | — | — |

Note: executor must re-grep at implementation time (`Text("`, `contentDescription = "`, `Toast.makeText`, `ChatEvent.Snackbar(`, `title = { Text(`, `label = { Text(`, `placeholder`) and reconcile any rows missed above; the table is the baseline, the grep is the gate (zero user-facing literals outside `res/` afterwards, excluding the NEVER-translate list).

**Verify:**
- `./gradlew :app:testDebugUnitTest --tests "com.warped.i18n.StringResourceParityTest"`
- `./gradlew :app:testDebugUnitTest` (full green)
- `./gradlew :app:assembleDebug`
- `grep -rn --include='*.kt' -E 'Text\("[^"]+"\)|contentDescription *= *"[^"]+"' app/src/main/java | grep -v -E 'stringResource|Preview|//'` returns only NEVER-translate items

**Done:** `values-es/strings.xml` exists with exact key parity (parity test green); no hardcoded user-facing Kotlin strings remain; full unit suite + assembleDebug green.

---

## Task 3 — Paragraph bottom spacing bump (C)

**Files:**
- `app/src/main/java/com/warped/ui/chat/components/MarkdownText.kt` (line 88: outer Column `Arrangement.spacedBy(4.dp)`)

**Action:**
1. Change outer `Column` `verticalArrangement = Arrangement.spacedBy(4.dp)` → `Arrangement.spacedBy(8.dp)`. Single value, one line. This Column wraps ALL block types (Text/Header/Code/List) in BOTH streaming and settled paths — `MarkdownText` is the shared composable (no separate streaming renderer), so one edit covers both per D-C.
2. Do NOT touch list-internal `spacedBy(6.dp)` (line 136), list `padding(vertical = 4.dp)`, code-block or inline-code padding.
3. Sanity-check no screenshot/golden tests assert the old spacing (grep `spacedBy` / `4.dp` in `app/src/test`).

**Verify:** `./gradlew :app:assembleDebug` + visual check in the human-verify step below (on-device, ES locale — combines with Task 2 locale check).

**Done:** Paragraph blocks render with 8.dp gaps; lists and code blocks unchanged; build green.

---

## Human verify (on-device, no adb)

1. Set system locale to **Español**, open the app: chat, models, endpoints, selector, settings, help, wizard (re-run from Settings), presets, benchmark, prompt lab, HF catalog, all dialogs/toasts/a11y — all Spanish, no English leaks, no missing-resource crashes.
2. Switch system locale to **English**: same tour, all English.
3. Chat paragraph spacing visibly roomier (compare headings/lists unchanged).
4. Ask a question in Spanish with web grounding on: answer arrives in Spanish, no "Based on the search results,"-style English lead-in. (Note: model language-following is probabilistic — the rule maximizes compliance but cannot guarantee it 100%, especially on small local models.)

## Honest notes

- Locale switching needs an on-device check with ES system locale (no adb in this environment) — the parity test guards key drift, not rendering.
- Model language-following is probabilistic; the prompt rule is the strongest available lever, not a guarantee.
- Pre-sweep `values-es` (commit `c5f200a6^`) is the ES base: 155 keys, tone reference ("Fuentes"-style neutral Latin American). Since `values/strings.xml` hasn't changed since the sweep commit, restore is a clean 1:1 base.

## Tests

- `GroundingPromptTest` + `LiteRTLmLoopTest` (updated verbatim/rule assertions)
- NEW `StringResourceParityTest` (values vs values-es key equality — FAIL on drift)
- Updated copy assertions for any tests pinning old hardcoded strings
- Full `:app:testDebugUnitTest` green + `assembleDebug`
