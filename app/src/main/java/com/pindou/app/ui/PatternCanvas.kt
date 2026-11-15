package com.pindou.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.pindou.app.core.pattern.PatternResult
import com.pindou.app.core.pattern.ColorLabelLayout
import com.pindou.app.core.pattern.CodeLabelMode
import com.pindou.app.ui.CellCodeDrawer.drawCellCode
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** 空格底色（棋盘浅灰蓝，与 Hairline 同色温） */
private val EmptyCellA = Color(0xFFF7F9FC)
private val EmptyCellB = Color(0xFFEDF1F7)

/** 预览画布的缩放/平移状态（换图纸时重建） */
private class PreviewTransform {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
}

private fun previewAvail(canvas: Offset, gutter: Float): Offset = Offset(
    (canvas.x - gutter * 2f).coerceAtLeast(1f),
    (canvas.y - gutter * 2f).coerceAtLeast(1f),
)

private fun previewBase(
    canvas: Offset,
    result: PatternResult,
    gutter: Float,
): Offset {
    val avail = previewAvail(canvas, gutter)
    val cell = min(avail.x / result.width, avail.y / result.height)
    return Offset(
        gutter + (avail.x - cell * result.width) / 2f,
        gutter + (avail.y - cell * result.height) / 2f,
    )
}

private fun previewCell(
    canvas: Offset,
    result: PatternResult,
    scale: Float,
    gutter: Float,
): Float {
    val avail = previewAvail(canvas, gutter)
    return min(avail.x / result.width, avail.y / result.height) * scale
}

private fun clampPreview(
    canvas: Offset,
    result: PatternResult,
    tf: PreviewTransform,
    gutter: Float,
) {
    val cell = previewCell(canvas, result, tf.scale, gutter)
    val base = previewBase(canvas, result, gutter)
    val contentW = cell * result.width
    val contentH = cell * result.height
    val avail = previewAvail(canvas, gutter)
    val x = if (contentW <= avail.x) {
        gutter + (avail.x - contentW) / 2f
    } else {
        (base.x + tf.offset.x).coerceIn(canvas.x - gutter - contentW, gutter)
    }
    val y = if (contentH <= avail.y) {
        gutter + (avail.y - contentH) / 2f
    } else {
        (base.y + tf.offset.y).coerceIn(canvas.y - gutter - contentH, gutter)
    }
    tf.offset = Offset(x - base.x, y - base.y)
}

/**
 * 图纸画布：按格绘制色卡颜色 + 网格线 + 色号标注，支持双指捏合缩放（1~20 倍）与平移。
 *
 * - 色号画在格子下沿标签内（见 [CellCodeDrawer]），格子放大到 ≥26px 时自动出现，
 *   解决"大图纸预览时色号看不清"的问题；
 * - 单指拖动不消费（交给外层页面滚动），双指才进入缩放/平移；双击复原；
 * - 只绘制可见格，放大后不会全量重绘；色号文字布局按 (色号,整数字号) 缓存。
 */
