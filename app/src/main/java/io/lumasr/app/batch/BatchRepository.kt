package io.lumasr.app.batch

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.lumasr.app.engine.Engine97
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BatchRepository {
    const val WORK_NAME = "lumasr_batch"

    private val _state = MutableStateFlow<QueueState?>(null)
    val state: StateFlow<QueueState?> = _state.asStateFlow()

    val snapshot: QueueState? get() = _state.value

    suspend fun ensureLoaded(context: Context) {
        if (_state.value == null) {
            _state.value = BatchStore.load(context)
        }
    }

    fun publish(state: QueueState) {
        _state.value = state
    }

    suspend fun persist(context: Context, state: QueueState) {
        _state.value = state
        BatchStore.save(context, state)
    }

    suspend fun resume(context: Context) {
        val current = _state.value ?: return
        if (current.running || current.items.isEmpty()) return
        persist(context, current.copy(running = true, finishedAt = 0))
        enqueue(context)
    }

    suspend fun retryFailed(context: Context) {
        val current = _state.value ?: return
        if (current.running) return
        if (current.items.none { it.status == QueueStatus.FAILED }) return
        val updated = current.copy(
            running = true,
            finishedAt = 0,
            items = current.items.map {
                if (it.status == QueueStatus.FAILED) {
                    it.copy(status = QueueStatus.PENDING, progress = 0f, error = null, elapsedMs = 0)
                } else {
                    it
                }
            },
        )
        persist(context, updated)
        enqueue(context)
    }

    suspend fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Engine97.cancel()

        val current = _state.value ?: return
        val updated = current.copy(
            running = false,
            items = current.items.map {
                if (it.status == QueueStatus.RUNNING) {
                    it.copy(status = QueueStatus.PENDING, progress = 0f)
                } else {
                    it
                }
            },
        )
        persist(context, updated)
    }

    suspend fun clearFinished(context: Context) {
        val current = _state.value ?: return
        val remaining = current.items.filterNot { it.isFinished }
        if (remaining.isEmpty()) {
            BatchStore.clear(context)
            _state.value = null
        } else {
            persist(context, current.copy(items = remaining))
        }
    }

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<BatchWorker>()
            .setConstraints(Constraints.Builder().build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
