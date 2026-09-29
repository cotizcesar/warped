---
phase: 58-opengraph-thumbnails
reviewed: 2026-09-29T00:00:00Z
depth: standard
files_reviewed: 23
files_reviewed_list:
  - app/src/main/java/com/warped/data/grounding/OpenGraphParser.kt
  - app/src/main/java/com/warped/data/grounding/WebPageFetcher.kt
  - app/src/main/java/com/warped/data/grounding/GroundingResult.kt
  - app/src/main/java/com/warped/data/grounding/MultiUrlFetcher.kt
  - app/src/main/java/com/warped/domain/model/GroundedSource.kt
  - app/src/main/java/com/warped/data/local/db/entity/GroundedSourceEntity.kt
  - app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt
  - app/src/main/java/com/warped/data/local/db/Migrations.kt
  - app/src/main/java/com/warped/data/local/db/AppDatabase.kt
  - app/src/main/java/com/warped/di/DatabaseModule.kt
  - app/src/main/java/com/warped/WarpedApplication.kt
  - app/src/main/java/com/warped/ui/chat/components/OgSourceCard.kt
  - app/src/main/java/com/warped/ui/chat/components/BrowserIntents.kt
  - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
  - app/src/main/java/com/warped/ui/chat/components/SourcePreviewSheet.kt
  - app/src/main/java/com/warped/ui/theme/Color.kt
  - app/build.gradle.kts
  - gradle/libs.versions.toml
  - app/schemas/com.warped.data.local.db.AppDatabase/16.json
  - app/src/test/java/com/warped/data/grounding/OpenGraphParserTest.kt
  - app/src/test/java/com/warped/data/local/db/Migration14To15StaticTest.kt
  - app/src/test/java/com/warped/data/local/db/Migration15To16StaticTest.kt
  - app/src/test/java/com/warped/ui/chat/components/OgSourceCardHelpersTest.kt
findings:
  critical: 0
  warning: 2
  info: 2
  total: 4
status: fixed
---

# Phase 58: Code Review Report

**Reviewed:** 2026-09-29T00:00:00Z
**Depth:** standard
**Files Reviewed:** 23
**Status:** issues_found

## Summary

Reviewed the full Phase 58 scope (git range `93344729..HEAD`: commits `106e00c9`, `c347da62`, `04616258`, `a05bd8e2`) covering the OG parse/persist backbone (58-01) and the Coil cards + sheet header UI (58-02). All key threat areas verified clean: Jsoup is parse-only (no `connect` in grounding sources), the http(s) image gate exists at both parse (`OpenGraphParser.resolveHttpUrl`) and render (`gatedHttpImageUrl` in card and sheet), Coil uses its own bare `OkHttpClient` with no `AuthInterceptor` reference, the v15→v16 migration is exactly 3 nullable `ALTER TABLE` statements registered in `DatabaseModule` with `fallbackToDestructiveMigration(false)`, OG columns flow through `toEntity`/`toDomain` so retry/replace carries them, disk cache is a fixed 50MB cap, copy is English, and there is no purple (coral `0xFFD97757` + neutrals only). No BLOCKERs. Two warnings (render-gate strictness gap, case-sensitive browser scheme check) and two info items (duplicated host fallback, dead-path spacer) below.

## Warnings

### WR-01: Render-side image gate accepts opaque non-hierarchical URIs

**File:** `app/src/main/java/com/warped/ui/chat/components/OgSourceCard.kt:238-243`
**Issue:** `gatedHttpImageUrl` splits on the first `:` (`value.substringBefore(':')`) and accepts anything whose prefix is `http`/`https`. This passes opaque URIs such as `https:foo`, `http:evil`, or `https:javascript:alert(1)` through to Coil — the parse-side gate (`OpenGraphParser.resolveHttpUrl`) is stricter, requiring an `http(s)://` prefix post-resolution. Impact is contained (Coil fails to load the garbage model and the card falls back to text-only via `onError`), so this is a defense-in-depth inconsistency, not an exploitable hole — but the gate's stated contract ("only gated URLs reach Coil") is not met.
**Fix:**
```kotlin
fun gatedHttpImageUrl(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    return if (value.startsWith("http://", ignoreCase = true) ||
        value.startsWith("https://", ignoreCase = true)
    ) value else null
}
```
Mirror the parse gate exactly so both layers enforce the same allowlist.

### WR-02: Browser scheme allowlist is case-sensitive

**File:** `app/src/main/java/com/warped/ui/chat/components/BrowserIntents.kt:27`
**Issue:** `uri.scheme != "http" && uri.scheme != "https"` rejects any URL whose stored scheme is uppercase (e.g. `HTTP://example.com`). URI schemes are case-insensitive per RFC 3986, and `GroundedSource.url` is untrusted stored text — a valid link with an uppercase scheme shows "Invalid link." instead of opening. The fetcher redirect gate does not have this problem (OkHttp normalizes scheme case); only this UI gate does.
**Fix:**
```kotlin
if (!uri.scheme.equals("http", ignoreCase = true) &&
    !uri.scheme.equals("https", ignoreCase = true)
) {
```

## Info

### IN-01: Duplicated host-fallback logic with divergent behavior

**File:** `app/src/main/java/com/warped/data/grounding/OpenGraphParser.kt:82-89` and `app/src/main/java/com/warped/ui/chat/components/OgSourceCard.kt:225-232`
**Issue:** `OpenGraphParser.hostOf` (internal) and `ogHostOf` (public) implement the same "host of URL or raw URL" fallback with subtle differences: `ogHostOf` strips a `www.` prefix, `hostOf` does not; empty-host fallbacks agree (raw URL) only by accident. Two sources of truth for one locked fallback chain (og:title → title → host).
**Fix:** Have the parser delegate to the shared helper (or move `ogHostOf` into a shared pure-Kotlin location both call), e.g. `internal fun hostOf(url: String): String = ogHostOf(url)` after verifying the `www.`-strip is acceptable for the parse path, with a unit test pinning the unified behavior.

### IN-02: Dead-path card skip still emits the inter-card spacer

**File:** `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt` (Fuentes `forEachIndexed`, `?: return@forEachIndexed` + trailing `if (index != fuenteList.lastIndex) Spacer(8.dp)`)
**Issue:** The `?: return@forEachIndexed` guard after `previewForTap` is unreachable by construction (`clickable` is true exactly when status is OK, and the legacy branch never returns null), but if it ever fired, the 8dp inter-card `Spacer` below the if/else would still be emitted, leaving a stray gap with no card. Either remove the dead guard (rely on the clickable ⟺ OK invariant with a comment) or move the spacer inside the rendered branches.
**Fix:** Delete the `?: return@forEachIndexed` and keep a comment stating the invariant, or scope the spacer to emitted cards only.

---

_Reviewed: 2026-09-29T00:00:00Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
