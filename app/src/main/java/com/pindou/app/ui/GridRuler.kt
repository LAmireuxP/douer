package com.pindou.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

/**
 * 图纸坐标尺：四周行列号。
 *
 * 坐标带固定在画布四边，格子随缩放/平移移动时刻度跟着走；格子太小时按
 * 5/10 抽稀，避免 60×60 图纸出现几十个挤在一起的小字。所有几何都从调用方
 * 传入的 origin/cell 推导，点击命中仍用同一套坐标。
 */
object GridRuler {

    /** 坐标带宽度（dp）。调用方用 density 转成 px 后统一传给几何函数。 */
    const val GUTTER_DP = 22f

    /**
     * 屏幕坐标尺的抽稀步长。
     *
     * Compose 的系统字体比导出的点阵字宽得多：40×40 在竖屏下每格约 30px，
     * 两位数标签按格标会直接叠在一起。这里把「每格」的门槛提到 56px，
     * 26px 以上只标 5 的倍数，再小按 10 抽稀。
     */
    fun labelStep(cell: Float): Int = when {
        cell >= 56f -> 1
        cell >= 30f -> 5
        cell >= 15f -> 10
        cell >= 8f -> 20
        else -> 50
    }

    fun DrawScope.drawGridRuler(
        origin: Offset,
        cell: Float,
        cols: Int,
        rows: Int,
        gutter: Float,
        measurer: TextMeasurer,
        cache: MutableMap<Pair<String, Int>, TextLayoutResult>,
        textColor: Color,
        bandColor: Color,
    ) {
        if (cell <= 0f || gutter <= 0f) return
        // 两侧都要留坐标带；极窄窗口下减半，保证中间仍有一像素内容区。
        val bandWidth = min(gutter, size.width / 2f)
        val bandHeight = min(gutter, size.height / 2f)
        if (bandWidth <= 0f || bandHeight <= 0f) return
        val rightX = size.width - bandWidth
        val bottomY = size.height - bandHeight

        // 坐标带铺底：不透明 surface 色，网格和色块不会从字缝里透出来。
        // 四条带分开画，重叠的四个角最后自然合成同一种底色。
        drawRect(
            color = bandColor,
            topLeft = Offset.Zero,
            size = androidx.compose.ui.geometry.Size(size.width, bandHeight),
        )
        drawRect(
            color = bandColor,
            topLeft = Offset(0f, bottomY),
            size = androidx.compose.ui.geometry.Size(size.width, bandHeight),
        )
        drawRect(
            color = bandColor,
            topLeft = Offset.Zero,
            size = androidx.compose.ui.geometry.Size(bandWidth, size.height),
        )
        drawRect(
            color = bandColor,
            topLeft = Offset(rightX, 0f),
            size = androidx.compose.ui.geometry.Size(bandWidth, size.height),
        )

        val line = Color.Black.copy(alpha = 0.55f)
        drawLine(line, Offset(0f, bandHeight), Offset(size.width, bandHeight), 1.4f)
        drawLine(line, Offset(0f, bottomY), Offset(size.width, bottomY), 1.4f)
        drawLine(line, Offset(bandWidth, 0f), Offset(bandWidth, size.height), 1.4f)
        drawLine(line, Offset(rightX, 0f), Offset(rightX, size.height), 1.4f)

        val step = labelStep(cell)
        val fontSize = (gutter * 0.42f).coerceIn(7f, 14f).sp
        val style = TextStyle(color = textColor, fontSize = fontSize)

        fun layout(label: String): TextLayoutResult {
            val key = label to fontSize.value.toInt()
            return cache.getOrPut(key) { measurer.measure(label, style) }
        }

        val firstX = max(0, (-origin.x / cell).toInt())
        val lastX = min(cols - 1, ((size.width - origin.x) / cell).toInt() + 1)
        val firstY = max(0, (-origin.y / cell).toInt())
        val lastY = min(rows - 1, ((size.height - origin.y) / cell).toInt() + 1)

        clipRect(0f, 0f, size.width, bandHeight) {
            for (x in firstX..lastX) {
                // 第一列始终标 1，之后标 5/10/20…，避免出现 1、6、11 这种读数
                if (x != 0 && (x + 1) % step != 0) continue
                val cx = origin.x + (x + 0.5f) * cell
                val tickX = origin.x + x * cell
                drawLine(
                    line,
                    Offset(tickX, bandHeight - 3f),
                    Offset(tickX, bandHeight),
                    1.2f,
                )
                val l = layout("${x + 1}")
                drawText(
                    l,
                    topLeft = Offset(cx - l.size.width / 2f, (bandHeight - l.size.height) / 2f),
                )
            }
        }
        clipRect(0f, bottomY, size.width, size.height) {
            for (x in firstX..lastX) {
                if (x != 0 && (x + 1) % step != 0) continue
                val cx = origin.x + (x + 0.5f) * cell
                val tickX = origin.x + x * cell
                drawLine(
                    line,
                    Offset(tickX, bottomY),
                    Offset(tickX, bottomY + 3f),
                    1.2f,
                )
                val l = layout("${x + 1}")
                drawText(
                    l,
                    topLeft = Offset(
                        cx - l.size.width / 2f,
                        bottomY + (bandHeight - l.size.height) / 2f,
                    ),
                )
            }
        }
        clipRect(0f, 0f, bandWidth, size.height) {
            for (y in firstY..lastY) {
                if (y != 0 && (y + 1) % step != 0) continue
                val cy = origin.y + (y + 0.5f) * cell
                val tickY = origin.y + y * cell
                drawLine(
                    line,
                    Offset(bandWidth - 3f, tickY),
                    Offset(bandWidth, tickY),
                    1.2f,
                )
                val l = layout("${y + 1}")
                drawText(
                    l,
                    topLeft = Offset((bandWidth - l.size.width) / 2f, cy - l.size.height / 2f),
                )
            }
        }
        clipRect(rightX, 0f, size.width, size.height) {
            for (y in firstY..lastY) {
                if (y != 0 && (y + 1) % step != 0) continue
                val cy = origin.y + (y + 0.5f) * cell
                val tickY = origin.y + y * cell
                drawLine(
                    line,
                    Offset(rightX, tickY),
                    Offset(rightX + 3f, tickY),
                    1.2f,
                )
                val l = layout("${y + 1}")
                drawText(
                    l,
                    topLeft = Offset(
                        rightX + (bandWidth - l.size.width) / 2f,
                        cy - l.size.height / 2f,
                    ),
                )
            }
        }
    }
}
