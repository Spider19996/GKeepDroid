package com.gkeepdroid

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.Alignment as GlanceAlignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize

/** True in the debug build, which is its own app next to the release. */
fun isDebugBuild(context: Context): Boolean =
    context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

private val WATERMARK_COLOR = Color(0x55FF3B30)

// Widgets sit on any wallpaper, so their mark is stronger.
private val WIDGET_WATERMARK_COLOR = Color(0xCCFF3B30)

/**
 * "DEBUG" across the screen of the debug build, drawn above everything. It takes no touches: taps
 * and scrolls go through it to what lies beneath.
 */
@Composable
fun DebugWatermark() {
    val context = LocalContext.current
    // In demo mode (for screenshots) the mark stays away.
    if (!isDebugBuild(context) || DemoNotes.version.intValue < 0 || DemoNotes.enabled(context)) return
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("DEBUG", color = WATERMARK_COLOR, fontSize = 64.sp, fontWeight = FontWeight.Black, modifier = Modifier.rotate(-20f))
    }
}

/** The same mark for the widgets: Glance cannot rotate text, so it is drawn once as a tilted picture. Touches pass through it as well. */
@Composable
fun DebugWatermarkGlance() {
    val context = androidx.glance.LocalContext.current
    if (!isDebugBuild(context) || DemoNotes.enabled(context)) return
    val picture = androidx.compose.runtime.remember { debugMarkBitmap(context) }
    Box(GlanceModifier.fillMaxSize(), contentAlignment = GlanceAlignment.Center) {
        Image(ImageProvider(picture), contentDescription = null)
    }
}

/** "DEBUG" as the app draws it (64 sp, black weight, tilted by 20 degrees), in the stronger widget color. */
private fun debugMarkBitmap(context: Context): Bitmap {
    val density = context.resources.displayMetrics.scaledDensity
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WIDGET_WATERMARK_COLOR.toArgb()
        textSize = 64f * density
        typeface = Typeface.create(Typeface.DEFAULT, 900, false)
    }
    val text = "DEBUG"
    val bounds = Rect().also { paint.getTextBounds(text, 0, text.length, it) }
    val w = bounds.width().toFloat()
    val h = bounds.height().toFloat()
    val angle = Math.toRadians(20.0)
    val outW = (w * Math.cos(angle) + h * Math.sin(angle)).toInt() + 4
    val outH = (w * Math.sin(angle) + h * Math.cos(angle)).toInt() + 4
    val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.translate(outW / 2f, outH / 2f)
    canvas.rotate(-20f)
    canvas.drawText(text, -w / 2f - bounds.left, h / 2f - bounds.bottom, paint)
    return bitmap
}
