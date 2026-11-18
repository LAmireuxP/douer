package com.pindou.app.core.export

import kotlin.math.min

/**
 * PDF「按底板」分页的纯数学部分，便于 JVM 单测。
 *
 * 页号 = 板号，按从左到右、从上到下排列；边缘板按实际宽高收窄。
 * 注意与施工引导的 29×29 底板保持同一口径。
 */
internal object BoardPdfLayout {
    const val SIDE = 29

    fun boardsX(width: Int): Int = (width + SIDE - 1) / SIDE

    fun boardsY(height: Int): Int = (height + SIDE - 1) / SIDE

    fun pageCount(width: Int, height: Int): Int = boardsX(width) * boardsY(height)

    fun colStart(width: Int, board: Int): Int = (board % boardsX(width)) * SIDE

    fun colCount(width: Int, board: Int): Int = min(SIDE, width - colStart(width, board))

    fun rowStart(width: Int, height: Int, board: Int): Int =
        (board / boardsX(width)) * SIDE

    fun rowCount(width: Int, height: Int, board: Int): Int =
        min(SIDE, height - rowStart(width, height, board))

    fun slice(cells: IntArray, width: Int, height: Int, board: Int): IntArray {
        require(width > 0 && height > 0) { "图纸尺寸无效" }
        require(cells.size >= width * height) { "格数据长度不足" }
        require(board in 0 until pageCount(width, height)) { "板号越界" }
        val colStart = colStart(width, board)
        val colCount = colCount(width, board)
        val rowStart = rowStart(width, height, board)
        val rowCount = rowCount(width, height, board)
        return IntArray(rowCount * colCount) { i ->
            val gy = rowStart + i / colCount
            val gx = colStart + i % colCount
            cells[gy * width + gx]
        }
    }
}
