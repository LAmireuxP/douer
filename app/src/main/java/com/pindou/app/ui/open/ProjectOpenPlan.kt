package com.pindou.app.ui.open

import com.pindou.app.core.data.GridCodec
import com.pindou.app.core.data.ProjectGridRepair
import com.pindou.app.core.studio.StudioEngine
import com.pindou.app.core.studio.StudioFrame
import com.pindou.app.core.studio.StudioLayer

/**
 * 打开项目时的纯决策：格子解码 → 尺寸修复 → 分页 → sidecar 复用/回退。
 *
 * 这一段原来埋在 `openProject` 的协程体里，分支多、每支都直接写 VM 状态，
 * 只能用真机造旧数据来验。抽出来后：
 * - 尺寸不符时「反推成功 / 反推失败」两条路都能 JVM 断言；
 * - sidecar 尺寸对不上时回退成「每页一层」，不再依赖文件系统；
 * - VM 只负责 I/O（读库、读 sidecar、repairSize 落库）和状态写入。
 */
object ProjectOpenPlan {

    sealed interface Result {
        data class Error(val message: String) : Result

        data class Ready(
            val width: Int,
            val height: Int,
            val pages: List<IntArray>,
            /** true = 原宽高与格子数不符，已按格子数反推并需要 repairSize 落库 */
            val repaired: Boolean,
            /** 有 sidecar 且尺寸匹配时直接复用；否则按页新建 */
            val studio: StudioEngine,
        ) : Result
    }

    /**
     * @param encodedCells Room 里的小端 int32 数组（`GridCodec` 格式）
     * @param loadedStudio 已从 sidecar 读出的工程；null = 没有/读失败
     */
    fun plan(
        width: Int,
        height: Int,
        pageCount: Int,
        encodedCells: ByteArray,
        loadedStudio: StudioEngine?,
        paletteArgb: IntArray,
    ): Result {
        if (width <= 0 || height <= 0 || encodedCells.isEmpty()) {
            return Result.Error("打开失败：图纸数据为空")
        }
        val cells = GridCodec.decodeCells(encodedCells)
        val pages = pageCount.coerceAtLeast(1)
        var gridWidth = width
        var gridHeight = height
        var pageLen = gridWidth * gridHeight
        var repaired = false

        if (cells.size != pageLen * pages) {
            val inferred = ProjectGridRepair.infer(width, height, cells.size, pages)
                ?: return Result.Error(
                    "打开失败：数据尺寸不符（${cells.size} vs ${pageLen * pages}）",
                )
            gridWidth = inferred.width
            gridHeight = inferred.height
            pageLen = gridWidth * gridHeight
            repaired = true
        }

        // 反推成功后 pageLen×pages 必然等于 cells.size（infer 先检查整除），
        // 这里不再做第二次长度校验。
        val pageCells = List(pages) { index ->
            cells.copyOfRange(index * pageLen, (index + 1) * pageLen)
        }

        val studio = loadedStudio?.takeIf {
            it.width == gridWidth && it.height == gridHeight
        } ?: StudioEngine(
            gridWidth,
            gridHeight,
            pageCells.mapIndexed { index, page ->
                StudioFrame(
                    mutableListOf(
                        StudioLayer(
                            if (pageCells.size == 1) "图层 1" else "第 ${index + 1} 页",
                            page.copyOf(),
                        ),
                    ),
                )
            },
            paletteArgb = paletteArgb,
        )

        return Result.Ready(
            width = gridWidth,
            height = gridHeight,
            pages = pageCells,
            repaired = repaired,
            studio = studio,
        )
    }
}
