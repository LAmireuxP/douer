package com.pindou.app.core.studio

import com.pindou.app.core.editor.ShapeTool
import com.pindou.app.core.editor.SymmetryMode
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 图层混合模式。色卡索引本身不带透明度，混合时先还原成 ARGB 再量化回色卡。 */
enum class StudioBlendMode(val label: String) {
    NORMAL("正常"),
    MULTIPLY("正片叠底"),
    SCREEN("滤色"),
    OVERLAY("叠加"),
    DARKEN("变暗"),
    LIGHTEN("变亮"),
    ADD("相加"),
}

class StudioLayer(
    var name: String,
    var cells: IntArray,
    var visible: Boolean = true,
    var locked: Boolean = false,
    var opacity: Float = 1f,
    var blend: StudioBlendMode = StudioBlendMode.NORMAL,
) {
    fun copy(): StudioLayer = StudioLayer(name, cells.copyOf(), visible, locked, opacity, blend)
}

class StudioFrame(
    val layers: MutableList<StudioLayer>,
    var activeLayer: Int = 0,
    /** 该帧在动画里的停留时间（毫秒）。GIF 导出按帧读取。 */
    var holdMs: Int = 100,
) {
    fun copy(): StudioFrame = StudioFrame(layers.map { it.copy() }.toMutableList(), activeLayer, holdMs)
}

/**
 * 画像素工作区核心：帧 -> 图层 -> 逐格色卡索引（-1 = 透明）。
 *
 * 设计约束：
 * - 保存时不拍平：StudioCodec 把整个树写进 sidecar，重开还能改图层/帧。
 * - 项目库仍拿一份拍平结果（每帧一页），缩略图、预览、PDF 继续走旧链路。
 * - 所有会改像素的操作都经过 [beginStroke] 或 [snapshot]，撤销粒度分别是
 *   一次笔画、一次结构操作。
 */
