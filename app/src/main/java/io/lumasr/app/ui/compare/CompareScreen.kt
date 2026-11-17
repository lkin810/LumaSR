package io.lumasr.app.ui.compare

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lumasr.app.AppViewModel
import io.lumasr.app.model.CompareInfo
import io.lumasr.app.model.SaveFormat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val before = vm.originalPreview
    val after = vm.resultPreview
    val info = vm.compareInfo
    val fromBatch = vm.compareFromBatch
    var showSave by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (fromBatch) "对比 ${vm.comparePosition} / ${vm.compareTotal}" else "效果对比",
                        )
                        Text(
                            info?.modelName ?: "加载中…",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = vm::backFromCompare) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!fromBatch) {
                        IconButton(onClick = { showSave = true }) {
                            Icon(Icons.Rounded.Save, contentDescription = "保存")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (vm.compareLoading) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "正在加载对比图…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                return@Column
            }

            if (before == null || after == null || info == null) {
                Text(
                    "结果已释放，请重新处理",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
                return@Column
            }

            CompareView(
                before = before,
                after = after,
                fullOutputWidth = info.outputWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            Spacer(Modifier.height(12.dp))
            InfoCard(info)
            Spacer(Modifier.height(12.dp))

            if (fromBatch) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedButton(
                        onClick = vm::comparePrev,
                        enabled = vm.comparePosition > 1,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("上一张")
                    }
                    OutlinedButton(
                        onClick = vm::compareNext,
                        enabled = vm.comparePosition < vm.compareTotal,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("下一张")
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Button(
                        onClick = vm::backFromCompare,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("返回任务")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = vm::backToConfig,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("重新处理")
                    }
                    Button(
                        onClick = { showSave = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("保存")
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }

    if (showSave && !fromBatch) {
        SaveSheet(
            vm = vm,
            onDismiss = { showSave = false },
            onSaved = {
                Toast.makeText(context, "已保存到相册 Pictures/LumaSR", Toast.LENGTH_SHORT).show()
                showSave = false
            },
            onFailed = {
                Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun InfoCard(info: CompareInfo) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "${info.sourceWidth}×${info.sourceHeight}  →  ${info.outputWidth}×${info.outputHeight}",
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${info.modelName} · ${info.elapsedMs} ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (info.downscaledInput) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "预览按内存预算降采样，实际输出文件尺寸可能更大",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveSheet(
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onFailed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val config = vm.config

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Text("保存到相册", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                "格式",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                Spacer(Modifier.height(14.dp))
                Text(
                    "质量 ${config.quality}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = config.quality.toFloat(),
                    onValueChange = { value -> vm.updateConfig { it.copy(quality = value.toInt()) } },
                    valueRange = 60f..100f,
                )
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (saving) return@Button
                    saving = true
                    scope.launch {
                        val uri = vm.saveResult()
                        saving = false
                        if (uri != null) onSaved() else onFailed()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !saving,
            ) {
                Text(if (saving) "保存中…" else "保存")
            }
        }
    }
}
