package com.gkeepdroid

import android.app.Activity
import android.os.Build
import android.view.Window
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

/** A color and a picture behind a whole screen; the note editor sets them while a note has them. One per activity. */
class ScreenTint {
    var color by androidx.compose.runtime.mutableStateOf<Int?>(null)
    var image by androidx.compose.runtime.mutableStateOf<android.graphics.Bitmap?>(null)
    /** The picture is the dark version of one of Keep's, so the screen stays dark and its text light. */
    var darkImage by androidx.compose.runtime.mutableStateOf(false)
}

/** Whether the app is in a dark theme (dark or OLED, or the phone's dark mode when the app follows it). */
val LocalDarkTheme = androidx.compose.runtime.compositionLocalOf { false }

/** True where content sits on a note's own color or picture. */
val LocalOnNote = androidx.compose.runtime.compositionLocalOf { false }

private fun lerpScheme(a: ColorScheme, b: ColorScheme, f: Float): ColorScheme {
    fun mix(x: Color, y: Color) = androidx.compose.ui.graphics.lerp(x, y, f)
    return b.copy(
        primary = mix(a.primary, b.primary), onPrimary = mix(a.onPrimary, b.onPrimary),
        primaryContainer = mix(a.primaryContainer, b.primaryContainer), onPrimaryContainer = mix(a.onPrimaryContainer, b.onPrimaryContainer),
        inversePrimary = mix(a.inversePrimary, b.inversePrimary),
        secondary = mix(a.secondary, b.secondary), onSecondary = mix(a.onSecondary, b.onSecondary),
        secondaryContainer = mix(a.secondaryContainer, b.secondaryContainer), onSecondaryContainer = mix(a.onSecondaryContainer, b.onSecondaryContainer),
        tertiary = mix(a.tertiary, b.tertiary), onTertiary = mix(a.onTertiary, b.onTertiary),
        tertiaryContainer = mix(a.tertiaryContainer, b.tertiaryContainer), onTertiaryContainer = mix(a.onTertiaryContainer, b.onTertiaryContainer),
        background = mix(a.background, b.background), onBackground = mix(a.onBackground, b.onBackground),
        surface = mix(a.surface, b.surface), onSurface = mix(a.onSurface, b.onSurface),
        surfaceVariant = mix(a.surfaceVariant, b.surfaceVariant), onSurfaceVariant = mix(a.onSurfaceVariant, b.onSurfaceVariant),
        surfaceTint = mix(a.surfaceTint, b.surfaceTint),
        inverseSurface = mix(a.inverseSurface, b.inverseSurface), inverseOnSurface = mix(a.inverseOnSurface, b.inverseOnSurface),
        error = mix(a.error, b.error), onError = mix(a.onError, b.onError),
        errorContainer = mix(a.errorContainer, b.errorContainer), onErrorContainer = mix(a.onErrorContainer, b.onErrorContainer),
        outline = mix(a.outline, b.outline), outlineVariant = mix(a.outlineVariant, b.outlineVariant), scrim = mix(a.scrim, b.scrim),
        surfaceBright = mix(a.surfaceBright, b.surfaceBright), surfaceDim = mix(a.surfaceDim, b.surfaceDim),
        surfaceContainer = mix(a.surfaceContainer, b.surfaceContainer), surfaceContainerHigh = mix(a.surfaceContainerHigh, b.surfaceContainerHigh),
        surfaceContainerHighest = mix(a.surfaceContainerHighest, b.surfaceContainerHighest),
        surfaceContainerLow = mix(a.surfaceContainerLow, b.surfaceContainerLow), surfaceContainerLowest = mix(a.surfaceContainerLowest, b.surfaceContainerLowest),
    )
}

/** The scheme to show: when [target] changes (the key tells when), the colors fade over to it instead of jumping. */
@Composable
private fun fadingScheme(target: ColorScheme, key: Any?): ColorScheme {
    var from by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(target) }
    var to by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(target) }
    val progress = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(1f) }
    val latest by androidx.compose.runtime.rememberUpdatedState(target)
    var shownKey by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(key) }
    androidx.compose.runtime.LaunchedEffect(key) {
        // Nothing changed (the first run, or the same look again): no fade, which would redraw the whole screen every frame for nothing.
        if (key == shownKey) return@LaunchedEffect
        shownKey = key
        from = lerpScheme(from, to, progress.value)
        to = latest
        progress.snapTo(0f)
        progress.animateTo(1f, androidx.compose.animation.core.tween(450))
    }
    return if (progress.value >= 1f) to else lerpScheme(from, to, progress.value)
}

