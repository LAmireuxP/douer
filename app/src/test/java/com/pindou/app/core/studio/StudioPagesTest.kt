package com.pindou.app.core.studio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class StudioPagesTest {

    @Test
    fun `单页回退命名为图层一`() {
        val pages = listOf(intArrayOf(1, 2, 3, 4))
        val engine = StudioPages.engineFrom(2, 2, pages, IntArray(0))

        assertEquals(1, engine.frames.size)
        assertEquals("图层 1", engine.frames[0].layers[0].name)
        assertArrayEquals(intArrayOf(1, 2, 3, 4), engine.frames[0].layers[0].cells)
    }

    @Test
    fun `多页按第 N 页命名并各建一帧`() {
        val engine = StudioPages.engineFrom(
            2, 2,
            listOf(intArrayOf(1, 1, 1, 1), intArrayOf(2, 2, 2, 2)),
            IntArray(0),
        )

        assertEquals(2, engine.frames.size)
        assertEquals("第 1 页", engine.frames[0].layers[0].name)
        assertEquals("第 2 页", engine.frames[1].layers[0].name)
        assertArrayEquals(intArrayOf(2, 2, 2, 2), engine.frames[1].layers[0].cells)
    }

    @Test
    fun `页数组是拷贝的改源不影响引擎`() {
        val page = intArrayOf(1, 2, 3, 4)
        val engine = StudioPages.engineFrom(2, 2, listOf(page), IntArray(0))

        assertNotSame(page, engine.frames[0].layers[0].cells)
        page[0] = 9
        assertEquals(1, engine.frames[0].layers[0].cells[0])
    }
}
