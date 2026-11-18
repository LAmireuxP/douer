package com.pindou.app.core.export

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** 按底板 PDF 分页：页数、末板收窄与切格索引。 */
class BoardPdfLayoutTest {

    @Test
    fun `底板页数公式`() {
        assertEquals(1, BoardPdfLayout.pageCount(29, 29))
        assertEquals(2, BoardPdfLayout.pageCount(30, 29))
        assertEquals(4, BoardPdfLayout.pageCount(58, 58))
        assertEquals(9, BoardPdfLayout.pageCount(60, 60))
    }

    @Test
    fun `末板按实际宽高收窄`() {
        assertEquals(58, BoardPdfLayout.colStart(60, 2))
        assertEquals(2, BoardPdfLayout.colCount(60, 2))
        assertEquals(58, BoardPdfLayout.rowStart(60, 60, 6))
        assertEquals(2, BoardPdfLayout.rowCount(60, 60, 6))
    }

    @Test
    fun `切格保持原网格索引`() {
        val w = 30
        val h = 29
        val cells = IntArray(w * h) { it }
        val last = BoardPdfLayout.slice(cells, w, h, 1)
        assertEquals(29, last.size)
        assertEquals(29, last[0])
        assertEquals(30 * 28 + 29, last.last())
    }

    @Test
    fun `多板拼回原格`() {
        val w = 58
        val h = 58
        val cells = IntArray(w * h) { it }
        val merged = IntArray(w * h)
        for (board in 0 until BoardPdfLayout.pageCount(w, h)) {
            val cols = BoardPdfLayout.colCount(w, board)
            val rows = BoardPdfLayout.rowCount(w, h, board)
            val part = BoardPdfLayout.slice(cells, w, h, board)
            val c0 = BoardPdfLayout.colStart(w, board)
            val r0 = BoardPdfLayout.rowStart(w, h, board)
            for (y in 0 until rows) {
                for (x in 0 until cols) {
                    merged[(r0 + y) * w + c0 + x] = part[y * cols + x]
                }
            }
        }
        assertArrayEquals(cells, merged)
    }
}
