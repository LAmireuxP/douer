package com.pindou.app.core.export

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import com.pindou.app.core.pattern.CodeLabelMode
import com.pindou.app.core.pattern.ColorLabelLayout
import com.pindou.app.core.pattern.PatternResult
import java.io.File
import java.io.FileOutputStream

/**
 * 导出落地：把渲染结果写成 PNG / PDF 文件并通过 FileProvider 分享。
 * 文件名规则：拼豆_<项目名>_<尺寸>_<时间戳>.<ext>
 */
object ExportService {

    /** PDF 分页模式：ROWS 按可用高度均分行；BOARDS 每页固定一块 29×29 拼装底板。 */
    enum class PageMode { ROWS, BOARDS }

    /** BOARDS 分页的底板边长；与施工引导的物理底板口径一致，两处需同步改。 */
    const val BOARD_SIDE = BoardPdfLayout.SIDE

    sealed interface Exported {
        data class FileReady(val file: File, val uri: Uri) : Exported
        data class Failed(val message: String) : Exported
    }

    /** 渲染并保存 PNG（pattern=彩色带色号，symbol=符号版） */
    fun exportPng(
        context: Context,
        results: List<PatternResult>,
        projectName: String,
        symbol: Boolean = false,
        codeLabels: CodeLabelMode = CodeLabelMode.FULL,
    ): Exported = runCatching {
        require(results.isNotEmpty()) { "无页数据" }
        // 每页独立渲染（含标题栏），纵向拼接成一张长图
        val cellPx = pngCellPx(results)
        val parts = results.map { r ->
            val titleHeight = if (symbol) 0 else titleBarHeight(cellPx)
            val rendered = if (symbol) {
                ExportRenderer.renderSymbol(
                    r,
                    ExportRenderer.RenderOptions(
                        cellPx = cellPx,
                        showCodes = false,
                        showRuler = true,
                    ),
                )
            } else {
                ExportRenderer.renderPattern(
                    r,
                    ExportRenderer.RenderOptions(
                        cellPx = cellPx,
                        // 色号不走位图点阵字：位图字体只有 7px 高，放大后仍糊，
                        // 统一改由 Android Canvas 用系统字体在最终分辨率上绘制
                        showCodes = false,
                        showGrid = true,
                        showRuler = true,
                        titleHeightPx = titleHeight,
                    ),
                )
            }
            val bmp = rendered.toBitmap()
            if (symbol) {
                drawSymbolLabels(bmp, r, cellPx, ExportRenderer.rulerSizePx(cellPx))
                bmp
            } else {
                drawCompactHeader(
                    bmp, titleHeight, projectName, r,
                    results.first().palette.beadSize.pitchMm,
                    ExportRenderer.rulerSizePx(cellPx), cellPx,
                )
                drawCellCodes(
                    bmp, r, cellPx, titleHeight,
                    ExportRenderer.rulerSizePx(cellPx),
                    codeLabels,
                )
                addLegend(bmp, r, cellPx)
            }
        }
        val name = fileName(results.first(), if (symbol) "symbol" else "pattern", "png")
        val output = if (parts.size == 1) {
            parts[0]
        } else {
            val totalHeight = parts.sumOf { it.height }
            createBitmap(parts[0].width, totalHeight).also { combined ->
                val canvas = Canvas(combined)
                var y = 0
                for (part in parts) {
                    canvas.drawBitmap(part, 0f, y.toFloat(), null)
                    y += part.height
                    part.recycle()
                }
            }
        }
        val uri = saveToDownloads(context, output, name, "image/png")
        output.recycle()
        Exported.FileReady(File(name), uri)
    }.getOrElse { Exported.Failed(it.message ?: "导出失败") }

    /** 单页导出（多页版本委托入口） */
    fun exportPng(
        context: Context,
        result: PatternResult,
        projectName: String,
        symbol: Boolean = false,
        codeLabels: CodeLabelMode = CodeLabelMode.FULL,
    ): Exported = exportPng(context, listOf(result), projectName, symbol, codeLabels)

