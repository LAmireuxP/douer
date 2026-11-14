package com.pindou.app.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeToolTest {

    @Test
    fun `直线对角光栅化`() {
        assertEquals(
            listOf(0, 4, 8),
            ShapeTool.LINE.indices(0, 0, 2, 2, 3, 3, filled = false),
        )
    }

    @Test
    fun `矩形描边与填充`() {
        val outline = ShapeTool.RECT.indices(0, 0, 2, 2, 3, 3, filled = false)
        assertEquals(8, outline.size)
        assertFalse(4 in outline)

        val filled = ShapeTool.RECT.indices(0, 0, 2, 2, 3, 3, filled = true)
        assertEquals(9, filled.size)
        assertTrue(4 in filled)
    }

    @Test
    fun `椭圆描边覆盖外框且不填中心`() {
        val outline = ShapeTool.ELLIPSE.indices(0, 0, 4, 4, 5, 5, filled = false)
        assertTrue(2 in outline)  // 上
        assertTrue(10 in outline) // 左
        assertTrue(14 in outline) // 右
        assertTrue(22 in outline) // 下
        assertFalse(12 in outline)
    }

    @Test
    fun `形状按水平对称同时落笔`() {
        val e = EditorEngine(5, 5, IntArray(25) { -1 })
        assertTrue(
            e.drawShape(
                kind = ShapeTool.LINE,
                start = 1 * 5 + 1,
                end = 3 * 5 + 1,
                color = 7,
                filled = false,
                symmetry = SymmetryMode.HORIZONTAL,
            ),
        )
        assertEquals(7, e.cells[1 * 5 + 1])
        assertEquals(7, e.cells[3 * 5 + 1])
        assertEquals(7, e.cells[1 * 5 + 3])
        assertEquals(7, e.cells[3 * 5 + 3])
    }
}
