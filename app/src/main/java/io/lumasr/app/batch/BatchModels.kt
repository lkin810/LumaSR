package io.lumasr.app.batch

import kotlinx.serialization.Serializable

object QueueStatus {
    const val PENDING = "PENDING"
    const val RUNNING = "RUNNING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
    const val SKIPPED = "SKIPPED"
}

@Serializable
data class QueueItem(
    val documentUri: String = "",
    val name: String = "",
    val relativePath: String = "",
    val outputPath: String = "",
    val status: String = QueueStatus.PENDING,
    val progress: Float = 0f,
    val error: String? = null,
    val outputUri: String? = null,
    val elapsedMs: Long = 0L,
    /** Copy the source file as-is instead of running the upscaler. */
    val copyOnly: Boolean = false,
) {
    val isFinished: Boolean
        get() = status == QueueStatus.DONE ||
            status == QueueStatus.FAILED ||
            status == QueueStatus.SKIPPED
}

@Serializable
data class QueueOptions(
    val modelId: Int,
    val scale: Int,
    val noise: Int,
    val style: Int,
    val backend: Int,
    val precision: Int,
    val maxOutputPixels: Long,
    val format: String,
    val quality: Int,
    val tileSize: Int = 128,
    val preserveStructure: Boolean = true,
    val notifications: Boolean = true,
    val skipLongEdge: Int = 0,
)

@Serializable
data class QueueState(
    val items: List<QueueItem> = emptyList(),
    val options: QueueOptions,
    val running: Boolean = false,
    val treeUri: String? = null,
    val folderName: String = "",
    val outputFolderName: String = "LumaSR_Output",
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val actualBackend: Int = -1,
    val fallbackReason: String? = null,
) {
    val doneCount: Int get() = items.count { it.status == QueueStatus.DONE }
    val failedCount: Int get() = items.count { it.status == QueueStatus.FAILED }
    val skippedCount: Int get() = items.count { it.status == QueueStatus.SKIPPED }
    val pendingCount: Int get() = items.count { it.status == QueueStatus.PENDING }

    val progress: Float
        get() {
            if (items.isEmpty()) return 0f
            val value = items.sumOf { item ->
                when (item.status) {
                    QueueStatus.DONE, QueueStatus.SKIPPED -> 1.0
                    QueueStatus.FAILED -> 1.0
                    QueueStatus.RUNNING -> item.progress.toDouble()
                    else -> 0.0
                }
            }
            return (value / items.size).toFloat().coerceIn(0f, 1f)
        }
}
