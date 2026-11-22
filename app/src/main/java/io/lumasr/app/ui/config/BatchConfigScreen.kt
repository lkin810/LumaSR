package io.lumasr.app.ui.config

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.util.qnn.QnnModelContexts
import io.lumasr.app.AppViewModel
import io.lumasr.app.engine.Engine97
import io.lumasr.app.engine.EngineModel
import io.lumasr.app.engine.EngineModels
import io.lumasr.app.engine.ModelCategory
import io.lumasr.app.folder.formatBytes
import io.lumasr.app.model.SaveFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchConfigScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val config = vm.config
    val selected = vm.selectedImages
    var category by remember { mutableStateOf(config.model.category) }

    val npuAvailable by produceState(false) {
        value = withContext(Dispatchers.IO) { Engine97.isQualcommNpuAvailable() }
    }
    val npuArch by produceState<Int?>(null) {
        value = withContext(Dispatchers.IO) { Engine97.htpArchitecture(context) }
    }
    val npuFp16 by produceState<Boolean?>(null) {
        value = withContext(Dispatchers.IO) { Engine97.detectFp16Support(context) }
    }
    val qnnName = Engine97.qnnModelName(config.modelId, config.scale, config.noise, config.style)
    val int8Supported = qnnName?.let { QnnModelContexts.supportsW8a8(it) } == true

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.startBatch() }

    val onStart = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.startBatch()
        }
    }

    val skipped = if (config.skipProcessed) selected.count { vm.isProcessed(it) } else 0
    val toProcess = selected.size - skipped

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("批处理参数")
                        Text(
                            "应用到 ${selected.size} 张图片",
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
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                ) {
                    Button(
                        onClick = onStart,
                        enabled = selected.isNotEmpty(),
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
                            if (skipped > 0) "开始处理 $toProcess 张（跳过 $skipped）" else "开始处理 ${selected.size} 张",
                            fontWeight = FontWeight.SemiBold,
                        )
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModelCategory.entries.forEach { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = {
                            category = item
                            val first = EngineModels.forCategory(item).first()
                            vm.updateConfig {
                                it.copy(
                                    modelId = first.id,
                                    scale = first.scales.first(),
                                    noise = first.noises.lastOrNull() ?: it.noise,
                                    style = if (first.hasStyle) it.style else EngineModels.STYLE_ANIME,
                                ).sanitized()
                            }
                        },
                        label = { Text(item.label) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            EngineModels.forCategory(category).forEach { model ->
                ModelRow(
                    model = model,
                    selected = config.modelId == model.id,
                    onClick = {
                        vm.updateConfig {
                            it.copy(
                                modelId = model.id,
                                scale = if (it.scale in model.scales) it.scale else model.scales.first(),
                                noise = if (model.noises.isEmpty() || it.noise in model.noises) {
                                    it.noise
                                } else {
                                    model.noises.last()
                                },
                                style = if (model.hasStyle) it.style else EngineModels.STYLE_ANIME,
                            ).sanitized()
                        }
                    },
                )
                Spacer(Modifier.height(7.dp))
            }

            if (config.model.hasStyle) {
                Spacer(Modifier.height(8.dp))
                Section("风格")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(EngineModels.STYLE_ANIME, EngineModels.STYLE_PHOTO).forEach { style ->
                        FilterChip(
                            selected = config.style == style,
                            onClick = { vm.updateConfig { it.copy(style = style).sanitized() } },
                            label = { Text(EngineModels.styleLabel(style)) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Section("倍数与降噪")
            if (config.model.scales.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    config.model.scales.forEach { scale ->
                        FilterChip(
                            selected = config.scale == scale,
                            onClick = { vm.updateConfig { it.copy(scale = scale).sanitized() } },
                            label = { Text("${scale}x") },
                        )
                    }
                }
            } else {
                Text(
                    "当前模型固定 ${config.outputScale}x",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (config.model.noises.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    config.model.noises.forEach { noise ->
                        FilterChip(
                            selected = config.noise == noise,
                            onClick = { vm.updateConfig { it.copy(noise = noise).sanitized() } },
                            label = { Text(EngineModels.noiseLabel(noise)) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Section("处理后端")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = config.backend == Engine97.BACKEND_VULKAN,
                    onClick = { vm.updateConfig { it.copy(backend = Engine97.BACKEND_VULKAN).sanitized() } },
                    label = { Text("Vulkan GPU") },
                )
                FilterChip(
                    selected = config.backend == Engine97.BACKEND_QUALCOMM_NPU,
                    onClick = {
                        vm.updateConfig {
                            it.copy(backend = Engine97.BACKEND_QUALCOMM_NPU).sanitized()
                        }
                    },
                    enabled = npuAvailable && EngineModels.npuSupports(
                        config.modelId,
                        config.scale,
                        config.noise,
                        config.style,
                    ),
                    label = { Text("Qualcomm NPU") },
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                when {
                    config.backendIssue(npuAvailable) != null -> config.backendIssue(npuAvailable)!!
                    config.backend == Engine97.BACKEND_QUALCOMM_NPU ->
                        "HTP ${npuArch?.let { "v$it" } ?: "未知"} · FP16 ${if (npuFp16 == false) "不支持" else "可用"} · 首次编译需几十秒"
                    else -> "NPU 支持 2x（CUGAN Pro 支持 3x），模型不支持时请用 Vulkan"
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (config.backend == Engine97.BACKEND_QUALCOMM_NPU) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = config.precision == Engine97.PRECISION_FP16,
                        onClick = { vm.updateConfig { it.copy(precision = Engine97.PRECISION_FP16) } },
                        label = { Text("FP16") },
                    )
                    FilterChip(
                        selected = config.precision == Engine97.PRECISION_INT8,
                        onClick = {
                            if (int8Supported) {
                                vm.updateConfig { it.copy(precision = Engine97.PRECISION_INT8) }
                            }
                        },
                        enabled = int8Supported,
                        label = { Text("INT8") },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Section("跳过超分")
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "长边达到所选值时，不放大，直接复制原图",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            0 to "关闭",
                            1920 to "1080p",
                            2560 to "2K",
                            3840 to "4K",
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = config.skipLongEdge == value,
                                onClick = { vm.setSkipLongEdge(value) },
                                label = { Text(label) },
                            )
                        }
                    }
                    if (config.skipLongEdge > 0) {
                        val copyCount = selected.count {
                            !vm.isProcessed(it) && vm.willCopy(it)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "长边 ≥ ${config.skipLongEdge} 时直接复制 · 本批预计 $copyCount 张",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Section("输出")
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("导出文件夹", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (config.outputTreeUri.isNotBlank()) {
                                    "${
                                        config.outputTreeName.ifBlank { "导出目录" }
                                    }/${vm.snapshot?.folderName.orEmpty()}"
                                } else {
                                    "${vm.snapshot?.folderName.orEmpty()}/${config.outputFolderName}"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ToggleRow(
                        title = "保留子文件夹结构",
                        subtitle = "子目录按原结构写入输出文件夹",
                        checked = config.preserveStructure,
                        onCheckedChange = vm::setPreserveStructure,
                    )
                    ToggleRow(
                        title = "跳过已处理图片",
                        subtitle = "输出文件夹已有同名文件时不重复处理",
                        checked = config.skipProcessed,
                        onCheckedChange = vm::setSkipProcessed,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "格式",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SaveFormat.entries.forEach { format ->
                            FilterChip(
                                selected = config.format == format,
                                onClick = {
                                    if (config.format != format) {
                                        vm.updateConfig { it.copy(format = format) }
                                    }
                                },
                                label = { Text(format.label) },
                            )
                        }
                    }
                    if (config.format.needsQuality) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "质量 ${config.quality}",
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
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "最大输出",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(12, 24, 48).forEach { mp ->
                            FilterChip(
                                selected = config.maxOutputMp == mp,
                                onClick = { vm.updateConfig { it.copy(maxOutputMp = mp) } },
                                label = { Text("$mp MP") },
                            )
                        }
                    }
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
                    buildString {
                        append("${config.model.name} · ${config.outputScale}x · ${config.backendLabel}")
                        append("\n已选 ${selected.size} 张 · ${formatBytes(vm.selectedBytes)}")
                        if (skipped > 0) append("\n其中 $skipped 张已处理，将跳过")
                    },
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
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

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp)
            Text(
                subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ModelRow(model: EngineModel, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(model.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        model.scales.joinToString("/") { "${it}x" },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    model.description,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
