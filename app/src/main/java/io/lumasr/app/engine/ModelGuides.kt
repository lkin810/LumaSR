package io.lumasr.app.engine

data class ModelGuide(
    val modelId: Int,
    val tagline: String,
    val bestFor: String,
    val detail: String,
    val speed: String,
    val quality: String,
)

object ModelGuides {
    val all: List<ModelGuide> = listOf(
        ModelGuide(
            modelId = EngineModels.MODEL_CUGAN_SE,
            tagline = "漫画 / 线稿主力",
            bestFor = "黑白漫画、线稿、色块简单的插画",
            detail = "降噪档位最全，线条和色块比较稳。2x 最稳，3x/4x 适合小图；NPU 只支持 2x。",
            speed = "中",
            quality = "稳定",
        ),
        ModelGuide(
            modelId = EngineModels.MODEL_CUGAN_PRO,
            tagline = "更高质量的 CUGAN",
            bestFor = "细节多的漫画、彩图",
            detail = "比 SE 慢一些，线条和纹理更细。支持 2x/3x，NPU 也支持 2x/3x。",
            speed = "较慢",
            quality = "高",
        ),
        ModelGuide(
            modelId = EngineModels.MODEL_NOSE,
            tagline = "无降噪线稿专用",
            bestFor = "已经比较干净的线稿、扫描图",
            detail = "不做降噪，尽量保留原始线条。只有 2x，而且只有 Vulkan。",
            speed = "中",
            quality = "保留原始",
        ),
        ModelGuide(
            modelId = EngineModels.MODEL_ESRGAN,
            tagline = "动漫 / 照片通用",
            bestFor = "动漫插画、照片",
            detail = "动漫风格支持 2x/3x/4x，照片风格固定 2x。纹理和细节比较强；NPU 支持 2x。",
            speed = "中",
            quality = "高",
        ),
        ModelGuide(
            modelId = EngineModels.MODEL_WAIFU2X,
            tagline = "经典动漫放大",
            bestFor = "老动画、动漫截图",
            detail = "多档降噪，线条柔和。只有 2x，仅 Vulkan，速度偏慢。",
            speed = "慢",
            quality = "柔和",
        ),
        ModelGuide(
            modelId = EngineModels.MODEL_WAIFU2X_FAST,
            tagline = "轻量快速",
            bestFor = "批量预览、快速出图",
            detail = "2x，仅 Vulkan。比 Waifu2x 快，细节会少一些。",
            speed = "快",
            quality = "标准",
        ),
        ModelGuide(
            modelId = EngineModels.MODEL_ANIMEJANAI,
            tagline = "超紧凑动漫",
            bestFor = "动漫截图、批量",
            detail = "2x，仅 Vulkan。模型小、速度快，适合大批量。",
            speed = "快",
            quality = "标准",
        ),
        ModelGuide(
            modelId = EngineModels.MODEL_SUDO,
            tagline = "超紧凑小图",
            bestFor = "头像、小图标、小图",
            detail = "2x，仅 Vulkan。体积小，适合小图快速放大。",
            speed = "快",
            quality = "标准",
        ),
        ModelGuide(
            modelId = EngineModels.MODEL_SPAN,
            tagline = "照片细节",
            bestFor = "真实照片、风景、人像",
            detail = "2x，NPU 支持 2x。细节自然，速度稍慢。",
            speed = "中",
            quality = "高",
        ),
    )

    fun forModel(modelId: Int): ModelGuide? = all.firstOrNull { it.modelId == modelId }
}
