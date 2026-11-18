package io.lumasr.app.folder

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import io.lumasr.app.model.SaveFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.IOException

/**
 * SAF helpers: recursive folder scan, output folder creation and bitmap writes.
 * All output goes to `<source>/<outputFolderName>`, optionally mirroring the
 * source sub-directory structure.
 */
object FolderScanner {
    private const val TAG = "LumaSR"

    /** Root-level benchmark output folder, kept out of normal folder scans. */
    const val TEST_FOLDER = "LumaSR_Test"

    private val IMAGE_EXTENSIONS = setOf(
        "png", "jpg", "jpeg", "webp", "bmp", "gif", "heic", "heif", "avif", "tif", "tiff",
    )

    private val PROJECTION = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
    )

    private data class Child(
        val docId: String,
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val mimeType: String?,
    )

    data class OutputRefresh(
        val files: Set<String>,
        val records: Map<String, OutputRecord>,
    )

    fun takePermission(context: Context, treeUri: Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        true
    }.getOrDefault(false)

    suspend fun scan(
        context: Context,
        treeUri: Uri,
        recursive: Boolean,
        preserveStructure: Boolean,
        format: SaveFormat,
        outputFolderName: String,
        onCount: (Int) -> Unit = {},
    ): FolderSnapshot = withContext(Dispatchers.IO) {
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
        val rootChildren = rootDocId?.let { queryChildren(context, treeUri, it) }
        val rootDocument = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull()
            ?: throw IOException("无法访问所选文件夹")
        val folderName = rootDocId?.let { queryRootName(context, treeUri, it) }
            ?: rootDocument.name?.takeIf { it.isNotBlank() }
            ?: "所选文件夹"

        val images = mutableListOf<FolderImage>()
        if (rootChildren != null && rootDocId != null) {
            walkCursor(
                context, treeUri, rootChildren, "", recursive, outputFolderName, images, onCount,
            )
        } else {
            walkDocumentFile(
                context, rootDocument, "", recursive, outputFolderName, images, onCount,
            )
        }

        val outputFiles = mutableSetOf<String>()
        if (rootChildren != null) {
            rootChildren
                .firstOrNull { it.isDirectory && it.name == outputFolderName }
                ?.let { collectOutputCursor(context, treeUri, it.docId, "", outputFiles) }
        } else {
            runCatching { rootDocument.findFile(outputFolderName) }
                .getOrNull()
                ?.takeIf { it.isDirectory }
                ?.let { collectOutputDocumentFile(it, "", outputFiles) }
        }

        val records = OutputIndex.load(context, treeUri, outputFolderName, preserveStructure)
        val annotated = images
            .map { image ->
                image.copy(
                    alreadyProcessed = isProcessed(
                        image, outputFiles, records, preserveStructure, format,
                    ),
                )
            }
            .sortedWith { a, b -> naturalCompare(a.relativePath, b.relativePath) }

        onCount(annotated.size)
        FolderSnapshot(
            treeUri = treeUri,
            folderName = folderName,
            images = annotated,
            recursive = recursive,
            outputFolderName = outputFolderName,
            outputFileCount = outputFiles.size,
            outputFiles = outputFiles,
            outputRecords = records,
        )
    }

    /** Re-reads only the output tree after a batch finishes; no input rescan. */
    suspend fun refreshOutputState(
        context: Context,
        treeUri: Uri,
        outputFolderName: String,
        preserveStructure: Boolean,
    ): OutputRefresh = withContext(Dispatchers.IO) {
        val files = mutableSetOf<String>()
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
        val rootChildren = rootDocId?.let { queryChildren(context, treeUri, it) }
        if (rootChildren != null) {
            rootChildren
                .firstOrNull { it.isDirectory && it.name == outputFolderName }
                ?.let { collectOutputCursor(context, treeUri, it.docId, "", files) }
        } else {
            runCatching { DocumentFile.fromTreeUri(context, treeUri)?.findFile(outputFolderName) }
                .getOrNull()
                ?.takeIf { it.isDirectory }
                ?.let { collectOutputDocumentFile(it, "", files) }
        }
        OutputRefresh(
            files = files,
            records = OutputIndex.load(context, treeUri, outputFolderName, preserveStructure),
        )
    }

    /**
     * Processed check. The index wins when present (it knows the exact
     * collision-free name). In flat mode an existing index is authoritative so
     * two same-named sources are not both treated as done.
     */
    fun isProcessed(
        image: FolderImage,
        outputFiles: Set<String>,
        records: Map<String, OutputRecord>,
        preserveStructure: Boolean,
        format: SaveFormat,
    ): Boolean {
        val record = records[image.relativePath]
        if (record != null && record.outputPath in outputFiles) {
            if (record.copied) return true
            if (record.format == format.name) return true
        }
        if (!preserveStructure && records.isNotEmpty()) return false
        return expectedOutputPath(image, preserveStructure, format) in outputFiles
    }

    private suspend fun walkCursor(
        context: Context,
        treeUri: Uri,
        children: List<Child>,
        prefix: String,
        recursive: Boolean,
        outputFolderName: String,
        out: MutableList<FolderImage>,
        onCount: (Int) -> Unit,
    ) {
        currentCoroutineContext().ensureActive()
        for (child in children) {
            if (child.isDirectory) {
                if (!recursive || child.name.startsWith(".")) continue
                // Only root-level output/test folders are excluded; a nested
                // folder that happens to share the name is still a valid source.
                if (prefix.isEmpty() &&
                    (child.name == outputFolderName || child.name == TEST_FOLDER)
                ) {
                    continue
                }
                val nested = queryChildren(context, treeUri, child.docId) ?: continue
                walkCursor(
                    context, treeUri, nested, "$prefix${child.name}/", true,
                    outputFolderName, out, onCount,
                )
            } else {
                if (!isImage(child.name, child.mimeType)) continue
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child.docId)
                val (width, height) = readDimensions(context, uri)
                out.add(
                    FolderImage(
                        documentUri = uri,
                        name = child.name,
                        relativePath = "$prefix${child.name}",
                        sizeBytes = child.size,
                        mimeType = child.mimeType,
                        width = width,
                        height = height,
                    ),
                )
                if (out.size % 25 == 0) onCount(out.size)
            }
        }
    }

    private suspend fun walkDocumentFile(
        context: Context,
        dir: DocumentFile,
        prefix: String,
        recursive: Boolean,
        outputFolderName: String,
        out: MutableList<FolderImage>,
        onCount: (Int) -> Unit,
    ) {
        currentCoroutineContext().ensureActive()
        val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
        for (child in children) {
            val name = child.name ?: continue
            if (child.isDirectory) {
                if (!recursive || name.startsWith(".")) continue
                if (prefix.isEmpty() &&
                    (name == outputFolderName || name == TEST_FOLDER)
                ) {
                    continue
                }
                walkDocumentFile(
                    context, child, "$prefix$name/", true, outputFolderName, out, onCount,
                )
            } else if (isImage(name, child.type)) {
                val (width, height) = readDimensions(context, child.uri)
                out.add(
                    FolderImage(
                        documentUri = child.uri,
                        name = name,
                        relativePath = "$prefix$name",
                        sizeBytes = child.length(),
                        mimeType = child.type,
                        width = width,
                        height = height,
                    ),
                )
                if (out.size % 25 == 0) onCount(out.size)
            }
        }
    }

    private fun collectOutputCursor(
        context: Context,
        treeUri: Uri,
        docId: String,
        prefix: String,
        out: MutableSet<String>,
    ) {
        val children = queryChildren(context, treeUri, docId) ?: return
        for (child in children) {
            if (child.isDirectory) {
                if (!child.name.startsWith(".")) {
                    collectOutputCursor(
                        context, treeUri, child.docId, "$prefix${child.name}/", out,
                    )
                }
            } else if (child.size != 0L) {
                out.add("$prefix${child.name}")
            }
        }
    }

    private fun collectOutputDocumentFile(
        dir: DocumentFile,
        prefix: String,
        out: MutableSet<String>,
    ) {
        val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
        for (child in children) {
            val name = child.name ?: continue
            if (child.isDirectory) {
                if (!name.startsWith(".")) {
                    collectOutputDocumentFile(child, "$prefix$name/", out)
                }
            } else if (child.length() != 0L) {
                out.add("$prefix$name")
            }
        }
    }

    private fun queryChildren(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
    ): List<Child>? = runCatching {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        context.contentResolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndex(PROJECTION[0])
            val nameIndex = cursor.getColumnIndex(PROJECTION[1])
            val mimeIndex = cursor.getColumnIndex(PROJECTION[2])
            val sizeIndex = cursor.getColumnIndex(PROJECTION[3])
            if (idIndex < 0 || nameIndex < 0 || mimeIndex < 0) return@use emptyList()

            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIndex) ?: continue
                    val name = cursor.getString(nameIndex) ?: continue
                    val mime = cursor.getString(mimeIndex)
                    val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        cursor.getLong(sizeIndex)
                    } else {
                        -1L
                    }
                    add(
                        Child(
                            docId = id,
                            name = name,
                            isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                            size = size,
                            mimeType = mime,
                        ),
                    )
                }
            }
        }
    }.getOrNull()

    private fun queryRootName(context: Context, treeUri: Uri, rootDocId: String): String? =
        runCatching {
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()

    private fun isImage(name: String, mime: String?): Boolean {
        if (mime?.startsWith("image/") == true) return true
        return name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
    }

    /** Reads intrinsic size from the header; no pixels are decoded. */
    private fun readDimensions(context: Context, uri: Uri): Pair<Int, Int> = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        options.outWidth to options.outHeight
    }.getOrDefault(0 to 0)

    fun expectedOutputPath(
        image: FolderImage,
        preserveStructure: Boolean,
        format: SaveFormat,
    ): String {
        val base = image.name.substringBeforeLast('.', image.name)
        val dir = if (preserveStructure) image.relativePath.substringBeforeLast('/', "") else ""
        return if (dir.isEmpty()) "$base.${format.ext}" else "$dir/$base.${format.ext}"
    }

    /** Builds a collision-free output path in flattened mode. */
    fun uniqueOutputPath(
        image: FolderImage,
        preserveStructure: Boolean,
        format: SaveFormat,
        used: MutableSet<String>,
    ): String {
        val base = image.name.substringBeforeLast('.', image.name)
        val dir = if (preserveStructure) image.relativePath.substringBeforeLast('/', "") else ""
        var candidate = if (dir.isEmpty()) "$base.${format.ext}" else "$dir/$base.${format.ext}"
        var index = 2
        while (candidate in used) {
            candidate = if (dir.isEmpty()) {
                "${base}_$index.${format.ext}"
            } else {
                "$dir/${base}_$index.${format.ext}"
            }
            index++
        }
        used.add(candidate)
        return candidate
    }

    /** Collision-free path that keeps the original file name and extension. */
    fun uniqueCopyOutputPath(
        image: FolderImage,
        preserveStructure: Boolean,
        used: MutableSet<String>,
    ): String {
        val ext = image.name.substringAfterLast('.', "").lowercase().ifBlank { "img" }
        val base = image.name.substringBeforeLast('.', image.name)
        val dir = if (preserveStructure) image.relativePath.substringBeforeLast('/', "") else ""
        fun path(suffix: String): String {
            val name = if (suffix.isEmpty()) "$base.$ext" else "${base}_$suffix.$ext"
            return if (dir.isEmpty()) name else "$dir/$name"
        }
        var candidate = path("")
        var index = 2
        while (candidate in used) {
            candidate = path(index.toString())
            index++
        }
        used.add(candidate)
        return candidate
    }

    fun ensureOutputRoot(
        context: Context,
        treeUri: Uri,
        outputFolderName: String,
    ): DocumentFile? {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val existing = runCatching { root.findFile(outputFolderName) }.getOrNull()
        if (existing != null && existing.isDirectory) return existing
        if (existing != null) runCatching { existing.delete() }
        return runCatching { root.createDirectory(outputFolderName) }.getOrNull()
    }

    fun ensureDirectory(parent: DocumentFile, name: String): DocumentFile? {
        val existing = runCatching { parent.findFile(name) }.getOrNull()
        if (existing != null && existing.isDirectory) return existing
        if (existing != null) runCatching { existing.delete() }
        return runCatching { parent.createDirectory(name) }.getOrNull()
    }

    fun openFolder(context: Context, treeUri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(treeUri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            Log.w(TAG, "No activity can open folder: ${it.message}")
            false
        }
    }

    /** Writes bitmaps below an output tree, caching directory handles. */
    class OutputWriter(
        private val context: Context,
        private val root: DocumentFile,
    ) {
        private val dirs = mutableMapOf("" to root)

        fun write(
            relativePath: String,
            bitmap: Bitmap,
            format: SaveFormat,
            quality: Int,
        ): Uri? {
            val parentPath = relativePath.substringBeforeLast('/', "")
            val fileName = relativePath.substringAfterLast('/')
            val parent = resolveDir(parentPath) ?: return null

            parent.findFile(fileName)?.let { runCatching { it.delete() } }
            val doc = runCatching { parent.createFile(format.mime, fileName) }.getOrNull() ?: return null
            val ok = runCatching {
                context.contentResolver.openOutputStream(doc.uri, "wt")?.use { raw ->
                    BufferedOutputStream(raw).use { output ->
                        bitmap.compress(compressFormat(format), quality.coerceIn(1, 100), output)
                    }
                } ?: false
            }.getOrDefault(false)

            if (!ok) {
                runCatching { doc.delete() }
                return null
            }
            return doc.uri
        }

        fun copy(relativePath: String, sourceUri: Uri): Uri? {
            val parentPath = relativePath.substringBeforeLast('/', "")
            val fileName = relativePath.substringAfterLast('/')
            val parent = resolveDir(parentPath) ?: return null
            val mime = context.contentResolver.getType(sourceUri) ?: mimeForName(fileName)

            parent.findFile(fileName)?.let { runCatching { it.delete() } }
            val doc = runCatching { parent.createFile(mime, fileName) }.getOrNull() ?: return null
            val ok = runCatching {
                val input = context.contentResolver.openInputStream(sourceUri)
                    ?: return@runCatching false
                input.use { source ->
                    val output = context.contentResolver.openOutputStream(doc.uri, "wt")
                        ?: return@runCatching false
                    output.use { target -> source.copyTo(target) }
                }
                true
            }.getOrDefault(false)

            if (!ok) {
                runCatching { doc.delete() }
                return null
            }
            return doc.uri
        }

        private fun mimeForName(name: String): String = when (name.substringAfterLast('.').lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "heic", "heif" -> "image/heic"
            "avif" -> "image/avif"
            "tif", "tiff" -> "image/tiff"
            else -> "application/octet-stream"
        }

        fun writeText(relativePath: String, text: String): Uri? {
            val parentPath = relativePath.substringBeforeLast('/', "")
            val fileName = relativePath.substringAfterLast('/')
            val parent = resolveDir(parentPath) ?: return null
            val mime = if (fileName.endsWith(".txt")) "text/plain" else "text/csv"

            parent.findFile(fileName)?.let { runCatching { it.delete() } }
            val doc = runCatching { parent.createFile(mime, fileName) }.getOrNull() ?: return null
            val ok = runCatching {
                context.contentResolver.openOutputStream(doc.uri, "wt")?.use { output ->
                    output.write(text.toByteArray(Charsets.UTF_8))
                    true
                } ?: false
            }.getOrDefault(false)
            if (!ok) {
                runCatching { doc.delete() }
                return null
            }
            return doc.uri
        }

        private fun resolveDir(path: String): DocumentFile? {
            if (path.isEmpty()) return root
            dirs[path]?.let { return it }
            val parentPath = path.substringBeforeLast('/', "")
            val name = path.substringAfterLast('/')
            val parent = resolveDir(parentPath) ?: return null
            val existing = parent.findFile(name)?.takeIf { it.isDirectory }
            val dir = existing ?: runCatching { parent.createDirectory(name) }.getOrNull() ?: return null
            dirs[path] = dir
            return dir
        }
    }

    private fun compressFormat(format: SaveFormat): Bitmap.CompressFormat = when (format) {
        SaveFormat.PNG -> Bitmap.CompressFormat.PNG
        SaveFormat.JPEG -> Bitmap.CompressFormat.JPEG
        SaveFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
    }
}
