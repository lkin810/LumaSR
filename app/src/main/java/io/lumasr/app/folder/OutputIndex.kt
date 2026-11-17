package io.lumasr.app.folder

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Persistent source -> output mapping for one folder tree + output rule.
 *
 * File names are ambiguous in flat mode (two `a.jpg` in different sub-folders
 * both want `a.png`), so the index records the exact output chosen at queue
 * creation. Missing/corrupt indexes fall back to the old filename check.
 */
object OutputIndex {
    private const val TAG = "LumaSR"
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Index(val records: Map<String, OutputRecord> = emptyMap())

    suspend fun load(
        context: Context,
        treeUri: Uri,
        outputFolderName: String,
        preserveStructure: Boolean,
    ): Map<String, OutputRecord> = withContext(Dispatchers.IO) {
        val file = file(context, treeUri, outputFolderName, preserveStructure)
        if (!file.isFile || file.length() == 0L) return@withContext emptyMap()
        runCatching {
            json.decodeFromString(Index.serializer(), file.readText()).records
        }.getOrElse {
            Log.w(TAG, "Unable to read output index: ${it.message}")
            emptyMap()
        }
    }

    suspend fun merge(
        context: Context,
        treeUri: Uri,
        outputFolderName: String,
        preserveStructure: Boolean,
        records: Collection<OutputRecord>,
    ) = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext
        val file = file(context, treeUri, outputFolderName, preserveStructure)
        val current = if (file.isFile && file.length() > 0L) {
            runCatching {
                json.decodeFromString(Index.serializer(), file.readText()).records
            }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }
        val merged = current.toMutableMap()
        records.forEach { merged[it.sourcePath] = it }
        save(file, Index(merged))
    }

    private fun save(file: File, index: Index) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.encodeToString(Index.serializer(), index))
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }.onFailure {
            Log.w(TAG, "Unable to write output index: ${it.message}")
        }
    }

    private fun file(
        context: Context,
        treeUri: Uri,
        outputFolderName: String,
        preserveStructure: Boolean,
    ): File {
        val identity = "$treeUri|$outputFolderName|$preserveStructure"
        val name = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)
        return File(File(context.filesDir, "output-index"), "$name.json")
    }
}
