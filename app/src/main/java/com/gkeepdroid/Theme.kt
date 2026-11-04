package com.gkeepdroid

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Applies the chosen theme mode; THEME_SYSTEM follows the phone's light/dark setting. */
@Composable
fun KeepTheme(themeMode: Int, opaque: Boolean = true, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        THEME_LIGHT -> false
        THEME_DARK, THEME_OLED -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    var colors: ColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    if (themeMode == THEME_OLED) {
        // True black saves power on OLED screens.
        colors = colors.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color(0xFF121212),
            surfaceContainerHigh = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF222222),
        )
    }

    val view = LocalView.current
    // Demo mode (for screenshots) runs full screen: the status bar and the three buttons are hidden, and a swipe from the edge brings them back.
    val fullScreen = DemoNotes.version.intValue >= 0 && DemoNotes.enabled(context)
    // Switching the demo mode on or off changes how the window is laid out; a fresh activity does that cleanly.
    val shownFullScreen = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(fullScreen) }
    SideEffect {
        if (shownFullScreen.value != fullScreen) {
            shownFullScreen.value = fullScreen
            (view.context as Activity).recreate()
            return@SideEffect
        }
        val window = (view.context as Activity).window
        // The window sits below the system bars; hiding the status bar alone leaves its strip empty, so demo mode lets the app draw there.
        WindowCompat.setDecorFitsSystemWindows(window, !fullScreen)
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (fullScreen) hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            else show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        // Hiding the bar is not enough on a phone with a camera hole: the strip next to it stays black unless the app may draw there.
        if (Build.VERSION.SDK_INT >= 28) {
            val attributes = window.attributes
            attributes.layoutInDisplayCutoutMode = if (fullScreen) {
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
            window.attributes = attributes
        }
    }

    MaterialTheme(colorScheme = colors) {
        // The Surface provides the content color, so text is readable on the background.
        Surface(
            Modifier.fillMaxSize(),
            color = if (opaque) colors.background else Color.Transparent,
            contentColor = colors.onBackground,
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.systemBarsPadding().imePadding()) { content() }
                // Debug builds only; it lies above everything and lets touches through.
                DebugWatermark()
            }
        }
    }
}
