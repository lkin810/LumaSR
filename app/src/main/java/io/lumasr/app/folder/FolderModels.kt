package io.lumasr.app.folder

import android.net.Uri
import kotlinx.serialization.Serializable

const val LARGE_FILE_BYTES = 4L * 1024 * 1024

/** One source -> output mapping, kept even when flat output names collide. */
@Serializable
data class OutputRecord(
    val sourcePath: String = "",
    val outputPath: String = "",
    val format: String = "",
    val modelId: Int = 0,
    val scale: Int = 2,
    val updatedAt: Long = 0L,
)

enum class GridFilter(val label: String) {
    ALL("全部"),
    UNPROCESSED("未处理"),
    PROCESSED("已处理"),
    LARGE("大图"),
}

data class FolderImage(
    val documentUri: Uri,
    val name: String,
    val relativePath: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val alreadyProcessed: Boolean = false,
) {
    val isLarge: Boolean get() = sizeBytes >= LARGE_FILE_BYTES
}

data class FolderSnapshot(
    val treeUri: Uri,
    val folderName: String,
    val images: List<FolderImage>,
    val recursive: Boolean,
    val outputFolderName: String,
    val outputFileCount: Int,
    val outputFiles: Set<String> = emptySet(),
    val outputRecords: Map<String, OutputRecord> = emptyMap(),
) {
    val processedCount: Int get() = images.count { it.alreadyProcessed }
    val totalBytes: Long get() = images.sumOf { it.sizeBytes }
    val largeCount: Int get() = images.count { it.isLarge }
}

/** Numeric-aware path comparison so P2 sorts before P10. */
fun naturalCompare(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            var si = i
            while (si < a.length && a[si].isDigit()) si++
            var sj = j
            while (sj < b.length && b[sj].isDigit()) sj++
            val na = a.substring(i, si).trimStart('0')
            val nb = b.substring(j, sj).trimStart('0')
            if (na.length != nb.length) return na.length - nb.length
            val cmp = na.compareTo(nb)
            if (cmp != 0) return cmp
            i = si
            j = sj
        } else {
            val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (cmp != 0) return cmp
            i++
            j++
        }
    }
    return (a.length - i) - (b.length - j)
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}
