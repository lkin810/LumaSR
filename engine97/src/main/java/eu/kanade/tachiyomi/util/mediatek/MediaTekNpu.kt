package eu.kanade.tachiyomi.util.mediatek

import android.content.Context
import eu.kanade.tachiyomi.util.npu.NpuModelStatus
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Snapdragon builds do not use the MediaTek LiteRT backend. The prebuilt
 * engine still exports the MediaTek JNI symbols, so keep a compatible stub to
 * avoid native registration problems while always reporting "unavailable".
 */
object MediaTekNpu {
    val states = MutableStateFlow<Map<String, NpuModelStatus>>(emptyMap())

    fun isDeviceSupported(): Boolean = false

    fun isActive(): Boolean = false

    fun prepare(context: Context, modelName: String, retry: Boolean = false): File? = null

    fun initialize(context: Context, modelName: String): Boolean = false

    private external fun nativeInitialize(
        model: String,
        cache: String,
        libraries: String,
        scale: Int,
        padding: Int,
        cpuTest: Boolean,
        prepareOnly: Boolean,
    ): String

    private external fun nativeIsActive(): Boolean
}
