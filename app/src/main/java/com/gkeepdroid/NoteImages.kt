package com.gkeepdroid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import java.io.File

/** Small thumbnails of the images the sync downloaded, sized for widgets and the editor. */
object NoteImages {
    private const val MAX_WIDTH = 720
    private const val MAX_FULL_SIDE = 2048

    private fun thumbFile(context: Context, email: String, imageId: String) =
        File(File(context.cacheDir, "thumbs").apply { mkdirs() }, "${NotesCache.imageDir(context, email).name}_${imageId}_$MAX_WIDTH.jpg")

    /** Largest power-of-two subsampling that keeps [size] at or above [target]; 1 for small images. */
    private fun sampleSize(size: Int, target: Int): Int {
        var sample = 1
        while (size / (sample * 2) >= target) sample *= 2
        return sample
    }

    /**
     * Copies the original image to the cache with a proper extension so other apps can open it
     * through the FileProvider; returns null if the image is missing.
     */
    fun exportForSharing(context: Context, email: String, imageId: String): File? {
        val source = File(NotesCache.imageDir(context, email), imageId)
        if (!source.exists()) return null
        val header = ByteArray(4).also { buffer -> source.inputStream().use { it.read(buffer) } }
        val isPng = header[0] == 0x89.toByte() && header[1] == 'P'.code.toByte()
        val target = File(File(context.cacheDir, "shared").apply { mkdirs() }, "$imageId.${if (isPng) "png" else "jpg"}")
        if (!target.exists()) source.copyTo(target)
        return target
    }

    /** Returns the thumbnail of a note image, creating it on first use; null if not downloaded yet. */
    fun thumbnail(context: Context, email: String, imageId: String): Bitmap? {
        val source = File(NotesCache.imageDir(context, email), imageId)
        if (!source.exists()) return null
        val thumb = thumbFile(context, email, imageId)
        if (!thumb.exists()) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            if (bounds.outWidth <= 0) return null
            val sample = sampleSize(bounds.outWidth, MAX_WIDTH)
            val decoded = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
            val scaled = if (decoded.width > MAX_WIDTH) {
                Bitmap.createScaledBitmap(decoded, MAX_WIDTH, decoded.height * MAX_WIDTH / decoded.width, true)
            } else {
                decoded
            }
            // Drawings are transparent PNGs; JPEG has no alpha, so put them on white first.
            val flat = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888).also {
                Canvas(it).apply { drawColor(Color.WHITE); drawBitmap(scaled, 0f, 0f, null) }
            }
            thumb.outputStream().use { flat.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }
        return BitmapFactory.decodeFile(thumb.path)
    }

    /** The image in a resolution good enough for zooming (at most 2048 px on its long side). */
    fun full(context: Context, email: String, imageId: String): Bitmap? {
        val source = File(NotesCache.imageDir(context, email), imageId)
        if (!source.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0) return null
        val longSide = maxOf(bounds.outWidth, bounds.outHeight)
        val sample = sampleSize(longSide, MAX_FULL_SIDE)
        val decoded = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        // Drawings are transparent; show them on white like in Keep.
        return Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply { drawColor(Color.WHITE); drawBitmap(decoded, 0f, 0f, null) }
        }
    }
}
