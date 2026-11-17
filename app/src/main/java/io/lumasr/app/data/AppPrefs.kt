package io.lumasr.app.data

import android.content.Context
import io.lumasr.app.engine.Engine97
import io.lumasr.app.engine.EngineModels
import io.lumasr.app.model.SaveFormat
import io.lumasr.app.model.UpscaleConfig

object AppPrefs {
    private const val FILE = "lumasr_prefs"

    fun load(context: Context): UpscaleConfig {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return UpscaleConfig(
            modelId = prefs.getInt("model_id", EngineModels.default.id),
            scale = prefs.getInt("scale", 2),
            noise = prefs.getInt("noise", 4),
            style = prefs.getInt("style", EngineModels.STYLE_ANIME),
            backend = prefs.getInt("backend", Engine97.defaultBackend()),
            precision = prefs.getInt("precision", Engine97.PRECISION_FP16),
            maxOutputMp = prefs.getInt("max_output_mp", 24),
            format = runCatching {
                SaveFormat.valueOf(prefs.getString("format", SaveFormat.PNG.name)!!)
            }.getOrDefault(SaveFormat.PNG),
            quality = prefs.getInt("quality", 95),
            tileSize = prefs.getInt("tile_size", UpscaleConfig.DEFAULT_TILE_SIZE),
            recursiveScan = prefs.getBoolean("recursive_scan", true),
            preserveStructure = prefs.getBoolean("preserve_structure", true),
            skipProcessed = prefs.getBoolean("skip_processed", true),
            outputFolderName = prefs.getString("output_folder", "LumaSR_Output")
                ?.takeIf { it.isNotBlank() } ?: "LumaSR_Output",
            notifications = prefs.getBoolean("notifications", true),
        )
    }

    fun save(context: Context, config: UpscaleConfig) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt("model_id", config.modelId)
            .putInt("scale", config.scale)
            .putInt("noise", config.noise)
            .putInt("style", config.style)
            .putInt("backend", config.backend)
            .putInt("precision", config.precision)
            .putInt("max_output_mp", config.maxOutputMp)
            .putString("format", config.format.name)
            .putInt("quality", config.quality)
            .putInt("tile_size", config.tileSize)
            .putBoolean("recursive_scan", config.recursiveScan)
            .putBoolean("preserve_structure", config.preserveStructure)
            .putBoolean("skip_processed", config.skipProcessed)
            .putString("output_folder", config.outputFolderName)
            .putBoolean("notifications", config.notifications)
            .apply()
    }

    fun loadLastFolder(context: Context): Pair<String, String>? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val uri = prefs.getString("last_tree_uri", null) ?: return null
        val name = prefs.getString("last_tree_name", "") ?: ""
        return uri to name
    }

    fun saveLastFolder(context: Context, uri: String, name: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("last_tree_uri", uri)
            .putString("last_tree_name", name)
            .apply()
    }
}
