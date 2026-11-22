package io.lumasr.app.ui.preprocess

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lumasr.app.AppViewModel
import io.lumasr.app.folder.PreprocessReport
import io.lumasr.app.folder.ResolutionBucket
import io.lumasr.app.folder.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreprocessScreen(vm: AppViewModel) {
    val selected = vm.selectedImages
    val report = vm.preprocessReport
    val analyzing = vm.preprocessAnalyzing
    val threshold = vm.config.skipLongEdge

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("预处理")
                        Text(
                            "已选 ${selected.size} 张 · ${formatBytes(vm.selectedBytes)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = vm::backToGrid) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (report == null) {
                        Button(
                            onClick = vm::analyzeSelection,
                            enabled = !analyzing && selected.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text(
                                if (analyzing) {
                                    "正在分析 ${vm.preprocessDone}/${selected.size}"
                                } else {
                                    "分析项目"
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = vm::analyzeSelection,
                            enabled = !analyzing,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text("重新分析")
                        }
                        Button(
                            onClick = vm::enterBatchConfig,
                            modifier = Modifier
                                .weight(1.4f)
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text("下一步：设置参数", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Rounded.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "先分析，再开始",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "读取每张图片的分辨率与清晰度，确认哪些直接复制、哪些需要超分。",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                if (threshold > 0) {
                    "当前规则：长边 ≥ $threshold 的图片直接复制，其余超分"
                } else {
                    "当前未开启跳过超分，所选图片都会超分"
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(14.dp))
            when {
                analyzing -> AnalysisProgress(
                    done = vm.preprocessDone,
                    total = selected.size,
                    progress = vm.preprocessProgress,
                )

                report != null -> ReportContent(report)

                else -> HintContent()
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AnalysisProgress(done: Int, total: Int, progress: Float) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("正在分析…", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text(
                    "$done/$total",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(9.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "图片较多时需要一些时间，可随时返回",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HintContent() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            HintRow("分辨率", "长边达到跳过阈值的图片标记为直接复制")
            Spacer(Modifier.height(10.dp))
            HintRow("清晰度", "采样估算，提示偏模糊的图片")
            Spacer(Modifier.height(10.dp))
            HintRow("输出方式", "复制保持原格式，超分按下一步设置的格式输出")
        }
    }
}

@Composable
private fun HintRow(title: String, subtitle: String) {
    Row {
        Text(
            title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(62.dp),
        )
        Text(
            subtitle,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReportContent(report: PreprocessReport) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(
            value = report.copyCount,
            label = "直接复制",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = report.upscaleCount,
            label = "需要超分",
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f),
        )
    }
    if (report.skipCount > 0) {
        Spacer(Modifier.height(8.dp))
        Text(
            "另有 ${report.skipCount} 张已处理，开始时将跳过",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(Modifier.height(16.dp))
    Section("分辨率分布")
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            ResolutionBucket.entries.forEachIndexed { index, bucket ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                DistributionRow(
                    label = bucket.label,
                    count = report.countFor(bucket),
                    total = report.items.size,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Section("清晰度参考")
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "仅统计需要超分的 ${report.upscaleCount} 张",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            listOf("清晰", "一般", "偏模糊").forEachIndexed { index, label ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                DistributionRow(
                    label = label,
                    count = report.sharpnessCount(label),
                    total = report.upscaleCount,
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            val unknown = report.upscaleCount - report.sharpnessMeasured
            if (unknown > 0) {
                Spacer(Modifier.height(10.dp))
                DistributionRow(
                    label = "未检测",
                    count = unknown,
                    total = report.upscaleCount,
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                "清晰度为 512 px 采样估算，扁平色块与强噪点会影响结果，仅供参考。",
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            if (report.skipLongEdge > 0) {
                "本次分析按长边 ≥ ${report.skipLongEdge} 统计，可在下一步修改阈值后重新分析。"
            } else {
                "本次分析未启用跳过超分，所有未处理图片都会超分。"
            },
            fontSize = 11.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun StatCard(
    value: Int,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = tint.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.30f)),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Text(
                "$value",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
            Text(
                label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DistributionRow(label: String, count: Int, total: Int, tint: Color) {
    val fraction = if (total > 0) count.toFloat() / total else 0f
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "$count 张",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (count > 0) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(tint.copy(alpha = 0.8f)),
                )
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 6.dp, top = 2.dp),
    )
}
