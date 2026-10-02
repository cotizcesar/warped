---
status: resolved
trigger: |
  6 bugs post-v3.0 en chat/voz/catalogo: (1) input del chat habilitado mientras
  se carga un modelo; (2) StackOverflowError en
  VoiceDictationManager$start$listener$1.onError(VoiceDictationManager.kt:88)
  al abrir el dictado; (3) dictado no detecta español (lo reconoce como
  inglés); (4) crash en el mismo onError:88 al enviar un mensaje dictado;
  (5) el dictado se detiene solo (auto-stop) en vez de solo con el botón;
  (6) tarjetas Downloaded del catálogo idénticas sin nombre del modelo ni
  indicador de modelo en uso.
symptoms:
  expected: |
    (1) Todo el input del chat deshabilitado mientras se carga un modelo.
    (2) Abrir el dictado nunca crashea. (3) El dictado reconoce español cuando
    se habla español. (4) Enviar un mensaje dictado no crashea. (5) El dictado
    solo se detiene al pulsar el botón. (6) Cada tarjeta Downloaded muestra
    nombre/tamaño/capacidades como las Available, con CTA abajo, y se distingue
    el modelo en uso.
  actual: |
    (1) El input sigue habilitado durante la carga del modelo. (2)/(4)
    StackOverflowError: onError:88 recursivo hasta SIG 9 (PID 21874 y 23005).
    (3) Español reconocido como inglés. (5) El dictado se auto-detiene por
    silencio. (6) Downloaded muestra tarjetas idénticas (punto verde + Use in
    chat + papelera) sin nombre. Screenshot 11:54 aportado.
  error_messages: |
    AndroidRuntime E: at com.warped.ui.chat.voice.VoiceDictationManager$start$listener$1.onError(VoiceDictationManager.kt:88)
    repetido cientos de veces -> StackOverflowError -> "Sending signal. PID SIG: 9".
  timeline: |
    Reportado 2026-10-02 en device, tras v3.0 (fases 63-66). VoiceDictationManager
    es nuevo de la fase 65.
  reproduction: |
    (2) Abrir el dictado (mic) algunas veces. (4) Dictar, enviar el mensaje.
    (3) Dictar en español. (1) Cargar un modelo y observar el input.
    (6) Abrir Model catalog con modelos descargados.
created: 2026-10-02
updated: 2026-10-02
---

# Debug Session: dictation-chat-bugs

## Current Focus
- hypothesis: confirmed — onError self-recursion (name shadowing) caused the StackOverflow; remaining bugs were missing gates (loading lock, locale extra, continuous re-arm, card CTA/in-use)
- test: VoiceDictationTest (rewritten for continuous) + CatalogInUseTest (new) + catalog suite + assembleDebug — all green
- expecting: null
- next_action: none — all 6 bugs fixed and verified
- reasoning_checkpoint: null
- tdd_checkpoint: null

## Evidence
- timestamp: 2026-10-02T00:00:00Z
  finding: VoiceDictationManager.kt:88 `onError(error)` inside anonymous RecognitionListener resolves to the listener's own override, not the constructor lambda — infinite recursion -> StackOverflowError -> SIG 9. Same file had no EXTRA_LANGUAGE (some recognizers default en-US) and single-shot semantics with flag cleared on every final/error.
  file: app/src/main/java/com/warped/ui/chat/voice/VoiceDictationManager.kt
- timestamp: 2026-10-02T00:00:00Z
  finding: ChatInputBar enabled gate ignored model loading (no isLoadingModel param); ChatScreen never passed connection.isLoadingModel. Mic/send/image/think all tappable mid-load.
  file: app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
- timestamp: 2026-10-02T00:00:00Z
  finding: Catalog downloaded cards used a compact header-row CTA with no in-use signal. Restructured to full Available-card header + full-width bottom CTA + "In use" pill driven by new CatalogViewModel.activeLocalModelId.
  file: app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt
- timestamp: 2026-10-02T00:00:00Z
  finding: Baseline check via git stash confirmed the 4 catalog test failures were caused by the new StateFlow initializer touching relaxed-mock `.value` (CCE); fixed with null initial value. All suites green after.
- timestamp: 2026-10-02T00:00:00Z
  finding: Verification — :app:testDebugUnitTest (VoiceDictationTest 8 tests, huggingface suite incl. new CatalogInUseTest 3 tests) BUILD SUCCESSFUL; :app:assembleDebug BUILD SUCCESSFUL.

## Eliminated
- Network/recognizer-availability causes for the crash: the stack shows pure in-process recursion at onError:88, no IPC in the loop.
- ModelCard title rendering bug for (6): the shared ModelCard always rendered the title; the reported "identical cards" matched the old compact header-row CTA with no in-use signal, fixed by layout restructure rather than a rendering fix.

## Resolution
- root_cause: VoiceDictationManager's anonymous RecognitionListener called bare `onError(error)`, which resolved to its own override (name shadowing) and recursed until StackOverflowError/SIG 9 — covering both the open-dictation crash and the dictate-then-send crash (late error callback). Companion causes: no EXTRA_LANGUAGE (en-US fallback mis-transcribed Spanish), single-shot flag clearing (auto-stop), missing isLoadingModel gate on the input bar, and a compact downloaded-card layout with no in-use signal.
- fix: Qualified callbacks via `this@VoiceDictationManager` + EXTRA_LANGUAGE=Locale.getDefault(); continuous dictation (finals and NO_MATCH/SPEECH_TIMEOUT re-arm, fatal errors stop, stop clears flag first so late callbacks can't re-arm); full input lock while a model loads; catalog downloaded cards reuse the Available header with a full-width bottom CTA plus "In use" pill from new activeLocalModelId flow and isEntryInUse helper.
- verification: VoiceDictationTest rewritten for continuous semantics (8 tests pass), new CatalogInUseTest (3 tests pass), full huggingface suite passes, :app:assembleDebug succeeds.
- files_changed:
  - app/src/main/java/com/warped/ui/chat/voice/VoiceDictationManager.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
  - app/src/main/java/com/warped/ui/huggingface/CatalogViewModel.kt
  - app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt
  - app/src/main/res/values/strings.xml (+ values-es)
  - app/src/test/java/com/warped/ui/chat/VoiceDictationTest.kt
  - app/src/test/java/com/warped/ui/huggingface/CatalogInUseTest.kt (new)
