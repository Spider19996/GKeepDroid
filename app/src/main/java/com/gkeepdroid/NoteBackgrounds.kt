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
    /** The average color of a picture, as ARGB. */
    fun averageColor(bitmap: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(bitmap, 16, 16, true)
        var r = 0L; var g = 0L; var b = 0L
        val pixels = IntArray(16 * 16).also { small.getPixels(it, 0, 16, 0, 0, 16, 16) }
        pixels.forEach { r += android.graphics.Color.red(it); g += android.graphics.Color.green(it); b += android.graphics.Color.blue(it) }
        val n = pixels.size
        return android.graphics.Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    /** The pictures Keep offers; these are the names its server accepts (checked by setting each on a test note). */
    val NAMES = listOf("celebration", "food", "groceries", "music", "notes", "places", "recipes", "travel", "video")

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
     * The picture for a widget card: the lower part of it ([fraction] of its height, as in the app, where the artwork sits at
     * the bottom), small, with the card's transparency applied, and only from what is on the device already (a widget is drawn
     * without waiting for the network; the sync fetches the pictures).
     */
    fun forWidget(context: Context, name: String, alpha: Float, fraction: Float): Bitmap? {
        if (!name.matches(Regex("[a-z0-9_]{1,40}"))) return null
        val key = "$name|${(alpha * 100).toInt()}|${(fraction * 100).toInt()}"
        synchronized(small) { small[key]?.let { return it } }
        val file = File(File(context.cacheDir, "backgrounds"), "$name.png")
        val full = if (file.exists()) BitmapFactory.decodeFile(file.path) else null
        if (full == null) return null
        val bitmap = widgetCrop(full, alpha, fraction, fromBottom = true)
        synchronized(small) { small[key] = bitmap }
        return bitmap
    }

    /** [full] cut to the shape of a widget card (width : height = 1 : [fraction]) and shrunk; the cut is at the bottom or in the middle. */
    internal fun widgetCrop(full: Bitmap, alpha: Float, fraction: Float, fromBottom: Boolean): Bitmap {
        val height = (WIDGET_SIZE * fraction).toInt().coerceAtLeast(1)
        // The part of the picture that has the card's proportions, as wide as possible.
        var sourceWidth = full.width
        var sourceHeight = (full.width * fraction).toInt()
        if (sourceHeight > full.height) {
            sourceHeight = full.height
            sourceWidth = (full.height / fraction).toInt()
        }
        val left = (full.width - sourceWidth) / 2
        val top = if (fromBottom) full.height - sourceHeight else (full.height - sourceHeight) / 2
        val bitmap = Bitmap.createBitmap(WIDGET_SIZE, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawBitmap(
            full, android.graphics.Rect(left, top, left + sourceWidth, top + sourceHeight), android.graphics.Rect(0, 0, WIDGET_SIZE, height),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply { this.alpha = (alpha * 255).toInt().coerceIn(0, 255) },
        )
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
        // Two pictures are named differently in their files than in the notes.
        val file = when (name) { "groceries" -> "grocery"; "recipes" -> "recipe"; else -> name }
        val connection = URL("https://www.gstatic.com/keep/backgrounds/${file}_light_0609.svg").openConnection() as HttpURLConnection
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


/**
 * A photo of the user's own as the background of a note. Keep has no such thing, so it stays on this phone: the app and its
 * widgets show it, Keep does not know about it. A white veil over it keeps the dark text readable.
 */
object CustomBackgrounds {
    private const val MAX = 1280

    private val memory = HashMap<String, Pair<Long, Bitmap>>()

    private fun file(context: Context, account: String, noteId: String): File {
        val name = java.security.MessageDigest.getInstance("SHA-1").digest("$account|$noteId".toByteArray())
            .joinToString("") { "%02x".format(it) }.take(24)
        return File(File(context.filesDir, "custom_backgrounds").apply { mkdirs() }, "$name.jpg")
    }

    /** Changes whenever the note's photo changes; 0 if it has none. */
    fun stamp(context: Context, account: String, noteId: String): Long = file(context, account, noteId).let { if (it.exists()) it.lastModified() else 0L }

    fun remove(context: Context, account: String, noteId: String) {
        file(context, account, noteId).delete()
    }

    /** Keeps the photo behind [uri] as the note's background, shrunk to a size that is enough for a screen. */
    fun save(context: Context, account: String, noteId: String, uri: android.net.Uri): Boolean {
        val bitmap = decode(context, uri) ?: return false
        return runCatching { file(context, account, noteId).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) } }.getOrDefault(false)
    }

    private fun decode(context: Context, uri: android.net.Uri): Bitmap? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX || bounds.outHeight / (sample * 2) >= MAX) sample *= 2
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }.getOrNull()

    private fun veil(bitmap: Bitmap): Bitmap {
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(copy).drawColor(android.graphics.Color.argb(150, 255, 255, 255))
        return copy
    }

    /** The saved photo with the veil; call off the main thread. */
    fun load(context: Context, account: String, noteId: String): Bitmap? {
        val file = file(context, account, noteId)
        if (!file.exists()) return null
        val key = file.path
        synchronized(memory) { memory[key]?.let { if (it.first == file.lastModified()) return it.second } }
        val bitmap = BitmapFactory.decodeFile(file.path)?.let(::veil) ?: return null
        synchronized(memory) { memory[key] = file.lastModified() to bitmap }
        return bitmap
    }

    /** A photo that was just picked, before it is saved. */
    fun preview(context: Context, uri: android.net.Uri): Bitmap? = decode(context, uri)?.let(::veil)

    /** The photo for a widget card, in the card's proportions and with its transparency. */
    fun forWidget(context: Context, account: String, noteId: String, alpha: Float, fraction: Float): Bitmap? {
        val photo = BitmapFactory.decodeFile(file(context, account, noteId).takeIf { it.exists() }?.path ?: return null) ?: return null
        return NoteBackgrounds.widgetCrop(veil(photo), alpha, fraction, fromBottom = false)
    }
}
