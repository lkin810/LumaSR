package io.lumasr.app.engine

import android.content.Context
import android.graphics.Bitmap
import eu.kanade.tachiyomi.util.npu.NpuModelStatus
import eu.kanade.tachiyomi.util.qnn.QnnModelContexts
import eu.kanade.tachiyomi.util.qnn.QualcommHtp
import eu.kanade.tachiyomi.util.waifu2x.Waifu2x
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Facade over the prebuilt Mihon Upscale engine (NCNN Vulkan + Qualcomm QNN).
 *
 * The native library is shipped as a prebuilt arm64-v8a .so with static JNI
 * registration, so its Kotlin classes must stay in the original
 * `eu.kanade.tachiyomi.util.*` packages.
 */
object Engine97 {
    const val BACKEND_VULKAN = Waifu2x.PROCESSING_BACKEND_VULKAN
    const val BACKEND_QUALCOMM_NPU = Waifu2x.PROCESSING_BACKEND_QUALCOMM_NPU

    const val PRECISION_FP16 = 0
    const val PRECISION_INT8 = QnnModelContexts.PRECISION_W8A8

    val npuStates: StateFlow<Map<String, NpuModelStatus>> get() = QnnModelContexts.states

    val fp16Supported: StateFlow<Boolean?> get() = QnnModelContexts.fp16Supported

    val nativeLoaded: Boolean by lazy {
        runCatching { System.loadLibrary("waifu2x-jni") }.isSuccess
    }

    @Volatile
    private var lastNpuKey: String? = null

    private val mutableLastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = mutableLastError.asStateFlow()

    /** Human-readable reasons from prepare/init, for the diagnostics card. */
    fun npuFailureDetail(): String {
        val parts = buildList {
            Waifu2x.lastQnnInitError?.let { add("QNN init: $it") }
            QnnModelContexts.states.value.values
                .filterIsInstance<NpuModelStatus.Failed>()
                .lastOrNull()
                ?.message
                ?.takeIf { it.isNotBlank() }
                ?.let { add("QNN prepare: $it") }
            mutableLastError.value?.let { add(it) }
            if (!qnnRuntimeAvailable()) add("QNN runtime 不可用")
            if (pageSizeBytes() == 16384L) {
                add("系统页大小 16KB，QNN skel 为 4KB 对齐，可能导致 nativeInitQnn 失败")
            }
        }.distinct()
        return parts.joinToString(" | ").ifBlank { "无详细错误" }
    }

    fun defaultBackend(): Int =
        if (isQualcommNpuAvailable()) BACKEND_QUALCOMM_NPU else BACKEND_VULKAN

    fun qnnRuntimeAvailable(): Boolean = runCatching {
        Waifu2x.isQnnRuntimeAvailable()
    }.getOrDefault(false)

    fun pageSizeBytes(): Long? = runCatching {
        android.system.Os.sysconf(android.system.OsConstants._SC_PAGE_SIZE)
    }.getOrNull()

    fun isNpuActive(): Boolean = runCatching {
        Waifu2x.isQnnActive()
    }.getOrDefault(false)

    fun actualBackend(requested: Int): Int =
        if (requested == BACKEND_QUALCOMM_NPU && isNpuActive()) {
            BACKEND_QUALCOMM_NPU
        } else {
            BACKEND_VULKAN
        }

    fun isQualcommNpuAvailable(): Boolean = runCatching {
        Waifu2x.isQualcommNpuAvailable()
    }.getOrDefault(false)

    fun htpArchitecture(context: Context): Int? = runCatching {
        QualcommHtp.architecture(context)
    }.getOrNull()

    fun detectFp16Support(context: Context): Boolean? = runCatching {
        QnnModelContexts.detectFp16Support(context)
    }.getOrNull()

    fun qnnModelName(
        modelId: Int,
        scale: Int,
        noise: Int,
        style: Int = EngineModels.STYLE_ANIME,
    ): String? = runCatching {
        Waifu2x.qnnModelName(modelId, EngineModels.outputScale(modelId, scale, style), noise, style)
    }.getOrNull()

    fun npuStateKey(modelName: String, precision: Int): String =
        QnnModelContexts.stateKey(modelName, precision)

    suspend fun prepareNpu(
        context: Context,
        modelId: Int,
        scale: Int,
        noise: Int,
        style: Int,
        precision: Int,
        retry: Boolean = false,
    ): File? = withContext(Dispatchers.IO) {
        val name = qnnModelName(modelId, scale, noise, style)
        if (name == null) {
            mutableLastError.value = "prepare: 当前模型/倍数没有对应 QNN 模型"
            return@withContext null
        }
        val effectivePrecision = resolveNpuPrecision(name, precision)
        val result = QnnModelContexts.prepare(
            context, name, retry = retry, precision = effectivePrecision,
        )
        if (result == null) {
            val state = QnnModelContexts.states.value.values
                .filterIsInstance<NpuModelStatus.Failed>()
                .lastOrNull()
            mutableLastError.value = "prepare: ${state?.message ?: "$name 编译失败"}"
        }
        result
    }

    private fun resolveNpuPrecision(modelName: String, precision: Int): Int {
        if (precision == PRECISION_INT8 && QnnModelContexts.supportsW8a8(modelName)) {
            return PRECISION_INT8
        }
        return 0
    }

