package com.pindou.app.core.studio

/**
 * 页数据 → Studio 工程的唯一映射。
 *
 * 项目库打开（无 sidecar）和「编辑图纸」进 Studio 都要同一条回退：
 * 单页叫「图层 1」，多页叫「第 N 页」。以前两处各抄一份，改名时容易只改一边。
 */
object StudioPages {

    fun engineFrom(
        width: Int,
        height: Int,
        pages: List<IntArray>,
        paletteArgb: IntArray,
    ): StudioEngine = StudioEngine(
        width,
        height,
        pages.mapIndexed { index, cells ->
            StudioFrame(
                mutableListOf(
                    StudioLayer(
                        if (pages.size == 1) "图层 1" else "第 ${index + 1} 页",
                        cells.copyOf(),
                    ),
                ),
            )
        },
        paletteArgb = paletteArgb,
    )
}
