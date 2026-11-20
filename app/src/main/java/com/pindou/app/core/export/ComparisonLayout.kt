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
