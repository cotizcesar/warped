# Requirements: Warped — v2.2 Simplificación + Web Grounding

**Defined:** 2026-09-28
**Core Value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.

## v1 Requirements

Scope comprometido para v2.2. Cada requirement mapea a exactamente una fase del roadmap.

### Remoción (DEL)

Superficie eliminada: skills, token HF, buscador. Verificación por grep-gates + build verde.

- [ ] **DEL-01**: Usuario chatea sin skills — ejecución local `@Tool` (Calculator, CurrentTime, JsonFormatter) eliminada y `runInference` vuelve a firma sin parámetro `skills`
- [ ] **DEL-02**: Superficie Skills eliminada sin referencias — SkillChipsRow, SkillPreferences, SkillRepository, ToolGating y template Summarize fuera del árbol (grep cero resultados)
- [ ] **DEL-03**: Loop remoto `tools[]` eliminado — sin rondas tool en LM Studio; filas `Role.TOOL` históricas siguen legibles en el transcript (solo lectura, sin migración Room)
- [ ] **DEL-04**: Access token de Hugging Face eliminado — sin campo en settings, sin headers `Authorization` en descargas, sin entrada en prefs cifradas, sin modelos con gate
- [ ] **DEL-05**: Buscador de modelos eliminado — solo catálogo estático `model_allowlist.json` con descarga directa (sin token) + gestión local (ver, borrar, progreso, cancelar)
- [ ] **DEL-06**: Post-remoción verde — keeps R8 de skills estrechados (keeps del SDK LiteRT-LM intactos), `scripts/audit-dependencies.sh` verde, smoke `assembleRelease` + chat local/remoto OK

### Web Grounding (WEB)

Grounding heurístico con cero nuevas dependencias (OkHttp existente + `ConnectivityManager`). `LlmModelHelper` sin cambios.

- [ ] **WEB-01**: Usuario pega una URL en el mensaje y la app la detecta (heurística determinista) y dispara un único fetch antes de la inferencia
- [ ] **WEB-02**: Fetch acotado y cancelable — cap de bytes, cap de redirects, timeouts, User-Agent browser, `Call.cancel()` cableado al botón Stop
- [ ] **WEB-03**: Contenido web inyectado como bloque delimitado `[WEB CONTEXT]` (extracción HTML→texto hand-rolled, truncado a presupuesto de contexto)
- [ ] **WEB-04**: System prompt de grounding — el modelo consume el bloque y cita fuentes, pide al usuario pegar un link cuando necesita info fresca, y nunca inventa URLs
- [ ] **WEB-05**: Sin internet o fallo de fetch, el usuario recibe respuesta solo-modelo con aviso visible (no generado por el modelo) — nunca contenido de error inyectado como contexto
- [ ] **WEB-06**: Usuario puede activar/desactivar web grounding en settings y ve estado "Leyendo página…" + fuentes citadas en la respuesta

### Temas de código (THEME)

Fix del selector que solo aplica Monokai (raíz localizada: `SyntaxHighlighterImpl.kt:44` hardcodea `.theme(monokai())`).

- [ ] **THEME-01**: Los 4 presets (Monokai, One Dark, GitHub, Dracula) aplican en bloques de código del chat — Python como caso guía verificado en los 4
- [ ] **THEME-02**: Test de regresión round-trip cubre los 4 presets (falla si algún preset se ignora silenciosamente)

## Future Requirements

Reconocido pero diferido — no entra en el roadmap v2.2.

### Web avanzado

- **WEBF-01**: Integración con API de búsqueda (Brave/Tavily) con API key del usuario
- **WEBF-02**: Multi-fetch agéntico (varias páginas por pregunta con loop de rondas)
- **WEBF-03**: Verificación de entailment post-hoc (respuesta fiel al contenido citado)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Cambios en variantes light/dark de los presets | Ya existen desde v1.6; el bug es que 3 presets no aplican — sin cambios de paletas |
| Eliminar el enum `Role.TOOL` de Room | Filas históricas deben seguir renderizando; solo mueren los escritores |
| Bump de LiteRT-LM (hold 0.17.1) | Grounding es inyección de system prompt, no trabajo de engine |
| Jsoup u otra lib de extracción HTML | Restricción cero-nuevas-dependencias; stripping hand-rolled es suficiente para 4–8 KB de texto |
| Render JS / headless browser | Costo y complejidad fuera de escala para grounding heurístico |
| Números Pixel 7 (PERF-16 + PERF-12/13) | Siguen CI-gated desde v2.1, sin cambios |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| DEL-01 | TBD | Pending |
| DEL-02 | TBD | Pending |
| DEL-03 | TBD | Pending |
| DEL-04 | TBD | Pending |
| DEL-05 | TBD | Pending |
| DEL-06 | TBD | Pending |
| WEB-01 | TBD | Pending |
| WEB-02 | TBD | Pending |
| WEB-03 | TBD | Pending |
| WEB-04 | TBD | Pending |
| WEB-05 | TBD | Pending |
| WEB-06 | TBD | Pending |
| THEME-01 | TBD | Pending |
| THEME-02 | TBD | Pending |

**Coverage:**
- v1 requirements: 14 total
- Mapped to phases: 0
- Unmapped: 14 ⚠️ (se resuelve en roadmap)

---
*Requirements defined: 2026-09-28*
*Last updated: 2026-09-28 after initial definition*
