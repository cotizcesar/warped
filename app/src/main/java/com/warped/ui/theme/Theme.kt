package com.warped.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight, onPrimary = OnPrimaryLight,
    surface = SurfaceLight, background = BackgroundLight,
    error = ErrorLight,
    surfaceVariant = Color(0xFFF3F4F6),
    outline = Color(0xFFD1D5DB),
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark, onPrimary = OnPrimaryDark,
    surface = SurfaceDark, background = BackgroundDark,
    error = ErrorDark,
    surfaceVariant = Color(0xFF1F2937),
    outline = Color(0xFF374151),
)

@Composable
fun WarpedTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicDarkColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = WarpedTypography,
        shapes = WarpedShapes,
        content = content
    )
}
