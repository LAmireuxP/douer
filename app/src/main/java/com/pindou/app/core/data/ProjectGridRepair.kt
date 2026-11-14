package com.pindou.app.core.data

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * 旧版本裁剪只写了格子数组、没有同步数据库宽高，打开时会遇到
 * “数据尺寸不符”。这里用格子总数和原宽高比例反推真实网格。
 *
 * 反推同时考虑：
 * 1. 新网格长宽比要尽量接近旧元数据；
 * 2. 长宽绝对差尽量小，避免在比例相近的因子里选到离谱尺寸。
 */
object ProjectGridRepair {

    data class Grid(val width: Int, val height: Int)

    fun infer(
        oldWidth: Int,
        oldHeight: Int,
        cellCount: Int,
        pages: Int,
    ): Grid? {
        if (oldWidth <= 0 || oldHeight <= 0 || cellCount <= 0 || pages <= 0) return null
        if (cellCount % pages != 0) return null
        val pageLen = cellCount / pages
        val wantedRatio = oldWidth.toDouble() / oldHeight
        var best: Grid? = null
        var bestScore = Double.MAX_VALUE

        for (w in 1..sqrt(pageLen.toDouble()).toInt().coerceAtLeast(1)) {
            if (pageLen % w != 0) continue
            val h = pageLen / w
            for (candidate in listOf(Grid(w, h), Grid(h, w))) {
                val ratio = candidate.width.toDouble() / candidate.height
                val ratioScore = abs(ln(ratio / wantedRatio))
                val sizeScore =
                    abs(candidate.width - oldWidth).toDouble() / oldWidth +
                        abs(candidate.height - oldHeight).toDouble() / oldHeight
                val score = ratioScore * 6.0 + sizeScore
                if (score < bestScore - 1e-9) {
                    bestScore = score
                    best = candidate
                }
            }
        }
        return best
    }
}