/**
 * Light colors and dark text for content that sits on a note's own color or picture. The wrapper is always there (only the
 * colors change), so switching a color or picture on or off does not rebuild the content and lose its state, such as the scroll position.
 */
@Composable
fun OnNoteTheme(
    active: Boolean, accent: Int? = null, transparentRows: Boolean = active,
    darkOnPicture: Boolean = false, textColor: Int? = null, content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val outer = MaterialTheme.colorScheme
    val outerContent = androidx.compose.material3.LocalContentColor.current
    val darkTheme = LocalDarkTheme.current
    var scheme = when {
        // The dark version of a picture needs light text, also in a light theme.
        darkOnPicture -> if (darkTheme) outer else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context) else darkColorScheme()
        !active -> outer
        else -> {
            val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicLightColorScheme(context) else expressiveLightColorScheme()
            if (accent == null) base else {
                // Buttons in a dark shade of the note's hue, their containers in a light one, so they sit well on the note.
                val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(accent, it) }
                val dark = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], (hsv[1] + 0.35f).coerceIn(0.45f, 0.9f), 0.34f)))
                val light = androidx.compose.ui.graphics.lerp(Color(accent), Color.White, 0.55f)
                base.copy(
                    primary = dark, onPrimary = Color.White,
                    primaryContainer = light, onPrimaryContainer = dark,
                    secondaryContainer = light, onSecondaryContainer = dark,
                    secondary = dark, onSecondary = Color.White,
                    surfaceTint = dark, outline = dark.copy(alpha = 0.6f),
                )
            }
        }
    }
    // A text color of the note's own wins over the theme's.
    textColor?.let {
        val own = Color(it)
        scheme = scheme.copy(onSurface = own, onBackground = own, onSurfaceVariant = own.copy(alpha = 0.75f))
    }
    val shown = fadingScheme(scheme, listOf(active, accent, darkOnPicture, textColor))
    val target = textColor?.let { Color(it) } ?: if (darkOnPicture) scheme.onSurface else if (active) DarkNoteText else outerContent
    // The text color follows the fade as well.
    val contentColor by androidx.compose.animation.animateColorAsState(target, androidx.compose.animation.core.tween(450), label = "content")
    MaterialExpressiveTheme(colorScheme = shown, motionScheme = MotionScheme.expressive()) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides contentColor,
            LocalOnNote provides transparentRows,
        ) { content() }
    }
}

/** Applies the chosen theme mode; THEME_SYSTEM follows the phone's light/dark setting. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeepTheme(themeMode: Int, opaque: Boolean = true, tint: ScreenTint? = null, content: @Composable () -> Unit) {
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
    val tinted = tint != null && (if (tint.image != null) !tint.darkImage else tint.color != null)
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
            isAppearanceLightStatusBars = !dark || tinted
            isAppearanceLightNavigationBars = !dark || tinted
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
            // A color or picture behind the screen fades in and out.
            val tintColor by androidx.compose.animation.animateColorAsState(
                tint?.color?.let { Color(it) } ?: Color.Transparent, androidx.compose.animation.core.tween(450), label = "tint",
            )
            Box(Modifier.fillMaxSize().background(tintColor)) {
                androidx.compose.animation.Crossfade(targetState = tint?.image, animationSpec = androidx.compose.animation.core.tween(450), label = "tintImage") { image ->
                    if (image != null) Box(Modifier.fillMaxSize().noteBackground(image))
                }
                // The content keeps clear of the bars, the keyboard and, in landscape, a camera hole at the side. The top
                // cutout is left out: the status bar covers it, and the full-screen demo draws into that strip on purpose.
                val clear = WindowInsets.systemBars.union(WindowInsets.ime).union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                androidx.compose.runtime.CompositionLocalProvider(LocalDarkTheme provides dark) {
                    Box(Modifier.windowInsetsPadding(clear)) { content() }
                }
                // Debug builds only; it lies above everything and lets touches through.
                DebugWatermark()
            }
        }
    }
}