    /**
     * Blocking model initialization. Call from an IO dispatcher.
     */
    fun init(
        context: Context,
        modelId: Int,
        scale: Int,
        noise: Int,
        style: Int,
        backend: Int,
        precision: Int = PRECISION_FP16,
        tileSize: Int = 128,
        tileSleepMs: Int = 0,
    ): Boolean {
        if (!nativeLoaded) return false

        // QNN keeps one context per process. Switching model/scale/precision
        // without rebuilding it would silently keep using the previous model.
        val npuKey = if (backend == BACKEND_QUALCOMM_NPU) {
            val name = qnnModelName(modelId, scale, noise, style) ?: return false
            "$name|$precision"
        } else {
            null
        }
        if (npuKey != null) {
            val previous = lastNpuKey
            if (previous != null && previous != npuKey) {
                android.util.Log.i("LumaSR", "NPU context changes ($previous -> $npuKey), reinitializing")
                runCatching { Waifu2x.destroy() }
                lastNpuKey = null
            }
        }

        return try {
            val ok = when (modelId) {
                EngineModels.MODEL_CUGAN_SE -> Waifu2x.initRealCugan(
                    context, noise, scale, isPro = false,
                    tileSleepMs = tileSleepMs, tileSize = tileSize,
                    precision = precision, fp16Arithmetic = false,
                    processingBackend = backend,
                )

                EngineModels.MODEL_CUGAN_PRO -> Waifu2x.initRealCugan(
                    context, noise, scale, isPro = true,
                    tileSleepMs = tileSleepMs, tileSize = tileSize,
                    precision = precision, fp16Arithmetic = false,
                    processingBackend = backend,
                )

                EngineModels.MODEL_ESRGAN -> Waifu2x.initRealESRGAN(
                    context, scale, style = style,
                    tileSleepMs = tileSleepMs, tileSize = tileSize,
                    precision = precision, fp16Arithmetic = false,
                    processingBackend = backend,
                )

                EngineModels.MODEL_NOSE -> if (backend == BACKEND_VULKAN) {
                    Waifu2x.initNose(
                        context,
                        tileSleepMs = tileSleepMs, tileSize = tileSize,
                        precision = precision, fp16Arithmetic = false,
                    )
                } else {
                    false
                }

                EngineModels.MODEL_WAIFU2X -> if (backend == BACKEND_VULKAN) {
                    Waifu2x.initWaifu2x(
                        context, noise, 2,
                        tileSleepMs = tileSleepMs, tileSize = tileSize,
                        precision = precision, fp16Arithmetic = false,
                    )
                } else {
                    false
                }

                EngineModels.MODEL_WAIFU2X_FAST -> if (backend == BACKEND_VULKAN) {
                    Waifu2x.initWaifu2xUpconv7(
                        context, noise, 2,
                        tileSleepMs = tileSleepMs, tileSize = tileSize,
                        precision = precision, fp16Arithmetic = false,
                    )
                } else {
                    false
                }

                else -> Waifu2x.initW2xEx(
                    context, modelId,
                    scale = 2,
                    tileSleepMs = tileSleepMs, tileSize = tileSize,
                    precision = precision, fp16Arithmetic = false,
                    processingBackend = backend,
                )
            }
            if (ok && npuKey != null && isNpuActive()) {
                lastNpuKey = npuKey
                mutableLastError.value = null
            } else if (!ok && npuKey != null) {
                mutableLastError.value = "init: ${Waifu2x.lastQnnInitError ?: "native init returned false"}"
            }
            ok
        } catch (t: Throwable) {
            mutableLastError.value = "init: ${t.message ?: t.javaClass.simpleName}"
            android.util.Log.e("LumaSR", "Engine97 init failed for $modelId", t)
            false
        }
    }

    suspend fun process(
        modelId: Int,
        input: Bitmap,
        pageIndex: Int = 0,
        onProgress: (Int) -> Unit = {},
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (!nativeLoaded || input.isRecycled) return@withContext null
        Waifu2x.prepareProcessing()
        val poll = launch {
            while (isActive) {
                onProgress(Waifu2x.getProgressPercent().coerceIn(0, 100))
                delay(120)
            }
        }
        try {
            val result = when (modelId) {
                EngineModels.MODEL_CUGAN_SE,
                EngineModels.MODEL_CUGAN_PRO,
                -> Waifu2x.processRealCugan(input, pageIndex)

                EngineModels.MODEL_ESRGAN -> Waifu2x.processRealESRGAN(input, pageIndex)
                EngineModels.MODEL_NOSE -> Waifu2x.processNose(input, pageIndex)
                EngineModels.MODEL_WAIFU2X,
                EngineModels.MODEL_WAIFU2X_FAST,
                -> Waifu2x.processWaifu2x(input, pageIndex)

                else -> Waifu2x.processW2xEx(input, pageIndex)
            }
            onProgress(100)
            result
        } catch (t: Throwable) {
            android.util.Log.e("LumaSR", "Engine97 process failed for $modelId", t)
            null
        } finally {
            poll.cancel()
        }
    }

    fun cancel() {
        runCatching { Waifu2x.abortProcessing() }
    }

    fun destroy() {
        lastNpuKey = null
        runCatching { Waifu2x.destroy() }
    }
}
