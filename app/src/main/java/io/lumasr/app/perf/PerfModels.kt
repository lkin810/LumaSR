package io.lumasr.app.perf

import kotlinx.serialization.Serializable

/** One processed image inside a run. Stage times are measured with elapsedRealtime. */
@Serializable
data class PerfImage(
    val index: Int = 0,
    val name: String = "",
    val status: String = "",
    val error: String? = null,
    val decodeMs: Long = 0L,
    val inferMs: Long = 0L,
    val encodeMs: Long = 0L,
    val waitMs: Long = 0L,
    val totalMs: Long = 0L,
    val inputWidth: Int = 0,
    val inputHeight: Int = 0,
    val outputWidth: Int = 0,
    val outputHeight: Int = 0,
    val downscaled: Boolean = false,
    val outputBytes: Long = 0L,
)

/** One batch / single-image run. Persisted as JSON under filesDir/perf. */
@Serializable
data class PerfRun(
    val id: String = "",
    val kind: String = "batch",
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val model: String = "",
    val scale: Int = 2,
    val noise: Int = 0,
    val style: Int = 0,
    val backend: String = "",
    val precision: String = "",
    val tileSize: Int = 128,
    val format: String = "",
    val quality: Int = 0,
    val maxOutputPixels: Long = 0L,
    val prepareMs: Long = 0L,
    val initMs: Long = 0L,
    val totalWallMs: Long = 0L,
    val actualBackend: String = "",
    val fallbackReason: String? = null,
    val device: String = "",
    val note: String? = null,
    val images: List<PerfImage> = emptyList(),
) {
    val doneImages: List<PerfImage>
        get() = images.filter { it.status == "DONE" && it.totalMs > 0 }

    val avgTotalMs: Long
        get() = doneImages.map { it.totalMs }.takeIf { it.isNotEmpty() }?.average()?.toLong() ?: 0L

    val medianTotalMs: Long
        get() {
            val values = doneImages.map { it.totalMs }.sorted()
            if (values.isEmpty()) return 0L
            return values[values.size / 2]
        }

    val p90TotalMs: Long
        get() {
            val values = doneImages.map { it.totalMs }.sorted()
            if (values.isEmpty()) return 0L
            return values[((values.size - 1) * 0.9).toInt()]
        }
}
