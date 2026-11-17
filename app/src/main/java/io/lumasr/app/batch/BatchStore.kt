package io.lumasr.app.batch

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

object BatchStore {
    private const val TAG = "LumaSR"
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private fun file(context: Context) = File(context.filesDir, "batch_queue.json")

    suspend fun load(context: Context): QueueState? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val f = file(context)
            if (!f.isFile || f.length() == 0L) return@withLock null
            try {
                json.decodeFromString<QueueState>(f.readText())
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to read batch queue: ${t.message}", t)
                null
            }
        }
    }

    suspend fun save(context: Context, state: QueueState) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val f = file(context)
            val tmp = File(f.parentFile, "${f.name}.tmp")
            try {
                tmp.writeText(json.encodeToString(QueueState.serializer(), state))
                if (!tmp.renameTo(f)) {
                    tmp.copyTo(f, overwrite = true)
                    tmp.delete()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to write batch queue: ${t.message}", t)
            }
        }
    }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            file(context).delete()
        }
    }
}
