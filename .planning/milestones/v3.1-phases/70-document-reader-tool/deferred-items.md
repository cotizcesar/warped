# Phase 70 Deferred Items (release-UAT)

Hardware-dependent checks that cannot run in this environment. Recorded with a
runbook rather than failing the phase (house precedent). All automated gates
are green (see VERIFICATION.md).

## UAT-70-01 — Attach UX visuals + TalkBack (device)

- **What:** Paperclip button 40dp in the input row; chip (filename + size +
  truncation marker + remove-X); long-filename ellipsis; remove-X hit target.
- **Runbook:** On a device, open chat → tap paperclip → pick a `.txt` →
  confirm chip shows `filename · N KB` + remove works single-tap. Pick a
  second file → replaces the first. Enable TalkBack → attach button announces
  "Attach document" (then "Replace attached document"), chip remove announces
  "Remove attached document", attached state announces the filename.
- **Expect:** Matches 70-UI-SPEC Surface 1 + Surface 2.
  status: acknowledged

## UAT-70-02 — Grounding + truncation notice (device, local model)

- **What:** Grounded answer from a picked document; over-cap truncation marker.
- **Runbook:** On a function-calling local model, attach a small `.txt`, ask
  a question answerable only from the file → answer cites file content.
  Attach a file larger than the cap (`perPageBudget(contextSize, 1)`, ~6000
  chars at 4K window) → chip shows `· showing first N chars` + Snackbar
  "Showing first N chars of {filename}" on send; answer still arrives.
- **Expect:** Never silent, never blocked (70-CONTEXT boundary).
  status: acknowledged

## UAT-70-03 — Degradation paths (device)

- **What:** Binary pick, empty read, failed read.
- **Runbook:** Pick a `.pdf` → Snackbar "Can't read {name} yet — text files
  (.txt, .md) only", no chip, text still sendable. Pick an empty `.txt` →
  send → Snackbar "Couldn't read {name} — answering without it", text-only
  answer arrives. Send is never dead-ended in any path.
- **Expect:** 70-UI-SPEC Surface 3 copy, SnackbarDuration.Short.
  status: acknowledged

## UAT-70-04 — Remote E2E per provider

- **What:** Same UX on remote endpoints via `read_text_file`.
- **Runbook:** Against each of OpenAI-compatible, Anthropic, Ollama,
  LM-Studio, and custom endpoints: attach a `.txt`, ask a file-grounded
  question → grounded answer + Fuentes document card. Against a server that
  rejects `tools[]` (400 naming tools) → exactly-one retry without tools +
  the tools-unsupported notice, turn completes (existing fallback path).
- **Expect:** Same cap, envelope, and fallback as local.
  status: acknowledged

## UAT-70-05 — Fuentes card + preview + rotation + locale

- **What:** Document card chrome, preview sheet, rotation survival, ES copy.
- **Runbook:** Grounded turn shows the document in Fuentes with filename
  label (+ `truncated at N chars` when truncated); tap → SourcePreviewSheet
  with the bounded text start and NO browser button. Rotate with attachment →
  chip survives. Switch to ES locale → all chip/Snackbar copy translated.
- **Expect:** 70-UI-SPEC copy table EN+ES.
  status: acknowledged

## NOTE-70-06 — Pre-existing test flake (not UAT, tracked here for visibility)

- **What:** `ModelSwitchUnloadTest.failed mount surfaces error and keeps
  draft` fails intermittently in FULL-package runs (passes alone and often
  in-suite). Real-thread `Dispatchers.Default` hop vs a 100-attempt virtual
  poll; prior stabilization commit 34bd2406 in history. Reproduced on the
  CLEAN tree during this phase — unrelated to Phase 70 changes (no test file
  touched).
- **Runbook:** Re-run `./gradlew :app:testDebugUnitTest`; if still red, run
  the single class to confirm, then file a stability fix (raise poll attempts
  or await the error via Turbine-style condition instead of fixed attempts).
  status: acknowledged