@Composable
fun PatternCanvas(
    result: PatternResult,
    showCodes: Boolean,
    codeLabels: CodeLabelMode = CodeLabelMode.FULL,
    modifier: Modifier = Modifier,
    cellMinPxForCodes: Float = CellCodeDrawer.MIN_CELL_PX,
) {
    val measurer = rememberTextMeasurer()
    val layoutCache = remember { mutableMapOf<Pair<String, Int>, TextLayoutResult>() }
    val transform = remember(result) { PreviewTransform() }
    val labels = remember(result, codeLabels) {
        when (codeLabels) {
            CodeLabelMode.FULL -> null
            CodeLabelMode.OUTLINE ->
                ColorLabelLayout.labelMaskOutline(result.cells, result.width, result.height)
        }
    }
    val gutter = with(LocalDensity.current) { GridRuler.GUTTER_DP.dp.toPx() }
    val rulerCache = remember { mutableMapOf<Pair<String, Int>, TextLayoutResult>() }
    val rulerTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val rulerBandColor = MaterialTheme.colorScheme.surface

    Canvas(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(result) {
                detectTapGestures(onDoubleTap = {
                    transform.scale = 1f
                    transform.offset = Offset.Zero
                })
            }
            .pointerInput(result) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var prevDist = 0f
                    var prevCentroid = Offset.Zero
                    var isTransform = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            // 双指：以质心为缩放中心捏合，并跟随质心平移
                            isTransform = true
                            val a = pressed[0].position
                            val b = pressed[1].position
                            val centroid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                            val dist = hypot(
                                (a.x - b.x).toDouble(),
                                (a.y - b.y).toDouble(),
                            ).toFloat()
                            val canvas = Offset(size.width.toFloat(), size.height.toFloat())
                            if (prevDist > 0f) {
                                val old = transform.scale
                                val new = (old * (dist / prevDist)).coerceIn(1f, 20f)
                                val k = new / old
                                val base = previewBase(canvas, result, gutter)
                                val origin = base + transform.offset
                                val newOrigin = Offset(
                                    centroid.x - (centroid.x - origin.x) * k,
                                    centroid.y - (centroid.y - origin.y) * k,
                                )
                                transform.scale = new
                                transform.offset = newOrigin - base
                            }
                            if (prevCentroid != Offset.Zero) {
                                transform.offset += centroid - prevCentroid
                            }
                            prevDist = dist
                            prevCentroid = centroid
                            clampPreview(
                                Offset(size.width.toFloat(), size.height.toFloat()),
                                result,
                                transform,
                                gutter,
                            )
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (isTransform) {
                            // 缩放后余下的单指继续平移画布
                            val c = pressed[0]
                            transform.offset += c.position - c.previousPosition
                            if (c.positionChanged()) c.consume()
                            clampPreview(
                                Offset(size.width.toFloat(), size.height.toFloat()),
                                result,
                                transform,
                                gutter,
                            )
                            prevDist = 0f
                            prevCentroid = Offset.Zero
                        } else {
                            // 纯单指：不消费，让外层页面正常滚动
                            prevDist = 0f
                            prevCentroid = Offset.Zero
                        }
                        // 平移钳制：内容始终铺满可用区域（1 倍时自动回正）
                        clampPreview(
                            Offset(size.width.toFloat(), size.height.toFloat()),
                            result,
                            transform,
                            gutter,
                        )
                    }
                }
            },
    ) {
        val canvas = Offset(size.width, size.height)
        val cell = previewCell(canvas, result, transform.scale, gutter)
        val origin = previewBase(canvas, result, gutter) + transform.offset

        // 只算可见格范围（放大时避免全量重绘）
        val firstX = max(0, (-origin.x / cell).toInt())
        val firstY = max(0, (-origin.y / cell).toInt())
        val lastX = min(result.width - 1, ((size.width - origin.x) / cell).toInt() + 1)
        val lastY = min(result.height - 1, ((size.height - origin.y) / cell).toInt() + 1)

        for (y in firstY..lastY) {
            for (x in firstX..lastX) {
                val idx = result.cells[y * result.width + x]
                val left = origin.x + x * cell
                val top = origin.y + y * cell
                val entry = if (idx >= 0) result.palette.colors.getOrNull(idx) else null
                val cellColor = if (entry != null) {
                    Color(entry.argb)
                } else {
                    if ((x + y) % 2 == 0) EmptyCellA else EmptyCellB
                }
                drawRect(
                    color = cellColor,
                    topLeft = Offset(left, top),
                    size = Size(cell, cell),
                )
            }
        }
        val line = Color.Black.copy(alpha = 0.10f)
        val major = Color.Black.copy(alpha = 0.34f)
        for (i in firstX..lastX + 1) {
            val px = origin.x + i * cell
            val isMajor = i % 5 == 0
            drawLine(
                if (isMajor) major else line,
                Offset(px, origin.y + firstY * cell),
                Offset(px, origin.y + (lastY + 1) * cell),
                if (isMajor) 1.4f else 1f,
            )
        }
        for (i in firstY..lastY + 1) {
            val py = origin.y + i * cell
            val isMajor = i % 5 == 0
            drawLine(
                if (isMajor) major else line,
                Offset(origin.x + firstX * cell, py),
                Offset(origin.x + (lastX + 1) * cell, py),
                if (isMajor) 1.4f else 1f,
            )
        }
        // 图纸外框：和导出图一致，边界不靠坐标带猜
        drawRect(
            color = Color.Black.copy(alpha = 0.55f),
            topLeft = origin,
            size = Size(cell * result.width, cell * result.height),
            style = Stroke(width = 1.2f),
        )
        if (showCodes && cell >= cellMinPxForCodes) {
            for (y in firstY..lastY) {
                for (x in firstX..lastX) {
                    val idx = result.cells[y * result.width + x]
                    if (idx < 0) continue
                    if (labels != null && !labels[y * result.width + x]) continue
                    val entry = result.palette.colors.getOrNull(idx) ?: continue
                    drawCellCode(
                        measurer = measurer,
                        code = entry.code,
                        left = origin.x + x * cell,
                        top = origin.y + y * cell,
                        cell = cell,
                        argb = entry.argb,
                        layoutCache = layoutCache,
                    )
                }
            }
        }

        with(GridRuler) {
            drawGridRuler(
                origin = origin,
                cell = cell,
                cols = result.width,
                rows = result.height,
                gutter = gutter,
                measurer = measurer,
                cache = rulerCache,
                textColor = rulerTextColor,
                bandColor = rulerBandColor,
            )
        }
    }
}
