# Quick Task: Play warnings fix (edge-to-edge, bitmaps, native symbols)

## Task
Resolve 3 Google Play Console warnings on the 2.5.2/beta track:

1. **Deprecated edge-to-edge APIs** (`Window.setStatusBarColor` /
   `setNavigationBarColor`) — caused by `android:statusBarColor` /
   `android:navigationBarColor` theme attrs in `themes.xml`. App already
   calls `enableEdgeToEdge()` in `MainActivity`, so the attrs are
   redundant. Fix: remove them.
2. **Manual bitmap download/decode** — 2 real app call sites doing
   unbounded `BitmapFactory` decodes (OOM risk):
   - `ChatInputBar.kt:75` (`decodeStream` on attached `content://` Uris)
     → migrate to Coil `AsyncImage` (already a dependency, own OkHttp,
     memory+disk cache via app singleton).
   - `MessageBubble.kt:621` (`decodeByteArray` on base64 data URLs, no
     local-file/URI form Coil can load directly) → bounded subsampled
     decode (`inJustDecodeBounds` + power-of-2 `inSampleSize`, 1024px cap).
   (`ModelDownloadWorker.byteStream` flagged by Play is a GGUF download,
   not an image — false positive, untouched.)
3. **Missing native debug symbols** (warning on bundle 20400046) — set
   `ndk.debugSymbolLevel = "FULL"` on the release build type so Play can
   symbolicate native crashes.

## Verification
- `./gradlew :app:compileDebugKotlin` (UI changes compile).
- `./gradlew bundleRelease` + confirm `BUNDLE-METADATA` debug symbols in
  the AAB (symbols fix).
- Atomic commit on `beta`, NO push (user instruction).

## Out of scope
- The 2.5.2 "crashes after opening" rejection (separate investigation,
  pending repro from Play prod install).
- 32-bit ABI hardening (`armeabi-v7a`/`x86` can install but lack
  `liblitertlm_jni.so`) — recorded follow-up, needs product decision.
