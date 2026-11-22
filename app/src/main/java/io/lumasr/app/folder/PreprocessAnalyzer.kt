package io.lumasr.app.folder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.max

private const val SHARPNESS_LOW = 80f
private const val SHARPNESS_HIGH = 260f
private const val SAMPLE_MAX_EDGE = 512

enum class PreprocessAction {
    COPY,
    UPSCALE,
    SKIP,
}

enum class ResolutionBucket(val label: String, private val maxEdge: Int) {
    BELOW_1080("低于 1080p", 1919),
    FULL_HD("1080p", 2559),
    QHD("2K", 3839),
    UHD("4K 及以上", Int.MAX_VALUE),
    ;

    companion object {
        fun of(longEdge: Int): ResolutionBucket = entries.first { longEdge <= it.maxEdge }
    }
}

data class PreprocessItem(
    val image: FolderImage,
    val action: PreprocessAction,
    /** Laplacian variance on a 512 px sample; -1 when not measured. */
    val sharpness: Float,
) {
    val bucket: ResolutionBucket get() = ResolutionBucket.of(image.longEdge)

    val sharpnessLabel: String
        get() = when {
            sharpness < 0f -> "未检测"
            sharpness < SHARPNESS_LOW -> "偏模糊"
            sharpness < SHARPNESS_HIGH -> "一般"
            else -> "清晰"
        }
}

data class PreprocessReport(
    val items: List<PreprocessItem>,
    val skipLongEdge: Int,
) {
    val copyCount: Int get() = items.count { it.action == PreprocessAction.COPY }
    val upscaleCount: Int get() = items.count { it.action == PreprocessAction.UPSCALE }
    val skipCount: Int get() = items.count { it.action == PreprocessAction.SKIP }
    val totalBytes: Long get() = items.sumOf { it.image.sizeBytes }

    fun countFor(bucket: ResolutionBucket): Int = items.count { it.bucket == bucket }

    fun sharpnessCount(label: String): Int = items.count {
        it.action == PreprocessAction.UPSCALE && it.sharpnessLabel == label
    }

    val sharpnessMeasured: Int get() = items.count {
        it.action == PreprocessAction.UPSCALE && it.sharpness >= 0f
    }
}

object PreprocessAnalyzer {

    fun actionFor(
        image: FolderImage,
        skipLongEdge: Int,
        alreadyProcessed: Boolean,
    ): PreprocessAction = when {
        alreadyProcessed -> PreprocessAction.SKIP
        skipLongEdge > 0 && image.longEdge >= skipLongEdge -> PreprocessAction.COPY
        else -> PreprocessAction.UPSCALE
    }

    /**
     * Estimates detail level by decoding a downscaled sample and measuring
     * the variance of the Laplacian. It is a heuristic hint, not a quality
     * verdict: flat art, skies and heavy noise all skew the score.
     */
    fun sharpnessOf(context: Context, uri: Uri): Float = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return -1f

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SAMPLE_MAX_EDGE) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return -1f
        laplacianVariance(bitmap)
    }.getOrDefault(-1f)

    private fun laplacianVariance(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 3 || height < 3) {
            bitmap.recycle()
            return -1f
        }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()

        val gray = FloatArray(pixels.size) { index ->
            val color = pixels[index]
            (color shr 16 and 0xFF) * 0.299f +
                (color shr 8 and 0xFF) * 0.587f +
                (color and 0xFF) * 0.114f
        }

        var sum = 0.0
        var squareSum = 0.0
        var count = 0
        for (y in 1 until height - 1) {
            var index = y * width + 1
            for (x in 1 until width - 1) {
                val value = (
                    4 * gray[index] - gray[index - 1] - gray[index + 1] -
                        gray[index - width] - gray[index + width]
                    ).toDouble()
                sum += value
                squareSum += value * value
                count++
                index++
            }
        }
        if (count == 0) return -1f
        val mean = sum / count
        return (squareSum / count - mean * mean).toFloat().coerceAtLeast(0f)
    }
}
