package com.pindou.app.core.pattern

/**
 * 图纸级几何变换：只重排格子，不碰色卡与调色板。
 *
 * 所有函数都要求 `width * height == cells.size`，调用方负责保证尺寸；
 * 越界输入直接抛 [IllegalArgumentException]，避免静默产出错位图纸。
 */
object PatternTransform {

    /** 左右镜像：new(x,y) = old(width-1-x, y)。 */
    fun mirrorHorizontal(cells: IntArray, width: Int, height: Int): IntArray {
        requireGrid(cells.size, width, height)
        return IntArray(cells.size) { i ->
            val x = i % width
            val y = i / width
            cells[y * width + (width - 1 - x)]
        }
    }

    /** 上下镜像：new(x,y) = old(x, height-1-y)。 */
    fun mirrorVertical(cells: IntArray, width: Int, height: Int): IntArray {
        requireGrid(cells.size, width, height)
        return IntArray(cells.size) { i ->
            val x = i % width
            val y = i / width
            cells[(height - 1 - y) * width + x]
        }
    }

    /** 锁定标记与格子同步镜像，避免翻转后锁定区域留在原处。 */
    fun mirrorHorizontal(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        requireGrid(mask.size, width, height)
        return BooleanArray(mask.size) { i ->
            val x = i % width
            val y = i / width
            mask[y * width + (width - 1 - x)]
        }
    }

    fun mirrorVertical(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        requireGrid(mask.size, width, height)
        return BooleanArray(mask.size) { i ->
            val x = i % width
            val y = i / width
            mask[(height - 1 - y) * width + x]
        }
    }

    private fun requireGrid(size: Int, width: Int, height: Int) {
        require(width > 0 && height > 0) { "尺寸必须为正: ${width}x$height" }
        require(size == width * height) { "格子数 $size 与 ${width}x$height 不符" }
    }
}
