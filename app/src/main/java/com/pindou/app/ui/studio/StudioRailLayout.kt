package com.pindou.app.ui.studio

/**
 * 浮窗位置换算：持久化存 0..1 比例，运行时用像素跟手。
 * 抽成纯函数，方便单测覆盖换屏幕、尺寸为 0、越界这些边界。
 */
object StudioRailLayout {

    fun clamp(value: Float, max: Float): Float {
        val safeMax = max.coerceAtLeast(0f)
        return value.coerceIn(0f, safeMax)
    }

    fun toPixels(fraction: Float, max: Float, defaultFraction: Float): Float {
        val safeMax = max.coerceAtLeast(0f)
        val f = if (fraction < 0f) defaultFraction else fraction
        return (f.coerceIn(0f, 1f) * safeMax).coerceIn(0f, safeMax)
    }

    fun toFraction(pixels: Float, max: Float): Float =
        if (max <= 0f) 0f else (pixels / max).coerceIn(0f, 1f)
}
