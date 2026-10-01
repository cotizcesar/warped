# LEAK-TOUR.md — Scripted LeakCanary Guided Audit (Phase 61, LEAK-01)

Reproducible, step-by-step leak tour for the Warped Android app. Each leg names
the exact UI steps, the leak-prone surface it exercises, and how to check
LeakCanary output. A stranger with the device below must be able to replay every
leg exactly. **Do NOT fix any leak found here — Phase 62 owns fixes.** Record
everything; triage assigns each finding to exactly one owning layer.

## Device preamble (mandatory)

| Item | Value |
|------|-------|
| Device | Physical Pixel 8 (USB-attached) |
| `ANDROID_SERIAL` | `37141FDJH0065Y` |
| adb | `/home/cotizcesar/Android/Sdk/platform-tools/adb` |
| Emulator `emulator-5554` | **EXCLUDED** — crash-loops, never use for this tour |
| Build under test | **debug** APK only (`./gradlew :app:installDebug`), LeakCanary 2.14 auto-installs via ContentProvider — no init code, no manual start |

Setup before every session:

```bash
export ANDROID_SERIAL=37141FDJH0065Y
ADB=/home/cotizcesar/Android/Sdk/platform-tools/adb
$ADB devices            # expect: 37141FDJH0065Y  device
./gradlew :app:installDebug
$ADB shell am start -n com.warped.app/.MainActivity
```

## How to trigger / check LeakCanary output

1. LeakCanary watches automatically in debug builds. After each leg, background
   the app (Home button) and wait ~10 s so retained-object checks fire.
2. A leak appears as a **system notification** ("1 leak detected in Warped").
   Tap it to open the LeakCanary analysis screen listing the leak trace and the
   retained heap reference chain.
3. For every finding note the **heap evidence ref**: the leak signature shown on
   the analysis screen (e.g. `ChatViewModel leaking` + retained-by chain). If
   no notification appears within 60 s after backgrounding, record
   `observed = clean`.
4. Heap dumps stay **on the dev device only** — never `adb pull` them to the
   repo, never commit them, never upload them (chat PII may be inside).
   Record the on-screen signature text, not the dump file.

## Leg 1 — Model load / switch / unload

**Surface:** `EngineManager` + `LiteRTLmEngine` native handles (largest
leak-prone area: model weights + native inference context).

1. Open the app → navigate to **Models** (`Screen.Models`).
2. Load model A (any downloaded GGUF; if none is on-device, download one first
   via **HuggingFace** screen — see §Deferral if the 2.6 GB download is
   unavailable on this network).
3. Start a 1-turn chat on model A ("Say hi"), wait for completion.
4. Back to **Models** → load model B (a different GGUF file).
5. Start a 1-turn chat on model B, wait for completion.
6. Back to **Models** → unload the current model (or load "none" / clear
   selection if no explicit unload affordance exists — record which).
7. Background the app (Home), wait 60 s, check the LeakCanary notification
   shade.

**Expect:** no retained `LiteRTLmEngine` / native handle after unload; model B
load must release model A weights (watch for OOM-adjacent growth on repeat).

## Leg 2 — Streaming chat + Stop

**Surface:** `ChatViewModel` / `ChatRepositoryImpl` single-flight
`runInference` + token collectors (cancelled coroutines, uncollected flows).

1. Navigate to **Chat** (`Screen.Chat`), select any loaded model or remote
   endpoint.
