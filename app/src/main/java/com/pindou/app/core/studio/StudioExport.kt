package com.pindou.app.core.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.pindou.app.core.export.ExportService
import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.core.pattern.PatternResult
import com.pindou.app.core.pattern.CodeLabelMode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 画像素的直接导出：像素 PNG（1×–64×）、GIF、色号图、调色板、PDF。
 *
 * 色号图/PDF 继续走 ExportService 的图纸样式（单行标题、四边坐标、
 * 格内色号、底部用量条），不在这个模块里另画一套。
 */
object StudioExport {

    private const val MAX_BITMAP_PIXELS = 18_000_000L
    private const val MAX_GIF_PIXELS = 32_000_000L

    fun exportPixelPng(
        context: Context,
        engine: StudioEngine,
        frame: Int,
        scale: Int,
        transparent: Boolean,
        projectName: String,
    ): ExportService.Exported = runCatching {
        val bitmap = renderPixelBitmap(engine, frame, scale, transparent)
        val name = "拼豆_${safe(projectName)}_像素_${effectiveScale(engine, scale)}x_${stamp()}.png"
        try {
            ExportService.savePng(context, bitmap, name)
        } finally {
            bitmap.recycle()
        }
    }.getOrElse { ExportService.Exported.Failed(it.message ?: "像素图导出失败") }

    fun exportAnimation(
        context: Context,
        engine: StudioEngine,
        scale: Int,
        projectName: String,
    ): ExportService.Exported = runCatching {
        require(engine.frameCount > 0) { "没有帧" }
        var s = scale.coerceIn(1, 8)
        // 所有帧一起算内存：单帧 12M 像素 × 20 帧会把手机直接打爆。
        while (engine.width.toLong() * s * engine.height * s * engine.frameCount > MAX_GIF_PIXELS && s > 1) s--
        val frames = engine.frames.indices.map { i ->
            val argb = engine.renderArgb(i)
            scaleArgb(argb, engine.width, engine.height, s)
        }
        val delays = engine.frames.map { (it.holdMs / 10).coerceAtLeast(2) }
        val bytes = GifEncoder.encode(frames, engine.width * s, engine.height * s, delays)
        val name = "拼豆_${safe(projectName)}_动画_${s}x_${stamp()}.gif"
        ExportService.saveBytes(context, bytes, name, "image/gif")
    }.getOrElse { ExportService.Exported.Failed(it.message ?: "GIF 导出失败") }

    fun exportCodeChart(
        context: Context,
        engine: StudioEngine,
        palette: BeadPalette,
        projectName: String,
        symbol: Boolean = false,
        codeLabels: CodeLabelMode = CodeLabelMode.OUTLINE,
    ): ExportService.Exported = runCatching {
        val results = engine.frames.indices.map { i ->
            PatternResult(engine.width, engine.height, engine.composite(i), palette, 0)
        }
        ExportService.exportPng(context, results, projectName, symbol, codeLabels)
    }.getOrElse { ExportService.Exported.Failed(it.message ?: "色号图导出失败") }

    fun exportPdf(
        context: Context,
        engine: StudioEngine,
        palette: BeadPalette,
        projectName: String,
        codeLabels: CodeLabelMode = CodeLabelMode.OUTLINE,
    ): ExportService.Exported = runCatching {
        val results = engine.frames.indices.map { i ->
            PatternResult(engine.width, engine.height, engine.composite(i), palette, 0)
        }
        ExportService.exportPdf(context, results, projectName, codeLabels)
    }.getOrElse { ExportService.Exported.Failed(it.message ?: "PDF 导出失败") }

