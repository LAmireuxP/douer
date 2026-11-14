package com.pindou.app.core.pattern

/**
 * 去掉图纸四周整行/整列的空白格（-1），让有颜色的格子紧贴上下左右边缘。
 *
 * 只裁“整行整列都为空”的外框，内部镂空保持原样；像素画/精确网格不调用它，
 * 避免裁边后逐像素对位发生偏移。
 */
object PatternTrim {

    data class Result(
        val cells: IntArray,
        val width: Int,
        val height: Int,
        val trimmed: Boolean,
        val left: Int = 0,
        val top: Int = 0,
    )

    fun trimEmptyEdges(cells: IntArray, width: Int, height: Int): Result {
        if (width <= 0 || height <= 0 || cells.size != width * height) {
            return Result(cells, width, height, false)
        }

        var minX = width
        var maxX = -1
        var minY = height
        var maxY = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (cells[row + x] < 0) continue
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }

        // 整张图都是空格：保持原尺寸，避免变成 0×0
        if (maxX < minX || maxY < minY) return Result(cells, width, height, false)
        if (minX == 0 && minY == 0 && maxX == width - 1 && maxY == height - 1) {
            return Result(cells, width, height, false)
        }

        val outW = maxX - minX + 1
        val outH = maxY - minY + 1
        val out = IntArray(outW * outH)
        for (y in 0 until outH) {
            System.arraycopy(cells, (minY + y) * width + minX, out, y * outW, outW)
        }
        return Result(out, outW, outH, true, minX, minY)
    }
}
