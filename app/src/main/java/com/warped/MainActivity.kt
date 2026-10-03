package com.warped

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.warped.ui.navigation.WarpedNavGraph
import com.warped.ui.theme.WarpedTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // No splashscreen-compat (removed 3.1.1): core-splashscreen calls
        // the API-35-deprecated Window.setStatusBarColor /
        // setNavigationBarColor internally (Play flags it) with no upgrade
        // path (1.2.0 is latest). API 31+ still shows the platform splash
        // from windowSplashScreenBackground in themes.xml; older devices
        // launch straight into content over windowBackground.
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            WarpedTheme {
                WarpedNavGraph()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return

        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }
}
