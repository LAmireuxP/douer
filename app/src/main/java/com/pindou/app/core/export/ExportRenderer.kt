package com.pindou.app.core.export

import com.pindou.app.core.pattern.PatternResult

/**
 * 导出渲染：把图纸渲染为位图（带色号版 / 符号版）。
 * 纯逻辑，不依赖 Android Bitmap——输出为像素数组 + 尺寸，由调用方包装成 Bitmap。
 * 这样可以在 JVM 单测里完整验证渲染结果。
 */
object ExportRenderer {

    /** 符号版编号前缀（与色号组合成唯一标记，黑白打印友好） */
    val SYMBOLS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ123456".toCharArray()

    data class RenderedBitmap(
        val width: Int,
        val height: Int,
        val pixels: IntArray,
    )

    data class RenderOptions(
        val cellPx: Int = 64,
        val showCodes: Boolean = true,
        val showGrid: Boolean = true,
        /** 四周行列坐标带。导出时开启，屏幕预览由 Compose 的 GridRuler 绘制。 */
        val showRuler: Boolean = false,
        /** 分页导出时的全局行偏移：坐标从 rowOffset + 1 开始，而不是每页重新从 1 数。 */
        val rowOffset: Int = 0,
        /**
         * 标题栏高度（像素）。内嵌点阵字体只有 ASCII 字形，中文标题会缺字，
         * 因此这里只**预留空间**，真正的标题文字由 Android 层用系统字体绘制
         * （见 ExportService.drawTitleBar）。
         */
        val titleHeightPx: Int = 0,
    )

    private const val BG = 0xFFFFFFFF.toInt()          // 白纸：打印不费墨，色块更干净
    private const val GRID_LINE = 0x30000000          // 细网格：白底约 #B3B3B3
    private const val GRID_MAJOR = 0xFF1A1A1A.toInt()  // 每 5 格一条主线
    private const val FRAME = 0xFF1A1A1A.toInt()       // 图纸外框
    private const val TEXT_DARK = 0xFF333333.toInt()
    private const val TEXT_LIGHT = 0xFFF5F5F5.toInt()
    private const val RULER_BG = 0xFFFFFFFF.toInt()
    private const val RULER_LINE = 0xFF1A1A1A.toInt()
    /** 大格间隔：与画像素/识别网格的 5×5 大正方形保持一致 */
    private const val MAJOR_EVERY = 5

    /** 坐标带宽度：随格子放大，但不小于 14px，避免数字挤成一团。 */
    fun rulerSizePx(cellPx: Int): Int = (cellPx * 0.5f).toInt().coerceIn(14, 40)

