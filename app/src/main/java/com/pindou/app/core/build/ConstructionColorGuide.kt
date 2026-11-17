package com.pindou.app.core.build

/**
 * 施工模式的「按色号」统计：算当前页每种颜色还剩多少颗、下一个该拼哪个色。
 *
 * 与 [ConstructionState] 分开，是因为状态只持有布尔进度，不认识调色板；
 * 这里只需要格数据 + 进度，纯函数、可单测。
 */
object ConstructionColorGuide {

    /**
     * 当前页每种颜色剩余（未拼）颗数。空格（cells < 0）不计入。
     *
     * @param pageOffset 当前页在 done 里的起始下标（= pageIndex × width × height）
     */
    fun remaining(
        cells: IntArray,
        done: BooleanArray,
        width: Int,
        height: Int,
        pageOffset: Int,
    ): Map<Int, Int> {
        val size = width * height
        if (width <= 0 || height <= 0 || cells.size < size) return emptyMap()
        require(pageOffset >= 0 && pageOffset + size <= done.size) { "进度数组与页偏移不符" }
        val out = HashMap<Int, Int>()
        for (i in 0 until size) {
            val c = cells[i]
            if (c < 0) continue
            if (done[pageOffset + i]) continue
            out[c] = (out[c] ?: 0) + 1
        }
        return out
    }

    /** 按调色板顺序列出还在用的颜色（有剩余颗数的），供选择条使用。 */
    fun activeColors(remaining: Map<Int, Int>): List<Int> =
        remaining.entries.filter { it.value > 0 }.map { it.key }.sorted()

    /**
     * 从 [from] 之后（不含）按 [order] 找下一个还有剩余的色；绕回开头，
     * 全部拼完返回 -1。[order] 通常是 [activeColors] 的结果。
     */
    fun nextIncomplete(order: List<Int>, remaining: Map<Int, Int>, from: Int): Int {
        if (order.isEmpty()) return -1
        val pos = order.indexOf(from)
        val start = if (pos < 0) 0 else pos + 1
        for (k in order.indices) {
            val color = order[(start + k) % order.size]
            if ((remaining[color] ?: 0) > 0) return color
        }
        return -1
    }
}