    /** 导出 .bpat 图纸文件（通用交换格式） */
    fun exportBpat(
        context: Context,
        jsonText: String,
        result: PatternResult,
    ): Exported = runCatching {
        val name = fileName(result, "bpat", "bpat")
        val uri = saveBytesToDownloads(
            context,
            jsonText.toByteArray(Charsets.UTF_8),
            name,
            "application/octet-stream",
        )
        Exported.FileReady(File(name), uri)
    }.getOrElse { Exported.Failed(it.message ?: "bpat 导出失败") }

    /**
     * 导出 CSV 采购清单（UTF-8 BOM）。
     *
     * BOM 是给 Excel/WPS 看的：没有它，双击打开中文表头会变乱码；
     * 表头只有四列，商家按「色号 + 颗数」直接下单。
     */
    fun exportCsv(
        context: Context,
        results: List<PatternResult>,
        projectName: String,
    ): Exported = runCatching {
        require(results.isNotEmpty()) { "无页数据" }
        val text = ShoppingList.renderCsv(results, projectName)
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            text.toByteArray(Charsets.UTF_8)
        val name = fileName(results.first(), "list", "csv")
        val uri = saveBytesToDownloads(context, bytes, name, "text/csv")
        Exported.FileReady(File(name), uri)
    }.getOrElse { Exported.Failed(it.message ?: "CSV 导出失败") }

    fun exportCsv(
        context: Context,
        result: PatternResult,
        projectName: String,
    ): Exported = exportCsv(context, listOf(result), projectName)

    /**
     * 画像素工作区的位图导出入口：调用方已经按最终分辨率渲染好，
     * 这里只负责落 Downloads 并返回可分享的 Uri。
     */
    fun savePng(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
    ): Exported = runCatching {
        val uri = saveToDownloads(context, bitmap, displayName, "image/png")
        Exported.FileReady(File(displayName), uri)
    }.getOrElse { Exported.Failed(it.message ?: "PNG 导出失败") }

    /** GIF / 色表等非位图导出。 */
    fun saveBytes(
        context: Context,
        bytes: ByteArray,
        displayName: String,
        mime: String,
    ): Exported = runCatching {
        val uri = saveBytesToDownloads(context, bytes, displayName, mime)
        Exported.FileReady(File(displayName), uri)
    }.getOrElse { Exported.Failed(it.message ?: "文件导出失败") }

    /**
     * API 26–28 写公共下载目录需要运行时存储权限；29+ 走 MediaStore 不需要。
     * 导出前由 UI 层调用本方法决定是否先申请权限。
     */
    fun needsLegacyStoragePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED

