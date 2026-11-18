package com.pindou.app.core.pattern

import kotlin.math.max

/**
 * 像素画校准页上的网格编辑操作（纯函数）。
 *
 * 从 `PixelCalibrationController` 抽出来，便于 JVM 测试直接覆盖
 * 越界钳制与列/行/格距的联动语义，不必构造 Compose state。
 */
object PixelGridOps {

    /**
     * 平移网格并按图像尺寸钳制偏移。
     *
     * 允许网格滑出图片边缘（至少保留一格重叠）：像素画常常正好铺满
     * 画布，若要求整张网格入图，往右拖会一动不动。
     */
    fun nudge(grid: PixelCalibration.Grid, imgW: Int, imgH: Int, dx: Double, dy: Double): PixelCalibration.Grid {
        val next = grid.nudged(dx, dy)
        val minX = -(next.cols - 1) * next.cellW
        val maxX = max(minX, imgW - next.cellW)
        val minY = -(next.rows - 1) * next.cellH
        val maxY = max(minY, imgH - next.cellH)
        return next.copy(
            offsetX = next.offsetX.coerceIn(minX, maxX),
            offsetY = next.offsetY.coerceIn(minY, maxY),
        )
    }

    /** 只改列数，格子大小、行数与偏移不动。 */
    fun setCols(grid: PixelCalibration.Grid, cols: Int): PixelCalibration.Grid =
        grid.copy(cols = cols.coerceIn(PixelCalibration.MIN_COLS, PixelCalibration.MAX_COLS))

    /** 只改行数，格子大小、列数与偏移不动。 */
    fun setRows(grid: PixelCalibration.Grid, rows: Int): PixelCalibration.Grid =
        grid.copy(rows = rows.coerceIn(1, PixelCalibration.MAX_COLS))

    /** 只改一格大小，列/行/偏移不动；宽高同步，保持正方形。 */
    fun setCellSize(grid: PixelCalibration.Grid, px: Float): PixelCalibration.Grid {
        val target = px.toDouble().coerceIn(PixelCalibration.MIN_CELL, PixelCalibration.MAX_CELL)
        return grid.copy(cellW = target, cellH = target)
    }
}
