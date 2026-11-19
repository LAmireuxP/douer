package com.pindou.app.core.build

/**
 * 进入施工模式时的纯决策：把持久化的完成字节还原成活动数组、方向和游标。
 *
 * 这里最容易出错的是「编辑器改过尺寸」：`doneCells` 的长度是按旧网格写的，
 * 直接拷进 `ConstructionState` 会触发 `require(done.size == w*h*pages)` 崩溃。
 * 现在的三条路：
 * - 长度完全匹配：原样还原，游标按当前方向夹到上限；
 * - 是单页长度的整数倍但不够总页数：保留已有页，其余页从空白开始，游标归零；
 * - 其他（含 null / 非整除 / 超长）：整份进度作废，游标归零。
 */
object ConstructionResumePlan {

    data class Plan(
        val done: BooleanArray,
        val orientation: ConstructionState.Orientation,
        val cursor: Int,
    )

    fun plan(
        stored: ByteArray?,
        pageSize: Int,
        pageCount: Int,
        orientationCode: Int,
        storedCursor: Int?,
        paletteSize: Int,
        width: Int,
        height: Int,
    ): Plan {
        val pages = pageCount.coerceAtLeast(1)
        val expected = pageSize * pages
        val orientation = orientationFrom(orientationCode)
        if (pageSize <= 0) {
            return Plan(BooleanArray(0), orientation, 0)
        }

        if (stored != null && stored.size == expected) {
            val done = BooleanArray(expected) { stored[it] != 0.toByte() }
            val upper = cursorUpper(orientation, paletteSize, width, height)
            return Plan(done, orientation, (storedCursor ?: 0).coerceIn(0, upper))
        }

        // 旧多页项目可能只存了前 N 页：保留已有页，其余留空
        if (stored != null && stored.size <= expected && stored.size % pageSize == 0) {
            val done = BooleanArray(expected)
            for (i in stored.indices) done[i] = stored[i] != 0.toByte()
            return Plan(done, orientation, 0)
        }

        return Plan(BooleanArray(expected), orientation, 0)
    }

    private fun orientationFrom(code: Int): ConstructionState.Orientation = when (code) {
        1 -> ConstructionState.Orientation.COLUMN
        2 -> ConstructionState.Orientation.COLOR
        3 -> ConstructionState.Orientation.BOARD
        else -> ConstructionState.Orientation.ROW
    }

    /** 游标上限随引导模式变化：按色号是色卡数，按底板是 29 格板数 */
    private fun cursorUpper(
        orientation: ConstructionState.Orientation,
        paletteSize: Int,
        width: Int,
        height: Int,
    ): Int = when (orientation) {
        ConstructionState.Orientation.COLOR -> (paletteSize - 1).coerceAtLeast(0)
        ConstructionState.Orientation.BOARD ->
            ((width + 28) / 29 * ((height + 28) / 29) - 1).coerceAtLeast(0)
        ConstructionState.Orientation.COLUMN -> (width - 1).coerceAtLeast(0)
        ConstructionState.Orientation.ROW -> (height - 1).coerceAtLeast(0)
    }
}
