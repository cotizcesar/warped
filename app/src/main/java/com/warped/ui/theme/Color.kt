package com.warped.ui.theme

import androidx.compose.ui.graphics.Color

val PrimaryLight = Color(0xFFD97757)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryDark = Color(0xFFD97757)
val OnPrimaryDark = Color(0xFF1C1C1C)

val SurfaceLight = Color(0xFFFAFAFA)
val SurfaceDark = Color(0xFF1F1F1E)
val BackgroundLight = Color(0xFFFFFFFF)
val BackgroundDark = Color(0xFF1F1F1E)

val ErrorLight = Color(0xFFDC2626)
val ErrorDark = Color(0xFFEF4444)

val UserBubbleLight = Color(0xFFD97757)
val UserBubbleDark = Color(0xFF121212)
val AssistantBubbleLight = Color(0xFFF3F4F6)
val AssistantBubbleDark = Color.Transparent

// Phase 58 (OG-02): OG card tokens. Dark container is the locked 2B2B29
// neutral (darkColorScheme surfaceVariant is 1F2937 — not a match, so a
// dedicated token); light reuses M3 surfaceVariant. Shimmer is a neutral
// gray pulse shown behind the Coil thumb while it loads.
val OgCardDark = Color(0xFF2B2B29)
val OgShimmer = Color(0xFF353534)
