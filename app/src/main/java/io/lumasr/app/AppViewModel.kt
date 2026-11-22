package io.lumasr.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.lumasr.app.batch.BatchRepository
import io.lumasr.app.batch.BatchStore
import io.lumasr.app.batch.QueueItem
import io.lumasr.app.batch.QueueOptions
import io.lumasr.app.batch.QueueState
import io.lumasr.app.batch.QueueStatus
import io.lumasr.app.benchmark.BenchmarkBackendMode
import io.lumasr.app.benchmark.BenchmarkResult
import io.lumasr.app.benchmark.BenchmarkVariant
import io.lumasr.app.data.AppPrefs
import io.lumasr.app.data.ImageLoader
import io.lumasr.app.data.ImageSaver
import io.lumasr.app.engine.Engine97
import io.lumasr.app.engine.EngineModels
import io.lumasr.app.folder.FolderImage
import io.lumasr.app.folder.FolderScanner
import io.lumasr.app.folder.FolderSnapshot
import io.lumasr.app.folder.GridFilter
import io.lumasr.app.folder.PreprocessAction
import io.lumasr.app.folder.PreprocessAnalyzer
import io.lumasr.app.folder.PreprocessItem
import io.lumasr.app.folder.PreprocessReport
import io.lumasr.app.model.CompareInfo
import io.lumasr.app.model.Dest
import io.lumasr.app.model.SaveFormat
import io.lumasr.app.model.UpscaleConfig
import io.lumasr.app.perf.PerfImage
import io.lumasr.app.perf.PerfLog
import io.lumasr.app.perf.PerfRun
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class AppViewModel(app: Application) : AndroidViewModel(app) {
    var config by mutableStateOf(AppPrefs.load(app))
        private set

    var dest by mutableStateOf<Dest>(Dest.Home)
        private set

    // ---- single image flow -------------------------------------------------
    var pendingUris by mutableStateOf<List<Uri>>(emptyList())
        private set

    var pendingIsBatch by mutableStateOf(false)
        private set

    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    private val _stage = MutableStateFlow("")
    val stage = _stage.asStateFlow()

    var originalPreview by mutableStateOf<ImageBitmap?>(null)
        private set

    var resultPreview by mutableStateOf<ImageBitmap?>(null)
        private set

    var compareInfo by mutableStateOf<CompareInfo?>(null)
        private set

    var actualBackendLabel by mutableStateOf("")
        private set

    var npuDiagnosticRunning by mutableStateOf(false)
        private set

    var npuDiagnosticReport by mutableStateOf("")
        private set

    // ---- lab benchmark -----------------------------------------------------
    var benchmarkImageUri by mutableStateOf<Uri?>(null)
        private set

    var benchmarkImageName by mutableStateOf("")
        private set

    var benchmarkOutputTree by mutableStateOf<Uri?>(null)
        private set

    var benchmarkOutputName by mutableStateOf("")
        private set

    var benchmarkBackendMode by mutableStateOf(BenchmarkBackendMode.BOTH)
        private set

    var benchmarkSelectedModels by mutableStateOf(
        setOf(
            EngineModels.MODEL_CUGAN_SE,
            EngineModels.MODEL_ESRGAN,
            EngineModels.MODEL_W2XEX_PHOTO_SMALL,
        ),
    )
        private set

    var benchmarkScales by mutableStateOf<Map<Int, Int>>(emptyMap())
        private set

    var benchmarkRunning by mutableStateOf(false)
        private set

    var benchmarkCurrent by mutableStateOf("")
        private set

    var benchmarkProgress by mutableStateOf(0f)
        private set

    var benchmarkResults by mutableStateOf<List<BenchmarkResult>>(emptyList())
        private set

    var benchmarkTestFolderUri by mutableStateOf<String?>(null)
        private set

    private var benchmarkJob: Job? = null
    private var modelGuideFromBenchmark = false

    var savedUri by mutableStateOf<Uri?>(null)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var resultBitmap: Bitmap? = null
    private var originalName: String = "image"
    private var singleJob: Job? = null

    // ---- folder batch flow -------------------------------------------------
    var snapshot by mutableStateOf<FolderSnapshot?>(null)
        private set

    var scanning by mutableStateOf(false)
        private set

    var scanMessage by mutableStateOf("")
        private set

    var selectedPaths by mutableStateOf<Set<String>>(emptySet())
        private set

    var gridFilter by mutableStateOf(GridFilter.UNPROCESSED)
        private set

    var gridSortDescending by mutableStateOf(false)
        private set

    var lastFolderUri by mutableStateOf<Uri?>(null)
        private set

    var lastFolderName by mutableStateOf("")
        private set

    private var scanJob: Job? = null
    private var batchWasRunning = false

    // ---- preprocess analysis -----------------------------------------------
    var preprocessReport by mutableStateOf<PreprocessReport?>(null)
        private set

    var preprocessAnalyzing by mutableStateOf(false)
        private set

    var preprocessProgress by mutableStateOf(0f)
        private set

    var preprocessDone by mutableStateOf(0)
        private set

    private var preprocessJob: Job? = null

    // ---- result compare ----------------------------------------------------
    var compareFromBatch by mutableStateOf(false)
        private set

    var comparePosition by mutableStateOf(0)
        private set

    var compareTotal by mutableStateOf(0)
        private set

    var compareLoading by mutableStateOf(false)
        private set

    private var compareItems: List<QueueItem> = emptyList()
    private var compareBitmaps: List<Bitmap> = emptyList()

    val nativeReady: Boolean get() = Engine97.nativeLoaded
    val vulkanAvailable: Boolean get() = Engine97.nativeLoaded
    val npuAvailable: Boolean by lazy { Engine97.isQualcommNpuAvailable() }
    val npuArchitecture: Int? by lazy { Engine97.htpArchitecture(getApplication()) }
    val npuFp16: Boolean? by lazy { Engine97.detectFp16Support(getApplication()) }

    val deviceLabel: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.ifBlank { Build.HARDWARE }
        } else {
            Build.HARDWARE
        }

    val visibleImages: List<FolderImage>
        get() {
            val all = snapshot?.images.orEmpty()
            val filtered = when (gridFilter) {
                GridFilter.ALL -> all
                GridFilter.UNPROCESSED -> all.filter { !isProcessed(it) }
                GridFilter.PROCESSED -> all.filter { isProcessed(it) }
                GridFilter.LARGE -> all.filter { it.isLarge }
            }
            return if (gridSortDescending) filtered.reversed() else filtered
        }

    val selectedImages: List<FolderImage>
        get() = snapshot?.images.orEmpty().filter { it.relativePath in selectedPaths }

    val selectedBytes: Long
        get() = selectedImages.sumOf { it.sizeBytes }

    val allVisibleSelected: Boolean
        get() {
            val visible = visibleImages
            return visible.isNotEmpty() && visible.all { it.relativePath in selectedPaths }
        }

    init {
        val safeConfig = config.sanitized()
        if (safeConfig != config) {
            config = safeConfig
            AppPrefs.save(app, safeConfig)
        }
        viewModelScope.launch { BatchRepository.ensureLoaded(app) }
        viewModelScope.launch {
            BatchRepository.state.collect { queue ->
                val running = queue?.running == true
                if (batchWasRunning && !running) refreshProcessed()
                batchWasRunning = running
            }
        }
        AppPrefs.loadLastFolder(app)?.let { (uri, name) ->
            lastFolderUri = runCatching { Uri.parse(uri) }.getOrNull()
            lastFolderName = name
        }
    }

    fun updateConfig(block: (UpscaleConfig) -> UpscaleConfig) {
        config = block(config)
        AppPrefs.save(getApplication(), config)
    }

    fun countFor(filter: GridFilter): Int {
        val all = snapshot?.images.orEmpty()
        return when (filter) {
            GridFilter.ALL -> all.size
            GridFilter.UNPROCESSED -> all.count { !isProcessed(it) }
            GridFilter.PROCESSED -> all.count { isProcessed(it) }
            GridFilter.LARGE -> all.count { it.isLarge }
        }
    }

    /** Recomputes the "already exists" flag for the current output rules. */
    fun isProcessed(image: FolderImage): Boolean {
        val snap = snapshot ?: return image.alreadyProcessed
        return FolderScanner.isProcessed(
            image = image,
            outputFiles = snap.outputFiles,
            records = snap.outputRecords,
            preserveStructure = config.preserveStructure,
            format = config.format,
        )
    }

    /** Refreshes output files and processed flags without rescanning inputs. */
    fun refreshProcessed() {
        val snap = snapshot ?: return
        val app = getApplication<Application>()
        val cfg = config
        viewModelScope.launch {
            runCatching {
                val refreshed = withContext(Dispatchers.IO) {
                    FolderScanner.refreshOutputState(
                        context = app,
                        outputTreeUri = outputTree(cfg) ?: snap.treeUri,
                        outputFolderName = if (cfg.outputTreeUri.isNotBlank()) {
                            snap.folderName
                        } else {
                            cfg.outputFolderName
                        },
                        preserveStructure = cfg.preserveStructure,
                    )
                }
                val current = snapshot
                if (current != null && current.treeUri == snap.treeUri) {
                    snapshot = current.copy(
                        images = current.images.map {
                            it.copy(
                                alreadyProcessed = FolderScanner.isProcessed(
                                    it,
                                    refreshed.files,
                                    refreshed.records,
                                    cfg.preserveStructure,
                                    cfg.format,
                                ),
                            )
                        },
                        outputFiles = refreshed.files,
                        outputFileCount = refreshed.files.size,
                        outputRecords = refreshed.records,
                    )
                }
            }.onFailure {
                Log.w(TAG, "Refresh processed state failed: ${it.message}")
            }
        }
    }

    // ---- navigation --------------------------------------------------------

    fun openHome() {
        recycleResult()
        dest = Dest.Home
    }

    fun openTasks() {
        dest = Dest.Tasks
    }

    fun openSettings() {
        dest = Dest.Settings
    }

    fun openModels() {
        dest = Dest.Models
    }

    // ---- lab: benchmark & model guide -------------------------------------

    fun openBenchmark() {
        if (benchmarkOutputTree == null) {
            lastFolderUri?.let {
                benchmarkOutputTree = it
                benchmarkOutputName = lastFolderName
            }
        }
        dest = Dest.Benchmark
    }

    fun backFromBenchmark() {
        if (benchmarkRunning) {
            benchmarkJob?.cancel()
            Engine97.cancel()
        }
        dest = Dest.Settings
    }

    fun openModelGuide(fromBenchmark: Boolean = false) {
        modelGuideFromBenchmark = fromBenchmark
        dest = Dest.ModelGuide
    }

    fun backFromModelGuide() {
        dest = if (modelGuideFromBenchmark) Dest.Benchmark else Dest.Settings
    }

    fun openPerfLog() {
        dest = Dest.PerfLog
    }

    fun backFromPerfLog() {
        dest = Dest.Settings
    }

    fun onBenchmarkImagePicked(uri: Uri?) {
        if (uri == null) return
        benchmarkImageUri = uri
        benchmarkImageName = queryDisplayName(uri) ?: "image"
        benchmarkResults = emptyList()
        benchmarkTestFolderUri = null
        benchmarkProgress = 0f
    }

    fun onBenchmarkOutputPicked(uri: Uri?) {
        if (uri == null) return
        val app = getApplication<Application>()
        FolderScanner.takePermission(app, uri)
        benchmarkOutputTree = uri
        benchmarkOutputName = runCatching { DocumentFile.fromTreeUri(app, uri)?.name }.getOrNull()
            ?: "所选文件夹"
    }

    fun toggleBenchmarkModel(modelId: Int) {
        benchmarkSelectedModels = if (modelId in benchmarkSelectedModels) {
            benchmarkSelectedModels - modelId
        } else {
            benchmarkSelectedModels + modelId
        }
    }

    fun setBenchmarkScale(modelId: Int, scale: Int) {
        val model = EngineModels.byIdOrDefault(modelId)
        if (scale !in model.scales) return
        benchmarkScales = benchmarkScales + (modelId to scale)
    }

    fun selectBenchmarkBackendMode(mode: BenchmarkBackendMode) {
        benchmarkBackendMode = mode
    }

    fun benchmarkScaleFor(modelId: Int): Int {
        val model = EngineModels.byIdOrDefault(modelId)
        return benchmarkScales[modelId]?.takeIf { it in model.scales }
            ?: model.scales.firstOrNull()
            ?: 2
    }

    fun benchmarkPlannedCount(): Int = buildBenchmarkVariants().size

    fun useModelForBenchmark(modelId: Int) {
        benchmarkSelectedModels = setOf(modelId)
        openBenchmark()
    }

    fun startBenchmark() {
        if (benchmarkRunning) return
        val app = getApplication<Application>()
        val imageUri = benchmarkImageUri ?: run {
            errorMessage = "请先选择一张测试图片"
            return
        }
        val treeUri = benchmarkOutputTree ?: run {
            errorMessage = "请先选择测试输出文件夹"
            return
        }
        if (benchmarkSelectedModels.isEmpty()) {
            errorMessage = "至少选择一个模型"
            return
        }
        if (BatchRepository.snapshot?.running == true) {
            errorMessage = "批处理正在运行，请先暂停或等待完成"
            return
        }
        if (singleJob?.isActive == true) {
            errorMessage = "单图处理正在运行，请稍后再试"
            return
        }

        val cfg = config
        val variants = buildBenchmarkVariants()
        if (variants.isEmpty()) {
            errorMessage = "没有可运行的模型组合"
            return
        }

        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
        benchmarkRunning = true
        benchmarkResults = emptyList()
        benchmarkProgress = 0f
        benchmarkCurrent = "准备测试目录"
        benchmarkTestFolderUri = null

        benchmarkJob = viewModelScope.launch {
            val results = mutableListOf<BenchmarkResult>()
            var writer: FolderScanner.OutputWriter? = null
            try {
                val testRoot = withContext(Dispatchers.IO) {
                    FolderScanner.ensureOutputRoot(app, treeUri, FolderScanner.TEST_FOLDER)
                }
                if (testRoot == null) {
                    errorMessage = "无法创建 ${FolderScanner.TEST_FOLDER}，请重新授权文件夹"
                    return@launch
                }
                val testDir = withContext(Dispatchers.IO) {
                    FolderScanner.ensureDirectory(testRoot, stamp)
                }
                if (testDir == null) {
                    errorMessage = "无法创建测试目录"
                    return@launch
                }
                benchmarkTestFolderUri = testDir.uri.toString()
                writer = FolderScanner.OutputWriter(app, testRoot)

                variants.forEachIndexed { index, variant ->
                    ensureActive()
                    benchmarkCurrent = benchmarkVariantLabel(variant)
                    val result = runBenchmarkVariant(
                        app = app,
                        imageUri = imageUri,
                        variant = variant,
                        cfg = cfg,
                        writer = writer!!,
                        testDir = stamp,
                    ) { stage -> benchmarkCurrent = stage }
                    results += result
                    benchmarkResults = results.toList()
                    benchmarkProgress = (index + 1).toFloat() / variants.size
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e(TAG, "Benchmark failed", t)
                errorMessage = "测试失败：${t.message ?: "未知错误"}"
            } finally {
                withContext(NonCancellable) {
                    withContext(Dispatchers.IO) {
                        val out = writer
                        if (out != null && results.isNotEmpty()) {
                            runCatching { out.writeText("$stamp/results.csv", benchmarkCsv(results)) }
                            runCatching { out.writeText("$stamp/device.txt", benchmarkDeviceInfo()) }
                        }
                    }
                    benchmarkRunning = false
                    benchmarkCurrent = ""
                }
            }
        }
    }

    fun cancelBenchmark() {
        if (!benchmarkRunning) return
        benchmarkJob?.cancel()
        Engine97.cancel()
        benchmarkCurrent = "正在取消…"
    }

    fun clearBenchmarkResults() {
        if (benchmarkRunning) return
        benchmarkResults = emptyList()
        benchmarkTestFolderUri = null
        benchmarkProgress = 0f
        benchmarkCurrent = ""
    }

    fun openBenchmarkFolder() {
        val app = getApplication<Application>()
        val uri = benchmarkTestFolderUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (uri != null && FolderScanner.openFolder(app, uri)) return
        errorMessage = "测试目录：${benchmarkOutputName}/${FolderScanner.TEST_FOLDER}"
    }

    private fun buildBenchmarkVariants(): List<BenchmarkVariant> {
        val out = mutableListOf<BenchmarkVariant>()
        EngineModels.all.forEach { model ->
            if (model.id !in benchmarkSelectedModels) return@forEach
            val scale = benchmarkScaleFor(model.id)
            val noise = model.noises.lastOrNull() ?: 0
            val style = EngineModels.STYLE_ANIME
            fun variant(backend: Int) = BenchmarkVariant(
                modelId = model.id,
                scale = scale,
                noise = noise,
                style = style,
                backend = backend,
            )
            when (benchmarkBackendMode) {
                BenchmarkBackendMode.VULKAN -> out += variant(Engine97.BACKEND_VULKAN)
                BenchmarkBackendMode.NPU -> out += variant(Engine97.BACKEND_QUALCOMM_NPU)
                BenchmarkBackendMode.BOTH -> {
                    out += variant(Engine97.BACKEND_VULKAN)
                    if (EngineModels.npuSupports(model.id, scale, noise, style)) {
                        out += variant(Engine97.BACKEND_QUALCOMM_NPU)
                    }
                }
            }
        }
        return out
    }

    private fun benchmarkVariantLabel(variant: BenchmarkVariant): String {
        val model = EngineModels.byIdOrDefault(variant.modelId)
        val backend = if (variant.backend == Engine97.BACKEND_QUALCOMM_NPU) "NPU" else "Vulkan"
        return "${model.name} · ${variant.scale}x · $backend"
    }

    private suspend fun runBenchmarkVariant(
        app: Application,
        imageUri: Uri,
        variant: BenchmarkVariant,
        cfg: UpscaleConfig,
        writer: FolderScanner.OutputWriter,
        testDir: String,
        onStage: (String) -> Unit,
    ): BenchmarkResult {
        val model = EngineModels.byIdOrDefault(variant.modelId)
        val outputScale = EngineModels.outputScale(variant.modelId, variant.scale, variant.style)
        val precision = if (variant.backend == Engine97.BACKEND_QUALCOMM_NPU) {
            Engine97.PRECISION_FP16
        } else {
            cfg.precision
        }

        var prepareMs = 0L
        var initMs = 0L
        var decodeMs = 0L
        var inferMs = 0L
        var encodeMs = 0L
        var inputWidth = 0
        var inputHeight = 0
        var outputWidth = 0
        var outputHeight = 0
        var outputBytes = 0L
        var downscaled = false
        var actualBackend = -1
        var outputUri: Uri? = null
        val started = SystemClock.elapsedRealtime()

        fun snapshot(error: String? = null) = BenchmarkResult(
            modelId = variant.modelId,
            modelName = model.name,
            scale = variant.scale,
            noise = variant.noise,
            style = variant.style,
            backend = variant.backend,
            precision = precision,
            prepareMs = prepareMs,
            initMs = initMs,
            decodeMs = decodeMs,
            inferMs = inferMs,
            encodeMs = encodeMs,
            totalMs = SystemClock.elapsedRealtime() - started,
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            outputWidth = outputWidth,
            outputHeight = outputHeight,
            outputBytes = outputBytes,
            downscaled = downscaled,
            actualBackend = actualBackend,
            outputUri = outputUri?.toString(),
            error = error,
        )

        if (variant.backend == Engine97.BACKEND_QUALCOMM_NPU &&
            !EngineModels.npuSupports(variant.modelId, variant.scale, variant.noise, variant.style)
        ) {
            return snapshot("NPU 不支持该模型 / 倍数")
        }

        try {
            if (variant.backend == Engine97.BACKEND_QUALCOMM_NPU) {
                onStage("${benchmarkVariantLabel(variant)} · 编译 NPU 模型")
                val t = SystemClock.elapsedRealtime()
                val prepared = withContext(Dispatchers.IO) {
                    Engine97.prepareNpu(
                        context = app,
                        modelId = variant.modelId,
                        scale = variant.scale,
                        noise = variant.noise,
                        style = variant.style,
                        precision = precision,
                    )
                }
                prepareMs = SystemClock.elapsedRealtime() - t
                if (prepared == null) {
                    return snapshot(Engine97.npuFailureDetail())
                }
            }

            onStage("${benchmarkVariantLabel(variant)} · 加载模型")
            val initStarted = SystemClock.elapsedRealtime()
            val ready = withContext(Dispatchers.IO) {
                Engine97.init(
                    context = app,
                    modelId = variant.modelId,
                    scale = variant.scale,
                    noise = variant.noise,
                    style = variant.style,
                    backend = variant.backend,
                    precision = precision,
                    tileSize = cfg.tileSize,
                )
            }
            initMs = SystemClock.elapsedRealtime() - initStarted
            if (!ready) {
                return snapshot(
                    if (variant.backend == Engine97.BACKEND_QUALCOMM_NPU) {
                        Engine97.npuFailureDetail()
                    } else {
                        "模型加载失败"
                    },
                )
            }
            actualBackend = Engine97.actualBackend(variant.backend)

            onStage("${benchmarkVariantLabel(variant)} · 解码图片")
            val decodeStarted = SystemClock.elapsedRealtime()
            val loaded = withContext(Dispatchers.IO) {
                ImageLoader.decodeUri(app, imageUri, outputScale, cfg.maxOutputPixels)
            }
            decodeMs = SystemClock.elapsedRealtime() - decodeStarted
            inputWidth = loaded.bitmap.width
            inputHeight = loaded.bitmap.height
            downscaled = loaded.downscaled

            var output: Bitmap? = null
            try {
                onStage("${benchmarkVariantLabel(variant)} · 推理")
                val inferStarted = SystemClock.elapsedRealtime()
                output = Engine97.process(variant.modelId, loaded.bitmap, 0)
                inferMs = SystemClock.elapsedRealtime() - inferStarted
            } finally {
                loaded.bitmap.recycle()
            }
            if (output == null) {
                return snapshot("推理失败或已取消")
            }

            try {
                outputWidth = output.width
                outputHeight = output.height
                onStage("${benchmarkVariantLabel(variant)} · 写 PNG")
                val encodeStarted = SystemClock.elapsedRealtime()
                outputUri = withContext(Dispatchers.IO) {
                    writer.write(
                        "$testDir/${benchmarkFileName(variant, model, precision)}",
                        output,
                        SaveFormat.PNG,
                        100,
                    )
                }
                encodeMs = SystemClock.elapsedRealtime() - encodeStarted
                if (outputUri == null) {
                    return snapshot("写入测试文件夹失败")
                }
                outputBytes = withContext(Dispatchers.IO) {
                    DocumentFile.fromSingleUri(app, outputUri!!)?.length() ?: 0L
                }
                return snapshot()
            } finally {
                output.recycle()
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Log.e(TAG, "Benchmark variant failed", t)
            return snapshot(t.message ?: t.javaClass.simpleName)
        }
    }

    private fun benchmarkFileName(
        variant: BenchmarkVariant,
        model: io.lumasr.app.engine.EngineModel,
        precision: Int,
    ): String = buildString {
        append(EngineModels.filePrefix(variant.modelId))
        append("_${variant.scale}x")
        if (model.noises.isNotEmpty()) append("_n${variant.noise}")
        if (model.hasStyle) append("_${if (variant.style == EngineModels.STYLE_PHOTO) "photo" else "anime"}")
        append(if (variant.backend == Engine97.BACKEND_QUALCOMM_NPU) "_npu" else "_vulkan")
        if (variant.backend == Engine97.BACKEND_QUALCOMM_NPU) {
            append(if (precision == Engine97.PRECISION_INT8) "_int8" else "_fp16")
        }
        append(".png")
    }

    private fun benchmarkCsv(results: List<BenchmarkResult>): String {
        val header = listOf(
            "model", "scale", "noise", "style", "backend", "precision",
            "prepare_ms", "init_ms", "decode_ms", "infer_ms", "encode_ms", "total_ms",
            "input_w", "input_h", "output_w", "output_h", "output_mp", "output_bytes",
            "downscaled", "actual_backend", "status", "error",
        ).joinToString(",")
        val rows = results.map { r ->
            listOf(
                r.modelName,
                r.scale.toString(),
                r.noise.toString(),
                r.style.toString(),
                r.backendLabel,
                if (r.backend == Engine97.BACKEND_QUALCOMM_NPU) {
                    if (r.precision == Engine97.PRECISION_INT8) "INT8" else "FP16"
                } else {
                    "FP16"
                },
                r.prepareMs.toString(),
                r.initMs.toString(),
                r.decodeMs.toString(),
                r.inferMs.toString(),
                r.encodeMs.toString(),
                r.totalMs.toString(),
                r.inputWidth.toString(),
                r.inputHeight.toString(),
                r.outputWidth.toString(),
                r.outputHeight.toString(),
                String.format(Locale.US, "%.2f", r.outputMp),
                r.outputBytes.toString(),
                r.downscaled.toString(),
                r.actualBackendLabel,
                if (r.error == null) "OK" else "FAILED",
                r.error ?: "",
            ).joinToString(",") { csvCell(it) }
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private fun csvCell(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }

    private fun benchmarkDeviceInfo(): String {
        val app = getApplication<Application>()
        val version = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName
        }.getOrNull() ?: "unknown"
        return buildString {
            appendLine("LumaSR benchmark")
            appendLine("version=$version")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("soc=$deviceLabel")
            appendLine("android=${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("htp=${Engine97.htpArchitecture(app)?.let { "v$it" } ?: "unknown"}")
            appendLine("qnn=${Engine97.qnnRuntimeAvailable()}")
            appendLine("fp16=${Engine97.detectFp16Support(app)}")
            appendLine("page_size=${Engine97.pageSizeBytes()}")
            appendLine("backend_mode=${benchmarkBackendMode.label}")
            appendLine("max_output_mp=${config.maxOutputMp}")
            appendLine("tile=${config.tileSize}")
            appendLine("source=${benchmarkImageName}")
            appendLine("created=${LocalDateTime.now()}")
        }
    }

    /** End-to-end NPU check: prepare -> init -> 64x64 inference. */
    fun runNpuDiagnostics() {
        if (npuDiagnosticRunning) return
        val app = getApplication<Application>()
        if (BatchRepository.snapshot?.running == true) {
            npuDiagnosticReport = "任务正在运行，请先暂停再运行 NPU 自检。"
            return
        }
        val cfg = config
        val model = EngineModels.byIdOrDefault(cfg.modelId)
        val supported = EngineModels.npuSupports(cfg.modelId, cfg.scale, cfg.noise, cfg.style)
        val npuName = Engine97.qnnModelName(cfg.modelId, cfg.scale, cfg.noise, cfg.style)
        npuDiagnosticRunning = true
        npuDiagnosticReport = "诊断中…"

        viewModelScope.launch {
            val lines = mutableListOf<String>()
            try {
                val version = runCatching {
                    app.packageManager.getPackageInfo(app.packageName, 0).versionName
                }.getOrNull() ?: "unknown"
                val htp = withContext(Dispatchers.IO) { Engine97.htpArchitecture(app) }
                val qnn = withContext(Dispatchers.IO) { Engine97.qnnRuntimeAvailable() }
                val npu = withContext(Dispatchers.IO) { Engine97.isQualcommNpuAvailable() }
                val fp16 = withContext(Dispatchers.IO) { Engine97.detectFp16Support(app) }
                val page = Engine97.pageSizeBytes()

                lines += "LumaSR $version"
                lines += "设备: ${Build.MANUFACTURER} ${Build.MODEL}"
                lines += "SoC: $deviceLabel"
                lines += "Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
                lines += "ABI: ${Build.SUPPORTED_ABIS.joinToString()}"
                lines += "HTP: ${htp?.let { "v$it" } ?: "未知"}"
                val nativeDir = app.applicationInfo.nativeLibraryDir
                val skelName = htp?.let { "libQnnHtpV${it}Skel.so" }
                if (skelName != null) {
                    val skel = File(nativeDir, skelName)
                    lines += "Skel: ${
                        if (skel.isFile) "${skel.name} (${skel.length()} B)" else "${skel.name} 缺失"
                    }"
                }
                lines += "FastRPC(libcdsprpc): ${
                    runCatching { System.loadLibrary("cdsprpc"); "OK" }
                        .getOrElse { "FAILED: ${it.message}" }
                }"
                lines += "nativeLibraryDir: $nativeDir"
                lines += "QNN runtime: $qnn"
                lines += "NPU 可用: $npu"
                lines += "FP16: ${fp16 ?: "未知"}"
                lines += "页大小: ${page?.let { "$it B" } ?: "未知"}"
                lines += "模型: ${model.name} ${cfg.outputScale}x / noise=${cfg.noise} / style=${cfg.style}"
                lines += "QNN 模型: ${npuName ?: "无"}"
                lines += "NPU 支持: $supported"
                lines += "精度: ${if (cfg.precision == Engine97.PRECISION_INT8) "INT8" else "FP16"}"

                if (!supported || npuName == null) {
                    lines += "结果: 当前模型/倍数不支持 NPU，请改用 Vulkan 或换 CUGAN SE 2x"
                } else {
                    val prepared = withContext(Dispatchers.IO) {
                        Engine97.prepareNpu(
                            context = app,
                            modelId = cfg.modelId,
                            scale = cfg.scale,
                            noise = cfg.noise,
                            style = cfg.style,
                            precision = cfg.precision,
                            retry = true,
                        )
                    }
                    lines += "Prepare: ${if (prepared != null) "OK" else "FAILED"}"
                    if (prepared == null) {
                        lines += "错误: ${Engine97.npuFailureDetail()}"
                    } else {
                        val ready = withContext(Dispatchers.IO) {
                            Engine97.init(
                                context = app,
                                modelId = cfg.modelId,
                                scale = cfg.scale,
                                noise = cfg.noise,
                                style = cfg.style,
                                backend = Engine97.BACKEND_QUALCOMM_NPU,
                                precision = cfg.precision,
                                tileSize = cfg.tileSize,
                            )
                        }
                        lines += "Init: $ready"
                        lines += "实际 NPU: ${
                            Engine97.actualBackend(Engine97.BACKEND_QUALCOMM_NPU) ==
                                Engine97.BACKEND_QUALCOMM_NPU
                        }"
                        if (!ready) {
                            lines += "错误: ${Engine97.npuFailureDetail()}"
                        } else {
                            val bitmap = withContext(Dispatchers.Default) {
                                Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
                                    eraseColor(0xFF3355AA.toInt())
                                }
                            }
                            val result = withContext(Dispatchers.Default) {
                                Engine97.process(cfg.modelId, bitmap, 0)
                            }
                            bitmap.recycle()
                            result?.recycle()
                            lines += "64x64 推理: ${if (result != null) "OK" else "FAILED"}"
                            if (result == null) {
                                lines += "错误: ${Engine97.npuFailureDetail()}"
                            }
                        }
                    }
                }
                lines += "最近错误: ${Engine97.lastError.value ?: "无"}"
                lines += "QNN 状态: ${
                    Engine97.npuStates.value.entries.joinToString { "${it.key}=${it.value}" }
                }"
            } catch (t: Throwable) {
                lines += "诊断异常: ${t.javaClass.simpleName}: ${t.message}"
            } finally {
                npuDiagnosticReport = lines.joinToString("\n")
                npuDiagnosticRunning = false
            }
        }
    }

    fun backFromModels() {
        dest = Dest.Settings
    }

    fun openLastFolder() {
        val uri = lastFolderUri ?: return
        openFolder(uri)
    }

    fun openFolder(treeUri: Uri) {
        FolderScanner.takePermission(getApplication(), treeUri)
        dest = Dest.FolderOverview(treeUri)
        rescan(treeUri, config.recursiveScan)
    }

    fun onFolderPicked(uri: Uri?) {
        if (uri != null) openFolder(uri)
    }

    fun rescan(
        treeUri: Uri,
        recursive: Boolean = config.recursiveScan,
        keepSelection: Boolean = false,
    ) {
        val app = getApplication<Application>()
        val previous = selectedPaths
        scanJob?.cancel()
        preprocessReport = null
        snapshot = null
        scanning = true
        scanMessage = "正在扫描文件夹…"
        gridFilter = GridFilter.UNPROCESSED

        scanJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    FolderScanner.scan(
                        context = app,
                        treeUri = treeUri,
                        recursive = recursive,
                        preserveStructure = config.preserveStructure,
                        format = config.format,
                        outputFolderName = config.outputFolderName,
                        outputTreeUri = outputTree(config),
                    )
                }
                snapshot = result
                val paths = result.images.map { it.relativePath }.toSet()
                selectedPaths = if (keepSelection) {
                    previous.intersect(paths)
                } else {
                    result.images
                        .filter { !it.alreadyProcessed }
                        .map { it.relativePath }
                        .toSet()
                }
                scanMessage = ""
                lastFolderUri = treeUri
                lastFolderName = result.folderName
                AppPrefs.saveLastFolder(app, treeUri.toString(), result.folderName)
            } catch (t: Throwable) {
                Log.e(TAG, "Folder scan failed", t)
                scanMessage = ""
                errorMessage = "扫描失败：${t.message ?: "无法读取文件夹"}"
            } finally {
                scanning = false
            }
        }
    }

    fun setRecursive(recursive: Boolean) {
        updateConfig { it.copy(recursiveScan = recursive) }
        snapshot?.treeUri?.let { rescan(it, recursive) }
    }

    fun setPreserveStructure(value: Boolean) {
        updateConfig { it.copy(preserveStructure = value) }
        snapshot?.treeUri?.let { rescan(it, config.recursiveScan, keepSelection = true) }
    }

    fun setSkipProcessed(value: Boolean) {
        updateConfig { it.copy(skipProcessed = value) }
    }

    fun setSkipLongEdge(value: Int) {
        updateConfig { it.copy(skipLongEdge = value.coerceAtLeast(0)) }
        preprocessReport = null
    }

    /** True when this image will be copied as-is under the current threshold. */
    fun willCopy(image: FolderImage): Boolean =
        config.skipLongEdge > 0 && image.longEdge >= config.skipLongEdge

    fun backFromFolder() {
        scanJob?.cancel()
        scanning = false
        preprocessReport = null
        snapshot = null
        dest = Dest.Home
    }

    fun enterGrid() {
        val uri = snapshot?.treeUri ?: return
        dest = Dest.Grid(uri)
    }

    fun backToOverview() {
        val uri = snapshot?.treeUri ?: return
        dest = Dest.FolderOverview(uri)
    }

    fun enterPreprocess() {
        if (selectedPaths.isEmpty()) {
            errorMessage = "请先选择要处理的图片"
            return
        }
        val uri = snapshot?.treeUri ?: return
        dest = Dest.Preprocess(uri)
    }

    /** Measures resolution buckets and an advisory sharpness score. */
    fun analyzeSelection() {
        if (preprocessAnalyzing) return
        val chosen = selectedImages
        if (chosen.isEmpty()) {
            errorMessage = "请先选择要处理的图片"
            return
        }
        val app = getApplication<Application>()
        val threshold = config.skipLongEdge
        preprocessJob?.cancel()
        preprocessReport = null
        preprocessProgress = 0f
        preprocessDone = 0
        preprocessAnalyzing = true

        preprocessJob = viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    chosen.mapIndexed { index, image ->
                        ensureActive()
                        val action = PreprocessAnalyzer.actionFor(
                            image = image,
                            skipLongEdge = threshold,
                            alreadyProcessed = config.skipProcessed && isProcessed(image),
                        )
                        // Only upscaled images need a sharpness sample.
                        val sharpness = if (action != PreprocessAction.UPSCALE) {
                            -1f
                        } else {
                            PreprocessAnalyzer.sharpnessOf(app, image.documentUri)
                        }
                        preprocessDone = index + 1
                        preprocessProgress = (index + 1).toFloat() / chosen.size
                        PreprocessItem(image = image, action = action, sharpness = sharpness)
                    }
                }
                preprocessReport = PreprocessReport(items = items, skipLongEdge = threshold)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                errorMessage = "分析失败：${t.message ?: t.javaClass.simpleName}"
            } finally {
                preprocessAnalyzing = false
            }
        }
    }

    fun backToGrid() {
        preprocessJob?.cancel()
        preprocessAnalyzing = false
        val uri = snapshot?.treeUri ?: return
        dest = Dest.Grid(uri)
    }

    fun enterBatchConfig() {
        val uri = snapshot?.treeUri ?: return
        dest = Dest.BatchConfig(uri)
    }

    // ---- grid selection ----------------------------------------------------

    fun selectGridFilter(filter: GridFilter) {
        gridFilter = filter
    }

    fun toggleSort() {
        gridSortDescending = !gridSortDescending
    }

    fun toggleImage(image: FolderImage) {
        preprocessReport = null
        selectedPaths = if (image.relativePath in selectedPaths) {
            selectedPaths - image.relativePath
        } else {
            selectedPaths + image.relativePath
        }
    }

    fun toggleSelectAllVisible() {
        preprocessReport = null
        val visible = visibleImages.map { it.relativePath }.toSet()
        selectedPaths = if (allVisibleSelected) {
            selectedPaths - visible
        } else {
            selectedPaths + visible
        }
    }

    fun invertVisible() {
        preprocessReport = null
        val visible = visibleImages.map { it.relativePath }.toSet()
        selectedPaths = (selectedPaths - visible) + (visible - selectedPaths)
    }

    // ---- batch start -------------------------------------------------------

    fun startBatch() {
        val snap = snapshot ?: return
        val app = getApplication<Application>()
        if (benchmarkRunning) {
            errorMessage = "模型对比测试运行中，请先等待或取消"
            return
        }
        if (BatchRepository.snapshot?.running == true) {
            errorMessage = "已有任务正在运行，请先暂停或等待完成"
            return
        }
        val chosen = snap.images.filter { it.relativePath in selectedPaths }
        if (chosen.isEmpty()) {
            errorMessage = "请先选择要处理的图片"
            return
        }

        val used = if (config.skipProcessed) {
            // Reserve existing outputs so a new same-named source cannot
            // overwrite a file that is still considered processed.
            snap.outputFiles.toMutableSet()
        } else {
            mutableSetOf()
        }
        val items = chosen.map { image ->
            val skipped = config.skipProcessed && isProcessed(image)
            val copyOnly = !skipped &&
                config.skipLongEdge > 0 &&
                image.longEdge >= config.skipLongEdge
            val outputPath = if (copyOnly) {
                FolderScanner.uniqueCopyOutputPath(
                    image,
                    config.preserveStructure,
                    used,
                )
            } else {
                FolderScanner.uniqueOutputPath(
                    image,
                    config.preserveStructure,
                    config.format,
                    used,
                )
            }
            QueueItem(
                documentUri = image.documentUri.toString(),
                name = image.name,
                relativePath = image.relativePath,
                outputPath = outputPath,
                status = if (skipped) QueueStatus.SKIPPED else QueueStatus.PENDING,
                copyOnly = copyOnly,
            )
        }

        val options = QueueOptions(
            modelId = config.modelId,
            scale = config.scale,
            noise = config.noise,
            style = config.style,
            backend = config.backend,
            precision = config.precision,
            maxOutputPixels = config.maxOutputPixels,
            format = config.format.name,
            quality = config.quality,
            tileSize = config.tileSize,
            preserveStructure = config.preserveStructure,
            notifications = config.notifications,
            skipLongEdge = config.skipLongEdge,
        )
        val state = QueueState(
            items = items,
            options = options,
            running = true,
            treeUri = snap.treeUri.toString(),
            folderName = snap.folderName,
            outputFolderName = if (config.outputTreeUri.isNotBlank()) {
                snap.folderName
            } else {
                config.outputFolderName
            },
            outputTreeUri = config.outputTreeUri.takeIf { it.isNotBlank() },
            startedAt = System.currentTimeMillis(),
        )

        viewModelScope.launch {
            BatchStore.save(app, state)
            BatchRepository.publish(state)
            BatchRepository.enqueue(app)
            dest = Dest.Tasks
        }
    }

    fun retryFailed() {
        viewModelScope.launch { BatchRepository.retryFailed(getApplication()) }
    }

    fun cancelBatch() {
        viewModelScope.launch { BatchRepository.cancel(getApplication()) }
    }

    fun resumeBatch() {
        viewModelScope.launch { BatchRepository.resume(getApplication()) }
    }

    fun clearBatch() {
        viewModelScope.launch { BatchRepository.clearFinished(getApplication()) }
    }

    fun openOutputFolder() {
        val context = getApplication<Application>()
        val state = BatchRepository.snapshot
        val custom = state?.outputTreeUri ?: config.outputTreeUri.takeIf { it.isNotBlank() }
        val tree = custom ?: state?.treeUri ?: snapshot?.treeUri?.toString()
        val uri = tree?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (uri != null && FolderScanner.openFolder(context, uri)) return
        val source = state?.folderName ?: snapshot?.folderName.orEmpty()
        if (custom != null) {
            val name = config.outputTreeName.takeIf { it.isNotBlank() } ?: "已指定导出文件夹"
            errorMessage = "输出位置：$name/${source.ifBlank { "源文件夹名" }}"
        } else {
            val folder = state?.outputFolderName ?: config.outputFolderName
            errorMessage = "输出位置：${source.ifBlank { "源文件夹" }}/$folder"
        }
    }

    /** Picks a fixed export root; each source gets a sub-folder named after it. */
    fun setOutputTree(uri: Uri) {
        val app = getApplication<Application>()
        if (!FolderScanner.takePermission(app, uri)) {
            errorMessage = "无法获得该文件夹的写入权限"
            return
        }
        val source = snapshot?.treeUri ?: lastFolderUri
        if (source != null && isInside(uri, source)) {
            errorMessage = "导出文件夹不能是源文件夹本身或其子目录"
            return
        }
        val name = runCatching { DocumentFile.fromTreeUri(app, uri)?.name }
            .getOrNull()
            .orEmpty()
        updateConfig { it.copy(outputTreeUri = uri.toString(), outputTreeName = name) }
        preprocessReport = null
        snapshot?.let { rescan(it.treeUri, keepSelection = true) }
    }

    fun clearOutputTree() {
        updateConfig { it.copy(outputTreeUri = "", outputTreeName = "") }
        preprocessReport = null
        snapshot?.let { rescan(it.treeUri, keepSelection = true) }
    }

    private fun outputTree(cfg: UpscaleConfig): Uri? = cfg.outputTreeUri
        .takeIf { it.isNotBlank() }
        ?.let { runCatching { Uri.parse(it) }.getOrNull() }

    private fun isInside(candidate: Uri, source: Uri): Boolean {
        if (candidate == source) return true
        if (candidate.authority != source.authority) return false
        val candidateId = runCatching {
            android.provider.DocumentsContract.getTreeDocumentId(candidate)
        }.getOrNull() ?: return false
        val sourceId = runCatching {
            android.provider.DocumentsContract.getTreeDocumentId(source)
        }.getOrNull() ?: return false
        return candidateId == sourceId || candidateId.startsWith("$sourceId/")
    }

    // ---- result compare ----------------------------------------------------

    fun openBatchCompare(item: QueueItem) {
        val state = BatchRepository.snapshot ?: return
        val done = state.items.filter { it.status == QueueStatus.DONE && it.outputUri != null }
        val index = done.indexOfFirst { it.documentUri == item.documentUri }
        if (index < 0) return
        compareItems = done
        compareTotal = done.size
        compareFromBatch = true
        dest = Dest.Compare
        loadBatchCompare(index)
    }

    fun comparePrev() {
        if (compareFromBatch && comparePosition > 1) {
            loadBatchCompare(comparePosition - 2)
        }
    }

    fun compareNext() {
        if (compareFromBatch && comparePosition < compareTotal) {
            loadBatchCompare(comparePosition)
        }
    }

    private fun loadBatchCompare(index: Int) {
        val item = compareItems.getOrNull(index) ?: return
        val app = getApplication<Application>()
        comparePosition = index + 1
        compareLoading = true
        recycleResult()

        viewModelScope.launch {
            try {
                val sourceUri = runCatching { Uri.parse(item.documentUri) }.getOrNull()
                val outputUri = item.outputUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
                if (sourceUri == null || outputUri == null) {
                    errorMessage = "原始图片或输出文件不可用"
                    compareLoading = false
                    return@launch
                }

                val pair = withContext(Dispatchers.IO) {
                    val source = ImageLoader.decodeUri(app, sourceUri, 1, COMPARE_PIXEL_BUDGET)
                    val output = ImageLoader.decodeUri(app, outputUri, 1, COMPARE_PIXEL_BUDGET)
                    source to output
                }
                val source = pair.first
                val output = pair.second

                compareBitmaps = listOf(source.bitmap, output.bitmap)
                originalPreview = source.bitmap.asImageBitmap()
                resultPreview = output.bitmap.asImageBitmap()
                resultBitmap = null

                val options = BatchRepository.snapshot?.options
                val model = options?.let { EngineModels.byIdOrDefault(it.modelId) } ?: config.model
                val scale = options?.let {
                    EngineModels.outputScale(it.modelId, it.scale, it.style)
                } ?: config.outputScale
                val backend = BatchRepository.snapshot?.actualBackend?.takeIf { it >= 0 }
                    ?: options?.backend
                    ?: config.backend
                val backendLabel = if (backend == Engine97.BACKEND_QUALCOMM_NPU) {
                    "Qualcomm NPU"
                } else {
                    "Vulkan GPU"
                }

                compareInfo = CompareInfo(
                    sourceWidth = source.sourceWidth,
                    sourceHeight = source.sourceHeight,
                    outputWidth = output.sourceWidth,
                    outputHeight = output.sourceHeight,
                    modelName = "${model.name} · ${scale}x · $backendLabel",
                    elapsedMs = item.elapsedMs,
                    downscaledInput = source.downscaled || output.downscaled,
                )
                compareLoading = false
            } catch (t: Throwable) {
                Log.e(TAG, "Batch compare load failed", t)
                compareLoading = false
                errorMessage = "无法加载对比图：${t.message ?: "未知错误"}"
            }
        }
    }

    // ---- single image flow -------------------------------------------------

    fun onSinglePicked(uri: Uri) {
        pendingUris = listOf(uri)
        pendingIsBatch = false
        dest = Dest.Configure(pendingUris, batch = false)
    }

    fun start() = startSingle()

    fun startSingle() {
        val uri = pendingUris.firstOrNull() ?: return
        val app = getApplication<Application>()
        if (benchmarkRunning) {
            errorMessage = "模型对比测试运行中，请先等待或取消"
            return
        }
        errorMessage = null
        savedUri = null
        actualBackendLabel = ""
        _progress.value = 0f
        _stage.value = "解码图片"
        dest = Dest.Processing
        singleJob?.cancel()

        singleJob = viewModelScope.launch {
            try {
                val runStartedAt = System.currentTimeMillis()
                val runStartElapsed = SystemClock.elapsedRealtime()
                var decodeMs = 0L
                var prepareMs = 0L
                var initMs = 0L
                var inputWidth = 0
                var inputHeight = 0
                originalName = withContext(Dispatchers.IO) { queryDisplayName(uri) } ?: "image"
                val outputScale = config.outputScale
                val decodeStarted = SystemClock.elapsedRealtime()
                val loaded = withContext(Dispatchers.IO) {
                    ImageLoader.decodeUri(app, uri, outputScale, config.maxOutputPixels)
                }
                decodeMs = SystemClock.elapsedRealtime() - decodeStarted
                inputWidth = loaded.bitmap.width
                inputHeight = loaded.bitmap.height

                var backend = config.backend
                var precision = config.precision
                _stage.value = if (backend == Engine97.BACKEND_QUALCOMM_NPU) {
                    "准备 NPU 模型（首次可能几十秒）"
                } else {
                    "加载模型"
                }
                if (backend == Engine97.BACKEND_QUALCOMM_NPU) {
                    val prepareStarted = SystemClock.elapsedRealtime()
                    val prepared = withContext(Dispatchers.IO) {
                        Engine97.prepareNpu(
                            context = app,
                            modelId = config.modelId,
                            scale = config.scale,
                            noise = config.noise,
                            style = config.style,
                            precision = precision,
                        )
                    }
                    prepareMs = SystemClock.elapsedRealtime() - prepareStarted
                    if (prepared == null) {
                        backend = Engine97.BACKEND_VULKAN
                        precision = Engine97.PRECISION_FP16
                        errorMessage = "NPU 模型不可用，本次已改用 Vulkan：${
                            Engine97.npuFailureDetail().take(220)
                        }"
                    }
                }
                _stage.value = if (backend == Engine97.BACKEND_QUALCOMM_NPU) {
                    "加载 NPU 模型"
                } else {
                    "加载模型"
                }
                val initStarted = SystemClock.elapsedRealtime()
                var ready = withContext(Dispatchers.IO) {
                    Engine97.init(
                        context = app,
                        modelId = config.modelId,
                        scale = config.scale,
                        noise = config.noise,
                        style = config.style,
                        backend = backend,
                        precision = precision,
                        tileSize = config.tileSize,
                    )
                }
                if (!ready && backend == Engine97.BACKEND_QUALCOMM_NPU) {
                    backend = Engine97.BACKEND_VULKAN
                    precision = Engine97.PRECISION_FP16
                    errorMessage = "NPU 初始化失败，本次已回退 Vulkan：${
                        Engine97.npuFailureDetail().take(220)
                    }"
                    ready = withContext(Dispatchers.IO) {
                        Engine97.init(
                            context = app,
                            modelId = config.modelId,
                            scale = config.scale,
                            noise = config.noise,
                            style = config.style,
                            backend = backend,
                            precision = precision,
                            tileSize = config.tileSize,
                        )
                    }
                }
                initMs = SystemClock.elapsedRealtime() - initStarted
                if (!ready) {
                    loaded.bitmap.recycle()
                    errorMessage = if (backend == Engine97.BACKEND_QUALCOMM_NPU) {
                        "NPU 不可用或该模型不支持 NPU，请换 Vulkan 或调整模型/倍数"
                    } else {
                        "模型加载失败，请重试"
                    }
                    dest = Dest.Configure(pendingUris, false)
                    return@launch
                }
                actualBackendLabel =
                    if (Engine97.actualBackend(backend) == Engine97.BACKEND_QUALCOMM_NPU) {
                        "Qualcomm NPU"
                    } else {
                        "Vulkan GPU"
                    }

                _stage.value = if (backend == Engine97.BACKEND_QUALCOMM_NPU) {
                    "NPU 推理中"
                } else {
                    "推理中"
                }
                val start = SystemClock.elapsedRealtime()
                val result = Engine97.process(
                    modelId = config.modelId,
                    input = loaded.bitmap,
                    pageIndex = 0,
                ) { percent ->
                    _progress.value = percent / 100f
                }
                val elapsed = SystemClock.elapsedRealtime() - start

                if (result == null) {
                    loaded.bitmap.recycle()
                    errorMessage = "处理失败或已取消"
                    dest = Dest.Configure(pendingUris, false)
                    return@launch
                }

                _stage.value = "生成预览"
                val previewPair = withContext(Dispatchers.Default) {
                    makePreviews(loaded.bitmap, result)
                }
                loaded.bitmap.recycle()

                recycleResult()
                resultBitmap = result
                originalPreview = previewPair.first
                resultPreview = previewPair.second
                compareInfo = CompareInfo(
                    sourceWidth = loaded.sourceWidth,
                    sourceHeight = loaded.sourceHeight,
                    outputWidth = result.width,
                    outputHeight = result.height,
                    modelName = modelLabel(),
                    elapsedMs = elapsed,
                    downscaledInput = loaded.downscaled,
                )
                runCatching {
                    PerfLog.save(
                        app,
                        PerfRun(
                            id = PerfLog.newId(),
                            kind = "single",
                            startedAt = runStartedAt,
                            finishedAt = System.currentTimeMillis(),
                            model = modelLabel(),
                            scale = config.scale,
                            noise = config.noise,
                            style = config.style,
                            backend = if (backend == Engine97.BACKEND_QUALCOMM_NPU) {
                                "NPU"
                            } else {
                                "Vulkan"
                            },
                            precision = if (precision == Engine97.PRECISION_INT8) {
                                "INT8"
                            } else {
                                "FP16"
                            },
                            tileSize = config.tileSize,
                            format = "preview",
                            quality = 0,
                            maxOutputPixels = config.maxOutputPixels,
                            prepareMs = prepareMs,
                            initMs = initMs,
                            totalWallMs = SystemClock.elapsedRealtime() - runStartElapsed,
                            actualBackend = if (
                                Engine97.actualBackend(backend) ==
                                Engine97.BACKEND_QUALCOMM_NPU
                            ) {
                                "NPU"
                            } else {
                                "Vulkan"
                            },
                            device = PerfLog.deviceLabel(),
                            images = listOf(
                                PerfImage(
                                    index = 0,
                                    name = originalName,
                                    status = "DONE",
                                    decodeMs = decodeMs,
                                    inferMs = elapsed,
                                    totalMs = SystemClock.elapsedRealtime() - runStartElapsed,
                                    inputWidth = inputWidth,
                                    inputHeight = inputHeight,
                                    outputWidth = result.width,
                                    outputHeight = result.height,
                                    downscaled = loaded.downscaled,
                                ),
                            ),
                        ),
                    )
                }
                _progress.value = 1f
                dest = Dest.Compare
            } catch (t: Throwable) {
                Log.e(TAG, "Single upscale failed", t)
                errorMessage = t.message ?: "处理失败"
                _stage.value = ""
                dest = Dest.Configure(pendingUris, false)
            }
        }
    }

    private fun modelLabel(): String {
        val model = config.model
        val style = if (model.hasStyle && config.modelId == EngineModels.MODEL_ESRGAN) {
            " ${EngineModels.styleLabel(config.style)}"
        } else {
            ""
        }
        return "${model.name}$style · ${config.outputScale}x · ${
            actualBackendLabel.ifBlank { config.backendLabel }
        }"
    }

    fun cancelProcessing() {
        Engine97.cancel()
        _stage.value = "取消中"
    }

    suspend fun saveResult(): Uri? {
        val bitmap = resultBitmap ?: return null
        val app = getApplication<Application>()
        val uri = ImageSaver.save(
            app,
            bitmap,
            config.format,
            config.quality,
            originalName,
            config.model.name,
            config.outputScale,
        )
        savedUri = uri
        return uri
    }

    fun backHome() {
        recycleResult()
        dest = Dest.Home
    }

    fun backToConfig() {
        recycleResult()
        dest = Dest.Configure(pendingUris, pendingIsBatch)
    }

    fun backFromCompare() {
        if (compareFromBatch) {
            recycleResult()
            compareItems = emptyList()
            compareTotal = 0
            comparePosition = 0
            compareFromBatch = false
            dest = Dest.Tasks
        } else {
            backToConfig()
        }
    }

    fun consumeError() {
        errorMessage = null
    }

    private fun recycleResult() {
        resultBitmap?.takeIf { !it.isRecycled }?.recycle()
        resultBitmap = null
        compareBitmaps.forEach { if (!it.isRecycled) it.recycle() }
        compareBitmaps = emptyList()
        originalPreview = null
        resultPreview = null
        compareInfo = null
    }

    private fun makePreviews(before: Bitmap, after: Bitmap): Pair<ImageBitmap, ImageBitmap> {
        val beforePreview = scaleDown(before, PREVIEW_MAX_EDGE)
        val afterPreview = scaleDown(after, PREVIEW_MAX_EDGE)
        return beforePreview.asImageBitmap() to afterPreview.asImageBitmap()
    }

    private fun scaleDown(source: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= maxEdge) return source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
        val factor = maxEdge.toFloat() / longest
        val w = (source.width * factor).roundToInt().coerceAtLeast(1)
        val h = (source.height * factor).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            getApplication<Application>().contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (t: Throwable) {
            null
        }
    }

    override fun onCleared() {
        singleJob?.cancel()
        scanJob?.cancel()
        benchmarkJob?.cancel()
        Engine97.cancel()
        recycleResult()
        super.onCleared()
    }

    companion object {
        private const val TAG = "LumaSR"
        private const val PREVIEW_MAX_EDGE = 2048
        private const val COMPARE_PIXEL_BUDGET = 4_000_000L
    }
}