    /**
     * 保存到系统「下载」根目录（用户能在文件管理器里直接看到，且不会被缓存清理）。
     * API 29+ 走 MediaStore；26–28 直接写公共 Download，需已授予存储权限。
     */
    private fun saveToDownloads(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        mime: String,
    ): Uri {
        return if (android.os.Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
                // 直接落 Download 根目录；不再建「拼豆图纸」子文件夹
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                // 写完前对其他应用不可见，避免相册/文件管理器索引到半截文件
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建下载文件")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: error("无法写入下载文件")
                val done = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, done, null, null)
            } catch (t: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw t
            }
            uri
        } else {
            val dir = legacyDownloadsDir()
            val file = File(dir, displayName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            uriFor(context, file)
        }
    }

    private fun saveBytesToDownloads(
        context: Context,
        bytes: ByteArray,
        displayName: String,
        mime: String,
    ): Uri {
        return if (android.os.Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建下载文件")
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("无法写入下载文件")
                val done = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, done, null, null)
            } catch (t: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw t
            }
            uri
        } else {
            val dir = legacyDownloadsDir()
            val file = File(dir, displayName)
            file.writeBytes(bytes)
            uriFor(context, file)
        }
    }

    /** API 26–28 的公共下载目录；无权限时给出明确失败信息，避免静默落到应用私有目录 */
    private fun legacyDownloadsDir(): File {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        if (!dir.isDirectory || !dir.canWrite()) {
            error("没有写入系统下载目录的权限，请在系统设置中允许存储权限后重试")
        }
        return dir
    }

    /** 导出 A4 分页 PDF：图纸分页 + 清单页 */
    fun exportPdf(
        context: Context,
        result: PatternResult,
        projectName: String,
        codeLabels: CodeLabelMode = CodeLabelMode.FULL,
        pageMode: PageMode = PageMode.ROWS,
    ): Exported = exportPdf(context, listOf(result), projectName, codeLabels, pageMode)

    /** 多页 PDF：每页图纸独立渲染切分，末尾附聚合清单页 */
    fun exportPdf(
        context: Context,
        results: List<PatternResult>,
        projectName: String,
        codeLabels: CodeLabelMode = CodeLabelMode.FULL,
        pageMode: PageMode = PageMode.ROWS,
    ): Exported = runCatching {
        require(results.isNotEmpty()) { "无页数据" }

        // A4 @ 72dpi = 595 x 842 pt；宽图纸用横向，格子能放大约 45%，
        // 打印出来的色号才不是一排小点（纸张方向随图纸长宽比自动选）。
        val portraitW = 595
        val portraitH = 842
        // 打印机通常有 5mm 左右不可打印边；24pt ≈ 8.5mm，宁可多留一点，
        // 也不要把坐标数字压在纸边被裁掉。
        val margin = 24f
        val doc = PdfDocument()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        var docPageIndex = 0
        for ((idx, r) in results.withIndex()) {
            val cellPx = pdfCellPx(r)
            val ruler = ExportRenderer.rulerSizePx(cellPx)
            val layoutW = ruler * 2 + r.width * cellPx + 1
            // PDF 内嵌的是位图：按 A4 宽度缩放后 1 格只剩 2~3pt，
            // 用屏幕档 cellPx 打印出来色号必然糊。这里按打印分辨率单独放大，
            // 再按整页可用宽高计算每页行数；不直接切长图，避免第二页没有坐标。
            val titleHeight = titleBarHeight(cellPx)
            val legendH = legendHeightPx(r.usage().size, cellPx)
            val boardsX = BoardPdfLayout.boardsX(r.width)
            val boardsY = BoardPdfLayout.boardsY(r.height)
            fun plan(pageW: Int, pageH: Int): PagePlan {
                val usableW = pageW - margin * 2f
                val usableH = pageH - margin * 2f
                val widthScale = usableW / layoutW
                val rowAreaPx =
                    usableH / widthScale - titleHeight - ruler * 2 - 1f - legendH
                val rows = (rowAreaPx / cellPx).toInt().coerceAtLeast(1)
                val pages = (r.height + rows - 1) / rows
                return PagePlan(rows, pages, cellPx * widthScale)
            }
            // BOARDS：每页固定一块 29×29 底板；行数由板边界与可用高度共同决定。
            // 页号 = 板号，打印后把纸放在对应物理板旁边逐板拼。
            fun planBoards(pageW: Int, pageH: Int): PagePlan {
                val usableW = pageW - margin * 2f
                val widthScale = usableW / layoutW
                // 每页固定完整的一块板，渲染时再按纸张等比缩放；
                // 不能按 legendH 截断行数，否则会漏掉板内下半部分。
                val rows = minOf(BOARD_SIDE, r.height)
                return PagePlan(rows, boardsX * boardsY, cellPx * widthScale)
            }

            val landscape = when (pageMode) {
                PageMode.ROWS -> {
                    val portraitPlan = plan(portraitW, portraitH)
                    val landscapePlan = plan(portraitH, portraitW)
                    val portraitOnePage = portraitPlan.pages == 1 && portraitPlan.cellPt >= 5f
                    val landscapeOnePage = landscapePlan.pages == 1 && landscapePlan.cellPt >= 5f
                    when {
                        // 能一页放下时优先一页，和参考图的单张图纸一致；
                        // 两种方向都能一页时选格子更大的那一种。
                        portraitOnePage && landscapeOnePage ->
                            landscapePlan.cellPt > portraitPlan.cellPt
                        portraitOnePage -> false
                        landscapeOnePage -> true
                        // 都要分页时选页数少的；页数相同选格子大的。
                        portraitPlan.pages != landscapePlan.pages ->
                            portraitPlan.pages < landscapePlan.pages
                        else -> landscapePlan.cellPt > portraitPlan.cellPt
                    }
                }
                PageMode.BOARDS -> {
                    val portraitBoardPlan = planBoards(portraitW, portraitH)
                    val landscapeBoardPlan = planBoards(portraitH, portraitW)
                    when {
                        portraitBoardPlan.pages == 1 -> false
                        landscapeBoardPlan.pages == 1 -> true
                        // 两方向都放得下时选格子更大的；否则选能放下的那个。
                        else -> landscapeBoardPlan.cellPt > portraitBoardPlan.cellPt
                    }
                }
            }
            val plan = when (pageMode) {
                PageMode.ROWS -> if (landscape) plan(portraitH, portraitW) else plan(portraitW, portraitH)
                PageMode.BOARDS -> if (landscape) planBoards(portraitH, portraitW) else planBoards(portraitW, portraitH)
            }
            val pageW = if (landscape) portraitH else portraitW
            val pageH = if (landscape) portraitW else portraitH
            val usableW = pageW - margin * 2f
            val usableH = pageH - margin * 2f
            val rowsPerPage = plan.rowsPerPage
            val sheetCount = plan.pages
            // ROWS：最后一页只剩两三行时很难看，把总行数平均分到各页；
            // BOARDS：页 = 板，行高固定板高，列区间随板横向平移。
            val baseRows = if (pageMode == PageMode.ROWS) r.height / sheetCount else 0
            val extraRows = if (pageMode == PageMode.ROWS) r.height % sheetCount else 0
            var nextRow = 0

            for (sheet in 0 until sheetCount) {
                val rowStart: Int
                val rowCount: Int
                val colStart: Int
                if (pageMode == PageMode.BOARDS) {
                    rowStart = BoardPdfLayout.rowStart(r.width, r.height, sheet)
                    rowCount = BoardPdfLayout.rowCount(r.width, r.height, sheet)
                    colStart = BoardPdfLayout.colStart(r.width, sheet)
                } else {
                    rowStart = nextRow
                    rowCount = baseRows + if (sheet < extraRows) 1 else 0
                    nextRow += rowCount
                    colStart = 0
                }
                val colCount = if (pageMode == PageMode.BOARDS) {
                    BoardPdfLayout.colCount(r.width, sheet)
                } else r.width
                val slice = PatternResult(
                    colCount,
                    rowCount,
                    IntArray(rowCount * colCount) { i ->
                        val gy = rowStart + i / colCount
                        val gx = colStart + i % colCount
                        r.cells[gy * r.width + gx]
                    },
                    r.palette,
                    r.elapsedMs,
                )
                val patternBmp = ExportRenderer
                    .renderPattern(
                        slice,
                        ExportRenderer.RenderOptions(
                            cellPx = cellPx,
                            showCodes = false,
                            showGrid = true,
                            showRuler = true,
                            rowOffset = rowStart,
                            colOffset = if (pageMode == PageMode.BOARDS) -colStart else 0,
                            titleHeightPx = titleHeight,
                        ),
                    )
                    .toBitmap()
                // 标题副标题按本页实际渲染的切片统计；BOARDS 时就是单板尺寸/色数/颗数
                val headerResult = slice
                val headerTitle = if (pageMode == PageMode.BOARDS && sheetCount > 1) {
                    "$projectName · 第 ${sheet + 1}/$sheetCount 板"
                } else {
                    projectName
                }
                drawCompactHeader(
                    patternBmp, titleHeight, headerTitle, headerResult,
                    r.palette.beadSize.pitchMm, ruler, cellPx,
                )
                drawCellCodes(patternBmp, slice, cellPx, titleHeight, ruler, codeLabels)
                val pageBmp = if (pageMode == PageMode.ROWS && sheet == sheetCount - 1) {
                    addLegend(patternBmp, r, cellPx)
                } else {
                    patternBmp
                }

                val contentW = pageBmp.width
                val contentH = pageBmp.height
                // 按整页宽度定标，再防止极端比例页面高度溢出。
                val scale = minOf(usableW / contentW, usableH / contentH)
                val destW = contentW * scale
                val destH = contentH * scale
                val left = margin + (usableW - destW) / 2f
                // 单页放得下时上下居中，避免图纸全挤在纸面上半部分
                val top = margin + (usableH - destH) / 2f
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, docPageIndex + 1).create())
                val c: Canvas = page.canvas
                c.drawBitmap(
                    pageBmp,
                    null,
                    android.graphics.RectF(left, top, left + destW, top + destH),
                    paint,
                )
                doc.finishPage(page)
                if (pageBmp !== patternBmp) patternBmp.recycle()
                pageBmp.recycle()
                docPageIndex++
            }
        }

        val pdfBytes = java.io.ByteArrayOutputStream().use { bos ->
            doc.writeTo(bos)
            bos.toByteArray()
        }
        doc.close()
        val name = fileName(results.first(), "print", "pdf")
        val uri = saveBytesToDownloads(context, pdfBytes, name, "application/pdf")
        Exported.FileReady(File(name), uri)
    }.getOrElse { Exported.Failed(it.message ?: "PDF 导出失败") }

    /**
     * 两行标题：粗体项目名 + 尺寸/色数/颗数副标题，左对齐。
     * 点阵字体没有中文字形，所以这里用系统字体在预留高度里绘制。
     */
    private fun drawCompactHeader(
        bitmap: Bitmap,
        titleHeight: Int,
        projectName: String,
        r: PatternResult,
        pitchMm: Double,
        ruler: Int,
        cell: Int,
    ) {
        if (titleHeight <= 0) return
        val canvas = Canvas(bitmap)
        val left = ruler + cell * 0.35f
        val maxWidth = bitmap.width - left - ruler
        val title = projectName.ifBlank { "拼豆图纸" }
        val cm = physicalCm(r.width, r.height, pitchMm)
        val subtitle = "${r.width}×${r.height}（$cm）/${r.usage().size}色/共" +
            "${r.cells.count { it >= 0 }}颗"

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = 0xFF111111.toInt()
            isFakeBoldText = true
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        var titleSize = (titleHeight * 0.42f).coerceAtLeast(13f)
        titlePaint.textSize = titleSize
        while (titleSize > 10f && titlePaint.measureText(title) > maxWidth) {
            titleSize *= 0.94f
            titlePaint.textSize = titleSize
        }
        val titleBaseline = titleHeight * 0.20f - titlePaint.ascent()
        canvas.drawText(title, left, titleBaseline, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = 0xFF666666.toInt()
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
            textSize = (titleHeight * 0.21f).coerceAtLeast(10f)
        }
        while (subPaint.textSize > 9f && subPaint.measureText(subtitle) > maxWidth) {
            subPaint.textSize *= 0.94f
        }
        canvas.drawText(subtitle, left, titleHeight * 0.90f, subPaint)
    }

    /** 实物尺寸（宽×高，cm）。按豆子中心距换算，四舍五入到 0.1cm。 */
    fun physicalCm(cols: Int, rows: Int, pitchMm: Double): String {
        if (pitchMm <= 0.0 || cols <= 0 || rows <= 0) return "—"
        val w = cols * pitchMm / 10.0
        val h = rows * pitchMm / 10.0
        return "${"%.1f".format(w)}×${"%.1f".format(h)}cm"
    }

    /** 底部用量条：白底黑框，色块 + 色号 + 颗数；颜色少时 4 列更接近参考图。 */
    private fun legendHeightPx(colorCount: Int, cellPx: Int): Int {
        if (colorCount <= 0) return 0
        val rowH = cellPx.coerceIn(32, 120)
        val cols = legendColumns(colorCount)
        val rows = (colorCount + cols - 1) / cols
        return rows * rowH + (rowH * 0.6f).toInt()
    }

    private fun addLegend(source: Bitmap, r: PatternResult, cellPx: Int): Bitmap {
        val usage = r.usage()
        if (usage.isEmpty()) return source
        val rowH = cellPx.coerceIn(32, 120)
        val legendH = legendHeightPx(usage.size, cellPx)
        val out = createBitmap(source.width, source.height + legendH)
        val canvas = Canvas(out)
        canvas.drawColor(0xFFFFFFFF.toInt())
        canvas.drawBitmap(source, 0f, 0f, null)

        val top = source.height.toFloat()
        val line = Paint().apply {
            color = 0xFF1A1A1A.toInt()
            strokeWidth = 1.2f
        }
        canvas.drawLine(0f, top, out.width.toFloat(), top, line)

        val cols = legendColumns(usage.size)
        val entryW = out.width.toFloat() / cols
        val codePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = 0xFF111111.toInt()
            textSize = rowH * 0.36f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            isFakeBoldText = true
        }
        val countPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = 0xFF666666.toInt()
            textSize = rowH * 0.32f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1A1A1A.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
        }
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        usage.forEachIndexed { i, (idx, count) ->
            val entry = r.palette.colors.getOrNull(idx) ?: return@forEachIndexed
            val col = i % cols
            val row = i / cols
            val pad = entryW * 0.07f
            val left = col * entryW + pad
            val right = (col + 1) * entryW - pad
            val chipTop = top + rowH * 0.15f + row * rowH
            val chipBottom = chipTop + rowH * 0.70f
            val chip = android.graphics.RectF(left, chipTop, right, chipBottom)
            val radius = rowH * 0.10f
            canvas.drawRoundRect(chip, radius, radius, white)
            canvas.drawRoundRect(chip, radius, radius, border)

            val swatch = rowH * 0.34f
            val swatchLeft = left + rowH * 0.12f
            val swatchTop = (chipTop + chipBottom) / 2f - swatch / 2f
            val swatchRect = android.graphics.RectF(
                swatchLeft, swatchTop, swatchLeft + swatch, swatchTop + swatch,
            )
            canvas.drawRoundRect(
                swatchRect,
                3f,
                3f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = entry.argb },
            )
            canvas.drawRoundRect(
                swatchRect,
                3f,
                3f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x40000000
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                },
            )
            val textLeft = swatchLeft + swatch + rowH * 0.12f
            val baseline = (chipTop + chipBottom) / 2f -
                (codePaint.ascent() + codePaint.descent()) / 2f
            canvas.drawText(
                entry.code,
                textLeft,
                baseline,
                codePaint,
            )
            val codeWidth = codePaint.measureText(entry.code)
            canvas.drawText(
                count.toString(),
                textLeft + codeWidth + rowH * 0.10f,
                baseline,
                countPaint,
            )
        }
        source.recycle()
        return out
    }

    /**
     * 色号标注：系统字体 + 抗锯齿，在最终像素分辨率上绘制。
     * 字号按格子高度 46% 取，宽度超格再等比缩；底色深浅自动切黑/白字。
     * 复杂度 O(格数)，78×70 约 5500 次 drawText，几十毫秒。
     */
    private fun drawCellCodes(
        bitmap: Bitmap,
        r: PatternResult,
        cell: Int,
        titleHeight: Int,
        ruler: Int,
        codeLabels: CodeLabelMode,
    ) {
        if (cell < 16) return
        val canvas = Canvas(bitmap)
        val outline = if (codeLabels == CodeLabelMode.OUTLINE) {
            ColorLabelLayout.labelMaskOutline(r.cells, r.width, r.height)
        } else {
            null
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        // 46% 格高是「两字符刚好不贴边」的上限；再大就会压到网格线。
        // 宽度按 measureText 收敛，色号 3 字符（如 A01 之外的 3 位码）自动缩字。
        val baseSize = cell * 0.46f
        val maxWidth = cell * 0.88f
        for (gy in 0 until r.height) {
            for (gx in 0 until r.width) {
                val idx = r.cells[gy * r.width + gx]
                if (idx < 0) continue
                if (outline != null && !outline[gy * r.width + gx]) continue
                val color = r.palette.colors[idx]
                paint.textSize = baseSize
                val measured = paint.measureText(color.code)
                if (measured > maxWidth) {
                    paint.textSize = baseSize * maxWidth / measured
                }
                paint.color = codeTextColor(color.argb)
                val cx = ruler + gx * cell + cell / 2f
                val cy = titleHeight + ruler + gy * cell + cell / 2f
                // 基线按字体度量居中，避免不同字号的数字上下偏移
                val baseline = cy - (paint.ascent() + paint.descent()) / 2f
                canvas.drawText(color.code, cx, baseline, paint)
            }
        }
    }

    /**
     * 符号版标注：格子符号（大）+ 色号（小）+ 底部对照表，同样走系统字体。
     * 符号分配规则必须与 ExportRenderer.renderSymbol 完全一致：
     * usage() 按用量降序，第 i 个颜色拿 SYMBOLS[i % size]。
     */
    private fun drawSymbolLabels(bitmap: Bitmap, r: PatternResult, cell: Int, ruler: Int) {
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val symbolOf = ExportRenderer.symbolAssignments(r)

        // 标题
        paint.textAlign = Paint.Align.LEFT
        paint.color = 0xFF333333.toInt()
        paint.textSize = (cell * 0.55f).coerceAtLeast(14f)
        paint.isFakeBoldText = true
        canvas.drawText("SYMBOL CHART", ruler + cell * 0.25f, cell * 0.68f, paint)
        paint.isFakeBoldText = false

        // 每格：符号居中偏上，色号贴底
        val titleH = cell
        val symSize = cell * 0.52f
        val codeSize = (cell * 0.26f).coerceAtLeast(9f)
        for (gy in 0 until r.height) {
            for (gx in 0 until r.width) {
                val idx = r.cells[gy * r.width + gx]
                if (idx < 0) continue
                val sym = symbolOf[idx] ?: continue
                val cx = ruler + gx * cell + cell / 2f
                val top = titleH + ruler + gy * cell
                paint.textAlign = Paint.Align.CENTER
                paint.color = 0xFF333333.toInt()
                paint.textSize = symSize
                canvas.drawText(sym.toString(), cx, top + cell * 0.58f, paint)
                if (cell >= 24) {
                    paint.textSize = codeSize
                    paint.color = 0xFF6A6A6A.toInt()
                    canvas.drawText(
                        r.palette.colors[idx].code,
                        cx,
                        top + cell - cell * 0.12f,
                        paint,
                    )
                }
            }
        }

        // 对照表：4 列，行高 cell/2，与 renderSymbol 布局一致
        val legendTop = titleH + ruler * 2 + r.height * cell + cell / 2
        val colW = r.width * cell / 4
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = (cell * 0.32f).coerceAtLeast(10f)
        paint.color = 0xFF333333.toInt()
        symbolOf.keys.forEachIndexed { i, colorIdx ->
            val col = i % 4
            val row = i / 4
            val x = ruler + col * colW + cell / 3 + 8f
            val y = legendTop + row * (cell / 2)
            canvas.drawText(
                "${symbolOf[colorIdx]} ${r.palette.colors[colorIdx].code}",
                x,
                y + cell * 0.34f,
                paint,
            )
        }
    }

    /** 分享纯文本清单 */
    fun shareList(context: Context, result: PatternResult, projectName: String) {
        shareList(context, listOf(result), projectName)
    }

    /** 分享多页聚合清单 */
    fun shareList(context: Context, results: List<PatternResult>, projectName: String) {
        val text = ShoppingList.render(results, projectName)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "拼豆采购清单 · $projectName")
        }
        context.startActivity(Intent.createChooser(intent, "分享采购清单"))
    }

    /** 分享已导出的文件 */
    fun shareFile(context: Context, exported: Exported.FileReady) {
        val mime = when (exported.file.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "bpat" -> "application/octet-stream"
            "csv" -> "text/csv"
            else -> "image/png"
        }
        val shareUri = shareUriFor(context, exported)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, shareUri)
            // 部分阅读器只认 FileProvider，不认 MediaStore 的 content://media/...
            // ClipData 同时保证接收方拿到读权限。
            clipData = ClipData.newUri(context.contentResolver, exported.file.name, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享图纸"))
    }

    /**
     * MediaStore 的 Downloads URI 在夸克/QQ 等第三方阅读器里会报“无效的文件路径”。
     * 分享前复制一份到 cache，用 FileProvider URI 发出去；下载目录里的原文件保留。
     */
    private fun shareUriFor(context: Context, exported: Exported.FileReady): Uri {
        if (exported.uri.authority != MediaStore.AUTHORITY) return exported.uri
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        // 分享缓存只保留本次副本，避免每次分享都留下一个几十 MB 的旧文件
        runCatching { dir.listFiles()?.forEach { it.delete() } }
        val local = File(dir, exported.file.name)
        return runCatching {
            context.contentResolver.openInputStream(exported.uri)?.use { input ->
                FileOutputStream(local).use { output -> input.copyTo(output) }
            } ?: error("无法读取导出文件")
            uriFor(context, local)
        }.getOrElse { exported.uri }
    }

    /**
     * PNG 导出分辨率：先按图纸宽度给高倍率，再用总像素预算收敛。
     * 32M 像素/整张 ≈ 128MB/份；单页直接压缩，多页才额外拼一张，
     * 配合 largeHeap 留出足够余量，同时保证常见图纸比旧版清晰 1.5~2 倍。
     */
    private fun pngCellPx(results: List<PatternResult>): Int {
        val maxWidth = results.maxOf { it.width }
        var cell = when {
            maxWidth <= 32 -> 176
            maxWidth <= 64 -> 120
            maxWidth <= 100 -> 96
            else -> 64
        }
        while (cell > 8 && results.sumOf { estimatePngArea(it, cell) } > MAX_PNG_PIXELS) {
            cell -= 4
        }
        return cell
    }

    private fun estimatePngArea(r: PatternResult, cell: Int): Long {
        val ruler = ExportRenderer.rulerSizePx(cell)
        val w = ruler * 2L + r.width.toLong() * cell + 1
        val h = titleBarHeight(cell) + ruler * 2L + r.height.toLong() * cell + 1 +
            legendHeightPx(r.usage().size, cell)
        return w * h
    }

    /** PDF 专用：比屏幕档再大一截，抵消 A4 缩放 */
    private fun pdfCellPx(result: PatternResult): Int {
        val base = when {
            result.width <= 32 -> 120
            result.width <= 64 -> 84
            result.width <= 100 -> 64
            else -> 44
        }
        // 整页位图峰值 ≈ 2 × w × h × 4B（渲染缓冲 + Bitmap）。
        // 133×189 在 44px 下约 200MB，低内存机必 OOM；按面积预算逐级降格，
        // 大图宁可字号小一点，也不要把导出做成闪退。
        var cell = base
        while (cell > 8) {
            val ruler = ExportRenderer.rulerSizePx(cell)
            val w = ruler * 2L + result.width.toLong() * cell + 1
            val h = ruler * 2L + result.height.toLong() * cell + 1 + titleBarHeight(cell)
            if (w * h <= MAX_PDF_BITMAP_PIXELS) break
            cell -= 4
        }
        return cell
    }

    private fun titleBarHeight(cell: Int): Int = (cell * 2.0f).toInt().coerceAtLeast(48)

    private data class PagePlan(val rowsPerPage: Int, val pages: Int, val cellPt: Float)

    /** 参考图 4 列；颜色多时 6 列，避免图例比图纸还高 */
    private fun legendColumns(colorCount: Int): Int = if (colorCount <= 12) 4 else 6
    /** PNG 总像素上限；largeHeap 下 32M 像素 ≈ 128MB/份，单页峰值约 256MB。 */
    private const val MAX_PNG_PIXELS = 32_000_000L
    /** PDF 单张位图上限：25M 像素 ≈ 100MB/张，渲染 + Bitmap 两份约 200MB。 */
    private const val MAX_PDF_BITMAP_PIXELS = 25_000_000L

    /**
     * 色号字色：按 WCAG 相对亮度取黑/白里对比度更高的一方。
     * 直接拿 0.5 亮度切会出现「中灰底 + 深灰字」这种 2:1 的低对比组合。
     */
    private fun codeTextColor(argb: Int): Int {
        fun lin(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        val r = lin((argb shr 16) and 0xFF)
        val g = lin((argb shr 8) and 0xFF)
        val b = lin(argb and 0xFF)
        val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
        // 与黑/白的对比度：(L+0.05)/0.05 与 1.05/(L+0.05)，分界 L≈0.179
        return if (lum > 0.179) 0xFF1C1C1E.toInt() else 0xFFF7F7F7.toInt()
    }

    private fun fileName(result: PatternResult, kind: String, ext: String): String {
        val ts = System.currentTimeMillis() / 1000
        return "拼豆_${result.width}x${result.height}_$kind$ts.$ext"
    }

    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun ExportRenderer.RenderedBitmap.toBitmap(): Bitmap {
        val bmp = createBitmap(width, height)
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmp
    }
}