class StudioEngine(
    initialWidth: Int,
    initialHeight: Int,
    initialFrames: List<StudioFrame>,
    initialFrame: Int = 0,
    paletteArgb: IntArray = IntArray(0),
) {
    var width: Int = initialWidth.coerceIn(1, MAX_SIDE)
        private set
    var height: Int = initialHeight.coerceIn(1, MAX_SIDE)
        private set

    val frames: MutableList<StudioFrame> = (
        initialFrames.ifEmpty { listOf(defaultFrame(width, height)) }
            .map { it.normalized(width, height) }
        ).toMutableList()

    var frameIndex: Int = initialFrame.coerceIn(0, frames.size - 1)
        private set

    /** 引擎自己的版本号；UI 另有一层 Compose 状态，避免直接观察可变对象。 */
    var revision: Int = 0
        private set

    private var palette: IntArray = paletteArgb.copyOf()
    private var matcher: PaletteMatcher? = PaletteMatcher(paletteArgb)

    /** 当前选区；null = 没有选区（所有像素都可编辑）。 */
    var selection: BooleanArray? = null
        private set

    private var clipboard: Clip? = null

    private val undoStack = ArrayDeque<Entry>()
    private val redoStack = ArrayDeque<Entry>()

    // composite（色号索引）和 renderArgb（屏幕 ARGB）是两种数据，分开缓存。
    // 之前共用一个 key，顶栏先算色数会把画布 ARGB 缓存顶掉，只能反复重算。
    private var cellsKey = Int.MIN_VALUE
    private var cellsIndex = -1
    private var cacheCells: IntArray? = null
    private var argbKey = Int.MIN_VALUE
    private var argbIndex = -1
    private var cacheArgb: IntArray? = null

    val frame: StudioFrame get() = frames[frameIndex.coerceIn(0, frames.size - 1)]
    val layers: MutableList<StudioLayer> get() = frame.layers
    val activeLayerIndex: Int get() = frame.activeLayer.coerceIn(0, layers.size - 1)
    val activeLayer: StudioLayer get() = layers[activeLayerIndex]
    val layerCount: Int get() = layers.size
    val frameCount: Int get() = frames.size
    val hasSelection: Boolean get() = selection != null
    val canPaste: Boolean get() = clipboard != null

    fun setPalette(argb: IntArray) {
        palette = argb.copyOf()
        matcher = PaletteMatcher(palette)
        invalidate()
    }

    fun paletteArgb(): IntArray = palette.copyOf()

    fun setActiveLayer(index: Int) {
        frame.activeLayer = index.coerceIn(0, layers.size - 1)
        bump()
    }

    fun setFrame(index: Int) {
        frameIndex = index.coerceIn(0, frames.size - 1)
        selection = null
        bump()
    }

    fun setFrameHold(index: Int, holdMs: Int) {
        if (index in frames.indices) {
            frames[index].holdMs = holdMs.coerceIn(20, 10_000)
            bump()
        }
    }

    fun frameHold(index: Int = frameIndex): Int =
        frames.getOrNull(index)?.holdMs ?: 100

    // ------------------------------------------------------------------
    // 合成
    // ------------------------------------------------------------------

    /** 当前帧拍平后的色卡索引，供保存/导出使用。 */
    fun composite(index: Int = frameIndex): IntArray {
        val safe = index.coerceIn(0, frames.size - 1)
        if (cellsKey == revision && cellsIndex == safe && cacheCells != null) {
            return cacheCells!!.copyOf()
        }
        val f = frames[safe]
        val out = if (needsBlend(f) && palette.isNotEmpty()) {
            quantize(renderArgb(safe))
        } else {
            val flat = IntArray(width * height) { -1 }
            for (layer in f.layers) {
                if (!layer.visible || layer.opacity <= 0f) continue
                for (i in flat.indices) {
                    if (layer.cells[i] >= 0) flat[i] = layer.cells[i]
                }
            }
            flat
        }
        cellsKey = revision
        cellsIndex = safe
        cacheCells = out
        return out.copyOf()
    }

    fun allComposites(): List<IntArray> = frames.indices.map { composite(it) }

    /**
     * 所有帧、所有可见图层合成后的内容包围盒（格坐标，含端点）。
     * 全空返回 null。只在“裁掉空白边”这种手动操作里调用，O(帧数×格数)。
     */
    fun contentBounds(): IntArray? {
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (index in frames.indices) {
            val cells = composite(index)
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
        }
        if (maxX < minX || maxY < minY) return null
        return intArrayOf(minX, minY, maxX, maxY)
    }

    /** 画布显示用的 ARGB；背景透明时 alpha=0。 */
    fun renderArgb(index: Int = frameIndex, background: Int? = null): IntArray {
        val safe = index.coerceIn(0, frames.size - 1)
        if (argbKey == revision && argbIndex == safe && cacheArgb != null && background == null) {
            return cacheArgb!!.copyOf()
        }
        val out = IntArray(width * height) { background ?: 0 }
        if (palette.isEmpty()) {
            val f = frames[safe]
            for (layer in f.layers) {
                if (!layer.visible || layer.opacity <= 0f) continue
                for (i in out.indices) {
                    val idx = layer.cells[i]
                    if (idx >= 0) out[i] = 0xFF000000.toInt() or (idx * 0x010101)
                }
            }
        } else {
            for (layer in frames[safe].layers) {
                if (!layer.visible || layer.opacity <= 0f) continue
                blendLayer(out, layer)
            }
        }
        if (background == null) {
            argbKey = revision
            argbIndex = safe
            cacheArgb = out
        }
        return out.copyOf()
    }

    private fun needsBlend(f: StudioFrame): Boolean =
        f.layers.any { it.visible && (it.opacity < 0.999f || it.blend != StudioBlendMode.NORMAL) }

    private fun blendLayer(dst: IntArray, layer: StudioLayer) {
        val opacity = layer.opacity.coerceIn(0f, 1f)
        for (i in dst.indices) {
            val idx = layer.cells[i]
            if (idx < 0) continue
            val srcColor = palette.getOrNull(idx) ?: continue
            val sr = (srcColor shr 16) and 0xFF
            val sg = (srcColor shr 8) and 0xFF
            val sb = srcColor and 0xFF
            val da = (dst[i] ushr 24) / 255f
            val dr = (dst[i] shr 16) and 0xFF
            val dg = (dst[i] shr 8) and 0xFF
            val db = dst[i] and 0xFF
            val br = blendChannel(layer.blend, dr, sr)
            val bg = blendChannel(layer.blend, dg, sg)
            val bb = blendChannel(layer.blend, db, sb)
            val outA = opacity + da * (1f - opacity)
            if (outA <= 0.0001f) {
                dst[i] = 0
                continue
            }
            val outR = ((br * opacity + dr * da * (1f - opacity)) / outA).roundToInt().coerceIn(0, 255)
            val outG = ((bg * opacity + dg * da * (1f - opacity)) / outA).roundToInt().coerceIn(0, 255)
            val outB = ((bb * opacity + db * da * (1f - opacity)) / outA).roundToInt().coerceIn(0, 255)
            dst[i] = ((outA * 255f).roundToInt().coerceIn(0, 255) shl 24) or
                (outR shl 16) or (outG shl 8) or outB
        }
    }

    private fun blendChannel(mode: StudioBlendMode, dst: Int, src: Int): Int = when (mode) {
        StudioBlendMode.NORMAL -> src
        StudioBlendMode.MULTIPLY -> dst * src / 255
        StudioBlendMode.SCREEN -> 255 - (255 - dst) * (255 - src) / 255
        StudioBlendMode.OVERLAY ->
            if (dst < 128) 2 * dst * src / 255
            else 255 - 2 * (255 - dst) * (255 - src) / 255
        StudioBlendMode.DARKEN -> min(dst, src)
        StudioBlendMode.LIGHTEN -> max(dst, src)
        StudioBlendMode.ADD -> min(255, dst + src)
    }

    private fun quantize(argb: IntArray): IntArray {
        val m = matcher ?: return IntArray(argb.size) { -1 }
        return IntArray(argb.size) { i -> m.nearest(argb[i]) }
    }

    // ------------------------------------------------------------------
    // 选区
    // ------------------------------------------------------------------

    fun selectRect(x0: Int, y0: Int, x1: Int, y1: Int, add: Boolean = false): Boolean {
        val left = min(x0, x1).coerceIn(0, width - 1)
        val right = max(x0, x1).coerceIn(0, width - 1)
        val top = min(y0, y1).coerceIn(0, height - 1)
        val bottom = max(y0, y1).coerceIn(0, height - 1)
        if (left == right && top == bottom) {
            // 单击不产生选区，避免点一下画布就把后续笔刷锁死在一格里
            clearSelection()
            return false
        }
        val mask = if (add) selection?.copyOf() ?: BooleanArray(width * height) else BooleanArray(width * height)
        for (y in top..bottom) for (x in left..right) mask[y * width + x] = true
        selection = mask
        bump()
        return true
    }

    /** 套索：xs/ys 是格坐标顶点，按像素中心做奇偶规则填充。 */
    fun selectLasso(xs: IntArray, ys: IntArray, add: Boolean = false): Boolean {
        if (xs.size < 3 || xs.size != ys.size) return false
        val mask = if (add) selection?.copyOf() ?: BooleanArray(width * height) else BooleanArray(width * height)
        var any = false
        for (y in 0 until height) {
            val cy = y + 0.5
            for (x in 0 until width) {
                val cx = x + 0.5
                if (pointInPolygon(cx, cy, xs, ys)) {
                    mask[y * width + x] = true
                    any = true
                }
            }
        }
        if (!any) return false
        selection = mask
        bump()
        return true
    }

    /**
     * 魔棒：连通区域按颜色距离扩散。[tolerance] 0..100。
     * [useComposite] = true 时以拍平结果为源（跨图层），否则只看当前图层。
     */
    fun selectMagicWand(
        index: Int,
        tolerance: Int = 0,
        useComposite: Boolean = false,
        add: Boolean = false,
    ): Boolean {
        if (index !in 0 until width * height) return false
        val source = if (useComposite) composite() else activeLayer.cells
        val target = source[index]
        val limit = toleranceColorDistance(tolerance)
        val mask = if (add) selection?.copyOf() ?: BooleanArray(width * height) else BooleanArray(width * height)
        val visited = BooleanArray(width * height)
        val queue = ArrayDeque<Int>()
        queue.add(index)
        visited[index] = true
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            mask[cur] = true
            val x = cur % width
            val y = cur / width
            fun visit(n: Int) {
                if (visited[n]) return
                visited[n] = true
                if (colorMatches(source[n], target, limit)) queue.add(n)
            }
            if (x > 0) visit(cur - 1)
            if (x < width - 1) visit(cur + 1)
            if (y > 0) visit(cur - width)
            if (y < height - 1) visit(cur + width)
        }
        if (mask.none { it } || mask.all { it }) {
            clearSelection()
            return false
        }
        selection = mask
        bump()
        return true
    }

    /** 魔棒的非连通版本：全图同色。 */
    fun selectByColor(index: Int, tolerance: Int = 0, useComposite: Boolean = false): Boolean {
        if (index !in 0 until width * height) return false
        val source = if (useComposite) composite() else activeLayer.cells
        val target = source[index]
        val limit = toleranceColorDistance(tolerance)
        val mask = BooleanArray(width * height)
        for (i in source.indices) if (colorMatches(source[i], target, limit)) mask[i] = true
        if (mask.all { it }) {
            clearSelection()
            return false
        }
        selection = mask
        bump()
        return true
    }

    fun selectAll() {
        selection = BooleanArray(width * height) { true }
        bump()
    }

    fun clearSelection() {
        selection = null
        bump()
    }

    fun invertSelection() {
        val old = selection
        selection = if (old == null) {
            BooleanArray(width * height) { true }
        } else {
            BooleanArray(old.size) { !old[it] }
        }
        if (selection?.none { it } == true) selection = null
        bump()
    }

    fun selectLayerContent() {
        val layer = activeLayer
        val mask = BooleanArray(width * height) { layer.cells[it] >= 0 }
        if (mask.none { it }) {
            clearSelection()
            return
        }
        selection = mask
        bump()
    }

    /** 选区包围盒：x0,y0,x1,y1（含端点）；无选区返回 null。 */
    fun selectionBounds(): IntArray? {
        val sel = selection ?: return null
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!sel[y * width + x]) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        return if (right < 0) null else intArrayOf(left, top, right, bottom)
    }

    fun copySelection(): Boolean {
        val layer = activeLayer
        val sel = selection
        val bounds = if (sel == null) intArrayOf(0, 0, width - 1, height - 1) else selectionBounds() ?: return false
        val w = bounds[2] - bounds[0] + 1
        val h = bounds[3] - bounds[1] + 1
        val cells = IntArray(w * h) { -1 }
        val mask = BooleanArray(w * h) { true }
        for (y in 0 until h) {
            for (x in 0 until w) {
                val src = (bounds[1] + y) * width + bounds[0] + x
                val dst = y * w + x
                if (sel != null && !sel[src]) {
                    mask[dst] = false
                    continue
                }
                cells[dst] = layer.cells[src]
            }
        }
        clipboard = Clip(cells, mask, w, h)
        bump()
        return true
    }

    fun cutSelection(): Boolean {
        if (!copySelection()) return false
        return deleteSelection()
    }

    fun pasteClipboard(atIndex: Int? = null): Boolean {
        val clip = clipboard ?: return false
        val before = snapshot()
        val target = atIndex?.let {
            intArrayOf((it % width).coerceAtLeast(0), (it / width).coerceAtLeast(0))
        } ?: selectionBounds()?.let { intArrayOf(it[0], it[1]) } ?: intArrayOf(0, 0)
        val layer = StudioLayer("粘贴", IntArray(width * height) { -1 })
        val sel = BooleanArray(width * height)
        for (y in 0 until clip.h) {
            for (x in 0 until clip.w) {
                if (!clip.mask[y * clip.w + x]) continue
                val dx = target[0] + x
                val dy = target[1] + y
                if (dx !in 0 until width || dy !in 0 until height) continue
                layer.cells[dy * width + dx] = clip.cells[y * clip.w + x]
                sel[dy * width + dx] = true
            }
        }
        val at = (activeLayerIndex + 1).coerceIn(0, layers.size)
        layers.add(at, layer)
        frame.activeLayer = at
        selection = if (sel.any { it }) sel else null
        pushSnapshot(before)
        bump()
        return true
    }

    fun deleteSelection(): Boolean {
        val layer = activeLayer
        if (layer.locked) return false
        val before = snapshot()
        val sel = selection
        if (sel == null) {
            layer.cells.fill(-1)
        } else {
            for (i in layer.cells.indices) if (sel[i]) layer.cells[i] = -1
        }
        pushSnapshot(before)
        bump()
        return true
    }

    fun fillSelection(color: Int, selectionOnly: Boolean = false): Boolean {
        val layer = activeLayer
        if (layer.locked) return false
        val sel = selection
        if (selectionOnly && sel == null) return false
        val before = snapshot()
        if (sel == null) {
            layer.cells.fill(color)
        } else {
            for (i in layer.cells.indices) if (sel[i]) layer.cells[i] = color
        }
        pushSnapshot(before)
        bump()
        return true
    }

    /** 移动当前图层里的选区内容；dx/dy 单位是格。 */
    fun moveSelection(dx: Int, dy: Int): Boolean {
        val sel = selection ?: return false
        if (dx == 0 && dy == 0) return false
        val layer = activeLayer
        if (layer.locked) return false
        val before = snapshot()
        val src = layer.cells.copyOf()
        for (i in src.indices) if (sel[i]) layer.cells[i] = -1
        val out = BooleanArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val from = y * width + x
                if (!sel[from]) continue
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until width || ny !in 0 until height) continue
                val to = ny * width + nx
                layer.cells[to] = src[from]
                out[to] = true
            }
        }
        selection = if (out.any { it }) out else null
        pushSnapshot(before)
        bump()
        return true
    }

    private data class Clip(val cells: IntArray, val mask: BooleanArray, val w: Int, val h: Int)

    // ------------------------------------------------------------------
    // 绘制
    // ------------------------------------------------------------------

    data class Change(val index: Int, val old: Int, val new: Int)

    inner class Stroke internal constructor() {
        internal val frameAt = frameIndex
        internal val layerAt = activeLayerIndex
        internal val changes = LinkedHashMap<Int, Change>()
        private val layer: StudioLayer? get() = frames.getOrNull(frameAt)?.layers?.getOrNull(layerAt)

        fun setCell(index: Int, color: Int): Boolean {
            if (index !in 0 until width * height) return false
            val layer = layer ?: return false
            if (layer.locked) return false
            if (selection?.get(index) == false) return false
            val old = layer.cells[index]
            if (old == color) return false
            layer.cells[index] = color
            changes.putIfAbsent(index, Change(index, old, color))
            return true
        }

        /** 方形/圆形笔刷；[circle] = false 时是方块笔。 */
        fun setBrush(index: Int, color: Int, size: Int, circle: Boolean): Boolean {
            if (index !in 0 until width * height) return false
            val cx = index % width
            val cy = index / width
            val side = size.coerceIn(1, 15)
            if (side == 1) return setCell(index, color)
            val radius = side / 2
            var changed = false
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    if (circle && dx * dx + dy * dy > radius * radius) continue
                    val x = cx + dx
                    val y = cy + dy
                    if (x !in 0 until width || y !in 0 until height) continue
                    if (setCell(y * width + x, color)) changed = true
                }
            }
            return changed
        }

        fun lineTo(
            from: Int,
            to: Int,
            color: Int,
            size: Int = 1,
            circle: Boolean = false,
            pixelPerfect: Boolean = false,
        ): Boolean {
            if (from !in 0 until width * height || to !in 0 until width * height) return false
            var points = bresenham(from % width, from / width, to % width, to / width)
            if (pixelPerfect) points = removeLCorners(points, width)
            var changed = false
            for (idx in points) if (setBrush(idx, color, size, circle)) changed = true
            return changed
        }
    }

    fun beginStroke(): Stroke = Stroke()

    fun endStroke(stroke: Stroke) {
        if (stroke.changes.isEmpty()) return
        undoStack.addLast(
            CellEntry(stroke.frameAt, stroke.layerAt, stroke.changes.values.toList()),
        )
        trimUndo()
        redoStack.clear()
        bump()
    }

    fun setCell(index: Int, color: Int): Boolean {
        val s = beginStroke()
        val changed = s.setCell(index, color)
        endStroke(s)
        return changed
    }

    fun floodFill(index: Int, color: Int, tolerance: Int = 0): Boolean {
        if (index !in 0 until width * height) return false
        val layer = activeLayer
        if (layer.locked) return false
        val origin = layer.cells[index]
        if (origin == color) return false
        val limit = toleranceColorDistance(tolerance)
        val visited = BooleanArray(width * height)
        val queue = ArrayDeque<Int>()
        val stroke = beginStroke()
        queue.add(index)
        visited[index] = true
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            stroke.setCell(cur, color)
            val x = cur % width
            val y = cur / width
            fun visit(n: Int) {
                if (visited[n]) return
                visited[n] = true
                if (colorMatches(layer.cells[n], origin, limit)) queue.add(n)
            }
            if (x > 0) visit(cur - 1)
            if (x < width - 1) visit(cur + 1)
            if (y > 0) visit(cur - width)
            if (y < height - 1) visit(cur + width)
        }
        endStroke(stroke)
        return stroke.changes.isNotEmpty()
    }

    fun drawShape(
        kind: ShapeTool,
        start: Int,
        end: Int,
        color: Int,
        filled: Boolean,
        symmetry: SymmetryMode,
        brushSize: Int = 1,
        circleBrush: Boolean = false,
    ): Boolean {
        if (start !in 0 until width * height || end !in 0 until width * height) return false
        if (activeLayer.locked) return false
        val stroke = beginStroke()
        val x0 = start % width
        val y0 = start / width
        val x1 = end % width
        val y1 = end / width
        // 形状对称必须成对变换起点和终点。不能拿两个 centers() 列表按下标配对：
        // 中心点被去重后两个列表长度可能不同（例如终点正好在角落），会把
        // 对角线错配成顶边，画出用户没点过的整行。
        val pairs = ArrayList<IntArray>(4)
        fun addPair(sx: Int, sy: Int, ex: Int, ey: Int) {
            pairs.add(intArrayOf(sx, sy, ex, ey))
        }
        addPair(x0, y0, x1, y1)
        if (symmetry == SymmetryMode.HORIZONTAL || symmetry == SymmetryMode.BOTH) {
            addPair(width - 1 - x0, y0, width - 1 - x1, y1)
        }
        if (symmetry == SymmetryMode.VERTICAL || symmetry == SymmetryMode.BOTH) {
            addPair(x0, height - 1 - y0, x1, height - 1 - y1)
        }
        if (symmetry == SymmetryMode.BOTH) {
            addPair(width - 1 - x0, height - 1 - y0, width - 1 - x1, height - 1 - y1)
        }
        for (p in pairs) {
            val indices = kind.indices(
                p[0], p[1], p[2], p[3], width, height, filled,
            )
            for (idx in indices) {
                if (brushSize <= 1 || filled) stroke.setCell(idx, color)
                else stroke.setBrush(idx, color, brushSize, circleBrush)
            }
        }
        endStroke(stroke)
        return stroke.changes.isNotEmpty()
    }

    /** 多边形：points 是 [x0,y0,x1,y1,…] 格坐标。 */
    fun drawPolygon(
        points: IntArray,
        color: Int,
        filled: Boolean,
        brushSize: Int = 1,
        circleBrush: Boolean = false,
    ): Boolean {
        if (points.size < 6 || points.size % 2 != 0) return false
        if (activeLayer.locked) return false
        val xs = IntArray(points.size / 2) { points[it * 2] }
        val ys = IntArray(points.size / 2) { points[it * 2 + 1] }
        val stroke = beginStroke()
        if (filled) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (pointInPolygon(x + 0.5, y + 0.5, xs, ys)) {
                        stroke.setCell(y * width + x, color)
                    }
                }
            }
        } else {
            for (i in xs.indices) {
                val j = (i + 1) % xs.size
                val pts = bresenham(xs[i], ys[i], xs[j], ys[j])
                for (idx in pts) stroke.setBrush(idx, color, brushSize, circleBrush)
            }
        }
        endStroke(stroke)
        return stroke.changes.isNotEmpty()
    }

    /** 二次/三次贝塞尔曲线，points = [x0,y0,x1,y1,x2,y2] 或再加一个控制点。 */
    fun drawCurve(
        points: IntArray,
        color: Int,
        brushSize: Int = 1,
        circleBrush: Boolean = false,
    ): Boolean {
        if (points.size != 6 && points.size != 8) return false
        if (activeLayer.locked) return false
        val stroke = beginStroke()
        val n = points.size / 2
        val xs = IntArray(n) { points[it * 2] }
        val ys = IntArray(n) { points[it * 2 + 1] }
        val steps = max(
            abs(xs.max() - xs.min()) + abs(ys.max() - ys.min()),
            1,
        ) * 2
        var last = -1
        for (s in 0..steps) {
            val t = s.toFloat() / steps
            val p = if (n == 3) {
                quadratic(xs[0], ys[0], xs[1], ys[1], xs[2], ys[2], t)
            } else {
                cubic(xs[0], ys[0], xs[1], ys[1], xs[2], ys[2], xs[3], ys[3], t)
            }
            val idx = p.second * width + p.first
            if (idx != last) {
                stroke.setBrush(idx, color, brushSize, circleBrush)
                last = idx
            }
        }
        endStroke(stroke)
        return stroke.changes.isNotEmpty()
    }

    /**
     * 线性/径向渐变。[colorA]/[colorB] 是色卡索引。
     * 没有选区时作用于整个当前图层；有选区时只填选区。
     */
    fun gradient(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        colorA: Int,
        colorB: Int,
        radial: Boolean,
        dithered: Boolean = false,
    ): Boolean {
        if (activeLayer.locked) return false
        val argbA = palette.getOrNull(colorA) ?: 0xFF000000.toInt()
        val argbB = palette.getOrNull(colorB) ?: 0xFFFFFFFF.toInt()
        val sel = selection
        val before = snapshot()
        val vx = (endX - startX).toFloat()
        val vy = (endY - startY).toFloat()
        val len2 = vx * vx + vy * vy
        val maxDist = hypot(vx.toDouble(), vy.toDouble()).toFloat().coerceAtLeast(0.001f)
        val layer = activeLayer
        var changed = false
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (sel != null && !sel[idx]) continue
                var t = if (radial) {
                    hypot((x - startX).toDouble(), (y - startY).toDouble()).toFloat() / maxDist
                } else {
                    if (len2 <= 0.0001f) 0f
                    else (((x - startX) * vx + (y - startY) * vy) / len2).coerceIn(0f, 1f)
                }
                if (dithered) {
                    val threshold = (BAYER4[y % 4][x % 4] + 0.5f) / 16f - 0.5f
                    t = (t + threshold / 8f).coerceIn(0f, 1f)
                }
                val mixed = lerpArgb(argbA, argbB, t)
                val mapped = matcher?.nearest(mixed) ?: colorA
                if (mapped >= 0 && layer.cells[idx] != mapped) {
                    layer.cells[idx] = mapped
                    changed = true
                }
            }
        }
        if (changed) pushSnapshot(before) else return false
        bump()
        return true
    }

    /** 抖动填充：Bayer 4×4 在两种颜色间按 [density]（0..100，A 的占比）分布。 */
    fun ditherFill(colorA: Int, colorB: Int, density: Int = 50): Boolean {
        if (activeLayer.locked) return false
        val sel = selection
        val before = snapshot()
        val threshold = density.coerceIn(0, 100) * 16 / 100
        val layer = activeLayer
        var changed = false
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (sel != null && !sel[idx]) continue
                val color = if (BAYER4[y % 4][x % 4] < threshold) colorA else colorB
                if (layer.cells[idx] != color) {
                    layer.cells[idx] = color
                    changed = true
                }
            }
        }
        if (changed) pushSnapshot(before) else return false
        bump()
        return true
    }

    /** 把文字/图案位图掩码盖到当前图层。 */
    fun stampMask(
        mask: BooleanArray,
        maskW: Int,
        maskH: Int,
        originX: Int,
        originY: Int,
        color: Int,
    ): Boolean {
        if (maskW <= 0 || maskH <= 0 || mask.size != maskW * maskH) return false
        if (activeLayer.locked) return false
        val before = snapshot()
        val layer = activeLayer
        var changed = false
        for (y in 0 until maskH) {
            for (x in 0 until maskW) {
                if (!mask[y * maskW + x]) continue
                val dx = originX + x
                val dy = originY + y
                if (dx !in 0 until width || dy !in 0 until height) continue
                val idx = dy * width + dx
                if (selection?.get(idx) == false) continue
                if (layer.cells[idx] != color) {
                    layer.cells[idx] = color
                    changed = true
                }
            }
        }
        if (changed) pushSnapshot(before) else return false
        bump()
        return true
    }

    /** 当前图层颜色替换；[allLayers] 时作用于所有帧所有图层。 */
    fun replaceColor(from: Int, to: Int, allLayers: Boolean = false): Boolean {
        val before = snapshot()
        var changed = false
        val targets = if (allLayers) {
            frames.flatMap { it.layers }
        } else {
            listOf(activeLayer)
        }
        for (layer in targets) {
            if (layer.locked) continue
            for (i in layer.cells.indices) {
                if (layer.cells[i] == from) {
                    layer.cells[i] = to
                    changed = true
                }
            }
        }
        if (changed) pushSnapshot(before) else return false
        bump()
        return true
    }

    // ------------------------------------------------------------------
    // 图层
    // ------------------------------------------------------------------

    fun addLayer(copyActive: Boolean = false): Int {
        val before = snapshot()
        val index = (activeLayerIndex + 1).coerceIn(0, layers.size)
        val layer = if (copyActive) {
            activeLayer.copy().also { it.name = "${activeLayer.name} 副本" }
        } else {
            StudioLayer("图层 ${layers.size + 1}", IntArray(width * height) { -1 })
        }
        layers.add(index, layer)
        frame.activeLayer = index
        pushSnapshot(before)
        bump()
        return index
    }

    fun duplicateLayer(index: Int): Int {
        if (index !in layers.indices) return -1
        val before = snapshot()
        val copy = layers[index].copy().also { it.name = "${it.name} 副本" }
        layers.add(index + 1, copy)
        frame.activeLayer = index + 1
        pushSnapshot(before)
        bump()
        return index + 1
    }

    fun deleteLayer(index: Int): Boolean {
        if (layers.size <= 1 || index !in layers.indices) return false
        val before = snapshot()
        layers.removeAt(index)
        frame.activeLayer = frame.activeLayer.coerceIn(0, layers.size - 1)
        pushSnapshot(before)
        bump()
        return true
    }

    fun moveLayer(from: Int, to: Int): Boolean {
        if (from !in layers.indices || to !in layers.indices || from == to) return false
        val before = snapshot()
        val layer = layers.removeAt(from)
        layers.add(to, layer)
        frame.activeLayer = to
        pushSnapshot(before)
        bump()
        return true
    }

    fun mergeDown(index: Int): Boolean {
        if (index <= 0 || index !in layers.indices) return false
        val lower = layers[index - 1]
        val upper = layers[index]
        if (lower.locked || upper.locked) return false
        val before = snapshot()
        val m = matcher
        for (i in lower.cells.indices) {
            val upperIdx = upper.cells[i]
            if (upperIdx < 0) continue
            if (upper.opacity >= 0.999f && upper.blend == StudioBlendMode.NORMAL) {
                lower.cells[i] = upperIdx
                continue
            }
            val lowerColor = palette.getOrNull(lower.cells[i]) ?: continue
            val upperColor = palette.getOrNull(upperIdx) ?: continue
            val lr = (lowerColor shr 16) and 0xFF
            val lg = (lowerColor shr 8) and 0xFF
            val lb = lowerColor and 0xFF
            val ur = (upperColor shr 16) and 0xFF
            val ug = (upperColor shr 8) and 0xFF
            val ub = upperColor and 0xFF
            val sr = blendChannel(upper.blend, lr, ur)
            val sg = blendChannel(upper.blend, lg, ug)
            val sb = blendChannel(upper.blend, lb, ub)
            val a = upper.opacity.coerceIn(0f, 1f)
            val out = (0xFF shl 24) or
                ((lr + (sr - lr) * a).roundToInt().coerceIn(0, 255) shl 16) or
                ((lg + (sg - lg) * a).roundToInt().coerceIn(0, 255) shl 8) or
                ((lb + (sb - lb) * a).roundToInt().coerceIn(0, 255))
            lower.cells[i] = m?.nearest(out) ?: upperIdx
        }
        layers.removeAt(index)
        frame.activeLayer = index - 1
        // 合并结果落在下层：如果下层之前被隐藏，合并后必须重新可见，
        // 否则用户会看到「合并后整层消失」。
        lower.visible = true
        pushSnapshot(before)
        bump()
        return true
    }

    /** 把当前帧所有可见图层压成一层。 */
    fun flattenFrame(): Boolean {
        if (layers.size <= 1) return false
        val before = snapshot()
        val flat = composite()
        layers.clear()
        layers.add(StudioLayer("图层 1", flat))
        frame.activeLayer = 0
        pushSnapshot(before)
        bump()
        return true
    }

    fun clearLayer(index: Int = activeLayerIndex): Boolean {
        if (index !in layers.indices || layers[index].locked) return false
        val before = snapshot()
        layers[index].cells.fill(-1)
        pushSnapshot(before)
        bump()
        return true
    }

    fun setLayerVisible(index: Int, visible: Boolean) {
        if (index in layers.indices) {
            layers[index].visible = visible
            invalidate()
        }
    }

    fun setLayerLocked(index: Int, locked: Boolean) {
        if (index in layers.indices) {
            layers[index].locked = locked
            bump()
        }
    }

    fun renameLayer(index: Int, name: String) {
        if (index in layers.indices && name.isNotBlank()) {
            layers[index].name = name.trim().take(40)
            bump()
        }
    }

    fun setLayerOpacity(index: Int, opacity: Float) {
        if (index in layers.indices) {
            layers[index].opacity = opacity.coerceIn(0f, 1f)
            invalidate()
        }
    }

    fun setLayerBlend(index: Int, blend: StudioBlendMode) {
        if (index in layers.indices) {
            layers[index].blend = blend
            invalidate()
        }
    }

    // ------------------------------------------------------------------
    // 帧
    // ------------------------------------------------------------------

    fun addFrame(copyCurrent: Boolean = false): Int {
        val before = snapshot()
        val f = if (copyCurrent) {
            frame.copy()
        } else {
            StudioFrame(mutableListOf(StudioLayer("图层 1", IntArray(width * height) { -1 })))
        }
        val index = frameIndex + 1
        frames.add(index, f)
        frameIndex = index
        selection = null
        pushSnapshot(before)
        bump()
        return index
    }

    fun duplicateFrame(index: Int = frameIndex): Int {
        if (index !in frames.indices) return -1
        val before = snapshot()
        frames.add(index + 1, frames[index].copy())
        frameIndex = index + 1
        selection = null
        pushSnapshot(before)
        bump()
        return frameIndex
    }

    fun deleteFrame(index: Int): Boolean {
        if (frames.size <= 1 || index !in frames.indices) return false
        val before = snapshot()
        frames.removeAt(index)
        frameIndex = frameIndex.coerceIn(0, frames.size - 1)
        selection = null
        pushSnapshot(before)
        bump()
        return true
    }

    fun moveFrame(from: Int, to: Int): Boolean {
        if (from !in frames.indices || to !in frames.indices || from == to) return false
        val before = snapshot()
        val f = frames.removeAt(from)
        frames.add(to, f)
        frameIndex = to
        pushSnapshot(before)
        bump()
        return true
    }

    fun reverseFrames(): Boolean {
        if (frames.size <= 1) return false
        val before = snapshot()
        frames.reverse()
        frameIndex = frames.size - 1 - frameIndex
        pushSnapshot(before)
        bump()
        return true
    }

    // ------------------------------------------------------------------
    // 变换
    // ------------------------------------------------------------------

    fun flipHorizontal(): Boolean {
        if (width < 2) return false
        val before = snapshot()
        val oldW = width
        forEachLayer { layer ->
            val src = layer.cells
            val out = IntArray(src.size)
            for (y in 0 until height) {
                for (x in 0 until oldW) out[y * oldW + (oldW - 1 - x)] = src[y * oldW + x]
            }
            layer.cells = out
        }
        selection = null
        pushSnapshot(before)
        bump()
        return true
    }

    fun flipVertical(): Boolean {
        if (height < 2) return false
        val before = snapshot()
        val oldW = width
        val oldH = height
        forEachLayer { layer ->
            val src = layer.cells
            val out = IntArray(src.size)
            for (y in 0 until oldH) {
                for (x in 0 until oldW) out[(oldH - 1 - y) * oldW + x] = src[y * oldW + x]
            }
            layer.cells = out
        }
        selection = null
        pushSnapshot(before)
        bump()
        return true
    }

    fun rotate90(clockwise: Boolean): Boolean {
        if (width <= 1 && height <= 1) return false
        val before = snapshot()
        val oldW = width
        val oldH = height
        val newW = oldH
        val newH = oldW
        forEachLayer { layer ->
            val src = layer.cells
            val out = IntArray(newW * newH)
            for (y in 0 until oldH) {
                for (x in 0 until oldW) {
                    val nx = if (clockwise) oldH - 1 - y else y
                    val ny = if (clockwise) x else oldW - 1 - x
                    out[ny * newW + nx] = src[y * oldW + x]
                }
            }
            layer.cells = out
        }
        width = newW
        height = newH
        selection = null
        pushSnapshot(before)
        bump()
        return true
    }

    fun rotate180(): Boolean {
        if (width <= 1 && height <= 1) return false
        val before = snapshot()
        forEachLayer { layer ->
            layer.cells = layer.cells.reversedArray()
        }
        selection = null
        pushSnapshot(before)
        bump()
        return true
    }

    /** 平移所有图层内容；移出画布的部分裁掉。 */
    fun shift(dx: Int, dy: Int): Boolean {
        if (dx == 0 && dy == 0) return false
        val before = snapshot()
        forEachLayer { layer ->
            val src = layer.cells
            val out = IntArray(src.size) { -1 }
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until width || ny !in 0 until height) continue
                    out[ny * width + nx] = src[y * width + x]
                }
            }
            layer.cells = out
        }
        selection = null
        pushSnapshot(before)
        bump()
        return true
    }

    /** 改画布尺寸，内容按锚点保留。anchor 0=左/上，0.5=居中，1=右/下。 */
    fun resizeCanvas(newWidth: Int, newHeight: Int, anchorX: Float = 0.5f, anchorY: Float = 0.5f): Boolean {
        val w = newWidth.coerceIn(1, MAX_SIDE)
        val h = newHeight.coerceIn(1, MAX_SIDE)
        if (w == width && h == height) return false
        val before = snapshot()
        val offsetX = ((w - width) * anchorX).roundToInt()
        val offsetY = ((h - height) * anchorY).roundToInt()
        forEachLayer { layer ->
            val src = layer.cells
            val out = IntArray(w * h) { -1 }
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val nx = x + offsetX
                    val ny = y + offsetY
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    out[ny * w + nx] = src[y * width + x]
                }
            }
            layer.cells = out
        }
        width = w
        height = h
        selection = null
        pushSnapshot(before)
        bump()
        return true
    }

    /** 内容整体缩放（最近邻），所有帧同步。 */
    fun scaleContent(newWidth: Int, newHeight: Int): Boolean {
        val w = newWidth.coerceIn(1, MAX_SIDE)
        val h = newHeight.coerceIn(1, MAX_SIDE)
        if (w == width && h == height) return false
        val before = snapshot()
        val oldW = width
        val oldH = height
        forEachLayer { layer ->
            val src = layer.cells
            val out = IntArray(w * h)
            for (y in 0 until h) {
                val sy = (y.toLong() * oldH / h).toInt().coerceIn(0, oldH - 1)
                for (x in 0 until w) {
                    val sx = (x.toLong() * oldW / w).toInt().coerceIn(0, oldW - 1)
                    out[y * w + x] = src[sy * oldW + sx]
                }
            }
            layer.cells = out
        }
        width = w
        height = h
        selection = null
        pushSnapshot(before)
        bump()
        return true
    }

    fun crop(left: Int, top: Int, newWidth: Int, newHeight: Int): Boolean {
        val w = newWidth.coerceIn(1, MAX_SIDE)
        val h = newHeight.coerceIn(1, MAX_SIDE)
        if (left < 0 || top < 0 || left + w > width || top + h > height) return false
        if (w == width && h == height) return false
        val before = snapshot()
        val oldW = width
        forEachLayer { layer ->
            val src = layer.cells
            val out = IntArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    out[y * w + x] = src[(top + y) * oldW + left + x]
                }
            }
            layer.cells = out
        }
        width = w
        height = h
        selection = null
        pushSnapshot(before)
        bump()
        return true
    }

    private inline fun forEachLayer(block: (StudioLayer) -> Unit) {
        for (f in frames) for (layer in f.layers) block(layer)
    }

    // ------------------------------------------------------------------
    // 统计
    // ------------------------------------------------------------------

    data class Usage(val index: Int, val count: Int)

    /** 当前帧色号用量，按数量降序。 */
    fun usage(index: Int = frameIndex): List<Usage> {
        val counts = HashMap<Int, Int>()
        for (c in composite(index)) if (c >= 0) counts[c] = (counts[c] ?: 0) + 1
        return counts.map { Usage(it.key, it.value) }.sortedByDescending { it.count }
    }

    fun usedColorCount(index: Int = frameIndex): Int = usage(index).size

    // ------------------------------------------------------------------
    // 撤销
    // ------------------------------------------------------------------

    private sealed interface Entry {
        val bytes: Int
    }

    private data class CellEntry(
        val frame: Int,
        val layer: Int,
        val changes: List<Change>,
    ) : Entry {
        override val bytes: Int get() = changes.size * 16
    }

    private data class SnapshotEntry(val before: Snapshot, val after: Snapshot) : Entry {
        override val bytes: Int get() = before.bytes + after.bytes
    }

    private data class Snapshot(
        val width: Int,
        val height: Int,
        val frames: List<StudioFrame>,
        val frameIndex: Int,
    ) {
        val bytes: Int get() = frames.sumOf { f -> f.layers.sumOf { it.cells.size * 4 + 128 } + 64 }
    }

    private fun snapshot(): Snapshot = Snapshot(
        width = width,
        height = height,
        frames = frames.map { it.copy() },
        frameIndex = frameIndex,
    )

    private fun restore(s: Snapshot) {
        width = s.width
        height = s.height
        frames.clear()
        frames.addAll(s.frames.map { it.copy() })
        frameIndex = s.frameIndex.coerceIn(0, frames.size - 1)
        selection = null
    }

    private fun pushSnapshot(before: Snapshot) {
        undoStack.addLast(SnapshotEntry(before, snapshot()))
        trimUndo()
        redoStack.clear()
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo(): Boolean {
        val e = undoStack.removeLastOrNull() ?: return false
        when (e) {
            is CellEntry -> {
                val f = frames.getOrNull(e.frame) ?: return false
                val layer = f.layers.getOrNull(e.layer) ?: return false
                for (c in e.changes.asReversed()) layer.cells[c.index] = c.old
            }
            is SnapshotEntry -> restore(e.before)
        }
        redoStack.addLast(e)
        bump()
        return true
    }

    fun redo(): Boolean {
        val e = redoStack.removeLastOrNull() ?: return false
        when (e) {
            is CellEntry -> {
                val f = frames.getOrNull(e.frame) ?: return false
                val layer = f.layers.getOrNull(e.layer) ?: return false
                for (c in e.changes) layer.cells[c.index] = c.new
            }
            is SnapshotEntry -> restore(e.after)
        }
        undoStack.addLast(e)
        bump()
        return true
    }

    private fun trimUndo() {
        var bytes = undoStack.sumOf { it.bytes }
        while (undoStack.size > MAX_HISTORY || (bytes > HISTORY_BUDGET_BYTES && undoStack.size > 1)) {
            val removed = undoStack.removeFirstOrNull() ?: break
            bytes -= removed.bytes
        }
    }

    private fun bump() {
        revision++
    }

    private fun invalidate() {
        revision++
        cellsKey = Int.MIN_VALUE
        argbKey = Int.MIN_VALUE
        cacheCells = null
        cacheArgb = null
    }

    // ------------------------------------------------------------------
    // 工具函数
    // ------------------------------------------------------------------

    private fun toleranceColorDistance(tolerance: Int): Double {
        if (palette.isEmpty()) return 0.0
        val t = tolerance.coerceIn(0, 100) / 100.0
        return t * 441.673
    }

    private fun colorMatches(a: Int, b: Int, limit: Double): Boolean {
        if (a == b) return true
        if (limit <= 0.0) return false
        if (palette.isEmpty()) return false
        val ca = palette.getOrNull(a) ?: return false
        val cb = palette.getOrNull(b) ?: return false
        val dr = ((ca shr 16) and 0xFF) - ((cb shr 16) and 0xFF)
        val dg = ((ca shr 8) and 0xFF) - ((cb shr 8) and 0xFF)
        val db = (ca and 0xFF) - (cb and 0xFF)
        return dr * dr + dg * dg + db * db <= limit * limit
    }

    private fun pointInPolygon(px: Double, py: Double, xs: IntArray, ys: IntArray): Boolean {
        var inside = false
        var j = xs.size - 1
        for (i in xs.indices) {
            val xi = xs[i] + 0.5
            val yi = ys[i] + 0.5
            val xj = xs[j] + 0.5
            val yj = ys[j] + 0.5
            if ((yi > py) != (yj > py) &&
                px < (xj - xi) * (py - yi) / ((yj - yi).takeIf { it != 0.0 } ?: 1e-9) + xi
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    private fun bresenham(x0: Int, y0: Int, x1: Int, y1: Int): List<Int> {
        var x = x0.coerceIn(0, width - 1)
        var y = y0.coerceIn(0, height - 1)
        val ex = x1.coerceIn(0, width - 1)
        val ey = y1.coerceIn(0, height - 1)
        val dx = abs(ex - x)
        val sx = if (x < ex) 1 else -1
        val dy = -abs(ey - y)
        val sy = if (y < ey) 1 else -1
        var err = dx + dy
        val out = ArrayList<Int>(max(dx, -dy) + 1)
        while (true) {
            out.add(y * width + x)
            if (x == ex && y == ey) break
            val e2 = 2 * err
            if (e2 >= dy) {
                err += dy
                x += sx
            }
            if (e2 <= dx) {
                err += dx
                y += sy
            }
        }
        return out
    }

    private fun removeLCorners(points: List<Int>, w: Int): List<Int> {
        if (points.size < 3) return points
        val out = ArrayList<Int>(points.size)
        out.add(points[0])
        for (i in 1 until points.size - 1) {
            val prev = out.last()
            val cur = points[i]
            val next = points[i + 1]
            val horizontal = (cur % w) != (prev % w) && (cur / w) == (prev / w)
            val vertical = (cur % w) == (prev % w) && (cur / w) != (prev / w)
            val nextHorizontal = (next % w) != (cur % w) && (next / w) == (cur / w)
            val nextVertical = (next % w) == (cur % w) && (next / w) != (cur / w)
            if ((horizontal && nextVertical) || (vertical && nextHorizontal)) continue
            out.add(cur)
        }
        out.add(points.last())
        return out
    }

    private fun quadratic(x0: Int, y0: Int, x1: Int, y1: Int, x2: Int, y2: Int, t: Float): Pair<Int, Int> {
        val u = 1f - t
        val x = u * u * x0 + 2 * u * t * x1 + t * t * x2
        val y = u * u * y0 + 2 * u * t * y1 + t * t * y2
        return x.roundToInt().coerceIn(0, width - 1) to y.roundToInt().coerceIn(0, height - 1)
    }

    private fun cubic(
        x0: Int, y0: Int, x1: Int, y1: Int, x2: Int, y2: Int, x3: Int, y3: Int, t: Float,
    ): Pair<Int, Int> {
        val u = 1f - t
        val x = u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3
        val y = u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3
        return x.roundToInt().coerceIn(0, width - 1) to y.roundToInt().coerceIn(0, height - 1)
    }

    private fun lerpArgb(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        val ar = (a shr 16) and 0xFF
        val ag = (a shr 8) and 0xFF
        val ab = a and 0xFF
        val br = (b shr 16) and 0xFF
        val bg = (b shr 8) and 0xFF
        val bb = b and 0xFF
        val r = (ar + (br - ar) * tt).roundToInt().coerceIn(0, 255)
        val g = (ag + (bg - ag) * tt).roundToInt().coerceIn(0, 255)
        val bl = (ab + (bb - ab) * tt).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    /**
     * 最近色匹配器：RGB555 缓存，避免每个像素都对整张色卡做一遍距离计算。
     * 混合模式/半透明图层只有打开时才走这里，普通绘制仍是纯索引拷贝。
     */
    private class PaletteMatcher(private val colors: IntArray) {
        private val cache = IntArray(1 shl 15) { Int.MIN_VALUE }

        fun nearest(argb: Int): Int {
            val a = argb ushr 24
            if (a < 8) return -1
            if (colors.isEmpty()) return -1
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            val key = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
            val hit = cache[key]
            if (hit != Int.MIN_VALUE) return hit
            var best = -1
            var bestDist = Long.MAX_VALUE
            for (i in colors.indices) {
                val c = colors[i]
                val dr = r - ((c shr 16) and 0xFF)
                val dg = g - ((c shr 8) and 0xFF)
                val db = b - (c and 0xFF)
                val dist = (dr * dr * 2 + dg * dg * 4 + db * db * 3).toLong()
                if (dist < bestDist) {
                    bestDist = dist
                    best = i
                }
            }
            cache[key] = best
            return best
        }
    }

    companion object {
        const val MAX_SIDE = 512
        private const val MAX_HISTORY = 40
        private const val HISTORY_BUDGET_BYTES = 48 * 1024 * 1024

        private val BAYER4 = arrayOf(
            intArrayOf(0, 8, 2, 10),
            intArrayOf(12, 4, 14, 6),
            intArrayOf(3, 11, 1, 9),
            intArrayOf(15, 7, 13, 5),
        )

        private fun defaultFrame(width: Int, height: Int): StudioFrame =
            StudioFrame(mutableListOf(StudioLayer("图层 1", IntArray(width * height) { -1 })))

        private fun StudioFrame.normalized(width: Int, height: Int): StudioFrame {
            val fixed = layers.map { layer ->
                if (layer.cells.size == width * height) layer
                else StudioLayer(
                    layer.name,
                    IntArray(width * height) { i -> layer.cells.getOrElse(i) { -1 } },
                    layer.visible,
                    layer.locked,
                    layer.opacity,
                    layer.blend,
                )
            }.ifEmpty { listOf(StudioLayer("图层 1", IntArray(width * height) { -1 })) }
            return StudioFrame(fixed.toMutableList(), activeLayer.coerceIn(0, fixed.size - 1), holdMs)
        }
    }
}
