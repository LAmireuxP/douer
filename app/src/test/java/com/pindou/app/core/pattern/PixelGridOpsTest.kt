package com.pindou.app.core.pattern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelGridOpsTest {

    private val grid = PixelCalibration.Grid(
        cols = 16,
        rows = 16,
        cellW = 4.0,
        cellH = 4.0,
        offsetX = 8.0,
        offsetY = 8.0,
    )

    @Test
    fun `平移按图像边界钳制最大偏移`() {
        val out = PixelGridOps.nudge(grid, imgW = 64, imgH = 64, dx = 1000.0, dy = 1000.0)
        assertEquals(64.0 - grid.cellW, out.offsetX, 0.0001)
        assertEquals(64.0 - grid.cellH, out.offsetY, 0.0001)
    }

    @Test
    fun `平移允许网格滑出左边缘但保留一格重叠`() {
        val out = PixelGridOps.nudge(grid, imgW = 64, imgH = 64, dx = -1000.0, dy = -1000.0)
        val minX = -(grid.cols - 1) * grid.cellW
        val minY = -(grid.rows - 1) * grid.cellH
        assertEquals(minX, out.offsetX, 0.0001)
        assertEquals(minY, out.offsetY, 0.0001)
        // 右边缘仍至少压住图片第一列
        assertTrue(out.offsetX + out.cols * out.cellW > 0.0)
    }

    @Test
    fun `改列数不动格子大小与偏移`() {
        val out = PixelGridOps.setCols(grid, 24)
        assertEquals(24, out.cols)
        assertEquals(grid.rows, out.rows)
        assertEquals(grid.cellW, out.cellW, 0.0001)
        assertEquals(grid.offsetX, out.offsetX, 0.0001)
    }

    @Test
    fun `改列数低于下限被钳制`() {
        val out = PixelGridOps.setCols(grid, -5)
        assertEquals(PixelCalibration.MIN_COLS, out.cols)
    }

    @Test
    fun `改列数高于上限被钳制`() {
        val out = PixelGridOps.setCols(grid, 10_000)
        assertEquals(PixelCalibration.MAX_COLS, out.cols)
    }

    @Test
    fun `改行数允许 1 行到上限`() {
        assertEquals(1, PixelGridOps.setRows(grid, 0).rows)
        assertEquals(PixelCalibration.MAX_COLS, PixelGridOps.setRows(grid, 9999).rows)
    }

    @Test
    fun `改格子大小保持宽高同步`() {
        val out = PixelGridOps.setCellSize(grid, 6.5f)
        assertEquals(6.5, out.cellW, 0.0001)
        assertEquals(6.5, out.cellH, 0.0001)
        assertEquals(grid.cols, out.cols)
        assertEquals(grid.rows, out.rows)
    }

    @Test
    fun `改格子大小低于下限被钳制`() {
        val out = PixelGridOps.setCellSize(grid, 0.1f)
        assertEquals(PixelCalibration.MIN_CELL, out.cellW, 0.0001)
        assertEquals(PixelCalibration.MIN_CELL, out.cellH, 0.0001)
    }

    @Test
    fun `改格子大小高于上限被钳制`() {
        val out = PixelGridOps.setCellSize(grid, 99_999f)
        assertEquals(PixelCalibration.MAX_CELL, out.cellW, 0.0001)
    }
}
