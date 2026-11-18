package io.lumasr.app.perf

import android.content.Context
import android.os.Build
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Persistent, low-overhead run log. Only a handful of numbers are kept per
 * image; writing happens once per run, never inside the inference loop.
 */
object PerfLog {
    private const val MAX_RUNS = 20
    private const val DIR_NAME = "perf"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val idFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    fun newId(): String = idFormat.format(LocalDateTime.now())

    fun deviceLabel(): String =
        "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /** Writes the run and keeps only the newest [MAX_RUNS] files. */
    @Synchronized
    fun save(context: Context, run: PerfRun) {
        if (run.id.isBlank()) return
        runCatching {
            val directory = dir(context)
            File(directory, "run-${run.id}.json")
                .writeText(json.encodeToString(run))
            directory.listFiles { file ->
                file.name.startsWith("run-") && file.extension == "json"
            }
                ?.sortedByDescending { it.name }
                ?.drop(MAX_RUNS)
                ?.forEach { it.delete() }
        }
    }

    fun recent(context: Context): List<PerfRun> {
        return dir(context)
            .listFiles { file -> file.name.startsWith("run-") && file.extension == "json" }
            ?.sortedByDescending { it.name }
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString<PerfRun>(file.readText()) }.getOrNull()
            }
            ?: emptyList()
    }

    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }

    fun formatTime(epochMs: Long): String {
        if (epochMs <= 0) return "-"
        return runCatching {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
        }.getOrDefault("-")
    }

    private fun seconds(ms: Long): String = when {
        ms <= 0 -> "0"
        ms < 1000 -> "${ms}ms"
        else -> String.format("%.2fs", ms / 1000.0)
    }

    fun summaryLine(run: PerfRun): String {
        val done = run.doneImages.size
        val failed = run.images.count { it.status == "FAILED" }
        return buildString {
            append(formatTime(run.startedAt))
            append(" · ")
            append(if (run.kind == "single") "单图" else "批量")
            append(" · ")
            append(run.model)
            append(" ${run.scale}x")
            append(" · ")
            append(run.backend)
            if (run.images.isNotEmpty()) append(" ${run.images.size} 张")
            if (done > 0) {
                append(" · 平均 ")
                append(seconds(run.avgTotalMs))
            }
            if (failed > 0) append(" · 失败 $failed")
        }
    }

    /** Plain text for the clipboard / sharing. */
    fun format(run: PerfRun): String = buildString {
        appendLine("LumaSR 运行日志")
        appendLine("时间: ${formatTime(run.startedAt)}  (${run.id})")
        appendLine("类型: ${if (run.kind == "single") "单图" else "批量"}")
        appendLine("设备: ${run.device}")
        appendLine(
            "模型: ${run.model} ${run.scale}x noise=${run.noise} style=${run.style}",
        )
        appendLine(
            "后端: ${run.backend} ${run.precision} · 实际 ${run.actualBackend.ifBlank { "-" }}",
        )
        appendLine("tile: ${run.tileSize} · 输出: ${run.format} q${run.quality}")
        appendLine("最大输出像素: ${run.maxOutputPixels / 1_000_000} MP")
        appendLine("准备: ${seconds(run.prepareMs)} · 初始化: ${seconds(run.initMs)}")
        appendLine("总耗时: ${seconds(run.totalWallMs)}")
        run.fallbackReason?.let { appendLine("回退原因: $it") }
        run.note?.let { appendLine("备注: $it") }

        val done = run.images.filter { it.status == "DONE" }
        val failed = run.images.count { it.status == "FAILED" }
        appendLine("图片: ${run.images.size} 张 · 完成 ${done.size} · 失败 $failed")

        if (done.isNotEmpty()) {
            val inferSum = done.sumOf { it.inferMs }
            val encodeSum = done.sumOf { it.encodeMs }
            val throughput = done.size * 1000.0 / run.totalWallMs.coerceAtLeast(1)
            appendLine(
                "吞吐: ${String.format(java.util.Locale.US, "%.2f", throughput)} 张/秒 " +
                    "（单项总计含与下一张的重叠，看吞吐更准）",
            )
            appendLine(
                "图像耗时: 平均 ${seconds(run.avgTotalMs)} · " +
                    "中位 ${seconds(run.medianTotalMs)} · P90 ${seconds(run.p90TotalMs)}",
            )
            appendLine(
                "阶段合计: 推理 ${seconds(inferSum)} · 编码 ${seconds(encodeSum)}",
            )
        }

        run.images.forEach { image ->
            appendLine(
                "#${image.index + 1} ${image.name} " +
                    "${image.inputWidth}x${image.inputHeight}->" +
                    "${image.outputWidth}x${image.outputHeight}" +
                    if (image.downscaled) " (已降采样)" else "",
            )
            val copyNote = if (image.copied) " · 直接复制" else ""
            val sizeNote = if (image.outputBytes > 0) " · ${image.outputBytes / 1024}KB" else ""
            appendLine(
                "   ${image.status} 解码 ${image.decodeMs}ms · " +
                    "推理 ${image.inferMs}ms · 编码 ${image.encodeMs}ms · " +
                    "等待 ${image.waitMs}ms · 总计 ${image.totalMs}ms" +
                    copyNote + sizeNote,
            )
            image.error?.let { appendLine("   错误: $it") }
        }
    }
}
