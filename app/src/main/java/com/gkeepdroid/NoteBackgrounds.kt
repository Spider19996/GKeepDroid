package com.gkeepdroid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.caverock.androidsvg.SVG
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The pictures behind notes in Keep ("celebration", "food", ...). Keep serves them as SVG files; they are fetched once,
 * drawn to a bitmap and kept on the device.
 */
object NoteBackgrounds {
    /** The pictures Keep offers (the names are those of its own files). */
    val NAMES = listOf("celebration", "food", "grocery", "music", "notes", "places", "recipe", "travel", "video")

    private const val SIZE = 768
    private val memory = HashMap<String, Bitmap>()

    /** Null when the picture is not on the device yet and cannot be fetched now. Call off the main thread. */
    fun load(context: Context, name: String): Bitmap? {
        // The name comes from Keep's answer and ends up in a file name and an address.
        if (!name.matches(Regex("[a-z0-9_]{1,40}"))) return null
        synchronized(memory) { memory[name]?.let { return it } }
        val file = File(File(context.cacheDir, "backgrounds").apply { mkdirs() }, "$name.png")
        var bitmap = if (file.exists()) BitmapFactory.decodeFile(file.path) else null
        if (bitmap == null) {
            bitmap = runCatching { render(download(name)) }.getOrNull() ?: return null
            runCatching { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
        synchronized(memory) { memory[name] = bitmap }
        return bitmap
    }

    private val small = HashMap<String, Bitmap>()

    /**
     * The picture for a widget: small, with the card's transparency applied, and only from what is on the device
     * already (a widget is drawn without waiting for the network; the sync fetches the pictures).
     */
    fun forWidget(context: Context, name: String, alpha: Float): Bitmap? {
        if (!name.matches(Regex("[a-z0-9_]{1,40}"))) return null
        val key = "$name|${(alpha * 100).toInt()}"
        synchronized(small) { small[key]?.let { return it } }
        val file = File(File(context.cacheDir, "backgrounds"), "$name.png")
        val full = if (file.exists()) BitmapFactory.decodeFile(file.path) else null
        if (full == null) return null
        val bitmap = Bitmap.createBitmap(WIDGET_SIZE, WIDGET_SIZE, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawBitmap(
            full, null, android.graphics.Rect(0, 0, WIDGET_SIZE, WIDGET_SIZE),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply { this.alpha = (alpha * 255).toInt().coerceIn(0, 255) },
        )
        synchronized(small) { small[key] = bitmap }
        return bitmap
    }

    /** Fetches the pictures that notes of any account use; true if one was new, so the widgets should be redrawn. */
    fun prefetch(context: Context): Boolean {
        var fetched = false
        CredentialStore.accounts(context).forEach { account ->
            RichNotes.backgrounds(context, account).forEach { name ->
                if (!File(File(context.cacheDir, "backgrounds"), "$name.png").exists() && load(context, name) != null) fetched = true
            }
        }
        return fetched
    }

    private const val WIDGET_SIZE = 360

    private fun download(name: String): ByteArray {
        val connection = URL("https://www.gstatic.com/keep/backgrounds/${name}_light_0609.svg").openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        return try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun render(svgBytes: ByteArray): Bitmap {
        val svg = SVG.getFromInputStream(svgBytes.inputStream())
        svg.setDocumentWidth(SIZE.toFloat())
        svg.setDocumentHeight(SIZE.toFloat())
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        svg.renderToCanvas(Canvas(bitmap))
        return bitmap
    }
}

/** The picture fills the card and is cut at the top and left, so the lower right corner always shows, as in Keep. */
fun Modifier.noteBackground(bitmap: Bitmap): Modifier {
    val image = bitmap.asImageBitmap()
    return drawBehind {
        val scale = maxOf(size.width / image.width, size.height / image.height)
        val visible = Size(size.width / scale, size.height / scale)
        drawImage(
            image,
            srcOffset = IntOffset((image.width - visible.width).toInt(), (image.height - visible.height).toInt()),
            srcSize = IntSize(visible.width.toInt().coerceAtLeast(1), visible.height.toInt().coerceAtLeast(1)),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        )
    }
}