    /**
     * 渲染彩色图纸。每格 cellPx 像素，格内居中绘制色号（若开启且格子够大）。
     * 顶部可选标题栏（图纸名 + 尺寸/色卡信息）。
     */
    fun renderPattern(result: PatternResult, options: RenderOptions): RenderedBitmap {
        val cell = options.cellPx.coerceIn(8, 256)
        val titleH = options.titleHeightPx.coerceAtLeast(0)
        val ruler = if (options.showRuler) rulerSizePx(cell) else 0
        val ox = ruler
        val oy = titleH + ruler
        val w = ruler * 2 + result.width * cell + 1
        val h = ruler * 2 + result.height * cell + 1 + titleH
        val pixels = IntArray(w * h) { BG }
        val labels = com.pindou.app.core.pattern.ColorLabelLayout.labelMask(
            result.cells, result.width, result.height,
        )

        for (gy in 0 until result.height) {
            for (gx in 0 until result.width) {
                val idx = result.cells[gy * result.width + gx]
                if (idx < 0) continue
                val argb = result.palette.colors[idx].argb
                val x0 = ox + gx * cell
                val y0 = oy + gy * cell
                fillRect(pixels, w, h, x0, y0, cell, cell, argb)
                if (options.showCodes && cell >= 20 && labels[gy * result.width + gx]) {
                    val code = result.palette.colors[idx].code
                    val textColor = if (isLight(argb)) TEXT_DARK else TEXT_LIGHT
                    // 点阵字高 7px，缩放到不超过格子高度 28%，避免遮住颜色
                    val maxScale = (cell * 0.28f / 7f).toInt().coerceAtLeast(1)
                    val byWidth = (cell * 0.9f / (code.length * 6f)).toInt().coerceAtLeast(1)
                    val glyphScale = minOf(maxScale, byWidth, 4)
                    val tw = code.length * 6 * glyphScale - glyphScale
                    val th = 7 * glyphScale
                    drawTextLine(
                        pixels, w, h, code,
                        x0 + (cell - tw) / 2,
                        y0 + (cell - th) / 2,
                        textColor, glyphScale,
                    )
                }
            }
        }

        if (options.showGrid) {
            // 细线先铺满，再用深色主线压出 5×5 大格；比所有线同色更容易数格
            for (gx in 0..result.width) {
                if (gx % MAJOR_EVERY == 0) continue
                val x = (ox + gx * cell).coerceAtMost(w - 1)
                drawVLine(pixels, w, h, x, oy, GRID_LINE)
            }
            for (gy in 0..result.height) {
                if (gy % MAJOR_EVERY == 0) continue
                val y = (oy + gy * cell).coerceAtMost(h - 1)
                drawHLine(pixels, w, h, y, GRID_LINE)
            }
            // 主线随格子加粗：120px 的大格上 1px 线打印出来会消失
            val majorWidth = (cell / 48).coerceIn(1, 4)
            for (gx in 0..result.width step MAJOR_EVERY) {
                val x = (ox + gx * cell).coerceAtMost(w - 1)
                for (t in 0 until majorWidth) {
                    drawVLine(pixels, w, h, (x + t).coerceAtMost(w - 1), oy, GRID_MAJOR)
                }
            }
            for (gy in 0..result.height step MAJOR_EVERY) {
                val y = (oy + gy * cell).coerceAtMost(h - 1)
                for (t in 0 until majorWidth) {
                    drawHLine(pixels, w, h, (y + t).coerceAtMost(h - 1), GRID_MAJOR)
                }
            }
        }
        if (ruler > 0) {
            drawRuler(
                pixels, w, h, ox, oy, cell, result.width, result.height,
                ruler, titleH, options.rowOffset,
            )
        }
        // 最外层双线框：参考图的「整张图纸」边界，裁切/打印时边界清楚
        if (options.showGrid) {
            val frameWidth = (cell / 40).coerceIn(2, 5)
            for (inset in 0 until frameWidth) {
                drawHLineRange(pixels, w, h, (titleH + inset).coerceAtMost(h - 1), 0, w, FRAME)
                drawHLineRange(pixels, w, h, (h - 1 - inset).coerceAtLeast(0), 0, w, FRAME)
                drawVLineRange(pixels, w, h, inset.coerceAtMost(w - 1), titleH, h, FRAME)
                drawVLineRange(pixels, w, h, (w - 1 - inset).coerceAtLeast(0), titleH, h, FRAME)
            }
        }
        return RenderedBitmap(w, h, pixels)
    }

