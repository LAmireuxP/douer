package com.pindou.app.core.editor

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 画像素里的基础形状：直线、矩形、椭圆。 */
enum class ShapeTool(val label: String) {
    LINE("直线"),
    RECT("矩形"),
    ELLIPSE("椭圆");

    /**
     * 把形状光栅化成格子索引。坐标都是格坐标，终点包含在形状内。
     * 矩形/椭圆支持描边和填充；直线始终描边。
     */
    fun indices(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        width: Int,
        height: Int,
        filled: Boolean,
    ): List<Int> {
        if (width <= 0 || height <= 0) return emptyList()
        val left = minOf(x0, x1).coerceIn(0, width - 1)
        val right = maxOf(x0, x1).coerceIn(0, width - 1)
        val top = minOf(y0, y1).coerceIn(0, height - 1)
        val bottom = maxOf(y0, y1).coerceIn(0, height - 1)
        val out = LinkedHashSet<Int>()

        fun add(x: Int, y: Int) {
            if (x in 0 until width && y in 0 until height) out.add(y * width + x)
        }

        when (this) {
            LINE -> {
                var x = x0.coerceIn(0, width - 1)
                var y = y0.coerceIn(0, height - 1)
                val ex = x1.coerceIn(0, width - 1)
                val ey = y1.coerceIn(0, height - 1)
                val dx = abs(ex - x)
                val sx = if (x < ex) 1 else -1
                val dy = -abs(ey - y)
                val sy = if (y < ey) 1 else -1
                var err = dx + dy
                while (true) {
                    add(x, y)
                    if (x == ex && y == ey) break
                    val e2 = 2 * err
                    if (e2 >= dy) {
                        err += dy
                        x += sx
                    }
                    if (e2 <= dx) {
                        err += dx
                        y += sy
                    }
                }
            }
            RECT -> {
                for (y in top..bottom) {
                    for (x in left..right) {
                        if (filled || x == left || x == right || y == top || y == bottom) {
                            add(x, y)
                        }
                    }
                }
            }
            ELLIPSE -> {
                val cx = (left + right) / 2.0
                val cy = (top + bottom) / 2.0
                val rx = (right - left) / 2.0
                val ry = (bottom - top) / 2.0
                if (rx < 0.5 || ry < 0.5) {
                    add(left, top)
                } else {
                    for (y in top..bottom) {
                        val ny = (y - cy) / ry
                        val dx = rx * sqrt((1.0 - ny * ny).coerceAtLeast(0.0))
                        val xa = (cx - dx).roundToInt()
                        val xb = (cx + dx).roundToInt()
                        if (filled) {
                            for (x in xa..xb) add(x, y)
                        } else {
                            add(xa, y)
                            add(xb, y)
                        }
                    }
                    // 顶部/底部曲率大，按行补 x 方向的两个端点，避免椭圆左右变尖
                    for (x in left..right) {
                        val nx = (x - cx) / rx
                        val dy = ry * sqrt((1.0 - nx * nx).coerceAtLeast(0.0))
                        val ya = (cy - dy).roundToInt()
                        val yb = (cy + dy).roundToInt()
                        if (filled) {
                            for (y in ya..yb) add(x, y)
                        } else {
                            add(x, ya)
                            add(x, yb)
                        }
                    }
                }
            }
        }
        return out.toList()
    }
}
