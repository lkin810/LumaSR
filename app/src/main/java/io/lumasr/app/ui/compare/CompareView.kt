package io.lumasr.app.ui.compare

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CompareView(
    before: ImageBitmap,
    after: ImageBitmap,
    fullOutputWidth: Int,
    modifier: Modifier = Modifier,
) {
    var fraction by remember { mutableFloatStateOf(0.5f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showOriginal by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF05070A)),
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val handlePx = with(LocalDensity.current) { 44.dp.toPx() }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        val newZoom = (zoom * gestureZoom).coerceIn(1f, 12f)
                        zoom = newZoom
                        offset = if (newZoom <= 1.001f) {
                            Offset.Zero
                        } else {
                            Offset(
                                (offset.x + pan.x).coerceIn(-widthPx, widthPx),
                                (offset.y + pan.y).coerceIn(-heightPx, heightPx),
                            )
                        }
                    }
                },
        ) {
            val imgW = after.width.toFloat()
            val imgH = after.height.toFloat()
            val base = min(size.width / imgW, size.height / imgH)
            val drawW = imgW * base * zoom
            val drawH = imgH * base * zoom
            val left = (size.width - drawW) / 2f + offset.x
            val top = (size.height - drawH) / 2f + offset.y
            val dstOffset = IntOffset(left.roundToInt(), top.roundToInt())
            val dstSize = IntSize(
                drawW.roundToInt().coerceAtLeast(1),
                drawH.roundToInt().coerceAtLeast(1),
            )

            if (showOriginal) {
                drawImage(
                    image = before,
                    dstOffset = dstOffset,
                    dstSize = dstSize,
                    filterQuality = FilterQuality.Medium,
                )
            } else {
                drawImage(
                    image = after,
                    dstOffset = dstOffset,
                    dstSize = dstSize,
                    filterQuality = FilterQuality.Medium,
                )
                clipRect(right = size.width * fraction) {
                    drawImage(
                        image = before,
                        dstOffset = dstOffset,
                        dstSize = dstSize,
                        filterQuality = FilterQuality.Medium,
                    )
                }

                val x = size.width * fraction
                drawLine(
                    color = Color.White.copy(alpha = 0.92f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
                drawCircle(
                    color = Color.White,
                    radius = 13.dp.toPx(),
                    center = Offset(x, size.height / 2f),
                    style = Fill,
                )
                drawCircle(
                    color = Color(0xFF7AA2FF),
                    radius = 6.dp.toPx(),
                    center = Offset(x, size.height / 2f),
                )
            }
        }

        Box(
            Modifier
                .offset { IntOffset((widthPx * fraction - handlePx / 2f).roundToInt(), 0) }
                .width(44.dp)
                .fillMaxHeight()
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        fraction = (fraction + dragAmount / widthPx).coerceIn(0f, 1f)
                    }
                },
        )

        if (!showOriginal) {
            Tag(text = "原图", modifier = Modifier.align(Alignment.TopStart))
            Tag(text = "超分", modifier = Modifier.align(Alignment.TopEnd))
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ZoomChip("适应") {
                zoom = 1f
                offset = Offset.Zero
            }
            ZoomChip("2x") {
                zoom = 2f
            }
            ZoomChip("1:1") {
                zoom = (fullOutputWidth.toFloat() / after.width.toFloat()).coerceIn(1f, 12f)
                offset = Offset.Zero
            }
            ZoomChip(if (showOriginal) "对比" else "看原图") {
                showOriginal = !showOriginal
            }
        }
    }
}

@Composable
private fun Tag(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.55f),
        modifier = modifier.padding(10.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ZoomChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.6f),
        onClick = onClick,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}
