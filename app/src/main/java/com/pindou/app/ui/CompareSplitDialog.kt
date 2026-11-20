package com.pindou.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.createBitmap
import com.pindou.app.core.export.ComparisonLayout
import com.pindou.app.core.export.ExportRenderer
import com.pindou.app.core.export.SplitCompareMath
import com.pindou.app.core.pattern.PatternResult
import com.pindou.app.ui.icon.MorphIcons
import com.pindou.app.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * 可拖分割线的前后对比：左原图、右图纸，手柄拖动即换分界。
 *
 * 与导出图共用 [ComparisonLayout.fit]，保证屏幕上看的位置和导出的版式一致；
 * 图纸位图只在打开时渲染一次（[ExportRenderer.renderPattern]），拖动只改裁剪比例，
 * 不重绘格子和文字，所以 60fps 拖得动。
 */
@Composable
fun CompareSplitDialog(
    original: Bitmap,
    result: PatternResult,
    onDismiss: () -> Unit,
) {
    val originalImage = remember(original) { original.asImageBitmap() }
    // 目标宽度 1250px：1440 宽屏上铺满内容区还不糊；小图纸（<32 格）也限制在 ≤64px/格
    val patternImage = remember(result) {
        val cell = (1250f / maxOf(result.width, result.height)).roundToInt().coerceIn(8, 64)
        val rendered = ExportRenderer.renderPattern(
            result,
            ExportRenderer.RenderOptions(
                cellPx = cell,
                showCodes = false,
                showGrid = true,
                showRuler = false,
            ),
        )
        // ExportRenderer 给的是 ARGB 数组，这里拼成 Bitmap 再转 Compose 的 ImageBitmap
        createBitmap(rendered.width, rendered.height).also {
            it.setPixels(rendered.pixels, 0, rendered.width, 0, 0, rendered.width, rendered.height)
        }.asImageBitmap()
    }
    var fraction by remember { mutableFloatStateOf(0.5f) }
    val handleRadiusPx = with(LocalDensity.current) { 22.dp.toPx() }
    val lineWidthPx = with(LocalDensity.current) { 3.dp.toPx() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.md, end = Spacing.sm, top = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("前后对比", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "拖动分割线：左原图、右图纸",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(MorphIcons.X, contentDescription = "关闭前后对比")
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(result.width.toFloat() / result.height.toFloat())
                            .background(Color(0xFF101010), RoundedCornerShape(12.dp)),
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        fraction = SplitCompareMath.fractionFor(
                                            offset.x,
                                            size.width.toFloat(),
                                        )
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures { change, _ ->
                                        fraction = SplitCompareMath.fractionFor(
                                            change.position.x,
                                            size.width.toFloat(),
                                        )
                                    }
                                },
                        ) {
                            val boxW = size.width
                            val boxH = size.height
                            val fitPattern = ComparisonLayout.fit(
                                patternImage.width, patternImage.height, boxW, boxH,
                            )
                            val fitOriginal = ComparisonLayout.fit(
                                originalImage.width, originalImage.height, boxW, boxH,
                            )

                            // 底层整张图纸
                            drawImage(
                                image = patternImage,
                                dstOffset = IntOffset(
                                    fitPattern.left.roundToInt(),
                                    fitPattern.top.roundToInt(),
                                ),
                                dstSize = IntSize(
                                    fitPattern.width.roundToInt(),
                                    fitPattern.height.roundToInt(),
                                ),
                            )

                            // 左半区盖原图：裁剪宽度 = 分割线位置
                            val dividerX = SplitCompareMath.dividerX(fraction, boxW)
                            clipRect(right = dividerX) {
                                drawImage(
                                    image = originalImage,
                                    dstOffset = IntOffset(
                                        fitOriginal.left.roundToInt(),
                                        fitOriginal.top.roundToInt(),
                                    ),
                                    dstSize = IntSize(
                                        fitOriginal.width.roundToInt(),
                                        fitOriginal.height.roundToInt(),
                                    ),
                                )
                            }

                            // 分割线与手柄
                            drawLine(
                                color = Color.White,
                                start = Offset(dividerX, 0f),
                                end = Offset(dividerX, boxH),
                                strokeWidth = lineWidthPx,
                            )
                            val cy = boxH / 2f
                            drawCircle(
                                color = Color.White,
                                radius = handleRadiusPx,
                                center = Offset(dividerX, cy),
                            )
                            val chevron = handleRadiusPx * 0.34f
                            val ink = Color(0xFF111111)
                            // 左右两个小三角，暗示"可拖动"
                            drawPath(
                                Path().apply {
                                    moveTo(dividerX - chevron * 0.6f, cy - chevron)
                                    lineTo(dividerX - chevron * 1.5f, cy)
                                    lineTo(dividerX - chevron * 0.6f, cy + chevron)
                                    close()
                                },
                                ink,
                            )
                            drawPath(
                                Path().apply {
                                    moveTo(dividerX + chevron * 0.6f, cy - chevron)
                                    lineTo(dividerX + chevron * 1.5f, cy)
                                    lineTo(dividerX + chevron * 0.6f, cy + chevron)
                                    close()
                                },
                                ink,
                            )
                        }

                        Text(
                            "原图",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(Spacing.xs)
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                                .padding(horizontal = Spacing.xs, vertical = 2.dp),
                        )
                        Text(
                            "图纸",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(Spacing.xs)
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                                .padding(horizontal = Spacing.xs, vertical = 2.dp),
                        )
                    }
                }

                Text(
                    "${result.width}×${result.height} · ${result.quality.colorCount} 色" +
                        " · 还原度 ${result.quality.score}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }
        }
    }
}