    /**
     * 渲染黑白符号版：每格白底 + 黑色符号 + 格内小字色号。
     * 按色卡索引循环分配符号（相同色号 = 相同符号），随附对照表。
     */
    fun renderSymbol(result: PatternResult, options: RenderOptions): RenderedBitmap {
        val cell = options.cellPx.coerceIn(16, 256)
        val ruler = if (options.showRuler) rulerSizePx(cell) else 0
        val ox = ruler
        val oy = cell + ruler
        // usage() 按用量降序，符号分配因此是确定性的：用得多的颜色拿前面的符号
        val usedIndices = result.usage().map { it.first }
        val symbolOf = symbolAssignments(result)

        val legendRows = usedIndices.size
        val titleH = cell
        val legendH = ((legendRows + 3) / 4) * (cell / 2) + cell
        val w = ruler * 2 + result.width * cell + 1
        val h = ruler * 2 + result.height * cell + 1 + titleH + legendH
        val pixels = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        val labels = com.pindou.app.core.pattern.ColorLabelLayout.labelMask(
            result.cells, result.width, result.height,
        )

        // 文字（标题/色号/对照表）可由 Android 层用系统字体覆盖绘制，
        // 纯 JVM 测试与旧调用方仍走内嵌点阵
        if (options.showCodes) {
            drawTextLine(pixels, w, h, "SYMBOL CHART", ox + 8, titleH / 2 - 8, TEXT_DARK, 2)
        }

        for (gy in 0 until result.height) {
            for (gx in 0 until result.width) {
                val idx = result.cells[gy * result.width + gx]
                if (idx < 0) continue
                val x0 = ox + gx * cell
                val y0 = oy + gy * cell
                // 符号版底色用浅灰，靠色号文字区分（黑白打印机友好）
                fillRect(pixels, w, h, x0 + 2, y0 + 2, cell - 4, cell - 4, 0xFFF2F2F2.toInt())
                if (options.showCodes && cell >= 24 && labels[gy * result.width + gx]) {
                    val code = result.palette.colors[idx].code
                    drawTextLine(
                        pixels, w, h, "${symbolOf[idx]} $code",
                        x0 + 2, y0 + cell - 9, TEXT_DARK, 1,
                    )
                }
            }
        }

        // 网格
        for (gx in 0..result.width) {
            val x = (ox + gx * cell).coerceAtMost(w - 1)
            drawVLine(pixels, w, h, x, oy, GRID_LINE)
        }
        for (gy in 0..result.height) {
            val y = (oy + gy * cell).coerceAtMost(h - 1)
            drawHLine(pixels, w, h, y, GRID_LINE)
        }

        // 对照表：4 列 × N 行
        val legendTop = oy + result.height * cell + ruler + cell / 2
        val colW = (w - ox) / 4
        usedIndices.forEachIndexed { i, colorIdx ->
            val col = i % 4
            val row = i / 4
            val x = ox + col * colW
            val y = legendTop + row * (cell / 2)
            val entry = result.palette.colors[colorIdx]
            drawRectOutline(pixels, w, h, x + 4, y + 2, cell / 3, cell / 3, entry.argb)
            if (options.showCodes) {
                val label = "${symbolOf[colorIdx]} ${entry.code}"
                drawTextLine(pixels, w, h, label, x + cell / 3 + 8, y + 2, TEXT_DARK, 1)
            }
        }

        if (ruler > 0) {
            drawRuler(
                pixels, w, h, ox, oy, cell, result.width, result.height,
                ruler, titleH, options.rowOffset,
            )
        }
        return RenderedBitmap(w, h, pixels)
    }

    /**
     * 符号分配的唯一入口：用量降序后循环取 SYMBOLS。
     *
     * 纯像素版 renderSymbol 与 Canvas 叠加版 drawSymbolLabels 必须共用它，
     * 否则扩符号表/改排序时两条路径会悄悄错位。
     */
    fun symbolAssignments(result: PatternResult): Map<Int, Char> {
        val map = LinkedHashMap<Int, Char>()
        result.usage().forEachIndexed { i, (colorIdx, _) ->
            map[colorIdx] = SYMBOLS[i % SYMBOLS.size]
        }
        return map
    }

