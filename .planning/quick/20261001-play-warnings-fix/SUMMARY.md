---
status: complete
date: 2026-10-01
track: beta
pushed: false
---

# Summary: Play warnings fix (edge-to-edge, bitmaps, native symbols)

## What changed (4 files, commit on `beta`, NOT pushed)
1. `app/src/main/res/values/themes.xml` — removed deprecated
   `android:statusBarColor` / `android:navigationBarColor` attrs
   (`enableEdgeToEdge()` already in `MainActivity`).
2. `.../ui/chat/components/ChatInputBar.kt` — attached-image preview:
   unbounded `BitmapFactory.decodeStream` → Coil `AsyncImage`
   (auto-downsample + memory/disk cache via app singleton).
3. `.../ui/chat/components/MessageBubble.kt` — base64 data-URL decode
   now bounded (`inJustDecodeBounds` + power-of-2 `inSampleSize`, 1024px
   cap via `decodeSampledBitmap`); display path unchanged.
4. `app/build.gradle.kts` — `ndk.debugSymbolLevel = "FULL"` on release.

## Verification
- `:app:compileDebugKotlin` → OK.
- `bundleRelease` → OK (R8 full mode clean).
- AAB contains `proguard.map` (Java deobfuscation OK) but **no native
  `debugsymbols/`**: verified both prebuilt `.so` (sqlcipher 4.19.1,
  litertlm 0.17.1) are fully stripped upstream — no symbol file exists
  to upload. The Play symbols warning can only clear if vendors ship
  symbols; the gradle flag is future-proofing for first-party native
  code. Reported honestly to the user, comment in code says so.
- No unit tests reference the touched code (grep clean).

## Notes / follow-ups (NOT done here)
- 2.5.2 "crashes after opening" rejection: separate open investigation
  (fresh-install repro passes on Pixel 8/A17; awaiting user to install
  Play prod build and report).
- `ModelDownloadWorker.byteStream` Play flag = GGUF download, false
  positive, untouched.
- 32-bit ABI risk (`armeabi-v7a`/`x86` installable, no litertlm `.so`):
  needs product decision (`abiFilters` vs graceful degradation).
