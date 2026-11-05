package com.gkeepdroid

import android.app.Activity
import android.os.Build
import android.view.Window
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** See-through system bars: the app's own background shows behind them. */
@Suppress("DEPRECATION")
private fun Window.makeBarsSeeThrough() {
    statusBarColor = android.graphics.Color.TRANSPARENT
    navigationBarColor = android.graphics.Color.TRANSPARENT
    // The system would put a scrim behind the three buttons; the app's background is wanted there.
    if (Build.VERSION.SDK_INT >= 29) isNavigationBarContrastEnforced = false
}

/** Applies the chosen theme mode; THEME_SYSTEM follows the phone's light/dark setting. */
@OptIn(ExperimentalLayoutApi::class)
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
        else -> expressiveLightColorScheme()
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
        // Edge to edge: the window reaches behind the system bars, so the app's background fills the whole screen
        // (no strip in another color above or below). The content keeps clear of the bars through the padding below.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.makeBarsSeeThrough()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (fullScreen) hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            else show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        // The strip next to a camera hole stays black unless the app may draw there.
        if (Build.VERSION.SDK_INT >= 28) {
            val attributes = window.attributes
            attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = attributes
        }
    }

    // Material 3 Expressive: springy motion and the expressive components' looks.
    MaterialExpressiveTheme(colorScheme = colors, motionScheme = MotionScheme.expressive()) {
        // The Surface provides the content color, so text is readable on the background.
        Surface(
            Modifier.fillMaxSize(),
            color = if (opaque) colors.background else Color.Transparent,
            contentColor = colors.onBackground,
        ) {
            Box(Modifier.fillMaxSize()) {
                // The content keeps clear of the bars, the keyboard and, in landscape, a camera hole at the side. The top
                // cutout is left out: the status bar covers it, and the full-screen demo draws into that strip on purpose.
                val clear = WindowInsets.systemBars.union(WindowInsets.ime).union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                Box(Modifier.windowInsetsPadding(clear)) { content() }
                // Debug builds only; it lies above everything and lets touches through.
                DebugWatermark()
            }
        }
    }
}
