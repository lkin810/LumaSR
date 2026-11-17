package io.lumasr.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import io.lumasr.app.model.SaveFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageSaver {
    private const val TAG = "LumaSR"
    private const val ALBUM = "Pictures/LumaSR"

    suspend fun save(
        context: Context,
        bitmap: Bitmap,
        format: SaveFormat,
        quality: Int,
        baseName: String,
        modelName: String,
        scale: Int,
    ): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = buildDisplayName(baseName, modelName, scale, format)

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, format.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, ALBUM)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = try {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        } catch (t: Throwable) {
            Log.e(TAG, "insert failed: ${t.message}", t)
            null
        } ?: return@withContext null

        try {
            val ok = resolver.openOutputStream(uri)?.use { output ->
                val compressFormat = when (format) {
                    SaveFormat.PNG -> Bitmap.CompressFormat.PNG
                    SaveFormat.JPEG -> Bitmap.CompressFormat.JPEG
                    SaveFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                }
                bitmap.compress(compressFormat, quality.coerceIn(1, 100), output)
            } ?: false

            if (!ok) {
                resolver.delete(uri, null, null)
                return@withContext null
            }

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            Log.i(TAG, "saved $displayName -> $uri")
            uri
        } catch (t: Throwable) {
            Log.e(TAG, "save failed: ${t.message}", t)
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun buildDisplayName(
        baseName: String,
        modelName: String,
        scale: Int,
        format: SaveFormat,
    ): String {
        val safeBase = baseName
            .substringBeforeLast('.')
            .replace(Regex("[^\\p{L}\\p{N}_-]"), "_")
            .take(36)
            .ifBlank { "image" }
        val safeModel = modelName
            .replace(Regex("[^\\p{L}\\p{N}_-]"), "_")
            .take(24)
        val stamp = System.currentTimeMillis()
        return "${safeBase}_${scale}x_${safeModel}_$stamp.${format.ext}"
    }
}
