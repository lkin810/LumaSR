package io.lumasr.app.ui.benchmark

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.lumasr.app.AppViewModel
import io.lumasr.app.benchmark.BenchmarkBackendMode
import io.lumasr.app.benchmark.BenchmarkResult
import io.lumasr.app.engine.EngineModel
import io.lumasr.app.engine.EngineModels
import io.lumasr.app.engine.ModelGuides
import io.lumasr.app.folder.formatBytes
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkScreen(vm: AppViewModel) {
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> vm.onBenchmarkImagePicked(uri) }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> vm.onBenchmarkOutputPicked(uri) }

    val view = LocalView.current
    DisposableEffect(vm.benchmarkRunning) {
        view.keepScreenOn = vm.benchmarkRunning
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("模型对比测试")
                        Text(
                            "实验室 · 同一张图比较质量与耗时",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = vm::backFromBenchmark) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = { BenchmarkActions(vm) },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SourceCard(
                    vm = vm,
                    onPick = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )
            }
            item {
                OutputCard(vm = vm, onPick = { folderPicker.launch(null) })
            }
            item { BackendCard(vm) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "选择模型",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFC8D3E4),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.openModelGuide(fromBenchmark = true) }) {
                        Text("模型说明")
                    }
                }
            }
            items(EngineModels.all, key = { it.id }) { model ->
                BenchmarkModelRow(vm, model)
            }

            if (vm.benchmarkRunning || vm.benchmarkResults.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "结果 ${vm.benchmarkResults.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFC8D3E4),
                            modifier = Modifier.weight(1f),
                        )
                        if (vm.benchmarkTestFolderUri != null) {
                            TextButton(onClick = vm::openBenchmarkFolder) { Text("打开文件夹") }
                        }
                        if (!vm.benchmarkRunning && vm.benchmarkResults.isNotEmpty()) {
                            TextButton(onClick = vm::clearBenchmarkResults) { Text("清空") }
                        }
                    }
                }
                if (vm.benchmarkRunning) {
                    item { BenchmarkProgressCard(vm) }
                }
                items(vm.benchmarkResults) { result ->
                    BenchmarkResultRow(result)
                }
            }

            item {
                Text(
                    "测试会写入 PNG 到 LumaSR_Test/时间戳/，文件可能较大；NPU 首次会编译模型，请保持 App 在前台。",
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun SourceCard(vm: AppViewModel, onPick: () -> Unit) {
    val uri = vm.benchmarkImageUri
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onPick),
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            if (uri != null) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            } else {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (uri == null) "选择一张测试图片" else vm.benchmarkImageName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (uri == null) {
                        "会分别用选中的模型处理这张图"
                    } else {
                        "点击更换图片 · 所有模型共用这张原图"
                    },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OutputCard(vm: AppViewModel, onPick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    vm.benchmarkOutputName.ifBlank { "未选择输出文件夹" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "输出到 ${vm.benchmarkOutputName.ifBlank { "所选文件夹" }}/LumaSR_Test/时间戳",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onPick) { Text("选择") }
        }
    }
}

@Composable
private fun BackendCard(vm: AppViewModel) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(15.dp)) {
            Text("测试后端", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BenchmarkBackendMode.entries.forEach { mode ->
                    FilterChip(
                        selected = vm.benchmarkBackendMode == mode,
                        onClick = { vm.selectBenchmarkBackendMode(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "两者：支持 NPU 的模型会同时跑 Vulkan 和 NPU，方便直接对比。",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BenchmarkModelRow(vm: AppViewModel, model: EngineModel) {
    val selected = model.id in vm.benchmarkSelectedModels
    val scale = vm.benchmarkScaleFor(model.id)
    val noise = model.noises.lastOrNull() ?: 0
    val npuSupported = EngineModels.npuSupports(
        model.id, scale, noise, EngineModels.STYLE_ANIME,
    )
    val guide = ModelGuides.forModel(model.id)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { vm.toggleBenchmarkModel(model.id) },
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { vm.toggleBenchmarkModel(model.id) },
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        model.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${model.category.label} · ${guide?.tagline ?: model.description}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    if (npuSupported) "NPU ${scale}x" else "仅 Vulkan",
                    fontSize = 9.sp,
                    color = if (npuSupported) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (selected) {
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "倍数",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    model.scales.forEach { item ->
                        FilterChip(
                            selected = scale == item,
                            onClick = { vm.setBenchmarkScale(model.id, item) },
                            label = { Text("${item}x") },
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Text(
                    guide?.bestFor ?: model.description,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BenchmarkProgressCard(vm: AppViewModel) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    vm.benchmarkCurrent.ifBlank { "测试中…" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${(vm.benchmarkProgress * 100).toInt()}%",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { vm.benchmarkProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
private fun BenchmarkResultRow(result: BenchmarkResult) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${result.modelName} · ${result.scale}x · ${result.backendLabel}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (result.error == null) {
                    Text(
                        "实际 ${result.actualBackendLabel}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            if (result.error != null) {
                Text(
                    result.error,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    "准备 ${formatMs(result.prepareMs)} · 初始化 ${formatMs(result.initMs)} · " +
                        "推理 ${formatMs(result.inferMs)} · 总 ${formatMs(result.totalMs)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append("输出 ${result.outputWidth}×${result.outputHeight}")
                        append(" · ${String.format(Locale.US, "%.1f", result.outputMp)} MP")
                        append(" · ${formatBytes(result.outputBytes)}")
                        if (result.downscaled) append(" · 输入已降采样")
                    },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BenchmarkActions(vm: AppViewModel) {
    val count = vm.benchmarkPlannedCount()
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 11.dp),
        ) {
            Text(
                when {
                    vm.benchmarkRunning -> vm.benchmarkCurrent.ifBlank { "测试中…" }
                    vm.benchmarkImageUri == null -> "先选择一张测试图片"
                    vm.benchmarkOutputTree == null -> "选择一个输出文件夹"
                    count == 0 -> "至少选择一个模型"
                    else -> "计划运行 $count 个模型/后端组合"
                },
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            if (vm.benchmarkRunning) {
                OutlinedButton(
                    onClick = vm::cancelBenchmark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Text("取消测试")
                }
            } else {
                Button(
                    onClick = vm::startBenchmark,
                    enabled = vm.benchmarkImageUri != null &&
                        vm.benchmarkOutputTree != null &&
                        count > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Text("开始测试", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String =
    if (ms <= 0) "-" else String.format(Locale.US, "%.1fs", ms / 1000.0)
