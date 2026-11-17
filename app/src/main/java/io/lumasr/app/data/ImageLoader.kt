package io.lumasr.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Decodes an image with the correct orientation (via ImageDecoder) and
 * downscales the input so the engine's output stays under the pixel budget.
 */
object ImageLoader {
    private const val TAG = "LumaSR"
    private const val MAX_INPUT_EDGE = 8192

    data class Loaded(
        val bitmap: Bitmap,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val outputWidth: Int,
        val outputHeight: Int,
        val downscaled: Boolean,
    )

    suspend fun decodeUri(
        context: Context,
        uri: Uri,
        outputScale: Int,
        maxOutputPixels: Long,
    ): Loaded = decode(ImageDecoder.createSource(context.contentResolver, uri), outputScale, maxOutputPixels)

    suspend fun decodeFile(
        context: Context,
        file: File,
        outputScale: Int,
        maxOutputPixels: Long,
    ): Loaded = decode(ImageDecoder.createSource(file), outputScale, maxOutputPixels)

    private fun decode(
        source: ImageDecoder.Source,
        outputScale: Int,
        maxOutputPixels: Long,
    ): Loaded {
        var sourceWidth = 0
        var sourceHeight = 0
        var targetWidth = 0
        var targetHeight = 0

        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            sourceWidth = info.size.width
            sourceHeight = info.size.height
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false

            val factor = computeScaleFactor(sourceWidth, sourceHeight, outputScale, maxOutputPixels)
            targetWidth = (sourceWidth * factor).roundToInt().coerceAtLeast(1)
            targetHeight = (sourceHeight * factor).roundToInt().coerceAtLeast(1)
            if (factor < 0.999f) {
                decoder.setTargetSize(targetWidth, targetHeight)
            } else {
                targetWidth = sourceWidth
                targetHeight = sourceHeight
            }
        }

        val downscaled = targetWidth != sourceWidth || targetHeight != sourceHeight
        Log.i(
            TAG,
            "decode ${sourceWidth}x$sourceHeight -> ${bitmap.width}x${bitmap.height} " +
                "(scale ${outputScale}x, downscaled=$downscaled)",
        )
        return Loaded(
            bitmap = bitmap,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            outputWidth = bitmap.width * outputScale,
            outputHeight = bitmap.height * outputScale,
            downscaled = downscaled,
        )
    }

    private fun computeScaleFactor(
        width: Int,
        height: Int,
        scale: Int,
        maxOutputPixels: Long,
    ): Float {
        if (width <= 0 || height <= 0) return 1f

        val outputPixels = width.toDouble() * height * scale * scale
        val budgetFactor = if (outputPixels <= maxOutputPixels) {
            1.0
        } else {
            sqrt(maxOutputPixels.toDouble() / outputPixels)
        }

        val edgeFactor = min(1.0, MAX_INPUT_EDGE.toDouble() / max(width, height))
        return min(budgetFactor, edgeFactor).toFloat().coerceIn(0.01f, 1f)
    }
}
