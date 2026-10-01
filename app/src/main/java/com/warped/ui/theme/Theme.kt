package com.warped.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

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
    // API-02 (T-60-02): system-bar icon contrast follows the APP theme, not
    // the system night mode. enableEdgeToEdge() auto-detects from system
    // configuration, which mismatches when the app theme differs — derive
    // from darkTheme instead (dark bars get light icons and vice versa, never
    // forced). Platform APIs only: no extra dependency for a two-flag call.
    val view = LocalView.current
    SideEffect {
        if (!view.isInEditMode) {
            (view.context as? Activity)?.window?.let { window ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    window.insetsController?.setSystemBarsAppearance(
                        if (darkTheme) 0 else mask,
                        mask
                    )
                } else {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = if (darkTheme) {
                        window.decorView.systemUiVisibility and
                            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and
                            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                    } else {
                        window.decorView.systemUiVisibility or
                            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    }
                }
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = WarpedTypography,
        shapes = WarpedShapes,
        content = content
    )
}
