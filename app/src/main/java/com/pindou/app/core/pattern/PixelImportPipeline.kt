package com.pindou.app.core.pattern

import com.pindou.app.core.image.TransformedImage
import com.pindou.app.core.palette.BeadPalette
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 像素图导入的单一转换入口。
 *
 * 校准页的「看图纸」和顶部「完成」必须来自同一次计算：之前预览用的是
 * 逐格原始采样色，完成时又重跑一遍 refine + 色卡量化，两边的网格、空格、
 * 色号都可能不同，用户看到 A、生成出 B。
 *
 * 这里把「网格收进图内 → 裁图 → 按画像素网格生成」固定成一条路径：
 * 预览直接铺 [Conversion.result] 的色号，完成时复用同一个结果，
 * 不再二次 refine / 二次采样。纯 Kotlin，可在任意线程调用。
 */
object PixelImportPipeline {

    /**
     * 进入校准页时准备好的网格。
     *
     * [applied] 表示智能对齐真的改动了网格；[detected] 表示自动检测
     * 没有落到兜底网格，UI 用它区分「自动识别」与「手动网格」。
     */
    data class Prepared(
        val grid: PixelCalibration.Grid,
        val method: PixelGridInspector.Method,
        val detected: Boolean,
        val score: Float,
        val applied: Boolean,
    )

    /**
     * 一次完整的转换结果：裁出的原图、图纸色号、预览像素。
     *
     * [previewArgb] 与 [result] 一一对应，空格为 0。UI 只读它，
     * 不再自己按格采样，避免出现两套颜色。
     */
    data class Conversion(
        val grid: PixelCalibration.Grid,
        val cropped: TransformedImage,
        val result: PatternResult,
        val previewArgb: IntArray,
        /** 收掉四周空白格后的最终图纸；确认落库/进编辑器用这一份 */
        val finalGrid: PixelCalibration.Grid = grid,
        val finalCropped: TransformedImage = cropped,
        val finalResult: PatternResult = result,
    )

    /**
     * 检测 → 智能吸附 → 收进图内。
     *
     * 智能对齐默认开启，所以这里必须在进页面之前就执行 [PixelCalibration.refine]；
     * 只把开关设成 true、等用户手动关一次再开，正是之前「默认开启但不生效」的根因。
     *
     * @param detect 可注入的检测器，单元测试用它模拟一个偏掉的初始网格
     */
    fun prepare(
        pixels: IntArray,
        w: Int,
        h: Int,
        smart: Boolean,
        threshold: Float,
        detect: (IntArray, Int, Int) -> PixelGridInspector.Detection =
            { p, pw, ph -> PixelGridInspector.detect(p, pw, ph) },
    ): Prepared {
        val detection = detect(pixels, w, h)
        var grid = detection.grid
        var method = detection.method
        var score = detection.confidence
        var applied = false
        if (smart) {
            val alignment = PixelCalibration.refine(pixels, w, h, grid, threshold)
            score = alignment.score
            applied = alignment.applied
            if (alignment.applied) {
                grid = alignment.grid
                method = PixelGridInspector.Method.MANUAL
            }
        }
        // 首次识别就把列/行补到 5×5 的整数倍。格子大小和原点保持不动，
        // 不能为了塞进图片压缩 cellW/cellH；convert() 支持图外格子填白。
        grid = grid.withCompleteBlocks()
        return Prepared(
            grid = grid,
            method = method,
            detected = detection.method != PixelGridInspector.Method.FALLBACK,
            score = score,
            applied = applied,
        )
    }

