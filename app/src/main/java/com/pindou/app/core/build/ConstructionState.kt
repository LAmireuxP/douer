package com.pindou.app.core.build

/**
 * 施工模式状态：纯 Kotlin，可单测。
 *
 * done 是活动数组：UI 直接原地改写（不重建对象），配合 [revision] 计数触发 Compose 重绘；
 * 持久化方（AppViewModel.saveConstructionProgress）每次操作后读取同一数组落库。
 * 进度按页平铺：done 长度为 width × height × pageCount，当前页由 [pageIndex] 指定。
 */
class ConstructionState(
    val width: Int,
    val height: Int,
    val done: BooleanArray,
    pages: Int = 1,
    initialPage: Int = 0,
    /** 引导方向；UI 可切换（按行/按列） */
    var orientation: Orientation = Orientation.ROW,
    /** 当前行/列号（按 [orientation] 解释），引导视线 */
    var cursor: Int = 0,
) {
    enum class Orientation { ROW, COLUMN, COLOR, BOARD }

    val pageCount: Int = pages.coerceAtLeast(1)
    var pageIndex: Int = initialPage.coerceIn(0, pageCount - 1)
        private set

    /** 数据变更计数：UI 观察它触发重绘（BooleanArray 原地改写无法被 Compose 察觉） */
    var revision: Int = 0
        private set

    init {
        require(done.size == width * height * pageCount) { "进度数组尺寸与网格/页数不符" }
    }

    val pageSize: Int get() = width * height
    val pageOffset: Int get() = pageIndex * pageSize
    val total: Int get() = pageSize

    val doneCount: Int get() = (pageOffset until pageOffset + pageSize).count { done[it] }

    /** 完成比例 0~1 */
    val progress: Float get() = if (total == 0) 0f else doneCount.toFloat() / total

    private fun bump() {
        revision++
    }

    /** 当前页某格是否已完成 */
    fun isDone(index: Int): Boolean =
        index in 0 until pageSize && done[pageOffset + index]

    /** 切换当前页；越界会被钳制，返回是否真的换页 */
    fun setPage(index: Int): Boolean {
        val target = index.coerceIn(0, pageCount - 1)
        if (target == pageIndex) return false
        pageIndex = target
        cursor = 0
        bump()
        return true
    }

    /** 切换单格完成状态 */
    fun toggle(index: Int) {
        if (index !in 0 until pageSize) return
        val global = pageOffset + index
        done[global] = !done[global]
        bump()
    }

    /** 设置单格完成状态；无变化返回 false */
    fun setDone(index: Int, value: Boolean): Boolean {
        if (index !in 0 until pageSize) return false
        val global = pageOffset + index
        if (done[global] == value) return false
        done[global] = value
        bump()
        return true
    }

    /** 整行标为完成/未完成，返回实际改动的格数 */
    fun setRowDone(row: Int, value: Boolean): Int {
        if (row !in 0 until height) return 0
        var changed = 0
        for (x in 0 until width) {
            if (setDone(row * width + x, value)) changed++
        }
        return changed
    }

    /** 整列标为完成/未完成，返回实际改动的格数 */
    fun setColDone(col: Int, value: Boolean): Int {
        if (col !in 0 until width) return 0
        var changed = 0
        for (y in 0 until height) {
            if (setDone(y * width + col, value)) changed++
        }
        return changed
    }

    /** 第一个还有未完成格的行号；全部完成返回 -1 */
    fun nextIncompleteRow(): Int {
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!done[pageOffset + y * width + x]) return y
            }
        }
        return -1
    }

    /** 第一个还有未完成格的列号；全部完成返回 -1 */
    fun nextIncompleteCol(): Int {
        for (x in 0 until width) {
            for (y in 0 until height) {
                if (!done[pageOffset + y * width + x]) return x
            }
        }
        return -1
    }

    /** 底板尺寸：29×29 是国内最常用的方板；小于它的图纸自然落在一块板上。 */
    val boardSide: Int = 29

    /** 按拼装底板划的总板数（横向 × 纵向）。板序号从左上角开始按行扫描。 */
    val boardCount: Int
        get() = acrossBoards(width) * acrossBoards(height)

    /** 当前板内格子的索引（当前页），板外格子为 -1。 */
    fun boardCells(board: Int): IntArray {
        if (board < 0 || board >= boardCount) return IntArray(0)
        val boardsX = acrossBoards(width)
        val bx = board % boardsX
        val by = board / boardsX
        val x0 = bx * boardSide
        val y0 = by * boardSide
        val x1 = minOf(width, x0 + boardSide)
        val y1 = minOf(height, y0 + boardSide)
        val out = ArrayList<Int>((x1 - x0).coerceAtLeast(0) * (y1 - y0).coerceAtLeast(0))
        for (y in y0 until y1) {
            for (x in x0 until x1) out.add(y * width + x)
        }
        return out.toIntArray()
    }

    /** 把任意格索引换算成底板编号；越界返回 -1。cursor 在 BOARD 模式下是板号。 */
    fun boardOf(index: Int): Int {
        if (index < 0 || index >= pageSize) return -1
        val x = index % width
        val y = index / width
        val bx = x / boardSide
        val by = y / boardSide
        return by * acrossBoards(width) + bx
    }

    /** 第一个还有未拼格的底板；全部完成返回 -1。 */
    fun nextIncompleteBoard(start: Int = 0): Int {
        for (b in start.coerceIn(0, boardCount) until boardCount) {
            for (i in boardCells(b)) {
                if (!done[pageOffset + i]) return b
            }
        }
        return -1
    }

    /**
     * 从 [fromExclusive] 的下一块开始找未完成板，到末尾后回绕开头。
     *
     * 「下一板」与「完成本板」必须共用这一套顺序；否则完成末尾的板后
     * 会停在已完成板上，而前面仍有漏拼板却点不动。
     */
    fun nextIncompleteBoardCircular(fromExclusive: Int = cursor): Int {
        if (boardCount <= 0) return -1
        val start = ((fromExclusive % boardCount) + boardCount) % boardCount
        for (offset in 0 until boardCount) {
            val board = (start + offset) % boardCount
            if (boardCells(board).any { !done[pageOffset + it] }) return board
        }
        return -1
    }

    /** 整块底板标为完成/未完成，返回实际改动格数。 */
    fun setBoardDone(board: Int, value: Boolean): Int {
        var changed = 0
        for (i in boardCells(board)) {
            if (setDone(i, value)) changed++
        }
        return changed
    }

    private fun acrossBoards(size: Int): Int = (size + boardSide - 1) / boardSide

    /** 按当前方向跳到下一个未完成行/列，并更新游标；返回是否找到（按色号由界面处理） */
    fun jumpToNextIncomplete(): Boolean {
        val next = when (orientation) {
            Orientation.ROW -> nextIncompleteRow()
            Orientation.COLUMN -> nextIncompleteCol()
            Orientation.COLOR, Orientation.BOARD -> return false
        }
        if (next < 0) return false
        cursor = next
        bump()
        return true
    }

    /**
     * 切换引导方式（变更计数 +1 触发重绘）；与属性 setter 的 JVM 签名错开。
     *
     * 四种模式的 cursor 语义不同（行号 / 列号 / 色号 / 板号），切换时归零，
     * 由界面按剩余进度挑第一个可用的位置，避免拿行号当色号用。
     */
    fun updateOrientation(value: Orientation) {
        if (orientation == value) return
        orientation = value
        cursor = 0
        bump()
    }
}
