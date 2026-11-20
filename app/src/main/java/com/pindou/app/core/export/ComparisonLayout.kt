package com.pindou.app.core.export

import kotlin.math.min

/**
 * 「前后对比」导出的纯布局计算：把原图/图纸等比例放进各自的面板。
 *
 * 只算位置，不碰 Bitmap/Canvas——这样 JVM 单测能锁住宽图/高图/极端比例的
 * 留白方向，真机只负责把算好的矩形画出来。
 */
object ComparisonLayout {

    data class Fit(val left: Float, val top: Float, val width: Float, val height: Float)

    fun fit(srcW: Int, srcH: Int, boxW: Float, boxH: Float): Fit {
        if (srcW <= 0 || srcH <= 0 || boxW <= 0f || boxH <= 0f) {
            return Fit(0f, 0f, 0f, 0f)
        }
        val scale = min(boxW / srcW, boxH / srcH)
        val w = srcW * scale
        val h = srcH * scale
        return Fit((boxW - w) / 2f, (boxH - h) / 2f, w, h)
    }
}

/**
 * 交互式前后对比的分割线数学。
 *
 * 分割线只允许停在 8%~92%：贴边时手柄会压住关闭按钮、也点不回来。
 * 单独抽出来是为了 JVM 单测能锁住越界与零宽度的兜底，UI 只负责画。
 */
object SplitCompareMath {

    const val MIN_FRACTION = 0.08f
    const val MAX_FRACTION = 0.92f

    /** 手指 x 坐标 → 分割比例；零宽度时退回中线，避免除零得到 NaN 传进 Canvas。 */
    fun fractionFor(x: Float, width: Float): Float {
        if (width <= 0f) return 0.5f
        return (x / width).coerceIn(MIN_FRACTION, MAX_FRACTION)
    }

    /** 分割比例 → 画布上的 x 像素 */
    fun dividerX(fraction: Float, width: Float): Float =
        width * fraction.coerceIn(MIN_FRACTION, MAX_FRACTION)
}
