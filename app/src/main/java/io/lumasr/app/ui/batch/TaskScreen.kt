package io.lumasr.app.ui.batch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.lumasr.app.AppViewModel
import io.lumasr.app.batch.QueueItem
import io.lumasr.app.batch.QueueState
import io.lumasr.app.batch.QueueStatus
import io.lumasr.app.engine.Engine97
import io.lumasr.app.ui.components.ProgressRing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(vm: AppViewModel, state: QueueState?) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("任务")
                        Text(
                            when {
                                state == null || state.items.isEmpty() -> "暂无任务"
                                state.running -> "${state.folderName} · 后台运行中"
                                else -> "${state.folderName} · 已暂停"
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            if (state != null && state.items.isNotEmpty()) {
                TaskActions(vm, state)
            }
        },
    ) { padding ->
        if (state == null || state.items.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "暂无任务",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "从首页选择文件夹开始批量超分",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = vm::openHome) { Text("去首页") }
                }
            }
            return@Scaffold
        }

        val doneItems = state.items.filter {
            it.status == QueueStatus.DONE && it.outputUri != null && !it.copyOnly
        }
        val current = state.items.firstOrNull { it.status == QueueStatus.RUNNING }

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item {
                OverallCard(state, current)
            }

            if (state.failedCount > 0) {
                item {
                    FailedCard(state, vm::retryFailed)
                }
            }

            if (doneItems.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "结果预览",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "点击对比",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(doneItems.takeLast(20)) { item ->
                            AsyncImage(
                                model = item.outputUri,
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(74.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { vm.openBatchCompare(item) },
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "任务列表",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            items(state.items) { item ->
                TaskItemRow(item)
            }
            item { Spacer(Modifier.height(6.dp)) }
        }
    }
}

@Composable
private fun OverallCard(state: QueueState, current: QueueItem?) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    progress = state.progress,
                    modifier = Modifier.size(120.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${(state.progress * 100).toInt()}%",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${state.doneCount + state.failedCount + state.skippedCount} / ${state.items.size}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            state.running -> "处理中"
                            state.pendingCount > 0 -> "已暂停"
                            else -> "处理完成"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        current?.name ?: outputLabel(state),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    val avgMs = state.items
                        .filter { it.status == QueueStatus.DONE && it.elapsedMs > 0 }
                        .map { it.elapsedMs }
                        .average()
                        .takeIf { !it.isNaN() }
                    val eta = if (state.running && avgMs != null) {
                        (avgMs * state.pendingCount / 1000).toInt()
                    } else {
                        null
                    }
                    Text(
                        buildString {
                            append("成功 ${state.doneCount}")
                            if (state.failedCount > 0) append(" · 失败 ${state.failedCount}")
                            if (state.skippedCount > 0) append(" · 跳过 ${state.skippedCount}")
                            if (state.actualBackend >= 0) {
                                append(" · 实际 ")
                                append(
                                    if (state.actualBackend == Engine97.BACKEND_QUALCOMM_NPU) {
                                        "NPU"
                                    } else {
                                        "Vulkan"
                                    },
                                )
                            }
                            if (eta != null && eta > 0) append("\n剩余约 $eta 秒")
                        },
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!state.fallbackReason.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            state.fallbackReason,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (state.running && current != null) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { current.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                )
            }

            val totalMs = if (state.finishedAt > 0 && state.startedAt > 0) {
                state.finishedAt - state.startedAt
            } else {
                0L
            }
            if (totalMs > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "总用时 ${totalMs / 1000} 秒 · 输出到 ${outputLabel(state)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun outputLabel(state: QueueState): String =
    if (!state.outputTreeUri.isNullOrBlank()) {
        "已指定导出目录/${state.folderName}"
    } else {
        "${state.folderName}/${state.outputFolderName}"
    }

@Composable
private fun FailedCard(state: QueueState, onRetry: () -> Unit) {
    val firstError = state.items.firstOrNull { it.status == QueueStatus.FAILED }?.error
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.24f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${state.failedCount} 张失败",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                )
                if (!firstError.isNullOrBlank()) {
                    Text(
                        firstError,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!state.running) {
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }
    }
}

@Composable
private fun TaskActions(vm: AppViewModel, state: QueueState) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (state.running) {
                OutlinedButton(
                    onClick = vm::cancelBatch,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("暂停")
                }
                Button(
                    onClick = vm::openOutputFolder,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("打开文件夹")
                }
            } else if (state.pendingCount > 0) {
                OutlinedButton(
                    onClick = vm::clearBatch,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("清理任务")
                }
                Button(
                    onClick = vm::resumeBatch,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("继续处理")
                }
            } else {
                OutlinedButton(
                    onClick = vm::openOutputFolder,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("打开文件夹")
                }
                Button(
                    onClick = vm::clearBatch,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("清理结果")
                }
            }
        }
    }
}

@Composable
private fun TaskItemRow(item: QueueItem) {
    val (tint, icon) = when (item.status) {
        QueueStatus.DONE -> MaterialTheme.colorScheme.secondary to Icons.Rounded.CheckCircle
        QueueStatus.FAILED -> MaterialTheme.colorScheme.error to Icons.Rounded.ErrorOutline
        QueueStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant to Icons.Rounded.CheckCircle
        QueueStatus.RUNNING -> MaterialTheme.colorScheme.primary to Icons.Rounded.PlayArrow
        else -> MaterialTheme.colorScheme.onSurfaceVariant to Icons.Rounded.HourglassEmpty
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.relativePath.ifBlank { item.name },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    when (item.status) {
                        QueueStatus.DONE -> buildString {
                            append(if (item.copyOnly) "已复制（跳过超分）" else "已完成")
                            if (item.elapsedMs > 0) append(" · ${item.elapsedMs / 1000.0}s")
                        }
                        QueueStatus.FAILED -> item.error ?: "失败"
                        QueueStatus.SKIPPED -> "已跳过（已处理）"
                        QueueStatus.RUNNING -> "处理中 ${(item.progress * 100).toInt()}%"
                        else -> "等待中"
                    },
                    fontSize = 10.sp,
                    color = if (item.status == QueueStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
