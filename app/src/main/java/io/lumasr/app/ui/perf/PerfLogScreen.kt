package io.lumasr.app.ui.perf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lumasr.app.AppViewModel
import io.lumasr.app.perf.PerfImage
import io.lumasr.app.perf.PerfLog
import io.lumasr.app.perf.PerfRun

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfLogScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var runs by remember { mutableStateOf(PerfLog.recent(context)) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("性能日志")
                        Text(
                            "最近 ${runs.size} 次运行 · 点击展开各阶段耗时",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = vm::backFromPerfLog) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = { runs = PerfLog.recent(context) },
                    ) { Text("刷新") }
                    TextButton(
                        enabled = runs.isNotEmpty(),
                        onClick = {
                            clipboard.setText(
                                AnnotatedString(
                                    runs.joinToString("\n\n") { PerfLog.format(it) },
                                ),
                            )
                        },
                    ) { Text("复制全部") }
                    TextButton(
                        enabled = runs.isNotEmpty(),
                        onClick = {
                            PerfLog.clear(context)
                            runs = emptyList()
                            expandedId = null
                        },
                    ) { Text("清空") }
                }
            }

            if (runs.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Assessment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.padding(4.dp))
                                Text("还没有运行记录", fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "跑一次单图或批量后，这里会记录 prepare / init / 解码 / 推理 / 编码 / 总耗时，方便对比每次改动。",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            items(runs, key = { it.id }) { run ->
                RunCard(
                    run = run,
                    expanded = expandedId == run.id,
                    onToggle = {
                        expandedId = if (expandedId == run.id) null else run.id
                    },
                )
            }
        }
    }
}

@Composable
private fun RunCard(run: PerfRun, expanded: Boolean, onToggle: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                PerfLog.summaryLine(run),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "准备 ${ms(run.prepareMs)} · 初始化 ${ms(run.initMs)} · " +
                    "总耗时 ${ms(run.totalWallMs)} · ${run.format} q${run.quality}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (run.actualBackend.isNotBlank()) {
                Text(
                    "实际后端 ${run.actualBackend}" +
                        (run.fallbackReason?.let { " · 回退: ${it.take(80)}" } ?: ""),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(Modifier.height(8.dp))
                Text(
                    "推理 ${ms(run.doneImages.sumOf { it.inferMs })} · " +
                        "编码 ${ms(run.doneImages.sumOf { it.encodeMs })} · " +
                        "中位 ${ms(run.medianTotalMs)} · P90 ${ms(run.p90TotalMs)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                run.images.take(50).forEach { image ->
                    ImageRow(image)
                }
                if (run.images.size > 50) {
                    Text(
                        "仅显示前 50 张，完整内容用「复制全部」",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                run.note?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    "点击查看每张图的分阶段耗时",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ImageRow(image: PerfImage) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "#${image.index + 1} ${image.name}",
                modifier = Modifier.weight(1f),
                fontSize = 11.sp,
                maxLines = 1,
            )
            Text(
                image.status,
                fontSize = 10.sp,
                color = if (image.status == "DONE") {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        Text(
            "${image.inputWidth}x${image.inputHeight} -> " +
                "${image.outputWidth}x${image.outputHeight}" +
                if (image.downscaled) " · 已降采样" else "",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "解码 ${image.decodeMs}ms · 推理 ${image.inferMs}ms · " +
                "编码 ${image.encodeMs}ms · 等待 ${image.waitMs}ms · " +
                "总计 ${image.totalMs}ms",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        image.error?.let {
            Text("错误: ${it.take(160)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun ms(value: Long): String = when {
    value <= 0 -> "0"
    value < 1000 -> "${value}ms"
    else -> String.format("%.2fs", value / 1000.0)
}
