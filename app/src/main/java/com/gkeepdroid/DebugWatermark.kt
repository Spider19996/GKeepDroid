package com.gkeepdroid

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment as GlanceAlignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.FontWeight as GlanceFontWeight
import androidx.glance.text.Text as GlanceText
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/** True in the debug build, which is its own app next to the release. */
fun isDebugBuild(context: Context): Boolean =
    context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

private val WATERMARK_COLOR = Color(0x55FF3B30)

/**
 * "DEBUG" across the screen of the debug build, drawn above everything. It takes no touches: taps
 * and scrolls go through it to what lies beneath.
 */
@Composable
fun DebugWatermark() {
    if (!isDebugBuild(LocalContext.current)) return
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(3) {
            Text("DEBUG", color = WATERMARK_COLOR, fontSize = 64.sp, fontWeight = FontWeight.Black, modifier = Modifier.rotate(-20f))
        }
    }
}

/** The same mark for the widgets (Glance cannot rotate text). Touches pass through it as well. */
@androidx.compose.runtime.Composable
fun DebugWatermarkGlance() {
    if (!isDebugBuild(androidx.glance.LocalContext.current)) return
    Box(GlanceModifier.fillMaxSize(), contentAlignment = GlanceAlignment.Center) {
        GlanceText(
            "DEBUG",
            style = TextStyle(color = ColorProvider(WATERMARK_COLOR), fontSize = 36.sp, fontWeight = GlanceFontWeight.Bold),
        )
    }
}