    /**
     * 按网格裁图并生成最终图纸。
     *
     * [options] 里的网格尺寸、过滤档会被强制覆盖：校准页已经确定了
     * 列/行，生成阶段再按参数页的 29×29 走就会把整张图压扁。
     */
    fun convert(
        pixels: IntArray,
        w: Int,
        h: Int,
        grid: PixelCalibration.Grid,
        palette: BeadPalette,
        options: GeneratorOptions,
        onStage: (GenerationStage, Float) -> Unit = { _, _ -> },
    ): Conversion {
        require(w > 0 && h > 0 && pixels.size == w * h) { "源图像素尺寸不符" }
        // 网格允许被拖到图片边缘外：按网格覆盖范围铺一张等大的画布，
        // 图片外的格子保持空白，这样拖动对齐后的生成结果和屏幕上的
        // 网格完全一致，不会被强制拉回图内。
        val left = grid.offsetX.roundToInt()
        val top = grid.offsetY.roundToInt()
        val outW = (grid.cols * grid.cellW).roundToInt().coerceAtLeast(1)
        val outH = (grid.rows * grid.cellH).roundToInt().coerceAtLeast(1)
        // 图外格子填白：透明像素在色卡量化里容易落成黑色，预览会出现
        // 一条黑边；像素画导入的底通常是白底，填白更接近用户预期。
        val padded = IntArray(outW * outH) { 0xFFFFFFFF.toInt() }
        val srcL = max(0, left)
        val srcT = max(0, top)
        val srcR = min(w, left + outW)
        val srcB = min(h, top + outH)
        if (srcR > srcL && srcB > srcT) {
            val copyW = srcR - srcL
            for (y in srcT until srcB) {
                System.arraycopy(
                    pixels,
                    y * w + srcL,
                    padded,
                    (y - top) * outW + (srcL - left),
                    copyW,
                )
            }
        }
        val cropped = TransformedImage(padded, outW, outH)

        val exact = options.copy(
            gridW = grid.cols.coerceIn(1, 500),
            gridH = grid.rows.coerceIn(1, 500),
            // 限色/抖动/去背是照片参数。用户上一轮调过照片后直接导像素画，
            // 这些开关会原样带进来，把 1:1 的色块抖散或吃掉，这里统一关掉。
            maxColors = null,
            ditheringEnabled = false,
            removeBackground = false,
            filterLevel = FilterLevel.NONE,
            exactArt = true,
            pixelGrid = grid.cols to grid.rows,
            autoFrame = false,
            // 画像素是作者选好的固定颜色，曝光修正会整体提亮，把原色拉偏；
            // 这条管线只做 1:1 还原。
            autoExposure = false,
        )
        val generated = PatternGenerator.generate(
            cropped.pixels, cropped.width, cropped.height,
            exact, palette, onStage,
        )
        // 补到 5×5 的格子可能完全落在原图外；生成阶段把它们填成了
        // 白色，这里按「与原图交集不足 60%」统一改回空格，避免多出
        // 一圈白豆。阈值和 PixelGridInspector.analyze 保持一致。
        val cells = generated.cells
        for (row in 0 until grid.rows) {
            val y0 = grid.offsetY + row * grid.cellH
            val y1 = y0 + grid.cellH
            for (col in 0 until grid.cols) {
                val x0 = grid.offsetX + col * grid.cellW
                val x1 = x0 + grid.cellW
                val overlapW = min(x1, w.toDouble()) - max(x0, 0.0)
                val overlapH = min(y1, h.toDouble()) - max(y0, 0.0)
                val area = grid.cellW * grid.cellH
                if (overlapW <= 0.0 || overlapH <= 0.0 ||
                    overlapW * overlapH < area * 0.60
                ) {
                    cells[row * grid.cols + col] = -1
                }
            }
        }
        val fullResult = generated.copy(cells = cells)
        val argb = IntArray(fullResult.cells.size)
        for (i in argb.indices) {
            val idx = fullResult.cells[i]
            argb[i] = if (idx in fullResult.palette.colors.indices) {
                fullResult.palette.colors[idx].argb
            } else {
                0
            }
        }

        // 校准网格为了补完整 5×5 块，可能在图外多出整行/整列；这些格子
        // 在落库前全部收掉，图纸尺寸紧贴有颜色的内容。校准页仍用上面的
        // fullResult / previewArgb 显示完整网格，确认时才切到 final*。
        val trim = PatternTrim.trimEmptyEdges(cells, grid.cols, grid.rows)
        val finalResult = if (trim.trimmed) {
            fullResult.copy(
                width = trim.width,
                height = trim.height,
                cells = trim.cells,
                framed = true,
            )
        } else {
            fullResult
        }
        val (finalCropped, finalGrid) = if (trim.trimmed) {
            cropToContent(cropped, grid, trim)
        } else {
            cropped to grid
        }
        return Conversion(
            grid = grid,
            cropped = cropped,
            result = fullResult,
            previewArgb = argb,
            finalGrid = finalGrid,
            finalCropped = finalCropped,
            finalResult = finalResult,
        )
    }

    /**
     * 按收边后的格子范围裁裁图，并给出与裁图一致的网格。
     *
     * 裁图本身是按整张网格铺出来的像素图，格宽可能带小数；这里按格数
     * 比例换算回像素边界，避免直接乘 cellW 后因四舍五入切掉半格。
     */
    private fun cropToContent(
        image: TransformedImage,
        grid: PixelCalibration.Grid,
        trim: PatternTrim.Result,
    ): Pair<TransformedImage, PixelCalibration.Grid> {
        val x0 = (trim.left.toDouble() / grid.cols * image.width)
            .roundToInt().coerceIn(0, image.width - 1)
        val x1 = ((trim.left + trim.width).toDouble() / grid.cols * image.width)
            .roundToInt().coerceIn(x0 + 1, image.width)
        val y0 = (trim.top.toDouble() / grid.rows * image.height)
            .roundToInt().coerceIn(0, image.height - 1)
        val y1 = ((trim.top + trim.height).toDouble() / grid.rows * image.height)
            .roundToInt().coerceIn(y0 + 1, image.height)

        val outW = x1 - x0
        val outH = y1 - y0
        val out = IntArray(outW * outH)
        for (y in 0 until outH) {
            System.arraycopy(image.pixels, (y0 + y) * image.width + x0, out, y * outW, outW)
        }
        val outGrid = PixelCalibration.Grid(
            cols = trim.width,
            rows = trim.height,
            cellW = outW.toDouble() / trim.width,
            cellH = outH.toDouble() / trim.height,
            offsetX = 0.0,
            offsetY = 0.0,
        )
        return TransformedImage(out, outW, outH) to outGrid
    }
}
