package io.lumasr.app.ui.config

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import eu.kanade.tachiyomi.util.npu.NpuModelStatus
import eu.kanade.tachiyomi.util.qnn.QnnModelContexts
import io.lumasr.app.AppViewModel
import io.lumasr.app.engine.Engine97
import io.lumasr.app.engine.EngineModel
import io.lumasr.app.engine.EngineModels
import io.lumasr.app.engine.ModelCategory
import io.lumasr.app.model.SaveFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleConfigScreen(
    vm: AppViewModel,
    uris: List<Uri>,
    batch: Boolean,
) {
    val context = LocalContext.current
    val config = vm.config
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
    val npuStates by Engine97.npuStates.collectAsState()
    val qnnName = Engine97.qnnModelName(config.modelId, config.scale, config.noise, config.style)
    val npuPrecision = if (config.backend == Engine97.BACKEND_QUALCOMM_NPU) {
        config.precision
    } else {
        Engine97.PRECISION_FP16
    }
    val npuState = qnnName?.let { npuStates[Engine97.npuStateKey(it, npuPrecision)] }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.start() }

    val onStart = {
        if (batch &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.start()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (batch) "批量超分配置" else "单张超分配置") },
                navigationIcon = {
                    IconButton(onClick = vm::backHome) {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = uris.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            if (batch) "开始处理 ${uris.size} 张图片" else "开始超分",
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
                .padding(horizontal = 20.dp),
        ) {
            Text(
                if (batch) "已选择 ${uris.size} 张图片，使用同一套参数" else "1 张图片待处理",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(18.dp))
            SectionTitle("模型")
            Spacer(Modifier.height(10.dp))

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

            Spacer(Modifier.height(10.dp))
            EngineModels.forCategory(category).forEach { model ->
                ModelCard(
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
                Spacer(Modifier.height(8.dp))
            }

            if (config.model.hasStyle) {
                Spacer(Modifier.height(12.dp))
                SectionTitle("风格")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(EngineModels.STYLE_ANIME, EngineModels.STYLE_PHOTO).forEach { style ->
                        FilterChip(
                            selected = config.style == style,
                            onClick = {
                                vm.updateConfig { it.copy(style = style).sanitized() }
                            },
                            label = { Text(EngineModels.styleLabel(style)) },
                        )
                    }
                }
            }

            if (config.model.scales.size > 1 && !(config.modelId == EngineModels.MODEL_ESRGAN &&
                    config.style == EngineModels.STYLE_PHOTO)
            ) {
                Spacer(Modifier.height(12.dp))
                SectionTitle("倍数")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    config.model.scales.forEach { scale ->
                        FilterChip(
                            selected = config.scale == scale,
                            onClick = {
                                vm.updateConfig { it.copy(scale = scale).sanitized() }
                            },
                            label = { Text("${scale}x") },
                        )
                    }
                }
            }

            if (config.model.noises.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SectionTitle("降噪")
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    config.model.noises.forEach { noise ->
                        FilterChip(
                            selected = config.noise == noise,
                            onClick = {
                                vm.updateConfig { it.copy(noise = noise).sanitized() }
                            },
                            label = { Text(EngineModels.noiseLabel(noise)) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("处理后端")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = config.backend == Engine97.BACKEND_VULKAN,
                    onClick = {
                        vm.updateConfig { it.copy(backend = Engine97.BACKEND_VULKAN) }
                    },
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
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    config.backendIssue(npuAvailable) != null -> config.backendIssue(npuAvailable)!!
                    config.backend == Engine97.BACKEND_QUALCOMM_NPU ->
                        "HTP ${npuArch?.let { "v$it" } ?: "未知"} · FP16 ${if (npuFp16 == false) "不支持" else "可用"}"
                    else ->
                        "NPU 模型支持 2x（Pro 支持 3x），首次使用会在设备上编译"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (npuAvailable) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
            )

            if (config.backend == Engine97.BACKEND_QUALCOMM_NPU) {
                Spacer(Modifier.height(8.dp))
                Text(
                    when (npuState) {
                        is NpuModelStatus.Preparing -> "NPU 模型编译中，首次使用可能需要几十秒…"
                        is NpuModelStatus.Ready -> "NPU 模型已就绪"
                        is NpuModelStatus.Failed -> "NPU 模型准备失败：${npuState.message}"
                        null -> "开始处理时会准备 NPU 模型（按设备 HTP 编译并缓存）"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (npuState is NpuModelStatus.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("精度")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (config.backend == Engine97.BACKEND_QUALCOMM_NPU) {
                    FilterChip(
                        selected = config.precision == Engine97.PRECISION_FP16,
                        onClick = { vm.updateConfig { it.copy(precision = Engine97.PRECISION_FP16) } },
                        label = { Text("FP16") },
                    )
                    val int8Supported = qnnName?.let { QnnModelContexts.supportsW8a8(it) } == true
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

            Spacer(Modifier.height(18.dp))
            SectionTitle("输出上限")
            Spacer(Modifier.height(4.dp))
            Text(
                "超过上限时会先降低输入分辨率，避免内存溢出",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(12, 24, 48).forEach { mp ->
                    FilterChip(
                        selected = config.maxOutputMp == mp,
                        onClick = { vm.updateConfig { it.copy(maxOutputMp = mp) } },
                        label = { Text("$mp MP") },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("保存格式")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SaveFormat.entries.forEach { format ->
                    FilterChip(
                        selected = config.format == format,
                        onClick = { vm.updateConfig { it.copy(format = format) } },
                        label = { Text(format.label) },
                    )
                }
            }

            if (config.format.needsQuality) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "质量 ${config.quality}",
                    style = MaterialTheme.typography.bodySmall,
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

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ModelCard(
    model: EngineModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(model.name, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        model.scales.joinToString("/") { "${it}x" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