    /**
     * 行列坐标带：四边都标，1-based。格子小于 18px 时按 10 格抽稀，
     * 保证打印出来仍能看清；主线用实心黑，区分于浅色网格。
     */
    private fun drawRuler(
        p: IntArray,
        w: Int,
        h: Int,
        ox: Int,
        oy: Int,
        cell: Int,
        cols: Int,
        rows: Int,
        ruler: Int,
        titleH: Int,
        rowOffset: Int,
    ) {
        val contentRight = ox + cols * cell
        val contentBottom = oy + rows * cell
        // 左/右/上/下四条坐标带。只包围图案区，不把符号版下方的对照表算进去。
        fillRect(p, w, h, 0, titleH, ruler, contentBottom + ruler - titleH, RULER_BG)
        fillRect(p, w, h, contentRight, titleH, ruler + 1, contentBottom + ruler - titleH, RULER_BG)
        fillRect(p, w, h, ox, titleH, w - ox, ruler, RULER_BG)
        fillRect(p, w, h, 0, contentBottom, w, ruler + 1, RULER_BG)
        // 坐标带外框
        drawVLineRange(p, w, h, ox, titleH, contentBottom + ruler, RULER_LINE)
        drawHLine(p, w, h, oy, RULER_LINE)
        drawVLineRange(p, w, h, contentRight, titleH, contentBottom + ruler, RULER_LINE)
        drawHLineRange(p, w, h, contentBottom, 0, w, RULER_LINE)

        // 参考图逐格标号；格子 ≥14px 时两位数仍放得下，就不再抽稀
        val step = if (cell >= 14) 1 else if (cell >= 8) 5 else 10
        val scale = (ruler * 0.55f / 7f).toInt().coerceIn(1, 3)
        val glyphH = 7 * scale
        for (gx in 0 until cols step step) {
            val label = (gx + 1).toString()
            val tw = label.length * 6 * scale - scale
            val x = ox + gx * cell + (cell - tw) / 2
            drawTextLine(
                p, w, h, label, x,
                titleH + (ruler - glyphH) / 2,
                RULER_LINE, scale,
            )
            drawVLineRange(
                p, w, h, ox + gx * cell,
                titleH, titleH + ruler, RULER_LINE,
            )
            drawTextLine(
                p, w, h, label, x,
                contentBottom + (ruler - glyphH) / 2,
                RULER_LINE, scale,
            )
            drawVLineRange(
                p, w, h, ox + gx * cell,
                contentBottom, contentBottom + ruler, RULER_LINE,
            )
        }
        for (gy in 0 until rows step step) {
            val label = (rowOffset + gy + 1).toString()
            val tw = label.length * 6 * scale - scale
            drawTextLine(
                p, w, h, label,
                (ruler - tw) / 2,
                oy + gy * cell + (cell - glyphH) / 2,
                RULER_LINE, scale,
            )
            drawTextLine(
                p, w, h, label,
                contentRight + (ruler - tw) / 2,
                oy + gy * cell + (cell - glyphH) / 2,
                RULER_LINE, scale,
            )
            drawHLineRange(
                p, w, h, oy + gy * cell,
                0, ox, RULER_LINE,
            )
            drawHLineRange(
                p, w, h, oy + gy * cell,
                contentRight, w, RULER_LINE,
            )
        }
    }

    // ---------- 基础绘图（纯像素操作） ----------

    private fun fillRect(p: IntArray, w: Int, h: Int, x: Int, y: Int, rw: Int, rh: Int, color: Int) {
        for (yy in y until (y + rh).coerceAtMost(h)) {
            if (yy < 0) continue
            val row = yy * w
            for (xx in x until (x + rw).coerceAtMost(w)) {
                if (xx < 0) continue
                p[row + xx] = color
            }
        }
    }

    private fun drawRectOutline(p: IntArray, w: Int, h: Int, x: Int, y: Int, rw: Int, rh: Int, color: Int) {
        for (xx in x until (x + rw).coerceAtMost(w)) {
            setPx(p, w, h, xx, y, color)
            setPx(p, w, h, xx, y + rh - 1, color)
        }
        for (yy in y until (y + rh).coerceAtMost(h)) {
            setPx(p, w, h, x, yy, color)
            setPx(p, w, h, x + rw - 1, yy, color)
        }
    }

    private fun setPx(p: IntArray, w: Int, h: Int, x: Int, y: Int, color: Int) {
        if (x in 0 until w && y in 0 until h) p[y * w + x] = color
    }

    private fun drawVLine(p: IntArray, w: Int, h: Int, x: Int, y0: Int, color: Int) {
        for (y in y0 until h) setPx(p, w, h, x, y, color)
    }

    private fun drawHLine(p: IntArray, w: Int, h: Int, y: Int, color: Int) {
        for (x in 0 until w) setPx(p, w, h, x, y, color)
    }

    private fun drawVLineRange(p: IntArray, w: Int, h: Int, x: Int, y0: Int, y1: Int, color: Int) {
        for (y in y0 until y1) setPx(p, w, h, x, y, color)
    }

    private fun drawHLineRange(p: IntArray, w: Int, h: Int, y: Int, x0: Int, x1: Int, color: Int) {
        for (x in x0 until x1) setPx(p, w, h, x, y, color)
    }

    private fun isLight(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return r * 299 + g * 587 + b * 114 > 128 * 1000
    }

    /** 绘制一行 ASCII 文本（内嵌 5x7 点阵，只覆盖色号常用字符集） */
    fun drawTextLine(p: IntArray, canvasW: Int, canvasH: Int, text: String, x: Int, y: Int, color: Int, scale: Int) {
        var cursor = x
        for (ch in text) {
            val glyph = Font5x7[ch]
            if (glyph != null) {
                for (row in 0 until 7) {
                    val bits = glyph[row]
                    for (col in 0 until 5) {
                        if (bits and (1 shl (4 - col)) != 0) {
                            for (dy in 0 until scale) {
                                for (dx in 0 until scale) {
                                    setPx(p, canvasW, canvasH, cursor + col * scale + dx, y + row * scale + dy, color)
                                }
                            }
                        }
                    }
                }
            }
            cursor += 6 * scale
        }
    }

