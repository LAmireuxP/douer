package com.pindou.app.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.pindou.app.core.editor.EditorEngine
import kotlin.math.abs
import kotlin.math.min

/** 画布变换。字段必须是 Compose state，否则捏合/平移只改字段不会触发重绘。 */
internal class CanvasTransform {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }
}

/**
 * 空格底色。原来是 (x+y) 交替的棋盘格，等于每格一次 drawRect：
 * 一张 60×60 的图光空背景就 3600 次绘制，滑动上色每帧重录一遍必掉帧。
 */
internal val EmptyCellColor = Color(0xFFEDEDE7)

internal fun availSize(canvas: Offset, gutter: Float): Offset = Offset(
    (canvas.x - gutter * 2f).coerceAtLeast(1f),
    (canvas.y - gutter * 2f).coerceAtLeast(1f),
)

internal fun baseOrigin(canvas: Offset, engine: EditorEngine, gutter: Float): Offset {
    val avail = availSize(canvas, gutter)
    val cellBase = min(avail.x / engine.width, avail.y / engine.height)
    return Offset(
        gutter + (avail.x - cellBase * engine.width) / 2f,
        gutter + (avail.y - cellBase * engine.height) / 2f,
    )
}

internal fun drawOrigin(
    canvas: Offset,
    tf: CanvasTransform,
    engine: EditorEngine,
    gutter: Float,
): Offset = baseOrigin(canvas, engine, gutter) + tf.offset

internal fun cellPx(
    canvas: Offset,
    tf: CanvasTransform,
    engine: EditorEngine,
    gutter: Float,
): Float {
    val avail = availSize(canvas, gutter)
    return min(avail.x / engine.width, avail.y / engine.height) * tf.scale
}

internal fun cellAt(
    view: Offset,
    canvas: Offset,
    tf: CanvasTransform,
    engine: EditorEngine,
    gutter: Float,
): Int {
    val cell = cellPx(canvas, tf, engine, gutter)
    if (cell <= 0f) return -1
    val origin = drawOrigin(canvas, tf, engine, gutter)
    val x = ((view.x - origin.x) / cell).toInt()
    val y = ((view.y - origin.y) / cell).toInt()
    if (x < 0 || y < 0 || x >= engine.width || y >= engine.height) return -1
    return y * engine.width + x
}

/** 把画布拉回可视区：缩放到 1 倍时强制居中；放大后允许在四边内平移。 */
internal fun clampTransform(
    canvas: Offset,
    engine: EditorEngine,
    tf: CanvasTransform,
    gutter: Float,
) {
    if (canvas.x <= 0f || canvas.y <= 0f) return
    val cell = cellPx(canvas, tf, engine, gutter)
    if (cell <= 0f) return
    val avail = availSize(canvas, gutter)
    val base = baseOrigin(canvas, engine, gutter)
    val origin = drawOrigin(canvas, tf, engine, gutter)
    val contentW = cell * engine.width
    val contentH = cell * engine.height
    val x = if (contentW <= avail.x) {
        gutter + (avail.x - contentW) / 2f
    } else {
        origin.x.coerceIn(canvas.x - gutter - contentW, gutter)
    }
    val y = if (contentH <= avail.y) {
        gutter + (avail.y - contentH) / 2f
    } else {
        origin.y.coerceIn(canvas.y - gutter - contentH, gutter)
    }
    tf.offset = Offset(x - base.x, y - base.y)
}

internal fun zoomAt(
    canvas: Offset,
    engine: EditorEngine,
    tf: CanvasTransform,
    focus: Offset,
    targetScale: Float,
    gutter: Float,
) {
    val oldScale = tf.scale
    val newScale = targetScale.coerceIn(1f, 20f)
    if (abs(newScale - oldScale) < 0.001f) return
    val origin = drawOrigin(canvas, tf, engine, gutter)
    val k = newScale / oldScale
    val newOrigin = Offset(
        focus.x - (focus.x - origin.x) * k,
        focus.y - (focus.y - origin.y) * k,
    )
    tf.scale = newScale
    tf.offset = newOrigin - baseOrigin(canvas, engine, gutter)
    clampTransform(canvas, engine, tf, gutter)
}
