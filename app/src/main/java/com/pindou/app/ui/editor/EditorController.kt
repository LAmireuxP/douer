package com.pindou.app.ui.editor

import com.pindou.app.core.editor.ColorReplaceOps
import com.pindou.app.core.editor.EditorEngine
import com.pindou.app.core.editor.PaletteOps
import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.core.pattern.PatternResult

/**
 * 编辑器子域：页操作、画布几何变换、整图换色。
 *
 * 编辑引擎本身、当前图纸、项目 id、仓储和提示条仍由 AppViewModel 持有；
 * controller 只通过 [Host] 读写这些共享状态。这样 EditorScreen 里的
 * `vm.undoEditor()` / `vm.applyPaletteRemap()` 一行都不用改，
 * 但 AppViewModel 不再为「切页/翻转/合并相近色」展开几十行样板。
 */
class EditorController(private val host: Host) {

    interface Host {
        val engine: EditorEngine?

        /** 整图换色会重建引擎（撤销栈随之清空），所以需要替换而不是原地改 */
        fun installEngine(engine: EditorEngine)

        val pages: List<IntArray>
        fun setPages(pages: List<IntArray>)

        val done: PatternResult?
        fun setDone(result: PatternResult)

        val palette: BeadPalette?
        fun setPalette(palette: BeadPalette)

        /** 编辑页当前选中的色卡索引；换色卡后要夹到新色卡范围内 */
        var colorIndex: Int
        fun clearRecentColors()

        val projectId: Long?
        fun persist(
            width: Int,
            height: Int,
            pages: List<IntArray>,
            locks: List<BooleanArray>,
            palette: BeadPalette,
        )

        fun notifyChanged()
        fun toast(message: String)
    }

    // ------------------------------------------------------------ 撤销 / 页

    fun undo() {
        host.engine?.undo()
        host.notifyChanged()
    }

    fun redo() {
        host.engine?.redo()
        host.notifyChanged()
    }

    fun switchPage(index: Int) {
        host.engine?.setPage(index)
        host.notifyChanged()
    }

    fun addPage(copyCurrent: Boolean) {
        host.engine?.addPage(copyCurrent)
        host.notifyChanged()
    }

    fun removePage(index: Int) {
        host.engine?.removePage(index)
        host.notifyChanged()
    }

    // ------------------------------------------------------------ 几何变换

    /** 改尺寸：所有页内容居中保留 */
    fun resize(newWidth: Int, newHeight: Int) {
        val engine = host.engine ?: return
        engine.resize(newWidth.coerceIn(5, 500), newHeight.coerceIn(5, 500))
        host.notifyChanged()
    }

    /** 编辑时裁剪：只保留 (left, top) 起的 w×h，所有页同步，可撤销 */
    fun crop(left: Int, top: Int, newWidth: Int, newHeight: Int) {
        val engine = host.engine ?: return
        val w = newWidth.coerceIn(5, 500)
        val h = newHeight.coerceIn(5, 500)
        if (left < 0 || top < 0) return
        if (left + w > engine.width || top + h > engine.height) return
        if (engine.crop(left, top, w, h)) host.notifyChanged()
    }

    fun flipHorizontal() {
        if (host.engine?.flipHorizontal() == true) host.notifyChanged()
    }

    fun flipVertical() {
        if (host.engine?.flipVertical() == true) host.notifyChanged()
    }

    fun rotate90(clockwise: Boolean) {
        if (host.engine?.rotate90(clockwise) == true) host.notifyChanged()
    }

    // ------------------------------------------------------------ 整图换色

