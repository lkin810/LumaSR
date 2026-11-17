package io.lumasr.app.model

import android.net.Uri
import eu.kanade.tachiyomi.util.qnn.QnnModelContexts
import io.lumasr.app.engine.Engine97
import io.lumasr.app.engine.EngineModel
import io.lumasr.app.engine.EngineModels

enum class SaveFormat(
    val label: String,
    val mime: String,
    val ext: String,
    val needsQuality: Boolean,
) {
    PNG("PNG", "image/png", "png", false),
    WEBP("WebP", "image/webp", "webp", true),
    JPEG("JPEG", "image/jpeg", "jpg", true),
}

data class UpscaleConfig(
    val modelId: Int = EngineModels.default.id,
    val scale: Int = 2,
    val noise: Int = 4,
    val style: Int = EngineModels.STYLE_ANIME,
    val backend: Int = Engine97.BACKEND_VULKAN,
    val precision: Int = Engine97.PRECISION_FP16,
    val maxOutputMp: Int = 24,
    val format: SaveFormat = SaveFormat.PNG,
    val quality: Int = 95,
    val tileSize: Int = 128,
    val recursiveScan: Boolean = true,
    val preserveStructure: Boolean = true,
    val skipProcessed: Boolean = true,
    val outputFolderName: String = "LumaSR_Output",
    val notifications: Boolean = true,
) {
    val model: EngineModel get() = EngineModels.byIdOrDefault(modelId)
    val outputScale: Int get() = EngineModels.outputScale(modelId, scale, style)
    val maxOutputPixels: Long get() = maxOutputMp * 1_000_000L
    val backendLabel: String
        get() = if (backend == Engine97.BACKEND_QUALCOMM_NPU) "Qualcomm NPU" else "Vulkan GPU"

    /** Keeps model / scale / noise / style / precision mutually valid. */
    fun sanitized(): UpscaleConfig {
        val model = EngineModels.byIdOrDefault(modelId)
        val nextScale = if (scale in model.scales) scale else model.scales.first()
        val nextNoise = if (model.noises.isEmpty() || noise in model.noises) {
            noise
        } else {
            model.noises.last()
        }
        val nextStyle = if (model.hasStyle) style else EngineModels.STYLE_ANIME
        val nextPrecision = when {
            backend == Engine97.BACKEND_QUALCOMM_NPU && precision != Engine97.PRECISION_FP16 -> {
                val name = Engine97.qnnModelName(modelId, nextScale, nextNoise, nextStyle)
                if (name != null && QnnModelContexts.supportsW8a8(name)) {
                    Engine97.PRECISION_INT8
                } else {
                    Engine97.PRECISION_FP16
                }
            }
            backend == Engine97.BACKEND_VULKAN && precision !in listOf(0, 1, 3) ->
                Engine97.PRECISION_FP16
            else -> precision
        }
        return copy(
            scale = nextScale,
            noise = nextNoise,
            style = nextStyle,
            precision = nextPrecision,
            tileSize = tileSize.takeIf { it in TILE_SIZES } ?: DEFAULT_TILE_SIZE,
        )
    }

    /**
     * Why the selected backend cannot run right now. The user's choice is kept
     * (instead of silently switching to Vulkan), the UI explains the reason and
     * the worker falls back explicitly for the current run.
     */
    fun backendIssue(npuAvailable: Boolean): String? {
        if (backend != Engine97.BACKEND_QUALCOMM_NPU) return null
        if (!npuAvailable) return "未检测到 Qualcomm NPU（需要 Snapdragon + QNN）"
        if (!EngineModels.npuSupports(modelId, scale, noise, style)) {
            return "当前模型 / 倍数 / 降噪不支持 NPU，请改用 Vulkan"
        }
        if (precision == Engine97.PRECISION_INT8) {
            val name = Engine97.qnnModelName(modelId, scale, noise, style)
            if (name == null || !QnnModelContexts.supportsW8a8(name)) {
                return "当前模型不支持 INT8，已按 FP16 运行"
            }
        }
        return null
    }

    companion object {
        val TILE_SIZES = listOf(128, 192, 256)
        const val DEFAULT_TILE_SIZE = 128
    }
}

data class CompareInfo(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    val modelName: String,
    val elapsedMs: Long,
    val downscaledInput: Boolean,
)

sealed interface Dest {
    data object Home : Dest
    data object Tasks : Dest
    data object Settings : Dest
    data object Models : Dest
    data object Benchmark : Dest
    data object ModelGuide : Dest
    data object PerfLog : Dest
    data class FolderOverview(val treeUri: Uri) : Dest
    data class Grid(val treeUri: Uri) : Dest
    data class BatchConfig(val treeUri: Uri) : Dest
    data class Configure(val uris: List<Uri>, val batch: Boolean) : Dest
    data object Processing : Dest
    data object Compare : Dest
}
