package com.pindou.app.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorEngineTest {

    private fun engine3x3() = EditorEngine(3, 3, IntArray(9) { -1 })

    @Test
    fun `笔触内同格多次修改只记首个旧值`() {
        val e = engine3x3()
        val s = e.startStroke()
        s.setCell(0, 5)
        s.setCell(0, 7)
        s.setCell(0, 9)
        e.endStroke(s)
        assertEquals(9, e.cells[0])
        e.undo()
        assertEquals(-1, e.cells[0]) // 恢复到笔触开始前的值，不是中间值
    }

    @Test
    fun `撤销重做往返`() {
        val e = engine3x3()
        val s = e.startStroke()
        s.setCell(1, 3)
        s.setCell(2, 4)
        e.endStroke(s)
        assertEquals(3, e.cells[1])
        assertTrue(e.undo())
        assertEquals(-1, e.cells[1])
        assertEquals(-1, e.cells[2])
        assertTrue(e.redo())
        assertEquals(3, e.cells[1])
        assertEquals(4, e.cells[2])
        assertFalse(e.redo())
    }

    @Test
    fun `锁定格不可绘制`() {
        val e = engine3x3()
        e.setLocked(4, true)
        val s = e.startStroke()
        assertFalse(s.setCell(4, 2))
        e.endStroke(s)
        assertEquals(-1, e.cells[4])
    }

    @Test
    fun `空笔触不入撤销栈`() {
        val e = engine3x3()
        val s = e.startStroke()
        // 尝试写锁定格（无效果）
        e.setLocked(0, true)
        s.setCell(0, 1)
        e.endStroke(s)
        assertFalse(e.canUndo)
        assertFalse(e.undo())
    }

    @Test
    fun `新笔触清空重做栈`() {
        val e = engine3x3()
        val s1 = e.startStroke()
        s1.setCell(0, 1)
        e.endStroke(s1)
        e.undo()
        assertTrue(e.canRedo)
        val s2 = e.startStroke()
        s2.setCell(8, 2)
        e.endStroke(s2)
        assertFalse(e.canRedo)
    }

    @Test
    fun `撤销栈上限 50`() {
        val e = engine3x3()
        repeat(120) { i ->
            val s = e.startStroke()
            s.setCell(i % 9, i)
            e.endStroke(s)
        }
        var undos = 0
        while (e.undo()) undos++
        assertEquals(50, undos)
    }

    @Test
    fun `revision 只在有变更时增长`() {
        val e = engine3x3()
        val r0 = e.revision
        e.setLocked(0, true)
        val r1 = e.revision
        val s = e.startStroke()
        assertFalse(s.setCell(0, 5)) // 锁定格无变更
        e.endStroke(s)
        assertEquals(r1, e.revision)
        val s2 = e.startStroke()
        assertTrue(s2.setCell(1, 5))
        e.endStroke(s2)
        assertTrue(e.revision > r1)
    }

    @Test
    fun `多页独立绘制与切换`() {
        val e = EditorEngine(3, 3, listOf(IntArray(9) { -1 }, IntArray(9) { -1 }))
        assertEquals(2, e.pageCount)
        e.setPage(1)
        val s = e.startStroke()
        s.setCell(0, 5)
        e.endStroke(s)
        e.setPage(0)
        assertEquals(-1, e.cells[0]) // 第 1 页不受第 2 页笔触影响
        e.setPage(1)
        assertEquals(5, e.cells[0])
    }

    @Test
    fun `油漆桶填充同色连通区域`() {
        val e = engine3x3()
        assertTrue(e.floodFill(4, 5))
        assertEquals(9, e.cells.count { it == 5 }) // 全空棋盘 4 连通充满
        e.undo()
        assertEquals(-1, e.cells[4]) // 整个填充作为一条撤销记录
    }

    @Test
    fun `油漆桶锁定格不改色但保持连通`() {
        val e = engine3x3()
        e.setLocked(1, true)
        assertTrue(e.floodFill(0, 7))
        assertEquals(-1, e.cells[1])
        assertEquals(8, e.cells.count { it == 7 })
    }

    @Test
    fun `油漆桶起点同色无操作`() {
        val e = engine3x3()
        assertFalse(e.floodFill(0, -1)) // 目标色与当前色相同
        assertFalse(e.canUndo)
    }

    @Test
    fun `新增复制页与删除页可撤销`() {
        val e = engine3x3()
        val s = e.startStroke()
        s.setCell(0, 5)
        e.endStroke(s)
        e.addPage(copyCurrent = true)
        assertEquals(2, e.pageCount)
        assertEquals(1, e.pageIndex)
        assertEquals(5, e.cells[0])
        assertTrue(e.removePage(0))
        assertEquals(1, e.pageCount)
        assertTrue(e.undo())
        assertEquals(2, e.pageCount) // 撤销删除页
        assertEquals(1, e.pageIndex)
        assertTrue(e.undo())
        assertEquals(1, e.pageCount) // 撤销新增页
        assertEquals(0, e.pageIndex)
    }

    @Test
    fun `改尺寸居中保留内容`() {
        val e = engine3x3()
        val s = e.startStroke()
        s.setCell(4, 5) // 中心格 (1,1)
        e.endStroke(s)
        e.resize(5, 5)
        assertEquals(25, e.cells.size)
        assertEquals(5, e.cells[2 * 5 + 2]) // (1,1) 平移到 (2,2)
        e.undo()
        assertEquals(3, e.width)
        assertEquals(3, e.height)
        assertEquals(9, e.cells.size)
        assertEquals(5, e.cells[4])
        e.redo()
        assertEquals(5, e.width)
        assertEquals(5, e.height)
    }

    @Test
    fun `多页改尺寸所有页同步`() {
        val e = EditorEngine(3, 3, listOf(IntArray(9) { -1 }, IntArray(9) { 2 }))
        e.resize(4, 4)
        assertEquals(16, e.pages[0].size)
        assertEquals(16, e.pages[1].size)
        assertEquals(9, e.pages[1].count { it == 2 }) // 9 个旧格全部保留
    }

    // ---- replaceColorOnPage ----

    @Test
    fun `同色替换整页并一次撤销还原`() {
        val e = EditorEngine(3, 3, IntArray(9) { i -> if (i % 2 == 0) 1 else 2 })
        assertTrue(e.replaceColorOnPage(1, 7))
        assertEquals(5, e.cells.count { it == 7 })
        assertEquals(4, e.cells.count { it == 2 })
        assertTrue(e.undo())
        assertEquals(5, e.cells.count { it == 1 })
        assertEquals(0, e.cells.count { it == 7 })
        assertFalse(e.canUndo) // 只入了一条历史
        assertTrue(e.redo())
        assertEquals(5, e.cells.count { it == 7 })
    }

    @Test
    fun `同色替换跳过锁定格`() {
        val e = EditorEngine(3, 3, IntArray(9) { 1 })
        e.setLocked(0, true)
        e.setLocked(4, true)
        assertTrue(e.replaceColorOnPage(1, 9))
        assertEquals(1, e.cells[0])
        assertEquals(1, e.cells[4])
        assertEquals(7, e.cells.count { it == 9 })
        e.undo()
        assertEquals(9, e.cells.count { it == 1 })
    }

    @Test
    fun `同色替换无目标或目标等于替换色时返回 false`() {
        val e = EditorEngine(3, 3, IntArray(9) { 1 })
        assertFalse(e.replaceColorOnPage(1, 1))
        assertFalse(e.replaceColorOnPage(5, 9))
        assertFalse(e.canUndo)
    }

    @Test
    fun `同色替换全锁定返回 false 不入历史`() {
        val e = EditorEngine(2, 2, IntArray(4) { 3 })
        repeat(4) { e.setLocked(it, true) }
        assertFalse(e.replaceColorOnPage(3, 8))
        assertFalse(e.canUndo)
    }

    @Test
    fun `同色替换只作用于当前页`() {
        val e = EditorEngine(3, 3, listOf(IntArray(9) { 1 }, IntArray(9) { 1 }))
        assertTrue(e.replaceColorOnPage(1, 6))
        e.setPage(1)
        assertEquals(9, e.cells.count { it == 1 })
    }

    // ---- connectedRegionSize ----

    @Test
    fun `连通块大小只统计四连通同色区域`() {
        // 1 1 2
        // 1 2 2
        // 3 3 2
        val e = EditorEngine(3, 3, intArrayOf(1, 1, 2, 1, 2, 2, 3, 3, 2))
        assertEquals(3, e.connectedRegionSize(0))
        assertEquals(2, e.connectedRegionSize(7))
        assertEquals(4, e.connectedRegionSize(8))
        assertEquals(4, e.connectedRegionSize(4))
        assertEquals(0, e.connectedRegionSize(-1))
        assertEquals(0, e.connectedRegionSize(99))
    }

    @Test
    fun `连通块统计不产生撤销记录`() {
        val e = EditorEngine(2, 2, intArrayOf(-1, -1, -1, 1))
        assertEquals(3, e.connectedRegionSize(0))
        assertFalse(e.canUndo)
        assertEquals(0, e.revision)
    }

    // ---- 区域擦除 ----

    @Test
    fun `区域擦除只清同色连通块并一次撤销`() {
        // 1 1 2
        // 1 2 2
        // 3 3 2
        val e = EditorEngine(3, 3, intArrayOf(1, 1, 2, 1, 2, 2, 3, 3, 2))
        assertTrue(e.eraseRegion(0))
        assertEquals(3, e.cells.count { it == -1 })
        assertEquals(0, e.cells.count { it == 1 })
        assertEquals(4, e.cells.count { it == 2 })
        assertEquals(3, e.cells[6])
        e.undo()
        assertEquals(3, e.cells.count { it == 1 })
        assertFalse(e.canUndo)
    }

    @Test
    fun `区域擦除跳过锁定格与空格`() {
        val e = EditorEngine(3, 3, IntArray(9) { 4 })
        e.setLocked(1, true)
        assertTrue(e.eraseRegion(0))
        assertEquals(4, e.cells[1])
        assertEquals(8, e.cells.count { it == -1 })
        assertFalse(EditorEngine(2, 2, IntArray(4) { -1 }).eraseRegion(0))
    }

    // ---- 裁剪 ----

    @Test
    fun `裁剪按矩形取多页内容并保持锁定`() {
        // 4×3: 每行 10,20,30,40 / 11,21,31,41 / 12,22,32,42
        val page0 = IntArray(12) { 10 + (it / 4) + (it % 4) * 10 }
        val page1 = IntArray(12) { it }
        val locks0 = BooleanArray(12).also { it[5] = true } // (1,1)
        val e = EditorEngine(4, 3, listOf(page0, page1), listOf(locks0, BooleanArray(12)))
        assertTrue(e.crop(1, 1, 2, 2))
        assertEquals(2, e.width)
        assertEquals(2, e.height)
        assertArrayEquals(intArrayOf(21, 31, 22, 32), e.pages[0])
        assertArrayEquals(intArrayOf(5, 6, 9, 10), e.pages[1])
        assertTrue(e.locks[0][0])
        assertEquals(1, e.locks[0].count { it })
        e.undo()
        assertEquals(4, e.width)
        assertEquals(3, e.height)
        assertEquals(12, e.cells.size)
        assertEquals(10, e.cells[0])
        e.redo()
        assertEquals(2, e.width)
        assertEquals(2, e.height)
    }

    @Test
    fun `裁剪边界校验与无变化返回 false`() {
        val e = EditorEngine(3, 3, IntArray(9) { -1 })
        assertFalse(e.crop(0, 0, 3, 3))
        assertThrows(IllegalArgumentException::class.java) { e.crop(2, 0, 2, 2) }
        assertThrows(IllegalArgumentException::class.java) { e.crop(-1, 0, 2, 2) }
        assertThrows(IllegalArgumentException::class.java) { e.crop(0, 0, 0, 2) }
    }

    // ---- 画像素式变换 ----

    @Test
    fun `水平翻转镜像所有页并可撤销`() {
        val page0 = intArrayOf(1, 2, 3, 4, 5, 6)
        val page1 = intArrayOf(7, 8, 9, 10, 11, 12)
        val e = EditorEngine(3, 2, listOf(page0, page1))
        assertTrue(e.flipHorizontal())
        assertArrayEquals(intArrayOf(3, 2, 1, 6, 5, 4), e.pages[0])
        assertArrayEquals(intArrayOf(9, 8, 7, 12, 11, 10), e.pages[1])
        assertTrue(e.undo())
        assertArrayEquals(page0, e.pages[0])
        assertArrayEquals(page1, e.pages[1])
    }

    @Test
    fun `垂直翻转镜像并保留锁定`() {
        val e = EditorEngine(2, 3, intArrayOf(1, 2, 3, 4, 5, 6))
        e.setLocked(0, true)
        assertTrue(e.flipVertical())
        assertArrayEquals(intArrayOf(5, 6, 3, 4, 1, 2), e.cells)
        assertTrue(e.locked[4])
    }

    @Test
    fun `顺时针旋转宽高互换且可撤销`() {
        val e = EditorEngine(2, 3, intArrayOf(1, 2, 3, 4, 5, 6))
        assertTrue(e.rotate90(clockwise = true))
        assertEquals(3, e.width)
        assertEquals(2, e.height)
        assertArrayEquals(intArrayOf(5, 3, 1, 6, 4, 2), e.cells)
        assertTrue(e.undo())
        assertEquals(2, e.width)
        assertEquals(3, e.height)
        assertArrayEquals(intArrayOf(1, 2, 3, 4, 5, 6), e.cells)
    }

    @Test
    fun `逆时针旋转与顺时针互为逆操作`() {
        val e = EditorEngine(2, 3, intArrayOf(1, 2, 3, 4, 5, 6))
        e.rotate90(clockwise = true)
        e.rotate90(clockwise = false)
        assertEquals(2, e.width)
        assertEquals(3, e.height)
        assertArrayEquals(intArrayOf(1, 2, 3, 4, 5, 6), e.cells)
    }

    @Test
    fun `编辑器支持 500 尺寸改画布`() {
        val e = EditorEngine(250, 250, IntArray(250 * 250) { -1 })
        e.resize(500, 500)
        assertEquals(500, e.width)
        assertEquals(500, e.height)
        assertEquals(250_000, e.cells.size)
        assertTrue(e.crop(0, 0, 500, 500) == false)
    }
}