    /** 换品牌：按 ΔE2000 最近色整图重映射并立即持久化 */
    fun applyPaletteRemap(newPalette: BeadPalette) {
        val engine = host.engine ?: return
        val current = host.done ?: return
        val remapped = PaletteOps.remapToPalette(engine.pages, current.palette, newPalette)
        val next = EditorEngine(
            engine.width,
            engine.height,
            remapped,
            engine.locks,
            engine.pageIndex,
        )
        host.installEngine(next)
        host.setDone(
            PatternResult(
                engine.width,
                engine.height,
                remapped[0],
                newPalette,
                current.elapsedMs,
            ),
        )
        host.setPages(remapped)
        host.setPalette(newPalette)
        // 新品牌色号数量可能不同，旧索引越界会让编辑页顶部色块直接崩
        host.colorIndex = host.colorIndex.coerceIn(0, newPalette.colors.lastIndex)
        host.clearRecentColors()
        host.projectId?.let { id ->
            host.persist(engine.width, engine.height, remapped, next.locks, newPalette)
        }
        host.notifyChanged()
        host.toast("已换用「${newPalette.brandLabel}」色卡")
    }

    /**
     * 全图合并相近色（ΔE 阈值），沿用编辑引擎的撤销栈：
     * 合并后按一次撤销即可回到合并前，不丢编辑历史。
     * 返回逐条「旧色 → 新色 · 格数」明细，UI 用于展示合并了什么。
     */
    fun mergeSimilar(maxDeltaE: Double): List<ColorReplaceOps.Move> {
        val engine = host.engine ?: return emptyList()
        val palette = host.palette ?: return emptyList()
        val outcome = ColorReplaceOps.mergeSimilar(engine.pages, engine.locks, palette, maxDeltaE)
        if (!outcome.changed) {
            host.toast("ΔE ≤ %.1f 没有可合并的相近色".format(maxDeltaE))
            return emptyList()
        }
        if (!engine.applyPages(outcome.pages)) return emptyList()
        syncAfterColorOp()
        persist()
        val usedBefore = engine.pages.flatMap { p -> p.filter { it >= 0 } }.distinct().size
        host.toast("已合并 ${outcome.moves.size} 组 · ${outcome.movedCells} 格（剩 $usedBefore 色）")
        return outcome.moves
    }

    /**
     * 替换相似色：[from] 及与它 ΔE ≤ [maxDeltaE] 的已用色统一替换为 [to]。
     * maxDeltaE = 0 即只替换这一个色号。返回明细供 UI 展示。
     */
    fun replaceSimilar(from: Int, to: Int, maxDeltaE: Double): List<ColorReplaceOps.Move> {
        val engine = host.engine ?: return emptyList()
        val palette = host.palette ?: return emptyList()
        if (from == to) {
            host.toast("源色与目标色相同")
            return emptyList()
        }
        val outcome = ColorReplaceOps.replaceSimilar(
            engine.pages, engine.locks, palette, from, to, maxDeltaE,
        )
        if (!outcome.changed) {
            host.toast(
                if (outcome.lockedSkipped > 0) {
                    "命中 ${outcome.lockedSkipped} 格，但都是锁定格"
                } else {
                    "没有匹配的格子"
                },
            )
            return emptyList()
        }
        if (!engine.applyPages(outcome.pages)) return emptyList()
        syncAfterColorOp()
        persist()
        val target = palette.colors.getOrNull(to)?.code ?: "?"
        host.toast("已把 ${outcome.moves.size} 色 · ${outcome.movedCells} 格换成 $target")
        return outcome.moves
    }

    /** 全局换色后同步 genState / projectPages，并刷新画布 */
    private fun syncAfterColorOp() {
        val engine = host.engine ?: return
        val current = host.done ?: return
        val pages = engine.pages.map { it.copyOf() }
        host.setPages(pages)
        host.setDone(
            current.copy(
                width = engine.width,
                height = engine.height,
                cells = pages.first(),
                palette = host.palette ?: current.palette,
            ),
        )
        host.notifyChanged()
    }

    /** 全局换色后立即落库（拷贝数组，避免协程执行时被继续编辑） */
    private fun persist() {
        val engine = host.engine ?: return
        val id = host.projectId ?: return
        val palette = host.palette ?: return
        host.persist(
            width = engine.width,
            height = engine.height,
            pages = engine.pages.map { it.copyOf() },
            locks = engine.locks.map { it.copyOf() },
            palette = palette,
        )
    }
}
