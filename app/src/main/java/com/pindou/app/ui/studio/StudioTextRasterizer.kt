package com.pindou.app.ui.studio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import kotlin.math.ceil
import kotlin.math.roundToInt

/** 文字工具：把字符串栅格化成逐格掩码，交给 StudioEngine.stampMask。 */
object StudioTextRasterizer {

    data class Mask(val width: Int, val height: Int, val cells: BooleanArray)

    /**
     * @param fontCells 字高（格）。按 4× 超采样绘制后按 4×4 覆盖率切成格子，
     *                  避免直接把 12sp 字画到 58×58 网格上变成一团糊。
     */
    fun rasterize(text: String, fontCells: Int): Mask? {
        if (text.isEmpty()) return null
        val scale = 4
        val fontPx = fontCells.coerceIn(2, 120) * scale
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF000000.toInt()
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = fontPx.toFloat()
            textAlign = Paint.Align.LEFT
        }
        val lines = text.split('\n').ifEmpty { listOf(text) }
        val lineHeight = (paint.fontMetrics.descent - paint.fontMetrics.ascent) * 1.12f
        val textW = lines.maxOf { paint.measureText(it) }.roundToInt().coerceAtLeast(1)
        val w = textW + scale * 2
        val h = ceil(lineHeight * lines.size).toInt() + scale * 2
        val bmp = createBitmap(w, h)
        val canvas = Canvas(bmp)
        val metrics = paint.fontMetrics
        var baseline = scale + (-metrics.ascent)
        for (line in lines) {
            canvas.drawText(line, scale.toFloat(), baseline, paint)
            baseline += lineHeight
        }

        val cellsW = ceil(w.toDouble() / scale).toInt().coerceAtLeast(1)
        val cellsH = ceil(h.toDouble() / scale).toInt().coerceAtLeast(1)
        val out = BooleanArray(cellsW * cellsH)
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        for (cy in 0 until cellsH) {
            for (cx in 0 until cellsW) {
                var dark = 0
                var total = 0
                for (py in cy * scale until minOf(h, (cy + 1) * scale)) {
                    for (px in cx * scale until minOf(w, (cx + 1) * scale)) {
                        total++
                        if ((pixels[py * w + px] ushr 24) > 40 &&
                            ((pixels[py * w + px] shr 16) and 0xFF) < 160
                        ) dark++
                    }
                }
                out[cy * cellsW + cx] = total > 0 && dark * 4 >= total
            }
        }
        bmp.recycle()
        return Mask(cellsW, cellsH, out)
    }
}