2. Send a prompt that generates a long answer (e.g. "Write a 500-word story
   about a lighthouse").
3. While tokens are still streaming, tap **Stop** (mid-stream cancellation).
4. Immediately send a second prompt ("Summarise in one sentence") and let it
   complete.
5. Repeat stop-mid-stream → re-prompt once more, then background the app, wait
   60 s, check LeakCanary.

**Expect:** cancelled inference jobs release cleanly; no retained
`ChatViewModel` collectors or half-consumed SSE/token buffers.

## Leg 3 — 5-URL grounding + cancel

**Surface:** `data/grounding/` 5-fan-out fetch + Tavily client + SSE
accumulators (parallel jobs, per-URL buffers, timeout paths).

1. In **Chat**, send a grounding-triggering prompt (a current-events question
   that fans out to ~5 URLs; confirm the grounding indicator / Fuentes strip
   appears).
2. While Fuentes are still loading, cancel (Stop button or navigate away from
   Chat to **Models** and back — record which cancellation was used).
3. Re-send the same prompt and let all 5 Fuentes fully resolve.
4. Background the app, wait 60 s, check LeakCanary.

**Expect:** cancelled fan-out releases all 5 fetch jobs + Tavily client
callbacks; no retained per-URL accumulators after full resolve.

## Leg 4 — Offline → retry

**Surface:** `ModelDownloadManager` (WorkManager + observers) and OkHttp retry
paths in `di/NetworkModule` (queued callbacks surviving connectivity loss).

1. Enable airplane mode (or `adb shell svc wifi disable && adb shell svc data
   disable`).
2. In **Chat**, send any prompt → observe the offline/error state and the
   retry affordance.
3. Start a model download in **Models**/**HuggingFace** while still offline
   (should queue/fail gracefully, no crash).
4. Disable airplane mode (re-enable wifi/data), tap **Retry** on the failed
   chat, let it complete.
5. Background the app, wait 60 s, check LeakCanary.

**Expect:** offline-queued callbacks and download observers release after
reconnect; no retained WorkManager observers or stale OkHttp call objects.

## Leg 5 — OG thumbnail scroll (Fuentes list)

**Surface:** Coil singleton in `WarpedApplication` (`SingletonImageLoader`)
+ Compose lazy-list image requests (view-holder / request disposables).

1. Navigate to **Fuentes** (`Screen.Endpoints` — the Fuentes list showing OG
   thumbnails via Coil 3).
2. Scroll the list top → bottom → top twice at moderate speed (let thumbnails
   load, cancel, reload).
3. Open a source detail (if any), go back to the list.
4. Navigate to **Settings** and back to **Fuentes** (screen-scope disposal
   check).
5. Background the app, wait 60 s, check LeakCanary.

**Expect:** scrolled-off image requests dispose; no retained Coil request
targets or list-item scopes after leaving the screen.

## Leg 6 — Rotation + process death

**Surface:** Activity/Fragment + ViewModel retention across config change;
saved-state restoration after process death.

1. In **Chat** with an active conversation visible, rotate the device
   portrait → landscape → portrait (3 rotations, pausing 3 s each).
2. Rotate once more mid-stream (send a long prompt, rotate while streaming).
3. Trigger process death: background the app, then
   `adb shell am kill com.warped.app` (or "Don't keep activities" path —
   record which), then relaunch from recents.
4. Verify the conversation list restores, background the app, wait 60 s,
   check LeakCanary.

**Expect:** no retained destroyed-Activity instances; ViewModels scoped
correctly; post-death restore leaks nothing.

## Result-recording table

Copy one row per leg per run:

| Leg | Observed (clean / leak signature) | Heap evidence ref (LeakCanary screen text) | Triage owner (native / VM / network / Compose) |
|-----|-----------------------------------|--------------------------------------------|-------------------------------------------------|
| 1 load/switch/unload | | | |
| 2 streaming + Stop | | | |
| 3 grounding + cancel | | | |
| 4 offline → retry | | | |
| 5 OG thumbnail scroll | | | |
| 6 rotation + process death | | | |

Rules: every finding gets **exactly one** owning layer (native → VM →
network → Compose, first responsible layer wins). `clean` legs stay in the
table with `observed = clean` — never delete a row.

## Deferral template (explicit, never silent)

Any leg that cannot run (e.g. the 2.6 GB model download is unavailable on the
current network, Tavily key missing, no second GGUF on-device) is recorded as
an explicit DEFERRED entry — never a silent pass:

```
DEFERRED — Leg <n> (<name>): <reason it could not run>
  (e.g. Leg 1 model-B load: only one GGUF on-device and metered network
   blocks the second download; retried <date>)
Needs: <what would unblock it> | Owner: <who> | Carry to: 61-02 / Phase 62
```

Deferred legs keep their empty table row with `Observed = DEFERRED — see
entry above`.
