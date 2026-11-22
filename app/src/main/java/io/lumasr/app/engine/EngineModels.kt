package io.lumasr.app.engine

enum class ModelCategory(val label: String) {
    MANGA("漫画"),
    ANIME("动漫"),
    PHOTO("照片"),
}

/**
 * One model family from the Mihon Upscale engine.
 *
 * [scales] are the Vulkan output scales; [npuScales] are the scales supported
 * by the Qualcomm QNN backend. [noises] are Real-CUGAN denoise levels
 * (0 = off, 1..3 = denoise strength, 4 = conservative).
 */
data class EngineModel(
    val id: Int,
    val name: String,
    val category: ModelCategory,
    val description: String,
    val scales: List<Int>,
    val noises: List<Int> = emptyList(),
    val hasStyle: Boolean = false,
    val npuScales: List<Int> = emptyList(),
    val npuNoises: List<Int> = emptyList(),
    val npuStyles: Boolean = false,
)

object EngineModels {
    const val MODEL_CUGAN_SE = 0
    const val MODEL_CUGAN_PRO = 1
    const val MODEL_ESRGAN = 2
    const val MODEL_NOSE = 3
    const val MODEL_WAIFU2X = 4
    const val MODEL_WAIFU2X_FAST = 5
    const val MODEL_ANIMEJANAI = 16
    const val MODEL_SUDO = 18
    const val MODEL_SPAN = 19

    const val STYLE_ANIME = 0
    const val STYLE_PHOTO = 1

    val all: List<EngineModel> = listOf(
        EngineModel(
            id = MODEL_CUGAN_SE,
            name = "Real-CUGAN SE",
            category = ModelCategory.MANGA,
            description = "漫画/线稿主力模型，支持 2x/3x/4x 与多档降噪",
            scales = listOf(2, 3, 4),
            noises = listOf(0, 1, 2, 3, 4),
            npuScales = listOf(2),
            npuNoises = listOf(0, 1, 2, 3, 4),
        ),
        EngineModel(
            id = MODEL_CUGAN_PRO,
            name = "Real-CUGAN Pro",
            category = ModelCategory.MANGA,
            description = "更高质量，2x/3x，降噪档位较少",
            scales = listOf(2, 3),
            noises = listOf(0, 3, 4),
            npuScales = listOf(2, 3),
            npuNoises = listOf(0, 3, 4),
        ),
        EngineModel(
            id = MODEL_NOSE,
            name = "Real-CUGAN Nose",
            category = ModelCategory.MANGA,
            description = "无降噪线稿模型，2x，仅 Vulkan",
            scales = listOf(2),
        ),
        EngineModel(
            id = MODEL_ESRGAN,
            name = "Real-ESRGAN",
            category = ModelCategory.ANIME,
            description = "动漫 2x/3x/4x；照片风格固定输出 2x",
            scales = listOf(2, 3, 4),
            hasStyle = true,
            npuScales = listOf(2),
            npuStyles = true,
        ),
        EngineModel(
            id = MODEL_WAIFU2X,
            name = "Waifu2x",
            category = ModelCategory.ANIME,
            description = "经典动漫放大，2x，多档降噪",
            scales = listOf(2),
            noises = listOf(0, 1, 2, 3),
        ),
        EngineModel(
            id = MODEL_WAIFU2X_FAST,
            name = "Waifu2x Fast",
            category = ModelCategory.ANIME,
            description = "Waifu2x 快速版，2x",
            scales = listOf(2),
            noises = listOf(0, 1, 2, 3),
        ),
        EngineModel(
            id = MODEL_ANIMEJANAI,
            name = "AnimeJaNai v2 UltraCompact",
            category = ModelCategory.ANIME,
            description = "超紧凑动漫模型，2x",
            scales = listOf(2),
        ),
        EngineModel(
            id = MODEL_SUDO,
            name = "sudo UltraCompact",
            category = ModelCategory.ANIME,
            description = "超紧凑 2x，适合小图",
            scales = listOf(2),
        ),
        EngineModel(
            id = MODEL_SPAN,
            name = "SPAN NomosUni Photo",
            category = ModelCategory.PHOTO,
            description = "照片 2x，细节自然，支持 NPU",
            scales = listOf(2),
            npuScales = listOf(2),
        ),
    )

    val default: EngineModel = all.first()

    fun byId(id: Int): EngineModel? = all.firstOrNull { it.id == id }

    fun byIdOrDefault(id: Int): EngineModel = byId(id) ?: default

    fun forCategory(category: ModelCategory): List<EngineModel> = all.filter { it.category == category }

    /** Real-ESRGAN photo style always outputs 2x even though the weights are 4x. */
    fun outputScale(modelId: Int, scale: Int, style: Int): Int {
        val model = byIdOrDefault(modelId)
        if (modelId == MODEL_ESRGAN) {
            return if (style == STYLE_PHOTO) 2 else scale.coerceIn(2, 4)
        }
        return if (scale in model.scales) scale else model.scales.firstOrNull() ?: 2
    }

    fun noiseLabel(noise: Int): String = when (noise) {
        0 -> "无降噪"
        1 -> "降噪 1"
        2 -> "降噪 2"
        3 -> "降噪 3"
        4 -> "保守"
        else -> "降噪 $noise"
    }

    fun styleLabel(style: Int): String = if (style == STYLE_PHOTO) "照片" else "动漫"

    /** Stable ASCII prefix for benchmark output filenames. */
    fun filePrefix(modelId: Int): String = when (modelId) {
        MODEL_CUGAN_SE -> "cugan-se"
        MODEL_CUGAN_PRO -> "cugan-pro"
        MODEL_ESRGAN -> "esrgan"
        MODEL_NOSE -> "cugan-nose"
        MODEL_WAIFU2X -> "waifu2x"
        MODEL_WAIFU2X_FAST -> "waifu2x-fast"
        MODEL_ANIMEJANAI -> "animejanai"
        MODEL_SUDO -> "sudo"
        MODEL_SPAN -> "span"
        else -> "model-$modelId"
    }

    fun npuSupports(modelId: Int, scale: Int, noise: Int, style: Int): Boolean {
        val model = byId(modelId) ?: return false
        if (model.npuScales.isEmpty()) return false
        val effectiveScale = outputScale(modelId, scale, style)
        if (effectiveScale !in model.npuScales) return false
        if (model.npuNoises.isNotEmpty() && noise !in model.npuNoises) return false
        if (model.hasStyle && !model.npuStyles) return false
        return true
    }
}