    /** 调色板 + 用量 PNG，方便对照买豆。 */
    fun exportPalette(
        context: Context,
        engine: StudioEngine,
        palette: BeadPalette,
        projectName: String,
    ): ExportService.Exported = runCatching {
        val counts = IntArray(palette.colors.size)
        for (i in engine.frames.indices) {
            for (c in engine.composite(i)) if (c in counts.indices) counts[c]++
        }
        val used = counts.indices.filter { counts[it] > 0 }.sortedByDescending { counts[it] }
        require(used.isNotEmpty()) { "画布还是空的" }

        val columns = 3
        val cellW = 360
        val cellH = 74
        val pad = 24
        val header = 84
        val rows = (used.size + columns - 1) / columns
        val width = pad * 2 + columns * cellW
        val height = header + pad + rows * cellH
        val bmp = createBitmap(width, height)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFFFFFFFF.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFF1F2937.toInt()
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 34f
        canvas.drawText("${projectName} 调色板 · ${used.size} 色", pad.toFloat(), 52f, paint)

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 24f
        for ((i, idx) in used.withIndex()) {
            val col = i % columns
            val row = i / columns
            val x = pad + col * cellW
            val y = header + pad + row * cellH
            paint.color = palette.colors[idx].argb
            canvas.drawRoundRect(
                x.toFloat(), y.toFloat(), x + 56f, y + 56f, 8f, 8f, paint,
            )
            paint.color = 0xFF111827.toInt()
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 24f
            canvas.drawText(palette.colors[idx].code, x + 68f, y + 26f, paint)
            paint.typeface = Typeface.DEFAULT
            paint.textSize = 20f
            paint.color = 0xFF6B7280.toInt()
            canvas.drawText("${counts[idx]} 颗", x + 68f, y + 52f, paint)
        }
        val name = "拼豆_${safe(projectName)}_调色板_${stamp()}.png"
        try {
            ExportService.savePng(context, bmp, name)
        } finally {
            bmp.recycle()
        }
    }.getOrElse { ExportService.Exported.Failed(it.message ?: "调色板导出失败") }

    /** 工作区预览/缩略图用：返回 1 格 = 1 像素的位图。 */
    fun frameBitmap(engine: StudioEngine, frame: Int, transparent: Boolean = true): Bitmap {
        val argb = if (transparent) engine.renderArgb(frame) else engine.renderArgb(frame, 0xFFFFFFFF.toInt())
        val bmp = createBitmap(engine.width, engine.height)
        bmp.setPixels(argb, 0, engine.width, 0, 0, engine.width, engine.height)
        return bmp
    }

    private fun renderPixelBitmap(
        engine: StudioEngine,
        frame: Int,
        requestedScale: Int,
        transparent: Boolean,
    ): Bitmap {
        val s = effectiveScale(engine, requestedScale)
        val argb = if (transparent) {
            engine.renderArgb(frame)
        } else {
            engine.renderArgb(frame, 0xFFFFFFFF.toInt())
        }
        val base = createBitmap(engine.width, engine.height)
        base.setPixels(argb, 0, engine.width, 0, 0, engine.width, engine.height)
        if (s == 1) return base
        val scaled = base.scale(engine.width * s, engine.height * s, false)
        if (scaled !== base) base.recycle()
        return scaled
    }

    fun effectiveScale(engine: StudioEngine, requested: Int): Int {
        var s = requested.coerceIn(1, 64)
        while (engine.width.toLong() * s * engine.height * s > MAX_BITMAP_PIXELS && s > 1) s--
        return s
    }

    private fun scaleArgb(src: IntArray, w: Int, h: Int, scale: Int): IntArray {
        if (scale == 1) return src
        val outW = w * scale
        val outH = h * scale
        val out = IntArray(outW * outH)
        for (y in 0 until outH) {
            val sy = y / scale
            for (x in 0 until outW) {
                out[y * outW + x] = src[sy * w + x / scale]
            }
        }
        return out
    }

    private fun safe(name: String): String {
        val cleaned = name.trim().map { c ->
            if (c.isLetterOrDigit() || c == '-' || c == '_') c else '_'
        }.joinToString("").take(40)
        return cleaned.ifBlank { "画像素" }
    }

    private fun stamp(): String {
        val now = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
        return now.format(java.util.Date())
    }
}
