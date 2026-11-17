package io.lumasr.app.batch

import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.documentfile.provider.DocumentFile
import io.lumasr.app.data.ImageLoader
import io.lumasr.app.engine.Engine97
import io.lumasr.app.engine.EngineModels
import io.lumasr.app.folder.FolderScanner
import io.lumasr.app.folder.OutputIndex
import io.lumasr.app.folder.OutputRecord
import io.lumasr.app.model.SaveFormat
import io.lumasr.app.perf.PerfImage
import io.lumasr.app.perf.PerfLog
import io.lumasr.app.perf.PerfRun
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Above this output size we do not keep a second result bitmap alive for
 * overlap; encoding is awaited before the next inference starts.
 */
private const val MAX_OVERLAP_OUTPUT_PIXELS = 8_000_000L

class BatchWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    /** Result of a background encode, applied on the worker coroutine. */
    private data class EncodeOutcome(
        val index: Int,
        val item: QueueItem,
        val record: OutputRecord? = null,
        val encodeMs: Long = 0L,
        val outputBytes: Long = 0L,
    )

    /**
     * Compresses and writes one result. Runs on an IO thread while the NPU
     * starts the next image, so bitmap recycling stays inside this call.
     */
    private fun encodeItem(
        context: Context,
        writer: FolderScanner.OutputWriter,
        index: Int,
        item: QueueItem,
        output: Bitmap,
        format: SaveFormat,
        quality: Int,
        options: QueueOptions,
        outputScale: Int,
        started: Long,
    ): EncodeOutcome {
        val encodeStarted = SystemClock.elapsedRealtime()
        val outputUri = runCatching {
            writer.write(item.outputPath, output, format, quality)
        }.getOrNull()
        val encodeMs = SystemClock.elapsedRealtime() - encodeStarted
        val outputBytes = outputUri?.let { uri ->
            runCatching { DocumentFile.fromSingleUri(context, uri)?.length() ?: 0L }
                .getOrDefault(0L)
        } ?: 0L
        output.recycle()
        val elapsed = SystemClock.elapsedRealtime() - started
        return if (outputUri == null) {
            EncodeOutcome(
                index = index,
                item = item.copy(
                    status = QueueStatus.FAILED,
                    error = "写入输出文件夹失败",
                    elapsedMs = elapsed,
                ),
                encodeMs = encodeMs,
            )
        } else {
            EncodeOutcome(
                index = index,
                item = item.copy(
                    status = QueueStatus.DONE,
                    progress = 1f,
                    outputUri = outputUri.toString(),
                    elapsedMs = elapsed,
                ),
                record = OutputRecord(
                    sourcePath = item.relativePath,
                    outputPath = item.outputPath,
                    format = format.name,
                    modelId = options.modelId,
                    scale = outputScale,
                    updatedAt = System.currentTimeMillis(),
                ),
                encodeMs = encodeMs,
                outputBytes = outputBytes,
            )
        }
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val initial = BatchStore.load(context) ?: return Result.success()
        if (initial.items.isEmpty()) return Result.success()

        var options = initial.options
        val model = EngineModels.byIdOrDefault(options.modelId)
        val outputScale = EngineModels.outputScale(options.modelId, options.scale, options.style)
        val format = runCatching { SaveFormat.valueOf(options.format) }.getOrDefault(SaveFormat.PNG)
        val treeUri = initial.treeUri?.let { runCatching { Uri.parse(it) }.getOrNull() }

        var state = initial.copy(
            running = true,
            startedAt = initial.startedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
            finishedAt = 0,
            actualBackend = -1,
            fallbackReason = null,
        )
        BatchRepository.persist(context, state)

        setForeground(createForegroundInfo(0, state.items.size, "准备任务"))
        BatchNotifications.updateProgress(context, 0, state.items.size, "准备任务")

        val outputRecords = mutableMapOf<String, OutputRecord>()
        val runStartedAt = System.currentTimeMillis()
        val runStartElapsed = SystemClock.elapsedRealtime()
        val runId = PerfLog.newId()
        val perfImages = mutableMapOf<Int, PerfImage>()
        var prepareMs = 0L
        var initMs = 0L

        try {
            if (treeUri == null) {
                failPending(context, state, "源文件夹不可用")
                return Result.failure()
            }
            if (state.items.none { it.status == QueueStatus.PENDING }) {
                return finish(context, state)
            }
            val outputRoot = FolderScanner.ensureOutputRoot(
                context,
                treeUri,
                initial.outputFolderName,
            )
            if (outputRoot == null) {
                failPending(context, state, "无法创建输出文件夹，请重新授权文件夹")
                return Result.failure()
            }
            val writer = FolderScanner.OutputWriter(context, outputRoot)

            // NPU first-use compile can take a minute. Do it explicitly so a
            // failure can fall back to Vulkan instead of failing the whole batch.
            if (options.backend == Engine97.BACKEND_QUALCOMM_NPU) {
                val supported = EngineModels.npuSupports(
                    options.modelId, options.scale, options.noise, options.style,
                )
                if (!supported) {
                    options = options.copy(
                        backend = Engine97.BACKEND_VULKAN,
                        precision = Engine97.PRECISION_FP16,
                    )
                    state = state.copy(
                        options = options,
                        fallbackReason = "当前模型不支持 NPU，已回退 Vulkan",
                    )
                    BatchRepository.persist(context, state)
                } else {
                    setForeground(
                        createForegroundInfo(0, state.items.size, "编译 NPU 模型（首次约 1 分钟）"),
                    )
                    BatchNotifications.updateProgress(
                        context, 0, state.items.size, "编译 NPU 模型（首次约 1 分钟）",
                    )
                    val prepareStarted = SystemClock.elapsedRealtime()
                    val prepared = Engine97.prepareNpu(
                        context = context,
                        modelId = options.modelId,
                        scale = options.scale,
                        noise = options.noise,
                        style = options.style,
                        precision = options.precision,
                    )
                    prepareMs = SystemClock.elapsedRealtime() - prepareStarted
                    if (prepared == null) {
                        options = options.copy(
                            backend = Engine97.BACKEND_VULKAN,
                            precision = Engine97.PRECISION_FP16,
                        )
                        state = state.copy(
                            options = options,
                            fallbackReason = "NPU 模型编译失败：${
                                Engine97.npuFailureDetail().take(180)
                            }，已回退 Vulkan",
                        )
                        BatchRepository.persist(context, state)
                    }
                }
            }

            val initStarted = SystemClock.elapsedRealtime()
            var ready = Engine97.init(
                context = context,
                modelId = options.modelId,
                scale = options.scale,
                noise = options.noise,
                style = options.style,
                backend = options.backend,
                precision = options.precision,
                tileSize = options.tileSize,
            )
            if (!ready && options.backend == Engine97.BACKEND_QUALCOMM_NPU) {
                options = options.copy(
                    backend = Engine97.BACKEND_VULKAN,
                    precision = Engine97.PRECISION_FP16,
                )
                state = state.copy(
                    options = options,
                    fallbackReason = "NPU 初始化失败：${
                        Engine97.npuFailureDetail().take(180)
                    }，已回退 Vulkan",
                )
                BatchRepository.persist(context, state)
                ready = Engine97.init(
                    context = context,
                    modelId = options.modelId,
                    scale = options.scale,
                    noise = options.noise,
                    style = options.style,
                    backend = options.backend,
                    precision = options.precision,
                    tileSize = options.tileSize,
                )
            }
            initMs = SystemClock.elapsedRealtime() - initStarted
            if (!ready) {
                failPending(
                    context,
                    state,
                    if (options.backend == Engine97.BACKEND_QUALCOMM_NPU) {
                        "NPU 不可用或模型不支持"
                    } else {
                        "模型加载失败"
                    },
                )
                return Result.failure()
            }
            state = state.copy(
                actualBackend = Engine97.actualBackend(options.backend),
            )
            BatchRepository.persist(context, state)

            var lastPublish = 0L
            var completedCount = state.items.count { it.isFinished }

            coroutineScope {
                var pendingEncode: Deferred<EncodeOutcome>? = null
                var pendingIndex = -1

                // Applies a finished background encode. Encoding never mutates
                // worker state, so commits stay on this coroutine.
                suspend fun commitPending(blocking: Boolean) {
                    val job = pendingEncode ?: return
                    if (!blocking && !job.isCompleted) return
                    pendingEncode = null
                    val index = pendingIndex
                    pendingIndex = -1
                    val outcome = try {
                        job.await()
                    } catch (c: CancellationException) {
                        throw c
                    } catch (t: Throwable) {
                        // Never leave the item RUNNING: a failed write is a
                        // real failure and must be visible in the task list.
                        val failed = state.items.getOrNull(index)?.copy(
                            status = QueueStatus.FAILED,
                            error = "写入失败：${t.message ?: "未知错误"}",
                        )
                        if (failed != null) {
                            state = state.withItem(index, failed)
                            perfImages[index]?.let { perf ->
                                perfImages[index] = perf.copy(
                                    status = failed.status,
                                    error = failed.error,
                                )
                            }
                            BatchRepository.persist(context, state)
                        }
                        null
                    } ?: return
                    outcome.record?.let { outputRecords[it.sourcePath] = it }
                    state = state.withItem(outcome.index, outcome.item)
                    perfImages[outcome.index]?.let { perf ->
                        perfImages[outcome.index] = perf.copy(
                            status = outcome.item.status,
                            error = outcome.item.error,
                            encodeMs = outcome.encodeMs,
                            outputBytes = outcome.outputBytes,
                            totalMs = outcome.item.elapsedMs,
                        )
                    }
                    completedCount += 1
                    BatchRepository.persist(context, state)
                    BatchNotifications.updateProgress(
                        context,
                        completedCount,
                        state.items.size,
                        "已完成 $completedCount/${state.items.size}",
                    )
                }

                for (index in state.items.indices) {
                    if (isStopped) break

                    val queued = state.items[index]
                    if (queued.isFinished) continue

                    commitPending(blocking = false)

                    var item = queued.copy(
                        status = QueueStatus.RUNNING,
                        progress = 0f,
                        error = null,
                        elapsedMs = 0,
                    )
                    state = state.withItem(index, item)
                    BatchRepository.persist(context, state)

                    val started = SystemClock.elapsedRealtime()
                    try {
                        val decodeStarted = SystemClock.elapsedRealtime()
                        val loaded = ImageLoader.decodeUri(
                            context,
                            Uri.parse(item.documentUri),
                            outputScale,
                            options.maxOutputPixels,
                        )
                        val decodeMs = SystemClock.elapsedRealtime() - decodeStarted

                        // Large outputs would hold two full result bitmaps at
                        // once; finish the previous encode before inferring.
                        var waitMs = 0L
                        if (loaded.outputWidth.toLong() * loaded.outputHeight >
                            MAX_OVERLAP_OUTPUT_PIXELS
                        ) {
                            val waitStarted = SystemClock.elapsedRealtime()
                            commitPending(blocking = true)
                            waitMs = SystemClock.elapsedRealtime() - waitStarted
                        }

                        val inferStarted = SystemClock.elapsedRealtime()
                        val result = Engine97.process(
                            modelId = options.modelId,
                            input = loaded.bitmap,
                            pageIndex = index,
                        ) progress@{ percent ->
                            val now = SystemClock.elapsedRealtime()
                            if (percent >= 100 || now - lastPublish >= 350) {
                                val current = state.items[index]
                                item = current.copy(progress = percent / 100f)
                                state = state.withItem(index, item)
                                BatchRepository.publish(state)
                                lastPublish = now
                                BatchNotifications.updateProgress(
                                    context,
                                    completedCount,
                                    state.items.size,
                                    "${current.name} $percent%",
                                )
                            }
                        }
                        val inferMs = SystemClock.elapsedRealtime() - inferStarted
                        perfImages[index] = PerfImage(
                            index = index,
                            name = item.name,
                            status = QueueStatus.RUNNING,
                            decodeMs = decodeMs,
                            inferMs = inferMs,
                            waitMs = waitMs,
                            totalMs = SystemClock.elapsedRealtime() - started,
                            inputWidth = loaded.bitmap.width,
                            inputHeight = loaded.bitmap.height,
                            outputWidth = result?.width ?: loaded.outputWidth,
                            outputHeight = result?.height ?: loaded.outputHeight,
                            downscaled = loaded.downscaled,
                        )
                        loaded.bitmap.recycle()

                        if (result == null) {
                            item = if (isStopped) {
                                item.copy(
                                    status = QueueStatus.PENDING,
                                    progress = 0f,
                                    error = null,
                                )
                            } else {
                                item.copy(
                                    status = QueueStatus.FAILED,
                                    error = "推理失败或已取消",
                                )
                            }
                            perfImages[index] = perfImages.getValue(index).copy(
                                status = item.status,
                                error = item.error,
                                totalMs = SystemClock.elapsedRealtime() - started,
                            )
                            state = state.withItem(index, item)
                            BatchRepository.persist(context, state)
                            continue
                        }

                        // Only one encode may be in flight. Overwriting it
                        // would drop the previous item's DONE state and index
                        // record, so drain it first; it still overlapped with
                        // this image's decode + inference.
                        commitPending(blocking = true)
                        pendingIndex = index
                        pendingEncode = async(Dispatchers.IO) {
                            encodeItem(
                                context = context,
                                writer = writer,
                                index = index,
                                item = item,
                                output = result,
                                format = format,
                                quality = options.quality,
                                options = options,
                                outputScale = outputScale,
                                started = started,
                            )
                        }
                    } catch (c: CancellationException) {
                        // Pause/stop is not a per-item failure: let the outer
                        // handler turn RUNNING back into PENDING.
                        throw c
                    } catch (t: Throwable) {
                        item = item.copy(
                            status = QueueStatus.FAILED,
                            error = t.message ?: "处理失败",
                            elapsedMs = SystemClock.elapsedRealtime() - started,
                        )
                        perfImages[index] = (perfImages[index] ?: PerfImage(
                            index = index,
                            name = item.name,
                        )).copy(
                            status = item.status,
                            error = item.error,
                            totalMs = item.elapsedMs,
                        )
                        state = state.withItem(index, item)
                        BatchRepository.persist(context, state)
                    }
                }

                commitPending(blocking = true)
            }

            if (isStopped) {
                withContext(NonCancellable) {
                    saveIndex(context, treeUri, initial.outputFolderName, options, outputRecords)
                    state = state.copy(
                        running = false,
                        items = state.items.map {
                            if (it.status == QueueStatus.RUNNING) {
                                it.copy(status = QueueStatus.PENDING, progress = 0f)
                            } else {
                                it
                            }
                        },
                    )
                    BatchRepository.persist(context, state)
                }
                return Result.success()
            }

            saveIndex(context, treeUri, initial.outputFolderName, options, outputRecords)
            return finish(context, state)
        } catch (c: CancellationException) {
            withContext(NonCancellable) {
                saveIndex(context, treeUri, initial.outputFolderName, options, outputRecords)
                state = state.copy(
                    running = false,
                    items = state.items.map {
                        if (it.status == QueueStatus.RUNNING) {
                            it.copy(status = QueueStatus.PENDING, progress = 0f)
                        } else {
                            it
                        }
                    },
                )
                BatchRepository.persist(context, state)
            }
            throw c
        } finally {
            withContext(NonCancellable) {
                saveIndex(context, treeUri, initial.outputFolderName, options, outputRecords)
                Engine97.cancel()
                runCatching {
                    PerfLog.save(
                        context,
                        PerfRun(
                            id = runId,
                            kind = "batch",
                            startedAt = runStartedAt,
                            finishedAt = System.currentTimeMillis(),
                            model = model.name,
                            scale = options.scale,
                            noise = options.noise,
                            style = options.style,
                            backend = if (options.backend == Engine97.BACKEND_QUALCOMM_NPU) {
                                "NPU"
                            } else {
                                "Vulkan"
                            },
                            precision = if (options.precision == Engine97.PRECISION_INT8) {
                                "INT8"
                            } else {
                                "FP16"
                            },
                            tileSize = options.tileSize,
                            format = options.format,
                            quality = options.quality,
                            maxOutputPixels = options.maxOutputPixels,
                            prepareMs = prepareMs,
                            initMs = initMs,
                            totalWallMs = SystemClock.elapsedRealtime() - runStartElapsed,
                            actualBackend = if (
                                Engine97.actualBackend(options.backend) ==
                                Engine97.BACKEND_QUALCOMM_NPU
                            ) {
                                "NPU"
                            } else {
                                "Vulkan"
                            },
                            fallbackReason = state.fallbackReason,
                            device = PerfLog.deviceLabel(),
                            note = "编码与下一张推理重叠，≤8MP",
                            images = perfImages.values.sortedBy { it.index },
                        ),
                    )
                }
            }
            runCatching {
                BatchNotifications.notifyDone(
                    context,
                    state.doneCount,
                    state.failedCount,
                    options.notifications,
                )
            }
        }
    }

    private suspend fun saveIndex(
        context: Context,
        treeUri: Uri?,
        outputFolderName: String,
        options: QueueOptions,
        records: Map<String, OutputRecord>,
    ) {
        if (treeUri == null || records.isEmpty()) return
        OutputIndex.merge(
            context = context,
            treeUri = treeUri,
            outputFolderName = outputFolderName,
            preserveStructure = options.preserveStructure,
            records = records.values,
        )
    }

    private suspend fun finish(context: Context, state: QueueState): Result {
        val finished = state.copy(running = false, finishedAt = System.currentTimeMillis())
        BatchRepository.persist(context, finished)
        return Result.success()
    }

    private suspend fun failPending(context: Context, state: QueueState, message: String) {
        val failed = state.copy(
            running = false,
            finishedAt = System.currentTimeMillis(),
            items = state.items.map {
                if (it.status == QueueStatus.PENDING) {
                    it.copy(status = QueueStatus.FAILED, error = message)
                } else {
                    it
                }
            },
        )
        BatchRepository.persist(context, failed)
        BatchNotifications.notifyDone(
            context,
            failed.doneCount,
            failed.failedCount,
            state.options.notifications,
        )
    }

    private fun QueueState.withItem(index: Int, item: QueueItem): QueueState {
        val list = items.toMutableList()
        list[index] = item
        return copy(items = list)
    }

    private fun createForegroundInfo(done: Int, total: Int, text: String): ForegroundInfo {
        return ForegroundInfo(
            BatchNotifications.PROGRESS_ID,
            BatchNotifications.progress(applicationContext, done, total, text),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }
}
