package app.danebrain.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

object ImageUtil {
    private const val MAX_EDGE = 2000
    private const val TARGET_BYTES = 500 * 1024

    /**
     * Copies an image into [dest] as an upright JPEG, long edge at most [MAX_EDGE] px and about
     * [TARGET_BYTES] or less, so scans stay legible without filling Obsidian Sync storage.
     */
    fun importJpeg(context: Context, source: Uri, dest: File) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // A bounds-only decode returns null by design, so only a missing stream counts as failure here.
        val stream = resolver.openInputStream(source) ?: throw IOException("Could not open image")
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not a readable image")

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = resolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("Could not decode image")

        val rotation = try {
            resolver.openInputStream(source)?.use { ExifInterface(it).rotationDegrees } ?: 0
        } catch (e: Exception) {
            0
        }
        val longEdge = maxOf(decoded.width, decoded.height)
        val scale = if (longEdge > MAX_EDGE) MAX_EDGE.toFloat() / longEdge else 1f
        val bitmap = if (scale != 1f || rotation != 0) {
            val m = Matrix().apply {
                postScale(scale, scale)
                if (rotation != 0) postRotate(rotation.toFloat())
            }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        } else decoded

        var quality = 85
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 8
        } while (bytes.size > TARGET_BYTES && quality >= 50)

        dest.parentFile?.mkdirs()
        dest.writeBytes(bytes)
    }

    /** A downsampled bitmap for on-screen preview. */
    fun preview(path: String, maxEdge: Int = 1200): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