    /** 5x7 点阵字体：数字、大写字母、常用符号 */
    private val Font5x7: Map<Char, IntArray> = buildMap {
        fun g(vararg rows: Int) = intArrayOf(*rows)
        put('0', g(0b01110, 0b10001, 0b10011, 0b10101, 0b11001, 0b10001, 0b01110))
        put('1', g(0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110))
        put('2', g(0b01110, 0b10001, 0b00001, 0b00010, 0b00100, 0b01000, 0b11111))
        put('3', g(0b11111, 0b00010, 0b00100, 0b00010, 0b00001, 0b10001, 0b01110))
        put('4', g(0b00010, 0b00110, 0b01010, 0b10010, 0b11111, 0b00010, 0b00010))
        put('5', g(0b11111, 0b10000, 0b11110, 0b00001, 0b00001, 0b10001, 0b01110))
        put('6', g(0b00110, 0b01000, 0b10000, 0b11110, 0b10001, 0b10001, 0b01110))
        put('7', g(0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b01000, 0b01000))
        put('8', g(0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b01110))
        put('9', g(0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00010, 0b01100))
        put('A', g(0b01110, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001))
        put('B', g(0b11110, 0b10001, 0b10001, 0b11110, 0b10001, 0b10001, 0b11110))
        put('C', g(0b01110, 0b10001, 0b10000, 0b10000, 0b10000, 0b10001, 0b01110))
        put('D', g(0b11110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b11110))
        put('E', g(0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b11111))
        put('F', g(0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b10000))
        put('G', g(0b01110, 0b10001, 0b10000, 0b10111, 0b10001, 0b10001, 0b01111))
        put('H', g(0b10001, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001))
        put('I', g(0b01110, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110))
        put('J', g(0b00111, 0b00010, 0b00010, 0b00010, 0b00010, 0b10010, 0b01100))
        put('K', g(0b10001, 0b10010, 0b10100, 0b11000, 0b10100, 0b10010, 0b10001))
        put('L', g(0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b11111))
        put('M', g(0b10001, 0b11011, 0b10101, 0b10101, 0b10001, 0b10001, 0b10001))
        put('N', g(0b10001, 0b10001, 0b11001, 0b10101, 0b10011, 0b10001, 0b10001))
        put('O', g(0b01110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110))
        put('P', g(0b11110, 0b10001, 0b10001, 0b11110, 0b10000, 0b10000, 0b10000))
        put('Q', g(0b01110, 0b10001, 0b10001, 0b10001, 0b10101, 0b10010, 0b01101))
        put('R', g(0b11110, 0b10001, 0b10001, 0b11110, 0b10100, 0b10010, 0b10001))
        put('S', g(0b01111, 0b10000, 0b10000, 0b01110, 0b00001, 0b00001, 0b11110))
        put('T', g(0b11111, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100))
        put('U', g(0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110))
        put('V', g(0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01010, 0b00100))
        put('W', g(0b10001, 0b10001, 0b10001, 0b10101, 0b10101, 0b11011, 0b10001))
        put('X', g(0b10001, 0b10001, 0b01010, 0b00100, 0b01010, 0b10001, 0b10001))
        put('Y', g(0b10001, 0b10001, 0b01010, 0b00100, 0b00100, 0b00100, 0b00100))
        put('Z', g(0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b10000, 0b11111))
        put(' ', g(0, 0, 0, 0, 0, 0, 0))
        put('-', g(0, 0, 0, 0b11111, 0, 0, 0))
        put(':', g(0, 0b00100, 0, 0, 0, 0b00100, 0))
        put('.', g(0, 0, 0, 0, 0, 0b00100, 0b00100))
        put('/', g(0b00001, 0b00010, 0b00100, 0b01000, 0b10000, 0, 0))
        put('#', g(0b01010, 0b11111, 0b01010, 0b01010, 0b11111, 0b01010, 0))
    }
}
