package io.lumasr.app.benchmark

import io.lumasr.app.engine.Engine97

enum class BenchmarkBackendMode(val label: String) {
    VULKAN("Vulkan"),
    NPU("NPU"),
    BOTH("两者"),
}

data class BenchmarkVariant(
    val modelId: Int,
    val scale: Int,
    val noise: Int,
    val style: Int,
    val backend: Int,
)

data class BenchmarkResult(
    val modelId: Int = 0,
    val modelName: String = "",
    val scale: Int = 2,
    val noise: Int = 0,
    val style: Int = 0,
    val backend: Int = 0,
    val precision: Int = 0,
    val prepareMs: Long = 0L,
    val initMs: Long = 0L,
    val decodeMs: Long = 0L,
    val inferMs: Long = 0L,
    val encodeMs: Long = 0L,
    val totalMs: Long = 0L,
    val inputWidth: Int = 0,
    val inputHeight: Int = 0,
    val outputWidth: Int = 0,
    val outputHeight: Int = 0,
    val outputBytes: Long = 0L,
    val downscaled: Boolean = false,
    val actualBackend: Int = -1,
    val outputUri: String? = null,
    val error: String? = null,
) {
    val backendLabel: String
        get() = if (backend == Engine97.BACKEND_QUALCOMM_NPU) "NPU" else "Vulkan"

    val actualBackendLabel: String
        get() = if (actualBackend == Engine97.BACKEND_QUALCOMM_NPU) "NPU" else "Vulkan"

    val outputMp: Float
        get() = outputWidth.toFloat() * outputHeight / 1_000_000f
}
