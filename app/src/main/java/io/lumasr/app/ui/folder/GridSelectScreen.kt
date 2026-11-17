package io.lumasr.app.ui.folder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.lumasr.app.AppViewModel
import io.lumasr.app.folder.FolderImage
import io.lumasr.app.folder.GridFilter
import io.lumasr.app.folder.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridSelectScreen(vm: AppViewModel) {
    val snapshot = vm.snapshot
    val visible = vm.visibleImages
    val selected = vm.selectedPaths

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("选择图片")
                        Text(
                            "${snapshot?.folderName.orEmpty()} · 已选 ${selected.size} / ${snapshot?.images?.size ?: 0}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = vm::backToOverview) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = vm::toggleSort) {
                        Icon(
                            Icons.Rounded.SwapVert,
                            contentDescription = "排序",
                            tint = if (vm.gridSortDescending) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
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
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AssistChip(
                            onClick = vm::toggleSelectAllVisible,
                            label = { Text(if (vm.allVisibleSelected) "取消全选" else "全选") },
                        )
                        AssistChip(
                            onClick = vm::invertVisible,
                            label = { Text("反选") },
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${selected.size} 张 · ${formatBytes(vm.selectedBytes)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    Button(
                        onClick = vm::enterBatchConfig,
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
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
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                GridFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = vm.gridFilter == filter,
                        onClick = { vm.selectGridFilter(filter) },
                        label = { Text("${filter.label} ${vm.countFor(filter)}") },
                    )
                }
            }

            if (visible.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "当前筛选下没有图片",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(visible, key = { it.relativePath }) { image ->
                    ThumbCell(
                        image = image,
                        processed = vm.isProcessed(image),
                        selected = image.relativePath in selected,
                        onClick = { vm.toggleImage(image) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThumbCell(
    image: FolderImage,
    processed: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.White.copy(alpha = 0.07f)
    }
    Box(
        Modifier
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = image.documentUri,
            contentDescription = image.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (processed) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp),
            ) {
                Text(
                    "已处理",
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f),
                )
                .border(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.55f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(13.dp),
                )
            }
        }

        Surface(
            color = Color.Black.copy(alpha = 0.45f),
            shape = RoundedCornerShape(5.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(5.dp),
        ) {
            Text(
                image.name,
                fontSize = 8.sp,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}
