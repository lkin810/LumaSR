package io.lumasr.app.ui.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import eu.kanade.tachiyomi.util.npu.NpuModelStatus
import io.lumasr.app.AppViewModel
import io.lumasr.app.engine.Engine97
import io.lumasr.app.engine.EngineModels
import io.lumasr.app.model.SaveFormat
import io.lumasr.app.model.UpscaleConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val config = vm.config
    val npuPage by produceState<Long?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { Engine97.pageSizeBytes() }
    }
    val qnnRuntime by produceState(initialValue = false) {
        value = withContext(Dispatchers.IO) { Engine97.qnnRuntimeAvailable() }
    }
    val npuStates by Engine97.npuStates.collectAsState()
    val lastEngineError by Engine97.lastError.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> if (uri != null) vm.setOutputTree(uri) }
    val hasExportTree = config.outputTreeUri.isNotBlank()
    val npuError = npuStates.values
        .filterIsInstance<NpuModelStatus.Failed>()
        .lastOrNull()
        ?.message

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("设置")
                        Text(
                            "处理、输出与设备",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column {
                    SectionLabel("处理")
                    SettingsCard {
                        SettingsRow(
                            icon = Icons.Rounded.Bolt,
                            title = "默认后端",
                            subtitle = config.backendIssue(vm.npuAvailable)
                                ?: "Qualcomm NPU 需要 Snapdragon + QNN",
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = config.backend == Engine97.BACKEND_VULKAN,
                                    onClick = {
                                        vm.updateConfig {
                                            it.copy(backend = Engine97.BACKEND_VULKAN).sanitized()
                                        }
                                    },
                                    label = { Text("Vulkan") },
                                )
                                FilterChip(
                                    selected = config.backend == Engine97.BACKEND_QUALCOMM_NPU,
                                    onClick = {
                                        vm.updateConfig {
                                            it.copy(backend = Engine97.BACKEND_QUALCOMM_NPU).sanitized()
                                        }
                                    },
                                    enabled = vm.npuAvailable && EngineModels.npuSupports(
                                        config.modelId,
                                        config.scale,
                                        config.noise,
                                        config.style,
                                    ),
                                    label = { Text("NPU") },
                                )
                            }
                        }
                        SettingsRow(
                            icon = Icons.Rounded.AutoAwesome,
                            title = "默认模型",
                            subtitle = "${config.model.name} · ${config.outputScale}x · ${
                                if (config.model.noises.isNotEmpty()) {
                                    EngineModels.noiseLabel(config.noise)
                                } else {
                                    "无降噪"
                                }
                            }",
                            onClick = vm::openModels,
                        ) {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        SettingsRow(
                            icon = Icons.Rounded.Shield,
                            title = "精度",
                            subtitle = if (config.backend == Engine97.BACKEND_QUALCOMM_NPU) {
                                "FP16 稳定，Anime 模型可选 INT8"
                            } else {
                                "Vulkan 推理精度"
                            },
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (config.backend == Engine97.BACKEND_QUALCOMM_NPU) {
                                    FilterChip(
                                        selected = config.precision == Engine97.PRECISION_FP16,
                                        onClick = {
                                            vm.updateConfig { it.copy(precision = Engine97.PRECISION_FP16) }
                                        },
                                        label = { Text("FP16") },
                                    )
                                    FilterChip(
                                        selected = config.precision == Engine97.PRECISION_INT8,
                                        onClick = {
                                            vm.updateConfig { it.copy(precision = Engine97.PRECISION_INT8) }
                                        },
                                        label = { Text("INT8") },
                                    )
                                } else {
                                    listOf(0 to "FP16", 1 to "FP32", 3 to "BF16").forEach { (value, label) ->
                                        FilterChip(
                                            selected = config.precision == value,
                                            onClick = { vm.updateConfig { it.copy(precision = value) } },
                                            label = { Text(label) },
                                        )
                                    }
                                }
                            }
                        }
                        SettingsRow(
                            icon = Icons.Rounded.Bolt,
                            title = "处理性能",
                            subtitle = "tile 越大越快，显存占用也更高",
                            showDivider = false,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                UpscaleConfig.TILE_SIZES.forEach { size ->
                                    FilterChip(
                                        selected = config.tileSize == size,
                                        onClick = {
                                            vm.updateConfig { it.copy(tileSize = size).sanitized() }
                                        },
                                        label = { Text("$size") },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column {
                    SectionLabel("输出")
                    SettingsCard {
                        SettingsRow(
                            icon = Icons.Rounded.Folder,
                            title = "导出文件夹",
                            subtitle = if (hasExportTree) {
                                "${
                                    config.outputTreeName.ifBlank { "已指定目录" }
                                } · 按源文件夹名建子目录"
                            } else {
                                "源文件夹 / ${config.outputFolderName}"
                            },
                            onClick = { exportPicker.launch(null) },
                        ) {
                            TextButton(
                                onClick = {
                                    if (hasExportTree) {
                                        vm.clearOutputTree()
                                    } else {
                                        exportPicker.launch(null)
                                    }
                                },
                            ) {
                                Text(if (hasExportTree) "恢复默认" else "选择")
                            }
                        }
                        SettingsRow(
                            icon = Icons.Rounded.Image,
                            title = "保留子文件夹结构",
                            subtitle = "子目录按原结构写入输出文件夹",
                        ) {
                            Switch(
                                checked = config.preserveStructure,
                                onCheckedChange = { value ->
                                    vm.updateConfig { it.copy(preserveStructure = value) }
                                },
                            )
                        }
                        SettingsRow(
                            icon = Icons.Rounded.Image,
                            title = "保存格式",
                            subtitle = "批量输出文件格式",
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SaveFormat.entries.forEach { format ->
                                    FilterChip(
                                        selected = config.format == format,
                                        onClick = { vm.updateConfig { it.copy(format = format) } },
                                        label = { Text(format.label) },
                                    )
                                }
                            }
                        }
                        SettingsRow(
                            icon = Icons.Rounded.Shield,
                            title = "最大输出",
                            subtitle = "超过上限时先降低输入分辨率",
                            showDivider = config.format.needsQuality,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(12, 24, 48).forEach { mp ->
                                    FilterChip(
                                        selected = config.maxOutputMp == mp,
                                        onClick = { vm.updateConfig { it.copy(maxOutputMp = mp) } },
                                        label = { Text("$mp") },
                                    )
                                }
                            }
                        }
                        if (config.format.needsQuality) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(
                                    "输出质量 ${config.quality}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Slider(
                                    value = config.quality.toFloat(),
                                    onValueChange = { value ->
                                        vm.updateConfig { it.copy(quality = value.toInt()) }
                                    },
                                    valueRange = 60f..100f,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column {
                    SectionLabel("设备")
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Memory,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(vm.deviceLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    buildString {
                                        append("Vulkan ")
                                        append(if (vm.vulkanAvailable) "可用" else "不可用")
                                        if (vm.npuAvailable) {
                                            append(" · HTP ")
                                            append(vm.npuArchitecture?.let { "v$it" } ?: "未知")
                                            append(" · FP16 ")
                                            append(if (vm.npuFp16 == false) "不支持" else "可用")
                                        } else {
                                            append(" · 未检测到 NPU")
                                        }
                                        append(" · QNN ")
                                        append(if (qnnRuntime) "可用" else "不可用")
                                        npuPage?.let { append(" · ${it / 1024}KB 页") }
                                    },
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val npuWarning = npuError
                                    ?: if (npuPage == 16384L) {
                                        "系统使用 16KB 页，QNN skel 需要 16KB 对齐"
                                    } else {
                                        null
                                    }
                                if (!npuWarning.isNullOrBlank()) {
                                    Text(
                                        npuWarning,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(top = 3.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column {
                    SectionLabel("NPU 诊断")
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(15.dp)) {
                            Text(
                                "选择 NPU 后如果失败，先运行自检，再复制报告反馈。自检会实际加载模型并跑一张 64×64，首次约几十秒。",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (vm.npuDiagnosticReport.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                SelectionContainer {
                                    Text(
                                        vm.npuDiagnosticReport,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        lineHeight = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            } else if (!lastEngineError.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "最近错误：$lastEngineError",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = vm::runNpuDiagnostics,
                                    enabled = !vm.npuDiagnosticRunning,
                                ) {
                                    if (vm.npuDiagnosticRunning) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(if (vm.npuDiagnosticRunning) "诊断中" else "运行 NPU 自检")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val report = vm.npuDiagnosticReport
                                            .ifBlank { lastEngineError ?: "暂无诊断信息" }
                                        clipboard.setText(AnnotatedString(report))
                                        Toast.makeText(context, "诊断报告已复制", Toast.LENGTH_SHORT).show()
                                    },
                                ) {
                                    Text("复制报告")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column {
                    SectionLabel("实验室")
                    SettingsCard {
                        SettingsRow(
                            icon = Icons.Rounded.Bolt,
                            title = "模型对比测试",
                            subtitle = "同一张图跑多个模型，比较质量与耗时",
                            onClick = vm::openBenchmark,
                            trailing = {},
                        )
                        SettingsRow(
                            icon = Icons.Rounded.AutoAwesome,
                            title = "模型说明",
                            subtitle = "不同模型的区别、适用场景与 NPU 支持",
                            onClick = { vm.openModelGuide() },
                            trailing = {},
                        )
                        SettingsRow(
                            icon = Icons.Rounded.Assessment,
                            title = "性能日志",
                            subtitle = "最近运行的各阶段耗时，可复制后反馈",
                            onClick = vm::openPerfLog,
                            showDivider = false,
                            trailing = {},
                        )
                    }
                }
            }

            item {
                Column {
                    SectionLabel("模型")
                    SettingsCard {
                        SettingsRow(
                            icon = Icons.Rounded.AutoAwesome,
                            title = "模型管理",
                            subtitle = "9 个家族 · NPU 模型按设备编译缓存",
                            onClick = vm::openModels,
                            showDivider = false,
                        ) {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            item {
                Column {
                    SectionLabel("其他")
                    SettingsCard {
                        SettingsRow(
                            icon = Icons.Rounded.Notifications,
                            title = "完成通知",
                            subtitle = "任务结束后发送通知",
                        ) {
                            Switch(
                                checked = config.notifications,
                                onCheckedChange = { value ->
                                    vm.updateConfig { it.copy(notifications = value) }
                                },
                            )
                        }
                        SettingsRow(
                            icon = Icons.Rounded.Shield,
                            title = "隐私",
                            subtitle = "图片只在本机处理，不会上传",
                            showDivider = false,
                        ) {
                            Text(
                                "本地",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "LumaSR 0.5.0 · Mihon Upscale 97 引擎 · NCNN Vulkan / Qualcomm QNN",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Column(
        Modifier
            .then(
                if (onClick != null) Modifier.clip(RoundedCornerShape(0.dp)).clickable(onClick = onClick)
                else Modifier,
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    subtitle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}
